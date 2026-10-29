package com.example.buddy.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.tan

/**
 * Native animated background of the Ocean look, ported from assets/scene/ocean.html: light rays, caustics, jellyfish,
 * background sharks, a school, a turtle, eight fish, a seahorse, a crab, eight kelp blades, rising bubbles and the sandy floor.
 * The original is one SVG (viewBox 390x844, slice) faded to 62% on top of the app's own water gradient. Everything is drawn in
 * viewBox units; the parts that are not simple strokes are pre-rendered once into small bitmaps (sprites) in [onSize], so a frame
 * is about 65 draw calls and allocates nothing.
 */
class OceanScene : ThemeScene() {
    override val ready = true

    private class Sprite(val bmp: Bitmap, val x0: Float, val y0: Float, val ppu: Float)

    // ---- geometry of the current size ----
    private var sc = 1f        // device px per viewBox unit
    private var offX = 0f
    private var offY = 0f
    private var dens = 1f
    private var built = false

    // ---- paints ----
    private val pSp = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val pShark = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val pRay = Paint(Paint.ANTI_ALIAS_FLAG)
    private val pCaust = Paint(Paint.ANTI_ALIAS_FLAG)
    private val pSchool = Paint(Paint.ANTI_ALIAS_FLAG)
    private val pTent = Paint(Paint.ANTI_ALIAS_FLAG)
    private val pFlip = Paint(Paint.ANTI_ALIAS_FLAG)
    private val pLegs = Paint(Paint.ANTI_ALIAS_FLAG)

    // ---- paths ----
    private val rayPath = Array(3) { Path() }
    private val rayShader = arrayOfNulls<Shader>(3)
    private val caustPath = Path()
    private val schoolPath = Path()
    private val flipPath = Path()
    private val legsPath = Path()
    private val tentPath = Array(4) { Path() }
    private val tentPivX = FloatArray(4)
    private var flipPx = 0f
    private var flipPy = 0f

    // ---- sprites ----
    private var strip: Sprite? = null
    private var shark: Sprite? = null
    private var bell: Sprite? = null
    private var turtle: Sprite? = null
    private var horse: Sprite? = null
    private var crab: Sprite? = null
    private val fishBody = arrayOfNulls<Sprite>(4)
    private val fishTail = arrayOfNulls<Sprite>(4)
    private val kelpSp = arrayOfNulls<Sprite>(8)
    private val kelpPx = FloatArray(8)
    private val bubSp = arrayOfNulls<Sprite>(16)

    // ---- data from the SVG ----
    private val rayTop = floatArrayOf(760f, 700f, 760f)

    // jellies: x, y, delay
    private val jx = floatArrayOf(300f, 90f)
    private val jy = floatArrayOf(200f, 560f)
    private val jd = floatArrayOf(0f, -4f)
    private val tentDelay = floatArrayOf(0f, -0.3f, -0.6f, -0.9f)

    // background sharks: y, scale, duration, delay
    private val shY = floatArrayOf(140f, 600f)
    private val shS = floatArrayOf(1f, 0.75f)
    private val shDur = floatArrayOf(26f, 34f)
    private val shDel = floatArrayOf(-4f, -20f)

    // fish: type (0 clown, 1 blue, 2 yellow, 3 pink), dir (+1 swims right, -1 left), scale, y, dy, duration, delay
    private val fType = intArrayOf(0, 1, 2, 3, 0, 1, 2, 3)
    private val fDir = floatArrayOf(1f, -1f, 1f, -1f, -1f, 1f, -1f, 1f)
    private val fScale = floatArrayOf(1.1f, 1.2f, 0.9f, 0.85f, 1f, 0.9f, 1.1f, 0.95f)
    private val fY = floatArrayOf(300f, 420f, 520f, 250f, 610f, 360f, 470f, 680f)
    private val fDy = floatArrayOf(-4f, 7f, 33f, -14f, 42f, -34f, 6f, 47f)
    private val fDur = floatArrayOf(18f, 22f, 15f, 20f, 24f, 19f, 26f, 17f)
    private val fDel = floatArrayOf(0f, -6f, -3f, -11f, -15f, -9f, -2f, -7f)

