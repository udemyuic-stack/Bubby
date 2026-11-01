package com.example.buddy.ui.home

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader

class NewYearBackdrop {
    private class Glow(val x: Float, val y: Float, val rx: Float, val ry: Float, val rotation: Float, val paint: Paint)

    private val glows = listOf(
        Glow(235f, 115f, 215f, 235f, 0f, glowPaint(intArrayOf(0xF7341E1A.toInt(), 0xD94E302A.toInt(), 0x00785450), floatArrayOf(0f, 0.55f, 1f))),
        Glow(360f, 30f, 100f, 60f, 0f, glowPaint(intArrayOf(0x8CE2A0B0.toInt(), 0x00E2A0B0), floatArrayOf(0f, 1f))),
        Glow(50f, 480f, 130f, 150f, 0f, glowPaint(intArrayOf(0xB3FFFFFF.toInt(), 0x00FFFFFF), floatArrayOf(0f, 1f))),
        Glow(200f, 660f, 240f, 100f, -8f, glowPaint(intArrayOf(0x59FFFFFF, 0x00FFFFFF), floatArrayOf(0f, 1f)))
    )
    private val flowerHalo = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader = RadialGradient(
            0f, 0f, HALO_REACH,
            intArrayOf(Color.WHITE, Color.WHITE, 0xB3FFFFFF.toInt(), 0x00FFFFFF),
            floatArrayOf(0f, HALO_SOLID, HALO_MID, HALO_END),
            Shader.TileMode.CLAMP
        )
    }
    private val flowerGlow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader = RadialGradient(
            0f, 0f, CORE_GLOW,
            intArrayOf(0xF2FFFFFF.toInt(), 0xF2FFFFFF.toInt(), 0x00FFFFFF),
            floatArrayOf(0f, CORE_SOLID, 1f),
            Shader.TileMode.CLAMP
        )
    }
    private val flowerCore = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }

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

    fun drawFlower(c: Canvas) {
        c.save()
        c.translate(NewYearLayout.FLOWER_X, NewYearLayout.FLOWER_Y)
        c.drawCircle(0f, 0f, HALO_RADIUS, flowerHalo)
        c.drawCircle(0f, 0f, CORE_GLOW, flowerGlow)
        c.drawCircle(0f, 0f, CORE_RADIUS, flowerCore)
        c.restore()
    }

    private fun glowPaint(colors: IntArray, stops: FloatArray) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader = RadialGradient(0f, 0f, 1f, colors, stops, Shader.TileMode.CLAMP)
    }

    private companion object {
        const val HALO_RADIUS = 22f
        const val HALO_REACH = 31.1f
        const val HALO_SOLID = 0.25f
        const val HALO_MID = 0.5f
        const val HALO_END = 0.72f
        const val CORE_RADIUS = 11f
        const val CORE_GLOW = 30f
        const val CORE_SOLID = 0.63f
    }
}
