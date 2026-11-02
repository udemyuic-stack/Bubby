package com.example.buddy.ui

import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView

class BattleScreen(act: UiActivity) : Screen(act) {
    override val coversScene = true
    override fun title() = t("Битва с Bubby", "Bubby Battle", "Bubby jangi")
    override val padH = 12

    private var current = -1
    private var game: BattleGame? = null
    private var view: BattleView? = null
    private lateinit var panel: LinearLayout
    private lateinit var promptText: TextView
    private lateinit var roundText: TextView
    private val optionBoxes = ArrayList<LinearLayout>()
    private val optionTexts = ArrayList<TextView>()
    private lateinit var fiftyBtn: LinearLayout
    private lateinit var freezeBtn: LinearLayout
    private lateinit var healBtn: LinearLayout
    private val accents = intArrayOf(P.orange, P.violet, P.teal, P.rose)
    private var playing = false

    private fun stars(i: Int) = Backend.int(ctx, "bb_stars_$i", 0)
    private fun unlocked(i: Int) = i == 0 || stars(i - 1) >= 1
    private fun oppName(o: BattleOpp) = t(o.ru, o.en, o.uz)
    private fun starsText(n: Int) = "★".repeat(n) + "☆".repeat(3 - n)

    override fun render(c: LinearLayout) {
        if (current < 0) renderPicker(c) else renderDuel(c)
    }

    private fun renderPicker(c: LinearLayout) {
        stop()
        c.add(ctx.card(pad = 16) {
            add(ctx.text(t("Битва с Bubby", "Bubby Battle", "Bubby jangi"), T.Title), MATCH, WRAP)
            add(ctx.text(t("Отвечай быстрее Bubby: верный ответ бьёт соперника. Победа открывает следующего.",
                "Answer faster than Bubby: a right answer hits your rival. A win unlocks the next one.",
                "Bubbydan tez javob ber: to'g'ri javob raqibga uradi. G'alaba keyingisini ochadi."), T.Body), MATCH, WRAP, top = 4)
        }, MATCH, WRAP, bottom = 10)
        for ((i, o) in BattleOpps.all.withIndex()) {
            val open = unlocked(i)
            c.add(ctx.card(pad = 14, onClick = if (open) ({ current = i; refresh() }) else null) {
                alpha = if (open) 1f else 0.5f
                add(ctx.row {
                    add(ctx.row(Gravity.CENTER) {
                        background = ctx.rounded(o.color and 0xFFFFFF or 0x55000000, 14f)
                        add(ctx.text(if (open) "${i + 1}" else "🔒", T.Title), WRAP, WRAP)
                    }, ctx.dp(48), ctx.dp(48), end = 12)
                    add(ctx.col {
                        add(ctx.text(oppName(o), T.Label), MATCH, WRAP)
                        add(ctx.text(t("точность", "accuracy", "aniqlik") + " ${(o.accuracy * 100).toInt()}% · ${o.timer.toInt()} s", T.Small), MATCH, WRAP, top = 2)
                    }, 0, WRAP, 1f)
                    add(ctx.text(starsText(stars(i)), T.Title, P.orange), WRAP, WRAP)
                })
            }, MATCH, WRAP, bottom = 8)
        }
    }

    private fun stop() {
        playing = false
        view?.play(false)
        view = null
        game = null
        optionBoxes.clear()
        optionTexts.clear()
    }

    private fun powerButton(label: String, onTap: () -> Unit): LinearLayout = ctx.row(Gravity.CENTER) {
        minimumHeight = ctx.dp(48)
        add(ctx.text(label, T.Label).apply { textSize = 13.5f; gravity = Gravity.CENTER }, WRAP, WRAP)
        press { onTap() }
    }

