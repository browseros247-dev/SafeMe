# Graph Report - safeme  (2026-09-10)

## Corpus Check
- 173 files · ~136,612 words
- Verdict: corpus is large enough that graph structure adds value.

## Summary
- 2137 nodes · 4628 edges · 137 communities (97 shown, 35 thin omitted)
- Extraction: 95% EXTRACTED · 5% INFERRED · 0% AMBIGUOUS · INFERRED: 219 edges (avg confidence: 0.85)
- Token cost: 0 input · 0 output

## Graph Freshness
- Built from commit: `dc409a0a`
- Run `git rev-parse HEAD` and compare to check if the graph is stale.
- Run `graphify update .` after code changes (no API cost).

## Community Hubs (Navigation)
- BackupScreen.kt
- grantPerm
- KeywordManagerScreen.kt
- SafeMeVpnService
- TitleMatchMode
- ImageVideoSearchGateTest
- ScheduleSheets.kt
- DnsVpnViewModel
- BlockedCounterTest
- SchedulePrefs.kt
- BlockingScreen.kt
- HomeScreen.kt
- ScheduleEvaluatorTest
- BackupCodecTest
- BlockScreen.kt
- SafeMeAccessibilityService
- ScheduleScreen.kt
- ThemePref
- ProfileScreen.kt
- ScheduleEditScreen.kt
- ScheduleViewModel.kt
- AntiTamperViewModel
- MainActivity.kt
- PrivateDnsBlockersTest
- DnsVpnScreen.kt
- KeywordManagerViewModel.kt
- KeywordManagerViewModel
- BackupCodec
- VpnBootReceiver.kt
- ViewModel
- VpnValidationTest
- VpnBlockersTest
- PermissionScreen.kt
- DnsVpnIcons.kt
- HomeViewModel.kt
- SafeMeTextField
- Color.kt
- PrivateDnsFilter
- VpnStatusStore
- gradlew
- DnsVpnViewModel.kt
- env.sh
- ScheduleBlock
- Composable
- bootstrap.sh
- .recycle
- ToastHost
- resolveEditedEnabled
- SafeMe sandbox toolchain — wipe survival kit
- app.js
- QuickActionType
- DeviceAdminUtils
- A11yProtectionUtils
- SafeMeAccessibilityService.kt
- toast
- BlockingPrefs.kt
- BackupStateStore
- BlockOverlayControllerTest
- isStableA11yDisabled
- ScheduleWarningTest
- Arrangement
- ScheduleEditViewModel
- AppCatalogTest
- BlockingPrefsState
- BlockScreenIcons.kt
- Part 1 — NopoX 1.0.53 reverse engineering
- LockType
- BlockOverlayController
- BrowserUrlGateTest
- 02 — Design philosophy
- BackupSnapshot
- ScheduleMode
- AppLockManager
- AppLockScreen.kt
- check_bundled_counts.py
- A11yProtectionPrefsState
- UninstallBlockersTest
- 01 — Architecture
- 12 — WRITE_SECURE_SETTINGS protection plan
- BundledKeywordCatalogTest
- A11yProtectionUtilsTest
- ProtectedSystemPagesTest
- saveLock
- Teck Stack ,Name & Details.md
- AppLockManagerTest
- 03 — UI design system
- 11 — Development guide
- BottomNavBar.kt
- Application
- ScheduleEngine
- resolveWhitelistSeed
- AutoLockDelay
- BlockScreenPrefsState
- BundledKeywords
- 04 — Security architecture
- BlockOverlayController.kt
- BlockGateActivity.kt
- AccessibilityProtectionScreen.kt
- A11yProtectionGuard
- 05 — Blocking engine (accessibility service)
- 07 — VPN / DNS filtering
- renderAppPicker
- AppLockPrefsState
- SafeMeProtectionService.kt
- DnsPreset
- kwRender
- JsoncTest
- EventSnapshot
- qaRender
- 08 — Backup & Restore
- 2. Principles
- ProtectedSystemPages
- ProtectionLayersTest
- Release signing
- SafeMe
- isOwnUiEvent
- checkUnlock
- shouldThrottleAppContentRecheck
- A11yBootReceiver.kt
- AppLockGateController
- BrowserUrlGate
- SafeMe Bundled Blocking Dataset (Engine-Only · Hidden from UI)
- 06 — Schedule-based blocking
- isValidScheduleWindow
- actAdd
- ScheduleAlarmReceiver.kt
- TunnelRestartPolicy
- 09 — App Picker
- TunnelRestartPolicyTest
- saveSchedule
- AuthenticationCallback
- Jsonc

