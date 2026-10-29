package com.example.buddy.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.tan

/**
 * Native animated background of the NewYear look, ported from assets/scene/newyear.html: a faint winter scene (moon, twinkling
 * stars, mountains with a nest, an eagle, snowy hills, a swaying tree with blinking lights, a campfire), the icy frame
 * (frosted edges, snowflake crystals, icicles with drips) and 120 falling snowflakes. Static layers are pre-rendered into
 * bitmaps once; every motion is a pure function of the time. Nothing is allocated while drawing.
 */
class NewYearScene : ThemeScene() {
    override val ready = true

    private val A0 = 0.44f   // the opacity of the whole scene svg (.scn.ny)

    private var W = 0f; private var H = 0f; private var D = 1f
    private var S = 1f; private var SX = 0f; private var SY = 0f   // the svg viewBox (390x844, "slice" into the view plus 12 dp) -> pixels
    private var FX = 1f                                           // the frame: pixels per viewBox unit (width based)
    private var snowSx = 1f; private var snowSy = 1f
    private var ok = false

    private var bmBack: Bitmap? = null
    private var bmHills: Bitmap? = null
    private var bmTree: Bitmap? = null
    private var bmTop: Bitmap? = null
    private var bmBot: Bitmap? = null
    private val dBack = RectF(-30f, 292f, 480f, 650f)
    private val dHills = RectF(-110f, 765f, 500f, 850f)
    private val dTree = RectF(76f, 490f, 314f, 845f)
    private var topH = 0f; private var botH = 0f
    private val bmpPaint = Paint(Paint.FILTER_BITMAP_FLAG)

