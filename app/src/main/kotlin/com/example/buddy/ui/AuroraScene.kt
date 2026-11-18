package com.example.buddy.ui

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

class AuroraScene : ThemeScene() {
    override val ready = true

    private class Blob(
        val color: Int,
        val homeX: Float,
        val homeY: Float,
        val radius: Float,
        val driftX: Float,
        val driftY: Float,
        val period: Float,
        val phase: Float,
        val parallax: Float
    ) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    }

    private val blobs = listOf(
        Blob(MINT, 0.18f, 0.22f, 0.62f, 0.10f, 0.06f, 21f, 0f, 14f),
        Blob(LIME, 0.82f, 0.38f, 0.55f, 0.09f, 0.08f, 27f, 1.7f, 18f),
        Blob(BLUE, 0.30f, 0.70f, 0.60f, 0.12f, 0.05f, 24f, 3.1f, 22f),
        Blob(DEEP, 0.78f, 0.86f, 0.58f, 0.08f, 0.06f, 30f, 4.4f, 26f)
    )
    private val ar = Random(11)
    private val NST = 40
    private val stX = FloatArray(NST) { ar.nextFloat() }
    private val stY = FloatArray(NST) { ar.nextFloat() * 0.7f }
    private val stP = FloatArray(NST) { 2f + ar.nextFloat() * 4f }
    private val stO = FloatArray(NST) { ar.nextFloat() * 6f }
    private val stR = FloatArray(NST) { 0.8f + ar.nextFloat() * 1.4f }
    private val NSN = 28
    private val snX = FloatArray(NSN) { ar.nextFloat() }
    private val snDur = FloatArray(NSN) { 12f + ar.nextFloat() * 14f }
    private val snOff = FloatArray(NSN) { ar.nextFloat() * 26f }
    private val snR = FloatArray(NSN) { 1f + ar.nextFloat() * 2f }
    private val snSway = FloatArray(NSN) { 8f + ar.nextFloat() * 22f }
    private val ribbonCol = intArrayOf(MINT, LIME, BLUE)
    private val ribbonBase = floatArrayOf(0.14f, 0.26f, 0.4f)
    private val ribbonPaint = Array(3) { Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL } }
    private val ribbonPath = android.graphics.Path()
    private val dot = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val streak = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }
    private val arcPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }
    private val arcRect = android.graphics.RectF()

    private fun buildRibbons(h: Float) {
        for (i in 0 until 3) {
            val y0 = ribbonBase[i] * h
            ribbonPaint[i].shader = android.graphics.LinearGradient(0f, y0 - 0.06f * h, 0f, y0 + 0.34f * h,
                intArrayOf(withAlpha(ribbonCol[i], 0), withAlpha(ribbonCol[i], 120), withAlpha(ribbonCol[i], 0)), floatArrayOf(0f, 0.3f, 1f), Shader.TileMode.CLAMP)
        }
        arcPaint.shader = android.graphics.LinearGradient(0f, 0f, 0f, h * 0.5f,
            intArrayOf(withAlpha(0xFFB8FFE0.toInt(), 0), withAlpha(0xFFB8FFE0.toInt(), 160)), null, Shader.TileMode.CLAMP)
    }

    private fun extras(cv: Canvas, w: Float, h: Float, t: Float, page: Float, density: Float) {
        val two = (2.0 * PI).toFloat()
        val px = page * 10f * density
        for (i in 0 until NST) {
            val tw = 0.5f + 0.5f * sin(two * t / stP[i] + stO[i])
            dot.color = 0xFFFFFFFF.toInt(); dot.alpha = (40f + 200f * tw).toInt()
            cv.drawCircle(stX[i] * w - px * 0.4f, stY[i] * h, stR[i] * density * (0.7f + 0.5f * tw), dot)
        }
        for (r in 0 until 3) {
            val y0 = ribbonBase[r] * h
            val amp = (0.035f + 0.012f * r) * h
            val ph = t * (0.35f + 0.12f * r) + r * 2.1f
            val steps = 14
            ribbonPath.reset()
            ribbonPath.moveTo(0f, y0)
            for (s in 0..steps) {
                val x = w * s / steps
                val k = s.toFloat() / steps
                ribbonPath.lineTo(x, y0 + sin(k * two * 1.3f + ph) * amp + sin(k * two * 2.7f - ph * 1.4f) * amp * 0.4f)
            }
            for (s in steps downTo 0) {
                val x = w * s / steps
                val k = s.toFloat() / steps
                ribbonPath.lineTo(x, y0 + 0.3f * h + sin(k * two * 1.1f + ph * 0.8f + 1f) * amp * 1.4f)
            }
            ribbonPath.close()
            ribbonPaint[r].alpha = (170f + 70f * sin(t * 0.5f + r)).toInt().coerceIn(60, 255)
            cv.save(); cv.translate(-px * (0.6f + 0.3f * r), 0f)
            cv.drawPath(ribbonPath, ribbonPaint[r])
            cv.restore()
        }
        for (i in 0 until NSN) {
            val u = ((t + snOff[i]) % snDur[i]) / snDur[i]
            val x = snX[i] * w + sin(u * two * 2f + i) * snSway[i] * density - px
            val y = -10f * density + (h + 20f * density) * u
            dot.color = 0xFFFFFFFF.toInt(); dot.alpha = if (u < 0.05f) (170f * u / 0.05f).toInt() else if (u > 0.95f) (170f * (1f - u) / 0.05f).toInt() else 170
            cv.drawCircle(x, y, snR[i] * density, dot)
        }
        val su = (t % 11f) / 1.6f
        if (su < 1f) {
            val sx = w * (0.15f + 0.6f * (((t / 11f).toInt() * 37) % 10) / 10f) + su * 0.35f * w
            val sy = 0.04f * h + su * 0.22f * h
            val fa = (1f - su)
            for (k in 0 until 6) {
                streak.color = 0xFFFFFFFF.toInt(); streak.strokeWidth = (2.6f - k * 0.38f) * density
                streak.alpha = (fa * 230f / (1 + k)).toInt()
                cv.drawLine(sx - k * 14f * density, sy - k * 7f * density, sx - (k + 1) * 14f * density, sy - (k + 1) * 7f * density, streak)
            }
            dot.color = 0xFFFFFFFF.toInt(); dot.alpha = (fa * 255f).toInt()
            cv.drawCircle(sx, sy, 2.6f * density, dot)
        }
        val au = (t % 19f) / 7f
        if (au < 1f) {
            val e = sin(au * PI.toFloat())
            arcPaint.strokeWidth = 26f * density
            arcPaint.alpha = (e * 200f).toInt()
            val cx = w * (0.1f + 0.8f * au)
            arcRect.set(cx - w * 0.7f, h * 0.05f, cx + w * 0.7f, h * 1.1f)
            cv.drawArc(arcRect, 200f, 140f, false, arcPaint)
        }
    }

    private val grainPaint = Paint().apply { alpha = GRAIN_ALPHA }
    private var built = false

    override fun onSize(w: Float, h: Float, density: Float) {
        val base = maxOf(w, h)
        for (b in blobs) {
            val r = b.radius * base
            b.paint.shader = RadialGradient(0f, 0f, r, intArrayOf(withAlpha(b.color, BLOB_ALPHA), withAlpha(b.color, 0)), null, Shader.TileMode.CLAMP)
        }
        grainPaint.shader = BitmapShader(grainTile(), Shader.TileMode.REPEAT, Shader.TileMode.REPEAT)
        buildRibbons(h)
        built = true
    }

    override fun draw(cv: Canvas, w: Float, h: Float, t: Float, dt: Float, page: Float) {
        if (!built) return
        val density = w / REF_WIDTH
        val twoPi = (2.0 * PI).toFloat()
        for (b in blobs) {
            val a = twoPi * (t / b.period) + b.phase
            val x = b.homeX * w + cos(a) * b.driftX * w - page * b.parallax * density
            val y = b.homeY * h + sin(a * 0.8f) * b.driftY * h
            cv.save()
            cv.translate(x, y)
            cv.drawPaint(b.paint)
            cv.restore()
        }
        extras(cv, w, h, t, page, density)
        cv.drawRect(0f, 0f, w, h, grainPaint)
    }

    private fun grainTile(): Bitmap {
        val bmp = Bitmap.createBitmap(GRAIN_SIZE, GRAIN_SIZE, Bitmap.Config.ARGB_8888)
        val rnd = Random(GRAIN_SEED)
        val px = IntArray(GRAIN_SIZE * GRAIN_SIZE) {
            val v = rnd.nextInt(GRAIN_MAX)
            if (rnd.nextBoolean()) (v shl 24) or 0x00FFFFFF else (v shl 24)
        }
        bmp.setPixels(px, 0, GRAIN_SIZE, 0, 0, GRAIN_SIZE, GRAIN_SIZE)
        return bmp
    }

    private fun withAlpha(color: Int, alpha: Int) = (alpha shl 24) or (color and 0x00FFFFFF)

    private companion object {
        const val MINT = 0xFF1EA97B.toInt()
        const val LIME = 0xFF72C613.toInt()
        const val BLUE = 0xFF2180E6.toInt()
        const val DEEP = 0xFF044B39.toInt()
        const val BLOB_ALPHA = 150
        const val GRAIN_SIZE = 128
        const val GRAIN_SEED = 7
        const val GRAIN_MAX = 60
        const val GRAIN_ALPHA = 90
        const val REF_WIDTH = 390f
    }
}
