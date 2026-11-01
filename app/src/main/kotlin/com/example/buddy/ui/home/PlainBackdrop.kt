package com.example.buddy.ui.home

import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import com.example.buddy.ui.Theme
import com.example.buddy.ui.ThemeDef

class PlainBackdrop(theme: ThemeDef) {
    private class Glow(val x: Float, val y: Float, val rx: Float, val ry: Float, val rotation: Float, val paint: Paint)

    private val flower = HalloweenBackdrop()
    private val glows = listOf(
        Glow(235f, 115f, 215f, 235f, 0f, glowPaint(intArrayOf(Theme.rgb(theme.dark, 247), Theme.rgb(theme.dark, 217), Theme.rgb(theme.dark, 0)), floatArrayOf(0f, 0.55f, 1f))),
        Glow(360f, 30f, 100f, 60f, 0f, glowPaint(intArrayOf(Theme.rgb(theme.glow2, 140), Theme.rgb(theme.glow2, 0)), floatArrayOf(0f, 1f))),
        Glow(50f, 480f, 130f, 150f, 0f, glowPaint(intArrayOf(0xB3FFFFFF.toInt(), 0x00FFFFFF), floatArrayOf(0f, 1f))),
        Glow(200f, 660f, 240f, 100f, -8f, glowPaint(intArrayOf(0x59FFFFFF, 0x00FFFFFF), floatArrayOf(0f, 1f)))
    )
    private val stemPath = PathData.parse("M60 70 C100 120 160 112 193 90 C230 105 290 100 330 60")
    private val stemShadowPath = PathData.parse("M60 70 C100 125 160 118 193 96")
    private val stem = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = STEM_WIDTH
        color = STEM_COLOR
    }
    private val stemShadow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = STEM_SHADOW_WIDTH
        strokeCap = Paint.Cap.ROUND
        color = STEM_SHADOW_COLOR
        maskFilter = BlurMaskFilter(STEM_BLUR, BlurMaskFilter.Blur.NORMAL)
    }
    private val bitmapPaint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val shadowTarget = RectF(SHADOW_LEFT, STEM_TOP + SHADOW_TOP, SHADOW_LEFT + SHADOW_WIDTH, STEM_TOP + SHADOW_TOP + SHADOW_HEIGHT)
    private var shadowBitmap: Bitmap? = null

    fun layout(scale: Float) {
        shadowBitmap?.recycle()
        val bitmap = Bitmap.createBitmap((SHADOW_WIDTH * scale).toInt().coerceAtLeast(1), (SHADOW_HEIGHT * scale).toInt().coerceAtLeast(1), Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.scale(scale, scale)
        canvas.translate(-SHADOW_LEFT, -SHADOW_TOP)
        canvas.drawPath(stemShadowPath, stemShadow)
        shadowBitmap = bitmap
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
        shadowBitmap?.let { c.drawBitmap(it, null, shadowTarget, bitmapPaint) }
        c.save()
        c.translate(0f, STEM_TOP)
        c.drawPath(stemPath, stem)
        c.restore()
    }

    fun drawFlower(c: Canvas) = flower.drawFlower(c)

    private fun glowPaint(colors: IntArray, stops: FloatArray) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader = RadialGradient(0f, 0f, 1f, colors, stops, Shader.TileMode.CLAMP)
    }

    private companion object {
        const val STEM_TOP = 380f
        const val STEM_WIDTH = 1.5f
        const val STEM_SHADOW_WIDTH = 6f
        val STEM_COLOR = Color.argb(115, 255, 255, 255)
        const val STEM_BLUR = 4.5f
        const val SHADOW_LEFT = 40f
        const val SHADOW_TOP = 50f
        const val SHADOW_WIDTH = 173f
        const val SHADOW_HEIGHT = 66f
        val STEM_SHADOW_COLOR = Color.argb(89, 120, 140, 170)
    }
}
