package com.example.buddy.ui

import android.view.Gravity
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView

class RunnerScreen(act: UiActivity) : Screen(act) {
    override fun title() = t("Космо-раннер", "Space runner", "Kosmo-yuguruvchi")
    override val padH = 12

    private val game = RunnerGame()
    private lateinit var view: RunnerView
    private lateinit var heartsText: TextView
    private lateinit var scoreText: TextView
    private lateinit var comboText: TextView
    private lateinit var powerPill: LinearLayout
    private lateinit var powerText: TextView
    private lateinit var sectorText: TextView
    private lateinit var phraseText: TextView
    private lateinit var meaningText: TextView
    private lateinit var panel: LinearLayout
    private val optionBoxes = ArrayList<LinearLayout>()
    private val optionTexts = ArrayList<TextView>()
    private val accents = intArrayOf(P.orange, P.violet, P.teal)

    private fun best() = Backend.int(ctx, "runner_best", 0)
    private fun bestSector() = Backend.int(ctx, "runner_sector", 0)

    private fun rankName(score: Int) = when (RunnerGame.rank(score)) {
        0 -> t("Кадет", "Cadet", "Kursant")
        1 -> t("Пилот", "Pilot", "Uchuvchi")
        2 -> t("Капитан", "Captain", "Kapitan")
        3 -> t("Командор", "Commander", "Qo'mondon")
        else -> t("Адмирал", "Admiral", "Admiral")
    }

    private fun powerName(p: RunnerGame.Power) = when (p) {
        RunnerGame.Power.SHIELD -> "🛡 " + t("Щит", "Shield", "Qalqon")
        RunnerGame.Power.SLOW -> "⏱ " + t("Замедление", "Slow-mo", "Sekinlashuv")
        RunnerGame.Power.HINT -> "💡 " + t("Подсказка", "Hint", "Maslahat")
        RunnerGame.Power.NONE -> ""
    }

    override fun render(c: LinearLayout) {
        heartsText = ctx.text("", T.Title).apply { textSize = 18f }
        scoreText = ctx.text("", T.Title).apply { textSize = 18f; gravity = Gravity.CENTER }
        comboText = ctx.text("", T.Label, P.orange).apply { textSize = 16f; gravity = Gravity.CENTER }
        powerText = ctx.text("", T.Label).apply { textSize = 13.5f }
        powerPill = ctx.row(Gravity.CENTER) {
            minimumHeight = ctx.dp(48)
            setPadding(ctx.dp(14), ctx.dp(6), ctx.dp(14), ctx.dp(6))
            add(powerText, WRAP, WRAP)
            press { game.usePower() }
        }
        val hud = ctx.row(Gravity.CENTER_VERTICAL) {
            add(heartsText, WRAP, WRAP, weight = 1f)
            add(scoreText, WRAP, WRAP, weight = 1f)
            add(comboText, WRAP, WRAP, end = 10)
            add(powerPill, WRAP, WRAP)
        }
        c.add(hud, MATCH, WRAP, top = 6, bottom = 8)

        sectorText = ctx.text("", T.Small).apply { gravity = Gravity.CENTER }
        phraseText = ctx.text("", T.Headline).apply { textSize = 28f; gravity = Gravity.CENTER; typeface = glassFont(bold = true) }
        meaningText = ctx.text("", T.Body).apply { gravity = Gravity.CENTER }
        val card = ctx.card(pad = 12) {
            add(sectorText, MATCH, WRAP)
            add(phraseText, MATCH, WRAP, top = 4)
            add(meaningText, MATCH, WRAP, top = 2)
        }
        c.add(card, MATCH, WRAP, bottom = 8)

        val fieldH = minOf(ctx.dp(430), (ctx.resources.displayMetrics.heightPixels * 0.44f).toInt())
        view = RunnerView(ctx, game).apply {
            contentDescription = t("Игровое поле", "Game field", "O'yin maydoni")
            onHide = { pauseGame() }
        }
        panel = ctx.card(pad = 18) {}
        val frame = FrameLayout(ctx).apply {
            addView(view, FrameLayout.LayoutParams(MATCH, fieldH))
            addView(panel, FrameLayout.LayoutParams(MATCH, WRAP, Gravity.CENTER).apply {
                setMargins(ctx.dp(14), ctx.dp(10), ctx.dp(14), ctx.dp(10))
            })
        }
        c.add(frame, MATCH, WRAP)

        val options = ctx.row(Gravity.CENTER) {}
        for (i in 0 until 3) {
            val tx = ctx.text("", T.Title).apply { textSize = 20f; gravity = Gravity.CENTER }
            optionTexts.add(tx)
            val box = ctx.row(Gravity.CENTER) {
                add(tx, WRAP, WRAP)
                press { game.setLane(i) }
            }
            optionBoxes.add(box)
            options.addView(box, LinearLayout.LayoutParams(0, ctx.dp(58), 1f).apply { setMargins(ctx.dp(4), 0, ctx.dp(4), 0) })
        }
        c.add(options, MATCH, WRAP, top = 10)

        game.onEvent = { e -> view.onGameEvent(e); onEvent(e) }
        showStart()
        paintHud()
    }

