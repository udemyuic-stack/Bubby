package com.example.buddy.ui.home

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.RectF
import android.view.animation.DecelerateInterpolator

class OceanHome(ctx: Context, host: Host) : HomeScene(ctx, host) {
    private val stage = OceanStage()
    private val sand = SandShaders()
    private val weeds = PetalWeeds()
    private val glow = OceanGlow()
    private val mascot = OceanMascot()
    private val caption = OceanCaption()
    private val castLayer = OceanCastLayer(stage)
    private val location = IntArray(2)
    private val mascotSpot = Hotspot(
        RectF(
            HalloweenLayout.MASCOT_LEFT,
            HalloweenLayout.MASCOT_TOP,
            HalloweenLayout.MASCOT_LEFT + HalloweenLayout.MASCOT_WIDTH,
            HalloweenLayout.MASCOT_TOP + HalloweenLayout.MASCOT_HEIGHT
        )
    ) {}
    private var island = 0f
    private var islandAnim: ValueAnimator? = null
    private var frameWidth = 0
    private var frameHeight = 0

    override val show = OceanShow(stage)

    init {
        for (spec in HalloweenLayout.petals) {
            val petal = SandPetal(spec, sand, weeds)
            stage.petals.add(petal)
            hotspots.add(Hotspot(hitArea(petal)) { spec.onTap(host) })
        }
    }

    override fun setStatus(running: Boolean, text: String) {
        caption.running = running
        caption.statusText = text
    }

    override fun setLabels(labels: List<String>) {
        stage.petals.forEachIndexed { i, petal -> labels.getOrNull(i)?.let { petal.lines = it.split('\n') } }
    }

    override fun setIslandOpen(open: Boolean) {
        islandAnim?.cancel()
        islandAnim = ValueAnimator.ofFloat(island, if (open) 1f else 0f).apply {
            duration = ISLAND_MS
            interpolator = DecelerateInterpolator()
            addUpdateListener { island = it.animatedValue as Float }
            start()
        }
    }

    override fun mascotArea() = mascotSpot

    override fun onMascotTap() = mascot.react(HomeClock.ms())

    override fun drawScene(c: Canvas, seconds: Float) {
        val time = HomeClock.seconds()
        val now = HomeClock.ms()
        syncFrame()
        c.save()
        shift(c, DEPTH_BACKDROP)
        c.scale(scale, scale)
        glow.drawBlobs(c)
        glow.drawTide(c)
        c.restore()
        glow.drawStorm(c, width.toFloat(), height.toFloat(), stage.storm)
        castLayer.draw(c, time, scale)
        c.save()
        shift(c, DEPTH_MASCOT)
        c.scale(scale, scale)
        mascot.draw(c, time, now, stage.spinning, island)
        caption.draw(c, time, island)
        c.restore()
        show.syncStage()
        c.save()
        shift(c, DEPTH_PETAL)
        c.scale(scale, scale)
        for (i in stage.petals.indices) stage.petals[i].draw(c, time)
        glow.drawCore(c)
        c.restore()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        OceanCast.attach()
    }

    override fun onDetachedFromWindow() {
        OceanCast.detach()
        islandAnim?.cancel()
        super.onDetachedFromWindow()
    }

    private fun syncFrame() {
        val root = rootView
        if (root.width != frameWidth || root.height != frameHeight) {
            frameWidth = root.width
            frameHeight = root.height
            stage.frame.update(frameWidth.toFloat(), frameHeight.toFloat(), resources.displayMetrics.density)
        }
        getLocationInWindow(location)
        stage.top = location[1].toFloat()
    }

    private fun hitArea(petal: SandPetal): RectF {
        val dx = petal.spec.width * HalloweenLayout.HIT_INSET
        val dy = petal.spec.height * HalloweenLayout.HIT_INSET
        val a = petal.area
        return RectF(a.left + dx, a.top + dy, a.right - dx, a.bottom - dy)
    }

    private companion object {
        const val ISLAND_MS = 220L
    }
}
