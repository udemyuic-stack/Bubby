package com.example.buddy.ui.home

import android.view.animation.PathInterpolator
import kotlin.math.floor

object OceanMotion {
    val ease = PathInterpolator(0.25f, 0.1f, 0.25f, 1f)
    val easeInOut = PathInterpolator(0.42f, 0f, 0.58f, 1f)
    val easeIn = PathInterpolator(0.42f, 0f, 1f, 1f)
    val easeOut = PathInterpolator(0f, 0f, 0.58f, 1f)
    val bounce = PathInterpolator(0.22f, 1f, 0.36f, 1f)
    val rebuild = PathInterpolator(0.3f, 0.7f, 0.3f, 1f)
    val spring = PathInterpolator(0.34f, 1.56f, 0.64f, 1f)
    val walk = PathInterpolator(0.3f, 0.1f, 0.4f, 1f)
    val sand = PathInterpolator(0.3f, 0.6f, 0.3f, 1f)

    fun clamp(x: Float) = if (x < 0f) 0f else if (x > 1f) 1f else x

    fun progress(ms: Long, start: Long, length: Long) = clamp((ms - start).toFloat() / length)

    fun lerp(a: Float, b: Float, t: Float) = a + (b - a) * t

    fun loop(seconds: Float, period: Float): Float {
        val x = seconds.toDouble() / period
        return (x - floor(x)).toFloat()
    }

    fun pingPong(seconds: Float, period: Float): Float {
        val x = seconds.toDouble() / period
        val m = x - 2.0 * floor(x / 2.0)
        return (if (m < 1.0) m else 2.0 - m).toFloat()
    }

    fun triangle(phase: Float) = 1f - kotlin.math.abs(2f * phase - 1f)
}

class Keyframes(private val offsets: FloatArray, private val values: FloatArray) {
    fun at(phase: Float): Float {
        for (i in 1 until offsets.size) {
            if (phase <= offsets[i]) {
                val k = OceanMotion.ease.getInterpolation((phase - offsets[i - 1]) / (offsets[i] - offsets[i - 1]))
                return OceanMotion.lerp(values[i - 1], values[i], k)
            }
        }
        return values[values.size - 1]
    }
}

class Track(private val offsets: FloatArray, private val values: FloatArray) {
    fun at(progress: Float): Float {
        for (i in 1 until offsets.size) {
            if (progress <= offsets[i]) {
                return OceanMotion.lerp(values[i - 1], values[i], (progress - offsets[i - 1]) / (offsets[i] - offsets[i - 1]))
            }
        }
        return values[values.size - 1]
    }
}
