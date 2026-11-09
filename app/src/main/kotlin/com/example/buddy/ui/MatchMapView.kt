package com.example.buddy.ui

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.view.MotionEvent
import android.view.View
import kotlin.math.abs
import kotlin.math.sin

class MatchMapView(c: Context, private val count: Int, private val unlocked: Int, private val stars: (Int) -> Int, private val worldColors: IntArray, private val worldNames: List<String>, private val perWorld: Int, private val onTap: (Int) -> Unit) : View(c) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = glassFont(bold = true); textAlign = Paint.Align.CENTER }
    private val path = Path()
    private val rect = RectF()
    private var phase = 0f
    private val shadowSh = arrayOfNulls<RadialGradient>(count)
    private val bodySh = arrayOfNulls<RadialGradient>(count)
    private val worldSh = arrayOfNulls<LinearGradient>((count + perWorld - 1) / perWorld)
    private val dirty = android.graphics.Rect()
    private val gap get() = context.dpf(96f)
    private val node get() = context.dpf(27f)
    private val pulse = ValueAnimator.ofFloat(0f, 6.2832f).apply {
        duration = 1600
        repeatCount = ValueAnimator.INFINITE
        addUpdateListener {
            phase = it.animatedValue as Float
            val cy = ny(unlocked - 1).toInt()
            val pad = context.dpf(48f).toInt()
            dirty.set(0, cy - pad, width, cy + pad)
            invalidate(dirty)
        }
    }

    fun totalHeight() = (count * gap + context.dpf(150f)).toInt()

    fun currentY() = ny(unlocked - 1)

    private fun nx(i: Int): Float {
        val w = width.toFloat()
        return w / 2f + sin(i * 1.05f) * w * 0.3f
    }

    private fun ny(i: Int): Float = height - context.dpf(80f) - i * gap

    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) {
        shadowSh.fill(null); bodySh.fill(null); worldSh.fill(null)
    }

    override fun onMeasure(w: Int, h: Int) = setMeasuredDimension(MeasureSpec.getSize(w), totalHeight())

    override fun onAttachedToWindow() { super.onAttachedToWindow(); pulse.start() }
    override fun onDetachedFromWindow() { super.onDetachedFromWindow(); pulse.cancel() }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        if (e.actionMasked == MotionEvent.ACTION_UP) {
            for (i in 0 until count) {
                if (abs(e.x - nx(i)) < node * 1.5f && abs(e.y - ny(i)) < node * 1.5f) { performClick(); onTap(i); break }
            }
        }
        return e.actionMasked == MotionEvent.ACTION_DOWN || e.actionMasked == MotionEvent.ACTION_UP
    }

    override fun performClick(): Boolean = super.performClick()

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val worlds = (count + perWorld - 1) / perWorld
        for (k in 0 until worlds) {
            val top = ny(minOf(count, (k + 1) * perWorld) - 1) - gap * 0.9f
            val bottom = if (k == 0) h else ny(k * perWorld - 1) - gap * 0.9f
            paint.shader = worldSh[k] ?: LinearGradient(0f, top, 0f, bottom, P.alpha(worldColors[k % worldColors.size], 70), P.alpha(worldColors[k % worldColors.size], 150), Shader.TileMode.CLAMP).also { worldSh[k] = it }
            canvas.drawRect(0f, maxOf(0f, top), w, bottom, paint)
            paint.shader = null
            text.color = P.alpha(0xFFFFFF, 220)
            text.textSize = context.dpf(15f)
            canvas.drawText("${k + 1} · " + worldNames[k % worldNames.size].uppercase(), w / 2f, maxOf(0f, top) + context.dpf(26f), text)
        }
        path.reset()
        path.moveTo(nx(0), ny(0))
        for (i in 1 until count) {
            val my = (ny(i - 1) + ny(i)) / 2f
            path.cubicTo(nx(i - 1), my, nx(i), my, nx(i), ny(i))
        }
        paint.style = Paint.Style.STROKE
        paint.strokeCap = Paint.Cap.ROUND
        paint.strokeWidth = context.dpf(16f)
        paint.color = 0x33000000
        canvas.save(); canvas.translate(0f, context.dpf(4f)); canvas.drawPath(path, paint); canvas.restore()
        paint.color = 0xFFF4E4C9.toInt()
        canvas.drawPath(path, paint)
        paint.strokeWidth = context.dpf(5f)
        paint.color = 0x66FFFFFF
        canvas.drawPath(path, paint)
        paint.style = Paint.Style.FILL
        for (i in 0 until count) drawNode(canvas, i)
    }

    private fun drawNode(canvas: Canvas, i: Int) {
        val x = nx(i)
        val y = ny(i)
        val r = node
        val open = i < unlocked
        val current = i == unlocked - 1
        val col = worldColors[(i / perWorld) % worldColors.size]
        val lift = if (current) abs(sin(phase)) * context.dpf(5f) else 0f
        paint.shader = shadowSh[i] ?: RadialGradient(x, y + r * 0.9f, r * 1.3f, 0x66000000, 0, Shader.TileMode.CLAMP).also { shadowSh[i] = it }
        canvas.save(); canvas.scale(1f, 0.35f, x, y + r * 0.9f); canvas.drawCircle(x, y + r * 0.9f, r * 1.3f, paint); canvas.restore()
        val base = if (open) col else 0xFF9A9AA6.toInt()
        paint.shader = null
        paint.color = darken(base)
        canvas.drawCircle(x, y - lift + r * 0.28f, r, paint)
        paint.shader = if (current) RadialGradient(x - r * 0.35f, y - lift - r * 0.4f, r * 1.4f, lighten(base), base, Shader.TileMode.CLAMP)
        else bodySh[i] ?: RadialGradient(x - r * 0.35f, y - r * 0.4f, r * 1.4f, lighten(base), base, Shader.TileMode.CLAMP).also { bodySh[i] = it }
        canvas.drawCircle(x, y - lift, r, paint)
        paint.shader = null
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = context.dpf(2.5f)
        paint.color = 0xCCFFFFFF.toInt()
        canvas.drawCircle(x, y - lift, r, paint)
        paint.style = Paint.Style.FILL
        if (current) {
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = context.dpf(3f)
            paint.color = P.alpha(0xFFD23A, (160 + 90 * sin(phase)).toInt())
            canvas.drawCircle(x, y - lift, r + context.dpf(6f) + sin(phase) * context.dpf(3f), paint)
            paint.style = Paint.Style.FILL
        }
        text.color = 0xFFFFFFFF.toInt()
        text.textSize = if (open) r * 0.95f else r * 0.8f
        canvas.drawText(if (open) "${i + 1}" else "🔒", x, y - lift + text.textSize * 0.35f, text)
        if (current) {
            text.textSize = r * 1.1f
            canvas.drawText("🧸", x + r * 1.5f, y - lift - r * 0.6f, text)
        }
        val s = stars(i)
        if (open && s >= 0) {
            text.textSize = r * 0.62f
            canvas.drawText("★".repeat(s) + "☆".repeat(3 - s), x, y + r * 1.75f, text.apply { color = 0xFFFFD23A.toInt() })
        }
    }

    private fun lighten(c: Int) = blend(c, 0xFFFFFFFF.toInt(), 0.45f)
    private fun darken(c: Int) = blend(c, 0xFF000000.toInt(), 0.35f)

    private fun blend(a: Int, b: Int, t: Float): Int {
        fun ch(s: Int) = ((a shr s and 255) * (1 - t) + (b shr s and 255) * t).toInt()
        return (0xFF shl 24) or (ch(16) shl 16) or (ch(8) shl 8) or ch(0)
    }
}
