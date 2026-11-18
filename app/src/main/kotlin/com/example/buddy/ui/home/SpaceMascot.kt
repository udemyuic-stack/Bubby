package com.example.buddy.ui.home

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import com.example.buddy.ui.Art
import com.example.buddy.ui.SvgArt
import com.example.buddy.ui.Theme
import kotlin.math.PI
import kotlin.math.sin

class SpaceMascot {
    var statusText = DEFAULT_STATUS
    var running = false
    var island = 0f
    var sad = false

    private var art: Art? = null
    private var bounceStartMs = Long.MIN_VALUE
    private val pill = RectF()
    private val metrics = Paint.FontMetrics()
    private val alarmPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader = RadialGradient(0f, 0f, 1f, intArrayOf(ALARM_CORE, ALARM_EDGE), floatArrayOf(0f, 1f), Shader.TileMode.CLAMP)
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

    fun bounce(nowMs: Long) { bounceStartMs = nowMs }

    fun draw(c: Canvas, seconds: Float, nowMs: Long, alarm: Float) {
        drawMascot(c, seconds, nowMs, alarm)
        val shift = island * ISLAND_SHIFT
        drawName(c, shift)
        drawStatus(c, seconds, shift)
    }

    private fun drawMascot(c: Canvas, seconds: Float, nowMs: Long, alarm: Float) {
        val drawing = art ?: SvgArt.get(ART_NAME).also { art = it } ?: return
        val pivotX = HalloweenLayout.MASCOT_WIDTH / 2f
        val pivotY = HalloweenLayout.MASCOT_HEIGHT * FLOAT_ORIGIN
        val phase = (seconds % FLOAT_PERIOD) / FLOAT_PERIOD
        val bounce = bounceLevel(nowMs)
        val squashX = Ease.keys(phase, FLOAT_AT, FLOAT_SX) * (1f + (BOUNCE_SCALE - 1f) * bounce)
        val squashY = Ease.keys(phase, FLOAT_AT, FLOAT_SY) * (1f + (BOUNCE_SCALE - 1f) * bounce)
        val shrink = Ease.lerp(1f, ISLAND_SCALE, island)
        c.save()
        c.translate(HalloweenLayout.MASCOT_LEFT, HalloweenLayout.MASCOT_TOP)
        c.translate(0f, Ease.keys(phase, FLOAT_AT, FLOAT_DY) - BOUNCE_LIFT * bounce)
        c.rotate(BOUNCE_TILT * bounce, pivotX, pivotY)
        c.scale(squashX, squashY, pivotX, pivotY)
        c.scale(shrink, shrink, pivotX, 0f)
        if (alarm > 0f) drawAlarm(c, alarm)
        c.scale(HalloweenLayout.MASCOT_WIDTH / HalloweenLayout.ART_WIDTH, HalloweenLayout.MASCOT_WIDTH / HalloweenLayout.ART_WIDTH)
        drawing.sad = sad
        try {
            drawing.draw(c, seconds)
        } finally {
            drawing.sad = false
        }
        c.restore()
    }

    private fun drawAlarm(c: Canvas, alarm: Float) {
        alarmPaint.alpha = (alarm * MAX_ALPHA).toInt()
        c.save()
        c.translate(HalloweenLayout.MASCOT_WIDTH / 2f, HalloweenLayout.MASCOT_HEIGHT / 2f)
        c.scale(ALARM_RX, ALARM_RY)
        c.drawCircle(0f, 0f, 1f, alarmPaint)
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
        dot.color = if (running) Theme.SPACE.accent else DOT_IDLE
        if (running) {
            dot.alpha = DOT_HALO_ALPHA
            c.drawCircle(dotX, dotY, DOT_SIZE * grow * DOT_HALO, dot)
            dot.alpha = MAX_ALPHA.toInt()
        }
        c.drawCircle(dotX, dotY, DOT_SIZE / 2f * grow, dot)
        statusPaint.getFontMetrics(metrics)
        c.drawText(statusText, dotX + DOT_SIZE / 2f + DOT_GAP, dotY - (metrics.ascent + metrics.descent) / 2f, statusPaint)
    }

    private fun bounceLevel(nowMs: Long): Float {
        val age = nowMs - bounceStartMs
        return if (age < 0 || age >= BOUNCE_MS) 0f else Ease.hump(age / BOUNCE_MS.toFloat())
    }

    private companion object {
        const val ART_NAME = "sun"
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
        const val DOT_IDLE = 0xFFA3A9C9.toInt()
        const val DOT_PULSE = 0.7f
        const val DOT_PERIOD = 1.6f
        const val DOT_HALO = 1.2f
        const val DOT_HALO_ALPHA = 90
        const val ISLAND_SCALE = 0.68f
        const val ISLAND_SHIFT = 24f
        const val FLOAT_PERIOD = 4.8f
        const val FLOAT_ORIGIN = 0.92f
        const val BOUNCE_MS = 700L
        const val BOUNCE_LIFT = 14f
        const val BOUNCE_TILT = -7f
        const val BOUNCE_SCALE = 1.1f
        const val ALARM_CORE = 0x80FF3C3C.toInt()
        const val ALARM_EDGE = 0x00FF3C3C
        const val ALARM_RX = 95f
        const val ALARM_RY = 80f
        const val MAX_ALPHA = 255f
        val FLOAT_AT = floatArrayOf(0f, 0.22f, 0.45f, 0.70f, 1f)
        val FLOAT_DY = floatArrayOf(0f, -8f, -11f, -4f, 0f)
        val FLOAT_SX = floatArrayOf(1f, 1.04f, 0.97f, 1.02f, 1f)
        val FLOAT_SY = floatArrayOf(1f, 0.96f, 1.03f, 0.98f, 1f)
    }
}
