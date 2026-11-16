package id.goresan.engine

/**
 * Semua shader mesin kanvas.
 *
 * Konvensi koordinat: kanvas memakai piksel dengan (0,0) di pojok kiri atas gambar. Di dalam tekstur,
 * baris kanvas y disimpan di baris tekstur y, sehingga semua pass antartekstur memakai koordinat kanvas
 * apa adanya. Hanya pass ke layar yang membalik sumbu y.
 * Semua warna di tekstur layer disimpan "premultiplied".
 */
object Shaders {

    /** Persegi panjang di ruang target (piksel kanvas) → NDC. Digambar sebagai triangle strip 4 titik. */
    const val RECT_VS = """#version 300 es
uniform vec4 uRect;
uniform vec2 uTarget;
out vec2 vPos;
void main() {
    vec2 c = vec2((gl_VertexID == 1 || gl_VertexID == 3) ? 1.0 : 0.0, gl_VertexID >= 2 ? 1.0 : 0.0);
    vec2 p = mix(uRect.xy, uRect.zw, c);
    vPos = p;
    gl_Position = vec4(p / uTarget * 2.0 - 1.0, 0.0, 1.0);
}
"""

    const val BG_FS = """#version 300 es
precision highp float;
uniform vec4 uColor;
out vec4 o;
void main() { o = uColor; }
"""

    /** Tekstur kertas kanvas: hasil tumpukan dikali bayangan/pola kertas (ubin berulang). */
    const val PAPER_FS = """#version 300 es
precision highp float;
in vec2 vPos;
uniform sampler2D uBase;
uniform sampler2D uPaper;
uniform vec2 uSize;
uniform float uPaperScale;
uniform float uStrength;
out vec4 o;
void main() {
    vec4 b = texture(uBase, vPos / uSize);
    vec3 p = texture(uPaper, vPos / uPaperScale).rgb;
    o = vec4(b.rgb * mix(vec3(1.0), p, uStrength), b.a);
}
"""

    /** Menumpuk satu layer di atas hasil tumpukan sebelumnya, lengkap dengan mode campur dan clipping mask. */
    const val COMPOSITE_FS = """#version 300 es
precision highp float;
in vec2 vPos;
uniform sampler2D uBase;
uniform sampler2D uLayer;
uniform sampler2D uClip;
uniform vec2 uSize;
uniform float uOpacity;
uniform int uMode;
uniform int uUseClip;
// Transformasi beberapa layer sekaligus: layer (dan dasar clipping-nya) diambil dari posisi asalnya.
uniform mat3 uInv;
uniform int uXf;
uniform int uClipXf;
uniform int uInterp;
// Onion skin: warna layer didorong ke warna ini sebanyak .a (0 = tanpa warna).
uniform vec4 uTint;
out vec4 o;

// Interpolasi saat transformasi: 0 terdekat, 1 bilinear, 2 bikubik (Catmull-Rom). p dalam piksel kanvas.
vec4 cubicW(float t) {
    float t2 = t * t;
    float t3 = t2 * t;
    return vec4(-0.5 * t3 + t2 - 0.5 * t, 1.5 * t3 - 2.5 * t2 + 1.0, -1.5 * t3 + 2.0 * t2 + 0.5 * t, 0.5 * t3 - 0.5 * t2);
}
vec4 interpAt(sampler2D t, vec2 p, vec2 size, int mode) {
    ivec2 mx = ivec2(size) - 1;
    if (mode == 0) return texelFetch(t, clamp(ivec2(floor(p)), ivec2(0), mx), 0);
    if (mode == 1) return texture(t, p / size);
    vec2 q = p - 0.5;
    vec2 f = q - floor(q);
    ivec2 b = ivec2(floor(q));
    vec4 wx = cubicW(f.x);
    vec4 wy = cubicW(f.y);
    vec4 acc = vec4(0.0);
    for (int j = 0; j < 4; j++) {
        vec4 row = vec4(0.0);
        for (int i = 0; i < 4; i++) row += texelFetch(t, clamp(b + ivec2(i - 1, j - 1), ivec2(0), mx), 0) * wx[i];
        acc += row * wy[j];
    }
    acc = clamp(acc, 0.0, 1.0);
    return vec4(min(acc.rgb, vec3(acc.a)), acc.a);
}

vec4 sampleAt(sampler2D t, int xf) {
    if (xf == 0) return texture(t, vPos / uSize);
    vec3 h = uInv * vec3(vPos, 1.0);
    if (h.z <= 1e-6) return vec4(0.0);
    vec2 p = h.xy / h.z;
    if (p.x < 0.0 || p.y < 0.0 || p.x > uSize.x || p.y > uSize.y) return vec4(0.0);
    return interpAt(t, p, uSize, uInterp);
}

float lum(vec3 c) { return dot(c, vec3(0.3, 0.59, 0.11)); }
vec3 clipColor(vec3 c) {
    float l = lum(c);
    float n = min(min(c.r, c.g), c.b);
    float x = max(max(c.r, c.g), c.b);
    if (n < 0.0) c = l + (c - l) * l / max(l - n, 1e-5);
    if (x > 1.0) c = l + (c - l) * (1.0 - l) / max(x - l, 1e-5);
    return c;
}
vec3 setLum(vec3 c, float l) { return clipColor(c + (l - lum(c))); }
float satOf(vec3 c) { return max(max(c.r, c.g), c.b) - min(min(c.r, c.g), c.b); }
vec3 setSat(vec3 c, float s) {
    float mx = max(max(c.r, c.g), c.b);
    float mn = min(min(c.r, c.g), c.b);
    return mx > mn ? (c - mn) * s / (mx - mn) : vec3(0.0);
}
float burn(float b, float s) {
    if (b >= 1.0) return 1.0;
    if (s <= 0.0) return 0.0;
    return 1.0 - min(1.0, (1.0 - b) / s);
}
float dodge(float b, float s) {
    if (b <= 0.0) return 0.0;
    if (s >= 1.0) return 1.0;
    return min(1.0, b / (1.0 - s));
}
float screen1(float b, float s) { return b + s - b * s; }
float hardLight(float b, float s) { return s <= 0.5 ? b * 2.0 * s : screen1(b, 2.0 * s - 1.0); }
float softLight(float b, float s) {
    if (s <= 0.5) return b - (1.0 - 2.0 * s) * b * (1.0 - b);
    float d = b <= 0.25 ? ((16.0 * b - 12.0) * b + 4.0) * b : sqrt(b);
    return b + (2.0 * s - 1.0) * (d - b);
}
float vivid(float b, float s) { return s <= 0.5 ? burn(b, 2.0 * s) : dodge(b, 2.0 * s - 1.0); }
float pinL(float b, float s) { return s <= 0.5 ? min(b, 2.0 * s) : max(b, 2.0 * s - 1.0); }
float ch(int m, float b, float s) {
    if (m == 1) return b * s;
    if (m == 2) return min(b, s);
    if (m == 3) return burn(b, s);
    if (m == 4) return max(b + s - 1.0, 0.0);
    if (m == 6) return max(b, s);
    if (m == 7) return screen1(b, s);
    if (m == 8) return dodge(b, s);
    if (m == 9) return min(b + s, 1.0);
    if (m == 11) return hardLight(s, b);
    if (m == 12) return softLight(b, s);
    if (m == 13) return hardLight(b, s);
    if (m == 14) return vivid(b, s);
    if (m == 15) return clamp(b + 2.0 * s - 1.0, 0.0, 1.0);
    if (m == 16) return pinL(b, s);
    if (m == 17) return (b + s >= 1.0) ? 1.0 : 0.0;
    if (m == 18) return abs(b - s);
    if (m == 19) return b + s - 2.0 * b * s;
    if (m == 20) return max(b - s, 0.0);
    if (m == 21) return s <= 0.0 ? 1.0 : min(b / s, 1.0);
    return s;
}
vec3 blend(int m, vec3 b, vec3 s) {
    if (m == 0) return s;
    if (m == 5) return lum(s) < lum(b) ? s : b;
    if (m == 10) return lum(s) > lum(b) ? s : b;
    if (m == 22) return setLum(setSat(s, satOf(b)), lum(b));
    if (m == 23) return setLum(setSat(b, satOf(s)), lum(b));
    if (m == 24) return setLum(s, lum(b));
    if (m == 25) return setLum(b, lum(s));
    return vec3(ch(m, b.r, s.r), ch(m, b.g, s.g), ch(m, b.b, s.b));
}
void main() {
    vec2 uv = vPos / uSize;
    vec4 base = texture(uBase, uv);
    vec4 lay = sampleAt(uLayer, uXf);
    float a = lay.a * uOpacity;
    if (uUseClip == 1) a *= sampleAt(uClip, uClipXf).a;
    if (a <= 0.0) { o = base; return; }
    vec3 cs = lay.rgb / max(lay.a, 1e-5);
    cs = mix(cs, uTint.rgb, uTint.a);
    float ab = base.a;
    vec3 cb = ab > 0.0 ? base.rgb / ab : vec3(0.0);
    vec3 bl = clamp(blend(uMode, cb, cs), 0.0, 1.0);
    vec3 co = a * (1.0 - ab) * cs + a * ab * bl + (1.0 - a) * base.rgb;
    o = vec4(co, a + ab * (1.0 - a));
}
"""

