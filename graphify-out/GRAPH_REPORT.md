# Graph Report - SafeMe  (2026-08-21)

## Corpus Check
- 151 files · ~124,524 words
- Verdict: corpus is large enough that graph structure adds value.

## Summary
- 1851 nodes · 3359 edges · 123 communities (97 shown, 26 thin omitted)
- Extraction: 91% EXTRACTED · 9% INFERRED · 0% AMBIGUOUS · INFERRED: 309 edges (avg confidence: 0.8)
- Token cost: 0 input · 0 output

## Graph Freshness
- Built from commit: `52c8a3ae`
- Run `git rev-parse HEAD` and compare to check if the graph is stale.
- Run `graphify update .` after code changes (no API cost).

## Community Hubs (Navigation)
- DnsVpnScreen.kt
- DnsPreset
- KeywordManagerViewModel
- SafeMeVpnService
- BlockingPrefs.kt
- AccessibilityProtectionScreen
- SafeMeTextField
- DnsVpnViewModel
- .onCreate
- FocusScreen.kt
- BlockingScreen.kt
- HomeScreen.kt
- PreventUninstallPrefsState
- BackupScreen
- BlockScreen
- SafeMeAccessibilityService
- ToastHost
- .attachOverlay
- ProfileScreen.kt
- MainScreen
- NavItem
- BackupError
- BlockScreenViewModel
- BlockScreenPrefsState
- scheduleDaysLabel
- VpnBootReceiver
- OnboardingNavHost
- VpnValidationTest
- blurredShadow
- DnsVpnIcons.kt
- BlockScreenIcons.kt
- PermissionIcons.kt
- VpnStatusStore
- gradlew
- Color.kt
- Reference/prototype/js/app.js
- QuickActionType
- A11yProtectionUtils
- BackupCodecTest
- toast
- BackupStateStore
- BlockOverlayControllerTest
- BlockOverlayController
- ScheduleEvaluatorTest
- AppCategory
- ScheduleEditViewModel
- AppCatalogTest
- Part 1 — NopoX 1.0.53 reverse engineering
- AppLockSheets.kt
- BackupSection
- ScheduleSheets.kt
- 02 — Design philosophy
- .seededStores
- AppLockManager
- AppLockScreen.kt
- ScheduleViewModel
- UninstallBlockersTest
- 01 — Architecture
- 12 — WRITE_SECURE_SETTINGS protection plan
- VpnAppsSheet
- A11yProtectionUtilsTest
- ProtectedSystemPagesTest
- saveLock
- Teck Stack ,Name & Details.md
- AppLockManagerTest
- 03 — UI design system
- 11 — Development guide
- SchedulePrefs.kt
- ScheduleEngine
- MethodInput
- AppLockViewModel
- ScheduleEditScreen
- 04 — Security architecture
- LockType
- A11yProtectionGuard
- 05 — Blocking engine (accessibility service)
- 07 — VPN / DNS filtering
- renderAppPicker
- AutoLockDelay
- ScheduleBlock
- shouldShowA11yWarning
- Intent
- JsoncTest
- 08 — Backup & Restore
- 2. Principles
- TitleBlockScreen
- ProtectionLayersTest
- Release signing
- SafeMe
- addKeyword
- checkUnlock
- AppLockGateController
- SafeMe Bundled Blocking Dataset (Engine-Only · Hidden from UI)
- 06 — Schedule-based blocking
- ScheduleMode
- ProtectedSystemPages
- ScheduleAlarmReceiver
- Outcome
- 09 — App Picker
- A11yBootReceiver
- TunnelRestartPolicyTest
- saveSchedule
- AppLockBiometrics
- grantPerm
- AntiTamperIcons.kt
- AppLockIcons.kt
- BackupIcons.kt
- BlockGateActivityTest
- Jsonc
- UninstallBlockers