## God Nodes (most connected - your core abstractions)
1. `SafeMeAccessibilityService` - 65 edges
2. `ScheduleEvaluatorTest` - 40 edges
3. `BackupCodecTest` - 36 edges
4. `toast()` - 35 edges
5. `BlockingPrefsState` - 34 edges
6. `DnsVpnViewModel` - 34 edges
7. `ToastHost()` - 33 edges
8. `SafeMeTextField()` - 28 edges
9. `BlockedCategory` - 27 edges
10. `LockType` - 26 edges

## Surprising Connections (you probably didn't know these)
- `BlockGate()` --calls--> `blockGateWhyReason()`  [INFERRED]
  app/src/main/java/com/safeme/app/BlockGateActivity.kt → app/src/main/java/com/safeme/app/BlockOverlayController.kt
- `createBackup()` --calls--> `a11yProtectionPrefs()`  [INFERRED]
  app/src/main/java/com/safeme/app/data/BackupManager.kt → app/src/main/java/com/safeme/app/data/A11yProtectionPrefs.kt
- `createBackup()` --calls--> `appLockPrefs()`  [INFERRED]
  app/src/main/java/com/safeme/app/data/BackupManager.kt → app/src/main/java/com/safeme/app/data/AppLockPrefs.kt
- `createBackup()` --calls--> `BackupSnapshot`  [INFERRED]
  app/src/main/java/com/safeme/app/data/BackupManager.kt → app/src/main/java/com/safeme/app/data/BackupCodec.kt
- `createBackup()` --calls--> `blockingPrefs()`  [INFERRED]
  app/src/main/java/com/safeme/app/data/BackupManager.kt → app/src/main/java/com/safeme/app/data/BlockingPrefs.kt

## Import Cycles
- None detected.

## Communities (137 total, 35 thin omitted)

### Community 0 - "BackupScreen.kt"
Cohesion: 0.12
Nodes (24): BackupError, EMPTY, INVALID_STRUCTURE, NOT_JSON, NOT_SAFEME, ROLLBACK_FAILED, UNSUPPORTED_VERSION, WRITE_FAILED (+16 more)

### Community 1 - "grantPerm"
Cohesion: 0.40
Nodes (5): finishOnboard(), grantPerm(), permAdvance(), permStatus(), skipPerm()

### Community 2 - "KeywordManagerScreen.kt"
Cohesion: 0.14
Nodes (29): BlockedCategory, ADULT, CUSTOM, DISTRACTION, GAMBLING, SHOPPING, SOCIAL_MEDIA, ActionButton() (+21 more)

### Community 3 - "SafeMeVpnService"
Cohesion: 0.21
Nodes (7): setVpnEnabled(), Context, IBinder, Intent, SafeMeVpnService, ParcelFileDescriptor, VpnService

### Community 4 - "TitleMatchMode"
Cohesion: 0.18
Nodes (8): TitleMatchMode, CONTAINS, EXACT, STARTS_WITH, AndroidViewModel, SharedFlow, StateFlow, TitleBlockViewModel

### Community 6 - "ScheduleSheets.kt"
Cohesion: 0.23
Nodes (17): AppPickerSheet(), AppRow(), CheckBox(), GrabBar(), h12(), Modifier, PrimaryPill(), SecondaryButton() (+9 more)

### Community 8 - "BlockedCounterTest"
Cohesion: 0.16
Nodes (7): blockedDateKey(), blockedTodayFlow(), incrementBlockedToday(), maybeRolloverBlockedCounter(), nextBlockedCounter(), rolloverBlockedCount(), BlockedCounterTest

### Community 9 - "SchedulePrefs.kt"
Cohesion: 0.26
Nodes (13): addSchedule(), deleteSchedule(), Flow, schedulePrefs(), SchedulePrefsState, schedulesFromJson(), schedulesToJson(), setA11yWarningDismissed() (+5 more)

### Community 10 - "BlockingScreen.kt"
Cohesion: 0.09
Nodes (34): setBlockingExcludedApps(), contentEnginePrefs(), ContentEnginePrefsState, Flow, setBlockImageVideoSearch(), BlockingScreen(), cardShape(), IconBox() (+26 more)

