package id.goresan.engine

import android.opengl.GLES30 as G
import android.opengl.Matrix
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.tan

/** Kamera tampilan 3D: putaran (radian), jarak, dan geser (satuan dunia). */
data class Cam3D(val yaw: Float = 0.55f, val pitch: Float = 0.3f, val dist: Float = 5.0f, val px: Float = 0f, val py: Float = 0f) {
    /** Satuan dunia per piksel layar di bidang target (untuk geser dua jari). */
    fun worldPerPx(screenH: Int): Float = 2f * dist * tan(Math.toRadians(FOVY / 2.0)).toFloat() / screenH.coerceAtLeast(1)

    companion object {
        const val FOVY = 30.0
    }
}

/**
 * Penggambar model 3D bertekstur kanvas: tampilan berbayang (dengan MSAA bila bisa) dan penyangga pilih (UV tiap
 * piksel layar, setengah resolusi) untuk memetakan sentuhan ke kanvas. Semua fungsi di thread GL, kecuali [uvAt].
 */
internal class Renderer3D(private val allowMsaa: Boolean = true) {
    private val prog = Program(VS, FS)
    private var vao = 0
    private var vbo = 0
    private var ibo = 0
    private var count = 0
    private var norm = floatArrayOf(0f, 0f, 0f, 1f)

    // Tampilan: MSAA (warna+kedalaman) → tekstur biasa → layar.
    private var vw = 0
    private var vh = 0
    private var msFbo = 0
    private var msColor = 0
    private var msDepth = 0
    private var resolveFbo = 0
    private var resolveTex = 0
    private var samples = 0

    // Penyangga pilih.
    private var pFbo = 0
    private var pTex = 0
    private var pDepth = 0
    private var pw = 0
    private var ph = 0
    private val pickBufs = arrayOfNulls<ByteBuffer>(2)
    private var pickFlip = 0

    @Volatile private var pick: PickSnap? = null
    /** Kamera/ukuran/model saat penyangga pilih dibuat; null = perlu dibuat ulang. */
    var pickKey: Any? = null
        private set
    private var meshSerial = 0

    private class PickSnap(val buf: ByteBuffer, val w: Int, val h: Int, val sw: Int, val sh: Int)

    val hasMesh get() = count > 0

    fun setMesh(m: Mesh?) {
        if (vbo != 0) G.glDeleteBuffers(2, intArrayOf(vbo, ibo), 0)
        if (vao != 0) G.glDeleteVertexArrays(1, intArrayOf(vao), 0)
        vao = 0; vbo = 0; ibo = 0; count = 0
        meshSerial++
        pickKey = null
        pick = null
        if (m == null) return
        norm = m.bounds()
        val nv = m.vertices
        val fb = ByteBuffer.allocateDirect(nv * 8 * 4).order(ByteOrder.nativeOrder())
        val f = fb.asFloatBuffer()
        for (i in 0 until nv) {
            f.put(m.pos, i * 3, 3)
            f.put(m.nrm, i * 3, 3)
            f.put(m.uv, i * 2, 2)
        }
        val ib = ByteBuffer.allocateDirect(m.idx.size * 4).order(ByteOrder.nativeOrder())
        ib.asIntBuffer().put(m.idx)
        val ids = IntArray(2)
        G.glGenBuffers(2, ids, 0)
        vbo = ids[0]; ibo = ids[1]
        val va = IntArray(1)
        G.glGenVertexArrays(1, va, 0)
        vao = va[0]
        G.glBindVertexArray(vao)
        G.glBindBuffer(G.GL_ARRAY_BUFFER, vbo)
        G.glBufferData(G.GL_ARRAY_BUFFER, nv * 32, fb, G.GL_STATIC_DRAW)
        G.glEnableVertexAttribArray(0)
        G.glVertexAttribPointer(0, 3, G.GL_FLOAT, false, 32, 0)
        G.glEnableVertexAttribArray(1)
        G.glVertexAttribPointer(1, 3, G.GL_FLOAT, false, 32, 12)
        G.glEnableVertexAttribArray(2)
        G.glVertexAttribPointer(2, 2, G.GL_FLOAT, false, 32, 24)
        G.glBindBuffer(G.GL_ELEMENT_ARRAY_BUFFER, ibo)
        G.glBufferData(G.GL_ELEMENT_ARRAY_BUFFER, m.idx.size * 4, ib, G.GL_STATIC_DRAW)
        G.glBindVertexArray(0)
        G.glBindBuffer(G.GL_ARRAY_BUFFER, 0)
        count = m.idx.size
    }

