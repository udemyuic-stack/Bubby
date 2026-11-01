package com.example.buddy.ui

import kotlin.random.Random

class BattleOpp(
    val ru: String, val en: String, val uz: String,
    val accuracy: Float, val thinkMin: Float, val thinkMax: Float, val timer: Float, val translate: Float,
    val color: Int
)

class BattleQuestion(val prompt: String, val options: List<String>, val correct: Int, val phrase: RunnerPhrase, val translate: Boolean)

object BattleOpps {
    val all = listOf(
        BattleOpp("Сонный Bubby", "Sleepy Bubby", "Mudroq Bubby", 0.40f, 6f, 9f, 10f, 0f, 0xFF8DB3FF.toInt()),
        BattleOpp("Любопытный Bubby", "Curious Bubby", "Qiziquvchan Bubby", 0.55f, 5f, 8f, 10f, 0f, 0xFF8DFF9B.toInt()),
        BattleOpp("Умный Bubby", "Clever Bubby", "Aqlli Bubby", 0.70f, 4f, 7f, 10f, 0f, 0xFFFFD23A.toInt()),
        BattleOpp("Про Bubby", "Pro Bubby", "Pro Bubby", 0.82f, 3f, 6f, 8f, 0.15f, 0xFFFF9A4A.toInt()),
        BattleOpp("Мастер Bubby", "Master Bubby", "Usta Bubby", 0.90f, 2f, 5f, 7f, 0.25f, 0xFFFF7BE0.toInt()),
        BattleOpp("Легенда Bubby", "Legend Bubby", "Afsona Bubby", 0.96f, 2f, 4f, 6f, 0.35f, 0xFFFF5470.toInt())
    )
}

class BattleGame(val oppIndex: Int, private val meaning: (RunnerPhrase) -> String, seed: Long = System.nanoTime()) {
    enum class Ev { ROUND, PLAYER_HIT, BOT_HIT, PLAYER_WRONG, TIMEOUT, OVER }

    var onEvent: ((Ev, Int) -> Unit)? = null

    val opp = BattleOpps.all[oppIndex]
    private val rnd = Random(seed)
    private val allPhrases = RunnerPhrases.sectors.flatMap { it.second }
    private val allWords = allPhrases.flatMap { it.pool }.distinct()
    private val deck = ArrayDeque<RunnerPhrase>()

    var playerHp = 100
    var botHp = 100
    var streak = 0
    var round = 0
    var elapsed = 0f
    var question: BattleQuestion? = null
    var chosen = -1
    var resolved = false
    var over = false
    var won = false
    var hidden = BooleanArray(4)
    var botAnswered = false
    var botRight = false
    var frozen = false
    var fiftyLeft = true
    var freezeLeft = true
    var healLeft = true
    val missed = LinkedHashMap<String, RunnerPhrase>()
    private var botT = 0f
    private var pause = 0f

    val timeLeft: Float get() = (opp.timer - elapsed).coerceAtLeast(0f)

    fun start() {
        playerHp = 100; botHp = 100; streak = 0; round = 0; over = false; won = false
        fiftyLeft = true; freezeLeft = true; healLeft = true
        missed.clear()
        nextRound()
    }

    private fun draw(): RunnerPhrase {
        if (deck.isEmpty()) deck.addAll(allPhrases.shuffled(rnd))
        return deck.removeFirst()
    }

    private fun nextRound() {
        round++
        val p = draw()
        val tr = rnd.nextFloat() < opp.translate
        val q = if (tr) translation(p) else gap(p)
        question = q
        chosen = -1; resolved = false; elapsed = 0f; pause = 0f
        hidden = BooleanArray(4)
        botAnswered = false; frozen = false
        botRight = rnd.nextFloat() < opp.accuracy
        botT = opp.thinkMin + rnd.nextFloat() * (opp.thinkMax - opp.thinkMin)
        onEvent?.invoke(Ev.ROUND, round)
    }

