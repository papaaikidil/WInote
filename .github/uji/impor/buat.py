#!/usr/bin/env python3
"""
Membuat berkas uji impor berlapis beserta hasil yang diharapkan:
  - PSD buatan ImageMagick (RLE, ZIP, 16 bit) dengan layer bergeser dan transparan;
  - .procreate buatan sendiri (NSKeyedArchiver biner, ubin LZO dan LZ4 berbingkai Apple, grup, masker);
  - PDF beberapa halaman (untuk uji emulator).
Hasil yang diharapkan disimpan sebagai RGBA premultiplied ukuran kanvas (baris 0 = atas), satu berkas per layer
dari bawah ke atas: <nama>.<i>.rgba, plus <nama>.json berisi sifat layer.
"""
import json
import os
import plistlib
import struct
import subprocess
import sys

import numpy as np
from PIL import Image

out = sys.argv[1]
os.makedirs(out, exist_ok=True)
tmp = os.path.join(out, "_tmp")
os.makedirs(tmp, exist_ok=True)


def premul(a):
    a = a.astype(np.uint32)
    r = a.copy()
    for c in range(3):
        r[..., c] = (a[..., c] * a[..., 3] + 127) // 255
    return r.astype(np.uint8)


def pattern(w, h, seed, alpha_hole=True):
    """Gambar RGBA lurus (tidak premultiplied) dengan gradien, garis, dan lubang transparan."""
    yy, xx = np.mgrid[0:h, 0:w]
    img = np.zeros((h, w, 4), np.uint8)
    img[..., 0] = (xx * 255 // max(1, w - 1) + seed * 40) % 256
    img[..., 1] = (yy * 255 // max(1, h - 1) + seed * 70) % 256
    img[..., 2] = ((xx + yy) * 3 + seed * 90) % 256
    img[..., 3] = 255
    if alpha_hole:
        cx, cy = w // 2, h // 2
        d = (xx - cx) ** 2 + (yy - cy) ** 2
        img[..., 3] = np.where(d < (min(w, h) // 4) ** 2, 0, 255)
        img[..., 3] = np.where((xx // 8) % 5 == 0, 128, img[..., 3])
        img[img[..., 3] == 0] = 0
    return img


def canvas_layer(W, H, img, x, y):
    c = np.zeros((H, W, 4), np.uint8)
    h, w = img.shape[:2]
    x0, y0 = max(0, x), max(0, y)
    x1, y1 = min(W, x + w), min(H, y + h)
    if x1 > x0 and y1 > y0:
        c[y0:y1, x0:x1] = img[y0 - y:y1 - y, x0 - x:x1 - x]
    return c


def over(dst, src):
    """Gabung lurus (straight) src di atas dst, hasil lurus."""
    sa = src[..., 3:4].astype(np.float64) / 255
    da = dst[..., 3:4].astype(np.float64) / 255
    oa = sa + da * (1 - sa)
    oc = (src[..., :3] * sa + dst[..., :3] * da * (1 - sa)) / np.maximum(oa, 1e-6)
    r = np.zeros_like(dst)
    r[..., :3] = np.clip(oc + 0.5, 0, 255).astype(np.uint8)
    r[..., 3:4] = np.clip(oa * 255 + 0.5, 0, 255).astype(np.uint8)
    return r


def save_expect(name, layers, meta):
    for i, l in enumerate(layers):
        premul(l).tofile(os.path.join(out, f"{name}.{i}.rgba"))
    with open(os.path.join(out, f"{name}.json"), "w") as f:
        json.dump(meta, f, indent=1)


# ---------------- PSD dari ImageMagick ----------------
W, H = 320, 240
src = [
    ("Latar", pattern(W, H, 0, alpha_hole=False), 0, 0),
    ("Bergeser", pattern(150, 100, 1), 40, 30),
    ("Keluar tepi", pattern(120, 90, 2), 250, 190),
]
expect = [canvas_layer(W, H, img, x, y) for _, img, x, y in src]
comp = expect[0]
for e in expect[1:]:
    comp = over(comp, e)
Image.fromarray(comp, "RGBA").save(os.path.join(tmp, "comp.png"))
args = [os.path.join(tmp, "comp.png")]
for i, (name, img, x, y) in enumerate(src):
    p = os.path.join(tmp, f"l{i}.png")
    Image.fromarray(img, "RGBA").save(p)
    args += ["-label", name, "-page", f"+{x}+{y}", p]
for tag, extra in [("im-rle", []), ("im-zip", ["-compress", "Zip"]), ("im-16", ["-depth", "16"])]:
    path = os.path.join(out, tag + ".psd")
    subprocess.run(["convert"] + args + extra + [path], check=True)
    save_expect(tag, expect, {"w": W, "h": H, "names": [s[0] for s in src]})
    print("PSD", tag, os.path.getsize(path))

# ---------------- .procreate ----------------
import lz4.block
import lzo

PW, PH, TS = 600, 400, 256
cols, rows = (PW + TS - 1) // TS, (PH + TS - 1) // TS


def tiles_of(img_premul, bpp):
    """Potong gambar (baris 0 = atas) jadi ubin Procreate: baris ubin dihitung dari bawah, data ubin dari bawah."""
    up = img_premul[::-1]  # baris 0 = bawah
    res = {}
    for r in range(rows):
        for c in range(cols):
            tw = min(TS, PW - c * TS)
            th = min(TS, PH - r * TS)
            t = up[r * TS:r * TS + th, c * TS:c * TS + tw]
            if not t.any():
                continue
            res[(c, r)] = np.ascontiguousarray(t).tobytes()
    return res


def lz4_apple(data):
    half = len(data) // 2
    a, b = data[:half], data[half:]
    out_ = b""
    ca = lz4.block.compress(a, store_size=False)
    out_ += b"bv41" + struct.pack("<II", len(a), len(ca)) + ca
    try:
        cb = lz4.block.compress(b, store_size=False, dict=a)
    except TypeError:
        cb = lz4.block.compress(b, store_size=False)
    out_ += b"bv41" + struct.pack("<II", len(b), len(cb)) + cb
    return out_ + b"bv4$"


yy, xx = np.mgrid[0:PH, 0:PW]
bottom = pattern(PW, PH, 3, alpha_hole=False)
mid = np.zeros((PH, PW, 4), np.uint8)
m_in = ((xx - 300) ** 2 + (yy - 200) ** 2) < 150 ** 2
mid[m_in] = (230, 40, 90, 255)
mid[(xx // 20) % 2 == 0] = (20, 200, 120, 180)
mid[yy > 380] = 0
mid2 = np.zeros((PH, PW, 4), np.uint8)
mid2[50:120, 50:550] = (255, 255, 0, 255)
top = np.zeros((PH, PW, 4), np.uint8)
top[200:390, 400:590] = (40, 90, 250, 200)
mask = np.full((PH, PW), 255, np.uint8)
mask[:, :300] = (xx[:, :300] * 255 // 300).astype(np.uint8)

files = {}
uuids = {"bottom": "B0770M00-0000-0000-0000-000000000001", "mid": "M1D00000-0000-0000-0000-000000000002",
         "mid2": "M1D20000-0000-0000-0000-000000000003", "top": "70P00000-0000-0000-0000-000000000004",
         "mask": "MA5K0000-0000-0000-0000-000000000005", "comp": "C0MP0000-0000-0000-0000-000000000006"}
for key, img, comp_ in [("bottom", bottom, "lzo"), ("mid", mid, "lzo"), ("mid2", mid2, "lzo"), ("top", top, "lz4")]:
    for (c, r), data in tiles_of(premul(img), 4).items():
        if comp_ == "lz4":
            files[f"{uuids[key]}/{c}~{r}.lz4"] = lz4_apple(data)
        else:
            files[f"{uuids[key]}/{c}~{r}.chunk"] = lzo.compress(data, 1, False)
mt = mask[::-1]
for r in range(rows):
    for c in range(cols):
        tw = min(TS, PW - c * TS)
        th = min(TS, PH - r * TS)
        t = mt[r * TS:r * TS + th, c * TS:c * TS + tw]
        if (t == 255).all():
            continue
        files[f"{uuids['mask']}/{c}~{r}.chunk"] = lzo.compress(np.ascontiguousarray(t).tobytes(), 1, False)

objs = ["$null"]


def add(o):
    objs.append(o)
    return plistlib.UID(len(objs) - 1)


cls_doc = add({"$classname": "SilicaDocument", "$classes": ["SilicaDocument", "NSObject"]})
cls_layer = add({"$classname": "SilicaLayer", "$classes": ["SilicaLayer", "NSObject"]})
cls_group = add({"$classname": "SilicaGroup", "$classes": ["SilicaGroup", "NSObject"]})
cls_arr = add({"$classname": "NSArray", "$classes": ["NSArray", "NSObject"]})
cls_str = add({"$classname": "NSString", "$classes": ["NSString", "NSObject"]})


def nsstr(s):
    return add({"$class": cls_str, "NS.string": s})


def layer(key, name, opacity=1.0, blend=0, hidden=False, clipped=False, preserve=False, mask_uid=None, typ=0):
    d = {"$class": cls_layer, "UUID": add(uuids[key]), "name": nsstr(name), "opacity": opacity, "blend": blend,
         "hidden": hidden, "clipped": clipped, "preserve": preserve, "type": typ, "version": 2}
    d["mask"] = mask_uid if mask_uid is not None else plistlib.UID(0)
    return add(d)


mask_uid = layer("mask", "Masker", typ=3)
l_bottom = layer("bottom", "Dasar")
l_mid = layer("mid", "Tengah bermasker", opacity=0.75, blend=1, mask_uid=mask_uid)
l_mid2 = layer("mid2", "Tengah tersembunyi", hidden=True, preserve=True)
grp = add({"$class": cls_group, "name": "Grup A", "isHidden": False, "isCollapsed": True, "opacity": 0.5,
           "children": add({"$class": cls_arr, "NS.objects": [l_mid2, l_mid]})})
l_top = layer("top", "Atas LZ4", blend=11, clipped=False)
l_comp = layer("comp", "Gabungan", typ=1)
root = add({
    "$class": cls_doc, "size": add("{600, 400}"), "tileSize": TS, "name": nsstr("Uji Procreate"),
    "backgroundColor": struct.pack("<4f", 0.2, 0.4, 0.6, 1.0), "backgroundHidden": False,
    "flippedHorizontally": False, "flippedVertically": False, "orientation": 1,
    "SilicaDocumentArchiveDPIKey": 264.0,
    "unwrappedLayers": add({"$class": cls_arr, "NS.objects": [l_top, grp, l_bottom]}),
    "composite": l_comp,
})
arch = {"$version": 100000, "$archiver": "NSKeyedArchiver", "$top": {"root": root}, "$objects": objs}
import zipfile

pp = os.path.join(out, "uji-procreate.procreate")
with zipfile.ZipFile(pp, "w", zipfile.ZIP_STORED) as z:
    z.writestr("Document.archive", plistlib.dumps(arch, fmt=plistlib.FMT_BINARY))
    for n, d in files.items():
        z.writestr(n, d)
    z.writestr("QuickLook/Thumbnail.png", b"")
mid_masked = mid.copy()
pm = premul(mid)
for c in range(4):
    pm[..., c] = ((pm[..., c].astype(np.uint32) * mask + 127) // 255).astype(np.uint8)
layers_exp = [premul(bottom), pm, premul(mid2), premul(top)]
for i, l in enumerate(layers_exp):
    l.tofile(os.path.join(out, f"uji-procreate.{i}.rgba"))
with open(os.path.join(out, "uji-procreate.json"), "w") as f:
    json.dump({"w": PW, "h": PH, "names": ["Dasar", "Tengah bermasker", "Tengah tersembunyi", "Atas LZ4"],
               "premultiplied": True}, f)
print("procreate", os.path.getsize(pp), len(files), "ubin")

# ---------------- PDF ----------------
pages = []
for i, col in enumerate([(240, 90, 60), (60, 160, 240), (90, 200, 120)]):
    im = Image.new("RGB", (595, 842), "white")
    a = np.array(im)
    a[100 + i * 150:300 + i * 150, 100:495] = col
    pages.append(Image.fromarray(a))
pages[0].save(os.path.join(out, "uji.pdf"), save_all=True, append_images=pages[1:], resolution=72)
print("pdf", os.path.getsize(os.path.join(out, "uji.pdf")))
