# Social Media Blocking — FINAL Fix Plan (Deep Reanalysis)

> **Date:** 2026-09-16 · **Branch target:** `agent/social-blocking-fixes` (from `main` @ `faf4ede`)
> **Scope:** Social Media Blocking only — Launch Block (whole-app) + In-App Tab Block. PU / schedule / keyword / title / URL engines are **not** touched.
> **Status:** PLAN ONLY — awaiting `Execute`.
> Supersedes `SOCIAL_MEDIA_BLOCKING_FIXES_PLAN.md` (first pass). Every claim below was re-verified against the tree in this pass; deltas vs the first plan are marked **[NEW]**.

---

## 0. Reanalysis evidence base

| File (lines) | What was re-verified |
|---|---|
| `SocialBlockingScreen.kt` (615) | letter-only icon rendering at **L378 / L463 / L592**; extras-row fallback L344–352 |
| `SocialBlockingGate.kt` (120) | `findTabNode` = label-presence search (L70–100); no selected-state check anywhere |
| `SafeMeAccessibilityService.kt` (2440) | whole gate window-state-only (L671–687); tab probe content path (L2150–2181, early-returns for non-tab pkgs at L2157); `isShowing()` early-returns at **L555 (handleEvent)** and **L1379 (puWatchdogTick)**; PU watchdog 250 ms pattern (L~1950); OEM event-drop documented in ≥6 KDoc comments |
| `BlockOverlayController.kt` (559) | `show()` awaits DataStore on critical path (L195); full-screen MATCH_PARENT window; `dismiss()` → HOME; singleton `showing` flag; `reassertIfShowing` |
| `ScheduleSheets.kt` (L397–410) | proven `getApplicationIcon` pattern — **but casts to `BitmapDrawable`**, which fails for `AdaptiveIconDrawable` (most modern apps) and falls into a 64 px canvas-draw path **[NEW finding]** |
| `GroupedAppPicker.kt` (L36–43) | row-lambda signature `(app, checked, onToggle)` — icon swap is local to the social sheet |
| `ActivityLog.kt` (L64–73) | pure append-with-**dedupe-and-cap** — confirms tab-cover bookkeeping policy **[NEW]** |
| `gradle/libs.versions.toml` L16 / `app/build.gradle.kts` L75 | `androidx.core-ktx` present → `Drawable.toBitmap()` available **[NEW]** |
| `tools/sandbox/bootstrap.sh` | idempotent JDK 25 + SDK 36 provisioning — verification toolchain |
| `accessibility_service_config.xml` | `notificationTimeout=100`; window-state + content + click + focus events subscribed |

---

## 1. Issue #1 — Real app icons on cards (highest priority)

### 1.1 Root cause (re-verified)
- `LaunchRow` L378, `TabRow` L463, picker row L592: `Text(label.take(1).uppercase())` — real icons are never loaded anywhere on this screen.
- Extras rows: label falls back to `pkg.substringAfterLast(".")`, generic grey pastels.
- Existing `ScheduleSheets` pattern works but **(a)** mis-handles adaptive icons via `as? BitmapDrawable` (they render through the crude 64 px fallback) and **(b)** re-decodes on every recomposition — do not copy it verbatim. **[NEW]**

### 1.2 Final design
**New `ui/components/AppIconImage.kt`** (shared, cached, adaptive-safe):
- Signature: `AppIconImage(packageNames: List<String>, sizePx: Int, modifier, fallback: @Composable () -> Unit)`.
- Load: `produceState(key = packageNames)` + `Dispatchers.IO`; try each package in order, use the **first installed** one (family support). Decode with `androidx.core.graphics.drawable.toBitmap(sizePx, sizePx)` — handles `AdaptiveIconDrawable`, `BitmapDrawable`, and vector legacy drawables uniformly. **[NEW — fixes the ScheduleSheets flaw]**
- Process-wide `LruCache<String, ImageBitmap>` (~64 entries @ 96 px ≈ 2.3 MB) so picker scrolling/recomposition never re-decodes. Negative results cached too (short-circuits uninstalled pkgs).
- Failure/uninstalled → `fallback` slot renders **the exact existing pastel-letter box** — pixel-identical to today when nothing is installed.
- `ScheduleSheets` deliberately NOT refactored onto it (no-regression discipline); optional follow-up. **[NEW scope decision]**

