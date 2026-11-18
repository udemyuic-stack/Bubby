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
 * Native animated background of the Blue look: a living sea over the whole page. Light rays and scrolling waves on top, three schools of
 * fish swimming across at different depths, jellyfish drifting, a whale passing now and then, bubbles rising the full height,
 * sparkling light spots, swaying kelp and coral at the foot. All geometry is built once; nothing is allocated while drawing.
 * Shares [SceneFit] and [altEase] with GreenScene.
 */
class BlueScene : ThemeScene() {
    override val ready = true

    private val fit = SceneFit()
    private val ACCENT = Color.parseColor("#2f66e0")
    private val OP = 0.34f
    private val rnd = java.util.Random(11)
    private fun rf(a: Float, b: Float) = a + (b - a) * rnd.nextFloat()

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

    private val NBUB = 26
    private val bx = FloatArray(NBUB) { rf(15f, 375f) }
    private val br = FloatArray(NBUB) { rf(2.2f, 7f) }
    private val bdur = FloatArray(NBUB) { rf(7f, 14f) }
    private val bdelay = FloatArray(NBUB) { rf(0f, 14f) }

    private val FISH = intArrayOf(
        Color.parseColor("#ff7a2f"), Color.parseColor("#ffb703"), Color.parseColor("#1d4ed8"),
        Color.parseColor("#14b8a6"), Color.parseColor("#ec4899"))
    private val NFISH = 17
    private val fishY = FloatArray(NFISH)
    private val fishSpd = FloatArray(NFISH)
    private val fishOff = FloatArray(NFISH) { rf(0f, 560f) }
    private val fishSize = FloatArray(NFISH)
    private val fishPh = FloatArray(NFISH) { rf(0f, 6.28f) }
    private val fishDir = FloatArray(NFISH)
    private val fishCol = IntArray(NFISH)
    private val fishBody = Path()
    private val fishTail = Path()
    private val fishFin = Path()

    private val NJ = 3
    private val jx = floatArrayOf(90f, 280f, 190f)
    private val jy = floatArrayOf(360f, 520f, 200f)
    private val jph = floatArrayOf(0f, 2.1f, 4.2f)
    private val bell = Path()
    private val tentacle = Path()

    private val NSP = 22
    private val spx = FloatArray(NSP) { rf(10f, 380f) }
    private val spy = FloatArray(NSP) { rf(40f, 640f) }
    private val spph = FloatArray(NSP) { rf(0f, 6.28f) }
    private val spr = FloatArray(NSP) { rf(2f, 6f) }

    private val whale = Path()
    private val whaleBelly = Path()

    private val coral = Array(4) { Path() }
    private val coralX = floatArrayOf(112f, 152f, 222f, 268f)
    private val coralColor = intArrayOf(Color.parseColor("#ff6b6b"), Color.parseColor("#ffa94d"), Color.parseColor("#f06595"), Color.parseColor("#ff8787"))
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
        for (i in 0 until NFISH) {
            val school = i / 6
            fishY[i] = when (school) { 0 -> rf(150f, 260f); 1 -> rf(340f, 470f); else -> rf(540f, 700f) }
            fishDir[i] = if (school == 1) -1f else 1f
            fishSpd[i] = (if (school == 0) 26f else if (school == 1) 18f else 32f) * rf(0.9f, 1.1f)
            fishSize[i] = (if (school == 0) 0.8f else if (school == 1) 1.15f else 0.95f) * rf(0.85f, 1.15f)
            fishCol[i] = FISH[(school * 2 + (i % 2)) % FISH.size]
        }
        fishBody.addOval(RectF(-14f, -6.5f, 14f, 6.5f), Path.Direction.CW)
        fishTail.apply { moveTo(-11f, 0f); lineTo(-25f, -9f); lineTo(-22f, 0f); lineTo(-25f, 9f); close() }
        fishFin.apply { moveTo(-2f, -5.5f); lineTo(3f, -12f); lineTo(7f, -5.5f); close() }
        bell.apply { moveTo(-18f, 0f); cubicTo(-18f, -26f, 18f, -26f, 18f, 0f); cubicTo(10f, 4f, -10f, 4f, -18f, 0f); close() }
        tentacle.apply { moveTo(0f, 2f); cubicTo(-5f, 14f, 5f, 24f, 0f, 38f) }
        whale.apply {
            moveTo(0f, 14f); cubicTo(10f, -26f, 90f, -36f, 150f, -6f); lineTo(178f, -28f); lineTo(172f, -4f); lineTo(182f, 20f)
            lineTo(150f, 8f); cubicTo(100f, 40f, 20f, 38f, 0f, 14f); close()
        }
        whaleBelly.apply { moveTo(6f, 18f); cubicTo(40f, 32f, 100f, 32f, 140f, 12f); cubicTo(100f, 22f, 40f, 22f, 6f, 18f); close() }
        for (i in 0 until 4) {
            coral[i].apply {
                moveTo(0f, 0f); lineTo(0f, -36f)
                moveTo(0f, -14f); lineTo(-12f, -30f)
                moveTo(0f, -22f); lineTo(12f, -42f)
            }
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
                line.color = ACCENT; line.alpha = (255f * OP).toInt(); line.strokeWidth = waveStroke[i] * 1.6f
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
            fill.color = Color.WHITE; fill.alpha = 90
            cv.drawCircle(x, y, br[i], fill)
            line.color = Color.WHITE; line.alpha = 230; line.strokeWidth = 1.2f
            cv.drawCircle(x, y, br[i], line)
            fill.alpha = 220
            cv.drawCircle(x - br[i] * 0.35f, y - br[i] * 0.35f, br[i] * 0.22f, fill)
        }
        cv.restore()

