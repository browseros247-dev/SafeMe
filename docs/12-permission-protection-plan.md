# 12 — WRITE_SECURE_SETTINGS protection plan

> **Status: PLAN — not implemented.** This document describes a proposed
> hardening of the accessibility self-heal's `WRITE_SECURE_SETTINGS` grant,
> so that revoking it effectively requires a password. It is derived from the
> current implementation described in
> [04 — Security architecture](04-security-architecture.md); nothing here is
> built yet.
>
> *Audited against AOSP master and the official Android 14 behavior-change
> pages: the re-grant-dialog claim (§2) and the Android 14 `Settings.Global`
> write-restriction claim (§4.4) were corrected, and every DPM API level in
> §4.2 was verified against the public reference.*

## 1. Goal and the honest answer

**Goal.** The self-heal rewrites
`Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES` / `ACCESSIBILITY_ENABLED`,
which requires `WRITE_SECURE_SETTINGS`, granted once over ADB:

```bash
adb shell pm grant com.safeme.app android.permission.WRITE_SECURE_SETTINGS
```

We want: *if anyone tries to revoke that permission, they must know a
password.*

**Direct answer: a literal password prompt at the framework level is not
possible for a third-party app.** Permission grant/revoke state is owned by
the system (`PermissionManagerService`); there is no API, broadcast, or hook
that lets a normal app intercept `adb shell pm revoke` or wrap it in an
authentication step. A literal gate requires a custom ROM or an
Xposed/Magisk (root) module — which root itself defeats, because whoever
holds root can remove SafeMe or read its storage regardless of any prompt
(see §6).

**What is achievable instead:** the same security outcome — *a user who does
not know the password effectively cannot revoke the permission* — by
eliminating every revocation vector and gating every in-app surface. That is
the plan in §3–§5.

## 2. Threat model: who can revoke today

| Vector | Mechanism | Feasible for a normal user? | Countermeasure |
|---|---|---|---|
| Settings → Apps → SafeMe | Runtime-permission toggle | **No** — `WRITE_SECURE_SETTINGS` is `signature\|privileged\|development`, not a dangerous (runtime) permission, so it has no toggle in the app-permission UI and no "special access" screen on stock Android | none needed (already blocked) |
| ADB `pm revoke` / `pm clear` / `appops set` | needs a connected, **authorized** computer | Yes, if USB debugging is on and a key is authorized | close the vector: §4 lockdown |
| Root shell / Magisk | anything | only on a rooted device | not solvable by any app (§6) |
| Factory reset | wipes app + grant | Yes (physical access) | not solvable; device owner must be re-provisioned (§7) |
| OEM ROMs (MIUI, ColorOS, …) | custom permission managers | sometimes; some ROMs also block `pm grant` unless "Allow granting permissions via USB" is on | test per ROM; treat OEM quirks as a known limitation (§8) |

Two facts carry most of the design:

1. **ADB authorization is already password-gated.** Connecting a new
   computer shows the on-device "Allow USB debugging?" dialog, which requires
   an unlocked screen. The device's screen-lock password *is* the gate for
   the only realistic non-root vector — provided the lock can't be removed.
2. **`WRITE_SECURE_SETTINGS` is a *development* permission** — grantable via
   `grantRuntimePermission`, which is exactly what `adb shell pm grant`
   invokes. Re-granting after a revocation is **not reliably possible from
   the app itself**: the standard runtime-permission dialog is the mechanism
   for dangerous (runtime) permissions, and the permission controller does
   not consistently surface a dialog for development permissions — the
   automation community's universal ADB/Shizuku requirement for this
   permission is the practical evidence. Treat dialog re-grant as
   best-effort and device-dependent; the dependable response to a revocation
   is detection + notification + (optionally) lock-on-tamper (§3).

## 3. Phase 1 — watchdog + in-app password gating (no new privileges)

Ships in a normal release; works regardless of Device Owner status.

### 3.1 Module-level change list

**`protect/`**

- **New `WriteSecureSettingsWatcher.kt`** — runs only while the a11y
  protection toggle is on (same gating as `A11yProtectionGuard`):
  - 30 s poll of
    `Context.checkSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS)`
    (API 23+). There is no revocation broadcast, so polling is required.
  - Also reads `Settings.Global.ADB_ENABLED` and
    `Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES` as tamper signals.
  - On a `GRANTED → DENIED` transition: append an activity-log entry
    (`ActivityLog.kt`, new `permission` type next to `block`/`schedule`/`vpn`/
    `a11y`), post a high-priority `NotificationManagerCompat` notification
    (reuse the existing hourly throttle), and surface a re-grant request
    (below).
  - Feeds `A11yProtectionStateHolder.writeSecureGranted` (new `@Volatile`
    field) so hot paths read it synchronously, matching the existing state
    holder pattern.
