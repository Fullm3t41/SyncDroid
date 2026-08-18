package com.synctosh.app.mesh

import java.io.Closeable
import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.InetAddress
import java.security.KeyStore
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import java.util.concurrent.atomic.AtomicBoolean
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLServerSocket
import javax.net.ssl.SSLSocket
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import kotlinx.coroutines.withContext
import com.syncdroid.shared.protocol.PairingCompletionMessage

data class PeerTlsIdentity(val publicKeySpki: ByteArray)

class DeviceTlsContext(
    identity: MacDeviceIdentity,
    trustedTlsKeys: Collection<ByteArray> = emptyList(),
    allowUnknownPeer: Boolean = false,
) {
    private val context = SSLContext.getInstance("TLSv1.2").apply {
        val password = "synctosh-runtime".toCharArray()
        val keys = KeyStore.getInstance("PKCS12").apply {
            load(null, password)
            setKeyEntry("synctosh", identity.privateKey(), password, arrayOf(identity.certificate))
        }
        val keyManagers = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm()).run {
            init(keys, password); keyManagers
        }
        init(
            keyManagers,
            arrayOf<TrustManager>(PinnedTrustManager(trustedTlsKeys.map(ByteArray::copyOf), allowUnknownPeer)),
            null,
        )
    }

    fun serverSocket(port: Int = 0): SSLServerSocket =
        (context.serverSocketFactory.createServerSocket(port) as SSLServerSocket).apply {
            enabledProtocols = arrayOf("TLSv1.2")
            needClientAuth = true
        }

    fun clientSocket(address: InetAddress, port: Int): SSLSocket =
        (context.socketFactory.createSocket(address, port) as SSLSocket).apply {
            enabledProtocols = arrayOf("TLSv1.2")
            useClientMode = true
        }
}

private class PinnedTrustManager(
    private val trustedKeys: Collection<ByteArray>,
    private val allowUnknown: Boolean,
) : X509TrustManager {
    override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) = check(chain)
    override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) = check(chain)
    override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()

    private fun check(chain: Array<out X509Certificate>?) {
        val certificate = chain?.singleOrNull() ?: throw CertificateException("Peer must present one certificate")
        certificate.checkValidity()
        if (allowUnknown) return
        if (trustedKeys.none { it.contentEquals(certificate.publicKey.encoded) }) {
            throw CertificateException("Peer TLS key is not pinned")
        }
    }
}

class AuthenticatedPeerConnection internal constructor(private val socket: SSLSocket) : Closeable {
    val peerTlsIdentity: PeerTlsIdentity by lazy {
        val certificate = socket.session.peerCertificates.singleOrNull() as? X509Certificate
            ?: throw CertificateException("Peer must present one certificate")
        PeerTlsIdentity(certificate.publicKey.encoded)
    }
    private val input = DataInputStream(socket.inputStream.buffered())
    private val output = DataOutputStream(socket.outputStream.buffered())

    suspend fun send(message: ByteArray) = withContext(Dispatchers.IO) {
        require(message.size <= MAX_MESSAGE_BYTES)
        synchronized(output) { output.writeInt(message.size); output.write(message); output.flush() }
    }

    suspend fun receive(): ByteArray = withContext(Dispatchers.IO) {
        val size = input.readInt().also { require(it in 0..MAX_MESSAGE_BYTES) }
        ByteArray(size).also(input::readFully)
    }

    override fun close() = socket.close()
}

class MeshPeerServer(
    private val tls: DeviceTlsContext,
    private val onConnection: suspend (AuthenticatedPeerConnection) -> Unit,
) : Closeable {
    private val running = AtomicBoolean(false)
    private var server: SSLServerSocket? = null
    private var scope: CoroutineScope? = null
    private var lifecycleJob: Job? = null
    val port get() = server?.localPort ?: 0

    fun start(): Int {
        check(running.compareAndSet(false, true))
        val socket = tls.serverSocket()
        server = socket
        val serverScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        scope = serverScope
        lifecycleJob = serverScope.coroutineContext[Job]
        serverScope.launch {
            while (isActive && running.get()) {
                val accepted = runCatching { socket.accept() as SSLSocket }.getOrElse {
                    if (running.get()) throw it else break
                }
                launch {
                    runCatching {
                        accepted.startHandshake()
                        AuthenticatedPeerConnection(accepted).use { connection ->
                            connection.peerTlsIdentity
                            onConnection(connection)
                        }
                    }.onFailure { runCatching { accepted.close() } }
                }
            }
        }
        return socket.localPort
    }

    override fun close() {
        running.set(false); runCatching { server?.close() }; scope?.cancel(); server = null; scope = null
    }

    suspend fun awaitClosed() {
        lifecycleJob?.join()
        lifecycleJob = null
    }
}

class MeshPeerClient(private val tls: DeviceTlsContext) {
    suspend fun connect(address: InetAddress, port: Int): AuthenticatedPeerConnection = withContext(Dispatchers.IO) {
        val socket = tls.clientSocket(address, port)
        socket.startHandshake()
        AuthenticatedPeerConnection(socket).also { it.peerTlsIdentity }
    }
}

class PairingConnectionProtocol(
    private val connection: AuthenticatedPeerConnection,
    private val handshake: PairingHandshake,
) {
    suspend fun run(): PairingResult {
        connection.send(PairingWireCodec.encode(handshake.createRound1()))
        handshake.receiveRound1(connection.receive().decodeAs())
        connection.send(PairingWireCodec.encode(handshake.createRound2()))
        handshake.receiveRound2(connection.receive().decodeAs())
        connection.send(PairingWireCodec.encode(handshake.createRound3()))
        handshake.receiveRound3(connection.receive().decodeAs())
        connection.send(PairingWireCodec.encode(handshake.createConfirmation()))
        return handshake.finish(connection.receive().decodeAs())
    }

    private inline fun <reified T> ByteArray.decodeAs(): T = PairingWireCodec.decode(this) as? T
        ?: error("Unexpected pairing message order")
}

object PairingCompletionCodec {
    fun encode(message: PairingCompletionMessage): ByteArray =
        com.syncdroid.shared.protocol.PairingCompletionWireCodec.encode(message)

    fun decode(bytes: ByteArray): PairingCompletionMessage =
        com.syncdroid.shared.protocol.PairingCompletionWireCodec.decode(bytes)
}

private const val MAX_MESSAGE_BYTES = 16 * 1024 * 1024
