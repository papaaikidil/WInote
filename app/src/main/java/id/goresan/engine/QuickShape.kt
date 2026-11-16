package id.goresan.engine

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.round
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Bentuk otomatis (seperti QuickShape): coretan tangan yang ditahan di ujungnya dikenali sebagai garis lurus,
 * elips/lingkaran, segitiga, kotak, segi banyak, atau garis patah, lalu diganti versi yang rapi.
 */
object QuickShape {

    enum class Kind(val label: String) {
        LINE("Garis lurus"), POLYLINE("Garis patah"), TRIANGLE("Segitiga"), RECT("Kotak"),
        POLYGON("Segi banyak"), ELLIPSE("Elips"), CIRCLE("Lingkaran"), ARC("Busur"),
    }

    /**
     * Bentuk hasil. Garis/segi banyak: [v] berisi titik sudut (x, y berpasangan).
     * Elips: pusat [cx],[cy], jari-jari [a],[b], dan sudut [ang] (radian).
     * Busur: pusat [cx],[cy], jari-jari [a], sudut awal [ang], dan sapuan [sweep] (radian, bertanda).
     */
    class Shape(
        val kind: Kind,
        val v: FloatArray,
        val closed: Boolean,
        val cx: Float, val cy: Float,
        val a: Float = 0f, val b: Float = 0f, val ang: Float = 0f, val sweep: Float = 0f,
    ) {
        /** Diputar [rot] radian dan diperbesar [s] kali terhadap titik ([px], [py]). */
        fun transformed(px: Float, py: Float, s: Float, rot: Float): Shape {
            val c = cos(rot); val sn = sin(rot)
            fun tx(x: Float, y: Float) = px + ((x - px) * c - (y - py) * sn) * s
            fun ty(x: Float, y: Float) = py + ((x - px) * sn + (y - py) * c) * s
            val nv = FloatArray(v.size)
            var i = 0
            while (i < v.size) { nv[i] = tx(v[i], v[i + 1]); nv[i + 1] = ty(v[i], v[i + 1]); i += 2 }
            return Shape(kind, nv, closed, tx(cx, cy), ty(cx, cy), a * s, b * s, ang + rot, sweep)
        }

        /** Garis lurus dengan ujung akhir dipindah ke ([x], [y]). */
        fun withEnd(x: Float, y: Float): Shape {
            val nv = v.copyOf()
            nv[nv.size - 2] = x; nv[nv.size - 1] = y
            return Shape(kind, nv, closed, cx, cy)
        }

        /**
         * Versi presisi (jari kedua ditahan di layar): lingkaran sempurna, persegi, segitiga sama sisi, segi-n
         * beraturan, garis dan ruas garis patah pada kelipatan 15°, busur dengan sapuan kelipatan 45° (setengah
         * lingkaran, seperempat, …).
         */
        fun perfect(): Shape {
            when (kind) {
                Kind.LINE, Kind.POLYLINE -> {
                    val nv = v.copyOf()
                    var i = 2
                    while (i + 1 < nv.size) {
                        val dx = v[i] - v[i - 2]
                        val dy = v[i + 1] - v[i - 1]
                        val len = hypot(dx, dy)
                        val a = snapAngle(atan2(dy, dx), STEP15)
                        nv[i] = nv[i - 2] + len * cos(a)
                        nv[i + 1] = nv[i - 1] + len * sin(a)
                        i += 2
                    }
                    return Shape(kind, nv, closed, nv[0], nv[1])
                }
                Kind.ELLIPSE, Kind.CIRCLE -> {
                    val r = (a + b) / 2f
                    return Shape(Kind.CIRCLE, v, true, cx, cy, r, r, 0f)
                }
                Kind.ARC -> {
                    val q = (PI / 4).toFloat()
                    val sw = (round(abs(sweep) / q) * q).coerceAtLeast(q) * (if (sweep < 0) -1f else 1f)
                    // Sudut awal digeser supaya busur tetap di tempat yang sama (titik tengahnya), lalu dibulatkan 15°.
                    val mid = ang + sweep / 2f
                    val start = snapAngle(mid - sw / 2f, STEP15)
                    return Shape(Kind.ARC, v, false, cx, cy, a, a, start, sw)
                }
                Kind.RECT -> {
                    if (v.size < 8) return this
                    val w = hypot(v[2] - v[0], v[3] - v[1])
                    val h = hypot(v[4] - v[2], v[5] - v[3])
                    val side = (w + h) / 2f
                    val th = snapAngle(atan2(v[3] - v[1], v[2] - v[0]), STEP15)
                    val turn = if (cross(v, 0) >= 0f) 1f else -1f
                    val ex = cos(th); val ey = sin(th)
                    val fx = -ey * turn; val fy = ex * turn
                    val x0 = cx - (ex + fx) * side / 2f
                    val y0 = cy - (ey + fy) * side / 2f
                    val out = floatArrayOf(
                        x0, y0,
                        x0 + ex * side, y0 + ey * side,
                        x0 + (ex + fx) * side, y0 + (ey + fy) * side,
                        x0 + fx * side, y0 + fy * side,
                    )
                    return Shape(Kind.RECT, out, true, cx, cy)
                }
                Kind.TRIANGLE, Kind.POLYGON -> {
                    val m = v.size / 2
                    if (m < 3) return this
                    var r = 0f
                    for (k in 0 until m) r += hypot(v[2 * k] - cx, v[2 * k + 1] - cy)
                    r /= m
                    val turn = if (cross(v, 0) >= 0f) 1f else -1f
                    val a0 = snapAngle(atan2(v[1] - cy, v[0] - cx), STEP15)
                    val step = (2 * PI / m).toFloat() * turn
                    val out = FloatArray(m * 2)
                    for (k in 0 until m) {
                        out[2 * k] = cx + r * cos(a0 + step * k)
                        out[2 * k + 1] = cy + r * sin(a0 + step * k)
                    }
                    return Shape(kind, out, true, cx, cy)
                }
            }
            @Suppress("UNREACHABLE_CODE")
            return this
        }

        /** Arah putaran titik sudut (tanda luas bertanda). */
        private fun cross(p: FloatArray, @Suppress("UNUSED_PARAMETER") from: Int): Float {
            var s = 0f
            val m = p.size / 2
            for (k in 0 until m) {
                val j = (k + 1) % m
                s += p[2 * k] * p[2 * j + 1] - p[2 * j] * p[2 * k + 1]
            }
            return s
        }

        /** Titik rapat (jarak sekitar [step] piksel) di sepanjang bentuk, untuk digambar dengan kuas. */
        fun path(step: Float = 3f): FloatArray {
            val out = ArrayList<Float>()
            if (kind == Kind.ARC) {
                val n = (abs(sweep) * a / step).toInt().coerceIn(8, 4000)
                for (k in 0..n) {
                    val t = ang + sweep * k / n
                    out.add(cx + a * cos(t)); out.add(cy + a * sin(t))
                }
            } else if (kind == Kind.ELLIPSE || kind == Kind.CIRCLE) {
                val per = (PI * (3 * (a + b) - sqrt((3 * a + b) * (a + 3 * b)))).toFloat()
                val n = (per / step).toInt().coerceIn(32, 4000)
                val c = cos(ang); val sn = sin(ang)
                for (k in 0..n) {
                    val t = k * 2.0 * PI / n
                    val ux = a * cos(t).toFloat(); val uy = b * sin(t).toFloat()
                    out.add(cx + ux * c - uy * sn); out.add(cy + ux * sn + uy * c)
                }
            } else {
                val m = v.size / 2
                val last = if (closed) m else m - 1
                out.add(v[0]); out.add(v[1])
                for (k in 0 until last) {
                    val x0 = v[2 * k]; val y0 = v[2 * k + 1]
                    val j = (k + 1) % m
                    val x1 = v[2 * j]; val y1 = v[2 * j + 1]
                    val len = hypot(x1 - x0, y1 - y0)
                    val n = (len / step).toInt().coerceAtLeast(1)
                    for (i in 1..n) {
                        val t = i.toFloat() / n
                        out.add(x0 + (x1 - x0) * t); out.add(y0 + (y1 - y0) * t)
                    }
                }
            }
            return out.toFloatArray()
        }
    }

