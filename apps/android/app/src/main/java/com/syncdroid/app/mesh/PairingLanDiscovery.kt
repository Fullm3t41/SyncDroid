package com.syncdroid.app.mesh

import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.SocketTimeoutException
import java.nio.charset.StandardCharsets
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** Local broadcast fallback for devices whose Android NSD resolver cannot return an mDNS host. */
class PairingLanDiscovery(private val localDeviceId: String) : AutoCloseable {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val socket = DatagramSocket(null).apply {
        reuseAddress = true
        broadcast = true
        soTimeout = RECEIVE_TIMEOUT_MILLIS
        bind(InetSocketAddress(PAIRING_DISCOVERY_PORT))
    }
    private val mutableOffers = kotlinx.coroutines.flow.MutableStateFlow<Map<String, DiscoveredPairingOffer>>(emptyMap())
    val offers: kotlinx.coroutines.flow.StateFlow<Map<String, DiscoveredPairingOffer>> = mutableOffers
    @Volatile private var localOffer: LocalOffer? = null

    init {
        scope.launch { receiveLoop() }
        scope.launch {
            while (isActive) {
                sendDiscoveryProbe()
                delay(PROBE_INTERVAL_MILLIS)
            }
        }
    }

    fun advertise(tcpPort: Int, invitationId: String) {
        require(tcpPort in 1..65_535 && invitationId.isNotBlank())
        localOffer = LocalOffer(tcpPort, invitationId)
        scope.launch { broadcastOffer(localOffer ?: return@launch) }
    }

    private fun receiveLoop() {
        val buffer = ByteArray(MAX_PACKET_BYTES)
        while (!socket.isClosed) {
            try {
                val packet = DatagramPacket(buffer, buffer.size)
                socket.receive(packet)
                val message = String(packet.data, packet.offset, packet.length, StandardCharsets.UTF_8)
                when {
                    message == DISCOVER_MESSAGE -> localOffer?.let { offer ->
                        sendOffer(offer, packet.address, packet.port)
                    }
                    message.startsWith(OFFER_PREFIX) -> acceptOffer(message, packet.address)
                }
            } catch (_: SocketTimeoutException) {
                // Wake periodically so close and cancellation are observed.
            } catch (_: Throwable) {
                if (!socket.isClosed) continue
            }
        }
    }

    private fun acceptOffer(message: String, source: InetAddress) {
        val parts = message.split('|')
        if (parts.size != 4 || parts[0] != OFFER_VERSION) return
        val invitationId = parts[1].takeIf { it.matches(SAFE_ID) } ?: return
        val deviceId = parts[2].takeIf { it.matches(SAFE_ID) && it != localDeviceId } ?: return
        val port = parts[3].toIntOrNull()?.takeIf { it in 1..65_535 } ?: return
        mutableOffers.value = mutableOffers.value + (invitationId to DiscoveredPairingOffer(
            invitationId = invitationId,
            deviceId = deviceId,
            address = source,
            port = port,
            serviceName = "SyncDroid-Mesh LAN pairing",
        ))
    }

    private fun sendDiscoveryProbe() {
        broadcast(DISCOVER_MESSAGE.toByteArray(StandardCharsets.UTF_8))
    }

    private fun broadcastOffer(offer: LocalOffer) {
        val message = offerMessage(offer).toByteArray(StandardCharsets.UTF_8)
        broadcast(message)
    }

    private fun broadcast(bytes: ByteArray) {
        broadcastAddresses().forEach { address -> send(bytes, address, PAIRING_DISCOVERY_PORT) }
    }

    private fun sendOffer(offer: LocalOffer, address: InetAddress, port: Int) {
        send(offerMessage(offer).toByteArray(StandardCharsets.UTF_8), address, port)
    }

    private fun send(bytes: ByteArray, address: InetAddress, port: Int) {
        runCatching { socket.send(DatagramPacket(bytes, bytes.size, address, port)) }
    }

    private fun offerMessage(offer: LocalOffer): String =
        "$OFFER_VERSION|${offer.invitationId}|$localDeviceId|${offer.tcpPort}"

    override fun close() {
        localOffer = null
        socket.close()
        scope.cancel()
        mutableOffers.value = emptyMap()
    }

    private data class LocalOffer(val tcpPort: Int, val invitationId: String)

    private companion object {
        const val PAIRING_DISCOVERY_PORT = 45_782
        const val OFFER_VERSION = "SDPO1"
        const val OFFER_PREFIX = "$OFFER_VERSION|"
        const val DISCOVER_MESSAGE = "SDPD1"
        const val MAX_PACKET_BYTES = 512
        const val RECEIVE_TIMEOUT_MILLIS = 1_000
        const val PROBE_INTERVAL_MILLIS = 1_000L
        val SAFE_ID = Regex("[A-Za-z0-9_-]{8,128}")

        fun broadcastAddresses(): Set<InetAddress> = buildSet {
            add(InetAddress.getByName("255.255.255.255"))
            runCatching {
                val interfaces = NetworkInterface.getNetworkInterfaces()
                while (interfaces.hasMoreElements()) {
                    interfaces.nextElement().interfaceAddresses.mapNotNullTo(this) { it.broadcast }
                }
            }
        }
    }
}
