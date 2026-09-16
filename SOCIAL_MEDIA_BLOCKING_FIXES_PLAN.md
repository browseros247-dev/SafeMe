# Social Media Blocking — 3-Issue Fix Plan (Deep Analysis)

> **Date:** 2026-09-16 · **Branch target:** `agent/social-blocking-fixes` (from `main` @ `faf4ede`)
> **Scope:** Social Media Blocking only (Launch Block + In-App Tab Block). No changes to PU / schedule / keyword / title / URL gates.
> **Status:** PLAN ONLY — awaiting `Execute`.

---

## 0. Evidence base (files read end-to-end)

| File | Role |
|---|---|
| `ui/screens/socialblocking/SocialBlockingScreen.kt` (615 ln) | Cards UI — `LaunchRow` L361, `TabRow` L447, `SocialPickerSheet` L517 |
| `ui/screens/socialblocking/SocialBlockingViewModel.kt` (194 ln) | prefs ↔ UI state |
| `data/SocialBlockingPrefs.kt` (164 ln) | DataStore state, TikTok/Instagram package families |
| `protect/SocialBlockingGate.kt` (120 ln) | pure gate logic: `isWholeAppBlocked`, `findTabNode`, throttles |
| `service/SafeMeAccessibilityService.kt` (2440 ln) | event pipeline `handleEvent` L551; social block L671–719; content probe L2150; watchdog L~1950 |
| `BlockOverlayController.kt` (559 ln) | universal full-screen gate window + `BlockGateActivity` fallback |
| `data/AppCatalog.kt`, `ui/screens/schedule/ScheduleSheets.kt` | app discovery + **existing real-icon pattern** (L397–410) |
| `res/xml/accessibility_service_config.xml` | event types + `notificationTimeout=100` |

---

## 1. Issue #1 — Real app icons on cards (highest priority)

### 1.1 Root cause (verified)
The three card renderers draw the **first letter of the app name** in a pastel box — the real
launcher icon is never loaded:

- `SocialBlockingScreen.kt:378` — `LaunchRow`: `Text(text = item.label.take(1).uppercase(), …)`
- `SocialBlockingScreen.kt:463` — `TabRow`: `Text(text = title.take(1).uppercase(), …)`
- `SocialBlockingScreen.kt:592` — picker sheet rows: `Text(text = app.label.take(1).uppercase(), …)`
- Extra launch rows (user-added apps) additionally fall back to `pkg.substringAfterLast(".")` as
  label and generic grey pastels (`LaunchBlockSection`, L344–352).

Meanwhile `ScheduleSheets.kt AppRow` (L397–410) already proves the working pattern in this
codebase: `produceState` + `Dispatchers.IO` + `packageManager.getApplicationIcon(pkg)` →
`BitmapDrawable` → `asImageBitmap()`, letter fallback. `InstalledApp` (AppCatalog) intentionally
carries no icon — icons are loaded per-composable.

### 1.2 Fix design
1. **New shared component** `ui/components/AppIconImage.kt`:
   - `@Composable fun AppIconImage(packageNames: List<String>, fallback: @Composable () -> Unit, modifier)`
   - Loads via `produceState` + IO dispatcher with `getApplicationIcon`, trying each package in
     order and using the **first installed** one (family support).
   - Process-wide `LruCache<String, ImageBitmap>` (~64 entries, ~48dp bitmaps ≈ few MB) so the
     picker list scroll and recompositions never re-decode; cache survives configuration changes.
   - Not-installed / load failure → renders the `fallback` slot (the existing pastel letter) —
     the screen must look identical to today on devices without those apps.
2. **`LaunchRow`**: pass a family list per row:
   - TikTok → `SocialBlockingPrefs.TIKTOK_PACKAGES` (5 pkgs), Instagram → `INSTAGRAM_PACKAGES`
     (2 pkgs), X → `com.twitter.android`, Reddit → `com.reddit.frontpage`, Twitch →
     `tv.twitch.android.app`.
   - Keep the pastel bg/fg of each row as the *frame*; the real icon renders at ~34dp inside the
     42dp rounded box (same framing as ScheduleSheets: 28dp icon in 40dp box).
   - Extra (user-added) rows: real icon + label resolved from `allApps` (already looked up for
     label — reuse it; drop the `substringAfterLast` fallback to only when the app vanished).
3. **`TabRow`**: map each feature row to its package(s): YouTube → `com.google.android.youtube`,
   Facebook → `com.facebook.katana` (fallback `com.facebook.lite`), Snapchat →
   `com.snapchat.android`. Real icon when installed, current pastel letter otherwise.
