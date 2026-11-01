package com.example.buddy.ui.home

import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

class SpaceMeteors(private val petals: List<SpacePetal>, private val fx: SpaceFx) {
    private val rng = Random.Default
    private val x0 = FloatArray(CAP)
    private val y0 = FloatArray(CAP)
    private val x1 = FloatArray(CAP)
    private val y1 = FloatArray(CAP)
    private val size = FloatArray(CAP)
    private val delay = LongArray(CAP)
    private val dur = FloatArray(CAP)
    private val target = IntArray(CAP)
    private val lx = FloatArray(CAP)
    private val ly = FloatArray(CAP)
    private val live = BooleanArray(CAP)
    private val stuck = BooleanArray(CAP)
    private val done = BooleanArray(CAP)
    private var count = 0

    private val tailPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader = LinearGradient(
            -1f, 0f, 0f, 0f,
            intArrayOf(0x00FF8C32, 0xBFFF8C32.toInt(), 0xFFFFF3C0.toInt()),
            floatArrayOf(0f, 0.7f, 1f), Shader.TileMode.CLAMP
        )
    }
    private val rockPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader = RadialGradient(
            -0.3f, -0.3f, 1.2f,
            intArrayOf(0xFFB08A64.toInt(), 0x994F3A2A.toInt(), 0xFF22160E.toInt()),
            floatArrayOf(0f, 0.6f, 1f), Shader.TileMode.CLAMP
        )
    }
    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader = RadialGradient(0f, 0f, 1f, intArrayOf(0x99FF9A3C.toInt(), 0x00FF9A3C), floatArrayOf(0f, 1f), Shader.TileMode.CLAMP)
    }

    private fun r(a: Float, b: Float) = a + rng.nextFloat() * (b - a)

    private fun add(delayMs: Long, durMs: Float, s: Float, tgt: Int, px: Float, py: Float, lxv: Float, lyv: Float) {
        if (count >= CAP) return
        val i = count++
        val rel = tgt == EARTH
        val ref = if (rel) EARTH_REF_X + px else px
        val side = if (ref < CENTER) -1f else 1f
        val a = r(-0.5f, 0.5f) + (if (ref < CENTER) 0.35f else -0.35f)
        val d = r(520f, 700f)
        x1[i] = px
        y1[i] = py
        x0[i] = px - sin(a) * d * side * 0.5f - side * r(80f, 200f)
        y0[i] = py - d
        size[i] = s
        delay[i] = delayMs
        dur[i] = durMs
        target[i] = tgt
        lx[i] = lxv
        ly[i] = lyv
        stuck[i] = rng.nextBoolean()
        done[i] = false
        live[i] = rel
    }

    fun build(er: Float) {
        count = 0
        for (i in 0 until EARTH_HITS) {
            val a = r(0f, 6.2832f)
            val d = er * 0.6f * Math.sqrt(rng.nextDouble()).toFloat()
            add((i * 170 + rng.nextInt(80)).toLong(), r(650f, 850f), r(7f, 11f), EARTH, cos(a) * d, sin(a) * d, 0f, 0f)
        }
        val order = petals.indices.shuffled(rng)
        for ((n, pi) in order.withIndex()) {
            val sp = petals[pi].spec
            val hits = if (rng.nextBoolean()) 1 else 2
            for (q in 0 until hits) {
                val lxv = r(-0.24f, 0.24f) * sp.width
                val lyv = r(-0.24f, 0.2f) * sp.height
                val rad = Math.toRadians(sp.rotation.toDouble())
                val cx = sp.left + sp.width / 2f
                val cy = sp.top + sp.height / 2f
                val wx = cx + (lxv * Math.cos(rad) - lyv * Math.sin(rad)).toFloat()
                val wy = cy + (lxv * Math.sin(rad) + lyv * Math.cos(rad)).toFloat()
                add((200 + n * 150 + q * 260 + rng.nextInt(90)).toLong(), r(600f, 800f), r(7f, 10f), pi, wx, wy, lxv, lyv)
            }
        }
        for (i in 0 until EXTRA) {
            add(rng.nextInt(1500).toLong(), r(700f, 900f), r(4f, 8f), NONE, r(20f, 370f), r(560f, 900f), 0f, 0f)
        }
        java.util.Arrays.fill(done, false)
    }

    fun stop() { count = 0 }

    fun draw(c: Canvas, ms: Long, startAbs: Long, ex: Float, ey: Float, earthLayer: Boolean, onEarthHit: (Float, Float) -> Unit) {
        for (i in 0 until count) {
            if (live[i] != earthLayer) continue
            val ox = if (live[i]) ex else 0f
            val oy = if (live[i]) ey else 0f
            val age = ms - delay[i]
            if (age < 0 || done[i]) continue
            val p = age / dur[i]
            if (p >= 1f) {
                done[i] = true
                impact(i, ms, startAbs, ox, oy, onEarthHit)
                continue
            }
            val e = p * p
            val px = ox + Ease.lerp(x0[i], x1[i], e)
            val py = oy + Ease.lerp(y0[i], y1[i], e)
            val s = size[i]
            c.save()
            c.translate(px, py)
            c.rotate(Math.toDegrees(atan2((y1[i] - y0[i]).toDouble(), (x1[i] - x0[i]).toDouble())).toFloat())
            val alpha = (minOf(1f, p / 0.1f) * 255f).toInt()
            tailPaint.alpha = alpha
            rockPaint.alpha = alpha
            glowPaint.alpha = alpha
            c.save()
            c.scale(s * 7f, s * 0.75f)
            c.drawRect(-1f, -0.5f, 0f, 0.5f, tailPaint)
            c.restore()
            c.save()
            c.scale(s * 1.1f, s * 1.1f)
            c.drawCircle(0f, 0f, 1f, glowPaint)
            c.restore()
            c.save()
            c.scale(s * 0.5f, s * 0.5f)
            c.drawCircle(0f, 0f, 1f, rockPaint)
            c.restore()
            c.restore()
        }
    }

    private fun impact(i: Int, ms: Long, startAbs: Long, ox: Float, oy: Float, onEarthHit: (Float, Float) -> Unit) {
        val px = ox + x1[i]
        val py = oy + y1[i]
        when (val t = target[i]) {
            NONE -> Unit
            EARTH -> {
                fx.boom(SpaceFx.EARTH, ms, px, py, r(16f, 26f))
                fx.debris(SpaceFx.EARTH, ms, px, py, 6, 40f)
                onEarthHit(px, py)
            }
            else -> {
                val rad = r(7f, 10.5f)
                fx.boom(SpaceFx.PETAL, ms, px, py, r(16f, 26f))
                fx.debris(SpaceFx.PETAL, ms, px, py, 6, 40f)
                fx.shards(SpaceFx.PETAL, ms, px, py)
                petals[t].craters.hit(lx[i], ly[i], rad, stuck[i], startAbs + ms)
            }
        }
    }

    private companion object {
        const val CAP = 40
        const val EARTH = -1
        const val NONE = -2
        const val EARTH_HITS = 8
        const val EXTRA = 10
        const val CENTER = 195f
        const val EARTH_REF_X = 84f
    }
}
