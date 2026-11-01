package com.example.buddy.ui.home

import android.content.Context
import android.graphics.Canvas

class SpaceShow(private val ctx: Context, private val petals: List<SpacePetal>, private val mascot: SpaceMascot) : Show() {
    private val fx = SpaceFx()
    private val meteors = SpaceMeteors(petals, fx)
    private val earthFx = SpaceEarthFx()
    private val rewind = SpaceRewind()
    private var startAbs = 0L
    private var built = false
    private var hitAt = NONE
    private var boomed = false
    private var rewound = false
    private var healed = false
    private var seconds = 0f
    private var lastMs = 0L
    var tiltX = 0f
    var tiltY = 0f

    override val durationMs get() = DURATION

    fun resize(widthPx: Float, heightPx: Float, scale: Float) {
        if (scale > 0f) rewind.resize(widthPx / scale, heightPx / scale)
    }

    override fun onStart() {
        fx.clear()
        petals.forEach { it.craters.clear() }
        rewind.load(ctx)
        startAbs = HomeClock.ms()
        built = false
        hitAt = NONE
        boomed = false
        rewound = false
        healed = false
    }

    override fun onCancel() {
        SpaceEarth.hidden = false
        SpaceEarth.shake = 0f
        SpaceEarth.shakeY = 0f
        mascot.sad = false
        petals.forEach { it.craters.clear() }
        fx.clear()
        meteors.stop()
    }

    override fun onDraw(c: Canvas, scale: Float, ms: Long) {
        seconds = HomeClock.seconds()
        lastMs = ms
        val ex = (SpaceEarth.x + SceneOrigin.dx) / scale
        val ey = (SpaceEarth.y + SceneOrigin.dy) / scale
        val er = SpaceEarth.r / scale
        mascot.sad = ms in SAD_AT until END
        c.save()
        c.scale(scale, scale)
        if (!built) {
            earthFx.prepare()
            meteors.build(er)
            built = true
        }
        val level = minOf((ms - REWIND_AT) / FADE, (END - ms) / FADE).coerceIn(0f, 1f)
        rewind.drawTint(c, level, ms)
        drawEarth(c, ms, ex, ey, er)
        if (ms >= REWIND_AT + HEAL_DELAY && !healed) {
            healed = true
            petals.forEach { it.craters.heal(startAbs + ms) }
        }
        meteors.draw(c, ms, startAbs, ex, ey, true, ::onEarthHit)
        fx.draw(c, ms, SpaceFx.EARTH)
        c.save()
        c.translate(tiltX * HomeScene.DEPTH_PETAL, tiltY * HomeScene.DEPTH_PETAL)
        meteors.draw(c, ms, startAbs, ex, ey, false, ::onEarthHit)
        fx.draw(c, ms, SpaceFx.PETAL)
        c.restore()
        if (ms >= REWIND_AT) rewind.drawPill(c, ex, ey, er, stageAt(ms - REWIND_AT), level)
        c.restore()
    }

    private fun onEarthHit(x: Float, y: Float) {
        if (hitAt == NONE) hitAt = lastMs
    }

    private fun drawEarth(c: Canvas, ms: Long, ex: Float, ey: Float, er: Float) {
        if (ms < BIG_BOOM) {
            val hit = if (hitAt == NONE) 0f else ((ms - hitAt) / HIT_FADE).coerceIn(0f, 1f)
            val phase = (seconds / SHAKE_PERIOD).toInt() and 1
            SpaceEarth.shake = (if (phase == 0) SHAKE_AMP else -SHAKE_AMP) * hit
            SpaceEarth.shakeY = (if (phase == 0) -SHAKE_AMP else SHAKE_AMP) * hit
            earthFx.drawHit(c, ex + SpaceEarth.shake, ey + SpaceEarth.shakeY, er, hit, seconds)
            return
        }
        if (!boomed) {
            boomed = true
            SpaceEarth.hidden = true
            SpaceEarth.shake = 0f
            SpaceEarth.shakeY = 0f
            fx.boom(SpaceFx.EARTH, ms, ex, ey, er * 2.1f)
            fx.debris(SpaceFx.EARTH, ms, ex, ey, 26, er * 2f)
        }
        if (ms < REWIND_AT) return
        if (!rewound) {
            rewound = true
            fx.implode(SpaceFx.EARTH, ms, ex, ey, er, 36, IMPLODE_SPREAD)
        }
        val t = ms - REWIND_AT
        val stage = stageAt(t)
        if (stage >= LAST_STAGE) {
            SpaceEarth.hidden = false
            return
        }
        earthFx.drawStage(c, ex, ey, er, stage, (t - STAGE_AT[stage]).toFloat(), seconds)
    }

    private fun stageAt(t: Long): Int {
        var s = 0
        for (i in STAGE_AT.indices) if (t >= STAGE_AT[i]) s = i
        return s
    }

    private companion object {
        const val NONE = -1L
        const val SAD_AT = 1500L
        const val BIG_BOOM = 2300L
        const val REWIND_AT = 3000L
        const val END = 8200L
        const val DURATION = 8300L
        const val HEAL_DELAY = 300L
        const val FADE = 400f
        const val HIT_FADE = 300f
        const val SHAKE_PERIOD = 0.12f
        const val SHAKE_AMP = 1.5f
        const val IMPLODE_SPREAD = 1500f
        const val LAST_STAGE = 5
        val STAGE_AT = longArrayOf(0, 1000, 2000, 2900, 3800, 4600)
    }
}
