#!/usr/bin/env bash
# Uji Gabung kuas (Combine Procreate): dua Monoline digabung jadi kuas garis tepi, Studio kuas kedua, pisahkan.
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
adb shell am start -W -n $PKG/id.goresan.DrawActivity --ez uji_buat true --es uji_warna 1F2430 >/dev/null 2>&1
sleep 20
shot awal
cari() { if tap_node "Cari kuas atau daftar…" 2; then ketik "$1"; sleep 3; adb shell input keyevent KEYCODE_ENTER; sleep 2; fi; }
# Duplikat Monoline dua kali (seperti di video).
tap_node "Kuas" 3 || true
cari monoline
tap_node "Monoline" 3 || echo "MONOLINE TIDAK KETEMU"
tap_node "Duplikat kuas ini" 3 || true
tap_node "Kembali" 2 || true
tap_node "Duplikat kuas ini" 3 || true
tap_node "Kembali" 2 || true
shot duplikat
# Gabung kuas: pilih dua salinan.
tap_node "Gabung kuas" 2 || echo "TOMBOL GABUNG TIDAK ADA"
shot mode-gabung
cari salinan
tap_node "Monoline salinan" 2 || true
shot pilih-satu
# Kuas ganda bawaan ditolak dengan pesan.
cari kapur
tap_node "Kapur Ganda" 1 || true
shot tolak-kuas-ganda
sleep 2
cari salinan
tap_node "Monoline salinan salinan" 2 || true
shot pilih-dua
tap_node "Gabungkan" 4 || echo "GABUNGKAN GAGAL"
shot studio-gabungan
# Cara gabung: Kurangi.
tap_node "Irisan (kali)" 2 || true
shot pilihan-cara
tap_node "Kurangi" 2 || true
# Kuas kedua: ukuran dibanding kuas utama ~×0.68.
tap_node "Atur kuas kedua" 2 || true
tap_node "Properti" 2 || true
shot kedua-properti
slide "Ukuran dibanding kuas utama" 0.15 396 2
shot kedua-ukuran
# Kuas utama: ukuran maksimum diperbesar supaya garis tepinya jelas.
tap_node "Kuas utama" 2 || true
tap_node "Properti" 2 || true
slide "Ukuran maksimum" 0.45 396 2
shot utama-properti
tap_node "Kuas ganda" 2 || true
shot utama-kuas-ganda
tap_node "Kembali" 2 || true
shot pustaka-gabungan
tap_node "Kuas" 2 || true
# Satu goresan berbelit (garis bersilangan dalam satu goresan menyatu) dan dua goresan terpisah yang bersilangan.
gerak jalur 8 30 18 18 28 30 18 42 8 30 30 30 40 18 50 42 60 30
gerak garis 66 18 90 42 0
gerak garis 90 18 66 42 0
shot garis-tepi
# Tombol "Jadikan garis tepi" dan Selisih.
tap_node "Kuas" 3 || true
tap_node "Monoline salinan" 3 || true
tap_node "Monoline salinan" 3 || true
tap_node "Kuas ganda" 2 || true
tap_node "Kurangi" 2 || true
tap_node "Selisih" 2 || true
tap_node "Jadikan garis tepi" 2 || true
shot jadikan-garis-tepi
tap_node "Kembali" 2 || true
tap_node "Kuas" 2 || true
gerak jalur 10 62 25 52 40 72 55 52 70 72 85 52
gerak jalur 12 85 30 76 50 92 70 76 88 88
shot garis-tepi-2
sleep 2
adb shell run-as $PKG cat files/kuas.json > $OUT/kuas.json 2>&1
grep -o '"dual":{[^}]*' $OUT/kuas.json | head -3
# Mulai ulang aplikasi: kuas gabungan tetap ada.
adb shell am force-stop $PKG
adb shell am start -W -n $PKG/id.goresan.DrawActivity --ez uji_buat true --es uji_warna 1F2430 >/dev/null 2>&1
sleep 20
tap_node "Kuas" 3 || true
cari salinan
shot pustaka-setelah-mulai-ulang
# Pisahkan kuas lewat tahan lama.
b=$(find_node "Monoline salinan")
if [ -n "$b" ]; then set -- $b; adb shell input swipe $1 $2 $1 $2 900; sleep 2; fi
shot menu-tahan
tap_node "Pisahkan kuas" 3 || echo "PISAHKAN TIDAK ADA"
shot setelah-pisah
adb shell run-as $PKG cat files/laporan.txt > $OUT/laporan.txt 2>&1
adb logcat -d | grep -iE "FATAL|AndroidRuntime" | tail -40 > $OUT/logcat.txt
echo selesai
