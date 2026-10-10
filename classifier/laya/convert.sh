#!/usr/bin/env bash
# laya/out/<mode>/model.onnx -> model.dlc (fp32) -> model_q.dlc (int8, calibrated) -> model_q_htp.dlc
# (HTP graph prepared offline for SM8750 / Hexagon v79). Deletes the big intermediates as it goes
# (disk is tight); logs and times are kept in the same folder.
# Usage: laya/convert.sh base|ft
set -euo pipefail
cd "$(dirname "$0")/.."
source ./qairt-env.sh >/dev/null
d=$PWD/laya/out/$1

s=$(date +%s); echo "[$1] qairt-converter..."
qairt-converter --input_network $d/model.onnx --output_path $d/model.dlc >$d/convert.log 2>&1
echo "convert $(( $(date +%s) - s ))s $(du -m $d/model.dlc | cut -f1) MB" | tee $d/convert.status
rm -f $d/model.onnx $d/model.onnx.data

s=$(date +%s); echo "[$1] snpe-dlc-quant (int8, $(wc -l <$d/calib.txt) calibration inputs)..."
(cd $d && snpe-dlc-quant --input_dlc model.dlc --input_list calib.txt --output_dlc model_q.dlc --use_native_input_files >quant.log 2>&1)
echo "quant $(( $(date +%s) - s ))s $(du -m $d/model_q.dlc | cut -f1) MB" | tee $d/quant.status
rm -f $d/model.dlc

s=$(date +%s); echo "[$1] snpe-dlc-graph-prepare (sm8750)..."
snpe-dlc-graph-prepare --input_dlc $d/model_q.dlc --output_dlc $d/model_q_htp.dlc --htp_socs sm8750 >$d/prepare.log 2>&1
echo "prepare $(( $(date +%s) - s ))s $(du -m $d/model_q_htp.dlc | cut -f1) MB" | tee $d/prepare.status
