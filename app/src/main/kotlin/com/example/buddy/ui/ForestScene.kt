package com.example.buddy.ui

import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * Native animated background of the rainy Forest look, ported from assets/scene/forest.html.
 * Layers (all in the mockup's 390x844 view box, slice-fitted like the SVG): five soft drifting clouds (pre-blurred sprites),
 * the tree line (13 back trees, two hills, three puddles, 6 front trees) which is rendered into one small bitmap at 20 Hz so
 * that the 46% group opacity of the original is exact and the trees can sway, expanding puddle ripples, 14 falling leaves,
 * 140 rain streaks (4 batched drawLines) with splashes, and the lightning flash every 5-11 seconds.
 * Nothing is allocated while drawing.
 */
class ForestScene : ThemeScene() {
    override val ready = true

    // ---------------------------------------------------------------------------------------------------- geometry
    private var dens = 1f
    private var k = 1f          // view-box unit -> pixel
    private var ox = 0f         // view-box origin in dp
    private var oy = 0f
    private var boxW = 414f     // the scene box (screen + 12 dp on every side) in dp
    private var boxH = 868f

    // ---------------------------------------------------------------------------------------------------- clouds
    private class Cloud(val cx: Float, val cy: Float, val rx: Float, val ry: Float, val op: Float, val dur: Float, val delay: Float) {
        var bmp: Bitmap? = null
    }
    private val clouds = arrayOf(
        Cloud(60f, 40f, 140f, 60f, .70f, 40f, -25.6f),
        Cloud(300f, 30f, 160f, 70f, .75f, 52f, -1.3f),
        Cloud(190f, 110f, 150f, 45f, .50f, 46f, -12.7f),
        Cloud(20f, 150f, 110f, 40f, .45f, 60f, -13.4f),
        Cloud(360f, 160f, 120f, 40f, .45f, 56f, -41.2f),
    )
    private val cloudPs = .5f
    private val cloudPaint = Paint(Paint.FILTER_BITMAP_FLAG)

    // ---------------------------------------------------------------------------------------------------- tree line
    // x, y, scale, sway delay, sway duration
    private val back = floatArrayOf(
        -10f, 748f, .5303274f, 0f, 4.5f,
        24f, 739f, .5288592f, -.8f, 5.2f,
        58f, 737f, .535079f, -1.6f, 5.9f,
        92f, 744f, .61996384f, -2.4f, 6.6f,
        126f, 739f, .5174566f, -3.2f, 4.5f,
        160f, 746f, .5793345f, -4f, 5.2f,
        194f, 736f, .60213423f, -4.8f, 5.9f,
        228f, 736f, .60025102f, -5.6f, 6.6f,
        262f, 743f, .5207216f, -6.4f, 4.5f,
        296f, 739f, .5608778f, -7.2f, 5.2f,
        330f, 742f, .5219449f, -8f, 5.9f,
        364f, 741f, .514077f, -8.8f, 6.6f,
        398f, 744f, .5934463f, -9.6f, 4.5f,
    )
    private val front = floatArrayOf(
        22f, 820f, 1.05f, 0f, 4.5f,
        92f, 838f, .9f, -.8f, 5.2f,
        165f, 826f, 1.15f, -1.6f, 5.9f,
        240f, 840f, .95f, -2.4f, 6.6f,
        312f, 824f, 1.1f, -3.2f, 4.5f,
        378f, 836f, .95f, -4f, 5.2f,
    )
    // canopy circles: kind (0 = light "cn2", 1 = dark "cn"), cx, cy, r  (back to front)
    private val canopy = floatArrayOf(
        0f, 0f, -118f, 34f,
        1f, -26f, -98f, 28f,
        1f, 26f, -100f, 29f,
        0f, -12f, -134f, 24f,
        1f, 16f, -136f, 22f,
        1f, 0f, -90f, 26f,
        0f, -34f, -116f, 18f,
        1f, 34f, -120f, 18f,
    )
    private val trunk = Path().apply {
        moveTo(-7f, 0f); cubicTo(-6f, -30f, -5f, -60f, -3f, -88f); lineTo(3f, -88f); cubicTo(5f, -60f, 6f, -30f, 7f, 0f); close()
        moveTo(-2f, -60f); cubicTo(-12f, -70f, -18f, -80f, -24f, -92f); lineTo(-20f, -94f); cubicTo(-14f, -84f, -8f, -76f, 0f, -68f); close()
        moveTo(2f, -54f); cubicTo(12f, -64f, 18f, -74f, 22f, -86f); lineTo(26f, -84f); cubicTo(22f, -72f, 16f, -62f, 4f, -48f); close()
    }
    private val hill1 = Path().apply {
        moveTo(-20f, 770f); cubicTo(60f, 748f, 130f, 760f, 200f, 754f); cubicTo(270f, 748f, 330f, 742f, 410f, 756f); lineTo(410f, 860f); lineTo(-20f, 860f); close()
    }
    private val hill2 = Path().apply {
        moveTo(-20f, 812f); cubicTo(70f, 800f, 160f, 812f, 240f, 806f); cubicTo(310f, 800f, 360f, 808f, 410f, 804f); lineTo(410f, 860f); lineTo(-20f, 860f); close()
    }
    private val treePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val shapePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val oval = RectF()

    private val BX0 = -30f
    private val BY0 = 630f
    private val BW = 450f
    private val BH = 240f
    private var kb = 1f
    private var bandBmp: Bitmap? = null
    private var bandCv: Canvas? = null
    private var bandFrame = -1
    private val bandPaint = Paint(Paint.FILTER_BITMAP_FLAG).apply { alpha = 117 }   // 46% group opacity of the svg

    // ---------------------------------------------------------------------------------------------------- ripples, leaves
    private val puddleX = floatArrayOf(70f, 260f, 350f)
    private val puddleY = floatArrayOf(790f, 812f, 770f)
    private val puddleRx = floatArrayOf(21f, 28f, 12.6f)
    private val puddleRy = floatArrayOf(4.2f, 5.6f, 2.52f)
    private val ripDelay = floatArrayOf(0f, -.7f, -1.4f, -.3f, -1f, -1.7f, -.6f, -1.3f, -2f)
    private val ripPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; color = 0xFFFFFFFF.toInt() }

    // x, y, duration, delay
    private val leaf = floatArrayOf(
        387f, 566f, 10.5f, -2.9f, 58f, 659f, 11.9f, -1.2f, 122f, 626f, 8f, -10.1f, 84f, 598f, 7.1f, -11.5f,
        343f, 688f, 10.8f, -11.6f, 250f, 689f, 8.3f, -10.1f, 241f, 666f, 8.5f, -3.3f, 96f, 564f, 10f, -.8f,
        376f, 596f, 7f, -7.9f, 261f, 612f, 10.4f, -9.3f, 55f, 658f, 8.4f, -10.5f, 99f, 669f, 11.2f, -3.4f,
        268f, 642f, 8.6f, -2.3f, 188f, 682f, 11.9f, -9.2f,
    )
    private val leafColor = intArrayOf(
        0xFFC98A3C.toInt(), 0xFFB9A24A.toInt(), 0xFFB9A24A.toInt(), 0xFFB9A24A.toInt(), 0xFFC98A3C.toInt(), 0xFF7FA86A.toInt(), 0xFFC98A3C.toInt(),
        0xFF7FA86A.toInt(), 0xFF7FA86A.toInt(), 0xFFB9A24A.toInt(), 0xFFC98A3C.toInt(), 0xFF7FA86A.toInt(), 0xFF5F8F6A.toInt(), 0xFFC98A3C.toInt(),
    )
    private val leafPath = Path().apply {
        moveTo(0f, -6f); cubicTo(5f, -3f, 5f, 3f, 0f, 6f); cubicTo(-5f, 3f, -5f, -3f, 0f, -6f); close()
        moveTo(0f, -6f); lineTo(0f, 6f)
    }
    private val leafFill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val leafLine = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = .5f; color = 0xFF3E5A4A.toInt() }

    // ---------------------------------------------------------------------------------------------------- rain
    private val RW = 414f
    private val RH = 868f
    private val NDROP = 140
    private val dVs = FloatArray(NDROP)      // speed in px/s
    private val dLen = FloatArray(NDROP)
    private val dA = FloatArray(NDROP)       // start y
    private val dYl = FloatArray(NDROP)      // landing y
    private val dTc = FloatArray(NDROP)      // seconds for one fall
    private val dOff = FloatArray(NDROP)
    private val dBin = IntArray(NDROP)
    private val binIdx: Array<IntArray>
    private val binLines: Array<FloatArray>
    private val rainPaint = Array(4) { Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; color = 0xFFEBF4FF.toInt() } }
    private val splashPath = Array(4) { Path() }
    private val splashCount = IntArray(4)
    private val splashPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = .8f; color = 0xFFF0F8FF.toInt() }

    // ---------------------------------------------------------------------------------------------------- lightning
    private val flashPaint = Paint()
    private val flashM = Matrix()
    private val fk = floatArrayOf(0f, .06f, .15f, .24f, .6f)
    private val fv = floatArrayOf(0f, .55f, .1f, .45f, 0f)

    init {
        val cnt = IntArray(4)
        for (i in 0 until NDROP) {
            val n = i.toDouble()
            val vs = ((9.0 + 5.0 * hs(n * 7.1 + 1.0)) * 60.0).toFloat()
            dVs[i] = vs
            dLen[i] = (10.0 + 14.0 * hs(n * 3.7 + 2.0)).toFloat()
            val o = .35 + .35 * hs(n * 5.3 + 3.0)
            val w = .6 + .8 * hs(n * 2.9 + 4.0)
            dBin[i] = (if (o > .525) 2 else 0) + (if (w > 1.0) 1 else 0)
            dA[i] = (-20.0 - 60.0 * hs(n * 11.3 + 5.0)).toFloat()
            dYl[i] = (868.0 - 30.0 - 90.0 * hs(n * 13.7 + 6.0)).toFloat()
            dTc[i] = (dYl[i] - dA[i]) / vs
            dOff[i] = (hs(n * 17.9 + 7.0) * dTc[i]).toFloat()
            cnt[dBin[i]]++
        }
        binIdx = Array(4) { IntArray(cnt[it]) }
        binLines = Array(4) { FloatArray(cnt[it] * 4) }
        val fill = IntArray(4)
        for (i in 0 until NDROP) { val b = dBin[i]; binIdx[b][fill[b]++] = i }
        // opacity .35-.70 and width .6-1.4 of the original, in four classes, times the .55 opacity of the rain canvas
        for (b in 0 until 4) {
            val a = (if (b >= 2) .61f else .44f) * .55f
            rainPaint[b].alpha = (a * 255f + .5f).toInt()
            rainPaint[b].strokeWidth = if (b % 2 == 1) 1.2f else .8f
        }
        flashPaint.shader = RadialGradient(0f, 0f, 1f, intArrayOf(0xFFFFFFFF.toInt(), 0x66FFFFFF, 0x00FFFFFF), floatArrayOf(0f, .6f, 1f), Shader.TileMode.CLAMP)
    }

    // ---------------------------------------------------------------------------------------------------- setup
    override fun onSize(w: Float, h: Float, density: Float) {
        dens = density
        val cw = w / density
        val ch = h / density
        boxW = cw + 24f; boxH = ch + 24f
        val sc = max(boxW / 390f, boxH / 844f)
        k = sc * density
        ox = (boxW - 390f * sc) / 2f - 12f
        oy = (boxH - 844f * sc) / 2f - 12f

        // clouds: one blurred ellipse each (sigma 16), rendered small and drawn enlarged
        val sigma = 16f
        for (c in clouds) {
            c.bmp?.recycle()
            val bw = ceil((2f * c.rx + 6f * sigma) * cloudPs).toInt()
            val bh = ceil((2f * c.ry + 6f * sigma) * cloudPs).toInt()
            val b = Bitmap.createBitmap(bw, bh, Bitmap.Config.ARGB_8888)
            val p = Paint(Paint.ANTI_ALIAS_FLAG)
            p.color = 0xFF5D6F8C.toInt()
            p.maskFilter = BlurMaskFilter(((sigma * cloudPs - .5f) / .57735f).coerceAtLeast(1f), BlurMaskFilter.Blur.NORMAL)
            val cx = bw / 2f; val cy = bh / 2f
            Canvas(b).drawOval(RectF(cx - c.rx * cloudPs, cy - c.ry * cloudPs, cx + c.rx * cloudPs, cy + c.ry * cloudPs), p)
            c.bmp = b
        }

        // the tree line bitmap
        kb = min(k, 1.5f)
        bandBmp?.recycle()
        val bb = Bitmap.createBitmap(ceil(BW * kb).toInt(), ceil(BH * kb).toInt(), Bitmap.Config.ARGB_8888)
        bandBmp = bb
        bandCv = Canvas(bb)
        bandFrame = -1

        // lightning flash: ellipse 80% x 50% of the box at 60% / 10%
        flashM.reset()
        flashM.setScale(boxW * .8f * density, boxH * .5f * density)
        flashM.postTranslate((boxW * .6f - 12f) * density, (boxH * .1f - 12f) * density)
        (flashPaint.shader as RadialGradient).setLocalMatrix(flashM)
    }

    // ---------------------------------------------------------------------------------------------------- frame
    override fun draw(cv: Canvas, w: Float, h: Float, t: Float, dt: Float, page: Float) {
        val band = bandBmp ?: return

        // clouds (far: 8 dp per page)
        cv.save()
        place(cv, page, 1f)
        for (c in clouds) {
            val bm = c.bmp ?: continue
            val dx = -30f + 60f * tri((t - c.delay) / c.dur)
            cloudPaint.alpha = (c.op * .46f * 255f + .5f).toInt()
            cv.save()
            cv.translate(c.cx + dx, c.cy)
            cv.scale(1f / cloudPs, 1f / cloudPs)
            cv.drawBitmap(bm, -bm.width / 2f, -bm.height / 2f, cloudPaint)
            cv.restore()
        }
        cv.restore()

        // trees, hills, puddles (a little nearer: 10 dp per page)
        val frame = (t * 20f).toInt()
        if (frame != bandFrame) { bandFrame = frame; renderBand(frame / 20f); band.prepareToDraw() }
        cv.save()
        place(cv, page, 1.25f)
        cv.translate(BX0, BY0)
        cv.scale(1f / kb, 1f / kb)
        cv.drawBitmap(band, 0f, 0f, bandPaint)
        cv.restore()

        cv.save()
        place(cv, page, 1.25f)
        drawRipples(cv, t)
        drawLeaves(cv, t)
        cv.restore()

        drawRain(cv, t, page)

        val f = flash(t)
        if (f > .004f) {
            flashPaint.alpha = (f * 255f + .5f).toInt().coerceIn(0, 255)
            cv.drawRect(0f, 0f, w, h, flashPaint)
        }
    }

    private fun place(cv: Canvas, page: Float, factor: Float) {
        cv.translate((ox - page * 8f * factor) * dens, oy * dens)
        cv.scale(k, k)
    }

    private fun renderBand(q: Float) {
        val c = bandCv ?: return
        bandBmp?.eraseColor(0)
        c.save()
        c.scale(kb, kb)
        c.translate(-BX0, -BY0)
        for (i in 0 until 13) tree(c, back, i, q, 0xFF93ADA8.toInt(), 0xFF7D9A96.toInt(), 0xFF7C7470.toInt())
        shapePaint.style = Paint.Style.FILL
        shapePaint.color = 0xFF6D8A86.toInt(); shapePaint.alpha = 153
        c.drawPath(hill1, shapePaint)
        shapePaint.color = 0xFF88A29C.toInt(); shapePaint.alpha = 179
        c.drawPath(hill2, shapePaint)
        shapePaint.color = 0xFFFFFFFF.toInt(); shapePaint.alpha = 89
        for (i in 0 until 3) {
            oval.set(puddleX[i] - puddleRx[i] * 2.857f, puddleY[i] - puddleRy[i] * 2.571f, puddleX[i] + puddleRx[i] * 2.857f, puddleY[i] + puddleRy[i] * 2.571f)
            c.drawOval(oval, shapePaint)
        }
        for (i in 0 until 6) tree(c, front, i, q, 0xFF6B9884.toInt(), 0xFF4F7A6A.toInt(), 0xFF5B4A3E.toInt())
        c.restore()
    }

    private fun tree(c: Canvas, a: FloatArray, i: Int, q: Float, light: Int, dark: Int, trunkColor: Int) {
        val o = i * 5
        val ang = -2.6f + 5.2f * bez(.42f, 0f, .58f, 1f, tri((q - a[o + 3]) / a[o + 4]))
        c.save()
        c.translate(a[o], a[o + 1])
        c.scale(a[o + 2], a[o + 2])
        c.translate(.5f, 0f)
        c.rotate(ang)
        c.translate(-.5f, 0f)
        treePaint.color = trunkColor
        c.drawPath(trunk, treePaint)
        var j = 0
        while (j < canopy.size) {
            treePaint.color = if (canopy[j] == 0f) light else dark
            c.drawCircle(canopy[j + 1], canopy[j + 2], canopy[j + 3], treePaint)
            j += 4
        }
        c.restore()
    }

    private fun drawRipples(cv: Canvas, t: Float) {
        for (i in 0 until 9) {
            val p = (i / 3)
            var ph = ((t - ripDelay[i]) / 2.1f) % 1f
            if (ph < 0f) ph += 1f
            val e = bez(0f, 0f, .58f, 1f, ph)
            val s = .2f + 2.4f * e
            ripPaint.alpha = (.9f * (1f - e) * .46f * 255f + .5f).toInt()
            ripPaint.strokeWidth = s
            oval.set(puddleX[p] - puddleRx[p] * s, puddleY[p] - puddleRy[p] * s, puddleX[p] + puddleRx[p] * s, puddleY[p] + puddleRy[p] * s)
            cv.drawOval(oval, ripPaint)
        }
    }

    private fun drawLeaves(cv: Canvas, t: Float) {
        for (i in 0 until 14) {
            val o = i * 4
            var ph = ((t - leaf[o + 3]) / leaf[o + 2]) % 1f
            if (ph < 0f) ph += 1f
            val x: Float; val y: Float; val rot: Float
            if (ph < .5f) {
                val u = ph / .5f
                x = leaf[o] - 30f * u; y = leaf[o + 1] + 90f * u; rot = 220f * u
            } else {
                val u = (ph - .5f) / .5f
                val my = leaf[o + 1] + 90f
                x = leaf[o] - 30f + 40f * u; y = my + (860f - my) * u; rot = 220f + 260f * u
            }
            val op = if (ph < .1f) ph / .1f else if (ph < .9f) 1f else (1f - ph) / .1f
            val al = (op * .46f * 255f + .5f).toInt()
            leafFill.color = leafColor[i]; leafFill.alpha = al
            leafLine.alpha = al
            cv.save()
            cv.translate(x, y)
            cv.rotate(rot)
            cv.drawPath(leafPath, leafFill)
            cv.drawPath(leafPath, leafLine)
            cv.restore()
        }
    }

    private fun drawRain(cv: Canvas, t: Float, page: Float) {
        for (b in 0 until 4) { splashPath[b].reset(); splashCount[b] = 0 }
        for (b in 0 until 4) {
            val idx = binIdx[b]
            val ln = binLines[b]
            var n = 0
            for (j in idx.indices) {
                val i = idx[j]
                val tc = dTc[i]
                val u = t + dOff[i]
                val kk = floor(u / tc)
                val ph = u - kk * tc
                val kd = kk.toDouble()
                val x0 = (-60.0 + (RW + 120.0) * hs(i * 97.13 + kd * 13.7 + .5)).toFloat()
                val y = dA[i] + dVs[i] * ph
                val x = x0 - .18f * dVs[i] * ph
                ln[n++] = x; ln[n++] = y; ln[n++] = x + dLen[i] * .18f; ln[n++] = y - dLen[i]
                if (ph < .33f) {
                    val px0 = (-60.0 + (RW + 120.0) * hs(i * 97.13 + (kd - 1.0) * 13.7 + .5)).toFloat()
                    val lx = px0 - .18f * dVs[i] * tc
                    val r = 1f + 27f * ph
                    val sb = min(3, (ph / .0825f).toInt())
                    splashPath[sb].addOval(lx - r * 1.6f, dYl[i] - r * .45f, lx + r * 1.6f, dYl[i] + r * .45f, Path.Direction.CW)
                    splashCount[sb]++
                }
            }
            lineCount[b] = n
        }
        cv.save()
        cv.translate((-12f - page * 8f) * dens, -12f * dens)
        cv.scale(boxW / RW * dens, boxH / RH * dens)
        for (b in 0 until 4) cv.drawLines(binLines[b], 0, lineCount[b], rainPaint[b])
        for (b in 0 until 4) {
            if (splashCount[b] == 0) continue
            val age = (b + .5f) * .0825f
            splashPaint.alpha = (max(0f, .6f - 1.8f * age) * .55f * 255f + .5f).toInt()
            cv.drawPath(splashPath[b], splashPaint)
        }
        cv.restore()
    }
    private val lineCount = IntArray(4)

    /** Opacity of the white lightning overlay at time t: an event every 5-11 s (first at 3.2 s), 0.6 s long. */
    private fun flash(t: Float): Float {
        val n0 = floor((t - 3.2f) / 8f).toInt()
        var best = 0f
        for (n in n0..n0 + 1) {
            if (n < 0) continue
            val e = 3.2f + 8f * n + ((hs(n + .5) - .5) * 6.0).toFloat()
            val tau = t - e
            if (tau < 0f || tau >= .6f) continue
            var s = 0
            while (s < 3 && tau >= fk[s + 1]) s++
            val u = (tau - fk[s]) / (fk[s + 1] - fk[s])
            val v = fv[s] + (fv[s + 1] - fv[s]) * bez(0f, 0f, .58f, 1f, u)
            if (v > best) best = v
        }
        return best
    }

    // ---------------------------------------------------------------------------------------------------- helpers
    /** Alternating 0..1..0 with period 2 (CSS `alternate`). */
    private fun tri(p: Float): Float {
        var q = p % 2f
        if (q < 0f) q += 2f
        return if (q < 1f) q else 2f - q
    }

    /** CSS cubic-bezier easing, solved by bisection, no allocation. */
    private fun bez(x1: Float, y1: Float, x2: Float, y2: Float, x: Float): Float {
        if (x <= 0f) return 0f
        if (x >= 1f) return 1f
        var lo = 0f
        var hi = 1f
        var tt = x
        for (i in 0 until 14) {
            val u = 1f - tt
            val bx = 3f * u * u * tt * x1 + 3f * u * tt * tt * x2 + tt * tt * tt
            if (bx < x) lo = tt else hi = tt
            tt = (lo + hi) * .5f
        }
        val u = 1f - tt
        return 3f * u * u * tt * y1 + 3f * u * tt * tt * y2 + tt * tt * tt
    }

    /** Deterministic pseudo-random number in [0,1). */
    private fun hs(n: Double): Double {
        val v = sin(n * 12.9898) * 43758.5453
        return v - floor(v)
    }
}
