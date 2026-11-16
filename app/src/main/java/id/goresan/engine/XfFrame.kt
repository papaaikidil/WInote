package id.goresan.engine

/**
 * Satu keadaan transformasi yang dikirim antarmuka ke mesin.
 * [m]: matriks 3×3 (baris) ruang asal → kanvas, bisa proyektif (Distorsi). [net]: 4×4 titik kendali Warp di ruang
 * asal (null = tanpa Warp). [src]: kotak isi asal (x0, y0, x1, y1). [interp]: 0 terdekat, 1 bilinear, 2 bikubik.
 * Sisanya untuk gambar bantu di layar: [quad] sudut kotak, [mode], [netPts] titik kendali Warp di kanvas,
 * [curves] garis kisi Warp, [guideX]/[guideY] garis tempel (NaN = tidak ada).
 */
class XfFrame(
    val m: FloatArray,
    val net: FloatArray?,
    val src: FloatArray,
    val interp: Int,
    val quad: FloatArray,
    val mode: Int,
    val netPts: FloatArray?,
    val curves: List<FloatArray>,
    val guideX: Float,
    val guideY: Float,
) {
    companion object {
        private fun bern(i: Int, t: Float): Float {
            val s = 1f - t
            return when (i) {
                0 -> s * s * s
                1 -> 3f * t * s * s
                2 -> 3f * t * t * s
                else -> t * t * t
            }
        }

        /** Titik permukaan Bézier kubik dari 4×4 titik kendali [net] (baris demi baris), u dan w di 0..1. */
        fun bezier(net: FloatArray, u: Float, w: Float): FloatArray {
            var x = 0f
            var y = 0f
            for (j in 0..3) {
                val bw = bern(j, w)
                for (i in 0..3) {
                    val b = bern(i, u) * bw
                    x += net[(j * 4 + i) * 2] * b
                    y += net[(j * 4 + i) * 2 + 1] * b
                }
            }
            return floatArrayOf(x, y)
        }
    }
}
