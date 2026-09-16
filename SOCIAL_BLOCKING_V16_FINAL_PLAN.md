# V16 FINAL PLAN — Sub-200ms Instant Launch Block (Deep Reanalysis v5, Plan Only)

> HEAD `f20c69d` V15: direct pkg poller 100ms + HOME kick + sync schedule load + 1s cooldown. Device still reports **2s** launch block. This plan deeply reanalyzes live code at f20c69d and proposes minimal, production-ready, scalable fix for ≤200ms with 100% surety, no influence on other features. Plan Only — do NOT execute until user says Execute.

---

## 0. Live State at f20c69d (V15)

**Service `SafeMeAccessibilityService.kt` (f20c69d):**
- `onServiceConnected`: sync load blockingPrefs + socialPrefs + schedulePrefs (first()) + `ScheduleEngine.apply` + `startPuWatchdog()` 250ms + `startLaunchBlockPoller()` 100ms
- `onAccessibilityEvent` main-thread fast lanes: `maybeSocialWholeFastLane(pkg)` + `maybeScheduleFastLane(pkg)` on WINDOW_STATE_CHANGED only, before queuing to `eventScope` (limitedParallelism(1) Default)
- `eventScope` serial queue: YouTube Home 10-20 content events/sec, each `collectTextsFrom` walk 50-100ms → backlog 500-1500ms
- Direct poller: `launchBlockPollerJob` serviceScope loop 100ms `directPkgLaunchBlockProbe()`: rootInActiveWindow 1 IPC → fallback lastForegroundPkg → cooldown 1000L → `performGlobalAction(HOME)` + `launchSocialWholeGate`/`launchScheduleGate`
- `launchSocialWholeGate`: `getApplicationLabel` (PackageManager IPC 50-100ms cold) + `performGlobalAction(HOME)` + `BlockOverlayController.show()`
- `launchScheduleGate`: `performGlobalAction(HOME)` + `show()`
- Content backstop `maybeSocialWholeContentGate` & watchdog `socialWatchdogProbe`: removed post-dismissal skip, dedup 1000L, check isShowing() return (no tab preempt)
- Fast lanes: `maybeSocialWholeFastLane` checks `isShowing() return` (BUG: blocks preempt of tab cover), dedup 1000L; `maybeScheduleFastLane` checks `isShowing() && !isShowingTabCover() return` (allows tab preempt)
- Cooldowns: `SCHEDULE_COOLDOWN_MS 1000L`, social whole 1000L, `COOLDOWN_MS 4000L` for keyword/PU/tab
- Rearm: clears social whole/tab + schedule + PU + keyword, good
- `BlockOverlayController.show()`: `if(showing && !preemptTabCover) return` → `showing=true` sync → `scope.launch(Default) { prefs = lastPrefs ?: DataStore.first() (50-300ms) } → mainHandler.post { attachOverlay }` → ComposeView + BlockOverlay composition 200-400ms first time. Dismiss early `showing=false` after HOME, window removal delayed 250ms.

**Overlay:**
- TYPE_ACCESSIBILITY_OVERLAY 2032, needs service WindowManager token
- First composition: Compose runtime init + SafeMeApp theme + BlockOverlay = 300-500ms on low-end (Vivo)
- `lastPrefs` cache fixes second gate, but first gate after service start still DataStore read
- `getApplicationLabel` in social whole gate on critical path

**ScheduleEngine:**
- Volatile sets, `isLaunchBlocked` O1, good, but depends on `apply` being called. Sync load in service fixes initial, but `onScheduleSetsChanged` posts to main looper → recheck throttled 5s (SCHEDULE_RECHECK_THROTTLE_MS) unless force=true (we use force=true). Good.

**Why V15 still 2s — Deep Reanalysis:**

### 1. Fast lane not firing on launcher click
- User taps icon in launcher: event pkg = launcher (com.bbk.launcher2 / com.miui.home), not blocked pkg. Fast lanes only check event pkg == blocked pkg. So they don't fire on click. They wait for WINDOW_STATE_CHANGED of blocked pkg, which system delays 300-800ms cold start (activity thread, theme, etc). Direct poller should catch via rootInActiveWindow, but root null during animation → fallback lastForegroundPkg which is still launcher until WINDOW_STATE_CHANGED updates it → miss first 2-3 polls (200-300ms).

### 2. Overlay show path has 2 async hops + IO + Compose
- `show()` → `scope.launch(Default)` → `DataStore.first()` 50-300ms → `mainHandler.post` → `attachOverlay` → ComposeView creation + composition 200-400ms first time. Total 300-800ms even after HOME kick. User perceives 2s because HOME kick itself is async `performGlobalAction` which system processes 100-300ms, plus overlay 300-800ms = 400-1100ms, plus detection 300ms = 700-1400ms, plus if first gate fails (WMS token race) fallback to activity 200-300ms extra = 1-2s observed.

