package com.example.buddy.ui.home

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.RectF
import android.view.animation.DecelerateInterpolator

class NewYearHome(ctx: Context, host: Host) : HomeScene(ctx, host) {
    private val petals = NewYearLayout.petals.mapIndexed { i, spec -> NewYearPetal(spec, i !in NewYearLayout.uncapped) }
    private val mascot = NewYearMascot()
    private val backdrop = NewYearBackdrop()
    private val mascotSpot = Hotspot(
        RectF(
            NewYearLayout.MASCOT_LEFT,
            NewYearLayout.MASCOT_TOP,
            NewYearLayout.MASCOT_LEFT + NewYearLayout.MASCOT_WIDTH,
            NewYearLayout.MASCOT_TOP + NewYearLayout.MASCOT_HEIGHT
        )
    ) {}
    private var islandAnim: ValueAnimator? = null

    override val show = NewYearShow(ctx)

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
        mascot.draw(c, time, now, show.mascotLift())
        c.restore()
        val knocked = show.knockPose()
        c.save()
        shift(c, DEPTH_PETAL)
        for (i in petals.indices) petals[i].draw(c, time, now, if (i == NewYearLayout.KNOCKED_PETAL) knocked else null)
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

    private fun onPetalTap(petal: NewYearPetal) {
        petal.pulse(HomeClock.ms())
        petal.spec.onTap(host)
    }

    private companion object {
        const val ISLAND_MS = 220L
    }
}
