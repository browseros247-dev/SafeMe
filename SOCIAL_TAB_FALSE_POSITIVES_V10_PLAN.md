# Tab-Gate False Positives — FINAL Plan (V10 final, post-reanalysis)

> **Date:** 2026-09-16 · **Baseline:** `agent/social-blocking-fixes` @ `24a2678` (local; **no push/merge without explicit command**)
> **Mode:** PLAN ONLY — awaiting `Execute`.
> **Symptoms:** (1) scrolling YouTube **Home** → whole screen blocked; (2) opening a **channel** gets blocked because it has a “Shorts” tab.
> **Budget:** **~30 lines changed (net ≈ +10), 2 files + 1 data entry, 0 UI code.** Tests updated in place; suite stays ≥356 green.

---

## 1. Timeline solved — why these surfaced NOW (git-verified this round)

V9 changed **geometry only**. `git show 24a2678` proves pre-V9 L2 hits returned
`TabHit(top)` → covers rendered as thin top **strips**. Therefore:

- A **visible** shelf-area hit (65–75% of screen; V8's predicate correctly passes it —
  it IS visible) has been firing since V7's 400-node budget, but as a barely-noticeable
  strip — plausibly what was read earlier as “gate not appearing properly” near the
  shelf. V9's full-screen covers made the same detections unmissable → symptom 1.
- Channel pages: L1 on a selected “Shorts” **strip caption** (top of page — no
  bottom-nav requirement in the match condition, verified this round) returned
  `TabHit(null)` = already full-screen pre-V9; channels simply hadn't been tested.
  L2 on a large channel grid would have been a strip pre-V9. → symptom 2.

No new detection path was created by V9 — the fix targets are the detection rules, and
each maps to a verified mechanism:

| Cause | Verified evidence | Symptom |
|---|---|---|
| **Shelf/grid nodes at 65–75% area pass the 0.65 threshold** (Shorts shelf: tall cards + header; `reel_recycler` scroller id; `isVisibleToUser` has no clipping check so mid-scroll counts) | threshold `0.65` at gate L243; knownIds incl. `reel_recycler` (L64–65) | 1 (and 2 via channel grid) |
| **L1 accepts a selected caption anywhere** — channel “Shorts” tab strips live at the top of the page | `findFirstSelectedTab` condition (fresh read): no nav-context; `navBarTopAbove` accepts only width ≥90% ∧ top ≥70%·H (L414) — strips can never match it, yet the hit returns anyway with `coverAboveY=null` | 2 |
| **cls-token branch sees content-source view classes, never window classes** (`snapshot.cls = event.className`, L374; window-state events route away from this branch) — its V5 intent was impossible; it can only fire on arbitrary scrolled views with reel/shorts in a non-obfuscated class name | service L2265–2273 (fresh read); my V8 exclusion of this path was based on a wrong assumption and is hereby corrected | 1 (secondary; YouTube classes are often obfuscated) |

## 2. Fix set — unchanged from V10 draft, now with per-item confidence and caveats

1. **Delete the cls-token branch** (service, −9 lines; anchor verified). Confidence it
   is a pure FP vector: high (signal semantics proven wrong). **Regression watch item:**
   IF Snapchat Spotlight on the device was relying on cls re-raises (only possible when
   Spotlight probes don't confirm — unlikely: no Spotlight complaint survived V5–V9
   testing), the checklist below catches it and the remedy is data-level (Spotlight
   token/knownId), not resurrection of a broken signal. `matchesToken` stays (tested).
2. **L1/L1b accept only in bottom-nav context** — hit requires `navBarTopAbove != null`.
   Verified edges: main tabs fire (captions live in the bottom bar); immersive Shorts
   tab with off-screen-translated nav still matches (top ≥ 70%·H); channel strips
   rejected; **caveat:** tablet side-rail navs won’t match → the fullscreen player is
   still gated by L2 (phone is the target; documented).
3. **Threshold 0.65 → 0.80** (one constant + 2 re-pinned tests). Separation is clean:
   shelves/grids ≤ ~75%; fullscreen players ≈ 100% of `displayMetrics` area (bounds
   include system bars); the 250 ms probe cadence absorbs fade-in frames.
4. **knownIds trim: drop `reel_recycler`, keep `reel_watch_fragment_root`** — the
   recycler is the generic reel-LIST id (shelf scrollers, channel grids); the fragment
   root is the canonical fullscreen surface. Recall preserved: fast-path (root) + BFS
   token scan (recycler still caught when genuinely fullscreen ≥0.80).
