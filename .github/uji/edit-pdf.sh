#!/usr/bin/env bash
# Uji edit PDF di catatan: pilih teks, stabilo, garis bawah, ubah teks, formulir, tanda tangan, putar, nomor halaman, penanda baca, ekspor sebagian.
set -x
PKG=id.hananote.app
OUT=uji-hasil
mkdir -p $OUT
adb install -r app/build/outputs/apk/debug/app-debug.apk || { echo "PASANG GAGAL"; exit 1; }
sleep 30
adb shell am broadcast -a android.intent.action.CLOSE_SYSTEM_DIALOGS >/dev/null 2>&1
adb shell settings put global hide_error_dialogs 1
adb shell settings put global window_animation_scale 0
adb shell settings put global transition_animation_scale 0
adb shell settings put global animator_duration_scale 0
adb logcat -c
SIZE=$(adb shell wm size | tail -1 | sed 's/.*: //' | tr -d '\r')
W=${SIZE%x*}; H=${SIZE#*x}
DEN=$(adb shell wm density | tail -1 | sed 's/.*: //' | tr -d '\r')
echo "layar $W x $H, density $DEN"
dp() { echo $(( $1 * DEN / 160 )); }
n=0
shot() { n=$((n+1)); adb exec-out screencap -p > "$OUT/$(printf %02d $n)-$1.png"; }
# Cetak "x0 y0 x1 y1" simpul pertama yang teks/content-desc-nya sama persis.
find_bounds() {
  adb shell uiautomator dump /sdcard/ui.xml >> "$OUT/dump-log.txt" 2>&1
  adb pull /sdcard/ui.xml "$OUT/ui-last.xml" >/dev/null 2>&1
  python3 - "$1" "$OUT/ui-last.xml" <<'PY'
import re, sys
want, path = sys.argv[1], sys.argv[2]
xml = open(path, encoding='utf-8', errors='ignore').read()
for m in re.finditer(r'<node [^>]*>', xml):
    n = m.group(0)
    t = re.search(r' text="([^"]*)"', n); d = re.search(r'content-desc="([^"]*)"', n)
    import html
    if (t and html.unescape(t.group(1)) == want) or (d and html.unescape(d.group(1)) == want):
        b = re.search(r'bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', n)
        if b:
            print(*map(int, b.groups()))
            break
PY
}
find_node() {
  local b; b=$(find_bounds "$1")
  [ -z "$b" ] && return 0
  set -- $b
  echo $(( ($1 + $3) / 2 )) $(( ($2 + $4) / 2 ))
}
tap_node() {
  local p; p=$(find_node "$1")
  if [ -z "$p" ]; then echo "TIDAK KETEMU: $1"; return 1; fi
  adb shell input tap $p; sleep ${2:-2}
}
# Ketuk penggeser berlabel $1 pada posisi $2 (0..1). Lebar area penggeser $3 dp (panel), tepi kirinya = tepi kiri label.
slide() {
  local b; b=$(find_bounds "$1")
  if [ -z "$b" ]; then echo "TIDAK KETEMU: $1"; return 1; fi
  local f=$2 wd=${3:-488} sl=${4:-4}
  set -- $b
  local y=$(( $4 + $(dp 22) ))
  local xl=$(( $1 + $(dp 10) )) xr=$(( $1 + $(dp $wd) - $(dp 10) ))
  local x; x=$(python3 -c "print(int($xl + ($xr - $xl) * $f))")
  adb shell input tap $x $y; sleep $sl
}
menu() {
  tap_node "Penyesuaian" 2 || return 1
  if ! tap_node "$1" 4; then
    # Item di bawah menu: gulir menu dulu.
    adb shell input swipe $(dp 200) $((H*80/100)) $(dp 200) $((H*30/100)) 600; sleep 2
    tap_node "$1" 4 || return 1
  fi
}
terapkan() { tap_node "Terapkan" 4 || true; }
batal() { tap_node "Batal" 3 || true; }
# sendevent perlu akses root ke /dev/input (citra google_apis mengizinkan adb root).
adb root; sleep 6; adb wait-for-device; sleep 4
adb shell id
# Perangkat sentuh emulator (multi-sentuh) untuk sendevent.
adb shell getevent -pl > $OUT/getevent.txt 2>&1
read TSDEV TSMX TSMY < <(python3 - $OUT/getevent.txt <<'PY'
import re, sys
txt = open(sys.argv[1], errors='ignore').read()
found = []
for blk in re.split(r'\nadd device \d+: ', '\n' + txt):
    m = re.match(r'(/dev/input/event\d+)', blk)
    if not m or 'ABS_MT_POSITION_X' not in blk:
        continue
    mx = re.search(r'ABS_MT_POSITION_X\s*:.*?max (\d+)', blk)
    my = re.search(r'ABS_MT_POSITION_Y\s*:.*?max (\d+)', blk)
    nm = re.search(r'name:\s*"([^"]*)"', blk)
    found.append((nm.group(1) if nm else '', m.group(1), mx.group(1), my.group(1)))
# Layar utama emulator = virtio_input_multi_touch_1 (yang lain untuk layar tambahan).
pick = [f for f in found if f[0].endswith('_1')] or found
if pick:
    print(*pick[0][1:])
PY
)
echo "sentuh: $TSDEV $TSMX $TSMY" | tee $OUT/sentuh.txt
gerak() {
  (cd /tmp && python3 $OLDPWD/.github/uji/gerak.py $TSDEV $TSMX $TSMY $W $H $DEN "$@")
  adb shell rm -rf /data/local/tmp/g
  adb push /tmp/g /data/local/tmp/ >/dev/null 2>&1
  adb shell sh /data/local/tmp/g/run.sh
  sleep 1.5
}
for k in $(seq 1 60); do adb shell service check activity 2>/dev/null | grep -q "found" && break; sleep 3; done
python3 -m pip install --quiet --break-system-packages pillow >/dev/null 2>&1 || python3 -m pip install --quiet pillow >/dev/null 2>&1
python3 .github/uji/pdf/buat.py /tmp/surat.pdf
# Folder data aplikasi baru ada setelah dibuka sekali.
adb shell am start -W -n $PKG/id.hananote.MainActivity >/dev/null 2>&1
sleep 15
adb push /tmp/surat.pdf /data/local/tmp/surat.pdf >/dev/null
adb shell "cat /data/local/tmp/surat.pdf | run-as $PKG sh -c 'cat > files/surat.pdf'"
adb shell am force-stop $PKG
for k in 1 2 3; do
  adb shell am start -W -n $PKG/id.hananote.MainActivity -a id.hananote.action.NEW_NOTE --es uji_pdf surat.pdf 2>&1 | tee -a $OUT/start.txt | grep -q "Status: ok" && break
  sleep 10
done
sleep 15
shot pdf-terbuka
# Letak halaman di layar dari tangkapan layar: halaman PDF putih bersih (rentang putih terpanjang di tengah layar).
halaman() {
  adb exec-out screencap -p > /tmp/l.png
  read PX0 PY0 PX1 PY1 < <(python3 - <<'PY'
from PIL import Image
im = Image.open('/tmp/l.png').convert('RGB'); W, H = im.size; px = im.load()
def runs(vals):
    out = []; start = None; last = None
    for i, v in enumerate(vals):
        if v:
            if start is None: start = i
            elif i - last > 60: out.append((start, last)); start = i
            last = i
    if start is not None: out.append((start, last))
    return out
y = H // 2
rx = max(runs([px[x, y] == (255, 255, 255) for x in range(W)]), key=lambda r: r[1] - r[0])
cx = (rx[0] + rx[1]) // 2
ry = max(runs([px[cx, yy] == (255, 255, 255) for yy in range(H)]), key=lambda r: r[1] - r[0])
print(rx[0], ry[0], rx[1], ry[1])
PY
)
  echo "halaman di layar: $PX0 $PY0 $PX1 $PY1" | tee -a $OUT/halaman.txt
}
# Titik PDF (poin dari kiri atas; lebar halaman 595) → layar.
pt() { python3 -c "s=($PX1-$PX0)/595.0; print(int($PX0 + s*$1), int($PY0 + s*$2))"; }
pct() { python3 -c "s=($PX1-$PX0)/595.0; print(round(($PX0 + s*$1)*100.0/$W, 2), round(($PY0 + s*$2)*100.0/$H, 2))"; }
ketuk_pt() { adb shell input tap $(pt $1 $2); sleep ${3:-2}; }
tap_node "PDF" 2 || echo "KELOMPOK PDF TIDAK ADA"
shot kelompok-pdf
tap_node "Pilih teks" 4 || echo "PILIH TEKS TIDAK ADA"
shot mode-teks-pdf
halaman
# 1. Ketuk kata "minyak" (baris 2) lalu Stabilo.
ketuk_pt 80 126 3
shot pilih-kata
tap_node "Stabilo teks pilihan" 3 || echo "STABILO TIDAK ADA"
shot stabilo
# 2. Tarik di atas "Harga per kilogram" (baris 3) lalu Garis bawah.
read A B < <(pct 61 145); read C D < <(pct 172 145)
gerak garis $A $B $C $D 0
sleep 2
shot tarik-pilih
tap_node "Garis bawahi teks pilihan" 3 || echo "GARIS BAWAH TIDAK ADA"
# 3. Coret "Catatan:" (Times, baris bawah).
ketuk_pt 80 266 3
shot pilih-catatan
tap_node "Coret teks pilihan" 3 || echo "CORET TIDAK ADA"
shot garis-bawah-coret
# 4. Ubah teks: ketuk "Yang" dua kali (satu baris), lalu ganti.
ketuk_pt 70 108 2
ketuk_pt 70 108 3
shot pilih-baris
tap_node "Ubah teks pilihan" 3 || echo "UBAH TEKS TIDAK ADA"
shot dialog-ubah
# Teks lama sudah terpilih semua: langsung ketik penggantinya (lebih panjang dari aslinya, harus tetap satu baris).
adb shell input text "Saya%syang%sbertanda%stangan%sdi%sbawah%sini%sdengan%ssungguh-sungguh%smenyatakan%sbahwa"; sleep 2
shot dialog-ubah-isi
tap_node "Ganti" 4 || echo "GANTI TIDAK ADA"
shot teks-diganti
adb exec-out screencap -p > $OUT/zz-teks-diganti-penuh.png
# 5. Formulir: isi nama, centang, tanda tangan.
tap_node "Formulir" 4 || echo "FORMULIR TIDAK ADA"
shot mode-formulir
ketuk_pt 265 200 3
shot dialog-kolom
adb shell input text "Wildan%sSembada"; sleep 2
tap_node "Isi" 3 || echo "ISI TIDAK ADA"
ketuk_pt 138 238 3
ketuk_pt 250 317 3
shot dialog-ttd
b=$(find_bounds "Papan tanda tangan")
if [ -n "$b" ]; then
  set -- $b
  x0=$(( $1 + ($3 - $1) / 6 )); x1=$(( $3 - ($3 - $1) / 6 )); ym=$(( ($2 + $4) / 2 ))
  adb shell input swipe $x0 $(( ym + 20 )) $(( (x0 + x1) / 2 )) $(( ym - 40 )) 300
  adb shell input swipe $(( (x0 + x1) / 2 )) $(( ym - 40 )) $x1 $(( ym + 30 )) 300
  adb shell input swipe $x0 $(( ym + 40 )) $x1 $(( ym + 35 )) 300
fi
sleep 1
shot ttd-digambar
tap_node "Simpan & tempel" 4 || echo "SIMPAN TTD TIDAK ADA"
shot formulir-terisi
# 6. Tanda tangan tersimpan bisa dipakai lagi.
tap_node "Tanda tangan" 3 || echo "TOMBOL TTD TIDAK ADA"
shot ttd-tersimpan
tap_node "Tutup" 2 || true
# 7. Nomor halaman.
tap_node "Halaman" 2 || true
tap_node "Nomor hal." 3 || echo "NOMOR HAL TIDAK ADA"
tap_node "Hal. 1" 1 || true
shot dialog-nomor
tap_node "Simpan" 4 || true
shot nomor-halaman
# 8. Putar halaman PDF (isi ikut berputar).
# Kelompok PDF ditutup lalu dibuka lagi: harus bergeser masuk layar.
tap_node "PDF" 2 || true; tap_node "PDF" 3 || true
shot kelompok-pdf-digeser
tap_node "Putar" 3 || echo "PUTAR TIDAK ADA"
shot tanya-putar
tap_node "Tetap putar" 4 || echo "TETAP PUTAR TIDAK ADA"
shot diputar
for k in 1 2 3; do tap_node "Putar" 3 || true; tap_node "Tetap putar" 3 || true; done
shot putar-kembali
# 9. Penanda bacaan terakhir: baca halaman 2, keluar, kembali ke halaman 1, masuk mode baca lagi.
tap_node "Halaman berikutnya" 4 || echo "BERIKUTNYA TIDAK ADA"
tap_node "Mode baca (hanya membaca)" 3 || echo "MODE BACA TIDAK ADA"
shot baca-hal-2
tap_node "Sunting" 3 || true
tap_node "Halaman sebelumnya" 4 || true
tap_node "Mode baca (hanya membaca)" 4 || true
shot tawaran-lanjut
tap_node "Lanjutkan" 5 || echo "LANJUTKAN TIDAK ADA"
shot lanjut-baca
tap_node "Sunting" 3 || true
# 10. Ekspor sebagian halaman jadi PDF.
# Bilah fitur digeser ke ujung kanan (kelompok PDF di paling kanan).
YB=$(dp 55)
adb shell input swipe $((W*85/100)) $YB $((W*10/100)) $YB 400; sleep 1
adb shell input swipe $((W*85/100)) $YB $((W*10/100)) $YB 400; sleep 2
shot bilah-digeser
tap_node "Ekspor hal." 3 || echo "EKSPOR TIDAK ADA"
tap_node "PDF" 2 || true
tap_node "Pilih halaman…" 2 || true
tap_node "Nomor halaman yang diekspor" 2 || true
adb shell input text "2"; sleep 2
shot dialog-ekspor
tap_node "Simpan" 8 || true
shot ekspor-selesai
adb shell run-as $PKG ls -la cache/export > $OUT/ekspor.txt 2>&1
adb shell "run-as $PKG sh -c 'cat cache/export/*.pdf'" > $OUT/ekspor.pdf 2>/dev/null
adb shell run-as $PKG cat files/hana/notes.json > $OUT/notes.json 2>&1
adb shell run-as $PKG ls -R files/hana/notes > $OUT/berkas.txt 2>&1
adb shell run-as $PKG cat files/crash.txt > $OUT/crash.txt 2>&1
adb logcat -d | grep -iE "FATAL|AndroidRuntime: [^>]|pdfbox|PDFBox|teks PDF|formulir PDF" | tail -60 > $OUT/logcat.txt
echo selesai
