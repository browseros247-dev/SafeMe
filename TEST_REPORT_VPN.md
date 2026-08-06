# SafeMe VPN Systematic Test Report

**Date:** 2026-08-05
**Emulator:** emulator-5554 (sdk_gphone64_x86_64, API 36 / Android 16)
**APK:** app/build/outputs/apk/debug/app-debug.apk v0.1.0 (com.safeme.app)
**Install:** streamed `adb install -r` at 22:15 (replaced previously-installed build)

## Baseline observations (before test execution)

| # | Observation | Severity |
|---|-------------|----------|
| B-0 | At 22:15:58 logcat shows tun0 established by boot receiver (MY_PACKAGE_REPLACED) with SafeMe addresses (10.0.0.0, fd00:10::2:0:0:2), then kernel logs `Failed to get if index, skip removeLocalNetAccess ...(tun0)` — tunnel came up then vanished while `VpnStatusStore.active` stayed true. UI stuck showing "Active" with no tun interface. | Investigate |
| B-1 | `VpnBootReceiver` calls `startForegroundService()` from a broadcast receiver; service calls `startForeground()` only AFTER synchronous `establishTunnel()`. A pre-existing crash at 19:47 (`ForegroundServiceDidNotStartInTimeException` in VpnBootReceiver.kt:29) confirms this can exceed the 5s foreground-start window on slow starts. | FAIL (crash risk) |
| B-2 | Initial persisted prefs (pre-install): vpn_enabled=true, preset=CLOUDFLARE_FAMILY, notif_mode=Default, notif_custom="M" | info |
| B-3 | **Stale-active state reproduced**: after install → force-stop → fresh `am start`, the service (pid 8711) is foreground with notification, persisted `enabled=true`, UI shows "Active / VPN filtering is on", but the kernel has NO tun interface, NO VPN NetworkAgent, NO 10.0.0.2 address. In-process `VpnStatusStore.active=true` while no tunnel exists. The toggle-sync fix makes the *toggle* track the live store, but the store itself was set true by the service on `establish()` and never cleared when the tunnel was torn down without `onRevoke` (process killed mid-tunnel). UI and reality disagree. | FAIL (stale state) |

---

## Test results

(append as tests execute)