## God Nodes (most connected - your core abstractions)
1. `SafeMeAccessibilityService` - 48 edges
2. `toast()` - 36 edges
3. `DnsVpnViewModel` - 34 edges
4. `BackupCodecTest` - 33 edges
5. `ScheduleEvaluatorTest` - 24 edges
6. `QuickActionType` - 23 edges
7. `KeywordManagerViewModel` - 22 edges
8. `MainScreen()` - 22 edges
9. `BlockedCategory` - 21 edges
10. `ScheduleEditViewModel` - 21 edges

## Surprising Connections (you probably didn't know these)
- `BlockGate()` --calls--> `blockGateWhyReason()`  [INFERRED]
  app/src/main/java/com/safeme/app/BlockGateActivity.kt → app/src/main/java/com/safeme/app/BlockOverlayController.kt
- `BlockGate()` --calls--> `BlockScreenPrefsState`  [INFERRED]
  app/src/main/java/com/safeme/app/BlockGateActivity.kt → app/src/main/java/com/safeme/app/data/BlockScreenPrefs.kt
- `createBackup()` --calls--> `appLockPrefs()`  [INFERRED]
  app/src/main/java/com/safeme/app/data/BackupManager.kt → app/src/main/java/com/safeme/app/data/AppLockPrefs.kt
- `createBackup()` --calls--> `blockingPrefs()`  [INFERRED]
  app/src/main/java/com/safeme/app/data/BackupManager.kt → app/src/main/java/com/safeme/app/data/BlockingPrefs.kt
- `createBackup()` --calls--> `blockScreenPrefs()`  [INFERRED]
  app/src/main/java/com/safeme/app/data/BackupManager.kt → app/src/main/java/com/safeme/app/data/BlockScreenPrefs.kt

## Import Cycles
- None detected.

## Communities (123 total, 26 thin omitted)

### Community 0 - "DnsVpnScreen.kt"
Cohesion: 0.19
Nodes (18): DnsPresetList(), DnsVpnScreen(), GroupLabel(), androidx, Color, Modifier, NotifSeg(), VpnDivider() (+10 more)

### Community 1 - "DnsPreset"
Cohesion: 0.22
Nodes (7): setVpnPreset(), DnsPreset, ADGUARD_FAMILY, CLOUDFLARE_FAMILY, CUSTOM, fromName(), VpnValidation

### Community 2 - "KeywordManagerViewModel"
Cohesion: 0.09
Nodes (33): BlockedCategory, ADULT, CUSTOM, DISTRACTION, GAMBLING, SHOPPING, SOCIAL_MEDIA, ActionButton() (+25 more)

### Community 3 - "SafeMeVpnService"
Cohesion: 0.09
Nodes (18): clearPrivateDnsBackup(), DnsVpnSettings, Context, Flow, PrivateDnsBackup, readPrivateDnsBackup(), savePrivateDnsBackup(), setVpnEnabled() (+10 more)

### Community 4 - "BlockingPrefs.kt"
Cohesion: 0.06
Nodes (51): BackupCodec, BackupParseResult, Failure, InvalidBackupException, T, RestoreResult, Success, addBlockedKeyword() (+43 more)

### Community 5 - "AccessibilityProtectionScreen"
Cohesion: 0.12
Nodes (18): AccessibilityProtectionCopyTest, AccessibilityProtectionScreen(), cardShape(), copyToClipboard(), Header(), Context, Dp, Modifier (+10 more)

### Community 6 - "SafeMeTextField"
Cohesion: 0.28
Nodes (12): Modifier, SafeMeTextField(), FocusRequester, PasswordField(), SearchField(), NameField(), SearchField(), SearchField() (+4 more)

### Community 7 - "DnsVpnViewModel"
Cohesion: 0.11
Nodes (6): DnsVpnUiState, DnsVpnViewModel, AndroidViewModel, Job, SharedFlow, StateFlow

### Community 8 - ".onCreate"
Cohesion: 0.21
Nodes (6): BlockGate(), BlockGateActivity, Bundle, incrementBlockedToday(), BlockOverlay(), ComponentActivity

