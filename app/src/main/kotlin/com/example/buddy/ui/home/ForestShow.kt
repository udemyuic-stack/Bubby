package com.example.buddy.ui.home

import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import kotlin.random.Random

class ForestShow : Show() {
    private val rng = Random.Default
    private val bolt = ForestBolt()
    private val fire = ForestFire()
    private val flashMatrix = Matrix()
    private val flashPaint = Paint().apply {
        shader = RadialGradient(0f, 0f, 1f, intArrayOf(0xFFFFFFFF.toInt(), 0x66FFFFFF, 0x00FFFFFF), floatArrayOf(0f, 0.6f, 1f), Shader.TileMode.CLAMP)
    }
    private var tree = 0
    private var width = 0f
    private var height = 0f

    override val durationMs = DURATION_MS

    fun resize(w: Float, h: Float) {
        width = w
        height = h
        flashMatrix.setScale(w * FLASH_RX, h * FLASH_RY)
        flashMatrix.postTranslate(w * FLASH_X, h * FLASH_Y)
        flashPaint.shader.setLocalMatrix(flashMatrix)
    }

    fun zap(): Float {
        val e = elapsedMs - ANTICIPATE_MS
        if (!running || e < 0 || e >= ZAP_MS) return 0f
        return Ease.keys(e / ZAP_MS.toFloat(), ZAP_AT, ZAP_X)
    }

    fun boltLevel(): Float {
        val e = elapsedMs - ANTICIPATE_MS
        if (!running || e < 0 || e >= BOLT_GLOW_MS) return 0f
        return Ease.keys(e / BOLT_GLOW_MS.toFloat(), GLOW_AT, GLOW_VALUE)
    }

    override fun onStart() {
        ForestTrees.reset()
        tree = rng.nextInt(ForestTrees.COUNT)
        val x = ForestTrees.x(tree)
        bolt.build(x + (rng.nextFloat() * 2f - 1f) * BOLT_SPREAD, x, ForestTrees.top[tree])
    }

    override fun onCancel() = ForestTrees.reset()

    override fun onDraw(c: Canvas, scale: Float, rawMs: Long) {
        val ms = rawMs - ANTICIPATE_MS
        if (ms < 0) return
        advanceTree(ms)
        val seconds = HomeClock.seconds()
        c.save()
        ForestTrees.place(c)
        fire.draw(c, tree, seconds, ramp(ms, BURN_AT, FIRE_FADE_MS) * (1f - ramp(ms, GONE_AT, FIRE_FADE_MS)), ramp(ms, BURN_AT, FIRE_FADE_MS) * (1f - ramp(ms, GROW_AT, FIRE_FADE_MS)))
        bolt.draw(c, bolt.level(ms))
        c.restore()
        drawFlash(c, ms)
    }

    private fun drawFlash(c: Canvas, ms: Long) {
        if (ms >= FLASH_MS) return
        val level = Ease.keys(ms / FLASH_MS.toFloat(), FLASH_AT, FLASH_VALUE)
        if (level <= 0.004f) return
        flashPaint.alpha = (level * MAX_ALPHA).toInt().coerceIn(0, MAX_ALPHA.toInt())
        c.drawRect(0f, 0f, width, height, flashPaint)
    }

    private fun advanceTree(ms: Long) {
        if (ms < GROW_AT) {
            ForestTrees.burn[tree] = ramp(ms, BURN_AT, FILL_MS)
            if (ms >= GONE_AT) {
                val p = ramp(ms, GONE_AT, GONE_MS)
                val eased = p * p
                ForestTrees.scaleX[tree] = Ease.lerp(1f, GONE_SX, eased)
                ForestTrees.scaleY[tree] = Ease.lerp(1f, GONE_SY, eased)
                ForestTrees.opacity[tree] = 1f - eased
            }
            return
        }
        val p = ramp(ms, GROW_AT, GROW_MS)
        ForestTrees.burn[tree] = 1f - ramp(ms, GROW_AT, FILL_MS)
        ForestTrees.scaleX[tree] = Ease.keys(p, GROW_KEYS, GROW_SX)
        ForestTrees.scaleY[tree] = Ease.keys(p, GROW_KEYS, GROW_SY)
        ForestTrees.opacity[tree] = (p / GROW_FADE).coerceAtMost(1f)
    }

    private fun ramp(ms: Long, at: Long, span: Long) = ((ms - at) / span.toFloat()).coerceIn(0f, 1f)

    companion object {
        const val ANTICIPATE_MS = 380L
        const val DURATION_MS = 6500L + ANTICIPATE_MS
        const val BURN_AT = 140L
        const val GONE_AT = 3140L
        const val GROW_AT = 3800L
        const val FILL_MS = 1400L
        const val FIRE_FADE_MS = 400L
        const val GONE_MS = 600L
        const val GROW_MS = 2600L
        const val GROW_FADE = 0.12f
        const val GONE_SX = 0.7f
        const val GONE_SY = 0.15f
        const val BOLT_SPREAD = 70f
        const val BOLT_GLOW_MS = 500L
        const val ZAP_MS = 500L
        const val FLASH_MS = 600L
        const val FLASH_X = 0.6f
        const val FLASH_Y = 0.1f
        const val FLASH_RX = 0.8f
        const val FLASH_RY = 0.5f
        const val MAX_ALPHA = 255f
        val GROW_KEYS = floatArrayOf(0f, 0.55f, 1f)
        val GROW_SX = floatArrayOf(0.05f, 0.7f, 1f)
        val GROW_SY = floatArrayOf(0f, 0.8f, 1f)
        val ZAP_AT = floatArrayOf(0f, 0.2f, 0.4f, 0.6f, 0.8f, 1f)
        val ZAP_X = floatArrayOf(0f, -5f, 5f, -3f, 3f, 0f)
        val GLOW_AT = floatArrayOf(0f, 0.12f, 0.3f, 0.45f, 1f)
        val GLOW_VALUE = floatArrayOf(0f, 1f, 0.2f, 1f, 0f)
        val FLASH_AT = floatArrayOf(0f, 0.1f, 0.25f, 0.4f, 1f)
        val FLASH_VALUE = floatArrayOf(0f, 0.55f, 0.1f, 0.45f, 0f)
    }
}
