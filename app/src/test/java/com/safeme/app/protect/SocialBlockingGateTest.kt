package com.safeme.app.protect

import android.view.accessibility.AccessibilityEvent
import com.safeme.app.data.SocialBlockingPrefs
import com.safeme.app.protect.SocialBlockingGate.SocialVertical
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure-logic coverage for the Social Media Blocking gate. The tree-walking
 * probe ([SocialBlockingGate.findActiveTab]) needs a live AccessibilityNodeInfo
 * tree and is validated on-device (manual checklist) — everything that CAN be
 * pure IS pure and covered here, matching the codebase's testing discipline.
 */
class SocialBlockingGateTest {

    // ---------- whole-app decision ----------

    @Test
    fun wholeGate_blocksListedPackage() {
        assertTrue(
            SocialBlockingGate.isWholeAppBlocked(
                "com.zhiliaoapp.musically",
                setOf("com.zhiliaoapp.musically"),
            )
        )
    }

    @Test
    fun wholeGate_allowsUnlistedPackage() {
        assertFalse(
            SocialBlockingGate.isWholeAppBlocked(
                "com.some.other.app",
                setOf("com.zhiliaoapp.musically"),
            )
        )
    }

    @Test
    fun wholeGate_systemExemptNeverBlockedEvenIfListed() {
        for (pkg in SocialBlockingGate.SYSTEM_EXEMPT) {
            assertFalse(
                "system-exempt pkg must never gate: $pkg",
                SocialBlockingGate.isWholeAppBlocked(pkg, setOf(pkg))
            )
        }
    }

    @Test
    fun wholeGate_emptyListBlocksNothing() {
        assertFalse(SocialBlockingGate.isWholeAppBlocked("com.twitter.android", emptySet()))
    }

    // ---------- tab allow-list (TAB_RULES registry) ----------

    @Test
    fun tabRules_exactlyTheThreeVerticalsPlusFacebookLite() {
        assertEquals(4, SocialBlockingGate.TAB_RULES.size)
        assertEquals(
            SocialBlockingGate.SocialVertical.SHORTS,
            SocialBlockingGate.verticalFor("com.google.android.youtube"),
        )
        assertEquals(
            SocialBlockingGate.SocialVertical.REELS,
            SocialBlockingGate.verticalFor("com.facebook.katana"),
        )
        assertEquals(
            SocialBlockingGate.SocialVertical.REELS,
            SocialBlockingGate.verticalFor("com.facebook.lite"),
        )
        assertEquals(
            SocialBlockingGate.SocialVertical.SPOTLIGHT,
            SocialBlockingGate.verticalFor("com.snapchat.android"),
        )
    }

    @Test
    fun tabRules_tikTokAndInstagramAreWholeAppOnly() {
        assertNull(SocialBlockingGate.verticalFor("com.zhiliaoapp.musically"))
        assertNull(SocialBlockingGate.verticalFor("com.ss.android.ugc.aweme"))
        assertNull(SocialBlockingGate.verticalFor("com.instagram.android"))
        assertNull(SocialBlockingGate.verticalFor("com.instagram.lite"))
    }

    @Test
    fun tabRules_tokenHintsPopulatedForFullscreenDetection() {
        // L2 adopted: Shorts infra ids are "reel_*"/"shorts_*", FB Reels views
        // contain "reel", Spotlight surfaces contain "spotlight".
        assertEquals(
            listOf("shorts", "reel"),
            SocialBlockingGate.TAB_RULES.getValue("com.google.android.youtube").second.tokenHints,
        )
        assertEquals(
            listOf("reel"),
            SocialBlockingGate.TAB_RULES.getValue("com.facebook.katana").second.tokenHints,
        )
        assertEquals(
            listOf("reel"),
            SocialBlockingGate.TAB_RULES.getValue("com.facebook.lite").second.tokenHints,
        )
        assertEquals(
            listOf("spotlight"),
            SocialBlockingGate.TAB_RULES.getValue("com.snapchat.android").second.tokenHints,
        )
    }

