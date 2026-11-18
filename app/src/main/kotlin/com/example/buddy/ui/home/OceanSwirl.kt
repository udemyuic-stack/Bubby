package com.example.buddy.ui.home

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

enum class MoteKind { SAND, LIGHT, DARK, CRAB, TURTLE }

class Mote {
    var alive = false
    var kind = MoteKind.SAND
    var bornMs = 0L
    var seekMs = 0L
    var startX = 0f
    var startY = 0f
    var y = 0f
    var angle = 0f
    var angleStep = 0f
    var rise = 0f
    var spin = 0f
    var spinStep = 0f
    var sizeFactor = 1f
    var fly = 0f
    var x = 0f
    var drawY = 0f
    var drawScale = 1f
    var drawAlpha = 1f
    var front = false
}

class OceanSwirl(private val art: CastArt) {
    private val motes = Array(POOL) { Mote() }
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val grain = RectF(-GRAIN_HALF, -GRAIN_HALF, GRAIN_HALF, GRAIN_HALF)
    private var lastMs = 0L

    fun reset(nowMs: Long) {
        for (m in motes) m.alive = false
        lastMs = nowMs
    }

    fun spawnGrain(nowMs: Long, x: Float, y: Float, simY: Float, random: Random) {
        val roll = random.nextFloat()
        val kind = if (roll < LIGHT_SHARE) MoteKind.LIGHT else if (roll < LIGHT_SHARE + DARK_SHARE) MoteKind.DARK else MoteKind.SAND
        spawn(nowMs, kind, x, y, simY, random, GRAIN_SEEK_MS, GRAIN_SPIN, 1f, random.nextFloat() * (RISE_MAX - RISE_MIN) + RISE_MIN, random.nextFloat() * (STEP_MAX - STEP_MIN) + STEP_MIN)
    }

    fun spawnSprite(nowMs: Long, kind: MoteKind, x: Float, y: Float, simY: Float, random: Random) {
        spawn(nowMs, kind, x, y, simY, random, SPRITE_SEEK_MS, SPRITE_SPIN, SPRITE_SIZE, random.nextFloat() * (SPRITE_RISE_MAX - SPRITE_RISE_MIN) + SPRITE_RISE_MIN, SPRITE_STEP)
    }

    fun update(nowMs: Long, towerX: Float, windOn: Boolean) {
        val frames = ((nowMs - lastMs).toFloat() / FRAME_MS).coerceIn(0f, MAX_FRAMES)
        lastMs = nowMs
        for (m in motes) if (m.alive) advance(m, nowMs, towerX, windOn, frames)
    }

    fun draw(c: Canvas, front: Boolean, castScale: Float) {
        for (m in motes) {
            if (!m.alive || m.front != front) continue
            c.save()
            c.translate(m.x, m.drawY)
            c.scale(m.drawScale * m.sizeFactor, m.drawScale * m.sizeFactor)
            c.rotate(m.spin)
            when (m.kind) {
                MoteKind.CRAB -> { c.scale(castScale, castScale); art.drawCrab(c, 0f, m.drawAlpha) }
                MoteKind.TURTLE -> { c.scale(castScale * TURTLE_SCALE, castScale * TURTLE_SCALE); art.drawTurtle(c, 0f, m.drawAlpha) }
                else -> drawGrain(c, m)
            }
            c.restore()
        }
    }

    private fun drawGrain(c: Canvas, m: Mote) {
        paint.color = when (m.kind) {
            MoteKind.LIGHT -> LIGHT
            MoteKind.DARK -> DARK
            else -> SAND
        }
        paint.alpha = (m.drawAlpha * OPAQUE).toInt()
        c.drawRoundRect(grain, GRAIN_RADIUS, GRAIN_RADIUS, paint)
    }