### Community 11 - "HomeScreen.kt"
Cohesion: 0.09
Nodes (42): ActivityEntry, activityFromJson(), activityLog(), activityToJson(), addActivity(), appendActivity(), formatActivityTime(), Flow (+34 more)

### Community 14 - "BlockScreen.kt"
Cohesion: 0.13
Nodes (28): blurredShadow(), Color, Dp, Modifier, BlockScreen(), bsImgColors(), CustomSwitch(), GhostBlockButton() (+20 more)

### Community 15 - "SafeMeAccessibilityService"
Cohesion: 0.14
Nodes (3): AccessibilityEvent, MatchResult, SafeMeAccessibilityService

### Community 16 - "ScheduleScreen.kt"
Cohesion: 0.20
Nodes (23): A11yWarningBanner(), cardShape(), ExactAlarmBanner(), exactAlarmSettingsIntent(), ExcludeAppsCard(), HeroCard(), HeroPill(), HeroRings() (+15 more)

### Community 17 - "ThemePref"
Cohesion: 0.14
Nodes (13): Flow, markOnboardingComplete(), onboardingComplete(), setThemePref(), ThemePref, DARK, LIGHT, SYSTEM (+5 more)

### Community 18 - "ProfileScreen.kt"
Cohesion: 0.25
Nodes (21): cardShape(), DeleteButton(), DeleteDialog(), Footer(), GroupLabel(), IconBox(), IdentityCard(), Color (+13 more)

### Community 19 - "ScheduleEditScreen.kt"
Cohesion: 0.27
Nodes (13): AppsCard(), appSummary(), DayCircles(), DeleteButton(), EnabledRow(), GroupLabel(), Header(), Modifier (+5 more)

### Community 20 - "ScheduleViewModel.kt"
Cohesion: 0.14
Nodes (10): scheduleTimeLabel(), scheduleWindowLabel(), AndroidViewModel, SharedFlow, StateFlow, ScheduleCard, ScheduleUiState, ScheduleViewModel (+2 more)

### Community 21 - "AntiTamperViewModel"
Cohesion: 0.25
Nodes (6): setPreventUninstallEnabled(), AntiTamperUiState, AntiTamperViewModel, AndroidViewModel, SharedFlow, StateFlow

### Community 22 - "MainActivity.kt"
Cohesion: 0.16
Nodes (11): Bundle, MainActivity, AppLockGateHost(), Modifier, OnboardingNavHost(), findActivity(), android, SafeMeTheme() (+3 more)

### Community 24 - "DnsVpnScreen.kt"
Cohesion: 0.20
Nodes (18): DnsPresetList(), DnsVpnScreen(), GroupLabel(), androidx, Color, Modifier, NotifSeg(), VpnDivider() (+10 more)

### Community 25 - "KeywordManagerViewModel.kt"
Cohesion: 0.29
Nodes (11): addBlockedKeyword(), BlockedKeyword, keywordsFromJson(), keywordsToJson(), removeBlockedKeyword(), resetUserBlockingPrefs(), setBlockingEnabled(), updateBlockedKeyword() (+3 more)

### Community 27 - "BackupCodec"
Cohesion: 0.19
Nodes (8): BackupCodec, BackupParseResult, Failure, InvalidBackupException, T, RestoreResult, Success, Exception

### Community 28 - "VpnBootReceiver.kt"
Cohesion: 0.53
Nodes (4): BroadcastReceiver, Context, Intent, VpnBootReceiver

### Community 29 - "ViewModel"
Cohesion: 0.17
Nodes (16): ManagePermissionsFlow(), StateFlow, OnboardingViewModel, AccessibilityPermissionStep(), BatteryPermissionStep(), hasNotificationsPermission(), Context, NotificationPermissionStep() (+8 more)

### Community 32 - "PermissionScreen.kt"
Cohesion: 0.40
Nodes (9): BackChip(), GrantPill(), HeroTitle(), Color, Modifier, PermissionScreen(), ProgressDots(), TimelineHeader() (+1 more)

### Community 34 - "HomeViewModel.kt"
Cohesion: 0.18
Nodes (11): Flow, preventUninstallPrefs(), PreventUninstallPrefsState, A11yStatus, HomeUiState, HomeViewModel, AndroidViewModel, Job (+3 more)

