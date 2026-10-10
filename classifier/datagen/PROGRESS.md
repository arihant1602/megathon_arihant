# Datagen Progress

| Type / File | Target | Completed | Status |
|---|---|---|---|
| train/water_leak.jsonl | 38 | 38 | Completed |
| train/washroom_plumbing.jsonl | 38 | 38 | Completed |
| train/power_outage.jsonl | 38 | 38 | Completed |
| train/electrical_hazard.jsonl | 38 | 38 | Completed |
| train/lighting_fault.jsonl | 38 | 38 | Completed |
| train/hvac_issue.jsonl | 38 | 38 | Completed |
| train/elevator_fault.jsonl | 38 | 38 | Completed |
| train/structural_damage.jsonl | 38 | 38 | Completed |
| train/furniture_fixture.jsonl | 38 | 38 | Completed |
| train/spill_cleanup.jsonl | 38 | 38 | Completed |
| train/washroom_hygiene.jsonl | 38 | 38 | Completed |
| train/pest_sighting.jsonl | 38 | 38 | Completed |
| train/cafeteria_issue.jsonl | 38 | 38 | Completed |
| train/access_control_fault.jsonl | 38 | 38 | Completed |
| train/lost_badge.jsonl | 38 | 38 | Completed |
| train/suspicious_activity.jsonl | 38 | 38 | Completed |
| train/theft_missing_asset.jsonl | 38 | 38 | Completed |
| train/cctv_fault.jsonl | 38 | 38 | Completed |
| train/injury_medical.jsonl | 38 | 38 | Completed |
| train/fire_smoke.jsonl | 38 | 38 | Completed |
| train/chemical_spill.jsonl | 38 | 38 | Completed |
| train/unsafe_condition.jsonl | 38 | 38 | Completed |
| train/network_outage.jsonl | 38 | 38 | Completed |
| train/conference_room_av.jsonl | 38 | 38 | Completed |
| train/device_fault.jsonl | 38 | 38 | Completed |
| train/lab_equipment_fault.jsonl | 38 | 38 | Completed |
| train/esd_issue.jsonl | 38 | 38 | Completed |
| train/lab_utility_failure.jsonl | 38 | 38 | Completed |
| train/none.jsonl | 60 | 60 | Completed |
| train/multi.jsonl | 40 | 40 | Completed |
| test/*.jsonl (all types + none + multi) | 128 | 128 | Completed |

Total Target: 1,292 conversations

## 2026-10-10 20:10 handover: Gemini -> Claude
- Gemini wrote water_leak, washroom_plumbing, power_outage, electrical_hazard, lighting_fault (190 conversations).
- Claude relabelled 56 sentences in those files: vague-symptom openings had been marked filler although they describe
  the incident (rule: symptoms/locations are relevant; feelings and wrong guesses stay filler). Backup: datagen/out.bak-gemini/.
- From hvac_issue on, Claude hand-writes conversations in datagen/src/<split>/<file>.txt (compact format);
  datagen/compile.py only converts that format to JSONL (no text generation).

## 2026-10-10 audit (datagen/audit.py) — open items
- R1 filler 19% -> write remaining batches at ~28% filler.
- S4 too few 1-turn and 4+-turn conversations -> skew remaining batches.
- L1 Gemini types use N/QRC in 97-100% of conversations; K3 author style is 100% detectable.
  Fix: after all types are written, Claude rewrites 19 of 38 conversations in each Gemini type
  (water_leak, washroom_plumbing, power_outage, electrical_hazard, lighting_fault) with invented locations.

## 2026-10-10 — DATASET COMPLETE (v1.0)
- All 28 types × 38 train, none 60, multi 40, test 128 = 1,292 conversations, 2,976 user turns, 5,544 sentences.
- Gemini-started types: 19 of 38 conversations each rewritten by Claude (datagen/rewrite/, spliced with datagen/splice.py).
- 28 conversations extended by one exchange to reach the 4+ turn share.
- validate_gen.py: 0 errors, 0 warnings. audit.py: 18 PASS, 0 WARN, 0 FAIL.
- Doc: datagen/DATASET.md -> datagen/doc/fine_tuning_dataset.pdf
