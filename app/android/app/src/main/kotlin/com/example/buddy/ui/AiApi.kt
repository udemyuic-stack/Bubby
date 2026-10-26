package com.example.buddy.ui

import android.content.Context
import com.example.buddy.Brain
import com.example.buddy.Tr
import org.json.JSONObject

/** Everything about AI connections that is not drawing: describing, testing, listing models, picking Groq models. */
object AiApi {
    class Info(val title: String, val model: String, val masked: String)

    private fun mask(k: String): String {
        if (k.isEmpty()) return ""
        if (k.length <= 8) return "••••"
        val head = when {
            k.startsWith("sk-or-") -> "sk-or-"
            k.startsWith("sk-ant") -> "sk-ant-"
            k.startsWith("AIza") -> "AIza"
            else -> k.substring(0, 3)
        }
        return head + "••••" + k.substring(k.length - 4)
    }

    fun describe(ctx: Context, spec: String): Info {
        val s = spec.trim()
        if (s.startsWith("AIza")) return Info("Google Gemini", "gemini", mask(s))
        if (s.startsWith("sk-ant")) return Info("Anthropic", "claude", mask(s))
        val p = s.split('|')
        if (p.size >= 2) {
            val name = when {
                p[0].contains("openrouter.ai") -> "OpenRouter"
                p[0].contains("groq.com") -> "Groq"
                else -> Tr.t(ctx, "Свой сервер", "Custom server", "O'z serveringiz")
            }
            val token = if (p.size > 2) p[2] else ""
            return Info(name, p[1], if (token.isEmpty()) p[0].replace(Regex("^https?://"), "") else mask(token))
        }
        return Info(Tr.t(ctx, "ИИ-ключ", "AI key", "AI kaliti"), "", mask(s))
    }

    /** Blocking: sends a tiny request through [spec]. Returns (ok, raw message). Call off the UI thread. */
    fun test(spec: String, ctx: Context): Pair<Boolean, String> = try {
        Brain(ctx).ping(spec); true to ""
    } catch (e: Exception) { false to (e.message ?: "error") }

    /** Blocking: model ids offered by an OpenAI-style server. Returns (ids or null, error message). */
    fun listModels(ctx: Context, baseIn: String, key: String): Pair<List<String>?, String> {
        val base = baseIn.trim().trimEnd('/')
        if (!base.startsWith("https://")) return null to Tr.t(ctx, "Только https-адрес", "Only https addresses are allowed", "Faqat https manzil mumkin")
        return try {
            val c = java.net.URL("$base/v1/models").openConnection() as java.net.HttpURLConnection
            c.connectTimeout = 15000; c.readTimeout = 20000
            if (key.isNotEmpty()) c.setRequestProperty("Authorization", "Bearer $key")
            val code = c.responseCode
            val body = (if (code in 200..299) c.inputStream else (c.errorStream ?: c.inputStream)).bufferedReader().use { it.readText() }
            if (code !in 200..299) null to "HTTP $code"
            else {
                val arr = JSONObject(body).getJSONArray("data")
                val skip = Regex("whisper|guard|orpheus|tts|embed|moderation|safeguard|allam|transcri", RegexOption.IGNORE_CASE)
                (0 until arr.length()).map { arr.getJSONObject(it).optString("id") }.filter { it.isNotEmpty() && !skip.containsMatchIn(it) } to ""
            }
        } catch (e: Exception) { null to (e.message ?: Tr.t(ctx, "ошибка сети", "network error", "tarmoq xatosi")) }
    }

