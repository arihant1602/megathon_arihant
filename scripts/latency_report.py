#!/usr/bin/env python3
"""Build the conversation-latency report (HTML -> PDF via headless Chromium) from VoiceBench results.

usage: scripts/latency_report.py OUT.pdf RUN1.json [RUN2.json ...] [--note "text for the accuracy section"]
The JSON files come from: VOICE=1 ANDROID_SERIAL=<kit> scripts/probe_kit.sh SCRIPT.txt > RUN.json
"""
import html, io, json, os, statistics, subprocess, sys, tempfile, datetime

import matplotlib
matplotlib.use("Agg")
import matplotlib.pyplot as plt

NAVY, SLATE, CRIMSON, OUTLINE, TINT = "#06103C", "#434753", "#BA1A1A", "#D1D5E4", "#E8ECFB"
STAGE_COLORS = ["#06103C", "#3B4F9A", "#7C8FD1", "#B6C2EC", "#5A8F6E", "#C9A227"]


def load(paths):
    turns, meta = [], {}
    for p in paths:
        d = json.load(open(p))
        meta.setdefault("device", d.get("device"))
        meta.setdefault("engine_load_ms", d.get("engine_load_ms"))
        meta.setdefault("load_ms", d.get("load_ms"))
        for t in d.get("turns", []):
            t["run"] = os.path.basename(p)
            turns.append(t)
    return turns, meta


def pct(xs, p):
    xs = sorted(xs)
    if not xs:
        return float("nan")
    k = (len(xs) - 1) * p
    f = int(k)
    c = min(f + 1, len(xs) - 1)
    return xs[f] + (xs[c] - xs[f]) * (k - f)


def llm_ms(t, kind):
    return sum(x["ms"] for x in t.get("llm", []) if x["kind"] == kind)


def svg(fig):
    buf = io.StringIO()
    fig.savefig(buf, format="svg", bbox_inches="tight")
    plt.close(fig)
    return buf.getvalue()[buf.getvalue().index("<svg"):]


def style():
    plt.rcParams.update({"font.family": "DejaVu Sans", "font.size": 9, "axes.edgecolor": OUTLINE, "axes.labelcolor": SLATE,
                         "xtick.color": SLATE, "ytick.color": SLATE, "axes.spines.top": False, "axes.spines.right": False})


def stages(t):
    """Wall-clock decomposition of one turn, from the end of the user's speech to the reply being audible."""
    endpoint = t["endpoint_ms"]
    asr = t["asr_ms"] or 0
    step = t["assistant_step_ms"] or 0
    text = t["text_ready_ms"]
    other = max(0.0, text - asr - step)                     # queueing, preview transcription still running, callbacks
    audible = t["audible_after_text_ms"] or 0
    tts = min(t["tts_first_audio_ms"], audible)
    out = max(0.0, audible - tts)                            # AudioTrack start -> first frame played
    return [("Pause detection", endpoint), ("Speech recognition (Whisper)", asr + other),
            ("Understanding + reply (Laya, policy, Qwen)", step), ("Voice synthesis to first audio (Piper)", tts),
            ("Audio output start", out)]


