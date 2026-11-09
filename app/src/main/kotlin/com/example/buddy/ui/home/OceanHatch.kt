package com.example.buddy.ui.home

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF

class OceanHatch(private val art: CastArt) {
    private var groundY = 0f
    private val shadow = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(115, 184, 146, 90) }
    private val shell = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = SHELL }
    private val shellEdge = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = EDGE_WIDTH
        color = EDGE
    }
    private val spots = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = SPOT }
    private val crackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = CRACK_WIDTH
        color = CRACK
    }
    private val oval = RectF()
    private val top = Path().apply {
        moveTo(-16f, -2f); cubicTo(-16f, -16f, -8f, -21f, 0f, -21f); cubicTo(8f, -21f, 16f, -16f, 16f, -2f)
        lineTo(11f, -6f); lineTo(6f, -1f); lineTo(1f, -7f); lineTo(-4f, -2f); lineTo(-9f, -6f); close()
    }
    private val crack = Path().apply {
        moveTo(-15f, -2f); rLineTo(5f, -4f); rLineTo(4f, 5f); rLineTo(5f, -6f); rLineTo(5f, 5f); rLineTo(5f, -4f)
    }
    private val wobble = Track(floatArrayOf(0f, 1f / 3f, 2f / 3f, 1f), floatArrayOf(0f, -12f, 12f, 0f))
    private val lidX = Track(floatArrayOf(0f, 0.6f, 1f), floatArrayOf(0f, 14f, 22f))
    private val lidY = Track(floatArrayOf(0f, 0.6f, 1f), floatArrayOf(0f, -38f, -10f))
    private val lidTurn = Track(floatArrayOf(0f, 0.6f, 1f), floatArrayOf(0f, 70f, 140f))
    private val lidAlpha = Track(floatArrayOf(0f, 0.6f, 1f), floatArrayOf(1f, 1f, 0f))
    private val babyOffsets = floatArrayOf(0f, 0.3f, 0.45f, 0.85f, 1f)
    private val babyX = Track(babyOffsets, floatArrayOf(0f, 0f, 0f, -60f, -110f))
    private val babyY = Track(babyOffsets, floatArrayOf(16f, -6f, -14f, -220f, -300f))
    private val babyScale = Track(babyOffsets, floatArrayOf(0.2f, 0.55f, 0.6f, 1f, 1.1f))
    private val babyAlpha = Track(babyOffsets, floatArrayOf(0f, 1f, 1f, 1f, 0f))

    fun begin(sandY: Float) {
        groundY = minOf(MAX_GROUND, sandY)
    }

    fun draw(c: Canvas, ms: Long, castScale: Float, crabX: Float, crabY: Float) {
        val hatch = ms - OceanTiming.HATCH_START
        if (hatch in 0..EGG_END) drawEgg(c, hatch)
        val baby = hatch - LID_START
        if (baby in 0..BABY_MS) drawBaby(c, baby, ms)
        val walk = ms - OceanTiming.CRAB_WALK_START
        if (walk in 0..OceanTiming.CRAB_WALK_MS) drawWalker(c, walk, ms, castScale, crabX, crabY)
    }

    private fun drawEgg(c: Canvas, hatch: Long) {
        val appear = OceanMotion.spring.getInterpolation(OceanMotion.progress(hatch, 0L, APPEAR_MS))
        val fade = 1f - OceanMotion.progress(hatch, FADE_START, FADE_MS)
        val alpha = OceanMotion.clamp(appear) * fade
        c.save()
        c.translate(EGG_X, groundY - EGG_LIFT + OceanMotion.lerp(RISE_FROM, 0f, appear))
        c.scale(OceanMotion.lerp(SCALE_FROM, 1f, appear), OceanMotion.lerp(SCALE_FROM, 1f, appear))
        c.rotate(rocking(hatch))
        val layer = c.saveLayerAlpha(-LAYER_X, -LAYER_UP, LAYER_X, LAYER_DOWN, (alpha * OPAQUE).toInt())
        oval.set(-16f, 18f, 16f, 26f)
        c.drawOval(oval, shadow)
        oval.set(-16f, -21f, 16f, 21f)
        c.drawOval(oval, shell)
        c.drawOval(oval, shellEdge)
        c.drawCircle(-6f, 6f, 2.4f, spots)
        c.drawCircle(6f, 12f, 1.8f, spots)
        c.drawCircle(4f, -4f, 1.5f, spots)
        crackPaint.alpha = (OceanMotion.progress(hatch, CRACK_START, CRACK_FADE_MS) * OPAQUE).toInt()
        c.drawPath(crack, crackPaint)
        drawLid(c, hatch)
        c.restoreToCount(layer)
        c.restore()
    }

    private fun drawLid(c: Canvas, hatch: Long) {
        val p = OceanMotion.easeOut.getInterpolation(OceanMotion.progress(hatch, LID_START, LID_MS))
        c.save()
        c.translate(lidX.at(p), lidY.at(p))
        c.rotate(lidTurn.at(p), 0f, LID_PIVOT_Y)
        shell.alpha = (lidAlpha.at(p) * OPAQUE).toInt()
        shellEdge.alpha = shell.alpha
        c.drawPath(top, shell)
        c.drawPath(top, shellEdge)
        shell.alpha = OPAQUE.toInt()
        shellEdge.alpha = OPAQUE.toInt()
        c.restore()
    }

    private fun rocking(hatch: Long): Float {
        var turn = 0f
        for (q in 0 until ROCKS) turn += wobble.at(OceanMotion.progress(hatch, ROCK_START + q * ROCK_STEP, ROCK_MS))
        return turn
    }

    private fun drawBaby(c: Canvas, baby: Long, ms: Long) {
        val p = OceanMotion.easeInOut.getInterpolation(baby.toFloat() / BABY_MS)
        val size = babyScale.at(p) * BABY_FIT * TURTLE_INNER
        c.save()
        c.translate(EGG_X + babyX.at(p), groundY - BABY_LIFT + babyY.at(p))
        c.scale(size, size)
        val flap = OceanMotion.easeInOut.getInterpolation(OceanMotion.pingPong(ms / 1000f, FLAP_PERIOD))
        art.drawTurtle(c, -FLAP_DEGREES + 2f * FLAP_DEGREES * flap, babyAlpha.at(p))
        c.restore()
    }

    private fun drawWalker(c: Canvas, walk: Long, ms: Long, castScale: Float, crabX: Float, crabY: Float) {
        val p = OceanMotion.walk.getInterpolation(walk.toFloat() / OceanTiming.CRAB_WALK_MS)
        val jitter = -1f + 2f * OceanMotion.pingPong(ms / 1000f, LEG_PERIOD)
        c.save()
        c.translate(OceanMotion.lerp(WALK_FROM, crabX, p), crabY)
        c.scale(castScale, castScale)
        art.drawCrab(c, jitter, WALKER_ALPHA)
        c.restore()
    }

    private companion object {
        const val EGG_X = 300f
        const val MAX_GROUND = 820f
        const val EGG_LIFT = 22f
        const val BABY_LIFT = 31.45f
        const val APPEAR_MS = 500L
        const val RISE_FROM = 30f
        const val SCALE_FROM = 0.4f
        const val ROCKS = 3
        const val ROCK_START = 600L
        const val ROCK_STEP = 420L
        const val ROCK_MS = 320L
        const val CRACK_START = 1500L
        const val CRACK_FADE_MS = 150L
        const val FADE_START = 1800L
        const val FADE_MS = 600L
        const val LID_START = 1900L
        const val LID_MS = 900L
        const val LID_PIVOT_Y = -11f
        const val EGG_END = FADE_START + FADE_MS
        const val BABY_MS = 3200L
        const val BABY_FIT = 0.55f
        const val TURTLE_INNER = 1.1f
        const val FLAP_PERIOD = 1.6f
        const val FLAP_DEGREES = 14f
        const val LEG_PERIOD = 0.25f
        const val WALK_FROM = -40f
        const val WALKER_ALPHA = 0.62f
        const val LAYER_X = 80f
        const val LAYER_UP = 90f
        const val LAYER_DOWN = 40f
        const val EDGE_WIDTH = 1.2f
        const val CRACK_WIDTH = 1.4f
        const val OPAQUE = 255f
        val SHELL = Color.rgb(251, 243, 223)
        val EDGE = Color.rgb(227, 210, 168)
        val SPOT = Color.rgb(232, 220, 188)
        val CRACK = Color.rgb(138, 106, 58)
    }
}