    private val fp = Paint(Paint.ANTI_ALIAS_FLAG)
    private val sp = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }
    private val gp = Paint(Paint.ANTI_ALIAS_FLAG)
    private val bp = Paint(Paint.ANTI_ALIAS_FLAG)   // used only while building bitmaps
    private val oval = RectF()

    // moon
    private val moonGlow = Paint(Paint.ANTI_ALIAS_FLAG)
    private val crescent = Path()
    private val moonShadow = Path()

    // stars
    private val starPath = Array(8) { Path() }
    private val starX = floatArrayOf(30f, 110f, 200f, 250f, 360f, 70f, 150f, 340f)
    private val starY = floatArrayOf(90f, 60f, 120f, 40f, 100f, 200f, 250f, 230f)
    private val starDelay = floatArrayOf(1.5f, 1.3f, 3.7f, 2.2f, 1.3f, 0f, 0.5f, 1.3f)
    private val starD = arrayOf(
        "M30 85 L31.2 88.8 L35 90 L31.2 91.2 L30 95 L28.8 91.2 L25 90 L28.8 88.8Z",
        "M110 55 L111.2 58.8 L115 60 L111.2 61.2 L110 65 L108.8 61.2 L105 60 L108.8 58.8Z",
        "M200 115 L201.2 118.8 L205 120 L201.2 121.2 L200 125 L198.8 121.2 L195 120 L198.8 118.8Z",
        "M250 35 L251.2 38.8 L255 40 L251.2 41.2 L250 45 L248.8 41.2 L245 40 L248.8 38.8Z",
        "M360 95 L361.2 98.8 L365 100 L361.2 101.2 L360 105 L358.8 101.2 L355 100 L358.8 98.8Z",
        "M70 195 L71.2 198.8 L75 200 L71.2 201.2 L70 205 L68.8 201.2 L65 200 L68.8 198.8Z",
        "M150 245 L151.2 248.8 L155 250 L151.2 251.2 L150 255 L148.8 251.2 L145 250 L148.8 248.8Z",
        "M340 225 L341.2 228.8 L345 230 L341.2 231.2 L340 235 L338.8 231.2 L335 230 L338.8 228.8Z")

    // eagle
    private val egWl = Path(); private val egWr = Path(); private val egBelly = Path(); private val egBeak = Path()
    private val egX = floatArrayOf(200f, 330f, 370f, 240f, 200f)
    private val egY = floatArrayOf(300f, 250f, 330f, 360f, 300f)
    private val egS = floatArrayOf(.55f, .6f, .5f, .45f, .55f)

    // tree lights
    private val bulbX = floatArrayOf(233f, 227f, 233f, 261f, 159f, 219f, 223f, 136f, 165f, 151f, 252f, 255f, 136f, 135f, 204f, 99f, 186f, 265f, 184f, 234f, 183f, 285f, 202f, 259f, 198f, 222f, 143f, 252f)
    private val bulbY = floatArrayOf(589f, 769f, 725f, 787f, 768f, 649f, 568f, 629f, 564f, 701f, 656f, 672f, 736f, 674f, 561f, 788f, 784f, 710f, 787f, 641f, 576f, 721f, 725f, 716f, 634f, 648f, 653f, 677f)
    private val bulbC = intArrayOf(0x4DD38A, 0xFF4D5E, 0x5AA8FF, 0x4DD38A, 0x5AA8FF, 0xFFD34D, 0xFFD34D, 0x5AA8FF, 0xC77DFF, 0x4DD38A, 0x5AA8FF, 0xFFD34D, 0xFFD34D, 0x4DD38A, 0xFF4D5E, 0x4DD38A, 0xFF4D5E, 0xFFD34D, 0xC77DFF, 0xFF4D5E, 0x5AA8FF, 0xFF4D5E, 0x4DD38A, 0xFF4D5E, 0x4DD38A, 0xFF4D5E, 0xFFD34D, 0x4DD38A)
    private val bulbD = floatArrayOf(.1f, .1f, 1.6f, 1.4f, 1.4f, .4f, .5f, 1f, .5f, 1.2f, .1f, .5f, 2f, .7f, 1.9f, .6f, 1.8f, 1f, 1.8f, .5f, .6f, 2f, 0f, .7f, 1.2f, 1.4f, .6f, .9f)
    private val topStar = Path()

    // campfire
    private val flame = Array(3) { Path() }
    private val flameOx = floatArrayOf(1.35f, -.59f, .025f)
    private val flameDelay = floatArrayOf(0f, .15f, .3f)
    private val flameCol = intArrayOf(0xFF6A2B, 0xFFB03A, 0xFFE27A)
    private val sparkSx = floatArrayOf(14f, -11f, 9f, 11f, 9f, 9f, -6f, 7f)
    private val sparkD = floatArrayOf(1.9f, .5f, 1.2f, .1f, .5f, 1.8f, 1.2f, 1.1f)
    private val fireGlow = Paint(Paint.ANTI_ALIAS_FLAG)

    // frame
    private val frL = Paint(); private val frR = Paint(); private val frC70 = Paint(Paint.ANTI_ALIAS_FLAG); private val frC60 = Paint(Paint.ANTI_ALIAS_FLAG)
    private val crystals = Path()
    private val crysPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; color = 0xFFFFFF }
    private val dripX = floatArrayOf(17.8f, 43.6f, 87.6f, 114.2f, 157.3f, 182.6f, 203.1f, 209.7f, 241.6f, 293.9f, 339.3f, 364.5f)
    private val dripY = floatArrayOf(32.2f, 30.9f, 32.3f, 32.7f, 35.4f, 30.2f, 38.2f, 37f, 32.2f, 34.1f, 29.5f, 29.4f)
    private val dripD = floatArrayOf(3.8f, 4.1f, 4.5f, 2.4f, .3f, .6f, 3.8f, 1.9f, 4.5f, .6f, 4.2f, 2.4f)
    private val crX = floatArrayOf(14f, 376f, 10f, 381f, 16f, 374f, 12f, 378f, 36f, 354f)
    private val crY = floatArrayOf(70f, 90f, 300f, 340f, 560f, 600f, 700f, 740f, 40f, 44f)
    private val crR = floatArrayOf(10f, 25f, 0f, 12f, 30f, 5f, 18f, 40f, 0f, 20f)
    private val crL = floatArrayOf(11f, 13f, 8f, 9f, 10f, 8f, 7f, 10f, 7f, 6f)

    // snow (120 flakes, like the canvas of the page)
    private val NF = 120
    private val NB = 10
    private val flX = FloatArray(NF); private val flP = FloatArray(NF); private val flW = FloatArray(NF)
    private val flSpd = FloatArray(NF); private val flT0 = FloatArray(NF); private val flT = FloatArray(NF)
    private val flB = IntArray(NF); private val flSlot = IntArray(NF); private val flHalo = IntArray(NF)
    private val bucketPts = Array(NB) { FloatArray(0) }
    private val bucketR = FloatArray(NB)
    private val bucketA = FloatArray(NB)
    private var haloPts = FloatArray(0)
    private val snowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; color = 0xFFFFFF }

    private val tmpM = Matrix()

    override fun onSize(w: Float, h: Float, density: Float) {
        if (w <= 0f || h <= 0f) return
        W = w; H = h; D = density
        S = max((w + 24f * D) / 390f, (h + 24f * D) / 844f)
        SX = -12f * D + ((w + 24f * D) - 390f * S) / 2f
        SY = -12f * D + ((h + 24f * D) - 844f * S) / 2f
        FX = w / 390f
        snowSx = w / 390f; snowSy = h / 844f
        for (b in arrayOf(bmBack, bmHills, bmTree, bmTop, bmBot)) b?.recycle()
        buildShapes()
        buildBitmaps()
        buildFrame()
        buildSnow()
        ok = true
    }

    private fun buildShapes() {
        for (i in 0 until 8) {
            val p = nyP(starD[i]); p.offset(-starX[i], -starY[i]); starPath[i].set(p)
        }
        // the moon: a lit disc with a shadow disc in front of it
        val disc = Path().apply { addCircle(78f, 120f, 18f, Path.Direction.CW) }
        moonShadow.reset(); moonShadow.addCircle(86f, 113f, 15f, Path.Direction.CW)
        crescent.reset(); crescent.op(disc, moonShadow, Path.Op.DIFFERENCE)
        moonGlow.shader = RadialGradient(78f, 120f, 44f, intArrayOf(0x59FFF8D6, 0x59FFF8D6, 0x2DFFF8D6, 0x00FFF8D6), floatArrayOf(0f, .41f, .68f, 1f), Shader.TileMode.CLAMP)
        egWl.set(nyP("M0 0 C-14 -16 -34 -22 -52 -16 C-40 -10 -30 -4 -22 2 C-30 2 -38 6 -44 10 C-28 12 -12 8 0 4Z"))
        egWr.set(nyP("M0 0 C14 -16 34 -22 52 -16 C40 -10 30 -4 22 2 C30 2 38 6 44 10 C28 12 12 8 0 4Z"))
        egBelly.set(nyP("M-6 16 L0 26 L6 16Z"))
        egBeak.set(nyP("M0 -8 L5 -2 L0 -4Z"))
        val ts = nyP("M195 494 L200 506 L213 506 L203 514 L207 527 L195 519 L183 527 L187 514 L177 506 L190 506Z")
        ts.offset(-195f, -510.5f); topStar.set(ts)
        flame[0].set(nyP("M0 4 C-16 -2 -18 -24 0 -50 C6 -32 20 -24 14 -6 C12 0 6 4 0 4Z"))
        flame[1].set(nyP("M-2 4 C-12 0 -12 -16 -2 -32 C2 -20 10 -14 8 -4 C6 2 2 4 -2 4Z"))
        flame[2].set(nyP("M0 4 C-6 2 -6 -8 0 -18 C2 -10 6 -6 4 0 C3 3 2 4 0 4Z"))
        fireGlow.shader = RadialGradient(0f, 0f, 58f, intArrayOf(0x73FFB347, 0x73FFB347, 0x39FFB347, 0x00FFB347), floatArrayOf(0f, .55f, .8f, 1f), Shader.TileMode.CLAMP)
    }

    private fun makeBmp(r: RectF, k: Float, block: (Canvas) -> Unit): Bitmap? {
        return try {
            val bw = max(1, ceil(r.width() * k).toInt()); val bh = max(1, ceil(r.height() * k).toInt())
            val b = Bitmap.createBitmap(bw, bh, Bitmap.Config.ARGB_8888)
            val c = Canvas(b)
            c.scale(bw / r.width(), bh / r.height())
            c.translate(-r.left, -r.top)
            block(c)
            b
        } catch (e: Throwable) { null }
    }

    private fun fillD(c: Canvas, d: String, rgb: Int, a: Float = 1f) {
        bp.style = Paint.Style.FILL; bp.shader = null; bp.color = (nyA(a) shl 24) or rgb
        c.drawPath(nyP(d), bp)
    }

    private fun strokeD(c: Canvas, d: String, rgb: Int, w: Float, a: Float = 1f) {
        bp.style = Paint.Style.STROKE; bp.shader = null; bp.strokeWidth = w; bp.strokeCap = Paint.Cap.ROUND; bp.color = (nyA(a) shl 24) or rgb
        c.drawPath(nyP(d), bp)
    }

    private fun buildBitmaps() {
        val q = S * 0.75f   // the far layers are soft and faint: three quarters of the pixels are enough
        bmBack = makeBmp(dBack, q) { c ->
            fillD(c, "M-20 600 L70 430 L110 470 L160 400 L230 520 L-20 640Z", 0x9FB2CC)
            fillD(c, "M70 430 L86 460 L100 452 L110 470 L120 456 L132 444 L160 400 L146 432 L130 440 L116 458 L100 446 L86 456Z", 0xFFFFFF)
            fillD(c, "M90 640 L285 300 L470 640Z", 0x8299B8)
            fillD(c, "M285 300 L330 380 L300 368 L282 392 L262 364 L240 380Z", 0xFFFFFF)
            fillD(c, "M285 300 L470 640 L380 640 L310 420Z", 0x6B80A0, .55f)
            strokeD(c, "M250 362 L230 420 M318 372 L340 430", 0xFFFFFF, 2f, .8f)
            // the nest at translate(318 446)
            c.save(); c.translate(318f, 446f)
            fillD(c, "M-34 10 L34 10 L26 22 L-30 22Z", 0x6B5A4C)
            bp.style = Paint.Style.FILL; bp.color = 0xFF8A6A42.toInt(); oval.set(-26f, -5f, 26f, 13f); c.drawOval(oval, bp)
            for (d in arrayOf("M-14 3 l25 -1", "M-10 1 l28 2", "M-7 8 l25 1", "M-5 1 l22 -6", "M-13 4 l29 4", "M-14 2 l20 5", "M-23 6 l20 -4", "M-15 8 l20 2", "M-18 9 l21 2", "M-20 4 l20 2"))
                strokeD(c, d, 0x5A4028, 1.6f)
            bp.style = Paint.Style.FILL; bp.color = 0xFFF2EFE6.toInt(); c.drawCircle(-7f, -2f, 5f, bp); c.drawCircle(6f, -3f, 5f, bp)
            fillD(c, "M-3 -4 l4 1 l-4 1Z M10 -5 l4 1 l-4 1Z", 0xF2B82A)
            c.restore()
        }
        bmHills = makeBmp(dHills, q) { c ->
            fillD(c, "M-20 790 C60 770 140 786 220 780 C300 772 350 786 410 778 L410 860 L-20 860Z", 0xFFFFFF)
            fillD(c, "M-20 820 C80 806 160 820 240 814 C310 808 360 818 410 812 L410 860 L-20 860Z", 0xE6EEF8)
            // the hills go on past the edges so the sideways drift never shows an end
            bp.style = Paint.Style.FILL; bp.shader = null; bp.color = 0xFFFFFFFF.toInt()
            c.drawRect(-120f, 790f, -19f, 860f, bp); c.drawRect(409f, 778f, 510f, 860f, bp)
            bp.color = 0xFFE6EEF8.toInt(); c.drawRect(-120f, 820f, -19f, 860f, bp); c.drawRect(409f, 812f, 510f, 860f, bp)
        }
        bmTree = makeBmp(dTree, S) { c ->
            fillRectBp(c, 183f, 800f, 207f, 840f, 0x7A5A3E)
            fillD(c, "M195 520 L255 610 Q195 622 135 610Z", 0x2F7A55)
            fillD(c, "M195 520 L219 547 Q195 558 171 547Z", 0xFFFFFF)
            strokeD(c, "M135 610 Q165 604 195 614 Q225 604 255 610", 0xFFFFFF, 4f)
            fillD(c, "M195 580 L280 690 Q195 702 110 690Z", 0x2F7A55)
            fillD(c, "M195 580 L229 613 Q195 626 161 613Z", 0xFFFFFF)
            strokeD(c, "M110 690 Q152 684 195 694 Q238 684 280 690", 0xFFFFFF, 4f)
            fillD(c, "M195 650 L310 790 Q195 802 80 790Z", 0x2F7A55)
            fillD(c, "M195 650 L241 692 Q195 709 149 692Z", 0xFFFFFF)
            strokeD(c, "M80 790 Q138 784 195 794 Q252 784 310 790", 0xFFFFFF, 4f)
            val bx = floatArrayOf(170f, 222f, 160f, 240f, 205f); val by = floatArrayOf(640f, 700f, 750f, 770f, 600f)
            val bc = intArrayOf(0xFF4D5E, 0x5AA8FF, 0xFFD34D, 0xC77DFF, 0x4DD38A)
            for (i in 0 until 5) {
                bp.style = Paint.Style.FILL; bp.shader = null; bp.color = 0xFF000000.toInt() or bc[i]; c.drawCircle(bx[i], by[i], 6f, bp)
                bp.style = Paint.Style.STROKE; bp.strokeWidth = 1f; bp.color = 0xFFFFFFFF.toInt(); c.drawCircle(bx[i], by[i], 6f, bp)
            }
        }
    }

    private fun fillRectBp(c: Canvas, l: Float, t: Float, r: Float, b: Float, rgb: Int) {
        bp.style = Paint.Style.FILL; bp.shader = null; bp.color = 0xFF000000.toInt() or rgb; c.drawRect(l, t, r, b, bp)
    }

    private fun buildFrame() {
        topH = ceil(40f * FX); botH = ceil(26f * FX)
        val vw = W / FX
        bmTop = makeBmp(RectF(0f, 0f, vw, topH / FX), FX) { c ->
            bp.style = Paint.Style.FILL; bp.shader = null; bp.color = 0xE6FFFFFF.toInt()
            oval.set(0f, 0f, vw, 7f); c.drawRoundRect(oval, 3f, 3f, bp)
            for (d in ICICLES) {
                val p = nyP(d); p.computeBounds(oval, true)
                bp.shader = LinearGradient(0f, 4f, 0f, oval.bottom, 0xF2FFFFFF.toInt(), 0x8CCFE3FF.toInt(), Shader.TileMode.CLAMP)
                c.drawPath(p, bp)
            }
            bp.shader = null
        }
        bmBot = makeBmp(RectF(0f, 818f, vw, 844f), FX) { c ->
            fillD(c, "M-5 844 L-5 830 C20 822 40 830 62 826 C90 820 110 832 140 828 C170 823 190 833 222 829 C252 824 272 832 300 827 C330 822 352 831 395 824 L395 844Z", 0xFFFFFF)
            fillD(c, "M-5 844 L-5 836 C40 832 80 838 130 835 C200 830 260 838 395 833 L395 844Z", 0xE8F0FA)
        }
        val e = FX * 16f
        frL.shader = LinearGradient(0f, 0f, e, 0f, 0xBFFFFFFF.toInt(), 0x00FFFFFF, Shader.TileMode.CLAMP)
        frR.shader = LinearGradient(W, 0f, W - e, 0f, 0xBFFFFFFF.toInt(), 0x00FFFFFF, Shader.TileMode.CLAMP)
        frC70.shader = RadialGradient(0f, 0f, 70f * FX, 0xD9FFFFFF.toInt(), 0x00FFFFFF, Shader.TileMode.CLAMP)
        frC60.shader = RadialGradient(0f, 0f, 60f * FX, 0xD9FFFFFF.toInt(), 0x00FFFFFF, Shader.TileMode.CLAMP)
        // snowflake crystals along the edges (one path, one stroke)
        crystals.reset()
        val arm = Path()
        for (i in 0 until 10) {
            val l = crL[i]
            arm.reset()
            arm.moveTo(0f, 0f); arm.lineTo(0f, -l)
            arm.moveTo(0f, -.55f * l); arm.lineTo(.22f * l, -.78f * l)
            arm.moveTo(0f, -.55f * l); arm.lineTo(-.22f * l, -.78f * l)
            for (k in 0 until 6) {
                tmpM.reset(); tmpM.postRotate(k * 60f + crR[i]); tmpM.postScale(FX, FX); tmpM.postTranslate(crX[i] * FX, crY[i] * H / 844f)
                crystals.addPath(arm, tmpM)
            }
        }
        crysPaint.strokeWidth = 1.3f * FX
    }

    private fun buildSnow() {
        val counts = IntArray(NB); var haloN = 0
        for (i in 0 until NF) {
            val r = nyH(i, 3) * 2.4f + .6f
            val v = nyH(i, 4) * .6f + .35f
            val o = nyH(i, 7) * .45f + .5f
            flX[i] = nyH(i, 1) * 390f; flP[i] = nyH(i, 5) * 6.28f; flW[i] = nyH(i, 6) * .7f + .2f
            flSpd[i] = v * (.6f + r * .45f) * 60f             // the page moves a flake by that many units per frame at 60 fps
            flT[i] = 858f / flSpd[i]
            flT0[i] = (nyH(i, 2) * 844f + 8f) / flSpd[i]
            val rb = ((r - .6f) / 2.4f * 5f).toInt().coerceIn(0, 4)
            val ab = if (o < .725f) 0 else 1
            val b = rb * 2 + ab
            flB[i] = b; flSlot[i] = counts[b]++
            bucketR[b] = .6f + (rb + .5f) * .48f
            bucketA[b] = if (ab == 0) .6f else .83f
            if (r > 2.6f) { flHalo[i] = haloN++ } else flHalo[i] = -1
        }
        for (b in 0 until NB) bucketPts[b] = FloatArray(counts[b] * 2)
        haloPts = FloatArray(haloN * 2)
    }

    override fun draw(cv: Canvas, w: Float, h: Float, t: Float, dt: Float, page: Float) {
        if (!ok) return
        val td = t.toDouble()
        val sh = -page * 8f * D
        cv.save()
        cv.translate(SX + sh, SY)
        cv.scale(S, S)

        // moon (the whole group breathes between .7 and 1)
        val m = .7f + .3f * nyEase(nyTri(td / 7.0))
        moonGlow.alpha = nyA(A0 * m)
        cv.drawCircle(78f, 120f, 44f, moonGlow)
        fp.style = Paint.Style.FILL
        fp.color = (nyA(A0 * m) shl 24) or 0xFFF8D6
        cv.drawPath(crescent, fp)
        fp.color = (nyA(A0 * m) shl 24) or 0xCBD8E6
        cv.drawPath(moonShadow, fp)

        // stars
        for (i in 0 until 8) {
            val p = nyCyc(td, starDelay[i], 3f)
            val u = nyEase(1f - abs(2f * p - 1f))
            fp.color = (nyA(A0 * (.3f + .7f * u)) shl 24) or 0xFFFFFF
            cv.save(); cv.translate(starX[i], starY[i]); cv.rotate(45f * u); val sc = .3f + .9f * u; cv.scale(sc, sc)
            cv.drawPath(starPath[i], fp); cv.restore()
        }

        bmBack?.let { bmpPaint.alpha = nyA(A0); cv.drawBitmap(it, null, dBack, bmpPaint) }

        // eagle: 16 s round, linear between the key points
        run {
            val p = nyCyc(td, 0f, 16f) * 4f
            val k = min(3, p.toInt()); val f = p - k
            val ex = egX[k] + (egX[k + 1] - egX[k]) * f
            val ey = egY[k] + (egY[k + 1] - egY[k]) * f
            val es = egS[k] + (egS[k + 1] - egS[k]) * f
            val flap = 1f - .65f * nyEase(nyTri(td / .5))
            cv.save(); cv.translate(ex, ey); cv.scale(es, es)
            fp.color = (nyA(A0) shl 24) or 0x5A3A22
            cv.save(); cv.translate(0f, -3.87f); cv.scale(1f, flap); cv.translate(0f, 3.87f); cv.drawPath(egWl, fp); cv.drawPath(egWr, fp); cv.restore()
            fp.color = (nyA(A0) shl 24) or 0x6B4428; oval.set(-9f, -10f, 9f, 18f); cv.drawOval(oval, fp)
            fp.color = (nyA(A0) shl 24) or 0xF2EFE6; cv.drawPath(egBelly, fp)
            fp.color = (nyA(A0) shl 24) or 0xF6F4EE; cv.drawCircle(0f, -10f, 7f, fp)
            fp.color = (nyA(A0) shl 24) or 0xF2B82A; cv.drawPath(egBeak, fp)
            fp.color = (nyA(A0) shl 24) or 0x1A1A1A; cv.drawCircle(2f, -11f, 1.2f, fp)
            cv.restore()
        }

        // near layers drift a little more than the far ones
        cv.translate(sh * .15f / S, 0f)
        bmHills?.let { bmpPaint.alpha = nyA(A0); cv.drawBitmap(it, null, dHills, bmpPaint) }

        // the tree sways (skewX -1deg .. 1deg), its lights blink
        run {
            val a = -1f + 2f * nyEase(nyTri(td / 6.0))
            cv.save(); cv.translate(195f, 840f); cv.skew(tan(Math.toRadians(a.toDouble())).toFloat(), 0f); cv.translate(-195f, -840f)
            bmTree?.let { bmpPaint.alpha = nyA(A0); cv.drawBitmap(it, null, dTree, bmpPaint) }
            fp.style = Paint.Style.FILL
            for (i in 0 until 28) {
                val u = nyEase(nyTri((td + bulbD[i]) / 1.8))
                fp.color = (nyA(A0 * (.35f + .65f * u)) shl 24) or bulbC[i]
                cv.drawCircle(bulbX[i], bulbY[i], 3.2f, fp)
            }
            val p = nyCyc(td, 0f, 2.4f)
            val u = nyEase(1f - abs(2f * p - 1f))
            fp.color = (nyA(A0) shl 24) or 0xFFD34D
            cv.translate(195f, 510.5f); cv.rotate(18f * u); val sc = 1f + .25f * u; cv.scale(sc, sc)
            cv.drawPath(topStar, fp)
            cv.restore()
        }

        // campfire at (62, 818)
        cv.save(); cv.translate(62f, 818f)
        fireGlow.alpha = nyA(A0)
        cv.drawCircle(0f, 0f, 58f, fireGlow)
        sp.strokeWidth = 8f
        sp.color = (nyA(A0) shl 24) or 0x6B4A2A; cv.drawLine(-24f, 10f, 22f, -2f, sp)
        sp.color = (nyA(A0) shl 24) or 0x7D5634; cv.drawLine(-22f, -2f, 24f, 10f, sp)
        for (i in 0 until 3) {
            val u = nyEase(nyTri((td + flameDelay[i]) / .3))
            val sx = .85f + .23f * u; val sy = .88f + .27f * u
            val ox = flameOx[i]
            fp.color = (nyA(A0) shl 24) or flameCol[i]
            cv.save(); cv.translate(ox, 4f); cv.scale(sx, sy); cv.skew(tan(Math.toRadians((-5.0 + 10.0 * u))).toFloat(), 0f); cv.translate(-ox, -4f)
            cv.drawPath(flame[i], fp); cv.restore()
        }
        for (i in 0 until 8) {
            val e = nyEaseOut(nyCyc(td, sparkD[i], 1.8f))
            fp.color = (nyA(A0 * (1f - e)) shl 24) or 0xFFD25A
            cv.drawCircle(sparkSx[i] * e, -20f - 70f * e, 1.6f, fp)
        }
        cv.restore()
        cv.restore()

        // the frame (above the scene, full strength): frosted edges, corners, crystals, icicles, drips, snow bank
        val cp = .35f + .4f * nyEase(nyTri(td / 4.0))
        cv.drawRect(0f, 0f, FX * 16f, H, frL)
        cv.drawRect(W - FX * 16f, 0f, W, H, frR)
        cv.drawCircle(0f, 0f, 70f * FX, frC70); cv.drawCircle(W, 0f, 70f * FX, frC70)
        cv.drawCircle(0f, H, 60f * FX, frC60); cv.drawCircle(W, H, 60f * FX, frC60)
        crysPaint.alpha = nyA(cp)
        cv.drawPath(crystals, crysPaint)
        bmpPaint.alpha = 255
        bmTop?.let { cv.drawBitmap(it, 0f, 0f, bmpPaint) }
        bmBot?.let { cv.drawBitmap(it, 0f, H - it.height, bmpPaint) }
        fp.style = Paint.Style.FILL
        for (i in 0 until 12) {
            val p = nyCyc(td, dripD[i], 5f)
            if (p < .7f) continue
            val al = if (p < .75f) nyEaseIn((p - .7f) / .05f) else 1f - nyEaseIn((p - .75f) / .25f)
            val dy = 60f * nyEaseIn((p - .7f) / .3f)
            fp.color = (nyA(al) shl 24) or 0xDCEBFF
            cv.drawCircle(dripX[i] * FX, (dripY[i] + dy) * FX, 1.8f * FX, fp)
        }

        // snow: positions are functions of the time (fall speed and sway as in the page's canvas loop)
        val fall = td
        for (i in 0 until NF) {
            val ph = ((fall + flT0[i]) % flT[i]).toFloat()
            val y = -8f + ph * flSpd[i]
            val x = flX[i] + 27.5f * flW[i] * (cos(flP[i].toDouble()) - cos(1.2 * td + flP[i])).toFloat()
            val b = flB[i]; val k = flSlot[i] * 2
            val pts = bucketPts[b]
            pts[k] = x * snowSx; pts[k + 1] = y * snowSy
            val hs = flHalo[i]
            if (hs >= 0) { haloPts[hs * 2] = x * snowSx; haloPts[hs * 2 + 1] = y * snowSy }
        }
        if (haloPts.isNotEmpty()) {
            snowPaint.strokeWidth = 2f * 2.2f * 2.8f * FX; snowPaint.alpha = nyA(.25f * .75f)
            cv.drawPoints(haloPts, 0, haloPts.size, snowPaint)
        }
        for (b in 0 until NB) {
            val pts = bucketPts[b]
            if (pts.isEmpty()) continue
            snowPaint.strokeWidth = 2f * bucketR[b] * FX; snowPaint.alpha = nyA(bucketA[b])
            cv.drawPoints(pts, 0, pts.size, snowPaint)
        }
    }

    private companion object {
        val ICICLES = arrayOf(
            "M-2.0 4 C2.5 8.3 4.7 12.7 5.5 15.7 C6.2 12.7 8.5 8.3 13.0 4Z",
            "M11.5 4 C15.3 15.6 17.2 27.2 17.8 30.2 C18.4 27.2 20.3 15.6 24.1 4Z",
            "M22.6 4 C24.9 8.9 26.1 13.7 26.5 16.7 C26.8 13.7 28.0 8.9 30.3 4Z",
            "M28.8 4 C31.7 7.4 33.2 10.8 33.7 13.8 C34.2 10.8 35.6 7.4 38.5 4Z",
            "M37.0 4 C41.0 15.0 43.0 25.9 43.6 28.9 C44.3 25.9 46.3 15.0 50.3 4Z",
            "M48.8 4 C52.4 17.9 54.2 31.8 54.8 34.8 C55.4 31.8 57.3 17.9 60.9 4Z",
            "M59.4 4 C61.9 8.9 63.2 13.9 63.6 16.9 C64.0 13.9 65.2 8.9 67.8 4Z",
            "M66.3 4 C68.9 10.7 70.2 17.5 70.6 20.5 C71.0 17.5 72.3 10.7 75.0 4Z",
            "M73.5 4 C76.1 7.8 77.4 11.7 77.9 14.7 C78.3 11.7 79.6 7.8 82.2 4Z",
            "M80.7 4 C84.9 15.6 86.9 27.3 87.6 30.3 C88.3 27.3 90.4 15.6 94.5 4Z",
            "M93.0 4 C95.2 7.8 96.4 11.5 96.7 14.5 C97.1 11.5 98.2 7.8 100.4 4Z",
            "M98.9 4 C102.4 7.8 104.1 11.5 104.7 14.5 C105.3 11.5 107.0 7.8 110.5 4Z",
            "M109.0 4 C112.1 15.8 113.7 27.7 114.2 30.7 C114.7 27.7 116.3 15.8 119.4 4Z",
            "M117.9 4 C120.3 7.6 121.5 11.2 121.9 14.2 C122.4 11.2 123.6 7.6 126.0 4Z",
            "M124.5 4 C126.7 10.6 127.8 17.2 128.2 20.2 C128.6 17.2 129.7 10.6 132.0 4Z",
            "M130.5 4 C133.8 10.0 135.5 16.1 136.1 19.1 C136.6 16.1 138.3 10.0 141.7 4Z",
            "M140.2 4 C143.8 15.8 145.6 27.6 146.2 30.6 C146.9 27.6 148.7 15.8 152.3 4Z",
            "M150.8 4 C154.7 17.2 156.6 30.4 157.3 33.4 C157.9 30.4 159.9 17.2 163.8 4Z",
            "M162.3 4 C165.0 14.6 166.3 25.1 166.8 28.1 C167.2 25.1 168.6 14.6 171.3 4Z",
            "M169.8 4 C172.3 12.6 173.5 21.3 173.9 24.3 C174.4 21.3 175.6 12.6 178.1 4Z",
            "M176.6 4 C180.2 14.6 182.0 25.2 182.6 28.2 C183.2 25.2 185.1 14.6 188.7 4Z",
            "M187.2 4 C189.5 12.0 190.6 20.0 191.0 23.0 C191.4 20.0 192.6 12.0 194.9 4Z",
            "M193.4 4 C195.7 14.9 196.8 25.8 197.2 28.8 C197.6 25.8 198.7 14.9 201.0 4Z",
            "M199.5 4 C201.7 18.6 202.7 33.2 203.1 36.2 C203.4 33.2 204.5 18.6 206.6 4Z",
            "M205.1 4 C207.9 18.0 209.2 32.0 209.7 35.0 C210.2 32.0 211.5 18.0 214.3 4Z",
            "M212.8 4 C215.8 14.5 217.3 24.9 217.8 27.9 C218.3 24.9 219.8 14.5 222.7 4Z",
            "M221.2 4 C225.6 8.6 227.8 13.1 228.6 16.1 C229.3 13.1 231.5 8.6 235.9 4Z",
            "M234.4 4 C238.7 15.6 240.9 27.2 241.6 30.2 C242.3 27.2 244.5 15.6 248.8 4Z",
            "M247.3 4 C249.4 10.2 250.4 16.4 250.8 19.4 C251.1 16.4 252.2 10.2 254.3 4Z",
            "M252.8 4 C256.9 7.4 258.9 10.8 259.5 13.8 C260.2 10.8 262.2 7.4 266.3 4Z",
            "M264.8 4 C267.3 8.0 268.6 11.9 269.0 14.9 C269.4 11.9 270.7 8.0 273.2 4Z",
            "M271.7 4 C275.0 10.2 276.6 16.4 277.2 19.4 C277.7 16.4 279.4 10.2 282.6 4Z",
            "M281.1 4 C284.2 11.0 285.7 18.1 286.2 21.1 C286.7 18.1 288.2 11.0 291.2 4Z",
            "M289.7 4 C292.2 16.5 293.4 29.1 293.9 32.1 C294.3 29.1 295.5 16.5 298.0 4Z",
            "M296.5 4 C299.8 9.4 301.4 14.9 301.9 17.9 C302.5 14.9 304.1 9.4 307.3 4Z",
            "M305.8 4 C310.1 9.2 312.2 14.4 312.9 17.4 C313.6 14.4 315.7 9.2 319.9 4Z",
            "M318.4 4 C322.9 8.9 325.2 13.7 325.9 16.7 C326.7 13.7 328.9 8.9 333.4 4Z",
            "M331.9 4 C336.4 14.2 338.6 24.5 339.3 27.5 C340.1 24.5 342.3 14.2 346.8 4Z",
            "M345.3 4 C348.1 10.9 349.5 17.7 350.0 20.7 C350.5 17.7 351.9 10.9 354.8 4Z",
            "M353.3 4 C355.8 10.1 357.0 16.2 357.4 19.2 C357.8 16.2 359.1 10.1 361.6 4Z",
            "M360.1 4 C362.7 14.2 364.1 24.4 364.5 27.4 C365.0 24.4 366.3 14.2 369.0 4Z",
            "M367.5 4 C369.8 15.6 371.0 27.2 371.4 30.2 C371.8 27.2 373.0 15.6 375.3 4Z",
            "M373.8 4 C377.4 15.1 379.1 26.3 379.7 29.3 C380.3 26.3 382.1 15.1 385.6 4Z",
            "M384.1 4 C386.4 11.9 387.5 19.7 387.9 22.7 C388.3 19.7 389.4 11.9 391.7 4Z",
            "M390.2 4 C393.5 12.2 395.2 20.4 395.7 23.4 C396.3 20.4 397.9 12.2 401.3 4Z"
        )
    }
}

