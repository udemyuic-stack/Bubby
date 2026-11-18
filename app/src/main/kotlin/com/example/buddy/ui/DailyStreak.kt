package com.example.buddy.ui

import android.content.Context
import java.time.LocalDate

object DailyStreak {
    val ladder = intArrayOf(1, 1, 2, 2, 3, 3, 5)

    private fun prefs(ctx: Context) = Backend.prefs(ctx)
    private fun today() = LocalDate.now().toEpochDay()

    fun bonus(ctx: Context) = Backend.int(ctx, "bonus_stars", 0)

    fun addStars(ctx: Context, n: Int) {
        if (n > 0) Backend.setInt(ctx, "bonus_stars", bonus(ctx) + n)
    }

    fun canClaim(ctx: Context) = prefs(ctx).getLong("ds_last", -1L) != today()

    fun streak(ctx: Context): Int {
        val last = prefs(ctx).getLong("ds_last", -1L)
        return if (last >= today() - 1) Backend.int(ctx, "ds_streak", 0) else 0
    }

    fun doneCount(ctx: Context): Int {
        val s = streak(ctx)
        return if (canClaim(ctx)) s % ladder.size else (s - 1) % ladder.size + 1
    }

    fun claim(ctx: Context): Int {
        if (!canClaim(ctx)) return 0
        val idx = streak(ctx) % ladder.size
        val reward = ladder[idx]
        prefs(ctx).edit().putLong("ds_last", today()).putInt("ds_streak", streak(ctx) + 1).apply()
        addStars(ctx, reward)
        return reward
    }
}
