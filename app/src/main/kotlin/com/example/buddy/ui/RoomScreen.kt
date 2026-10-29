package com.example.buddy.ui

import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView

/**
 * "Bubby's Room": a match-3 puzzle. Swap two neighbours to make three or more in a row; four make a roller, five a
 * sparkle, an L or T a splash. Reach the level's goals before the moves run out; stars are kept per level.
 */
class RoomScreen(act: UiActivity) : Screen(act) {
    override fun title() = t("Комната Bubby", "Bubby's room", "Bubby xonasi")

    private val prefs get() = ctx.getSharedPreferences("buddy", android.content.Context.MODE_PRIVATE)
    private val levels = MatchLevels.all
    private val engine = Match3Engine()
    private lateinit var board: MatchBoardView
    private lateinit var face: TextView
    private lateinit var line: TextView
    private lateinit var movesText: TextView
    private lateinit var scoreText: TextView
    private lateinit var goalsText: TextView
    private lateinit var picker: LinearLayout
    private lateinit var actions: LinearLayout
    private var index = 0
    private var over = false

    private fun unlocked() = prefs.getInt("bm_level", 1).coerceIn(1, levels.size)
    private fun starsOf(i: Int) = prefs.getInt("bm_stars_$i", 0)

    private fun react(emoji: String, text: String) { face.text = emoji; line.text = text; face.jump() }

    override fun render(c: LinearLayout) {
        face = ctx.text("🙂", T.Headline).apply { textSize = 44f; gravity = Gravity.CENTER }
        line = ctx.text("", T.Body).apply { gravity = Gravity.CENTER; textSize = 15f }
        movesText = ctx.text("", T.Title).apply { textSize = 18f; gravity = Gravity.CENTER }
        scoreText = ctx.text("", T.Small).apply { gravity = Gravity.CENTER }
        goalsText = ctx.text("", T.Label).apply { gravity = Gravity.CENTER; textSize = 14.5f }
        picker = ctx.row(Gravity.START)
        actions = ctx.row(Gravity.CENTER)
        board = MatchBoardView(ctx, engine)
        board.onMoveShown = { res -> moveShown(res) }
        board.onRefused = { react("🙈", t("Так нельзя — нужно собрать три в ряд", "Not allowed: a swap has to make three in a row", "Bo'lmaydi: almashtirish uchta qatorni hosil qilishi kerak")) }
        board.onCombo = { n -> react(if (n >= 4) "🤩" else "😎", t("Комбо ×$n!", "Combo ×$n!", "Kombo ×$n!")) }

        c.add(face, MATCH, WRAP)
        c.add(line, MATCH, WRAP, top = 2, bottom = 8)
        val hud = ctx.row(Gravity.CENTER) {
            add(movesText, WRAP, WRAP, weight = 1f)
            add(scoreText, WRAP, WRAP, weight = 1f)
        }
        c.add(hud, MATCH, WRAP, bottom = 4)
        c.add(goalsText, MATCH, WRAP, bottom = 10)
        c.add(board, MATCH, WRAP)
        c.add(actions, MATCH, WRAP, top = 12)
        c.add(android.widget.HorizontalScrollView(ctx).apply { isHorizontalScrollBarEnabled = false; addView(picker) }, MATCH, WRAP, top = 10)
        index = (unlocked() - 1).coerceIn(0, levels.size - 1)
        start(index)
        // CI: plays the first legal moves by itself so that the screenshots show the animations and any crash shows in the log
        if (act.intent.getBooleanExtra("autoplay", false)) {
            fun step() { board.postDelayed({ if (!over) { engine.findMove()?.let { board.tryMove(it[0], it[1]) }; step() } }, 1300) }
            step()
        }
    }

    private fun start(i: Int) {
        index = i; over = false
        val lvl = levels[i]
        engine.fill()
        engine.setLevel(lvl.moves, lvl.dustCells())
        for (k in lvl.dustCells()) engine.dust[k] = lvl.dustLayerAt(k)
        board.syncFromEngine(); board.locked = false
        hud(); pickerRows(); actionRows(win = false)
        react("🙂", t("Уровень ${i + 1}: ${goalLine(lvl)}", "Level ${i + 1}: ${goalLine(lvl)}", "${i + 1}-daraja: ${goalLine(lvl)}"))
    }

    private val emoji = arrayOf("💧", "🖌️", "💡", "🛋️", "🌿")

