package com.example.buddy.ui.home

import kotlin.math.exp
import kotlin.math.sin

object ForestStorm {
    const val WINDOW_MS = 2600L
    private const val HOLD_MS = 4200L
    private const val GLOW_MS = 1800L

    @Volatile var level = 0f
        private set
    @Volatile var glow = 0f
        private set

    private var target = 0f
    private var taps = 0
    private var lastTapMs = Long.MIN_VALUE
    private var holdUntilMs = Long.MIN_VALUE
    private var glowStartMs = Long.MIN_VALUE

    fun tap(nowMs: Long, showMs: Long) {
        taps = if (nowMs - lastTapMs < WINDOW_MS) taps + 1 else 1
        lastTapMs = nowMs
        when (taps) {
            1 -> { target = 0.45f; holdUntilMs = nowMs + HOLD_MS }
            2 -> { target = 0.8f; holdUntilMs = nowMs + HOLD_MS; glowStartMs = nowMs }
            else -> { target = 1f; holdUntilMs = nowMs + showMs; taps = 0 }
        }
    }

    fun step(dt: Float, nowMs: Long) {
        if (nowMs > holdUntilMs) target = 0f
        val k = if (target > level) RISE else FALL
        level += (target - level) * (1f - exp(-dt * k))
        if (level < 0.002f && target == 0f) level = 0f
        val age = nowMs - glowStartMs
        glow = if (age < 0 || age >= GLOW_MS) 0f else {
            val p = age / GLOW_MS.toFloat()
            (sin(p * 6.2832f * 2.5f) * 0.5f + 0.5f) * (1f - p) * 0.9f
        }
    }

    fun reset() { level = 0f; glow = 0f; target = 0f; taps = 0; holdUntilMs = Long.MIN_VALUE }

    private const val RISE = 1.6f
    private const val FALL = 0.45f
}
