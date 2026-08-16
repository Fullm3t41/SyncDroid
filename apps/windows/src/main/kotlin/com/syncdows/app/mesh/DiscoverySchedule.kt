package com.syncdows.app.mesh

import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime

data class ScheduledDiscoveryWindow(val start: LocalDateTime, val end: LocalDateTime)

val SUPPORTED_DISCOVERY_INTERVALS = listOf(5, 15, 30, 60, 6 * 60, 24 * 60, 48 * 60, 7 * 24 * 60)
val SUPPORTED_DISCOVERY_WINDOWS = listOf(30L, 60L, 120L, 300L)

fun discoveryIntervalLabel(minutes: Int, compact: Boolean = false): String = when {
    minutes == 7 * 24 * 60 -> if (compact) "1 wk" else "1 week"
    minutes % 60 == 0 -> {
        val hours = minutes / 60
        if (compact) "$hours hr" else "$hours ${if (hours == 1) "hour" else "hours"}"
    }
    else -> if (compact) "$minutes min" else "$minutes minutes"
}

fun discoveryWindowLabel(seconds: Long): String = when {
    seconds < 60 -> "$seconds seconds"
    seconds == 60L -> "1 minute"
    else -> "${seconds / 60} minutes"
}

fun currentOrNextDiscoveryWindow(
    now: LocalDateTime,
    intervalMinutes: Int,
    windowSeconds: Long,
): ScheduledDiscoveryWindow {
    require(intervalMinutes > 0 && windowSeconds > 0)
    val anchor = LocalDate.of(1970, 1, 5).atStartOfDay()
    val elapsedMinutes = Duration.between(anchor, now).toMinutes()
    val currentIndex = Math.floorDiv(elapsedMinutes, intervalMinutes.toLong())
    val currentStart = anchor.plusMinutes(currentIndex * intervalMinutes)
    val currentEnd = currentStart.plusSeconds(windowSeconds)
    return if (!now.isBefore(currentStart) && now.isBefore(currentEnd)) {
        ScheduledDiscoveryWindow(currentStart, currentEnd)
    } else {
        val next = currentStart.plusMinutes(intervalMinutes.toLong())
        ScheduledDiscoveryWindow(next, next.plusSeconds(windowSeconds))
    }
}

fun upcomingDiscoveryWindows(
    now: LocalDateTime,
    intervalMinutes: Int,
    windowSeconds: Long,
    count: Int,
): List<ScheduledDiscoveryWindow> {
    require(count >= 0)
    if (count == 0) return emptyList()
    val first = currentOrNextDiscoveryWindow(now, intervalMinutes, windowSeconds).let { window ->
        if (!now.isBefore(window.start) && now.isBefore(window.end)) {
            val next = window.start.plusMinutes(intervalMinutes.toLong())
            ScheduledDiscoveryWindow(next, next.plusSeconds(windowSeconds))
        } else window
    }
    return List(count) { index ->
        val start = first.start.plusMinutes(intervalMinutes.toLong() * index)
        ScheduledDiscoveryWindow(start, start.plusSeconds(windowSeconds))
    }
}
