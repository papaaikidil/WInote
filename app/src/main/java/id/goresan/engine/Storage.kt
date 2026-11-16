package id.goresan.engine

import android.content.Context
import android.graphics.Bitmap
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.util.UUID
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.zip.Deflater
import java.util.zip.DeflaterOutputStream
import java.util.zip.Inflater
import java.util.zip.InflaterInputStream

/**
 * Penyimpanan karya di memori internal aplikasi:
 *   karya/<id>/doc.json   — nama, ukuran, latar, dan daftar layer
 *   karya/<id>/L<n>.lyr   — piksel tiap layer (RGBA premultiplied, dipadatkan)
 *   karya/<id>/thumb.png  — gambar kecil untuk galeri
 * Semua tulis/baca berjalan berurutan di satu thread, jadi muat ulang selalu melihat simpanan terakhir.
 */
object Storage {
    val io: ExecutorService = Executors.newSingleThreadExecutor { r -> Thread(r, "goresan-io") }

    data class ArtInfo(val id: String, val name: String, val w: Int, val h: Int, val modified: Long, val thumb: File)

    fun root(ctx: Context): File = File(ctx.filesDir, "karya").apply { mkdirs() }
    fun dir(ctx: Context, id: String): File = File(root(ctx), id)

    fun list(ctx: Context): List<ArtInfo> {
        val out = ArrayList<ArtInfo>()
        root(ctx).listFiles()?.forEach { d ->
            val meta = readMeta(d) ?: return@forEach
            out.add(
                ArtInfo(
                    id = d.name,
                    name = meta.optString("name", "Tanpa judul"),
                    w = meta.optInt("w"),
                    h = meta.optInt("h"),
                    modified = meta.optLong("modified", d.lastModified()),
                    thumb = File(d, "thumb.png"),
                )
            )
        }
        return out.sortedByDescending { it.modified }
    }

    fun create(ctx: Context, name: String, w: Int, h: Int, bg: Int, bgVisible: Boolean = true, dpi: Int = 300, paper: Int = Paper.NONE, profile: Int = 0): String {
        val id = "k" + System.currentTimeMillis().toString(36) + UUID.randomUUID().toString().take(4)
        val d = dir(ctx, id).apply { mkdirs() }
        val now = System.currentTimeMillis()
        val meta = JSONObject().apply {
            put("name", name); put("w", w); put("h", h)
            put("bg", bg); put("bgVisible", bgVisible); put("dpi", dpi)
            if (profile != 0) put("profile", profile)
            if (paper != Paper.NONE) { put("paper", paper); put("paperStrength", Paper.defaultStrength(paper).toDouble()) }
            put("active", 1); put("nextId", 2)
            put("created", now); put("modified", now)
            put("layers", JSONArray().put(layerJson(1, LayerProps("Layer 1", true, 1f, 0, false, false, false))))
        }
        writeMeta(d, meta)
        return id
    }

    fun layerJson(id: Int, p: LayerProps): JSONObject = JSONObject().apply {
        put("id", id); put("name", p.name); put("visible", p.visible); put("opacity", p.opacity.toDouble())
        put("blend", p.blend); put("alphaLock", p.alphaLock); put("clip", p.clip); put("locked", p.locked)
        if (p.group != 0) put("group", p.group)
        if (p.assist) put("assist", true)
        if (p.text != null) put("text", p.text)
        if (p.reference) put("reference", true)
    }

    fun guideJson(g: DrawingGuide): JSONObject = JSONObject().apply {
        put("type", g.type); put("color", g.color); put("opacity", g.opacity.toDouble())
        put("thickness", g.thickness.toDouble()); put("gridSize", g.gridSize.toDouble())
        if (!g.cx.isNaN()) put("cx", g.cx.toDouble())
        if (!g.cy.isNaN()) put("cy", g.cy.toDouble())
        put("angle", g.angle.toDouble())
        put("vps", JSONArray().apply { g.vps.forEach { put(it.toDouble()) } })
        put("symmetry", g.symmetry); put("rotational", g.rotational)
    }

    fun guideFrom(o: JSONObject?): DrawingGuide {
        if (o == null) return DrawingGuide()
        val d = DrawingGuide()
        fun f(k: String, def: Float) = if (o.has(k)) o.optDouble(k, def.toDouble()).toFloat() else def
        val arr = o.optJSONArray("vps")
        val vps = if (arr == null) emptyList() else List(arr.length()) { arr.optDouble(it, 0.0).toFloat() }
        return DrawingGuide(
            type = o.optInt("type", d.type).coerceIn(0, 3),
            color = o.optInt("color", d.color),
            opacity = f("opacity", d.opacity).coerceIn(0f, 1f),
            thickness = f("thickness", d.thickness).coerceIn(0f, 1f),
            gridSize = f("gridSize", d.gridSize).coerceAtLeast(DrawingGuide.MIN_GRID),
            cx = f("cx", Float.NaN),
            cy = f("cy", Float.NaN),
            angle = f("angle", 0f),
            vps = vps.take(2 * DrawingGuide.MAX_VP).let { if (it.size % 2 == 1) it.dropLast(1) else it },
            symmetry = o.optInt("symmetry", d.symmetry).coerceIn(0, 3),
            rotational = o.optBoolean("rotational", false),
        )
    }

