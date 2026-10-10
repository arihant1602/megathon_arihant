# Fine-tuning dataset: incident-report classifier

*Form pack: Qualcomm HQ incident reporting (`incident_taxonomy.json`, 28 incident types). Dataset version 1.0, October 2026.*

## 1. What it is

A set of **multi-turn, hand-written English conversations** between an employee reporting a facility
problem and an assistant that asks follow-up questions. It trains the on-device classifier
(recommended model: Ettin-encoder-17m, trimmed vocab; see `bench/RESULTS.md`) to do three things after
every user turn:

| Task | Label | Where it comes from |
|---|---|---|
| **Incident type** | one of 28 type ids, `undetermined` (not clear yet) or `none` (not an incident) | `type_known` on each user turn |
| **Sentence relevance** | relevant / filler for each user sentence | `relevant` on each sentence |
| **Form fields mentioned** | multi-label: which form fields a sentence gives information about | `fields` on each sentence |

Filling in the field *values* is not the classifier's job (an LLM does that from the relevant sentences),
so values are not labelled.

### Size and composition

| Part | Count | Notes |
|---|---|---|
| 28 types × 32 `single` | 896 | normal reports |
| 28 types × 6 `confusable` | 168 | start out sounding like a neighbouring type, then resolve |
| `none` | 60 | not an incident: wrong app, chit-chat, IT help, mic tests |
| `multi` | 40 | two incidents in one report (primary + secondary type) |
| **Train total** | **1,164** | |
| Test (4 per type + 10 none + 6 multi) | 128 | unseen reporters, places and situations |
| **Total** | **1,292 conversations** | 2,976 user turns (one training sample each), 5,544 labelled sentences |

### How a training sample is built

One sample **per user turn**: the input is *all user turns so far* joined in order (assistant turns are
**not** included, see K2). Targets: that turn's `type_known`; relevance and fields for every sentence so
far (broadcast to the sentence's tokens). Split by **conversation**, never by turn, so no conversation is
in both train and validation.

### How it was made

- Two LLM authors wrote every conversation by hand, sentence by sentence, with no templates or generation
  scripts: Gemini (Antigravity) started five types (water_leak, washroom_plumbing, power_outage,
  electrical_hazard, lighting_fault) and Claude wrote everything else. In those five types, half of the
  conversations were rewritten by Claude with invented locations, so **every type contains both writing
  styles and both location kinds** (conditions L1, K3, S2). `datagen/compile.py` only converts Claude's
  plain-text format to JSONL.
- Every file passes `datagen/validate_gen.py` (format, label validity, no repeated sentence of 5+ words
  anywhere in the dataset) and is audited by `datagen/audit.py` (the balance conditions in section 3).
- A label review found that vague symptom openings ("the wall plate was scorching hot") had been marked as
  filler. 56 sentences were relabelled to follow rule R4; the pre-review copy is kept in `datagen/out.bak-gemini/`.

## 2. The case: Qualcomm HQ form pack

### What we assumed

The first form pack models an **incident reporting assistant for a Qualcomm campus**. Everything in it is
fixed configuration in `incident_taxonomy.json`; the AI never invents types, fields or routes.

- **Organisation:** departments modelled on Qualcomm's Facilities & Security Group (Facilities; Environment,
  Health & Safety; Security), with IT and R&D Labs as partner orgs.
- **Campus:** buildings identified by letter codes, **N** (floors 1–12) and **QRC** (floors 1–6); 14 space types
  (conference room, workstation bay, R&D lab, server/IDF room, pantry, cafeteria, washroom, lobby/reception,
  elevator lobby, stairwell, corridor, parking, loading dock, outdoor).
- **Demo values:** priorities, SLAs, floors, space types and technician rosters were chosen by the team. They are
  **not** Qualcomm internal data.
- **Division of work:** the **classifier** picks the incident type and the relevant sentences; an **LLM** fills only
  the fields listed for that type, quoting the transcript span each value came from; **code** applies the
  priority and routing rules.
- **Conversation policy:** at most 4 follow-up questions, at most 2 fields per question, asked in the order
  safety-critical → location → type-specific → time. "I don't know" is allowed; an unknown safety field is
  treated as the worse case.

### Taxonomy structure

| Section | Contents |
|---|---|
| `meta` | name, version, org model, design rule, demo-values note |
| `priorities` | P1–P4 with response SLA and meaning |
| `completion_policy` | follow-up limits and question order (above) |
| `location_model` | buildings, floors, space types; required: building, floor, space_id |
| `common_fields` | fields every report has: building, floor, space_type, space_id, landmark, occurred_at, immediate_actions_taken, persons_involved, witnesses, asset_tag, plus system fields (incident_type, description, reporter) |
| `field_library` | ~50 type-specific fields: type (enum / bool / string / int / time), allowed values, safety flag, question text |
| `incident_types` | the 28 types below: id, label, department, role, default priority, required/optional fields, rules, seed phrases |
| `global_rules` | unknown safety field → worse case; injury or fire words → add secondary incident; classifier confidence < 0.6 → ask the user to confirm between top-2 types |
| `departments`, `assignment` | who gets the ticket, when it is due, where it is on the map |

