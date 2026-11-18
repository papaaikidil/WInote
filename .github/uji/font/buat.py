"""Berkas uji impor font: TTF asli, nama ganda, WOFF, WOFF2, ZIP ala Google Fonts, PDF, gambar, teks."""
import io
import os
import shutil
import sys
import zipfile

from fontTools.ttLib import TTFont

src = sys.argv[1]
out = sys.argv[2]
os.makedirs(out, exist_ok=True)
raw = open(src, "rb").read()

shutil.copy(src, os.path.join(out, "asli.ttf"))
# Nama berkas aneh (seperti kiriman pengguna): ekstensi ganda, dan .otf padahal isinya TrueType.
shutil.copy(src, os.path.join(out, "NanaFont-Regular.ttf.otf"))
shutil.copy(src, os.path.join(out, "tanpa-ekstensi"))

f = TTFont(src)
f.flavor = "woff"
f.save(os.path.join(out, "web.woff"))
# WOFF2 cukup kepalanya saja (yang diuji: ditolak dengan pesan yang tepat); pembuatnya butuh brotli.
open(os.path.join(out, "web.woff2"), "wb").write(b"wOF2" + raw[4:200])

with zipfile.ZipFile(os.path.join(out, "Poppins.zip"), "w") as z:
    z.writestr("Poppins-VariableFont_wght.ttf", raw)
    z.writestr("Poppins-Italic-VariableFont_wght.ttf", raw)
    for st in ("Thin", "Regular", "Bold", "Italic", "BoldItalic", "Black"):
        z.writestr(f"static/Poppins-{st}.ttf", raw)
    z.writestr("OFL.txt", "lisensi")
    z.writestr("README.txt", "baca")
    z.writestr("__MACOSX/static/._Poppins-Regular.ttf", b"\x00\x05\x16\x07sampah")

with zipfile.ZipFile(os.path.join(out, "dua-font.zip"), "w") as z:
    z.writestr("fonts/Satu.ttf", raw)
    z.writestr("fonts/Dua.woff", open(os.path.join(out, "web.woff"), "rb").read())

with zipfile.ZipFile(os.path.join(out, "kosong.zip"), "w") as z:
    z.writestr("catatan.txt", "bukan font")

open(os.path.join(out, "Fix Font nana.pdf"), "wb").write(b"%PDF-1.4\n%\xe2\xe3\xcf\xd3\n1 0 obj\n<<>>\nendobj\n")
open(os.path.join(out, "gambar.otf"), "wb").write(b"\x89PNG\r\n\x1a\n" + b"\x00" * 64)
open(os.path.join(out, "teks.ttf"), "wb").write(b"ini bukan font sama sekali\n" * 4)
print("berkas uji:", sorted(os.listdir(out)))
