"""Test-time augmentation by shuffling + accuracy strategies for the fine-tuned model.
  python laya/tta.py build          -> laya/out/ft/tta/ + tta.txt: 5 shuffled views of every test turn
  python laya/tta.py score <variant> -> compares single input vs voting over shuffles, + strategy analysis
A "shuffle" permutes the order of the user's turns so far and the order of sentences inside each turn
(turn separators kept, as in training). Shuffling tokens would destroy the text."""
import glob, json, os, re, sys, zlib
import numpy as np

HERE = os.path.dirname(os.path.abspath(__file__)); ROOT = os.path.dirname(HERE)
sys.path.insert(0, HERE)
from common import *  # noqa: E402
from tokenizers import Tokenizer  # noqa: E402
from paths import laya_directory

N_VIEWS = 5
D = os.path.join(HERE, "out", "ft")
LD = laya_directory()


def setup():
    tok = Tokenizer.from_file(os.path.join(LD, "tokenizer/tokenizer.json"))
    tax, types, fields = label_space()
    convs = load_conversations(os.path.join(ROOT, "datagen/out"), "test")
    return tok, tax, types, fields, convs


def shuffled_views(conv, k, tok, types, fields):
    """N_VIEWS samples for user turn k of conv, each with turns and sentences permuted (seeded)."""
    turns = [t for t in conv["turns"] if t["role"] == "user"][: k + 1]
    label = turns[-1]["type_known"]
    out, n_sent = [], sum(len(t["sentences"]) for t in turns)
    for v in range(N_VIEWS):
        rng = np.random.default_rng(zlib.crc32(f"{conv['id']}|{k}|{v}".encode()))
        order = rng.permutation(len(turns))
        fake_turns = []
        for i in order:
            sents = [turns[i]["sentences"][j] for j in rng.permutation(len(turns[i]["sentences"]))]
            fake_turns.append({"role": "user", "sentences": sents, "type_known": label})
        fake = {"id": conv["id"], "kind": conv["kind"], "turns": fake_turns}
        out.append(build_samples([fake], tok, types, fields)[-1])
    return out, n_sent


def build():
    from export import write_inputs
    tok, tax, types, fields, convs = setup()
    pairs, multi = [], 0
    for c in convs:
        for k in range(sum(t["role"] == "user" for t in c["turns"])):
            views, n_sent = shuffled_views(c, k, tok, types, fields)
            multi += n_sent > 1
            pairs += [(s["input_ids"], s["attention_mask"]) for s in views]
    write_inputs(D, "tta", pairs)
    print(f"{len(pairs)} inputs ({len(pairs) // N_VIEWS} turns x {N_VIEWS}); turns with >1 sentence (shuffle changes input): {multi}")


def read_type_logits(var, list_name, n_types, n_fields):
    sub = f"device_{var}" + ("" if list_name == "test" else f"_{list_name}")
    res = sorted(glob.glob(os.path.join(D, sub, "out/Result_*")), key=lambda p: int(re.search(r"(\d+)$", p).group(1)))
    return np.stack([split_single_output(np.fromfile(glob.glob(r + "/logits*.raw")[0], np.float32), n_types, n_fields)[0]
                     for r in res])


def softmax(z, T=1.0):
    z = z / T; z = z - z.max(-1, keepdims=True); p = np.exp(z); return p / p.sum(-1, keepdims=True)


def vote(P):
    """P: [n_views, n_types] probabilities. Majority of argmax; tie -> highest summed probability."""
    votes = np.bincount(P.argmax(1), minlength=P.shape[1]).astype(float)
    return int(np.argmax(votes + 1e-6 * P.sum(0)))