### Community 35 - "SafeMeTextField"
Cohesion: 0.16
Nodes (24): ProtectedServiceEntry, Modifier, SafeMeTextField(), MasterSwitch(), PickerRow(), SearchField(), ServicePickerScreen(), SearchField() (+16 more)

### Community 36 - "Color.kt"
Cohesion: 0.12
Nodes (7): iconBuilder(), ImageVector, iconBuilder(), ImageVector, iconBuilder(), ImageVector, AppColors

### Community 37 - "PrivateDnsFilter"
Cohesion: 0.35
Nodes (6): clearPrivateDnsBackup(), Context, readPrivateDnsBackup(), savePrivateDnsBackup(), Context, PrivateDnsFilter

### Community 39 - "gradlew"
Cohesion: 0.83
Nodes (3): gradlew script, die(), warn()

### Community 40 - "DnsVpnViewModel.kt"
Cohesion: 0.18
Nodes (13): DnsVpnSettings, Flow, PrivateDnsBackup, setVpnCustomDns(), setVpnNotifCustom(), setVpnNotifMode(), setVpnWhitelist(), writeVpnSettings() (+5 more)

### Community 43 - "env.sh"
Cohesion: 0.22
Nodes (9): ANDROID_HOME, ANDROID_SDK_ROOT, GRADLE_USER_HOME, JAVA_HOME, PATH, env.sh script, gradle_run(), new_log() (+1 more)

### Community 44 - "ScheduleBlock"
Cohesion: 0.38
Nodes (3): ScheduleBlock, ActiveRules, ScheduleEvaluator

### Community 45 - "Composable"
Cohesion: 0.19
Nodes (17): KeyCircle(), FocusRequester, Modifier, PasswordField(), PatternGrid(), PinDots(), PinKeypad(), shakeEffect() (+9 more)

### Community 46 - "bootstrap.sh"
Cohesion: 0.36
Nodes (9): ANDROID_HOME, ANDROID_SDK_ROOT, ensure_jdk(), JAVA_HOME, ok(), PATH, bootstrap.sh script, step() (+1 more)

### Community 48 - "ToastHost"
Cohesion: 0.13
Nodes (25): HostToast, Flow, Modifier, ToastHost(), ToastPill(), AntiTamperScreen(), cardShape(), Header() (+17 more)

### Community 50 - "SafeMe sandbox toolchain — wipe survival kit"
Cohesion: 0.25
Nodes (7): Last verified state, Recovery (one command), SafeMe sandbox toolchain — wipe survival kit, The problem, Two locations (identical files), What persists vs what doesn't, Why these exact choices

### Community 53 - "app.js"
Cohesion: 0.04
Nodes (43): a11yStatus(), ACTIVITY, APP_CATS, applyTheme(), appPickSel, APPS, back(), DEFAULT_APP_SEL (+35 more)

### Community 54 - "QuickActionType"
Cohesion: 0.09
Nodes (26): Flow, quickActionPrefs(), quickActionsFromJson(), quickActionsToJson(), QuickActionType, APPLOCK, BACKUP, HISTORY (+18 more)

### Community 55 - "DeviceAdminUtils"
Cohesion: 0.36
Nodes (5): DeviceAdminUtils, Context, Intent, SafeMeDeviceAdminReceiver, DeviceAdminReceiver

### Community 56 - "A11yProtectionUtils"
Cohesion: 0.33
Nodes (3): A11yProtectionUtils, Context, ComponentName

### Community 57 - "SafeMeAccessibilityService.kt"
Cohesion: 0.11
Nodes (8): AccessibilityService, UninstallBlockers, PrivateDnsBlockers, VpnBlockers, Intent, Job, shouldThrottleScheduleRecheck(), SafeMeAccessibilityServiceScheduleTest

### Community 58 - "toast"
Cohesion: 0.14
Nodes (24): addKeyword(), addSite(), addTitle(), appsDone(), cancelDelay(), closeBlockov(), closeSheets(), delTitle() (+16 more)

### Community 59 - "BlockingPrefs.kt"
Cohesion: 0.19
Nodes (28): addBlockedWebsite(), addTitleBlockRule(), addTrustedWebsite(), addWhitelistKeyword(), BlockedWebsite, blockingEnabled(), blockingPrefs(), deleteTitleBlockRule() (+20 more)

