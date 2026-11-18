package com.example.buddy.ui

import kotlin.random.Random

class RunnerGame(seed: Long = System.nanoTime()) {
    enum class Power { NONE, SHIELD, SLOW, HINT }
    enum class Ev { GATE, CORRECT, WRONG, HIT, SHIELD_BREAK, ORB, POWER_READY, POWER_USED, SECTOR, BOSS, BOSS_CLEAR, LANE, OVER }

    class Gate(val phrase: RunnerPhrase, val options: List<String>, val boss: Boolean) {
        var z = START_Z
        var done = false
        var ok = false
        var pick = 1
        var t = 0f
        val correct = options.indexOf(phrase.answer)
    }

    class Item(val lane: Int, val fromLane: Int, var z: Float, val rock: Boolean, val mul: Float) {
        var spent = false

        fun laneAt(): Float {
            val k = ((8f - z) / 4f).coerceIn(0f, 1f)
            return fromLane + (lane - fromLane) * k
        }
    }

    companion object {
        const val START_Z = 14f
        const val GATES_PER_SECTOR = 8
        const val BAG_SIZE = 10
        const val MAX_HEARTS = 5
        private val RANKS = intArrayOf(0, 150, 400, 800, 1500)

        fun rank(score: Int): Int = RANKS.indexOfLast { score >= it }.coerceAtLeast(0)
    }

    private val rnd = Random(seed)
    private val review = ArrayList<Pair<RunnerPhrase, Int>>()
    private val recent = ArrayDeque<RunnerPhrase>()
    private var spawnT = 1f

    val items = ArrayList<Item>()
    val missed = LinkedHashMap<String, RunnerPhrase>()
    var onEvent: ((Ev) -> Unit)? = null

    var gate: Gate? = null
        private set
    var hearts = 3
        private set
    var score = 0
        private set
    var streak = 0
        private set
    var bag = 0
        private set
    var power = Power.NONE
        private set
    var shield = false
        private set
    var slowT = 0f
        private set
    var hint = -1
        private set
    var sector = 0
        private set
    var inSector = 0
        private set
    var bossLeft = 0
        private set
    var answered = 0
        private set
    var lane = 1
        private set
    var over = false
        private set
    var time = 0f
        private set
    private var bossFail = false

    val mult get() = (1 + streak / 3).coerceAtMost(5)
    val sectorName get() = if (sector < RunnerPhrases.sectors.size) RunnerPhrases.sectors[sector].first else "MIX"

    fun start() {
        hearts = 3; score = 0; streak = 0; bag = 0; power = Power.NONE; shield = false; slowT = 0f; hint = -1
        sector = 0; inSector = 0; bossLeft = 0; bossFail = false; answered = 0; lane = 1; over = false; time = 0f
        spawnT = 1f
        items.clear(); missed.clear(); review.clear(); recent.clear()
        newGate()
    }

    fun setLane(i: Int) {
        val l = i.coerceIn(0, 2)
        if (l == lane) return
        lane = l
        emit(Ev.LANE)
    }

    fun usePower() {
        when (power) {
            Power.NONE -> return
            Power.SHIELD -> shield = true
            Power.SLOW -> slowT = 6f
            Power.HINT -> {
                val g = gate?.takeIf { !it.done } ?: return
                hint = g.options.indices.filter { it != g.correct }.random(rnd)
            }
        }
        power = Power.NONE
        emit(Ev.POWER_USED)
    }

    fun speed(): Float {
        val base = 3.4f + 0.55f * minOf(sector, 10) + 0.03f * minOf(answered, 70)
        val boss = if (gate?.boss == true) 1.2f else 1f
        val slow = if (slowT > 0f) 0.5f else 1f
        return base.coerceAtMost(10f) * boss * slow
    }

