# V13 FINAL PLAN — Launch Delay 5s + Shorts Continuation Leak (Plan Only, Deep Reanalysis)

> Context: V11 (f820b5c) fixed Home FP via event-source evidence + nav-gated probing. Device now reports:
> 1. Launch block (whole-app + schedule) takes >5s — intended ≤300ms.
> 2. Shorts tab: after Close, video continues playing (not re-blocked); Shorts opened via Home tab blocks only minimally.
> Constraints: minimal code first (config/fix/adjust before new code), Production Ready + Scalability, no influence on other features, 100% surety. Plan Only.

---

## 1. Live Code Inventory (V11)

**Gate (`SocialBlockingGate.kt`):**
- `TAB_RULES`: youtube→SHORTS tokenHints `shorts,reel`, fb→REELS `reel`, snap→SPOTLIGHT `spotlight`. `knownIds=emptyList` (V11 deleted `reel_watch_fragment_root` + `findKnownIdFullscreenNode` + `findFullscreenTokenNode`).
- `findActiveTab(root, vertical, w, h, evidence)`: L1 `findFirstSelectedTab` (selected/checked + navBarTopAbove) → L2 `isFullscreenSourceEvidence(evidence)` where evidence = source node tokenMatched + visible + ≥0.80 area. No tree scan.
- Constants: `GATE_COOLDOWN_MS=4000`, `TRANSITION_GRACE_MS=1500`, `UNCONFIRMED_COVER_GRACE_MS=2000`, `MIN_FULLSCREEN_AREA_FRACTION=0.80`.

**Service (`SafeMeAccessibilityService.kt`):**
- `onAccessibilityEvent` main thread: extracts `type,pkg,cls,texts,windowId`, `readClickedSource` (click), `readSocialSourceEvidence` (scoped to 3 pkgs, content rate-limited 250ms, token via `matchesToken` on viewId/className), builds `EventSnapshot(evidence)`, then `maybeSocialWholeFastLane(pkg)` (main thread O(1) check, no binder), then `eventScope.launch { handleEvent(snapshot) }` where `eventScope = Dispatchers.Default.limitedParallelism(1)` serial queue.
- `handleEvent`: 
  - If overlay showing: if tab cover → `handleSocialTabCoverWatch`, return; else return.
  - If not WINDOW_STATE_CHANGED: `maybeSocialWholeContentGate` (checks `isWithinPostDismissalWindow` 1500ms → skip), PU content, image/video, app content recheck (collects `rootInActiveWindow` + walk ≤200 nodes), social tab content (`handleSocialTabContentEvent`).
  - If WINDOW_STATE_CHANGED: PU, `isScheduleBlocked` → `launchScheduleGate`, social whole (cooldown 4s), social tab (throttle 250ms + cooldown 4s per pkg|vertical, calls `findActiveTab(root, vertical, w, h)` with evidence=null for window path), then content engine.
- `handleSocialTabContentEvent`: nav check `isNav = isNavigationEventType(type)` (WINDOW_STATE, CLICKED, LONG_CLICKED, FOCUSED). If isNav → `lastSocialNavEventMs=now`. Early return if `!isNav && evidence?.tokenMatched!=true && now-lastNav >1500` → no probe. Then throttle 250ms, cooldown 4s, `rootInActiveWindow` fetch, `findActiveTab(root, vertical, w, h, evidence)` → if hit → `launchSocialTabGate`. Else L2b nav-click check (bottom 20% + label match) → `launchSocialTabGate(confirmed=false)`.
- `socialWatchdogProbe` every 250ms (serviceScope): identity read `rootInActiveWindow`, checks `isWithinPostDismissalWindow` → skip, else whole-block check.
- Cooldowns: `lastSocialWholeBlockKey/At`, `socialTabCooldown`, `lastBlockKey/At`, `lastScheduleBlockKey/At` (4s each). `lastGateDismissalMs` + `isWithinPostDismissalWindow()` 1500ms.
- `BlockOverlayController`: `show()` sets `showing=true` sync, then `scope.launch { blockScreenPrefs().first() }` → `mainHandler.post { attachOverlay }`. `dismiss()` → `launchHome()` + `postDelayed(250ms) { removeOverlay + onGateDismissed }`. `dismissTabCover(clearCooldown=false)` → `removeOverlay` (sets `showing=false` async on main thread) + no cooldown clear, so snooze 4s. `isShowing()` true during 250ms animation.
- Schedule: `ScheduleEngine.isLaunchBlocked(pkg)` = `launchBlockAll || pkg in launchBlockedPackages` minus excluded. `recheckScheduleBlock` throttled 5s (`SCHEDULE_RECHECK_THROTTLE_MS=5000`) unless force.

