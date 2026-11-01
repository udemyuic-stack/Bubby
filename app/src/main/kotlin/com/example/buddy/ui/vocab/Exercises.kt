package com.example.buddy.ui.vocab

import java.util.Random
import kotlin.math.max
import kotlin.math.min

class Card(
    val entry: Long,
    val sense: Long,
    val original: String,
    val lemma: String,
    val ipa: String,
    val reading: String,
    val pos: String,
    val definition: String,
    val translations: List<String>,
    val examples: List<ExampleData>,
    val meta: Map<String, String>,
    val source: String,
    val target: String
) {
    fun words(): List<String> = listOf(original, lemma).map { it.trim() }.filter { it.isNotEmpty() }.distinct()
}

enum class Ask { CHOICE, TYPED, SENTENCE }

class Exercise(
    val type: String,
    val dim: Dimension,
    val ask: Ask,
    val question: String,
    val prompt: String,
    val options: List<String>,
    val accepted: List<String>,
    val shown: String,
    val context: String = ""
)

enum class Grade { CORRECT, CLOSE, WRONG }

object Exercises {
    private const val OPTIONS = 4

    fun cloze(sentence: String, words: List<String>): String? {
        for (w in words.sortedByDescending { it.length }) {
            val i = sentence.indexOf(w, ignoreCase = true)
            if (i < 0) continue
            val before = sentence.getOrNull(i - 1)
            val after = sentence.getOrNull(i + w.length)
            val cjk = w.any { it.code >= 0x2E80 }
            if (!cjk && ((before?.isLetter() == true) || (after?.isLetter() == true))) continue
            return sentence.substring(0, i) + "____" + sentence.substring(i + w.length)
        }
        return null
    }

    private fun options(correct: String, pool: List<String>, rnd: Random): List<String>? {
        val key = VocabText.normalize(correct)
        val others = pool.map { it.trim() }.filter { it.isNotEmpty() && VocabText.normalize(it) != key }.distinctBy { VocabText.normalize(it) }.toMutableList()
        if (others.isEmpty()) return null
        val picked = ArrayList<String>()
        while (picked.size < OPTIONS - 1 && others.isNotEmpty()) picked.add(others.removeAt(rnd.nextInt(others.size)))
        picked.add(correct)
        picked.shuffle(rnd)
        return picked
    }

    fun build(type: String, card: Card, wordPool: List<String>, meaningPool: List<String>, seed: Long): Exercise? {
        val rnd = Random(seed)
        val trans = card.translations.filter { it.isNotBlank() }
        if (trans.isEmpty()) return null
        val main = trans.first()
        val word = card.lemma.ifBlank { card.original }
        return when (type) {
            "choice_meaning" -> options(main, meaningPool, rnd)?.let { Exercise(type, Dimension.RECOGNITION, Ask.CHOICE, card.original, "", it, trans, main) }
                ?: build("recall_meaning", card, wordPool, meaningPool, seed)
            "choice_word" -> options(card.original, wordPool, rnd)?.let { Exercise(type, Dimension.RECOGNITION, Ask.CHOICE, main, "", it, listOf(card.original), card.original) }
                ?: build("type_word", card, wordPool, meaningPool, seed)
            "recall_meaning" -> Exercise(type, Dimension.MEANING_RECALL, Ask.TYPED, card.original, card.pos, emptyList(), trans, trans.joinToString(" / "))
            "recall_definition" -> Exercise(type, Dimension.MEANING_RECALL, Ask.TYPED, card.definition, "", emptyList(), card.words(), card.original)
            "type_word" -> Exercise(type, Dimension.PRODUCTION, Ask.TYPED, main, card.pos, emptyList(), card.words(), card.original)
            "cloze" -> {
                val usable = card.examples.withIndex().mapNotNull { (i, e) -> cloze(e.text, card.words())?.let { Triple(i, it, e) } }
                if (usable.isEmpty()) return null
                val (i, text, e) = usable[rnd.nextInt(usable.size)]
                Exercise(type, Dimension.CONTEXT, Ask.TYPED, text, e.translation, emptyList(), card.words(), card.original, "ex$i")
            }
            "own_sentence" -> Exercise(type, Dimension.SENTENCE, Ask.SENTENCE, card.original, main, emptyList(), emptyList(), card.original, "own")
            "lang_drill" -> {
                val art = card.meta["article"]
                when {
                    art != null && card.source == "de" -> Exercise(type, Dimension.LANGUAGE, Ask.CHOICE, word, "", listOf("der", "die", "das"), listOf(art.trim().lowercase()), art.trim().lowercase())
                    card.reading.isNotBlank() -> Exercise(type, Dimension.LANGUAGE, Ask.TYPED, card.original, "", emptyList(), listOf(card.reading), card.reading)
                    else -> null
                }
            }
            else -> null
        }
    }

    private fun lev(a: String, b: String): Int {
        var prev = IntArray(b.length + 1) { it }
        for (i in 1..a.length) {
            val cur = IntArray(b.length + 1)
            cur[0] = i
            for (j in 1..b.length) cur[j] = min(min(cur[j - 1] + 1, prev[j] + 1), prev[j - 1] + if (a[i - 1] == b[j - 1]) 0 else 1)
            prev = cur
        }
        return prev[b.length]
    }

    fun grade(ex: Exercise, input: String): Grade {
        val given = VocabText.normalize(input)
        if (given.isEmpty()) return Grade.WRONG
        if (ex.ask == Ask.CHOICE) return if (ex.accepted.any { VocabText.normalize(it) == given }) Grade.CORRECT else Grade.WRONG
        var best = Grade.WRONG
        for (a in ex.accepted) {
            val n = VocabText.normalize(a)
            if (n == given) return Grade.CORRECT
            if (VocabText.fold(a) == VocabText.fold(input)) { best = Grade.CLOSE; continue }
            val cjk = n.any { it.code >= 0x2E80 }
            val allowed = if (cjk) 0 else if (n.length >= 9) 2 else if (n.length >= 5) 1 else 0
            if (allowed > 0 && lev(VocabText.fold(a), VocabText.fold(input)) <= allowed) best = Grade.CLOSE
        }
        return best
    }

    fun isHighConfidenceMiss(ok: Boolean, c: Confidence) = !ok && c == Confidence.SURE
}

class Verdict(val ok: Boolean, val why: String)

object Judge {
    fun system(card: Card, target: String): String =
        "You check a language learner's sentence. The learner studies the word \"" + card.original.replace("\"", "'") + "\" (language " + card.source + ", meaning: " +
            card.translations.joinToString(", ") + "). Decide if the sentence uses the word correctly in meaning and grammar. " +
            "Reply with ONE JSON object and nothing else: {\"ok\":true or false,\"why\":\"one short sentence in language code $target\"}. " +
            "Ignore small typos if the word is used correctly. If the sentence does not contain the word, ok is false."

    fun parse(raw: String): Verdict? {
        val a = raw.indexOf('{')
        val b = raw.lastIndexOf('}')
        if (a < 0 || b <= a) return null
        return try {
            val o = org.json.JSONObject(raw.substring(a, b + 1))
            if (!o.has("ok") || o.opt("ok") !is Boolean) null else Verdict(o.getBoolean("ok"), o.optString("why", "").trim().take(240))
        } catch (_: Exception) { null }
    }

    fun containsWord(sentence: String, card: Card): Boolean {
        val s = VocabText.fold(sentence)
        return card.words().any { s.contains(VocabText.fold(it)) }
    }
}
