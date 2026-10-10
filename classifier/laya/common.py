"""Shared code for the Laya experiments (used on Colab for training and locally for export/eval).

- Data: one sample per user turn. Input = all USER sentences so far (assistant text is never input:
  it leaks the type). Layout: [CLS] turn1 sentences [SEP] turn2 sentences [SEP] ... padded to L.
- Model: Laya's encoder (ModernBERT-large, 28 layers) + 3 heads (type 30, per-token relevance 2,
  per-token fields multi-label). Fine-tuning uses a small self-contained LoRA (no peft dependency)
  that can be merged into the weights for ONNX/QAIRT export.
- Zero-shot (no fine-tuning): mean-pooled Laya embedding vs. embeddings of taxonomy descriptions.
- Metrics computed from raw logits, so the same code scores PyTorch, onnxruntime and on-device outputs.
"""
import json, os
import numpy as np
import torch
import torch.nn as nn
import torch.nn.functional as F

LAYA_REPO = "convaiinnovations/laya"
LAYA_REVISION = "7b928d828b7b0e022f929d9bd2e44165aa270148"
LAYA_FILES = ["model.safetensors", "encoder/config.json", "tokenizer/tokenizer.json"]
L = 128
CLS, SEP, PAD = 50281, 50282, 50283
LOCATION_FIELDS = ["building", "floor", "space_type", "space_id", "landmark", "occurred_at",
                   "immediate_actions_taken", "persons_involved", "witnesses", "asset_tag"]
LORA_TARGETS = ("attn.Wqkv", "attn.Wo", "mlp.Wi", "mlp.Wo")


# ----------------------------------------------------------------------------- labels
def label_space(taxonomy_path):
    tax = json.load(open(taxonomy_path))
    types = [t["id"] for t in tax["incident_types"]] + ["undetermined", "none"]
    fields = list(LOCATION_FIELDS)
    for t in tax["incident_types"]:
        for f in t["required"] + t.get("optional", []):
            if f not in fields:
                fields.append(f)
    return tax, types, fields


# ----------------------------------------------------------------------------- data
def load_conversations(out_dir, split):
    convs = []
    for root, _, files in os.walk(os.path.join(out_dir, split)):
        for f in sorted(files):
            if f.endswith(".jsonl"):
                convs += [json.loads(l) for l in open(os.path.join(root, f)) if l.strip()]
    return sorted(convs, key=lambda c: c["id"])


def train_val_split(convs):
    """Deterministic 90/10 split by conversation (never by turn)."""
    val = {c["id"] for i, c in enumerate(convs) if i % 10 == 7}
    return [c for c in convs if c["id"] not in val], [c for c in convs if c["id"] in val]


def build_samples(convs, tokenizer, types, fields):
    """Returns a list of dicts with int32 ids/mask [L], type label, per-token rel/field labels and
    token->sentence index (for sentence-level metrics)."""
    tix, fix = {t: i for i, t in enumerate(types)}, {f: i for i, f in enumerate(fields)}
    out = []
    for c in convs:
        turns = [t for t in c["turns"] if t["role"] == "user"]
        chunks = []  # (token ids, rel, field multi-hot, sentence key) per sentence, SEP after each turn
        for k, t in enumerate(turns):
            for j, s in enumerate(t["sentences"]):
                ids = tokenizer.encode(" " + s["text"], add_special_tokens=False).ids  # leading space as in running text
                fh = np.zeros(len(fields), np.float32)
                for f in s["fields"]:
                    fh[fix[f]] = 1
                chunks.append((ids, int(s["relevant"]), fh, (k, j)))
            chunks.append(([SEP], -100, None, None))
            # sample after user turn k
            body = list(chunks)
            while sum(len(x[0]) for x in body) > L - 1:  # keep the newest turns
                body.pop(0)
            ids = [CLS]; rel = [-100]; fl = [np.zeros(len(fields), np.float32)]; fmask = [0]; sent = [-1]
            sent_keys = []
            for tok_ids, r, fh, key in body:
                if key is not None:
                    sent_keys.append(key)
                for tid in tok_ids:
                    ids.append(tid); rel.append(r)
                    fl.append(fh if fh is not None else np.zeros(len(fields), np.float32))
                    fmask.append(0 if fh is None else 1)
                    sent.append(len(sent_keys) - 1 if key is not None else -1)
            n = len(ids)
            pad = L - n
            sent_rel = [next(x[1] for x in body if x[3] == key) for key in sent_keys]
            sent_fields = [next(x[2] for x in body if x[3] == key) for key in sent_keys]
            out.append({
                "conv_id": c["id"], "turn": k, "last_turn": k == len(turns) - 1, "kind": c["kind"],
                "input_ids": np.array(ids + [PAD] * pad, np.int32),
                "attention_mask": np.array([1] * n + [0] * pad, np.int32),
                "type": tix[t["type_known"]],
                "rel": np.array(rel + [-100] * pad, np.int64),
                "fields": np.stack(fl + [np.zeros(len(fields), np.float32)] * pad),
                "field_mask": np.array(fmask + [0] * pad, np.float32),
                "sent_index": np.array(sent + [-1] * pad, np.int32),
                "sent_rel": np.array(sent_rel, np.int64),
                "sent_fields": np.stack(sent_fields) if sent_fields else np.zeros((0, len(fields)), np.float32),
            })
    return out


