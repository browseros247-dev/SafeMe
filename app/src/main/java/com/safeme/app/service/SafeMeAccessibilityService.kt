package com.safeme.app.service

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.os.Build
import android.os.SystemClock
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.safeme.app.BlockGateActivity
import com.safeme.app.data.BlockingPrefsState
import com.safeme.app.data.BundledKeywords
import com.safeme.app.data.TitleBlockRule
import com.safeme.app.data.TitleMatchMode
import com.safeme.app.data.blockingPrefs
import com.safeme.app.data.normalizeDomain
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Core blocking engine.
 *
 * Reacts to [AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED], walks the active window's
 * node tree collecting visible text/titles/descriptions and URL-ish strings, then matches
 * them against the merged (bundled + user custom) keyword and website lists. Title rules
 * are matched against the window title only ([AccessibilityEvent.getText] on
 * window-state-changed events), never against body text, so a rule like "Apps" can't fire
 * just because the Settings home list shows an "Apps" entry. On a match it raises the
 * block gate over the offending app by launching [BlockGateActivity].
 *
 * Robustness guarantees:
 *  - Every event is processed inside a try/catch; a malformed event or node tree can never
 *    crash the service.
 *  - DataStore failures degrade to a safe default state (empty lists, blocking enabled).
 *  - Dedup + cooldown prevents re-launching the gate for the same window in a loop.
 *  - The engine skips SafeMe's own package, so the gate itself never re-triggers a block.
 */
