# Graph Report - SafeMe  (2026-09-10)

## Corpus Check
- 180 files · ~148,218 words
- Verdict: corpus is large enough that graph structure adds value.

## Summary
- 2290 nodes · 4875 edges · 128 communities (97 shown, 26 thin omitted)
- Extraction: 96% EXTRACTED · 4% INFERRED · 0% AMBIGUOUS · INFERRED: 217 edges (avg confidence: 0.85)
- Token cost: 0 input · 0 output

## Graph Freshness
- Built from commit: `36826a61`
- Run `git rev-parse HEAD` and compare to check if the graph is stale.
- Run `graphify update .` after code changes (no API cost).

## Community Hubs (Navigation)
- BackupScreen.kt
- grantPerm
- KeywordManagerScreen.kt
- SafeMeVpnService
- TitleBlockRule
- ImageVideoSearchGateTest
- Social Media Blocking — Detailed Execution Plan (SafeMe)
- DnsVpnViewModel
- BlockedCounterTest
- SchedulePrefs.kt
- BlockingScreen.kt
- HomeScreen.kt
- ScheduleEvaluatorTest
- BackupCodecTest
- BlockScreen.kt
- BlockingViewModel.kt
- ScheduleScreen.kt
- ScheduleSheets.kt
- ProfileScreen.kt
- ScheduleEditScreen.kt
- ScheduleViewModel.kt
- preventUninstallPrefs
- BlockSheets.kt
- PrivateDnsBlockersTest
- DnsVpnScreen.kt
- SocialBlockingViewModel
- Social Media Blocking — Final Execution Plan (Deep Reanalysis)
- BackupCodec
- VpnBootReceiver.kt
- ViewModel
- VpnValidationTest
- VpnBlockersTest
- PermissionScreen.kt
- Composable
- HomeViewModel.kt
- TitleBlockScreen.kt
- Color.kt
- PrivateDnsFilter
- VpnStatusStore
- gradlew
- DnsVpnViewModel.kt
- env.sh
- ScheduleBlock
- MutableInteractionSource
- bootstrap.sh
- SafeMeAccessibilityService
- ToastHost
- resolveEditedEnabled
- SafeMe sandbox toolchain — wipe survival kit
- app.js
- QuickActionType
- DeviceAdminUtils
- A11yProtectionUtils
- MainScreen.kt
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
- .add
- Part 1 — NopoX 1.0.53 reverse engineering
- LockType
- SocialBlockingScreen.kt
- BlockedKeyword
- 02 — Design philosophy
- .seededStores
- ScheduleMode
- AppLockManager
- AppLockScreen.kt
- check_bundled_counts.py
- SocialBlockingGate
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
- ServicePickerScreen.kt
- isValidScheduleWindow
- ScheduleEngine
- resolveWhitelistSeed
- AppLockViewModel
- BackupSection
- BrowserUrlGate
- 04 — Security architecture
- AntiTamperScreen.kt
- AccessibilityProtectionScreen.kt
- BackupError
- 05 — Blocking engine (accessibility service)
- 07 — VPN / DNS filtering
- renderSocial
- AutoLockDelay
- ProtectionLayersEvaluator
- VpnValidation
- kwRender
- JsoncTest
- qaRender
- 08 — Backup & Restore
- 2. Principles
- ProtectionLayersTest
- Release signing
- SafeMe
- checkUnlock
- SafeMeAccessibilityServiceAppContentRecheckTest
- AppLockGateController
- SafeMe Bundled Blocking Dataset (Engine-Only · Hidden from UI)
- 06 — Schedule-based blocking
- ScheduleAlarmReceiver.kt
- TunnelRestartPolicy
- 09 — App Picker
- TunnelRestartPolicyTest
- saveSchedule
- Jsonc

## God Nodes (most connected - your core abstractions)
1. `SafeMeAccessibilityService` - 69 edges
2. `toast()` - 43 edges
3. `ScheduleEvaluatorTest` - 40 edges
4. `BackupCodecTest` - 36 edges
5. `ToastHost()` - 35 edges
6. `BlockingPrefsState` - 34 edges
7. `DnsVpnViewModel` - 34 edges
8. `SafeMeTextField()` - 30 edges
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

