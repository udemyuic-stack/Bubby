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
 * [kind] is "guess", "rps", "memory" (all instant) or "twenty" (the assistant asks yes/no questions: one short network answer per turn). The voice games (riddles, quiz, word chain) go through the assistant instead.
 */
class GameScreen(act: UiActivity, private val kind: String) : Screen(act) {
    override fun title() = when (kind) {
        "guess" -> t("Угадай число", "Guess the number", "Sonni top")
        "rps" -> t("Камень-ножницы-бумага", "Rock-paper-scissors", "Tosh-qaychi-qog'oz")
        "twenty" -> t("20 вопросов", "20 questions", "20 ta savol")
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

    private var stage = -1

    private fun outcomeText() = ctx.text("", T.Title, P.orange).apply { gravity = Gravity.CENTER; textSize = 18f }

    private fun finish(st: Int, outcome: TextView) {
        val earned = GameStages.pay(ctx, kind, stage, st)
        outcome.text = GameStages.starsText(st) + if (earned > 0) "  +$earned ⭐" else ""
    }

    private fun stagesButton(c: LinearLayout) {
        c.add(ctx.button(t("Выбор уровня", "Stages", "Bosqichlar"), BtnStyle.Outline) { stage = -1; refresh() }, MATCH, WRAP, top = 8)
    }

    private fun goal(i: Int): String = when (kind) {
        "guess" -> {
            val g = GameStages.guess[i]
            t("от 1 до ${g.max} · ${g.tries} попыток", "1 to ${g.max} · ${g.tries} tries", "1 dan ${g.max} gacha · ${g.tries} urinish")
        }
        "rps" -> {
            val r = GameStages.rps[i]
            val how = when (r.mode) {
                0 -> t("Bubby играет случайно", "Bubby plays at random", "Bubby tasodifiy o'ynaydi")
                1 -> t("Bubby бьёт твой прошлый ход", "Bubby counters your last move", "Bubby oldingi yuringni uradi")
                2 -> t("Bubby бьёт твой частый ход", "Bubby counters your favourite", "Bubby sevimli yuringni uradi")
                else -> t("Bubby читает твои ходы", "Bubby reads your moves", "Bubby yuringni o'qiydi")
            }
            t("до ${r.target} побед · $how", "first to ${r.target} · $how", "${r.target} g'alabagacha · $how")
        }
        else -> {
            val m = GameStages.memory[i]
            t("${m.cards} карт · ${m.moves} ходов", "${m.cards} cards · ${m.moves} moves", "${m.cards} karta · ${m.moves} yurish")
        }
    }

    private fun renderPicker(c: LinearLayout) {
        val accents = intArrayOf(P.orange, P.violet, P.teal, P.rose, P.sky)
        c.add(ctx.card(pad = 16) {
            add(ctx.text(title(), T.Title), MATCH, WRAP)
            add(ctx.text(t("Каждый уровень сложнее. Звезда на уровне открывает следующий.",
                "Every stage is harder. A star on a stage unlocks the next one.",
                "Har bosqich qiyinroq. Bosqichdagi yulduz keyingisini ochadi."), T.Body), MATCH, WRAP, top = 4)
        }, MATCH, WRAP, bottom = 10)
        for (i in 0 until GameStages.COUNT) {
            val open = GameStages.unlocked(ctx, kind, i)
            c.add(ctx.card(pad = 14, onClick = if (open) ({ stage = i; refresh() }) else null) {
                alpha = if (open) 1f else 0.5f
                add(ctx.row {
                    add(ctx.row(Gravity.CENTER) {
                        background = ctx.rounded(accents[i] and 0xFFFFFF or 0x55000000, 14f)
                        add(ctx.text(if (open) "${i + 1}" else "🔒", T.Title), WRAP, WRAP)
                    }, ctx.dp(48), ctx.dp(48), end = 12)
                    add(ctx.col {
                        add(ctx.text(t("Уровень ${i + 1}", "Stage ${i + 1}", "${i + 1}-bosqich"), T.Label), MATCH, WRAP)
                        add(ctx.text(goal(i), T.Small), MATCH, WRAP, top = 2)
                    }, 0, WRAP, 1f)
                    add(ctx.text(GameStages.starsText(GameStages.stars(ctx, kind, i)), T.Title, P.orange), WRAP, WRAP)
                })
            }, MATCH, WRAP, bottom = 8)
        }
    }

    override fun render(c: LinearLayout) {
        if (kind != "twenty" && stage < 0) { renderPicker(c); return }
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
            "twenty" -> twenty(c)
            else -> memory(c)
        }
    }

