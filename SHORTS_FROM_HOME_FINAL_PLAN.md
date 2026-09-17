# Shorts-from-Home — FINAL Plan (V7, supersedes V6)

> **Date:** 2026-09-16 · **Baseline:** `agent/social-blocking-fixes` @ `e796f6c` (local; **no push/merge without explicit command**)
> **Mode:** PLAN ONLY — awaiting `Execute`.
> **Symptom:** a Short opened from the YouTube Home feed is not blocked; all other social fixes behave.
> **Budget:** **~25 production lines + ~5 tests, ONE file** (`SocialBlockingGate.kt`). Service, VM, screen, prefs untouched. Track C is a **0-engine-line** data edit after one device capture.

---

## 1. Reanalysis — every claim below re-verified against the committed tree this round

| Verified fact (fresh read of `e796f6c`) | Consequence |
|---|---|
| `handleEvent` routes **all** content/click/focus events in gated apps to `handleSocialTabContentEvent`; window-state events take the window path | Probes run ≥4×/s during Shorts playback — “no event” is not the failure |
| `findActiveTab` = L1 → L1b → L2a (token node ≥65% screen); L2b/cls live in the service behind nav-region bounds | Only **L2a** can fire for a Home-feed Short — by design; every other layer must stay silent there (false-positive guards) |
| Both L1 and L2a share `MAX_STRINGS = 200 / MAX_DEPTH = 12` (gate L96–97, loops L152, L229) | A tree carrying Home-feed debris can exhaust the budget **before** the player subtree |
| Probe-miss diagnostic shipped (service L2324, 2 s throttle, first 14 nodes + `nodes(N)` count) | One logcat line on the device discriminates every remaining cause |
| `findAccessibilityNodeInfosByViewId` used nowhere in the codebase | The framework’s deep single-IPC search — budget-free — is an untapped lever |

**External evidence:** the documented Shorts detector IDs are
`com.google.android.youtube:id/reel_watch_fragment_root` and `:id/reel_recycler`
(StackOverflow 2025; stable since ~2021) — both contain `reel`, both are fullscreen, so
the shipped tokens are *correct for builds that expose them*. The same source documents
**asynchronous rendering** making detection intermittent — solved by repeated probes,
which our 250 ms cadence already provides. Tooling (ReelPal, 2026) confirms hierarchies
“change with every app update.”

## 2. Cause convergence (persistent failure ⇒ only two live causes)

- **Excluded:** L1/L1b/L2b (by design, and firing them here would re-create the launch
  false positive or the video-title false positive); event flow (verified above);
  async rendering (repeated probes); H5 delivery/overlay (the same overlay path serves
  whole-app blocks — if launch blocking works on the device, excluded); cooldowns
  (miss paths never set them; dismissal clears them).
- **H2 — BFS budget exhaustion:** during/after the Home→Shorts transition the tree can
  still carry the feed; BFS spends all 200 nodes on feed debris before the player
  sibling. Explains persistent failure even with correct tokens.
- **H1 — token drift:** the device’s YouTube build renamed/obfuscated the IDs, so no
  node carries `reel`/`shorts`. Explains persistent failure with any budget.
- **H4 — settings** (a11y / master / Shorts flag): 0-code; the `social: initial
  cachedSocial …` line confirms in seconds (protocol step 0).

## 3. The fix — all of it inside `SocialBlockingGate.kt`

**A. `knownIds` framework fast-path (~20 lines + data) — kills H2 deterministically.**
New `TabRule` field `knownIds: List<String> = emptyList()` (fully-qualified view IDs).
`findActiveTab` consults it between L1b and the BFS: for each ID,
`root.findAccessibilityNodeInfosByViewId(id)` — **one IPC, any depth, no budget** —
then the SAME ≥65%-screen area verification as L2a before returning
`TabHit(top-or-null)`. YouTube ships with the two documented IDs; Facebook/Snapchat keep
empty lists → **literal no-op for them**. Fail-open: `runCatching` around the API (it
throws on dying windows), returned nodes recycled. Cost: ≤2 IPCs per probe, cheaper
than the BFS it front-runs. Growth: a new ID = one list entry (the registry seam).

**B. L2a dedicated budget (~4 lines) — hedge for unknown tokens.**
The BFS token scan gets its own `400 nodes / depth 14` instead of sharing L1’s 200/12.
L1 is untouched (Shorts-**tab** behavior stays bit-identical). Runs only after L1+knownIds
miss, inside the 3 gated apps, behind the 250 ms throttle, early-exits on hit.

