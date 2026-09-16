# Home-Screen False Positive — FINAL Plan (V8 final, post-reanalysis; fixes the V7 regression)

> **Date:** 2026-09-16 · **Baseline:** `agent/social-blocking-fixes` @ `c2db90a` (local; **no push/merge without explicit command**)
> **Mode:** PLAN ONLY — awaiting `Execute`.
> **Symptom:** Shorts cover raised while the user is on the Home screen (false positive), appearing with V7.
> **Budget:** **~36 production lines + ~35 test lines, 2 files** (`SocialBlockingGate.kt`, 4 argument/log touches in the service). Nothing else touched.

---

## 1. Root cause — every link verified this round

| Verified fact (fresh reads of `c2db90a` + official docs) | Consequence |
|---|---|
| Both fullscreen acceptances (knownIds fast-path, L2a BFS) check **geometry only** (`area ≥ 65%`); `isVisibleToUser` appears **0 times in the codebase** | An invisible node with fullscreen bounds is accepted |
| `findAccessibilityNodeInfosByViewId` returns nodes **regardless of visibility**, unbounded (new in V7) | Timeline matches: V7 introduced the false positive |
| YouTube **preloads** Shorts surfaces (instant-open pre-warm); hidden via alpha-0 / off-screen layout / behind Home — all keep real fullscreen bounds in the a11y tree | The invisible `reel_watch_fragment_root`/`reel_recycler` sits there during Home browsing |
| Official docs: `isVisibleToUser` = attached + VISIBLE + alpha > 0 (app-side computed) | One flag read distinguishes preload from playback |
| Watch behavior (shipped): confirmed cover + probe miss → **immediate** dismiss | After the fix, a Home misfire can never become sticky |

**Chain:** preload node in tree → knownIds finds it (no visibility check) → area passes →
cover raised **confirmed** → watch re-probe finds the same node → re-confirms →
**sticky cover loop on Home**. Secondary vector: L2b treats a bottom-20% tap on a video
card whose subtree contains “shorts” as a nav tap (transient ~2.5 s cover).

**Discriminator for the user report:** cover titled **“YouTube Shorts”** = this stack.
Cover titled with an **app name** = whole-app gate (different mechanism). Cover on the
**phone launcher** = not social. Plan scoped to the first; say the word if it's another.

## 2. The fix — one pure predicate at both acceptance points

**A. `isPlausibleFullscreenSurface` (pure, unit-tested):**
```
isVisible && w>0 && h>0 && area ≥ 65% of screen
&& intersects the visible screen (right>0 && bottom>0 && left<screenW && top<screenH)
```
`isVisible` = `runCatching { node.isVisibleToUser }.getOrDefault(false)` — fail **closed
for this one check** (the probe overall stays fail-open). Kills every preload hiding
mode a11y can express: INVISIBLE/GONE (absent from tree), alpha-0 (flag false),
off-screen position (intersection). Applied at the knownIds fast-path AND the L2a BFS
token node — so Facebook/Snapchat get the identical, strictly-safer acceptance.

**B. Fire-time attribution (~6 ln):** `TabHit` gains `matchedVia: String? = null`
(the id/token that fired; L1 hits stay null); `launchSocialTabGate` logs it on the
existing launched-line. Gate stays a pure object (no logging import). If a false
positive ever survives — only z-order occlusion can; a11y has no z-order — one logcat
line names the exact node and the remedy is a data edit.

**C. L2b nav-item height cap (~6 ln):** `isBottomNavClick(centerY, height, screenH)`
also requires `height ≤ 20%·screenH`. Verified margins: YouTube's nav ≈ 6–8% of
screen height; feed/shelf cards ≥ 25%. Applied to the L2b gate **and** the watch's
nav-click-away dismissal (service L2275, L2383 pass `clickedBounds?.height()`), so a
bottom-feed card tap can neither raise nor dismiss a cover.

**Rejected simpler alternatives (analyzed, not assumed):**
- *Drop `reel_recycler` from knownIds* — if the false positive comes from the
  fragment-root preload it changes nothing; the predicate is needed regardless, and
  both ids keep recall. Insufficient alone.
