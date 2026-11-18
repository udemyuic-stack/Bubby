package com.example.buddy.ui.home

import kotlin.math.abs

object Ease {
    fun smooth(p: Float): Float {
        val q = p.coerceIn(0f, 1f)
        return q * q * (3f - 2f * q)
    }

    fun lerp(a: Float, b: Float, p: Float) = a + (b - a) * p

    fun wave(x: Float): Float {
        val p = x - kotlin.math.floor(x)
        return if (p < 0.5f) p * 2f else (1f - p) * 2f
    }

    fun hump(p: Float) = smooth(1f - abs(2f * p.coerceIn(0f, 1f) - 1f))

    fun keys(p: Float, at: FloatArray, values: FloatArray): Float {
        val q = p.coerceIn(0f, 1f)
        for (i in 0 until at.size - 1) {
            if (q <= at[i + 1]) return lerp(values[i], values[i + 1], smooth((q - at[i]) / (at[i + 1] - at[i])))
        }
        return values[values.size - 1]
    }
}
