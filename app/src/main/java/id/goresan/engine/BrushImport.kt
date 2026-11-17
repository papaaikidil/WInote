package id.goresan.engine

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.net.Uri
import android.provider.OpenableColumns
import java.io.ByteArrayInputStream
import java.io.File
import java.nio.ByteBuffer
import java.util.zip.ZipInputStream
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * Impor kuas dari luar:
 *  - **.brush / .brushset** dari Procreate (zip berisi Shape.png, Grain.png, dan Brush.archive);
 *  - **.abr** dari Photoshop (kuas bergambar/sampled, versi 1, 2, 6, 7, 10), lengkap dengan nama preset;
 *  - **gambar PNG/JPG/WebP**, dipakai sebagai bentuk ujung kuas (cap).
 * Bentuk ujung disimpan sebagai data abu-abu persegi (256×256; kuas .abr besar sampai 1024×1024), tekstur
 * 512×512 (putih = cat), di folder "kuas".
 * Pengaturan Procreate yang terbaca: nama, jarak cap, dan sebaran; sisanya memakai pengaturan wajar dan bisa
 * diubah di Studio Kuas.
 */
object BrushImport {
    const val TIP_N = 256
    const val GRAIN_N = 512
    private const val MAX_BYTES = 80L * 1024 * 1024

    @Volatile private var dir: File? = null

    fun init(ctx: Context) {
        if (dir == null) dir = File(ctx.filesDir, "kuas").apply { mkdirs() }
    }

    /** Data tekstur persegi yang tersimpan beserta panjang sisinya, atau null bila tidak ada/rusak. */
    fun readSquare(name: String): Pair<ByteArray, Int>? {
        val d = dir ?: return null
        val f = File(d, name)
        if (!f.exists()) return null
        val len = f.length()
        val n = kotlin.math.sqrt(len.toDouble()).roundToInt()
        if (n < 16 || n > 4096 || n.toLong() * n != len) return null
        val data = runCatching { f.readBytes() }.getOrNull() ?: return null
        return data to n
    }

    /** Hapus berkas gambar ujung/tekstur kuas impor yang sudah tidak dipakai. */
    fun deleteFile(name: String) {
        val d = dir ?: return
        if (name.isNotEmpty() && !name.contains('/')) File(d, name).delete()
    }

    class Result(val brushes: List<Brush>, val message: String)

    fun import(ctx: Context, uri: Uri): Result {
        init(ctx)
        val name = displayName(ctx, uri) ?: "Kuas"
        val base = name.substringBeforeLast('.').ifBlank { "Kuas" }
        val bytes = ctx.contentResolver.openInputStream(uri)?.use { input ->
            val out = java.io.ByteArrayOutputStream()
            val buf = ByteArray(64 * 1024)
            var total = 0L
            while (true) {
                val n = input.read(buf)
                if (n < 0) break
                total += n
                if (total > MAX_BYTES) return Result(emptyList(), "Berkas terlalu besar (lebih dari 80 MB).")
                out.write(buf, 0, n)
            }
            out.toByteArray()
        } ?: return Result(emptyList(), "Berkas tidak bisa dibuka.")
        val lower = name.lowercase()
        if (lower.endsWith(".abr") || looksAbr(bytes)) return importAbr(bytes, base)
        val magic = if (bytes.size >= 24) String(bytes, 0, 24, Charsets.ISO_8859_1) else ""
        if (lower.endsWith(".pat") || magic.startsWith("8BPT") || magic.endsWith("GPAT")) return importPat(bytes, base)
        val isZip = bytes.size > 4 && bytes[0] == 'P'.code.toByte() && bytes[1] == 'K'.code.toByte()
        return if (isZip) importZip(bytes, base) else importImage(bytes, base)
    }

    // ---------------- gambar biasa ----------------