    fun propsFrom(o: JSONObject) = LayerProps(
        name = o.optString("name", "Layer"),
        visible = o.optBoolean("visible", true),
        opacity = o.optDouble("opacity", 1.0).toFloat(),
        blend = o.optInt("blend", 0),
        alphaLock = o.optBoolean("alphaLock", false),
        clip = o.optBoolean("clip", false),
        locked = o.optBoolean("locked", false),
        group = o.optInt("group", 0),
        assist = o.optBoolean("assist", false),
        text = if (o.has("text")) o.optString("text") else null,
        reference = o.optBoolean("reference", false),
    )

    fun readMeta(d: File): JSONObject? {
        val f = File(d, "doc.json")
        if (!f.exists()) return null
        return runCatching { JSONObject(f.readText()) }.getOrNull()
    }

    fun writeMeta(d: File, meta: JSONObject) {
        d.mkdirs()
        val tmp = File(d, "doc.json.tmp")
        tmp.writeText(meta.toString())
        tmp.renameTo(File(d, "doc.json"))
    }

    fun rename(ctx: Context, id: String, name: String) {
        val d = dir(ctx, id)
        val m = readMeta(d) ?: return
        m.put("name", name)
        writeMeta(d, m)
    }

    fun delete(ctx: Context, id: String) {
        dir(ctx, id).deleteRecursively()
    }

    fun duplicate(ctx: Context, id: String): String? {
        val src = dir(ctx, id)
        val m = readMeta(src) ?: return null
        val nid = "k" + System.currentTimeMillis().toString(36) + UUID.randomUUID().toString().take(4)
        val dst = dir(ctx, nid)
        src.copyRecursively(dst, overwrite = true)
        m.put("name", m.optString("name", "Karya") + " (salinan)")
        m.put("modified", System.currentTimeMillis())
        writeMeta(dst, m)
        return nid
    }

    private fun layerFile(d: File, id: Int) = File(d, "L$id.lyr")

    fun writeLayer(d: File, id: Int, w: Int, h: Int, buf: ByteBuffer) {
        d.mkdirs()
        val tmp = File(d, "L$id.tmp")
        val arr = ByteArray(64 * 1024)
        DataOutputStream(BufferedOutputStream(FileOutputStream(tmp), 64 * 1024)).use { out ->
            out.writeInt(0x47525331) // "GRS1"
            out.writeInt(w)
            out.writeInt(h)
            val def = Deflater(1)
            val z = DeflaterOutputStream(out, def, 64 * 1024)
            buf.position(0)
            while (buf.remaining() > 0) {
                val n = minOf(arr.size, buf.remaining())
                buf.get(arr, 0, n)
                z.write(arr, 0, n)
            }
            z.finish()
            def.end()
        }
        tmp.renameTo(layerFile(d, id))
    }

    fun readLayer(d: File, id: Int, w: Int, h: Int): ByteBuffer? {
        val f = layerFile(d, id)
        if (!f.exists()) return null
        return runCatching {
            DataInputStream(BufferedInputStream(FileInputStream(f), 64 * 1024)).use { inp ->
                if (inp.readInt() != 0x47525331) return@use null
                val fw = inp.readInt()
                val fh = inp.readInt()
                if (fw != w || fh != h) return@use null
                val inf = Inflater()
                val z = InflaterInputStream(inp, inf, 64 * 1024)
                val total = w * h * 4
                val bb = Buffers.direct(total)
                val arr = ByteArray(64 * 1024)
                var got = 0
                while (got < total) {
                    val n = z.read(arr, 0, minOf(arr.size, total - got))
                    if (n < 0) break
                    bb.put(arr, 0, n)
                    got += n
                }
                inf.end()
                bb.position(0)
                bb
            }
        }.getOrNull()
    }

    fun writeThumb(d: File, bmp: Bitmap) {
        val tmp = File(d, "thumb.tmp")
        FileOutputStream(tmp).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        tmp.renameTo(File(d, "thumb.png"))
    }

    /** Hapus file layer yang sudah tidak dipakai. */
    fun cleanup(d: File, keep: Set<Int>) {
        d.listFiles()?.forEach { f ->
            val n = f.name
            if (n.startsWith("L") && (n.endsWith(".lyr") || n.endsWith(".tmp"))) {
                val id = n.substring(1, n.lastIndexOf('.')).toIntOrNull()
                if (id == null || id !in keep) f.delete()
            }
        }
    }
}
