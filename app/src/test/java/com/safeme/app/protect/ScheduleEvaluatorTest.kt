package com.safeme.app.protect

import com.safeme.app.data.ScheduleBlock
import com.safeme.app.data.ScheduleMode
import com.safeme.app.data.scheduleDaysLabel
import com.safeme.app.data.scheduleModeLabel
import com.safeme.app.data.scheduleTimeLabel
import com.safeme.app.data.scheduleWindowLabel
import java.util.Calendar
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Unit tests for the pure Schedule-Based App Blocking decision core. */
class ScheduleEvaluatorTest {

    // ------------------------------------------------------------ helpers

    private fun rule(
        name: String = "Test",
        days: List<Int> = listOf(dayIndexOf(2026, 7, 5)),
        startMinute: Int = 21 * 60,
        endMinute: Int = 23 * 60,
        mode: ScheduleMode = ScheduleMode.BOTH,
        apps: List<String> = listOf("com.tiktok"),
        enabled: Boolean = true,
    ) = ScheduleBlock(
        id = name,
        name = name,
        days = days,
        startMinute = startMinute,
        endMinute = endMinute,
        mode = mode,
        appPackages = apps,
        enabled = enabled,
    )

    /** Prototype day index (0=Mon..6=Sun) of a calendar date. */
    private fun dayIndexOf(year: Int, month0: Int, day: Int): Int {
        val c = Calendar.getInstance().apply {
            clear()
            set(year, month0, day)
        }
        return (c.get(Calendar.DAY_OF_WEEK) - 1 + 6) % 7
    }

    private fun at(year: Int, month0: Int, day: Int, hour: Int, minute: Int): Long =
        Calendar.getInstance().apply {
            clear()
            set(year, month0, day, hour, minute, 0)
        }.timeInMillis

    /** A Wednesday in August 2026 — compute its day index dynamically. */
    private val wednesdayIndex = dayIndexOf(2026, 7, 5)

    // ---------------------------------------------------------- active window

    @Test
    fun evaluate_matchesDayAndWindow() {
        val r = rule(days = listOf(wednesdayIndex), startMinute = 21 * 60, endMinute = 23 * 60)
        val active = ScheduleEvaluator.evaluate(listOf(r), at(2026, 7, 5, 21, 30))
        assertEquals(setOf("com.tiktok"), active.launchBlockedPackages)
        assertEquals(setOf("com.tiktok"), active.internetBlockedPackages)
        assertFalse(active.launchBlockAll)
        assertFalse(active.internetBlockAll)
    }

    @Test
    fun evaluate_ignoresOtherDays() {
        val r = rule(days = listOf(wednesdayIndex))
        // Thursday 2026-08-06 at the same time — must be inactive.
        val active = ScheduleEvaluator.evaluate(listOf(r), at(2026, 7, 6, 21, 30))
        assertFalse(active.hasLaunchBlock)
        assertFalse(active.hasInternetBlock)
    }

    @Test
    fun evaluate_windowIsHalfOpenEndExclusive() {
        val r = rule(startMinute = 21 * 60, endMinute = 23 * 60)
        assertTrue(ScheduleEvaluator.evaluate(listOf(r), at(2026, 7, 5, 22, 59)).hasLaunchBlock)
        assertFalse(ScheduleEvaluator.evaluate(listOf(r), at(2026, 7, 5, 23, 0)).hasLaunchBlock)
    }

    @Test
    fun evaluate_disabledRulesAreSkipped() {
        val r = rule(enabled = false)
        val active = ScheduleEvaluator.evaluate(listOf(r), at(2026, 7, 5, 21, 30))
        assertFalse(active.hasLaunchBlock)
    }

    @Test
    fun evaluate_modeMapsCorrectly() {
        val internet = ScheduleEvaluator.evaluate(
            listOf(rule(mode = ScheduleMode.INTERNET)),
            at(2026, 7, 5, 21, 30),
        )
        assertEquals(setOf("com.tiktok"), internet.internetBlockedPackages)
        assertTrue(internet.launchBlockedPackages.isEmpty())

        val launch = ScheduleEvaluator.evaluate(
            listOf(rule(mode = ScheduleMode.LAUNCH)),
            at(2026, 7, 5, 21, 30),
        )
        assertEquals(setOf("com.tiktok"), launch.launchBlockedPackages)
        assertTrue(launch.internetBlockedPackages.isEmpty())
    }

    @Test
    fun evaluate_emptyAppsBlocksEverything() {
        val r = rule(apps = emptyList())
        val active = ScheduleEvaluator.evaluate(listOf(r), at(2026, 7, 5, 21, 30))
        assertTrue(active.launchBlockAll)
        assertTrue(active.internetBlockAll)
    }

