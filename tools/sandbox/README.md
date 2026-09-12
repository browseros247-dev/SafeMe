# SafeMe sandbox toolchain — wipe survival kit

## The problem
This sandbox restores from a snapshot after inactivity. The snapshot keeps
regular files under `/home/user` (repo, commits, docs — capped ~128 MB), but
**always drops**:
- `~/.cache`, `~/.local`, `build/`, `node_modules/` etc. (excluded by design)
- everything outside `/home/user` (e.g. `/swapfile`)
- processes, mounted state, executable bits on some restores (`gradlew +x`)

The Android toolchain (~2.3 GB: 2× JDK, SDK, Gradle caches) plus the 3 GB
swapfile therefore evaporate. The fix is **fast re-provisioning, not
prevention** — downloads run at ~100 MB/s here, so a cold rebuild takes
about 5 minutes.

## Two locations (identical files)
- `~/androenv/` — the live instance used day-to-day.
- `SafeMe/tools/sandbox/` — the versioned mirror, committed to git.
The scripts are location-independent (`env.sh`/`verify.log` resolve next to
the script), so both copies work standalone. Recovery order: use
`~/androenv`; if it is ever lost, copy it back from the repo mirror.

## What persists vs what doesn't
| Survives inactivity | Evaporates |
|---|---|
| `~/SafeMe` incl. `.git` (fix commits + this kit) | `~/.cache/androenv` (JDKs + SDK) |
| `~/SafeMe-Audit-Report.md`, `~/SafeMe-B*-Fix-Plan.md` | `~/.cache/gradle-home` (deps) |
| `~/SafeMe/local.properties` (SDK path pointer) | `/swapfile` (kernel state) |
| `~/androenv/*` (this kit — small text files) | Gradle daemons, `gradlew` exec bit (sometimes) |

## Recovery (one command)
```bash
bash ~/androenv/bootstrap.sh
```
Idempotent: every component is checksummed-by-presence and skipped when
healthy, so re-running on a warm box takes seconds. It restores swap, JDK 25
(+17 fallback), cmdline-tools, SDK packages (platform-tools, android-36,
build-tools 36.0.0), licenses, `local.properties`, `gradlew +x`, and writes
`env.sh`.

Then verify:
```bash
bash ~/androenv/verify.sh
```
Runs the full `:app:testDebugUnitTest` suite plus `:app:lintDebug` with the
proven low-RAM flags (1792m heap, 1 worker, in-process Kotlin) and prints a
result summary, including whether any lint finding touches the B1/B6 files.

For an interactive shell with the toolchain on `PATH`:
```bash
source ~/androenv/env.sh
```

## Why these exact choices
- **JDK 25 runs everything**: the project targets Java 25 bytecode
  (`VERSION_25` in `app/build.gradle.kts`); JDK 17 can compile it but cannot
  *run* the tests (`class file version 69`).
- **Swap is mandatory**: 2 GB RAM cannot link AGP 9 + Kotlin 2.3 (daemon dies
  of Metaspace exhaustion). 3 GB swap + 1792m heap is the proven envelope.
- **Gradle home under `.cache`**: keeps workspace snapshots small; deps
  re-download fast. Cold start ≈ 5 min, warm verify ≈ 1 min.
- **Kit versioned in-repo** (`tools/sandbox/`): the live `~/androenv` copy
  persists via snapshot, but the repo mirror guarantees the kit survives
  even a total workspace loss (as long as the repo is pushed).

## Last verified state
- `:app:testDebugUnitTest`: 29 classes, 291 tests, 0 failures (incl. new
  `BlockedCounterTest` 12/12 and `ScheduleEditEnabledTest` 5/5)
- `:app:lintDebug`: BUILD SUCCESSFUL, 0 findings attributable to B1/B6
- Commits: `38004e3` (B1), `d21fe7d` (B6) on `main`, local-only (push still
  awaits a GitHub credential)
