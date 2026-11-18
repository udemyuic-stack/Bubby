package com.example.buddy.ui.home

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Shader

class PetalWeeds {
    private val stems = Array(OceanArt.weedStems.size) { stemPath(OceanArt.weedStems[it]) }
    private val stemPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        shader = LinearGradient(0f, VIEW_H, 0f, 0f, Color.rgb(47, 122, 74), Color.rgb(127, 207, 122), Shader.TileMode.CLAMP)
    }
    private val leafPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(95, 191, 106) }

    fun draw(c: Canvas, seconds: Float, petalW: Float, petalH: Float, grow: Float) {
        if (grow <= 0f) return
        val boxW = petalW * (1f - 2f * SIDE_MARGIN)
        val boxH = petalH * HEIGHT_SHARE
        c.save()
        c.translate(-petalW / 2f + petalW * SIDE_MARGIN, petalH / 2f - petalH * BOTTOM_MARGIN - boxH)
        c.scale(boxW / VIEW_W, boxH / VIEW_H)
        c.scale(1f, grow, VIEW_W / 2f, VIEW_H)
        for (i in stems.indices) drawStem(c, seconds, i)
        for (leaf in OceanArt.weedLeaves) {
            c.save()
            c.rotate(leaf[4], leaf[0], leaf[1])
            c.drawOval(leaf[0] - leaf[2], leaf[1] - leaf[3], leaf[0] + leaf[2], leaf[1] + leaf[3], leafPaint)
            c.restore()
        }
        c.restore()
    }

    private fun drawStem(c: Canvas, seconds: Float, index: Int) {
        val data = OceanArt.weedStems[index]
        val swing = OceanMotion.easeInOut.getInterpolation(OceanMotion.pingPong(seconds - data[DELAY], SWAY_PERIOD))
        stemPaint.strokeWidth = data[WIDTH]
        c.save()
        c.rotate(-SWAY_DEGREES + 2f * SWAY_DEGREES * swing, data[0], VIEW_H)
        c.drawPath(stems[index], stemPaint)
        c.restore()
    }

    private fun stemPath(d: FloatArray) = Path().apply {
        moveTo(d[0], d[1])
        cubicTo(d[2], d[3], d[4], d[5], d[6], d[7])
        cubicTo(d[8], d[9], d[10], d[11], d[12], d[13])
    }

    private companion object {
        const val VIEW_W = 60f
        const val VIEW_H = 40f
        const val SIDE_MARGIN = 0.22f
        const val BOTTOM_MARGIN = 0.03f
        const val HEIGHT_SHARE = 0.4f
        const val SWAY_PERIOD = 2.8f
        const val SWAY_DEGREES = 7f
        const val WIDTH = 14
        const val DELAY = 15
    }
}
