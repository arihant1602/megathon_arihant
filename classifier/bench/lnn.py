"""Bidirectional CfC (closed-form continuous-time) liquid network, fixed length, unrolled.

Cell (Hasani et al. 2022, no ncps dependency):
    z    = tanh(Wx x_t + Wh h + b)                 backbone
    gate = sigmoid(ta(z) * t + tb(z))              t = 1 (uniform token steps), folded in
    h'   = tanh(f1(z)) * (1 - gate) + gate * tanh(f2(z))
ta/tb/f1/f2 are fused into one Linear(H -> 4H). Wx is applied to all timesteps at once.
Padding (mask == 0) keeps the previous state, so right-padding is harmless in both directions.
"""
import torch
import torch.nn as nn

N_TYPES, N_REL, N_FIELDS = 30, 2, 60


class CfCDirection(nn.Module):
    def __init__(self, d_in, hidden):
        super().__init__()
        self.hidden = hidden
        self.wx = nn.Linear(d_in, hidden)
        self.wh = nn.Linear(hidden, hidden, bias=False)
        self.heads = nn.Linear(hidden, 4 * hidden)

    def forward(self, x, m, reverse=False):
        # x [1, L, D], m [1, L, 1] float
        L = x.shape[1]
        xp = self.wx(x)
        h = x.new_zeros(1, self.hidden)
        out = [None] * L
        for t in (range(L - 1, -1, -1) if reverse else range(L)):
            z = torch.tanh(xp[:, t] + self.wh(h))
            ta, tb, f1, f2 = self.heads(z).chunk(4, dim=-1)
            gate = torch.sigmoid(ta + tb)
            h_new = torch.tanh(f1) * (1 - gate) + gate * torch.tanh(f2)
            mt = m[:, t]
            h = mt * h_new + (1 - mt) * h
            out[t] = h
        return torch.stack(out, dim=1)


class CfCClassifier(nn.Module):
    def __init__(self, vocab, emb=256, hidden=192):
        super().__init__()
        self.emb = nn.Embedding(vocab, emb)
        self.fwd = CfCDirection(emb, hidden)
        self.bwd = CfCDirection(emb, hidden)
        self.type_head = nn.Linear(2 * hidden, N_TYPES)
        self.rel_head = nn.Linear(2 * hidden, N_REL)
        self.field_head = nn.Linear(2 * hidden, N_FIELDS)

    def forward(self, input_ids, attention_mask):
        m = attention_mask.to(torch.float32).unsqueeze(-1)
        x = self.emb(input_ids)
        h = torch.cat([self.fwd(x, m), self.bwd(x, m, reverse=True)], dim=-1)
        pooled = (h * m).sum(1) / m.sum(1).clamp(min=1.0)
        return self.type_head(pooled), self.rel_head(h), self.field_head(h)


def n_params(model):
    return sum(p.numel() for p in model.parameters())


if __name__ == "__main__":
    for vocab, hid in [(8192, 64), (8192, 128), (8192, 192), (50368, 192)]:
        mdl = CfCClassifier(vocab, hidden=hid).eval()
        ids = torch.randint(0, vocab, (1, 128), dtype=torch.int32)
        mask = torch.ones(1, 128, dtype=torch.int32)
        mask[:, 100:] = 0
        t, r, f = mdl(ids, mask)
        print(vocab, hid, f"{n_params(mdl)/1e6:.2f}M", tuple(t.shape), tuple(r.shape), tuple(f.shape))
