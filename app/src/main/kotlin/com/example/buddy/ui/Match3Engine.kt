package com.example.buddy.ui

import kotlin.random.Random

/**
 * The rules of "Bubby's Room", a match-3 puzzle. Pure Kotlin (no Android classes) so that it can be tested on a plain JVM.
 *
 * The board is [w] columns by [h] rows; cell index = row * w + col, row 0 is the top. A cell holds a piece colour
 * (0 until [colors]) or -1 (empty), a special kind and, below the piece, a layer count of dust that is wiped when a piece
 * on that cell is cleared. [swap] plays one move and returns the list of [Step]s that the view animates one after another.
 */
class Match3Engine(val w: Int = 7, val h: Int = 8, val colors: Int = 5, seed: Long = System.nanoTime()) {
    companion object {
        const val NONE = 0
        const val ROLLER_H = 1     // clears its row
        const val ROLLER_V = 2     // clears its column
        const val SPLASH = 3       // clears the 3x3 around it
        const val SPARKLE = 4      // clears every piece of one colour
    }

    class Fall(val from: Int, val to: Int)
    class Fresh(val cell: Int, val type: Int, val fromRow: Int)   // fromRow: negative row above the board where it starts
    class Spawn(val cell: Int, val special: Int)

    /** One round of a move: what was cleared, which specials were born, how pieces fell and which new pieces came in. */
    class Step(val cleared: IntArray, val spawned: List<Spawn>, val falls: List<Fall>, val fresh: List<Fresh>, val gained: Int, val combo: Int, val fired: List<Int>)

    class Result(val valid: Boolean, val steps: List<Step>, val shuffled: Boolean)

    private val rnd = Random(seed)
    val type = IntArray(w * h) { -1 }
    val special = IntArray(w * h)
    val dust = IntArray(w * h)

    var score = 0; private set
    var movesLeft = 0
    val collected = IntArray(colors)

    private fun r(i: Int) = i / w
    private fun c(i: Int) = i % w
    private fun inside(row: Int, col: Int) = row in 0 until h && col in 0 until w

    // ---- setting up ----------------------------------------------------------------------------------------------------

    /** Fills the board with random pieces without any ready match and with at least one possible move. */
    fun fill() {
        var guard = 0
        do {
            for (i in 0 until w * h) {
                special[i] = NONE
                var t: Int
                var tries = 0
                do { t = rnd.nextInt(colors); tries++ } while (tries < 20 && makesRun(i, t))
                type[i] = t
            }
            guard++
        } while ((findRuns(null).isNotEmpty() || findMove() == null) && guard < 200)
    }

    /** Would [t] at [i] complete three in a row with the pieces to its left or above (the cells are filled top-left first)? */
    private fun makesRun(i: Int, t: Int): Boolean {
        val row = r(i); val col = c(i)
        if (col >= 2 && type[i - 1] == t && type[i - 2] == t) return true
        if (row >= 2 && type[i - w] == t && type[i - 2 * w] == t) return true
        return false
    }

    fun setLevel(moves: Int, dustCells: IntArray = IntArray(0), dustLayers: Int = 1) {
        movesLeft = moves; score = 0
        collected.fill(0); dust.fill(0)
        for (i in dustCells) if (i in dust.indices) dust[i] = dustLayers
    }

    fun dustLeft(): Int { var n = 0; for (d in dust) if (d > 0) n++; return n }

    // ---- looking for matches ---------------------------------------------------------------------------------------------

    /** A run of equal colours found by [findRuns]: [cells] in line, horizontal or vertical. */
    class Run(val cells: IntArray, val horizontal: Boolean, val type: Int)

    private fun same(a: Int, b: Int) = type[a] >= 0 && type[a] == type[b]

    /** All runs of three or more. With [on] given the board is first read as if those two cells were swapped. */
    fun findRuns(on: IntArray?): List<Run> {
        val out = ArrayList<Run>()
        val t = if (on == null) type else type.copyOf().also { val x = it[on[0]]; it[on[0]] = it[on[1]]; it[on[1]] = x }
        for (row in 0 until h) {
            var col = 0
            while (col < w) {
                val v = t[row * w + col]
                var e = col
                while (e + 1 < w && v >= 0 && t[row * w + e + 1] == v) e++
                if (v >= 0 && e - col >= 2) out += Run(IntArray(e - col + 1) { row * w + col + it }, true, v)
                col = e + 1
            }
        }
        for (col in 0 until w) {
            var row = 0
            while (row < h) {
                val v = t[row * w + col]
                var e = row
                while (e + 1 < h && v >= 0 && t[(e + 1) * w + col] == v) e++
                if (v >= 0 && e - row >= 2) out += Run(IntArray(e - row + 1) { (row + it) * w + col }, false, v)
                row = e + 1
            }
        }
        return out
    }

    private fun adjacent(a: Int, b: Int) = (r(a) == r(b) && Math.abs(c(a) - c(b)) == 1) || (c(a) == c(b) && Math.abs(r(a) - r(b)) == 1)

