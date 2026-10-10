Start the approved LNN vs small-transformer speed/size benchmark on my phone. Context:

- Project: ~/Projects/megathon. QAIRT SDK 2.47 is set up there; always `source ~/Projects/megathon/qairt-env.sh` (never the SDK's envsetup.sh). Python venv is .venv-qairt (py3.10, torch 2.4.1 CPU, onnx 1.16.1, onnxruntime 1.18.0). Install anything new with `uv pip install --no-cache --python .venv-qairt/bin/python ...`. Disk is ~5 GB free, so watch it.
- Phone: OnePlus CPH2467, Snapdragon 695 (SM6375), Hexagon v66 DSP, connected over adb. `run_on_phone.sh` already shows how to push libs (lib/aarch64-android + lib/hexagon-v66/unsigned), run snpe-net-run on cpu/gpu/dsp (DSP needs an snpe-dlc-quant'd model) and read timings with snpe-diagview.
- Goal: measured size + speed (CPU/GPU/DSP) and conversion pass/fail. Random weights; accuracy is out of scope. Pass line: under 200 ms per user turn on the phone.

Models (all share Ettin's tokenizer, int32 inputs [1, L] + attention mask, three heads: incident type 30 classes = 28 types + undetermined + none, per-token relevance 2, per-token fields multi-label ~60):
- L1: hand-written bidirectional CfC LNN (closed-form cell: Linear + sigmoid + tanh, no ncps), hidden 192, trimmed vocab ~8k x 256-dim embedding
- L2: same LNN with Ettin's full ~50k vocab embedding
- T1: jhu-clsp/ettin-encoder-17m
- T2: jhu-clsp/ettin-encoder-32m
- NOT ModernBERT-base.
Sequence lengths: 128 and 256.

Steps:
1. Install a transformers version with ModernBERT support that works with torch 2.4.1; download Ettin-17m/32m; create bench/.
2. bench/lnn.py: CfC model with fixed length, unrolled time loop.
3. Export all to ONNX (Ettin with attn_implementation="eager", static shapes, int32 ids); check onnxruntime matches PyTorch on the laptop.
4. qairt-converter -> .dlc; snpe-dlc-quant -> 8-bit .dlc, calibrating with ~50 real tokenized sentences from data/*.jsonl. Record .dlc sizes and conversion time.
5. bench/bench_phone.sh: each model x length x {cpu, gpu, dsp}, 50 inputs each; collect average inference time, init time, and per-layer runtime placement (flag DSP->CPU fallback).
6. bench/RESULTS.md: one table (params, dlc size fp32/int8, CPU/GPU/DSP ms at 128/256, fallback notes) + short verdict against the 200 ms line.

Fallbacks: if the unrolled LNN fails or is too slow to convert, try 128 first, then a single-cell DLC looped from the app or chunked state-carrying export. If Ettin export breaks on torch 2.4.1, use ettin-encoder-17m's published onnx/model.onnx or patch attention. Report DSP fallbacks honestly; label results "Snapdragon 695", not QIDK.

Don't touch datagen/ (Antigravity is writing data there). Work step by step, tell me briefly after each step, and stop to ask if something fails twice.
