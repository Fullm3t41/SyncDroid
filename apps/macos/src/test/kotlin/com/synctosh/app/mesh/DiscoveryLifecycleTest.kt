package com.synctosh.app.mesh

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DiscoveryLifecycleTest {
    @Test
    fun pairingUdpSocketExistsOnlyWhileEnabled() {
        val discovery = PairingLanDiscovery("device-identifier-1234", discoveryPort = 0)
        try {
            assertFalse(discovery.isRunning)
            discovery.setEnabled(true)
            assertTrue(discovery.isRunning)
            discovery.setEnabled(false)
            assertFalse(discovery.isRunning)
        } finally {
            discovery.close()
        }
    }
}
