package com.example.buddy.ui.home

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
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

class PetalPose(var dx: Float = 0f, var dy: Float = 0f, var rotation: Float = 0f)

class NewYearPetal(val spec: PetalSpec, private val capped: Boolean) {
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
    private val metrics = Paint.FontMetrics()
    private val labelSize = if (spec.kind == GlassKind.Active) LABEL_SIZE_ACTIVE else LABEL_SIZE
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(LABEL_FONT, Typeface.NORMAL)
        color = when (spec.kind) {
            GlassKind.Mid -> TEXT_LIGHT
            GlassKind.Active -> TEXT_ACTIVE
            else -> TEXT_DARK
        }
    }
    private val iconPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = ICON_STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        color = textPaint.color
    }
    private val frostPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
    private val capFill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
    private val capShadeFill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = CAP_SHADE }
    private val capGlintFill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(CAP_GLINT_ALPHA, 255, 255, 255) }
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
        textPaint.textSize = labelSize * scale
        textPaint.getFontMetrics(metrics)
        baselineShift = -(metrics.ascent + metrics.descent) / 2f
    }

    fun hitArea(): RectF {
        val dx = spec.width * NewYearLayout.HIT_INSET
        val dy = spec.height * NewYearLayout.HIT_INSET
        return RectF(spec.left + dx, spec.top + dy, spec.left + spec.width - dx, spec.top + spec.height - dy)
    }

    fun pulse(nowMs: Long) { pulseStartMs = nowMs }

    fun draw(c: Canvas, seconds: Float, nowMs: Long, pose: PetalPose?) {
        val idle = if (pose == null) -IDLE_RISE * unit * (1f - cos(TWO_PI * seconds / IDLE_PERIOD)) / 2f else 0f
        val press = pressScale(nowMs)
        c.save()
        c.translate(centerX + (pose?.dx ?: 0f) * unit, centerY + idle + (pose?.dy ?: 0f) * unit)
        c.rotate(spec.rotation + (pose?.rotation ?: 0f))
        c.scale(press, press)
        if (spec.kind == GlassKind.Active) drawGlow(c)
        body.draw(c)
        drawFrost(c, seconds)
        if (capped) drawCap(c)
        drawContent(c)
        c.restore()
    }

    private fun drawGlow(c: Canvas) {
        c.save()
        c.scale(widthPx * GLOW_REACH, heightPx * GLOW_REACH)
        c.drawCircle(0f, 0f, 1f, glowPaint)
        c.restore()
    }

    private fun drawFrost(c: Canvas, seconds: Float) {
        val twinkle = FROST_LOW + (1f - FROST_LOW) * Ease.smooth(Ease.wave(seconds / FROST_PERIOD))
        for (i in FROST_X.indices) {
            frostPaint.alpha = (FROST_ALPHA[i] * twinkle * MAX_ALPHA).toInt()
            c.drawCircle((FROST_X[i] - 0.5f) * widthPx, (FROST_Y[i] - 0.5f) * heightPx, FROST_R[i] * unit, frostPaint)
        }
    }

    private fun drawCap(c: Canvas) {
        val left = -widthPx / 2f + widthPx * CAP_INSET
        val width = widthPx * (1f - 2f * CAP_INSET)
        c.save()
        c.translate(left, -heightPx / 2f - CAP_RISE * unit)
        c.scale(width / NewYearPetalArt.CAP_VIEW_WIDTH, CAP_HEIGHT * unit / NewYearPetalArt.CAP_VIEW_HEIGHT)
        c.drawPath(NewYearPetalArt.capSnow, capFill)
        c.drawPath(NewYearPetalArt.capShade, capShadeFill)
        c.drawOval(NewYearPetalArt.capGlint, capGlintFill)
        c.restore()
    }

    private fun drawContent(c: Canvas) {
        val total = (ICON_BOX + ICON_GAP + LINE_HEIGHT * lines.size) * unit
        val top = -total / 2f
        c.save()
        c.translate(0f, CONTENT_SHIFT * unit)
        c.rotate(-spec.rotation)
        c.save()
        c.translate(-ICON_BOX * unit / 2f, top)
        c.scale(ICON_BOX * unit / ICON_VIEW, ICON_BOX * unit / ICON_VIEW)
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
        const val LABEL_SIZE_ACTIVE = 13f
        const val LINE_HEIGHT = 14.4f
        const val ICON_BOX = 26f
        const val ICON_VIEW = HalloweenArt.ICON_SIZE
        const val ICON_GAP = 6f
        const val ICON_STROKE = 1.1f
        const val CONTENT_SHIFT = 5f
        const val TEXT_DARK = 0xFF2F4568.toInt()
        const val TEXT_LIGHT = 0xFFFFFFFF.toInt()
        const val TEXT_ACTIVE = 0xFF2A3A58.toInt()
        const val IDLE_RISE = 5f
        const val IDLE_PERIOD = 6f
        const val PRESS_MS = 260L
        const val PRESS_DEPTH = 0.06f
        const val GLOW_CORE = 0x59FFFFFF
        const val GLOW_EDGE = 0x00FFFFFF
        const val GLOW_STOP = 0.6f
        const val GLOW_REACH = 0.62f
        const val CAP_INSET = 0.24f
        const val CAP_RISE = 6f
        const val CAP_HEIGHT = 15f
        const val CAP_SHADE = 0xFFD9E6F6.toInt()
        const val CAP_GLINT_ALPHA = 230
        const val FROST_PERIOD = 7f
        const val FROST_LOW = 0.55f
        const val MAX_ALPHA = 255f
        val FROST_X = floatArrayOf(0.22f, 0.74f, 0.64f, 0.34f, 0.84f)
        val FROST_Y = floatArrayOf(0.72f, 0.30f, 0.80f, 0.40f, 0.62f)
        val FROST_R = floatArrayOf(1.4f, 1.2f, 1.6f, 1.1f, 1.2f)
        val FROST_ALPHA = floatArrayOf(0.9f, 0.9f, 0.8f, 0.7f, 0.75f)
    }
}
