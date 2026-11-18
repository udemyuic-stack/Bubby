package com.example.buddy.ui

import kotlin.random.Random

class BeatRank(
    val ru: String, val en: String, val uz: String,
    val bpm: Int, val lanes: Int,
    val density: Float, val eighth: Float, val hold: Float, val chord: Float,
    val top: Int, val bottom: Int, val glow: Int
)

class BeatNote(val time: Float, val lane: Int, val len: Float) {
    var state = PENDING
    var pitch = 0

    companion object {
        const val PENDING = 0
        const val HELD = 1
        const val HIT = 2
        const val MISSED = 3
        const val BROKEN = 4
    }
}

class BeatChart(val rank: BeatRank, val notes: List<BeatNote>, val beatSec: Float, val leadBeats: Int, val totalBeats: Int) {
    val duration: Float get() = totalBeats * beatSec
}

object BeatTracks {
    const val LEAD_BEATS = 4
    val progression = intArrayOf(0, 5, 7, 3)
    val scale = intArrayOf(0, 3, 5, 7, 10, 12)

    val ranks = listOf(
        BeatRank("Бронза", "Bronze", "Bronza", 90, 3, 0.78f, 0f, 0f, 0f, 0xFF1B1030.toInt(), 0xFF5A2E1E.toInt(), 0xFFFFB36B.toInt()),
        BeatRank("Серебро", "Silver", "Kumush", 105, 3, 0.85f, 0.25f, 0f, 0f, 0xFF0B1A33.toInt(), 0xFF3A4A66.toInt(), 0xFFB8D4FF.toInt()),
        BeatRank("Золото", "Gold", "Oltin", 120, 4, 0.88f, 0.3f, 0f, 0f, 0xFF2A1A05.toInt(), 0xFF6B4A0C.toInt(), 0xFFFFD23A.toInt()),
        BeatRank("Платина", "Platinum", "Platina", 135, 4, 0.9f, 0.35f, 0.18f, 0f, 0xFF04202B.toInt(), 0xFF0B5C5C.toInt(), 0xFF7DF9FF.toInt()),
        BeatRank("Алмаз", "Diamond", "Olmos", 150, 4, 0.92f, 0.4f, 0.18f, 0.2f, 0xFF1A0A33.toInt(), 0xFF4A1A8C.toInt(), 0xFFB79BFF.toInt()),
        BeatRank("Легенда", "Legend", "Afsona", 165, 4, 0.95f, 0.5f, 0.2f, 0.3f, 0xFF2A0A18.toInt(), 0xFF8C1A3A.toInt(), 0xFFFF7BE0.toInt())
    )

    fun build(index: Int): BeatChart {
        val rank = ranks[index]
        val beat = 60f / rank.bpm
        val total = (LEAD_BEATS + rank.bpm / 4 * 4 + 4)
        val rnd = Random(1000 + index)
        val busy = FloatArray(rank.lanes)
        val notes = ArrayList<BeatNote>()
        var last = rank.lanes / 2

        fun pick(avoid: Int, at: Float): Int {
            for (tries in 0 until 6) {
                val l = if (rnd.nextFloat() < 0.6f) (last + rnd.nextInt(-1, 2)).coerceIn(0, rank.lanes - 1) else rnd.nextInt(rank.lanes)
                if (l != avoid && busy[l] <= at) return l
            }
            for (l in 0 until rank.lanes) if (l != avoid && busy[l] <= at) return l
            return -1
        }

        fun add(time: Float, lane: Int, len: Float, bar: Int) {
            val n = BeatNote(time, lane, len)
            n.pitch = scale[lane % scale.size] + progression[bar % progression.size]
            notes.add(n)
            busy[lane] = time + len + beat * 0.25f
        }

        var b = LEAD_BEATS
        while (b < total - 2) {
            val bar = (b - LEAD_BEATS) / 4
            val t = b * beat
            if (rnd.nextFloat() < rank.density || (b - LEAD_BEATS) % 4 == 0) {
                val lane = pick(-1, t)
                if (lane >= 0) {
                    val hold = rank.hold > 0f && rnd.nextFloat() < rank.hold
                    add(t, lane, if (hold) beat * 1.5f else 0f, bar)
                    last = lane
                    if (rank.chord > 0f && rnd.nextFloat() < rank.chord) {
                        val second = pick(lane, t)
                        if (second >= 0) add(t, second, 0f, bar)
                    }
                }
            }
            if (rank.eighth > 0f && rnd.nextFloat() < rank.eighth) {
                val te = t + beat * 0.5f
                val lane = pick(last, te)
                if (lane >= 0) { add(te, lane, 0f, bar); last = lane }
            }
            b++
        }
        notes.sortBy { it.time }
        return BeatChart(rank, notes, beat, LEAD_BEATS, total)
    }
}
