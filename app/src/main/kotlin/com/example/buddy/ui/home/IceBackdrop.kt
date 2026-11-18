package com.example.buddy.ui.home

import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader

class IceBackdrop {
    private class Glow(val x: Float, val y: Float, val rx: Float, val ry: Float, val rotation: Float, val paint: Paint)

    private val glows = listOf(
        Glow(235f, 115f, 215f, 235f, 0f, glowPaint(intArrayOf(0xF7341E1A.toInt(), 0xD94E302A.toInt(), 0x00785450), floatArrayOf(0f, 0.55f, 1f))),
        Glow(360f, 30f, 100f, 60f, 0f, glowPaint(intArrayOf(0x8CE2A0B0.toInt(), 0x00E2A0B0), floatArrayOf(0f, 1f))),
        Glow(50f, 480f, 130f, 150f, 0f, glowPaint(intArrayOf(0xB3FFFFFF.toInt(), 0x00FFFFFF), floatArrayOf(0f, 1f))),
        Glow(200f, 660f, 240f, 100f, -8f, glowPaint(intArrayOf(0x59FFFFFF, 0x00FFFFFF), floatArrayOf(0f, 1f)))
    )
    private val flower = HalloweenBackdrop()
    private val stemTop = Path().apply {
        moveTo(60f, STEM_TOP + 70f)
        cubicTo(100f, STEM_TOP + 120f, 160f, STEM_TOP + 112f, 193f, STEM_TOP + 90f)
        cubicTo(230f, STEM_TOP + 105f, 290f, STEM_TOP + 100f, 330f, STEM_TOP + 60f)
    }
    private val stemShade = Path().apply {
        moveTo(60f, STEM_TOP + 70f)
        cubicTo(100f, STEM_TOP + 125f, 160f, STEM_TOP + 118f, 193f, STEM_TOP + 96f)
    }
    private val stemLine = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.5f
        color = 0x73FFFFFF
    }
    private val stemBox = RectF()
    private val stemBounds = RectF().also { stemShade.computeBounds(it, true) }
    private val bitmapPaint = Paint(Paint.FILTER_BITMAP_FLAG)
    private var stemShadow: Bitmap? = null

    fun layout(scale: Float) {
        stemShadow?.recycle()
        val pad = SOFT_PAD
        stemBox.set(stemBounds.left - pad, stemBounds.top - pad, stemBounds.right + pad, stemBounds.bottom + pad)
        val bmp = Bitmap.createBitmap((stemBox.width() * scale).toInt() + 1, (stemBox.height() * scale).toInt() + 1, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeWidth = SOFT_WIDTH
            color = SOFT_COLOR
            maskFilter = BlurMaskFilter(((SOFT_SIGMA - 0.5f) / SIGMA_PER_RADIUS) * scale, BlurMaskFilter.Blur.NORMAL)
        }
        canvas.scale(scale, scale)
        canvas.translate(-stemBox.left, -stemBox.top)
        canvas.drawPath(stemShade, paint)
        stemShadow = bmp
    }

    fun drawGlows(c: Canvas) {
        for (i in glows.indices) {
            val g = glows[i]
            c.save()
            c.translate(g.x, g.y)
            c.rotate(g.rotation)
            c.scale(g.rx, g.ry)
            c.drawCircle(0f, 0f, 1f, g.paint)
            c.restore()
        }
    }

    fun drawStem(c: Canvas) {
        stemShadow?.let { c.drawBitmap(it, null, stemBox, bitmapPaint) }
        c.drawPath(stemTop, stemLine)
    }

    fun drawFlower(c: Canvas) = flower.drawFlower(c)

    private fun glowPaint(colors: IntArray, stops: FloatArray) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader = RadialGradient(0f, 0f, 1f, colors, stops, Shader.TileMode.CLAMP)
    }

    private companion object {
        const val STEM_TOP = 380f
        const val SOFT_WIDTH = 6f
        const val SOFT_SIGMA = 3f
        const val SOFT_PAD = 16f
        const val SOFT_COLOR = 0x59788CAA
        const val SIGMA_PER_RADIUS = 0.57735f
    }
}
