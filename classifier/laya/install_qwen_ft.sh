#!/usr/bin/env bash
# Register the fine-tuned extractor (qwen3-incident-q8_0.gguf from laya/qwen_finetune.ipynb) in Ollama as
# "incident-qwen3", reusing the chat template/parameters of the base Qwen3:0.6B so prompts match training.
# Usage: laya/install_qwen_ft.sh path/to/qwen3-incident-q8_0.gguf      then: python laya/assistant.py --model incident-qwen3
set -euo pipefail
gguf=$(realpath "${1:?path to qwen3-incident-q8_0.gguf}")
mf=$(mktemp)
ollama show Qwen3:0.6B --modelfile | sed "s#^FROM .*#FROM $gguf#" | sed '/^PARAMETER temperature/d' > "$mf"
echo "PARAMETER temperature 0" >> "$mf"
ollama create incident-qwen3 -f "$mf"
rm -f "$mf"
ollama list | grep incident-qwen3
