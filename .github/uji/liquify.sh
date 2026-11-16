#!/usr/bin/env bash
# Uji Liquify: GPU, mulus saat ditahan, Momentum, tepi (tangkapan layar tiap langkah + catatan waktu per sapuan).
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
# Hentikan uji bila aplikasi tidak lagi di depan.
depan() {
  adb shell dumpsys activity activities | grep -q "ResumedActivity.*$PKG" && return 0
  shot keluar; echo "APLIKASI TIDAK DI DEPAN"
  adb shell run-as $PKG cat files/laporan.txt > $OUT/laporan.txt 2>&1
  adb logcat -d -t 3000 > $OUT/logcat.txt
  exit 0
}

adb shell am start -n $PKG/id.goresan.DrawActivity --ez uji_buat true
sleep 20
shot kanvas-terbuka

# ---- gambar: garis-garis mendatar berwarna di bagian bawah (bar Liquify ada di atas) ----
CX=$((W - $(dp 32))); CY=$(dp 26)
warna() {  # $1 rona 0..1, $2 saturasi, $3 kecerahan
  adb shell input tap $CX $CY; sleep 3
  tap_node "Nilai" 2 || true
  slide "Rona" $1 332 2
  slide "Saturasi" $2 332 2
  slide "Kecerahan" $3 332 2
  adb shell input tap $((W*50/100)) $((H*93/100)); sleep 2
}
garis() { adb shell input swipe $((W*10/100)) $1 $((W*92/100)) $1 500; sleep 1; }
warna 0.92 0.8 1.0
for y in 60 66 72 78 84 90; do garis $((H*y/100)); done
warna 0.08 0.9 1.0
for y in 63 69 75 81 87; do garis $((H*y/100)); done
# Garis tebal (beberapa goresan rapat) untuk uji Tepi.
warna 0.6 0.8 0.5
for d in 0 3 6 9 12 15; do adb shell input swipe $((W*70/100 + d)) $((H*58/100)) $((W*70/100 + d)) $((H*94/100)) 500; sleep 1; done
shot gambar-siap
depan

menu "Liquify" && {
  shot panel
  # Dorong: beberapa sapuan bergelombang cepat.
  adb shell input swipe $((W*15/100)) $((H*64/100)) $((W*35/100)) $((H*80/100)) 300; sleep 1
  adb shell input swipe $((W*35/100)) $((H*80/100)) $((W*55/100)) $((H*64/100)) 300; sleep 1
  adb shell input swipe $((W*20/100)) $((H*86/100)) $((W*50/100)) $((H*70/100)) 250; sleep 2
  shot dorong
  # Putar kanan: pena ditahan diam 2 detik (efek harus terus berjalan mulus tiap frame).
  tap_node "Putar kanan" 2
  adb shell input swipe $((W*30/100)) $((H*76/100)) $((W*30/100)) $((H*76/100)) 2000; sleep 2
  shot putar-tahan
  # Cubit ditahan.
  tap_node "Cubit" 2
  adb shell input swipe $((W*50/100)) $((H*78/100)) $((W*50/100)) $((H*78/100)) 1200; sleep 2
  shot cubit
  # Momentum penuh: sentakan cepat, sapuan berlanjut setelah jari diangkat.
  tap_node "Dorong" 2
  slide "Momentum" 1.0 237
  shot momentum-penggeser
  adb shell input swipe $((W*12/100)) $((H*90/100)) $((W*20/100)) $((H*88/100)) 80; sleep 3
  shot momentum-sentak
  slide "Momentum" 0.0 237
  adb shell input swipe $((W*12/100)) $((H*62/100)) $((W*20/100)) $((H*60/100)) 80; sleep 3
  shot tanpa-momentum
  # Tepi: menyapu di sepanjang garis tebal menipiskannya.
  tap_node "Tepi" 2
  adb shell input swipe $((W*70/100 + 7)) $((H*60/100)) $((W*70/100 + 7)) $((H*92/100)) 1500; sleep 2
  shot tepi
  terapkan; }
depan
shot diterapkan
tap_node "Urungkan" 3 || true
shot diurungkan

adb shell run-as $PKG cat files/laporan.txt > $OUT/laporan.txt 2>&1
adb logcat -d > $OUT/logcat.txt
grep -E "Liquify|FATAL|AndroidRuntime: (FATAL|java)" $OUT/logcat.txt | head -60 > $OUT/galat.txt
grep -E "Liquify" $OUT/laporan.txt >> $OUT/galat.txt
echo selesai