### Community 60 - "BackupStateStore"
Cohesion: 0.09
Nodes (13): A11yProtectionStore, AppLockStore, BackupFile, BackupStateStore, backupStores(), BlockingStore, BlockScreenStore, ContentEngineStore (+5 more)

### Community 61 - "BlockOverlayControllerTest"
Cohesion: 0.13
Nodes (5): blockActivitySub(), blockActivityTitle(), blockGateMessage(), blockGateWhyReason(), BlockOverlayControllerTest

### Community 63 - "ScheduleWarningTest"
Cohesion: 0.24
Nodes (4): requiresAccessibility(), shouldShowA11yWarning(), shouldShowExactAlarmWarning(), ScheduleWarningTest

### Community 64 - "Arrangement"
Cohesion: 0.11
Nodes (30): AppCatalog, AppCategory, GAMES, MESSAGING, NEWS_PROD, OTHER, PAYMENT, SHOPPING (+22 more)

### Community 65 - "ScheduleEditViewModel"
Cohesion: 0.12
Nodes (8): newScheduleId(), Factory, AndroidViewModel, SharedFlow, StateFlow, T, ScheduleEditUiState, ScheduleEditViewModel

### Community 67 - "BlockingPrefsState"
Cohesion: 0.19
Nodes (3): BlockingPrefsState, isExcludedFromContentEngine(), ExcludeAppsE2ETest

### Community 68 - "BlockScreenIcons.kt"
Cohesion: 0.29
Nodes (4): iconBuilder(), ImageVector, iconBuilder(), ImageVector

### Community 69 - "Part 1 — NopoX 1.0.53 reverse engineering"
Cohesion: 0.11
Nodes (18): 1.1 Architecture overview, 1.2 Components and permissions (manifest), 1.3 Accessibility service configuration, 1.4 The detection core (`MyAccessibilityService.checkPreventUninstall`), 1.5 Execution flow (detection → protection), 1.6 Timing characteristics (static analysis), 1.7 Live measurement attempt, 1.8 Weaknesses, race conditions, bypasses, edge cases (+10 more)

### Community 70 - "LockType"
Cohesion: 0.19
Nodes (18): LockType, OFF, PASSWORD, PATTERN, PIN, HeroCard(), methodLabel(), AppLockSetupSheet() (+10 more)

### Community 73 - "02 — Design philosophy"
Cohesion: 0.11
Nodes (18): 02 — Design philosophy, 1.1 Fail open, never fail closed on detection, 1.2 Add-only writes for system settings, 1.3 Never lock the user out, 1.4 Idempotent coordinators, 1.5 Never crash on the user's data, 1.6 Mirror the prototype, not the reference code, 1. Core principles (+10 more)

### Community 74 - "BackupSnapshot"
Cohesion: 0.15
Nodes (14): BackupSection, A11Y_PROTECTION, APP_LOCK, BLOCK_SCREEN, BLOCKING, CONTENT_ENGINE, PREVENT_UNINSTALL, QUICK_ACTIONS (+6 more)

### Community 75 - "ScheduleMode"
Cohesion: 0.18
Nodes (6): scheduleDaysLabel(), ScheduleMode, BOTH, INTERNET, LAUNCH, scheduleModeLabel()

### Community 76 - "AppLockManager"
Cohesion: 0.26
Nodes (3): AppLockManager, Context, ByteArray

### Community 77 - "AppLockScreen.kt"
Cohesion: 0.26
Nodes (14): AppLockScreen(), autoLockValue(), Chevron(), DisableButton(), Header(), androidx, Color, Modifier (+6 more)

### Community 78 - "check_bundled_counts.py"
Cohesion: 0.46
Nodes (7): is_domain(), literals_in_listof(), main(), measure(), Keep bundled-dataset.md counts in sync with the dataset sources (B11). Parses…, regenerate(), render()

### Community 79 - "A11yProtectionPrefsState"
Cohesion: 0.23
Nodes (11): a11yProtectionPrefs(), A11yProtectionPrefsState, addProtectedA11yComponent(), Flow, removeProtectedA11yComponent(), setA11yProtectionEnabled(), writeA11yProtectionPrefs(), AndroidViewModel (+3 more)

