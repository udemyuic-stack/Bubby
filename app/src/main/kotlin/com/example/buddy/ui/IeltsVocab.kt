package com.example.buddy.ui

import kotlin.random.Random

class IeltsWord(val topic: String, val word: String, val uz: String, val example: String, val exUz: String = "") {
    val id: String get() = "$topic:$word"

    fun gap(): String {
        val i = example.indexOf(word, ignoreCase = true)
        return if (i < 0) example else example.replaceRange(i, i + word.length, "_____")
    }

    val hasGap: Boolean get() = example.contains(word, ignoreCase = true)
}

class IeltsDef(val topic: String, val word: String, val def: String, val uz: String = "") {
    val short: String get() = def.substringBefore(';').let { if (it.length > 90) it.take(87).trimEnd() + "..." else it }
}

class IeltsPhrase(val group: String, val phrase: String, val uz: String, val example: String)

class IeltsStruct(val n: Int, val title: String, val uz: String, val formula: String, val markers: List<List<String>>, val examples: List<String>) {
    val answer: String get() = markers.joinToString(" … ") { it.first() }

    fun gap(example: String): Pair<String, String>? {
        if (markers.isEmpty()) return null
        var out = example
        var from = 0
        val parts = ArrayList<String>()
        for (slot in markers) {
            val hit = slot.mapNotNull { alt ->
                Regex("(?<![\\w'])" + Regex.escape(alt) + "(?![\\w'])", RegexOption.IGNORE_CASE).find(out, from)
            }.minByOrNull { it.range.first } ?: return null
            parts.add(hit.value.lowercase())
            out = out.replaceRange(hit.range, "_____")
            from = hit.range.first + 5
        }
        return out to parts.joinToString(" … ")
    }

    fun gaps(): List<Pair<String, String>> = examples.mapNotNull { gap(it) }
}

class IeltsCue(val title: String, val bullets: List<String>, val topic: String)

class IeltsAsk(val topic: String, val question: String)

class IeltsSpeaking(val phrases: List<IeltsPhrase>, val words: List<IeltsWord>, val part1: List<IeltsAsk>, val cues: List<IeltsCue>, val part3: List<IeltsAsk>)

class IeltsQuestion(val prompt: String, val options: List<String>, val answer: Int, val explain: String)

object IeltsVocab {
    val topicOrder = listOf("environment", "technology", "health", "crime", "education", "work", "tourism", "city", "society")

    fun parseWords(text: String): List<IeltsWord> = text.lineSequence().mapNotNull { l ->
        val f = l.split("|")
        if (f.size < 4 || f[1].isBlank() || f[2].isBlank()) null else IeltsWord(f[0].trim(), f[1].trim(), f[2].trim(), f[3].trim(), f.getOrNull(4)?.trim().orEmpty())
    }.toList()

    val defOrder = listOf("education", "environment", "food", "globalization", "technology", "health", "crime", "art")

    fun parseDefs(text: String): List<IeltsDef> = text.lineSequence().mapNotNull { l ->
        val f = l.split("|")
        if (f.size < 3 || f[1].isBlank() || f[2].isBlank()) null else IeltsDef(f[0].trim(), f[1].trim(), f[2].trim(), f.getOrNull(3)?.trim().orEmpty())
    }.toList()

    fun defsByTopic(defs: List<IeltsDef>): Map<String, List<IeltsDef>> {
        val g = defs.groupBy { it.topic }
        return defOrder.filter { g.containsKey(it) }.associateWith { g.getValue(it) }
    }

    fun quizDefs(pool: List<IeltsDef>, all: List<IeltsDef>, rnd: Random, size: Int): List<IeltsQuestion> =
        pool.shuffled(rnd).take(size).mapNotNull { d ->
            val others = all.filter { it.word != d.word }.map { it.short }.filter { it != d.short }.distinct().shuffled(rnd).take(3)
            if (others.size < 3) null else {
                val opts = (others + d.short).shuffled(rnd)
                IeltsQuestion(d.word, opts, opts.indexOf(d.short), d.def)
            }
        }

    val speakOrder = listOf("hometown", "home", "work-study", "free time", "family-friends", "travel", "food", "technology", "shopping", "weather", "sport-health", "arts-media", "people")

