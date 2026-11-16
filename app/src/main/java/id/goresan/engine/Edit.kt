package id.goresan.engine

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.util.zip.Deflater
import java.util.zip.Inflater

/**
 * Isi piksel satu wilayah untuk riwayat urungkan. Data mentah dipadatkan di latar belakang supaya
 * riwayat panjang tidak menghabiskan memori.
 */
class RegionData(val w: Int, val h: Int, raw: ByteBuffer) {
    private var raw: ByteBuffer? = raw
    private var packed: ByteArray? = null

    /** Sudah diantrekan untuk dipadatkan (supaya tidak diantrekan dua kali). */
    @Volatile var queued = false

    @Synchronized
    fun bytes(): Long = (raw?.capacity()?.toLong() ?: 0L) + (packed?.size?.toLong() ?: 0L)

    /** Ukuran data yang masih mentah (belum dipadatkan). */
    @Synchronized
    fun rawBytes(): Long = raw?.capacity()?.toLong() ?: 0L

    /** Dipanggil oleh antrean pemadat: dilewati bila data sudah diganti ([put]) sejak diantrekan. */
    @Synchronized
    fun packQueued() {
        if (!queued) return
        pack()
    }

    /** Dipadatkan sepotong-sepotong, tanpa menyalin seluruh data mentah sekaligus (hemat memori di kanvas besar). */

    @Synchronized
    fun pack() {
        queued = false
        val r = raw ?: return
        val src = r.duplicate()
        src.position(0)
        val def = Deflater(1)
        val chunk = ByteArray(256 * 1024)
        val out = ByteArray(64 * 1024)
        val bos = ByteArrayOutputStream(maxOf(1024, r.capacity() / 16))
        try {
            while (src.hasRemaining()) {
                val n = minOf(chunk.size, src.remaining())
                src.get(chunk, 0, n)
                def.setInput(chunk, 0, n)
                while (!def.needsInput()) {
                    val k = def.deflate(out)
                    if (k > 0) bos.write(out, 0, k)
                }
            }
            def.finish()
            while (!def.finished()) {
                val k = def.deflate(out)
                if (k > 0) bos.write(out, 0, k)
            }
        } finally {
            def.end()
        }
        packed = bos.toByteArray()
        raw = null
    }

    /** Data mentah (dibongkar bila perlu, langsung ke penyangga tujuan). */
    @Synchronized
    fun take(): ByteBuffer {
        raw?.let { it.position(0); return it }
        val p = packed ?: return Buffers.direct(w * h * 4)
        val bb = Buffers.direct(w * h * 4)
        val inf = Inflater()
        try {
            inf.setInput(p)
            val buf = ByteArray(256 * 1024)
            while (bb.hasRemaining() && !inf.finished()) {
                val n = inf.inflate(buf, 0, minOf(buf.size, bb.remaining()))
                if (n == 0 && (inf.needsInput() || inf.needsDictionary())) break
                bb.put(buf, 0, n)
            }
        } finally {
            inf.end()
        }
        bb.position(0)
        return bb
    }

    @Synchronized
    fun put(b: ByteBuffer) {
        queued = false
        raw = b
        packed = null
    }
}

data class LayerProps(
    val name: String,
    val visible: Boolean,
    val opacity: Float,
    val blend: Int,
    val alphaLock: Boolean,
    val clip: Boolean,
    val locked: Boolean,
    /** Id grup tempat layer berada (0 = tidak di grup). */
    val group: Int = 0,
    /** Bantuan menggambar (Drawing Assist): goresan di layer ini mengikuti panduan menggambar. */
    val assist: Boolean = false,
    /** Layer teks: isi dan gaya teks (JSON TextSpec) supaya bisa diedit lagi; null = layer biasa. */
    val text: String? = null,
)

/** Data satu grup layer. Anggota grup selalu berurutan (bersebelahan) di daftar layer. */
data class GroupData(val name: String, val visible: Boolean, val collapsed: Boolean)

class LayerSnapshot(val props: LayerProps, val data: RegionData)

/**
 * Satu langkah di riwayat. Semua langkah bersifat "tukar": menerapkan langkah yang sama dua kali
 * mengembalikan keadaan semula, jadi urungkan dan ulangi memakai kode yang sama.
 */
sealed class Edit {
    class Pixels(val layerId: Int, val rect: IRect, val data: RegionData) : Edit()
    class Props(val layerId: Int, var props: LayerProps) : Edit()
    class Exist(val layerId: Int, var index: Int, var snapshot: LayerSnapshot?) : Edit()
    class Move(val layerId: Int, var otherIndex: Int) : Edit()
    class Bg(var color: Int, var visible: Boolean) : Edit()
    /** Data grup ditukar (null = grup belum/tidak ada). */
    class GroupMeta(val groupId: Int, var data: GroupData?) : Edit()
    /** Urutan layer ditukar dengan urutan [ids]. */
    class Order(var ids: List<Int>) : Edit()
    class Group(val list: List<Edit>) : Edit()

    /** Wilayah kanvas yang berubah bila langkah ini diurungkan/diulang; null = bisa di mana saja (gambar ulang semua). */
    fun area(): IRect? = when (this) {
        is Pixels -> rect.copy()
        is Group -> {
            var u: IRect? = IRect()
            for (e in list) {
                val r = e.area()
                if (r == null) { u = null; break }
                u!!.union(r)
            }
            u
        }
        else -> null
    }

    /** Nama langkah untuk pesan "Urungkan …" / "Ulangi …". */
    fun label(): String = when (this) {
        is Pixels -> "goresan"
        is Props -> "pengaturan layer"
        is Exist -> "layer"
        is Move, is Order -> "urutan layer"
        is Bg -> "latar"
        is GroupMeta -> "grup"
        is Group -> list.firstOrNull { it is Exist }?.label() ?: list.firstOrNull()?.label() ?: "langkah"
    }

    fun bytes(): Long = when (this) {
        is Pixels -> data.bytes()
        is Exist -> snapshot?.data?.bytes() ?: 0L
        is Group -> list.sumOf { it.bytes() }
        else -> 64L
    }

    fun forEachData(f: (RegionData) -> Unit) {
        when (this) {
            is Pixels -> f(data)
            is Exist -> snapshot?.let { f(it.data) }
            is Group -> list.forEach { it.forEachData(f) }
            else -> Unit
        }
    }
}
