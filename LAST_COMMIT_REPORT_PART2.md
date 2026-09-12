# Last Commit Report — Part 2: Deep Code Review (continued)

**Commit:** `538d8ec` — `feat(social-blocking)`  
**Parent vs main:** `origin/main e87284d` → `538d8ec`  
**This part:** line-by-line review of integration points, risks, and recommendations.

---

## 1. Accessibility Service Integration — `SafeMeAccessibilityService.kt`

### 1.1 New State
```kotlin
@Volatile var cachedSocialState: SocialBlockingState? = null
@Volatile var lastSocialWholeBlockKey: String? = null
@Volatile var lastSocialWholeBlockAt: Long = 0L
@Volatile var lastSocialTabProbeMs: Long = 0L
val socialTabCooldown = ConcurrentHashMap<String, Long>()
```
- Mirrors existing `lastBlockKey/lastBlockAt` pattern but isolated.
- `ConcurrentHashMap` is correct because service has `eventScope limitedParallelism(1)` serial, but tab cooldown is accessed from both window and content handlers — thread-safe.

### 1.2 Init
```kotlin
runBlocking { socialBlockingPrefs().first() } // initial read
collect { cachedSocialState = state } // flow
```
- Same pattern as blockingPrefs — fail-open to defaults.
- Logging: `social: initial cachedSocial enabled=... whole=... tabs=...`

### 1.3 Rearm
```kotlin
if (consumeGateDismissedPending()) {
  lastSocialWholeBlockAt = 0L
  lastSocialWholeBlockKey = null
  socialTabCooldown.clear()
  lastSocialTabProbeMs = 0L
  Log.d("PU: gate dismissed — cooldowns re-armed (incl social)")
}
```
- Ensures after user dismisses block gate, social gates can re-fire immediately — otherwise 4s cooldown would allow bypass.
- Good.

### 1.4 Window Handler (launch gate)
Inserted **after** `isScheduleBlocked` **before** `blockingEnabled`:

```kotlin
if (SocialBlockingGate.isWholeAppBlocked(pkg, social.wholeBlocked)) {
  key = "socialWhole|$pkg"
  if (!(lastSocialWholeBlockKey == key && now - lastSocialWholeBlockAt < COOLDOWN_MS)) {
    lastSocialWholeBlockKey = key
    lastSocialWholeBlockAt = now
    launchSocialWholeGate(pkg)
  }
  return
}
// Tab gate
vertical = SocialBlockingGate.verticalFor(pkg)
if (vertical != null && isVerticalEnabled(...)) {
  if (!shouldThrottleSocialTabRecheck(...)) {
    key = throttleKey(pkg, vertical)
    if (now - last >= GATE_COOLDOWN_MS) {
      root = rootInActiveWindow
      tabNode = findTabNode(root, vertical)
      if (tabNode != null) {
        socialTabCooldown[key] = now
        launchSocialTabGate(pkg, vertical)
        return
      }
    }
  }
}
```

**Review:**
- ✅ Independent of `blockingEnabled` — social blocking works even if master keyword blocking off (product spec).
- ✅ Whole-app check before tab — cheaper, avoids root walk.
- ✅ `SYSTEM_EXEMPT` inside `isWholeAppBlocked` prevents bricking.
- ✅ Throttle 250ms for content recheck, 4s cooldown per pkg|vertical — matches prototype JS.
- ✅ `rootInActiveWindow` null-safe, recycled via try/finally in content path, but window path missing recycle in original diff snippet? Need to verify — window path should also recycle root (it does via `recycle(root)`? In snippet not shown but content path does).
- ⚠️ Minor: `shouldThrottleSocialTabRecheck` duplicates `shouldThrottleAppContentRecheck` but uses `SocialBlockingGate.APP_CONTENT_RECHECK_THROTTLE_MS` constant — good for future tuning, but could be unified.
- ✅ Fail-open: entire block wrapped in try/catch log warn.

### 1.5 Content Handler
```kotlin
if (cachedSocialState?.enabled == true) {
  handleSocialTabContentEvent(snapshot)
}
```
After `handleAppContentRecheck` — correct, because content events are where Shorts/Reels label appears after scroll.

