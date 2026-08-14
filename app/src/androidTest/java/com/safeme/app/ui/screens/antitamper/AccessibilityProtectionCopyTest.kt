package com.safeme.app.ui.screens.antitamper

import android.content.ClipboardManager
import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.safeme.app.ui.theme.SafeMeApp
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Regression test for the ADB command Copy button on the Accessibility
 * Protection screen: tapping it must put the complete one-line command on the
 * system clipboard and briefly switch the label to "Copied".
 */
@RunWith(AndroidJUnit4::class)
class AccessibilityProtectionCopyTest {

    @get:Rule
    val rule = createComposeRule()

    @Test
    fun copyButtonCopiesFullCommandAndShowsFeedback() {
        rule.setContent {
            SafeMeApp {
                AccessibilityProtectionScreen(onBack = {})
            }
        }

        val expected = "adb shell pm grant com.safeme.app android.permission.WRITE_SECURE_SETTINGS"

        // The ADB instruction box is collapsed by default; expand it first.
        // (If the WRITE_SECURE_SETTINGS grant is already present the box is
        // hidden entirely and there is nothing to copy — the assertion below
        // then fails with a clear message rather than a false pass.)
        runCatching {
            rule.onNodeWithText("Show ADB setup instructions").performClick()
        }
        rule.waitUntil(timeoutMillis = 5_000) {
            runCatching { rule.onNodeWithText("Copy").assertIsDisplayed() }.isSuccess
        }

        rule.onNodeWithText("Copy").performClick()

        // Brief "Copied" confirmation appears.
        rule.onNodeWithText("Copied").assertIsDisplayed()

        // The system clipboard holds the complete command.
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        val clipboard = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val item = clipboard.primaryClip?.getItemAt(0)
        assertEquals(
            "Clipboard must contain the complete ADB command",
            expected,
            item?.coerceToText(ctx).toString(),
        )

        // Feedback reverts to "Copy" after the ~2 s window.
        rule.waitUntil(timeoutMillis = 6_000) {
            runCatching { rule.onNodeWithText("Copy").assertIsDisplayed() }.isSuccess
        }
    }
}
