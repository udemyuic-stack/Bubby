package com.example.buddy.ui.home

import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import kotlin.math.floor
import kotlin.random.Random

class SpaceEarthFx {
    private val clip = Path().apply { addCircle(0f, 0f, R, Path.Direction.CW) }
    private val continents = Path()
    private val clouds = Path()
    private val dust = FloatArray(DUST * 3)
    private val dustColors = IntArray(DUST)
    private val box = RectF()
    private val matrix = Matrix()
    private var lavaGlow: Bitmap? = null
    private var damageGlow: Bitmap? = null
    private var lightsGlow: Bitmap? = null
    private val dst = RectF(-BAKE_HALF, -BAKE_HALF, BAKE_HALF, BAKE_HALF)
    private val bmpPaint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
    private val lava = sphere(-8f, -12f, 64f, intArrayOf(0xFFFFE08A.toInt(), 0xFFFF6A1A.toInt(), 0xFF8A1A08.toInt()), floatArrayOf(0f, 0.5f, 1f))
    private val crust = sphere(-9.2f, -12f, 64f, intArrayOf(0xFF9A8F96.toInt(), 0xFF3C3540.toInt()), floatArrayOf(0f, 1f))
    private val ocean = sphere(-9.2f, -12f, 64f, intArrayOf(0xFF7FD0FF.toInt(), 0xFF2F7FD6.toInt(), 0xFF174A9A.toInt()), floatArrayOf(0f, 0.6f, 1f))
    private val shade = sphere(-16f, -16f, 72f, intArrayOf(0x000A0820, 0x000A0820, 0x990A0820.toInt()), floatArrayOf(0f, 0.55f, 1f))
    private val brown = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFB08A5A.toInt() }
    private val green = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF4FA457.toInt() }
    private val cloud = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xBFFFFFFF.toInt() }
    private val craterFill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x992B2530.toInt() }
    private val craterRim = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 0.6f
        color = 0xFFB7AEB8.toInt()
    }
    private val softCloud = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader = RadialGradient(0f, 0f, 1f, intArrayOf(0x80FFFFFF.toInt(), 0x40FFFFFF, 0x00FFFFFF), floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP)
    }
    private val dustPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rim = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = 0xFFFFFFFF.toInt()
    }
    private val atmo = Paint(Paint.ANTI_ALIAS_FLAG)
    private val atmoBlue = halo(0x8F, 0xD0, 0xFF)
    private val atmoOrange = halo(0xFF, 0x8A, 0x3A)
    private val hitFill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF3A0C08.toInt() }
    private val fireCore = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFF6A1A.toInt() }
    private val fireGlow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader = RadialGradient(0f, 0f, 1f, intArrayOf(0x99FF6A1A.toInt(), 0x00FF6A1A), floatArrayOf(0f, 1f), Shader.TileMode.CLAMP)
    }

    init {
        for (i in 0 until CONT.size step 5) {
            box.set(CONT[i] - CONT[i + 2], CONT[i + 1] - CONT[i + 3], CONT[i] + CONT[i + 2], CONT[i + 1] + CONT[i + 3])
            matrix.setRotate(CONT[i + 4], CONT[i], CONT[i + 1])
            val part = Path()
            part.addOval(box, Path.Direction.CW)
            part.transform(matrix)
            continents.addPath(part)
        }
        for (i in 0 until CLOUDS.size step 4) {
            box.set(CLOUDS[i] - CLOUDS[i + 2], CLOUDS[i + 1] - CLOUDS[i + 3], CLOUDS[i] + CLOUDS[i + 2], CLOUDS[i + 1] + CLOUDS[i + 3])
            clouds.addOval(box, Path.Direction.CW)
        }
        val rng = Random(DUST_SEED)
        for (i in 0 until DUST) {
            dust[i * 3] = rng.nextFloat() * 150f - 80f
            dust[i * 3 + 1] = rng.nextFloat() * 54f - 27f
            dust[i * 3 + 2] = 0.8f + rng.nextFloat() * 1.6f
            dustColors[i] = DUST_COLORS[rng.nextInt(DUST_COLORS.size)]
        }
    }

    private fun sphere(cx: Float, cy: Float, r: Float, colors: IntArray, stops: FloatArray) =
        Paint(Paint.ANTI_ALIAS_FLAG).apply { shader = RadialGradient(cx, cy, r, colors, stops, Shader.TileMode.CLAMP) }

    private fun halo(r: Int, g: Int, b: Int) = RadialGradient(
        0f, 0f, 1f,
        intArrayOf((0x73 shl 24) or (r shl 16) or (g shl 8) or b, (0x73 shl 24) or (r shl 16) or (g shl 8) or b, (0x2E shl 24) or (r shl 16) or (g shl 8) or b, (r shl 16) or (g shl 8) or b),
        floatArrayOf(0f, 0.6f, 0.87f, 1f), Shader.TileMode.CLAMP
    )

    fun prepare() {
        if (lavaGlow != null) return
        lavaGlow = bake(0xFFFFE27A.toInt(), 1.6f) { c, p ->
            val path = Path()
            var i = 0
            while (i < LAVA.size) {
                path.rewind()
                path.moveTo(LAVA[i], LAVA[i + 1])
                path.quadTo(LAVA[i + 2], LAVA[i + 3], LAVA[i + 4], LAVA[i + 5])
                path.quadTo(LAVA[i + 6], LAVA[i + 7], LAVA[i + 8], LAVA[i + 9])
                c.drawPath(path, p)
                i += 10
            }
        }
        damageGlow = bake(0xFFFF8A2A.toInt(), 2f) { c, p ->
            val path = Path()
            var i = 0
            while (i < DAMAGE.size) {
                path.rewind()
                path.moveTo(DAMAGE[i], DAMAGE[i + 1])
                for (k in 1..3) path.lineTo(DAMAGE[i + k * 2], DAMAGE[i + k * 2 + 1])
                c.drawPath(path, p)
                i += 8
            }
        }
        lightsGlow = bake(0xFFFFE680.toInt(), 0f) { c, p ->
            p.style = Paint.Style.FILL
            var i = 0
            while (i < LIGHTS.size) {
                c.drawCircle(LIGHTS[i], LIGHTS[i + 1], 0.9f, p)
                i += 2
            }
        }
    }

    private fun bake(color: Int, width: Float, draw: (Canvas, Paint) -> Unit): Bitmap {
        val side = (BAKE_HALF * 2f * BAKE_DENSITY).toInt()
        val bmp = Bitmap.createBitmap(side, side, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.translate(side / 2f, side / 2f)
        c.scale(BAKE_DENSITY, BAKE_DENSITY)
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = width
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            this.color = color
            maskFilter = BlurMaskFilter(GLOW_SIGMA * BAKE_DENSITY * 1.73f, BlurMaskFilter.Blur.NORMAL)
        }
        draw(c, p)
        p.maskFilter = null
        draw(c, p)
        return bmp
    }

    private fun placed(c: Canvas, ex: Float, ey: Float, k: Float) {
        c.translate(ex, ey)
        c.scale(k, k)
    }

    fun drawHit(c: Canvas, ex: Float, ey: Float, er: Float, level: Float, seconds: Float) {
        if (level <= 0f) return
        c.save()
        placed(c, ex, ey, er / R)
        c.clipPath(clip)
        hitFill.alpha = (level * HIT_ALPHA * 255f).toInt()
        c.drawCircle(0f, 0f, R, hitFill)
        damageGlow?.let {
            bmpPaint.alpha = (level * 255f).toInt()
            c.drawBitmap(it, null, dst, bmpPaint)
        }
        for (i in FIRE.indices step 3) {
            val w = Ease.wave((seconds - FIRE[i + 2]) / FIRE_PERIOD)
            val sx = Ease.lerp(0.8f, 1.15f, w)
            val sy = Ease.lerp(0.8f, 1.3f, w)
            c.save()
            c.translate(FIRE[i], FIRE[i + 1] + FIRE_RY)
            c.scale(sx, sy)
            fireGlow.alpha = (level * 255f).toInt()
            c.save()
            c.translate(0f, -FIRE_RY)
            c.scale(FIRE_RX * 2.4f, FIRE_RY * 1.8f)
            c.drawCircle(0f, 0f, 1f, fireGlow)
            c.restore()
            fireCore.alpha = (level * 255f).toInt()
            box.set(-FIRE_RX, -2f * FIRE_RY, FIRE_RX, 0f)
            c.drawOval(box, fireCore)
            c.restore()
        }
        c.restore()
    }

    fun drawStage(c: Canvas, ex: Float, ey: Float, er: Float, stage: Int, age: Float, seconds: Float) {
        val grow = Ease.smooth(age / STAGE_GROW)
        val scale = when (stage) {
            0 -> Ease.lerp(0.1f, 0.6f, grow)
            1 -> Ease.lerp(0.6f, 1f, grow)
            else -> 1f
        }
        if (stage == 0) drawDust(c, ex, ey, er)
        if (stage >= 1) {
            atmo.shader = if (stage == 1) atmoOrange else atmoBlue
            c.save()
            placed(c, ex, ey, er / R)
            c.scale(ATMO_REACH, ATMO_REACH)
            c.drawCircle(0f, 0f, 1f, atmo)
            c.restore()
        }
        c.save()
        placed(c, ex, ey, er * scale / R)
        c.clipPath(clip)
        when (stage) {
            0, 1 -> drawMolten(c)
            2 -> drawCrust(c)
            else -> drawLiving(c, stage, age, seconds)
        }
        c.drawCircle(0f, 0f, R, shade)
        c.restore()
        val q = (age / RIM_MS).coerceIn(0f, 1f)
        rim.alpha = (Ease.lerp(1f, 0.6f, q) * 255f).toInt()
        rim.strokeWidth = Ease.lerp(4f, 1f, q)
        c.drawCircle(ex, ey, er * scale, rim)
    }

    private fun drawDust(c: Canvas, ex: Float, ey: Float, er: Float) {
        c.save()
        placed(c, ex, ey, er / R)
        for (i in 0 until DUST) {
            dustPaint.color = dustColors[i]
            c.drawCircle(dust[i * 3], dust[i * 3 + 1], dust[i * 3 + 2], dustPaint)
        }
        c.restore()
    }

    private fun drawMolten(c: Canvas) {
        c.drawCircle(0f, 0f, R, lava)
        lavaGlow?.let {
            bmpPaint.alpha = 255
            c.drawBitmap(it, null, dst, bmpPaint)
        }
    }

    private fun drawCrust(c: Canvas) {
        c.drawCircle(0f, 0f, R, crust)
        for (i in 0 until CRATERS.size step 3) {
            c.drawCircle(CRATERS[i], CRATERS[i + 1], CRATERS[i + 2], craterFill)
            c.drawCircle(CRATERS[i], CRATERS[i + 1], CRATERS[i + 2], craterRim)
        }
        for (i in SOFT.indices step 4) {
            c.save()
            c.translate(SOFT[i], SOFT[i + 1])
            c.scale(SOFT[i + 2] * 1.5f, SOFT[i + 3] * 2.2f)
            c.drawCircle(0f, 0f, 1f, softCloud)
            c.restore()
        }
    }

    private fun drawLiving(c: Canvas, stage: Int, age: Float, seconds: Float) {
        c.drawCircle(0f, 0f, R, ocean)
        val landShift = -80f * (seconds / 16f - floor(seconds / 16f))
        c.save()
        c.translate(landShift, 0f)
        c.drawPath(continents, brown)
        if (stage >= 4) {
            green.alpha = (if (stage == 4) (age / GREEN_MS).coerceIn(0f, 1f) else 1f).times(255f).toInt()
            c.drawPath(continents, green)
        }
        c.restore()
        if (stage >= 5) {
            lightsGlow?.let {
                bmpPaint.alpha = 255
                c.drawBitmap(it, null, dst, bmpPaint)
            }
        }
        c.save()
        c.translate(-80f * (seconds / 10f - floor(seconds / 10f)), 0f)
        c.drawPath(clouds, cloud)
        c.restore()
    }

    private companion object {
        const val R = 40f
        const val HIT_ALPHA = 0.45f
        const val STAGE_GROW = 1000f
        const val RIM_MS = 500f
        const val GREEN_MS = 600f
        const val ATMO_REACH = 60f
        const val BAKE_HALF = 50f
        const val BAKE_DENSITY = 2.5f
        const val GLOW_SIGMA = 2.5f
        const val FIRE_RX = 5f
        const val FIRE_RY = 7f
        const val FIRE_PERIOD = 0.6f
        const val DUST = 90
        const val DUST_SEED = 7
        val DUST_COLORS = intArrayOf(0xFFFFB36B.toInt(), 0xFFE8D6FF.toInt(), 0xFFFFD9A0.toInt(), 0xFFFFFFFF.toInt())
        val CONT = floatArrayOf(
            -22f, -12f, 14f, 9f, -20f, 6f, 8f, 11f, 16f, 15f, 25f, -20f, 9f, 6f, 0f, -12f, 24f, 10f, 6f, 10f,
            -36f, 14f, 6f, 9f, 0f, 30f, 22f, 7f, 5f, 30f, 58f, -12f, 14f, 9f, -20f, 86f, 8f, 11f, 16f, 15f,
            105f, -20f, 9f, 6f, 0f, 68f, 24f, 10f, 6f, 10f, 44f, 14f, 6f, 9f, 0f, 110f, 22f, 7f, 5f, 30f
        )
        val CLOUDS = floatArrayOf(
            -30f, -26f, 14f, 3f, -4f, -4f, 18f, 3f, 22f, 14f, 12f, 3f, -20f, 30f, 10f, 3f,
            50f, -26f, 14f, 3f, 76f, -4f, 18f, 3f, 102f, 14f, 12f, 3f, 60f, 30f, 10f, 3f
        )
        val SOFT = floatArrayOf(-14f, -18f, 16f, 5f, 16f, 12f, 18f, 5f)
        val CRATERS = floatArrayOf(7f, -2f, 5.7f, 2f, 7f, 3.2f, -21f, 21f, 3.9f, 11f, -24f, 3.4f, 16f, 28f, 6.5f, 12f, -26f, 4.8f)
        val LIGHTS = floatArrayOf(
            11.2f, -6.5f, 19.9f, -1.5f, 8.6f, 13f, 25.5f, -4.5f, 25.7f, 22.5f, 31.6f, 19f, 32.8f, 10.7f, 22.2f, -10.3f,
            17.2f, -4.8f, 32f, 13.3f, 8.3f, 5.7f, 14.6f, -4.6f, 24.8f, -0.9f, 28.2f, 1.9f, 31.1f, -4.4f, 9f, -9.9f,
            11.1f, 19.2f, 15f, -19.2f, 13.4f, 1f, 15.2f, 9f, 10.1f, 11.9f, 22f, -19.5f, 10.5f, -4.8f, 13.7f, 1.3f,
            23.8f, -12.1f, 16f, 6.1f
        )
        val LAVA = floatArrayOf(
            -19f, 23f, -18f, 24f, -31f, 26f, -44f, 28f, -39f, 25f,
            -6f, 25f, -12f, 30f, 7f, 29f, 26f, 28f, -4f, 39f,
            -14f, -17f, -23f, -14f, -38f, -31f, -53f, -48f, -35f, -25f,
            16f, 33f, 7f, 29f, 23f, 38f, 39f, 47f, 26f, 44f,
            34f, 27f, 46f, 33f, 32f, 19f, 18f, 5f, 28f, 3f,
            -10f, 17f, -3f, 24f, -34f, 13f, -65f, 2f, -49f, -2f,
            15f, 3f, 16f, -8f, 28f, 0f, 40f, 8f, 24f, 13f,
            33f, -17f, 26f, -28f, 20f, -39f, 14f, -50f, 16f, -27f
        )
        val DAMAGE = floatArrayOf(
            8f, 7f, 10f, -4f, 5f, -6f, -1f, -1f,
            20f, 20f, 31f, 24f, 27f, 20f, 29f, 15f,
            15f, 14f, 2f, 14f, -9f, 21f, -14f, 26f,
            -12f, -9f, -2f, -12f, -9f, -23f, -11f, -32f,
            9f, 6f, 14f, 13f, 9f, 13f, 1f, 13f,
            1f, -7f, -4f, -11f, -12f, 1f, -5f, -6f,
            -10f, 18f, 0f, 19f, -3f, 10f, -1f, 4f
        )
        val FIRE = floatArrayOf(-2f, -15f, 1.0f, 0f, 1f, 0.39f, 1f, 15f, 0.62f, -12f, 19f, 0.54f, -1f, 3f, 0.15f, 18f, -9f, 0.55f)
    }
}
