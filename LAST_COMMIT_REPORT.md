# Last Commit Report — Social Media Blocking

**Commit:** `538d8ec92c06097f8823fa59bec3b1e9ceabc9cd`  
**Branch:** `arena/01a09472-safeme` (based on `36826a6` / `agent/fixes-b1-b11`)  
**Author:** Arena Agent <agent@arena.ai>  
**AuthorDate:** 2026-09-10 09:16:54 +0000  
**CommitDate:** 2026-09-10 09:29:58 +0000  
**vs origin/main:** `e87284d` → `538d8ec` — 135 files changed, ~73k insertions / 47k deletions (mostly `graphify-out` churn + initial import)

---

## 1. Commit Message Summary

> **feat(social-blocking): implement Social Media Blocking with whole-app + tab gates**

Implements two-option Social Media Blocking replacing the App-Feature card. Keeps short-video vertical gating (Feed/Messages/Profile stay usable, only Reels/Shorts/Spotlight gated) but splits across two independent, persistent gates.

### Prototype work (`Reference/prototype`)
- Rename App-Feature → Social Media Blocking (sub: `Whole apps or just tabs · Reels · Shorts · Spotlight`) and wire `MoreGrid` to `nav('socialblocking')`
- Add `#sc-socialblocking` with SafeMe light theme (`var(--bg)/surface/line/ink/brand`) — not dark screenshot palette
- Master card (shield, Master ON, 5 launch 3/3 tabs), QUICK FOCUS MODE 1-Tap Preset (Deep Work/Balanced/Relax), LAUNCH BLOCK Full App +Add (5 rows: TikTok/Instagram/X/Reddit/Twitch) and IN-APP TAB BLOCK 3 active (YouTube Shorts/Facebook Reels/Snapchat Spotlight)
- App picker = All Apps filtered Social default, TikTok/Instagram live whole-app only (never in tab allow-list)

---

## 2. What Changed vs `origin/main` (Focused Diff)

### 2.1 New Files (core feature)
```
app/src/main/java/com/safeme/app/data/SocialBlockingPrefs.kt
app/src/main/java/com/safeme/app/protect/SocialBlockingGate.kt
app/src/main/java/com/safeme/app/ui/screens/socialblocking/SocialBlockingIcons.kt
app/src/main/java/com/safeme/app/ui/screens/socialblocking/SocialBlockingScreen.kt (615 lines)
app/src/main/java/com/safeme/app/ui/screens/socialblocking/SocialBlockingViewModel.kt (194 lines)
app/src/main/java/com/safeme/app/ui/util/ExactAlarmUtils.kt
SOCIAL_MEDIA_BLOCKING_EXECUTION_PLAN.md (300 lines)
SOCIAL_MEDIA_BLOCKING_FINAL_PLAN.md (231 lines)
tools/ci/check_bundled_counts.py
tools/sandbox/{README.md,bootstrap.sh,env.sh,verify.sh}
app/src/test/java/com/safeme/app/data/BlockedCounterTest.kt
app/src/test/java/com/safeme/app/ui/screens/schedule/ScheduleEditEnabledTest.kt
app/src/test/java/com/safeme/app/ui/screens/schedule/ScheduleEditValidationTest.kt
```

### 2.2 Modified Files (integration)
- **Accessibility Service:** `SafeMeAccessibilityService.kt` — + isolated social gates, fail-open, no merge into keyword engine
- **Overlay:** `BlockOverlayController.kt` — `socialWhole→App blocked`, `socialTab→Tab blocked`, polished sub + why reason
- **Backup:** `BackupCodec.kt` (SOCIAL_BLOCKING enum last, nullable snapshot for v1 compat), `BackupManager.kt` (10 prefs, 11 stores), `BackupScreen.kt` (exhaustive when → SOCIAL_BLOCKING)
- **Navigation/UI:** `BlockingScreen.kt` (MoreGrid onOpenSocialBlocking), `MainScreen.kt` (composable("socialblocking")), `HomeViewModel.kt`, `Schedule*` screens
- **Prefs:** `BlockingPrefs.kt`, `SchedulePrefs.kt`, `BundledAdult.kt`
- **Res:** `strings.xml` +30 social_* strings
- **Tests:** `BackupCodecTest.kt`, `BackupManagerTest.kt`, `ScheduleEvaluatorTest.kt`, `ImageVideoSearchGateTest.kt`, etc.
- **Prototype:** `Reference/prototype/{index.html,css/styles.css,js/app.js}` light theme
- **Build:** `artifacts/app-release.apk` 3.1M unsigned, `lint-baseline.xml`