    private fun importImage(bytes: ByteArray, base: String): Result {
        val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            ?: return Result(emptyList(), "Berkas ini bukan kuas atau gambar yang dikenali.")
        val id = newId()
        val tip = "$id-tip.r8"
        writeR8(tip, shapeR8(bmp))
        bmp.recycle()
        val b = defaults(id, base, "Impor").copy(tipFile = tip, spacing = 0.15f)
        return Result(listOf(b), "Kuas \"$base\" ditambahkan.")
    }

    // ---------------- Photoshop (.abr) ----------------

    /** Berkas .abr tanpa akhiran nama: versi 6/7/10 diikuti "8BIM". */
    private fun looksAbr(b: ByteArray): Boolean =
        b.size > 12 && Abr.version(b) in listOf(6, 7, 10) &&
            b[4] == '8'.code.toByte() && b[5] == 'B'.code.toByte() && b[6] == 'I'.code.toByte() && b[7] == 'M'.code.toByte()

    /**
     * Kuas Photoshop diubah jadi kuas seperti Procreate: **bentuk** (gambar ujung atau bulat bawaan untuk kuas
     * "computed"), **tekstur/grain** dari pola (pattern) yang dipakai kuas, dan pengaturan goresan/dinamika dari
     * presetnya (jarak, sudut, kebulatan, tekanan ukuran/opasitas, variasi ukuran/sudut/opasitas, sebaran, arah).
     * Gambar yang dimaksudkan sebagai **stempel** (coretan/ornamen/garis bawah yang disimpan seukuran aslinya tanpa
     * dinamika) dijadikan kuas stempel: satu tarikan = satu cap utuh.
     */
    private fun importAbr(bytes: ByteArray, base: String): Result {
        val ver = Abr.version(bytes)
        if (ver !in listOf(1, 2, 6, 7, 10)) {
            return Result(emptyList(), "Versi kuas Photoshop ini ($ver) belum dikenali.")
        }
        val presets = runCatching { Abr.presets(bytes) }.getOrDefault(emptyList())
        val patterns = runCatching { Abr.patterns(bytes) }.getOrDefault(emptyMap())
        val byUuid = HashMap<String, Pair<Int, Abr.Preset>>()
        presets.forEachIndexed { i, p -> p.uuid?.let { byUuid[it] = i to p } }
        val category = base.replace('_', ' ').trim().take(40).ifBlank { "Photoshop" }
        val made = ArrayList<Pair<Int, Brush>>()
        val grains = HashMap<String, String>()
        var k = 0
        var stamps = 0
        var textured = 0
        fun nextId() = newId() + made.size

        Abr.samples(bytes) { s ->
            k++
            val pre = byUuid[s.key]
            val p = pre?.second
            val long = max(s.w, s.h)
            val diameter = p?.diameter?.takeIf { it > 0f } ?: long.toFloat()
            val stamp = Abr.looksStamp(s.w, s.h, p)
            val n = when {
                stamp && long > 600 || diameter >= 500f -> 1024
                long > 300 -> 512
                else -> TIP_N
            }
            val id = nextId()
            val f = "$id-tip.r8"
            var gray = s.gray
            if (p?.flipX == true) gray = Abr.flip(gray, s.w, s.h, true)
            if (p?.flipY == true) gray = Abr.flip(gray, s.w, s.h, false)
            writeR8(f, Abr.fit(gray, s.w, s.h, n))
            val name = (p?.name ?: s.name)?.trim()?.takeIf { it.isNotBlank() } ?: "$category $k"
            var b = defaults(id, name.take(40), category).copy(
                tipFile = f,
                sizeMax = diameter.coerceIn(2f, 1000f),
                spacing = ((p?.spacing ?: s.spacing ?: 25f) / 100f).coerceIn(0.01f, 10f),
            )
            if (stamp) {
                stamps++
                b = b.copy(stamp = true, spacing = 1.5f, pressureSize = 0f, pressureOpacity = 0f, sizeMin = 20f, defaultSize = 0.75f)
                p?.roundness?.let { if (it in 1f..99.5f) b = b.copy(roundness = it / 100f) }
            } else {
                b = applyPreset(sized(b, diameter), p)
            }
            texture(b, p, patterns, grains)?.let { b = it; textured++ }
            made.add((pre?.first ?: (10_000 + k)) to b)
        }
        // Kuas bulat bawaan Photoshop ("computed"): bentuk bulat keras/lembut dengan pengaturan presetnya.
        presets.forEachIndexed { i, p ->
            if (!p.computed) return@forEachIndexed
            val id = nextId()
            val hard = (p.hardness ?: 100f) >= 80f
            var b = defaults(id, (p.name?.takeIf { it.isNotBlank() } ?: "$category ${i + 1}").take(40), category).copy(
                tip = if (hard) TipShape.HARD_ROUND else TipShape.SOFT_ROUND,
                spacing = ((p.spacing ?: 25f) / 100f).coerceIn(0.01f, 10f),
            )
            b = applyPreset(sized(b, p.diameter ?: 20f), p)
            texture(b, p, patterns, grains)?.let { b = it; textured++ }
            made.add(i to b)
        }
        if (made.isEmpty()) {
            return Result(emptyList(), "Tidak ada kuas yang bisa dibaca di berkas ini.")
        }
        val out = made.sortedBy { it.first }.map { it.second }
        val head = if (out.size == 1) "Kuas \"${out[0].name}\" ditambahkan." else "${out.size} kuas ditambahkan ke kategori \"$category\"."
        val parts = ArrayList<String>()
        if (stamps > 0) parts.add("$stamps kuas stempel (tarik garis = satu cap utuh)")
        if (textured > 0) parts.add("$textured kuas bertekstur pola")
        return Result(out, head + if (parts.isEmpty()) "" else " " + parts.joinToString(", ").replaceFirstChar { it.uppercase() } + ".")
    }

