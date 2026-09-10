# Social Media Blocking — Detailed Execution Plan (SafeMe)

> **Status:** Plan-only — awaiting `Execute` confirmation  
> **Date:** 2026-09-10 (Asia/Dhaka)  
> **Prototype source of truth:** `/home/user/SafeMe/Reference/prototype/index.html` → `sc-socialblocking` (light SafeMe theme, exact screenshot layout+text, dark screenshot colors **not** used)  
> **Request:** Remove `FOCUS & LIMITS` top label first, then implement screen properly keeping SafeMe’s existing design philosophy (light palette, tokens, typography, radius, shadows) and only reusing layout/text from screenshot.

---

## 0. Summary

We will replace the placeholder `App-Feature` card on `Blocking` with a first-class **Social Media Blocking** screen that matches the screenshot’s **layout + exact strings** but is rendered entirely in SafeMe’s **light design system**:

- `Background #FAF7F3 / Surface #FFFFFF / Line #EAE3DB / Ink #1F1A16 / Ink-2 #6B625A / Brand #D97757 / BrandSoft #FBEFE8`
- No dark `#0F1115` from screenshot, no `FOCUS & LIMITS` header (Task 0.1).
- Two independent gates share one master switch: **(1) Launch Block — whole-app persistent launch gate** and **(2) In-App Tab Block — tab-only overlay** for exactly `YouTube Shorts / Facebook Reels / Snapchat Spotlight` (TikTok & Instagram **must stay under whole-app**).

Prototype already shows the *exact* desired layout in SafeMe light colors. This plan makes it production Kotlin/Compose with DataStore, ViewModel, Accessibility gating, presets, throttling, navigation, and tests.

---

## 1. Goals & Non-Goals

### Goals
- Pixel-close to screenshot layout/text, but 100% SafeMe theming (light, warm, serif title, 16–20dp radius, `1dp line` borders).
- One master switch that pauses both gates; independent of global `blockingEnabled` (per spec).
- **Quick Focus Mode**: 1-tap presets `Deep Work (All apps & tabs) / Balanced (Tabs only) / Relax (All paused)`.
- **Launch Block**: `Full App` — any installed app, picker = All Apps, default 5 pinned (`TikTok, Instagram, X / Twitter, Reddit, Twitch`) + user-added extras. Persistent gate (no back-stack slip).
- **In-App Tab Block**: only `YouTube (com.google.android.youtube) / Facebook (katana + lite) / Snapchat (com.snapchat.android)` — tab-only overlay, keeps rest of app.
- Persisted, atomic, versioned via DataStore + included in Backup.
- Accessible, tested, covered by unit + instrumentation tests.

### Non-Goals
- No new backend, no VPN/DNS changes.
- Do **not** add TikTok/Instagram to tab gate (even if they show reels).
- No `FOCUS & LIMITS` header (removed).
- No dark-mode redesign — dark mode is automatic via `DarkAppColors` but not a separate spec.

---

## 2. Current State Audit

### Prototype (`Reference/prototype`)
- `index.html#sc-socialblocking` already light-themed, exact strings, 3 preset cards, 5 launch rows, 3 tab rows, `+ Add` opens `sheetSocialApps` (All Apps grouped by `AppCatalog.CATEGORY_ORDER`).
- `js/app.js: SOCIAL = {enabled:true, wholeBlocked:Set(5), youtube:true, facebook:true, snapchat:true}` persisted `safeme_social_blocking_v1`; `renderSocial()` handles master pill, `5 launch`/`3/3 tabs`, preset highlight (`Deep Work` selected when `whole>=4 && tabs===3`), `togLaunch`, `togSocialFeature`, `applyPreset`, `openSocialApps`.

### Kotlin App (`app/src/main/java/com/safeme/app`)
- `ui/screens/blocking/BlockingScreen.kt` — `MoreGrid` still points at provisional `App-Feature` navigation; needs `onOpenSocialBlocking`.
- `data/AppCatalog.kt` — single source of truth, `load()` + `groupApps()` already used by `GroupedAppPicker.kt`.
- `ui/theme/Theme.kt` — `LightAppColors`/`DarkAppColors`, `LocalAppColors` used everywhere.
- `service/SafeMeAccessibilityService.kt` + `protect/*` — accessibility is the gating point; no social-specific gating yet.
- No `data/SocialBlockingPrefs.kt` yet; Backup (`data/BackupManager.kt` + `BackupCodec.kt`) will need to include new prefs.

