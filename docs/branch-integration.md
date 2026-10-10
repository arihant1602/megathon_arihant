# Voice-to-form branch integration

Work branch: `integration/voice-form`.

## Available code

- `main` (`1cbf11c`): Android voice capture, native Whisper speech recognition,
  Qwen fact extraction, repeated context-aware questions, saved drafts, review
  and JSON export through `ConversationActivity` / `VoiceInterview`.
- `origin/classifier-model` (`bed92e8`): classifier datasets, CfC benchmark code,
  QAIRT tooling and device diagnostics. Merged into the integration branch.
- [codebreaker77/Model](https://github.com/codebreaker77/Model), commit
  `462ceb0`: newer Laya fine-tuning/export tools, desktop form assistant,
  fine-tuned LoRA/head checkpoint and benchmark records. Merged from
  `refs/remotes/model/main` after cloning into `tmp/laya-model-repo`.
- The separate UI branch is not available in the local refs. Remote fetch failed
  because GitHub credentials could not be obtained. Its code remains unreviewed.

## Intended connected flow

1. The reporting UI starts or restores an interview draft.
2. Microphone capture sends pause-ended answers to Whisper; tentative captions
   never commit form values.
3. A compatible trained classifier proposes the incident/form type. Uncertain,
   out-of-scope or multiple-incident results require clarification.
4. Qwen extracts grounded facts from each final answer using the current question
   and form schema. App policy validates evidence and typed values.
5. Qwen composes follow-up questions for unresolved fields, with safety and
   location handled first. Each answer updates the same draft.
6. After required answers and the extra-details turn, the UI presents the form
   for review and corrections. Export uses the existing draft JSON contract.

Main already implements steps 2, 4, 5 and 6 in its current interview screen.
The separate UI and trained classifier are the remaining integration work.

## Classifier compatibility

The merged `classifier/bench/lnn.py` exports three heads: conversation type,
token relevance and token fields. Benchmark inputs use INT32 token tensors.
The app's `OnnxTextScores` adapter instead requires INT64 `input_ids` and
`attention_mask`, a single FLOAT `scores` output and its declared vocabulary
normalization. Benchmark artifacts cannot be connected directly to that adapter.

The newer Model repository includes `classifier/laya_ft.pt`: LoRA/head weights,
30 type labels, 60 field labels, a 128-token context and temperature calibration.
It excludes the base encoder, tokenizer and ONNX/DLC runtime files. The checkpoint
alone cannot execute inference. Its benchmark records report fine-tuned Laya
W8A16 accuracy of 87.6% over 259 test turns and 10.9 ms per turn on SM8750P;
these are upstream measurements, not independently reproduced here.

The newer exporter uses INT32 inputs and a concatenated FLOAT `logits` output
(type + token relevance + token field heads). Its tokenizer uses the pinned
ModernBERT byte-level vocabulary with CLS/SEP/PAD and user-only history;
the legacy word-vocabulary adapter cannot reproduce that preprocessing.
The active Android voice interview continues to use Qwen routing. This source
merge does not activate Laya or claim NPU execution in the Android app.

The imported assistant also has a different completion policy: it permits ending
after incident type/building/floor, and marks its local outcome `submitted`.
The Android flow requires the department form answers and human review, and
exports without dispatch. Preserve that Android behavior when connecting the UI;
do not treat the desktop prototype's status as an actual submission.

Dataset format and audit tools now read the canonical Android asset at
`app/src/main/assets/incident_taxonomy.json` instead of requiring an untracked
taxonomy copy inside `classifier`. The audit reads UTF-8 explicitly on Windows.
Laya tools likewise use the Android taxonomy and explicit UTF-8. Set
`LAYA_MODEL_DIR` to a directory containing `model.safetensors`,
`encoder/config.json` and `tokenizer/tokenizer.json`; otherwise they use the
pinned Hugging Face snapshot at revision
`7b928d828b7b0e022f929d9bd2e44165aa270148`. `HF_HUB_CACHE` is respected.

## Validation

- Gradle offline checks with `nativeModels=false` and `bundleModels=false`:
  core inference, audio and app unit tests passed (26 tests).
- Dataset format check: 1,292 conversations, zero errors or warnings.
- Dataset balance/leakage audit: 18 checks passed, zero failures or warnings;
  1,164 train and 128 test conversations.
- `python scripts/test_laya_integration.py`: six checks passed, including actual
  checkpoint labels/revision/context against the app taxonomy, uncertain type
  handling, location requirements, confident corrections, out-of-scope handling
  and rejection of ungrounded extraction values. Policy tests run without models.
- These checks do not validate native model inference, generated question
  quality, physical-device behavior or the unavailable UI branch.

## Resume integration

Restore GitHub authentication and fetch all remote branches. Compare the UI
branch's Activity/navigation and draft schema with `ConversationActivity`, then
connect its reporting screen to the interview lifecycle. Inspect the latest
classifier runtime export and tokenizer, implement its matching Android adapter
and test low-confidence routing and corrections before running the combined app
on a phone. Obtain `model.onnx` with any external weight files and tokenizer JSON,
or the device-specific W8A16 DLC plus its runtime integration. Alternatively,
obtain the pinned base encoder and run the imported exporter with the included
checkpoint. A CPU ONNX adapter and an SNPE/HTP adapter are separate runtime paths.
