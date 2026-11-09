package com.example.buddy.ui.home

import android.graphics.Canvas
import android.graphics.Paint
import kotlin.math.min
import kotlin.math.sin

class CandyShow(private val mascot: CandyMascot) : Show() {
    override val durationMs = DURATION_MS

    private val colors = intArrayOf(0xFFFF5C9A.toInt(), 0xFF5CD6B5.toInt(), 0xFFFFD84D.toInt(), 0xFF9B6BFF.toInt())
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    override fun onCancel() {
        mascot.jelly = 0f
    }

    override fun onDraw(c: Canvas, scale: Float, ms: Long) {
        val p = ms / DURATION_MS.toFloat()
        mascot.jelly = Ease.smooth(min(p, 1f - p) / JELLY_SPAN)
        val w = c.width.toFloat()
        val h = c.height.toFloat()
        for (i in 0 until DROPS) {
            val start = (i * GOLDEN) % 1f * START_SPREAD
            val q = ((p - start) / FALL_SPAN).coerceIn(0f, 1f)
            if (q <= 0f || q >= 1f) continue
            val x = ((i * GOLDEN * 7f) % 1f) * w + sin(q * SWAY_TURNS) * SWAY * scale
            val y = Ease.lerp(-DROP_R * scale, h + DROP_R * scale, q * q)
            paint.color = colors[i % colors.size]
            c.drawCircle(x, y, DROP_R * scale * (SIZE_MIN + (i % SIZES) / SIZES.toFloat()), paint)
        }
    }

    private companion object {
        const val DURATION_MS = 4200L
        const val JELLY_SPAN = 0.15f
        const val DROPS = 28
        const val GOLDEN = 0.618034f
        const val START_SPREAD = 0.55f
        const val FALL_SPAN = 0.4f
        const val SWAY = 14f
        const val SWAY_TURNS = 9f
        const val DROP_R = 9f
        const val SIZE_MIN = 0.6f
        const val SIZES = 4
    }
}
