package com.example.buddy.ui

import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import android.graphics.Shader

/**
 * Native animated background of the Ice look, ported from assets/scene/ice.html: northern-light bands, two ranges of ice
 * peaks drifting a few pixels, drifting mist, the sea with ripples and three bobbing icebergs, an ice shelf with cracks,
 * twinkling sparkles on the peaks and 34 falling snowflakes. The whole picture sits at 24 % opacity like the original.
 *
 * The SVG of the page is 390 x 844 units, fitted "slice" into a box 12 dp bigger than the screen on every side. Static layers
 * (mountains, mist, shelf, bergs, aurora) are rendered once into bitmaps in [onSize] (the blurs too), so a frame is a handful of
 * bitmap blits, one clip, a wave path, six sparkles and the snow.
 */
class IceScene : ThemeScene() {
    override val ready = true

    private class Layer(val bmp: Bitmap, val x0: Float, val y0: Float, val x1: Float, val y1: Float)

    private val G = 0.24f   // opacity of the whole SVG (.scn{opacity:.24})
    private val cWhite = 0xFFFFFFFF.toInt()
    private val cIce2 = 0xFFB6C9E3.toInt()
    private val cFar = 0xFF6F8FBD.toInt()
    private val cNear = 0xFF5A78A8.toInt()
    private val cWater = 0xFF7F9BBD.toInt()

    // ---- easing tables (CSS cubic-bezier) ----
    private val easeStd = lutOf(.25f, .1f, .25f, 1f)   // "ease", the default of the snow
    private val easeIO = lutOf(.42f, 0f, .58f, 1f)     // "ease-in-out"

    private fun bez(p1: Float, p2: Float, t: Float): Float { val u = 1f - t; return 3f * u * u * t * p1 + 3f * u * t * t * p2 + t * t * t }
    private fun lutOf(x1: Float, y1: Float, x2: Float, y2: Float): FloatArray {
        val a = FloatArray(129)
        for (i in 0..128) {
            val x = i / 128f
            var lo = 0f; var hi = 1f
            for (k in 0 until 30) { val m = (lo + hi) / 2f; if (bez(x1, x2, m) < x) lo = m else hi = m }
            a[i] = bez(y1, y2, (lo + hi) / 2f)
        }
        return a
    }
    private fun lk(a: FloatArray, x: Float): Float {
        if (x <= 0f) return 0f
        if (x >= 1f) return 1f
        val f = x * 128f
        val i = f.toInt()
        return a[i] + (a[i + 1] - a[i]) * (f - i)
    }
    /** 0 -> 1 -> 0 over a period of 2 (CSS "alternate"). */
    private fun ping(x: Float): Float { var m = x % 2f; if (m < 0f) m += 2f; return if (m > 1f) 2f - m else m }
    private fun a8(f: Float): Int = (f * 255f + .5f).toInt().coerceIn(0, 255)

