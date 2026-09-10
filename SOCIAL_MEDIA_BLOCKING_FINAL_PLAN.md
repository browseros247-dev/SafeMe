# Social Media Blocking — Final Execution Plan (Deep Reanalysis)

> **Date:** 2026-09-10 · Dhaka (UTC+6)  
> **Branch:** `agent/fixes-b1-b11` → `agent/social-blocking`  
> **Prototype:** `SafeMe/Reference/prototype/index.html#sc-socialblocking` (light SafeMe theme, exact screenshot layout/text — dark screenshot colors *not* used)  
> **User directive:** Keep **layout + exact text** from screenshot, but **colors/design philosophy = SafeMe existing light system**. **Remove `FOCUS & LIMITS` top label first** (Task 0.1) before any other work. Plan-only, await `Execute`.

---

## 1. Deep Reanalysis — What We Actually Found

### 1.1 Prototype audit (`Reference/prototype`)
- `index.html` has two deliverables: `Reference/` (current) and `/tmp/SafeMe/Reference/` (ephemeral). Workspace source of truth is `SafeMe/Reference/prototype/index.html` (87 k lines, single-page shell with `.phone` 392px, `.screenwrap`, 23 screens).
- `sc-socialblocking` **already** light-themed, exact screenshot strings, no `FOCUS & LIMITS` after our interim patch (sticky bar is `BACK + FOCUS & LIMITS` peach centered — now patched to `BACK + Social Media Blocking` via `SubHeader`). Dark screenshot (`#0F1115` `#1C1E22`) was replaced by `var(--bg)`/`var(--surface)`/`var(--line)` tokens. Icons use `APPS` light pastel `bg/fg` (e.g., TikTok `#FDEEE2/#F97316`), not dark.
- `js/app.js` — `SOCIAL = {enabled:true, wholeBlocked:Set(TikTok,Instagram,X / Twitter,Reddit,Twitch), youtube:true, facebook:true, snapchat:true}` persisted `safeme_social_blocking_v1`. `renderSocial()` drives master pill (`Master ON` green), `5 launch`/`3/3 tabs`, preset highlight (`Deep Work` selected when `whole>=4 && tabs===3`), `togLaunch` (whole-app), `togSocialFeature` (tabs only), `applyPreset`, `openSocialApps` → `sheetSocialApps` (grouped `AppCatalog` via `classifyApp`). Throttle `250ms` + `4000ms pkg|vertical` distinct keys. `node --check` passes.
- `css/styles.css` tokens: the **only** canonical colors are `LightAppColors`/`DarkAppColors` in `ui/theme/Color.kt`. Prototype `css/styles.css` mirrors them (`--brand #D97757`, `--bg #FAF7F3`, `--surface #FFFFFF`, etc.). No custom dark for this screen — correct.

### 1.2 Kotlin audit (`app/src/main`)
- **Theme:** `ui/theme/Color.kt: LightAppColors` (`brand 0xFFD97757`, `brandSoft 0xFFFBEFE8`, `background 0xFFFAF7F3`, `surface 0xFFFFFFFF`, `line 0xFFEAE3DB`, `ink 0xFF1F1A16`, `success 0xFF2E7D5B`) + `DarkAppColors` + `LocalAppColors`. Typography `SerifFamily/Source_Serif_4` for `displayLarge/Medium`, `UiFamily` for body. CompositionLocal — **must** be used, no hard-coded hex in Compose.
- **Navigation:** `ui/screens/main/MainScreen.kt` `NavHost` with `blocking`, `keywords`, `vpn`, `titleblock`, `otherfeatures`, etc. `BlockingScreen.kt: MoreGrid` has 6 tiles; provisional `App-Feature` tile is `toast('Coming soon')` → needs `onOpenSocialBlocking`.
- **App discovery:** `data/AppCatalog.kt` — `load()` queries `PackageManager` (launcher), dedupes, excludes own package, `categorize()` pure with fixed `CATEGORY_ORDER` (Social, Video & Music, …). `GroupedAppPicker.kt` already groups/searches exactly like prototype.
- **Accessibility engine:** `service/SafeMeAccessibilityService.kt` (2280 lines) — serial `eventScope (limitedParallelism(1))`, `cachedState: BlockingPrefsState`, `cachedPuEnabled`, wall-clock `lastBlockKey/lastBlockAt` cooldown `4000ms`, `rootInActiveWindow` walks capped `MAX_DEPTH 12`/`MAX_STRINGS 200`, fail-open everywhere, PU watchdog `250ms` cadence (`PU_WATCHDOG_INTERVAL_MS = 250L`). No social gating yet — insertion point is `handleEvent()` after PU then schedule then keyword engine.
- **Persistence:** All prefs are `DataStore<Preferences>` per feature (`BlockingPrefs`, `SchedulePrefs`, `VpnPrefs`, …) + `BackupManager/BackupCodec` JSON `v1` with `Jsonc` comments. New feature must follow same.
- **Other:** `BlockOverlayController`/`BlockGateActivity` is the universal overlay host (fast cover, fallback to activity if no overlay permission). `ProtectionLayers` exposes `layersActive`.

