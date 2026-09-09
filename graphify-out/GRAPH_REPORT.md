# Graph Report - SafeMe  (2026-09-09)

## Corpus Check
- 173 files · ~139,264 words
- Verdict: corpus is large enough that graph structure adds value.

## Summary
- 2116 nodes · 4595 edges · 122 communities (91 shown, 26 thin omitted)
- Extraction: 95% EXTRACTED · 5% INFERRED · 0% AMBIGUOUS · INFERRED: 218 edges (avg confidence: 0.85)
- Token cost: 0 input · 0 output

## Graph Freshness
- Built from commit: `d3669655`
- Run `git rev-parse HEAD` and compare to check if the graph is stale.
- Run `graphify update .` after code changes (no API cost).

## Community Hubs (Navigation)
- BackupScreen.kt
- grantPerm
- KeywordManagerScreen.kt
- SafeMeVpnService
- TitleBlockRule
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
- ToastHost
- ThemePref
- ProfileScreen.kt
- ScheduleEditScreen.kt
- ScheduleViewModel.kt
- preventUninstallPrefs
- MainActivity.kt
- PrivateDnsBlockersTest
- DnsVpnScreen.kt
- BlockingViewModel.kt
- KeywordManagerViewModel
- QuickActionType
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
- BackupError
- AboutScreen.kt
- resolveEditedEnabled
- SafeMe sandbox toolchain — wipe survival kit
- app.js
- .add
- DeviceAdminUtils
- A11yProtectionUtils
- BlockSheets.kt
- toast
- BlockingPrefs.kt
- BackupStateStore
- BlockOverlayControllerTest
- isStableA11yDisabled
- ScheduleWarningTest
- Arrangement
- ScheduleEditViewModel
- AppCatalogTest
- quickActionsFromJson
- AppLockIcons.kt
- Part 1 — NopoX 1.0.53 reverse engineering
- LockType
- BackupSection
- BlockedKeyword
- 02 — Design philosophy
- .seededStores
- ScheduleMode
- AppLockManager
- AppLockScreen.kt
- check_bundled_counts.py
- contentEnginePrefs
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
- MainScreen.kt
- ProtectionLayersEvaluator
- ScheduleEngine
- resolveWhitelistSeed
- AppLockViewModel.kt
- BundledKeywords
- 04 — Security architecture
- 05 — Blocking engine (accessibility service)
- 07 — VPN / DNS filtering
- renderAppPicker
- AutoLockDelay
- VpnValidation
- JsoncTest
- 08 — Backup & Restore
- 2. Principles
- ProtectionLayersTest
- Release signing
- SafeMe
- addKeyword
- checkUnlock
- Application
- SafeMe Bundled Blocking Dataset (Engine-Only · Hidden from UI)
- 06 — Schedule-based blocking
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
3. `toast()` - 36 edges
4. `BackupCodecTest` - 36 edges
5. `BlockingPrefsState` - 34 edges
6. `DnsVpnViewModel` - 34 edges
7. `ToastHost()` - 33 edges
8. `SafeMeTextField()` - 28 edges
9. `BlockedCategory` - 27 edges
10. `LockType` - 26 edges

## Surprising Connections (you probably didn't know these)
- `createBackup()` --calls--> `a11yProtectionPrefs()`  [INFERRED]
  app/src/main/java/com/safeme/app/data/BackupManager.kt → app/src/main/java/com/safeme/app/data/A11yProtectionPrefs.kt
- `createBackup()` --calls--> `appLockPrefs()`  [INFERRED]
  app/src/main/java/com/safeme/app/data/BackupManager.kt → app/src/main/java/com/safeme/app/data/AppLockPrefs.kt
- `createBackup()` --calls--> `BackupSnapshot`  [INFERRED]
  app/src/main/java/com/safeme/app/data/BackupManager.kt → app/src/main/java/com/safeme/app/data/BackupCodec.kt
- `createBackup()` --calls--> `blockingPrefs()`  [INFERRED]
  app/src/main/java/com/safeme/app/data/BackupManager.kt → app/src/main/java/com/safeme/app/data/BlockingPrefs.kt
- `createBackup()` --calls--> `blockScreenPrefs()`  [INFERRED]
  app/src/main/java/com/safeme/app/data/BackupManager.kt → app/src/main/java/com/safeme/app/data/BlockScreenPrefs.kt

## Import Cycles
- None detected.

## Communities (122 total, 26 thin omitted)

