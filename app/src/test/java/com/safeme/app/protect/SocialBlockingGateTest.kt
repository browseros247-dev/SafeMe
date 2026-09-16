package com.safeme.app.protect

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
        // L2a token scan has its own larger budget; the fast-path makes the
        // documented YouTube ids budget-independent entirely.
        assertEquals(400, SocialBlockingGate.TOKEN_SCAN_MAX_NODES)
        assertEquals(14, SocialBlockingGate.TOKEN_SCAN_MAX_DEPTH)
    }

    // ---------- knownIds fast-path registry (V7 fix A) ----------

    @Test
    fun knownIds_documentedForYoutubeOnly() {
        assertEquals(
            listOf(
                "com.google.android.youtube:id/reel_watch_fragment_root",
                "com.google.android.youtube:id/reel_recycler",
            ),
            SocialBlockingGate.TAB_RULES.getValue("com.google.android.youtube").second.knownIds,
        )
        // Other verticals keep empty lists → the fast-path is a literal no-op
        // for them (zero behavior change until an id is documented).
        assertTrue(SocialBlockingGate.TAB_RULES.getValue("com.facebook.katana").second.knownIds.isEmpty())
        assertTrue(SocialBlockingGate.TAB_RULES.getValue("com.facebook.lite").second.knownIds.isEmpty())
        assertTrue(SocialBlockingGate.TAB_RULES.getValue("com.snapchat.android").second.knownIds.isEmpty())
    }

    @Test
    fun knownIds_areFullyQualifiedResourceIds() {
        for ((pkg, pair) in SocialBlockingGate.TAB_RULES) {
            for (id in pair.second.knownIds) {
                assertTrue(
                    "knownId must be a fully-qualified resource id ($pkg): $id",
                    id.startsWith("com.google.android.youtube:id/") && id.length > "com.google.android.youtube:id/".length
                )
            }
        }
    }

    @Test
    fun knownIds_tokenHintsStayConsistentForYoutube() {
        // The documented ids' stable fragments are also covered by the BFS
        // token scan — two independent paths to the same surfaces.
        val rule = SocialBlockingGate.TAB_RULES.getValue("com.google.android.youtube").second
        for (id in rule.knownIds) {
            assertTrue(
                "knownId $id must contain a tokenHint",
                rule.tokenHints.any { it in id }
            )
        }
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
    fun bottomNavClick_onlyBottomFifthOfScreen() {
        val h = 2400
        assertTrue(SocialBlockingGate.isBottomNavClick(1920, h))  // exactly 80%
        assertTrue(SocialBlockingGate.isBottomNavClick(2300, h))
        assertFalse(SocialBlockingGate.isBottomNavClick(1919, h)) // just above the region
        assertFalse(SocialBlockingGate.isBottomNavClick(1200, h))
        assertFalse(SocialBlockingGate.isBottomNavClick(null, h))
        assertFalse(SocialBlockingGate.isBottomNavClick(2300, 0)) // unknown screen height → fail closed
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
        // Nav-region click on the blocked caption → gate.
        assertTrue(SocialBlockingGate.isNavClickFor(listOf("Spotlight"), 2280, h, SocialVertical.SPOTLIGHT))
        // Mid-screen click on a video TITLED "Shorts…" → must NOT gate (G2).
        assertFalse(SocialBlockingGate.isNavClickFor(listOf("Epic shorts compilation"), 1100, h, SocialVertical.SHORTS))
        // Nav-region click on a DIFFERENT tab → must NOT gate.
        assertFalse(SocialBlockingGate.isNavClickFor(listOf("Home"), 2280, h, SocialVertical.SHORTS))
        // No bounds (stale source node) → fail closed.
        assertFalse(SocialBlockingGate.isNavClickFor(listOf("Spotlight"), null, h, SocialVertical.SPOTLIGHT))
    }
}
