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
bx0=$((W*32/100)); bx1=$((W*55/100)); by0=$((H*30/100)); by1=$((H*60/100))
kotak $bx0 $by0 $bx1 $by1
adb shell input swipe $CX $CY $(((bx0+bx1)/2)) $(((by0+by1)/2)) 1500; sleep 5
warna 0.6 0.8 0.5
# Huruf "F" di dalam kotak supaya arah/balik terlihat.
adb shell input swipe $((bx0+60)) $((by1-60)) $((bx0+60)) $((by0+60)) 500; sleep 1
adb shell input swipe $((bx0+60)) $((by0+60)) $((bx1-80)) $((by0+60)) 500; sleep 1
adb shell input swipe $((bx0+60)) $(((by0+by1)/2)) $((bx1-140)) $(((by0+by1)/2)) 500; sleep 1
shot gambar
L=$((bx0-E)); R=$((bx1+E)); T=$((by0-E)); B=$((by1+E)); MX=$(((L+R)/2)); MY=$(((T+B)/2))
tap_node "Transformasi" 4 || true
shot transformasi-buka
# Seragam: tarik sudut kanan-bawah.
adb shell input swipe $R $B $((R+200)) $((B+120)) 900; sleep 3
shot seragam-sudut
tap_node "Atur ulang" 3 || true
# Putar lewat bulatan di atas.
adb shell input swipe $MX $((T - $(dp 30))) $((MX+300)) $((T+40)) 900; sleep 3
shot putar-pegangan
tap_node "Atur ulang" 3 || true
# Bebas: tarik tepi kanan.
tap_node "Bebas" 2 || true
adb shell input swipe $R $MY $((R+250)) $MY 900; sleep 3
shot bebas-tepi
tap_node "Atur ulang" 3 || true
# Distorsi: tarik sudut kanan-atas.
tap_node "Distorsi" 2 || true
adb shell input swipe $R $T $((R+180)) $((T+150)) 900; sleep 3
adb shell input swipe $L $B $((L-120)) $((B+60)) 900; sleep 3
shot distorsi
tap_node "Atur ulang" 3 || true
# Warp: seret bagian dalam.
tap_node "Warp" 2 || true
shot warp-kisi
adb shell input swipe $MX $MY $((MX+160)) $((MY+90)) 900; sleep 3
shot warp-lengkung
tap_node "Bilinear" 2 || true
shot interpolasi-bikubik
tap_node "Balik H" 3 || true
shot warp-dibalik
tap_node "Selesai" 5 || true
shot warp-diterapkan
tap_node "Urungkan" 3 || true
shot warp-diurungkan
# Snapping: seret mendekati tengah kanvas, tahan, foto garis tempel.
tap_node "Transformasi" 4 || true
tap_node "Seragam" 2 || true
tap_node "Snapping" 2 || true
tap_node "Putar 45°" 3 || true
shot putar-45
cxs=$((W/2)); cys=$((H/2))
adb shell input motionevent DOWN $MX $MY
for k in 1 2 3 4 5 6; do adb shell input motionevent MOVE $((MX + (cxs - MX)*k/6 + 6)) $((MY + (cys - MY)*k/6 - 5)); sleep 0.2; done
sleep 2
shot snapping-garis
adb shell input motionevent UP $((cxs + 6)) $((cys - 5))
sleep 2
tap_node "Selesai" 5 || true
shot snapping-diterapkan
adb shell run-as $PKG cat files/laporan.txt > $OUT/laporan.txt 2>&1
adb logcat -d > $OUT/logcat.txt
echo selesai
