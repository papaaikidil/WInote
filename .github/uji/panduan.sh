#!/usr/bin/env bash
# Uji Panduan Menggambar: Grid 2D, Isometrik, Perspektif, Simetri, dan Bantuan menggambar.
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
adb shell am start -n $PKG/id.goresan.DrawActivity --ez uji_buat true
sleep 20
shot awal
buka_edit() {
  tap_node "Aksi" 3 || true
  for k in 1 2 3; do
    tap_node "Edit panduan menggambar" 4 && return 0
    adb shell input swipe $(dp 150) $(dp 520) $(dp 150) $(dp 140) 400; sleep 2
  done
  echo "EDIT PANDUAN TIDAK KETEMU"
}
garis() { adb shell input swipe $1 $2 $3 $4 700; sleep 2; }
P() { echo $((W*$1/100)) $((H*$2/100)); }
# 1. Grid 2D + Bantuan menggambar: garis agak miring jadi mendatar/tegak.
buka_edit
shot edit-grid
tap_node "Bantuan menggambar" 2 || true
shot edit-grid-bantuan
tap_node "Selesai" 3 || true
garis $(P 20 30) $(P 70 36)
garis $(P 30 40) $(P 34 80)
shot grid-garis
# 2. Isometrik: garis kira-kira 30° jadi tepat 30°.
buka_edit
tap_node "Isometrik" 2 || true
shot edit-iso
tap_node "Selesai" 3 || true
garis $(P 40 70) $(P 70 52)
shot iso-garis
# 3. Perspektif 2 titik: ketuk dua titik hilang di kanvas.
buka_edit
tap_node "Perspektif" 2 || true
adb shell input tap $(P 15 45); sleep 2
adb shell input tap $(P 85 45); sleep 2
shot edit-persp
tap_node "Selesai" 3 || true
garis $(P 45 60) $(P 30 68)
garis $(P 55 60) $(P 72 70)
garis $(P 50 55) $(P 51 80)
shot persp-garis
# 4. Simetri kuadran: satu goresan jadi empat.
buka_edit
tap_node "Simetri" 2 || true
tap_node "Kuadran" 2 || true
shot edit-simetri
tap_node "Selesai" 3 || true
adb shell input swipe $(P 30 25) $(P 42 38) 600; sleep 1
adb shell input swipe $(P 25 35) $(P 40 30) 600; sleep 2
shot simetri-kuadran
# 5. Radial + simetri putar.
buka_edit
tap_node "Radial" 2 || true
tap_node "Simetri putar" 2 || true
shot edit-radial
tap_node "Selesai" 3 || true
adb shell input swipe $(P 55 30) $(P 60 22) 600; sleep 2
shot radial-putar
# 6. Panel layer: label Bantuan.
tap_node "Layer" 3 || true
shot layer
adb shell input keyevent KEYCODE_BACK; sleep 2
# 7. Matikan panduan: goresan bebas lagi.
tap_node "Aksi" 3 || true
for k in 1 2 3; do
  tap_node "Panduan menggambar" 2 && break
  adb shell input swipe $(dp 150) $(dp 520) $(dp 150) $(dp 140) 400; sleep 2
done
shot aksi-mati
adb shell input keyevent KEYCODE_BACK; sleep 2
garis $(P 20 88) $(P 70 82)
shot bebas
adb shell run-as $PKG cat files/laporan.txt > $OUT/laporan.txt 2>&1
adb logcat -d -t 600 | grep -i "goresan\|hananote\|AndroidRuntime" | tail -80 > $OUT/logcat.txt
echo selesai
