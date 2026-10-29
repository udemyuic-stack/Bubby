package com.example.buddy.ui

import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max

/**
 * Native animated background of the Halloween look, ported from assets/scene/halloween.html (390x844 SVG, shown at 55% opacity):
 * moon with halo, drifting mist wisps, five bats, cobweb with a spider on a thread, two ghosts, two swaying bare trees, hill with
 * tombstones and three pumpkins (flickering faces), mist banks. Blurs and static art are rendered once into bitmaps in [onSize];
 * [draw] only places them (nothing is allocated). All motion is a pure function of the time.
 */
class HalloweenScene : ThemeScene() {
    override val ready = true

    // ---- scene (390x844 viewBox, "slice" in a box 12 dp larger on every side) -> screen
    private var d = 1f
    private var s = 1f
    private var tx = 0f
    private var ty = 0f
    private var k = 1f          // device pixels per scene unit

    private val bp = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
    private val fp = Paint(Paint.ANTI_ALIAS_FLAG)
    private val sp = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val r1 = RectF()

    // ---- shapes (SVG paths of the original), built once
    private val wingPath = svgPath("M0 0 C-6 -6 -14 -8 -24 -4 C-19 -2 -17 2 -15 5 C-11 1 -7 2 -4 6 C-2 3 0 3 0 3 C0 3 2 3 4 6 C7 2 11 1 15 5 C17 2 19 -2 24 -4 C14 -8 6 -6 0 0Z")
    private val ghostPath = svgPath("M-12 14 C-12 -6 -6 -16 0 -16 C6 -16 12 -6 12 14 C9 10 6 10 4 14 C2 10 -2 10 -4 14 C-6 10 -9 10 -12 14Z")
    private val webPath = svgPath(
        "M0 0 L120 0 M0 0 L114 37 M0 0 L97 71 M0 0 L71 97 M0 0 L37 114 M0 0 L0 120 " +
        "M30 0 Q24 4 29 9 Q22 11 24 18 Q17 17 18 24 Q11 22 9 29 Q4 24 0 30 " +
        "M55 0 Q45 7 52 17 Q40 20 44 32 Q32 32 32 44 Q20 40 17 52 Q7 45 0 55 " +
        "M80 0 Q65 10 76 25 Q58 30 65 47 Q46 46 47 65 Q30 58 25 76 Q10 65 0 80 " +
        "M104 0 Q84 13 99 32 Q76 39 84 61 Q60 60 61 84 Q39 76 32 99 Q13 84 0 104")
    private val legsPath = svgPath(
        "M0 -2 Q-9 -6 -12 2 M0 -2 Q-9 -2 -12 6 M0 -2 Q-9 2 -12 10 M0 -2 Q9 -6 12 2 M0 -2 Q9 -2 12 6 M0 -2 Q9 2 12 10")
    private val spiderBody = Path().apply {
        addOval(RectF(-6f, -7f, 6f, 7f), Path.Direction.CW)
        addCircle(0f, -8f, 4f, Path.Direction.CW)
    }
    private val tombPath = svgPath("M-14 0 L-14 -26 C-14 -40 14 -40 14 -26 L14 0Z")
    private val tombCross = svgPath("M-6 -24 H6 M0 -30 V-14")
    private val stemPath = svgPath("M-2 -31 C-2 -38 2 -40 5 -42")
    private val facePath = svgPath(
        "M-13 -20 L-7 -27 L-3 -19Z M13 -20 L7 -27 L3 -19Z " +
        "M-14 -11 L-9 -6 L-5 -10 L0 -5 L5 -10 L9 -6 L14 -11 C10 -1 -10 -1 -14 -11Z")
    private val hillPath = Path()

    // ---- bitmaps (rebuilt in onSize)
    private var moonBm: Bitmap? = null
    private var wispBm = arrayOfNulls<Bitmap>(2)
    private var fogBm = arrayOfNulls<Bitmap>(3)
    private var treeBm = arrayOfNulls<Bitmap>(2)
    private var groundBm: Bitmap? = null
    private var faceBm = arrayOfNulls<Bitmap>(3)
    private val moonDst = RectF()
    private val treeDst = Array(2) { RectF() }
    private val groundDst = RectF()
    private val wispHalf = floatArrayOf(140f, 160f)          // half width of each bitmap (scene units); half height is 24
    private val fogHalf = floatArrayOf(230f, 210f, 270f)     // half height is 40