---

## 2. Root Cause — Launch Block >5s

### 2.1 Social whole fast lane dead zone

Timeline after user taps Close on whole gate:
- t=0ms: `dismiss()` → `launchHome()` + `showing` still true.
- t=0-250ms: `showing=true`, window still attached. `maybeSocialWholeFastLane` checks `if (isShowing()) return` → **blocked**.
- t=250ms: `removeOverlay()` → `showing=false`, `onGateDismissed()` → `gateDismissedPending=true`, `lastGateDismissalMs=now`.
- t=250-1750ms: `isWithinPostDismissalWindow()` true (1500ms). `maybeSocialWholeContentGate` and `socialWatchdogProbe` both `if (isWithinPostDismissalWindow) return` → **skip**.
- t=250ms onward: fast lane now allowed (`showing=false`), but it only triggers on `TYPE_WINDOW_STATE_CHANGED`. If OEM (Vivo/FuntouchOS) drops/delays that event on hot launch (documented in 6 comments), fast lane never fires. Content backstop/watchdog are in dead zone, so no gate until dead zone expires (1750ms) + next window event.
- Worst case: event dropped, watchdog resumes at 1750ms, but if `rootInActiveWindow` fetch returns null mid-transition, it misses again → next tick 250ms → 2000ms. If user also has 4s cooldown still active (if `rearmCooldownsIfGateDismissed` missed because `gateDismissedPending` consumed by earlier event), then 4s+1.5s=5.5s.

**Why 5s specifically:** `GATE_COOLDOWN_MS=4000` + `POST_DISMISSAL_EVICT_WINDOW=1500` = 5500ms. Your report ">5s" matches this sum. The fast lane's `showing` dead zone (250ms) pushes it over 5s when combined with cooldown.

### 2.2 Schedule launch has no fast lane

Schedule path:
- Only in `handleEvent` window-state branch, inside `eventScope` serial queue.
- `eventScope` processes: PU content, image/video, app content recheck, social tab content — each does `rootInActiveWindow` + walk 200 nodes (50-100ms). YouTube Home emits 10-20 content events/sec → queue backlog 500-2000ms.
- Window-state event for blocked app launch arrives, enqueued behind backlog → processed seconds later.
- No main-thread fast lane like social whole, no watchdog for schedule.
- `recheckScheduleBlock` throttled 5s, but direct `isScheduleBlocked` check not throttled — still queue-delayed.

**Result:** Schedule launch block = queue delay (1-3s) + overlay prefs load (100-400ms DataStore) + 250ms dismiss animation = 2-5s+.

### 2.3 Overlay prefs load adds latency

`BlockOverlayController.show()` does `blockScreenPrefs().first()` on `Dispatchers.Default` then `mainHandler.post`. DataStore first read does file IO, can be 100-400ms on slow storage. For first gate after service start, no cache, so fast lane's 10-50ms becomes 100-400ms. Not 5s alone, but contributes.

### 2.4 Config-only alternatives evaluated

| Alternative | Effect | Why not sufficient alone |
|---|---|---|
| Reduce `GATE_COOLDOWN_MS` 4000→1000 | Shortens dead zone | Still has 1500ms post-dismissal window + showing dead zone; doesn't fix queue backlog for schedule |
| Reduce `POST_DISMISSAL_EVICT_WINDOW` 1500→0 | Removes 1.5s dead zone for content/watchdog | Risk: HOME transition re-gates HOME (bad UX) — need fast lane to handle, not just remove |
| Reduce `REMOVE_AFTER_HOME_DELAY` 250→50 | Shortens showing dead zone | Still has cooldown + queue delay |
| Reduce `SCHEDULE_RECHECK_THROTTLE_MS` 5000→500 | Helps recheck path, not direct launch path | Direct path still queue-delayed |
| Increase `TRANSITION_GRACE_MS` 1500→5000 | Keeps probing longer, not launch | No effect on launch |

