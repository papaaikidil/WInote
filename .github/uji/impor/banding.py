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
# TIFF dari Tiff.write
try:
    from PIL import Image
    tw, th = 203, 77
    st = np.fromfile(os.path.join(out, "tiff.straight.rgba"), np.uint8).reshape(th, tw, 4).astype(int)
    pm = np.fromfile(os.path.join(out, "tiff.premul.rgba"), np.uint8).reshape(th, tw, 4).astype(int)
    im = Image.open(os.path.join(out, "uji-rgba.tif"))
    print("TIFF RGBA:", im.mode, im.size, im.info.get("dpi"), im.info.get("compression"))
    g = np.array(im.convert("RGBA")).astype(int)
    vis = st[..., 3] > 0
    d = np.abs(g - st)[vis].max()
    da = np.abs(g[..., 3] - st[..., 3]).max()
    # Warna dari premultiplied 8 bit hanya bisa dipulihkan kira-kira bila alfa kecil.
    ok = da == 0 and d <= 3
    print(("ok" if ok else "GAGAL") + f" TIFF RGBA: selisih warna maks {d}, alfa {da}")
    fail += 0 if ok else 1
    im2 = Image.open(os.path.join(out, "uji-rgb.tif"))
    print("TIFF RGB:", im2.mode, im2.size, im2.info.get("dpi"))
    g2 = np.array(im2.convert("RGB")).astype(int)
    d2 = np.abs(g2 - pm[..., :3]).max()
    print(("ok" if d2 == 0 else "GAGAL") + f" TIFF RGB: selisih maks {d2}")
    fail += 0 if d2 == 0 else 1
except Exception as e:
    print("GAGAL TIFF:", e)
    fail += 1
print("SEMUA LULUS (banding)" if fail == 0 else f"ADA {fail} KEGAGALAN (banding)")
