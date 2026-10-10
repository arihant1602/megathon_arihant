# Incident assistant (PC prototype)

You type (later: speak) a report. After every message it classifies, works out what's missing, asks for it,
and classifies again. Panels show the chat, what the classifier sees, and the live form with priority and routing.

**Essential fields** (marked `"essential": true` in `incident_taxonomy.json`, listed in
`completion_policy.essential_fields`): **WHAT** = incident type (which also decides **WHO** responds) and
**WHERE** = building + floor.
1. **Collecting essentials:** it asks only for the missing essentials (LLM-written, specific questions; the
   3-option menu if the type is unclear). Saying "done" here gets "I can't file this yet, I still need ...".
2. **Ready:** once all three are known it says the report is ready and which team will handle it, then asks
   for more details one question at a time (safety fields first, each field asked once). Every question ends
   with "say 'done' to file the report now".
3. **Done:** "done", "that's all", "no that's it", "nothing else" and similar file the report and end the conversation.

```sh
ollama serve                                   # if not already running
python laya/assistant.py                       # interactive
python laya/assistant.py --script laya/demo/fire.txt
python laya/assistant.py --replay --limit 128  # test-set replay summary
python laya/assistant.py --selftest
# after fine-tuning (laya/qwen_finetune.ipynb on Colab):
laya/install_qwen_ft.sh ~/Downloads/qwen3-incident-q8_0.gguf
python laya/assistant.py --model incident-qwen3
```
Use `.venv-qairt/bin/python`. Options: `--threshold 0.8`, `--no-llm`, `--ckpt laya_ft.pt`.

## How a turn works (`laya/assistant.py::Assistant.step`)
1. **Classifier** (fine-tuned Laya, fp32 CPU, ~0.45 s) on all user turns so far gives calibrated type
   probabilities, relevant/filler per sentence, and fields mentioned per sentence.
2. **Type** (`form.settle_type`): settles at ≥ 0.8; switches when another type reaches ≥ 0.8; un-settles if
   the settled type drops below 0.3; a confident classifier beats matching the reply to the 3-option menu.
3. **Values:**
   - rules first for building codes, floors ("fourth floor", "ground floor") and "just now" (`llm.rule_extract`);
   - then **Qwen3-0.6B** (Ollama, JSON schema, thinking off) for the fields just asked plus the fields the
     classifier saw in this turn (≤ 5);
   - when the type first settles, one catch-up call reads everything relevant so far for that type's required fields.
   - Every value passes `llm.validate`: allowed enum values only; text and numbers grounded in the user's words;
     "unknown" only if the user said they don't know.
4. **Next question** (`form.next_action`): essentials first (type, building, floor), then details in the order
   safety → location → type fields → time, ≤ 2 fields per question, each asked once;
   - the question is written by Qwen (`llm.question`); it falls back to the taxonomy's wording if the LLM output
     isn't a single valid question or misses one of two fields (`--template-questions` forces the taxonomy wording);
   - `fire_smoke` gets its safety notice first;
   - `none` → out of scope;
   - if the type is still unclear: "Is it A, B or C?".
5. **Priority and routing** (`form.route`): default priority, per-type rules, "unknown safety field = worse case",
   notify list, SLA, and secondary incidents from keywords.
6. Each session is saved to `laya/out/sessions/<time>.json` (every turn: probabilities, sentences, extraction,
   LLM tokens and latency, action, form).

## Current results (base Qwen3-0.6B, not fine-tuned yet)
- **Self-test passes:** classifier 9/12 end-of-conversation, routing rules, policy order, validator.
- **Demos:** fire and lift-trapped complete in 3 questions with P1 routing; leave question → out of scope.
  Water leak and the power-outage correction stall on values base Qwen can't extract ("it's spreading across
  the floor", "meeting room Saturn"); the fine-tune targets exactly this.
- **Replay** (128 test conversations, the dataset's own user turns):
  - end-of-conversation type 85.9% (classifier alone 89.1%);
  - 53% of mentioned required fields extracted;
  - LLM 200 ms median per call (RTX 4050);
  - largest prompt 209 tokens; 97% of calls fit a 256-token device context.
  - Replay cannot measure completion: the dataset's user turns answer the dataset's questions, not ours.

## Fine-tuning the extractor (`laya/qwen_finetune.ipynb` + `laya/laya_qwen_colab.zip`)
1. Training prompts are built exactly as the assistant builds them (`llm.build_prompt`).
2. Qwen3-4B fills in values for fields our labels say were mentioned; the production validator keeps only valid ones.
3. Qwen3-0.6B is LoRA-fine-tuned, base vs fine-tuned is scored on the test conversations, and the notebook exports:
   - `qwen3-incident-q8_0.gguf` → `laya/install_qwen_ft.sh` → Ollama;
   - merged HF weights → Qualcomm AI Hub compile for the 8 Elite.

Dry-run checked on CPU with a tiny stand-in model.

## Path to the 8 Elite (not built yet)
- **Classifier:** the W8A16 DLC already runs on the NPU at 10.9 ms (`laya/BENCHMARK.md`).
- **Extractor:** the fine-tuned Qwen3-0.6B, compiled for the SM8750 through Qualcomm AI Hub (its
  `qwen3_0_6b` recipe lists the 8 Elite) and run with Genie/GenieX. Same prompts, validator and rules.