    private fun renderDuel(c: LinearLayout) {
        stop()
        val g = BattleGame(current, { p -> t(p.ru, p.ru, p.uz) })
        game = g
        val v = BattleView(ctx, g).apply {
            contentDescription = t("Арена", "Arena", "Arena")
            onHide = { pauseGame() }
        }
        view = v
        g.onEvent = { e, value -> v.onGameEvent(e, value); onEvent(e) }

        roundText = ctx.text("", T.Small).apply { gravity = Gravity.CENTER }
        promptText = ctx.text("", T.Headline).apply { textSize = 26f; gravity = Gravity.CENTER; typeface = glassFont(bold = true) }
        c.add(ctx.row(Gravity.CENTER_VERTICAL) {
            add(ctx.text(oppName(g.opp), T.Title), WRAP, WRAP, weight = 1f)
            add(ctx.row(Gravity.CENTER) {
                minimumHeight = ctx.dp(48)
                minimumWidth = ctx.dp(48)
                background = ctx.rounded(P.alpha(P.sky, 60), 24f)
                contentDescription = t("Пауза", "Pause", "To'xtatish")
                add(ctx.text("❚❚", T.Label), WRAP, WRAP)
                press { pauseGame() }
            }, WRAP, WRAP)
        }, MATCH, WRAP, top = 4, bottom = 6)

        val arenaH = minOf(ctx.dp(190), (ctx.resources.displayMetrics.heightPixels * 0.24f).toInt())
        panel = ctx.card(pad = 18) {}
        val frame = FrameLayout(ctx).apply {
            addView(v, FrameLayout.LayoutParams(MATCH, arenaH))
            addView(panel, FrameLayout.LayoutParams(MATCH, WRAP, Gravity.CENTER).apply {
                setMargins(ctx.dp(14), ctx.dp(6), ctx.dp(14), ctx.dp(6))
            })
        }
        c.add(frame, MATCH, WRAP)

        c.add(ctx.card(pad = 12) {
            add(roundText, MATCH, WRAP)
            add(promptText, MATCH, WRAP, top = 4)
        }, MATCH, WRAP, top = 8, bottom = 8)

        for (i in 0 until 4) {
            val tx = ctx.text("", T.Title).apply { textSize = 18f; gravity = Gravity.CENTER }
            optionTexts.add(tx)
            val box = ctx.row(Gravity.CENTER) {
                setPadding(ctx.dp(8), ctx.dp(8), ctx.dp(8), ctx.dp(8))
                add(tx, MATCH, WRAP)
                press { if (playing) game?.answer(i) }
            }
            optionBoxes.add(box)
            c.add(box, MATCH, ctx.dp(58), bottom = 8)
        }

        fiftyBtn = powerButton("50/50") { if (playing) { game?.fifty(); paintQuestion() } }
        freezeBtn = powerButton("❄ " + t("Заморозка", "Freeze", "Muzlatish")) { if (playing) { game?.freeze(); paintQuestion() } }
        healBtn = powerButton("❤ +20") { if (playing) { game?.heal(); paintQuestion() } }
        c.add(ctx.row(Gravity.CENTER) {
            for (b in listOf(fiftyBtn, freezeBtn, healBtn)) addView(b, LinearLayout.LayoutParams(0, ctx.dp(48), 1f).apply { setMargins(ctx.dp(4), 0, ctx.dp(4), 0) })
        }, MATCH, WRAP, top = 4)

        showReady()
    }

    private fun onEvent(e: BattleGame.Ev) {
        paintQuestion()
        if (e == BattleGame.Ev.OVER) {
            playing = false
            view?.postDelayed({ view?.play(false); showResult() }, 1300)
        }
    }

    private fun paintQuestion() {
        val g = game ?: return
        val q = g.question ?: return
        roundText.text = t("Раунд", "Round", "Raund") + " ${g.round} · " + if (q.translate) t("Перевод", "Translation", "Tarjima") else t("Вставь слово", "Fill the gap", "Bo'sh joyni to'ldir")
        promptText.text = q.prompt
        for (i in 0 until 4) {
            val hide = g.hidden[i]
            val showCorrect = g.resolved && i == q.correct
            val wrongPick = g.resolved && i == g.chosen && i != q.correct
            val color = when {
                showCorrect -> P.success
                wrongPick -> P.error
                else -> accents[i]
            }
            optionTexts[i].text = q.options[i]
            optionBoxes[i].background = ctx.rounded(P.alpha(color, if (showCorrect || wrongPick) 170 else 55), 22f, color, 2f)
            optionBoxes[i].alpha = if (hide) 0.2f else 1f
            optionBoxes[i].contentDescription = q.options[i]
        }
        fiftyBtn.alpha = if (g.fiftyLeft) 1f else 0.35f
        freezeBtn.alpha = if (g.freezeLeft && !g.frozen) 1f else 0.35f
        healBtn.alpha = if (g.healLeft) 1f else 0.35f
        for (b in listOf(fiftyBtn, freezeBtn, healBtn)) b.background = ctx.rounded(P.alpha(P.sky, 50), 24f)
    }

