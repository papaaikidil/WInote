package id.goresan.engine

import android.graphics.Color
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Kumpulan dab (cap ujung kuas) yang siap digambar: x, y, diameter, sudut, kebulatan, alfa, r, g, b, bleed.
 * Kebulatan negatif = bentuk dibalik (cermin). Bleed 0..1 = ambang: hanya bagian bentuk/tekstur yang lebih pekat
 * dari ambang yang tampak.
 */
class DabList {
    var data = FloatArray(S * 512)
        private set
    var size = 0
        private set

    fun clear() { size = 0 }

    fun add(
        x: Float, y: Float, s: Float, ang: Float, round: Float, a: Float, r: Float, g: Float, b: Float,
        bleed: Float = 0f,
    ) {
        // Pengaman: jangan sampai satu goresan menghabiskan memori.
        if (size >= MAX_DABS) return
        if ((size + 1) * S > data.size) data = data.copyOf(data.size * 2)
        val o = size * S
        data[o] = x; data[o + 1] = y; data[o + 2] = s; data[o + 3] = ang; data[o + 4] = round
        data[o + 5] = a; data[o + 6] = r; data[o + 7] = g; data[o + 8] = b; data[o + 9] = bleed
        size++
    }

    /** Salin satu dab (S angka) dari [v]. */
    fun addRaw(v: FloatArray) {
        if (size >= MAX_DABS) return
        if ((size + 1) * S > data.size) data = data.copyOf(data.size * 2)
        System.arraycopy(v, 0, data, size * S, S)
        size++
    }

    fun bounds(out: IRect) {
        out.clear()
        if (size == 0) return
        var x0 = Float.MAX_VALUE; var y0 = Float.MAX_VALUE
        var x1 = -Float.MAX_VALUE; var y1 = -Float.MAX_VALUE
        for (i in 0 until size) {
            val o = i * S
            val rad = data[o + 2] * 0.71f + 1.5f
            x0 = min(x0, data[o] - rad); y0 = min(y0, data[o + 1] - rad)
            x1 = max(x1, data[o] + rad); y1 = max(y1, data[o + 1] + rad)
        }
        out.unionF(x0, y0, x1, y1)
    }

    companion object {
        const val MAX_DABS = 400_000
        /** Jumlah angka per dab. */
        const val S = 10
    }
}

/** Dab yang ditahan dulu (belum digambar permanen) supaya ujung akhir goresan bisa diruncingkan saat pena diangkat. */
class HeldDabs {
    private var data = FloatArray(DabList.S * 256)
    private var dist = FloatArray(256)
    private var start = 0
    var count = 0
        private set

    fun clear() { start = 0; count = 0 }

    fun add(v: FloatArray, d: Float) {
        if (start > 0 && start + count + 1 > dist.size) {
            System.arraycopy(data, start * DabList.S, data, 0, count * DabList.S)
            System.arraycopy(dist, start, dist, 0, count)
            start = 0
        }
        if (start + count + 1 > dist.size) {
            data = data.copyOf(data.size * 2)
            dist = dist.copyOf(dist.size * 2)
        }
        val k = start + count
        System.arraycopy(v, 0, data, k * DabList.S, DabList.S)
        dist[k] = d
        count++
    }

    fun firstDist(): Float = dist[start]
    fun lastDist(): Float = dist[start + count - 1]
    fun distAt(i: Int): Float = dist[start + i]

    /** Isi dab ke-[i] (0 = paling lama) ke [o]. */
    fun get(i: Int, o: FloatArray) { System.arraycopy(data, (start + i) * DabList.S, o, 0, DabList.S) }

    fun removeFirst() { start++; count--; if (count == 0) start = 0 }
}

/**
 * Penghalus garis global (Aksi › Tekanan & penghalusan, seperti Preferensi › Pressure and Smoothing di Procreate).
 * Digabung dengan pengaturan kuas: hasil = 1 − (1 − kuas)(1 − global).
 */
class Smoothing(
    val stabilization: Float = 0f,
    val motion: Float = 0f,
    val expression: Float = 0f,
) {
    companion object {
        val NONE = Smoothing()
    }
}

/**
 * Mengubah titik mentah pena menjadi dab:
 *  1. kurva tekanan,
 *  2. Penyaring gerak (Motion filtering): tali yang ditarik — getaran tangan di bawah panjang tali hilang
 *     berapa pun kecepatannya, garis jadi lurus dan mulus; Ekspresi mengembalikan sebagian karakter tangan,
 *  3. Stabilisasi: makin cepat pena bergerak makin kuat penghalusannya (gerak pelan tetap teliti),
 *  4. StreamLine: goresan menyusul pena dengan halus (tertinggal sedikit lalu menyusul saat pena berhenti/diangkat);
 *     StreamLine Tekanan menghaluskan perubahan tekanan,
 *  5. penghalusan jalur dengan kurva kuadratik,
 *  6. penempatan dab per jarak (spacing) beserta dinamika tekanan, kemiringan, dan getaran.
 */
class StrokeProcessor {
    private var b: Brush = Brushes.builtIn[0]
    private var base = 10f
    private var cr = 0f
    private var cg = 0f
    private var cb = 0f
    private val hsv = FloatArray(3)
    private var hasPressure = false
    private var hasTilt = false
    private var gamma = 1f
    private var spacingFrac = 0.1f
    private var maxSize = 4000f
    private var rnd = java.util.Random()
    /** Acak terpisah untuk ekor sementara: pola getaran ekor tetap dari bingkai ke bingkai dan tidak mengubah goresan. */
    private val tailRnd = java.util.Random()
    private var tailSeed = 0L

