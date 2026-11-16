package id.goresan.engine

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import kotlin.math.max
import kotlin.math.min

/** Membaca kertas sendiri: foto/gambar kertas (PNG/JPG) atau pola Photoshop/GIMP (.pat). Hasil: abu-abu n×n bersambung. */
object PaperImport {
    private const val MAX_BYTES = 40L shl 20

    fun read(ctx: Context, uri: Uri): Pair<ByteArray, Int>? {
        val bytes = ctx.contentResolver.openInputStream(uri)?.use { input ->
            val out = java.io.ByteArrayOutputStream()
            val buf = ByteArray(64 * 1024)
            var total = 0L
            while (true) {
                val n = input.read(buf)
                if (n < 0) break
                total += n
                if (total > MAX_BYTES) return null
                out.write(buf, 0, n)
            }
            out.toByteArray()
        } ?: return null
        // Pola (.pat) sudah bersambung: cukup diulang jadi persegi.
        val pat = runCatching { Abr.patFile(bytes).firstOrNull() ?: Abr.gimpPat(bytes) }.getOrNull()
        if (pat != null) {
            val (g, w, h) = Abr.tileSquare(pat.gray, pat.w, pat.h)
            val n = max(64, min(1024, max(w, h)))
            return Abr.stretch(g, w, h, n) to n
        }
        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, o)
        if (o.outWidth <= 0 || o.outHeight <= 0) return null
        var sample = 1
        while (min(o.outWidth, o.outHeight) / (sample * 2) >= 1024) sample *= 2
        val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample }) ?: return null
        val side = min(bmp.width, bmp.height)
        val n = min(1024, side).coerceAtLeast(64)
        val sq = Bitmap.createBitmap(bmp, (bmp.width - side) / 2, (bmp.height - side) / 2, side, side)
        val sc = if (side == n) sq else Bitmap.createScaledBitmap(sq, n, n, true)
        val px = IntArray(n * n)
        sc.getPixels(px, 0, n, 0, 0, n, n)
        if (sc !== sq) sc.recycle()
        if (sq !== bmp) sq.recycle()
        bmp.recycle()
        val gray = ByteArray(n * n) {
            val c = px[it]
            (((c shr 16 and 0xFF) * 299 + (c shr 8 and 0xFF) * 587 + (c and 0xFF) * 114) / 1000).toByte()
        }
        return Paper.seamless(gray, n) to n
    }
}
