#!/usr/bin/env bash
# Usage: run_test.sh <device: default|bottom|back|id> <source> <seconds> <tag> [rate]
# Triggers one recording in the mic test app, waits for it, prints the result JSON.
set -uo pipefail
PKG=com.sentinel.mictest
adb logcat -c
adb shell am start -n $PKG/.MainActivity --es device "$1" --ei seconds "$3" --es source "$2" --es tag "$4" --ei rate "${5:-16000}" >/dev/null 2>&1
timeout $(( $3 * 3 + 20 )) bash -c 'until adb logcat -d -s MicTest | grep -q "DONE\|ERROR"; do :; done'
adb logcat -d -s MicTest:I | grep -E "ERROR|WARN|INIT" | sed 's/.*MicTest *: //' >&2
adb shell run-as $PKG cat files/"$4".json
echo