def score(var):
    tok, tax, types, fields, convs = setup()
    test = build_samples(convs, tok, types, fields)
    y = np.array([s["type"] for s in test]); last = np.array([s["last_turn"] for s in test])
    T = json.load(open(os.path.join(D, "colab_metrics.json")))["temperature"]
    single = softmax(read_type_logits(var, "test", len(types), len(fields)), T)
    V = softmax(read_type_logits(var, "tta", len(types), len(fields)), T).reshape(len(test), N_VIEWS, -1)
    acc = lambda pred: (float((pred == y).mean()), float((pred[last] == y[last]).mean()))
    r = {"single (original order)": acc(single.argmax(1)),
         "5 shuffles, majority vote": acc(np.array([vote(v) for v in V])),
         "5 shuffles, mean probability": acc(V.mean(1).argmax(1)),
         "original + 4 shuffles, majority vote": acc(np.array([vote(np.concatenate([s[None], v[:4]])) for s, v in zip(single, V)])),
         "shuffled view 1 alone": acc(V[:, 0].argmax(1))}
    print(f"\n== TTA ({var}, {len(test)} test turns)          all turns   final turn")
    for k, (a, f) in r.items():
        print(f"  {k:40s} {a:7.1%}   {f:7.1%}")

    # ---- strategies on the plain single-input outputs
    P = single; pred = P.argmax(1); conf = P.max(1)
    top2 = np.argsort(-P, 1)[:, :2]
    s = {"top2_acc_all": float(np.mean([yy in t for yy, t in zip(y, top2)])),
         "top2_acc_final": float(np.mean([yy in t for yy, t in zip(y[last], top2[last])]))}
    print(f"\n== top-2 contains the right type: all turns {s['top2_acc_all']:.1%}, final turn {s['top2_acc_final']:.1%}")
    print("\n== confidence gate (final turns): answer only if confidence >= t, else ask a confirming question")
    gate = []
    for t in (0.0, 0.5, 0.6, 0.7, 0.8, 0.9):
        m = last & (conf >= t); cov = m.sum() / last.sum(); a = float((pred[m] == y[m]).mean()) if m.any() else float("nan")
        asked = last & (conf < t); fixed = np.mean([yy in tt for yy, tt in zip(y[asked], top2[asked])]) if asked.any() else 1.0
        overall = (np.sum(pred[m] == y[m]) + fixed * asked.sum()) / last.sum()
        gate.append({"t": t, "coverage": float(cov), "acc_answered": a, "acc_with_top2_confirm": float(overall)})
        print(f"  t={t:.1f}: answers {cov:6.1%} of conversations directly, {a:6.1%} right; "
              f"+ top-2 confirmation for the rest -> {overall:6.1%}")
    s["gate"] = gate

    und = types.index("undetermined"); err = pred != y
    e_und = err & ((y == und) | (pred == und))
    s["errors_all"] = int(err.sum()); s["errors_involving_undetermined"] = int(e_und.sum())
    print(f"\n== errors: {err.sum()} of {len(y)} turns; {e_und.sum()} of them are 'undetermined' vs a type "
          f"(when to commit, not which type)")
    strict = ~((y == und) | (pred == und))
    s["acc_type_vs_type"] = float((pred[strict] == y[strict]).mean())
    print(f"   accuracy when neither label is 'undetermined': {s['acc_type_vs_type']:.1%}")
    conf_pairs = {}
    for a, b in zip(y[err & ~e_und], pred[err & ~e_und]):
        key = f"{types[a]} -> {types[b]}"; conf_pairs[key] = conf_pairs.get(key, 0) + 1
    s["top_confusions"] = sorted(conf_pairs.items(), key=lambda x: -x[1])[:8]
    print("   most common type->type confusions:", s["top_confusions"][:5])

    # conversation-level: sticky decision (keep the first prediction that reaches confidence >= 0.9)
    by_conv = {}
    for i, smp in enumerate(test):
        by_conv.setdefault(smp["conv_id"], []).append(i)
    sticky_ok = 0
    for ids in by_conv.values():
        final = next((pred[i] for i in ids if conf[i] >= 0.9 and pred[i] != und), pred[ids[-1]])
        sticky_ok += final == y[ids[-1]]
    s["sticky_final_acc"] = sticky_ok / len(by_conv)
    print(f"\n== sticky decision (lock in the first confident >=0.9 type in a conversation): {s['sticky_final_acc']:.1%} of conversations")
    json.dump({"tta": r, "strategies": s}, open(os.path.join(D, f"tta_results_{var}.json"), "w"), indent=1, default=float)


if __name__ == "__main__":
    build() if sys.argv[1] == "build" else score(sys.argv[2] if len(sys.argv) > 2 else "w8a16")
