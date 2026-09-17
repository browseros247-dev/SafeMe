# V11 FINAL — BlockerX-mechanism port (reanalyzed): nav-gated probing + event-source evidence

**Mode: PLAN ONLY — no code changes until explicit "Execute".**
This revision supersedes all previous V11 drafts after a full re-analysis
pass (root cause, recall walk-throughs, rejected alternatives, live-code
verification of every touch point). V10 shipped (`f095a38`, APK
`31aefe00…`) but the device still covers YouTube Home.

---

## 0. Constraint compliance (owner's three standing rules)

| Rule | Compliance (verified against live code this round) |
| --- | --- |
| **1. Minimal code — adjust before implement** | Net LOC is **negative**: −~75 lines (two tree-scan functions + consts + 1 obsolete test + 3 rewritten registry tests) vs +~30 (evidence value class, scoped capture, probe-gate condition). No new files/classes beyond one private data class; no config/manifest/resource changes; no new dependencies. Reuses existing machinery: `isPlausibleFullscreenSurface` (pure, unit-tested — **untouched**), `matchesToken` (exists, dormant → active), the `readClickedSource` main-thread capture pattern (exists — extended, same single fetch), existing throttle/cooldown/watch plumbing. |
| **2. Production ready + scalable** | Detection gets structurally simpler (2 z-order-blind paths deleted, 1 evidence rule). Growth = data only: new vertical = `TabRule` entry; new app = `TAB_RULES` + prefs flag + UI row; the evidence rule is app-agnostic. Battery/CPU **improves**: today every scroll tick runs L1-BFS(≤200 nodes)+knownIds IPC+BFS(≤400) at 4 Hz inside YouTube; V11 replaces that with ≤4 light single-node captures/sec and tree probes only in navigation context. Fail-open on every capture. |
| **3. No influence on other features + 100% surety** | Verified: **all 12** event-type checks in the service are equality-based (L379/393/595/980/1014/1062/1128/1183/1194/2245/2365/2378) — non-social behavior cannot shift. `EventSnapshot` gains a nullable field with a default → all other construction/uses untouched. Capture is scoped: `type==CONTENT_CHANGED && social enabled && pkg ∈ TAB_RULES` (3 packages) && 250 ms rate limit — every other package/event: byte-identical pipeline, zero added binder calls. Whole-app gates (fast lane/backstop/watchdog), keyword, schedule, PU, family: untouched. Surety (§4) is structural, not a bet on tree composition. |

---

## 1. Root cause — why V10 still blocks Home

**Empirical, from our own device history:** YouTube keeps reel surfaces in
the Home window tree (V7 proved preloads exist on Home; V8 proved some are
invisible-flagged). Something in that tree still satisfies our full
acceptance (`isVisibleToUser` + ≥0.80 area + intersects) during Home on
V10 — the prime candidate is the **retained Shorts fragment**
(`reel_watch_fragment_root`) after a Shorts visit: VISIBLE-flagged,
alpha>0, fullscreen bounds, occluded by Home in z-order — and
`isVisibleToUser` is computed app-side (attached+VISIBLE+alpha) and
**cannot see z-order occlusion** (official Android docs; long recorded as
our residual mode).

Two fire paths then hit it on V10:
- **Home scroll**: `handleSocialTabContentEvent` probes on every content
  event (250 ms) → knownIds path accepts the fragment → cover.
- **Warm resume**: the window-state launch probe (service L754) runs the
  same `findActiveTab` → gates instantly on returning to YouTube.
- **Stuck loop**: the tab-watch dismissal probe re-finds the fragment →
  the cover re-raises/holds after BACK/Close.

**Honest limit of sandbox analysis:** we cannot dump the user's live tree,
so the exact identity of the ≥0.80 visible node is not provable from here.
V10 failed precisely by betting on one mechanism (shelf area). **V11 is
therefore mechanism-agnostic**: it makes "no event source from a visible
fullscreen surface" the acceptance rule, which no Home-screen node —
identified or not — can satisfy.

