package com.example.buddy.ui

import kotlin.math.abs

class BeatGame(val chart: BeatChart) {
    enum class Ev { PERFECT, GOOD, MISS, BREAK, WIN, FAIL }

    var onEvent: ((Ev, Int) -> Unit)? = null

    var score = 0
    var combo = 0
    var maxCombo = 0
    var health = 100
    var perfect = 0
    var good = 0
    var miss = 0
    var finished = false
    var won = false
    private val down = BooleanArray(chart.rank.lanes)

    val mult: Int get() = (1 + combo / 10).coerceAtMost(4)
    val total: Int get() = chart.notes.size

    val accuracy: Float get() = if (total == 0) 0f else (perfect + good * 0.6f) / total

    val stars: Int
        get() = if (!won) 0 else when {
            accuracy >= 0.95f -> 3
            accuracy >= 0.85f -> 2
            accuracy >= 0.70f -> 1
            else -> 0
        }

    private fun emit(e: Ev, lane: Int) { onEvent?.invoke(e, lane) }

    private fun heal(n: Int) { health = (health + n).coerceAtMost(100) }

    private fun lose(n: Int, lane: Int) {
        health -= n
        if (health <= 0 && !finished) { health = 0; finished = true; won = false; emit(Ev.FAIL, lane) }
    }

    private fun gain(base: Int) {
        combo++
        if (combo > maxCombo) maxCombo = combo
        score += base * mult
    }

    fun tap(lane: Int, t: Float) {
        if (finished || lane !in down.indices) return
        down[lane] = true
        var best: BeatNote? = null
        for (n in chart.notes) {
            if (n.time > t + GOOD) break
            if (n.lane != lane || n.state != BeatNote.PENDING || n.time < t - GOOD) continue
            best = n
            break
        }
        val n = best ?: return
        val d = abs(n.time - t)
        if (d <= PERFECT) { perfect++; gain(100); heal(2); emit(Ev.PERFECT, lane) } else { good++; gain(60); heal(1); emit(Ev.GOOD, lane) }
        n.state = if (n.len > 0f) BeatNote.HELD else BeatNote.HIT
    }

    fun release(lane: Int, t: Float) {
        if (lane !in down.indices) return
        down[lane] = false
        for (n in chart.notes) {
            if (n.state != BeatNote.HELD || n.lane != lane) continue
            if (t < n.time + n.len - 0.15f) {
                n.state = BeatNote.BROKEN
                combo = 0
                emit(Ev.BREAK, lane)
                lose(6, lane)
            } else {
                n.state = BeatNote.HIT
                gain(50)
            }
        }
    }

    fun update(t: Float) {
        if (finished) return
        for (n in chart.notes) {
            if (n.time > t + 0.2f) break
            when (n.state) {
                BeatNote.PENDING -> if (n.time < t - GOOD) {
                    n.state = BeatNote.MISSED
                    miss++
                    combo = 0
                    emit(Ev.MISS, n.lane)
                    lose(12, n.lane)
                    if (finished) return
                }
                BeatNote.HELD -> if (t >= n.time + n.len) {
                    n.state = BeatNote.HIT
                    gain(50)
                }
            }
        }
        if (t >= chart.notes.lastOrNull()?.let { it.time + it.len + 1f } ?: 0f) {
            finished = true
            won = health > 0
            emit(if (won) Ev.WIN else Ev.FAIL, 0)
        }
    }

    fun clearDown() { down.fill(false) }

    fun isDown(lane: Int) = lane in down.indices && down[lane]

    companion object {
        const val PERFECT = 0.06f
        const val GOOD = 0.12f
    }
}