    private val mvp = FloatArray(16)
    private val nrmM = FloatArray(9)

    private fun matrices(cam: Cam3D, w: Int, h: Int) {
        val proj = FloatArray(16)
        Matrix.perspectiveM(proj, 0, Cam3D.FOVY.toFloat(), w / h.toFloat().coerceAtLeast(1f), 0.05f, 60f)
        val view = FloatArray(16)
        Matrix.setIdentityM(view, 0)
        Matrix.translateM(view, 0, cam.px, cam.py, -cam.dist)
        val rot = FloatArray(16)
        Matrix.setIdentityM(rot, 0)
        Matrix.rotateM(rot, 0, Math.toDegrees(cam.pitch.toDouble()).toFloat(), 1f, 0f, 0f)
        Matrix.rotateM(rot, 0, Math.toDegrees(cam.yaw.toDouble()).toFloat(), 0f, 1f, 0f)
        val model = FloatArray(16)
        Matrix.setIdentityM(model, 0)
        Matrix.scaleM(model, 0, norm[3], norm[3], norm[3])
        Matrix.translateM(model, 0, -norm[0], -norm[1], -norm[2])
        val rm = FloatArray(16)
        Matrix.multiplyMM(rm, 0, rot, 0, model, 0)
        val mv = FloatArray(16)
        Matrix.multiplyMM(mv, 0, view, 0, rm, 0)
        Matrix.multiplyMM(mvp, 0, proj, 0, mv, 0)
        // Normal: putaran saja (skala seragam).
        nrmM[0] = rot[0]; nrmM[1] = rot[1]; nrmM[2] = rot[2]
        nrmM[3] = rot[4]; nrmM[4] = rot[5]; nrmM[5] = rot[6]
        nrmM[6] = rot[8]; nrmM[7] = rot[9]; nrmM[8] = rot[10]
    }