    /**
     * Pratinjau layer aktif: layer + goresan (cat/hapus), isi warna (ColorDrop/isi layer), atau penyesuaian
     * Hue/Saturasi/Kecerahan. Alpha lock menjaga transparansi asli layer.
     */
    const val PREVIEW_FS = """#version 300 es
precision highp float;
in vec2 vPos;
uniform sampler2D uLayer;
uniform sampler2D uStroke;
uniform sampler2D uPred;
uniform sampler2D uMask;
uniform vec2 uSize;
uniform int uOp;
uniform float uOpacity;
uniform int uAlphaLock;
uniform int uUsePred;
uniform int uUseMask;
uniform vec3 uFill;
uniform vec3 uHsb;
// Clone: selisih posisi sumber terhadap pena (piksel kanvas).
uniform vec2 uClone;
// Pilihan (Selection): perubahan hanya berlaku di dalam masker.
uniform sampler2D uSel;
uniform int uUseSel;
// Tekstur per goresan: dikenakan pada seluruh goresan (bukan per cap), jadi tidak tertutup cap yang menumpuk.
uniform sampler2D uGrain;
uniform float uGrainScale;
uniform float uGrainStrength;
// Cat air: sapuan diolah saat digabung. uWetP = (tepi gelap, bercak, granulasi, tepi lembut).
uniform int uWet;
uniform vec4 uWetP;
uniform float uWetDens;
uniform vec2 uWetSeed;
uniform float uWetR;
uniform float uWetK;
uniform int uGlaze;
uniform sampler2D uNoise;
uniform sampler2D uPaper;
uniform float uPaperScale;
// Rendering kuas (Procreate › Rendering): uRend 1 = aktif.
uniform int uRend;
uniform float uRendK;
uniform float uThreshold;
uniform float uWetEdge;
uniform float uBurnt;
uniform int uBurntMode;
uniform int uBlendMode;
uniform float uEdgeR;
// Kuas ganda: topeng kuas kedua (alfa) dan cara menggabungnya.
uniform int uDual;
// Cat air hidup: cat basah sesi (rgb = warna tanpa premultiply, a = sebaran cat) ikut digabung dengan goresan.
uniform sampler2D uWetF;
uniform int uUseF;
uniform int uNoStroke;
uniform int uLive;
uniform int uDualMode;
uniform sampler2D uDualTex;
uniform sampler2D uDualPred;
uniform vec3 uDualColor;
out vec4 o;
float lumP(vec3 c) { return dot(c, vec3(0.3, 0.59, 0.11)); }
vec3 clipColorP(vec3 c) {
    float l = lumP(c);
    float n = min(min(c.r, c.g), c.b);
    float x = max(max(c.r, c.g), c.b);
    if (n < 0.0) c = l + (c - l) * l / max(l - n, 1e-5);
    if (x > 1.0) c = l + (c - l) * (1.0 - l) / max(x - l, 1e-5);
    return c;
}
vec3 setLumP(vec3 c, float l) { return clipColorP(c + (l - lumP(c))); }
float satOfP(vec3 c) { return max(max(c.r, c.g), c.b) - min(min(c.r, c.g), c.b); }
vec3 setSatP(vec3 c, float s) {
    float mx = max(max(c.r, c.g), c.b);
    float mn = min(min(c.r, c.g), c.b);
    return mx > mn ? (c - mn) * s / (mx - mn) : vec3(0.0);
}
float burnP(float b, float s) { if (b >= 1.0) return 1.0; if (s <= 0.0) return 0.0; return 1.0 - min(1.0, (1.0 - b) / s); }
float dodgeP(float b, float s) { if (b <= 0.0) return 0.0; if (s >= 1.0) return 1.0; return min(1.0, b / (1.0 - s)); }
float screenP(float b, float s) { return b + s - b * s; }
float hardP(float b, float s) { return s <= 0.5 ? b * 2.0 * s : screenP(b, 2.0 * s - 1.0); }
float softP(float b, float s) {
    if (s <= 0.5) return b - (1.0 - 2.0 * s) * b * (1.0 - b);
    float d = b <= 0.25 ? ((16.0 * b - 12.0) * b + 4.0) * b : sqrt(b);
    return b + (2.0 * s - 1.0) * (d - b);
}
float chP(int m, float b, float s) {
    if (m == 1) return b * s;
    if (m == 2) return min(b, s);
    if (m == 3) return burnP(b, s);
    if (m == 4) return max(b + s - 1.0, 0.0);
    if (m == 6) return max(b, s);
    if (m == 7) return screenP(b, s);
    if (m == 8) return dodgeP(b, s);
    if (m == 9) return min(b + s, 1.0);
    if (m == 11) return hardP(s, b);
    if (m == 12) return softP(b, s);
    if (m == 13) return hardP(b, s);
    if (m == 14) return s <= 0.5 ? burnP(b, 2.0 * s) : dodgeP(b, 2.0 * s - 1.0);
    if (m == 15) return clamp(b + 2.0 * s - 1.0, 0.0, 1.0);
    if (m == 16) return s <= 0.5 ? min(b, 2.0 * s) : max(b, 2.0 * s - 1.0);
    if (m == 17) return (b + s >= 1.0) ? 1.0 : 0.0;
    if (m == 18) return abs(b - s);
    if (m == 19) return b + s - 2.0 * b * s;
    if (m == 20) return max(b - s, 0.0);
    if (m == 21) return s <= 0.0 ? 1.0 : min(b / s, 1.0);
    return s;
}
vec3 blendP(int m, vec3 b, vec3 s) {
    if (m == 0) return s;
    if (m == 5) return lumP(s) < lumP(b) ? s : b;
    if (m == 10) return lumP(s) > lumP(b) ? s : b;
    if (m == 22) return setLumP(setSatP(s, satOfP(b)), lumP(b));
    if (m == 23) return setLumP(setSatP(b, satOfP(s)), lumP(b));
    if (m == 24) return setLumP(s, lumP(b));
    if (m == 25) return setLumP(b, lumP(s));
    return vec3(chP(m, b.r, s.r), chP(m, b.g, s.g), chP(m, b.b, s.b));
}
// Gabungkan alfa goresan [a] dengan topeng kuas kedua [m].
float dualCombine(float a, float m) {
    if (uDualMode == 0) return a * m;
    if (uDualMode == 1) return a + m - a * m;
    if (uDualMode == 2) return a * (1.0 - m);
    if (uDualMode == 3) return abs(a - m);
    if (uDualMode == 4) return min(a, m);
    return max(a, m);
}
float dualMask(vec2 uv) {
    float m = texture(uDualTex, uv).a;
    if (uUsePred == 1) { float q = texture(uDualPred, uv).a; m = m + q * (1.0 - m); }
    return m;
}
float strokeGrain() {
    if (uGrainStrength <= 0.0) return 1.0;
    return mix(1.0, texture(uGrain, vPos / uGrainScale).r, uGrainStrength);
}
float strokeAlpha(vec2 p) {
    vec2 uv = p / uSize;
    float a = uNoStroke == 1 ? 0.0 : texture(uStroke, uv).a;
    if (uUsePred == 1) {
        float q = texture(uPred, uv).a;
        a = a + q * (1.0 - a);
    }
    if (uDual == 1) a = dualCombine(a, dualMask(uv));
    if (uUseF == 1) {
        float f = texture(uWetF, uv).a;
        a = a + f * (1.0 - a);
    }
    return a;
}
float nz(vec2 p, float scale, float k) {
    return texture(uNoise, (p / (scale * uWetK) + uWetSeed * k) / 256.0).r;
}
// Cakupan sapuan cat air: ambang dari kepekatan cap, digoyang noise supaya tepinya tak rata seperti air mengering.
// Tanpa percabangan supaya turunan layar (fwidth) sah: tepi dihaluskan minimal ±1 piksel (tidak bergerigi).
float wetCov(vec2 p) {
    float a = strokeAlpha(p);
    float e = (nz(p, 30.0, 1.0) - 0.5) * 0.85 + (nz(p, 6.0, 1.7) - 0.5) * 0.45;
    // Cat air hidup: ketidakrataan tepi sudah datang dari rembesan, jadi noise di sini lebih tenang (tanpa lubang), dan
    // rembesan yang tipis tetap tampak (ambang lebih rendah).
    float th = 0.35;
    if (uLive == 1) { e *= 0.35; th = 0.14; }
    float ae = a + e * clamp(1.2 - a, 0.0, 1.0);
    float w = max(0.05 + uWetP.w * 0.3, fwidth(ae) * 0.9);
    return smoothstep(th - w, th + w, ae) * step(0.003, a);
}
// Alfa akhir sapuan cat air di titik ini (0..1).
float wetAlpha(bool lift) {
    float cov = wetCov(vPos);
    float avg = 0.0;
    for (int k = 0; k < 8; k++) {
        float ang = float(k) * 0.7853982;
        avg += wetCov(vPos + vec2(cos(ang), sin(ang)) * uWetR);
    }
    avg *= 0.125;
    float rim = clamp((cov - avg) * 2.0, 0.0, 1.0);
    float nl = nz(vPos, 90.0, 0.37);
    float nm = nz(vPos, 26.0, 1.0);
    float valley = 1.0 - texture(uPaper, vPos / uPaperScale).a;
    float mott = (1.0 + uWetP.y * (nl - 0.5) * 1.6) * (1.0 + 0.45 * uWetP.y * (nm - 0.5));
    float dens = uWetDens * mott * (1.0 + uWetP.z * (valley - 0.5) * 1.2);
    if (lift) return clamp(cov * (0.55 + 0.45 * mott * 0.8), 0.0, 1.0);
    // Cat air hidup: bagian hasil rembesan (sebaran menipis) lebih encer daripada inti sapuan.
    if (uLive == 1) dens *= mix(0.35, 1.0, smoothstep(0.14, 0.8, strokeAlpha(vPos)));
    return clamp(cov * (dens + uWetP.x * rim * 0.45), 0.0, 1.0);
}
vec3 rgb2hsv(vec3 c) {
    vec4 K = vec4(0.0, -1.0 / 3.0, 2.0 / 3.0, -1.0);
    vec4 p = mix(vec4(c.bg, K.wz), vec4(c.gb, K.xy), step(c.b, c.g));
    vec4 q = mix(vec4(p.xyw, c.r), vec4(c.r, p.yzx), step(p.x, c.r));
    float d = q.x - min(q.w, q.y);
    float e = 1.0e-10;
    return vec3(abs(q.z + (q.w - q.y) / (6.0 * d + e)), d / (q.x + e), q.x);
}
vec3 hsv2rgb(vec3 c) {
    vec4 K = vec4(1.0, 2.0 / 3.0, 1.0 / 3.0, 3.0);
    vec3 p = abs(fract(c.xxx + K.xyz) * 6.0 - K.www);
    return c.z * mix(K.xxx, clamp(p - K.xxx, 0.0, 1.0), c.y);
}
void main() {
    vec2 uv = vPos / uSize;
    vec4 d = texture(uLayer, uv);
    vec4 r = d;
    if (uOp == 0 || uOp == 1) {
        vec4 s = uNoStroke == 1 ? vec4(0.0) : texture(uStroke, uv);
        if (uUsePred == 1) {
            vec4 p = texture(uPred, uv);
            s = s + p * (1.0 - s.a);
        }
        if (uDual == 1) {
            // Kuas ganda: alfa goresan digabung dengan topeng kuas kedua; bagian yang hanya dari kuas kedua memakai warna kuas.
            vec3 col = s.a > 0.0 ? s.rgb / s.a : uDualColor;
            float A = dualCombine(s.a, dualMask(uv));
            s = vec4(col * A, A);
        }
        if (uUseF == 1) {
            vec4 f = texture(uWetF, uv);
            s = s + vec4(f.rgb * f.a, f.a) * (1.0 - s.a);
        }
        if (uWet == 1) {
            vec3 col = s.a > 0.0 ? s.rgb / s.a : vec3(0.0);
            float A = wetAlpha(uOp == 1);
            s = vec4(col * A, A);
        }
        float rim = 0.0;
        if (uRend == 1) {
            // Rendering kuas: tepi basah (melembut/meresap), intensitas glaze, ambang alfa.
            float a0 = s.a;
            vec3 col = a0 > 0.0 ? s.rgb / a0 : (uDual == 1 ? uDualColor : vec3(0.0));
            float a = a0;
            if (uWetEdge > 0.0 || uBurnt > 0.0) {
                float avg = 0.0;
                vec3 cavg = vec3(0.0);
                for (int k = 0; k < 8; k++) {
                    float ang = float(k) * 0.7853982;
                    vec2 q = vPos + vec2(cos(ang), sin(ang)) * uEdgeR;
                    float qa = strokeAlpha(q);
                    avg += qa;
                    if (a0 <= 0.0 && qa > 0.0) cavg = texture(uStroke, q / uSize).rgb / max(texture(uStroke, q / uSize).a, 1e-4);
                }
                avg *= 0.125;
                if (a0 <= 0.0 && avg > 0.0) col = cavg;
                a = mix(a0, avg, uWetEdge * 0.85);
                rim = clamp((a0 - avg) * 2.5, 0.0, 1.0);
            }
            if (uRendK != 1.0) a = 1.0 - pow(1.0 - clamp(a, 0.0, 1.0), uRendK);
            if (uThreshold > 0.0) a = a >= uThreshold ? 1.0 : 0.0;
            s = vec4(col * a, a);
        }
        s *= uOpacity * strokeGrain();
        if (uOp == 0 && uGlaze == 1) {
            // Lapisan menumpuk (multiply): cat di atas cat jadi lebih pekat, seperti lapisan cat air.
            r = vec4(s.rgb * (1.0 - d.a) + d.rgb * (1.0 - s.a) + s.rgb * d.rgb, s.a + d.a - s.a * d.a);
        } else if (uOp == 0 && uRend == 1 && (uBlendMode != 0 || uBurnt > 0.0)) {
            // Mode campur goresan (dan tepi terbakar di tempat goresan menimpa cat lain).
            float a = s.a;
            vec3 cs = a > 0.0 ? s.rgb / a : vec3(0.0);
            float ab = d.a;
            vec3 cb = ab > 0.0 ? d.rgb / ab : vec3(0.0);
            vec3 bl = clamp(blendP(uBlendMode, cb, cs), 0.0, 1.0);
            if (uBurnt > 0.0 && ab > 0.0) bl = mix(bl, clamp(blendP(uBurntMode, cb, cs), 0.0, 1.0), clamp(uBurnt * (rim * 1.5 + 0.15), 0.0, 1.0));
            r = vec4(a * (1.0 - ab) * cs + a * ab * bl + (1.0 - a) * d.rgb, a + ab * (1.0 - a));
        } else {
            r = (uOp == 0) ? (s + d * (1.0 - s.a)) : (d * (1.0 - s.a));
        }
    } else if (uOp == 2) {
        float m = (uUseMask == 1) ? texture(uMask, uv).r : 1.0;
        vec4 f = vec4(uFill, 1.0) * uOpacity;
        if (m > 0.75) r = f + d * (1.0 - f.a);
        else if (m > 0.25) r = d + f * (1.0 - d.a);
    } else if (uOp == 3) {
        if (d.a > 0.0) {
            vec3 hsv = rgb2hsv(d.rgb / d.a);
            hsv.x = fract(hsv.x + uHsb.x + 1.0);
            hsv.y = clamp(uHsb.y < 0.0 ? hsv.y * (1.0 + uHsb.y) : hsv.y + (1.0 - hsv.y) * uHsb.y, 0.0, 1.0);
            hsv.z = clamp(uHsb.z < 0.0 ? hsv.z * (1.0 + uHsb.z) : hsv.z + (1.0 - hsv.z) * uHsb.z, 0.0, 1.0);
            r = vec4(hsv2rgb(hsv) * d.a, d.a);
        }
    } else if (uOp == 4) {
        // Clone: goresan menyalin isi layer dari titik sumber.
        vec4 s = texture(uStroke, uv);
        if (uUsePred == 1) {
            vec4 p = texture(uPred, uv);
            s = s + p * (1.0 - s.a);
        }
        float m = s.a * uOpacity * strokeGrain();
        vec2 q = vPos + uClone;
        vec4 c = (q.x < 0.0 || q.y < 0.0 || q.x > uSize.x || q.y > uSize.y) ? vec4(0.0) : texture(uLayer, q / uSize);
        r = c * m + d * (1.0 - c.a * m);
    }
    if (uAlphaLock == 1 && uOp != 3) {
        vec3 c = r.a > 0.0 ? r.rgb / r.a : vec3(0.0);
        r = vec4(c * d.a, d.a);
    }
    if (uUseSel == 1) r = mix(d, r, texture(uSel, uv).r);
    o = r;
}
"""

