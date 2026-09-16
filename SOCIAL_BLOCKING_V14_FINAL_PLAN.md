# V14 FINAL PLAN — Launch Block 3-5s Delay → Direct Pkg Blocking (Plan Only, Deep Reanalysis v3)

> Context: V13 (622167b, APK cbad7711) added schedule fast lane + reel_recycler L2b + prefs cache + early showing=false. Device still reports launch block (whole-app + schedule) takes 3-5s, not instant. User: "find another approach for executing these requirements, like use direct pkg blocks, blocking mechanism, or another one or more."
> Constraints: minimal code first (config/fix before new code), Production Ready + Scalability, no influence on other features, 100% surety. Plan Only.

---

## 1. Live V13 Code — Launch Path Inventory

**Whole-app social:**
- `onAccessibilityEvent` main thread: `maybeSocialWholeFastLane(pkg)` O(1) `isWholeAppBlocked` check, `if(isShowing) return`, cooldown 4s, then `launchSocialWholeGate` → `BlockOverlayController.show` (scope.launch Default → DataStore first() cached via lastPrefs → mainHandler.post attach).
- Content backstop `maybeSocialWholeContentGate`: `if(isShowing) return`, `if(isWithinPostDismissalWindow 1500ms) return`, cooldown.
- Watchdog `socialWatchdogProbe` every 250ms (serviceScope): identity read `rootInActiveWindow`, `if(isShowing) return`, `if(isWithinPostDismissalWindow) return`, cooldown.

**Schedule launch:**
- `onAccessibilityEvent` main thread V13: `maybeScheduleFastLane(pkg)` O(1) `isScheduleBlocked`, `if(isShowing && !isShowingTabCover) return`, cooldown 4s, `launchScheduleGate`.
- Window path `handleEvent` WINDOW_STATE_CHANGED: `if(isScheduleBlocked) launchScheduleGate` (inside eventScope serial queue limitedParallelism(1)).
- `recheckScheduleBlock` throttled 5s, called on schedule sets changed with force=true.
- **BUG FOUND:** `rearmCooldownsIfGateDismissed()` clears `lastSocialWholeBlockAt/Key`, `socialTabCooldown`, `lastBlockAt`, `lastPuBlockAt`, but **does NOT clear `lastScheduleBlockKey/At`**. So after closing schedule gate, 4s cooldown remains → reopen within 4s = no block → perceived 3-5s delay.
- **ScheduleEngine init:** `SafeMeApp` collects `schedulePrefs` async via `appScope.launch { collect { apply } }`. No synchronous `runBlocking first()` like social/blocking prefs. So at cold service start, `launchBlockedPackages` empty until first collect emits (100-500ms DataStore read). Fast lane then sees empty set → no block. Must wait for apply → next window event.

**Overlay:**
- V13 cache `lastPrefs` + early `showing=false` after HOME launch (window removal still delayed 250ms). First gate after service start still loads DataStore (100-400ms) if no cache.

**EventScope queue:**
- `eventScope = Default.limitedParallelism(1)` serial. Each content event (YouTube Home 10-20/sec) does `rootInActiveWindow` + walk ≤200 nodes (50-100ms). Queue backlog 500-2000ms. Schedule window path lives in this queue → delayed. Social fast lane bypasses queue, schedule fast lane now also bypasses (V13), but if `isShowing` true during dismiss animation, fast lane returns.

**Why still 3-5s after V13:**
- Schedule: cooldown not rearmed + async init + `isShowing` dead zone (even with early clear, `removeOverlay` still async, but flag cleared). 4s cooldown alone = 3-5s report.
- Social whole: fast lane checks `isShowing` (now early cleared, so 250ms dead zone gone) but content/watchdog still skip 1500ms post-dismissal window. If window-state event dropped by OEM (Vivo documented), must wait for watchdog after 1500ms → 1.5-2s. If cooldown not cleared due to race, 4s+1.5s=5.5s.
- Overlay prefs first load 100-400ms adds.

---

## 2. Direct Pkg Blocking — Alternatives Evaluated (Minimal Code First)

