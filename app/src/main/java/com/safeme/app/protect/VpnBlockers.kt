package com.safeme.app.protect

/**
 * Pure, unit-testable rule that identifies Android's "VPN" settings page while
 * Prevent Uninstall is ON. The app drives per-app blocking through its own VPN
 * tunnel (see [com.safeme.app.service.SafeMeAccessibilityService]); a user
 * editing the VPN settings page can disable or reset that tunnel, so it is a
 * tamper surface that must be gated.
 *
 * ## False-positive discipline (the core constraint)
 *
 * Detection is scoped to Settings-family packages only ([isSettingsPackage])
 * and matches on **class name + window title**, never on page body text. The
 * realistic trap is defused:
 *
 *  - **Network & Internet parent page** — its *body* contains a "VPN" row (and
 *    a "VPN: SafeMe" entry when the app's VPN is configured), but its *window
 *    title* is "Network & internet" → no match once the title is normalized
 *    (`networkinternet`). This is exactly why the over-broad SubSettings-based
 *    app-info rule was removed ([PreventUninstallBlockers] no longer matches
 *    `subsettings`).
 *
 * Every branch is fail-open: on any error the page is NOT blocked.
 */
object VpnBlockers {

    /** Class-name markers for dedicated VPN settings activities. */
    private val VPN_CLASS_MARKERS = listOf("vpn")

    /** Normalized (lowercase, spaces stripped) window-title markers. */
    private val VPN_TITLE_MARKERS = listOf("vpn")

    /**
     * True when [pkg]/[lowerClass]/[titles] identify the VPN settings page.
     * [lowerClass] must already be lowercased by the caller (mirrors
     * [UninstallBlockers]); [titles] is the joined WINDOW-title text (event
     * titles / window title attribute only — never page body text); it is
     * normalized here via [ProtectedSystemPages.normalize], so a title like
     * "VPN" matches. Mirrors [PrivateDnsBlockers.isPrivateDnsTargetPage].
     */
    fun isVpnTargetPage(pkg: String, lowerClass: String, titles: String): Boolean {
        if (!ProtectedSystemPages.isSettingsPackage(pkg)) return false
        // Never gate the uninstall-confirmation dialog surface.
        if (lowerClass.contains("uninstalleractivity")) return false
        // Confident, locale-independent match.
        if (VPN_CLASS_MARKERS.any { lowerClass.contains(it) }) return true
        // Settings search results host never gates via the title fallback.
        if (lowerClass.contains("search")) return false
        val normalized = ProtectedSystemPages.normalize(titles)
        if (normalized.isBlank()) return false
        return VPN_TITLE_MARKERS.any { normalized.contains(it) }
    }
}
