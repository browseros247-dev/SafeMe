package com.safeme.app.protect

/**
 * Text/class markers used by the Prevent Uninstall guards. Ported from the
 * reference (BlockerPageUtils). Every match is additionally gated on the
 * current window being a settings-family package showing SafeMe's own app
 * name — so unrelated Apps/App-info pages and other apps' settings pages are
 * never blocked.
 */
object UninstallBlockers {

    /**
     * Phrases that appear on the Device Admin deactivation page / dialog.
     * Specific enough to avoid "Administrator settings" lists generally.
     */
    val DEVICE_ADMIN_TEXTS_TO_MATCH: List<String> = listOf(
        "device admin",           // Device administrators page title / deactivate dialog
        "deactivate",             // Deactivate button text
        "extended_title",         // internal view ID
        "applabel_title",         // internal view ID
        "header_title",           // internal view ID
        "alertTitle",             // internal view ID (dialog title)
        "detail_title",           // internal view ID
    )

    /**
     * Force-stop button text across locales. Combined with our app name on the
     * page, this confirms the user is on SafeMe's own App Info page.
     */
    val FORCE_STOP_TEXTS_TO_MATCH: List<String> = listOf(
        "force stop",             // en
        "forcestop",              // en (no space)
        "erzwingen",              // de
        "detener",                // es
        "forcer l'arrêt",         // fr
        "forza interruzione",     // it
        "強制終了",                 // ja
        "강제 종료",                 // ko
        "принудительно остановить",// ru
        "强行停止",                 // zh
        "zorla durdur",           // tr
    )

    /**
     * Class-name markers for the app-info page of an installed app (hosted by
     * Settings on AOSP/OEM). "subsettings" alone is ambiguous (it also hosts
     * the a11y detail page on modern AOSP) — the caller must ALSO confirm our
     * app name is on page AND exclude our a11y detail page (description
     * fingerprint) before treating SubSettings as our App Info page.
     */
    val APP_INFO_CLASS_MARKERS: List<String> = listOf(
        "appinfodashboard",
        "installedappdetails",
        "appinfoactivity",
        "appinfopage",
        "appinfo",
        "appdetails",
        "appdetail",
        "subsettings",
    )

    val UNINSTALL_KEYWORDS: List<String> = listOf(
        "uninstall",
        "disable",
        "force stop",
        "forcestop",
        "deactivate",
        "remove",
        "clear data",
        "cleardata",
        "storage",
        "permissions",
    )

    /**
     * Pure decision core for the app-info / device-admin / force-stop /
     * uninstall guard surface. Every framework-dependent input is passed in
     * (app-name presence including the node-tree probe result, the
     * caller-appropriate our-a11y-detail check, Device Admin state) so the
     * logic is unit-testable without an AccessibilityService.
     *
     * [isOurA11yDetailPage] is evaluated lazily (it may probe the node tree)
     * and only when the page is otherwise a target. All branches are
     * fail-open: anything that is not unmistakably SafeMe's own tamper surface
     * is left untouched.
     */
    fun isOurUninstallTargetPage(
        pkg: String,
        lowerClass: String,
        lowerText: String,
        appIsOnPage: Boolean,
        isOurA11yDetailPage: () -> Boolean,
        adminActive: Boolean,
    ): Boolean {
        // The stock uninstall confirmation lives in the packageinstaller
        // package (UninstallerActivity). Only that dialog surface may be
        // gated there — its install/update screens must never be blocked.
        if (ProtectedSystemPages.isUninstallerPackage(pkg)) {
            // The uninstall confirmation is reported either as the
            // UninstallerActivity class or as a generic AlertDialog window
            // (observed on Android 16). Both surfaces are gated on the
            // "uninstall" text so install/update/permission dialogs in the
            // packageinstaller are never blocked.
            val isUninstallSurface = lowerClass.contains("uninstalleractivity") ||
                lowerClass.contains("alertdialog")
            val hasUninstallText = lowerText.contains("uninstall")
            return isUninstallSurface && appIsOnPage && hasUninstallText
        }

        if (lowerClass.contains("uninstalleractivity")) {
            if (appIsOnPage) return true
        }

        if (!appIsOnPage) return false

        // Never block our own a11y detail page (the eviction path handles it).
        if (isOurA11yDetailPage()) return false

        // DeviceAdminAdd hosts BOTH the activation flow (admin NOT active)
        // and the deactivation flow (admin active). The activation page
        // must never be blocked — its text matches the "device admin" and
        // "uninstall" markers ("device administrator", our own ADD
        // explanation), so a stale PU flag would lock the user out of
        // turning the feature on. Only deactivation is a tamper surface.
        if (lowerClass.contains("deviceadminadd") && !adminActive) return false

        val isAppInfoClass = APP_INFO_CLASS_MARKERS.any { lowerClass.contains(it) }
        val hasUninstallKeyword = UNINSTALL_KEYWORDS.any { lowerText.contains(it) }
        if (isAppInfoClass || hasUninstallKeyword) return true

        // The Device-admin texts can also appear on the administrators LIST
        // page — block it only while our admin is actually active.
        if (adminActive && DEVICE_ADMIN_TEXTS_TO_MATCH.any { lowerText.contains(it) }) return true

        if (FORCE_STOP_TEXTS_TO_MATCH.any { lowerText.contains(it) }) return true

        return false
    }
}
