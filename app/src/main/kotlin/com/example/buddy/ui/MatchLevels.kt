package com.example.buddy.ui

/** What a level asks for: collect [n] pieces of colour [color], clear all dust, or reach a score. */
sealed class MatchGoal {
    class Collect(val color: Int, val n: Int) : MatchGoal()
    object ClearDust : MatchGoal()
    class Score(val n: Int) : MatchGoal()
}

/**
 * One level of "Bubby's Room". [dust] is the dust map, 8 lines of 7 characters ('.' nothing, '1' one layer, '2' two layers),
 * top line first. Colours: 0 paint drop, 1 brush, 2 lamp, 3 cushion, 4 leaf.
 */
class MatchLevel(val moves: Int, val goals: List<MatchGoal>, val dust: List<String> = emptyList(), val colors: Int = 5) {
    fun dustCells(): IntArray {
        val out = ArrayList<Int>()
        for ((r, line) in dust.withIndex()) for ((c, ch) in line.withIndex()) if (ch == '1' || ch == '2') out += r * 7 + c
        return out.toIntArray()
    }
    fun dustLayerAt(cell: Int): Int {
        val r = cell / 7; val c = cell % 7
        return dust.getOrNull(r)?.getOrNull(c)?.let { if (it == '2') 2 else if (it == '1') 1 else 0 } ?: 0
    }
}

object MatchLevels {
    private val none = emptyList<String>()
    private val thick = listOf(".......", ".......", "..222..", ".22222.", "..222..", ".......", ".......", ".......")
    private val mix = listOf(".......", ".22.22.", ".21.12.", "..111..", "..111..", ".21.12.", ".22.22.", ".......")
    private val centre = listOf(".......", ".......", "..111..", ".11111.", ".11111.", "..111..", ".......", ".......")
    private val cross = listOf("...1...", "...1...", "...1...", "1111111", "...1...", "...1...", "...1...", "...1...")
    private val ring = listOf(".......", ".11111.", ".1...1.", ".1...1.", ".1...1.", ".1...1.", ".11111.", ".......")
    private val corners = listOf("11...11", "1.....1", ".......", ".......", ".......", ".......", "1.....1", "11...11")
    private val checker = listOf(".......", ".......", "1.1.1.1", ".1.1.1.", "1.1.1.1", ".1.1.1.", ".......", ".......")
    private val core2 = listOf(".......", ".......", "..222..", "..222..", "..222..", ".......", ".......", ".......")
    private val twin2 = listOf(".......", ".......", ".2...2.", ".2...2.", ".2...2.", ".2...2.", ".......", ".......")
    private val floor = listOf(".......", ".......", ".......", ".......", ".......", ".......", ".......", "1111111")
    private val band2 = listOf(".......", ".......", ".......", "2222222", "2222222", ".......", ".......", ".......")
    private val mix2 = listOf("2.....2", ".1.1.1.", "..212..", ".1.1.1.", ".1.1.1.", "..212..", ".1.1.1.", "2.....2")
    private val heart = listOf(".......", ".11.11.", "1111111", "1111111", ".11111.", "..111..", "...1...", ".......")
    private val lring2 = listOf(".......", ".22222.", ".2...2.", ".2.1.2.", ".2.1.2.", ".2...2.", ".22222.", ".......")
    private val stairs = listOf("1......", "11.....", ".11....", "..11...", "...11..", "....11.", ".....11", "......1")
    private val vault = listOf("2222222", "2.....2", "2.111.2", "2.111.2", "2.111.2", "2.....2", "2222222", ".......")
    private val diag = listOf("1.....1", ".1...1.", "..1.1..", "...1...", "..1.1..", ".1...1.", "1.....1", ".......")

    /** 30 levels in three tiers; the numbers come from a bot simulation (see the balance notes in the commit history). */
    val all: List<MatchLevel> = listOf(
        MatchLevel(10, listOf(MatchGoal.Collect(0, 15))),
        MatchLevel(11, listOf(MatchGoal.Collect(1, 16))),
        MatchLevel(12, listOf(MatchGoal.Score(1600))),
        MatchLevel(14, listOf(MatchGoal.ClearDust), centre),
        MatchLevel(12, listOf(MatchGoal.Collect(2, 20), MatchGoal.Collect(4, 18))),
        MatchLevel(16, listOf(MatchGoal.ClearDust), cross),
        MatchLevel(16, listOf(MatchGoal.Score(3600))),
        MatchLevel(13, listOf(MatchGoal.ClearDust), ring),
        MatchLevel(13, listOf(MatchGoal.Collect(0, 28), MatchGoal.Collect(3, 28))),
        MatchLevel(23, listOf(MatchGoal.ClearDust), corners),
        MatchLevel(22, listOf(MatchGoal.ClearDust), checker),
        MatchLevel(12, listOf(MatchGoal.ClearDust), core2),
        MatchLevel(17, listOf(MatchGoal.ClearDust), twin2),
        MatchLevel(22, listOf(MatchGoal.Collect(1, 18), MatchGoal.ClearDust), floor),
        MatchLevel(15, listOf(MatchGoal.Collect(2, 22), MatchGoal.ClearDust), cross),
        MatchLevel(22, listOf(MatchGoal.ClearDust), band2),
        MatchLevel(11, listOf(MatchGoal.ClearDust), thick),
        MatchLevel(16, listOf(MatchGoal.ClearDust), mix),
        MatchLevel(32, listOf(MatchGoal.Collect(4, 12), MatchGoal.ClearDust), mix2),
        MatchLevel(14, listOf(MatchGoal.ClearDust), heart),
        MatchLevel(13, listOf(MatchGoal.Score(2800))),
        MatchLevel(11, listOf(MatchGoal.Collect(0, 19), MatchGoal.Collect(1, 19))),
        MatchLevel(20, listOf(MatchGoal.ClearDust), lring2),
        MatchLevel(23, listOf(MatchGoal.Score(3500), MatchGoal.ClearDust), floor),
        MatchLevel(13, listOf(MatchGoal.Collect(3, 18), MatchGoal.ClearDust), ring),
        MatchLevel(12, listOf(MatchGoal.Collect(0, 26), MatchGoal.Collect(2, 26), MatchGoal.Collect(4, 26))),
        MatchLevel(17, listOf(MatchGoal.ClearDust), stairs),
        MatchLevel(20, listOf(MatchGoal.Score(5800))),
        MatchLevel(30, listOf(MatchGoal.ClearDust), vault),
        MatchLevel(16, listOf(MatchGoal.Collect(1, 22), MatchGoal.ClearDust), diag),
    )
}
