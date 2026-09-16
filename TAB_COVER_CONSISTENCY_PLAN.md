# Tab-Cover Consistency — FINAL Plan (V9 final, post-reanalysis)

> **Date:** 2026-09-16 · **Baseline:** `agent/social-blocking-fixes` @ `d76610c` (local; **no push/merge without explicit command**)
> **Mode:** PLAN ONLY — awaiting `Execute`.
> **Symptoms (device round):** (1) Shorts from Home — gate “not appearing properly”; (2) Shorts tab — block screen “not the same as my other block screens”.
> **Budget:** **~8 production lines changed, 2 files, 0 new files, 0 UI code.** Suite stays 356 green.

---

## 1. Root cause — one mechanism, both symptoms (every claim re-verified in code this round)

**Content identity is proven in code, not from comments:** `attachOverlay` (controller
L311–334) sets the SAME `BlockOverlay(...)` composable for every gate type — tab covers
included. Exactly two things differ for a social tab cover today:

| Difference | Verified location | Verdict |
|---|---|---|
| **Window height = `coverAboveY`** (gravity TOP; `MATCH_PARENT` only when null) | controller L367–370 | **The bug + the inconsistency.** L2 (Shorts from Home) returns `TabHit(rect.top)` whenever the player sits below the status bar → cover window ≈60–100 px → the block UI **squashed into a thin top strip** (“not appearing properly”; correct only in the fully-immersive case — hence flaky). L1 (Shorts tab) returns the nav-bar top → same UI, ~8% shorter, app nav strip below (“not the same block screen”). |
| **Close-button semantics** (`onClose`: tab → `dismissTabCover(clearCooldown=false)` = stay in app + 4 s snooze; others → `dismiss()` = HOME eject) | controller L327 | **Intentional, kept** (audit trail §4). Not the reported complaint; still sensible full-screen: Close → nav becomes tappable → leave, or 4 s peek then re-cover. Full parity (Close=HOME) would be a one-line follow-up if the owner ever wants it. |

Why “gate fires but strip” is the right diagnosis for symptom 1 (not “never fires”): the
Home false positive you reported **proves the `reel_*` ids exist in your build’s tree**
(knownIds found them); V8’s visibility predicate then filtered the invisible preloads.
During real playback the same ids are found and visible → the gate fires → the only
thing left to be wrong is the geometry. Residual safety net: if anything still misses,
one `adb logcat -s SafeMeA11y` line (`via=` / probe-miss) names it.

## 2. The fix — social tab covers always full-screen (~8 lines, 2 files)

1. **Service L2222:** `BlockOverlayController.show(this, pkg, label, "socialTab")` —
   drop the `coverAboveY` argument (default null → `MATCH_PARENT`, exactly what
   keyword/schedule/socialWhole already do). `coverAboveY` stays in the launched-log line.
2. **Service L2428 (watch):** `refitTabCover(null)` — provable no-op (refit returns early
   when `lp.height == wanted`, verified L586); kept as OEM-quirk safety net.
3. **Gate (2 return sites):** L2 hits return `TabHit(null, matchedVia)` — fullscreen
   player ⇒ fullscreen cover; the value can never mislead a future caller. L1 still
   computes `coverAboveY` (log-only). Controller’s scoped machinery stays intact and
   dormant — re-enabling scoping later = one argument (scalability preserved).

**Ripple verified safe this round:** the wake-rebuild path `reattachOverlay` reuses
`lastCoverAboveY` ([H2], controller L465/L477) — with null stored, a rebuilt tab cover
re-attaches full-screen, consistent with the new design. No controller change needed.

## 3. Zero-influence proof (grep-verified this round, not asserted)

- `BlockOverlayController.show` call sites: keyword (L2166), schedule (L2186),
  socialWhole (L2192) — **none** passes `coverAboveY`; only socialTab did (L2222).
- `refitTabCover`: exactly one caller (watch, L2428). `TabHit` in tests: **0** references.
- Detection stack (L1/L1b/L2/knownIds/visibility predicate/L2b/height cap), whole-app
  family gate, watch dismiss semantics, cooldown/snooze, prefs/VM/screen: untouched.
- Suite 356 stays green (nothing pins the changed values); gates: `testDebugUnitTest` +
  `lintDebug` + `assembleRelease` + DEX marker. Commit **local only**.

## 4. Behavior after the fix (owner decisions on record)

| Scenario | Cover | Exit |
|---|---|---|
| Short from Home (any status-bar state) | **Full block screen, immediately** | BACK / app-switch → ≤1 probe; Close → 4 s in-app snooze |
| Shorts / Reels / Spotlight tab (incl. “directly” on app open) | **Identical full block screen to every other gate** | same |
| Nav while tab-covered | Covered — same as every other block screen (this request overrides the old nav-usable design; one-argument revert if ever wanted) | Close frees the nav (snooze) |
| Home feed / shelf visible / “…shorts…” title card | No cover (V8 guards unchanged) | — |
| Wake / rebuild while covered | Full-screen re-attach ([H2] with null) | — |

## 5. Execution order

1. Service: L2222 show-arg drop + L2428 `refitTabCover(null)`.
2. Gate: 2 L2 return sites → `TabHit(null, matchedVia)`.
3. Full gate (356 green, lint, release, DEX) → local commit. **No push/merge without explicit command.**
4. Device checklist: Short from Home → instant FULL block screen; Shorts tab → the same
   screen as a launch block; Close → 4 s snooze then re-cover; BACK/app-switch → dismiss;
   Home browsing → zero covers. Fallback: one logcat line names any residual.

---

## Addendum — implementation record (2026-09-16)

Shipped exactly as planned: service show-call drops `coverAboveY` (social tab covers now
MATCH_PARENT — identical block screen to every other gate); watch `refitTabCover(null)`
(provable no-op safety net); gate L2 returns `TabHit(null, matchedVia)` at both sites;
`TabHit` KDoc annotated (coverAboveY retained for diagnostics / dormant scoping).
Close-button semantics intentionally unchanged (tab Close = 4 s in-app snooze).

Gate: **356 tests / 0 failures** (nothing pinned the changed values — as predicted),
lint green, `assembleRelease` green. DEX-verified: ` via=`, `reel_watch_fragment_root`,
`nav click fired`, `social fast lane`, `social tab gate launched` all present.
Same signing key (`347be353…`) → direct update on the test device.
APK: `/home/user/SafeMe-0.1.0-release.apk`, SHA-256 `b77e8b99…`. **Not pushed, not merged.**
