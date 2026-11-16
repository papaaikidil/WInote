#!/usr/bin/env bash
# Uji ruang gambar di emulator: buat kanvas, menggores, ganti kuas, menggores lagi, ColorDrop.
# Hasil (tangkapan layar, laporan teknis, logcat) disimpan di folder uji-hasil.
set -x
PKG=id.hananote.app
OUT=uji-hasil
mkdir -p $OUT
adb install -r app/build/outputs/apk/debug/app-debug.apk || { echo "PASANG GAGAL"; exit 1; }
# Tunggu sistem tenang setelah boot, lalu tutup dialog "tidak merespons" bila ada.
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
find_node() {
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
            x0, y0, x1, y1 = map(int, b.groups())
            print((x0 + x1) // 2, (y0 + y1) // 2)
            break
PY
}
tap_node() {
  local p; p=$(find_node "$1")
  if [ -z "$p" ]; then echo "TIDAK KETEMU: $1"; return 1; fi
  adb shell input tap $p; sleep ${2:-2}
}

adb shell am start -n $PKG/id.goresan.DrawActivity --ez uji_buat true
sleep 10
shot kanvas-terbuka
KX=$((W - $(dp 210))); KY=$(dp 26)
kuas() { tap_node "Kuas" 3 || { adb shell input tap $KX $KY; sleep 3; }; }

# Kanvas kadang baru siap beberapa detik kemudian di emulator.
sleep 10
# Bentuk otomatis: lingkaran bergelombang, pena ditahan di ujung -> lingkaran rapi.
lingkaran() {
  local cx=$1 cy=$2 r=$3
  python3 - $cx $cy $r <<'PY' > /tmp/titik.txt
import math, sys, random
cx, cy, r = map(float, sys.argv[1:4]); random.seed(5)
for k in range(0, 27):
    t = -math.pi / 2 + k * 2 * math.pi / 24
    w = r * (1 + 0.07 * math.sin(3 * t) + random.uniform(-0.03, 0.03))
    print(int(cx + w * math.cos(t)), int(cy + w * math.sin(t)))
PY
  local first=1
  while read x y; do
    if [ $first = 1 ]; then adb shell input stylus motionevent DOWN $x $y </dev/null; first=0; else adb shell input stylus motionevent MOVE $x $y </dev/null; fi
    LX=$x; LY=$y
  done < /tmp/titik.txt
}
lingkaran $((W*36/100)) $((H*19/100)) $((H*8/100))
sleep 1.5
shot bentuk-ditahan
adb shell input stylus motionevent UP $LX $LY
sleep 2
shot bentuk-lingkaran

# Segitiga bergelombang, ditahan, lalu digeser sedikit (ukuran berubah) sebelum diangkat.
tx0=$((W*55/100)); tx1=$((W*72/100)); ty0=$((H*9/100)); ty1=$((H*29/100))
adb shell input stylus motionevent DOWN $(((tx0+tx1)/2)) $ty0
for k in 1 2 3 4 5 6; do adb shell input stylus motionevent MOVE $(((tx0+tx1)/2 + (tx1-tx0)/2*k/6 + (k%2)*6)) $((ty0 + (ty1-ty0)*k/6)); done
for k in 1 2 3 4 5 6; do adb shell input stylus motionevent MOVE $((tx1 - (tx1-tx0)*k/6)) $((ty1 + (k%2)*7)); done
for k in 1 2 3 4 5 6; do adb shell input stylus motionevent MOVE $((tx0 + (tx1-tx0)/2*k/6 - (k%2)*6)) $((ty1 - (ty1-ty0)*k/6)); done
sleep 1.5
adb shell input stylus motionevent MOVE $(((tx0+tx1)/2 + 20)) $((ty0 - 25))
adb shell input stylus motionevent MOVE $(((tx0+tx1)/2 + 40)) $((ty0 - 50))
sleep 1
adb shell input stylus motionevent UP $(((tx0+tx1)/2 + 40)) $((ty0 - 50))
sleep 2
shot bentuk-segitiga

# Sekali urungkan: segitiga kembali jadi coretan asli.
tap_node "Urungkan" 2 || true
shot urungkan-sekali
# ColorDrop ke dalam lingkaran rapi (warna sama dengan garisnya): garis dan isian harus menyatu tanpa pita pucat.
adb shell input swipe $((W - $(dp 32))) $(dp 26) $((W*36/100)) $((H*19/100)) 1500
sleep 5
shot colordrop-lingkaran
# Pena terdeteksi mematikan gambar-dengan-jari: nyalakan lagi untuk uji berikutnya.
tap_node "Pena saja" 2 || true

# Goresan panjang & pelan (7 detik) dimulai 1 detik setelah goresan lain: simpan otomatis (4 detik) tidak boleh memotongnya.
adb shell input swipe $((W*30/100)) $((H*40/100)) $((W*40/100)) $((H*40/100)) 300
sleep 1
adb shell input swipe $((W*15/100)) $((H*94/100)) $((W*85/100)) $((H*94/100)) 7000
sleep 2
shot goresan-panjang

# Grup layer: tambah layer, pilih dua layer, kelompokkan, sembunyikan/tampilkan, lipat.
tap_node "Layer" 3 || true
shot panel-layer
tap_node "Tambah layer" 2 || true
tap_node "Pilih" 2 || true
tap_node "Layer 1" 2 || true
shot pilih-dua-layer
tap_node "Kelompokkan" 3 || true
shot grup-dibuat
# Mode campur ala Procreate: ketuk huruf "N" di baris layer.
tap_node "N" 2 || true
shot panel-mode-campur
tap_node "Screen" 2 || true
shot mode-screen
# Kembali ke Normal (goresan berikutnya ada di layer ini), lalu tutup panel mode.
tap_node "Normal" 2 || true
tap_node "N" 2 || true
tap_node "Tampilkan grup" 3 || true
shot grup-disembunyikan
tap_node "Tampilkan grup" 3 || true
tap_node "Lipat grup" 2 || true
shot grup-dilipat

# Tahan & seret di panel layer (jari: tahan 1 detik, lalu geser bertahap).
seret() {
  local x1=$1 y1=$2 x2=$3 y2=$4
  adb shell input motionevent DOWN $x1 $y1 </dev/null
  sleep 1.2
  for k in 1 2 3 4 5; do adb shell input motionevent MOVE $((x1 + (x2-x1)*k/5)) $((y1 + (y2-y1)*k/5)) </dev/null; done
  sleep 0.6
  adb shell input motionevent UP $x2 $y2 </dev/null
  sleep 3
}
tap_node "Buka grup" 2 || true
shot sebelum-seret
p1=$(find_node "Layer 1"); pb=$(find_node "Warna latar")
if [ -n "$p1" ] && [ -n "$pb" ]; then
  set -- $p1; ax=$1; ay=$2; set -- $pb; bx=$1; by=$2
  # Lepas di baris latar, sisi kiri: Layer 1 keluar dari grup (tetap di bawah grup).
  seret $ax $ay $((bx - $(dp 100))) $by
fi
shot seret-keluar-grup
p1=$(find_node "Layer 1"); p3=$(find_node "Layer 3")
if [ -n "$p1" ] && [ -n "$p3" ]; then
  set -- $p1; ax=$1; ay=$2; set -- $p3; cx=$1; cy=$2
  # Lepas tepat di atas Layer 3 (anggota grup): Layer 1 masuk lagi ke grup.
  seret $ax $ay $cx $cy
fi
shot seret-ke-grup
adb shell input tap $((W*20/100)) $((H*50/100)); sleep 2

# Gores 1 dengan kuas awal (jari menggambar).
adb shell input swipe $((W*25/100)) $((H*35/100)) $((W*65/100)) $((H*40/100)) 700
sleep 2
shot gores1-kuas-awal

# Buka pustaka kuas, pilih Pensil HB.
kuas
shot pustaka-kuas
tap_node "Pensil HB" 2
shot pilih-pensil-hb
adb shell input tap $((W*90/100)) $((H*90/100)); sleep 1
adb shell input swipe $((W*25/100)) $((H*50/100)) $((W*65/100)) $((H*55/100)) 700
sleep 2
shot gores2-pensil-hb

# Pilih Tinta Studio (kategori Tinta).
kuas
tap_node "Tinta" 2
tap_node "Tinta Studio" 2
shot pilih-tinta-studio
adb shell input tap $((W*90/100)) $((H*90/100)); sleep 1
adb shell input swipe $((W*25/100)) $((H*65/100)) $((W*65/100)) $((H*70/100)) 700
sleep 2
shot gores3-tinta-studio

# Kotak tertutup dari 4 goresan (ujungnya saling menyilang).
kotak() {
  local x0=$1 y0=$2 x1=$3 y1=$4 e=$((W*15/1000))
  adb shell input swipe $((x0-e)) $y0 $((x1+e)) $y0 600; sleep 1
  adb shell input swipe $x1 $((y0-e)) $x1 $((y1+e)) 600; sleep 1
  adb shell input swipe $((x1+e)) $y1 $((x0-e)) $y1 600; sleep 1
  adb shell input swipe $x0 $((y1+e)) $x0 $((y0-e)) 600; sleep 1
}
CX=$((W - $(dp 32))); CY=$(dp 26)
drop() { adb shell input swipe $CX $CY $1 $2 1500; sleep 5; }

# Kotak A dengan Tinta Studio, lalu ColorDrop ke dalamnya.
ax0=$((W*72/100)); ax1=$((W*90/100)); ay0=$((H*45/100)); ay1=$((H*70/100))
kotak $ax0 $ay0 $ax1 $ay1
shot kotak-tinta
drop $(((ax0+ax1)/2)) $(((ay0+ay1)/2))
shot colordrop-kotak-tinta

# Tanpa menekan "Selesai": langsung ganti kuas ke Pensil 6B (isian harus tersimpan otomatis).
kuas
shot pustaka-setelah-colordrop
tap_node "Sketsa" 2
tap_node "Pensil 6B" 2
adb shell input tap $((W*50/100)) $((H*93/100)); sleep 1

# Kotak B dengan pensil bertekstur, lalu ColorDrop.
bx0=$((W*10/100)); bx1=$((W*21/100)); by0=$((H*45/100)); by1=$((H*75/100))
kotak $bx0 $by0 $bx1 $by1
shot kotak-pensil
drop $(((bx0+bx1)/2)) $(((by0+by1)/2))
shot colordrop-kotak-pensil

# Menggores lagi langsung sesudah ColorDrop.
adb shell input swipe $((W*30/100)) $((H*86/100)) $((W*65/100)) $((H*88/100)) 700
sleep 3
shot gores-setelah-colordrop


# Pindah bersama: pilih dua layer di panel, transformasi, geser ke kanan, selesai.
shot sebelum-pindah-bersama
tap_node "Layer" 3 || true
tap_node "Pilih" 2 || true
tap_node "Layer 1" 2 || true
shot pilih-untuk-pindah
tap_node "Pindah bersama" 4 || true
shot transform-bersama
adb shell input swipe $((W*50/100)) $((H*55/100)) $((W*62/100)) $((H*60/100)) 1200
sleep 3
tap_node "Selesai" 4 || true
shot hasil-pindah-bersama

# Gambar kini masuk ke dalam buku: buka beranda WInote.
adb shell am start -n $PKG/id.hananote.MainActivity
sleep 8
shot beranda-winote
tap_node "Gambar" 4 || true
shot buku-gambar

adb shell run-as $PKG cat files/laporan.txt > $OUT/laporan.txt 2>&1
adb logcat -d > $OUT/logcat.txt
echo selesai
