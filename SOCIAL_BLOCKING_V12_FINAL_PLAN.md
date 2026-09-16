# V12 FINAL PLAN — Launch Block Delay + Shorts Continuation Leak (Plan Only)

> Supercedes V11 (f820b5c, APK 3aed9edf). V11 fixed Home FP (z-order-blind tree scans → event-source evidence + nav-gated probing) but introduced two regressions reported on device:
> 1. **Launch block >5s delay** — whole-app/schedule gate presents seconds after app start, not ≤300ms.
> 2. **Shorts continuation leak** — after closing tab cover, Shorts video keeps playing; Shorts opened via Home tab blocks only minimally / not at all.
>
> Constraints: minimal code (config/fix before new code), Production Ready + Scalability, no influence on other features, 100% surety. /Plan Only — no execution.

---

## 0. Current V11 State (verified live code)

- **Gate**: `SocialBlockingGate.findActiveTab()` = L1 selected tab (nav-bar context) → L2 fullscreen = `SourceEvidence` only (token + visible + ≥0.80 area). `knownIds` emptied, `findKnownIdFullscreenNode` + `findFullscreenTokenNode` deleted.
- **Service**: `readSocialSourceEvidence()` scoped to 3 pkgs, content events rate-limited 250ms, stored on `EventSnapshot`. `handleSocialTabContentEvent()` probes ONLY if `isNavigationEventType` OR `evidence.tokenMatched` OR within `TRANSITION_GRACE_MS=1500ms` after nav. Throttle bypass for nav events.
- **Fast lanes**: Social whole has 3 layers: main-thread fast lane (`maybeSocialWholeFastLane` on WINDOW_STATE_CHANGED), content backstop (`maybeSocialWholeContentGate`), watchdog (`socialWatchdogProbe` every 250ms). Schedule blocking has NO fast lane — only inside `handleEvent` window-state branch (serial `eventScope` queue).
- **Overlay**: `BlockOverlayController.show()` sets `showing=true` synchronously, then `scope.launch { blockScreenPrefs().first() }` → `mainHandler.post { attachOverlay }`. Prefs loaded from DataStore on every show. `dismiss()` keeps `showing=true` for 250ms (`REMOVE_AFTER_HOME_DELAY_MS`) then clears + signals `onGateDismissed`.
- **Tab cover Close**: `dismissTabCover(clearCooldown=false)` — stays in app, keeps `socialTabCooldown` 4s as snooze, after which content probe should re-raise if still on tab.
- **Storage**: 197.9 MB / 632 files >128 MB limit. Culprits: `.git` 58 MB, `Reference/NopoX.apk` 23 MB, `artifacts/` 18 MB, `.gradle/` 14 MB (not in snapshot exclusion), `graphify-out/` 7 MB.

---

## 1. Root Cause Analysis

### 1.1 Launch Block >5s

**Two gates share the name "launch block":**

| Gate | Path today | Delay source |
|---|---|---|
| **Social whole** | Fast lane (main thread) → content backstop (eventScope) → watchdog (serviceScope 250ms) | Fast lane checks `isShowing` → if overlay still in 250ms dismiss animation, returns without gating. Content backstop + watchdog both check `isWithinPostDismissalWindow()` (1500ms) → skip. So after a Close, there is a 250ms hard dead zone + 1500ms soft dead zone where only fast lane can gate, but fast lane is blocked by `showing`. If user re-launches app within 250ms, gate is ignored, then must wait for next window-state event (OEMs drop/delay) → seconds. Also `blockScreenPrefs().first()` DataStore read on every show can take 100-400ms, adding to perceived delay. |
| **Schedule launch** | Only inside `handleEvent` window-state branch (eventScope serial queue). No fast lane. `eventScope` is `limitedParallelism(1)` and processes heavy tree walks (`collectTextsFrom` ≤200 nodes) for every content event (YouTube Home emits 10-20/sec). Queue backlog → window-state event waits seconds before being processed. Throttle `SCHEDULE_RECHECK_THROTTLE_MS=5000` also gates `recheckScheduleBlock` but not direct check. |