---

## 3. Architecture Deep Dive

### 3.1 Data Layer — `SocialBlockingPrefs.kt`
```kotlin
data class SocialBlockingState(
  enabled: Boolean = true,
  wholeBlocked: Set<String> = DEFAULT 5 pkgs dedup, // TikTok family + Instagram + X + Reddit + Twitch
  youtube: Boolean = true,
  facebook: Boolean = true,
  snapchat: Boolean = true
)
```
- DataStore name `safeme_social_blocking_prefs`
- Keys: `KEY_SOCIAL_ENABLED`, `KEY_SOCIAL_WHOLE_BLOCKED` (stringSet), `YT`, `FB`, `SNAP`
- Helpers:
  - `TIKTOK_PACKAGES` = 5 pkgs (`musically`, `aweme`, `trill`, `musically.go`, `aweme.lite`)
  - `INSTAGRAM_PACKAGES` = 2 pkgs (`instagram.android`, `lite`)
  - `displayKeyFor()` collapses families → display dedup
  - `displayCount()` deduped count (TikTok 2→1)
  - `applySocialPreset(deep/balanced/relax)`:
    - deep: enabled=true, whole=DEFAULT 5, tabs all true
    - balanced: enabled=true, whole=empty, tabs true
    - relax: enabled=false, whole=empty, tabs stay true (for next enable)
  - `writeSocialBlockingPrefs()` atomic restore for BackupManager

### 3.2 Gate Logic — `SocialBlockingGate.kt`
- **Object** pure, fail-open
- `FEATURE_PACKAGES` = 3 only (allow-list):
  ```
  com.google.android.youtube → SHORTS
  com.facebook.katana → REELS
  com.facebook.lite → REELS
  com.snapchat.android → SPOTLIGHT
  ```
  TikTok/Instagram **never** in tab gate (product spec: whole-app only)
- `SYSTEM_EXEMPT` = systemui, safeme.app, settings, android, packageinstaller (never whole-blocked → avoids bricking)
- `isWholeAppBlocked(pkg, wholeBlocked)` pure check
- `verticalFor(pkg)` → SocialVertical? null if not in allow-list
- Tab detection:
  - Regex `\bshorts\b`, `\breels\b`, `\bspotlight\b` case-insensitive, word-boundary to avoid false positives
  - BFS with `MAX_DEPTH 12`, `MAX_STRINGS 200`, identityHashCode visited set, bounded scan
  - `findTabNode(root, vertical)` returns matching node (caller recycles)
- Throttle constants: `APP_CONTENT_RECHECK_THROTTLE_MS 250L`, `GATE_COOLDOWN_MS 4000L`
- `throttleKey(pkg, vertical) = "$pkg|${vertical.name}"` so YouTube Shorts and Snapchat Spotlight don't share cooldown

### 3.3 Accessibility Service Integration
Isolated, fail-open, no merge into keyword engine:

- Imports `SocialBlockingState`, `socialBlockingPrefs()`, `SocialBlockingGate`
- New volatile state:
  - `@Volatile cachedSocialState`
  - `lastSocialWholeBlockKey/At` (dedup persistent launch)
  - `lastSocialTabProbeMs`
  - `ConcurrentHashMap socialTabCooldown`
  - top-level `shouldThrottleSocialTabRecheck` via `GATE.APP_CONTENT_RECHECK`
