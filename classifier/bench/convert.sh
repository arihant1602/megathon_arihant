#!/usr/bin/env bash
# Convert bench/out/<model>_L<len>/model.onnx -> model.dlc (fp32), and if calib.txt exists
# -> model_q.dlc (8-bit, snpe-dlc-quant). Times and logs go into the same folder.
# Usage: bench/convert.sh [dir ...]   (default: every bench/out/*/ with a model.onnx)
set -uo pipefail
cd "$(dirname "$0")/.."
source ./qairt-env.sh >/dev/null
dirs=("$@"); [ ${#dirs[@]} -eq 0 ] && dirs=(bench/out/*/)
for d in "${dirs[@]}"; do
  d=${d%/}; [ -f "$d/model.onnx" ] || continue
  if [ ! -f "$d/model.dlc" ]; then
    s=$(date +%s.%N)
    if qairt-converter --input_network "$d/model.onnx" --output_path "$d/model.dlc" >"$d/convert.log" 2>&1; then
      printf '%s convert OK %.1fs %s bytes\n' "$d" "$(echo "$(date +%s.%N) - $s" | bc)" "$(stat -c%s "$d/model.dlc")" | tee "$d/convert.status"
    else
      echo "$d convert FAIL (see convert.log)" | tee "$d/convert.status"; tail -5 "$d/convert.log"; continue
    fi
  fi
  if [ -f "$d/calib.txt" ] && [ ! -f "$d/model_q.dlc" ]; then
    s=$(date +%s.%N)
    if (cd "$d" && snpe-dlc-quant --input_dlc model.dlc --input_list calib.txt --output_dlc model_q.dlc --use_native_input_files) >"$d/quant.log" 2>&1; then
      printf '%s quant OK %.1fs %s bytes\n' "$d" "$(echo "$(date +%s.%N) - $s" | bc)" "$(stat -c%s "$d/model_q.dlc")" | tee "$d/quant.status"
    else
      echo "$d quant FAIL (see quant.log)" | tee "$d/quant.status"; tail -5 "$d/quant.log"
    fi
  fi
done
