package com.safeme.app.service

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [isOwnUiEvent] — the guard that prevents SafeMe from
 * gating its own UI (keyword editor, exclude-apps sheet, gate "Why:" text
 * all legitimately contain blocklist words).
 */
class SafeMeAccessibilityServiceOwnUiGuardTest {

    private val ownPackage = "com.safeme.app"
    private val ime = "com.google.android.inputmethod.latin"
    private val chrome = "com.android.chrome"

    @Test
    fun ownEvent_isSuppressed() {
        assertTrue(isOwnUiEvent(ownPackage, ownPackage, chrome))
    }

    @Test
    fun foreignEvent_overOwnForeground_isSuppressed() {
        // IME keystrokes while SafeMe's keyword editor is foreground.
        assertTrue(isOwnUiEvent(ownPackage, ime, ownPackage))
    }

    @Test
    fun foreignEvent_overForeignForeground_notSuppressed() {
        assertFalse(isOwnUiEvent(ownPackage, ime, chrome))
        assertFalse(isOwnUiEvent(ownPackage, chrome, ime))
    }

    @Test
    fun nullPackages_neverSuppressed() {
        assertFalse(isOwnUiEvent(null, ownPackage, ownPackage))
        assertFalse(isOwnUiEvent(ownPackage, null, null))
        assertFalse(isOwnUiEvent(null, null, null))
    }
}