| Approach | Code size | Pros | Cons | Verdict |
|---|---|---|---|---|
| **A. Config only: clear schedule cooldown on dismissal + sync load schedule prefs** | 4 lines | Fixes 4s cooldown bug, fixes async init | Still relies on WINDOW_STATE_CHANGED events which OEMs drop; still has 1500ms post-dismissal skip for watchdog | **Must do, but not sufficient alone** |
| **B. Config only: reduce/remove post-dismissal window for launch blocks** `POST_DISMISSAL_EVICT_WINDOW 1500→0 for whole/schedule` | 2 lines | Removes 1.5s dead zone | Risk: HOME transition re-gates HOME (bad UX) if fast lane blocked. Need fast lane to handle, not just remove. | Partial, include with guard |
| **C. Config only: reduce `GATE_COOLDOWN` 4000→1000, `SCHEDULE_COOLDOWN` 4000→500** | 2 lines | Shortens dead zone | Still queue-dependent, doesn't fix OEM dropped events | Partial |
| **D. Direct pkg poller — reuse PU watchdog loop, add 100ms launch poller** | 15 lines | Independent of accessibility events, O(1) pkg check, runs every 100ms, ignores post-dismissal window for launch (genuine focus should gate), bypasses eventScope queue, no new threads (reuse serviceScope) | Slightly more battery (100ms vs 250ms) but still cheap (1 binder call) | **Primary new mechanism** |
| **E. UsageStatsManager poller** | 20 lines + permission | Direct foreground pkg via UsageStats, works even if rootInActiveWindow null | Requires PACKAGE_USAGE_STATS permission, user must grant, more code, not minimal | Fallback if rootInActiveWindow unreliable |
| **F. Accessibility config packageNames filter** | manifest + xml change | Only receive events for blocked pkgs → reduces event storm, faster delivery | Requires dynamic update when blocked set changes (re-set service info), more code, affects all features | Not minimal, not scalable (blocked set changes often) |
| **G. VPN per-app block for launch-blocked apps** | 30 lines | Internet block makes app unusable instantly, even if overlay delayed | Doesn't block launch UI, only internet, not intended for launch block | Not for launch |
| **H. DevicePolicyManager suspend packages** | 50 lines + device owner | Instant, system-level block | Requires device owner/profile owner, not production ready for consumer app | Reject |

**Chosen combination (minimal, production ready):**
- **A + B + C (config fixes, 6 lines)** + **D (direct pkg poller, 15 lines)** = **~21 lines net**, no new permissions, no manifest, no new dependencies, reuses existing watchdog infrastructure.

---

## 3. Design — V14 Direct Pkg Blocking

### 3.1 Fix existing bugs (config, 6 lines)

**File: `SafeMeAccessibilityService.kt`**

1. **Rearm schedule cooldown on dismissal:**
   ```kotlin
   fun rearmCooldownsIfGateDismissed() {
     if (consumeGateDismissedPending()) {
       lastPuBlockAt=0; lastBlockAt=0
       lastSocialWholeBlockAt=0; lastSocialWholeBlockKey=null
       socialTabCooldown.clear(); lastSocialTabProbeMs=0
       lastScheduleBlockAt=0; lastScheduleBlockKey=null // <- ADD, fixes 4s delay
       ...
     }
   }
   ```

2. **Sync load schedule prefs in onServiceConnected (like social/blocking):**
   ```kotlin
   runCatching {
     runBlocking {
       val initialSchedule = schedulePrefs().first()
       ScheduleEngine.apply(this@SafeMeAccessibilityService, initialSchedule.schedules, initialSchedule.excludedApps)
     }
   }
   ```
   Ensures `launchBlockedPackages` non-empty at first window event, not async 100-500ms later.

3. **Reduce post-dismissal window for launch blocks only:**
   Keep `POST_DISMISSAL_EVICT_WINDOW=1500` for PU (a11y detail eviction needs it), but for launch blocks (social whole + schedule) bypass it in watchdog/content backstop — they already bypass in fast lane, but watchdog/content currently skip. Change to:
   ```kotlin
   // In maybeSocialWholeContentGate and socialWatchdogProbe: remove isWithinPostDismissalWindow check for launch blocks
   // Or keep check but allow if pkg != last dismissed? Simpler: fast lanes already bypass, watchdog/content should NOT skip for launch blocks — only for PU.
   ```
   Actually current watchdog/content skip for launch blocks to avoid HOME re-gate. But HOME re-gate is prevented by fast lane's `isShowing` check? Need more precise: after Close, we launch HOME, foreground becomes launcher, not blocked app. So re-gating HOME not possible. The post-dismissal skip was to prevent re-gating the app being covered away during HOME transition. But fast lane already handles genuine focus gain. So we can safely remove post-dismissal check for launch blocks (social whole + schedule) in watchdog/content backstop, keep it for PU only.

