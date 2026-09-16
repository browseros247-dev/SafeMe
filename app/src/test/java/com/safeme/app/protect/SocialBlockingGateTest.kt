package com.safeme.app.protect

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
    fun tabRules_tokenHintsReservedEmptyForFullscreenEscalation() {
        for ((pkg, pair) in SocialBlockingGate.TAB_RULES) {
            assertTrue(
                "tokenHints must ship empty until L2 detection is adopted: $pkg",
                pair.second.tokenHints.isEmpty()
            )
        }
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
    }
}
