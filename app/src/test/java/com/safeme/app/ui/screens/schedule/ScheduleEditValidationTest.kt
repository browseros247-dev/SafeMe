package com.safeme.app.ui.screens.schedule

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** B10: window validation — only coinciding start/end is rejected. */
class ScheduleEditValidationTest {

    @Test
    fun window_equalIsInvalid() {
        assertFalse(isValidScheduleWindow(22 * 60, 22 * 60))
        assertFalse(isValidScheduleWindow(0, 0))
    }

    @Test
    fun window_normalIsValid() {
        assertTrue(isValidScheduleWindow(21 * 60, 23 * 60))
    }

    @Test
    fun window_wrapIsValid() {
        assertTrue(isValidScheduleWindow(22 * 60, 7 * 60))
    }
}
