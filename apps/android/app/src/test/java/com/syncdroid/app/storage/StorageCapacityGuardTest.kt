package com.syncdroid.app.storage

import org.junit.Assert.assertEquals
import org.junit.Test

class StorageCapacityGuardTest {
    @Test
    fun `warning threshold uses five percent when it is below ten GiB`() {
        assertEquals(5 * GIB, lowStorageWarningThreshold(100 * GIB))
    }

    @Test
    fun `warning threshold is capped at ten GiB`() {
        assertEquals(10 * GIB, lowStorageWarningThreshold(1_000 * GIB))
    }

    @Test
    fun `capacity at the threshold remains available`() {
        assertEquals(
            StorageCapacityState.AVAILABLE,
            classifyStorageCapacity(totalBytes = 100 * GIB, availableBytes = 5 * GIB),
        )
    }

    @Test
    fun `capacity below the threshold is low`() {
        assertEquals(
            StorageCapacityState.LOW,
            classifyStorageCapacity(totalBytes = 100 * GIB, availableBytes = 5 * GIB - 1),
        )
    }

    @Test
    fun `zero available capacity is full`() {
        assertEquals(
            StorageCapacityState.FULL,
            classifyStorageCapacity(totalBytes = 100 * GIB, availableBytes = 0),
        )
    }

    private companion object {
        const val GIB = 1024L * 1024 * 1024
    }
}
