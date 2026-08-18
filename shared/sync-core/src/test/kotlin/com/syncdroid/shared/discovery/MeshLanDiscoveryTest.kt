package com.syncdroid.shared.discovery

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MeshLanDiscoveryTest {
    @Test
    fun `group tags are stable without exposing the group id`() {
        val groupId = "private-mesh-identifier"
        val tag = meshLanGroupTag(groupId)

        assertEquals(tag, meshLanGroupTag(groupId))
        assertNotEquals(groupId, tag)
        assertNotEquals(tag, meshLanGroupTag("another-mesh"))
    }

    @Test
    fun `discovery releases its socket outside an active window`() {
        val discovery = MeshLanDiscovery("device-identifier-1234", "private-mesh-identifier", discoveryPort = 0)
        try {
            assertFalse(discovery.isRunning)
            discovery.start(42_424)
            assertTrue(discovery.isRunning)
            discovery.stop()
            assertFalse(discovery.isRunning)
        } finally {
            discovery.close()
        }
    }
}