    // ---- runcing akhir: dab terakhir sepanjang [taperLen] ditahan dulu ----
    private var holdOn = false
    private var taperLen = 0f
    private var previewMode = false
    private val held = HeldDabs()
    private val dv = FloatArray(DabList.S)

    // ---- penghalus ----
    private var started = false
    private var lastT = 0L
    private val lastRaw = FloatArray(5)
    private var prevRawP = 1f
    /** Piksel kanvas per dp layar (jarak penyaring gerak dan kecepatan diukur di layar, tidak bergantung zum). */
    private var canvasPerDp = 1f

    // Penyaring gerak
    private var mfR = 0f
    private var mfExpr = 0f
    private var mx = 0f
    private var my = 0f
    private var ex = 0f
    private var ey = 0f

    // Stabilisasi (dua tahap, tau bergantung kecepatan)
    private var stabTau = 0f
    private var s1x = 0f
    private var s1y = 0f
    private var s2x = 0f
    private var s2y = 0f
    private var spd = 0f
    private var vrx = 0f
    private var vry = 0f

    // StreamLine
    private var slTau = 0f
    private var slpTau = 0f
    private var sx = 0f
    private var sy = 0f
    private var sp = 1f
    private var st = 0f
    private var sa = 0f
    private val tmp = FloatArray(5)

    /** Titik mentah pena terbaru (x, y, tekanan, kemiringan, azimut), untuk "ekor" goresan sampai ujung pena. */
    private val rawRing = Array(RAW_N) { FloatArray(5) }
    private var rawCount = 0
    private val rawTime = LongArray(RAW_N)

    // Jalur
    private var nPts = 0
    private val p0 = FloatArray(5)
    private val p1 = FloatArray(5)
    private val cur = FloatArray(5)
    private val qa = FloatArray(5)
    private val qb = FloatArray(5)
    private val mid = FloatArray(5)
    private var residual = 0f
    private var travelled = 0f
    private var dirX = 1f
    private var dirY = 0f

    // Kecepatan untuk prediksi
    private var vx = 0f
    private var vy = 0f
    private var lqx = 0f
    private var lqy = 0f
    private var lqt = 0L
    private var haveQ = false
    private var qTime = 0L

    companion object {
        /** Jumlah titik mentah yang diingat untuk ekor goresan (±0,3–0,5 detik). */
        const val RAW_N = 96
    }

    fun begin(
        brush: Brush, sizePx: Float, color: Int, pressure: Boolean, tilt: Boolean,
        pressureGamma: Float, spacing: Float, maxDiameter: Float,
        smoothing: Smoothing = Smoothing.NONE, canvasPerDp: Float = 1f,
        allowHold: Boolean = true,
        color2: Int = color,
        viewRot: Float = 0f,
    ) {
        b = brush
        base = if (sizePx.isFinite()) max(0.5f, sizePx) else 10f
        cr = Color.red(color) / 255f
        cg = Color.green(color) / 255f
        cb = Color.blue(color) / 255f
        Color.colorToHSV(color, hsv)
        c2r = Color.red(color2) / 255f
        c2g = Color.green(color2) / 255f
        c2b = Color.blue(color2) / 255f
        this.viewRot = if (viewRot.isFinite()) viewRot else 0f
        setupDynamics()
        hasPressure = pressure
        hasTilt = tilt
        gamma = pressureGamma
        spacingFrac = if (spacing.isFinite()) spacing.coerceIn(0.01f, 2f) else 0.1f
        maxSize = maxDiameter
        this.canvasPerDp = if (canvasPerDp.isFinite() && canvasPerDp > 0f) canvasPerDp else 1f
        started = false
        held.clear()
        previewMode = false
        taperLen = b.taperEnd.coerceIn(0f, 1f) * base * 8f
        holdOn = allowHold && taperLen > 0.5f
        tailSeed = System.nanoTime()
        nPts = 0
        residual = 0f
        travelled = 0f
        haveQ = false
        vx = 0f
        vy = 0f

        fun comb(a: Float, g: Float) = 1f - (1f - a.coerceIn(0f, 1f)) * (1f - g.coerceIn(0f, 1f))
        val stab = comb(b.stabilization, smoothing.stabilization)
        val motion = comb(b.motionFilter, smoothing.motion)
        val expr = if (b.motionFilter > 0.001f) b.motionExpression else 0f
        // Penyaring gerak: panjang tali sampai ±44 dp di layar pada 100%.
        mfR = if (motion < 0.005f) 0f else motion.pow(1.15f) * 44f * this.canvasPerDp
        mfExpr = comb(expr, if (smoothing.motion > 0.001f) smoothing.expression else 0f)
        // Stabilisasi: total tertinggal sampai ±190 ms pada 100% (dua tahap), hanya saat pena bergerak cepat.
        stabTau = if (stab < 0.005f) 0f else stab.pow(1.4f) * 95f
        // StreamLine: tali waktu (tau) sampai ±210 ms pada 100%.
        val sl = b.streamline.coerceIn(0f, 1f)
        slTau = sl * sl * 210f
        val slp = b.streamlinePressure.coerceIn(0f, 1f)
        slpTau = 5f + slp * slp * 280f
    }

