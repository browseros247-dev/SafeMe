# Social Media Blocking — FINAL Plan (V5, deep reanalysis of device-test feedback)

> **Date:** 2026-09-16 · **Branch:** `agent/social-blocking-fixes` (local; **no push/merge without explicit command**)
> **Mode:** PLAN ONLY — awaiting `Execute`. Supersedes `…_V4_PLAN.md`; V4's core fixes stand, this pass hardens them with evidence and closes two design gaps V4 missed.
> **Formula:** minimal code, production-grade certainty, scalability seams already in place. **~200 production lines + ~60 test lines, 5 files edited, 0 new files.** Suite baseline: 339/0.

---

## 1. Reanalysis findings — what was verified and what was RULED OUT

**Ruled out as causes of “launch block not working at all” (with evidence):**
- **v3 code regression:** the whole-gate window-state path is **byte-identical to pre-v3** (re-read L722+; v3 only *added* delivery layers around it). Order PU → schedule → social intact.
- **Stuck-overlay deadlock:** audited every `showing`-flag transition in `BlockOverlayController` (show/preempt/dismiss/dismissTabCover/removeOverlay/attach-failure) — no path leaves `showing=true` without a window; the one race found earlier is already fixed (`7f638a0`).
- **R8/minify stripping (first release build):** `proguard-rules.pro` is empty; prior releases ran the same engine under default R8 rules; the new code uses no reflection/serialization. No plausible mechanism.
- **Fresh-install defaults:** DataStore-absent → `DEFAULT_WHOLE_BLOCKED` (5 pkgs), `enabled=true` — verified in `SocialBlockingPrefs`.

**Remaining candidate causes for Issue 1 (all three live until measured):**
1. **Package-family mismatch — PROVABLE CODE BUG.** Gate is exact-match (`isWholeAppBlocked` L60–63) vs Lite/Go variants common on South-Asian devices (`musically.go`, `aweme.lite`, `instagram.lite`). v3's family-aware icons made the card *look* enabled while the gate matched nothing.
2. **Accessibility service disabled by the reinstall** (the key change forced uninstall/reinstall) — would suppress ALL gates, consistent with issues 1–3 simultaneously. Device state, not code.
3. **Master OFF / Relax-Balanced preset** (Balanced clears `wholeBlocked`) — device state.

Causes 2–3 are discriminated in seconds by the **existing** log line
`social: initial cachedSocial enabled=… whole=… tabs=…` and the Home shield's a11y layer —
baked into the verification protocol (§6). The code fix addresses cause 1 unconditionally.

**New design gaps found in the V4 plan itself (fixed in this plan):**
- **G1 — watch would kill valid L2b covers:** a cover raised by the new nav-click signal
  (L2b) is not confirmable by L1/L2a probes on trees lacking both signals; V4's watch
  dismissed on the first probe miss → a legitimate Spotlight cover would drop ~250 ms
  after appearing. Fix: `confirmed` flag + grace + nav-click dismissal shortcut (§3).
- **G2 — L2b false-positive vector:** clicking a *video titled* “…shorts…” would gate a
  normal video. Fix: L2b requires the click to be in the **bottom-nav region**
  (clicked-node centerY ≥ 80% of screen height) — needs one new snapshot field (§3).

---

## 2. Fix A — family-aware whole-app blocking (Issue 1, ~20 ln)

Unchanged from V4 (re-verified):
- `SocialBlockingPrefs.familyOf(pkg): Set<String>?` (expose existing family mapping;
  `displayKeyFor` currently private, L72).
- `isWholeAppBlocked`: exact match **OR** same-family sibling blocked; SYSTEM_EXEMPT still
  first. Fixes existing installs/backups with zero migration. Pure → unit-tested.
- Row `checked` = any family member blocked; `toggleLaunch(pkg, family)` stores/removes the
  full family list; picker/Select-All/extras keep exact-pkg semantics; `displayCount`
  already dedupes families.

## 3. Fix B — layered tab detection + watch semantics (Issues 2+3, ~75 ln)

**Detection stack** (all inside the existing BFS / event paths; each layer fail-open):