    private fun goalLine(l: MatchLevel): String = l.goals.joinToString(" · ") { g ->
        when (g) {
            is MatchGoal.Collect -> "${emoji[g.color]} ×${g.n}"
            is MatchGoal.ClearDust -> t("убрать всю пыль", "clear all the dust", "changni tozala")
            is MatchGoal.Score -> t("${g.n} очков", "${g.n} points", "${g.n} ball")
        }
    }

    private fun goalsDone(): Boolean = levels[index].goals.all { g ->
        when (g) {
            is MatchGoal.Collect -> engine.collected[g.color] >= g.n
            is MatchGoal.ClearDust -> engine.dustLeft() == 0
            is MatchGoal.Score -> engine.score >= g.n
        }
    }

    private fun hud() {
        movesText.text = t("Ходов: ${engine.movesLeft}", "Moves: ${engine.movesLeft}", "Yurishlar: ${engine.movesLeft}")
        scoreText.text = t("Очки: ${engine.score}", "Score: ${engine.score}", "Ball: ${engine.score}")
        goalsText.text = levels[index].goals.joinToString("   ") { g ->
            when (g) {
                is MatchGoal.Collect -> "${emoji[g.color]} ${minOf(engine.collected[g.color], g.n)}/${g.n}"
                is MatchGoal.ClearDust -> "🧹 " + t("пыли: ${engine.dustLeft()}", "dust: ${engine.dustLeft()}", "chang: ${engine.dustLeft()}")
                is MatchGoal.Score -> "⭐ ${minOf(engine.score, g.n)}/${g.n}"
            }
        }
    }

    private fun moveShown(res: Match3Engine.Result) {
        hud()
        if (over) return
        if (goalsDone()) { win(); return }
        if (engine.movesLeft <= 0) { lose(); return }
        if (res.shuffled) react("🔀", t("Ходов не осталось — перемешал!", "No moves left, so I shuffled!", "Yurish qolmadi, aralashtirdim!"))
        else if (engine.movesLeft <= 3) react("😬", t("Осталось ходов: ${engine.movesLeft}", "Only ${engine.movesLeft} moves left", "${engine.movesLeft} ta yurish qoldi"))
        else if (res.steps.size < 3) react("🙂", goalLine(levels[index]))
    }

    private fun win() {
        over = true; board.locked = true
        val lvl = levels[index]
        val stars = when { engine.movesLeft * 100 >= lvl.moves * 30 -> 3; engine.movesLeft * 100 >= lvl.moves * 10 -> 2; else -> 1 }
        if (stars > starsOf(index)) prefs.edit().putInt("bm_stars_$index", stars).apply()
        if (index + 2 > unlocked()) prefs.edit().putInt("bm_level", minOf(levels.size, index + 2)).apply()
        react("🥳", "⭐".repeat(stars) + "  " + t("Комната стала уютнее!", "The room got cosier!", "Xona yanada shinam bo'ldi!"))
        act.confetti()
        actionRows(win = true); pickerRows()
    }

    private fun lose() {
        over = true; board.locked = true
        react("😢", t("Ходы закончились. Попробуем ещё раз?", "Out of moves. Shall we try again?", "Yurishlar tugadi. Yana urinamizmi?"))
        actionRows(win = false)
    }

    private fun actionRows(win: Boolean) {
        actions.removeAllViews()
        if (win && index + 1 < levels.size) {
            actions.addView(ctx.button(t("Дальше", "Next level", "Keyingi daraja")) { start(index + 1) }, LinearLayout.LayoutParams(0, WRAP, 1f).apply { setMargins(ctx.dp(4), 0, ctx.dp(4), 0) })
        }
        actions.addView(ctx.button(t("Заново", "Restart", "Qaytadan"), BtnStyle.Tonal) { start(index) }, LinearLayout.LayoutParams(0, WRAP, 1f).apply { setMargins(ctx.dp(4), 0, ctx.dp(4), 0) })
        actions.addView(ctx.button(t("Комната", "My room", "Xonam"), BtnStyle.Tonal) { act.push(RoomShopScreen(act, fromGame = true)) }, LinearLayout.LayoutParams(0, WRAP, 1f).apply { setMargins(ctx.dp(4), 0, ctx.dp(4), 0) })
    }

    private fun pickerRows() {
        picker.removeAllViews()
        for (i in 0 until unlocked()) {
            val stars = starsOf(i)
            picker.addView(ctx.chip("${i + 1}" + if (stars > 0) "★$stars" else "", i == index) { if (!board.locked || over) start(i) },
                LinearLayout.LayoutParams(WRAP, WRAP).apply { setMargins(ctx.dp(3), 0, ctx.dp(3), 0) })
        }
    }
}
