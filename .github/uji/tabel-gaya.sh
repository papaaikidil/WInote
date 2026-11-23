#!/usr/bin/env bash
# Uji garis dan warna tabel ala Word: garis yang tampil, tebal garis, warna garis, warna isi sel, baris judul,
# baris selang-seling, warna per sel saat mengetik, garis bantu putus-putus, dan tersimpan setelah dibuka ulang.
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
halaman
# Gulir isi dialog ke bawah sampai simpul $1 terlihat.
gulir_cari() {
  for k in 1 2 3 4; do
    [ -n "$(find_node "$1")" ] && return 0
    adb shell input swipe $((W/2)) $((H*68/100)) $((W/2)) $((H*38/100)) 500; sleep 1
  done
  [ -n "$(find_node "$1")" ]
}
# Kotak tabel di layar: piksel berwarna garis tabel (biru 2F43B5) di tangkapan layar terakhir.
kotak_tabel() {
  adb exec-out screencap -p > /tmp/t.png
  read TX0 TY0 TX1 TY1 < <(python3 - <<'PY'
from PIL import Image
im = Image.open('/tmp/t.png').convert('RGB'); W, H = im.size; px = im.load()
xs = []; ys = []
for y in range(0, H, 2):
    for x in range(0, W, 2):
        r, g, b = px[x, y]
        if abs(r - 47) < 30 and abs(g - 67) < 30 and abs(b - 181) < 35:
            xs.append(x); ys.append(y)
if xs:
    print(min(xs), min(ys), max(xs), max(ys))
else:
    print(0, 0, 0, 0)
PY
)
  echo "tabel di layar: $TX0 $TY0 $TX1 $TY1" | tee -a $OUT/tabel.txt
}
# Titik tengah sel baris $1 kolom $2 (tabel 4 x 3, tinggi baris sama).
sel() { echo $(( TX0 + (TX1 - TX0) * (2 * $2 + 1) / 6 )) $(( TY0 + (TY1 - TY0) * (2 * $1 + 1) / 8 )); }

# ---------- 1. Sisipkan tabel di halaman 2 ----------
tap_node "Halaman berikutnya" 3 || true
tap_node "Sisipkan" 2 || echo "SISIPKAN TIDAK ADA"
tap_node "Tabel" 3 || echo "TABEL TIDAK ADA"
tap_node "Sisipkan" 3 || echo "SISIPKAN TABEL TIDAK ADA"
sleep 1
shot tabel-baru
# ---------- 2. Garis & warna dari bilah pilihan ----------
tap_node "Garis & warna" 3 || echo "GARIS WARNA TIDAK ADA"
shot dialog-awal
tap_node "Garis luar saja" 1 || echo "GARIS LUAR TIDAK ADA"
tap_node "Dalam mendatar" 1 || echo "DALAM MENDATAR TIDAK ADA"
tap_node "Tebal garis 3 pt" 1 || echo "TEBAL 3 TIDAK ADA"
tap_node "Garis Biru" 1 || echo "GARIS BIRU TIDAK ADA"
shot dialog-garis
gulir_cari "Isi Kuning muda" && tap_node "Isi Kuning muda" 1 || echo "ISI KUNING TIDAK ADA"
gulir_cari "Judul Biru" && tap_node "Judul Biru" 1 || echo "JUDUL BIRU TIDAK ADA"
gulir_cari "Baris selang-seling (baris isi berganti warna)" && tap_node "Baris selang-seling (baris isi berganti warna)" 1 || echo "SELANG-SELING TIDAK ADA"
shot dialog-warna
tap_node "Terapkan" 3 || echo "TERAPKAN TIDAK ADA"
shot tabel-bergaya
# ---------- 3. Ketik isi sel, warna satu kolom, dan buka dialog dari bilah ketik ----------
kotak_tabel
tap_node "Teks" 2 || true
adb shell input tap $(sel 0 0); sleep 2
adb shell input text "Barang"; sleep 1
tap_node "Sel berikutnya" 1 || echo "SEL BERIKUTNYA TIDAK ADA"
adb shell input text "Jumlah"; sleep 1
tap_node "Sel berikutnya" 1 || true
adb shell input text "Harga"; sleep 1
tap_node "Sel berikutnya" 1 || true
adb shell input text "Jeruk"; sleep 1
tap_node "Sel berikutnya" 1 || true
adb shell input text "10"; sleep 1
tap_node "Sel berikutnya" 1 || true
adb shell input text "5000"; sleep 1
adb shell input keyevent 4; sleep 1
shot sel-terisi
tap_node "Warna sel" 2 || echo "WARNA SEL TIDAK ADA"
shot panel-warna-sel
tap_node "Kolom" 1 || echo "KOLOM TIDAK ADA"
tap_node "Sel Hijau muda" 2 || echo "SEL HIJAU TIDAK ADA"
shot kolom-hijau
tap_node "Kembali" 1 || true
tap_node "Garis & warna" 3 || echo "GARIS WARNA DARI SEL TIDAK ADA"
shot dialog-dari-sel
tap_node "Batal" 2 || adb shell input keyevent 4
tap_node "Selesai" 2 || adb shell input keyevent 4
sleep 1
shot garis-bantu
# ---------- 4. Mode baca: garis bantu putus-putus tidak tampil ----------
tap_node "Mode baca (hanya membaca)" 3 || echo "MODE BACA TIDAK ADA"
shot mode-baca
tap_node "Keluar dari mode baca" 3 || true
# ---------- 5. Tersimpan: tutup lalu buka lagi ----------
tap_node "Kembali ke rak" 5 || echo "KEMBALI KE RAK TIDAK ADA"
tap_node "Dokumen PDF" 6 || echo "BUKA CATATAN TIDAK ADA"
sleep 3
shot dibuka-lagi
# ---------- 6. Tanpa garis sama sekali ----------
kotak_tabel
tap_node "Pilih" 2 || true
adb shell input tap $(( (TX0 + TX1) / 2 )) $(( (TY0 + TY1) / 2 )); sleep 2
shot dipilih-lagi
tap_node "Garis & warna" 3 || echo "GARIS WARNA KEDUA TIDAK ADA"
tap_node "Tanpa garis" 1 || true
tap_node "Terapkan" 3 || true
tap_node "Mode baca (hanya membaca)" 3 || true
shot tanpa-garis-baca
tap_node "Keluar dari mode baca" 3 || true
shot tanpa-garis-sunting
adb logcat -d | grep -iE "FATAL|AndroidRuntime|hananote" | tail -80 > $OUT/logcat.txt
grep -c "FATAL" $OUT/logcat.txt
echo selesai