    @Test
    fun tabRules_labelPatternsAreWordBoundedAndCaseInsensitive() {
        val shorts = SocialBlockingGate.TAB_RULES.getValue("com.google.android.youtube").second.label
        assertTrue(shorts.containsMatchIn("Shorts"))
        assertTrue(shorts.containsMatchIn("YouTube Shorts"))
        assertTrue(shorts.containsMatchIn("SHORTS"))
        assertFalse(shorts.containsMatchIn("Short"))
        assertFalse(shorts.containsMatchIn("Shortstop"))

        val reels = SocialBlockingGate.TAB_RULES.getValue("com.facebook.katana").second.label
        assertTrue(reels.containsMatchIn("Reels"))
        assertFalse(reels.containsMatchIn("Reel"))

        val spotlight = SocialBlockingGate.TAB_RULES.getValue("com.snapchat.android").second.label
        assertTrue(spotlight.containsMatchIn("Spotlight"))
        assertFalse(spotlight.containsMatchIn("Spot"))
    }

    // ---------- vertical enable mapping ----------

    @Test
    fun verticalEnabled_mapsFlagsToVerticals() {
        assertTrue(SocialBlockingGate.isVerticalEnabled(SocialBlockingGate.SocialVertical.SHORTS, youtube = true, facebook = false, snapchat = false))
        assertTrue(SocialBlockingGate.isVerticalEnabled(SocialBlockingGate.SocialVertical.REELS, youtube = false, facebook = true, snapchat = false))
        assertTrue(SocialBlockingGate.isVerticalEnabled(SocialBlockingGate.SocialVertical.SPOTLIGHT, youtube = false, facebook = false, snapchat = true))
        assertFalse(SocialBlockingGate.isVerticalEnabled(SocialBlockingGate.SocialVertical.SHORTS, youtube = false, facebook = true, snapchat = true))
    }

    // ---------- throttle keys & windows ----------

    @Test
    fun throttleKey_isScopedPerPackageAndVertical() {
        val yt = SocialBlockingGate.throttleKey("com.google.android.youtube", SocialBlockingGate.SocialVertical.SHORTS)
        val snap = SocialBlockingGate.throttleKey("com.snapchat.android", SocialBlockingGate.SocialVertical.SPOTLIGHT)
        val fbKatana = SocialBlockingGate.throttleKey("com.facebook.katana", SocialBlockingGate.SocialVertical.REELS)
        val fbLite = SocialBlockingGate.throttleKey("com.facebook.lite", SocialBlockingGate.SocialVertical.REELS)
        assertNotEquals(yt, snap)
        assertNotEquals(fbKatana, fbLite) // Facebook main & Lite don't share a cooldown
        assertEquals("com.google.android.youtube|SHORTS", yt)
    }

    @Test
    fun contentRecheck_throttlesNonClicksButNeverClicks() {
        val now = 100_000L
        // Non-click inside the 250 ms window → throttled.
        assertTrue(
            SocialBlockingGate.shouldThrottleAppContentRecheck(
                lastMs = now - 100L, nowMs = now, isClick = false,
            )
        )
        // Non-click past the window → probe.
        assertFalse(
            SocialBlockingGate.shouldThrottleAppContentRecheck(
                lastMs = now - 251L, nowMs = now, isClick = false,
            )
        )
        // Deliberate click → always probes (deduped by the gate cooldown).
        assertFalse(
            SocialBlockingGate.shouldThrottleAppContentRecheck(
                lastMs = now - 1L, nowMs = now, isClick = true,
            )
        )
    }

    @Test
    fun gateConstants_matchDocumentedCadence() {
        assertEquals(250L, SocialBlockingGate.APP_CONTENT_RECHECK_THROTTLE_MS)
        assertEquals(4_000L, SocialBlockingGate.GATE_COOLDOWN_MS)
        assertEquals(2_000L, SocialBlockingGate.UNCONFIRMED_COVER_GRACE_MS)
        // [V11] Navigation-context probe window (BlockerX mechanism port):
        // outside nav context the tab gate never touches the tree.
        assertEquals(1_500L, SocialBlockingGate.TRANSITION_GRACE_MS)
    }

    // ---------- knownIds fast-path registry (V7 fix A) ----------