    private fun gap(p: RunnerPhrase): BattleQuestion {
        val opts = ArrayList<String>(p.pool.shuffled(rnd))
        val extra = allWords.filter { it !in opts }.shuffled(rnd)
        var k = 0
        while (opts.size < 4 && k < extra.size) opts.add(extra[k++])
        opts.shuffle(rnd)
        return BattleQuestion(p.text, opts.map { it.uppercase() }, opts.indexOf(p.answer), p, false)
    }

    private fun translation(p: RunnerPhrase): BattleQuestion {
        val right = meaning(p)
        val others = allPhrases.filter { it !== p }.map(meaning).filter { it != right }.distinct().shuffled(rnd).take(3)
        val opts = (others + right).shuffled(rnd)
        return BattleQuestion(p.filled(), opts, opts.indexOf(right), p, true)
    }

    private fun damage(left: Float): Int = 12 + (left / opp.timer * 8f).toInt() + (if (streak > 0) minOf(10, streak * 2) else 0)

    private fun finishRound() {
        resolved = true
        pause = 1.3f
        if (playerHp <= 0 || botHp <= 0 || round >= MAX_ROUNDS) {
            over = true
            won = playerHp > 0 && (botHp <= 0 || playerHp > botHp)
        }
    }

    fun answer(i: Int) {
        val q = question ?: return
        if (resolved || over || i !in 0..3 || hidden[i]) return
        chosen = i
        if (i == q.correct) {
            val botFirst = botRight && !frozen && botT < elapsed
            if (botFirst) {
                val d = damage(opp.timer - botT).coerceAtLeast(12)
                playerHp = (playerHp - d).coerceAtLeast(0)
                streak = 0
                missed[q.phrase.text] = q.phrase
                onEvent?.invoke(Ev.BOT_HIT, d)
            } else {
                streak++
                val d = damage(timeLeft)
                botHp = (botHp - d).coerceAtLeast(0)
                onEvent?.invoke(Ev.PLAYER_HIT, d)
            }
        } else {
            streak = 0
            playerHp = (playerHp - WRONG_DAMAGE).coerceAtLeast(0)
            missed[q.phrase.text] = q.phrase
            onEvent?.invoke(Ev.PLAYER_WRONG, WRONG_DAMAGE)
        }
        finishRound()
        if (over) onEvent?.invoke(Ev.OVER, 0)
    }

    fun update(dt: Float) {
        if (over || question == null) return
        if (resolved) {
            pause -= dt
            if (pause <= 0f) nextRound()
            return
        }
        elapsed += dt
        if (!botAnswered && !frozen && elapsed >= botT) {
            botAnswered = true
            if (botRight) {
                val q = question!!
                val d = damage(opp.timer - botT).coerceAtLeast(12)
                playerHp = (playerHp - d).coerceAtLeast(0)
                streak = 0
                missed[q.phrase.text] = q.phrase
                onEvent?.invoke(Ev.BOT_HIT, d)
                finishRound()
                if (over) onEvent?.invoke(Ev.OVER, 0)
                return
            }
        }
        if (elapsed >= opp.timer) {
            val q = question!!
            streak = 0
            playerHp = (playerHp - WRONG_DAMAGE).coerceAtLeast(0)
            missed[q.phrase.text] = q.phrase
            onEvent?.invoke(Ev.TIMEOUT, WRONG_DAMAGE)
            finishRound()
            if (over) onEvent?.invoke(Ev.OVER, 0)
        }
    }

    fun fifty() {
        val q = question ?: return
        if (!fiftyLeft || resolved || over) return
        fiftyLeft = false
        var n = 0
        for (i in rnd.nextInt(4).let { s -> (0..3).map { (it + s) % 4 } }) {
            if (i != q.correct && n < 2) { hidden[i] = true; n++ }
        }
    }

    fun freeze() {
        if (!freezeLeft || resolved || over) return
        freezeLeft = false
        frozen = true
    }

    fun heal() {
        if (!healLeft || over) return
        healLeft = false
        playerHp = (playerHp + 20).coerceAtMost(100)
    }

    val stars: Int
        get() = if (!won) 0 else when {
            playerHp > 75 -> 3
            playerHp > 50 -> 2
            else -> 1
        }

    companion object {
        const val WRONG_DAMAGE = 8
        const val MAX_ROUNDS = 25
    }
}