### 1.3 Gap vs. desired
- No Kotlin `SocialBlocking` data/ViewModel/Screen/Gate yet; navigation not wired; Backup not including social; no tests for social gates.
- Prototype still has interim `FOCUS & LIMITS` text in older tmp copy (now removed in workspace) — **Task 0.1 ensures removal is verified in both prototype + Kotlin**.

---

## 2. Requirements Traceability

| Screenshot text/layout (must keep exactly) | SafeMe mapping | Implementation |
|---|---|---|
| `FOCUS & LIMITS` top | **Remove** → standard `SubHeader` | Task 0.1 |
| `Social Media Blocking` + `Block a whole app at launch, or target specific addictive tabs.` | `SubHeader(title, subtitle)` | Screen header |
| Master card `Social Media Blocking / Whole apps & tabs protected` + `Master ON/OFF` + `5 launch`/`3/3 tabs` | Light card `Surface`/`Line` | Master section |
| `QUICK FOCUS MODE / 1-Tap Preset` + 3 presets `Deep Work/All apps & tabs` `Balanced/Tabs only` `Relax/All paused` | 3-column grid, selected = `BrandSoft`+`Brand` | Presets |
| `LAUNCH BLOCK / Full App / Entire app … / + Add` + 5 rows (TikTok/Instagram/X/Reddit/Twitch exact subtitles) | `Launch Block` list, `+ Add` → `GroupedAppPicker` All Apps | Launch gate |
| `IN-APP TAB BLOCK / 3 active / Keep useful functions…` + 3 rows (YouTube Shorts/Facebook Reels/Snapchat Spotlight exact subtitles) | `Tab Block` list, only 3 packages | Tab gate |
| Icons (TikTok music, Instagram camera, X, Reddit alien, Twitch, YouTube play, Facebook f, Snapchat ghost) | Use `APPS` light `bg/fg/ic` + local vectors for Twitch/Reddit | Icons |
| Toggles orange when on | `Switch` with `Brand` track when `on`, `SwOff` when off | Controls |

TikTok = `com.zhiliaoapp.musically` + `com.ss.android.ugc.trill` (2 pkgs, one display row). Instagram = `com.instagram.android`, X = `com.twitter.android`, Reddit = `com.reddit.frontpage`, Twitch = `com.twitch.android`. Tabs: `com.google.android.youtube`, `com.facebook.katana` + `com.facebook.lite`, `com.snapchat.android`. TikTok/Instagram **never** in tab gate.

---

## 3. Design Philosophy Lock

- **Palette only from `LightAppColors/DarkAppColors`** — no `#0F1115` dark screenshot. Light defaults: `background #FAF7F3`, `surface #FFFFFF`, `line #EAE3DB`, `ink #1F1A16`, `ink2 #6B625A`, `ink3 #A89E94`, `brand #D97757`, `brandSoft #FBEFE8`, `success #2E7D5B`, `successBg #E7F0EC`.
- **Typography** `SafeMeTypography`: title `Serif 26sp/700 -0.5`, sections `11.5sp uppercase 700 ink3`, body `13sp/12–14sp ink2`, captions `11–11.5sp`.
- **Shape/elevation** `RoundedCornerShape 16–20dp`, `1dp Line` border, `0–1dp` shadow, no heavy dark cards.
- **Motion** `tween 250ms` for switch, preset border.

---

## 4. Data Model