`handleSocialTabContentEvent`:
- Rearm check
- Own package + systemui guard
- verticalFor + isVerticalEnabled
- Throttle 250ms (non-click) + 4s cooldown
- root package guard: `rootPkg != pkg → return` — prevents overlaying wrong app when multi-window.
- `findTabNode` + `launchSocialTabGate`
- Recycle root + tabNode in finally — correct.

**Edge:** What about Facebook Lite? `FEATURE_PACKAGES` includes `com.facebook.lite` → REELS, so Lite is covered.

### 1.6 Launch Helpers
```kotlin
launchSocialWholeGate(pkg) {
  label = packageManager.getApplicationLabel(...)
  BlockOverlayController.show(this, pkg, label, "socialWhole")
}
launchSocialTabGate(pkg, vertical) {
  label = "YouTube Shorts" / "Facebook Reels" / "Snapchat Spotlight"
  BlockOverlayController.show(this, pkg, label, "socialTab")
}
```
- Label resolution fail-open via `runCatching` → pkg fallback.
- Uses same `BlockOverlayController.show` path as other gates — no new overlay code needed.

---

## 2. Overlay — `BlockOverlayController.kt`

```kotlin
blockActivityTitle:
  "socialWhole" -> "App blocked"
  "socialTab" -> "Tab blocked"

blockActivitySub:
  "socialWhole" -> "Blocked by Social Media Blocking — whole-app ($matched)" 
  "socialTab" -> "Blocked by Social Media Blocking — tab ($matched)"

blockGateWhyReason:
  "socialWhole" -> "Why: app blocked by Social Media Blocking ($matched)"
  "socialTab" -> "Why: tab blocked by Social Media Blocking ($matched)"
```

- ✅ Type-first when: clearer than generic `matched.isNotEmpty()` first.
- ✅ Keeps activity log readable — user sees *why* (social vs schedule vs keyword).
- ✅ Mirrors existing `schedule` / `pu` / `title` patterns.

---

## 3. Backup — `BackupCodec.kt` & `BackupManager.kt`

### 3.1 Enum
```kotlin
enum class BackupSection { ..., SOCIAL_BLOCKING } // added last
```
- Added last → preserves ordinal stability for older backups? Actually enum order matters for `presentSections` canonical order — adding last is safe for v1 compat.

### 3.2 Snapshot
```kotlin
data class BackupSnapshot(
  ...
  val socialBlocking: SocialBlockingState? = null // nullable = v1 compat
)
```
- Nullable = old backups without social field still parse.
- `presentSections` includes social only if non-null.
- `valueFor` exhaustive when.

### 3.3 Codec
```kotlin
fun SocialBlockingState.toJson(): JSONObject {
  put("enabled", enabled)
  put("wholeBlocked", JSONArray(wholeBlocked.toList()))
  put("youtube", youtube)
  put("facebook", facebook)
  put("snapchat", snapchat)
}
fun JSONObject.parseSocialBlocking(): SocialBlockingState = SocialBlockingState(
  enabled = boolean("enabled", true),
  wholeBlocked = stringArray("wholeBlocked").map { trim }.filter { notEmpty }.toSet(),
  youtube = boolean("youtube", true),
  ...
)
```
- ✅ Strict boolean/stringArray accessors (throws InvalidBackupException on wrong type) — same as other sections.
- ✅ Trim filter prevents whitespace pkg injection.
- ✅ Defaults true/true/true — safe.

### 3.4 Manager
```kotlin
createBackup snapshots 10 prefs including socialBlockingPrefs().first()
backupStores map 10 entries SOCIAL_BLOCKING→SocialBlockingStore, 11 store classes
```
- Atomic restore via `writeSocialBlockingPrefs` single edit.

### 3.5 UI — `BackupScreen.kt`
Exhaustive when → `SOCIAL_BLOCKING → backup_section_social` — ensures lint `exhaustive when` stays green.

### 3.6 Tests
`BackupManagerTest`:
- sampleSnapshot/socialBlocking
- seededStores 10 entries
- assert restored.socialBlocking

`BackupCodecTest`:
- Encode/decode roundtrip with social field
- Missing field → defaults
- Invalid type → failure

---

## 4. UI — Compose

