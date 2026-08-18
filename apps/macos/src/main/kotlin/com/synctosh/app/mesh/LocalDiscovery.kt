package com.synctosh.app.mesh

import java.io.Closeable
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.Inet4Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.SocketTimeoutException
import java.nio.charset.StandardCharsets
import javax.jmdns.JmDNS
import javax.jmdns.ServiceEvent
import javax.jmdns.ServiceInfo
import javax.jmdns.ServiceListener
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class PairingOffer(
    val invitationId: String,
    val deviceId: String,
    val address: InetAddress,
    val port: Int,
    val serviceName: String,
)

data class DiscoveredPeer(
    val deviceId: String,
    val address: InetAddress,
    val port: Int,
    val protocolMajor: Int,
    val lastSeenAtMillis: Long,
)

/** Android-compatible UDP pairing discovery fallback. */
class PairingLanDiscovery(private val localDeviceId: String) : Closeable {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val socket = DatagramSocket(null).apply {
        reuseAddress = true
        broadcast = true
        soTimeout = 1_000
        bind(InetSocketAddress(PAIRING_PORT))
    }
    private val mutableOffers = MutableStateFlow<Map<String, PairingOffer>>(emptyMap())
    val offers: StateFlow<Map<String, PairingOffer>> = mutableOffers
    @Volatile private var localOffer: LocalOffer? = null

    init {
        scope.launch { receiveLoop() }
        scope.launch {
            while (isActive) {
                sendBroadcast(DISCOVER.toByteArray(StandardCharsets.UTF_8))
                localOffer?.let { sendBroadcast(it.message().toByteArray(StandardCharsets.UTF_8)) }
                delay(1_000)
            }
        }
    }

    fun advertise(tcpPort: Int, invitationId: String) {
        require(tcpPort in 1..65_535 && invitationId.matches(SAFE_ID))
        localOffer = LocalOffer(tcpPort, invitationId)
    }

    fun stopAdvertising() { localOffer = null }

    private fun receiveLoop() {
        val buffer = ByteArray(512)
        while (!socket.isClosed) {
            try {
                val packet = DatagramPacket(buffer, buffer.size)
                socket.receive(packet)
                val message = String(packet.data, packet.offset, packet.length, StandardCharsets.UTF_8)
                when {
                    message == DISCOVER -> localOffer?.let {
                        send(it.message().toByteArray(StandardCharsets.UTF_8), packet.address, packet.port)
                    }
                    message.startsWith("$OFFER_VERSION|") -> accept(message, packet.address)
                }
            } catch (_: SocketTimeoutException) {
                // Periodically observe closure.
            } catch (_: Throwable) {
                if (socket.isClosed) return
            }
        }
    }

    private fun accept(message: String, source: InetAddress) {
        val parts = message.split('|')
        if (parts.size != 4 || parts[0] != OFFER_VERSION) return
        val invitation = parts[1].takeIf { it.matches(SAFE_ID) } ?: return
        val device = parts[2].takeIf { it.matches(SAFE_ID) && it != localDeviceId } ?: return
        val port = parts[3].toIntOrNull()?.takeIf { it in 1..65_535 } ?: return
        mutableOffers.value = mutableOffers.value + (invitation to PairingOffer(invitation, device, source, port, "LAN pairing"))
    }

    private fun sendBroadcast(bytes: ByteArray) = broadcastAddresses().forEach { send(bytes, it, PAIRING_PORT) }
    private fun send(bytes: ByteArray, address: InetAddress, port: Int) = runCatching {
        socket.send(DatagramPacket(bytes, bytes.size, address, port))
    }.getOrNull()

    override fun close() { localOffer = null; socket.close(); scope.cancel(); mutableOffers.value = emptyMap() }

    private inner class LocalOffer(val port: Int, val invitation: String) {
        fun message() = "$OFFER_VERSION|$invitation|$localDeviceId|$port"
    }

    private companion object {
        const val PAIRING_PORT = 45_782
        const val OFFER_VERSION = "SDPO1"
        const val DISCOVER = "SDPD1"
        val SAFE_ID = Regex("[A-Za-z0-9_-]{8,128}")
    }
}

/** Bonjour/DNS-SD discovery for both ordinary mesh sessions and pairing offers. */
class BonjourDiscovery(private val localDeviceId: String) : Closeable {
    private val mdns = JmDNS.create(localAddress(), "SyncTosh-${localDeviceId.take(8)}")
    private val mutablePairingOffers = MutableStateFlow<Map<String, PairingOffer>>(emptyMap())
    private val mutablePeers = MutableStateFlow<Map<String, DiscoveredPeer>>(emptyMap())
    val pairingOffers: StateFlow<Map<String, PairingOffer>> = mutablePairingOffers
    val peers: StateFlow<Map<String, DiscoveredPeer>> = mutablePeers
    private var pairingService: ServiceInfo? = null
    private var meshService: ServiceInfo? = null
    private var meshPort: Int? = null
    private var meshEnabled = true

