package com.syncdroid.app.sync

import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.math.roundToInt

fun relativeLastSyncLabel(
    lastSyncMillis: Long?,
    now: ZonedDateTime = ZonedDateTime.now(),
): String {
    if (lastSyncMillis == null) return "Waiting for first sync"
    val synced = Instant.ofEpochMilli(lastSyncMillis).atZone(now.zone)
    val days = ChronoUnit.DAYS.between(synced.toLocalDate(), now.toLocalDate()).coerceAtLeast(0)
    val time = synced.format(DateTimeFormatter.ofPattern("HH:mm", Locale.getDefault()))
    val relative = when {
        days == 0L -> "$time today"
        days == 1L -> "$time yesterday"
        days < 7L -> "$time ${synced.format(DateTimeFormatter.ofPattern("EEEE", Locale.getDefault()))}"
        days < 14L -> "last week"
        days < 45L -> "last month"
        days < 345L -> "${(days / 30.0).roundToInt().coerceAtLeast(2)} months ago"
        days < 545L -> "last year"
        else -> "${(days / 365.0).roundToInt().coerceAtLeast(2)} years ago"
    }
    return "Last synced $relative"
}

fun detailedSyncTimestamp(
    lastSyncMillis: Long?,
    zoneId: ZoneId = ZoneId.systemDefault(),
): String = lastSyncMillis?.let {
    Instant.ofEpochMilli(it).atZone(zoneId).format(
        DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy 'at' HH:mm:ss", Locale.getDefault()),
    )
} ?: "Not yet synced"