### Community 0 - "BackupScreen.kt"
Cohesion: 0.17
Nodes (16): ActionPill(), BackupActionCard(), BackupHeader(), BackupScreen(), BackupViewModel, cardShape(), errorRes(), GroupLabel() (+8 more)

### Community 1 - "grantPerm"
Cohesion: 0.50
Nodes (5): finishOnboard(), grantPerm(), permAdvance(), permStatus(), skipPerm()

### Community 2 - "KeywordManagerScreen.kt"
Cohesion: 0.14
Nodes (29): BlockedCategory, ADULT, CUSTOM, DISTRACTION, GAMBLING, SHOPPING, SOCIAL_MEDIA, ActionButton() (+21 more)

### Community 3 - "SafeMeVpnService"
Cohesion: 0.21
Nodes (7): DnsVpnSettings, Context, IBinder, Intent, SafeMeVpnService, ParcelFileDescriptor, VpnService

### Community 4 - "TitleBlockRule"
Cohesion: 0.17
Nodes (16): addTitleBlockRule(), deleteTitleBlockRule(), TitleBlockRule, TitleMatchMode, CONTAINS, EXACT, STARTS_WITH, titleRulesFromJson() (+8 more)

### Community 6 - "ScheduleSheets.kt"
Cohesion: 0.23
Nodes (17): AppPickerSheet(), AppRow(), CheckBox(), GrabBar(), h12(), Modifier, PrimaryPill(), SecondaryButton() (+9 more)

### Community 8 - "BlockedCounterTest"
Cohesion: 0.17
Nodes (6): blockedDateKey(), incrementBlockedToday(), maybeRolloverBlockedCounter(), nextBlockedCounter(), rolloverBlockedCount(), BlockedCounterTest

### Community 9 - "SchedulePrefs.kt"
Cohesion: 0.26
Nodes (13): addSchedule(), deleteSchedule(), Flow, schedulePrefs(), SchedulePrefsState, schedulesFromJson(), schedulesToJson(), setA11yWarningDismissed() (+5 more)

### Community 10 - "BlockingScreen.kt"
Cohesion: 0.18
Nodes (24): BlockingScreen(), cardShape(), IconBox(), IconVariant, Amber, Dark, Green, Red (+16 more)

### Community 11 - "HomeScreen.kt"
Cohesion: 0.09
Nodes (43): ActivityEntry, activityFromJson(), activityLog(), activityToJson(), addActivity(), appendActivity(), formatActivityTime(), Flow (+35 more)

### Community 13 - "BackupCodecTest"
Cohesion: 0.06
Nodes (15): BackupCodec, BackupParseResult, BackupSnapshot, Failure, InvalidBackupException, T, RestoreResult, Success (+7 more)

### Community 14 - "BlockScreen.kt"
Cohesion: 0.26
Nodes (15): BlockScreen(), bsImgColors(), CustomSwitch(), GhostBlockButton(), GradientTile(), GroupLabel(), HeaderRow(), Color (+7 more)

### Community 15 - "SafeMeAccessibilityService"
Cohesion: 0.05
Nodes (18): AccessibilityEvent, AccessibilityNodeInfo, AccessibilityService, UninstallBlockers, PrivateDnsBlockers, ProtectedSystemPages, VpnBlockers, EventSnapshot (+10 more)

### Community 16 - "ToastHost"
Cohesion: 0.08
Nodes (48): HostToast, Flow, Modifier, ToastHost(), ToastPill(), AccessibilityProtectionScreen(), cardShape(), copyToClipboard() (+40 more)

### Community 17 - "ThemePref"
Cohesion: 0.14
Nodes (13): Flow, markOnboardingComplete(), onboardingComplete(), setThemePref(), ThemePref, DARK, LIGHT, SYSTEM (+5 more)

### Community 18 - "ProfileScreen.kt"
Cohesion: 0.23
Nodes (23): cardShape(), DeleteButton(), DeleteDialog(), Footer(), GroupLabel(), IconBox(), IdentityCard(), Color (+15 more)

### Community 19 - "ScheduleEditScreen.kt"
Cohesion: 0.27
Nodes (13): AppsCard(), appSummary(), DayCircles(), DeleteButton(), EnabledRow(), GroupLabel(), Header(), Modifier (+5 more)

### Community 20 - "ScheduleViewModel.kt"
Cohesion: 0.14
Nodes (10): scheduleTimeLabel(), scheduleWindowLabel(), AndroidViewModel, SharedFlow, StateFlow, ScheduleCard, ScheduleUiState, ScheduleViewModel (+2 more)

