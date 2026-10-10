"""Train the bidirectional CfC liquid network (bench/lnn.py) on the same data, splits and heads as Laya,
then export it for QAIRT. CPU training; early stopping on the validation split.
Usage: python laya/train_lnn.py            -> laya/out/lnn/{lnn.pt, train_log.json, model.onnx, calib/, test/}
       python laya/train_lnn.py export     -> export only, from laya/out/lnn/lnn.pt
Vocabulary: token ids seen in the TRAIN split only (pad=0, unk=1); everything else maps to unk."""
import copy, json, math, os, sys, time
import numpy as np, torch
import torch.nn.functional as F

HERE = os.path.dirname(os.path.abspath(__file__)); ROOT = os.path.dirname(HERE)
sys.path.insert(0, HERE); sys.path.insert(0, os.path.join(ROOT, "bench"))
from common import *  # noqa: E402
from lnn import CfCClassifier, n_params  # noqa: E402
from tokenizers import Tokenizer  # noqa: E402
import onnx, onnxruntime as ort  # noqa: E402

SEED, EPOCHS, BS, LR, HIDDEN, EMB, PATIENCE = 0, 40, 32, 2e-3, 192, 256, 6
LD = os.path.expanduser("~/.cache/huggingface/hub/models--convaiinnovations--laya/snapshots/" + LAYA_REVISION)
OUT = os.path.join(HERE, "out", "lnn")


def remap(samples, vmap):
    for s in samples:
        s["input_ids"] = np.array([vmap.get(int(i), 1) for i in s["input_ids"]], np.int32)
    return samples


def losses(b, out):
    tl, rl, fl = out
    lt = F.cross_entropy(tl, b["type"])
    lr = F.cross_entropy(rl.reshape(-1, 2), b["rel"].reshape(-1), ignore_index=-100)
    lf = (F.binary_cross_entropy_with_logits(fl, b["fields"], reduction="none").mean(-1) * b["field_mask"]).sum() \
        / b["field_mask"].sum().clamp(min=1)
    return lt + 0.5 * lr + 0.5 * lf


def batch(samples, idx, trim=True):
    b = collate([samples[j] for j in idx])
    if trim:  # cut to the longest sequence in the batch (right padding is masked, so this is exact)
        n = int(b["attention_mask"].sum(1).max())
        for k in ("input_ids", "attention_mask", "rel", "fields", "field_mask"):
            b[k] = b[k][:, :n]
    return b


@torch.no_grad()
def predict(m, samples, bs=64):
    m.eval(); T, R, Fl = [], [], []
    for i in range(0, len(samples), bs):
        b = batch(samples, range(i, min(i + bs, len(samples))), trim=False)
        t, r, f = m(b["input_ids"], b["attention_mask"])
        T.append(t); R.append(r); Fl.append(f)
    return torch.cat(T).numpy(), torch.cat(R).numpy(), torch.cat(Fl).numpy()