V10's nav-context (channel pages) and 0.80 threshold hardening remain and
are kept.

## 2. BlockerX evidence (io.funswitch.blocker 5.0.87, decompiled)

Artifacts: `analysis/funswitch/x/` (XAPK), `analysis/funswitch/src1/…/MyAccessibilityService.java`
(jadx 1.5.6), smali via dexdump.

1. **Nav-event gating** — `isActiveNavigationEvent`: eventType ∈ {32
   WINDOW_STATE, 1 CLICKED, 8 FOCUSED, 4 SELECTED} (smali `const/16 #32` at
   0x01cd, `extraCommand.invokeSuspend`). Reels/shorts surfaces are
   **never evaluated during scrolling**.
2. **Event-time id lookup** — YouTube: `reel_recycler` (+ `app.revanced`
   fork); YT Music: `section_list_content`. `fragment_root`: **0×** in
   their DEX. Insta: event text/desc/class tokens `reel|clips` +
   `clips_viewer|reel_viewer|reels_tray` on selected nodes — i.e.
   **event-source-driven** evidence.
3. **Subscription check** — their eventTypes `0x80082b` does **not**
   include VIEW_SELECTED (predicate lists it; never delivered). Touch taps
   on nav always emit CLICKED → we skip the `typeViewSelected` config
   change: smaller footprint, zero cross-handler impact.

## 3. Design — one capture feeding two changes

### 3.1 Scoped source-evidence capture (service, snapshot build)

`SourceEvidence(tokenMatched: Boolean, isVisible: Boolean, bounds: Rect,
idOrCls: String)` — captured in `onAccessibilityEvent` exactly where
`readClickedSource` already runs:
- **Clicks**: derived from the source node the click path already fetches
  (zero added binder calls).
- **Content events**: only when social blocking enabled, `pkg ∈ TAB_RULES`,
  and ≥250 ms since last capture (new `lastSocialEvidenceMs`). One
  `event.source` + 4 getters; token via existing `matchesToken` on source
  viewId/className. Fail-open → null.
- Stored on `EventSnapshot` (nullable, defaulted).

### 3.2 Change A — probing only in navigation context (~8 lines)

`handleSocialTabContentEvent` probes only when:
1. event is navigation-class (WINDOW_STATE | CLICKED | LONG_CLICKED |
   FOCUSED), **or**
2. within `TRANSITION_GRACE_MS = 1_500` of the last nav event, **or**
3. `evidence?.tokenMatched == true` (also starts the grace window — covers
   deep-link/auto-swipe entry with no click/window event).
Nav events bypass the 250 ms throttle (extend the existing `isClick`
bypass). **Sustained scrolling never touches the tree.**

### 3.3 Change B — fullscreen detection = event-source evidence (gate)

- **Delete** `findKnownIdFullscreenNode`, `findFullscreenTokenNode`,
  `TOKEN_SCAN_MAX_NODES/DEPTH`.
- `findActiveTab(root, vertical, w, h, evidence)` = **L1 unchanged**
  (selected caption + V10 nav context) → fullscreen acceptance:
  `evidence.tokenMatched && isPlausibleFullscreenSurface(evidence.isVisible,
  bounds…, w, h)` → `TabHit(null, matchedVia="srcToken(<idOrCls>)")`.
- `TabRule.knownIds` → `emptyList()` with reserved-slot comment (data
  growth convention preserved). L1b, L2b unchanged.

### 3.4 Why this is z-order-proof (surety core)

**An occluded surface emits no accessibility events** — only what the user
sees produces content-change events from its own subtree. So:
- the retained fragment can never be an event *source*;
- Home shelf headers/inline autoplay cards do carry `reel_*` ids but are
  ~5–35 % of screen → fail the 0.80 geometric bound;