4. **`SocialPickerSheet` rows**: replace letter with `AppIconImage(listOf(app.packageName))`
   (always installed by definition — comes from `AppCatalog.load`), letter as safety fallback.
5. No ViewModel/prefs/DataStore changes. Pure rendering layer.

### 1.3 Acceptance
- TikTok/Instagram/X/Reddit/Twitch rows show the **installed app's real icon**; uninstalled apps
  fall back to today's pastel letter (no empty boxes).
- Tab rows show YouTube/Facebook/Snapchat real icons when installed.
- Picker sheet shows real icons for every app; fast scroll stays smooth (LruCache).
- Dark/light themes unchanged (icons are app-provided bitmaps, frame colors untouched).

---

## 2. Issue #2 — In-App Tab Blocking blocks the ENTIRE app

### 2.1 Root cause (verified, two independent layers)

**Layer A — detection false-positive.** `SocialBlockingGate.findTabNode()` (Gate L70–100) blocks
when a node whose text/contentDescription matches `\bshorts\b` / `\breels\b` / `\bspotlight\b`
**exists anywhere in the window tree**. Those labels are **always present** — they are the
bottom-navigation tab captions on YouTube/Facebook/Snapchat home screens. So the gate fires as
soon as the app's first tree renders (window-state event or first 250 ms-throttled content
event), *even while the user is on Home/Feed/Chat* → "instead of blocking the tab on first
launch, the whole app becomes blocked after a few seconds". There is **no check that the tab is
actually selected/active**.

**Layer B — enforcement is full-screen.** `launchSocialTabGate()` (service L2134–2141) calls the
same `BlockOverlayController.show(…, "socialTab")` used by whole-app blocks: a `MATCH_PARENT ×
MATCH_PARENT` `TYPE_ACCESSIBILITY_OVERLAY` window whose Close launches **HOME**
(`dismiss()` → `launchHome()`). Even with perfect detection it would cover the whole app and
eject the user — the opposite of the product spec ("Keep useful functions… Feed/Messages/
Profile stay active"; Gate KDoc even claims "overlay that node (keep scroll/FAB)" but no such
scoping exists in code).

### 2.2 Fix design

**A. Correct detection — "is the blocked tab ACTIVE?"**
New `SocialBlockingGate.TabHit` + `isTabActive(nodes, vertical)` built on a **pure core** so it
is unit-testable without Robolectric (walk the a11y tree into a small `NodeView(text, desc,
viewId, selected, checked, bounds, depth, parentId)` list — bounded `MAX_DEPTH 12` /
`MAX_STRINGS 200`, same discipline as today — then run pure matching):

1. **L1 — selected bottom-nav tab (primary):** a nav item matching the vertical's label whose
   `isSelected == true` (or `isChecked`, or contentDescription containing "selected"). This is
   the state YouTube/Facebook/Snapchat bottom-nav items expose. Presence without selection →
   **no block** (fixes the launch false-positive).
2. **L2 — fullscreen feed (secondary):** when there is no visible nav (deep link straight into
   Shorts / Spotlight / a fullscreen Reels viewer): require *strong* signals — a node whose
   `viewIdResourceName` contains a characteristic token (`shorts`, `reel`, `spotlight`) in a
   large-bounds container, or label match with a dominant vertical-paging sibling structure.
   Narrow allow-list of id tokens per vertical; anything ambiguous → fail open (no block).
3. Return `TabHit(navBarBounds: Rect?)` — union of the bottom-nav item bounds (walk up from the
   matched tab node to its multi-child nav container) — used for overlay anchoring. Fullscreen
   feed → `navBarBounds = null` → cover full screen.
4. TikTok/Instagram stay excluded (unchanged allow-list).

