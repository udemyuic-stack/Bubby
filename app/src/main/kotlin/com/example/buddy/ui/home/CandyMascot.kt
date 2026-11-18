package com.example.buddy.ui.home

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import kotlin.math.PI
import kotlin.math.sin

class CandyMascot : IceMascot() {
    var jelly = 0f

    private val haloPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader = RadialGradient(
            HalloweenLayout.CENTER_X, HALO_CY, HALO_R,
            intArrayOf(HALO_CORE, HALO_MID, 0x004A1633), floatArrayOf(0f, HALO_MID_AT, 1f), Shader.TileMode.CLAMP
        )
    }
    private val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader = RadialGradient(
            -BODY_LIGHT_X, -BODY_LIGHT_Y, BODY_SPREAD,
            intArrayOf(0xFFFF9CC4.toInt(), 0xFFFF5C9A.toInt(), 0xFFD63B78.toInt()), floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP
        )
    }
    private val bellyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x66FFFFFF }
    private val earPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFFE3EE.toInt() }
    private val eyePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF2E0C20.toInt() }
    private val shinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xCCFFFFFF.toInt() }
    private val smilePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = SMILE_WIDTH
        strokeCap = Paint.Cap.ROUND
        color = 0xFF2E0C20.toInt()
    }
    private val oval = RectF()

    override fun draw(c: Canvas, seconds: Float, nowMs: Long) {
        c.drawCircle(HalloweenLayout.CENTER_X, HALO_CY, HALO_R, haloPaint)
        super.draw(c, seconds, nowMs)
    }

    override fun drawBody(c: Canvas, seconds: Float) {
        val wobble = jelly * WOBBLE * sin(TWO_PI * seconds * WOBBLE_HZ)
        c.save()
        c.translate(ART_CX, ART_BOTTOM)
        c.scale(1f + wobble, 1f - wobble)
        c.translate(0f, -BODY_RY)
        for (side in EARS) {
            c.drawCircle(side * EAR_X, -EAR_Y, EAR_R, bodyPaint)
            c.drawCircle(side * EAR_X, -EAR_Y, EAR_R * EAR_INNER, earPaint)
        }
        oval.set(-BODY_RX, -BODY_RY, BODY_RX, BODY_RY)
        c.drawOval(oval, bodyPaint)
        oval.set(-BELLY_RX, -BELLY_RY + BELLY_DY, BELLY_RX, BELLY_RY + BELLY_DY)
        c.drawOval(oval, bellyPaint)
        for (side in EARS) {
            c.drawCircle(side * EYE_X, -EYE_Y, EYE_R, eyePaint)
            c.drawCircle(side * EYE_X - EYE_SHINE, -EYE_Y - EYE_SHINE, EYE_R * EYE_SHINE_SIZE, shinePaint)
        }
        oval.set(-SMILE_RX, -SMILE_Y - SMILE_RY, SMILE_RX, -SMILE_Y + SMILE_RY)
        c.drawArc(oval, SMILE_START, SMILE_SWEEP, false, smilePaint)
        oval.set(-BODY_RX * SHINE_X, -BODY_RY * SHINE_Y - SHINE_R, -BODY_RX * SHINE_X + SHINE_R * 2f, -BODY_RY * SHINE_Y + SHINE_R)
        c.drawOval(oval, shinePaint)
        c.restore()
    }

    private companion object {
        const val TWO_PI = (2.0 * PI).toFloat()
        const val HALO_CY = 215f
        const val HALO_R = 150f
        const val HALO_CORE = 0xE64A1633.toInt()
        const val HALO_MID = 0x997A2A55.toInt()
        const val HALO_MID_AT = 0.55f
        const val ART_CX = 85f
        const val ART_BOTTOM = 126f
        const val BODY_RX = 44f
        const val BODY_RY = 52f
        const val BODY_LIGHT_X = 14f
        const val BODY_LIGHT_Y = 18f
        const val BODY_SPREAD = 80f
        const val BELLY_RX = 24f
        const val BELLY_RY = 22f
        const val BELLY_DY = 18f
        const val EAR_X = 30f
        const val EAR_Y = 44f
        const val EAR_R = 15f
        const val EAR_INNER = 0.55f
        const val EYE_X = 15f
        const val EYE_Y = 20f
        const val EYE_R = 5f
        const val EYE_SHINE = 1.6f
        const val EYE_SHINE_SIZE = 0.35f
        const val SMILE_Y = 4f
        const val SMILE_RX = 11f
        const val SMILE_RY = 8f
        const val SMILE_WIDTH = 2.4f
        const val SMILE_START = 20f
        const val SMILE_SWEEP = 140f
        const val SHINE_X = 0.55f
        const val SHINE_Y = 0.55f
        const val SHINE_R = 5f
        const val WOBBLE = 0.12f
        const val WOBBLE_HZ = 2.2f
        val EARS = floatArrayOf(-1f, 1f)
    }
}