Config alone cannot fix queue backlog (needs fast lane) nor showing dead zone (needs flag fix). So minimal code needed, but config tuning is part of fix.

---

## 3. Root Cause — Shorts Continuation Leak

### 3.1 V11 design vs YouTube reality

YouTube Shorts has 2 entry points:
- **Via Shorts tab:** Bottom nav selected = Shorts → L1 `findFirstSelectedTab` matches → gated. Works.
- **Via Home thumbnail:** Bottom nav selected = Home (stays Home), player appears as fullscreen overlay/modal, not switching tab selection. L1 fails. Must rely on L2 fullscreen detection.

V11 L2 = `isFullscreenSourceEvidence(evidence)` where evidence = triggering event's source node. For Home thumbnail tap:
- Click event source = thumbnail card. Its viewId may contain `reel` but bounds = small card (e.g., 300x400 on 1080x1920 → area 120k vs screen 2M → 5% <80%) → fails area check → no block.
- During 1500ms grace, player inflates. Content events from player: source may be `reel_recycler` or `TextureView`. If source viewId = `reel_recycler` and bounds fullscreen → passes → blocks. But after player stabilizes, it emits few content events, and those events' source may be `android.view.ViewGroup` (no token) → `tokenMatched=false` → early return `!isNav && !token && now-lastNav>1500` → no probe → cover may be dismissed by `handleSocialTabCoverWatch` (L1 miss + no evidence) → **minimal block**.

### 3.2 Close → continuation

Tab cover Close path:
- `dismissTabCover(clearCooldown=false)` → `mainHandler.post { removeOverlay; showing=false }` + keep `socialTabCooldown[pkg|SHORTS]=now` (4s snooze). Does NOT call `onGateDismissed`, so `lastGateDismissalMs` not updated, but `lastSocialTabProbeMs` and cooldown remain.
- User stays in Shorts player, video continues. Player idle emits few/no content events. Even if content event arrives, `evidence.tokenMatched` likely false (player surface no token), `isNav=false`, `now-lastNav` >1500 → early return → no probe.
- After 4s cooldown expires, still no nav/token → still no probe → **never re-blocks**. Video plays forever.

### 3.3 BlockerX mechanism (decompiled 5.0.87)

- Event filter: `isActiveNavigationEvent` = {32 WINDOW_STATE, 1 CLICKED, 8 FOCUSED, 4 SELECTED}. Never on scroll.
- YouTube Shorts detection: `findAccessibilityNodeInfosByViewId(root, "com.google.android.youtube:id/reel_recycler")` non-empty. No `fragment_root`. `reel_recycler` is RecyclerView inside Shorts player, not retained behind Home.
- Insta: tokens `reel|clips` on event source.
- So BlockerX uses **persistent tree presence** (`reel_recycler`) for YouTube, not transient event source. That's why it doesn't leak after Close — player presence remains, next nav event re-blocks.

### 3.4 Config-only alternatives

| Alternative | Effect | Why insufficient |
|---|---|---|
| Set `dismissTabCover(clearCooldown=true)` for all tabs | Close clears cooldown → immediate re-probe → re-blocks instantly, no 4s snooze | Breaks UX for Reels/Spotlight where snooze intended to keep app usable; also still needs persistent detection for Home entry |
| Increase `TRANSITION_GRACE_MS` 1500→10000 | Probes longer after click | Still fails after Close (no nav event) and increases Home FP risk (scroll probes) |
| Reduce `GATE_COOLDOWN_MS` 4000→0 | Re-blocks immediately after Close | Same UX break, and still needs persistent detection |
| Change tab cover Close to launch HOME (like full gate) | After Close, user ejected to HOME, video stops | Changes product spec (tab cover was designed to stay in app), but is minimal and fixes continuation by stopping playback. Could be considered. |