### Community 21 - "preventUninstallPrefs"
Cohesion: 0.20
Nodes (9): Flow, preventUninstallPrefs(), PreventUninstallPrefsState, setPreventUninstallEnabled(), AntiTamperUiState, AntiTamperViewModel, AndroidViewModel, SharedFlow (+1 more)

### Community 22 - "MainActivity.kt"
Cohesion: 0.17
Nodes (9): AccessibilityProtectionCopyTest, Bundle, MainActivity, AppLockGateHost(), findActivity(), android, SafeMeApp(), SafeMeTheme() (+1 more)

### Community 24 - "DnsVpnScreen.kt"
Cohesion: 0.14
Nodes (22): DnsPresetList(), DnsVpnScreen(), GroupLabel(), androidx, Color, Modifier, NotifSeg(), VpnDivider() (+14 more)

### Community 25 - "BlockingViewModel.kt"
Cohesion: 0.17
Nodes (7): setBlockingEnabled(), setBlockingExcludedApps(), BlockingUiState, BlockingViewModel, AndroidViewModel, SharedFlow, StateFlow

### Community 27 - "QuickActionType"
Cohesion: 0.20
Nodes (12): Flow, quickActionPrefs(), quickActionsToJson(), QuickActionType, APPLOCK, BACKUP, HISTORY, KEYWORD (+4 more)

### Community 28 - "VpnBootReceiver.kt"
Cohesion: 0.53
Nodes (4): BroadcastReceiver, Context, Intent, VpnBootReceiver

### Community 29 - "ViewModel"
Cohesion: 0.18
Nodes (17): ManagePermissionsFlow(), Modifier, OnboardingNavHost(), StateFlow, OnboardingViewModel, AccessibilityPermissionStep(), BatteryPermissionStep(), hasNotificationsPermission() (+9 more)

### Community 32 - "PermissionScreen.kt"
Cohesion: 0.18
Nodes (16): blurredShadow(), Color, Dp, Modifier, BackChip(), GrantPill(), HeroTitle(), Color (+8 more)

### Community 34 - "HomeViewModel.kt"
Cohesion: 0.22
Nodes (8): A11yStatus, HomeUiState, HomeViewModel, AndroidViewModel, Job, SharedFlow, StateFlow, PrefsSnapshot

### Community 35 - "SafeMeTextField"
Cohesion: 0.21
Nodes (19): Modifier, SafeMeTextField(), SearchField(), NameField(), SearchField(), EmptyCard(), Header(), HeroCard() (+11 more)

### Community 36 - "Color.kt"
Cohesion: 0.12
Nodes (7): iconBuilder(), ImageVector, iconBuilder(), ImageVector, iconBuilder(), ImageVector, AppColors

### Community 37 - "PrivateDnsFilter"
Cohesion: 0.31
Nodes (7): clearPrivateDnsBackup(), Context, PrivateDnsBackup, readPrivateDnsBackup(), savePrivateDnsBackup(), Context, PrivateDnsFilter

### Community 39 - "gradlew"
Cohesion: 0.83
Nodes (3): gradlew script, die(), warn()

### Community 40 - "DnsVpnViewModel.kt"
Cohesion: 0.18
Nodes (13): Flow, setVpnCustomDns(), setVpnEnabled(), setVpnNotifCustom(), setVpnNotifMode(), setVpnPreset(), setVpnWhitelist(), writeVpnSettings() (+5 more)

### Community 43 - "env.sh"
Cohesion: 0.22
Nodes (9): ANDROID_HOME, ANDROID_SDK_ROOT, GRADLE_USER_HOME, JAVA_HOME, PATH, env.sh script, gradle_run(), new_log() (+1 more)

### Community 44 - "ScheduleBlock"
Cohesion: 0.38
Nodes (3): ScheduleBlock, ActiveRules, ScheduleEvaluator

### Community 45 - "Composable"
Cohesion: 0.26
Nodes (14): KeyCircle(), FocusRequester, Modifier, PasswordField(), PatternGrid(), PinDots(), PinKeypad(), shakeEffect() (+6 more)

### Community 46 - "bootstrap.sh"
Cohesion: 0.36
Nodes (9): ANDROID_HOME, ANDROID_SDK_ROOT, ensure_jdk(), JAVA_HOME, ok(), PATH, bootstrap.sh script, step() (+1 more)