    // ---- guess the number -----------------------------------------------------------------------------------------

    private fun guess(c: LinearLayout) {
        val st = GameStages.guess[stage]
        val max = st.max
        var secret = Random.nextInt(1, max + 1)
        var tries = 0
        var over = false
        var lo = 1; var hi = max
        val range = ctx.text("", T.Small).apply { gravity = Gravity.CENTER }
        val outcome = outcomeText()
        val input: EditText = ctx.field(t("Число от 1 до $max", "A number from 1 to $max", "1 dan $max gacha son"), number = true).apply {
            gravity = Gravity.CENTER; textSize = 22f
            imeOptions = EditorInfo.IME_ACTION_DONE
        }
        fun status() { range.text = t("Между $lo и $hi · попыток: $tries из ${st.tries}", "Between $lo and $hi · tries: $tries of ${st.tries}", "$lo va $hi orasida · urinishlar: $tries/${st.tries}") }
        fun reset() {
            secret = Random.nextInt(1, max + 1); tries = 0; lo = 1; hi = max; over = false
            input.setText(""); input.isEnabled = true
            outcome.text = ""
            react("🤔", t("Я загадал число от 1 до $max. Угадаешь за ${st.tries}?", "I thought of a number from 1 to $max. Can you guess it in ${st.tries}?", "1 dan $max gacha son o'yladim. ${st.tries} urinishda topa olasanmi?"))
            status()
        }
        fun check() {
            if (over) return
            val v = input.text.toString().trim().toIntOrNull()
            if (v == null || v !in 1..max) { input.shake(); react("🙄", t("Нужно число от 1 до $max", "I need a number from 1 to $max", "1 dan $max gacha son kerak")); return }
            tries++
            input.setText("")
            when {
                v == secret -> {
                    over = true
                    val key = "best_guess_$stage"
                    val best = prefs.getInt(key, 0)
                    if (best == 0 || tries < best) prefs.edit().putInt(key, tries).apply()
                    react("🎉", t("Угадал за $tries! Рекорд: ${minOf(tries, if (best == 0) tries else best)}", "You got it in $tries! Best: ${minOf(tries, if (best == 0) tries else best)}", "$tries urinishda topding! Rekord: ${minOf(tries, if (best == 0) tries else best)}"))
                    input.isEnabled = false
                    finish(GameStages.guessStars(st, tries), outcome)
                    act.confetti()
                    face.cheer()
                }
                tries >= st.tries -> {
                    over = true
                    input.isEnabled = false
                    react("😢", t("Попытки кончились. Я загадал $secret.", "Out of tries. The number was $secret.", "Urinishlar tugadi. Men $secret ni o'ylagan edim."))
                }
                v < secret -> { lo = maxOf(lo, v + 1); react("😏", t("Больше ▲", "Higher ▲", "Kattaroq ▲")) }
                else -> { hi = minOf(hi, v - 1); react("😏", t("Меньше ▼", "Lower ▼", "Kichikroq ▼")) }
            }
            status()
        }
        input.setOnEditorActionListener { _, id, _ -> if (id == EditorInfo.IME_ACTION_DONE) { check(); true } else false }
        c.add(range, MATCH, WRAP, bottom = 6)
        c.add(outcome, MATCH, WRAP, bottom = 6)
        c.add(input, MATCH, WRAP)
        c.add(ctx.button(t("Проверить", "Check", "Tekshirish")) { check() }, MATCH, WRAP, top = 10)
        c.add(ctx.button(t("Новая игра", "New game", "Yangi o'yin"), BtnStyle.Tonal) { reset() }, MATCH, WRAP, top = 8)
        stagesButton(c)
        reset()
    }

    // ---- rock, paper, scissors ------------------------------------------------------------------------------------

