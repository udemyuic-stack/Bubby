package com.example.buddy.ui.home

import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import android.graphics.Typeface
import com.example.buddy.ui.GlassDrawable
import com.example.buddy.ui.GlassKind
import com.example.buddy.ui.PETAL_RADII
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

class IcePetal(val spec: PetalSpec, private val index: Int) {
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
    private val active = spec.kind == GlassKind.Active
    private val lineHeight = (if (active) ACTIVE_SIZE else LABEL_SIZE) * LINE_FACTOR
    private val metrics = Paint.FontMetrics()
    private val shadowColor = when (spec.kind) {
        GlassKind.Active -> SHADOW_ACTIVE
        GlassKind.Dim -> SHADOW_DIM
        else -> 0
    }
    private val shadowDy = if (active) ACTIVE_DY else DIM_DY
    private val shadowBlur = if (active) ACTIVE_BLUR else DIM_BLUR
    private var shadow: Bitmap? = null
    private var shadowX = 0f
    private var shadowY = 0f
    private val bitmapPaint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(if (active) FONT_ACTIVE else FONT, Typeface.NORMAL)
        color = when (spec.kind) {
            GlassKind.Mid -> TEXT_MID
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
        bakeShadow(halfW, halfH)
        textPaint.textSize = (if (active) ACTIVE_SIZE else LABEL_SIZE) * scale
        textPaint.getFontMetrics(metrics)
        baselineShift = -(metrics.ascent + metrics.descent) / 2f
    }

    private fun bakeShadow(halfW: Float, halfH: Float) {
        shadow?.recycle()
        shadow = null
        if (shadowColor == 0) return
        val pad = (shadowBlur + shadowDy) * unit
        val bmp = Bitmap.createBitmap((2f * (halfW + pad)).toInt() + 1, (2f * (halfH + pad)).toInt() + 1, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val outline = Path()
        outline.addRoundRect(
            RectF(-halfW, -halfH, halfW, halfH),
            FloatArray(PETAL_RADII.size) { PETAL_RADII[it] * (if (it % 2 == 0) widthPx else heightPx) },
            Path.Direction.CW
        )
        val sigma = shadowBlur * unit / 2f
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = shadowColor
            maskFilter = BlurMaskFilter(((sigma - 0.5f) / SIGMA_PER_RADIUS).coerceAtLeast(1f), BlurMaskFilter.Blur.NORMAL)
        }
        canvas.translate(halfW + pad, halfH + pad)
        canvas.save()
        canvas.translate(0f, shadowDy * unit)
        canvas.drawPath(outline, paint)
        canvas.restore()
        val clear = Paint(Paint.ANTI_ALIAS_FLAG).apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR) }
        canvas.drawPath(outline, clear)
        shadow = bmp
        shadowX = -halfW - pad
        shadowY = -halfH - pad
    }

    fun hitArea(): RectF {
        val dx = spec.width * HalloweenLayout.HIT_INSET
        val dy = spec.height * HalloweenLayout.HIT_INSET
        return RectF(spec.left + dx, spec.top + dy, spec.left + spec.width - dx, spec.top + spec.height - dy)
    }

    fun pulse(nowMs: Long) { pulseStartMs = nowMs }

    fun draw(c: Canvas, seconds: Float, nowMs: Long) {
        val idle = -IDLE_RISE * unit * (1f - cos(TWO_PI * (seconds + index * IDLE_STAGGER) / IDLE_PERIOD)) / 2f
        val press = pressScale(nowMs)
        c.save()
        c.translate(centerX, centerY + idle)
        c.rotate(spec.rotation)
        c.scale(press, press)
        shadow?.let { c.drawBitmap(it, shadowX, shadowY, bitmapPaint) }
        body.draw(c)
        drawContent(c)
        c.restore()
    }

    private fun drawContent(c: Canvas) {
        val total = (ICON_BOX + ICON_GAP) * unit + lineHeight * unit * lines.size
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
        const val FONT = "sans-serif-light"
        const val FONT_ACTIVE = "sans-serif"
        const val LABEL_SIZE = 12f
        const val ACTIVE_SIZE = 13f
        const val LINE_FACTOR = 1.2f
        const val ICON_BOX = 26f
        const val ICON_GAP = 6f
        const val ICON_STROKE = 1.1f
        const val CONTENT_SHIFT = 5f
        const val TEXT_DIM = 0xFF3F5578.toInt()
        const val TEXT_MID = 0xFFFFFFFF.toInt()
        const val TEXT_ACTIVE = 0xFF2A3A58.toInt()
        const val IDLE_RISE = 5f
        const val IDLE_PERIOD = 6f
        const val IDLE_STAGGER = 0.9f
        const val SIGMA_PER_RADIUS = 0.57735f
        const val SHADOW_DIM = 0x2E5A78A5
        const val SHADOW_ACTIVE = 0x4D3C5A8C
        const val DIM_DY = 10f
        const val DIM_BLUR = 24f
        const val ACTIVE_DY = 14f
        const val ACTIVE_BLUR = 34f
        const val PRESS_MS = 260L
        const val PRESS_DEPTH = 0.06f
    }
}