    const val STEP15 = (PI / 12).toFloat()

    /** Sudut dibulatkan ke kelipatan [step] terdekat. */
    fun snapAngle(a: Float, step: Float): Float = round(a / step) * step

    /** Kenali bentuk dari titik coretan ([n] titik). Null bila tidak cukup mirip bentuk apa pun. */
    fun recognize(xs: FloatArray, ys: FloatArray, n: Int): Shape? {
        if (n < 3) return null
        var len = 0f
        for (i in 1 until n) len += hypot(xs[i] - xs[i - 1], ys[i] - ys[i - 1])
        if (len < 24f) return null
        val p = resample(xs, ys, n, len, (len / 3f).toInt().coerceIn(24, 400))
        val m = p.size / 2

        var x0 = Float.MAX_VALUE; var y0 = Float.MAX_VALUE; var x1 = -Float.MAX_VALUE; var y1 = -Float.MAX_VALUE
        for (i in 0 until m) {
            x0 = min(x0, p[2 * i]); x1 = max(x1, p[2 * i]); y0 = min(y0, p[2 * i + 1]); y1 = max(y1, p[2 * i + 1])
        }
        val big = max(x1 - x0, y1 - y0)
        val diag = hypot(x1 - x0, y1 - y0)

        // 1. Garis lurus.
        val sx = p[0]; val sy = p[1]; val ex = p[2 * m - 2]; val ey = p[2 * m - 1]
        val chord = hypot(ex - sx, ey - sy)
        if (chord > 0.8f * len) {
            var maxDev = 0f
            var sumDev = 0f
            for (i in 0 until m) {
                val d = segDist(p[2 * i], p[2 * i + 1], sx, sy, ex, ey)
                maxDev = max(maxDev, d); sumDev += d
            }
            if (maxDev <= max(0.09f * chord, 4f) && sumDev / m <= max(0.04f * chord, 2f)) {
                return Shape(Kind.LINE, floatArrayOf(sx, sy, ex, ey), false, sx, sy)
            }
        }

        // 2. Bentuk tertutup: ujung kembali dekat ke awal (boleh sedikit kelewatan).
        val closeTol = 0.25f * big
        var bestD = hypot(ex - sx, ey - sy)
        var cutStart = 0
        var cutEnd = m - 1
        val q = (m * 0.25f).toInt()
        for (j in 1..q) {
            val d1 = hypot(ex - p[2 * j], ey - p[2 * j + 1])
            if (d1 < bestD) { bestD = d1; cutStart = j; cutEnd = m - 1 }
            val k = m - 1 - j
            val d2 = hypot(sx - p[2 * k], sy - p[2 * k + 1])
            if (d2 < bestD) { bestD = d2; cutStart = 0; cutEnd = k }
        }
        if (bestD <= closeTol && big >= 16f) {
            val c = p.copyOfRange(2 * cutStart, 2 * cutEnd + 2)
            return closedShape(c, big)
        }

        // 3. Busur (lengkung yang mengikuti satu lingkaran).
        arc(p)?.let { return it }

        // 4. Garis patah dengan sudut yang tegas (misalnya huruf V atau L).
        val verts = dpOpen(p, 0.07f * diag)
        val nv = verts.size / 2
        if (nv in 3..5 && sharpCorners(verts, false)) {
            val err = polyError(p, verts, false)
            if (err <= 0.03f * diag) {
                return Shape(Kind.POLYLINE, verts, false, verts[0], verts[1])
            }
        }
        return null
    }

