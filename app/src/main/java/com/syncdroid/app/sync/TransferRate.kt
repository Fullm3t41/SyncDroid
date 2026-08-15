package com.syncdroid.app.sync

import java.util.Locale
import kotlin.math.roundToLong

/** Samples file payload bytes without counting protocol or TLS framing overhead. */
class TransferRateSampler(
    private val sampleIntervalNanos: Long = 1_000_000_000L,
    private val nanoTime: () -> Long = System::nanoTime,
    private val onSample: (bytesPerSecond: Long) -> Unit,
) {
    private var sampleStartedAtNanos: Long? = null
    private var bytesInSample = 0L

    @Synchronized
    fun record(byteCount: Long) {
        require(byteCount >= 0) { "Transferred byte count cannot be negative" }
        val now = nanoTime()
        val startedAt = sampleStartedAtNanos
        if (startedAt == null) {
            sampleStartedAtNanos = now
            bytesInSample = byteCount
            return
        }

        bytesInSample += byteCount
        val elapsedNanos = now - startedAt
        if (elapsedNanos < sampleIntervalNanos) return

        val bytesPerSecond = (bytesInSample.toDouble() * NANOS_PER_SECOND / elapsedNanos)
            .roundToLong()
            .coerceAtLeast(0L)
        bytesInSample = 0L
        sampleStartedAtNanos = now
        onSample(bytesPerSecond)
    }

    private companion object {
        const val NANOS_PER_SECOND = 1_000_000_000.0
    }
}

fun formatTransferRate(bytesPerSecond: Long): String {
    val safeRate = bytesPerSecond.coerceAtLeast(0L)
    return when {
        safeRate >= GIBIBYTE -> formatRate(safeRate.toDouble() / GIBIBYTE, "GB/s")
        safeRate >= MEBIBYTE -> formatRate(safeRate.toDouble() / MEBIBYTE, "MB/s")
        safeRate >= KIBIBYTE -> formatRate(safeRate.toDouble() / KIBIBYTE, "KB/s")
        else -> "$safeRate B/s"
    }
}

private fun formatRate(value: Double, unit: String): String {
    val pattern = if (value >= 100) "%.0f" else "%.1f"
    return "${String.format(Locale.US, pattern, value)} $unit"
}

private const val KIBIBYTE = 1024L
private const val MEBIBYTE = 1024L * KIBIBYTE
private const val GIBIBYTE = 1024L * MEBIBYTE
