package id.goresan.engine

import org.json.JSONArray
import org.json.JSONObject
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Model 3D untuk dilukis: posisi asli, normal, UV (v = 0 di bawah gambar, seperti OBJ), dan indeks segitiga.
 * Teksturnya = kanvas karya (ukuran kanvas = ukuran tekstur).
 */
class Mesh(val pos: FloatArray, val nrm: FloatArray, val uv: FloatArray, val idx: IntArray) {
    val vertices get() = pos.size / 3
    val triangles get() = idx.size / 3

    /** Pusat dan skala supaya model muat di bola berjari-jari 1. */
    fun bounds(): FloatArray {
        var x0 = Float.MAX_VALUE; var y0 = Float.MAX_VALUE; var z0 = Float.MAX_VALUE
        var x1 = -Float.MAX_VALUE; var y1 = -Float.MAX_VALUE; var z1 = -Float.MAX_VALUE
        for (i in 0 until vertices) {
            val x = pos[i * 3]; val y = pos[i * 3 + 1]; val z = pos[i * 3 + 2]
            x0 = min(x0, x); x1 = max(x1, x); y0 = min(y0, y); y1 = max(y1, y); z0 = min(z0, z); z1 = max(z1, z)
        }
        val cx = (x0 + x1) / 2; val cy = (y0 + y1) / 2; val cz = (z0 + z1) / 2
        var r = 0f
        for (i in 0 until vertices) {
            val dx = pos[i * 3] - cx; val dy = pos[i * 3 + 1] - cy; val dz = pos[i * 3 + 2] - cz
            r = max(r, dx * dx + dy * dy + dz * dz)
        }
        return floatArrayOf(cx, cy, cz, if (r > 0f) 1f / sqrt(r) else 1f)
    }

    fun write(f: File) {
        DataOutputStream(f.outputStream().buffered(1 shl 16)).use { o ->
            o.writeInt(0x574E4D31) // "WNM1"
            o.writeInt(vertices)
            o.writeInt(idx.size)
            val bb = ByteBuffer.allocate(max(pos.size, idx.size) * 4)
            fun floats(a: FloatArray) { bb.clear(); bb.asFloatBuffer().put(a); o.write(bb.array(), 0, a.size * 4) }
            floats(pos); floats(nrm); floats(uv)
            bb.clear(); bb.asIntBuffer().put(idx); o.write(bb.array(), 0, idx.size * 4)
        }
    }

    companion object {
        fun read(f: File): Mesh? = runCatching {
            DataInputStream(f.inputStream().buffered(1 shl 16)).use { inp ->
                if (inp.readInt() != 0x574E4D31) return@use null
                val nv = inp.readInt()
                val ni = inp.readInt()
                fun floats(n: Int): FloatArray {
                    val b = ByteArray(n * 4); inp.readFully(b)
                    val a = FloatArray(n); ByteBuffer.wrap(b).asFloatBuffer().get(a); return a
                }
                val pos = floats(nv * 3); val nrm = floats(nv * 3); val uv = floats(nv * 2)
                val b = ByteArray(ni * 4); inp.readFully(b)
                val idx = IntArray(ni); ByteBuffer.wrap(b).asIntBuffer().get(idx)
                Mesh(pos, nrm, uv, idx)
            }
        }.getOrNull()
    }
}

/** Hasil impor: model, gambar tekstur bawaan (PNG/JPG) atau nama berkasnya, dan warna dasar material. */
class ModelImport(val mesh: Mesh, val texture: ByteArray?, val textureName: String?, val baseColor: Int?, val autoUv: Boolean)

object ModelLoader {
    const val MAX_TRIS = 1_500_000

    // ================= OBJ =================