- **New `ReGrantCoordinator.kt`** — exposes a
  `MutableStateFlow<ReGrantRequest>` that a foreground activity collects; on
  request it launches `ActivityResultContracts.RequestPermission` with
  `WRITE_SECURE_SETTINGS`. **Best-effort**: the runtime dialog is not
  reliably available for development permissions (§2), so this is a
  convenience, never the primary defense. Throttle attempts (e.g. max 1 per
  10 min) so a denied dialog is not spammed. Policy is configurable: `off`
  (detect + notify only) / `attempt` (show the dialog).
- **`A11yProtectionGuard.kt` / `A11yProtectionUtils.kt`** — treat
  `checkSelfPermission != GRANTED` as a write-failure cause alongside the
  existing `SecurityException` path in `selfHealAll`; wire the watcher into
  the same failure notification so there is one coherent failure UX.
- **`AppLockManager.kt` / `AppLockStateHolder.kt`** — unchanged; reused as
  the gate for the new UI (§3.2).

**`data/`**

- **`a11y_protection_prefs`** — new keys: `writeSecureGranted` (cached check
  result), `revocationCount`, `lastRevocationAt`, `reGrantPolicy`.
- **`ActivityLog.kt`** — extend the entry-type union with `permission`;
  the existing dedupe-and-cap helper applies unchanged.
- **`ProtectionLayers.kt`** — (optional) count the `a11yProtection` layer as
  active only when the write path is live: protection on **and**
  `writeSecureGranted`.

**`ui/`**

- **Anti-Tamper → Accessibility protection** — new "Write-permission status"
  card: granted/revoked, last revocation time, and a re-grant button.
  *Read and action are both gated behind the App Lock* (the
  `AppLockGateHost` / biometric unlock flow already in place).
- Every existing "disable protection" / anti-tamper affordance that could
  surface the permission state is wrapped in the same App Lock gate.

**`SafeMeApp.kt`** — start/stop the watcher beside the existing
a11y-protection collector in the startup sequence (see
[01 — Architecture](01-architecture.md) §4).

### 3.2 APIs

| Purpose | API |
|---|---|
| read grant state | `Context.checkSelfPermission(String)` |
| re-grant (best-effort) | `ActivityResultContracts.RequestPermission` / `Activity.requestPermissions` — the runtime dialog is not reliably surfaced for development permissions; ADB (`pm grant`) or a Shizuku-style helper stays the authoritative path |
| tamper signals | `Settings.Global.getString(ADB_ENABLED)`, `Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES` |
| gate UI | existing `AppLockManager.verify` (PBKDF2, constant-time, rate-limited) |

## 4. Phase 2 — Device Owner lockdown: remove the vectors

SafeMe already registers a Device Admin
(`DeviceAdminUtils$SafeMeDeviceAdminReceiver`, currently minimal
`<uses-policies/>`). This phase promotes it to **Device Owner** so SafeMe can
close every non-root revocation vector.

### 4.1 Manifest & provisioning

- **`AndroidManifest.xml`** — receiver stays as-is (`BIND_DEVICE_ADMIN`);
  `res/xml/device_admin.xml` gains the policies the DPM calls need
  (at minimum `force-lock` for `lockNow()`).
