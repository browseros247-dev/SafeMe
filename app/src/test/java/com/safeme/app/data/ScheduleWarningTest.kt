package com.safeme.app.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Unit tests for the \"Accessibility Service required\" banner predicate. */
class ScheduleWarningTest {

    private fun block(
        mode: ScheduleMode,
        enabled: Boolean = true,
    ) = ScheduleBlock(
        id = "id",
        name = "n",
        days = listOf(0),
        startMinute = 60,
        endMinute = 120,
        mode = mode,
        appPackages = emptyList(),
        enabled = enabled,
    )

    @Test
    fun requiresAccessibility_onlyLaunchAndBoth() {
        assertTrue(requiresAccessibility(ScheduleMode.LAUNCH))
        assertTrue(requiresAccessibility(ScheduleMode.BOTH))
        assertFalse(requiresAccessibility(ScheduleMode.INTERNET))
    }

    @Test
    fun banner_hiddenWhenNoEnabledScheduleNeedsA11y() {
        assertFalse(shouldShowA11yWarning(emptyList(), a11yEnabled = false, dismissed = false))
        assertFalse(
            shouldShowA11yWarning(
                listOf(block(ScheduleMode.INTERNET)),
                a11yEnabled = false,
                dismissed = false,
            )
        )
    }

    @Test
    fun banner_hiddenWhenOnlyPausedScheduleNeedsA11y() {
        assertFalse(
            shouldShowA11yWarning(
                listOf(block(ScheduleMode.LAUNCH, enabled = false)),
                a11yEnabled = false,
                dismissed = false,
            )
        )
    }

    @Test
    fun banner_visibleWhenEnabledLaunchOrBothScheduleAndA11yOff() {
        assertTrue(
            shouldShowA11yWarning(
                listOf(block(ScheduleMode.LAUNCH)),
                a11yEnabled = false,
                dismissed = false,
            )
        )
        assertTrue(
            shouldShowA11yWarning(
                listOf(block(ScheduleMode.BOTH)),
                a11yEnabled = false,
                dismissed = false,
            )
        )
        // One a11y-requiring schedule among internet-only ones is enough.
        assertTrue(
            shouldShowA11yWarning(
                listOf(block(ScheduleMode.INTERNET), block(ScheduleMode.LAUNCH)),
                a11yEnabled = false,
                dismissed = false,
            )
        )
    }

    @Test
    fun banner_hiddenWhenA11yEnabled() {
        assertFalse(
            shouldShowA11yWarning(
                listOf(block(ScheduleMode.BOTH)),
                a11yEnabled = true,
                dismissed = false,
            )
        )
    }

    @Test
    fun banner_hiddenWhenDismissed() {
        assertFalse(
            shouldShowA11yWarning(
                listOf(block(ScheduleMode.BOTH)),
                a11yEnabled = false,
                dismissed = true,
            )
        )
    }

    // -------------------------------------------- exact-alarm banner predicate

    @Test
    fun exactBanner_hiddenBelowS() {
        assertFalse(
            shouldShowExactAlarmWarning(
                listOf(block(ScheduleMode.BOTH)),
                granted = false,
                dismissed = false,
                sdkInt = 30,
            )
        )
    }

    @Test
    fun exactBanner_hiddenWhenGranted() {
        assertFalse(
            shouldShowExactAlarmWarning(
                listOf(block(ScheduleMode.BOTH)),
                granted = true,
                dismissed = false,
                sdkInt = 34,
            )
        )
    }

    @Test
    fun exactBanner_hiddenWhenDismissed() {
        assertFalse(
            shouldShowExactAlarmWarning(
                listOf(block(ScheduleMode.BOTH)),
                granted = false,
                dismissed = true,
                sdkInt = 34,
            )
        )
    }

    @Test
    fun exactBanner_hiddenWithNoEnabledSchedules() {
        assertFalse(
            shouldShowExactAlarmWarning(
                emptyList(),
                granted = false,
                dismissed = false,
                sdkInt = 34,
            )
        )
        assertFalse(
            shouldShowExactAlarmWarning(
                listOf(block(ScheduleMode.BOTH, enabled = false)),
                granted = false,
                dismissed = false,
                sdkInt = 34,
            )
        )
    }

    @Test
    fun exactBanner_visibleWhenDeniedWithEnabledSchedule() {
        assertTrue(
            shouldShowExactAlarmWarning(
                listOf(block(ScheduleMode.BOTH)),
                granted = false,
                dismissed = false,
                sdkInt = 31,
            )
        )
        // One enabled schedule among paused ones is enough.
        assertTrue(
            shouldShowExactAlarmWarning(
                listOf(block(ScheduleMode.INTERNET, enabled = false), block(ScheduleMode.LAUNCH)),
                granted = false,
                dismissed = false,
                sdkInt = 34,
            )
        )
    }
}