---

## 3. Scope — What We Will Build vs. Remove

### Keep from screenshot (exact)
- Title `Social Media Blocking`, subtitle `Block a whole app at launch, or target specific addictive tabs.`
- Master card text `Whole apps & tabs protected`, pills `Master ON/OFF`, `5 launch`, `3/3 tabs`.
- Section headers: `QUICK FOCUS MODE`/`1-Tap Preset`, `LAUNCH BLOCK`/`Full App`/`Entire app is blocked before opening.`/`+ Add`, 5 launch rows with exact subtitles, `IN-APP TAB BLOCK`/`3 active`/`Keep useful functions, block endless feeds & reels.` + 3 tab rows with exact subtitles.
- Layout: sticky top bar → title → master card → 3-column presets → launch list → tab list. No extra hero, no `How it works` two-column (replaced by minimal footnote in prototype).

### Remove first (Task 0.1)
- **Top centered label `FOCUS & LIMITS`** — entire `<span>FOCUS & LIMITS</span>` in the sticky bar. Replace sticky bar with standard `SubHeader` (`Back + Social Media Blocking` + subtitle `Block a whole app…`) matching `TitleBlockScreen`/`DnsVpnScreen` pattern. No other header.

### Keep SafeMe philosophy (not screenshot dark)
- Background `Background`, cards `Surface` + `Line` border `1dp`, radius `16–20dp`, shadow `0 1dp 2dp rgba(31,26,22,.05)`.
- Typography: `SafeMeTypography`, title `serif 26sp/700`, sections `11.5sp uppercase 700 ink-3`, subtitles `11.5–13sp ink-2`.
- Accent only on selected preset (`BrandSoft` bg + `Brand` `1.5dp` border) and `Full App`/`3 active` pills (`BrandSoft`/`BrandDark`), switches `Brand` when on.

---

## 4. Design Decisions & Rationale

| Decision | Rationale |
|---|---|
| Master is **independent** of `BlockingPrefs.blockingEnabled` | Spec: Social gate should pause without disabling global keyword/VPN blocking. Mirrors prototype `SOCIAL.enabled`. |
| `wholeBlocked: Set<String>` stores **packageNames**, not display labels | Handles renames, locale; `X / Twitter` display ↔ `com.twitter.android` package. Extra apps (beyond 5) supported dynamically. |
| Tab gate allow-list = **exactly 3 packages** (`youtube`, `facebook.katana`+`lite`, `snapchat`) + `tiktok`/`instagram` explicitly return `null` | Prevents scope creep; keeps infinite TikTok feed under whole-app as required. |
| **Throttling 250ms + 4000ms cooldown per `package|vertical`** distinct keys | Avoids overlay flicker on scroll, saves battery; matches proposal `SOCIAL_FEATURE_THROTTLE`. |
| `Persist whole + tabs + master atomically` via DataStore `safeme_social_blocking_prefs` | Crash-safe, included in backup JSON. |
| Light theme only spec — dark via `DarkAppColors` auto | No duplicate dark screenshot; keeps design system single-source. |
| `FOCUS & LIMITS` removed → standard `SubHeader` | Matches every other blocking sub-screen, reduces top chrome, fixes user complaint. |

---

## 5. Data Model & Persistence

