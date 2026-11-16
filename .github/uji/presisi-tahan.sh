#!/usr/bin/env bash
# Uji menu Penyesuaian di emulator: siapkan gambar berwarna, lalu coba setiap penyesuaian (tangkapan layar tiap langkah).
# Uji lama (bentuk otomatis, grup layer, ColorDrop, dll.) ada di gambar-lama.sh.
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
    if (t and t.group(1) == want) or (d and d.group(1) == want):
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

adb shell am start -n $PKG/id.goresan.DrawActivity --ez uji_buat true
sleep 20
# Gambar dengan pena (stylus) lalu tahan lama: bentuk muncul, lalu jadi presisi.
pena() {  # $1 berkas titik, $2 detik ditahan
  local first=1
  while read x y; do
    if [ $first = 1 ]; then adb shell input stylus motionevent DOWN $x $y </dev/null; first=0; else adb shell input stylus motionevent MOVE $x $y </dev/null; fi
    LX=$x; LY=$y
  done < "$1"
  sleep $2
}
python3 - $((W*28/100)) $((H*45/100)) $((H*18/100)) <<'PY' > /tmp/oval.txt
import math, sys, random
cx, cy, r = map(float, sys.argv[1:4]); random.seed(3)
for k in range(0, 29):
    t = -math.pi / 2 + k * 2 * math.pi / 26
    w = r * (1 + 0.05 * math.sin(3 * t) + random.uniform(-0.02, 0.02))
    print(int(cx + 1.45 * w * math.cos(t)), int(cy + w * math.sin(t)))
PY
# Oval ditahan sebentar saja (tetap elips).
pena /tmp/oval.txt 1.2
adb shell input stylus motionevent UP $LX $LY; sleep 2
shot elips-biasa
# Oval kedua di kanan, ditahan lama: lingkaran sempurna.
python3 - $((W*68/100)) $((H*45/100)) $((H*18/100)) <<'PY' > /tmp/oval2.txt
import math, sys, random
cx, cy, r = map(float, sys.argv[1:4]); random.seed(4)
for k in range(0, 29):
    t = -math.pi / 2 + k * 2 * math.pi / 26
    w = r * (1 + 0.05 * math.sin(3 * t) + random.uniform(-0.02, 0.02))
    print(int(cx + 1.45 * w * math.cos(t)), int(cy + w * math.sin(t)))
PY
pena /tmp/oval2.txt 3
shot ditahan-lama
adb shell input stylus motionevent UP $LX $LY; sleep 2
shot lingkaran-sempurna
# Segitiga miring ditahan lama: sama sisi.
python3 - $((W*45/100)) $((H*62/100)) <<'PY' > /tmp/tri.txt
import sys
ax, ay = map(int, sys.argv[1:3])
V = [(ax, ay), (ax + 300, ay + 330), (ax - 280, ay + 380), (ax, ay)]
for i in range(3):
    (x0, y0), (x1, y1) = V[i], V[i + 1]
    for k in range(8):
        t = k / 8
        print(int(x0 + (x1 - x0) * t + (k % 2) * 7), int(y0 + (y1 - y0) * t))
print(ax + 2, ay + 3)
PY
pena /tmp/tri.txt 3
adb shell input stylus motionevent UP $LX $LY; sleep 2
shot segitiga-sama-sisi
tap_node "Urungkan" 3 || true
shot urungkan-sekali
adb shell run-as $PKG cat files/laporan.txt > $OUT/laporan.txt 2>&1
echo selesai
