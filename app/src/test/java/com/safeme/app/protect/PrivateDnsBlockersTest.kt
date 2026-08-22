package com.safeme.app.protect

import java.util.Locale
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for the pure Private DNS guard-surface decision core
 * ([PrivateDnsBlockers.isPrivateDnsTargetPage]). The service lowercases the
 * class before delegating and supplies the window title(s) (never page body
 * text); both are mirrored here so every branch is exercisable on the JVM.
 */
class PrivateDnsBlockersTest {

    private fun isPdn(
        pkg: String = "com.android.settings",
        lowerClass: String = "com.android.settings.SubSettings",
        title: String = "Private DNS",
    ): Boolean = PrivateDnsBlockers.isPrivateDnsTargetPage(
        pkg = pkg,
        // The service lowercases the class before delegating here.
        lowerClass = lowerClass.lowercase(Locale.ROOT),
        titles = title,
    )

    // ---- Positive: the Private DNS page is a target ----

    @Test
    fun dedicatedActivityClassIsTarget() {
        // OEM dedicated activity: the class marker is enough — no title needed.
        assertTrue(
            isPdn(
                lowerClass = "com.android.settings.PrivateDnsActivity",
                title = "",
            )
        )
    }

    @Test
    fun genericSubSettingsWithPrivateDnsTitleIsTarget() {
        // Stock AOSP: a generic SubSettings container titled "Private DNS".
        assertTrue(isPdn(lowerClass = "com.android.settings.SubSettings", title = "Private DNS"))
    }

    @Test
    fun privateDnsModeTitleIsTarget() {
        assertTrue(isPdn(title = "Private Dns Mode"))
    }

    @Test
    fun normalizesTitleByStrippingSpaces() {
        assertTrue(isPdn(title = "Private  DNS"))
    }

    @Test
    fun noSafeMeNameIsRequired() {
        // The surface carries no SafeMe identity — unlike the app-info pages.
        assertTrue(isPdn(title = "Private DNS provider"))
    }

    // ---- False-positive traps ----

    @Test
    fun parentNetworkInternetTitleIsNeverTarget() {
        // The parent page's body has a "Private DNS" row, but its window title
        // is "Network & Internet" — the title-only matcher must not fire.
        assertFalse(isPdn(title = "Network & Internet"))
    }

    @Test
    fun searchResultsScreenWithPrivateDnsTitleIsNeverTarget() {
        // Settings search results host — a search titled by the query "Private
        // DNS" must not gate via the title fallback.
        assertFalse(
            isPdn(
                lowerClass = "com.android.settings.search.SearchSettingsActivity",
                title = "Private DNS",
            )
        )
    }

    @Test
    fun titleContainingOnlyDnsIsNeverTarget() {
        assertFalse(isPdn(title = "DNS"))
    }

    @Test
    fun blankTitleAndGenericClassIsNeverTarget() {
        assertFalse(isPdn(lowerClass = "com.android.settings.SubSettings", title = ""))
    }

    @Test
    fun unrelatedSystemPageIsNeverTarget() {
        assertFalse(isPdn(lowerClass = "com.android.settings.Settings", title = "Network & Internet"))
    }

    // ---- Scope guards ----

    @Test
    fun nonSettingsPackageIsNeverTarget() {
        assertFalse(
            isPdn(
                pkg = "com.android.chrome",
                lowerClass = "com.android.chrome.PrivateDnsActivity",
                title = "Private DNS",
            )
        )
    }

    @Test
    fun uninstallerActivityIsNeverTarget() {
        assertFalse(isPdn(lowerClass = "com.android.packageinstaller.UninstallerActivity"))
    }

    @Test
    fun realPrivateDnsClassStillTargetEvenIfClassContainsSearch() {
        // The class-marker check runs before the search guard, so a genuine
        // PDNS activity whose name happens to contain "search" is still gated.
        assertTrue(
            isPdn(
                lowerClass = "com.android.settings.PrivateDnsSearchActivity",
                title = "",
            )
        )
    }
}
