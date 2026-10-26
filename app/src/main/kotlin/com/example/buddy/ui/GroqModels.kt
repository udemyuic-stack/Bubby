package com.example.buddy.ui

/** Chooses which of the models a Groq key can use Bubby should connect (pure logic, tested in qa-jvm). */
object GroqModels {
    /** Up to three models, best first; with the app in Uzbek, Qwen (trained on many more languages) goes first. */
    fun pick(all: List<String>, lang: String): List<String> {
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
}
