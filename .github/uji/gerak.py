#!/usr/bin/env python3
"""
Membuat skrip sendevent (multi-sentuh protokol B) untuk emulator: goresan jari bergetar, ketukan/tahan 2-3 jari.
Pemakaian: gerak.py <perangkat> <maxX> <maxY> <W> <H> <dens> <perintah> [argumen...]
  garis x0 y0 x1 y1 getar   — satu jari dari (x0,y0) ke (x1,y1) (persen layar), getaran tangan `getar` dp
  ketuk n                   — ketukan n jari di tengah kanvas
  tahan n ms                — n jari ditahan diam selama ms
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
