# Source this from bash or zsh:  source ~/Projects/megathon/qairt-env.sh
# Replaces the SDK's bin/envsetup.sh, which breaks under zsh (relies on BASH_SOURCE).

if [ -n "${ZSH_VERSION:-}" ]; then _here="${(%):-%x}"; else _here="${BASH_SOURCE[0]}"; fi
MEGATHON_ROOT="$(cd "$(dirname "$_here")" && pwd)"
unset _here

export QAIRT_SDK_ROOT="$MEGATHON_ROOT/qairt/2.47.0.260601"
export QNN_SDK_ROOT="$QAIRT_SDK_ROOT"
export SNPE_ROOT="$QAIRT_SDK_ROOT"

source "$MEGATHON_ROOT/.venv-qairt/bin/activate"

export PYTHONPATH="$QAIRT_SDK_ROOT/lib/python:$QAIRT_SDK_ROOT/benchmarks/QNN${PYTHONPATH:+:$PYTHONPATH}"
export PATH="$QAIRT_SDK_ROOT/bin/x86_64-linux-clang:$PATH"
# host-deps holds LLVM libunwind.so.1 (from Ubuntu 22.04 libunwind-14); Arch only ships GNU libunwind.so.8
export LD_LIBRARY_PATH="$QAIRT_SDK_ROOT/lib/x86_64-linux-clang:$QAIRT_SDK_ROOT/lib/host-deps${LD_LIBRARY_PATH:+:$LD_LIBRARY_PATH}"

# Android SDK/NDK (installed via Android Studio SDK Manager)
export ANDROID_HOME="${ANDROID_HOME:-$HOME/Android/Sdk}"
if [ -d "$ANDROID_HOME/ndk" ]; then
  export ANDROID_NDK_ROOT="$(ls -d "$ANDROID_HOME"/ndk/* 2>/dev/null | sort -V | tail -1)"
  export PATH="$ANDROID_NDK_ROOT:$PATH"
fi
export PATH="$ANDROID_HOME/platform-tools:$PATH"

echo "[qairt] SDK=$QAIRT_SDK_ROOT  NDK=${ANDROID_NDK_ROOT:-<not installed>}"
