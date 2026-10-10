"""Render datagen/DATASET.md to datagen/doc/dataset.html and dataset.pdf (headless Chrome).
Usage: python3 datagen/doc/make_pdf.py   (needs the `markdown` package; system python has it)"""
import os, subprocess
import markdown

HERE = os.path.dirname(os.path.abspath(__file__))
body = markdown.markdown(open(os.path.join(HERE, "..", "DATASET.md")).read(), extensions=["tables"])
css = """
:root { --ink:#1d2329; --muted:#5d6670; --line:#d9dde2; --soft:#f4f6f8; --acc:#2f5d8a; --bad:#c0392b; }
@page { size: A4; margin: 16mm 15mm; }
body { background:#fff; color:var(--ink); font:9.8pt/1.5 "Noto Sans","DejaVu Sans",Arial,sans-serif; margin:0; }
main { max-width:180mm; margin:0 auto; }
h1 { font-size:20pt; margin:0 0 1mm; letter-spacing:-0.3px; }
h1 + p em { color:var(--muted); font-style:normal; }
h2 { font-size:13pt; margin:7mm 0 2.5mm; padding-bottom:1.5mm; border-bottom:1.5px solid var(--ink); }
h3 { font-size:10.5pt; margin:4mm 0 1.5mm; color:var(--acc); }
p { margin:0 0 2.5mm; } ul, ol { margin:0 0 3mm; padding-left:5mm; } li { margin-bottom:1.1mm; }
table { border-collapse:collapse; width:100%; font-size:8.6pt; margin:2mm 0 3.5mm; break-inside:auto; }
th, td { padding:1.3mm 1.8mm; border-bottom:1px solid var(--line); text-align:left; vertical-align:top; }
th { background:var(--soft); font-weight:600; }
tr { break-inside:avoid; }
code { font-family:"JetBrainsMono NF","Adwaita Mono",monospace; font-size:8.3pt; background:var(--soft); padding:0 1mm; }
strong { font-weight:700; }
td { overflow-wrap:break-word; } td code { font-size:7.4pt; overflow-wrap:anywhere; }
@media (max-width:700px) { main { padding:0 16px; } table { display:block; overflow-x:auto; } }
"""
html = f'<!doctype html><html lang="en"><head><meta charset="utf-8"><title>Fine-tuning Dataset</title><style>{css}</style></head><body><main>{body}</main></body></html>'
open(os.path.join(HERE, "dataset.html"), "w").write(html)
pdf = os.path.join(HERE, "dataset.pdf")
subprocess.run(["google-chrome-stable", "--headless=new", "--disable-gpu", "--no-pdf-header-footer",
                f"--print-to-pdf={pdf}", "file://" + os.path.join(HERE, "dataset.html")],
               check=True, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
print(pdf, os.path.getsize(pdf), "bytes")