    fun update(dt: Float) {
        if (over) return
        time += dt
        slowT = maxOf(0f, slowT - dt)
        val sp = speed()
        val g = gate
        if (g != null) {
            g.z -= sp * dt
            if (!g.done && g.z <= 0f) resolve(g)
            if (g.done) {
                g.t += dt
                if (g.t > 0.9f && !over) advance(g)
            }
        }
        val cur = gate
        if (cur != null && !cur.done && cur.z < 9f) {
            spawnT -= dt
            if (spawnT <= 0f) {
                spawnItem(cur.boss)
                spawnT = maxOf(0.45f, 0.95f - 0.06f * sector) + rnd.nextFloat() * 0.5f
            }
        }
        if (over) return
        val it = items.iterator()
        while (it.hasNext()) {
            val item = it.next()
            item.z -= sp * item.mul * dt
            if (!item.spent && item.z <= 0f) {
                item.spent = true
                if (item.lane == lane) touch(item)
            }
            if (item.z < -1f) it.remove()
            if (over) return
        }
    }

    private fun touch(item: Item) {
        if (item.rock) {
            streak = 0
            loseLife()
        } else {
            score += 2
            addBag(1)
            emit(Ev.ORB)
        }
    }

    private fun spawnItem(boss: Boolean) {
        val chance = minOf(0.8f, 0.55f + 0.04f * sector) + (if (boss) 0.15f else 0f)
        val rock = rnd.nextFloat() < minOf(chance, 0.9f)
        val to = rnd.nextInt(3)
        val drift = rock && sector >= 3 && rnd.nextFloat() < 0.45f
        val from = if (drift) (to + 1 + rnd.nextInt(2)) % 3 else to
        val mul = if (rock && sector >= 2 && rnd.nextFloat() < 0.3f) 1.4f else 1f
        items.add(Item(to, from, START_Z, rock, mul))
        if (rock && sector >= 2 && rnd.nextFloat() < 0.35f) {
            val other = (to + 1 + rnd.nextInt(2)) % 3
            items.add(Item(other, other, START_Z, true, 1f))
        }
    }

    private fun resolve(g: Gate) {
        g.done = true
        g.pick = lane
        g.ok = lane == g.correct
        hint = -1
        answered++
        if (g.ok) {
            streak++
            score += (if (g.boss) 15 else 10) * mult
            addBag(1)
            emit(Ev.CORRECT)
        } else {
            if (g.boss) bossFail = true
            streak = 0
            missed[g.phrase.text] = g.phrase
            if (review.none { it.first === g.phrase }) review.add(g.phrase to answered + 4)
            emit(Ev.WRONG)
            loseLife()
        }
    }

    private fun advance(g: Gate) {
        if (g.boss) {
            bossLeft--
            if (bossLeft == 0) {
                if (!bossFail) {
                    hearts = minOf(MAX_HEARTS, hearts + 1)
                    score += 20
                    emit(Ev.BOSS_CLEAR)
                }
                sector++
                inSector = 0
                emit(Ev.SECTOR)
            }
        } else {
            inSector++
            if (inSector >= GATES_PER_SECTOR) {
                bossLeft = 3 + (if (sector >= 4) 1 else 0) + (if (sector >= 8) 1 else 0)
                bossFail = false
                emit(Ev.BOSS)
            }
        }
        newGate()
    }

    private fun newGate() {
        val pool = if (sector < RunnerPhrases.sectors.size) RunnerPhrases.sectors[sector].second else RunnerPhrases.all
        val due = review.firstOrNull { it.second <= answered }
        var phrase = due?.first
        if (due != null) review.remove(due)
        if (phrase == null) {
            var tries = 0
            do { phrase = pool[rnd.nextInt(pool.size)] } while (phrase in recent && ++tries < 8)
        }
        val p = phrase!!
        recent.addLast(p)
        if (recent.size > 6) recent.removeFirst()
        val options = (p.pool.filter { it != p.answer }.shuffled(rnd).take(2) + p.answer).shuffled(rnd)
        gate = Gate(p, options, bossLeft > 0)
        emit(Ev.GATE)
    }

    private fun addBag(n: Int) {
        bag += n
        if (bag < BAG_SIZE) return
        if (power == Power.NONE) {
            bag -= BAG_SIZE
            power = Power.values()[1 + rnd.nextInt(3)]
            emit(Ev.POWER_READY)
        } else bag = BAG_SIZE
    }

    private fun loseLife() {
        if (shield) {
            shield = false
            emit(Ev.SHIELD_BREAK)
            return
        }
        hearts--
        emit(Ev.HIT)
        if (hearts <= 0) {
            over = true
            emit(Ev.OVER)
        }
    }

    private fun emit(e: Ev) { onEvent?.invoke(e) }
}
