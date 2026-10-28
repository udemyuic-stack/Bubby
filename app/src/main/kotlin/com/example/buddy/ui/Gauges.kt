package com.example.buddy.ui

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.view.View
import android.view.animation.DecelerateInterpolator
import com.example.buddy.AgentWidgets

/** A ring that fills to [value] (0..1), with the percentage in the middle; "—" when there is no number. */
class RingGauge(c: Context) : View(c) {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val box = RectF()
    private var shown = 0f
    private var target: Float? = null
    private var anim: ValueAnimator? = null

    fun setValue(v: Float?) {
        val old = target
        target = v
        if (v == null) { shown = 0f; invalidate(); return }
        anim?.cancel()
        anim = ValueAnimator.ofFloat(if (old == null) 0f else shown, v).apply {
            duration = 800; interpolator = DecelerateInterpolator(1.8f)
            addUpdateListener { shown = it.animatedValue as Float; invalidate() }
            start()
        }
    }

    override fun onDraw(cv: Canvas) {
        val d = resources.displayMetrics.density
        val s = minOf(width, height).toFloat()
        val sw = 10f * d
        box.set((width - s) / 2 + sw / 2, (height - s) / 2 + sw / 2, (width + s) / 2 - sw / 2, (height + s) / 2 - sw / 2)
        p.style = Paint.Style.STROKE; p.strokeWidth = sw; p.strokeCap = Paint.Cap.ROUND
        p.color = P.outlineVariant
        cv.drawArc(box, 135f, 270f, false, p)
        val t = target
        if (t != null) { p.color = AgentWidgets.ringColor(t); cv.drawArc(box, 135f, 270f * shown.coerceAtLeast(0.01f), false, p) }
        p.style = Paint.Style.FILL; p.color = P.onSurface; p.textAlign = Paint.Align.CENTER
        p.typeface = Typeface.DEFAULT_BOLD; p.textSize = s * 0.24f
        cv.drawText(if (t != null) "${(shown * 100).toInt()}%" else "—", width / 2f, height / 2f + s * 0.08f, p)
    }
}