    /** Would swapping [a] and [b] be a legal move? */
    fun canSwap(a: Int, b: Int): Boolean {
        if (a !in type.indices || b !in type.indices || !adjacent(a, b)) return false
        if (type[a] < 0 || type[b] < 0) return false
        if (special[a] == SPARKLE || special[b] == SPARKLE) return true
        if (special[a] != NONE && special[b] != NONE) return true
        return findRuns(intArrayOf(a, b)).isNotEmpty()
    }

    /** The first legal move as (cell, neighbour), or null. Also what the hint shows. */
    fun findMove(): IntArray? {
        for (i in 0 until w * h) {
            if (c(i) + 1 < w && canSwap(i, i + 1)) return intArrayOf(i, i + 1)
            if (r(i) + 1 < h && canSwap(i, i + w)) return intArrayOf(i, i + w)
        }
        return null
    }

    // ---- playing a move ------------------------------------------------------------------------------------------------------

    fun swap(a: Int, b: Int): Result {
        if (!canSwap(a, b)) return Result(false, emptyList(), false)
        movesLeft--
        val steps = ArrayList<Step>()
        val sa = special[a]; val sb = special[b]
        // swap the pieces
        run { val t = type[a]; type[a] = type[b]; type[b] = t; val s = special[a]; special[a] = special[b]; special[b] = s }
        val marks = BooleanArray(w * h)
        val fired = ArrayList<Int>()
        var combo = 1
        var forced = false
        if (sa != NONE && sb != NONE) {
            forced = true
            combine(a, b, sa, sb, marks, fired)
        } else if (sa == SPARKLE || sb == SPARKLE) {
            forced = true
            val spark = if (sa == SPARKLE) b else a          // after the swap the sparkle sits where the other piece was
            val other = if (sa == SPARKLE) a else b
            val color = type[other]
            marks[spark] = true
            for (i in type.indices) if (type[i] == color && type[i] >= 0) marks[i] = true
            fired += spark
        }
        var anchors = intArrayOf(a, b)
        while (true) {
            val spawns = ArrayList<Spawn>()
            val runs = findRuns(null)
            if (!forced && runs.isEmpty()) break
            val keep = HashSet<Int>()
            if (runs.isNotEmpty()) markRuns(runs, anchors, marks, spawns, keep)
            forced = false
            expand(marks, keep, fired)
            val step = resolve(marks, spawns, keep, combo, fired)
            steps += step
            combo++
            anchors = IntArray(0)
            java.util.Arrays.fill(marks, false)
            fired.clear()
        }
        var shuffled = false
        if (findMove() == null) { shuffle(); shuffled = true }
        return Result(true, steps, shuffled)
    }

    /** Two specials swapped: their combined blast. */
    private fun combine(a: Int, b: Int, sa: Int, sb: Int, marks: BooleanArray, fired: ArrayList<Int>) {
        fun line(i: Int, rowToo: Boolean, colToo: Boolean) {
            if (rowToo) for (x in 0 until w) marks[r(i) * w + x] = true
            if (colToo) for (y in 0 until h) marks[y * w + c(i)] = true
        }
        fun area(i: Int, radius: Int) {
            for (dy in -radius..radius) for (dx in -radius..radius) if (inside(r(i) + dy, c(i) + dx)) marks[(r(i) + dy) * w + c(i) + dx] = true
        }
        val kinds = setOf(sa, sb)
        fired += a; fired += b
        when {
            sa == SPARKLE && sb == SPARKLE -> java.util.Arrays.fill(marks, true)
            SPARKLE in kinds -> {
                // every piece of the most common colour turns into the other special and fires
                val other = if (sa == SPARKLE) sb else sa
                val counts = IntArray(colors)
                for (i in type.indices) if (type[i] >= 0) counts[type[i]]++
                var best = 0; for (k in 1 until colors) if (counts[k] > counts[best]) best = k
                for (i in type.indices) if (type[i] == best) {
                    marks[i] = true
                    if (i != a && i != b) when (other) {
                        ROLLER_H, ROLLER_V -> line(i, other == ROLLER_H, other == ROLLER_V)
                        SPLASH -> area(i, 1)
                    }
                }
                marks[a] = true; marks[b] = true
            }
            sa == SPLASH && sb == SPLASH -> area(b, 2)
            SPLASH in kinds -> { for (d in -1..1) { if (inside(r(b) + d, c(b))) line((r(b) + d) * w + c(b), true, false); if (inside(r(b), c(b) + d)) line(r(b) * w + c(b) + d, false, true) } }
            else -> line(b, true, true)   // roller + roller: a cross
        }
    }

