package com.safeme.app.service

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Regression coverage for the app content-event re-check throttle. */
class SafeMeAccessibilityServiceAppContentRecheckTest {

    @Test
    fun contentFlood_withinThrottleWindow_isThrottled() {
        val now = 10_000L

        assertTrue(
            shouldThrottleAppContentRecheck(
                lastRecheckMs = now - 100L,
                nowMs = now,
                isClick = false,
            ),
        )
    }

    @Test
    fun contentFlood_pastThrottleWindow_isNotThrottled() {
        val now = 10_000L

        assertFalse(
            shouldThrottleAppContentRecheck(
                lastRecheckMs = now - APP_CONTENT_RECHECK_THROTTLE_MS,
                nowMs = now,
                isClick = false,
            ),
        )
    }

    @Test
    fun deliberateClick_bypassesThrottle() {
        val now = 10_000L

        assertFalse(
            shouldThrottleAppContentRecheck(
                lastRecheckMs = now - 1L,
                nowMs = now,
                isClick = true,
            ),
        )
    }

    @Test
    fun firstRecheck_ever_isNotThrottled() {
        val now = 10_000L

        assertFalse(
            shouldThrottleAppContentRecheck(
                lastRecheckMs = 0L,
                nowMs = now,
                isClick = false,
            ),
        )
    }
}