    private fun ensureView(w: Int, h: Int) {
        if (w == vw && h == vh && resolveFbo != 0) return
        freeView()
        vw = w; vh = h
        val a = IntArray(1)
        // Tekstur hasil (tanpa MSAA).
        G.glGenTextures(1, a, 0); resolveTex = a[0]
        G.glBindTexture(G.GL_TEXTURE_2D, resolveTex)
        G.glTexImage2D(G.GL_TEXTURE_2D, 0, G.GL_RGBA8, w, h, 0, G.GL_RGBA, G.GL_UNSIGNED_BYTE, null)
        G.glTexParameteri(G.GL_TEXTURE_2D, G.GL_TEXTURE_MIN_FILTER, G.GL_LINEAR)
        G.glTexParameteri(G.GL_TEXTURE_2D, G.GL_TEXTURE_MAG_FILTER, G.GL_LINEAR)
        G.glBindTexture(G.GL_TEXTURE_2D, 0)
        G.glGenFramebuffers(1, a, 0); resolveFbo = a[0]
        G.glBindFramebuffer(G.GL_FRAMEBUFFER, resolveFbo)
        G.glFramebufferTexture2D(G.GL_FRAMEBUFFER, G.GL_COLOR_ATTACHMENT0, G.GL_TEXTURE_2D, resolveTex, 0)
        // MSAA 4× bila didukung; kalau tidak, gambar langsung ke tekstur hasil dengan kedalaman biasa.
        val maxS = IntArray(1)
        G.glGetIntegerv(G.GL_MAX_SAMPLES, maxS, 0)
        samples = if (allowMsaa) minOf(4, maxS[0]) else 0
        G.glGenRenderbuffers(1, a, 0); msDepth = a[0]
        G.glBindRenderbuffer(G.GL_RENDERBUFFER, msDepth)
        if (samples >= 2) {
            G.glRenderbufferStorageMultisample(G.GL_RENDERBUFFER, samples, G.GL_DEPTH_COMPONENT24, w, h)
            G.glGenRenderbuffers(1, a, 0); msColor = a[0]
            G.glBindRenderbuffer(G.GL_RENDERBUFFER, msColor)
            G.glRenderbufferStorageMultisample(G.GL_RENDERBUFFER, samples, G.GL_RGBA8, w, h)
            G.glGenFramebuffers(1, a, 0); msFbo = a[0]
            G.glBindFramebuffer(G.GL_FRAMEBUFFER, msFbo)
            G.glFramebufferRenderbuffer(G.GL_FRAMEBUFFER, G.GL_COLOR_ATTACHMENT0, G.GL_RENDERBUFFER, msColor)
            G.glFramebufferRenderbuffer(G.GL_FRAMEBUFFER, G.GL_DEPTH_ATTACHMENT, G.GL_RENDERBUFFER, msDepth)
            if (G.glCheckFramebufferStatus(G.GL_FRAMEBUFFER) != G.GL_FRAMEBUFFER_COMPLETE) {
                G.glDeleteFramebuffers(1, intArrayOf(msFbo), 0); msFbo = 0
                G.glDeleteRenderbuffers(2, intArrayOf(msColor, msDepth), 0); msColor = 0
                G.glGenRenderbuffers(1, a, 0); msDepth = a[0]
                G.glBindRenderbuffer(G.GL_RENDERBUFFER, msDepth)
                samples = 0
            }
        }
        if (samples < 2) {
            G.glRenderbufferStorage(G.GL_RENDERBUFFER, G.GL_DEPTH_COMPONENT24, w, h)
            G.glBindFramebuffer(G.GL_FRAMEBUFFER, resolveFbo)
            G.glFramebufferRenderbuffer(G.GL_FRAMEBUFFER, G.GL_DEPTH_ATTACHMENT, G.GL_RENDERBUFFER, msDepth)
        }
        G.glBindRenderbuffer(G.GL_RENDERBUFFER, 0)
        G.glBindFramebuffer(G.GL_FRAMEBUFFER, 0)
    }

    private fun freeView() {
        if (msFbo != 0) G.glDeleteFramebuffers(1, intArrayOf(msFbo), 0)
        if (resolveFbo != 0) G.glDeleteFramebuffers(1, intArrayOf(resolveFbo), 0)
        if (msColor != 0) G.glDeleteRenderbuffers(1, intArrayOf(msColor), 0)
        if (msDepth != 0) G.glDeleteRenderbuffers(1, intArrayOf(msDepth), 0)
        if (resolveTex != 0) G.glDeleteTextures(1, intArrayOf(resolveTex), 0)
        msFbo = 0; resolveFbo = 0; msColor = 0; msDepth = 0; resolveTex = 0; vw = 0; vh = 0
    }

    private fun drawMesh(pickMode: Boolean, tex: Int) {
        prog.use()
        G.glUniformMatrix4fv(prog.loc("uMvp"), 1, false, mvp, 0)
        prog.m3("uNrm", nrmM)
        prog.i("uPick", if (pickMode) 1 else 0)
        prog.tex("uTex", 0, tex)
        G.glEnable(G.GL_DEPTH_TEST)
        G.glDepthFunc(G.GL_LEQUAL)
        G.glDisable(G.GL_BLEND)
        G.glDisable(G.GL_SCISSOR_TEST)
        G.glBindVertexArray(vao)
        G.glDrawElements(G.GL_TRIANGLES, count, G.GL_UNSIGNED_INT, 0)
        G.glBindVertexArray(0)
        G.glDisable(G.GL_DEPTH_TEST)
        G.glBindTexture(G.GL_TEXTURE_2D, 0)
    }