    /** Ujung kuas (dab). Posisi dalam piksel kanvas, dikurangi offset untuk target kecil (pratinjau kuas). */
    const val DAB_VS = """#version 300 es
layout(location = 0) in vec2 aPos;
layout(location = 1) in vec2 aUv;
layout(location = 2) in vec4 aColor;
layout(location = 3) in float aBleed;
uniform vec2 uTarget;
uniform vec2 uOffset;
out vec2 vUv;
out vec4 vColor;
out vec2 vPos;
out float vBleed;
void main() {
    vec2 p = aPos - uOffset;
    gl_Position = vec4(p / uTarget * 2.0 - 1.0, 0.0, 1.0);
    vUv = aUv;
    vColor = aColor;
    vPos = aPos;
    vBleed = aBleed;
}
"""

    const val DAB_FS = """#version 300 es
precision highp float;
in vec2 vUv;
in vec4 vColor;
in vec2 vPos;
in float vBleed;
uniform sampler2D uTip;
uniform sampler2D uGrain;
uniform float uGrainScale;
uniform float uGrainStrength;
// Glaze intens/berat: cap lebih pekat (1 = biasa).
uniform float uBoost;
out vec4 o;
void main() {
    float t = texture(uTip, vUv).r;
    if (uGrainStrength > 0.0) {
        float g = texture(uGrain, vPos / uGrainScale).r;
        t *= mix(1.0, g, uGrainStrength);
    }
    // Bleed: hanya bagian bentuk/tekstur yang lebih pekat dari ambang yang tampak (tekanan ringan / pena miring).
    if (vBleed > 0.0) t = clamp((t - vBleed) / max(1.0 - vBleed, 0.05), 0.0, 1.0);
    float a = t * vColor.a;
    if (uBoost > 1.0) a = 1.0 - pow(1.0 - clamp(a, 0.0, 1.0), uBoost);
    o = vec4(vColor.rgb * a, a);
}
"""

    /** Alat baur (smudge): mencampur isi layer di bawah ujung kuas dengan cat yang "terbawa". */
    const val SMUDGE_FS = """#version 300 es
precision highp float;
in vec2 vUv;
in vec4 vColor;
in vec2 vPos;
uniform sampler2D uTip;
uniform sampler2D uCur;
uniform sampler2D uCarry;
uniform vec2 uRectMin;
uniform vec2 uRectSize;
uniform vec2 uTSize;
uniform float uStrength;
uniform int uAlphaLock;
uniform sampler2D uSel;
uniform int uUseSel;
uniform vec2 uCanvas;
out vec4 o;
void main() {
    vec2 l = vPos - uRectMin;
    vec4 cur = texture(uCur, l / uTSize);
    vec4 car = texture(uCarry, l / uRectSize);
    float m = texture(uTip, vUv).r * uStrength;
    if (uUseSel == 1) m *= texture(uSel, vPos / uCanvas).r;
    vec4 r = mix(cur, car, m);
    if (uAlphaLock == 1) {
        vec3 c = r.a > 0.0 ? r.rgb / r.a : vec3(0.0);
        r = vec4(c * cur.a, cur.a);
    }
    o = r;
}
"""

