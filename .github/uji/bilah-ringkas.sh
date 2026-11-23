#!/usr/bin/env bash
# Uji bilah alat ringkas: pilihan kuas dan pengaturan alat sebagai daftar tegak ke bawah, lalu bilah lengkap.
#
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
python3 -m pip install --quiet --break-system-packages pillow >/dev/null 2>&1 || python3 -m pip install --quiet pillow >/dev/null 2>&1
python3 .github/uji/pdf/buat.py /tmp/surat.pdf
# Folder data aplikasi baru ada setelah dibuka sekali.
adb shell am start -W -n $PKG/id.hananote.MainActivity >/dev/null 2>&1
sleep 15
adb push /tmp/surat.pdf /data/local/tmp/surat.pdf >/dev/null
adb shell "cat /data/local/tmp/surat.pdf | run-as $PKG sh -c 'cat > files/surat.pdf'"
adb shell am force-stop $PKG
for k in 1 2 3; do
  adb shell am start -W -n $PKG/id.hananote.MainActivity -a id.hananote.action.NEW_NOTE --es uji_pdf surat.pdf 2>&1 | tee -a $OUT/start.txt | grep -q "Status: ok" && break
  sleep 10
done
sleep 15
shot pdf-terbuka
# Letak halaman di layar dari tangkapan layar: halaman PDF putih bersih (rentang putih terpanjang di tengah layar).
halaman() {
  adb exec-out screencap -p > /tmp/l.png
  read PX0 PY0 PX1 PY1 < <(python3 - <<'PY'
from PIL import Image
im = Image.open('/tmp/l.png').convert('RGB'); W, H = im.size; px = im.load()
def runs(vals):
    out = []; start = None; last = None
    for i, v in enumerate(vals):
        if v:
            if start is None: start = i
            elif i - last > 60: out.append((start, last)); start = i
            last = i
    if start is not None: out.append((start, last))
    return out
y = H // 2
rx = max(runs([px[x, y] == (255, 255, 255) for x in range(W)]), key=lambda r: r[1] - r[0])
cx = (rx[0] + rx[1]) // 2
ry = max(runs([px[cx, yy] == (255, 255, 255) for yy in range(H)]), key=lambda r: r[1] - r[0])
print(rx[0], ry[0], rx[1], ry[1])
PY
)
  echo "halaman di layar: $PX0 $PY0 $PX1 $PY1" | tee -a $OUT/halaman.txt
}
# Titik PDF (poin dari kiri atas; lebar halaman 595) → layar.
pt() { python3 -c "s=($PX1-$PX0)/595.0; print(int($PX0 + s*$1), int($PY0 + s*$2))"; }
pct() { python3 -c "s=($PX1-$PX0)/595.0; print(round(($PX0 + s*$1)*100.0/$W, 2), round(($PY0 + s*$2)*100.0/$H, 2))"; }
ketuk_pt() { adb shell input tap $(pt $1 $2); sleep ${3:-2}; }
halaman
# Ketuk simpul pertama yang teks/content-desc-nya diawali $1.
tap_awal() {
  adb shell uiautomator dump /sdcard/ui.xml >> "$OUT/dump-log.txt" 2>&1
  adb pull /sdcard/ui.xml "$OUT/ui-last.xml" >/dev/null 2>&1
  local p; p=$(python3 - "$1" "$OUT/ui-last.xml" <<'PY'
import re, sys, html
want, path = sys.argv[1], sys.argv[2]
xml = open(path, encoding='utf-8', errors='ignore').read()
for m in re.finditer(r'<node [^>]*>', xml):
    n = m.group(0)
    t = re.search(r' text="([^"]*)"', n); d = re.search(r'content-desc="([^"]*)"', n)
    if (t and html.unescape(t.group(1)).startswith(want)) or (d and html.unescape(d.group(1)).startswith(want)):
        b = re.search(r'bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', n)
        if b:
            x0, y0, x1, y1 = map(int, b.groups()); print((x0 + x1) // 2, (y0 + y1) // 2); break
PY
)
  if [ -z "$p" ]; then echo "TIDAK KETEMU (awalan): $1"; return 1; fi
  adb shell input tap $p; sleep ${2:-2}
}
tutup_menu() { adb shell input keyevent 4; sleep 1; }

shot bilah-ringkas
# ---------- 1. Pilihan kuas ----------
tap_awal "Pilihan kuas" 2 || echo "PILIHAN KUAS TIDAK ADA"
shot daftar-kuas
tap_node "Stabilo" 2 || echo "STABILO TIDAK ADA"
shot stabilo-dipilih
# ---------- 2. Pengaturan stabilo: warna dan ketebalan tegak ----------
tap_awal "Pengaturan Stabilo" 2 || echo "PENGATURAN STABILO TIDAK ADA"
shot pengaturan-stabilo
tutup_menu
read A B < <(pct 80 330); read C D < <(pct 300 330)
gerak garis $A $B $C $D 0
# ---------- 3. Penghapus ----------
tap_awal "Pilihan kuas" 2 || true
tap_node "Penghapus" 2 || echo "PENGHAPUS TIDAK ADA"
tap_awal "Pengaturan Penghapus" 2 || echo "PENGATURAN PENGHAPUS TIDAK ADA"
shot pengaturan-penghapus
tap_node "Goresan utuh" 2 || echo "GORESAN UTUH TIDAK ADA"
shot penghapus-goresan-utuh
tutup_menu
shot tombol-penghapus
# ---------- 4. Bentuk ----------
tap_awal "Pilihan kuas" 2 || true
tap_node "Bentuk" 2 || echo "BENTUK TIDAK ADA"
tap_awal "Pengaturan Bentuk" 2 || echo "PENGATURAN BENTUK TIDAK ADA"
shot pengaturan-bentuk
tap_node "Bintang" 2 || echo "BINTANG TIDAK ADA"
tutup_menu
read A B < <(pct 340 300); read C D < <(pct 460 420)
gerak garis $A $B $C $D 0
shot bintang
# ---------- 5. Selotip ----------
tap_awal "Pilihan kuas" 2 || true
tap_node "Selotip" 2 || echo "SELOTIP TIDAK ADA"
tap_awal "Pengaturan Selotip" 2 || echo "PENGATURAN SELOTIP TIDAK ADA"
shot pengaturan-selotip
tutup_menu
# ---------- 6. Pena lalu menulis ----------
tap_awal "Pilihan kuas" 2 || true
tap_node "Pena" 2 || echo "PENA TIDAK ADA"
read A B < <(pct 80 380); read C D < <(pct 300 400)
gerak garis $A $B $C $D 0
shot pena-menulis
# ---------- 7. Bilah lengkap lalu ringkas lagi ----------
tap_node "Jajarkan semua alat di bilah" 2 || echo "JAJARKAN TIDAK ADA"
shot bilah-lengkap
tap_node "Ringkas bilah alat" 2 || echo "RINGKAS TIDAK ADA"
shot bilah-ringkas-lagi
adb logcat -d | grep -iE "FATAL|AndroidRuntime" | tail -40 > $OUT/logcat.txt
echo selesai