### Community 9 - "FocusScreen.kt"
Cohesion: 0.12
Nodes (24): cardShape(), FocusScreen(), HeroCard(), IconBox(), Color, Dp, ImageVector, Modifier (+16 more)

### Community 10 - "BlockingScreen.kt"
Cohesion: 0.11
Nodes (28): BlockingScreen(), cardShape(), IconBox(), IconVariant, Amber, Dark, Green, Red (+20 more)

### Community 11 - "HomeScreen.kt"
Cohesion: 0.08
Nodes (42): ActivityEntry, activityFromJson(), activityLog(), activityToJson(), addActivity(), appendActivity(), formatActivityTime(), Flow (+34 more)

### Community 12 - "PreventUninstallPrefsState"
Cohesion: 0.50
Nodes (3): Flow, preventUninstallPrefs(), PreventUninstallPrefsState

### Community 13 - "BackupScreen"
Cohesion: 0.17
Nodes (16): ActionPill(), BackupActionCard(), BackupHeader(), BackupScreen(), BackupViewModel, cardShape(), errorRes(), GroupLabel() (+8 more)

### Community 14 - "BlockScreen"
Cohesion: 0.14
Nodes (24): BlockScreen(), bsImgColors(), CustomSwitch(), GhostBlockButton(), GradientTile(), GroupLabel(), HeaderRow(), Color (+16 more)

### Community 15 - "SafeMeAccessibilityService"
Cohesion: 0.09
Nodes (15): AccessibilityEvent, AccessibilityNodeInfo, AccessibilityService, BlockingPrefsState, consumeGateDismissedPending(), EventSnapshot, isWithinPostDismissalWindow(), Intent (+7 more)

### Community 16 - "ToastHost"
Cohesion: 0.07
Nodes (36): HostToast, Flow, Modifier, ToastHost(), ToastPill(), AntiTamperScreen(), cardShape(), Header() (+28 more)

### Community 17 - ".attachOverlay"
Cohesion: 0.32
Nodes (5): OverlayLifecycleOwner, Lifecycle, LifecycleOwner, SavedStateRegistry, SavedStateRegistryOwner

### Community 18 - "ProfileScreen.kt"
Cohesion: 0.06
Nodes (47): Flow, onboardingComplete(), setThemePref(), ThemePref, DARK, LIGHT, SYSTEM, AboutHeader() (+39 more)

### Community 19 - "MainScreen"
Cohesion: 0.39
Nodes (6): MasterSwitch(), PickerRow(), SearchField(), ServicePickerScreen(), MainScreen(), PlaceholderScreen()

### Community 20 - "NavItem"
Cohesion: 0.73
Nodes (5): BadgeDot(), BottomNavBar(), Modifier, NavDestination, NavItem()

### Community 21 - "BackupError"
Cohesion: 0.25
Nodes (8): BackupError, EMPTY, INVALID_STRUCTURE, NOT_JSON, NOT_SAFEME, ROLLBACK_FAILED, UNSUPPORTED_VERSION, WRITE_FAILED

### Community 22 - "BlockScreenViewModel"
Cohesion: 0.22
Nodes (3): BlockScreenViewModel, AndroidViewModel, StateFlow

### Community 23 - "BlockScreenPrefsState"
Cohesion: 0.60
Nodes (4): blockScreenPrefs(), BlockScreenPrefsState, Flow, writeBlockScreenPrefs()

### Community 28 - "VpnBootReceiver"
Cohesion: 0.33
Nodes (4): BroadcastReceiver, Context, Intent, VpnBootReceiver

### Community 29 - "OnboardingNavHost"
Cohesion: 0.07
Nodes (26): Bundle, MainActivity, SafeMeApp, ManagePermissionsFlow(), Modifier, OnboardingNavHost(), StateFlow, OnboardingViewModel (+18 more)

