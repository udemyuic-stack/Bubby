package com.example.buddy.ui.home

import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import kotlin.math.PI
import kotlin.math.min

class AuroraShow(private val mascot: AuroraMascot) : Show() {
    override val durationMs = DURATION_MS

    private val colors = intArrayOf(0xFF1EA97B.toInt(), 0xFF72C613.toInt(), 0xFF2180E6.toInt())
    private val paints = Array(colors.size) { Paint(Paint.ANTI_ALIAS_FLAG) }
    private var bandWidth = 0f

    override fun onCancel() {
        mascot.split = 0f
        mascot.spin = 0f
    }

    override fun onDraw(c: Canvas, scale: Float, ms: Long) {
        val p = ms / DURATION_MS.toFloat()
        mascot.split = Ease.smooth(min(p, 1f - p) / SPLIT_SPAN)
        mascot.spin = p * SPINS * (2.0 * PI).toFloat()
        drawRibbons(c, p)
    }

    private fun drawRibbons(c: Canvas, p: Float) {
        val w = c.width.toFloat()
        val h = c.height.toFloat()
        if (bandWidth != w * BAND_FRACTION) buildShaders(w)
        val fade = Ease.hump(p)
        for (i in paints.indices) {
            val lag = i * RIBBON_LAG
            val q = ((p - lag) / (1f - RIBBON_LAG * paints.size)).coerceIn(0f, 1f)
            paints[i].alpha = (fade * MAX_ALPHA).toInt()
            c.save()
            c.rotate(RIBBON_TILT, w / 2f, h / 2f)
            c.translate(Ease.lerp(-bandWidth, w, q), -h * RIBBON_OVERSHOOT)
            c.drawRect(0f, 0f, bandWidth, h * (1f + 2f * RIBBON_OVERSHOOT), paints[i])
            c.restore()
        }
    }

    private fun buildShaders(w: Float) {
        bandWidth = w * BAND_FRACTION
        for (i in paints.indices) {
            paints[i].shader = LinearGradient(
                0f, 0f, bandWidth, 0f,
                intArrayOf(colors[i] and 0x00FFFFFF, colors[i], colors[i] and 0x00FFFFFF), null, Shader.TileMode.CLAMP
            )
        }
    }

    private companion object {
        const val DURATION_MS = 4200L
        const val SPLIT_SPAN = 0.2f
        const val SPINS = 1.5f
        const val BAND_FRACTION = 0.45f
        const val RIBBON_TILT = -18f
        const val RIBBON_OVERSHOOT = 0.3f
        const val RIBBON_LAG = 0.12f
        const val MAX_ALPHA = 140f
    }
}
