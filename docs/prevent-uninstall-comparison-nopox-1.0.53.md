# Prevent-Uninstall Mechanism: NopoX 1.0.53 (Reference) vs SafeMe — Full Analysis

**Date:** 2026-08-17
**Reference APK:** `SafeMe/Reference/NopoX v1.0.53.apk` (package `com.planproductive.nopox`, versionCode 153)
**Target:** SafeMe prevent-uninstall (PU) implementation
**Measurement environment:** Android emulator, Pixel 7 profile, API 36 (Android 16), SafeMe debug build `artifacts/app-debug.apk` installed from current repo source

---

## Part 1 — NopoX 1.0.53 reverse engineering

### 1.1 Architecture overview

NopoX's uninstall prevention is a **layered, accessibility-service-driven scheme**:

1. **OS-level uninstall block** — a Device Admin that is activated with *zero declared policies*. The bare fact of being an active device admin makes the system refuse to uninstall the app directly (the user must deactivate the admin first), and marks the App Info page with "This app is a device admin". The `DeviceAdminReceiver` overrides no callbacks — it is purely a status marker.
2. **Surface protection** — an `AccessibilityService` (`MyAccessibilityService`) watches the foreground window. When the user reaches any page from which the protection could be removed — App Info, Device Admin deactivation, the app's own accessibility-service detail page — it **instantly covers the screen with a full-screen overlay** (`PornBlockPage`, `TYPE_APPLICATION_OVERLAY`). The user is bounced off the page; if they dismiss the overlay, they land back on the page and are re-covered.
3. **Self-preservation** — a per-service reconnection cooldown, an app add/remove package listener, and OEM-specific handling (Xiaomi auto-start text, Huawei ultra-power-saving text, multi-user text) that detect when the app's own auxiliary capabilities are being revoked or killed.

The overlay is the core response primitive. NopoX never launches an activity for protection — it overlays. This sidesteps Android 10+ background-activity-launch (BAL) restrictions entirely.

### 1.2 Components and permissions (manifest)

| Item | Value |
|---|---|
| Permissions | `SYSTEM_ALERT_WINDOW`, `PACKAGE_USAGE_STATS` (query), `QUERY_ALL_PACKAGES`, VPN (`BIND_VPN_SERVICE`), boot receiver, post-notification, battery-ignore, etc. |
| Service | `com.planproductive.nopox.features.blockerPage.service.MyAccessibilityService` |
| VPN service | `...blockerPage.service.MyVpnService` (DNS filtering; not part of PU) |
| Receiver | bare `DeviceAdminReceiver` (zero policies) |
| Other | `AppSystemActionReceiver` / `...AllTime` / `...AllTimeWithData` (package add/remove, boot, screen state), `AppDataCheckWorker` (WorkManager), `TransparentActivity` (Play-Store rating only — **not** protection) |

### 1.3 Accessibility service configuration

Decoded from the binary XML:

```
eventTypes      0x0080082b
                = TYPE_VIEW_CLICKED | TYPE_VIEW_LONG_CLICKED | TYPE_VIEW_FOCUSED
                  | TYPE_WINDOW_STATE_CHANGED | TYPE_WINDOW_CONTENT_CHANGED
                  | TYPE_VIEW_ACCESSIBILITY_FOCUSED | TYPE_VIEW_CONTEXT_CLICKED
notificationTimeout  100 ms
flags           0x5b  (REPORT_VIEW_IDS | RETRIEVE_INTERACTIVE_WINDOWS | ...)
canRetrieveWindowContent  true
```

Key point: **seven event types**, including `TYPE_WINDOW_CONTENT_CHANGED` — so in-page fragment navigation inside the Settings host activity produces events. SafeMe subscribes only to `TYPE_WINDOW_STATE_CHANGED` (see Part 2).

### 1.4 The detection core (`MyAccessibilityService.checkPreventUninstall`)

Reconstructed from smali (`smali/classes4/.../MyAccessibilityService.smali`, ~1,400 lines of the method):

