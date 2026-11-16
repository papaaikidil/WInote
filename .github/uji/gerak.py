#!/usr/bin/env python3
"""
Membuat skrip sendevent (multi-sentuh protokol B) untuk emulator: goresan jari bergetar, ketukan/tahan 2-3 jari.
Pemakaian: gerak.py <perangkat> <maxX> <maxY> <W> <H> <dens> <perintah> [argumen...]
  garis x0 y0 x1 y1 getar   — satu jari dari (x0,y0) ke (x1,y1) (persen layar), getaran tangan `getar` dp
  ketuk n                   — ketukan n jari di tengah kanvas
  geser3 persen             — tiga jari bergeser ke bawah (menu Salin & Tempel)
  tahan n ms                — n jari ditahan diam selama ms
  seret x0 y0 x1 y1 ms      — satu jari (piksel) ditahan ms lalu digeser (SwatchDrop)
  cubit x1 y1 x2 y2 sisa    — dua jari (piksel) saling mendekat sampai jaraknya tinggal sisa %
  ketuk2xy x y              — ketukan dua jari di (x, y) piksel
  geser2xy x y dx           — dua jari di (x, y) piksel bergeser ke kanan sejauh dx piksel
  gosok3                    — tiga jari menggosok kiri-kanan (bersihkan layer)
Hasilnya: folder g/ berisi bingkai biner struct input_event (satu berkas per bingkai, ditulis sekaligus dengan cat
supaya jari-jari mendarat hampir bersamaan) dan run.sh untuk dijalankan di perangkat.
"""
import os
import struct
import math
import random
import sys

dev, maxx, maxy, W, H, dens = sys.argv[1], int(sys.argv[2]), int(sys.argv[3]), int(sys.argv[4]), int(sys.argv[5]), int(sys.argv[6])
cmd = sys.argv[7]
a = sys.argv[8:]
out = []
frame = []
OUTDIR = "g"


def ev(t, c, v):
    frame.append(struct.pack("<qqHHi", 0, 0, t, c, v))


def sx(px):
    return int(px * maxx / max(1, W - 1))


def sy(py):
    return int(py * maxy / max(1, H - 1))


def syn():
    ev(0, 0, 0)
    n = len([x for x in out if x.startswith("cat")])
    name = f"{n:04d}.bin"
    with open(os.path.join(OUTDIR, name), "wb") as f:
        f.write(b"".join(frame))
    frame.clear()
    out.append(f"cat /data/local/tmp/g/{name} > {dev}")


def down(slot, tid, x, y):
    ev(3, 47, slot)
    ev(3, 57, tid)
    ev(3, 53, sx(x))
    ev(3, 54, sy(y))
    # Tanpa tekanan > 0, Android menganggap sentuhan ini "melayang".
    ev(3, 58, 600)


def move(slot, x, y):
    ev(3, 47, slot)
    ev(3, 53, sx(x))
    ev(3, 54, sy(y))


def up(slot):
    ev(3, 47, slot)
    ev(3, 57, -1)


os.makedirs(OUTDIR, exist_ok=True)
for f in os.listdir(OUTDIR):
    os.remove(os.path.join(OUTDIR, f))
dp = dens / 160.0
if cmd == "garis":
    x0, y0, x1, y1, getar = map(float, a[:5])
    X0, Y0, X1, Y1 = W * x0 / 100, H * y0 / 100, W * x1 / 100, H * y1 / 100
    n = 70
    rnd = random.Random(7)
    L = math.hypot(X1 - X0, Y1 - Y0)
    nx, ny = -(Y1 - Y0) / L, (X1 - X0) / L
    pts = []
    for i in range(n + 1):
        t = i / n
        j = getar * dp * (math.sin(t * 2 * math.pi * 9) * 0.8 + rnd.uniform(-0.5, 0.5))
        pts.append((X0 + (X1 - X0) * t + nx * j, Y0 + (Y1 - Y0) * t + ny * j))
    down(0, 500, *pts[0])
    ev(1, 330, 1)
    syn()
    for p in pts[1:]:
        move(0, *p)
        syn()
    if not (len(a) > 5 and a[5] == "tahan"):
        up(0)
        ev(1, 330, 0)
        syn()
elif cmd == "geser3":
    # Tiga jari mendarat hampir bersamaan lalu bergeser ke bawah sejauh a[0] persen tinggi layar.
    dist = H * float(a[0]) / 100
    cx, cy = W * 0.55, H * 0.35
    gap = 80 * dp
    fingers = [(cx + (k - 1) * gap, cy + (k % 2) * 15 * dp) for k in range(3)]
    for k, (x, y) in enumerate(fingers):
        down(k, 700 + k, x, y)
        if k == 0:
            ev(1, 330, 1)
        syn()
    steps = 24
    for i in range(1, steps + 1):
        for k, (x, y) in enumerate(fingers):
            move(k, x, y + dist * i / steps)
        syn()
        out.append("usleep 12000")
    for k in range(3):
        up(k)
        if k == 2:
            ev(1, 330, 0)
        syn()