- **Provisioning** (parent's ADB, documented in-app):

  ```bash
  adb shell dpm set-device-owner \
    com.safeme.app/com.safeme.app.protect.DeviceAdminUtils\$SafeMeDeviceAdminReceiver
  ```

  Requirements/constraints:
  - the device must have **no accounts** (remove them first), and
    provisioning fails on already-provisioned/OEM-restricted devices —
    treat "freshly reset device" as the supported path;
  - once set, it is effectively **one-way** until
    `dpm remove-active-admin` (needs ADB) or factory reset;
  - SafeMe can then only be removed by the parent — `setUninstallBlocked`
    hides it from the UI;
  - Play-Store updates keep working as long as the update keeps the admin
    receiver declared — a manifest change that drops it silently ends
    ownership, so a build guard/test must assert the receiver exists.

### 4.2 New `protect/DeviceOwnerManager.kt`

Thin, try/catch-wrapped DPM wrapper (mirroring the defensive style of
`DeviceAdminUtils`), unit-testable policy decisions kept pure:

- `isDeviceOwner()` → `dpm.isDeviceOwnerApp(context.packageName)` (API 21).
- `applyLockdown()` — idempotent, App-Lock-gated entry:
  - `setGlobalSetting(admin, Settings.Global.ADB_ENABLED, "0")` (method
    API 21, device owner; `ADB_ENABLED` is not on the small
    profile-owner-allowed key set);
  - `setGlobalSetting(admin, Settings.Global.ADB_WIFI_ENABLED, "0")`
    (`ADB_WIFI_ENABLED` constant API 30 / Android 11+);
  - `setUsbDataSignalingEnabled(admin, false)` (API 30, device owner only) —
    blocks USB data outright;
  - `addUserRestriction(admin, UserManager.DISALLOW_DEBUGGING_FEATURES)`
    (method API 21; constant API 28; device or profile owner) — Developer
    options disappear. Verified in AOSP master: the SettingsProvider itself
    re-enforces this by forcing `ADB_ENABLED`/`ADB_WIFI_ENABLED` to the
    restricted value on every restriction change, so the lockdown holds even
    if an OEM ignores the UI hiding;
  - `addUserRestriction(admin, UserManager.DISALLOW_SAFE_BOOT)` (method
    API 21; constant API 26; device owner) — closes the safe-mode bypass;
  - `setUninstallBlocked(admin, packageName, true)` (API 21, device or
    profile owner);
  - `setPasswordQuality(admin, PASSWORD_QUALITY_COMPLEX)` (method API 8;
    constant API 11) + `setMaximumTimeToLock(admin, 15 * 60_000L)` (API 8) —
    guarantees a strong screen lock exists; that lock is the password gate
    for any future ADB authorization (§2);
  - (optional hardening) `setPermittedAccessibilityServices(admin, [own
    component])` (API 21, device or profile owner) so the user cannot swap
    in a different a11y service.
- `reassertLockdown()` — poll the readable state
  (`Settings.Global.ADB_ENABLED`, restriction state) and re-apply; some OEM
  ROMs let the user flip settings back.
- `temporarilyAllowUsbDebugging(windowMs)` — inverse of the ADB settings,
  App-Lock gated, auto-reverts after the window or on next boot (parent
  recovery path, §4.3).
- `lockOnTamper()` — policy decision: on a detected revocation/ADB re-enable,
  call `lockNow()` (API 8, requires the `force-lock` policy) to force
  re-authentication
  of the screen lock. Requires a recovery path so it can't be weaponized
  (App Lock / parent flow), and must not fire inside an a11y-management
  window (the a11y kill vector, [04](04-security-architecture.md) §3).

**`data/`** — `safeme_pu_prefs` (or a new `do_prefs` domain): lockdown
toggle, temporary-ADB window, tamper log.

**`ui/`** — new "System protection" section on Anti-Tamper: device-owner
status, lockdown on/off (App Lock gated), provisioning instructions, and the
temporary-USB-debugging button. Everything is opt-in: the section only
appears when `isDeviceOwner()` is true.

**`SafeMeApp.kt`** — collector for the lockdown toggle →
`DeviceOwnerManager.applyLockdown`; re-assert on boot/update (extend
`A11yBootReceiver` or a new `LockdownBootReceiver`).

### 4.3 Recovery flows

| Situation | Path |
|---|---|
| Parent lost ADB after lockdown | in-app "temporarily enable USB debugging" (App Lock gated) → `setGlobalSetting(ADB_ENABLED, "1")`; auto-revert after N minutes / boot |
| Need to remove Device Owner | temporary-ADB → `adb shell dpm remove-active-admin ...` → uninstall |
| Factory reset | device owner is cleared; re-provision on the fresh device |
| Update broke ownership | never: build guard asserts the admin receiver is declared |

### 4.4 Caveats

- **Writes to `Settings.Global` (e.g. `ADB_ENABLED`) have always required
  `WRITE_SECURE_SETTINGS`** — the same ADB-granted permission the self-heal
  uses; no Android 14 behavior change alters this (the official
  behavior-change pages list nothing about settings writes). The real
  modern change is on the **read** side: since ~Android 12, non-public
  `@hide` `Settings.Global`/`Settings.Secure` keys are restricted, and
  Android 14 hardens this for apps targeting API 34+ via public-settings
  allowlists + compat gating (`enforceSettingReadable` in AOSP master).
  Holders of `WRITE_SECURE_SETTINGS` are exempt, and `ADB_ENABLED` is a
  public key, so SafeMe's tamper-signal reads and self-heal writes are
  unaffected. The lockdown still goes through
  `DevicePolicyManager.setGlobalSetting` because that is the sanctioned
  device-owner path and does not consume the grant.
- Device Owner does **not** grant `WRITE_SECURE_SETTINGS` — the ADB grant is
  still needed for the self-heal writes; the lockdown's job is to make that
  grant un-revocable by a non-root user, not to replace it.
- OEMs vary: some ignore user restrictions; Xiaomi additionally requires
  "Allow granting permissions via USB" for `pm grant` itself.

## 5. Phase 3 — (optional) literal password gate

Only if a *literal* prompt is required: a custom ROM or an Xposed/Magisk
module that hooks `PermissionManagerService.revokeRuntimePermission` /
`grantRuntimePermission` (or the `pm` shell path) to require a secret before
touching `WRITE_SECURE_SETTINGS`. See the comparison in §6 — this is only
defensible for a single personally-owned rooted device.

## 6. Comparison: Device Owner lockdown vs custom ROM / Xposed

| Dimension | Device Owner lockdown (§4) | Custom ROM / Xposed (root) |
|---|---|---|
| What it adds | removes revocation vectors (ADB off, dev options hidden, USB data off, uninstall blocked, safe boot blocked); App Lock gates every in-app surface; watchdog detects + alarms + re-grants | literal password dialog in the framework before `revokeRuntimePermission` / a11y disable |
| Effort | manifest policies + one new `DeviceOwnerManager` module + provisioning flow + UI; reuses the existing Device Admin receiver; no system fork | maintain a ROM fork or a Magisk/Xposed module; hook framework internals; module must track Android releases as framework code churns |
| Maintenance | low — public, stable DPM APIs; watch OEM quirks | high — broken by every Android major and by ROM updates; must be reflashed/reinstalled; users of rooted devices often skip OTA updates |
| Defeat-ability by root | defeated by root (root removes the app, its data, or the policies) | **self-defeating**: the module itself runs under root; a root attacker uninstalls the module or patches the check — and a rooted device lets ADB run as root, bypassing everything |
| Non-root attacker | effectively blocked | no added value — a non-root user already has no UI path to revoke `WRITE_SECURE_SETTINGS` (§2) |
| Deployment | single app update, Play-Store compatible | unlock bootloader (voids warranty, wipes device), custom recovery, sideloading; not distributable |
| Risk | none to user data | root/unlock wipes the device; OTAs break the module |

**Verdict.** Device Owner lockdown delivers ~all of the security goal for a
fraction of the effort and is the only approach that can ship to real users.
The Xposed/ROM route adds a literal prompt but is defeated by the same root
that enables it, so its marginal security value is near zero for this threat
model: the attacker we care about (a user without the password) has no root,
and a root-holding attacker cannot be stopped by any app-level design.
Phase 3 is only worth it for a hobby rooted device where the *appearance* of
a prompt matters more than the security it adds.

## 7. Rollout & rollback

1. **Phase 1** ships first in a normal release — Play-safe, zero privileges,
   pure detection/gating. Rollback is a normal app update.
2. **Phase 2** is strictly opt-in: the lockdown section only appears once the
   parent has provisioned Device Owner over ADB. Never auto-provision (it
   cannot be done from the app anyway).
3. **Rollback of Phase 2** = temporary-ADB → `dpm remove-active-admin` →
   uninstall/reinstall. A factory reset also clears it (and everything else).

## 8. Test checklist

1. ADB-grant the permission → confirm no toggle exists in
   Settings → Apps → SafeMe.
2. `pm revoke` → watcher fires the notification and re-grant attempt within
   one poll cycle; activity feed gains a `permission` entry.
3. Re-grant (best-effort, where the device surfaces the dialog): deny →
   throttled, no dialog spam; allow → self-heal writes work again. On
   devices with no dialog, confirm detection + notification still fire and
   document ADB as the authoritative re-grant path.
4. Device Owner: verify ADB off, Developer options hidden, USB data blocked,
   uninstall blocked, safe mode blocked; `reassertLockdown` re-applies after
   a manual flip (on ROMs that allow it).
5. App Lock gating: every self-heal / system-protection surface requires the
   password; brute-force backoff still holds.
6. Boot/update cycle: lockdown + self-heal re-assert after reboot and after
   a package update; build guard passes (admin receiver still declared).
7. `lockOnTamper` (if enabled): fires only outside a11y-management windows
   and always has a working parent recovery path.
8. OEM matrix: at minimum stock AOSP (Pixel/emulator) and one Xiaomi and one
   Samsung device, for provisioning, `pm grant`, and restriction behavior.

## 9. Limitations (honest)

- **Root defeats everything** — no app-level design prevents a root holder
  from removing SafeMe, reading its App Lock hash, or resetting its state.
- **Factory reset defeats everything** — it clears the app, the grant, and
  Device Owner; re-provisioning is required.
- **Device Owner is one-way-ish** — removal requires ADB or factory reset;
  this is a feature (parent-only) but must be surfaced in the provisioning
  UI before the parent commits.
- **Re-grant is best-effort, not a gate** — the runtime dialog is not
  reliably available for development permissions, and where it appears it
  is deniable; treat detection + notification + lock-on-tamper as the real
  deterrents, with ADB as the authoritative re-grant path.
- **OEMs vary** — restrictions can be ignored and `pm grant` can be blocked;
  test on target hardware (§8).
