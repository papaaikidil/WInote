#!/usr/bin/env bash
# Uji urungkan/ulangi yang responsif (ketuk & tahan 2-3 jari, tahan tombol) dan stabilisasi gaya Procreate.
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
# Pesan singkat (flash) yang sedang tampil, dicatat ke berkas.
pesan() {
  adb shell uiautomator dump /sdcard/ui-p.xml >/dev/null 2>&1
  echo "$1: $(adb shell cat /sdcard/ui-p.xml | grep -o 'text="\(Urungkan\|Ulangi\|Tidak ada\)[^"]*"' | head -3 | tr '\n' ' ')" >> $OUT/pesan.txt
}
for k in $(seq 1 60); do adb shell service check activity 2>/dev/null | grep -q "found" && break; sleep 3; done
for k in 1 2 3; do
  adb shell am start -W -n $PKG/id.goresan.DrawActivity --ez uji_buat true 2>&1 | tee -a $OUT/start.txt | grep -q "Status: ok" && break
  sleep 10
done
sleep 20
shot awal
# 1. Garis bergetar tanpa penghalus (baris atas), lalu dengan stabilisasi/penyaring gerak global (baris bawah).
gerak garis 25 18 88 18 3
shot garis-mentah
atur() { # label nilai
  local lab="$1"
  tap_node "Aksi" 3 || true
  # Gulir panel Aksi sampai paling bawah (bagian Tekanan & penghalusan ada di dekat akhir).
  for k in 1 2 3 4 5 6; do
    adb shell input swipe $(dp 150) $(dp 600) $(dp 150) $(dp 150) 400; sleep 1
  done
  sleep 1
  # Seret penggeser ke ujung kanan (1) atau kiri (0) supaya nilainya pasti.
  local b; b=$(find_bounds "$1")
  if [ -n "$b" ]; then
    set -- $b "$2"
    local y=$(( $4 + $(dp 24) )) xm=$(( $1 + $(dp 176) ))
    if [ "$5" = "1.0" ]; then adb shell input swipe $xm $y $(( $1 + $(dp 400) )) $y 500; else adb shell input swipe $xm $y 2 $y 500; fi
    sleep 2
  fi
  shot "aksi-$(echo $lab | cut -c1-8 | tr ' ' '-')"
  tap_node "Aksi" 3 || true
}
atur "Jumlah stabilisasi" 1.0
gerak garis 25 30 88 30 3
shot garis-stabil
atur "Jumlah stabilisasi" 0.0
atur "Penyaring gerak" 1.0
gerak garis 25 42 88 42 3
shot garis-penyaring
# Pena masih menempel di ujung garis (penyaring gerak menahan ujung goresan sepanjang tali):
# tinta harus tetap sampai di ujung pena tanpa jeda, lalu diangkat.
gerak garis 25 48 70 48 3 tahan
shot ekor-pena-menempel
gerak angkat
shot ekor-diangkat
atur "Penyaring gerak" 0.0
# 2. Sepuluh goresan pendek untuk diurungkan.
for i in 0 1 2 3 4 5 6 7 8 9; do
  y=$(( 54 + i * 3 ))
  gerak garis 30 $y 80 $y 0
done
shot sepuluh-goresan
# Ketuk dua jari = urungkan satu (langsung, tidak menunggu).
gerak ketuk 2
pesan "ketuk-2"
shot urungkan-1
# Ketuk tiga jari = ulangi satu.
gerak ketuk 3
pesan "ketuk-3"
shot ulangi-1
# Tahan dua jari ±1,6 detik = urungkan berulang (makin cepat).
gerak tahan 2 1600
pesan "tahan-2"
shot tahan-dua-jari
# Tahan tiga jari = ulangi berulang.
gerak tahan 3 1200
pesan "tahan-3"
shot tahan-tiga-jari
# Tahan tombol Urungkan di bilah samping.
p=$(find_node "Urungkan")
if [ -n "$p" ]; then adb shell input swipe $p $p 1500; sleep 2; fi
pesan "tahan-tombol"
shot tahan-tombol-urungkan
# Pena melayang lalu ketuk dua jari: tetap mengurungkan.
gerak tahan 3 900
adb shell input stylus motionevent HOVER_ENTER $((W*30/100)) $((H*30/100)) 2>&1 | head -1
adb shell input stylus motionevent HOVER_MOVE $((W*31/100)) $((H*30/100)) 2>&1 | head -1
gerak ketuk 2
pesan "melayang-ketuk-2"
shot melayang-ketuk-dua
adb shell input stylus motionevent HOVER_EXIT $((W*31/100)) $((H*30/100)) 2>&1 | head -1
# 3. Studio kuas: bagian Stabilisasi seperti Procreate.
tap_node "Kuas" 3 || true
shot pustaka
tap_node "Tinta" 2 || true
tap_node "Tinta Studio" 3 || true
tap_node "Tinta Studio" 4 || true
# Gulir Studio (panel di kanan) sampai bagian Stabilisasi terlihat.
for k in 1 2 3 4 5 6 7 8; do
  b=$(find_bounds "Penyaring gerak")
  if [ -n "$b" ]; then set -- $b; [ $4 -lt $((H*70/100)) ] && break; fi
  adb shell input swipe $((W*80/100)) $((H*70/100)) $((W*80/100)) $((H*30/100)) 600; sleep 2
done
shot studio-stabilisasi
adb shell input keyevent KEYCODE_BACK; sleep 2
shot akhir
adb shell run-as $PKG cat files/laporan.txt > $OUT/laporan.txt 2>&1
adb logcat -d -t 800 | grep -i "goresan\|hananote\|AndroidRuntime" | tail -80 > $OUT/logcat.txt
echo selesai
