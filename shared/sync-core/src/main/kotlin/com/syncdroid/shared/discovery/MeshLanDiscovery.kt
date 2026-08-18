package com.syncdroid.shared.discovery

import java.io.Closeable
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.SocketTimeoutException
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.Base64
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class LanMeshPeer(
    val deviceId: String,
    val address: InetAddress,
    val port: Int,
    val protocolMajor: Int,
    val lastSeenAtMillis: Long,
)

/** UDP broadcast fallback for devices whose DNS-SD implementation is unavailable or unreliable. */
class MeshLanDiscovery(
    private val localDeviceId: String,
    groupId: String,
) : Closeable {
    private val groupTag = meshLanGroupTag(groupId)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val socket = DatagramSocket(null).apply {
        reuseAddress = true
        broadcast = true
        soTimeout = RECEIVE_TIMEOUT_MILLIS
        bind(InetSocketAddress(DISCOVERY_PORT))
    }
    private val mutablePeers = MutableStateFlow<Map<String, LanMeshPeer>>(emptyMap())
    val peers: StateFlow<Map<String, LanMeshPeer>> = mutablePeers
    @Volatile private var advertisedPort: Int? = null

    init {
        scope.launch { receiveLoop() }
        scope.launch {
            while (isActive) {
                pruneExpired()
                if (advertisedPort != null) broadcast(QUERY)
                advertisedPort?.let { broadcast(announcement(it)) }
                delay(ANNOUNCEMENT_INTERVAL_MILLIS)
            }
        }
    }

    fun start(tcpPort: Int) {
        require(tcpPort in 1..65_535)
        advertisedPort = tcpPort
        broadcast(QUERY)
        broadcast(announcement(tcpPort))
    }

    fun stop() {
        advertisedPort = null
        mutablePeers.value = emptyMap()
    }

    private fun receiveLoop() {
        val buffer = ByteArray(512)
        while (!socket.isClosed) {
            try {
                val packet = DatagramPacket(buffer, buffer.size)
                socket.receive(packet)
                val message = String(packet.data, packet.offset, packet.length, StandardCharsets.UTF_8)
                when {
                    message == "$QUERY|$groupTag" -> advertisedPort?.let { send(announcement(it), packet.address) }
                    message.startsWith("$ANNOUNCEMENT|") -> acceptAnnouncement(message, packet.address)
                }
            } catch (_: SocketTimeoutException) {
                pruneExpired()
            } catch (_: Throwable) {
                if (socket.isClosed) return
            }
        }
    }

    private fun acceptAnnouncement(message: String, source: InetAddress) {
        val parts = message.split('|')
        if (parts.size != 5 || parts[0] != ANNOUNCEMENT || parts[1] != groupTag) return
        val deviceId = parts[2].takeIf { it != localDeviceId && it.matches(SAFE_ID) } ?: return
        val tcpPort = parts[3].toIntOrNull()?.takeIf { it in 1..65_535 } ?: return
        val protocol = parts[4].toIntOrNull()?.takeIf { it > 0 } ?: return
        mutablePeers.value = mutablePeers.value + (
            deviceId to LanMeshPeer(deviceId, source, tcpPort, protocol, System.currentTimeMillis())
        )
    }

    private fun announcement(port: Int) = "$ANNOUNCEMENT|$groupTag|$localDeviceId|$port|$PROTOCOL_MAJOR"
    private fun broadcast(prefix: String) {
        val message = if (prefix == QUERY) "$QUERY|$groupTag" else prefix
        broadcastAddresses().forEach { send(message, it) }
    }
    private fun send(message: String, address: InetAddress) = runCatching {
        val bytes = message.toByteArray(StandardCharsets.UTF_8)
        socket.send(DatagramPacket(bytes, bytes.size, address, DISCOVERY_PORT))
    }.getOrNull()

    private fun pruneExpired() {
        val cutoff = System.currentTimeMillis() - PEER_EXPIRY_MILLIS
        mutablePeers.value = mutablePeers.value.filterValues { it.lastSeenAtMillis >= cutoff }
    }

    override fun close() {
        advertisedPort = null
        socket.close()
        scope.cancel()
        mutablePeers.value = emptyMap()
    }

    private companion object {
        const val DISCOVERY_PORT = 45_783
        const val PROTOCOL_MAJOR = 2
        const val QUERY = "SDMQ1"
        const val ANNOUNCEMENT = "SDMA1"
        const val RECEIVE_TIMEOUT_MILLIS = 1_000
        const val ANNOUNCEMENT_INTERVAL_MILLIS = 1_000L
        const val PEER_EXPIRY_MILLIS = 15_000L
        val SAFE_ID = Regex("[A-Za-z0-9_-]{8,128}")
    }
}

fun meshLanGroupTag(groupId: String): String =
    MessageDigest.getInstance("SHA-256")
        .digest(groupId.toByteArray(StandardCharsets.UTF_8))
        .copyOfRange(0, 12)
        .let { Base64.getUrlEncoder().withoutPadding().encodeToString(it) }

private fun broadcastAddresses(): Set<InetAddress> = buildSet {
    add(InetAddress.getByName("255.255.255.255"))
    val interfaces = runCatching { NetworkInterface.getNetworkInterfaces() }.getOrNull() ?: return@buildSet
    while (interfaces.hasMoreElements()) {
        val network = interfaces.nextElement()
        if (!runCatching { network.isUp && !network.isLoopback }.getOrDefault(false)) continue
        network.interfaceAddresses.mapNotNullTo(this) { it.broadcast }
    }
}
