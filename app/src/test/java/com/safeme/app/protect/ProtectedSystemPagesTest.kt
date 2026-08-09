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
    fun settingsSubpackageIsNotRecognized() {
        // M2 fix: isSettingsPackage is an exact allowlist match (all AOSP
        // Settings sub-pages share the single com.android.settings package;
        // the old ".settings" substring fallback was removed as too broad).
        assertFalse(ProtectedSystemPages.isSettingsPackage("com.android.settings.foo"))
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

    // ---- isPuSurface / isUninstallerPackage ----

    @Test
    fun settingsPackageIsPuSurface() {
        assertTrue(ProtectedSystemPages.isPuSurface("com.android.settings"))
        assertTrue(ProtectedSystemPages.isPuSurface("com.miui.securitycenter"))
    }

    @Test
    fun packageinstallerIsPuSurface() {
        assertTrue(ProtectedSystemPages.isPuSurface("com.android.packageinstaller"))
        assertTrue(ProtectedSystemPages.isPuSurface("com.google.android.packageinstaller"))
        assertTrue(ProtectedSystemPages.isUninstallerPackage("com.android.packageinstaller"))
    }

    @Test
    fun unrelatedPackageIsNotPuSurface() {
        assertFalse(ProtectedSystemPages.isPuSurface("com.other.app"))
        assertFalse(ProtectedSystemPages.isPuSurface("com.android.chrome"))
        assertFalse(ProtectedSystemPages.isPuSurface("com.android.systemui"))
        assertFalse(ProtectedSystemPages.isPuSurface(""))
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
