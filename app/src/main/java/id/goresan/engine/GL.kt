package id.goresan.engine

import android.opengl.GLES30 as G
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/** Persegi panjang bilangan bulat (piksel kanvas), x1/y1 eksklusif. */
class IRect(var x0: Int = 0, var y0: Int = 0, var x1: Int = 0, var y1: Int = 0) {
    val w: Int get() = x1 - x0
    val h: Int get() = y1 - y0
    val isEmpty: Boolean get() = x1 <= x0 || y1 <= y0

    fun set(o: IRect): IRect { x0 = o.x0; y0 = o.y0; x1 = o.x1; y1 = o.y1; return this }
    fun set(a: Int, b: Int, c: Int, d: Int): IRect { x0 = a; y0 = b; x1 = c; y1 = d; return this }
    fun clear() { x0 = 0; y0 = 0; x1 = 0; y1 = 0 }

    fun union(o: IRect) {
        if (o.isEmpty) return
        if (isEmpty) { set(o); return }
        x0 = min(x0, o.x0); y0 = min(y0, o.y0); x1 = max(x1, o.x1); y1 = max(y1, o.y1)
    }

    fun unionF(fx0: Float, fy0: Float, fx1: Float, fy1: Float) {
        union(IRect(floor(fx0).toInt(), floor(fy0).toInt(), ceil(fx1).toInt(), ceil(fy1).toInt()))
    }

    fun clampTo(w: Int, h: Int): IRect {
        x0 = x0.coerceIn(0, w); x1 = x1.coerceIn(0, w)
        y0 = y0.coerceIn(0, h); y1 = y1.coerceIn(0, h)
        return this
    }

    fun inset(d: Int): IRect { x0 -= d; y0 -= d; x1 += d; y1 += d; return this }
    fun copy() = IRect(x0, y0, x1, y1)
    override fun toString() = "[$x0,$y0 - $x1,$y1]"
}

/** Tekstur (RGBA8 premultiplied atau R8) yang biasanya sekaligus punya framebuffer sendiri. */
class Tex(val w: Int, val h: Int, val single: Boolean = false, repeat: Boolean = false, withFbo: Boolean = true) {
    val id: Int
    val fb: Int

    init {
        val a = IntArray(1)
        G.glGenTextures(1, a, 0)
        id = a[0]
        G.glBindTexture(G.GL_TEXTURE_2D, id)
        G.glPixelStorei(G.GL_UNPACK_ALIGNMENT, 1)
        if (single) {
            G.glTexImage2D(G.GL_TEXTURE_2D, 0, G.GL_R8, w, h, 0, G.GL_RED, G.GL_UNSIGNED_BYTE, null)
        } else {
            G.glTexImage2D(G.GL_TEXTURE_2D, 0, G.GL_RGBA8, w, h, 0, G.GL_RGBA, G.GL_UNSIGNED_BYTE, null)
        }
        val err = G.glGetError()
        if (err == G.GL_OUT_OF_MEMORY) {
            G.glDeleteTextures(1, a, 0)
            throw OutOfMemoryError("Memori grafis penuh")
        }
        G.glTexParameteri(G.GL_TEXTURE_2D, G.GL_TEXTURE_MIN_FILTER, G.GL_LINEAR)
        G.glTexParameteri(G.GL_TEXTURE_2D, G.GL_TEXTURE_MAG_FILTER, G.GL_LINEAR)
        val wrap = if (repeat) G.GL_REPEAT else G.GL_CLAMP_TO_EDGE
        G.glTexParameteri(G.GL_TEXTURE_2D, G.GL_TEXTURE_WRAP_S, wrap)
        G.glTexParameteri(G.GL_TEXTURE_2D, G.GL_TEXTURE_WRAP_T, wrap)
        if (withFbo) {
            G.glGenFramebuffers(1, a, 0)
            fb = a[0]
            G.glBindFramebuffer(G.GL_FRAMEBUFFER, fb)
            G.glFramebufferTexture2D(G.GL_FRAMEBUFFER, G.GL_COLOR_ATTACHMENT0, G.GL_TEXTURE_2D, id, 0)
            val st = G.glCheckFramebufferStatus(G.GL_FRAMEBUFFER)
            if (st != G.GL_FRAMEBUFFER_COMPLETE) {
                G.glBindFramebuffer(G.GL_FRAMEBUFFER, 0)
                G.glDeleteFramebuffers(1, intArrayOf(fb), 0)
                G.glDeleteTextures(1, intArrayOf(id), 0)
                throw OutOfMemoryError("Framebuffer gagal ($st)")
            }
            G.glViewport(0, 0, w, h)
            G.glDisable(G.GL_SCISSOR_TEST)
            G.glClearColor(0f, 0f, 0f, 0f)
            G.glClear(G.GL_COLOR_BUFFER_BIT)
            G.glBindFramebuffer(G.GL_FRAMEBUFFER, 0)
        } else {
            fb = 0
        }
    }

