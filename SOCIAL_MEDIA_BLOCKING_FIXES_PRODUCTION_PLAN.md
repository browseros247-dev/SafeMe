# Social Media Blocking — PRODUCTION Fix Plan (Minimal Core + Hardening + Scalability)

> **Date:** 2026-09-16 · **Branch target:** `agent/social-blocking-fixes` (from `main` @ `faf4ede`)
> **Status:** PLAN ONLY — awaiting `Execute`. **Authoritative v3** — supersedes `…_MINIMAL_PLAN.md` (v2, kept as the core spec) and `…_FINAL_PLAN.md` (v1, deferred-mechanisms reference).
> **Formula:** the v2 minimal core stays exactly as specified; this plan adds only (a) named production-hardening items — each mapped to a concrete failure risk — and (b) scalability seams that make future growth a data change, not an engine change. **No speculative generality.**
> **Baseline verified:** toolchain up (JDK 25.0.4 + SDK 36); `:app:testDebugUnitTest` @ `faf4ede` = **317 tests / 0 failures**.

---

## 1. Recap — the v2 minimal core (unchanged)

| # | Fix | Core change | ~Lines |
|---|---|---|---|
| 1 | Real app icons | `InstalledAppIcon` composable in `SocialBlockingIcons.kt` (produceState + IO + `getApplicationIcon` + `toBitmap()`, first-installed-of-family wins, pastel-letter fallback) wired into `LaunchRow`/`TabRow`/picker | 40 |
| 2 | Tab-only blocking | `findTabNode` requires `isSelected`/`isChecked` on match-or-ancestor ≤3 up + returns nav-bar bounds; `show(coverAboveY=…)` sized window above nav; `dismissWithoutHome()`; tab watch on the `isShowing()` branch auto-dismisses when the user leaves the tab | 80 |
| 3 | Instant whole-app block | Content-event set-lookup backstop (6 ln) + social check piggybacked on the already-running 250 ms watchdog tick (12 ln) | 20 |

Root causes and line-number evidence: see v2 §“Root causes” (all re-verified twice; nothing changed).

---

## 2. Production hardening ledger (each item = named risk → small cost)

### H1. Cover-type collision preemption (~8 ln, `BlockOverlayController.show`)
**Risk (verified):** `show()` starts with `if (showing) return`. If a tab cover is up and a *full* gate
must fire (keyword hit under the cover, schedule start, PU surface), the full gate is **silently
skipped** — a real enforcement hole.
**Fix:** when incoming `type != "socialTab"` and the current cover is a tab cover → remove the tab
window immediately (no HOME, no dismissal signal) and proceed with the full gate. Full-over-full and
tab-over-tab dedupe exactly as today.

