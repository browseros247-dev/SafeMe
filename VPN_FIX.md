# SafeMe VPN — Root Cause & Production Fix

## 0.5 Round 3 — VPN status, DNS filtering, onboarding cleanup

### Real-time VPN status (system-level disable now reflected instantly)

Previously the UI relied on a static `SafeMeVpnService.isActive` flag and the
persisted `enabled` preference — neither can see the system revoke the VPN
(notification shade / Settings). Now:

- A process-wide `VpnStatusStore` (`StateFlow<Boolean>`) is updated by the
  service on tunnel start, stop, teardown, and — critically — on
  `VpnService.onRevoke()`, where the service also persists `enabled=false` so
  the UI and the boot receiver agree with reality.
- `DnsVpnViewModel` collects the store, so the VPN screen's pill/status
  ("Active/Off", "Tap to re-enable protection") updates in real time.
- The toggle recovers a stale "on" intent: if `enabled=true` but the tunnel is
  down, tapping re-establishes instead of flipping the setting off.

### DNS filtering robustness (adult blocking)

DNS queries are intercepted on UDP/TCP port 53 in the packet engine and
matched against the bundled adult dataset + user rules. Added a fallback
`BlockingRules.rawMatch()` that scans the raw DNS payload case-insensitively
for blocked domains when the question section can't be decoded normally
(odd/compressed encodings), wired into both the UDP and TCP DNS paths. Blocked
queries get an NXDOMAIN response; unparseable-but-blocked queries are dropped
rather than forwarded. (DoH/DoT on 443/853 cannot be filtered without TLS
interception — inherent to non-root VPNs.)

### Onboarding cleanup

- Removed the "Exact alarms" permission screen from the flow (and its
  `SCHEDULE_EXACT_ALARM` manifest permission + strings); the step indicator is
  now dynamic (3 steps).
- If all required permissions (notifications + accessibility) are already
  granted, the onboarding screens are skipped entirely and the app opens
  directly to the main screen, persisting completion in the background.

### General fixes

- Home "master protection" toggle now persists to the shared blocking setting
  (previously cosmetic) and the blocked-today stat shows the real counter.
- Whitelist-sheet app icons are loaded off the main thread.

---

## 0. Stability round 2 — launch crash, VPN-enable crash, screen delay

### Issue 1: app sometimes closes immediately after launch (intermittent)

**Root cause A — unsafe Activity cast in the theme.**
`SafeMeApp` (used by `MainActivity` *and* `BlockGateActivity`) applied the
status-bar theming with:

```kotlin
val window = (view.context as android.app.Activity).window
```

`view.context` is not guaranteed to be an `Activity` — Android frequently
wraps it in a `ContextThemeWrapper`, so this `ClassCastException` crashed the
process on some devices/themes, intermittently. Fixed with a safe
`Context.findActivity()` that walks the `ContextWrapper` chain and skips the
theming when no Activity is reachable.

**Root cause B — unguarded DataStore flows.**
Every screen/service collected DataStore flows (`onboardingComplete()`,
`themePref()`, `dnsVpnSettings()`, `blockingPrefs()`, …) without any error
handling. If the on-disk preferences file is corrupt or locked (common after
the process is killed mid-write — Android apps are killed all the time), the
flow throws and the exception propagates out of `collectAsState` /
`viewModelScope.launch { collect }` → **process crash at launch**. Fixed by
adding `.catch { emit(default) }` to every DataStore accessor so a corrupt
store degrades to safe defaults instead of crashing.

**Root cause C — `checkNotNull(LocalActivity.current)` in `OnboardingNavHost`.**
Hardened to fall back to `context.findActivity()` / default `viewModel()`.

### Issue 2: enabling the VPN crashes the app

**Root cause — guaranteed tunnel restart ~1.5 s after every enable + a race.**
`registerDefaultNetworkCallback()` delivers `onAvailable` for the current
default network *immediately after registration*. The old recovery logic
treated that as a network change and scheduled a tunnel restart ~1.5 s after
every enable. During that restart, the TUN reader could still be processing a
packet when the TCP relay's connect pool had already been shut down →
`RejectedExecutionException` from `connectExecutor.execute(...)` propagated
out of the packet loop and **crashed the whole process** (an uncaught
exception on any thread kills the Android process). `onCapabilitiesChanged`
(which fires repeatedly on the same network) made it worse.

