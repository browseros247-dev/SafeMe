# Social Media Blocking — V4 Fix Plan (Device-Test Feedback)

> **Date:** 2026-09-16 · **Branch:** `agent/social-blocking-fixes` (local only — no push/merge without explicit command)
> **Mode:** PLAN ONLY — awaiting `Execute`.
> **Formula (unchanged):** minimal code first, production hardening only where a named risk demands it, scalability seams already in place (`TAB_RULES.tokenHints` was reserved for exactly Fix B). **~75 production lines + ~40 test lines, 4 files edited, 0 new files.**
> **Suite baseline:** 339 tests / 0 failures (verified after v3).

---

## 1. Diagnoses (all four confirmed in code, not guessed)

### Issue 1 — "Launch block not working at all" → package-family mismatch
`SocialBlockingGate.isWholeAppBlocked` (L60–63) is **exact package match** against
`DEFAULT_WHOLE_BLOCKED` = primary packages only (`com.zhiliaoapp.musically`,
`com.instagram.android`, …). Devices carrying **Lite/Go/regional variants**
(`com.zhiliaoapp.musically.go`, `com.ss.android.ugc.aweme.lite`, `com.instagram.lite` —
common in South Asia) never match → no block, ever. The v3 icon fix made this *look*
functional: `InstalledAppIcon` is family-aware, so the card shows the installed Lite
variant's real icon with the toggle ON — while the gate blocks nothing.
Row `checked` (Screen L349: `item.pkg in wholeBlocked`) and `toggleLaunch` (exact pkg)
share the same blindness.
*Pre-check before blaming code:* the release-APK key change forced an uninstall/reinstall,
which **disables the accessibility service** — verification step 0 below covers it.

### Issue 2 — Snapchat Spotlight not blocked → L1-only detection
Detection requires a **selected** nav item (`findActiveTab`). Snapchat's nav is icon-only
and Spotlight opens a fullscreen feed; selection state is not reliably exposed → L1 never
matches. This is precisely the deferred **L2 fullscreen detection** whose slot
(`TabRule.tokenHints`) already ships empty in `TAB_RULES`.

### Issue 3 — Shorts opened from YouTube Home not blocked → same L2 gap, by design
Tapping a Short from the Home feed enters the fullscreen Shorts player with **no selected
"Shorts" nav tab** — L1 (correctly, per the anti-false-positive rule) does not fire. The
v3 plan documented this exact gap as the trigger condition for adopting L2. Trigger met.

### Issue 4 — Cards shown for uninstalled apps
`LaunchBlockSection` renders `DEFAULT_LAUNCH.forEach` unconditionally (L348); tab rows
likewise. No installed-filter exists.

---

## 2. Fixes (minimal, each mapped to its issue)

### Fix A — Family-aware whole-app blocking (~20 ln) — *the* issue-1 fix
**Gate side (primary, fixes existing installs/backups with zero migration):**
`isWholeAppBlocked` → `pkg ∈ wholeBlocked` **OR** any same-family sibling is blocked.
`SocialBlockingPrefs` exposes `familyOf(pkg): Set<String>?` (TikTok 5-pkg / Instagram 2-pkg
families already exist there; one visibility change + 4-line gate edit). Pure → unit-tested.
**UI side (consistency: shown icon = toggled thing = blocked thing):**
- Row `checked` = *any* family member ∈ wholeBlocked.
- `toggleLaunch(pkg, family)` stores/removes the **whole family list** (uninstalled members
  are inert at the gate; `displayCount` already dedupes families for the pill).
- Picker (exact installed pkgs), Select/Deselect All, extras, SYSTEM_EXEMPT: unchanged.

### Fix B — L2 fullscreen detection via the reserved `tokenHints` slot (~35 ln) — issues 2+3
Inside the **existing** `findActiveTab` BFS (no new traversal), additionally track:
1. `navBarTop` — first full-width container in the bottom 30% of the screen (already
   computed for selected hits; now captured even when no tab is selected) → scoped cover.
2. **Strong token** — a node whose `viewIdResourceName`/`className` contains any of the
   vertical's `tokenHints` **and** whose bounds cover ≥65% of screen area.

Decision order: selected tab hit (unchanged, scoped cover) → else strong token →
`TabHit(navBarTop ?: fullscreen)` → else null (**fail-open, under-block never over-block**).