    @Test
    fun knownIds_registryEmptyReservedSlot() {
        // [V11] The tree-scan fast-path was deleted (z-order-blind: YouTube's
        // retained Shorts fragment behind Home is VISIBLE-flagged with
        // fullscreen bounds — isVisibleToUser cannot see occlusion). knownIds
        // is a reserved data slot: every vertical ships empty; fullscreen
        // detection is event-source evidence only.
        for ((_, pair) in SocialBlockingGate.TAB_RULES) {
            assertTrue(pair.second.knownIds.isEmpty())
        }
        // tokenHints remain the token registry for source-evidence matching.
        assertEquals(
            listOf("shorts", "reel"),
            SocialBlockingGate.TAB_RULES.getValue("com.google.android.youtube").second.tokenHints,
        )
    }

    // ---------- family-aware whole-app decision (Issue 1 / Fix A) ----------

    @Test
    fun wholeGate_blockedPrimaryCoversInstalledLiteVariant() {
        // Device runs TikTok Lite; the stored set holds the primary package.
        assertTrue(
            SocialBlockingGate.isWholeAppBlocked(
                "com.ss.android.ugc.aweme.lite",
                setOf("com.zhiliaoapp.musically"),
            )
        )
        // And the reverse: stored Go variant gates the primary.
        assertTrue(
            SocialBlockingGate.isWholeAppBlocked(
                "com.zhiliaoapp.musically",
                setOf("com.zhiliaoapp.musically.go"),
            )
        )
    }

    @Test
    fun wholeGate_familyWorksForFacebookInstagramSnapchat() {
        assertTrue(SocialBlockingGate.isWholeAppBlocked("com.facebook.lite", setOf("com.facebook.katana")))
        assertTrue(SocialBlockingGate.isWholeAppBlocked("com.facebook.katana", setOf("com.facebook.lite")))
        assertTrue(SocialBlockingGate.isWholeAppBlocked("com.instagram.lite", setOf("com.instagram.android")))
        assertTrue(SocialBlockingGate.isWholeAppBlocked("com.snapchat.android.lite", setOf("com.snapchat.android")))
        assertTrue(SocialBlockingGate.isWholeAppBlocked("com.snapchat.android", setOf("com.snapchat.android.lite")))
    }

    @Test
    fun wholeGate_familyNeverLeaksAcrossProducts() {
        assertFalse(SocialBlockingGate.isWholeAppBlocked("com.twitter.android", setOf("com.zhiliaoapp.musically")))
        assertFalse(SocialBlockingGate.isWholeAppBlocked("com.zhiliaoapp.musically", setOf("com.instagram.android")))
        assertFalse(SocialBlockingGate.isWholeAppBlocked("com.facebook.katana", setOf("com.instagram.lite")))
        // Unrelated package sharing a prefix is NOT family.
        assertFalse(SocialBlockingGate.isWholeAppBlocked("com.facebook.orca", setOf("com.facebook.katana")))
    }

    // ---------- prefs family helpers (pure core of Fix A / Fix D3) ----------

    @Test
    fun familyOf_mapsEveryVariantToItsFullFamily() {
        val tiktok = SocialBlockingPrefs.TIKTOK_PACKAGES
        for (pkg in tiktok) {
            assertEquals(tiktok, SocialBlockingPrefs.familyOf(pkg))
        }
        assertEquals(SocialBlockingPrefs.INSTAGRAM_PACKAGES, SocialBlockingPrefs.familyOf("com.instagram.lite"))
        assertEquals(SocialBlockingPrefs.FACEBOOK_PACKAGES, SocialBlockingPrefs.familyOf("com.facebook.lite"))
        assertEquals(SocialBlockingPrefs.SNAPCHAT_PACKAGES, SocialBlockingPrefs.familyOf("com.snapchat.android"))
        // Unknown snapchat variant still maps via prefix.
        assertEquals(SocialBlockingPrefs.SNAPCHAT_PACKAGES, SocialBlockingPrefs.familyOf("com.snapchat.future.variant"))
        // No family for everything else.
        assertNull(SocialBlockingPrefs.familyOf("com.twitter.android"))
        assertNull(SocialBlockingPrefs.familyOf("com.reddit.frontpage"))
    }

