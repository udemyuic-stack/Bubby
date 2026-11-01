package com.example.buddy.ui

import android.content.Intent
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView
import java.time.LocalDate

class WordScreen(act: UiActivity) : Screen(act) {
    override fun title() = t("Слово дня", "Daily word", "Kun so'zi")
    override val padH = 12

    private val today = LocalDate.now().toEpochDay()
    private val secret = WordGame.secret(today)
    private val guesses = ArrayList<String>()
    private var current = StringBuilder()
    private val cells = ArrayList<TextView>()
    private val keys = HashMap<Char, TextView>()
    private lateinit var info: TextView
    private lateinit var result: LinearLayout

    private val colCorrect = P.success
    private val colPresent = P.warning
    private val colAbsent = 0xFF4A4A5A.toInt()

    private fun prefs() = Backend.prefs(ctx)

    private fun won() = guesses.lastOrNull() == secret
    private fun over() = won() || guesses.size >= WordGame.TRIES

    private fun streak(): Int {
        val last = prefs().getLong("dw_last_win", -1L)
        return if (last >= today - 1) Backend.int(ctx, "dw_streak", 0) else 0
    }

    private fun load() {
        guesses.clear()
        if (prefs().getLong("dw_day", -1L) == today) {
            val raw = Backend.str(ctx, "dw_guesses")
            if (raw.isNotEmpty()) guesses.addAll(raw.split(",").filter { it.length == WordGame.LEN })
        } else {
            prefs().edit().putLong("dw_day", today).putString("dw_guesses", "").apply()
        }
    }

    private fun save() { Backend.setStr(ctx, "dw_guesses", guesses.joinToString(",")) }

    override fun render(c: LinearLayout) {
        cells.clear()
        keys.clear()
        current = StringBuilder()
        load()

        info = ctx.text("", T.Small).apply { gravity = Gravity.CENTER }
        c.add(info, MATCH, WRAP, top = 4, bottom = 8)

        val size = minOf(ctx.dp(54), (ctx.resources.displayMetrics.widthPixels - ctx.dp(80)) / WordGame.LEN)
        for (r in 0 until WordGame.TRIES) {
            val row = ctx.row(Gravity.CENTER) {}
            for (k in 0 until WordGame.LEN) {
                val cell = ctx.text("", T.Title).apply {
                    gravity = Gravity.CENTER
                    textSize = 22f
                    typeface = glassFont(bold = true)
                }
                cells.add(cell)
                row.addView(cell, LinearLayout.LayoutParams(size, size).apply { setMargins(ctx.dp(3), ctx.dp(3), ctx.dp(3), ctx.dp(3)) })
            }
            c.add(row, MATCH, WRAP)
        }

        result = ctx.card(pad = 14) {}
        c.add(result, MATCH, WRAP, top = 8, bottom = 4)

        val rows = listOf("qwertyuiop", "asdfghjkl", "zxcvbnm")
        for ((ri, letters) in rows.withIndex()) {
            val row = ctx.row(Gravity.CENTER) {}
            if (ri == 2) row.addView(wideKey("⏎", 1.5f) { submit() }, LinearLayout.LayoutParams(0, ctx.dp(48), 1.5f).apply { setMargins(ctx.dp(2), ctx.dp(3), ctx.dp(2), ctx.dp(3)) })
            for (ch in letters) {
                val key = ctx.row(Gravity.CENTER) {
                    add(ctx.text(ch.uppercase(), T.Label).apply { gravity = Gravity.CENTER }.also { keys[ch] = it }, WRAP, WRAP)
                    contentDescription = ch.toString()
                    press { type(ch) }
                }
                row.addView(key, LinearLayout.LayoutParams(0, ctx.dp(48), 1f).apply { setMargins(ctx.dp(2), ctx.dp(3), ctx.dp(2), ctx.dp(3)) })
            }
            if (ri == 2) row.addView(wideKey("⌫", 1.5f) { back() }, LinearLayout.LayoutParams(0, ctx.dp(48), 1.5f).apply { setMargins(ctx.dp(2), ctx.dp(3), ctx.dp(2), ctx.dp(3)) })
            c.add(row, MATCH, WRAP)
        }
        paint()
    }

    private fun wideKey(label: String, w: Float, onTap: () -> Unit): LinearLayout = ctx.row(Gravity.CENTER) {
        add(ctx.text(label, T.Label).apply { gravity = Gravity.CENTER }, WRAP, WRAP)
        contentDescription = if (label == "⏎") t("Ввод", "Enter", "Kiritish") else t("Стереть", "Delete", "O'chirish")
        press { onTap() }
    }