    // kelp: stem as 8 numbers (x0 y0, c1, c2, p1, c3, c4, p2) then width, duration, delay, then 5 leaves of (cx, cy, angle, pivot x)
    private val kelpData = arrayOf(
        floatArrayOf(8f, 860f, 26f, 704f, -6f, 574f, 18f, 444f, 24f, 392f, 12f, 340f, 16f, 330f, 7f, 4f, 0f,
            4f, 756f, -30f, 0f, 14f, 678f, 30f, 16f, -1f, 600f, -30f, 0f, 16f, 522f, 30f, 16f, -2f, 444f, -30f, 0f),
        floatArrayOf(24f, 860f, 42f, 746f, 10f, 651f, 34f, 556f, 40f, 518f, 28f, 480f, 32f, 470f, 7f, 5f, -0.7f,
            17f, 784f, -30f, 16f, 29f, 727f, 30f, 32f, 14f, 670f, -30f, 16f, 34f, 613f, 30f, 32f, 15f, 556f, -30f, 16f),
        floatArrayOf(40f, 860f, 58f, 770f, 26f, 695f, 50f, 620f, 56f, 590f, 44f, 560f, 48f, 550f, 5f, 6f, -1.4f,
            29f, 800f, -30f, 32f, 52f, 755f, 30f, 48f, 30f, 710f, -30f, 32f, 46f, 665f, 30f, 48f, 33f, 620f, -30f, 32f),
        floatArrayOf(-4f, 860f, 14f, 668f, -18f, 508f, 6f, 348f, 12f, 284f, 0f, 220f, 4f, 210f, 7f, 4f, -2.1f,
            -11f, 732f, -30f, -12f, 4f, 636f, 30f, 4f, -16f, 540f, -30f, -12f, 4f, 444f, 30f, 4f, -15f, 348f, -30f, -12f),
        floatArrayOf(382f, 860f, 364f, 692f, 396f, 552f, 372f, 412f, 366f, 356f, 378f, 300f, 374f, 290f, 6f, 5f, -2.8f,
            392f, 748f, 30f, 390f, 375f, 664f, -30f, 374f, 390f, 580f, 30f, 390f, 377f, 496f, -30f, 374f, 392f, 412f, 30f, 390f),
        floatArrayOf(366f, 860f, 348f, 740f, 380f, 640f, 356f, 540f, 350f, 500f, 362f, 460f, 358f, 450f, 7f, 6f, -3.5f,
            377f, 780f, 30f, 374f, 360f, 720f, -30f, 358f, 377f, 660f, 30f, 374f, 355f, 600f, -30f, 358f, 373f, 540f, 30f, 374f),
        floatArrayOf(350f, 860f, 332f, 776f, 364f, 706f, 340f, 636f, 334f, 608f, 346f, 580f, 342f, 570f, 5f, 4f, -4.2f,
            361f, 804f, 30f, 358f, 343f, 762f, -30f, 342f, 355f, 720f, 30f, 358f, 341f, 678f, -30f, 342f, 355f, 636f, 30f, 358f),
        floatArrayOf(394f, 860f, 376f, 662f, 408f, 497f, 384f, 332f, 378f, 266f, 390f, 200f, 386f, 190f, 6f, 5f, -4.9f,
            402f, 728f, 30f, 402f, 386f, 629f, -30f, 386f, 402f, 530f, 30f, 402f, 388f, 431f, -30f, 386f, 403f, 332f, 30f, 402f)
    )
    private val kelpDur = FloatArray(8)
    private val kelpDel = FloatArray(8)
    private val kelpPy = FloatArray(8)

    // bubbles: x, radius, duration, delay
    private val bubX = floatArrayOf(310f, 313f, 75f, 314f, 335f, 184f, 29f, 108f, 177f, 156f, 146f, 146f, 279f, 138f, 96f, 60f)
    private val bubR = floatArrayOf(5.6f, 3.9f, 2.8f, 4.4f, 3.5f, 3.8f, 5.2f, 5.7f, 5.0f, 4.3f, 4.2f, 2.1f, 4.6f, 4.9f, 4.1f, 4.4f)
    private val bubDur = floatArrayOf(11f, 8.8f, 10.3f, 11f, 9.9f, 7.8f, 8.1f, 10.4f, 7.1f, 10.2f, 8f, 6.6f, 8.7f, 11.4f, 9.9f, 8f)
    private val bubDel = floatArrayOf(-5.2f, -8.8f, -6.5f, -11.8f, -9f, -5.7f, -5f, -0.7f, -0.9f, -1.6f, -4.4f, -1.1f, -7.4f, -7.9f, -8.5f, -0.2f)
    private val bubDx = floatArrayOf(0f, 8f, -6f, 7f, 0f)

    private val tmpR = RectF()
    private val tmpR2 = RectF()
    private val tmpM = Matrix()

    private fun col(rgb: Int, a: Float): Int = ((a * 255f + 0.5f).toInt() shl 24) or (rgb and 0xFFFFFF)

    // ================= time helpers =================
    /** position 0..1 inside a looping animation of [dur] seconds that started at [delay] (negative delay = already running) */
    private fun ph(t: Float, dur: Float, delay: Float): Float {
        val x = (t.toDouble() - delay) / dur
        return (x - floor(x)).toFloat()
    }

