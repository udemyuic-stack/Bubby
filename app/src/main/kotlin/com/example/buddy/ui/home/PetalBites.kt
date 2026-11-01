package com.example.buddy.ui.home

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import kotlin.math.PI
import kotlin.math.min
import kotlin.math.sin

class Bite(
    val x: Float,
    val y: Float,
    val radius: Float,
    val teeth: FloatArray,
    val drips: FloatArray,
    val spots: FloatArray,
    val bornMs: Long,
    val shakeX: Float,
    val shakeY: Float
)

class PetalBites(
    private val bites: List<Bite>,
    private val unit: Float,
    private val healHalfMs: Long,
    private val healFullMs: Long
) {
    var growth = 1f
        private set
    var flash = 0f
        private set
    var offsetX = 0f
        private set
    var offsetY = 0f
        private set
    var visible = false
        private set
    private var now = 0L

    private val clear = Paint(Paint.ANTI_ALIAS_FLAG).apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR) }
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val drip = Path()

    fun update(nowMs: Long) {
        now = nowMs
        growth = growthAt(nowMs)
        flash = flashAt(nowMs)
        visible = growth > MIN_GROWTH && bites.isNotEmpty() && nowMs >= bites[0].bornMs
        offsetX = 0f
        offsetY = 0f
        for (i in bites.indices) {
            val age = nowMs - bites[i].bornMs
            if (age in 0 until SHAKE_MS) {
                val push = sin(PI.toFloat() * age / SHAKE_MS)
                offsetX = bites[i].shakeX * push
                offsetY = bites[i].shakeY * push
            }
        }
    }

    fun punch(c: Canvas) {
        for (i in bites.indices) {
            val b = bites[i]
            if (now < b.bornMs) continue
            c.drawCircle(b.x, b.y, b.radius * growth, clear)
            for (t in 0 until TEETH) {
                c.drawCircle(b.x + b.teeth[2 * t] * growth, b.y + b.teeth[2 * t + 1] * growth, b.radius * TOOTH_RATIO * growth, clear)
            }
        }
    }

    fun drawMarks(c: Canvas) {
        for (i in bites.indices) {
            val b = bites[i]
            if (now >= b.bornMs) drawBiteMarks(c, b)
        }
    }

    private fun drawBiteMarks(c: Canvas, b: Bite) {
        val r = b.radius * growth
        stroke.color = RIM_DARK
        stroke.strokeWidth = RIM_DARK_WIDTH * unit
        c.drawCircle(b.x, b.y, r + RIM_DARK_GAP * unit, stroke)
        stroke.color = RIM_RED
        stroke.strokeWidth = RIM_RED_WIDTH * unit
        c.drawCircle(b.x, b.y, r + RIM_RED_GAP * unit, stroke)
        stroke.color = RIM_DARK
        stroke.strokeWidth = TOOTH_WIDTH * unit
        for (t in 0 until TEETH) {
            c.drawCircle(b.x + b.teeth[2 * t] * growth, b.y + b.teeth[2 * t + 1] * growth, b.radius * TOOTH_RATIO * growth + TOOTH_GAP * unit, stroke)
        }
        drawBlood(c, b)
    }

    private fun drawBlood(c: Canvas, b: Bite) {
        val alpha = min(1f, growth * BLOOD_FADE)
        val flow = ((now - b.bornMs) / DRIP_MS.toFloat()).coerceIn(0f, 1f)
        c.save()
        c.translate(b.x, b.y)
        c.scale(growth, growth)
        c.translate(-b.x, -b.y)
        fill.color = DRIP_COLOR
        fill.alpha = (alpha * FULL_ALPHA).toInt()
        for (d in 0 until b.drips.size / 3) {
            val x = b.drips[3 * d]
            val y = b.drips[3 * d + 1]
            val h = b.drips[3 * d + 2] * flow * flow
            val half = DRIP_HALF * unit
            val belly = DRIP_BELLY * unit
            drip.rewind()
            drip.moveTo(x - half, y)
            drip.cubicTo(x - half, y + h * DRIP_NECK, x - belly, y + h, x, y + h)
            drip.cubicTo(x + belly, y + h, x + half, y + h * DRIP_NECK, x + half, y)
            drip.close()
            c.drawPath(drip, fill)
        }
        fill.color = SPOT_COLOR
        fill.alpha = (alpha * FULL_ALPHA).toInt()
        for (s in 0 until b.spots.size / 3) c.drawCircle(b.spots[3 * s], b.spots[3 * s + 1], b.spots[3 * s + 2], fill)
        c.restore()
    }

    private fun growthAt(t: Long): Float = when {
        t < healHalfMs -> 1f
        t < healHalfMs + HEAL_MS -> 1f + (HALF - 1f) * easeOut((t - healHalfMs) / HEAL_MS.toFloat())
        t < healFullMs -> HALF
        t < healFullMs + HEAL_MS -> HALF * (1f - easeOut((t - healFullMs) / HEAL_MS.toFloat()))
        else -> 0f
    }

    private fun flashAt(t: Long): Float = flashIn(t - healHalfMs - HEAL_MS) + flashIn(t - healFullMs - HEAL_MS)

    private fun flashIn(age: Long): Float = if (age in 0 until FLASH_MS) sin(PI.toFloat() * age / FLASH_MS) else 0f

    private fun easeOut(p: Float): Float {
        val q = 1f - p.coerceIn(0f, 1f)
        return 1f - q * q * q
    }

    companion object {
        const val HEAL_MS = 700L
        const val FLASH_MS = 700L
        const val TEETH = 4
        private const val HALF = 0.5f
        private const val MIN_GROWTH = 0.02f
        private const val SHAKE_MS = 220L
        private const val DRIP_MS = 900L
        private const val TOOTH_RATIO = 0.2f
        private const val BLOOD_FADE = 1.4f
        private const val FULL_ALPHA = 255f
        private const val RIM_DARK = 0xFF7A0012.toInt()
        private const val RIM_RED = 0xFFE01430.toInt()
        private const val DRIP_COLOR = 0xFFA8001B.toInt()
        private const val SPOT_COLOR = 0xFFB0001C.toInt()
        private const val RIM_DARK_WIDTH = 5.5f
        private const val RIM_DARK_GAP = 2.6f
        private const val RIM_RED_WIDTH = 1.8f
        private const val RIM_RED_GAP = 1f
        private const val TOOTH_WIDTH = 3f
        private const val TOOTH_GAP = 1.6f
        private const val DRIP_HALF = 2.4f
        private const val DRIP_BELLY = 3.6f
        private const val DRIP_NECK = 0.6f
    }
}
