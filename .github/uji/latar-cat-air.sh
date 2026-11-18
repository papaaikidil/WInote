#!/usr/bin/env bash
# Uji kuas Latar Cat Air (bercak bertepi kembang kol + tekstur awan cat air), seperti latar cat air Procreate.
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
adb shell am start -W -n $PKG/id.goresan.DrawActivity --ez uji_buat true --es uji_warna 1F5FA8 >/dev/null 2>&1
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
cari_kuas latar "Latar Cat Air"
shot pustaka
tap_node "Kuas" 2 || true
# Satu bercak di tengah, seperti awal video.
gerak jalur 40 35 46 45 42 58 50 66 56 55 52 42 46 36
shot bercak-awal
# Diperluas ke seluruh kanvas dengan beberapa sapuan besar.
gerak jalur 12 15 30 22 20 40 35 55 18 75 30 90 50 80
gerak jalur 60 12 80 20 90 40 75 55 92 72 80 90 62 85
gerak jalur 30 10 50 15 70 30 55 50 40 70 60 92
shot latar-penuh
gerak jalur 20 30 40 50 60 40 80 60
shot latar-lapis-dua
# Kuas Bercak Cat Air untuk menambah awan gelap.
cari_kuas bercak "Bercak Cat Air"
tap_node "Kuas" 2 || true
gerak jalur 70 30 76 36 72 44
gerak jalur 25 70 30 78 22 82
shot bercak-tambahan
# Pengaturan: bentuk dan tekstur baru.
cari_kuas latar "Latar Cat Air"
tap_node "Latar Cat Air" 3 || true
tap_node "Bentuk" 2 || true
shot studio-bentuk
tap_node "Tekstur" 2 || true
shot studio-tekstur
tap_node "Kembali" 2 || true
tap_node "Kuas" 2 || true
adb shell run-as $PKG cat files/laporan.txt > $OUT/laporan.txt 2>&1
adb logcat -d | grep -iE "FATAL|AndroidRuntime: [^>]|glsl|shader|compile|ANR" | tail -40 > $OUT/logcat.txt
echo selesai