    private fun spawn(nowMs: Long, kind: MoteKind, x: Float, y: Float, simY: Float, random: Random, seek: Long, spinStep: Float, size: Float, rise: Float, angleStep: Float) {
        val m = motes.firstOrNull { !it.alive } ?: return
        m.alive = true
        m.kind = kind
        m.bornMs = nowMs
        m.seekMs = seek
        m.startX = x
        m.startY = y
        m.y = simY
        m.angle = random.nextFloat() * FULL_TURN
        m.angleStep = angleStep
        m.rise = rise
        m.spin = 0f
        m.spinStep = spinStep
        m.sizeFactor = size
        m.fly = 0f
        m.x = x
        m.drawY = y
        m.drawScale = 1f
        m.drawAlpha = 1f
        m.front = false
    }

    private fun advance(m: Mote, nowMs: Long, towerX: Float, windOn: Boolean, frames: Float) {
        val age = nowMs - m.bornMs
        val reach = OceanMotion.clamp((OceanArt.TORNADO_BOTTOM - m.y) / (OceanArt.TORNADO_BOTTOM - OceanArt.TORNADO_TOP))
        val radius = MIN_RADIUS + RADIUS_SPAN * reach
        m.angle += m.angleStep * frames
        val orbitX = towerX + radius * cos(m.angle)
        val depth = sin(m.angle)
        if (age < m.seekMs) {
            val f = age.toFloat() / m.seekMs
            val e = f * f
            m.x = m.startX + (orbitX - m.startX) * e
            m.drawY = m.startY + (m.y - m.startY) * e
            m.drawScale = 1f
            m.drawAlpha = 1f
        } else {
            m.y -= m.rise * (if (windOn) 1f else SLOW_RISE) * frames
            m.x = orbitX
            m.drawY = m.y
            val near = (depth + 1f) / 2f
            m.drawScale = SCALE_FAR + SCALE_SPAN * near
            m.drawAlpha = ALPHA_FAR + ALPHA_SPAN * near
            if (!windOn) {
                m.fly += frames
                m.x += cos(m.angle) * m.fly * FLY_STEP
            }
        }
        m.spin += m.spinStep * frames
        m.front = depth > 0f
        if (m.drawY < OceanArt.TORNADO_TOP - EXIT_MARGIN || m.fly > FLY_LIMIT || age > LIFE_MS) m.alive = false
    }

    private companion object {
        const val POOL = 320
        const val FRAME_MS = 16.667f
        const val MAX_FRAMES = 4f
        const val GRAIN_HALF = 3f
        const val GRAIN_RADIUS = 2.4f
        const val OPAQUE = 255f
        const val LIGHT_SHARE = 0.3f
        const val DARK_SHARE = 0.21f
        const val GRAIN_SEEK_MS = 450L
        const val SPRITE_SEEK_MS = 700L
        const val GRAIN_SPIN = 6f
        const val SPRITE_SPIN = 14f
        const val SPRITE_SIZE = 1.1f
        const val SPRITE_STEP = 0.16f
        const val SPRITE_RISE_MIN = 2.2f
        const val SPRITE_RISE_MAX = 3f
        const val RISE_MIN = 1.2f
        const val RISE_MAX = 2.6f
        const val STEP_MIN = 0.12f
        const val STEP_MAX = 0.2f
        const val MIN_RADIUS = 12f
        const val RADIUS_SPAN = 104f
        const val SLOW_RISE = 0.4f
        const val FLY_STEP = 4f
        const val FLY_LIMIT = 40f
        const val EXIT_MARGIN = 60f
        const val LIFE_MS = 9000L
        const val SCALE_FAR = 0.75f
        const val SCALE_SPAN = 0.35f
        const val ALPHA_FAR = 0.55f
        const val ALPHA_SPAN = 0.45f
        const val FULL_TURN = 6.283f
        const val TURTLE_SCALE = 1.1f
        val SAND = Color.rgb(217, 184, 120)
        val LIGHT = Color.rgb(243, 224, 176)
        val DARK = Color.rgb(184, 146, 90)
    }
}
