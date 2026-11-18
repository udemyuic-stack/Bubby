package com.example.buddy.ui.home

import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Shader
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

class SpaceFx {
    private val kind = IntArray(CAP)
    private val born = LongArray(CAP) { FREE }
    private val dur = FloatArray(CAP)
    private val x = FloatArray(CAP)
    private val y = FloatArray(CAP)
    private val tx = FloatArray(CAP)
    private val ty = FloatArray(CAP)
    private val size = FloatArray(CAP)
    private val layer = IntArray(CAP)
    private var next = 0
    private val rng = Random.Default
    private val shard = Path().apply {
        moveTo(0f, -1f)
        lineTo(0.7f, 0.8f)
        lineTo(-0.7f, 0.8f)
        close()
    }
    private val boomPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader = RadialGradient(
            0f, 0f, 1f,
            intArrayOf(0xFFFFFBE0.toInt(), 0xFFFFD04A.toInt(), 0xFFFF6A1A.toInt(), 0x00FF6A1A),
            floatArrayOf(0f, 0.25f, 0.55f, 0.72f), Shader.TileMode.CLAMP
        )
    }
    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = RING_STROKE
    }
    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader = RadialGradient(0f, 0f, 1f, intArrayOf(0xFFFF9A3C.toInt(), 0x00FF9A3C), floatArrayOf(0f, 1f), Shader.TileMode.CLAMP)
    }
    private val shardPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFFFFFF.toInt() }

    fun clear() { born.fill(FREE) }

    private fun add(l: Int, k: Int, ms: Long, px: Float, py: Float, qx: Float, qy: Float, s: Float, d: Float) {
        val i = next
        next = (next + 1) % CAP
        kind[i] = k
        layer[i] = l
        born[i] = ms
        x[i] = px
        y[i] = py
        tx[i] = qx
        ty[i] = qy
        size[i] = s
        dur[i] = d
    }

    fun boom(l: Int, ms: Long, px: Float, py: Float, s: Float) {
        add(l, BOOM, ms, px, py, 0f, 0f, s, BOOM_MS)
        add(l, RING, ms, px, py, 0f, 0f, s, RING_MS)
    }

    fun debris(l: Int, ms: Long, px: Float, py: Float, count: Int, reach: Float) {
        for (i in 0 until count) {
            val a = rng.nextFloat() * 2f * PI.toFloat()
            val d = reach * (0.5f + rng.nextFloat() * 0.8f)
            add(l, DEBRIS, ms, px, py, cos(a) * d, sin(a) * d + DEBRIS_FALL, 1f, 600f + rng.nextFloat() * 500f)
        }
    }

    fun shards(l: Int, ms: Long, px: Float, py: Float) {
        for (i in 0 until SHARDS) {
            val a = rng.nextFloat() * 2f * PI.toFloat()
            val d = 14f + rng.nextFloat() * 22f
            add(l, SHARD, ms, px, py, cos(a) * d, sin(a) * d + 10f, 2.5f + rng.nextFloat() * 2f, 700f + rng.nextFloat() * 500f)
        }
    }

    fun implode(l: Int, ms: Long, cx: Float, cy: Float, er: Float, count: Int, spread: Float) {
        for (i in 0 until count) {
            val a = rng.nextFloat() * 2f * PI.toFloat()
            val d = er * (1.4f + rng.nextFloat() * 2.6f)
            add(l, IMPLODE, ms + (rng.nextFloat() * spread).toLong(), cx + cos(a) * d, cy + sin(a) * d, cx, cy, 1.5f + rng.nextFloat() * 1.5f, 700f + rng.nextFloat() * 300f)
        }
    }

    fun draw(c: Canvas, ms: Long, l: Int) {
        for (i in 0 until CAP) {
            if (born[i] == FREE || layer[i] != l) continue
            val age = ms - born[i]
            if (age < 0) continue
            val p = age / dur[i]
            if (p >= 1f) { born[i] = FREE; continue }
            when (kind[i]) {
                BOOM -> drawBoom(c, i, p)
                RING -> drawRing(c, i, p)
                DEBRIS -> drawDebris(c, i, p)
                SHARD -> drawShard(c, i, p)
                IMPLODE -> drawImplode(c, i, p)
            }
        }
    }

    private fun drawBoom(c: Canvas, i: Int, p: Float) {
        val s = if (p < 0.35f) Ease.lerp(0.1f, 1f, p / 0.35f) else Ease.lerp(1f, 1.4f, (p - 0.35f) / 0.65f)
        val a = if (p < 0.35f) 1f else 1f - (p - 0.35f) / 0.65f
        boomPaint.alpha = (a * 255f).toInt()
        c.save()
        c.translate(x[i], y[i])
        c.scale(size[i] * 0.75f * s, size[i] * 0.75f * s)
        c.drawCircle(0f, 0f, 1f, boomPaint)
        c.restore()
    }

    private fun drawRing(c: Canvas, i: Int, p: Float) {
        val e = sin(p * PI.toFloat() / 2f)
        ringPaint.color = 0xFFFFE6B4.toInt()
        ringPaint.alpha = ((1f - p) * 0.9f * 255f).toInt()
        c.drawCircle(x[i], y[i], size[i] * 0.5f * Ease.lerp(0.2f, 2.2f, e), ringPaint)
    }

    private fun drawDebris(c: Canvas, i: Int, p: Float) {
        val e = sin(p * PI.toFloat() / 2f)
        val px = x[i] + tx[i] * e
        val py = y[i] + ty[i] * e
        glowPaint.alpha = ((1f - p) * 110f).toInt()
        c.save()
        c.translate(px, py)
        c.scale(5f, 5f)
        c.drawCircle(0f, 0f, 1f, glowPaint)
        c.restore()
        dotPaint.color = 0xFF5A3A24.toInt()
        dotPaint.alpha = ((1f - p) * 255f).toInt()
        c.drawCircle(px, py, 2.5f, dotPaint)
    }

    private fun drawShard(c: Canvas, i: Int, p: Float) {
        val e = sin(p * PI.toFloat() / 2f)
        shardPaint.alpha = ((1f - p) * 255f).toInt()
        c.save()
        c.translate(x[i] + tx[i] * e, y[i] + ty[i] * e)
        c.rotate(p * 240f)
        c.scale(size[i], size[i])
        c.drawPath(shard, shardPaint)
        c.restore()
    }

    private fun drawImplode(c: Canvas, i: Int, p: Float) {
        val e = p * p
        dotPaint.color = 0xFFFFC27A.toInt()
        dotPaint.alpha = (minOf(1f, p * 5f) * (1f - p * 0.4f) * 255f).toInt()
        c.drawCircle(Ease.lerp(x[i], tx[i], e), Ease.lerp(y[i], ty[i], e), size[i], dotPaint)
    }

    companion object {
        const val EARTH = 0
        const val PETAL = 1
        private const val CAP = 192
        private const val FREE = Long.MIN_VALUE
        private const val BOOM = 0
        private const val RING = 1
        private const val DEBRIS = 2
        private const val SHARD = 3
        private const val IMPLODE = 4
        private const val BOOM_MS = 650f
        private const val RING_MS = 700f
        private const val RING_STROKE = 3f
        private const val DEBRIS_FALL = 20f
        private const val SHARDS = 6
    }
}
