package com.safeme.app.protect

/**
 * Pure, unit-testable rule that identifies Android's "Private DNS" settings
 * screen while Prevent Uninstall is ON. The app sets system-wide DNS filtering
 * through this same surface ([com.safeme.app.vpn.PrivateDnsFilter] writes
 * `private_dns_mode`/`private_dns_specifier` under WRITE_SECURE_SETTINGS), so a
 * user editing it bypasses the app's DNS protection — a genuine tamper surface.
 *
 * ## False-positive discipline (the core constraint)
 *
 * Detection is scoped to Settings-family packages only ([isSettingsPackage])
 * and matches on **class name + window title**, never on page body text. The
 * two realistic traps are both defused:
 *
 *  - **Network & Internet parent page** — its *body* contains a "Private DNS"
 *    row, but its *window title* is "Network & internet" → no match once the
 *    title is normalized (`networkinternet`).
 *  - **Settings Search results** — a search whose query is "Private DNS" titles
 *    the results screen with the query. Any class carrying a `search` marker is
 *    excluded from the *title-fallback* path (a false negative on the transient
 *    search screen is harmless — the user then navigates to the real page, which
 *    gates; a false positive blocking a search is the annoying failure). The
 *    class-marker check runs first, so a real PDNS activity that happens to
 *    contain `search` is still gated.
 *
 * Every branch is fail-open: on any error the page is NOT blocked.
 */
object PrivateDnsBlockers {

    /** Class-name markers for dedicated Private DNS settings activities. */
    private val PRIVATE_DNS_CLASS_MARKERS = listOf("privatedns")

    /** Normalized (lowercase, spaces stripped) window-title markers. */
    private val PRIVATE_DNS_TITLE_MARKERS = listOf("privatedns")

    /**
     * True when [pkg]/[lowerClass]/[titles] identify the Private DNS settings
     * screen. [lowerClass] must already be lowercased by the caller (mirrors
     * [UninstallBlockers]); [titles] is the joined WINDOW-title text (event
     * titles / window title attribute only — never page body text); it is
     * normalized here via [ProtectedSystemPages.normalize], so a title like
     * "Private DNS" (`private dns` → `privatedns`) matches.
     */
    fun isPrivateDnsTargetPage(pkg: String, lowerClass: String, titles: String): Boolean {
        if (!ProtectedSystemPages.isSettingsPackage(pkg)) return false
        // Never gate the uninstall-confirmation dialog surface.
        if (lowerClass.contains("uninstalleractivity")) return false
        // Confident, locale-independent match.
        if (PRIVATE_DNS_CLASS_MARKERS.any { lowerClass.contains(it) }) return true
        // Settings search results host never gates via the title fallback.
        if (lowerClass.contains("search")) return false
        val normalized = ProtectedSystemPages.normalize(titles)
        if (normalized.isBlank()) return false
        return PRIVATE_DNS_TITLE_MARKERS.any { normalized.contains(it) }
    }
}
