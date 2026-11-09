package com.example.buddy.ui.home

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import com.example.buddy.ui.Glass
import com.example.buddy.ui.glassFont
import kotlin.math.PI
import kotlin.math.cos

class OceanCaption {
    var statusText = DEFAULT_STATUS
    var running = false

    private val pill = RectF()
    private val metrics = Paint.FontMetrics()
    private val namePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = NAME_COLOR
        textSize = NAME_SIZE
        textAlign = Paint.Align.CENTER
        typeface = glassFont(thin = true)
        letterSpacing = NAME_SPACING
        setShadowLayer(NAME_SHADOW_RADIUS, 0f, 1f, NAME_SHADOW)
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = PILL_TEXT
        textSize = PILL_TEXT_SIZE
        typeface = glassFont(thin = true)
        setShadowLayer(TEXT_SHADOW_RADIUS, 0f, 1f, NAME_SHADOW)
    }
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = PILL_FILL }
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1f
        color = PILL_STROKE
    }
    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    fun draw(c: Canvas, seconds: Float, island: Float) {
        val shift = ISLAND_SHIFT * island
        namePaint.getFontMetrics(metrics)
        c.drawText(NAME, CENTER_X, OceanArt.NAME_TOP + shift - metrics.ascent + NAME_PAD, namePaint)
        drawPill(c, seconds, shift)
    }

    private fun drawPill(c: Canvas, seconds: Float, shift: Float) {
        val textWidth = textPaint.measureText(statusText)
        val width = PAD_X + DOT_SIZE + GAP + textWidth + PAD_X
        val left = CENTER_X - width / 2f
        val top = OceanArt.PILL_TOP + shift
        pill.set(left, top, left + width, top + PILL_HEIGHT)
        c.drawRoundRect(pill, PILL_HEIGHT / 2f, PILL_HEIGHT / 2f, fillPaint)
        c.drawRoundRect(pill, PILL_HEIGHT / 2f, PILL_HEIGHT / 2f, strokePaint)
        drawDot(c, seconds, left + PAD_X + DOT_SIZE / 2f, top + PILL_HEIGHT / 2f)
        textPaint.getFontMetrics(metrics)
        val baseline = top + PILL_HEIGHT / 2f - (metrics.ascent + metrics.descent) / 2f
        c.drawText(statusText, left + PAD_X + DOT_SIZE + GAP, baseline, textPaint)
    }

    private fun drawDot(c: Canvas, seconds: Float, x: Float, y: Float) {
        if (running) {
            val pulse = 1f + PULSE_GAIN * 0.5f * (1f - cos(2.0 * PI * seconds / PULSE_PERIOD).toFloat())
            dotPaint.color = Glass.ACCENT
            dotPaint.setShadowLayer(DOT_GLOW, 0f, 0f, Glass.ACCENT)
            c.drawCircle(x, y, DOT_SIZE / 2f * pulse, dotPaint)
        } else {
            dotPaint.clearShadowLayer()
            dotPaint.color = DOT_IDLE
            c.drawCircle(x, y, DOT_SIZE / 2f, dotPaint)
        }
    }

    private companion object {
        const val NAME = "Bubby"
        const val DEFAULT_STATUS = "Помощник выключен"
        const val CENTER_X = 195f
        const val NAME_SIZE = 26f
        const val NAME_SPACING = 0.04f
        const val NAME_PAD = 1f
        const val NAME_SHADOW_RADIUS = 10f
        const val TEXT_SHADOW_RADIUS = 6f
        const val PILL_TEXT_SIZE = 12f
        const val PILL_HEIGHT = 28f
        const val PAD_X = 14f
        const val GAP = 8f
        const val DOT_SIZE = 7f
        const val DOT_GLOW = 10f
        const val PULSE_GAIN = 0.7f
        const val PULSE_PERIOD = 1.6f
        const val ISLAND_SHIFT = 24f
        val NAME_COLOR = Color.rgb(244, 241, 238)
        val NAME_SHADOW = Color.argb(166, 8, 62, 92)
        val PILL_TEXT = Color.rgb(241, 236, 234)
        val PILL_FILL = Color.argb(36, 255, 255, 255)
        val PILL_STROKE = Color.argb(77, 255, 255, 255)
        val DOT_IDLE = Color.argb(153, 255, 255, 255)
    }
}