    init {
        mdns.addServiceListener(PAIRING_TYPE, listener(PAIRING_TYPE, ::acceptPairing, ::losePairing))
        mdns.addServiceListener(MESH_TYPE, listener(MESH_TYPE, ::acceptMesh, ::loseMesh))
    }

    fun advertisePairing(port: Int, invitationId: String) {
        stopPairingAdvertisement()
        val info = ServiceInfo.create(
            PAIRING_TYPE,
            "SyncTosh-Pair-${localDeviceId.take(8)}",
            port,
            0,
            0,
            mapOf("id" to localDeviceId, "invite" to invitationId),
        )
        mdns.registerService(info)
        pairingService = info
    }

    fun stopPairingAdvertisement() {
        pairingService?.let { runCatching { mdns.unregisterService(it) } }
        pairingService = null
    }

    @Synchronized
    fun advertiseMesh(port: Int) {
        require(port in 1..65_535)
        meshPort = port
        if (meshEnabled) registerMeshService(port)
    }

    @Synchronized
    fun setMeshEnabled(enabled: Boolean) {
        if (meshEnabled == enabled) return
        meshEnabled = enabled
        if (enabled) {
            meshPort?.let(::registerMeshService)
            runCatching { mdns.list(MESH_TYPE, 1_000).forEach(::acceptMesh) }
        } else {
            meshService?.let { runCatching { mdns.unregisterService(it) } }
            meshService = null
        }
    }

    private fun registerMeshService(port: Int) {
        if (meshService != null) return
        val info = ServiceInfo.create(
            MESH_TYPE,
            "SyncTosh-${localDeviceId.take(8)}",
            port,
            0,
            0,
            mapOf("id" to localDeviceId, "v" to "2"),
        )
        mdns.registerService(info)
        meshService = info
    }

    private fun listener(
        type: String,
        accept: (ServiceInfo) -> Unit,
        lose: (String) -> Unit,
    ) = object : ServiceListener {
        override fun serviceAdded(event: ServiceEvent) { mdns.requestServiceInfo(type, event.name, true) }
        override fun serviceResolved(event: ServiceEvent) = accept(event.info)
        override fun serviceRemoved(event: ServiceEvent) = lose(event.name)
    }

    private fun acceptPairing(info: ServiceInfo) {
        val id = info.getPropertyString("id") ?: return
        val invitation = info.getPropertyString("invite") ?: return
        if (id == localDeviceId || !invitation.matches(Regex("[A-Za-z0-9_-]{8,128}"))) return
        val address = info.inet4Addresses.firstOrNull() ?: info.inetAddresses.firstOrNull() ?: return
        mutablePairingOffers.value = mutablePairingOffers.value + (
            invitation to PairingOffer(invitation, id, address, info.port, info.name)
        )
    }

    private fun losePairing(name: String) {
        mutablePairingOffers.value = mutablePairingOffers.value.filterValues { it.serviceName != name }
    }

    private fun acceptMesh(info: ServiceInfo) {
        val id = info.getPropertyString("id") ?: return
        if (id == localDeviceId) return
        val version = info.getPropertyString("v")?.toIntOrNull() ?: return
        val address = info.inet4Addresses.firstOrNull() ?: info.inetAddresses.firstOrNull() ?: return
        mutablePeers.value = mutablePeers.value + (
            id to DiscoveredPeer(id, address, info.port, version, System.currentTimeMillis())
        )
    }

    private fun loseMesh(name: String) {
        // Android service names end with the first eight device-ID characters.
        mutablePeers.value = mutablePeers.value.filterKeys { !name.endsWith(it.take(8), ignoreCase = true) }
    }

    override fun close() {
        stopPairingAdvertisement()
        meshEnabled = false
        meshService?.let { runCatching { mdns.unregisterService(it) } }
        meshService = null
        mdns.close()
    }

    private companion object {
        const val PAIRING_TYPE = "_syncdroid-pair._tcp.local."
        const val MESH_TYPE = "_syncdroid._tcp.local."
    }
}

private fun localAddress(): InetAddress {
    val interfaces = NetworkInterface.getNetworkInterfaces()
    while (interfaces.hasMoreElements()) {
        val network = interfaces.nextElement()
        if (!network.isUp || network.isLoopback || network.isVirtual) continue
        val addresses = network.inetAddresses
        while (addresses.hasMoreElements()) {
            val address = addresses.nextElement()
            if (address is Inet4Address && !address.isLoopbackAddress && !address.isLinkLocalAddress) return address
        }
    }
    return InetAddress.getLoopbackAddress()
}

private fun broadcastAddresses(): Set<InetAddress> = buildSet {
    add(InetAddress.getByName("255.255.255.255"))
    val interfaces = NetworkInterface.getNetworkInterfaces()
    while (interfaces.hasMoreElements()) interfaces.nextElement().interfaceAddresses.mapNotNullTo(this) { it.broadcast }
}
