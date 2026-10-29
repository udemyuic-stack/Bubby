package com.example.buddy.ui

import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import kotlin.math.floor
import kotlin.math.sin

/**
 * Native animated background of the Glass look, ported from assets/scene/glass.html: 12 translucent orbs rising with a
 * sideways drift, two slow light sweeps and 26 drifting dust motes. Transparent over the app's own gradient.
 * Every motion is a pure function of the scene time t; nothing is allocated while drawing.
 */
class GlassScene : ThemeScene() {
    override val ready = true

    // ---- the page's deterministic hash (same numbers as the web page) ----
    private fun hash(i: Int, k: Int): Float {
        val x = Math.sin(i * 12.9898 + k * 78.233) * 43758.5453
        return (x - Math.floor(x)).toFloat()
    }

    // ---- CSS "ease-in-out" = cubic-bezier(.42,0,.58,1), tabulated once ----
    private val ease = FloatArray(257).also { lut ->
        for (i in 0..256) {
            val x = i / 256.0
            var s = x
            for (n in 0 until 12) {   // Newton on the bezier's x(s)
                val u = 1 - s
                val bx = 3 * u * u * s * .42 + 3 * u * s * s * .58 + s * s * s
                val dx = 3 * u * u * .42 + 6 * u * s * (.58 - .42) + 3 * s * s * (1 - .58)
                if (dx < 1e-6) break
                s -= (bx - x) / dx
                s = s.coerceIn(0.0, 1.0)
            }
            val u = 1 - s
            lut[i] = (3 * u * s * s + s * s * s).toFloat()   // y(s): control y values 0 and 1
        }
    }
    private fun easeIO(x: Float): Float {
        val f = x.coerceIn(0f, 1f) * 256f
        val i = f.toInt().coerceAtMost(255)
        return ease[i] + (ease[i + 1] - ease[i]) * (f - i)
    }

    // ---- the elements (CSS values; dp for px, fractions for %) ----
    private val nOrb = 12
    private val orbLeft = FloatArray(nOrb) { hash(it, 2) }
    private val orbD = FloatArray(nOrb) { 22f + hash(it, 1) * 60f }
    private val orbOx = FloatArray(nOrb) { hash(it, 3) * 70f - 35f }
    private val orbDur = FloatArray(nOrb) { 16f + hash(it, 4) * 18f }
    private val orbOff = FloatArray(nOrb) { hash(it, 5) * 24f }   // negative animation-delay = phase offset
    private val orbShader = arrayOfNulls<RadialGradient>(nOrb)