- **Guard conditions** (all must hold):
  - `preventUninstall` flag is ON (set from the Room DB switch `SwitchIdentifier.PREVENT_UNINSTALL`, armed via `BlockerPageUtils.updateAccessibilityBlockingValues` on app start, service connect, and package-change broadcasts).
  - The foreground package is the **Settings app** (`getSettingAppPackageName()`) — plus OEM variants matched by text (Xiaomi auto-start, Huawei ultra-power-saving, multi-user).
- **Page identification** is text-driven. It compares the event text (and, on miss, the active-window node tree) against a set of match strings built per session:
  - `deviceAdminTextToMatch` — the "Device admin" / deactivation surface.
  - The app's own **app name** on App Info / force-stop / uninstall-confirmation pages.
  - The app's own **accessibility-service label/description** on the service detail page.
- **Decision → response:**
  - `showPuBlockPage(...)` → `PornBlockPage.blockWindow(...)` → full-screen `TYPE_APPLICATION_OVERLAY` (window type 2032), flags `FLAG_NOT_FOCUSABLE | FLAG_NOT_TOUCH_MODAL | FLAG_LAYOUT_IN_SCREEN`, formatted opaque.
  - A promotion variant (`showPuPromotionPage`) upsells premium over the same surface.
- **Dismissal:** the overlay's close button is gated by a **CountDownTimer** whose duration is the user-configurable "block screen countdown" (default UI shows "24 Hours"); the close is only enabled after the countdown elapses. `TYPE_APPLICATION_OVERLAY` with `FLAG_NOT_FOCUSABLE` takes no key events, so BACK is handled by the window underneath — the overlay keeps covering.

### 1.5 Execution flow (detection → protection)

```
User opens a protected page
   │
   ▼
Settings window appears → a11y event (WINDOW_STATE_CHANGED / CONTENT_CHANGED,
  VIEW_CLICKED / FOCUSED...) delivered within ~100 ms (notificationTimeout)
   │
   ▼
onAccessibilityEvent → checkPreventUninstall(event, pkg, cls)
   │  (also re-arms service config: updateAccessibilityVariables)
   ├─ pkg != Settings  → ignore
   ├─ page not about NopoX (no app name / no device-admin text) → ignore
   └─ match → showPuBlockPage → PornBlockPage.blockWindow
              → addView(TYPE_APPLICATION_OVERLAY, full-screen)  ← cover in place
   │
   ▼
User cannot reach Uninstall / Deactivate / a11y-toggle controls
   (overlay re-raised on every re-activation; close gated by countdown)
```

No polling, no watchdog, no artificial delay: the decision runs synchronously inside the event callback. The only background loop is the config re-arm (`updateAccessibilityVariables`, DB reads on Dispatchers.IO).

### 1.6 Timing characteristics (static analysis)

- **Event delivery:** bounded by `notificationTimeout = 100 ms`; typically ~10–80 ms after the window appears.
- **Decision:** synchronous, in-process; a few string comparisons plus at most one bounded node-tree walk.
- **Cover:** `WindowManager.addView` on an already-bound view — tens of ms, no activity launch, no BAL.
- **Expected end-to-end (page appears → overlay visible): ~100–300 ms.**
- Latency sources: the 100 ms event aggregation window, the IO-dispatcher DB re-arm (off the hot path), and view inflation on first use (amortized).

### 1.7 Live measurement attempt

**Not feasible in this environment.** `preventUninstall` is a **premium feature**: on a fresh install the toggle is absent from the main UI; the code routes it through `PremiumFeatureIdentifiers.PREVENT_UNINSTALL` and shows a premium-upsell page instead. The Room DB is not reachable (no root on the production-build emulator image), so the switch cannot be forced on. All NopoX latency figures above are therefore derived statically. A future pass should measure on a device with a premium (or cracked) install.

### 1.8 Weaknesses, race conditions, bypasses, edge cases

