package com.safeme.app.protect

import com.safeme.app.data.BlockedCategory
import com.safeme.app.data.BlockingPrefsState
import com.safeme.app.data.BundledKeywords

/**
 * NopoX-parity image/video search gate.
 *
 * Fires ONLY when a browser surface shows a search-URL signature for an
 * images/videos vertical AND an adult keyword co-occurs in the same text
 * pool. A plain "cats" image search, or an adult word on a normal page,
 * never fires — co-occurrence is mandatory (false-positive discipline).
 *
 * Pure matcher: no Android dependencies, fully unit-testable.
 */
object ImageVideoSearchGate {

    const val GATE_TYPE = "imgvidsearch"
    const val KIND_IMAGES = "images"
    const val KIND_VIDEOS = "videos"

    val IMAGE_SEARCH_BROWSERS = setOf(
        "com.android.chrome",
        "org.mozilla.firefox",
        "com.microsoft.emmx",
        "com.brave.browser",
        "com.opera.browser",
        "com.sec.android.app.sbrowser",
        "mark.via.gp",
    )

    private val IMAGE_SIGNATURES = listOf(
        "tbm=isch",
        "/images/search",
        "iax=images",
    )

    private val VIDEO_SIGNATURES = listOf(
        "tbm=vid",
        "/videos/search",
        "iax=videos",
        "/video/search",
    )

    data class Match(
        val signature: String,
        val kind: String,
    )

    fun isImageSearchBrowser(pkg: String?): Boolean =
        pkg != null && pkg in IMAGE_SEARCH_BROWSERS

    /**
     * @param lowerTexts already-lowercased text pool (event texts + window dump).
     * @return match or null when suppressed (whitelist) or not co-occurring.
     */
    fun matches(
        browserPkg: String?,
        lowerTexts: List<String>,
        state: BlockingPrefsState,
    ): Match? {
        if (!isImageSearchBrowser(browserPkg)) return null

        // Whitelist keywords suppress first, mirroring the keyword engine.
        val suppressed = state.whitelistKeywords.any { wl ->
            val needle = wl.lowercase()
            needle.isNotBlank() && lowerTexts.any { it.contains(needle) }
        }
        if (suppressed) return null

        val hit = imageVideoSignatureIn(lowerTexts) ?: return null
        if (!adultKeywordIn(lowerTexts, state)) return null
        return hit
    }

    /** First image/video vertical signature found in the pool, or null. */
    fun imageVideoSignatureIn(lowerTexts: List<String>): Match? {
        for (text in lowerTexts) {
            for (sig in IMAGE_SIGNATURES) {
                if (sig in text) return Match(sig, KIND_IMAGES)
            }
            for (sig in VIDEO_SIGNATURES) {
                if (sig in text) return Match(sig, KIND_VIDEOS)
            }
        }
        return null
    }

    /** User ADULT-category blocklist keywords + bundled adult keywords. */
    fun adultKeywordIn(lowerTexts: List<String>, state: BlockingPrefsState): Boolean {
        val userAdult = state.blocklistKeywords
            .filter { it.category == BlockedCategory.ADULT }
            .map { it.value.lowercase().trim() }
            .filter { it.isNotBlank() }
        val bundled = BundledKeywords.keywords.map { it.value.lowercase().trim() }
            .filter { it.isNotBlank() }
        val needles = userAdult + bundled
        if (needles.isEmpty()) return false
        return lowerTexts.any { text -> needles.any { needle -> text.contains(needle) } }
    }
}