### Community 47 - "BackupError"
Cohesion: 0.25
Nodes (8): BackupError, EMPTY, INVALID_STRUCTURE, NOT_JSON, NOT_SAFEME, ROLLBACK_FAILED, UNSUPPORTED_VERSION, WRITE_FAILED

### Community 48 - "AboutScreen.kt"
Cohesion: 0.46
Nodes (7): AboutHeader(), AboutIdentityCard(), AboutLinksList(), AboutRow(), AboutScreen(), Color, ImageVector

### Community 49 - "resolveEditedEnabled"
Cohesion: 0.20
Nodes (4): isValidScheduleWindow(), resolveEditedEnabled(), ScheduleEditEnabledTest, ScheduleEditValidationTest

### Community 50 - "SafeMe sandbox toolchain — wipe survival kit"
Cohesion: 0.25
Nodes (7): Last verified state, Recovery (one command), SafeMe sandbox toolchain — wipe survival kit, The problem, Two locations (identical files), What persists vs what doesn't, Why these exact choices

### Community 53 - "app.js"
Cohesion: 0.05
Nodes (37): a11yStatus(), APP_CATS, applyTheme(), appPickSel, APPS, back(), DEFAULT_APP_SEL, editTitle() (+29 more)

### Community 54 - ".add"
Cohesion: 0.26
Nodes (10): AddBadge(), ArrowButton(), EditActionRow(), EditHeader(), EditSectionTitle(), AndroidViewModel, StateFlow, QuickActionsEditScreen() (+2 more)

### Community 55 - "DeviceAdminUtils"
Cohesion: 0.36
Nodes (5): DeviceAdminUtils, Context, Intent, SafeMeDeviceAdminReceiver, DeviceAdminReceiver

### Community 56 - "A11yProtectionUtils"
Cohesion: 0.06
Nodes (31): a11yProtectionPrefs(), A11yProtectionPrefsState, addProtectedA11yComponent(), Flow, removeProtectedA11yComponent(), setA11yProtectionEnabled(), writeA11yProtectionPrefs(), A11yBootReceiver (+23 more)

### Community 57 - "BlockSheets.kt"
Cohesion: 0.38
Nodes (9): CustomMessageSheet(), ImgTile(), androidx, Color, Modifier, MotivationImageSheet(), SheetField(), SheetGrab() (+1 more)

### Community 58 - "toast"
Cohesion: 0.13
Nodes (24): addTitle(), appsDone(), cancelDelay(), closeBlockov(), closeSheets(), delTitle(), dwellStep(), openBlockov() (+16 more)

### Community 59 - "BlockingPrefs.kt"
Cohesion: 0.17
Nodes (29): addBlockedKeyword(), addBlockedWebsite(), addTrustedWebsite(), addWhitelistKeyword(), blockedTodayFlow(), BlockedWebsite, blockingEnabled(), blockingPrefs() (+21 more)

### Community 60 - "BackupStateStore"
Cohesion: 0.09
Nodes (13): A11yProtectionStore, AppLockStore, BackupFile, BackupStateStore, backupStores(), BlockingStore, BlockScreenStore, ContentEngineStore (+5 more)

### Community 61 - "BlockOverlayControllerTest"
Cohesion: 0.05
Nodes (26): BlockGateActivityTest, BlockGate(), BlockGateActivity, Bundle, blockActivitySub(), blockActivityTitle(), blockGateMessage(), blockGateWhyReason() (+18 more)

### Community 63 - "ScheduleWarningTest"
Cohesion: 0.24
Nodes (4): requiresAccessibility(), shouldShowA11yWarning(), shouldShowExactAlarmWarning(), ScheduleWarningTest

### Community 64 - "Arrangement"
Cohesion: 0.11
Nodes (30): AppCatalog, AppCategory, GAMES, MESSAGING, NEWS_PROD, OTHER, PAYMENT, SHOPPING (+22 more)

### Community 65 - "ScheduleEditViewModel"
Cohesion: 0.12
Nodes (8): newScheduleId(), Factory, AndroidViewModel, SharedFlow, StateFlow, T, ScheduleEditUiState, ScheduleEditViewModel

### Community 68 - "AppLockIcons.kt"
Cohesion: 0.29
Nodes (4): iconBuilder(), ImageVector, iconBuilder(), ImageVector

### Community 69 - "Part 1 — NopoX 1.0.53 reverse engineering"
Cohesion: 0.11
Nodes (18): 1.1 Architecture overview, 1.2 Components and permissions (manifest), 1.3 Accessibility service configuration, 1.4 The detection core (`MyAccessibilityService.checkPreventUninstall`), 1.5 Execution flow (detection → protection), 1.6 Timing characteristics (static analysis), 1.7 Live measurement attempt, 1.8 Weaknesses, race conditions, bypasses, edge cases (+10 more)

