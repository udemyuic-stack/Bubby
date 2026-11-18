package com.example.buddy.ui.home

import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

class GhostFlight(
    private val delayMs: Long,
    private val durationMs: Long,
    private val offsets: FloatArray,
    private val xs: FloatArray,
    private val ys: FloatArray,
    private val scales: FloatArray,
    private val alphas: FloatArray,
    private val biteStartsMs: LongArray,
    private val biteMs: Long
) {
    var x = 0f
        private set
    var y = 0f
        private set
    var scale = 0f
        private set
    var alpha = 0f
        private set
    var mouthX = 1f
        private set
    var mouthY = 1f
        private set

    fun sample(elapsedMs: Long) {
        val t = ((elapsedMs - delayMs) / durationMs.toFloat()).coerceIn(0f, 1f)
        var i = 0
        while (i < offsets.size - 2 && t > offsets[i + 1]) i++
        val span = offsets[i + 1] - offsets[i]
        val p = if (span <= 0f) 1f else Ease.smooth((t - offsets[i]) / span)
        x = Ease.lerp(xs[i], xs[i + 1], p)
        y = Ease.lerp(ys[i], ys[i + 1], p)
        scale = Ease.lerp(scales[i], scales[i + 1], p)
        alpha = Ease.lerp(alphas[i], alphas[i + 1], p)
        sampleMouth(elapsedMs)
    }

    private fun sampleMouth(elapsedMs: Long) {
        mouthX = 1f
        mouthY = 1f
        for (j in biteStartsMs.indices) {
            val p = (elapsedMs - biteStartsMs[j]) / (biteMs * MOUTH_SHARE)
            if (p < 0f || p >= 1f) continue
            val seg = (p * MOUTH_SEGMENTS).toInt()
            val q = p * MOUTH_SEGMENTS - seg
            mouthX = Ease.lerp(MOUTH_X[seg], MOUTH_X[seg + 1], q)
            mouthY = Ease.lerp(MOUTH_Y[seg], MOUTH_Y[seg + 1], q)
        }
    }

    companion object {
        private const val MOUTH_SHARE = 0.8f
        private const val MOUTH_SEGMENTS = 3
        private const val LUNGE = 0.12f
        private const val LUNGE_SCALE = 1.18f
        private const val LUNGE_SHARE = 0.45f
        private const val EXIT_MS = 700L
        private const val DROP_Y = 30f
        private const val ENTRY_SCALE = 0.3f
        private const val EXIT_SCALE = 0.5f
        private const val ENTRY_SPREAD = 240f
        private const val EXIT_SPREAD = 200f
        private const val TOP_Y = -120f
        private const val BOTTOM_Y = 960f
        private const val EXIT_Y = -160f
        private const val LAST_OFFSET = 0.99f
        private val MOUTH_X = floatArrayOf(1f, 1.5f, 0.7f, 1f)
        private val MOUTH_Y = floatArrayOf(1f, 1.7f, 0.5f, 1f)

        fun plan(
            rng: Random,
            sites: FloatArray,
            centerX: Float,
            centerY: Float,
            rotationDeg: Float,
            unit: Float,
            delayMs: Long,
            approachMs: Long,
            biteMs: Long
        ): GhostFlight {
            val count = BitePlanner.BITES
            val frames = 2 + count * 2 + 1
            val offsets = FloatArray(frames)
            val xs = FloatArray(frames)
            val ys = FloatArray(frames)
            val scales = FloatArray(frames)
            val alphas = FloatArray(frames)
            val total = (approachMs + biteMs * count + EXIT_MS).toFloat()
            val angle = Math.toRadians(rotationDeg.toDouble())
            val cosA = cos(angle).toFloat()
            val sinA = sin(angle).toFloat()
            fun worldX(i: Int) = centerX + sites[2 * i] * cosA - sites[2 * i + 1] * sinA
            fun worldY(i: Int) = centerY + sites[2 * i] * sinA + sites[2 * i + 1] * cosA + DROP_Y * unit
            xs[0] = worldX(0) + (rng.nextFloat() * 2f - 1f) * ENTRY_SPREAD * unit
            ys[0] = (if (rng.nextBoolean()) TOP_Y else BOTTOM_Y) * unit
            scales[0] = ENTRY_SCALE
            var k = 1
            for (j in 0 until count) {
                val at = (approachMs + biteMs * j) / total
                val px = worldX(j)
                val py = worldY(j)
                offsets[k] = at; xs[k] = px; ys[k] = py; scales[k] = 1f; alphas[k] = 1f
                k++
                offsets[k] = min(at + biteMs * LUNGE_SHARE / total, LAST_OFFSET)
                xs[k] = px - (px - centerX) * LUNGE
                ys[k] = py - (py - centerY) * LUNGE
                scales[k] = LUNGE_SCALE; alphas[k] = 1f
                k++
            }
            offsets[k] = (approachMs + biteMs * count) / total
            xs[k] = worldX(count - 1); ys[k] = worldY(count - 1); scales[k] = 1f; alphas[k] = 1f
            k++
            offsets[k] = 1f
            xs[k] = worldX(count - 1) + (rng.nextFloat() * 2f - 1f) * EXIT_SPREAD * unit
            ys[k] = EXIT_Y * unit
            scales[k] = EXIT_SCALE
            val starts = LongArray(count) { delayMs + approachMs + biteMs * it + (biteMs * 0.4f).toLong() }
            return GhostFlight(delayMs, total.toLong(), offsets, xs, ys, scales, alphas, starts, biteMs)
        }
    }
}