    private fun onEvent(e: RunnerGame.Ev) {
        when (e) {
            RunnerGame.Ev.SECTOR -> view.toast(t("Сектор ${game.sector + 1}", "Sector ${game.sector + 1}", "Sektor ${game.sector + 1}"))
            RunnerGame.Ev.BOSS -> view.toast(t("БОСС!", "BOSS!", "BOSS!"))
            RunnerGame.Ev.BOSS_CLEAR -> view.toast(t("Босс побеждён! +❤", "Boss down! +❤", "Boss yengildi! +❤"))
            RunnerGame.Ev.POWER_READY -> view.toast(powerName(game.power))
            RunnerGame.Ev.CORRECT -> if (game.streak % 3 == 0) view.toast("x${game.mult}")
            RunnerGame.Ev.OVER -> view.postDelayed({ showOver() }, 900)
            else -> {}
        }
        paintHud()
    }

    private fun paintHud() {
        heartsText.text = "❤️".repeat(game.hearts.coerceAtLeast(0)) + "🖤".repeat((3 - game.hearts).coerceAtLeast(0))
        scoreText.text = "⭐ ${game.score}"
        comboText.text = if (game.mult > 1) "x${game.mult}" else ""
        val has = game.power != RunnerGame.Power.NONE
        powerText.text = if (has) powerName(game.power) else "✨ ${game.bag}/${RunnerGame.BAG_SIZE}"
        powerPill.background = ctx.rounded(P.alpha(if (has) P.warning else P.sky, if (has) 150 else 60), 24f)
        powerPill.isEnabled = has
        powerPill.contentDescription = powerText.text
        val g = game.gate
        sectorText.text = (if (g?.boss == true) "BOSS · " else "") + game.sectorName + " · " + t("Сектор", "Sector", "Sektor") + " ${game.sector + 1}"
        if (g != null) {
            phraseText.text = if (g.done) g.phrase.filled() else g.phrase.text
            meaningText.text = if (game.sector >= 4 && !g.done) "?" else t(g.phrase.ru, g.phrase.ru, g.phrase.uz)
            for (i in 0 until 3) {
                optionTexts[i].text = g.options[i].uppercase()
                val sel = i == game.lane
                val dim = !g.done && i == game.hint
                optionBoxes[i].background = ctx.rounded(P.alpha(accents[i], if (sel) 150 else 55), 22f, if (sel) accents[i] else 0, 2f)
                optionBoxes[i].alpha = if (dim) 0.3f else 1f
                optionBoxes[i].contentDescription = g.options[i]
            }
        }
    }

