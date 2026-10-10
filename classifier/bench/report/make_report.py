"""Build the model-comparison report from bench/results.json -> bench/report/report.html (+ PDF via Chrome).
Usage: python bench/report/make_report.py"""
import io, json, os, subprocess
import matplotlib
matplotlib.use("Agg")
import matplotlib.pyplot as plt

HERE = os.path.dirname(os.path.abspath(__file__))
BENCH = os.path.dirname(HERE)
SOC = "SM8750P"
rows = json.load(open(os.path.join(BENCH, "results.json")))
for r in rows:
    ex = json.load(open(os.path.join(BENCH, "out", f"{r['model']}_L{r['L']}", "export.json")))
    r["onnx_nodes"] = ex["onnx_nodes"]

ORDER = ["L1h64", "L1h128", "L1h192", "L2h192", "T1t", "T1", "T2"]
LABEL = {"L1h64": "LNN h64", "L1h128": "LNN h128", "L1h192": "LNN h192", "L2h192": "LNN h192 full-vocab",
         "T1t": "Ettin-17m trimmed", "T1": "Ettin-17m", "T2": "Ettin-32m"}
DESC = {"L1h64": "Bidirectional CfC liquid net, hidden 64, 8k vocab",
        "L1h128": "Bidirectional CfC liquid net, hidden 128, 8k vocab",
        "L1h192": "Bidirectional CfC liquid net, hidden 192, 8k vocab",
        "L2h192": "Bidirectional CfC liquid net, hidden 192, full 50k Ettin vocab",
        "T1t": "Ettin-encoder-17m (7 layers, d=256), vocab trimmed to 8k",
        "T1": "Ettin-encoder-17m (7 layers, d=256), full 50k vocab",
        "T2": "Ettin-encoder-32m (10 layers, d=384), full 50k vocab"}
LNN_C, TF_C, ACC = "#9a6b3f", "#2f5d8a", "#c0392b"
color = lambda m: LNN_C if m.startswith("L") else TF_C
get = {(r["model"], r["L"]): r for r in rows}


def ms(m, L, rt):
    return get[(m, L)][SOC][rt]["infer_ms"]


def svg(fig):
    buf = io.StringIO()
    fig.savefig(buf, format="svg", bbox_inches="tight")
    plt.close(fig)
    s = buf.getvalue()
    return s[s.index("<svg"):]


plt.rcParams.update({"font.family": "sans-serif", "font.size": 9, "axes.spines.top": False,
                     "axes.spines.right": False, "axes.edgecolor": "#888", "axes.titleweight": "bold",
                     "axes.titlesize": 10, "svg.fonttype": "none"})

# 1. latency per runtime at L=128, log scale, 200 ms line
fig, axes = plt.subplots(1, 3, figsize=(9.2, 3.0), sharey=True)
for ax, (rt, name) in zip(axes, [("cpu", "CPU"), ("gpu", "GPU (Adreno)"), ("dsp", "NPU (Hexagon HTP, int8)")]):
    vals = [ms(m, 128, rt) for m in ORDER]
    ax.barh([LABEL[m] for m in ORDER], vals, color=[color(m) for m in ORDER])
    ax.set_xscale("log"); ax.set_xlim(0.3, 3000)
    ax.axvline(200, color=ACC, ls="--", lw=1.2)
    for i, v in enumerate(vals):
        ax.text(v * 1.15, i, f"{v:.1f}", va="center", fontsize=8)
    ax.set_title(name); ax.set_xlabel("ms per inference (log)")
    if ax is axes[0]:
        ax.invert_yaxis()
chart_latency = svg(fig)

# 2. CPU scaling 128 -> 256
fig, ax = plt.subplots(figsize=(4.4, 2.9))
for m in ORDER:
    ax.plot([128, 256], [ms(m, 128, "cpu"), ms(m, 256, "cpu")], marker="o", color=color(m), lw=1.5,
            alpha=0.55 if m in ("L1h64", "L1h128", "L2h192") else 1)
for m in ("L1h192", "T1t", "T2"):
    ax.text(262, ms(m, 256, "cpu"), LABEL[m], va="center", fontsize=8, color=color(m))
ax.axhline(200, color=ACC, ls="--", lw=1.2); ax.text(130, 230, "200 ms pass line", color=ACC, fontsize=8)
ax.set_yscale("log"); ax.set_xticks([128, 256]); ax.set_xlim(110, 340)
ax.set_xlabel("sequence length (tokens)"); ax.set_ylabel("CPU ms (log)"); ax.set_title("CPU latency vs input length")
chart_cpu = svg(fig)