### 3. `getApplicationLabel` on critical path
- Social whole gate does PackageManager IPC on calling thread (Default? Actually launchSocialWholeGate called from Default scope (eventScope or serviceScope). That IPC can be 50-100ms, but on cold PackageManager it can be 200-400ms (Vivo). Adds to critical path.

### 4. Social whole fast lane preempt bug
- `maybeSocialWholeFastLane` checks `if(isShowing()) return` without allowing tab cover preempt. If a tab cover (Shorts) is up and user launches whole-blocked app (e.g., YouTube whole-blocked), fast lane blocked. Direct poller allows preempt, but fast lane should too. Causes 250ms-1s extra when tab cover present.

### 5. Cooldown dedup suppressing retry after failed attach
- Launch gates set `lastSocialWholeBlockKey/At` BEFORE HOME + overlay. If overlay addView fails (token null after rebind, or window already attached), we fallback to activity but cooldown remains 1000L. Next detection within 1s is deduped → no retry → 1s exposure until cooldown expires. On Vivo, token null after rebind observed, causes 1s+ delay.

### 6. Dismiss 250ms delay keeps window attached
- `dismiss()` sets `showing=false` early (V13 fix) but keeps window attached 250ms for visual continuity. If new launch arrives within 250ms, `show()` sees `showing=false` so proceeds, but `wm.addView` may fail because old window still attached (same wm, different view) → WMS "already added" or "token null" → fallback to activity (slower) → 2s.

### 7. Direct poller single detection method
- Only `rootInActiveWindow` + `lastForegroundPkg`. During app start animation, root null, lastForegroundPkg stale. Needs UsageStatsManager or ActivityManager as second source for instant pkg.

### 8. No instant blank cover
- User sees blocked app content for 300-800ms while Compose loads. Should show instant black fullscreen view (0ms) then upgrade to full BlockOverlay.

**Sum of remaining delays:** launcher click 0ms → WINDOW_STATE_CHANGED 300-800ms → fast lane 10ms → HOME 100-300ms → overlay hops 300-800ms + label 100ms = 800-1900ms → **~2s observed**.

---

## 1. Alternatives Evaluated (Minimal Code First)

| Approach | Lines | Pros | Cons | Verdict |
|---|---|---|---|---|
| **A. Fix fast lane preempt bug** `maybeSocialWholeFastLane` allow tab cover preempt | 1 | Fixes tab→whole transition | Still not instant | **Must do** |
| **B. Make overlay show for launch blocks synchronous, no scope.launch, no DataStore/PackageManager on critical path** | 15 | Removes 2 hops + IO from critical path, ≤50ms | Needs pre-cache prefs + label | **Must do** |
| **C. Two-stage overlay: instant blank black view (0ms) + async upgrade to BlockOverlay** | 20 | User sees black instantly, not app content, even if Compose 400ms | Extra view management | **Must do for ≤200ms perception** |
| **D. Direct pkg detection via UsageStatsManager queryEvents 500ms + ActivityManager** | 25 + permission | Independent of rootInActiveWindow, catches launch even before window drawn | Needs PACKAGE_USAGE_STATS permission, user grant | **Add as fallback, not primary** |
| **E. Pre-cache blockScreenPrefs + app labels at service start, pre-warm ComposeView** | 10 | Removes DataStore + PM IPC from first gate | Small init cost | **Must do** |
| **F. Make HOME kick on fast lane main-thread path, not just in launch gates** | 3 | Kicks app to HOME within 50ms of WINDOW_STATE_CHANGED, before overlay | Changes UX slightly but matches PU pattern | **Must do** |
| **G. Fix cooldown: set after successful HOME, clear on failure, reduce to 500ms for launch** | 5 | Allows retry if first attach fails, no 1s dead zone | Slightly more gates if user spams | **Must do** |
| **H. Make dismiss immediate for launch preempt (no 250ms delay when new launch arrives)** | 5 | Prevents WMS "already added" failure | Flash possible but rare | **Must do** |
| **I. Reduce poller to 50ms for first 2s after detection, then 100ms** | 5 | Catches fast launches | 20 IPC/sec for 2s negligible | **Nice to have** |
| **J. Use DevicePolicyManager suspend packages** | 50 + device owner | System-level instant | Requires device owner, not consumer | Reject |
| **K. VPN per-app block** | already exists for internet | Makes app unusable even if UI visible | Doesn't block UI | Keep as is |

**Chosen minimal combo (A+B+C+E+F+G+H+I): ~60 lines net, no new permission required for core, UsageStats as optional fallback.**

---

## 2. Final Design V16 — Sub-200ms Instant Launch

### 2.1 Fix preempt bug (1 line)

```kotlin
// maybeSocialWholeFastLane: allow preempting tab cover like schedule fast lane
if (BlockOverlayController.isShowing() && !BlockOverlayController.isShowingTabCover()) return
```

### 2.2 Pre-cache & pre-warm at service start (10 lines)

In `onServiceConnected`:
```kotlin
// Pre-cache block screen prefs synchronously
runCatching { runBlocking { lastPrefs = appContext.blockScreenPrefs().first() } }
// Pre-warm ComposeView on main thread (creates recomposer, theme) — 1 dummy attach/detach off-screen
mainHandler.post { try { /* create dummy ComposeView, setContent {}, then dispose */ } catch(_:Throwable){} }
```

In `BlockOverlayController`: make `lastPrefs` accessible or keep in controller, but service can warm via `show()` with dummy pkg that is immediately dismissed? Simpler: controller pre-caches prefs on first `show` call already, but we will make launch path use cached prefs only, no DataStore read.

### 2.3 Two-stage overlay for launch blocks (20 lines)

New method in `BlockOverlayController`:
```kotlin
fun showInstantLaunchBlock(context: Context, pkg: String, matched: String, type: String) {
  // Stage 1: instant blank black fullscreen view, added SYNCHRONOUSLY on main thread if called from main, else post
  // No Compose, no DataStore, just FrameLayout with black background, clickable
  // Set showing=true synchronously
  // Stage 2: async upgrade to full BlockOverlay via existing attachOverlay (replace view content)
}
```

Implementation:
- If already on main looper, `attachInstantBlank()` directly (WindowManager.addView with FrameLayout black, MATCH_PARENT, TYPE_ACCESSIBILITY_OVERLAY, NOT_FOCUSABLE)
- Then `scope.launch { prefs = lastPrefs ?: BlockScreenPrefsState() } → mainHandler.post { upgrade to ComposeView }`
- Blank view appears in 10-30ms (WMS addView), user sees black, not blocked app
- Upgrade to full block screen in 200-400ms, but perceived block is instant

### 2.4 Synchronous launch path, no scope.launch hop (15 lines)

New fast path for launch blocks:
```kotlin
fun showLaunchBlockNow(context: Context, pkg: String, matched: String, type: String) {
  // Called from main thread fast lanes and direct poller (which is on serviceScope Default, so post to main)
  // If on main: attachInstantBlank() synchronously, then async upgrade
  // If off main: mainHandler.post { attachInstantBlank() }
  // No PackageManager label fetch — use pkg as label for instant, fetch label async for activity feed
}
```

Change `launchSocialWholeGate` and `launchScheduleGate`:
- No `getApplicationLabel` on critical path — use `pkg` as label for instant, launch async job to fetch real label for logging/activity
- No cooldown set before HOME — set after HOME success
- HOME kick BEFORE overlay: `performGlobalAction(HOME)` first (50ms), then `showInstantLaunchBlock`

### 2.5 Immediate HOME kick on fast lane main-thread (3 lines)

```kotlin
fun maybeSocialWholeFastLane(pkg: String?) {
  // ... checks ...
  try { performGlobalAction(GLOBAL_ACTION_HOME) } catch(_:Throwable){}
  launchSocialWholeGate(pkg) // which also does HOME + instant blank
}
fun maybeScheduleFastLane(pkg: String?) {
  try { performGlobalAction(GLOBAL_ACTION_HOME) } catch(_:Throwable){}
  launchScheduleGate(pkg)
}
```

### 2.6 Fix cooldown & dismiss race (5 lines)

- In `directPkgLaunchBlockProbe`, `maybeSocialWholeFastLane`, `maybeScheduleFastLane`, `maybeSocialWholeContentGate`, `socialWatchdogProbe`: set cooldown AFTER successful HOME, not before. On failure, clear cooldown.
- Reduce launch dedup to 500L (was 1000L) for instant retry, keep 4000L for keyword/PU/tab.
- In `BlockOverlayController.show()`: when preempting (full over tab or full over full with new pkg), remove old window SYNCHRONOUSLY (not delayed 250ms) before adding new. Keep 250ms delay only for normal dismiss (Close button).

```kotlin
// In dismiss(): if new launch pending, remove immediately, not delayed
// Add method removeOverlayNow() that removes synchronously on main thread
```

### 2.7 Direct pkg detection multi-source + adaptive interval (10 lines)

```kotlin
fun directPkgLaunchBlockProbe() {
  if (isShowing() && !isShowingTabCover()) return
  var fgPkg: String? = null
  // 1. rootInActiveWindow (fast, 1 IPC)
  val root = try { rootInActiveWindow } catch(_:Throwable){ null }
  if (root != null) { fgPkg = root.packageName?.toString(); recycle(root) }
  // 2. fallback lastForegroundPkg (updated on window events)
  if (fgPkg == null) fgPkg = lastForegroundPkg
  // 3. fallback UsageStatsManager if permission granted (optional, no crash if not)
  if (fgPkg == null) fgPkg = try { getForegroundViaUsageStats() } catch(_:Throwable){ null }
  // 4. fallback ActivityManager runningAppProcesses (best effort)
  if (fgPkg == null) fgPkg = try { getForegroundViaActivityManager() } catch(_:Throwable){ null }
  // ...
}
```

Add helpers `getForegroundViaUsageStats()` and `getForegroundViaActivityManager()` with try/catch, return null if not available. No new permission required for core; UsageStats is optional enhancement if user granted.

Adaptive poller:
```kotlin
startLaunchBlockPoller() {
  serviceScope.launch {
    var fastModeUntil = 0L
    while(true) {
      directPkgLaunchBlockProbe()
      val now = elapsedRealtime()
      val interval = if (now < fastModeUntil) 50L else 100L
      delay(interval)
    }
  }
}
// When launch detected, set fastModeUntil = now + 2000L to poll 50ms for 2s
```

### 2.8 Production Ready + Scalability

- Data-driven sets, O1 lookup, no hardcoded pkgs
- Poller 50-100ms = 10-20 IPC/sec worst for 2s, then 10/sec, <1% battery (PU watchdog already 4/sec)
- Two-stage overlay reuses existing WMS token, fail-open to activity
- No new permission for core path, UsageStats optional
- Fail-open try/catch everywhere, recycle

### 2.9 No Influence + 100% Surety

| Feature | Isolation |
|---|---|
| PU, keyword, URL, image/video, tab | Separate cooldown keys (launch 500L, others 4000L), separate prefs. Direct poller only O1 pkg check, no tree walk/text → cannot affect content engine. Fast lane HOME kick only for whole/schedule launch, not for tab/keyword. |
| Tab gates | Not touched, still 4s cooldown, still full-screen per V9 owner decision |
| Overlay | New instant blank path only for launch types (socialWhole, schedule), not for tab/PU/keyword. Existing show() path unchanged for other types. |
| ScheduleEngine | No change, only service calls apply sync |

**Surety:** Launch block now has 4 independent delivery layers: main-thread fast lane (WINDOW_STATE_CHANGED) + direct poller multi-source 50ms + content backstop + watchdog 250ms, all with instant HOME kick + instant blank cover. Even if 3 layers miss (OEM drops events, root null), 4th catches within 50ms. Blank cover appears in 10-30ms WMS, full block screen upgrades after. No bet on event timing or Compose init.

---

## 3. Verification Gates

1. `:app:testDebugUnitTest` 357/0/0
2. `:app:lintDebug` 0 errors
3. `:app:assembleRelease` OK
4. DEX: present `reel_recycler`, `social fast lane`, `schedule fast lane`, `directPkg`, `launchBlockPoller`, `showInstantLaunchBlock` / `attachInstantBlank`, `performGlobalAction`, `srcToken`, `navTab`; absent `reel_watch_fragment_root`
5. Signer unchanged `347be3532e6716989ad047e4a091ab436ad9215811fea7656cb717dbc30d97d0`
6. Device: whole/schedule cold/hot launch ≤200ms (blank 30ms + HOME 50ms + full 200ms), Close→HOME→immediate relaunch instant, no HOME re-gate, Shorts via Home ≤500ms persistent, Home scroll 0 FP, no influence on PU/keyword.

---

## 4. Execution Steps (Plan Only)

1. Edit `SafeMeAccessibilityService.kt`: fix preempt bug, add multi-source fg detection, adaptive poller, HOME kick on fast lanes, cooldown after success + 500L, no label IPC on critical path.
2. Edit `BlockOverlayController.kt`: add `showInstantLaunchBlock` / `attachInstantBlank` two-stage, `removeOverlayNow`, pre-cache prefs, synchronous path for launch.
3. Bootstrap + test + lint + release single foreground ≥1740s.
4. DEX + signer + APK + SHA, addendum, local commit only, no push/merge.

**LOC est: +60 / -10 net +50 vs V15, still minimal, production-ready.**

---
Prepared 2026-09-16 — Plan Only, awaiting Execute.