    // ---- timing tables of the original
    private val batY = floatArrayOf(90f, 140f, 210f, 60f, 260f)
    private val batDur = floatArrayOf(11f, 14f, 12f, 16f, 18f)
    private val batOff = floatArrayOf(0f, 5f, 9f, 3f, 12f)                 // = -animation-delay
    private val batSc = floatArrayOf(1f, .8f, .9f, .7f, .65f)
    private val batX = floatArrayOf(-40f, 90f, 200f, 310f, 440f)
    private val batDy = floatArrayOf(0f, -30f, 10f, -25f, 0f)
    private val wispCx = floatArrayOf(250f, 120f)
    private val wispCy = floatArrayOf(170f, 230f)
    private val wispPeriod = floatArrayOf(60f, 76f)
    private val fogCx = floatArrayOf(100f, 330f, 200f)
    private val fogCy = floatArrayOf(770f, 800f, 830f)
    private val fogPeriod = floatArrayOf(48f, 60f, 72f)
    private val fogOff = floatArrayOf(12f, 15f, 18f)
    private val gX = floatArrayOf(60f, 330f)
    private val gY = floatArrayOf(420f, 520f)
    private val gDur = floatArrayOf(16f, 20f)
    private val gOff = floatArrayOf(5f, .1f)
    private val pkX = floatArrayOf(70f, 330f, 232f)
    private val pkY = floatArrayOf(812f, 818f, 830f)
    private val pkSc = floatArrayOf(1.1f, 1.3f, .8f)
    private val pkOff = floatArrayOf(0f, .6f, 1.1f)
    private val treePx = floatArrayOf(24f, 372f)
    private val treeOff = floatArrayOf(0f, 2f)

    override fun onSize(w: Float, h: Float, density: Float) {
        recycleAll()
        if (w <= 0f || h <= 0f) return
        d = density
        val cw = w / d
        val ch = h / d
        s = max((cw + 24f) / 390f, (ch + 24f) / 844f)
        tx = -12f + ((cw + 24f) - 390f * s) / 2f
        ty = -12f + ((ch + 24f) - 844f * s) / 2f
        k = d * s
        val x0 = -tx / s
        val x1 = (cw - tx) / s
        val xl = floor(x0) - 80f
        val xr = ceil(x1) + 80f

        // hill (flat beyond the original ends so the parallax never shows an edge)
        hillPath.rewind()
        hillPath.moveTo(xl - 10f, 776f); hillPath.lineTo(-20f, 776f)
        hillPath.cubicTo(70f, 750f, 150f, 772f, 230f, 760f)
        hillPath.cubicTo(300f, 750f, 350f, 766f, 410f, 758f)
        hillPath.lineTo(xr + 10f, 758f); hillPath.lineTo(xr + 10f, 870f); hillPath.lineTo(xl - 10f, 870f); hillPath.close()

        moonBm = buildMoon()
        moonDst.set(292f - 130f, 150f - 130f, 292f + 130f, 150f + 130f)
        for (i in 0..1) wispBm[i] = blurEllipse(if (i == 0) 90f else 110f, 12f, wispHalf[i], 24f, WISP, .55f)
        for (i in 0..2) fogBm[i] = blurEllipse(floatArrayOf(180f, 160f, 220f)[i], 20f, fogHalf[i], 40f, FOG, .7f)
        treeBm[0] = buildTree(TREE0, treePx[0], treeDst[0])
        treeBm[1] = buildTree(TREE1, treePx[1], treeDst[1])
        buildGround(xl, xr)
        for (i in 0..2) faceBm[i] = buildFace(pkSc[i])
    }

    private fun recycleAll() {
        moonBm?.recycle(); moonBm = null
        groundBm?.recycle(); groundBm = null
        for (i in wispBm.indices) { wispBm[i]?.recycle(); wispBm[i] = null }
        for (i in fogBm.indices) { fogBm[i]?.recycle(); fogBm[i] = null }
        for (i in treeBm.indices) { treeBm[i]?.recycle(); treeBm[i] = null }
        for (i in faceBm.indices) { faceBm[i]?.recycle(); faceBm[i] = null }
    }

    // ---------------------------------------------------------------- pre-rendering

    private fun blurRadius(sigmaPx: Float) = ((sigmaPx - .5f) / .57735f).coerceAtLeast(1f)

