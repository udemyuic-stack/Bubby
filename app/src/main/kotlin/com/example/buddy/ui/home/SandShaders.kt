package com.example.buddy.ui.home

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Shader

class SandShaders {
    val speckleDark = speckles(DARK_TILE, 0.2f, 0.3f, DARK_RADIUS, Color.argb(115, 190, 150, 85))
    val speckleLight = speckles(LIGHT_TILE, 0.7f, 0.6f, LIGHT_RADIUS, Color.argb(153, 255, 250, 230))

    private fun speckles(tile: Int, fx: Float, fy: Float, radius: Float, color: Int): Paint {
        val bitmap = Bitmap.createBitmap(tile, tile, Bitmap.Config.ARGB_8888)
        val dot = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color }
        Canvas(bitmap).drawCircle(tile * fx, tile * fy, radius, dot)
        return Paint().apply { shader = BitmapShader(bitmap, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT) }
    }

    private companion object {
        const val DARK_TILE = 11
        const val LIGHT_TILE = 8
        const val DARK_RADIUS = 1f
        const val LIGHT_RADIUS = 0.9f
    }
}
