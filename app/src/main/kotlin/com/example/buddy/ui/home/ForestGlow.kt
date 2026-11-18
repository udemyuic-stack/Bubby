package com.example.buddy.ui.home

import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Paint

object ForestGlow {
    const val FLAME_LEFT = -26f
    const val FLAME_TOP = -58f
    const val STEM_LEFT = 40f
    const val STEM_TOP = 50f
    const val SPRITE_SCALE = 2f

    private const val FLAME_WIDTH = 50
    private const val FLAME_HEIGHT = 70
    private const val FLAME_SIGMA = 3f
    private const val FLAME_COLOR = 0xFFFF6A2B.toInt()
    private const val STEM_WIDTH = 170
    private const val STEM_HEIGHT = 90
    private const val STEM_SIGMA = 3f
    private const val STEM_STROKE = 6f
    private const val STEM_COLOR = 0x59788CAA
    private const val SIGMA_TO_RADIUS = 0.57735f

    val flame: Bitmap by lazy {
        val bitmap = Bitmap.createBitmap((FLAME_WIDTH * SPRITE_SCALE).toInt(), (FLAME_HEIGHT * SPRITE_SCALE).toInt(), Bitmap.Config.ARGB_8888)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = FLAME_COLOR
            maskFilter = BlurMaskFilter(radius(FLAME_SIGMA), BlurMaskFilter.Blur.NORMAL)
        }
        val canvas = Canvas(bitmap)
        canvas.scale(SPRITE_SCALE, SPRITE_SCALE)
        canvas.translate(-FLAME_LEFT, -FLAME_TOP)
        canvas.drawPath(ForestArt.flameOuter, paint)
        bitmap
    }

    val stem: Bitmap by lazy {
        val bitmap = Bitmap.createBitmap((STEM_WIDTH * SPRITE_SCALE).toInt(), (STEM_HEIGHT * SPRITE_SCALE).toInt(), Bitmap.Config.ARGB_8888)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = STEM_STROKE
            strokeCap = Paint.Cap.ROUND
            color = STEM_COLOR
            maskFilter = BlurMaskFilter(radius(STEM_SIGMA), BlurMaskFilter.Blur.NORMAL)
        }
        val canvas = Canvas(bitmap)
        canvas.scale(SPRITE_SCALE, SPRITE_SCALE)
        canvas.translate(-STEM_LEFT, -STEM_TOP)
        canvas.drawPath(ForestArt.stemShade, paint)
        bitmap
    }

    private fun radius(sigma: Float) = ((sigma * SPRITE_SCALE - 0.5f) / SIGMA_TO_RADIUS).coerceAtLeast(1f)
}
