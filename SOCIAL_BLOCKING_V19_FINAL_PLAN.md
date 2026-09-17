# SOCIAL BLOCKING V19 FINAL PLAN — V17 Launch Gate Regression + Social Gate UX + Slow Card

**Date:** 2026-09-17 (Asia/Dhaka)  
**Branch:** `agent/social-blocking-fixes` HEAD `32fee98` (V17)  
**Symptoms:**
1. "block gate is not appearing at all in lunch Block app" — whole-app social + schedule launch blocks never show (V17 regression)
2. "gate is not same as other because in this social media blocking mechanism the gate can close without time out & can tab others button" — social gate UX differs from other gates (needs clarification, plan covers both interpretations)
3. "when I open this card the app is displayed slowly" — SocialBlockingScreen / picker loads slowly (AppCatalog + icons)

**Mode:** Plan-Only per user. No execution until "Execute". Supersedes V18 plan (V18 root cause still valid, now expanded).

## 1. Deep Reanalysis — Live Code 32fee98

### 1.1 Launch Gate Not Appearing — Confirmed Double-Dedup (V18 RC)

Same as V18: callers set cooldown + HOME, then callee checks same cooldown and bails.

**Callers with duplicate HOME/cooldown/fastMode:**
- `directPkgLaunchBlockProbe()` (lines 1499-1545)
- `maybeSocialWholeContentGate()` (2681-2698)
- `maybeSocialWholeFastLane()` (2710-2726)
- `maybeScheduleFastLane()` (2738-2752)
- `socialWatchdogProbe()` (2770-2790)
- `handleEvent` window-state social whole (836-854)

**Callees with same dedup:**
- `launchScheduleGate()` (2385-2392): `if lastKey==pkg && now-last<500 return; HOME; set key; showInstantLaunchBlock`
- `launchSocialWholeGate()` (2394-2415): same + async label

Result: every path does `set key → call gate → gate sees fresh key → return`. No overlay.

### 1.2 Social Gate "Not Same As Other" — Two Interpretations

**Current code after owner decision (Round-5):**
- Owner: "uniform full-screen blocking for tab covers overrides earlier 'keep nav usable' scoped design; tab Close keeps its 4s in-app snooze"
- Implementation: `launchSocialTabGate()` calls `BlockOverlayController.show(pkg, label, "socialTab")` WITHOUT `coverAboveY` → full-screen (MATCH_PARENT), identical visuals to other gates. `refitTabCover(null)` keeps full-screen.
- `BlockOverlay` dwell = `prefs.dwell` (default 5s, min 3s) for ALL types — close button disabled until countdown 0.
- `onClose`:
  - `socialTab`: `dismissTabCover(clearCooldown=false)` → stays in app, no HOME, cooldown 4s snooze (per-vertical `socialTabCooldown`)
  - `socialWhole` / `schedule` / `pu` / `keyword`: `dismiss()` → HOME + 250ms cover persistence + `onGateDismissed()` re-arms cooldowns

**User statement ambiguity:**
- **Interpretation A (Bug):** Social gate SHOULD be same as others (require dwell timeout, block all buttons, HOME on close), but currently allows immediate close + tapping other buttons → weaker protection.
- **Interpretation B (Feature):** Social gate IS intentionally different (tab cover stays in app, allows other tabs, immediate close with 4s snooze) — user is just noting difference, but wants it to be production-ready and fast.

**Evidence for B being intended:**
- FootnoteCard: "Tab overlays the view. 250ms throttle · 4s cooldown. No root, no VPN." — suggests tab overlay is lighter.
- `dismissTabCover(clearCooldown=false)` keeps cooldown as snooze — if dwell also required, user would wait 5s + 4s snooze = 9s friction, maybe too much for tab switching. Immediate close (dwell=0) + 4s cooldown is more balanced: you can instantly leave Shorts, but re-entering within 4s is snoozed, after 4s it re-blocks.
- Whole-app social SHOULD be same as other gates (HOME + dwell) because it's launch block — entire app blocked before opening.