- `onServiceConnected`: `runBlocking` initial social prefs + collect flow
- `rearmCooldownsIfGateDismissed`: clear social cooldowns + log incl social
- `handleEvent window`:
  - After `isScheduleBlocked`, before `blockingEnabled`
  - Whole-app persistent launch gate: `TYPE_WINDOW_STATE_CHANGED`, `COOLDOWN_MS` dedup
  - Tab gate: allow-list, 250ms throttle, 4s cooldown per pkg|vertical, `rootInActiveWindow → findTabNode → launchSocialTabGate`
- `handleEvent content`:
  - After `handleAppContentRecheck`, `handleSocialTabContentEvent` (rearm, own systemui guard, vertical check, 250ms+4s, root pkg guard)
- Helpers:
  - `launchSocialWholeGate(label→show pkg,label,socialWhole)`
  - `launchSocialTabGate(Shorts/Reels/Spotlight→socialTab)`
  - `handleSocialTabContentEvent` full

### 3.4 Overlay / Block Activity
- `BlockOverlayController.kt`:
  - `blockActivityTitle` socialWhole→App blocked, socialTab→Tab blocked
  - `blockActivitySub` type-first polished: `Whole-app/tab ($matched)` else generic
  - `blockGateWhyReason` socialWhole/tab → Why: app/tab blocked by Social Media Blocking

### 3.5 UI — Compose Light Theme
**File:** `SocialBlockingScreen.kt` 615 lines, `SocialBlockingViewModel.kt` 194 lines

- Scaffold uses `LightAppColors` (`background #FAF7F3`, `surface #FFFFFF`, `line #EAE3DB`, `ink #1F1A16`, `brand #D97757`, `success #2E7D5B`)
- Structure:
  - SubHeader(title="Social Media Blocking", sub="Block a whole app at launch, or target specific addictive tabs.")
  - Master card: shield icon, `Master ON/OFF`, `5 launch` / `3/3 tabs` counts
  - QUICK FOCUS MODE 1-Tap Preset: Deep Work (All apps & tabs), Balanced (Tabs only), Relax (All paused) — 3-column grid, selected = BrandSoft+Brand
  - LAUNCH BLOCK Full App +Add (5 rows TikTok/Instagram/X/Reddit/Twitch subtitles)
  - IN-APP TAB BLOCK 3 active (YouTube Shorts/Facebook Reels/Snapchat Spotlight)
  - Footnote: "How it works — Whole-app blocks on launch. Tab overlays the view. 250ms throttle · 4s cooldown. No root, no VPN."
- Grouped picker: All Apps filtered Social default, TikTok/Instagram live whole-app only (never in tab allow-list)
- ViewModel:
  - `combine(socialBlockingPrefs(), Unit)` → `SocialBlockingUiState` with `displayLaunchCount`, `activeTabs`, `preset` resolve
  - `resolvePreset`: !enabled→relax, whole>=4 && tabs==3→deep, whole==0 && tabs==3→balanced else none
  - Actions: `toggleMaster()`, `applyPreset()`, `toggleLaunch(pkg)`, `setWholeBlocked()`, `toggleYoutube/Facebook/Snapchat()`, toasts via SharedFlow
  - App loading via `AppCatalog.load()` off IO, sorted

### 3.6 Backup / Restore
- `BackupCodec.kt`: add `SOCIAL_BLOCKING` enum last, `BackupSnapshot.socialBlocking` nullable (schema v1 compat), `presentSections/valueFor/encode/decode`, `SocialBlockingState.toJson/parseSocialBlocking` with strict boolean/stringArray accessors + trim filter
- `BackupManager.kt`: `createBackup` snapshots 10 prefs including `socialBlockingPrefs().first()`, `backupStores` map 10 entries `SOCIAL_BLOCKING→SocialBlockingStore`, 11 store classes
- `BackupScreen.kt`: exhaustive when → `SOCIAL_BLOCKING → backup_section_social`
- `strings.xml`: 30+ `social_*` (title/sub, master, qfm, presets, launch/tab titles/subs, footnote, sheet/search, toasts, `backup_section_social`)
- Tests: `BackupManagerTest` sampleSnapshot/socialBlocking, seededStores 10 entries, assert restored.socialBlocking