    @Test
    fun toggleFamilyInSet_addsWholeFamilyThenRemovesWholeFamily() {
        val family = SocialBlockingPrefs.TIKTOK_PACKAGES
        val start = setOf("com.twitter.android")
        val added = SocialBlockingPrefs.toggleFamilyInSet(start, family)
        assertTrue(added.containsAll(family))
        assertTrue(added.contains("com.twitter.android"))
        // Removing triggers when ANY member is present — even a legacy single-variant store.
        val legacy = setOf("com.zhiliaoapp.musically.go", "com.reddit.frontpage")
        val removed = SocialBlockingPrefs.toggleFamilyInSet(legacy, family)
        assertEquals(setOf("com.reddit.frontpage"), removed)
        assertEquals(start, SocialBlockingPrefs.toggleFamilyInSet(added, family) - "com.twitter.android" + "com.twitter.android")
    }

    @Test
    fun toggleFamilyInSet_emptyFamilyIsNoOp() {
        val cur = setOf("a")
        assertEquals(cur, SocialBlockingPrefs.toggleFamilyInSet(cur, emptySet()))
    }

    @Test
    fun isFamilyBlocked_directCore() {
        assertTrue(SocialBlockingPrefs.isFamilyBlocked("com.zhiliaoapp.musically", setOf("com.zhiliaoapp.musically")))
        assertTrue(SocialBlockingPrefs.isFamilyBlocked("com.ss.android.ugc.trill", setOf("com.zhiliaoapp.musically.go")))
        assertFalse(SocialBlockingPrefs.isFamilyBlocked("com.twitter.android", setOf("com.zhiliaoapp.musically")))
        assertFalse(SocialBlockingPrefs.isFamilyBlocked("com.twitter.android", emptySet()))
    }

    // ---------- L2b nav-click helpers (pure) ----------

    @Test
    fun matchesToken_isCaseInsensitiveAndVerticalScoped() {
        assertTrue(SocialBlockingGate.matchesToken("com.snapchat.android.feature.spotlight.SpotlightFragment", SocialBlockingGate.SocialVertical.SPOTLIGHT))
        assertTrue(SocialBlockingGate.matchesToken("com.facebook.video.ReelPlayerView", SocialBlockingGate.SocialVertical.REELS))
        assertTrue(SocialBlockingGate.matchesToken("ReelPlaybackController", SocialBlockingGate.SocialVertical.SHORTS)) // "reel" is a Shorts-infra token too
        assertFalse(SocialBlockingGate.matchesToken("com.google.android.youtube.HomeActivity", SocialBlockingGate.SocialVertical.SHORTS))
        assertFalse(SocialBlockingGate.matchesToken(null, SocialVertical.SHORTS))
        assertFalse(SocialBlockingGate.matchesToken("", SocialVertical.SPOTLIGHT))
    }

    @Test
    fun bottomNavClick_needsNavRegionAndNavItemSize() {
        val h = 2400
        val navH = 170 // real nav item ≈ 7% of screen height
        assertTrue(SocialBlockingGate.isBottomNavClick(1920, navH, h))  // exactly 80%
        assertTrue(SocialBlockingGate.isBottomNavClick(2300, navH, h))
        assertFalse(SocialBlockingGate.isBottomNavClick(1919, navH, h)) // just above the region
        assertFalse(SocialBlockingGate.isBottomNavClick(1200, navH, h))
        assertFalse(SocialBlockingGate.isBottomNavClick(null, navH, h))
        assertFalse(SocialBlockingGate.isBottomNavClick(2300, null, h)) // no bounds → fail closed
        assertFalse(SocialBlockingGate.isBottomNavClick(2300, navH, 0)) // unknown screen height → fail closed
        // V8: bottom-of-feed cards (>= 25% screen height) can never pass.
        assertFalse(SocialBlockingGate.isBottomNavClick(2100, 700, h))
        assertFalse(SocialBlockingGate.isBottomNavClick(2100, 0, h))
        // Boundary: exactly 20% of screen height is still nav-sized; +1 px is not.
        assertTrue(SocialBlockingGate.isBottomNavClick(2100, h / 5, h))
        assertFalse(SocialBlockingGate.isBottomNavClick(2100, h / 5 + 1, h))
    }

