package com.example.buddy.ui.home

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path

object OceanCast {
    private val owners = java.util.concurrent.atomic.AtomicInteger()
    val drawnByHome: Boolean get() = owners.get() > 0

    fun attach() { owners.incrementAndGet() }

    fun detach() { owners.updateAndGet { (it - 1).coerceAtLeast(0) } }
}

class CastArt {
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }
    private val flippers = Path()
    private val legs = Path()
    private val shell = Path()
    private val flipperPivotX: Float
    private val flipperPivotY: Float

    init {
        flippers.moveTo(-6f, 6f); flippers.cubicTo(-14f, 14f, -24f, 16f, -30f, 14f)
        flippers.cubicTo(-24f, 8f, -14f, 4f, -6f, 2f); flippers.close()
        flippers.moveTo(8f, 6f); flippers.cubicTo(14f, 16f, 22f, 20f, 28f, 20f)
        flippers.cubicTo(24f, 12f, 16f, 6f, 8f, 2f); flippers.close()
        val bounds = android.graphics.RectF()
        flippers.computeBounds(bounds, true)
        flipperPivotX = bounds.centerX()
        flipperPivotY = bounds.top
        for (side in floatArrayOf(-1f, 1f)) for (reach in LEG_REACH) {
            legs.moveTo(side * 8f, 2f)
            legs.lineTo(side * 16f, reach)
        }
        shell.moveTo(-12f, -2f); shell.lineTo(-4f, -8f); shell.lineTo(6f, -8f)
        shell.lineTo(12f, -2f); shell.lineTo(6f, 6f); shell.lineTo(-4f, 6f); shell.close()
    }

    fun drawCrab(c: Canvas, legShift: Float, alpha: Float) {
        stroke(CRAB_LEG, 2f, alpha)
        c.save()
        c.translate(0f, legShift)
        c.drawPath(legs, line)
        c.restore()
        solid(CRAB_BODY, alpha)
        c.drawOval(-12f, -8f, 12f, 8f, fill)
        c.drawCircle(-14f, -8f, 4f, fill)
        c.drawCircle(14f, -8f, 4f, fill)
        stroke(CRAB_BODY, 1.6f, alpha)
        c.drawLine(-4f, -7f, -4f, -12f, line)
        c.drawLine(4f, -7f, 4f, -12f, line)
        solid(WHITE, alpha)
        c.drawCircle(-4f, -13f, 2f, fill)
        c.drawCircle(4f, -13f, 2f, fill)
        solid(PUPIL, alpha)
        c.drawCircle(-4f, -13f, 1f, fill)
        c.drawCircle(4f, -13f, 1f, fill)
    }

    fun drawTurtle(c: Canvas, flipperAngle: Float, alpha: Float) {
        solid(TURTLE_FLIPPER, alpha)
        c.save()
        c.rotate(flipperAngle, flipperPivotX, flipperPivotY)
        c.drawPath(flippers, fill)
        c.restore()
        solid(TURTLE_SHELL, alpha)
        c.drawOval(-20f, -13f, 20f, 13f, fill)
        stroke(TURTLE_PATTERN, 1.4f, alpha)
        c.drawPath(shell, line)
        solid(TURTLE_HEAD, alpha)
        c.drawCircle(24f, -2f, 6f, fill)
        solid(TURTLE_EYE, alpha)
        c.drawCircle(26f, -4f, 1.3f, fill)
    }

    private fun solid(rgb: Int, alpha: Float) {
        fill.color = OPAQUE or rgb
        fill.alpha = (alpha * 255f).toInt()
    }

    private fun stroke(rgb: Int, width: Float, alpha: Float) {
        line.color = OPAQUE or rgb
        line.alpha = (alpha * 255f).toInt()
        line.strokeWidth = width
    }

    private companion object {
        const val OPAQUE = -0x1000000
        const val CRAB_BODY = 0xFF6A4A
        const val CRAB_LEG = 0xE8503A
        const val WHITE = 0xFFFFFF
        const val PUPIL = 0x222222
        const val TURTLE_FLIPPER = 0x7FBF8A
        const val TURTLE_SHELL = 0x5F9A62
        const val TURTLE_PATTERN = 0x3F7A45
        const val TURTLE_HEAD = 0x8FCF96
        const val TURTLE_EYE = 0x1A2A20
        val LEG_REACH = floatArrayOf(6f, 8f, 10f)
    }
}
