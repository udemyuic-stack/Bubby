package com.example.buddy.ui

import android.view.Gravity
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import kotlin.random.Random

/**
 * Games that play inside the app: every move answers at once (no network, no floating island), the mascot reacts to each one.
 * [kind] is "guess", "rps" or "memory". The voice games (riddles, quiz, word chain) go through the assistant instead.
 */
class GameScreen(act: UiActivity, private val kind: String) : Screen(act) {
    override fun title() = when (kind) {
        "guess" -> t("Угадай число", "Guess the number", "Sonni top")
        "rps" -> t("Камень-ножницы-бумага", "Rock-paper-scissors", "Tosh-qaychi-qog'oz")
        else -> t("Найди пару", "Memory match", "Juftini top")
    }

    private val prefs get() = ctx.getSharedPreferences("buddy", android.content.Context.MODE_PRIVATE)

    /** Bubby's face: changes with every move. */
    private lateinit var face: TextView
    private lateinit var line: TextView

    private fun react(emoji: String, text: String) {
        face.text = emoji; line.text = text
        face.jump()
    }

    override fun render(c: LinearLayout) {
        val top = ctx.col {
            gravity = Gravity.CENTER_HORIZONTAL
            face = ctx.text("🙂", T.Headline).apply { textSize = 56f; gravity = Gravity.CENTER }
            line = ctx.text("", T.Body).apply { gravity = Gravity.CENTER; textSize = 16f }
            add(face, WRAP, WRAP, top = 8)
            add(line, MATCH, WRAP, top = 4, bottom = 8)
        }
        c.add(top, MATCH, WRAP)
        when (kind) {
            "guess" -> guess(c)
            "rps" -> rps(c)
            else -> memory(c)
        }
    }

    // ---- guess the number -----------------------------------------------------------------------------------------

    private fun guess(c: LinearLayout) {
        var secret = Random.nextInt(1, 101)
        var tries = 0
        var lo = 1; var hi = 100
        val range = ctx.text("", T.Small).apply { gravity = Gravity.CENTER }
        val input: EditText = ctx.field(t("Число от 1 до 100", "A number from 1 to 100", "1 dan 100 gacha son"), number = true).apply {
            gravity = Gravity.CENTER; textSize = 22f
            imeOptions = EditorInfo.IME_ACTION_DONE
        }
        fun status() { range.text = t("Между $lo и $hi · попыток: $tries", "Between $lo and $hi · tries: $tries", "$lo va $hi orasida · urinishlar: $tries") }
        fun reset() {
            secret = Random.nextInt(1, 101); tries = 0; lo = 1; hi = 100
            input.setText(""); input.isEnabled = true
            react("🤔", t("Я загадал число от 1 до 100. Угадаешь?", "I thought of a number from 1 to 100. Can you guess it?", "1 dan 100 gacha son o'yladim. Topa olasanmi?"))
            status()
        }
        fun check() {
            val v = input.text.toString().trim().toIntOrNull()
            if (v == null || v !in 1..100) { input.shake(); react("🙄", t("Нужно число от 1 до 100", "I need a number from 1 to 100", "1 dan 100 gacha son kerak")); return }
            tries++
            input.setText("")
            when {
                v == secret -> {
                    val best = prefs.getInt("best_guess", 0)
                    if (best == 0 || tries < best) prefs.edit().putInt("best_guess", tries).apply()
                    react("🎉", t("Угадал за $tries! Рекорд: ${minOf(tries, if (best == 0) tries else best)}", "You got it in $tries! Best: ${minOf(tries, if (best == 0) tries else best)}", "$tries urinishda topding! Rekord: ${minOf(tries, if (best == 0) tries else best)}"))
                    input.isEnabled = false
                    act.confetti()
                }
                v < secret -> { lo = maxOf(lo, v + 1); react("😏", t("Больше ▲", "Higher ▲", "Kattaroq ▲")) }
                else -> { hi = minOf(hi, v - 1); react("😏", t("Меньше ▼", "Lower ▼", "Kichikroq ▼")) }
            }
            status()
        }
        input.setOnEditorActionListener { _, id, _ -> if (id == EditorInfo.IME_ACTION_DONE) { check(); true } else false }
        c.add(range, MATCH, WRAP, bottom = 10)
        c.add(input, MATCH, WRAP)
        c.add(ctx.button(t("Проверить", "Check", "Tekshirish")) { check() }, MATCH, WRAP, top = 10)
        c.add(ctx.button(t("Новая игра", "New game", "Yangi o'yin"), BtnStyle.Tonal) { reset() }, MATCH, WRAP, top = 8)
        reset()
    }

    // ---- rock, paper, scissors ------------------------------------------------------------------------------------

