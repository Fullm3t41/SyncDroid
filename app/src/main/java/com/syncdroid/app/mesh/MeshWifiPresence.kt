package com.syncdroid.app.mesh

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import android.util.Log
import java.security.MessageDigest
import java.util.Base64
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Foreground-only LAN presence used to offer registration of an unapproved Wi-Fi network. */
class MeshWifiPresence(
    context: Context,
    private val localDeviceId: String,
    groupId: String,
) : AutoCloseable {
    private val appContext = context.applicationContext
    private val nsd = appContext.getSystemService(NsdManager::class.java)
    private val wifi = appContext.getSystemService(WifiManager::class.java)
    private val groupTag = meshPresenceGroupTag(groupId)
    private val serviceName = meshPresenceServiceName(groupTag, localDeviceId)
    private val multicastLock = wifi.createMulticastLock("syncdroid-presence-mdns").apply {
        setReferenceCounted(false)
    }
    private val services = mutableMapOf<String, String>()
    private val mutablePeerIds = MutableStateFlow<Set<String>>(emptySet())
    val peerIds: StateFlow<Set<String>> = mutablePeerIds.asStateFlow()
    private var registration: NsdManager.RegistrationListener? = null
    private var discovery: NsdManager.DiscoveryListener? = null

    fun start() {
        check(registration == null && discovery == null) { "Mesh presence is already running" }
        multicastLock.acquire()
        register()
        discover()
    }

    private fun register() {
        val info = NsdServiceInfo().apply {
            serviceName = this@MeshWifiPresence.serviceName
            serviceType = SERVICE_TYPE
            // Presence uses its own service type and never connects to this placeholder port.
            setPort(PRESENCE_ONLY_PORT)
        }
        val listener = object : NsdManager.RegistrationListener {
            override fun onServiceRegistered(serviceInfo: NsdServiceInfo) = Unit
            override fun onRegistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                Log.w(TAG, "Presence registration failed: $errorCode")
            }
            override fun onServiceUnregistered(serviceInfo: NsdServiceInfo) = Unit
            override fun onUnregistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) = Unit
        }
        registration = listener
        nsd.registerService(info, NsdManager.PROTOCOL_DNS_SD, listener)
    }

    private fun discover() {
        val listener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(serviceType: String) = Unit
            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                Log.w(TAG, "Presence discovery failed to start: $errorCode")
                close()
            }
            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
                Log.w(TAG, "Presence discovery failed to stop: $errorCode")
            }
            override fun onDiscoveryStopped(serviceType: String) = Unit
            override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                if (!samePresenceServiceType(serviceInfo.serviceType)) return
                val deviceId = parseMeshPresenceDeviceId(serviceInfo.serviceName, groupTag) ?: return
                if (deviceId == localDeviceId) return
                services[serviceInfo.serviceName] = deviceId
                publish()
            }
            override fun onServiceLost(serviceInfo: NsdServiceInfo) {
                services.remove(serviceInfo.serviceName)
                publish()
            }
        }
        discovery = listener
        nsd.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, listener)
    }

    private fun publish() {
        mutablePeerIds.value = services.values.toSet()
    }

    override fun close() {
        discovery?.let { runCatching { nsd.stopServiceDiscovery(it) } }
        registration?.let { runCatching { nsd.unregisterService(it) } }
        discovery = null
        registration = null
        services.clear()
        mutablePeerIds.value = emptySet()
        if (multicastLock.isHeld) multicastLock.release()
    }

    private companion object {
        const val SERVICE_TYPE = "_syncdroid-presence._tcp."
        const val PRESENCE_ONLY_PORT = 9
        const val TAG = "SyncDroidPresence"
    }
}

internal fun meshPresenceGroupTag(groupId: String): String =
    MessageDigest.getInstance("SHA-256")
        .digest(groupId.toByteArray(Charsets.UTF_8))
        .copyOfRange(0, 9)
        .let { Base64.getUrlEncoder().withoutPadding().encodeToString(it) }

internal fun meshPresenceServiceName(groupTag: String, deviceId: String): String =
    "SyncDroidPresence-$groupTag-$deviceId"

internal fun parseMeshPresenceDeviceId(serviceName: String, groupTag: String): String? {
    val prefix = "SyncDroidPresence-$groupTag-"
    if (!serviceName.startsWith(prefix)) return null
    return serviceName.removePrefix(prefix)
        .substringBefore(" (")
        .takeIf { it.length == DEVICE_ID_LENGTH && it.all(::isDeviceIdCharacter) }
}

private fun isDeviceIdCharacter(character: Char): Boolean =
    character.isLetterOrDigit() || character == '-' || character == '_'

private fun samePresenceServiceType(value: String): Boolean =
    value.trimEnd('.').equals("_syncdroid-presence._tcp", ignoreCase = true)

private const val DEVICE_ID_LENGTH = 24
