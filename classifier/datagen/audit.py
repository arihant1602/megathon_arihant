#!/usr/bin/env python3
"""Balance / shortcut audit for the classifier dataset (complements validate_gen.py, which checks format).
Each check is a condition the data must meet so the model learns the incident, not a shortcut.
Usage: python3 datagen/audit.py [datagen/out]      Exit 1 if any FAIL."""
import json, math, os, random, re, sys
from pathlib import Path
from collections import Counter, defaultdict

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
TAX = json.loads((Path(ROOT).parent / "app/src/main/assets/incident_taxonomy.json").read_text(encoding="utf-8"))
TYPES = [t["id"] for t in TAX["incident_types"]]
FL = TAX["field_library"]
GEMINI = {"water_leak", "washroom_plumbing", "power_outage", "electrical_hazard", "lighting_fault"}
WORD = re.compile(r"[a-z']+")
res = []


def check(name, ok, detail, warn_only=False):
    res.append(("PASS" if ok else ("WARN" if warn_only else "FAIL"), name, detail))


def load(path):
    out = []
    for d, _, fs in os.walk(path):
        for f in sorted(fs):
            if f.endswith(".jsonl"):
                out += [json.loads(l) for l in open(os.path.join(d, f), encoding="utf-8") if l.strip()]
    return out


def users(c):
    return [t for t in c["turns"] if t["role"] == "user"]


def nb_accuracy(samples, seed=0):
    """Multinomial naive Bayes on bag of words, conversation-level 70/30 split. samples: [(label, words)]."""
    rnd = random.Random(seed); s = samples[:]; rnd.shuffle(s)
    cut = int(len(s) * 0.7); tr, te = s[:cut], s[cut:]
    wc, cc, vocab = defaultdict(Counter), Counter(), set()
    for y, ws in tr:
        cc[y] += 1; wc[y].update(ws); vocab.update(ws)
    tot = {y: sum(c.values()) for y, c in wc.items()}
    V = len(vocab) + 1
    hit = 0
    for y, ws in te:
        best = max(cc, key=lambda k: math.log(cc[k]) + sum(math.log((wc[k][w] + 1) / (tot[k] + V)) for w in ws))
        hit += best == y
    return hit / max(1, len(te))