Fixed:
- The network callback now tracks the current default `Network` and restarts
  only on a **real handover** (a different network appears, or the current one
  is lost). The initial `onAvailable` after registration is treated as the
  baseline; same-network capability churn is ignored. The tracked network is
  cleared on unregister.
- `TcpRelay.submitConnect` never throws (`RejectedExecutionException` after
  shutdown is swallowed); `handle()` is wrapped so a malformed/racing segment
  is dropped per-connection instead of crashing the process.
- All engine threads (`TunReader`, TCP sweeper, UDP selector loop) catch
  `Throwable` — a transient error can never take down the VPN or the app.
- Restart callbacks are cancellable: `startTunnel`/`stopTunnel` clear any
  pending restart, and a cooldown prevents restart storms.
- `onStartCommand` dispatches through a safe executor wrapper — no
  `RejectedExecutionException` on the main thread during teardown.

### Issue 3: VPN details screen opens with a significant delay

**Root cause — PackageManager work on the main thread during first frame.**
`DnsVpnViewModel.init` → `loadInstalledApps()` → `enumerateApps()` ran on
`Dispatchers.Main.immediate`, so `queryIntentActivities` + per-app
`loadLabel` blocked the main thread before the screen's first frame was drawn.

Fixed: enumeration now runs on `Dispatchers.Default`; the screen renders
immediately, the whitelist sheet shows a "Loading apps…" state, and the list
fills in asynchronously. Also, notification-text changes no longer restart
the tunnel on every keystroke — the running service refreshes its
notification via a new `ACTION_UPDATE_NOTIF` instead.

---

## 1. Root cause: "no app has internet when the VPN is enabled"

The old `SafeMeVpnService` established a real TUN interface and routed
**all** traffic into it:

```kotlin
.addRoute("0.0.0.0", 0)
.addRoute("::", 0)
```

…but the packet loop read packets from the tunnel and **discarded them**:

```kotlin
private fun handlePacket(buffer: ByteBuffer) {
    ...
    // For DNS & VPN filtering, packets are simply acknowledged through the tunnel.
    // Full protocol handling is intentionally omitted; the tunnel keeps traffic flowing.
}
```

A TUN device is not a magic bypass: the kernel delivers every captured packet
to the app, and **the app must forward the packet out the real network and
write responses back into the TUN**. Because nothing was ever forwarded or
written back, every outbound packet from every app entered the TUN and was
silently dropped — a total network blackout while the VPN was on.

Additional defects in the old implementation that compounded the problem:

- **Multiple threads shared a single `FileInputStream` on the TUN fd** →
  races, corrupted reads, packet loss.
- **No socket protection** (`VpnService.protect`) — any relayed socket would
  loop back into the tunnel.
- **No DNS relay or filtering at all** — the app's core feature did nothing.
- **No recovery logic** — no restart on network change, no handling of tunnel
  loss, stop/start races (`running` flag could wedge the service off after a
  config change).
- Pointless `Selector` loop and oversized 32 KB read buffer.

## 2. The fix: a complete userspace packet engine

The VPN now runs a real packet engine (`com.safeme.app.vpn`) that implements
the client side of the network for every app:

| Component | File | Role |
|---|---|---|
| IP/TCP/UDP/ICMP codecs | `IpPacket.kt`, `IpBuild.kt` | Header parse/build, RFC 1071 checksums, IPv4/IPv6 builders |
| Fragmentation | `IpBuild.kt` (`FragmentReassembler`) | Inbound fragment reassembly; outbound MTU-safe fragmentation |
| DNS codec | `DnsMessage.kt` | Query parse, name decode (compression), NXDOMAIN response synthesis |
| Rules engine | `BlockingRules.kt` | Domain/keyword blocking with trusted/whitelist overrides |
| TUN writer / protector | `TunWriter.kt` | Serialized TUN writes; `VpnService.protect` interface |
| UDP relay | `UdpRelay.kt` | Datagram forwarding (all UDP), DNS-over-UDP filtering, per-flow selector |
| TCP relay | `TcpRelay.kt` | Full userspace TCP state machine (SYN/SYN-ACK, seq/ack, MSS clamp, GBN retransmission, FIN/RST, backpressure), DNS-over-TCP filtering |
| Engine | `VpnEngine.kt` | Single TUN reader loop, dispatch, ICMP echo, lifecycle |
| Service | `SafeMeVpnService.kt` | Tunnel setup, serialized start/stop, network-change restart, notification |