    /**
     * Wet Mix (cat basah): cat kuas (diencerkan air) bercampur dengan cat yang terbawa dari kanvas (tarikan), lalu
     * ditempelkan ke layer. Cat di bawah kuas bisa dilembutkan (blur). Muatan cat ada di alfa cap (vColor.a).
     */
    const val WETMIX_FS = """#version 300 es
precision highp float;
in vec2 vUv;
in vec4 vColor;
in vec2 vPos;
in float vBleed;
uniform sampler2D uTip;
uniform sampler2D uCur;
uniform sampler2D uCarry;
uniform sampler2D uGrain;
uniform float uGrainScale;
uniform float uGrainStrength;
uniform vec2 uRectMin;
uniform vec2 uRectSize;
uniform vec2 uTSize;
uniform float uPull;
uniform float uDil;
uniform float uAttack;
uniform float uBlurR;
uniform float uGrade;
uniform float uOpacity;
uniform int uAlphaLock;
uniform sampler2D uSel;
uniform int uUseSel;
uniform vec2 uCanvas;
out vec4 o;
void main() {
    vec2 l = vPos - uRectMin;
    vec2 tu = l / uTSize;
    vec4 cur = texture(uCur, tu);
    vec4 base = cur;
    if (uBlurR > 0.0) {
        vec2 px = vec2(uBlurR) / uTSize;
        cur = (cur * 2.0 + texture(uCur, tu + vec2(px.x, 0.0)) + texture(uCur, tu - vec2(px.x, 0.0))
            + texture(uCur, tu + vec2(0.0, px.y)) + texture(uCur, tu - vec2(0.0, px.y))) / 6.0;
    }
    vec4 car = texture(uCarry, l / uRectSize);
    float t = texture(uTip, vUv).r;
    if (uGrainStrength > 0.0) {
        float g = texture(uGrain, vPos / uGrainScale).r;
        if (uGrade > 0.0) { float w = 0.5 * (1.0 - uGrade * 0.9); g = smoothstep(0.5 - w, 0.5 + w, g); }
        t *= mix(1.0, g, uGrainStrength);
    }
    if (vBleed > 0.0) t = clamp((t - vBleed) / max(1.0 - vBleed, 0.05), 0.0, 1.0);
    if (uUseSel == 1) t *= texture(uSel, vPos / uCanvas).r;
    // Cat di kuas: warna kuas (muatan × air) bercampur dengan cat yang terseret dari kanvas.
    float load = clamp(vColor.a, 0.0, 1.0);
    vec4 paint = vec4(vColor.rgb, 1.0) * load * (1.0 - uDil * 0.85);
    vec4 brush = paint * (1.0 - uPull) + car * uPull;
    float k = t * (0.25 + 0.75 * uAttack) * uOpacity;
    vec4 r = brush * k + cur * (1.0 - brush.a * k);
    // Blur tetap berlaku di bawah kuas walau catnya habis.
    r = mix(base, r, clamp(t * 4.0, 0.0, 1.0));
    if (uAlphaLock == 1) {
        vec3 c = r.a > 0.0 ? r.rgb / r.a : vec3(0.0);
        r = vec4(c * base.a, base.a);
    }
    o = r;
}
"""

    /**
     * Cat air hidup, langkah rembesan: cat yang masih basah merambat ke piksel tetangga (paling jauh 1 piksel per
     * langkah), menipis sepanjang jalan, dan terhambat serat/lekukan kertas sehingga tepinya tak rata. Makin kering,
     * makin jarang merambat. Warna cat yang sama-sama basah saling bercampur.
     * uF: rgb = warna (tanpa premultiply), a = sebaran cat. uW: r = sisa basah.
     */
    const val WET_SPREAD_FS = """#version 300 es
precision highp float;
in vec2 vPos;
uniform sampler2D uF;
uniform sampler2D uW;
uniform sampler2D uNoise;
uniform sampler2D uPaper;
uniform float uPaperScale;
uniform vec2 uSize;
uniform float uLoss;
uniform float uNoiseK;
uniform vec2 uSeed;
uniform vec2 uJit;
uniform float uMix;
uniform vec4 uBound;
out vec4 o;
float best;
float edgeK;
// Acak tanpa sin (stabil di GPU ponsel): hash12 Dave Hoskins.
float hash12(vec2 p) {
    vec3 p3 = fract(vec3(p.xyx) * 0.1031);
    p3 += dot(p3, p3.yzx + 33.33);
    return fract((p3.x + p3.y) * p3.z);
}
float ws;
vec3 cs;
float res;
void nb(vec2 off, float len, float salt) {
    vec2 q = (vPos + off) / uSize;
    vec4 fn = texture(uF, q);
    float wn = texture(uW, q).r;
    if (wn > 0.0 && fn.a > 0.004) {
        // Acak per piksel per langkah: makin basah makin sering merambat.
        float go = step(hash12(vPos + uJit + vec2(salt * 31.7, salt * 11.3)), wn * 0.22 + 0.02);
        // Menipis sebanding sebaran cat (cat tipis tetap merembes jauh), paling sedikit satu tingkat warna per piksel.
        float dec = max(uLoss * res * len * max(fn.a, 0.25), 1.0 / 255.0);
        best = max(best, (fn.a - dec) * go * edgeK);
        cs += fn.rgb * fn.a * wn;
        ws += fn.a * wn;
    }
}
void main() {
    vec2 uv = vPos / uSize;
    vec4 f = texture(uF, uv);
    float w = texture(uW, uv).r;
    float n1 = texture(uNoise, (vPos / uNoiseK + uSeed) / 256.0).r;
    float n2 = texture(uNoise, (vPos / (uNoiseK * 0.31) + uSeed * 1.7) / 256.0).r;
    float valley = 1.0 - texture(uPaper, vPos / uPaperScale).a;
    res = clamp(0.35 + 1.1 * n1 + 0.45 * n2 - 0.4 * valley, 0.5, 2.0);
    // Dekat batas wilayah simulasi rembesan menipis sendiri (tidak terpotong lurus).
    float ed = min(min(vPos.x - uBound.x, uBound.z - vPos.x), min(vPos.y - uBound.y, uBound.w - vPos.y));
    edgeK = clamp(ed / 24.0, 0.0, 1.0);
    best = f.a;
    ws = w * f.a;
    cs = f.rgb * ws;
    nb(vec2(1.0, 0.0), 1.0, 1.0);
    nb(vec2(-1.0, 0.0), 1.0, 2.0);
    nb(vec2(0.0, 1.0), 1.0, 3.0);
    nb(vec2(0.0, -1.0), 1.0, 4.0);
    nb(vec2(1.0, 1.0), 1.4142, 5.0);
    nb(vec2(-1.0, 1.0), 1.4142, 6.0);
    nb(vec2(1.0, -1.0), 1.4142, 7.0);
    nb(vec2(-1.0, -1.0), 1.4142, 8.0);
    vec3 col = f.rgb;
    if (ws > 0.0) {
        vec3 avg = cs / ws;
        col = f.a < 0.004 ? avg : mix(f.rgb, avg, clamp(uMix * w, 0.0, 1.0));
    }
    o = best > 0.004 ? vec4(col, best) : vec4(0.0);
}
"""

    /** Cat air hidup, langkah air: piksel yang baru terjangkau rembesan ikut basah; semua mengering perlahan. */
    const val WET_WATER_FS = """#version 300 es
precision highp float;
in vec2 vPos;
uniform sampler2D uW;
uniform sampler2D uFOld;
uniform sampler2D uFNew;
uniform vec2 uSize;
uniform float uEvap;
out vec4 o;
float wAt(vec2 off) { return texture(uW, (vPos + off) / uSize).r; }
void main() {
    vec2 uv = vPos / uSize;
    float w = texture(uW, uv).r;
    if (texture(uFNew, uv).a > texture(uFOld, uv).a + 0.001) {
        float m = max(max(wAt(vec2(1.0, 0.0)), wAt(vec2(-1.0, 0.0))), max(wAt(vec2(0.0, 1.0)), wAt(vec2(0.0, -1.0))));
        m = max(m, max(max(wAt(vec2(1.0, 1.0)), wAt(vec2(-1.0, 1.0))), max(wAt(vec2(1.0, -1.0)), wAt(vec2(-1.0, -1.0)))));
        w = max(w, m - 0.012);
    }
    o = vec4(max(0.0, w - uEvap), 0.0, 0.0, 1.0);
}
"""

    /** Cat air hidup: goresan (premultiply) dimasukkan ke cat basah sesi. */
    const val WET_IN_FS = """#version 300 es
precision highp float;
in vec2 vPos;
uniform sampler2D uStroke;
uniform sampler2D uF;
uniform vec2 uSize;
uniform vec3 uColor;
out vec4 o;
void main() {
    vec2 uv = vPos / uSize;
    vec4 s = texture(uStroke, uv);
    vec4 f = texture(uF, uv);
    float a = s.a + f.a * (1.0 - s.a);
    // Di tepi goresan (alfa sangat kecil) warna premultiply 8-bit tidak akurat: pakai warna kuas.
    vec3 sc = mix(uColor, s.rgb / max(s.a, 1e-4), smoothstep(0.05, 0.2, s.a));
    vec3 c = a > 0.0 ? (sc * s.a + f.rgb * f.a * (1.0 - s.a)) / a : vec3(0.0);
    o = a > 0.004 ? vec4(clamp(c, 0.0, 1.0), a) : vec4(0.0);
}
"""