## Communities (128 total, 26 thin omitted)

### Community 0 - "BackupScreen.kt"
Cohesion: 0.14
Nodes (22): BackupParseResult, Failure, RestoreResult, Success, BackupFile, createBackup(), ActionPill(), BackupActionCard() (+14 more)

### Community 1 - "grantPerm"
Cohesion: 0.40
Nodes (5): finishOnboard(), grantPerm(), permAdvance(), permStatus(), skipPerm()

### Community 2 - "KeywordManagerScreen.kt"
Cohesion: 0.09
Nodes (34): BlockedCategory, ADULT, CUSTOM, DISTRACTION, GAMBLING, SHOPPING, SOCIAL_MEDIA, ActionButton() (+26 more)

### Community 3 - "SafeMeVpnService"
Cohesion: 0.21
Nodes (7): DnsVpnSettings, Context, IBinder, Intent, SafeMeVpnService, ParcelFileDescriptor, VpnService

### Community 4 - "TitleBlockRule"
Cohesion: 0.17
Nodes (16): addTitleBlockRule(), deleteTitleBlockRule(), TitleBlockRule, TitleMatchMode, CONTAINS, EXACT, STARTS_WITH, titleRulesFromJson() (+8 more)

### Community 6 - "Social Media Blocking — Detailed Execution Plan (SafeMe)"
Cohesion: 0.06
Nodes (35): 0. Summary, 10. Navigation, 11. Step-by-Step Execution Order (dependencies, files, estimates), 12. Testing Strategy, 13. Risks & Mitigations, 14. Acceptance Criteria / QA Checklist, 15. What I Need From You to Execute, 1. Goals & Non-Goals (+27 more)

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
Nodes (42): ActivityEntry, activityFromJson(), activityLog(), activityToJson(), addActivity(), appendActivity(), formatActivityTime(), Flow (+34 more)

### Community 14 - "BlockScreen.kt"
Cohesion: 0.26
Nodes (15): BlockScreen(), bsImgColors(), CustomSwitch(), GhostBlockButton(), GradientTile(), GroupLabel(), HeaderRow(), Color (+7 more)

### Community 15 - "BlockingViewModel.kt"
Cohesion: 0.15
Nodes (10): setBlockingExcludedApps(), contentEnginePrefs(), ContentEnginePrefsState, Flow, setBlockImageVideoSearch(), BlockingUiState, BlockingViewModel, AndroidViewModel (+2 more)

### Community 16 - "ScheduleScreen.kt"
Cohesion: 0.20
Nodes (23): A11yWarningBanner(), cardShape(), ExactAlarmBanner(), exactAlarmSettingsIntent(), ExcludeAppsCard(), HeroCard(), HeroPill(), HeroRings() (+15 more)

### Community 17 - "ScheduleSheets.kt"
Cohesion: 0.22
Nodes (18): AppPickerSheet(), AppRow(), CheckBox(), GrabBar(), h12(), Modifier, PrimaryPill(), SearchField() (+10 more)

### Community 18 - "ProfileScreen.kt"
Cohesion: 0.05
Nodes (51): AccessibilityProtectionCopyTest, Flow, markOnboardingComplete(), onboardingComplete(), setThemePref(), ThemePref, DARK, LIGHT (+43 more)

### Community 19 - "ScheduleEditScreen.kt"
Cohesion: 0.26
Nodes (14): AppsCard(), appSummary(), DayCircles(), DeleteButton(), EnabledRow(), GroupLabel(), Header(), Modifier (+6 more)

### Community 20 - "ScheduleViewModel.kt"
Cohesion: 0.14
Nodes (10): scheduleTimeLabel(), scheduleWindowLabel(), AndroidViewModel, SharedFlow, StateFlow, ScheduleCard, ScheduleUiState, ScheduleViewModel (+2 more)

