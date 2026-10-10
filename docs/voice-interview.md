# Voice interview and assembled report

## User flow

The launcher is now `ConversationActivity`: one scrollable screen with a voice
circle, assistant question, live transcript, answer progress and optional text
input. Import and language controls live in Setup. The form appears on the same
screen when the interview reaches review.

Default APKs contain Whisper Base and Qwen3 0.6B Q8_0. First launch extracts and
checksum-validates the bundled models in private storage, then loads them on the
background worker. No phone download or manual import is needed. Setup still
allows a replacement bundle; existing imported packs are preserved.

1. Tap the circle. The assistant asks what happened and where.
2. After playback, capture begins automatically. Whisper produces tentative
   captions when its worker is free; roughly 1.6 seconds of silence ends an answer.
   Wait for “Your turn - speak now” before speaking. “I'm done answering” ends
   an answer explicitly without discarding it; Pause stops the conversation.
3. Capture stops while final Whisper transcription/translation and Qwen run.
   Only final answers commit facts. Tentative text is never a completion signal.
4. Qwen initially proposes a configured incident type; code maps it to a department.
   For unclear or multiple distinct incidents, the interviewer asks for the main issue.
5. Qwen extracts facts in one call. After validating and applying those facts,
   a separate focused Qwen call composes a natural follow-up using the updated
   draft and latest answer. Code limits question targets to unresolved fields in
   the highest remaining safety/location priority tier; the model chooses within
   that tier rather than being forced to ask about its first field.
6. Once required fields are known or explicitly unknown/declined, the app asks:
   “Is there anything else you would like to add before I prepare your report?”
7. An actual answer to that step is required. Additional details and corrections
   are processed; unresolved corrections reopen collection. Silence never finishes.
8. The completed draft appears as a readable form with unknowns marked, correction
   controls and JSON export. Nothing is dispatched or submitted.

Tap Pause to stop the loop. Backgrounding stops capture, playback and pending
generation. The latest saved draft restores after reopening, without automatically
opening the microphone. Setup can replace the draft after confirmation.

## Definite forms

`app/src/main/assets/department_forms.json` contains versioned department templates.
Every department requires description, reporter name, building, floor, exact area,
time, injury status and immediate safety information. Extra department fields:

| Department | Additional required fields |
| --- | --- |
| Facilities Engineering | Affected system, impact |
| Housekeeping & Workplace Services | Service needed, impact |
| Global Security | Security concern, impact |
| Environment, Health & Safety | Hazard details, impact |
| IT & Workplace Technology | Technology affected, impact |
| R&D Lab Operations | Lab item, impact |

Configured incident-specific required fields are added from the existing taxonomy:
for example, a water leak also needs source, spread and proximity to electrical
equipment. These are editable demo templates, not official departmental forms.

Questions vary; allowed fields do not. Known fields need a typed value and an
exact quote from the current answer. Unknown/ambiguous values are null. Ambiguous
required fields block completion. Explicit unknown answers count as answered but
remain visible, including a warning when safety is unknown. Completion means
ready for review, not that every fact is certain or the situation is safe.

## System prompts and model contract

`VoiceInterview.kt` contains the routing and interview prompts. The routing prompt
restricts selection to the 28 configured IDs and requires a quote from the answer.
The interview prompt receives the current question/final English answer, active
form definitions, existing values/statuses, unresolved required fields, up to
three bounded recent question/answer pairs, and the current interview stage.

Instructions cover grounded extraction, corrections, negation, explicitly unknown
answers, ambiguous yes/no responses, safety-first questioning, at most two related
question targets, and ignoring instructions embedded in transcript data. The model
cannot claim submission, invent fields or generate priority/SLA. Explicit incident
corrections trigger route reconsideration; Setup also permits manual type selection.
Fields outside a changed template are removed and requirements are recomputed.

The extraction response has `fields`, `extra_answer_complete` and
`reconsider_incident_type`. The separate question response has `question` and
`question_fields`. JNI `generateJson` uses a
general JSON-object grammar for these contracts. Legacy `generate` retains its
extraction-specific grammar for the earlier engine.

Code checks response keys, field allowlists, value types, quotes and question
targets. An invalid question response falls back to a configured question;
`question_source` and `question_fallback_reason` distinguish that fallback from
actual generated questions in exported drafts. Structural/evidence validation cannot prove semantic
correctness; human review is still needed.

## Integration boundary

`VoiceInterviewEngine` implements the existing `VoiceEngine` capture interface.
This flow uses Whisper + Qwen even with a full bundle; it does not use CfC/Laya.
The earlier engine, ONNX adapters, fixture tools and turn contract remain in source.
The interview does not execute zone/history tools or calculate priority.

Drafts live in app-private `voice-interview-draft.json`, written through a temporary
file. Exports include template/model versions, department/type, values, evidence,
original/English turns, unknowns and extra-details answers. The friend's final
reporting/storage/submission flow still needs an explicit adapter.

## Android Studio handoff

Build and run from Android Studio with native models enabled. The new JNI method
requires rebuilding the native library; an older APK cannot run this interview.
Use the existing reference Whisper/Qwen bundle from Setup, an ARM64 phone and an
offline English TTS voice. If TTS is unavailable, the question remains visible
and capture resumes for an answer.

The speaking/listening handoff now waits for microphone shutdown before playback,
requests media audio focus, then waits 250 ms after playback before reopening the
microphone. A further 250 ms of ambient calibration runs before “Your turn - speak
now” appears. Missing or stalled TTS reports a visible voice status and cannot
leave the interview waiting forever. Setup includes spoken-question settings.
“Answer now / skip playback” lets the user bypass playback explicitly.

Pause detection uses a noise-relative energy threshold, hysteresis, 120 ms minimum
voiced audio and 1.6 seconds of silence. It is still an energy heuristic, not a
trained VAD, and noisy-room behavior requires device evaluation. The explicit
“I'm done answering” button provides an alternative when silence detection fails.
Question composition adds another local LLM call; its latency and response quality
have not been measured. Invalid generated questions remain visibly attributable
to a template fallback in exported draft metadata.

If models are absent or loading fails, the circle/Start action opens Setup rather
than silently staying disabled. Import `tmp/reference-models/incident-asr-llm.zip`
from the phone's file picker; having that ZIP on the workstation does not install
it in the app. Wait for loading to finish, then start and grant microphone access.
TTS initialization messages do not replace model-loading failures. Microphone
permission completion resumes the requested listening action, including when the
permission dialog temporarily backgrounds the Activity. Permanently denied access
offers the app settings screen.

No Android build, emulator/device run, or test was performed for this change.
Earlier validation reports describe the previous extraction screen. Dynamic
questions and model accuracy require evaluation with the actual models. This is
pause-driven interaction, not a demonstrated real-time latency result. Capture
stops during playback; hands-free barge-in is not implemented in this flow.
Long answers/context or token exhaustion produce an explicit error and preserve
the previous draft instead of committing partial results.
