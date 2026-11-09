package com.example.buddy.ui.home

import android.graphics.Canvas
import android.graphics.RectF
import android.view.animation.Interpolator
import android.view.animation.LinearInterpolator
import android.view.animation.PathInterpolator

object Curves {
    val linear: Interpolator = LinearInterpolator()
    val easeIn: Interpolator = PathInterpolator(0.42f, 0f, 1f, 1f)
    val easeOut: Interpolator = PathInterpolator(0f, 0f, 0.58f, 1f)
    val easeInOut: Interpolator = PathInterpolator(0.42f, 0f, 0.58f, 1f)
    val lift: Interpolator = PathInterpolator(0.3f, 0.7f, 0.3f, 1f)
    val overshoot: Interpolator = PathInterpolator(0.34f, 1.56f, 0.64f, 1f)
}

class Key(val at: Float, val x: Float, val y: Float, val alpha: Float = 1f, val rotation: Float = 0f)

class Pose(var x: Float = 0f, var y: Float = 0f, var alpha: Float = 1f, var rotation: Float = 0f)

class Phase(
    val startMs: Long,
    val durationMs: Long,
    val curve: Interpolator,
    val keys: List<Key>,
    val flip: Boolean = false,
    val walking: Boolean = true
) {
    val endMs get() = startMs + durationMs

    fun sample(ms: Long, out: Pose) {
        val raw = ((ms - startMs).toFloat() / durationMs).coerceIn(0f, 1f)
        val p = curve.getInterpolation(raw)
        var i = 0
        while (i < keys.size - 2 && p > keys[i + 1].at) i++
        val a = keys[i]
        val b = keys[i + 1]
        val q = ((p - a.at) / (b.at - a.at)).coerceIn(0f, 1f)
        out.x = Ease.lerp(a.x, b.x, q)
        out.y = Ease.lerp(a.y, b.y, q)
        out.alpha = Ease.lerp(a.alpha, b.alpha, q)
        out.rotation = Ease.lerp(a.rotation, b.rotation, q)
    }
}

class Sprite(
    private val actor: Actor,
    private val phases: List<Phase>,
    private val endMs: Long,
    private val showBefore: Boolean = false
) {
    private val pose = Pose()
    private val layer = RectF()

    fun draw(c: Canvas, ms: Long) {
        if (ms >= endMs) return
        val phase = phases.lastOrNull { it.startMs <= ms } ?: if (showBefore) phases.first() else return
        phase.sample(ms, pose)
        if (pose.alpha <= 0f) return
        c.save()
        c.translate(pose.x, pose.y)
        c.rotate(pose.rotation)
        if (phase.flip) c.scale(-1f, 1f)
        if (pose.alpha < 1f) {
            layer.set(-actor.width, -actor.height, actor.width, actor.height)
            c.saveLayerAlpha(layer, (pose.alpha * 255f).toInt())
        }
        actor.draw(c, clock(ms) / 1000f, ms)
        if (pose.alpha < 1f) c.restore()
        c.restore()
    }

    private fun clock(ms: Long): Float {
        var paused = 0L
        for (p in phases) if (!p.walking && ms > p.startMs) paused += minOf(ms, p.endMs) - p.startMs
        return (ms - paused).toFloat()
    }
}
