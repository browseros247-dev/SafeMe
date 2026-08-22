package com.safeme.app.protect

import com.safeme.app.data.BlockedCategory
import com.safeme.app.data.BlockedKeyword
import com.safeme.app.data.BlockedWebsite
import com.safeme.app.data.BlockingPrefsState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BrowserUrlGateTest {

    private fun state(
        blocklist: List<BlockedKeyword> = emptyList(),
        whitelist: List<String> = emptyList(),
        blockedSites: List<BlockedWebsite> = emptyList(),
        trusted: List<String> = emptyList(),
    ) = BlockingPrefsState(
        blocklistKeywords = blocklist,
        whitelistKeywords = whitelist,
        blockedWebsites = blockedSites,
        trustedWebsites = trusted,
    )

    // --- Allowlist ---

    @Test
    fun knownBrowsersAreAllowlisted() {
        listOf(
            "com.android.chrome",
            "org.mozilla.firefox",
            "com.microsoft.emmx",
            "com.brave.browser",
            "com.opera.browser",
            "com.opera.mini.native",
            "com.opera.gx",
            "com.sec.android.app.sbrowser",
            "mark.via.gp",
            "com.duckduckgo.mobile.android",
            "com.vivaldi.browser",
            "com.yandex.browser",
        ).forEach { pkg -> assertTrue(pkg, BrowserUrlGate.isBrowserPackage(pkg)) }
    }

    @Test
    fun nonBrowsersAreNeverAllowlisted() {
        assertFalse(BrowserUrlGate.isBrowserPackage(null))
        assertFalse(BrowserUrlGate.isBrowserPackage(""))
        assertFalse(BrowserUrlGate.isBrowserPackage("com.android.settings"))
        assertFalse(BrowserUrlGate.isBrowserPackage("com.instagram.android"))
        assertFalse(BrowserUrlGate.isBrowserPackage("com.safeme.app"))
    }

    // --- URL candidate extraction ---

    @Test
    fun acceptsUrlShapedStrings() {
        assertEquals(
            listOf("pornhub.com"),
            BrowserUrlGate.extractUrlCandidates(listOf("https://www.pornhub.com/video?abc=1")),
        )
        assertEquals(
            listOf("m.pornhub.com"),
            BrowserUrlGate.extractUrlCandidates(listOf("m.pornhub.com")),
        )
        assertTrue(
            BrowserUrlGate.extractUrlCandidates(listOf("http://example.com:8080/path")).contains(
                "example.com",
            ),
        )
        assertTrue(
            BrowserUrlGate.extractUrlCandidates(listOf("www.reddit.com/r/nofap")).contains(
                "reddit.com",
            ),
        )
    }

    @Test
    fun rejectsQueriesTitlesAndNonUrls() {
        val rejected = listOf(
            "my file.txt",
            "hot dogs near me",
            "porn sites list",   // spaces → never a URL bar value
            "about:blank",
            "porn",
            "",
            "  ",
        )
        assertTrue(BrowserUrlGate.extractUrlCandidates(rejected).isEmpty())
    }

    @Test
    fun editTextEntriesClassifyByClassNameAndShape() {
        val candidates =
            BrowserUrlGate.urlCandidatesFromNodeEntries(
                listOf(
                    "android.widget.EditText" to "https://www.pornhub.com/video",
                    "android.widget.EditText" to "hot dogs near me",
                    "android.widget.TextView" to "https://www.pornhub.com/video",
                    "android.widget.EditText" to "  ",
                ),
            )
        assertEquals(listOf("pornhub.com"), candidates)
    }

    // --- evaluate ordering ---

    @Test
    fun whitelistSuppressionComesFirst() {
        val s = state(
            whitelist = listOf("reddit.com/r/nofap"),
            blocklist = listOf(BlockedKeyword("reddit", BlockedCategory.CUSTOM)),
        )
        assertNull(
            BrowserUrlGate.evaluate(listOf("https://www.reddit.com/r/nofap/top"), s),
        )
    }

    @Test
    fun trustedWebsiteSuppressesBeforeKeywordNeedles() {
        val s = state(
            trusted = listOf("pornhub.com"),
            blocklist = listOf(BlockedKeyword("porn", BlockedCategory.ADULT)),
        )
        assertNull(BrowserUrlGate.evaluate(listOf("https://www.pornhub.com/video"), s))
    }

    @Test
    fun userBlocklistKeywordMatchesRawUrl() {
        val s = state(blocklist = listOf(BlockedKeyword("bet365", BlockedCategory.GAMBLING)))
        val match = BrowserUrlGate.evaluate(listOf("https://www.bet365.com/sport"), s)
        assertEquals("bet365", match?.value)
        assertEquals("keyword", match?.type)
    }

    @Test
    fun bundledCatalogDomainGatesVisit() {
        // EN catalog domains are ALWAYS loaded (D4): kaufmich.com must gate on
        // any device language. The reported value/type depends on whether the
        // device-language keyword list (D1/D3 verbatim mixed entries) also
        // carries the entry, so only gating itself is asserted.
        val match = BrowserUrlGate.evaluate(listOf("https://www.kaufmich.com/profil"), state())
        assertTrue(match != null)
    }

    @Test
    fun coreKeywordNeedlesRankBeforeDomainRules() {
        // Locked evaluation order: bundled keyword needles fire before the
        // domain step. Uses a CORE curated term (always first in the merged
        // list) so the assertion is independent of device language.
        val match = BrowserUrlGate.evaluate(listOf("https://www.xvideos.com/video"), state())
        assertEquals("xvideos", match?.value)
        assertEquals("keyword", match?.type)
    }

    @Test
    fun userBlockedWebsiteSuffixMatchAtLabelBoundaries() {
        val s = state(blockedSites = listOf(BlockedWebsite("evil.example", BlockedCategory.CUSTOM)))
        val hit = BrowserUrlGate.evaluate(listOf("https://sub.evil.example/x"), s)
        assertEquals("evil.example", hit?.value)

        // Label-boundary guard: "notevil.example" is NOT evil.example.
        assertNull(BrowserUrlGate.evaluate(listOf("https://notevil.example/x"), s))
    }

    @Test
    fun noMatchReturnsNull() {
        assertNull(
            BrowserUrlGate.evaluate(
                listOf("https://de.wikipedia.org/wiki/Android", "www.wikipedia.org"),
                state(),
            ),
        )
    }

    @Test
    fun emptyCandidateListIsNeverEvaluated() {
        assertNull(BrowserUrlGate.evaluate(emptyList(), state()))
    }
}
