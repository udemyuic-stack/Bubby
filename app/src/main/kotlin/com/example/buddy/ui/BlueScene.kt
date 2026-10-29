package com.example.buddy.ui

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import kotlin.math.PI
import kotlin.math.sin

/**
 * Native animated background of the Blue look: light rays, five scrolling waves, swaying kelp and rising bubbles
 * (assets/scene/blue.html, the `wh` artwork, drawn at 11% opacity behind the pages). 43 draw calls per frame, all geometry built once.
 * Shares [SceneFit] and [altEase] with GreenScene.
 */
class BlueScene : ThemeScene() {
    override val ready = true

    private val fit = SceneFit()
    private val ACCENT = Color.parseColor("#2f66e0")
    private val OP = 0.11f

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }
    private val rayPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }

    // rays: four slanted strips that fade out downwards, the whole fan swings +-3 degrees around the top middle (9 s each way)
    private val rays = Array(4) { Path() }
    private val rayBottom = floatArrayOf(700f, 640f, 720f, 520f)
    private val rayShader = arrayOfNulls<Shader>(4)

    // waves: a sine of period 130 that scrolls left by one period per cycle
    private val waveBase = floatArrayOf(26f, 70f, 250f, 470f, 690f)
    private val waveAmp = floatArrayOf(9f, 7f, 5f, 6f, 4f)
    private val waveDur = floatArrayOf(5f, 6f, 9f, 7.5f, 11f)
    private val waveStroke = floatArrayOf(0f, 2.0f, 1.7f, 1.4f, 1.1f)   // the first one is a filled band instead
    private val waves = Array(5) { Path() }

    private val kelp = Array(5) { Path() }
    private val kelpPivotX = FloatArray(5)
    private val kelpPivotY = FloatArray(5)
    private val hill = Path()

    private val bx = floatArrayOf(246f, 199f, 126f, 241f, 297f, 262f, 227f, 51f, 193f, 42f, 161f, 344f, 74f, 217f)
    private val br = floatArrayOf(2.8f, 4.1f, 3.9f, 2.4f, 6.6f, 2.2f, 2.2f, 3.3f, 3.8f, 3.3f, 3.5f, 6.7f, 4.4f, 5.9f)
    private val bdur = floatArrayOf(7f, 13.2f, 12.4f, 11.9f, 8.4f, 9.9f, 7.2f, 12.2f, 9.3f, 12f, 12.1f, 7.5f, 13.7f, 13.4f)
    private val bdelay = floatArrayOf(3.9f, 7.9f, 4.3f, 2.7f, 5.1f, 11.4f, 0.9f, 12.6f, 13.4f, 4.4f, 8.3f, 11.6f, 13.4f, 11.4f)
    // a bubble wobbles sideways while it climbs 930: x and y offsets at 0, 25, 50, 75, 100 % of its trip
    private val bobX = floatArrayOf(0f, 8f, -6f, 7f, 0f)
    private val bobY = floatArrayOf(0f, -230f, -460f, -690f, -930f)

    init {
        rays[0].apply { moveTo(150f, 0f); lineTo(190f, 0f); lineTo(120f, 700f); lineTo(40f, 700f); close() }
        rays[1].apply { moveTo(220f, 0f); lineTo(245f, 0f); lineTo(260f, 640f); lineTo(200f, 640f); close() }
        rays[2].apply { moveTo(290f, 0f); lineTo(330f, 0f); lineTo(420f, 720f); lineTo(340f, 720f); close() }
        rays[3].apply { moveTo(60f, 0f); lineTo(85f, 0f); lineTo(10f, 520f); lineTo(-30f, 520f); close() }
        for (i in 0 until 4) {
            // white 90% -> 0 over each strip's own height, times the 11% of the whole page
            rayShader[i] = LinearGradient(0f, 0f, 0f, rayBottom[i], Color.argb((255f * 0.9f * OP).toInt(), 255, 255, 255), Color.argb(0, 255, 255, 255), Shader.TileMode.CLAMP)
        }
        for (i in 0 until 5) {
            val p = waves[i]
            val twoPi = (2.0 * PI).toFloat()
            p.moveTo(0f, waveBase[i])
            var x = 0f
            while (x <= 780f) { p.lineTo(x, waveBase[i] + waveAmp[i] * sin(twoPi * x / 130f)); x += 10f }
            if (i == 0) { p.lineTo(780f, 0f); p.lineTo(0f, 0f); p.close() }
        }
        val kd = arrayOf(
            floatArrayOf(30f, 850f, 16f, 803f, 44f, 756f, 26f, 694f),
            floatArrayOf(62f, 850f, 48f, 789f, 76f, 728f, 58f, 647f),
            floatArrayOf(330f, 850f, 316f, 801f, 344f, 752f, 326f, 687f),
            floatArrayOf(352f, 850f, 338f, 808f, 366f, 765f, 348f, 709f),
            floatArrayOf(372f, 850f, 358f, 797f, 386f, 743f, 368f, 672f),
        )
        val r = RectF()
        for (i in 0 until 5) {
            val a = kd[i]
            kelp[i].moveTo(a[0], a[1]); kelp[i].cubicTo(a[2], a[3], a[4], a[5], a[6], a[7])
            kelp[i].computeBounds(r, true)
            kelpPivotX[i] = r.centerX(); kelpPivotY[i] = r.bottom   // turns around the middle of its foot (transform-origin 50% 100%)
        }
        hill.apply {
            moveTo(-10f, 844f); cubicTo(60f, 800f, 120f, 830f, 190f, 810f); cubicTo(260f, 790f, 320f, 820f, 400f, 800f); lineTo(400f, 844f); close()
        }
    }

    override fun onSize(w: Float, h: Float, density: Float) { fit.set(w, h, density) }

    override fun draw(cv: Canvas, w: Float, h: Float, t: Float, dt: Float, page: Float) {
        // light rays (farthest)
        fit.begin(cv, page, 0.5f)
        val e = altEase(t, 9f)
        cv.rotate(-3f + 6f * e, 195f, 0f)
        rayPaint.alpha = (255f * (0.6f + 0.4f * e)).toInt()
        for (i in 0 until 4) { rayPaint.shader = rayShader[i]; cv.drawPath(rays[i], rayPaint) }
        cv.restore()

        // waves
        fit.begin(cv, page, 1f)
        for (i in 0 until 5) {
            cv.save()
            cv.translate(-130f * ((t % waveDur[i]) / waveDur[i]), 0f)
            if (i == 0) {
                fill.color = Color.WHITE; fill.alpha = (255f * 0.55f * OP).toInt()
                cv.drawPath(waves[0], fill)
            } else {
                line.color = Color.WHITE; line.alpha = (255f * OP).toInt(); line.strokeWidth = waveStroke[i]
                cv.drawPath(waves[i], line)
            }
            cv.restore()
        }
        cv.restore()

        // kelp and the hill it grows from (near)
        fit.begin(cv, page, 1.3f)
        line.color = ACCENT; line.alpha = (255f * OP).toInt(); line.strokeWidth = 7f
        for (i in 0 until 5) {
            cv.save()
            cv.rotate(-5f + 10f * altEase(t + 0.8f * i, 4.5f), kelpPivotX[i], kelpPivotY[i])
            cv.drawPath(kelp[i], line)
            cv.restore()
        }
        fill.color = ACCENT; fill.alpha = (255f * 0.5f * OP).toInt()
        cv.drawPath(hill, fill)
        cv.restore()

        // bubbles
        fit.begin(cv, page, 1.1f)
        for (i in 0 until 14) {
            val seg = ((t + bdelay[i]) % bdur[i]) / bdur[i] * 4f
            val k = if (seg >= 4f) 3 else seg.toInt()
            val f = seg - k
            val x = bx[i] + bobX[k] + (bobX[k + 1] - bobX[k]) * f
            val y = 880f + bobY[k] + (bobY[k + 1] - bobY[k]) * f
            fill.color = Color.WHITE; fill.alpha = (255f * 0.25f * OP).toInt()
            cv.drawCircle(x, y, br[i], fill)
            line.color = Color.WHITE; line.alpha = (255f * OP).toInt(); line.strokeWidth = 1f
            cv.drawCircle(x, y, br[i], line)
        }
        cv.restore()
    }
}
