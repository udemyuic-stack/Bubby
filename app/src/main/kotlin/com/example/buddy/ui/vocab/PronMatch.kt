package com.example.buddy.ui.vocab

object PronMatch {
    private val SPLIT = Regex("[|\\n]")
    private val NON_WORD = Regex("[^\\p{L}\\p{N}]+")

    fun matches(word: String, transcript: String): Boolean {
        val w = tokens(word)
        if (w.isEmpty()) return false
        for (h in transcript.split(SPLIT)) {
            val t = tokens(h)
            if (t.size < w.size) continue
            for (i in 0..t.size - w.size) {
                if (t.subList(i, i + w.size) == w) return true
            }
        }
        return false
    }

    private fun tokens(s: String): List<String> =
        VocabText.fold(s).replace("'", "").replace(NON_WORD, " ").trim().split(' ').filter { it.isNotEmpty() }
}