    /** Busur lingkaran: kecocokan lingkaran (metode Kåsa) pada jalur terbuka. */
    private fun arc(p: FloatArray): Shape? {
        val m = p.size / 2
        var mx = 0.0; var my = 0.0
        for (i in 0 until m) { mx += p[2 * i]; my += p[2 * i + 1] }
        mx /= m; my /= m
        // Minimalkan Σ (u² + v² + D u + E v + F)² dalam koordinat yang dipusatkan.
        var suu = 0.0; var suv = 0.0; var svv = 0.0; var su = 0.0; var sv = 0.0
        var sz = 0.0; var szu = 0.0; var szv = 0.0
        for (i in 0 until m) {
            val u = p[2 * i] - mx; val v = p[2 * i + 1] - my; val z = u * u + v * v
            suu += u * u; suv += u * v; svv += v * v; su += u; sv += v
            sz += z; szu += z * u; szv += z * v
        }
        val n = m.toDouble()
        // [suu suv su; suv svv sv; su sv n] [D E F]ᵀ = -[szu szv sz]ᵀ
        val a = arrayOf(doubleArrayOf(suu, suv, su, -szu), doubleArrayOf(suv, svv, sv, -szv), doubleArrayOf(su, sv, n, -sz))
        for (c in 0 until 3) {
            var piv = c
            for (r in c + 1 until 3) if (abs(a[r][c]) > abs(a[piv][c])) piv = r
            if (abs(a[piv][c]) < 1e-9) return null
            val t = a[c]; a[c] = a[piv]; a[piv] = t
            for (r in 0 until 3) if (r != c) {
                val f = a[r][c] / a[c][c]
                for (k in c until 4) a[r][k] -= f * a[c][k]
            }
        }
        val d = a[0][3] / a[0][0]; val e = a[1][3] / a[1][1]; val f = a[2][3] / a[2][2]
        val cu = -d / 2; val cv = -e / 2
        val r2 = cu * cu + cv * cv - f
        if (r2 <= 0) return null
        val r = sqrt(r2)
        val cx = mx + cu; val cy = my + cv
        var x0 = Double.MAX_VALUE; var x1 = -Double.MAX_VALUE; var y0 = Double.MAX_VALUE; var y1 = -Double.MAX_VALUE
        var err = 0.0; var maxErr = 0.0
        for (i in 0 until m) {
            val dd = abs(hypot(p[2 * i] - cx, p[2 * i + 1] - cy) - r)
            err += dd; maxErr = max(maxErr, dd)
            x0 = min(x0, p[2 * i].toDouble()); x1 = max(x1, p[2 * i].toDouble())
            y0 = min(y0, p[2 * i + 1].toDouble()); y1 = max(y1, p[2 * i + 1].toDouble())
        }
        err /= m
        if (r > 4 * hypot(x1 - x0, y1 - y0)) return null
        if (err > 0.035 * r || maxErr > 0.1 * r) return null
        // Sapuan sudut: dijumlahkan bertahap supaya tidak meloncat di ±180°.
        var prev = atan2(p[1] - cy, p[0] - cx)
        val start = prev
        var sweep = 0.0
        for (i in 1 until m) {
            val cur = atan2(p[2 * i + 1] - cy, p[2 * i] - cx)
            var dlt = cur - prev
            if (dlt > PI) dlt -= 2 * PI
            if (dlt < -PI) dlt += 2 * PI
            sweep += dlt
            prev = cur
        }
        if (abs(sweep) < PI / 4.5 || abs(sweep) > PI * 1.8) return null
        return Shape(Kind.ARC, FloatArray(0), false, cx.toFloat(), cy.toFloat(), r.toFloat(), r.toFloat(), start.toFloat(), sweep.toFloat())
    }