### Community 21 - "preventUninstallPrefs"
Cohesion: 0.20
Nodes (9): Flow, preventUninstallPrefs(), PreventUninstallPrefsState, setPreventUninstallEnabled(), AntiTamperUiState, AntiTamperViewModel, AndroidViewModel, SharedFlow (+1 more)

### Community 22 - "BlockSheets.kt"
Cohesion: 0.38
Nodes (9): CustomMessageSheet(), ImgTile(), androidx, Color, Modifier, MotivationImageSheet(), SheetField(), SheetGrab() (+1 more)

### Community 24 - "DnsVpnScreen.kt"
Cohesion: 0.14
Nodes (22): DnsPresetList(), DnsVpnScreen(), GroupLabel(), androidx, Color, Modifier, NotifSeg(), VpnDivider() (+14 more)

### Community 25 - "SocialBlockingViewModel"
Cohesion: 0.10
Nodes (16): applySocialPreset(), Flow, setSocialBlockingEnabled(), setSocialFacebook(), setSocialSnapchat(), setSocialWholeBlocked(), setSocialYoutube(), SocialBlockingPrefs (+8 more)

### Community 26 - "Social Media Blocking — Final Execution Plan (Deep Reanalysis)"
Cohesion: 0.07
Nodes (28): 10. Risks & Mitigations, 11. Acceptance Criteria, 12. Ready to Execute, 1.1 Prototype audit (`Reference/prototype`), 1.2 Kotlin audit (`app/src/main`), 1.3 Gap vs. desired, 1. Deep Reanalysis — What We Actually Found, 2. Requirements Traceability (+20 more)

### Community 27 - "BackupCodec"
Cohesion: 0.27
Nodes (4): BackupCodec, InvalidBackupException, T, Exception

### Community 28 - "VpnBootReceiver.kt"
Cohesion: 0.53
Nodes (4): BroadcastReceiver, Context, Intent, VpnBootReceiver

### Community 29 - "ViewModel"
Cohesion: 0.21
Nodes (15): ManagePermissionsFlow(), OnboardingNavHost(), StateFlow, OnboardingViewModel, AccessibilityPermissionStep(), BatteryPermissionStep(), hasNotificationsPermission(), Context (+7 more)

### Community 32 - "PermissionScreen.kt"
Cohesion: 0.18
Nodes (16): blurredShadow(), Color, Dp, Modifier, BackChip(), GrantPill(), HeroTitle(), Color (+8 more)

### Community 33 - "Composable"
Cohesion: 0.23
Nodes (18): Modifier, SafeMeTextField(), AppRow(), CustomDnsSheet(), androidx, FocusRequester, Modifier, VpnAppsSheet() (+10 more)

### Community 34 - "HomeViewModel.kt"
Cohesion: 0.22
Nodes (8): A11yStatus, HomeUiState, HomeViewModel, AndroidViewModel, Job, SharedFlow, StateFlow, PrefsSnapshot

### Community 35 - "TitleBlockScreen.kt"
Cohesion: 0.27
Nodes (12): EmptyCard(), Header(), HeroCard(), ImageVector, modeLabel(), RuleRow(), SearchField(), Seg() (+4 more)

### Community 36 - "Color.kt"
Cohesion: 0.07
Nodes (18): BadgeDot(), BottomNavBar(), Modifier, NavDestination, NavItem(), iconBuilder(), ImageVector, iconBuilder() (+10 more)

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

### Community 45 - "MutableInteractionSource"
Cohesion: 0.26
Nodes (15): KeyCircle(), FocusRequester, Modifier, PasswordField(), PatternGrid(), PinDots(), PinKeypad(), shakeEffect() (+7 more)

### Community 46 - "bootstrap.sh"
Cohesion: 0.36
Nodes (9): ANDROID_HOME, ANDROID_SDK_ROOT, ensure_jdk(), JAVA_HOME, ok(), PATH, bootstrap.sh script, step() (+1 more)

### Community 47 - "SafeMeAccessibilityService"
Cohesion: 0.06
Nodes (18): AccessibilityEvent, AccessibilityService, UninstallBlockers, PrivateDnsBlockers, ProtectedSystemPages, VpnBlockers, EventSnapshot, isOwnUiEvent() (+10 more)