    private fun showPanel(block: LinearLayout.() -> Unit) {
        panel.removeAllViews()
        panel.visibility = View.VISIBLE
        panel.block()
    }

    private fun heading(s: String): TextView = ctx.text(s, T.Title).apply { textSize = 20f; gravity = Gravity.CENTER }

    private fun showReady() {
        paintDummy()
        showPanel {
            val o = BattleOpps.all[current]
            add(heading(oppName(o)), MATCH, WRAP)
            add(ctx.button(t("В бой", "Fight", "Jang")) { begin() }, MATCH, WRAP, top = 10)
        }
    }

    private fun paintDummy() {
        for (i in 0 until 4) {
            optionTexts[i].text = "—"
            optionBoxes[i].background = ctx.rounded(P.alpha(accents[i], 40), 22f)
        }
        roundText.text = ""
        promptText.text = "…"
        for (b in listOf(fiftyBtn, freezeBtn, healBtn)) b.background = ctx.rounded(P.alpha(P.sky, 50), 24f)
    }

    private fun begin() {
        panel.visibility = View.GONE
        playing = true
        game?.start()
        view?.play(true)
    }

    private fun pauseGame() {
        val g = game ?: return
        if (!playing || g.over) return
        playing = false
        view?.play(false)
        showPanel {
            add(heading(t("Пауза", "Paused", "To'xtatildi")), MATCH, WRAP)
            add(ctx.button(t("Продолжить", "Resume", "Davom etish")) { panel.visibility = View.GONE; playing = true; view?.play(true) }, MATCH, WRAP, top = 10)
            add(ctx.button(t("Выйти", "Quit", "Chiqish"), BtnStyle.Outline) { leave() }, MATCH, WRAP, top = 8)
        }
    }

    private fun leave() {
        current = -1
        refresh()
    }

    private fun showResult() {
        val g = game ?: return
        val i = current
        val st = g.stars
        val prev = stars(i)
        if (st > prev) Backend.setInt(ctx, "bb_stars_$i", st)
        val earned = (st - prev).coerceAtLeast(0) * (i + 1) + if (prev == 0 && st >= 1) 2 else 0
        DailyStreak.addStars(ctx, earned)
        var title: TextView? = null
        var gain: TextView? = null
        showPanel {
            title = heading(if (g.won) t("Победа!", "Victory!", "G'alaba!") else t("Поражение", "Defeat", "Mag'lubiyat"))
            add(title!!, MATCH, WRAP)
            add(ctx.text(starsText(st), T.Headline, P.orange).apply { gravity = Gravity.CENTER }, MATCH, WRAP, top = 4)
            add(ctx.text("❤ ${g.playerHp} · ${g.round} " + t("раундов", "rounds", "raund"), T.Body).apply { gravity = Gravity.CENTER }, MATCH, WRAP, top = 4)
            if (earned > 0) { gain = ctx.text("+$earned ⭐", T.Title, P.orange).apply { gravity = Gravity.CENTER; textSize = 18f }; add(gain!!, MATCH, WRAP, top = 6) }
            if (g.missed.isNotEmpty()) {
                add(ctx.text(t("Повтори", "Review", "Takrorlang"), T.Label).apply { gravity = Gravity.CENTER }, MATCH, WRAP, top = 8)
                for (p in g.missed.values.take(4)) add(ctx.text("${p.filled()} — " + t(p.ru, p.ru, p.uz), T.Small).apply { gravity = Gravity.CENTER }, MATCH, WRAP, top = 2)
            }
            add(ctx.button(t("Ещё раз", "Again", "Qayta")) { refresh() }, MATCH, WRAP, top = 10)
            add(ctx.button(t("К соперникам", "Back to rivals", "Raqiblarga"), BtnStyle.Outline) { leave() }, MATCH, WRAP, top = 8)
        }
        val head = title ?: return
        if (g.won) head.post {
            if (!head.isAttachedToWindow) return@post
            act.confetti()
            head.cheer()
            gain?.let { act.flyStars(head, it, 10) }
        }
    }
}
