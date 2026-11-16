#!/usr/bin/env bash
# Uji 0.1.110: pangkas & ubah ukuran kanvas, info kanvas, jendela referensi.
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
# Cari baris di panel Aksi (gulir panel bila belum tampak).
aksi() {
  local k
  [ -z "$(find_bounds "Preferensi")$(find_bounds "Tambah teks")$(find_bounds "Ganti nama karya")" ] && tap_node "Aksi" 3
  for k in 1 2 3 4; do
    [ -n "$(find_bounds "$1")" ] && break
    adb shell input swipe $(dp 200) $((H * 80 / 100)) $(dp 200) $((H * 40 / 100)) 600; sleep 2
  done
  tap_node "$1" 3
}
kosongkan() { # kolom teks yang sedang aktif
  adb shell input keyevent KEYCODE_MOVE_END
  for k in 1 2 3 4 5 6 7; do adb shell input keyevent KEYCODE_DEL; done
}
for k in $(seq 1 60); do adb shell service check activity 2>/dev/null | grep -q "found" && break; sleep 3; done
for k in 1 2 3; do
  adb shell am start -W -n $PKG/id.goresan.DrawActivity --ez uji_buat true --ei uji_w 1600 --ei uji_h 1200 --es uji_warna 2C6BD8 2>&1 | tee -a $OUT/start.txt | grep -q "Status: ok" && break
  sleep 10
done
sleep 30
depan
gerak garis 20 25 80 30 0
gerak garis 20 50 80 75 0
gerak garis 50 20 50 85 0
shot gambar
# Info kanvas.
aksi "Info kanvas" || true
sleep 3
shot info
tap_node "Tutup" 2 || true
# Jendela referensi (kanvas ini).
aksi "Jendela referensi" || true
sleep 4
shot referensi
gerak garis 30 85 85 85 0
sleep 4
shot referensi-diperbarui
# Pangkas: 1200 x 900 di tengah.
aksi "Pangkas & ubah ukuran" || true
sleep 5
shot pangkas
if tap_node "Lebar (px)" 2; then kosongkan; ketik 1200; fi
adb shell input keyevent KEYCODE_ENTER; sleep 1
if tap_node "Tinggi (px)" 2; then kosongkan; ketik 900; fi
adb shell input keyevent KEYCODE_ENTER; sleep 1
tap_node "Tengah" 2 || true
sleep 1
shot pangkas-bingkai
tap_node "Terapkan" 3 || true
sleep 8
depan
shot hasil-pangkas
# Ubah skala isi 50%.
aksi "Pangkas & ubah ukuran" || true
sleep 5
tap_node "Ubah skala isi" 2 || true
tap_node "50%" 2 || true
sleep 1
shot skala
tap_node "Terapkan" 3 || true
sleep 8
depan
shot hasil-skala
# Urungkan tidak mengembalikan ukuran (riwayat kosong).
gerak ketuk 2
sleep 2
aksi "Info kanvas" || true
sleep 3
shot info-akhir
tap_node "Tutup" 2 || true
depan
shot akhir
adb shell run-as $PKG cat files/laporan.txt > $OUT/laporan.txt 2>&1
adb logcat -d -t 1500 | grep -i "goresan\|hananote\|AndroidRuntime\|Galat" | tail -120 > $OUT/logcat.txt
echo selesai
