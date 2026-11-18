package com.example.buddy.ui

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

/** Where the 390 x 844 artwork of a scene page sits on the screen: `preserveAspectRatio="xMidYMid slice"` on a box that reaches 12 dp past every edge. */
internal class SceneFit {
    var k = 1f
    var ox = 0f
    var oy = 0f
    var density = 1f
    fun set(w: Float, h: Float, d: Float) {
        density = d
        val bw = w + 24f * d
        val bh = h + 24f * d
        k = max(bw / 390f, bh / 844f)
        ox = -12f * d + (bw - 390f * k) / 2f
        oy = -12f * d + (bh - 844f * k) / 2f
    }
    /** Starts a layer in the 390 x 844 space, slid sideways by [factor] times the page drift (8 dp per page). Pair with `cv.restore()`. */
    fun begin(cv: Canvas, page: Float, factor: Float) {
        cv.save()
        cv.translate(ox - page * 8f * density * factor, oy)
        cv.scale(k, k)
    }
}

/** CSS `ease-in-out infinite alternate`: 0..1..0 with period 2 * [dur], where [time] already contains the (negative) delay. */
internal fun altEase(time: Float, dur: Float): Float {
    val p = (time % (2f * dur)) / dur
    val tri = if (p < 1f) p else 2f - p
    return 0.5f - 0.5f * cos(PI.toFloat() * tri)
}

/**
 * Native animated background of the Green look: a meadow day over the whole page. Sun with turning rays and drifting clouds on top,
 * a swaying tree, butterflies and bees flying across the full height, petals and leaves falling, pollen twinkling, a flock of birds
 * crossing now and then, swaying grass and flowers at the foot. All geometry is built once; nothing is allocated while drawing.
 */
class GreenScene : ThemeScene() {
    override val ready = true

