"""Export benchmark models to ONNX (static [1, L] int32 inputs) and check onnxruntime vs PyTorch.
Usage: python bench/export.py [model ...]   (default: all)  -> bench/out/<model>_L<len>/model.onnx
All weights are random (Ettin built from its config.json); accuracy is out of scope."""
import json, os, sys, time
import numpy as np
import onnx, onnxruntime as ort, torch
import torch.nn as nn
from onnxsim import simplify

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from lnn import CfCClassifier, N_TYPES, N_REL, N_FIELDS, n_params

BENCH = os.path.dirname(os.path.abspath(__file__))
OUT = os.path.join(BENCH, "out")
LENGTHS = [128, 256]
TRIM_VOCAB = 8192
ETTIN_VOCAB = 50368


class EttinClassifier(nn.Module):
    """Ettin architecture from its config.json, random init (weights don't change speed/size)."""
    def __init__(self, cfg_dir, vocab=None):
        super().__init__()
        from transformers import ModernBertConfig, ModernBertModel
        cfg = ModernBertConfig.from_pretrained(cfg_dir)
        if vocab:  # trimmed vocab, same ids as the L1 LNNs
            cfg.vocab_size, cfg.pad_token_id = vocab, 0
        cfg._attn_implementation = "eager"
        cfg.reference_compile = False
        self.enc = ModernBertModel(cfg)
        d = cfg.hidden_size
        self.type_head = nn.Linear(d, N_TYPES)
        self.rel_head = nn.Linear(d, N_REL)
        self.field_head = nn.Linear(d, N_FIELDS)

    def forward(self, input_ids, attention_mask):
        h = self.enc(input_ids=input_ids.long(), attention_mask=attention_mask.long()).last_hidden_state
        return self.type_head(h[:, 0]), self.rel_head(h), self.field_head(h)


MODELS = {
    "L1h64":  lambda: (CfCClassifier(TRIM_VOCAB, hidden=64), TRIM_VOCAB),
    "L1h128": lambda: (CfCClassifier(TRIM_VOCAB, hidden=128), TRIM_VOCAB),
    "L1h192": lambda: (CfCClassifier(TRIM_VOCAB, hidden=192), TRIM_VOCAB),
    "L2h192": lambda: (CfCClassifier(ETTIN_VOCAB, hidden=192), ETTIN_VOCAB),
    "T1": lambda: (EttinClassifier(os.path.join(BENCH, "hf/ettin-17m")), ETTIN_VOCAB),
    "T1t": lambda: (EttinClassifier(os.path.join(BENCH, "hf/ettin-17m"), TRIM_VOCAB), TRIM_VOCAB),
    "T2": lambda: (EttinClassifier(os.path.join(BENCH, "hf/ettin-32m")), ETTIN_VOCAB),
}


def export(name, L):
    torch.manual_seed(0)
    model, vocab = MODELS[name]()
    model.eval()
    d = os.path.join(OUT, f"{name}_L{L}")
    os.makedirs(d, exist_ok=True)
    ids = torch.randint(5, vocab, (1, L), dtype=torch.int32)
    mask = torch.ones(1, L, dtype=torch.int32)
    mask[:, L * 3 // 4:] = 0
    path = os.path.join(d, "model.onnx")
    t0 = time.time()
    with torch.no_grad():
        ref = [o.numpy() for o in model(ids, mask)]
        torch.onnx.export(model, (ids, mask), path, opset_version=17,
                          input_names=["input_ids", "attention_mask"],
                          output_names=["type_logits", "rel_logits", "field_logits"])
    t_export = time.time() - t0
    t0 = time.time()
    m, ok = simplify(onnx.load(path))
    if ok:
        onnx.save(m, path)
    t_sim = time.time() - t0
    sess = ort.InferenceSession(path, providers=["CPUExecutionProvider"])
    got = sess.run(None, {"input_ids": ids.numpy(), "attention_mask": mask.numpy()})
    diff = max(float(np.abs(a - b).max()) for a, b in zip(ref, got))
    info = {"model": name, "L": L, "params": n_params(model), "onnx_bytes": os.path.getsize(path),
            "onnx_nodes": len(onnx.load(path).graph.node), "export_s": round(t_export, 1),
            "simplify_s": round(t_sim, 1), "simplified": ok, "ort_vs_torch_maxdiff": diff}
    json.dump(info, open(os.path.join(d, "export.json"), "w"), indent=1)
    print(json.dumps(info), flush=True)


if __name__ == "__main__":
    for name in sys.argv[1:] or list(MODELS):
        for L in LENGTHS:
            try:
                export(name, L)
            except Exception as e:
                print(f"FAIL {name} L{L}: {type(e).__name__}: {e}", flush=True)
