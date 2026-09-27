#!/usr/bin/env bash
set -euo pipefail

count_log() {
    adb logcat -d -v brief | grep -c "$1" || true
}

initial_kernels=$(count_log 'initialized emulator kernel')
initial_activities=$(count_log 'Activity.onCreate()')
test "$initial_kernels" -gt 0
test "$initial_activities" -gt 0

adb shell settings put system user_rotation 1
sleep 5
test "$(count_log 'initialized emulator kernel')" -eq "$initial_kernels"
test "$(count_log 'Activity.onCreate()')" -eq "$initial_activities"

adb shell input keyevent HOME
sleep 2
adb shell am start -n org.codewiz.droid64/ui.FullscreenActivity
sleep 2
test "$(count_log 'initialized emulator kernel')" -eq "$initial_kernels"
adb shell pidof org.codewiz.droid64
adb logcat -d -v brief > /tmp/droid64-lifecycle-log.txt
if grep -q 'FATAL EXCEPTION' /tmp/droid64-lifecycle-log.txt; then
    echo 'App crashed during lifecycle smoke test' >&2
    exit 1
fi