    private fun rps(c: LinearLayout) {
        val st = GameStages.rps[stage]
        val picks = listOf("✊", "✋", "✌️")
        var bot = GameStages.RpsBot(st)
        var you = 0; var me = 0; var tie = 0
        var over = false
        val score = ctx.text("", T.Title).apply { gravity = Gravity.CENTER; textSize = 17f }
        val result = ctx.text("", T.Body).apply { gravity = Gravity.CENTER; textSize = 40f }
        val outcome = outcomeText()
        fun showScore() { score.text = t("Ты $you · Bubby $me · ничьи $tie · до ${st.target}", "You $you · Bubby $me · draws $tie · first to ${st.target}", "Sen $you · Bubby $me · durang $tie · ${st.target} gacha") }
        fun play(i: Int) {
            if (over) return
            val b = bot.next()
            bot.record(i)
            result.text = picks[i] + "  vs  " + picks[b]
            result.pop()
            when ((i - b + 3) % 3) {
                0 -> { tie++; react("😅", t("Ничья!", "A draw!", "Durang!")) }
                1 -> { you++; react("😆", t("Ты выиграл раунд!", "You win this round!", "Bu raundda sen yutding!")); result.burst() }
                else -> { me++; react("😎", t("Этот раунд мой!", "This round is mine!", "Bu raund meniki!")) }
            }
            showScore()
            if (you >= st.target) {
                over = true
                react("🎉", t("Ты выиграл серию $you:$me!", "You won the series $you:$me!", "Seriyani $you:$me hisobida yutding!"))
                finish(GameStages.rpsStars(you - me), outcome)
                act.confetti()
                face.cheer()
            } else if (me >= st.target) {
                over = true
                react("😎", t("Серия за мной $me:$you!", "I won the series $me:$you!", "Seriya menda $me:$you!"))
            }
        }
        c.add(result, MATCH, WRAP, bottom = 6)
        c.add(score, MATCH, WRAP, bottom = 6)
        c.add(outcome, MATCH, WRAP, bottom = 8)
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
        c.add(ctx.button(t("Новая серия", "New series", "Yangi seriya"), BtnStyle.Tonal) {
            you = 0; me = 0; tie = 0; over = false; bot = GameStages.RpsBot(st)
            showScore(); result.text = ""; outcome.text = ""
            react("🙂", t("Давай сначала!", "Let's start again!", "Qaytadan boshlaymiz!"))
        }, MATCH, WRAP, top = 16)
        stagesButton(c)
        showScore()
        react("😎", t("Выбирай — я уже выбрал!", "Pick one, I have already picked!", "Tanla, men tanlab bo'ldim!"))
    }

    // ---- 20 questions: the user thinks of something, Bubby asks yes/no questions and guesses ------------------------

