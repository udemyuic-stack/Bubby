package com.example.buddy.ui

import android.content.Context
import kotlin.random.Random

object GameStages {
    const val COUNT = 5

    data class Guess(val max: Int, val tries: Int)
    data class Rps(val target: Int, val mode: Int, val chance: Int)
    data class Memory(val cards: Int, val moves: Int) {
        val pairs get() = cards / 2
        val cols get() = if (cards == 6) 3 else 4
    }

    val guess = listOf(Guess(20, 8), Guess(50, 8), Guess(100, 8), Guess(500, 10), Guess(1000, 10))
    val rps = listOf(Rps(3, 0, 0), Rps(3, 1, 40), Rps(4, 2, 60), Rps(4, 3, 70), Rps(5, 3, 80))
    val memory = listOf(Memory(6, 12), Memory(8, 16), Memory(12, 22), Memory(16, 30), Memory(20, 38))

    fun stars(ctx: Context, kind: String, i: Int) = Backend.int(ctx, "gs_${kind}_$i", 0)

    fun unlocked(ctx: Context, kind: String, i: Int) = i == 0 || stars(ctx, kind, i - 1) >= 1

    fun starsText(n: Int) = "★".repeat(n) + "☆".repeat(3 - n)

    fun pay(ctx: Context, kind: String, i: Int, st: Int): Int {
        val prev = stars(ctx, kind, i)
        if (st > prev) Backend.setInt(ctx, "gs_${kind}_$i", st)
        val earned = (st - prev).coerceAtLeast(0) * (i + 1) + if (prev == 0 && st >= 1) 2 else 0
        if (earned > 0) DailyStreak.addStars(ctx, earned)
        return earned
    }

    fun guessPar(g: Guess) = 32 - Integer.numberOfLeadingZeros(g.max - 1)

    fun guessStars(g: Guess, tries: Int): Int {
        val par = guessPar(g)
        return when {
            tries <= par -> 3
            tries <= par + 2 -> 2
            else -> 1
        }
    }

    fun rpsStars(margin: Int): Int = when {
        margin >= 3 -> 3
        margin == 2 -> 2
        margin == 1 -> 1
        else -> 0
    }

    fun memoryStars(m: Memory, moves: Int): Int = when {
        moves * 2 <= m.pairs * 3 -> 3
        moves <= m.pairs * 2 -> 2
        else -> 1
    }

    class RpsBot(private val stage: Rps) {
        private val history = ArrayList<Int>()

        fun record(move: Int) { history += move }

        fun next(): Int {
            if (stage.mode == 0 || Random.nextInt(100) >= stage.chance) return Random.nextInt(3)
            val predicted = when (stage.mode) {
                1 -> history.lastOrNull()
                2 -> frequent()
                else -> afterPair()
            } ?: return Random.nextInt(3)
            return (predicted + 1) % 3
        }

        private fun frequent(): Int? {
            if (history.isEmpty()) return null
            val counts = IntArray(3)
            for (m in history) counts[m]++
            return counts.indices.maxByOrNull { counts[it] }
        }

        private fun afterPair(): Int? {
            if (history.size < 3) return frequent()
            val a = history[history.size - 2]
            val b = history[history.size - 1]
            val counts = IntArray(3)
            var seen = 0
            for (i in 0 until history.size - 2) {
                if (history[i] == a && history[i + 1] == b) { counts[history[i + 2]]++; seen++ }
            }
            if (seen == 0) return frequent()
            return counts.indices.maxByOrNull { counts[it] }
        }
    }
}
