package com.example.buddy.ui.home

import android.graphics.Canvas
import android.graphics.Paint
import com.example.buddy.ui.Theme
import com.example.buddy.ui.ThemeDef
import kotlin.math.cos
import kotlin.math.sin

class PlainEffects(theme: ThemeDef) {
    private var burstX = 0f
    private var burstY = 0f
    private var burstStartMs = Long.MIN_VALUE
    private var rippleX = 0f
    private var rippleY = 0f
    private var rippleStartMs = Long.MIN_VALUE
    private val cosines = FloatArray(DOTS) { cos(it.toFloat() / DOTS * TWO_PI) }
    private val sines = FloatArray(DOTS) { sin(it.toFloat() / DOTS * TWO_PI) }
    private val dot = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = theme.accent }
    private val dotHalo = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Theme.rgb(theme.accent, HALO_ALPHA) }
    private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = 0xFFFFFFFF.toInt()
    }
    private val ringHalo = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = Theme.rgb(theme.glow, 255)
    }

    fun burst(x: Float, y: Float, nowMs: Long) {
        burstX = x
        burstY = y
        burstStartMs = nowMs
    }

    fun ripple(x: Float, y: Float, nowMs: Long) {
        rippleX = x
        rippleY = y
        rippleStartMs = nowMs
    }

    fun draw(c: Canvas, nowMs: Long) {
        drawBurst(c, nowMs)
        drawRipple(c, nowMs)
    }

    private fun drawBurst(c: Canvas, nowMs: Long) {
        val age = nowMs - burstStartMs
        if (age < 0 || age >= BURST_MS) return
        val e = settle(age / BURST_MS.toFloat())
        val fade = 1f - e
        val radius = DOT_RADIUS * (1f - 0.7f * e)
        dot.alpha = (fade * MAX_ALPHA).toInt()
        dotHalo.alpha = (fade * HALO_ALPHA).toInt()
        for (i in 0 until DOTS) {
            val x = burstX + cosines[i] * REACH * e
            val y = burstY + sines[i] * REACH * e
            c.drawCircle(x, y, radius * HALO_SCALE, dotHalo)
            c.drawCircle(x, y, radius, dot)
        }
    }

    private fun drawRipple(c: Canvas, nowMs: Long) {
        val age = nowMs - rippleStartMs
        if (age < 0 || age >= RIPPLE_MS) return
        val e = settle(age / RIPPLE_MS.toFloat())
        val grow = RIPPLE_MAX * e
        val fade = RIPPLE_ALPHA * (1f - e)
        ring.strokeWidth = RIPPLE_STROKE * grow
        ring.alpha = (fade * RIPPLE_RIM * MAX_ALPHA).toInt()
        ringHalo.strokeWidth = RIPPLE_STROKE * grow * HALO_STROKE
        ringHalo.alpha = (fade * RIPPLE_HALO * MAX_ALPHA).toInt()
        c.drawCircle(rippleX, rippleY, RIPPLE_RADIUS * grow, ringHalo)
        c.drawCircle(rippleX, rippleY, RIPPLE_RADIUS * grow, ring)
    }

    private fun settle(p: Float): Float {
        val q = 1f - p
        return 1f - q * q * q * q
    }

    private companion object {
        const val TWO_PI = 6.2831855f
        const val DOTS = 8
        const val REACH = 48f
        const val DOT_RADIUS = 4f
        const val HALO_SCALE = 2f
        const val HALO_ALPHA = 70
        const val BURST_MS = 750L
        const val RIPPLE_MS = 750L
        const val RIPPLE_RADIUS = 20f
        const val RIPPLE_MAX = 9f
        const val RIPPLE_STROKE = 2f
        const val RIPPLE_ALPHA = 0.7f
        const val RIPPLE_RIM = 0.85f
        const val RIPPLE_HALO = 0.5f
        const val HALO_STROKE = 3f
        const val MAX_ALPHA = 255f
    }
}
