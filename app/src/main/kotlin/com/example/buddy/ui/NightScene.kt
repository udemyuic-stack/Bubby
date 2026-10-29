package com.example.buddy.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Native animated background of the Night look, ported from assets/scene/night.html: two aurora bands drifting
 * sideways, a glowing moon, 70 twinkling stars and two shooting stars. Transparent over the app's own gradient.
 * The blurred auroras are baked once (in [onSize]) into small bitmaps; per frame they are just scaled and faded.
 * Every motion is a pure function of the scene time t; nothing is allocated while drawing.
 */
class NightScene : ThemeScene() {
    override val ready = true

    // ---- the page's deterministic hash ----
    private fun hash(i: Int, k: Int): Float {
        val x = Math.sin(i * 12.9898 + k * 78.233) * 43758.5453
        return (x - Math.floor(x)).toFloat()
    }

    // ---- CSS "ease-in-out" = cubic-bezier(.42,0,.58,1), tabulated once ----
    private val ease = FloatArray(257).also { lut ->
        for (i in 0..256) {
            val x = i / 256.0
            var s = x
            for (n in 0 until 12) {
                val u = 1 - s
                val bx = 3 * u * u * s * .42 + 3 * u * s * s * .58 + s * s * s
                val dx = 3 * u * u * .42 + 6 * u * s * (.58 - .42) + 3 * s * s * (1 - .58)
                if (dx < 1e-6) break
                s -= (bx - x) / dx
                s = s.coerceIn(0.0, 1.0)
            }
            val u = 1 - s
            lut[i] = (3 * u * s * s + s * s * s).toFloat()
        }
    }
    private fun easeIO(x: Float): Float {
        val f = x.coerceIn(0f, 1f) * 256f
        val i = f.toInt().coerceAtMost(255)
        return ease[i] + (ease[i + 1] - ease[i]) * (f - i)
    }

    // ---- stars ----
    private val nStar = 70
    private val starX = FloatArray(nStar) { hash(it, 2) }
    private val starY = FloatArray(nStar) { hash(it, 3) }
    private val starS = FloatArray(nStar) { 1f + hash(it, 1) * 2.2f }
    private val starDur = FloatArray(nStar) { 2f + hash(it, 4) * 4f }
    private val starOff = FloatArray(nStar) { hash(it, 5) * 6f }
    private val starNear = FloatArray(nStar) { 1f + .4f * (starS[it] - 1f) / 2.2f }

