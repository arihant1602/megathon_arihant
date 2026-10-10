#!/usr/bin/env python3
"""Validate generated conversations against incident_taxonomy.json.
Usage: python validate.py data/water_leak.jsonl   (one file)
       python validate.py data/                    (all files)
Prints every error. Exit code 0 only if everything passes."""
import json, sys, os, re
from collections import Counter

TAX = json.load(open(os.path.join(os.path.dirname(os.path.abspath(__file__)), "incident_taxonomy.json")))
LIB = TAX["field_library"]
TYPES = {t["id"]: t for t in TAX["incident_types"]}
BUILDINGS = {b["code"]: b["floors"] for b in TAX["location_model"]["buildings"]}
SPACE_TYPES = set(TAX["location_model"]["space_types"])
LOC = ["building", "floor", "space_type", "space_id", "occurred_at"]
OPT_COMMON = ["landmark", "immediate_actions_taken", "persons_involved", "witnesses", "asset_tag"]
RANK = {"P1": 1, "P2": 2, "P3": 3, "P4": 4}
SLOTS = [  # (lang_style, opening) for slot 1..18, must match the prompt
    ("english", "complete"), ("english", "complete"), ("english", "complete"),
    ("english", "missing"), ("english", "missing"), ("english", "missing"),
    ("english", "missing"), ("english", "missing"), ("english", "missing"),
    ("english", "rambling"), ("english", "rambling"), ("english", "rambling"),
    ("hindi_translated", "complete"), ("hindi_translated", "missing"),
    ("hindi_translated", "missing"), ("hindi_translated", "rambling"),
    ("hinglish", "missing"), ("hinglish", "complete"),
]
seen_openings = set()

def words(s): return len(re.findall(r"\S+", s))

def check_value(f, v):
    if f == "building": return v in BUILDINGS
    if f == "space_type": return v in SPACE_TYPES
    if f == "floor": return isinstance(v, int)
    if f in ("space_id", "occurred_at", "landmark", "immediate_actions_taken", "asset_tag"):
        return isinstance(v, str) and v.strip() != ""
    if f in ("persons_involved", "witnesses"):
        return isinstance(v, list) and all(isinstance(x, str) for x in v)
    spec = LIB[f]
    if v == "unknown": return True
    t = spec["type"]
    if t == "enum": return v in spec["values"]
    if t == "bool": return isinstance(v, bool)
    if t == "int": return isinstance(v, int) and not isinstance(v, bool)
    return isinstance(v, str) and v.strip() != ""

def rule_matches(rule, fields):
    w = rule["when"]; f = w["field"]; v = fields.get(f)
    safety = LIB.get(f, {}).get("safety", False)
    if w["op"] == "eq":
        return v == w["value"] or (v == "unknown" and safety)
    if w["op"] == "in":
        return v in w["value"] or (v == "unknown" and safety)
    return False

def expected_priority(t, fields):
    p = t["default_priority"]
    for r in t["rules"]:
        if "set_priority" in r and rule_matches(r, fields) and RANK[r["set_priority"]] < RANK[p]:
            p = r["set_priority"]
    return p