    /**
     * Rentang ukuran kuas impor: ukuran awal = diameter preset Photoshop, dan penggeser ukuran bisa sampai
     * 2,5 kali lipatnya (ukuran = min + (maks − min)·s^1,8, lihat AppState.sizePx).
     */
    private fun sized(b: Brush, diameter: Float): Brush {
        val d = diameter.coerceIn(1f, 1000f)
        val lo = 1f
        val hi = (d * 2.5f).coerceIn(max(d, 8f), 1000f)
        val s = if (hi <= lo) 1f else ((d - lo) / (hi - lo)).coerceIn(0f, 1f).toDouble().pow(1.0 / 1.8).toFloat()
        return b.copy(sizeMin = lo, sizeMax = hi, defaultSize = s.coerceIn(0.05f, 1f))
    }

    /** Pengaturan goresan dan dinamika preset Photoshop dipetakan ke pengaturan kuas (seperti Studio Kuas Procreate). */
    private fun applyPreset(base: Brush, p: Abr.Preset?): Brush {
        if (p == null) return base
        var b = base
        p.roundness?.let { if (it in 1f..99.5f) b = b.copy(roundness = it / 100f) }
        p.angle?.let { a -> if (a != 0f) b = b.copy(angle = ((a % 180f) + 180f) % 180f) }
        // Dinamika bentuk: ukuran (tekanan/kemiringan + variasi), sudut (arah goresan/kemiringan + variasi).
        if (p.tipDynamics) {
            val sz = p.size
            val range = (1f - (p.minDiameter ?: 0f) / 100f).coerceIn(0f, 1f)
            b = b.copy(
                pressureSize = if (sz?.control == 2) range else 0f,
                tiltSize = if (sz?.control == 3) range else 0f,
                sizeJitter = ((sz?.jitter ?: 0f) / 100f).coerceIn(0f, 1f),
            )
            p.angleDyn?.let { a ->
                val mode = when (a.control) {
                    5, 6 -> AngleMode.FOLLOW
                    3 -> AngleMode.TILT
                    else -> b.angleMode
                }
                b = b.copy(angleMode = mode, angleJitter = (a.jitter / 100f).coerceIn(0f, 1f))
            }
        } else {
            b = b.copy(pressureSize = 0f, sizeJitter = 0f)
        }
        // Sebaran: jarak sebar (persen diameter) dan jumlah cap per langkah.
        if (p.scatterOn) {
            val count = (p.count ?: 1f).coerceIn(1f, 16f)
            b = b.copy(
                scatter = ((p.scatter?.jitter ?: 0f) / 100f / 1.5f).coerceIn(0f, 1f),
                spacing = (b.spacing / count).coerceIn(0.01f, 10f),
            )
        }
        // Transfer: opasitas/aliran mengikuti tekanan, variasi opasitas.
        b = if (p.paintDynamics) {
            val op = p.opacity
            val press = when {
                op?.control == 2 -> (1f - (p.minOpacity ?: 0f) / 100f).coerceIn(0f, 1f)
                p.flow?.control == 2 -> 0.6f
                else -> 0f
            }
            b.copy(pressureOpacity = press, opacityJitter = ((op?.jitter ?: 0f) / 100f).coerceIn(0f, 1f))
        } else {
            b.copy(pressureOpacity = 0f)
        }
        return b
    }

