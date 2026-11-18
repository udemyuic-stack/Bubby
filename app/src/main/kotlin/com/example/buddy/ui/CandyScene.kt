package com.example.buddy.ui

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

class CandyScene : ThemeScene() {
    override val ready = true

    private class Blob(val color: Int, val x: Float, val y: Float, val radius: Float, val period: Float, val phase: Float) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    }

    private val blobs = listOf(
        Blob(PINK, 0.15f, 0.2f, 0.6f, 23f, 0f),
        Blob(MINT, 0.85f, 0.45f, 0.55f, 29f, 2f),
        Blob(LEMON, 0.35f, 0.8f, 0.6f, 26f, 4f)
    )
    private val sprinkleX = FloatArray(SPRINKLES)
    private val sprinkleY = FloatArray(SPRINKLES)
    private val sprinkleSpeed = FloatArray(SPRINKLES)
    private val sprinkleTurn = FloatArray(SPRINKLES)
    private val sprinkleColor = IntArray(SPRINKLES)
    private val sprinklePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        strokeCap = Paint.Cap.ROUND
        strokeWidth = SPRINKLE_WIDTH
    }
    private var built = false
    private var unit = 1f

    init {
        val colors = intArrayOf(PINK, MINT, LEMON, GRAPE)
        val rnd = kotlin.random.Random(SEED)
        for (i in 0 until SPRINKLES) {
            sprinkleX[i] = rnd.nextFloat()
            sprinkleY[i] = rnd.nextFloat()
            sprinkleSpeed[i] = SPEED_MIN + rnd.nextFloat() * SPEED_SPAN
            sprinkleTurn[i] = rnd.nextFloat() * TWO_PI
            sprinkleColor[i] = colors[i % colors.size]
        }
    }

    private val r2 = kotlin.random.Random(21)
    private val NBUB = 16
    private val bubX = FloatArray(NBUB) { r2.nextFloat() }
    private val bubR = FloatArray(NBUB) { 6f + r2.nextFloat() * 16f }
    private val bubDur = FloatArray(NBUB) { 9f + r2.nextFloat() * 10f }
    private val bubOff = FloatArray(NBUB) { r2.nextFloat() * 18f }
    private val bubSway = FloatArray(NBUB) { 10f + r2.nextFloat() * 24f }
    private val bubCol = IntArray(NBUB) { intArrayOf(PINK, MINT, LEMON, GRAPE)[it % 4] }
    private val NFALL = 16
    private val fallX = FloatArray(NFALL) { r2.nextFloat() }
    private val fallDur = FloatArray(NFALL) { 10f + r2.nextFloat() * 10f }
    private val fallOff = FloatArray(NFALL) { r2.nextFloat() * 20f }
    private val fallSize = FloatArray(NFALL) { 0.8f + r2.nextFloat() * 0.9f }
    private val fallSpin = FloatArray(NFALL) { (r2.nextFloat() - 0.5f) * 540f }
    private val fallSway = FloatArray(NFALL) { 14f + r2.nextFloat() * 30f }
    private val fallCol = IntArray(NFALL) { intArrayOf(PINK, MINT, LEMON, GRAPE)[(it + 1) % 4] }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }
    private val star = android.graphics.Path()
    private val wrapper = android.graphics.Path()
    private val sheen = Paint(Paint.ANTI_ALIAS_FLAG)
    private var sheenW = 0f

    private fun buildExtras(w: Float, d: Float) {
        star.reset()
        for (i in 0 until 10) {
            val rad = if (i % 2 == 0) 12f else 5f
            val a = -PI.toFloat() / 2f + i * PI.toFloat() / 5f
            if (i == 0) star.moveTo(cos(a) * rad, sin(a) * rad) else star.lineTo(cos(a) * rad, sin(a) * rad)
        }
        star.close()
        wrapper.reset()
        wrapper.moveTo(-8f, 0f); wrapper.lineTo(-17f, -8f); wrapper.lineTo(-17f, 8f); wrapper.close()
        wrapper.moveTo(8f, 0f); wrapper.lineTo(17f, -8f); wrapper.lineTo(17f, 8f); wrapper.close()
        sheenW = 0.3f * w
        sheen.shader = android.graphics.LinearGradient(-sheenW / 2f, 0f, sheenW / 2f, 0f, intArrayOf(0x00FFFFFF, 0x59FFFFFF, 0x00FFFFFF), null, Shader.TileMode.CLAMP)
    }

    private fun extras(cv: Canvas, w: Float, h: Float, t: Float, page: Float) {
        val px = page * PARALLAX * unit
        for (i in 0 until NBUB) {
            val u = ((t + bubOff[i]) % bubDur[i]) / bubDur[i]
            val r = bubR[i] * unit
            val x = bubX[i] * w + sin(u * TWO_PI * 2f + i) * bubSway[i] * unit - px
            val y = h + r - (h + 2f * r) * u
            val fade = if (u < 0.08f) u / 0.08f else if (u > 0.92f) (1f - u) / 0.08f else 1f
            fill.color = bubCol[i]; fill.alpha = (70f * fade).toInt()
            cv.drawCircle(x, y, r, fill)
            line.color = Color.WHITE; line.strokeWidth = 1.6f * unit; line.alpha = (200f * fade).toInt()
            cv.drawCircle(x, y, r, line)
            fill.color = Color.WHITE; fill.alpha = (220f * fade).toInt()
            cv.drawCircle(x - r * 0.38f, y - r * 0.38f, r * 0.2f, fill)
        }
        for (i in 0 until NFALL) {
            val u = ((t + fallOff[i]) % fallDur[i]) / fallDur[i]
            val sz = fallSize[i] * unit
            val x = fallX[i] * w + sin(u * TWO_PI * 1.5f + i) * fallSway[i] * unit - px * 1.3f
            val y = -30f * unit + (h + 60f * unit) * u
            val fade = if (u < 0.06f) u / 0.06f else if (u > 0.94f) (1f - u) / 0.06f else 1f
            cv.save()
            cv.translate(x, y); cv.rotate(fallSpin[i] * u); cv.scale(sz, sz)
            fill.color = fallCol[i]; fill.alpha = (235f * fade).toInt()
            when (i % 3) {
                0 -> cv.drawPath(star, fill)
                1 -> {
                    cv.drawCircle(0f, 0f, 9f, fill)
                    cv.drawPath(wrapper, fill)
                    line.color = Color.WHITE; line.strokeWidth = 1.6f; line.alpha = (200f * fade).toInt()
                    cv.drawLine(-4f, -5f, 4f, 5f, line)
                }
                else -> {
                    line.color = Color.WHITE; line.strokeWidth = 2.4f; line.alpha = (230f * fade).toInt()
                    cv.drawLine(0f, 8f, 0f, 24f, line)
                    cv.drawCircle(0f, 0f, 10f, fill)
                    line.color = Color.WHITE; line.strokeWidth = 1.6f; line.alpha = (210f * fade).toInt()
                    cv.drawCircle(0f, 0f, 6f, line)
                    cv.drawCircle(0f, 0f, 2.5f, line)
                }
            }
            cv.restore()
        }
        val su = (t % 12f) / 12f
        val e = 0.5f - 0.5f * cos(PI.toFloat() * su * 2f).coerceIn(-1f, 1f)
        if (su < 0.5f) {
            val k = su * 2f
            cv.save()
            cv.translate(-sheenW + (w + 2f * sheenW) * k, 0.55f * h)
            cv.rotate(18f)
            cv.drawRect(-sheenW / 2f, -0.7f * h, sheenW / 2f, 0.7f * h, sheen)
            cv.restore()
        }
        val bu = (t % 27f) / 13f
        if (bu < 1f) {
            val r = 36f * unit
            val x = w * 0.72f + sin(t * 0.9f) * 22f * unit - px
            val y = h + r + 90f * unit - (h + 2f * r + 180f * unit) * bu
            line.color = Color.WHITE; line.strokeWidth = 1.6f * unit; line.alpha = 220
            cv.drawLine(x, y + r * 1.12f, x + sin(t * 2f) * 12f * unit, y + r * 1.12f + 70f * unit, line)
            fill.color = PINK; fill.alpha = 240
            cv.save()
            cv.translate(x, y); cv.scale(1f, 1.18f)
            cv.drawCircle(0f, 0f, r, fill)
            fill.color = Color.WHITE; fill.alpha = 150
            cv.drawCircle(-r * 0.32f, -r * 0.3f, r * 0.2f, fill)
            cv.restore()
            fill.color = PINK; fill.alpha = 240
            cv.drawCircle(x, y + r * 1.2f, 4f * unit, fill)
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
        buildExtras(w, density)
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
        val len = SPRINKLE_LENGTH * unit
        for (i in 0 until SPRINKLES) {
            val fall = (sprinkleY[i] + t * sprinkleSpeed[i]) % 1f
            val x = sprinkleX[i] * w + sin(t * SWAY + sprinkleTurn[i]) * SWAY_SIZE * unit - page * PARALLAX * unit
            val y = fall * (h + 2f * len) - len
            val turn = sprinkleTurn[i] + t * SPIN
            sprinklePaint.color = (SPRINKLE_ALPHA shl 24) or (sprinkleColor[i] and 0x00FFFFFF)
            cv.drawLine(x - cos(turn) * len, y - sin(turn) * len, x + cos(turn) * len, y + sin(turn) * len, sprinklePaint)
        }
        extras(cv, w, h, t, page)
    }

    private companion object {
        const val TWO_PI = (2.0 * PI).toFloat()
        const val PINK = 0xFFFF5C9A.toInt()
        const val MINT = 0xFF5CD6B5.toInt()
        const val LEMON = 0xFFFFD84D.toInt()
        const val GRAPE = 0xFF9B6BFF.toInt()
        const val BLOB_ALPHA = 120
        const val DRIFT = 0.06f
        const val PARALLAX = 16f
        const val SPRINKLES = 26
        const val SEED = 11
        const val SPEED_MIN = 0.012f
        const val SPEED_SPAN = 0.02f
        const val SPRINKLE_WIDTH = 5f
        const val SPRINKLE_LENGTH = 5f
        const val SPRINKLE_ALPHA = 150
        const val SWAY = 0.6f
        const val SWAY_SIZE = 14f
        const val SPIN = 0.5f
    }
}