    private val fit = SceneFit()
    private val INK = Color.parseColor("#123d1a")
    private val ACCENT = Color.parseColor("#2fa83a")
    private val ACCENT2 = Color.parseColor("#7bc62d")
    private val OP = 0.30f
    private val SUN = Color.parseColor("#ffd84a")
    private val PINK = Color.parseColor("#ff8fb8")
    private val BF = intArrayOf(Color.parseColor("#ff9f1c"), Color.parseColor("#e63e8c"), Color.parseColor("#3a86ff"), Color.parseColor("#9b5de5"), Color.parseColor("#ffd23f"))
    private val BF2 = intArrayOf(Color.parseColor("#7a3b00"), Color.parseColor("#fff0f6"), Color.parseColor("#e8f1ff"), Color.parseColor("#fff4cc"), Color.parseColor("#6b4d00"))
    private val rnd = java.util.Random(7)
    private fun rf(a: Float, b: Float) = a + (b - a) * rnd.nextFloat()

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }

    private val ground = RectF(260f - 320f, 860f - 90f, 260f + 320f, 860f + 90f)
    private val trunk = Path()
    private val branchWidths = floatArrayOf(9f, 7f, 8f, 5f, 4f)
    private val branches = Array(5) { Path() }

    // the nine crowns: a soft disc and a cloud of leaves each, pulsing out of step
    private val cx = floatArrayOf(170f, 245f, 335f, 140f, 285f, 372f, 215f, 310f, 95f)
    private val cy = floatArrayOf(360f, 300f, 330f, 440f, 410f, 405f, 225f, 232f, 380f)
    private val cr = floatArrayOf(70f, 86f, 76f, 52f, 62f, 48f, 56f, 62f, 40f)
    private val crownLeaves = Array(9) { Path() }

    private val NF = 22
    private val fx = FloatArray(NF) { rf(10f, 380f) }
    private val fdur = FloatArray(NF) { rf(9f, 16f) }
    private val fdelay = FloatArray(NF) { rf(0f, 16f) }
    private val fsway = FloatArray(NF) { rf(18f, 46f) }
    private val fscale = FloatArray(NF) { rf(0.9f, 1.7f) }
    private val fspin = FloatArray(NF) { rf(-420f, 420f) }
    private val fkind = IntArray(NF) { it % 3 }

    private val sunShader = RadialGradient(330f, 80f, 230f, intArrayOf(Color.argb(150, 255, 224, 90), Color.argb(60, 255, 224, 90), Color.argb(0, 255, 224, 90)), floatArrayOf(0f, 0.45f, 1f), Shader.TileMode.CLAMP)
    private val glow = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL; shader = sunShader }
    private val sunRays = Path()
    private val cloud = Path()
    private val ncloud = 4
    private val cloudY = floatArrayOf(70f, 150f, 250f, 330f)
    private val cloudSpeed = floatArrayOf(9f, 6f, 12f, 7.5f)
    private val cloudOff = floatArrayOf(40f, 260f, 150f, 420f)
    private val cloudScale = floatArrayOf(1f, 1.3f, 0.8f, 1.1f)

    private val wingUp = Path()
    private val wingLow = Path()
    private val NB = 5
    private val bcx = floatArrayOf(110f, 280f, 190f, 70f, 330f)
    private val bcy = floatArrayOf(160f, 300f, 470f, 620f, 740f)
    private val bax = floatArrayOf(90f, 80f, 120f, 70f, 55f)
    private val bay = floatArrayOf(70f, 90f, 80f, 60f, 50f)
    private val bfx = floatArrayOf(0.31f, 0.27f, 0.23f, 0.35f, 0.29f)
    private val bfy = floatArrayOf(0.43f, 0.37f, 0.51f, 0.33f, 0.47f)
    private val bph = floatArrayOf(0f, 1.7f, 3.1f, 4.4f, 5.6f)

    private val NBEE = 3
    private val beeX = floatArrayOf(260f, 90f, 200f)
    private val beeY = floatArrayOf(540f, 420f, 700f)
    private val beeAx = floatArrayOf(70f, 60f, 80f)
    private val beeAy = floatArrayOf(30f, 26f, 22f)
    private val beeF = floatArrayOf(0.7f, 0.9f, 0.6f)
    private val beeBody = RectF(-6f, -4.2f, 6f, 4.2f)
    private val beeWing = RectF(-3f, -8f, 3f, -1f)

    private val NS = 26
    private val sx = FloatArray(NS) { rf(8f, 382f) }
    private val sy = FloatArray(NS) { rf(60f, 800f) }
    private val sph = FloatArray(NS) { rf(0f, 6.28f) }
    private val sspd = FloatArray(NS) { rf(0.6f, 1.8f) }
    private val sr = FloatArray(NS) { rf(1.4f, 3.2f) }

    private val grassBack = Path()
    private val grassFront = Path()
    private val NFL = 6
    private val flX = floatArrayOf(36f, 108f, 176f, 252f, 312f, 364f)
    private val flH = floatArrayOf(70f, 96f, 62f, 88f, 74f, 100f)
    private val flColor = intArrayOf(Color.parseColor("#ff5d8f"), Color.parseColor("#ffd23f"), Color.parseColor("#ffffff"), Color.parseColor("#ff9f1c"), Color.parseColor("#c77dff"), Color.parseColor("#ff5d8f"))
    private val bird = Path()
    private val fallLeaf = Path()
    private val fallOutline = Path()   // leaf edge plus its centre vein, for the thin outline

    init {
        trunk.apply {
            moveTo(272f, 844f); cubicTo(282f, 760f, 284f, 680f, 278f, 600f); cubicTo(274f, 560f, 264f, 530f, 252f, 500f)
            lineTo(264f, 495f); cubicTo(278f, 524f, 292f, 552f, 298f, 590f); cubicTo(304f, 540f, 320f, 500f, 342f, 470f)
            lineTo(352f, 478f); cubicTo(332f, 508f, 318f, 548f, 314f, 600f); cubicTo(310f, 680f, 318f, 760f, 334f, 844f); close()
        }
        branches[0].apply { moveTo(296f, 594f); cubicTo(270f, 520f, 230f, 470f, 180f, 420f) }
        branches[1].apply {
            moveTo(256f, 505f); cubicTo(220f, 440f, 190f, 400f, 150f, 365f)
            moveTo(344f, 474f); cubicTo(360f, 440f, 372f, 420f, 384f, 395f)
        }
        branches[2].apply { moveTo(302f, 560f); cubicTo(302f, 480f, 300f, 400f, 300f, 320f) }
        branches[3].apply {
            moveTo(300f, 420f); cubicTo(330f, 380f, 340f, 350f, 336f, 320f)
            moveTo(300f, 360f); cubicTo(270f, 300f, 240f, 270f, 215f, 230f)
        }
        branches[4].apply { moveTo(190f, 430f); cubicTo(170f, 420f, 140f, 430f, 120f, 440f) }

        val proto = Path().apply { moveTo(0f, -9f); cubicTo(7f, -5f, 7f, 5f, 0f, 9f); cubicTo(-7f, 5f, -7f, -5f, 0f, -9f); close() }
        val m = Matrix()
        for (i in 0 until 9) {
            val a = LEAVES[i]
            var j = 0
            while (j < a.size) {
                m.setScale(a[j + 3], a[j + 3]); m.postRotate(a[j + 2]); m.postTranslate(a[j], a[j + 1])
                crownLeaves[i].addPath(proto, m)
                j += 4
            }
        }
        for (i in 0 until 8) {
            val a = i * (PI.toFloat() / 4f)
            val c = cos(a); val sn = sin(a)
            val a2 = a + 0.16f
            sunRays.moveTo(330f + c * 52f, 80f + sn * 52f)
            sunRays.lineTo(330f + c * 210f, 80f + sn * 210f)
            sunRays.lineTo(330f + cos(a2) * 210f, 80f + sin(a2) * 210f)
            sunRays.lineTo(330f + cos(a2) * 52f, 80f + sin(a2) * 52f)
            sunRays.close()
        }
        cloud.addCircle(0f, 0f, 22f, Path.Direction.CW); cloud.addCircle(26f, -8f, 28f, Path.Direction.CW)
        cloud.addCircle(56f, 0f, 22f, Path.Direction.CW); cloud.addCircle(28f, 8f, 22f, Path.Direction.CW)
        wingUp.apply { moveTo(0f, 0f); cubicTo(2f, -16f, 16f, -26f, 20f, -14f); cubicTo(22f, -4f, 10f, 0f, 0f, 0f); close() }
        wingLow.apply { moveTo(0f, 0f); cubicTo(4f, -2f, 16f, 2f, 14f, 12f); cubicTo(10f, 18f, 2f, 8f, 0f, 0f); close() }
        for (layer in 0 until 2) {
            val g = if (layer == 0) grassBack else grassFront
            val n = if (layer == 0) 30 else 24
            val hMax = if (layer == 0) 54f else 38f
            for (i in 0 until n) {
                val x = (i + rnd.nextFloat() * 0.6f) * (400f / n) - 5f
                val hh = hMax * rf(0.55f, 1f)
                val lean = rf(-9f, 9f)
                g.moveTo(x - 4f, 0f); g.quadTo(x - 1f + lean * 0.4f, -hh * 0.6f, x + lean, -hh); g.quadTo(x + 1f + lean * 0.4f, -hh * 0.55f, x + 4f, 0f); g.close()
            }
        }
        fallLeaf.apply { moveTo(0f, -8f); cubicTo(6f, -4f, 6f, 4f, 0f, 8f); cubicTo(-6f, 4f, -6f, -4f, 0f, -8f); close() }
        fallOutline.addPath(fallLeaf); fallOutline.moveTo(0f, -8f); fallOutline.lineTo(0f, 8f)
    }

    override fun onSize(w: Float, h: Float, density: Float) { fit.set(w, h, density) }

    override fun draw(cv: Canvas, w: Float, h: Float, t: Float, dt: Float, page: Float) {
        fit.begin(cv, page, 0.4f)
        cv.drawCircle(330f, 80f, 230f * (0.96f + 0.04f * sin(t * 1.3f)), glow)
        cv.save()
        cv.rotate(t * 5f, 330f, 80f)
        fill.color = SUN; fill.alpha = 46
        cv.drawPath(sunRays, fill)
        cv.restore()
        fill.color = SUN; fill.alpha = 235
        cv.drawCircle(330f, 80f, 40f, fill)
        cv.restore()

        fit.begin(cv, page, 0.7f)
        for (i in 0 until ncloud) {
            val x = ((t * cloudSpeed[i] + cloudOff[i]) % 560f) - 110f
            cv.save()
            cv.translate(x, cloudY[i]); cv.scale(cloudScale[i], cloudScale[i])
            fill.color = Color.WHITE; fill.alpha = 205
            cv.drawPath(cloud, fill)
            cv.restore()
        }
        cv.restore()

        fit.begin(cv, page, 1f)
        fill.color = ACCENT; fill.alpha = (255f * 0.5f * OP).toInt()
        cv.drawOval(ground, fill)

        // the whole tree sways +-1.4 degrees around its foot, 7 s each way
        cv.rotate(-1.4f + 2.8f * altEase(t, 7f), 300f, 844f)
        fill.color = INK; fill.alpha = (255f * OP).toInt()
        cv.drawPath(trunk, fill)
        line.color = INK; line.alpha = (255f * OP).toInt()
        for (i in 0 until 5) { line.strokeWidth = branchWidths[i]; cv.drawPath(branches[i], line) }
        for (i in 0 until 9) {
            val op = 0.75f + 0.25f * altEase(t + 1.1f * i, 5.5f)
            fill.color = ACCENT; fill.alpha = (255f * 0.35f * OP * op).toInt()
            cv.drawCircle(cx[i], cy[i], cr[i], fill)
            fill.color = ACCENT2; fill.alpha = (255f * 0.9f * OP * op).toInt()
            cv.drawPath(crownLeaves[i], fill)
        }
        cv.restore()

        fit.begin(cv, page, 1.5f)
        for (i in 0 until NF) {
            val u = ((t + fdelay[i]) % fdur[i]) / fdur[i]
            val x = fx[i] + fsway[i] * sin(u * 6.2832f * 2f) - 30f * u
            val y = -30f + 910f * u
            val op = if (u < 0.06f) u / 0.06f else if (u > 0.94f) (1f - u) / 0.06f else 1f
            cv.save()
            cv.translate(x, y); cv.rotate(fspin[i] * u); cv.scale(fscale[i], fscale[i])
            when (fkind[i]) {
                0 -> { fill.color = ACCENT2; fill.alpha = (255f * 0.85f * op).toInt() }
                1 -> { fill.color = PINK; fill.alpha = (255f * 0.9f * op).toInt() }
                else -> { fill.color = ACCENT; fill.alpha = (255f * 0.75f * op).toInt() }
            }
            cv.drawPath(fallLeaf, fill)
            if (fkind[i] != 1) {
                line.color = INK; line.strokeWidth = 0.6f; line.alpha = (255f * 0.5f * op).toInt()
                cv.drawPath(fallOutline, line)
            }
            cv.restore()
        }
        cv.restore()

        fit.begin(cv, page, 1.2f)
        for (i in 0 until NS) {
            val tw = sin(t * sspd[i] + sph[i])
            val a = (tw * tw)
            fill.color = Color.WHITE; fill.alpha = (255f * (0.15f + 0.75f * a)).toInt()
            cv.drawCircle(sx[i] + 10f * sin(t * 0.4f + sph[i]), sy[i] - 14f * sin(t * 0.3f + sph[i] * 2f), sr[i] * (0.7f + 0.5f * a), fill)
            fill.color = SUN; fill.alpha = (255f * 0.35f * a).toInt()
            cv.drawCircle(sx[i] + 10f * sin(t * 0.4f + sph[i]), sy[i] - 14f * sin(t * 0.3f + sph[i] * 2f), sr[i] * 2.2f, fill)
        }
        cv.restore()

        fit.begin(cv, page, 1.4f)
        for (i in 0 until NB) {
            val a = t * bfx[i] + bph[i]
            val b = t * bfy[i] + bph[i] * 1.3f
            val x = bcx[i] + bax[i] * sin(a)
            val y = bcy[i] + bay[i] * sin(b)
            val vx = bax[i] * bfx[i] * cos(a)
            val vy = bay[i] * bfy[i] * cos(b)
            val heading = atan2(vy, vx) * 57.29578f
            val flap = 0.18f + 0.82f * abs(cos(t * (11f + i) + bph[i]))
            cv.save()
            cv.translate(x, y); cv.rotate(heading)
            for (side in 0 until 2) {
                cv.save()
                cv.scale(1f, if (side == 0) flap else -flap)
                fill.color = BF[i]; fill.alpha = 235
                cv.drawPath(wingUp, fill)
                cv.drawPath(wingLow, fill)
                fill.color = BF2[i]; fill.alpha = 200
                cv.drawCircle(11f, -11f, 3.2f, fill)
                cv.drawCircle(8f, 7f, 2.2f, fill)
                cv.restore()
            }
            line.color = INK; line.strokeWidth = 2.4f; line.alpha = 230
            cv.drawLine(-8f, 0f, 10f, 0f, line)
            line.strokeWidth = 0.9f
            cv.drawLine(10f, 0f, 16f, -5f, line)
            cv.drawLine(10f, 0f, 16f, 5f, line)
            cv.restore()
        }
        for (i in 0 until NBEE) {
            val a = t * beeF[i] + i * 2f
            val x = beeX[i] + beeAx[i] * sin(a)
            val y = beeY[i] + beeAy[i] * sin(a * 2f) + 6f * sin(t * 22f + i)
            val dir = if (cos(a) >= 0f) 1f else -1f
            cv.save()
            cv.translate(x, y); cv.scale(dir, 1f)
            fill.color = Color.WHITE; fill.alpha = (255f * (0.45f + 0.35f * abs(sin(t * 55f + i)))).toInt()
            cv.drawOval(beeWing, fill)
            fill.color = SUN; fill.alpha = 245
            cv.drawOval(beeBody, fill)
            line.color = INK; line.strokeWidth = 2f; line.alpha = 230
            cv.drawLine(-2f, -3.6f, -2f, 3.6f, line)
            cv.drawLine(2.5f, -3.6f, 2.5f, 3.6f, line)
            cv.restore()
        }
        cv.restore()

        val bu = (t % 17f) / 6.5f
        if (bu < 1f) {
            fit.begin(cv, page, 0.8f)
            line.color = INK; line.strokeWidth = 1.8f; line.alpha = 210
            for (i in 0 until 7) {
                val k = i / 2 + 1
                val side = if (i % 2 == 0) 1f else -1f
                val bx = -60f + 520f * bu - k * 20f
                val by = 120f + 40f * bu + side * k * 11f
                val f = sin(t * 7f + i) * 4f
                bird.reset()
                bird.moveTo(bx - 8f, by + f * 0.4f); bird.quadTo(bx - 4f, by - 4f - f, bx, by)
                bird.quadTo(bx + 4f, by - 4f - f, bx + 8f, by + f * 0.4f)
                cv.drawPath(bird, line)
            }
            cv.restore()
        }

        fit.begin(cv, page, 1.6f)
        cv.translate(0f, 856f)
        cv.save()
        cv.skew(0.10f * sin(t * 1.1f), 0f)
        fill.color = ACCENT; fill.alpha = 150
        cv.drawPath(grassBack, fill)
        cv.restore()
        for (i in 0 until NFL) {
            cv.save()
            cv.translate(flX[i], 0f)
            cv.rotate(4f * sin(t * 1.2f + i * 1.3f))
            line.color = ACCENT; line.strokeWidth = 2.4f; line.alpha = 230
            cv.drawLine(0f, 0f, 0f, -flH[i], line)
            for (p in 0 until 5) {
                val ang = p * 1.2566f + t * 0.3f
                fill.color = flColor[i]; fill.alpha = 240
                cv.drawCircle(cos(ang) * 6.5f, -flH[i] + sin(ang) * 6.5f, 4.6f, fill)
            }
            fill.color = SUN; fill.alpha = 255
            cv.drawCircle(0f, -flH[i], 3.4f, fill)
            cv.restore()
        }
        cv.save()
        cv.skew(0.13f * sin(t * 1.4f + 1f), 0f)
        fill.color = ACCENT2; fill.alpha = 230
        cv.drawPath(grassFront, fill)
        cv.restore()
        cv.restore()
    }

    private companion object {
        /** Per crown: x, y, rotation (deg), scale of every leaf, from the original artwork. */
        val LEAVES: Array<FloatArray> = arrayOf(
        floatArrayOf(149f,323f,234f,0.85f,174f,346f,21f,1.16f,115f,353f,25f,0.86f,161f,394f,45f,0.96f,185f,407f,208f,1.08f,227f,312f,309f,1f,128f,320f,111f,1.37f,132f,369f,230f,1.06f,176f,314f,21f,0.94f,191f,352f,113f,1.21f,164f,339f,286f,1.29f,140f,368f,189f,1.41f,197f,338f,353f,0.88f,160f,387f,55f,1.14f,115f,378f,275f,1.2f,215f,340f,250f,1.22f,180f,355f,302f,1.46f,167f,377f,22f,1.29f,188f,412f,296f,1f,156f,378f,8f,1.12f,130f,320f,21f,1.34f,126f,333f,141f,1.41f,120f,355f,198f,1.42f),
        floatArrayOf(292f,347f,100f,1.09f,224f,350f,345f,0.91f,198f,265f,84f,1.14f,258f,269f,1f,1.09f,226f,309f,343f,1.28f,247f,315f,243f,0.84f,303f,336f,315f,1.36f,229f,287f,37f,1.24f,181f,244f,75f,0.91f,222f,242f,0f,0.91f,187f,282f,9f,1.41f,262f,255f,91f,1.04f,225f,251f,306f,1.5f,240f,298f,31f,0.87f,222f,270f,298f,0.91f,175f,358f,190f,0.9f,251f,239f,190f,1.48f,298f,325f,94f,1.06f,196f,335f,192f,1.35f,220f,264f,292f,1.49f,297f,339f,295f,1.32f,205f,302f,128f,0.82f,176f,272f,93f,1.28f,312f,293f,337f,1.49f,312f,283f,79f,0.96f,201f,262f,225f,1.43f,295f,297f,235f,1.36f,184f,321f,328f,1.35f),
        floatArrayOf(367f,327f,64f,1.35f,313f,364f,350f,1.08f,322f,381f,261f,0.92f,287f,290f,326f,1.36f,289f,367f,353f,1.26f,316f,336f,47f,0.81f,396f,347f,190f,1.45f,326f,372f,297f,0.95f,303f,306f,87f,1.21f,304f,321f,47f,1.44f,316f,325f,210f,1.43f,325f,378f,181f,1.17f,338f,275f,158f,0.93f,271f,364f,62f,1.13f,364f,336f,117f,1.16f,342f,362f,38f,1.19f,303f,305f,278f,1.16f,343f,360f,328f,1.11f,350f,331f,184f,1.28f,329f,334f,172f,1.46f,361f,373f,339f,0.98f,343f,381f,302f,0.9f,286f,323f,26f,0.97f,280f,349f,282f,1.43f,290f,355f,238f,0.9f),
        floatArrayOf(174f,476f,79f,1.47f,131f,439f,356f,1.38f,110f,435f,186f,1.04f,113f,426f,260f,0.81f,145f,435f,7f,1.03f,151f,441f,23f,1.49f,165f,477f,38f,0.99f,99f,462f,97f,0.89f,133f,472f,295f,0.98f,109f,473f,205f,1.29f,104f,405f,248f,1.1f,102f,474f,228f,1.36f,103f,468f,24f,1.4f,136f,427f,199f,1.45f,119f,411f,190f,0.97f,105f,414f,18f,0.94f,123f,425f,273f,1f),
        floatArrayOf(285f,380f,125f,0.81f,259f,365f,264f,1.19f,252f,408f,336f,0.87f,319f,404f,178f,1.38f,274f,411f,248f,1.49f,268f,441f,254f,1.25f,275f,396f,20f,0.89f,240f,432f,92f,0.91f,241f,442f,313f,1.27f,262f,386f,106f,1.12f,249f,405f,95f,1.47f,335f,414f,88f,1.48f,265f,397f,0f,1.07f,282f,410f,72f,1.15f,233f,388f,32f,1.08f,237f,366f,110f,0.96f,294f,413f,270f,1.26f,308f,445f,140f,1.03f,336f,377f,261f,1.25f,237f,441f,321f,1.24f),
        floatArrayOf(391f,427f,50f,1.17f,372f,429f,290f,1.38f,379f,433f,246f,1.29f,350f,371f,48f,1.05f,340f,429f,201f,1.24f,382f,418f,176f,0.8f,396f,423f,181f,1.17f,385f,374f,265f,0.98f,337f,388f,263f,0.94f,392f,439f,178f,1.07f,370f,418f,276f,1.23f,384f,375f,53f,0.98f,392f,391f,204f,0.81f,336f,388f,242f,1.28f,386f,390f,186f,1.13f,369f,378f,322f,0.94f),
        floatArrayOf(261f,262f,6f,1.12f,245f,264f,162f,0.99f,187f,262f,76f,1.21f,181f,227f,343f,0.89f,245f,226f,319f,1.29f,189f,258f,175f,0.82f,168f,224f,162f,1.01f,181f,212f,114f,1.39f,168f,246f,302f,0.88f,256f,243f,325f,1f,203f,216f,360f,1.21f,202f,219f,99f,0.83f,177f,253f,103f,1.45f,191f,205f,184f,0.93f,203f,263f,318f,1.37f,227f,260f,339f,1.18f,236f,187f,264f,1.12f,239f,237f,103f,0.83f),
        floatArrayOf(355f,197f,170f,1.04f,289f,254f,351f,0.98f,326f,213f,201f,1.08f,275f,201f,75f,1.43f,310f,206f,326f,1.5f,305f,198f,69f,0.86f,293f,194f,86f,0.98f,317f,268f,270f,1.09f,301f,234f,136f,1.04f,264f,211f,348f,0.89f,310f,244f,311f,0.95f,286f,209f,144f,1.11f,358f,264f,314f,0.82f,261f,251f,322f,1.13f,319f,186f,141f,1.45f,344f,265f,350f,0.97f,269f,200f,188f,1.28f,357f,253f,233f,1.34f,306f,237f,14f,1.35f,282f,271f,232f,1.01f),
        floatArrayOf(70f,365f,229f,1.29f,69f,354f,189f,1.21f,87f,363f,216f,0.81f,82f,378f,345f,1.25f,121f,379f,85f,0.97f,126f,392f,111f,0.82f,95f,390f,151f,0.98f,106f,406f,82f,0.82f,84f,375f,246f,0.94f,115f,394f,182f,0.94f,127f,369f,295f,0.96f,76f,396f,106f,1.47f,95f,361f,80f,1.09f)
        )
    }
}
