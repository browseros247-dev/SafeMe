# Shorts-from-Home — Residual Fix Plan (V6, final)

> **Date:** 2026-09-16 · **Branch:** `agent/social-blocking-fixes` @ `e796f6c` (local; **no push/merge without explicit command**)
> **Mode:** PLAN ONLY — awaiting `Execute`.
> **Symptom:** a Short opened from the YouTube **Home feed** is still not blocked (V5 shipped).
> **Budget:** Track 2 = **~12 production lines + ~4 tests, 1 file**. Track 1 = **0 engine lines** (data edit after one device capture). No other feature is touched.

---

## 1. Where the shipped stack stands for this exact scenario

For a Short tapped from Home, layer by layer (verified against `e796f6c`):

| Layer | Can it fire here? | Why |
|---|---|---|
| L1 / L1b (selected nav tab, ±child) | **No — by design** | Home stays selected; no “Shorts” tab is selected/checked. Firing here would block the whole app at launch — the original false positive. |
| L2a (token node ≥65% screen) | **The only layer that can** | Shorts fullscreen = `reel_watch_fragment_root`-class node containing token `reel`/`shorts`, ~100% screen area. |
| L2a-cls (window-class token) | Rarely | YouTube is single-activity; the class usually carries no token. |
| L2b (nav-region click) | **No — by design** | The tap is a feed card mid-screen, not a bottom-nav item. The region bound exists precisely so a video *titled* “shorts…” can never gate. |

Public evidence: accessibility blockers detect the Shorts screen by
`com.google.android.youtube:id/reel_watch_fragment_root` and `:id/reel_recycler`
(StackOverflow, 2025) — both contain `reel`, both satisfy the area bound. **But** current
tooling (ReelPal, 2026) documents that these hierarchies are “partly obfuscated and
**change with every app update**.” So the shipped `reel`/`shorts` tokens are correct for
known builds and unverifiable for the user’s exact build without device truth.

## 2. Candidate causes — each one is distinguished by ONE existing log line

Reproduce once with `adb logcat -s SafeMeA11y` (or `adb shell uiautomator dump /sdcard/ui.xml && adb pull /sdcard/ui.xml` for the full tree), then read:

| # | Cause | Log signature during repro | Fix (size) |
|---|---|---|---|
| H1 | Build renamed the IDs — no token in tree | `social tab probe miss … nodes(N): …` lists player nodes with non-token ids | **1-line data edit**: add the real token to `tokenHints` (the seam built for exactly this) |
| H2 | Probe never reaches the player node (200-node BFS budget) | probe-miss line shows `nodes(200)` (budget ceiling) with feed chrome only | Track 2a (below) |
| H3 | Token exists only on small controls (<65%) | probe-miss shows token id on a small node | 1-line threshold/second-token edit guided by the dump |
| H4 | Probe never runs (master/tab flag/a11y off) | **no** social lines at all; check `social: initial cachedSocial enabled=… tabs=…` | **0 code** — settings |
| H5 | Gate fired, cover not drawn | `social tab gate launched …` present, no cover | Delivery layer — separate path; note: if whole-app launch block works on this device, delivery + overlay permission are already proven and H5 is excluded |

This is not guesswork deferred — it is the diagnostic mechanism V5 shipped, and every
branch ends in a ≤1-line change or a settings toggle.

## 3. Track 2 — blind hardening to ship NOW regardless of the capture (~12 lines, 1 file)

**2a. Dedicated L2a scan budget** (covers H2, `SocialBlockingGate.kt` only):
the token scan gets its own limits — `400 nodes / depth 14` — instead of sharing L1’s
`200/12`. L1 is untouched (Shorts-**tab** blocking keeps its exact shipped behavior).
Cost is bounded where it can only run: after an L1 miss, inside the 3 gated apps, behind
the 250 ms probe throttle, with early-exit on hit. Worst case ≈2× the existing probe,
4×/s, only while a gated vertical is enabled — no other engine ever calls this function.

**Rejected alternatives (documented so the decision is auditable):**
- *Match `contentDescription`/text tokens* — “Shorts” captions sit in the bottom nav of
  **every** YouTube screen; this re-creates the launch false positive. **Never.**
- *Token node with any large ancestor* — the root ancestor is always ≥65%, so a Home-feed
  Shorts **shelf card** would gate. False-positive vector. Rejected.
- *Large-vertical-swipe heuristic (ReelPal-style)* — Home-feed flings produce the same
  signal; acceptable for counting, not for blocking. Rejected.
- *Blind token widening* — `reel`/`shorts` are substrings, so `reel_watch`, `reel_recycler`,
  `shorts_*` all already match; widening adds noise, not coverage. Rejected.

## 4. Error handling & full scenario matrix (as requested)

| Scenario | Expected | Layer / error path |
|---|---|---|
| Short tapped from Home feed | Full cover ≤1 probe cycle (≤~500 ms) | L2a; on tree-read failure → fail open, next 250 ms event retries |
| Shorts shelf merely **visible** on Home | **Never gated** | Area bound ≥65% kills shelf cards; verified by unit test |
| Video titled “…shorts…” tapped mid-screen | **Never gated** | L2b nav-region bound; unit-tested |
| Shorts tab entry / Reels tab / Spotlight | Scoped cover, nav stays usable | L1/L1b (unchanged, bit-identical) |
| Auto-swipe from a normal video into Shorts | Covered on next event | L2a via content events; 250 ms throttle |
| Shorts via deep link / share / notification | Covered | Window path → same L2a probe |
| Normal watch page with Shorts recommendations below | **Never gated** | Recs are small nodes; area bound |
| Swipe away / Close / re-enter | Dismiss ≤1 event; Close = 4 s snooze; re-entry re-blocks | Watch + cooldown (shipped, unchanged) |
| Rotation / font-scale while covered | Cover refits | `refitTabCover` (shipped) |
| Tree unreadable / stale nodes / exceptions | Fail open, never crash, never whole-app cover | `runCatching` at every level + service-level catch |
| Event storm (scroll spam) | ≤4 probes/s | 250 ms throttle; clicks bypass but 4 s gate cooldown dedupes |
| Service cold start mid-Shorts | State restored, next event gates | `cachedSocialState` init (shipped) |
| YouTube build with unknown IDs | Probe-miss log names the fix | V5 diagnostics (shipped) |
| A11y off / master off / tab flag off | Nothing fires — by design | Protocol step 0 + `initial cachedSocial` line |
| Overlay permission missing | Covers can’t draw (any gate) | H5 discriminator: if launch-block works, excluded |

## 5. Zero-influence proof & verification

- 2a touches one private function (`findFullscreenTokenNode` limits) called only by
  `findActiveTab`’s L2 branch, which runs only on L1 misses inside the 3 allow-listed
  apps — grep-verified call graph; keyword/URL/schedule/PU engines never enter this file.
- Track 1 fixes are `TAB_RULES` **data** edits — the registry’s documented growth seam.
- Tests: +~4 pure cases (budget independence, area-bound shelf guard stays, token
  substring behavior, grace constant unchanged) → suite ≥350 green; lint; `assembleRelease`;
  DEX spot-check. Commit **local only**.
- Device checklist: step 0 (a11y + master + Shorts flag ON) → launch-block regression →
  Short from Home (cover) → Home feed with shelf visible (NO cover) → Shorts tab
  (unchanged scoped cover) → swipe-away dismiss.

## 6. Execution order

1. Ship Track 2a + tests + full gate (works even if the capture never happens).
2. Take the one logcat capture on-device; apply the ≤1-line data fix indicated by §2.
3. Re-run checklist; commit locally. **No push/merge without explicit command.**
