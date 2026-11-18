package com.example.buddy.ui.home

import kotlin.math.max

class SceneFrame {
    var scale = 1f
        private set
    private var offsetX = 0f
    private var offsetY = 0f

    fun update(width: Float, height: Float, density: Float) {
        val boxW = width + 2f * BLEED_DP * density
        val boxH = height + 2f * BLEED_DP * density
        scale = max(boxW / VIEW_WIDTH, boxH / VIEW_HEIGHT)
        offsetX = -BLEED_DP * density + (boxW - VIEW_WIDTH * scale) / 2f
        offsetY = -BLEED_DP * density + (boxH - VIEW_HEIGHT * scale) / 2f
    }

    fun x(view: Float) = offsetX + view * scale

    fun y(view: Float) = offsetY + view * scale

    private companion object {
        const val BLEED_DP = 12f
        const val VIEW_WIDTH = 390f
        const val VIEW_HEIGHT = 844f
    }
}

class OceanStage {
    val frame = SceneFrame()
    val petals = ArrayList<SandPetal>()
    var crabHidden = false
    var turtleHidden = false
    var turtleFade = 1f
    var top = 0f
    var storm = 0f
    var spinning = false
    var crabX = 0f
    var crabY = 0f
    var turtleX = 0f
    var turtleY = 0f

    fun calm() {
        crabHidden = false
        turtleHidden = false
        turtleFade = 1f
        storm = 0f
        spinning = false
        for (i in petals.indices) petals[i].restore()
    }
}
