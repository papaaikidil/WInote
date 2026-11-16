#!/usr/bin/env bash
# Uji impor kuas Photoshop lengkap: stempel coretan, kuas bertekstur pola + dinamika, kuas bulat bawaan, dan berkas pola .pat.
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
python3 .github/uji/buat-abr.py uji.abr uji.pat
adb push uji.abr /data/local/tmp/uji.abr
adb push uji.pat /data/local/tmp/uji.pat
adb shell am start -n $PKG/id.goresan.DrawActivity --ez uji_buat true
sleep 15
adb shell "cat /data/local/tmp/uji.abr | run-as $PKG sh -c 'cat > files/Kuas_Uji.abr'"
adb shell "cat /data/local/tmp/uji.pat | run-as $PKG sh -c 'cat > files/Pola_Uji.pat'"
adb shell am force-stop $PKG
adb shell am start -n $PKG/id.goresan.DrawActivity --ez uji_buat true --es uji_kuas Kuas_Uji.abr
sleep 25
shot sesudah-impor
pilih() { tap_node "Kuas" 3 || true; tap_node "$1" 3 || true; tap_node "Kuas" 3 || true; }
# Garis bawah (stempel): tarik = satu cap utuh.
adb shell input swipe $((W*15/100)) $((H*20/100)) $((W*85/100)) $((H*20/100)) 900; sleep 3
shot garis-bawah
# Coretan spiral seperti kuas tinta: tarik = satu coretan utuh, bukan coretan berulang.
pilih "Coretan Spiral Uji"
adb shell input swipe $((W*20/100)) $((H*40/100)) $((W*70/100)) $((H*40/100)) 900; sleep 3
shot coretan-tarik
adb shell input tap $((W*82/100)) $((H*40/100)); sleep 3
shot coretan-ketuk
tap_node "Kuas" 3 || true
shot pustaka-kuas
tap_node "Bulat Bertekstur Uji" 3 || true
tap_node "Kuas" 3 || true
adb shell input swipe $((W*15/100)) $((H*60/100)) $((W*85/100)) $((H*64/100)) 1200; sleep 3
# Bolak-balik dalam satu goresan: tekstur tetap tampak (tidak tertutup cap yang menumpuk).
adb shell input swipe $((W*15/100)) $((H*56/100)) $((W*85/100)) $((H*56/100)) 400; sleep 3
shot bertekstur
pilih "Bulat Keras Uji"
adb shell input swipe $((W*15/100)) $((H*72/100)) $((W*85/100)) $((H*74/100)) 1200; sleep 3
shot bulat-keras
# Studio kuas bertekstur: pengaturan hasil impor.
tap_node "Kuas" 3 || true
tap_node "Bulat Bertekstur Uji" 3 || true
tap_node "Bulat Bertekstur Uji" 4 || true
shot studio
adb shell input keyevent KEYCODE_BACK; sleep 2
adb shell input keyevent KEYCODE_BACK; sleep 2
# Pola .pat: tiap pola jadi kuas pola.
adb shell am force-stop $PKG
adb shell am start -n $PKG/id.goresan.DrawActivity --ez uji_buat true --es uji_kuas Pola_Uji.pat
sleep 25
shot sesudah-pola
adb shell input swipe $((W*15/100)) $((H*30/100)) $((W*85/100)) $((H*34/100)) 1500; sleep 3
adb shell input swipe $((W*15/100)) $((H*38/100)) $((W*85/100)) $((H*42/100)) 1500; sleep 3
shot pola-garis
pilih "Pola Bintik Uji"
adb shell input swipe $((W*15/100)) $((H*58/100)) $((W*85/100)) $((H*62/100)) 1500; sleep 3
adb shell input swipe $((W*15/100)) $((H*66/100)) $((W*85/100)) $((H*70/100)) 1500; sleep 3
shot pola-bintik
tap_node "Kuas" 3 || true
shot pustaka-pola
adb shell run-as $PKG ls -l files/kuas/ > $OUT/berkas.txt 2>&1
adb shell run-as $PKG cat files/kuas.json >> $OUT/berkas.txt 2>&1
adb shell run-as $PKG cat files/laporan.txt > $OUT/laporan.txt 2>&1
adb logcat -d -t 400 | grep -i "goresan\|hananote\|AndroidRuntime" | tail -80 > $OUT/logcat.txt
echo selesai
