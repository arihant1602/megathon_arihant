"""Graphviz sources for the architecture document. Each value is DOT; build_docs.py renders them to inline SVG."""

STYLE = '''
  graph [fontname="Liberation Sans", fontsize=10, bgcolor="transparent", pad=0.15, nodesep=0.28, ranksep=0.34];
  node  [fontname="Liberation Sans", fontsize=9.5, shape=box, style="rounded,filled", color="#D1D5E4", fillcolor="#F1F3F9", fontcolor="#0A0E1A", margin="0.12,0.06"];
  edge  [fontname="Liberation Sans", fontsize=8.5, color="#434753", fontcolor="#434753", arrowsize=0.7];
'''
NPU = 'fillcolor="#06103C", fontcolor="white", color="#06103C"'
CPU = 'fillcolor="#E8ECFB", color="#B6C2EC"'
CODE = 'fillcolor="white", color="#D1D5E4"'
IO = 'fillcolor="#EDF5EF", color="#B9D6C2"'
STORE = 'shape=cylinder, fillcolor="#FFF4DB", color="#E7CF94"'
DECIDE = 'shape=diamond, style="filled", fillcolor="white", color="#7C8FD1", fontsize=8.5'

CONTEXT = f'''digraph G {{ rankdir=LR; {STYLE}
  user [label="Employee\\n(speaks)", shape=ellipse, {IO}];
  subgraph cluster_kit {{ label="Snapdragon 8 Elite device: everything on-device, no network"; fontsize=9.5; color="#D1D5E4"; style="rounded";
    app [label="Sentinel AI app\\nvoice assistant", {CPU}];
    models [label="On-device models\\nWhisper · Laya · Qwen3-0.6B · Piper", {STORE}];
    store [label="Filed reports\\n(reports.json)", {STORE}];
    app -> models [dir=both, label="inference"]; app -> store [label="file"];
  }}
  desk [label="Facilities desk\\n(same app, desk role)", shape=ellipse, {IO}];
  user -> app [label="voice"]; app -> user [label="spoken questions"];
  store -> desk [label="reports by priority"]; desk -> store [label="status"];
}}'''

TURN = f'''digraph G {{ rankdir=TB; {STYLE}
  mic [label="Microphone\\nVOICE_COMMUNICATION · echo canceller · noise suppressor\\n16/48 kHz PCM16, 20 ms frames", {IO}];
  rs [label="PcmResampler → 16 kHz float", {CODE}];
  ep [label="NoiseRobustEndpoint\\nfloor = low percentile of last 3 s · start +15 dB for 200 ms\\npause 0.9 s · peak ≥ −35 dBFS · flat ‘speech’ = noise · 15 s cap", {CODE}];
  prev [label="Live caption\\n(preview transcription every 2 s while idle)", {CPU}];
  wenc [label="Whisper encoder\\nFP16 HTP graph · 10 s window (≤9.5 s turns) or 30 s", {NPU}];
  wdec [label="Whisper decoder (whisper.cpp, 6 threads)\\nEnglish · prompt with building codes · drop no-speech segments", {CPU}];
  filt [label="Speech filter + normalization\\ndrop [Music]/(applause)/hallucinations · ‘an end building’→‘N building’", {CODE}];
  ns [label="Not speech → keep listening", {CODE}];
  step [label="Assistant.step (port of laya/assistant.py)\\nsee next diagram", {CODE}];
  laya [label="Laya classifier W8A16 (SNPE, int32 inputs)\\ntype probs · relevance + fields per sentence", {NPU}];
  qwen [label="Qwen3-0.6B Q8_0 (llama.cpp)\\nvalue extraction · question wording · option choice\\nJSON constrained by per-call GBNF grammar", {CPU}];
  view [label="AssistantView → UI\\nquestion · WHAT/WHO/WHERE · form · routing", {CODE}];
  tts [label="Piper lessac-medium (sherpa-onnx)\\n1.2× · sentence streaming · 22.05→48 kHz · AudioTrack", {CPU}];
  spk [label="Speaker", {IO}];
  auto [label="Mic re-opens 300 ms after speech\\n(next answer)", {CODE}];
  file [label="‘done’ → filed\\nReportStore + respond-by = clock + SLA", {STORE}];
  mic -> rs -> ep; ep -> prev [style=dashed, label="while talking"]; ep -> wenc [label="turn ended"];
  wenc -> wdec -> filt; filt -> ns [label="nothing spoken"]; filt -> step [label="text"];
  step -> laya [dir=both]; step -> qwen [dir=both]; step -> view -> tts -> spk; tts -> auto [label="finished"]; auto -> mic [style=dashed];
  step -> file [label="submit", style=dashed];
}}'''

