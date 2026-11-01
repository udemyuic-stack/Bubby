package com.example.buddy.ui.home

import android.graphics.Canvas
import android.graphics.Paint
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

class Branch(private val capacity: Int = 220) {
    private val seg = FloatArray(capacity * 5)
    private val out = FloatArray(capacity * 4)
    var count = 0
        private set

    fun clear() {
        count = 0
    }

    fun build(seed: Int, ox: Float, oy: Float, rays: Int, length: Float, baseAngle: Float = 0f, spreadRad: Float = (2.0 * Math.PI).toFloat()) {
        val rng = Random(seed)
        count = 0
        for (r in 0 until rays) {
            val a = baseAngle + spreadRad * (r + rng.nextFloat() * 0.6f) / rays
            grow(rng, ox, oy, a, length * (0.7f + rng.nextFloat() * 0.5f), 0f, 0)
        }
    }

    private fun grow(rng: Random, x0: Float, y0: Float, angle: Float, length: Float, birth: Float, depth: Int) {
        var x = x0
        var y = y0
        var a = angle
        var b = birth
        val steps = (length / STEP).toInt().coerceAtLeast(2)
        for (i in 0 until steps) {
            if (count >= capacity) return
            a += (rng.nextFloat() - 0.5f) * JITTER
            val nx = x + cos(a) * STEP
            val ny = y + sin(a) * STEP
            val nb = (b + STEP / (length * (1f + depth * 0.6f)) * (1f - birth * 0.2f) * 0.9f).coerceAtMost(1f)
            val o = count * 5
            seg[o] = x
            seg[o + 1] = y
            seg[o + 2] = nx
            seg[o + 3] = ny
            seg[o + 4] = b
            count++
            if (depth < MAX_DEPTH && i > 0 && rng.nextFloat() < FORK_CHANCE) {
                val side = if (rng.nextBoolean()) 1f else -1f
                grow(rng, nx, ny, a + side * (0.5f + rng.nextFloat() * 0.5f), length * (0.35f + rng.nextFloat() * 0.25f), nb, depth + 1)
            }
            x = nx
            y = ny
            b = nb
        }
    }

    fun point(i: Int, o: Int): Float = seg[(i % count.coerceAtLeast(1)) * 5 + 2 + o]

    fun draw(c: Canvas, paint: Paint, reveal: Float, limit: Float = 1f) {
        if (count == 0 || reveal <= 0f) return
        var n = 0
        for (i in 0 until count) {
            val o = i * 5
            val birth = seg[o + 4]
            if (birth > limit) continue
            val f = ((reveal - birth) / SPAN).coerceIn(0f, 1f)
            if (f <= 0f) continue
            out[n++] = seg[o]
            out[n++] = seg[o + 1]
            out[n++] = seg[o] + (seg[o + 2] - seg[o]) * f
            out[n++] = seg[o + 1] + (seg[o + 3] - seg[o + 1]) * f
        }
        if (n > 0) c.drawLines(out, 0, n, paint)
    }

    private companion object {
        const val STEP = 14f
        const val JITTER = 0.7f
        const val FORK_CHANCE = 0.16f
        const val MAX_DEPTH = 2
        const val SPAN = 0.12f
    }
}
