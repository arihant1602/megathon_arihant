"""Tokenize real conversations from data/*.jsonl into int32 raw inputs for calibration + benchmarking.
Writes bench/out/<model>_L<len>/inputs/{ids,mask}_NN.raw and calib.txt (snpe input-list format).
L1* and T*t models use a trimmed vocab: data token ids remapped to 0..N (unseen ids -> UNK=1)."""
import glob, json, os
import numpy as np
from tokenizers import Tokenizer

BENCH = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(BENCH)
N = 50
PAD = 50283

tok = Tokenizer.from_file(os.path.join(BENCH, "hf/ettin-17m/tokenizer.json"))
convs = [json.loads(l) for f in sorted(glob.glob(os.path.join(ROOT, "data/*.jsonl"))) for l in open(f) if l.strip()]
texts = [" ".join(t["text"] for t in c["turns"] if t["role"] == "user") for c in convs]
enc = [tok.encode(t).ids for t in texts]

lens = np.array([len(e) for e in enc])
print(f"{len(enc)} conversations, tokens (all user turns): median {int(np.median(lens))}, "
      f"p95 {int(np.percentile(lens, 95))}, max {lens.max()}, >128: {(lens > 128).sum()}, >256: {(lens > 256).sum()}")

vocab_ids = sorted({i for e in enc for i in e} | {PAD})
trim = {old: new for new, old in enumerate(vocab_ids, start=2)}  # 0 = pad, 1 = unk
trim[PAD] = 0
print(f"trimmed vocab from data: {len(trim) + 1} ids")

pick = [enc[i] for i in np.linspace(0, len(enc) - 1, N).astype(int)]  # spread over all types
for d in sorted(glob.glob(os.path.join(BENCH, "out/*_L*/"))):
    name, L = os.path.basename(d.rstrip("/")).rsplit("_L", 1)
    L = int(L)
    os.makedirs(os.path.join(d, "inputs"), exist_ok=True)
    lines = []
    for k, e in enumerate(pick):
        e = e[:L]
        ids = np.full(L, PAD, np.int32); ids[:len(e)] = e
        mask = np.zeros(L, np.int32); mask[:len(e)] = 1
        if name.startswith("L1") or name.endswith("t"):
            ids = np.array([trim.get(int(i), 1) for i in ids], np.int32)
        ids.reshape(1, L).tofile(os.path.join(d, f"inputs/ids_{k:02d}.raw"))
        mask.reshape(1, L).tofile(os.path.join(d, f"inputs/mask_{k:02d}.raw"))
        lines.append(f"input_ids:=inputs/ids_{k:02d}.raw attention_mask:=inputs/mask_{k:02d}.raw")
    open(os.path.join(d, "calib.txt"), "w").write("\n".join(lines) + "\n")
    print(f"{d}: {N} inputs")
