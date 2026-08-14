package com.safeme.app.protect

import java.util.Locale
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for the pure PU guard-surface decision core
 * ([UninstallBlockers.isOurUninstallTargetPage]). The framework-dependent
 * inputs (app-name presence, our-a11y-detail probe, Device Admin state) are
 * passed in, so every branch is exercisable on the JVM.
 */
class UninstallBlockersTest {

    private fun isTarget(
        pkg: String = "com.android.settings",
        lowerClass: String = "com.android.settings.applications.appinfo.AppInfoDashboardFragment",
        lowerText: String = "safeme app info uninstall force stop disable",
        appIsOnPage: Boolean = true,
        isOurA11yDetailPage: Boolean = false,
        adminActive: Boolean = true,
    ): Boolean = UninstallBlockers.isOurUninstallTargetPage(
        pkg = pkg,
        // The service lowercases class/page text before delegating here.
        lowerClass = lowerClass.lowercase(Locale.ROOT),
        lowerText = lowerText.lowercase(Locale.ROOT),
        appIsOnPage = appIsOnPage,
        isOurA11yDetailPage = { isOurA11yDetailPage },
        adminActive = adminActive,
    )

    // ---- App Info / force-stop / keywords ----

    @Test
    fun appInfoPageIsTarget() {
        assertTrue(isTarget())
    }

    @Test
    fun appInfoPageWithoutOurAppNameIsNeverTarget() {
        assertFalse(isTarget(appIsOnPage = false))
    }

    @Test
    fun forceStopTextWithAppNameIsTarget() {
        assertTrue(
            isTarget(
                lowerClass = "com.android.settings.Settings",
                lowerText = "safeme force stop",
            )
        )
    }

    @Test
    fun plainSettingsPageWithAppNameButNoKeywordsIsNotTarget() {
        assertFalse(
            isTarget(
                lowerClass = "com.android.settings.Settings",
                lowerText = "safeme network wifi bluetooth",
            )
        )
    }

    // ---- Device Admin: deactivation is a target, activation never is ----

    @Test
    fun deviceAdminDeactivationIsTarget() {
        assertTrue(
            isTarget(
                lowerClass = "com.android.settings.DeviceAdminAdd",
                lowerText = "safeme device admin deactivate",
            )
        )
    }

    @Test
    fun deviceAdminActivationIsNeverTarget() {
        assertFalse(
            isTarget(
                lowerClass = "com.android.settings.DeviceAdminAdd",
                lowerText = "safeme device admin",
                adminActive = false,
            )
        )
    }

    @Test
    fun deviceAdminTextsBlockedOnlyWhileAdminActive() {
        assertTrue(
            isTarget(
                lowerClass = "com.android.settings.DeviceAdminAdd",
                lowerText = "safeme deactivate",
            )
        )
        assertFalse(
            isTarget(
                lowerClass = "com.android.settings.DeviceAdminAdd",
                lowerText = "safeme deactivate",
                adminActive = false,
            )
        )
    }

    // ---- Uninstaller package (packageinstaller) ----

    @Test
    fun uninstallConfirmationActivityIsTarget() {
        assertTrue(
            isTarget(
                pkg = "com.android.packageinstaller",
                lowerClass = "com.android.packageinstaller.UninstallerActivity",
                lowerText = "safeme uninstall",
            )
        )
    }

    @Test
    fun uninstallerAlertDialogWithUninstallTextIsTarget() {
        assertTrue(
            isTarget(
                pkg = "com.android.packageinstaller",
                lowerClass = "android.app.AlertDialog",
                lowerText = "safeme uninstall",
            )
        )
    }

    @Test
    fun uninstallerWithoutUninstallTextIsNeverTarget() {
        // Install / update / permission dialogs in the packageinstaller must
        // never be blocked.
        assertFalse(
            isTarget(
                pkg = "com.android.packageinstaller",
                lowerClass = "com.android.packageinstaller.UninstallerActivity",
                lowerText = "safeme update",
            )
        )
        assertFalse(
            isTarget(
                pkg = "com.android.packageinstaller",
                lowerClass = "android.app.AlertDialog",
                lowerText = "safeme permissions",
            )
        )
    }

    @Test
    fun uninstallerSurfaceRequiresOurAppName() {
        assertFalse(
            isTarget(
                pkg = "com.android.packageinstaller",
                lowerClass = "com.android.packageinstaller.UninstallerActivity",
                lowerText = "chrome uninstall",
                appIsOnPage = false,
            )
        )
    }

    // ---- Our own a11y detail page is never gated ----

    @Test
    fun ourA11yDetailPageIsNeverGated() {
        assertFalse(
            isTarget(
                lowerText = "safeme",
                isOurA11yDetailPage = true,
            )
        )
    }

    // ---- Unrelated windows ----

    @Test
    fun unrelatedAppPageIsNeverTarget() {
        assertFalse(
            isTarget(
                pkg = "com.android.chrome",
                lowerText = "chrome settings about version",
                appIsOnPage = false,
            )
        )
    }
}
