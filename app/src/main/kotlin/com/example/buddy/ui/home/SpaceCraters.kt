package com.example.buddy.ui.home

import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Shader
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

class SpaceCraters {
    private class Crater(val x: Float, val y: Float, val r: Float, val at: Long, val stuck: Boolean, rng: Random) {
        val hole = Path()
        val rock = Path()
        val cracks = FloatArray(MAX_SEGMENTS * 4)
        var segments = 0
        val shakeX = rng.nextFloat() * 2f * SHAKE_X - SHAKE_X
        val shakeY = SHAKE_Y_MIN + rng.nextFloat() * (SHAKE_Y_MAX - SHAKE_Y_MIN)
        val drift = rng.nextFloat() * 2f - 1f

        init {
            hole.addCircle(x, y, r, Path.Direction.CW)
            for (i in 0 until JAGS) {
                val a = i * JAG_STEP + (rng.nextFloat() * 2f - 1f) * JAG_JITTER
                val d = r * (0.7f + rng.nextFloat() * 0.35f)
                hole.addCircle(x + cos(a) * d, y + sin(a) * d, r * (0.35f + rng.nextFloat() * 0.2f), Path.Direction.CW)
            }
            val count = 6 + rng.nextInt(4)
            for (i in 0 until count) {
                var a = rng.nextFloat() * 2f * PI.toFloat()
                var px = x
                var py = y
                val steps = 3 + rng.nextInt(2)
                for (s in 0 until steps) {
                    if (segments >= MAX_SEGMENTS) break
                    a += (rng.nextFloat() - 0.5f) * 0.9f
                    val len = r * (0.7f + rng.nextFloat() * 0.9f)
                    val nx = px + cos(a) * len
                    val ny = py + sin(a) * len
                    add(px, py, nx, ny)
                    if (rng.nextFloat() < BRANCH && segments < MAX_SEGMENTS) {
                        val b = a + (if (rng.nextBoolean()) 0.8f else -0.8f)
                        add(nx, ny, nx + cos(b) * len * 0.7f, ny + sin(b) * len * 0.7f)
                    }
                    px = nx
                    py = ny
                }
            }
            val pts = 8
            for (i in 0 until pts) {
                val a = i * 2f * PI.toFloat() / pts
                val d = r * (0.7f + rng.nextFloat() * 0.35f)
                if (i == 0) rock.moveTo(x + cos(a) * d, y + sin(a) * d) else rock.lineTo(x + cos(a) * d, y + sin(a) * d)
            }
            rock.close()
        }

        private fun add(x0: Float, y0: Float, x1: Float, y1: Float) {
            val o = segments * 4
            cracks[o] = x0
            cracks[o + 1] = y0
            cracks[o + 2] = x1
            cracks[o + 3] = y1
            segments++
        }
    }

