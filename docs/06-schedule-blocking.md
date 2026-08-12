# 06 — Schedule-based blocking

Schedule-based app blocking is coordinated by `protect/ScheduleEngine` — the
**only** component that turns schedule rules into enforcement.

## 1. Data model (`data/SchedulePrefs.kt`)

```kotlin
data class ScheduleBlock(
    val id: String,               // UUID
    val name: String,
    val days: List<Int>,          // 0=Mon … 6=Sun (prototype order)
    val startMinute: Int,         // minutes from midnight, 0..1439
    val endMinute: Int,           // > startMinute, same day only (no wraparound)
    val mode: ScheduleMode,       // INTERNET | LAUNCH | BOTH
    val appPackages: List<String>,// empty ⇒ blocks every app (blocksAllApps)
    val enabled: Boolean = true,
)
```

- **Day convention:** schedules store `0=Mon … 6=Sun` (prototype order);
  `ScheduleEvaluator` converts to `Calendar.DAY_OF_WEEK` internally.
- **Empty `appPackages`** means "no apps picked → blocks everything", per the
  prototype's "No apps — schedule blocks everything" editor copy.
- Persisted as a JSON array in `schedule_prefs` (`schedules_json`); plus an
  `a11y_warn_dismissed` flag for the "Accessibility Service required"
  banner (`shouldShowA11yWarning`).

## 2. Pure decision core (`protect/ScheduleEvaluator.kt`)

Android-free, unit-tested:

- `isActiveAt(rule, dayIndex, minuteOfDay)` — enabled, matching day, valid
  window, `minuteOfDay in start until end`.
- `evaluate(rules, now)` — **union semantics**: if any active rule targets a
  package, it is blocked. Produces `ActiveRules` with four fields:
  `internetBlockedPackages`, `internetBlockAll`, `launchBlockedPackages`,
  `launchBlockAll`.
- `nextBoundary(rules, now)` — earliest future start **or** end of any
  enabled rule (day offsets 0..7 cover every day-of-week combination
  exactly once), or `Long.MAX_VALUE` when no boundary exists.

## 3. Coordinator (`protect/ScheduleEngine.kt`)

Process-wide singleton, idempotent, safe from any thread. `apply(context,
schedules, now)`:

1. Evaluate the active rules.
2. **Launch blocking** → if the set changed, push to
   `SafeMeAccessibilityService.onScheduleSetsChanged()`, which re-checks the
   current foreground window so a block starting while an app is already open
   takes effect immediately.
3. **Internet blocking** → if the set changed, call
   `SafeMeVpnService.applyScheduledBlocks(...)`, which restarts the tunnel in
   the matching mode (per-app block / block-all) or starts it when consent
   was previously granted. See [07](07-vpn-dns-filtering.md).
4. **Re-arm the next boundary alarm** via `ScheduleAlarmReceiver`.
5. **Activity feed** — log "Launch blocking active" / "Internet blocking
   active" only when a block actually turns on (deduped by the store).

`reevaluate(context)` re-reads schedules from DataStore and applies — called
by `SafeMeApp` on every persisted change, by the alarm receiver at
boundaries, and by the safety ticker.

## 4. Alarm & boot (`protect/ScheduleAlarmReceiver.kt`)

- One exact `RTC_WAKEUP` alarm is scheduled for the next boundary
  (`setExactAndAllowWhileIdle`); on API 31+ without the exact-alarm
  permission it degrades to inexact (`set`).
- When it fires (or on `BOOT_COMPLETED` / `MY_PACKAGE_REPLACED`) the receiver
  runs `ScheduleEngine.reevaluate` in `goAsync()` and re-arms the next alarm.
- `Long.MAX_VALUE` cancels the alarm (no enabled schedules).

## 5. Safety ticker (`SafeMeApp`)

A 60 s ticker bounds drift from inexact alarms / missed doze wakeups. It is
**gated**: it only calls `reevaluate` while at least one schedule is enabled
(`hasEnabledSchedules`), so an idle device with no schedules does zero
periodic work.

## 6. Enforcement surfaces

| Mode | Launch blocking (a11y) | Internet blocking (VPN) |
|---|---|---|
| `LAUNCH` | gates app on open | — |
| `INTERNET` | — | per-app tunnel block |
| `BOTH` | gates app on open | per-app tunnel block |
| any mode, empty `appPackages` | gates **all** apps except `SCHEDULE_SYSTEM_EXEMPT` | block-all tunnel mode (everything except SafeMe + whitelist) |

The a11y service checks `ScheduleEngine.isLaunchBlocked(pkg)` before the
keyword engine, independent of the master blocking switch; `launchBlockAll`
exempts critical system surfaces (`com.android.systemui`, Settings, launchers,
package installer, permission controllers, DocumentsUI, media provider,
phone/telecom) so the user can always escape.

## 7. Editing flow (`ui/screens/schedule/`)

- `ScheduleScreen` — list of schedule cards with day/window labels
  (`scheduleDaysLabel`, `scheduleWindowLabel` helpers) and enable toggles.
- `ScheduleEditScreen` — create/edit with `?editId=`; days, start/end pickers
  (validated "start before end"), mode segmented control (Internet | Launch |
  Both), and the shared app picker for `appPackages` (see
  [09](09-app-picker.md)).
- Mutations go through `Context.addSchedule` / `updateSchedule` /
  `deleteSchedule` / `toggleSchedule`; the `schedulePrefs()` flow notifies
  `SafeMeApp`, which calls `ScheduleEngine.apply` and re-arms the alarm.