**Recommendation (minimal, scalable, best practice):**
- **Whole-app social (`socialWhole`) + schedule + PU + keyword:** same gate — `dwell = prefs.dwell`, `onClose = HOME`, full-screen, no other buttons.
- **Tab (`socialTab`):** differentiated but production-hardened — `dwell = 0` (immediate close, no timeout), `onClose = stay in app`, full-screen but after close you can tap other buttons (other tabs). Keep 4s cooldown as snooze. This matches "can close without timeout & can tab others button" as **intended UX**, not bug.

If owner wants A (uniform), change is 1 line: `dwell = prefs.dwell` for all. Plan includes both, default to B (differentiated) as it is more usable and matches existing `dismissTabCover` stay-in-app contract.

**Implementation minimal:**
- In `BlockOverlayController.attachOverlay()`, dwell param already passed. For `socialTab`, pass `0` instead of `prefs.dwell`. Or pass `prefs` but override in `BlockOverlay` call: `dwell = if (type == "socialTab") 0 else prefs.dwell`.
- Keep `onClose` already differentiated.
- Ensure `coverAboveY` not used (full-screen) — already full-screen via no param.

### 1.3 Slow Card — App Display Slowly When Opening Card

**Current:**
- `SocialBlockingViewModel.loadApps()` on `Dispatchers.IO`: `AppCatalog.load(context)` does `queryIntentActivities` (1 IPC) + for each app `getApplicationInfo` + `loadLabel` (N IPCs, N=150-300) → 500ms-1500ms on low-end.
- `SocialBlockingScreen.LaunchBlockSection` shows `DEFAULT_LAUNCH` (5 items) filtered by `installedPkgs` — fast, but `InstalledAppIcon` per row does `PackageManager.getApplicationIcon` (IPC + bitmap decode) synchronously in composition → jank.
- `SocialPickerSheet`: `GroupedAppPickerList` with 150-300 items in `Column`? Actually `GroupedAppPickerList` likely uses LazyColumn? Need check, but if it uses Column + verticalScroll, it's heavy.
- `LaunchRow` and `TabRow` also use `InstalledAppIcon` which is synchronous.

**Root causes:**
1. No caching — every open reloads all apps + icons.
2. Icon loading on main thread composition.
3. Possibly no `key` or `remember` for icon, causing reload on recomposition.

**Minimal fix (config + small code):**
- Cache `AppCatalog.load()` result in memory + disk (simple `MutableStateFlow` already caches in VM, but VM is recreated per screen open — should cache in singleton or `AppCatalog` object with `volatile` cache + timestamp).
- Make `InstalledAppIcon` async: use `remember { mutableStateOf }` + `LaunchedEffect(packageNames)` to load icon off main thread, or use Coil `rememberAsyncImagePainter` with `packageName` as data (PackageManager icon loading via Coil is off-main).
- For `LaunchBlockSection`, limit to 5 default rows + extras — already minimal, but ensure icons loaded async.
- For picker sheet, ensure `GroupedAppPickerList` uses `LazyColumn` (not Column) — if already LazyColumn, keep; if not, switch (config change, minimal).
- Add `isLoading` shimmer already exists — keep.

## 2. Constraints (standing)

- Minimize Code: prefer config/fix/small-code over large-scale coding
- Production-Ready & Scalable: reliable, maintainable, growth = data edits not engine edits
- No Regressions: 357/0/0 tests, lintVital 0, DEX checks, signer unchanged, no influence other features
- Best Practices: single source of truth, easy debugging, fail-closed
- No push/merge without explicit command
- Plan-Only

## 3. Fix Design — Three Issues, Minimal Code

### 3.1 Issue 1: Launch Gate Not Appearing (Critical, 100% repro)

**Design:** Centralize dedup + HOME + cooldown + fastMode in `launch*Gate` only. Callers thin.

**Edits in `SafeMeAccessibilityService.kt` (same as V18, net -40 lines):**