    /** Cat air hidup: tempat yang baru digores jadi basah penuh. */
    const val WET_IN_W_FS = """#version 300 es
precision highp float;
in vec2 vPos;
uniform sampler2D uStroke;
uniform sampler2D uW;
uniform vec2 uSize;
out vec4 o;
void main() {
    vec2 uv = vPos / uSize;
    float w = texture(uW, uv).r;
    o = vec4(texture(uStroke, uv).a > 0.01 ? 1.0 : w, 0.0, 0.0, 1.0);
}
"""

    /** Blur Gaussian satu arah (dipakai dua kali: mendatar lalu tegak). */
    const val BLUR_FS = """#version 300 es
precision highp float;
in vec2 vPos;
uniform sampler2D uSrc;
uniform vec2 uDir;
uniform float uSigma;
uniform vec2 uSize;
out vec4 o;
void main() {
    if (uSigma < 0.5) { o = texture(uSrc, vPos / uSize); return; }
    float stp = max(1.0, uSigma * 3.0 / 16.0);
    vec4 acc = vec4(0.0);
    float ws = 0.0;
    for (int i = -16; i <= 16; i++) {
        float x = float(i) * stp;
        float w = exp(-x * x / (2.0 * uSigma * uSigma));
        acc += texture(uSrc, (vPos + uDir * x) / uSize) * w;
        ws += w;
    }
    o = acc / ws;
}
"""

    /**
     * Pembesaran 2× untuk blur bertingkat: interpolasi B-spline kubik (4 sampel bilinear) supaya gradien hasil blur besar
     * mulus tanpa "patahan" bilinear. Pada tingkat terakhir diberi dither ±½ LSB supaya gradien halus tidak berpita.
     */
    const val UPSAMPLE_FS = """#version 300 es
precision highp float;
in vec2 vPos;
uniform sampler2D uSrc;
uniform vec2 uSrcSize;
uniform vec2 uDstSize;
uniform int uDither;
out vec4 o;
float hash(vec2 p) {
    vec3 p3 = fract(vec3(p.xyx) * vec3(0.1031, 0.1030, 0.0973));
    p3 += dot(p3, p3.yzx + 33.33);
    return fract((p3.x + p3.y) * p3.z);
}
void main() {
    vec2 coord = vPos / uDstSize * uSrcSize - 0.5;
    vec2 idx = floor(coord);
    vec2 f = coord - idx;
    vec2 f2 = f * f;
    vec2 f3 = f2 * f;
    vec2 w0 = (-f3 + 3.0 * f2 - 3.0 * f + 1.0) / 6.0;
    vec2 w1 = (3.0 * f3 - 6.0 * f2 + 4.0) / 6.0;
    vec2 w2 = (-3.0 * f3 + 3.0 * f2 + 3.0 * f + 1.0) / 6.0;
    vec2 w3 = f3 / 6.0;
    vec2 g0 = w0 + w1;
    vec2 g1 = w2 + w3;
    vec2 h0 = (idx - 0.5 + w1 / g0) / uSrcSize;
    vec2 h1 = (idx + 1.5 + w3 / g1) / uSrcSize;
    vec4 r = g0.y * (g0.x * texture(uSrc, h0) + g1.x * texture(uSrc, vec2(h1.x, h0.y)))
           + g1.y * (g0.x * texture(uSrc, vec2(h0.x, h1.y)) + g1.x * texture(uSrc, h1));
    if (uDither == 1 && r.a > 0.0) {
        float n = (hash(vPos) + hash(vPos + 17.0) - 1.0) / 255.0;
        r.a = clamp(r.a + n, 0.0, 1.0);
        r.rgb = clamp(r.rgb + n, vec3(0.0), vec3(r.a));
    }
    o = r;
}
"""

    /**
     * Mode Pena penyesuaian. uMode 0: satu tapak bulat lembut (ke masker goresan R8, dicampur MAX);
     * 1: goresan → masker (dicampur lewat blending); 2: pratinjau = campuran layer asli dan hasil penuh menurut masker.
     */
    const val PEN_FS = """#version 300 es
precision highp float;
in vec2 vPos;
uniform int uMode;
uniform vec3 uDab;
uniform float uAmt;
uniform float uOp;
uniform int uErase;
uniform vec2 uSize;
uniform sampler2D uSrc;
uniform sampler2D uFull;
uniform sampler2D uMask;
uniform sampler2D uStroke;
out vec4 o;
void main() {
    if (uMode == 0) {
        float d = length(vPos - uDab.xy) / uDab.z;
        float a = (1.0 - smoothstep(0.35, 1.0, d)) * uAmt;
        o = vec4(a, 0.0, 0.0, a);
        return;
    }
    vec2 uv = vPos / uSize;
    float s = texture(uStroke, uv).r * uOp;
    if (uMode == 1) {
        o = vec4(s, 0.0, 0.0, s);
        return;
    }
    float m = texture(uMask, uv).r;
    float m2 = uErase == 1 ? m * (1.0 - s) : m + s * (1.0 - m);
    o = mix(texture(uSrc, uv), texture(uFull, uv), m2);
}
"""