    /** Segi banyak tidak memotong dirinya sendiri. */
    private fun simple(v: FloatArray): Boolean {
        val m = v.size / 2
        fun cross(ax: Float, ay: Float, bx: Float, by: Float, cx: Float, cy: Float) = (bx - ax) * (cy - ay) - (by - ay) * (cx - ax)
        for (i in 0 until m) for (j in i + 2 until m) {
            if (i == 0 && j == m - 1) continue
            val a = i; val b = (i + 1) % m; val c = j; val d = (j + 1) % m
            val d1 = cross(v[2 * c], v[2 * c + 1], v[2 * d], v[2 * d + 1], v[2 * a], v[2 * a + 1])
            val d2 = cross(v[2 * c], v[2 * c + 1], v[2 * d], v[2 * d + 1], v[2 * b], v[2 * b + 1])
            val d3 = cross(v[2 * a], v[2 * a + 1], v[2 * b], v[2 * b + 1], v[2 * c], v[2 * c + 1])
            val d4 = cross(v[2 * a], v[2 * a + 1], v[2 * b], v[2 * b + 1], v[2 * d], v[2 * d + 1])
            if ((d1 > 0) != (d2 > 0) && (d3 > 0) != (d4 > 0)) return false
        }
        return true
    }

    /** Semua sudut dalam segi banyak/garis patah cukup tajam (tidak lebih tumpul dari 145°). */
    private fun sharpCorners(v: FloatArray, closed: Boolean): Boolean {
        val m = v.size / 2
        val range = if (closed) 0 until m else 1 until m - 1
        for (k in range) {
            val pk = (k + m - 1) % m; val nk = (k + 1) % m
            val ax = v[2 * pk] - v[2 * k]; val ay = v[2 * pk + 1] - v[2 * k + 1]
            val bx = v[2 * nk] - v[2 * k]; val by = v[2 * nk + 1] - v[2 * k + 1]
            val la = hypot(ax, ay); val lb = hypot(bx, by)
            if (la < 1e-3f || lb < 1e-3f) return false
            if ((ax * bx + ay * by) / (la * lb) < -0.82f) return false
        }
        return true
    }

