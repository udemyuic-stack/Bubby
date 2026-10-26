package com.example.buddy

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.view.View
import android.view.animation.LinearInterpolator
import kotlin.math.max
import kotlin.math.sin

/** Tiny status animation: bars while listening, bouncing dots while thinking, equalizer while speaking. */
class WaveView(c: Context) : View(c) {
    companion object { const val NONE = 0; const val LISTEN = 1; const val THINK = 2; const val SPEAK = 3 }

    var mode = NONE
        set(v) { if (field == v) return; field = v; sync(); invalidate() }
    var color = 0xFF7A6BC4.toInt()
        set(v) { field = v; invalidate() }
    var accent = 0xFFE6E1F8.toInt()
        set(v) { field = v; invalidate() }

    private val u = resources.displayMetrics.density
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val r = RectF()
    private var phase = 0f
    private var anim: ValueAnimator? = null

    override fun onMeasure(a: Int, b: Int) = setMeasuredDimension((38 * u).toInt(), (24 * u).toInt())

    private fun sync() {
        val want = mode != NONE && isAttachedToWindow && visibility == VISIBLE && IslandUi.animOn(context)
        if (!want) { anim?.cancel(); anim = null; return }
        if (anim != null) return
        anim = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 1100
            repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
            addUpdateListener { phase = it.animatedValue as Float; invalidate() }
            start()
        }
    }

    override fun onAttachedToWindow() { super.onAttachedToWindow(); sync() }
    override fun onDetachedFromWindow() { anim?.cancel(); anim = null; super.onDetachedFromWindow() }
    override fun onVisibilityChanged(changedView: View, visibility: Int) {
        super.onVisibilityChanged(changedView, visibility)
        sync()
    }

    private fun mix(a: Int, b: Int, f: Float): Int {
        val k = 1f - f
        return Color.argb(255,
            (Color.red(a) * k + Color.red(b) * f).toInt(),
            (Color.green(a) * k + Color.green(b) * f).toInt(),
            (Color.blue(a) * k + Color.blue(b) * f).toInt())
    }

    private fun bars(cv: Canvas, n: Int, cycles: Int, maxFrac: Float) {
        val bw = 3.2f * u
        val gap = 2.6f * u
        val total = n * bw + (n - 1) * gap
        val x0 = (width - total) / 2f
        val cy = height / 2f
        for (i in 0 until n) {
            val a = 0.5f + 0.5f * sin(6.2831855f * phase * cycles + i * 0.9f)
            val bh = height * maxFrac * (0.22f + 0.78f * a)
            val x = x0 + i * (bw + gap)
            p.color = mix(color, accent, if (n > 1) i / (n - 1f) else 0f)
            r.set(x, cy - bh / 2f, x + bw, cy + bh / 2f)
            cv.drawRoundRect(r, bw / 2f, bw / 2f, p)
        }
    }

    override fun onDraw(cv: Canvas) {
        p.style = Paint.Style.FILL
        when (mode) {
            LISTEN -> bars(cv, 7, 2, 1f)
            SPEAK -> bars(cv, 4, 3, 0.6f)
            THINK -> {
                val rad = 2.6f * u
                for (i in 0..2) {
                    val t = ((phase - i * 0.16f) % 1f + 1f) % 1f
                    val b = max(0f, sin(6.2831855f * t))
                    p.color = mix(color, accent, i / 2f)
                    p.alpha = (255 * (0.5f + 0.5f * b)).toInt()
                    cv.drawCircle(width / 2f + (i - 1) * 8f * u, height / 2f - b * 5f * u, rad, p)
                }
            }
        }
    }
}