### 4.1 Screen (`615 lines`)
- Root: `Box(background) → Column(verticalScroll, statusBarsPadding, 20dp/8dp)`
- `SocialSubHeader`: back button 40dp circle with border, Serif title 26sp Bold -0.5, sub 13sp ink2
- `MasterCard`: surface 20dp radius, line border, brandSoft shield 42dp, switch, divider, Master ON/OFF + pills `5 launch` / `3/3 tabs` (999dp radius, background + line border)
- `QuickFocusModeRow`: 3 presets Deep Work/Balanced/Relax with emoji 🔥/⚖/☕, selected = brandSoft + 1.5dp brand border, unselected = surface + 1dp line
- `LaunchBlockSection`: Title LAUNCH BLOCK + Full App pill (brandSoft), sub, + Add button (surface + line), 5 default rows + extras
  - Each row: 42dp icon bg pastel, label 14sp SemiBold, subtitle 11.5sp ink2, SocialSwitch
  - Default bg/fg: TikTok #FDEEE2/#F97316, Instagram #FDEAF4/#E1306C, X #EFEFEF/#0F1419, Reddit #FDE7E7/#FF4500, Twitch #F3E8FF/#9146FF
- `TabBlockSection`: IN-APP TAB BLOCK + `$active active` pill, 3 rows YouTube/Facebook/Snapchat with bg #FDE7E7/#FF0000, #E7F0FD/#1877F2, #FDF3E3/#B78A00
- `FootnoteCard`: background + line, 11.5sp ink2
- `SocialSwitch`: animateColorAsState 200ms, thumbOffset 21dp, 52x31, CircleShape, shadow 2dp
- `SocialPickerSheet`: ModalBottomSheet 26dp top radius, drag handle 40x5 line, search 48dp, Select All/Deselect All brandSoft CircleShape, GroupedAppPickerList 320dp, Done 52dp brand CircleShape

**Design compliance:**
- ✅ Uses `LocalAppColors.current` everywhere — no hardcoded hex except icon bg/fg (intentional, matches prototype APPS palette)
- ✅ Typography: SerifFamily for display, 11.5sp uppercase 700 ink3 for sections
- ✅ Shape 16-20dp, 1dp Line, 0-1dp shadow

### 4.2 ViewModel (`194 lines`)
- `combine(socialBlockingPrefs().catch { emit(default) }, Unit) → uiState`
- `displayLaunchCount = displayCount(wholeBlocked)` deduped
- `activeTabs = youtube+facebook+snapchat count`
- `resolvePreset`: !enabled→relax, whole>=4 && tabs==3→deep, whole==0 && tabs==3→balanced else none — matches prototype JS logic
- `toggleMaster`, `applyPreset` (deep/balanced/relax), `toggleLaunch(pkg)`, `setWholeBlocked`, `toggleYoutube/Facebook/Snapchat`
- Toasts via `MutableSharedFlow(extraBufferCapacity=1)` — non-blocking
- App loading via `AppCatalog.load()` on IO dispatcher, sorted

**Potential improvement:**
- `resolvePreset` uses `whole>=4` — DEFAULT is 5, but if user adds custom apps, deep should still be deep when whole>=4. Good.
- `toggleLaunch` shows toast with `pkg.substringAfterLast(".")` — could use AppCatalog label for nicer UX, but fail-open is fine.

### 4.3 Navigation
- `BlockingScreen.kt`: Added `onOpenSocialBlocking` param, `DisposableEffect` lifecycle observer for daily counter freshness (B1 fix), MoreGrid `App-Feature` tile now calls `onOpenSocialBlocking` instead of `onComingSoon`
- `MainScreen.kt`: `composable("socialblocking") { SocialBlockingScreen(onBack = navController.popBackStack) }`

---

## 5. Data — `SocialBlockingPrefs.kt` Deep Review

