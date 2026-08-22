package com.safeme.app.protect

import java.util.Locale
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for the pure VPN guard-surface decision core
 * ([VpnBlockers.isVpnTargetPage]). The service lowercases the class before
 * delegating and supplies the window title(s) (never page body text); both are
 * mirrored here so every branch is exercisable on the JVM.
 */
class VpnBlockersTest {

    private fun isVpn(
        pkg: String = "com.android.settings",
        lowerClass: String = "com.android.settings.SubSettings",
        title: String = "VPN",
    ): Boolean = VpnBlockers.isVpnTargetPage(
        pkg = pkg,
        // The service lowercases the class before delegating here.
        lowerClass = lowerClass.lowercase(Locale.ROOT),
        titles = title,
    )

    // ---- Positive: the VPN page is a target ----

    @Test
    fun dedicatedActivityClassIsTarget() {
        // OEM dedicated activity: the class marker is enough — no title needed.
        assertTrue(
            isVpn(
                lowerClass = "com.android.settings.vpn.VpnSettings",
                title = "",
            )
        )
    }

    @Test
    fun genericSubSettingsWithVpnTitleIsTarget() {
        // Stock AOSP: a generic SubSettings container titled "VPN".
        assertTrue(isVpn(lowerClass = "com.android.settings.SubSettings", title = "VPN"))
    }

    @Test
    fun normalizesTitleByStrippingSpaces() {
        assertTrue(isVpn(title = "V P N"))
    }

    @Test
    fun noSafeMeNameIsRequired() {
        // The surface carries no SafeMe identity — unlike the app-info pages.
        assertTrue(isVpn(title = "VPN settings"))
    }

    // ---- False-positive traps ----

    @Test
    fun parentNetworkInternetTitleIsNeverTarget() {
        // The parent page's body has a "VPN" row (and a "VPN: SafeMe" entry when
        // the app's VPN is configured), but its window title is "Network &
        // Internet" — the title-only matcher must not fire.
        assertFalse(isVpn(title = "Network & Internet"))
    }

    @Test
    fun privateDnsDialogTitleIsNeverTarget() {
        // The Private DNS dialog must never be mistaken for the VPN page.
        assertFalse(isVpn(title = "Select Private DNS Mode"))
    }

    @Test
    fun searchResultsScreenWithVpnTitleIsNeverTarget() {
        // Settings search results host — a search titled by the query "VPN"
        // must not gate via the title fallback.
        assertFalse(
            isVpn(
                lowerClass = "com.android.settings.search.SearchSettingsActivity",
                title = "VPN",
            )
        )
    }

    @Test
    fun titleContainingOnlyDnsIsNeverTarget() {
        assertFalse(isVpn(title = "DNS"))
    }

    @Test
    fun blankTitleAndGenericClassIsNeverTarget() {
        assertFalse(isVpn(lowerClass = "com.android.settings.SubSettings", title = ""))
    }

    @Test
    fun unrelatedSystemPageIsNeverTarget() {
        assertFalse(isVpn(lowerClass = "com.android.settings.Settings", title = "Network & Internet"))
    }

    // ---- Scope guards ----

    @Test
    fun nonSettingsPackageIsNeverTarget() {
        assertFalse(
            isVpn(
                pkg = "com.android.chrome",
                lowerClass = "com.android.chrome.vpn.VpnSettings",
                title = "VPN",
            )
        )
    }

    @Test
    fun uninstallerActivityIsNeverTarget() {
        assertFalse(isVpn(lowerClass = "com.android.packageinstaller.UninstallerActivity"))
    }

    @Test
    fun realVpnClassStillTargetEvenIfClassContainsSearch() {
        // The class-marker check runs before the search guard, so a genuine VPN
        // activity whose name happens to contain "search" is still gated.
        assertTrue(
            isVpn(
                lowerClass = "com.android.settings.vpn.VpnSearchActivity",
                title = "",
            )
        )
    }
}
