# Whisper + Qwen integration and live test plan

**Previous extraction-flow reference:** the launcher now uses the voice interview
in [voice-interview.md](voice-interview.md). Its routing/form behavior supersedes
the screen instructions below. Model artifact preparation still applies.
The new interview has not been built or tested here.

## Current scope

The `asr_llm` bundle runs real pretrained multilingual Whisper Base and Qwen3
0.6B locally through whisper.cpp and llama.cpp. Both use CPU execution. No training
is needed to start this reference integration. CfC and Laya are deferred.

The pipeline is microphone -> 16 kHz PCM -> pause endpoint -> Whisper original
transcript + English translation -> Qwen grounded extraction/local zone tools ->
candidate JSON. English text can enter directly before Qwen. Android's installed
offline English voice reads an app-approved question.

Full transcript whitespace and evidence offsets are preserved when CfC is off.
Results declare `classification_outcome: not_run`, no incident candidates or
probabilities, empty relevance decisions, and disabled CfC/Laya stages. Qwen does
not assign incident type, priority, department or SLA. Incident-specific form
lookup is blocked in this mode. Existing Phase 3-4 code must request a user choice
before incident-specific routing/forms; this engine does not submit reports.

## Model artifacts

| Role | Artifact | Source | Download size |
| --- | --- | --- | --- |
| Speech/translation | multilingual `ggml-base.bin` | [whisper.cpp models](https://huggingface.co/ggerganov/whisper.cpp) | 147,951,465 bytes |
| Extraction/tools | `Qwen3-0.6B-Q8_0.gguf` | [official Qwen GGUF repository](https://huggingface.co/Qwen/Qwen3-0.6B-GGUF) | 639,446,688 bytes |

`scripts/fetch_reference_models.py` pins repository revisions and SHA-256 hashes.
It verifies downloads, resumes partial files, and builds a stored ZIP. This is a
workstation preparation step; the app has no runtime model download. Q8_0 is the
artifact supplied by the official repository. A smaller quantization can be
evaluated later against this baseline. Disk size does not predict peak RAM.

```powershell
python scripts/fetch_reference_models.py
# Use --workers 1 if a proxy cannot serve HTTP range requests.
powershell -File scripts/prepare_native.ps1
.\gradlew.bat :core:inference:test :core:audio:testDebugUnitTest :app:testDebugUnitTest :app:assembleDebug :app:lintDebug -PnativeModels=true
python -m unittest discover -s scripts -p 'test_*.py' -v
```

Outputs:

- APK: `app/build/outputs/apk/debug/app-debug.apk` (native ARM64 runtime and bundled weights by default).
- Import ZIP: `tmp/reference-models/incident-asr-llm.zip` (about 787 MB decimal).
- Model spec: `tmp/reference-models/asr-llm.spec.json`.

Use an ARM64 Android device, API 24 or newer. Allow space for the ZIP and unpacked
models plus temporary import staging. Confirm available RAM before loading. In
Android Studio uses the repository's `nativeModels=true` default. After preparing
native sources and model ZIP, sync and Run. First launch extracts and checksum-validates
the bundled pack in private storage; existing imported packs are reused. No phone
download or import is required for the default build. Use `-PbundleModels=false`
for the previous import-only packaging, or `-PmodelBundlePath=...` for a custom ZIP.
An explicit `-PnativeModels=false` build cannot load
these native models.

## Integration checks now

1. Import verifies every file hash and explicit active role list. Missing CfC or
   Laya is allowed only with `pipeline: asr_llm`; incomplete full bundles fail.
2. Only Whisper and Qwen are constructed. Existing full mode still constructs
   all four models when a complete trained pack is supplied.
3. Qwen generation uses a JSON grammar. Parsing checks required response keys,
   field allowlist, confidence, statuses, evidence, grouping spans and tool names.
   A grammar guarantees syntax, not correct facts; invalid facts cause errors.
4. Local calls still have three-round/eight-call limits. Preview results cannot
   call tools or commit fields. Revision cancellation rejects stale final work.
5. Tests cover original full mode, two-model evidence offsets, absent predictions,
   tool loops, disabled form calls, unknown/null handling, parser failures, bundle
   contracts, resumable downloads and audio/WAV behavior. Test doubles verify
   orchestration; they do not measure pretrained model quality.

## Your live phone test sequence

1. Install the default native APK and open the app. Built-in Whisper and Qwen are
   prepared automatically on first launch; wait for loading to finish. Only an
   import-only build needs a ZIP copied to the phone and imported through **Setup**.
   The remaining sequence below documents the earlier extraction screen; use
   [voice interview](voice-interview.md) for the current launcher flow.
2. Begin with English text: `There is water leaking in Conference Room 2, building
   N, floor 1. I noticed it ten minutes ago.` Inspect candidates and exact evidence;
   `space_id`, `building`, `floor` and `occurred_at` are configured fields. The
   model may miss fields; record misses rather than bypassing validation.
3. Set the current question to `Which floor?`; submit `I don't know`. Expected:
   an evidence-backed unknown/null floor, not an invented floor number.
4. Submit `Not floor 1; it is floor 2.` Check correction and negation handling.
   While a slow result is running, edit the question or revision. The old turn
   must not update the draft or execute additional tools after invalidation.
5. Ask about `Conference Room 2`/`Pantry`, then an unknown room. If Qwen requests
   a zone tool, inspect its real SQLite result. Fixture matches require confirmation;
   unknown names stay unresolved. A skipped/malformed tool request is a model
   failure to record, not a successful tool test. Form calls must be rejected.
6. Enable airplane mode and record the same report in English, Hindi and mixed
   Hindi/English. Speak 3-10 seconds, then pause for at least a second. Confirm final
   English + original captions, grounded candidates, no network dependency and
   no incident-type prediction. Silence alone must not create a report.
7. Try background noise, fillers, two incidents, an interruption, a long report,
   denied microphone permission, stop/restart capture and background/foreground.
   Verify errors are visible and the app stays responsive. Without hardware AEC,
   question playback during recording is deliberately disabled.
8. Export diagnostics after each batch. Keep the spoken reference transcript,
   expected fields, actual fields, JSON/call errors, device model and model versions
   alongside the export. Record cold load separately from warm-turn timing.

Use at least 10 scenarios x 3 language conditions x 3 repetitions (90 turns),
plus negative cases. Have your friend independently review expected answers.
This small matrix is an integration evaluation, not the M1 training/test dataset.

## Timing and real-time acceptance

Measure user speech end -> final transcript -> candidate JSON -> question audio.
Record median and p95, queue overloads, peak process memory, battery and thermal
state over a 20-30 minute session. Existing diagnostics measure inference processing,
local calls, UI feedback and TTS enqueue-to-start; they do not establish the whole
speech-end-to-audible-response budget. Use a screen/external audio recording or
add correlated monotonic timestamps at each boundary before claiming that budget.

The endpoint currently waits 800 ms of silence. Repeated ASR windows, translation,
CPU Qwen and tool rounds add latency. Measure this baseline on the actual kit,
then tune preview frequency, token budget and a smaller quantization as needed.
Keep the M1 document's acceptance thresholds unchanged. This CPU integration does
not prove Hexagon acceleration, low power operation or the full M1 requirements.

## Friend's Phase 3-4 merge

1. Consume the existing version-1 turn JSON; `metadata.stage_status` is additive.
   Keep session/turn/revision checks before applying any candidate.
2. Confirm the selected incident type in the app while Laya is absent. App code
   uses its taxonomy to pick forms and deterministic routing/priority/SLA rules.
   There is currently no confirmed-type input to this engine's Qwen form tool;
   add that explicit contract and tests before enabling it in `asr_llm` mode.
3. Replace demo zone/history fixtures with your application's repository through
   `LocalTools`. The model proposes fields; app validation/review owns commits.
4. Replay the text cases, then run the same voice matrix inside the merged app.
   Verify corrections, unknowns, multiple incidents, review and idempotent submit.

## CfC and Laya later

- Curate labelled conversations and independently recorded held-out speech;
  synthetic labels require human review and split leakage checks. Pretrained
  Whisper/Qwen do not replace the task-specific CfC/Laya dataset.
- Train/evaluate classifiers, then export INT8 artifacts with exact vocabulary,
  label order and the existing `word_ids_mask_v1` adapter contract, or implement
  a reviewed new tokenizer adapter. Benchmark pre/post-quantization accuracy.
- Build a complete `pipeline: full` bundle. Test filter evidence remapping,
  `none`/`undetermined`, look-alike categories, corrections and two-incident turns.
- Run A/B evaluation against this no-filter/no-classifier baseline on identical
  held-out turns. Reject filtering changes that discard actual incident evidence.
- Measure the complete four-model pipeline on the kit before enabling production
  routing. A QNN/Hexagon backend requires separate export/integration work.
