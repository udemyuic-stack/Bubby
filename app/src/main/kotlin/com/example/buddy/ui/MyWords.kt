package com.example.buddy.ui

import org.json.JSONArray
import org.json.JSONObject
import java.util.Random
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

enum class WordState { NEW, LEARNING, REVIEWING, ALMOST, MASTERED }

enum class Recall { SURE, UNSURE, WRONG, UNCHECKED }

class Review(val day: Long, val type: Char, val res: Int, val ms: Int)

class MyWord(
    var en: String,
    var uz: String = "",
    var definition: String = "",
    var example: String = "",
    var mine: String = "",
    val added: Long,
    var step: Int = 0,
    var ease: Double = 2.0,
    var due: Long = added,
    val reviews: MutableList<Review> = ArrayList()
)

class Question(val word: MyWord, val type: Char, val retry: Boolean = false)

object MyWords {
    val BASE = intArrayOf(1, 3, 7, 14, 30)
    const val MAX_REVIEWS = 20
    const val SESSION_DUE = 8
    const val SESSION_HARD = 3
    const val SESSION_NEW = 3
    const val SESSION_MIX = 3

    fun gap(step: Int, ease: Double): Int {
        val base = BASE[min(max(step, 1), BASE.size) - 1]
        return max(1, (base * ease / 2.0).roundToInt())
    }

    fun apply(w: MyWord, outcome: Recall, type: Char, today: Long, ms: Int, retry: Boolean = false) {
        val res = when (outcome) { Recall.WRONG -> 0; Recall.UNCHECKED -> 2; else -> 1 }
        w.reviews.add(Review(today, type, res, ms))
        while (w.reviews.size > MAX_REVIEWS) w.reviews.removeAt(0)
        if (retry || outcome == Recall.UNCHECKED) {
            if (w.reviews.size == 1 && w.due < today) w.due = today
            return
        }
        when (outcome) {
            Recall.SURE -> {
                w.step = min(BASE.size, w.step + 1)
                w.ease = min(2.5, w.ease + 0.1)
                w.due = today + gap(w.step, w.ease)
            }
            Recall.UNSURE -> {
                w.due = today + gap(w.step, w.ease)
            }
            else -> {
                w.step = max(0, w.step - 2)
                w.ease = max(1.3, w.ease - 0.2)
                w.due = today + 1
            }
        }
    }

    fun state(w: MyWord): WordState {
        if (w.reviews.isEmpty()) return WordState.NEW
        return when {
            w.step <= 2 -> WordState.LEARNING
            w.step == 3 -> WordState.REVIEWING
            w.step == 4 -> WordState.ALMOST
            else -> {
                val good = w.reviews.filter { it.res == 1 && it.type != 'D' }
                if (good.map { it.day }.toSet().size >= 2 && good.map { it.type }.toSet().size >= 2) WordState.MASTERED else WordState.ALMOST
            }
        }
    }

    fun isDue(w: MyWord, today: Long) = w.reviews.isNotEmpty() && w.due <= today

    fun missRate(w: MyWord): Double {
        val checked = w.reviews.filter { it.res != 2 }
        if (checked.isEmpty()) return 0.0
        return checked.count { it.res == 0 }.toDouble() / checked.size
    }

    fun accuracy(words: List<MyWord>, today: Long, days: Int = 30): Int? {
        var ok = 0
        var all = 0
        for (w in words) for (r in w.reviews) {
            if (r.res == 2 || r.type == 'D' || today - r.day > days) continue
            all++
            if (r.res == 1) ok++
        }
        return if (all == 0) null else (ok * 100.0 / all).roundToInt()
    }

    fun hardest(words: List<MyWord>, n: Int = 5): List<MyWord> =
        words.filter { it.reviews.count { r -> r.res != 2 } >= 2 && missRate(it) > 0.0 }
            .sortedByDescending { missRate(it) }.take(n)

    fun normalize(s: String): String =
        s.lowercase().replace(Regex("[ʻʼ’‘`´ʹ]"), "'").replace('ё', 'е')
            .replace(Regex("[^\\p{L}\\p{N}'\\s-]"), " ").replace(Regex("\\s+"), " ").trim()

    fun lev(a: String, b: String): Int {
        if (a == b) return 0
        var prev = IntArray(b.length + 1) { it }
        for (i in 1..a.length) {
            val cur = IntArray(b.length + 1)
            cur[0] = i
            for (j in 1..b.length) {
                cur[j] = min(min(cur[j - 1] + 1, prev[j] + 1), prev[j - 1] + if (a[i - 1] == b[j - 1]) 0 else 1)
            }
            prev = cur
        }
        return prev[b.length]
    }

