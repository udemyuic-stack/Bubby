package com.example.buddy.ui

import org.json.JSONArray
import org.json.JSONObject

class GrammarAiResult(val questions: List<GrammarQ>, val rejected: Int)

object GrammarAiText {
    const val MAX_TEXT = 160
    const val MIN_VALID = 5

    val levels = listOf("A2", "B1", "B2", "C1")

    fun system(): String = """You write English grammar exercises for learners. Reply with JSON only, no markdown.
Format: {"questions":[ ... 10 items ... ]}
Item types:
{"type":"choose","text":"sentence with ___ for the gap","options":["4 different options, one is the answer"],"answer":"the right option","why":"short rule"}
{"type":"mistake","text":"sentence with exactly one wrong word","index":0,"fix":"the correct word or words","why":"short rule"}
{"type":"order","text":"a correct sentence of 3 to 14 words","why":"short rule"}
{"type":"type","text":"sentence with ___ and the base verb in brackets","answers":["accepted answers"],"why":"short rule"}
index counts words split by spaces from 0. Every text is at most $MAX_TEXT characters. Mix the types: 5 choose, 2 mistake, 2 order, 1 type."""

    fun user(topic: String, level: String): String = "Topic: $topic. CEFR level: $level. Write 10 exercises."

    fun parse(reply: String): GrammarAiResult? {
        val start = reply.indexOf('{')
        val end = reply.lastIndexOf('}')
        if (start < 0 || end <= start) return null
        val arr: JSONArray = try {
            JSONObject(reply.substring(start, end + 1)).optJSONArray("questions") ?: return null
        } catch (e: Exception) {
            return null
        }
        val out = ArrayList<GrammarQ>()
        var bad = 0
        for (i in 0 until arr.length()) {
            val q = arr.optJSONObject(i)?.let { item(it, out.size) }
            if (q == null) bad++ else out.add(q)
        }
        return if (out.size < MIN_VALID) null else GrammarAiResult(out, bad)
    }

    private fun strings(a: JSONArray?): List<String> {
        if (a == null) return emptyList()
        return (0 until a.length()).map { a.optString(it, "").trim() }
    }

    private fun item(o: JSONObject, n: Int): GrammarQ? {
        val text = o.optString("text", "").trim().replace(Regex("\\s+"), " ")
        val why = o.optString("why", "").trim().take(MAX_TEXT)
        if (text.isEmpty() || text.length > MAX_TEXT) return null
        val id = "${GrammarWeak.AI_TOPIC}.$n"
        return when (o.optString("type", "")) {
            "choose" -> {
                val opts = strings(o.optJSONArray("options"))
                val answer = o.optString("answer", "").trim()
                val ok = "___" in text && opts.size == 4 && opts.all { it.isNotEmpty() && it.length <= MAX_TEXT } &&
                    opts.map { GrammarCheck.norm(it) }.distinct().size == 4 && answer in opts
                if (ok) GrammarQ(id, GrammarWeak.AI_TOPIC, GKind.CHOOSE, text, listOf(answer), listOf(answer) + opts.filter { it != answer }, -1, why) else null
            }
            "mistake" -> {
                val words = text.split(" ")
                val idx = if (o.has("index")) o.optInt("index", -1) else -1
                val fix = o.optString("fix", "").trim()
                val ok = idx in words.indices && fix.isNotEmpty() && fix.length <= MAX_TEXT &&
                    GrammarCheck.norm(fix) != GrammarCheck.norm(words[idx])
                if (ok) GrammarQ(id, GrammarWeak.AI_TOPIC, GKind.MISTAKE, text, listOf(fix), emptyList(), idx, why) else null
            }
            "order" -> {
                val count = text.split(" ").size
                if (count in 3..14) GrammarQ(id, GrammarWeak.AI_TOPIC, GKind.ORDER, text, listOf(text), emptyList(), -1, why) else null
            }
            "type" -> {
                val answers = strings(o.optJSONArray("answers")).filter { it.isNotEmpty() && it.length <= MAX_TEXT }
                if ("___" in text && answers.isNotEmpty()) GrammarQ(id, GrammarWeak.AI_TOPIC, GKind.TYPE, text, answers, emptyList(), -1, why) else null
            }
            else -> null
        }
    }
}
