#!/usr/bin/env bash
# Build Whisper-base encoder DLCs (FP16, HTP cache for SM8750) for the NPU path in core/native-whisper.
# Needs the QAIRT 2.47 SDK env (qairt-converter, snpe-dlc-graph-prepare) and torch + onnx.
# usage: build_encoder_dlc.sh ggml-base.bin OUT_DIR
# Then: ANDROID_SERIAL=<kit> scripts/push_models_kit.sh OUT_DIR/whisper_base_encoder_*_htp.dlc
# Measured on the SM8750 kit: 30 s window 190 ms, 10 s window 18 ms (vs PyTorch: cosine 0.99992).
set -euo pipefail
here=$(cd "$(dirname "$0")" && pwd)
mkdir -p "$2"
for spec in "1500 whisper_base_encoder_fp16_htp" "500 whisper_base_encoder_10s_fp16_htp"; do
    set -- "$1" "$2" $spec
    python "$here/export_encoder.py" "$1" "$2/encoder_$3.onnx" "$3"
    qairt-converter --input_network "$2/encoder_$3.onnx" --float_bitwidth 16 --output_path "$2/encoder_$3_fp16.dlc"
    snpe-dlc-graph-prepare --input_dlc "$2/encoder_$3_fp16.dlc" --htp_socs sm8750 --output_dlc "$2/$4.dlc"
done
