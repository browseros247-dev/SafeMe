package com.safeme.app.protect

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProtectedSystemPagesTest {

    // ---- isSettingsPackage ----

    @Test
    fun aospSettingsIsRecognized() {
        assertTrue(ProtectedSystemPages.isSettingsPackage("com.android.settings"))
    }

    @Test
    fun settingsSubpackageIsRecognized() {
        assertTrue(ProtectedSystemPages.isSettingsPackage("com.android.settings.foo"))
    }

    @Test
    fun unrelatedPackageIsNotSettings() {
        assertFalse(ProtectedSystemPages.isSettingsPackage("com.other.app"))
        assertFalse(ProtectedSystemPages.isSettingsPackage("com.android.chrome"))
        assertFalse(ProtectedSystemPages.isSettingsPackage(""))
    }

    // ---- isAccessibilityManagementScreen ----

    @Test
    fun aospAccessibilitySettingsScreenIsManagement() {
        assertTrue(
            ProtectedSystemPages.isAccessibilityManagementScreen(
                "com.android.settings",
                "com.android.settings.Settings\$AccessibilitySettingsActivity",
            )
        )
    }

    @Test
    fun aospAccessibilityServiceDetailFragmentIsManagement() {
        assertTrue(
            ProtectedSystemPages.isAccessibilityManagementScreen(
                "com.android.settings",
                "com.android.settings.accessibility.ToggleAccessibilityServicePreferenceFragment",
            )
        )
    }

    @Test
    fun blankClassNameIsNeverManagement() {
        assertFalse(
            ProtectedSystemPages.isAccessibilityManagementScreen(
                "com.android.settings",
                "",
            )
        )
    }

    @Test
    fun uninstallerActivityIsNotManagement() {
        assertFalse(
            ProtectedSystemPages.isAccessibilityManagementScreen(
                "com.android.settings",
                "com.android.settings.UninstallerActivity",
            )
        )
    }

    @Test
    fun nonSettingsPackageIsNotManagement() {
        assertFalse(
            ProtectedSystemPages.isAccessibilityManagementScreen(
                "com.other.app",
                "com.other.app.accessibility.AccessibilityService",
            )
        )
    }

    @Test
    fun appInfoPageIsNotManagement() {
        assertFalse(
            ProtectedSystemPages.isAccessibilityManagementScreen(
                "com.android.settings",
                "com.android.settings.applications.AppInfoDashboardActivity",
            )
        )
    }

    // ---- pageTextMatchesOurService ----

    @Test
    fun pageTextContainingDescriptionPrefixMatches() {
        val description = "SafeMe uses accessibility services to detect and block " +
            "content that distracts you while the app is active."
        val normalized = ProtectedSystemPages.normalize(description)
        val pageText = ProtectedSystemPages.normalize(
            "SafeMe uses accessibility services to detect and block content that " +
                "distracts you while the app is active. SafeMe also uses it to protect " +
                "itself from being disabled or uninstalled."
        )
        assertTrue(ProtectedSystemPages.pageTextMatchesOurService(pageText, normalized))
    }

    @Test
    fun unrelatedPageTextDoesNotMatch() {
        val description = "SafeMe uses accessibility services to detect and block " +
            "content that distracts you while the app is active."
        val normalized = ProtectedSystemPages.normalize(description)
        val pageText = ProtectedSystemPages.normalize("Some other app accessibility service")
        assertFalse(ProtectedSystemPages.pageTextMatchesOurService(pageText, normalized))
    }

    // ---- detailOnlyFingerprint ----

    @Test
    fun fingerprintIsDescriptionSuffixWhenDescriptionStartsWithSummary() {
        val summary = "SafeMe uses accessibility services to detect and block " +
            "content that distracts you while the app is active."
        val description = summary + " SafeMe also uses it to protect itself from being " +
            "disabled or uninstalled while Prevent Uninstall is on, and to block the " +
            "settings pages that manage it."
        val fingerprint = ProtectedSystemPages.detailOnlyFingerprint(
            ProtectedSystemPages.normalize(description),
            ProtectedSystemPages.normalize(summary),
        )
        assertTrue(fingerprint.isNotEmpty())
        // The fingerprint is the text that appears ONLY on the detail page.
        assertFalse(ProtectedSystemPages.normalize(summary).contains(fingerprint))
        assertTrue(ProtectedSystemPages.normalize(description).contains(fingerprint))
    }

    @Test
    fun shortDescriptionYieldsEmptyFingerprint() {
        val fingerprint = ProtectedSystemPages.detailOnlyFingerprint("short", "shor")
        assertEquals("", fingerprint)
    }

    @Test
    fun shortSummaryFallsBackToDropPrefix() {
        val description = "a very long service description that is definitely more " +
            "than forty characters long so the drop prefix path is exercised fully"
        val fingerprint = ProtectedSystemPages.detailOnlyFingerprint(
            ProtectedSystemPages.normalize(description),
            ProtectedSystemPages.normalize("tiny"),
        )
        // Summary is too short to strip, so the first 40 chars are dropped.
        assertFalse(ProtectedSystemPages.normalize(description).take(40).contains(fingerprint))
        assertTrue(ProtectedSystemPages.normalize(description).contains(fingerprint))
    }

    // ---- normalize ----

    @Test
    fun normalizeLowercasesAndStripsSpaces() {
        assertEquals(
            "safemeusesaccessibilityservices",
            ProtectedSystemPages.normalize(" SafeMe uses accessibility services "),
        )
    }
}