class SafeMeAccessibilityService : AccessibilityService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @Volatile
    private var cachedState: BlockingPrefsState? = null

    @Volatile
    private var lastBlockKey: String? = null

    @Volatile
    private var lastBlockAt: Long = 0L

    @Volatile
    private var titleScopeWarned: Boolean = false

    override fun onServiceConnected() {
        super.onServiceConnected()
        serviceScope.launch {
            try {
                blockingPrefs().collect { cachedState = it }
            } catch (t: Throwable) {
                cachedState = BlockingPrefsState()
            }
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        try {
            handleEvent(event)
        } catch (t: Throwable) {
            // Never crash the service on a malformed event.
        }
    }

    private fun handleEvent(event: AccessibilityEvent?) {
        if (event == null) return
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return

        val pkg = event.packageName?.toString() ?: return
        val ownPackage = applicationContext.packageName ?: return
        if (pkg == ownPackage) return

        val state = cachedState ?: return
        if (!state.blockingEnabled) return

        val texts = collectTexts(event)
        if (texts.isEmpty()) return

        val match = findMatch(texts, state)
            ?: findTitleMatchIfSettings(pkg, event, state)
            ?: return
        val key = "$pkg|${match.value}"
        val now = SystemClock.elapsedRealtime()
        if (lastBlockKey == key && now - lastBlockAt < COOLDOWN_MS) return

        lastBlockKey = key
        lastBlockAt = now
        launchGate(pkg, match)
    }

    /**
     * Collects candidate window-title strings for a window-state-changed event.
     * The framework populates [AccessibilityEvent.getText] with the window's
     * title (verified on-device: "Settings", "Apps", "Notifications" for the
     * corresponding Settings pages). On API 33+ the window's own title attribute
     * is used as a fallback when the event carries no text.
     */
    private fun collectTitles(event: AccessibilityEvent): List<String> {
        val out = ArrayList<String>()
        try {
            event.text?.forEach { t ->
                t.toString().takeIf { it.isNotBlank() }?.let { out.add(it) }
            }
        } catch (_: Throwable) {
        }
        if (out.isEmpty() && Build.VERSION.SDK_INT >= 33) {
            try {
                rootInActiveWindow?.window?.title?.toString()
                    ?.takeIf { it.isNotBlank() }?.let { out.add(it) }
            } catch (_: Throwable) {
            }
        }
        return out.distinct()
    }

    private fun collectTexts(event: AccessibilityEvent): List<String> {
        val out = ArrayList<String>()
        try {
            event.text?.forEach { out.add(it.toString()) }
        } catch (_: Throwable) {
        }
        try {
            val root = rootInActiveWindow ?: return out.distinct()
            walk(root, out, 0)
        } catch (_: Throwable) {
        }
        return out.distinct()
    }

    private fun walk(node: AccessibilityNodeInfo, out: MutableList<String>, depth: Int) {
        if (depth > MAX_DEPTH || out.size >= MAX_STRINGS) return
        try {
            node.text?.toString()?.takeIf { it.isNotBlank() }?.let { out.add(it) }
            node.contentDescription?.toString()?.takeIf { it.isNotBlank() }?.let { out.add(it) }
        } catch (_: Throwable) {
        }
        val childCount = try {
            node.childCount
        } catch (_: Throwable) {
            0
        }
        for (i in 0 until childCount) {
            val child = try {
                node.getChild(i)
            } catch (_: Throwable) {
                null
            } ?: continue
            walk(child, out, depth + 1)
        }
    }

    private fun findMatch(
        texts: List<String>,
        state: BlockingPrefsState,
    ): MatchResult? {
        val lowerTexts = texts.map { it.lowercase() }

        // Whitelist keywords override the blocklist entirely.
        if (state.whitelistKeywords.any { wl ->
                val needle = wl.lowercase()
                needle.isNotBlank() && lowerTexts.any { it.contains(needle) }
            }
        ) {
            return null
        }

        // Keyword match: case-insensitive substring against collected text.
        state.blocklistKeywords.forEach { kw ->
            val needle = kw.value.lowercase()
            if (needle.isNotBlank() && lowerTexts.any { it.contains(needle) }) {
                return MatchResult(kw.value, "keyword")
            }
        }
        BundledKeywords.keywords.forEach { kw ->
            val needle = kw.value.lowercase()
            if (needle.isNotBlank() && lowerTexts.any { it.contains(needle) }) {
                return MatchResult(kw.value, "keyword")
            }
        }

        // Website match: domain-suffix match at label boundaries.
        val hosts = lowerTexts.mapNotNull { text ->
            normalizeDomain(text).takeIf { it.isNotEmpty() }
        }
        if (hosts.isEmpty()) return null

        if (state.trustedWebsites.any { trusted ->
                val t = trusted.lowercase()
                t.isNotEmpty() && hosts.any { it == t || it.endsWith(".$t") }
            }
        ) {
            return null
        }

        state.blockedWebsites.forEach { site ->
            val d = site.domain.lowercase()
            if (d.isNotEmpty() && hosts.any { it == d || it.endsWith(".$d") }) {
                return MatchResult(site.domain, "website")
            }
        }
        BundledKeywords.websites.forEach { site ->
            val d = site.domain.lowercase()
            if (d.isNotEmpty() && hosts.any { it == d || it.endsWith(".$d") }) {
                return MatchResult(site.domain, "website")
            }
        }

        return null
    }

    private fun findTitleMatch(
        titles: List<String>,
        rules: List<TitleBlockRule>,
        state: BlockingPrefsState,
    ): MatchResult? {
        val enabledRules = rules.filter { it.enabled }
        if (enabledRules.isEmpty() || titles.isEmpty()) return null
        val lowerTitles = titles.map { it.lowercase() }

        // Whitelist keywords suppress title rules too, mirroring findMatch so the
        // whitelist escape hatch is honored consistently across all match paths.
        if (state.whitelistKeywords.any { wl ->
                val needle = wl.lowercase()
                needle.isNotBlank() && lowerTitles.any { it.contains(needle) }
            }
        ) {
            return null
        }

        enabledRules.forEach { rule ->
            val needle = rule.value.lowercase()
            if (needle.isBlank()) return@forEach
            val matched = when (rule.mode) {
                TitleMatchMode.CONTAINS -> lowerTitles.any { it.contains(needle) }
                TitleMatchMode.EXACT -> lowerTitles.any { it == needle }
                TitleMatchMode.STARTS_WITH -> lowerTitles.any { it.startsWith(needle) }
            }
            if (matched) {
                return MatchResult(rule.value, "title")
            }
        }
        return null
    }

    private fun findTitleMatchIfSettings(
        pkg: String,
        event: AccessibilityEvent,
        state: BlockingPrefsState,
    ): MatchResult? {
        if (!isSettingsPackage(pkg)) {
            // Warn once per service session when a Settings-looking package is seen
            // but isn't allowlisted. On OEM forks that ship Settings under a
            // different package this is the only diagnostic that title blocking
            // can't fire; plain app windows are ignored so the warning stays
            // meaningful instead of firing on every window change.
            if (!titleScopeWarned && looksLikeSettingsPackage(pkg) && state.titleBlockRules.any { it.enabled }) {
                titleScopeWarned = true
                Log.w(
                    TAG,
                    "Title rules configured, but Settings package \"$pkg\" is not in " +
                        "allowlist $SETTINGS_PACKAGES; title blocking won't fire on this device's Settings"
                )
            }
            return null
        }
        return findTitleMatch(collectTitles(event), state.titleBlockRules, state)
    }

    private fun launchGate(pkg: String, match: MatchResult) {
        val intent = Intent(this, BlockGateActivity::class.java).apply {
            addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS
            )
            putExtra(BlockGateActivity.EXTRA_PACKAGE, pkg)
            putExtra(BlockGateActivity.EXTRA_MATCHED, match.value)
            putExtra(BlockGateActivity.EXTRA_TYPE, match.type)
        }
        startActivity(intent)
    }

    override fun onInterrupt() {
        // No-op.
    }

    override fun onUnbind(intent: Intent?): Boolean {
        serviceScope.cancel()
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }

    private data class MatchResult(
        val value: String,
        val type: String,
    )

    private companion object {
        // Known Settings packages. AOSP uses com.android.settings; some OEM
        // forks route Settings sub-pages through a different package and would
        // need to be added here for title blocking to fire on those devices.
        val SETTINGS_PACKAGES = setOf("com.android.settings")
        const val TAG = "SafeMeA11y"
        const val COOLDOWN_MS = 4_000L
        const val MAX_DEPTH = 12
        const val MAX_STRINGS = 200
    }

    private fun isSettingsPackage(pkg: String): Boolean = pkg in SETTINGS_PACKAGES

    private fun looksLikeSettingsPackage(pkg: String): Boolean =
        pkg.contains("settings", ignoreCase = true)
}
