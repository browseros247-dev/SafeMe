package com.safeme.app.protect

import com.safeme.app.data.BlockingPrefsState
import com.safeme.app.data.BundledKeywordCatalog
import com.safeme.app.data.BundledKeywords
import com.safeme.app.data.normalizeDomain
import java.util.Locale

/**
 * Dedicated browser URL-bar gate (NopoX-parity site detection).
 *
 * Supported browsers carry the address bar text in the accessibility tree;
 * evaluating THAT text with URL-shape rules is far more reliable than generic
 * body-text keyword matching. False positives are contained three ways:
 *  1. the gate only ever fires for allowlisted browser packages;
 *  2. URL candidates must match a strict no-space host shape (search queries,
 *     page titles and file names never qualify);
 *  3. whitelist/trusted suppression runs FIRST, mirroring [com.safeme.app.data]
 *     engine invariants.
 *
 * Domain coverage decision (D4): the English catalog's site domains are ALWAYS
 * loaded plus the device language's domains, so non-English devices keep full
 * site coverage while plain keyword needles stay device-language-only (D1).
 */
internal object BrowserUrlGate {

    /** Allowlisted browser packages (extended allowlist, decision D5). */
    private val BROWSER_PACKAGES = setOf(
        "com.android.chrome",
        "org.mozilla.firefox",
        "org.mozilla.focus",
        "com.microsoft.emmx",
        "com.brave.browser",
        "com.opera.browser",
        "com.opera.mini.native",
        "com.opera.gx",
        "com.opera.app",
        "com.sec.android.app.sbrowser",
        "mark.via.gp",
        "com.duckduckgo.mobile.android",
        "com.vivaldi.browser",
        "com.yandex.browser",
    )

    /**
     * Strict URL shape: optional scheme, optional www, dotted host, optional
     * port/path/query. Whole-string match with NO whitespace anywhere, so
     * search queries and page titles are rejected outright.
     */
    private val URL_SHAPE = Regex(
        "^(?:https?://)?(?:www\\.)?[a-z0-9-]+(?:\\.[a-z0-9-]+)+(?::\\d{2,5})?(?:/[^\\s]*)?$",
        RegexOption.IGNORE_CASE,
    )

    data class UrlMatch(
        val value: String,
        val type: String,
    )

    fun isBrowserPackage(pkg: String?): Boolean = pkg != null && pkg in BROWSER_PACKAGES

    /**
     * Extracts URL candidates from raw accessibility strings. Only strict
     * URL-shaped, space-free strings qualify; each candidate is normalized to
     * its host form (scheme/path stripped via [normalizeDomain]).
     */
    fun extractUrlCandidates(texts: List<String>): List<String> =
        texts.mapNotNull { raw ->
            val t = raw.trim()
            if (t.isEmpty() || t.length > 2000 || t.contains(' ')) return@mapNotNull null
            if (!URL_SHAPE.matches(t)) return@mapNotNull null
            normalizeDomain(t)
                .removePrefix("www.")
                .substringBefore(':')
                .takeIf { it.contains('.') }
        }.distinct()

    /**
     * Pure classification of collected tree entries (className to text pairs):
     * an EditText node whose text is URL-shaped contributes a candidate.
     */
    fun urlCandidatesFromNodeEntries(entries: List<Pair<String?, String>>): List<String> =
        extractUrlCandidates(
            entries.filter { (cls, _) ->
                cls != null && cls.substringAfterLast('.').equals("EditText", ignoreCase = true)
            }.map { (_, text) -> text },
        )

    /**
     * Evaluates raw URL candidates against user config + bundled catalogs.
     * Suppression-first order mirrors the keyword engine: whitelist keywords,
     * trusted websites, then user/bundled keyword needles, then domain suffix
     * matching at label boundaries. Returns null when nothing matches.
     */
    fun evaluate(rawUrls: List<String>, state: BlockingPrefsState): UrlMatch? {
        if (rawUrls.isEmpty()) return null
        val lowerUrls = rawUrls.map { it.lowercase() }

        // 1. Whitelist keywords override everything (engine invariant).
        if (state.whitelistKeywords.any { wl ->
                val needle = wl.lowercase().trim()
                needle.isNotBlank() && lowerUrls.any { it.contains(needle) }
            }
        ) {
            return null
        }

        val hosts = lowerUrls.mapNotNull { url ->
            normalizeDomain(url).takeIf { it.isNotEmpty() }
        }
        if (hosts.isEmpty()) return null

        // 2. Trusted websites suppress (exact or subdomain).
        if (state.trustedWebsites.any { trusted ->
                val t = trusted.lowercase()
                t.isNotEmpty() && hosts.any { it == t || it.endsWith(".$t") }
            }
        ) {
            return null
        }

        // 3. User blocklist keywords (substring on the raw URL).
        state.blocklistKeywords.forEach { kw ->
            val needle = kw.value.lowercase()
            if (needle.isNotBlank() && lowerUrls.any { it.contains(needle) }) {
                return UrlMatch(kw.value, "keyword")
            }
        }

        // 4. Bundled keywords — the SAME merged list the keyword engine uses
        //    (curated terms + device-language catalog entries, D1/D3).
        BundledKeywords.keywords.forEach { kw ->
            val needle = kw.value.lowercase()
            if (needle.isNotBlank() && lowerUrls.any { it.contains(needle) }) {
                return UrlMatch(kw.value, "keyword")
            }
        }

        // 5. Domain suffix match: user sites, curated sites, then ALWAYS the
        //    EN catalog domains plus the device-language catalog domains (D4).
        val deviceLang = Locale.getDefault().language?.lowercase()
        val domainSources = LinkedHashSet<String>().apply {
            state.blockedWebsites.forEach { add(it.domain) }
            BundledKeywords.websites.forEach { add(it.domain) }
            addAll(BundledKeywordCatalog.domainsFor("en"))
            if (deviceLang != null && deviceLang != "en") {
                addAll(BundledKeywordCatalog.domainsFor(deviceLang))
            }
        }
        domainSources.forEach { source ->
            val d = normalizeDomain(source)
            if (d.isEmpty()) return@forEach
            if (hosts.any { it == d || it.endsWith(".$d") }) {
                return UrlMatch(source, "website")
            }
        }

        return null
    }
}
