package com.example.buddy.ui.home

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import com.example.buddy.Tr

class SpaceRewind {
    private var years = Array(STAGES) { "" }
    private var names = Array(STAGES) { "" }
    private var width = 0f
    private var height = 0f
    private val lines = FloatArray(LINE_COUNT * 4)
    private val box = RectF()
    private val tint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader = RadialGradient(
            0f, 0f, 1f,
            intArrayOf(0x1F7896FF, 0x47281E78, 0x47281E78),
            floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP
        )
    }
    private val scan = Paint().apply {
        strokeWidth = SCAN_WIDTH
        color = 0xFFFFFFFF.toInt()
    }
    private val pillFill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x9E141032.toInt() }
    private val pillRim = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1f
        color = 0x59FFFFFF
    }
    private val yearPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFFFFFFF.toInt()
        textSize = YEAR_SIZE
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    }
    private val namePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xD9FFFFFF.toInt()
        textSize = NAME_SIZE
        typeface = Typeface.create("sans-serif", Typeface.NORMAL)
    }

    fun resize(widthDesign: Float, heightDesign: Float) {
        width = widthDesign
        height = heightDesign
    }

    fun load(ctx: Context) {
        fun t(ru: String, en: String, uz: String) = Tr.t(ctx, ru, en, uz)
        years = arrayOf(
            t("4,6 млрд лет назад", "4.6 billion years ago", "4,6 milliard yil oldin"),
            t("4,5 млрд лет назад", "4.5 billion years ago", "4,5 milliard yil oldin"),
            t("4,0 млрд лет назад", "4.0 billion years ago", "4,0 milliard yil oldin"),
            t("3,8 млрд лет назад", "3.8 billion years ago", "3,8 milliard yil oldin"),
            t("540 млн лет назад", "540 million years ago", "540 million yil oldin"),
            t("Сегодня", "Today", "Bugun")
        )
        names = arrayOf(
            t("Протопланетный диск", "Protoplanetary disk", "Protoplanetar disk"),
            t("Расплавленная Земля", "Molten Earth", "Erigan Yer"),
            t("Твёрдая кора", "Solid crust", "Qattiq qobiq"),
            t("Первые океаны", "First oceans", "Birinchi okeanlar"),
            t("Расцвет жизни", "Life flourishes", "Hayot gullab-yashnashi"),
            t("Наши дни 🌍", "Present day 🌍", "Hozirgi kun 🌍")
        )
    }

    fun drawTint(c: Canvas, level: Float, ms: Long) {
        if (level <= 0f || width <= 0f) return
        c.save()
        c.translate(width * 0.5f, height * 0.45f)
        c.scale(width * 0.8f, height * 0.6f)
        tint.alpha = (level * 255f).toInt()
        c.drawRect(-2f, -2f, 2f, 2f, tint)
        c.restore()
        val offset = (ms / SCROLL_MS * SCAN_SCROLL) % SCAN_STEP
        var n = 0
        var y = offset
        while (y < height && n < LINE_COUNT) {
            lines[n * 4] = 0f
            lines[n * 4 + 1] = y
            lines[n * 4 + 2] = width
            lines[n * 4 + 3] = y
            n++
            y += SCAN_STEP
        }
        scan.alpha = (level * SCAN_ALPHA * 255f).toInt()
        c.drawLines(lines, 0, n * 4, scan)
    }

    fun drawPill(c: Canvas, ex: Float, ey: Float, er: Float, stage: Int, level: Float) {
        if (level <= 0f) return
        val year = years[stage]
        val name = names[stage]
        val w = maxOf(yearPaint.measureText(year), namePaint.measureText(name)) + 2f * PAD_X
        val left = (ex - 60f).coerceIn(10f, 190f)
        val top = ey + er + 20f
        box.set(left, top, left + w, top + PILL_HEIGHT)
        pillFill.alpha = (level * 0x9E).toInt()
        pillRim.alpha = (level * 0x59).toInt()
        c.drawRoundRect(box, RADIUS, RADIUS, pillFill)
        c.drawRoundRect(box, RADIUS, RADIUS, pillRim)
        yearPaint.alpha = (level * 255f).toInt()
        namePaint.alpha = (level * 0xD9).toInt()
        c.drawText(year, left + PAD_X, top + 19f, yearPaint)
        c.drawText(name, left + PAD_X, top + 34f, namePaint)
    }

    private companion object {
        const val STAGES = 6
        const val LINE_COUNT = 200
        const val SCAN_WIDTH = 2f
        const val SCAN_STEP = 5f
        const val SCAN_SCROLL = 10f
        const val SCROLL_MS = 500f
        const val SCAN_ALPHA = 0.07f
        const val YEAR_SIZE = 15f
        const val NAME_SIZE = 11f
        const val PAD_X = 14f
        const val PILL_HEIGHT = 44f
        const val RADIUS = 18f
    }
}
