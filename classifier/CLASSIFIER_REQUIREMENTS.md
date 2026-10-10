# Classifier — Functional Requirements (skeleton)

A general, on-device text classifier for voice-to-form apps. One base model,
fine-tuned per deployment ("form pack"). Qualcomm HQ incident reporting is the
first form pack, not a special case.

## 0. Scope

- IN: decide *which form* an utterance belongs to, and *which sentences matter*.
- OUT: filling field values (LLM), priority/severity and routing (rules in code),
  speech recognition, translation, text generation.

## 1. Form pack (the only domain-specific input)

- FP1  A form pack is one JSON config + one labelled dataset. Nothing domain-specific
       lives in model code.
- FP2  Config declares the form types (id, label, description). Current pack:
       `incident_taxonomy.json` → `incident_types` (28 types).
- FP3  Dataset is JSONL conversations: `form type` per conversation,
       `relevant: true|false` per user sentence (current: `data/*.jsonl`).
- FP4  Swapping to a new place/domain = new form pack + re-run fine-tuning.
       No code changes.

## 2. Tasks the classifier must deliver

- T1  **Form-type classification**: user utterance(s) → one form type id from the pack.
- T2  **Sentence relevance**: each user sentence → relevant / filler.
- T3  **Out-of-scope**: an utterance matching no form type → `none` (not a forced guess).
- T4  **Multi-turn**: T1 can use all user turns so far, not just the first.
- T5  (optional) **Top-k**: return the 2–3 best form types for the LLM/user to confirm.

## 3. Output contract

```json
{
  "form_type": {"id": "water_leak", "confidence": 0.93,
                "top_k": [["water_leak", 0.93], ["washroom_plumbing", 0.04]]},
  "sentences": [{"text": "...", "relevant": true, "confidence": 0.88}],
  "model_version": "pack-qualcomm-hq@0.1"
}
```

- O1  Confidences are calibrated (temperature-scaled on a held-out split), so
      a fixed threshold means the same thing across form packs.
- O2  Below threshold → caller asks a follow-up instead of trusting the label.
- O3  Output ids only ever come from the pack config (no free text).

## 4. Input

- I1  English and romanized Hinglish text from ASR (Latin script).
- I2  Devanagari: unsupported unless ASR transliterates/translates first. (decide)
- I3  Max input length: ___ tokens (proposed 128 per sentence, 256 per conversation).

## 5. On-device constraints

- D1  Runs fully offline via QAIRT (CPU / GPU / Hexagon).
- D2  Latency: ___ ms per utterance on target device (proposed ≤100 ms on QIDK HTP).
- D3  Model size: ___ MB on device (INT8).
- D4  Static input shapes (QAIRT requirement); padding + mask handled in the app.

## 6. Fine-tuning workflow

- F1  One command: `train --pack <config.json> --data <dir>` → `.onnx` + `.dlc` + metrics.
- F2  Trains on the team laptop (RTX 4050, 6 GB) in under ___ min for ~200–2,000 conversations.
- F3  Handles class imbalance (current relevance labels are 555 true / 55 false).
- F4  Reports per-class accuracy/F1, confusion matrix, and calibration error.
- F5  Every form type has ≥ ___ examples before a pack is accepted
      (current data covers 11 of 28 types).

## 7. Acceptance (per form pack)

- A1  Form-type accuracy ≥ ___ % on held-out data, split by lang_style
      (english / hindi_translated / hinglish).
- A2  Filler-sentence recall ≥ ___ %.
- A3  Out-of-scope utterances rejected ≥ ___ %.
- A4  Same predictions (± quantization tolerance) on laptop and on device.

## 8. Open decisions

- [ ] Base model: ModernBERT-base vs a smaller ModernBERT-style encoder vs LNN (see comparison)
- [ ] Devanagari handling (I2)
- [ ] Fixed label head per pack vs label-embedding matching (new types without retraining)
- [ ] Target device for latency numbers: QIDK vs test phone (Snapdragon 695)