    val bytes: Long get() = w.toLong() * h * (if (single) 1 else 4)

    fun delete() {
        G.glDeleteTextures(1, intArrayOf(id), 0)
        if (fb != 0) G.glDeleteFramebuffers(1, intArrayOf(fb), 0)
    }
}

class Program(vs: String, fs: String) {
    val id: Int
    private val locs = HashMap<String, Int>()

    init {
        val v = compile(G.GL_VERTEX_SHADER, vs)
        val f = compile(G.GL_FRAGMENT_SHADER, fs)
        id = G.glCreateProgram()
        G.glAttachShader(id, v)
        G.glAttachShader(id, f)
        G.glLinkProgram(id)
        val ok = IntArray(1)
        G.glGetProgramiv(id, G.GL_LINK_STATUS, ok, 0)
        if (ok[0] == 0) throw RuntimeException("Shader gagal ditautkan: " + G.glGetProgramInfoLog(id))
        G.glDeleteShader(v)
        G.glDeleteShader(f)
    }

    fun use() = G.glUseProgram(id)
    fun loc(n: String): Int = locs.getOrPut(n) { G.glGetUniformLocation(id, n) }
    fun i(n: String, v: Int) = G.glUniform1i(loc(n), v)
    fun f(n: String, v: Float) = G.glUniform1f(loc(n), v)
    fun f2(n: String, a: Float, b: Float) = G.glUniform2f(loc(n), a, b)
    fun f3(n: String, a: Float, b: Float, c: Float) = G.glUniform3f(loc(n), a, b, c)
    fun f4(n: String, a: Float, b: Float, c: Float, d: Float) = G.glUniform4f(loc(n), a, b, c, d)
    fun m3(n: String, m: FloatArray) = G.glUniformMatrix3fv(loc(n), 1, false, m, 0)
    fun fv(n: String, v: FloatArray) = G.glUniform1fv(loc(n), v.size, v, 0)

    fun tex(n: String, unit: Int, texId: Int) {
        G.glActiveTexture(G.GL_TEXTURE0 + unit)
        G.glBindTexture(G.GL_TEXTURE_2D, texId)
        i(n, unit)
    }

    companion object {
        fun compile(type: Int, src: String): Int {
            val s = G.glCreateShader(type)
            G.glShaderSource(s, src)
            G.glCompileShader(s)
            val ok = IntArray(1)
            G.glGetShaderiv(s, G.GL_COMPILE_STATUS, ok, 0)
            if (ok[0] == 0) {
                val log = G.glGetShaderInfoLog(s)
                G.glDeleteShader(s)
                throw RuntimeException("Shader gagal: $log")
            }
            return s
        }
    }
}

object Buffers {
    fun direct(bytes: Int): ByteBuffer = ByteBuffer.allocateDirect(bytes).order(ByteOrder.nativeOrder())
}

/** Matriks Android (baris) → matriks GLSL (kolom). */
fun colMajor(v: FloatArray, out: FloatArray = FloatArray(9)): FloatArray {
    out[0] = v[0]; out[1] = v[3]; out[2] = v[6]
    out[3] = v[1]; out[4] = v[4]; out[5] = v[7]
    out[6] = v[2]; out[7] = v[5]; out[8] = v[8]
    return out
}
