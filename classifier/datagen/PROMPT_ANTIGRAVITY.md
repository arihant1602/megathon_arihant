# Task: hand-write a training dataset for an incident-report classifier

You are writing training data for a small on-device classifier used in a voice-driven
incident reporting app. Employees report facility problems by talking to an assistant.
They rarely say everything at once: the report arrives in pieces over several turns,
and the assistant asks follow-up questions for what is missing.

After every user turn, the classifier must output:
1. **type_known**: which incident type the conversation is about, using only what the
   user has said so far (or `undetermined` if that's not clear yet).
2. For every user sentence: **relevant** (does it carry report information, or is it filler?)
   and **fields** (which form fields the sentence gives information about).

You will write **about 1,300 conversations by hand**.

## Absolute rules

- **Write every conversation yourself, sentence by sentence.** Do NOT write or run any
  script, template, loop, string formatting or code that generates, combines,
  paraphrases or fills in text. No find-and-replace variations of earlier conversations.
  Every sentence must be newly written for the conversation it's in. Spending many tokens
  is expected and fine. Quality and variety matter more than speed.
- The only code you may run is the checker: `python3 datagen/validate_gen.py <path>`.
  Do not edit it, `incident_taxonomy.json`, or anything outside `datagen/out/`,
  `datagen/PLAN.md` and `datagen/PROGRESS.md`.
- **Plain English only.** No Hindi or Hinglish words, no Devanagari. Write natural, everyday
  spoken English, the kind a speech-to-text system produces: contractions, run-on
  sentences, informal wording. No emojis and no markdown inside the text.

## Read first

1. `incident_taxonomy.json`. Learn each of the 28 `incident_types` (`id`, `label`,
   `required` and `optional` fields, `rules`, `seed_phrases`) and the `field_library`
   (each field's meaning and allowed values). Seed phrases show the *topic* only. Do
   not copy their wording, and ignore the Hindi ones.
2. `datagen/examples/example.jsonl`: one correctly formatted conversation.
3. `datagen/validate_gen.py`: what the checker enforces.

## Record format (one JSON object per line, JSONL)

```json
{"id": "water_leak_001",
 "incident_type": "water_leak",
 "kind": "single",
 "split": "train",
 "scenario": "one line: who is reporting, what is really going on, how the conversation unfolds",
 "turns": [
   {"role": "user", "text": "<the sentences joined by single spaces>",
    "sentences": [{"text": "...", "relevant": false, "fields": []},
                  {"text": "...", "relevant": true,  "fields": ["water_spread"]}],
    "type_known": "water_leak"},
   {"role": "assistant", "text": "...", "asks": ["building", "floor"]},
   ...
 ]}
```

- `kind`: `single` (normal), `confusable` (deliberately resembles another type; add
  `"confused_with": "<other type id>"`), `multi` (two incidents in one report; add
  `"secondary_types": ["<type id>"]`), or `none` (not an incident; `incident_type` is `"none"`).
- Turns alternate user → assistant, starting with the user. Usually end with an
  assistant closing line (`asks: []`).
- `text` must be exactly the `sentences` texts joined with single spaces.

## Labelling rules (be strict and consistent)

**relevant = true** if the sentence tells us anything about the incident: what is
happening, where, when, how bad, who is affected, what has been done, or a direct
answer to the assistant's question (including "I don't know" answers and corrections
of earlier details).

**relevant = false (filler)**: greetings, thanks, apologies, small talk, feelings
with no facts ("this is so frustrating"), asides about their day, questions to the
assistant that add no facts, and restating something already said with nothing new.
Filler should sometimes mention words from *other* incident types, e.g. "the
cafeteria queue was insane today" inside an HVAC report. That teaches the model not to
classify by keyword. Aim for **20–35% filler sentences** overall.

**fields**: list every form field the sentence gives information about. Use the
location/common fields (`building`, `floor`, `space_type`, `space_id`, `landmark`,
`occurred_at`, `immediate_actions_taken`, `persons_involved`, `witnesses`, `asset_tag`)
plus the incident type's own `required`/`optional` fields. A sentence that answers "I
don't know" to a field still lists that field. Filler sentences always have `fields: []`.

**type_known**: the type a careful human could name with confidence after reading
*only the user turns so far*. Use `undetermined` while it is genuinely unclear ("hi,
I need to report something", "something's off in the washroom", "there's a weird smell").
Once known, it never goes back to `undetermined`. It *may* change to a different type
if the user's later words show an early reading was wrong (e.g. "burning smell" looked
like `fire_smoke`, then turns out to be a sparking socket → `electrical_hazard`).
The last user turn must equal `incident_type`. For `none` conversations, use `none`
once it is clear the user isn't reporting an incident.

## Writing realistic partial reports

Make the opening user turn deliberately incomplete in most conversations. Mix these
opening styles across each type (none should dominate):
- only *what* is wrong, no location or time
- only *where* they are, the problem comes next turn
- greeting or apology only ("hey, got a minute?")
- a vague symptom that doesn't yet reveal the type (aim for 20–30% of conversations
  starting `undetermined`)
