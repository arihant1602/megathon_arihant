"""Export Laya to ONNX for QAIRT, plus calibration and test inputs for the device.
Usage: python laya/export.py base            -> laya/out/base/   (un-fine-tuned, outputs a normalised embedding)
       python laya/export.py ft laya_ft.pt   -> laya/out/ft/     (fine-tuned, LoRA merged, outputs 3 heads)
Static [1, 128] int32 inputs, eager attention, opset 17. Checks onnxruntime against PyTorch."""
import json, os, sys, time
import numpy as np, onnx, onnxruntime as ort, torch

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(HERE)
sys.path.insert(0, HERE)
from common import *  # noqa: E402
from tokenizers import Tokenizer  # noqa: E402
from paths import laya_directory

LD = laya_directory()
N_CALIB = 50


def write_inputs(d, name, pairs):
    """pairs: list of (ids, mask) -> d/<name>/ids_NNN.raw, mask_NNN.raw and d/<name>.txt input list."""
    os.makedirs(os.path.join(d, name), exist_ok=True)
    lines = []
    for k, (ids, mask) in enumerate(pairs):
        ids.reshape(1, L).astype(np.int32).tofile(os.path.join(d, name, f"ids_{k:03d}.raw"))
        mask.reshape(1, L).astype(np.int32).tofile(os.path.join(d, name, f"mask_{k:03d}.raw"))
        lines.append(f"input_ids:={name}/ids_{k:03d}.raw attention_mask:={name}/mask_{k:03d}.raw")
    open(os.path.join(d, f"{name}.txt"), "w").write("\n".join(lines) + "\n")


def main(mode, ckpt=None):
    tok = Tokenizer.from_file(os.path.join(LD, "tokenizer/tokenizer.json"))
    tax, types, fields = label_space()
    train_c, _ = train_val_split(load_conversations(os.path.join(ROOT, "datagen/out"), "train"))
    test = build_samples(load_conversations(os.path.join(ROOT, "datagen/out"), "test"), tok, types, fields)
    train = build_samples(train_c, tok, types, fields)
    d = os.path.join(HERE, "out", mode)
    os.makedirs(d, exist_ok=True)

    enc = export_friendly_masks(load_laya_encoder(LD, attn="eager"))  # bounded masks for quantization
    if mode == "base":
        model = LayaEmbedder(enc).eval()
        names = ["embedding"]
    else:
        ck = torch.load(ckpt, map_location="cpu", weights_only=True)
        assert ck["types"] == types and ck["fields"] == fields, "label space differs from the checkpoint"
        model = LayaClassifier(add_lora(enc, ck["lora"]["r"], ck["lora"]["alpha"]), len(types), len(fields))
        missing, unexpected = model.load_state_dict(ck["state"], strict=False)
        assert not unexpected and all(".base." in m or not (m.endswith(".A") or m.endswith(".B")) for m in missing)
        merge_lora(model.enc)
        model.eval()
        model = SingleOutput(model).eval()  # one output tensor (see SingleOutput)
        names = ["logits"]
        json.dump({k: ck[k] for k in ("temperature", "test_fp32_finetuned", "test_fp32_zeroshot", "config")},
                  open(os.path.join(d, "colab_metrics.json"), "w"), indent=1)

    # inputs: calibration from train (spread), device test = all test samples (+ prototypes for base)
    pick = [train[i] for i in np.linspace(0, len(train) - 1, N_CALIB).astype(int)]
    write_inputs(d, "calib", [(s["input_ids"], s["attention_mask"]) for s in pick])
    test_pairs = [(s["input_ids"], s["attention_mask"]) for s in test]
    if mode == "base":
        protos = prototype_texts(tax)
        test_pairs += [encode_text(tok, protos[t]) for t in types]
        test_pairs += [encode_text(tok, x) for x in department_prototype_texts(tax).values()]
    write_inputs(d, "test", test_pairs)

    ids = torch.from_numpy(test[0]["input_ids"]).unsqueeze(0)
    mask = torch.from_numpy(test[0]["attention_mask"]).unsqueeze(0)
    path = os.path.join(d, "model.onnx")
    t0 = time.time()
    with torch.no_grad():
        ref = model(ids, mask)
        ref = [ref.numpy()] if isinstance(ref, torch.Tensor) else [r.numpy() for r in ref]
        torch.onnx.export(model, (ids, mask), path, opset_version=17,
                          input_names=["input_ids", "attention_mask"], output_names=names)
    sess = ort.InferenceSession(path, providers=["CPUExecutionProvider"])
    got = sess.run(None, {"input_ids": ids.numpy(), "attention_mask": mask.numpy()})
    diff = max(float(np.abs(a - b).max()) for a, b in zip(ref, got))
    size = sum(os.path.getsize(os.path.join(d, f)) for f in os.listdir(d) if f.startswith("model.onnx"))
    info = {"mode": mode, "onnx_MB": round(size / 2**20, 1), "nodes": len(onnx.load(path, load_external_data=False).graph.node),
            "export_s": round(time.time() - t0, 1), "ort_vs_torch_maxdiff": diff,
            "n_test_inputs": len(test_pairs), "n_test_samples": len(test)}
    json.dump(info, open(os.path.join(d, "export.json"), "w"), indent=1)
    print(json.dumps(info))


if __name__ == "__main__":
    main(sys.argv[1], sys.argv[2] if len(sys.argv) > 2 else None)