### Community 32 - "blurredShadow"
Cohesion: 0.40
Nodes (4): blurredShadow(), Color, Dp, Modifier

### Community 39 - "gradlew"
Cohesion: 0.83
Nodes (3): gradlew script, die(), warn()

### Community 53 - "Reference/prototype/js/app.js"
Cohesion: 0.06
Nodes (35): a11yStatus(), applyTheme(), appPickSel, back(), DEFAULT_APP_SEL, editTitle(), fmt12h(), groups (+27 more)

### Community 54 - "QuickActionType"
Cohesion: 0.05
Nodes (38): fromId(), Flow, quickActionPrefs(), quickActionsFromJson(), quickActionsToJson(), QuickActionType, APPLOCK, BACKUP (+30 more)

### Community 56 - "A11yProtectionUtils"
Cohesion: 0.15
Nodes (10): A11yProtectionStateHolder, A11yProtectionUtils, Context, ProtectedServiceEntry, DeviceAdminUtils, Context, Intent, SafeMeDeviceAdminReceiver (+2 more)

### Community 57 - "BackupCodecTest"
Cohesion: 0.09
Nodes (11): a11yProtectionPrefs(), A11yProtectionPrefsState, Flow, writeA11yProtectionPrefs(), BackupSnapshot, createBackup(), AndroidViewModel, StateFlow (+3 more)

### Community 58 - "toast"
Cohesion: 0.10
Nodes (29): addTitle(), appsDone(), cancelDelay(), closeBlockov(), closeSheets(), delTitle(), dwellStep(), openBlockov() (+21 more)

### Community 60 - "BackupStateStore"
Cohesion: 0.10
Nodes (11): A11yProtectionStore, AppLockStore, BackupFile, BackupStateStore, backupStores(), BlockingStore, BlockScreenStore, PreventUninstallStore (+3 more)

### Community 61 - "BlockOverlayControllerTest"
Cohesion: 0.13
Nodes (5): blockActivitySub(), blockActivityTitle(), blockGateMessage(), blockGateWhyReason(), BlockOverlayControllerTest

### Community 62 - "BlockOverlayController"
Cohesion: 0.24
Nodes (4): BlockOverlayController, Context, View, WindowManager

### Community 64 - "AppCategory"
Cohesion: 0.13
Nodes (16): AppCatalog, AppCategory, GAMES, MESSAGING, NEWS_PROD, OTHER, PAYMENT, SHOPPING (+8 more)

### Community 65 - "ScheduleEditViewModel"
Cohesion: 0.11
Nodes (8): Factory, AndroidViewModel, SharedFlow, StateFlow, T, ScheduleEditUiState, ScheduleEditViewModel, ViewModelProvider

### Community 69 - "Part 1 — NopoX 1.0.53 reverse engineering"
Cohesion: 0.11
Nodes (18): 1.1 Architecture overview, 1.2 Components and permissions (manifest), 1.3 Accessibility service configuration, 1.4 The detection core (`MyAccessibilityService.checkPreventUninstall`), 1.5 Execution flow (detection → protection), 1.6 Timing characteristics (static analysis), 1.7 Live measurement attempt, 1.8 Weaknesses, race conditions, bypasses, edge cases (+10 more)

### Community 70 - "AppLockSheets.kt"
Cohesion: 0.35
Nodes (11): AppLockSetupSheet(), AutoLockSheet(), CheckBox(), ConfirmInput(), CreateInput(), GhostButton(), GrabBar(), Modifier (+3 more)

### Community 71 - "BackupSection"
Cohesion: 0.20
Nodes (9): BackupSection, A11Y_PROTECTION, APP_LOCK, BLOCK_SCREEN, BLOCKING, PREVENT_UNINSTALL, QUICK_ACTIONS, SCHEDULES (+1 more)

