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

    // ---- normalize ----

    @Test
    fun normalizeLowercasesAndStripsSpaces() {
        assertEquals(
            "safemeusesaccessibilityservices",
            ProtectedSystemPages.normalize(" SafeMe uses accessibility services "),
        )
    }
}