    @Test
    fun labelMatchesVertical_wordBoundedAndNonEmpty() {
        assertTrue(SocialBlockingGate.labelMatchesVertical(listOf("Spotlight"), SocialVertical.SPOTLIGHT))
        assertTrue(SocialBlockingGate.labelMatchesVertical(listOf("nav", "Spotlight", "3"), SocialVertical.SPOTLIGHT))
        assertFalse(SocialBlockingGate.labelMatchesVertical(listOf("Home"), SocialVertical.SPOTLIGHT))
        assertFalse(SocialBlockingGate.labelMatchesVertical(listOf("Short"), SocialVertical.SHORTS))
        assertFalse(SocialBlockingGate.labelMatchesVertical(emptyList(), SocialVertical.SHORTS))
    }

    @Test
    fun navClickFor_needsBothNavRegionAndLabel() {
        val h = 2400
        val navH = 170
        val cardH = 700
        // Nav-region click on the blocked caption → gate.
        assertTrue(SocialBlockingGate.isNavClickFor(listOf("Spotlight"), 2280, navH, h, SocialVertical.SPOTLIGHT))
        // Mid-screen click on a video TITLED "Shorts…" → must NOT gate (G2).
        assertFalse(SocialBlockingGate.isNavClickFor(listOf("Epic shorts compilation"), 1100, navH, h, SocialVertical.SHORTS))
        // Nav-region click on a DIFFERENT tab → must NOT gate.
        assertFalse(SocialBlockingGate.isNavClickFor(listOf("Home"), 2280, navH, h, SocialVertical.SHORTS))
        // No bounds (stale source node) → fail closed.
        assertFalse(SocialBlockingGate.isNavClickFor(listOf("Spotlight"), null, navH, h, SocialVertical.SPOTLIGHT))
        // V8: bottom-of-feed card titled "…shorts…" → must NOT gate (height cap).
        assertFalse(SocialBlockingGate.isNavClickFor(listOf("Epic shorts compilation"), 2100, cardH, h, SocialVertical.SHORTS))
    }

    // ---------- V8: visibility-verified fullscreen acceptance ----------

    @Test
    fun fullscreenSurface_rejectsInvisibleFullscreenBounds() {
        val w = 1080; val h = 2400
        // The Home-screen false positive: a PRELOADED Shorts fragment, hidden
        // (alpha-0 → isVisibleToUser=false) but carrying fullscreen bounds.
        assertFalse(SocialBlockingGate.isPlausibleFullscreenSurface(false, 0, 0, w, h, w, h))
        // The genuinely playing Short: visible + fullscreen → accepted.
        assertTrue(SocialBlockingGate.isPlausibleFullscreenSurface(true, 0, 0, w, h, w, h))
    }

    @Test
    fun fullscreenSurface_rejectsOffScreenPreloads() {
        val w = 1080; val h = 2400
        // Laid out below the fold (off-screen preload position).
        assertFalse(SocialBlockingGate.isPlausibleFullscreenSurface(true, 0, h, w, 2 * h, w, h))
        // Laid out to the right of the screen.
        assertFalse(SocialBlockingGate.isPlausibleFullscreenSurface(true, w, 0, 2 * w, h, w, h))
        // Partially off-screen but still covering >=65% → accepted (real players
        // often extend under the status/nav bars).
        assertTrue(SocialBlockingGate.isPlausibleFullscreenSurface(true, 0, -200, w, h - 200, w, h))
    }

    @Test
    fun fullscreenSurface_areaThresholdAndDegenerateCases() {
        val w = 1080; val h = 2400
        // [V10] Threshold raised to 0.80: 79% of screen (oversized shelf band)
        // → rejected; 81% → accepted. A real player is ≈100%.
        assertFalse(SocialBlockingGate.isPlausibleFullscreenSurface(true, 0, 0, w, (h * 0.79).toInt(), w, h))
        assertTrue(SocialBlockingGate.isPlausibleFullscreenSurface(true, 0, 0, w, (h * 0.81).toInt(), w, h))
        // The old 0.65–0.75 shelf band must now be firmly rejected.
        assertFalse(SocialBlockingGate.isPlausibleFullscreenSurface(true, 0, 0, w, (h * 0.65).toInt(), w, h))
        assertFalse(SocialBlockingGate.isPlausibleFullscreenSurface(true, 0, 0, w, (h * 0.75).toInt(), w, h))
        // Degenerate geometry → rejected.
        assertFalse(SocialBlockingGate.isPlausibleFullscreenSurface(true, 0, 0, 0, h, w, h))
        assertFalse(SocialBlockingGate.isPlausibleFullscreenSurface(true, 0, 0, w, 0, w, h))
        // Unknown screen dimensions → fail closed.
        assertFalse(SocialBlockingGate.isPlausibleFullscreenSurface(true, 0, 0, w, h, 0, h))
        assertFalse(SocialBlockingGate.isPlausibleFullscreenSurface(true, 0, 0, w, h, w, 0))
    }

