package com.safeme.app.vpn

import com.safeme.app.data.BlockedCategory
import com.safeme.app.data.BlockedKeyword
import com.safeme.app.data.BlockedWebsite
import com.safeme.app.data.BlockingPrefsState

/**
 * Domain / keyword blocking rules evaluated against DNS query names.
 *
 * Decision order (most specific wins):
 *   1. Trusted domain (explicit user allow)          → allow
 *   2. Whitelist keyword (explicit user allow)       → allow
 *   3. Blocked domain (bundled or user)              → block
 *   4. Blocked keyword (bundled or user)             → block
 *   5. Otherwise                                     → allow
 *
 * Unless [blockingEnabled] is false, in which case nothing is ever blocked and
 * the VPN simply relays DNS — traffic is never interrupted by default.
 */
class BlockingRules private constructor(
    private val blockedDomains: Set<String>,
    private val trustedDomains: Set<String>,
    private val blockedKeywords: List<String>,
    private val whitelistKeywords: List<String>,
    private val blockingEnabled: Boolean,
) {

    fun shouldBlock(host: String): Boolean {
        if (!blockingEnabled) return false
        val normalized = normalize(host)
        if (normalized.isEmpty()) return false

        if (trustedDomains.any { matchesDomain(it, normalized) }) return false
        if (whitelistKeywords.any { normalized.contains(it) }) return false
        if (blockedDomains.any { matchesDomain(it, normalized) }) return true
        if (blockedKeywords.any { normalized.contains(it) }) return true
        return false
    }

    fun isBlockingEnabled(): Boolean = blockingEnabled

    /**
     * Fallback matcher for DNS payloads whose question section could not be
     * decoded normally (odd encodings, compression edge cases). Scans the raw
     * bytes case-insensitively for a blocked domain and returns it if present.
     * Whitelisted/trusted entries are honored first.
     */
    fun rawMatch(data: ByteArray): String? {
        if (!blockingEnabled || data.isEmpty() || blockedDomains.isEmpty()) return null
        val lower = ByteArray(data.size)
        for (i in data.indices) {
            val c = data[i].toInt() and 0xFF
            lower[i] = if (c in 'A'.code..'Z'.code) (c + 32).toByte() else data[i]
        }
        val lowerText = String(lower, Charsets.ISO_8859_1)
        // Whitelists win over raw matches too.
        if (trustedDomains.any { lowerText.contains(it) }) return null
        for (domain in blockedDomains) {
            if (domain.isNotEmpty() && lowerText.contains(domain)) return domain
        }
        return null
    }

    companion object {
        fun normalize(host: String): String =
            host.trim().lowercase().trimEnd('.')

        fun matchesDomain(pattern: String, host: String): Boolean {
            val p = normalize(pattern)
            return p.isNotEmpty() && (host == p || host.endsWith(".$p"))
        }

        /**
         * Builds rules from the persisted blocking preferences plus the bundled
         * dataset, mirroring the keyword counts shown in the UI (user keywords +
         * bundled keywords).
         */
        fun fromPrefs(
            state: BlockingPrefsState,
            bundledKeywords: List<BlockedKeyword> = emptyList(),
            bundledWebsites: List<BlockedWebsite> = emptyList(),
        ): BlockingRules {
            val blockedDomains = LinkedHashSet<String>()
            state.blockedWebsites.forEach { blockedDomains.add(normalize(it.domain)) }
            bundledWebsites.forEach { blockedDomains.add(normalize(it.domain)) }

            val blockedKeywords = ArrayList<String>()
            state.blocklistKeywords.forEach { blockedKeywords.add(normalize(it.value)) }
            bundledKeywords.forEach { blockedKeywords.add(normalize(it.value)) }

            val trusted = state.trustedWebsites.mapTo(LinkedHashSet()) { normalize(it) }
            val whitelist = state.whitelistKeywords.mapTo(ArrayList()) { normalize(it) }

            return BlockingRules(
                blockedDomains = blockedDomains,
                trustedDomains = trusted,
                blockedKeywords = blockedKeywords.filter { it.isNotEmpty() },
                whitelistKeywords = whitelist.filter { it.isNotEmpty() },
                blockingEnabled = state.blockingEnabled,
            )
        }

        /** Rules that never block — used when filtering is intentionally off. */
        fun passthrough(): BlockingRules =
            BlockingRules(emptySet(), emptySet(), emptyList(), emptyList(), false)
    }
}

/** Utility for keyword normalization shared with the UI layer. */
object KeywordNormalizer {
    fun normalize(value: String): String = BlockingRules.normalize(value)
}
