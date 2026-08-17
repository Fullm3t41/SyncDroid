package com.syncdroid.shared.discovery

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class MeshLanDiscoveryTest {
    @Test
    fun `group tags are stable without exposing the group id`() {
        val groupId = "private-mesh-identifier"
        val tag = meshLanGroupTag(groupId)

        assertEquals(tag, meshLanGroupTag(groupId))
        assertNotEquals(groupId, tag)
        assertNotEquals(tag, meshLanGroupTag("another-mesh"))
    }
}