    @Test
    fun evaluate_unionAcrossRules() {
        val r1 = rule(name = "A", apps = listOf("com.tiktok"), mode = ScheduleMode.LAUNCH)
        val r2 = rule(name = "B", apps = listOf("com.instagram"), mode = ScheduleMode.INTERNET)
        val active = ScheduleEvaluator.evaluate(listOf(r1, r2), at(2026, 7, 5, 21, 30))
        assertEquals(setOf("com.tiktok"), active.launchBlockedPackages)
        assertEquals(setOf("com.instagram"), active.internetBlockedPackages)
    }

    @Test
    fun evaluate_emptyRulesInactive() {
        val active = ScheduleEvaluator.evaluate(emptyList(), at(2026, 7, 5, 21, 30))
        assertFalse(active.hasLaunchBlock)
        assertFalse(active.hasInternetBlock)
    }

    @Test
    fun calendarDayOfWeek_mapsMonTo2SunTo1() {
        assertEquals(2, ScheduleEvaluator.calendarDayOfWeek(0)) // Mon
        assertEquals(3, ScheduleEvaluator.calendarDayOfWeek(1)) // Tue
        assertEquals(1, ScheduleEvaluator.calendarDayOfWeek(6)) // Sun
    }

    // ---------------------------------------------------------- next boundary

    @Test
    fun nextBoundary_returnsNextStart() {
        val r = rule(days = listOf(wednesdayIndex), startMinute = 21 * 60, endMinute = 23 * 60)
        // Tuesday evening — boundary is Wednesday 21:00.
        val tue = at(2026, 7, 4, 20, 0)
        val boundary = ScheduleEvaluator.nextBoundary(listOf(r), tue)
        assertEquals(at(2026, 7, 5, 21, 0), boundary)
    }

    @Test
    fun nextBoundary_returnsEndWhenInsideWindow() {
        val r = rule(days = listOf(wednesdayIndex), startMinute = 21 * 60, endMinute = 23 * 60)
        val inside = at(2026, 7, 5, 22, 0)
        val boundary = ScheduleEvaluator.nextBoundary(listOf(r), inside)
        assertEquals(at(2026, 7, 5, 23, 0), boundary)
    }

    @Test
    fun nextBoundary_wrapsToNextWeek() {
        val r = rule(days = listOf(wednesdayIndex), startMinute = 21 * 60, endMinute = 23 * 60)
        // Thursday right after the window — next boundary is next Wednesday.
        val thu = at(2026, 7, 6, 0, 0)
        val boundary = ScheduleEvaluator.nextBoundary(listOf(r), thu)
        assertEquals(at(2026, 7, 12, 21, 0), boundary)
    }

    @Test
    fun nextBoundary_noRulesReturnsMax() {
        assertEquals(Long.MAX_VALUE, ScheduleEvaluator.nextBoundary(emptyList()))
    }

    @Test
    fun nextBoundary_disabledRulesIgnored() {
        val r = rule(enabled = false)
        assertEquals(Long.MAX_VALUE, ScheduleEvaluator.nextBoundary(listOf(r)))
    }

    // ---------------------------------------------------------------- labels

    @Test
    fun daysLabel_dailyForAllSeven() {
        assertEquals("Daily", scheduleDaysLabel(listOf(0, 1, 2, 3, 4, 5, 6)))
    }

    @Test
    fun daysLabel_joinsSortedWithDotSeparator() {
        assertEquals("Mon · Wed · Fri", scheduleDaysLabel(listOf(4, 0, 2)))
        assertEquals("Tue · Sat", scheduleDaysLabel(listOf(1, 5)))
    }

    @Test
    fun daysLabel_emptyAndDeduplicated() {
        assertEquals("", scheduleDaysLabel(emptyList()))
        assertEquals("Mon · Fri", scheduleDaysLabel(listOf(0, 4, 0)))
    }

    @Test
    fun modeLabel_matchesPrototype() {
        assertEquals("Internet + Launch", scheduleModeLabel(ScheduleMode.BOTH))
        assertEquals("Internet blocked", scheduleModeLabel(ScheduleMode.INTERNET))
        assertEquals("Launch blocked", scheduleModeLabel(ScheduleMode.LAUNCH))
    }

    @Test
    fun timeLabel_padsToHhmm() {
        assertEquals("21:00", scheduleTimeLabel(21 * 60))
        assertEquals("07:05", scheduleTimeLabel(7 * 60 + 5))
        assertEquals("00:00", scheduleTimeLabel(0))
        assertEquals("23:59", scheduleTimeLabel(23 * 60 + 59))
    }

    @Test
    fun windowLabel_usesEnDashWithSpaces() {
        assertEquals("21:00 – 23:00", scheduleWindowLabel(21 * 60, 23 * 60))
    }
}
