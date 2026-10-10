#!/usr/bin/env python3
"""Check hand-written classifier conversations against incident_taxonomy.json.
Usage: python3 datagen/validate_gen.py datagen/out            (all files, recursive)
       python3 datagen/validate_gen.py datagen/out/water_leak.jsonl
Prints ERRORs (must fix), WARNs (should fix) and coverage stats. Exit 0 only with zero ERRORs.
This script only checks; it never writes data."""
import json, os, re, sys
from pathlib import Path
from collections import Counter, defaultdict

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
TAX = json.loads((Path(ROOT).parent / "app/src/main/assets/incident_taxonomy.json").read_text(encoding="utf-8"))
TYPES = {t["id"]: t for t in TAX["incident_types"]}
LOCATION_FIELDS = ["building", "floor", "space_type", "space_id", "landmark", "occurred_at",
                   "immediate_actions_taken", "persons_involved", "witnesses", "asset_tag"]
KINDS = {"single", "confusable", "multi", "none"}
HINDI = re.compile(r"\b(hai|hain|nahi|nahin|kya|mein|paani|raha|rahi|gaya|gayi|yaar|accha|acha|theek|bhai|kuch|abhi|bahut|wala|wali|chal|dekho)\b", re.I)
DEVANAGARI = re.compile(r"[ऀ-ॿ]")

def allowed_fields(type_ids):
    f = set(LOCATION_FIELDS)
    for t in type_ids:
        f |= set(TYPES[t]["required"]) | set(TYPES[t].get("optional", []))
    return f

def norm(s):
    return re.sub(r"[^a-z0-9 ]", "", re.sub(r"\s+", " ", s.lower())).strip()

def load(paths):
    for p in paths:
        files = [os.path.join(d, f) for d, _, fs in os.walk(p) for f in sorted(fs) if f.endswith(".jsonl")] if os.path.isdir(p) else [p]
        for fp in sorted(files):
            for i, line in enumerate(open(fp, encoding="utf-8"), 1):
                if line.strip():
                    yield fp, i, line

