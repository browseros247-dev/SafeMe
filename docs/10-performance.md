# 10 — Performance

Performance is a first-class constraint: SafeMe runs long-lived background
services and must stay invisible when idle. This document records the
measured optimizations and the principles that produced them.

## 1. Measured results (baseline → after)

| Metric | Before | After |
|---|---|---|
| Release APK size | 10.3 MB (no minify) | **3.07 MB** (−70%) with R8 minify + resource shrinking |
| Cold start | 1406 / 1789 ms | 1487 / 1764 ms — unchanged within noise (sync theme read is free) |
| Idle background work | 60 s DataStore re-read + full re-evaluate **always**; 30 s a11y poll **always** (even with protection off) | Both gated: ticker only while a schedule is enabled; poll only while protection is on (the ContentObserver still covers the instant path) |
| Startup theme flash | First frame used SYSTEM, then flipped to the stored pref; window bg followed system only | Theme pref read synchronously in `Application.onCreate` → first Compose frame is correct; window background matched before first frame → **no flash** |
| Per-frame allocations | `blurredShadow` built a new `Paint` + `BlurMaskFilter` on every draw | Built lazily once per modifier, reused across frames |
| Redundant recomposition | Every DataStore emission (even identical) triggered a full Home rebuild + uiState copy | `distinctUntilChanged` on all Home prefs sources + activity feed |

## 2. Principles

### Gate background work by need

- The 60 s schedule safety ticker (`SafeMeApp`) only runs while ≥1 schedule
  is enabled. With none, a reevaluate would just re-read DataStore from disk
  every minute for no effect.
- The 30 s a11y-guard poll only runs while protection is on; the
  `ContentObserver` (instant path) always covers re-enable.
- The VPN watchdog thread only exists while a tunnel is active.

### No blocking reads on hot paths

- Process-wide `@Volatile` holders (`ThemePrefHolder`, `AppLockStateHolder`,
  `A11yProtectionStateHolder`, the a11y service's cached state, `VpnStatusStore`)
  are fed by DataStore collectors at startup and read synchronously in event
  handlers / the gate. A stale read is always safer than a blocking one.
- The one synchronous read is the theme pref in `Application.onCreate`
  (~few ms) — it eliminates the first-frame theme flash.

### Bound every per-event cost

- The a11y node walk is bounded: max depth 12, max 200 strings, nodes
  recycled on API 26–32.
- Expensive node-tree probes are throttled (a11y detail-page probe 10 s,
  kick 15 s, PU toast 60 s, schedule recheck 5 s).
- The 4 s block cooldown prevents gate relaunch loops.

### Compose hygiene

- `distinctUntilChanged` on DataStore-derived flows so bursts of identical
  emissions skip recomposition.
- `blurredShadow` reuses a lazily-built `Paint`/`BlurMaskFilter`.
- Screens use `LazyColumn`/`verticalScroll`; pickers chunk data; icons are
  pre-drawn vectors (no runtime rasterization).

### Shrink the artifact

- Release builds: `isMinifyEnabled = true`, `isShrinkResources = true`,
  `proguard-android-optimize.txt` + `proguard-rules.pro`. No reflection /
  serialization in the codebase, so R8 is safe.
- Verified: minified release APK launches, renders Home + Profile, and shows
  zero `FATAL EXCEPTION` / `ClassNotFound` in logcat.

## 3. What is deliberately NOT optimized further

- **VPN watchdog blocking `Os.read`**: a blocking read with a 50 ms backoff
  is a liveness detector that only runs while a tunnel is active — inherent
  to the DNS-only architecture, not a bottleneck.
- **Alarm vs ticker tradeoff**: exact boundary alarms are preferred; the
  60 s ticker is the bounded fallback for API 31+ without exact-alarm
  permission. The ticker is cheap because it is gated by enabled schedules.
- **DataStore reads in `reevaluate`**: schedule changes are rare; the
  ticker does not run when idle, and `apply` is idempotent so repeated
  evaluations never restart healthy services.

## 4. Verification workflow

Each optimization was verified incrementally:

1. Establish the baseline (APK size, cold-start timing via
   `adb shell am start -W`, logcat inspection).
2. Make one change.
3. Re-measure and confirm no regression (cold start within noise, no new
   lint findings, unit tests green).
4. Smoke-test the affected screens on the emulator; for R8 changes, install
   the minified APK and confirm launch + navigation with zero crashes.
