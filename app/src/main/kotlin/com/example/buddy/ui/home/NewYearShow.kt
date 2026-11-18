package com.example.buddy.ui.home

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.provider.Settings
import kotlin.random.Random

object NewYearEagle {
    @Volatile
    var away = false
}

class NewYearShow(private val ctx: Context) : Show() {
    private val knock = NewYearKnock(NewYearLayout.petals[NewYearLayout.KNOCKED_PETAL])
    private var script: NewYearScript? = null
    private var total = 0L
    private val sparkPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader = RadialGradient(0f, 0f, 1f, intArrayOf(0xFFFFFFFF.toInt(), SPARK_MID, SPARK_EDGE), floatArrayOf(0f, 0.4f, 0.7f), Shader.TileMode.CLAMP)
    }

    override val durationMs get() = total

    fun mascotLift(): Float {
        if (!running) return 0f
        val ms = elapsedMs
        if (ms >= JUMPS * JUMP_MS) return 0f
        return JUMP_PX * Ease.hump((ms % JUMP_MS) / JUMP_MS.toFloat())
    }

    fun knockPose(): PetalPose? = if (running) knock.poseAt(elapsedMs) else null

    override fun onStart() {
        total = if (reducedMotion()) 0L else NewYearScript.TOTAL_MS
        if (total == 0L) return
        NewYearEagle.away = true
        script = NewYearScript(knock, Random.Default)
    }

    override fun onCancel() {
        NewYearEagle.away = false
        script = null
    }

    override fun onDraw(c: Canvas, scale: Float, ms: Long) {
        val s = script ?: return
        NewYearEagle.away = ms < NewYearScript.EAGLE_BACK
        c.save()
        c.scale(scale, scale)
        for (spark in s.sparks) drawSpark(c, spark, ms)
        for (sprite in s.sprites) sprite.draw(c, ms)
        c.restore()
    }

    private fun drawSpark(c: Canvas, s: NewYearScript.Spark, ms: Long) {
        val age = ms - s.atMs
        if (age < 0 || age >= SPARK_LIFE_MS) return
        val p = age / SPARK_LIFE_MS.toFloat()
        val size = Ease.lerp(1f, 0.2f, p) * SPARK_R
        c.save()
        c.translate(s.x + s.dx * p, s.y + s.dy * p)
        c.scale(size, size)
        sparkPaint.alpha = ((1f - p) * 255f).toInt()
        c.drawCircle(0f, 0f, 1f, sparkPaint)
        c.restore()
    }

    private fun reducedMotion() =
        Settings.Global.getFloat(ctx.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f

    private companion object {
        const val JUMPS = 3
        const val JUMP_MS = 500L
        const val JUMP_PX = 16f
        const val SPARK_LIFE_MS = 900L
        const val SPARK_R = 3f
        const val SPARK_MID = 0xFFFFE27A.toInt()
        const val SPARK_EDGE = 0x00FFE27A
    }
}
