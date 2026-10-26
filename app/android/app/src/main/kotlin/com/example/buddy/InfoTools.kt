package com.example.buddy

import android.content.Context
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Live facts for the AI: weather for any city, exchange rates, exact math and unit conversion. BLOCKING: call off the main thread. */
object InfoTools {
    /** Runs one of {"do":"weather"|"currency"|"calc"|"convert", ...}; returns English facts, throws with a readable message. */
    fun run(ctx: Context, a: JSONObject): String = when (jsStr(a, "do")) {
        "weather" -> weather(ctx, jsStr(a, "city").trim())
        "currency" -> currency(a.optDouble("amount", 1.0), jsStr(a, "from"), jsStr(a, "to"))
        "calc" -> {
            val e = jsStr(a, "expr").trim()
            "$e = " + MathTools.fmt(MathTools.calc(e))
        }
        "convert" -> {
            if (!a.has("value") || a.isNull("value")) throw IllegalArgumentException("no value to convert")
            MathTools.convert(a.optDouble("value"), jsStr(a, "from"), jsStr(a, "to"))
        }
        else -> throw IllegalArgumentException("unknown info action")
    }

    private fun getJson(url: String): JSONObject {
        var c: HttpURLConnection? = null
        try {
            c = URL(url).openConnection() as HttpURLConnection
            c.connectTimeout = 8000
            c.readTimeout = 8000
            if (c.responseCode != 200) throw Exception("HTTP ${c.responseCode}")
            return JSONObject(c.inputStream.bufferedReader().use { it.readText() })
        } finally {
            try { c?.disconnect() } catch (_: Exception) { }
        }
    }

    private fun weather(ctx: Context, cityIn: String): String {
        val lat: Double
        val lon: Double
        val place: String
        if (cityIn.isEmpty()) {
            val loc = Briefing.lastLocation(ctx)
                ?: throw Exception("the phone location is unavailable: ask the user which city")
            lat = loc.latitude; lon = loc.longitude; place = "the phone's location"
        } else {
            val g = getJson("https://geocoding-api.open-meteo.com/v1/search?count=1&language=en&format=json&name=" + URLEncoder.encode(cityIn.take(80), "UTF-8"))
            val r = g.optJSONArray("results")?.optJSONObject(0) ?: throw Exception("city not found: $cityIn")
            lat = r.getDouble("latitude"); lon = r.getDouble("longitude")
            place = listOf(r.optString("name"), r.optString("country")).filter { it.isNotBlank() }.joinToString(", ")
        }
        val url = "https://api.open-meteo.com/v1/forecast?latitude=%.3f&longitude=%.3f".format(Locale.US, lat, lon) +
            "&current=temperature_2m,apparent_temperature,relative_humidity_2m,weather_code,wind_speed_10m" +
            "&daily=weather_code,temperature_2m_max,temperature_2m_min,precipitation_probability_max&forecast_days=3&timezone=auto"
        val j = getJson(url)
        val cur = j.getJSONObject("current")
        val d = j.getJSONObject("daily")
        val sb = StringBuilder("Weather in $place:\n")
        sb.append("Now: ").append(Briefing.wmo(cur.optInt("weather_code", -1))).append(", ")
            .append(Math.round(cur.optDouble("temperature_2m"))).append(" C (feels like ")
            .append(Math.round(cur.optDouble("apparent_temperature"))).append(" C), humidity ")
            .append(Math.round(cur.optDouble("relative_humidity_2m"))).append("%, wind ")
            .append(Math.round(cur.optDouble("wind_speed_10m"))).append(" km/h\n")
        val times = d.optJSONArray("time")
        val codes = d.optJSONArray("weather_code")
        val hi = d.optJSONArray("temperature_2m_max")
        val lo = d.optJSONArray("temperature_2m_min")
        val rain = d.optJSONArray("precipitation_probability_max")
        for (i in 0 until (times?.length() ?: 0)) {
            val label = when (i) { 0 -> "Today"; 1 -> "Tomorrow"; else -> times!!.optString(i) }
            sb.append(label).append(": ").append(Briefing.wmo(codes?.optInt(i, -1) ?: -1))
                .append(", ").append(Math.round(lo?.optDouble(i) ?: Double.NaN)).append(" to ").append(Math.round(hi?.optDouble(i) ?: Double.NaN)).append(" C")
            val p = rain?.optDouble(i, Double.NaN) ?: Double.NaN
            if (!p.isNaN()) sb.append(", rain chance ").append(Math.round(p)).append('%')
            sb.append('\n')
        }
        return sb.toString().trim()
    }

    private val CUR_ALIAS = mapOf(
        "SUM" to "UZS", "SOM" to "UZS", "SO'M" to "UZS", "СУМ" to "UZS", "СОМ" to "UZS",
        "DOLLAR" to "USD", "ДОЛЛАР" to "USD", "EURO" to "EUR", "ЕВРО" to "EUR",
        "RUBLE" to "RUB", "RUBL" to "RUB", "РУБЛЬ" to "RUB", "YUAN" to "CNY", "ЮАНЬ" to "CNY"
    )

    private fun code(raw: String): String {
        val u = raw.trim().uppercase()
        val c = CUR_ALIAS[u] ?: u
        if (!Regex("[A-Z]{3}").matches(c)) throw IllegalArgumentException("unknown currency '$raw': use a 3-letter code like USD")
        return c
    }

    private fun currency(amount: Double, fromRaw: String, toRaw: String): String {
        if (amount.isNaN() || amount.isInfinite()) throw IllegalArgumentException("no amount")
        val from = code(fromRaw)
        val to = code(toRaw)
        val j = getJson("https://open.er-api.com/v6/latest/$from")
        if (j.optString("result") != "success") throw Exception("unknown currency $from")
        val rate = j.getJSONObject("rates").optDouble(to, Double.NaN)
        if (rate.isNaN()) throw Exception("unknown currency $to")
        val day = SimpleDateFormat("d MMM yyyy", Locale.ENGLISH).format(Date(j.optLong("time_last_update_unix", 0L) * 1000))
        return "%s %s = %s %s (market rate 1 %s = %s %s, updated %s; banks and exchange offices differ a little)".format(
            MathTools.fmt(amount), from, "%,.2f".format(Locale.US, amount * rate), to, from, MathTools.fmt(rate), to, day
        )
    }
}
