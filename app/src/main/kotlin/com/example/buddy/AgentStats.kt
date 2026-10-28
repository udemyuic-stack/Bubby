package com.example.buddy

import android.content.Context

/** The last usage numbers the computer reported, kept so the dashboard and widgets still show something offline. */
object AgentStats {
    private fun prefs(ctx: Context) = ctx.applicationContext.getSharedPreferences("agent_stats", Context.MODE_PRIVATE)

    fun save(ctx: Context, u: Usage) {
        prefs(ctx).edit()
            .putFloat("five", u.fiveHour ?: -1f).putLong("fiveR", u.fiveHourResets)
            .putFloat("seven", u.sevenDay ?: -1f).putLong("sevenR", u.sevenDayResets)
            .putInt("done", u.doneToday ?: -1).putLong("tokens", u.tokensToday ?: -1L)
            .putString("plan", u.plan).putLong("at", u.updatedAt).apply()
    }

    fun last(ctx: Context): Usage? {
        val p = prefs(ctx)
        if (!p.contains("at")) return null
        return Usage(
            p.getFloat("five", -1f).takeIf { it >= 0f }, p.getLong("fiveR", 0L),
            p.getFloat("seven", -1f).takeIf { it >= 0f }, p.getLong("sevenR", 0L),
            p.getInt("done", -1).takeIf { it >= 0 }, p.getLong("tokens", -1L).takeIf { it >= 0 },
            p.getString("plan", null), p.getLong("at", 0L)
        )
    }

    /** The newest numbers: live ones while connected, otherwise the saved ones. */
    fun current(ctx: Context): Usage? = BuddyService.agentUsage ?: last(ctx)

    /** Home-screen widgets show agents and limits; they are redrawn whenever either changes. */
    fun refreshWidgets(ctx: Context) {
        try { AgentWidgets.refresh(ctx) } catch (_: Exception) { }
    }
}
