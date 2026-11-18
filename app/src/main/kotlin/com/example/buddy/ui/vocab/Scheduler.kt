package com.example.buddy.ui.vocab

import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

object Scheduler {
    val BASE = intArrayOf(1, 3, 7, 14, 30)
    const val MAX_STEP = 5
    const val MIN_EASE = 1.3
    const val MAX_EASE = 2.5
    const val FAST_MS = 8000
    const val LONG_GAP = 14

    fun gap(step: Int, ease: Double, lapses: Int): Int {
        if (step <= 0) return 1
        val base = BASE[min(step, BASE.size) - 1]
        val penalty = 1.0 / (1.0 + 0.15 * min(lapses, 4))
        return max(1, (base * ease / 2.0 * penalty).roundToInt())
    }

    fun apply(p: Progress, a: Attempt): Progress {
        val interval = if (p.last < 0) 0 else (a.day - p.last).toInt()
        val late = if (p.attempts == 0) 0 else max(0, (a.day - p.due).toInt())
        if (!a.ok) {
            val sureMiss = a.confidence == Confidence.SURE
            val step = max(0, p.step - 2)
            val ease = max(MIN_EASE, p.ease - 0.2 - if (sureMiss) 0.2 else 0.0)
            return Progress(step, ease, a.day + 1, p.lapses + 1, a.day, p.attempts + 1, p.illusion || sureMiss, 0, p.longTypes, p.longContexts)
        }
        val guess = a.confidence == Confidence.GUESS
        val slow = a.ms > FAST_MS
        val up = when (a.confidence) {
            Confidence.SURE -> true
            Confidence.SOME -> !slow
            Confidence.GUESS -> false
        }
        val step = if (up) min(MAX_STEP, p.step + 1) else p.step
        var ease = p.ease
        if (a.confidence == Confidence.SURE) ease += 0.1
        if (guess) ease -= 0.05
        if (late >= max(1, gap(p.step, p.ease, p.lapses)) && !guess) ease += 0.05
        ease = min(MAX_EASE, max(MIN_EASE, ease))
        val streak = if (guess) 0 else p.streak + 1
        val illusion = if (p.illusion && streak >= 2) false else p.illusion
        val longOk = interval >= LONG_GAP && !guess
        val types = if (longOk) p.longTypes + a.type else p.longTypes
        val contexts = if (longOk && a.context.isNotEmpty()) p.longContexts + a.context else p.longContexts
        return Progress(step, ease, a.day + gap(step, ease, p.lapses), p.lapses, a.day, p.attempts + 1, illusion, streak, types, contexts)
    }

    fun fold(attempts: List<Attempt>, start: Progress = Progress()): Progress = attempts.fold(start) { p, a -> apply(p, a) }
}

object Mastery {
    fun label(p: Progress): Label {
        if (p.attempts == 0) return Label.NEW
        return when {
            p.step <= 1 -> Label.LEARNING
            p.step == 2 -> Label.DEVELOPING
            p.step < Scheduler.MAX_STEP -> Label.STRONG
            p.longTypes.size >= 2 && p.longContexts.size >= 2 -> Label.WELL_RETAINED
            else -> Label.STRONG
        }
    }

    fun overall(byDim: Map<Dimension, Progress>): Label {
        val tried = byDim.filter { it.value.attempts > 0 }
        if (tried.isEmpty()) return Label.NEW
        var worst = tried.filter { it.key.core }.values.map { label(it) }.minOrNull() ?: tried.values.map { label(it) }.minOrNull()!!
        val produced = (byDim[Dimension.PRODUCTION]?.attempts ?: 0) > 0
        if (!produced && worst > Label.DEVELOPING) worst = Label.DEVELOPING
        return worst
    }

    fun strength(p: Progress): Int = if (p.attempts == 0) 0 else min(100, p.step * 20 - p.lapses.coerceAtMost(5) * 2 + (if (p.illusion) -10 else 0)).coerceAtLeast(0)
}