### Community 48 - "ToastHost"
Cohesion: 0.26
Nodes (12): HostToast, Flow, Modifier, ToastHost(), ToastPill(), AboutHeader(), AboutIdentityCard(), AboutLinksList() (+4 more)

### Community 50 - "SafeMe sandbox toolchain — wipe survival kit"
Cohesion: 0.25
Nodes (7): Last verified state, Recovery (one command), SafeMe sandbox toolchain — wipe survival kit, The problem, Two locations (identical files), What persists vs what doesn't, Why these exact choices

### Community 53 - "app.js"
Cohesion: 0.04
Nodes (44): a11yStatus(), ACTIVITY, APP_CATS, applyTheme(), appPickSel, APPS, back(), DEFAULT_APP_SEL (+36 more)

### Community 54 - "QuickActionType"
Cohesion: 0.15
Nodes (14): Flow, quickActionPrefs(), quickActionsFromJson(), quickActionsToJson(), QuickActionType, APPLOCK, BACKUP, HISTORY (+6 more)

### Community 55 - "DeviceAdminUtils"
Cohesion: 0.36
Nodes (5): DeviceAdminUtils, Context, Intent, SafeMeDeviceAdminReceiver, DeviceAdminReceiver

### Community 56 - "A11yProtectionUtils"
Cohesion: 0.06
Nodes (33): a11yProtectionPrefs(), A11yProtectionPrefsState, addProtectedA11yComponent(), Flow, removeProtectedA11yComponent(), setA11yProtectionEnabled(), writeA11yProtectionPrefs(), A11yBootReceiver (+25 more)

### Community 57 - "MainScreen.kt"
Cohesion: 0.48
Nodes (5): ExcludeAppsCard(), OtherFeaturesHeaderRow(), OtherFeaturesScreen(), MainScreen(), PlaceholderScreen()

### Community 58 - "toast"
Cohesion: 0.11
Nodes (29): addKeyword(), addSite(), addTitle(), appsDone(), cancelDelay(), closeBlockov(), closeSheets(), delTitle() (+21 more)

### Community 59 - "BlockingPrefs.kt"
Cohesion: 0.19
Nodes (27): addBlockedKeyword(), addBlockedWebsite(), addTrustedWebsite(), addWhitelistKeyword(), blockedTodayFlow(), blockingEnabled(), blockingPrefs(), keywordsFromJson() (+19 more)

### Community 60 - "BackupStateStore"
Cohesion: 0.10
Nodes (12): A11yProtectionStore, AppLockStore, BackupStateStore, backupStores(), BlockingStore, BlockScreenStore, ContentEngineStore, PreventUninstallStore (+4 more)

### Community 61 - "BlockOverlayControllerTest"
Cohesion: 0.06
Nodes (24): BlockGate(), BlockGateActivity, Bundle, blockActivitySub(), blockActivityTitle(), blockGateMessage(), blockGateWhyReason(), BlockOverlayController (+16 more)

### Community 63 - "ScheduleWarningTest"
Cohesion: 0.24
Nodes (4): requiresAccessibility(), shouldShowA11yWarning(), shouldShowExactAlarmWarning(), ScheduleWarningTest

### Community 64 - "Arrangement"
Cohesion: 0.16
Nodes (16): AppCatalog, AppCategory, GAMES, MESSAGING, NEWS_PROD, OTHER, PAYMENT, SHOPPING (+8 more)

### Community 65 - "ScheduleEditViewModel"
Cohesion: 0.12
Nodes (8): newScheduleId(), Factory, AndroidViewModel, SharedFlow, StateFlow, T, ScheduleEditUiState, ScheduleEditViewModel

### Community 67 - "BlockingPrefsState"
Cohesion: 0.13
Nodes (5): BlockingPrefsState, ImageVideoSearchGate, Match, isExcludedFromContentEngine(), ExcludeAppsE2ETest

### Community 68 - ".add"
Cohesion: 0.26
Nodes (10): AddBadge(), ArrowButton(), EditActionRow(), EditHeader(), EditSectionTitle(), AndroidViewModel, StateFlow, QuickActionsEditScreen() (+2 more)