    fun add(x: Float, y: Float, p0: Float, tilt0: Float, az0: Float, t: Long, out: DabList) {
        // Masukan tidak wajar (NaN/tak hingga) diabaikan supaya penempatan dab tidak berputar tanpa akhir.
        if (!x.isFinite() || !y.isFinite()) return
        val p = if (p0.isFinite()) p0 else 1f
        val tilt = if (tilt0.isFinite()) tilt0 else 0f
        val az = if (az0.isFinite()) az0 else 0f
        val pr = if (hasPressure) curveP(p.coerceIn(0f, 1f).pow(gamma).let { if (it.isFinite()) it else 1f }) else 1f
        prevRawP = if (started) lastRaw[2] else pr
        lastRaw[0] = x; lastRaw[1] = y; lastRaw[2] = pr; lastRaw[3] = tilt; lastRaw[4] = az
        if (!started) rawCount = 0
        val slot = rawCount % RAW_N
        lastRaw.copyInto(rawRing[slot])
        rawTime[slot] = t
        rawCount++
        if (!started) {
            started = true
            mx = x; my = y; ex = x; ey = y
            s1x = x; s1y = y; s2x = x; s2y = y
            vrx = x; vry = y; spd = 0f
            sx = x; sy = y; sp = pr; st = tilt; sa = az
            lastT = t
            qTime = t
            tmp[0] = x; tmp[1] = y; tmp[2] = pr; tmp[3] = tilt; tmp[4] = az
            emitPoint(tmp, out)
            return
        }
        val dt = (t - lastT).coerceIn(1L, 40L).toFloat()
        lastT = t
        qTime = t
        step(x, y, pr, tilt, az, dt, 1f, out)
    }

    private fun ema(dt: Float, tau: Float) = if (tau < 0.5f) 1f else 1f - exp(-dt / tau)

    /** Satu langkah penghalus. [mfScale] mengecilkan tali penyaring gerak saat goresan menyusul di akhir. */
    private fun step(x: Float, y: Float, pr: Float, tilt: Float, az: Float, dt: Float, mfScale: Float, out: DabList) {
        // Kecepatan pena di layar (dp/ms), dihaluskan sedikit.
        val v = hypot(x - vrx, y - vry) / canvasPerDp / dt
        vrx = x; vry = y
        spd += (v - spd) * ema(dt, 28f)

        // 1. Penyaring gerak: tali sepanjang mfR; ujung goresan hanya bergerak bila pena lebih jauh dari tali.
        var ox = x
        var oy = y
        if (mfR > 0f) {
            val r = mfR * mfScale
            val dx = x - mx
            val dy = y - my
            val d = hypot(dx, dy)
            if (d > r) {
                val f = 1f - r / d
                mx += dx * f
                my += dy * f
            }
            ox = mx
            oy = my
            if (mfExpr > 0.001f) {
                // Ekspresi: sebagian gerak tangan (yang sedikit dihaluskan) dikembalikan.
                val ke = ema(dt, 10f)
                ex += (x - ex) * ke
                ey += (y - ey) * ke
                val w = mfExpr * 0.6f
                ox += (ex - mx) * w
                oy += (ey - my) * w
            }
        }

        // 2. Stabilisasi: makin cepat makin halus (gerak pelan hampir tanpa penghalusan).
        if (stabTau > 0f) {
            var g = ((spd - 0.04f) / 1.1f).coerceIn(0f, 1f)
            g = g * g * (3f - 2f * g)
            val k = ema(dt, stabTau * (0.1f + 0.9f * g))
            s1x += (ox - s1x) * k
            s1y += (oy - s1y) * k
            s2x += (s1x - s2x) * k
            s2y += (s1y - s2y) * k
            ox = s2x
            oy = s2y
        }

        // 3. StreamLine: posisi menyusul pena dengan tali waktu; tekanan dihaluskan terpisah.
        val k = ema(dt, slTau)
        sx += (ox - sx) * k
        sy += (oy - sy) * k
        sp += (pr - sp) * ema(dt, slpTau)
        st += (tilt - st) * ema(dt, 10f)
        sa = az
        tmp[0] = sx; tmp[1] = sy; tmp[2] = sp; tmp[3] = st; tmp[4] = sa
        emitPoint(tmp, out)
    }

    private fun smoothing() = mfR > 0f || stabTau > 0f || slTau >= 0.5f

    /**
     * Pena diangkat: goresan menyusul sampai titik akhir pena, lalu ujung akhir diruncingkan bila kuas memakai
     * Runcing di akhir.
     *  - Titik saat pena sedang terangkat (tekanannya anjlok) tidak ikut dikejar, dan tekanan titik akhir dipakai apa
     *    adanya: dulu tekanan sebelumnya dipakai, sehingga gerakan saat mengangkat pena tergambar sebagai "kaki" tebal.
     *  - Kait kecil mendadak di 45 ms terakhir (posisi bergeser ke arah kemiringan pena saat ujungnya terangkat)
     *    dibuang.
     */
    fun end(out: DabList) {
        if (!started) return
        if (smoothing()) {
            val q = rawRing[endIndex() % RAW_N]
            var i = 0
            while (i < 300) {
                i++
                qTime += 8
                step(q[0], q[1], q[2], q[3], q[4], 8f, (1f - i / 10f).coerceAtLeast(0f), out)
                if (i >= 10 && hypot(sx - q[0], sy - q[1]) < 0.3f) break
                if (out.size >= DabList.MAX_DABS) break
            }
        }
        if (nPts >= 2) line(cur, p1, out)
        releaseHeld(out, taper = true)
    }

    private fun rr(i: Int) = rawRing[i % RAW_N]
    private fun rt(i: Int) = rawTime[i % RAW_N]

