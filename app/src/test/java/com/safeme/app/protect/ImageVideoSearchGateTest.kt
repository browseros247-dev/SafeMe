package com.safeme.app.protect

import com.safeme.app.data.BlockedCategory
import com.safeme.app.data.BlockedKeyword
import com.safeme.app.data.BlockingPrefsState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ImageVideoSearchGateTest {

    private fun state(
        blocklist: List<BlockedKeyword> = emptyList(),
        whitelist: List<String> = emptyList(),
    ): BlockingPrefsState = BlockingPrefsState(
        blocklistKeywords = blocklist,
        whitelistKeywords = whitelist,
    )

    @Test
    fun googleImages_adultTerm_matches() {
        val texts = listOf(
            "https://www.google.com/search?q=xxx&tbm=isch",
            "xxx images",
        )
        val m = ImageVideoSearchGate.matches("com.android.chrome", texts, state())
        assertNotNull(m)
        assertEquals(ImageVideoSearchGate.KIND_IMAGES, m!!.kind)
        assertEquals("tbm=isch", m.signature)
    }

    @Test
    fun googleVideos_adultTerm_matches() {
        val texts = listOf("google.com/search?q=xxx&tbm=vid")
        val m = ImageVideoSearchGate.matches("com.android.chrome", texts, state())
        assertNotNull(m)
        assertEquals(ImageVideoSearchGate.KIND_VIDEOS, m!!.kind)
    }

    @Test
    fun bingImages_adultTerm_matches() {
        val texts = listOf("https://www.bing.com/images/search?q=pussy")
        val m = ImageVideoSearchGate.matches("org.mozilla.firefox", texts, state())
        assertNotNull(m)
        assertEquals("/images/search", m!!.signature)
    }

    @Test
    fun duckDuckGoImages_adultTerm_matches() {
        val texts = listOf("duckduckgo.com/?q=xxx&iax=images&ia=images")
        val m = ImageVideoSearchGate.matches("com.microsoft.emmx", texts, state())
        assertNotNull(m)
        assertEquals(ImageVideoSearchGate.KIND_IMAGES, m!!.kind)
    }

    @Test
    fun yandexVideoSearch_adultTerm_matches() {
        val texts = listOf("yandex.com/video/search?text=xxx")
        val m = ImageVideoSearchGate.matches("com.brave.browser", texts, state())
        assertNotNull(m)
        assertEquals(ImageVideoSearchGate.KIND_VIDEOS, m!!.kind)
    }

    @Test
    fun catsImageSearch_neverMatches() {
        val texts = listOf(
            "https://www.google.com/search?q=cats&tbm=isch",
            "cute cat photos",
        )
        assertNull(ImageVideoSearchGate.matches("com.android.chrome", texts, state()))
    }

    @Test
    fun adultWordOnNormalPage_neverMatches() {
        val texts = listOf(
            "https://en.wikipedia.org/wiki/Sex_education",
            "an article mentioning xxx movies in a film-history context",
        )
        assertNull(ImageVideoSearchGate.matches("com.android.chrome", texts, state()))
    }

    @Test
    fun blankTexts_neverMatches() {
        assertNull(ImageVideoSearchGate.matches("com.android.chrome", emptyList(), state()))
    }

    @Test
    fun nonBrowserPackage_neverMatches() {
        val texts = listOf("google.com/search?q=xxx&tbm=isch")
        assertNull(ImageVideoSearchGate.matches("com.instagram.android", texts, state()))
        assertNull(ImageVideoSearchGate.matches(null, texts, state()))
    }

    @Test
    fun whitelistKeyword_suppressesMatch() {
        val texts = listOf("google.com/search?q=xxx&tbm=isch")
        assertNull(
            ImageVideoSearchGate.matches(
                "com.android.chrome",
                texts,
                state(whitelist = listOf("xxx")),
            ),
        )
    }

    @Test
    fun customAdultBlocklistKeyword_matches() {
        val texts = listOf("bing.com/images/search?q=boobs")
        val m = ImageVideoSearchGate.matches(
            "com.android.chrome",
            texts,
            state(blocklist = listOf(BlockedKeyword("boobs", BlockedCategory.ADULT))),
        )
        assertNotNull(m)
    }

    @Test
    fun nonAdultCustomCategory_doesNotSatisfyCoOccurrence() {
        val texts = listOf("bing.com/images/search?q=football")
        assertNull(
            ImageVideoSearchGate.matches(
                "com.android.chrome",
                texts,
                state(blocklist = listOf(BlockedKeyword("football", BlockedCategory.DISTRACTION))),
            ),
        )
    }

    @Test
    fun imageVideoSignatureIn_prefersImageBeforeVideo() {
        val m = ImageVideoSearchGate.imageVideoSignatureIn(listOf("tbm=isch tbm=vid"))
        assertNotNull(m)
        assertEquals(ImageVideoSearchGate.KIND_IMAGES, m!!.kind)
    }

    @Test
    fun isImageSearchBrowser_coversGuardedList() {
        assertTrue(ImageVideoSearchGate.isImageSearchBrowser("mark.via.gp"))
        assertTrue(ImageVideoSearchGate.isImageSearchBrowser("com.sec.android.app.sbrowser"))
        assertFalse(ImageVideoSearchGate.isImageSearchBrowser("com.safeme.app"))
    }
}