def main(out_pdf, paths):
    style()
    turns, meta = load(paths)
    ok = [t for t in turns if "error" not in t and t.get("speech_end_to_reply_audible_ms")]
    bad = [t for t in turns if t not in ok]
    total = [t["speech_end_to_reply_audible_ms"] for t in ok]
    after = [t["turn_detected_to_reply_audible_ms"] for t in ok]
    first = [t for t in ok if t["turn"] == 1]
    later = [t for t in ok if t["turn"] > 1]

    def row(name, xs, note=""):
        xs = [x for x in xs if x is not None]
        return (f"<tr><td>{name}</td><td>{len(xs)}</td><td>{pct(xs, .5):.0f}</td><td>{pct(xs, .9):.0f}</td>"
                f"<td>{min(xs):.0f}</td><td>{max(xs):.0f}</td><td class=note>{note}</td></tr>") if xs else ""

    stage_rows = "".join([
        row("Pause detection (end of speech → turn detected)", [t["endpoint_ms"] for t in ok], "0.9 s pause rule, minus the fade of the last word"),
        row("Speech recognition: Whisper base, encoder on NPU", [t["asr_ms"] for t in ok], "10 s window graph for short turns"),
        row("Incident classifier: fine-tuned Laya W8A16 on NPU", [t["classifier_ms"] for t in ok], "one inference over all user sentences"),
        row("Qwen3-0.6B value extraction (CPU)", [llm_ms(t, "extract") for t in ok if llm_ms(t, "extract")], "only when fields are wanted"),
        row("Qwen3-0.6B question writing (CPU)", [llm_ms(t, "question") for t in ok if llm_ms(t, "question")], "ask/detail turns"),
        row("Whole assistant step (classifier + policy + Qwen)", [t["assistant_step_ms"] for t in ok]),
        row("Turn detected → answer text ready", [t["text_ready_ms"] for t in ok]),
        row("Piper TTS: text → first audio synthesized", [t["tts_first_audio_ms"] for t in ok], "sentence streaming, 1.2× speed"),
        row("Text → reply audible (TTS + audio output)", [t["audible_after_text_ms"] for t in ok], "first frame played (AudioTrack marker)"),
    ])

    # Chart 1: median stage breakdown, first turn vs later turns vs all
    groups = [("All turns", ok), ("First turn of a report", first), ("Follow-up turns", later)]
    fig, ax = plt.subplots(figsize=(7.2, 2.3))
    names = [n for n, _ in stages(ok[0])]
    for gi, (label, ts) in enumerate(groups):
        if not ts:
            continue
        left = 0
        for si, name in enumerate(names):
            v = statistics.median(dict(stages(t))[name] for t in ts)
            ax.barh(gi, v, left=left, color=STAGE_COLORS[si], height=0.55, label=name if gi == 0 else None)
            if v > 160:
                ax.text(left + v / 2, gi, f"{v:.0f}", ha="center", va="center", color="white" if si < 2 or si == 4 else NAVY, fontsize=7.5)
            left += v
        ax.text(left + 40, gi, f"{left:.0f} ms", va="center", color=NAVY, fontsize=8.5, fontweight="bold")
    ax.set_yticks(range(len(groups)), [g[0] for g in groups])
    ax.invert_yaxis()
    ax.set_xlabel("milliseconds after the user stops speaking (medians)")
    ax.legend(loc="upper center", bbox_to_anchor=(0.5, -0.32), ncol=3, frameon=False, fontsize=7.5)
    chart_breakdown = svg(fig)

    # Chart 2: distribution of end-to-end latency per turn
    fig, ax = plt.subplots(figsize=(7.2, 2.1))
    xs = sorted(total)
    ax.step(xs, [(i + 1) / len(xs) for i in range(len(xs))], where="post", color=NAVY, lw=1.6, label="speech end → reply audible")
    xs2 = sorted(after)
    ax.step(xs2, [(i + 1) / len(xs2) for i in range(len(xs2))], where="post", color="#7C8FD1", lw=1.6, label="turn detected → reply audible")
    for p in (0.5, 0.9):
        ax.axhline(p, color=OUTLINE, lw=0.8, ls="--")
    ax.set_xlabel("milliseconds")
    ax.set_ylabel("share of turns")
    ax.legend(frameon=False, fontsize=7.5, loc="lower right")
    chart_cdf = svg(fig)

    # Chart 3: per turn timeline (stacked) in conversation order for run 1
    run1 = [t for t in ok if t["run"] == ok[0]["run"]]
    fig, ax = plt.subplots(figsize=(7.2, 2.6))
    for i, t in enumerate(run1):
        left = 0
        for si, (name, v) in enumerate(stages(t)):
            ax.bar(i, v, bottom=left, color=STAGE_COLORS[si], width=0.7)
            left += v
    ax.set_xticks(range(len(run1)), [f"C{t['conversation']}.{t['turn']}" for t in run1], rotation=90, fontsize=7)
    ax.set_ylabel("ms")
    ax.set_xlabel("conversation.turn (first pass)")
    chart_turns = svg(fig)

    wers = [t["wer"] for t in ok]
    exact = sum(1 for w in wers if w == 0)
    pss = [t["pss_mb"] for t in ok]
    cpu = [t["cpu_cores_avg"] for t in ok]
    temps = [t["battery_temp_c"] for t in ok]
    heads = [t["thermal_headroom"] for t in ok if t["thermal_headroom"] >= 0]
    loadms = meta.get("engine_load_ms") or {}
    convs = sorted({(t["run"], t["conversation"]) for t in turns})
    filed = sum(1 for t in ok if t.get("done"))
    per_turn = "".join(
        f"<tr><td>{t['conversation']}.{t['turn']}</td><td>{html.escape(t['said'])}</td><td>{html.escape(t['heard'])}</td>"
        f"<td>{t['wer']:.2f}</td><td>{t['endpoint_ms']:.0f}</td><td>{t['asr_ms']}</td><td>{t['classifier_ms']:.0f}</td>"
        f"<td>{llm_ms(t, 'extract') or '–'}</td><td>{llm_ms(t, 'question') or '–'}</td><td>{t['tts_first_audio_ms']}</td>"
        f"<td><b>{t['speech_end_to_reply_audible_ms']:.0f}</b></td></tr>" for t in ok if t["run"] == ok[0]["run"])
    failures_items = "".join(f"<li>Conversation {t['conversation']}, turn {t['turn']} (“{html.escape(t['said'])}”): {html.escape(t['error'])}</li>" for t in bad)
    failures_html = f"<h3>Turns that did not complete</h3><ul>{failures_items}</ul>" if bad else ""
    note_html = f"<p>{html.escape(NOTE)}</p>" if NOTE else ""
    misheard = [t for t in ok if t["wer"] > 0]
    misheard_html = ("<p>Misheard turns:</p><ul>" + "".join(
        f"<li>said “{html.escape(t['said'])}”, heard “{html.escape(t['heard'])}” (WER {t['wer']:.2f})</li>" for t in misheard) +
        "</ul>") if misheard else ""

    commit = subprocess.run(["git", "-C", os.path.dirname(os.path.abspath(__file__)), "rev-parse", "--short", "HEAD"],
                            capture_output=True, text=True).stdout.strip()
    today = datetime.date.today().strftime("%d %B %Y")
    doc = TEMPLATE.format(**locals(), n=len(ok), runs=len(paths), nconv=len(convs),
                          p50=pct(total, .5), p50s=pct(total, .5) / 1000, p90=pct(total, .9), pmax=max(total), pmin=min(total),
                          a50=pct(after, .5), a90=pct(after, .9),
                          f50=pct([t["speech_end_to_reply_audible_ms"] for t in first], .5),
                          l50=pct([t["speech_end_to_reply_audible_ms"] for t in later], .5),
                          wer_mean=statistics.mean(wers) * 100, pss_max=max(pss), pss_med=statistics.median(pss),
                          cpu_med=statistics.median(cpu), cpu_max=max(cpu), tmin=min(temps), tmax=max(temps),
                          hmin=min(heads) if heads else -1, hmax=max(heads) if heads else -1,
                          load_laya=loadms.get("laya_npu", 0), load_whisper=loadms.get("whisper", 0), load_qwen=loadms.get("qwen", 0),
                          load_tok=loadms.get("tokenizer", 0), load_total=meta.get("load_ms") or 0,
                          device=html.escape(meta.get("device") or "SM8750"),
                          endpoint_med=pct([t["endpoint_ms"] for t in ok], .5),
                          step_med=pct([t["assistant_step_ms"] for t in ok], .5),
                          asr_med=pct([t["asr_ms"] for t in ok], .5), tts_med=pct([t["tts_first_audio_ms"] for t in ok], .5),
                          aud_med=pct([t["audible_after_text_ms"] for t in ok], .5),
                          ext_med=pct([llm_ms(t, "extract") for t in ok if llm_ms(t, "extract")], .5),
                          q_med=pct([llm_ms(t, "question") for t in ok if llm_ms(t, "question")], .5),
                          clf_med=pct([t["classifier_ms"] for t in ok], .5))
    with tempfile.TemporaryDirectory() as tmp:
        page = os.path.join(tmp, "report.html")
        open(page, "w").write(doc)
        open(os.path.splitext(out_pdf)[0] + ".html", "w").write(doc)
        subprocess.run(["chromium", "--headless", "--no-sandbox", "--disable-gpu", "--no-pdf-header-footer",
                        f"--print-to-pdf={os.path.abspath(out_pdf)}", page], check=True, capture_output=True)
    print("wrote", out_pdf)