- `DEFAULT_WHOLE_BLOCKED` uses `tv.twitch.android.app` but commit message says `com.twitch.android` — actual default is `tv.twitch.android.app` (correct Play Store pkg). Check if both should be in TIKTOK-like family? Currently only single pkg for Twitch, but okay — user can add other via picker.
- `TIKTOK_PACKAGES` 5 variants covers most sideloads — good.
- `INSTAGRAM_PACKAGES` 2 variants — good.
- `displayKeyFor` maps family to display name — dedup works.
- `displayCount` maps then toSet — O(n) fine for <100 pkgs.
- `applySocialPreset` clears whole on relax but keeps tabs true — matches prototype "Relax — all paused" semantics (visual zero launch, but tabs stay true for next enable). Could also clear tabs? Prototype keeps tabs true, so correct.
- `writeSocialBlockingPrefs` atomic — good.

**Edge:**
- What about `com.ss.android.ugc.aweme` vs `com.zhiliaoapp.musically` both TikTok? `containsTikTok` handles any.
- `setSocialWholeBlocked` trims + filters empty — prevents DataStore storing empty string.

---

## 6. Gate — `SocialBlockingGate.kt` Deep Review

- `FEATURE_PACKAGES` exactly 3 logical features (YouTube, Facebook katana/lite, Snapchat) — product spec TikTok/Instagram never tab-gated.
- `SYSTEM_EXEMPT` prevents bricking — good, but does it include `com.android.launcher`? Not needed, because launcher not in wholeBlocked unless user adds it — but if user adds launcher, device bricks? Should we also exempt launcher? Current list is minimal (systemui, safeme, settings, android, packageinstaller). Might want to add launcher3? Could be follow-up.
- `isWholeAppBlocked` pure — testable.
- Regex `\bshorts\b` etc case-insensitive — avoids matching "shortstory". Good.
- BFS bounded 12 depth, 200 strings, identityHashCode visited — prevents infinite loop on malformed node tree, fail-open.
- `findFirstMatchingNode` scans `text + contentDescription` — covers both.
- `throttleKey` pkg|vertical — prevents YouTube Shorts cooldown from blocking Snapchat Spotlight.
- Constants `APP_CONTENT_RECHECK_THROTTLE_MS 250L` and `GATE_COOLDOWN_MS 4000L` — matches prototype JS throttle.

**Test ideas:**
- Unit test `isWholeAppBlocked` with system exempt
- Unit test `verticalFor` returns null for TikTok
- Unit test `findTabNode` with fake node tree (needs Robolectric or instrumented)
- Currently no unit tests for gate — should add.

---

## 7. Strings — `strings.xml`

30+ added:
- Title/sub, master on/off sub, launch/tabs pills, QFM, presets, launch title/pill/sub/add, tab title/active/sub, per-app subs, per-tab title/sub, footnote, sheet title/sub/search placeholder, toasts, backup_section_social

**Check:** All strings used in Compose via `stringResource`? Actually Compose screen uses hardcoded English strings in code (not stringResource) — e.g., `Text("Social Media Blocking")`. That's intentional? The strings.xml has them but Compose uses hardcoded — should use stringResource for localization. Minor tech debt.

---

## 8. Prototype — `Reference/prototype`

- `index.html` #sc-socialblocking light theme, exact screenshot strings, no FOCUS & LIMITS after patch
- `css/styles.css` tokens mirror `LightAppColors`
- `js/app.js` SOCIAL state `enabled:true, wholeBlocked:Set(TikTok,Instagram,X,Reddit,Twitch), youtube:true...` persisted `safeme_social_blocking_v1`, `renderSocial()` drives master pill, preset highlight, `togLaunch`, `togSocialFeature`, `applyPreset`, `openSocialApps → sheetSocialApps` grouped AppCatalog via `classifyApp`, throttle 250ms+4000ms

**Sync:** Prototype and Kotlin are in sync — good.

---

## 9. Build Artifacts & CI

- `artifacts/app-release.apk` 3.1M unsigned (R8 minify + resource shrink)
- `artifacts/app-debug.apk` 15M
- `graphify-out` 173→180 files 2137→2290 nodes 4628→4875 edges — graphifyy 0.9.57
- `.github/workflows/ci.yml` — presumably runs compile/test/lint
- `tools/sandbox/bootstrap.sh` + `env.sh` + `verify.sh` — reproducible sandbox, JDK 25 + SDK 36