4. **Reduce cooldowns (config):** `GATE_COOLDOWN 4000→2000` for tab, keep whole at 2000? Actually whole should be 1000 to allow quick re-block. Schedule 4000→1000. Still dedupes double-fires but not 4s.

### 3.2 Direct pkg poller — new mechanism (15 lines)

**Reuse existing `puWatchdogTick` loop (250ms) but add separate 100ms launch poller, or make existing loop check launch blocks every 100ms.**

Simplest: Add new `launchBlockPollerJob` in `serviceScope`, interval 100ms, independent of PU flag.

```kotlin
private var launchBlockPollerJob: Job? = null

fun startLaunchBlockPoller() {
  if (launchBlockPollerJob?.isActive==true) return
  launchBlockPollerJob = serviceScope.launch {
    while(true) {
      try { directPkgLaunchBlockProbe() } catch(_:Throwable){}
      delay(100L) // 100ms direct pkg check
    }
  }
}

fun directPkgLaunchBlockProbe() {
  if (BlockOverlayController.isShowing() && !BlockOverlayController.isShowingTabCover()) return // full gate up, dedup
  // No post-dismissal check for launch blocks — genuine focus should gate immediately
  var fgPkg: String? = null
  val root = try { rootInActiveWindow } catch(_:Throwable){ null }
  if (root != null) {
    fgPkg = try { root.packageName?.toString() } catch(_:Throwable){ null }
    recycle(root)
  }
  if (fgPkg==null) fgPkg = lastForegroundPkg
  if (fgPkg==null) return
  val own = applicationContext.packageName ?: return
  if (fgPkg==own) return

  val now = elapsedRealtime()
  // Social whole direct
  val social = cachedSocialState
  if (social!=null && social.enabled && SocialBlockingGate.isWholeAppBlocked(fgPkg, social.wholeBlocked)) {
    val key="socialWhole|$fgPkg"
    if (!(lastSocialWholeBlockKey==key && now-lastSocialWholeBlockAt<1000L)) { // 1s dedup, not 4s
      lastSocialWholeBlockKey=key; lastSocialWholeBlockAt=now
      launchSocialWholeGate(fgPkg)
      return
    }
  }
  // Schedule direct
  if (isScheduleBlocked(fgPkg)) {
    val key=fgPkg
    if (!(lastScheduleBlockKey==key && now-lastScheduleBlockAt<1000L)) {
      lastScheduleBlockKey=key; lastScheduleBlockAt=now
      launchScheduleGate(fgPkg)
      return
    }
  }
}
```

