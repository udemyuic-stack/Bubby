package com.example.buddy.ui

import android.content.Context
import android.provider.Settings
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.view.animation.Interpolator
import android.view.animation.PathInterpolator
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin

object Motion {
    const val INSTANT = 90L
    const val QUICK = 160L
    const val STANDARD = 240L
    const val EMPHASIS = 400L

    const val PRESS_SCALE = 0.96f
    const val STAGGER = 30L
    const val STAGGER_ITEMS = 6
    const val RISE_DP = 12

    private const val SPRING_DECAY = 8f
    private const val SPRING_FREQ = 8.94f

    val standard: Interpolator = PathInterpolator(0.2f, 0f, 0f, 1f)
    val exit: Interpolator = PathInterpolator(0.4f, 0f, 1f, 1f)
    val settle: Interpolator = DecelerateInterpolator(1.4f)
    val spring: Interpolator = Interpolator { t ->
        if (t >= 1f) 1f
        else 1f - exp(-SPRING_DECAY * t) * (cos(SPRING_FREQ * t) + SPRING_DECAY / SPRING_FREQ * sin(SPRING_FREQ * t))
    }

    fun enabled(ctx: Context): Boolean =
        Settings.Global.getFloat(ctx.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) > 0f

    fun View.ms(d: Long): Long = if (enabled(context)) d else 0L

    fun stagger(ctx: Context, index: Int): Long =
        if (enabled(ctx)) index.coerceIn(0, STAGGER_ITEMS - 1) * STAGGER else 0L

    fun enter(view: View, index: Int = 0) {
        if (!enabled(view.context)) return
        view.alpha = 0f
        view.translationY = view.context.dp(RISE_DP).toFloat()
        view.animate().alpha(1f).translationY(0f)
            .setStartDelay(stagger(view.context, index))
            .setDuration(STANDARD)
            .setInterpolator(standard)
            .start()
    }
}
