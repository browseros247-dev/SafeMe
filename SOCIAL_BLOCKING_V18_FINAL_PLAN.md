# SOCIAL BLOCKING V18 FINAL PLAN — Launch Block Gate Not Appearing (V17 Regression)

**Date:** 2026-09-17 (Asia/Dhaka)  
**Branch:** `agent/social-blocking-fixes` HEAD `32fee98` (V17 ultimate)  
**Symptom:** "block gate is not appearing at all in lunch Block app" — whole-app social + schedule launch blocks never show overlay after V17.
**Mode:** Plan-Only per user command. No execution until "Execute".

## 1. Deep Reanalysis from Live Code (f20c69d → 32fee98)

### 1.1 V17 Intent (from V17 plan)
- Two-stage overlay: blank black 10-30ms + HOME 50ms = ≤80ms perceived, full ≤150ms
- Multi-source fg, dedicated IO dispatcher, pre-cache prefs, pre-warm Compose, preempt fix, 500L cooldown after HOME success

### 1.2 What V17 Actually Implemented (read live files)

**BlockOverlayController.kt V17:**
- `cachedPrefs`, `instantBlankView`, `preCachePrefs()`, `getCachedPrefsOrDefault()`
- `showInstantLaunchBlock()`: if `showing` → `removeOverlayNowInternal()` immediate, set `showing=true`, `attachInstantBlank()`, then `scope.launch { mainHandler.post { upgradeToFullOverlay() } }`
- `attachInstantBlank()`: black FrameLayout, TYPE_ACCESSIBILITY_OVERLAY, clickable
- `upgradeToFullOverlay()`: remove blank, remove old overlay, `attachOverlay()`
- `removeOverlayNowInternal()`: immediate removal, clears views but keeps showing flag managed by caller
- `removeOverlay()` now keeps `cachedPrefs`/`lastPrefs` (intentional)

**SafeMeAccessibilityService.kt V17:**
- `launchPollerDispatcher = Dispatchers.IO`, `fastModeUntilMs`
- `onServiceConnected`: pre-cache prefs + pre-warm ComposeView dummy
- `startLaunchBlockPoller()`: adaptive 50ms when `now < fastModeUntilMs` else 100ms
- `directPkgLaunchBlockProbe()`: 4-source fg (root + lastFgPkg + UsageStats + AM), 500L dedup, HOME kick before set cooldown, fastMode 2s, then `launchSocialWholeGate()` / `launchScheduleGate()`
- `maybeSocialWholeContentGate()`, `maybeSocialWholeFastLane()`, `maybeScheduleFastLane()`, `socialWatchdogProbe()`: each does 500L dedup + HOME kick + set key + fastMode + call launch*Gate
- `handleEvent` window-state social whole: outer dedup check + HOME + set key + fastMode + `launchSocialWholeGate()`
- `launchScheduleGate()`: 500L dedup + HOME + set key + `showInstantLaunchBlock()`
- `launchSocialWholeGate()`: 500L dedup + HOME + set key + `showInstantLaunchBlock()` + async label fetch (no fastMode set inside)

### 1.3 Root Cause — Double Dedup Kills Every Launch Gate (100% repro)

**Critical bug: cooldown set BEFORE calling launch*Gate, then launch*Gate checks same cooldown and bails.**

Trace for `directPkgLaunchBlockProbe` → social whole:
```
directPkgLaunchBlockProbe:
  if lastKey==key && now-last<500 return   // first check
  performGlobalAction(HOME)
  lastKey=key; lastAt=now; fastMode=now+2000
  launchSocialWholeGate(pkg)               // call

launchSocialWholeGate:
  now2 = elapsedRealtime() // ~1-2ms later
  if lastKey==key && now2-last<500 return  // SECOND check → TRUE → return without overlay!
  // showInstantLaunchBlock never reached
```

Same pattern in:
- `directPkgLaunchBlockProbe` schedule path
- `maybeSocialWholeContentGate`
- `maybeSocialWholeFastLane`
- `maybeScheduleFastLane`
- `socialWatchdogProbe`
- `handleEvent` window-state social whole (outer if + inner dedup)