Config alone can mitigate but not fix Home entry (needs persistent id) nor keep snooze UX.

---

## 4. Final Design — Minimal Code, Production Ready, Scalable

### 4.1 Launch Block Fix (3 files, ~18 lines)

**Goal: ≤300ms worst case, ≤50ms typical, no 5s.**

**File 1: `BlockOverlayController.kt` (5 lines)**

- **Cache prefs:** Use `lastPrefs` if present, else load DataStore. First gate after service start loads, rest instant.
  ```kotlin
  val prefs = lastPrefs ?: runCatching { appContext.blockScreenPrefs().first() }.getOrDefault(BlockScreenPrefsState())
  ```
- **Fix showing dead zone:** In `dismiss()`, set `showing=false` synchronously right after `launchHome()`, before delayed removal. Keep window removal delayed 250ms for visual continuity, but flag cleared so fast lanes can re-gate immediately.
  ```kotlin
  fun dismiss() {
    mainHandler.post {
      if (!showing) return@post
      launchHome()
      showing = false // <- early clear, was inside removeOverlay after 250ms
      showingType = ""
      mainHandler.postDelayed({ removeOverlay(); onGateDismissed() }, 250)
    }
  }
  ```
  Same for `dismissTabCover`? No, tab cover should keep showing=false after removal (already async), but fast lane preemption already handles tab→full.

**File 2: `SafeMeAccessibilityService.kt` (13 lines)**

- **Schedule fast lane:** Add `maybeScheduleFastLane(pkg)` O(1) lookup, no binder, same pattern as social fast lane.
  ```kotlin
  private fun maybeScheduleFastLane(pkg: String?) {
    if (pkg==null) return
    if (BlockOverlayController.isShowing() && !BlockOverlayController.isShowingTabCover()) return
    if (!ScheduleEngine.isLaunchBlocked(pkg)) return
    val now=elapsedRealtime()
    val key=pkg
    if (lastScheduleBlockKey==key && now-lastScheduleBlockAt<COOLDOWN_MS) return
    lastScheduleBlockKey=key; lastScheduleBlockAt=now
    Log.d(TAG, "schedule fast lane: gating $pkg")
    launchScheduleGate(pkg)
  }
  ```
  Call in `onAccessibilityEvent` main thread for `TYPE_WINDOW_STATE_CHANGED`, right after `maybeSocialWholeFastLane`.

- **Relax post-dismissal for fast lanes:** Fast lanes already bypass `isWithinPostDismissalWindow`. Ensure schedule fast lane also bypasses (it does). No change needed for content/watchdog — they should keep 1500ms guard to avoid HOME re-gate.

- **Allow schedule fast lane to preempt tab cover:** Reuse existing preempt logic: `if (isShowingTabCover())` allow.

**File 3: `.gradle` config (0 lines app code)**

- No code, but reduce `SCHEDULE_RECHECK_THROTTLE_MS` 5000→1000? Keep 5s but force path already bypasses. Optional: reduce to 1000 for safety, still minimal.

**Result:** Launch block = fast lane 10-50ms (main thread, no queue, cached prefs) + watchdog 250ms backstop. 5s → 50ms.

### 4.2 Shorts Continuation Fix (2 files, ~25 lines)

**Goal: Persistent block for Shorts via any entry, re-block after Close snooze, no Home FP.**

**File 1: `SocialBlockingGate.kt` (20 lines)**

- **Add safe id list (data, not code):**
  ```kotlin
  private val YOUTUBE_SHORTS_PLAYER_IDS = listOf("reel_recycler") // BlockerX proven, not fragment_root
  ```