| Layer | Signal | Catches | Cost |
|---|---|---|---|
| **L1** | label match + selected/checked on node, ≤3 ancestors **or first-level children** (new child probe) | nav tabs that expose selection (YouTube, FB — expected) | +6 ln |
| **L2a** | `viewIdResourceName`/`className` contains `tokenHints` token AND bounds ≥65% screen area; **plus** window-state `snapshot.cls` token match | fullscreen players: Shorts-from-Home (`shorts|reel` — YouTube's internal codename for Shorts infra is literally “reel”), FB Reels (`reel`), Spotlight (`spotlight`) | +30 ln |
| **L2b** | TYPE_VIEW_CLICKED whose clicked-node bounds are in the bottom-nav region (centerY ≥ 80% screen H) and `clickedTexts` match the vertical label | navs that expose **no** selection state (the likely Snapchat case) — deterministic user-intent signal | +20 ln |

- L2a/L1 return `TabHit(navBarTop?)` → scoped cover above nav when nav identified, else
  fullscreen — existing plumbing, no controller changes.
- L2b gates directly from the event (no tree walk): new `clickedBounds` field in
  `EventSnapshot`, captured on the main thread where `event.source` is already read
  (`readClickedSourceTexts`, L392+). G1's false-positive control built in.
- Token data fills the reserved `TabRule.tokenHints` slot — adding/tuning an app stays a
  one-line data edit (scalability seam intact).

**Watch semantics (closes G1):**
- `launchSocialTabGate(…, confirmed: Boolean)` — L1/L2a raise **confirmed** covers:
  probe-miss dismisses immediately (today's snappy behavior preserved exactly).
- L2b raises **unconfirmed** covers: probe-miss dismissal suppressed during a **2 s grace**
  (tree stabilisation); dismissed instantly on (a) foreground app change, or (b) a
  bottom-nav-region click whose texts do **not** match the blocked vertical (user tapped
  another tab) — no waiting for probes.
- After grace, miss → dismiss (bounded worst case ≈2 s of cover on a zero-signal tree;
  the diagnostic log below then drives the one-round-trip token fix).

**Production diagnosability (+12 ln):** throttled (2 s) `Log.d` summary when a probe inside
a FEATURE_PACKAGE finds nothing: first ~12 nodes' text/desc-prefix/viewId-prefix/selected/
bounds% — one `adb logcat -s SafeMeA11y` capture on the device fully characterises any
residual miss; tokens are then tuned as data, not guesswork.

## 4. Fix C — hide uninstalled cards (Issue 4, ~20 ln)

Unchanged from V4 (re-verified: `DEFAULT_LAUNCH.forEach` L348 renders unconditionally):
- Default rows, extras rows, tab rows render only when any family package ∈ `allApps`
  (already-loaded `InstalledApp` list — no new discovery).
- Pills truthful: `displayLaunchCount` = blocked ∩ visible; `activeTabs` = enabled ∩
  installed host. DataStore/backup untouched; uninstalled defaults stay as inert set
  entries (gate-safe, restore-safe).

## 4b. Fix D — toggle reliability & feedback (“toggle enable/disable not working”, ~25 ln)

Deep re-analysis of the report found **three distinct causes**, all verified in code —
the switch composable, call-site wiring, and DataStore collection are intact, so the
report decomposes as:

**D1 — enforcement-side (primary, same root as Issue 1).** Row toggles store exact
packages (`com.zhiliaoapp.musically`) while the device runs a variant — the toggle flips
the stored set correctly but enforcement never matched that package, so blocking behavior
never changes → the toggle *looks* dead. **Fixed by Fix A** (family-aware gate + toggle);
no separate work.

**D2 — silent swallow when master is OFF (verified UX bug).** Screen-level guards
(`SocialBlockingScreen` L400 rows / L495 tabs: `onToggle = { if (enabled) onToggle() }`)
drop the tap **before** the ViewModel, so the VM's intended feedback toasts —
“Turn on Master first” (VM L133) / “Turn on Social Media Blocking first” — are unreachable.
The switch is only dimmed (`alpha 0.45`), still tappable, and gives **zero** response.
Fix: delete both screen-level guards (2-line change) so the VM’s explanatory toast fires;
dimming stays as the visual cue.

**D3 — stale-read race on rapid taps (verified).** Every toggle is read-modify-write
against `_uiState.value` + absolute setter (`toggleMaster` VM L110, `toggleLaunch` L131,
tab toggles): two taps inside the DataStore round-trip both read the same state, the
second write is a no-op → the switch appears to bounce back. Fix: move the flip **inside**
`socialBlockingDataStore.edit { }` (atomic, serialized by DataStore):
- `toggleSocialEnabled()` — flips `KEY_SOCIAL_ENABLED` in-transaction,
- `toggleSocialWholeBlocked(family)` — in-transaction set-delta (this IS Fix A's family
  toggle — one atomic function serves both),
- `toggleSocialVertical(key)` — flips the vertical flag in-transaction.
Pure delta core extracted (`toggleFamilyInSet(current, family)`) → unit-tested.
Pickers keep the absolute `setSocialWholeBlocked` (multi-select semantics, unchanged).

**Not a cause (ruled out by inspection):** `SocialSwitch` click wiring, `collectAsState`
plumbing, single-ViewModel scoping, DataStore instance sharing — all verified intact.

## 5. Regression guards (“must not influence other features”)

- `isWholeAppBlocked` callers are social-only (fast lane, window path, content backstop,
  watchdog, tab-watch handoff) — grep-verified; keyword/title/URL/schedule/PU never call it.
- L2a/L2b fire **only where today's code fires nothing** (no selected tab); every existing
  block keeps its exact trigger. L2b is click-scoped + nav-region-scoped.
- Confirmed-cover watch behavior is bit-identical to shipped v3; grace applies only to the
  new L2b covers.
- Row hiding is rendering-only; prefs schema, BackupCodec, gate inputs unchanged.
- Full suite (339) stays green; ~18 new pure tests: family matching (incl. exempt
  precedence + sibling direction), L2a decision table (area threshold, token, cls),
  L2b region/label matrix, pill math.

## 6. Execution order & verification

| # | Step | Files |
|---|---|---|
| 1 | Fix A (family) + Fix D3 (atomic toggles — same functions) | `SocialBlockingPrefs.kt`, `SocialBlockingGate.kt`, `SocialBlockingViewModel.kt`, `SocialBlockingScreen.kt` |
| 2 | Fix D2 (remove silent-swallow guards) | `SocialBlockingScreen.kt` |
| 3 | Fix B detection stack (L1 child probe, L2a+cls, L2b+clickedBounds, tokens) | `SocialBlockingGate.kt`, `SafeMeAccessibilityService.kt` |
| 4 | Fix B watch semantics (confirmed flag, grace, nav-click shortcut) + diagnostic log | `SafeMeAccessibilityService.kt` |
| 5 | Fix C (installed filter + pills) | `SocialBlockingScreen.kt`, `SocialBlockingViewModel.kt` |
| 6 | Tests + full gate: `:app:testDebugUnitTest` (≥339 green) + `:app:lintDebug` + `:app:assembleRelease` | `app/src/test/**` |
| 7 | Commit locally. **No push/merge without explicit command.** | — |

**Device verification protocol — with cause-discrimination for Issue 1:**
0. Settings → Accessibility → SafeMe **ON** (reinstall disables it); Social screen master
   **ON**, pill shows “N launch” > 0. *(If either was off, issues 1–3 were partly device
   state — retest before judging the fix.)*
1. `adb logcat -s SafeMeA11y` while launching a blocked app: expect
   `social fast lane: gating …` (or `social watchdog: gating …`) and the cover ≤300 ms.
   If **no** log line: read the startup line `social: initial cachedSocial enabled=… whole=…`
   — it names the cause (service off / master off / empty list) without guesswork.
2. YouTube: Home + Shorts shelf free; tap Short from Home → fullscreen cover (L2a);
   Shorts tab → scoped cover; leave → instant dismiss.
3. Snapchat → Spotlight → cover (L1 child-probe, L2a token, or L2b nav click — whichever
   the build exposes); leave via nav/back → cover gone ≤ ~2 s worst case. If missed:
   the throttled tree-summary log names the exact node fields → one-line token tuning.
4. Uninstalled apps' cards gone; pills match visible rows.
5. Toggles: master OFF then tap a row/tab switch → “Turn on Master first” toast (no more
   silent tap); master ON → row toggle flips and **changes enforcement** (launch the app:
   blocked ↔ not blocked, incl. Lite variants); rapid double-taps register every tap.
6. Regression pass: keyword, schedule, PU gates unchanged; suite green.

---

## Addendum — implementation record (what actually shipped, 2026-09-16)

Executed against the real tree; three deltas vs the plan text above, all reducing scope:

1. **“Watch semantics” landed in the existing event-driven `handleSocialTabCoverWatch`**
   (there is no polling loop): unconfirmed (L2b) covers get the 2 s probe-miss grace +
   instant nav-click-away dismissal; any tree evidence **promotes** a cover to confirmed,
   after which dismissal is bit-identical to shipped behavior. L1b (child-selected) and
   L2a (fullscreen token ≥65% screen area) live inside `findActiveTab`, so every existing
   probe site (window path, content events, cover watch) gained the coverage with zero
   call-site changes. L2a-cls (window-class token) and L2b (bottom-20%-screen click with
   caption match) fire only where L1/L1b/L2a found nothing.
2. **Pill math is screen-local** (`visibleLaunchCount` / `visibleTabCount`), NOT in
   `SocialBlockingState` — the state object keeps prefs-truthful semantics for its other
   consumers (service init log, `resolvePreset`); the installed filter is display-only,
   with prefs-truth fallback while the app catalog loads.
3. **L2b helpers take `centerY: Int?`** (not `Rect`) so they stay pure-JVM unit-testable,
   matching the gate test file's discipline.

Shipped: 5 production files, 1 test file; **350 tests / 0 failures** (339 baseline + 11
new), lint green, `assembleRelease` green; DEX-verified (`nav click fired`, `cls token
fired`, `probe miss`, `nav click away from` all present in release classes.dex).
Signed with the existing local key (`347be353…`) — direct update over the previous
test APK, no uninstall needed. **Not pushed, not merged.**