### Priorities

| | Label | Response SLA | Meaning |
|---|---|---|---|
| P1 | Critical | 15 min | Risk to life, active hazard, or site-wide outage. Page on-call immediately. |
| P2 | High | 60 min | Spreading damage, security exposure, or work stopped for many people. |
| P3 | Medium | 240 min | Contained problem affecting a room or small group. |
| P4 | Low | 1440 min | Cosmetic or convenience issue. Next business day. |

### Departments

| ID | Department | Roles |
|---|---|---|
| FAC | Facilities Engineering | Plumbing Technician, Electrical Technician, HVAC Technician, Civil & Interiors Technician, Elevator Service (vendor) |
| HWS | Housekeeping & Workplace Services | Housekeeping Staff, Cafeteria & Pantry Operations, Pest Control (vendor) |
| SEC | Global Security | Security Officer, Security Systems Technician (access control, CCTV), GSOC Analyst (Global Security Operations Center) |
| EHS | Environment, Health & Safety | EHS Engineer, Emergency Response Team / First Aid |
| ITW | IT & Workplace Technology | IT Support Technician, Conference Room AV Technician, Network Engineer |
| LAB | R&D Lab Operations | Lab Technician, Lab Facilities Engineer (bench power, UPS, chillers) |

### The 28 defined incident types

`*` = safety-critical field (asked first). "Confusable with" = the groups the dataset deliberately writes
look-alike conversations for. Rules in the taxonomy can raise the priority (for example a water leak near
electrics becomes P1, a snake sighting becomes P1).

| # | ID | Label | Dept | Default | Required fields | Optional | Confusable with |
|---|---|---|---|---|---|---|---|
| 1 | water_<wbr>leak | Water leak | FAC | P3 | `leak_source`, `water_spread`*, `near_electrical`* | `immediate_actions_taken` | washroom_plumbing |
| 2 | washroom_<wbr>plumbing | Washroom plumbing fault | FAC | P3 | `fixture_type`, `plumbing_issue` | – | water_leak |
| 3 | power_<wbr>outage | Power outage | FAC | P3 | `outage_scope`, `critical_equipment_affected`* | – | electrical_hazard, lighting_fault |
| 4 | electrical_<wbr>hazard | Electrical hazard | FAC | P1 | `hazard_kind`, `smoke_present`* | `immediate_actions_taken` | fire_smoke, lighting_fault, power_outage |
| 5 | lighting_<wbr>fault | Lighting fault | FAC | P4 | `lighting_scope` | – | electrical_hazard, power_outage |
| 6 | hvac_<wbr>issue | Temperature / HVAC issue | FAC | P3 | `hvac_condition`, `affected_scope` | – | – |
| 7 | elevator_<wbr>fault | Elevator fault | FAC | P2 | `elevator_id`, `persons_trapped`* | – | – |
| 8 | structural_<wbr>damage | Building damage | FAC | P3 | `damage_kind`, `falling_or_injury_risk`* | – | unsafe_condition |
| 9 | furniture_<wbr>fixture | Furniture / fixture repair | FAC | P4 | `furniture_item` | – | – |
| 10 | spill_<wbr>cleanup | Spill (non-hazardous) | HWS | P3 | `spill_substance`*, `slip_hazard`* | – | chemical_spill |
| 11 | washroom_<wbr>hygiene | Washroom hygiene / supplies | HWS | P4 | `hygiene_issue` | – | cafeteria_issue, pest_sighting |
| 12 | pest_<wbr>sighting | Pest sighting | HWS | P3 | `pest_kind` | – | cafeteria_issue, washroom_hygiene |
| 13 | cafeteria_<wbr>issue | Cafeteria / pantry issue | HWS | P3 | `cafeteria_issue`, `food_safety_concern`* | – | pest_sighting, washroom_hygiene |
| 14 | access_<wbr>control_<wbr>fault | Door / badge reader fault | SEC | P3 | `door_state`, `secure_area`* | – | cctv_fault, lost_badge |
| 15 | lost_<wbr>badge | Lost badge | SEC | P3 | `last_seen` | – | access_control_fault |
| 16 | suspicious_<wbr>activity | Suspicious person / tailgating | SEC | P2 | `person_description`, `behavior`* | `witnesses` | theft_missing_asset |
| 17 | theft_<wbr>missing_<wbr>asset | Theft / missing asset | SEC | P3 | `asset_kind`, `contains_confidential`* | `asset_tag` | suspicious_activity |
| 18 | cctv_<wbr>fault | CCTV camera fault | SEC | P3 | `camera_location` | – | access_control_fault |
| 19 | injury_<wbr>medical | Injury / medical emergency | EHS | P2 | `injury_kind`*, `person_conscious`*, `persons_count` | `persons_involved`, `witnesses`, `immediate_actions_taken` | unsafe_condition |
| 20 | fire_<wbr>smoke | Fire / smoke | EHS | P1 | `fire_visible`*, `evacuation_status` | `alarm_triggered` | electrical_hazard |
| 21 | chemical_<wbr>spill | Chemical / hazardous spill | EHS | P2 | `substance_name`*, `spill_quantity`, `persons_exposed`* | – | spill_cleanup |
| 22 | unsafe_<wbr>condition | Near miss / unsafe condition | EHS | P3 | `hazard_description` | `witnesses` | injury_medical, structural_damage |
| 23 | network_<wbr>outage | Network outage | ITW | P3 | `network_scope`, `connection_type` | – | conference_room_av, device_fault |
| 24 | conference_<wbr>room_<wbr>av | Conference room AV fault | ITW | P4 | `av_equipment`, `meeting_in_progress` | – | device_fault, network_outage |
| 25 | device_<wbr>fault | Printer / shared device fault | ITW | P4 | `device_kind` | `asset_tag` | conference_room_av, network_outage |
| 26 | lab_<wbr>equipment_<wbr>fault | Lab equipment fault | LAB | P3 | `equipment_kind`, `test_in_progress` | `asset_tag` | esd_issue, lab_utility_failure |
| 27 | esd_<wbr>issue | ESD control issue | LAB | P3 | `esd_issue` | – | lab_equipment_fault, lab_utility_failure |
| 28 | lab_<wbr>utility_<wbr>failure | Lab utility failure | LAB | P2 | `lab_utility`, `test_in_progress` | – | esd_issue, lab_equipment_fault |

