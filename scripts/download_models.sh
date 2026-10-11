#!/usr/bin/env bash
# Download every model the app needs into MODEL_DIR (the layout of the app's files/models/) and verify checksums.
# usage: scripts/download_models.sh MODEL_DIR        (about 2.1 GB)
# Then:  ANDROID_SERIAL=<device> scripts/push_models_kit.sh MODEL_DIR/*
set -euo pipefail
dir=${1:?usage: scripts/download_models.sh MODEL_DIR}
mkdir -p "$dir"
release=https://github.com/arihant1602/megathon_arihant/releases/download/models-v1
fetch() {   # url file sha256
    if [ -f "$dir/$2" ] && echo "$3  $dir/$2" | sha256sum -c --status; then echo "ok (already there): $2"; return; fi
    echo "downloading $2"
    curl -fL --retry 5 -C - -o "$dir/$2.part" "$1"
    echo "$3  $dir/$2.part" | sha256sum -c --status || { echo "checksum mismatch for $2" >&2; exit 1; }
    mv "$dir/$2.part" "$dir/$2"
    echo "ok: $2"
}
# Ours (fine-tuned / compiled for the SM8750 NPU), from the GitHub release.
fetch "$release/laya_w8a16_htp.dlc" laya_w8a16_htp.dlc 07de147df64fb7fea5eca2224b453d90eaca68d8036ef53beb564cc6894153a6
fetch "$release/whisper_base_encoder_fp16_htp.dlc" whisper_base_encoder_fp16_htp.dlc 9b214fcbaca8f40246a99d94792dfee34608f6f53d82d2533dd076032b4d2a0d
fetch "$release/whisper_base_encoder_10s_fp16_htp.dlc" whisper_base_encoder_10s_fp16_htp.dlc 73f9cd370569d13945c0fc9eacf607a4779b3ddc4e1c02ec49eeb93456618add
# Public models at pinned revisions.
fetch https://huggingface.co/ggerganov/whisper.cpp/resolve/5359861c739e955e79d9a303bcbc70fb988958b1/ggml-base.bin \
      ggml-base.bin 60ed5bc3dd14eea856493d334349b405782ddcaf0028d4b5df4088345fba2efe
fetch https://huggingface.co/Qwen/Qwen3-0.6B-GGUF/resolve/23749fefcc72300e3a2ad315e1317431b06b590a/Qwen3-0.6B-Q8_0.gguf \
      Qwen3-0.6B-Q8_0.gguf 9465e63a22add5354d9bb4b99e90117043c7124007664907259bd16d043bb031
# Voice: Piper en_US lessac-medium packaged for sherpa-onnx.
if [ -f "$dir/voice/en_US-lessac-medium.onnx" ] && \
   echo "4ba07d8549906668ee855fd9abf9faf66c5db74742712ff026a159f7277fca9f  $dir/voice/en_US-lessac-medium.onnx" | sha256sum -c --status; then
    echo "ok (already there): voice/"
else
    echo "downloading voice/"
    tmp=$(mktemp -d)
    curl -fL --retry 5 https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/vits-piper-en_US-lessac-medium.tar.bz2 | tar -xj -C "$tmp"
    echo "4ba07d8549906668ee855fd9abf9faf66c5db74742712ff026a159f7277fca9f  $tmp/vits-piper-en_US-lessac-medium/en_US-lessac-medium.onnx" | sha256sum -c --status \
        || { echo "checksum mismatch for the voice" >&2; exit 1; }
    rm -rf "$dir/voice"; mv "$tmp/vits-piper-en_US-lessac-medium" "$dir/voice"; rm -rf "$tmp"
    echo "ok: voice/"
fi
echo "All models in $dir:"; ls -la "$dir"