**B. Scoped enforcement — cover only the tab's content area**
1. `BlockOverlayController.showTabGate(context, pkg, label, contentRect)`:
   - New window **sized/positioned to `contentRect`** (= screen minus bottom-nav bar; `lp.width/
     height/x/y`, gravity `TOP|START`), `TYPE_ACCESSIBILITY_OVERLAY`, `FLAG_NOT_FOCUSABLE |
     FLAG_NOT_TOUCH_MODAL | FLAG_LAYOUT_IN_SCREEN` — same stability contract as the full gate
     (a11y token, service WindowManager, wake re-assert).
   - Because the window does not span the nav bar, **bottom-nav taps reach the app naturally** —
     the user can switch to Feed/Messages/Profile.
   - Hosts a new compact `TabBlockOverlay` composable (brand card: shield, "YouTube Shorts is
     blocked", why chip). **No HOME launch, no dwell countdown** — dismissal is automatic.
   - `fullscreen = true` when `contentRect == null` (L2 case) → current full-screen cover is
     correct there (the whole screen IS the blocked vertical) — but still no HOME ejection;
     auto-dismiss instead.
2. **Auto-dismiss watch (the key behavioral change):** while the tab overlay is up, the service
   re-probes on the existing 250 ms watchdog cadence:
   - blocked tab no longer active (user switched tabs) or foreground pkg changed → **remove
     overlay silently** and clear that vertical's cooldown entry (re-entry blocks instantly);
   - tab still active but window missing (OEM hid it) → re-add;
   - bounds changed (rotation, nav resize) → `updateViewLayout` to the new `contentRect`.
3. **Cooldown semantics fix:** `socialTabCooldown` (4 s) stays as *re-check* dedupe only; it is
   cleared for the vertical whenever the overlay auto-dismisses, so re-entering the tab never
   gets a 4 s free pass (mirrors `rearmCooldownsIfGateDismissed` for the full gate).

**C. Service integration (both call sites)**
- Window-state path (service L688–712): replace `findTabNode` with `isTabActive`; gate only on
  an active tab — which also correctly covers the case "app restored directly onto the Shorts
  tab" (gate immediately, scoped).
- Content path `handleSocialTabContentEvent` (L2150–2181): same replacement; `isClick` still
  bypasses the 250 ms throttle so tapping the Shorts tab blocks within one event.
- `handleEvent`'s early `if (BlockOverlayController.isShowing()) return` guard must NOT swallow
  tab-overlay supervision: `isShowing()` will distinguish full gate vs tab gate
  (`isShowingTabGate()`), and the tab watch runs from the watchdog loop, not from `handleEvent`.

### 2.3 Acceptance
- Launch YouTube (blocking Shorts ON) → **home/feed is fully usable**; nothing covers the app.
- Tap **Shorts** tab → content area covered within ~250 ms (one event round-trip), bottom nav
  visible and tappable; tap **Home** → cover disappears instantly, feed usable.
- Re-enter Shorts → blocked instantly (no 4 s gap).
- Deep link / restore directly into Shorts or Spotlight → fullscreen cover (still no HOME
  eject); swipe/nav away → cover auto-clears.
- Facebook Reels + Snapchat Spotlight behave identically; disabling a vertical frees that app
  immediately; whole-app gate untouched.

---

## 3. Issue #3 — Blocked apps are not blocked instantly (seconds delay)

### 3.1 Root cause (verified)
The whole-app social gate is evaluated **only** inside the `TYPE_WINDOW_STATE_CHANGED` branch of
`handleEvent` (service L671–687). This codebase itself documents — in at least six comments
(watchdog KDoc, `puWatchdogTick`, `schedulePostDismissalReprobes`, `onGateDismissed`, …) — that
**OEMs like Vivo/FuntouchOS drop or delay window-state events on task resume**, which is exactly
why the PU gate got a 250 ms watchdog + reprobe chains. The social gate has **no watchdog and no
content-event backstop** (`handleSocialTabContentEvent` early-returns for every non-tab package
at L2157), so when the window-state event is late/dropped the whole-app block waits for the
*next* window-state event — typically the app's splash→main-activity transition, i.e. "blocked
after a few seconds".

Secondary latency contributors:
- `BlockOverlayController.show()` (L193–197) awaits a **DataStore read**
  (`blockScreenPrefs().first()`) on the critical path before posting the window attach — cold
  file I/O adds to the gate latency on every first show after process start.
- The 4 s `COOLDOWN_MS` dedupe (L680) relies on the dismissal re-arm reaching the service; if
  the overlay is removed without `onGateDismissed()` (system teardown, service rebind race), a
  re-launch inside 4 s is silently suppressed.

### 3.2 Fix design
1. **Social watchdog (backstop, mirrors the proven PU pattern):** new lightweight
   `socialWatchdogTick()` on its own `serviceScope` job at the existing 250 ms cadence, active
   only while `cachedSocialState?.enabled == true`:
   - resolve foreground identity with ONE cheap `rootInActiveWindow` pkg read (fallback to
     `lastForegroundPkg`);
   - foreground pkg ∈ `wholeBlocked` (and not `SYSTEM_EXEMPT`, not own UI) → raise the whole-app
     gate (deduped by `BlockOverlayController.isShowing()` + existing 4 s key cooldown);
   - foreground pkg ∈ FEATURE_PACKAGES with an active blocked tab → raise/keep the tab overlay
     (shares the Issue-#2 auto-dismiss watch loop);
   - skip while our own gate window or SafeMe UI is foreground. Worst-case exposure with this
     backstop ≈ 250 ms even when the OEM drops every window-state event.
2. **Content-event backstop for whole-app blocks:** in the non-window-state branch of
   `handleEvent` (next to `handleSocialTabContentEvent`), add a cheap identity check —
   `snapshot.pkg ∈ wholeBlocked` → gate. No tree walk needed (pkg comes free with the event),
   so event-flooding apps get gated on their first content event too.
3. **Take DataStore off the gate critical path:** cache `BlockScreenPrefsState` in a `@Volatile`
   (collected flow in `onServiceConnected`, same pattern as `cachedSocialState`);
   `BlockOverlayController.show()` uses the cached value (defaults when cache not yet warm) and
   never suspends before `attachOverlay`.
4. **Cooldown hygiene:** when the watchdog observes that no overlay is showing while a whole
   gate cooldown key is set and the blocked app is foreground, treat the stale key as expired
   (re-gate). This removes the "system removed the overlay → 4 s exposure" hole.
5. Event path stays the primary (it is the ~10–50 ms fast lane); watchdog/content paths only
   fire when the event path missed. All new paths fail-open with try/catch, matching service
   discipline.

### 3.3 Acceptance
- Launch a whole-blocked app from launcher (cold + hot start): block cover presents while the
  app is still starting — visually **instant** (≤ ~300 ms worst case on event-dropping OEMs,
  ~50 ms on stock).
- Re-launch right after closing a gate: blocked instantly (cooldown re-arm verified; stale-key
  recovery works even when dismissal signal was lost).
- No battery/CPU regression: watchdog tick is one pkg read; tree walks only in FEATURE_PACKAGES
  apps at the existing 250 ms/4 s throttles.

---

## 4. Execution order (single engineer, serial)

| Phase | Work | Files | Depends |
|---|---|---|---|
| **0** | Branch `agent/social-blocking-fixes`; toolchain bootstrap (`bash tools/sandbox/bootstrap.sh`) | — | — |
| **1** | Issue #1: `AppIconImage` component + wire into `LaunchRow`/`TabRow`/picker/extras | `ui/components/AppIconImage.kt` (new), `SocialBlockingScreen.kt` | 0 |
| **2** | Issue #2a: pure `TabHit`/`isTabActive` core + tree flattener | `protect/SocialBlockingGate.kt` | 0 |
| **3** | Issue #2b: `showTabGate` + compact `TabBlockOverlay` + tab/full mode split + auto-dismiss API | `BlockOverlayController.kt`, `ui/screens/blockscreen/TabBlockOverlay.kt` (new) | 2 |
| **4** | Issue #2c+#3: service wiring — window/content paths use `isTabActive`; whole-app content backstop; `socialWatchdogTick`; cached block-screen prefs; cooldown hygiene | `SafeMeAccessibilityService.kt`, `BlockOverlayController.kt` | 2,3 |
| **5** | Tests: `SocialBlockingGateTest` (pure core), throttle/cooldown tests, `BlockOverlayControllerTest` additions (rect math, mode split) | `app/src/test/**` | 2,3,4 |
| **6** | Verify: `:app:testDebugUnitTest` + `:app:lintDebug` + `:app:assembleDebug`; regression pass on PU/schedule/keyword gates (existing suites green) | — | 5 |

Deliberately NOT changed: whole-app gate semantics (persistent cover + HOME on Close), PU /
schedule / keyword / title / URL engines, DataStore schema, BackupCodec, prototype HTML.

## 5. Regression matrix

| Existing behavior | Risk | Guard |
|---|---|---|
| Whole-app block persists until Close→HOME | unchanged code path; only extra delivery paths added | existing dismissal re-arm + new stale-key recovery; `BlockOverlayControllerTest` |
| PU gate over Settings / a11y pages | watchdog is a **separate job**, gated on social flag; PU tick untouched | PU suites + manual Settings pass |
| Keyword/title/URL engine ordering | social block still evaluated before `evaluateContentEngine`; no new early returns on the content path | `SafeMeAccessibilityService*Test` |
| Schedule launch gates | `isScheduleBlocked` still evaluated before social block (unchanged order) | schedule suite |
| Block screen dwell/custom message | cache fallback = current defaults; prefs flow keeps it live | existing controller test + manual |
| Screen visuals when apps not installed | letter fallback preserved verbatim | visual check both themes |

## 6. What cannot be verified in this sandbox
Overlay timing and OEM event-drop behavior need a **physical device** — unit tests cover the pure
decision core; the plan ships with a manual test checklist (per acceptance sections above) to run
on the test phone (YouTube/Facebook/Snapchat installed, Shorts/Reels/Spotlight toggled).