### New file: `data/SocialBlockingPrefs.kt`
```kotlin
data class SocialBlockingState(
  val enabled: Boolean = true, // screenshot default Master ON
  val wholeBlocked: Set<String> = setOf(
    "com.zhiliaoapp.musically", // TikTok (primary)
    "com.ss.android.ugc.trill", // TikTok alt
    "com.instagram.android",
    "com.twitter.android",
    "com.reddit.frontpage",
    "com.twitch.android" // Twitch — maps to 5 display names (X/Twitter single pkg)
  ),
  val blockYoutubeShorts: Boolean = true,
  val blockFacebookReels: Boolean = true,
  val blockSnapchatSpotlight: Boolean = true
)
```
- **Display mapping**: `TikTok ↔ {musically, trill} ` counted as one row; toggle `TikTok` adds/removes **both** packages.
- **DataStore**: `Context.socialBlockingDataStore` → `DataStore<Preferences>` key `safeme_social_blocking_prefs`, keys: `enabled_bool`, `whole_set_stringSet`, `yt_bool`, `fb_bool`, `snap_bool` + `schema_version_int = 1`.
- **Flows**: `state: Flow<SocialBlockingState>`; `suspend fun setEnabled()`, `toggleWhole(pkg)`, `setFeature(FEATURE, bool)`, `applyPreset(Preset)`.
- **Backup**: add `socialBlocking` field to `BackupCodec` JSON; version bump `v1` migration keeps defaults for missing fields.

### Preset enum
```kotlin
enum class SocialPreset { DEEP, BALANCED, RELAX }
fun SocialBlockingState.applyPreset(p: SocialPreset) = when(p){
  DEEP -> copy(enabled=true, wholeBlocked = defaultWholeSet, blockYoutubeShorts=true, blockFacebookReels=true, blockSnapchatSpotlight=true)
  BALANCED -> copy(enabled=true, wholeBlocked = emptySet(), blockYoutubeShorts=true, blockFacebookReels=true, blockSnapchatSpotlight=true)
  RELAX -> copy(enabled=false)
}
```

---

## 6. Architecture

```
UI (Compose)
  SocialBlockingScreen.kt  ←→  SocialBlockingViewModel.kt
                                   ↕ Flow + suspend
                              SocialBlockingPrefs.kt (DataStore)
                                   ↕
                        SafeMeAccessibilityService.kt
                             ↙              ↘
              SocialWholeAppGate.kt   SocialFeatureGate.kt
                    (FEATURE_PACKAGES map, 250ms throttle, 4000ms cooldown)
```

- **Whole-app gate**: `onWindowStateChanged`/`onAccessibilityEvent(TYPE_WINDOW_STATE_CHANGED)` → `if(enabled && wholeBlocked.contains(event.packageName)) launch BlockGateActivity` — persistent, `FLAG_ACTIVITY_NEW_TASK|CLEAR_TASK`, no `finish()` slip.
- **Tab gate**: if `package in FEATURE_PACKAGES` → lightweight `findFocus` + node text regex for tab labels (`Shorts/Reels/Spotlight` localized) → overlay `View` bound to tab node parent, throttled.
- Both gates short-circuit when `!enabled` (cheap).
- `SafeMeProtectionService` restart keeps gates alive.

### Package structure (new vs. edit)

```
NEW  data/SocialBlockingPrefs.kt
NEW  ui/screens/socialblocking/SocialBlockingScreen.kt
NEW  ui/screens/socialblocking/SocialBlockingViewModel.kt
NEW  ui/screens/socialblocking/SocialBlockingIcons.kt
EDIT ui/screens/blocking/BlockingScreen.kt         (wire card)
EDIT ui/screens/main/MainScreen.kt                  (NavHost route)
EDIT service/SafeMeAccessibilityService.kt          (call gates)
EDIT protect/ProtectionLayers.kt                    (expose layersActive count)
EDIT data/BackupManager.kt + BackupCodec.kt
EDIT ui/components/GroupedAppPicker.kt (reused, no fork)
```

---

## 7. UI Spec — Compose (exact screenshot, SafeMe light theme)

**Screen:** `SocialBlockingScreen.kt` — `Scaffold` with `SubHeader(title="Social Media Blocking", subtitle="Block a whole app at launch, or target specific addictive tabs.", onBack)`, `verticalScroll`, `padding 20dp/8dp` (like `BlockingScreen`).

