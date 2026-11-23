#!/usr/bin/env bash
# Uji catatan: orientasi halaman (tegak/mendatar, tersimpan), putar tulisan terpilih, menu tipe/bentuk selotip.
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
# Catatan baru langsung terbuka (seperti dari widget).
for k in 1 2 3; do
  adb shell am start -W -n $PKG/id.hananote.MainActivity -a id.hananote.action.NEW_NOTE 2>&1 | tee -a $OUT/start.txt | grep -q "Status: ok" && break
  sleep 10
done
sleep 15
shot awal
find_bounds "Selotip" > /dev/null
cp $OUT/ui-last.xml $OUT/ui-awal.xml
python3 -m pip install --quiet --break-system-packages pillow >/dev/null 2>&1 || python3 -m pip install --quiet pillow >/dev/null 2>&1
# ---------- 1. Putar tulisan yang dipilih ----------
# Tiga baris tulisan tangan di tengah halaman.
for y in 40 46 52; do gerak garis 35 $y 60 $y 7; done
shot tulisan
tap_node "Pilih" 2 || echo "PILIH TIDAK ADA"
# Laso mengelilingi tulisan.
gerak jalur 31 35 64 35 64 57 31 57 31 36
sleep 3
shot pilih-tulisan
# Pegangan putar: lingkaran gelap di atas kotak pilihan (tengah mendatar kotak = 47,5% lebar).
read HX HY < <(python3 - "$OUT/$(printf %02d $n)-pilih-tulisan.png" $W $H $DEN <<'PY'
import sys
from PIL import Image
im = Image.open(sys.argv[1]).convert('RGB')
W, H, den = int(sys.argv[2]), int(sys.argv[3]), int(sys.argv[4])
dp = den / 160
cx = int(W * 0.475)
top = int(H * 0.40)
xs = []; ys = []
for y in range(int(top - 80 * dp), int(top - 14 * dp)):
    for x in range(int(cx - 30 * dp), int(cx + 30 * dp)):
        r, g, b = im.getpixel((x, y))
        if r + g + b < 360:
            xs.append(x); ys.append(y)
# Median: lingkaran pegangan (padat) mengalahkan garis putus-putus kotak.
if xs:
    xs.sort(); ys.sort()
    print(xs[len(xs) // 2], ys[len(ys) // 2])
else:
    print(cx, int(top - 50 * dp))
PY
)
echo "pegangan putar: $HX $HY" | tee $OUT/pegangan.txt
# Seret pegangan 35° searah jarum jam (titik tengah kotak kira-kira 46% tinggi).
read EX EY < <(python3 -c "
import math
hx,hy=$HX,$HY; cy=int($H*0.46); R=cy-hy
a=math.radians(35)
print(int(hx+R*math.sin(a)), int(cy-R*math.cos(a)))")
adb shell input swipe $HX $HY $EX $EY 1500; sleep 3
shot putar-miring
tap_node "Putar 90°" 3 || echo "PUTAR 90 TIDAK ADA"
shot putar-90
tap_node "Urungkan" 3 || true
shot urungkan-90
tap_node "Selesai" 2 || true
# ---------- 2. Selotip: tipe (Lurus/Bebas) dan bentuk terpisah ----------
tap_node "Selotip" 3 || echo "TOMBOL SELOTIP TIDAK ADA"
shot selotip-bilah
tap_node "Tipe selotip" 2 || echo "TIPE SELOTIP TIDAK ADA"
shot menu-tipe
tap_node "Bebas" 2 || echo "BEBAS TIDAK ADA"
gerak garis 15 70 45 71 10
tap_node "Bentuk selotip" 2 || echo "BENTUK SELOTIP TIDAK ADA"
shot menu-bentuk
tap_node "Bintang" 2 || echo "BINTANG TIDAK ADA"
gerak garis 60 64 75 78 0
shot selotip-bintang
tap_node "Pena" 2 || true
# ---------- 3. Orientasi halaman ----------
tap_node "Halaman" 3 || echo "KELOMPOK HALAMAN TIDAK ADA"
tap_node "Orientasi" 3 || echo "ORIENTASI TIDAK ADA"
shot dialog-orientasi
tap_node "Mendatar" 2 || echo "MENDATAR TIDAK ADA"
tap_node "Semua halaman" 2 || true
shot dialog-mendatar
tap_node "Terapkan" 4 || echo "TERAPKAN TIDAK ADA"
shot jadi-mendatar
tap_node "Tambah" 4 || echo "TAMBAH TIDAK ADA"
shot halaman-baru-mendatar
adb shell "run-as $PKG sh -c 'cat files/hana/notes/*/pages.json'" < /dev/null > $OUT/halaman-sebelum-tutup.json 2>&1
# Tutup aplikasi lalu buka lagi: tetap mendatar.
sleep 4
adb shell input keyevent KEYCODE_HOME; sleep 4
adb shell am force-stop $PKG; sleep 2
NID=$(adb shell run-as $PKG ls files/hana/notes | tr -d '\r' | head -1)
echo "catatan: $NID" | tee $OUT/catatan.txt
adb shell am start -W -n $PKG/id.hananote.MainActivity -a id.hananote.action.OPEN_NOTE --es note_id "$NID" 2>&1 | tee -a $OUT/start.txt
sleep 12
shot buka-ulang
adb shell run-as $PKG cat files/hana/notes/$NID/pages.json > $OUT/halaman-buka-ulang.json 2>&1
# Halaman 1 saja dijadikan tegak lewat menu tahan di gambar kecilnya.
B=$(find_bounds "Seret untuk memindah halaman 1")
echo "pegangan halaman 1: $B" | tee -a $OUT/pegangan.txt
if [ -n "$B" ]; then
  set -- $B
  TX=$(( $1 - $(dp 40) )); TY=$(( $4 + $(dp 20) ))
  adb shell input swipe $TX $TY $TX $TY 1200; sleep 3
  shot menu-halaman
  tap_node "Jadikan tegak" 4 || echo "JADIKAN TEGAK TIDAK ADA"
  shot halaman1-tegak
fi
adb shell run-as $PKG cat files/hana/notes/$NID/pages.json > $OUT/halaman-akhir.json 2>&1
adb shell run-as $PKG ls -la files/hana/notes/$NID > $OUT/berkas.txt 2>&1
adb logcat -d | grep -iE "FATAL|AndroidRuntime|hananote" | tail -80 > $OUT/logcat.txt
grep -c "FATAL" $OUT/logcat.txt
echo selesai
