# Social Media Blocking — MINIMAL Fix Plan (Smallest Viable Solution)

> **Date:** 2026-09-16 · **Branch target:** `agent/social-blocking-fixes` (from `main` @ `faf4ede`)
> **Guiding principle:** smallest change that fixes the behavior. Reuse what exists; add code only where nothing existing can be adjusted. **~170 lines total, 4 files edited, 0 new production files.**
> **Status:** PLAN ONLY — awaiting `Execute`. This is the authoritative plan; the earlier `SOCIAL_MEDIA_BLOCKING_FIXES_FINAL_PLAN.md` is kept for reference but its larger mechanisms are **deferred** (see §6).
> **Baseline already verified:** toolchain bootstrapped (JDK 25.0.4 + SDK 36); `:app:testDebugUnitTest` on `main` @ `faf4ede` = **BUILD SUCCESSFUL, 317 tests / 0 failures**.

---

## Root causes (unchanged, all verified line-by-line)

1. **Icons:** `SocialBlockingScreen.kt` renders `label.take(1)` letters at L378 (LaunchRow), L463 (TabRow), L592 (picker). Real icons are never loaded.
2. **Tab block = whole-app block:** (a) `SocialBlockingGate.findTabNode` matches the label *anywhere* in the tree — the bottom-nav captions "Shorts/Reels/Spotlight" are **always present**, so it fires at launch while the user is on Home; (b) `launchSocialTabGate` calls the same full-screen `BlockOverlayController.show()` whose Close launches HOME, and once shown nothing ever removes the cover when the user navigates away.
3. **Not instant:** whole-app gate is evaluated **only** on `TYPE_WINDOW_STATE_CHANGED` (service L671–687). This codebase documents in ≥6 comments that OEMs (Vivo/FuntouchOS) **drop/delay window-state events** — the PU gate has a 250 ms watchdog for exactly this; the social gate has no backstop, so the block waits for the next window transition (= seconds).

---

## Fix 1 — Real app icons (~40 lines, 1 file)

**No new component, no cache layer.** Reuse the pattern already proven in production in
`ScheduleSheets.kt` L397–410, with one correction: decode via `androidx.core.graphics.drawable.toBitmap()`
(core-ktx is already a dependency) instead of `as? BitmapDrawable`, which silently fails for
adaptive icons (most modern apps).

- Add one small composable (~25 lines) **inside the existing `SocialBlockingIcons.kt`**:
  `InstalledAppIcon(packageNames: List<String>, size: Dp, fallback: @Composable () -> Unit)` —
  `produceState` + IO + `getApplicationIcon` on each package in order (first installed wins) →
  `toBitmap()`; on any failure renders the `fallback` slot.
- Three call-site swaps (each ~5 lines): `LaunchRow`, `TabRow`, picker rows. The fallback slot is
  **the exact existing pastel-letter box**, so devices without those apps look identical to today.
- Package lists per row (constants already exist in `SocialBlockingPrefs`):
  TikTok → `TIKTOK_PACKAGES` (5), Instagram → `INSTAGRAM_PACKAGES` (2), X → `com.twitter.android`,
  Reddit → `com.reddit.frontpage`, Twitch → `tv.twitch.android.app`; tab rows:
  `com.google.android.youtube` / `com.facebook.katana` → `com.facebook.lite` / `com.snapchat.android`.
  Extras rows pass their own pkg.

**Deliberately skipped:** `LruCache` (ScheduleSheets runs fine without one; add later only if picker
scroll shows jank), `InstalledApp`/`AppCatalog` changes, family-wide toggling (behavior change — out of scope).

---

## Fix 2 — Tab block only blocks the tab (~80 lines, 3 files)