    private fun closedShape(p: FloatArray, big: Float): Shape? {
        val m = p.size / 2
        if (m < 8) return null
        val scale = big / 2f

        // Elips: arah dari kovarians, pusat dari kotak pembatas dalam arah itu, jari-jari dari kuadrat terkecil.
        var mx = 0.0; var my = 0.0
        for (i in 0 until m) { mx += p[2 * i]; my += p[2 * i + 1] }
        mx /= m; my /= m
        var sxx = 0.0; var syy = 0.0; var sxy = 0.0
        for (i in 0 until m) {
            val dx = p[2 * i] - mx; val dy = p[2 * i + 1] - my
            sxx += dx * dx; syy += dy * dy; sxy += dx * dy
        }
        val th = 0.5 * atan2(2 * sxy, sxx - syy)
        val ct = cos(th); val st = sin(th)
        var u0 = Double.MAX_VALUE; var u1 = -Double.MAX_VALUE; var v0 = Double.MAX_VALUE; var v1 = -Double.MAX_VALUE
        for (i in 0 until m) {
            val dx = p[2 * i] - mx; val dy = p[2 * i + 1] - my
            val u = dx * ct + dy * st; val v = -dx * st + dy * ct
            u0 = min(u0, u); u1 = max(u1, u); v0 = min(v0, v); v1 = max(v1, v)
        }
        val cu = (u0 + u1) / 2; val cv = (v0 + v1) / 2
        val ecx = mx + cu * ct - cv * st
        val ecy = my + cu * st + cv * ct
        // Kuadrat terkecil untuk A = 1/a², B = 1/b² pada A u² + B v² = 1.
        var s11 = 0.0; var s12 = 0.0; var s22 = 0.0; var r1 = 0.0; var r2 = 0.0
        for (i in 0 until m) {
            val dx = p[2 * i] - ecx; val dy = p[2 * i + 1] - ecy
            val u = dx * ct + dy * st; val v = -dx * st + dy * ct
            val uu = u * u; val vv = v * v
            s11 += uu * uu; s12 += uu * vv; s22 += vv * vv; r1 += uu; r2 += vv
        }
        val det = s11 * s22 - s12 * s12
        var ea = ((u1 - u0) / 2).toFloat()
        var eb = ((v1 - v0) / 2).toFloat()
        if (abs(det) > 1e-9) {
            val aa = (r1 * s22 - r2 * s12) / det
            val bb = (r2 * s11 - r1 * s12) / det
            if (aa > 0 && bb > 0) { ea = (1 / sqrt(aa)).toFloat(); eb = (1 / sqrt(bb)).toFloat() }
        }
        var ellErr = 0f
        for (i in 0 until m) {
            val dx = (p[2 * i] - ecx).toFloat(); val dy = (p[2 * i + 1] - ecy).toFloat()
            val u = dx * ct.toFloat() + dy * st.toFloat(); val v = -dx * st.toFloat() + dy * ct.toFloat()
            val rho = hypot(u, v)
            if (rho < 1e-3f) continue
            val cphi = u / rho; val sphi = v / rho
            val re = ea * eb / sqrt((eb * cphi) * (eb * cphi) + (ea * sphi) * (ea * sphi))
            ellErr += abs(rho - re)
        }
        ellErr /= m * scale

        // Segi banyak dari sudut-sudut yang menonjol.
        val verts = mergeStraight(dpClosed(p, 0.12f * scale), 0.16f * scale)
        val nv = verts.size / 2
        val polyErr = if (nv >= 3) polyError(p, verts, true) / scale else Float.MAX_VALUE

        val ellOk = ellErr <= 0.11f
        val polyOk = nv in 3..8 && polyErr <= 0.08f && sharpCorners(verts, true) && simple(verts)
        val usePoly = polyOk && (!ellOk || polyErr < ellErr * 0.8f)
        if (usePoly) {
            if (nv == 3) {
                return Shape(Kind.TRIANGLE, verts, true, centroidX(verts), centroidY(verts))
            }
            if (nv == 4) rectFrom(verts)?.let { return it }
            return Shape(Kind.POLYGON, verts, true, centroidX(verts), centroidY(verts))
        }
        if (ellOk) {
            val ex = ecx.toFloat(); val ey = ecy.toFloat()
            if (min(ea, eb) / max(ea, eb) >= 0.86f) {
                val r = (ea + eb) / 2f
                return Shape(Kind.CIRCLE, FloatArray(0), true, ex, ey, r, r, 0f)
            }
            return Shape(Kind.ELLIPSE, FloatArray(0), true, ex, ey, ea, eb, th.toFloat())
        }
        return null
    }

