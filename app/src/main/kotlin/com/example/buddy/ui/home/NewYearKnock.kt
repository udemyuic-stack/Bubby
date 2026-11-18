package com.example.buddy.ui.home

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

class NewYearKnock(spec: PetalSpec) {
    val petalIndex = NewYearLayout.KNOCKED_PETAL
    val bx = spec.left + spec.width / 2f
    val by = spec.top + spec.height / 2f
    val bh: Float
    val side = if (bx >= NewYearLayout.CENTER_X) 1f else -1f
    val fallX = (bx + side * FALL_OFFSET).coerceIn(FALL_MIN, FALL_MAX)
    val dx = fallX - bx
    val dyDown: Float
    val bearY = NewYearLayout.GROUND - BEAR_LIFT
    val dyBear = bearY - BEAR_RAISE - by
    private val pose = PetalPose()
    private val fallY = floatArrayOf(0f, 0f, 0f, 0f)
    private val resetX = floatArrayOf(0f, 0f, 0f, 0f)
    private val resetY = floatArrayOf(0f, 0f, -10f, 0f)

    init {
        val rad = Math.toRadians(spec.rotation.toDouble())
        bh = (spec.width * abs(sin(rad)) + spec.height * abs(cos(rad))).toFloat()
        dyDown = NewYearLayout.GROUND - bh * SINK - by
    }

    fun poseAt(ms: Long): PetalPose? = when {
        ms < FALL_AT -> null
        ms < FALL_AT + FALL_MS -> fall(Curves.easeIn.getInterpolation((ms - FALL_AT) / FALL_MS.toFloat()))
        ms < RESET_AT -> set(dx, dyDown, side * 88f)
        ms < RESET_AT + RESET_MS -> reset(Curves.lift.getInterpolation((ms - RESET_AT) / RESET_MS.toFloat()))
        else -> null
    }

    private fun fall(p: Float): PetalPose {
        fallY[1] = dyDown; fallY[2] = dyDown - 10f; fallY[3] = dyDown
        return set(
            dx * lin(p, FALL_AT_STOPS, FALL_X),
            lin(p, FALL_AT_STOPS, fallY),
            side * lin(p, FALL_AT_STOPS, FALL_ROT)
        )
    }

    private fun reset(p: Float): PetalPose {
        resetX[0] = dx; resetX[1] = dx
        resetY[0] = dyDown; resetY[1] = dyBear
        return set(
            lin(p, RESET_AT_STOPS, resetX),
            lin(p, RESET_AT_STOPS, resetY),
            side * lin(p, RESET_AT_STOPS, RESET_ROT)
        )
    }

    private fun set(x: Float, y: Float, r: Float): PetalPose {
        pose.dx = x
        pose.dy = y
        pose.rotation = r
        return pose
    }

    private fun lin(p: Float, at: FloatArray, v: FloatArray): Float {
        var i = 0
        while (i < at.size - 2 && p > at[i + 1]) i++
        return Ease.lerp(v[i], v[i + 1], ((p - at[i]) / (at[i + 1] - at[i])).coerceIn(0f, 1f))
    }

    companion object {
        private val FALL_AT_STOPS = floatArrayOf(0f, 0.8f, 0.9f, 1f)
        private val FALL_X = floatArrayOf(0f, 1f, 1f, 1f)
        private val FALL_ROT = floatArrayOf(0f, 78f, 84f, 88f)
        private val RESET_AT_STOPS = floatArrayOf(0f, 0.4f, 0.85f, 1f)
        private val RESET_ROT = floatArrayOf(88f, 0f, 0f, 0f)
        const val KNOCK_AT = 1150L
        const val FLIGHT_MS = 560L
        const val FALL_AT = KNOCK_AT + FLIGHT_MS
        const val FALL_MS = 900L
        const val RESET_AT = 5850L
        const val RESET_MS = 1300L
        private const val FALL_OFFSET = 30f
        private const val FALL_MIN = 60f
        private const val FALL_MAX = 330f
        private const val SINK = 0.35f
        private const val BEAR_LIFT = 52f
        private const val BEAR_RAISE = 110f
    }
}