5. **`matchedVia = "navTab"` on L1/L1b** — every firing path now named in `via=`
   (navTab / known-id / viewId-or-cls / navClick), making any residual a one-line
   logcat diagnosis.

## 3. Residual risk — pre-committed remedies (evidence-driven, no speculative code)

- **Channel Shorts grid ≥80% carrying a reel/shorts id** (the only surviving vector for
  symptom 2 if it is not the L1 strip): `via=` names the exact id → remedy chosen from:
  raise threshold / add bounded SurfaceView-presence check to acceptance / id-level
  exclusion. Decided on evidence, not built now.
- **Z-order-occluded preload** (a11y has no z-order): `via=…fragment_root` on Home →
  drop that knownId (data). Low likelihood (INVISIBLE/alpha-0 preloads already filtered).
- **Nav-bar container itself marked selected** (would fire L1 on every screen, not just
  scroll — inconsistent with the report; excluded by symptom shape, kept on the list):
  `via=navTab` on Home would name it.

## 4. Scenario matrix after the fix

| Scenario | Expected |
|---|---|
| Home scroll (shelf passing; any device ratio) | **Never gated** |
| Channel pages incl. selected “Shorts” strip tab | **Never gated** |
| Shorts tab (main) / Shorts from Home / deep link / auto-swipe | Gated ≤~1 probe cycle (L1 nav-context + L2 unchanged) |
| FB Reels / Spotlight tabs | Gated via bottom-nav L1; Spotlight watch item in checklist |
| Whole-app family gate / keyword / schedule / PU / delivery | Untouched |
| Any future misfire | Exactly named by `via=` in one log line |

## 5. Zero-influence proof & verification

- Service: one branch deleted; no other `matchesToken` caller (grep-verified).
- Gate: one condition tightened (L1/L1b share it), one constant, knownIds −1 entry;
  L2 predicate, L2b, height cap, watch, whole-app gate: untouched.
- Tests: predicate boundary re-pinned (0.79/0.81), knownIds registry test → single id,
  token/id consistency test still passes (“reel” ⊂ fragment-root id). Suite ≥356 green;
  lint; `assembleRelease`; DEX: **absence** of `cls token fired`, presence of `navTab`
  + all prior markers. Commit **local only**.
- Device checklist: Home scroll 60 s → 0 covers; open 3 channels (≥1 with Shorts tab) →
  0 covers; Shorts tab + Short-from-Home → gated ≤1 s; **Spotlight + Reels tabs →
  gated (cls-removal regression check)**; BACK/swipe-away dismiss; Close 4 s snooze.

## 6. Execution order

1. Service: delete cls branch.
2. Gate: nav-context condition + threshold 0.80 + `matchedVia="navTab"` + knownIds trim.
3. Test updates → full gate → local commit. **No push/merge without explicit command.**

---

## 7. Execution addendum (2026-09-16, executed)

**All V10 edits applied and verified.** Verification results, in gate order:

| Gate | Result |
| --- | --- |
| `:app:testDebugUnitTest` | BUILD SUCCESSFUL 2m57s — **356 tests, 0 failures, 0 errors** |
| `:app:lintDebug` | BUILD SUCCESSFUL — **0 errors**, 68 warnings (unchanged from V9 baseline) |
| `:app:assembleRelease` | BUILD SUCCESSFUL 9m57s (combined lint+release run) |
| DEX string proof | `cls token fired` → **0 occurrences (path removed)**; `nav click fired` ×1, `social tab gate launched` ×1, `reel_watch_fragment_root` ×1, `social fast lane` ×1, `navTab` ×1, `navClick` ×1; fire log carries `via=$via` (service L2219) |
| APK signer | SHA-256 `347be353…30d97d0` — identical to V9 → **direct update over installed app is safe** |
| Delivered APK | `/home/user/SafeMe-0.1.0-release.apk` (3,189,427 B), SHA-256 `31aefe00031cb00c8b6793853a0462cce54c0e663d52c593b703138ab7b880e0` |

Note on attribution values: `known-id` is not a literal in the DEX by design — line 186
logs the **concrete matched resource-id** as `matchedVia` (e.g. `reel_watch_fragment_root`),
so via= ∈ {navTab, <resource-id>, viewId|cls, navClick} in practice.

Commit: local only on `agent/social-blocking-fixes` (3 sources + this plan). No push/merge.

**Device checklist for the field test** (unchanged from §5): Home scroll 60 s → 0 covers;
3 channel pages incl. one with a Shorts tab → 0 covers; Shorts/Reels/Spotlight main tabs →
gated; Short-from-Home → gated ≤ ~1 s; BACK/swipe-away dismiss + Close 4 s snooze intact.
