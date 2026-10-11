#!/usr/bin/env python3
"""Build docs/architecture.pdf and docs/prd.pdf (HTML + Graphviz SVG -> PDF via headless Chromium).

usage: python3 scripts/docs/build_docs.py [OUT_DIR]   (default: docs/)
Needs: graphviz (dot), chromium.
"""
import datetime, os, subprocess, sys, tempfile

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import diagrams as D  # noqa: E402

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))


def svg(dot):
    out = subprocess.run(["dot", "-Tsvg"], input=dot, capture_output=True, text=True, check=True).stdout
    return out[out.index("<svg"):]


def fig(name, caption, narrow=False):
    return f'<figure{" class=narrow" if narrow else ""}>{svg(getattr(D, name))}<figcaption>{caption}</figcaption></figure>'


FONTS = os.path.join(ROOT, "app/src/main/res/font")
CSS = """
@font-face { font-family:'Roboto Flex'; font-weight:400; src:url('file://FONTS/roboto_flex_regular.ttf'); }
@font-face { font-family:'Roboto Flex'; font-weight:500; src:url('file://FONTS/roboto_flex_medium.ttf'); }
@font-face { font-family:'Roboto Flex Display'; font-weight:400; src:url('file://FONTS/roboto_flex_display_regular.ttf'); }
@page { size: A4; margin: 17mm 15mm 17mm 15mm; }
body { font-family: 'Roboto Flex','Liberation Sans',sans-serif; color:#0A0E1A; font-size:9.6pt; line-height:1.45; }
h1 { font-family:'Roboto Flex Display','Roboto Flex',sans-serif; font-size:24pt; font-weight:400; letter-spacing:-0.3pt; color:#06103C; margin:0 0 3pt; }
h2 { font-size:14pt; font-weight:500; color:#06103C; margin:18pt 0 6pt; break-after:avoid; }
h3 { font-size:10.8pt; font-weight:500; margin:11pt 0 4pt; break-after:avoid; }
p { margin:0 0 6pt; } .meta { color:#434753; font-size:8.8pt; margin-bottom:12pt; }
table { border-collapse:collapse; width:100%; font-size:8.5pt; margin:4pt 0 9pt; break-inside:auto; }
th { text-align:left; font-weight:500; color:#434753; border-bottom:1px solid #D1D5E4; padding:3.5pt 5pt; }
td { border-bottom:1px solid #E2E6F0; padding:3pt 5pt; vertical-align:top; } tr { break-inside:avoid; }
td.k { white-space:nowrap; font-weight:500; color:#06103C; }
figure { margin:6pt 0 10pt; break-inside:avoid; text-align:center; } figcaption { font-size:8.3pt; color:#434753; text-align:left; }
svg { width:100%; height:auto; max-height:232mm; }
figure.narrow svg { width:72%; }
.box { background:#E8ECFB; border-radius:10pt; padding:8pt 12pt; margin:8pt 0; }
.legend span { display:inline-block; padding:1pt 6pt; border-radius:5pt; margin-right:6pt; font-size:8pt; }
ul,ol { margin:0 0 6pt 15pt; padding:0; } li { margin-bottom:2.5pt; }
code { font-family:'Liberation Mono',monospace; font-size:8.2pt; background:#F1F3F9; padding:0 2pt; border-radius:3pt; }
.pb { break-before:page; } .two { columns:2; column-gap:14pt; }
""".replace("FONTS", FONTS)

LEGEND = ('<p class=legend><span style="background:#06103C;color:white">NPU (Hexagon HTP)</span>'
          '<span style="background:#E8ECFB">CPU model</span><span style="background:white;border:1px solid #D1D5E4">app logic</span>'
          '<span style="background:#EDF5EF">user-facing / I/O</span><span style="background:#FFF4DB">data / files</span></p>')


