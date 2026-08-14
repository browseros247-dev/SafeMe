package com.safeme.app

import android.content.Intent
import android.content.IntentFilter
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.lifecycle.Lifecycle
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Regression test for the global Close → Home contract: dismissing the Block
 * screen must always launch the launcher (HOME), never return to the page that
 * triggered the block, and must finish the gate activity.
 *
 * Uses a "website" type gate, but `closeGate()` is shared by every gate type
 * (PU, keyword, website, schedule, title), so this covers them all.
 */
@RunWith(AndroidJUnit4::class)
class BlockGateActivityTest {

    @get:Rule
    val rule = createAndroidComposeRule<BlockGateActivity>()

    @Test
    fun closeButtonGoesHomeAndFinishes() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()

        // Intercept the launcher intent so the test asserts it fired without
        // actually bouncing to the launcher. block=true swallows the launch.
        val homeFilter = IntentFilter(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
        }
        val monitor = instrumentation.addMonitor(homeFilter, null, true)

        // The Close button only becomes clickable after the dwell countdown
        // finishes (persisted setting, default 5 s). Wait for it to unlock.
        rule.waitUntil(timeoutMillis = 20_000) {
            runCatching { rule.onNodeWithText("Close").assertHasClickAction() }.isSuccess
        }
        rule.onNodeWithText("Close").performClick()

        assertTrue(
            "Close must fire a launcher (HOME) intent instead of returning to the blocked page",
            instrumentation.checkMonitorHit(monitor, 1),
        )

        // The gate must finish rather than linger over the blocked page.
        rule.waitUntil(timeoutMillis = 10_000) {
            rule.activityRule.scenario.state == Lifecycle.State.DESTROYED
        }
    }
}
