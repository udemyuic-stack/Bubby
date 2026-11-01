package com.example.buddy.ui.home

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Shader
import kotlin.math.sin
import kotlin.random.Random

class IceShow : Show() {
    private val cracks = Branch()
    private val glow = line(7f, 0x5535D6FF)
    private val core = line(2.2f, 0xFFE9FBFF.toInt())
    private val shardPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFBFEFFF.toInt() }
    private val shardEdge = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 1.4f; color = 0xFFFFFFFF.toInt() }
    private val glint = line(1.6f, 0xFFFFFFFF.toInt())
    private val shard = Path().apply { moveTo(0f, -9f); lineTo(6f, 5f); lineTo(-5f, 7f); close() }
    private val mist = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader = RadialGradient(0f, 0f, 1f, intArrayOf(0x66D8F6FF, 0x00D8F6FF), floatArrayOf(0f, 1f), Shader.TileMode.CLAMP)
    }
    private val sx = FloatArray(SHARDS)
    private val sy = FloatArray(SHARDS)
    private val vx = FloatArray(SHARDS)
    private val vy = FloatArray(SHARDS)
    private val spin = FloatArray(SHARDS)
    private val delay = FloatArray(SHARDS)
    private val size = FloatArray(SHARDS)
    private val mx = FloatArray(MISTS)
    private val mph = FloatArray(MISTS)
    private val msz = FloatArray(MISTS)

    override val durationMs = DURATION_MS

    private fun line(w: Float, col: Int) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = w
        strokeCap = Paint.Cap.ROUND
        color = col
    }

    override fun onStart() {
        val rng = Random(System.nanoTime())
        cracks.build(rng.nextInt(), 0f, 0f, RAYS, CRACK_LEN)
        for (i in 0 until SHARDS) {
            val k = rng.nextInt(cracks.count.coerceAtLeast(1))
            sx[i] = cracks.point(k, 0)
            sy[i] = cracks.point(k, 1)
            vx[i] = (rng.nextFloat() - 0.5f) * 90f
            vy[i] = -30f - rng.nextFloat() * 50f
            spin[i] = (rng.nextFloat() - 0.5f) * 540f
            delay[i] = 0.12f + rng.nextFloat() * 0.3f
            size[i] = 0.7f + rng.nextFloat() * 0.9f
        }
        for (i in 0 until MISTS) {
            mx[i] = (rng.nextFloat() - 0.5f) * 260f
            mph[i] = rng.nextFloat() * 6.28f
            msz[i] = 45f + rng.nextFloat() * 45f
        }
    }

    override fun onDraw(c: Canvas, scale: Float, ms: Long) {
        val p = ms / DURATION_MS.toFloat()
        val grow = Ease.smooth((p / GROW_END).coerceIn(0f, 1f))
        val heal = Ease.smooth(((p - HEAL_AT) / (1f - HEAL_AT)).coerceIn(0f, 1f))
        val limit = 1f - heal
        c.save()
        c.translate(originX, originY)
        c.scale(scale, scale)
        val fade = 1f - Ease.smooth(((p - 0.8f) / 0.2f).coerceIn(0f, 1f))
        glow.alpha = (0x55 * fade).toInt()
        core.alpha = (255 * fade).toInt()
        cracks.draw(c, glow, grow, limit)
        cracks.draw(c, core, grow, limit)
        drawGlints(c, p, grow, limit)
        drawShards(c, p)
        drawMist(c, p)
        c.restore()
    }

    private fun drawGlints(c: Canvas, p: Float, grow: Float, limit: Float) {
        if (grow < 0.5f) return
        for (i in 0 until GLINTS) {
            val k = i * 7 + 3
            val tw = sin(p * 40f + i * 1.7f)
            if (tw < 0.3f) continue
            val gx = cracks.point(k, 0)
            val gy = cracks.point(k, 1)
            val s = 7f * tw * limit
            glint.alpha = (255 * tw * limit).toInt().coerceIn(0, 255)
            c.drawLine(gx - s, gy, gx + s, gy, glint)
            c.drawLine(gx, gy - s, gx, gy + s, glint)
        }
    }

    private fun drawShards(c: Canvas, p: Float) {
        for (i in 0 until SHARDS) {
            val t = ((p - delay[i]) / (SHARD_LIFE)).coerceIn(0f, 1f)
            if (t <= 0f || t >= 1f) continue
            val secs = t * SHARD_SECS
            val x = sx[i] + vx[i] * secs
            val y = sy[i] + vy[i] * secs + 0.5f * GRAVITY * secs * secs
            val a = (255 * (1f - t * t)).toInt()
            shardPaint.alpha = a
            shardEdge.alpha = a
            c.save()
            c.translate(x, y)
            c.rotate(spin[i] * secs)
            c.scale(size[i], size[i])
            c.drawPath(shard, shardPaint)
            c.drawPath(shard, shardEdge)
            c.restore()
        }
    }

    private fun drawMist(c: Canvas, p: Float) {
        val a = Ease.hump(p)
        if (a <= 0.02f) return
        mist.alpha = (255 * a).toInt().coerceIn(0, 255)
        for (i in 0 until MISTS) {
            val x = mx[i] + sin(p * 3f + mph[i]) * 22f
            val y = MIST_Y - (i % 3) * 8f
            c.save()
            c.translate(x, y)
            c.scale(msz[i] * (1f + 0.3f * p), msz[i] * 0.4f)
            c.drawCircle(0f, 0f, 1f, mist)
            c.restore()
        }
    }

    companion object {
        const val DURATION_MS = 2400L
        private const val SHARDS = 14
        private const val MISTS = 6
        private const val GLINTS = 9
        private const val RAYS = 6
        private const val CRACK_LEN = 150f
        private const val GROW_END = 0.38f
        private const val HEAL_AT = 0.62f
        private const val SHARD_LIFE = 0.45f
        private const val SHARD_SECS = 1.1f
        private const val GRAVITY = 420f
        private const val MIST_Y = 70f
    }
}
