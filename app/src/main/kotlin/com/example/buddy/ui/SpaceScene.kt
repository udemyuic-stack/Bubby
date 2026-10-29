package com.example.buddy.ui

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.sin

/**
 * Native animated background of the Space look, ported from assets/scene/space.html (390 x 844 design units, drawn like the page's
 * `slice` svg that is 12 dp larger than the screen on every side, at 72 % opacity): blurred nebulae, a spiral galaxy, 46 twinkling stars,
 * a black hole, an asteroid belt, four planets on tilted orbits, a comet, three shooting stars, a nova and the Earth with its moon.
 * Nothing is allocated while drawing; blurs are pre-rendered into small bitmaps, every motion is a function of t.
 */
class SpaceScene : ThemeScene() {
    override val ready = true

    private class Blob(val bmp: Bitmap, val hw: Float, val hh: Float, val rgb: Int)

    private val G = 0.72f                       // the page's svg opacity
    private var sc = 0f
    private var ox = 0f
    private var oy = 0f
    private var dens = 1f
    private val bitmaps = ArrayList<Bitmap>()
    private val m = Matrix()
    private val lm = Matrix()
    private val rect = RectF()

    private val pF = Paint(Paint.ANTI_ALIAS_FLAG)
    private val pS = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val pB = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val pSh = Paint(Paint.ANTI_ALIAS_FLAG)
    private val pDash1 = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 6f; color = 0xFFFF9A3A.toInt(); pathEffect = DashPathEffect(floatArrayOf(14f, 6f, 4f, 8f), 0f) }
    private val pDash2 = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 2f; color = 0xFFFFE08A.toInt(); pathEffect = DashPathEffect(floatArrayOf(6f, 10f), 0f) }

    // ---- static data (generated once from the page) ----
    private val starData = nums("65 332 3.008 -1.9 2.2 267 667 4.08 -1.1 3.2 57 233 4.184 -0.4 3.9 323 574 2.762 -1.6 3.1 298 565 4.027 -0.1 2 256 774 3.819 -2.3 4.3 230 250 1.508 -0.3 4 46 118 2.362 -1 3.6 55 465 1.534 -3 4.4 355 507 3.538 -2.7 3.6 112 411 2.255 -0.6 2.8 187 829 2.629 -2.3 3.9 267 658 1.729 -3.8 3.2 179 96 3.172 -2.9 2.3 154 299 2.872 -0.9 3.5 335 725 3.649 -3.8 4.2 163 30 4.043 -2 2.2 193 377 2.883 -2.7 4.3 51 413 4.173 -3.1 4.3 288 515 4.102 -1.2 3 222 523 3.908 -3.2 3.3 312 507 2.694 -0.8 2 273 269 4.033 -0.4 4.2 293 495 3.039 -0.2 2.9 17 594 2.174 -1.4 3.9 71 55 4.388 -0.1 3.3 272 691 1.808 -1.3 2.8 323 444 2.91 -1.3 2.3 251 239 4.049 -3.8 3.4 67 646 3.602 -3.2 2.4 350 485 3.644 -2.1 3.1 284 262 3.179 -1.3 3.5 347 320 3.144 -2.7 2.2 336 518 3.595 -1.6 2.5 210 448 2.108 -3.7 2.1 142 14 1.599 -2.2 4.1 340 616 4.382 -1.8 3.9 272 506 2.58 -0.8 3.8 107 271 3.578 -3.5 2.3 243 528 3.8 -2.1 4 259 832 2.803 -2.1 4.4 344 39 3.994 -0.6 4.1 167 473 4.25 -1.6 3.1 55 453 3.568 -1.7 2.2 268 270 2.859 -1.2 2.9 98 440 4.027 -1.1 3.2")
    private val galaxyData = nums("3.0 0.0 1.8 1.00 4.5 1.6 1.7 0.96 5.0 4.2 1.7 0.92 4.1 7.2 1.6 0.87 1.7 9.9 1.5 0.83 -2.1 11.6 1.4 0.79 -6.8 11.7 1.4 0.75 -11.7 9.7 1.3 0.71 -16.0 5.7 1.2 0.66 -18.7 -0.2 1.2 0.62 -19.2 -7.2 1.1 0.58 -16.9 -14.5 1.0 0.54 -11.8 -20.9 1.0 0.50 -4.2 -25.4 0.9 0.45 5.1 -27.0 0.8 0.41 15.0 -25.1 0.8 0.37 24.0 -19.6 0.7 0.33 30.9 -10.7 0.6 0.29 -3.0 0.0 1.8 1.00 -4.5 -1.6 1.7 0.96 -5.0 -4.2 1.7 0.92 -4.1 -7.2 1.6 0.87 -1.7 -9.9 1.5 0.83 2.1 -11.6 1.4 0.79 6.8 -11.7 1.4 0.75 11.7 -9.7 1.3 0.71 16.0 -5.7 1.2 0.66 18.7 0.2 1.2 0.62 19.2 7.2 1.1 0.58 16.9 14.5 1.0 0.54 11.8 20.9 1.0 0.50 4.2 25.4 0.9 0.45 -5.1 27.0 0.8 0.41 -15.0 25.1 0.8 0.37 -24.0 19.6 0.7 0.33 -30.9 10.7 0.6 0.29")
    private val beltData = nums("34.8 -181.0 0.9 -178.8 36.8 1.8 -177.3 20.1 1.3 55.4 -183.8 1.2 77.9 -176.5 0.9 22.8 193.7 1.6 136.6 -130.5 1.9 -160.5 104.7 1.0 110.8 -153.6 1.2 177.6 -44.2 1.6 103.5 156.0 1.3 -167.8 -82.0 2.2 146.9 121.0 1.1 -176.0 -64.3 1.8 40.3 177.4 0.8 -21.2 185.9 1.6 145.7 107.9 1.5 172.3 -50.2 1.4 -76.4 -175.2 1.9 -31.1 -177.0 2.1 -127.4 -135.7 2.2 -32.3 190.0 2.2 -46.5 176.3 1.0 -12.1 176.8 1.5 -164.8 102.2 1.1 30.8 -181.7 1.6 177.1 17.2 1.0 176.6 25.7 2.0 -73.6 -172.5 1.5 168.4 -71.5 2.0 34.9 -184.9 1.7 -106.1 -155.5 0.8 162.3 -83.1 1.3 39.7 181.0 1.3 191.9 -25.4 1.1 -75.1 -178.0 1.0 -81.3 -176.7 1.7 -149.4 125.2 1.0 -118.1 149.7 2.2 -162.7 68.3 1.5 -61.3 -171.2 2.0 185.4 -49.8 1.8 137.6 133.0 1.3 -81.4 176.2 1.7 -176.5 -47.9 1.7 -176.7 -71.0 1.3 143.2 122.4 2.1 131.4 120.8 1.0 -114.9 150.0 1.5 -98.5 -146.5 2.0 -161.5 -86.3 1.8 -134.9 120.3 1.4 -158.6 85.9 1.6 132.0 132.3 0.9 -102.7 -165.4 1.9 -74.4 -168.2 0.9 -5.6 178.9 1.5 179.6 26.6 1.2 -179.7 24.0 1.9 -77.1 179.0 1.1 -160.4 -107.0 1.3 -178.6 -61.7 1.5 180.8 -44.9 1.3 -18.0 187.8 2.1 5.2 -182.3 1.8 -96.4 -147.5 2.0 183.7 -25.4 1.4 186.3 -35.3 1.0 126.0 -148.3 1.9 152.8 -102.4 1.1 192.8 -29.1 1.8 21.1 191.4 1.3 -99.9 149.7 1.9 -119.5 146.1 1.4 -176.8 -56.0 1.9 45.1 -182.1 1.3 71.7 174.4 1.0 110.2 159.5 1.8 183.0 -58.9 1.7 96.8 -163.6 1.0 53.1 -181.2 1.2 158.4 92.6 2.0 -21.3 -177.5 1.2 -12.9 -195.5 2.0 179.1 20.7 1.4 109.0 -152.7 1.0 -175.2 -86.0 1.7 -142.8 133.2 1.1 -187.9 -19.6 2.2 101.7 156.0 1.6")
    private val lightData = nums("11.2 -6.5 19.9 -1.5 8.6 13.0 25.5 -4.5 25.7 22.5 31.6 19.0 32.8 10.7 22.2 -10.3 17.2 -4.8 32.0 13.3 8.3 5.7 14.6 -4.6 24.8 -0.9 28.2 1.9 31.1 -4.4 9.0 -9.9 11.1 19.2 15.0 -19.2 13.4 1.0 15.2 9.0 10.1 11.9 22.0 -19.5 10.5 -4.8 13.7 1.3 23.8 -12.1 16.0 6.1")
    // continents: cx cy rx ry rotation; clouds: cx cy rx ry (both patterns repeat every 80 units)
    private val landData = nums("-22 -12 14 9 -20  6 8 11 16 15  25 -20 9 6 0  -12 24 10 6 10  -36 14 6 9 0  30 22 7 5 30")
    private val cloudData = nums("-30 -26 14 3  -4 -4 18 3  22 14 12 3  -20 30 10 3")
    // nebulae: cx cy rx ry rgb alpha duration
    private val nebCx = floatArrayOf(80f, 320f, 230f, 60f)
    private val nebCy = floatArrayOf(520f, 380f, 700f, 120f)
    private val nebRx = floatArrayOf(150f, 130f, 170f, 110f)
    private val nebRy = floatArrayOf(90f, 80f, 70f, 60f)
    private val nebRgb = intArrayOf(0xFF9AD5, 0x7FD6FF, 0xB49CFF, 0xFFB36B)
    private val nebA = floatArrayOf(.55f, .5f, .55f, .35f)
    private val nebDur = floatArrayOf(30f, 36f, 42f, 34f)
    private val orbRx = floatArrayOf(220f, 270f, 320f, 370f)
    private val orbRy = floatArrayOf(60f, 76f, 92f, 108f)
    private val orbDur = floatArrayOf(34f, 48f, 62f, 80f)
    private val orbBegin = floatArrayOf(-6f, -20f, -40f, -10f)
    private val shX = floatArrayOf(330f, 220f, 370f)
    private val shY = floatArrayOf(300f, 470f, 620f)
    private val shDelay = floatArrayOf(0f, -2.6f, -5.1f)

    // ---- built in onSize ----
    private var nebBlob = arrayOfNulls<Blob>(4)
    private var glowBH: Blob? = null
    private var glowComet: Blob? = null
    private var glowNova: Blob? = null
    private var atmo: Blob? = null
    private var coreGal: Blob? = null
    private var galBmp: Bitmap? = null
    private var galBs = 1f
    private var beltBmp: Bitmap? = null
    private var beltBs = 1f
    private var starBmp: Bitmap? = null
    private var lightsBmp: Bitmap? = null
    private var earthBs = 1f
    private var landSh: BitmapShader? = null
    private var cloudSh: BitmapShader? = null
    private var oceanSh: Shader? = null
    private var shadeSh: Shader? = null
    private var jupSh: Shader? = null
    private var satSh: Shader? = null
    private var uraSh: Shader? = null
    private var nepSh: Shader? = null
    private var tail1: Shader? = null
    private var tail2: Shader? = null
    private val orbLut = arrayOfNulls<FloatArray>(4)
    private var moonLut = FloatArray(0)
    private val tailPath1 = Path().apply { moveTo(0f, 0f); lineTo(-120f, -34f); lineTo(-118f, -28f); close() }
    private val tailPath2 = Path().apply { moveTo(0f, 0f); lineTo(-90f, -16f); lineTo(-88f, -12f); close() }
    private val disc1 = Path().apply { addCircle(0f, 0f, 22f, Path.Direction.CW) }
    private val disc2 = Path().apply { addCircle(0f, 0f, 17f, Path.Direction.CW) }

    override fun onSize(w: Float, h: Float, density: Float) {
        if (w <= 0f || h <= 0f) return
        dens = density
        for (b in bitmaps) b.recycle()
        bitmaps.clear()
        val bw = w + 24f * density
        val bh = h + 24f * density
        sc = max(bw / 390f, bh / 844f)
        ox = -12f * density + (bw - 390f * sc) / 2f
        oy = -12f * density + (bh - 844f * sc) / 2f

        for (i in 0 until 4) nebBlob[i] = blob(nebRx[i], nebRy[i], 22f, nebRgb[i])
        glowBH = blob(26f, 26f, 4f, 0xFFB36B)
        glowComet = blob(4f, 4f, 4f, 0xFFFFFF)
        glowNova = blob(16f, 16f, 4f, 0xFFFFFF)
        atmo = blob(52f, 52f, 4f, 0x8FD0FF)
        coreGal = blob(5f, 5f, 4f, 0xFFF6D8)

        // galaxy: 36 dots in a 68 x 68 box
        galBs = sc
        val gb = mk(ceil(68f * galBs).toInt(), ceil(68f * galBs).toInt(), Bitmap.Config.ARGB_8888)
        run {
            val c = Canvas(gb)
            pF.shader = null
            var i = 0
            while (i < galaxyData.size) {
                pF.color = (a(galaxyData[i + 3]) shl 24) or 0xFFFFFF
                c.drawCircle((galaxyData[i] + 34f) * galBs, (galaxyData[i + 1] + 34f) * galBs, galaxyData[i + 2] * galBs, pF)
                i += 4
            }
        }
        galBmp = gb

        // asteroid belt: 90 dots in a 396 x 396 box, half resolution, one colour (tinted when drawn)
        beltBs = sc * 0.6f
        val bb = mk(ceil(396f * beltBs).toInt(), ceil(396f * beltBs).toInt(), Bitmap.Config.ALPHA_8)
        run {
            val c = Canvas(bb)
            pF.color = 0xFF000000.toInt()
            var i = 0
            while (i < beltData.size) {
                c.drawCircle((beltData[i] + 198f) * beltBs, (beltData[i + 1] + 198f) * beltBs, max(0.8f, beltData[i + 2] * beltBs), pF)
                i += 3
            }
        }
        beltBmp = bb

        // unit star (4 points, outer radius 30 px in a 64 px bitmap)
        val sb = mk(64, 64, Bitmap.Config.ALPHA_8)
        run {
            val c = Canvas(sb)
            val p = Path()
            p.moveTo(32f, 2f); p.lineTo(39.5f, 24.5f); p.lineTo(62f, 32f); p.lineTo(39.5f, 39.5f)
            p.lineTo(32f, 62f); p.lineTo(24.5f, 39.5f); p.lineTo(2f, 32f); p.lineTo(24.5f, 24.5f); p.close()
            pF.color = 0xFF000000.toInt()
            c.drawPath(p, pF)
        }
        starBmp = sb

        // earth tiles (80 x 80 units, repeat horizontally every 80)
        earthBs = sc
        val ts = ceil(80f * earthBs).toInt()
        val land = mk(ts, ts, Bitmap.Config.ARGB_8888)
        run {
            val c = Canvas(land)
            c.scale(earthBs, earthBs); c.translate(40f, 40f)
            pF.color = 0xFF4FA457.toInt()
            var i = 0
            while (i < landData.size) {
                for (dx in intArrayOf(-80, 0, 80)) {
                    c.save()
                    c.rotate(landData[i + 4], landData[i] + dx, landData[i + 1])
                    rect.set(landData[i] + dx - landData[i + 2], landData[i + 1] - landData[i + 3], landData[i] + dx + landData[i + 2], landData[i + 1] + landData[i + 3])
                    c.drawOval(rect, pF)
                    c.restore()
                }
                i += 5
            }
        }
        val cloud = mk(ts, ts, Bitmap.Config.ARGB_8888)
        run {
            val c = Canvas(cloud)
            c.scale(earthBs, earthBs); c.translate(40f, 40f)
            pF.color = 0xFFFFFFFF.toInt()
            var i = 0
            while (i < cloudData.size) {
                for (dx in intArrayOf(-80, 0, 80)) {
                    rect.set(cloudData[i] + dx - cloudData[i + 2], cloudData[i + 1] - cloudData[i + 3], cloudData[i] + dx + cloudData[i + 2], cloudData[i + 1] + cloudData[i + 3])
                    c.drawOval(rect, pF)
                }
                i += 4
            }
        }
        val lights = mk(ts, ts, Bitmap.Config.ARGB_8888)
        run {
            val c = Canvas(lights)
            c.scale(earthBs, earthBs); c.translate(40f, 40f)
            var i = 0
            while (i < lightData.size) {
                pF.color = 0x26FFE680
                c.drawCircle(lightData[i], lightData[i + 1], 2.4f, pF)
                pF.color = 0xFFFFE680.toInt()
                c.drawCircle(lightData[i], lightData[i + 1], 0.9f, pF)
                i += 2
            }
        }
        lightsBmp = lights
        landSh = BitmapShader(land, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT)
        cloudSh = BitmapShader(cloud, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT)
        oceanSh = RadialGradient(-9.6f, -12f, 64f, intArrayOf(0xFF7FD0FF.toInt(), 0xFF2F7FD6.toInt(), 0xFF174A9A.toInt()), floatArrayOf(0f, .6f, 1f), Shader.TileMode.CLAMP)
        shadeSh = RadialGradient(-16f, -16f, 72f, intArrayOf(0x000A0820, 0x000A0820, 0x990A0820.toInt()), floatArrayOf(0f, .55f, 1f), Shader.TileMode.CLAMP)

        jupSh = LinearGradient(0f, -12f, 0f, 12f,
            intArrayOf(0xFFE9D2B0.toInt(), 0xFFC9935E.toInt(), 0xFFF1E0C4.toInt(), 0xFFB9774A.toInt(), 0xFFEAD2AE.toInt(), 0xFFC58B58.toInt(), 0xFFE2C69C.toInt()),
            floatArrayOf(0f, .2f, .32f, .48f, .6f, .78f, 1f), Shader.TileMode.CLAMP)
        satSh = RadialGradient(-3f, -3f, 10f, 0xFFFFF0C8.toInt(), 0xFFC79A4E.toInt(), Shader.TileMode.CLAMP)
        uraSh = RadialGradient(-2.4f, -2.4f, 8f, 0xFFE2FBFF.toInt(), 0xFF5EC4D6.toInt(), Shader.TileMode.CLAMP)
        nepSh = RadialGradient(-2.4f, -2.4f, 8f, 0xFFBFE0FF.toInt(), 0xFF2F5FD0.toInt(), Shader.TileMode.CLAMP)
        tail1 = LinearGradient(0f, 0f, -120f, 0f, 0xF2E8F4FF.toInt(), 0x009FC8FF, Shader.TileMode.CLAMP)
        tail2 = LinearGradient(0f, 0f, -90f, 0f, 0xF2E8F4FF.toInt(), 0x009FC8FF, Shader.TileMode.CLAMP)
        for (i in 0 until 4) orbLut[i] = lut(orbRx[i], orbRy[i])
        moonLut = lut(64f, 18f)
    }

    private fun mk(w: Int, h: Int, cfg: Bitmap.Config): Bitmap {
        val b = Bitmap.createBitmap(max(1, w), max(1, h), cfg)
        bitmaps.add(b)
        return b
    }

    /** A gaussian-blurred filled ellipse (sigma in design units) as a small alpha mask, centred in a (2*hw x 2*hh) box. */
    private fun blob(rx: Float, ry: Float, sg: Float, rgb: Int): Blob {
        val hw = rx + 3f * sg
        val hh = ry + 3f * sg
        val lf = if (sg * sc >= 12f) 0.25f else if (sg * sc >= 6f) 0.5f else 1f
        val q = sc * lf
        val bw = max(2, ceil(2f * hw * q).toInt())
        val bh = max(2, ceil(2f * hh * q).toInt())
        val b = mk(bw, bh, Bitmap.Config.ALPHA_8)
        val c = Canvas(b)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.color = 0xFF000000.toInt()
        p.maskFilter = BlurMaskFilter(max(1f, (sg * q - 0.5f) / 0.57735f), BlurMaskFilter.Blur.NORMAL)
        val sx = bw / (2f * hw)
        val sy = bh / (2f * hh)
        rect.set((hw - rx) * sx, (hh - ry) * sy, (hw + rx) * sx, (hh + ry) * sy)
        c.drawOval(rect, p)
        return Blob(b, hw, hh, rgb)
    }

    /** Position-by-arc-length table of an ellipse run from the left point through the bottom (SVG sweep 0), like animateMotion. */
    private fun lut(rx: Float, ry: Float): FloatArray {
        val mm = 720
        val n = 180
        val cum = DoubleArray(mm + 1)
        var px = -rx.toDouble()
        var py = 0.0
        for (j in 1..mm) {
            val ph = PI - 2.0 * PI * j / mm
            val x = rx * cos(ph)
            val y = ry * sin(ph)
            cum[j] = cum[j - 1] + Math.hypot(x - px, y - py)
            px = x; py = y
        }
        val out = FloatArray(n + 1)
        var j = 0
        for (k in 0..n) {
            val target = cum[mm] * k / n
            while (j < mm - 1 && cum[j + 1] < target) j++
            val span = cum[j + 1] - cum[j]
            val fr = if (span > 0) (target - cum[j]) / span else 0.0
            out[k] = (PI - 2.0 * PI * (j + fr) / mm).toFloat()
        }
        return out
    }

    private fun phiAt(l: FloatArray, p: Float): Float {
        val f = p * (l.size - 1)
        val i = f.toInt().coerceIn(0, l.size - 2)
        return l[i] + (l[i + 1] - l[i]) * (f - i)
    }

    private fun drawBlob(cv: Canvas, b: Blob, cx: Float, cy: Float, k: Float, alpha: Float) {
        pB.color = (a(alpha) shl 24) or b.rgb
        m.setScale(2f * b.hw / b.bmp.width * k, 2f * b.hh / b.bmp.height * k)
        m.postTranslate(cx - b.hw * k, cy - b.hh * k)
        cv.drawBitmap(b.bmp, m, pB)
    }

    override fun draw(cv: Canvas, w: Float, h: Float, t: Float, dt: Float, page: Float) {
        if (sc == 0f) return
        val sh = page * 8f * dens

        // ---- far: nebulae, galaxy, stars ----
        cv.save(); cv.translate(ox - sh * 0.75f, oy); cv.scale(sc, sc)
        for (i in 0 until 4) {
            val q = ease(tri(t / nebDur[i]))
            drawBlob(cv, nebBlob[i]!!, nebCx[i] - 20f + 40f * q, nebCy[i] + 10f - 20f * q, 1f, nebA[i] * G)
        }
        // galaxy
        cv.save()
        cv.translate(336f, 84f); cv.scale(1f, 0.5f); cv.rotate(360f * frac(t / 40f))
        cv.save(); cv.scale(1f / galBs, 1f / galBs)
        pB.color = (a(G) shl 24) or 0xFFFFFF
        cv.drawBitmap(galBmp!!, -34f * galBs, -34f * galBs, pB)
        cv.restore()
        drawBlob(cv, coreGal!!, 0f, 0f, 1f, G)
        cv.restore()
        // stars
        val sBmp = starBmp!!
        var i = 0
        while (i < starData.size) {
            val q = ease(tri((t - starData[i + 3]) / starData[i + 4]))
            val k = starData[i + 2] * (0.4f + 0.75f * q) / 30f
            m.setTranslate(-32f, -32f)
            m.postScale(k, k)
            m.postRotate(45f * q)
            m.postTranslate(starData[i], starData[i + 1])
            pB.color = (a((0.4f + 0.6f * q) * G) shl 24) or 0xFFFFFF
            cv.drawBitmap(sBmp, m, pB)
            i += 5
        }
        cv.restore()

        // ---- middle: black hole, belt, orbits, comet, shooting stars, nova ----
        cv.save(); cv.translate(ox - sh, oy); cv.scale(sc, sc)
        // black hole
        drawBlob(cv, glowBH!!, 44f, 250f, 1f, 0.35f * G)
        cv.save(); cv.translate(44f, 250f)
        cv.save(); cv.scale(1f, 0.32f); cv.rotate(360f * frac(t / 5f))
        pDash1.alpha = a(0.9f * G); pDash2.alpha = a(G)
        cv.drawPath(disc1, pDash1)
        cv.drawPath(disc2, pDash2)
        cv.restore()
        pF.shader = null
        pF.color = (a(G) shl 24) or 0x0B0716
        cv.drawCircle(0f, 0f, 10f, pF)
        pS.strokeWidth = 1f; pS.color = (a(0.8f * G) shl 24) or 0xFFCF7A
        cv.drawCircle(0f, 0f, 10.6f, pS)
        cv.restore()
        // asteroid belt
        cv.save()
        cv.translate(195f, 190f); cv.rotate(-10f); cv.scale(1f, 0.25f); cv.rotate(360f * frac(t / 80f)); cv.scale(1f / beltBs, 1f / beltBs)
        pB.color = (a(G) shl 24) or 0xEFE6D8
        cv.drawBitmap(beltBmp!!, -198f * beltBs, -198f * beltBs, pB)
        cv.restore()
        // orbits and planets
        for (o in 0 until 4) {
            cv.save()
            cv.rotate(-10f, 195f, 190f)
            rect.set(195f - orbRx[o], 190f - orbRy[o], 195f + orbRx[o], 190f + orbRy[o])
            pS.strokeWidth = 0.8f; pS.color = (a(0.5f * G) shl 24) or 0xFFFFFF
            cv.drawOval(rect, pS)
            val phi = phiAt(orbLut[o]!!, frac((t - orbBegin[o]) / orbDur[o]))
            cv.translate(195f + orbRx[o] * cos(phi), 190f + orbRy[o] * sin(phi))
            when (o) {
                0 -> { pSh.shader = jupSh; pSh.alpha = a(G); cv.drawCircle(0f, 0f, 12f, pSh); planetRim(cv, 12f) }
                1 -> {
                    cv.save(); cv.rotate(-18f)
                    rect.set(-22f, -5f, 22f, 5f)
                    pS.strokeWidth = 3f; pS.color = (a(G) shl 24) or 0xE8D3A0
                    cv.drawOval(rect, pS)
                    cv.restore()
                    pSh.shader = satSh; pSh.alpha = a(G); cv.drawCircle(0f, 0f, 10f, pSh)
                    cv.save(); cv.rotate(-18f)
                    cv.drawArc(rect, 180f, -180f, false, pS)
                    cv.restore()
                }
                2 -> { pSh.shader = uraSh; pSh.alpha = a(G); cv.drawCircle(0f, 0f, 8f, pSh); planetRim(cv, 8f) }
                else -> { pSh.shader = nepSh; pSh.alpha = a(G); cv.drawCircle(0f, 0f, 8f, pSh); planetRim(cv, 8f) }
            }
            cv.restore()
        }
        // comet: crosses in the first 60 % of 16 s, then waits off screen
        val cu = frac(t / 16f) / 0.6f
        if (cu < 1f) {
            val cx = -40f + 500f * cu
            val cy = 120f + 180f * cu
            cv.save(); cv.translate(cx, cy)
            pSh.shader = tail1; pSh.alpha = a(G); cv.drawPath(tailPath1, pSh)
            pSh.shader = tail2; pSh.alpha = a(0.7f * G); cv.drawPath(tailPath2, pSh)
            cv.restore()
            drawBlob(cv, glowComet!!, cx, cy, 1f, G)
            pF.color = (a(G) shl 24) or 0xFFFFFF
            cv.drawCircle(cx, cy, 2.4f, pF)
        }
        // shooting stars: only the white head shows (the page's tail gradient is one pixel long)
        for (s in 0 until 3) {
            val ph = frac((t - shDelay[s]) / 7.5f)
            if (ph >= 0.10f) continue
            val q = easeOut(ph / 0.10f)
            val op = if (ph < 0.02f) easeOut(ph / 0.02f) else 1f - easeOut((ph - 0.02f) / 0.08f)
            pF.color = (a(op * G) shl 24) or 0xFFFFFF
            cv.drawCircle(shX[s] + 110f * q, shY[s] + 38f * q, 1.8f, pF)
        }
        // nova
        run {
            val ph = frac(t / 6f)
            var ns = 0.3f
            var no = 0.4f
            if (ph >= 0.7f && ph < 0.8f) { val u = ease((ph - 0.7f) / 0.1f); ns = 0.3f + 1.3f * u; no = 0.4f + 0.6f * u }
            else if (ph >= 0.8f) { val u = ease((ph - 0.8f) / 0.2f); ns = 1.6f - 1.3f * u; no = 1f - 0.6f * u }
            drawBlob(cv, glowNova!!, 350f, 470f, ns, no * G)
            if (ph >= 0.74f) {
                val u = easeOut((ph - 0.74f) / 0.26f)
                val rs = 0.2f + 3.8f * u
                val ro = if (ph < 0.78f) 0.9f * easeOut((ph - 0.74f) / 0.04f) else 0.9f * (1f - easeOut((ph - 0.78f) / 0.22f))
                pS.strokeWidth = 1.5f * rs; pS.color = (a(ro * G) shl 24) or 0xFFFFFF
                cv.drawCircle(350f, 470f, 10f * rs, pS)
            }
            pF.color = (a(G) shl 24) or 0xFFFFFF
            cv.drawCircle(350f, 470f, 2.2f, pF)
        }
        cv.restore()

        // ---- near: the Earth and its moon ----
        cv.save(); cv.translate(ox - sh * 1.25f, oy); cv.scale(sc, sc); cv.translate(84f, 368f)
        drawBlob(cv, atmo!!, 0f, 0f, 1f, 0.45f * G)
        pSh.shader = oceanSh; pSh.alpha = a(G); cv.drawCircle(0f, 0f, 40f, pSh)
        lm.setScale(1f / earthBs, 1f / earthBs); lm.postTranslate(-40f - 80f * frac(t / 16f), -40f)
        landSh!!.setLocalMatrix(lm)
        pSh.shader = landSh; pSh.alpha = a(G); cv.drawCircle(0f, 0f, 40f, pSh)
        m.setScale(1f / earthBs, 1f / earthBs); m.postTranslate(-40f, -40f)
        pB.color = (a(G) shl 24) or 0xFFFFFF
        cv.drawBitmap(lightsBmp!!, m, pB)
        lm.setScale(1f / earthBs, 1f / earthBs); lm.postTranslate(-40f - 80f * frac(t / 10f), -40f)
        cloudSh!!.setLocalMatrix(lm)
        pSh.shader = cloudSh; pSh.alpha = a(0.75f * G); cv.drawCircle(0f, 0f, 40f, pSh)
        pSh.shader = shadeSh; pSh.alpha = a(G); cv.drawCircle(0f, 0f, 40f, pSh)
        pS.strokeWidth = 1f; pS.color = (a(0.6f * G) shl 24) or 0xFFFFFF
        cv.drawCircle(0f, 0f, 40f, pS)
        // moon on a 64 x 18 ellipse around the Earth, always in front
        val mp = phiAt(moonLut, frac(t / 9f))
        val mx = 64f * cos(mp)
        val my = 18f * sin(mp)
        pF.color = (a(G) shl 24) or 0xEBE8F2
        cv.drawCircle(mx, my, 7f, pF)
        pS.strokeWidth = 1f; pS.color = (a(G) shl 24) or 0xFFFFFF
        cv.drawCircle(mx, my, 7f, pS)
        pF.color = (a(G) shl 24) or 0xC9C3D6
        cv.drawCircle(mx - 2f, my - 2f, 1.6f, pF)
        cv.drawCircle(mx + 2.5f, my + 2f, 1.1f, pF)
        pSh.shader = null
        cv.restore()
    }

    private fun planetRim(cv: Canvas, r: Float) {
        pS.strokeWidth = 0.8f; pS.color = (a(0.6f * G) shl 24) or 0xFFFFFF
        cv.drawCircle(0f, 0f, r, pS)
    }

    private fun a(x: Float): Int = (x * 255f + 0.5f).toInt().coerceIn(0, 255)
    private fun frac(x: Float): Float = x - floor(x)
    private fun tri(p: Float): Float { val q = frac(p); return if (q < 0.5f) q * 2f else 2f - q * 2f }
    private fun ease(x: Float): Float { val c = x.coerceIn(0f, 1f); return c * c * (3f - 2f * c) }
    private fun easeOut(x: Float): Float = sin(x.coerceIn(0f, 1f) * (PI / 2.0)).toFloat()

    private fun nums(s: String): FloatArray {
        val parts = s.trim().split(' ').filter { it.isNotEmpty() }
        val out = FloatArray(parts.size)
        for (i in parts.indices) out[i] = parts[i].toFloat()
        return out
    }
}
