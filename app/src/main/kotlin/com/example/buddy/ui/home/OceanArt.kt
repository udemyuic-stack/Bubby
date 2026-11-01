package com.example.buddy.ui.home

import android.graphics.Color

class GlowBlob(
    val left: Float,
    val top: Float,
    val w: Float,
    val h: Float,
    val rotation: Float,
    val colors: IntArray,
    val stops: FloatArray,
)

object OceanArt {
    const val NAME_TOP = 233f
    const val PILL_TOP = 272f
    const val TORNADO_TOP = 290f
    const val TORNADO_BOTTOM = 830f
    const val TORNADO_CENTER_X = 195f
    const val TORNADO_SWAY = 120f
    const val TORNADO_WIDTH = 240f
    const val TORNADO_HEIGHT = 540f

    val blobs = listOf(
        GlowBlob(
            20f, -120f, 430f, 470f, 0f,
            intArrayOf(Color.argb(128, 40, 130, 180), Color.argb(102, 80, 170, 210), Color.argb(0, 120, 200, 230)),
            floatArrayOf(0f, 0.55f, 1f),
        ),
        GlowBlob(
            260f, -30f, 200f, 120f, 0f,
            intArrayOf(Color.argb(140, 120, 230, 220), Color.argb(0, 120, 230, 220)),
            floatArrayOf(0f, 1f),
        ),
        GlowBlob(
            -80f, 330f, 260f, 300f, 0f,
            intArrayOf(Color.argb(179, 255, 255, 255), Color.argb(0, 255, 255, 255)),
            floatArrayOf(0f, 1f),
        ),
        GlowBlob(
            -40f, 560f, 480f, 200f, -8f,
            intArrayOf(Color.argb(89, 255, 255, 255), Color.argb(0, 255, 255, 255)),
            floatArrayOf(0f, 1f),
        ),
    )

    val weedStems = arrayOf(
        floatArrayOf(12f, 40f, 8f, 30f, 17f, 22f, 12f, 13f, 10f, 8f, 14f, 4f, 15f, 6f, 2.6f, 0f),
        floatArrayOf(22f, 40f, 18f, 30f, 27f, 22f, 22f, 13f, 20f, 8f, 24f, 4f, 25f, 0f, 3.2f, -0.5f),
        floatArrayOf(34f, 40f, 30f, 30f, 39f, 22f, 34f, 13f, 32f, 8f, 36f, 4f, 37f, 4f, 2.8f, -1f),
        floatArrayOf(46f, 40f, 42f, 30f, 51f, 22f, 46f, 13f, 44f, 8f, 48f, 4f, 49f, 9f, 2.4f, -1.5f),
    )

    val weedLeaves = arrayOf(
        floatArrayOf(18f, 24f, 4f, 1.6f, -30f),
        floatArrayOf(38f, 20f, 4f, 1.6f, 30f),
        floatArrayOf(27f, 12f, 3.4f, 1.4f, -20f),
    )

    val ringRadiusX = floatArrayOf(108f, 99f, 91f, 82f, 74f, 65f, 56f, 48f, 39f, 31f, 22f, 13f, 6f)
    val ringRadiusY = floatArrayOf(16f, 15f, 14f, 13f, 12f, 10f, 9f, 8f, 7f, 6f, 5f, 4f, 3f)
    val ringWidth = floatArrayOf(2.6f, 2.5f, 2.4f, 2.2f, 2.1f, 2f, 1.9f, 1.8f, 1.6f, 1.5f, 1.4f, 1.3f, 1.2f)
}