- a source that is token-matching + visible + ≥0.80 **is by definition a
  visible fullscreen shorts surface** — product-correct to gate.
The argument holds for any node, identified or not.

## 4. Rejected alternatives (deep-reanalysis record)

| Alt | Why rejected |
| --- | --- |
| **X. Literal BlockerX port** (`reel_recycler` knownId, nav-gated, no evidence) | Bets on "reel_recycler never present on Home/channels" — unverified; exactly the bet class that burned V10. Evidence removes the bet at lower code cost. |
| **Y. Corroboration ring** (keep scans; require recent event source inside candidate subtree) | Needs a node ring + identity/ancestor walks (~30 lines, lifecycle-sensitive). Evidence acceptance is strictly stronger and smaller. |
| **Z. Empty knownIds + raise threshold (e.g. 0.95)** | A visible-flagged fullscreen fragment still passes any area bound — FP survives. |
| **typeViewSelected subscription** | BlockerX doesn't actually subscribe it; adds a new event type into every handler for no proven gain. Dropped (all type checks are equality-based, so it stays a trivial future config flip if ever needed). |

## 5. Walk-throughs

**False positives → 0 covers:**

| Scenario | Why it cannot gate |
| --- | --- |
| Home scroll, cold, any device ratio | A: no probe; B: Home sources small/non-token |
| Home scroll after Shorts visit (retained occluded fragment) | fragment emits no events → never a source; L1 nothing selected |
| Warm resume onto Home | window-state probe passes `evidence=null` → L1 only → miss |
| Click any Home/channel video | grace probe → L1 miss; card/regular-player sources carry no vertical token; regular player ids match nothing |
| Channel pages incl. Shorts strip (scroll+clicks) | A + V10 nav-context L1; strip not fullscreen |
| Stuck cover after BACK/Close-snooze | watch: no evidence + L1 miss → `hit=null` → dismisses; after snooze, next probe misses → stays free |

**True positives:**

| Scenario | Path | Latency |
| --- | --- | --- |
| Shorts/Reels/Spotlight tab tap | CLICKED → L1 selected caption + nav context | immediate |
| Tab tap where selection missing (variant) | grace probes → player sources → srcToken | ≤ ~500 ms |
| Short-from-Home card tap | grace ticks → inflating player emits token sources ≥0.80 | ≤ ~500 ms |
| Deep link (cold) | WINDOW_STATE (L1 if tab selected) + player events → srcToken | ≤ ~500 ms |
| Deep link (warm) / auto-swipe from inline | token-source pre-filter probes on the event itself | ≤ ~500 ms |
| Warm resume onto real Shorts | L1 if tab selected, else resumed playback events → srcToken | ≤ ~500 ms |
| Watch holds cover on real Shorts | playback keeps emitting in-surface sources | persistent |

## 6. Files touched (3) + exact test inventory

1. **`SafeMeAccessibilityService.kt`** — `SourceEvidence` + capture (click
   path extension + scoped content capture), nav predicate + grace +
   probe-gate condition, throttle bypass tweak, `findActiveTab` call sites
   (window-state probe → `null`; content handler + watch →
   `snapshot.evidence`), `via=` attribution incl. `srcToken`.
2. **`SocialBlockingGate.kt`** — delete 2 scan functions + consts; evidence
   acceptance; empty `knownIds` w/ comment; `matchesToken` KDoc
   (source-view semantics).
3. **`SocialBlockingGateTest.kt`** (29 tests today; suite 356) —
   **remove**: TOKEN_SCAN consts test (L181); **rewrite**: 3 knownIds
   registry tests (L188/203/215) → knownIds-empty + tokenHints intact;
   **keep unchanged**: all `isPlausibleFullscreenSurface` boundary rows
   (0.79/0.65/0.75 reject, 0.81 accept) and `matchesToken` rows (now the
   live path); **add**: SourceEvidence acceptance decision rows (token ×
   visible × area) + nav/grace predicate rows if extracted pure.

