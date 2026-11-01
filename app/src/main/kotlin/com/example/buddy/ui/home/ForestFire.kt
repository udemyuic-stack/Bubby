package com.example.buddy.ui.home

import android.graphics.Canvas
import android.graphics.Paint
import kotlin.math.tan

class ForestFire {
    private val outer = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = OUTER_COLOR }
    private val inner = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = INNER_COLOR }
    private val glow = Paint(Paint.FILTER_BITMAP_FLAG)
    private val smoke = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = SMOKE_COLOR }

    fun draw(c: Canvas, tree: Int, seconds: Float, fire: Float, smokeLevel: Float) {
        c.save()
        c.translate(ForestTrees.x(tree), ForestTrees.baseY(tree))
        val size = ForestTrees.size(tree)
        c.scale(size, size)
        if (fire > 0f) {
            outer.alpha = (fire * MAX_ALPHA).toInt()
            inner.alpha = outer.alpha
            glow.alpha = (fire * GLOW_ALPHA).toInt()
            for (i in FLAMES.indices step FLAME_STRIDE) drawFlame(c, i, seconds)
        }
        if (smokeLevel > 0f) drawSmoke(c, seconds, smokeLevel)
        c.restore()
    }

    private fun drawFlame(c: Canvas, i: Int, seconds: Float) {
        c.save()
        c.translate(FLAMES[i], FLAMES[i + 1])
        c.scale(FLAMES[i + 2], FLAMES[i + 2])
        flicker(c, seconds - FLAMES[i + 3])
        c.save()
        c.translate(ForestGlow.FLAME_LEFT, ForestGlow.FLAME_TOP)
        c.scale(1f / ForestGlow.SPRITE_SCALE, 1f / ForestGlow.SPRITE_SCALE)
        c.drawBitmap(ForestGlow.flame, 0f, 0f, glow)
        c.restore()
        c.drawPath(ForestArt.flameOuter, outer)
        c.restore()
        c.save()
        c.translate(FLAMES[i], FLAMES[i + 1])
        c.scale(FLAMES[i + 2], FLAMES[i + 2])
        flicker(c, seconds - FLAMES[i + 4])
        c.drawPath(ForestArt.flameInner, inner)
        c.restore()
    }

    private fun flicker(c: Canvas, seconds: Float) {
        val p = Ease.smooth(Ease.wave(seconds / FLICKER_PERIOD))
        c.scale(Ease.lerp(FLICKER_X_MIN, FLICKER_X_MAX, p), Ease.lerp(FLICKER_Y_MIN, FLICKER_Y_MAX, p))
        c.skew(tan(Math.toRadians(Ease.lerp(-FLICKER_SKEW, FLICKER_SKEW, p).toDouble())).toFloat(), 0f)
    }

    private fun drawSmoke(c: Canvas, seconds: Float, level: Float) {
        for (i in PUFFS.indices step PUFF_STRIDE) {
            var phase = ((seconds - PUFFS[i + 3]) / SMOKE_PERIOD) % 1f
            if (phase < 0f) phase += 1f
            val e = 1f - (1f - phase) * (1f - phase)
            smoke.alpha = (SMOKE_ALPHA * (1f - e) * level * MAX_ALPHA).toInt()
            c.drawCircle(PUFFS[i] + SMOKE_DRIFT * e, PUFFS[i + 1] - SMOKE_RISE * e, PUFFS[i + 2] * Ease.lerp(SMOKE_FROM, SMOKE_TO, e), smoke)
        }
    }

    private companion object {
        const val FLAME_STRIDE = 5
        const val PUFF_STRIDE = 4
        const val OUTER_COLOR = 0xFFFF6A2B.toInt()
        const val INNER_COLOR = 0xFFFFD25A.toInt()
        const val SMOKE_COLOR = 0xFF556070.toInt()
        const val FLICKER_PERIOD = 0.64f
        const val FLICKER_X_MIN = 0.82f
        const val FLICKER_X_MAX = 1.1f
        const val FLICKER_Y_MIN = 0.86f
        const val FLICKER_Y_MAX = 1.18f
        const val FLICKER_SKEW = 4f
        const val SMOKE_PERIOD = 2f
        const val SMOKE_ALPHA = 0.7f
        const val SMOKE_DRIFT = 10f
        const val SMOKE_RISE = 70f
        const val SMOKE_FROM = 0.6f
        const val SMOKE_TO = 2f
        const val MAX_ALPHA = 255f
        const val GLOW_ALPHA = 200f
        val FLAMES = floatArrayOf(
            0f, -96f, 1.3f, -0.53f, -0.20f,
            -24f, -86f, 1f, -0.19f, -0.08f,
            24f, -90f, 1.05f, -0.20f, -0.56f,
            -6f, -132f, 0.9f, -0.44f, -0.70f,
            2f, -30f, 0.8f, -0.08f, -0.84f
        )
        val PUFFS = floatArrayOf(
            -10f, -140f, 12f, 0f,
            8f, -140f, 15f, -0.7f,
            0f, -140f, 10f, -1.3f
        )
    }
}