    /** [mtl]: isi berkas .mtl (bila ikut dipilih) untuk mencari map_Kd/Kd. */
    fun parseObj(text: String, mtl: String?): ModelImport {
        val vp = FloatList(); val vt = FloatList(); val vn = FloatList()
        val outPos = FloatList(); val outUv = FloatList(); val outNrm = FloatList(); val outIdx = IntList()
        val vertPosIndex = IntList() // indeks posisi asal tiap verteks keluaran (untuk normal halus)
        val map = HashMap<Long, Int>()
        var hasUv = false
        var hasNrm = true
        var firstMtl: String? = null
        val face = IntArray(3 * 64)
        for (raw in text.lineSequence()) {
            val line = raw.trim()
            if (line.isEmpty() || line[0] == '#') continue
            val parts = line.split(WS)
            when (parts[0]) {
                "v" -> { vp.add(parts.f(1)); vp.add(parts.f(2)); vp.add(parts.f(3)) }
                "vt" -> { vt.add(parts.f(1)); vt.add(if (parts.size > 2) parts.f(2) else 0f) }
                "vn" -> { vn.add(parts.f(1)); vn.add(parts.f(2)); vn.add(parts.f(3)) }
                "usemtl" -> if (firstMtl == null && parts.size > 1) firstMtl = parts[1]
                "f" -> {
                    val n = min(parts.size - 1, 64)
                    if (n < 3) continue
                    for (k in 0 until n) {
                        val c = parts[k + 1].split('/')
                        fun ix(s: String?, count: Int): Int {
                            if (s.isNullOrEmpty()) return -1
                            val v = s.toInt()
                            return if (v < 0) count + v else v - 1
                        }
                        face[k * 3] = ix(c[0], vp.size / 3)
                        face[k * 3 + 1] = ix(c.getOrNull(1), vt.size / 2)
                        face[k * 3 + 2] = ix(c.getOrNull(2), vn.size / 3)
                    }
                    val ids = IntArray(n)
                    for (k in 0 until n) {
                        val pi = face[k * 3]; val ti = face[k * 3 + 1]; val ni = face[k * 3 + 2]
                        if (ti >= 0) hasUv = true
                        if (ni < 0) hasNrm = false
                        val key = (pi.toLong() shl 42) or ((ti + 1).toLong() shl 21) or (ni + 1).toLong()
                        ids[k] = map.getOrPut(key) {
                            val id = outPos.size / 3
                            outPos.add(vp[pi * 3]); outPos.add(vp[pi * 3 + 1]); outPos.add(vp[pi * 3 + 2])
                            if (ti >= 0) { outUv.add(vt[ti * 2]); outUv.add(vt[ti * 2 + 1]) } else { outUv.add(0f); outUv.add(0f) }
                            if (ni >= 0) { outNrm.add(vn[ni * 3]); outNrm.add(vn[ni * 3 + 1]); outNrm.add(vn[ni * 3 + 2]) } else { outNrm.add(0f); outNrm.add(0f); outNrm.add(0f) }
                            vertPosIndex.add(pi)
                            id
                        }
                    }
                    for (k in 1 until n - 1) { outIdx.add(ids[0]); outIdx.add(ids[k]); outIdx.add(ids[k + 1]) }
                    if (outIdx.size / 3 > MAX_TRIS) error("Model terlalu besar (lebih dari ${MAX_TRIS / 1000} ribu segitiga)")
                }
            }
        }
        if (outIdx.size == 0) error("Berkas OBJ tidak berisi permukaan (f)")
        var mesh = Mesh(outPos.toArray(), outNrm.toArray(), outUv.toArray(), outIdx.toArray())
        if (!hasNrm) smoothNormals(mesh, vertPosIndex.toArray(), vp.size / 3)
        var auto = false
        if (!hasUv) { mesh = boxUv(mesh, creases = !hasNrm); auto = true }
        // Material: tekstur (map_Kd) dan warna dasar (Kd) material pertama yang dipakai.
        var texName: String? = null
        var base: Int? = null
        if (mtl != null) {
            var cur: String? = null
            for (raw in mtl.lineSequence()) {
                val p = raw.trim().split(WS)
                when (p[0]) {
                    "newmtl" -> cur = p.getOrNull(1)
                    "map_Kd" -> if ((firstMtl == null || cur == firstMtl) && texName == null) texName = p.last().substringAfterLast('/').substringAfterLast('\\')
                    "Kd" -> if ((firstMtl == null || cur == firstMtl) && base == null && p.size >= 4) {
                        base = rgb(p.f(1), p.f(2), p.f(3))
                    }
                }
            }
        }
        return ModelImport(mesh, null, texName, base, auto)
    }