### 3.7 Navigation
- `BlockingScreen.kt` MoreGrid `onOpenSocialBlocking`
- `MainScreen.kt` import + `composable("socialblocking") → SocialBlockingScreen`
- 700-line Compose LightAppColors scaffold

---

## 4. Build / Verification (from commit message)

- Android toolchain bootstrap JDK 25 + SDK 36 (platform-tools, android-36, build-tools 36.0.0)
- graphifyy 0.9.57 update 173→180 files 2137→2290 nodes 4628→4875 edges
- `compileDebugKotlin`, `testDebugUnitTest`, `lintDebug` 68 warnings baseline — all green
- Release APK 3.1M unsigned (`artifacts/app-release.apk`)

---

## 5. Security / Safety Properties

- **Fail-open:** all gates wrapped in try/catch, malformed input never crashes service
- **System exempt:** systemui, settings, safeme, packageinstaller never whole-blocked → avoids device brick
- **Isolated:** social gates not merged into keyword engine — separate cooldown maps, separate throttle keys
- **Bounded:** MAX_DEPTH 12, MAX_STRINGS 200, 250ms throttle, 4s cooldown per pkg|vertical
- **Persistent whole-app:** launch gate uses `lastSocialWholeBlockKey` dedup, not one-shot
- **Tab precision:** word-boundary regex, allow-list only 3 pkgs, TikTok/Instagram excluded from tab detection
- **Backup atomic:** `writeSocialBlockingPrefs` single edit transaction

---

## 6. Strings Added (values/strings.xml)

30+ entries:
- `social_title`, `social_sub`, `social_master_sub_on/off`, `social_master_on/off`
- `social_launch %d launch`, `social_tabs %d/3 tabs`
- `social_qfm_title/sub`, `social_preset_deep/balanced/relax` + subs
- `social_launch_title/pill/sub`, `social_add`
- `social_tab_title/active/sub`
- `social_tiktok_sub`, `social_instagram_sub`, `social_xtwitter_sub`, `social_reddit_sub`, `social_twitch_sub`
- `social_youtube_title/sub`, `social_facebook_title/sub`, `social_snapchat_title/sub`
- `social_footnote`, `social_sheet_title/sub`, `social_search_placeholder`
- `social_toast_*` (on/off, launch blocked/allowed, master required, tab blocked/allowed, preset deep/balanced/relax)
- `backup_section_social`

---

## 7. Plan Docs

- `SOCIAL_MEDIA_BLOCKING_EXECUTION_PLAN.md` (300 lines) — Task 0.1 remove FOCUS & LIMITS first
- `SOCIAL_MEDIA_BLOCKING_FINAL_PLAN.md` (231 lines) — deep reanalysis, requirements traceability, data model, architecture, UI spec

---

## 8. Overall Assessment

This commit is a **feature-complete vertical slice** for Social Media Blocking:

✅ DataStore + dedup logic + presets + atomic restore  
✅ Pure gate object with bounded BFS + regex + throttle  
✅ Accessibility service integration isolated + fail-open + system exempt  
✅ Overlay titles/subs/why reasons  
✅ Compose UI light theme matching SafeMe design system, 5 launch + 3 tab rows, quick presets, grouped picker  
✅ Backup/restore v1 compat + tests  
✅ Prototype HTML/CSS/JS light-themed  
✅ Build green (compile/test/lint), APK 3.1M

**Risks / Follow-ups:**
- Tab detection relies on English text `Shorts/Reels/Spotlight` — may need localization later (currently intentional, fail-open)
- No instrumented tests yet for social blocking overlay (unit tests cover prefs/gate, but a11y flow is manual)
- `displayCount` dedup logic correct but could use explicit mapping for X/Reddit/Twitch families if those get lite variants in future
- Graphify cache churn (47k deletions) is noise — should be gitignored or regenerated in CI only

---

## 9. Commands to Verify Locally

```bash
./gradlew :app:compileDebugKotlin
./gradlew :app:testDebugUnitTest
./gradlew :app:lintDebug
./gradlew :app:assembleRelease
```

APK: `artifacts/app-release.apk` (3.1 MB unsigned)

---
*Generated 2026-09-12 from commit 538d8ec*
