#!/usr/bin/env bash
# Uji: opasitas ketuk dua jari, Alpha Lock geser dua jari, satukan ke grup, gosok tiga jari, QuickMenu, impor font, ekspor APNG.
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
adb shell am start -n $PKG/id.goresan.DrawActivity --ez uji_buat true
sleep 20
cp app/src/main/assets/fonts/bangers.ttf /tmp/UjiFont.ttf
adb push /tmp/UjiFont.ttf /data/local/tmp/UjiFont.ttf >/dev/null
adb shell "cat /data/local/tmp/UjiFont.ttf | run-as $PKG sh -c 'cat > files/UjiFont.ttf'"
adb shell am force-stop $PKG
adb shell am start -W -n $PKG/id.goresan.DrawActivity --ez uji_buat true --es uji_warna 1E88E5 --es uji_font UjiFont.ttf
sleep 25
depan
buka_layer() { [ -n "$(find_bounds "Pilih")" ] || tap_node "Layer" 3; }
tutup_layer() { [ -n "$(find_bounds "Pilih")" ] && tap_node "Layer" 2; return 0; }
tebal() {
  local x0=$1 y0=$2 x1=$3 y1=$4 k
  for k in 0 1 2 3 4 5; do gerak garis $x0 $(python3 -c "print($y0+$k*0.8)") $x1 $(python3 -c "print($y1+$k*0.8)") 0; done
}
tebal 15 40 55 40
shot goresan

# 1. Ketuk dua jari di layer = opasitas; geser jari di kanvas menurunkan opasitas; ketuk = selesai.
buka_layer
read X Y < <(find_node "Layer 1")
gerak ketuk2xy $X $Y
sleep 2
shot opasitas-mode
gerak seret $((W * 60 / 100)) $((H * 60 / 100)) $((W * 30 / 100)) $((H * 60 / 100)) 0
sleep 2
shot opasitas-diturunkan
adb shell input tap $((W * 50 / 100)) $((H * 80 / 100)); sleep 2
buka_layer
shot opasitas-layer

# 2. Geser dua jari ke kanan di layer = Alpha Lock.
read X Y < <(find_node "Layer 1")
gerak geser2xy $X $Y $(dp 150)
sleep 2
shot alpha-lock-geser2

# 3. Layer kedua, lalu Satukan ke grup dengan layer bawah.
tap_node "Tambah layer" 2
tutup_layer
tebal 15 55 55 55
buka_layer
tap_node "Layer 2" 2
tap_node "Satukan ke grup dengan layer bawah" 3
shot satukan-grup
tutup_layer

# 4. Gosok tiga jari = bersihkan layer aktif, lalu urungkan.
gerak gosok3
sleep 3
shot gosok-tiga-jari
tap_node "Urungkan" 3 || true
shot gosok-diurungkan

# 5. QuickMenu: tahan tombol pipet.
tahan "Pipet"
shot quickmenu
tahan "Quick: Bersihkan layer"
shot quickmenu-ganti
tap_node "Balik warna" 2
shot quickmenu-diganti
tap_node "Quick: Balik warna" 3
shot quickmenu-dijalankan
tap_node "Urungkan" 2 || true

# 6. Font impor muncul di kategori Font saya.
aksi "Tambah teks"
sleep 3
tap_node "Tutup keyboard" 2 || adb shell input keyevent KEYCODE_BACK
sleep 2
tap_node "Font saya" 2
shot font-saya
tap_node "UjiFont" 2 || true
shot font-dipakai
tap_node "Batal" 2 || adb shell input keyevent KEYCODE_BACK
sleep 2

# 7. Ekspor animasi PNG (APNG).
depan
aksi "Ekspor animasi PNG (APNG)"
sleep 20
shot apng
mkdir -p $OUT/ekspor
adb shell ls -la /sdcard/Pictures/WInote > $OUT/ekspor.txt 2>&1
adb pull /sdcard/Pictures/WInote $OUT/ekspor/ > /dev/null 2>&1
python3 - $OUT/ekspor >> $OUT/ekspor.txt 2>&1 <<'PY'
import sys, glob, os
from PIL import Image
for p in glob.glob(os.path.join(sys.argv[1], "**", "*animasi*.png"), recursive=True):
    im = Image.open(p)
    print("APNG", os.path.basename(p), im.size, "bingkai", getattr(im, "n_frames", 1), "mode", im.mode)
PY
adb shell run-as $PKG ls -la files/fonts >> $OUT/ekspor.txt 2>&1
adb shell run-as $PKG cat files/laporan.txt > $OUT/laporan.txt 2>&1
adb logcat -d -t 3000 | grep -i "goresan\|hananote\|AndroidRuntime\|FATAL" | tail -200 > $OUT/logcat.txt
grep -n "FATAL" $OUT/logcat.txt | head -20 > $OUT/galat.txt
echo selesai