STEP = f'''digraph G {{ rankdir=TB; {STYLE} node [fontsize=9];
  in [label="User turn text", {IO}];
  end [label="Short closing reply?\\n(‘done’, ‘that's all’…)", {DECIDE}];
  cls [label="Laya on all user sentences so far\\n[CLS] turn1 [SEP] turn2 [SEP] … (128 tokens)", {NPU}];
  conf [label="Pending ‘Is it 1/2/3?’ menu", {DECIDE}];
  pick [label="Map reply: ordinal / label words\\n→ Qwen choose (unless classifier ≥0.8)", {CODE}];
  settle [label="settle_type\\n≥0.8 settle / switch · <0.3 un-settle\\nconfident classifier overrides a confirmation", {CODE}];
  want [label="Fields to fill: just asked + seen by Laya in relevant sentences (≤5)\\n+ catch-up of required fields when the type first settles", {CODE}];
  rules [label="rule_extract: floors (‘fourth floor’, ‘ground floor’),\\nbuilding codes N/QRC, ‘just now’", {CODE}];
  llm [label="Qwen extract (schema grammar)\\n→ validate: enum only · grounded in user words ·\\n‘unknown’ only if said · building word check", {CPU}];
  na [label="next_action (form.py)", {CODE}];
  oos [label="Out of scope\\n(not an incident ≥0.8)", {IO}];
  menu [label="Type unclear\\n‘Is it (1) A, (2) B or (3) C?’ / open question", {IO}];
  ess [label="Ask missing essentials\\nbuilding + floor (fire: safety notice first)", {IO}];
  ready [label="READY: ‘your report is ready and <team> will handle it’", {IO}];
  det [label="Ask details, safety first, ≤2 fields, each once\\n+ ‘You can also say done’", {IO}];
  sub [label="Filed: ‘<team> has been notified’", {IO}];
  q [label="Qwen rewrites the question\\n(validated, else taxonomy wording)", {CPU}];
  route [label="Routing: priority rules (e.g. leak near electrics → P1),\\nnotify roles, SLA, secondary incidents", {CODE}];
  in -> end; end -> na [label="yes (no re-classification)"]; end -> cls [label="no"]; cls -> conf; conf -> pick [label="yes"]; conf -> settle [label="no"]; pick -> settle;
  settle -> want -> rules -> llm -> na;
  na -> oos; na -> menu; na -> ess; na -> ready; ready -> det; ready -> sub; na -> det; na -> sub;
  ess -> q; det -> q; q -> route; menu -> route; sub -> route;
}}'''

STATES = f'''digraph G {{ rankdir=LR; {STYLE}
  start [label="New report", shape=circle, width=0.8, fixedsize=true, {IO}];
  type [label="Type not clear\\n(open question / menu)", {CODE}];
  where [label="Type known\\nlocation missing", {CODE}];
  readyst [label="READY to file\\n(details optional)", fillcolor="#EDF5EF", color="#B9D6C2"];
  filed [label="Filed", shape=doublecircle, {IO}];
  oos [label="Not an incident\\n(closed)", shape=doublecircle, fillcolor="#FFDAD6", color="#E9A9A2"];
  start -> type [label="unclear"]; start -> where [label="type ≥0.8"]; start -> readyst [label="type + building + floor"];
  type -> where [label="settled"]; type -> oos [label="‘none’ ≥0.8"]; where -> readyst [label="building + floor"];
  readyst -> readyst [label="detail answers"]; readyst -> filed [label="‘done’"];
  where -> type [label="type collapses <0.3"]; type -> type [label="‘done’ → can't file yet"];
}}'''