# 3. size
fig, ax = plt.subplots(figsize=(4.4, 2.9))
vals = [get[(m, 128)]["dlc_q_MB"] for m in ORDER]
ax.barh([LABEL[m] for m in ORDER], vals, color=[color(m) for m in ORDER])
for i, v in enumerate(vals):
    ax.text(v + 0.4, i, f"{v} MB", va="center", fontsize=8)
ax.invert_yaxis(); ax.set_xlim(0, 37); ax.set_xlabel("int8 .dlc size, MB (L=128)"); ax.set_title("On-device model size")
chart_size = svg(fig)


def row_html(r):
    d = r[SOC]
    f = lambda rt: f"{d[rt]['infer_ms']:.1f}"
    cpu_cls = ' class="bad"' if d["cpu"]["infer_ms"] >= 200 else ""
    best = ' class="best"' if r["model"] == "T1t" else ""
    return (f"<tr{best}><td>{LABEL[r['model']]}</td><td>{r['L']}</td><td>{r['params_M']:.2f}M</td>"
            f"<td>{r['onnx_nodes']:,}</td><td>{r['dlc_MB']}</td><td>{r['dlc_q_MB']}</td>"
            f"<td>{r['convert'][1]:.0f}</td><td{cpu_cls}>{f('cpu')}</td><td>{f('gpu')}</td>"
            f"<td>{f('dsp')}</td><td>{d['dsp']['init_ms'] / 1000:.1f}</td></tr>")


table = "\n".join(row_html(get[(m, L)]) for m in ORDER for L in (128, 256))
models = "\n".join(f"<tr><td>{LABEL[m]}</td><td><code>{m}</code></td><td>{DESC[m]}</td></tr>" for m in ORDER)