    /** Tekstur pola (pattern) Photoshop jadi grain kuas; satu berkas dipakai bersama oleh kuas dengan pola yang sama. */
    private fun texture(b: Brush, p: Abr.Preset?, patterns: Map<String, Abr.Pattern>, cache: HashMap<String, String>): Brush? {
        if (p == null || !p.textureOn) return null
        val pat = p.textureId?.let { patterns[it] } ?: return null
        val key = pat.id + if (p.textureInvert) "-i" else ""
        val (tiled, tw, th) = Abr.tileSquare(pat.gray, pat.w, pat.h)
        val file = cache.getOrPut(key) {
            val f = "pola-${newId()}-${cache.size}.r8"
            var g = Abr.stretch(tiled, tw, th, GRAIN_N)
            if (p.textureInvert) g = ByteArray(g.size) { (255 - (g[it].toInt() and 0xFF)).toByte() }
            writeR8(f, g)
            f
        }
        val scale = (tw + th) / 2f * (p.textureScale ?: 100f) / 100f / GRAIN_N
        return b.copy(
            grainFile = file,
            grainScale = scale.coerceIn(0.05f, 3f),
            grainStrength = ((p.textureDepth ?: 100f) / 100f).coerceIn(0.05f, 1f),
            grainStroke = !p.textureEachTip,
        )
    }

    // ---------------- pola Photoshop (.pat) ----------------

    /** Tiap pola di berkas .pat jadi kuas pola: kuas bulat yang melukiskan pola itu (lewat grain), seukuran aslinya. */
    private fun importPat(bytes: ByteArray, base: String): Result {
        val pats = runCatching { Abr.patFile(bytes).ifEmpty { listOfNotNull(Abr.gimpPat(bytes)) } }.getOrDefault(emptyList())
        if (pats.isEmpty()) return Result(emptyList(), "Tidak ada pola yang bisa dibaca di berkas .pat ini.")
        val category = base.replace('_', ' ').trim().take(40).ifBlank { "Pola" }
        val out = ArrayList<Brush>()
        for ((i, pat) in pats.withIndex()) {
            val id = newId() + i
            val f = "pola-$id.r8"
            val (tiled, tw, th) = Abr.tileSquare(pat.gray, pat.w, pat.h)
            writeR8(f, Abr.levels(Abr.stretch(tiled, tw, th, GRAIN_N)))
            out.add(
                defaults(id, (pat.name.ifBlank { "Pola ${i + 1}" }).take(40), category).copy(
                    tip = TipShape.HARD_ROUND,
                    sizeMax = 400f,
                    spacing = 0.05f,
                    pressureSize = 0.2f,
                    pressureOpacity = 0f,
                    grainFile = f,
                    grainScale = ((tw + th) / 2f / GRAIN_N).coerceIn(0.05f, 3f),
                    grainStrength = 1f,
                    grainStroke = true,
                    defaultSize = 0.6f,
                ),
            )
        }
        return Result(out, "${out.size} kuas pola ditambahkan ke kategori \"$category\".")
    }