    /** Segi empat yang sudutnya mendekati siku menjadi persegi panjang (lurus bila hampir mendatar). */
    private fun rectFrom(v: FloatArray): Shape? {
        for (k in 0 until 4) {
            val ax = v[2 * ((k + 3) % 4)] - v[2 * k]; val ay = v[2 * ((k + 3) % 4) + 1] - v[2 * k + 1]
            val bx = v[2 * ((k + 1) % 4)] - v[2 * k]; val by = v[2 * ((k + 1) % 4) + 1] - v[2 * k + 1]
            val cosA = (ax * bx + ay * by) / (hypot(ax, ay) * hypot(bx, by))
            if (abs(cosA) > 0.34f) return null // lebih dari ±20° dari siku
        }
        // Arah rata-rata sisi (periode 90°).
        var sc = 0.0; var ss = 0.0
        for (k in 0 until 4) {
            val j = (k + 1) % 4
            val a = atan2((v[2 * j + 1] - v[2 * k + 1]).toDouble(), (v[2 * j] - v[2 * k]).toDouble())
            val w = hypot(v[2 * j] - v[2 * k], v[2 * j + 1] - v[2 * k + 1]).toDouble()
            sc += w * cos(4 * a); ss += w * sin(4 * a)
        }
        var th = atan2(ss, sc) / 4
        val snap = round(th / (PI / 2)) * (PI / 2)
        if (abs(th - snap) < PI / 18) th = snap
        val c = cos(th); val s = sin(th)
        var u0 = Double.MAX_VALUE; var u1 = -Double.MAX_VALUE; var w0 = Double.MAX_VALUE; var w1 = -Double.MAX_VALUE
        for (k in 0 until 4) {
            val x = v[2 * k].toDouble(); val y = v[2 * k + 1].toDouble()
            val u = x * c + y * s; val w = -x * s + y * c
            u0 = min(u0, u); u1 = max(u1, u); w0 = min(w0, w); w1 = max(w1, w)
        }
        // Rata-rata sisi berhadapan, bukan kotak terluar, supaya tidak membesar karena sudut yang meleset.
        val us = DoubleArray(4); val ws = DoubleArray(4)
        for (k in 0 until 4) {
            val x = v[2 * k].toDouble(); val y = v[2 * k + 1].toDouble()
            us[k] = x * c + y * s; ws[k] = -x * s + y * c
        }
        val su = us.sorted(); val sw = ws.sorted()
        val ua = (su[0] + su[1]) / 2; val ub = (su[2] + su[3]) / 2
        val wa = (sw[0] + sw[1]) / 2; val wb = (sw[2] + sw[3]) / 2
        if (ub - ua < 2 || wb - wa < 2) return null
        val corners = doubleArrayOf(ua, wa, ub, wa, ub, wb, ua, wb)
        val out = FloatArray(8)
        for (k in 0 until 4) {
            val u = corners[2 * k]; val w = corners[2 * k + 1]
            out[2 * k] = (u * c - w * s).toFloat(); out[2 * k + 1] = (u * s + w * c).toFloat()
        }
        return Shape(Kind.RECT, out, true, centroidX(out), centroidY(out))
    }