### Community 81 - "01 — Architecture"
Cohesion: 0.12
Nodes (16): 01 — Architecture, 1. Big picture, 2. Layer map, 3. Module-by-module, 4. Startup sequence, 5. Key data flows, 6. Process components and receivers, 7. State holders (process-wide caches) (+8 more)

### Community 82 - "12 — WRITE_SECURE_SETTINGS protection plan"
Cohesion: 0.12
Nodes (16): 12 — WRITE_SECURE_SETTINGS protection plan, 1. Goal and the honest answer, 2. Threat model: who can revoke today, 3.1 Module-level change list, 3.2 APIs, 3. Phase 1 — watchdog + in-app password gating (no new privileges), 4.1 Manifest & provisioning, 4.2 New `protect/DeviceOwnerManager.kt` (+8 more)

### Community 86 - "saveLock"
Cohesion: 0.17
Nodes (15): getWizCode(), openLockSetup(), patTap(), pinBackS(), refreshWizBtn(), renderDotsId(), resetWiz(), saveLock() (+7 more)

### Community 87 - "Teck Stack ,Name & Details.md"
Cohesion: 0.13
Nodes (14): Android App, Architecture, Background Processing, Build System, CI/CD, Code Quality, Dependency Injection, Dependency Management (+6 more)

### Community 89 - "03 — UI design system"
Cohesion: 0.14
Nodes (13): 03 — UI design system, 1. Theme, 2. Shared components (`ui/components/`), 3. Navigation, 4. Home screen anatomy (reference screen), 5. Writing a new screen, Card language, Colors (`ui/theme/Color.kt`) (+5 more)

### Community 90 - "11 — Development guide"
Cohesion: 0.14
Nodes (14): 11 — Development guide, 1. Build & toolchain, 2. Unit testing, 3. Conventions, 4. Adding a feature (workflow), 5. Constraints checklist (do not break), 6. CI/CD, Comments (+6 more)

### Community 91 - "BottomNavBar.kt"
Cohesion: 0.80
Nodes (5): BadgeDot(), BottomNavBar(), Modifier, NavDestination, NavItem()

### Community 92 - "Application"
Cohesion: 0.20
Nodes (8): A11yProtectionStateHolder, SafeMeApp, A11yProtectionUiState, AccessibilityProtectionViewModel, AndroidViewModel, SharedFlow, StateFlow, Application

### Community 95 - "AutoLockDelay"
Cohesion: 0.13
Nodes (13): AutoLockDelay, AFTER_15S, AFTER_1M, AFTER_30S, AFTER_5M, IMMEDIATELY, OFF, setAppLockAutoLock() (+5 more)

### Community 96 - "BlockScreenPrefsState"
Cohesion: 0.23
Nodes (7): blockScreenPrefs(), BlockScreenPrefsState, Flow, writeBlockScreenPrefs(), BlockScreenViewModel, AndroidViewModel, StateFlow

### Community 97 - "BundledKeywords"
Cohesion: 0.17
Nodes (4): BundledKeywordCatalog, BundledKeywords, ImageVideoSearchGate, Match

### Community 98 - "04 — Security architecture"
Cohesion: 0.14
Nodes (14): 04 — Security architecture, 1. Permissions model, 2. App Lock, 3. Prevent Uninstall & Device Admin, 4. Protection layers summary, 5. Accessibility protection (self-heal), Accessibility guards (`service/SafeMeAccessibilityService.handlePreventUninstall`), Device Admin (`protect/DeviceAdminUtils.kt`) (+6 more)

### Community 99 - "BlockOverlayController.kt"
Cohesion: 0.19
Nodes (8): BlockGateActivityTest, OverlayLifecycleOwner, Lifecycle, LifecycleOwner, SavedStateRegistry, SavedStateRegistryOwner, View, WindowManager

### Community 100 - "BlockGateActivity.kt"
Cohesion: 0.24
Nodes (6): AccessibilityProtectionCopyTest, BlockGate(), BlockGateActivity, Bundle, BlockOverlay(), SafeMeApp()

### Community 101 - "AccessibilityProtectionScreen.kt"
Cohesion: 0.31
Nodes (12): AccessibilityProtectionScreen(), cardShape(), copyToClipboard(), Header(), Context, Dp, Modifier, MasterCard() (+4 more)

