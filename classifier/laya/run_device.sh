#!/usr/bin/env bash
# Run laya/out/<mode>/model_q_htp.dlc on the SM8750 dev kit NPU (Hexagon v79 HTP) over every test input,
# pull the raw outputs to laya/out/<mode>/device/ and save the timing summary.
# Usage: [ANDROID_SERIAL=fe7980db] laya/run_device.sh base|ft [variant]
#   variant: a quantized model_<variant>_htp.dlc on the HTP (default q), or fp32cpu / fp32gpu for the unquantized model.dlc
#   list: input list name in laya/out/<mode>/ (default test -> test.txt + test/)
set -euo pipefail
cd "$(dirname "$0")/.."
source ./qairt-env.sh >/dev/null
export ANDROID_SERIAL=${ANDROID_SERIAL:-fe7980db}
mode=$1; var=${2:-q}; list=${3:-test}; d=laya/out/$mode; D=/data/local/tmp/snpe; R=$D/laya_${mode}_${var}_$list
o=$d/device_$var; [[ $list == test ]] || o=${o}_$list
case $var in
  fp32cpu) dlc=model.dlc; rt= ;;              # unquantized, Kryo CPU, fp32
  fp32gpu) dlc=model.dlc; rt=--use_gpu ;;     # unquantized, Adreno GPU, fp32 math / fp16 storage
  *)       dlc=model_${var}_htp.dlc; rt=--use_dsp ;;   # quantized, Hexagon HTP
esac
soc=$(adb shell getprop ro.soc.model | tr -d '\r')
[[ $soc == SM8750* ]] || { echo "connected device is '$soc', expected the SM8750 dev kit"; exit 1; }
echo "device $ANDROID_SERIAL / $soc"
adb shell mkdir -p $D
adb push --sync "$QAIRT_SDK_ROOT/lib/aarch64-android/." "$QAIRT_SDK_ROOT/lib/hexagon-v79/unsigned/." \
  "$QAIRT_SDK_ROOT/bin/aarch64-android/snpe-net-run" $D/ >/dev/null 2>&1
adb shell "chmod +x $D/snpe-net-run 2>/dev/null; rm -rf $R; mkdir -p $R"
adb push $d/$dlc $d/$list $d/$list.txt $R/ >/dev/null 2>&1
s=$(date +%s)
adb shell "cd $R && LD_LIBRARY_PATH=$D ADSP_LIBRARY_PATH='$D;/vendor/lib/rfsa/adsp;/system/lib/rfsa/adsp;/dsp' \
  $D/snpe-net-run --container $dlc --input_list $list.txt --output_dir out $rt \
  --userbuffer_auto --use_native_input_files --perf_profile burst --profiling_level basic" >$o.run.log 2>&1
echo "ran $(wc -l <$d/$list.txt) inputs in $(( $(date +%s) - s ))s"
rm -rf $o; mkdir -p $o
adb pull $R/out $o/ >/dev/null 2>&1
adb exec-out cat $R/out/SNPEDiag.log >$o/SNPEDiag.log
snpe-diagview --input_log $o/SNPEDiag.log >$o/diagview.txt 2>&1
grep -E "Total Inference Time|^  Init:" $o/diagview.txt
adb shell rm -rf $R