    private fun twenty(c: LinearLayout) {
        val turns = ArrayList<Pair<String, String>>()   // the whole talk, for the model
        var asked = 0
        var guessing = false
        var busy = false
        val counter = ctx.text("", T.Small).apply { gravity = Gravity.CENTER }
        val answers = ctx.col()
        val lang = when (com.example.buddy.Tr.lang(ctx)) { "ru" -> "Russian"; "uz" -> "Uzbek (Latin script)"; else -> "English" }
        val system = "You are playing 20 Questions. The user is thinking of an animal, object, food or well-known thing (never a private person). " +
            "Each turn you either ask ONE short yes/no question, or, when you are fairly sure, make a guess. You may make a guess at any time, and you must guess by question 20. " +
            "Do not repeat questions. Reply with STRICT JSON only, no other text: {\"say\":\"<your question or guess, in $lang, max 14 words>\",\"guess\":true or false}."
        fun parse(raw: String): Pair<String, Boolean> {
            val a = raw.indexOf('{'); val b = raw.lastIndexOf('}')
            if (a >= 0 && b > a) try {
                val o = org.json.JSONObject(raw.substring(a, b + 1))
                val s = o.optString("say", "").trim()
                if (s.isNotEmpty()) return s to o.optBoolean("guess", false)
            } catch (_: Exception) { }
            return raw.trim().take(160) to false
        }
        lateinit var showButtons: (List<Pair<String, String>>) -> Unit
        fun status() { counter.text = if (asked == 0) "" else t("Вопрос $asked из 20", "Question $asked of 20", "$asked/20-savol") }
        var lastSent = ""
        fun ask(userSays: String) {
            if (busy) return
            busy = true
            lastSent = userSays
            turns += "user" to userSays
            react("🤔", t("Думаю…", "Thinking…", "O'ylayapman…"))
            showButtons(emptyList())
            Thread {
                val raw = try { com.example.buddy.Brain(ctx.applicationContext).chatOnce(system + " Questions asked so far: $asked.", turns) } catch (e: Exception) { "" }
                act.runOnUiThread {
                    busy = false
                    if (raw.isBlank()) {
                        turns.removeAt(turns.size - 1)
                        react("😕", t("Не получилось связаться с ИИ. Проверьте ключ и интернет.", "Could not reach the AI. Check the key and the internet.", "AI bilan aloqa bo'lmadi. Kalit va internetni tekshiring."))
                        showButtons(listOf(t("Ещё раз", "Try again", "Qayta urinish") to "retry"))
                        return@runOnUiThread
                    }
                    val (say, isGuess) = parse(raw)
                    turns += "assistant" to raw
                    guessing = isGuess
                    if (!isGuess) asked++
                    status()
                    react(if (isGuess) "🧐" else "🤨", say)
                    showButtons(if (isGuess) listOf(t("Да, верно!", "Yes, correct!", "Ha, to'g'ri!") to "win", t("Нет", "No", "Yo'q") to "wrong")
                    else listOf(t("Да", "Yes", "Ha") to "Yes", t("Нет", "No", "Yo'q") to "No", t("Может быть", "Maybe", "Balki") to "Maybe", t("Не знаю", "I don't know", "Bilmayman") to "I don't know"))
                }
            }.start()
        }
        showButtons = { list ->
            answers.removeAllViews()
            var i = 0
            while (i < list.size) {
                val r = ctx.row(Gravity.CENTER)
                for (k in 0..1) {
                    val (label, code) = list.getOrNull(i + k) ?: break
                    r.addView(ctx.button(label, BtnStyle.Tonal) {
                        when (code) {
                            "retry" -> ask(lastSent)
                            "win" -> {
                                react("🥳", t("Ура, я угадал за $asked вопросов!", "Yes! I got it in $asked questions!", "Ura, $asked savolda topdim!"))
                                showButtons(listOf(t("Сыграть ещё", "Play again", "Yana o'ynash") to "again")); act.confetti(); face.cheer()
                            }
                            "wrong" -> {
                                lastSent = if (asked >= 20) "No, that is not it. I win - you have used all 20 questions." else "No, that is not it. Keep asking."
                                if (asked >= 20) {
                                    react("😭", t("Сдаюсь! Ты победил — что ты загадал?", "I give up! You win. What was it?", "Taslim! Sen yutding. Nima o'ylagan eding?"))
                                    showButtons(listOf(t("Сыграть ещё", "Play again", "Yana o'ynash") to "again"))
                                } else ask(lastSent)
                            }
                            "again" -> { turns.clear(); asked = 0; guessing = false; status(); ask("Let's start. I have thought of something. Ask your first question.") }
                            else -> { lastSent = code; ask(code) }
                        }
                    }, LinearLayout.LayoutParams(0, WRAP, 1f).apply { setMargins(ctx.dp(4), ctx.dp(4), ctx.dp(4), ctx.dp(4)) })
                }
                answers.addView(r, LinearLayout.LayoutParams(MATCH, WRAP))
                i += 2
            }
        }
        c.add(counter, MATCH, WRAP, bottom = 8)
        c.add(answers, MATCH, WRAP)
        react("🧠", t("Загадай животное, предмет или еду — я угадаю меньше чем за 20 вопросов.", "Think of an animal, an object or a food. I will guess it in under 20 questions.", "Hayvon, narsa yoki taom o'yla. Men 20 ta savoldan kam topaman."))
        showButtons(listOf(t("Я загадал!", "I have one!", "O'yladim!") to "again"))
    }

    // ---- memory match ---------------------------------------------------------------------------------------------

