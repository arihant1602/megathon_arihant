#!/usr/bin/env bash
# Linux/macOS equivalent of prepare_native.ps1: fetch pinned whisper.cpp and llama.cpp into third_party/.
set -euo pipefail
workspace="${1:-$(cd "$(dirname "$0")/.." && pwd)}"
vendor="$workspace/third_party"
mkdir -p "$vendor"
while read -r name url commit; do
    target="$vendor/$name"
    if [ ! -d "$target/.git" ]; then
        mkdir -p "$target"
        git -C "$target" init --quiet
        git -C "$target" remote add origin "$url"
    fi
    if [ -n "$(git -C "$target" status --porcelain)" ]; then
        echo "Local native-source changes found in $target. Preserve them before preparing sources." >&2
        exit 1
    fi
    if [ "$(git -C "$target" rev-parse --verify --quiet HEAD || true)" != "$commit" ]; then
        git -C "$target" fetch --depth 1 origin "$commit"
        git -C "$target" checkout --quiet --detach "$commit"
    fi
    echo "$name: $commit"
done <<'SOURCES'
whisper.cpp https://github.com/ggml-org/whisper.cpp.git d1be6fde11ac6e0407606b4e42fe72d34add8037
llama.cpp https://github.com/ggml-org/llama.cpp.git aa94f2086147cd4bfbcff818ce998dffce5fcb39
SOURCES