private fun nyA(a: Float): Int = (a.coerceIn(0f, 1f) * 255f + .5f).toInt()

private fun nyH(i: Int, k: Int): Float { val x = Math.sin(i * 12.9898 + k * 78.233) * 43758.5453; return (x - floor(x)).toFloat() }

/** CSS cubic-bezier(x1,y1,x2,y2) easing. */
private fun nyBez(x: Float, x1: Float, y1: Float, x2: Float, y2: Float): Float {
    if (x <= 0f) return 0f
    if (x >= 1f) return 1f
    var lo = 0f; var hi = 1f; var u = x
    for (i in 0 until 14) {
        val v = 1f - u
        val bx = 3f * v * v * u * x1 + 3f * v * u * u * x2 + u * u * u
        if (bx < x) lo = u else hi = u
        u = (lo + hi) * .5f
    }
    val v = 1f - u
    return 3f * v * v * u * y1 + 3f * v * u * u * y2 + u * u * u
}
private fun nyEase(x: Float) = nyBez(x, .42f, 0f, .58f, 1f)      // ease-in-out
private fun nyEaseIn(x: Float) = nyBez(x, .42f, 0f, 1f, 1f)      // ease-in
private fun nyEaseOut(x: Float) = nyBez(x, 0f, 0f, .58f, 1f)     // ease-out

