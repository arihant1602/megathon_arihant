#!/usr/bin/env python3
"""Replace conversation blocks in datagen/src/train/<type>.txt with same-id blocks from
datagen/rewrite/<type>.txt (both hand-written). Usage: python3 datagen/splice.py <type> ..."""
import os, re, sys
ROOT = os.path.dirname(os.path.abspath(__file__))
for t in sys.argv[1:]:
    main = os.path.join(ROOT, "src", "train", t + ".txt")
    new = {re.match(r"@ (\S+)", b).group(1): b.strip() for b in open(os.path.join(ROOT, "rewrite", t + ".txt")).read().split("\n\n") if b.strip()}
    blocks = [b.strip() for b in open(main).read().split("\n\n") if b.strip()]
    out = [new.pop(re.match(r"@ (\S+)", b).group(1), b) for b in blocks]
    assert not new, f"ids not in {t}: {list(new)}"
    open(main, "w").write("\n\n".join(out) + "\n")
    print(t, "spliced")