def main(paths):
    errors, warns = [], []
    seen_ids, sentence_seen = {}, {}
    openings = Counter()
    first_turns = defaultdict(list)
    stats = defaultdict(Counter)
    rel = Counter(); rel_by_type = defaultdict(Counter)
    field_cov = defaultdict(Counter)
    turn_hist = Counter(); undetermined_first = Counter()

    for fp, ln, line in load(paths):
        where = f"{os.path.relpath(fp, ROOT)}:{ln}"
        E = lambda m: errors.append(f"{where}: {m}")
        try:
            r = json.loads(line)
        except json.JSONDecodeError as e:
            E(f"bad JSON ({e})"); continue
        rid, it, kind = r.get("id"), r.get("incident_type"), r.get("kind")
        if rid in seen_ids: E(f"duplicate id {rid} (first at {seen_ids[rid]})")
        seen_ids[rid] = where
        if kind not in KINDS: E(f"kind must be one of {sorted(KINDS)}, got {kind!r}")
        if r.get("split") not in ("train", "test"): E("split must be 'train' or 'test'")
        if not isinstance(r.get("scenario"), str) or len(r.get("scenario", "")) < 15: E("scenario must be a one-line description")
        if kind == "none":
            if it != "none": E("kind 'none' needs incident_type 'none'")
            types_here = []
        else:
            if it not in TYPES: E(f"unknown incident_type {it!r}"); continue
            types_here = [it]
        sec = r.get("secondary_types", [])
        if kind == "multi":
            if not sec or any(s not in TYPES or s == it for s in sec): E("multi needs secondary_types: other valid type ids")
            types_here += [s for s in sec if s in TYPES]
        elif sec: E("secondary_types only allowed when kind is 'multi'")
        if kind == "confusable":
            cw = r.get("confused_with")
            if cw not in TYPES or cw == it: E("confusable needs confused_with: a different valid type id")
        allowed = allowed_fields(types_here)
        valid_known = set(TYPES) | {"undetermined", "none"}

        turns = r.get("turns", [])
        if not turns or turns[0].get("role") != "user": E("first turn must be role 'user'"); continue
        user_turns, known_seq = 0, []
        for k, t in enumerate(turns):
            exp = "user" if k % 2 == 0 else "assistant"
            if t.get("role") != exp: E(f"turn {k}: expected role {exp}"); break
            if exp == "assistant":
                asks = t.get("asks", [])
                if not t.get("text", "").strip(): E(f"turn {k}: empty assistant text")
                if len(asks) > 2: E(f"turn {k}: assistant asks more than 2 fields")
                bad = [a for a in asks if a not in allowed]
                if bad: E(f"turn {k}: asks unknown fields {bad}")
                continue
            user_turns += 1
            sents = t.get("sentences", [])
            if not sents: E(f"turn {k}: user turn has no sentences"); continue
            joined = " ".join(s.get("text", "").strip() for s in sents)
            if re.sub(r"\s+", " ", t.get("text", "")).strip() != joined:
                E(f"turn {k}: text is not exactly the sentences joined by single spaces")
            if DEVANAGARI.search(t.get("text", "")) or HINDI.search(t.get("text", "")):
                E(f"turn {k}: non-English words found (input must be plain English)")
            for s in sents:
                if not isinstance(s.get("relevant"), bool): E(f"turn {k}: sentence missing boolean 'relevant'")
                fl = s.get("fields", [])
                bad = [f for f in fl if f not in allowed]
                if bad: E(f"turn {k}: sentence fields not valid for this type: {bad}")
                if s.get("relevant") is False and fl: E(f"turn {k}: filler sentence must have fields []")
                rel[s.get("relevant")] += 1; rel_by_type[it][s.get("relevant")] += 1
                for f in fl: field_cov[it][f] += 1
                n = norm(s.get("text", ""))
                if len(n.split()) >= 5:
                    if n in sentence_seen: E(f"turn {k}: sentence repeated from {sentence_seen[n]}: \"{s['text']}\"")
                    else: sentence_seen[n] = where
            tk = t.get("type_known")
            if tk not in valid_known: E(f"turn {k}: type_known must be a type id, 'undetermined' or 'none'")
            known_seq.append(tk)
        if not known_seq: continue
        if kind == "none" and any(x not in ("none", "undetermined") for x in known_seq):
            E("kind 'none' conversation must only use type_known 'none'/'undetermined'")
        if kind != "none":
            if known_seq[-1] != it: E(f"last user turn type_known must be {it}")
            first_known = next((i for i, x in enumerate(known_seq) if x == it), None)
            if first_known is not None and "undetermined" in known_seq[first_known:]:
                E("type_known went back to 'undetermined' after the type was known")
        if known_seq[0] == "undetermined": undetermined_first[it] += 1
        first = turns[0].get("text", "")
        openings[" ".join(norm(first).split()[:4])] += 1
        first_turns[it].append((where, set(zip(*[norm(first).split()[i:] for i in range(3)]))))
        stats[it][r.get("split")] += 1; stats[it][kind] += 1
        turn_hist[user_turns] += 1

    for o, c in openings.items():
        if c > 3 and o: warns.append(f"opening '{o} ...' used {c} times (vary first words)")
    for it, lst in first_turns.items():
        for i in range(len(lst)):
            for j in range(i + 1, len(lst)):
                a, b = lst[i][1], lst[j][1]
                if a and b and len(a & b) / len(a | b) > 0.5:
                    warns.append(f"near-duplicate first turns: {lst[i][0]} vs {lst[j][0]}")

    total = sum(sum(v for k, v in c.items() if k in ("train", "test")) for c in stats.values())
    print(f"== {total} conversations, {sum(n * c for n, c in turn_hist.items())} user turns")
    print("user turns per conversation:", dict(sorted(turn_hist.items())))
    if rel: print(f"filler ratio: {rel[False] / (rel[True] + rel[False]):.0%} (target 20-35%)")
    print(f"{'type':24s} train test single conf multi none  filler  first-turn-undetermined")
    for it in sorted(stats):
        c, rc = stats[it], rel_by_type[it]
        fr = rc[False] / max(1, rc[True] + rc[False])
        n = c["train"] + c["test"]
        print(f"{it:24s} {c['train']:5d} {c['test']:4d} {c['single']:6d} {c['confusable']:4d} {c['multi']:5d} {c['none']:4d}  {fr:5.0%}   {undetermined_first[it] / max(1, n):5.0%}")
    for it in sorted(stats):
        if it in TYPES:
            missing = [f for f in TYPES[it]["required"] if field_cov[it][f] < 5]
            if missing: warns.append(f"{it}: required fields mentioned <5 times: {missing}")
    for w in warns: print("WARN ", w)
    for e in errors: print("ERROR", e)
    print(f"== {len(errors)} errors, {len(warns)} warnings")
    return 1 if errors else 0

if __name__ == "__main__":
    sys.exit(main(sys.argv[1:] or [os.path.join(ROOT, "datagen", "out")]))
