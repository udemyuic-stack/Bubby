package com.example.buddy.ui.home

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.RectF
import android.view.animation.DecelerateInterpolator
import com.example.buddy.ui.ThemeDef

abstract class PlainHome(ctx: Context, host: Host, theme: ThemeDef) : HomeScene(ctx, host) {
    private val petals = HalloweenLayout.petals.map { PlainPetal(it, theme) }
    private val mascot = PlainMascot(theme)
    private val backdrop = PlainBackdrop(theme)
    private val effects = PlainEffects(theme)
    private val mascotSpot = Hotspot(
        RectF(
            HalloweenLayout.MASCOT_LEFT,
            HalloweenLayout.MASCOT_TOP,
            HalloweenLayout.MASCOT_LEFT + HalloweenLayout.MASCOT_WIDTH,
            HalloweenLayout.MASCOT_TOP + HalloweenLayout.MASCOT_HEIGHT
        )
    ) {}
    private var islandAnim: ValueAnimator? = null

    override val show = IdleShow()

    init {
        petals.forEach { petal -> hotspots.add(Hotspot(petal.hitArea()) { onPetalTap(petal) }) }
    }

    override fun setStatus(running: Boolean, text: String) {
        mascot.running = running
        mascot.statusText = text
    }

    override fun setLabels(labels: List<String>) {
        petals.forEachIndexed { i, petal -> labels.getOrNull(i)?.let { petal.label = it } }
    }

    override fun setIslandOpen(open: Boolean) {
        islandAnim?.cancel()
        islandAnim = ValueAnimator.ofFloat(mascot.island, if (open) 1f else 0f).apply {
            duration = ISLAND_MS
            interpolator = DecelerateInterpolator()
            addUpdateListener { mascot.island = it.animatedValue as Float }
            start()
        }
    }

    override fun mascotArea() = mascotSpot

    override fun onMascotTap() {
        mascot.bounce(HomeClock.ms())
        effects.burst(MASCOT_CENTER_X, MASCOT_CENTER_Y, HomeClock.ms())
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        petals.forEach { it.layout(scale) }
        backdrop.layout(scale)
    }

    override fun drawScene(c: Canvas, seconds: Float) {
        val time = HomeClock.seconds()
        val now = HomeClock.ms()
        c.save()
        shift(c, DEPTH_BACKDROP)
        c.scale(scale, scale)
        backdrop.drawGlows(c)
        c.restore()
        c.save()
        shift(c, DEPTH_MASCOT)
        c.scale(scale, scale)
        mascot.draw(c, time, now)
        c.restore()
        c.save()
        shift(c, DEPTH_PETAL)
        for (i in petals.indices) petals[i].draw(c, time, now)
        c.restore()
        c.save()
        shift(c, DEPTH_BACKDROP)
        c.scale(scale, scale)
        backdrop.drawFlower(c)
        c.restore()
        c.save()
        c.scale(scale, scale)
        effects.draw(c, now)
        c.restore()
    }

    override fun onDetachedFromWindow() {
        islandAnim?.cancel()
        super.onDetachedFromWindow()
    }

    private fun onPetalTap(petal: PlainPetal) {
        petal.pulse(HomeClock.ms())
        val x = petal.spec.left + petal.spec.width / 2f
        val y = petal.spec.top + petal.spec.height / 2f
        val now = HomeClock.ms()
        effects.ripple(x, y, now)
        if (petal.spec.icon == PetalIcon.PLAY) effects.burst(x, y, now)
        if (petal.spec.icon == PetalIcon.HAND) onMascotTap()
        petal.spec.onTap(host)
    }

    private companion object {
        const val ISLAND_MS = 220L
        const val MASCOT_CENTER_X = HalloweenLayout.MASCOT_LEFT + HalloweenLayout.MASCOT_WIDTH / 2f
        const val MASCOT_CENTER_Y = HalloweenLayout.MASCOT_TOP + HalloweenLayout.MASCOT_HEIGHT / 2f
    }
}
