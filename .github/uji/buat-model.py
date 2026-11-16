#!/usr/bin/env python3
"""Berkas uji model 3D: kubus GLB bertekstur, bola OBJ ber-UV (+ MTL + PNG), kubus OBJ tanpa UV."""
import json, math, struct, sys, zlib, os

def png(w, h, px):
    raw = b''.join(b'\0' + bytes(px[y * w * 3:(y + 1) * w * 3]) for y in range(h))
    def chunk(t, d):
        c = struct.pack('>I', len(d)) + t + d
        return c + struct.pack('>I', zlib.crc32(t + d) & 0xffffffff)
    return b'\x89PNG\r\n\x1a\n' + chunk(b'IHDR', struct.pack('>IIBBBBB', w, h, 8, 2, 0, 0, 0)) + chunk(b'IDAT', zlib.compress(raw, 9)) + chunk(b'IEND', b'')

def tex(w=256, h=256):
    px = []
    for y in range(h):
        for x in range(w):
            q = (x * 4 // w) + (y * 4 // h)
            c = [(240, 200, 120), (120, 180, 230)][q % 2]
            if x < 6 or y < 6: c = (40, 40, 40)
            px += c
    return png(w, h, px)

out = sys.argv[1] if len(sys.argv) > 1 else '.'
# --- kubus GLB: 6 sisi, tiap sisi UV penuh ---
faces = [((1,0,0),(0,0,-1),(0,1,0)), ((-1,0,0),(0,0,1),(0,1,0)), ((0,1,0),(1,0,0),(0,0,-1)),
         ((0,-1,0),(1,0,0),(0,0,1)), ((0,0,1),(1,0,0),(0,1,0)), ((0,0,-1),(-1,0,0),(0,1,0))]
pos, nrm, uv, idx = [], [], [], []
for n, u, v in faces:
    b = len(pos) // 3
    for su, sv in ((-1,-1),(1,-1),(1,1),(-1,1)):
        pos += [n[i] + su * u[i] + sv * v[i] for i in range(3)]
        nrm += list(n)
        uv += [(su + 1) / 2, 1 - (sv + 1) / 2]  # glTF: v ke bawah
    idx += [b, b + 1, b + 2, b, b + 2, b + 3]
img = tex()
bin_ = struct.pack('<%df' % len(pos), *pos) + struct.pack('<%df' % len(nrm), *nrm) + struct.pack('<%df' % len(uv), *uv) + struct.pack('<%dH' % len(idx), *idx)
while len(bin_) % 4: bin_ += b'\0'
io = len(bin_); bin_ += img
while len(bin_) % 4: bin_ += b'\0'
nv = len(pos) // 3
L = [nv * 12, nv * 12, nv * 8, len(idx) * 2]
O = [0, L[0], L[0] + L[1], L[0] + L[1] + L[2]]
j = {"asset": {"version": "2.0"}, "scene": 0, "scenes": [{"nodes": [0]}],
     "nodes": [{"mesh": 0, "rotation": [0, 0.3826834, 0, 0.9238795]}],
     "meshes": [{"primitives": [{"attributes": {"POSITION": 0, "NORMAL": 1, "TEXCOORD_0": 2}, "indices": 3, "material": 0}]}],
     "materials": [{"pbrMetallicRoughness": {"baseColorTexture": {"index": 0}}}],
     "textures": [{"source": 0}], "images": [{"bufferView": 4, "mimeType": "image/png"}],
     "bufferViews": [{"buffer": 0, "byteOffset": O[i], "byteLength": L[i]} for i in range(4)] + [{"buffer": 0, "byteOffset": io, "byteLength": len(img)}],
     "accessors": [{"bufferView": 0, "componentType": 5126, "count": nv, "type": "VEC3", "min": [-1, -1, -1], "max": [1, 1, 1]},
                   {"bufferView": 1, "componentType": 5126, "count": nv, "type": "VEC3"},
                   {"bufferView": 2, "componentType": 5126, "count": nv, "type": "VEC2"},
                   {"bufferView": 3, "componentType": 5123, "count": len(idx), "type": "SCALAR"}],
     "buffers": [{"byteLength": len(bin_)}]}
js = json.dumps(j).encode()
while len(js) % 4: js += b' '
glb = struct.pack('<III', 0x46546C67, 2, 12 + 8 + len(js) + 8 + len(bin_)) + struct.pack('<II', len(js), 0x4E4F534A) + js + struct.pack('<II', len(bin_), 0x004E4942) + bin_
open(os.path.join(out, 'kubus.glb'), 'wb').write(glb)
# --- bola OBJ ber-UV + MTL + tekstur ---
lines = ['mtllib bola.mtl', 'o bola']
S, R = 32, 16
for r in range(R + 1):
    th = math.pi * r / R
    for s in range(S + 1):
        ph = 2 * math.pi * s / S
        x, y, z = math.sin(th) * math.cos(ph), math.cos(th), math.sin(th) * math.sin(ph)
        lines.append('v %.5f %.5f %.5f' % (x, y, z))
        lines.append('vt %.5f %.5f' % (s / S, 1 - r / R))
        lines.append('vn %.5f %.5f %.5f' % (x, y, z))
lines.append('usemtl kulit')
for r in range(R):
    for s in range(S):
        a = r * (S + 1) + s + 1; b = a + S + 1
        lines.append('f %d/%d/%d %d/%d/%d %d/%d/%d %d/%d/%d' % (a, a, a, b, b, b, b + 1, b + 1, b + 1, a + 1, a + 1, a + 1))
open(os.path.join(out, 'bola.obj'), 'w').write('\n'.join(lines) + '\n')
open(os.path.join(out, 'bola.mtl'), 'w').write('newmtl kulit\nKd 1 1 1\nmap_Kd tekstur-bola.png\n')
open(os.path.join(out, 'tekstur-bola.png'), 'wb').write(tex(512, 256))
# --- kubus OBJ tanpa UV dan tanpa normal ---
v = [(-1,-1,-1),(1,-1,-1),(1,1,-1),(-1,1,-1),(-1,-1,1),(1,-1,1),(1,1,1),(-1,1,1)]
f = [(1,2,3,4),(5,8,7,6),(1,5,6,2),(2,6,7,3),(3,7,8,4),(5,1,4,8)]
open(os.path.join(out, 'tanpa-uv.obj'), 'w').write(''.join('v %d %d %d\n' % p for p in v) + ''.join('f %d %d %d %d\n' % q for q in f))
print('selesai')
