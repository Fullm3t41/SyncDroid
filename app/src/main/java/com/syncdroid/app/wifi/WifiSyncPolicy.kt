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

    fun allowsSyncWithForegroundOverride(
        isWifiConnected: Boolean,
        currentSsid: String?,
        appInForeground: Boolean,
    ): Boolean = isWifiConnected && (appInForeground || allowsSync(isWifiConnected, currentSsid))

    fun withNetworkEnabled(rawSsid: String): WifiSyncPolicy {
        val ssid = rawSsid.trim().removeSurrounding("\"")
        if (ssid.isEmpty()) return this
        val existingIndex = networks.indexOfFirst { it.ssid == ssid }
        val updated = networks.toMutableList()
        if (existingIndex >= 0) {
            updated[existingIndex] = updated[existingIndex].copy(enabled = true)
        } else {
            updated.add(WifiNetworkRule(ssid))
        }
        return copy(requireApprovedWifi = true, networks = updated)
    }
}