PHASES = f'''digraph G {{ rankdir=LR; {STYLE}
  idle [label="Ready\\n‘Tap to talk’", {CODE}]; listen [label="Listening\\nmic open", {IO}];
  think [label="Thinking\\nWhisper → assistant", {CPU}]; speak [label="Speaking\\nPiper reply", {CPU}];
  idle -> listen [label="tap"]; listen -> think [label="pause detected"]; listen -> idle [label="tap stop"];
  think -> speak [label="answer ready"]; think -> listen [label="noise only"]; speak -> listen [label="question asked\\n(+300 ms)"];
  speak -> listen [label="tap to answer now", style=dashed]; speak -> idle [label="report filed / closed"];
}}'''

HARDWARE = f'''digraph G {{ rankdir=LR; {STYLE} node [fontsize=9];
  subgraph cluster_npu {{ label="Hexagon v79 NPU (HTP) via SNPE 2.47"; color="#06103C"; style="rounded"; fontcolor="#06103C";
    l [label="Laya incident classifier\\nW8A16 DLC · ~11 ms / turn", {NPU}];
    we [label="Whisper base encoder\\nFP16 · 18 ms (10 s) / 190 ms (30 s)", {NPU}];
  }}
  subgraph cluster_cpu {{ label="Oryon CPU (8 cores, armv8.2+fp16+dotprod+i8mm builds)"; color="#7C8FD1"; style="rounded"; fontcolor="#3B4F9A";
    wd [label="Whisper decoder\\nwhisper.cpp", {CPU}]; qw [label="Qwen3-0.6B Q8_0\\nllama.cpp · ~0.3–1.2 s / call", {CPU}];
    pp [label="Piper TTS\\nsherpa-onnx (ONNX Runtime) · ~60 ms to first audio", {CPU}];
    lg [label="Kotlin: endpointing, tokenizer, form policy,\\nvalidator, routing, UI (Compose)", {CODE}];
  }}
  subgraph cluster_audio {{ label="Audio subsystem"; color="#B9D6C2"; style="rounded";
    mic [label="Mic + AEC + NS", {IO}]; spk [label="Speaker (48 kHz mixer)", {IO}];
  }}
  mic -> lg -> we -> wd -> lg; lg -> l [dir=both]; lg -> qw [dir=both]; lg -> pp -> spk;
}}'''

MODULES = f'''digraph G {{ rankdir=TB; {STYLE} node [fontsize=9];
  app [label=":app (Kotlin, Jetpack Compose)\\nui/app · feature/assistant · engine · assistant · data", {CPU}];
  audio [label=":core:audio\\nConversationCapture (AudioRecord)", {CODE}];
  inf [label=":core:inference\\nNoiseRobustEndpoint · PcmResampler · VoiceEngine", {CODE}];
  nw [label=":core:native-whisper\\nwhisper.cpp JNI + SNPE encoder (ANEForge hook)", {CODE}];
  nl [label=":core:native-llama\\nllama.cpp JNI + per-call GBNF", {CODE}];
  ns [label=":core:native-snpe\\nSNPE C API JNI (int32 user buffers)", {CODE}];
  sh [label="sherpa-onnx JNI\\n(static ONNX Runtime) + Tts.kt", {CODE}];
  tp [label="third_party (pinned)\\nwhisper.cpp d1be6fde · llama.cpp aa94f20", {STORE}];
  sdk [label="QAIRT / SNPE 2.47 SDK\\nlibSNPE · HTP v79 stub/skel", {STORE}];
  assets [label="APK assets\\nincident_taxonomy.json · laya_tokenizer.json · laya_labels.json", {STORE}];
  models [label="files/models (sideloaded)\\nLaya DLC · Whisper ggml + 2 encoder DLCs · Qwen GGUF · Piper voice", {STORE}];
  app -> audio; app -> inf; app -> nw; app -> nl; app -> ns; app -> sh; audio -> inf;
  nw -> tp; nl -> tp; nw -> sdk; ns -> sdk; app -> assets; app -> models;
}}'''

