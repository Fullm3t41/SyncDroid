package com.synctosh.app.platform

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class MacWifiTest {
    @Test
    fun `parses current network name`() {
        assertEquals("Home Mesh", MacWifi.parseCurrentNetwork("Current Wi-Fi Network: Home Mesh\n"))
    }

    @Test
    fun `does not mistake disconnected output for an ssid`() {
        assertNull(MacWifi.parseCurrentNetwork("You are not associated with an AirPort network.\n"))
    }
}
