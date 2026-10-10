# Voice-to-form branch integration

Work branch: `integration/voice-form`.

## Available code

- `main` (`1cbf11c`): Android voice capture, native Whisper speech recognition,
  Qwen fact extraction, repeated context-aware questions, saved drafts, review
  and JSON export through `ConversationActivity` / `VoiceInterview`.
- `origin/classifier-model` (`bed92e8`): classifier datasets, CfC benchmark code,
  QAIRT tooling and device diagnostics. Merged into the integration branch.
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

The merged branch excludes trained model binaries. Its benchmark implementation
does not establish trained classification accuracy. The active voice interview
continues to use Qwen routing; the legacy Laya/CfC adapters are not activated by
this merge. Inspect the latest model branch and its trained tokenizer, labels,
tensor shapes and confidence policy before adding the runtime adapter.

Dataset format and audit tools now read the canonical Android asset at
`app/src/main/assets/incident_taxonomy.json` instead of requiring an untracked
taxonomy copy inside `classifier`. The audit reads UTF-8 explicitly on Windows.

## Validation

- Gradle offline checks with `nativeModels=false` and `bundleModels=false`:
  core inference, audio and app unit tests passed (26 tests).
- Dataset format check: 1,292 conversations, zero errors or warnings.
- Dataset balance/leakage audit: 18 checks passed, zero failures or warnings;
  1,164 train and 128 test conversations.
- These checks do not validate native model inference, generated question
  quality, physical-device behavior or the unavailable UI branch.

## Resume integration

Restore GitHub authentication and fetch all remote branches. Compare the UI
branch's Activity/navigation and draft schema with `ConversationActivity`, then
connect its reporting screen to the interview lifecycle. Inspect the latest
classifier branch for trained weights and its inference contract, implement the
matching adapter and test low-confidence routing and corrections before running
the combined app on a phone.
