package com.example.buddy

import android.content.Context
import android.provider.Settings
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator

object IslandUi {
    const val RISK_NONE = 0
    const val RISK_MID = 1
    const val RISK_HIGH = 2

    private val READ_ONLY = setOf("read", "grep", "glob", "ls", "notebookread", "todoread", "websearch")
    private val WRITERS = setOf("bash", "write", "edit", "multiedit", "notebookedit")
    private val DANGER = Regex("\\brm\\b|\\bsudo\\b|\\bmkfs|\\bdd\\b|\\bchmod\\b|\\bchown\\b|git\\s+push|--force|\\bkill\\b|drop\\s+table")

    /** False when the user turned animations off in developer options. */
    fun animOn(ctx: Context): Boolean = try {
        Settings.Global.getFloat(ctx.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) > 0f
    } catch (_: Exception) { true }

    fun risk(tool: String, command: String): Int {
        val t = tool.lowercase()
        if (t in READ_ONLY) return RISK_NONE
        if (t in WRITERS) return RISK_HIGH
        if (DANGER.containsMatchIn(command.lowercase())) return RISK_HIGH
        return RISK_MID
    }

    fun riskColor(r: Int): Int = when (r) {
        RISK_HIGH -> 0xFFFF5A52.toInt()
        RISK_MID -> 0xFFFFB454.toInt()
        else -> 0xFF8A8A93.toInt()
    }

    fun reveal(v: View, u: Float, delay: Long = 0L) {
        v.animate().cancel()
        if (!animOn(v.context)) { v.alpha = 1f; v.translationY = 0f; return }
        v.alpha = 0f; v.translationY = 8f * u
        v.animate().alpha(1f).translationY(0f).setStartDelay(delay).setDuration(220)
            .setInterpolator(DecelerateInterpolator(1.5f)).start()
    }

    /**
     * Squash-and-stretch press: dips in (slightly wider than tall, a touch dimmer) with a tick of haptics, then springs
     * back with overshoot. Never consumes the touch, so clicks still work.
     */
    fun pressable(v: View, to: Float = 0.96f) {
        v.setOnTouchListener { view, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                    view.animate().scaleX(to - 0.015f).scaleY(minOf(1f, to + 0.025f)).alpha(0.88f)
                        .setStartDelay(0).setDuration(80).setInterpolator(DecelerateInterpolator()).start()
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> view.animate().scaleX(1f).scaleY(1f).alpha(1f)
                    .setStartDelay(0).setDuration(320).setInterpolator(OvershootInterpolator(3.2f)).start()
            }
            false
        }
    }
}
