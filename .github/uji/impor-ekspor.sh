#!/usr/bin/env bash
# Uji: impor PSD/.procreate/PDF jadi karya berlapis, ekspor TIFF/tiap layer/animasi, potong-salin-tempel + geser 3 jari.
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
ketik() { local s="$1"; for ((i=0; i<${#s}; i++)); do adb shell input text "${s:$i:1}"; sleep 0.4; done; }
tahan() { local p; p=$(find_node "$1"); [ -z "$p" ] && { echo "TIDAK KETEMU: $1"; return 1; }; adb shell input swipe $p $p 900; sleep 2; }
pakai() { # kategori kuas
  tap_node "Kuas" 3 || true
  tap_node "$1" 2 || true
  tap_node "$2" 2 || true
  tap_node "Kuas" 2 || true
}
cari() { # kata kunci, nama kuas persis
  local k
  for k in 1 2 3; do
    [ -n "$(find_bounds "Cari kuas atau daftar…")" ] && break
    tap_node "Kuas" 4 || true
  done
  if tap_node "Cari kuas atau daftar…" 2; then
    ketik "$1"; sleep 3
    # Tutup papan ketik dulu supaya hasil di bawahnya bisa diketuk.
    adb shell input keyevent KEYCODE_ENTER; sleep 2
    tap_node "$2" 2 || echo "TIDAK KETEMU KUAS: $2"
    tap_node "Hapus" 2 || true
  fi
  [ -n "$(find_bounds "Cari kuas atau daftar…")" ] && tap_node "Kuas" 2
  depan
}
# Hentikan uji bila aplikasi tidak lagi di depan (supaya gerakan tidak mengenai aplikasi lain).
depan() {
  adb shell dumpsys activity activities | grep -q "ResumedActivity.*$PKG" && return 0
  shot keluar; echo "APLIKASI TIDAK DI DEPAN"
  adb shell run-as $PKG cat files/laporan.txt > $OUT/laporan.txt 2>&1
  adb logcat -d -t 1500 | grep -i "goresan\|hananote\|AndroidRuntime" | tail -120 > $OUT/logcat.txt
  exit 0
}
# Panel Aksi sedang terbuka (satu kali dump).
panel_aksi() {
  adb shell uiautomator dump /sdcard/ui.xml >> "$OUT/dump-log.txt" 2>&1
  adb shell cat /sdcard/ui.xml 2>/dev/null | grep -q 'text="Tambah teks"\|text="Simpan PDF"\|text="Rekam time-lapse"\|text="Animation Assist"\|text="Info kanvas"\|text="Preferensi"\|text="Panduan menggambar"'
}
# Cari baris di panel Aksi (buka panel bila perlu, gulir panel bila belum tampak; tidak pernah menggeser kanvas).
aksi() {
  local k
  for k in 1 2 3; do
    panel_aksi && break
    tap_node "Aksi" 3
  done
  for k in 1 2 3 4 5; do
    [ -n "$(find_bounds "$1")" ] && break
    panel_aksi || { echo "PANEL AKSI TIDAK TERBUKA: $1"; return 1; }
    adb shell input swipe $(dp 200) $((H * 80 / 100)) $(dp 200) $((H * 40 / 100)) 600; sleep 2
  done
  tap_node "$1" 3
}
kosongkan() { # kolom teks yang sedang aktif
  adb shell input keyevent KEYCODE_MOVE_END
  for k in 1 2 3 4 5 6 7; do adb shell input keyevent KEYCODE_DEL; done
}
for k in $(seq 1 60); do adb shell service check activity 2>/dev/null | grep -q "found" && break; sleep 3; done
# Berkas uji (PSD dari ImageMagick, .procreate buatan, PDF 3 halaman).
sudo apt-get install -y -qq imagemagick liblzo2-dev > /dev/null 2>&1
pip install -q python-lzo lz4 pillow numpy 2>&1 | tail -1
python3 .github/uji/impor/buat.py /tmp/fx | tee $OUT/buat.txt
# Taruh berkas di folder data aplikasi (butuh aplikasi pernah dibuka sekali).
adb shell am start -n $PKG/id.goresan.DrawActivity --ez uji_buat true
sleep 20
for f in im-rle.psd im-16.psd uji-procreate.procreate uji.pdf; do
  adb push /tmp/fx/$f /data/local/tmp/$f >/dev/null
  adb shell "cat /data/local/tmp/$f | run-as $PKG sh -c 'cat > files/$f'"
done
adb shell run-as $PKG ls -l files/ > $OUT/berkas.txt 2>&1
buka() {
  adb shell am force-stop $PKG
  for k in 1 2 3; do
    adb shell am start -W -n $PKG/id.goresan.DrawActivity --ez uji_buat true --es uji_impor "$1" 2>&1 | tee -a $OUT/start.txt | grep -q "Status: ok" && break
    sleep 10
  done
  sleep 25
  depan
}
layer() { tap_node "Layer" 3 || true; shot "$1"; tap_node "Layer" 2 || true; }

# 1. PSD dari ImageMagick (layer bergeser, transparan).
buka im-rle.psd
shot psd
layer psd-layer
buka im-16.psd
shot psd-16bit
# 2. .procreate (LZO, LZ4, grup, masker, mode campur, latar biru).
buka uji-procreate.procreate
shot procreate
layer procreate-layer
# 3. PDF tiga halaman: tiap halaman satu layer, Page Assist menyala.
buka uji.pdf
shot pdf
layer pdf-layer

# 4. Ekspor dari karya .procreate.
buka uji-procreate.procreate
aksi "Simpan TIFF" || true
sleep 8; shot tiff
aksi "Ekspor tiap layer: PNG" || true
sleep 12; shot layer-png
aksi "Ekspor tiap layer: PDF" || true
sleep 10; shot layer-pdf
aksi "Ekspor animasi GIF" || true
sleep 15; shot gif
depan
adb shell ls -lR /sdcard/Download/WInote /sdcard/Pictures/WInote /sdcard/Movies/WInote > $OUT/ekspor.txt 2>&1
mkdir -p $OUT/ekspor
adb pull /sdcard/Download/WInote $OUT/ekspor/ > /dev/null 2>&1
adb pull /sdcard/Pictures/WInote $OUT/ekspor/ > /dev/null 2>&1
python3 - "$OUT/ekspor" <<'PY' >> $OUT/ekspor.txt 2>&1
import glob, sys
from PIL import Image
for p in sorted(glob.glob(sys.argv[1] + "/**/*", recursive=True)):
    if p.lower().endswith((".tif", ".png", ".gif")):
        try:
            im = Image.open(p)
            print("BACA", p, im.format, im.mode, im.size, getattr(im, "n_frames", 1), im.info.get("dpi"))
        except Exception as e:
            print("GAGAL BACA", p, e)
PY

# 5. Salin & tempel lewat panel Aksi, lalu menu geser tiga jari.
aksi "Salin" || true
sleep 3
aksi "Tempel" || true
sleep 4
shot tempel
layer tempel-layer
gerak geser3 25
sleep 3
shot menu-tiga-jari
tap_node "Duplikat" 3 || true
sleep 3
layer duplikat-layer
gerak geser3 25
sleep 3
tap_node "Potong" 3 || true
sleep 3
shot dipotong
gerak ketuk 2
sleep 3
shot potong-diurungkan
adb shell run-as $PKG cat files/laporan.txt > $OUT/laporan.txt 2>&1
adb logcat -d > $OUT/logcat-penuh.txt
grep -E "Impor|impor|FATAL|AndroidRuntime: (FATAL|java)|Papan klip|TIFF|layer .* gagal" $OUT/logcat-penuh.txt | head -80 > $OUT/galat.txt
grep -E "Impor|impor|TIFF|Papan|gagal" $OUT/laporan.txt >> $OUT/galat.txt
rm -f $OUT/logcat-penuh.txt
echo selesai