def collate(batch):
    return {k: torch.from_numpy(np.stack([b[k] for b in batch])) for k in
            ("input_ids", "attention_mask", "rel", "fields", "field_mask")} | \
           {"type": torch.tensor([b["type"] for b in batch])}


# ----------------------------------------------------------------------------- model
def load_laya_encoder(laya_dir, attn="eager"):
    from safetensors.torch import load_file
    from transformers import ModernBertConfig, ModernBertModel
    cfg = ModernBertConfig.from_pretrained(os.path.join(laya_dir, "encoder"))
    cfg._attn_implementation = attn
    cfg.reference_compile = False
    enc = ModernBertModel(cfg)
    sd = load_file(os.path.join(laya_dir, "model.safetensors"))
    sd = {k[len("encoder."):]: v.float() for k, v in sd.items() if k.startswith("encoder.")}
    missing, unexpected = enc.load_state_dict(sd, strict=False)
    missing = [m for m in missing if "rotary" not in m and "inv_freq" not in m]
    assert not missing and not unexpected, (missing, unexpected)
    return enc.float()


def export_friendly_masks(enc, neg=-60.0):
    """ModernBERT builds additive attention masks with float32 min (-3.4e38). On a quantized NPU graph
    that single value blows up the activation range of the attention scores and every real score
    collapses. Use a bounded -60 instead (softmax weight < 1e-26, identical in fp32). Export only."""
    import types

    def _update_attention_mask(self, attention_mask, output_attentions):
        am = attention_mask.to(torch.float32)
        n = am.shape[1]
        glob = ((1.0 - am)[:, None, None, :] * neg).expand(am.shape[0], 1, n, n)
        rows = torch.arange(n, device=am.device).unsqueeze(0)
        window = (torch.abs(rows - rows.T) <= self.config.local_attention // 2)[None, None]
        return glob, glob.masked_fill(window.logical_not(), neg)

    enc._update_attention_mask = types.MethodType(_update_attention_mask, enc)
    return enc


class LoRALinear(nn.Module):
    def __init__(self, base: nn.Linear, r=16, alpha=32, dropout=0.05):
        super().__init__()
        self.base, self.scale = base, alpha / r
        self.A = nn.Parameter(torch.randn(r, base.in_features) * 0.01)
        self.B = nn.Parameter(torch.zeros(base.out_features, r))
        self.drop = nn.Dropout(dropout)
        for p in self.base.parameters():
            p.requires_grad = False

    def forward(self, x):
        return self.base(x) + (self.drop(x) @ self.A.t() @ self.B.t()) * self.scale

    @torch.no_grad()
    def merged(self):
        self.base.weight += (self.B @ self.A) * self.scale
        return self.base


def add_lora(enc, r=16, alpha=32):
    for p in enc.parameters():  # freeze the whole encoder; only LoRA A/B (and the heads) train
        p.requires_grad = False
    for name, mod in list(enc.named_modules()):
        for tgt in LORA_TARGETS:
            if name.endswith(tgt) and isinstance(mod, nn.Linear):
                parent = enc.get_submodule(name.rsplit(".", 1)[0])
                setattr(parent, name.rsplit(".", 1)[1], LoRALinear(mod, r, alpha))
    return enc


def merge_lora(enc):
    for name, mod in list(enc.named_modules()):
        if isinstance(mod, LoRALinear):
            parent = enc.get_submodule(name.rsplit(".", 1)[0])
            setattr(parent, name.rsplit(".", 1)[1], mod.merged())
    return enc


def mean_pool(h, mask):
    m = mask.unsqueeze(-1).to(h.dtype)
    return (h * m).sum(1) / m.sum(1).clamp(min=1.0)


class LayaClassifier(nn.Module):
    def __init__(self, enc, n_types, n_fields):
        super().__init__()
        self.enc = enc
        d = enc.config.hidden_size
        self.type_head = nn.Sequential(nn.Dropout(0.1), nn.Linear(d, n_types))
        self.rel_head = nn.Linear(d, 2)
        self.field_head = nn.Linear(d, n_fields)

    def forward(self, input_ids, attention_mask):
        h = self.enc(input_ids=input_ids.long(), attention_mask=attention_mask.long()).last_hidden_state
        return self.type_head(mean_pool(h, attention_mask)), self.rel_head(h), self.field_head(h)


class SingleOutput(nn.Module):
    """Export wrapper: SNPE on the HTP only writes the last output of multi-output graphs, so the three heads
    are flattened and concatenated into one tensor: [type (n_types) | rel (L*2) | fields (L*n_fields)]."""
    def __init__(self, model):
        super().__init__()
        self.model = model

    def forward(self, input_ids, attention_mask):
        t, r, f = self.model(input_ids, attention_mask)
        return torch.cat([t.reshape(1, -1), r.reshape(1, -1), f.reshape(1, -1)], dim=1)


def split_single_output(x, n_types, n_fields):
    x = np.asarray(x).reshape(-1)
    return x[:n_types], x[n_types:n_types + L * 2].reshape(L, 2), x[n_types + L * 2:].reshape(L, n_fields)


class LayaEmbedder(nn.Module):
    """Un-fine-tuned Laya: L2-normalised mean-pooled embedding (for zero-shot)."""
    def __init__(self, enc):
        super().__init__()
        self.enc = enc

    def forward(self, input_ids, attention_mask):
        h = self.enc(input_ids=input_ids.long(), attention_mask=attention_mask.long()).last_hidden_state
        return F.normalize(mean_pool(h, attention_mask), dim=-1)


def trainable_state(model):
    """LoRA A/B + heads only (small file to download from Colab)."""
    return {k: v.detach().cpu() for k, v in model.state_dict().items()
            if k.endswith(".A") or k.endswith(".B") or not k.startswith("enc.")}


# ----------------------------------------------------------------------------- zero-shot prototypes
def prototype_texts(tax):
    """One description per label, built only from the taxonomy (no dataset text)."""
    texts = {}
    for t in tax["incident_types"]:
        seeds = [s for s in t.get("seed_phrases", []) if s.isascii()][:4]
        texts[t["id"]] = f"I want to report a {t['label'].lower()}. " + " ".join(seeds)
    texts["undetermined"] = "Hi, I need to report something. Hello, can I tell you about a problem?"
    texts["none"] = "This is not an incident. I have a general question about leave, payroll or how the app works."
    return texts


def departments(tax):
    """type id -> department id, and department id -> list of its type ids (taxonomy order)."""
    t2d = {t["id"]: t["dept"] for t in tax["incident_types"]}
    d2t = {}
    for t, d in t2d.items():
        d2t.setdefault(d, []).append(t)
    return t2d, d2t


def department_prototype_texts(tax):
    """One description per department, built only from the taxonomy (name, roles, its incident labels)."""
    _, d2t = departments(tax)
    label = {t["id"]: t["label"].lower() for t in tax["incident_types"]}
    out = {}
    for d in tax["departments"]:
        roles = ", ".join(r["name"] for r in d["roles"])
        out[d["id"]] = (f"I want to report a problem for {d['name']} ({roles}). "
                        f"For example: {', '.join(label[t] for t in d2t.get(d['id'], []))}.")
    return out


def zero_shot(S, P, Pd, types, tax, scale=20.0):
    """S: [N, d] input embeddings, P: [len(types), d] label embeddings, Pd: [n_dept, d] department embeddings
    (order of department_prototype_texts). All L2-normalised. Returns (flat_logits, two_stage_logits, dept_true,
    dept_pred): flat = all 30 labels at once; two-stage = department (+ none/undetermined) first, then only that
    department's types, both from the same embedding (one forward pass per input)."""
    t2d, d2t = departments(tax)
    dept_ids = list(department_prototype_texts(tax))
    flat = S @ P.T * scale
    ui, ni = types.index("undetermined"), types.index("none")
    stage1 = np.concatenate([S @ Pd.T, S @ P[[ui, ni]].T], axis=1)  # 6 departments + undetermined + none
    s1 = stage1.argmax(1)
    two = np.full_like(flat, -1e9)
    for i, c in enumerate(s1):
        if c < len(dept_ids):
            idx = [types.index(t) for t in d2t[dept_ids[c]]]
            two[i, idx] = flat[i, idx]
        else:
            two[i, ui if c == len(dept_ids) else ni] = 0.0
    names = dept_ids + ["undetermined", "none"]
    dept_pred = [names[c] for c in s1]
    return flat, two, names, dept_pred


def two_stage_from_logits(type_logits, types, tax):
    """For classifier heads: stage 1 = department (sum of its types' probabilities) or undetermined / none,
    stage 2 = best type inside the chosen department. Returns (masked logits, predicted stage-1 label)."""
    _, d2t = departments(tax)
    z = np.asarray(type_logits, np.float64)
    p = np.exp(z - z.max(1, keepdims=True)); p /= p.sum(1, keepdims=True)
    groups = [(d, [types.index(t) for t in ts]) for d, ts in d2t.items()] + \
             [("undetermined", [types.index("undetermined")]), ("none", [types.index("none")])]
    g = np.stack([p[:, idx].sum(1) for _, idx in groups], 1).argmax(1)
    out = np.full_like(z, -1e9)
    for i, c in enumerate(g):
        idx = groups[c][1]
        out[i, idx] = z[i, idx]
    return out, [groups[c][0] for c in g]


def dept_of_label(label, tax):
    t2d, _ = departments(tax)
    return t2d.get(label, label)  # undetermined/none map to themselves


def encode_text(tokenizer, text):
    ids = [CLS] + tokenizer.encode(" " + text, add_special_tokens=False).ids[: L - 2] + [SEP]
    n = len(ids)
    return np.array(ids + [PAD] * (L - n), np.int32), np.array([1] * n + [0] * (L - n), np.int32)


# ----------------------------------------------------------------------------- metrics
def macro_f1(y, p, labels):
    f = []
    for c in labels:
        tp = np.sum((p == c) & (y == c)); fp = np.sum((p == c) & (y != c)); fn = np.sum((p != c) & (y == c))
        if tp + fp + fn:
            f.append(2 * tp / (2 * tp + fp + fn))
    return float(np.mean(f)) if f else 0.0


def evaluate(samples, type_logits, rel_logits=None, field_logits=None, types=None):
    y = np.array([s["type"] for s in samples]); p = np.asarray(type_logits).argmax(-1)
    last = np.array([s["last_turn"] for s in samples])
    known = np.array([types[t] not in ("undetermined",) for t in y]) if types else np.ones_like(last)
    res = {"n_samples": len(samples),
           "type_acc_all_turns": float((p == y).mean()),
           "type_acc_final_turn": float((p[last] == y[last]).mean()),
           "type_macro_f1": macro_f1(y, p, range(len(types) if types else int(y.max()) + 1)),
           "type_acc_known_turns": float((p[known] == y[known]).mean())}
    if rel_logits is not None:
        sy, sp, fy, fp_ = [], [], [], []
        for s, rl, fl in zip(samples, np.asarray(rel_logits), np.asarray(field_logits)):
            for j in range(len(s["sent_rel"])):
                tok = s["sent_index"] == j
                if not tok.any():
                    continue
                sy.append(s["sent_rel"][j]); sp.append(int(rl[tok].mean(0).argmax()))
                fy.append(s["sent_fields"][j]); fp_.append((1 / (1 + np.exp(-fl[tok].mean(0)))) > 0.5)
        sy, sp = np.array(sy), np.array(sp)
        fy, fp_ = np.array(fy) > 0.5, np.array(fp_)
        tp = (fy & fp_).sum(); prec = tp / max(1, fp_.sum()); rec = tp / max(1, fy.sum())
        res |= {"relevance_acc": float((sy == sp).mean()),
                "filler_f1": macro_f1(sy, sp, [0]),
                "fields_micro_f1": float(2 * prec * rec / max(1e-9, prec + rec))}
    return res
