package com.example.buddy.ui.home

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import kotlin.random.Random

class OceanSandRise(private val petalCount: Int) {
    private val startX = FloatArray(petalCount * PER_PETAL)
    private val startY = FloatArray(petalCount * PER_PETAL)
    private val targetX = FloatArray(petalCount * PER_PETAL)
    private val targetY = FloatArray(petalCount * PER_PETAL)
    private val light = BooleanArray(petalCount * PER_PETAL)
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val grain = RectF(-HALF, -HALF, HALF, HALF)

    fun seed(areas: List<RectF>, random: Random) {
        for (p in 0 until petalCount) {
            val area = areas[p]
            for (q in 0 until PER_PETAL) {
                val i = p * PER_PETAL + q
                targetX[i] = area.left + (MARGIN + random.nextFloat() * (1f - 2f * MARGIN)) * area.width()
                targetY[i] = area.top + (MARGIN + random.nextFloat() * (1f - 2f * MARGIN)) * area.height()
                startX[i] = targetX[i] + random.nextFloat() * 2f * SIDE_SPREAD - SIDE_SPREAD
                startY[i] = FLOOR
                light[i] = q % LIGHT_EVERY == 0
            }
        }
    }

    fun draw(c: Canvas, ms: Long) {
        for (p in 0 until petalCount) {
            val base = OceanTiming.REBUILD_START + p * OceanTiming.REBUILD_STEP
            if (ms < base || ms > base + LAST_END) continue
            for (q in 0 until PER_PETAL) drawGrain(c, p * PER_PETAL + q, ms - base - q * DELAY_STEP)
        }
    }

    private fun drawGrain(c: Canvas, i: Int, elapsed: Long) {
        if (elapsed < 0L || elapsed > DURATION) return
        val p = OceanMotion.sand.getInterpolation(elapsed.toFloat() / DURATION)
        val travel = OceanMotion.clamp(p / ARRIVE)
        val opacity = when {
            p < FADE_IN -> p / FADE_IN
            p < ARRIVE -> 1f
            else -> 1f - (p - ARRIVE) / (1f - ARRIVE)
        }
        paint.color = if (light[i]) LIGHT else SAND
        paint.alpha = (OceanMotion.clamp(opacity) * OPAQUE).toInt()
        c.save()
        c.translate(OceanMotion.lerp(startX[i], targetX[i], travel), OceanMotion.lerp(startY[i], targetY[i], travel))
        c.drawRoundRect(grain, RADIUS, RADIUS, paint)
        c.restore()
    }

    private companion object {
        const val PER_PETAL = 26
        const val DURATION = 900L
        const val DELAY_STEP = 12L
        const val LAST_END = DURATION + PER_PETAL * DELAY_STEP
        const val FADE_IN = 0.2f
        const val ARRIVE = 0.6f
        const val MARGIN = 0.1f
        const val SIDE_SPREAD = 60f
        const val FLOOR = 860f
        const val LIGHT_EVERY = 3
        const val HALF = 3f
        const val RADIUS = 2.4f
        const val OPAQUE = 255f
        val SAND = Color.rgb(217, 184, 120)
        val LIGHT = Color.rgb(243, 224, 176)
    }
}
