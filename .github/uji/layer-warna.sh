#!/usr/bin/env bash
# Uji: masker layer, layer referensi + SwatchDrop, balik warna, pilih isi layer, gestur panel layer, harmoni warna, palet.
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
sudo apt-get install -y -qq imagemagick liblzo2-dev > /dev/null 2>&1
pip install -q python-lzo lz4 pillow numpy 2>&1 | tail -1
python3 .github/uji/impor/buat.py /tmp/fx > $OUT/buat.txt 2>&1
# Foto berwarna untuk palet otomatis.
python3 - <<'PY'
from PIL import Image, ImageDraw
im = Image.new("RGB", (400, 300), (240, 230, 210))
d = ImageDraw.Draw(im)
for i, c in enumerate([(200, 40, 40), (30, 120, 200), (250, 200, 40), (40, 150, 70), (120, 60, 160)]):
    d.rectangle([i * 80, 150, i * 80 + 79, 299], fill=c)
d.ellipse([140, 20, 260, 140], fill=(255, 140, 0))
im.save("/tmp/fx/foto.png")
PY
adb shell am start -n $PKG/id.goresan.DrawActivity --ez uji_buat true
sleep 20
for f in palet/uji.ase palet/uji.aco palet/uji.swatches palet/uji.gpl foto.png; do
  b=$(basename $f)
  adb push /tmp/fx/$f /data/local/tmp/$b >/dev/null
  adb shell "cat /data/local/tmp/$b | run-as $PKG sh -c 'cat > files/$b'"
done
adb shell am force-stop $PKG
adb shell am start -W -n $PKG/id.goresan.DrawActivity --ez uji_buat true --es uji_warna E53935 \
  --es uji_palet uji.ase,uji.aco,uji.swatches,uji.gpl --es uji_palet_foto foto.png
sleep 25
depan
layer() { tap_node "Layer" 3 || true; shot "$1"; tap_node "Layer" 2 || true; }
buka_layer() { [ -n "$(find_bounds "Pilih")" ] || tap_node "Layer" 3; }
tutup_layer() { [ -n "$(find_bounds "Pilih")" ] && tap_node "Layer" 2; return 0; }
warna() { # buka panel warna
  [ -n "$(find_bounds "Cakram")" ] || { adb shell input tap $((W - $(dp 30))) $(dp 25); sleep 3; }
}
tutup_warna() { [ -n "$(find_bounds "Cakram")" ] && { adb shell input tap $((W - $(dp 30))) $(dp 25); sleep 2; }; return 0; }
tebal() { # goresan tebal: beberapa garis sejajar (persen layar)
  local x0=$1 y0=$2 x1=$3 y1=$4 k
  for k in 0 1 2 3 4; do gerak garis $x0 $(python3 -c "print($y0+$k*0.8)") $x1 $(python3 -c "print($y1+$k*0.8)") 0; done
}

# 1. Masker layer: goresan merah, masker, goresan hitam di masker menyembunyikan sebagian.
tebal 15 45 55 45
shot merah
buka_layer
tap_node "Layer 1" 2   # layer aktif → menu
tap_node "Masker layer" 3
shot masker-dibuat
tutup_layer
warna; tap_node "Warna #000000" 2 || true; tutup_warna
gerak garis 30 30 30 70 0
gerak garis 31 30 31 70 0
gerak garis 32 30 32 70 0
gerak garis 33 30 33 70 0
shot masker-digores
buka_layer
shot masker-layer
tap_node "Masker layer" 2    # masker sudah aktif → menu masker
tap_node "Balik masker" 3
shot masker-dibalik
tap_node "Masker layer" 2
tap_node "Terapkan masker" 3
shot masker-diterapkan
tap_node "Urungkan" 2 || true
tutup_layer

