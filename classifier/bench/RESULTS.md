# LNN vs small transformer: on-device speed/size benchmark

**Device:** Qualcomm dev kit, **SM8750P (Snapdragon 8 Elite), Hexagon v79 HTP**, Android 15.
Not measured on the Snapdragon 695 phone (it's used for USB tethering, so it was left out on purpose).
If wanted later: `ANDROID_SERIAL=25006571 bench/bench_phone.sh && python bench/results.py SM6375`.
**SDK:** QAIRT/SNPE 2.47.0.260601, `snpe-net-run --perf_profile burst --profiling_level basic`.
**Pass line:** < 200 ms per user turn (one inference over all user turns so far).

## What was measured

| id | model | vocab |
|---|---|---|
| L1h64 / L1h128 / L1h192 | bidirectional CfC liquid net (`bench/lnn.py`), hidden 64/128/192, emb 256, unrolled | 8,192 (trimmed) |
| L2h192 | same, hidden 192 | 50,368 (full Ettin) |
| T1 | Ettin-encoder-17m (ModernBERT arch, 7 layers, d=256) | 50,368 |
| T1t | T1 with the trimmed 8,192 vocab (extra, for a fair size comparison) | 8,192 |
| T2 | Ettin-encoder-32m (10 layers, d=384) | 50,368 |

All models: int32 `input_ids` + `attention_mask` [1, L], three heads (type 30, per-token
relevance 2, per-token fields 60). **Random weights**; Ettin built from its `config.json`.
Speed and size don't depend on the weight values. Inputs: 50 real conversations from
`data/*.jsonl` tokenized with the Ettin tokenizer (also used as int8 calibration data).
The device runs used **no CPU fallback** (`--enable_cpu_fallback` is off by default), so every
successful HTP/GPU run executed every layer on that runtime.

## Results (SM8750P, average of 50 inferences)

| model | L | params | dlc fp32 MB | dlc int8 MB | convert s | quant s | CPU ms | GPU ms | HTP ms | HTP init ms |
|---|---|---|---|---|---|---|---|---|---|---|
| L1h64 | 128 | 2.18M | 11.2 | 5.1 | 72.1 | 7.9 | 334.4 | 19.3 | 0.7 | 1840 |
| L1h64 | 256 | 2.18M | 14.1 | 8.1 | 145.4 | 32.0 | 1446.2 | 37.2 | 1.2 | 6000 |
| L1h128 | 128 | 2.35M | 11.8 | 5.3 | 72.7 | 8.2 | 346.9 | 21.5 | 0.9 | 1829 |
| L1h128 | 256 | 2.35M | 14.7 | 8.2 | 146.2 | 32.4 | 1450.8 | 41.6 | 1.5 | 5934 |
| L1h192 | 128 | 2.6M | 12.8 | 5.5 | 72.3 | 8.7 | 353.3 | 22.0 | 0.9 | 1680 |
| L1h192 | 256 | 2.6M | 15.7 | 8.5 | 145.6 | 34.0 | 1466.8 | 43.8 | 1.7 | 5915 |
| L2h192 | 128 | 13.4M | 54.0 | 15.8 | 75.8 | 8.8 | 353.3 | 22.0 | 0.9 | 1685 |
| L2h192 | 256 | 13.4M | 56.9 | 18.8 | 144.9 | 34.3 | 1466.7 | 43.5 | 1.8 | 5906 |
| T1 | 128 | 16.82M | 64.4 | 16.3 | 9.3 | 1.4 | 8.3 | 5.7 | 0.7 | 481 |
| T1 | 256 | 16.82M | 64.6 | 16.4 | 9.2 | 2.4 | 14.9 | 8.2 | 1.4 | 516 |
| **T1t** | 128 | 6.02M | 23.3 | **6.0** | 8.6 | 1.2 | **8.5** | **5.6** | **0.7** | 321 |
| **T1t** | 256 | 6.02M | 23.4 | **6.1** | 8.6 | 2.4 | 14.7 | 7.9 | 1.4 | 346 |
| T2 | 128 | 31.92M | 122.1 | 30.8 | 12.6 | 2.8 | 20.6 | 9.4 | 1.3 | 780 |
| T2 | 256 | 31.92M | 122.2 | 30.8 | 12.5 | 5.4 | 40.1 | 13.4 | 2.5 | 837 |

Conversion: **14/14 passed** ONNX export (onnxruntime vs PyTorch max diff ≤ 1.8e-6),
qairt-converter, snpe-dlc-quant and all 42 device runs. Raw data: `bench/results.json`,
logs in `bench/out/<model>_L<len>/`.

## Verdict against 200 ms

- **HTP: everything passes by ~100×** (0.7–2.5 ms). Speed can't decide between them here.
- **GPU: everything passes** (5.6–44 ms); Ettin is 2–5× faster than the LNNs.
- **CPU: the LNNs fail** (334–353 ms at L128, ~1.45 s at L256). Ettin passes (8–40 ms).
  The unrolled LNN is 5.6k–11k tiny ops, and SNPE's CPU runtime pays overhead per op.
  The transformer is 325–457 large, parallel ops.
- **Size:** with the same trimmed vocab the two are equal: **T1t 6.0 MB vs L1h192 5.5 MB int8**.
  The embedding table is most of every model; the LNN's hidden size barely matters (5.1–5.5 MB).
  The LNN's DLC also grows with L (5 → 8 MB) because of unrolling; Ettin's does not.
- **Init (first load):** LNN 1.7–6.0 s on HTP vs Ettin 0.3–0.8 s.
- Real conversations in `data/` are 43 tokens median, 71 max, so **L=128 is enough**.

**Recommendation: Ettin-encoder-17m with a trimmed vocab (T1t).** It matches the LNN on size,
is as fast or faster on every runtime, is the only option that passes 200 ms on CPU,
loads 5–18× faster, and starts from pretrained language knowledge. The LNN would have to
learn English from ~1,300 conversations. The LNN gives no speed or size advantage here.

## Caveats

- Times are SNPE execute time only (no tokenization or app overhead), burst perf mode.
- Random weights: int8 **accuracy** after quantization is untested; check it after training.
- An LNN written as a plain C/Kotlin loop (not through SNPE) could be much faster on CPU than
  the numbers above. Not measured.
- HTP init could drop further with an offline-prepared cache (`snpe-dlc-graph-prepare`). Not measured.
- Trimming Ettin's vocab means slicing the pretrained embedding rows to the tokens the form
  pack uses (plus common English). Doable, but it needs a fallback for unseen tokens (UNK).

## Reproduce

```sh
source qairt-env.sh
python bench/export.py                 # ONNX + onnxruntime check   -> bench/out/*/model.onnx
python bench/make_inputs.py            # tokenized inputs + calib.txt
bench/convert.sh                       # .dlc + int8 .dlc
ANDROID_SERIAL=<serial> bench/bench_phone.sh
python bench/results.py SM8750P        # table
```
