package com.example.buddy.ui.home

import android.graphics.Canvas
import android.graphics.RectF
import kotlin.random.Random

class OceanShow(private val stage: OceanStage) : Show() {
    override val durationMs = OceanTiming.DURATION

    private val art = CastArt()
    private val tornado = OceanTornado()
    private val swirl = OceanSwirl(art)
    private val hatch = OceanHatch(art)
    private val timeline = OceanPetalTimeline()
    private val random = Random.Default
    private val areas = ArrayList<RectF>()
    private lateinit var rise: OceanSandRise
    private lateinit var crumbled: BooleanArray
    private var grainsSpawned = 0
    private var crabWhisked = false
    private var turtleWhisked = false
    private var hatchBegun = false

    fun syncStage() {
        if (!running) return
        val ms = elapsedMs
        if (ms >= durationMs) return
        spawnTornadoGrains(ms)
        spawnCrumbleGrains(ms)
        whiskCast(ms)
        if (!hatchBegun && ms >= OceanTiming.HATCH_START) {
            hatch.begin(stage.crabY - HATCH_GROUND_OFFSET)
            hatchBegun = true
        }
        returnCast(ms)
        stage.storm = stormLevel(ms)
        stage.spinning = ms < OceanTiming.STORM_END
        for (i in stage.petals.indices) timeline.apply(stage.petals[i], i, ms)
        swirl.update(ms, tornado.centerX(ms), ms < OceanTiming.TORNADO_OFF)
    }

    override fun onStart() {
        stage.calm()
        areas.clear()
        for (petal in stage.petals) {
            petal.reseedCracks(random)
            areas.add(petal.area)
        }
        rise = OceanSandRise(stage.petals.size).also { it.seed(areas, random) }
        crumbled = BooleanArray(stage.petals.size)
        grainsSpawned = 0
        crabWhisked = false
        turtleWhisked = false
        hatchBegun = false
        swirl.reset(0L)
    }

    override fun onCancel() {
        swirl.reset(0L)
        stage.calm()
    }

    override fun onDraw(c: Canvas, scale: Float, ms: Long) {
        val castScale = stage.frame.scale / scale
        c.save()
        c.scale(scale, scale)
        swirl.draw(c, false, castScale)
        tornado.draw(c, ms, tornado.strength(ms))
        swirl.draw(c, true, castScale)
        rise.draw(c, ms)
        hatch.draw(c, ms, castScale, stage.crabX, stage.crabY)
        c.restore()
    }

    private fun spawnTornadoGrains(ms: Long) {
        while (grainsSpawned < OceanTiming.GRAIN_COUNT && ms >= grainsSpawned * OceanTiming.GRAIN_STEP) {
            val x = tornado.centerX(ms) + spread(TORNADO_SPREAD)
            val y = OceanArt.TORNADO_BOTTOM - random.nextFloat() * SPAWN_DEPTH
            swirl.spawnGrain(ms, x, y, OceanArt.TORNADO_BOTTOM - random.nextFloat() * SIM_DEPTH, random)
            grainsSpawned++
        }
    }

    private fun spawnCrumbleGrains(ms: Long) {
        for (i in areas.indices) {
            if (crumbled[i] || ms < timeline.crumbleStart(i) + OceanTiming.CRUMBLE_DELAY) continue
            crumbled[i] = true
            val area = areas[i]
            for (g in 0 until CRUMBLE_GRAINS) {
                val x = area.left + (INNER_MIN + random.nextFloat() * INNER_SPAN) * area.width()
                val y = area.top + (INNER_MIN + random.nextFloat() * INNER_SPAN) * area.height()
                val simY = OceanArt.TORNADO_BOTTOM - CRUMBLE_MAX_LIFT + random.nextFloat() * CRUMBLE_LIFT_SPAN
                swirl.spawnGrain(ms, x, y, simY, random)
            }
        }
    }

    private fun whiskCast(ms: Long) {
        if (!crabWhisked && ms >= OceanTiming.CRAB_WHISK) {
            swirl.spawnSprite(ms, MoteKind.CRAB, stage.crabX, stage.crabY, OceanArt.TORNADO_BOTTOM - WHISK_LIFT, random)
            stage.crabHidden = true
            crabWhisked = true
        }
        if (!turtleWhisked && ms >= OceanTiming.TURTLE_WHISK) {
            swirl.spawnSprite(ms, MoteKind.TURTLE, stage.turtleX, stage.turtleY, OceanArt.TORNADO_BOTTOM - WHISK_LIFT, random)
            stage.turtleHidden = true
            turtleWhisked = true
        }
    }

    private fun returnCast(ms: Long) {
        if (crabWhisked) stage.crabHidden = ms < OceanTiming.CRAB_WALK_START + OceanTiming.CRAB_WALK_MS
        if (turtleWhisked) {
            val back = ms - OceanTiming.HATCH_START - OceanTiming.TURTLE_RETURN
            stage.turtleHidden = back < 0L
            stage.turtleFade = if (back < 0L) 0f else OceanMotion.progress(back, 0L, OceanTiming.TURTLE_FADE_MS)
        }
    }

    private fun stormLevel(ms: Long) =
        OceanMotion.progress(ms, 0L, OceanTiming.STORM_FADE) - OceanMotion.progress(ms, OceanTiming.STORM_END, OceanTiming.STORM_FADE)

    private fun spread(range: Float) = random.nextFloat() * 2f * range - range

    private companion object {
        const val HATCH_GROUND_OFFSET = 15f
        const val TORNADO_SPREAD = 40f
        const val SPAWN_DEPTH = 40f
        const val SIM_DEPTH = 60f
        const val CRUMBLE_GRAINS = 34
        const val INNER_MIN = 0.15f
        const val INNER_SPAN = 0.7f
        const val CRUMBLE_MAX_LIFT = 160f
        const val CRUMBLE_LIFT_SPAN = 140f
        const val WHISK_LIFT = 120f
    }
}
