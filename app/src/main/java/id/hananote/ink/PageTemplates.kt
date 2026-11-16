package id.hananote.ink

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.text.TextPaint
import id.hananote.data.PaperSize
import id.hananote.data.PaperTemplate
import kotlin.math.max
import kotlin.math.min

/**
 * Templat halaman siap pakai: daftar tugas, agenda harian, perencana mingguan, catatan rapat, dan
 * catatan Cornell. Digambar langsung (bukan gambar), jadi tajam di semua ukuran kertas dan ikut
 * terang/gelap kertasnya.
 */
object PageTemplates {

    val all = listOf(PaperTemplate.TODO, PaperTemplate.DAILY, PaperTemplate.WEEKLY, PaperTemplate.MEETING, PaperTemplate.CORNELL)

    private class Tools(dark: Boolean, val hair: Float, w: Float) {
        val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            color = if (dark) 0x40FFFFFF else 0x3833415C
            strokeWidth = max(0.6f, hair)
        }
        val strong = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            color = if (dark) 0x70FFFFFF else 0x6633415C
            strokeWidth = max(1f, hair * 1.5f)
        }
        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = if (dark) 0x14FFFFFF else 0x0F33415C
        }
        val label = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = if (dark) 0x99FFFFFF.toInt() else 0x9933415C.toInt()
            textSize = (w * 0.019f).coerceIn(8f, 13f)
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        }
        val small = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = if (dark) 0x80FFFFFF.toInt() else 0x8033415C.toInt()
            textSize = (w * 0.015f).coerceIn(6.5f, 10f)
        }
    }

    /**
     * Gambar templat [t] di kertas berukuran [w] × [h] poin. [hair] tebal garis paling tipis (dipakai
     * pratinjau kecil supaya garis tetap terlihat).
     */
    fun draw(c: Canvas, t: PaperTemplate, w: Float, h: Float, dark: Boolean, hair: Float = 0f) {
        val k = Tools(dark, hair, w)
        when (t) {
            PaperTemplate.TODO -> todo(c, w, h, k)
            PaperTemplate.DAILY -> daily(c, w, h, k)
            PaperTemplate.WEEKLY -> weekly(c, w, h, k)
            PaperTemplate.MEETING -> meeting(c, w, h, k)
            PaperTemplate.CORNELL -> cornell(c, w, h, k)
            else -> Unit
        }
    }

    private val gap get() = 8f * PaperSize.mmToPt(1f)
    private fun margin(w: Float) = max(18f, w * 0.06f)

    /** Judul di kiri atas dan isian tanggal di kanan atas. Hasil: posisi y garis bawah kepala. */
    private fun header(c: Canvas, w: Float, title: String, k: Tools, field: String = "Tanggal"): Float {
        val m = margin(w)
        val y = m + k.label.textSize
        c.drawText(title, m, y, k.label)
        val fw = k.small.measureText("$field: ")
        val fx = w - m - max(w * 0.28f, fw + 60f)
        c.drawText("$field:", fx, y, k.small)
        c.drawLine(fx + fw, y + 2f, w - m, y + 2f, k.line)
        val under = y + k.label.textSize * 0.9f
        c.drawLine(m, under, w - m, under, k.strong)
        return under
    }

    private fun checkbox(c: Canvas, x: Float, lineY: Float, size: Float, k: Tools) {
        val b = lineY - size * 0.35f
        c.drawRoundRect(x, b - size, x + size, b, size * 0.18f, size * 0.18f, k.strong)
    }

    private fun ruled(c: Canvas, l: Float, t: Float, r: Float, b: Float, k: Tools, step: Float = gap, boxes: Boolean = false) {
        var y = t + step
        while (y <= b + 0.5f) {
            c.drawLine(l, y, r, y, k.line)
            if (boxes) checkbox(c, l + 2f, y, step * 0.45f, k)
            y += step
        }
    }

    private fun section(c: Canvas, l: Float, t: Float, r: Float, b: Float, title: String, k: Tools): Float {
        c.drawRoundRect(l, t, r, b, 6f, 6f, k.strong)
        val hh = k.small.textSize * 2f
        c.drawRect(l + 1f, t + 1f, r - 1f, t + hh, k.fill)
        c.drawText(title, l + 8f, t + hh * 0.68f, k.small)
        return t + hh
    }

    private fun todo(c: Canvas, w: Float, h: Float, k: Tools) {
        val m = margin(w)
        val top = header(c, w, "Daftar tugas", k)
        ruled(c, m, top + gap * 0.5f, w - m, h - m, k, boxes = true)
    }

    private fun daily(c: Canvas, w: Float, h: Float, k: Tools) {
        val m = margin(w)
        val top = header(c, w, "Agenda harian", k, "Hari/tanggal") + gap * 0.5f
        val bottom = h - m
        val split = m + (w - 2 * m) * 0.58f
        // Jadwal per jam.
        val hours = (6..21).toList()
        val row = (bottom - top) / hours.size
        val timeW = k.small.measureText("00.00") + 10f
        c.drawLine(m + timeW, top, m + timeW, bottom, k.line)
        hours.forEachIndexed { i, hr ->
            val y = top + row * i
            c.drawText(String.format("%02d.00", hr), m, y + k.small.textSize + 2f, k.small)
            c.drawLine(m, y + row, split - 8f, y + row, k.line)
        }
        c.drawLine(m, top, split - 8f, top, k.line)
        // Kolom kanan: prioritas, tugas, catatan.
        val l = split + 8f
        val r = w - m
        val pH = gap * 4.2f
        var y = section(c, l, top, r, top + pH, "Prioritas", k)
        ruled(c, l + 6f, y - gap * 0.2f, r - 6f, top + pH - 4f, k, boxes = true)
        val tTop = top + pH + 10f
        val tH = gap * 9.2f
        y = section(c, l, tTop, r, tTop + tH, "Tugas", k)
        ruled(c, l + 6f, y - gap * 0.2f, r - 6f, tTop + tH - 4f, k, boxes = true)
        val nTop = tTop + tH + 10f
        if (nTop < bottom - gap * 2) {
            y = section(c, l, nTop, r, bottom, "Catatan", k)
            ruled(c, l + 6f, y - gap * 0.2f, r - 6f, bottom - 4f, k)
        }
    }

    private fun weekly(c: Canvas, w: Float, h: Float, k: Tools) {
        val m = margin(w)
        val top = header(c, w, "Perencana mingguan", k, "Minggu") + gap * 0.4f
        val days = listOf("Senin", "Selasa", "Rabu", "Kamis", "Jumat", "Sabtu", "Minggu", "Catatan")
        val cols = if (w > h) 4 else 2
        val rows = (days.size + cols - 1) / cols
        val cw = (w - 2 * m - (cols - 1) * 8f) / cols
        val ch = (h - m - top - (rows - 1) * 8f) / rows
        days.forEachIndexed { i, d ->
            val col = i % cols
            val rw = i / cols
            val l = m + col * (cw + 8f)
            val t = top + rw * (ch + 8f)
            val y = section(c, l, t, l + cw, t + ch, d, k)
            ruled(c, l + 6f, y - gap * 0.3f, l + cw - 6f, t + ch - 4f, k, boxes = d != "Catatan")
        }
    }

    private fun meeting(c: Canvas, w: Float, h: Float, k: Tools) {
        val m = margin(w)
        var y = header(c, w, "Catatan rapat", k) + gap
        for (f in listOf("Topik", "Peserta", "Tempat")) {
            c.drawText("$f:", m, y - 4f, k.small)
            c.drawLine(m + k.small.measureText("$f: ") + 4f, y, w - m, y, k.line)
            y += gap
        }
        y += gap * 0.3f
        val actionTop = h - m - min(h * 0.28f, gap * 9f)
        c.drawText("Catatan", m, y + k.small.textSize, k.label)
        ruled(c, m, y + k.small.textSize + 4f, w - m, actionTop - gap * 0.6f, k)
        val inner = section(c, m, actionTop, w - m, h - m, "Tindak lanjut", k)
        // Kolom: tugas | penanggung jawab | tenggat.
        val c1 = m + (w - 2 * m) * 0.6f
        val c2 = m + (w - 2 * m) * 0.8f
        c.drawText("PJ", c1 + 6f, inner - k.small.textSize * 0.6f, k.small)
        c.drawText("Tenggat", c2 + 6f, inner - k.small.textSize * 0.6f, k.small)
        c.drawLine(c1, actionTop, c1, h - m, k.line)
        c.drawLine(c2, actionTop, c2, h - m, k.line)
        ruled(c, m + 6f, inner - gap * 0.2f, w - m - 6f, h - m - 4f, k, boxes = true)
    }

    private fun cornell(c: Canvas, w: Float, h: Float, k: Tools) {
        val m = margin(w)
        val top = header(c, w, "Topik:", k)
        val sumTop = top + (h - m - top) * 0.78f
        val cue = m + (w - 2 * m) * 0.3f
        c.drawText("Kata kunci / pertanyaan", m, top + k.small.textSize + 6f, k.small)
        c.drawLine(cue, top, cue, sumTop, k.strong)
        ruled(c, cue + 6f, top, w - m, sumTop - 4f, k)
        c.drawLine(m, sumTop, w - m, sumTop, k.strong)
        c.drawText("Ringkasan", m, sumTop + k.small.textSize + 6f, k.label)
        ruled(c, m, sumTop + k.small.textSize + 8f, w - m, h - m, k)
    }
}