    private fun List<String>.f(i: Int): Float = getOrNull(i)?.toFloatOrNull() ?: 0f
    private val WS = Regex("\\s+")
    private fun rgb(r: Float, g: Float, b: Float): Int =
        (0xFF shl 24) or ((r.coerceIn(0f, 1f) * 255).toInt() shl 16) or ((g.coerceIn(0f, 1f) * 255).toInt() shl 8) or (b.coerceIn(0f, 1f) * 255).toInt()

    /** Normal halus: rata-rata normal segitiga per posisi asal. */
    private fun smoothNormals(m: Mesh, posIndex: IntArray, nPos: Int) {
        val acc = FloatArray(nPos * 3)
        for (t in 0 until m.triangles) {
            val a = m.idx[t * 3]; val b = m.idx[t * 3 + 1]; val c = m.idx[t * 3 + 2]
            val n = faceNormal(m.pos, a, b, c)
            for (v in intArrayOf(a, b, c)) {
                val p = posIndex[v]
                acc[p * 3] += n[0]; acc[p * 3 + 1] += n[1]; acc[p * 3 + 2] += n[2]
            }
        }
        for (v in 0 until m.vertices) {
            val p = posIndex[v]
            val x = acc[p * 3]; val y = acc[p * 3 + 1]; val z = acc[p * 3 + 2]
            val l = sqrt(x * x + y * y + z * z).takeIf { it > 0f } ?: 1f
            m.nrm[v * 3] = x / l; m.nrm[v * 3 + 1] = y / l; m.nrm[v * 3 + 2] = z / l
        }
    }

    private fun faceNormal(p: FloatArray, a: Int, b: Int, c: Int): FloatArray {
        val ux = p[b * 3] - p[a * 3]; val uy = p[b * 3 + 1] - p[a * 3 + 1]; val uz = p[b * 3 + 2] - p[a * 3 + 2]
        val vx = p[c * 3] - p[a * 3]; val vy = p[c * 3 + 1] - p[a * 3 + 1]; val vz = p[c * 3 + 2] - p[a * 3 + 2]
        return floatArrayOf(uy * vz - uz * vy, uz * vx - ux * vz, ux * vy - uy * vx)
    }

    /**
     * Model tanpa UV: proyeksi kotak otomatis. Tiap segitiga masuk ke salah satu dari 6 kotak (3 × 2) menurut arah
     * normalnya, diproyeksikan tegak lurus. Bagian yang menghadap arah sama bisa berbagi piksel tekstur.
     */
    private fun boxUv(m: Mesh, creases: Boolean): Mesh {
        val b = m.bounds()
        val n = m.triangles
        val pos = FloatArray(n * 9); val nrm = FloatArray(n * 9); val uv = FloatArray(n * 6); val idx = IntArray(n * 3)
        for (t in 0 until n) {
            val fn = faceNormal(m.pos, m.idx[t * 3], m.idx[t * 3 + 1], m.idx[t * 3 + 2])
            val ax = abs(fn[0]); val ay = abs(fn[1]); val az = abs(fn[2])
            val axis = if (ax >= ay && ax >= az) 0 else if (ay >= az) 1 else 2
            val neg = fn[axis] < 0
            val cell = axis * 2 + if (neg) 1 else 0
            val cx = (cell % 3) / 3f; val cy = (cell / 3) / 2f
            for (k in 0 until 3) {
                val v = m.idx[t * 3 + k]
                val o = t * 3 + k
                for (j in 0 until 3) { pos[o * 3 + j] = m.pos[v * 3 + j]; nrm[o * 3 + j] = m.nrm[v * 3 + j] }
                // Koordinat ternormalisasi -1..1 lalu ke 0..1 di dalam kotaknya (dengan tepi 2%).
                val x = (m.pos[v * 3] - b[0]) * b[3]; val y = (m.pos[v * 3 + 1] - b[1]) * b[3]; val z = (m.pos[v * 3 + 2] - b[2]) * b[3]
                val (s, r) = when (axis) { 0 -> (if (neg) z else -z) to y; 1 -> x to (if (neg) z else -z); else -> (if (neg) -x else x) to y }
                uv[o * 2] = cx + (0.02f + 0.96f * (s * 0.5f + 0.5f)) / 3f
                uv[o * 2 + 1] = cy + (0.02f + 0.96f * (r * 0.5f + 0.5f)) / 2f
                idx[o] = o
            }
        }
        if (creases && n <= 400_000) creaseNormals(pos, nrm, n)
        return Mesh(pos, nrm, uv, idx)
    }

