package com.example.buddy.ui.vocab

import kotlin.math.roundToInt

class AttemptRow(val sense: Long, val entry: Long, val word: String, val dim: Dimension, val type: String, val ok: Boolean, val day: Long, val confidence: Confidence)

class SenseStat(val sense: Long, val entry: Long, val word: String, val progress: Map<Dimension, Progress>)

class Funnel(val saved: Int, val recognized: Int, val recallable: Int, val usable: Int, val retained: Int)

enum class Pattern { RECOGNIZES_NOT_PRODUCES, FORGETS_AFTER_DAYS, WEAK_LANGUAGE_DETAIL, OVERCONFIDENT }

object Analytics {
    const val FORGET_GAP = 7
    private const val RECENT = 40

    private fun solid(p: Progress?, step: Int) = p != null && p.attempts > 0 && p.step >= step

    fun funnel(savedEntries: Int, stats: List<SenseStat>): Funnel {
        val recognized = HashSet<Long>()
        val recallable = HashSet<Long>()
        val usable = HashSet<Long>()
        val retained = HashSet<Long>()
        for (s in stats) {
            val pr = s.progress
            if (solid(pr[Dimension.RECOGNITION], 1) || solid(pr[Dimension.MEANING_RECALL], 1) || solid(pr[Dimension.PRODUCTION], 1)) recognized.add(s.entry)
            if (solid(pr[Dimension.MEANING_RECALL], 2)) recallable.add(s.entry)
            if (solid(pr[Dimension.PRODUCTION], 2) || solid(pr[Dimension.CONTEXT], 2) || solid(pr[Dimension.SENTENCE], 2)) usable.add(s.entry)
            if (Mastery.overall(pr) == Label.WELL_RETAINED) retained.add(s.entry)
        }
        return Funnel(savedEntries, recognized.size, recallable.size, usable.size, retained.size)
    }

    fun accuracy(rows: List<AttemptRow>, today: Long, days: Int = 30): Int? {
        val r = rows.filter { today - it.day in 0..days.toLong() }
        if (r.isEmpty()) return null
        return (r.count { it.ok } * 100.0 / r.size).roundToInt()
    }

    fun streak(sessionDays: Set<Long>, today: Long): Int {
        var d = if (today in sessionDays) today else today - 1
        var n = 0
        while (d in sessionDays) { n++; d-- }
        return n
    }

    fun hardest(rows: List<AttemptRow>, n: Int = 5): List<Pair<String, Int>> =
        rows.filter { !it.ok }.groupBy { it.word }.map { it.key to it.value.size }.filter { it.second >= 2 }
            .sortedWith(compareByDescending<Pair<String, Int>> { it.second }.thenBy { it.first }).take(n)

    fun mistakesByType(rows: List<AttemptRow>, n: Int = 3): List<Pair<String, Int>> =
        rows.groupBy { it.type }.filter { it.value.size >= 3 }
            .map { it.key to (it.value.count { r -> !r.ok } * 100.0 / it.value.size).roundToInt() }
            .filter { it.second > 0 }.sortedByDescending { it.second }.take(n)

    fun patterns(stats: List<SenseStat>, rows: List<AttemptRow>, today: Long): Set<Pattern> {
        val out = LinkedHashSet<Pattern>()
        val noProduce = stats.count { s ->
            val rec = s.progress[Dimension.RECOGNITION]
            val prod = s.progress[Dimension.PRODUCTION]
            solid(rec, 3) && prod != null && prod.attempts > 0 && (prod.step <= 1 || prod.lapses >= 2)
        }
        if (noProduce >= 1) out.add(Pattern.RECOGNIZES_NOT_PRODUCES)
        var forgot = 0
        val byKey = rows.groupBy { it.sense to it.dim }
        for (list in byKey.values) {
            val sorted = list.sortedBy { it.day }
            for (i in 1 until sorted.size) if (!sorted[i].ok && sorted[i - 1].ok && sorted[i].day - sorted[i - 1].day >= FORGET_GAP) forgot++
        }
        if (forgot >= 2) out.add(Pattern.FORGETS_AFTER_DAYS)
        val lang = rows.filter { it.dim == Dimension.LANGUAGE }
        if (lang.size >= 3 && lang.count { it.ok } * 2 < lang.size) out.add(Pattern.WEAK_LANGUAGE_DETAIL)
        if (rows.count { !it.ok && it.confidence == Confidence.SURE && today - it.day in 0..30 } >= 2) out.add(Pattern.OVERCONFIDENT)
        return out
    }

    fun newPerSession(rows: List<AttemptRow>, today: Long): Int {
        val recent = rows.filter { today - it.day in 0..30 }.sortedByDescending { it.day }.take(RECENT)
        if (recent.size < 10) return 3
        val acc = recent.count { it.ok } * 100 / recent.size
        return when {
            acc >= 85 -> 5
            acc >= 65 -> 3
            acc >= 50 -> 1
            else -> 0
        }
    }

    fun options(rows: List<AttemptRow>, today: Long, aiOn: Boolean, activePair: Long): PlanOptions =
        PlanOptions(aiOn = aiOn, activePair = activePair, newPerSession = newPerSession(rows, today))
}