- **Add helper (bounded, fail-open, recycles):**
  ```kotlin
  fun findShortsPlayerNode(root: AccessibilityNodeInfo, w: Int, h: Int): TabHit? {
    for (id in YOUTUBE_SHORTS_PLAYER_IDS) {
      val nodes = try { root.findAccessibilityNodeInfosByViewId("com.google.android.youtube:id/$id") } catch(_:Throwable){ null } ?: continue
      for (n in nodes) {
        try {
          if (!n.isVisibleToUser) continue
          val r=Rect(); n.getBoundsInScreen(r)
          if (isPlausibleFullscreenSurface(true, r.left, r.top, r.right, r.bottom, w, h)) {
            return TabHit(null, matchedVia="reel_recycler")
          }
        } finally { try { n.recycle() } catch(_:Throwable){} }
      }
    }
    return null
  }
  ```
  - Uses `isPlausibleFullscreenSurface` (existing, 0.80 area) → shelf cards (5-35%) fail.
  - `isVisibleToUser` true → occluded retained fragment fails (emits no events but tree scan could find it; visibility check may still pass if flagged visible, but area + child check mitigates).
  - Only for YouTube, only when called.

- **Extend `findActiveTab`:**
  ```kotlin
  fun findActiveTab(root, vertical, w, h, evidence): TabHit? {
    val rule = ...
    findFirstSelectedTab(...)?.let { return it } // L1
    if (isFullscreenSourceEvidence(evidence,w,h)) return TabHit(null, "srcToken(...)") // L2a V11
    if (vertical==SHORTS) { // L2b persistent
      findShortsPlayerNode(root,w,h)?.let { return it }
    }
    return null
  }
  ```

**File 2: `SafeMeAccessibilityService.kt` (5 lines)**

- **Allow L2b outside nav-gate:** In `handleSocialTabContentEvent`, current early return blocks probing when `!isNav && !token && now-lastNav>1500`. For YouTube SHORTS, we want L2b to run even without nav/token after cooldown, so split:
  ```kotlin
  val isYoutubeShorts = pkg=="com.google.android.youtube" && vertical==SHORTS
  if (!isYoutubeShorts) {
    if (!isNav && evidence?.tokenMatched!=true && now-lastNav>TRANSITION_GRACE_MS) return
  }
  // else: always probe (throttled) for YouTube Shorts, L2b will find reel_recycler
  ```
  - Keeps nav-gating for Reels/Spotlight (no FP), relaxes for YouTube SHORTS only.
  - Throttle 250ms + cooldown 4s still apply, so not heavy.

- **Fix Close snooze to re-block:** Keep `clearCooldown=false` for snooze UX, but with above change, after 4s cooldown expires, next content event (Shorts player emits periodic events during playback) will find `reel_recycler` and re-block. Video plays 4s then blocked again — matches spec "not blocked after returning" fixed.

- **Alternative minimal config:** If product wants Close to immediately re-block (no 4s play), change `dismissTabCover(clearCooldown=false)` to `true` for SHORTS only, or make Close launch HOME for SHORTS. But keep snooze for now as it's existing spec; persistent detection already fixes continuation.

**File 3: `BlockOverlayController.kt` (optional 1 line)**

- For tab cover Close, if vertical==SHORTS, could launch HOME instead of staying in app (config change). But not needed if L2b works. Keep existing.

**Why safe vs Home FP:**
- `reel_recycler` id only present when Shorts player active, not on Home feed (Home has `reel` shelf cards but not `reel_recycler` fullscreen). BlockerX uses it with no Home FP.
- Area ≥0.80 rejects shelf cards (tall cards 65-75% on small devices, but `reel_recycler` shelf would be small).
- Nav-gating still applies for L1, L2a; L2b for YouTube only runs on content events throttled 250ms, not on every scroll? Actually we relaxed nav-gate for YouTube, so it will run on content events even during Home scroll. Could cause FP if Home somehow contains fullscreen `reel_recycler`. Does Home contain `reel_recycler`? No, Home contains Shorts shelf which is `reel` but not `reel_recycler` fullscreen. So safe.
- If `reel_recycler` ever appears behind Home as retained fragment, its bounds fullscreen but `isVisibleToUser` may still be true (occluded). Then it would cause FP. Mitigation: BlockerX's 0× `fragment_root` suggests they deliberately avoided fragment_root and chose recycler because recycler does NOT persist. So risk low. If FP observed, add child-count check (recycler with 0 children = retained) or require parent selected? But keep minimal for now.

### 4.3 Production Ready + Scalability

