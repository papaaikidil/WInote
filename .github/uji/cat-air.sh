#!/usr/bin/env bash
# Uji kuas cat air dan tekstur kertas kanvas.
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
python3 .github/uji/buat-abr.py uji.abr
# Tunggu sistem emulator benar-benar siap (layanan activity ada), lalu buka ruang gambar.
for k in $(seq 1 60); do adb shell service check activity 2>/dev/null | grep -q "found" && break; sleep 3; done
for k in 1 2 3; do
  adb shell am start -W -n $PKG/id.goresan.DrawActivity --ez uji_buat true --es uji_warna 8DB82C --ei uji_kertas 1 2>&1 | tee -a $OUT/start.txt | grep -q "Status: ok" && break
  sleep 10
done
sleep 25
shot awal-kertas
# Pilih kuas dari kategori Cat Air.
pilih() { tap_node "Kuas" 3 || true; tap_node "Cat Air" 2 || true; tap_node "$1" 3 || true; tap_node "Kuas" 3 || true; }
# Gerakan melingkar dengan input motionevent (titik demi titik).
lingkar() { # cx cy rx ry (persen layar)
  python3 - "$@" "$W" "$H" > /tmp/lingkar.txt <<'PY'
import math, sys
cx, cy, rx, ry, W, H = map(float, sys.argv[1:7])
pts = []
for k in range(0, 41):
    t = k / 40 * 2 * math.pi * 1.05
    pts.append((int(W * (cx + rx * math.cos(t)) / 100), int(H * (cy + ry * math.sin(t)) / 100)))
print("DOWN %d %d" % pts[0])
for p in pts[1:]: print("MOVE %d %d" % p)
print("UP %d %d" % pts[-1])
PY
  while read a x y; do adb shell input motionevent $a $x $y < /dev/null; done < /tmp/lingkar.txt
  sleep 2
}
pilih "Cat Air Utama"
lingkar 35 45 14 22
lingkar 35 45 10 16
adb shell input swipe $((W*20/100)) $((H*78/100)) $((W*55/100)) $((H*74/100)) 700; sleep 2
adb shell input swipe $((W*25/100)) $((H*70/100)) $((W*50/100)) $((H*82/100)) 700; sleep 2
shot utama
pilih "Cat Air Basah"
lingkar 68 40 12 18
shot basah
pilih "Kuas Kering"
adb shell input swipe $((W*58/100)) $((H*72/100)) $((W*85/100)) $((H*68/100)) 900; sleep 2
pilih "Detail Cat Air"
adb shell input swipe $((W*60/100)) $((H*80/100)) $((W*85/100)) $((H*84/100)) 900; sleep 2
shot kering-detail
# Pustaka kuas Cat Air.
tap_node "Kuas" 3 || true
tap_node "Cat Air" 2 || true
shot pustaka
tap_node "Cat Air Utama" 3 || true
tap_node "Cat Air Utama" 4 || true
shot studio
adb shell input keyevent KEYCODE_BACK; sleep 2
tap_node "Kuas" 3 || true
# Panel Kertas kanvas dari Aksi.
tap_node "Aksi" 3 || true
for k in 1 2 3; do
  tap_node "Kertas kanvas" 4 && break
  adb shell input swipe $(dp 150) $(dp 520) $(dp 150) $(dp 140) 400; sleep 2
done
sleep 4
shot panel-kertas
tap_node "Kanvas" 4 || true
shot kertas-kanvas
tap_node "Bergaris" 4 || true
shot kertas-bergaris
tap_node "Cat air kasar" 4 || true
shot kertas-kasar
tap_node "Selesai" 3 || true
shot akhir
adb shell run-as $PKG cat files/laporan.txt > $OUT/laporan.txt 2>&1
adb logcat -d -t 600 | grep -i "goresan\|hananote\|AndroidRuntime" | tail -80 > $OUT/logcat.txt
echo selesai