### Default policy: forward everything

> "It must not block or interrupt network access unless a specific
> application, domain, keyword, or user-defined rule is intentionally
> configured to do so."

- **TCP / UDP / ICMP**: always forwarded. Blocking is applied **only** to DNS
  names that match a rule (bundled adult dataset + user blocked websites /
  keywords), minus trusted/whitelisted entries.
- Blocked DNS queries get a synthesized **NXDOMAIN** response (fast fail,
  standard behaviour), and the app's "blocked today" counter is incremented.
- Excluded applications keep bypassing via `addDisallowedApplication` (unchanged
  semantics; the service also excludes itself defensively).
- Every real socket is `protect()`ed so relayed traffic never re-enters the TUN.

### IPv4 / IPv6

Both families are parsed, relayed, filtered and fragmented. IPv6 TCP/UDP
checksums use the IPv6 pseudo-header; IPv6 fragment headers are parsed and
reassembled; outbound IPv6 datagrams are fragmented with a fragment header.
ICMPv4/v6 echo requests are answered locally so `ping` works.

### Stability & recovery

- **Serialized command executor** — start/stop/restart can never race (fixes
  the wedge where a quick STOP→START left the VPN off).
- **START_STICKY** — system restart after a kill re-checks persisted settings.
- **Network-change recovery** — a `ConnectivityManager` default-network
  callback debounces and re-establishes the tunnel on Wi-Fi↔cellular handover,
  airplane mode, etc., with a restart cool-down that prevents restart loops.
- **Live rule updates** — keyword/website/master-toggle changes are applied to
  a running tunnel instantly (rules are held in an `AtomicReference`), no
  restart needed.
- **Unexpected tunnel loss** (another VPN takes over, permission revoked) →
  engine notifies the service, which debounce-restarts or stops cleanly.
- **Boot / package-replaced** receiver restores the VPN (existing behaviour).
- Idle/connect/FIN timeouts, flow-table cleanup, bounded buffers and
  backpressure prevent leaks and unbounded memory growth.
- Stopping from the notification now persists `enabled=false` so the UI and
  boot receiver stay consistent (new `ACTION_STOP_PERSIST`).

## 3. Files changed

Added:
- `app/src/main/java/com/safeme/app/vpn/IpPacket.kt`
- `app/src/main/java/com/safeme/app/vpn/IpBuild.kt`
- `app/src/main/java/com/safeme/app/vpn/DnsMessage.kt`
- `app/src/main/java/com/safeme/app/vpn/BlockingRules.kt`
- `app/src/main/java/com/safeme/app/vpn/TunWriter.kt`
- `app/src/main/java/com/safeme/app/vpn/UdpRelay.kt`
- `app/src/main/java/com/safeme/app/vpn/TcpRelay.kt`
- `app/src/main/java/com/safeme/app/vpn/VpnEngine.kt`
- Tests: `InternetChecksumTest.kt`, `IpPacketTest.kt`, `DnsMessageTest.kt`,
  `BlockingRulesTest.kt`, `TcpSeqTest.kt`, `VpnRelayIntegrationTest.kt`

Rewritten:
- `app/src/main/java/com/safeme/app/service/SafeMeVpnService.kt`

Tweaked:
- `gradle.properties` — memory-friendly JVM settings (CI/sandbox safe).

## 4. Validation

- `./gradlew :app:compileDebugKotlin` — clean.
- `./gradlew :app:testDebugUnitTest` — unit tests + **end-to-end integration
  tests** that drive the real relays against actual local TCP/UDP sockets:
  UDP echo round-trip, DNS-over-UDP block & forward (upstream never sees
  blocked queries), full TCP request/response cycle with correct
  sequence/ack, TCP FIN close, TCP RST on unreachable destination, and
  DNS-over-TCP block & forward. All packet checksums, headers, and
  fragmentation verified.

## 5. Known limitations (inherent to non-root Android VPNs)

- DNS-over-HTTPS (443) and DNS-over-TLS (853) cannot be filtered without TLS
  interception; standard UDP/TCP DNS is fully covered.
- Loopback traffic (e.g. apps querying 127.0.0.1 DNS) bypasses the tunnel by
  design (kernel loopback routes are more specific than the VPN routes).
