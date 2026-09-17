package com.safeme.app.service

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Regression coverage for the Social tab-probe throttle. The delivery layers
 * (fast lane / content backstop / watchdog) share the service's whole-gate
 * cooldown fields and are verified on-device; this covers the pure cadence
 * helper they all rely on, in the same style as
 * [SafeMeAccessibilityServiceScheduleTest].
 */
class SafeMeAccessibilityServiceSocialTest {

    @Test
    fun socialTabRecheck_throttlesContentFloods() {
        val now = 50_000L
        assertTrue(
            shouldThrottleSocialTabRecheck(
                lastRecheckMs = now - 100L,
                nowMs = now,
                isClick = false,
            )
        )
    }

    @Test
    fun socialTabRecheck_probesAfterThrottleWindow() {
        val now = 50_000L
        assertFalse(
            shouldThrottleSocialTabRecheck(
                lastRecheckMs = now - 251L,
                nowMs = now,
                isClick = false,
            )
        )
    }

    @Test
    fun socialTabRecheck_clicksAlwaysProbe() {
        val now = 50_000L
        // Tapping the Shorts/Reels/Spotlight tab must gate within one event —
        // deliberate clicks bypass the flood throttle (the 4 s gate cooldown
        // dedupes double-fires).
        assertFalse(
            shouldThrottleSocialTabRecheck(
                lastRecheckMs = now - 1L,
                nowMs = now,
                isClick = true,
            )
        )
    }

    @Test
    fun socialTabRecheck_firstProbeNeverThrottled() {
        // Service starts with lastSocialTabProbeMs = 0 and elapsedRealtime is
        // large, so the very first probe always passes.
        assertFalse(
            shouldThrottleSocialTabRecheck(
                lastRecheckMs = 0L,
                nowMs = 100_000L,
                isClick = false,
            )
        )
    }
}