### 2a. Detection: require the tab to be SELECTED (edit `findTabNode`, ~20 lines)
Keep the existing bounded BFS. One change: when a node's text/desc matches the vertical's label,
**accept it only if `isSelected`/`isChecked` is true on that node or an ancestor ≤3 levels up**
(selection lives on the nav item container; the label on a child TextView). Otherwise keep scanning.
Return the matched node as today **plus the nav-bar bounds**: from the matched node, walk up ≤4
ancestors to the first node ≥90% of screen width → its bounds are the nav bar (fallback: null).

Effects, all in the fail-open direction:
- Launch on Home tab → "Shorts" caption exists but is not selected → **no block** (fixes the complaint).
- Feed video titled "…shorts…" → not selected → **no block** (also fixes a second false-positive vector).
- User taps the Shorts tab → click event (already bypasses the 250 ms throttle) → selected → block within one event round-trip.
- App restored directly onto Shorts → window-state probe sees selected → block.

### 2b. Enforcement: reuse the existing overlay, anchored above the nav bar (~25 lines, `BlockOverlayController`)
- `show()`/`attachOverlay()` gain an optional `coverAboveY: Int? = null`. When set, the window uses
  `width=MATCH_PARENT, height=coverAboveY, gravity=TOP` instead of MATCH_PARENT — **the bottom nav stays
  visible and tappable** (the window simply doesn't span it). Every existing caller passes nothing →
  full-screen behavior byte-identical (default parameter).
- `onClose` lambda (L260) branches on type: `"socialTab"` → new `dismissWithoutHome()`, else `dismiss()`
  (1 line).
- New `dismissWithoutHome()` (~5 lines): `removeOverlay()` + `SafeMeAccessibilityService.onGateDismissed()`.
  Reusing the existing dismissal signal is intentional — it already clears `socialTabCooldown`
  (service L413–417), so re-entering the tab re-blocks instantly with zero new cooldown code.
- A `@Volatile showingType` set synchronously in `show()` (~3 lines) + `isShowingTabCover()` accessor.
- **No new composable** — the existing `BlockOverlay` renders inside the shorter window as-is
  (fills window bounds; dwell/why/Close all keep working). No activity fallback for tab covers
  (failures are re-raised by the watch below).

### 2c. Auto-dismiss when the user leaves the tab (~25 lines, service)
`handleEvent` L555 currently returns while *any* cover is showing. Adjust to:
```
if (isShowing()) {
    if (isShowingTabCover()) handleSocialTabWatch(snapshot)   // ~12 lines
    return
}
```
`handleSocialTabWatch`: foreground pkg (snapshot, fallback one root read) differs from the covered pkg,
**or** `findTabNode` no longer finds the selected tab → `dismissWithoutHome()`. Content events flood
continuously while Shorts plays and on every BACK/tab tap, so this fires within ~100–250 ms of the
user navigating away — no new watchdog job needed.

### 2d. Service call sites (both already exist — parameter additions only, ~10 lines)
`launchSocialTabGate(pkg, vertical, coverAboveY)` passes the nav bounds from 2a (null → full-screen
cover, which is the correct behavior for fullscreen-feed cases where no nav was found). Window-state
and content paths unchanged otherwise.

**Contingency (documented, not pre-built):** if device testing shows a target app's nav does not
expose `isSelected`/`isChecked`, the fallback is to escalate detection for that app only — the pure-core
`isTabActive` design from the FINAL plan is the documented upgrade path. Direction of failure until
then: under-block (tab not covered), never whole-app block.

---

## Fix 3 — Instant whole-app blocking (~20 lines, 1 file)

Two tiny backstops, reusing machinery that already runs — no new job, no new scope:

1. **Content-event backstop (~6 lines).** In `handleEvent`'s non-window-state branch (L556+), first
   statement: `snapshot.pkg` passes `SocialBlockingGate.isWholeAppBlocked` (SYSTEM_EXEMPT included)
   → `launchSocialWholeGate(pkg)` + return. Pure set lookup, no tree walk. Content-changed events
   flood from any app that renders anything, so even when the window-state event is dropped the gate
   fires on the first rendered frame (~100–250 ms, bounded by the framework's 100 ms batch).
2. **Watchdog piggyback (~12 lines).** `startPuWatchdog()` already runs unconditionally every 250 ms
   (verified L345); only the tick body is PU-gated. Insert at the top of `puWatchdogTick()`, before
   `if (!cachedPuEnabled) return`: if social enabled and `rootInActiveWindow`'s pkg (one cheap read,
   fallback `lastForegroundPkg`) is whole-blocked and no cover is showing → `launchSocialWholeGate`.
   This covers the documented Vivo case (hot task-resume with a static screen: window-state dropped
   *and* no content flood) with worst-case exposure ≤250 ms — the same guarantee the PU gate already
   relies on. Existing 4 s key cooldown + `isShowing` dedupe apply unchanged.

**Deliberately skipped** (micro-optimizations, not causes of the seconds-delay): main-thread fast
lane (queue delay is <50 ms; backstops make it moot), DataStore prefs caching (cold read happens once
per process, ~ms), stale-cooldown recovery (dismissal re-arm already covers both dismissal paths;
watchdog re-gate covers a dead cover since `isShowing()==false`).

---

## Code ledger

| File | Change | ~Lines |
|---|---|---|
| `ui/screens/socialblocking/SocialBlockingIcons.kt` | + `InstalledAppIcon` composable | 25 |
| `ui/screens/socialblocking/SocialBlockingScreen.kt` | 3 call-site swaps | 15 |
| `protect/SocialBlockingGate.kt` | selected-ancestor check + nav-bounds walk-up in `findTabNode` | 20 |
| `BlockOverlayController.kt` | optional `coverAboveY`, `dismissWithoutHome`, `showingType`, Close branch | 25 |
| `service/SafeMeAccessibilityService.kt` | tab watch while cover up, coverAboveY plumbing, content backstop, watchdog piggyback | 65 |
| `app/src/test/.../SocialBlockingGateTest.kt` (new test file) | throttle/whole-gate/tab-cooldown pure checks (traversal stays untested, as today) | 40 |
| **Total** | **5 files edited + 1 test file** | **~190** |

For comparison, the deferred FINAL-plan approach was ~600–900 lines with 2 new production files.

## Verification (unchanged bar, less new surface)

1. Full suite stays green: `:app:testDebugUnitTest` (baseline 317/0 already recorded) + new gate tests.
2. `:app:lintDebug` + `:app:assembleDebug`.
3. Manual device checklist (the only way to validate overlay behavior):
   - YouTube launch → free; Home feed with "shorts" in titles → free; tap Shorts → covered above nav ≤250 ms, nav tappable; tap Home → cover gone instantly; re-enter Shorts → instant cover.
   - FB Reels / Snapchat Spotlight same matrix.
   - Whole-app: cold + hot (recents) launch of blocked app → cover ≤300 ms; Close → HOME → relaunch → instant.
   - Regressions: keyword gate, PU Settings gate (incl. while a tab cover is up — PU tick untouched), schedule gate, `blockedToday` behavior.

## Regression guards

- `show()` default parameter → every existing gate (keyword/PU/schedule/whole-app) renders exactly as today.
- Watchdog insertion is additive and returns early when social blocking is disabled/empty; PU logic below it untouched.
- Detection change strictly narrows tab firing (selected-only) — impossible to fire in cases the old code didn't.
- Icon fallback renders the current pastel letter verbatim when no icon resolves.
- No prefs/schema/backup changes.

## Deferred (revisit only if device testing demands)

| Item | Trigger to revisit |
|---|---|
| Fullscreen-feed token detection (L2 from FINAL plan) | deep-linked Shorts/Reels/Spotlight sessions must be covered |
| Nav not exposing `isSelected` on a target app | that app's tab never blocks on device |
| Icon `LruCache` | picker scroll jank observed |
| Pure-core `isTabActive` + Robolectric tree tests | detection needs per-app tuning |
| Compact `TabBlockOverlay` UI | full-size `BlockOverlay` looks wrong in the shorter window |