elif cmd == "seret":
    # Satu jari (piksel): turun di (x0,y0), ditahan `tahan` ms, lalu digeser ke (x1,y1) dan diangkat.
    x0, y0, x1, y1 = map(float, a[:4])
    hold = int(a[4]) if len(a) > 4 else 0
    down(0, 800, x0, y0)
    ev(1, 330, 1)
    syn()
    if hold:
        out.append(f"usleep {hold * 1000}")
    steps = 30
    for i in range(1, steps + 1):
        move(0, x0 + (x1 - x0) * i / steps, y0 + (y1 - y0) * i / steps)
        syn()
        out.append("usleep 12000")
    out.append("usleep 150000")
    up(0)
    ev(1, 330, 0)
    syn()
elif cmd == "cubit":
    # Dua jari (piksel) di (x1,y1) dan (x2,y2) saling mendekat sampai jaraknya tinggal `sisa` persen.
    x1, y1, x2, y2 = map(float, a[:4])
    rest = float(a[4]) / 100 if len(a) > 4 else 0.3
    mx, my = (x1 + x2) / 2, (y1 + y2) / 2
    down(0, 810, x1, y1)
    ev(1, 330, 1)
    syn()
    down(1, 811, x2, y2)
    syn()
    out.append("usleep 60000")
    steps = 24
    for i in range(1, steps + 1):
        f = 1 - (1 - rest) * i / steps
        move(0, mx + (x1 - mx) * f, my + (y1 - my) * f)
        move(1, mx + (x2 - mx) * f, my + (y2 - my) * f)
        syn()
        out.append("usleep 15000")
    up(0)
    syn()
    up(1)
    ev(1, 330, 0)
    syn()
elif cmd == "ketuk2xy":
    # Ketukan dua jari di sekitar (x, y) piksel.
    x, y = map(float, a[:2])
    gap = 30 * dp
    down(0, 820, x - gap, y)
    ev(1, 330, 1)
    syn()
    down(1, 821, x + gap, y)
    syn()
    out.append("usleep 90000")
    up(0)
    syn()
    up(1)
    ev(1, 330, 0)
    syn()
elif cmd == "geser2xy":
    # Dua jari di sekitar (x, y) piksel bergeser ke kanan sejauh dx piksel.
    x, y, dxx = map(float, a[:3])
    gap = 30 * dp
    down(0, 830, x - gap, y)
    ev(1, 330, 1)
    syn()
    down(1, 831, x + gap, y)
    syn()
    out.append("usleep 40000")
    steps = 20
    for i in range(1, steps + 1):
        move(0, x - gap + dxx * i / steps, y)
        move(1, x + gap + dxx * i / steps, y)
        syn()
        out.append("usleep 12000")
    up(0)
    syn()
    up(1)
    ev(1, 330, 0)
    syn()
elif cmd == "gosok3":
    # Tiga jari menggosok kiri-kanan (4 kali bolak-balik) di tengah kanvas.
    cx, cy = W * 0.55, H * 0.5
    gap = 70 * dp
    fingers = [(cx + (k - 1) * gap, cy + (k % 2) * 15 * dp) for k in range(3)]
    for k, (x, y) in enumerate(fingers):
        down(k, 840 + k, x, y)
        if k == 0:
            ev(1, 330, 1)
        syn()
    amp = 80 * dp
    for rep in range(8):
        for i in range(1, 9):
            off = amp * i / 8 if rep % 2 == 0 else amp - amp * i / 8
            for k, (x, y) in enumerate(fingers):
                move(k, x + off, y)
            syn()
            out.append("usleep 12000")
    for k in range(3):
        up(k)
        if k == 2:
            ev(1, 330, 0)
        syn()
elif cmd == "angkat":
    up(0)
    ev(1, 330, 0)
    syn()
else:
    nf = int(a[0])
    hold = int(a[1]) if cmd == "tahan" else 70
    cx, cy = W * 0.6, H * 0.5
    gap = 70 * dp
    fingers = [(cx + (k - (nf - 1) / 2) * gap, cy + (k % 2) * 20 * dp) for k in range(nf)]
    for k, (x, y) in enumerate(fingers):
        down(k, 600 + k, x, y)
        if k == 0:
            ev(1, 330, 1)
        syn()
    out.append(f"usleep {hold * 1000}")
    for k in range(nf):
        up(k)
        if k == nf - 1:
            ev(1, 330, 0)
        syn()
with open(os.path.join(OUTDIR, "run.sh"), "w") as f:
    f.write("\n".join(out) + "\n")
