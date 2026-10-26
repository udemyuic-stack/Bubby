package com.example.buddy

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.BatteryManager
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/** Builds English facts for a morning briefing. BLOCKING: call off the main thread. */
object Briefing {
    fun build(ctx: Context): String {
        val sb = StringBuilder()
        val now = System.currentTimeMillis()
        sb.append("Now: ").append(SimpleDateFormat("EEEE, d MMMM yyyy, HH:mm", Locale.ENGLISH).format(Date(now))).append('\n')

        try {
            val end = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, 23); set(Calendar.MINUTE, 59); set(Calendar.SECOND, 59)
            }.timeInMillis
            val due = TaskStore(ctx).all().filter { !it.done && (it.dueMillis ?: Long.MAX_VALUE) <= end }
            sb.append("Tasks due today or overdue:\n")
            if (due.isEmpty()) sb.append("none\n")
            else due.take(10).forEach { t ->
                val d = t.dueMillis?.let { (if (it < now) " (overdue, " else " (due ") + fmtWhen(it) + ")" } ?: ""
                sb.append("- ").append(t.title.take(60)).append(d).append('\n')
            }
        } catch (_: Exception) {
        }

        try {
            val ev = CalendarStore(ctx).events(now, now + 24 * 3_600_000L).take(10)
            sb.append("Calendar events, next 24 hours:\n")
            if (ev.isEmpty()) sb.append("none\n")
            else ev.forEach { e ->
                val loc = if (e.location.isNotEmpty()) " @" + e.location.take(30) else ""
                sb.append("- ").append(fmtWhen(e.startMillis)).append(' ').append(e.title.take(60)).append(loc).append('\n')
            }
        } catch (_: Exception) {
        }

        try {
            val bm = ctx.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
            val lvl = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
            if (lvl in 0..100) sb.append("Battery: ").append(lvl).append("%\n")
        } catch (_: Exception) {
        }

        weather(ctx)?.let { sb.append(it).append('\n') }
        return sb.toString().trim()
    }

    private fun lastLocation(ctx: Context): Location? {
        if (ctx.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) return null
        return try {
            val lm = ctx.getSystemService(Context.LOCATION_SERVICE) as LocationManager
            var best: Location? = null
            for (p in lm.allProviders) {
                val l = try { lm.getLastKnownLocation(p) } catch (_: Exception) { null } ?: continue
                if (best == null || l.time > best.time) best = l
            }
            best
        } catch (_: Exception) {
            null
        }
    }

    private fun weather(ctx: Context): String? {
        val loc = lastLocation(ctx) ?: return null
        var c: HttpURLConnection? = null
        return try {
            val url = "https://api.open-meteo.com/v1/forecast?latitude=%.2f&longitude=%.2f".format(Locale.US, loc.latitude, loc.longitude) +
                "&current=temperature_2m,weather_code,wind_speed_10m&daily=temperature_2m_max,temperature_2m_min&timezone=auto"
            c = URL(url).openConnection() as HttpURLConnection
            c.connectTimeout = 6000
            c.readTimeout = 6000
            if (c.responseCode != 200) return null
            val j = JSONObject(c.inputStream.bufferedReader().use { it.readText() })
            val cur = j.getJSONObject("current")
            val daily = j.optJSONObject("daily")
            val hi = daily?.optJSONArray("temperature_2m_max")?.optDouble(0)
            val lo = daily?.optJSONArray("temperature_2m_min")?.optDouble(0)
            val sb = StringBuilder("Weather now: ")
            sb.append(wmo(cur.optInt("weather_code", -1))).append(", ")
                .append(Math.round(cur.optDouble("temperature_2m"))).append(" C, wind ")
                .append(Math.round(cur.optDouble("wind_speed_10m"))).append(" km/h")
            if (hi != null && lo != null && !hi.isNaN() && !lo.isNaN())
                sb.append("; today low ").append(Math.round(lo)).append(" C, high ").append(Math.round(hi)).append(" C")
            sb.toString()
        } catch (_: Exception) {
            null
        } finally {
            try { c?.disconnect() } catch (_: Exception) {}
        }
    }

    private fun wmo(code: Int): String = when (code) {
        0 -> "clear sky"
        1, 2 -> "partly cloudy"
        3 -> "overcast"
        45, 48 -> "fog"
        51, 53, 55, 56, 57 -> "drizzle"
        61, 63, 65, 66, 67, 80, 81, 82 -> "rain"
        71, 73, 75, 77, 85, 86 -> "snow"
        95, 96, 99 -> "thunderstorm"
        else -> "unknown conditions"
    }
}