### Community 69 - "Part 1 — NopoX 1.0.53 reverse engineering"
Cohesion: 0.11
Nodes (18): 1.1 Architecture overview, 1.2 Components and permissions (manifest), 1.3 Accessibility service configuration, 1.4 The detection core (`MyAccessibilityService.checkPreventUninstall`), 1.5 Execution flow (detection → protection), 1.6 Timing characteristics (static analysis), 1.7 Live measurement attempt, 1.8 Weaknesses, race conditions, bypasses, edge cases (+10 more)

### Community 70 - "LockType"
Cohesion: 0.22
Nodes (16): LockType, OFF, PASSWORD, PATTERN, PIN, AppLockSetupSheet(), AutoLockSheet(), CheckBox() (+8 more)

### Community 71 - "SocialBlockingScreen.kt"
Cohesion: 0.27
Nodes (15): FootnoteCard(), Color, Modifier, LaunchBlockSection(), LaunchItem, LaunchRow(), MasterCard(), PresetCard() (+7 more)

### Community 72 - "BlockedKeyword"
Cohesion: 0.18
Nodes (4): BlockedKeyword, BlockedWebsite, BundledKeywords, BrowserUrlGateTest

### Community 73 - "02 — Design philosophy"
Cohesion: 0.11
Nodes (18): 02 — Design philosophy, 1.1 Fail open, never fail closed on detection, 1.2 Add-only writes for system settings, 1.3 Never lock the user out, 1.4 Idempotent coordinators, 1.5 Never crash on the user's data, 1.6 Mirror the prototype, not the reference code, 1. Core principles (+10 more)

### Community 74 - ".seededStores"
Cohesion: 0.28
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

### Community 79 - "SocialBlockingGate"
Cohesion: 0.22
Nodes (6): AccessibilityNodeInfo, SocialBlockingGate, SocialVertical, REELS, SHORTS, SPOTLIGHT

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

### Community 91 - "ServicePickerScreen.kt"
Cohesion: 0.60
Nodes (5): ProtectedServiceEntry, MasterSwitch(), PickerRow(), SearchField(), ServicePickerScreen()

### Community 95 - "AppLockViewModel"
Cohesion: 0.23
Nodes (5): AppLockUiState, AppLockViewModel, AndroidViewModel, SharedFlow, StateFlow

### Community 96 - "BackupSection"
Cohesion: 0.17
Nodes (11): BackupSection, A11Y_PROTECTION, APP_LOCK, BLOCK_SCREEN, BLOCKING, CONTENT_ENGINE, PREVENT_UNINSTALL, QUICK_ACTIONS (+3 more)

### Community 97 - "BrowserUrlGate"
Cohesion: 0.21
Nodes (3): BundledKeywordCatalog, BrowserUrlGate, UrlMatch

### Community 98 - "04 — Security architecture"
Cohesion: 0.14
Nodes (14): 04 — Security architecture, 1. Permissions model, 2. App Lock, 3. Prevent Uninstall & Device Admin, 4. Protection layers summary, 5. Accessibility protection (self-heal), Accessibility guards (`service/SafeMeAccessibilityService.handlePreventUninstall`), Device Admin (`protect/DeviceAdminUtils.kt`) (+6 more)

### Community 100 - "AntiTamperScreen.kt"
Cohesion: 0.42
Nodes (8): AntiTamperScreen(), cardShape(), Header(), Dp, Modifier, Note(), PreventUninstallCard(), ProtectBtn()

### Community 101 - "AccessibilityProtectionScreen.kt"
Cohesion: 0.19
Nodes (14): BlockGateActivityTest, AccessibilityProtectionScreen(), cardShape(), copyToClipboard(), Header(), Context, Dp, Modifier (+6 more)

### Community 102 - "BackupError"
Cohesion: 0.25
Nodes (8): BackupError, EMPTY, INVALID_STRUCTURE, NOT_JSON, NOT_SAFEME, ROLLBACK_FAILED, UNSUPPORTED_VERSION, WRITE_FAILED

