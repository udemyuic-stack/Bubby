package com.example.buddy.ui.home

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import kotlin.math.cos

class GhostPainter {
    private val box = RectF()
    private val oval = RectF()
    private val halo = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader = RadialGradient(CENTER_X, CENTER_Y, HALO_RADIUS, intArrayOf(HALO_CORE, HALO_EDGE), floatArrayOf(0f, 1f), Shader.TileMode.CLAMP)
    }
    private val body = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader = LinearGradient(0f, BODY_TOP, 0f, BODY_BOTTOM, intArrayOf(BODY_A, BODY_B, BODY_C), floatArrayOf(0f, 0.7f, 1f), Shader.TileMode.CLAMP)
    }
    private val rim = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; color = RIM_COLOR; strokeWidth = RIM_WIDTH }
    private val armRim = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; color = ARM_RIM_COLOR; strokeWidth = ARM_RIM_WIDTH }
    private val flat = Paint(Paint.ANTI_ALIAS_FLAG)
    private val eyeLeft = eyePaint(EYE_LEFT_X, EYE_Y)
    private val eyeRight = eyePaint(EYE_RIGHT_X, EYE_Y)
    private val mouth = eyePaint(CENTER_X, MOUTH_Y)

    fun draw(c: Canvas, flight: GhostFlight, seconds: Float, unit: Float) {
        val s = flight.scale * unit
        val cx = flight.x
        val cy = flight.y - BOX_LIFT * unit
        val reach = LAYER_REACH * s
        box.set(cx - reach, cy - reach, cx + reach, cy + reach)
        val layer = c.saveLayerAlpha(box, (flight.alpha.coerceIn(0f, 1f) * MAX_ALPHA).toInt())
        val wobble = Ease.smooth(Ease.wave(seconds / WOBBLE_PERIOD))
        val turn = Math.toRadians((Ease.lerp(-TURN_DEG, TURN_DEG, wobble)).toDouble())
        c.translate(cx, cy + Ease.lerp(-BOB, BOB, wobble) * s)
        c.scale(FIT * s * cos(turn).toFloat(), FIT * s)
        c.translate(-CENTER_X, -CENTER_Y)
        drawArt(c, flight, seconds)
        c.restoreToCount(layer)
    }

    private fun drawArt(c: Canvas, flight: GhostFlight, seconds: Float) {
        c.drawCircle(CENTER_X, CENTER_Y, HALO_RADIUS, halo)
        flat.color = SHADOW_COLOR
        ellipse(c, CENTER_X, SHADOW_Y, SHADOW_RX, SHADOW_RY)
        drawArm(c, HalloweenArt.ghostArmLeft, ARM_LEFT_X, seconds)
        drawArm(c, HalloweenArt.ghostArmRight, ARM_RIGHT_X, seconds + ARM_LAG)
        c.drawPath(HalloweenArt.ghostBody, body)
        flat.color = SHADE_COLOR
        ellipse(c, SHADE_X, SHADE_Y, SHADE_RX, SHADE_RY)
        flat.color = SHINE_COLOR
        ellipse(c, SHINE_X, SHINE_Y, SHINE_RX, SHINE_RY)
        flat.color = Color.WHITE
        ellipse(c, GLINT_X, GLINT_Y, GLINT_RX, GLINT_RY)
        c.drawPath(HalloweenArt.ghostBody, rim)
        drawFace(c, flight)
    }

    private fun drawArm(c: Canvas, arm: Path, pivotX: Float, seconds: Float) {
        val swing = Ease.lerp(-ARM_SWING, ARM_SWING, Ease.smooth(Ease.wave(seconds / ARM_PERIOD)))
        c.save()
        c.rotate(swing, pivotX, ARM_PIVOT_Y)
        c.drawPath(arm, body)
        c.drawPath(arm, armRim)
        c.restore()
    }

    private fun drawFace(c: Canvas, flight: GhostFlight) {
        oval.set(EYE_LEFT_X - EYE_RX, EYE_Y - EYE_RY, EYE_LEFT_X + EYE_RX, EYE_Y + EYE_RY)
        c.drawOval(oval, eyeLeft)
        oval.set(EYE_RIGHT_X - EYE_RX, EYE_Y - EYE_RY, EYE_RIGHT_X + EYE_RX, EYE_Y + EYE_RY)
        c.drawOval(oval, eyeRight)
        flat.color = Color.WHITE
        c.drawCircle(GLINT_EYE_LEFT, GLINT_EYE_Y, GLINT_EYE_R, flat)
        c.drawCircle(GLINT_EYE_RIGHT, GLINT_EYE_Y, GLINT_EYE_R, flat)
        c.save()
        c.scale(flight.mouthX, flight.mouthY, CENTER_X, MOUTH_Y)
        oval.set(CENTER_X - MOUTH_RX, MOUTH_Y - MOUTH_RY, CENTER_X + MOUTH_RX, MOUTH_Y + MOUTH_RY)
        c.drawOval(oval, mouth)
        flat.color = Color.WHITE
        c.drawPath(HalloweenArt.ghostFangs, flat)
        flat.color = LOWER_FANG_COLOR
        c.drawPath(HalloweenArt.ghostLowerFangs, flat)
        c.restore()
        flat.color = CHEEK_COLOR
        ellipse(c, CHEEK_LEFT_X, CHEEK_Y, CHEEK_RX, CHEEK_RY)
        ellipse(c, CHEEK_RIGHT_X, CHEEK_Y, CHEEK_RX, CHEEK_RY)
    }

    private fun ellipse(c: Canvas, cx: Float, cy: Float, rx: Float, ry: Float) {
        oval.set(cx - rx, cy - ry, cx + rx, cy + ry)
        c.drawOval(oval, flat)
    }

    private fun eyePaint(cx: Float, cy: Float) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader = RadialGradient(cx - EYE_FOCUS, cy - EYE_FOCUS, EYE_GRADIENT, EYE_CORE, EYE_EDGE, Shader.TileMode.CLAMP)
    }

    private companion object {
        const val CENTER_X = 85f
        const val CENTER_Y = 73f
        const val FIT = 0.38f
        const val BOX_LIFT = 21f
        const val LAYER_REACH = 55f
        const val MAX_ALPHA = 255f
        const val WOBBLE_PERIOD = 1.1f
        const val TURN_DEG = 30f
        const val BOB = 3f
        const val HALO_RADIUS = 100f
        const val HALO_CORE = 0x59FFFFFF
        const val HALO_EDGE = 0x00FFFFFF
        const val BODY_TOP = 16f
        const val BODY_BOTTOM = 120f
        const val BODY_A = 0xFAFFFFFF.toInt()
        const val BODY_B = 0xEBEFE8FF.toInt()
        const val BODY_C = 0xBFD8CDF2.toInt()
        const val RIM_COLOR = 0xBFFFFFFF.toInt()
        const val RIM_WIDTH = 2f
        const val ARM_RIM_COLOR = 0xB3FFFFFF.toInt()
        const val ARM_RIM_WIDTH = 1.5f
        const val ARM_LEFT_X = 27f
        const val ARM_RIGHT_X = 143f
        const val ARM_PIVOT_Y = 66f
        const val ARM_SWING = 10f
        const val ARM_PERIOD = 0.7f
        const val ARM_LAG = 0.17f
        const val SHADOW_COLOR = 0x401A0F26
        const val SHADOW_Y = 150f
        const val SHADOW_RX = 42f
        const val SHADOW_RY = 6f
        const val SHADE_COLOR = 0x598A74B8
        const val SHADE_X = 118f
        const val SHADE_Y = 88f
        const val SHADE_RX = 20f
        const val SHADE_RY = 30f
        const val SHINE_COLOR = 0xB3FFFFFF.toInt()
        const val SHINE_X = 62f
        const val SHINE_Y = 38f
        const val SHINE_RX = 20f
        const val SHINE_RY = 11f
        const val GLINT_X = 58f
        const val GLINT_Y = 34f
        const val GLINT_RX = 7f
        const val GLINT_RY = 4f
        const val EYE_LEFT_X = 66f
        const val EYE_RIGHT_X = 104f
        const val EYE_Y = 64f
        const val EYE_RX = 9f
        const val EYE_RY = 12f
        const val EYE_FOCUS = 3f
        const val EYE_GRADIENT = 14f
        const val EYE_CORE = 0xFF5A4A78.toInt()
        const val EYE_EDGE = 0xFF140D22.toInt()
        const val GLINT_EYE_LEFT = 63f
        const val GLINT_EYE_RIGHT = 101f
        const val GLINT_EYE_Y = 59f
        const val GLINT_EYE_R = 3f
        const val MOUTH_Y = 94f
        const val MOUTH_RX = 12f
        const val MOUTH_RY = 13f
        const val LOWER_FANG_COLOR = 0xE6FFFFFF.toInt()
        const val CHEEK_COLOR = 0x99FF9FB2.toInt()
        const val CHEEK_LEFT_X = 50f
        const val CHEEK_RIGHT_X = 120f
        const val CHEEK_Y = 82f
        const val CHEEK_RX = 8f
        const val CHEEK_RY = 5f
    }
}