TEMPLATE = """<!doctype html><html><head><meta charset="utf-8"><title>Conversation latency</title>
<style>
@page {{ size: A4; margin: 18mm 16mm 18mm 16mm; }}
body {{ font-family: 'Roboto Flex', 'Roboto', 'DejaVu Sans', sans-serif; color: #0A0E1A; font-size: 10pt; line-height: 1.45; }}
h1 {{ font-size: 24pt; font-weight: 400; letter-spacing: -0.3pt; color: #06103C; margin: 0 0 4pt; }}
h2 {{ font-size: 14pt; font-weight: 500; color: #06103C; margin: 20pt 0 6pt; break-after: avoid; }}
h3 {{ font-size: 11pt; font-weight: 500; margin: 12pt 0 4pt; break-after: avoid; }}
p {{ margin: 0 0 7pt; max-width: 165mm; }}
.meta {{ color: #434753; font-size: 9pt; margin-bottom: 14pt; }}
.hero {{ display: flex; gap: 8pt; margin: 10pt 0 6pt; }}
.card {{ flex: 1; background: #F1F3F9; border-radius: 10pt; padding: 9pt 11pt; }}
.card .v {{ font-size: 20pt; color: #06103C; letter-spacing: -0.3pt; }}
.card .k {{ font-size: 8.5pt; color: #434753; }}
table {{ border-collapse: collapse; width: 100%; font-size: 8.6pt; margin: 4pt 0 8pt; }}
th {{ text-align: left; font-weight: 500; color: #434753; border-bottom: 1px solid #D1D5E4; padding: 4pt 5pt; }}
td {{ border-bottom: 1px solid #E2E6F0; padding: 3.5pt 5pt; vertical-align: top; }}
td.note {{ color: #434753; font-size: 8pt; }}
.small td, .small th {{ font-size: 7.4pt; padding: 2.5pt 3pt; }}
figure {{ margin: 6pt 0 10pt; break-inside: avoid; }}
figcaption {{ font-size: 8.5pt; color: #434753; }}
svg {{ max-width: 100%; height: auto; }}
.box {{ background: #E8ECFB; border-radius: 10pt; padding: 9pt 12pt; margin: 8pt 0; }}
ul {{ margin: 0 0 7pt 14pt; padding: 0; }} li {{ margin-bottom: 3pt; }}
.pb {{ break-before: page; }}
</style></head><body>
<h1>How fast does the voice assistant answer?</h1>
<div class=meta>Sentinel AI incident assistant · Snapdragon 8 Elite dev kit ({device}) · build {commit} · {today}</div>

<p>This report measures the time between a user finishing a spoken answer and the assistant starting to speak its reply,
across {n} turns from {nconv} scripted incident conversations ({runs} passes), and breaks that time down by stage.
Everything ran on the device: speech recognition and incident classification on the Hexagon NPU, the language model and
the voice on the CPU.</p>

<div class=hero>
 <div class=card><div class=v>{p50:.0f} ms</div><div class=k>median, end of speech → reply audible</div></div>
 <div class=card><div class=v>{p90:.0f} ms</div><div class=k>90th percentile (worst {pmax:.0f} ms)</div></div>
 <div class=card><div class=v>{a50:.0f} ms</div><div class=k>median after the pause is detected</div></div>
 <div class=card><div class=v>{exact}/{n}</div><div class=k>turns transcribed word-perfect</div></div>
</div>

<div class=box><b>In short.</b> A typical spoken turn gets an audible reply in about {p50s:.1f} s after the user stops
talking (p90 {p90:.0f} ms). About {endpoint_med:.0f} ms of that is the deliberate pause the assistant waits for to be
sure the user has finished; the computing itself takes about {a50:.0f} ms. The first turn of each report is slower
(median {f50:.0f} ms vs {l50:.0f} ms for follow-ups) because the language model then reads everything said so far to
fill the form in one go. The NPU stages are small: Laya {clf_med:.0f} ms and Whisper about {asr_med:.0f} ms per turn.
</div>

<h2>Where the time goes</h2>
<figure>{chart_breakdown}<figcaption>Median time spent in each stage, from the moment the user stops speaking until the
reply's first sound plays. Bar totals are sums of the stage medians, so they differ slightly from the overall median. "Speech recognition" includes waiting for a live-caption transcription that may still be
running when the turn ends.</figcaption></figure>

<table><tr><th>Stage</th><th>n</th><th>p50 ms</th><th>p90 ms</th><th>min</th><th>max</th><th></th></tr>{stage_rows}</table>

<figure>{chart_cdf}<figcaption>Distribution over all {n} turns. Read across at 0.5 for the median and 0.9 for the 90th
percentile.</figcaption></figure>

<h2>What was measured, and how</h2>
<p><b>Voice path, end to end.</b> Each user line was synthesized with the Piper voice and streamed into the app's real
voice engine in 20 ms frames at real-time pace, over low background noise (about −66 dBFS), exactly as microphone audio
arrives. The same code then runs as in a live conversation: pause detection, Whisper base (encoder on the NPU, decoder on
the CPU), the fine-tuned Laya classifier on the NPU, the form policy, Qwen3-0.6B for value extraction and question
wording, and the Piper voice for the reply. A turn only continues once the previous reply has finished playing.</p>
<p><b>Timestamps.</b> <i>End of speech</i> is when the last frame of the user's audio entered the engine. <i>Turn
detected</i> is when the pause detector closed the turn. <i>Answer text ready</i> is when the engine published the next
question. <i>Reply audible</i> is when the first frame of the reply's audio was played by the audio system (an AudioTrack
position marker), so it includes synthesis and the audio output buffer, but not the speaker's own acoustic delay
(a few ms).</p>
<p><b>Other metrics.</b> Word error rate of each transcript against the scripted line; app memory (PSS); average CPU cores
used during a turn (process CPU time ÷ wall time); battery temperature and Android's thermal headroom (1.0 = throttling).</p>

<h2>Accuracy of what was heard</h2>
<p>{exact} of {n} turns were transcribed word-perfect; mean word error rate {wer_mean:.1f}%. Building codes are the
weak spot: a single letter spoken as a letter ("N building") is easily heard as "M", "an end" or "in". Whisper is now
primed with the site's building codes, and a small normalization step maps the common confusions back
("an end building" → "N building", "Q.R.C." → "QRC", a lone "Dunn" → "done"). {filed} conversations were filed by voice.
</p>
{misheard_html}
{note_html}

<h2>Resources</h2>
<table>
<tr><th>Metric</th><th>Value</th><th></th></tr>
<tr><td>App memory (PSS)</td><td>median {pss_med:.0f} MB, max {pss_max:.0f} MB</td><td class=note>all models resident: Laya DLC, Whisper + 2 NPU encoder graphs, Qwen GGUF, Piper</td></tr>
<tr><td>CPU during a turn</td><td>median {cpu_med:.2f} cores, max {cpu_max:.2f}</td><td class=note>out of 8 Oryon cores; Qwen and the Whisper decoder dominate</td></tr>
<tr><td>Battery temperature</td><td>{tmin:.1f}–{tmax:.1f} °C</td><td class=note>no sustained heating over the runs</td></tr>
<tr><td>Thermal headroom</td><td>{hmin:.2f}–{hmax:.2f}</td><td class=note>Android forecast; 1.0 means throttling, so far from it</td></tr>
<tr><td>Model loading (once, at app start)</td><td>{load_total:.0f} ms total</td><td class=note>tokenizer {load_tok} ms, Laya on NPU {load_laya} ms, Whisper + encoders {load_whisper} ms, Qwen {load_qwen} ms</td></tr>
</table>

<h2>What would make it faster</h2>
<ul>
<li><b>Shorter pause rule.</b> Waiting for 0.9 s of silence is the single biggest fixed cost ({endpoint_med:.0f} ms).
0.6 s would save about 300 ms per turn, at the price of cutting off users who pause mid-sentence more often.</li>
<li><b>Fewer or faster language-model calls.</b> Qwen extraction (p50 {ext_med:.0f} ms) and question writing (p50
{q_med:.0f} ms) run on the CPU. Using the taxonomy's fixed question text instead of a written one saves the second call;
the planned fine-tuned Qwen should extract better with shorter output; moving the model to the NPU (Genie) would cut both.</li>
<li><b>Start speaking sooner.</b> The reply voice already streams sentence by sentence (first audio in about {tts_med:.0f}
ms); the fixed lead-ins ("Got it, your report is ready…") could be spoken while the language model is still writing the
question.</li>
</ul>

<h2>Limits of this measurement</h2>
<ul>
<li>The user's voice is synthetic and clean. Real voices, accents and a busy room make recognition harder; the timings
are representative, the accuracy is optimistic.</li>
<li>Audio was injected into the engine rather than captured by the microphone, so microphone buffering (about 20–40 ms)
is not included.</li>
<li>One device, one session. Times include whatever else the kit was doing; the 90th percentile shows the spread.</li>
</ul>
{failures_html}

<h2 class=pb>Appendix: every turn (first pass)</h2>
<table class=small><tr><th>#</th><th>User said</th><th>Heard</th><th>WER</th><th>pause</th><th>ASR</th><th>Laya</th><th>extract</th><th>question</th><th>TTS 1st</th><th>total ms</th></tr>
{per_turn}</table>
<p class=meta>Times in ms. "pause" = end of speech → turn detected; "total" = end of speech → reply audible.
Raw data and the generator: scripts/latency_report.py, scripts/probe_kit.sh (VOICE=1), app/src/debug/…/VoiceBench.kt.</p>
</body></html>"""


NOTE = ""

if __name__ == "__main__":
    args = sys.argv[2:]
    if "--note" in args:
        i = args.index("--note"); NOTE = args[i + 1]; args = args[:i] + args[i + 2:]
    main(sys.argv[1], args)