### Community 72 - "ScheduleSheets.kt"
Cohesion: 0.22
Nodes (17): AppPickerSheet(), AppRow(), CheckBox(), GrabBar(), h12(), Modifier, PrimaryPill(), SecondaryButton() (+9 more)

### Community 73 - "02 — Design philosophy"
Cohesion: 0.11
Nodes (18): 02 — Design philosophy, 1.1 Fail open, never fail closed on detection, 1.2 Add-only writes for system settings, 1.3 Never lock the user out, 1.4 Idempotent coordinators, 1.5 Never crash on the user's data, 1.6 Mirror the prototype, not the reference code, 1. Core principles (+10 more)

### Community 74 - ".seededStores"
Cohesion: 0.31
Nodes (3): executeRestore(), BackupManagerTest, FakeStore

### Community 76 - "AppLockManager"
Cohesion: 0.26
Nodes (4): AppLockPrefsState, AppLockManager, Context, ByteArray

### Community 77 - "AppLockScreen.kt"
Cohesion: 0.21
Nodes (16): AppLockScreen(), autoLockValue(), Chevron(), DisableButton(), Header(), HeroCard(), androidx, Color (+8 more)

### Community 78 - "ScheduleViewModel"
Cohesion: 0.17
Nodes (8): scheduleTimeLabel(), scheduleWindowLabel(), AndroidViewModel, SharedFlow, StateFlow, ScheduleCard, ScheduleUiState, ScheduleViewModel

### Community 81 - "01 — Architecture"
Cohesion: 0.12
Nodes (16): 01 — Architecture, 1. Big picture, 2. Layer map, 3. Module-by-module, 4. Startup sequence, 5. Key data flows, 6. Process components and receivers, 7. State holders (process-wide caches) (+8 more)

### Community 82 - "12 — WRITE_SECURE_SETTINGS protection plan"
Cohesion: 0.12
Nodes (16): 12 — WRITE_SECURE_SETTINGS protection plan, 1. Goal and the honest answer, 2. Threat model: who can revoke today, 3.1 Module-level change list, 3.2 APIs, 3. Phase 1 — watchdog + in-app password gating (no new privileges), 4.1 Manifest & provisioning, 4.2 New `protect/DeviceOwnerManager.kt` (+8 more)

### Community 83 - "VpnAppsSheet"
Cohesion: 0.26
Nodes (14): AppRow(), CustomDnsSheet(), androidx, FocusRequester, Modifier, VpnAppsSheet(), VpnCheckbox(), VpnDoneButton() (+6 more)

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

### Community 91 - "SchedulePrefs.kt"
Cohesion: 0.32
Nodes (11): addSchedule(), deleteSchedule(), fromName(), Flow, schedulePrefs(), SchedulePrefsState, schedulesFromJson(), schedulesToJson() (+3 more)

### Community 94 - "MethodInput"
Cohesion: 0.26
Nodes (12): KeyCircle(), Modifier, PatternGrid(), PinDots(), PinKeypad(), shakeEffect(), MethodInput(), AppLockGateHost() (+4 more)

### Community 95 - "AppLockViewModel"
Cohesion: 0.19
Nodes (5): AppLockUiState, AppLockViewModel, AndroidViewModel, SharedFlow, StateFlow

### Community 97 - "ScheduleEditScreen"
Cohesion: 0.28
Nodes (12): AppsCard(), appSummary(), DayCircles(), DeleteButton(), GroupLabel(), Header(), Modifier, ModeSegment() (+4 more)

### Community 98 - "04 — Security architecture"
Cohesion: 0.15
Nodes (13): 04 — Security architecture, 1. Permissions model, 2. App Lock, 3. Prevent Uninstall & Device Admin, 4. Protection layers summary, 5. Accessibility protection (self-heal), Accessibility guards (`service/SafeMeAccessibilityService.handlePreventUninstall`), Device Admin (`protect/DeviceAdminUtils.kt`) (+5 more)

