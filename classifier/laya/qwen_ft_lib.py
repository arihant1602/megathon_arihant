"""Data building, teacher labelling and scoring for fine-tuning Qwen3-0.6B as the form extractor.
Shared by laya/qwen_finetune.ipynb (Colab) and local tests. Prompts come from llm.build_prompt, i.e. exactly
what the live assistant (laya/assistant.py) sends, so the fine-tuned model sees the same format in production."""
import json, random, re
import llm as L
import form as F
from common import load_conversations, train_val_split

SEED = 0
MAX_FIELDS = 5


# ----------------------------------------------------------------------------- examples (prompt side)
def examples_from_conversation(c, rng):
    """One example per user turn (+ one 'type just settled' catch-up example), mirroring assistant.step()."""
    out = []
    true = c["incident_type"] if c["incident_type"] in F.TYPES else None
    prev_q, prev_asks, settled = None, [], False
    turns = c["turns"]
    rel_so_far = []
    for i, t in enumerate(turns):
        if t["role"] == "assistant":
            prev_q, prev_asks = t["text"], t.get("asks", [])
            continue
        rel = [s for s in t["sentences"] if s["relevant"]]
        rel_so_far += rel
        text = " ".join(s["text"] for s in rel) or (" ".join(s["text"] for s in t["sentences"]) if prev_asks else "")
        mentioned = list(dict.fromkeys(f for s in rel for f in s["fields"]))
        cand = F.candidate_fields([t["type_known"]] if t["type_known"] in F.TYPES else ([true] if true else []))
        want = [f for f in dict.fromkeys(prev_asks + mentioned) if f in cand or f in F.CORE][:MAX_FIELDS]
        pool = [f for f in cand if f not in want and f not in mentioned]
        if pool and len(want) < MAX_FIELDS and rng.random() < 0.3:  # a field the user did not mention: must be omitted
            want.append(rng.choice(pool))
        if want and text:
            out.append({"conv_id": c["id"], "kind": "turn", "question": prev_q, "text": text, "fields": want,
                        "mentioned": [f for f in want if f in mentioned]})
        if true and not settled and t["type_known"] == true:  # catch-up call when the type first settles
            settled = True
            req = [f for f in F.required_fields(true)][:MAX_FIELDS]
            all_text = " ".join(s["text"] for s in rel_so_far)
            ment = {f for s in rel_so_far for f in s["fields"]}
            if all_text and len(rel_so_far) > len(rel):
                out.append({"conv_id": c["id"], "kind": "catchup", "question": None, "text": all_text, "fields": req,
                            "mentioned": [f for f in req if f in ment]})
    return out


def build_examples(out_dir):
    rng = random.Random(SEED)
    tr, va = train_val_split(load_conversations(out_dir, "train"))
    te = load_conversations(out_dir, "test")
    return {name: [e for c in convs for e in examples_from_conversation(c, rng)]
            for name, convs in (("train", tr), ("val", va), ("test", te))}


# ----------------------------------------------------------------------------- teacher (value side)
TEACHER_SYSTEM = ("You convert what a user said into form values. Every listed field IS mentioned by the user. "
                  "Reply with a JSON object containing exactly the listed fields. For short-text fields copy the "
                  "user's own words (a few words). For yes/no fields answer yes or no. For numbers give digits. "
                  "If the user said they don't know, use \"unknown\". No explanation.")


def teacher_messages(ex):
    metas = [F.field_meta(f) for f in ex["mentioned"]]
    p = (f"Assistant asked: {ex['question']}\n" if ex["question"] else "") + f"User said: {ex['text']}\nFields:\n" + \
        "\n".join("- " + L._describe(m) for m in metas)
    return [{"role": "system", "content": TEACHER_SYSTEM}, {"role": "user", "content": p}]


def parse_json(text):
    m = re.search(r"\{.*\}", text, re.S)
    if not m:
        return None
    try:
        return json.loads(m.group(0))
    except json.JSONDecodeError:
        return None


def to_schema_value(m, v):
    """Python value from validate() -> the string form the schema/Ollama uses."""
    if m["kind"] == "bool" and isinstance(v, bool):
        return "yes" if v else "no"
    return str(v)


def finalize(ex, teacher_raw):
    """Validate teacher values (same validator as production). Fields that fail are removed from the prompt
    too, so the student never learns to omit a field that was actually stated."""
    data = parse_json(teacher_raw) or {}
    metas = {f: F.field_meta(f) for f in ex["fields"]}
    ok = L.validate({k: v for k, v in data.items() if k in ex["mentioned"]}, [metas[f] for f in ex["mentioned"]], ex["text"])
    dropped = [f for f in ex["mentioned"] if f not in ok]
    fields = [f for f in ex["fields"] if f not in dropped]
    if not fields:
        return None
    target = {f: to_schema_value(metas[f], ok[f]) for f in fields if f in ok}
    return {**ex, "fields": fields, "target": target, "dropped": dropped}


# ----------------------------------------------------------------------------- student format
def student_text(ex, with_answer=True):
    """Exactly the string Ollama's Qwen3 template builds with think=False (system, user + ' /no_think',
    empty think block), followed by the JSON answer for training."""
    msgs = L.build_prompt([F.field_meta(f) for f in ex["fields"]], ex["text"], ex["question"])
    s = (f"<|im_start|>system\n{msgs[0]['content']}<|im_end|>\n"
         f"<|im_start|>user\n{msgs[1]['content']} /no_think<|im_end|>\n"
         f"<|im_start|>assistant\n<think>\n\n</think>\n\n")
    return (s + json.dumps(ex["target"]) + "<|im_end|>") if with_answer else s


# ----------------------------------------------------------------------------- scoring
def score(examples, outputs):
    """outputs: raw generated strings. Field-level precision/recall/F1 vs targets, exact match, JSON validity."""
    tp = fp = fn = exact = valid = 0
    per_kind = {}
    for ex, raw in zip(examples, outputs):
        data = parse_json(raw)
        valid += data is not None
        metas = [F.field_meta(f) for f in ex["fields"]]
        pred = {k: to_schema_value(F.field_meta(k), v) for k, v in L.validate(data or {}, metas, ex["text"]).items()}
        gold = ex["target"]
        for k, v in pred.items():
            if gold.get(k) is not None and str(gold[k]).lower() == str(v).lower():
                tp += 1
            else:
                fp += 1
        fn += sum(1 for k in gold if str(pred.get(k, "")).lower() != str(gold[k]).lower())
        hit = {k.lower(): str(v).lower() for k, v in pred.items()} == {k.lower(): str(v).lower() for k, v in gold.items()}
        exact += hit
        k = ex["kind"]; per_kind.setdefault(k, [0, 0]); per_kind[k][0] += hit; per_kind[k][1] += 1
    p = tp / max(1, tp + fp); r = tp / max(1, tp + fn)
    return {"n": len(examples), "json_valid": valid / max(1, len(examples)), "field_precision": p, "field_recall": r,
            "field_f1": 2 * p * r / max(1e-9, p + r), "exact_match": exact / max(1, len(examples)),
            "exact_by_kind": {k: v[0] / v[1] for k, v in per_kind.items()}}
