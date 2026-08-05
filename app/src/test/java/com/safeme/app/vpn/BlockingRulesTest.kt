package com.safeme.app.vpn

import com.safeme.app.data.BlockedCategory
import com.safeme.app.data.BlockedKeyword
import com.safeme.app.data.BlockedWebsite
import com.safeme.app.data.BlockingPrefsState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BlockingRulesTest {

    private fun rules(
        blockedWebsites: List<String> = emptyList(),
        trustedWebsites: List<String> = emptyList(),
        keywords: List<String> = emptyList(),
        whitelistKeywords: List<String> = emptyList(),
        blockingEnabled: Boolean = true,
    ): BlockingRules = BlockingRules.fromPrefs(
        BlockingPrefsState(
            blocklistKeywords = keywords.map { BlockedKeyword(it, BlockedCategory.CUSTOM) },
            whitelistKeywords = whitelistKeywords,
            blockedWebsites = blockedWebsites.map { BlockedWebsite(it, BlockedCategory.CUSTOM) },
            trustedWebsites = trustedWebsites,
            blockingEnabled = blockingEnabled,
        )
    )

    @Test
    fun `exact domain match is blocked`() {
        val r = rules(blockedWebsites = listOf("pornhub.com"))
        assertTrue(r.shouldBlock("pornhub.com"))
    }

    @Test
    fun `subdomain of blocked domain is blocked`() {
        val r = rules(blockedWebsites = listOf("pornhub.com"))
        assertTrue(r.shouldBlock("www.pornhub.com"))
        assertTrue(r.shouldBlock("a.b.pornhub.com."))
    }

    @Test
    fun `unrelated domains are allowed`() {
        val r = rules(blockedWebsites = listOf("pornhub.com"))
        assertFalse(r.shouldBlock("example.com"))
        assertFalse(r.shouldBlock("notpornhub.com"))
        assertFalse(r.shouldBlock("pornhub.com.evil.test"))
    }

    @Test
    fun `keyword substring blocks`() {
        val r = rules(keywords = listOf("porn"))
        assertTrue(r.shouldBlock("pornhub.com"))
        assertTrue(r.shouldBlock("my-porn-site.net"))
        assertFalse(r.shouldBlock("example.com"))
    }

    @Test
    fun `trusted website overrides blocked domain`() {
        val r = rules(blockedWebsites = listOf("pornhub.com"), trustedWebsites = listOf("pornhub.com"))
        assertFalse(r.shouldBlock("pornhub.com"))
        assertFalse(r.shouldBlock("www.pornhub.com"))
    }

    @Test
    fun `whitelist keyword overrides blocked keyword`() {
        val r = rules(keywords = listOf("porn"), whitelistKeywords = listOf("pornhub"))
        assertFalse(r.shouldBlock("pornhub.com"))
        assertTrue(r.shouldBlock("pornsite.org"))
    }

    @Test
    fun `blocking disabled allows everything`() {
        val r = rules(blockedWebsites = listOf("pornhub.com"), keywords = listOf("porn"), blockingEnabled = false)
        assertFalse(r.shouldBlock("pornhub.com"))
        assertFalse(r.shouldBlock("pornsite.org"))
    }

    @Test
    fun `case and dots are normalized`() {
        val r = rules(blockedWebsites = listOf("PornHub.COM."))
        assertTrue(r.shouldBlock("WWW.PornHub.Com"))
    }

    @Test
    fun `empty host never blocks`() {
        val r = rules(blockedWebsites = listOf("pornhub.com"))
        assertFalse(r.shouldBlock(""))
        assertFalse(r.shouldBlock("   "))
        assertFalse(r.shouldBlock("."))
    }

    @Test
    fun `passthrough rules never block`() {
        val r = BlockingRules.passthrough()
        assertFalse(r.shouldBlock("anything.example"))
        assertFalse(r.isBlockingEnabled())
    }

    @Test
    fun `bundled dataset participates`() {
        val r = BlockingRules.fromPrefs(
            BlockingPrefsState(blockingEnabled = true),
            bundledKeywords = listOf(BlockedKeyword("sexchat", BlockedCategory.ADULT)),
            bundledWebsites = listOf(BlockedWebsite("xvideos.com", BlockedCategory.ADULT)),
        )
        assertTrue(r.shouldBlock("xvideos.com"))
        assertTrue(r.shouldBlock("www.xvideos.com"))
        assertTrue(r.shouldBlock("sexchat.example.com"))
        assertFalse(r.shouldBlock("example.com"))
    }

    @Test
    fun `domain matching helper`() {
        assertTrue(BlockingRules.matchesDomain("example.com", "example.com"))
        assertTrue(BlockingRules.matchesDomain("example.com", "sub.example.com"))
        assertFalse(BlockingRules.matchesDomain("ample.com", "example.com"))
        assertFalse(BlockingRules.matchesDomain("example.com", "notexample.com"))
    }

    // ---- raw payload fallback (DNS question that couldn't be decoded) ----

    @Test
    fun `raw match finds blocked domain case-insensitively`() {
        val r = rules(blockedWebsites = listOf("pornhub.com"))
        val payload = "some-raw-dns-bytes-WWW.PornHub.Com-more".toByteArray(Charsets.ISO_8859_1)
        assertEquals("pornhub.com", r.rawMatch(payload))
    }

    @Test
    fun `raw match honors trusted domains`() {
        val r = rules(blockedWebsites = listOf("pornhub.com"), trustedWebsites = listOf("pornhub.com"))
        val payload = "www.pornhub.com".toByteArray(Charsets.ISO_8859_1)
        assertTrue(r.rawMatch(payload) == null)
    }

    @Test
    fun `raw match returns null when nothing blocked`() {
        val r = rules(blockedWebsites = listOf("pornhub.com"))
        val payload = "example.org".toByteArray(Charsets.ISO_8859_1)
        assertTrue(r.rawMatch(payload) == null)
    }

    @Test
    fun `raw match is inert when blocking disabled`() {
        val r = rules(blockedWebsites = listOf("pornhub.com"), blockingEnabled = false)
        val payload = "pornhub.com".toByteArray(Charsets.ISO_8859_1)
        assertTrue(r.rawMatch(payload) == null)
    }
}