    /**
     * Normal bersudut (sudut lipat 50°): tiap sudut segitiga merata-ratakan normal segitiga di posisi yang sama yang
     * arahnya dekat, sehingga tepi tajam (kubus) tetap tajam dan permukaan lengkung tetap halus.
     */
    private fun creaseNormals(pos: FloatArray, nrm: FloatArray, n: Int) {
        val fn = FloatArray(n * 3)
        for (t in 0 until n) {
            val f = faceNormal(pos, t * 3, t * 3 + 1, t * 3 + 2)
            val l = sqrt(f[0] * f[0] + f[1] * f[1] + f[2] * f[2]).takeIf { it > 0f } ?: 1f
            fn[t * 3] = f[0] / l; fn[t * 3 + 1] = f[1] / l; fn[t * 3 + 2] = f[2] / l
        }
        val at = HashMap<Long, IntList>()
        fun key(v: Int): Long {
            val q = 1e5f
            val x = (pos[v * 3] * q).toLong(); val y = (pos[v * 3 + 1] * q).toLong(); val z = (pos[v * 3 + 2] * q).toLong()
            return (x * 73856093L) xor (y * 19349663L) xor (z * 83492791L)
        }
        for (v in 0 until n * 3) at.getOrPut(key(v)) { IntList() }.add(v / 3)
        val cosLim = 0.64f
        for (v in 0 until n * 3) {
            val t = v / 3
            var x = 0f; var y = 0f; var z = 0f
            val list = at[key(v)]!!
            for (i in 0 until list.size) {
                val o = list[i]
                val d = fn[t * 3] * fn[o * 3] + fn[t * 3 + 1] * fn[o * 3 + 1] + fn[t * 3 + 2] * fn[o * 3 + 2]
                if (d >= cosLim) { x += fn[o * 3]; y += fn[o * 3 + 1]; z += fn[o * 3 + 2] }
            }
            val l = sqrt(x * x + y * y + z * z).takeIf { it > 0f } ?: 1f
            nrm[v * 3] = x / l; nrm[v * 3 + 1] = y / l; nrm[v * 3 + 2] = z / l
        }
    }

    // ================= GLB (glTF 2.0 biner) =================