    private fun centroidX(v: FloatArray): Float { var s = 0f; for (k in 0 until v.size / 2) s += v[2 * k]; return s / (v.size / 2) }
    private fun centroidY(v: FloatArray): Float { var s = 0f; for (k in 0 until v.size / 2) s += v[2 * k + 1]; return s / (v.size / 2) }

    private fun resample(xs: FloatArray, ys: FloatArray, n: Int, len: Float, count: Int): FloatArray {
        val out = FloatArray(count * 2)
        val step = len / (count - 1)
        out[0] = xs[0]; out[1] = ys[0]
        var k = 1
        var acc = 0f
        var px = xs[0]; var py = ys[0]
        var i = 1
        while (i < n && k < count) {
            val d = hypot(xs[i] - px, ys[i] - py)
            if (acc + d >= step && d > 0f) {
                val t = (step - acc) / d
                val nx = px + (xs[i] - px) * t
                val ny = py + (ys[i] - py) * t
                out[2 * k] = nx; out[2 * k + 1] = ny
                k++
                px = nx; py = ny
                acc = 0f
            } else {
                acc += d
                px = xs[i]; py = ys[i]
                i++
            }
        }
        while (k < count) { out[2 * k] = xs[n - 1]; out[2 * k + 1] = ys[n - 1]; k++ }
        return out
    }

    private fun segDist(x: Float, y: Float, ax: Float, ay: Float, bx: Float, by: Float): Float {
        val dx = bx - ax; val dy = by - ay
        val l2 = dx * dx + dy * dy
        if (l2 < 1e-6f) return hypot(x - ax, y - ay)
        val t = (((x - ax) * dx + (y - ay) * dy) / l2).coerceIn(0f, 1f)
        return hypot(x - (ax + dx * t), y - (ay + dy * t))
    }

    /** Douglas–Peucker pada jalur terbuka; hasil: titik sudut berpasangan x, y. */
    private fun dpOpen(p: FloatArray, eps: Float): FloatArray {
        val m = p.size / 2
        val keep = BooleanArray(m)
        keep[0] = true; keep[m - 1] = true
        dp(p, 0, m - 1, eps, keep)
        return pick(p, keep)
    }

