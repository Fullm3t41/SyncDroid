package com.syncdroid.app.scheduling

import java.time.LocalTime
import java.time.LocalDateTime
import java.time.LocalDate
import java.time.Duration
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

data class DiscoveryWindow(val start: LocalTime, val end: LocalTime) {
    fun label(): String {
        val selected = if (start.second != 0 || end.second != 0) secondFormatter else minuteFormatter
        return "${start.format(selected)}–${end.format(selected)}"
    }
    fun startLabel(): String = start.format(minuteFormatter)

    private companion object {
        val minuteFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
        val secondFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss")
    }
}

/** Returns the next wall-clock boundary, strictly after [now]. */
fun nextRendezvousStart(now: LocalTime, intervalMinutes: Int): LocalTime {
    require(intervalMinutes > 0 && intervalMinutes <= MINUTES_PER_DAY) { "Interval must be between 1 minute and 24 hours" }
    val currentMinute = now.hour * 60 + now.minute
    val nextMinute = ((currentMinute / intervalMinutes) + 1) * intervalMinutes
    return LocalTime.MIDNIGHT.plusMinutes((nextMinute % MINUTES_PER_DAY).toLong())
}

fun nextRendezvousStart(now: LocalDateTime, intervalMinutes: Int): LocalDateTime {
    require(intervalMinutes > 0) { "Interval must be positive" }
    val anchor = RENDEZVOUS_CALENDAR_ANCHOR.atStartOfDay()
    val elapsedWholeMinutes = Duration.between(anchor, now).toMinutes()
    val nextInterval = Math.floorDiv(elapsedWholeMinutes, intervalMinutes.toLong()) + 1
    return anchor.plusMinutes(nextInterval * intervalMinutes)
}

fun millisUntilNextRendezvous(now: LocalDateTime, intervalMinutes: Int): Long =
    Duration.between(now, nextRendezvousStart(now, intervalMinutes)).toMillis().coerceAtLeast(1)

/** Preserves local-midnight rendezvous times when a daylight-saving boundary is crossed. */
fun millisUntilNextRendezvous(now: ZonedDateTime, intervalMinutes: Int): Long {
    val next = nextRendezvousStart(now.toLocalDateTime(), intervalMinutes).atZone(now.zone)
    return Duration.between(now, next).toMillis().coerceAtLeast(1)
}

data class ScheduledDiscoveryWindow(val start: LocalDateTime, val end: LocalDateTime) {
    fun label(includeDate: Boolean): String {
        val formatter = if (includeDate) dateTimeFormatter else minuteFormatter
        val endFormatter = if (includeDate && start.toLocalDate() != end.toLocalDate()) dateTimeFormatter else minuteFormatter
        return "${start.format(formatter)}–${end.format(endFormatter)}"
    }

    fun startLabel(includeDate: Boolean): String =
        start.format(if (includeDate) dateTimeFormatter else minuteFormatter)

    private companion object {
        val minuteFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
        val dateTimeFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE d MMM, HH:mm")
    }
}

fun alignedDiscoveryWindows(
    now: LocalDateTime,
    intervalMinutes: Int,
    windowSeconds: Long = rendezvousWindowSeconds(intervalMinutes),
    count: Int = 3,
): List<ScheduledDiscoveryWindow> {
    require(windowSeconds > 0) { "Window must be positive" }
    require(count >= 0) { "Count cannot be negative" }
    val first = nextRendezvousStart(now, intervalMinutes)
    return List(count) { index ->
        val start = first.plusMinutes(intervalMinutes.toLong() * index)
        ScheduledDiscoveryWindow(start, start.plusSeconds(windowSeconds))
    }
}

fun alignedDiscoveryWindows(
    now: LocalTime,
    intervalMinutes: Int,
    windowSeconds: Long = rendezvousWindowSeconds(intervalMinutes),
    count: Int = 3,
): List<DiscoveryWindow> {
    require(windowSeconds > 0) { "Window must be positive" }
    require(count >= 0) { "Count cannot be negative" }
    val first = nextRendezvousStart(now, intervalMinutes)
    return List(count) { index ->
        val start = first.plusMinutes(intervalMinutes.toLong() * index)
        DiscoveryWindow(start, start.plusSeconds(windowSeconds))
    }
}

fun rendezvousWindowSeconds(intervalMinutes: Int): Long = DiscoveryPolicy.DEFAULT_WINDOW_SECONDS

fun discoveryWindows(
    firstPing: LocalTime,
    intervalMinutes: Int,
    windowMinutes: Int = 5,
    count: Int = 3,
): List<DiscoveryWindow> {
    require(intervalMinutes > 0) { "Interval must be positive" }
    require(windowMinutes > 0) { "Window must be positive" }
    require(count >= 0) { "Count cannot be negative" }

    return List(count) { index ->
        val start = firstPing.plusMinutes(intervalMinutes.toLong() * index)
        DiscoveryWindow(start, start.plusMinutes(windowMinutes.toLong()))
    }
}

private const val MINUTES_PER_DAY = 24 * 60

// Monday at local midnight gives every device the same daily, alternate-day, and weekly phase.
private val RENDEZVOUS_CALENDAR_ANCHOR: LocalDate = LocalDate.of(1970, 1, 5)