    fun parseGlb(bytes: ByteArray): ModelImport {
        val bb = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        if (bytes.size < 20 || bb.getInt(0) != 0x46546C67) error("Bukan berkas GLB")
        var off = 12
        var json: JSONObject? = null
        var bin: ByteBuffer? = null
        while (off + 8 <= bytes.size) {
            val len = bb.getInt(off)
            val type = bb.getInt(off + 4)
            if (type == 0x4E4F534A) json = JSONObject(String(bytes, off + 8, len, Charsets.UTF_8))
            if (type == 0x004E4942) bin = ByteBuffer.wrap(bytes, off + 8, len).slice().order(ByteOrder.LITTLE_ENDIAN)
            off += 8 + len
        }
        val j = json ?: error("GLB tanpa JSON")
        val data = bin ?: error("GLB tanpa data biner")
        val accessors = j.optJSONArray("accessors") ?: JSONArray()
        val views = j.optJSONArray("bufferViews") ?: JSONArray()
        val meshes = j.optJSONArray("meshes") ?: error("GLB tanpa mesh")
        val nodes = j.optJSONArray("nodes") ?: JSONArray()

        fun read(ai: Int, comps: Int): FloatArray {
            val a = accessors.getJSONObject(ai)
            val count = a.getInt("count")
            val out = FloatArray(count * comps)
            if (!a.has("bufferView")) return out
            val v = views.getJSONObject(a.getInt("bufferView"))
            val ct = a.getInt("componentType")
            val size = when (ct) { 5126, 5125 -> 4; 5123, 5122 -> 2; else -> 1 }
            val stride = v.optInt("byteStride", 0).takeIf { it > 0 } ?: (size * comps)
            val base = v.optInt("byteOffset", 0) + a.optInt("byteOffset", 0)
            val norm = a.optBoolean("normalized", false)
            for (i in 0 until count) for (c in 0 until comps) {
                val p = base + i * stride + c * size
                out[i * comps + c] = when (ct) {
                    5126 -> data.getFloat(p)
                    5125 -> data.getInt(p).toLong().and(0xFFFFFFFFL).toFloat()
                    5123 -> (data.getShort(p).toInt() and 0xFFFF).let { if (norm) it / 65535f else it.toFloat() }
                    5122 -> data.getShort(p).toInt().let { if (norm) max(it / 32767f, -1f) else it.toFloat() }
                    5121 -> (data.get(p).toInt() and 0xFF).let { if (norm) it / 255f else it.toFloat() }
                    else -> data.get(p).toInt().let { if (norm) max(it / 127f, -1f) else it.toFloat() }
                }
            }
            return out
        }
        fun readIdx(ai: Int): IntArray {
            val a = accessors.getJSONObject(ai)
            val count = a.getInt("count")
            val v = views.getJSONObject(a.getInt("bufferView"))
            val ct = a.getInt("componentType")
            val base = v.optInt("byteOffset", 0) + a.optInt("byteOffset", 0)
            return IntArray(count) { i ->
                when (ct) {
                    5125 -> data.getInt(base + i * 4)
                    5123 -> data.getShort(base + i * 2).toInt() and 0xFFFF
                    else -> data.get(base + i).toInt() and 0xFF
                }
            }
        }

        val outPos = FloatList(); val outNrm = FloatList(); val outUv = FloatList(); val outIdx = IntList()
        var material = -1
        var anyUv = false
        fun addMesh(mi: Int, m: FloatArray) {
            val prims = meshes.getJSONObject(mi).optJSONArray("primitives") ?: return
            for (pi in 0 until prims.length()) {
                val p = prims.getJSONObject(pi)
                if (p.optInt("mode", 4) != 4) continue
                val at = p.getJSONObject("attributes")
                if (!at.has("POSITION")) continue
                val pos = read(at.getInt("POSITION"), 3)
                val n = pos.size / 3
                val nrm = if (at.has("NORMAL")) read(at.getInt("NORMAL"), 3) else null
                val uv = if (at.has("TEXCOORD_0")) read(at.getInt("TEXCOORD_0"), 2).also { anyUv = true } else FloatArray(n * 2)
                if (material < 0 && p.has("material")) material = p.getInt("material")
                val base = outPos.size / 3
                for (i in 0 until n) {
                    val x = pos[i * 3]; val y = pos[i * 3 + 1]; val z = pos[i * 3 + 2]
                    outPos.add(m[0] * x + m[4] * y + m[8] * z + m[12])
                    outPos.add(m[1] * x + m[5] * y + m[9] * z + m[13])
                    outPos.add(m[2] * x + m[6] * y + m[10] * z + m[14])
                    if (nrm != null) {
                        val nx = nrm[i * 3]; val ny = nrm[i * 3 + 1]; val nz = nrm[i * 3 + 2]
                        var tx = m[0] * nx + m[4] * ny + m[8] * nz
                        var ty = m[1] * nx + m[5] * ny + m[9] * nz
                        var tz = m[2] * nx + m[6] * ny + m[10] * nz
                        val l = sqrt(tx * tx + ty * ty + tz * tz).takeIf { it > 0f } ?: 1f
                        tx /= l; ty /= l; tz /= l
                        outNrm.add(tx); outNrm.add(ty); outNrm.add(tz)
                    } else { outNrm.add(0f); outNrm.add(0f); outNrm.add(0f) }
                    // glTF: v = 0 di atas gambar; di sini v = 0 di bawah (seperti OBJ).
                    outUv.add(uv[i * 2]); outUv.add(1f - uv[i * 2 + 1])
                }
                val ids = if (p.has("indices")) readIdx(p.getInt("indices")) else IntArray(n) { it }
                for (k in ids) outIdx.add(base + k)
                if (outIdx.size / 3 > MAX_TRIS) error("Model terlalu besar (lebih dari ${MAX_TRIS / 1000} ribu segitiga)")
            }
        }
        fun local(nd: JSONObject): FloatArray {
            nd.optJSONArray("matrix")?.let { a -> return FloatArray(16) { a.getDouble(it).toFloat() } }
            val t = nd.optJSONArray("translation"); val r = nd.optJSONArray("rotation"); val s = nd.optJSONArray("scale")
            val tx = t?.getDouble(0)?.toFloat() ?: 0f; val ty = t?.getDouble(1)?.toFloat() ?: 0f; val tz = t?.getDouble(2)?.toFloat() ?: 0f
            val qx = r?.getDouble(0)?.toFloat() ?: 0f; val qy = r?.getDouble(1)?.toFloat() ?: 0f
            val qz = r?.getDouble(2)?.toFloat() ?: 0f; val qw = r?.getDouble(3)?.toFloat() ?: 1f
            val sx = s?.getDouble(0)?.toFloat() ?: 1f; val sy = s?.getDouble(1)?.toFloat() ?: 1f; val sz = s?.getDouble(2)?.toFloat() ?: 1f
            return floatArrayOf(
                (1 - 2 * (qy * qy + qz * qz)) * sx, (2 * (qx * qy + qz * qw)) * sx, (2 * (qx * qz - qy * qw)) * sx, 0f,
                (2 * (qx * qy - qz * qw)) * sy, (1 - 2 * (qx * qx + qz * qz)) * sy, (2 * (qy * qz + qx * qw)) * sy, 0f,
                (2 * (qx * qz + qy * qw)) * sz, (2 * (qy * qz - qx * qw)) * sz, (1 - 2 * (qx * qx + qy * qy)) * sz, 0f,
                tx, ty, tz, 1f,
            )
        }
        fun mul(a: FloatArray, b: FloatArray): FloatArray {
            val o = FloatArray(16)
            android.opengl.Matrix.multiplyMM(o, 0, a, 0, b, 0)
            return o
        }
        fun visit(ni: Int, parent: FloatArray, depth: Int) {
            if (depth > 64) return
            val nd = nodes.getJSONObject(ni)
            val m = mul(parent, local(nd))
            if (nd.has("mesh")) addMesh(nd.getInt("mesh"), m)
            nd.optJSONArray("children")?.let { c -> for (i in 0 until c.length()) visit(c.getInt(i), m, depth + 1) }
        }
        val ident = FloatArray(16).also { android.opengl.Matrix.setIdentityM(it, 0) }
        val scenes = j.optJSONArray("scenes")
        if (scenes != null && scenes.length() > 0 && nodes.length() > 0) {
            val roots = scenes.getJSONObject(j.optInt("scene", 0).coerceIn(0, scenes.length() - 1)).optJSONArray("nodes") ?: JSONArray()
            for (i in 0 until roots.length()) visit(roots.getInt(i), ident, 0)
        } else {
            for (i in 0 until meshes.length()) addMesh(i, ident)
        }
        if (outIdx.size == 0) error("GLB tidak berisi segitiga")
        val hasNrm = outNrm.toArray().any { it != 0f }
        var mesh = Mesh(outPos.toArray(), outNrm.toArray(), outUv.toArray(), outIdx.toArray())
        if (!hasNrm) smoothNormals(mesh, IntArray(mesh.vertices) { it }, mesh.vertices)
        var auto = false
        if (!anyUv) { mesh = boxUv(mesh, creases = !hasNrm); auto = true }
        // Tekstur warna dasar material pertama.
        var tex: ByteArray? = null
        var base: Int? = null
        if (material >= 0) {
            val mat = j.optJSONArray("materials")?.optJSONObject(material)
            val pbr = mat?.optJSONObject("pbrMetallicRoughness")
            pbr?.optJSONArray("baseColorFactor")?.let { f ->
                base = rgb(f.optDouble(0, 1.0).toFloat(), f.optDouble(1, 1.0).toFloat(), f.optDouble(2, 1.0).toFloat())
            }
            val ti = pbr?.optJSONObject("baseColorTexture")?.optInt("index", -1) ?: -1
            if (ti >= 0) {
                val src = j.optJSONArray("textures")?.optJSONObject(ti)?.optInt("source", -1) ?: -1
                val img = if (src >= 0) j.optJSONArray("images")?.optJSONObject(src) else null
                if (img != null && img.has("bufferView")) {
                    val v = views.getJSONObject(img.getInt("bufferView"))
                    val o = v.optInt("byteOffset", 0)
                    val l = v.getInt("byteLength")
                    tex = ByteArray(l).also { data.position(o); data.get(it); data.position(0) }
                } else if (img != null && img.optString("uri").startsWith("data:")) {
                    val u = img.getString("uri")
                    tex = android.util.Base64.decode(u.substringAfter(","), android.util.Base64.DEFAULT)
                }
            }
        }
        return ModelImport(mesh, tex, null, base, auto)
    }