    // ---------------- Procreate ----------------

    private class Part(var shape: ByteArray? = null, var grain: ByteArray? = null, var archive: ByteArray? = null)

    /** Nama berkas daftar kuas WInote di dalam zip ekspor (.wkuas). */
    const val SET_JSON = "winote-kuas.json"

    /**
     * Ekspor kuas ke zip WInote (.wkuas): daftar pengaturan [SET_JSON] plus gambar ujung/tekstur di folder files/.
     * Bisa diimpor lagi lewat Impor kuas (di tablet ini atau tablet lain).
     */
    fun exportSet(brushes: List<Brush>, out: java.io.OutputStream) {
        val d = dir
        java.util.zip.ZipOutputStream(java.io.BufferedOutputStream(out)).use { zip ->
            val arr = org.json.JSONArray()
            for (b in brushes) arr.put(b.toJson())
            zip.putNextEntry(java.util.zip.ZipEntry(SET_JSON))
            zip.write(arr.toString().toByteArray(Charsets.UTF_8))
            zip.closeEntry()
            val files = LinkedHashSet<String>()
            for (b in brushes) {
                if (b.tipFile.isNotEmpty()) files.add(b.tipFile)
                if (b.grainFile.isNotEmpty()) files.add(b.grainFile)
            }
            for (f in files) {
                val src = d?.let { File(it, f) } ?: continue
                if (!src.exists() || f.contains('/')) continue
                zip.putNextEntry(java.util.zip.ZipEntry("files/$f"))
                src.inputStream().use { it.copyTo(zip) }
                zip.closeEntry()
            }
        }
    }