        fit.begin(cv, page, 0.9f)
        for (i in 0 until NSP) {
            val a = sin(t * 0.9f + spph[i])
            fill.color = Color.WHITE; fill.alpha = (255f * 0.75f * a * a).toInt()
            cv.drawCircle(spx[i] + 14f * sin(t * 0.35f + spph[i]), spy[i] + 10f * sin(t * 0.5f + spph[i] * 2f), spr[i] * (0.6f + 0.6f * a * a), fill)
        }
        cv.restore()

        val wu = (t % 23f) / 9f
        if (wu < 1f) {
            fit.begin(cv, page, 0.6f)
            cv.translate(-220f + 640f * wu, 300f + 22f * sin(t * 0.7f))
            cv.rotate(2.5f * sin(t * 1.6f), 80f, 0f)
            fill.color = Color.parseColor("#1e3a8a"); fill.alpha = 150
            cv.drawPath(whale, fill)
            fill.color = Color.WHITE; fill.alpha = 90
            cv.drawPath(whaleBelly, fill)
            fill.color = Color.WHITE; fill.alpha = 230
            cv.drawCircle(22f, 4f, 2.2f, fill)
            cv.restore()
        }

        fit.begin(cv, page, 1.05f)
        for (i in 0 until NFISH) {
            val raw = (t * fishSpd[i] + fishOff[i]) % 560f
            val x = if (fishDir[i] > 0f) raw - 80f else 480f - raw
            val y = fishY[i] + 9f * sin(t * 1.4f + fishPh[i])
            cv.save()
            cv.translate(x, y); cv.scale(fishDir[i] * fishSize[i], fishSize[i])
            fill.color = fishCol[i]; fill.alpha = 235
            cv.save()
            cv.rotate(16f * sin(t * 8f + fishPh[i]), -11f, 0f)
            cv.drawPath(fishTail, fill)
            cv.restore()
            cv.drawPath(fishBody, fill)
            cv.drawPath(fishFin, fill)
            fill.color = Color.WHITE; fill.alpha = 235
            cv.drawCircle(8f, -1.5f, 2.4f, fill)
            fill.color = Color.BLACK; fill.alpha = 230
            cv.drawCircle(8.6f, -1.5f, 1.1f, fill)
            cv.restore()
        }
        cv.restore()

        fit.begin(cv, page, 1.15f)
        for (i in 0 until NJ) {
            val x = jx[i] + 34f * sin(t * 0.32f + jph[i])
            val y = jy[i] + 70f * sin(t * 0.22f + jph[i])
            val pulse = 0.85f + 0.2f * sin(t * 2.2f + jph[i])
            cv.save()
            cv.translate(x, y)
            cv.save()
            cv.scale(1f, pulse)
            fill.color = Color.parseColor("#c084fc"); fill.alpha = 205
            cv.drawPath(bell, fill)
            cv.restore()
            line.color = Color.parseColor("#c084fc"); line.strokeWidth = 2f; line.alpha = 170
            for (k in -2..2) {
                cv.save()
                cv.translate(k * 6.5f, 0f)
                cv.skew(0.18f * sin(t * 2f + jph[i] + k), 0f)
                cv.scale(1f, 1f + 0.12f * (1f - pulse) * 4f)
                cv.drawPath(tentacle, line)
                cv.restore()
            }
            cv.restore()
        }
        cv.restore()

        fit.begin(cv, page, 1.3f)
        cv.translate(0f, 846f)
        line.strokeWidth = 5f
        for (i in 0 until 4) {
            cv.save()
            cv.translate(coralX[i], 0f)
            cv.rotate(3f * sin(t * 1.1f + i * 1.4f))
            line.color = coralColor[i]; line.alpha = 235
            cv.drawPath(coral[i], line)
            cv.restore()
        }
        cv.restore()
    }
}