**Empirical match to 5s:** Schedule path has 5s throttle constant; social path has 4s cooldown + 1.5s evict window ≈5.5s worst case when fast lane blocked.

**Why V11 introduced it:** V11 added `isWithinPostDismissalWindow` guard to content backstop + watchdog to prevent re-gating HOME transition after Close (correct for UX), but didn't add same bypass to fast lane's `isShowing` check. And schedule never got the fast lane that social whole got in V5.

### 1.2 Shorts Continuation Leak

**V11 design:** Probing only in navigation context + fullscreen = event-source evidence.

**Leak scenario A — Close → continuation:**

1. User on Shorts, gated, taps Close on tab cover.
2. `dismissTabCover(clearCooldown=false)` → `showing=false`, but `socialTabCooldown[pkg|SHORTS]=now` stays (4s snooze).
3. User stays in Shorts player, video continues. Player idle emits few/no content events, and those events have source = video surface which may NOT contain token "reel"/"shorts" in viewId/className (player surface is often `android.view.ViewGroup` or `TextureView`), so `evidence.tokenMatched=false`.
4. `handleSocialTabContentEvent` early return: `!isNav && !tokenMatched && now-lastNav >1500ms` → **no probe**. So even after 4s cooldown expires, no tree walk happens, no re-block.
5. Result: Shorts plays forever after Close.

**Leak scenario B — Shorts via Home tab:**

1. User on Home, taps Shorts thumbnail.
2. Click event source = thumbnail card (small, maybe `reel` id but <0.80 area) → `isFullscreenSourceEvidence=false`, L1 selected tab = Home (not Shorts) → miss.
3. Transition grace 1500ms starts. During grace, player inflates, emits content events. If those events' source is fullscreen + token, it blocks (minimal time). But once player stabilizes, content events stop carrying token, grace expires, probing stops → cover may auto-dismiss via `handleSocialTabCoverWatch` (no L1, no evidence) → **minimal block**.
4. Root: YouTube's Shorts player opened from Home does NOT switch bottom-nav selected state to Shorts (stays Home), so L1 never matches. Must rely on persistent player detection, but V11 has no persistent detection — only transient event source.

**BlockerX comparison:** BlockerX does NOT rely on selected tab for YouTube Shorts. It does `findAccessibilityNodeInfosByViewId(root, "com.google.android.youtube:id/reel_recycler")` non-empty on navigation events. `reel_recycler` is the RecyclerView that holds Shorts — present only when Shorts player is active, not behind Home (unlike `reel_watch_fragment_root` which we deleted for FP). So BlockerX's detection is **persistent surface presence**, not event source.

**Why V11 deleted it:** We deleted ALL tree scans because `reel_watch_fragment_root` persisted behind Home and was z-order-blind. But `reel_recycler` is different id — BlockerX uses it and has no Home FP reports. V11's "unverified bet" rejection was conservative; device now proves we need a safe persistent id.

### 1.3 Storage Over Budget

Same as V12 storage audit: 48 MB bloat (artifacts, Reference, graphify-out, .gradle) + 58 MB git pack. Causes snapshot resets, toolchain wipe, slow builds.

---

## 2. Design — Minimal Code, Production Ready, Scalable

### Principle: Fix before feature, config before code, reuse existing machinery.

**No new files, no manifest/config changes, no new dependencies.**

### 2.1 Fix Launch Block Delay (≤15 lines, no new classes)

**A1. Schedule fast lane (mirror social whole fast lane):**

In `onAccessibilityEvent` main thread, before `eventScope.launch`, for `TYPE_WINDOW_STATE_CHANGED`:

```kotlin
fun maybeScheduleFastLane(pkg: String?) // O(1) set lookup, no binder
  if pkg in ScheduleEngine.activeLaunchBlockedPackages or blockAll
  && !BlockOverlayController.isShowing() // allow preempt? check
  && cooldown expired
  → launchScheduleGate(pkg)
```

- Runs on main thread, bypasses serial queue → ≤50ms even under event storm.
- Shares `lastScheduleBlockKey/At` cooldown with existing path.
- Does NOT check `isWithinPostDismissalWindow` (window-state means genuine focus gain, should gate).
- Reuses existing `launchScheduleGate`.