    /**
     * Decides which cells of the found runs are cleared and which special pieces are born from them.
     * 5 in a line: sparkle; L or T (a horizontal and a vertical run sharing a cell): splash; 4 in a line: roller.
     */
    private fun markRuns(runs: List<Run>, anchors: IntArray, marks: BooleanArray, spawns: ArrayList<Spawn>, keep: HashSet<Int>) {
        for (run in runs) for (i in run.cells) marks[i] = true
        val used = HashSet<Run>()
        fun anchorOf(cells: IntArray): Int { for (x in anchors) if (x in cells) return x; return cells[cells.size / 2] }
        for (run in runs) if (run.cells.size >= 5) { spawn(spawns, keep, anchorOf(run.cells), SPARKLE); used += run }
        for (hr in runs) if (hr.horizontal && hr !in used) for (vr in runs) if (!vr.horizontal && vr !in used && vr.type == hr.type) {
            val shared = hr.cells.firstOrNull { it in vr.cells } ?: continue
            spawn(spawns, keep, shared, SPLASH); used += hr; used += vr
            break
        }
        for (run in runs) if (run !in used && run.cells.size == 4) {
            spawn(spawns, keep, anchorOf(run.cells), if (run.horizontal) ROLLER_V else ROLLER_H)   // a roller sweeps across its match
            used += run
        }
    }

    private fun spawn(spawns: ArrayList<Spawn>, keep: HashSet<Int>, cell: Int, kind: Int) {
        if (cell in keep) return
        spawns += Spawn(cell, kind); keep += cell
    }

    /** Specials caught in the blast fire too, each cell once. */
    private fun expand(marks: BooleanArray, keep: HashSet<Int>, fired: ArrayList<Int>) {
        val done = BooleanArray(w * h)
        for (i in fired) done[i] = true   // already fired by the swap itself
        var again = true
        while (again) {
            again = false
            for (i in 0 until w * h) {
                if (!marks[i] || done[i] || special[i] == NONE) continue   // also a special that sits where a new special is born still fires first
                done[i] = true; again = true
                fired += i
                when (special[i]) {
                    ROLLER_H -> for (x in 0 until w) marks[r(i) * w + x] = true
                    ROLLER_V -> for (y in 0 until h) marks[y * w + c(i)] = true
                    SPLASH -> for (dy in -1..1) for (dx in -1..1) if (inside(r(i) + dy, c(i) + dx)) marks[(r(i) + dy) * w + c(i) + dx] = true
                    SPARKLE -> {
                        // caught by a blast: it takes away one colour, the most common one
                        val counts = IntArray(colors)
                        for (k in type.indices) if (type[k] >= 0) counts[type[k]]++
                        var best = 0; for (k in 1 until colors) if (counts[k] > counts[best]) best = k
                        for (k in type.indices) if (type[k] == best) marks[k] = true
                    }
                }
            }
        }
    }

    /** Clears the marked cells, drops the pieces above and brings in new ones: one step of the move. */
    private fun resolve(marks: BooleanArray, spawns: List<Spawn>, keep: Set<Int>, combo: Int, fired: List<Int>): Step {
        val cleared = ArrayList<Int>()   // the pieces that really leave the board (a cell that turns into a special stays)
        for (i in 0 until w * h) if (marks[i] && type[i] >= 0) {
            if (dust[i] > 0) dust[i]--
            if (i !in keep) cleared += i
        }
        var points = 0
        for (i in cleared) {
            collected[type[i]]++
            points += 10
        }
        points *= combo
        score += points
        for (s in spawns) { special[s.cell] = s.special }   // the anchor stays on the board as the new special piece
        for (i in cleared) { type[i] = -1; special[i] = NONE }   // anchors keep their colour
        // gravity
        val falls = ArrayList<Fall>()
        val fresh = ArrayList<Fresh>()
        for (col in 0 until w) {
            var write = h - 1
            for (row in h - 1 downTo 0) {
                val i = row * w + col
                if (type[i] >= 0) {
                    if (write != row) {
                        val to = write * w + col
                        type[to] = type[i]; special[to] = special[i]; type[i] = -1; special[i] = NONE
                        falls += Fall(i, to)
                    }
                    write--
                }
            }
            val above = write + 1   // empty cells at the top of this column; the new pieces all fall in from above the board
            for (row in write downTo 0) {
                val i = row * w + col
                type[i] = rnd.nextInt(colors); special[i] = NONE
                fresh += Fresh(i, type[i], row - above)
            }
        }
        return Step(cleared.toIntArray(), spawns, falls, fresh, points, combo, fired.toList())
    }

    /** No move left: mixes the plain pieces until there is a move and no ready match. */
    fun shuffle() {
        val cells = ArrayList<Int>()
        for (i in 0 until w * h) if (special[i] == NONE) cells += i
        var guard = 0
        do {
            val vals = cells.map { type[it] }.shuffled(rnd)
            for ((k, i) in cells.withIndex()) type[i] = vals[k]
            guard++
        } while ((findRuns(null).isNotEmpty() || findMove() == null) && guard < 300)
        if (findRuns(null).isNotEmpty() || findMove() == null) fill()
    }
}
