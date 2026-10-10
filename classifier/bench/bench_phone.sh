#!/usr/bin/env bash
# Benchmark bench/out/<model>_L<len>/ on the connected Snapdragon device: cpu + gpu use model.dlc,
# dsp (Hexagon HTP) uses model_q.dlc. Runs every input in calib.txt with profiling, then saves
# run log, raw SNPEDiag.log and snpe-diagview CSV/summary in <dir>/device/<rt>/.
# Results go to <dir>/device/<SoC>/<rt>/. With several devices attached, set ANDROID_SERIAL.
# Usage: [ANDROID_SERIAL=xxx] bench/bench_phone.sh [dir ...] [-- cpu gpu dsp]   (default: all dirs, all runtimes)
set -uo pipefail
cd "$(dirname "$0")/.."
source ./qairt-env.sh >/dev/null

dirs=(); rts=()
while [ $# -gt 0 ]; do [ "$1" = "--" ] && { shift; rts=("$@"); break; }; dirs+=("${1%/}"); shift; done
[ ${#dirs[@]} -eq 0 ] && dirs=(bench/out/*_L*)
[ ${#rts[@]} -eq 0 ] && rts=(cpu gpu dsp)

if [ -z "${ANDROID_SERIAL:-}" ] && [ "$(adb devices | awk 'NR>1 && $2=="device"' | wc -l)" -gt 1 ]; then
  echo "several devices attached, set ANDROID_SERIAL:"; adb devices -l; exit 1
fi
D=/data/local/tmp/snpe
SOC=$(adb shell getprop ro.soc.model | tr -d '\r')
case $SOC in
  SM8750*) HEX=v79 ;; SM8650*) HEX=v75 ;; SM8550*) HEX=v73 ;; SM6375*) HEX=v66 ;;
  *) echo "unknown SoC $SOC: set the Hexagon version"; exit 1 ;;
esac
echo "Device: $(adb shell getprop ro.product.model | tr -d '\r') / $SOC / Hexagon $HEX"
adb shell mkdir -p $D/bench
adb push --sync "$QAIRT_SDK_ROOT/lib/aarch64-android/." "$QAIRT_SDK_ROOT/lib/hexagon-$HEX/unsigned/." \
  "$QAIRT_SDK_ROOT/bin/aarch64-android/snpe-net-run" $D/ >/dev/null 2>&1
adb shell chmod +x $D/snpe-net-run

for d in "${dirs[@]}"; do
  name=$(basename "$d"); R=$D/bench/$name
  adb shell "rm -rf $R && mkdir -p $R"
  adb push "$d/inputs" "$d/calib.txt" $R/ >/dev/null 2>&1
  for rt in "${rts[@]}"; do
    case $rt in
      cpu) dlc=model.dlc;   flag= ;;          # CPU is the default runtime
      gpu) dlc=model.dlc;   flag=--use_gpu ;;
      dsp) dlc=model_q.dlc; flag=--use_dsp ;;
    esac
    out="$d/device/$SOC/$rt"; rm -rf "$out"; mkdir -p "$out"
    [ -f "$d/$dlc" ] || { echo "$name $rt: no $dlc, skipped" | tee "$out/status"; continue; }
    adb push "$d/$dlc" $R/ >/dev/null 2>&1
    # no runtime fallback flag: if a layer can't run on $rt the run fails instead of silently using CPU
    adb shell "cd $R && LD_LIBRARY_PATH=$D ADSP_LIBRARY_PATH='$D;/vendor/lib/rfsa/adsp;/system/lib/rfsa/adsp;/dsp' \
      $D/snpe-net-run --container $dlc --input_list calib.txt --output_dir out_$rt $flag \
      --userbuffer_auto --use_native_input_files --perf_profile burst --profiling_level basic" >"$out/run.log" 2>&1
    rc=$?
    adb exec-out cat $R/out_$rt/SNPEDiag.log >"$out/SNPEDiag.log" 2>/dev/null
    if [ $rc -ne 0 ] || [ ! -s "$out/SNPEDiag.log" ]; then
      echo "$name $rt: FAIL (see $out/run.log)" | tee "$out/status"; tail -3 "$out/run.log"; continue
    fi
    snpe-diagview --input_log "$out/SNPEDiag.log" --output "$out/diag.csv" >"$out/diagview.txt" 2>&1
    adb shell rm -f $R/$dlc; adb shell rm -rf $R/out_$rt
    echo "$name $rt: OK" | tee "$out/status"
  done
done
