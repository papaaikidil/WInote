#!/usr/bin/env bash
# Uji garis tepi menyatu dengan warna garis tepi dan warna isi yang diatur sendiri-sendiri.
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
ketik() { local s="$1"; for ((i=0; i<${#s}; i++)); do adb shell input text "${s:$i:1}"; sleep 0.4; done; }
adb shell am start -W -n $PKG/id.goresan.DrawActivity --ez uji_buat true --es uji_warna 1E88E5 >/dev/null 2>&1
sleep 20
shot awal
cari() { if tap_node "Cari kuas atau daftar…" 2; then ketik "$1"; sleep 3; adb shell input keyevent KEYCODE_ENTER; sleep 2; fi; }
hapus_cari() { tap_node "Hapus" 2 || true; }
# Ketuk simpul; bila belum terlihat, gulir isi Studio ke atas lalu coba lagi.
ketuk_gulir() {
  for k in 1 2 3 4; do
    tap_node "$1" ${2:-2} && return 0
    adb shell input swipe $((W - $(dp 120))) $((H * 70 / 100)) $((W - $(dp 120))) $((H * 45 / 100)) 500; sleep 1.5
  done
  return 1
}
# Gulir isi Studio kembali ke atas.
isi_atas() { for k in 1 2 3; do adb shell input swipe $((W - $(dp 120))) $((H * 30 / 100)) $((W - $(dp 120))) $((H * 75 / 100)) 400; sleep 1; done; }
cari_kuas() {
  tap_node "Kuas" 3 || true
  hapus_cari
  cari "$1"
  tap_node "$2" 3 || echo "KUAS TIDAK KETEMU: $2"
}
huruf() { # baris y0 y1 (persen): X, T, gelombang memotong garis
  gerak garis 6 $1 22 $2 0; gerak garis 22 $1 6 $2 0
  gerak garis 28 $1 46 $1 0; gerak garis 37 $1 37 $2 0
  gerak garis 52 $(( ($1 + $2) / 2 )) 92 $(( ($1 + $2) / 2 )) 0
  gerak garis 52 $(( $1 + 2 )) 92 $(( $2 - 2 )) 12
}
cari_kuas gelembung "Huruf Gelembung"
shot pustaka
tap_node "Kuas" 2 || true
huruf 10 34
shot bawaan
# Garis tepi merah, isi kuning.
cari_kuas gelembung "Huruf Gelembung"
tap_node "Huruf Gelembung" 3 || true
shot studio-garis-tepi
tap_node "Warna garis tepi: Warna sendiri" 2 || echo "WARNA GARIS TEPI TIDAK ADA"
ketuk_gulir "Warna garis tepi Merah" || echo "MERAH TIDAK ADA"
ketuk_gulir "Warna isi Kuning" || echo "KUNING TIDAK ADA"
shot merah-kuning-atur
tap_node "Kembali" 2 || true
shot pustaka-pratinjau
tap_node "Kuas" 2 || true
huruf 42 66
shot merah-kuning
# Isi ikut warna aktif (biru), garis tepi tetap merah.
cari_kuas gelembung "Huruf Gelembung"
tap_node "Huruf Gelembung" 3 || true
isi_atas
ketuk_gulir "Warna isi: Ikut warna aktif" || echo "ISI AKTIF TIDAK ADA"
shot isi-aktif-atur
tap_node "Kembali" 2 || true
tap_node "Kuas" 2 || true
huruf 72 94
shot merah-isi-biru
sleep 2
adb shell run-as $PKG cat files/kuas.json > $OUT/kuas.json 2>&1
adb shell run-as $PKG cat files/laporan.txt > $OUT/laporan.txt 2>&1
adb logcat -d | grep -iE "FATAL|AndroidRuntime: [^>]|glsl|shader|compile" | tail -40 > $OUT/logcat.txt
echo selesai
