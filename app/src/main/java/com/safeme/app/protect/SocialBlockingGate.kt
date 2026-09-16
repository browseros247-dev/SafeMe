package com.safeme.app.protect

import android.graphics.Rect
import android.view.accessibility.AccessibilityEvent
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
         * RESERVED data slot (kept for growth; unused by the engine since
         * [V11]). Formerly: fully-qualified ids of known fullscreen surfaces
         * for a tree-scan fast-path. Tree scans are z-order-blind — a
         * retained Shorts fragment behind Home is VISIBLE-flagged with
         * fullscreen bounds — so fullscreen detection is event-source
         * evidence only (see [findActiveTab]). Empty = no-op.
         */
        val knownIds: List<String> = emptyList(),
    )

    /**
     * Allow-list for tab gating — exactly 3 logical features (plus Facebook
     * Lite alias). TikTok / Instagram intentionally absent (whole-app only).
     * Adding a new tab-blocked app = one entry here + one prefs flag + one UI row.
     */
    val TAB_RULES: Map<String, Pair<SocialVertical, TabRule>> = mapOf(
        // tokenHints: lowercase fragments matched against an event SOURCE's
        // viewIdResourceName / className ([V11] source-evidence semantics;
        // Shorts infra ids are "reel_*"/"shorts_*", FB Reel views contain
        // "reel", Spotlight contains "spotlight"). The >=80%-screen-area +
        // visibility bound in isFullscreenSourceEvidence is what keeps Home
        // shelf cards, headers and inline players from ever firing them.
        "com.google.android.youtube" to (SocialVertical.SHORTS to TabRule(
            Regex("""\bshorts\b""", RegexOption.IGNORE_CASE),
            listOf("shorts", "reel"),
            // [V11] knownIds emptied — the tree-scan fast-path was deleted:
            // YouTube retains the Shorts fragment (reel_watch_fragment_root)
            // behind Home, VISIBLE-flagged with fullscreen bounds, and
            // isVisibleToUser cannot see z-order occlusion, so ANY tree-scan
            // id lookup gates Home. Fullscreen detection = event-source
            // evidence only (an occluded surface emits no events).
            emptyList(),
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
     * [coverAboveY] — screen Y of the bottom-nav bar's top edge. NOTE (V9):
     * the service now always presents FULL-screen tab covers (identical block
     * screen to every other gate); this value is retained for diagnostics and
     * a possible return to scoped covers. Null when no
     * nav bar could be identified (fullscreen feed) → caller covers the full
     * screen, which is correct because the whole screen IS the blocked surface.
     */
    class TabHit(val coverAboveY: Int?, val matchedVia: String? = null)

    /**
     * [V11] Fullscreen-surface evidence captured by the service from the
     * triggering event's OWN source node (scoped to tab-gate packages,
     * rate-limited; null for every other event). [tokenMatched] = source
     * viewId/className contains a vertical token ([matchesToken]); the int
     * bounds + [isVisible] feed the unchanged [isPlausibleFullscreenSurface]
     * decision table; [idOrCls] is fire-time attribution only.
     */
    data class SourceEvidence(
        val tokenMatched: Boolean,
        val isVisible: Boolean,
        val left: Int,
        val top: Int,
        val right: Int,
        val bottom: Int,
        val idOrCls: String,
    )

    /**
     * [V11] Pure fullscreen decision on the event's own source — the SINGLE
     * fullscreen rule (no tree scans: they are z-order-blind). An occluded
     * surface (e.g. the retained Shorts fragment behind Home) emits no
     * accessibility events, so it can never be a source; Home shelf/inline
     * sources fail the >=0.80 area bound geometrically.
     */
    fun isFullscreenSourceEvidence(
        evidence: SourceEvidence?,
        screenWidthPx: Int,
        screenHeightPx: Int,
    ): Boolean =
        evidence != null && evidence.tokenMatched &&
            isPlausibleFullscreenSurface(
                evidence.isVisible,
                evidence.left, evidence.top, evidence.right, evidence.bottom,
                screenWidthPx, screenHeightPx,
            )

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
        evidence: SourceEvidence? = null,
    ): TabHit? {
        val rule = TAB_RULES.values.firstOrNull { it.first == vertical }?.second ?: return null
        // L1/L1b first — every path that fires today fires identically.
        findFirstSelectedTab(root, rule.label, screenWidthPx, screenHeightPx)?.let { return it }
        // [V11] Fullscreen detection = evidence from the triggering event's
        // OWN source node — never a tree scan. The former knownIds fast-path
        // and BFS token scan were both z-order-blind: YouTube's retained
        // Shorts fragment behind Home passes isVisibleToUser with fullscreen
        // bounds and gated Home on every probe. An occluded surface emits no
        // events, so it can never be an event source.
        if (isFullscreenSourceEvidence(evidence, screenWidthPx, screenHeightPx)) {
            return TabHit(null, matchedVia = "srcToken(${evidence?.idOrCls})")
        }
        // [V13] L2b persistent Shorts player — BlockerX proven reel_recycler id, visible + fullscreen.
        // Fixes: Shorts via Home tab (selected=Home, not Shorts) + continuation after Close (no token events).
        // Scoped to SHORTS vertical only, area check rejects Home shelf cards.
        if (vertical == SocialVertical.SHORTS) {
            findShortsPlayerNode(root, screenWidthPx, screenHeightPx)?.let { return it }
        }
        return null
    }

    /**
     * Fraction of screen area a token node must cover to count as the
     * fullscreen player. [V10] Raised 0.65 → 0.80: the Home Shorts shelf
     * (tall cards + header) reaches ~65–75% on smaller/16:9 devices while
     * genuinely visible mid-scroll; a real fullscreen player is ≈100% of
     * displayMetrics area (its bounds include the system bars). Clean
     * separation in both directions.
     */
    private const val MIN_FULLSCREEN_AREA_FRACTION = 0.80

    /**
     * Visibility-verified fullscreen acceptance — the SINGLE fullscreen rule.
     * [V11] Applied to the triggering event's OWN source node (see
     * [isFullscreenSourceEvidence]), never to a tree scan. A surface qualifies
     * only when it is VISIBLE to the user (isVisibleToUser: attached + VISIBLE
     * + alpha>0), covers >=80% of the screen, and its bounds intersect the
     * visible screen.
     *
     * Source-evidence semantics also defeat the case isVisibleToUser CANNOT
     * see (a retained Shorts fragment behind Home stays VISIBLE-flagged with
     * fullscreen bounds): an occluded surface emits no accessibility events,
     * so it can never be an event source. Invisible preloads (alpha-0 /
     * off-screen layout) are rejected by the visibility + bounds legs.
     * Fail-closed for this check: unknown visibility/bounds -> no gate. Pure —
     * unit-tested decision table.
     */
    fun isPlausibleFullscreenSurface(
        isVisible: Boolean,
        left: Int, top: Int, right: Int, bottom: Int,
        screenWidthPx: Int, screenHeightPx: Int,
    ): Boolean {
        if (!isVisible) return false
        if (screenWidthPx <= 0 || screenHeightPx <= 0) return false
        val w = right - left
        val h = bottom - top
        if (w <= 0 || h <= 0) return false
        if (w.toLong() * h < screenWidthPx.toLong() * screenHeightPx * MIN_FULLSCREEN_AREA_FRACTION) return false
        // Bounds must intersect the visible screen — off-screen preloads have
        // full-size bounds positioned outside it.
        return right > 0 && bottom > 0 && left < screenWidthPx && top < screenHeightPx
    }

    /** [V13] YouTube Shorts player ids — BlockerX proven, safe vs Home FP. reel_recycler is the RecyclerView inside Shorts player, not retained behind Home (unlike reel_watch_fragment_root which caused V10/V11 Home FP). */
    private val YOUTUBE_SHORTS_PLAYER_IDS = listOf("reel_recycler")

    /**
     * [V13] Persistent Shorts player detection — finds reel_recycler id that is visible + fullscreen.
     * Uses framework's indexed findAccessibilityNodeInfosByViewId (O(1) hash, not BFS). Fail-open, recycles.
     * Only for YouTube SHORTS vertical, called from findActiveTab L2b.
     * Area check rejects Home shelf cards (5-35% area). isVisibleToUser rejects invisible preloads.
     * BlockerX uses same id with no Home FP reports.
     */
    fun findShortsPlayerNode(root: AccessibilityNodeInfo, screenWidthPx: Int, screenHeightPx: Int): TabHit? {
        for (id in YOUTUBE_SHORTS_PLAYER_IDS) {
            val nodes = try {
                root.findAccessibilityNodeInfosByViewId("com.google.android.youtube:id/$id")
            } catch (_: Throwable) {
                null
            } ?: continue
            for (n in nodes) {
                try {
                    if (!runCatching { n.isVisibleToUser }.getOrDefault(false)) continue
                    val rect = Rect()
                    val ok = runCatching { n.getBoundsInScreen(rect) }.isSuccess
                    if (!ok) continue
                    if (isPlausibleFullscreenSurface(true, rect.left, rect.top, rect.right, rect.bottom, screenWidthPx, screenHeightPx)) {
                        return TabHit(null, matchedVia = "reel_recycler")
                    }
                } finally {
                    try {
                        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.TIRAMISU) n.recycle()
                    } catch (_: Throwable) {}
                }
            }
        }
        return null
    }

    /** L2b helpers — pure so they are unit-testable without a11y trees. */

    /**
     * True when [cls] contains any tokenHint of [vertical]. [V11] semantics:
     * [cls] is an event SOURCE's viewId or className — content/click events
     * carry the source view's identity, never the window class. This is the
     * live token matcher for source evidence (see [isFullscreenSourceEvidence]).
     */
    fun matchesToken(cls: String?, vertical: SocialVertical): Boolean {
        cls ?: return false
        val rule = TAB_RULES.values.firstOrNull { it.first == vertical }?.second ?: return false
        if (rule.tokenHints.isEmpty()) return false
        val lower = cls.lowercase()
        return rule.tokenHints.any { it in lower }
    }

    /**
     * True when the click landed in the bottom 20% of the screen (nav-bar
     * region) AND the clicked node is nav-item sized (<= 20% of screen height).
     * The height cap keeps bottom-of-feed video/shelf cards (>= 25% screen
     * height) from impersonating a nav tap — in either direction (gating OR
     * dismissing a cover).
     */
    fun isBottomNavClick(clickedCenterY: Int?, clickedHeightPx: Int?, screenHeightPx: Int): Boolean =
        clickedCenterY != null && clickedHeightPx != null && screenHeightPx > 0 &&
            clickedCenterY >= screenHeightPx * 4 / 5 &&
            clickedHeightPx in 1..(screenHeightPx / 5)

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
        clickedHeightPx: Int?,
        screenHeightPx: Int,
        vertical: SocialVertical,
    ): Boolean =
        isBottomNavClick(clickedCenterY, clickedHeightPx, screenHeightPx) &&
            labelMatchesVertical(clickedTexts, vertical)

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
                // [V10] Accept ONLY in bottom-nav context: the selected caption
                // must sit inside the full-width bottom bar (navBarTopAbove,
                // <=4 hops up). A channel page's "Shorts" TAB STRIP (top of
                // page) is browsing chrome, not the Shorts destination —
                // covering it was the channel-page false positive. No nav
                // context → keep scanning, never gate.
                val navTop = navBarTopAbove(node, screenWidthPx, screenHeightPx)
                if (navTop != null) return TabHit(navTop, matchedVia = "navTab")
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
     * [V11] Probe window after a navigation event — covers Home→Shorts
     * player inflation after a tap without polling the tree during scroll.
     * Outside navigation context (and without a token-bearing event source)
     * the tab gate never touches the tree: sustained feed scrolling cannot
     * gate, whatever the app keeps in its tree.
     */
    const val TRANSITION_GRACE_MS = 1_500L

    /**
     * Key for tab cooldown — pkg|vertical so YouTube Shorts and Snapchat Spotlight don't share cooldown.
     * Whole gate uses plain pkg key (pkg alone) and is persistent after first block (service keeps lastBlockKey).
     */
    fun throttleKey(pkg: String, vertical: SocialVertical): String = "$pkg|${vertical.name}"

    fun shouldThrottleAppContentRecheck(lastMs: Long, nowMs: Long, isClick: Boolean): Boolean =
        !isClick && nowMs - lastMs < APP_CONTENT_RECHECK_THROTTLE_MS

    /**
     * [V11] Navigation-class events — the ONLY contexts in which the tab
     * gate probes the tree (plus [TRANSITION_GRACE_MS] after one, plus
     * token-bearing sources). BlockerX-proven mechanism: their detector
     * accepts exactly {WINDOW_STATE_CHANGED, VIEW_CLICKED,
     * VIEW_LONG_CLICKED, VIEW_FOCUSED} and never evaluates on scroll.
     * VIEW_SELECTED is intentionally absent (not subscribed in our
     * accessibility config; touch nav taps always emit VIEW_CLICKED).
     */
    fun isNavigationEventType(type: Int): Boolean =
        type == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED ||
            type == AccessibilityEvent.TYPE_VIEW_CLICKED ||
            type == AccessibilityEvent.TYPE_VIEW_LONG_CLICKED ||
            type == AccessibilityEvent.TYPE_VIEW_FOCUSED
}
