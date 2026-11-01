package com.example.buddy.ui.home

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.RectF
import android.view.animation.DecelerateInterpolator

open class IceHome(
    ctx: Context,
    host: Host,
    private val mascot: IceMascot = IceMascot(),
    final override val show: Show = IceShow(),
    private val decor: Boolean = true
) : HomeScene(ctx, host) {
    private val petals = HalloweenLayout.petals.mapIndexed { i, spec -> IcePetal(spec, i) }
    private val backdrop = IceBackdrop()
    private val mascotSpot = Hotspot(
        RectF(
            HalloweenLayout.MASCOT_LEFT,
            HalloweenLayout.MASCOT_TOP,
            HalloweenLayout.MASCOT_LEFT + HalloweenLayout.MASCOT_WIDTH,
            HalloweenLayout.MASCOT_TOP + HalloweenLayout.MASCOT_HEIGHT
        )
    ) {}
    private var islandAnim: ValueAnimator? = null

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

    override fun onMascotTap() = mascot.bounce(HomeClock.ms())

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        petals.forEach { it.layout(scale) }
        backdrop.layout(scale)
    }

    override fun drawScene(c: Canvas, seconds: Float) {
        val time = HomeClock.seconds()
        val now = HomeClock.ms()
        if (decor) {
            c.save()
            shift(c, DEPTH_BACKDROP)
            c.scale(scale, scale)
            backdrop.drawGlows(c)
            c.restore()
        }
        c.save()
        shift(c, DEPTH_MASCOT)
        c.scale(scale, scale)
        mascot.draw(c, time, now)
        c.restore()
        c.save()
        shift(c, DEPTH_PETAL)
        if (decor) {
            c.save()
            c.scale(scale, scale)
            backdrop.drawStem(c)
            c.restore()
        }
        for (i in petals.indices) petals[i].draw(c, time, now)
        c.restore()
        if (decor) {
            c.save()
            shift(c, DEPTH_BACKDROP)
            c.scale(scale, scale)
            backdrop.drawFlower(c)
            c.restore()
        }
    }

    override fun onDetachedFromWindow() {
        islandAnim?.cancel()
        super.onDetachedFromWindow()
    }

    private fun onPetalTap(petal: IcePetal) {
        petal.pulse(HomeClock.ms())
        petal.spec.onTap(host)
    }

    private companion object {
        const val ISLAND_MS = 220L
    }
}
