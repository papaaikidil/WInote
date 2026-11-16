#!/usr/bin/env python3
"""Bandingkan layer hasil pengurai Kotlin dengan yang diharapkan (selisih per kanal)."""
import glob
import json
import os
import sys

import numpy as np

fx, out = sys.argv[1], sys.argv[2]
fail = 0
for meta_path in sorted(glob.glob(os.path.join(fx, "*.json"))):
    name = os.path.basename(meta_path)[:-5]
    meta = json.load(open(meta_path))
    w, h = meta["w"], meta["h"]
    n = len(meta["names"])
    for i in range(n):
        e = np.fromfile(os.path.join(fx, f"{name}.{i}.rgba"), np.uint8).reshape(h, w, 4).astype(int)
        p = os.path.join(out, f"{name}.{i}.rgba")
        if not os.path.exists(p):
            print(f"GAGAL {name} layer {i}: tidak ada keluaran")
            fail += 1
            continue
        g = np.fromfile(p, np.uint8).reshape(h, w, 4).astype(int)
        d = np.abs(e - g)
        bad = (d.max(axis=2) > 2).sum()
        status = "ok" if bad == 0 else "GAGAL"
        if bad:
            fail += 1
            ys, xs = np.nonzero(d.max(axis=2) > 2)
            print(f"  contoh beda di ({xs[0]},{ys[0]}): harap {e[ys[0], xs[0]]} dapat {g[ys[0], xs[0]]}")
        print(f"{status} {name} layer {i} '{meta['names'][i]}': selisih maks {d.max()}, piksel beda {bad}")
print("SEMUA LULUS (banding)" if fail == 0 else f"ADA {fail} KEGAGALAN (banding)")
