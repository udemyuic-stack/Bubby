package com.example.buddy.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.util.SparseIntArray
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

class BeatView(c: Context, private val game: BeatGame, private val clock: () -> Float) : View(c) {
    private val chart = game.chart
    private val rank = chart.rank
    private val lanes = rank.lanes
    private val look = 1.9f - BeatTracks.ranks.indexOf(rank) * 0.1f
    private val laneColors = intArrayOf(0xFF7DF9FF.toInt(), 0xFFFF7BE0.toInt(), 0xFFFFD23A.toInt(), 0xFF8DFF9B.toInt())

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = glassFont(bold = true); textAlign = Paint.Align.CENTER }
    private val path = Path()
    private val rect = RectF()

    private val sx = FloatArray(MAX_SPARKS)
    private val sy = FloatArray(MAX_SPARKS)
    private val svx = FloatArray(MAX_SPARKS)
    private val svy = FloatArray(MAX_SPARKS)
    private val slife = FloatArray(MAX_SPARKS)
    private val scol = IntArray(MAX_SPARKS)
    private var spark = 0
    private val padFlash = FloatArray(4)
    private val pointerLane = SparseIntArray()

    private var bg: LinearGradient? = null
    private var glow: RadialGradient? = null
    private var hy = 0f
    private var hitY = 0f
    private var cx = 0f
    private var roadW = 0f
    private var laneW = 0f
    private var padTop = 0f
    private var first = 0
    private var t = 0f
    private var judge = ""
    private var judgeColor = 0
    private var judgeT = 0f

    var haptic = true
    var onHide: (() -> Unit)? = null
    private var wantRun = false
    private var running = false
    private var last = 0L
    private val tick = Runnable { step() }

    fun play(on: Boolean) { wantRun = on; sync() }

    private fun sync() {
        val go = wantRun && isAttachedToWindow && windowVisibility == VISIBLE
        if (go == running) return
        running = go
        if (go) { last = System.nanoTime(); postOnAnimation(tick) } else removeCallbacks(tick)
    }

    fun onGameEvent(e: BeatGame.Ev, lane: Int) {
        when (e) {
            BeatGame.Ev.PERFECT -> { show("PERFECT", 0xFFFFD23A.toInt()); burst(lane, 14); if (haptic) performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP) }
            BeatGame.Ev.GOOD -> { show("GOOD", 0xFF7DF9FF.toInt()); burst(lane, 7) }
            BeatGame.Ev.MISS -> show("MISS", 0xFFFF5470.toInt())
            BeatGame.Ev.BREAK -> show("BREAK", 0xFFFF5470.toInt())
            else -> {}
        }
        if (lane in 0..3) padFlash[lane] = 1f
    }

    private fun show(s: String, color: Int) { judge = s; judgeColor = color; judgeT = 0.6f }

    private fun burst(lane: Int, n: Int) {
        val x = laneX(lane, 1f)
        val col = laneColors[lane % 4]
        for (i in 0 until n) {
            val k = spark++ % MAX_SPARKS
            val a = (Math.random() * Math.PI).toFloat()
            val v = 90f + (Math.random() * 220f).toFloat()
            sx[k] = x; sy[k] = hitY
            svx[k] = kotlin.math.cos(a) * v * (if (Math.random() < 0.5) -1 else 1)
            svy[k] = -kotlin.math.sin(a) * v
            slife[k] = 0.5f
            scol[k] = col
        }
    }

    private fun step() {
        if (!running) return
        val now = System.nanoTime()
        val dt = min((now - last) / 1e9f, 0.06f)
        if (dt >= 0.014f) {
            last = now
            t = clock()
            game.update(t)
            judgeT = max(0f, judgeT - dt)
            for (i in 0 until 4) padFlash[i] = max(0f, padFlash[i] - dt * 4f)
            for (k in 0 until MAX_SPARKS) {
                if (slife[k] <= 0f) continue
                slife[k] -= dt
                sx[k] += svx[k] * dt; sy[k] += svy[k] * dt; svy[k] += 600f * dt
            }
            while (first < chart.notes.size && chart.notes[first].let { it.time + it.len < t - 0.3f }) first++
            invalidate()
        }
        postOnAnimation(tick)
    }

    private fun depth(dt: Float) = max(0.7f, 1f + dt / look * (DEPTH - 1f))
    private fun yAt(d: Float) = hy + (hitY - hy) / d
    private fun laneX(i: Int, d: Float) = cx + (i + 0.5f - lanes / 2f) * laneW / d

    private fun laneAt(x: Float) = (((x - (cx - roadW / 2f)) / roadW) * lanes).toInt().coerceIn(0, lanes - 1)

    override fun onAttachedToWindow() { super.onAttachedToWindow(); sync() }
    override fun onDetachedFromWindow() { super.onDetachedFromWindow(); running = false; removeCallbacks(tick) }
    override fun onWindowVisibilityChanged(visibility: Int) {
        super.onWindowVisibilityChanged(visibility)
        if (visibility != VISIBLE && running) onHide?.invoke()
        sync()
    }

    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) {
        val padH = max(resources.displayMetrics.density * 56f, h * 0.15f)
        padTop = h - padH - 4f
        hitY = padTop - 10f
        hy = h * 0.2f
        cx = w / 2f
        roadW = w * 0.9f
        laneW = roadW / lanes
        bg = LinearGradient(0f, 0f, 0f, h.toFloat(), rank.top, rank.bottom, Shader.TileMode.CLAMP)
        glow = RadialGradient(cx, hy, w * 0.55f, (rank.glow and 0xFFFFFF) or 0x66000000, rank.glow and 0xFFFFFF, Shader.TileMode.CLAMP)
    }

    override fun onDraw(cv: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        paint.shader = bg
        cv.drawRect(0f, 0f, w, h, paint)
        paint.shader = null

        val beat = chart.beatSec
        val phase = if (t < 0f) 0f else (t / beat) - floor(t / beat)
        val pulse = (1f - phase) * (1f - phase)

        paint.shader = glow
        paint.alpha = (110 + 100 * pulse).toInt().coerceAtMost(255)
        cv.drawCircle(cx, hy, w * 0.55f, paint)
        paint.shader = null
        paint.alpha = 255

        val r = w * (0.11f + 0.025f * pulse)
        paint.color = rank.glow
        cv.drawCircle(cx, hy - r * 0.2f, r, paint)
        paint.color = 0xFF1A1030.toInt()
        cv.drawCircle(cx - r * 0.35f, hy - r * 0.35f, r * 0.12f, paint)
        cv.drawCircle(cx + r * 0.35f, hy - r * 0.35f, r * 0.12f, paint)
        rect.set(cx - r * 0.3f, hy - r * 0.25f, cx + r * 0.3f, hy + r * 0.2f)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = r * 0.07f
        cv.drawArc(rect, 20f, 140f, false, paint)
        paint.style = Paint.Style.FILL

        paint.color = 0xCC000010.toInt()
        path.reset()
        path.moveTo(cx - roadW / 2f / DEPTH, hy)
        path.lineTo(cx + roadW / 2f / DEPTH, hy)
        path.lineTo(cx + roadW / 2f, hitY + 6f)
        path.lineTo(cx - roadW / 2f, hitY + 6f)
        path.close()
        cv.drawPath(path, paint)

        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2f
        paint.color = (rank.glow and 0xFFFFFF) or 0x55000000
        for (i in 0..lanes) {
            val xf = cx + (i - lanes / 2f) * laneW
            cv.drawLine(cx + (xf - cx) / DEPTH, hy, xf, hitY + 6f, paint)
        }
        val scroll = if (t < 0f) 0f else (t / beat) - floor(t / beat)
        for (k in 0 until 7) {
            val d = 1f + ((k - scroll + 7f) % 7f) / 7f * (DEPTH - 1f)
            val y = yAt(d)
            val half = roadW / 2f / d
            paint.alpha = (30 + 60 / d).toInt().coerceAtMost(255)
            cv.drawLine(cx - half, y, cx + half, y, paint)
        }
        paint.alpha = 255
        paint.style = Paint.Style.FILL

        paint.color = 0xFFFFFFFF.toInt()
        paint.alpha = 200
        cv.drawRect(cx - roadW / 2f, hitY - 2f, cx + roadW / 2f, hitY + 4f, paint)
        paint.alpha = 255

        var last = chart.notes.size - 1
        while (last >= first && chart.notes[last].time - t > look) last--
        for (idx in last downTo first) {
            val n = chart.notes[idx]
            if (n.state == BeatNote.HIT) continue
            drawNote(cv, n)
        }

        for (i in 0 until lanes) {
            val down = game.isDown(i)
            val col = laneColors[i % 4]
            val a = (60 + 140 * padFlash[i % 4] + (if (down) 80 else 0)).toInt().coerceAtMost(255)
            paint.color = (col and 0xFFFFFF) or (a shl 24)
            rect.set(cx - roadW / 2f + i * laneW + 4f, padTop, cx - roadW / 2f + (i + 1) * laneW - 4f, height - 4f)
            cv.drawRoundRect(rect, 18f, 18f, paint)
        }

        for (k in 0 until MAX_SPARKS) {
            if (slife[k] <= 0f) continue
            paint.color = (scol[k] and 0xFFFFFF) or (min(1f, slife[k] * 2f) * 255f).toInt().shl(24)
            cv.drawCircle(sx[k], sy[k], 5f, paint)
        }

        if (t < chart.leadBeats * beat) {
            val remaining = chart.leadBeats - floor(t / beat).toInt()
            if (remaining in 1..3) {
                textPaint.color = Color.WHITE
                textPaint.textSize = h * 0.2f
                cv.drawText(remaining.toString(), cx, h * 0.5f, textPaint)
            }
        }

        if (judgeT > 0f) {
            textPaint.color = judgeColor
            textPaint.alpha = (judgeT / 0.6f * 255).toInt().coerceIn(0, 255)
            textPaint.textSize = h * 0.07f + (0.6f - judgeT) * 20f
            cv.drawText(judge, cx, h * 0.42f, textPaint)
        }
        if (game.combo >= 5) {
            textPaint.color = Color.WHITE
            textPaint.alpha = 220
            textPaint.textSize = h * 0.05f
            cv.drawText("${game.combo} COMBO", cx, h * 0.5f + h * 0.08f, textPaint)
        }
    }

    private fun drawNote(cv: Canvas, n: BeatNote) {
        val held = n.state == BeatNote.HELD
        val d = if (held) 1f else depth(n.time - t)
        val col = laneColors[n.lane % 4]
        val dim = n.state == BeatNote.MISSED || n.state == BeatNote.BROKEN
        val x = laneX(n.lane, d)
        val y = yAt(d)
        if (n.len > 0f) {
            val d2 = depth(n.time + n.len - t)
            val y2 = yAt(d2)
            val w1 = laneW * 0.3f / d
            val w2 = laneW * 0.3f / d2
            val x2 = laneX(n.lane, d2)
            paint.color = (col and 0xFFFFFF) or ((if (dim) 60 else 150) shl 24)
            path.reset()
            path.moveTo(x - w1, y)
            path.lineTo(x + w1, y)
            path.lineTo(x2 + w2, y2)
            path.lineTo(x2 - w2, y2)
            path.close()
            cv.drawPath(path, paint)
        }
        val hw = laneW * 0.42f / d
        val hh = laneW * 0.16f / d
        paint.color = (col and 0xFFFFFF) or ((if (dim) 90 else 255) shl 24)
        rect.set(x - hw, y - hh, x + hw, y + hh)
        cv.drawRoundRect(rect, hh, hh, paint)
        paint.color = 0x66FFFFFF
        rect.set(x - hw * 0.8f, y - hh * 0.7f, x + hw * 0.8f, y - hh * 0.1f)
        cv.drawRoundRect(rect, hh * 0.5f, hh * 0.5f, paint)
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        parent?.requestDisallowInterceptTouchEvent(true)
        if (!running) {
            if (e.actionMasked == MotionEvent.ACTION_UP || e.actionMasked == MotionEvent.ACTION_POINTER_UP) pointerLane.delete(e.getPointerId(e.actionIndex))
            if (e.actionMasked == MotionEvent.ACTION_CANCEL) pointerLane.clear()
            return true
        }
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                val i = e.actionIndex
                val lane = laneAt(e.getX(i))
                pointerLane.put(e.getPointerId(i), lane)
                game.tap(lane, clock())
                padFlash[lane % 4] = max(padFlash[lane % 4], 0.5f)
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> {
                val i = e.actionIndex
                val lane = pointerLane.get(e.getPointerId(i), -1)
                if (lane >= 0) game.release(lane, clock())
                pointerLane.delete(e.getPointerId(i))
            }
            MotionEvent.ACTION_CANCEL -> {
                for (k in 0 until pointerLane.size()) game.release(pointerLane.valueAt(k), clock())
                pointerLane.clear()
            }
        }
        return true
    }

    companion object {
        const val DEPTH = 6f
        const val MAX_SPARKS = 48
    }
}