    // ================= tulis GLB =================

    /** Simpan model + tekstur (PNG) sebagai satu berkas GLB. */
    fun writeGlb(out: OutputStream, m: Mesh, png: ByteArray, name: String) {
        val nv = m.vertices
        fun pad4(n: Int) = (n + 3) / 4 * 4
        val posLen = nv * 12; val nrmLen = nv * 12; val uvLen = nv * 8; val idxLen = m.idx.size * 4
        val oPos = 0; val oNrm = oPos + posLen; val oUv = oNrm + nrmLen; val oIdx = oUv + uvLen; val oImg = oIdx + idxLen
        val binLen = pad4(oImg + png.size)
        val bin = ByteBuffer.allocate(binLen).order(ByteOrder.LITTLE_ENDIAN)
        var x0 = Float.MAX_VALUE; var y0 = Float.MAX_VALUE; var z0 = Float.MAX_VALUE
        var x1 = -Float.MAX_VALUE; var y1 = -Float.MAX_VALUE; var z1 = -Float.MAX_VALUE
        for (i in 0 until nv) {
            val x = m.pos[i * 3]; val y = m.pos[i * 3 + 1]; val z = m.pos[i * 3 + 2]
            bin.putFloat(x); bin.putFloat(y); bin.putFloat(z)
            x0 = min(x0, x); x1 = max(x1, x); y0 = min(y0, y); y1 = max(y1, y); z0 = min(z0, z); z1 = max(z1, z)
        }
        for (v in m.nrm) bin.putFloat(v)
        for (i in 0 until nv) { bin.putFloat(m.uv[i * 2]); bin.putFloat(1f - m.uv[i * 2 + 1]) }
        for (v in m.idx) bin.putInt(v)
        bin.put(png)
        val json = JSONObject().apply {
            put("asset", JSONObject().put("version", "2.0").put("generator", "WInote"))
            put("scene", 0)
            put("scenes", JSONArray().put(JSONObject().put("nodes", JSONArray().put(0))))
            put("nodes", JSONArray().put(JSONObject().put("mesh", 0).put("name", name)))
            put("meshes", JSONArray().put(JSONObject().put("name", name).put("primitives", JSONArray().put(JSONObject()
                .put("attributes", JSONObject().put("POSITION", 0).put("NORMAL", 1).put("TEXCOORD_0", 2))
                .put("indices", 3).put("material", 0)))))
            put("materials", JSONArray().put(JSONObject()
                .put("name", name)
                .put("doubleSided", true)
                .put("pbrMetallicRoughness", JSONObject()
                    .put("baseColorTexture", JSONObject().put("index", 0))
                    .put("metallicFactor", 0).put("roughnessFactor", 1))))
            put("samplers", JSONArray().put(JSONObject().put("magFilter", 9729).put("minFilter", 9987).put("wrapS", 10497).put("wrapT", 10497)))
            put("textures", JSONArray().put(JSONObject().put("sampler", 0).put("source", 0)))
            put("images", JSONArray().put(JSONObject().put("bufferView", 4).put("mimeType", "image/png")))
            fun view(o: Int, l: Int, target: Int?) = JSONObject().put("buffer", 0).put("byteOffset", o).put("byteLength", l).apply { if (target != null) put("target", target) }
            put("bufferViews", JSONArray()
                .put(view(oPos, posLen, 34962)).put(view(oNrm, nrmLen, 34962)).put(view(oUv, uvLen, 34962))
                .put(view(oIdx, idxLen, 34963)).put(view(oImg, png.size, null)))
            fun acc(v: Int, type: String, ct: Int, count: Int) = JSONObject().put("bufferView", v).put("componentType", ct).put("count", count).put("type", type)
            put("accessors", JSONArray()
                .put(acc(0, "VEC3", 5126, nv).put("min", JSONArray().put(x0.toDouble()).put(y0.toDouble()).put(z0.toDouble()))
                    .put("max", JSONArray().put(x1.toDouble()).put(y1.toDouble()).put(z1.toDouble())))
                .put(acc(1, "VEC3", 5126, nv)).put(acc(2, "VEC2", 5126, nv)).put(acc(3, "SCALAR", 5125, m.idx.size)))
            put("buffers", JSONArray().put(JSONObject().put("byteLength", binLen)))
        }
        var js = json.toString().toByteArray(Charsets.UTF_8)
        val jl = pad4(js.size)
        if (jl != js.size) js = js + ByteArray(jl - js.size) { 0x20 }
        val total = 12 + 8 + jl + 8 + binLen
        val head = ByteBuffer.allocate(28).order(ByteOrder.LITTLE_ENDIAN)
        head.putInt(0x46546C67); head.putInt(2); head.putInt(total)
        head.putInt(jl); head.putInt(0x4E4F534A)
        out.write(head.array(), 0, 20)
        out.write(js)
        val bh = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN)
        bh.putInt(binLen); bh.putInt(0x004E4942)
        out.write(bh.array())
        out.write(bin.array(), 0, binLen)
    }
}

/** Daftar float/int yang bisa bertambah tanpa boxing. */
class FloatList {
    private var a = FloatArray(1024)
    var size = 0
        private set
    fun add(v: Float) { if (size == a.size) a = a.copyOf(size * 2); a[size++] = v }
    operator fun get(i: Int) = a[i]
    fun toArray(): FloatArray = a.copyOf(size)
}

class IntList {
    private var a = IntArray(1024)
    var size = 0
        private set
    fun add(v: Int) { if (size == a.size) a = a.copyOf(size * 2); a[size++] = v }
    operator fun get(i: Int) = a[i]
    fun toArray(): IntArray = a.copyOf(size)
}