    /** 0..1..0 ping-pong ("alternate") of a [dur] second leg */
    private fun alt(t: Float, dur: Float, delay: Float): Float {
        val x = (t.toDouble() - delay) / dur
        val m = x - 2.0 * floor(x / 2.0)
        return (if (m < 1.0) m else 2.0 - m).toFloat()
    }

    /** 0..1..0 triangle of a 0..1 phase (keyframes 0% / 50% / 100%) */
    private fun tri(p: Float): Float = 1f - abs(2f * p - 1f)

    /** CSS ease-in-out: cubic-bezier(.42, 0, .58, 1) */
    private fun ease(x: Float): Float {
        if (x <= 0f) return 0f
        if (x >= 1f) return 1f
        var lo = 0f
        var hi = 1f
        var tt = x
        for (i in 0 until 9) {
            val m = 1f - tt
            val xx = 3f * m * m * tt * 0.42f + 3f * m * tt * tt * 0.58f + tt * tt * tt
            if (xx < x) lo = tt else hi = tt
            tt = (lo + hi) * 0.5f
        }
        val m = 1f - tt
        return 3f * m * tt * tt + tt * tt * tt
    }

    private fun tanDeg(a: Float): Float = tan(a * 0.017453292f)

    // ================= building =================
    private fun makeSprite(x0: Float, y0: Float, x1: Float, y1: Float, ppu: Float, draw: (Canvas) -> Unit): Sprite {
        val bw = max(1, ceil((x1 - x0) * ppu).toInt())
        val bh = max(1, ceil((y1 - y0) * ppu).toInt())
        val bmp = Bitmap.createBitmap(bw, bh, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.scale(ppu, ppu)
        c.translate(-x0, -y0)
        draw(c)
        return Sprite(bmp, x0, y0, ppu)
    }

    private fun spr(cv: Canvas, sp: Sprite, p: Paint) {
        cv.save()
        cv.translate(sp.x0, sp.y0)
        cv.scale(1f / sp.ppu, 1f / sp.ppu)
        cv.drawBitmap(sp.bmp, 0f, 0f, p)
        cv.restore()
    }

    private fun fillP(rgb: Int, a: Float = 1f) = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL; color = col(rgb, a) }
    private fun strokeP(rgb: Int, w: Float, a: Float = 1f, round: Boolean = false) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = w; color = col(rgb, a)
        if (round) strokeCap = Paint.Cap.ROUND
    }

    override fun onSize(w: Float, h: Float, density: Float) {
        dens = density
        // the scene box is the page plus 12 css px on every side, the viewBox 390x844 is sliced into it, centred
        val bw = w + 24f * density
        val bh = h + 24f * density
        sc = max(bw / 390f, bh / 844f)
        offX = -12f * density + (bw - 390f * sc) / 2f
        offY = -12f * density + (bh - 844f * sc) / 2f

        val op = 0.62f
        pSp.alpha = (op * 255f + 0.5f).toInt()
        pShark.alpha = (op * 0.75f * 255f + 0.5f).toInt()
        pRay.style = Paint.Style.FILL
        pCaust.style = Paint.Style.STROKE; pCaust.strokeWidth = 1.4f; pCaust.color = col(0xFFFFFF, 0.5f * op)
        pSchool.style = Paint.Style.FILL; pSchool.color = col(0xC9D8E6, op)
        pTent.style = Paint.Style.STROKE; pTent.strokeWidth = 1.6f; pTent.color = col(0xD9A8F5, op)
        pFlip.style = Paint.Style.FILL; pFlip.color = col(0x7FBF8A, op)
        pLegs.style = Paint.Style.STROKE; pLegs.strokeWidth = 2f; pLegs.strokeCap = Paint.Cap.ROUND; pLegs.color = col(0xE8503A, op)

        buildPaths()
        buildSprites()
        built = true
    }

    private fun buildPaths() {
        // light rays: white, vertical gradient from .85 to 0 over the path's own box
        rayPath[0].apply { rewind(); moveTo(120f, 0f); lineTo(170f, 0f); lineTo(110f, 760f); lineTo(30f, 760f); close() }
        rayPath[1].apply { rewind(); moveTo(220f, 0f); lineTo(250f, 0f); lineTo(290f, 700f); lineTo(220f, 700f); close() }
        rayPath[2].apply { rewind(); moveTo(300f, 0f); lineTo(340f, 0f); lineTo(430f, 760f); lineTo(350f, 760f); close() }
        for (i in 0 until 3) rayShader[i] = LinearGradient(0f, 0f, 0f, rayTop[i], 0xD9FFFFFF.toInt(), 0x00FFFFFF, Shader.TileMode.CLAMP)

        // caustics: five wavy lines "q12 -8 24 0 t24 0 t24 0"
        caustPath.rewind()
        val cx = floatArrayOf(40f, 180f, 260f, 90f, 300f)
        val cy = floatArrayOf(120f, 80f, 160f, 220f, 60f)
        for (i in 0 until 5) {
            caustPath.moveTo(cx[i], cy[i])
            caustPath.rQuadTo(12f, -8f, 24f, 0f)
            caustPath.rQuadTo(12f, 8f, 24f, 0f)
            caustPath.rQuadTo(12f, -8f, 24f, 0f)
        }

        // school of ten small fish, one path
        schoolPath.rewind()
        val sx = floatArrayOf(0f, 12f, 6f, 20f, 26f, 16f, -8f, 32f, -4f, 36f)
        val sy = floatArrayOf(0f, 4f, -8f, -4f, 6f, 12f, 8f, 0f, -12f, -10f)
        for (i in 0 until 10) {
            val x = sx[i]; val y = sy[i]
            schoolPath.moveTo(x - 5f, y)
            schoolPath.cubicTo(x - 3f, y - 3f, x + 3f, y - 3f, x + 5f, y)
            schoolPath.cubicTo(x + 3f, y + 3f, x - 3f, y + 3f, x - 5f, y)
            schoolPath.close()
            schoolPath.moveTo(x - 5f, y); schoolPath.lineTo(x - 8f, y + 2f); schoolPath.lineTo(x - 8f, y - 2f); schoolPath.close()
        }

        // turtle flippers (both in one path; they turn around the top-centre of their box)
        flipPath.rewind()
        flipPath.moveTo(-6f, 6f); flipPath.cubicTo(-14f, 14f, -24f, 16f, -30f, 14f); flipPath.cubicTo(-24f, 8f, -14f, 4f, -6f, 2f); flipPath.close()
        flipPath.moveTo(8f, 6f); flipPath.cubicTo(14f, 16f, 22f, 20f, 28f, 20f); flipPath.cubicTo(24f, 12f, 16f, 6f, 8f, 2f); flipPath.close()
        flipPath.computeBounds(tmpR, true)
        flipPx = tmpR.centerX(); flipPy = tmpR.top

        // crab legs
        legsPath.rewind()
        legsPath.moveTo(-8f, 2f); legsPath.lineTo(-16f, 6f)
        legsPath.moveTo(-8f, 2f); legsPath.lineTo(-16f, 8f)
        legsPath.moveTo(-8f, 2f); legsPath.lineTo(-16f, 10f)
        legsPath.moveTo(8f, 2f); legsPath.lineTo(16f, 6f)
        legsPath.moveTo(8f, 2f); legsPath.lineTo(16f, 8f)
        legsPath.moveTo(8f, 2f); legsPath.lineTo(16f, 10f)

        // jellyfish tentacles, each skews around the top-centre of its own box
        tentPath[0].rewind(); tentPath[0].moveTo(-9f, 0f); tentPath[0].cubicTo(-13f, 10f, -5f, 18f, -9f, 30f)
        tentPath[1].rewind(); tentPath[1].moveTo(-3f, 0f); tentPath[1].cubicTo(-7f, 10f, 1f, 18f, -3f, 30f)
        tentPath[2].rewind(); tentPath[2].moveTo(3f, 0f); tentPath[2].cubicTo(-1f, 10f, 7f, 18f, 3f, 30f)
        tentPath[3].rewind(); tentPath[3].moveTo(9f, 0f); tentPath[3].cubicTo(5f, 10f, 13f, 18f, 9f, 30f)
        for (i in 0 until 4) { tentPath[i].computeBounds(tmpR, true); tentPivX[i] = tmpR.centerX() }
    }

    private fun buildSprites() {
        // ---- sandy floor, shell, coral and rocks (one strip along the bottom, drawn at full strength, faded when drawn) ----
        strip = makeSprite(-20f, 750f, 410f, 870f, sc) { c ->
            val sand = Path()
            sand.moveTo(-20f, 806f); sand.cubicTo(60f, 792f, 140f, 812f, 220f, 800f); sand.cubicTo(300f, 790f, 350f, 806f, 410f, 798f)
            sand.lineTo(410f, 870f); sand.lineTo(-20f, 870f); sand.close()
            c.drawPath(sand, fillP(0xF2E2B8, 0.85f))
            val shell = Path()
            shell.moveTo(192f, 820f); shell.lineTo(200f, 810f); shell.lineTo(208f, 820f); shell.lineTo(204f, 822f); shell.lineTo(196f, 822f); shell.close()
            c.drawPath(shell, fillP(0xFFD0B8))
            val bump = Path()
            bump.moveTo(330f, 818f); bump.rQuadTo(6f, -10f, 12f, 0f); bump.close()
            c.drawPath(bump, fillP(0xFFF0E0))
            val coral = strokeP(0xFF8FB1, 5f, 1f, true)
            val cp = Path()
            val ex = floatArrayOf(-14f, -19.6f, -15.4f, -4f, -5.6f, -4.4f, 8f, 11.2f, 8.8f, 16f, 22.4f, 17.6f)
            for (i in 0 until 4) {
                cp.moveTo(110f, 820f)
                cp.rCubicTo(ex[i * 3], -20f, ex[i * 3 + 1], -36f, ex[i * 3 + 2], -50f)
            }
            c.drawPath(cp, coral)
            c.drawCircle(270f, 816f, 14f, fillP(0xB49CFF))
            c.drawCircle(286f, 820f, 10f, fillP(0xC9B6FF))
            c.drawCircle(256f, 822f, 9f, fillP(0xA88CF0))
        }

        // ---- background shark (drawn in its own 0..246 x 0..110 units, scaled by .42 on screen) ----
        shark = makeSprite(0f, 0f, 246f, 110f, sc * 0.42f) { c ->
            val p = Path()
            p.moveTo(200f, 54f); p.cubicTo(212f, 40f, 226f, 22f, 240f, 6f); p.cubicTo(235f, 28f, 233f, 44f, 238f, 57f)
            p.cubicTo(233f, 70f, 235f, 88f, 244f, 108f); p.cubicTo(228f, 90f, 214f, 74f, 200f, 62f); p.close()
            c.drawPath(p, fillP(0x7E93AA))
            p.rewind(); p.moveTo(100f, 34f); p.cubicTo(108f, 18f, 120f, 6f, 136f, 1f); p.cubicTo(131f, 16f, 131f, 26f, 138f, 37f); p.close()
            c.drawPath(p, fillP(0x6D8299))
            p.rewind(); p.moveTo(4f, 58f); p.cubicTo(14f, 44f, 50f, 32f, 104f, 32f); p.cubicTo(150f, 32f, 188f, 42f, 208f, 54f)
            p.cubicTo(190f, 67f, 150f, 78f, 104f, 78f); p.cubicTo(64f, 78f, 30f, 70f, 4f, 60f); p.close()
            c.drawPath(p, fillP(0x8EA3B8))
            p.rewind(); p.moveTo(10f, 62f); p.cubicTo(40f, 72f, 90f, 78f, 140f, 73f); p.cubicTo(120f, 69f, 60f, 67f, 10f, 62f); p.close()
            c.drawPath(p, fillP(0xEEF3F7))
            p.rewind(); p.moveTo(96f, 70f); p.cubicTo(104f, 84f, 112f, 94f, 128f, 101f); p.cubicTo(122f, 88f, 118f, 80f, 114f, 72f); p.close()
            c.drawPath(p, fillP(0x6D8299))
            c.drawCircle(30f, 48f, 4f, fillP(0x0F1822))
        }

        // ---- jellyfish bell (pulses as one piece) ----
        bell = makeSprite(-18f, -17f, 18f, 2f, sc * 1.2f) { c ->
            val p = Path()
            p.moveTo(-16f, 0f); p.cubicTo(-16f, -20f, 16f, -20f, 16f, 0f); p.cubicTo(10f, -3f, 4f, 3f, 0f, 0f); p.cubicTo(-4f, 3f, -10f, -3f, -16f, 0f); p.close()
            c.drawPath(p, fillP(0xE8B8FF, 0.85f))
            p.rewind(); p.moveTo(-10f, -10f); p.cubicTo(-6f, -16f, 4f, -16f, 8f, -12f)
            c.drawPath(p, strokeP(0xFFFFFF, 2f, 0.7f))
        }

        // ---- turtle (everything but the flippers) ----
        turtle = makeSprite(-22f, -15f, 32f, 15f, sc * 1.1f) { c ->
            c.drawOval(-20f, -13f, 20f, 13f, fillP(0x5F9A62))
            val p = Path()
            p.moveTo(-12f, -2f); p.lineTo(-4f, -8f); p.lineTo(6f, -8f); p.lineTo(12f, -2f); p.lineTo(6f, 6f); p.lineTo(-4f, 6f); p.close()
            c.drawPath(p, strokeP(0x3F7A45, 1.4f))
            c.drawCircle(24f, -2f, 6f, fillP(0x8FCF96))
            c.drawCircle(26f, -4f, 1.3f, fillP(0x1A2A20))
        }

        // ---- fish: four kinds, body and tail apart (the tail wags) ----
        val tailC = intArrayOf(0xFF8A2A, 0xFFD34D, 0xFFC21A, 0xFF6F9A)
        val bodyC = intArrayOf(0xFF8A2A, 0x3F7FE0, 0xFFD34D, 0xFF8FB1)
        for (k in 0 until 4) {
            val tc = tailC[k]
            val bc = bodyC[k]
            fishTail[k] = makeSprite(-29f, -11f, -15f, 11f, sc * 1.25f) { c ->
                val p = Path()
                p.moveTo(-16f, 0f); p.lineTo(-28f, -10f); p.lineTo(-25f, 0f); p.lineTo(-28f, 10f); p.close()
                c.drawPath(p, fillP(tc))
                c.drawPath(p, strokeP(0xFFFFFF, 0.6f))
            }
            fishBody[k] = makeSprite(-20f, -17f, 20f, 14f, sc * 1.25f) { c ->
                val p = Path()
                p.moveTo(-18f, 0f); p.cubicTo(-12f, -12f, 10f, -12f, 18f, 0f); p.cubicTo(10f, 12f, -12f, 12f, -18f, 0f); p.close()
                c.drawPath(p, fillP(bc))
                c.drawPath(p, strokeP(0xFFFFFF, 0.7f))
                if (k == 0) {
                    p.rewind()
                    p.moveTo(-4f, -9f); p.cubicTo(-1f, -3f, -1f, 3f, -4f, 9f); p.lineTo(0f, 9f); p.cubicTo(2f, 3f, 2f, -3f, 0f, -9f); p.close()
                    p.moveTo(8f, -6f); p.cubicTo(10f, -2f, 10f, 2f, 8f, 6f); p.lineTo(11f, 5f); p.cubicTo(12f, 2f, 12f, -2f, 11f, -5f); p.close()
                    c.drawPath(p, fillP(0xFFFFFF))
                }
                if (k == 1) {
                    p.rewind(); p.moveTo(-12f, -2f); p.cubicTo(-2f, -6f, 8f, -6f, 14f, -2f)
                    c.drawPath(p, strokeP(0x1F3F90, 2f))
                }
                p.rewind(); p.moveTo(-4f, -9f); p.cubicTo(0f, -15f, 6f, -14f, 8f, -8f)
                c.drawPath(p, fillP(tc, 0.9f))
                c.drawCircle(11f, -3f, 2.6f, fillP(0xFFFFFF))
                c.drawCircle(11.6f, -3f, 1.4f, fillP(0x1A2230))
            }
        }

        // ---- seahorse ----
        horse = makeSprite(-14f, -28f, 17f, 28f, sc * 1.1f) { c ->
            val p = Path()
            p.moveTo(0f, -24f); p.cubicTo(8f, -26f, 10f, -18f, 6f, -14f); p.cubicTo(12f, -8f, 12f, 4f, 4f, 10f); p.cubicTo(0f, 14f, 2f, 22f, -4f, 24f)
            p.cubicTo(-10f, 26f, -12f, 18f, -6f, 18f); p.cubicTo(-2f, 18f, -4f, 12f, -6f, 8f); p.cubicTo(-10f, 0f, -8f, -12f, -2f, -14f)
            p.cubicTo(-6f, -18f, -6f, -22f, 0f, -24f); p.close()
            c.drawPath(p, fillP(0xFFC46B))
            c.drawPath(p, strokeP(0xFFFFFF, 0.7f))
            c.drawLine(6f, -22f, 14f, -20f, strokeP(0xFFC46B, 3f, 1f, true))
            c.drawCircle(3f, -20f, 1.6f, fillP(0x1A1A2A))
        }

        // ---- crab body (the legs jitter separately) ----
        crab = makeSprite(-20f, -16f, 20f, 12f, sc * 1.2f) { c ->
            val red = 0xFF6A4A
            c.drawOval(-12f, -8f, 12f, 8f, fillP(red))
            c.drawCircle(-14f, -8f, 4f, fillP(red))
            c.drawCircle(14f, -8f, 4f, fillP(red))
            val st = strokeP(red, 1.6f)
            c.drawLine(-4f, -7f, -4f, -12f, st); c.drawLine(4f, -7f, 4f, -12f, st)
            c.drawCircle(-4f, -13f, 2f, fillP(0xFFFFFF)); c.drawCircle(4f, -13f, 2f, fillP(0xFFFFFF))
            c.drawCircle(-4f, -13f, 1f, fillP(0x222222)); c.drawCircle(4f, -13f, 1f, fillP(0x222222))
        }

        // ---- kelp: stem with gradient + five leaves, one sprite per blade ----
        val stem = Path()
        val leaves = Path()
        val one = Path()
        for (k in 0 until 8) {
            val d = kelpData[k]
            stem.rewind()
            stem.moveTo(d[0], d[1]); stem.cubicTo(d[2], d[3], d[4], d[5], d[6], d[7]); stem.cubicTo(d[8], d[9], d[10], d[11], d[12], d[13])
            val wd = d[14]
            kelpDur[k] = d[15]; kelpDel[k] = d[16]
            leaves.rewind()
            for (j in 0 until 5) {
                val cxl = d[17 + j * 4]; val cyl = d[18 + j * 4]; val ang = d[19 + j * 4]; val rx = d[20 + j * 4]
                one.rewind(); one.addOval(cxl - 9f, cyl - 3.6f, cxl + 9f, cyl + 3.6f, Path.Direction.CW)
                tmpM.setRotate(ang, rx, cyl); one.transform(tmpM)
                leaves.addPath(one)
            }
            stem.computeBounds(tmpR, true)
            val gTop = tmpR.top; val gBot = tmpR.bottom
            leaves.computeBounds(tmpR2, true)
            val l = minOf(tmpR.left - wd / 2f, tmpR2.left) - 1f
            val r = maxOf(tmpR.right + wd / 2f, tmpR2.right) + 1f
            val tp = minOf(tmpR.top - wd / 2f, tmpR2.top) - 1f
            val bt = maxOf(tmpR.bottom + wd / 2f, tmpR2.bottom) + 1f
            // pivot: 50% 100% of the fill box (stem geometry plus leaves)
            kelpPx[k] = (minOf(tmpR.left, tmpR2.left) + maxOf(tmpR.right, tmpR2.right)) / 2f
            kelpPy[k] = maxOf(tmpR.bottom, tmpR2.bottom)
            val grad = LinearGradient(0f, gBot, 0f, gTop, 0xFF2F7A4A.toInt(), 0xFF7FCF7A.toInt(), Shader.TileMode.CLAMP)
            val stemPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = wd; strokeCap = Paint.Cap.ROUND; shader = grad }
            val leafPaint = fillP(0x5FBF6A)
            val stemCopy = Path(stem)
            val leavesCopy = Path(leaves)
            kelpSp[k] = makeSprite(l, tp, r, bt, sc * 0.85f) { c ->
                c.drawPath(stemCopy, stemPaint)
                c.drawPath(leavesCopy, leafPaint)
            }
        }

        // ---- bubbles: a faint white disc with a 1-unit white ring, each at its own size ----
        for (i in 0 until 16) {
            val r = bubR[i]
            bubSp[i] = makeSprite(-r - 1f, -r - 1f, r + 1f, r + 1f, sc * 1.5f) { c ->
                c.drawCircle(0f, 0f, r, fillP(0xFFFFFF, 0.25f))
                c.drawCircle(0f, 0f, r, strokeP(0xFFFFFF, 1f))
            }
        }
    }

    // ================= frame =================
    override fun draw(cv: Canvas, w: Float, h: Float, t: Float, dt: Float, page: Float) {
        if (!built) return
        val far = -page * 8f * dens / sc
        val mid = -page * 10f * dens / sc
        val near = -page * 13f * dens / sc

        cv.save()
        cv.translate(offX, offY)
        cv.scale(sc, sc)

        // ---- far layer: rays, caustics (skewX -3..3 deg and fade .55..1 over 8 s) ----
        cv.save()
        cv.translate(far, 0f)
        run {
            val u = ease(alt(t, 8f, 0f))
            cv.save()
            cv.skew(tanDeg(-3f + 6f * u), 0f)
            pRay.alpha = ((0.55f + 0.45f * u) * 0.62f * 255f + 0.5f).toInt()
            for (i in 0 until 3) { pRay.shader = rayShader[i]; cv.drawPath(rayPath[i], pRay) }
            cv.restore()
            val c = ease(alt(t, 4f, 0f))
            cv.save()
            cv.translate(-8f + 16f * c, 6f * c)
            cv.drawPath(caustPath, pCaust)
            cv.restore()
        }
        cv.restore()

        // ---- the sandy floor ----
        cv.save()
        cv.translate(near, 0f)
        strip?.let { spr(cv, it, pSp) }
        cv.restore()

        // ---- jellyfish ----
        cv.save()
        cv.translate(mid, 0f)
        val bl = bell
        for (j in 0 until 2) {
            val u = ease(tri(ph(t, 14f, jd[j])))
            cv.save()
            cv.translate(jx[j] + 20f * u, jy[j] - 90f * u)
            if (bl != null) {
                val pu = ease(tri(ph(t, 1.4f, 0f)))
                cv.save()
                cv.translate(0f, 0.87f)
                cv.scale(1f + 0.15f * pu, 1f - 0.15f * pu)
                cv.translate(0f, -0.87f)
                spr(cv, bl, pSp)
                cv.restore()
            }
            for (i in 0 until 4) {
                val a = -10f + 20f * ease(alt(t, 1.4f, tentDelay[i]))
                cv.save()
                cv.translate(tentPivX[i], 0f)
                cv.skew(tanDeg(a), 0f)
                cv.translate(-tentPivX[i], 0f)
                cv.drawPath(tentPath[i], pTent)
                cv.restore()
            }
            cv.restore()
        }
        cv.restore()

        // ---- background sharks and the school (far) ----
        cv.save()
        cv.translate(far, 0f)
        val sh = shark
        if (sh != null) for (i in 0 until 2) {
            val p = ph(t, shDur[i], shDel[i])
            cv.save()
            cv.translate(460f - 620f * p, shY[i] + 30f * p)
            cv.translate(-50f, -24f)
            cv.scale(shS[i], shS[i])
            cv.scale(0.42f, 0.42f)
            spr(cv, sh, pShark)
            cv.restore()
        }
        run {
            val p = ph(t, 22f, 0f)
            val x: Float
            val y: Float
            if (p < 0.5f) { val f = p * 2f; x = -60f + 260f * f; y = 380f - 40f * f }
            else { val f = p * 2f - 1f; x = 200f + 260f * f; y = 340f + 50f * f }
            cv.save()
            cv.translate(x, y)
            cv.drawPath(schoolPath, pSchool)
            cv.restore()
        }
        cv.restore()

        // ---- turtle (mid) ----
        cv.save()
        cv.translate(mid, 0f)
        run {
            val p = ph(t, 40f, 0f)
            cv.translate(-60f + 520f * p, 170f + 60f * p)
            cv.scale(1.1f, 1.1f)
            cv.save()
            cv.rotate(-14f + 28f * ease(alt(t, 1.6f, 0f)), flipPx, flipPy)
            cv.drawPath(flipPath, pFlip)
            cv.restore()
            turtle?.let { spr(cv, it, pSp) }
        }
        cv.restore()

        // ---- fish (mid) ----
        cv.save()
        cv.translate(mid, 0f)
        val tu = ease(alt(t, 0.35f, 0f))
        val tailAng = -14f + 28f * tu
        val tailSx = 0.9f + 0.1f * tu
        for (i in 0 until 8) {
            val p = ph(t, fDur[i], fDel[i])
            val x = if (fDir[i] > 0f) -60f + 510f * p else 450f - 510f * p
            val y = fY[i] + fDy[i] * tri(p)
            val k = fType[i]
            val fs = fScale[i]
            cv.save()
            cv.translate(x, y)
            cv.scale(fDir[i] * fs, fs)
            fishTail[k]?.let {
                cv.save()
                cv.translate(-16f, 0f)
                cv.skew(0f, tanDeg(tailAng))
                cv.scale(tailSx, 1f)
                cv.translate(16f, 0f)
                spr(cv, it, pSp)
                cv.restore()
            }
            fishBody[k]?.let { spr(cv, it, pSp) }
            cv.restore()
        }
        cv.restore()

        // ---- seahorse, crab (near) ----
        cv.save()
        cv.translate(near, 0f)
        horse?.let {
            val u = ease(tri(ph(t, 4f, 0f)))
            cv.save()
            cv.translate(352f, 520f - 20f * u)
            cv.rotate(-4f + 8f * u)
            spr(cv, it, pSp)
            cv.restore()
        }
        crab?.let {
            val u = ease(alt(t, 9f, 0f))
            cv.save()
            cv.translate(170f + 80f * u, 808f - 4f * u)
            cv.save()
            cv.translate(0f, -1f + 2f * alt(t, 0.25f, 0f))
            cv.drawPath(legsPath, pLegs)
            cv.restore()
            spr(cv, it, pSp)
            cv.restore()
        }

        // ---- kelp: rotate and skew -3..3 deg around the foot ----
        for (k in 0 until 8) {
            val sp = kelpSp[k] ?: continue
            val a = -3f + 6f * ease(alt(t, kelpDur[k], kelpDel[k]))
            cv.save()
            cv.translate(kelpPx[k], kelpPy[k])
            cv.rotate(a)
            cv.skew(tanDeg(a), 0f)
            cv.translate(-kelpPx[k], -kelpPy[k])
            spr(cv, sp, pSp)
            cv.restore()
        }

        // ---- bubbles rising 940 units in 7..11 s, wobbling sideways ----
        for (i in 0 until 16) {
            val sp = bubSp[i] ?: continue
            val p = ph(t, bubDur[i], bubDel[i])
            val q = p * 4f
            val seg = if (q >= 4f) 3 else q.toInt()
            val f = q - seg
            val dx = bubDx[seg] + (bubDx[seg + 1] - bubDx[seg]) * f
            cv.save()
            cv.translate(bubX[i] + dx, 880f - 940f * p)
            spr(cv, sp, pSp)
            cv.restore()
        }
        cv.restore()

        cv.restore()
    }
}
