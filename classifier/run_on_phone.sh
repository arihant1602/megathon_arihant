#!/usr/bin/env bash
# Run a DLC on the connected Snapdragon phone and print timing per processor.
# Usage: ./run_on_phone.sh <model.dlc> <input_list.txt> <runtime: cpu|gpu|dsp> [quantized.dlc for dsp]
# Raw files listed in input_list.txt must sit next to it.
set -euo pipefail
HERE="$(cd "$(dirname "$0")" && pwd)"
source "$HERE/qairt-env.sh" >/dev/null

DLC=$1; LIST=$2; RT=$3
D=/data/local/tmp/snpe
case $RT in
  cpu) FLAG= ;;
  gpu) FLAG=--use_gpu ;;
  dsp) FLAG=--use_dsp; DLC=${4:-$DLC} ;;   # DSP needs an 8-bit model (snpe-dlc-quant)
  *) echo "runtime must be cpu|gpu|dsp"; exit 1 ;;
esac

echo "Device: $(adb shell getprop ro.product.model) / SoC $(adb shell getprop ro.soc.model)"
# SDK runtime libs (ARM) + Hexagon v66 DSP libs (Snapdragon 695); pushed once, adb skips unchanged files
adb shell mkdir -p $D
adb push --sync "$QAIRT_SDK_ROOT/lib/aarch64-android/." "$QAIRT_SDK_ROOT/lib/hexagon-v66/unsigned/." \
  "$QAIRT_SDK_ROOT/bin/aarch64-android/snpe-net-run" $D/ >/dev/null
adb push "$DLC" "$LIST" $D/ >/dev/null
(cd "$(dirname "$LIST")" && adb push $(cat "$(basename "$LIST")") $D/ >/dev/null)

adb shell "cd $D && chmod +x snpe-net-run && rm -rf out_$RT && \
  LD_LIBRARY_PATH=$D ADSP_LIBRARY_PATH='$D;/vendor/lib/rfsa/adsp;/system/lib/rfsa/adsp;/dsp' \
  ./snpe-net-run --container $(basename "$DLC") --input_list $(basename "$LIST") --output_dir out_$RT $FLAG" | tail -1

OUT="$(dirname "$LIST")/phone_out/$RT"; rm -rf "$OUT"; mkdir -p "$OUT"
adb pull $D/out_$RT/Result_0 "$OUT/" >/dev/null
adb exec-out cat $D/out_$RT/SNPEDiag.log > "$OUT/SNPEDiag.log"
snpe-diagview --input_log "$OUT/SNPEDiag.log" | grep -E "Total Inference Time|RPC Execute|Subnet 0"
echo "Outputs: $OUT/Result_0"
