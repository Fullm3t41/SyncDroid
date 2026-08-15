package com.syncdroid.app.wifi

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WifiSyncPolicyTest {
    private val policy = WifiSyncPolicy(
        requireApprovedWifi = true,
        networks = listOf(
            WifiNetworkRule("Home Wi-Fi"),
            WifiNetworkRule("Workshop"),
            WifiNetworkRule("Disabled network", enabled = false),
        ),
    )

    @Test
    fun anyEnabledApprovedNetworkAllowsSync() {
        assertTrue(policy.allowsSync(true, "Home Wi-Fi"))
        assertTrue(policy.allowsSync(true, "Workshop"))
    }

    @Test
    fun disabledUnknownOrDisconnectedNetworkPausesSync() {
        assertFalse(policy.allowsSync(true, "Disabled network"))
        assertFalse(policy.allowsSync(true, "Coffee shop"))
        assertFalse(policy.allowsSync(false, "Home Wi-Fi"))
        assertFalse(policy.allowsSync(true, null))
    }

    @Test
    fun registeredWifiGateCannotBeBypassed() {
        val unrestricted = policy.copy(requireApprovedWifi = false)
        assertFalse(unrestricted.allowsSync(true, "Coffee shop"))
        assertFalse(unrestricted.allowsSync(false, null))
    }

    @Test
    fun ssidsAreCaseSensitive() {
        assertFalse(policy.allowsSync(true, "home wi-fi"))
    }

    @Test
    fun foregroundAppCanSyncOnAnUnregisteredWifi() {
        assertTrue(policy.allowsSyncWithForegroundOverride(true, "Coffee shop", appInForeground = true))
        assertFalse(policy.allowsSyncWithForegroundOverride(true, "Coffee shop", appInForeground = false))
    }

    @Test
    fun foregroundOverrideStillRequiresWifi() {
        assertFalse(policy.allowsSyncWithForegroundOverride(false, null, appInForeground = true))
    }

    @Test
    fun enablingSuggestedNetworkAddsOrReenablesItWithoutDuplicates() {
        val added = policy.withNetworkEnabled("Coffee shop")
        val reenabled = policy.withNetworkEnabled("Disabled network")

        assertTrue(added.allowsSync(true, "Coffee shop"))
        assertTrue(reenabled.allowsSync(true, "Disabled network"))
        assertTrue(reenabled.networks.count { it.ssid == "Disabled network" } == 1)
    }
}