### 4.1 `data/SocialBlockingPrefs.kt` (new)
```kotlin
data class SocialBlockingState(
  val enabled: Boolean = true,
  val wholeBlocked: Set<String> = setOf(
    "com.zhiliaoapp.musically", "com.ss.android.ugc.trill",
    "com.instagram.android", "com.twitter.android",
    "com.reddit.frontpage", "com.twitch.android"
  ), // 5 display rows
  val blockYoutubeShorts: Boolean = true,
  val blockFacebookReels: Boolean = true,
  val blockSnapchatSpotlight: Boolean = true,
  val schemaVersion: Int = 1
)
enum class SocialPreset { DEEP, BALANCED, RELAX }
```
- Whole stored as **packageNames**; TikTok display row maps to 2 pkgs.
- `Context.socialBlockingDataStore: DataStore<Preferences>` name `safeme_social_blocking_prefs`, keys `enabled`, `whole` (`stringSet`), `yt`, `fb`, `snap`, `version`.
- `Flow<SocialBlockingState>` + suspend `setEnabled`, `setWholeBlocked(Set)`, `toggleWhole(displayName)`, `setFeature(Feature,bool)`, `applyPreset`.
- `displayCount = wholeBlocked display deduped (TikTok 2→1)`.

### 4.2 `AppCatalog` mapping
- No hard-coded app list; `GroupedAppPicker` discovers via `AppCatalog.load()`; selected set is `wholeBlocked` (packageNames). Extra apps added by user appear as `launch-row.extra` dynamically.

### 4.3 Backup
- `BackupCodec` JSON `socialBlocking: {enabled, wholeBlocked[], yt, fb, snap, version}`; migration: missing fields → defaults; `BackupManager` includes it in export/import.

---

## 5. Architecture & Gating

```
BlockingScreen (MoreGrid) ──onOpenSocialBlocking──▶ NavHost("socialblocking")
                                                      │
SocialBlockingScreen ◀──collectAsState── SocialBlockingViewModel ──Flow──▶ SocialBlockingPrefs (DataStore)
           │ picks via GroupedAppPicker (AppCatalog.load/groupApps)
           └─ writes ────────────────────────────────▶ SocialBlockingPrefs
                                                      │
SafeMeAccessibilityService.handleEvent() ──▶ SocialBlockingGate
        ├─ if !enabled → skip
        ├─ if pkg in wholeBlocked → BlockOverlayController.show(pkg, "whole")
        └─ else if pkg in FEATURE_PACKAGES (3 only, tiktok/instagram → null) → throttled tab node check → overlay on tab node
```

- **FEATURE_PACKAGES**: `mapOf("com.google.android.youtube"→SHORTS, "com.facebook.katana"→REELS, "com.facebook.lite"→REELS, "com.snapchat.android"→SPOTLIGHT)`.
- **Whole gate**: `TYPE_WINDOW_STATE_CHANGED`, `SYSTEM_EXEMPT` (`com.android.systemui`, `com.safeme.app`, `com.android.settings` etc. exempt for whole? Settings not exempt for whole? Whole blocks third-party launch only; system exempt.)
- **Tab gate**: throttled `250ms` (`APP_CONTENT_RECHECK_THROTTLE`) + `4000ms` `pkg|vertical` cooldown map (`ConcurrentHashMap`), finds tab label node (`Shorts/Reels/Spotlight` text, contentDescription), overlays parent, keeps scroll/FAB alive.
- Scope: service `eventScope` background serial, fail-open try/catch, `BlockOverlayController` universal.

---

## 6. UI Spec (Compose, exact screenshot structure, SafeMe light tokens)

**File:** `ui/screens/socialblocking/SocialBlockingScreen.kt`

- **Root:** `Box(background LocalAppColors.current.background)` → `Column(verticalScroll, padding 20dp/8dp + statusBarsPadding)` with `SubHeader(title="Social Media Blocking", subtitle="Block a whole app at launch, or target specific addictive tabs.", onBack)`. No `FOCUS & LIMITS`.
- **Master card:** `Surface(20dp, line 1dp)` Row: `42dp` shield `BrandSoft`/`BrandDark` + title `14.5sp/700 Ink` / subtitle `12sp Ink2` + `Switch(enabled)`.
  - Divider `Line` → Row: `Master ON/OFF` (7dp dot `Success`/`Ink3` + `12.5sp/600`) + pills `5 launch`/`3/3 tabs` (`Bg` bg + `Line` `11sp/600 Ink2`).
