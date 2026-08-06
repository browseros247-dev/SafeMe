# Graph Report - .  (2026-08-05)

## Corpus Check
- cluster-only mode — file stats not available

## Summary
- 892 nodes · 1707 edges · 53 communities (44 shown, 9 thin omitted)
- Extraction: 96% EXTRACTED · 4% INFERRED · 0% AMBIGUOUS · INFERRED: 67 edges (avg confidence: 0.8)
- Token cost: 0 input · 0 output

## Graph Freshness
- Built from commit: `cfaabd90`
- Run `git rev-parse HEAD` and compare to check if the graph is stale.
- Run `graphify update .` after code changes (no API cost).

## Community Hubs (Navigation)
- Community 0
- Community 1
- Community 2
- Community 3
- Community 4
- Community 5
- Community 6
- Community 7
- Community 8
- Community 9
- Community 10
- Community 11
- Community 12
- Community 13
- Community 14
- Community 15
- Community 16
- Community 17
- Community 18
- Community 19
- Community 20
- Community 21
- Community 22
- Community 23
- Community 24
- Community 25
- Community 26
- Community 27
- Community 28
- Community 29
- Community 30
- Community 31
- Community 32
- Community 33
- Community 34
- Community 35
- Community 36
- Community 37
- Community 38
- Community 39
- Community 40

## God Nodes (most connected - your core abstractions)
1. `toast()` - 33 edges
2. `TcpConnection` - 30 edges
3. `DnsVpnViewModel` - 28 edges
4. `SafeMeVpnService` - 24 edges
5. `KeywordManagerViewModel` - 22 edges
6. `BlockedCategory` - 21 edges
7. `TcpRelay` - 21 edges
8. `UdpRelay` - 19 edges
9. `VpnRelayIntegrationTest` - 19 edges
10. `BlockingRulesTest` - 18 edges

## Surprising Connections (you probably didn't know these)
- `saveWebsite()` --calls--> `normalizeDomain()`  [INFERRED]
  app/src/main/java/com/safeme/app/ui/screens/keywords/KeywordManagerScreen.kt → app/src/main/java/com/safeme/app/data/BlockingPrefs.kt
- `setVpnPreset()` --references--> `DnsPreset`  [EXTRACTED]
  app/src/main/java/com/safeme/app/data/VpnPrefs.kt → app/src/main/java/com/safeme/app/vpn/VpnConfig.kt
- `BlockingScreen()` --calls--> `ToastHost()`  [INFERRED]
  app/src/main/java/com/safeme/app/ui/screens/blocking/BlockingScreen.kt → app/src/main/java/com/safeme/app/ui/components/ToastHost.kt
- `HomeScreen()` --calls--> `ToastHost()`  [INFERRED]
  app/src/main/java/com/safeme/app/ui/screens/home/HomeScreen.kt → app/src/main/java/com/safeme/app/ui/components/ToastHost.kt
- `KeywordManagerScreen()` --calls--> `ToastHost()`  [INFERRED]
  app/src/main/java/com/safeme/app/ui/screens/keywords/KeywordManagerScreen.kt → app/src/main/java/com/safeme/app/ui/components/ToastHost.kt

## Import Cycles
- None detected.

## Communities (53 total, 9 thin omitted)

### Community 0 - "Community 0"
Cohesion: 0.08
Nodes (17): TcpHeader, ByteArrayBuffer, FlowKey, ByteArray, InetAddress, Socket, parseWindowScale(), TcpConnection (+9 more)

### Community 1 - "Community 1"
Cohesion: 0.07
Nodes (29): AccessibilityEvent, AccessibilityNodeInfo, AccessibilityService, addBlockedKeyword(), addBlockedWebsite(), addTrustedWebsite(), addWhitelistKeyword(), BlockedKeyword (+21 more)