    private fun type(ch: Char) {
        if (over() || current.length >= WordGame.LEN) return
        current.append(ch)
        paint()
    }

    private fun back() {
        if (over() || current.isEmpty()) return
        current.deleteCharAt(current.length - 1)
        paint()
    }

    private fun submit() {
        if (over()) return
        if (current.length < WordGame.LEN) {
            cells[guesses.size * WordGame.LEN].parent?.let { (it as android.view.View).shake() }
            ctx.toast(t("Нужно 5 букв", "Need 5 letters", "5 ta harf kerak"))
            return
        }
        guesses.add(current.toString())
        current = StringBuilder()
        save()
        if (over()) finishDay()
        paint()
    }

    private fun finishDay() {
        if (prefs().getLong("dw_paid", -1L) == today) return
        prefs().edit().putLong("dw_paid", today).apply()
        if (won()) {
            val last = prefs().getLong("dw_last_win", -1L)
            val s = if (last == today - 1) Backend.int(ctx, "dw_streak", 0) + 1 else 1
            Backend.setInt(ctx, "dw_streak", s)
            prefs().edit().putLong("dw_last_win", today).apply()
            DailyStreak.addStars(ctx, WordGame.stars(guesses.size))
        } else {
            Backend.setInt(ctx, "dw_streak", 0)
        }
    }

    private fun tint(v: Int) = when (v) {
        WordGame.CORRECT -> colCorrect
        WordGame.PRESENT -> colPresent
        else -> colAbsent
    }

    private fun paint() {
        for (r in 0 until WordGame.TRIES) {
            val g = guesses.getOrNull(r)
            val ev = g?.let { WordGame.evaluate(it, secret) }
            for (k in 0 until WordGame.LEN) {
                val cell = cells[r * WordGame.LEN + k]
                when {
                    g != null -> {
                        cell.text = g[k].uppercase()
                        cell.setTextColor(0xFFFFFFFF.toInt())
                        cell.background = ctx.rounded(tint(ev!![k]), 10f)
                    }
                    r == guesses.size && k < current.length -> {
                        cell.text = current[k].uppercase()
                        cell.setTextColor(P.onSurface)
                        cell.background = ctx.rounded(P.alpha(P.sky, 40), 10f, P.sky, 2f)
                    }
                    else -> {
                        cell.text = ""
                        cell.background = ctx.rounded(P.alpha(P.sky, 25), 10f, P.outlineVariant, 1f)
                    }
                }
            }
        }
        val st = WordGame.keyStates(guesses, secret)
        for ((ch, tv) in keys) {
            val s = st[ch - 'a']
            val box = tv.parent as android.view.View
            box.background = ctx.rounded(if (s < 0) P.alpha(P.sky, 50) else tint(s), 12f)
            tv.setTextColor(if (s < 0) P.onSurface else 0xFFFFFFFF.toInt())
        }
        info.text = if (over()) "" else t("Попытка", "Try", "Urinish") + " ${guesses.size + 1}/${WordGame.TRIES} · 🔥 ${streak()}"
        paintResult()
    }

    private fun paintResult() {
        result.removeAllViews()
        if (!over()) { result.visibility = android.view.View.GONE; return }
        result.visibility = android.view.View.VISIBLE
        val w = won()
        result.add(ctx.text(
            if (w) t("Верно!", "Got it!", "To'g'ri!") else t("Слово было: ${secret.uppercase()}", "The word was: ${secret.uppercase()}", "So'z: ${secret.uppercase()}"),
            T.Title).apply { gravity = Gravity.CENTER; textSize = 20f }, MATCH, WRAP)
        if (w) result.add(ctx.text("+${WordGame.stars(guesses.size)} ⭐ · 🔥 ${streak()}", T.Body, P.orange).apply { gravity = Gravity.CENTER }, MATCH, WRAP, top = 4)
        result.add(ctx.text(t("Новое слово завтра", "Next word tomorrow", "Keyingi so'z ertaga"), T.Small).apply { gravity = Gravity.CENTER }, MATCH, WRAP, top = 4)
        result.add(ctx.button(t("Поделиться", "Share", "Ulashish")) {
            val send = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, WordGame.share(guesses, secret, w))
            }
            ctx.startActivity(Intent.createChooser(send, null))
        }, MATCH, WRAP, top = 10)
    }
}
