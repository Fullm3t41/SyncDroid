package com.syncdroid.app.scheduling

import java.time.LocalTime
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class DiscoveryScheduleTest {
    @Test
    fun fiveMinutePingsCreateOverlappingRendezvousCoverage() {
        val windows = discoveryWindows(LocalTime.of(18, 0), intervalMinutes = 5, count = 3)

        assertEquals(listOf("18:00–18:05", "18:05–18:10", "18:10–18:15"), windows.map { it.label() })
    }

    @Test
    fun intervalMustBePositive() {
        assertThrows(IllegalArgumentException::class.java) {
            discoveryWindows(LocalTime.NOON, intervalMinutes = 0)
        }
    }

    @Test
    fun fifteenMinuteScheduleStartsAtNextSharedClockBoundary() {
        val windows = alignedDiscoveryWindows(LocalTime.of(18, 7, 30), intervalMinutes = 15, count = 4)

        assertEquals(
            listOf("18:15–18:20", "18:30–18:35", "18:45–18:50", "19:00–19:05"),
            windows.map { it.label() },
        )
    }

    @Test
    fun fifteenMinuteScheduleUsesQuarterHourBoundariesAndFiveMinuteWindows() {
        val windows = alignedDiscoveryWindows(LocalTime.of(9, 1), intervalMinutes = 15, count = 3)

        assertEquals(
            listOf("09:15–09:20", "09:30–09:35", "09:45–09:50"),
            windows.map { it.label() },
        )
    }

    @Test
    fun exactBoundaryAdvancesToNextInterval() {
        assertEquals(LocalTime.of(18, 30), nextRendezvousStart(LocalTime.of(18, 15), 15))
    }

    @Test
    fun nextBoundaryRollsIntoTomorrow() {
        val now = LocalDateTime.of(2026, 8, 15, 23, 59, 30)

        assertEquals(LocalDateTime.of(2026, 8, 16, 0, 0), nextRendezvousStart(now, 15))
        assertEquals(30_000L, millisUntilNextRendezvous(now, 15))
    }

    @Test
    fun sixHourScheduleUsesMidnightBasedBoundaries() {
        val now = LocalDateTime.of(2026, 8, 15, 7, 42)

        assertEquals(LocalDateTime.of(2026, 8, 15, 12, 0), nextRendezvousStart(now, 6 * 60))
    }

    @Test
    fun dailyScheduleAlwaysReturnsMidnight() {
        val now = LocalDateTime.of(2026, 8, 15, 7, 42)

        assertEquals(LocalDateTime.of(2026, 8, 16, 0, 0), nextRendezvousStart(now, 24 * 60))
    }

    @Test
    fun fortyEightHourScheduleUsesSharedAlternatingMidnights() {
        val now = LocalDateTime.of(2026, 8, 15, 7, 42)

        assertEquals(LocalDateTime.of(2026, 8, 17, 0, 0), nextRendezvousStart(now, 48 * 60))
    }

    @Test
    fun weeklyScheduleUsesMondayMidnight() {
        val now = LocalDateTime.of(2026, 8, 15, 7, 42)

        assertEquals(LocalDateTime.of(2026, 8, 17, 0, 0), nextRendezvousStart(now, 7 * 24 * 60))
    }

    @Test
    fun upcomingWindowsRemainTheNextFutureCadencePoints() {
        val now = LocalDateTime.of(2026, 8, 15, 7, 42)
        val windows = alignedDiscoveryWindows(now, intervalMinutes = 48 * 60, count = 3)

        assertEquals(
            listOf(
                LocalDateTime.of(2026, 8, 17, 0, 0),
                LocalDateTime.of(2026, 8, 19, 0, 0),
                LocalDateTime.of(2026, 8, 21, 0, 0),
            ),
            windows.map { it.start },
        )
    }

    @Test
    fun zonedDelayPreservesMidnightAcrossDaylightSavingChange() {
        val adelaide = ZoneId.of("Australia/Adelaide")
        val now = ZonedDateTime.of(2026, 10, 4, 0, 0, 0, 0, adelaide)

        assertEquals(47 * 60 * 60 * 1_000L, millisUntilNextRendezvous(now, 48 * 60))
    }

    @Test
    fun cadenceDefaultsCanBeOverridden() {
        assertEquals(3 * 60, DiscoveryPolicy().intervalMinutes)
        assertEquals(300L, DiscoveryPolicy(intervalMinutes = 15).windowSeconds)
        assertEquals(
            600L,
            DiscoveryPolicy(intervalMinutes = 15, windowSecondsOverride = 600).windowSeconds,
        )
        assertEquals(listOf(300L, 600L, 900L), DiscoveryPolicy.SUPPORTED_WINDOWS_SECONDS.sorted())
        assertEquals(listOf(15, 30, 60, 180, 360, 1_440, 2_880, 10_080), DiscoveryPolicy.SUPPORTED_INTERVALS.sorted())
    }

    @Test
    fun policySupportsExtendedIntervals() {
        assertEquals(300L, DiscoveryPolicy(intervalMinutes = 6 * 60).windowSeconds)
        assertEquals(300L, DiscoveryPolicy(intervalMinutes = 24 * 60).windowSeconds)
        assertEquals(300L, DiscoveryPolicy(intervalMinutes = 48 * 60).windowSeconds)
        assertEquals(300L, DiscoveryPolicy(intervalMinutes = 7 * 24 * 60).windowSeconds)
    }
}