    /** Indeks titik mentah yang dipakai sebagai akhir goresan (titik saat pena terangkat dan kait mendadak dibuang). */
    private fun endIndex(): Int {
        val n = min(rawCount, RAW_N)
        val last = rawCount - 1
        if (n < 3) return last
        val oldest = rawCount - n
        var e = last
        // 1. Pena sedang terangkat: tekanan anjlok dibanding tekanan sesaat sebelumnya.
        if (hasPressure) {
            var ref = 0f
            for (i in last downTo oldest) {
                if (rt(last) - rt(i) > 150L) break
                ref = max(ref, rr(i)[2])
            }
            while (e > oldest + 1 && rr(e)[2] < ref * 0.25f && rt(last) - rt(e) < 80L) e--
        }
        // 2. Kait mendadak di ujung: belok tajam (> 60°) dalam 45 ms terakhir dan pendek (< 36 dp).
        val dp = canvasPerDp
        var bestC = -1
        var bestAng = 0f
        var c = e - 1
        while (c > oldest) {
            if (rt(e) - rt(c) > 45L) break
            var r = c - 1
            while (r > oldest && rt(c) - rt(r) < 30L) r--
            val a1 = rr(r)
            val a2 = rr(c)
            val a3 = rr(e)
            val v1x = a2[0] - a1[0]
            val v1y = a2[1] - a1[1]
            val v2x = a3[0] - a2[0]
            val v2y = a3[1] - a2[1]
            val l1 = hypot(v1x, v1y)
            val l2 = hypot(v2x, v2y)
            if (l1 > 4f * dp && l2 > 1f * dp && l2 < 36f * dp) {
                val cs = ((v1x * v2x + v1y * v2y) / (l1 * l2)).coerceIn(-1f, 1f)
                val ang = kotlin.math.acos(cs)
                if (ang > bestAng) { bestAng = ang; bestC = c }
            }
            c--
        }
        if (bestC >= 0 && bestAng > 1.05f) e = bestC
        return e
    }

    private fun emitPoint(q: FloatArray, out: DabList) {
        if (haveQ) {
            val dtq = (qTime - lqt).toFloat()
            if (dtq > 0f) {
                val nvx = (q[0] - lqx) / dtq
                val nvy = (q[1] - lqy) / dtq
                vx = vx * 0.55f + nvx * 0.45f
                vy = vy * 0.55f + nvy * 0.45f
            }
        }
        lqx = q[0]; lqy = q[1]; lqt = qTime; haveQ = true

        when (nPts) {
            0 -> {
                q.copyInto(p0)
                q.copyInto(cur)
                nPts = 1
                dab(q, 0f, out)
                residual = spacingAt(q[2], q[3])
            }
            1 -> {
                q.copyInto(p1)
                nPts = 2
                midpoint(p0, p1, mid)
                line(cur, mid, out)
                mid.copyInto(cur)
            }
            else -> {
                midpoint(p1, q, mid)
                quad(cur, p1, mid, out)
                mid.copyInto(cur)
                p1.copyInto(p0)
                q.copyInto(p1)
            }
        }
    }

    private fun midpoint(a: FloatArray, c: FloatArray, o: FloatArray) {
        for (i in 0 until 4) o[i] = (a[i] + c[i]) * 0.5f
        o[4] = c[4]
    }

    private fun quad(a: FloatArray, c: FloatArray, e: FloatArray, out: DabList) {
        val len = hypot(c[0] - a[0], c[1] - a[1]) + hypot(e[0] - c[0], e[1] - c[1])
        val segs = (len / 4f).toInt().coerceIn(1, 48)
        a.copyInto(qa)
        for (i in 1..segs) {
            val t = i.toFloat() / segs
            val u = 1f - t
            qb[0] = u * u * a[0] + 2f * u * t * c[0] + t * t * e[0]
            qb[1] = u * u * a[1] + 2f * u * t * c[1] + t * t * e[1]
            qb[2] = a[2] + (e[2] - a[2]) * t
            qb[3] = a[3] + (e[3] - a[3]) * t
            qb[4] = e[4]
            line(qa, qb, out)
            qb.copyInto(qa)
        }
    }

    private val lp = FloatArray(5)

    private fun line(a: FloatArray, e: FloatArray, out: DabList) {
        val dx = e[0] - a[0]
        val dy = e[1] - a[1]
        val len = hypot(dx, dy)
        if (!(len >= 1e-4f) || !len.isFinite()) return
        if (!residual.isFinite() || residual <= 0f) residual = 0.3f
        dirX = dx / len
        dirY = dy / len
        var d = 0f
        while (true) {
            val need = residual
            if (d + need > len) {
                residual -= (len - d)
                break
            }
            d += need
            val t = d / len
            lp[0] = a[0] + dx * t
            lp[1] = a[1] + dy * t
            lp[2] = a[2] + (e[2] - a[2]) * t
            lp[3] = a[3] + (e[3] - a[3]) * t
            lp[4] = e[4]
            dab(lp, travelled + d, out)
            residual = spacingAt(lp[2], lp[3])
            if (out.size >= DabList.MAX_DABS) break
        }
        travelled += len
    }

    /**
     * Kemiringan 0..1. [t] = sudut pena dari tegak lurus (radian). Sudut mulai (Brush.tiltAngle, derajat dari
     * permukaan): efek mulai saat pena lebih rendah dari sudut itu dan penuh di ±20° dari permukaan.
     */
    private fun tiltN(t: Float): Float {
        val elev = 90f - Math.toDegrees(t.toDouble()).toFloat()
        val trig = b.tiltAngle.coerceIn(25f, 90f)
        return ((trig - elev) / (trig - 20f)).coerceIn(0f, 1f)
    }

    private fun sizeAt(p: Float, tilt: Float): Float {
        var s = base
        if (hasPressure) s *= 1f - b.pressureSize * (1f - p)
        if (hasTilt) s *= 1f + b.tiltSize * tiltN(tilt) * 1.5f
        return s
    }

