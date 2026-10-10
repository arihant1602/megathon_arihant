#!/usr/bin/env python3
"""Format converter only: turns hand-written datagen/src/<split>/<file>.txt into datagen/out/<split>/<file>.jsonl.
It never writes or changes wording; `text` is just the hand-written sentences joined with spaces.

Source format (one conversation per block, blank line between blocks):
    @ <id> <kind> [confused=<type>] [secondary=<type>,<type>]
    S <scenario>
    U <type_known>                      user turn
    + <field> <field> :: <sentence>     relevant sentence (fields may be empty)
    - :: <sentence>                     filler sentence
    A <field>,<field> :: <assistant>    assistant turn (asks may be empty)
Usage: python3 datagen/compile.py   (all files under datagen/src)"""
import glob, json, os, sys

ROOT = os.path.dirname(os.path.abspath(__file__))


def parse(path, split):
    convs, cur, n = [], None, 0
    for n, raw in enumerate(open(path, encoding="utf-8"), 1):
        line = raw.rstrip("\n")
        if not line.strip() or line.startswith("#"):
            continue
        tag, rest = line[0], line[2:].strip()
        try:
            if tag == "@":
                parts = rest.split()
                cur = {"id": parts[0], "incident_type": None, "kind": parts[1]}
                for p in parts[2:]:
                    k, v = p.split("=")
                    if k == "confused":
                        cur["confused_with"] = v
                    elif k == "secondary":
                        cur["secondary_types"] = v.split(",")
                cur["split"], cur["scenario"], cur["turns"] = split, "", []
                convs.append(cur)
            elif tag == "S":
                cur["scenario"] = rest
            elif tag == "U":
                cur["turns"].append({"role": "user", "text": "", "sentences": [], "type_known": rest})
            elif tag in "+-":
                fields, text = rest.split("::", 1) if tag == "+" else ("", rest.split("::", 1)[1])
                cur["turns"][-1]["sentences"].append(
                    {"text": text.strip(), "relevant": tag == "+", "fields": fields.split()})
            elif tag == "A":
                asks, text = rest.split("::", 1)
                cur["turns"].append({"role": "assistant", "text": text.strip(),
                                     "asks": [a for a in asks.replace(",", " ").split() if a]})
            else:
                raise ValueError(f"unknown tag {tag!r}")
        except Exception as e:
            sys.exit(f"{path}:{n}: {e}: {line}")
    for c in convs:
        for t in c["turns"]:
            if t["role"] == "user":
                t["text"] = " ".join(s["text"] for s in t["sentences"])
        users = [t for t in c["turns"] if t["role"] == "user"]
        c["incident_type"] = "none" if c["kind"] == "none" else users[-1]["type_known"]
        # key order matching the existing files
        order = ["id", "incident_type", "kind", "confused_with", "secondary_types", "split", "scenario", "turns"]
        c_sorted = {k: c[k] for k in order if k in c}
        c.clear(); c.update(c_sorted)
    return convs


if __name__ == "__main__":
    for src in sorted(glob.glob(os.path.join(ROOT, "src", "*", "*.txt"))):
        split = os.path.basename(os.path.dirname(src))
        dst = os.path.join(ROOT, "out", split, os.path.basename(src)[:-4] + ".jsonl")
        convs = parse(src, split)
        with open(dst, "w", encoding="utf-8") as f:
            for c in convs:
                f.write(json.dumps(c, ensure_ascii=False) + "\n")
        print(f"{os.path.relpath(dst, ROOT)}: {len(convs)}")