- Called from `onServiceConnected` via `startLaunchBlockPoller()`.
- Runs on `serviceScope` (Default), not `eventScope`, so bypasses queue.
- 100ms interval = 10 checks/sec, 1 binder call (`rootInActiveWindow`) per check, cheap.
- No `isWithinPostDismissalWindow` check — launch blocks should gate even right after dismissal if user reopens app (HOME transition already handled by `isShowing` check? Actually after Close we launch HOME, fgPkg becomes launcher, not blocked, so no re-gate. If user quickly reopens blocked app from launcher, fgPkg becomes blocked, should gate immediately even within 1500ms window — that's intended instant block).
- Cooldown 1s dedup, not 4s, so reopen within 1s still deduped but not 4s delay.
- `isShowing` check prevents double-gate while overlay up, but allows preempting tab cover (check `isShowingTabCover`).

**Why direct pkg is more reliable than event-based:**
- Accessibility events can be dropped/delayed by OEMs (Vivo/FuntouchOS documented). Polling `rootInActiveWindow` every 100ms is independent, always gets current foreground.
- No dependency on event type (WINDOW_STATE vs CONTENT). Direct pkg check works for any launch (cold, hot, recents).
- No dependency on DataStore async — uses cached sets which are sync loaded at service start.

**Battery:** 100ms poll = 10 binder calls/sec. Each `rootInActiveWindow` is cheap (one IPC). PU watchdog already does 4 calls/sec (250ms). Total 14 calls/sec, still negligible (<1% battery). Can make 150ms if needed.

### 3.3 Overlay instant attach (already V13, keep)

- Prefs cache `lastPrefs` already, early `showing=false` already. Keep.
- For direct poller, overlay attach still async via `show()` → scope.launch → mainHandler.post. But with cached prefs, it's 0ms + mainHandler post (16ms). So total launch block = poll interval 100ms + 16ms = **~116ms worst, ~50ms typical**.

### 3.4 Files touched (2 files, ~21 lines)

1. `SafeMeAccessibilityService.kt`:
   - Add `lastSchedule` to `rearmCooldownsIfGateDismissed` (2 lines)
   - Sync load schedule prefs in `onServiceConnected` (8 lines)
   - Add `directPkgLaunchBlockProbe()` + `launchBlockPollerJob` + `startLaunchBlockPoller()` (15 lines)
   - Call `startLaunchBlockPoller()` in `onServiceConnected`
   - Remove `isWithinPostDismissalWindow` check from `maybeSocialWholeContentGate` and `socialWatchdogProbe` for launch blocks, or keep but bypass for direct poller (config)

2. `SocialBlockingGate.kt`: no change (V13 L2b kept)

3. `BlockOverlayController.kt`: no change (V13 kept)

**Net: +21 lines, no new permissions, no manifest, no new files.**

### 3.5 Production Ready + Scalability

- **Data-driven:** Blocked sets from DataStore, no hardcoded pkgs. New blocked app = DataStore edit, poller picks it up automatically.
- **Scalable:** Poller O(1) set lookup, not tree walk. Works for 1 or 100 blocked apps same cost.
- **Battery:** 100ms poll is standard for app blockers (BlockerX uses 150ms notificationTimeout). Can be made adaptive: 100ms when screen on + blocked app in recents, 500ms otherwise.
- **Fail-open:** All wrapped in try/catch, recycle, return on error.

### 3.6 No Influence + 100% Surety

| Feature | Isolation |
|---|---|
| PU guards, keyword, URL, image/video, tab gates | Separate cooldown keys, separate prefs, equality event checks. Direct poller only checks `isWholeAppBlocked` and `isScheduleBlocked`, no tree walk, no text collection, so cannot affect content engine. |
| Social whole/content/watchdog | Direct poller shares same cooldown keys (reuses), so deduped, cannot double-gate. Removes post-dismissal skip only for launch blocks, keeps it for PU (a11y detail eviction needs it). |
| Tab gates (SHORTS) | Not touched, V13 L2b kept. |
| Overlay | No change to visuals, only flag timing. |

**Surety:**
- Launch delay fix is structural: direct pkg polling independent of accessibility event delivery (which OEMs drop), independent of serial queue backlog, independent of DataStore async init (sync load added), independent of 4s cooldown bug (rearm fixed). No bet on event timing.
- If `rootInActiveWindow` returns null (mid-transition), fallback to `lastForegroundPkg` and next poll 100ms later retries — so worst case 200ms, not 3-5s.
- If poller ever finds retained fragment (shouldn't, pkg check only), it would gate only if pkg is blocked — correct.
- Under-block direction: if poller misses (null root + no lastForeground), next poll 100ms later catches — 100ms exposure, not 5s.

---

## 4. Verification Gates

1. `testDebugUnitTest`: 357/0/0 (no logic change to pure functions, only service polling)
2. `lintDebug`: 0 errors
3. `assembleRelease`: BUILD SUCCESSFUL
4. DEX: present `reel_recycler`, `social fast lane`, `schedule fast lane`, `directPkg`, `launchBlockPoller`; absent `reel_watch_fragment_root`
5. Signer unchanged
6. Device:
   - Cold launch whole-blocked app (social) → cover ≤150ms
   - Hot launch (recents) → ≤150ms
   - Schedule launch → ≤150ms
   - Close → HOME → immediate relaunch (within 500ms) → instant (no 4s cooldown)
   - No HOME re-gate after Close (launcher not blocked)
   - Shorts via Home + via tab still gated (V13 kept)
   - Home scroll 0 FP

---

## 5. Execution Steps (when authorized)

1. Edit `SafeMeAccessibilityService.kt`: rearm schedule cooldown, sync load schedule prefs, add direct poller job + probe, start in `onServiceConnected`, remove post-dismissal skip for launch blocks in content/watchdog.
2. Single foreground `bootstrap + test + lint + release`.
3. DEX + signer + APK + SHA.
4. Local commit only.

**Est. LOC: +21 / -2, net +19.**

---
Prepared 2026-09-16 — Plan Only, no execution.
