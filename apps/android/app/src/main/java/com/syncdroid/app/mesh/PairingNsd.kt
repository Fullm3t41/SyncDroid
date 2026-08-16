package com.syncdroid.app.mesh

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import java.net.InetAddress
import java.nio.charset.StandardCharsets
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class DiscoveredPairingOffer(
    val invitationId: String,
    val deviceId: String,
    val address: InetAddress,
    val port: Int,
    val serviceName: String,
)

class PairingNsd(context: Context, private val localDeviceId: String) : AutoCloseable {
    private val appContext = context.applicationContext
    private val nsd = appContext.getSystemService(NsdManager::class.java)
    private val wifi = appContext.getSystemService(WifiManager::class.java)
    private val multicastLock = wifi.createMulticastLock("syncdroid-pairing-mdns").apply { setReferenceCounted(false) }
    private val mutableOffers = MutableStateFlow<Map<String, DiscoveredPairingOffer>>(emptyMap())
    val offers: StateFlow<Map<String, DiscoveredPairingOffer>> = mutableOffers.asStateFlow()
    private var registration: NsdManager.RegistrationListener? = null
    private var discovery: NsdManager.DiscoveryListener? = null

    fun advertise(port: Int, invitationId: String) {
        acquireLock()
        val info = NsdServiceInfo().apply {
            serviceName = "SyncDroid-Pair-${localDeviceId.take(8)}"
            serviceType = SERVICE_TYPE
            setPort(port)
            setAttribute("id", localDeviceId)
            setAttribute("invite", invitationId)
        }
        val listener = object : NsdManager.RegistrationListener {
            override fun onServiceRegistered(serviceInfo: NsdServiceInfo) = Unit
            override fun onRegistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) = Unit
            override fun onServiceUnregistered(serviceInfo: NsdServiceInfo) = Unit
            override fun onUnregistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) = Unit
        }
        registration = listener
        nsd.registerService(info, NsdManager.PROTOCOL_DNS_SD, listener)
    }

    fun discover() {
        if (discovery != null) return
        acquireLock()
        val listener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(serviceType: String) = Unit
            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) = Unit
            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) = Unit
            override fun onDiscoveryStopped(serviceType: String) = Unit
            override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                if (serviceInfo.serviceType != SERVICE_TYPE) return
                @Suppress("DEPRECATION")
                nsd.resolveService(serviceInfo, object : NsdManager.ResolveListener {
                    override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) = Unit
                    override fun onServiceResolved(resolved: NsdServiceInfo) = accept(resolved)
                })
            }
            override fun onServiceLost(serviceInfo: NsdServiceInfo) {
                mutableOffers.value = mutableOffers.value.filterValues { it.serviceName != serviceInfo.serviceName }
            }
        }
        discovery = listener
        nsd.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, listener)
    }

    private fun accept(info: NsdServiceInfo) {
        val id = info.attributes["id"]?.toString(StandardCharsets.UTF_8) ?: return
        val invitation = info.attributes["invite"]?.toString(StandardCharsets.UTF_8) ?: return
        if (id == localDeviceId || invitation.isBlank()) return
        @Suppress("DEPRECATION")
        val address = info.host ?: return
        mutableOffers.value = mutableOffers.value + (invitation to DiscoveredPairingOffer(
            invitation, id, address, info.port, info.serviceName,
        ))
    }

    private fun acquireLock() {
        if (!multicastLock.isHeld) multicastLock.acquire()
    }

    override fun close() {
        discovery?.let { runCatching { nsd.stopServiceDiscovery(it) } }
        registration?.let { runCatching { nsd.unregisterService(it) } }
        discovery = null
        registration = null
        mutableOffers.value = emptyMap()
        if (multicastLock.isHeld) multicastLock.release()
    }

    private companion object { const val SERVICE_TYPE = "_syncdroid-pair._tcp." }
}