def validate_line(line, n, type_id, errs):
    def e(msg): errs.append(f"line {n}: {msg}")
    try:
        c = json.loads(line)
    except Exception as ex:
        return e(f"not valid JSON ({ex})")
    keys = {"id", "incident_type", "slot", "lang_style", "opening", "turns", "final_fields", "final_priority"}
    if set(c) != keys: return e(f"keys must be exactly {sorted(keys)}, got {sorted(c)}")
    t = TYPES.get(c["incident_type"])
    if not t: return e(f"unknown incident_type {c['incident_type']}")
    if c["incident_type"] != type_id: e(f"incident_type {c['incident_type']} does not match file {type_id}")
    if not isinstance(c["slot"], int) or isinstance(c["slot"], bool) or not 1 <= c["slot"] <= 18: return e("slot must be int 1-18")
    if c["id"] != f"{type_id}_{c['slot']:02d}": e(f"id must be {type_id}_{c['slot']:02d}")
    want_style, want_open = SLOTS[c["slot"] - 1]
    if c["lang_style"] != want_style: e(f"slot {c['slot']} must be lang_style {want_style}")
    if c["opening"] != want_open: e(f"slot {c['slot']} must be opening {want_open}")

    turns = c["turns"]
    if not isinstance(turns, list) or len(turns) < 2 or len(turns) % 2: return e("turns must be an even-length list, at least 2")
    for i, tu in enumerate(turns):
        role = "user" if i % 2 == 0 else "assistant"
        if tu.get("role") != role: e(f"turn {i+1} must be role {role}")
        if not isinstance(tu.get("text"), str) or not tu["text"].strip(): e(f"turn {i+1} empty text")
        if role == "user":
            s = tu.get("sentences")
            if not isinstance(s, list) or not s or any(set(x) != {"text", "relevant"} or not isinstance(x["relevant"], bool) for x in s):
                e(f"turn {i+1} sentences must be a list of {{text, relevant(bool)}}")
            if set(tu) != {"role", "text", "sentences"}: e(f"turn {i+1} user keys must be role,text,sentences")
        else:
            if set(tu) != {"role", "text", "asks"}: e(f"turn {i+1} assistant keys must be role,text,asks")
            last = i == len(turns) - 1
            asks = tu.get("asks", [])
            allowed = set(t["required"]) | {"building", "floor", "space_id", "occurred_at"}
            if last:
                if asks: e("final assistant turn must have asks: []")
                if words(tu["text"]) > 15: e(f"closing line over 15 words: {tu['text']!r}")
            else:
                if not 1 <= len(asks) <= 2: e(f"turn {i+1} asks must list 1-2 fields")
                bad = [a for a in asks if a not in allowed]
                if bad: e(f"turn {i+1} asks for fields not allowed for this type: {bad}")
                override = t.get("user_prompt_override")
                if not (i == 1 and override and tu["text"].startswith(override)) and words(tu["text"]) > 12:
                    e(f"turn {i+1} assistant over 12 words: {tu['text']!r}")
    if t["id"] == "fire_smoke" and len(turns) >= 2 and not turns[1]["text"].startswith(t["user_prompt_override"]):
        e("fire_smoke first assistant turn must start with the exact user_prompt_override")

    first = turns[0]["text"].strip().lower()
    if first in seen_openings: e("duplicate opening message")
    seen_openings.add(first)
    irrelevant = sum(1 for tu in turns if tu["role"] == "user" for x in tu.get("sentences", []) if x.get("relevant") is False)
    if c["opening"] == "complete" and len(turns) != 2: e("complete opening must have exactly 2 turns (user, assistant closing)")
    if c["opening"] in ("missing", "rambling") and len(turns) < 4: e(f"{c['opening']} opening must have at least 4 turns")
    if c["opening"] == "rambling" and irrelevant == 0: e("rambling opening needs at least one sentence with relevant: false")
    if c["opening"] != "rambling" and irrelevant > 0: e("only rambling openings may have relevant: false sentences")

    ff = c["final_fields"]
    if not isinstance(ff, dict): return e("final_fields must be an object")
    allowed_f = set(LOC) | set(OPT_COMMON) | set(t["required"]) | set(t["optional"])
    for f in ff:
        if f not in allowed_f: e(f"final_fields has field not allowed for this type: {f}")
        elif not check_value(f, ff[f]): e(f"bad value for {f}: {ff[f]!r}")
    for f in LOC + t["required"]:
        if f not in ff: e(f"final_fields missing required field: {f}")
    if ff.get("building") in BUILDINGS and isinstance(ff.get("floor"), int) and ff["floor"] not in BUILDINGS[ff["building"]]:
        e(f"floor {ff['floor']} does not exist in building {ff['building']}")
    if t["id"] == "spill_cleanup" and ff.get("spill_substance") == "unknown":
        e("spill_cleanup must not use spill_substance 'unknown' (that reroutes to chemical_spill)")
    exp = expected_priority(t, ff)
    if c["final_priority"] != exp: e(f"final_priority should be {exp}, got {c['final_priority']}")
    return c

def validate_file(path):
    type_id = os.path.splitext(os.path.basename(path))[0]
    errs, slots = [], Counter()
    if type_id not in TYPES:
        return [f"file name {type_id} is not an incident_type id"], slots
    lines = [l for l in open(path, encoding="utf-8").read().splitlines() if l.strip()]
    if len(lines) != 18: errs.append(f"file must have exactly 18 lines, has {len(lines)}")
    for n, line in enumerate(lines, 1):
        c = validate_line(line, n, type_id, errs)
        if isinstance(c, dict) and isinstance(c.get("slot"), int): slots[c["slot"]] += 1
    missing = [s for s in range(1, 19) if slots[s] != 1]
    if missing: errs.append(f"each slot 1-18 must appear exactly once; problem slots: {missing}")
    return errs, slots

if __name__ == "__main__":
    target = sys.argv[1] if len(sys.argv) > 1 else "data"
    files = sorted(os.path.join(target, f) for f in os.listdir(target) if f.endswith(".jsonl")) if os.path.isdir(target) else [target]
    total_bad = 0
    for p in files:
        errs, _ = validate_file(p)
        status = "PASS" if not errs else f"FAIL ({len(errs)} errors)"
        print(f"{os.path.basename(p)}: {status}")
        for x in errs: print("   -", x)
        total_bad += len(errs)
    if os.path.isdir(target):
        have = {os.path.splitext(os.path.basename(p))[0] for p in files}
        miss = sorted(set(TYPES) - have)
        if miss: print("MISSING FILES:", miss); total_bad += len(miss)
    print("ALL PASS" if total_bad == 0 else f"TOTAL ERRORS: {total_bad}")
    sys.exit(0 if total_bad == 0 else 1)
