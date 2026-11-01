package com.example.buddy.ui.home

import android.graphics.Canvas

class OceanCastLayer(private val stage: OceanStage) {
    private val art = CastArt()

    fun draw(c: Canvas, seconds: Float, designScale: Float) {
        val frame = stage.frame
        val crabLift = OceanMotion.easeInOut.getInterpolation(OceanMotion.pingPong(seconds, CRAB_PERIOD))
        val crabX = frame.x(CRAB_START_X + CRAB_TRAVEL * crabLift)
        val crabY = frame.y(CRAB_Y - CRAB_RISE * crabLift)
        val turtleSpan = OceanMotion.loop(seconds, TURTLE_PERIOD)
        val turtleX = frame.x(TURTLE_START_X + TURTLE_TRAVEL_X * turtleSpan)
        val turtleY = frame.y(TURTLE_START_Y + TURTLE_TRAVEL_Y * turtleSpan)
        stage.crabX = crabX / designScale
        stage.crabY = (crabY - stage.top) / designScale
        stage.turtleX = turtleX / designScale
        stage.turtleY = (turtleY - stage.top) / designScale
        c.save()
        c.translate(0f, -stage.top)
        if (!stage.crabHidden) {
            val jitter = -1f + 2f * OceanMotion.pingPong(seconds, LEG_PERIOD)
            c.save()
            c.translate(crabX, crabY)
            c.scale(frame.scale, frame.scale)
            art.drawCrab(c, jitter, SCENE_ALPHA)
            c.restore()
        }
        if (!stage.turtleHidden) {
            val flap = OceanMotion.easeInOut.getInterpolation(OceanMotion.pingPong(seconds, FLAP_PERIOD))
            c.save()
            c.translate(turtleX, turtleY)
            c.scale(frame.scale * TURTLE_SCALE, frame.scale * TURTLE_SCALE)
            art.drawTurtle(c, -FLAP_DEGREES + 2f * FLAP_DEGREES * flap, SCENE_ALPHA * stage.turtleFade)
            c.restore()
        }
        c.restore()
    }

    private companion object {
        const val SCENE_ALPHA = 0.62f
        const val CRAB_PERIOD = 9f
        const val CRAB_START_X = 170f
        const val CRAB_TRAVEL = 80f
        const val CRAB_Y = 808f
        const val CRAB_RISE = 4f
        const val LEG_PERIOD = 0.25f
        const val TURTLE_PERIOD = 40f
        const val TURTLE_START_X = -60f
        const val TURTLE_TRAVEL_X = 520f
        const val TURTLE_START_Y = 170f
        const val TURTLE_TRAVEL_Y = 60f
        const val TURTLE_SCALE = 1.1f
        const val FLAP_PERIOD = 1.6f
        const val FLAP_DEGREES = 14f
    }
}