- **Quick Focus Mode:** header `11.5sp uppercase 700 Ink` + `11sp Ink3` → grid `3 columns 8dp`: each `Surface 16dp` 12dp pad, selected `BrandSoft` + `Brand 1.5dp` border. Content fire `🔥/⚖/☕` + title `13sp/700` + sub `11sp Ink2`.
- **Launch Block:** header `11.5sp uppercase 700` + pill `Full App` (`BrandSoft/BrandDark 10sp`) + sub `11.5sp Ink2` + end `+ Add` (`Surface/Line 12.5sp BrandDark`). List `10dp gap`: each row `Surface 16dp` 12dp pad: `42dp` icon (`APPS` light bg/fg, Twitch/Reddit custom) + title `14sp/600` + sub `11.5sp Ink2` exact + `Switch`. Default 5 + dynamic extras. Dim `alpha 0.45` when `!enabled`.
- **In-App Tab Block:** header `11.5sp uppercase 700` + pill `3 active` (`BrandSoft/BrandDark` when >0 else `Surface/Ink3`) + sub `11.5sp Ink2` → 3 rows same spec, `Switch` bound to yt/fb/snap.
- **Footnote (minimal):** `Bg` + `Line 14dp` → `How it works — Whole-app blocks on launch. Tab overlays the view. 250ms throttle · 4s cooldown. No root, no VPN.` `11.5sp Ink2`.

All `contentDescription` + `Role.Switch`, switches `animateColorAsState`, respects dark via `DarkAppColors`.

---

## 7. Navigation

- `ui/screens/blocking/BlockingScreen.kt:MoreGrid` tile `t=Social Media Blocking, s=Whole apps or just tabs · Reels · Shorts · Spotlight, icon=BlockingIcons.Shield, onClick=onOpenSocialBlocking`.
- `ui/screens/main/MainScreen.kt` NavHost: `composable("socialblocking"){ SocialBlockingScreen(onBack=navController::popBackStack) }` + deep link `open://protectyourself/socialblocking` alias `appfeature`.
- `MainActivity` intent filter unchanged.

---

## 8. Detailed Execution Plan — Ordered, Dependent, File-Level

### Phase 0 — Guard & Scaffolding (0.5d)
**0.1 Remove `FOCUS & LIMITS` (first, blocking):**
- Search `grep -R "FOCUS"` in `Reference/prototype` + `app/src`.
- Edit `SafeMe/Reference/prototype/index.html#sc-socialblocking` sticky bar: delete `<span>FOCUS & LIMITS</span>` + peach style, keep `SubHeader` div (back+title). Same in `app` stub if present.
- Verify prototype `sc-socialblocking` renders without header (screenshot crop).
- **Files:** `Reference/prototype/index.html`, `Reference/prototype/js/app.js` (if header logic), `ui/screens/socialblocking/SocialBlockingScreen.kt` (ensure no `Text("FOCUS & LIMITS")`).
- **Done when:** `grep -r "FOCUS" SafeMe` returns 0 in socialblocking.

**0.2 Scaffold files (no logic):**
- `NEW data/SocialBlockingPrefs.kt` (state + DataStore keys stub)
- `NEW ui/screens/socialblocking/SocialBlockingScreen.kt` (stub composable)
- `NEW ui/screens/socialblocking/SocialBlockingViewModel.kt`
- `NEW ui/screens/socialblocking/SocialBlockingIcons.kt`
- `NEW protect/SocialBlockingGate.kt` (object with `FEATURE_PACKAGES` map stub)

### Phase 1 — Data (1d) — depends 0.2
**1.1** Full `SocialBlockingPrefs.kt` with `Flow`, `suspend` setters, `applyPreset`, default whole 6 pkgs, `displayCount` helper.
**1.2** `BackupManager` + `BackupCodec` include `socialBlocking`; migration test `SocialBlockingPrefsTest`.
**1.3** `ActivityLog` entry for social changes (optional).

### Phase 2 — Gates (1.5d) — depends 1.1
**2.1** `SocialBlockingGate.kt` → `isWholeAppBlocked(pkg): Boolean` pure + `SYSTEM_EXEMPT` set, TikTok 2→1.
**2.2** `SocialFeatureGate.kt` → `FEATURE_PACKAGES` 3 (+lite), `shouldBlockTab(pkg, root): Vertical?` (returns null for tiktok/instagram even if sig), `throttleKey = pkg|vertical`, `shouldThrottle(now)` 250/4000.
**2.3** Integrate in `SafeMeAccessibilityService.handleEvent()`: after `cachedPuEnabled` + `isScheduleBlocked`, check `socialBlockingState.enabled` → whole → tab → `BlockOverlayController`. Keep `isOwnUiEvent`/`isExcludedFromContentEngine` guards.