### H2. Sized-window re-assert fidelity (~4 ln, controller)
**Risk:** OEM wake re-assert (`refreshOverlay`, L349–360) reuses stored `overlayLp` → sized tab windows
survive ✅ (verified). But the full-rebuild path `reattachOverlay()` reconstructs from the `last*`
params (L130–134) — without a stored `lastCoverAboveY`, a rebuilt tab cover would come back
**full-screen** (the exact bug we're fixing).
**Fix:** persist `coverAboveY` alongside `lastPkg/lastType/lastPrefs`; `reattachOverlay` passes it back.

### H3. Bounds-drift refit (~8 ln, service tab watch)
**Risk:** rotation, font-scale change, or nav show/hide leaves the cover height stale (feed strip
exposed or nav half-covered).
**Fix:** the tab watch (v2 §2c) already re-runs `findTabNode` on every content event while covered —
when it returns nav bounds that differ from the current cover height by >48 px, `updateViewLayout`.
No new probe path; pure reuse of the watch we're already building.

### H4. Main-thread fast lane for whole-app blocks (~10 ln, service)
**Risk:** `eventScope` is serial (`limitedParallelism(1)`); under event storms the window-state gate
can queue behind in-flight processing — the only remaining latency class after the v2 backstops.
**Fix:** in `onAccessibilityEvent`, before enqueueing: `TYPE_WINDOW_STATE_CHANGED` +
`cachedSocialState` (volatile) enabled + `pkg ∈ wholeBlocked` + not own/exempt + no full cover →
`BlockOverlayController.show(…)` directly (thread-safe; `showing` set synchronously). Cost: one set
lookup on the main thread (µs). This restores v1's fast lane **because production latency SLA
(“instant”) justifies 10 lines** — with the content backstop and watchdog kept as the deeper layers.

### H5. Bookkeeping policy enforced in code (~3 ln, controller)
**Risk:** tab covers would inflate `blockedToday` and spam the activity feed (each tab re-entry logs).
**Fix:** guard the existing bookkeeping block with `type != "socialTab"`. Whole-gate bookkeeping untouched.

### H6. Structured logging (~6 ln, service + controller)
Gate launches, tab cover dismissals, refits and preemptions get `Log.d(TAG, …)` lines matching house
style — field-debuggability without a debugger (this codebase is debugged via logcat on OEM devices).

### H7. Docs parity (~20 ln, `docs/05-blocking-engine.md`)
**Verified gap:** the blocking-engine doc has **zero** mention of the social gates (grep: no hits).
Add a short “Social Media Blocking gates” section: whole-gate delivery layers (window-state → fast
lane → content backstop → watchdog), tab-gate selected-state rule, scoped cover + auto-dismiss,
cooldown semantics. Production = the next engineer can maintain this without archaeology.

### H8. Commit hygiene (0 ln)
One commit per fix (+tests), conventional-commit messages matching repo history
(`fix(social): …`), so any single fix can be reverted independently — the rollback story.

**Explicit non-hardening (considered, rejected as not production-relevant):** DataStore prefs
caching (cold read once/process, ms-scale), stale-cooldown recovery (watchdog re-gates within the
4 s key expiry once `isShowing()==false`), Robolectric traversal tests (traversal stays untested
exactly as `findTabNode` is today — unchanged risk, documented).

---

## 3. Scalability seams (growth = data edits, not engine edits)

### S1. Tab-rule registry (~10 ln restructure, `SocialBlockingGate`)
Replace the parallel constants (`FEATURE_PACKAGES` map + 3 private regexes + `isVerticalEnabled`
when-chain) with one table:
```kotlin
data class TabRule(val label: Regex, val tokenHints: List<String> = emptyList())
val TAB_RULES: Map<String /*pkg*/, Pair<SocialVertical, TabRule>>
```
**Adding a future app/vertical** (e.g. Instagram Reels tab, TikTok) = one map entry + one UI row
(`TabRow` is already parameterized) + one prefs boolean — engine, controller, and watch need no
change. `tokenHints` is the reserved slot for the deferred L2 fullscreen detection: it ships empty
(no behavior), so the v1 escalation path becomes additive data, never rework.

### S2. Icon families as data (~4 ln, screen)
`LaunchItem` gains `iconPackages: List<String> = listOf(pkg)`. Rows declare their family; the
composable iterates. New brands/families never touch loading code.

### S3. Bounded icon cache (~15 ln, `InstalledAppIcon`)
Production picker lists can hold 100–300 apps; `LazyColumn` recycling would re-decode on scroll-back.
`android.util.LruCache` (thread-safe), 48 entries @ density-scaled 2× size, negative results cached.
Constant memory (~4 MB ceiling), constant lookup time regardless of installed-app count.

### S4. Scale-flat enforcement costs (design property, 0 ln)
`wholeBlocked` membership = O(1) set lookup everywhere (fast lane, backstop, watchdog) — blocking
300 apps costs the same as 5. Tree walks stay capped (`MAX_DEPTH 12`/`MAX_STRINGS 200`) and only run
inside `FEATURE_PACKAGES`. Watchdog cost is constant (one pkg read/tick) and self-disables when the
feature is off/empty. No allocation in the hot event path.

### S5. Generalized anchored covers (property of v2 `coverAboveY`, 0 extra ln)
The optional-bounds parameter is not Shorts-specific: any future anchored cover (top-banner ads,
comment sections) reuses the same window plumbing.

---

## 4. OEM-quirk × mechanism matrix (production evidence table)

| Quirk (documented in this codebase or by Android) | Covered by |
|---|---|
| Window-state events dropped/delayed (Vivo/FuntouchOS) | H4 fast lane (when delivered) → content backstop → watchdog ≤250 ms |
| Overlay windows hidden on screen-off and never re-shown | existing SCREEN_ON receiver + `refreshOverlay` — works for sized windows (verified L352), H2 keeps full-rebuild faithful |
| A11y service rebind drops overlay token | existing `reassertIfShowing(serviceContext)` + H2 |
| Rotation / font-scale / nav resize under a tab cover | H3 refit via the tab watch |
| Event storm delays serial queue | H4 (bypasses queue) |
| Full gate needed while tab cover up | H1 preemption |
| Rapid tab re-entry after auto-dismiss | existing `onGateDismissed` re-arm clears `socialTabCooldown` (service L413–417) — verified |
| Split-screen / multi-window | bounds come from `getBoundsInScreen` (screen coords) — sized window stays correct; noted limitation: cover may extend past the app pane (cosmetic) |
| Uninstalled app icon lookup | `runCatching` → pastel-letter fallback (unchanged visual) |

---

## 5. Code ledger (final)

| File | Change | ~Lines |
|---|---|---|
| `ui/screens/socialblocking/SocialBlockingIcons.kt` | `InstalledAppIcon` + LruCache (S3) | 40 |
| `ui/screens/socialblocking/SocialBlockingScreen.kt` | 3 call-site swaps + `iconPackages` (S2) | 20 |
| `protect/SocialBlockingGate.kt` | selected-ancestor rule + nav-bounds + `TAB_RULES` registry (S1) | 35 |
| `BlockOverlayController.kt` | `coverAboveY` (+H2 persistence), `dismissWithoutHome`, `showingType`, H1 preemption, H5 bookkeeping guard, Close branch, H6 logs | 50 |
| `service/SafeMeAccessibilityService.kt` | tab watch (+H3 refit), H4 fast lane, content backstop, watchdog piggyback, coverAboveY plumbing, H6 logs | 95 |
| `docs/05-blocking-engine.md` | H7 social-gates section | 20 |
| tests (new `SocialBlockingGateTest` + additions) | whole-gate exemptions, tab cooldown/re-arm, throttle helpers, `coverAboveY` default pass-through — house pure-function style (verified against `SafeMeAccessibilityServiceScheduleTest`) | 60 |
| **Total** | **6 files edited + 1 test file** | **~320** |

Still ~⅓ of the v1 estimate, and every line beyond the v2 core maps to a named risk (H1–H8) or a
named growth axis (S1–S5).

## 6. Verification (unchanged bar)

1. `:app:testDebugUnitTest` — baseline 317/0 recorded; must stay green + new tests pass.
2. `:app:lintDebug`, `:app:assembleDebug`.
3. Manual device checklist (v2 §Verification) **plus** production cases: rotate under cover → refits;
   screen off/on under cover → re-asserts sized; keyword block fires over a tab cover (H1);
   recents hot-launch of blocked app → ≤300 ms; Close tab cover → stays in app; Close full gate → HOME.

## 7. Still refused (kept out on purpose)

Pure-core `isTabActive` flattener, `TabBlockOverlay` composable, dedicated social watchdog job,
L2 fullscreen token detection (registry slot reserved via S1), DataStore prefs caching, family-wide
row toggling, `ScheduleSheets` refactor. Each has a documented revisit trigger (v2 §“Deferred”).