### Community 103 - "05 — Blocking engine (accessibility service)"
Cohesion: 0.17
Nodes (12): 05 — Blocking engine (accessibility service), 1. Event pipeline, 2. Rule sources, 3. Matching semantics, 4. Block gate (`BlockGateActivity`), 5. Robustness guarantees, 6. Relationship to other features, Cooldowns & dedup (+4 more)

### Community 104 - "07 — VPN / DNS filtering"
Cohesion: 0.15
Nodes (13): 07 — VPN / DNS filtering, 1. Architecture: DNS-delegated filtering, 2. Tunnel modes, 3. Lifecycle, 4. Watchdog (`vpn/TunnelRestartPolicy.kt`), 5. Schedule integration, 6. Boot re-arm & status, 7. UI (`ui/screens/vpn/`) (+5 more)

### Community 105 - "renderAppPicker"
Cohesion: 0.17
Nodes (15): appChip(), classifyApp(), deselectAllApps(), deselectAllVpnApps(), kwAdd(), kwEdit(), openSheet(), refreshAppPicker() (+7 more)

### Community 106 - "AppLockPrefsState"
Cohesion: 0.31
Nodes (8): appLockPrefs(), AppLockPrefsState, Flow, setAppLock(), setAppLockBiometric(), setAppLockForgotDisabled(), writeAppLockPrefs(), AppLockStateHolder

### Community 107 - "SafeMeProtectionService.kt"
Cohesion: 0.36
Nodes (5): Context, IBinder, Intent, SafeMeProtectionService, Service

### Community 108 - "DnsPreset"
Cohesion: 0.17
Nodes (6): setVpnPreset(), DnsPreset, ADGUARD_FAMILY, CLOUDFLARE_FAMILY, CUSTOM, VpnValidation

### Community 109 - "kwRender"
Cohesion: 0.28
Nodes (9): kwGo(), kwLabels(), kwMgCard(), kwOverride(), kwRemove(), kwRender(), kwRowHTML(), kwTab() (+1 more)

### Community 113 - "qaRender"
Cohesion: 0.29
Nodes (8): homeQuickRender(), qaAdd(), qaIcon(), qaMove(), qaRemove(), qaRender(), qaReset(), qaRow()

### Community 114 - "08 — Backup & Restore"
Cohesion: 0.22
Nodes (9): 08 — Backup & Restore, 1. Files & responsibilities, 2. File format, 3. Export, 4. Import & validation, 5. UX flow, 6. Notes & behavior verified on-device, Restore (`executeRestore`) (+1 more)

### Community 115 - "2. Principles"
Cohesion: 0.20
Nodes (10): 10 — Performance, 1. Measured results (baseline → after), 2. Principles, 3. What is deliberately NOT optimized further, 4. Verification workflow, Bound every per-event cost, Compose hygiene, Gate background work by need (+2 more)

### Community 118 - "Release signing"
Cohesion: 0.20
Nodes (9): CI key identity (canonical), CI (recommended for releases), Local (developer machine), Reference builds, Release signing, Rules, Signing sources, TWO signing keys exist — they are NOT interchangeable (+1 more)

### Community 119 - "SafeMe"
Cohesion: 0.22
Nodes (9): Constraints at a glance, Documentation, Feature overview, Getting started, Optional ADB grants, Protection layers, Release signing, Repository layout (+1 more)

### Community 121 - "checkUnlock"
Cohesion: 0.25
Nodes (9): checkUnlock(), closeLockov(), lockNow(), methodLabel(), patUnlock(), pickAuto(), renderLock(), renderUnlock() (+1 more)

### Community 123 - "A11yBootReceiver.kt"
Cohesion: 0.53
Nodes (4): A11yBootReceiver, BroadcastReceiver, Context, Intent

### Community 126 - "SafeMe Bundled Blocking Dataset (Engine-Only · Hidden from UI)"
Cohesion: 0.18
Nodes (10): 1. Curated adult list (`data/BundledAdult.kt`), 2. NopoX catalog (`data/BundledKeywordCatalog.kt`), 3. Recovery whitelist seed, ADULT — Keywords (20), ADULT — Websites (113), Dataset totals (ADULT ONLY), IMPORTANT — Engine-Only Usage, Integrity (+2 more)

