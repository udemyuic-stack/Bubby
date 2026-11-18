package com.example.buddy.ui.home

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import com.example.buddy.ui.Art
import com.example.buddy.ui.SvgArt
import com.example.buddy.ui.TapMotion
import com.example.buddy.ui.TapState
import com.example.buddy.ui.Theme
import com.example.buddy.ui.ThemeDef
import kotlin.math.PI
import kotlin.math.sin

class PlainMascot(private val theme: ThemeDef) {
    var statusText = DEFAULT_STATUS
    var running = false
    var island = 0f

    private var art: Art? = null
    private var bounceStartMs = Long.MIN_VALUE
    private val tapState = TapState()
    private val tapMs = TapMotion.durationMs(theme.mascot)
    private val pill = RectF()
    private val metrics = Paint.FontMetrics()
    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader = RadialGradient(0f, 0f, 1f, intArrayOf(Theme.rgb(theme.glow2, GLOW_ALPHA), Theme.rgb(theme.glow2, 0)), floatArrayOf(0f, 1f), Shader.TileMode.CLAMP)
    }
    private val namePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = NAME_COLOR
        textSize = NAME_SIZE
        textAlign = Paint.Align.CENTER
        letterSpacing = NAME_SPACING / NAME_SIZE
        typeface = Typeface.create(FONT, Typeface.NORMAL)
    }
    private val statusPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = STATUS_COLOR
        textSize = STATUS_SIZE
        typeface = Typeface.create(FONT, Typeface.NORMAL)
    }
    private val pillFill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = PILL_FILL }
    private val pillRim = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = PILL_RIM
        color = PILL_RIM_COLOR
    }
    private val dot = Paint(Paint.ANTI_ALIAS_FLAG)
    private val flies = theme.mascot == "butterfly"

    fun bounce(nowMs: Long) { bounceStartMs = nowMs; if (tapMs > 0L) tapState.tap(nowMs) }

    fun draw(c: Canvas, seconds: Float, nowMs: Long) {
        drawMascot(c, seconds, nowMs)
        val shift = island * ISLAND_SHIFT
        drawName(c, shift)
        drawStatus(c, seconds, shift)
    }

    private fun drawMascot(c: Canvas, seconds: Float, nowMs: Long) {
        val drawing = art ?: SvgArt.get(theme.mascot)?.also { art = it } ?: return
        val pivotX = HalloweenLayout.MASCOT_WIDTH / 2f
        val pivotY = HalloweenLayout.MASCOT_HEIGHT / 2f
        val bounce = bounceLevel(nowMs) * (if (tapMs > 0L) REACT_HOP else 1f)
        drawing.tapP = if (tapMs > 0L) tapState.phase(nowMs, tapMs) else -1f
        drawing.tapLevel = tapState.level
        drawing.tapEgg = tapState.egg
        val grow = 1f + (BOUNCE_SCALE - 1f) * bounce
        val shrink = Ease.lerp(1f, ISLAND_SCALE, island)
        c.save()
        c.translate(HalloweenLayout.MASCOT_LEFT, HalloweenLayout.MASCOT_TOP)
        c.scale(shrink, shrink, pivotX, 0f)
        c.translate(0f, -BOUNCE_LIFT * bounce)
        c.rotate(BOUNCE_TILT * bounce, pivotX, pivotY)
        val flight = if (flies && android.animation.ValueAnimator.areAnimatorsEnabled()) 1f - island else 0f
        if (flight > 0f) {
            val dx = flightX(seconds) * flight
            val dy = flightY(seconds) * flight
            val tilt = (flightX(seconds + HEADING_DT) - flightX(seconds)) / HEADING_DT * HEADING_TILT * flight
            c.translate(dx, dy)
            c.rotate(tilt.coerceIn(-MAX_TILT, MAX_TILT), pivotX, pivotY)
        }
        c.scale(grow, grow, pivotX, pivotY)
        c.save()
        c.translate(GLOW_X, GLOW_Y)
        c.scale(GLOW_RX, GLOW_RY)
        c.drawCircle(0f, 0f, 1f, glowPaint)
        c.restore()
        if (flight > 0f) {
            val beat = 0.5f + 0.5f * sin(seconds * FLAP_SPEED)
            c.scale(1f - (1f - FLAP_MIN) * beat * flight, 1f, pivotX, pivotY)
        }
        c.scale(HalloweenLayout.MASCOT_WIDTH / HalloweenLayout.ART_WIDTH, HalloweenLayout.MASCOT_WIDTH / HalloweenLayout.ART_WIDTH)
        drawing.draw(c, seconds)
        c.restore()
    }

    private fun drawName(c: Canvas, shift: Float) {
        namePaint.getFontMetrics(metrics)
        c.drawText(NAME, HalloweenLayout.CENTER_X, NAME_CENTER + shift - (metrics.ascent + metrics.descent) / 2f, namePaint)
    }

    private fun drawStatus(c: Canvas, seconds: Float, shift: Float) {
        val textWidth = statusPaint.measureText(statusText)
        val width = 2f * (PILL_RIM + PILL_PAD_X) + DOT_SIZE + DOT_GAP + textWidth
        val left = HalloweenLayout.CENTER_X - width / 2f
        val top = PILL_TOP + shift
        pill.set(left, top, left + width, top + PILL_HEIGHT)
        c.drawRoundRect(pill, PILL_HEIGHT / 2f, PILL_HEIGHT / 2f, pillFill)
        c.drawRoundRect(pill, PILL_HEIGHT / 2f, PILL_HEIGHT / 2f, pillRim)
        val dotX = left + PILL_RIM + PILL_PAD_X + DOT_SIZE / 2f
        val dotY = top + PILL_HEIGHT / 2f
        val grow = if (running) 1f + DOT_PULSE * sin(PI.toFloat() * ((seconds % DOT_PERIOD) / DOT_PERIOD)) else 1f
        dot.color = if (running) theme.accent else theme.accent2
        if (running) {
            dot.alpha = DOT_HALO_ALPHA
            c.drawCircle(dotX, dotY, DOT_SIZE * grow * DOT_HALO, dot)
            dot.alpha = MAX_ALPHA
        }
        c.drawCircle(dotX, dotY, DOT_SIZE / 2f * grow, dot)
        statusPaint.getFontMetrics(metrics)
        c.drawText(statusText, dotX + DOT_SIZE / 2f + DOT_GAP, dotY - (metrics.ascent + metrics.descent) / 2f, statusPaint)
    }

    private fun loopLevel(seconds: Float): Float {
        val u = (seconds % LOOP_PERIOD) / LOOP_LENGTH
        return if (u >= 1f) 0f else sin(PI.toFloat() * u)
    }

    private fun flightX(seconds: Float): Float {
        val hover = sin(seconds * 0.55f) * HOVER_X + sin(seconds * 1.37f) * HOVER_X * 0.3f
        val loop = loopLevel(seconds)
        return hover * (1f - loop) + sin(seconds * 1.8f) * LOOP_X * loop
    }

    private fun flightY(seconds: Float): Float {
        val hover = sin(seconds * 0.83f + 1.1f) * HOVER_Y + sin(seconds * 2.1f) * HOVER_Y * 0.25f
        val loop = loopLevel(seconds)
        return hover * (1f - loop) + sin(seconds * 3.6f) * LOOP_Y * loop
    }

    private fun bounceLevel(nowMs: Long): Float {
        val age = nowMs - bounceStartMs
        return if (age < 0 || age >= BOUNCE_MS) 0f else Ease.hump(age / BOUNCE_MS.toFloat())
    }

    private companion object {
        const val FONT = "sans-serif-light"
        const val NAME = "Bubby"
        const val DEFAULT_STATUS = "Помощник выключен"
        const val NAME_SIZE = 26f
        const val NAME_SPACING = 1f
        const val NAME_CENTER = 249.4f
        const val NAME_COLOR = 0xFFF4F1EE.toInt()
        const val STATUS_SIZE = 12f
        const val STATUS_COLOR = 0xFFF1ECEA.toInt()
        const val PILL_TOP = 272f
        const val PILL_HEIGHT = 29f
        const val PILL_RIM = 1f
        const val PILL_PAD_X = 14f
        const val PILL_FILL = 0x24FFFFFF
        const val PILL_RIM_COLOR = 0x4DFFFFFF
        const val DOT_SIZE = 7f
        const val DOT_GAP = 8f
        const val DOT_PULSE = 0.7f
        const val DOT_PERIOD = 1.6f
        const val DOT_HALO = 1.2f
        const val DOT_HALO_ALPHA = 90
        const val ISLAND_SCALE = 0.68f
        const val ISLAND_SHIFT = 24f
        const val BOUNCE_MS = 700L
        const val REACT_HOP = 0.3f
        const val BOUNCE_LIFT = 14f
        const val BOUNCE_TILT = -7f
        const val BOUNCE_SCALE = 1.1f
        const val GLOW_ALPHA = 102
        const val GLOW_X = 57.8f
        const val GLOW_Y = 46.2f
        const val GLOW_RX = 75.5f
        const val GLOW_RY = 59.8f
        const val MAX_ALPHA = 255
        const val FLAP_SPEED = 8.8f
        const val FLAP_MIN = 0.72f
        const val HOVER_X = 26f
        const val HOVER_Y = 12f
        const val LOOP_X = 80f
        const val LOOP_Y = 22f
        const val LOOP_PERIOD = 14f
        const val LOOP_LENGTH = 3.5f
        const val HEADING_DT = 0.1f
        const val HEADING_TILT = 0.35f
        const val MAX_TILT = 18f
    }
}
