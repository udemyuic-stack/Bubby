package com.example.buddy.ui

// Plain-JVM checks of the match-3 rules (Match3Engine has no Android classes).
// Run: /tmp/.../kc.sh style: compile Match3Engine.kt + this file with kotlinc and run com.example.buddy.ui.Match3TestKt

private var pass = 0
private var fail = 0
private fun check(name: String, ok: Boolean, detail: String = "") { if (ok) pass++ else { fail++; println("FAIL: $name $detail") } }

private const val W = 7
private const val H = 8
private fun at(row: Int, col: Int) = row * W + col

/** A board without any run: colour = (col + 2 * row) mod 5. */
private fun calm(e: Match3Engine) {
    for (r in 0 until H) for (c in 0 until W) { e.type[at(r, c)] = (c + 2 * r) % 5; e.special[at(r, c)] = 0 }
}

fun main() {
    // ---- filling ----
    for (seed in 1L..200L) {
        val e = Match3Engine(seed = seed); e.fill()
        check("fill $seed full", e.type.all { it in 0 until 5 })
        check("fill $seed no run", e.findRuns(null).isEmpty())
        check("fill $seed has move", e.findMove() != null)
    }

    // ---- run detection ----
    run {
        val e = Match3Engine(seed = 1); calm(e)
        check("calm board has no run", e.findRuns(null).isEmpty())
        for (c in 0..2) e.type[at(0, c)] = 4
        val r = e.findRuns(null)
        check("three in a row", r.size == 1 && r[0].horizontal && r[0].cells.size == 3, "got ${r.size}")
        for (c in 0..3) e.type[at(1, c)] = 4
        check("four in a row", e.findRuns(null).any { it.cells.size == 4 })
        calm(e)
        for (rr in 0..4) e.type[at(rr, 6)] = 3
        check("five in a column", e.findRuns(null).any { !it.horizontal && it.cells.size == 5 })
    }

    // ---- an illegal swap changes nothing ----
    run {
        val e = Match3Engine(seed = 2); calm(e); e.movesLeft = 10
        val before = e.type.copyOf()
        val res = e.swap(at(0, 0), at(0, 1))
        check("illegal swap invalid", !res.valid)
        check("illegal swap board same", before.contentEquals(e.type))
        check("illegal swap keeps moves", e.movesLeft == 10)
        check("far swap invalid", !e.canSwap(at(0, 0), at(0, 2)))
        check("diagonal swap invalid", !e.canSwap(at(0, 0), at(1, 1)))
    }

    // ---- a plain three ----
    run {
        val e = Match3Engine(seed = 3); calm(e); e.movesLeft = 5
        e.type[at(3, 0)] = 0; e.type[at(3, 1)] = 0; e.type[at(3, 2)] = 1; e.type[at(2, 2)] = 0
        check("three: legal", e.canSwap(at(2, 2), at(3, 2)))
        val res = e.swap(at(2, 2), at(3, 2))
        check("three: valid", res.valid)
        check("three: first step clears the three", res.steps.isNotEmpty() && listOf(at(3, 0), at(3, 1), at(3, 2)).all { it in res.steps[0].cleared.toList() })
        check("three: no special", res.steps[0].spawned.isEmpty())
        check("three: moves used", e.movesLeft == 4)
        check("three: board full after", e.type.all { it >= 0 })
        check("three: no run left", e.findRuns(null).isEmpty())
        check("three: score", e.score >= 30)
    }

    // ---- four makes a roller, five a sparkle ----
    run {
        val e = Match3Engine(seed = 4); calm(e); e.movesLeft = 5
        e.type[at(3, 0)] = 0; e.type[at(3, 1)] = 0; e.type[at(3, 3)] = 0; e.type[at(3, 4)] = 2; e.type[at(3, 2)] = 1; e.type[at(2, 2)] = 0
        val res = e.swap(at(2, 2), at(3, 2))
        check("four: valid", res.valid)
        val sp = res.steps[0].spawned
        check("four: roller born", sp.size == 1 && sp[0].special == Match3Engine.ROLLER_V && sp[0].cell == at(3, 2), "got ${sp.map { it.special }}")
        check("four: the roller piece stays", !res.steps[0].cleared.contains(at(3, 2)))
    }
    run {
        val e = Match3Engine(seed = 5); calm(e); e.movesLeft = 5
        e.type[at(3, 0)] = 0; e.type[at(3, 1)] = 0; e.type[at(3, 3)] = 0; e.type[at(3, 4)] = 0; e.type[at(3, 2)] = 1; e.type[at(2, 2)] = 0
        val res = e.swap(at(2, 2), at(3, 2))
        val sp = res.steps[0].spawned
        check("five: sparkle born", sp.size == 1 && sp[0].special == Match3Engine.SPARKLE, "got ${sp.map { it.special }}")
    }
    // ---- L shape: a splash ----
    run {
        val e = Match3Engine(seed = 6); calm(e); e.movesLeft = 5
        // horizontal two at (4,0),(4,1) and vertical two at (2,2),(3,2) plus the piece that completes both at (4,2)
        e.type[at(4, 0)] = 0; e.type[at(4, 1)] = 0; e.type[at(2, 2)] = 0; e.type[at(3, 2)] = 0
        e.type[at(4, 2)] = 3; e.type[at(4, 3)] = 0   // swap (4,2) and (4,3): 0 lands on (4,2)
        val res = e.swap(at(4, 2), at(4, 3))
        check("L: valid", res.valid)
        check("L: splash born", res.steps[0].spawned.any { it.special == Match3Engine.SPLASH }, "got ${res.steps[0].spawned.map { it.special }}")
    }

    // ---- specials firing ----
    run {
        val e = Match3Engine(seed = 7); calm(e); e.movesLeft = 5
        e.special[at(5, 3)] = Match3Engine.ROLLER_H
        // a match that includes the roller: (5,3) colour 4 with (5,4),(5,5) made 4
        val col = e.type[at(5, 3)]
        e.type[at(5, 4)] = col; e.type[at(5, 6)] = col; e.type[at(5, 5)] = (col + 1) % 5; e.type[at(4, 5)] = col
        val res = e.swap(at(4, 5), at(5, 5))
        check("roller: valid", res.valid)
        check("roller: its whole row went", (0 until W).all { res.steps[0].cleared.contains(at(5, it)) } || res.steps[0].spawned.isNotEmpty())
    }
    run {
        val e = Match3Engine(seed = 8); calm(e); e.movesLeft = 5
        e.special[at(2, 2)] = Match3Engine.SPARKLE
        val target = e.type[at(2, 3)]
        val count = e.type.count { it == target }
        check("sparkle: swap legal with a plain piece", e.canSwap(at(2, 2), at(2, 3)))
        val res = e.swap(at(2, 2), at(2, 3))
        check("sparkle: valid", res.valid)
        check("sparkle: every piece of that colour cleared", res.steps[0].cleared.size >= count, "cleared ${res.steps[0].cleared.size} of $count")
    }
    run {
        val e = Match3Engine(seed = 9); calm(e); e.movesLeft = 5
        e.special[at(2, 2)] = Match3Engine.SPARKLE; e.special[at(2, 3)] = Match3Engine.SPARKLE
        val res = e.swap(at(2, 2), at(2, 3))
        check("two sparkles: clear the board", res.valid && res.steps[0].cleared.size == W * H, "cleared ${res.steps[0].cleared.size}")
    }
    run {
        val e = Match3Engine(seed = 10); calm(e); e.movesLeft = 5
        e.special[at(3, 3)] = Match3Engine.ROLLER_H; e.special[at(3, 4)] = Match3Engine.ROLLER_V
        val res = e.swap(at(3, 3), at(3, 4))
        check("two rollers: a cross", res.valid && (0 until W).all { res.steps[0].cleared.contains(at(3, it)) } && (0 until H).all { res.steps[0].cleared.contains(at(it, 4)) })
    }

    // ---- dust ----
    run {
        val e = Match3Engine(seed = 11); calm(e)
        e.setLevel(5, intArrayOf(at(3, 0), at(3, 1), at(0, 0)), 1)
        e.type[at(3, 0)] = 0; e.type[at(3, 1)] = 0; e.type[at(3, 2)] = 1; e.type[at(2, 2)] = 0
        check("dust: starts with 3", e.dustLeft() == 3)
        e.swap(at(2, 2), at(3, 2))
        check("dust: the two under the match are gone", e.dustLeft() <= 1, "left ${e.dustLeft()}")
    }

    // ---- fall bookkeeping: replay the steps on a copy and compare ----
    run {
        val e = Match3Engine(seed = 12); e.fill(); e.setLevel(50)
        val mv = e.findMove()!!
        val before = e.type.copyOf()
        val res = e.swap(mv[0], mv[1])
        check("replay: valid", res.valid)
        // every cleared cell had a piece, every fall moves a piece down, fresh pieces come from above the board
        var ok = true
        for (s in res.steps) {
            for (f in s.falls) if (f.to <= f.from) ok = false
            for (f in s.fresh) if (f.fromRow >= 0) ok = false
        }
        check("replay: falls go down, new pieces come from above", ok)
        check("replay: board changed", !before.contentEquals(e.type))
    }

    // ---- many random games: the invariants always hold ----
    for (seed in 1L..60L) {
        val e = Match3Engine(seed = seed); e.fill(); e.setLevel(1000)
        val rnd = java.util.Random(seed)
        var okAll = true
        for (n in 0 until 150) {
            // pick a random legal move
            val moves = ArrayList<IntArray>()
            for (i in 0 until W * H) {
                if (i % W + 1 < W && e.canSwap(i, i + 1)) moves += intArrayOf(i, i + 1)
                if (i / W + 1 < H && e.canSwap(i, i + W)) moves += intArrayOf(i, i + W)
            }
            if (moves.isEmpty()) { okAll = false; println("seed $seed move $n: no legal move left"); break }
            val m = moves[rnd.nextInt(moves.size)]
            val res = e.swap(m[0], m[1])
            if (!res.valid) { okAll = false; println("seed $seed move $n: a legal move was refused"); break }
            if (e.type.any { it < 0 }) { okAll = false; println("seed $seed move $n: empty cell left"); break }
            if (e.findRuns(null).isNotEmpty()) { okAll = false; println("seed $seed move $n: run left standing"); break }
            if (e.findMove() == null) { okAll = false; println("seed $seed move $n: dead board"); break }
            if (res.steps.isEmpty()) { okAll = false; println("seed $seed move $n: no steps"); break }
        }
        check("random game $seed", okAll)
    }

    println("match3: $pass passed, $fail failed")
    if (fail > 0) System.exit(1)
}
