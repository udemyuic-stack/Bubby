package com.example.buddy.ui.vocab

import org.json.JSONArray
import org.json.JSONObject

object TutorMatch {
    private const val VOWELS = "aeiou"
    private const val APOS = "'ʻʼ’‘`´ʹ"

    private class Tok(val start: Int, val end: Int, val folded: String)

    private fun isTokenChar(c: Char) = c.isLetterOrDigit() || c == '-' || APOS.indexOf(c) >= 0

    private fun isApos(c: Char) = APOS.indexOf(c) >= 0

    private fun tokens(text: String): List<Tok> {
        val out = ArrayList<Tok>()
        var i = 0
        while (i < text.length) {
            if (!isTokenChar(text[i])) { i++; continue }
            var j = i
            while (j < text.length && isTokenChar(text[j])) j++
            var s = i
            var e = j
            while (s < e && (isApos(text[s]) || text[s] == '-')) s++
            if (e - s > 2 && isApos(text[e - 2]) && (text[e - 1] == 's' || text[e - 1] == 'S')) e -= 2
            while (e > s && (isApos(text[e - 1]) || text[e - 1] == '-')) e--
            if (e > s) out.add(Tok(s, e, VocabText.fold(text.substring(s, e))))
            i = j
        }
        return out
    }

    private fun consonant(c: Char) = c.isLetter() && VOWELS.indexOf(c) < 0

    fun forms(word: String): Set<String> {
        val w = VocabText.fold(word)
        if (w.length <= 2) return setOf(w)
        val out = LinkedHashSet<String>()
        out.add(w)
        for (s in listOf("s", "es", "ed", "d", "ing", "er", "est")) out.add(w + s)
        val last = w[w.length - 1]
        val stem = w.dropLast(1)
        if (last == 'y' && consonant(w[w.length - 2])) for (s in listOf("ies", "ied", "ier", "iest")) out.add(stem + s)
        if (last == 'e') for (s in listOf("ing", "er", "est")) out.add(stem + s)
        if (w.endsWith("ie")) out.add(w.dropLast(2) + "ying")
        if (consonant(last) && last !in "wxy" && VOWELS.indexOf(w[w.length - 2]) >= 0 && consonant(w[w.length - 3])) {
            for (s in listOf("ed", "ing", "er", "est")) out.add(w + last + s)
        }
        return out
    }

    fun find(text: String, target: String): List<IntRange> {
        val want = tokens(target).map { it.folded }
        if (want.isEmpty()) return emptyList()
        val have = tokens(text)
        val n = want.size
        val first = forms(want[0])
        val lastForms = if (n > 1) forms(want[n - 1]) else first
        val out = ArrayList<IntRange>()
        var i = 0
        while (i + n <= have.size) {
            var ok = true
            for (k in 0 until n) {
                val tok = have[i + k].folded
                val match = when {
                    k == 0 -> tok in first
                    k == n - 1 -> tok in lastForms
                    else -> tok == want[k]
                }
                if (!match) { ok = false; break }
            }
            if (ok) { out.add(have[i].start..(have[i + n - 1].end - 1)); i += n } else i++
        }
        return out
    }

    fun findAny(text: String, words: List<String>): List<IntRange> =
        words.flatMap { find(text, it) }.distinct().sortedBy { it.first }
}

class TutorQuestion(val type: String, val sense: Long, val prompt: String, val options: List<String>, val answer: Int, val why: String)

class TutorLesson(val kind: TutorKind, val title: String, val text: String, val hits: Map<Long, List<IntRange>>, val questions: List<TutorQuestion>)

sealed class TutorParsed {
    class Ok(val lesson: TutorLesson) : TutorParsed()
    class Bad(val reason: String, val missing: List<String> = emptyList()) : TutorParsed()
}

object TutorValidator {
    const val MIN_Q = 3
    const val MAX_Q = 5
    private const val MAX_TEXT = 2000
    private const val MIN_WORDS = 60
    private const val MAX_WORDS = 260
    private const val MAX_TITLE = 80
    private const val MAX_OPTION = 80
    private const val MAX_PROMPT = 300
    private const val MAX_WHY = 300
    private val BLANK = Regex("_{3,}")
    private val SPACES = Regex("\\s+")

    private fun extract(raw: String): JSONObject? {
        val a = raw.indexOf('{')
        val b = raw.lastIndexOf('}')
        if (a < 0 || b <= a) return null
        return try { JSONObject(raw.substring(a, b + 1)) } catch (_: Exception) { null }
    }