# 2. Layer referensi + SwatchDrop: garis kotak di Layer 2 (referensi), warna dijatuhkan ke Layer 3.
buka_layer
tap_node "Tambah layer" 2
tutup_layer
warna; tap_node "Warna #000000" 2 || true; tutup_warna
gerak garis 12 58 40 58 0
gerak garis 40 58 40 88 0
gerak garis 40 88 12 88 0
gerak garis 12 88 12 58 0
buka_layer
tap_node "Layer 2" 2
tap_node "Referensi (untuk ColorDrop)" 3
tap_node "Tambah layer" 2
shot referensi-layer
tutup_layer
warna
tap_node "Palet" 2
shot palet-tab
# SwatchDrop dari baris palet utama di tab Cakram (warna merah palet bawaan).
tap_node "Cakram" 2
read X0 Y0 < <(find_node "Warna #E53935")
if [ -n "$X0" ]; then
  gerak seret $X0 $Y0 $((W * 26 / 100)) $((H * 73 / 100)) 900
  sleep 4
fi
shot swatchdrop
tap_node "Selesai" 3 || true
shot swatchdrop-selesai

# 3. Balik warna dan Pilih isi layer pada Layer 3.
buka_layer
tap_node "Layer 3" 2
tap_node "Balik warna (invert)" 3
shot invert
tap_node "Layer 3" 2
tap_node "Pilih isi layer" 3
shot pilih-isi
tap_node "Hapus pilihan" 2 || true
tap_node "Pilihan" 2 || true

# 4. Gestur panel layer.
buka_layer
read X Y < <(find_node "Layer 3")
gerak ketuk2xy $X $Y
sleep 2
shot ketuk-dua-jari
read X Y < <(find_node "Layer 3")
adb shell input swipe $X $Y $((X - $(dp 160))) $Y 250; sleep 2
shot geser-kiri
tap_node "Duplikat" 3
shot duplikat
read X Y < <(find_node "Layer 1")
adb shell input swipe $X $Y $((X + $(dp 160))) $Y 250; sleep 2
shot geser-kanan
tap_node "Batal" 2 || true
read A1 B1 < <(find_node "Layer 3 salinan")
read A2 B2 < <(find_node "Layer 3")
if [ -n "$A1" ] && [ -n "$A2" ]; then
  gerak cubit $A1 $((B1 - $(dp 10))) $A2 $((B2 + $(dp 10))) 20
  sleep 3
fi
shot cubit-gabung
tutup_layer

# 5. Harmoni warna dan palet.
warna
tap_node "Harmoni" 2
shot harmoni
tap_node "Komplementer  ▾" 2 || true
tap_node "Triadik" 2 || true
shot harmoni-triadik
tap_node "Palet" 2
shot palet-impor
tap_node "Menu palet" 2 && tap_node "Simpan sebagai .swatches" 4
tutup_warna
adb shell ls -la /sdcard/Download/WInote > $OUT/ekspor.txt 2>&1
# Masker tersimpan dan terbuka lagi: tunggu simpan otomatis, buka ulang karya yang sama.
sleep 8
ART=$(adb shell run-as $PKG ls -t files/karya | head -1 | tr -d '\r')
adb shell run-as $PKG cat files/karya/$ART/doc.json > $OUT/doc.json 2>&1
adb shell run-as $PKG ls -l files/karya/$ART >> $OUT/doc.json 2>&1
adb shell am force-stop $PKG
adb shell am start -W -n $PKG/id.goresan.DrawActivity --es open_art "$ART" > /dev/null 2>&1
sleep 20
depan
buka_layer
shot dibuka-ulang
tap_node "Masker layer" 2
shot masker-dipilih-ulang
adb shell run-as $PKG cat shared_prefs/goresan.xml 2>/dev/null | grep -o '"palettes"[^<]*\|name="palettes">[^<]*' | head -c 3000 > $OUT/palet-prefs.txt
adb shell run-as $PKG cat files/laporan.txt > $OUT/laporan.txt 2>&1
adb logcat -d -t 3000 | grep -i "goresan\|hananote\|AndroidRuntime\|FATAL" | tail -200 > $OUT/logcat.txt
grep -n "FATAL\|AndroidRuntime" $OUT/logcat.txt | head -20 > $OUT/galat.txt
echo selesai