def architecture(commit, today):
    return f"""<!doctype html><html><head><meta charset=utf-8><title>Sentinel AI architecture</title><style>{CSS}</style></head><body>
<h1>Sentinel AI: how the voice incident assistant works</h1>
<div class=meta>Architecture, pipeline and tech stack · Snapdragon 8 Elite (SM8750) · build {commit} · {today}</div>

<div class=box><b>One sentence.</b> An employee taps the mic and says what's wrong; the phone transcribes it (Whisper),
decides what kind of incident it is (fine-tuned Laya on the NPU), asks only for what's missing (form policy from the
incident taxonomy), pulls the values out of the answers (Qwen3-0.6B with strict validation), speaks each question
(Piper), and files a routed, prioritised report when the user says "done". Nothing leaves the device.</div>

<h2>1. System context</h2>
{fig("CONTEXT", "Two roles share one app on one device: a reporter who talks to the assistant, and the facilities desk that works the filed reports.")}
{LEGEND}

<h2 class=pb>2. One conversation turn, end to end</h2>
<p>Every user answer goes through the same path. Audio stays on the device; the heavy encoders run on the NPU, the
small language model and the voice on the CPU.</p>
{fig("TURN", caption="The voice path. Dashed edges are background or optional paths. Median timings on the kit: pause 0.79 s, Whisper 0.19 s, Laya 11 ms, Qwen 0.4–0.9 s per call, first reply audio 0.06 s; end of speech → reply audible 1.78 s median (see the latency report).")}

<h3>Stage by stage</h3>
<table><tr><th>Stage</th><th>What it does</th><th>Implementation</th></tr>
<tr><td class=k>Capture</td><td>Opens the mic with echo cancellation and noise suppression; 20 ms PCM16 frames; never blocks on inference.</td><td><code>core/audio ConversationCapture</code>, AudioRecord VOICE_COMMUNICATION</td></tr>
<tr><td class=k>Resample</td><td>Any capture rate → 16 kHz mono float.</td><td><code>core/inference PcmResampler</code></td></tr>
<tr><td class=k>Pause detection</td><td>Decides when the user has finished. Noise floor = 20th percentile of the last 3 s (follows fans/AC); speech starts at +15 dB for 200 ms; turn ends after 0.9 s within 6 dB of the floor; turns must peak ≥ −35 dBFS (people a few metres away are ignored); 1 s of flat loud sound is learned as new background; 15 s cap.</td><td><code>core/inference NoiseRobustEndpoint</code> (7 unit tests)</td></tr>
<tr><td class=k>Live caption</td><td>While the user talks, the audio so far is transcribed every 2 s if the engine is idle, for the on-screen caption.</td><td><code>IncidentEngine</code> previews</td></tr>
<tr><td class=k>Speech recognition</td><td>Whisper base. Encoder on the NPU through whisper.cpp's external-encoder hook: 10 s window graph for turns ≤ 9.5 s (18 ms), 30 s graph otherwise (190 ms). Decoder on the CPU. Language fixed to English; initial prompt lists the building codes; segments with no-speech probability ≥ 0.6 dropped.</td><td><code>core/native-whisper</code> (whisper.cpp d1be6fde + <code>whisper_snpe_encoder.cpp</code>)</td></tr>
<tr><td class=k>Speech filter</td><td>Removes non-speech tags ([Music], (applause), ♪) and Whisper's noise hallucinations ("Thank you."); if nothing is left the mic just keeps listening. Normalises domain mishearings: "an end / M building" → "N building", "Q.R.C." → "QRC", lone "Dunn" → "done".</td><td><code>IncidentEngine.spoken / normalizeSpeech</code> (tested)</td></tr>
<tr><td class=k>Assistant step</td><td>Classify → settle type → extract values → choose next action → word the question → route. Exact port of <code>laya/assistant.py</code>, <code>form.py</code>, <code>llm.py</code>.</td><td><code>app/…/assistant/</code> (8 parity tests vs Python)</td></tr>
<tr><td class=k>Voice reply</td><td>Piper lessac-medium at 1.2×, synthesised sentence by sentence while the previous sentence plays; resampled to 48 kHz so it uses the device's primary audio mixer.</td><td><code>engine/NeuralSpeaker</code>, sherpa-onnx 1.13.8</td></tr>
<tr><td class=k>Turn-taking</td><td>When a question has been spoken, the mic re-opens 300 ms later by itself. Tapping during speech interrupts and listens immediately.</td><td><code>IncidentAssistantViewModel</code></td></tr>
<tr><td class=k>Filing</td><td>"done" (or the button) files the report; respond-by time = filing time from the device clock + the priority's SLA.</td><td><code>data/ReportStore</code> (atomic JSON)</td></tr>
</table>

<h2 class=pb>3. Inside the assistant step</h2>
{fig("STEP", narrow=True, caption="Decision flow per turn (port of laya/assistant.py + form.py). The language model never decides the incident type or the priority; those come from Laya and from the taxonomy rules.")}

<h3>Rules the code guarantees</h3>
<ul>
<li><b>Essentials first.</b> A report needs WHAT (incident type, which also decides WHO: the department) and WHERE (building and floor). Until they are known only those are asked; "done" early gets "I can't file this yet…".</li>
<li><b>Type settling.</b> Settle at ≥ 0.8 (calibrated with temperature 1.5733); switch when another type reaches 0.8; un-settle if the settled type drops below 0.3; below 0.8 offer the top-3 menu ("Is it (1) A, (2) B or (3) C?") if they cover ≥ 50 %, else ask an open question; a confident classifier beats the user's menu answer.</li>
<li><b>Details after READY.</b> Safety fields first, then location, then type-specific, then time; at most two fields per question, each asked once, each ending "(You can also say 'done' to file the report now.)".</li>
<li><b>Values must be grounded.</b> Enums only from the allowed list; text/number values must appear in the user's words; "unknown" only if the user said they don't know; a building must be a known code or contain a building word. Deterministic rules run first (floors incl. "fourth/ground floor", building codes, "just now").</li>
<li><b>Routing from the taxonomy.</b> Default priority per type, raised by rules (e.g. water near electrics → P1 + electrician + EHS; an unknown safety field counts as the worse case); extra roles notified; secondary incidents flagged when the transcript mentions them; SLA from the priority.</li>
<li><b>Out of scope.</b> If Laya is ≥ 0.8 sure it is not an incident, the assistant says so and files nothing.</li>
</ul>

<h2>4. Conversation and screen states</h2>
{fig("STATES", "Report states. READY means the essentials are known and the user can file at any time.")}
{fig("PHASES", "What the voice screen shows. The mic opens by itself after every question.")}

<h2 class=pb>5. The incident classifier (Laya)</h2>
{fig("LAYA", narrow=True, caption="Classifier input and output. One NPU inference per turn (~11 ms) returns the incident type and, per sentence, whether it is relevant and which form fields it mentions.")}
<p>Laya is convaiinnovations' Laya encoder (ModernBERT-large, 28 layers) fine-tuned with LoRA on a synthetic
conversation dataset built from the incident taxonomy (1,292 conversations, 28 types plus "not an incident" and
multi-incident cases). It reads only the user's sentences, because the assistant's questions would give away the type.
It reaches 87.6 % type accuracy on the test set running W8A16 on the NPU (88.0 % in fp32; the same model without
fine-tuning scores 11.6 %). Exporting it for the NPU needed two fixes: attention masks bounded at −60 instead of
float-min (which collapses the quantized attention range), and one concatenated output tensor (SNPE on the HTP only
returns the last output of a multi-output graph). The Kotlin tokenizer reproduces the Hugging Face tokenizer token for
token (tested), and the input layout matches the Python <code>build_samples</code> exactly.</p>

<h2>6. What runs where</h2>
{fig("HARDWARE", "Hardware mapping on the Snapdragon 8 Elite. Native code is compiled for the Oryon cores' FP16, dot-product and int8-matmul instructions; without that Whisper took minutes per call.")}

<h2 class=pb>7. Code structure</h2>
{fig("MODULES", "Gradle modules and what they depend on. Model files are not in the APK; they are copied to the app's private storage.")}
<table><tr><th>Package / path</th><th>Contents</th></tr>
<tr><td class=k>app/…/assistant</td><td><code>Form.kt</code> (taxonomy, FormState, settle_type, next_action, routing) · <code>Extract.kt</code> (prompts, GBNF grammars, validator, rule_extract) · <code>Assistant.kt</code> (turn loop) · <code>Classifier.kt</code> (Laya input/output) · <code>LayaTokenizer.kt</code></td></tr>
<tr><td class=k>app/…/engine</td><td><code>IncidentEngine</code> (audio → Whisper → assistant, model loading, speech filter) · <code>NativeModels</code> (JNI bindings, WhisperSpeech) · <code>SnpeLayaRunner</code> · <code>LlamaLlm</code> · <code>NeuralSpeaker</code> / <code>SystemSpeech</code> · main's original engines (unused by the UI)</td></tr>
<tr><td class=k>app/…/feature/assistant</td><td><code>IncidentAssistantViewModel</code>: capture, engine, speech, turn-taking, filing, desk read-aloud</td></tr>
<tr><td class=k>app/…/ui/app</td><td>Compose screens: role, home, voice report, form, questions, review, history, report detail, desk; components per the design system</td></tr>
<tr><td class=k>app/…/data</td><td><code>ReportStore</code>, <code>FiledReport</code>, <code>ReportStatus</code></td></tr>
<tr><td class=k>app/src/debug</td><td><code>ProbeActivity</code> (adb-only via DUMP permission): scripted turns, Whisper/TTS timing, room levels, <code>VoiceBench</code> latency benchmark</td></tr>
<tr><td class=k>core/*</td><td>audio capture; endpointing and resampling; JNI to whisper.cpp, llama.cpp and SNPE</td></tr>
<tr><td class=k>scripts/</td><td><code>prepare_native.sh</code> · <code>push_models_kit.sh</code> · <code>fetch_sherpa_onnx.sh</code> · <code>whisper_npu/</code> · <code>probe_kit.sh</code> · <code>latency_report.py</code> · <code>docs/</code></td></tr>
</table>

<h2>8. Screens</h2>
{fig("UI", "Navigation. The reporter side is voice only; the form and question screens are views onto the same conversation.")}
<p>Design system: navy <code>#06103C</code> primary, cool slate surfaces, crimson only for live recording, P1 and
missing essentials; Roboto Flex editorial display + UI text type scale; Aurora voice orb (respects reduced motion);
48 dp touch targets; labeled top-bar actions.</p>

<h2 class=pb>9. How the models are built and deployed</h2>
{fig("BUILD", "Offline pipeline. Laya and the Whisper encoder are compiled for the SM8750 HTP ahead of time; Qwen and Piper are used as published.")}
<table><tr><th>Model</th><th>File on device</th><th>Size</th><th>Runs on</th></tr>
<tr><td class=k>Laya (fine-tuned)</td><td><code>laya_w8a16_htp.dlc</code></td><td>860 MB</td><td>NPU, SNPE</td></tr>
<tr><td class=k>Whisper base (multilingual)</td><td><code>ggml-base.bin</code> + <code>whisper_base_encoder_fp16_htp.dlc</code> (30 s) + <code>…_10s_fp16_htp.dlc</code></td><td>148 + 262 + 90 MB</td><td>encoder NPU, decoder CPU</td></tr>
<tr><td class=k>Qwen3-0.6B</td><td><code>Qwen3-0.6B-Q8_0.gguf</code></td><td>639 MB</td><td>CPU, llama.cpp</td></tr>
<tr><td class=k>Piper en_US lessac-medium</td><td><code>voice/</code> (ONNX + espeak-ng data)</td><td>79 MB</td><td>CPU, sherpa-onnx</td></tr>
</table>
<p>Models are copied into the app's private storage with <code>scripts/push_models_kit.sh</code> (Android 15 blocks apps
from reading files adb creates in their external folder). The APK carries the taxonomy, the Laya tokenizer and the
label list. All models load once at app start (about 1.4 s) and stay resident (about 2.5 GB).</p>

<h2>10. Tech stack</h2>
<table><tr><th>Layer</th><th>Technology</th></tr>
<tr><td class=k>Device</td><td>Qualcomm Snapdragon 8 Elite dev kit (SM8750P), Hexagon v79 NPU (HTP), 8 Oryon cores, 15 GB RAM, Android 15</td></tr>
<tr><td class=k>App</td><td>Kotlin 1.9.22, Jetpack Compose (BOM 2024.02, Material 3), Navigation Compose, AndroidX ViewModel/StateFlow; minSdk 24, target 34</td></tr>
<tr><td class=k>Build</td><td>Gradle 8.11.1, AGP 8.5, JDK 17, NDK (27.2 default, 30 on Linux), CMake; native builds tuned with <code>GGML_CPU_ARM_ARCH=armv8.2-a+fp16+dotprod+i8mm</code></td></tr>
<tr><td class=k>NPU runtime</td><td>Qualcomm QAIRT / SNPE 2.47 (C API, int32 and float user buffers, HTP v79 stub/skel, FastRPC)</td></tr>
<tr><td class=k>Speech recognition</td><td>whisper.cpp (pinned), Whisper base; encoder exported PyTorch → ONNX → FP16 DLC</td></tr>
<tr><td class=k>Language model</td><td>llama.cpp (pinned), Qwen3-0.6B Q8_0, thinking off, JSON forced by per-call GBNF grammars</td></tr>
<tr><td class=k>Classifier</td><td>Laya (ModernBERT-large) + LoRA (PyTorch, Colab) → ONNX → W8A16 DLC; Kotlin byte-level BPE tokenizer</td></tr>
<tr><td class=k>Voice</td><td>sherpa-onnx 1.13.8 (ONNX Runtime statically linked), Piper VITS en_US lessac-medium, AudioTrack 48 kHz</td></tr>
<tr><td class=k>Audio</td><td>AudioRecord VOICE_COMMUNICATION, AcousticEchoCanceler, NoiseSuppressor, custom energy endpointing</td></tr>
<tr><td class=k>Data</td><td><code>incident_taxonomy.json</code> (28 types, 6 departments, 50+13 fields, P1–P4 SLAs 15/60/240/1440 min, rules); reports as JSON in private storage</td></tr>
<tr><td class=k>Tooling</td><td>Python 3 (PyTorch, tokenizers, onnx) for export and parity fixtures; Graphviz, matplotlib, headless Chromium for docs; adb/run-as for deployment</td></tr>
</table>

<h2>11. Quality, privacy and performance</h2>
<div class=two>
<p><b>Tests.</b> 8 parity tests against the Python reference (tokenizer, classifier input, rules, validator, end
phrases, six scripted conversations with every action, value and routing, selftest cases, grammar); 7 endpoint tests on
synthetic noise; 3 speech-filter/normalization tests; core pipeline tests from main. On-device checks with the probe.</p>
<p><b>Privacy.</b> No network use; reports stay in app-private storage; transcripts are never written to the system
log; the debug probe can only be started from adb (DUMP permission) and is absent from release builds.</p>
<p><b>Performance (kit, 23 voice turns).</b> End of speech → reply audible: 1.78 s median, 2.79 s p90. After the pause
is detected: 1.0 s. First turn of a report 2.73 s (catch-up extraction), follow-ups 1.64 s. 19/23 turns transcribed
word-perfect. CPU 0.67 cores median; no thermal pressure.</p>
<p><b>Known limits.</b> English only; single-letter building names are fragile (mitigated); a nearby person's clear
sentence can still be taken as a turn; the stock 0.6B model misses some values (fine-tune planned); all models
resident need about 2.5 GB.</p>
</div>
</body></html>"""