    fun check(answer: String, target: String): Int {
        val a = normalize(answer)
        val t = normalize(target)
        if (a.isEmpty() || t.isEmpty()) return 0
        if (a == t) return 2
        if (t.length >= 5 && lev(a, t) == 1) return 1
        return 0
    }

    fun cloze(sentence: String, word: String): String? {
        if (sentence.isBlank() || word.isBlank()) return null
        val rx = Regex("(?<![\\p{L}])" + Regex.escape(word.trim()) + "[\\p{L}]*", RegexOption.IGNORE_CASE)
        if (!rx.containsMatchIn(sentence)) return null
        return rx.replace(sentence, "_____")
    }

    private val STOP = setOf("the", "and", "that", "this", "with", "from", "have", "been", "which", "when", "what", "into", "your", "their", "there", "about", "being", "used", "very", "such", "some", "than", "then", "them", "they", "were", "will", "would", "something", "someone")

    fun meaningMatch(answer: String, w: MyWord): Boolean? {
        val a = normalize(answer)
        if (a.length < 2) return false
        val variants = w.uz.split(',', ';', '/').map { normalize(it) }.filter { it.length >= 2 }
        if (variants.any { a == it || (it.length >= 4 && a.contains(it)) }) return true
        val keys = normalize(w.definition).split(' ').filter { it.length >= 4 && it !in STOP }.toSet()
        if (keys.isNotEmpty()) {
            val words = a.split(' ').toSet()
            if (keys.count { it in words } >= min(2, keys.size)) return true
        }
        return null
    }

    fun available(w: MyWord, aiOn: Boolean): List<Char> {
        val l = ArrayList<Char>()
        val hasMeaning = w.uz.isNotBlank() || w.definition.isNotBlank()
        if (w.uz.isNotBlank()) l.add('A')
        l.add('B')
        if (cloze(w.example, w.en) != null || cloze(w.mine, w.en) != null) l.add('C')
        if (w.uz.isNotBlank()) l.add('D')
        if (aiOn) {
            l.add('E')
            if (hasMeaning) l.add('F')
        }
        return l
    }

    fun pickType(w: MyWord, prev: Char?, aiOn: Boolean, rnd: Random, avoid: Char? = null): Char {
        val all = available(w, aiOn)
        val last = w.reviews.lastOrNull()?.type
        var pool = all.filter { it != prev && it != last && it != avoid }
        if (pool.isEmpty()) pool = all.filter { it != prev && it != avoid }
        if (pool.isEmpty()) pool = all.filter { it != avoid }
        if (pool.isEmpty()) pool = all
        val nonD = pool.filter { it != 'D' }
        if (nonD.isNotEmpty() && (pool.size == nonD.size || rnd.nextInt(4) != 0)) return nonD[rnd.nextInt(nonD.size)]
        return pool[rnd.nextInt(pool.size)]
    }

    fun buildSession(words: List<MyWord>, today: Long, aiOn: Boolean, rnd: Random, practiceAnyway: Boolean = false): List<Question> {
        val chosen = LinkedHashSet<MyWord>()
        words.filter { isDue(it, today) }.sortedBy { it.due }.take(SESSION_DUE).forEach { chosen.add(it) }
        words.filter { it !in chosen && it.reviews.size >= 3 && missRate(it) > 0.4 }.sortedByDescending { missRate(it) }.take(SESSION_HARD).forEach { chosen.add(it) }
        words.filter { it.reviews.isEmpty() }.take(SESSION_NEW).forEach { chosen.add(it) }
        if (chosen.isNotEmpty()) {
            words.filter { it !in chosen }.shuffled(rnd).take(SESSION_MIX).forEach { chosen.add(it) }
        } else if (practiceAnyway) {
            words.shuffled(rnd).take(10).forEach { chosen.add(it) }
        }
        val order = chosen.toList().shuffled(rnd)
        val out = ArrayList<Question>()
        var prev: Char? = null
        for (w in order) {
            val type = pickType(w, prev, aiOn, rnd)
            out.add(Question(w, type))
            prev = type
        }
        return out
    }