    // ---- the artwork (svg units) ----
    private val farPoly = floatArrayOf(-20f, 560f, 30f, 470f, 60f, 505f, 105f, 415f, 140f, 482f, 178f, 442f, 214f, 520f, 252f, 428f, 290f, 476f, 332f, 398f, 368f, 468f, 398f, 445f, 420f, 560f, 410f, 560f, 410f, 900f, -20f, 900f)
    private val farShards = floatArrayOf(30f, 470f, 60f, 505f, 54f, 900f, 34f, 900f, 105f, 415f, 140f, 482f, 134f, 900f, 109f, 900f, 178f, 442f, 214f, 520f, 208f, 900f, 182f, 900f, 252f, 428f, 290f, 476f, 284f, 900f, 256f, 900f, 332f, 398f, 368f, 468f, 362f, 900f, 336f, 900f, 398f, 445f, 420f, 560f, 414f, 900f, 402f, 900f)
    private val farLines = floatArrayOf(30f, 470f, 40f, 486f, 105f, 415f, 117f, 445f, 178f, 442f, 191f, 477f, 252f, 428f, 265f, 450f, 332f, 398f, 345f, 430f, 398f, 445f, 406f, 497f)
    private val nearPoly = floatArrayOf(-20f, 660f, -10f, 560f, 40f, 610f, 88f, 500f, 130f, 590f, 190f, 530f, 236f, 640f, 296f, 515f, 340f, 600f, 384f, 540f, 420f, 640f, 410f, 660f, 410f, 900f, -20f, 900f)
    private val nearShards = floatArrayOf(-10f, 560f, 40f, 610f, 34f, 900f, -6f, 900f, 88f, 500f, 130f, 590f, 124f, 900f, 92f, 900f, 190f, 530f, 236f, 640f, 230f, 900f, 194f, 900f, 296f, 515f, 340f, 600f, 334f, 900f, 300f, 900f, 384f, 540f, 420f, 640f, 414f, 900f, 388f, 900f)
    private val nearLines = floatArrayOf(-10f, 560f, 8f, 582f, 88f, 500f, 103f, 540f, 190f, 530f, 206f, 580f, 296f, 515f, 311f, 553f, 384f, 540f, 397f, 585f)
    private val shelfPoly = floatArrayOf(-20f, 700f, 20f, 690f, 40f, 708f, 70f, 694f, 100f, 712f, 130f, 698f, 165f, 716f, 180f, 844f, -20f, 844f)
    private val shelfCracks = floatArrayOf(5f, 712f, 9f, 844f, 19f, 712f, 22f, 844f, 33f, 713f, 32f, 844f, 47f, 707f, 51f, 844f, 61f, 712f, 59f, 844f, 75f, 706f, 78f, 844f, 89f, 709f, 87f, 844f, 103f, 706f, 107f, 844f, 117f, 715f, 113f, 844f, 131f, 714f, 133f, 844f, 145f, 712f, 143f, 844f, 159f, 714f, 155f, 844f)
    // each iceberg: white body, shaded side, reflection below the waterline
    private val bergWhite = arrayOf(
        floatArrayOf(205f, 776f, 216f, 752f, 226f, 742f, 237f, 756f, 251f, 776f),
        floatArrayOf(282f, 776f, 300f, 740f, 314f, 724f, 331f, 745f, 352f, 776f),
        floatArrayOf(352f, 776f, 362f, 758f, 369f, 750f, 379f, 760f, 390f, 776f))
    private val bergShade = arrayOf(
        floatArrayOf(226f, 742f, 237f, 756f, 251f, 776f, 228f, 776f),
        floatArrayOf(314f, 724f, 331f, 745f, 352f, 776f, 317f, 776f),
        floatArrayOf(369f, 750f, 379f, 760f, 390f, 776f, 371f, 776f))
    private val bergRefl = arrayOf(
        floatArrayOf(209f, 778f, 247f, 778f, 230f, 798f),
        floatArrayOf(286f, 778f, 348f, 778f, 320f, 809f),
        floatArrayOf(356f, 778f, 386f, 778f, 373f, 794f))
    private val bergDur = floatArrayOf(4f, 5f, 6f)
    private val bergDelay = floatArrayOf(0f, -1.3f, -2.6f)
    private val sparkX = floatArrayOf(105f, 332f, 252f, 88f, 296f, 190f)
    private val sparkY = floatArrayOf(418f, 401f, 431f, 503f, 518f, 533f)
    // snowflakes: x, duration, delay, radius
    private val snow = floatArrayOf(
        270f, 9.3f, -15.8f, 1.0f, 307f, 13.2f, -7.9f, 1.0f, 100f, 14.8f, -9.0f, 1.8f, 339f, 14.9f, -7.3f, 1.0f, 282f, 16.6f, -12.7f, 2.6f,
        161f, 13.6f, -0.5f, 2.2f, 288f, 12.6f, -15.2f, 2.2f, 197f, 9.2f, -12.3f, 1.0f, 109f, 17.2f, -8.5f, 1.3f, 362f, 9.7f, -11.3f, 1.6f,
        345f, 9.8f, -6.0f, 1.4f, 209f, 10.1f, -4.4f, 2.2f, 51f, 13.2f, -8.8f, 0.9f, 349f, 13.0f, -3.4f, 1.9f, 374f, 12.8f, -6.9f, 2.2f,
        202f, 10.9f, -4.9f, 1.6f, 303f, 17.0f, -3.8f, 1.4f, 201f, 14.4f, -10.4f, 2.6f, 21f, 10.9f, -4.6f, 2.6f, 312f, 11.7f, -1.3f, 1.5f,
        46f, 14.7f, -0.3f, 1.3f, 188f, 13.1f, -17.3f, 1.5f, 247f, 10.2f, -6.9f, 2.3f, 321f, 17.2f, -14.7f, 1.2f, 127f, 10.4f, -11.3f, 2.1f,
        283f, 17.6f, -15.9f, 1.2f, 309f, 9.4f, -2.0f, 1.0f, 262f, 11.1f, -12.7f, 2.5f, 131f, 17.1f, -8.8f, 1.6f, 266f, 15.5f, -1.2f, 1.2f,
        116f, 14.9f, -11.1f, 1.7f, 37f, 17.3f, -3.7f, 1.4f, 8f, 12.7f, -4.5f, 1.0f, 23f, 12.3f, -10.3f, 1.2f)
    private val snowN = snow.size / 4

