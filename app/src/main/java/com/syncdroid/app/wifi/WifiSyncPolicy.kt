package com.syncdroid.app.wifi

data class WifiNetworkRule(
    val ssid: String,
    val enabled: Boolean = true,
)

data class WifiSyncPolicy(
    val requireApprovedWifi: Boolean = true,
    val networks: List<WifiNetworkRule> = emptyList(),
) {
    fun allowsSync(isWifiConnected: Boolean, currentSsid: String?): Boolean {
        if (!isWifiConnected) return false
        if (currentSsid.isNullOrBlank()) return false
        return networks.any { it.enabled && it.ssid == currentSsid }
    }

    fun enabledNetworkCount(): Int = networks.count { it.enabled }
}