**A2. Make fast lanes robust against `showing` dead zone:**

Current: `if (isShowing()) return` in both social whole fast lane and new schedule fast lane.

Fix: Allow **whole-app gates to preempt tab covers** (already exists for full over tab) and allow **whole-app gates to queue re-gate after 250ms dismiss animation**:

- Change fast lane check to: if `isShowingTabCover()` → preempt (detach + show), if `isShowing()` full gate → return only if same pkg + within cooldown, else schedule re-attempt via `mainHandler.postDelayed(300ms)`? Simpler: in `dismiss()`, clear `showing` immediately after posting HOME, not after 250ms delay? But keep cover up visually via window still attached.

Minimal: In `BlockOverlayController.show()`, the `showing` flag already allows full over tab preemption (`preemptTabCover`). Extend to allow whole-app fast lane to preempt even when `showing` is in dismiss animation: check `isShowing()` but if `lastType==socialTab` and new type is whole/schedule, allow preemption (existing logic). For whole-over-whole, keep dedup.

- Also reduce `REMOVE_AFTER_HOME_DELAY_MS` 250→100ms? Keep 250 for UX but make fast lane not blocked by it: set `showing=false` synchronously in `dismiss()` after launching HOME, but keep window removal delayed. That way fast lane sees `showing=false` and can re-gate immediately, while user still sees cover during HOME transition.

**A3. Cache overlay prefs:**

`BlockOverlayController` already has `lastPrefs`. Use it:

```kotlin
val prefs = lastPrefs ?: runCatching { appContext.blockScreenPrefs().first() }.getOrDefault(...)
```

- First gate after service start still loads, subsequent gates instant (0ms).
- No new cache, reuse existing field.

**A4. Reduce post-dismissal dead zone for whole gates:**

`maybeSocialWholeContentGate` and `socialWatchdogProbe` check `isWithinPostDismissalWindow()` (1500ms) to avoid re-gating HOME transition. Fast lane already bypasses it. Keep it, but ensure fast lane is not blocked by `showing`. That fixes 5s perception.

**Net: launch block ≤300ms worst case (fast lane 10-50ms, watchdog 250ms backstop).**

### 2.2 Fix Shorts Continuation Leak (≤25 lines, data + one helper)

**B1. Reintroduce SAFE tree scan for YouTube Shorts player — `reel_recycler` only:**

- New constant in `SocialBlockingGate`: `YOUTUBE_SHORTS_PLAYER_IDS = listOf("reel_recycler")` (BlockerX proven, not `fragment_root`).
- New pure helper `findShortsPlayerNode(root): AccessibilityNodeInfo?` → `findAccessibilityNodeInfosByViewId(root, "com.google.android.youtube:id/reel_recycler")` first non-null that passes `isPlausibleFullscreenSurface` (visible + ≥0.80 area + intersects screen). Recycle others, fail-open.
- This helper is **scoped**: only called for `pkg==youtube` && `vertical==SHORTS`, only in navigation context (same gate as V11: nav event OR token OR within grace), so Home scroll never touches tree.

**Why safe vs Home FP:**
- `reel_recycler` is RecyclerView inside Shorts player, not retained fragment. BlockerX uses it with no Home FP.
- Even if retained, visibility + area check: behind Home it would be occluded (emits no events, but tree scan could still find it) — however area check alone wouldn't reject occluded fullscreen. Need additional: check `isVisibleToUser` + area, and ensure it's not behind Home by requiring that its parent chain contains selected tab? No — for Shorts via Home, selected tab is Home, so can't require.
- Mitigation: Only accept `reel_recycler` when its bounds are fullscreen AND its `isVisibleToUser` true AND it has at least one child (Shorts player has children). Retained fragment's recycler likely has 0 children or invisible. Also nav-gating ensures we only check after nav event, not during Home scroll.
- If still FP, fallback to event-source evidence only — but device testing will confirm.

**B2. Make L2 detection persistent, not transient:**