### Community 70 - "LockType"
Cohesion: 0.22
Nodes (16): LockType, OFF, PASSWORD, PATTERN, PIN, AppLockSetupSheet(), AutoLockSheet(), CheckBox() (+8 more)

### Community 71 - "BackupSection"
Cohesion: 0.18
Nodes (10): BackupSection, A11Y_PROTECTION, APP_LOCK, BLOCK_SCREEN, BLOCKING, CONTENT_ENGINE, PREVENT_UNINSTALL, QUICK_ACTIONS (+2 more)

### Community 73 - "02 — Design philosophy"
Cohesion: 0.11
Nodes (18): 02 — Design philosophy, 1.1 Fail open, never fail closed on detection, 1.2 Add-only writes for system settings, 1.3 Never lock the user out, 1.4 Idempotent coordinators, 1.5 Never crash on the user's data, 1.6 Mirror the prototype, not the reference code, 1. Core principles (+10 more)

### Community 74 - ".seededStores"
Cohesion: 0.35
Nodes (3): executeRestore(), BackupManagerTest, FakeStore

### Community 75 - "ScheduleMode"
Cohesion: 0.18
Nodes (6): scheduleDaysLabel(), ScheduleMode, BOTH, INTERNET, LAUNCH, scheduleModeLabel()

### Community 76 - "AppLockManager"
Cohesion: 0.25
Nodes (4): AppLockPrefsState, AppLockManager, Context, ByteArray

### Community 77 - "AppLockScreen.kt"
Cohesion: 0.23
Nodes (16): AppLockScreen(), autoLockValue(), Chevron(), DisableButton(), Header(), HeroCard(), androidx, Color (+8 more)

### Community 78 - "check_bundled_counts.py"
Cohesion: 0.46
Nodes (7): is_domain(), literals_in_listof(), main(), measure(), Keep bundled-dataset.md counts in sync with the dataset sources (B11). Parses…, regenerate(), render()

### Community 79 - "contentEnginePrefs"
Cohesion: 0.60
Nodes (4): contentEnginePrefs(), ContentEnginePrefsState, Flow, setBlockImageVideoSearch()

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

### Community 91 - "MainScreen.kt"
Cohesion: 0.20
Nodes (15): ProtectedServiceEntry, BadgeDot(), BottomNavBar(), Modifier, NavDestination, NavItem(), MasterSwitch(), PickerRow() (+7 more)

### Community 95 - "AppLockViewModel.kt"
Cohesion: 0.20
Nodes (5): AppLockUiState, AppLockViewModel, AndroidViewModel, SharedFlow, StateFlow

### Community 97 - "BundledKeywords"
Cohesion: 0.16
Nodes (4): BundledKeywordCatalog, BundledKeywords, BrowserUrlGate, UrlMatch

### Community 98 - "04 — Security architecture"
Cohesion: 0.14
Nodes (14): 04 — Security architecture, 1. Permissions model, 2. App Lock, 3. Prevent Uninstall & Device Admin, 4. Protection layers summary, 5. Accessibility protection (self-heal), Accessibility guards (`service/SafeMeAccessibilityService.handlePreventUninstall`), Device Admin (`protect/DeviceAdminUtils.kt`) (+6 more)

### Community 103 - "05 — Blocking engine (accessibility service)"
Cohesion: 0.17
Nodes (12): 05 — Blocking engine (accessibility service), 1. Event pipeline, 2. Rule sources, 3. Matching semantics, 4. Block gate (`BlockGateActivity`), 5. Robustness guarantees, 6. Relationship to other features, Cooldowns & dedup (+4 more)

### Community 104 - "07 — VPN / DNS filtering"
Cohesion: 0.15
Nodes (13): 07 — VPN / DNS filtering, 1. Architecture: DNS-delegated filtering, 2. Tunnel modes, 3. Lifecycle, 4. Watchdog (`vpn/TunnelRestartPolicy.kt`), 5. Schedule integration, 6. Boot re-arm & status, 7. UI (`ui/screens/vpn/`) (+5 more)

### Community 105 - "renderAppPicker"
Cohesion: 0.17
Nodes (15): appChip(), classifyApp(), deselectAllApps(), deselectAllVpnApps(), openKwEdit(), openSheet(), openSiteEdit(), refreshAppPicker() (+7 more)

