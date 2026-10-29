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
    private val centre = listOf(".......", ".......", "..111..", ".11111.", ".11111.", "..111..", ".......", ".......")
    private val rows = listOf(".......", ".......", ".......", ".......", ".......", ".......", "1111111", "1111111")
    private val corners = listOf("11...11", "1.....1", ".......", ".......", ".......", ".......", "1.....1", "11...11")
    private val thick = listOf(".......", ".......", "..222..", ".22222.", "..222..", ".......", ".......", ".......")
    private val wall = listOf("1111111", ".......", ".......", ".......", ".......", ".......", ".......", "1111111")
    private val mix = listOf(".......", ".22.22.", ".21.12.", "..111..", "..111..", ".21.12.", ".22.22.", ".......")

    val all: List<MatchLevel> = listOf(
        MatchLevel(15, listOf(MatchGoal.Collect(0, 15))),
        MatchLevel(15, listOf(MatchGoal.Collect(1, 18))),
        MatchLevel(18, listOf(MatchGoal.Score(700))),
        MatchLevel(18, listOf(MatchGoal.ClearDust), rows),
        MatchLevel(20, listOf(MatchGoal.Collect(2, 18), MatchGoal.Collect(4, 12))),
        MatchLevel(20, listOf(MatchGoal.ClearDust), corners),
        MatchLevel(22, listOf(MatchGoal.ClearDust), thick),
        MatchLevel(22, listOf(MatchGoal.Collect(3, 20), MatchGoal.ClearDust), wall),
        MatchLevel(20, listOf(MatchGoal.Score(1400))),
        MatchLevel(25, listOf(MatchGoal.Collect(0, 20), MatchGoal.ClearDust), mix),
    )
}
