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
import android.view.MotionEvent
import android.view.View
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

class RunnerView(c: Context, private val game: RunnerGame) : View(c) {
    private class Spark(var x: Float, var y: Float, var vx: Float, var vy: Float, var life: Float, val max: Float, val color: Int, val r: Float)
    private class Star(val x: Float, var y: Float, val r: Float, val phase: Float)

    private class Look(val top: Int, val bottom: Int, val nebula: Int, val c1: Int, val c2: Int)

    private val looks = arrayOf(
        Look(0xFF070B2A.toInt(), 0xFF2A1458.toInt(), 0x8246FF, 0xFFFFB36B.toInt(), 0xFF9A4A1F.toInt()),
        Look(0xFF04202B.toInt(), 0xFF0B4F5C.toInt(), 0x00C8FF, 0xFF7FD4FF.toInt(), 0xFF1B4FA0.toInt()),
        Look(0xFF2A0A28.toInt(), 0xFF5C1A4A.toInt(), 0xFF3CAA, 0xFFFF7A6B.toInt(), 0xFF6B1E1A.toInt()),
        Look(0xFF081F14.toInt(), 0xFF14513A.toInt(), 0x3CE6A0, 0xFF8DFF9B.toInt(), 0xFF1D6B4A.toInt())
    )

    private val rnd = Random(7)
    private val stars = Array(90) { Star(rnd.nextFloat() * 390f, rnd.nextFloat() * 700f, 0.5f + rnd.nextFloat() * 1.2f, rnd.nextFloat() * 6.3f) }
    private val sparks = ArrayList<Spark>()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = glassFont(bold = true); textAlign = Paint.Align.CENTER }
    private val rect = RectF()
    private val path = Path()
    private val orbColors = intArrayOf(0xFF7DF9FF.toInt(), 0xFFFF7BE0.toInt(), 0xFFFFD23A.toInt(), 0xFF8DFF9B.toInt(), 0xFFB79BFF.toInt())

    private var sc = 1f
    private var lh = 700f
    private var hy = 140f
    private var py = 640f
    private var laneF = 1f
    private var scroll = 0f
    private var shake = 0f
    private var flash = 0f
    private var flashColor = 0
    private var toast = ""
    private var toastT = 9f
    private var builtFor = -1
    private var bgPaint = Paint()
    private var nebulaPaint = Paint()
    private var planetPaint = Paint()
    private var glowPaint = Paint()

    private var wantRun = false
    private var running = false
    private var last = 0L
    private val tick = Runnable { step() }

    fun play(on: Boolean) {
        wantRun = on
        sync()
    }

    fun toast(s: String) { toast = s; toastT = 0f }

    fun onGameEvent(e: RunnerGame.Ev) {
        val x = shipX()
        when (e) {
            RunnerGame.Ev.CORRECT -> { flash(0xFF50FFAA.toInt(), 0.3f); burst(x, py - 30f, 0xFF8DFF9B.toInt(), 22) }
            RunnerGame.Ev.WRONG -> flash(0xFFFF4646.toInt(), 0.25f)
            RunnerGame.Ev.HIT -> { shake = 0.6f; flash(0xFFFF4646.toInt(), 0.35f); burst(x, py, 0xFFFF8A5B.toInt(), 40) }
            RunnerGame.Ev.SHIELD_BREAK -> { shake = 0.3f; flash(0xFF7DF9FF.toInt(), 0.3f); burst(x, py, 0xFF7DF9FF.toInt(), 30) }
            RunnerGame.Ev.ORB -> burst(x, py - 20f, 0xFFFFD23A.toInt(), 10)
            RunnerGame.Ev.POWER_READY, RunnerGame.Ev.BOSS_CLEAR -> { flash(0xFFFFD23A.toInt(), 0.3f); burst(x, py - 40f, 0xFFFFD23A.toInt(), 30) }
            RunnerGame.Ev.OVER -> burst(x, py, 0xFFFFFFFF.toInt(), 60)
            else -> {}
        }
    }

    private fun flash(color: Int, a: Float) { flashColor = color; flash = a }

    private fun burst(x: Float, y: Float, color: Int, n: Int) {
        for (i in 0 until n) {
            val a = rnd.nextFloat() * 6.283f
            val v = 30f + rnd.nextFloat() * 220f
            val l = 0.4f + rnd.nextFloat() * 0.8f
            sparks.add(Spark(x, y, cos(a) * v, sin(a) * v - 40f, l, l, color, 1.5f + rnd.nextFloat() * 3f))
        }
        if (sparks.size > 260) sparks.subList(0, sparks.size - 260).clear()
    }

    private fun shipX() = 195f + (laneF - 1f) * 110f

    private fun sync() {
        val go = wantRun && isAttachedToWindow && windowVisibility == VISIBLE
        if (go == running) return
        running = go
        if (go) { last = System.nanoTime(); postOnAnimation(tick) } else removeCallbacks(tick)
    }

    private fun step() {
        if (!running) return
        val now = System.nanoTime()
        val dt = (now - last) / 1e9f
        if (dt >= 0.030f) {
            last = now
            val d = min(dt, 0.06f)
            game.update(d)
            animate(d)
            invalidate()
        }
        postOnAnimation(tick)
    }

    private fun animate(dt: Float) {
        laneF += (game.lane - laneF) * min(1f, dt * 12f)
        val sp = game.speed()
        scroll = (scroll + sp * dt) % 2f
        for (s in stars) { s.y += sp * 14f * dt; if (s.y > lh) s.y -= lh }
        shake = maxOf(0f, shake - dt)
        flash = maxOf(0f, flash - dt)
        toastT += dt
        val it = sparks.iterator()
        while (it.hasNext()) {
            val p = it.next()
            p.life -= dt
            if (p.life <= 0f) { it.remove(); continue }
            p.x += p.vx * dt; p.y += p.vy * dt; p.vy += 260f * dt
        }
    }

    override fun onAttachedToWindow() { super.onAttachedToWindow(); sync() }
    override fun onDetachedFromWindow() { super.onDetachedFromWindow(); running = false; removeCallbacks(tick) }
    var onHide: (() -> Unit)? = null

    override fun onWindowVisibilityChanged(visibility: Int) {
        super.onWindowVisibilityChanged(visibility)
        if (visibility != VISIBLE && running) onHide?.invoke()
        sync()
    }

    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) {
        sc = w / 390f
        lh = h / sc
        hy = lh * 0.2f
        py = lh - 70f
        builtFor = -1
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        if (e.actionMasked == MotionEvent.ACTION_DOWN || e.actionMasked == MotionEvent.ACTION_MOVE) {
            game.setLane((e.x / (width / 3f)).toInt())
            return true
        }
        return e.actionMasked == MotionEvent.ACTION_UP
    }

    private fun look() = looks[game.sector % looks.size]

    private fun build(idx: Int) {
        val l = looks[idx]
        bgPaint = Paint().apply { shader = LinearGradient(0f, 0f, 0f, lh, l.top, l.bottom, Shader.TileMode.CLAMP) }
        nebulaPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = RadialGradient(90f, hy + 60f, 200f, (0x52 shl 24) or l.nebula, l.nebula, Shader.TileMode.CLAMP)
        }
        planetPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = RadialGradient(300f, hy * 0.7f, 70f, l.c1, l.c2, Shader.TileMode.CLAMP)
        }
        glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = RadialGradient(150f, lh - 20f, 260f, (0x44 shl 24) or l.nebula, l.nebula and 0xFFFFFF, Shader.TileMode.CLAMP)
        }
        builtFor = idx
    }

    private fun depth(z: Float) = 3f / (3f + z)
    private fun yAt(s: Float) = hy + (py - hy) * s
    private fun xAt(lane: Float, s: Float) = 195f + (lane - 1f) * 110f * s

    override fun onDraw(canvas: Canvas) {
        val idx = game.sector % looks.size
        if (builtFor != idx) build(idx)
        canvas.save()
        canvas.scale(sc, sc)
        if (shake > 0f) canvas.translate(sin(game.time * 90f) * shake * 8f, cos(game.time * 70f) * shake * 6f)
        paint.shader = null
        canvas.drawRect(-20f, -20f, 410f, lh + 20f, bgPaint)
        canvas.drawCircle(90f, hy + 60f, 200f, nebulaPaint)
        canvas.drawCircle(300f, hy * 0.7f, 70f, planetPaint)
        canvas.drawCircle(150f, lh - 20f, 260f, glowPaint)
        drawStars(canvas)
        drawFloor(canvas)
        val g = game.gate
        val things = game.items
        for (i in things.indices.reversed()) drawItem(canvas, things[i])
        if (g != null) drawGate(canvas, g)
        drawShip(canvas)
        drawSparks(canvas)
        drawToast(canvas)
        canvas.restore()
        if (flash > 0f) {
            paint.color = flashColor
            paint.alpha = (flash * 255f).toInt().coerceIn(0, 255)
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
            paint.alpha = 255
        }
    }

    private fun drawStars(canvas: Canvas) {
        paint.style = Paint.Style.FILL
        for (s in stars) {
            val tw = 0.5f + 0.5f * sin(game.time * 2f + s.phase)
            paint.color = 0xFFFFFFFF.toInt()
            paint.alpha = (90 + 140 * tw).toInt()
            canvas.drawCircle(s.x, s.y, s.r, paint)
        }
        paint.alpha = 255
    }

    private fun drawFloor(canvas: Canvas) {
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1.4f
        paint.color = 0xFFFFFFFF.toInt()
        for (off in floatArrayOf(-1.5f, -0.5f, 0.5f, 1.5f)) {
            paint.alpha = 46
            canvas.drawLine(xAt(1f + off, 0.02f), yAt(0.02f), xAt(1f + off, 1.3f), yAt(1.3f), paint)
        }
        for (k in 0 until 8) {
            val z = ((k * 2f - scroll * 1f) % 16f + 16f) % 16f
            val s = depth(z)
            paint.alpha = (70 * s).toInt()
            canvas.drawLine(xAt(-0.5f, s), yAt(s), xAt(2.5f, s), yAt(s), paint)
        }
        paint.alpha = 255
        paint.style = Paint.Style.FILL
    }

    private fun drawItem(canvas: Canvas, it: RunnerGame.Item) {
        if (it.spent && it.z < -0.4f) return
        val s = depth(it.z)
        val x = xAt(it.laneAt(), s)
        val y = yAt(s) - 30f * s
        paint.style = Paint.Style.FILL
        val fade = if (it.z < 0f) (1f + it.z / 0.4f).coerceIn(0f, 1f) else 1f
        if (it.rock) {
            val r = 28f * s
            paint.color = if (it.mul > 1f) 0xFF9C5560.toInt() else 0xFF6F6A7C.toInt(); paint.alpha = (255 * fade).toInt()
            canvas.save()
            canvas.rotate(game.time * 40f + it.lane * 50f, x, y)
            canvas.drawCircle(x, y, r, paint)
            paint.color = 0xFF4A465A.toInt(); paint.alpha = (255 * fade).toInt()
            canvas.drawCircle(x - r * 0.3f, y - r * 0.2f, r * 0.28f, paint)
            canvas.drawCircle(x + r * 0.35f, y + r * 0.3f, r * 0.2f, paint)
            canvas.restore()
        } else {
            val r = 13f * s
            val c = orbColors[(it.lane + (game.time * 3f).toInt()) % orbColors.size]
            paint.color = c; paint.alpha = (90 * fade).toInt()
            canvas.drawCircle(x, y, r * 1.9f, paint)
            paint.color = c; paint.alpha = (255 * fade).toInt()
            canvas.drawCircle(x, y, r, paint)
            paint.color = 0xFFFFFFFF.toInt(); paint.alpha = (200 * fade).toInt()
            canvas.drawCircle(x - r * 0.3f, y - r * 0.3f, r * 0.3f, paint)
        }
        paint.alpha = 255
    }

    private fun drawGate(canvas: Canvas, g: RunnerGame.Gate) {
        if (g.z < -0.9f) return
        val s = depth(g.z)
        val al = if (g.z < 0f) maxOf(0f, 1f + g.z / 0.9f) else 1f
        val y = yAt(s)
        for (i in 0 until 3) {
            val cx = xAt(i.toFloat(), s)
            val cy = y - 62f * s
            val w = 100f * s
            val h = 52f * s
            rect.set(cx - w / 2f, cy - h / 2f, cx + w / 2f, cy + h / 2f)
            var fill = 0x1AFFFFFF
            var stroke = 0x99FFFFFF.toInt()
            if (g.boss) stroke = 0xFFFFD23A.toInt()
            if (g.done) {
                if (i == g.correct) { fill = 0x4778FFAF; stroke = 0xF28CFFBE.toInt() }
                else if (i == g.pick) { fill = 0x47FF6464; stroke = 0xF2FF8282.toInt() }
            } else if (i == game.lane) fill = 0x38FFFFFF
            val dim = if (!g.done && i == game.hint) 0.25f else 1f
            paint.style = Paint.Style.FILL
            paint.color = fill
            paint.alpha = (Color.alpha(fill) * al * dim).toInt()
            canvas.drawRoundRect(rect, 14f * s, 14f * s, paint)
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = (if (i == game.lane && !g.done) 3f else 1.6f) * s.coerceAtLeast(0.5f)
            paint.color = stroke
            paint.alpha = (Color.alpha(stroke) * al * dim).toInt()
            canvas.drawRoundRect(rect, 14f * s, 14f * s, paint)
            textPaint.color = 0xFFFFFFFF.toInt()
            textPaint.alpha = (255 * al * dim).toInt()
            textPaint.textSize = 22f * s
            canvas.drawText(g.options[i].uppercase(), cx, cy + 8f * s, textPaint)
        }
        paint.style = Paint.Style.FILL
        paint.alpha = 255
    }

    private fun drawShip(canvas: Canvas) {
        val x = shipX()
        val y = py + sin(game.time * 3.2f) * 5f
        canvas.save()
        canvas.rotate((game.lane - laneF) * 14f, x, y)
        paint.style = Paint.Style.FILL
        paint.color = 0xFFFFB347.toInt()
        paint.alpha = (150 + 100 * abs(sin(game.time * 24f))).toInt()
        canvas.drawCircle(x, y + 26f, 8f, paint)
        paint.alpha = 255
        path.reset()
        path.moveTo(x, y - 30f)
        path.lineTo(x + 22f, y + 20f)
        path.lineTo(x, y + 10f)
        path.lineTo(x - 22f, y + 20f)
        path.close()
        paint.color = look().c1
        canvas.drawPath(path, paint)
        paint.color = 0xFFFFFFFF.toInt()
        canvas.drawCircle(x, y - 6f, 5f, paint)
        if (game.shield) {
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 3f
            paint.color = 0xFF7DF9FF.toInt()
            paint.alpha = (150 + 100 * abs(sin(game.time * 6f))).toInt()
            canvas.drawCircle(x, y, 40f, paint)
            paint.alpha = 255
            paint.style = Paint.Style.FILL
        }
        canvas.restore()
    }

    private fun drawSparks(canvas: Canvas) {
        paint.style = Paint.Style.FILL
        for (p in sparks) {
            paint.color = p.color
            paint.alpha = (255 * (p.life / p.max)).toInt().coerceIn(0, 255)
            canvas.drawCircle(p.x, p.y, p.r, paint)
        }
        paint.alpha = 255
    }

    private fun drawToast(canvas: Canvas) {
        if (toastT > 1.4f || toast.isEmpty()) return
        val a = if (toastT < 0.2f) toastT / 0.2f else if (toastT > 1f) 1f - (toastT - 1f) / 0.4f else 1f
        val pop = 1f + 0.25f * maxOf(0f, 1f - toastT * 4f)
        textPaint.color = 0xFFFFFFFF.toInt()
        textPaint.alpha = (255 * a).toInt().coerceIn(0, 255)
        textPaint.textSize = 30f * pop
        canvas.drawText(toast, 195f, lh * 0.42f, textPaint)
    }
}
