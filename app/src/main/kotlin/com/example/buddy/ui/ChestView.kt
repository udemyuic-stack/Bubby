package com.example.buddy.ui

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Camera
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.view.View
import android.view.animation.OvershootInterpolator
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

class ChestView(c: Context) : View(c) {
    private class Star(val a: Float, val v: Float, val r: Float)

    var ready = false
        set(v) { field = v; if (v && isAttachedToWindow) wobble.start() else wobble.cancel(); invalidate() }
    var big = false
        set(v) { field = v; invalidate() }

    private var open = 0f
    private var t = 0f
    private var wob = 0f
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()
    private val cam = Camera()
    private val mat = Matrix()
    private val stars = Array(18) { Star(Random.nextFloat() * 0.9f - 0.45f, 0.6f + Random.nextFloat() * 0.8f, 3f + Random.nextFloat() * 4f) }

    private val wobble = ValueAnimator.ofFloat(0f, (2 * PI).toFloat()).apply {
        duration = 1400
        repeatCount = ValueAnimator.INFINITE
        addUpdateListener { wob = it.animatedValue as Float; invalidate() }
    }

    private val opener = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = 900
        interpolator = OvershootInterpolator(1.6f)
        addUpdateListener { open = it.animatedValue as Float; t = it.animatedFraction; invalidate() }
    }

    fun openUp() {
        wobble.cancel()
        wob = 0f
        opener.start()
    }

    fun close() {
        opener.cancel()
        open = 0f
        t = 0f
        invalidate()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        wobble.cancel()
        opener.cancel()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (ready && open == 0f) wobble.start()
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val s = minOf(w / 220f, h / 150f)
        val cx = w / 2f
        val baseY = h * 0.9f
        val bw = 150f * s
        val bh = 62f * s
        val lh = 40f * s
        val tilt = sin(wob) * 4f * (if (ready) 1f else 0f)
        canvas.save()
        canvas.rotate(tilt, cx, baseY)
        val shadow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = RadialGradient(cx, baseY + 4 * s, bw * 0.7f, 0x55000000, 0, Shader.TileMode.CLAMP)
        }
        canvas.save()
        canvas.scale(1f, 0.18f, cx, baseY + 4 * s)
        canvas.drawCircle(cx, baseY + 4 * s, bw * 0.7f, shadow)
        canvas.restore()

        if (open > 0.05f) {
            val g = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = RadialGradient(cx, baseY - bh, bw * 0.9f, 0xCCFFE9A0.toInt(), 0, Shader.TileMode.CLAMP)
                alpha = (255 * open.coerceIn(0f, 1f)).toInt()
            }
            canvas.drawCircle(cx, baseY - bh, bw * 0.9f, g)
            paint.style = Paint.Style.FILL
            for (st in stars) {
                val p = ((t * 1.4f - st.a * 0.2f)).coerceIn(0f, 1f)
                if (p <= 0f || p >= 1f) continue
                val x = cx + st.a * bw * 1.4f * p
                val y = baseY - bh - st.v * 90f * s * p + 70f * s * p * p
                paint.color = 0xFFFFD23A.toInt()
                paint.alpha = (255 * (1f - p)).toInt()
                canvas.drawCircle(x, y, st.r * s * (1f - p * 0.4f), paint)
            }
            paint.alpha = 255
        }

        rect.set(cx - bw / 2f, baseY - bh, cx + bw / 2f, baseY)
        paint.shader = LinearGradient(0f, rect.top, 0f, rect.bottom, 0xFFB2713B.toInt(), 0xFF6B3F1D.toInt(), Shader.TileMode.CLAMP)
        canvas.drawRoundRect(rect, 10f * s, 10f * s, paint)
        paint.shader = null
        paint.color = 0xFFF3C34B.toInt()
        canvas.drawRect(cx - bw / 2f, baseY - bh * 0.62f, cx + bw / 2f, baseY - bh * 0.5f, paint)
        canvas.drawRect(cx - 7f * s, baseY - bh * 0.78f, cx + 7f * s, baseY - bh * 0.3f, paint)
        paint.color = 0xFF6B3F1D.toInt()
        canvas.drawCircle(cx, baseY - bh * 0.52f, 3.2f * s, paint)

        val hingeY = baseY - bh
        cam.save()
        cam.rotateX(-open * 105f)
        cam.getMatrix(mat)
        cam.restore()
        mat.preTranslate(-cx, -hingeY)
        mat.postTranslate(cx, hingeY)
        canvas.save()
        canvas.concat(mat)
        rect.set(cx - bw / 2f - 3f * s, hingeY - lh, cx + bw / 2f + 3f * s, hingeY + 2f * s)
        paint.shader = LinearGradient(0f, rect.top, 0f, rect.bottom, 0xFFD08A4A.toInt(), 0xFF8A5126.toInt(), Shader.TileMode.CLAMP)
        canvas.drawRoundRect(rect, 14f * s, 14f * s, paint)
        paint.shader = null
        paint.color = if (big) 0xFFFFE08A.toInt() else 0xFFF3C34B.toInt()
        canvas.drawRect(cx - 7f * s, hingeY - lh, cx + 7f * s, hingeY + 2f * s, paint)
        canvas.drawRect(cx - bw / 2f - 3f * s, hingeY - lh * 0.55f, cx + bw / 2f + 3f * s, hingeY - lh * 0.4f, paint)
        canvas.restore()
        canvas.restore()
    }
}
