package com.syncdroid.app.sync

import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Test

class SyncTimeFormatterTest {
    private val zone = ZoneId.of("Australia/Adelaide")
    private val now = ZonedDateTime.of(2026, 8, 15, 18, 30, 0, 0, zone)

    @Test fun formatsTodayYesterdayAndWeekday() {
        assertEquals("Last synced 17:05 today", label(0, 17, 5))
        assertEquals("Last synced 17:05 yesterday", label(1, 17, 5))
        assertEquals("Last synced 17:05 Wednesday", label(3, 17, 5))
    }

    @Test fun formatsLongerRelativePeriods() {
        assertEquals("Last synced last week", label(8))
        assertEquals("Last synced last month", label(25))
        assertEquals("Last synced 2 months ago", label(60))
        assertEquals("Last synced last year", label(400))
        assertEquals("Last synced 2 years ago", label(730))
    }

    @Test fun formatsNeverAndDetailedTimestamp() {
        assertEquals("Waiting for first sync", relativeLastSyncLabel(null, now))
        val synced = now.minusDays(1).withHour(9).withMinute(7).withSecond(6)
        assertEquals(
            "Friday, 14 August 2026 at 09:07:06",
            detailedSyncTimestamp(synced.toInstant().toEpochMilli(), zone),
        )
    }

    private fun label(daysAgo: Long, hour: Int = 12, minute: Int = 0): String {
        val synced = now.minusDays(daysAgo).withHour(hour).withMinute(minute)
        return relativeLastSyncLabel(synced.toInstant().toEpochMilli(), now)
    }
}
