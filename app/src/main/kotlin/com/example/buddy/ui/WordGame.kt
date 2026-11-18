package com.example.buddy.ui

object WordGame {
    const val LEN = 5
    const val TRIES = 6
    const val ABSENT = 0
    const val PRESENT = 1
    const val CORRECT = 2

    fun secret(epochDay: Long): String = WordList.words[Math.floorMod(epochDay, WordList.words.size.toLong()).toInt()]

    fun evaluate(guess: String, secret: String): IntArray {
        val res = IntArray(LEN)
        val left = IntArray(26)
        for (i in 0 until LEN) {
            if (guess[i] == secret[i]) res[i] = CORRECT else left[secret[i] - 'a']++
        }
        for (i in 0 until LEN) {
            if (res[i] == CORRECT) continue
            val k = guess[i] - 'a'
            if (left[k] > 0) { res[i] = PRESENT; left[k]-- }
        }
        return res
    }

    fun keyStates(guesses: List<String>, secret: String): IntArray {
        val st = IntArray(26) { -1 }
        for (g in guesses) {
            val r = evaluate(g, secret)
            for (i in 0 until LEN) {
                val k = g[i] - 'a'
                if (r[i] > st[k]) st[k] = r[i]
            }
        }
        return st
    }

    fun stars(tries: Int): Int = when {
        tries <= 1 -> 3
        tries <= 3 -> 2
        else -> 1
    }

    fun share(guesses: List<String>, secret: String, won: Boolean): String {
        val sb = StringBuilder("Bubby Word ").append(if (won) guesses.size.toString() else "X").append("/").append(TRIES).append('\n')
        for (g in guesses) {
            sb.append('\n')
            for (v in evaluate(g, secret)) sb.append(when (v) { CORRECT -> "🟩"; PRESENT -> "🟨"; else -> "⬛" })
        }
        return sb.toString()
    }
}
