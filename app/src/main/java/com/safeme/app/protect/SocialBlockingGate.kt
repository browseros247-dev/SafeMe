package com.safeme.app.protect

import android.view.accessibility.AccessibilityNodeInfo

/**
 * Social Media Blocking — two gates, fail-open, throttle-aware.
 *
 * Whole-app gate:  pkg in wholeBlocked → block on TYPE_WINDOW_STATE_CHANGED (persistent).
 * Tab gate: only 3 pkgs (YouTube/ Facebook/ Snapchat) → find tab label node (Shorts/Reels/Spotlight)
 *           and overlay that node (keep Feed/Messages/Profile scrollable).
 *
 * TikTok + Instagram NEVER in FEATURE_PACKAGES (product spec: they live whole-app only).
 * System packages (systemui, settings, safeme itself) are exempt from whole gate to avoid bricking.
 */
object SocialBlockingGate {

    /** Tabs that can be gated — null means device keeps useful functions. */
    enum class SocialVertical { SHORTS, REELS, SPOTLIGHT }

    /**
     * Allow-list for tab gating — exactly 3 logical features (plus Facebook Lite alias).
     * TikTok / Instagram intentionally absent (whole-app only).
     */
    val FEATURE_PACKAGES: Map<String, SocialVertical> = mapOf(
        "com.google.android.youtube" to SocialVertical.SHORTS,
        "com.facebook.katana" to SocialVertical.REELS,
        "com.facebook.lite" to SocialVertical.REELS,
        "com.snapchat.android" to SocialVertical.SPOTLIGHT,
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
    fun verticalFor(pkg: String): SocialVertical? = FEATURE_PACKAGES[pkg]

    // ---------- Tab node detection (text/contentDescription regex, bound to allow-list) ----------

    // Keep short — avoid matching generic "Short" elsewhere. Word-boundary + case-insensitive.
    private val SHORTS_TEXT = Regex("""\bshorts\b""", RegexOption.IGNORE_CASE)
    private val REELS_TEXT = Regex("""\breels\b""", RegexOption.IGNORE_CASE)
    private val SPOTLIGHT_TEXT = Regex("""\bspotlight\b""", RegexOption.IGNORE_CASE)

    private const val MAX_DEPTH = 12
    private const val MAX_STRINGS = 200

    /**
     * Searches root for a node whose text/contentDescription matches the vertical's label.
     * Returns the matching node (caller must recycle) or null. Bounded depth/strings, fail-open.
     * Caller overlays the node's parent bounds (keeps scroll/FAB) — this gate only finds the tab label.
     */
    fun findTabNode(root: AccessibilityNodeInfo, vertical: SocialVertical): AccessibilityNodeInfo? {
        val pattern = when (vertical) {
            SocialVertical.SHORTS -> SHORTS_TEXT
            SocialVertical.REELS -> REELS_TEXT
            SocialVertical.SPOTLIGHT -> SPOTLIGHT_TEXT
        }
        return findFirstMatchingNode(root, pattern)
    }

    private fun findFirstMatchingNode(root: AccessibilityNodeInfo, pattern: Regex): AccessibilityNodeInfo? {
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
            if (text.isNotEmpty() && pattern.containsMatchIn(text)) {
                return node // caller owns recycle
            }
            for (i in 0 until node.childCount) {
                val child = try { node.getChild(i) } catch (_: Throwable) { null } ?: continue
                deque.add(child to depth + 1)
            }
        }
        return null
    }

    /** Checks whether a vertical is enabled in the prefs state. */
    fun isVerticalEnabled(vertical: SocialVertical, youtube: Boolean, facebook: Boolean, snapchat: Boolean): Boolean =
        when (vertical) {
            SocialVertical.SHORTS -> youtube
            SocialVertical.REELS -> facebook
            SocialVertical.SPOTLIGHT -> snapchat
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
