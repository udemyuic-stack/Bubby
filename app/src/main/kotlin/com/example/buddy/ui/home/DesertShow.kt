package com.example.buddy.ui.home

import android.graphics.Canvas
import android.graphics.Paint
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

class DesertShow(private val mascot: DesertMascot) : Show() {
    override val durationMs = DURATION_MS

    private val sandPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        strokeCap = Paint.Cap.ROUND
        strokeWidth = STREAK_WIDTH
        color = 0xFFE8B86B.toInt()
    }
    private val weedPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = WEED_STROKE
        color = 0xFF8A5A2B.toInt()
    }

    override fun onCancel() {
        mascot.bloom = 0f
    }

    override fun onDraw(c: Canvas, scale: Float, ms: Long) {
        val p = ms / DURATION_MS.toFloat()
        mascot.bloom = Ease.smooth((p - BLOOM_FROM) / BLOOM_SPAN)
        drawStorm(c, p, scale)
        drawWeed(c, p, scale)
    }

    private fun drawStorm(c: Canvas, p: Float, scale: Float) {
        val level = Ease.smooth(min(p, STORM_END - p) / STORM_RAMP)
        if (level <= 0f) return
        val w = c.width.toFloat()
        val h = c.height.toFloat()
        sandPaint.alpha = (level * STREAK_ALPHA).toInt()
        for (i in 0 until STREAKS) {
            val lane = (i * GOLDEN) % 1f
            val x = (((i * GOLDEN * 5f) % 1f) + p * STREAK_SPEED * (1f + lane)) % 1f
            val y = lane * h
            val len = STREAK_LENGTH * scale * (1f + lane)
            c.drawLine(x * (w + len) - len, y, x * (w + len), y + len * STREAK_SLOPE, sandPaint)
        }
    }

    private fun drawWeed(c: Canvas, p: Float, scale: Float) {
        val q = ((p - WEED_FROM) / WEED_SPAN).coerceIn(0f, 1f)
        if (q <= 0f || q >= 1f) return
        val w = c.width.toFloat()
        val h = c.height.toFloat()
        val r = WEED_R * scale
        val x = Ease.lerp(-r * 2f, w + r * 2f, q)
        val y = h * WEED_Y - kotlin.math.abs(sin(q * WEED_HOPS * PI.toFloat())) * HOP * scale
        c.save()
        c.translate(x, y)
        c.rotate(q * WEED_TURNS * 360f)
        for (i in 0 until WEED_RINGS) {
            val a = PI.toFloat() * i / WEED_RINGS
            c.save()
            c.rotate(Math.toDegrees(a.toDouble()).toFloat())
            c.drawOval(-r, -r * (WEED_FLAT + cos(a) * 0.15f), r, r * (WEED_FLAT + cos(a) * 0.15f), weedPaint)
            c.restore()
        }
        c.restore()
    }

    private companion object {
        const val DURATION_MS = 4600L
        const val STORM_END = 0.7f
        const val STORM_RAMP = 0.12f
        const val STREAKS = 42
        const val GOLDEN = 0.618034f
        const val STREAK_SPEED = 2.2f
        const val STREAK_LENGTH = 34f
        const val STREAK_SLOPE = 0.08f
        const val STREAK_WIDTH = 3f
        const val STREAK_ALPHA = 170f
        const val WEED_FROM = 0.3f
        const val WEED_SPAN = 0.5f
        const val WEED_R = 22f
        const val WEED_Y = 0.78f
        const val WEED_HOPS = 4f
        const val HOP = 26f
        const val WEED_TURNS = 3f
        const val WEED_RINGS = 3
        const val WEED_FLAT = 0.9f
        const val WEED_STROKE = 2.2f
        const val BLOOM_FROM = 0.7f
        const val BLOOM_SPAN = 0.15f
    }
}