    /** Impor zip ekspor WInote: id dan nama berkas dibuat baru supaya tidak bertabrakan dengan kuas yang sudah ada. */
    private fun importWinote(bytes: ByteArray, base: String): Result? {
        var json: String? = null
        val files = HashMap<String, ByteArray>()
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            while (true) {
                val e = zip.nextEntry ?: break
                if (e.isDirectory) continue
                val path = e.name.replace('\\', '/')
                when {
                    path == SET_JSON -> json = String(zip.readBytes(), Charsets.UTF_8)
                    path.startsWith("files/") && !path.substringAfter("files/").contains('/') ->
                        files[path.substringAfter("files/")] = zip.readBytes()
                }
            }
        }
        val text = json ?: return null
        val arr = runCatching { org.json.JSONArray(text) }.getOrNull() ?: return Result(emptyList(), "Berkas kuas WInote rusak.")
        val out = ArrayList<Brush>()
        val renamed = HashMap<String, String>()
        val stamp = newId()
        fun file(old: String): String {
            if (old.isEmpty()) return ""
            renamed[old]?.let { return it }
            val data = files[old] ?: return ""
            val nn = "$stamp-${renamed.size}-" + old.substringAfter('-', old)
            writeR8(nn, data)
            renamed[old] = nn
            return nn
        }
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val b = runCatching { Brush.fromJson(o) }.getOrNull() ?: continue
            out.add(
                b.copy(
                    id = stamp + i,
                    builtIn = false,
                    category = b.category.ifBlank { base }.take(40),
                    tipFile = file(b.tipFile),
                    grainFile = file(b.grainFile),
                ),
            )
        }
        if (out.isEmpty()) return Result(emptyList(), "Tidak ada kuas di berkas ini.")
        return Result(out, "${out.size} kuas diimpor ke daftar \"${out.first().category}\".")
    }

    private fun importZip(bytes: ByteArray, base: String): Result {
        importWinote(bytes, base)?.let { return it }
        val parts = LinkedHashMap<String, Part>()
        var setName: String? = null
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            while (true) {
                val e = zip.nextEntry ?: break
                if (e.isDirectory) continue
                val path = e.name.replace('\\', '/')
                val file = path.substringAfterLast('/')
                val folder = path.substringBeforeLast('/', "")
                val lower = file.lowercase()
                if (lower.startsWith("._")) continue
                when {
                    lower == "shape.png" -> parts.getOrPut(folder) { Part() }.shape = zip.readBytes()
                    lower == "grain.png" -> parts.getOrPut(folder) { Part() }.grain = zip.readBytes()
                    lower == "brush.archive" -> parts.getOrPut(folder) { Part() }.archive = zip.readBytes()
                    lower == "brushset.plist" -> setName = runCatching { (Bplist.parse(zip.readBytes()) as? Map<*, *>)?.get("name") as? String }.getOrNull()
                }
            }
        }
        val usable = parts.filter { it.value.shape != null || it.value.grain != null }
        if (usable.isEmpty()) return Result(emptyList(), "Tidak ada kuas di berkas ini.")
        val category = (setName ?: base).take(40)
        val out = ArrayList<Brush>()
        var k = 1
        for ((folder, p) in usable) {
            val settings = p.archive?.let { runCatching { archiveRoot(it) }.getOrNull() } ?: emptyMap()
            val name = (settings["name"] as? String)?.takeIf { it.isNotBlank() }
                ?: if (usable.size == 1) base else "$base ${k}"
            k++
            val id = newId() + out.size
            var b = defaults(id, name.take(40), category)
            p.shape?.let { data ->
                BitmapFactory.decodeByteArray(data, 0, data.size)?.let { bmp ->
                    val f = "$id-tip.r8"
                    writeR8(f, shapeR8(bmp))
                    bmp.recycle()
                    b = b.copy(tipFile = f)
                }
            }
            p.grain?.let { data ->
                BitmapFactory.decodeByteArray(data, 0, data.size)?.let { bmp ->
                    val g = grainR8(bmp)
                    bmp.recycle()
                    // Tekstur yang (hampir) putih semua tidak berpengaruh: tidak dipakai.
                    if (g.count { (it.toInt() and 0xFF) < 235 } > g.size / 50) {
                        val f = "$id-grain.r8"
                        writeR8(f, g)
                        b = b.copy(grainFile = f, grainStrength = 0.85f)
                    }
                }
            }
            num(settings, "plotSpacing")?.let { b = b.copy(spacing = it.coerceIn(0.02f, 1.5f)) }
            num(settings, "plotJitter")?.let { b = b.copy(scatter = it.coerceIn(0f, 1f)) }
            num(settings, "plotSmoothing")?.let { b = b.copy(streamline = it.coerceIn(0f, 1f)) }
            out.add(b)
        }
        val msg = if (out.size == 1) "Kuas \"${out[0].name}\" ditambahkan." else "${out.size} kuas ditambahkan ke kategori \"$category\"."
        return Result(out, msg)
    }

    private fun num(m: Map<String, Any?>, key: String): Float? = when (val v = m[key]) {
        is Double -> v.toFloat()
        is Long -> v.toFloat()
        is Float -> v
        is Int -> v.toFloat()
        else -> null
    }

    /** Isi objek akar NSKeyedArchiver (Brush.archive): kunci → nilai sederhana (teks/angka). */
    private fun archiveRoot(bytes: ByteArray): Map<String, Any?> {
        val top = Bplist.parse(bytes) as? Map<*, *> ?: return emptyMap()
        val objects = top["\$objects"] as? List<*> ?: return emptyMap()
        val rootRef = (top["\$top"] as? Map<*, *>)?.get("root") as? Bplist.Uid ?: return emptyMap()
        val root = objects.getOrNull(rootRef.v) as? Map<*, *> ?: return emptyMap()
        val out = HashMap<String, Any?>()
        for ((k, v) in root) {
            val key = k as? String ?: continue
            out[key] = if (v is Bplist.Uid) objects.getOrNull(v.v) else v
        }
        return out
    }

    // ---------------- olah gambar ----------------

    /**
     * Bentuk ujung kuas: gambar dipaskan ke 256×256 (di tengah). Gambar dengan latar transparan memakai
     * transparansinya; gambar tanpa transparansi memakai terang-gelap, dan dibalik bila latarnya terang
     * (cap hitam di kertas putih), supaya bagian yang bergambar selalu jadi cat.
     */
    private fun shapeR8(src: Bitmap): ByteArray {
        val n = TIP_N
        val bmp = Bitmap.createBitmap(n, n, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val s = min(n.toFloat() / src.width, n.toFloat() / src.height)
        val w = max(1, (src.width * s).toInt())
        val h = max(1, (src.height * s).toInt())
        val dst = Rect((n - w) / 2, (n - h) / 2, (n - w) / 2 + w, (n - h) / 2 + h)
        c.drawBitmap(src, null, dst, Paint(Paint.FILTER_BITMAP_FLAG))
        val px = IntArray(n * n)
        bmp.getPixels(px, 0, n, 0, 0, n, n)
        bmp.recycle()
        // Transparansi asli dari gambar sumber (bukan dari tepi kosong hasil memaskan).
        val srcPx = IntArray(src.width * src.height)
        src.getPixels(srcPx, 0, src.width, 0, 0, src.width, src.height)
        val transparent = src.hasAlpha() && srcPx.count { (it ushr 24) < 250 } > srcPx.size / 100
        val out = ByteArray(n * n)
        if (transparent) {
            for (i in px.indices) out[i] = (px[i] ushr 24).toByte()
        } else {
            var edge = 0L
            var edgeN = 0
            for (i in srcPx.indices) {
                val x = i % src.width
                val y = i / src.width
                if (x == 0 || y == 0 || x == src.width - 1 || y == src.height - 1) { edge += lum(srcPx[i]); edgeN++ }
            }
            val invert = edgeN > 0 && edge / edgeN > 127
            for (i in px.indices) {
                val a = px[i] ushr 24
                if (a == 0) { out[i] = 0; continue }
                val l = lum(px[i])
                out[i] = ((if (invert) 255 - l else l) * a / 255).toByte()
            }
        }
        return out
    }

    /** Tekstur: gambar direntangkan ke 512×512 (berulang), terang = cat. */
    private fun grainR8(src: Bitmap): ByteArray {
        val n = GRAIN_N
        val bmp = Bitmap.createScaledBitmap(src, n, n, true)
        val px = IntArray(n * n)
        bmp.getPixels(px, 0, n, 0, 0, n, n)
        if (bmp !== src) bmp.recycle()
        val out = ByteArray(n * n)
        for (i in px.indices) {
            val a = px[i] ushr 24
            out[i] = (lum(px[i]) * a / 255 + (255 - a)).coerceAtMost(255).toByte()
        }
        return out
    }

    private fun lum(c: Int): Int = (((c shr 16) and 0xFF) * 54 + ((c shr 8) and 0xFF) * 183 + (c and 0xFF) * 19) shr 8

    private fun writeR8(name: String, data: ByteArray) {
        val d = dir ?: return
        val tmp = File(d, "$name.tmp")
        tmp.writeBytes(data)
        tmp.renameTo(File(d, name))
    }

    private fun newId() = "i" + System.currentTimeMillis().toString(36)

    /** Pengaturan awal kuas impor: mengikuti tekanan pena, bisa diubah di Studio Kuas. */
    private fun defaults(id: String, name: String, category: String) = Brush(
        id = id,
        name = name,
        category = category,
        tip = TipShape.SOFT_ROUND,
        sizeMax = 80f,
        sizeMin = 2f,
        spacing = 0.12f,
        pressureSize = 0.7f,
        pressureOpacity = 0.35f,
        defaultSize = 0.3f,
        builtIn = false,
    )

    private fun displayName(ctx: Context, uri: Uri): String? = runCatching {
        ctx.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }
    }.getOrNull() ?: uri.lastPathSegment?.substringAfterLast('/')
}