1. **Device admin is a decoy.** Zero policies + no `onDisableRequested` handling: the admin exists only for the OS-level uninstall refusal. It offers no self-defense on its own deactivation page — that page is protected only by the overlay.
2. **Text dependence.** Detection relies on the page *text* (event text or tree). On Android 15/16, accessibility events increasingly hide descriptions and some texts; a Settings page that renders SafeMe's name only in a non-exposed view can slip through. NopoX mitigates with tree probing but still depends on `canRetrieveWindowContent`.
3. **Overlay can be bypassed by disabling accessibility from elsewhere.** The overlay covers the a11y detail page, but the *list* page and the Settings search path ("Accessibility" search result) are only text-matched, not covered; the service can be toggled from a surface that doesn't render the app label in exposed text. NopoX's `initAppAddRemoveListener` and cooldowns react *after* the fact.
4. **No notification-shade coverage.** The overlay is `FLAG_NOT_FOCUSABLE` and `FLAG_NOT_TOUCH_MODAL`; the shade can be pulled over it (the overlay isn't a full-screen `TYPE_SYSTEM_ALERT` focus grab), and Settings shortcuts in the shade can re-navigate. The overlay re-covers only on a subsequent event.
5. **Re-cover loop races.** Dismiss → page re-activated → re-cover is event-driven; if the events are coalesced/starved (a fast double-tap on "Deactivate"), a single window-state-changed event may be missed, leaving the page exposed until the next event. No watchdog exists to re-probe.
6. **Premium gating.** PU is off for non-premium users — the mechanism is a sales feature, not a guarantee.

---

## Part 2 — SafeMe current implementation

### 2.1 Architecture

SafeMe's PU is a **service + activity** scheme:

1. **OS-level block** — `DeviceAdminUtils.SafeMeDeviceAdminReceiver` active as device admin (also zero policies). Same OS-level uninstall refusal as NopoX. Verified active on the emulator (`dumpsys device_policy`).
2. **Surface protection** — `SafeMeAccessibilityService` (events: **only `TYPE_WINDOW_STATE_CHANGED`**, 100 ms timeout, `flagReportViewIds`, `canRetrieveWindowContent`) detects protected pages and launches **`BlockGateActivity`** — a full-screen activity with a Compose `BlockOverlay` (dwell countdown + ready-gated Close), which on close **always returns to HOME**, never back to the protected page.
3. **Self-heal** — `A11yProtectionUtils.selfHealAllAsync` + `A11yProtectionGuard.ensureWatching` re-enable the a11y service and device admin if the user disables them.
4. **Watchdog** — a 2 s `puWatchdogTick` coroutine re-probes the active window for any PU surface, catching activations the event path misses (pages opened without a window-state change, e.g. the SubSettings-hosted a11y detail page), and is kicked immediately on gate dismissal.

Detection is **scoped to SafeMe's own identity**: every branch requires the app's name/label/description on the page (event text or bounded node-tree walk) or the exact a11y detail-page fingerprint (window title contains the service label; label appears ≥2 times in walked texts; or label+“shortcut” in one text). Everything is fail-open.

### 2.2 Measured latency (emulator, API 36, current repo source)

All timings from `ActivityTaskManager` / logcat timestamps, page launch → gate START → gate Displayed:

| Scenario | Page→gate START | Page→gate **Displayed** | Path |
|---|---|---|---|
| App Info page (`InstalledAppDetails`) | **165 ms** (samples 165–500 ms) | ~440–570 ms | event path |
| Device Admin deactivation (`DeviceAdminAdd`) | **304 ms** | **568 ms** | event path |
| A11y detail page (SubSettings-hosted) | 159–340 ms (phase-dependent) | ~530 ms | **watchdog** (no event fires) |
| Detection decision → gate START | +10 ms | — | both |
| Gate activity Displayed | +274–318 ms after START | — | both |

Notable details:
- The gate launches with `BAL_ALLOW_TOKEN` — the system's background-activity-launch allowance to accessibility services; on this emulator the exemption works.
- The a11y detail page fires **no window-state-changed event** (the page opens inside the same `SubSettings` window). The event path logs `not a target`; the 2 s-cadence watchdog is what gates it. Observed 159–340 ms responses reflect watchdog ticks that happened to land during the page transition; **worst case is ~2 s** (tick just missed → next tick).
- Gate dismissal (BACK) lands on the launcher and immediately kicks the watchdog, which re-arms the PU cooldown (`lastPuBlockAt = 0`) so a revealed protected page is re-gated at once.

### 2.3 Weaknesses / risks

1. **`TYPE_WINDOW_STATE_CHANGED` only.** Any in-page navigation that doesn't change the window (Settings sub-pages hosted in `SubSettings`, e.g. the a11y detail page) is invisible to the event path and depends entirely on the 2 s watchdog → up to ~2 s exposure, worst case.
2. **Activity launch is the response primitive.** (a) It depends on the a11y BAL exemption, which some OEMs break/limit — the gate then cannot start from the background and the page stays exposed. (b) It is slow to be *visible*: +274–318 ms for the activity to Display after START, vs. tens of ms for an overlay `addView`. (c) An activity transition is a heavyweight, user-visible flicker.
3. **No `SYSTEM_ALERT_WINDOW`.** SafeMe declares no overlay permission, so the NopoX-style instant cover is not available.
4. **Event-text dependence on modern Android.** Same as NopoX (mitigated by tree probing, window-title checks, and the watchdog).
5. **Watchdog cadence fixed at 2 s.** A single tick does a bounded tree walk; it is throttled by `PU_APP_NAME_PROBE_THROTTLE_MS = 5 s` for the framework `findAccessibilityNodeInfosByText` probe, but the walked-texts checks run every tick. 2 s is a compromise between coverage and battery; it is the dominant latency source on the a11y-detail path.

---

## Part 3 — Side-by-side comparison

| Dimension | NopoX 1.0.53 | SafeMe (current) |
|---|---|---|
| OS-level uninstall block | Device admin (active, zero policies) | Device admin (active, zero policies) — **equal** |
| Detection events | 7 types incl. `CONTENT_CHANGED`, `CLICKED`, `FOCUSED` | `WINDOW_STATE_CHANGED` only |
| In-page (fragment) navigation detection | Yes (event path) | **No** — covered only by 2 s watchdog |
| Extra detection backstop | None (no watchdog) | 2 s watchdog + kick-on-dismissal + self-heal |
| Response primitive | Full-screen `TYPE_APPLICATION_OVERLAY` (needs SAW) | Full-screen `BlockGateActivity` |
| BAL dependency | **None** (no activity launch) | Depends on a11y BAL exemption |
| Time-to-cover | ~100–300 ms (static estimate) | 165–570 ms to Display; worst ~2 s (watchdog) |
| Dismissal | Countdown-gated close (default 24 h) | Dwell countdown + ready-gated Close; BACK → HOME |
| A11y-detail handling | Covered by overlay; re-cover on next event | Gate → HOME bounce; watchdog eviction + toast |
| Self-heal / re-arm | Re-arm on connect & package changes (no explicit a11y self-heal found) | `selfHealAllAsync` + `A11yProtectionGuard` (stronger) |
| OEM surface handling | Extensive text-matched OEM pages (Xiaomi auto-start, Huawei ultra-PS, multi-user) | Settings-package allow-list incl. common OEM packages; no OEM-specific text logic |
| Fail mode | Overlay only re-raises on next event (no watchdog) | Fail-open + watchdog backstop (safer against false positives; slower worst case) |

**Verdict:**
- **NopoX is more robust on the *response* side** — instant overlay, zero BAL risk, and no event-type blind spot for in-page navigation (it subscribes to content-changed). Its detection is strictly event-driven, though: dismiss → re-activate races can leave the page exposed with no watchdog.
- **SafeMe is more robust on the *persistence* side** — self-healing of the a11y service/admin and a watchdog backstop that guarantees re-gating even when no event fires. Its weaknesses are the narrow event mask, the activity-launch response (BAL + display latency), and the 2 s watchdog worst case.

---

## Part 4 — Recommendations for SafeMe

**P1 — Add `TYPE_WINDOW_CONTENT_CHANGED` (and `TYPE_VIEW_CLICKED`/`FOCUSED`)** to `accessibility_service_config.xml` (SafeMe already tolerates higher event volume elsewhere, and the notification timeout stays 100 ms). This moves the SubSettings-hosted a11y detail page and other in-page navigations onto the **event path**, collapsing the worst-case latency from ~2 s to ~100–300 ms and making the watchdog a true backstop instead of the primary detector. Watch CPU: reuse the existing event-text + tree-walk helpers, and keep the throttles.

**P2 — Consider an overlay response for PU.** Declare `SYSTEM_ALERT_WINDOW` and, for the *PU* gate only, raise a NopoX-style `TYPE_APPLICATION_OVERLAY` (full-screen, `FLAG_LAYOUT_IN_SCREEN`, touch-modal but not focusable) instead of launching `BlockGateActivity`:
- Removes the BAL dependency entirely (OEM-proof).
- Cuts time-to-visible by ~250 ms (tens of ms vs +274–318 ms activity Display).
- Keep the dwell countdown + HOME bounce semantics; keep `BlockGateActivity` as the fallback when overlay permission is not granted (or for non-PU gates).
Trade-off: overlays can be covered by the notification shade (same as NopoX); add a shade-awareness re-check on the watchdog tick.

**P3 — Make the watchdog adaptive.** Instead of a fixed 2 s cadence, probe immediately after a `WINDOW_CONTENT_CHANGED`/`WINDOW_STATE_CHANGED` event on a PU-surface package (P2's event path makes the 2 s tick rare anyway), and keep the kick-on-gate-dismissal. Optionally lower `PU_WATCHDOG_INTERVAL_MS` to 1 s now that the walked-texts checks are bounded and throttled.

**P4 — Hardening the gate.** (a) Guard the a11y *list* page too: it currently renders SafeMe's label once, which the fingerprint deliberately ignores — but the list page is the toggle surface; consider a light self-heal re-arm on list-page visibility instead of blocking it (blocking the list page is what gets a11y services auto-disabled). (b) Add the notification-shade surface: when the shade is open over the gate and closed again, re-probe the revealed window (watchdog tick already covers this on return).

**P5 — Keep, but document, the fail-open rule.** Every branch is fail-open by design (false positives on legitimate Settings pages are worse than false negatives). With the event mask widened (P1), re-verify that the a11y LIST page never matches the detail fingerprint — the current 3-layer fingerprint (title, ≥2 label occurrences, label+“shortcut”) is sound; the added event types must not erode it.

**P6 — Measurement follow-up.** (a) Re-run the latency series after P1/P2 on the emulator (expect: a11y-detail path 150–350 ms end-to-end, event-path unchanged, overlay path faster to visible). (b) Test on a real device and one API 32/33 device (a11y event text exposure differs; BAL exemption behavior differs). (c) For NopoX: obtain a premium-enabled install to measure its actual end-to-end latency and confirm the static estimate.

---

## Appendix A — Key artifacts

- Decompiled sources: `SafeMe/Reference/jadx-out/sources/com/planproductive/nopox/...`
- Raw smali: `SafeMe/Reference/smali/classes4/com/planproductive/nopox/features/blockerPage/service/MyAccessibilityService.smali` (+ `$Companion.smali`), `.../utils/PornBlockPage*.smali`
- Manifest dump: `SafeMe/Reference/manifest_dump.xml`
- Decoded a11y XML: `SafeMe/Reference/apk-extracted/res/xml/accessibility_service_config.xml`
- SafeMe source: `SafeMe/app/src/main/java/com/safeme/app/service/SafeMeAccessibilityService.kt`, `.../protect/{A11yProtectionUtils,A11yProtectionGuard,DeviceAdminUtils,ProtectedSystemScreens,PreventUninstallBlockers}.kt`, `SafeMe/app/src/main/java/com/safeme/app/BlockGateActivity.kt`, `SafeMe/app/src/main/res/xml/accessibility_service_config.xml`
- Live latency data: logcat `ActivityTaskManager` START / Displayed lines, tag `SafeMeA11y`

## Appendix B — Measurement method

For each scenario: `logcat -c`, trigger the surface (`am start` for App Info / DeviceAdminAdd; uiautomator-guided tap for the a11y detail row), then diff `ActivityTaskManager` timestamps of the Settings page START vs the `BlockGateActivity` START and its `Displayed` line. Watchdog-path attribution: gate fired with no preceding `PU: blocking page` log (watchdog calls `launchPuGate` silently). NopoX live timing was blocked by the premium paywall (see §1.7).