### Community 106 - "AutoLockDelay"
Cohesion: 0.17
Nodes (14): appLockPrefs(), AutoLockDelay, AFTER_15S, AFTER_1M, AFTER_30S, AFTER_5M, IMMEDIATELY, OFF (+6 more)

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

### Community 120 - "addKeyword"
Cohesion: 0.25
Nodes (9): addKeyword(), addSite(), applyKwFilter(), applySiteFilter(), filterKwAll(), filterSites(), openManage(), removeRow() (+1 more)

### Community 121 - "checkUnlock"
Cohesion: 0.25
Nodes (9): checkUnlock(), closeLockov(), lockNow(), methodLabel(), patUnlock(), pickAuto(), renderLock(), renderUnlock() (+1 more)

### Community 124 - "Application"
Cohesion: 0.18
Nodes (5): AppLockStateHolder, SafeMeApp, AppLockGateController, StateFlow, Application

### Community 126 - "SafeMe Bundled Blocking Dataset (Engine-Only · Hidden from UI)"
Cohesion: 0.18
Nodes (10): 1. Curated adult list (`data/BundledAdult.kt`), 2. NopoX catalog (`data/BundledKeywordCatalog.kt`), 3. Recovery whitelist seed, ADULT — Keywords (20), ADULT — Websites (113), Dataset totals (ADULT ONLY), IMPORTANT — Engine-Only Usage, Integrity (+2 more)

### Community 127 - "06 — Schedule-based blocking"
Cohesion: 0.25
Nodes (8): 06 — Schedule-based blocking, 1. Data model (`data/SchedulePrefs.kt`), 2. Pure decision core (`protect/ScheduleEvaluator.kt`), 3. Coordinator (`protect/ScheduleEngine.kt`), 4. Alarm & boot (`protect/ScheduleAlarmReceiver.kt`), 5. Safety ticker (`SafeMeApp`), 6. Enforcement surfaces, 7. Editing flow (`ui/screens/schedule/`)

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
Cohesion: 0.27
Nodes (5): AppLockBiometrics, AuthenticationCallback, Context, BiometricPrompt, FragmentActivity

## Knowledge Gaps
- **268 isolated node(s):** `SCREENS`, `ORDER`, `stack`, `groups`, `ptb` (+263 more)
  These have ≤1 connection - possible missing edges or undocumented components. (Counts symbols only; 538 node(s) total have ≤1 connection when file, concept and rationale nodes are included.)
- **26 thin communities (<3 nodes) omitted from report** — run `graphify query` to explore isolated nodes.

## Suggested Questions
_Questions this graph is uniquely positioned to answer:_

- **Why does `SafeMeAccessibilityService` connect `SafeMeAccessibilityService` to `A11yProtectionUtils`, `BackupCodecTest`, `ScheduleEngine`, `BlockOverlayControllerTest`?**
  _High betweenness centrality (0.049) - this node is a cross-community bridge._
- **Why does `BlockingPrefsState` connect `BackupCodecTest` to `BundledKeywords`, `HomeViewModel.kt`, `KeywordManagerScreen.kt`, `ImageVideoSearchGateTest`, `BlockedKeyword`, `.seededStores`, `SafeMeAccessibilityService`, `BlockingPrefs.kt`?**
  _High betweenness centrality (0.042) - this node is a cross-community bridge._
- **Why does `ToastHost()` connect `ToastHost` to `BackupScreen.kt`, `KeywordManagerScreen.kt`, `SafeMeTextField`, `BlockingScreen.kt`, `HomeScreen.kt`, `AppLockScreen.kt`, `AboutScreen.kt`, `ProfileScreen.kt`, `ScheduleEditScreen.kt`, `DnsVpnScreen.kt`, `MainScreen.kt`?**
  _High betweenness centrality (0.042) - this node is a cross-community bridge._
- **What connects `SCREENS`, `ORDER`, `stack` to the rest of the system?**
  _268 weakly-connected nodes found - possible documentation gaps or missing edges._
- **Should `KeywordManagerScreen.kt` be split into smaller, more focused modules?**
  _Cohesion score 0.14193548387096774 - nodes in this community are weakly interconnected._
- **Should `HomeScreen.kt` be split into smaller, more focused modules?**
  _Cohesion score 0.0899854862119013 - nodes in this community are weakly interconnected._
- **Should `BackupCodecTest` be split into smaller, more focused modules?**
  _Cohesion score 0.06302521008403361 - nodes in this community are weakly interconnected._