    private fun spacingAt(p: Float, tilt: Float): Float {
        var s = spacingFrac * min(sizeAt(p, tilt), maxSize)
        if (b.speedSpacing > 0f) s *= 1f + b.speedSpacing * speedN() * 3f
        if (b.spacingJitter > 0f) s *= 1f + b.spacingJitter * rnd.nextFloat() * 2f
        return if (s.isNaN() || s < 0.3f) 0.3f else s
    }

    private val hsvTmp = FloatArray(3)

    // ---- dinamika per goresan ----
    private var c2r = 0f
    private var c2g = 0f
    private var c2b = 0f
    private var viewRot = 0f
    /** Warna dasar goresan ini (sesudah getaran per goresan), RGB 0..1. */
    private var sr = 0f
    private var sg = 0f
    private var sb = 0f
    private var colorDyn = false
    private var baseAngle = 0f
    private var tipMin = 0.12f
    private var fallLen = 0f
    private val curveLut = FloatArray(257)
    private var curveOn = false
    private val rgbT = FloatArray(3)
    private val hsvT = FloatArray(3)

    /** Pengaturan yang ditetapkan sekali per goresan: getaran warna per goresan, putaran acak, kurva tekanan. */
    private fun setupDynamics() {
        // Warna dasar goresan: getaran per goresan (sekali di awal goresan).
        sr = cr; sg = cg; sb = cb
        if (b.strokeHue > 0f || b.strokeSat > 0f) {
            rgbToHsv(sr, sg, sb, hsvT)
            hsvT[0] = ((hsvT[0] + (rnd.nextFloat() * 2f - 1f) * b.strokeHue * 0.5f) % 1f + 1f) % 1f
            if (b.strokeSat > 0f) hsvT[1] = hsvT[1] + (rnd.nextFloat() - hsvT[1]) * b.strokeSat
            hsvToRgb(hsvT[0], hsvT[1], hsvT[2], rgbT)
            sr = rgbT[0]; sg = rgbT[1]; sb = rgbT[2]
        }
        if (b.strokeLight > 0f) { val k = rnd.nextFloat() * b.strokeLight; sr += (1f - sr) * k; sg += (1f - sg) * k; sb += (1f - sb) * k }
        if (b.strokeDark > 0f) { val k = 1f - rnd.nextFloat() * b.strokeDark; sr *= k; sg *= k; sb *= k }
        if (b.strokeColor2 > 0f) {
            val k = rnd.nextFloat() * b.strokeColor2
            sr += (c2r - sr) * k; sg += (c2g - sg) * k; sb += (c2b - sb) * k
        }
        colorDyn = b.hueJitter > 0f || b.satJitter > 0f || b.lightJitter > 0f || b.darkJitter > 0f || b.color2Jitter > 0f ||
            b.pressHue != 0f || b.pressSat != 0f || b.pressBright != 0f || b.pressColor2 != 0f ||
            b.tiltHue != 0f || b.tiltSat != 0f || b.tiltBright != 0f || b.tiltColor2 != 0f
        baseAngle = if (b.randomized) rnd.nextFloat() * 2f * PI.toFloat() else 0f
        tipMin = 0.04f + 0.8f * b.taperTip.coerceIn(0f, 1f)
        val fo = b.fallOff.coerceIn(0f, 1f)
        val k = 1f - fo
        fallLen = if (fo <= 0f) 0f else base * (2f + 150f * k * k)
        // Kurva tekanan kuas: tabel 257 nilai dari kurva halus (Hermite monoton) lewat titik-titiknya.
        curveOn = b.pressureCurve.size >= 2
        if (curveOn) buildCurve(b.pressureCurve, curveLut)
    }

    /** Pena: tekanan sesudah kurva kuas. */
    private fun curveP(p: Float): Float {
        if (!curveOn) return p
        val x = p.coerceIn(0f, 1f) * 256f
        val i = x.toInt().coerceIn(0, 255)
        val f = x - i
        return curveLut[i] + (curveLut[i + 1] - curveLut[i]) * f
    }

    /** Kecepatan goresan di cap ini, 0 (pelan) .. 1 (cepat, ±2,5 dp/ms). */
    private fun speedN(): Float {
        val v = hypot(vx, vy) / canvasPerDp
        return sqrt((v / 2.5f).coerceIn(0f, 1f))
    }

    /** Pengali dinamika kecepatan: [amt] > 0 cepat = berkurang, < 0 lambat = berkurang. */
    private fun speedK(amt: Float, sN: Float, k: Float): Float =
        if (amt >= 0f) 1f - amt * k * sN else 1f + amt * k * (1f - sN)

