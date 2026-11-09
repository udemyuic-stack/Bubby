package com.example.buddy.ui.home

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

class AuroraMascot : IceMascot() {
    var split = 0f
    var spin = 0f

    private val haloPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader = RadialGradient(
            HalloweenLayout.CENTER_X, HALO_CY, HALO_R,
            intArrayOf(HALO_CORE, HALO_MID, 0x00044B39), floatArrayOf(0f, HALO_MID_AT, 1f), Shader.TileMode.CLAMP
        )
    }
    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader = RadialGradient(
            0f, 0f, GLOW_R,
            intArrayOf(0x661EA97B, 0x2272C613, 0x00000000), floatArrayOf(0f, GLOW_MID_AT, 1f), Shader.TileMode.CLAMP
        )
    }
    private val orbPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader = RadialGradient(
            -ORB_R * ORB_LIGHT_X, -ORB_R * ORB_LIGHT_Y, ORB_R * ORB_LIGHT_SPREAD,
            intArrayOf(0xFFF4FFE8.toInt(), 0xFFC8EFC1.toInt(), 0xFF1EA97B.toInt(), 0xFF044B39.toInt()),
            floatArrayOf(0f, ORB_STOP_A, ORB_STOP_B, 1f), Shader.TileMode.CLAMP
        )
    }
    private val rimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = RIM_WIDTH
        color = 0x66FFFFFF
    }

    override fun draw(c: Canvas, seconds: Float, nowMs: Long) {
        c.drawCircle(HalloweenLayout.CENTER_X, HALO_CY, HALO_R, haloPaint)
        super.draw(c, seconds, nowMs)
    }

    override fun drawBody(c: Canvas, seconds: Float) {
        val breath = 1f + BREATH * sin(TWO_PI * seconds / BREATH_PERIOD)
        c.save()
        c.translate(ART_CX, ART_CY)
        c.scale(breath, breath)
        c.drawCircle(0f, 0f, GLOW_R, glowPaint)
        if (split > 0f) drawSatellites(c)
        c.drawOrbAt(0f, 0f, 1f - MAIN_SHRINK * split)
        c.restore()
    }

    private fun Canvas.drawOrbAt(x: Float, y: Float, size: Float) {
        save()
        translate(x, y)
        scale(size, size)
        drawCircle(0f, 0f, ORB_R, orbPaint)
        drawCircle(0f, 0f, ORB_R - RIM_WIDTH / 2f, rimPaint)
        restore()
    }

    private fun drawSatellites(c: Canvas) {
        for (i in 0 until SATELLITES) {
            val a = spin + TWO_PI * i / SATELLITES
            c.drawOrbAt(cos(a) * SPREAD * split, sin(a) * SPREAD * split * SQUASH, SAT_SIZE)
        }
    }

    private companion object {
        const val TWO_PI = (2.0 * PI).toFloat()
        const val HALO_CY = 215f
        const val HALO_R = 150f
        const val HALO_CORE = 0xE6044B39.toInt()
        const val HALO_MID = 0x99053438.toInt()
        const val HALO_MID_AT = 0.55f
        const val ART_CX = 85f
        const val ART_CY = 68f
        const val ORB_R = 46f
        const val ORB_LIGHT_X = 0.35f
        const val ORB_LIGHT_Y = 0.4f
        const val ORB_LIGHT_SPREAD = 1.5f
        const val ORB_STOP_A = 0.25f
        const val ORB_STOP_B = 0.68f
        const val GLOW_R = 82f
        const val GLOW_MID_AT = 0.55f
        const val RIM_WIDTH = 1.2f
        const val BREATH = 0.03f
        const val BREATH_PERIOD = 3.6f
        const val SATELLITES = 3
        const val SPREAD = 62f
        const val SQUASH = 0.55f
        const val SAT_SIZE = 0.42f
        const val MAIN_SHRINK = 0.25f
    }
}
