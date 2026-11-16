#!/usr/bin/env python3
"""Buat berkas kuas Photoshop .abr (versi 6.2) kecil untuk uji impor, mirip berkas asli:
- stempel garis bawah lebar (2400x400) dan coretan spiral (900x500) seukuran aslinya tanpa dinamika -> kuas stempel;
- ujung bulat lembut kecil dengan dinamika tekanan ukuran/opasitas dan tekstur pola -> kuas bertekstur;
- kuas bulat bawaan Photoshop (computedBrush) dengan kekerasan 100.
Bagian patt berisi satu pola abu-abu 128x128 (garis-garis miring). Argumen kedua (opsional) menulis berkas .pat
berisi pola yang sama dan satu pola RGB."""
import math, struct, sys

def packbits(row):
    out = bytearray(); i = 0; n = len(row)
    while i < n:
        j = i
        while j + 1 < n and row[j + 1] == row[i] and j - i < 127: j += 1
        if j > i:
            out += bytes([257 - (j - i + 1)]) + bytes([row[i]]); i = j + 1; continue
        j = i
        while j + 1 < n and row[j + 1] != row[j] and j - i < 127: j += 1
        out += bytes([j - i]) + row[i:j + 1]; i = j + 1
    return bytes(out)

def raster(w, h, f):
    img = bytearray(w * h)
    for y in range(h):
        for x in range(w):
            img[y * w + x] = max(0, min(255, int(f(x, y))))
    return img

def sample(uuid, w, h, f):
    img = f(w, h) if f is spiral_img else raster(w, h, f)
    rows = [packbits(bytes(img[y * w:(y + 1) * w])) for y in range(h)]
    body = bytearray()
    body += bytes([len(uuid)]) + uuid.encode()
    body += b'\0' * (301 - len(body))
    body += struct.pack('>iiiiHB', 0, 0, h, w, 8, 1)
    body += b''.join(struct.pack('>H', len(r)) for r in rows) + b''.join(rows)
    entry = struct.pack('>I', len(body)) + body
    while len(entry) % 4: entry += b'\0'
    return entry

def wave(x, y):
    # garis bergelombang menipis ke kanan, tebal 60 px di kiri
    t = x / 2400.0
    cy = 200 + 90 * math.sin(t * 7.0)
    half = 32 * (1 - 0.85 * t) + 2
    d = abs(y - cy)
    return 255 * min(1.0, max(0.0, half - d + 0.5))

def spiral_img(w, h):
    # coretan spiral memanjang (seperti kuas tinta coretan): garis 7 px digambar dengan cap bulat di sepanjang kurva
    img = bytearray(w * h)
    for k in range(0, 6000):
        t = k / 6000.0
        a = t * 6 * 2 * math.pi
        r = 30 + 170 * t
        px = 150 + t * 600 + r * 1.2 * math.cos(a)
        py = 250 + r * math.sin(a)
        for yy in range(int(py) - 5, int(py) + 6):
            for xx in range(int(px) - 5, int(px) + 6):
                if 0 <= xx < w and 0 <= yy < h:
                    v = int(255 * min(1.0, max(0.0, 4.0 - math.hypot(xx - px, yy - py))))
                    if v > img[yy * w + xx]: img[yy * w + xx] = v
    return img

def soft(x, y):
    d = math.hypot(x - 100, y - 100) / 100.0
    return 255 * max(0.0, 1 - d) ** 1.5