def prd(commit, today):
    return f"""<!doctype html><html><head><meta charset=utf-8><title>Sentinel AI PRD</title><style>{CSS}</style></head><body>
<h1>Sentinel AI: product requirements</h1>
<div class=meta>On-device voice incident reporting · v0.1 (hackathon build {commit}) · {today}</div>

<h2>1. Problem</h2>
<p>When something goes wrong at a workplace (a leak, a stuck lift, smoke, a broken badge reader) the person who
notices it rarely knows which team owns it, what details that team needs, or how urgent it is. Reports arrive late,
incomplete ("there's a leak somewhere upstairs") and to the wrong desk, and responders spend the first minutes calling
back for the location. Typing a form on a phone while standing in water is the wrong interface, and sending a voice
recording of an internal site to a cloud service is often not allowed.</p>

<h2>2. Product</h2>
<p>A voice assistant on the phone that takes an incident report the way a good dispatcher would: the employee says what
happened; the assistant works out what kind of incident it is and who handles it, asks only for what's missing
(starting with where), and files a structured, prioritised report the moment the essentials are known. It runs
entirely on the device (Snapdragon 8 Elite NPU and CPU), so it works offline and no audio or text leaves the phone.</p>

<h2>3. Users</h2>
<table><tr><th>User</th><th>Needs</th></tr>
<tr><td class=k>Reporter (any employee)</td><td>Report in under a minute, hands-free, without knowing department names or form fields; know that the right team has it and how soon they'll respond.</td></tr>
<tr><td class=k>Facilities desk / responder</td><td>Complete reports with location and safety facts up front, sorted by urgency, with the right people already notified; mark progress.</td></tr>
<tr><td class=k>Site / EHS admin</td><td>Control the incident types, fields, routing rules and SLAs without code (the taxonomy file).</td></tr>
</table>

<h2>4. Goals and non-goals</h2>
<div class=two>
<p><b>Goals</b></p>
<ul><li>Natural back-and-forth by voice, reply within about 2 s of the user finishing.</li>
<li>Correct incident type and routing for clear reports; ask when unsure.</li>
<li>Never file without WHAT and WHERE; never invent a value.</li>
<li>Fully on-device and offline.</li>
<li>Works in a noisy workplace.</li></ul>
<p><b>Non-goals (v0.1)</b></p>
<ul><li>Sending reports to a server or ticketing system.</li>
<li>Authentication (the role choice is not access control).</li>
<li>Languages other than English (Hindi planned).</li>
<li>Photos, maps, indoor positioning.</li></ul>
</div>

<h2>5. User stories</h2>
<ol>
<li>As a reporter, I tap once and describe the problem in my own words, and the assistant asks me follow-up questions out loud until it has what it needs.</li>
<li>As a reporter, if I'm vague ("something's wrong with the water"), the assistant offers me the likely options and I pick one by voice.</li>
<li>As a reporter, I'm told as soon as my report is ready and which team will handle it, and I can say "done" at any point after that.</li>
<li>As a reporter, I can see the form filling itself from what I said and correct a field by tapping it.</li>
<li>As a reporter, I can see my past reports and their status.</li>
<li>As a responder, I see open reports most urgent first, with location, safety facts, people notified and a respond-by time.</li>
<li>As a responder, I can have a report read out to me and mark it in progress or resolved.</li>
<li>As an admin, I change incident types, questions, rules and SLAs in one JSON file.</li>
</ol>

<h2>6. Functional requirements</h2>
<table><tr><th>ID</th><th>Requirement</th><th>Status</th></tr>
<tr><td class=k>F1</td><td>Voice-only reporting: one mic control; the mic re-opens automatically after each spoken question; tapping during speech interrupts.</td><td>Done</td></tr>
<tr><td class=k>F2</td><td>End-of-turn detection on a natural pause (~0.9 s), robust to steady and changing background noise; ignores music, noise and distant talkers.</td><td>Done</td></tr>
<tr><td class=k>F3</td><td>On-device speech recognition with live caption while speaking.</td><td>Done</td></tr>
<tr><td class=k>F4</td><td>Classify the incident into one of 28 taxonomy types (or not-an-incident) every turn, with calibrated confidence.</td><td>Done</td></tr>
<tr><td class=k>F5</td><td>Essentials first: ask only for missing type/building/floor; refuse to file without them, saying why.</td><td>Done</td></tr>
<tr><td class=k>F6</td><td>Clarify uncertain types with a top-3 menu or an open question; accept "the second one" or a label.</td><td>Done</td></tr>
<tr><td class=k>F7</td><td>After READY, ask remaining details one question at a time, safety first, ≤ 2 fields per question, each once; "done" files.</td><td>Done</td></tr>
<tr><td class=k>F8</td><td>Extract field values only when grounded in the user's words; enums from the allowed list; "unknown" only when said.</td><td>Done</td></tr>
<tr><td class=k>F9</td><td>Route from the taxonomy: owning team, priority (rules can raise it), roles notified, secondary incidents, SLA; respond-by from the device clock.</td><td>Done</td></tr>
<tr><td class=k>F10</td><td>Spoken replies in a natural voice that starts within ~0.3 s of the text being ready.</td><td>Done</td></tr>
<tr><td class=k>F11</td><td>Live form view with provenance ("Auto-filled from voice input"), per-field status, tap-to-correct with type validation.</td><td>Done</td></tr>
<tr><td class=k>F12</td><td>Review screen and filed confirmation with reference ID; history of the user's reports.</td><td>Done</td></tr>
<tr><td class=k>F13</td><td>Facilities desk: list by status and priority, filters by status and team, detail, read-aloud, start work / resolve.</td><td>Done</td></tr>
<tr><td class=k>F14</td><td>Out-of-scope requests are declined without filing.</td><td>Done</td></tr>
<tr><td class=k>F15</td><td>Hindi / Hinglish input with replies in the user's language.</td><td>Planned</td></tr>
<tr><td class=k>F16</td><td>Sync filed reports to a server / ticketing system when online.</td><td>Planned</td></tr>
</table>

<h2>7. Non-functional requirements</h2>
<table><tr><th>Area</th><th>Requirement</th><th>Measured (v0.1)</th></tr>
<tr><td class=k>Latency</td><td>Reply audible ≤ 2.0 s median and ≤ 3.0 s p90 after the user stops talking</td><td>1.78 s median, 2.79 s p90</td></tr>
<tr><td class=k>Processing</td><td>≤ 1.2 s median from turn detected to reply audible</td><td>1.0 s</td></tr>
<tr><td class=k>Classification</td><td>≥ 85 % incident type accuracy on held-out conversations, on the NPU</td><td>87.6 %</td></tr>
<tr><td class=k>Transcription</td><td>≥ 80 % of clean turns word-perfect; building and floor captured</td><td>19/23 (83 %); one building code misheard</td></tr>
<tr><td class=k>Privacy</td><td>No network access; no transcripts in system logs</td><td>Met</td></tr>
<tr><td class=k>Resources</td><td>Fits a 12 GB phone alongside other apps; no thermal throttling over a session</td><td>~2.5 GB resident; headroom 0.33–0.40</td></tr>
<tr><td class=k>Startup</td><td>Models ready ≤ 3 s after launch</td><td>~1.4 s</td></tr>
<tr><td class=k>Accessibility</td><td>48 dp targets, font scaling, reduced motion, labeled controls</td><td>Met</td></tr>
</table>

<h2>8. Success metrics</h2>
<ul>
<li>Time from first word to filed report (target median &lt; 60 s).</li>
<li>Share of reports filed with building and floor (target 100 %, enforced) and with all safety fields answered.</li>
<li>Share of reports routed to the right team without reassignment (target ≥ 90 %).</li>
<li>Responder call-backs per report for missing information (target: halve the current rate).</li>
<li>Turns per report (target ≤ 4) and abandoned conversations.</li>
</ul>

<h2>9. Experience principles</h2>
<ul>
<li><b>Voice first.</b> The screen confirms; the conversation leads. No typing to report.</li>
<li><b>Ask less.</b> Only missing essentials first; never ask the same thing twice; the user can stop at any time once READY.</li>
<li><b>Say what happens next.</b> "Your report is ready and Facilities Engineering will handle it."</li>
<li><b>Calm authority.</b> Editorial type, one navy accent, crimson only for urgency and missing essentials.</li>
</ul>

<h2>10. Dependencies and constraints</h2>
<ul>
<li>Snapdragon 8 Elite class device with Hexagon NPU; QAIRT/SNPE runtime (not redistributable, supplied at build time).</li>
<li>About 2.2 GB of model files copied to the device; English only in v0.1.</li>
<li>The incident taxonomy is the single source of truth for types, fields, questions, routing and SLAs.</li>
</ul>

<h2>11. Risks</h2>
<table><tr><th>Risk</th><th>Mitigation</th></tr>
<tr><td>Misheard locations (single-letter building names)</td><td>Whisper primed with building codes; normalization; essentials re-asked; tap-to-correct on the form.</td></tr>
<tr><td>Bystander speech taken as a turn in busy spaces</td><td>Peak-level gate, non-speech filter; option to ignore out-of-scope voice turns instead of closing.</td></tr>
<tr><td>Small LLM misses values</td><td>Deterministic rules first, validation, catch-up extraction; fine-tuned Qwen; larger model on the NPU later.</td></tr>
<tr><td>Memory on mid-range phones</td><td>Smaller Whisper graph only, quantized models, load on demand.</td></tr>
<tr><td>Another build overwrites the app on shared kits</td><td>Separate application id for team builds.</td></tr>
</table>

<h2>12. Roadmap</h2>
<ol>
<li><b>Next:</b> fine-tuned Qwen for extraction; shorter pause rule option (0.6 s); speak fixed lead-ins while the question is generated.</li>
<li><b>Then:</b> Hindi/Hinglish via Whisper small (encoder on the NPU) with Hindi question templates and a Hindi voice.</li>
<li><b>Later:</b> 3–4B language model on the NPU (Genie); server sync; per-site taxonomies; photos.</li>
</ol>
</body></html>"""


def main():
    out = sys.argv[1] if len(sys.argv) > 1 else os.path.join(ROOT, "docs")
    os.makedirs(out, exist_ok=True)
    commit = subprocess.run(["git", "-C", ROOT, "rev-parse", "--short", "HEAD"], capture_output=True, text=True).stdout.strip()
    today = datetime.date.today().strftime("%d %B %Y")
    for name, build in (("architecture", architecture), ("prd", prd)):
        with tempfile.TemporaryDirectory() as tmp:
            page = os.path.join(tmp, f"{name}.html")
            open(page, "w").write(build(commit, today))
            pdf = os.path.join(out, f"{name}.pdf")
            subprocess.run(["chromium", "--headless", "--no-sandbox", "--disable-gpu", "--no-pdf-header-footer",
                            f"--print-to-pdf={pdf}", page], check=True, capture_output=True)
            print("wrote", pdf)


if __name__ == "__main__":
    main()
