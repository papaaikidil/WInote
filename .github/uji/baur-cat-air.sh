#!/usr/bin/env bash
# Uji baur mengikuti kuas: cat air dibaurkan larut (halus), dibanding menyeret, dan kuas Pembaur Cat Air.
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
for k in $(seq 1 60); do adb shell service check activity 2>/dev/null | grep -q "found" && break; sleep 3; done
for k in 1 2 3; do
  adb shell am start -W -n $PKG/id.goresan.DrawActivity --ez uji_buat true --es uji_warna D81B60 2>&1 | tee -a $OUT/start.txt | grep -q "Status: ok" && break
  sleep 10
done
sleep 20
shot awal
nilai() {
  find_bounds "$1" >/dev/null
  python3 - "$OUT/ui-last.xml" "$1" <<'PY' | tee -a $OUT/nilai.txt
import re, sys, html
xml = open(sys.argv[1], encoding='utf-8', errors='ignore').read()
texts = [html.unescape(t) for t in re.findall(r' text="([^"]*)"', xml)]
want = sys.argv[2]
for i, t in enumerate(texts):
    if t.startswith(want):
        print(t, '=', texts[i + 1] if i + 1 < len(texts) else '?')
        break
else:
    print(want, '= TIDAK ADA')
PY
}
ketik() { local s="$1"; for ((i=0; i<${#s}; i++)); do adb shell input text "${s:$i:1}"; sleep 0.4; done; }
# Tab Properti ada di bawah daftar tab Studio: gulir daftar tab dulu, lalu gulir isinya ke bawah.
properti() {
  tap_node "Properti" 2 || { adb shell input swipe $((W*61/100)) $((H*70/100)) $((W*61/100)) $((H*30/100)) 600; sleep 2; tap_node "Properti" 2; } || true
}
# Pilih kuas lewat kotak cari di pustaka (pustaka sudah terbuka).
cari_kuas() {
  tap_node "Cari kuas atau daftar…" 2 || return 1
  ketik "$1"; sleep 3
  adb shell input keyevent KEYCODE_ENTER; sleep 2
  tap_node "$2" 3 || echo "TIDAK KETEMU KUAS: $2"
}
warna_btn() { adb shell input tap $((W - $(dp 30))) $(dp 25); sleep 2; }
warna() { warna_btn; tap_node "Warna #$1" 2 || true; shot "warna-$1"; warna_btn; }
pilih() { tap_node "Kuas" 3 || true; tap_node "Cat Air" 2 || true; tap_node "$1" 3 || true; shot "pustaka-$(echo $1 | tr ' ' '-')"; tap_node "Kuas" 3 || true; }
# Tiga baris, masing-masing: merah muda di kiri, kuning jingga di kanan, bertemu di tengah (x ≈ 50%).
ROWS="20 45 70"
pilih "Cat Air Utama"
for y in $ROWS; do for dy in 0 4 8; do gerak garis 16 $((y+dy)) 52 $((y+dy)) 0; done; done
sleep 9
warna FFB300
for y in $ROWS; do for dy in 0 4 8; do gerak garis 48 $((y+dy)) 84 $((y+dy)) 0; done; done
sleep 9
shot sebelum-baur
# Baris 1: alat Baur + Cat Air Utama (otomatis larut).
tap_node "Baur" 2 || true
tap_node "Baur" 3 || true
tap_node "Cat Air" 2 || true
tap_node "Cat Air Utama" 3 || true
shot pustaka-baur
tap_node "Cat Air Utama" 4 || true
properti
adb shell input swipe $((W*70/100)) $((H*75/100)) $((W*70/100)) $((H*35/100)) 600; sleep 2
shot studio-properti
nilai "Larut saat dibaurkan"
tap_node "Kembali" 2 || true
tap_node "Baur" 3 || true
baur() { local y=$1; for dy in 0 2 4 6 8 10; do
  if [ $((dy % 4)) -eq 0 ]; then gerak garis 36 $((y+dy)) 64 $((y+dy)) 0; else gerak garis 64 $((y+dy)) 36 $((y+dy)) 0; fi
done; }
baur 19
shot baris1-larut
# Baris 2: larut 0% (menyeret, seperti sebelumnya) untuk pembanding.
tap_node "Baur" 3 || true
tap_node "Cat Air Utama" 4 || true
properti
adb shell input swipe $((W*70/100)) $((H*75/100)) $((W*70/100)) $((H*35/100)) 600; sleep 2
slide "Larut saat dibaurkan (otomatis)" 0.0 396 2
nilai "Larut saat dibaurkan"
shot studio-larut-0
tap_node "Kembali" 2 || true
tap_node "Baur" 3 || true
baur 44
shot baris2-seret
# Kembalikan ke otomatis.
tap_node "Baur" 3 || true
tap_node "Cat Air Utama" 4 || true
properti
adb shell input swipe $((W*70/100)) $((H*75/100)) $((W*70/100)) $((H*35/100)) 600; sleep 2
tap_node "Kembalikan ke otomatis" 2 || true
nilai "Larut saat dibaurkan"
shot studio-otomatis
tap_node "Kembali" 2 || true
tap_node "Baur" 3 || true
# Baris 3: kuas Pembaur Cat Air dengan alat Kuas (tidak menambah warna).
tap_node "Kuas" 2 || true
tap_node "Kuas" 3 || true
cari_kuas pembaur "Pembaur Cat Air"
shot pustaka-pembaur
tap_node "Kuas" 3 || true
baur 69
shot baris3-pembaur
# Urungkan satu goresan pembaur lalu ulangi (urungkan harus berfungsi).
tap_node "Urungkan" 2 || true
shot urung
tap_node "Ulangi" 2 || true
shot akhir
adb shell run-as $PKG cat files/laporan.txt > $OUT/laporan.txt 2>&1
adb logcat -d -t 600 | grep -i "goresan\|hananote\|AndroidRuntime\|GL_\|glError" | tail -80 > $OUT/logcat.txt
cat $OUT/nilai.txt
echo selesai
