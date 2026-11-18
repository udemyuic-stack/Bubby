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
    val target: String,
    val relations: Map<String, List<String>> = emptyMap()
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
    val context: String = "",
    val speak: String = ""
)

class Rel(val entry: Long, val pos: String, val text: String)

const val SLOT = "{}"

enum class Grade { CORRECT, CLOSE, WRONG }

object Exercises {
    private const val OPTIONS = 4
    private const val MIX = -7046029254386353131L

    private fun match(sentence: String, words: List<String>): IntRange? {
        for (w in words.sortedByDescending { it.length }) {
            val i = sentence.indexOf(w, ignoreCase = true)
            if (i < 0) continue
            val before = sentence.getOrNull(i - 1)
            val after = sentence.getOrNull(i + w.length)
            val cjk = w.any { it.code >= 0x2E80 }
            if (!cjk && ((before?.isLetter() == true) || (after?.isLetter() == true))) continue
            return i until i + w.length
        }
        return null
    }

    private fun swap(sentence: String, words: List<String>, with: (String) -> String): String? =
        match(sentence, words)?.let { r -> sentence.substring(0, r.first) + with(sentence.substring(r)) + sentence.substring(r.last + 1) }

    fun cloze(sentence: String, words: List<String>): String? = swap(sentence, words) { "____" }

    fun frame(phrase: String, words: List<String>): String? = swap(phrase, words) { SLOT }

    fun mark(sentence: String, words: List<String>): String? = swap(sentence, words) { "«$it»" }

