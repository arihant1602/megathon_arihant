#!/usr/bin/env bash
# Fetch the sherpa-onnx Android runtime (ONNX Runtime statically linked, so it cannot clash with
# onnxruntime-android) into third_party/, and optionally the voice (Piper en_US lessac-medium) into MODEL_DIR/voice.
# usage: scripts/fetch_sherpa_onnx.sh [MODEL_DIR]
# Then:  tar -C MODEL_DIR -cf /tmp/voice.tar voice && adb push /tmp/voice.tar /data/local/tmp/ && adb shell "chmod 644
#        /data/local/tmp/voice.tar && run-as org.seedhecode.incidentassistant sh -c 'cd files/models && tar xf /data/local/tmp/voice.tar'"
set -euo pipefail
version=v1.13.8
root="$(cd "$(dirname "$0")/.." && pwd)"
tmp=$(mktemp -d)
trap 'rm -rf "$tmp"' EXIT
curl -fsSL "https://github.com/k2-fsa/sherpa-onnx/releases/download/$version/sherpa-onnx-$version-android-static-link-onnxruntime.tar.bz2" | tar -xj -C "$tmp"
mkdir -p "$root/third_party/sherpa-onnx/jniLibs/arm64-v8a"
cp "$tmp/jniLibs/arm64-v8a/libsherpa-onnx-jni.so" "$root/third_party/sherpa-onnx/jniLibs/arm64-v8a/"
echo "sherpa-onnx $version: third_party/sherpa-onnx/jniLibs"
if [ -n "${1:-}" ]; then
    mkdir -p "$1"
    curl -fsSL "https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/vits-piper-en_US-lessac-medium.tar.bz2" | tar -xj -C "$1"
    rm -rf "$1/voice" && mv "$1/vits-piper-en_US-lessac-medium" "$1/voice"
    echo "Voice: $1/voice"
fi
