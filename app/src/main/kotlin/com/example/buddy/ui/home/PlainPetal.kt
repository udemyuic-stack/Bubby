package com.example.buddy.ui.home

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.RectF
import com.example.buddy.ui.GlassDrawable
import com.example.buddy.ui.GlassKind
import com.example.buddy.ui.PETAL_RADII
import com.example.buddy.ui.ThemeDef
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

class PlainPetal(val spec: PetalSpec, theme: ThemeDef) {
    var label: String = spec.label
        set(value) { field = value; lines = value.split('\n') }

    private var lines = spec.label.split('\n')
    private var unit = 1f
    private var centerX = 0f
    private var centerY = 0f
    private var lineHeight = 0f
    private var baselineShift = 0f
    private var pulseStartMs = Long.MIN_VALUE
    private var body = GlassDrawable(spec.kind, radii = PETAL_RADII)
    private val metrics = Paint.FontMetrics()
    private val active = spec.kind == GlassKind.Active
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(if (active) ACTIVE_FONT else LABEL_FONT, Typeface.NORMAL)
        color = when (spec.kind) {
            GlassKind.Mid -> TEXT_LIGHT
            GlassKind.Active -> theme.ink
            else -> theme.ink2
        }
    }
    private val iconPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = ICON_STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        color = textPaint.color
    }

    fun layout(scale: Float) {
        unit = scale
        val widthPx = spec.width * scale
        val heightPx = spec.height * scale
        centerX = (spec.left + spec.width / 2f) * scale
        centerY = (spec.top + spec.height / 2f) * scale
        val halfW = widthPx / 2f
        val halfH = heightPx / 2f
        body = GlassDrawable(spec.kind, radii = PETAL_RADII, density = scale)
        body.setBounds(-halfW.toInt(), -halfH.toInt(), halfW.toInt(), halfH.toInt())
        val size = if (active) ACTIVE_SIZE else LABEL_SIZE
        textPaint.textSize = size * scale
        lineHeight = size * LINE_SPACING
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
        drawContent(c)
        c.restore()
    }

    private fun drawContent(c: Canvas) {
        val total = (ICON_BOX + ICON_GAP + lineHeight * lines.size) * unit
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
            c.drawText(lines[i], 0f, textTop + (i + 0.5f) * lineHeight * unit + baselineShift, textPaint)
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
        const val LINE_SPACING = 1.2f
        const val ICON_BOX = 26f
        const val ICON_GAP = 6f
        const val ICON_STROKE = 1.1f
        const val CONTENT_SHIFT = 5f
        const val TEXT_LIGHT = 0xFFFFFFFF.toInt()
        const val IDLE_RISE = 5f
        const val IDLE_PERIOD = 6f
        const val PRESS_MS = 260L
        const val PRESS_DEPTH = 0.06f
    }
}