1. **Master card** (`Surface`, `RoundedCornerShape(20dp)`, `border 1dp Line`, `shadow 1dp`)
   - Row: `42dp` shield `BrandSoft` icon + `14.5sp/700 Ink` title + `12sp Ink-2` subtitle `Whole apps & tabs protected` • `Switch` (`Switch` with `Brand` thumb when on)
   - Divider `Line 1dp` → Row: `Master ON/OFF` (dot `Success`/`Ink3` + `12.5sp/600`) + pills `5 launch` / `3/3 tabs` (`Bg` bg + `Line` border, `11sp/600 Ink-2`)

2. **Quick Focus Mode**
   - Header: `11.5sp uppercase 700 Ink` `QUICK FOCUS MODE` — `11sp Ink-3` `1-Tap Preset` end
   - Grid `3 columns 8dp`: each `Surface` `16dp`, `12dp` padding; selected = `BrandSoft` bg + `Brand 1.5dp`, unselected = `Surface` + `Line 1dp`. Icons: `🔥/⚖/☕` (or vector from `SocialBlockingIcons`), title `13sp/700 BrandDark when selected else Ink`, subtitle `11sp Ink-2`.

3. **Launch Block**
   - Header: `LAUNCH BLOCK` `11.5sp uppercase 700` + pill `Full App` (`BrandSoft`/`BrandDark` `10sp/700`) + subtitle `11.5sp Ink-2` + end `+ Add` button (`Surface` + `Line`, `BrandDark` text, `12.5sp/600`)
   - List `10dp gap`, each row `Surface 16dp` `12dp` padding: `42dp` icon (`APPS` light bg/fg), `14sp/600 Ink` title, `11.5sp Ink-2` exact subtitle, `Switch` end. Default 5 pinned rows + `extra` rows from `AppCatalog` dynamically appended for user-added packages. Dim to `0.45` when `!enabled`.

4. **In-App Tab Block**
   - Header: `IN-APP TAB BLOCK` + pill `3 active` (`BrandSoft`/`BrandDark` when `>0` else `Surface`/`Ink3`) + subtitle `Keep useful functions…`
   - 3 rows same row spec but subtitles: `Main video search & subscriptions stay active` etc. Switches bound to `blockYoutubeShorts` etc. Dim when `!enabled`.

5. **Footer footnote** (minimal): `Bg` + `Line` `14dp` card, `11.5sp Ink-2` → `How it works — Whole-app blocks on launch. Tab overlays the view. 250ms throttle · 4s cooldown. No root, no VPN.` (no two-column grid, keeps minimal philosophy).

**States:** Loading (shimmer), Empty (`No apps yet — tap + Add. Try TikTok, Instagram`), All paused (`Master OFF` dims lists, pills show `0 launch`/`0/3 tabs`, preset `Relax` selected).

**Theming:** All colors via `LocalAppColors`; dark mode auto via `DarkAppColors` but no custom dark overrides — keeps philosophy.

**Accessibility:** `contentDescription` on switches (`TikTok launch block`), `role=Switch`, `semantics`.

---

## 8. Logic Details

- **Master** toggles `enabled`; when `false`, both gates short-circuit, UI dims, `tabActivePill` shows `0 active`.
- **Presets**: `Deep` = `enabled true` + default 5 whole + 3 tabs on; `Balanced` = `enabled true` + `whole empty` + 3 tabs on; `Relax` = `enabled false`. Toast + `ActivityLog` entry.
- **Launch toggle**: `togLaunch(name)` maps display name → `Set<package>` (TikTok → 2 pkgs, others 1), adds/removes atomically, updates `5 launch` count.
- **`+ Add`**: `GroupedAppPicker` sheet (reused), `AppCatalog.load()` + `groupApps(query)` (Social default first but All Apps searchable), multi-select, `Done` → `updateWholeBlocked(newSet)` + `ActivityLog`.
- **Tab toggles**: `setFeature(FEATURE, !current)` when `enabled`, else toast `Turn on Master first`.
- **Stats**: `wholeBlocked.size` → `5 launch` pill (TikTok 2 pkgs counted as 1 display); `gated = yt+fb+snap` → `3/3 tabs` and `3 active`.
- **Gating (service)**: Coarse `if(!enabled) return`; whole check `packageName in wholeBlocked` → `BlockGateActivity`; else if `package in FEATURE_PACKAGES` → check tab node → overlay. System packages (`com.android.systemui` etc.) exempt.