    private val starPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL; color = 0xFFFFFFFF.toInt() }
    private val shootPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val moonPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val aurPaint = Paint(Paint.FILTER_BITMAP_FLAG)
    private var glowFrom: RadialGradient? = null
    private var glowTo: RadialGradient? = null
    private var glowFromR = 0f
    private var glowToR = 0f

    // ---- auroras: baked bitmap (1/BAKE resolution), element size and centre ----
    private val BAKE = 4
    private val aurBmp = arrayOfNulls<Bitmap>(2)
    private val aurCx = FloatArray(2)      // element centre, px
    private val aurCy = FloatArray(2)
    private val aurOx = FloatArray(2)      // element centre inside the bitmap, in bitmap pixels
    private val aurOy = FloatArray(2)
    private val aurDur = floatArrayOf(14f, 19f)
    private val aurOff = floatArrayOf(0f, 6f)

    private var dens = 1f
    private var moonCx = 0f
    private var moonCy = 0f

    private fun erfc(x: Double): Double {
        val z = abs(x)
        val t = 1.0 / (1.0 + .5 * z)
        val r = t * exp(-z * z - 1.26551223 + t * (1.00002368 + t * (.37409196 + t * (.09678418 + t * (-.18628806 +
            t * (.27886807 + t * (-1.13520398 + t * (1.48851587 + t * (-.82215223 + t * .17087277)))))))))
        return if (x >= 0) r else 2.0 - r
    }

    /** Gaussian-blurred disc (box-shadow with [blur] and [spread] in dp) as a radial gradient that ends at 0. */
    private fun glow(d: Float, blur: Float, spread: Float, alpha: Float, outR: FloatArray, slot: Int): RadialGradient {
        val r0 = (23f + spread) * d
        val sigma = blur / 2f * d
        val rr = r0 + 3f * sigma
        outR[slot] = rr
        val n = 14
        val cols = IntArray(n)
        val pos = FloatArray(n)
        for (k in 0 until n) {
            val dist = rr * k / (n - 1)
            var a = alpha * .5 * erfc((dist - r0) / (sigma * 1.41421356))
            if (k == n - 1) a = 0.0
            pos[k] = k / (n - 1f)
            cols[k] = (((a * 255).roundToInt().coerceIn(0, 255)) shl 24) or 0xA0AFFF
        }
        return RadialGradient(0f, 0f, rr, cols, pos, Shader.TileMode.CLAMP)
    }

    private val tmpR = FloatArray(2)

    private fun boxPass(src: FloatArray, dst: FloatArray, w: Int, h: Int, r: Int, horizontal: Boolean) {
        val inv = 1f / (2 * r + 1)
        val lines = if (horizontal) h else w
        val len = if (horizontal) w else h
        val step = if (horizontal) 1 else w
        for (l in 0 until lines) {
            val base = if (horizontal) l * w else l
            var sum = 0f
            for (x in 0..min(r, len - 1)) sum += src[base + x * step]
            for (x in 0 until len) {
                dst[base + x * step] = sum * inv
                val add = x + 1 + r
                if (add < len) sum += src[base + add * step]
                val sub = x - r
                if (sub >= 0) sum -= src[base + sub * step]
            }
        }
    }

    /** One blurred aurora ellipse, rendered at 1/BAKE size with a gaussian (3 box passes) of sigma 30 css px. */
    private fun bake(ewPx: Float, ehPx: Float, density: Float, c: IntArray, al: FloatArray): Bitmap {
        val sigma = 30f * density
        val pad = ceil(3f * sigma).toInt()
        val bw = ceil((ewPx + 2 * pad) / BAKE).toInt()
        val bh = ceil((ehPx + 2 * pad) / BAKE).toInt()
        val n = bw * bh
        val ch = Array(4) { FloatArray(n) }   // premultiplied r, g, b, a
        val rx = ewPx / 2f
        val ry = ehPx / 2f
        val edge = min(rx, ry) / BAKE
        for (iy in 0 until bh) {
            val y = (iy + .5f) * BAKE - pad
            val ny = (y - ry) / ry
            for (ix in 0 until bw) {
                val x = (ix + .5f) * BAKE - pad
                val nx = (x - rx) / rx
                val cov = ((1f - sqrt(nx * nx + ny * ny)) * edge + .5f).coerceIn(0f, 1f)
                if (cov <= 0f) continue
                // linear-gradient(90deg, c0, c1, c2) in premultiplied space
                val gx = (x / ewPx).coerceIn(0f, 1f)
                val seg = if (gx < .5f) 0 else 1
                val f = if (gx < .5f) gx * 2f else (gx - .5f) * 2f
                val a0 = al[seg]; val a1 = al[seg + 1]
                val c0 = c[seg]; val c1 = c[seg + 1]
                val a = a0 + (a1 - a0) * f
                val rr = ((c0 shr 16 and 255) * a0 + ((c1 shr 16 and 255) * a1 - (c0 shr 16 and 255) * a0) * f) / 255f
                val gg = ((c0 shr 8 and 255) * a0 + ((c1 shr 8 and 255) * a1 - (c0 shr 8 and 255) * a0) * f) / 255f
                val bb = ((c0 and 255) * a0 + ((c1 and 255) * a1 - (c0 and 255) * a0) * f) / 255f
                val o = iy * bw + ix
                ch[0][o] = rr * cov; ch[1][o] = gg * cov; ch[2][o] = bb * cov; ch[3][o] = a * cov
            }
        }
        // gaussian ~ 3 box blurs (sigma in bitmap pixels)
        val s = sigma / BAKE
        var wl = floor(sqrt(4.0 * s * s + 1.0)).toInt()
        if (wl % 2 == 0) wl--
        if (wl < 1) wl = 1
        val m = (((12.0 * s * s - 3.0 * wl * wl - 12.0 * wl - 9.0) / (-4.0 * wl - 4.0))).roundToInt()
        val tmp = FloatArray(n)
        for (k in 0 until 4) {
            for (pass in 0 until 3) {
                val size = if (pass < m) wl else wl + 2
                val r = (size - 1) / 2
                boxPass(ch[k], tmp, bw, bh, r, true)
                boxPass(tmp, ch[k], bw, bh, r, false)
            }
        }
        val px = IntArray(n)
        for (o in 0 until n) {
            val a = ch[3][o]
            if (a <= 1e-4f) { px[o] = 0; continue }
            val ai = (a * 255f).roundToInt().coerceIn(0, 255)
            val r = (ch[0][o] / a * 255f).roundToInt().coerceIn(0, 255)
            val g = (ch[1][o] / a * 255f).roundToInt().coerceIn(0, 255)
            val b = (ch[2][o] / a * 255f).roundToInt().coerceIn(0, 255)
            px[o] = (ai shl 24) or (r shl 16) or (g shl 8) or b
        }
        val bmp = Bitmap.createBitmap(bw, bh, Bitmap.Config.ARGB_8888)
        bmp.setPixels(px, 0, bw, 0, 0, bw, bh)
        return bmp
    }

    override fun onSize(w: Float, h: Float, density: Float) {
        dens = density
        // aurora 1: left -10% top 6% 70% x 22%; aurora 2: left 30% top 14% 80% x 18%
        aurBmp[0]?.recycle(); aurBmp[1]?.recycle()
        val e1w = .7f * w; val e1h = .22f * h
        aurBmp[0] = bake(e1w, e1h, density, intArrayOf(0x5A6EFF, 0x7864FF, 0x3CC8FF), floatArrayOf(0f, .8f, 0f))
        aurCx[0] = -.1f * w + e1w / 2f; aurCy[0] = .06f * h + e1h / 2f
        aurOx[0] = (ceil(3f * 30f * density) + e1w / 2f) / BAKE; aurOy[0] = (ceil(3f * 30f * density) + e1h / 2f) / BAKE
        val e2w = .8f * w; val e2h = .18f * h
        aurBmp[1] = bake(e2w, e2h, density, intArrayOf(0x3CDCC8, 0x5AA0FF, 0xB464FF), floatArrayOf(0f, .7f, 0f))
        aurCx[1] = .3f * w + e2w / 2f; aurCy[1] = .14f * h + e2h / 2f
        aurOx[1] = (ceil(3f * 30f * density) + e2w / 2f) / BAKE; aurOy[1] = (ceil(3f * 30f * density) + e2h / 2f) / BAKE

        // moon: right 12%, top 9%, 46 px; radial-gradient(circle at 35% 35%, #fff, #dfe4ff 60%, #aab4ee), farthest-corner
        val md = 46f * density
        moonCx = w - .12f * w - md / 2f
        moonCy = .09f * h + md / 2f
        moonPaint.shader = RadialGradient(-.15f * md, -.15f * md, .919f * md,
            intArrayOf(0xFFFFFFFF.toInt(), 0xFFDFE4FF.toInt(), 0xFFAAB4EE.toInt()), floatArrayOf(0f, .6f, 1f), Shader.TileMode.CLAMP)
        // glow: box-shadow 0 0 30px 8px rgba(160,175,255,.3)  <->  0 0 50px 16px rgba(160,175,255,.55), crossfaded
        glowFrom = glow(density, 30f, 8f, .3f, tmpR, 0); glowFromR = tmpR[0]
        glowTo = glow(density, 50f, 16f, .55f, tmpR, 1); glowToR = tmpR[1]

        // shooting star: 110 x 2 px, transparent -> white towards the head
        shootPaint.shader = LinearGradient(-110f * density, 0f, 0f, 0f, 0x00FFFFFF, 0xFFFFFFFF.toInt(), Shader.TileMode.CLAMP)
    }

    override fun draw(cv: Canvas, w: Float, h: Float, t: Float, dt: Float, page: Float) {
        val dn = dens
        val shift = -page * 8f * dn

        // 1. aurora bands: alternate (ease-in-out): translateX(-30px) scale(1,.9) op .35  <->  translateX(30px) scale(1.1,1.1) op .7
        for (i in 0 until 2) {
            val bmp = aurBmp[i] ?: continue
            var u = (t + aurOff[i]) / aurDur[i]
            u -= 2f * floor(u / 2f)
            val e = easeIO(if (u < 1f) u else 2f - u)
            aurPaint.alpha = ((.35f + .35f * e) * 255f).toInt()
            cv.save()
            cv.translate(aurCx[i] + (-30f + 60f * e) * dn + shift, aurCy[i])
            cv.scale((1f + .1f * e) * BAKE, (.9f + .2f * e) * BAKE)
            cv.drawBitmap(bmp, -aurOx[i], -aurOy[i], aurPaint)
            cv.restore()
        }

        // 2. moon with its breathing glow (6 s, alternate, ease-in-out)
        run {
            var u = t / 6f
            u -= 2f * floor(u / 2f)
            val e = easeIO(if (u < 1f) u else 2f - u)
            cv.save()
            cv.translate(moonCx + shift, moonCy)
            glowPaint.shader = glowFrom
            glowPaint.alpha = ((1f - e) * .9f * 255f).toInt()
            cv.drawCircle(0f, 0f, glowFromR, glowPaint)
            glowPaint.shader = glowTo
            glowPaint.alpha = (e * .9f * 255f).toInt()
            cv.drawCircle(0f, 0f, glowToR, glowPaint)
            moonPaint.alpha = (.9f * 255f).toInt()
            cv.drawCircle(0f, 0f, 23f * dn, moonPaint)
            cv.restore()
        }

        // 3. stars: tw keyframes, 0%,100%: op .15 scale .6   50%: op 1 scale 1.25
        for (i in 0 until nStar) {
            var u = (t + starOff[i]) / starDur[i]
            u -= floor(u)
            val e = easeIO(if (u < .5f) u * 2f else 2f - u * 2f)
            var x = starX[i] * w + shift * starNear[i]
            x -= w * floor(x / w)                  // wraps, so the sky stays full when the page slides
            starPaint.alpha = ((.15f + .85f * e) * 255f).toInt()
            cv.drawCircle(x, starY[i] * h, starS[i] * dn * .5f * (.6f + .65f * e), starPaint)
        }

        // 4. shooting stars: shoot keyframes (linear): 0%: op 0  3%: op 1  12%: translate(260,118) op 0, then idle until 100%
        for (i in 0 until 2) {
            val dur = 8f + i * 5f
            val delay = i * 3.5f
            if (t < delay) continue
            var u = (t - delay) / dur
            u -= floor(u)
            if (u >= .12f) continue
            val op = if (u < .03f) u / .03f else 1f - (u - .03f) / .09f
            val f = u / .12f
            cv.save()
            // rotate(24deg) about the head: the element's right end
            cv.translate((8f + i * 30f) / 100f * w + 110f * dn + 260f * dn * f + shift * 1.4f, (4f + i * 14f) / 100f * h + 1f * dn + 118f * dn * f)
            cv.rotate(24f)
            shootPaint.alpha = (op * 255f).toInt()
            cv.drawRect(-110f * dn, -1f * dn, 0f, 1f * dn, shootPaint)
            cv.restore()
        }
    }
}
