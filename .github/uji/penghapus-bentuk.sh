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
CX=$((W - $(dp 32))); CY=$(dp 26)
kotak() {
  local x0=$1 y0=$2 x1=$3 y1=$4 e=$((W*15/1000))
  adb shell input swipe $((x0-e)) $y0 $((x1+e)) $y0 600; sleep 1
  adb shell input swipe $x1 $((y0-e)) $x1 $((y1+e)) 600; sleep 1
  adb shell input swipe $((x1+e)) $y1 $((x0-e)) $y1 600; sleep 1
  adb shell input swipe $x0 $((y1+e)) $x0 $((y0-e)) 600; sleep 1
}
# Kotak hitam besar diisi ColorDrop.
bx0=$((W*20/100)); bx1=$((W*80/100)); by0=$((H*25/100)); by1=$((H*85/100))
kotak $bx0 $by0 $bx1 $by1
adb shell input swipe $CX $CY $(((bx0+bx1)/2)) $(((by0+by1)/2)) 1500; sleep 5
shot kotak-hitam
# Pilih penghapus, buat lingkaran bergelombang dengan pena lalu tahan.
tap_node "Penghapus" 2 || true
lingkaran() {
  local cx=$1 cy=$2 r=$3
  python3 - $cx $cy $r <<'PY' > /tmp/titik.txt
import math, sys, random
cx, cy, r = map(float, sys.argv[1:4]); random.seed(5)
for k in range(0, 27):
    t = -math.pi / 2 + k * 2 * math.pi / 24
    w = r * (1 + 0.07 * math.sin(3 * t) + random.uniform(-0.03, 0.03))
    print(int(cx + w * math.cos(t)), int(cy + w * math.sin(t)))
PY
  local first=1
  while read x y; do
    if [ $first = 1 ]; then adb shell input stylus motionevent DOWN $x $y </dev/null; first=0; else adb shell input stylus motionevent MOVE $x $y </dev/null; fi
    LX=$x; LY=$y
  done < /tmp/titik.txt
}
lingkaran $((W*38/100)) $((H*55/100)) $((H*16/100))
sleep 1.5
shot hapus-lingkaran-ditahan
adb shell input stylus motionevent UP $LX $LY
sleep 3
shot hapus-lingkaran-rapi
# Garis lurus bergelombang dengan penghapus, ditahan.
adb shell input stylus motionevent DOWN $((W*55/100)) $((H*35/100))
for k in 1 2 3 4 5 6 7 8; do adb shell input stylus motionevent MOVE $((W*55/100 + W*20/100*k/8)) $((H*35/100 + H*40/100*k/8 + (k%2)*8)); done
sleep 1.5
adb shell input stylus motionevent UP $((W*75/100)) $((H*75/100))
sleep 3
shot hapus-garis-rapi
tap_node "Urungkan" 3 || true
shot garis-diurungkan-sekali
adb shell run-as $PKG cat files/laporan.txt > $OUT/laporan.txt 2>&1
echo selesai