### Community 103 - "05 — Blocking engine (accessibility service)"
Cohesion: 0.17
Nodes (12): 05 — Blocking engine (accessibility service), 1. Event pipeline, 2. Rule sources, 3. Matching semantics, 4. Block gate (`BlockGateActivity`), 5. Robustness guarantees, 6. Relationship to other features, Cooldowns & dedup (+4 more)

### Community 104 - "07 — VPN / DNS filtering"
Cohesion: 0.15
Nodes (13): 07 — VPN / DNS filtering, 1. Architecture: DNS-delegated filtering, 2. Tunnel modes, 3. Lifecycle, 4. Watchdog (`vpn/TunnelRestartPolicy.kt`), 5. Schedule integration, 6. Boot re-arm & status, 7. UI (`ui/screens/vpn/`) (+5 more)

### Community 105 - "renderSocial"
Cohesion: 0.13
Nodes (26): actAdd(), actDot(), appByName(), appChip(), applyPreset(), classifyApp(), deselectAllApps(), deselectAllSocialApps() (+18 more)

### Community 106 - "AutoLockDelay"
Cohesion: 0.15
Nodes (14): appLockPrefs(), AutoLockDelay, AFTER_15S, AFTER_1M, AFTER_30S, AFTER_5M, IMMEDIATELY, OFF (+6 more)

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

### Community 124 - "AppLockGateController"
Cohesion: 0.25
Nodes (3): AppLockStateHolder, AppLockGateController, StateFlow

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

## Knowledge Gaps
- **333 isolated node(s):** `SCREENS`, `ORDER`, `stack`, `groups`, `ptb` (+328 more)
  These have ≤1 connection - possible missing edges or undocumented components. (Counts symbols only; 629 node(s) total have ≤1 connection when file, concept and rationale nodes are included.)
- **26 thin communities (<3 nodes) omitted from report** — run `graphify query` to explore isolated nodes.

## Suggested Questions
_Questions this graph is uniquely positioned to answer:_

- **Why does `ToastHost()` connect `ToastHost` to `BackupScreen.kt`, `KeywordManagerScreen.kt`, `TitleBlockScreen.kt`, `AntiTamperScreen.kt`, `AccessibilityProtectionScreen.kt`, `SocialBlockingScreen.kt`, `BlockingScreen.kt`, `HomeScreen.kt`, `AppLockScreen.kt`, `ScheduleScreen.kt`, `ProfileScreen.kt`, `ScheduleEditScreen.kt`, `DnsVpnScreen.kt`, `MainScreen.kt`?**
  _High betweenness centrality (0.050) - this node is a cross-community bridge._
- **Why does `ScheduleBlock` connect `ScheduleBlock` to `ScheduleEditViewModel`, `SchedulePrefs.kt`, `.seededStores`, `ScheduleMode`, `ScheduleEvaluatorTest`, `BackupCodecTest`, `ScheduleViewModel.kt`, `ScheduleEngine`, `ScheduleWarningTest`?**
  _High betweenness centrality (0.032) - this node is a cross-community bridge._
- **Why does `BlockedCategory` connect `KeywordManagerScreen.kt` to `BlockedKeyword`, `BlockingPrefsState`, `BlockingPrefs.kt`?**
  _High betweenness centrality (0.031) - this node is a cross-community bridge._
- **What connects `SCREENS`, `ORDER`, `stack` to the rest of the system?**
  _333 weakly-connected nodes found - possible documentation gaps or missing edges._
- **Should `BackupScreen.kt` be split into smaller, more focused modules?**
  _Cohesion score 0.1354679802955665 - nodes in this community are weakly interconnected._
- **Should `KeywordManagerScreen.kt` be split into smaller, more focused modules?**
  _Cohesion score 0.08843537414965986 - nodes in this community are weakly interconnected._
- **Should `Social Media Blocking — Detailed Execution Plan (SafeMe)` be split into smaller, more focused modules?**
  _Cohesion score 0.05555555555555555 - nodes in this community are weakly interconnected._