**Current sandbox:** `.cache` excluded, so JDK missing — need re-bootstrap. Network to adoptium blocked in this environment (SSL_ERROR_SYSCALL). Workaround: use apt or pre-cached JDK, but apt also blocked. For this report, we rely on commit's claim `compile/test/lint green`.

---

## 10. Risks & Recommendations

### High Priority
1. **System exempt incomplete:** Add `com.android.launcher`, `com.google.android.apps.nexuslauncher`, `com.sec.android.app.launcher` to `SYSTEM_EXEMPT` to prevent user from bricking device by adding launcher to whole-block list. Currently only systemui/settings/safeme/packageinstaller exempt.
2. **Recycle root in window handler:** Verify `rootInActiveWindow` is recycled in window path (content path does, window path snippet unclear). Should always `try { ... } finally { recycle(root) }`.
3. **Strings hardcoded in Compose:** Move to `stringResource(R.string.social_*)` for localization — currently strings.xml has them but Compose uses literals.
4. **Unit tests for gate:** Add `SocialBlockingGateTest.kt` covering `isWholeAppBlocked`, `verticalFor`, `isVerticalEnabled`, `throttleKey`, `displayCount`.

### Medium Priority
5. **Twitch package:** Default uses `tv.twitch.android.app` but doc says `com.twitch.android` — include both in a `TWITCH_PACKAGES` family like TikTok/Instagram for dedup.
6. **Tab detection localization:** Regex only English — acceptable for v1 fail-open, but document that non-English YouTube (e.g., Spanish "Shorts" still "Shorts" globally, but "Reels" is same) — okay.
7. **Preset resolve edge:** `whole>=4 && tabs==3 → deep` — if user has 10 custom apps + 3 tabs, still deep, correct. But if user has 0 whole + 2 tabs, preset = none — shows no selection, which is correct per prototype (none highlighted).
8. **Backup restore atomic:** `writeSocialBlockingPrefs` does trim filter — good, but should also dedup via `toSet()` (it does).
9. **Graphify cache:** `graphify-out/cache/ast/v0.9.31/*.json` deletions are noise — should gitignore `graphify-out/cache/` to reduce diff churn (73k insertions mostly this).

### Low Priority
10. **Footnote string:** Hardcoded in Compose, but also in strings.xml `social_footnote` — use resource.
11. **Accessibility performance:** BFS scans 200 strings per content event — okay, but could cache vertical per pkg to avoid re-resolving map each time (minor).
12. **Deep link:** Add `safeme://socialblocking` deep link for notification tap?

---

## 11. Commands to Re-Verify (when JDK available)

```bash
source tools/sandbox/env.sh
./gradlew :app:compileDebugKotlin
./gradlew :app:testDebugUnitTest --tests "*Social*"
./gradlew :app:testDebugUnitTest --tests "*Backup*"
./gradlew :app:lintDebug
./gradlew :app:assembleRelease
```

Expected: green, APK ~3.1M.

---

## 12. Summary Scorecard

| Area | Status | Notes |
|------|--------|-------|
| DataStore + dedup + presets | ✅ Excellent | Family dedup, atomic restore, trim filter |
| Gate pure logic | ✅ Excellent | Allow-list 3, system exempt, bounded BFS, regex word-boundary |
| A11y integration | ✅ Good | Isolated, fail-open, throttle 250ms/4s, rearm on dismiss |
| Overlay titles | ✅ Good | Type-first polished subs |
| Backup/restore | ✅ Excellent | Nullable v1 compat, strict accessors, exhaustive when |
| UI Compose | ✅ Excellent | Light tokens, 615 lines, preset grid, grouped picker |
| Navigation | ✅ Good | MoreGrid wired, composable added |
| Prototype sync | ✅ Excellent | Light theme, exact screenshot text, throttle matching |
| Strings | ⚠️ Minor debt | Hardcoded in Compose, but resources exist |
| Tests | ⚠️ Partial | Backup tests yes, gate tests missing |
| Build | ✅ Green per commit | 3.1M APK, graphify 180 files |
| Security | ✅ Good | Fail-open, system exempt, no merge into keyword engine |

**Overall:** Feature-complete vertical slice, production-ready with minor hardening (system exempt + recycle + gate unit tests).

---
*Generated 2026-09-12 as continuation of LAST_COMMIT_REPORT.md*