    fun hint(ctx: Context, raw: String): String {
        val m = raw.lowercase()
        return when {
            listOf("401", "403", "unauthor", "invalid", "api key").any { m.contains(it) } ->
                Tr.t(ctx, "Ключ неверный или не подходит к этому сервису.", "The key is wrong or does not fit this service.", "Kalit noto'g'ri yoki bu xizmatga mos kelmaydi.")
            listOf("429", "quota", "limit").any { m.contains(it) } ->
                Tr.t(ctx, "Бесплатные запросы на сегодня закончились. Подождите или выберите другую модель.", "Free requests for today have run out. Wait or choose another model.", "Bugungi bepul so'rovlar tugadi. Kuting yoki boshqa modelni tanlang.")
            listOf("timeout", "timed out", "unknownhost", "unable to resolve", "connect", "network").any { m.contains(it) } ->
                Tr.t(ctx, "Нет связи с сервисом. Проверьте интернет и адрес.", "Cannot reach the service. Check your internet and the address.", "Xizmatga ulanib bo'lmadi. Internet va manzilni tekshiring.")
            m.contains("404") -> Tr.t(ctx, "Модель или адрес не найдены. Проверьте название модели.", "Model or address not found. Check the model name.", "Model yoki manzil topilmadi. Model nomini tekshiring.")
            else -> Tr.t(ctx, "Проверка не удалась.", "The check failed.", "Tekshirib bo'lmadi.")
        }
    }

    fun groqHint(ctx: Context, raw: String): String {
        val m = raw.lowercase()
        return when {
            listOf("401", "403", "unauthor", "invalid").any { m.contains(it) } ->
                Tr.t(ctx, "Ключ не подходит: скопируйте его заново целиком", "The key does not fit: copy it again in full", "Kalit mos kelmadi: uni qaytadan to'liq nusxalang")
            listOf("429", "quota", "limit").any { m.contains(it) } ->
                Tr.t(ctx, "Запросы на сегодня закончились, попробуйте позже", "Requests for today have run out, try again later", "Bugungi so'rovlar tugadi, keyinroq urinib ko'ring")
            listOf("timeout", "timed out", "unknownhost", "unable to resolve", "connect", "network").any { m.contains(it) } ->
                Tr.t(ctx, "Нет интернета", "No internet", "Internet yo'q")
            else -> hint(ctx, raw)
        }
    }

    const val GROQ_BASE = "https://api.groq.com/openai"

    /** Up to three Groq models, best first; with the app in Uzbek, Qwen (trained on many more languages) goes first. */
    fun pickGroqModels(all: List<String>, lang: String): List<String> {
        val ids = all.distinct()
        val out = mutableListOf<String>()
        fun add(m: String?) { if (m != null && m !in out && out.size < 3) out.add(m) }
        val newestQwen = ids.filter { it.startsWith("qwen/") }.sortedDescending().firstOrNull()
        if (lang == "uz") add(newestQwen)
        add(ids.firstOrNull { it.startsWith("openai/gpt-oss-120b") })
        add(newestQwen)
        add(ids.firstOrNull { it.startsWith("openai/gpt-oss-20b") })
        if (out.size < 3) {
            fun size(m: String): Int = Regex("(\\d+)b").find(m.lowercase())?.groupValues?.get(1)?.toInt() ?: 0
            fun pref(m: String) = if (Regex("llama|gpt-oss|qwen").containsMatchIn(m)) 1 else 0
            ids.filter { it !in out }.sortedWith { a, b -> val c = pref(b).compareTo(pref(a)); if (c != 0) c else size(b).compareTo(size(a)) }.forEach { add(it) }
        }
        return out
    }

    fun shortModel(id: String) = if (id.contains('/')) id.substringAfterLast('/') else id

    fun connectedText(ctx: Context, models: List<String>): String {
        val n = models.map { shortModel(it) }
        if (n.isEmpty()) return Tr.t(ctx, "Готово! Bubby подключён.", "Done! Bubby is connected.", "Tayyor! Bubby ulandi.")
        val backups = when (n.size) {
            1 -> ""
            2 -> Tr.t(ctx, ", ${n[1]} (запасная)", ", ${n[1]} (backup)", ", ${n[1]} (zaxira)")
            else -> Tr.t(ctx, ", ${n[1]} и ${n[2]} (запасные)", ", ${n[1]} and ${n[2]} (backups)", ", ${n[1]} va ${n[2]} (zaxira)")
        }
        return Tr.t(ctx,
            "Подключено: ${n.first()} (основная)$backups. У каждой отдельный дневной лимит — если одна закончится, Bubby сам переключится",
            "Connected: ${n.first()} (primary)$backups. Each has its own daily limit — if one runs out, Bubby switches by itself",
            "Ulandi: ${n.first()} (asosiy)$backups. Har birining alohida kunlik limiti bor — biri tugasa, Bubby o'zi almashadi")
    }
}
