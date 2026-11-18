package com.example.buddy.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View
import com.example.buddy.ui.home.Branch
import com.example.buddy.ui.home.Ease
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.random.Random

class ShatterOverlay(c: Context) : View(c) {
    private val cracks = Branch(CAPACITY)
    private val glow = line(6f, 0x6638D4FF)
    private val core = line(2f, 0xFFF4FCFF.toInt())
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x30CFE6FF }
    private val edge = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 2f; strokeJoin = Paint.Join.ROUND }
    private val sheen = line(3f, 0xFFFFFFFF.toInt())
    private val path = Path()
    private val vx = FloatArray(FRAGS * 8)
    private val vn = IntArray(FRAGS)
    private val cx = FloatArray(FRAGS)
    private val cy = FloatArray(FRAGS)
    private val dx = FloatArray(FRAGS)
    private val dy = FloatArray(FRAGS)
    private val rot = FloatArray(FRAGS)
    private val delay = FloatArray(FRAGS)
    private var frags = 0
    private var stage = 0
    private var ox = 0f
    private var oy = 0f
    private var lastTap = 0L
    private var stageAt = 0L
    private var from = 0f
    private var to = 0f
    private var finalAt = 0L
    private var healAt = 0L
    private var seed = 0

    private fun line(w: Float, col: Int) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = w
        strokeCap = Paint.Cap.ROUND
        color = col
    }

    fun tap(x: Float, y: Float) {
        if (!Motion.enabled(context)) return
        val now = android.os.SystemClock.uptimeMillis()
        if (finalAt != 0L) return
        if (stage > 0 && now - lastTap > WINDOW_MS) clear()
        lastTap = now
        if (stage == 0) {
            ox = x
            oy = y
            seed = Random.nextInt()
            cracks.build(seed, 0f, 0f, RAYS, CRACK_LEN * width / 390f)
            from = 0f
        } else {
            from = current(now)
        }
        stage++
        stageAt = now
        healAt = 0L
        to = TARGETS[stage - 1]
        if (stage == 3) {
            finalAt = now
            buildFragments()
            buzz(true)
        } else {
            buzz(false)
        }
        invalidate()
    }

    private fun clear() {
        stage = 0
        finalAt = 0L
        healAt = 0L
        from = 0f
        to = 0f
        frags = 0
    }

    private fun buzz(big: Boolean) {
        val k = if (big && Build.VERSION.SDK_INT >= 30) HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.CLOCK_TICK
        performHapticFeedback(k)
    }

    private fun current(now: Long): Float = Ease.lerp(from, to, Ease.smooth(((now - stageAt) / GROW_MS.toFloat()).coerceIn(0f, 1f)))

    private fun buildFragments() {
        val rng = Random(seed + 1)
        val k = width / 390f
        val n = FRAGS / 2
        frags = 0
        for (i in 0 until n) {
            val a0 = TAU * i / n + (rng.nextFloat() - 0.5f) * 0.18f
            val a1 = TAU * (i + 1) / n + (rng.nextFloat() - 0.5f) * 0.18f
            val r1 = (60f + rng.nextFloat() * 40f) * k
            val r2 = (240f + rng.nextFloat() * 120f) * k
            put(0f, 0f, cos(a0) * r1, sin(a0) * r1, cos(a1) * r1, sin(a1) * r1, rng)
            put(cos(a0) * r1, sin(a0) * r1, cos(a1) * r1, sin(a1) * r1, cos(a1) * r2, sin(a1) * r2, cos(a0) * r2, sin(a0) * r2, rng)
        }
    }

    private fun put(x0: Float, y0: Float, x1: Float, y1: Float, x2: Float, y2: Float, rng: Random) = place(floatArrayOf(x0, y0, x1, y1, x2, y2), rng)

    private fun put(x0: Float, y0: Float, x1: Float, y1: Float, x2: Float, y2: Float, x3: Float, y3: Float, rng: Random) = place(floatArrayOf(x0, y0, x1, y1, x2, y2, x3, y3), rng)

    private fun place(p: FloatArray, rng: Random) {
        if (frags >= FRAGS) return
        val n = p.size / 2
        var mx = 0f
        var my = 0f
        for (j in 0 until n) { mx += p[j * 2]; my += p[j * 2 + 1] }
        mx /= n
        my /= n
        val f = frags++
        vn[f] = n
        for (j in 0 until n) {
            vx[f * 8 + j * 2] = p[j * 2] - mx
            vx[f * 8 + j * 2 + 1] = p[j * 2 + 1] - my
        }
        cx[f] = mx
        cy[f] = my
        val len = hypot(mx, my).coerceAtLeast(1f)
        val dist = (0.35f + rng.nextFloat() * 0.8f) * width * 0.6f
        dx[f] = if (len < 2f) (rng.nextFloat() - 0.5f) * dist else mx / len * dist
        dy[f] = if (len < 2f) (rng.nextFloat() - 0.5f) * dist else my / len * dist + dist * 0.25f
        rot[f] = (rng.nextFloat() - 0.5f) * 420f
        delay[f] = rng.nextFloat() * 0.18f
    }

    override fun onDraw(c: Canvas) {
        if (stage == 0) return
        val now = android.os.SystemClock.uptimeMillis()
        var reveal = current(now)
        var fade = 1f
        if (finalAt == 0L && now - lastTap > WINDOW_MS) {
            if (healAt == 0L) healAt = now
            val h = ((now - healAt) / HEAL_MS.toFloat()).coerceIn(0f, 1f)
            fade = 1f - h
            if (h >= 1f) { clear(); return }
        }
        c.save()
        c.translate(ox, oy)
        var jitter = 0f
        if (finalAt != 0L) {
            val t = now - finalAt
            val total = ANTICIPATE_MS + FLY_MS + RESTORE_MS
            if (t >= total) { clear(); c.restore(); return }
            reveal = 1.2f
            if (t < ANTICIPATE_MS) jitter = sin(t * 0.09f) * 2.2f * (t / ANTICIPATE_MS.toFloat())
            if (t >= ANTICIPATE_MS + FLY_MS) fade = 1f - ((t - ANTICIPATE_MS - FLY_MS) / RESTORE_MS.toFloat())
            if (t >= ANTICIPATE_MS) drawFragments(c, (t - ANTICIPATE_MS) / FLY_MS.toFloat().coerceAtLeast(1f), fade)
        }
        c.translate(jitter, 0f)
        glow.alpha = (0x66 * fade).toInt().coerceIn(0, 255)
        core.alpha = (255 * fade).toInt().coerceIn(0, 255)
        cracks.draw(c, glow, reveal)
        cracks.draw(c, core, reveal)
        c.restore()
        postInvalidateDelayed(FRAME_MS)
    }

    private fun drawFragments(c: Canvas, tt: Float, fade: Float) {
        val t = tt.coerceIn(0f, 1.2f)
        for (f in 0 until frags) {
            val q = ((t - delay[f]) / (1f - delay[f] - 0.05f)).coerceIn(0f, 1f)
            val m = Ease.hump(q)
            c.save()
            c.translate(cx[f] + dx[f] * m, cy[f] + dy[f] * m)
            c.rotate(rot[f] * m)
            path.rewind()
            val n = vn[f]
            path.moveTo(vx[f * 8], vx[f * 8 + 1])
            for (j in 1 until n) path.lineTo(vx[f * 8 + j * 2], vx[f * 8 + j * 2 + 1])
            path.close()
            fill.alpha = ((0x30 + 0x30 * m) * fade).toInt().coerceIn(0, 255)
            c.drawPath(path, fill)
            val hi = 0.5f + 0.5f * sin(rot[f] * m * 0.05f + f)
            edge.color = 0xFFFFFFFF.toInt()
            edge.alpha = ((120 + 135 * hi) * fade).toInt().coerceIn(0, 255)
            c.drawPath(path, edge)
            sheen.alpha = (200 * hi * m * fade).toInt().coerceIn(0, 255)
            c.drawLine(vx[f * 8], vx[f * 8 + 1], vx[f * 8 + 2], vx[f * 8 + 3], sheen)
            c.restore()
        }
    }

    override fun onDetachedFromWindow() {
        clear()
        super.onDetachedFromWindow()
    }

    private companion object {
        const val CAPACITY = 640
        const val FRAGS = 32
        const val RAYS = 8
        const val CRACK_LEN = 150f
        const val WINDOW_MS = 2600L
        const val GROW_MS = 380L
        const val HEAL_MS = 700L
        const val ANTICIPATE_MS = 380L
        const val FLY_MS = 2000L
        const val RESTORE_MS = 450L
        const val FRAME_MS = 33L
        const val TAU = 6.2831855f
        val TARGETS = floatArrayOf(0.4f, 0.8f, 1.2f)
    }
}
