package com.example.buddy.ui

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

class DesertScene : ThemeScene() {
    override val ready = true

    private class Blob(val color: Int, val x: Float, val y: Float, val radius: Float, val period: Float, val phase: Float) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    }

    private val blobs = listOf(
        Blob(SUN, 0.8f, 0.15f, 0.55f, 28f, 0f),
        Blob(CLAY, 0.1f, 0.5f, 0.6f, 24f, 2.3f),
        Blob(TEAL, 0.7f, 0.85f, 0.55f, 31f, 4.1f)
    )
    private val dustX = FloatArray(DUST)
    private val dustY = FloatArray(DUST)
    private val dustSpeed = FloatArray(DUST)
    private val dustSize = FloatArray(DUST)
    private val dustPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = (DUST_ALPHA shl 24) or (SAND and 0x00FFFFFF) }
    private var built = false
    private var unit = 1f

    init {
        val rnd = kotlin.random.Random(SEED)
        for (i in 0 until DUST) {
            dustX[i] = rnd.nextFloat()
            dustY[i] = rnd.nextFloat()
            dustSpeed[i] = SPEED_MIN + rnd.nextFloat() * SPEED_SPAN
            dustSize[i] = SIZE_MIN + rnd.nextFloat() * SIZE_SPAN
        }
    }

    private val dr = kotlin.random.Random(33)
    private val NSTAR = 26
    private val sx = FloatArray(NSTAR) { dr.nextFloat() }
    private val sy = FloatArray(NSTAR) { dr.nextFloat() * 0.35f }
    private val sp = FloatArray(NSTAR) { 2f + dr.nextFloat() * 4f }
    private val NGR = 36
    private val gy = FloatArray(NGR) { dr.nextFloat() }
    private val gSpeed = FloatArray(NGR) { 0.04f + dr.nextFloat() * 0.07f }
    private val gOff = FloatArray(NGR) { dr.nextFloat() }
    private val gLen = FloatArray(NGR) { 6f + dr.nextFloat() * 16f }
    private val NBIRD = 4
    private val fillP = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val lineP = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND }
    private val dunePath = android.graphics.Path()
    private val birdPath = android.graphics.Path()
    private val sunPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stormPaint = Paint()

    private fun buildExtras(w: Float, h: Float) {
        sunPaint.shader = RadialGradient(0f, 0f, 0.42f * w, intArrayOf(0xB0FFE9A8.toInt(), 0x40FFC468, 0x00FFA64D), floatArrayOf(0f, 0.35f, 1f), Shader.TileMode.CLAMP)
        stormPaint.shader = android.graphics.LinearGradient(0f, 0f, w, 0f, intArrayOf(0x00E8B66A, 0x80E8B66A.toInt(), 0x00E8B66A), null, Shader.TileMode.CLAMP)
    }

    private fun dune(cv: Canvas, w: Float, h: Float, base: Float, amp: Float, ph: Float, color: Int, shift: Float) {
        dunePath.reset()
        dunePath.moveTo(0f, h)
        val n = 10
        for (i in 0..n) {
            val k = i.toFloat() / n
            dunePath.lineTo(w * k, h * base + sin(k * TWO_PI * 1.2f + ph + shift) * amp * h)
        }
        dunePath.lineTo(w, h)
        dunePath.close()
        fillP.color = color
        cv.drawPath(dunePath, fillP)
    }

    private fun extras(cv: Canvas, w: Float, h: Float, t: Float, page: Float) {
        val px = page * PARALLAX * unit
        for (i in 0 until NSTAR) {
            val tw = 0.5f + 0.5f * sin(TWO_PI * t / sp[i] + i)
            fillP.color = 0xFFFFF4D6.toInt(); fillP.alpha = (30f + 190f * tw).toInt()
            cv.drawCircle(sx[i] * w - px * 0.3f, sy[i] * h, (0.8f + tw) * unit, fillP)
        }
        val pulse = 0.85f + 0.15f * sin(t * 0.9f)
        cv.save()
        cv.translate(0.76f * w - px * 0.5f, 0.17f * h)
        cv.scale(pulse, pulse)
        cv.drawPaint(sunPaint)
        cv.restore()
        fillP.color = 0xFFFFE9A8.toInt(); fillP.alpha = 235
        cv.drawCircle(0.76f * w - px * 0.5f, 0.17f * h, 26f * unit * pulse, fillP)
        lineP.color = 0xFFFFE9A8.toInt(); lineP.strokeWidth = 1.4f * unit
        for (k in 0 until 4) {
            val y = 0.30f * h + k * 9f * unit
            lineP.alpha = 60 - k * 12
            val sh = sin(t * 1.6f + k * 1.3f) * 8f * unit
            cv.drawLine(w * 0.55f + sh, y, w * 0.95f + sh, y, lineP)
        }
        dune(cv, w, h, 0.66f, 0.03f, 0.6f, 0x40C9803A, px * 0.002f + t * 0.02f)
        dune(cv, w, h, 0.78f, 0.04f, 2.4f, 0x55B8662A, px * 0.003f - t * 0.015f)
        dune(cv, w, h, 0.9f, 0.035f, 4.1f, 0x66985020, px * 0.004f + t * 0.01f)

        fillP.color = 0xFFFFE08A.toInt()
        for (i in 0 until NGR) {
            val x = ((gOff[i] + t * gSpeed[i]) % 1f) * (w + 40f * unit) - 20f * unit
            val y = gy[i] * h + sin(t * 0.7f + i) * 12f * unit
            fillP.alpha = 120
            cv.drawRect(x, y, x + gLen[i] * unit * 0.3f, y + 1.6f * unit, fillP)
            cv.drawCircle(x, y, 1.4f * unit, fillP)
        }

        val tu = (t % 21f) / 15f
        if (tu < 1f) {
            val r = 15f * unit
            val x = -r + (w + 2f * r) * tu
            val y = 0.86f * h - r - abs(sin(tu * 22f)) * 8f * unit
            lineP.color = 0xFF6E4320.toInt(); lineP.strokeWidth = 1.4f * unit; lineP.alpha = 230
            cv.save(); cv.translate(x, y); cv.rotate(tu * 1600f)
            cv.drawCircle(0f, 0f, r, lineP)
            cv.drawCircle(0f, 0f, r * 0.62f, lineP)
            cv.drawLine(-r, 0f, r, 0f, lineP)
            cv.drawLine(0f, -r, 0f, r, lineP)
            cv.restore()
        }

        lineP.color = 0xFF4A2A18.toInt(); lineP.strokeWidth = 1.8f * unit; lineP.alpha = 200
        val bu = (t % 25f) / 18f
        if (bu < 1f) {
            for (b in 0 until NBIRD) {
                val x = -40f * unit + (w + 80f * unit) * bu + b * 22f * unit * (if (b % 2 == 0) 1 else -1)
                val y = 0.2f * h + b * 12f * unit + sin(bu * 9f + b) * 6f * unit
                val flap = sin(t * 9f + b * 1.7f) * 5f * unit
                birdPath.reset()
                birdPath.moveTo(x - 9f * unit, y - flap)
                birdPath.quadTo(x - 4f * unit, y - 3f * unit, x, y)
                birdPath.quadTo(x + 4f * unit, y - 3f * unit, x + 9f * unit, y - flap)
                cv.drawPath(birdPath, lineP)
            }
        }

        val st = (t % 23f) / 6f
        if (st < 1f) {
            val e = sin(st * PI.toFloat())
            stormPaint.alpha = (e * 255f).toInt()
            val x = -w + 2f * w * st
            cv.save(); cv.translate(x, 0f)
            cv.drawRect(0f, 0f, w, h, stormPaint)
            cv.restore()
            fillP.color = 0xFFF2CD8A.toInt()
            for (i in 0 until 24) {
                val gx = (((i * 53) % 100) / 100f * w + st * w * 3f) % w
                val gyy = ((i * 37) % 100) / 100f * h
                fillP.alpha = (e * 190f).toInt()
                cv.drawRect(gx, gyy, gx + 14f * unit, gyy + 1.4f * unit, fillP)
            }
        }
    }

    override fun onSize(w: Float, h: Float, density: Float) {
        unit = density
        val base = maxOf(w, h)
        for (b in blobs) {
            b.paint.shader = RadialGradient(
                0f, 0f, b.radius * base,
                intArrayOf((BLOB_ALPHA shl 24) or (b.color and 0x00FFFFFF), b.color and 0x00FFFFFF), null, Shader.TileMode.CLAMP
            )
        }
        buildExtras(w, h)
        built = true
    }

    override fun draw(cv: Canvas, w: Float, h: Float, t: Float, dt: Float, page: Float) {
        if (!built) return
        for (b in blobs) {
            val a = TWO_PI * t / b.period + b.phase
            cv.save()
            cv.translate(b.x * w + cos(a) * DRIFT * w - page * PARALLAX * unit, b.y * h + sin(a) * DRIFT * h)
            cv.drawPaint(b.paint)
            cv.restore()
        }
        for (i in 0 until DUST) {
            val x = (dustX[i] + t * dustSpeed[i]) % 1f
            val y = dustY[i] * h + sin(t * BOB + i) * BOB_SIZE * unit
            cv.drawCircle(x * (w + 2f * MARGIN * unit) - MARGIN * unit - page * PARALLAX * unit, y, dustSize[i] * unit, dustPaint)
        }
        extras(cv, w, h, t, page)
    }

    private companion object {
        const val TWO_PI = (2.0 * PI).toFloat()
        const val SUN = 0xFFFFA64D.toInt()
        const val CLAY = 0xFFE8743B.toInt()
        const val TEAL = 0xFF2A9D8F.toInt()
        const val SAND = 0xFFFFE08A.toInt()
        const val BLOB_ALPHA = 125
        const val DRIFT = 0.05f
        const val PARALLAX = 16f
        const val DUST = 30
        const val SEED = 5
        const val SPEED_MIN = 0.01f
        const val SPEED_SPAN = 0.025f
        const val SIZE_MIN = 1.2f
        const val SIZE_SPAN = 2.4f
        const val DUST_ALPHA = 150
        const val BOB = 0.8f
        const val BOB_SIZE = 10f
        const val MARGIN = 8f
    }
}