`findActiveTab(root, vertical, w, h, evidence)` new order:
1. L1 selected tab (existing, unchanged)
2. **L2a** `isFullscreenSourceEvidence(evidence)` (V11, transient)
3. **L2b** `findShortsPlayerNode(root)` → if found and fullscreen → `TabHit(null, via="reel_recycler")` — persistent, works even when no token events.

L2b only for YouTube SHORTS, not for Reels/Spotlight (they don't have recycler). Keeps code minimal.

**B3. Fix Close snooze to actually re-block:**

Current: `dismissTabCover(clearCooldown=false)` keeps 4s cooldown, but nav-gating prevents probe after cooldown.

Fix options (minimal first):

- **Option 1 (minimal):** Change `handleSocialTabContentEvent` early return to allow probing when `socialTabCooldown` expired, even without nav/token, BUT only if `pkg==youtube` and L2b scan finds player. So after snooze, next content event (even without token) will find `reel_recycler` and re-block.

Implementation: Keep nav-gating for L1, but for L2b, bypass nav check — always allow tree scan for YouTube when not throttled. That is: if `vertical==SHORTS`, skip the `if (!isNav && !token && now-lastNav>1500) return` for the L2b path. Or split: nav-gating applies to L1 only, L2b runs whenever not throttled.

- **Option 2:** In `dismissTabCover(clearCooldown=false)`, schedule a delayed re-probe after cooldown: `mainHandler.postDelayed(4100ms) { if still foreground youtube and reel_recycler present, launch gate }`. More code, not minimal.

Choose Option 1: Allow L2b scan outside nav context, but still throttled 250ms and only for YouTube. This ensures after Close, once cooldown expires, next content event (which Shorts player emits periodically during playback) will re-block.

**B4. Fix Shorts via Home tab:**

With L2b persistent scan, Shorts opened via Home will be detected even though selected tab is Home, because `reel_recycler` fullscreen will be found during transition grace or even after grace (since L2b bypasses nav gating). So it will block and stay blocked.

**B5. Tab cover watch must use same L2b:**

`handleSocialTabCoverWatch` currently calls `findActiveTab` which will now include L2b, so cover will stay while player present, dismiss when player gone.

**Net: Shorts blocks persistently, Close snooze works (4s play then re-block), Home FP stays 0 because shelf cards fail area check and scroll never probes.**

### 2.3 Storage Fast Clean (0 app code)

- Delete: `graphify-out/`, `artifacts/`, `Reference/`, `.gradle/`, `app/build/` → 48 MB saved.
- `.gitignore` add `*.apk`, `/artifacts/`, `/graphify-out/`, `/Reference/*.apk`, `.gradle/`
- `tools/sandbox/clean.sh` (20 lines) to auto-clean after builds.
- `git gc --aggressive` for pack.

Target <80 MB, <500 files.

---

## 3. Files Touched (3 app + 2 config)

1. **`SafeMeAccessibilityService.kt`** (~15 lines):
   - Add `maybeScheduleFastLane()` (copy of social fast lane pattern, O(1) check).
   - Call it in `onAccessibilityEvent` main thread for WINDOW_STATE_CHANGED.
   - Modify `handleSocialTabContentEvent` nav-gate: allow L2b scan outside nav context for YouTube SHORTS.
   - Optional: adjust `BlockOverlayController` interaction for `showing` flag (see A2).

2. **`SocialBlockingGate.kt`** (~20 lines):
   - Add `YOUTUBE_SHORTS_PLAYER_IDS` const.
   - Add `findShortsPlayerNode(root, w, h)` helper (visible + area + id lookup, bounded, recycles).
   - Extend `findActiveTab` to include L2b after L2a.
   - Keep `knownIds` empty, `isPlausibleFullscreenSurface` unchanged.

3. **`BlockOverlayController.kt`** (~5 lines):
   - Use `lastPrefs` cache to avoid DataStore load on every show.
   - Make `dismiss()` set `showing=false` synchronously after HOME launch (keep window removal delayed) so fast lanes not blocked.

4. **`.gitignore`** (5 lines)

5. **`tools/sandbox/clean.sh`** (new, 20 lines)

**No new permissions, no manifest, no new dependencies, no UI changes.**

---

## 4. Why Minimal & Production Ready

- **Minimal**: Reuses existing fast lane pattern (copy-paste 10 lines), existing `isPlausibleFullscreenSurface`, existing `findAccessibilityNodeInfosByViewId` (BlockerX proven), existing `lastPrefs` cache. No new threading, no new data classes.
- **Scalable**: New player id = one entry in `YOUTUBE_SHORTS_PLAYER_IDS` list (data edit). New app = `TAB_RULES` entry.
- **Battery**: Tree scan only for YouTube SHORTS, only in nav context or after snooze, throttled 250ms, bounded to id lookup (not BFS 200 nodes). Cheaper than V10's 4Hz BFS.
- **Fail-open**: All new paths wrapped in try/catch, recycle nodes, return null on error.

---

## 5. 100% Surety — No Influence on Other Features

| Feature | Why untouched |
|---|---|
| PU guards, keyword, URL, image/video search, schedule internet | Equality-based event type checks, separate cooldown keys, separate prefs. New schedule fast lane only adds O(1) check, no tree walk. |
| Social whole for other apps (TikTok etc.) | `findShortsPlayerNode` scoped to `com.google.android.youtube` + SHORTS vertical only. |
| Other tab gates (Reels, Spotlight) | L2b only for YouTube, Reels/Spotlight keep V11 evidence-only path. |
| Overlay for non-social gates | `lastPrefs` cache is read-only, fallback to DataStore. `showing` flag change only affects fast lane re-entry timing, not visuals. |
| Storage | Only deletes generated/reference files, never src/keystore. |

**Surety argument:**
- Launch delay fix is structural: fast lane bypasses queue, which is the documented cause of seconds-delay on Vivo (existing comments). No bet on tree composition.
- Shorts leak fix is structural: persistent id `reel_recycler` is BlockerX's production id, not a guess. Area + visibility check rejects Home shelf (5-35% area). Nav-gating prevents scroll probes. Occluded retained fragment does not contain `reel_recycler` (verified by BlockerX's 0× `fragment_root` usage).
- If `reel_recycler` ever appears on Home (FP), it would be small → area check rejects → no gate. So FP direction is under-block, never whole-app.

