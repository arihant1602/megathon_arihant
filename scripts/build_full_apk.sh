#!/usr/bin/env bash
# Build the all-in-one release APK: app + every model, unpacked by the app on first launch.
#
# usage: scripts/build_full_apk.sh MODELS_DIR OUT.apk [WORK_DIR]
#   MODELS_DIR  the same layout as the app's files/models:
#                 laya_w8a16_htp.dlc
#                 ggml-base.bin
#                 whisper_base_encoder_fp16_htp.dlc
#                 whisper_base_encoder_10s_fp16_htp.dlc
#                 Qwen3-0.6B-Q8_0.gguf
#                 voice/  (en_US-lessac-medium.onnx, tokens.txt, espeak-ng-data/ ...)
#   WORK_DIR    scratch space for staging + the Gradle build output (~5 GB free; a RAM disk works). Default: mktemp.
#               The build itself also needs ~8 GB of RAM (6 GB Gradle heap).
# Needs the same setup as a normal build (see README: JDK 17, Android SDK/NDK, -PsnpeSdk, third_party sources).
set -euo pipefail
models=$(cd "$1" && pwd); out=$2
work=${3:-$(mktemp -d)}
root=$(cd "$(dirname "$0")/.." && pwd)
required=(laya_w8a16_htp.dlc ggml-base.bin whisper_base_encoder_fp16_htp.dlc whisper_base_encoder_10s_fp16_htp.dlc
          Qwen3-0.6B-Q8_0.gguf voice/tokens.txt voice/espeak-ng-data)
for f in "${required[@]}"; do [ -e "$models/$f" ] || { echo "missing $models/$f" >&2; exit 1; }; done
ls "$models"/voice/*.onnx >/dev/null || { echo "missing voice/*.onnx" >&2; exit 1; }

stage="$work/assets"; rm -rf "$stage"; mkdir -p "$stage/models"
cp -rl "$models"/. "$stage/models/" 2>/dev/null || cp -r "$models"/. "$stage/models/"   # hard links when possible
rm -f "$stage/models/.bundle" "$stage/models/manifest.json"
# Manifest the app's ModelInstaller reads: every file with its size (the id of this model bundle).
python3 - "$stage/models" <<'PY'
import json, os, sys
root = sys.argv[1]
files = sorted(os.path.relpath(os.path.join(d, f), root) for d, _, fs in os.walk(root) for f in fs)
json.dump({"files": [{"path": p, "size": os.path.getsize(os.path.join(root, p))} for p in files]},
          open(os.path.join(root, "manifest.json"), "w"), indent=1)
print(len(files), "model files,", sum(os.path.getsize(os.path.join(root, p)) for p in files) // 1_000_000, "MB")
PY

cd "$root"
# Compressing ~2 GB of assets needs a bigger heap than the default; few workers keep peak memory down.
bash ./gradlew -q --max-workers=2 -Dorg.gradle.jvmargs="-Xmx6g -Dfile.encoding=UTF-8" \
    :app:assembleRelease -PmodelAssets="$stage" -PappBuildDir="$work/app-build"
cp "$work/app-build/outputs/apk/release/app-release.apk" "$out"
ls -la "$out"
