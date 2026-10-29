package com.example.buddy.ui

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max

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
 * Native animated background of the Green look: a swaying tree with pulsing crowns and leaves falling past it (assets/scene/green.html,
 * the `bf` artwork, drawn at 11% opacity behind the pages). About 35 draw calls per frame, all geometry built once.
 */
class GreenScene : ThemeScene() {
    override val ready = true

    private val fit = SceneFit()
    private val INK = Color.parseColor("#123d1a")
    private val ACCENT = Color.parseColor("#2fa83a")
    private val ACCENT2 = Color.parseColor("#7bc62d")
    private val OP = 0.11f

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

    // falling leaves: x, duration, delay (seconds, already positive)
    private val fx = floatArrayOf(88f, 261f, 365f, 90f, 220f, 144f, 328f, 253f)
    private val fdur = floatArrayOf(14.7f, 9.3f, 9.9f, 10.1f, 13.4f, 11.0f, 13.5f, 14.0f)
    private val fdelay = floatArrayOf(2.2f, 0.4f, 0.8f, 6.7f, 15.0f, 2.8f, 0.5f, 14.8f)
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
        fallLeaf.apply { moveTo(0f, -8f); cubicTo(6f, -4f, 6f, 4f, 0f, 8f); cubicTo(-6f, 4f, -6f, -4f, 0f, -8f); close() }
        fallOutline.addPath(fallLeaf); fallOutline.moveTo(0f, -8f); fallOutline.lineTo(0f, 8f)
    }

    override fun onSize(w: Float, h: Float, density: Float) { fit.set(w, h, density) }

    override fun draw(cv: Canvas, w: Float, h: Float, t: Float, dt: Float, page: Float) {
        // ground glow and the tree (far layer)
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

        // leaves drifting down (near layer): 12 s style fall, left by 40 then right by 50 while turning one and a half times
        fit.begin(cv, page, 1.5f)
        for (i in 0 until 8) {
            val u = ((t + fdelay[i]) % fdur[i]) / fdur[i]
            val x: Float; val y: Float; val rot: Float
            if (u < 0.5f) {
                val f = u * 2f
                x = fx[i] - 40f * f; y = 220f + 320f * f; rot = 260f * f
            } else {
                val f = (u - 0.5f) * 2f
                x = fx[i] - 40f + 50f * f; y = 540f + 340f * f; rot = 260f + 280f * f
            }
            val op = if (u < 0.08f) u / 0.08f else if (u > 0.92f) (1f - u) / 0.08f else 1f
            cv.save()
            cv.translate(x, y); cv.rotate(rot)
            fill.color = ACCENT2; fill.alpha = (255f * OP * op).toInt()
            cv.drawPath(fallLeaf, fill)
            line.color = INK; line.strokeWidth = 0.6f; line.alpha = (255f * OP * op).toInt()
            cv.drawPath(fallOutline, line)
            cv.restore()
        }
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