    private fun memory(c: LinearLayout) {
        val st = GameStages.memory[stage]
        val faces = listOf("🐶", "🐱", "🦊", "🐼", "🐸", "🐵", "🦄", "🐙", "🐯", "🐧")
        val cards = ArrayList<TextView>()
        var values = emptyList<String>()
        val matched = BooleanArray(st.cards)
        var first = -1
        var busy = false
        var over = false
        var moves = 0
        var pairs = 0
        val info = ctx.text("", T.Small).apply { gravity = Gravity.CENTER }
        val outcome = outcomeText()
        fun status() {
            val best = prefs.getInt("best_memory_$stage", 0)
            info.text = t("Ходов: $moves из ${st.moves}" + (if (best > 0) " · рекорд: $best" else ""), "Moves: $moves of ${st.moves}" + (if (best > 0) " · best: $best" else ""), "Yurishlar: $moves/${st.moves}" + (if (best > 0) " · rekord: $best" else ""))
        }
        var gen = 0   // a new deal makes every pending flip and unflip of the old one do nothing
        fun flip(i: Int, up: Boolean, then: () -> Unit = {}) {
            val v = cards[i]
            val g = gen
            v.animate().cancel()
            v.animate().setInterpolator(android.view.animation.LinearInterpolator()).rotationY(90f).setDuration(90).withEndAction {
                if (g != gen) return@withEndAction
                v.text = if (up) values[i] else "?"
                v.rotationY = -90f
                v.animate().setInterpolator(android.view.animation.LinearInterpolator()).rotationY(0f).setDuration(90).withEndAction { if (g == gen) then() }.start()
            }.start()
        }
        fun deal() {
            gen++
            values = (faces.take(st.pairs) + faces.take(st.pairs)).shuffled()
            for (i in 0 until st.cards) { cards[i].animate().cancel(); matched[i] = false; cards[i].text = "?"; cards[i].rotationY = 0f; cards[i].alpha = 1f; cards[i].scaleX = 1f; cards[i].scaleY = 1f }
            first = -1; busy = false; over = false; moves = 0; pairs = 0
            outcome.text = ""
            status()
            react("🧐", t("Найди все пары за ${st.moves} ходов!", "Find all the pairs in ${st.moves} moves!", "Hamma juftni ${st.moves} yurishda top!"))
        }
        fun tap(i: Int) {
            if (busy || over || matched[i] || i == first) return
            if (first < 0) { first = i; flip(i, true); return }
            val a = first; first = -1
            moves++; busy = true
            status()
            flip(i, true) {
                if (values[a] == values[i]) {
                    matched[a] = true; matched[i] = true; pairs++; busy = false
                    cards[a].alpha = .45f; cards[i].alpha = .45f
                    cards[i].burst()
                    if (pairs == st.pairs) {
                        over = true
                        val best = prefs.getInt("best_memory_$stage", 0)
                        if (best == 0 || moves < best) prefs.edit().putInt("best_memory_$stage", moves).apply()
                        react("🎉", t("Все пары за $moves ходов!", "All pairs in $moves moves!", "Hamma juft $moves yurishda!"))
                        finish(GameStages.memoryStars(st, moves), outcome)
                        status(); act.confetti(); face.cheer()
                    } else if (moves >= st.moves) {
                        over = true
                        react("😢", t("Ходы закончились. Попробуй ещё!", "Out of moves. Try again!", "Yurishlar tugadi. Yana urinib ko'r!"))
                    } else react("😄", t("Пара!", "A pair!", "Juft!"))
                } else {
                    val g = gen
                    if (moves >= st.moves) {
                        over = true
                        react("😢", t("Ходы закончились. Попробуй ещё!", "Out of moves. Try again!", "Yurishlar tugadi. Yana urinib ko'r!"))
                        cards[i].postDelayed({ if (g == gen) { flip(a, false); flip(i, false) } }, 650)
                    } else {
                        react("😬", t("Не то…", "Not those…", "Bular emas…"))
                        cards[i].postDelayed({ if (g == gen) { flip(a, false); flip(i, false) { busy = false } } }, 650)
                    }
                }
            }
        }
        c.add(info, MATCH, WRAP, bottom = 4)
        c.add(outcome, MATCH, WRAP, bottom = 6)
        val rows = (st.cards + st.cols - 1) / st.cols
        for (r in 0 until rows) {
            val row = ctx.row(Gravity.CENTER)
            for (k in 0 until st.cols) {
                val idx = r * st.cols + k
                val cell = ctx.text("?", T.Headline).apply {
                    textSize = 30f; gravity = Gravity.CENTER
                    background = ctx.rounded(P.container, 20f, P.outlineVariant)
                    cameraDistance = ctx.dpf(8000f)
                    setOnClickListener { tap(idx) }   // not press{}: its animations would cancel the flip and its end action
                }
                cards += cell
                row.addView(cell, LinearLayout.LayoutParams(0, ctx.dp(74), 1f).apply { setMargins(ctx.dp(4), ctx.dp(4), ctx.dp(4), ctx.dp(4)) })
            }
            c.add(row, MATCH, WRAP)
        }
        c.add(ctx.button(t("Новая игра", "New game", "Yangi o'yin"), BtnStyle.Tonal) { deal() }, MATCH, WRAP, top = 12)
        stagesButton(c)
        deal()
    }
}

/** A quick sideways shake: the input was wrong. */
internal fun View.shake() {
    animate().cancel()
    animate().translationX(-context.dpf(10f)).setDuration(60).withEndAction {
        animate().translationX(context.dpf(10f)).setDuration(90).withEndAction { animate().translationX(0f).setDuration(60).start() }.start()
    }.start()
}
