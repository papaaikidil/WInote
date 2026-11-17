#!/usr/bin/env bash
# Uji selotip hafalan di catatan: tempel (lurus, bebas, bentuk), ketuk buka/tutup, tutup/buka semua, simpan, hapus.
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
for k in $(seq 1 60); do adb shell service check activity 2>/dev/null | grep -q "found" && break; sleep 3; done
# Catatan baru langsung terbuka (seperti dari widget).
for k in 1 2 3; do
  adb shell am start -W -n $PKG/id.hananote.MainActivity -a id.hananote.action.NEW_NOTE 2>&1 | tee -a $OUT/start.txt | grep -q "Status: ok" && break
  sleep 10
done
sleep 15
shot awal
find_bounds "Selotip" > /dev/null
cp $OUT/ui-last.xml $OUT/ui-awal.xml
# Tulisan tangan (jari menulis di emulator): empat baris, dua kata tiap baris.
for y in 30 42 54 66; do gerak garis 15 $y 45 $y 7; gerak garis 55 $y 85 $y 7; done
shot tulisan
tap_node "Selotip" 3 || echo "TOMBOL SELOTIP TIDAK ADA"
shot alat-selotip
bentuk() { tap_node "Bentuk selotip" 2 || true; tap_node "$1" 2 || echo "BENTUK TIDAK KETEMU: $1"; }
# Lurus (bawaan), bebas, dan bentuk-bentuk.
gerak garis 14 30 46 30 0
bentuk "Bebas"; gerak garis 54 30 86 31 10
bentuk "Kotak"; gerak garis 14 38 46 46 0
bentuk "Lingkaran"; gerak garis 54 37 86 47 0
bentuk "Segitiga"; gerak garis 14 49 29 59 0
bentuk "Bintang"; gerak garis 31 49 46 60 0
bentuk "Hati"; gerak garis 54 49 69 60 0
bentuk "Belah ketupat"; gerak garis 71 49 86 60 0
bentuk "Segi enam"; gerak garis 14 61 46 71 0
bentuk "Kotak tumpul"; gerak garis 54 61 86 71 0
shot semua-tertutup
# Ketuk dengan alat Selotip: terbuka.
adb shell input tap $((W*30/100)) $((H*30/100)); sleep 2
adb shell input tap $((W*70/100)) $((H*42/100)); sleep 2
shot dua-terbuka
# Ketuk lagi: tertutup kembali.
adb shell input tap $((W*70/100)) $((H*42/100)); sleep 2
shot satu-tutup-lagi
# Alat Pena: ketuk selotip = buka; tarik di atas selotip = menulis.
tap_node "Pena" 2 || true
adb shell input tap $((W*22/100)) $((H*56/100)); sleep 2
shot pena-ketuk
gerak garis 58 64 82 68 4
shot pena-tulis
# Tutup semua / buka semua.
tap_node "Selotip" 3 || true
tap_node "Tutup semua selotip" 2 || echo "TUTUP SEMUA TIDAK KETEMU"
shot tutup-semua
tap_node "Buka semua selotip" 2 || echo "BUKA SEMUA TIDAK KETEMU"
shot buka-semua
tap_node "Tutup semua selotip" 2 || true
adb shell input tap $((W*30/100)) $((H*30/100)); sleep 2
shot sebelum-simpan
# Simpan: keluar ke layar utama, hentikan aplikasi, lalu buka catatan yang sama.
sleep 4
adb shell input keyevent KEYCODE_HOME; sleep 4
adb shell am force-stop $PKG; sleep 2
NID=$(adb shell run-as $PKG ls files/hana/notes | tr -d '\r' | head -1)
echo "catatan: $NID" | tee $OUT/catatan.txt
adb shell am start -W -n $PKG/id.hananote.MainActivity -a id.hananote.action.OPEN_NOTE --es note_id "$NID" 2>&1 | tee -a $OUT/start.txt
sleep 12
shot buka-ulang
# Lepas semua lalu urungkan.
tap_node "Selotip" 3 || true
tap_node "Lepas semua selotip" 3 || echo "LEPAS SEMUA TIDAK KETEMU"
shot lepas-semua
tap_node "Urungkan" 3 || echo "URUNGKAN TIDAK KETEMU"
shot urungkan
# Penghapus menghapus selotip yang tersapu.
tap_node "Penghapus" 2 || true
gerak garis 10 66 50 66 0
shot penghapus
# Pilih: selotip bisa dipilih, digeser, dan diganti warnanya.
tap_node "Pilih" 2 || true
adb shell input tap $((W*70/100)) $((H*42/100)); sleep 2
shot pilih
adb shell run-as $PKG ls -la files/hana/notes/$NID > $OUT/berkas.txt 2>&1
adb logcat -d | grep -iE "FATAL|AndroidRuntime|hananote" | tail -80 > $OUT/logcat.txt
grep -c "FATAL" $OUT/logcat.txt
echo selesai