---

## 9. Service & System Integration

- `SafeMeAccessibilityService.onAccessibilityEvent`: after existing keyword/schedule checks, call `SocialBlockingGate.maybeBlock(event)`.
- `SocialWholeAppGate.kt`: `fun isWholeAppBlocked(pkg: String): Boolean` pure, unit-tested.
- `SocialFeatureGate.kt`: `FEATURE_PACKAGES = mapOf("com.google.android.youtube" to SHORTS, "com.facebook.katana" to REELS, "com.facebook.lite" to REELS, "com.snapchat.android" to SPOTLIGHT)` — `fun shouldBlockTab(pkg, rootNode): Boolean?`.
- Throttle: `ConcurrentHashMap<String, Long>` key `pkg|vertical` last overlay time; `if(now - last < 4000) skip`.
- No `WRITE_SECURE_SETTINGS` needed.

---

## 10. Navigation

- `BlockingScreen.kt`: `MoreGrid` card `t=Social Media Blocking, s=Whole apps or just tabs · Reels · Shorts · Spotlight, icon=BlockingIcons.Block, onClick=onOpenSocialBlocking` (replaces provisional `App-Feature`).
- `MainScreen.kt` / `OnboardingNavHost.kt`: add `composable("socialblocking") { SocialBlockingScreen(onBack) }`; deep link `open://protectyourself/socialblocking` alias `appfeature` for backward compat.

---

## 11. Step-by-Step Execution Order (dependencies, files, estimates)

### Phase 0 — Cleanup & Scaffolding (0.5d)
- **0.1 Remove `FOCUS & LIMITS`** — edit `Reference/prototype/index.html` sticky bar + `ui/screens/socialblocking/SocialBlockingScreen.kt` top bar (delete `Text("FOCUS & LIMITS")`), keep standard `SubHeader`. *Blocks all later UI work.* File: `index.html`, `SocialBlockingScreen.kt`.
- **0.2 Create module files** — `data/SocialBlockingPrefs.kt`, `ui/screens/socialblocking/*`, `protect/SocialFeatureGate.kt`. No logic yet, just stubs + imports.

### Phase 1 — Data Layer (1d)
- **1.1** Implement `SocialBlockingPrefs.kt` with DataStore, `defaultWholeSet` (6 pkgs), preset helper, flows.
- **1.2** Wire `BackupCodec`/`BackupManager` to include `socialBlocking` JSON, migration test.
- **1.3** Unit tests `SocialBlockingPrefsTest` (toggle, preset, persistence).

### Phase 2 — Gates (1.5d)
- **2.1** `SocialWholeAppGate.kt` pure logic + tests (system exempt, TikTok 2 pkgs).
- **2.2** `SocialFeatureGate.kt` allow-list + node matching + throttle/cooldown + tests.
- **2.3** Integrate calls in `SafeMeAccessibilityService` behind `enabled` check; manual QA with `adb shell dumpsys accessibility`.

### Phase 3 — UI (2d) — depends on Phase 1
- **3.1** `SocialBlockingViewModel.kt` — exposes `uiState: StateFlow<SocialBlockingUiState>` (enabled, whole Set, yt/fb/snap bools, launch list, tab counts, preset), handles `toggleMaster`, `applyPreset`, `toggleLaunch`, `toggleFeature`, `setWholeBlocked`.
- **3.2** `SocialBlockingScreen.kt` — compose exact light layout (master card, 3 presets grid, 5+ launch list, 3 tab list, `+ Add` → `GroupedAppPicker` sheet). Uses `LocalAppColors`, `collectAsState`, `Switch`.
- **3.3** `SocialBlockingIcons.kt` — shield, fire, scales, coffee vectors in `Brand` palette.
- **3.4** Hook `GroupedAppPicker` (+ `AppCatalog`) for `+ Add` sheet (search, multi-select, Done).

### Phase 4 — Navigation & Wiring (0.5d)
- **4.1** `BlockingScreen` card rename + nav lambda `onOpenSocialBlocking`.
- **4.2** `MainScreen` NavHost route + deep link.
- **4.3** `ProtectionLayers` layersActive includes social when `enabled && (whole non-empty || any tab on)`.