    /**
     * Penyesuaian per piksel. Satu sumber untuk banyak jenis; tiap jenis dikompilasi sendiri dengan #define KIND = Adj.*.
     * uSrc = layer asli, uAux = hasil pass sebelumnya (blur untuk Sharpen/Bloom), uLut = tabel 256×1 (Curves, Gradient Map).
     */
    const val FILTER_FS = """#version 300 es
precision highp float;
precision highp int;
in vec2 vPos;
uniform sampler2D uSrc;
uniform sampler2D uAux;
uniform sampler2D uLut;
uniform vec2 uSize;
uniform float uTaps;
uniform float uP[12];
uniform int uAlphaLock;
out vec4 o;

vec4 at(vec2 p) {
    if (p.x < 0.0 || p.y < 0.0 || p.x > uSize.x || p.y > uSize.y) return vec4(0.0);
    return texture(uSrc, p / uSize);
}
vec3 unpre(vec4 c) { return c.a > 0.0 ? c.rgb / c.a : vec3(0.0); }
float lum(vec3 c) { return dot(c, vec3(0.299, 0.587, 0.114)); }
// Acak per sel kisi (hanya aritmetika float: hasil sama di semua GPU).
float hash(vec2 p, float seed) {
    vec3 p3 = fract(vec3(floor(p).xyx + seed * vec3(17.13, 31.71, 7.37)) * vec3(0.1031, 0.1030, 0.0973));
    p3 += dot(p3, p3.yzx + 33.33);
    return fract((p3.x + p3.y) * p3.z);
}
float vnoise(vec2 p, float seed) {
    vec2 i = floor(p);
    vec2 f = p - i;
    vec2 u = f * f * (3.0 - 2.0 * f);
    float a = hash(i, seed);
    float b = hash(i + vec2(1.0, 0.0), seed);
    float c = hash(i + vec2(0.0, 1.0), seed);
    float d = hash(i + vec2(1.0, 1.0), seed);
    return mix(mix(a, b, u.x), mix(c, d, u.x), u.y);
}
vec3 lut3(vec3 c) {
    vec3 x = (clamp(c, 0.0, 1.0) * 255.0 + 0.5) / 256.0;
    return vec3(texture(uLut, vec2(x.r, 0.5)).r, texture(uLut, vec2(x.g, 0.5)).g, texture(uLut, vec2(x.b, 0.5)).b);
}
// Tiga sampel → satu piksel dengan kanal R, G, B dari sampel berbeda (pemisahan warna). Dihitung "di atas kertas putih"
// supaya garis gelap di layer transparan pun memunculkan pinggiran warna.
vec4 splitRgb(vec4 a, vec4 b, vec4 c) {
    vec3 w = vec3(1.0 - a.a + a.r, 1.0 - b.a + b.g, 1.0 - c.a + c.b);
    float al = max(max(a.a, b.a), c.a);
    return vec4(clamp(w - (1.0 - al), 0.0, 1.0), al);
}
vec2 rot(vec2 p, float a) {
    float c = cos(a);
    float s = sin(a);
    return vec2(c * p.x - s * p.y, s * p.x + c * p.y);
}
// Halftone: nilai tinta [0..1] di pusat sel → titik bulat. Mengembalikan liputan titik di p.
float dotCover(vec2 p, float cs, float ang, int ch) {
    vec2 q = rot(p, ang);
    vec2 cc = (floor(q / cs) + 0.5) * cs;
    vec4 sc = at(rot(cc, -ang));
    vec3 c = unpre(sc);
    float v;
    if (ch == 3) {
        v = (1.0 - lum(c));
    } else {
        float k = 1.0 - max(max(c.r, c.g), c.b);
        if (ch == 4) v = k;
        else {
            float cv = ch == 0 ? c.r : (ch == 1 ? c.g : c.b);
            v = k >= 1.0 ? 0.0 : (1.0 - cv - k) / (1.0 - k);
        }
    }
    v = clamp(v * sc.a, 0.0, 1.0);
    float rad = sqrt(v) * cs * 0.7071;
    if (rad < 0.35) return 0.0;
    return clamp(rad - length(q - cc) + 0.5, 0.0, 1.0);
}

void main() {
    vec2 uv = vPos / uSize;
    vec4 s = texture(uSrc, uv);
    vec4 r = s;
#if KIND == 2
    {
        // Color Balance: geser kanal untuk bayangan, nada tengah, dan sorotan.
        if (s.a > 0.0) {
            vec3 c = s.rgb / s.a;
            float l = lum(c);
            float sh = clamp((l - 0.333) / -0.25 + 0.5, 0.0, 1.0) * 0.7;
            float md = clamp((l - 0.333) / 0.25 + 0.5, 0.0, 1.0) * clamp((l + 0.333 - 1.0) / -0.25 + 0.5, 0.0, 1.0) * 0.7;
            float hi = clamp((l + 0.333 - 1.0) / 0.25 + 0.5, 0.0, 1.0) * 0.7;
            c += vec3(uP[0], uP[1], uP[2]) * sh + vec3(uP[3], uP[4], uP[5]) * md + vec3(uP[6], uP[7], uP[8]) * hi;
            r = vec4(clamp(c, 0.0, 1.0) * s.a, s.a);
        }
    }
#elif KIND == 3
    {
        // Curves
        if (s.a > 0.0) r = vec4(lut3(s.rgb / s.a) * s.a, s.a);
    }
#elif KIND == 4
    {
        // Gradient Map: kecerahan → warna gradien.
        if (s.a > 0.0) {
            vec3 c = s.rgb / s.a;
            vec3 g = texture(uLut, vec2((lum(c) * 255.0 + 0.5) / 256.0, 0.5)).rgb;
            r = vec4(mix(c, g, uP[0]) * s.a, s.a);
        }
    }
#elif KIND == 5
    {
        // Motion Blur: rata-rata sepanjang garis searah.
        float len = uP[0];
        if (len >= 0.5) {
            vec2 d = vec2(cos(uP[1]), sin(uP[1])) * len;
            int n = int(clamp(len, 4.0, uTaps));
            vec4 acc = vec4(0.0);
            for (int i = 0; i < 128; i++) {
                if (i >= n) break;
                acc += at(vPos + d * ((float(i) + 0.5) / float(n) - 0.5));
            }
            r = acc / float(n);
        }
    }
#elif KIND == 6
    {
        // Perspective Blur: blur memancar dari titik pusat (posisional = dua arah, searah = menuju pusat).
        // Sampel diletakkan rata dalam skala log (diberi bobot k supaya sebarannya tetap sama dengan rata dalam k):
        // dengan begitu beberapa lintasan bisa digabung tanpa celah (lihat Engine.perspPasses).
        float amt = uP[0];
        if (amt > 0.001) {
            vec2 c = vec2(uP[2], uP[3]);
            vec2 v = vPos - c;
            float k0 = uP[1] < 0.5 ? 1.0 - amt * 0.25 : 1.0 - amt * 0.5;
            float k1 = uP[1] < 0.5 ? 1.0 + amt * 0.25 : 1.0;
            k0 = max(k0, 0.02);
            float u0 = log(k0);
            float u1 = log(k1);
            int n = int(clamp(length(v) * k1 * (u1 - u0), 4.0, uTaps));
            vec4 acc = vec4(0.0);
            float ws = 0.0;
            for (int i = 0; i < 128; i++) {
                if (i >= n) break;
                float k = exp(mix(u0, u1, (float(i) + 0.5) / float(n)));
                acc += at(c + v * k) * k;
                ws += k;
            }
            r = acc / ws;
        }
    }
#elif KIND == 7
    {
        // Noise: awan / gelombang / punggung, dicampur seperti overlay.
        float amt = uP[0];
        if (amt > 0.001 && s.a > 0.0) {
            vec2 p = vPos / max(uP[1], 1.0);
            int oct = int(uP[2] + 0.5);
            float turb = uP[3];
            float sum = 0.0;
            float amp = 0.5;
            float norm = 0.0;
            for (int i = 0; i < 8; i++) {
                if (i >= oct) break;
                float nv = vnoise(p, uP[5] + float(i));
                if (uP[4] > 1.5) nv = 1.0 - abs(nv * 2.0 - 1.0);
                else if (uP[4] > 0.5) nv = abs(nv * 2.0 - 1.0);
                sum += nv * amp;
                norm += amp;
                amp *= mix(0.35, 0.8, turb);
                p = p * 2.03 + vec2(1.7, -2.3) * turb * nv;
            }
            float n = sum / max(norm, 1e-5);
            if (uP[4] > 1.5) n = n * 1.3 - 0.37;
            else if (uP[4] > 0.5) n = n * 1.3 + 0.07;
            // Jumlah besar juga menaikkan kontras pola supaya efeknya bisa benar-benar kuat.
            n = clamp((n - 0.5) * (1.0 + amt * 1.5) + 0.5, 0.0, 1.0);
            vec3 c = s.rgb / s.a;
            vec3 ov = mix(2.0 * c * n, 1.0 - 2.0 * (1.0 - c) * (1.0 - n), step(0.5, c));
            r = vec4(mix(c, ov, min(1.0, amt * 1.5)) * s.a, s.a);
        }
    }
#elif KIND == 8
    {
        // Sharpen (unsharp mask): warna dan alfa dipertajam terpisah.
        vec4 b = texture(uAux, uv);
        float k = uP[0] * 3.0;
        float a = clamp(s.a + (s.a - b.a) * k, 0.0, 1.0);
        vec3 c = clamp(unpre(s) + (unpre(s) - unpre(b)) * k, 0.0, 1.0);
        r = vec4(c * a, a);
    }
#elif KIND == 100
    {
        // Bagian terang untuk Bloom.
        vec3 c = unpre(s);
        float l = max(max(c.r, c.g), c.b) * 0.5 + lum(c) * 0.5;
        float thr = mix(0.95, 0.0, uP[1]);
        float w = smoothstep(thr, thr + 0.2, l) * s.a;
        r = vec4(c * w, w);
    }
#elif KIND == 9
    {
        // Bloom: cahaya hasil blur digabung "screen".
        vec4 g = clamp(texture(uAux, uv) * uP[0] * 3.0 * (1.0 + 2.0 * uP[3]), 0.0, 1.0);
        r = s + g - s * g;
    }
#elif KIND == 10
    {
        // Glitch
        float amt = uP[0];
        if (amt > 0.001) {
            float bs = max(uP[1], 2.0);
            float seed = uP[2];
            int mode = int(uP[3] + 0.5);
            vec2 p = vPos;
            if (mode == 0) {
                // Artefak: blok-blok bergeser, sebagian kanalnya tertukar.
                vec2 cell = floor(p / vec2(bs * 2.0, bs));
                if (hash(cell, seed) < amt * 0.6) {
                    vec2 off = (vec2(hash(cell, seed + 1.0), hash(cell, seed + 2.0)) - 0.5) * bs * 4.0 * amt;
                    r = at(p + off);
                    float sw = hash(cell, seed + 3.0);
                    if (sw < 0.25) r = vec4(r.g, r.b, r.r, r.a);
                    else if (sw < 0.4) r = vec4(r.b, r.r, r.g, r.a);
                }
            } else if (mode == 1) {
                // Gelombang: baris bergeser mengikuti gelombang.
                float row = floor(p.y / max(bs * 0.25, 1.0));
                float sh = sin(p.y / bs * 2.0 + seed) * bs * amt + (hash(vec2(row, 7.0), seed) - 0.5) * bs * amt * 1.5;
                r = at(p + vec2(sh, 0.0));
            } else if (mode == 2) {
                // Sinyal: pita mendatar bergeser dengan warna terpisah.
                float band = floor(p.y / bs);
                float on = step(1.0 - amt * 0.8, hash(vec2(3.0, band), seed));
                float sh = (hash(vec2(5.0, band), seed) - 0.5) * bs * 3.0 * on;
                float sp = bs * 0.3 * amt * (0.4 + on);
                r = splitRgb(at(p + vec2(sh + sp, 0.0)), at(p + vec2(sh, 0.0)), at(p + vec2(sh - sp, 0.0)));
                // Bintik derau hanya di atas isi layer (bagian transparan tetap transparan).
                if (on > 0.5 && r.a > 0.0 && hash(vec2(floor(p.x / 3.0), floor(p.y / 2.0)), seed + 9.0) < 0.12 * amt) {
                    float g = hash(p, seed + 11.0);
                    r = vec4(vec3(g) * r.a, r.a);
                }
            } else {
                // Pecah: kanal warna menyebar ke arah berbeda.
                float d = bs * amt;
                r = splitRgb(at(p + vec2(d, -d * 0.3)), at(p), at(p + vec2(-d, d * 0.3)));
            }
        }
    }
#elif KIND == 11
    {
        // Halftone: warna penuh (CMYK) / sablon / koran.
        float k = uP[0];
        if (k > 0.001) {
            float cs = mix(3.0, max(uSize.x, uSize.y) / 12.0, pow(k, 1.5));
            int mode = int(uP[1] + 0.5);
            if (mode == 0) {
                float cC = dotCover(vPos, cs, 0.2618, 0);
                float cM = dotCover(vPos, cs, 1.309, 1);
                float cY = dotCover(vPos, cs, 0.0, 2);
                float cK = dotCover(vPos, cs, 0.7854, 4);
                vec3 w = vec3(1.0 - cC, 1.0 - cM, 1.0 - cY) * (1.0 - cK);
                float al = 1.0 - min(min(w.r, w.g), w.b);
                r = vec4(w - (1.0 - al), al);
            } else if (mode == 1) {
                vec2 q = rot(vPos, 0.7854);
                vec2 cc = (floor(q / cs) + 0.5) * cs;
                vec4 sc = at(rot(cc, -0.7854));
                vec3 c = unpre(sc);
                float v = clamp(sc.a * (1.0 - lum(c) * 0.6), 0.0, 1.0);
                float rad = sqrt(v) * cs * 0.7071;
                float cov = rad < 0.35 ? 0.0 : clamp(rad - length(q - cc) + 0.5, 0.0, 1.0);
                r = vec4(c * cov, cov);
            } else {
                float cov = dotCover(vPos, cs, 0.7854, 3);
                r = vec4(0.0, 0.0, 0.0, cov);
            }
        }
    }
#elif KIND == 12
    {
        // Chromatic Aberration: perspektif (dari titik pusat) atau geser (satu arah).
        float amt = uP[0];
        if (amt > 0.01) {
            vec2 off;
            if (uP[3] < 0.5) {
                vec2 v = vPos - vec2(uP[4], uP[5]);
                float dn = clamp(length(v) / (0.5 * length(uSize)), 0.0, 1.0);
                float f = pow(dn, mix(0.4, 3.0, uP[1]));
                off = (length(v) > 0.0 ? normalize(v) : vec2(0.0)) * amt * f;
            } else {
                off = vec2(cos(uP[2]), sin(uP[2])) * amt;
            }
            r = splitRgb(at(vPos - off), s, at(vPos + off));
        }
    }
#elif KIND == 101
    {
        // Kunci alfa setelah blur berlapis: warna dari hasil (uAux), alfa dari layer asli (uSrc).
        r = vec4(unpre(texture(uAux, uv)) * s.a, s.a);
    }
#endif
#if KIND != 100 && KIND != 101
    if (uAlphaLock == 1) r = vec4(unpre(r) * s.a, s.a);
#endif
    o = r;
}
"""

