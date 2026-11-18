package com.example.buddy.ui.home

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Shader
import kotlin.random.Random

class ForestBolt {
    private val rng = Random.Default
    private val trunk = Path()
    private val branches = Path()
    private val core = strokePaint(CORE_WIDTH)
    private val twig = strokePaint(BRANCH_WIDTH)
    private val stamps = FloatArray(MAX_STAMPS * 2)
    private var stampCount = 0
    private val halo = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader = RadialGradient(0f, 0f, HALO_RADIUS, intArrayOf(HALO_CORE, HALO_EDGE), floatArrayOf(0f, 1f), Shader.TileMode.CLAMP)
    }

    fun build(startX: Float, endX: Float, endY: Float) {
        trunk.rewind()
        branches.rewind()
        stampCount = 0
        var x = startX
        var y = START_Y
        trunk.moveTo(x, y)
        var prevX = x
        var prevY = y
        for (i in 1..SEGMENTS) {
            y = START_Y + (endY - START_Y) * i / SEGMENTS
            x = startX + (endX - startX) * i / SEGMENTS + if (i < SEGMENTS) spread(JITTER) else 0f
            trunk.lineTo(x, y)
            stamp(prevX, prevY, x, y)
            prevX = x
            prevY = y
            if (i > 1 && i < SEGMENTS - 1 && rng.nextFloat() < BRANCH_CHANCE) fork(x, y, if (rng.nextBoolean()) 1f else -1f)
        }
    }

    private fun fork(x0: Float, y0: Float, dir: Float) {
        var x = x0
        var y = y0
        branches.moveTo(x, y)
        for (k in 1..FORK_SEGMENTS) {
            x += dir * (BRANCH_SPREAD / FORK_SEGMENTS) + spread(FORK_JITTER)
            y += BRANCH_DROP_MIN + rng.nextFloat() * (BRANCH_DROP_MAX - BRANCH_DROP_MIN) / FORK_SEGMENTS
            branches.lineTo(x, y)
        }
    }

    fun level(ms: Long): Float {
        if (ms >= DURATION_MS) return 0f
        return Ease.keys(ms / DURATION_MS.toFloat(), LEVEL_AT, LEVEL_VALUE)
    }

    fun draw(c: Canvas, level: Float) {
        if (level <= 0f) return
        halo.alpha = (level * MAX_ALPHA).toInt()
        for (i in 0 until stampCount) {
            c.save()
            c.translate(stamps[i * 2], stamps[i * 2 + 1])
            c.drawCircle(0f, 0f, HALO_RADIUS, halo)
            c.restore()
        }
        core.alpha = (level * MAX_ALPHA).toInt()
        c.drawPath(trunk, core)
        twig.alpha = core.alpha
        c.drawPath(branches, twig)
    }

    private fun stamp(x0: Float, y0: Float, x1: Float, y1: Float) {
        val steps = (kotlin.math.hypot(x1 - x0, y1 - y0) / STAMP_GAP).toInt().coerceAtLeast(1)
        for (k in 0 until steps) {
            if (stampCount >= MAX_STAMPS) return
            val p = k / steps.toFloat()
            stamps[stampCount * 2] = x0 + (x1 - x0) * p
            stamps[stampCount * 2 + 1] = y0 + (y1 - y0) * p
            stampCount++
        }
    }

    private fun spread(range: Float) = (rng.nextFloat() * 2f - 1f) * range

    private fun strokePaint(width: Float) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = width
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        color = BOLT_COLOR
    }

    companion object {
        const val DURATION_MS = 750L
        private const val SEGMENTS = 11
        private const val START_Y = -20f
        private const val FORK_SEGMENTS = 3
        private const val FORK_JITTER = 9f
        private const val JITTER = 22f
        private const val BRANCH_CHANCE = 0.55f
        private const val BRANCH_SPREAD = 70f
        private const val BRANCH_DROP_MIN = 12f
        private const val BRANCH_DROP_MAX = 70f
        private const val CORE_WIDTH = 3.2f
        private const val BRANCH_WIDTH = 1.6f
        private const val HALO_RADIUS = 14f
        private const val HALO_CORE = 0x66FFFBE0
        private const val HALO_EDGE = 0x00FFFBE0
        private const val STAMP_GAP = 8f
        private const val MAX_STAMPS = 160
        private const val MAX_ALPHA = 255f
        private const val BOLT_COLOR = 0xFFFFFBE0.toInt()
        private val LEVEL_AT = floatArrayOf(0f, 0.08f, 0.25f, 0.4f, 1f)
        private val LEVEL_VALUE = floatArrayOf(0f, 1f, 0.15f, 1f, 0f)
    }
}
