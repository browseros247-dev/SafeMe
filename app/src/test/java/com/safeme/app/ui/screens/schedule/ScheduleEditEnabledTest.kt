package com.safeme.app.ui.screens.schedule

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScheduleEditEnabledTest {

    @Test
    fun newScheduleDefaultsEnabled() {
        assertTrue(resolveEditedEnabled(editId = null, storedEnabled = null))
    }

    @Test
    fun newScheduleIgnoresAnyStoredValue() {
        assertTrue(resolveEditedEnabled(editId = null, storedEnabled = false))
    }

    @Test
    fun editPreservesDisabled() {
        assertFalse(resolveEditedEnabled(editId = "exams", storedEnabled = false))
    }

    @Test
    fun editPreservesEnabled() {
        assertTrue(resolveEditedEnabled(editId = "exams", storedEnabled = true))
    }

    @Test
    fun editWithMissingScheduleFailsSafeToEnabled() {
        // Schedule deleted elsewhere while editing: updateSchedule() no-ops,
        // and the flag must not strand a phantom disabled state.
        assertTrue(resolveEditedEnabled(editId = "exams", storedEnabled = null))
    }
}
