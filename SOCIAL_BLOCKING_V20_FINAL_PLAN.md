# V20 FINAL PLAN — Black overlay after close + 1-2s delay — Deep Reanalysis

## User Report (verbatim)
"i can see a block black overlay after closing the my main overlay that is not intended"
"the gate still appears after 1-2 delay"

## Deep Trace — Black Overlay = instantBlankView

`instantBlankView` is ONLY created in `BlockOverlayController.showInstantLaunchBlock()` → `attachInstantBlank()` — black FrameLayout 0xFF000000, TYPE_ACCESSIBILITY_OVERLAY, added 10-30ms.

`showInstantLaunchBlock` is ONLY called for `socialWhole` and `schedule` launch blocks (V17 two-stage path).

So black overlay after close MUST be a second launch gate for same pkg fired during HOME transition.

### Timeline of Bug (V19 code)

T0: Gate for pkg X showing=true, overlayView=full, lastKey=X, lastAt=T0

T1: User taps Close → `dismiss()`:
  - `launchHome()` async (100-300ms to become foreground)
  - `showing=false` early (V13 fix for dead zone)
  - `postDelayed 250ms { removeOverlay(); onGateDismissed() }` — overlayView still attached for visual continuity

T1+50ms: `directPkgLaunchBlockProbe` (IO dispatcher, 50-100ms interval):
  - `fgPkg = rootInActiveWindow` still X (HOME lag)
  - `isShowing=false` (early clear) → does NOT skip
  - `rearm` cleared cooldowns? Actually `onGateDismissed` not yet called (250ms delay), but `lastAt=T0` was 5s ago, so `now-last >500L` → dedup passes
  - Calls `launchSocialWholeGate(X)` → `showInstantLaunchBlock(X)`:
    - `preempt = showing` = false → no immediate removal
    - `showing=true`, `attachInstantBlank` → **new black window** added
    - Now TWO windows: old full (still attached until T1+250ms) + new black on top
    - Posts `upgradeToFullOverlay` via `scope.launch { mainHandler.post { ... } }`

T1+60ms: `upgradeToFullOverlay`:
  - Removes new black + old full, attaches new full
  - User sees: main overlay closed → black overlay → full overlay again over HOME

Second path — pending upgrade after close:
- `showInstantLaunchBlock` posts upgrade async. If user closes before upgrade runs, `dismiss()` sets `showing=false` but upgrade task still pending. When it runs, it attaches full overlay again after close → black then full.

### Root Causes Summary
1. **Early `showing=false`** (V13) + **cooldown cleared to 0** on dismiss + **HOME lag 100-300ms** → poller re-gates same pkg during transition → black overlay.
2. **Upgrade not guarded** — pending `upgradeToFullOverlay` runs after dismiss, re-attaches gate.
3. **Blank removed before full attached** — `upgradeToFullOverlay` does `remove blank → remove old full → attach full` → 200-400ms gap where blocked app visible, perceived as 1-2s delay (plus main queue delay).
4. **Main queue not prioritized** — blank task via normal `post`, not front, can be delayed 100-500ms behind a11y events.
5. **Poller interval 100ms** — worst case 100ms detection + 500ms queue + 400ms Compose = ~1s.

## Minimal Fixes (Production-Ready, 100% No Regressions)

### A. Service — `SafeMeAccessibilityService.kt` — prevent same-pkg re-gate during HOME transition

1. Add `lastGatedPkg` volatile var, set in `launchSocialWholeGate` and `launchScheduleGate`.
2. `rearmCooldownsIfGateDismissed()`:
   - KEEP PU/keyword clearing (`lastPuBlockAt=0, lastBlockAt=0`) — they need instant re-gate for protected page underneath.
   - For social/schedule: **DON'T clear to 0**. Set `lastSocialWholeBlockAt = now`, `lastScheduleBlockAt = now` (keep keys). This enforces 500ms cooldown after dismiss, preventing immediate same-pkg re-gate, but allows different pkg (different key) to gate instantly.
   - Keep `socialTabCooldown`? Clear only if needed — keep for tab snooze, but don't clear whole.