**C. Capture-guided data edit (0 engine lines) — kills H1/H3 with certainty.**
One repro with `adb logcat -s SafeMeA11y` → read the shipped probe-miss line:

| Log shows | Fix |
|---|---|
| `nodes(200)` + feed debris only | H2 — fixed by A/B already; verify |
| Player nodes with unfamiliar ids | Add the id’s stable fragment to `tokenHints` / full id to `knownIds` — one data line |
| Token id on a small node only | H3 — adjust that one node’s handling per the dump (still data-level) |
| No social lines at all | H4 — settings; no code |

**Rejected alternatives (auditable):** contentDescription/text token matching (“Shorts”
captions sit in the nav of every screen; expanded-metadata sheets on normal videos can
contain “#shorts” → false-positive vectors); large-ancestor or scrollable-container
heuristics (the feed RecyclerView and `reel_recycler` are indistinguishable → shelf
false positives); swipe heuristics (Home flings match); blind token widening (`reel` is
a substring — `reel_watch`, `reel_recycler` already match).

## 4. Error handling & full scenario matrix

| Scenario | Expected | Layer / error path |
|---|---|---|
| Short tapped from Home (documented build) | Cover ≤~1 probe cycle | knownIds fast-path; async paint covered by 250 ms re-probes |
| Short tapped from Home (drifted build) | Cover after data edit | Capture → C; until then probe-miss log names the ids |
| Shorts shelf merely visible on Home | **Never gated** | ≥65% area bound (fast-path AND BFS); unit-tested |
| Video titled “…shorts…” tapped mid-screen | **Never gated** | L2b nav-region bound; unit-tested |
| Shorts tab / Reels / Spotlight tabs | Unchanged scoped covers | L1/L1b bit-identical |
| Auto-swipe normal→Shorts; deep link; share; notification | Covered | Same probes on window/content events |
| Normal watch page with Shorts recs; expanded “#shorts” metadata | **Never gated** | Area bound + text/desc matching rejected |
| Dying window / dead node during fast-path | Fail open, no crash | `runCatching` + recycle |
| Swipe away / Close / re-enter / rotation | Dismiss ≤1 event; 4 s snooze; refit | Shipped watch, unchanged |
| Event storm | ≤4 probes/s; knownIds adds ≤2 IPCs each | 250 ms throttle + 4 s gate cooldown |
| A11y/master/flag off | Nothing fires — by design | Protocol step 0 + init log line |

## 5. Zero-influence proof & verification

- One file; `knownIds` defaults empty → FB/Snap paths and every non-social engine see
  byte-identical behavior; L1 loop constants untouched; the fast-path only runs where
  today’s code returns null.
- Tests: +~5 pure cases (registry contents per vertical, empty-knownIds no-op, area
  guard unchanged, constants) → suite ≥350 green; `lintDebug`; `assembleRelease`;
  DEX spot-check (`knownIds`-path log/string markers). Commit **local only**.
- Device checklist: step 0 (a11y + master + Shorts flag) → launch-block regression →
  Short from Home (cover ≤1 s) → Home with shelf visible (NO cover) → Shorts tab
  unchanged → swipe-away dismiss → send the one probe-miss line if anything still misses.

## 6. Execution order

1. Implement A + B + tests + full gate (helps even before any capture).
2. One on-device logcat line → apply the indicated data edit (C) if needed.
3. Re-run checklist; commit locally. **No push/merge without explicit command.**

---

## Addendum — implementation record (2026-09-16)

Shipped exactly as planned, all inside `SocialBlockingGate.kt`:
`TabRule.knownIds` (YouTube: `reel_watch_fragment_root`, `reel_recycler`; FB/Snapchat
empty → no-op), `findKnownIdFullscreenNode` fast-path (framework deep search + same
≥65% area guard + recycle + fail-open) consulted between L1b and the BFS, and the
token scan's own `TOKEN_SCAN_MAX_NODES=400 / TOKEN_SCAN_MAX_DEPTH=14` (L1 constants
untouched at 200/12).

Gate: **353 tests / 0 failures** (+3 new: registry contents, id format, token/id
consistency), lint green, `assembleRelease` green. DEX-verified: both known ids present
in release `classes.dex`; previous layers (`nav click fired`, `fast lane`) intact.
Same signing key (`347be353…`) → direct update on the test device.
APK: `/home/user/SafeMe-0.1.0-release.apk`, SHA-256 `d042ba48…`. **Not pushed, not merged.**

Remaining unknown (H1 — id drift on the device's build) closes with one line from
`adb logcat -s SafeMeA11y`: the `social tab probe miss …` entry names the exact node
ids, and the fix is one data entry.