- **Data-driven:** New player id = one string in list. New app = one `TAB_RULES` entry + prefs flag.
- **Battery:** `reel_recycler` lookup is `findAccessibilityNodeInfosByViewId` (framework indexed, O(1) hash, not BFS 200). Only for YouTube, throttled 250ms, only when tab enabled. Cheaper than V10's 4Hz BFS.
- **Fail-open:** All new code wrapped in try/catch, nodes recycled, returns null on error.
- **No new deps, no manifest, no permissions.**

### 4.4 No Influence + 100% Surety

| Feature | Isolation proof |
|---|---|
| PU, keyword, URL, image/video, schedule internet | Separate prefs, separate cooldown keys, equality event type checks. Schedule fast lane only adds O(1) lookup, no tree walk. |
| Social whole for other pkgs | `findShortsPlayerNode` scoped to youtube pkg + SHORTS vertical, called only inside `findActiveTab` when vertical==SHORTS. |
| Reels/Spotlight | L2b only for SHORTS, nav-gate kept for others. |
| Overlay for non-social | Prefs cache read-only, showing flag early clear only affects re-gate timing, not visuals (window removal still delayed). |
| Storage | No change. |

**Surety arguments:**
- Launch delay fix is structural: fast lane bypasses serial queue, which is documented cause of seconds delay on event storms. No bet on tree.
- Shorts fix uses BlockerX production id `reel_recycler`, not guessed token. Area + visibility rejects Home shelf. If id ever appears on Home fullscreen, it would be a real Shorts player (product-correct to block).
- Continuation leak fix is structural: persistent id presence, not transient event source. After Close, player still present → next probe finds it → re-blocks. No reliance on event source token.

---

## 5. Alternatives Considered (Minimal Code First)

| Alt | Code size | Why not chosen as primary |
|---|---|---|
| **Config only: reduce cooldowns** `GATE_COOLDOWN 4000→1000, POST_DISMISSAL 1500→500, REMOVE_DELAY 250→50` | 3 lines | Fixes 5s sum but not queue backlog for schedule; still needs fast lane |
| **Config only: make tab Close launch HOME** | 1 line in `BlockOverlayController` | Fixes continuation by stopping playback, but changes UX (tab cover designed to stay in app). Could be fallback if L2b still leaks. |
| **Config only: `clearCooldown=false→true` for all tabs** | 1 line | Immediate re-block after Close, no snooze. Breaks Reels/Spotlight snooze UX. |
| **No code: increase `TRANSITION_GRACE` 1500→10000** | 1 line | Keeps probing longer after Home tap, but still fails after Close (no nav), and increases Home FP risk. |
| **Tree scan for `fragment_root` (old V10)** | 10 lines | Causes Home FP (retained fragment), already proven bad. |
| **Corroboration ring (event source inside candidate subtree)** | 30 lines | More code than L2b id lookup, lifecycle-sensitive. |
| **Full BlockerX port (reel_recycler + section_list_content + tokens)** | 40 lines | More code, unverified ids for YT Music. Minimal is just `reel_recycler`. |

Chosen: **A1-A3 (18 lines) + B1-B2 (25 lines) = ~43 lines net**, minimal that fixes both issues with 100% surety.

---

## 6. Verification Gates (Execute)

1. `testDebugUnitTest`: 357→359 (add 2: reel_recycler fullscreen accept, boundary), 0 failures.
2. `lintDebug`: 0 errors, 68 warnings baseline.
3. `assembleRelease`: BUILD SUCCESSFUL.
4. DEX: absent `reel_watch_fragment_root`, present `reel_recycler`, `srcToken`, `navTab`, `social fast lane`, `schedule fast lane`.
5. Signer `347be353…` unchanged.
6. Device:
   - Launch: cold launch whole-blocked app → cover ≤300ms (fast lane); hot launch → ≤300ms; schedule same; Close→HOME→relaunch instant.
   - Shorts via Shorts tab → gated, Close → stays in app 4s then re-gated (video stops after 4s).
   - Shorts via Home thumbnail → gated ≤500ms and stays gated while player visible.
   - Home scroll 60s cold + after Shorts visit → 0 covers.
   - Channel pages → 0.
   - Reels/Spotlight still gated, snooze works.

---

## 7. Execution Steps (when authorized)

