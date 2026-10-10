"""Collect bench/out/*/ (export.json, convert/quant status, device/<rt>/diagview.txt) into one table.
Usage: python bench/results.py [SoC ...]   (default SM8750P) -> markdown table per SoC, bench/results.json"""
import glob, json, os, re, sys

BENCH = os.path.dirname(os.path.abspath(__file__))
RTS = ["cpu", "gpu", "dsp"]
SOCS = sys.argv[1:] or ["SM8750P"]


def read(p):
    return open(p).read() if os.path.exists(p) else ""


def status(d, step):
    s = read(os.path.join(d, f"{step}.status"))
    m = re.search(r"OK ([\d.]+)s", s)
    return ("OK", float(m.group(1))) if m else (("FAIL", None) if "FAIL" in s else ("-", None))


def device(d, soc, rt):
    p = os.path.join(d, "device", soc, rt)
    st = read(os.path.join(p, "status"))
    if "OK" not in st:
        return {"status": "FAIL" if "FAIL" in st else ("skip" if st else "-")}
    txt = read(os.path.join(p, "diagview.txt"))
    inf = re.search(r"Total Inference Time: (\d+) us", txt)
    init = re.search(r"\n  Init: (\d+) us", txt)
    return {"status": "OK", "infer_ms": int(inf.group(1)) / 1000 if inf else None,
            "init_ms": int(init.group(1)) / 1000 if init else None}


rows = []
for d in sorted(glob.glob(os.path.join(BENCH, "out/*_L*/"))):
    name, L = os.path.basename(d.rstrip("/")).rsplit("_L", 1)
    ex = json.loads(read(os.path.join(d, "export.json")) or "{}")
    row = {"model": name, "L": int(L), "params_M": round(ex.get("params", 0) / 1e6, 2),
           "onnx_nodes": ex.get("onnx_nodes"), "ort_diff": ex.get("ort_vs_torch_maxdiff"),
           "convert": status(d, "convert"), "quant": status(d, "quant"),
           "dlc_MB": round(os.path.getsize(d + "model.dlc") / 2**20, 1) if os.path.exists(d + "model.dlc") else None,
           "dlc_q_MB": round(os.path.getsize(d + "model_q.dlc") / 2**20, 1) if os.path.exists(d + "model_q.dlc") else None}
    for soc in SOCS:
        row[soc] = {rt: device(d, soc, rt) for rt in RTS}
    rows.append(row)

json.dump(rows, open(os.path.join(BENCH, "results.json"), "w"), indent=1)


def ms(r):
    return f"{r['infer_ms']:.1f}" if r.get("status") == "OK" and r.get("infer_ms") is not None else r.get("status", "-")


for soc in SOCS:
    print(f"\n### {soc}\n")
    print("| model | L | params | dlc fp32 MB | dlc int8 MB | convert s | quant s | CPU ms | GPU ms | DSP/HTP ms | DSP/HTP init ms |")
    print("|---|---|---|---|---|---|---|---|---|---|---|")
    for r in rows:
        c, q, dv = r["convert"], r["quant"], r[soc]
        print(f"| {r['model']} | {r['L']} | {r['params_M']}M | {r['dlc_MB']} | {r['dlc_q_MB']} | "
              f"{c[1] if c[0] == 'OK' else c[0]} | {q[1] if q[0] == 'OK' else q[0]} | "
              f"{ms(dv['cpu'])} | {ms(dv['gpu'])} | {ms(dv['dsp'])} | {dv['dsp'].get('init_ms', '-')} |")
