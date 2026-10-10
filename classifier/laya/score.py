"""Score on-device outputs (laya/out/<mode>/device/out/Result_N/*.raw) with the same metrics as Colab.
Usage: python laya/score.py base|ft [variant]  -> prints metrics, writes laya/out/<mode>/device_metrics_<variant>.json"""
import glob, json, os, re, sys
import numpy as np
HERE = os.path.dirname(os.path.abspath(__file__)); ROOT = os.path.dirname(HERE)
sys.path.insert(0, HERE)
from common import *  # noqa
from tokenizers import Tokenizer  # noqa

mode = sys.argv[1]; var = sys.argv[2] if len(sys.argv) > 2 else "q"; d = os.path.join(HERE, "out", mode)
LD = os.path.expanduser("~/.cache/huggingface/hub/models--convaiinnovations--laya/snapshots/" + LAYA_REVISION)
tok = Tokenizer.from_file(os.path.join(LD, "tokenizer/tokenizer.json"))
tax, types, fields = label_space(os.path.join(ROOT, "incident_taxonomy.json"))
test = build_samples(load_conversations(os.path.join(ROOT, "datagen/out"), "test"), tok, types, fields)
res = sorted(glob.glob(os.path.join(d, f"device_{var}/out/Result_*")), key=lambda p: int(re.search(r"(\d+)$", p).group(1)))
read = lambda r, name, shape: np.fromfile(glob.glob(os.path.join(r, f"{name}*.raw"))[0], np.float32).reshape(shape)

if mode == "base":
    E = np.stack([read(r, "embedding", (1024,)) for r in res])
    E /= np.maximum(np.linalg.norm(E, axis=1, keepdims=True), 1e-12)
    nT, nD = len(types), len(department_prototype_texts(tax))
    assert len(E) == len(test) + nT + nD, (len(E), len(test), nT, nD)
    S, P, Pd = E[:len(test)], E[len(test):len(test) + nT], E[len(test) + nT:]
    flat, two, _, dpred = zero_shot(S, P, Pd, types, tax)
    dtrue = [dept_of_label(types[s["type"]], tax) for s in test]
    m = {"flat": evaluate(test, flat, types=types), "two_stage": evaluate(test, two, types=types),
         "dept_acc_stage1": float(np.mean([a == b for a, b in zip(dtrue, dpred)]))}
else:
    parts = [split_single_output(read(r, "logits", (-1,)), len(types), len(fields)) for r in res]
    T = np.stack([p[0] for p in parts]); Rl = np.stack([p[1] for p in parts]); Fl = np.stack([p[2] for p in parts])
    assert len(T) == len(test)
    two, dpred = two_stage_from_logits(T, types, tax)
    dtrue = [dept_of_label(types[s["type"]], tax) for s in test]
    m = {"flat": evaluate(test, T, Rl, Fl, types=types), "two_stage": evaluate(test, two, types=types),
         "dept_acc_stage1": float(np.mean([a == b for a, b in zip(dtrue, dpred)]))}
txt = open(os.path.join(d, f"device_{var}/diagview.txt")).read()
inf = re.search(r"Total Inference Time: (\d+) us", txt); init = re.search(r"\n  Init: (\d+) us", txt)
m |= {"npu_ms_per_turn": int(inf.group(1)) / 1000 if inf else None, "npu_init_ms": int(init.group(1)) / 1000 if init else None,
      "dlc_MB": round(os.path.getsize(os.path.join(d, "model.dlc" if var.startswith("fp32") else f"model_{var}_htp.dlc")) / 2**20, 1)}
json.dump(m, open(os.path.join(d, f"device_metrics_{var}.json"), "w"), indent=1)
print(json.dumps(m, indent=1))
