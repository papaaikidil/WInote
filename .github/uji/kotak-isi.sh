#!/usr/bin/env bash
# Uji ColorDrop kotak: garis kotak hitam, isi oranye di dalamnya; luar kotak harus tetap putih.
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

adb shell am start -n $PKG/id.goresan.DrawActivity --ez uji_buat true
sleep 20
CX=$((W - $(dp 32))); CY=$(dp 26)
warna() {
  adb shell input tap $CX $CY; sleep 3
  tap_node "Nilai" 2 || true
  slide "Rona" $1 332 2
  slide "Saturasi" $2 332 2
  slide "Kecerahan" $3 332 2
  adb shell input tap $((W*50/100)) $((H*93/100)); sleep 2
}
E=$((W*15/1000))
kotak() {
  local x0=$1 y0=$2 x1=$3 y1=$4
  adb shell input swipe $((x0-E)) $y0 $((x1+E)) $y0 600; sleep 1
  adb shell input swipe $x1 $((y0-E)) $x1 $((y1+E)) 600; sleep 1
  adb shell input swipe $((x1+E)) $y1 $((x0-E)) $y1 600; sleep 1
  adb shell input swipe $x0 $((y1+E)) $x0 $((y0-E)) 600; sleep 1
}
warna 0.03 0.85 0.95
bx0=$((W*20/100)); bx1=$((W*45/100)); by0=$((H*20/100)); by1=$((H*55/100))
warna 0 0 0
bx0=$((W*20/100)); bx1=$((W*45/100)); by0=$((H*20/100)); by1=$((H*55/100))
kotak $bx0 $by0 $bx1 $by1
shot kotak
warna 0.03 0.85 0.95
adb shell input swipe $CX $CY $(((bx0+bx1)/2)) $(((by0+by1)/2)) 1500; sleep 6
shot isi-pratinjau
tap_node "Selesai" 5 || true
shot isi-hasil
# Kotak kedua: garis oranye, isi oranye (warna sama, seperti uji pilihan).
cx0=$((W*55/100)); cx1=$((W*80/100))
kotak $cx0 $by0 $cx1 $by1
adb shell input swipe $CX $CY $(((cx0+cx1)/2)) $(((by0+by1)/2)) 1500; sleep 6
tap_node "Selesai" 5 || true
shot isi-sewarna
echo "kotak1 $bx0 $by0 $bx1 $by1 kotak2 $cx0 $by0 $cx1 $by1" > $OUT/kotak.txt
adb shell run-as $PKG cat files/laporan.txt > $OUT/laporan.txt 2>&1
echo selesai
