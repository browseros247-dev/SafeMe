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
}
