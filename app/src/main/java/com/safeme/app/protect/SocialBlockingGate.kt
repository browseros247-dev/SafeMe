package com.safeme.app.protect

import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo

/**
 * Social Media Blocking — two gates, fail-open, throttle-aware.
 *
 * Whole-app gate:  pkg in wholeBlocked → block on TYPE_WINDOW_STATE_CHANGED (persistent).
 * Tab gate: only allow-listed pkgs (YouTube/Facebook/Snapchat) → block ONLY while the
 *           vertical's tab is the SELECTED bottom-nav tab, and cover only the area
 *           ABOVE the nav bar (nav stays visible & tappable — Feed/Messages/Profile
 *           remain usable). A label merely being present in the tree (bottom-nav
 *           captions always are!) is NOT a block condition — that was the
 *           whole-app-blocked-at-launch false positive.
 *
 * TikTok + Instagram NEVER in TAB_RULES (product spec: they live whole-app only).
 * System packages (systemui, settings, safeme itself) are exempt from whole gate to avoid bricking.
 */
object SocialBlockingGate {

    /** Tabs that can be gated — null means device keeps useful functions. */
    enum class SocialVertical { SHORTS, REELS, SPOTLIGHT }

    /**
     * Per-vertical detection rule.
     *
     * [label] — word-boundary regex matching the tab caption (kept short to
     * avoid generic matches; selection state is what actually gates).
     * [tokenHints] — reserved slot for the future fullscreen-feed detector
     * (view-id/class tokens like "reel"/"spotlight" when no nav bar is
     * present). Ships empty: no behavior until the escalation is adopted,
     * so adding L2 detection later is a data edit, not an engine change.
     */
    data class TabRule(val label: Regex, val tokenHints: List<String> = emptyList())

    /**
     * Allow-list for tab gating — exactly 3 logical features (plus Facebook
     * Lite alias). TikTok / Instagram intentionally absent (whole-app only).
     * Adding a new tab-blocked app = one entry here + one prefs flag + one UI row.
     */
    val TAB_RULES: Map<String, Pair<SocialVertical, TabRule>> = mapOf(
        "com.google.android.youtube" to (SocialVertical.SHORTS to TabRule(Regex("""\bshorts\b""", RegexOption.IGNORE_CASE))),
        "com.facebook.katana" to (SocialVertical.REELS to TabRule(Regex("""\breels\b""", RegexOption.IGNORE_CASE))),
        "com.facebook.lite" to (SocialVertical.REELS to TabRule(Regex("""\breels\b""", RegexOption.IGNORE_CASE))),
        "com.snapchat.android" to (SocialVertical.SPOTLIGHT to TabRule(Regex("""\bspotlight\b""", RegexOption.IGNORE_CASE))),
    )

    /** Packages never whole-blocked even if user somehow adds them (would brick device). */
    val SYSTEM_EXEMPT: Set<String> = setOf(
        "com.android.systemui",
        "com.safeme.app",
        "com.android.settings",
        "android",
        "com.google.android.packageinstaller",
        "com.android.packageinstaller",
    )

    /** Pure whole-app decision — throttle is applied outside (persistent launch block). */
    fun isWholeAppBlocked(pkg: String, wholeBlocked: Set<String>): Boolean {
        if (pkg in SYSTEM_EXEMPT) return false
        return pkg in wholeBlocked
    }

    /** Returns the vertical if pkg is in allow-list else null (TikTok/Instagram → null even if text matches). */
    fun verticalFor(pkg: String): SocialVertical? = TAB_RULES[pkg]?.first

    /** Checks whether a vertical is enabled in the prefs state. */
    fun isVerticalEnabled(vertical: SocialVertical, youtube: Boolean, facebook: Boolean, snapchat: Boolean): Boolean =
        when (vertical) {
            SocialVertical.SHORTS -> youtube
            SocialVertical.REELS -> facebook
            SocialVertical.SPOTLIGHT -> snapchat
        }

    // ---------- Active-tab detection (selected-state, bound to allow-list) ----------

    /** Selection may live on the nav-item container while the label sits on a child TextView — walk up. */
    private const val SELECTED_ANCESTOR_HOPS = 3

    /** Ancestors inspected when looking for the full-width nav-bar container. */
    private const val NAV_BAR_ANCESTOR_HOPS = 4

    private const val MAX_DEPTH = 12
    private const val MAX_STRINGS = 200

    /**
     * Result of an active-tab probe.
     *
     * [coverAboveY] — screen Y of the bottom-nav bar's top edge; the tab cover
     * spans 0..coverAboveY so the nav stays visible and tappable. Null when no
     * nav bar could be identified (fullscreen feed) → caller covers the full
     * screen, which is correct because the whole screen IS the blocked surface.
     */
    class TabHit(val coverAboveY: Int?)

