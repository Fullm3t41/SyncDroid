package com.synctosh.app.mesh

import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime

data class ScheduledDiscoveryWindow(val start: LocalDateTime, val end: LocalDateTime)

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
