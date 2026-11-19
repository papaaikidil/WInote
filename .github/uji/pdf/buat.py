"""Buat PDF uji (tanpa pustaka): 2 halaman A4 berteks (Helvetica, Times) dan formulir AcroForm (teks, centang, tanda tangan)."""
import sys

out = sys.argv[1]
objs = {}

def obj(n, body):
    objs[n] = body

def stream(n, data, extra=""):
    d = data.encode("latin-1")
    objs[n] = f"<< /Length {len(d)} {extra}>>\nstream\n".encode("latin-1") + d + b"\nendstream"

page1 = """BT /F2 18 Tf 170 780 Td (SURAT PERNYATAAN) Tj ET
BT /F1 12 Tf 60 730 Td (Yang bertanda tangan di bawah ini menyatakan bahwa) Tj 0 -18 Td (minyak jeruk purut dikirim setiap hari Senin.) Tj 0 -18 Td (Harga per kilogram mengikuti kesepakatan bersama.) Tj ET
BT /F1 12 Tf 60 638 Td (Nama:) Tj ET
0.6 w 130 632 270 20 re S
BT /F1 12 Tf 60 600 Td (Setuju:) Tj ET
130 596 16 16 re S
BT /F1 12 Tf 60 540 Td (Tanda tangan:) Tj ET
170 500 m 330 500 l S
BT /F3 12 Tf 60 572 Td (Catatan: dokumen ini dibuat untuk uji fitur edit PDF.) Tj ET"""
page2 = """BT /F3 14 Tf 60 780 Td (Halaman kedua dokumen PDF.) Tj 0 -20 Td (Teks ini memakai huruf Times.) Tj ET"""

obj(1, "<< /Type /Catalog /Pages 2 0 R /AcroForm 10 0 R >>")
obj(2, "<< /Type /Pages /Kids [3 0 R 4 0 R] /Count 2 >>")
res = "/Resources << /Font << /F1 5 0 R /F2 6 0 R /F3 7 0 R >> >>"
obj(3, f"<< /Type /Page /Parent 2 0 R /MediaBox [0 0 595 842] {res} /Contents 8 0 R /Annots [11 0 R 12 0 R 13 0 R] >>")
obj(4, f"<< /Type /Page /Parent 2 0 R /MediaBox [0 0 595 842] {res} /Contents 9 0 R >>")
obj(5, "<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica /Encoding /WinAnsiEncoding >>")
obj(6, "<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica-Bold /Encoding /WinAnsiEncoding >>")
obj(7, "<< /Type /Font /Subtype /Type1 /BaseFont /Times-Roman /Encoding /WinAnsiEncoding >>")
stream(8, page1)
stream(9, page2)
obj(10, "<< /Fields [11 0 R 12 0 R 13 0 R] /NeedAppearances true /DA (/F1 0 Tf 0 g) /DR << /Font << /F1 5 0 R >> >> >>")
obj(11, "<< /Type /Annot /Subtype /Widget /FT /Tx /T (nama) /Rect [130 632 400 652] /P 3 0 R /F 4 /DA (/F1 11 Tf 0 g) >>")
obj(12, "<< /Type /Annot /Subtype /Widget /FT /Btn /T (setuju) /Rect [130 596 146 612] /P 3 0 R /F 4 /V /Off /AS /Off /AP << /N << /Yes 14 0 R /Off 15 0 R >> >> >>")
obj(13, "<< /Type /Annot /Subtype /Widget /FT /Sig /T (ttd) /Rect [170 500 330 550] /P 3 0 R /F 4 >>")
stream(14, "q 0 g 1.5 w 3 8 m 7 3 l 13 13 l S Q", "/Type /XObject /Subtype /Form /BBox [0 0 16 16] ")
stream(15, "", "/Type /XObject /Subtype /Form /BBox [0 0 16 16] ")

buf = bytearray(b"%PDF-1.4\n%\xe2\xe3\xcf\xd3\n")
offs = {}
for n in sorted(objs):
    offs[n] = len(buf)
    body = objs[n]
    if isinstance(body, str):
        body = body.encode("latin-1")
    buf += f"{n} 0 obj\n".encode() + body + b"\nendobj\n"
xref = len(buf)
count = max(objs) + 1
buf += f"xref\n0 {count}\n0000000000 65535 f \n".encode()
for n in range(1, count):
    buf += f"{offs[n]:010d} 00000 n \n".encode()
buf += f"trailer\n<< /Size {count} /Root 1 0 R >>\nstartxref\n{xref}\n%%EOF\n".encode()
open(out, "wb").write(bytes(buf))
print("PDF", len(buf), "bait")