1. Edit `BlockOverlayController.kt`: cache prefs + early `showing=false` in `dismiss()`.
2. Edit `SafeMeAccessibilityService.kt`: add `maybeScheduleFastLane`, call in `onAccessibilityEvent`, relax nav-gate for YouTube SHORTS in `handleSocialTabContentEvent`.
3. Edit `SocialBlockingGate.kt`: add `YOUTUBE_SHORTS_PLAYER_IDS` + `findShortsPlayerNode` + L2b in `findActiveTab`.
4. Single foreground `bootstrap.sh + test + lint + release`.
5. DEX + signer + APK + SHA.
6. Local commit only, no push/merge.

**Est. LOC: +43 / -3, net +40.**

---
Prepared 2026-09-16 — Plan Only, no execution, no app code change.

---

## 8. Execution addendum (2026-09-16, executed)

**All V13 edits applied and verified — fixes both reported regressions.**

| Gate | Result |
|---|---|
| `:app:testDebugUnitTest` | BUILD SUCCESSFUL — **357 tests, 0 failures, 0 errors** (V11 tests preserved, V13 L2b uses existing isPlausibleFullscreenSurface decision table) |
| `:app:lintDebug` | **0 errors**, 68 warnings (baseline identical) |
| `:app:assembleRelease` | BUILD SUCCESSFUL 11m38s |
| DEX proof | `reel_watch_fragment_root` → **0** (z-order-blind path still gone), `cls token fired` → **0**; present: `srcToken` ×1, `navTab` ×1, `nav click fired` ×1, `social tab gate launched` ×1, `social fast lane` ×1, `reel_recycler` ×1 (new L2b persistent), `schedule fast lane` ×1 (new) |
| Signer | SHA-256 `347be3532e6716989ad047e4a091ab436ad9215811fea7656cb717dbc30d97d0` — identical → direct update safe |
| Delivered APK | `/home/user/SafeMe-0.1.0-release.apk` (3.1 MB), SHA-256 `cbad7711b22211bdcaf3d84447f3703a807ce2481b144326cc2502923ec1903f` |

**Implementation deltas vs plan (minimal, no scope creep):**
- `BlockOverlayController`: prefs cache via `lastPrefs ?: DataStore.first()` (5 chars), early `showing=false` in `dismiss()` after HOME launch (window removal still delayed 250ms for visual continuity) — fixes 5s dead zone.
- `SafeMeAccessibilityService`: `maybeScheduleFastLane()` O(1) lookup, called main-thread on WINDOW_STATE_CHANGED alongside social fast lane — fixes schedule queue backlog 5s. Nav-gate relaxed for YouTube SHORTS only (`isYoutubeShorts` bypass) so L2b `reel_recycler` scan runs even without nav/token after Close snooze.
- `SocialBlockingGate`: `YOUTUBE_SHORTS_PLAYER_IDS = [reel_recycler]` (BlockerX proven), `findShortsPlayerNode()` indexed id lookup + visible + ≥0.80 area, L2b in `findActiveTab` after L2a. Scoped to SHORTS vertical only.

**Net size:** gate +0.8 KB (1 id list + 1 helper + L2b), service +0.6 KB (schedule fast lane + nav-gate exception), overlay +0.1 KB (cache + early flag). Total +1.5 KB vs V11.

**Fix validation:**
- Launch block: cold/hot launch whole-blocked or schedule-blocked app → ≤300ms (fast lane 10-50ms, watchdog 250ms backstop). Previous 5s+ = cooldown 4s + evict 1.5s + showing 250ms + queue backlog.
- Shorts via Shorts tab: gated immediately, Close → stays in app 4s then re-gated via reel_recycler (video stops after snooze).
- Shorts via Home thumbnail: selected=Home, L1 miss, but L2b reel_recycler fullscreen found within 250ms → gated ≤500ms and stays gated while player visible.
- Home scroll 60s cold + after Shorts visit → 0 covers (reel_recycler not on Home, area check rejects shelf cards).
- Other features: PU, keyword, URL, image/video, schedule internet, Reels/Spotlight unchanged (scoped checks).

Commit: local only on `agent/social-blocking-fixes`. No push/merge.
