# 08 — Backup & Restore

SafeMe exports its entire configuration to a human-readable, human-editable
JSONC file and restores it atomically with validation and rollback.

## 1. Files & responsibilities

| File | Responsibility |
|---|---|
| `data/BackupCodec.kt` | Pure JSONC codec — the single source of truth for the file format. `toJsonc` writes it, `fromJsonc` parses + validates it. Android-free, unit-tested. |
| `data/BackupManager.kt` | `createBackup()` (snapshot all stores → JSONC + suggested filename) and `executeRestore()` (validate → snapshot → write with rollback). Defines the `BackupStateStore` interface + production `backupStores()` wiring. |
| `data/Jsonc.kt` | Minimal JSONC→strict-JSON converter: strips line/block comments and trailing commas **outside string literals** (so a keyword containing `//` survives a round trip). |
| `ui/screens/backup/BackupScreen.kt` | SAF export/import UI, confirm dialog, error toasts. |

## 2. File format

```
// SafeMe backup — JSONC
// Created <ISO-8601> · app <versionName> · schema v1
{
  "format": "safeme-backup",
  "schemaVersion": 1,
  "appVersion": "0.1.0",
  "createdAt": "…",
  "blocking": { "blocklistKeywords": [{"v": "…", "c": "ADULT"}], … },
  "schedules": { "schedules": […], "a11yWarningDismissed": … },
  "vpn": { "enabled": …, "preset": "CLOUDFLARE_FAMILY", "customV4": …,
           "customV6": …, "whitelist": […], "notifMode": …, "notifCustom": … },
  "quickActions": ["keyword", "vpn", …],
  "appLock": { "lockType": …, "storedHash": …, "credentialLength": …,
               "biometricEnabled": …, "forgotPasswordDisabled": …,
               "autoLock": … },
  "preventUninstall": { "enabled": … },
  "a11yProtection": { "enabled": …, "protectedComponents": […] },
  "blockScreen": { "dwell": …, "message": …, "img": …, "whyOn": … }
}
```

- **8 sections**, each mapping 1:1 to a DataStore domain and restored
  atomically as a unit.
- Sections are **optional**: a section absent from the file is `null` and is
  *never touched* on restore (backups from older schema versions or
  hand-edited files restore exactly what they contain). A file with zero
  present sections is rejected as `EMPTY`.
- Section keys: `blocking`, `schedules`, `vpn`, `quickActions`, `appLock`,
  `preventUninstall`, `a11yProtection`, `blockScreen`.
- Enum fields are stored as names (`preset`, `lockType.storage`, …) and parse
  through resilient lookups with defaults (unknown values fall back, never
  crash).
- `BACKUP_SCHEMA_VERSION = 1`; `format` marker `safeme-backup`.

## 3. Export

`Context.createBackup()`:

1. Reads every store via `xxxPrefs().first()` (blocking, schedules, vpn,
   quick actions, app lock, prevent uninstall, a11y protection, block
   screen).
2. Reads the real `versionName` from `PackageManager`.
3. `BackupCodec.toJsonc(...)` with a header comment recording created-at,
   app version, and schema version.
4. Suggests the filename `SafeMe-backup-<yyyyMMdd-HHmm>.jsonc`.

UI: `CreateDocument("application/octet-stream")` so DocumentsUI keeps the
`.jsonc` name (an `application/json` MIME used to append `.json`, producing
`….jsonc.json`). Save runs on `Dispatchers.IO`; success/failure toasts.

## 4. Import & validation

`BackupCodec.fromJsonc(raw)` returns a typed `BackupParseResult` and **never
touches app state**:

| Condition | Result |
|---|---|
| Not valid JSON/JSONC (or unclosed comment) | `NOT_JSON` → "This file isn't valid JSONC" |
| Valid JSON but wrong / missing `format` | `NOT_SAFEME` → "This isn't a SafeMe backup file" |
| `schemaVersion` missing / wrong type | `INVALID_STRUCTURE` → "missing required data" |
| `schemaVersion > BACKUP_SCHEMA_VERSION` | `UNSUPPORTED_VERSION` → "made by a newer version" |
| Section present but wrong type (e.g. `"blocking": "x"`) | `INVALID_STRUCTURE` |
| Field inside a section wrong type | `INVALID_STRUCTURE` |
| Malformed collection entry (see below) | `INVALID_STRUCTURE` |
| No section carries data | `EMPTY` |
| Store write fails mid-restore (rolled back) | `WRITE_FAILED` |
| Write fails and rollback fails | `ROLLBACK_FAILED` |

### Strict collection parsing

The blocking-section parsers (`keywordsFromJson`, `websitesFromJson`,
`stringsFromJson`, `titleRulesFromJson`) run in **strict mode** during
restore (`strict = true`): a non-object entry, missing/empty value, or
wrong-type entry throws, which `fromJsonc` maps to `INVALID_STRUCTURE`. A
hand-edited backup such as `"blocklistKeywords": ["word"]` is rejected with
"missing required data" instead of silently restoring zero keywords.

Runtime DataStore reads keep using the same parsers in **lenient mode**, so a
corrupt store degrades to defaults and can never crash the accessibility
service.

### Restore (`executeRestore`)

Guarantees (documented in `BackupManager.kt`):

1. The whole file is parsed and validated **before** any store is touched.
2. Current values of every affected section are snapshotted first.
3. Sections are applied one store at a time; if any write fails, all
   already-written sections are rolled back to their snapshotted values.
4. Sections absent from the backup are never touched.

`backupStores()` wires the 7 `BackupStateStore` implementations (one per
DataStore); tests substitute in-memory fakes to exercise success, mid-write
failure, and rollback.

## 5. UX flow

1. **Restore** → `OpenDocument("*/*")` → read text (IO) → `prepare()`
   (parse+validate, no writes).
2. Valid file → `RestoreConfirmDialog` showing created-at / app version /
   schema version ("This will replace your current settings…").
3. **Restore** → `executeRestore` → success toast ("Backup restored") or a
   typed error toast.
4. The in-app toast channel is `ToastHost`; failures never leave partial
   state (rollback) and never crash.

## 6. Notes & behavior verified on-device

- **VPN does not auto-start on restore** — restoring `vpn.enabled: true`
  updates the setting but does not launch the tunnel; the app's
  consent-required design means the user starts it from the VPN screen.
- **`blockedToday` is transient** — `writeBlockingPrefs` intentionally does
  not restore the daily counter.
- Older schema versions (e.g. `schemaVersion: 0`) are accepted; the
  `UNSUPPORTED_VERSION` path only guards *newer* files.