def stripes(x, y):
    return 255 if ((x + y) // 8) % 2 == 0 else 40

# ---- deskriptor Photoshop ----
def key(k):
    return struct.pack('>I', 0 if len(k) == 4 else len(k)) + k.encode('latin-1')

def uni(s):
    s += '\0'
    return struct.pack('>I', len(s)) + s.encode('utf-16-be')

def T(k, s): return key(k) + b'TEXT' + uni(s)
def U(k, unit, v): return key(k) + b'UntF' + unit.encode() + struct.pack('>d', v)
def B(k, v): return key(k) + b'bool' + bytes([1 if v else 0])
def L(k, v): return key(k) + b'long' + struct.pack('>i', v)
def D(k, v): return key(k) + b'doub' + struct.pack('>d', v)
def obj(cls, items): return uni('') + key(cls) + struct.pack('>I', len(items)) + b''.join(items)
def O(k, cls, items): return key(k) + b'Objc' + obj(cls, items)
def dyn(k, control, jitter): return O(k, 'brVr', [L('bVTy', control), L('fStp', 25), U('jitter', '#Prc', jitter)])

def sampled(name, uuid, dmtr, spcn, extra=()):
    brsh = O('Brsh', 'sampledBrush', [U('Dmtr', '#Pxl', dmtr), U('Angl', '#Ang', 0), U('Rndn', '#Prc', 100),
                                      U('Spcn', '#Prc', spcn), B('Intr', True), B('flipX', False), B('flipY', False),
                                      T('sampledData', uuid)])
    return obj('brushPreset', [T('Nm  ', name), brsh] + list(extra))

def computed(name, dmtr, hrdn, spcn):
    brsh = O('Brsh', 'computedBrush', [U('Dmtr', '#Pxl', dmtr), U('Hrdn', '#Prc', hrdn), U('Angl', '#Ang', 0),
                                       U('Rndn', '#Prc', 100), U('Spcn', '#Prc', spcn), B('Intr', True),
                                       B('flipX', False), B('flipY', False)])
    return obj('brushPreset', [T('Nm  ', name), brsh, B('useTipDynamics', False), B('useScatter', False),
                               B('useTexture', False), B('usePaintDynamics', False)])

# ---- pola ----
def pattern(pid, name, w, h, chans, mode):
    """Satu pola: versi, mode, titik, nama, ID, lalu Virtual Memory Array List (saluran mentah)."""
    out = struct.pack('>IIHH', 1, mode, h, w) + uni(name) + bytes([len(pid)]) + pid.encode()
    vm = struct.pack('>iiii', 0, 0, h, w) + struct.pack('>I', len(chans))
    for c in chans:
        body = struct.pack('>I', 8) + struct.pack('>iiii', 0, 0, h, w) + struct.pack('>HB', 8, 0) + bytes(c)
        vm += struct.pack('>II', 1, len(body)) + body
    vm += struct.pack('>I', 0) * 2  # topeng pengguna dan lembar: tidak ada
    out += struct.pack('>II', 3, len(vm)) + vm
    return out

pola = raster(128, 128, stripes)
pat1 = pattern('uji-pola-0001', 'Pola Garis Uji', 128, 128, [pola], 1)

samp = (sample('uji-garis-0001', 2400, 400, wave) + sample('uji-coret-0003', 900, 500, spiral_img) +
        sample('uji-bulat-0002', 200, 200, soft))
tekstur = [B('useTipDynamics', True), U('minimumDiameter', '#Prc', 20), dyn('szVr', 2, 0),
           dyn('angleDynamics', 0, 0), B('useScatter', False),
           B('useTexture', True), O('Txtr', 'Ptrn', [T('Nm  ', 'Pola Garis Uji'), T('Idnt', 'uji-pola-0001')]),
           U('textureScale', '#Prc', 100), U('textureDepth', '#Prc', 90), B('InvT', False),
           B('usePaintDynamics', True), dyn('opVr', 2, 0), dyn('prVr', 0, 0), U('minimumOpacity', '#Prc', 10)]
presets = [
    sampled('Garis Bawah Uji', 'uji-garis-0001', 2400, 25),
    sampled('Coretan Spiral Uji', 'uji-coret-0003', 900, 25),
    sampled('Bulat Bertekstur Uji', 'uji-bulat-0002', 60, 10, tekstur),
    computed('Bulat Keras Uji', 30, 100, 5),
]
desc = struct.pack('>I', 16) + uni('') + key('null') + struct.pack('>I', 1) + key('Brsh') + b'VlLs' + \
    struct.pack('>I', len(presets)) + b''.join(b'Objc' + p for p in presets)
patt = struct.pack('>I', len(pat1)) + pat1
while len(patt) % 4: patt += b'\0'

out = struct.pack('>HH', 6, 2)
out += b'8BIMsamp' + struct.pack('>I', len(samp)) + samp
out += b'8BIMpatt' + struct.pack('>I', len(patt)) + patt
out += b'8BIMdesc' + struct.pack('>I', len(desc)) + desc
open(sys.argv[1], 'wb').write(out)
print('abr', len(out), 'byte')

if len(sys.argv) > 2:
    r = raster(96, 64, lambda x, y: 255 if math.hypot((x % 32) - 16, (y % 32) - 16) < 9 else 0)
    g = raster(96, 64, lambda x, y: 128)
    pat2 = pattern('uji-pola-0002', 'Pola Bintik Uji', 96, 64, [r, g, g], 3)
    pf = b'8BPT' + struct.pack('>HI', 1, 2) + pat1 + pat2
    open(sys.argv[2], 'wb').write(pf)
    print('pat', len(pf), 'byte')