    // ---- reusable objects (nothing is allocated while drawing) ----
    private val pBmp = Paint(Paint.FILTER_BITMAP_FLAG)
    private val pFill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = cWhite }
    private val pWave = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = cWhite; style = Paint.Style.STROKE; strokeWidth = 1.2f }
    private val rU = RectF()
    private val rPx = RectF()
    private val tmp = Path()
    private val wave = Path()
    private val star = Path()
    private val aboveNear = Path()   // everything that is not the near range: the mist is hidden behind it
    private val bergDst = Array(3) { RectF() }
    private val bergOx = FloatArray(3)
    private val bergOy = FloatArray(3)

    private var aur: Layer? = null
    private var far: Layer? = null
    private var mist: Layer? = null
    private var near: Layer? = null
    private var shelf: Layer? = null
    private val bergs = arrayOfNulls<Layer>(3)

    private var built = false
    private var dens = 1f
    private var sc = 1f   // svg unit -> pixel
    private var tx = 0f   // pixel position of svg x = 0
    private var ty = 0f

    init {
        wave.moveTo(-20f, 776f)
        for (i in 0 until 18) {
            val x0 = -20f + 30f * i
            wave.quadTo(x0 + 15f, if (i % 2 == 0) 780f else 772f, x0 + 30f, 776f)
        }
        star.moveTo(0f, -7f); star.lineTo(1.5f, -1.5f); star.lineTo(7f, 0f); star.lineTo(1.5f, 1.5f)
        star.lineTo(0f, 7f); star.lineTo(-1.5f, 1.5f); star.lineTo(-7f, 0f); star.lineTo(-1.5f, -1.5f); star.close()
        aboveNear.moveTo(-300f, -200f); aboveNear.lineTo(700f, -200f); aboveNear.lineTo(700f, 900f)
        var i = nearPoly.size / 2 - 2   // from the bottom right corner back along the ridge to the left edge
        while (i >= 0) { aboveNear.lineTo(nearPoly[i * 2], nearPoly[i * 2 + 1]); i-- }
        aboveNear.lineTo(-20f, 900f); aboveNear.lineTo(-300f, 900f); aboveNear.close()
    }

    private fun poly(p: Path, a: FloatArray) {
        p.reset(); p.moveTo(a[0], a[1])
        var i = 2
        while (i < a.size) { p.lineTo(a[i], a[i + 1]); i += 2 }
        p.close()
    }

    private fun fillP(color: Int, a: Float, sh: Shader? = null): Paint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color; alpha = a8(a); if (sh != null) shader = sh }

    private fun strokeP(color: Int, wdt: Float, a: Float): Paint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color; alpha = a8(a); style = Paint.Style.STROKE; strokeWidth = wdt }

    private fun layer(x0: Float, y0: Float, x1: Float, y1: Float, k0: Float, draw: (Canvas, Matrix) -> Unit): Layer? {
        var k = k0
        for (attempt in 0..1) {
            try {
                val bw = Math.ceil(((x1 - x0) * k).toDouble()).toInt().coerceAtLeast(1)
                val bh = Math.ceil(((y1 - y0) * k).toDouble()).toInt().coerceAtLeast(1)
                val b = Bitmap.createBitmap(bw, bh, Bitmap.Config.ARGB_8888)
                val m = Matrix()
                m.setScale(bw / (x1 - x0), bh / (y1 - y0))
                m.preTranslate(-x0, -y0)
                draw(Canvas(b), m)
                return Layer(b, x0, y0, x1, y1)
            } catch (e: OutOfMemoryError) { k *= 0.5f }
        }
        return null
    }

    private fun free() {
        built = false
        aur?.bmp?.recycle(); far?.bmp?.recycle(); mist?.bmp?.recycle(); near?.bmp?.recycle(); shelf?.bmp?.recycle()
        aur = null; far = null; mist = null; near = null; shelf = null
        for (i in 0 until 3) { bergs[i]?.bmp?.recycle(); bergs[i] = null }
    }

    private fun blur(color: Int, a: Float, m: Matrix): Paint {
        val sigma = 14f * m.mapRadius(1f)   // feGaussianBlur stdDeviation 14 (svg units)
        return Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color; alpha = a8(a)
            maskFilter = BlurMaskFilter(Math.max(1f, (sigma - 0.5f) / 0.57735f), BlurMaskFilter.Blur.NORMAL)
        }
    }

    override fun onSize(w: Float, h: Float, density: Float) {
        free()
        if (w <= 0f || h <= 0f) return
        dens = density
        val cw = w + 24f * density   // the .scene box is 12 px larger than the screen on every side
        val ch = h + 24f * density
        sc = Math.max(cw / 390f, ch / 844f)   // preserveAspectRatio "xMidYMid slice"
        tx = -12f * density + (cw - 390f * sc) / 2f
        ty = -12f * density + (ch - 844f * sc) / 2f

        // northern lights: two blurred bands, half resolution (they are soft anyway)
        aur = layer(-80f, 40f, 480f, 340f, sc * 0.5f) { c, m ->
            val p1 = Path()
            p1.moveTo(-30f, 150f); p1.cubicTo(70f, 80f, 160f, 200f, 260f, 120f); p1.cubicTo(360f, 40f, 380f, 100f, 430f, 150f)
            p1.lineTo(430f, 210f); p1.cubicTo(340f, 170f, 270f, 240f, 170f, 190f); p1.cubicTo(70f, 140f, 40f, 180f, -30f, 230f); p1.close()
            val p2 = Path()
            p2.moveTo(-30f, 230f); p2.cubicTo(90f, 170f, 190f, 260f, 300f, 200f); p2.cubicTo(410f, 140f, 400f, 200f, 430f, 220f)
            p2.lineTo(430f, 250f); p2.cubicTo(320f, 240f, 220f, 290f, 110f, 250f); p2.cubicTo(0f, 210f, 20f, 260f, -30f, 270f); p2.close()
            p1.transform(m); p2.transform(m)
            c.drawPath(p1, blur(0xFFA8E6D8.toInt(), 1f, m))
            c.drawPath(p2, blur(0xFFC9B8F0.toInt(), 0.8f, m))
        }

        // far ice range, with the part the near range covers cut out (the near range is 90 % opaque)
        far = layer(-20f, 398f, 430f, 850f, sc) { c, m ->
            c.concat(m)
            val grad = LinearGradient(0f, 398f, 0f, 900f, cWhite, cIce2, Shader.TileMode.CLAMP)
            poly(tmp, farPoly); c.drawPath(tmp, fillP(cWhite, 0.75f, grad))
            val shard = fillP(cFar, 0.41f)
            var i = 0
            while (i < farShards.size) {
                tmp.reset(); tmp.moveTo(farShards[i], farShards[i + 1]); tmp.lineTo(farShards[i + 2], farShards[i + 3])
                tmp.lineTo(farShards[i + 4], farShards[i + 5]); tmp.lineTo(farShards[i + 6], farShards[i + 7]); tmp.close()
                c.drawPath(tmp, shard); i += 8
            }
            val hl = strokeP(cWhite, 1.6f, 0.9f)
            i = 0
            while (i < farLines.size) { c.drawLine(farLines[i], farLines[i + 1], farLines[i + 2], farLines[i + 3], hl); i += 4 }
            val cut = fillP(0xFF000000.toInt(), 0.9f); cut.xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_OUT)
            poly(tmp, nearPoly); c.drawPath(tmp, cut)
        }

        // mist: two soft ellipses (blur rendered once)
        mist = layer(-100f, 520f, 650f, 760f, sc * 0.5f) { c, m ->
            val p = blur(cWhite, 1f, m)
            rU.set(120f - 170f, 600f - 26f, 120f + 170f, 600f + 26f); m.mapRect(rU); c.drawOval(rU, p)
            rU.set(420f - 180f, 690f - 22f, 420f + 180f, 690f + 22f); m.mapRect(rU); c.drawOval(rU, p)
        }

        // near ice range with its snow tongue and cracks, and the sea (water tint) over it; the shelf is cut out
        near = layer(-20f, 500f, 430f, 850f, sc) { c, m ->
            c.concat(m)
            val grad = LinearGradient(0f, 500f, 0f, 900f, cWhite, cIce2, Shader.TileMode.CLAMP)
            poly(tmp, nearPoly); c.drawPath(tmp, fillP(cWhite, 0.9f, grad))
            val shard = fillP(cNear, 0.5f)
            val hl = strokeP(cWhite, 1.6f, 0.9f)
            var i = 0
            while (i < nearShards.size) {
                tmp.reset(); tmp.moveTo(nearShards[i], nearShards[i + 1]); tmp.lineTo(nearShards[i + 2], nearShards[i + 3])
                tmp.lineTo(nearShards[i + 4], nearShards[i + 5]); tmp.lineTo(nearShards[i + 6], nearShards[i + 7]); tmp.close()
                c.drawPath(tmp, shard); i += 8
            }
            i = 0
            while (i < nearLines.size) { c.drawLine(nearLines[i], nearLines[i + 1], nearLines[i + 2], nearLines[i + 3], hl); i += 4 }
            tmp.reset(); tmp.moveTo(210f, 600f); tmp.cubicTo(200f, 650f, 170f, 690f, 110f, 720f); tmp.lineTo(250f, 720f)
            tmp.cubicTo(250f, 690f, 260f, 650f, 270f, 610f); tmp.close()
            c.drawPath(tmp, fillP(cWhite, 0.7f))
            val cr = strokeP(cFar, 1f, 0.6f)
            tmp.reset(); tmp.moveTo(200f, 640f); tmp.cubicTo(210f, 650f, 230f, 652f, 246f, 646f); c.drawPath(tmp, cr)
            tmp.reset(); tmp.moveTo(180f, 672f); tmp.cubicTo(200f, 684f, 230f, 686f, 252f, 678f); c.drawPath(tmp, cr)
            tmp.reset(); tmp.moveTo(150f, 700f); tmp.cubicTo(180f, 712f, 220f, 712f, 250f, 704f); c.drawPath(tmp, cr)
            c.drawRect(-20f, 770f, 410f, 850f, fillP(cWater, 0.45f))
            val cut = fillP(0xFF000000.toInt(), 1f); cut.xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_OUT)
            poly(tmp, shelfPoly); c.drawPath(tmp, cut)
        }

        // ice shelf in the lower left: opaque, with vertical cracks
        shelf = layer(-20f, 688f, 185f, 850f, sc) { c, m ->
            c.concat(m)
            val grad = LinearGradient(0f, 690f, 0f, 844f, cWhite, cIce2, Shader.TileMode.CLAMP)
            poly(tmp, shelfPoly); c.drawPath(tmp, fillP(cWhite, 1f, grad))
            val cr = strokeP(cFar, 1f, 0.55f)
            var i = 0
            while (i < shelfCracks.size) { c.drawLine(shelfCracks[i], shelfCracks[i + 1], shelfCracks[i + 2], shelfCracks[i + 3], cr); i += 4 }
        }

        // the three icebergs, each flattened into one small bitmap
        for (b in 0 until 3) {
            val all = arrayOf(bergWhite[b], bergShade[b], bergRefl[b])
            var minX = 1e9f; var minY = 1e9f; var maxX = -1e9f; var maxY = -1e9f
            for (a in all) { var i = 0; while (i < a.size) { minX = Math.min(minX, a[i]); maxX = Math.max(maxX, a[i]); minY = Math.min(minY, a[i + 1]); maxY = Math.max(maxY, a[i + 1]); i += 2 } }
            val ox = (minX + maxX) / 2f   // transform-origin 50% 100% of the group's box
            val oy = maxY
            bergOx[b] = ox; bergOy[b] = oy
            bergDst[b].set(minX - 1f - ox, minY - 1f - oy, maxX + 1f - ox, maxY + 1f - oy)
            bergs[b] = layer(minX - 1f, minY - 1f, maxX + 1f, maxY + 1f, sc) { c, m ->
                c.concat(m)
                poly(tmp, bergWhite[b]); c.drawPath(tmp, fillP(cWhite, 1f))
                poly(tmp, bergShade[b]); c.drawPath(tmp, fillP(cFar, 0.5f))
                poly(tmp, bergRefl[b]); c.drawPath(tmp, fillP(cWhite, 0.35f))
            }
        }
        built = true
    }

    /** Draws a pre-rendered layer whose svg origin sits at pixel (ox, oy). */
    private fun blit(cv: Canvas, l: Layer?, ox: Float, oy: Float) {
        if (l == null) return
        rPx.set(ox + l.x0 * sc, oy + l.y0 * sc, ox + l.x1 * sc, oy + l.y1 * sc)
        cv.drawBitmap(l.bmp, null, rPx, pBmp)
    }

    override fun draw(cv: Canvas, w: Float, h: Float, t: Float, dt: Float, page: Float) {
        if (!built) return
        val s = sc
        val pg = page * dens   // sideways drift: far layers 8 dp per page, nearer ones a little more
        val shFar = tx - pg * 8f
        val shNear = tx - pg * 10f
        val shSnow = tx - pg * 12f

        // northern lights: translateX -14..14, scaleY .9..1.12 (about y = 0), opacity .45..0.9, 10 s ease-in-out alternate
        val aurL = aur
        if (aurL != null) {
            val e = lk(easeIO, ping(t / 10f))
            pBmp.alpha = a8(G * (0.45f + 0.45f * e))
            cv.save()
            cv.translate(tx - pg * 6f + (-14f + 28f * e) * s, ty)
            cv.scale(s, s * (0.9f + 0.22f * e))
            rU.set(aurL.x0, aurL.y0, aurL.x1, aurL.y1)
            cv.drawBitmap(aurL.bmp, null, rU, pBmp)
            cv.restore()
        }

        pBmp.alpha = a8(G)
        // far range: translateX -5..5, 26 s ease-in-out alternate
        blit(cv, far, shFar + (-5f + 10f * lk(easeIO, ping(t / 26f))) * s, ty)

        // mist (clipped to what is above the near range): slides 160 units in 22 s, back and forth so it never jumps
        val nox = shNear + (5f - 10f * lk(easeIO, ping(t / 18f))) * s   // near range: 18 s alternate-reverse
        val mistL = mist
        if (mistL != null) {
            cv.save()
            cv.translate(nox, ty)
            cv.scale(s, s)
            cv.clipPath(aboveNear)
            cv.translate(-80f + 160f * ping(t / 22f), 0f)
            rU.set(mistL.x0, mistL.y0, mistL.x1, mistL.y1)
            cv.drawBitmap(mistL.bmp, null, rU, pBmp)
            cv.restore()
        }

        blit(cv, near, nox, ty)

        // sea ripples: one wave period is 30 units, the page slides 60 per 6 s linearly
        pWave.alpha = a8(G)
        cv.save()
        cv.translate(shNear, ty)
        cv.scale(s, s)
        cv.translate(-60f * ((t / 6f) % 1f), 0f)
        cv.drawPath(wave, pWave)
        cv.restore()

        // icebergs: translateY -2..3, rotate -1.5..1.5 deg about the bottom centre, ease-in-out alternate
        for (b in 0 until 3) {
            val l = bergs[b] ?: continue
            val e = lk(easeIO, ping((t - bergDelay[b]) / bergDur[b]))
            cv.save()
            cv.translate(shNear, ty)
            cv.scale(s, s)
            cv.translate(bergOx[b], bergOy[b] - 2f + 5f * e)
            cv.rotate(-1.5f + 3f * e)
            cv.drawBitmap(l.bmp, null, bergDst[b], pBmp)
            cv.restore()
        }

        blit(cv, shelf, shNear, ty)

        // sparkles on the peaks: 3.2 s, each 0.9 s after the other; they only show between 60 % and 100 % of the cycle
        for (i in 0 until 6) {
            val u = ((t + 0.9f * i) / 3.2f) % 1f
            if (u < 0.6f) continue
            val sc2: Float; val rot: Float; val op: Float
            if (u < 0.75f) { val e = lk(easeIO, (u - 0.6f) / 0.15f); sc2 = 1.3f * e; rot = 45f * e; op = e }
            else { val e = lk(easeIO, (u - 0.75f) / 0.25f); sc2 = 1.3f * (1f - e); rot = 45f * (1f - e); op = 1f - e }
            if (op <= 0.003f) continue
            pFill.alpha = a8(G * op)
            cv.save()
            cv.translate((if (i < 3) shFar else shNear) + sparkX[i] * s, ty + sparkY[i] * s)
            cv.scale(s * sc2, s * sc2)
            cv.rotate(rot)
            cv.drawPath(star, pFill)
            cv.restore()
        }

        // snow: two eased legs (the CSS keyframes use the default "ease" for each), loops by modulo
        pFill.alpha = a8(G)
        for (i in 0 until snowN) {
            val o = i * 4
            val u = ((t - snow[o + 2]) / snow[o + 1]) % 1f
            val x: Float; val y: Float
            if (u < 0.5f) { val e = lk(easeStd, u * 2f); y = -20f + 440f * e; x = snow[o] + 18f * e }
            else { val e = lk(easeStd, (u - 0.5f) * 2f); y = 420f + 450f * e; x = snow[o] + 18f - 28f * e }
            cv.drawCircle(shSnow + x * s, ty + y * s, snow[o + 3] * s, pFill)
        }
    }
}