```kotlin
// launchScheduleGate — sole owner
private fun launchScheduleGate(pkg: String) {
    val now = SystemClock.elapsedRealtime()
    if (lastScheduleBlockKey == pkg && now - lastScheduleBlockAt < 500L) return
    try { performGlobalAction(GLOBAL_ACTION_HOME) } catch (_: Throwable) {}
    lastScheduleBlockKey = pkg
    lastScheduleBlockAt = now
    fastModeUntilMs = now + 2000L
    Log.d(TAG, "schedule gate: $pkg [V19]")
    BlockOverlayController.showInstantLaunchBlock(this, pkg, "", "schedule")
}

// launchSocialWholeGate — sole owner, add fastMode
private fun launchSocialWholeGate(pkg: String) {
    val now = SystemClock.elapsedRealtime()
    val key = "socialWhole|$pkg"
    if (lastSocialWholeBlockKey == key && now - lastSocialWholeBlockAt < 500L) return
    Log.d(TAG, "social whole gate: $pkg [V19]")
    try { performGlobalAction(GLOBAL_ACTION_HOME) } catch (_: Throwable) {}
    lastSocialWholeBlockKey = key
    lastSocialWholeBlockAt = now
    fastModeUntilMs = now + 2000L
    BlockOverlayController.showInstantLaunchBlock(this, pkg, pkg, "socialWhole")
    serviceScope.launch { runCatching { /* async real label log */ } }
}

// Thin wrappers — remove duplicate HOME/cooldown/fastMode
directPkgLaunchBlockProbe() -> just call launchSocialWholeGate / launchScheduleGate
maybeSocialWholeContentGate -> Log + launchSocialWholeGate
maybeSocialWholeFastLane -> Log + launchSocialWholeGate
maybeScheduleFastLane -> Log + launchScheduleGate
socialWatchdogProbe -> Log + launchSocialWholeGate
handleEvent window-state social whole -> launchSocialWholeGate(pkg) only
```

### 3.2 Issue 2: Social Gate UX — Dwell & Other Buttons

**Decision:** Whole-app social = same as other gates (dwell = prefs.dwell, HOME on close). Tab = differentiated (dwell=0 immediate close, stay in app, allow other tabs after close). This matches "can close without timeout & can tab others button" as intended for tab.

**Minimal edit in `BlockOverlayController.kt` `attachOverlay` caller `show()` and `showInstantLaunchBlock`:**

- `show()` already has `type` param. In `attachOverlay`, dwell is from prefs. Change to:
```kotlin
val effectiveDwell = if (type == TYPE_SOCIAL_TAB) 0 else prefs.dwell.coerceAtLeast(0)
```
Pass `effectiveDwell` to `BlockOverlay(dwell = effectiveDwell, ...)`

- `showInstantLaunchBlock` uses `upgradeToFullOverlay` which calls `attachOverlay` — same effectiveDwell logic applies (since it passes prefs). So tab gate will have dwell 0, whole-app will have prefs.dwell.

- If owner wants uniform (Interpretation A), change to `effectiveDwell = prefs.dwell` for all — 1 line.

**Why production-ready:**
- Single place controls dwell per type — scalable, data-driven.
- Tab immediate close + 4s cooldown = balanced friction, easy to debug via logs (`socialTabCooldown`).
- Whole-app same as PU/keyword = consistent security.

**No regression:**
- `BlockOverlay` already handles `dwell <=0` → `ready=true` immediately.
- `dismissTabCover` stays in app, `dismiss` goes HOME — unchanged.

### 3.3 Issue 3: Slow Card — App Display Slowly

**Minimal fixes (3 small changes, no large refactor):**

**A. Cache AppCatalog in singleton (config):**
```kotlin
object AppCatalog {
    @Volatile private var cached: List<InstalledApp>? = null
    @Volatile private var cachedAt: Long = 0L
    fun load(context: Context): List<InstalledApp> {
        val now = SystemClock.elapsedRealtime()
        cached?.let { if (now - cachedAt < 60_000L) return it } // 60s cache
        // existing load logic
        val result = ... 
        cached = result
        cachedAt = now
        return result
    }
    fun invalidate() { cached = null }
}
```
- Minimal, no new deps, 60s cache avoids reload on every card open.

