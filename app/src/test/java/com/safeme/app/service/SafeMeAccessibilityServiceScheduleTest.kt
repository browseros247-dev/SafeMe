package com.safeme.app.service

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Regression coverage for immediate schedule activation checks. */
class SafeMeAccessibilityServiceScheduleTest {

    @Test
    fun forcedScheduleRecheck_bypassesThrottle() {
        val now = 10_000L

        assertFalse(
            shouldThrottleScheduleRecheck(
                lastRecheckMs = now - 1_000L,
                nowMs = now,
                force = true,
            ),
        )
    }

    @Test
    fun normalScheduleRecheck_keepsThrottle() {
        val now = 10_000L

        assertTrue(
            shouldThrottleScheduleRecheck(
                lastRecheckMs = now - 1_000L,
                nowMs = now,
                force = false,
            ),
        )
    }
}
