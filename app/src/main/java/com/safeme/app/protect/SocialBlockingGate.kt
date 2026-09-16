package com.safeme.app.protect

import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo
import com.safeme.app.data.SocialBlockingPrefs

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
    data class TabRule(
        val label: Regex,
        val tokenHints: List<String> = emptyList(),
        /**
         * Fully-qualified view ids of the vertical's KNOWN fullscreen surfaces
         * (e.g. YouTube's documented Shorts player ids). Probed via the
         * framework's deep single-IPC search — any depth, no BFS budget — and
         * still gated by the >=65%-screen area verification. Empty = no-op.
         */
        val knownIds: List<String> = emptyList(),
    )

    /**
     * Allow-list for tab gating — exactly 3 logical features (plus Facebook
     * Lite alias). TikTok / Instagram intentionally absent (whole-app only).
     * Adding a new tab-blocked app = one entry here + one prefs flag + one UI row.
     */
    val TAB_RULES: Map<String, Pair<SocialVertical, TabRule>> = mapOf(
        // tokenHints: lowercase fragments matched against viewIdResourceName /
        // className of FULLSCREEN player nodes (Shorts infra ids are "reel_*"/
        // "shorts_*", FB Reel views contain "reel", Spotlight contains "spotlight").
        // The >=65%-screen-area bound in findFullscreenTokenNode is what keeps
        // Home-feed shelf cards and thumbnails from ever firing them.
        "com.google.android.youtube" to (SocialVertical.SHORTS to TabRule(
            Regex("""\bshorts\b""", RegexOption.IGNORE_CASE),
            listOf("shorts", "reel"),
            // Documented Shorts surfaces (stable since ~2021, StackOverflow 2025).
            listOf(
                "com.google.android.youtube:id/reel_watch_fragment_root",
                "com.google.android.youtube:id/reel_recycler",
            ),
        )),
        "com.facebook.katana" to (SocialVertical.REELS to TabRule(Regex("""\breels\b""", RegexOption.IGNORE_CASE), listOf("reel"))),
        "com.facebook.lite" to (SocialVertical.REELS to TabRule(Regex("""\breels\b""", RegexOption.IGNORE_CASE), listOf("reel"))),
        "com.snapchat.android" to (SocialVertical.SPOTLIGHT to TabRule(Regex("""\bspotlight\b""", RegexOption.IGNORE_CASE), listOf("spotlight"))),
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

    /**
     * Pure whole-app decision — throttle is applied outside (persistent launch block).
     * Family-aware: blocking "TikTok" covers every installed TikTok variant
     * (musically / aweme / trill / go / lite) and vice versa, so a toggled row
     * always enforces on the package the device actually runs. Exempt precedence
     * is unchanged.
     */
    fun isWholeAppBlocked(pkg: String, wholeBlocked: Set<String>): Boolean {
        if (pkg in SYSTEM_EXEMPT) return false
        return SocialBlockingPrefs.isFamilyBlocked(pkg, wholeBlocked)
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
        // L1/L1b first — every path that fires today fires identically.
        findFirstSelectedTab(root, rule.label, screenWidthPx, screenHeightPx)?.let { return it }
        // knownIds fast-path: framework deep search — immune to the BFS budget
        // that Home-feed debris can exhaust mid-transition.
        findKnownIdFullscreenNode(root, rule.knownIds, screenWidthPx, screenHeightPx)?.let { return it }
        // L2: fullscreen player token (Shorts opened from Home, Reel, Spotlight)
        // — only consulted where L1 found nothing.
        return findFullscreenTokenNode(root, rule.tokenHints, screenWidthPx, screenHeightPx)
    }

    /**
     * knownIds fast-path: for each documented fullscreen-surface id, one
     * framework search ([AccessibilityNodeInfo.findAccessibilityNodeInfosByViewId]
     * — single IPC, any depth, no probe budget), then the SAME >=65%-screen
     * area verification as the BFS token scan, so this path can never fire on
     * shelf cards or thumbnails. Empty list = no-op; fail-open on dying
     * windows; every returned node recycled.
     */
    private fun findKnownIdFullscreenNode(
        root: AccessibilityNodeInfo,
        knownIds: List<String>,
        screenWidthPx: Int,
        screenHeightPx: Int,
    ): TabHit? {
        if (knownIds.isEmpty() || screenWidthPx <= 0 || screenHeightPx <= 0) return null
        val screenArea = screenWidthPx.toLong() * screenHeightPx
        val rect = Rect()
        for (id in knownIds) {
            val found = runCatching { root.findAccessibilityNodeInfosByViewId(id) }.getOrNull() ?: continue
            for (node in found) {
                node ?: continue
                val big = runCatching {
                    node.getBoundsInScreen(rect)
                    rect.width() > 0 && rect.height() > 0 &&
                        rect.width().toLong() * rect.height() >= screenArea * MIN_FULLSCREEN_AREA_FRACTION
                }.getOrDefault(false)
                if (big) {
                    val top = rect.top
                    runCatching { node.recycle() }
                    return TabHit(if (top > 0) top else null)
                }
                runCatching { node.recycle() }
            }
        }
        return null
    }

    /**
     * L2: BFS for a node whose viewIdResourceName or className contains one of
     * [tokenHints] AND whose bounds cover at least [MIN_FULLSCREEN_AREA_FRACTION]
     * of the screen — i.e. the vertical's fullscreen player. Returns a full-cover
     * TabHit (top of the player, null when it starts at the screen top). Bounded
     * like every other probe, fail-open, and a no-op while tokenHints is empty.
     */
    private fun findFullscreenTokenNode(
        root: AccessibilityNodeInfo,
        tokenHints: List<String>,
        screenWidthPx: Int,
        screenHeightPx: Int,
    ): TabHit? {
        if (tokenHints.isEmpty() || screenWidthPx <= 0 || screenHeightPx <= 0) return null
        val screenArea = screenWidthPx.toLong() * screenHeightPx
        val deque: ArrayDeque<Pair<AccessibilityNodeInfo, Int>> = ArrayDeque()
        deque.add(root to 0)
        var scanned = 0
        val visited = mutableSetOf<Int>()
        val rect = Rect()
        while (deque.isNotEmpty() && scanned < TOKEN_SCAN_MAX_NODES) {
            val (node, depth) = deque.removeFirst()
            if (depth > TOKEN_SCAN_MAX_DEPTH) continue
            val id = System.identityHashCode(node)
            if (!visited.add(id)) continue
            scanned++

            val viewId = runCatching { node.viewIdResourceName }.getOrNull()?.lowercase()
            val cls = runCatching { node.className?.toString() }.getOrNull()?.lowercase()
            val tokenHit = (viewId != null && tokenHints.any { it in viewId }) ||
                (cls != null && tokenHints.any { it in cls })
            if (tokenHit) {
                val big = runCatching {
                    node.getBoundsInScreen(rect)
                    rect.width() > 0 && rect.height() > 0 &&
                        rect.width().toLong() * rect.height() >= screenArea * MIN_FULLSCREEN_AREA_FRACTION
                }.getOrDefault(false)
                if (big) return TabHit(if (rect.top > 0) rect.top else null)
            }
            for (i in 0 until node.childCount) {
                val child = try { node.getChild(i) } catch (_: Throwable) { null } ?: continue
                deque.add(child to depth + 1)
            }
        }
        return null
    }

    /** Fraction of screen area a token node must cover to count as the fullscreen player. */
    private const val MIN_FULLSCREEN_AREA_FRACTION = 0.65

    /**
     * The L2a token scan runs with its OWN budget, larger than L1's 200/12:
     * it only executes after L1 + knownIds missed, behind the 250 ms probe
     * throttle and the 4 s gate cooldown, and a Home→Shorts transition tree
     * can carry substantial feed debris before the player subtree. L1 keeps
     * its original tighter limits — tab detection behavior is untouched.
     */
    const val TOKEN_SCAN_MAX_NODES = 400
    const val TOKEN_SCAN_MAX_DEPTH = 14

    /** L2b helpers — pure so they are unit-testable without a11y trees. */

    /** True when [cls] (window class) contains any tokenHint of [vertical]. */
    fun matchesToken(cls: String?, vertical: SocialVertical): Boolean {
        cls ?: return false
        val rule = TAB_RULES.values.firstOrNull { it.first == vertical }?.second ?: return false
        if (rule.tokenHints.isEmpty()) return false
        val lower = cls.lowercase()
        return rule.tokenHints.any { it in lower }
    }

    /** True when the click landed in the bottom 20% of the screen (nav-bar region). */
    fun isBottomNavClick(clickedCenterY: Int?, screenHeightPx: Int): Boolean =
        clickedCenterY != null && screenHeightPx > 0 &&
            clickedCenterY >= screenHeightPx * 4 / 5

    /** True when [clickedTexts] match the vertical's tab caption regex. */
    fun labelMatchesVertical(clickedTexts: List<String>, vertical: SocialVertical): Boolean {
        if (clickedTexts.isEmpty()) return false
        val rule = TAB_RULES.values.firstOrNull { it.first == vertical }?.second ?: return false
        return rule.label.containsMatchIn(clickedTexts.joinToString(" "))
    }

    /**
     * L2b: deterministic user-intent signal for navs that expose NO selection
     * state — a click inside the bottom-nav region whose collected texts match
     * the vertical's caption. The region bound keeps video-title taps (a video
     * named "Shorts…") from ever gating.
     */
    fun isNavClickFor(
        clickedTexts: List<String>,
        clickedCenterY: Int?,
        screenHeightPx: Int,
        vertical: SocialVertical,
    ): Boolean =
        isBottomNavClick(clickedCenterY, screenHeightPx) && labelMatchesVertical(clickedTexts, vertical)

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
            if (text.isNotEmpty() && pattern.containsMatchIn(text) &&
                (isSelfOrAncestorSelected(node) || hasSelectedChild(node))
            ) {
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
     * L1b: some nav implementations (notably Snapchat's) mark a CHILD of the
     * labeled container selected instead of the labeled node or its ancestors —
     * the upward walk cannot see that. One level down, bounded to small child
     * counts so feed containers can never satisfy it.
     */
    private fun hasSelectedChild(node: AccessibilityNodeInfo): Boolean {
        val count = runCatching { node.childCount }.getOrDefault(0)
        if (count <= 0 || count > 6) return false
        for (i in 0 until count) {
            val child = runCatching { node.getChild(i) }.getOrNull() ?: continue
            val sel = runCatching { child.isSelected || child.isChecked }.getOrDefault(false)
            runCatching { child.recycle() }
            if (sel) return true
        }
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
     * Grace window for covers raised by the L2b nav-click signal on trees that
     * expose no selection state: the tab watch must not probe-dismiss them
     * before the tree has had a chance to expose confirming evidence.
     */
    const val UNCONFIRMED_COVER_GRACE_MS = 2_000L

    /**
     * Key for tab cooldown — pkg|vertical so YouTube Shorts and Snapchat Spotlight don't share cooldown.
     * Whole gate uses plain pkg key (pkg alone) and is persistent after first block (service keeps lastBlockKey).
     */
    fun throttleKey(pkg: String, vertical: SocialVertical): String = "$pkg|${vertical.name}"

    fun shouldThrottleAppContentRecheck(lastMs: Long, nowMs: Long, isClick: Boolean): Boolean =
        !isClick && nowMs - lastMs < APP_CONTENT_RECHECK_THROTTLE_MS
}