    private fun dab(pt: FloatArray, dist: Float, out: DabList) {
        val p = pt[2]
        val tilt = pt[3]
        val tn = if (hasTilt) tiltN(tilt) else 0f
        val sN = if (b.speedSize != 0f || b.speedOpacity != 0f) speedN() else 0f
        var size = sizeAt(p, tilt)
        if (b.speedSize != 0f) size *= speedK(b.speedSize, sN, 0.85f)
        var a = b.flow
        if (b.taperStart > 0f) {
            val l = b.taperStart * base * 8f
            if (dist < l) {
                val f = dist / l
                size *= tipMin + (1f - tipMin) * f
                a *= 1f - b.taperOpacity * (1f - f)
            }
        }
        if (hasPressure) a *= 1f - b.pressureOpacity * (1f - p)
        if (hasTilt) a *= 1f - b.tiltOpacity * tn * 0.8f
        if (b.speedOpacity != 0f) a *= speedK(b.speedOpacity, sN, 0.9f)
        if (fallLen > 0f) a *= exp(-dist / fallLen)
        var bleed = 0f
        if (hasPressure && b.pressureBleed > 0f) bleed = max(bleed, b.pressureBleed * (1f - p) * 0.92f)
        if (hasTilt && b.tiltBleed > 0f) bleed = max(bleed, b.tiltBleed * tn * 0.92f)
        var round = b.roundness
        if (hasPressure && b.pressureRound > 0f) round *= 1f - b.pressureRound * (1f - p) * 0.9f
        if (hasTilt && b.tiltRound > 0f) round *= 1f - b.tiltRound * tn * 0.9f
        // Warna dasar cap ini: warna goresan, digeser tekanan dan kemiringan.
        var r = sr
        var g = sg
        var bl = sb
        if (colorDyn) {
            val pk = if (hasPressure) 1f - p else 0f
            if (b.pressHue != 0f || b.pressSat != 0f || b.pressBright != 0f || b.tiltHue != 0f || b.tiltSat != 0f || b.tiltBright != 0f) {
                rgbToHsv(r, g, bl, hsvT)
                hsvT[0] = ((hsvT[0] + b.pressHue * pk * 0.5f + b.tiltHue * tn * 0.5f) % 1f + 1f) % 1f
                hsvT[1] = (hsvT[1] * (1f - b.pressSat * pk) * (1f - b.tiltSat * tn)).coerceIn(0f, 1f)
                hsvToRgb(hsvT[0], hsvT[1], hsvT[2], rgbT)
                r = rgbT[0]; g = rgbT[1]; bl = rgbT[2]
                val br = b.pressBright * pk + b.tiltBright * tn
                if (br > 0f) { val k = br.coerceAtMost(1f); r += (1f - r) * k; g += (1f - g) * k; bl += (1f - bl) * k }
                else if (br < 0f) { val k = 1f + br.coerceAtLeast(-1f); r *= k; g *= k; bl *= k }
            }
            val k2 = (b.pressColor2 * pk + b.tiltColor2 * tn).coerceIn(0f, 1f)
            if (k2 > 0f) { r += (c2r - r) * k2; g += (c2g - g) * k2; bl += (c2b - bl) * k2 }
        }
        // Jumlah cap di titik ini.
        var n = b.count.coerceIn(1, 16)
        if (n > 1 && b.countJitter > 0f) n -= (rnd.nextFloat() * b.countJitter * (n - 1) + 0.5f).toInt()
        for (k in 0 until max(1, n)) stamp(pt, dist, size, a, round, bleed, r, g, bl, out)
    }

    /** Satu cap: getaran per cap (ukuran, opasitas, posisi, putaran, kebulatan, warna), lalu ditahan atau digambar. */
    private fun stamp(
        pt: FloatArray, dist: Float, size0: Float, a0: Float, round0: Float, bleed: Float,
        r0: Float, g0: Float, b0: Float, out: DabList,
    ) {
        var size = size0
        var a = a0
        var round = round0
        if (b.sizeJitter > 0f) size *= 1f - b.sizeJitter * rnd.nextFloat() * 0.9f
        if (b.opacityJitter > 0f) a *= 1f - b.opacityJitter * rnd.nextFloat()
        if (b.roundJitterV > 0f) round *= 1f - b.roundJitterV * rnd.nextFloat() * 0.9f
        if (b.roundJitterH > 0f) {
            // Dipipihkan mendatar: lebar mengecil, tinggi tetap.
            val k = 1f - b.roundJitterH * rnd.nextFloat() * 0.9f
            size *= k
            round /= k
        }
        var x = pt[0]
        var y = pt[1]
        if (b.scatter > 0f) {
            val ang = rnd.nextFloat() * 2f * PI.toFloat()
            val rr = sqrt(rnd.nextFloat()) * b.scatter * size * 1.5f
            x += cos(ang) * rr
            y += sin(ang) * rr
        }
        if (b.jitterLateral > 0f) {
            val o = (rnd.nextFloat() * 2f - 1f) * b.jitterLateral * size
            x += -dirY * o
            y += dirX * o
        }
        if (b.jitterLinear > 0f) {
            val o = (rnd.nextFloat() * 2f - 1f) * b.jitterLinear * size * 1.5f
            x += dirX * o
            y += dirY * o
        }
        var angle = Math.toRadians(b.angle.toDouble()).toFloat() + baseAngle
        when (b.angleMode) {
            AngleMode.FOLLOW -> angle += atan2(dirY, dirX)
            AngleMode.TILT -> angle += if (hasTilt) pt[4] else atan2(dirY, dirX)
            AngleMode.FIXED -> if (b.orientScreen) angle -= viewRot
        }
        if (b.angleJitter > 0f) angle += b.angleJitter * (rnd.nextFloat() * 2f - 1f) * PI.toFloat()
        if (b.flipX) { angle += PI.toFloat(); round = -round }
        if (b.flipY) round = -round
        size = min(size, maxSize)
        if (size < 1f) {
            a *= max(size, 0.08f)
            size = 1f
        }
        var r = r0
        var g = g0
        var bl = b0
        if (colorDyn) {
            if (b.hueJitter > 0f || b.satJitter > 0f) {
                rgbToHsv(r, g, bl, hsvT)
                if (b.hueJitter > 0f) hsvT[0] = ((hsvT[0] + (rnd.nextFloat() * 2f - 1f) * b.hueJitter / 6f) % 1f + 1f) % 1f
                if (b.satJitter > 0f) hsvT[1] = hsvT[1] + (rnd.nextFloat() - hsvT[1]) * b.satJitter
                hsvToRgb(hsvT[0], hsvT[1], hsvT[2], rgbT)
                r = rgbT[0]; g = rgbT[1]; bl = rgbT[2]
            }
            if (b.lightJitter > 0f) { val k = rnd.nextFloat() * b.lightJitter; r += (1f - r) * k; g += (1f - g) * k; bl += (1f - bl) * k }
            if (b.darkJitter > 0f) { val k = 1f - rnd.nextFloat() * b.darkJitter; r *= k; g *= k; bl *= k }
            if (b.color2Jitter > 0f && rnd.nextFloat() < b.color2Jitter) { r = c2r; g = c2g; bl = c2b }
        }
        if (holdOn && !previewMode) {
            dv[0] = x; dv[1] = y; dv[2] = size; dv[3] = angle; dv[4] = round
            dv[5] = a.coerceIn(0f, 1f); dv[6] = r; dv[7] = g; dv[8] = bl; dv[9] = bleed
            held.add(dv, dist)
            // Yang sudah lebih jauh dari panjang runcing dilepas ke goresan permanen.
            while (held.count > 0 && held.firstDist() < dist - taperLen) {
                held.get(0, dv)
                out.addRaw(dv)
                held.removeFirst()
            }
        } else {
            out.add(x, y, size, angle, round, a.coerceIn(0f, 1f), r, g, bl, bleed)
        }
    }

