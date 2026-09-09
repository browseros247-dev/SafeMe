#!/usr/bin/env bash
# SafeMe verification suite — compile + unit tests + lint.
# Hardened for post-restore flakiness: detects real failures (pipefail),
# logs everything, retries once after stopping stale daemons, forces fresh
# test execution (cleanTest) and refuses to report green on stale test XMLs.
# Location-independent (see bootstrap.sh header): env.sh and verify.log
# resolve next to this script.
# NOTE: Gradle args live in bash ARRAYS — never in flat strings — so JVM
# options containing spaces survive as single arguments.
set -u
set -o pipefail
SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" >/dev/null 2>&1 && pwd)"
# shellcheck source=/dev/null
source "$SCRIPT_DIR/env.sh"
cd /home/user/SafeMe
chmod +x gradlew 2>/dev/null || true

BASE=(./gradlew -Dorg.gradle.workers.max=1 -Pkotlin.compiler.execution.strategy=in-process --console=plain)
JVM=(-Dorg.gradle.jvmargs="-Xmx1792m -XX:MaxMetaspaceSize=512m -Dfile.encoding=UTF-8")
LOG=$SCRIPT_DIR/verify.log
RUN_START=$(date +%s)
: > "$LOG"

# gradle_run <task...>: runs once; on failure stops daemons and retries once.
gradle_run() {
    echo "--- gradle $* (attempt 1) ---" >>"$LOG"
    if "${BASE[@]}" "$@" "${JVM[@]}" >>"$LOG" 2>&1; then return 0; fi
    echo "[verify] attempt 1 failed — stopping daemons, retrying once..." | tee -a "$LOG"
    "${BASE[@]}" --stop "${JVM[@]}" >>"$LOG" 2>&1 || true
    sleep 3
    echo "--- gradle $* (attempt 2) ---" >>"$LOG"
    "${BASE[@]}" "$@" "${JVM[@]}" >>"$LOG" 2>&1
}

# new_log: print only log lines appended since $1 (a line count).
new_log() { tail -n +"$(( $1 + 1 ))" "$LOG"; }

fail=0
echo "=== [1/2] :app:testDebugUnitTest (full suite, forced re-run) ==="
mark=$(wc -l <"$LOG")
if gradle_run :app:cleanTestDebugUnitTest :app:testDebugUnitTest; then
    new_log "$mark" | grep -E "BUILD (SUCCESSFUL|FAILED)" | tail -1
else
    fail=1
    echo "TEST TASK FAILED — relevant log excerpt:"
    new_log "$mark" | grep -A15 "What went wrong" | head -20
fi
python3 - "$RUN_START" <<'EOF'
import glob, os, sys, xml.etree.ElementTree as ET
start = int(sys.argv[1])
t=s=f=e=0; files=0; stale=False
for p in glob.glob('app/build/test-results/testDebugUnitTest/*.xml'):
    r = ET.parse(p).getroot(); files+=1
    t+=int(r.get('tests',0)); s+=int(r.get('skipped',0)); f+=int(r.get('failures',0)); e+=int(r.get('errors',0))
    if int(os.path.getmtime(p)) < start: stale=True
print(f'RESULT: {files} classes, {t} tests, {s} skipped, {f} failures, {e} errors'
      + (' — STALE XMLs, DO NOT TRUST' if stale and files else ''))
sys.exit(1 if (stale and files) or f or e else 0)
EOF
[ $? -ne 0 ] && fail=1

echo "=== [2/2] :app:lintDebug ==="
mark=$(wc -l <"$LOG")
if gradle_run :app:lintDebug; then
    new_log "$mark" | grep -E "BUILD (SUCCESSFUL|FAILED)|Lint found" | tail -2
else
    fail=1
    echo "LINT TASK FAILED — relevant log excerpt:"
    new_log "$mark" | grep -A15 "What went wrong" | head -20
fi
python3 -c "
import xml.etree.ElementTree as ET
t = ET.parse('app/build/reports/lint-results-debug.xml')
mine = ['BlockingPrefs.kt','BlockingScreen.kt','BlockingViewModel.kt','HomeViewModel.kt',
        'ScheduleEditViewModel.kt','ScheduleEditScreen.kt','BlockedCounterTest.kt','ScheduleEditEnabledTest.kt']
n=0
for i in t.getroot().findall('issue'):
    files=[l.get('file','') for l in i.findall('location')]
    if any(any(m in f for m in mine) for f in files):
        n+=1; print('TOUCHES B1/B6:', i.get('severity'), i.get('id'), (i.get('message') or '')[:100])
print('RESULT: lint findings touching B1/B6 Kotlin files:', n)
"

[ $fail -eq 0 ] && echo "VERIFY: ALL GREEN" || { echo "VERIFY: FAILURES PRESENT (full log: $LOG)"; exit 1; }
