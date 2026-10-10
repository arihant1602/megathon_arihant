"""Whisper encoder from a whisper.cpp ggml file -> ONNX with a fixed window, for the NPU (see build_encoder_dlc.sh).

  python export_encoder.py ggml-base.bin encoder_1500.onnx 1500   # 30 s window: mel [1,80,3000] -> embd [1,1500,512]
  python export_encoder.py ggml-base.bin encoder_500.onnx 500     # 10 s window: mel [1,80,1000] -> embd [1,500,512]
"""
import struct, sys, numpy as np, torch, torch.nn as nn, torch.nn.functional as F

def read_ggml(path):
    f = open(path, "rb")
    assert struct.unpack("<I", f.read(4))[0] == 0x67676d6c
    hp = dict(zip(["n_vocab", "n_audio_ctx", "n_audio_state", "n_audio_head", "n_audio_layer", "n_text_ctx",
                   "n_text_state", "n_text_head", "n_text_layer", "n_mels", "ftype"], struct.unpack("<11i", f.read(44))))
    n_mel, n_fft = struct.unpack("<2i", f.read(8))
    filters = np.frombuffer(f.read(4 * n_mel * n_fft), np.float32).reshape(n_mel, n_fft)
    n_vocab = struct.unpack("<i", f.read(4))[0]
    for _ in range(n_vocab):
        f.read(struct.unpack("<i", f.read(4))[0])
    tensors = {}
    while True:
        head = f.read(12)
        if len(head) < 12:
            break
        n_dims, name_len, ttype = struct.unpack("<3i", head)
        ne = struct.unpack(f"<{n_dims}i", f.read(4 * n_dims))
        name = f.read(name_len).decode()
        dt = {0: np.float32, 1: np.float16}[ttype]
        n = int(np.prod(ne))
        data = np.frombuffer(f.read(n * np.dtype(dt).itemsize), dt).astype(np.float32).reshape(tuple(reversed(ne)))
        tensors[name] = data
    return hp, filters, tensors

class Block(nn.Module):
    def __init__(s, d, h):
        super().__init__(); s.h = h
        s.attn_ln = nn.LayerNorm(d); s.q = nn.Linear(d, d); s.k = nn.Linear(d, d, bias=False); s.v = nn.Linear(d, d); s.o = nn.Linear(d, d)
        s.mlp_ln = nn.LayerNorm(d); s.fc1 = nn.Linear(d, 4 * d); s.fc2 = nn.Linear(4 * d, d)
    def forward(s, x):
        y = s.attn_ln(x); B, T, D = y.shape; hd = D // s.h
        q = s.q(y).view(B, T, s.h, hd).transpose(1, 2); k = s.k(y).view(B, T, s.h, hd).transpose(1, 2); v = s.v(y).view(B, T, s.h, hd).transpose(1, 2)
        a = torch.softmax((q @ k.transpose(-1, -2)) / (hd ** 0.5), -1) @ v
        x = x + s.o(a.transpose(1, 2).reshape(B, T, D))
        return x + s.fc2(F.gelu(s.fc1(s.mlp_ln(x))))

class Encoder(nn.Module):
    def __init__(s, hp):
        super().__init__(); d = hp["n_audio_state"]
        s.conv1 = nn.Conv1d(hp["n_mels"], d, 3, padding=1); s.conv2 = nn.Conv1d(d, d, 3, stride=2, padding=1)
        s.register_buffer("pos", torch.zeros(hp["n_audio_ctx"], d))
        s.blocks = nn.ModuleList([Block(d, hp["n_audio_head"]) for _ in range(hp["n_audio_layer"])]); s.ln_post = nn.LayerNorm(d)
    n_ctx = 1500  # window (audio_ctx); the positional embedding is sliced to it, as whisper.cpp does

    def forward(s, mel):
        x = F.gelu(s.conv2(F.gelu(s.conv1(mel)))).transpose(1, 2) + s.pos[: s.n_ctx]
        for b in s.blocks:
            x = b(x)
        return s.ln_post(x)

def load(path):
    hp, filters, t = read_ggml(path)
    m = Encoder(hp); T = lambda n: torch.from_numpy(t[n].copy())
    with torch.no_grad():
        m.conv1.weight.copy_(T("encoder.conv1.weight")); m.conv1.bias.copy_(T("encoder.conv1.bias").reshape(-1))
        m.conv2.weight.copy_(T("encoder.conv2.weight")); m.conv2.bias.copy_(T("encoder.conv2.bias").reshape(-1))
        m.pos.copy_(T("encoder.positional_embedding"))
        for i, b in enumerate(m.blocks):
            p = f"encoder.blocks.{i}."
            for mod, n in [(b.attn_ln, "attn_ln"), (b.mlp_ln, "mlp_ln")]:
                mod.weight.copy_(T(p + n + ".weight")); mod.bias.copy_(T(p + n + ".bias"))
            for mod, n in [(b.q, "attn.query"), (b.k, "attn.key"), (b.v, "attn.value"), (b.o, "attn.out"), (b.fc1, "mlp.0"), (b.fc2, "mlp.2")]:
                mod.weight.copy_(T(p + n + ".weight"))
                if mod.bias is not None: mod.bias.copy_(T(p + n + ".bias"))
        m.ln_post.weight.copy_(T("encoder.ln_post.weight")); m.ln_post.bias.copy_(T("encoder.ln_post.bias"))
    return hp, filters, m.eval()

if __name__ == "__main__":
    hp, filters, m = load(sys.argv[1])
    m.n_ctx = int(sys.argv[3]) if len(sys.argv) > 3 else hp["n_audio_ctx"]
    mel = torch.randn(1, hp["n_mels"], 2 * m.n_ctx)
    torch.onnx.export(m, (mel,), sys.argv[2], input_names=["mel"], output_names=["embd"], opset_version=17)
    print("exported", sys.argv[2], "window", m.n_ctx)