    private fun rps(c: LinearLayout) {
        val picks = listOf("✊", "✋", "✌️")
        var you = 0; var me = 0; var tie = 0
        val score = ctx.text("", T.Title).apply { gravity = Gravity.CENTER; textSize = 17f }
        val result = ctx.text("", T.Body).apply { gravity = Gravity.CENTER; textSize = 40f }
        fun showScore() { score.text = t("Ты $you · Bubby $me · ничьи $tie", "You $you · Bubby $me · draws $tie", "Sen $you · Bubby $me · durang $tie") }
        fun play(i: Int) {
            val b = Random.nextInt(3)
            result.text = picks[i] + "  vs  " + picks[b]
            result.pop()
            when ((i - b + 3) % 3) {
                0 -> { tie++; react("😅", t("Ничья!", "A draw!", "Durang!")) }
                1 -> { you++; react("😆", t("Ты выиграл раунд!", "You win this round!", "Bu raundda sen yutding!")); result.burst() }
                else -> { me++; react("😎", t("Этот раунд мой!", "This round is mine!", "Bu raund meniki!")) }
            }
            showScore()
        }
        c.add(result, MATCH, WRAP, bottom = 6)
        c.add(score, MATCH, WRAP, bottom = 14)
        val row = ctx.row(Gravity.CENTER)
        picks.forEachIndexed { i, p ->
            val b = ctx.text(p, T.Headline).apply {
                textSize = 40f; gravity = Gravity.CENTER
                background = ctx.rounded(P.container, 24f, P.outlineVariant)
                press { play(i) }
            }
            row.addView(b, LinearLayout.LayoutParams(0, ctx.dp(96), 1f).apply { setMargins(ctx.dp(5), 0, ctx.dp(5), 0) })
        }
        c.add(row, MATCH, WRAP)
        c.add(ctx.button(t("Сбросить счёт", "Reset the score", "Hisobni nolga tushirish"), BtnStyle.Tonal) { you = 0; me = 0; tie = 0; showScore(); result.text = ""; react("🙂", t("Давай сначала!", "Let's start again!", "Qaytadan boshlaymiz!")) }, MATCH, WRAP, top = 16)
        showScore()
        react("😎", t("Выбирай — я уже выбрал!", "Pick one, I have already picked!", "Tanla, men tanlab bo'ldim!"))
    }

    // ---- memory match ---------------------------------------------------------------------------------------------

    private fun memory(c: LinearLayout) {
        val faces = listOf("🐶", "🐱", "🦊", "🐼", "🐸", "🐵", "🦄", "🐙")
        val cards = ArrayList<TextView>()
        var values = emptyList<String>()
        val matched = BooleanArray(16)
        var first = -1
        var busy = false
        var moves = 0
        var pairs = 0
        val info = ctx.text("", T.Small).apply { gravity = Gravity.CENTER }
        fun status() {
            val best = prefs.getInt("best_memory", 0)
            info.text = t("Ходов: $moves" + (if (best > 0) " · рекорд: $best" else ""), "Moves: $moves" + (if (best > 0) " · best: $best" else ""), "Yurishlar: $moves" + (if (best > 0) " · rekord: $best" else ""))
        }
        fun flip(i: Int, up: Boolean, then: () -> Unit = {}) {
            val v = cards[i]
            v.animate().cancel()
            v.animate().rotationY(90f).setDuration(90).withEndAction {
                v.text = if (up) values[i] else "?"
                v.rotationY = -90f
                v.animate().rotationY(0f).setDuration(90).withEndAction { then() }.start()
            }.start()
        }
        fun deal() {
            values = (faces + faces).shuffled()
            for (i in 0 until 16) { matched[i] = false; cards[i].text = "?"; cards[i].rotationY = 0f; cards[i].alpha = 1f }
            first = -1; busy = false; moves = 0; pairs = 0
            status()
            react("🧐", t("Найди все пары!", "Find all the pairs!", "Hamma juftlarni top!"))
        }
        fun tap(i: Int) {
            if (busy || matched[i] || i == first) return
            if (first < 0) { first = i; flip(i, true); return }
            val a = first; first = -1
            moves++; busy = true
            status()
            flip(i, true) {
                if (values[a] == values[i]) {
                    matched[a] = true; matched[i] = true; pairs++; busy = false
                    cards[a].alpha = .45f; cards[i].alpha = .45f
                    cards[i].burst()
                    if (pairs == 8) {
                        val best = prefs.getInt("best_memory", 0)
                        if (best == 0 || moves < best) prefs.edit().putInt("best_memory", moves).apply()
                        react("🎉", t("Все пары за $moves ходов!", "All pairs in $moves moves!", "Hamma juft $moves yurishda!"))
                        status(); act.confetti()
                    } else react("😄", t("Пара!", "A pair!", "Juft!"))
                } else {
                    react("😬", t("Не то…", "Not those…", "Bular emas…"))
                    cards[i].postDelayed({ flip(a, false); flip(i, false) { busy = false } }, 650)
                }
            }
        }
        c.add(info, MATCH, WRAP, bottom = 10)
        for (r in 0 until 4) {
            val row = ctx.row(Gravity.CENTER)
            for (k in 0 until 4) {
                val idx = r * 4 + k
                val cell = ctx.text("?", T.Headline).apply {
                    textSize = 30f; gravity = Gravity.CENTER
                    background = ctx.rounded(P.container, 20f, P.outlineVariant)
                    cameraDistance = ctx.dpf(8000f)
                    press { tap(idx) }
                }
                cards += cell
                row.addView(cell, LinearLayout.LayoutParams(0, ctx.dp(74), 1f).apply { setMargins(ctx.dp(4), ctx.dp(4), ctx.dp(4), ctx.dp(4)) })
            }
            c.add(row, MATCH, WRAP)
        }
        c.add(ctx.button(t("Новая игра", "New game", "Yangi o'yin"), BtnStyle.Tonal) { deal() }, MATCH, WRAP, top = 12)
        deal()
    }
}

/** A quick sideways shake: the input was wrong. */
private fun View.shake() {
    animate().cancel()
    animate().translationX(-context.dpf(10f)).setDuration(60).withEndAction {
        animate().translationX(context.dpf(10f)).setDuration(90).withEndAction { animate().translationX(0f).setDuration(60).start() }.start()
    }.start()
}
