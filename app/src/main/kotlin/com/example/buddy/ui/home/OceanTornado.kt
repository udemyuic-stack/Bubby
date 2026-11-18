package com.example.buddy.ui.home

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import kotlin.math.sin
import kotlin.math.tan

class OceanTornado {
    private val body = Path().apply {
        moveTo(10f, 20f); cubicTo(40f, 40f, 80f, 60f, 100f, 200f); cubicTo(108f, 300f, 114f, 420f, 118f, 530f)
        lineTo(122f, 530f); cubicTo(126f, 420f, 132f, 300f, 140f, 200f); cubicTo(160f, 60f, 200f, 40f, 230f, 20f)
        close()
    }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader = LinearGradient(
            0f, 0f, OceanArt.TORNADO_WIDTH, 0f,
            intArrayOf(Color.argb(0, 232, 215, 170), Color.argb(140, 244, 234, 208), Color.argb(140, 217, 192, 138), Color.argb(0, 232, 215, 170)),
            floatArrayOf(0f, 0.3f, 0.7f, 1f),
            Shader.TileMode.CLAMP,
        )
    }
    private val soft = Paint(fill).apply {
        style = Paint.Style.STROKE
        strokeWidth = SOFT_WIDTH
        alpha = SOFT_ALPHA
    }
    private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val oval = RectF()
    private val dashes = Array(RING_COUNT) { Array(DASH_STEPS) { step -> DashPathEffect(floatArrayOf(DASH_ON, DASH_OFF), DASH_CYCLE * step / DASH_STEPS) } }

    fun centerX(ms: Long): Float {
        val t = ms / 1000f
        return OceanArt.TORNADO_CENTER_X + OceanArt.TORNADO_SWAY * sin(t * SWAY_SPEED) * minOf(1f, t / SWAY_RAMP)
    }

    fun strength(ms: Long): Float = when {
        ms < OceanTiming.TORNADO_OFF -> minOf(1f, ms / GROW_MS)
        else -> maxOf(0f, 1f - (ms - OceanTiming.TORNADO_OFF) / FADE_MS)
    }

    fun draw(c: Canvas, ms: Long, strength: Float) {
        if (strength <= 0f) return
        val x = centerX(ms)
        val wobble = OceanMotion.pingPong(ms / 1000f, WOBBLE_PERIOD)
        val skew = tan(Math.toRadians((OceanMotion.easeInOut.getInterpolation(wobble) * 2f - 1f) * WOBBLE_DEGREES.toDouble())).toFloat()
        c.save()
        c.translate(x, OceanArt.TORNADO_BOTTOM)
        c.scale(WIDTH_BASE + WIDTH_GAIN * strength, strength)
        c.translate(-OceanArt.TORNADO_WIDTH / 2f, -OceanArt.TORNADO_HEIGHT)
        c.translate(OceanArt.TORNADO_WIDTH / 2f, OceanArt.TORNADO_HEIGHT / 2f)
        c.skew(skew, 0f)
        c.translate(-OceanArt.TORNADO_WIDTH / 2f, -OceanArt.TORNADO_HEIGHT / 2f)
        val layer = if (strength < 1f) c.saveLayerAlpha(0f, 0f, OceanArt.TORNADO_WIDTH, OceanArt.TORNADO_HEIGHT, (strength * OPAQUE).toInt()) else NO_LAYER
        c.drawPath(body, fill)
        c.drawPath(body, soft)
        drawRings(c, ms / 1000f)
        if (layer != NO_LAYER) c.restoreToCount(layer)
        c.restore()
    }

    private fun drawRings(c: Canvas, seconds: Float) {
        for (i in 0 until RING_COUNT) {
            val phase = OceanMotion.loop(seconds, RING_BASE_PERIOD + RING_PERIOD_STEP * i)
            val travelled = if (i % 2 == 0) phase else 1f - phase
            ring.pathEffect = dashes[i][(travelled * DASH_STEPS).toInt().coerceAtMost(DASH_STEPS - 1)]
            ring.color = if (i % 2 == 0) RING_TAN else RING_WHITE
            ring.strokeWidth = OceanArt.ringWidth[i]
            val cy = RING_FIRST_Y + RING_SPACING * i
            oval.set(RING_CENTER_X - OceanArt.ringRadiusX[i], cy - OceanArt.ringRadiusY[i], RING_CENTER_X + OceanArt.ringRadiusX[i], cy + OceanArt.ringRadiusY[i])
            c.drawOval(oval, ring)
        }
    }

    private companion object {
        const val NO_LAYER = -1
        const val RING_COUNT = 13
        const val RING_FIRST_Y = 20f
        const val RING_SPACING = 40f
        const val RING_CENTER_X = 120f
        const val RING_BASE_PERIOD = 0.35f
        const val RING_PERIOD_STEP = 0.05f
        const val DASH_ON = 40f
        const val DASH_OFF = 28f
        const val DASH_CYCLE = 68f
        const val DASH_STEPS = 16
        const val SOFT_WIDTH = 8f
        const val SOFT_ALPHA = 40
        const val OPAQUE = 255f
        const val SWAY_SPEED = 1.5f
        const val SWAY_RAMP = 0.8f
        const val GROW_MS = 700f
        const val FADE_MS = 556f
        const val WOBBLE_PERIOD = 0.5f
        const val WOBBLE_DEGREES = 4f
        const val WIDTH_BASE = 0.4f
        const val WIDTH_GAIN = 0.6f
        val RING_TAN = Color.argb(191, 224, 198, 144)
        val RING_WHITE = Color.argb(191, 255, 255, 255)
    }
}