    private val list = ArrayList<Crater>()
    private val rng = Random.Default
    private val matrix = Matrix()
    private val scratch = Path()
    private var healAt = Long.MAX_VALUE
    private val scorch = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader = RadialGradient(0f, 0f, 1f, intArrayOf(SCORCH_CORE, SCORCH_MID, 0), floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP)
    }
    private val crackDark = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = CRACK_DARK
        strokeCap = Paint.Cap.ROUND
        color = CRACK_DARK_COLOR
    }
    private val crackLight = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = CRACK_LIGHT
        strokeCap = Paint.Cap.ROUND
        color = 0xF2FFFFFF.toInt()
    }
    private val ember = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = EMBER_STROKE
    }
    private val rockFill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = ROCK_COLOR }
    private val rockRim = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.2f
        color = ROCK_RIM
    }
    private val dimple = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = DIMPLE }
    private val smoke = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = SMOKE }

    var shakeX = 0f
        private set
    var shakeY = 0f
        private set

    val any get() = list.isNotEmpty()

    fun hit(x: Float, y: Float, r: Float, stuck: Boolean, at: Long) {
        if (list.size >= MAX_CRATERS) return
        list.add(Crater(x, y, r, at, stuck, rng))
    }

    fun heal(at: Long) { if (list.isNotEmpty()) healAt = at }

    fun clear() {
        list.clear()
        healAt = Long.MAX_VALUE
        shakeX = 0f
        shakeY = 0f
    }

    fun update(now: Long) {
        if (healAt != Long.MAX_VALUE && now - healAt > HEAL_END) clear()
        var dx = 0f
        var dy = 0f
        for (k in list) {
            val p = (now - k.at) / SHAKE_MS
            if (p < 0f || p >= 1f) continue
            val f = when {
                p < 0.35f -> p / 0.35f
                p < 0.7f -> 1f - (p - 0.35f) / 0.35f * 1.7f
                else -> -0.7f * (1f - (p - 0.7f) / 0.3f)
            }
            dx = k.shakeX * f
            dy = k.shakeY * f
        }
        shakeX = dx
        shakeY = dy
    }

    private fun holeScale(now: Long): Float {
        val t = now - healAt
        return if (healAt == Long.MAX_VALUE || t < HOLE_DELAY) 1f else 1f - Ease.smooth((t - HOLE_DELAY) / HOLE_SPAN)
    }

    fun clipHoles(c: Canvas, now: Long, unit: Float) {
        if (list.isEmpty()) return
        val h = holeScale(now)
        if (h <= 0.01f) return
        for (k in list) {
            if (now < k.at) continue
            matrix.setScale(unit, unit)
            matrix.preTranslate(k.x, k.y)
            matrix.preScale(h, h)
            matrix.preTranslate(-k.x, -k.y)
            k.hole.transform(matrix, scratch)
            c.clipOutPath(scratch)
        }
    }

    fun drawBase(c: Canvas, now: Long) {
        val h = holeScale(now)
        if (h <= 0.01f) return
        val heal = if (healAt == Long.MAX_VALUE) -1L else now - healAt
        for (k in list) {
            val age = now - k.at
            if (age < 0) continue
            c.save()
            c.translate(k.x, k.y)
            c.scale(k.r * SCORCH_REACH * h, k.r * SCORCH_REACH * h)
            scorch.alpha = (255 * h).toInt()
            c.drawCircle(0f, 0f, 1f, scorch)
            c.restore()
            val grow = (age / CRACK_MS).coerceAtMost(1f)
            val erase = if (heal < CRACK_DELAY) 0f else ((heal - CRACK_DELAY) / CRACK_SPAN).coerceIn(0f, 1f)
            val from = (erase * k.segments).toInt()
            val to = (grow * k.segments).toInt().coerceAtMost(k.segments)
            for (i in from until to) {
                val o = i * 4
                c.drawLine(k.cracks[o], k.cracks[o + 1], k.cracks[o + 2], k.cracks[o + 3], crackDark)
                c.drawLine(k.cracks[o], k.cracks[o + 1], k.cracks[o + 2], k.cracks[o + 3], crackLight)
            }
        }
    }

    fun drawTop(c: Canvas, now: Long, seconds: Float) {
        val h = holeScale(now)
        val heal = if (healAt == Long.MAX_VALUE) -1L else now - healAt
        for (k in list) {
            val age = now - k.at
            if (age < 0) continue
            if (h > 0.01f) {
                val cool = (age / EMBER_MS).coerceIn(0f, 1f)
                ember.color = mix(EMBER_HOT, EMBER_COLD, cool)
                ember.alpha = (255 * h).toInt()
                c.drawCircle(k.x, k.y, (k.r + 1f) * h, ember)
            }
            if (!k.stuck) continue
            val fly = if (heal < 0) 0f else (heal / ROCK_FLY).coerceIn(0f, 1f)
            if (fly >= 1f) continue
            c.save()
            c.translate(k.drift * ROCK_DRIFT * fly, -ROCK_RISE * fly * fly)
            rockFill.alpha = (255 * (1f - fly)).toInt()
            rockRim.alpha = rockFill.alpha
            c.drawPath(k.rock, rockFill)
            c.drawPath(k.rock, rockRim)
            dimple.alpha = (90 * (1f - fly)).toInt()
            c.drawCircle(k.x - k.r * 0.25f, k.y - k.r * 0.2f, k.r * 0.18f, dimple)
            c.drawCircle(k.x + k.r * 0.25f, k.y + k.r * 0.25f, k.r * 0.12f, dimple)
            c.restore()
            if (fly > 0f) continue
            for (i in 0 until PUFFS) {
                val q = ((seconds + i * 0.5f) % SMOKE_PERIOD) / SMOKE_PERIOD
                smoke.alpha = (110 * sin(PI.toFloat() * q)).toInt()
                c.drawCircle(k.x + (i - 1) * k.r * 0.35f, k.y - k.r * 0.4f - q * k.r * 2f, k.r * 0.5f * (0.6f + q * 0.8f), smoke)
            }
        }
    }

    private fun mix(a: Int, b: Int, p: Float): Int {
        val r = ((a shr 16 and 255) + ((b shr 16 and 255) - (a shr 16 and 255)) * p).toInt()
        val g = ((a shr 8 and 255) + ((b shr 8 and 255) - (a shr 8 and 255)) * p).toInt()
        val bl = ((a and 255) + ((b and 255) - (a and 255)) * p).toInt()
        return (0xFF shl 24) or (r shl 16) or (g shl 8) or bl
    }

    private companion object {
        const val MAX_CRATERS = 4
        const val MAX_SEGMENTS = 48
        const val JAGS = 6
        const val JAG_STEP = 1.047f
        const val JAG_JITTER = 0.3f
        const val BRANCH = 0.35f
        const val SHAKE_X = 6f
        const val SHAKE_Y_MIN = 3f
        const val SHAKE_Y_MAX = 7f
        const val SHAKE_MS = 320f
        const val SCORCH_REACH = 2.6f
        const val SCORCH_CORE = 0xD91A0F08.toInt()
        const val SCORCH_MID = 0x734A2410
        const val CRACK_MS = 260f
        const val CRACK_DELAY = 400L
        const val CRACK_SPAN = 1500f
        const val CRACK_DARK = 2.4f
        const val CRACK_LIGHT = 1.1f
        const val CRACK_DARK_COLOR = 0x8C1E143C.toInt()
        const val HOLE_DELAY = 500L
        const val HOLE_SPAN = 1600f
        const val HEAL_END = 2200L
        const val EMBER_MS = 2400f
        const val EMBER_STROKE = 2.2f
        const val EMBER_HOT = 0xFF8A2A
        const val EMBER_COLD = 0x3A2416
        const val ROCK_FLY = 800f
        const val ROCK_RISE = 90f
        const val ROCK_DRIFT = 50f
        const val ROCK_COLOR = 0xFF5A4030.toInt()
        const val ROCK_RIM = 0xFFFF8A2A.toInt()
        const val DIMPLE = 0xFF22160E.toInt()
        const val SMOKE = 0xFF6A6478.toInt()
        const val PUFFS = 3
        const val SMOKE_PERIOD = 1.6f
    }
}
