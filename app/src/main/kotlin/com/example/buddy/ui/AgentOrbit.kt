package com.example.buddy.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.view.View
import kotlin.math.cos
import kotlin.math.sin

/** "Many agents, one island": the mascot in the middle and every agent as a small coloured cube on a ring around it. */
class AgentOrbit(c: Context, private val items: List<Item>) : View(c) {
    class Item(val name: String, val color: Int, val state: String)

    private val d = c.resources.displayMetrics.density
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val critter = GlassCritter().also { it.withHalo = false }

    private fun busy(s: String) = s in setOf("working", "thinking", "searching", "approval", "question")

    override fun onDraw(cv: Canvas) {
        val w = width.toFloat(); val h = height.toFloat()
        val cx = w / 2f; val cy = h / 2f - 4f * d
        val t = android.os.SystemClock.uptimeMillis() / 1000.0
        val spin = Math.toRadians(t * 7.0)   // one turn in about 51 s
        val r = minOf(w, h) / 2f - 34f * d
        // rings and tick marks
        p.style = Paint.Style.STROKE; p.strokeWidth = 1f * d; p.color = Theme.rgb(Glass.TEXT2, 46)
        cv.drawCircle(cx, cy, r, p)
        cv.drawCircle(cx, cy, r * .66f, p)
        p.color = Theme.rgb(Glass.TEXT2, 30)
        for (i in 0 until 72) {
            val a = Math.toRadians(i * 5.0) - spin * .6   // the ticks drift the other way, slower
            val r0 = r * .72f; val r1 = r * (if (i % 6 == 0) .8f else .77f)
            cv.drawLine((cx + cos(a) * r0).toFloat(), (cy + sin(a) * r0).toFloat(), (cx + cos(a) * r1).toFloat(), (cy + sin(a) * r1).toFloat(), p)
        }
        // the mascot in the middle on its dark patch
        p.style = Paint.Style.FILL
        val core = r * .5f
        p.shader = RadialGradient(cx, cy, core * 1.5f, intArrayOf(Theme.rgb(Theme.cur.dark, 245), Theme.rgb(Theme.cur.dark, 170), 0), floatArrayOf(0f, .62f, 1f), Shader.TileMode.CLAMP)
        cv.drawCircle(cx, cy, core * 1.5f, p); p.shader = null
        cv.save()
        val k = core * 2f / 170f
        cv.translate(cx - 85f * k, cy - 68f * k); cv.scale(k, k)
        critter.draw(cv)
        cv.restore()
        // the agents
        val n = items.size
        for ((i, it) in items.withIndex()) {
            val a = -Math.PI / 2 + i * 2 * Math.PI / maxOf(n, 1) + spin
            val x = (cx + cos(a) * r).toFloat(); val y = (cy + sin(a) * r).toFloat()
            val bw = 36f * d; val bh = 29f * d
            if (busy(it.state)) {
                p.shader = RadialGradient(x, y, bw * 1.1f, intArrayOf(Theme.rgb(it.color, 120), 0), null, Shader.TileMode.CLAMP)
                cv.drawCircle(x, y, bw * 1.1f, p); p.shader = null
            }
            val box = RectF(x - bw / 2, y - bh / 2, x + bw / 2, y + bh / 2)
            p.shader = LinearGradient(0f, box.top, 0f, box.bottom, it.color, Color.rgb((Color.red(it.color) * .72f).toInt(), (Color.green(it.color) * .72f).toInt(), (Color.blue(it.color) * .72f).toInt()), Shader.TileMode.CLAMP)
            cv.drawRoundRect(box, bh * .42f, bh * .42f, p); p.shader = null
            p.color = Color.argb(90, 255, 255, 255)
            cv.drawOval(RectF(box.left + bw * .12f, box.top + bh * .1f, box.left + bw * .55f, box.top + bh * .3f), p)
            p.color = 0xFF1A1F2C.toInt()
            cv.drawCircle(x - bw * .16f, y + bh * .06f, 2.3f * d, p); cv.drawCircle(x + bw * .16f, y + bh * .06f, 2.3f * d, p)
            // name
            p.color = Theme.rgb(if (Glass.NIGHT) it.color else Glass.TEXT, 255)
            p.textAlign = Paint.Align.CENTER; p.textSize = 11.5f * d; p.typeface = glassFont(bold = true)
            cv.drawText(it.name.take(14), x, y + bh / 2 + 13f * d, p)
        }
        postInvalidateOnAnimation()
    }
}