    /**
     * Finds the vertical's tab node and accepts it ONLY when the node itself or
     * an ancestor ≤[SELECTED_ANCESTOR_HOPS] up is selected/checked — i.e. the
     * user is actually ON that tab. Bottom-nav captions are present on every
     * screen of these apps; presence alone must never gate (that false
     * positive covered the whole app seconds after launch).
     *
     * Bounded depth/strings, fail-open: anything ambiguous → null → no block.
     */
    fun findActiveTab(
        root: AccessibilityNodeInfo,
        vertical: SocialVertical,
        screenWidthPx: Int,
        screenHeightPx: Int,
    ): TabHit? {
        val rule = TAB_RULES.values.firstOrNull { it.first == vertical }?.second ?: return null
        return findFirstSelectedTab(root, rule.label, screenWidthPx, screenHeightPx)
    }

    private fun findFirstSelectedTab(
        root: AccessibilityNodeInfo,
        pattern: Regex,
        screenWidthPx: Int,
        screenHeightPx: Int,
    ): TabHit? {
        val deque: ArrayDeque<Pair<AccessibilityNodeInfo, Int>> = ArrayDeque()
        deque.add(root to 0)
        var scanned = 0
        val visited = mutableSetOf<Int>()
        while (deque.isNotEmpty() && scanned < MAX_STRINGS) {
            val (node, depth) = deque.removeFirst()
            if (depth > MAX_DEPTH) continue
            // Avoid revisiting same node object identity hash.
            val id = System.identityHashCode(node)
            if (!visited.add(id)) continue
            scanned++

            val text = (node.text?.toString().orEmpty() + " " + node.contentDescription?.toString().orEmpty()).trim()
            if (text.isNotEmpty() && pattern.containsMatchIn(text) && isSelfOrAncestorSelected(node)) {
                return TabHit(navBarTopAbove(node, screenWidthPx, screenHeightPx))
            }
            for (i in 0 until node.childCount) {
                val child = try { node.getChild(i) } catch (_: Throwable) { null } ?: continue
                deque.add(child to depth + 1)
            }
        }
        return null
    }

    /** True when [node] or an ancestor ≤[SELECTED_ANCESTOR_HOPS] up reports selected/checked. */
    private fun isSelfOrAncestorSelected(node: AccessibilityNodeInfo): Boolean {
        if (runCatching { node.isSelected || node.isChecked }.getOrDefault(false)) return true
        var parent = runCatching { node.parent }.getOrNull()
        var hops = 0
        while (parent != null && hops < SELECTED_ANCESTOR_HOPS) {
            val selected = runCatching { parent.isSelected || parent.isChecked }.getOrDefault(false)
            if (selected) {
                runCatching { parent.recycle() }
                return true
            }
            val next = runCatching { parent.parent }.getOrNull()
            runCatching { parent.recycle() }
            parent = next
            hops++
        }
        runCatching { parent?.recycle() }
        return false
    }

    /**
     * Screen Y of the bottom-nav bar's top edge, found by walking up from the
     * matched tab node to the first ancestor that is (a) ≥90% of screen width
     * and (b) positioned in the bottom 30% of the screen — the nav-bar
     * container signature. Returns null when no such ancestor exists
     * (fullscreen feed / unexpected tree) → caller falls back to full cover.
     */
    private fun navBarTopAbove(node: AccessibilityNodeInfo, screenWidthPx: Int, screenHeightPx: Int): Int? {
        if (screenWidthPx <= 0 || screenHeightPx <= 0) return null
        val rect = Rect()
        var cur: AccessibilityNodeInfo? = node
        var hops = 0
        while (cur != null && hops <= NAV_BAR_ANCESTOR_HOPS) {
            val ok = runCatching {
                cur.getBoundsInScreen(rect)
                rect.width() >= screenWidthPx * 9 / 10 && rect.top >= screenHeightPx * 7 / 10 && rect.top > 0
            }.getOrDefault(false)
            if (ok) {
                val top = rect.top
                if (cur !== node) runCatching { cur.recycle() }
                return top
            }
            val next = runCatching { cur.parent }.getOrNull()
            if (cur !== node) runCatching { cur.recycle() }
            cur = next
            hops++
        }
        runCatching { cur?.recycle() }
        return null
    }

    // ---------- Throttle helpers (shared with service cooldown map) ----------

    const val APP_CONTENT_RECHECK_THROTTLE_MS = 250L
    const val GATE_COOLDOWN_MS = 4_000L

    /**
     * Key for tab cooldown — pkg|vertical so YouTube Shorts and Snapchat Spotlight don't share cooldown.
     * Whole gate uses plain pkg key (pkg alone) and is persistent after first block (service keeps lastBlockKey).
     */
    fun throttleKey(pkg: String, vertical: SocialVertical): String = "$pkg|${vertical.name}"

    fun shouldThrottleAppContentRecheck(lastMs: Long, nowMs: Long, isClick: Boolean): Boolean =
        !isClick && nowMs - lastMs < APP_CONTENT_RECHECK_THROTTLE_MS
}
