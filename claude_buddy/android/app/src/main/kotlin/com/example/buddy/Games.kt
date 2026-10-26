package com.example.buddy

import android.content.Context
import org.json.JSONArray
import java.util.concurrent.ThreadLocalRandom

object Games {
    fun contextLine(ctx: Context): String {
        val st = try {
            ctx.getSharedPreferences("buddy", Context.MODE_PRIVATE).getString("game_state", "")?.trim() ?: ""
        } catch (_: Exception) { "" }
        val r = ThreadLocalRandom.current()
        val rps = listOf("rock", "paper", "scissors")[r.nextInt(3)]
        val coin = if (r.nextBoolean()) "heads" else "tails"
        return "Current game: ${st.ifEmpty { "none" }}. Fresh random numbers (use them if the game needs luck, never invent your own): " +
            "n100=${r.nextInt(1, 101)}, d6=${r.nextInt(1, 7)}, rps=$rps, coin=$coin."
    }

    fun chips(ctx: Context): List<String> {
        return try {
            val raw = ctx.getSharedPreferences("buddy", Context.MODE_PRIVATE).getString("game_chips", "") ?: ""
            if (raw.isBlank()) return emptyList()
            val arr = JSONArray(raw)
            val out = ArrayList<String>()
            for (i in 0 until arr.length()) {
                val t = arr.optString(i, "").trim()
                if (t.isNotEmpty()) out.add(t.take(40))
                if (out.size >= 6) break
            }
            out
        } catch (_: Exception) { emptyList() }
    }
}
