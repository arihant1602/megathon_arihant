#!/usr/bin/env bash
# Temporarily moves the kit's MIC1 (amic1) and MIC4 (amic5) record routes onto DEC0,
# matching QMMI's working MIC4 loopback routing. Bind mount only: nothing is written
# to /vendor, and a reboot undoes it. Revert without reboot: ./apply.sh revert
set -euo pipefail
cd "$(dirname "$0")"
V=/vendor/etc/audio/sku_sun/mixer_paths_sun_qrd.xml
T=/data/local/tmp/mixer_paths_sun_qrd.xml

adb root >/dev/null; adb wait-for-device
if [ "${1:-}" = revert ]; then
  adb shell "umount $V; stop vendor.audio-hal-aidl; start vendor.audio-hal-aidl; stop audioserver; start audioserver"
  echo "reverted"; exit 0
fi
adb push mixer_paths_sun_qrd.xml $T >/dev/null
adb shell "umount $V 2>/dev/null; chcon u:object_r:vendor_configs_file:s0 $T && chmod 644 $T && mount -o bind $T $V"
adb shell "grep -A4 'path name=\"amic5\"' $V"
adb shell "stop vendor.audio-hal-aidl; start vendor.audio-hal-aidl; stop audioserver; start audioserver"
until [ "$(adb shell getprop init.svc.audioserver | tr -d '\r')" = running ]; do :; done
echo "applied; audio HAL: $(adb shell getprop init.svc.vendor.audio-hal-aidl)"