    /** The moon group: blurred halo (sigma 14), disc and three craters, at full opacity (the animated alpha is applied when drawing). */
    private fun buildMoon(): Bitmap {
        val n = (260f * k).toInt().coerceAtLeast(2)
        val bm = Bitmap.createBitmap(n, n, Bitmap.Config.ARGB_8888)
        val c = Canvas(bm)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.color = 0xFFFFD27A.toInt(); p.alpha = (.35f * 255).toInt()
        p.maskFilter = BlurMaskFilter(blurRadius(14f * k), BlurMaskFilter.Blur.NORMAL)
        c.drawCircle(n / 2f, n / 2f, 86f * k, p)
        p.maskFilter = null
        c.scale(k, k)
        c.translate(-(292f - 130f), -(150f - 130f))
        p.color = 0xFFFFE3A3.toInt()
        c.drawCircle(292f, 150f, 54f, p)
        p.color = 0xFFE9B85A.toInt()
        p.alpha = 128; c.drawCircle(272f, 136f, 9f, p)
        p.alpha = 115; c.drawCircle(308f, 168f, 12f, p)
        p.alpha = 128; c.drawCircle(300f, 128f, 5f, p)
        return bm
    }

    /** A feGaussianBlur(14) ellipse cropped like the SVG filter region ([halfH] above and below its centre). */
    private fun blurEllipse(rx: Float, ry: Float, halfW: Float, halfH: Float, color: Int, alpha: Float): Bitmap {
        val bw = (2f * halfW * k).toInt().coerceAtLeast(2)
        val bh = (2f * halfH * k).toInt().coerceAtLeast(2)
        val bm = Bitmap.createBitmap(bw, bh, Bitmap.Config.ALPHA_8)
        val c = Canvas(bm)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.color = color; p.alpha = (alpha * 255).toInt()
        p.maskFilter = BlurMaskFilter(blurRadius(14f * k), BlurMaskFilter.Blur.NORMAL)
        c.drawOval(RectF(bw / 2f - rx * k, bh / 2f - ry * k, bw / 2f + rx * k, bh / 2f + ry * k), p)
        return bm
    }