    private fun kindKey(kind: TutorKind) = if (kind == TutorKind.PASSAGE) "passage" else "dialogue"

    fun parse(raw: String, kind: TutorKind, targets: List<TutorTarget>): TutorParsed {
        val o = extract(raw) ?: return TutorParsed.Bad("the reply is not a JSON object")
        if (o.optString("kind", "").trim().lowercase() != kindKey(kind)) return TutorParsed.Bad("\"kind\" must be \"${kindKey(kind)}\"")
        val text = o.optString("text", "").trim()
        if (text.isEmpty()) return TutorParsed.Bad("\"text\" is empty")
        if (text.length > MAX_TEXT) return TutorParsed.Bad("\"text\" is too long")
        val words = text.split(SPACES).count { it.isNotEmpty() }
        if (words < MIN_WORDS || words > MAX_WORDS) return TutorParsed.Bad("\"text\" has $words words, it must have 120-220")
        val title = o.optString("title", "").trim()
        if (title.length > MAX_TITLE) return TutorParsed.Bad("\"title\" is too long")
        if (kind == TutorKind.PASSAGE && title.isEmpty()) return TutorParsed.Bad("\"title\" is empty")

        val hits = LinkedHashMap<Long, List<IntRange>>()
        val missing = ArrayList<String>()
        for (t in targets) {
            val r = TutorMatch.findAny(text, t.words)
            if (r.isEmpty()) missing.add(t.shown) else hits[t.sense] = r
        }
        if (missing.isNotEmpty()) return TutorParsed.Bad("missing", missing)

        val qs = ArrayList<TutorQuestion>()
        val arr = o.optJSONArray("questions") ?: JSONArray()
        for (i in 0 until arr.length()) {
            if (qs.size >= MAX_Q) break
            question(arr.optJSONObject(i) ?: continue, targets)?.let { qs.add(it) }
        }
        if (qs.size < MIN_Q) return TutorParsed.Bad("questions")
        return TutorParsed.Ok(TutorLesson(kind, title, text, hits, qs))
    }

    private fun question(q: JSONObject, targets: List<TutorTarget>): TutorQuestion? {
        val type = q.optString("type", "").trim().lowercase()
        if (type != "gap" && type != "read") return null
        val word = VocabText.fold(q.optString("word", ""))
        if (word.isEmpty()) return null
        val target = targets.firstOrNull { t -> t.words.any { VocabText.fold(it) == word } } ?: return null
        val a = q.optJSONArray("options") ?: return null
        if (a.length() < 3 || a.length() > 4) return null
        val options = ArrayList<String>()
        for (i in 0 until a.length()) {
            val s = (a.opt(i) as? String)?.trim() ?: return null
            if (s.isEmpty() || s.length > MAX_OPTION) return null
            options.add(s)
        }
        if (options.map { VocabText.fold(it) }.toSet().size != options.size) return null
        val ans = q.opt("answer") as? Number ?: return null
        val answer = ans.toInt()
        if (answer.toDouble() != ans.toDouble() || answer !in options.indices) return null
        var prompt = q.optString("q", "").trim()
        if (prompt.isEmpty() || prompt.length > MAX_PROMPT) return null
        if (type == "gap") {
            if (BLANK.findAll(prompt).count() != 1) return null
            prompt = prompt.replace(BLANK, "____")
            if (TutorMatch.findAny(options[answer], target.words).isEmpty()) return null
        }
        val why = q.optString("why", "").trim().take(MAX_WHY)
        return TutorQuestion(type, target.sense, prompt, options, answer, why)
    }
}

object TutorGen {
    fun run(ai: VocabAi, kind: TutorKind, targets: List<TutorTarget>, explainLang: String, topic: String?): TutorParsed {
        val system = TutorPrompt.system(kind, targets, explainLang, topic)
        var last: TutorParsed.Bad? = null
        for (attempt in 0..1) {
            val user = TutorPrompt.user(targets) + (last?.let { "\n\n" + TutorPrompt.fix(it.reason, it.missing) } ?: "")
            when (val p = TutorValidator.parse(ai.complete(system, user), kind, targets)) {
                is TutorParsed.Ok -> return p
                is TutorParsed.Bad -> last = p
            }
        }
        return last ?: TutorParsed.Bad("no reply")
    }
}