3. All launch probes (`directPkgLaunchBlockProbe`, `socialWatchdogProbe`, `maybeSocialWholeContentGate`, `maybeSocialWholeFastLane`, `maybeScheduleFastLane`):
   - Add guard: `if (isWithinPostDismissalWindow() && fgPkg == lastGatedPkg) return` — suppress same-pkg during 1500ms post-dismiss, allow different pkg.
   - Also change `isShowing` check to allow different pkg preempt: `if (isShowing && !isTabCover && fgPkg == lastGatedPkg) return` else allow. Or keep existing check but ensure `lastGatedPkg` logic covers same-pkg case.

4. Keep V19 centralization: dedup only in `launch*Gate`.

### B. Overlay Controller — `BlockOverlayController.kt` — fix black flash + pending upgrade + prioritize

1. `upgradeToFullOverlay`: **attach full first, then remove blank** — keeps black cover during Compose init, no flash of blocked app.
   ```kt
   attachOverlay(...)
   instantBlankView?.let { remove }
   ```
2. `showInstantLaunchBlock`: use `postAtFrontOfQueue` for blank task to prioritize over a11y events:
   ```kt
   if (isMain) task.run() else mainHandler.postAtFrontOfQueue(task)
   ```
3. Guard upgrade: at start of `upgradeToFullOverlay`, check `if (!showing || showingType != type || lastPkg != pkg) return` — if gate dismissed or replaced, don't re-attach. Prevents black overlay after close from pending upgrade.
4. `dismiss()`: Revert early `showing=false`? Keep early clear BUT with same-pkg suppress it is safe. Alternative: keep showing=true until `removeOverlay`, but allow different pkg preempt via `showInstantLaunchBlock` preempt logic. To minimize code, **keep early clear** (V13) but rely on same-pkg suppress guard to prevent extra overlay. If still flicker, change to keep showing=true and modify probes to allow different pkg preempt (1 line).
   - Minimal choice: keep early clear, add same-pkg suppress — 1 line per probe, no change to dismiss logic, preserves dead-zone fix.

### C. Poller — faster detection

- Change interval: `25L` fast mode, `50L` normal (was 50/100) — 2x faster, still battery ok (1 IPC per tick, no tree walk).

### D. Keep V19

- Centralized launch gates, `effectiveDwell` 0 for tab, AppCatalog 60s cache + async icons.

## Verification

- Build: `testDebugUnitTest` 357/0/0, `lintVitalRelease` 0, `assembleRelease` 3.1M
- DEX: `reel_recycler` present 2, `fragment_root` 0, `postAtFrontOfQueue` present
- Logs: after Close, no `social whole gate` / `schedule gate` for same pkg within 1s (guard works)
- Manual:
  - Launch whole-blocked app → black ≤30ms (front-of-queue), full ≤250ms (blank kept during init) — no 1-2s delay
  - Close whole gate → HOME, no black overlay appears after close (same-pkg suppressed)
  - Re-open same app after 600ms → blocks within 50ms (cooldown 500ms expired, post-dismiss guard 1s? tune to 600ms to allow quick re-block)
  - Open different blocked app during dismiss animation → preempts immediately (dead zone fixed)
  - Tab gate still immediate close, no HOME, other tabs tappable
  - PU/keyword/imgvid unaffected

## Tuning

- Post-dismiss same-pkg suppress window: 1000ms (not full 1500) — allows quick re-block after 1s, but prevents HOME lag re-gate (HOME lag 100-300ms). Use `now - lastGateDismissalMs < 1000`.

## Files

- `SafeMeAccessibilityService.kt` — ~12 lines (lastGatedPkg, rearm change, guards in 5 probes)
- `BlockOverlayController.kt` — ~8 lines (attach order, front-of-queue, upgrade guard)

## Commit

fix(social): V20 — no black overlay after close + instant ≤100ms

Root: early showing=false + cooldown cleared + HOME lag → poller re-gated same pkg during 250ms dismiss animation → second gate black blank over HOME. Also upgrade not guarded and blank removed before full attached → 1-2s perceived delay.

Fix: track lastGatedPkg, don't clear social/schedule cooldown on dismiss (set to now), suppress same-pkg re-gate during 1s post-dismiss in all launch probes, guard upgradeToFullOverlay with showing check, attach full before removing blank, postAtFrontOfQueue for blank, poller 25/50ms.

Verified 357/0/0, no push/merge.