### Community 99 - "LockType"
Cohesion: 0.16
Nodes (11): appLockPrefs(), fromStorage(), Flow, LockType, OFF, PASSWORD, PATTERN, PIN (+3 more)

### Community 101 - "A11yProtectionGuard"
Cohesion: 0.33
Nodes (3): A11yProtectionGuard, getInstance(), Context

### Community 103 - "05 — Blocking engine (accessibility service)"
Cohesion: 0.17
Nodes (12): 05 — Blocking engine (accessibility service), 1. Event pipeline, 2. Rule sources, 3. Matching semantics, 4. Block gate (`BlockGateActivity`), 5. Robustness guarantees, 6. Relationship to other features, Cooldowns & dedup (+4 more)

### Community 104 - "07 — VPN / DNS filtering"
Cohesion: 0.15
Nodes (13): 07 — VPN / DNS filtering, 1. Architecture: DNS-delegated filtering, 2. Tunnel modes, 3. Lifecycle, 4. Watchdog (`vpn/TunnelRestartPolicy.kt`), 5. Schedule integration, 6. Boot re-arm & status, 7. UI (`ui/screens/vpn/`) (+5 more)

### Community 105 - "renderAppPicker"
Cohesion: 0.26
Nodes (12): APP_CATS, appChip(), APPS, classifyApp(), deselectAllApps(), deselectAllVpnApps(), refreshAppPicker(), renderAppPicker() (+4 more)

### Community 106 - "AutoLockDelay"
Cohesion: 0.20
Nodes (8): AutoLockDelay, AFTER_15S, AFTER_1M, AFTER_30S, AFTER_5M, IMMEDIATELY, OFF, AppLockStateHolder

### Community 107 - "ScheduleBlock"
Cohesion: 0.25
Nodes (4): newScheduleId(), ScheduleBlock, ActiveRules, ScheduleEvaluator

### Community 108 - "shouldShowA11yWarning"
Cohesion: 0.36
Nodes (3): requiresAccessibility(), shouldShowA11yWarning(), ScheduleWarningTest

### Community 109 - "Intent"
Cohesion: 0.29
Nodes (7): Context, IBinder, Intent, SafeMeProtectionService, start(), stop(), Service

### Community 114 - "08 — Backup & Restore"
Cohesion: 0.20
Nodes (9): 08 — Backup & Restore, 1. Files & responsibilities, 2. File format, 3. Export, 4. Import & validation, 5. UX flow, 6. Notes & behavior verified on-device, Restore (`executeRestore`) (+1 more)

### Community 115 - "2. Principles"
Cohesion: 0.20
Nodes (10): 10 — Performance, 1. Measured results (baseline → after), 2. Principles, 3. What is deliberately NOT optimized further, 4. Verification workflow, Bound every per-event cost, Compose hygiene, Gate background work by need (+2 more)

### Community 116 - "TitleBlockScreen"
Cohesion: 0.33
Nodes (10): EmptyCard(), Header(), HeroCard(), ImageVector, modeLabel(), RuleRow(), Seg(), SheetField() (+2 more)

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

### Community 126 - "SafeMe Bundled Blocking Dataset (Engine-Only · Hidden from UI)"
Cohesion: 0.25
Nodes (7): ADULT — Keywords (21), ADULT — Websites (93), Dataset totals (ADULT ONLY), IMPORTANT — Engine-Only Usage, Integrity, Integrity footer, SafeMe Bundled Blocking Dataset (Engine-Only · Hidden from UI)

### Community 127 - "06 — Schedule-based blocking"
Cohesion: 0.25
Nodes (8): 06 — Schedule-based blocking, 1. Data model (`data/SchedulePrefs.kt`), 2. Pure decision core (`protect/ScheduleEvaluator.kt`), 3. Coordinator (`protect/ScheduleEngine.kt`), 4. Alarm & boot (`protect/ScheduleAlarmReceiver.kt`), 5. Safety ticker (`SafeMeApp`), 6. Enforcement surfaces, 7. Editing flow (`ui/screens/schedule/`)