- an emotional or panicked burst
- nearly complete (at most 1 in 6)

In later user turns, mix:
- short answers ("second floor", "no", "not sure")
- answering only half the question
- volunteering unasked details
- correcting an earlier detail ("actually it's the fourth floor")
- going off-topic mid-way
- asking the assistant a question back
- "I don't know" for safety questions

**Length**: user turns range from 2 words to 5+ sentences. Conversations have 1 to 5
user turns; aim for roughly 15% with 1, 35% with 2, 30% with 3, 20% with 4–5.

**Assistant turns** follow the taxonomy's `completion_policy`:
- ask at most 2 fields per question
- order: safety-critical fields first, then location, then the type's required fields,
  then `occurred_at`
- reword the field `question` texts naturally; don't paste them
- for `fire_smoke`, the assistant first gives the `user_prompt_override` safety message in
  its own words
- the assistant never asks for a field the user already gave

**Variety you must actively rotate** (track it in `datagen/PLAN.md`):
- Reporter: new intern, senior engineer, security guard, housekeeping staff, lab
  technician, receptionist, visitor, manager, night-shift worker, contractor.
- Tone: calm, rushed, annoyed, worried, joking, apologetic, terse.
- Location: about half use the taxonomy's buildings (`N`, `QRC`) and `space_types`,
  phrased many different ways; the other half use made-up places ("Block C", "the
  east wing", "level 2 of the annex", "the lab near the loading bay"), so the model
  doesn't tie a type to one building name. Sometimes give no location until asked.
- Time: exact clock times, "just now", "since lunch", "this morning", "about twenty
  minutes ago", "yesterday evening", or nothing at all.
- Field values: across each type, cover every allowed value of its enum fields,
  both true and false for its bool fields, and `unknown` answers.

Before writing a type, list in `datagen/PLAN.md` its planned conversations, one line
each (reporter, tone, opening style, situation, user-turn count), so no two are the same
situation. Before each new batch, reread that type's existing file and don't repeat an
opening, situation or distinctive phrase. The checker rejects any repeated sentence of
5+ words across the whole dataset and warns about similar openings.

## Confusable pairs (the most important data)

`confusable` conversations must sound like the neighbouring type at first, then resolve
to the true type from details. Write them for these groups:
- water_leak ↔ washroom_plumbing
- power_outage ↔ lighting_fault ↔ electrical_hazard
- fire_smoke ↔ electrical_hazard
- spill_cleanup ↔ chemical_spill
- network_outage ↔ device_fault ↔ conference_room_av
- access_control_fault ↔ lost_badge
- suspicious_activity ↔ theft_missing_asset
- structural_damage ↔ unsafe_condition
- lab_equipment_fault ↔ lab_utility_failure ↔ esd_issue
- pest_sighting ↔ cafeteria_issue ↔ washroom_hygiene
- cctv_fault ↔ access_control_fault
- injury_medical ↔ unsafe_condition

## How many

| What | Where | Count |
|---|---|---|
| Each of the 28 types: 32 `single` + 6 `confusable` | `datagen/out/train/<type_id>.jsonl` | 38 × 28 = 1,064 |
| `none`: wrong-app questions, chit-chat, general IT help, complaints that aren't facility incidents, testing the mic, asking how the app works | `datagen/out/train/none.jsonl` | 60 |
| `multi`: e.g. a pipe burst and someone slipped and hurt themselves; smoke from a printer plus a tripped alarm. `incident_type` is what the user mostly reports about; the second goes in `secondary_types`. | `datagen/out/train/multi.jsonl` | 40 |
| **Test set**, written last: 4 per type (at least 1 confusable) + 10 `none` + 6 `multi`, all `"split": "test"`. Use reporters, places and situations you did *not* use in training. | `datagen/out/test/<type_id>.jsonl`, `none.jsonl`, `multi.jsonl` | 128 |

Total ≈ **1,290 conversations**. IDs: `<file name>_<3-digit number>`, e.g. `hvac_issue_017`,
`none_004`, `test_hvac_issue_002`.

## Process (repeat until done)

1. Pick the next type. Write its plan lines in `datagen/PLAN.md`.
2. Write 10 conversations by hand and append them to its file.
3. Run `python3 datagen/validate_gen.py datagen/out`. Fix **every ERROR** by rewriting the
   affected conversation by hand. Fix WARNs about repeated openings and near-duplicates
   too.
4. Repeat steps 2–3 until the type has 38 conversations, then update
   `datagen/PROGRESS.md` (one line per finished file with its count).
5. Do all 28 types, then `none`, then `multi`, then the test set.
6. Final check: `python3 datagen/validate_gen.py datagen/out` must show **0 errors**, filler
   20–35%, every type 38 train + 4 test, and each type's first-turn-undetermined around
   20–30%. Paste the final checker summary into `datagen/PROGRESS.md`.

If you get interrupted, read `datagen/PROGRESS.md` and `datagen/PLAN.md` and continue
where you stopped. Never regenerate or overwrite finished files.