### Community 2 - "Community 2"
Cohesion: 0.08
Nodes (34): BlockedCategory, ADULT, CUSTOM, DISTRACTION, GAMBLING, SHOPPING, SOCIAL_MEDIA, ActionButton() (+26 more)

### Community 3 - "Community 3"
Cohesion: 0.08
Nodes (17): DnsVpnSettings, setVpnEnabled(), setVpnPreset(), InetAddress, Intent, java, SafeMeVpnService, BlockingRules (+9 more)

### Community 4 - "Community 4"
Cohesion: 0.08
Nodes (20): blockedTodayFlow(), blockingEnabled(), onboardingComplete(), setThemePref(), ThemePref, DARK, LIGHT, SYSTEM (+12 more)

### Community 5 - "Community 5"
Cohesion: 0.06
Nodes (32): a11yStatus(), applyTheme(), back(), editTitle(), fmt12h(), groups, h12(), initTheme() (+24 more)

### Community 6 - "Community 6"
Cohesion: 0.10
Nodes (13): ByteOrder, InternetChecksum, IpConstants, IpHeader, IpPacket, ByteArray, InetAddress, TcpFlags (+5 more)

### Community 7 - "Community 7"
Cohesion: 0.09
Nodes (35): DnsPresetList(), DnsVpnScreen(), GroupLabel(), androidx, Color, Modifier, NotifSeg(), VpnDivider() (+27 more)

### Community 8 - "Community 8"
Cohesion: 0.07
Nodes (26): android, BlockGate(), BlockGateActivity, Bundle, ComponentActivity, incrementBlockedToday(), Bundle, ComponentActivity (+18 more)

### Community 9 - "Community 9"
Cohesion: 0.10
Nodes (28): HostToast, Modifier, ToastHost(), ToastPill(), cardShape(), FocusScreen(), HeroCard(), IconBox() (+20 more)

### Community 10 - "Community 10"
Cohesion: 0.11
Nodes (28): BlockingScreen(), cardShape(), IconBox(), IconVariant, Amber, Dark, Green, Red (+20 more)

### Community 11 - "Community 11"
Cohesion: 0.11
Nodes (26): A11yBanner(), cardShape(), FeedCard(), FeedRow(), HeroCard(), HeroRing(), HeroRings(), HomeHeaderRow() (+18 more)

### Community 12 - "Community 12"
Cohesion: 0.15
Nodes (9): Addr, ByteArray, java, Socket, SocketProtector, TunWriter, ByteArray, VpnRelayIntegrationTest (+1 more)

### Community 13 - "Community 13"
Cohesion: 0.12
Nodes (7): Fragment, FragmentReassembler, IpBuilder, Key, ByteArray, InetAddress, IpPacketTest

### Community 14 - "Community 14"
Cohesion: 0.14
Nodes (26): BlockScreen(), bsImgColors(), CustomSwitch(), GhostBlockButton(), GhostSmallButton(), GradientTile(), GroupLabel(), HeaderRow() (+18 more)

### Community 15 - "Community 15"
Cohesion: 0.14
Nodes (5): DnsVpnUiState, DnsVpnViewModel, AndroidViewModel, SharedFlow, StateFlow

### Community 16 - "Community 16"
Cohesion: 0.13
Nodes (23): cardShape(), HeroCard(), HeroPill(), HeroRings(), IconBox(), Color, Dp, ImageVector (+15 more)

### Community 17 - "Community 17"
Cohesion: 0.13
Nodes (25): addTitle(), appsDone(), cancelDelay(), clearRedirect(), closeBlockov(), closeSheets(), delTitle(), dwellStep() (+17 more)

### Community 18 - "Community 18"
Cohesion: 0.22
Nodes (23): cardShape(), DeleteButton(), DeleteDialog(), Footer(), GroupLabel(), IconBox(), IdentityCard(), Color (+15 more)

### Community 19 - "Community 19"
Cohesion: 0.17
Nodes (15): getWizCode(), openLockSetup(), patTap(), pinBackS(), refreshWizBtn(), renderDotsId(), resetWiz(), saveLock() (+7 more)