Two extra classifier labels exist outside the taxonomy: **`undetermined`** (the user hasn't said enough
yet) and **`none`** (not a facility incident at all).

## 3. Conditions the data must meet (anti-overfitting, anti-imbalance)

Checked automatically by `python3 datagen/audit.py` unless marked *manual*.

### Class balance
- **C1** Same number of training conversations per type (max/min ≤ 1.2). Target 38 each.
- **C2** Enough out-of-scope (`none` ≥ 60) and `multi` (≥ 40) examples, so the model can say "not an incident".
- **C3** ≥ 6 confusable conversations per type, covering the 12 confusable groups in both directions.
- **C4** *(manual)* `undetermined` is ~25–30% of first turns; the model must learn to wait, not guess.

### Relevance balance
- **R1** Filler is 20–35% of all sentences.
- **R2** Each type's filler rate is within 12–40%: if one type had far more filler, the filler rate would hint at the type.
- **R3** *(manual)* Filler sometimes mentions words from **other** types ("the cafeteria queue was insane" inside an HVAC report), so the model can't classify by keyword alone.
- **R4** *(manual)* One relevance rule everywhere: symptoms, places, times, actions and "I don't know" answers are relevant; feelings, greetings, small talk and wrong guesses are filler.

### Structure must not give the type away
- **S1** Mean user turns per type within ±0.6 of the overall mean.
- **S2** Mean words per sentence per type within ±25% of the overall mean.
- **S3** First-turn `undetermined` rate per type between 15% and 40%.
- **S4** Turn mix close to 15% one-turn / 35% two / 30% three / 20% four-plus.
- **S5** *(manual)* Opening styles rotate per type: only *what*, only *where*, greeting, vague symptom, panicked burst, nearly complete (≤ 1 in 6).

### Location, people and wording must not give the type away
- **L1** Qualcomm buildings (N, QRC) in 30–75% of each type's conversations; the rest use invented places.
- **L2** *(manual)* Reporters (intern, engineer, guard, housekeeping, lab tech, receptionist, visitor, manager, night shift, contractor) and tones rotate across every type.
- **K1** Filler sentences alone must not predict the type (naive Bayes accuracy ≤ 3× chance).
- **K2** The assistant's questions *do* reveal the type (about 70% accuracy from assistant text alone), so **assistant text must never be model input**.
- **K3** Writing style must not reveal the type. With two authors, each type should contain both styles.
- **D1** No repeated sentence of 5+ words anywhere, and no near-duplicate first turns (`validate_gen.py`).

