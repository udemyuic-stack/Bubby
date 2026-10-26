package com.example.buddy

import android.text.Html
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** Fetches a few web results (title + snippet) so any model, even a local one, can answer fresh questions. */
object WebSearch {
    private val link = Regex("""<a[^>]*class="result__a"[^>]*>(.*?)</a>""", RegexOption.DOT_MATCHES_ALL)
    private val snippet = Regex("""<a[^>]*class="result__snippet"[^>]*>(.*?)</a>""", RegexOption.DOT_MATCHES_ALL)

    private fun clean(s: String) = Html.fromHtml(s, Html.FROM_HTML_MODE_LEGACY).toString().replace(Regex("\\s+"), " ").trim()

    /** Returns up to 5 "title — snippet" lines, capped at ~1500 chars; throws on network errors. */
    fun run(query: String, ctx: android.content.Context? = null): String {
        fun t(ru: String, en: String, uz: String) = if (ctx == null) ru else Tr.t(ctx, ru, en, uz)
        val q = query.trim().take(200)
        if (q.isEmpty()) throw Exception(t("пустой запрос", "empty query", "bo'sh so'rov"))
        val c = URL("https://html.duckduckgo.com/html/?q=" + URLEncoder.encode(q, "UTF-8")).openConnection() as HttpURLConnection
        c.connectTimeout = 8000; c.readTimeout = 8000
        c.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 14) ClaudeBuddy")
        if (c.responseCode !in 200..299) throw Exception(t("поиск недоступен (HTTP ${c.responseCode})", "search unavailable (HTTP ${c.responseCode})", "qidiruv mavjud emas (HTTP ${c.responseCode})"))
        val html = c.inputStream.bufferedReader().use { it.readText() }
        val titles = link.findAll(html).map { clean(it.groupValues[1]) }.toList()
        val snips = snippet.findAll(html).map { clean(it.groupValues[1]) }.toList()
        val out = StringBuilder()
        for (i in titles.indices.take(5)) {
            val s = snips.getOrNull(i).orEmpty()
            out.append("- ").append(titles[i]); if (s.isNotEmpty()) out.append(" — ").append(s); out.append('\n')
            if (out.length > 1500) break
        }
        if (out.isEmpty()) throw Exception(t("ничего не найдено", "nothing found", "hech narsa topilmadi"))
        return out.toString().take(1600)
    }
}