- *Revert V7* — reinstates the Shorts-from-Home under-block. Regression. Rejected.
- *Raise knownIds covers as unconfirmed (grace)* — the watch probe finds the same
  invisible node and re-promotes → sticky again. Wrong layer. Rejected.

## 3. No-regression proof for the blocking we just shipped

A genuinely playing Short is visible, on-screen, fullscreen → passes trivially. Entry
fade-in: alpha > 0 from the first animated frame; worst case the cover lands one
250 ms probe later. Back-transition: player still visible while fading → cover holds,
then one probe after Home settles the miss dismisses it (confirmed path — immediate).
Documented caveat (official docs): with **accessibility magnification on API 26–29**,
`isVisibleToUser` may incorrectly return false → under-block direction (fail-open,
never a false positive). L1/L1b, family gate, delivery, watch semantics: untouched.

## 4. Scenario matrix (delta)

| Scenario | Expected | Mechanism |
|---|---|---|
| Home with preloaded Shorts fragment (alpha-0 / off-screen / behind) | **Never gated** | Predicate A |
| Bottom-of-feed card “…shorts…” tapped | **Never gated** | Height cap C |
| Real Short (Home tap / deep link / auto-swipe / tab) | Covered ≤~1 probe cycle | Predicate passes trivially |
| Shorts shelf visible on Home | **Never gated** | Area bound (unchanged) |
| Back from Shorts → Home | Cover holds through fade, dismisses ≤1 probe after | Confirmed miss path |
| Legit cover + bottom card tap | Cover **stays** | C on dismissal path |
| Dying node during visibility read | No gate | fail-closed on the check |
| Any future false positive | Named in one log line | Attribution B |
| Magnification on API ≤29 | Possible under-block (safe direction) | Documented caveat |

## 5. Zero-influence proof & verification

- 2 files; predicate + `matchedVia` default + height-cap default keep every other
  caller byte-identical; L1 loop, prefs, VM, screen untouched.
- Tests: +~7 pure cases (predicate decision table: invisible / off-screen-below /
  64% / 65% / zero-dim / visible-fullscreen / partially-offscreen; height-cap matrix);
  2 existing `isBottomNavClick` tests updated to the new signature. Suite ≥353 green;
  `lintDebug`; `assembleRelease`; DEX marker check. Commit **local only**.
- Device checklist: Home scroll ~60 s → **zero covers**; shelf visible → none; Short
  from Home → cover ≤1 s; Shorts tab unchanged; bottom card “…shorts…” tap → none;
  swipe-away dismiss intact; back-from-Shorts dismiss ≤1 s.

## 6. Execution order

1. Predicate + both acceptance points + `matchedVia` + log (gate, service log line).
2. Height cap + 2 service arg touches; update the 2 signature-pinned tests.
3. New tests → full gate → local commit. **No push/merge without explicit command.**

---

## Addendum — implementation record (2026-09-16)

Shipped exactly as planned:
`SocialBlockingGate.isPlausibleFullscreenSurface` (pure: visible && ≥65% && intersects
visible screen) now gates BOTH L2 acceptances (knownIds fast-path + BFS token scan) with
`isVisibleToUser` read fail-closed; `TabHit.matchedVia` carries the firing id/token into
the existing "gate launched" log line (`via=…`, incl. `cls` / `navClick` tags);
`isBottomNavClick` gained the nav-item height cap (≤20% screen height) applied at both
the L2b gate and the watch's nav-click-away dismissal.

Gate: **356 tests / 0 failures** (+3 predicate decision-table tests; 2 signature-pinned
tests updated), lint green, `assembleRelease` green. DEX-verified: ` via=` marker
present; `reel_watch_fragment_root`, `nav click fired`, `social fast lane` intact.
Same signing key (`347be353…`) → direct update on the test device.
APK: `/home/user/SafeMe-0.1.0-release.apk`, SHA-256 `55cbdea5…`. **Not pushed, not merged.**
