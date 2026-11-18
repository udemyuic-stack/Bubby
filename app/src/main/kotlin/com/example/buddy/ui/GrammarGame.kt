package com.example.buddy.ui

import kotlin.random.Random

object GrammarCheck {
    private const val APOS = "’‘ʼʻ`´"

    fun norm(s: String): String {
        var r = s.lowercase()
        for (c in APOS) r = r.replace(c, '\'')
        return r.replace(Regex("\\s+"), " ").trim().trimEnd('.', '!', '?').trim()
    }

    fun choose(q: GrammarQ, option: String): Boolean = option == q.answers.first()

    fun mistake(q: GrammarQ, index: Int): Boolean = index == q.wrongIndex

    fun order(q: GrammarQ, words: List<String>): Boolean = norm(words.joinToString(" ")) == norm(q.text)

    fun typed(q: GrammarQ, input: String): Boolean {
        val n = norm(input)
        return n.isNotEmpty() && q.answers.any { norm(it) == n }
    }

    fun options(q: GrammarQ, rnd: Random): List<String> = q.options.shuffled(rnd)

    fun tiles(q: GrammarQ, rnd: Random): List<String> {
        val w = q.words()
        if (w.size < 2) return w
        repeat(8) {
            val s = w.shuffled(rnd)
            if (s != w) return s
        }
        return w.reversed()
    }
}

object GrammarStars {
    fun of(score: Int): Int = when {
        score >= 9 -> 3
        score >= 7 -> 2
        score >= 5 -> 1
        else -> 0
    }
}

object GrammarWeak {
    fun decode(s: String): LinkedHashSet<String> = s.split(",").map { it.trim() }.filter { it.isNotEmpty() }.toCollection(LinkedHashSet())

    fun encode(ids: Set<String>): String = ids.joinToString(",")

    fun update(ids: Set<String>, q: GrammarQ, right: Boolean): LinkedHashSet<String> {
        val out = LinkedHashSet(ids)
        if (q.topic == AI_TOPIC) return out
        if (right) out.remove(q.id) else out.add(q.id)
        return out
    }

    fun questions(ids: Set<String>, rnd: Random): List<GrammarQ> =
        ids.mapNotNull { GrammarBank.byId(it) }.shuffled(rnd).take(GrammarSession.SIZE)

    const val AI_TOPIC = "ai"
}

class GrammarSession(val questions: List<GrammarQ>) {
    var index = 0
        private set
    var score = 0
        private set
    var streak = 0
        private set
    var bestStreak = 0
        private set
    val missed = ArrayList<GrammarQ>()
    var rewarded = false

    val current: GrammarQ? get() = questions.getOrNull(index)
    val done: Boolean get() = index >= questions.size
    val stars: Int get() = GrammarStars.of(score * SIZE / questions.size.coerceAtLeast(1))

    fun answer(right: Boolean) {
        val q = current ?: return
        if (right) {
            score++
            streak++
            bestStreak = maxOf(bestStreak, streak)
        } else {
            streak = 0
            missed.add(q)
        }
    }

    fun next() {
        if (!done) index++
    }

    companion object {
        const val SIZE = 10

        fun topic(t: GrammarTopic, rnd: Random): GrammarSession = GrammarSession(t.questions.shuffled(rnd).take(SIZE))
    }
}

object GrammarBattle {
    fun phrases(): List<RunnerPhrase> = GrammarBank.all().filter { it.kind == GKind.CHOOSE }
        .map { RunnerPhrase(it.text, it.answers.first(), it.options, it.why, it.why) }
}
