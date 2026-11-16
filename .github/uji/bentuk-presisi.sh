#!/usr/bin/env bash
# Uji menu Penyesuaian di emulator: siapkan gambar berwarna, lalu coba setiap penyesuaian (tangkapan layar tiap langkah).
# Uji lama (bentuk otomatis, grup layer, ColorDrop, dll.) ada di gambar-lama.sh.
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
    if (t and t.group(1) == want) or (d and d.group(1) == want):
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

# sendevent butuh akses root (citra emulator userdebug).
adb root; sleep 4; adb wait-for-device; sleep 2
adb shell am start -n $PKG/id.goresan.DrawActivity --ez uji_buat true
sleep 20
shot kanvas
# Cari perangkat layar sentuh multi-jari untuk sendevent (dua jari sekaligus).
adb shell getevent -pl > $OUT/getevent.txt 2>&1
read DEV MAXX MAXY <<<"$(python3 - $OUT/getevent.txt <<'PY'
import re, sys
txt = open(sys.argv[1], errors='ignore').read()
found = []
for blk in re.split(r'add device \d+: ', txt)[1:]:
    path = blk.split('\n', 1)[0].strip()
    name = re.search(r'name:\s*"([^"]*)"', blk)
    mx = re.search(r'ABS_MT_POSITION_X\s*:.*?max (\d+)', blk)
    my = re.search(r'ABS_MT_POSITION_Y\s*:.*?max (\d+)', blk)
    if mx and my:
        found.append((name.group(1) if name else '', path, mx.group(1), my.group(1)))
# Layar utama emulator = virtio_input_multi_touch_1 (atau yang pertama bila tidak ada).
pick = next((f for f in found if f[0].endswith('_1')), found[0] if found else None)
if pick: print(pick[1], pick[2], pick[3])
PY
)"
echo "perangkat $DEV $MAXX $MAXY"
# Tulis skrip sendevent: jari 1 menggambar bentuk lalu diam, jari 2 menyentuh (bentuk jadi presisi), lalu dilepas.
mt() {  # $1 = berkas titik (x y per baris, piksel layar), $2 = nama skrip
  python3 - "$1" "$DEV" "$MAXX" "$MAXY" "$W" "$H" <<'PY' > "$OUT/$2"
import sys
pts, dev, mx, my, W, H = sys.argv[1], sys.argv[2], int(sys.argv[3]), int(sys.argv[4]), int(sys.argv[5]), int(sys.argv[6])
P = [tuple(map(int, l.split())) for l in open(pts) if l.strip()]
def X(x): return int(x * mx / (W - 1))
def Y(y): return int(y * my / (H - 1))
out = []
def ev(t, c, v): out.append(f"sendevent {dev} {t} {c} {v}")
def syn(): ev(0, 0, 0)
x, y = P[0]
ev(3, 47, 0); ev(3, 57, 1); ev(3, 53, X(x)); ev(3, 54, Y(y)); ev(1, 330, 1); syn()
for (x, y) in P[1:]:
    ev(3, 47, 0); ev(3, 53, X(x)); ev(3, 54, Y(y)); syn()
out.append("sleep 1.3")
fx, fy = int(W * 0.85), int(H * 0.2)
ev(3, 47, 1); ev(3, 57, 2); ev(3, 53, X(fx)); ev(3, 54, Y(fy)); syn()
out.append("sleep 2.5")
ev(3, 47, 1); ev(3, 57, 4294967295); syn()
ev(3, 47, 0); ev(3, 57, 4294967295); ev(1, 330, 0); syn()
print("\n".join(out))
PY
  adb push "$OUT/$2" /data/local/tmp/$2 >/dev/null
}
# Oval bergelombang (lebih lebar dari tingginya).
python3 - $((W*30/100)) $((H*45/100)) $((H*20/100)) <<'PY' > /tmp/oval.txt
import math, sys, random
cx, cy, r = map(float, sys.argv[1:4]); random.seed(3)
for k in range(0, 29):
    t = -math.pi / 2 + k * 2 * math.pi / 26
    w = r * (1 + 0.05 * math.sin(3 * t) + random.uniform(-0.02, 0.02))
    print(int(cx + 1.4 * w * math.cos(t)), int(cy + w * math.sin(t)))
PY
mt /tmp/oval.txt oval.sh
adb shell sh /data/local/tmp/oval.sh &
MTP=$!
sleep 9
shot oval-plus-jari
wait $MTP
sleep 2
shot lingkaran-sempurna
# Segitiga miring bergelombang.
python3 - $((W*62/100)) $((H*25/100)) <<'PY' > /tmp/tri.txt
import sys
ax, ay = map(int, sys.argv[1:3])
V = [(ax, ay), (ax + 330, ay + 420), (ax - 260, ay + 470), (ax, ay)]
for i in range(3):
    (x0, y0), (x1, y1) = V[i], V[i + 1]
    for k in range(8):
        t = k / 8
        print(int(x0 + (x1 - x0) * t + (k % 2) * 7), int(y0 + (y1 - y0) * t))
print(ax + 2, ay + 3)
PY
mt /tmp/tri.txt tri.sh
adb shell sh /data/local/tmp/tri.sh &
MTP=$!
sleep 9
shot segitiga-plus-jari
wait $MTP
sleep 2
shot segitiga-sama-sisi
tap_node "Transformasi" 4 || true
shot pegangan-baru
tap_node "Batal" 3 || true
adb shell run-as $PKG cat files/laporan.txt > $OUT/laporan.txt 2>&1
adb logcat -d | grep -i "goresan\|hananote" | tail -50 > $OUT/logcat.txt
echo selesai