    /** Shader filter untuk satu jenis saja (cabang lain dibuang praprosesor: kompilasi cepat, register sedikit). */
    fun filter(kind: Int): String = FILTER_FS.replaceFirst("#version 300 es\n", "#version 300 es\n#define KIND $kind\n")

    /** Liquify: tiap piksel mengambil warna dari posisi asal menurut medan pergeseran (kisi kasar, interpolasi bilinear). */
    const val LIQUIFY_FS = """#version 300 es
precision highp float;
precision highp int;
in vec2 vPos;
uniform sampler2D uSrc;
uniform highp sampler2D uField;
uniform vec2 uSize;
uniform vec2 uGrid;
uniform float uStep;
uniform float uAmt;
uniform int uAlphaLock;
out vec4 o;
void main() {
    vec2 g = vPos / uStep;
    ivec2 mx = ivec2(uGrid) - 2;
    ivec2 i = clamp(ivec2(floor(g)), ivec2(0), mx);
    vec2 f = clamp(g - vec2(i), 0.0, 1.0);
    vec2 a = texelFetch(uField, i, 0).xy;
    vec2 b = texelFetch(uField, i + ivec2(1, 0), 0).xy;
    vec2 c = texelFetch(uField, i + ivec2(0, 1), 0).xy;
    vec2 d = texelFetch(uField, i + ivec2(1, 1), 0).xy;
    vec2 p = vPos + mix(mix(a, b, f.x), mix(c, d, f.x), f.y) * uAmt;
    vec4 r = (p.x < 0.0 || p.y < 0.0 || p.x > uSize.x || p.y > uSize.y) ? vec4(0.0) : texture(uSrc, p / uSize);
    if (uAlphaLock == 1) {
        vec4 s = texture(uSrc, vPos / uSize);
        vec3 cc = r.a > 0.0 ? r.rgb / r.a : vec3(0.0);
        r = vec4(cc * s.a, s.a);
    }
    o = r;
}
"""

    /**
     * Satu sapuan Liquify di GPU (sama dengan Engine.cpuDab): titik kisi p mengambil isi dari titik q, jadi pergeseran
     * barunya = q + D(q) − p, dengan D(q) diambil bilinear dari medan lama. Digambar hanya di kotak sapuan.
     */
    const val LIQ_DAB_FS = """#version 300 es
precision highp float;
precision highp int;
in vec2 vPos;
uniform highp sampler2D uField;
uniform vec2 uGrid;
uniform float uStep;
uniform int uMode;
uniform vec2 uC;
uniform float uRad;
uniform float uK;
uniform vec2 uV;
uniform vec2 uN;
uniform float uDistort;
uniform float uCell;
uniform float uSeed;
out vec4 o;
vec2 fieldAt(vec2 g) {
    g = clamp(g, vec2(0.0), uGrid - 1.0);
    ivec2 i = ivec2(min(floor(g), uGrid - 2.0));
    vec2 f = g - vec2(i);
    vec2 a = texelFetch(uField, i, 0).rg;
    vec2 b = texelFetch(uField, i + ivec2(1, 0), 0).rg;
    vec2 c = texelFetch(uField, i + ivec2(0, 1), 0).rg;
    vec2 d = texelFetch(uField, i + ivec2(1, 1), 0).rg;
    return mix(mix(a, b, f.x), mix(c, d, f.x), f.y);
}
void main() {
    ivec2 gi = ivec2(floor(vPos));
    vec2 old = texelFetch(uField, gi, 0).rg;
    vec2 p = vec2(gi) * uStep;
    vec2 dv = p - uC;
    float d2 = dot(dv, dv);
    float r2 = uRad * uRad;
    if (d2 >= r2) { o = vec4(old, 0.0, 1.0); return; }
    float t = 1.0 - d2 / r2;
    float w = t * t * uK;
    if (uMode == 7) { o = vec4(old * (1.0 - min(1.0, w)), 0.0, 1.0); return; }
    vec2 q;
    if (uMode == 0) {
        q = p - uV * w;
    } else if (uMode == 1 || uMode == 2) {
        float ang = uMode == 1 ? w : -w;
        float c = cos(ang);
        float s = sin(ang);
        q = uC + vec2(dv.x * c + dv.y * s, -dv.x * s + dv.y * c);
    } else if (uMode == 3) {
        q = uC + dv * (1.0 + w);
    } else if (uMode == 4) {
        q = uC + dv * (1.0 - w);
    } else if (uMode == 5) {
        ivec2 hc = ivec2(floor(p / uCell));
        uint h = (uint(hc.x) * 73856093u) ^ (uint(hc.y) * 19349663u) ^ (uint(uSeed) * 83492791u);
        h ^= h >> 13u;
        h *= 0x5bd1e995u;
        h ^= h >> 15u;
        float a = float(h & 0xFFFFu) / 65535.0 * 6.2831855;
        float m = w * uRad * (0.15 + uDistort * 0.6);
        q = p + vec2(cos(a), sin(a)) * m;
    } else {
        float proj = dot(dv, uN);
        q = p + uN * proj * w * (0.5 + uDistort);
    }
    o = vec4(q + fieldAt(q / uStep) - p, 0.0, 1.0);
}
"""

    /** Transformasi layer: tiap piksel target mengambil warna dari posisi asalnya. */
    const val TRANSFORM_FS = """#version 300 es
precision highp float;
in vec2 vPos;
uniform sampler2D uLayer;
uniform mat3 uInv;
uniform vec2 uSize;
uniform int uInterp;
out vec4 o;

// Interpolasi saat transformasi: 0 terdekat, 1 bilinear, 2 bikubik (Catmull-Rom). p dalam piksel kanvas.
vec4 cubicW(float t) {
    float t2 = t * t;
    float t3 = t2 * t;
    return vec4(-0.5 * t3 + t2 - 0.5 * t, 1.5 * t3 - 2.5 * t2 + 1.0, -1.5 * t3 + 2.0 * t2 + 0.5 * t, 0.5 * t3 - 0.5 * t2);
}
vec4 interpAt(sampler2D t, vec2 p, vec2 size, int mode) {
    ivec2 mx = ivec2(size) - 1;
    if (mode == 0) return texelFetch(t, clamp(ivec2(floor(p)), ivec2(0), mx), 0);
    if (mode == 1) return texture(t, p / size);
    vec2 q = p - 0.5;
    vec2 f = q - floor(q);
    ivec2 b = ivec2(floor(q));
    vec4 wx = cubicW(f.x);
    vec4 wy = cubicW(f.y);
    vec4 acc = vec4(0.0);
    for (int j = 0; j < 4; j++) {
        vec4 row = vec4(0.0);
        for (int i = 0; i < 4; i++) row += texelFetch(t, clamp(b + ivec2(i - 1, j - 1), ivec2(0), mx), 0) * wx[i];
        acc += row * wy[j];
    }
    acc = clamp(acc, 0.0, 1.0);
    return vec4(min(acc.rgb, vec3(acc.a)), acc.a);
}

void main() {
    vec3 h = uInv * vec3(vPos, 1.0);
    if (h.z <= 1e-6) { o = vec4(0.0); return; }
    vec2 p = h.xy / h.z;
    if (p.x < 0.0 || p.y < 0.0 || p.x > uSize.x || p.y > uSize.y) { o = vec4(0.0); return; }
    o = interpAt(uLayer, p, uSize, uInterp);
}
"""

    /** Warp: jaring segitiga, tiap titik membawa posisi tujuan (aPos) dan posisi asal di layer (aSrc). */
    const val MESH_VS = """#version 300 es
layout(location = 0) in vec2 aPos;
layout(location = 1) in vec2 aSrc;
uniform vec2 uTarget;
out vec2 vSrc;
void main() {
    vSrc = aSrc;
    gl_Position = vec4(aPos / uTarget * 2.0 - 1.0, 0.0, 1.0);
}
"""

