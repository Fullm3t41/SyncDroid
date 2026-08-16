package com.syncdows.app.platform

import kotlin.test.Test
import kotlin.test.assertEquals

class WindowsWifiTest {
    @Test
    fun parsesTheConnectedSsidWithoutMistakingBssid() {
        val netsh = """
            There is 1 interface on the system:

                Name                   : Wi-Fi
                State                  : connected
                SSID                   : Upstairs 6 GHz
                BSSID                  : aa:bb:cc:dd:ee:ff
        """.trimIndent()

        assertEquals("Upstairs 6 GHz", WindowsWifi.parseSsid(netsh))
    }

    @Test
    fun reportsNoSsidWhenDisconnected() {
        assertEquals(null, WindowsWifi.parseSsid("State : disconnected\nRadio status : Hardware On"))
    }
}
