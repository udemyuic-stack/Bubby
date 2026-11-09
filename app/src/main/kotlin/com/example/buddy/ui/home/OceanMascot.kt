package com.example.buddy.ui.home

import android.graphics.Canvas
import com.example.buddy.ui.Art
import com.example.buddy.ui.SvgArt

class OceanMascot {
    private var art: Art? = null
    private var reactStartMs = NEVER
    private val rise = Keyframes(FLOAT_OFFSETS, floatArrayOf(0f, -8f, -11f, -4f, 0f))
    private val stretchX = Keyframes(FLOAT_OFFSETS, floatArrayOf(1f, 1.04f, 0.97f, 1.02f, 1f))
    private val stretchY = Keyframes(FLOAT_OFFSETS, floatArrayOf(1f, 0.96f, 1.03f, 0.98f, 1f))

    fun react(nowMs: Long) {
        reactStartMs = nowMs
    }

    fun draw(c: Canvas, seconds: Float, nowMs: Long, spinning: Boolean, island: Float) {
        val picture = art ?: SvgArt.get(ART_NAME)?.also { art = it } ?: return
        val phase = OceanMotion.loop(seconds, FLOAT_PERIOD)
        val kick = reactAmount(nowMs)
        c.save()
        c.translate(HalloweenLayout.MASCOT_LEFT, HalloweenLayout.MASCOT_TOP + rise.at(phase))
        c.scale(stretchX.at(phase), stretchY.at(phase), PIVOT_X, PIVOT_Y)
        c.translate(0f, -KICK_LIFT * kick)
        c.rotate(-KICK_TILT * kick, PIVOT_X, PIVOT_Y)
        val grow = 1f + KICK_GROW * kick
        c.scale(grow, grow, PIVOT_X, PIVOT_Y)
        if (spinning) {
            val swing = OceanMotion.triangle(OceanMotion.loop(seconds, SPIN_PERIOD))
            c.rotate(-SPIN_DEGREES + 2f * SPIN_DEGREES * swing, HalloweenLayout.MASCOT_WIDTH / 2f, HalloweenLayout.MASCOT_HEIGHT / 2f)
        }
        val shrink = OceanMotion.lerp(1f, ISLAND_SCALE, island)
        c.scale(shrink, shrink, PIVOT_X, 0f)
        c.scale(ART_SCALE, ART_SCALE)
        picture.draw(c, seconds)
        c.restore()
    }

    private fun reactAmount(nowMs: Long): Float {
        if (reactStartMs == NEVER) return 0f
        val p = (nowMs - reactStartMs).toFloat() / REACT_MS
        if (p >= 1f) return 0f
        return if (p < 0.5f) OceanMotion.bounce.getInterpolation(p * 2f) else 1f - OceanMotion.bounce.getInterpolation(p * 2f - 1f)
    }

    private companion object {
        const val ART_NAME = "jelly"
        const val ART_SCALE = 0.68f
        const val ISLAND_SCALE = 0.68f
        const val NEVER = -1L
        const val FLOAT_PERIOD = 4.8f
        const val PIVOT_X = HalloweenLayout.MASCOT_WIDTH / 2f
        const val PIVOT_Y = HalloweenLayout.MASCOT_HEIGHT * 0.92f
        const val REACT_MS = 700L
        const val KICK_LIFT = 14f
        const val KICK_TILT = 7f
        const val KICK_GROW = 0.1f
        const val SPIN_PERIOD = 0.6f
        const val SPIN_DEGREES = 14f
        val FLOAT_OFFSETS = floatArrayOf(0f, 0.22f, 0.45f, 0.7f, 1f)
    }
}
