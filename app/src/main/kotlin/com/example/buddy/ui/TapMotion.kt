package com.example.buddy.ui

import android.graphics.Canvas
import android.graphics.Paint
import com.example.buddy.ui.home.Ease
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin

class TapState {
    private var startMs = Long.MIN_VALUE
    private var lastMs = Long.MIN_VALUE
    var level = 1
        private set
    var egg = false
        private set

    fun tap(nowMs: Long) {
        level = if (nowMs - lastMs < COMBO_MS) (level + 1).coerceAtMost(3) else 1
        egg = kotlin.random.Random.nextInt(EGG_ONE_IN) == 0
        lastMs = nowMs
        startMs = nowMs
    }

    fun phase(nowMs: Long, durMs: Long): Float {
        val age = nowMs - startMs
        return if (age < 0 || age >= durMs) -1f else age / durMs.toFloat()
    }

    private companion object {
        const val COMBO_MS = 650L
        const val EGG_ONE_IN = 8
    }
}

object TapMotion {
    const val BUTTERFLY_MS = 1200L
    const val SHARK_MS = 1100L
    private const val TAU = (2.0 * PI).toFloat()

    fun durationMs(name: String): Long = when (name) {
        "butterfly" -> BUTTERFLY_MS
        "shark" -> SHARK_MS
        else -> 0L
    }

    fun butterflyBody(cv: Canvas, p: Float, level: Int, egg: Boolean) {
        val u = Ease.smooth(p) * TAU
        val x: Float
        val y: Float
        if (egg) {
            val s = sin(u)
            x = 0.9f * 16f * s * s * s
            y = -0.9f * (13f * cos(u) - 5f * cos(2f * u) - 2f * cos(3f * u) - cos(4f * u)) + 4.5f
        } else if (level >= 3) {
            x = 22f * sin(u)
            y = -9f * sin(2f * u)
        } else {
            x = 15f * sin(u)
            y = -9f * (1f - cos(u))
        }
        cv.translate(x, y)
        cv.rotate(18f * cos(u) * Ease.hump(p), 85f, 70f)
    }

    fun butterflyWings(p: Float): Float {
        if (p < 0.12f) return 1f - 0.8f * Ease.smooth(p / 0.12f)
        val q = (p - 0.12f) / 0.88f
        val flap = 0.5f + 0.5f * cos(TAU * 4f * q)
        val settle = Ease.smooth(q * 1.4f)
        val open = 0.22f + 0.78f * flap
        return open + (1f - open) * (if (q > 0.85f) settle else 0f)
    }

    fun butterflyAntenna(p: Float): Float = 26f * exp(-4f * p) * sin(14f * p)

    fun sharkBody(cv: Canvas, p: Float, level: Int, egg: Boolean) {
        val x = Ease.keys(p, DASH_AT, DASH_X)
        val sy = Ease.keys(p, BITE_AT, BITE_SY)
        cv.translate(x, 0f)
        cv.scale(1f, sy, 100f, 72f)
        if (egg) cv.rotate(360f * Ease.smooth((p - 0.1f) / 0.8f), 100f, 72f)
        else if (level >= 3) cv.translate(0f, -10f * sin(TAU * p))
    }

    fun sharkTail(p: Float): Float = if (p < 0.2f) 6f * p else 24f * sin(TAU * 3.5f * p) * Ease.hump(p)

    fun sharkEye(p: Float): Float = if (p in 0.02f..0.45f) 0.25f else 1f

    fun sharkGill(p: Float): Float = if (p in 0.45f..0.7f) 1.6f else 1f

    fun sharkBubbles(cv: Canvas, p: Float, paint: Paint) {
        if (p < 0.45f) return
        val q = (p - 0.45f) / 0.55f
        for (i in 0 until BUBBLES) {
            val k = (q - i * 0.08f).coerceIn(0f, 1f)
            if (k <= 0f) continue
            val bx = 118f - 10f * i - 8f * k + 3f * sin(TAU * (k + i * 0.2f))
            val by = 66f - 44f * k - 4f * i
            paint.alpha = (200 * (1f - k)).toInt().coerceIn(0, 255)
            cv.drawCircle(bx, by, 2.2f + 1.4f * (i % 3) * (1f - 0.4f * k), paint)
        }
    }

    private const val BUBBLES = 5
    private val DASH_AT = floatArrayOf(0f, 0.2f, 0.5f, 1f)
    private val DASH_X = floatArrayOf(0f, -10f, 28f, 0f)
    private val BITE_AT = floatArrayOf(0f, 0.5f, 0.56f, 0.66f, 1f)
    private val BITE_SY = floatArrayOf(1f, 1f, 0.9f, 1.06f, 1f)
}
