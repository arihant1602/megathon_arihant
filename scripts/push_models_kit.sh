#!/usr/bin/env bash
# Copy the on-device models into the (debug) app's internal storage on the dev kit. Android 15 does not let the
# app open files that adb created in its external dir, so they go through /data/local/tmp + run-as.
# usage: ANDROID_SERIAL=<kit> scripts/push_models_kit.sh laya_w8a16_htp.dlc ggml-base.bin Qwen3-0.6B-Q8_0.gguf
set -euo pipefail
: "${ANDROID_SERIAL:?set ANDROID_SERIAL to the dev kit serial}"
pkg=org.seedhecode.incidentassistant
adb shell "run-as $pkg mkdir -p files/models"
for f in "$@"; do
    base=$(basename "$f")
    adb push "$f" "/data/local/tmp/$base"
    adb shell "chmod 644 /data/local/tmp/$base && run-as $pkg cp /data/local/tmp/$base files/models/ && rm /data/local/tmp/$base"
done
adb shell "run-as $pkg ls -la files/models"