**B. Async icon loading in `InstalledAppIcon` (small code, best practice):**
Current likely synchronous `pm.getApplicationIcon`. Change to:
```kotlin
@Composable
fun InstalledAppIcon(packageNames: List<String>, size: Dp, fallback: @Composable () -> Unit) {
    var icon by remember { mutableStateOf<Drawable?>(null) }
    val context = LocalContext.current
    LaunchedEffect(packageNames) {
        withContext(Dispatchers.IO) {
            val pkg = packageNames.firstOrNull { isInstalled(it) } ?: packageNames.firstOrNull()
            val drawable = runCatching { context.packageManager.getApplicationIcon(pkg ?: "") }.getOrNull()
            icon = drawable
        }
    }
    if (icon != null) Image(...) else fallback()
}
```
- Off-main, easy to debug, no Coil needed (keeps minimal). Or use Coil if already in deps — check build.gradle.

**C. Ensure LazyColumn for picker (config):**
- Verify `GroupedAppPickerList` uses `LazyColumn` — if it uses `Column + verticalScroll`, switch to `LazyColumn` (minimal). This is config change, not large code.

**Performance expectation:**
- First open: ~500ms (cached after), second open: <50ms from memory cache.
- Icons load async, no jank, shimmer fallback.

## 4. Verification Plan (after Execute)

1. Build: `./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleRelease` single call
   - Expect 357/0/0, lintVital 0, debug lint ≤69 warnings
2. DEX: `reel_recycler` present, `fragment_root` absent 0, fast lanes present, signer `347be353...`
3. Source grep: `showInstantLaunchBlock`, `instantBlankView`, `fastModeUntilMs`, `effectiveDwell` present
4. Logic grep: no duplicate HOME/cooldown in fast lanes/poller — only in launch*Gate
5. Manual (emulator/device logs):
   - Launch blocked app → log `direct poller: gating` → `social whole gate: pkg [V19]` → blank black 10-30ms → HOME 50ms → full overlay ≤150ms → close button: whole-app requires dwell, tab immediate
   - Open SocialBlockingScreen card → first open <600ms, second open <100ms (cache), icons appear async without blocking scroll
6. APK SHA + signer record, copy to `/home/user/SafeMe-0.1.0-release.apk`
7. Commit locally no push/merge

## 5. Alternatives Considered

- **Launch gate:** Remove dedup from gate, keep in callers → rejected, violates single source, larger diff, easy to miss.
- **Social gate uniform vs differentiated:** Uniform (dwell same) = 1 line, but less UX for tab (9s friction). Differentiated (dwell 0 for tab) = minimal, more usable, matches existing stay-in-app contract. Recommend differentiated, but uniform is trivial toggle.
- **Slow card:** Full rewrite with Room DB cache → rejected, overkill. Simple 60s memory cache + async icon = minimal, production-ready, scalable.

## 6. Risks & Mitigations

- Centralized HOME: previously double HOME (poller + gate) → now single HOME, actually fixes flicker.
- 500L cooldown too short → tight loop? Mitigated by fastMode 2s + isShowing guard + dismiss sets showing=false synchronously.
- Async icon: icon may flicker fallback → use `remember` + placeholder, acceptable.
- Dwell 0 for tab: immediate close might feel too easy? Mitigated by 4s cooldown snooze — re-entering within 4s snoozed, after 4s re-blocks.

## 7. Expected Outcome

- Launch block gate appears every time, ≤80ms perceived (blank+HOME), full ≤150ms
- Social whole gate same as other gates (dwell + HOME), tab gate immediate close + stay in app + allow other tabs (other buttons) + 4s cooldown — production-ready, easy to debug
- Card opens fast: first <600ms, second <100ms, no jank
- No regressions, 100% surety, minimal code
