package com.example.buddy.ui.home

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import com.example.buddy.ui.GlassDrawable
import com.example.buddy.ui.GlassKind
import com.example.buddy.ui.PETAL_RADII
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

class SpacePetal(val spec: PetalSpec) {
    val craters = SpaceCraters()
    var label: String = spec.label
        set(value) { field = value; lines = value.split('\n') }
    var widthPx = 0f
        private set
    var heightPx = 0f
        private set
    var centerX = 0f
        private set
    var centerY = 0f
        private set

    private var lines = spec.label.split('\n')
    private var unit = 1f
    private var pulseStartMs = Long.MIN_VALUE
    private var baselineShift = 0f
    private var body = GlassDrawable(spec.kind, radii = PETAL_RADII, shadow = false)
    private val outline = Path()
    private val outlineBox = RectF()
    private val metrics = Paint.FontMetrics()
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(LABEL_FONT, Typeface.NORMAL)
        color = if (spec.kind == GlassKind.Mid) TEXT_LIGHT else TEXT_DARK
    }
    private val iconPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = ICON_STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        color = textPaint.color
    }
    private val starPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFFFFFF.toInt() }
    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader = RadialGradient(0f, 0f, 1f, intArrayOf(GLOW_CORE, GLOW_EDGE), floatArrayOf(GLOW_STOP, 1f), Shader.TileMode.CLAMP)
    }

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
        outlineBox.set(-halfW, -halfH, halfW, halfH)
        outline.rewind()
        outline.addRoundRect(outlineBox, FloatArray(PETAL_RADII.size) { PETAL_RADII[it] * (if (it % 2 == 0) widthPx else heightPx) }, Path.Direction.CW)
        textPaint.textSize = LABEL_SIZE * scale
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
        craters.update(nowMs)
        val idle = -IDLE_RISE * unit * (1f - cos(TWO_PI * seconds / IDLE_PERIOD)) / 2f
        val press = pressScale(nowMs)
        c.save()
        c.translate(centerX + craters.shakeX * unit, centerY + idle + craters.shakeY * unit)
        c.rotate(spec.rotation)
        c.scale(press, press)
        if (spec.kind == GlassKind.Active) drawGlow(c)
        c.save()
        craters.clipHoles(c, nowMs, unit)
        body.draw(c)
        drawStars(c, seconds)
        drawContent(c)
        c.restore()
        if (craters.any) {
            c.save()
            c.clipPath(outline)
            c.save()
            craters.clipHoles(c, nowMs, unit)
            c.scale(unit, unit)
            craters.drawBase(c, nowMs)
            c.restore()
            c.scale(unit, unit)
            craters.drawTop(c, nowMs, seconds)
            c.restore()
        }
        c.restore()
    }

    private fun drawGlow(c: Canvas) {
        c.save()
        c.scale(widthPx * GLOW_REACH, heightPx * GLOW_REACH)
        c.drawCircle(0f, 0f, 1f, glowPaint)
        c.restore()
    }

    private fun drawStars(c: Canvas, seconds: Float) {
        for (i in STAR_X.indices) {
            val twinkle = TW_LOW + (TW_HIGH - TW_LOW) * Ease.wave(seconds / TW_PERIOD + i * 0.23f)
            starPaint.alpha = (twinkle * 255f).toInt()
            c.drawCircle(-widthPx / 2f + widthPx * STAR_X[i], -heightPx / 2f + heightPx * STAR_Y[i], STAR_R[i] * unit, starPaint)
        }
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
        const val LABEL_SIZE = 12f
        const val LINE_HEIGHT = 14.4f
        const val ICON_BOX = 26f
        const val ICON_GAP = 6f
        const val ICON_STROKE = 1.1f
        const val CONTENT_SHIFT = 5f
        const val TEXT_DARK = 0xFF2E2246.toInt()
        const val TEXT_LIGHT = 0xFFFFF6EC.toInt()
        const val IDLE_RISE = 5f
        const val IDLE_PERIOD = 6f
        const val PRESS_MS = 260L
        const val PRESS_DEPTH = 0.06f
        const val GLOW_CORE = 0x80FFBE5A.toInt()
        const val GLOW_EDGE = 0x00FFBE5A
        const val GLOW_STOP = 0.6f
        const val GLOW_REACH = 0.62f
        const val TW_LOW = 0.35f
        const val TW_HIGH = 0.95f
        const val TW_PERIOD = 5.2f
        val STAR_X = floatArrayOf(0.26f, 0.70f, 0.78f, 0.36f)
        val STAR_Y = floatArrayOf(0.30f, 0.24f, 0.70f, 0.76f)
        val STAR_R = floatArrayOf(0.8f, 0.7f, 0.9f, 0.6f)
    }
}