def main():
    torch.manual_seed(SEED); np.random.seed(SEED); torch.set_num_threads(16)
    os.makedirs(OUT, exist_ok=True)
    tok = Tokenizer.from_file(os.path.join(LD, "tokenizer/tokenizer.json"))
    tax, types, fields = label_space(os.path.join(ROOT, "incident_taxonomy.json"))
    tr_c, va_c = train_val_split(load_conversations(os.path.join(ROOT, "datagen/out"), "train"))
    S = {"train": build_samples(tr_c, tok, types, fields), "val": build_samples(va_c, tok, types, fields),
         "test": build_samples(load_conversations(os.path.join(ROOT, "datagen/out"), "test"), tok, types, fields)}
    seen = sorted({int(i) for s in S["train"] for i in s["input_ids"]} - {PAD})
    vmap = {PAD: 0} | {t: k + 2 for k, t in enumerate(seen)}
    for v in S.values():
        remap(v, vmap)
    unk = np.mean([np.mean(s["input_ids"][s["attention_mask"] == 1] == 1) for s in S["test"]])
    print(f"vocab {len(vmap) + 1} (train tokens only), test unk rate {unk:.1%}", {k: len(v) for k, v in S.items()})

    m = CfCClassifier(len(vmap) + 1, emb=EMB, hidden=HIDDEN)
    opt = torch.optim.AdamW(m.parameters(), lr=LR, weight_decay=0.01)
    steps = EPOCHS * math.ceil(len(S["train"]) / BS)
    sched = torch.optim.lr_scheduler.OneCycleLR(opt, max_lr=LR, total_steps=steps, pct_start=0.1)
    best, best_state, bad, log, t0 = -1, None, 0, [], time.time()
    for ep in range(EPOCHS):
        m.train(); order = np.random.permutation(len(S["train"])); tot = 0
        for i in range(0, len(order), BS):
            b = batch(S["train"], order[i:i + BS])
            loss = losses(b, m(b["input_ids"], b["attention_mask"]))
            opt.zero_grad(); loss.backward(); torch.nn.utils.clip_grad_norm_(m.parameters(), 1.0); opt.step(); sched.step()
            tot += float(loss.detach())
        v = evaluate(S["val"], *predict(m, S["val"]), types=types)
        score = v["type_acc_all_turns"] + 0.5 * v["fields_micro_f1"]
        log.append({"epoch": ep + 1, "loss": tot / math.ceil(len(order) / BS), **v, "s": round(time.time() - t0)})
        print(f"epoch {ep + 1} loss {log[-1]['loss']:.3f} val type {v['type_acc_all_turns']:.3f} rel {v['relevance_acc']:.3f} "
              f"fields {v['fields_micro_f1']:.3f} | {time.time() - t0:.0f}s", flush=True)
        if score > best:
            best, best_state, bad = score, copy.deepcopy(m.state_dict()), 0
        else:
            bad += 1
            if bad >= PATIENCE:
                print("early stop"); break
    m.load_state_dict(best_state); m.eval()

    vt = torch.from_numpy(predict(m, S["val"])[0]); vy = torch.tensor([s["type"] for s in S["val"]])
    logT = torch.zeros(1, requires_grad=True); o = torch.optim.LBFGS([logT], lr=0.1, max_iter=100)
    def nll():
        o.zero_grad(); l = F.cross_entropy(vt / logT.exp(), vy); l.backward(); return l
    o.step(nll)
    test = evaluate(S["test"], *predict(m, S["test"]), types=types)
    print("TEST fp32 (laptop):", json.dumps({k: round(v, 3) for k, v in test.items()}))
    torch.save({"state": best_state, "vocab_map": vmap, "types": types, "fields": fields, "hidden": HIDDEN, "emb": EMB,
                "temperature": float(logT.exp())}, os.path.join(OUT, "lnn.pt"))
    json.dump({"params": n_params(m), "vocab": len(vmap) + 1, "test_unk_rate": float(unk), "epochs_run": len(log),
               "train_seconds": round(time.time() - t0), "log": log, "test_fp32_laptop": test},
              open(os.path.join(OUT, "train_log.json"), "w"), indent=1)
    export_lnn()


def export_lnn():
    """Rebuild the samples, load lnn.pt and export ONNX + calibration/test inputs (vocab-remapped ids)."""
    import importlib.util
    spec = importlib.util.spec_from_file_location("laya_export", os.path.join(HERE, "export.py"))
    write_inputs = importlib.util.module_from_spec(spec); spec.loader.exec_module(write_inputs); write_inputs = write_inputs.write_inputs
    ck = torch.load(os.path.join(OUT, "lnn.pt"), weights_only=True)
    tok = Tokenizer.from_file(os.path.join(LD, "tokenizer/tokenizer.json"))
    tax, types, fields = label_space(os.path.join(ROOT, "incident_taxonomy.json"))
    tr_c, _ = train_val_split(load_conversations(os.path.join(ROOT, "datagen/out"), "train"))
    S = {"train": remap(build_samples(tr_c, tok, types, fields), ck["vocab_map"]),
         "test": remap(build_samples(load_conversations(os.path.join(ROOT, "datagen/out"), "test"), tok, types, fields), ck["vocab_map"])}
    m = CfCClassifier(len(ck["vocab_map"]) + 1, emb=ck["emb"], hidden=ck["hidden"]); m.load_state_dict(ck["state"]); m.eval()
    # export for QAIRT: static [1, 128] int32, single concatenated output, + calibration / device test inputs
    pick = [S["train"][i] for i in np.linspace(0, len(S["train"]) - 1, 50).astype(int)]
    write_inputs(OUT, "calib", [(s["input_ids"], s["attention_mask"]) for s in pick])
    write_inputs(OUT, "test", [(s["input_ids"], s["attention_mask"]) for s in S["test"]])
    wrap = SingleOutput(m).eval()
    ids = torch.from_numpy(S["test"][0]["input_ids"]).unsqueeze(0); mask = torch.from_numpy(S["test"][0]["attention_mask"]).unsqueeze(0)
    path = os.path.join(OUT, "model.onnx")
    with torch.no_grad():
        ref = wrap(ids, mask).numpy()
        torch.onnx.export(wrap, (ids, mask), path, opset_version=17, input_names=["input_ids", "attention_mask"], output_names=["logits"])
    got = ort.InferenceSession(path, providers=["CPUExecutionProvider"]).run(None, {"input_ids": ids.numpy(), "attention_mask": mask.numpy()})[0]
    print("onnx nodes", len(onnx.load(path).graph.node), "ort vs torch", float(np.abs(ref - got).max()))


if __name__ == "__main__":
    export_lnn() if sys.argv[1:] == ["export"] else main()
