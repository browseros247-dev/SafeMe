# STORAGE ADJUSTMENT V12 PLAN — Fast Clean (Plan Only)

> Context: Session created 197.9 MB / 632 files > 128 MB limit → 10 files not saved. Previous BlockerX artifacts (190 MB) already deleted, workspace back to 124 MB SafeMe + 3.1 MB root APK, but still over due to stacked artifacts. User: "You have stacked it and can move to the next so you have the fast clean. This, we adjust storage." /Plan Only mode — no execution, no code changes to app logic.

## 1. Current Audit (from `du -sh`, 2026-09-16)

| Path | Size | Files | Save-worthy? | Root cause |
|---|---|---|---|---|
| `SafeMe/.git/objects/pack/*.pack` | 58 MB | 1 | YES (history) but bloated | APKs committed in history (Reference/*.apk, artifacts/*.apk, old releases) |
| `SafeMe/Reference/NopoX v1.0.53.apk` | 23 MB | 1 | NO for build | Reference APK for manual analysis, never used by gradle |
| `SafeMe/artifacts/app-debug.apk` | 15 MB | 1 | NO | Debug build leftover, duplicate |
| `SafeMe/artifacts/app-release.apk` | 3.1 MB | 1 | NO (duplicate) | Duplicate of `/home/user/SafeMe-0.1.0-release.apk` (V11) |
| `SafeMe/.gradle/` (9.6.1 + config-cache) | 14 MB | ~300 | NO | Gradle cache — NOT in snapshot exclusion list (excluded: build, .cache, node_modules etc., but NOT .gradle) → counts toward 128 MB |
| `SafeMe/graphify-out/` (graph.html 2.6M + graph.json 3.3M) | 7 MB | 5 | NO | Generated analysis output from graphify tool |
| `SafeMe/app/` src only | ~3 MB | ~200 | YES | Actual source |
| 12x `SOCIAL_*.md`, `SHORTS_*.md`, `HOME_*.md`, `TAB_*.md` | ~150 KB | 12 | PARTIAL | Stacked plan history — only V11 active |
| Root `SafeMe-0.1.0-release.apk` | 3.1 MB | 1 | YES (single deliverable) | V11 final |

Total counted: ~124 MB SafeMe + 3.1 MB root = 127.1 MB + hidden .gradle already inside that 124 MB. But session writer counted 197.9 MB because it counts all writes including `app/build/` (14 MB intermediates) that are excluded from final snapshot but counted as "created this session" — and 10 files were dropped.

### Why snapshot keeps resetting:
- `.gradle` not excluded → every build adds 14 MB to snapshot
- `artifacts/` + `Reference/` + `graphify-out/` = 48 MB pure bloat
- `.git` pack 58 MB grows each commit that adds APKs (we commit APK hash in message but APK itself not in repo — however old commits had APKs? Need `git log --name-only` check)

## 2. Goals (standing constraints)

1. **Minimal code**: solve without code, or small config/cleanup script only. No app logic changes.
2. **Production Ready With Scalability**: single source of truth for APK, no duplicates, deterministic clean builds, <80 MB target, <500 files.
3. **No influence on other features**: 100% surety — only delete generated/reference files, never `app/src`, `keystore/`, `gradle-wrapper`, `tools/sandbox/bootstrap.sh`.
4. **Fast clean**: after adjustment, `bootstrap.sh` + `assembleRelease` should be <12 min cold, snapshot <80 MB.

## 3. Plan — 3 Phases, Zero App Code

### Phase A: Immediate Safe Delete (saves ~48 MB, no risk)

All paths verified NOT referenced by `app/build.gradle.kts`, `settings.gradle.kts`, `tools/sandbox/*.sh`:

```bash
# 1. Generated analysis — never used by build
rm -rf /home/user/SafeMe/graphify-out/

# 2. Duplicate APKs — keep only root deliverable
rm -rf /home/user/SafeMe/artifacts/
# root /home/user/SafeMe-0.1.0-release.apk is the single deliverable (already present_file)

# 3. Reference APK — 23 MB, manual only, can be re-downloaded
# Option: move to external doc link, not in workspace
rm -rf /home/user/SafeMe/Reference/
# If need to keep manifest for reference:
# mkdir -p SafeMe/Reference && keep only manifest_dump.xml + jadx.log (<100KB)

# 4. Gradle cache — safe to delete, bootstrap restores toolchain
rm -rf /home/user/SafeMe/.gradle/
rm -rf /home/user/SafeMe/app/build/  # already gone, but ensure
```

**Expected after A**: 124 MB → ~62 MB SafeMe + 3.1 MB root = 65 MB. Files: 627 → ~320.

### Phase B: Prevent Recurrence (config only, ~5 lines)

1. **`.gitignore` hardening** — append (currently missing):
```
# V12 storage guard
/artifacts/
/graphify-out/
/Reference/*.apk
/app/build/
/build/
.gradle/
*.apk
!keystore/*.jks
```
Note: keep root `SafeMe-0.1.0-release.apk` outside SafeMe/ (in /home/user/) so gitignore `*.apk` inside SafeMe/ doesn't delete deliverable, but prevents future accidental commits.

2. **Create `tools/sandbox/clean.sh`** (small, <30 lines):
```bash
#!/bin/bash
# Fast clean — idempotent, called after verification
set -e
cd "$(dirname "$0")/../.."
rm -rf app/build/ build/ .gradle/ artifacts/ graphify-out/
echo "clean: $(du -sh . 2>/dev/null | tail -1)"
```
Make executable, call at end of `verify.sh`.

3. **Git pack shrink** (optional, if .git stays >50 MB after A):
```bash
git gc --prune=now --aggressive
# If still >50 MB, history contains APK blobs — do NOT rewrite history now (risk).
# Instead document: future commits must NOT add *.apk, use external release.
```

### Phase C: Scalable Production Storage (no code)

- **Single APK path**: `/home/user/SafeMe-0.1.0-release.apk` only. CI copies from `app/build/outputs/apk/release/app-release.apk` then deletes build dir.
- **Plan docs consolidation**: Move old `SOCIAL_MEDIA_BLOCKING_*.md` (V4-V10) to `docs/archive/` or delete, keep only `SOCIAL_TAB_FALSE_POSITIVES_V11_PLAN.md` + this V12 plan. Saves ~120 KB + inode count.
- **Reference material**: Store NopoX/BlockerX analysis as link + notes in plan, not APK binary. If needed, download on-demand in `analysis/` which is gitignored and cleaned after use.
- **Target**: <80 MB workspace, <500 files, snapshot <128 MB with 30% headroom for next build.

## 4. Verification Gates (same as V11, plus storage)

| Gate | Command | Expected |
|---|---|---|
| Audit | `du -sh SafeMe; find SafeMe -type f | wc -l` | <80 MB, <500 files |
| No APK in repo | `git ls-files | grep -E "\.apk$"` | empty (only root outside repo) |
| .git size | `du -sh SafeMe/.git` | <35 MB after gc |
| Build still works | `bash tools/sandbox/bootstrap.sh && ./gradlew :app:testDebugUnitTest :app:assembleRelease --console=plain` | 357 tests, BUILD SUCCESSFUL |
| Deliverable | `ls -lh /home/user/SafeMe-0.1.0-release.apk && sha256sum` | 3.1 MB, signer 347be353... |
| Snapshot | Next session start | no "Workspace over budget" |

## 5. What NOT to do (100% surety)

- Do NOT delete `app/src/`, `keystore/safeme-release.jks`, `keystore.properties`, `gradle/wrapper/`, `tools/sandbox/bootstrap.sh`, `lint-baseline.xml`
- Do NOT `git filter-branch` / history rewrite (would change commit hashes, break remote)
- Do NOT add `.gradle` to snapshot exclusion list (platform-controlled) — just clean it
- Do NOT touch `SafeMe-0.1.0-release.apk` root deliverable

## 6. Execution Steps (for next turn, when authorized)

1. `rm -rf graphify-out/ artifacts/ Reference/ .gradle/ app/build/`
2. `git gc --prune=now --aggressive` (measure .git)
3. Append to `.gitignore` (5 lines)
4. Create `tools/sandbox/clean.sh` + chmod +x
5. Re-run audit + single foreground build verification
6. Commit local: `chore(storage): fast clean — remove 48 MB bloat, guard 128 MB limit`

**Estimated time**: cleanup 5s, gc 10s, verification 13-15 min cold.

---
Prepared 2026-09-16 — Plan Only, no execution, no app code change.
