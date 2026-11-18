"""Periksa hasil FontTest: nama/jenis/jumlah font sesuai harapan, dan hasil WOFF sama isinya dengan TTF asli."""
import os
import sys

from fontTools.ttLib import TTFont

hasil = {}
for line in open(sys.argv[1], encoding="utf-8"):
    line = line.rstrip("\n")
    if " => " in line:
        k, v = line.split(" => ", 1)
        hasil[k] = ("ok", [x.split(" ")[0] for x in v.split(" | ")])
    elif " !! " in line:
        k, v = line.split(" !! ", 1)
        hasil[k] = ("tolak", v)
keluar = sys.argv[2]
uji = sys.argv[3]

gagal = []
def harap(nama, jenis, isi):
    h = hasil.get(nama)
    if h is None or h[0] != jenis or (isi is not None and (h[1] != isi if jenis == "ok" else isi not in h[1])):
        gagal.append(f"{nama}: dapat {h}, harap {jenis} {isi}")

harap("asli.ttf", "ok", ["asli.ttf"])
harap("NanaFont-Regular.ttf.otf", "ok", ["NanaFont-Regular.ttf"])
harap("tanpa-ekstensi", "ok", ["tanpa-ekstensi.ttf"])
harap("web.woff", "ok", ["web.ttf"])
harap("web.woff2", "tolak", "WOFF2")
harap("Poppins.zip", "ok", ["Poppins-Regular.ttf", "Poppins-Bold.ttf", "Poppins-Italic.ttf", "Poppins-BoldItalic.ttf"])
harap("dua-font.zip", "ok", ["Satu.ttf", "Dua.ttf"])
harap("kosong.zip", "tolak", "tidak ada berkas font")
harap("Fix Font nana.pdf", "tolak", "PDF")
harap("gambar.otf", "tolak", "gambar")
harap("teks.ttf", "tolak", "bukan font")

# Hasil WOFF -> TTF: tiap tabel sama persis dengan isi WOFF-nya (dibuka fontTools), kecuali checksum di 'head'.
asli = TTFont(os.path.join(uji, "web.woff"))
for nama in ("web.woff.0.ttf", "dua-font.zip.1.ttf"):
    p = os.path.join(keluar, nama)
    if not os.path.exists(p):
        gagal.append(f"{nama}: tidak ada"); continue
    jadi = TTFont(p)
    for tag in asli.reader.keys():
        a = asli.reader[tag]; b = jadi.reader[tag]
        if tag == "head":
            a = a[:8] + a[12:]; b = b[:8] + b[12:]
        if a != b:
            gagal.append(f"{nama}: tabel {tag} beda")
    if jadi.getGlyphOrder() != asli.getGlyphOrder():
        gagal.append(f"{nama}: urutan glyph beda")
    print(nama, "->", len(jadi.getGlyphOrder()), "glyph, tabel", sorted(jadi.reader.keys()))

if gagal:
    print("GAGAL:")
    for g in gagal:
        print(" -", g)
    sys.exit(1)
print("SEMUA LULUS")