LAYA = f'''digraph G {{ rankdir=TB; {STYLE} node [fontsize=9];
  s [label="User sentences only\\n(assistant text would leak the type)", {IO}];
  tok [label="LayaTokenizer (Kotlin port)\\nbyte-level BPE · NFC · added tokens\\nleading space per sentence", {CODE}];
  lay [label="input_ids, attention_mask int32 [1,128]\\n[CLS] t1 s1 s2 [SEP] t2 s3 [SEP] … PAD 50283\\nnewest turns kept", {CODE}];
  m [label="Laya (ModernBERT-large encoder, 28 layers)\\n+ LoRA fine-tune + 3 heads\\nW8A16 DLC on HTP", {NPU}];
  o [label="one tensor ‘logits’\\n[30 types | 128×2 relevance | 128×60 fields]", {CODE}];
  r [label="softmax(type / 1.5733) → 30 probs\\nper sentence: mean relevance > 0.5, field sigmoid > 0.5", {CODE}];
  s -> tok -> lay -> m -> o -> r;
}}'''

BUILD = f'''digraph G {{ rankdir=LR; {STYLE} node [fontsize=8.8];
  subgraph cluster_l {{ label="Laya (PC + Colab)"; color="#D1D5E4"; style="rounded";
    ds [label="Synthetic dataset\\n1,292 conversations · 28 types\\n+ none + multi-incident", {STORE}];
    ft [label="LoRA fine-tune (Colab)\\ntype · relevance · fields heads\\ntemperature calibration", {CPU}];
    ex [label="Export: merge LoRA · bounded masks (−60)\\nsingle concatenated output → ONNX", {CODE}];
    q [label="qairt-converter → quantize W8A16\\n→ snpe-dlc-graph-prepare SM8750", {CODE}];
    ds -> ft -> ex -> q;
  }}
  subgraph cluster_w {{ label="Whisper encoder"; color="#D1D5E4"; style="rounded";
    g [label="ggml-base.bin\\n→ PyTorch encoder", {CODE}]; o2 [label="ONNX, fixed window\\n1500 (30 s) / 500 (10 s)", {CODE}];
    d2 [label="FP16 DLC + HTP cache\\n(cosine 0.99992 vs PyTorch)", {CODE}]; g -> o2 -> d2;
  }}
  other [label="Qwen3-0.6B Q8_0 GGUF (Hugging Face)\\nPiper lessac-medium (sherpa-onnx release)", {STORE}];
  kit [label="Device: files/models\\n(adb + run-as)", {IO}];
  q -> kit; d2 -> kit; other -> kit;
}}'''

UI = f'''digraph G {{ rankdir=LR; {STYLE} node [fontsize=9];
  role [label="Role choice\\nReporter / Facilities desk", {IO}];
  home [label="Home\\n‘Report it by voice’ · model status · recent reports", {CODE}];
  talk [label="Voice report (Talk tab)\\norb · question · WHAT/WHO/WHERE · conversation", {CPU}];
  hist [label="History", {CODE}];
  form [label="Incident form\\nauto-filled from voice · tap to correct", {CODE}];
  qs [label="Questions\\ntap-to-answer options", {CODE}];
  rev [label="Review and file", {CODE}];
  det [label="Report detail", {CODE}];
  desk [label="Facilities desk\\nopen work by priority · filters", {CODE}];
  ddet [label="Report detail (desk)\\nListen to report · Start work · Mark resolved", {CODE}];
  role -> home; role -> desk; home -> talk; home -> hist; talk -> form -> rev; talk -> qs; hist -> det; home -> det; rev -> det; desk -> ddet;
}}'''
