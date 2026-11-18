package com.example.buddy.ui.home

class OceanPetalTimeline {
    private val weedBloom = Track(floatArrayOf(0f, 0.7f, 1f), floatArrayOf(0f, 1.15f, 1f))

    fun crumbleStart(index: Int) = OceanTiming.CRUMBLE_START + index * OceanTiming.CRUMBLE_STEP

    fun apply(petal: SandPetal, index: Int, ms: Long) {
        val crumble = crumbleStart(index)
        val rebuild = OceanTiming.REBUILD_START + index * OceanTiming.REBUILD_STEP + OceanTiming.REVEAL_DELAY
        if (ms >= rebuild) applyRebuild(petal, ms - rebuild) else applyCrumble(petal, ms, crumble)
    }

    private fun applyCrumble(petal: SandPetal, ms: Long, crumble: Long) {
        petal.restore()
        petal.crack = OceanMotion.progress(ms, crumble, OceanTiming.CRACK_FADE)
        val fall = OceanMotion.progress(ms, crumble + OceanTiming.CRUMBLE_DELAY, OceanTiming.CRUMBLE_FADE)
        val eased = OceanMotion.easeIn.getInterpolation(fall)
        petal.alpha = 1f - eased
        petal.scale = OceanMotion.lerp(1f, FALL_SCALE, eased)
    }

    private fun applyRebuild(petal: SandPetal, sinceRebuild: Long) {
        petal.restore()
        val grow = OceanMotion.rebuild.getInterpolation(OceanMotion.progress(sinceRebuild, 0L, OceanTiming.REVEAL_MS))
        petal.reveal = grow
        petal.alpha = OceanMotion.lerp(REBUILD_ALPHA, 1f, grow)
        val bloom = OceanMotion.progress(sinceRebuild, OceanTiming.WEED_DELAY, OceanTiming.WEED_MS)
        petal.weed = weedBloom.at(OceanMotion.easeOut.getInterpolation(bloom))
    }

    private companion object {
        const val FALL_SCALE = 0.82f
        const val REBUILD_ALPHA = 0.4f
    }
}