html = f"""<!doctype html><html lang="en"><head><meta charset="utf-8">
<title>Classifier Model Comparison</title>
<style>
:root {{ --ink:#1d2329; --muted:#5d6670; --line:#d9dde2; --lnn:{LNN_C}; --tf:{TF_C}; --acc:{ACC}; --bg:#ffffff; --soft:#f4f6f8; }}
@page {{ size: A4; margin: 16mm 15mm 16mm 15mm; }}
* {{ box-sizing: border-box; }}
body {{ background: var(--bg); color: var(--ink); font: 10pt/1.5 "Noto Sans", "DejaVu Sans", Arial, sans-serif; margin: 0; }}
main {{ max-width: 180mm; margin: 0 auto; }}
h1 {{ font-size: 21pt; margin: 0 0 2mm; letter-spacing: -0.3px; }}
h2 {{ font-size: 13pt; margin: 8mm 0 2.5mm; padding-bottom: 1.5mm; border-bottom: 1.5px solid var(--ink); }}
h3 {{ font-size: 10.5pt; margin: 4mm 0 1.5mm; }}
p {{ margin: 0 0 2.5mm; }}
.sub {{ color: var(--muted); margin-bottom: 6mm; }}
.verdict {{ background: var(--soft); border-left: 4px solid var(--tf); padding: 3.5mm 4.5mm; margin: 0 0 4mm; }}
.verdict b.big {{ font-size: 12pt; display: block; margin-bottom: 1.5mm; }}
.kpis {{ display: grid; grid-template-columns: repeat(4, 1fr); gap: 3mm; margin: 4mm 0; }}
.kpi {{ border: 1px solid var(--line); border-radius: 2mm; padding: 2.5mm 3mm; }}
.kpi .v {{ font-size: 15pt; font-weight: 700; }}
.kpi .l {{ font-size: 8pt; color: var(--muted); line-height: 1.3; }}
table {{ border-collapse: collapse; width: 100%; font-size: 8.6pt; margin: 2mm 0 3mm; }}
th, td {{ padding: 1.4mm 1.8mm; border-bottom: 1px solid var(--line); text-align: right; }}
th {{ background: var(--soft); font-weight: 600; vertical-align: bottom; }}
td:first-child, th:first-child, .left td, .left th {{ text-align: left; }}
tr.best td {{ background: #e8f0f8; font-weight: 600; }}
td.bad {{ color: var(--acc); font-weight: 700; }}
.legend span {{ display: inline-block; width: 9px; height: 9px; margin: 0 1.5mm 0 4mm; vertical-align: middle; }}
.charts2 {{ display: grid; grid-template-columns: 1fr 1fr; gap: 4mm; }}
figure {{ margin: 2mm 0; break-inside: avoid; }}
figure svg {{ width: 100%; height: auto; }}
figcaption {{ font-size: 8.5pt; color: var(--muted); }}
ul {{ margin: 0 0 3mm; padding-left: 5mm; }} li {{ margin-bottom: 1.2mm; }}
code {{ font-family: "JetBrainsMono NF", "Adwaita Mono", monospace; font-size: 8.5pt; background: var(--soft); padding: 0 1mm; }}
pre {{ background: var(--soft); padding: 3mm; font-size: 8pt; white-space: pre-wrap; }}
.pb {{ break-before: page; }}
.foot {{ color: var(--muted); font-size: 8pt; margin-top: 6mm; border-top: 1px solid var(--line); padding-top: 2mm; }}
@media (max-width: 700px) {{ main {{ padding: 0 16px; }} .kpis, .charts2 {{ grid-template-columns: 1fr 1fr; }} table {{ display:block; overflow-x:auto; }} }}
</style></head><body><main>

<h1>On-device classifier: model comparison</h1>
<div class="sub">Liquid neural networks (CfC) vs small transformers (Ettin) for the incident-report classifier ·
measured on a Qualcomm SM8750P dev kit (Snapdragon 8 Elite) · QAIRT/SNPE 2.47 · 10 Oct 2026</div>

<div class="verdict"><b class="big">Recommendation: Ettin-encoder-17m with a trimmed 8k vocabulary</b>
It is the same size as the best LNN (6.0 MB vs 5.5 MB int8), equal or faster on every processor,
the only family that passes the 200 ms line on CPU, loads 5–18× faster, and starts with pretrained
English knowledge. The LNN would have to learn the language from ~1,300 conversations. The LNN showed
no speed or size advantage.</div>

<div class="kpis">
<div class="kpi"><div class="v">14 / 14</div><div class="l">model × length builds converted, quantized and ran (42 device runs)</div></div>
<div class="kpi"><div class="v">&lt; 3 ms</div><div class="l">every model on the Hexagon NPU, ~100× under the 200 ms line</div></div>
<div class="kpi"><div class="v">~350 ms</div><div class="l">LNN on CPU at 128 tokens; fails 200 ms (Ettin-17m: 8.5 ms)</div></div>
<div class="kpi"><div class="v">6.0 MB</div><div class="l">Ettin-17m trimmed, int8 — same size as the LNNs (5.1–5.5 MB)</div></div>
</div>

<h2>Models compared</h2>
<table class="left"><thead><tr><th>Name</th><th>ID</th><th>Architecture</th></tr></thead><tbody>
{models}</tbody></table>
<p>All models take the same input (Ettin tokenizer, int32 <code>input_ids</code> + <code>attention_mask</code>
of shape [1, L]) and have the same three heads: incident type (30 classes = 28 types + undetermined + none),
per-token relevance (2) and per-token form fields (60, multi-label). ModernBERT-base was excluded by decision.</p>

<h2 class="pb">Latency on device</h2>
<p class="legend"><span style="background:{LNN_C}"></span>Liquid neural network (CfC)<span style="background:{TF_C}"></span>Transformer (Ettin)
<span style="background:{ACC};height:2px"></span>200 ms pass line</p>
<figure>{chart_latency}<figcaption>Average of 50 real conversations, 128-token input, burst performance mode. Log scale.</figcaption></figure>

<div class="charts2">
<figure>{chart_cpu}<figcaption>The unrolled LNN grows ~4× on CPU when the input doubles; Ettin grows ~2×.</figcaption></figure>
<figure>{chart_size}<figcaption>The word-embedding table is most of every model. Trimming it brings Ettin-17m down to LNN size.</figcaption></figure>
</div>

<h2 class="pb">Full results</h2>
<table><thead><tr><th>Model</th><th>L</th><th>Params</th><th>ONNX ops</th><th>DLC fp32 MB</th><th>DLC int8 MB</th>
<th>Convert s</th><th>CPU ms</th><th>GPU ms</th><th>NPU ms</th><th>NPU load s</th></tr></thead><tbody>
{table}</tbody></table>
<p style="font-size:8.5pt;color:var(--muted)">CPU and GPU run the fp32 model; NPU runs the int8 model (snpe-dlc-quant, calibrated
on the same 50 conversations). Red = fails the 200 ms line. "NPU load" is the first-time init on the Hexagon HTP.
CPU fallback was disabled, so every successful NPU/GPU run executed all layers on that processor.</p>

<h2>Findings</h2>
<h3>Speed</h3>
<ul>
<li><b>NPU:</b> every model takes 0.7–2.5 ms. Speed cannot choose between them on the Hexagon HTP.</li>
<li><b>GPU:</b> all pass (5.6–44 ms); Ettin is 2–5× faster than the LNNs.</li>
<li><b>CPU:</b> the LNNs fail (334–353 ms at 128 tokens, ~1.45 s at 256). Ettin-17m passes at 8–15 ms, Ettin-32m at 21–40 ms.
The LNN has to be unrolled into a fixed graph of 5,600–11,300 tiny sequential ops, and the CPU runtime pays overhead for each.
A transformer is 325–457 large, parallel ops.</li>
<li><b>Hidden size barely matters</b> for the LNN (h64 vs h192: 334 vs 353 ms CPU), so per-op overhead, not maths, dominates.</li>
</ul>
<h3>Size and load time</h3>
<ul>
<li>The embedding table dominates: LNN h192 is 5.5 MB with an 8k vocab and 15.8 MB with the full 50k vocab.</li>
<li>Same 8k vocab: Ettin-17m 6.0 MB vs LNN 5.1–5.5 MB int8 — effectively equal.</li>
<li>The LNN's file grows with input length (5 → 8 MB from 128 → 256 tokens) because of unrolling; Ettin's does not.</li>
<li>First load on the NPU: LNN 1.7–6.0 s, Ettin 0.3–0.8 s.</li>
</ul>
<h3>Input length</h3>
<p>The existing labelled conversations are 43 tokens (median) and 71 tokens at most, so a 128-token input
covers them all. 256 only matters if the new multi-turn data runs much longer.</p>

<h2>Method</h2>
<ul>
<li><b>Device:</b> Qualcomm dev kit "Sun", SoC SM8750P (Snapdragon 8 Elite), Hexagon v79 HTP, Android 15. The Snapdragon 695 test phone was not benchmarked.</li>
<li><b>Pipeline:</b> PyTorch → ONNX (opset 17, static shapes, onnxsim; onnxruntime matched PyTorch within 1.8e-6) →
<code>qairt-converter</code> → <code>snpe-dlc-quant</code> (int8) → <code>snpe-net-run</code> with
<code>--perf_profile burst --profiling_level basic</code> → <code>snpe-diagview</code>.</li>
<li><b>Inputs:</b> 50 real conversations from <code>data/*.jsonl</code>, tokenized with the Ettin tokenizer, padded to L.</li>
<li><b>Weights are random.</b> Ettin was built from its published config. Speed and size do not depend on weight values.</li>
</ul>

<h2>Limits</h2>
<ul>
<li>Times are model execution only: no tokenization or app overhead.</li>
<li>Accuracy after int8 quantization is untested; measure it once the model is trained.</li>
<li>An LNN written as a plain loop in app code (outside the Qualcomm SDK) might be much faster on CPU. Not measured.</li>
<li>NPU load time could drop with an offline-prepared graph cache (<code>snpe-dlc-graph-prepare</code>). Not measured.</li>
<li>Trimming Ettin's vocabulary means keeping only the pretrained embedding rows for tokens the form pack uses
(plus common English), with an unknown-token fallback.</li>
</ul>

<div class="foot">Source: <code>bench/</code> in the megathon repo — <code>RESULTS.md</code>, <code>results.json</code>,
per-run logs in <code>bench/out/&lt;model&gt;_L&lt;len&gt;/</code>. Regenerate with <code>python bench/report/make_report.py</code>.</div>
</main></body></html>"""

out_html = os.path.join(HERE, "report.html")
open(out_html, "w").write(html)
out_pdf = os.path.join(HERE, "model_comparison.pdf")
subprocess.run(["google-chrome-stable", "--headless=new", "--disable-gpu", "--no-pdf-header-footer",
                f"--print-to-pdf={out_pdf}", "file://" + out_html], check=True,
               stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
print(out_html, out_pdf, os.path.getsize(out_pdf), "bytes")
