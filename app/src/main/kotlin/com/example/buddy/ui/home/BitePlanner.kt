package com.example.buddy.ui.home

import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

private fun Random.between(from: Float, to: Float) = from + nextFloat() * (to - from)

object BitePlanner {
    const val BITES = 3
    private const val RADIUS_RATIO = 0.21f
    private const val EDGE_RATIO = 0.47f
    private const val TOOTH_REACH = 0.95f
    private const val SHAKE_PX = 4f
    private const val DRIP_COUNT = 3
    private const val SPOT_COUNT = 5
    private const val RIM_OFFSET = 3f
    private val TOOTH_ANGLES = floatArrayOf(-0.75f, -0.32f, 0.32f, 0.75f)
    private val ARC_START = floatArrayOf(-2.6f, -0.4f, 1f)
    private val ARC_END = floatArrayOf(-0.6f, 0.6f, 2.6f)

    fun sites(rng: Random, w: Float, h: Float): FloatArray {
        val out = FloatArray(BITES * 2)
        for (i in 0 until BITES) {
            val a = rng.between(ARC_START[i], ARC_END[i]) - (PI / 2).toFloat()
            out[2 * i] = cos(a) * w * EDGE_RATIO
            out[2 * i + 1] = sin(a) * h * EDGE_RATIO
        }
        return out
    }

    fun bite(rng: Random, x: Float, y: Float, w: Float, h: Float, unit: Float, bornMs: Long): Bite {
        val radius = min(w, h) * RADIUS_RATIO
        val a0 = atan2(-y, -x)
        val teeth = FloatArray(PetalBites.TEETH * 2)
        for (t in 0 until PetalBites.TEETH) {
            teeth[2 * t] = cos(a0 + TOOTH_ANGLES[t]) * radius * TOOTH_REACH
            teeth[2 * t + 1] = sin(a0 + TOOTH_ANGLES[t]) * radius * TOOTH_REACH
        }
        val drips = FloatArray(DRIP_COUNT * 3)
        for (q in 0 until DRIP_COUNT) {
            val a = a0 + rng.between(-1f, 1f)
            drips[3 * q] = x + cos(a) * (radius + RIM_OFFSET * unit)
            drips[3 * q + 1] = y + sin(a) * (radius + RIM_OFFSET * unit)
            drips[3 * q + 2] = rng.between(8f, 18f) * unit
        }
        val spots = FloatArray(SPOT_COUNT * 3)
        for (q in 0 until SPOT_COUNT) {
            val a = a0 + rng.between(-1.6f, 1.6f)
            val d = radius + rng.between(5f, 14f) * unit
            spots[3 * q] = x + cos(a) * d
            spots[3 * q + 1] = y + sin(a) * d
            spots[3 * q + 2] = rng.between(0.8f, 2.2f) * unit
        }
        val len = hypot(x, y).coerceAtLeast(1f)
        return Bite(x, y, radius, teeth, drips, spots, bornMs, x / len * SHAKE_PX * unit, y / len * SHAKE_PX * unit)
    }
}
