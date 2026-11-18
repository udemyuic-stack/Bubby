package com.example.buddy.ui.home

import android.graphics.Canvas

object ForestTrees {
    const val COUNT = 6
    const val STRIDE = 5
    const val BASE_ALPHA = 255

    val front = floatArrayOf(
        22f, 820f, 1.05f, 0f, 4.5f,
        92f, 838f, .9f, -.8f, 5.2f,
        165f, 826f, 1.15f, -1.6f, 5.9f,
        240f, 840f, .95f, -2.4f, 6.6f,
        312f, 824f, 1.1f, -3.2f, 4.5f,
        378f, 836f, .95f, -4f, 5.2f
    )
    val top = floatArrayOf(662f, 703f, 654f, 698f, 659f, 694f)

    val burn = FloatArray(COUNT)
    val scaleX = FloatArray(COUNT) { 1f }
    val scaleY = FloatArray(COUNT) { 1f }
    val opacity = FloatArray(COUNT) { 1f }

    @Volatile
    var flash = 0f

    private var density = 1f
    private var unit = 1f
    private var originX = 0f
    private var originY = 0f

    fun setView(density: Float, unit: Float, originX: Float, originY: Float) {
        this.density = density
        this.unit = unit
        this.originX = originX
        this.originY = originY
    }

    fun place(c: Canvas) {
        c.translate(SceneOrigin.dx, SceneOrigin.dy)
        c.translate(originX * density, originY * density)
        c.scale(unit, unit)
    }

    fun x(i: Int) = front[i * STRIDE]

    fun baseY(i: Int) = front[i * STRIDE + 1]

    fun size(i: Int) = front[i * STRIDE + 2]

    fun reset() {
        for (i in 0 until COUNT) {
            burn[i] = 0f
            scaleX[i] = 1f
            scaleY[i] = 1f
            opacity[i] = 1f
        }
    }

    fun changed(i: Int) = burn[i] != 0f || scaleX[i] != 1f || scaleY[i] != 1f || opacity[i] != 1f
}