## 7. Verification gates (Execute phase)

`:app:testDebugUnitTest` green (count re-pinned) → `:app:lintDebug`
0 errors → `:app:assembleRelease` → DEX strings: **absent**
`reel_watch_fragment_root`, `cls token fired`; **present** `srcToken`,
`navTab`, `nav click fired` → signer `347be353…` (direct update) → APK +
SHA-256 delivered → addendum to this plan → **local commit only; no
push/merge without explicit command.**

## 8. Device checklist

1. Home scroll 60 s cold → 0 covers.
2. Shorts tab (gated ✓) → BACK → Home scroll 60 s → 0; Close-snooze path
   same; cover dismisses on BACK (no stuck loop).
3. Leave & resume YouTube (warm) onto Home → no instant cover.
4. 3 channel pages incl. Shorts strip: scroll + click videos → 0.
5. Shorts / Reels / Spotlight main tabs → gated.
6. Short-from-Home tap **and** auto-swipe → gated ≤ ~1 s.
7. Dismissals: BACK / swipe-away / Close 4 s snooze.
8. Residual → one `adb logcat -s SafeMeAccessibilityService` capture;
   `via=` pinpoints the path.

## 9. Residuals + fallbacks (evidence-driven, data edits)

- **Under-block** if a fullscreen surface emitted no token-bearing content
  events: existing `logSocialProbeMiss` surfaces it; fallback = re-add a
  knownIds scan *behind* the same evidence rule (small edit).
- **Pathological** full-bleed Home inline player ≥80 % visible with token
  id would gate — that surface is a fullscreen short; product-correct.
- BlockerX data bank (future data-only growth): ReVanced ids, YT Music
  `section_list_content`, Snapchat `ngs_spotlight_icon_container` /
  `ngs_community_icon_container`, Insta `clips_viewer|reel_viewer|
  reels_tray`, per-package throttles, contentChangeTypes 0/5 drop.

---

## 10. Execution addendum (2026-09-16, executed)

**All V11 edits applied and verified.** Gates, in order:

| Gate | Result |
| --- | --- |
| `:app:testDebugUnitTest` | BUILD SUCCESSFUL — **357 tests, 0 failures, 0 errors** (356 − 2 removed knownIds/TOKEN_SCAN tests + 3 new: nav-event predicate, evidence acceptance, evidence boundary) |
| `:app:lintDebug` | **0 errors**, 68 warnings (identical to V9/V10 baseline) |
| `:app:assembleRelease` | BUILD SUCCESSFUL (combined 20m29s run) |
| DEX proof | `reel_watch_fragment_root` → **0** (tree-scan paths gone from the shipping binary), `cls token fired` → **0**; present: `srcToken` ×1, `navTab` ×1, `nav click fired` ×1, `social tab gate launched` ×1, `social fast lane` ×1 |
| Signer | SHA-256 `347be353…30d97d0` — identical to installed V9/V10 builds → **direct update safe** |
| Delivered APK | `/home/user/SafeMe-0.1.0-release.apk` (3,189,427 B), SHA-256 `3aed9edf54e2ab73b5f323337e54ae7d0e8a9404e521132c20251fa31d15615e` |

Implementation deltas vs plan (both improvements, no scope change):
- `SourceEvidence` carries **int bounds** instead of a `Rect` (keeps the
  acceptance decision table pure-JVM unit-testable without android.jar
  stubs).
- L2b nav-click branch keeps its exact `TYPE_VIEW_CLICKED`-only condition
  (the shared `isClick` local became `isNav`; the click-only semantics are
  preserved verbatim).

Net size: gate −1.2 KB (2 scan paths + consts deleted), service +3.8 KB
(scoped capture + probe gating), tests +2.2 KB.

Commit: local only on `agent/social-blocking-fixes`. No push/merge.
