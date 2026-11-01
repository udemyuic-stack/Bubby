package com.example.buddy.ui.home

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
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

class NewYearMascot {
    var statusText = DEFAULT_STATUS
    var running = false
    var island = 0f

    private var art: Art? = null
    private var bounceStartMs = Long.MIN_VALUE
    private val pill = RectF()
    private val blob = RectF(BLOB_LEFT, BLOB_TOP, BLOB_LEFT + BLOB_WIDTH, BLOB_TOP + BLOB_HEIGHT)
    private val metrics = Paint.FontMetrics()
    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader = RadialGradient(0f, 0f, 1f, intArrayOf(GLOW_CORE, GLOW_EDGE), floatArrayOf(0f, 1f), Shader.TileMode.CLAMP)
    }
    private val blobPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        val matrix = Matrix()
        matrix.setScale(BLOB_REACH_X, BLOB_REACH_Y)
        matrix.postTranslate(BLOB_LEFT + BLOB_WIDTH * BLOB_FOCUS_X, BLOB_TOP + BLOB_HEIGHT * BLOB_FOCUS_Y)
        shader = RadialGradient(0f, 0f, 1f, intArrayOf(Color.WHITE, BLOB_MID, BLOB_EDGE), floatArrayOf(0f, BLOB_MID_STOP, 1f), Shader.TileMode.CLAMP)
            .also { it.setLocalMatrix(matrix) }
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

    fun draw(c: Canvas, seconds: Float, nowMs: Long, lift: Float) {
        drawMascot(c, seconds, nowMs, lift)
        val shift = island * ISLAND_SHIFT
        drawName(c, shift)
        drawStatus(c, seconds, shift)
    }

    private fun drawMascot(c: Canvas, seconds: Float, nowMs: Long, lift: Float) {
        val drawing = art ?: SvgArt.get(ART_NAME).also { art = it } ?: return
        val pivotX = NewYearLayout.MASCOT_WIDTH / 2f
        val pivotY = NewYearLayout.MASCOT_HEIGHT * FLOAT_ORIGIN
        val phase = (seconds % FLOAT_PERIOD) / FLOAT_PERIOD
        val bounce = bounceLevel(nowMs)
        val squashX = Ease.keys(phase, FLOAT_AT, FLOAT_SX) * (1f + (BOUNCE_SCALE - 1f) * bounce)
        val squashY = Ease.keys(phase, FLOAT_AT, FLOAT_SY) * (1f + (BOUNCE_SCALE - 1f) * bounce)
        val shrink = Ease.lerp(1f, ISLAND_SCALE, island)
        val fit = NewYearLayout.MASCOT_WIDTH / NewYearLayout.ART_WIDTH
        c.save()
        c.translate(NewYearLayout.MASCOT_LEFT, NewYearLayout.MASCOT_TOP)
        c.translate(0f, Ease.keys(phase, FLOAT_AT, FLOAT_DY) - BOUNCE_LIFT * bounce - lift)
        c.rotate(BOUNCE_TILT * bounce, pivotX, pivotY)
        c.scale(squashX, squashY, pivotX, pivotY)
        c.scale(shrink, shrink, pivotX, 0f)
        c.scale(fit, fit)
        drawBackdrop(c)
        drawing.draw(c, seconds)
        c.restore()
    }

    private fun drawBackdrop(c: Canvas) {
        c.save()
        c.translate(NewYearLayout.ART_WIDTH / 2f, NewYearLayout.ART_HEIGHT / 2f)
        c.scale(GLOW_RX, GLOW_RY)
        c.drawCircle(0f, 0f, 1f, glowPaint)
        c.restore()
        c.drawRoundRect(blob, BLOB_RADIUS_X, BLOB_RADIUS_Y, blobPaint)
    }

    private fun drawName(c: Canvas, shift: Float) {
        namePaint.getFontMetrics(metrics)
        c.drawText(NAME, NewYearLayout.CENTER_X, NAME_CENTER + shift - (metrics.ascent + metrics.descent) / 2f, namePaint)
    }

    private fun drawStatus(c: Canvas, seconds: Float, shift: Float) {
        val textWidth = statusPaint.measureText(statusText)
        val width = 2f * (PILL_RIM + PILL_PAD_X) + DOT_SIZE + DOT_GAP + textWidth
        val left = NewYearLayout.CENTER_X - width / 2f
        val top = PILL_TOP + shift
        pill.set(left, top, left + width, top + PILL_HEIGHT)
        c.drawRoundRect(pill, PILL_HEIGHT / 2f, PILL_HEIGHT / 2f, pillFill)
        c.drawRoundRect(pill, PILL_HEIGHT / 2f, PILL_HEIGHT / 2f, pillRim)
        val dotX = left + PILL_RIM + PILL_PAD_X + DOT_SIZE / 2f
        val dotY = top + PILL_HEIGHT / 2f
        val grow = if (running) 1f + DOT_PULSE * sin(PI.toFloat() * ((seconds % DOT_PERIOD) / DOT_PERIOD)) else 1f
        dot.color = if (running) Theme.NEWYEAR.accent else DOT_IDLE
        if (running) {
            dot.alpha = DOT_HALO_ALPHA
            c.drawCircle(dotX, dotY, DOT_SIZE * grow * DOT_HALO, dot)
            dot.alpha = MAX_ALPHA
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
        const val ART_NAME = "snowman"
        const val FONT = "sans-serif-light"
        const val NAME = "Bubby"
        const val DEFAULT_STATUS = "Помощник выключен"
        const val NAME_SIZE = 26f
        const val NAME_SPACING = 1f
        const val NAME_CENTER = 265.4f
        const val NAME_COLOR = 0xFFF4F1EE.toInt()
        const val STATUS_SIZE = 12f
        const val STATUS_COLOR = 0xFFF1ECEA.toInt()
        const val PILL_TOP = 288f
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
        const val MAX_ALPHA = 255
        const val ISLAND_SCALE = 0.68f
        const val ISLAND_SHIFT = 24f
        const val FLOAT_PERIOD = 4.8f
        const val FLOAT_ORIGIN = 0.92f
        const val BOUNCE_MS = 700L
        const val BOUNCE_LIFT = 14f
        const val BOUNCE_TILT = -7f
        const val BOUNCE_SCALE = 1.1f
        const val GLOW_CORE = 0x47FFFFFF
        const val GLOW_EDGE = 0x00FFFFFF
        const val GLOW_RX = 111f
        const val GLOW_RY = 88f
        const val BLOB_LEFT = 7f
        const val BLOB_TOP = 9f
        const val BLOB_WIDTH = 156f
        const val BLOB_HEIGHT = 118f
        const val BLOB_RADIUS_X = 50f
        const val BLOB_RADIUS_Y = 42.5f
        const val BLOB_FOCUS_X = 0.34f
        const val BLOB_FOCUS_Y = 0.22f
        const val BLOB_REACH_X = 145.6f
        const val BLOB_REACH_Y = 130.2f
        const val BLOB_MID = 0xFFF3F7FD.toInt()
        const val BLOB_EDGE = 0xFFD4E1F2.toInt()
        const val BLOB_MID_STOP = 0.55f
        val FLOAT_AT = floatArrayOf(0f, 0.22f, 0.45f, 0.70f, 1f)
        val FLOAT_DY = floatArrayOf(0f, -8f, -11f, -4f, 0f)
        val FLOAT_SX = floatArrayOf(1f, 1.04f, 0.97f, 1.02f, 1f)
        val FLOAT_SY = floatArrayOf(1f, 0.96f, 1.03f, 0.98f, 1f)
    }
}