    fun parseSpeaking(text: String): IeltsSpeaking {
        val ph = ArrayList<IeltsPhrase>()
        val w = ArrayList<IeltsWord>()
        val p1 = ArrayList<IeltsAsk>()
        val cues = ArrayList<IeltsCue>()
        val p3 = ArrayList<IeltsAsk>()
        for (l in text.lineSequence()) {
            val f = l.split("|").map { it.trim() }
            when {
                f[0] == "P" && f.size >= 5 && f[2].isNotEmpty() -> ph.add(IeltsPhrase(f[1], f[2], f[3], f[4]))
                f[0] == "W" && f.size >= 5 && f[2].isNotEmpty() && f[3].isNotEmpty() -> w.add(IeltsWord(f[1], f[2], f[3], f[4]))
                f[0] == "Q" && f.size >= 3 && f[2].isNotEmpty() -> p1.add(IeltsAsk(f[1], f[2]))
                f[0] == "C" && f.size >= 4 && f[1].isNotEmpty() -> cues.add(IeltsCue(f[1], f[2].split(";").map { it.trim() }.filter { it.isNotEmpty() }, f[3]))
                f[0] == "T" && f.size >= 3 && f[2].isNotEmpty() -> p3.add(IeltsAsk(f[1], f[2]))
            }
        }
        return IeltsSpeaking(ph, w, p1, cues, p3)
    }

    val collocOrder = listOf("education", "crime", "health", "environment", "technology", "work", "globalization", "transport", "media", "culture", "government", "social", "art", "sport", "science", "family", "city", "tourism", "population", "food", "housing", "climate", "economy", "immigration", "space")

    fun collocByTopic(words: List<IeltsWord>): Map<String, List<IeltsWord>> {
        val g = words.groupBy { it.topic }
        return collocOrder.filter { g.containsKey(it) }.associateWith { g.getValue(it) }
    }

    fun parseIdioms(text: String): List<IeltsPhrase> = text.lineSequence().mapNotNull { l ->
        val f = l.split("|")
        if (f.size < 4 || f[1].isBlank() || f[2].isBlank()) null
        else IeltsPhrase(f[0].trim(), f[1].trim(), f[2].trim(), f.drop(3).joinToString("|").split("||").map { it.trim() }.filter { it.isNotEmpty() }.joinToString("\n"))
    }.toList()

    fun speakByTopic(words: List<IeltsWord>): Map<String, List<IeltsWord>> {
        val g = words.groupBy { it.topic }
        return speakOrder.filter { g.containsKey(it) }.associateWith { g.getValue(it) }
    }

    fun parsePhrases(text: String): List<IeltsPhrase> = text.lineSequence().mapNotNull { l ->
        val f = l.split("|")
        if (f.size < 4 || f[1].isBlank()) null else IeltsPhrase(f[0].trim(), f[1].trim(), f[2].trim(), f[3].trim())
    }.toList()

    fun parseStructs(text: String): List<IeltsStruct> = text.lineSequence().mapNotNull { l ->
        val f = l.split("|")
        val n = f.getOrNull(0)?.trim()?.toIntOrNull()
        if (n == null || f.size < 6) null else IeltsStruct(n, f[1].trim(), f[2].trim(), f[3].trim(),
            f[4].split(";").map { slot -> slot.split("/").map { it.trim() }.filter { it.isNotEmpty() } }.filter { it.isNotEmpty() },
            f.drop(5).joinToString("|").split("||").map { it.trim() }.filter { it.isNotEmpty() })
    }.toList()

    fun byTopic(words: List<IeltsWord>): Map<String, List<IeltsWord>> {
        val g = words.groupBy { it.topic }
        return topicOrder.filter { g.containsKey(it) }.associateWith { g.getValue(it) }
    }

    fun quiz(pool: List<IeltsWord>, all: List<IeltsWord>, rnd: Random, size: Int): List<IeltsQuestion> =
        pool.shuffled(rnd).take(size).mapNotNull { w ->
            val others = all.filter { it.id != w.id }.map { it.uz }.filter { it != w.uz }.distinct().shuffled(rnd).take(3)
            if (others.size < 3) null else {
                val opts = (others + w.uz).shuffled(rnd)
                IeltsQuestion(w.word, opts, opts.indexOf(w.uz), w.example)
            }
        }

    fun gaps(pool: List<IeltsWord>, all: List<IeltsWord>, rnd: Random, size: Int): List<IeltsQuestion> =
        pool.filter { it.hasGap }.shuffled(rnd).take(size).mapNotNull { w ->
            val others = all.filter { it.id != w.id && it.word != w.word }.map { it.word }.distinct().shuffled(rnd).take(3)
            if (others.size < 3) null else {
                val opts = (others + w.word).shuffled(rnd)
                IeltsQuestion(w.gap(), opts, opts.indexOf(w.word), w.uz)
            }
        }

    fun structGaps(all: List<IeltsStruct>, rnd: Random, size: Int): List<IeltsQuestion> =
        all.filter { it.gaps().isNotEmpty() }.shuffled(rnd).take(size).mapNotNull { s ->
            val (prompt, answer) = s.gaps().random(rnd)
            val others = all.filter { it.n != s.n }.map { it.answer }.filter { it != answer }.distinct().shuffled(rnd).take(3)
            if (others.size < 3) null else {
                val opts = (others + answer).shuffled(rnd)
                IeltsQuestion(prompt, opts, opts.indexOf(answer), s.title + " — " + s.formula)
            }
        }

