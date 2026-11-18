package com.example.buddy.ui.home

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import com.example.buddy.ui.GlassKind
import com.example.buddy.ui.glassFont
import kotlin.math.PI
import kotlin.math.cos
import kotlin.random.Random

class SandPetal(
    val spec: PetalSpec,
    private val sand: SandShaders,
    private val weeds: PetalWeeds,
) {
    val area = RectF(spec.left, spec.top, spec.left + spec.width, spec.top + spec.height)
    var lines: List<String> = spec.label.split('\n')
    var alpha = 1f
    var scale = 1f
    var crack = 0f
    var reveal = 1f
    var weed = 1f

    private val halfW = spec.width / 2f
    private val halfH = spec.height / 2f
    private val shape = Path()
    private val crackPath = Path()
    private val body = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader = LinearGradient(0f, -halfH, 0f, halfH, Color.argb(15, 255, 255, 255), Color.argb(20, 230, 205, 150), Shader.TileMode.CLAMP)
    }
    private val rim = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = RIM_WIDTH
        color = Color.argb(if (spec.kind == GlassKind.Active) RIM_STRONG else RIM_SOFT, 255, 255, 255)
    }
    private val highlight = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = RIM_WIDTH
        color = Color.argb(if (spec.kind == GlassKind.Active) RIM_STRONG else RIM_SOFT, 255, 255, 255)
    }
    private val iconPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = ICON_STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        color = INK
    }
    private val halo = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; color = Color.argb(0, 255, 240, 200) }
    private val crackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = CRACK_WIDTH
        strokeCap = Paint.Cap.ROUND
        color = CRACK_COLOR
    }
    private val label = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = LABEL_SIZE
        textAlign = Paint.Align.CENTER
        typeface = glassFont(thin = true)
        color = INK
        setShadowLayer(GLOW_RADIUS, 0f, 0f, Color.argb(242, 255, 255, 255))
    }
    private val labelBaseline: Float

    init {
        val fm = label.fontMetrics
        labelBaseline = (LINE_HEIGHT - (fm.descent - fm.ascent)) / 2f - fm.ascent
        shape.addRoundRect(
            RectF(-halfW, -halfH, halfW, halfH),
            floatArrayOf(
                TOP_RX * spec.width, TOP_RY * spec.height, TOP_RX * spec.width, TOP_RY * spec.height,
                BOTTOM_RX * spec.width, BOTTOM_RY * spec.height, BOTTOM_RX * spec.width, BOTTOM_RY * spec.height,
            ),
            Path.Direction.CW,
        )
    }

    fun restore() {
        alpha = 1f
        scale = 1f
        crack = 0f
        reveal = 1f
        weed = 1f
    }

    fun reseedCracks(random: Random) {
        crackPath.rewind()
        for (line in 0 until CRACK_LINES) {
            var x = CRACK_CENTER + random.nextFloat() * 2f * CRACK_SPREAD - CRACK_SPREAD
            var y = CRACK_CENTER + random.nextFloat() * 2f * CRACK_SPREAD - CRACK_SPREAD
            crackPath.moveTo(x, y)
            for (step in 0 until CRACK_STEPS) {
                x += random.nextFloat() * 2f * CRACK_STEP - CRACK_STEP
                y += random.nextFloat() * 2f * CRACK_STEP - CRACK_STEP
                crackPath.lineTo(x, y)
            }
        }
    }

    fun draw(c: Canvas, seconds: Float) {
        if (alpha <= 0f) return
        val lift = IDLE_LIFT * 0.5f * (1f - cos(2.0 * PI * seconds / IDLE_PERIOD).toFloat())
        c.save()
        c.translate(area.centerX(), area.centerY() - lift)
        c.rotate(spec.rotation)
        c.scale(scale, scale)
        val faded = alpha < 1f
        if (faded) c.saveLayerAlpha(-spec.width, -spec.height, spec.width, spec.height, (alpha * 255f).toInt())
        val clipped = reveal < 1f
        if (clipped) { c.save(); c.clipRect(-spec.width, halfH - reveal * spec.height, spec.width, spec.height * 2f) }
        drawSand(c)
        weeds.draw(c, seconds, spec.width, spec.height, weed)
        drawContent(c)
        if (clipped) c.restore()
        if (faded) c.restore()
        c.restore()
    }

    private fun drawSand(c: Canvas) {
        if (spec.kind == GlassKind.Active) drawHalo(c)
        c.drawPath(shape, body)
        c.drawPath(shape, sand.speckleDark)
        c.drawPath(shape, sand.speckleLight)
        if (crack > 0f) drawCracks(c)
        c.save()
        c.clipPath(shape)
        c.translate(0f, RIM_WIDTH)
        c.drawPath(shape, highlight)
        c.restore()
        c.drawPath(shape, rim)
    }

    private fun drawHalo(c: Canvas) {
        for (i in HALO_WIDTHS.indices) {
            halo.strokeWidth = HALO_WIDTHS[i]
            halo.alpha = HALO_ALPHAS[i]
            c.drawPath(shape, halo)
        }
    }

    private fun drawCracks(c: Canvas) {
        crackPaint.alpha = (crack * 255f).toInt()
        c.save()
        c.clipPath(shape)
        c.translate(-halfW, -halfH)
        c.scale(spec.width / CRACK_BOX, spec.height / CRACK_BOX)
        c.drawPath(crackPath, crackPaint)
        c.restore()
    }

    private fun drawContent(c: Canvas) {
        c.save()
        c.rotate(-spec.rotation)
        val blockH = ICON_BOX + ICON_GAP + lines.size * LINE_HEIGHT
        var top = (CONTENT_MARGIN - blockH) / 2f
        c.save()
        c.translate(-ICON_BOX / 2f, top)
        c.scale(ICON_BOX / HalloweenArt.ICON_SIZE, ICON_BOX / HalloweenArt.ICON_SIZE)
        c.drawPath(HalloweenArt.icon(spec.icon), iconPaint)
        c.restore()
        top += ICON_BOX + ICON_GAP
        for (i in lines.indices) c.drawText(lines[i], 0f, top + i * LINE_HEIGHT + labelBaseline, label)
        c.restore()
    }

    private companion object {
        val INK = Color.rgb(74, 50, 22)
        val CRACK_COLOR = Color.rgb(138, 98, 50)
        val HALO_WIDTHS = floatArrayOf(14f, 9f, 4f)
        val HALO_ALPHAS = intArrayOf(10, 18, 30)
        const val RIM_SOFT = 140
        const val RIM_STRONG = 204
        const val RIM_WIDTH = 1f
        const val TOP_RX = 0.5f
        const val TOP_RY = 0.64f
        const val BOTTOM_RX = 0.46f
        const val BOTTOM_RY = 0.36f
        const val IDLE_LIFT = 5f
        const val IDLE_PERIOD = 6f
        const val LABEL_SIZE = 12f
        const val LINE_HEIGHT = 14.4f
        const val ICON_GAP = 6f
        const val ICON_BOX = 26f
        const val ICON_STROKE = 1.1f
        const val CONTENT_MARGIN = 10f
        const val GLOW_RADIUS = 6f
        const val CRACK_WIDTH = 1.8f
        const val CRACK_BOX = 100f
        const val CRACK_CENTER = 50f
        const val CRACK_SPREAD = 10f
        const val CRACK_STEP = 18f
        const val CRACK_LINES = 6
        const val CRACK_STEPS = 4
    }
}
