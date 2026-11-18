package com.example.buddy.ui.home

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader

class DesertMascot : IceMascot() {
    var bloom = 0f
    private var tapAt = Long.MIN_VALUE
    private var spread = 0f
    private var tapBloom = 0f
    private var sway = 0f
    private val spinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = SPINE_WIDTH
        strokeCap = Paint.Cap.ROUND
        color = 0xFFF4E9B8.toInt()
    }
    private val sparkPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFFF2A8.toInt() }

    override fun bounce(nowMs: Long) {
        super.bounce(nowMs)
        tapAt = nowMs
    }

    private fun react(seconds: Float) {
        val tp = (HomeClock.ms() - tapAt) / TAP_MS.toFloat()
        if (tp < 0f || tp > 1f) {
            spread = 0f
            tapBloom = 0f
        } else {
            spread = Ease.smooth(tp / SPREAD_IN) * (1f - Ease.smooth((tp - SPREAD_OUT) / SPREAD_FADE))
            tapBloom = Ease.smooth((tp - BLOOM_IN) / BLOOM_RISE) * (1f - Ease.smooth((tp - BLOOM_OUT) / BLOOM_FADE))
        }
        sway = SWAY_DEG * kotlin.math.sin(seconds * SWAY_SPEED)
    }

    private val haloPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader = RadialGradient(
            HalloweenLayout.CENTER_X, HALO_CY, HALO_R,
            intArrayOf(HALO_CORE, HALO_MID, 0x003D1F0F), floatArrayOf(0f, HALO_MID_AT, 1f), Shader.TileMode.CLAMP
        )
    }
    private val greenPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF3FA66B.toInt() }
    private val ribPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = RIB_WIDTH
        strokeCap = Paint.Cap.ROUND
        color = 0xFF7BD39A.toInt()
    }
    private val potPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFC8643A.toInt() }
    private val rimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFE8895A.toInt() }
    private val eyePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF2B1408.toInt() }
    private val petalPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFF6FA8.toInt() }
    private val centerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFFD84D.toInt() }
    private val smilePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = SMILE_WIDTH
        strokeCap = Paint.Cap.ROUND
        color = 0xFF2B1408.toInt()
    }
    private val box = RectF()

    override fun draw(c: Canvas, seconds: Float, nowMs: Long) {
        c.drawCircle(HalloweenLayout.CENTER_X, HALO_CY, HALO_R, haloPaint)
        super.draw(c, seconds, nowMs)
    }

    override fun drawBody(c: Canvas, seconds: Float) {
        c.save()
        c.translate(ART_CX, ART_BOTTOM)
        react(seconds)
        val breathe = 1f + BREATHE * kotlin.math.sin(seconds * BREATHE_SPEED) + spread * SPREAD_SWELL
        c.scale(1f + (breathe - 1f) * 0.4f, breathe)
        drawArm(c, -1f)
        drawArm(c, 1f)
        box.set(-BODY_W, -BODY_H - POT_H, BODY_W, -POT_H + BODY_W)
        c.drawRoundRect(box, BODY_W, BODY_W, greenPaint)
        c.drawLine(-RIB_X, -BODY_H - POT_H + RIB_TOP, -RIB_X, -POT_H - RIB_BOTTOM, ribPaint)
        c.drawLine(RIB_X, -BODY_H - POT_H + RIB_TOP, RIB_X, -POT_H - RIB_BOTTOM, ribPaint)
        val blink = if (seconds % BLINK_EVERY < BLINK_FOR) 0.12f else 1f
        for (side in SIDES) {
            c.save()
            c.scale(1f, blink, side * EYE_X, -EYE_Y)
            c.drawCircle(side * EYE_X, -EYE_Y, EYE_R, eyePaint)
            c.restore()
        }
        box.set(-SMILE_RX, -SMILE_Y - SMILE_RY, SMILE_RX, -SMILE_Y + SMILE_RY)
        c.drawArc(box, SMILE_START, SMILE_SWEEP, false, smilePaint)
        drawPot(c)
        if (spread > 0.01f) drawSpines(c)
        val level = maxOf(bloom, tapBloom)
        if (level > 0f) drawBloom(c, level)
        c.restore()
    }

    private fun drawSpines(c: Canvas) {
        spinePaint.alpha = (255 * spread).toInt()
        for (side in SIDES) {
            for (i in 0 until SPINES) {
                val y = -POT_H - 14f - i * SPINE_GAP
                val x = side * (BODY_W - 1f)
                c.drawLine(x, y, x + side * SPINE_LEN * spread, y - SPINE_LEN * 0.55f * spread, spinePaint)
            }
        }
    }

    private fun drawArm(c: Canvas, side: Float) {
        c.save()
        c.rotate(sway * side, 0f, -ARM_Y)
        box.set(minOf(0f, side * ARM_REACH), -ARM_Y - ARM_T, maxOf(0f, side * ARM_REACH), -ARM_Y)
        c.drawRoundRect(box, ARM_T / 2f, ARM_T / 2f, greenPaint)
        val x = side * ARM_REACH
        box.set(x - ARM_T / 2f, -ARM_Y - ARM_UP, x + ARM_T / 2f, -ARM_Y)
        c.drawRoundRect(box, ARM_T / 2f, ARM_T / 2f, greenPaint)
        c.restore()
    }

    private fun drawPot(c: Canvas) {
        box.set(-POT_W, -POT_H, POT_W, 0f)
        c.drawRoundRect(box, POT_R, POT_R, potPaint)
        box.set(-POT_W - POT_LIP, -POT_H, POT_W + POT_LIP, -POT_H + POT_LIP_H)
        c.drawRoundRect(box, POT_R, POT_R, rimPaint)
    }

    private fun drawBloom(c: Canvas, level: Float) {
        val r = BLOOM_R
        c.save()
        c.translate(0f, -BODY_H - POT_H + BLOOM_DY)
        for (i in 0 until PETALS) {
            val k = (level * (PETALS + 1) - i).coerceIn(0f, 1f)
            if (k <= 0f) continue
            c.save()
            c.rotate(360f * i / PETALS)
            c.drawCircle(0f, -r * k, r * PETAL_SIZE * k, petalPaint)
            c.restore()
        }
        c.drawCircle(0f, 0f, r * CENTER_SIZE * Ease.smooth(level), centerPaint)
        if (level > SPARK_FROM) {
            val s = (level - SPARK_FROM) / (1f - SPARK_FROM)
            sparkPaint.alpha = (255 * s).toInt()
            c.drawCircle(-r * 1.6f, -r * 1.7f - 3f * s, 1.8f, sparkPaint)
            c.drawCircle(r * 1.5f, -r * 2.2f - 4f * s, 1.4f, sparkPaint)
            c.drawCircle(r * 0.2f, -r * 2.8f - 5f * s, 1.1f, sparkPaint)
        }
        c.restore()
    }

    private companion object {
        const val TAP_MS = 2200L
        const val SPREAD_IN = 0.12f
        const val SPREAD_OUT = 0.4f
        const val SPREAD_FADE = 0.2f
        const val SPREAD_SWELL = 0.03f
        const val BLOOM_IN = 0.14f
        const val BLOOM_RISE = 0.4f
        const val BLOOM_OUT = 0.72f
        const val BLOOM_FADE = 0.28f
        const val SPARK_FROM = 0.85f
        const val SWAY_DEG = 2.4f
        const val SWAY_SPEED = 1.3f
        const val BREATHE = 0.018f
        const val BREATHE_SPEED = 2.1f
        const val BLINK_EVERY = 4.3f
        const val BLINK_FOR = 0.14f
        const val SPINES = 4
        const val SPINE_GAP = 16f
        const val SPINE_LEN = 9f
        const val SPINE_WIDTH = 1.8f
        const val HALO_CY = 215f
        const val HALO_R = 150f
        const val HALO_CORE = 0xE63D1F0F.toInt()
        const val HALO_MID = 0x996B3A1C.toInt()
        const val HALO_MID_AT = 0.55f
        const val ART_CX = 85f
        const val ART_BOTTOM = 130f
        const val BODY_W = 22f
        const val BODY_H = 88f
        const val RIB_X = 8f
        const val RIB_WIDTH = 2f
        const val RIB_TOP = 22f
        const val RIB_BOTTOM = 8f
        const val ARM_Y = 34f
        const val ARM_T = 14f
        const val ARM_REACH = 38f
        const val ARM_UP = 34f
        const val EYE_X = 8f
        const val EYE_Y = 70f
        const val EYE_R = 3.6f
        const val SMILE_Y = 56f
        const val SMILE_RX = 8f
        const val SMILE_RY = 6f
        const val SMILE_WIDTH = 2.2f
        const val SMILE_START = 20f
        const val SMILE_SWEEP = 140f
        const val POT_W = 26f
        const val POT_H = 26f
        const val POT_R = 6f
        const val POT_LIP = 4f
        const val POT_LIP_H = 9f
        const val BLOOM_R = 10f
        const val BLOOM_DY = -2f
        const val PETALS = 5
        const val PETAL_SIZE = 0.7f
        const val CENTER_SIZE = 0.6f
        val SIDES = floatArrayOf(-1f, 1f)
    }
}