    /** Douglas–Peucker pada jalur tertutup: dibelah di titik terjauh dari titik awal. */
    private fun dpClosed(p: FloatArray, eps: Float): FloatArray {
        val m = p.size / 2
        var far = 0
        var fd = -1f
        for (i in 1 until m) {
            val d = hypot(p[2 * i] - p[0], p[2 * i + 1] - p[1])
            if (d > fd) { fd = d; far = i }
        }
        val keep = BooleanArray(m)
        keep[0] = true; keep[far] = true
        dp(p, 0, far, eps, keep)
        // Paruh kedua: dari titik terjauh sampai kembali ke awal.
        val ext = FloatArray((m - far + 1) * 2)
        for (i in far until m) { ext[2 * (i - far)] = p[2 * i]; ext[2 * (i - far) + 1] = p[2 * i + 1] }
        ext[ext.size - 2] = p[0]; ext[ext.size - 1] = p[1]
        val keep2 = BooleanArray(ext.size / 2)
        keep2[0] = true; keep2[keep2.size - 1] = true
        dp(ext, 0, keep2.size - 1, eps, keep2)
        for (i in 1 until keep2.size - 1) if (keep2[i]) keep[far + i] = true
        // Titik awal coretan biasanya bukan sudut: dibuang bila hampir segaris dengan tetangganya.
        return pick(p, keep)
    }

    private fun dp(p: FloatArray, a: Int, b: Int, eps: Float, keep: BooleanArray) {
        if (b <= a + 1) return
        var md = -1f
        var mi = a
        for (i in a + 1 until b) {
            val d = segDist(p[2 * i], p[2 * i + 1], p[2 * a], p[2 * a + 1], p[2 * b], p[2 * b + 1])
            if (d > md) { md = d; mi = i }
        }
        if (md > eps) {
            keep[mi] = true
            dp(p, a, mi, eps, keep)
            dp(p, mi, b, eps, keep)
        }
    }

    private fun pick(p: FloatArray, keep: BooleanArray): FloatArray {
        val out = ArrayList<Float>()
        for (i in keep.indices) if (keep[i]) { out.add(p[2 * i]); out.add(p[2 * i + 1]) }
        return out.toFloatArray()
    }

    /** Titik sudut yang hampir lurus (atau terlalu berdekatan) pada segi banyak tertutup digabung. */
    private fun mergeStraight(v0: FloatArray, minGap: Float): FloatArray {
        var v = v0
        var changed = true
        while (changed && v.size / 2 > 3) {
            changed = false
            val m = v.size / 2
            var worst = -1
            var worstScore = 0f
            for (k in 0 until m) {
                val pk = (k + m - 1) % m; val nk = (k + 1) % m
                val ax = v[2 * pk] - v[2 * k]; val ay = v[2 * pk + 1] - v[2 * k + 1]
                val bx = v[2 * nk] - v[2 * k]; val by = v[2 * nk + 1] - v[2 * k + 1]
                val la = hypot(ax, ay); val lb = hypot(bx, by)
                val cosA = if (la < 1e-3f || lb < 1e-3f) -1f else (ax * bx + ay * by) / (la * lb)
                // cos mendekati -1 = lurus (sudut 180°).
                val straight = cosA < -0.94f // lebih lurus dari ±20°
                val tooClose = min(la, lb) < minGap
                val score = if (straight) 2f + (-cosA) else if (tooClose) 1f + (minGap - min(la, lb)) / minGap else 0f
                if (score > worstScore) { worstScore = score; worst = k }
            }
            if (worst >= 0) {
                val out = FloatArray(v.size - 2)
                var j = 0
                for (k in 0 until m) if (k != worst) { out[j++] = v[2 * k]; out[j++] = v[2 * k + 1] }
                v = out
                changed = true
            }
        }
        return v
    }

    /** Rata-rata jarak titik coretan ke sisi-sisi segi banyak. */
    private fun polyError(p: FloatArray, v: FloatArray, closed: Boolean): Float {
        val m = p.size / 2
        val nv = v.size / 2
        val segs = if (closed) nv else nv - 1
        var sum = 0f
        for (i in 0 until m) {
            var best = Float.MAX_VALUE
            for (k in 0 until segs) {
                val j = (k + 1) % nv
                best = min(best, segDist(p[2 * i], p[2 * i + 1], v[2 * k], v[2 * k + 1], v[2 * j], v[2 * j + 1]))
            }
            sum += best
        }
        return sum / m
    }
}
