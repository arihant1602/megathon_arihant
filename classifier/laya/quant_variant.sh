#!/usr/bin/env bash
# Quantize laya/out/<mode>/model.dlc into a named variant and prepare it for SM8750 HTP.
# Usage: laya/quant_variant.sh <mode> <variant> [snpe-dlc-quant options...]
#   e.g. laya/quant_variant.sh base w8a16 --act_bitwidth 16 --bias_bitwidth 32 --use_per_channel_quantization
set -euo pipefail
cd "$(dirname "$0")/.."
source ./qairt-env.sh >/dev/null
mode=$1; var=$2; shift 2; d=$PWD/laya/out/$mode
s=$(date +%s)
(cd $d && snpe-dlc-quant --input_dlc model.dlc --input_list calib.txt --output_dlc model_$var.dlc --use_native_input_files "$@" >quant_$var.log 2>&1)
snpe-dlc-graph-prepare --input_dlc $d/model_$var.dlc --output_dlc $d/model_${var}_htp.dlc --htp_socs sm8750 >$d/prepare_$var.log 2>&1
rm -f $d/model_$var.dlc
echo "$mode/$var: $(( $(date +%s) - s ))s, $(du -m $d/model_${var}_htp.dlc | cut -f1) MB, options: $*" | tee $d/variant_$var.status
