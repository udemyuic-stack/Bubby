package com.example.buddy.ui

import android.content.Context
import android.provider.Settings
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.view.animation.Interpolator
import android.view.animation.PathInterpolator

object Motion {
    const val INSTANT = 90L
    const val QUICK = 160L
    const val STANDARD = 240L
    const val EMPHASIS = 400L

    const val PRESS_SCALE = 0.96f

    val standard: Interpolator = PathInterpolator(0.2f, 0f, 0f, 1f)
    val exit: Interpolator = PathInterpolator(0.4f, 0f, 1f, 1f)
    val settle: Interpolator = DecelerateInterpolator(1.4f)

    fun enabled(ctx: Context): Boolean =
        Settings.Global.getFloat(ctx.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) > 0f

    fun View.ms(d: Long): Long = if (enabled(context)) d else 0L
}
