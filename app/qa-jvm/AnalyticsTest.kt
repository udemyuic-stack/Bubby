package com.example.buddy

import com.example.buddy.ui.vocab.*

private fun row(sense: Long, ok: Boolean, day: Long, dim: Dimension = Dimension.RECOGNITION, type: String = "choice_meaning", c: Confidence = Confidence.SOME, word: String = "w$sense") = AttemptRow(sense, sense, word, dim, type, ok, day, c)

fun analyticsTests() {
    check("an streak counts back from today", Analytics.streak(setOf(8, 9, 10), 10) == 3)
    check("an streak survives not practising yet today", Analytics.streak(setOf(8, 9), 10) == 2)
    check("an streak broken by gap", Analytics.streak(setOf(5, 9, 10), 10) == 2 && Analytics.streak(setOf(3), 10) == 0 && Analytics.streak(emptySet(), 10) == 0)

    check("an accuracy none", Analytics.accuracy(emptyList(), 10) == null)
    check("an accuracy window", Analytics.accuracy(listOf(row(1, true, 10), row(2, false, 10), row(3, true, 10), row(4, true, 10), row(5, false, 100)), 10) == 75)

    val rows = listOf(row(1, false, 1, word = "alpha"), row(1, false, 2, word = "alpha"), row(1, false, 3, word = "alpha"), row(2, false, 1, word = "beta"), row(2, false, 2, word = "beta"), row(3, false, 1, word = "gamma"))
    check("an hardest needs two misses sorted", Analytics.hardest(rows) == listOf("alpha" to 3, "beta" to 2))
    check("an mistakes by type min sample", Analytics.mistakesByType(listOf(row(1, false, 1, type = "a"), row(1, true, 1, type = "a"))).isEmpty())
    val mt = Analytics.mistakesByType(listOf(row(1, false, 1, type = "a"), row(1, false, 1, type = "a"), row(1, true, 1, type = "a"), row(1, true, 1, type = "b"), row(1, true, 1, type = "b"), row(1, false, 1, type = "b")))
    check("an mistakes by type ranked", mt == listOf("a" to 67, "b" to 33))

    fun prog(step: Int, attempts: Int = 3, lapses: Int = 0) = Progress(step = step, attempts = attempts, lapses = lapses)
    val stats = listOf(
        SenseStat(1, 1, "a", mapOf(Dimension.RECOGNITION to prog(1))),
        SenseStat(2, 2, "b", mapOf(Dimension.RECOGNITION to prog(3), Dimension.MEANING_RECALL to prog(2))),
        SenseStat(3, 3, "c", mapOf(Dimension.RECOGNITION to prog(5), Dimension.MEANING_RECALL to prog(3), Dimension.PRODUCTION to prog(2))),
        SenseStat(4, 4, "d", emptyMap())
    )
    val f = Analytics.funnel(5, stats)
    check("an funnel counts", f.saved == 5 && f.recognized == 3 && f.recallable == 2 && f.usable == 1 && f.retained == 0)
    check("an funnel counts entry once with two senses", Analytics.funnel(1, listOf(SenseStat(1, 7, "x", mapOf(Dimension.RECOGNITION to prog(1))), SenseStat(2, 7, "x", mapOf(Dimension.RECOGNITION to prog(2))))).recognized == 1)
    check("an funnel monotone", f.recognized >= f.recallable && f.recallable >= f.usable && f.usable >= f.retained)

    val p1 = Analytics.patterns(listOf(SenseStat(1, 1, "a", mapOf(Dimension.RECOGNITION to prog(4), Dimension.PRODUCTION to prog(1)))), emptyList(), 10)
    check("an pattern recognises not produces", Pattern.RECOGNIZES_NOT_PRODUCES in p1)
    check("an no pattern when production fine", Analytics.patterns(listOf(SenseStat(1, 1, "a", mapOf(Dimension.RECOGNITION to prog(4), Dimension.PRODUCTION to prog(3)))), emptyList(), 10).isEmpty())
    val forget = listOf(row(1, true, 1), row(1, false, 12), row(2, true, 1), row(2, false, 10))
    check("an pattern forgets after days", Pattern.FORGETS_AFTER_DAYS in Analytics.patterns(emptyList(), forget, 20))
    check("an forgetting needs a real gap", Pattern.FORGETS_AFTER_DAYS !in Analytics.patterns(emptyList(), listOf(row(1, true, 1), row(1, false, 2), row(2, true, 1), row(2, false, 2)), 20))
    check("an pattern weak language detail", Pattern.WEAK_LANGUAGE_DETAIL in Analytics.patterns(emptyList(), listOf(row(1, false, 1, Dimension.LANGUAGE), row(1, false, 2, Dimension.LANGUAGE), row(1, true, 3, Dimension.LANGUAGE)), 10))
    check("an pattern overconfident", Pattern.OVERCONFIDENT in Analytics.patterns(emptyList(), listOf(row(1, false, 9, c = Confidence.SURE), row(2, false, 10, c = Confidence.SURE)), 10))

    fun many(n: Int, okCount: Int) = (0 until n).map { row(it.toLong(), it < okCount, 10) }
    check("an adapt default with little data", Analytics.newPerSession(many(5, 5), 10) == 3)
    check("an adapt more when strong", Analytics.newPerSession(many(20, 19), 10) == 5)
    check("an adapt normal", Analytics.newPerSession(many(20, 15), 10) == 3)
    check("an adapt fewer when struggling", Analytics.newPerSession(many(20, 11), 10) == 1)
    check("an adapt none when failing", Analytics.newPerSession(many(20, 5), 10) == 0)
    check("an adapt options carries pair", Analytics.options(many(20, 19), 10, true, 7).let { it.activePair == 7L && it.aiOn && it.newPerSession == 5 })
}