    fun phraseQuiz(all: List<IeltsPhrase>, rnd: Random, size: Int): List<IeltsQuestion> =
        all.shuffled(rnd).take(size).mapNotNull { p ->
            val others = all.filter { it.uz != p.uz }.map { it.uz }.distinct().shuffled(rnd).take(3)
            if (others.size < 3) null else {
                val opts = (others + p.uz).shuffled(rnd)
                IeltsQuestion(p.phrase, opts, opts.indexOf(p.uz), p.example)
            }
        }
}

class IeltsDrill(val key: String, val prompt: String, val answer: String, val options: List<String>, val explain: String, val sub: String = "") {
    val typed: Boolean get() = options.isEmpty()
}

object IeltsSrs {
    val intervals = intArrayOf(0, 0, 1, 3, 7, 14)
    const val MAX_BOX = 5

    fun gap(box: Int): Int = intervals[box.coerceIn(1, MAX_BOX)]

    fun due(box: Int, last: Int, today: Int): Boolean = last <= 0 || today - last >= gap(box)

    fun next(box: Int, right: Boolean): Int = if (right) (box + 1).coerceAtMost(MAX_BOX) else 1

    fun mastered(box: Int): Boolean = box >= 4
}

object IeltsTyped {
    private fun norm(s: String): String {
        var r = s.lowercase()
        for (c in "’‘ʼʻ`´") r = r.replace(c, '\'')
        r = r.replace(Regex("\\(.*?\\)"), " ").replace(Regex("\\s+"), " ").trim().trimEnd('.', '!', '?', ';').trim()
        return r
    }

    private fun alts(answer: String): List<String> = answer.split("/").map { norm(it) }.filter { it.isNotEmpty() }

    private fun lev(a: String, b: String): Int {
        val d = IntArray(b.length + 1) { it }
        for (i in 1..a.length) {
            var prev = d[0]
            d[0] = i
            for (j in 1..b.length) {
                val t = d[j]
                d[j] = minOf(d[j] + 1, d[j - 1] + 1, prev + if (a[i - 1] == b[j - 1]) 0 else 1)
                prev = t
            }
        }
        return d[b.length]
    }

    fun check(input: String, answer: String): Int {
        val n = norm(input)
        if (n.isEmpty()) return 0
        val a = alts(answer)
        if (a.any { it == n || it.removePrefix("to ") == n.removePrefix("to ") }) return 2
        if (n.length >= 6 && a.any { lev(n, it) <= 1 }) return 1
        return 0
    }

    fun hint(answer: String, shown: Int): String {
        val first = alts(answer).firstOrNull() ?: return ""
        var left = shown
        return first.map { c -> if (c == ' ') ' ' else if (left > 0) { left--; c } else '_' }.joinToString("")
    }
}

object IeltsDrills {
    fun word(w: IeltsWord, key: String, typed: Boolean, all: List<IeltsWord>, rnd: Random): IeltsDrill? {
        if (typed) return IeltsDrill(key, w.uz, w.word, emptyList(), w.example)
        val others = all.filter { it.id != w.id }.map { it.uz }.filter { it != w.uz }.distinct().shuffled(rnd).take(3)
        if (others.size < 3) return null
        val opts = (others + w.uz).shuffled(rnd)
        return IeltsDrill(key, w.word, w.uz, opts, w.example)
    }

    fun def(d: IeltsDef, key: String, typed: Boolean, all: List<IeltsDef>, rnd: Random): IeltsDrill? {
        if (typed) return IeltsDrill(key, if (d.uz.isEmpty()) d.short else d.uz + "\n" + d.short, d.word, emptyList(), d.def)
        val others = all.filter { it.word != d.word }.map { it.short }.filter { it != d.short }.distinct().shuffled(rnd).take(3)
        if (others.size < 3) return null
        val opts = (others + d.short).shuffled(rnd)
        return IeltsDrill(key, d.word, d.short, opts, d.def)
    }

    fun phrase(p: IeltsPhrase, key: String, all: List<IeltsPhrase>, rnd: Random): IeltsDrill? {
        val others = all.filter { it.uz != p.uz }.map { it.uz }.distinct().shuffled(rnd).take(3)
        if (others.size < 3) return null
        val opts = (others + p.uz).shuffled(rnd)
        return IeltsDrill(key, p.phrase, p.uz, opts, p.example)
    }

    fun fromQuestions(qs: List<IeltsQuestion>): List<IeltsDrill> = qs.map { IeltsDrill("", it.prompt, it.options[it.answer], it.options, it.explain) }
}
