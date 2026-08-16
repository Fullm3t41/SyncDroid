package com.syncdroid.shared.sync

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MeshRoutePlannerTest {
    @Test
    fun `five devices form a bounded connected initial graph`() {
        val devices = listOf("a", "b", "c", "d", "e")
        val edges = devices.associateWith { local ->
            initialMeshFanoutTargets(local, devices - local)
        }

        assertTrue(edges.values.all { it.size <= 2 })
        assertEquals(listOf("b", "c"), edges.getValue("a"))
        assertEquals(listOf("c", "d"), edges.getValue("b"))
        assertEquals(listOf("d", "e"), edges.getValue("c"))
        assertEquals(listOf("e"), edges.getValue("d"))
        assertEquals(emptyList(), edges.getValue("e"))
    }

    @Test
    fun `propagation excludes the source and active sessions`() {
        val selected = propagationFanoutTargets(
            localDeviceId = "b",
            sourceDeviceId = "a",
            peers = listOf(
                MeshRouteCandidate("a"),
                MeshRouteCandidate("c", active = true),
                MeshRouteCandidate("d", lastSessionAtMillis = 20),
                MeshRouteCandidate("e", lastSessionAtMillis = 10),
            ),
        )

        assertEquals(listOf("e", "d"), selected)
    }

    @Test
    fun `never contacted devices are preferred for propagation`() {
        val selected = propagationFanoutTargets(
            localDeviceId = "seed",
            sourceDeviceId = null,
            peers = listOf(
                MeshRouteCandidate("old", lastSessionAtMillis = 5_000),
                MeshRouteCandidate("new-a"),
                MeshRouteCandidate("new-b"),
            ),
        )

        assertEquals(setOf("new-a", "new-b"), selected.toSet())
    }
}
