#!/usr/bin/env bash
# Copy model files/folders into the (debug) app's private storage on the device: files/models/.
# Android 15 does not let the app open files that adb created in its external dir, so everything goes through
# /data/local/tmp + run-as (needs a debuggable build, e.g. assembleDebug). Install the app first.
#
# usage: ANDROID_SERIAL=<device> scripts/push_models_kit.sh PATH...
#   PATH is a file (-> files/models/<name>) or a folder (-> files/models/<name>/, e.g. voice/).
# example: ANDROID_SERIAL=<device> scripts/push_models_kit.sh models/*
set -euo pipefail
: "${ANDROID_SERIAL:?set ANDROID_SERIAL to the device serial (see adb devices)}"
pkg=org.seedhecode.incidentassistant
adb shell "run-as $pkg mkdir -p files/models" || { echo "Install the debug APK first (run-as needs a debuggable build)." >&2; exit 1; }
for f in "$@"; do
    base=$(basename "$f")
    if [ -d "$f" ]; then
        tmp=$(mktemp)
        tar -C "$(dirname "$f")" -cf "$tmp" "$base"
        adb push "$tmp" "/data/local/tmp/$base.tar" >/dev/null
        rm -f "$tmp"
        adb shell "chmod 644 /data/local/tmp/$base.tar && run-as $pkg sh -c 'cd files/models && rm -rf $base && tar xf /data/local/tmp/$base.tar' && rm /data/local/tmp/$base.tar"
    else
        adb push "$f" "/data/local/tmp/$base" >/dev/null
        adb shell "chmod 644 /data/local/tmp/$base && run-as $pkg cp /data/local/tmp/$base files/models/ && rm /data/local/tmp/$base"
    fi
    echo "pushed $base"
done
adb shell "run-as $pkg ls -la files/models"
