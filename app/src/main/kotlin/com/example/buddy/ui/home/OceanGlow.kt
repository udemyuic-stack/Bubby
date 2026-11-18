package com.example.buddy.ui.home

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Shader

class OceanGlow {
    private val blobPaints = Array(OceanArt.blobs.size) { index ->
        val blob = OceanArt.blobs[index]
        Paint(Paint.ANTI_ALIAS_FLAG).apply { shader = RadialGradient(0f, 0f, 1f, blob.colors, blob.stops, Shader.TileMode.CLAMP) }
    }
    private val tideLine = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = LINE_WIDTH
        color = Color.argb(115, 255, 255, 255)
    }
    private val tideShade = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = SHADE_WIDTH
        strokeCap = Paint.Cap.ROUND
        color = Color.argb(89, 120, 140, 170)
    }
    private val tideLinePath = Path().apply {
        moveTo(60f, 70f); cubicTo(100f, 120f, 160f, 112f, 193f, 90f); cubicTo(230f, 105f, 290f, 100f, 330f, 60f)
    }
    private val tideShadePath = Path().apply {
        moveTo(60f, 70f); cubicTo(100f, 125f, 160f, 118f, 193f, 96f)
    }
    private val core = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader = RadialGradient(
            0f, 0f, 1f,
            intArrayOf(Color.WHITE, Color.WHITE, Color.argb(179, 255, 255, 255), Color.argb(0, 255, 255, 255)),
            floatArrayOf(0f, 0.25f / 0.72f, 0.5f / 0.72f, 1f),
            Shader.TileMode.CLAMP,
        )
    }
    private val spark = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader = RadialGradient(
            0f, 0f, 1f,
            intArrayOf(Color.WHITE, Color.WHITE, Color.argb(0, 255, 255, 255)),
            floatArrayOf(0f, SPARK_SOLID, 1f),
            Shader.TileMode.CLAMP,
        )
    }

    private val storm = Paint().apply {
        shader = RadialGradient(
            0f, 0f, 1f,
            intArrayOf(Color.argb(46, 200, 170, 110), Color.argb(115, 60, 90, 110)),
            floatArrayOf(0f, 1f),
            Shader.TileMode.CLAMP,
        )
    }

    fun drawStorm(c: Canvas, width: Float, height: Float, level: Float) {
        if (level <= 0f) return
        storm.alpha = (level * OPAQUE).toInt()
        c.save()
        c.translate(width * STORM_X, height * STORM_Y)
        c.scale(width * STORM_RX, height * STORM_RY)
        c.drawRect(-STORM_X / STORM_RX, -STORM_Y / STORM_RY, (1f - STORM_X) / STORM_RX, (1f - STORM_Y) / STORM_RY, storm)
        c.restore()
    }

    fun drawBlobs(c: Canvas) {
        for (i in blobPaints.indices) {
            val blob = OceanArt.blobs[i]
            c.save()
            c.translate(blob.left + blob.w / 2f, blob.top + blob.h / 2f)
            c.rotate(blob.rotation)
            c.scale(blob.w / 2f, blob.h / 2f)
            c.drawCircle(0f, 0f, 1f, blobPaints[i])
            c.restore()
        }
    }

    fun drawTide(c: Canvas) {
        c.save()
        c.translate(0f, TIDE_TOP)
        c.drawPath(tideShadePath, tideShade)
        c.drawPath(tideLinePath, tideLine)
        c.restore()
    }

    fun drawCore(c: Canvas) {
        c.save()
        c.translate(CORE_X, CORE_Y)
        c.scale(CORE_RADIUS, CORE_RADIUS)
        c.drawCircle(0f, 0f, 1f, core)
        c.restore()
        c.save()
        c.translate(CORE_X, CORE_Y)
        c.scale(SPARK_RADIUS, SPARK_RADIUS)
        c.drawCircle(0f, 0f, 1f, spark)
        c.restore()
    }

    private companion object {
        const val OPAQUE = 255f
        const val STORM_X = 0.5f
        const val STORM_Y = 0.55f
        const val STORM_RX = 0.8f
        const val STORM_RY = 0.6f
        const val LINE_WIDTH = 1.5f
        const val SHADE_WIDTH = 6f
        const val TIDE_TOP = 380f
        const val CORE_X = 193f
        const val CORE_Y = 470f
        const val CORE_RADIUS = 31.7f
        const val SPARK_RADIUS = 30f
        const val SPARK_SOLID = 0.45f
    }
}