    const val MESH_FS = """#version 300 es
precision highp float;
in vec2 vSrc;
uniform sampler2D uLayer;
uniform vec2 uSize;
uniform int uInterp;
out vec4 o;

// Interpolasi saat transformasi: 0 terdekat, 1 bilinear, 2 bikubik (Catmull-Rom). p dalam piksel kanvas.
vec4 cubicW(float t) {
    float t2 = t * t;
    float t3 = t2 * t;
    return vec4(-0.5 * t3 + t2 - 0.5 * t, 1.5 * t3 - 2.5 * t2 + 1.0, -1.5 * t3 + 2.0 * t2 + 0.5 * t, 0.5 * t3 - 0.5 * t2);
}
vec4 interpAt(sampler2D t, vec2 p, vec2 size, int mode) {
    ivec2 mx = ivec2(size) - 1;
    if (mode == 0) return texelFetch(t, clamp(ivec2(floor(p)), ivec2(0), mx), 0);
    if (mode == 1) return texture(t, p / size);
    vec2 q = p - 0.5;
    vec2 f = q - floor(q);
    ivec2 b = ivec2(floor(q));
    vec4 wx = cubicW(f.x);
    vec4 wy = cubicW(f.y);
    vec4 acc = vec4(0.0);
    for (int j = 0; j < 4; j++) {
        vec4 row = vec4(0.0);
        for (int i = 0; i < 4; i++) row += texelFetch(t, clamp(b + ivec2(i - 1, j - 1), ivec2(0), mx), 0) * wx[i];
        acc += row * wy[j];
    }
    acc = clamp(acc, 0.0, 1.0);
    return vec4(min(acc.rgb, vec3(acc.a)), acc.a);
}

void main() {
    o = interpAt(uLayer, vSrc, uSize, uInterp);
}
"""

    /** Kanvas ke layar dengan matriks tampilan (geser, perbesar, putar, balik). */
    const val SCREEN_VS = """#version 300 es
uniform vec4 uRect;
uniform mat3 uView;
uniform vec2 uScreen;
out vec2 vPos;
void main() {
    vec2 c = vec2((gl_VertexID == 1 || gl_VertexID == 3) ? 1.0 : 0.0, gl_VertexID >= 2 ? 1.0 : 0.0);
    vec2 p = mix(uRect.xy, uRect.zw, c);
    vPos = p;
    vec3 s = uView * vec3(p, 1.0);
    gl_Position = vec4(s.x / uScreen.x * 2.0 - 1.0, 1.0 - s.y / uScreen.y * 2.0, 0.0, 1.0);
}
"""

    const val SCREEN_FS = """#version 300 es
precision highp float;
in vec2 vPos;
uniform sampler2D uComp;
uniform vec2 uSize;
uniform int uChecker;
uniform float uCheckScale;
uniform sampler2D uSel;
uniform int uUseSel;
/** Warna layar: bit 1 = matriks ruang RGB (uCmMat, linear), bit 2 = LUT proof CMYK (uLut, sebelum matriks). */
uniform int uCm;
uniform mat3 uCmMat;
uniform highp sampler3D uLut;
out vec4 o;
vec3 linS(vec3 c) { return mix(c / 12.92, pow((c + 0.055) / 1.055, vec3(2.4)), step(vec3(0.04045), c)); }
vec3 encS(vec3 c) {
    c = clamp(c, 0.0, 1.0);
    return mix(c * 12.92, 1.055 * pow(c, vec3(1.0 / 2.4)) - 0.055, step(vec3(0.0031308), c));
}
void main() {
    vec4 c = texture(uComp, vPos / uSize);
    vec3 bg = vec3(1.0);
    if (uChecker == 1) {
        vec2 q = floor(vPos / uCheckScale);
        float k = mod(q.x + q.y, 2.0);
        bg = vec3(mix(0.80, 0.94, k));
    }
    vec3 col = c.rgb + bg * (1.0 - c.a);
    if (uCm >= 2) col = texture(uLut, clamp(col, 0.0, 1.0) * (32.0 / 33.0) + 0.5 / 33.0).rgb;
    if (uCm == 1 || uCm == 3) col = encS(uCmMat * linS(col));
    if (uUseSel == 1) {
        // Bagian di luar pilihan: digelapkan dengan garis-garis miring; tepinya garis putus-putus hitam-putih.
        float m = texture(uSel, vPos / uSize).r;
        vec2 sp = gl_FragCoord.xy;
        float stripe = step(0.5, fract((sp.x + sp.y) / 18.0));
        float outside = 1.0 - m;
        col = mix(col, col * 0.72 + vec3(0.06, 0.09, 0.14) * stripe, outside * 0.85);
        float fw = max(fwidth(m), 1e-4);
        float edge = 1.0 - clamp(abs(m - 0.5) / (fw * 1.3), 0.0, 1.0);
        float dash = step(0.5, fract((sp.x - sp.y) / 12.0));
        col = mix(col, vec3(dash), edge);
    }
    o = vec4(col, 1.0);
}
"""

    /** Ubah warna tekstur (premultiply): uMode 1 = matriks ruang RGB (linear), 2 = LUT proof CMYK. */
    const val COLOR_FS = """#version 300 es
precision highp float;
in vec2 vPos;
uniform sampler2D uSrc;
uniform highp sampler3D uLut;
uniform vec2 uSize;
uniform int uMode;
uniform mat3 uMat;
out vec4 o;
vec3 linS(vec3 c) { return mix(c / 12.92, pow((c + 0.055) / 1.055, vec3(2.4)), step(vec3(0.04045), c)); }
vec3 encS(vec3 c) {
    c = clamp(c, 0.0, 1.0);
    return mix(c * 12.92, 1.055 * pow(c, vec3(1.0 / 2.4)) - 0.055, step(vec3(0.0031308), c));
}
void main() {
    vec4 s = texture(uSrc, vPos / uSize);
    if (s.a <= 0.0) { o = vec4(0.0); return; }
    vec3 c = clamp(s.rgb / s.a, 0.0, 1.0);
    if (uMode == 1) c = encS(uMat * linS(c));
    else c = texture(uLut, c * (32.0 / 33.0) + 0.5 / 33.0).rgb;
    o = vec4(c * s.a, s.a);
}
"""

    /**
     * Operasi dengan masker pilihan. uMode: 0 = layer × masker, 1 = layer × (1 − masker), 2 = campur uA→uB menurut
     * masker (penyesuaian hanya di dalam pilihan), 3 = isi warna di dalam pilihan.
     */
    const val SELOP_FS = """#version 300 es
precision highp float;
in vec2 vPos;
uniform sampler2D uA;
uniform sampler2D uB;
uniform sampler2D uMask;
uniform vec2 uSize;
uniform int uMode;
uniform vec3 uColor;
uniform int uAlphaLock;
out vec4 o;
void main() {
    vec2 uv = vPos / uSize;
    float m = texture(uMask, uv).r;
    vec4 a = texture(uA, uv);
    if (uMode == 0) { o = a * m; return; }
    if (uMode == 1) { o = a * (1.0 - m); return; }
    if (uMode == 2) { o = mix(a, texture(uB, uv), m); return; }
    vec4 r = vec4(uColor, 1.0) * m + a * (1.0 - m);
    if (uAlphaLock == 1) {
        vec3 c = r.a > 0.0 ? r.rgb / r.a : vec3(0.0);
        r = vec4(c * a.a, a.a);
    }
    o = r;
}
"""

    /** Persegi di ruang layar (piksel layar, y ke bawah). */
    const val SPRITE_VS = """#version 300 es
uniform vec4 uRect;
uniform vec2 uScreen;
out vec2 vPos;
void main() {
    vec2 c = vec2((gl_VertexID == 1 || gl_VertexID == 3) ? 1.0 : 0.0, gl_VertexID >= 2 ? 1.0 : 0.0);
    vec2 p = mix(uRect.xy, uRect.zw, c);
    vPos = p;
    gl_Position = vec4(p.x / uScreen.x * 2.0 - 1.0, 1.0 - p.y / uScreen.y * 2.0, 0.0, 1.0);
}
"""

    /** Lingkaran tipis penanda ukuran kuas saat pena melayang. */
    const val RING_FS = """#version 300 es
precision highp float;
in vec2 vPos;
uniform vec2 uCenter;
uniform float uRadius;
uniform float uPx;
out vec4 o;
void main() {
    float d = abs(distance(vPos, uCenter) - uRadius);
    float core = 1.0 - smoothstep(0.5 * uPx, 1.2 * uPx, d);
    float halo = 1.0 - smoothstep(1.3 * uPx, 2.6 * uPx, d);
    o = vec4(vec3(core), max(core, halo * 0.5));
}
"""

    /** Pegangan transformasi: titik berwarna berbingkai putih dengan bayangan tipis. */
    const val DOT_FS = """#version 300 es
precision highp float;
in vec2 vPos;
uniform vec2 uCenter;
uniform float uRadius;
uniform float uPx;
uniform vec4 uColor;
out vec4 o;
void main() {
    float d = distance(vPos, uCenter);
    float inner = 1.0 - smoothstep(uRadius - 2.2 * uPx, uRadius - 1.4 * uPx, d);
    float body = 1.0 - smoothstep(uRadius - 0.5 * uPx, uRadius + 0.5 * uPx, d);
    float shadow = (1.0 - smoothstep(uRadius, uRadius + 2.5 * uPx, d)) * 0.35;
    vec3 c = mix(vec3(1.0), uColor.rgb, inner);
    o = vec4(c * body, body) + vec4(0.0, 0.0, 0.0, shadow * (1.0 - body));
}
"""

    const val LINE_VS = """#version 300 es
layout(location = 0) in vec2 aPos;
uniform vec2 uScreen;
void main() {
    gl_Position = vec4(aPos.x / uScreen.x * 2.0 - 1.0, 1.0 - aPos.y / uScreen.y * 2.0, 0.0, 1.0);
}
"""

    const val LINE_FS = """#version 300 es
precision highp float;
uniform vec4 uColor;
out vec4 o;
void main() { o = uColor; }
"""
}
