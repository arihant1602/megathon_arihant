#!/usr/bin/env bash
# Run a typed conversation (one user turn per line) through the on-device pipeline on the dev kit (debug APK).
# usage: ANDROID_SERIAL=<kit> scripts/probe_kit.sh SCRIPT.txt [CLIP.wav] > result.json  (wav: 16 kHz mono, Whisper timing)
set -euo pipefail
: "${ANDROID_SERIAL:?set ANDROID_SERIAL to the dev kit serial}"
pkg=org.seedhecode.incidentassistant
name=$(basename "$1")
adb push "$1" "/data/local/tmp/$name" >/dev/null
adb shell "chmod 644 /data/local/tmp/$name && run-as $pkg sh -c 'mkdir -p files/probe && rm -f files/probe/$name.out.json && cp /data/local/tmp/$name files/probe/' && rm /data/local/tmp/$name"
extra=()
if [ -n "${2:-}" ]; then
    wav=$(basename "$2")
    adb push "$2" "/data/local/tmp/$wav" >/dev/null
    adb shell "chmod 644 /data/local/tmp/$wav && run-as $pkg cp /data/local/tmp/$wav files/probe/ && rm /data/local/tmp/$wav"
    extra=(--es wav "$wav")
fi
adb shell am start -W -n $pkg/.ProbeActivity --es script "$name" "${extra[@]}" >/dev/null
for _ in $(seq 1 300); do
    sleep 2
    adb shell "run-as $pkg test -f files/probe/$name.out.json" 2>/dev/null && break
done
adb exec-out run-as $pkg cat "files/probe/$name.out.json"