### Community 127 - "06 — Schedule-based blocking"
Cohesion: 0.25
Nodes (8): 06 — Schedule-based blocking, 1. Data model (`data/SchedulePrefs.kt`), 2. Pure decision core (`protect/ScheduleEvaluator.kt`), 3. Coordinator (`protect/ScheduleEngine.kt`), 4. Alarm & boot (`protect/ScheduleAlarmReceiver.kt`), 5. Safety ticker (`SafeMeApp`), 6. Enforcement surfaces, 7. Editing flow (`ui/screens/schedule/`)

### Community 129 - "actAdd"
Cohesion: 0.67
Nodes (4): actAdd(), actDot(), feedRender(), histRender()

### Community 130 - "ScheduleAlarmReceiver.kt"
Cohesion: 0.52
Nodes (4): BroadcastReceiver, Context, Intent, ScheduleAlarmReceiver

### Community 132 - "TunnelRestartPolicy"
Cohesion: 0.33
Nodes (4): Outcome, KEEP_WAITING, TUNNEL_DEAD, TunnelRestartPolicy

### Community 133 - "09 — App Picker"
Cohesion: 0.29
Nodes (7): 09 — App Picker, 1. Discovery (`data/AppCatalog.kt`), 2. Category taxonomy, 3. Grouping & search, 4. Presentation (`ui/components/GroupedAppPicker.kt`), 5. Consumers, Classification precedence (`categorize`)

### Community 138 - "saveSchedule"
Cohesion: 0.33
Nodes (6): daysLabel(), delSchedule(), modeTxt(), saveSchedule(), schedCardHTML(), schedCount()

### Community 139 - "AuthenticationCallback"
Cohesion: 0.31
Nodes (4): AppLockBiometrics, AuthenticationCallback, Context, BiometricPrompt

## Knowledge Gaps
- **276 isolated node(s):** `SCREENS`, `ORDER`, `stack`, `groups`, `ptb` (+271 more)
  These have ≤1 connection - possible missing edges or undocumented components. (Counts symbols only; 549 node(s) total have ≤1 connection when file, concept and rationale nodes are included.)
- **35 thin communities (<3 nodes) omitted from report** — run `graphify query` to explore isolated nodes.

## Suggested Questions
_Questions this graph is uniquely positioned to answer:_

- **Why does `SafeMeAccessibilityService` connect `SafeMeAccessibilityService` to `BlockOverlayController.kt`, `BlockGateActivity.kt`, `BlockingPrefsState`, `.recycle`, `EventSnapshot`, `A11yProtectionUtils`, `SafeMeAccessibilityService.kt`, `ScheduleEngine`?**
  _High betweenness centrality (0.052) - this node is a cross-community bridge._
- **Why does `BlockingPrefsState` connect `BlockingPrefsState` to `BundledKeywords`, `HomeViewModel.kt`, `KeywordManagerScreen.kt`, `ImageVideoSearchGateTest`, `BackupCodec`, `BrowserUrlGateTest`, `BackupSnapshot`, `BackupCodecTest`, `SafeMeAccessibilityService`, `SafeMeAccessibilityService.kt`, `BlockingPrefs.kt`, `BrowserUrlGate`?**
  _High betweenness centrality (0.047) - this node is a cross-community bridge._
- **Why does `ToastHost()` connect `ToastHost` to `BackupScreen.kt`, `KeywordManagerScreen.kt`, `SafeMeTextField`, `AccessibilityProtectionScreen.kt`, `BlockingScreen.kt`, `HomeScreen.kt`, `AppLockScreen.kt`, `ScheduleScreen.kt`, `ProfileScreen.kt`, `ScheduleEditScreen.kt`, `DnsVpnScreen.kt`?**
  _High betweenness centrality (0.038) - this node is a cross-community bridge._
- **What connects `SCREENS`, `ORDER`, `stack` to the rest of the system?**
  _276 weakly-connected nodes found - possible documentation gaps or missing edges._
- **Should `BackupScreen.kt` be split into smaller, more focused modules?**
  _Cohesion score 0.11822660098522167 - nodes in this community are weakly interconnected._
- **Should `KeywordManagerScreen.kt` be split into smaller, more focused modules?**
  _Cohesion score 0.1350806451612903 - nodes in this community are weakly interconnected._
- **Should `BlockingScreen.kt` be split into smaller, more focused modules?**
  _Cohesion score 0.08562367864693446 - nodes in this community are weakly interconnected._