### Phase 3 — ViewModel (0.7d) — depends 1.1
**3.1** `SocialBlockingViewModel` exposes `uiState: StateFlow<SocialBlockingUiState>` combining `SocialBlockingPrefs.state` + `AppCatalog.load()` for `+ Add` sheet selected set; functions `toggleMaster`, `applyPreset`, `toggleLaunch(displayName)`, `toggleFeature`, `setWholeBlocked`.

### Phase 4 — UI (1.5d) — depends 3.1
**4.1** `SocialBlockingScreen.kt` implements exact light layout (§6) using `LocalAppColors`, `collectAsState`, `Switch`, `GroupedAppPicker` sheet.
**4.2** `GroupedAppPicker` integration: `AppCatalog.load()` off main, `groupApps(query)` for search, multi-select, `Done` → `viewModel.setWholeBlocked`.
**4.3** Preset highlight logic: `whole>=4 && tabs===3 → Deep`, `whole==0 && tabs===3 → Balanced`, `!enabled → Relax`.
**4.4** Dimming when `!enabled` (`alpha 0.45` rows, switches disabled).

### Phase 5 — Navigation & Wiring (0.5d) — depends 4.1
**5.1** `BlockingScreen` tile wire + `BlockingViewModel` toast.
**5.2** `MainScreen` NavHost route + deep link.

### Phase 6 — Polish/A11y (0.5d)
ContentDescription, `Role.Switch`, dynamic font, `DarkAppColors` smoke, `statusBarsPadding`.

### Phase 7 — Testing (1d)
- Unit: `SocialBlockingPrefsTest`, `SocialWholeAppGateTest`, `SocialFeatureGateTest` (allow-list size, tiktok null, throttle).
- Compose: `SocialBlockingScreenTest` (Master toggles dims, preset highlight, + Add adds extra row).
- Service: Robolectric `AccessibilityEvent` with shadow root for tab.
- Backup round-trip.

### Phase 8 — Docs & Cleanup (0.3d)
Update `bundled-dataset.md`, `README`, prototype already light; final `grep FOCUS` check.

**Total ~6.5–7d single engineer, serial.**

---

## 9. File Checklist (new/edit)

- `Reference/prototype/index.html` — remove `FOCUS & LIMITS`, keep light exact layout (already).
- `Reference/prototype/js/app.js` — `SOCIAL` defaults (5 launch, 3 tabs, enabled true), `renderSocial` light colors (already).
- `NEW app/src/main/java/com/safeme/app/data/SocialBlockingPrefs.kt`
- `NEW app/src/main/java/com/safeme/app/protect/SocialBlockingGate.kt`
- `NEW app/src/main/java/com/safeme/app/ui/screens/socialblocking/*`
- `EDIT app/src/main/java/com/safeme/app/ui/screens/blocking/BlockingScreen.kt`
- `EDIT app/src/main/java/com/safeme/app/ui/screens/main/MainScreen.kt`
- `EDIT app/src/main/java/com/safeme/app/service/SafeMeAccessibilityService.kt`
- `EDIT app/src/main/java/com/safeme/app/data/BackupManager.kt` + `BackupCodec.kt`
- `EDIT app/src/main/java/com/safeme/app/ui/theme/Color.kt` — no change, reuse.

---

## 10. Risks & Mitigations

- **FOCUS removal missed** → Task 0.1 + final `grep` gate.
- **TikTok double-count** → display count dedupes 2 pkgs as 1.
- **Tab false positive** → bind overlay to tab node, text regex, throttle, keep scroll/FAB.
- **System app block** → `SYSTEM_EXEMPT` + `AppCatalog.load` excludes own package.
- **Dark regression** → all colors via `LocalAppColors`, test both.

---

## 11. Acceptance Criteria

- [ ] No `FOCUS & LIMITS` in `sc-socialblocking` (prototype + Kotlin).
- [ ] Light tokens only (no `#0F1115`).
- [ ] Master `ON` green dot, `5 launch`/`3/3 tabs` live, `OFF` dims and `0 launch`/`0/3`.
- [ ] Presets highlight correctly.
- [ ] 5 Launch rows exact subtitles, toggles persist; `+ Add` picks any app and adds extra row.
- [ ] 3 Tab rows only, toggles persist, `3 active` pill correct.
- [ ] Service whole blocks on launch persistent, tab overlays with 250/4000 limits.
- [ ] Backup round-trip, `./gradlew test` green, `node --check` green.

---

## 12. Ready to Execute

A single `Execute` reply will run Phase 0.1 first (removing `FOCUS & LIMITS`) then 0.2→8 in order, keeping prototype + Kotlin in sync.
