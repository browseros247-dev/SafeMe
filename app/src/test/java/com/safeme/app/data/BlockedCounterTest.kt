package com.safeme.app.data

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.ZoneId

class BlockedCounterTest {

    // blockedDateKey — fixed epochs, exact ISO dates.

    @Test
    fun dateKeyFormatsUtcDate() {
        // 2026-09-09T00:00:00Z
        assertEquals("2026-09-09", blockedDateKey(1788912000000L, ZoneId.of("UTC")))
    }

    @Test
    fun dateKeyRespectsNonUtcZone() {
        // 2026-09-08T18:00:00Z is still Sep 8 in UTC but already Sep 9 in Dhaka (+06:00).
        assertEquals("2026-09-08", blockedDateKey(1788890400000L, ZoneId.of("UTC")))
        assertEquals("2026-09-09", blockedDateKey(1788890400000L, ZoneId.of("Asia/Dhaka")))
    }

    @Test
    fun dateKeyRollsAtMidnightBoundary() {
        // 23:59:59.999 vs 00:00:00.000 UTC.
        assertEquals("2026-09-08", blockedDateKey(1788911999999L, ZoneId.of("UTC")))
        assertEquals("2026-09-09", blockedDateKey(1788912000000L, ZoneId.of("UTC")))
    }

    // rolloverBlockedCount — read-side mapping.

    @Test
    fun rolloverKeepsSameDayCount() {
        assertEquals("2026-09-09" to 7, rolloverBlockedCount("2026-09-09", 7, "2026-09-09"))
    }

    @Test
    fun rolloverResetsStaleDate() {
        assertEquals("2026-09-09" to 0, rolloverBlockedCount("2026-09-08", 42, "2026-09-09"))
    }

    @Test
    fun rolloverResetsMissingDate() {
        // Legacy installs have a count but no date key: one-time visual reset.
        assertEquals("2026-09-09" to 0, rolloverBlockedCount(null, 1234, "2026-09-09"))
    }

    @Test
    fun rolloverCoercesGarbageAndFutureDates() {
        assertEquals("2026-09-09" to 0, rolloverBlockedCount("not-a-date", 5, "2026-09-09"))
        assertEquals("2026-09-09" to 0, rolloverBlockedCount("", 5, "2026-09-09"))
        assertEquals("2026-09-09" to 0, rolloverBlockedCount("2026-09-10", 5, "2026-09-09"))
    }

    @Test
    fun rolloverClampsNegativeCount() {
        assertEquals("2026-09-09" to 0, rolloverBlockedCount("2026-09-09", -3, "2026-09-09"))
    }

    // nextBlockedCounter — write-side increment.

    @Test
    fun incrementSameDayAddsOne() {
        assertEquals("2026-09-09" to 1, nextBlockedCounter("2026-09-09", 0, "2026-09-09"))
        assertEquals("2026-09-09" to 8, nextBlockedCounter("2026-09-09", 7, "2026-09-09"))
    }

    @Test
    fun incrementAfterMidnightStartsAtOne() {
        assertEquals("2026-09-09" to 1, nextBlockedCounter("2026-09-08", 42, "2026-09-09"))
        assertEquals("2026-09-09" to 1, nextBlockedCounter(null, 42, "2026-09-09"))
    }

    @Test
    fun incrementClampsNegativeCount() {
        assertEquals("2026-09-09" to 1, nextBlockedCounter("2026-09-09", -3, "2026-09-09"))
    }

    @Test
    fun incrementOutputDateAlwaysEqualsToday() {
        val cases = listOf(
            Triple("2026-09-09", 0, "2026-09-09"),
            Triple("2026-09-08", 99, "2026-09-09"),
            Triple(null, 5, "2026-09-09"),
            Triple("garbage", 5, "2026-09-09"),
        )
        for ((storedDate, storedCount, today) in cases) {
            assertEquals(today, nextBlockedCounter(storedDate, storedCount, today).first)
            assertEquals(today, rolloverBlockedCount(storedDate, storedCount, today).first)
        }
    }
}