**Wiring:**
| Row | Packages tried (first installed wins) | Fallback |
|---|---|---|
| TikTok row | `SocialBlockingPrefs.TIKTOK_PACKAGES` (5 pkgs) | orange pastel “T” (current) |
| Instagram row | `SocialBlockingPrefs.INSTAGRAM_PACKAGES` (2 pkgs) | pink pastel “I” (current) |
| X / Twitter row | `com.twitter.android` | grey pastel “X” |
| Reddit row | `com.reddit.frontpage` | red pastel “R” |
| Twitch row | `tv.twitch.android.app` | purple pastel “T” |
| Extras rows | the stored pkg (icon works even while `allApps` still loads) | grey pastel letter |
| YouTube Shorts tab row | `com.google.android.youtube` | red pastel “Y” |
| Facebook Reels tab row | `com.facebook.katana`, then `com.facebook.lite` | blue pastel “F” |
| Snapchat Spotlight tab row | `com.snapchat.android` | yellow pastel “S” |
| Picker sheet rows | `app.packageName` (always installed) | letter |

Icon renders ~30 dp inside the existing 42 dp rounded frame (40 dp frame / 28 dp icon in the picker, matching ScheduleSheets proportions). Frame colors, layout, toggles, dimming: untouched.

**Explicit non-goals (regression guards):** toggling a row still toggles its single stored pkg (family-wide toggling would change gate behavior — separate product decision); `InstalledApp`/`AppCatalog` data class unchanged; no prefs/schema change.

### 1.3 Acceptance
Installed apps show real launcher icons in all three lists; uninstalled apps keep today's pastel letter; picker scroll stays smooth; light/dark unchanged.

---

## 2. Issue #2 — In-App Tab Block blocks the ENTIRE app

### 2.1 Root cause (re-verified, two layers)
**A. Detection false-positive.** `findTabNode` matches `\bshorts\b`/`\breels\b`/`\bspotlight\b` **anywhere** in the tree. Those strings are the bottom-nav captions — always present — *and* can appear in home-feed content (e.g. video titles containing “shorts”). Result: gate fires on the first tree-ready probe (window-state event or first 250 ms content probe) → “whole app blocked a few seconds after launch, not the tab”. **[NEW: home-feed text matches are a second false-positive vector the selected-state fix also closes]**

**B. Enforcement is full-screen.** `launchSocialTabGate` (L2134) → same `BlockOverlayController.show(…, "socialTab")` as whole-app: MATCH_PARENT cover, Close → HOME. The KDoc's claim “overlay that node (keep scroll/FAB)” was never implemented.