    /** Gambar model ke layar (framebuffer 0) berukuran [w]×[h], tekstur = [tex]. */
    fun draw(cam: Cam3D, w: Int, h: Int, tex: Int) {
        if (count == 0 || w <= 0 || h <= 0) return
        ensureView(w, h)
        matrices(cam, w, h)
        val target = if (samples >= 2) msFbo else resolveFbo
        G.glBindFramebuffer(G.GL_FRAMEBUFFER, target)
        G.glViewport(0, 0, w, h)
        G.glClearColor(0.16f, 0.16f, 0.18f, 1f)
        G.glClearDepthf(1f)
        G.glDepthMask(true)
        G.glClear(G.GL_COLOR_BUFFER_BIT or G.GL_DEPTH_BUFFER_BIT)
        drawMesh(false, tex)
        if (samples >= 2) {
            G.glBindFramebuffer(G.GL_READ_FRAMEBUFFER, msFbo)
            G.glBindFramebuffer(G.GL_DRAW_FRAMEBUFFER, resolveFbo)
            G.glBlitFramebuffer(0, 0, w, h, 0, 0, w, h, G.GL_COLOR_BUFFER_BIT, G.GL_NEAREST)
        }
        G.glBindFramebuffer(G.GL_READ_FRAMEBUFFER, resolveFbo)
        G.glBindFramebuffer(G.GL_DRAW_FRAMEBUFFER, 0)
        G.glBlitFramebuffer(0, 0, w, h, 0, 0, w, h, G.GL_COLOR_BUFFER_BIT, G.GL_NEAREST)
        G.glBindFramebuffer(G.GL_FRAMEBUFFER, 0)
    }

    /** Buat penyangga pilih untuk kamera [cam] (setengah resolusi layar) dan salin ke memori. */
    fun renderPick(cam: Cam3D, w: Int, h: Int) {
        if (count == 0 || w <= 0 || h <= 0) return
        val nw = (w / 2).coerceAtLeast(1)
        val nh = (h / 2).coerceAtLeast(1)
        if (nw != pw || nh != ph || pFbo == 0) {
            if (pFbo != 0) G.glDeleteFramebuffers(1, intArrayOf(pFbo), 0)
            if (pTex != 0) G.glDeleteTextures(1, intArrayOf(pTex), 0)
            if (pDepth != 0) G.glDeleteRenderbuffers(1, intArrayOf(pDepth), 0)
            pw = nw; ph = nh
            val a = IntArray(1)
            G.glGenTextures(1, a, 0); pTex = a[0]
            G.glBindTexture(G.GL_TEXTURE_2D, pTex)
            G.glTexImage2D(G.GL_TEXTURE_2D, 0, G.GL_RGBA8, pw, ph, 0, G.GL_RGBA, G.GL_UNSIGNED_BYTE, null)
            G.glTexParameteri(G.GL_TEXTURE_2D, G.GL_TEXTURE_MIN_FILTER, G.GL_NEAREST)
            G.glTexParameteri(G.GL_TEXTURE_2D, G.GL_TEXTURE_MAG_FILTER, G.GL_NEAREST)
            G.glBindTexture(G.GL_TEXTURE_2D, 0)
            G.glGenRenderbuffers(1, a, 0); pDepth = a[0]
            G.glBindRenderbuffer(G.GL_RENDERBUFFER, pDepth)
            G.glRenderbufferStorage(G.GL_RENDERBUFFER, G.GL_DEPTH_COMPONENT24, pw, ph)
            G.glBindRenderbuffer(G.GL_RENDERBUFFER, 0)
            G.glGenFramebuffers(1, a, 0); pFbo = a[0]
            G.glBindFramebuffer(G.GL_FRAMEBUFFER, pFbo)
            G.glFramebufferTexture2D(G.GL_FRAMEBUFFER, G.GL_COLOR_ATTACHMENT0, G.GL_TEXTURE_2D, pTex, 0)
            G.glFramebufferRenderbuffer(G.GL_FRAMEBUFFER, G.GL_DEPTH_ATTACHMENT, G.GL_RENDERBUFFER, pDepth)
            pickBufs[0] = null; pickBufs[1] = null
        }
        matrices(cam, w, h)
        G.glBindFramebuffer(G.GL_FRAMEBUFFER, pFbo)
        G.glViewport(0, 0, pw, ph)
        G.glClearColor(1f, 1f, 1f, 1f)
        G.glClearDepthf(1f)
        G.glDepthMask(true)
        G.glClear(G.GL_COLOR_BUFFER_BIT or G.GL_DEPTH_BUFFER_BIT)
        drawMesh(true, 0)
        pickFlip = 1 - pickFlip
        val buf = pickBufs[pickFlip] ?: ByteBuffer.allocateDirect(pw * ph * 4).order(ByteOrder.nativeOrder()).also { pickBufs[pickFlip] = it }
        buf.position(0)
        G.glPixelStorei(G.GL_PACK_ALIGNMENT, 1)
        G.glReadPixels(0, 0, pw, ph, G.GL_RGBA, G.GL_UNSIGNED_BYTE, buf)
        G.glPixelStorei(G.GL_PACK_ALIGNMENT, 4)
        G.glBindFramebuffer(G.GL_FRAMEBUFFER, 0)
        pick = PickSnap(buf, pw, ph, w, h)
        pickKey = Triple(cam, w * 100000 + h, meshSerial)
    }

