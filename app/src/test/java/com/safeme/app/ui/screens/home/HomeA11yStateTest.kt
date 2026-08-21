package com.safeme.app.ui.screens.home

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeA11yStateTest {

    @Test
    fun transientNegativeReadDoesNotConfirmDisabled() {
        assertFalse(isStableA11yDisabled(listOf(false, false, true), requiredReads = 4))
    }

    @Test
    fun consecutiveNegativeReadsConfirmDisabled() {
        assertTrue(isStableA11yDisabled(listOf(false, false, false, false), requiredReads = 4))
    }

    @Test
    fun positiveReadPreventsDisabledConfirmation() {
        assertFalse(isStableA11yDisabled(listOf(true), requiredReads = 4))
    }

    @Test
    fun incompleteReadSequenceDoesNotConfirmDisabled() {
        assertFalse(isStableA11yDisabled(emptyList(), requiredReads = 4))
    }
}