/**
 * Pembaca property list biner Apple ("bplist00") secukupnya: angka, teks, data, larik, kamus, dan UID
 * (dipakai NSKeyedArchiver di Brush.archive Procreate).
 */
object Bplist {
    class Uid(val v: Int)

    fun parse(bytes: ByteArray): Any? {
        if (bytes.size < 40 || String(bytes, 0, 8, Charsets.US_ASCII) != "bplist00") {
            // Ada juga brushset.plist berbentuk XML: ambil nama saja.
            val text = String(bytes, Charsets.UTF_8)
            val m = Regex("<key>name</key>\\s*<string>([^<]*)</string>").find(text) ?: return null
            return mapOf("name" to m.groupValues[1])
        }
        val buf = ByteBuffer.wrap(bytes)
        val t = bytes.size - 32
        val offSize = bytes[t + 6].toInt() and 0xFF
        val refSize = bytes[t + 7].toInt() and 0xFF
        val count = buf.getLong(t + 8).toInt()
        val topIdx = buf.getLong(t + 16).toInt()
        val tableOff = buf.getLong(t + 24).toInt()
        if (count <= 0 || count > 2_000_000 || offSize !in 1..8 || refSize !in 1..8) return null
        val offsets = IntArray(count) { i -> readUInt(bytes, tableOff + i * offSize, offSize).toInt() }
        val cache = HashMap<Int, Any?>()
        fun obj(i: Int, depth: Int): Any? {
            if (i !in 0 until count || depth > 64) return null
            cache[i]?.let { return it }
            var p = offsets[i]
            val marker = bytes[p].toInt() and 0xFF
            val hi = marker shr 4
            val lo = marker and 0x0F
            p++
            fun length(): Int {
                if (lo != 0x0F) return lo
                val m = bytes[p].toInt() and 0xFF
                val n = 1 shl (m and 0x0F)
                val v = readUInt(bytes, p + 1, n).toInt()
                p += 1 + n
                return v
            }
            val v: Any? = when (hi) {
                0x0 -> when (lo) { 0x8 -> false; 0x9 -> true; else -> null }
                0x1 -> { val n = 1 shl lo; readUInt(bytes, p, n) }
                0x2 -> if (lo == 2) buf.getFloat(p).toDouble() else buf.getDouble(p)
                0x3 -> buf.getDouble(p)
                0x4 -> { val n = length(); bytes.copyOfRange(p, p + n) }
                0x5 -> { val n = length(); String(bytes, p, n, Charsets.US_ASCII) }
                0x6 -> { val n = length(); String(bytes, p, n * 2, Charsets.UTF_16BE) }
                0x8 -> Uid(readUInt(bytes, p, lo + 1).toInt())
                0xA -> {
                    val n = length()
                    val refs = IntArray(n) { k -> readUInt(bytes, p + k * refSize, refSize).toInt() }
                    refs.map { obj(it, depth + 1) }
                }
                0xD -> {
                    val n = length()
                    val keys = IntArray(n) { k -> readUInt(bytes, p + k * refSize, refSize).toInt() }
                    val vals = IntArray(n) { k -> readUInt(bytes, p + (n + k) * refSize, refSize).toInt() }
                    val m = LinkedHashMap<Any?, Any?>()
                    for (k in 0 until n) m[obj(keys[k], depth + 1)] = obj(vals[k], depth + 1)
                    m
                }
                else -> null
            }
            cache[i] = v
            return v
        }
        return obj(topIdx, 0)
    }

    private fun readUInt(b: ByteArray, at: Int, n: Int): Long {
        var v = 0L
        for (k in 0 until n) v = (v shl 8) or (b[at + k].toLong() and 0xFF)
        return v
    }
}