### 2.2 Final design — detection: “is the blocked tab ACTIVE?”
Rewrite `SocialBlockingGate` around a **pure core** (unit-testable, no Robolectric):
1. **Tree flattener** (in-service, bounded as today: `MAX_DEPTH 12`, `MAX_STRINGS 200`, fail-open): `AccessibilityNodeInfo` tree → `List<NodeView(id, parentId, text, desc, viewId, selected, checked, bounds, depth)>` capturing `isSelected`/`isChecked`/`getBoundsInScreen`/`viewIdResourceName`.
2. **`findBottomNav(nodes, screen)`** — pure: a node with `bounds.top ≥ 80%·screenH`, `width ≥ 90%·screenW`, `childCount ≥ 3`, and ≥2 children carrying non-empty text/desc. (Bottom-nav containers satisfy this on YouTube/FB/Snapchat; fail-open when none found.)
3. **`isTabActive(nodes, vertical)` → `TabHit(navBounds: Rect?) | null`** — pure, ordered:
   - **Nav visible:** take the nav item with `selected || checked == true` (selection lives on the item container; labels on child TextViews — so walk **up ≤3 ancestors** from any label-matching node testing `selected/checked`, and symmetrically read the container's descendant labels). Selected label matches the vertical → `TabHit(navBounds)`. Selected label is anything else (Home/Feed/Chat/…) → **NOT active — return null even if the vertical's label or `reel`-tokened nodes exist elsewhere** (this single rule kills both the launch false-positive and the home-feed-reel-preview false-positive). **[NEW guard]**
   - **Nav hidden (fullscreen player / deep link / Spotlight):** fire only on a **strong token**: a node whose `viewIdResourceName`/`className` matches the per-vertical token list (YouTube: `shorts|reel` — Shorts' internal codename is literally “reel”; Facebook: `reel`; Snapchat: `spotlight`) **and** whose bounds cover ≥65% of screen area → `TabHit(null)` (fullscreen cover). Anything ambiguous → null (fail open; missing a rare deep-link case is strictly better than the current everything-blocked behavior).
4. TikTok/Instagram remain absent from `FEATURE_PACKAGES` (unchanged).
5. Legacy `findTabNode` deleted; only 2 call sites exist (service L699, L2173 — verified by grep).

### 2.3 Final design — enforcement: scoped, self-dismissing tab cover
**`BlockOverlayController` mode split [NEW structure]:**
- `showFullGate(...)` — today's `show()`, unchanged semantics (persistent, Close → HOME, activity fallback, bookkeeping).
- `showTabGate(context, pkg, label, contentRect: Rect?)`:
  - Window **positioned/sized to `contentRect`** (= full screen minus the bottom-nav bar; `lp.width/height/x/y`, gravity `TOP|START`) → the nav bar stays visible **and tappable** (the window simply does not span it — touches reach the app naturally). Same stability contract: `TYPE_ACCESSIBILITY_OVERLAY`, service WindowManager + a11y token, `FLAG_NOT_FOCUSABLE|FLAG_NOT_TOUCH_MODAL|FLAG_LAYOUT_IN_SCREEN`, wake re-assert, lifecycle-owner recipe.
  - `contentRect == null` (L2 fullscreen case) → full-screen cover, **but still no HOME eject** (auto-dismiss only).
  - Hosts a new compact `TabBlockOverlay` composable (brand card: shield, “YouTube Shorts is blocked”, why chip, “Switch tabs to continue” hint). No dwell countdown, no Close-to-HOME.
  - **No `BlockGateActivity` fallback for tab gates** — an activity would eject to HOME and violate the contract; failures are retried by the watchdog instead. **[NEW decision]**
  - **Bookkeeping:** tab covers do **not** increment `blockedToday` and do **not** add activity-feed entries (they're a soft, self-dismissing cover, and repeated tab entries would inflate counters; `ActivityLog`'s dedupe/cap confirmed at L64–73). Whole-app bookkeeping unchanged. **[NEW decision]**
- `isShowing()` splits into `isShowingFullGate()` / `isShowingTabGate()`:
  - `handleEvent` L555 and `puWatchdogTick` L1379 use `isShowingFullGate()` — **the tab cover must not suspend event processing or the PU watchdog** (keyword gates keep working under a tab cover; the page below is still evaluated). **[NEW — verified both call sites]**
  - Tab-cover auto-dismiss does **not** raise `gateDismissedPending`/HOME (no PU/keyword cooldown churn); it clears only that vertical's `socialTabCooldown` entry so re-entry blocks instantly.

**Service integration:**
- Window-state path (L688–712) and `handleSocialTabContentEvent` (L2150–2181): replace `findTabNode` with `isTabActive` (via the flattener). `isClick` still bypasses the 250 ms probe throttle → tapping the Shorts tab gates within one event round-trip.
- **Auto-dismiss supervision** runs inside the new social watchdog tick (see §3): every 250 ms while a tab cover is up — re-evaluate `isTabActive`; user switched tabs / app changed → remove cover silently + clear the vertical's cooldown; still active but window gone (OEM hid it) → re-add; nav bounds changed (rotation) → `updateViewLayout`. **[NEW — supervision moved entirely to the watchdog so `handleEvent`'s `isShowing` guard can't starve it]**
- Prefs flips while the app is open (e.g. user enables Shorts blocking from Settings while YouTube sits on Shorts) are picked up by the same watchdog tick — no extra wiring. **[NEW]**

### 2.4 Acceptance
- Open YouTube with Shorts blocking ON → **home/feed fully usable, nothing covered, ever** (launch false-positive gone).
- Tap Shorts → content area covered ≤ ~250 ms; bottom nav visible/tappable; tap Home → cover vanishes instantly; re-enter Shorts → blocked instantly (no 4 s gap).
- Feed containing a video titled “…shorts…” → **no block** (selected-state guard). **[NEW case]**
- Restore/deep-link directly into fullscreen Shorts or Spotlight → full-screen cover, no HOME eject; navigate away → auto-clear.
- Rotation while covered → cover re-fits. FB Reels + Snapchat Spotlight identical. Vertical toggles off → freed immediately.
- Whole-app gate behavior identical to today.

---

## 3. Issue #3 — Whole-app block is not instant (seconds delay)

### 3.1 Root cause (re-verified)
1. **Primary:** the whole-app gate exists **only** in the `TYPE_WINDOW_STATE_CHANGED` branch (L671–687). This codebase documents in ≥6 comments that OEMs (Vivo/FuntouchOS) **drop or delay window-state events** — the PU gate got a 250 ms watchdog + reprobe chains for exactly this; the social gate has **neither watchdog nor content-event backstop** (`handleSocialTabContentEvent` returns at L2157 for every non-tab pkg). A dropped/delayed event ⇒ block waits for the next window transition (splash→main ≈ seconds).
2. `BlockOverlayController.show()` suspends on a **DataStore read** (L195 `blockScreenPrefs().first()`) before the window attach — cold file I/O on the gate's critical path.
3. The 4 s `COOLDOWN_MS` dedupe (L680) relies on the dismissal re-arm reaching the service; if the cover dies without `onGateDismissed()` (system teardown / rebind race), a re-launch inside 4 s is silently ungated.
4. **[NEW]** Serial `eventScope` (`limitedParallelism(1)`) means the window-state event queues behind any in-flight event storm from the previous app — small, but the fast lane below removes it entirely.

### 3.2 Final design — three independent delivery paths + hygiene
**A. Main-thread fast lane (new primary, truly instant).** In `onAccessibilityEvent`, before enqueuing to `eventScope`: for `TYPE_WINDOW_STATE_CHANGED` only, do an O(1) check — `cachedSocialState?.enabled == true` && `pkg ∉ {null, own, SYSTEM_EXEMPT}` && `pkg ∈ wholeBlocked` && `!BlockOverlayController.isShowingFullGate()` → `BlockOverlayController.show(...)` directly (`show()` sets `showing=true` synchronously and is thread-safe; `cachedSocialState` is `@Volatile`). No binder calls, no tree walks, no queue. Dedupe: `showing` flag + the existing `socialWhole|$pkg` 4 s key (fields already `@Volatile`; worst-case double-call is deduped by `showing`). SYSTEM_EXEMPT check keeps Settings/installer/systemui safe even if user-added. **[NEW]**

**B. Social watchdog (250 ms backstop, mirrors the proven PU pattern).** New `socialWatchdogTick()` on its own `serviceScope` job:
- Instant-return unless `cachedSocialState?.enabled == true` and (wholeBlocked non-empty or any vertical on) — zero cost when the feature is off/empty. **[NEW micro-guard]**
- Skip while `isShowingFullGate()` or SafeMe UI foreground; skip gating within `POST_DISMISSAL_EVICT_WINDOW_MS` (1.5 s) of any gate dismissal so the post-Close HOME transition can't re-trigger the cover. **[NEW race guard]**
- Resolve fg identity with one cheap `rootInActiveWindow` pkg read (fallback `lastForegroundPkg`, same as PU tick):
  - fg ∈ wholeBlocked → raise full gate;
  - fg ∈ FEATURE_PACKAGES → run the tab supervision from §2.3 (keep/dismiss/resize tab cover, raise it if the tab is active and no cover);
  - **stale-cooldown recovery:** fg ∈ wholeBlocked, no cover showing, and the 4 s key is still set but no dismissal signal arrived → treat the key as expired and gate. **[NEW]**
- Worst-case exposure with dropped events: ≤ ~250 ms (same math the codebase already relies on for PU).

**C. Content-event backstop.** In `handleEvent`'s non-window-state branch, next to `handleSocialTabContentEvent`: `snapshot.pkg ∈ wholeBlocked` (set lookup only — no tree walk) → full gate. Event-flooding apps get gated on their first content event even if every window-state event was dropped. **[NEW]**

**D. DataStore off the critical path.** Cache `BlockScreenPrefsState` in a `@Volatile` (warm via `runBlocking` first-read in `onServiceConnected`, kept live by a collect in `serviceScope` — identical pattern to `cachedSocialState`); `show()`/`showTabGate()` consume the cache (defaults if not yet warm) and never suspend before `attachOverlay`. **[NEW]**

Ordering preserved: PU → schedule → social (whole → tab) → content engine. Fast lane respects `SYSTEM_EXEMPT`; nothing else in `handleEvent` moves.

### 3.3 Acceptance
- Cold + hot launch of a whole-blocked app: cover presents while the app is still starting — ≤ ~300 ms worst case on event-dropping OEMs, ~10–50 ms on stock (fast lane hits before the app's first frame settles).
- Re-launch immediately after Close → HOME: gated instantly (re-arm intact; stale-key recovery covers lost dismissal signals).
- Toggling an app into the block list while it's open: gated within 250 ms (watchdog).
- CPU: watchdog = 1 pkg read / 250 ms only while the feature is on; tree walks only inside FEATURE_PACKAGES at existing throttles; no measurable battery delta.

---

## 4. Execution plan (ordered, file-level)

| Phase | Work | Files (NEW/EDIT) | Depends |
|---|---|---|---|
| **0** | Branch `agent/social-blocking-fixes`; toolchain via `tools/sandbox/bootstrap.sh`; baseline `:app:testDebugUnitTest` green before edits — **✅ ALREADY VERIFIED in reanalysis: bootstrap OK (JDK 25.0.4 + SDK 36), `BUILD SUCCESSFUL in 2m49s`, 317 tests / 0 failures / 0 skipped on `main` @ `faf4ede`** | — | — |
| **1** | Issue #1: `AppIconImage` (LruCache + `toBitmap`) + wire into `LaunchRow`/`TabRow`/extras/picker rows | NEW `ui/components/AppIconImage.kt`; EDIT `SocialBlockingScreen.kt` | 0 |
| **2** | Issue #2a: pure core — `NodeView` flattener + `findBottomNav` + `isTabActive`/`TabHit`; delete `findTabNode` | EDIT `protect/SocialBlockingGate.kt` | 0 |
| **3** | Issue #2b: `showFullGate`/`showTabGate` split, `isShowingFullGate`/`isShowingTabGate`, cached block-screen prefs, tab bookkeeping policy; compact `TabBlockOverlay` | EDIT `BlockOverlayController.kt`; NEW `ui/screens/blockscreen/TabBlockOverlay.kt` | 2 |
| **4** | Issue #2c + #3: service — fast lane, `socialWatchdogTick` (backstop + tab supervision + stale-key recovery + post-dismissal guard), content-event backstop, both social call sites on `isTabActive`, `isShowingFullGate()` at L555/L1379 | EDIT `SafeMeAccessibilityService.kt` | 2, 3 |
| **5** | Tests: `SocialBlockingGateTest` (nav/selected/fullscreen-token/no-nav/home-feed-guard cases), cooldown/stale-key unit tests, `BlockOverlayControllerTest` additions (mode split, rect math), smoke that existing suites stay green | NEW/EDIT `app/src/test/**` | 2–4 |
| **6** | Verify: `:app:testDebugUnitTest` + `:app:lintDebug` + `:app:assembleDebug`; manual device checklist (below) | — | 5 |

### Manual device checklist (cannot be automated in sandbox)
1. YouTube: launch → free; Shorts tab → scoped cover ≤250 ms, nav tappable; Home → instant dismiss; re-enter → instant cover; feed titled “shorts” → no cover; rotate under cover → refits.
2. Facebook Reels + Snapchat Spotlight: same matrix (incl. fullscreen Spotlight).
3. Whole-app: TikTok cold/hot launch → instant cover (stopwatch ≤300 ms); Close→HOME→re-launch → instant; toggle app into list while open → ≤250 ms.
4. Regressions: keyword block in browser still fires; PU Settings gate unaffected while a tab cover is up; schedule launch gate unaffected; `blockedToday` counts only full gates.

## 5. Regression matrix

| Existing behavior | Change risk | Guard |
|---|---|---|
| Whole-app gate: persistent cover, Close→HOME, 4 s dedupe, dismissal re-arm | delivery paths added; semantics unchanged | existing dismissal re-arm + `BlockOverlayControllerTest` + manual #3 |
| PU gate (Settings/a11y pages) | watchdog is a separate job; L1379 keeps full-gate semantics | PU suites + manual #4 |
| Keyword/title/URL engine order & exclusions | social still before `evaluateContentEngine`; tab cover no longer suspends evaluation (strictly more protection, same order) | `SafeMeAccessibilityService*Test` suites |
| Schedule launch gates | order unchanged (schedule evaluated before social) | schedule suite |
| Block screen dwell/message customization | cache fallback = current defaults; flow keeps it live | controller test + manual |
| Cards when target apps not installed | pastel-letter fallback preserved verbatim | visual check both themes |
| Backup/restore, DataStore schema | untouched (no key/shape changes) | `BackupManagerTest` |
| Activity feed & blockedToday | tab covers excluded by design (full gates unchanged) | `ActivityLog` dedupe/cap confirmed; manual #4 |

## 6. Known limitations (documented, fail-open by design)
- L2 fullscreen detection is token-heuristic; a future app update renaming internal view ids could let a deep-linked fullscreen Shorts/Reels session through until the nav reappears. Direction of failure is “under-block”, never “block the whole app”.
- `isSelected` semantics on OEM-modified Facebook/Snapchat builds could hide selection state; L1 then fails open (no tab block) rather than false-positive.
- Fast lane/watchdog assume the a11y service is connected (unchanged precondition for every gate in this app).