Result: **every launch path sets cooldown, then immediately calls a method that sees the fresh cooldown and returns.** No blank, no overlay, no fallback activity. User sees app launch then HOME (from poller's HOME kick) but no block screen — perceived as "not appearing at all".

Schedule path identical.

### 1.4 Secondary Issues (not root, but to fix in same minimal change)

- `launchSocialWholeGate` does NOT set `fastModeUntilMs`, but all callers did — after centralization, fastMode must be set inside gate.
- `launchScheduleGate` missing `fastModeUntilMs` set (was set in callers).
- `handleEvent` social whole window-state path duplicates HOME+cooldown logic that should live only in gate.
- `showInstantLaunchBlock` uses `scope.launch { mainHandler.post }` — double hop (Default → Main). Could be just `mainHandler.post` directly. Not breaking, but unnecessary latency ~1-2ms. Keep minimal, but note.
- `attachInstantBlank` uses `context.getSystemService(WINDOW_SERVICE)` — must be AccessibilityService context (has token). All callers pass `this` (service), so OK. No fix needed, but document.

### 1.5 Why Tests Didn't Catch

- `BlockOverlayControllerTest` tests `show()` not `showInstantLaunchBlock` dedup interaction with service cooldowns.
- `SafeMeAccessibilityServiceSocialTest` mocks package checks but not double-dedup timing.
- Unit tests run without WindowManager, so overlay addView path not exercised.
- 357/0/0 still passes — regression is integration-level (service ↔ controller cooldown handshake).

## 2. Constraints (standing + new)

- Minimize Code: prefer config/fix/small-code over large implementation
- Production-Ready & Scalable: reliable, maintainable, growth = data edits
- No Regressions: 357/0/0, lintVital 0, DEX checks, signer unchanged, no influence other features
- Best Practices: single source of truth, easy debugging via logs, fail-closed
- No push/merge without explicit command
- Plan-Only

## 3. Minimal Fix Design — Centralize in launch*Gate (Single Source of Truth)

### 3.1 Principle

**One place does dedup + HOME + cooldown + fastMode + overlay.** Callers become thin wrappers: `if (blocked) launchGate(pkg)`.

This is minimal (remove ~40 lines duplicated HOME/cooldown), production-hardened (no race between caller and callee), and matches original V15 architecture where launch*Gate was the gatekeeper.

### 3.2 Exact Edits (file: SafeMeAccessibilityService.kt)

**A. `launchScheduleGate(pkg: String)` — make it sole owner:**
```kotlin
private fun launchScheduleGate(pkg: String) {
    val now = SystemClock.elapsedRealtime()
    if (lastScheduleBlockKey == pkg && now - lastScheduleBlockAt < 500L) return
    try { performGlobalAction(GLOBAL_ACTION_HOME) } catch (_: Throwable) {}
    lastScheduleBlockKey = pkg
    lastScheduleBlockAt = now
    fastModeUntilMs = now + 2000L
    Log.d(TAG, "schedule gate: $pkg [V18]")
    BlockOverlayController.showInstantLaunchBlock(this, pkg, "", "schedule")
}
```

**B. `launchSocialWholeGate(pkg: String)` — same, add fastMode:**
```kotlin
private fun launchSocialWholeGate(pkg: String) {
    val now = SystemClock.elapsedRealtime()
    val key = "socialWhole|$pkg"
    if (lastSocialWholeBlockKey == key && now - lastSocialWholeBlockAt < 500L) return
    val label = pkg // instant, no PM IPC
    Log.d(TAG, "social whole gate launched (pkg=$pkg) [V18]")
    try { performGlobalAction(GLOBAL_ACTION_HOME) } catch (_: Throwable) {}
    lastSocialWholeBlockKey = key
    lastSocialWholeBlockAt = now
    fastModeUntilMs = now + 2000L
    BlockOverlayController.showInstantLaunchBlock(this, pkg, label, "socialWhole")
    serviceScope.launch { runCatching { packageManager.getApplicationLabel(...) } } // async log only
}
```

**C. `directPkgLaunchBlockProbe()` — strip to thin wrapper:**
```kotlin
private fun directPkgLaunchBlockProbe() {
    if (BlockOverlayController.isShowing() && !isShowingTabCover()) return
    var fgPkg: String? = null
    // 1..4 sources unchanged
    ...
    if (fgPkg == null) return
    if (fgPkg == own) return
    val social = cachedSocialState
    if (social != null && social.enabled && SocialBlockingGate.isWholeAppBlocked(fgPkg, social.wholeBlocked)) {
        Log.d(TAG, "direct poller: gating social whole $fgPkg [V18]")
        launchSocialWholeGate(fgPkg)
        return
    }
    if (isScheduleBlocked(fgPkg)) {
        Log.d(TAG, "direct poller: gating schedule $fgPkg [V18]")
        launchScheduleGate(fgPkg)
        return
    }
}
```
Remove all `now`, `key`, `if dedup`, `HOME`, `last*`, `fastMode` from this method.

**D. `maybeSocialWholeContentGate(snapshot)`:**
```kotlin
private fun maybeSocialWholeContentGate(snapshot: EventSnapshot) {
    val social = cachedSocialState ?: return
    if (!social.enabled || social.wholeBlocked.isEmpty()) return
    val pkg = snapshot.pkg ?: return
    if (!SocialBlockingGate.isWholeAppBlocked(pkg, social.wholeBlocked)) return
    if (BlockOverlayController.isShowing() && !BlockOverlayController.isShowingTabCover()) return
    Log.d(TAG, "social content backstop: gating $pkg [V18]")
    launchSocialWholeGate(pkg)
}
```
Remove dedup/HOME/fastMode.

**E. `maybeSocialWholeFastLane(pkg)`:**
```kotlin
private fun maybeSocialWholeFastLane(pkg: String?) {
    if (pkg == null) return
    val social = cachedSocialState ?: return
    if (!social.enabled || social.wholeBlocked.isEmpty()) return
    if (pkg == own) return
    if (!SocialBlockingGate.isWholeAppBlocked(pkg, social.wholeBlocked)) return
    if (BlockOverlayController.isShowing() && !BlockOverlayController.isShowingTabCover()) return
    Log.d(TAG, "social fast lane: gating $pkg [V18]")
    launchSocialWholeGate(pkg)
}
```

**F. `maybeScheduleFastLane(pkg)`:**
```kotlin
private fun maybeScheduleFastLane(pkg: String?) {
    if (pkg == null) return
    if (pkg == own) return
    if (!isScheduleBlocked(pkg)) return
    if (BlockOverlayController.isShowing() && !BlockOverlayController.isShowingTabCover()) return
    Log.d(TAG, "schedule fast lane: gating $pkg [V18]")
    launchScheduleGate(pkg)
}
```

**G. `socialWatchdogProbe()`:**
```kotlin
private fun socialWatchdogProbe() {
    val social = cachedSocialState ?: return
    if (!social.enabled || social.wholeBlocked.isEmpty()) return
    if (BlockOverlayController.isShowing() && !BlockOverlayController.isShowingTabCover()) return
    var pkg: String? = null
    // identity + 4-source fallback unchanged
    ...
    if (pkg == null) return
    if (pkg == own) return
    if (!SocialBlockingGate.isWholeAppBlocked(pkg, social.wholeBlocked)) return
    Log.d(TAG, "social watchdog: gating $pkg [V18]")
    launchSocialWholeGate(pkg)
}
```

**H. `handleEvent` window-state social whole block (around line 836-854):**
Current:
```kotlin
if (SocialBlockingGate.isWholeAppBlocked(pkg, social.wholeBlocked)) {
    val key = "socialWhole|$pkg"
    val now = ...
    if (!(lastKey==key && now-last<500)) {
        HOME; lastKey=key; lastAt=now; fastMode; launchGate
    }
    return
}
```
Fix to:
```kotlin
if (SocialBlockingGate.isWholeAppBlocked(pkg, social.wholeBlocked)) {
    launchSocialWholeGate(pkg)
    return
}
```

### 3.3 BlockOverlayController.kt — No logic change needed for this bug, but verify:

- `showInstantLaunchBlock` already handles preempt via `removeOverlayNowInternal()` immediate — keep.
- Ensure `removeOverlayNowInternal()` does NOT clear `showing` (caller sets) — current is correct for preempt path.
- Optional micro-opt: change `scope.launch { mainHandler.post { upgrade } }` to `mainHandler.post { upgrade }` to save 1-2ms. Minimal, keep as is for now to minimize diff; can be V18.1 if needed.
- Keep `cachedPrefs`/`instantBlankView` — they are not cause of "not appearing", they are fix for latency.

### 3.4 No New Permissions, No Manifest, No New Files

- UsageStats fallback already optional try/catch — keep.
- No influence on PU/keyword/imgvid/title paths — they use `BlockOverlayController.show()` not `showInstantLaunchBlock()`.

## 4. Verification Plan (after Execute)

1. **Build:** `./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleRelease` (single foreground, 600s+)
   - Expect 357/0/0, lintVital 0 errors, debug lint ≤69 warnings (baseline filtered)
2. **DEX:** `dexdump` check `reel_recycler` present, `fragment_root` absent 0, fast lanes present, signer `347be353...`
3. **Source grep:** `showInstantLaunchBlock`, `attachInstantBlank`, `instantBlankView`, `launchPollerDispatcher`, `fastModeUntilMs` present; `fragment_root` only in comments (no code use)
4. **Logic grep:** Ensure no duplicate `performGlobalAction` + `last*BlockKey` in fast lanes/poller — only in launch*Gate
5. **Manual log reasoning:** After fix, direct poller log → launch gate log → blank attach log should appear in order without early return
6. **APK SHA + signer** record, copy to `/home/user/SafeMe-0.1.0-release.apk`
7. **Commit locally** no push/merge, message `fix(social): V18 — fix V17 double-dedup launch gate never showing`
8. **Workspace size** <10MB

## 5. Alternatives Considered (minimal first)

- **A. Remove dedup from launch*Gate, keep in callers:** Rejected — callers are 5 places, easy to miss one, violates single source of truth, larger diff.
- **B. Keep both but add 10ms delay before second check:** Rejected — race-prone, not production-ready.
- **C. Revert to V15 show() (no blank):** Rejected — would reintroduce 3-5s delay, not meet V17 latency goal, larger regression.
- **D. Rewrite BlockOverlayController to use activity fallback only:** Rejected — loses 10-30ms blank benefit, larger code.

Chosen design is minimal (net -40 lines), production-ready, scalable (new launch block type = add one gate method, not 5 call sites).

## 6. Risks & Mitigations

- **Risk:** Centralized HOME in launch*Gate means HOME happens even when called from fast lane that already did HOME? Mitigation: after fix, HOME only happens once per gate (inside gate), not twice — actually fixes double HOME.
- **Risk:** 500L cooldown too short → tight loop? Mitigation: 500L proven in V17 plan, prevents loop but allows instant re-block; fastMode 2s ensures poller stays 50ms during burst.
- **Risk:** Blank view token invalid on some OEMs → no overlay. Mitigation: existing catch → `launchFallbackActivity` fallback already in `showInstantLaunchBlock` catch block; keep.
- **Risk:** `isShowing()` guard in poller prevents re-gate during dismiss animation (250ms). Mitigation: `dismiss()` sets `showing=false` synchronously (V13 fix), so poller can re-gate immediately — already in place.

## 7. Expected Outcome

- Launch block gate appears **every time** for whole-app social + schedule
- Perceived latency ≤80ms (blank 10-30ms + HOME 50ms) + full ≤150ms
- No regression on tab covers, PU, keyword, imgvid
- 100% surety via single source of truth + 4-layer fg + blank fallback