    fun distractors(correct: MyWord, words: List<MyWord>, rnd: Random, n: Int = 3): List<String> {
        val own = words.filter { it !== correct && it.uz.isNotBlank() && !it.uz.equals(correct.uz, true) }.map { it.uz.trim() }
        val builtIn = EnglishWords.all.map { it.uz }.filter { !it.equals(correct.uz.trim(), true) && it !in own }
        val out = ArrayList<String>()
        out.addAll(own.distinct().shuffled(rnd).take(n))
        if (out.size < n) out.addAll(builtIn.shuffled(rnd).take(n - out.size))
        return out
    }

    fun add(words: MutableList<MyWord>, en: String, uz: String, definition: String, example: String, today: Long): MyWord? {
        val e = en.trim().replace(Regex("\\s+"), " ")
        if (e.isEmpty() || e.length > 60) return null
        if (words.any { it.en.equals(e, true) }) return null
        val w = MyWord(e, uz.trim(), definition.trim(), example.trim(), "", today)
        words.add(w)
        return w
    }

    fun toJson(words: List<MyWord>): String {
        val arr = JSONArray()
        for (w in words) {
            val o = JSONObject()
            o.put("en", w.en).put("uz", w.uz).put("def", w.definition).put("ex", w.example).put("mine", w.mine)
            o.put("added", w.added).put("step", w.step).put("ease", w.ease).put("due", w.due)
            val r = JSONArray()
            for (x in w.reviews) r.put(JSONArray().put(x.day).put(x.type.toString()).put(x.res).put(x.ms))
            o.put("rev", r)
            arr.put(o)
        }
        return arr.toString()
    }

    fun fromJson(s: String): MutableList<MyWord> {
        val out = ArrayList<MyWord>()
        try {
            val arr = JSONArray(s)
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val revs = ArrayList<Review>()
                val r = o.optJSONArray("rev")
                if (r != null) for (j in 0 until r.length()) {
                    val x = r.getJSONArray(j)
                    revs.add(Review(x.getLong(0), x.getString(1).firstOrNull() ?: 'A', x.getInt(2), x.optInt(3)))
                }
                out.add(MyWord(o.getString("en"), o.optString("uz"), o.optString("def"), o.optString("ex"), o.optString("mine"),
                    o.optLong("added"), o.optInt("step"), o.optDouble("ease", 2.0), o.optLong("due"), revs))
            }
        } catch (_: Exception) { }
        return out
    }

    fun parseVerdict(raw: String): Pair<Boolean, String>? {
        val m = Regex("\\{[^{}]*}", RegexOption.DOT_MATCHES_ALL).find(raw) ?: return null
        return try {
            val o = JSONObject(m.value)
            if (!o.has("ok")) null else o.getBoolean("ok") to o.optString("why", "")
        } catch (_: Exception) { null }
    }
}

object MyWordsGrammar {
    private val EN = listOf(
        Regex("^(?:add|save|put|store) (?:word |words )?(.+?) (?:to|in|into|on) (?:word list|words|vocabulary|vocab|dictionary|wordlist)$"),
        Regex("^(?:save|add|remember) (?:the )?words? (.+)$")
    )
    private val RU = listOf(
        Regex("^(?:добавь|добавить|запиши|сохрани|положи) (?:слово )?(.+?) (?:в|во) (?:мои )?(?:слова|словарь|словарик|список слов|словарный запас)$"),
        Regex("^(?:сохрани|запомни|добавь|запиши) слово (.+)$")
    )
    private val UZ = listOf(
        Regex("^(.+?) (?:ni )?(?:so'zlar(?:im)?ga|lug'at(?:im)?ga|lugat(?:im)?ga|so'zlar ro'yxat(?:im|i)?ga|ro'yxatga) (?:qo'shib qo'y|qo'shvor|qo'shing|qo'sh|saqlab qo'y|saqla|yoz)$"),
        Regex("^(?:so'zlar(?:im)?ga|lug'at(?:im)?ga|lugat(?:im)?ga) (.+?) (?:ni )?(?:qo'shib qo'y|qo'shvor|qo'shing|qo'sh|saqla|yoz)$"),
        Regex("^(.+?) so'zini (?:saqlab qo'y|saqla|qo'shib qo'y|qo'sh|yodla|yozib qo'y|yozib ol)$")
    )
    private val WORD = Regex("^\\p{L}[\\p{L}'-]*$")

    fun parse(t: String): String? {
        for (rx in EN + RU + UZ) {
            val m = rx.find(t) ?: continue
            val w = m.groupValues[1].trim()
            val parts = w.split(' ').filter { it.isNotEmpty() }
            if (parts.isEmpty() || parts.size > 3 || w.length > 40) continue
            if (parts.any { !WORD.matches(it) }) continue
            return w
        }
        return null
    }
}