    fun distractors(tiers: List<List<String>>, exclude: Collection<String>, n: Int, rnd: Random): List<String> {
        val banned = exclude.map { VocabText.normalize(it) }.toHashSet()
        val picked = ArrayList<String>()
        for (tier in tiers) {
            if (picked.size >= n) break
            val cands = tier.map { it.trim() }.filter { it.isNotEmpty() }
                .groupBy { VocabText.normalize(it) }
                .filterKeys { it.isNotEmpty() && it !in banned }
                .toSortedMap()
                .values.map { it.min() }
                .toMutableList()
            while (picked.size < n && cands.isNotEmpty()) {
                val c = cands.removeAt(rnd.nextInt(cands.size))
                picked.add(c)
                banned.add(VocabText.normalize(c))
            }
        }
        return picked
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

    fun build(type: String, card: Card, wordPool: List<String>, meaningPool: List<String>, seed: Long, frames: List<Rel> = emptyList(), synonyms: List<Rel> = emptyList()): Exercise? {
        val rnd = Random(seed)
        val trans = card.translations.filter { it.isNotBlank() }
        if (trans.isEmpty()) return null
        val main = trans.first()
        val word = card.lemma.ifBlank { card.original }
        return when (type) {
            "choice_meaning" -> options(main, meaningPool, rnd)?.let { Exercise(type, Dimension.RECOGNITION, Ask.CHOICE, card.original, "", it, trans, main) }
                ?: build("recall_meaning", card, wordPool, meaningPool, seed, frames, synonyms)
            "choice_word" -> options(card.original, wordPool, rnd)?.let { Exercise(type, Dimension.RECOGNITION, Ask.CHOICE, main, "", it, listOf(card.original), card.original) }
                ?: build("type_word", card, wordPool, meaningPool, seed, frames, synonyms)
            "recall_meaning" -> Exercise(type, Dimension.MEANING_RECALL, Ask.TYPED, card.original, card.pos, emptyList(), trans, trans.joinToString(" / "))
            "recall_definition" -> Exercise(type, Dimension.MEANING_RECALL, Ask.TYPED, card.definition, "", emptyList(), card.words(), card.original)
            "type_word" -> Exercise(type, Dimension.PRODUCTION, Ask.TYPED, main, card.pos, emptyList(), card.words(), card.original)
            "cloze" -> {
                val usable = card.examples.withIndex().mapNotNull { (i, e) -> cloze(e.text, card.words())?.let { Triple(i, it, e) } }
                if (usable.isEmpty()) return null
                val (i, text, e) = usable[rnd.nextInt(usable.size)]
                Exercise(type, Dimension.CONTEXT, Ask.TYPED, text, e.translation, emptyList(), card.words(), card.original, "ex$i")
            }
            "dictation" -> {
                if (card.source != "en") return null
                val rnd = Random(seed * MIX)
                val usable = card.examples.withIndex().mapNotNull { (i, e) -> cloze(e.text, card.words())?.let { Triple(i, it, e) } }
                if (usable.isNotEmpty() && rnd.nextBoolean()) {
                    val (i, text, e) = usable[rnd.nextInt(usable.size)]
                    Exercise(type, Dimension.LISTENING, Ask.TYPED, text, "", emptyList(), card.words(), card.original, "dict_ex$i", e.text)
                } else Exercise(type, Dimension.LISTENING, Ask.TYPED, "", "", emptyList(), card.words(), card.original, "dict_word", card.original)
            }
            "collocation" -> collocation(card, main, word, frames, Random(seed * MIX))
            "synonym_context" -> synonymContext(card, wordPool, synonyms, Random(seed * MIX))
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

    private fun collocation(card: Card, main: String, word: String, frames: List<Rel>, rnd: Random): Exercise? {
        val words = card.words()
        val cols = card.relations["collocation"].orEmpty().map { it.trim() }.filter { it.isNotEmpty() && frame(it, words) != null }
        if (cols.isEmpty()) return null
        val at = rnd.nextInt(cols.size)
        val correct = cols[at]
        val mistakes = card.relations["mistake"].orEmpty().filter { frame(it, words) != null }
        val others = frames.filter { it.entry != card.entry }
        val same = others.filter { it.pos == card.pos }.map { it.text.replace(SLOT, word) }
        val rest = others.filter { it.pos != card.pos }.map { it.text.replace(SLOT, word) }
        val wrong = distractors(listOf(mistakes, same, rest), cols, OPTIONS - 1, rnd)
        if (wrong.size < 2) return null
        val options = (wrong + correct).toMutableList().apply { shuffle(rnd) }
        return Exercise("collocation", Dimension.CONTEXT, Ask.CHOICE, card.original, main, options, cols, correct, "col$at")
    }

    private fun synonymContext(card: Card, wordPool: List<String>, synonyms: List<Rel>, rnd: Random): Exercise? {
        val words = card.words()
        val keys = words.map { VocabText.normalize(it) }.toSet()
        val syns = card.relations["synonym"].orEmpty().map { it.trim() }.filter { it.isNotEmpty() && VocabText.normalize(it) !in keys }
        if (syns.isEmpty()) return null
        val usable = card.examples.withIndex().mapNotNull { (i, e) -> mark(e.text, words)?.let { Triple(i, it, e) } }
        if (usable.isEmpty()) return null
        val (i, text, e) = usable[rnd.nextInt(usable.size)]
        val correct = syns[rnd.nextInt(syns.size)]
        val near = synonyms.filter { it.entry != card.entry && it.pos == card.pos }.map { it.text }
        val tiers = listOf(card.relations["antonym"].orEmpty(), card.relations["confusable"].orEmpty(), near, wordPool)
        val wrong = distractors(tiers, syns + words, OPTIONS - 1, rnd)
        if (wrong.size < 2) return null
        val options = (wrong + correct).toMutableList().apply { shuffle(rnd) }
        return Exercise("synonym_context", Dimension.CONTEXT, Ask.CHOICE, text, e.translation, options, syns, correct, "syn_ex$i")
    }

    fun supports(type: String, card: Card, wordPool: List<String>, meaningPool: List<String>, frames: List<Rel>, synonyms: List<Rel>): Boolean =
        build(type, card, wordPool, meaningPool, 0, frames, synonyms) != null

    fun passes(ex: Exercise, g: Grade): Boolean = if (ex.type == "dictation") g == Grade.CORRECT else g != Grade.WRONG

    fun alsoCorrect(ex: Exercise): List<String> {
        if (ex.ask != Ask.CHOICE) return emptyList()
        val shown = VocabText.normalize(ex.shown)
        return ex.accepted.filter { VocabText.normalize(it) != shown }.distinctBy { VocabText.normalize(it) }
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