    // ---------- [V11] navigation context + source-evidence acceptance ----------

    @Test
    fun navigationEventTypes_areExactlyTheProbeTriggers() {
        assertTrue(SocialBlockingGate.isNavigationEventType(AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED))
        assertTrue(SocialBlockingGate.isNavigationEventType(AccessibilityEvent.TYPE_VIEW_CLICKED))
        assertTrue(SocialBlockingGate.isNavigationEventType(AccessibilityEvent.TYPE_VIEW_LONG_CLICKED))
        assertTrue(SocialBlockingGate.isNavigationEventType(AccessibilityEvent.TYPE_VIEW_FOCUSED))
        // Scroll/content churn and everything else never opens a probe window.
        assertFalse(SocialBlockingGate.isNavigationEventType(AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED))
        assertFalse(SocialBlockingGate.isNavigationEventType(AccessibilityEvent.TYPE_VIEW_SCROLLED))
        assertFalse(SocialBlockingGate.isNavigationEventType(AccessibilityEvent.TYPE_VIEW_SELECTED))
        assertFalse(SocialBlockingGate.isNavigationEventType(AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED))
        assertFalse(SocialBlockingGate.isNavigationEventType(0))
    }

    @Test
    fun sourceEvidence_acceptsOnlyVisibleTokenFullscreenSources() {
        val w = 1080
        val h = 2400
        fun ev(token: Boolean, vis: Boolean, l: Int, t: Int, r: Int, b: Int) =
            SocialBlockingGate.SourceEvidence(token, vis, l, t, r, b, "com.google.android.youtube:id/reel_watch_fragment_root")
        // Visible fullscreen token source = the vertical's player screen.
        assertTrue(SocialBlockingGate.isFullscreenSourceEvidence(ev(true, true, 0, 0, w, h), w, h))
        // No token (Home feed sources, regular player) → never gates.
        assertFalse(SocialBlockingGate.isFullscreenSourceEvidence(ev(false, true, 0, 0, w, h), w, h))
        // Invisible source (dying window / alpha-0 preload) → never gates.
        assertFalse(SocialBlockingGate.isFullscreenSourceEvidence(ev(true, false, 0, 0, w, h), w, h))
        // Shelf / inline-player sized sources fail the >=0.80 area bound.
        assertFalse(SocialBlockingGate.isFullscreenSourceEvidence(ev(true, true, 0, 1200, w, 1800), w, h))
        // Off-screen positioned sources (preload bounds) → never gate.
        assertFalse(SocialBlockingGate.isFullscreenSourceEvidence(ev(true, true, 0, h, w, 2 * h), w, h))
        // Null evidence (window-state probe, non-social events) → never gates.
        assertFalse(SocialBlockingGate.isFullscreenSourceEvidence(null, w, h))
        // Degenerate screen metrics → fail-closed.
        assertFalse(SocialBlockingGate.isFullscreenSourceEvidence(ev(true, true, 0, 0, w, h), 0, h))
    }

    @Test
    fun sourceEvidence_boundaryReusesFullscreenFraction() {
        val w = 1000
        val h = 1000
        fun ev(bottom: Int) = SocialBlockingGate.SourceEvidence(true, true, 0, 0, w, bottom, "id")
        // Same unit-tested 0.80 boundary as isPlausibleFullscreenSurface.
        assertFalse(SocialBlockingGate.isFullscreenSourceEvidence(ev(790), w, h))
        assertFalse(SocialBlockingGate.isFullscreenSourceEvidence(ev(650), w, h))
        assertFalse(SocialBlockingGate.isFullscreenSourceEvidence(ev(750), w, h))
        assertTrue(SocialBlockingGate.isFullscreenSourceEvidence(ev(810), w, h))
    }
}