### Community 20 - "Community 20"
Cohesion: 0.38
Nodes (4): DnsCodec, DnsQuery, DnsQuestion, ByteArray

### Community 22 - "Community 22"
Cohesion: 0.20
Nodes (3): BlockScreenViewModel, AndroidViewModel, StateFlow

### Community 23 - "Community 23"
Cohesion: 0.42
Nodes (7): BadgeDot(), BottomNavBar(), Modifier, NavDestination, NavItem(), MainScreen(), PlaceholderScreen()

### Community 25 - "Community 25"
Cohesion: 0.25
Nodes (9): addKeyword(), addSite(), applyKwFilter(), applySiteFilter(), filterKwAll(), filterSites(), openManage(), removeRow() (+1 more)

### Community 26 - "Community 26"
Cohesion: 0.25
Nodes (9): checkUnlock(), closeLockov(), lockNow(), methodLabel(), patUnlock(), pickAuto(), renderLock(), renderUnlock() (+1 more)

### Community 28 - "Community 28"
Cohesion: 0.33
Nodes (4): Context, Intent, VpnBootReceiver, BroadcastReceiver

### Community 29 - "Community 29"
Cohesion: 0.33
Nodes (3): StateFlow, OnboardingViewModel, ViewModel

### Community 31 - "Community 31"
Cohesion: 0.33
Nodes (6): daysLabel(), delSchedule(), modeTxt(), saveSchedule(), schedCardHTML(), schedCount()

### Community 32 - "Community 32"
Cohesion: 0.40
Nodes (4): blurredShadow(), Color, Dp, Modifier

### Community 34 - "Community 34"
Cohesion: 0.40
Nodes (5): finishOnboard(), grantPerm(), permAdvance(), permStatus(), skipPerm()

### Community 35 - "Community 35"
Cohesion: 0.40
Nodes (5): openKwEdit(), openSheet(), openSiteEdit(), startDelay(), testDelay()

### Community 39 - "Community 39"
Cohesion: 0.83
Nodes (3): gradlew script, die(), warn()

## Knowledge Gaps
- **36 isolated node(s):** `ADULT`, `GAMBLING`, `SOCIAL_MEDIA`, `SHOPPING`, `DISTRACTION` (+31 more)
  These have ≤1 connection - possible missing edges or undocumented components.
- **9 thin communities (<3 nodes) omitted from report** — run `graphify query` to explore isolated nodes.

## Suggested Questions
_Questions this graph is uniquely positioned to answer:_

- **Why does `ToastHost()` connect `Community 9` to `Community 2`, `Community 4`, `Community 7`, `Community 10`, `Community 11`, `Community 16`, `Community 18`?**
  _High betweenness centrality (0.240) - this node is a cross-community bridge._
- **Why does `Flow` connect `Community 4` to `Community 1`, `Community 3`, `Community 9`?**
  _High betweenness centrality (0.217) - this node is a cross-community bridge._
- **Why does `MainScreen()` connect `Community 23` to `Community 2`, `Community 7`, `Community 9`, `Community 10`, `Community 11`, `Community 14`, `Community 16`, `Community 18`?**
  _High betweenness centrality (0.111) - this node is a cross-community bridge._
- **What connects `ADULT`, `GAMBLING`, `SOCIAL_MEDIA` to the rest of the system?**
  _36 weakly-connected nodes found - possible documentation gaps or missing edges._
- **Should `Community 0` be split into smaller, more focused modules?**
  _Cohesion score 0.08322026232473993 - nodes in this community are weakly interconnected._
- **Should `Community 1` be split into smaller, more focused modules?**
  _Cohesion score 0.06605222734254992 - nodes in this community are weakly interconnected._
- **Should `Community 2` be split into smaller, more focused modules?**
  _Cohesion score 0.08326530612244898 - nodes in this community are weakly interconnected._