def main(path):
    data = load(path)
    train = [c for c in data if c["split"] == "train"]
    test = [c for c in data if c["split"] == "test"]
    typed = [c for c in train if c["incident_type"] in TYPES and c["kind"] in ("single", "confusable")]
    per = defaultdict(list)
    for c in typed:
        per[c["incident_type"]].append(c)
    present = [t for t in TYPES if per[t]]

    # 1. class balance
    counts = {t: len(per[t]) for t in TYPES}
    done = [n for n in counts.values() if n]
    check("C1 class balance (train convs per type, max/min <= 1.2)", done and max(done) / min(done) <= 1.2,
          f"{len(present)}/28 types present, min {min(done) if done else 0}, max {max(done) if done else 0}")
    check("C1b all 28 types present", len(present) == 28, f"missing: {[t for t in TYPES if not per[t]][:8]}...")
    nn = sum(c["kind"] == "none" for c in train); nm = sum(c["kind"] == "multi" for c in train)
    check("C2 out-of-scope + multi present (none >= 60, multi >= 40)", nn >= 60 and nm >= 40, f"none {nn}, multi {nm}")
    conf = {t: sum(c["kind"] == "confusable" for c in per[t]) for t in present}
    check("C3 confusables per type >= 6", all(v >= 6 for v in conf.values()),
          f"lowest {min(conf.values()) if conf else 0}")

    # 2. relevance balance, overall and per type
    fr = {}
    for t in present:
        s = [x for c in per[t] for u in users(c) for x in u["sentences"]]
        fr[t] = sum(not x["relevant"] for x in s) / len(s)
    allf = [x for c in train for u in users(c) for x in u["sentences"]]
    g = sum(not x["relevant"] for x in allf) / max(1, len(allf))
    check("R1 overall filler ratio 20-35%", 0.20 <= g <= 0.35, f"{g:.1%}")
    check("R2 per-type filler ratio within 12-40% (filler rate must not reveal the type)",
          all(0.12 <= v <= 0.40 for v in fr.values()),
          f"range {min(fr.values()):.0%}-{max(fr.values()):.0%}" if fr else "-")

    # 3. structure must not reveal the type
    tc = {t: sum(len(users(c)) for c in per[t]) / len(per[t]) for t in present}
    check("S1 mean user turns per type within ±0.6 of global", all(abs(v - sum(tc.values()) / len(tc)) <= 0.6 for v in tc.values()),
          f"range {min(tc.values()):.2f}-{max(tc.values()):.2f}")
    ul = {t: sum(len(WORD.findall(x["text"].lower())) for c in per[t] for u in users(c) for x in u["sentences"]) /
          sum(len(u["sentences"]) for c in per[t] for u in users(c)) for t in present}
    mean_ul = sum(ul.values()) / len(ul)
    check("S2 mean words per sentence per type within ±25% of global (style must not reveal type/author)",
          all(abs(v - mean_ul) / mean_ul <= 0.25 for v in ul.values()),
          "outliers: " + ", ".join(f"{t} {v:.1f}" for t, v in ul.items() if abs(v - mean_ul) / mean_ul > 0.25) + f" (global {mean_ul:.1f})")
    und = {t: sum(users(c)[0]["type_known"] == "undetermined" for c in per[t]) / len(per[t]) for t in present}
    check("S3 first-turn undetermined per type 15-40%", all(0.15 <= v <= 0.40 for v in und.values()),
          "outliers: " + ", ".join(f"{t} {v:.0%}" for t, v in und.items() if not 0.15 <= v <= 0.40))
    hist = Counter(len(users(c)) for c in typed)
    one, four = hist[1] / len(typed), sum(v for k, v in hist.items() if k >= 4) / len(typed)
    check("S4 turn mix: >=10% one-turn and >=12% four-plus-turn conversations", one >= 0.10 and four >= 0.12,
          f"1-turn {one:.0%}, 4+ turns {four:.0%}", warn_only=True)

    # 4. location must not reveal the type
    loc = {}
    for t in present:
        n_conv = len(per[t])
        q = sum(bool(re.search(r"\b(N|QRC)\b", " ".join(x["text"] for u in users(c) for x in u["sentences"] if "building" in x["fields"]))) for c in per[t])
        loc[t] = q / n_conv
    check("L1 Qualcomm buildings (N/QRC) used in 30-75% of each type's conversations",
          all(0.30 <= v <= 0.75 for v in loc.values()),
          "outliers: " + ", ".join(f"{t} {v:.0%}" for t, v in loc.items() if not 0.30 <= v <= 0.75), warn_only=True)

    # 5. field coverage
    low = []
    for t in present:
        req = next(x for x in TAX["incident_types"] if x["id"] == t)["required"]
        fc = Counter(f for c in per[t] for u in users(c) for x in u["sentences"] for f in x["fields"])
        low += [f"{t}.{f}={fc[f]}" for f in req if fc[f] < 10]
    check("F1 every required field labelled >= 10 times per type", not low, ", ".join(low[:10]))

    # 6. shortcut probes with a naive Bayes bag-of-words model
    fill = [(c["incident_type"], [w for u in users(c) for x in u["sentences"] if not x["relevant"] for w in WORD.findall(x["text"].lower())])
            for c in typed]
    fill = [s for s in fill if s[1]]
    if len(present) >= 3 and len(fill) > 60:
        acc, chance = nb_accuracy(fill), 1 / len(present)
        check("K1 filler-only text must not predict the type (NB accuracy <= 3x chance)", acc <= 3 * chance,
              f"accuracy {acc:.0%} vs chance {chance:.0%}")
    asst = [(c["incident_type"], [w for t in c["turns"] if t["role"] == "assistant" for w in WORD.findall(t["text"].lower())]) for c in typed]
    acc_a = nb_accuracy(asst)
    check("K2 assistant text predicts the type (expected) -> it must NOT be part of model input",
          True, f"NB accuracy from assistant turns alone {acc_a:.0%}; keep classifier input = user turns only")
    rewritten = set()
    rw = os.path.join(ROOT, "datagen", "rewrite")
    if os.path.isdir(rw):
        for f in os.listdir(rw):
            rewritten |= set(re.findall(r"^@ (\S+)", open(os.path.join(rw, f), encoding="utf-8").read(), re.M))
    author = lambda c: "gemini" if c["incident_type"] in GEMINI and c["id"] not in rewritten else "claude"
    if GEMINI & set(present):
        mix = {t: sum(author(c) == "claude" for c in per[t]) / len(per[t]) for t in GEMINI & set(present)}
        check("K3 both authors' styles present in every Gemini-started type (30-70% Claude-written)",
              all(0.30 <= v <= 0.70 for v in mix.values()),
              ", ".join(f"{t} {v:.0%} Claude" for t, v in sorted(mix.items())))
        auth = [(author(c), [w for u in users(c) for x in u["sentences"] for w in WORD.findall(x["text"].lower())]) for c in typed]
        acc_w = nb_accuracy(auth); base = max(Counter(a for a, _ in auth).values()) / len(auth)
        check("K3b (info) author is detectable from wording, so authorship must stay mixed within types",
              True, f"author accuracy {acc_w:.0%}, majority baseline {base:.0%}")

    # 7. train/test separation
    norm = lambda s: re.sub(r"[^a-z0-9 ]", "", s.lower()).strip()
    trs = {norm(x["text"]) for c in train for u in users(c) for x in u["sentences"] if len(x["text"].split()) >= 4}
    leak = [x["text"] for c in test for u in users(c) for x in u["sentences"] if norm(x["text"]) in trs]
    check("T1 no test sentence (4+ words) appears in train", not leak, f"{len(leak)} leaked")
    check("T2 test set present (>= 4 per type)", len(test) >= 4 * 28, f"{len(test)} test conversations", warn_only=True)

    for st, n, d in res:
        print(f"{st:4s}  {n}\n      {d}")
    fails = sum(r[0] == "FAIL" for r in res)
    print(f"== {fails} FAIL, {sum(r[0] == 'WARN' for r in res)} WARN, {sum(r[0] == 'PASS' for r in res)} PASS "
          f"({len(train)} train / {len(test)} test conversations)")
    return 1 if fails else 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1] if len(sys.argv) > 1 else os.path.join(ROOT, "datagen", "out")))
