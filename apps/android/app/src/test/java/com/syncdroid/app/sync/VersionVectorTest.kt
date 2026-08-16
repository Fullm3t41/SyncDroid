package com.syncdroid.app.sync

import org.junit.Assert.assertEquals
import org.junit.Test

class VersionVectorTest {
    @Test fun ordersCausalUpdates() {
        val first = VersionVector().increment("phone")
        val second = first.increment("tablet")
        assertEquals(CausalRelation.Before, first.relationTo(second))
        assertEquals(CausalRelation.After, second.relationTo(first))
    }

    @Test fun detectsConcurrentUpdates() {
        val phone = VersionVector(mapOf("phone" to 2))
        val tablet = VersionVector(mapOf("tablet" to 3))
        assertEquals(CausalRelation.Concurrent, phone.relationTo(tablet))
    }

    @Test fun mergeKeepsHighestCounterForEveryDevice() {
        val merged = VersionVector(mapOf("phone" to 2, "tablet" to 1))
            .merge(VersionVector(mapOf("phone" to 1, "tablet" to 4)))
        assertEquals(mapOf("phone" to 2L, "tablet" to 4L), merged.counters)
    }

    @Test fun jsonRoundTrip() {
        val vector = VersionVector(mapOf("phone" to 7, "tablet" to 4))
        assertEquals(vector, VersionVector.fromJson(vector.toJson()))
    }
}
