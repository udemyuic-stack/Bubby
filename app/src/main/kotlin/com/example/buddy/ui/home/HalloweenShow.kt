package com.example.buddy.ui.home

import android.content.Context
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.provider.Settings
import kotlin.math.PI
import kotlin.math.cos
import kotlin.random.Random

class HalloweenShow(private val ctx: Context, private val petals: List<HalloweenPetal>) : Show() {
    private val rng = Random.Default
    private val painter = GhostPainter()
    private val flights = ArrayList<GhostFlight>()
    private val tintMatrix = Matrix()
    private val tintPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader = RadialGradient(0f, 0f, 1f, intArrayOf(TINT_CORE, TINT_EDGE), floatArrayOf(0f, 1f), Shader.TileMode.CLAMP)
    }
    private var total = 0L
    private var huntEnd = 0L
    private var width = 0f
    private var height = 0f

    override val durationMs get() = total

    fun resize(w: Float, h: Float) {
        width = w
        height = h
        tintMatrix.setScale(w * TINT_RX, h * TINT_RY)
        tintMatrix.postTranslate(w * TINT_X, h * TINT_Y)
        tintPaint.shader.setLocalMatrix(tintMatrix)
    }

    fun alarm(): Float {
        if (!running) return 0f
        val ms = elapsedMs
        val fadeIn = (ms / TINT_FADE_MS.toFloat()).coerceIn(0f, 1f)
        val fadeOut = ((huntEnd + TINT_FADE_MS - ms) / TINT_FADE_MS.toFloat()).coerceIn(0f, 1f)
        return minOf(fadeIn, fadeOut)
    }

    fun drawBackdrop(c: Canvas) {
        val level = alarm()
        if (level <= 0f) return
        val pulse = (1f - cos(2f * PI.toFloat() * elapsedMs / TINT_PULSE_MS)) / 2f
        tintPaint.alpha = (level * (TINT_BASE + TINT_SWING * pulse) * MAX_ALPHA).toInt().coerceIn(0, MAX_ALPHA.toInt())
        c.drawRect(0f, 0f, width, height, tintPaint)
    }

    override fun onStart() {
        val reduced = Settings.Global.getFloat(ctx.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
        val approach = if (reduced) REDUCED_APPROACH_MS else APPROACH_MS
        val bite = if (reduced) REDUCED_BITE_MS else BITE_MS
        val startMs = HomeClock.ms()
        var lastEnd = 0L
        flights.clear()
        petals.forEachIndexed { i, petal ->
            val delay = i * STAGGER_MS
            val end = delay + approach + bite * BitePlanner.BITES
            lastEnd = maxOf(lastEnd, end)
            planPetal(petal, startMs, delay, approach, bite)
        }
        huntEnd = lastEnd + HEAL_FULL_AFTER_MS + PetalBites.HEAL_MS
        total = lastEnd + HEAL_FULL_AFTER_MS + PetalBites.HEAL_MS + PetalBites.FLASH_MS
    }

    override fun onCancel() {
        petals.forEach { it.bites = null }
        flights.clear()
    }

    override fun onDraw(c: Canvas, scale: Float, ms: Long) {
        val seconds = ms / MS_PER_SECOND
        for (i in flights.indices) {
            val flight = flights[i]
            flight.sample(ms)
            if (flight.alpha > MIN_VISIBLE) painter.draw(c, flight, seconds, scale)
        }
    }

    private fun planPetal(petal: HalloweenPetal, startMs: Long, delay: Long, approach: Long, bite: Long) {
        val unitPx = petal.widthPx / petal.spec.width
        val sites = BitePlanner.sites(rng, petal.widthPx, petal.heightPx)
        val bites = List(BitePlanner.BITES) {
            val born = startMs + delay + approach + bite * it + (bite * BITE_BEAT).toLong()
            BitePlanner.bite(rng, sites[2 * it], sites[2 * it + 1], petal.widthPx, petal.heightPx, unitPx, born)
        }
        val end = startMs + delay + approach + bite * BitePlanner.BITES
        petal.bites = PetalBites(bites, unitPx, end + HEAL_HALF_AFTER_MS, end + HEAL_FULL_AFTER_MS)
        flights.add(GhostFlight.plan(rng, sites, petal.centerX, petal.centerY, petal.spec.rotation, unitPx, delay, approach, bite))
    }

    private companion object {
        const val STAGGER_MS = 130L
        const val APPROACH_MS = 650L
        const val BITE_MS = 460L
        const val REDUCED_APPROACH_MS = 50L
        const val REDUCED_BITE_MS = 60L
        const val BITE_BEAT = 0.65f
        const val HEAL_HALF_AFTER_MS = 2300L
        const val HEAL_FULL_AFTER_MS = 5300L
        const val TINT_FADE_MS = 500L
        const val TINT_PULSE_MS = 1800f
        const val TINT_BASE = 0.8f
        const val TINT_SWING = 0.2f
        const val TINT_CORE = 0x66FF2828
        const val TINT_EDGE = 0xA0780014.toInt()
        const val TINT_X = 0.5f
        const val TINT_Y = 0.4f
        const val TINT_RX = 0.7f
        const val TINT_RY = 0.5f
        const val MIN_VISIBLE = 0.01f
        const val MS_PER_SECOND = 1000f
        const val MAX_ALPHA = 255f
    }
}