    private val nDust = 26
    private val dustX = FloatArray(nDust) { hash(it, 7) }
    private val dustY = FloatArray(nDust) { hash(it, 8) }
    private val dustS = FloatArray(nDust) { 1.5f + hash(it, 6) * 2f }
    private val dustDx = FloatArray(nDust) { hash(it, 9) * 40f - 20f }
    private val dustDy = FloatArray(nDust) { hash(it, 10) * -50f }
    private val dustDur = FloatArray(nDust) { 5f + hash(it, 11) * 6f }
    private val dustOff = FloatArray(nDust) { hash(it, 12) * 8f }

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; color = 0xFFFFFFFF.toInt() }
    private val dust = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL; color = 0xFFFFFFFF.toInt() }
    private val sweepA = Paint(Paint.ANTI_ALIAS_FLAG)
    private val sweepB = Paint(Paint.ANTI_ALIAS_FLAG)
    private var sweepW = 0f

    private var dens = 1f

    override fun onSize(w: Float, h: Float, density: Float) {
        dens = density
        ring.strokeWidth = 1.2f * density
        for (i in 0 until nOrb) {
            val d = orbD[i] * density
            // radial-gradient(circle at 32% 28%, white .55, white .12 55%, (200,220,245) .18); farthest-corner radius
            orbShader[i] = RadialGradient(-.18f * d, -.22f * d, .9904f * d,
                intArrayOf(0x8CFFFFFF.toInt(), 0x1FFFFFFF, 0x2EC8DCF5), floatArrayOf(0f, .55f, 1f), Shader.TileMode.CLAMP)
        }
        // light sweep: 34% wide, 130% tall, horizontal transparent - white .28 - transparent, rotated 18 degrees
        sweepW = .34f * w
        val g = LinearGradient(-sweepW / 2, 0f, sweepW / 2, 0f, intArrayOf(0x00FFFFFF, 0x47FFFFFF, 0x00FFFFFF), null, Shader.TileMode.CLAMP)
        sweepA.shader = g
        sweepB.shader = g
        sweepB.alpha = (.7f * 255f).toInt()
    }

    private fun sweep(cv: Canvas, w: Float, h: Float, t: Float, left: Float, delay: Float, p: Paint, shift: Float) {
        val u = ((t + delay) / 16f).let { it - floor(it) }
        val e = easeIO(if (u < .5f) u * 2f else 2f - 2f * u)      // 0%,100%: -120%   50%: 330% (ease-in-out each half)
        val tx = (-1.2f + 4.5f * e) * sweepW
        cv.save()
        cv.translate(left * w + sweepW / 2f + tx + shift, .55f * h)
        cv.rotate(18f)
        cv.drawRect(-sweepW / 2f, -.65f * h, sweepW / 2f, .65f * h, p)
        cv.restore()
    }

    override fun draw(cv: Canvas, w: Float, h: Float, t: Float, dt: Float, page: Float) {
        val dn = dens
        val shift = -page * 8f * dn

        // 1. the two light sweeps (the far layer)
        sweep(cv, w, h, t, 0f, 0f, sweepA, shift)
        sweep(cv, w, h, t, .4f, 8f, sweepB, shift)

        // 2. orbs rising: keyframes up (0%: 0,0 scale .9 op 0 / 12%: op .85 / 50%: ox,-52vh scale 1 / 100%: -.6ox,-118vh scale 1.05 op 0)
        val vh = h / 100f
        for (i in 0 until nOrb) {
            var u = (t + orbOff[i]) / orbDur[i]
            u -= floor(u)
            val op = if (u < .12f) .85f * u / .12f else .85f * (1f - (u - .12f) / .88f)
            if (op < .004f) continue
            val tx: Float; val ty: Float; val sc: Float
            if (u < .5f) {
                val f = u * 2f
                tx = orbOx[i] * f; ty = -52f * vh * f; sc = .9f + .1f * f
            } else {
                val f = (u - .5f) * 2f
                tx = orbOx[i] * (1f - 1.6f * f); ty = (-52f - 66f * f) * vh; sc = 1f + .05f * f
            }
            val d = orbD[i] * dn
            val box = d + 2.4f * dn                       // content box + 1.2 px border on each side
            val near = 1f + .5f * (orbD[i] - 22f) / 60f   // bigger orbs read as nearer: they slide a little further
            val cx = orbLeft[i] * w + box / 2f + tx * dn + shift * near
            val cy = h + 60f * dn - box / 2f + ty
            cv.save()
            cv.translate(cx, cy)
            cv.scale(sc, sc)
            fill.shader = orbShader[i]
            fill.alpha = (op * 255f).toInt()
            cv.drawCircle(0f, 0f, d / 2f + 1.2f * dn, fill)
            ring.alpha = (op * .65f * 255f).toInt()
            cv.drawCircle(0f, 0f, d / 2f + .6f * dn, ring)
            cv.restore()
        }

        // 3. dust: keyframes dust (alternate, ease-in-out): translate (0,0) op .15  <->  (dx,dy) op .8
        for (i in 0 until nDust) {
            var u = (t + dustOff[i]) / dustDur[i]
            u -= 2f * floor(u / 2f)
            val e = easeIO(if (u < 1f) u else 2f - u)
            var x = dustX[i] * w + dustDx[i] * dn * e + shift
            x -= w * floor(x / w)                          // wraps, so the field stays full when the page slides
            val y = dustY[i] * h + dustDy[i] * dn * e
            dust.alpha = ((.15f + .65f * e) * .85f * 255f).toInt()
            cv.drawCircle(x, y, dustS[i] * dn / 2f, dust)
        }
    }
}
