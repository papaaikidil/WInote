#!/usr/bin/env bash
# Uji fitur teks: font komik/artistik/eksklusif, gaya, bentuk baris (lengkung, gelombang, lingkaran, jalur).
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
for k in $(seq 1 60); do adb shell service check activity 2>/dev/null | grep -q "found" && break; sleep 3; done
for k in 1 2 3; do
  adb shell am start -W -n $PKG/id.goresan.DrawActivity --ez uji_buat true 2>&1 | tee -a $OUT/start.txt | grep -q "Status: ok" && break
  sleep 10
done
sleep 20
tambah_teks() { # teks (spasi = %s)
  tap_node "Aksi" 5 || true
  tap_node "Tambah teks" 6 || true
  tap_node "Ketik teks… (Enter = baris baru)" 4 || true
  # Diketik per huruf dengan jeda seperti orang mengetik (emulator lambat membuang ketukan yang terlalu cepat).
  python3 -c "import sys; print('\n'.join(sys.argv[1].replace('%s',' ')))" "$1" | while IFS= read -r ch; do
    if [ "$ch" = " " ] || [ -z "$ch" ]; then adb shell input keyevent KEYCODE_SPACE < /dev/null; else adb shell input text "$ch" < /dev/null; fi
    sleep 0.8
  done
  sleep 3
  adb shell uiautomator dump /sdcard/ui-teks.xml >/dev/null 2>&1
  adb shell cat /sdcard/ui-teks.xml | grep -o 'text="[^"]*"' | grep -v 'text=""' | head -8 >> $OUT/isi-kolom.txt
  echo "--" >> $OUT/isi-kolom.txt
  shot "ketik-$(echo $1 | cut -c1-6)"
  tap_node "Tutup keyboard" 3 || true
}
# 1. Teks komik (Bangers) lurus, lalu lengkung dan lingkaran.
tambah_teks "WAH%sKEREN"
shot ketik
tap_node "Bentuk" 2 || true
tap_node "Lengkung" 3 || true
shot lengkung
tap_node "Lingkaran" 3 || true
shot lingkaran
tap_node "Lengkung" 3 || true
tap_node "Efek" 2 || true
shot efek
tap_node "Selesai" 4 || true
shot teks-1
# Geser teks pertama tidak bisa setelah selesai; teks kedua: eksklusif + gelombang, dipindah ke bawah.
tambah_teks "Elegan%sSekali"
tap_node "Font" 2 || true
tap_node "Eksklusif" 2 || true
tap_node "Great Vibes" 3 || true
shot font-eksklusif
tap_node "Bentuk" 2 || true
tap_node "Gelombang" 3 || true
adb shell input swipe $((W*50/100)) $((H*20/100)) $((W*30/100)) $((H*12/100)) 800; sleep 3
shot gelombang
tap_node "Selesai" 4 || true
# 3. Artistik + jalur bebas yang digambar sendiri.
tambah_teks "ikuti%sjalur%sini%sya"
tap_node "Font" 2 || true
tap_node "Artistik" 2 || true
tap_node "Pacifico" 3 || true
tap_node "Bentuk" 2 || true
tap_node "Jalur bebas" 3 || true
python3 - "$W" "$H" > /tmp/jalur.txt <<'PY'
import math, sys
W, H = map(float, sys.argv[1:3])
pts = [(int(W * (0.15 + 0.7 * t)), int(H * (0.36 + 0.1 * math.sin(t * 6.28)))) for t in [i / 30 for i in range(31)]]
print("DOWN %d %d" % pts[0])
for p in pts[1:]: print("MOVE %d %d" % p)
print("UP %d %d" % pts[-1])
PY
while read a x y; do adb shell input motionevent $a $x $y < /dev/null; done < /tmp/jalur.txt
sleep 4
shot jalur
tap_node "Selesai" 4 || true
sleep 3
shot akhir
tap_node "Layer" 3 || true
shot layer
adb shell run-as $PKG cat files/laporan.txt > $OUT/laporan.txt 2>&1
adb logcat -d -t 800 | grep -i "goresan\|hananote\|AndroidRuntime" | tail -80 > $OUT/logcat.txt
echo selesai