    private fun showStart() {
        panel.removeAllViews()
        panel.visibility = android.view.View.VISIBLE
        panel.add(ctx.text(t("Космо-раннер", "Space runner", "Kosmo-yuguruvchi"), T.Title).apply { textSize = 20f; gravity = Gravity.CENTER }, MATCH, WRAP)
        panel.add(ctx.text(
            t("Выбери полосу: на воротах нужное слово. Уворачивайся от астероидов, собирай звёзды, копи комбо. После 8 ворот — босс. С 5-го сектора перевод скрыт.",
                "Pick a lane: the gate holds the missing word. Dodge asteroids, grab stars, build combos. A boss comes after 8 gates. From sector 5 the translation is hidden.",
                "Yo'lakni tanla: darvozada yetishmayotgan so'z bor. Asteroidlardan qoch, yulduz yig', kombo to'pla. 8 darvozadan keyin boss chiqadi. 5-sektordan tarjima yashiriladi."),
            T.Body).apply { gravity = Gravity.CENTER }, MATCH, WRAP, top = 8)
        panel.add(ctx.text(t("Рекорд", "Best", "Rekord") + ": ${best()} · " + rankName(best()), T.Small).apply { gravity = Gravity.CENTER }, MATCH, WRAP, top = 8)
        panel.add(ctx.button(t("Старт", "Start", "Boshlash")) { begin() }, MATCH, WRAP, top = 12)
    }

    private fun pauseGame() {
        if (panel.visibility == android.view.View.VISIBLE || game.over) return
        view.play(false)
        panel.removeAllViews()
        panel.visibility = android.view.View.VISIBLE
        panel.add(ctx.text(t("Пауза", "Paused", "To'xtatildi"), T.Title).apply { textSize = 20f; gravity = Gravity.CENTER }, MATCH, WRAP)
        panel.add(ctx.button(t("Продолжить", "Resume", "Davom etish")) {
            panel.visibility = android.view.View.GONE
            view.play(true)
        }, MATCH, WRAP, top = 12)
    }

    private fun begin() {
        panel.visibility = android.view.View.GONE
        game.start()
        view.play(true)
        paintHud()
    }

    private fun showOver() {
        view.play(false)
        val newBest = game.score > best()
        if (newBest) Backend.setInt(ctx, "runner_best", game.score)
        if (game.sector + 1 > bestSector()) Backend.setInt(ctx, "runner_sector", game.sector + 1)
        panel.removeAllViews()
        panel.visibility = android.view.View.VISIBLE
        panel.add(ctx.text(t("Игра окончена", "Game over", "O'yin tugadi"), T.Title).apply { textSize = 20f; gravity = Gravity.CENTER }, MATCH, WRAP)
        panel.add(ctx.text("⭐ ${game.score} · " + rankName(game.score) + " · " + t("Сектор", "Sector", "Sektor") + " ${game.sector + 1}", T.Body).apply { gravity = Gravity.CENTER }, MATCH, WRAP, top = 6)
        panel.add(ctx.text(
            if (newBest) t("Новый рекорд!", "New best!", "Yangi rekord!") else t("Рекорд", "Best", "Rekord") + ": ${best()}",
            T.Small, if (newBest) P.orange else 0).apply { gravity = Gravity.CENTER }, MATCH, WRAP, top = 2)
        if (game.missed.isNotEmpty()) {
            panel.add(ctx.text(t("Повтори", "Review", "Takrorlang"), T.Label).apply { gravity = Gravity.CENTER }, MATCH, WRAP, top = 10)
            for (p in game.missed.values.take(5)) {
                panel.add(ctx.text("${p.filled()} — " + t(p.ru, p.ru, p.uz), T.Small).apply { gravity = Gravity.CENTER }, MATCH, WRAP, top = 3)
            }
        }
        val earned = (game.score / 80).coerceAtMost(15)
        DailyStreak.addStars(ctx, earned)
        if (earned > 0) panel.add(ctx.text("+$earned ⭐", T.Title, P.orange).apply { gravity = Gravity.CENTER; textSize = 18f }, MATCH, WRAP, top = 8)
        panel.add(ctx.button(t("Заново", "Play again", "Qayta o'ynash")) { begin() }, MATCH, WRAP, top = 12)
    }
}