    /** One bare tree: its 63 round-capped quadratic branches, drawn at full opacity; the part behind the hill is thinned out. */
    private fun buildTree(data: FloatArray, px: Float, rel: RectF): Bitmap {
        var minX = 1e9f; var minY = 1e9f; var maxX = -1e9f
        var i = 0
        while (i < data.size) {
            minX = minOf(minX, data[i], data[i + 4]); maxX = max(maxX, max(data[i], data[i + 4]))
            minY = minOf(minY, data[i + 1], data[i + 5]); i += 7
        }
        val l = floor(minX) - 8f
        val r = ceil(maxX) + 8f
        val t = floor(minY) - 8f
        val b = 862f
        val bm = Bitmap.createBitmap(((r - l) * k).toInt().coerceAtLeast(2), ((b - t) * k).toInt().coerceAtLeast(2), Bitmap.Config.ALPHA_8)
        val c = Canvas(bm)
        c.scale(k, k)
        c.translate(-l, -t)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.style = Paint.Style.STROKE; p.strokeCap = Paint.Cap.ROUND; p.color = TREE
        val path = Path()
        i = 0
        while (i < data.size) {
            path.rewind()
            path.moveTo(data[i], data[i + 1]); path.quadTo(data[i + 2], data[i + 3], data[i + 4], data[i + 5])
            p.strokeWidth = data[i + 6]
            c.drawPath(path, p)
            i += 7
        }
        // the hill is 70% opaque over the tree inside the SVG group: keep about half of the trunk under it
        val e = Paint(Paint.ANTI_ALIAS_FLAG)
        e.xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_OUT); e.color = Color.BLACK; e.alpha = 128
        c.drawPath(hillPath, e)
        rel.set(l - px, t - 840f, r - px, b - 840f)
        return bm
    }

    /** Hill, three tombstones and the three pumpkin bodies (everything static in the foreground). */
    private fun buildGround(xl: Float, xr: Float) {
        val top = 735f
        val bot = 862f
        val bm = Bitmap.createBitmap(((xr - xl) * k).toInt().coerceAtLeast(2), ((bot - top) * k).toInt().coerceAtLeast(2), Bitmap.Config.ARGB_8888)
        val c = Canvas(bm)
        c.scale(k, k)
        c.translate(-xl, -top)
        val f = Paint(Paint.ANTI_ALIAS_FLAG)
        f.color = 0xFF7A6696.toInt(); f.alpha = (.7f * 255).toInt()
        c.drawPath(hillPath, f)
        val st = Paint(Paint.ANTI_ALIAS_FLAG)
        st.style = Paint.Style.STROKE; st.strokeWidth = 2f; st.color = 0xFF4A3B60.toInt()
        f.color = 0xFF6C5A86.toInt()
        val tx0 = floatArrayOf(118f, 200f, 262f)
        val ty0 = floatArrayOf(790f, 782f, 796f)
        val tr = floatArrayOf(-6f, 4f, -3f)
        for (i in 0..2) {
            c.save(); c.translate(tx0[i], ty0[i]); c.rotate(tr[i])
            c.drawPath(tombPath, f); c.drawPath(tombCross, st)
            c.restore()
        }
        val rl = RectF()
        val sk = Paint(Paint.ANTI_ALIAS_FLAG)
        sk.style = Paint.Style.STROKE; sk.strokeWidth = 3f; sk.strokeCap = Paint.Cap.ROUND; sk.color = 0xFF3C7A3A.toInt()
        for (i in 0..2) {
            c.save(); c.translate(pkX[i], pkY[i]); c.scale(pkSc[i], pkSc[i])
            f.color = 0xFFE86A10.toInt()
            rl.set(-24f, -31f, 2f, 1f); c.drawOval(rl, f)
            rl.set(-2f, -31f, 24f, 1f); c.drawOval(rl, f)
            f.color = 0xFFFF8A1F.toInt()
            rl.set(-13f, -33f, 13f, 1f); c.drawOval(rl, f)
            c.drawPath(stemPath, sk)
            c.restore()
        }
        groundBm = bm
        groundDst.set(xl, top, xr, bot)
    }

    /** A pumpkin face with its feGaussianBlur(3) glow (blur twice, then the sharp shape on top), at full opacity. */
    private fun buildFace(sc: Float): Bitmap {
        val kk = k * sc
        val n = (48f * kk).toInt().coerceAtLeast(2)
        val bm = Bitmap.createBitmap(n, n, Bitmap.Config.ALPHA_8)
        val c = Canvas(bm)
        val m = Matrix()
        m.setScale(kk, kk); m.postTranslate(24f * kk, 38f * kk)
        val dp = Path()
        facePath.transform(m, dp)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.color = 0xFFFFE27A.toInt()
        p.maskFilter = BlurMaskFilter(blurRadius(3f * kk), BlurMaskFilter.Blur.NORMAL)
        c.drawPath(dp, p); c.drawPath(dp, p)
        p.maskFilter = null
        c.drawPath(dp, p)
        return bm
    }

    // ---------------------------------------------------------------- animation helpers

    /** 0..1..0 triangle wave over [period] seconds. */
    private fun tri(x: Float, period: Float): Float {
        val p = x / period
        val f = p - floor(p)
        return if (f < .5f) f * 2f else 2f - f * 2f
    }

    /** CSS ease-in-out, close enough: smoothstep. */
    private fun sm(x: Float) = x * x * (3f - 2f * x)

    /** The keyframes 0% .55, 40% 1, 60% .75, 100% 1 of hwFace, each segment eased. */
    private fun faceOp(q: Float): Float = when {
        q < .4f -> .55f + .45f * sm(q / .4f)
        q < .6f -> 1f - .25f * sm((q - .4f) / .2f)
        else -> .75f + .25f * sm((q - .6f) / .4f)
    }

    private fun enter(cv: Canvas, page: Float, shift: Float) {
        cv.save()
        cv.translate((tx - page * shift) * d, ty * d)
        cv.scale(k, k)
    }

    private fun bmp(cv: Canvas, bm: Bitmap?, dst: RectF, color: Int, a: Float) {
        if (bm == null) return
        bp.color = color
        bp.alpha = (a * 255f).toInt().coerceIn(0, 255)
        cv.drawBitmap(bm, null, dst, bp)
    }

    private fun fill(color: Int, a: Float): Paint {
        fp.color = color
        fp.alpha = (a * 255f).toInt().coerceIn(0, 255)
        return fp
    }

    private fun stroke(color: Int, a: Float, wd: Float): Paint {
        sp.color = color
        sp.alpha = (a * 255f).toInt().coerceIn(0, 255)
        sp.strokeWidth = wd
        return sp
    }

    // ---------------------------------------------------------------- frame

    override fun draw(cv: Canvas, w: Float, h: Float, t: Float, dt: Float, page: Float) {
        if (groundBm == null) return

        // moon (opacity .8 <-> 1, 6 s alternate) and the two mist wisps
        enter(cv, page, 8f)
        bmp(cv, moonBm, moonDst, Color.WHITE, BASE * (.8f + .2f * sm(tri(t, 12f))))
        for (i in 0..1) {
            val x = -50f + 100f * tri(t, wispPeriod[i])
            val hw = wispHalf[i]
            r1.set(wispCx[i] + x - hw, wispCy[i] - 24f, wispCx[i] + x + hw, wispCy[i] + 24f)
            bmp(cv, wispBm[i], r1, WISP, BASE)
        }

        // bats crossing the sky, wings flapping every .44 s
        val wing = 1f - .75f * sm(tri(t, .44f))
        for (i in 0..4) {
            val p = (t + batOff[i]) / batDur[i]
            val u = (p - floor(p)) * 4f
            val j = u.toInt().coerceAtMost(3)
            val f = u - j
            val x = batX[j] + (batX[j + 1] - batX[j]) * f
            val y = batY[i] + batDy[j] + (batDy[j + 1] - batDy[j]) * f
            cv.save()
            cv.translate(x, y); cv.scale(batSc[i], batSc[i])
            cv.save()
            cv.translate(0f, -1.25f); cv.scale(1f, wing); cv.translate(0f, 1.25f)
            cv.drawPath(wingPath, fill(BAT, BASE))
            cv.restore()
            r1.set(-3f, -3f, 3f, 5f)
            cv.drawOval(r1, fp)
            cv.restore()
        }

        // cobweb and the spider on its thread (6 s ease-in-out)
        cv.drawPath(webPath, stroke(Color.WHITE, .75f * BASE, .8f))
        val sy = -60f + 80f * sm(tri(t, 6f))
        cv.save()
        cv.translate(0f, sy)
        cv.drawLine(62f, 0f, 62f, 100f, stroke(Color.WHITE, BASE, .7f))
        cv.translate(62f, 100f)
        cv.drawPath(spiderBody, fill(BAT, BASE))
        cv.drawPath(legsPath, stroke(BAT, BASE, 1.4f))
        cv.restore()
        cv.restore()

        // two ghosts rising and fading
        enter(cv, page, 10f)
        for (i in 0..1) {
            val f = sm(tri(t + gOff[i], gDur[i]))
            val o = (.2f + .7f * f) * BASE
            cv.save()
            cv.translate(gX[i] + 40f * f, gY[i] - 60f * f)
            cv.drawPath(ghostPath, fill(Color.WHITE, .85f * o))
            fill(BAT, o)
            cv.drawCircle(-4f, -4f, 1.8f, fp)
            cv.drawCircle(4f, -4f, 1.8f, fp)
            cv.restore()
        }
        cv.restore()

        // swaying trees (rotate -1.4 <-> 1.4 deg, 6 s), hill with tombstones and pumpkins, flickering faces
        enter(cv, page, 12f)
        for (i in 0..1) {
            val a = -1.4f + 2.8f * sm(tri(t + treeOff[i], 12f))
            cv.save()
            cv.translate(treePx[i], 840f); cv.rotate(a)
            bmp(cv, treeBm[i], treeDst[i], TREE, BASE)
            cv.restore()
        }
        bmp(cv, groundBm, groundDst, Color.WHITE, BASE)
        for (i in 0..2) {
            val o = faceOp(tri(t + pkOff[i], 2.6f))
            val sc = pkSc[i]
            r1.set(pkX[i] - 24f * sc, pkY[i] - 38f * sc, pkX[i] + 24f * sc, pkY[i] + 10f * sc)
            bmp(cv, faceBm[i], r1, FACE, BASE * o)
        }
        cv.restore()

        // mist banks drifting along the ground
        enter(cv, page, 14f)
        for (i in 0..2) {
            val x = -60f + 120f * tri(t + fogOff[i], fogPeriod[i])
            val hw = fogHalf[i]
            r1.set(fogCx[i] + x - hw, fogCy[i] - 40f, fogCx[i] + x + hw, fogCy[i] + 40f)
            bmp(cv, fogBm[i], r1, FOG, BASE)
        }
        cv.restore()
    }

    // ---------------------------------------------------------------- svg path parser (absolute M L H V C Q Z, all the file needs)

    private fun svgPath(dd: String): Path {
        val p = Path()
        var i = 0
        val n = dd.length
        var cmd = 'M'
        var cx = 0f; var cy = 0f
        fun num(): Float {
            while (i < n && (dd[i] == ' ' || dd[i] == ',')) i++
            val st = i
            if (i < n && (dd[i] == '-' || dd[i] == '+')) i++
            while (i < n && (dd[i].isDigit() || dd[i] == '.')) i++
            return dd.substring(st, i).toFloat()
        }
        while (true) {
            while (i < n && (dd[i] == ' ' || dd[i] == ',')) i++
            if (i >= n) break
            val ch = dd[i]
            if (ch.isLetter()) { cmd = ch; i++ }
            when (cmd) {
                'M' -> { cx = num(); cy = num(); p.moveTo(cx, cy); cmd = 'L' }
                'L' -> { cx = num(); cy = num(); p.lineTo(cx, cy) }
                'H' -> { cx = num(); p.lineTo(cx, cy) }
                'V' -> { cy = num(); p.lineTo(cx, cy) }
                'Q' -> { val a = num(); val b = num(); cx = num(); cy = num(); p.quadTo(a, b, cx, cy) }
                'C' -> { val a = num(); val b = num(); val c = num(); val e = num(); cx = num(); cy = num(); p.cubicTo(a, b, c, e, cx, cy) }
                'Z', 'z' -> p.close()
                else -> break
            }
        }
        return p
    }

    private companion object {
        const val BASE = .55f                        // .scn.hw { opacity: .55 }
        const val BAT = 0xFF2B1D3C.toInt()
        const val TREE = 0xFF2B1D3C.toInt()
        const val WISP = 0xFFEFE8F8.toInt()
        const val FOG = 0xFFF2ECFA.toInt()
        const val FACE = 0xFFFFE27A.toInt()

        // branches of the two trees: x0, y0, control x, control y, x1, y1, stroke width
        val TREE0 = floatArrayOf(
            24f,840f,34.8f,780.6f,40.7f,721.2f,12f,
            40.7f,721.2f,24.2f,679.2f,12.1f,637.3f,7.4f,
            12.1f,637.3f,-2.1f,613.6f,-24.2f,590f,4.6f,
            -24.2f,590f,-36.1f,579.8f,-59.5f,569.6f,2.9f,
            -59.5f,569.6f,-67f,567.1f,-85.3f,564.5f,1.8f,
            -85.3f,564.5f,-100.2f,566.1f,-103.9f,567.7f,1.1f,
            -85.3f,564.5f,-98.2f,558.8f,-99.9f,553f,1.1f,
            -59.5f,569.6f,-67.2f,556.6f,-72.9f,543.6f,1.8f,
            -72.9f,543.6f,-79.2f,537.6f,-87.6f,531.6f,1.1f,
            -72.9f,543.6f,-75.3f,534f,-72.5f,524.4f,1.1f,
            -24.2f,590f,-30f,571.2f,-28.8f,552.3f,2.9f,
            -28.8f,552.3f,-35.6f,541.9f,-42.5f,531.4f,1.8f,
            -42.5f,531.4f,-48.6f,527.2f,-56.2f,523f,1.1f,
            -42.5f,531.4f,-45f,523.1f,-43.9f,514.8f,1.1f,
            -28.8f,552.3f,-25.4f,539.1f,-17.8f,525.9f,1.8f,
            -17.8f,525.9f,-22.4f,516.2f,-17.3f,506.4f,1.1f,
            -17.8f,525.9f,-6.4f,518.5f,-4f,511.2f,1.1f,
            12.1f,637.3f,22f,605.9f,25.6f,574.5f,4.6f,
            25.6f,574.5f,19f,555.1f,13.3f,535.8f,2.9f,
            13.3f,535.8f,2.7f,526.2f,-5.9f,516.5f,1.8f,
            -5.9f,516.5f,-12f,514.5f,-25.5f,512.5f,1.1f,
            -5.9f,516.5f,-4.5f,507.6f,-10.2f,498.8f,1.1f,
            13.3f,535.8f,17.5f,523.2f,16.2f,510.5f,1.8f,
            16.2f,510.5f,12.2f,502.4f,11.1f,494.2f,1.1f,
            16.2f,510.5f,18.5f,504.1f,26.4f,497.8f,1.1f,
            25.6f,574.5f,39.5f,560.2f,53.8f,546f,2.9f,
            53.8f,546f,58.2f,532.9f,65.6f,519.7f,1.8f,
            65.6f,519.7f,62.8f,510.3f,62.4f,501f,1.1f,
            65.6f,519.7f,72.3f,512.5f,81.6f,505.3f,1.1f,
            53.8f,546f,70.8f,540f,80.3f,534.1f,1.8f,
            80.3f,534.1f,81.8f,526.5f,93f,518.9f,1.1f,
            80.3f,534.1f,85.7f,533.1f,98.5f,532.2f,1.1f,
            40.7f,721.2f,71.5f,692.3f,91.8f,663.4f,7.4f,
            91.8f,663.4f,98.3f,637.3f,111.9f,611.2f,4.6f,
            111.9f,611.2f,106.7f,592.2f,112.5f,573.2f,2.9f,
            112.5f,573.2f,110f,561.2f,98.5f,549.2f,1.8f,
            98.5f,549.2f,93f,545f,81.1f,540.8f,1.1f,
            98.5f,549.2f,93.8f,539.5f,96.7f,529.8f,1.1f,
            112.5f,573.2f,117.5f,562.5f,126.5f,551.9f,1.8f,
            126.5f,551.9f,130.6f,542.7f,128.5f,533.5f,1.1f,
            126.5f,551.9f,129.8f,547.3f,143.4f,542.8f,1.1f,
            111.9f,611.2f,130.1f,600.2f,141.9f,589.3f,2.9f,
            141.9f,589.3f,149.4f,577.2f,153.7f,565.2f,1.8f,
            153.7f,565.2f,147.9f,556.2f,153.6f,547.3f,1.1f,
            153.7f,565.2f,155.9f,560.2f,167.6f,555.2f,1.1f,
            141.9f,589.3f,158.4f,586.1f,168.7f,583f,1.8f,
            168.7f,583f,178.4f,577.7f,183.3f,572.4f,1.1f,
            168.7f,583f,183.6f,585.9f,186.6f,588.9f,1.1f,
            91.8f,663.4f,112.4f,649.8f,140.6f,636.1f,4.6f,
            140.6f,636.1f,147.9f,620f,163.6f,603.8f,2.9f,
            163.6f,603.8f,167f,589.4f,167.5f,574.9f,1.8f,
            167.5f,574.9f,161.9f,564.5f,161.9f,554.1f,1.1f,
            167.5f,574.9f,173.7f,566f,180.4f,557.2f,1.1f,
            163.6f,603.8f,173.4f,597.2f,189.2f,590.5f,1.8f,
            189.2f,590.5f,196.5f,581.5f,201.2f,572.4f,1.1f,
            189.2f,590.5f,198.4f,591.3f,207.9f,592f,1.1f,
            140.6f,636.1f,158.3f,636.1f,177.4f,636.2f,2.9f,
            177.4f,636.2f,185f,629.3f,199.3f,622.4f,1.8f,
            199.3f,622.4f,205.7f,616.2f,209.5f,609.9f,1.1f,
            199.3f,622.4f,212.7f,621.9f,216.8f,621.4f,1.1f,
            177.4f,636.2f,188.1f,641f,200.6f,645.8f,1.8f,
            200.6f,645.8f,212.3f,646.1f,219.6f,646.4f,1.1f,
            200.6f,645.8f,210.8f,652.2f,212.3f,658.7f,1.1f
        )
        val TREE1 = floatArrayOf(
            372f,840f,357.3f,788.3f,353.8f,736.6f,11f,
            353.8f,736.6f,339.3f,710.7f,313.9f,684.8f,6.8f,
            313.9f,684.8f,290.4f,675.6f,272.5f,666.4f,4.2f,
            272.5f,666.4f,261.6f,667.6f,241.7f,668.7f,2.6f,
            241.7f,668.7f,229.6f,673.1f,223.4f,677.5f,1.6f,
            223.4f,677.5f,218.9f,683f,214.6f,688.6f,1f,
            223.4f,677.5f,212.1f,676.8f,210.5f,676.1f,1f,
            241.7f,668.7f,227.4f,665.6f,222.9f,662.4f,1.6f,
            222.9f,662.4f,219.9f,663.8f,209.1f,665.3f,1f,
            222.9f,662.4f,214.6f,657.3f,211.9f,652.2f,1f,
            272.5f,666.4f,268.8f,653.9f,255.5f,641.3f,2.6f,
            255.5f,641.3f,244.8f,635.6f,235.6f,630f,1.6f,
            235.6f,630f,222.7f,629.8f,219.7f,629.6f,1f,
            235.6f,630f,228.6f,623.2f,228.8f,616.4f,1f,
            255.5f,641.3f,251.4f,631f,254.7f,620.7f,1.6f,
            254.7f,620.7f,245.5f,614.3f,248.1f,607.9f,1f,
            254.7f,620.7f,256.3f,614.1f,259.2f,607.5f,1f,
            313.9f,684.8f,308.8f,663.6f,309f,642.4f,4.2f,
            309f,642.4f,299.6f,630.5f,293.5f,618.6f,2.6f,
            293.5f,618.6f,289.6f,614f,276.5f,609.4f,1.6f,
            276.5f,609.4f,275.8f,608.6f,264.1f,607.8f,1f,
            276.5f,609.4f,272f,603.4f,268.5f,597.5f,1f,
            293.5f,618.6f,294.6f,609.5f,292f,600.4f,1.6f,
            292f,600.4f,284.2f,594.9f,286.8f,589.4f,1f,
            292f,600.4f,293.4f,594.9f,297.4f,589.3f,1f,
            309f,642.4f,315f,629.1f,319.5f,615.7f,2.6f,
            319.5f,615.7f,320.4f,605.6f,320.6f,595.4f,1.6f,
            320.6f,595.4f,316.3f,589.1f,313.4f,582.8f,1f,
            320.6f,595.4f,325.3f,589.5f,325.4f,583.6f,1f,
            319.5f,615.7f,327.1f,610.5f,334.5f,605.3f,1.6f,
            334.5f,605.3f,331.3f,600f,339.4f,594.7f,1f,
            334.5f,605.3f,344.1f,604.9f,347.7f,604.5f,1f,
            353.8f,736.6f,361.3f,705.4f,378f,674.2f,6.8f,
            378f,674.2f,377.9f,651.9f,371.1f,629.7f,4.2f,
            371.1f,629.7f,365f,617.7f,353.9f,605.7f,2.6f,
            353.9f,605.7f,348.8f,601.2f,334.2f,596.6f,1.6f,
            334.2f,596.6f,323f,596.5f,319.1f,596.4f,1f,
            334.2f,596.6f,331f,591f,326.6f,585.4f,1f,
            353.9f,605.7f,345.5f,595.4f,349f,585.1f,1.6f,
            349f,585.1f,346.7f,579.3f,338.6f,573.5f,1f,
            349f,585.1f,346.9f,578.6f,352.9f,572.2f,1f,
            371.1f,629.7f,379.3f,616.6f,381.6f,603.5f,2.6f,
            381.6f,603.5f,381.8f,594.4f,380.4f,585.3f,1.6f,
            380.4f,585.3f,377f,579.3f,375.2f,573.3f,1f,
            380.4f,585.3f,385.4f,579.2f,383.6f,573.1f,1f,
            381.6f,603.5f,388.5f,596f,395f,588.4f,1.6f,
            395f,588.4f,399.1f,581.7f,397.2f,575.1f,1f,
            395f,588.4f,395.2f,585.5f,406.1f,582.5f,1f,
            378f,674.2f,390.6f,656.4f,412.2f,638.7f,4.2f,
            412.2f,638.7f,423.4f,623.2f,426.7f,607.7f,2.6f,
            426.7f,607.7f,425.4f,596f,426.2f,584.4f,1.6f,
            426.2f,584.4f,429.1f,577.5f,420.4f,570.6f,1f,
            426.2f,584.4f,424.6f,577f,434.6f,569.7f,1f,
            426.7f,607.7f,441.6f,599.2f,445f,590.7f,1.6f,
            445f,590.7f,442f,583.2f,450f,575.6f,1f,
            445f,590.7f,453.4f,587.5f,460.5f,584.2f,1f,
            412.2f,638.7f,426.3f,631f,445.5f,623.4f,2.6f,
            445.5f,623.4f,445.9f,613.6f,458.2f,603.8f,1.6f,
            458.2f,603.8f,455.4f,595.2f,458.5f,586.6f,1f,
            458.2f,603.8f,469.8f,599.9f,470.8f,596.1f,1f,
            445.5f,623.4f,462f,623.6f,471.2f,623.8f,1.6f,
            471.2f,623.8f,480.5f,618.8f,487.3f,613.9f,1f,
            471.2f,623.8f,475.1f,627.2f,486.1f,630.6f,1f
        )
    }
}