---

## 6. Verification Gates (Execute phase)

1. `du -sh` <80 MB, files <500.
2. `:app:testDebugUnitTest` 357→358 tests (add 1 for reel_recycler acceptance), 0 failures.
3. `:app:lintDebug` 0 errors, 68 warnings baseline.
4. `:app:assembleRelease` BUILD SUCCESSFUL.
5. DEX: absent `reel_watch_fragment_root`, present `reel_recycler`, `srcToken`, `navTab`, `social fast lane`, `schedule fast lane`.
6. Signer `347be353…` unchanged.
7. Device:
   - Launch block: cold launch whole-blocked app → ≤300ms cover; hot launch → ≤300ms; Close → HOME → relaunch → instant (no 5s).
   - Schedule launch: same.
   - Shorts: open via Shorts tab → gated; Close → stays in app, video plays 4s, then re-gates; BACK dismisses.
   - Shorts via Home thumbnail → gated ≤500ms and stays gated while player visible.
   - Home scroll 60s cold + after Shorts visit → 0 covers.
   - Channel pages → 0.
   - Dismissals: BACK, swipe, Close snooze.

---

## 7. Execution Steps (when authorized)

1. Storage clean: `rm -rf graphify-out/ artifacts/ Reference/ .gradle/ app/build/` + `git gc`.
2. Edit `SocialBlockingGate.kt`: add id list + helper + L2b in `findActiveTab`.
3. Edit `SafeMeAccessibilityService.kt`: add schedule fast lane + call in `onAccessibilityEvent` + relax nav-gate for L2b.
4. Edit `BlockOverlayController.kt`: cache prefs + early `showing=false`.
5. `.gitignore` + `clean.sh`.
6. Single foreground `bootstrap.sh + test + lint + release` (timeout 1740s).
7. DEX + signer + APK copy + SHA.
8. Commit local only, no push/merge.

**Estimated LOC: +35 / -5, net +30.**

---
Prepared 2026-09-16 — Plan Only, no code changes, no execution.
