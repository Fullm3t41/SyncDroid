package com.syncdroid.app.mesh

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotEquals
import org.junit.Test

class MeshWifiPresenceTest {
    @Test
    fun presenceNameRevealsDeviceOnlyToTheMatchingMeshTag() {
        val firstGroup = meshPresenceGroupTag("mesh-one")
        val secondGroup = meshPresenceGroupTag("mesh-two")
        val deviceId = "abcdefghijklmnopqrstuvwx"
        val serviceName = meshPresenceServiceName(firstGroup, deviceId)

        assertNotEquals(firstGroup, secondGroup)
        assertEquals(deviceId, parseMeshPresenceDeviceId(serviceName, firstGroup))
        assertNull(parseMeshPresenceDeviceId(serviceName, secondGroup))
    }

    @Test
    fun androidCollisionSuffixDoesNotChangePresenceIdentity() {
        val groupTag = meshPresenceGroupTag("mesh")
        val deviceId = "abcdEFGHijklMNOPqrstUVWX"
        val serviceName = meshPresenceServiceName(groupTag, deviceId) + " (2)"

        assertEquals(deviceId, parseMeshPresenceDeviceId(serviceName, groupTag))
    }
}