    /** Lepas semua dab yang ditahan; bila [taper], ujung terakhir diruncingkan. */
    private fun releaseHeld(out: DabList, taper: Boolean) {
        if (held.count == 0) return
        val end = held.lastDist()
        for (i in 0 until held.count) {
            held.get(i, dv)
            if (taper && taperLen > 0f) {
                val f = ((end - held.distAt(i)) / taperLen).coerceIn(0f, 1f)
                var size = dv[2] * (tipMin + (1f - tipMin) * f)
                var a = dv[5] * (1f - b.taperOpacity * (1f - f))
                if (size < 1f) {
                    a *= max(size, 0.08f)
                    size = 1f
                }
                dv[2] = size
                dv[5] = a
            }
            out.addRaw(dv)
        }
        held.clear()
    }

    private val pa = FloatArray(5)
    private val pb = FloatArray(5)

    // ---- simpan/pulihkan keadaan penghalus dan jalur (untuk ekor sementara) ----
    private val snapF = FloatArray(64)
    private val snapL = LongArray(3)
    private var snapN = 0
    private var snapQ = false

    private fun save() {
        var i = 0
        fun f(v: Float) { snapF[i++] = v }
        f(mx); f(my); f(ex); f(ey); f(s1x); f(s1y); f(s2x); f(s2y); f(spd); f(vrx); f(vry)
        f(sx); f(sy); f(sp); f(st); f(sa); f(residual); f(travelled); f(dirX); f(dirY)
        f(vx); f(vy); f(lqx); f(lqy)
        for (a in arrayOf(p0, p1, cur)) for (v in a) f(v)
        snapL[0] = lastT; snapL[1] = lqt; snapL[2] = qTime
        snapN = nPts
        snapQ = haveQ
    }

    private fun restore() {
        var i = 0
        fun g(): Float = snapF[i++]
        mx = g(); my = g(); ex = g(); ey = g(); s1x = g(); s1y = g(); s2x = g(); s2y = g(); spd = g(); vrx = g(); vry = g()
        sx = g(); sy = g(); sp = g(); st = g(); sa = g(); residual = g(); travelled = g(); dirX = g(); dirY = g()
        vx = g(); vy = g(); lqx = g(); lqy = g()
        for (a in arrayOf(p0, p1, cur)) for (k in a.indices) a[k] = g()
        lastT = snapL[0]; lqt = snapL[1]; qTime = snapL[2]
        nPts = snapN
        haveQ = snapQ
    }

    /**
     * Dab sementara (digambar ulang tiap bingkai, tidak masuk goresan):
     *  1. "ekor": goresan seolah-olah menyusul ke ujung pena sekarang, memakai penghalus yang sama persis dengan
     *     goresan aslinya. Hasilnya selalu mulus (tanpa getar/kait), tersambung tanpa patahan dari goresan, dan
     *     berakhir tepat di ujung pena — persis seperti yang tergambar bila pena diangkat saat itu,
     *  2. prediksi [ms] ke depan bila diaktifkan.
     * Tidak mengubah keadaan goresan.
     */
    fun predict(ms: Float, out: DabList) {
        if (!started) return
        // Dab yang masih ditahan (belum diruncingkan) ikut ditampilkan sementara.
        for (i in 0 until held.count) {
            held.get(i, dv)
            out.addRaw(dv)
        }
        previewMode = true
        save()
        val keepRnd = rnd
        tailRnd.setSeed(tailSeed)
        rnd = tailRnd
        if (smoothing()) {
            val tx = lastRaw[0]
            val ty = lastRaw[1]
            var i = 0
            while (i < 240) {
                i++
                qTime += 8
                step(tx, ty, lastRaw[2], lastRaw[3], lastRaw[4], 8f, (1f - i / 10f).coerceAtLeast(0f), out)
                if (i >= 10 && hypot(sx - tx, sy - ty) < 0.3f) break
                if (out.size >= DabList.MAX_DABS) break
            }
        }
        if (nPts >= 2) line(cur, p1, out)
        // Prediksi: perpanjang sedikit searah gerak pena (dari titik mentah terakhir).
        val n = min(rawCount, RAW_N)
        if (ms > 0f && n >= 2 && mfR <= 0f) {
            val a = rawRing[(rawCount - 1) % RAW_N]
            var k = 1
            while (k < n - 1 && rawTime[(rawCount - 1) % RAW_N] - rawTime[(rawCount - 1 - k) % RAW_N] < 16L) k++
            val b0 = rawRing[(rawCount - 1 - k) % RAW_N]
            val dtv = (rawTime[(rawCount - 1) % RAW_N] - rawTime[(rawCount - 1 - k) % RAW_N]).coerceAtLeast(1L).toFloat()
            var px = (a[0] - b0[0]) / dtv * ms
            var py = (a[1] - b0[1]) / dtv * ms
            val lim = max(2f * base, 48f)
            val l = hypot(px, py)
            if (l > lim) { px *= lim / l; py *= lim / l }
            val e = if (nPts >= 2) p1 else cur
            e.copyInto(pa)
            e.copyInto(pb)
            pb[0] += px
            pb[1] += py
            line(pa, pb, out)
        }
        rnd = keepRnd
        restore()
        previewMode = false
    }

