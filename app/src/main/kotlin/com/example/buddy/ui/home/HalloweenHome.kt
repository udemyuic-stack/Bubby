package com.example.buddy.ui.home

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.RectF
import android.view.animation.DecelerateInterpolator

class HalloweenHome(ctx: Context, host: Host) : HomeScene(ctx, host) {
    private val petals = HalloweenLayout.petals.map { HalloweenPetal(it) }
    private val mascot = HalloweenMascot()
    private val backdrop = HalloweenBackdrop()
    private val mascotSpot = Hotspot(
        RectF(
            HalloweenLayout.MASCOT_LEFT,
            HalloweenLayout.MASCOT_TOP,
            HalloweenLayout.MASCOT_LEFT + HalloweenLayout.MASCOT_WIDTH,
            HalloweenLayout.MASCOT_TOP + HalloweenLayout.MASCOT_HEIGHT
        )
    ) {}
    private var islandAnim: ValueAnimator? = null

    override val show = HalloweenShow(ctx, petals)

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

    fun startShow() = show.start()

    override fun mascotArea() = mascotSpot

    override fun onMascotTap() = mascot.bounce(HomeClock.ms())

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        petals.forEach { it.layout(scale) }
        show.resize(w.toFloat(), h.toFloat())
    }

    override fun drawScene(c: Canvas, seconds: Float) {
        val time = HomeClock.seconds()
        val now = HomeClock.ms()
        c.save()
        shift(c, DEPTH_BACKDROP)
        c.scale(scale, scale)
        backdrop.drawGlows(c)
        c.restore()
        show.drawBackdrop(c)
        c.save()
        shift(c, DEPTH_MASCOT)
        c.scale(scale, scale)
        mascot.draw(c, time, now, show.alarm())
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
    }

    override fun onDetachedFromWindow() {
        islandAnim?.cancel()
        super.onDetachedFromWindow()
    }

    private fun onPetalTap(petal: HalloweenPetal) {
        petal.pulse(HomeClock.ms())
        petal.spec.onTap(host)
    }

    private companion object {
        const val ISLAND_MS = 220L
    }
}