Token data (one-line edits filling the reserved slot): SHORTS → `["shorts","reel"]`
(YouTube's internal codename for Shorts infrastructure is literally "reel"),
REELS → `["reel"]`, SPOTLIGHT → `["spotlight"]`.

Why ≥65% area kills the false positives: YouTube's Home Shorts *shelf* and Facebook's
Home-feed Reels *previews* carry reel-ish ids but occupy <65% of the screen; only the
actual fullscreen player qualifies. Nav remains visible+tappable when found (cover above
it), and the existing tab watch auto-dismisses on leaving the player — no new supervision
code.

**Selection robustness (+6 ln):** also probe first-level **children** for selected/checked
(some frameworks put the flag on the icon child, not the item container) — cheap insurance
for Spotlight's custom nav.

**Production diagnosability (+4 ln):** one `Log.d(TAG,…)` when a label match is rejected
(selected state, viewId prefix, bounds %) and one when L2 fires. If Spotlight still misses
on the device, **one `adb logcat -s SafeMeA11y` capture** gives the exact tree → token
tuned precisely in a single round-trip instead of guess-rebuild cycles.

### Fix C — Hide cards for uninstalled apps (~20 ln) — issue 4
- Default rows, extras rows, and tab rows render **only if any family package is installed**
  (`allApps` from the ViewModel — already loaded, no new discovery code).
- ViewModel pills stay truthful: `displayLaunchCount` = blocked ∩ visible families;
  `activeTabs` = enabled ∩ installed host app. Denominator "3" and all texts unchanged.
- **DataStore/backup untouched** — uninstalled defaults simply aren't rendered; their
  inert set entries never affect the gate.

---

## 3. Alternatives considered and rejected (per the minimal-code mandate)

| Alternative | Why not |
|---|---|
| Storing installed variants at first screen open (prefs migration) | Gate-side family match achieves the same with no migration, works for restored backups too |
| Content-desc/scroll-structure heuristics for L2 | Unreliable across versions; view-id/class tokens + area bound are the stable signal, and logs cover the residual |
| Per-app detection plugins/config file | `TAB_RULES` already is the registry; a file loader is code we don't need |
| Blocking the Home feed because a Shorts shelf exists | That IS the original false-positive bug — shelf stays free by design |

## 4. Regression guards ("must not influence other features")

- `isWholeAppBlocked` is called **only** from social paths (fast lane, window path, content
  backstop, watchdog, tab-watch handoff) — verified by grep; keyword/title/URL/schedule/PU
  engines never touch it. SYSTEM_EXEMPT still evaluated first.
- L2 fires **only when no selected tab matched** — every case the shipped code blocks, it
  still blocks identically; it can only add detections on fullscreen feeds.
- Child-selection probe only widens L1 acceptance on label-matched nodes; non-matching
  trees behave exactly as before.
- Row hiding is rendering-only: prefs schema, BackupCodec, gate inputs unchanged.
- Full suite (339) must stay green; ~15 new pure tests added (family match incl. exempt
  precedence, L2 decision table, pill math).

## 5. Execution order & verification

| # | Step | Files |
|---|---|---|
| 1 | Fix A: `familyOf` + gate + VM toggle + row checked | `SocialBlockingPrefs.kt`, `SocialBlockingGate.kt`, `SocialBlockingViewModel.kt`, `SocialBlockingScreen.kt` |
| 2 | Fix B: L2 in `findActiveTab` + tokenHints data + child-selected probe + 2 log lines | `SocialBlockingGate.kt` |
| 3 | Fix C: installed-filter on rows + pill math | `SocialBlockingScreen.kt`, `SocialBlockingViewModel.kt` |
| 4 | Tests: extend `SocialBlockingGateTest` (family, L2 table), VM pill tests if pure-extractable | `app/src/test/**` |
| 5 | Gate: `:app:testDebugUnitTest` (≥339 green) + `:app:lintDebug` + `:app:assembleRelease` (fresh local-key APK) | — |
| 6 | Commit locally. **No push/merge without explicit command.** | — |

**Device verification protocol (for you, after installing the new APK):**
0. *Pre-check:* Settings → Accessibility → SafeMe **enabled** (reinstall disables it!);
   Social screen master **ON**.
1. Launch installed TikTok/Instagram (any variant) → block cover while app starts; logcat:
   `social fast lane: gating …` or `social watchdog: gating …`.
2. YouTube: Home feed + Shorts shelf → free; tap a Short → fullscreen cover; back → free
   instantly; Shorts **tab** → scoped cover, nav tappable.
3. Snapchat → Spotlight → cover; leave → cover gone. If it misses: capture
   `adb logcat -s SafeMeA11y` while opening Spotlight — the rejection log will name the
   exact node fields to tune (one round-trip fix).
4. Cards for apps you don't have are gone; pills match visible rows.
5. Regression pass: keyword block, schedule block, PU Settings gate unchanged.
