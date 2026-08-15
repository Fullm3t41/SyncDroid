package com.syncdroid.app.wifi

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.annotation.RequiresApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import java.util.concurrent.ConcurrentHashMap

data class WifiConnectionState(
    val isWifiConnected: Boolean = false,
    val ssid: String? = null,
    val canReadSsid: Boolean = false,
)

fun requiredWifiRuntimePermissions(): Array<String> =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        arrayOf(
            Manifest.permission.NEARBY_WIFI_DEVICES,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.ACCESS_FINE_LOCATION,
        )
    } else {
        arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION)
    }

fun hasWifiRuntimePermission(context: Context): Boolean =
    requiredWifiRuntimePermissions().all {
        context.checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED
    }

@Composable
fun rememberWifiConnectionState(refreshKey: Int = 0): WifiConnectionState {
    val context = LocalContext.current
    var state by remember { mutableStateOf(WifiConnectionState()) }

    DisposableEffect(context, refreshKey) {
        val monitor = WifiConnectionMonitor(context.applicationContext)
        monitor.start { state = it }
        onDispose { monitor.stop() }
    }
    return state
}

class WifiConnectionMonitor(private val context: Context) {
    private val connectivityManager = context.getSystemService(ConnectivityManager::class.java)
    private val mainHandler = Handler(Looper.getMainLooper())
    private var listener: ((WifiConnectionState) -> Unit)? = null
    private var callback: ConnectivityManager.NetworkCallback? = null
    private val wifiNetworks = ConcurrentHashMap.newKeySet<Network>()
    private val knownSsids = ConcurrentHashMap<Network, String>()

    fun start(onChanged: (WifiConnectionState) -> Unit) {
        listener = onChanged
        val permissionGranted = hasWifiRuntimePermission(context)
        val networkCallback = createCallback(permissionGranted)
        callback = networkCallback
        val requestBuilder = NetworkRequest.Builder()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            requestBuilder.clearCapabilities()
        } else {
            // Android 10 has no clearCapabilities(). Remove the platform's default
            // requirements individually so a local-only Wi-Fi network is still observed.
            requestBuilder
                .removeCapability(NetworkCapabilities.NET_CAPABILITY_NOT_RESTRICTED)
                .removeCapability(NetworkCapabilities.NET_CAPABILITY_TRUSTED)
                .removeCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)
        }
        val wifiRequest = requestBuilder
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            .build()
        val connectedWifi = connectedWifiNetwork()
        if (connectedWifi == null) {
            publish(WifiConnectionState(canReadSsid = permissionGranted))
        } else {
            wifiNetworks.add(connectedWifi)
            update(connectedWifi, permissionGranted)
        }
        runCatching { connectivityManager.registerNetworkCallback(wifiRequest, networkCallback) }
            .onFailure { publish(WifiConnectionState(canReadSsid = permissionGranted)) }
    }

    fun stop() {
        callback?.let { runCatching { connectivityManager.unregisterNetworkCallback(it) } }
        callback = null
        wifiNetworks.clear()
        knownSsids.clear()
        listener = null
    }

    private fun createCallback(permissionGranted: Boolean): ConnectivityManager.NetworkCallback {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && permissionGranted) {
            createLocationAwareCallback()
        } else {
            createLegacyCallback(permissionGranted)
        }
    }

    private fun createLegacyCallback(permissionGranted: Boolean): ConnectivityManager.NetworkCallback =
        object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                wifiNetworks.add(network)
                update(network, permissionGranted)
            }

            override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
                wifiNetworks.add(network)
                publish(rememberSsid(network, stateFromCapabilities(capabilities, hasWifiRuntimePermission(context))))
            }

            override fun onLost(network: Network) {
                onWifiLost(network, hasWifiRuntimePermission(context))
            }
        }

    @RequiresApi(Build.VERSION_CODES.S)
    private fun createLocationAwareCallback(): ConnectivityManager.NetworkCallback =
        object : ConnectivityManager.NetworkCallback(FLAG_INCLUDE_LOCATION_INFO) {
            override fun onAvailable(network: Network) {
                wifiNetworks.add(network)
                update(network, true)
            }

            override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
                wifiNetworks.add(network)
                publish(rememberSsid(network, stateFromCapabilities(capabilities, true)))
            }

            override fun onLost(network: Network) {
                onWifiLost(network, true)
            }
        }

    private fun onWifiLost(lostNetwork: Network, permissionGranted: Boolean) {
        wifiNetworks.remove(lostNetwork)
        knownSsids.remove(lostNetwork)
        val network = wifiNetworks.firstOrNull()
        if (network == null) {
            publish(WifiConnectionState(canReadSsid = permissionGranted))
        } else {
            update(network, permissionGranted)
        }
    }

    @Suppress("DEPRECATION")
    private fun connectedWifiNetwork(): Network? = connectivityManager.allNetworks.firstOrNull { network ->
        connectivityManager.getNetworkCapabilities(network)
            ?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
    }

    private fun update(network: Network, permissionGranted: Boolean) {
        val capabilities = connectivityManager.getNetworkCapabilities(network)
        publish(
            capabilities?.let { rememberSsid(network, stateFromCapabilities(it, permissionGranted)) }
                ?: WifiConnectionState(canReadSsid = permissionGranted),
        )
    }

    private fun rememberSsid(network: Network, state: WifiConnectionState): WifiConnectionState {
        state.ssid?.let { knownSsids[network] = it }
        return if (state.ssid == null && state.isWifiConnected) {
            state.copy(ssid = knownSsids[network])
        } else {
            state
        }
    }

    @SuppressLint("MissingPermission")
    @Suppress("DEPRECATION")
    private fun stateFromCapabilities(
        capabilities: NetworkCapabilities,
        permissionGranted: Boolean,
    ): WifiConnectionState {
        val connected = capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
        if (!connected) return WifiConnectionState(canReadSsid = permissionGranted)

        val rawSsid = if (permissionGranted) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val transportSsid = (capabilities.transportInfo as? WifiInfo)?.ssid
                transportSsid?.takeUnless(::isUnknownSsid)
                    ?: context.getSystemService(WifiManager::class.java)?.connectionInfo?.ssid
            } else {
                context.getSystemService(WifiManager::class.java)?.connectionInfo?.ssid
            }
        } else {
            null
        }
        return WifiConnectionState(
            isWifiConnected = true,
            ssid = sanitizeSsid(rawSsid),
            canReadSsid = permissionGranted,
        )
    }

    private fun publish(state: WifiConnectionState) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            listener?.invoke(state)
        } else {
            mainHandler.post { listener?.invoke(state) }
        }
    }

}

private fun sanitizeSsid(rawSsid: String?): String? = rawSsid
    ?.removeSurrounding("\"")
    ?.takeIf { it.isNotBlank() && !isUnknownSsid(it) }

private fun isUnknownSsid(ssid: String): Boolean =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        ssid == WifiManager.UNKNOWN_SSID
    } else {
        ssid == "<unknown ssid>"
    }
