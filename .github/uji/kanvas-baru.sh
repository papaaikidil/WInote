#!/usr/bin/env bash
# Uji layar Kanvas khusus (satuan, DPI, properti latar) dan pengaturan latar di panel Layer.
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

adb shell am start -n $PKG/id.goresan.DrawActivity
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
ketik() { for k in "$@"; do tap_node "$k" 1 || true; done; }
tap_node "Kanvas baru" 4 || true
shot kanvas-khusus
# A4 dalam milimeter: 210 × 297 mm pada 300 DPI = 2480 × 3508 px
tap_node "Lebar" 2 || true
tap_node "Milimeter" 2 || true
ketik 2 1 0
tap_node "Berikutnya" 2 || true
ketik 2 9 7
shot a4-mm
# DPI 150: ukuran cetak tetap, piksel jadi separuh (1240 × 1754 px)
tap_node "DPI" 2 || true
ketik 1 5 0
shot dpi-150
tap_node "Piksel" 2 || true
shot satuan-piksel
tap_node "Ukuran siap pakai" 3 || true
shot siap-pakai
tap_node "Persegi" 3 || true
tap_node "Properti kanvas" 3 || true
# pilih warna latar: ketuk kotak saturasi-kecerahan (kuning muda), lalu rona
b=$(find_bounds "Warna latar")
set -- $b
SVX=$(( $1 + $(dp 150) )); SVY=$(( $4 + $(dp 10) + $(dp 60) ))
adb shell input tap $(( $1 + $(dp 90) )) $SVY; sleep 2
shot properti-latar
tap_node "Buat" 25 || true
shot kanvas-dibuat
# Pengaturan latar di panel Layer: ganti warna, lalu Selesai, lalu urungkan.
tap_node "Layer" 3 || true
tap_node "Warna latar" 3 || true
shot dialog-latar
b=$(find_bounds "Latar kanvas")
set -- $b
adb shell input tap $(( $1 + $(dp 250) )) $(( $4 + $(dp 150) )); sleep 3
shot latar-diganti
tap_node "Sembunyikan latar" 3 || true
shot latar-disembunyikan
tap_node "Selesai" 4 || true
tap_node "Layer" 3 || true
shot setelah-selesai
tap_node "Urungkan" 4 || true
shot urungkan
tap_node "Aksi" 3 || true
adb shell input swipe $(dp 150) $(dp 600) $(dp 150) $(dp 150) 600; sleep 2
shot aksi-bawah
tap_node "Ubah DPI" 3 || true
tap_node "150" 2 || true
shot ubah-dpi
tap_node "Simpan" 3 || true
shot aksi-dpi-baru
tap_node "Aksi" 3 || true
tap_node "Aksi" 3 || true
tap_node "Simpan PNG ke Galeri" 15 || true
tap_node "Aksi" 3 || true
tap_node "Simpan JPG ke Galeri" 15 || true
shot sesudah-ekspor
adb shell ls -l /sdcard/Pictures/WInote/ > $OUT/ekspor.txt 2>&1
mkdir -p $OUT/ekspor
adb pull /sdcard/Pictures/WInote/. $OUT/ekspor/ >> $OUT/ekspor.txt 2>&1
adb shell run-as $PKG cat files/laporan.txt > $OUT/laporan.txt 2>&1
echo selesai
