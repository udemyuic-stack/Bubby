package com.example.buddy.ui.home

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import com.example.buddy.ui.GlassDrawable
import com.example.buddy.ui.GlassKind
import com.example.buddy.ui.PETAL_RADII
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

class ForestPetal(val spec: PetalSpec, private val index: Int) {
    var label: String = spec.label
        set(value) { field = value; lines = value.split('\n') }

    private var lines = spec.label.split('\n')
    private var unit = 1f
    private var widthPx = 0f
    private var heightPx = 0f
    private var centerX = 0f
    private var centerY = 0f
    private var pulseStartMs = Long.MIN_VALUE
    private var baselineShift = 0f
    private var body = GlassDrawable(spec.kind, radii = PETAL_RADII, shadow = false)
    private val metrics = Paint.FontMetrics()
    private val dropBox = RectF()
    private val active = spec.kind == GlassKind.Active
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(if (active) ACTIVE_FONT else LABEL_FONT, Typeface.NORMAL)
        color = when (spec.kind) {
            GlassKind.Mid -> TEXT_LIGHT
            GlassKind.Active -> TEXT_ACTIVE
            else -> TEXT_DIM
        }
    }
    private val iconPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = ICON_STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        color = textPaint.color
    }
    private val dropPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    fun layout(scale: Float) {
        unit = scale
        widthPx = spec.width * scale
        heightPx = spec.height * scale
        centerX = (spec.left + spec.width / 2f) * scale
        centerY = (spec.top + spec.height / 2f) * scale
        val halfW = widthPx / 2f
        val halfH = heightPx / 2f
        body = GlassDrawable(spec.kind, radii = PETAL_RADII, shadow = false, density = scale)
        body.setBounds(-halfW.toInt(), -halfH.toInt(), halfW.toInt(), halfH.toInt())
        textPaint.textSize = (if (active) ACTIVE_SIZE else LABEL_SIZE) * scale
        textPaint.getFontMetrics(metrics)
        baselineShift = -(metrics.ascent + metrics.descent) / 2f
    }

    fun hitArea(): RectF {
        val dx = spec.width * HalloweenLayout.HIT_INSET
        val dy = spec.height * HalloweenLayout.HIT_INSET
        return RectF(spec.left + dx, spec.top + dy, spec.left + spec.width - dx, spec.top + spec.height - dy)
    }

    fun pulse(nowMs: Long) { pulseStartMs = nowMs }

    fun draw(c: Canvas, seconds: Float, nowMs: Long) {
        val idle = -IDLE_RISE * unit * (1f - cos(TWO_PI * seconds / IDLE_PERIOD)) / 2f
        val press = pressScale(nowMs)
        c.save()
        c.translate(centerX, centerY + idle)
        c.rotate(spec.rotation)
        c.scale(press, press)
        body.draw(c)
        drawDrop(c, seconds)
        drawContent(c)
        c.restore()
    }

    private fun drawDrop(c: Canvas, seconds: Float) {
        var phase = ((seconds + DROP_DELAYS[index]) / DROP_PERIOD) % 1f
        if (phase < 0f) phase += 1f
        val fade = Ease.keys(phase, DROP_FADE_AT, DROP_FADE)
        if (fade <= 0f) return
        val slide = Ease.keys(phase, DROP_SLIDE_AT, DROP_SLIDE) * unit
        val x = -widthPx / 2f + widthPx * DROP_LEFT[index]
        val y = -heightPx / 2f + heightPx * DROP_TOP + slide
        val halfW = DROP_WIDTH * unit / 2f
        val halfH = DROP_HEIGHT * unit / 2f
        dropPaint.color = DROP_TRAIL
        dropPaint.alpha = (fade * DROP_TRAIL_ALPHA).toInt()
        dropBox.set(x - halfW, y - DROP_TRAIL_GAP * unit - halfH, x + halfW, y - DROP_TRAIL_GAP * unit + halfH)
        c.drawOval(dropBox, dropPaint)
        dropPaint.color = DROP_BODY
        dropPaint.alpha = (fade * DROP_BODY_ALPHA).toInt()
        dropBox.set(x - halfW, y - halfH, x + halfW, y + halfH)
        c.drawOval(dropBox, dropPaint)
        dropPaint.color = DROP_SHINE
        dropPaint.alpha = (fade * MAX_ALPHA).toInt()
        c.drawCircle(x - halfW * DROP_SHINE_X, y - halfH * DROP_SHINE_Y, DROP_SHINE_RADIUS * unit, dropPaint)
    }

    private fun drawContent(c: Canvas) {
        val total = (ICON_BOX + ICON_GAP + LINE_HEIGHT * lines.size) * unit
        val top = -total / 2f
        c.save()
        c.translate(0f, CONTENT_SHIFT * unit)
        c.rotate(-spec.rotation)
        c.save()
        c.translate(-ICON_BOX * unit / 2f, top)
        c.scale(ICON_BOX * unit / HalloweenArt.ICON_SIZE, ICON_BOX * unit / HalloweenArt.ICON_SIZE)
        c.drawPath(HalloweenArt.icon(spec.icon), iconPaint)
        c.restore()
        val textTop = top + (ICON_BOX + ICON_GAP) * unit
        for (i in lines.indices) {
            c.drawText(lines[i], 0f, textTop + (i + 0.5f) * LINE_HEIGHT * unit + baselineShift, textPaint)
        }
        c.restore()
    }

    private fun pressScale(nowMs: Long): Float {
        val age = nowMs - pulseStartMs
        if (age < 0 || age >= PRESS_MS) return 1f
        return 1f - PRESS_DEPTH * sin(PI.toFloat() * age / PRESS_MS)
    }

    private companion object {
        const val TWO_PI = (2.0 * PI).toFloat()
        const val LABEL_FONT = "sans-serif-light"
        const val ACTIVE_FONT = "sans-serif"
        const val LABEL_SIZE = 12f
        const val ACTIVE_SIZE = 13f
        const val LINE_HEIGHT = 14.4f
        const val ICON_BOX = 26f
        const val ICON_GAP = 6f
        const val ICON_STROKE = 1.1f
        const val CONTENT_SHIFT = 5f
        const val TEXT_DIM = 0xFF3F5578.toInt()
        const val TEXT_LIGHT = 0xFFFFFFFF.toInt()
        const val TEXT_ACTIVE = 0xFF2A3A58.toInt()
        const val IDLE_RISE = 5f
        const val IDLE_PERIOD = 6f
        const val PRESS_MS = 260L
        const val PRESS_DEPTH = 0.06f
        const val DROP_PERIOD = 7f
        const val DROP_TOP = 0.1f
        const val DROP_WIDTH = 5f
        const val DROP_HEIGHT = 7f
        const val DROP_TRAIL_GAP = 14f
        const val DROP_TRAIL = 0xFFFFFFFF.toInt()
        const val DROP_TRAIL_ALPHA = 46
        const val DROP_BODY = 0xFF96BEF0.toInt()
        const val DROP_BODY_ALPHA = 190
        const val DROP_SHINE = 0xFFFFFFFF.toInt()
        const val DROP_SHINE_X = 0.3f
        const val DROP_SHINE_Y = 0.35f
        const val DROP_SHINE_RADIUS = 1f
        const val MAX_ALPHA = 255f
        val DROP_LEFT = floatArrayOf(0.30f, 0.66f, 0.40f, 0.58f, 0.26f, 0.70f)
        val DROP_DELAYS = floatArrayOf(1f, 3.5f, 5f, 2f, 6f, 4.2f)
        val DROP_FADE_AT = floatArrayOf(0f, 0.1f, 0.9f, 1f)
        val DROP_FADE = floatArrayOf(0f, 1f, 1f, 0f)
        val DROP_SLIDE_AT = floatArrayOf(0f, 0.55f, 1f)
        val DROP_SLIDE = floatArrayOf(0f, 8f, 70f)
    }
}