/** Position in [0,1) of a looping animation of [period] s whose negative CSS delay is -[delay]. */
private fun nyCyc(t: Double, delay: Float, period: Float): Float { val x = (t + delay) / period; return (x - floor(x)).toFloat() }
/** 0..1..0 over two units of [x] (an "alternate" animation). */
private fun nyTri(x: Double): Float { val m = x - 2.0 * floor(x / 2.0); return (if (m < 1.0) m else 2.0 - m).toFloat() }

/** A tiny SVG path parser: M L H V C Q Z (absolute and relative). */
private fun nyP(d: String): Path {
    val p = Path()
    var i = 0; val n = d.length
    var cx = 0f; var cy = 0f; var sx = 0f; var sy = 0f
    var cmd = ' '
    fun num(): Float {
        while (i < n && (d[i] == ' ' || d[i] == ',')) i++
        val st = i
        if (i < n && (d[i] == '-' || d[i] == '+')) i++
        while (i < n && (d[i].isDigit() || d[i] == '.')) i++
        return d.substring(st, i).toFloat()
    }
    while (true) {
        while (i < n && (d[i] == ' ' || d[i] == ',')) i++
        if (i >= n) break
        if (d[i].isLetter()) { cmd = d[i]; i++ }
        val rel = cmd.isLowerCase()
        when (cmd.uppercaseChar()) {
            'M' -> { var x = num(); var y = num(); if (rel) { x += cx; y += cy }; p.moveTo(x, y); cx = x; cy = y; sx = x; sy = y; cmd = if (rel) 'l' else 'L' }
            'L' -> { var x = num(); var y = num(); if (rel) { x += cx; y += cy }; p.lineTo(x, y); cx = x; cy = y }
            'H' -> { var x = num(); if (rel) x += cx; p.lineTo(x, cy); cx = x }
            'V' -> { var y = num(); if (rel) y += cy; p.lineTo(cx, y); cy = y }
            'C' -> {
                var x1 = num(); var y1 = num(); var x2 = num(); var y2 = num(); var x = num(); var y = num()
                if (rel) { x1 += cx; y1 += cy; x2 += cx; y2 += cy; x += cx; y += cy }
                p.cubicTo(x1, y1, x2, y2, x, y); cx = x; cy = y
            }
            'Q' -> {
                var x1 = num(); var y1 = num(); var x = num(); var y = num()
                if (rel) { x1 += cx; y1 += cy; x += cx; y += cy }
                p.quadTo(x1, y1, x, y); cx = x; cy = y
            }
            'Z' -> { p.close(); cx = sx; cy = sy }
            else -> break
        }
    }
    return p
}