### Field labels
- **F1** Every required field of a type labelled ≥ 10 times in that type.
- **F2** *(manual)* Across each type, every enum value, both values of each yes/no field, and "don't know" answers appear.

### Train / test separation
- **T1** No test sentence (4+ words) appears in train.
- **T2** Test set has ≥ 4 conversations per type, with reporters, places and situations not used in training.
- **T3** *(manual)* Split by conversation; calibration (temperature scaling) uses a validation split carved from train, never the test set.

### Acceptance

The dataset is accepted for fine-tuning only when `validate_gen.py` reports **0 errors** and `audit.py`
reports **0 FAIL**. Warnings must be reviewed and either fixed or explained.

| Check | Required result | Result (v1.0) |
|---|---|---|
| Format and labels (`validate_gen.py`) | 0 errors | **0 errors, 0 warnings** |
| C1–C3 | 28 × 38 train, 6 confusables each, `none` 60, `multi` 40 | **pass**: 38 per type, 6 confusables each, 60 / 40 |
| R1, R2 | filler 20–35% overall, 12–40% per type | **pass**: 23% overall, 15–34% per type |
| S1, S2, S3 | per-type turns, sentence length, undetermined openings within bounds | **pass**: 2.0–2.6 turns, sentence length within ±25% of 7.7 words, 16–29% undetermined openings |
| S4 | ≥ 10% one-turn, ≥ 12% four-plus-turn | **pass**: 15% / 12% |
| L1 | N/QRC in 30–75% of each type | **pass** |
| K1 | filler-only naive Bayes ≤ 3× chance | **pass**: 7% vs 4% chance |
| K3 | both styles in every Gemini-started type | **pass**: 50% / 50% in all five |
| F1 | required fields ≥ 10 labels per type | **pass** |
| T1, T2 | 128 test conversations, no shared sentence with train | **pass**: 128, 0 shared |

**Audit summary: 18 PASS, 0 WARN, 0 FAIL.** User turns per conversation: 1 → 179, 2 → 702, 3 → 277,
4 → 108, 5 → 26.

## 4. Assumptions

1. **Input is clean, complete English text** from speech recognition: no Hindi/Hinglish, no ASR errors.
2. **The classifier sees user turns only.** The assistant's questions follow the taxonomy's completion policy, but they are not model input.
3. **Synthetic conversations stand in for real reports.** LLM-written dialogue approximates how employees talk; it has not been compared against real reports.
4. **Balanced classes.** Every type gets the same amount of data. Real incident frequencies differ (lighting faults are far more common than snakes), so deployment may need prior adjustment.
5. **One primary type per conversation.** `multi` conversations have a secondary type, but the type head predicts one label; secondaries are handled by the taxonomy's global rules.
6. **`type_known` is a judgement**: "what a careful human could name with confidence from the user's words so far". Different annotators would sometimes disagree on the turn where it switches.
7. **Fields are mentions, not values.** A sentence is tagged with a field if it says anything about it, including "I don't know".
8. **The taxonomy is fixed** (28 types, field library, demo location model). Changing it means relabelling.

## 5. Limitations

- **Small.** 38 conversations (~95 turn samples) per type. Enough to fine-tune a pretrained encoder, not to train one from scratch (one reason the LNN was rejected).
- **Two LLM authors.** Mixing both styles inside every type removes style as a shortcut for the type, but both authors are LLMs: real employees will phrase things in ways neither wrote.
- **No independent label check.** The writer labels its own sentences; there is no second annotator and no agreement score. One systematic error (symptoms marked as filler) was found and fixed during review; others may remain.
- **Test set shares the authors.** Accuracy on it will be optimistic. A small **human-written test set** (team members, real phrasing, ideally spoken then transcribed) is needed before trusting any number.
- **No speech noise, accents or code-switching**, although requirement I1 mentions Hinglish.
- **Labels are coarse for relevance.** Relevance is per sentence; long run-on sentences mixing filler and facts are labelled relevant as a whole.
- **Old data excluded.** `data/*.jsonl` (198 templated conversations, 11 types, partly Hindi) is not used for training; it was only used as benchmark input.
- **Pack-specific.** Everything here applies to the Qualcomm HQ pack. A new form pack needs its own dataset that meets the same conditions.

## 6. Files

| Path | What |
|---|---|
| `datagen/out/train/<type>.jsonl`, `datagen/out/test/` | the dataset |
| `datagen/src/` | Claude's hand-written source text |
| `datagen/PLAN.md`, `datagen/PROGRESS.md` | per-conversation plan and progress log |
| `datagen/validate_gen.py` | format and label checker |
| `datagen/audit.py` | balance and shortcut audit (section 3) |
| `datagen/PROMPT_ANTIGRAVITY.md` | the writing rules both authors follow |