    /**
     * Pena diam tetapi masih menempel: penghalus terus berjalan dengan waktu supaya goresan menyusul ke ujung pena
     * (seperti Procreate), tidak menunggu pena bergerak lagi. Mengembalikan true bila goresan masih tertinggal.
     */
    fun idle(now: Long, out: DabList): Boolean {
        if (!started || !smoothing()) return false
        // Pena bergerak mengirim titik ±240 kali per detik; tanpa titik selama 24 ms berarti pena berhenti.
        if (now - lastT >= 24L) {
            var steps = 0
            while (now - lastT >= 8L && steps < 12) {
                lastT += 8L
                qTime = lastT
                step(lastRaw[0], lastRaw[1], lastRaw[2], lastRaw[3], lastRaw[4], 8f, 1f, out)
                steps++
            }
        }
        // Penyaring gerak sengaja berhenti sepanjang tali dari pena; yang dikejar adalah ujung tali.
        val tx = if (mfR > 0f) mx else lastRaw[0]
        val ty = if (mfR > 0f) my else lastRaw[1]
        return hypot(sx - tx, sy - ty) > 0.5f
    }

    /** RGB 0..1 → HSV (rona 0..1). */
    private fun rgbToHsv(r: Float, g: Float, b: Float, o: FloatArray) {
        val mx = max(r, max(g, b))
        val mn = min(r, min(g, b))
        val d = mx - mn
        var h = 0f
        if (d > 1e-6f) {
            h = when (mx) {
                r -> ((g - b) / d) % 6f
                g -> (b - r) / d + 2f
                else -> (r - g) / d + 4f
            } / 6f
            if (h < 0f) h += 1f
        }
        o[0] = h
        o[1] = if (mx > 1e-6f) d / mx else 0f
        o[2] = mx
    }

    private fun hsvToRgb(h: Float, s: Float, v: Float, o: FloatArray) {
        val hh = ((h % 1f) + 1f) % 1f * 6f
        val i = hh.toInt().coerceIn(0, 5)
        val f = hh - i
        val p = v * (1f - s)
        val q = v * (1f - s * f)
        val t = v * (1f - s * (1f - f))
        when (i) {
            0 -> { o[0] = v; o[1] = t; o[2] = p }
            1 -> { o[0] = q; o[1] = v; o[2] = p }
            2 -> { o[0] = p; o[1] = v; o[2] = t }
            3 -> { o[0] = p; o[1] = q; o[2] = v }
            4 -> { o[0] = t; o[1] = p; o[2] = v }
            else -> { o[0] = v; o[1] = p; o[2] = q }
        }
    }

}

/**
 * Kurva halus (Hermite monoton, tidak berayun) lewat (0,0), titik-titik [pts] (x0, y0, x1, y1, …), dan (1,1),
 * ditulis ke [lut] sebanyak lut.size nilai untuk x = 0..1.
 */
fun buildCurve(pts: List<Float>, lut: FloatArray) {
    val xs = ArrayList<Float>()
    val ys = ArrayList<Float>()
    xs.add(0f); ys.add(0f)
    val pairs = (0 until pts.size / 2).map { pts[it * 2].coerceIn(0f, 1f) to pts[it * 2 + 1].coerceIn(0f, 1f) }
        .filter { it.first > 0.001f && it.first < 0.999f }.sortedBy { it.first }
    for ((x, y) in pairs) if (x - xs.last() > 0.01f) { xs.add(x); ys.add(y) }
    if (1f - xs.last() < 0.01f) { xs.removeAt(xs.size - 1); ys.removeAt(ys.size - 1) }
    xs.add(1f); ys.add(1f)
    val n = xs.size
    val d = FloatArray(n - 1) { (ys[it + 1] - ys[it]) / (xs[it + 1] - xs[it]) }
    val m = FloatArray(n)
    m[0] = d[0]
    m[n - 1] = d[n - 2]
    for (i in 1 until n - 1) m[i] = if (d[i - 1] * d[i] <= 0f) 0f else (d[i - 1] + d[i]) / 2f
    for (i in 0 until n - 1) {
        if (d[i] == 0f) { m[i] = 0f; m[i + 1] = 0f; continue }
        val a = m[i] / d[i]
        val b = m[i + 1] / d[i]
        val h = a * a + b * b
        if (h > 9f) {
            val t = 3f / kotlin.math.sqrt(h)
            m[i] = t * a * d[i]
            m[i + 1] = t * b * d[i]
        }
    }
    val last = lut.size - 1
    var seg = 0
    for (k in 0..last) {
        val x = k.toFloat() / last
        while (seg < n - 2 && x > xs[seg + 1]) seg++
        val h = xs[seg + 1] - xs[seg]
        val t = ((x - xs[seg]) / h).coerceIn(0f, 1f)
        val t2 = t * t
        val t3 = t2 * t
        val y = (2 * t3 - 3 * t2 + 1) * ys[seg] + (t3 - 2 * t2 + t) * h * m[seg] +
            (-2 * t3 + 3 * t2) * ys[seg + 1] + (t3 - t2) * h * m[seg + 1]
        lut[k] = y.coerceIn(0f, 1f)
    }
}