    fun pickKeyFor(cam: Cam3D, w: Int, h: Int): Any = Triple(cam, w * 100000 + h, meshSerial)

    /** UV (0..1, v = 0 di bawah) di titik layar, null bila tidak mengenai model. Aman dari thread mana pun. */
    fun uvAt(sx: Float, sy: Float): FloatArray? {
        val p = pick ?: return null
        val x = (sx * p.w / p.sw).toInt().coerceIn(0, p.w - 1)
        val y = (p.h - 1 - (sy * p.h / p.sh).toInt()).coerceIn(0, p.h - 1)
        val o = (y * p.w + x) * 4
        val r = p.buf.get(o).toInt() and 0xFF
        val g = p.buf.get(o + 1).toInt() and 0xFF
        val b = p.buf.get(o + 2).toInt() and 0xFF
        val a = p.buf.get(o + 3).toInt() and 0xFF
        if (r == 255 && g == 255 && b == 255 && a == 255) return null
        return floatArrayOf((r * 256 + g) / 65534f, (b * 256 + a) / 65534f)
    }

    fun release() {
        setMesh(null)
        freeView()
        if (pFbo != 0) G.glDeleteFramebuffers(1, intArrayOf(pFbo), 0)
        if (pTex != 0) G.glDeleteTextures(1, intArrayOf(pTex), 0)
        if (pDepth != 0) G.glDeleteRenderbuffers(1, intArrayOf(pDepth), 0)
        pFbo = 0; pTex = 0; pDepth = 0; pw = 0; ph = 0
    }

    companion object {
        private const val VS = """#version 300 es
layout(location = 0) in vec3 aPos;
layout(location = 1) in vec3 aNrm;
layout(location = 2) in vec2 aUv;
uniform mat4 uMvp;
uniform mat3 uNrm;
out vec3 vN;
out vec2 vUv;
void main() {
    gl_Position = uMvp * vec4(aPos, 1.0);
    vN = uNrm * aNrm;
    vUv = aUv;
}
"""
        private const val FS = """#version 300 es
precision highp float;
in vec3 vN;
in vec2 vUv;
uniform sampler2D uTex;
uniform int uPick;
out vec4 o;
void main() {
    vec2 uv = fract(vUv);
    if (uPick == 1) {
        vec2 q = floor(uv * 65534.0 + 0.5);
        o = vec4(floor(q.x / 256.0) / 255.0, mod(q.x, 256.0) / 255.0, floor(q.y / 256.0) / 255.0, mod(q.y, 256.0) / 255.0);
        return;
    }
    // Baris 0 tekstur kanvas = atas gambar = v 1.
    vec4 c = texture(uTex, vec2(uv.x, 1.0 - uv.y));
    vec3 col = c.rgb + vec3(1.0) * (1.0 - c.a);
    vec3 n = normalize(vN);
    if (!gl_FrontFacing) n = -n;
    vec3 l = normalize(vec3(-0.35, 0.55, 0.76));
    float d = max(dot(n, l), 0.0);
    float rim = pow(1.0 - max(n.z, 0.0), 3.0) * 0.12;
    o = vec4(col * (0.42 + 0.58 * d) + rim, 1.0);
}
"""
    }
}
