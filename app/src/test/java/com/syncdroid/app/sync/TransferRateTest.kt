package com.syncdroid.app.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TransferRateTest {
    @Test
    fun `sampler reports bytes per second after its interval`() {
        var now = 0L
        val samples = mutableListOf<Long>()
        val sampler = TransferRateSampler(nanoTime = { now }, onSample = samples::add)

        sampler.record(512 * 1024L)
        now = 500_000_000L
        sampler.record(256 * 1024L)
        assertTrue(samples.isEmpty())

        now = 1_000_000_000L
        sampler.record(256 * 1024L)

        assertEquals(listOf(1024L * 1024L), samples)
    }

    @Test
    fun `formatter chooses a readable binary unit`() {
        assertEquals("768 KB/s", formatTransferRate(768L * 1024L))
        assertEquals("12.5 MB/s", formatTransferRate((12.5 * 1024 * 1024).toLong()))
        assertEquals("1.5 GB/s", formatTransferRate((1.5 * 1024 * 1024 * 1024).toLong()))
    }
}