### Phase 5 — Polish & A11y (0.5d)
- **5.1** Content descriptions, switch roles, dynamic font, dark-mode smoke test, `statusBarsPadding`.
- **5.2** Empty/All-paused states, toasts (`Master on/off`, `TikTok blocked`), `ActivityLog` entries.

### Phase 6 — Testing (1d)
- **6.1** Unit: `SocialWholeAppGateTest` (whole/tiktok), `SocialFeatureGateTest` (4 pkgs, tiktok excluded, throttle).
- **6.2** UI: `SocialBlockingScreenTest` (Master toggles dims, preset highlight, + Add adds row).
- **6.3** Instrumentation: `SafeMeAccessibilityServiceTest` with shadow event (launch block vs tab overlay).
- **6.4** Manual QA checklist (see §14).

### Phase 7 — Docs & Cleanup (0.5d)
- Update `bundled-dataset.md`, `README`, prototype `index.html` already light; remove any remaining `isWholeAppBlocked` debug text from UI.

**Total ≈ 7d single engineer.**

---

## 12. Testing Strategy

- **Unit**: `AppCatalogTest` already covers grouping; add `SocialBlockingPrefsTest`, `SocialFeatureGateTest` (allow-list size 3 (+lite), tiktok null, cooldown).
- **Compose UI**: `createComposeRule` for `SocialBlockingScreen` — assert `Master ON` text toggles, `5 launch` updates after `+ Add` selects `Chrome`, preset `Balanced` clears whole but keeps tabs.
- **Service**: Robolectric `AccessibilityEvent` with `packageName` + mock `AccessibilityNodeInfo` for tab detection.
- **Backup**: round-trip JSON with social field.

---

## 13. Risks & Mitigations

| Risk | Mitigation |
|---|---|
| `FOCUS & LIMITS` removal missed in one place | Grep `FOCUS` in `Reference/prototype` + `app/src`; plan Task 0.1 verifies. |
| TikTok 2 packages double-counted in `5 launch` | Display count dedupes TikTok pair as 1; DataStore stores 6 pkgs but UI shows 5. |
| Tab gate false positive on non-reels screen | Bind overlay to tab node parent + text regex + throttle; keep `Other` scroll alive. |
| `+ Add` adds system app that shouldn’t be blockable | `AppCatalog.load` already excludes `ownPackage`; gate exempts `com.android.systemui`. |
| Dark-mode regression (light spec only) | All colors via `LocalAppColors` with `DarkAppColors` overrides; test both. |

---

## 14. Acceptance Criteria / QA Checklist

- [ ] No `FOCUS & LIMITS` string anywhere in `SocialBlockingScreen` or prototype `sc-socialblocking`.
- [ ] Master `ON` → `Master ON` green dot, `5 launch`/`3/3 tabs` update live; `OFF` dims lists to `0.45` and pills `0 launch`/`0/3`.
- [ ] Presets: `Deep Work` selects when `whole>=4 && tabs 3/3`, `Balanced` when `whole 0 && tabs 3/3`, `Relax` when `!enabled`.
- [ ] Launch: 5 pinned rows exact subtitles, toggles persist; `+ Add` picks any app (search `instagram`), `Done` adds extra row; TikTok toggle adds/removes both packages.
- [ ] Tabs: only 3 rows exactly, toggles persist, `3 active` pill reflects `enabled?gated:0`.
- [ ] Service: whole-app blocks on launch (persistent, not back), tab overlays only on Shorts/Reels/Spotlight node with 250ms/4s limits.
- [ ] Backup export/import round-trips social state.
- [ ] Light theme matches SafeMe tokens (no `#0F1115` dark), dark theme auto via `DarkAppColors`.
- [ ] `node --check` + `./gradlew test` green.

---

## 15. What I Need From You to Execute

A single `Execute` reply. I will then implement in the order above, starting with **removing `FOCUS & LIMITS`** and updating prototype + Kotlin in one PR (`agent/social-blocking`), keeping `Reference/prototype` and `app` in sync.