### Community 128 - "ScheduleMode"
Cohesion: 0.29
Nodes (5): ScheduleMode, BOTH, INTERNET, LAUNCH, scheduleModeLabel()

### Community 130 - "ScheduleAlarmReceiver"
Cohesion: 0.38
Nodes (5): BroadcastReceiver, Context, Intent, ScheduleAlarmReceiver, scheduleBoundary()

### Community 132 - "Outcome"
Cohesion: 0.33
Nodes (4): Outcome, KEEP_WAITING, TUNNEL_DEAD, TunnelRestartPolicy

### Community 133 - "09 — App Picker"
Cohesion: 0.29
Nodes (7): 09 — App Picker, 1. Discovery (`data/AppCatalog.kt`), 2. Category taxonomy, 3. Grouping & search, 4. Presentation (`ui/components/GroupedAppPicker.kt`), 5. Consumers, Classification precedence (`categorize`)

### Community 135 - "A11yBootReceiver"
Cohesion: 0.33
Nodes (4): A11yBootReceiver, BroadcastReceiver, Context, Intent

### Community 138 - "saveSchedule"
Cohesion: 0.33
Nodes (6): daysLabel(), delSchedule(), modeTxt(), saveSchedule(), schedCardHTML(), schedCount()

### Community 140 - "grantPerm"
Cohesion: 0.50
Nodes (5): finishOnboard(), grantPerm(), permAdvance(), permStatus(), skipPerm()

## Knowledge Gaps
- **249 isolated node(s):** `SCREENS`, `ORDER`, `stack`, `groups`, `ptb` (+244 more)
  These have ≤1 connection - possible missing edges or undocumented components.
- **26 thin communities (<3 nodes) omitted from report** — run `graphify query` to explore isolated nodes.

## Suggested Questions
_Questions this graph is uniquely positioned to answer:_

- **Why does `MainScreen()` connect `MainScreen` to `DnsVpnScreen.kt`, `ScheduleEditScreen`, `KeywordManagerViewModel`, `AccessibilityProtectionScreen`, `FocusScreen.kt`, `BlockingScreen.kt`, `HomeScreen.kt`, `AppLockScreen.kt`, `BackupScreen`, `BlockScreen`, `ToastHost`, `ProfileScreen.kt`, `NavItem`, `TitleBlockScreen`, `QuickActionType`, `OnboardingNavHost`?**
  _High betweenness centrality (0.157) - this node is a cross-community bridge._
- **Why does `ToastHost()` connect `ToastHost` to `DnsVpnScreen.kt`, `ScheduleEditScreen`, `KeywordManagerViewModel`, `AccessibilityProtectionScreen`, `FocusScreen.kt`, `BlockingScreen.kt`, `HomeScreen.kt`, `BackupScreen`, `AppLockScreen.kt`, `ProfileScreen.kt`, `TitleBlockScreen`?**
  _High betweenness centrality (0.084) - this node is a cross-community bridge._
- **Why does `DnsVpnScreen()` connect `DnsVpnScreen.kt` to `ToastHost`, `VpnAppsSheet`, `MainScreen`, `DnsVpnViewModel`?**
  _High betweenness centrality (0.070) - this node is a cross-community bridge._
- **What connects `SCREENS`, `ORDER`, `stack` to the rest of the system?**
  _249 weakly-connected nodes found - possible documentation gaps or missing edges._
- **Should `KeywordManagerViewModel` be split into smaller, more focused modules?**
  _Cohesion score 0.08503401360544217 - nodes in this community are weakly interconnected._
- **Should `SafeMeVpnService` be split into smaller, more focused modules?**
  _Cohesion score 0.08888888888888889 - nodes in this community are weakly interconnected._
- **Should `BlockingPrefs.kt` be split into smaller, more focused modules?**
  _Cohesion score 0.06234567901234568 - nodes in this community are weakly interconnected._