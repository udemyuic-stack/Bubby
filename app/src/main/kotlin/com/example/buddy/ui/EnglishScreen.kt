package com.example.buddy.ui

import android.speech.tts.TextToSpeech
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import java.time.LocalDate
import java.util.Locale

class EnglishScreen(act: UiActivity) : Screen(act) {
    override fun title() = t("Английский", "English", "Inglizcha")
    override val padH = 12

    private val words = EnglishWords.all
    private var mode = -1
    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private lateinit var stage: LinearLayout

    private fun tr(w: EnglishWord) = t(w.ru, w.ru, w.uz)
    private fun starsText(n: Int) = "★".repeat(n) + "☆".repeat(3 - n)

    private fun stopTts() {
        tts?.stop()
        tts?.shutdown()
        tts = null
        ttsReady = false
    }

    override fun render(c: LinearLayout) {
        if (mode < 0) { stopTts(); picker(c); return }
        c.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: View) {}
            override fun onViewDetachedFromWindow(v: View) { stopTts() }
        })
        c.add(ctx.button(t("← Режимы", "← Modes", "← Rejimlar"), BtnStyle.Text) { mode = -1; refresh() }, WRAP, ctx.dp(48))
        stage = ctx.col {}
        c.add(stage, MATCH, WRAP)
        when (mode) {
            0 -> startCards()
            1 -> startScramble()
            else -> startListen()
        }
    }

    private fun picker(c: LinearLayout) {
        val modes = listOf(
            Triple(t("Карточки", "Flashcards", "Kartochkalar"), t("Повторение по коробкам Лейтнера", "Leitner box repetition", "Leitner qutilari bilan takrorlash"), "🗂"),
            Triple(t("Собери слово", "Word scramble", "So'zni tuz"), t("Составь слово из букв: от коротких к длинным", "Build the word from letters: short to long", "Harflardan so'z tuz: qisqadan uzunga"), "🔤"),
            Triple(t("Слушай и выбери", "Listen and pick", "Tingla va tanla"), t("Bubby произносит слово, выбери перевод", "Bubby says the word, pick the translation", "Bubby so'zni aytadi, tarjimani tanla"), "🎧")
        )
        for ((i, m) in modes.withIndex()) {
            c.add(ctx.card(pad = 14, onClick = { mode = i; refresh() }) {
                add(ctx.row {
                    add(ctx.text(m.third, T.Headline).apply { textSize = 30f }, WRAP, WRAP, end = 14)
                    add(ctx.col {
                        add(ctx.text(m.first, T.Label), MATCH, WRAP)
                        add(ctx.text(m.second, T.Small), MATCH, WRAP, top = 2)
                    }, 0, WRAP, 1f)
                })
            }, MATCH, WRAP, bottom = 8)
        }
    }

    private fun box(i: Int) = Backend.int(ctx, "en_box_$i", 1)

    private fun startCards() {
        val idx = words.indices.shuffled().sortedBy { box(it) }.take(10)
        var pos = 0
        var known = 0
        fun show() {
            stage.removeAllViews()
            if (pos >= idx.size) {
                stage.add(ctx.card(pad = 18) {
                    add(ctx.text(t("Готово!", "Done!", "Tayyor!"), T.Title).apply { gravity = Gravity.CENTER; textSize = 20f }, MATCH, WRAP)
                    add(ctx.text(t("Знаю: $known из ${idx.size}", "Known: $known of ${idx.size}", "Bildim: $known / ${idx.size}"), T.Body).apply { gravity = Gravity.CENTER }, MATCH, WRAP, top = 6)
                    add(ctx.button(t("Ещё раз", "Again", "Qayta")) { refresh() }, MATCH, WRAP, top = 12)
                }, MATCH, WRAP, top = 8)
                return
            }
            val w = words[idx[pos]]
            var flipped = false
            val front = ctx.text(w.en, T.Headline).apply { textSize = 34f; gravity = Gravity.CENTER; typeface = glassFont(bold = true) }
            val back = ctx.text(tr(w), T.Title).apply { gravity = Gravity.CENTER; textSize = 22f; visibility = View.INVISIBLE }
            val buttons = ctx.row(Gravity.CENTER) {}
            stage.add(ctx.text("${pos + 1}/${idx.size} · " + t("коробка", "box", "quti") + " ${box(idx[pos])}", T.Small).apply { gravity = Gravity.CENTER }, MATCH, WRAP, top = 8)
            stage.add(ctx.card(pad = 28, onClick = {
                if (!flipped) { flipped = true; back.visibility = View.VISIBLE; buttons.visibility = View.VISIBLE }
            }) {
                minimumHeight = ctx.dp(180)
                gravity = Gravity.CENTER
                add(front, MATCH, WRAP)
                add(back, MATCH, WRAP, top = 14)
            }, MATCH, WRAP, top = 6, bottom = 10)
            stage.add(ctx.text(t("Нажми на карточку", "Tap the card", "Kartochkani bos"), T.Small).apply { gravity = Gravity.CENTER }, MATCH, WRAP, bottom = 8)
            buttons.visibility = View.INVISIBLE
            buttons.addView(ctx.button(t("Снова", "Again", "Qayta"), BtnStyle.Outline) {
                Backend.setInt(ctx, "en_box_${idx[pos]}", 1); pos++; show()
            }, LinearLayout.LayoutParams(0, WRAP, 1f).apply { setMargins(0, 0, ctx.dp(6), 0) })
            buttons.addView(ctx.button(t("Знаю", "I know", "Bilaman")) {
                Backend.setInt(ctx, "en_box_${idx[pos]}", (box(idx[pos]) + 1).coerceAtMost(5)); known++; pos++; show()
            }, LinearLayout.LayoutParams(0, WRAP, 1f).apply { setMargins(ctx.dp(6), 0, 0, 0) })
            stage.add(buttons, MATCH, WRAP)
        }
        show()
    }

    private fun result(correct: Int, total: Int, paidKey: String) {
        stage.removeAllViews()
        val st = when { correct >= 10 -> 3; correct >= 8 -> 2; correct >= 6 -> 1; else -> 0 }
        val today = LocalDate.now().toEpochDay()
        val paid = Backend.prefs(ctx).getLong(paidKey, -1L) == today
        val earned = if (!paid && st > 0) st else 0
        if (earned > 0) { Backend.prefs(ctx).edit().putLong(paidKey, today).apply(); DailyStreak.addStars(ctx, earned) }
        stage.add(ctx.card(pad = 18) {
            add(ctx.text(t("Готово!", "Done!", "Tayyor!"), T.Title).apply { gravity = Gravity.CENTER; textSize = 20f }, MATCH, WRAP)
            add(ctx.text(starsText(st), T.Headline, P.orange).apply { gravity = Gravity.CENTER }, MATCH, WRAP, top = 4)
            add(ctx.text("$correct / $total", T.Body).apply { gravity = Gravity.CENTER }, MATCH, WRAP, top = 4)
            if (earned > 0) add(ctx.text("+$earned ⭐", T.Title, P.orange).apply { gravity = Gravity.CENTER; textSize = 18f }, MATCH, WRAP, top = 6)
            add(ctx.button(t("Ещё раз", "Again", "Qayta")) { refresh() }, MATCH, WRAP, top = 12)
        }, MATCH, WRAP, top = 8)
    }

    private fun startScramble() {
        val session = words.filter { it.en.length in 3..8 }.shuffled().take(10).sortedBy { it.en.length }
        var pos = 0
        var correct = 0
        fun show() {
            stage.removeAllViews()
            if (pos >= session.size) { result(correct, session.size, "en_paid_1"); return }
            val w = session[pos]
            var letters = w.en.toList().shuffled()
            while (letters.size > 1 && letters.joinToString("") == w.en) letters = letters.shuffled()
            val built = StringBuilder()
            val used = BooleanArray(letters.size)
            val usedOrder = ArrayList<Int>()
            var firstTry = true
            val answer = ctx.text("", T.Headline).apply { textSize = 30f; gravity = Gravity.CENTER; typeface = glassFont(bold = true); minimumHeight = ctx.dp(56) }
            val tiles = ArrayList<View>()
            val flow = FlowLayout(ctx, ctx.dp(8))
            fun sync() {
                answer.text = built.toString().uppercase()
                for (i in tiles.indices) tiles[i].alpha = if (used[i]) 0.25f else 1f
            }
            fun check() {
                if (built.length != w.en.length) return
                if (built.toString() == w.en) {
                    if (firstTry) correct++
                    answer.setTextColor(P.success)
                    answer.postDelayed({ pos++; show() }, 700)
                } else {
                    firstTry = false
                    answer.shake()
                    built.setLength(0); used.fill(false); usedOrder.clear(); sync()
                }
            }
            for (i in letters.indices) {
                val tile = ctx.row(Gravity.CENTER) {
                    background = ctx.rounded(P.alpha(P.violet, 80), 16f)
                    add(ctx.text(letters[i].uppercase(), T.Title), WRAP, WRAP)
                    press {
                        if (!used[i] && built.length < w.en.length) { used[i] = true; usedOrder.add(i); built.append(letters[i]); sync(); check() }
                    }
                }
                tiles.add(tile)
                flow.addView(tile, LinearLayout.LayoutParams(ctx.dp(52), ctx.dp(52)))
            }
            stage.add(ctx.text("${pos + 1}/${session.size}", T.Small).apply { gravity = Gravity.CENTER }, MATCH, WRAP, top = 8)
            stage.add(ctx.card(pad = 18) {
                add(ctx.text(tr(w), T.Title).apply { gravity = Gravity.CENTER; textSize = 22f }, MATCH, WRAP)
                add(answer, MATCH, WRAP, top = 10)
            }, MATCH, WRAP, top = 4, bottom = 12)
            stage.add(flow, MATCH, WRAP, bottom = 12)
            stage.add(ctx.button(t("Стереть", "Undo", "O'chirish"), BtnStyle.Outline) {
                if (usedOrder.isNotEmpty()) { used[usedOrder.removeAt(usedOrder.size - 1)] = false; built.setLength(built.length - 1); sync() }
            }, MATCH, WRAP)
        }
        show()
    }

    private fun speak(word: String) {
        if (ttsReady) tts?.speak(word, TextToSpeech.QUEUE_FLUSH, null, "w")
    }

    private fun startListen() {
        tts = TextToSpeech(ctx) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val r = tts?.setLanguage(Locale.US)
                ttsReady = r != TextToSpeech.LANG_MISSING_DATA && r != TextToSpeech.LANG_NOT_SUPPORTED
            }
        }
        val session = words.shuffled().take(10)
        var pos = 0
        var correct = 0
        fun show() {
            stage.removeAllViews()
            if (pos >= session.size) { result(correct, session.size, "en_paid_2"); return }
            val w = session[pos]
            val opts = (words.filter { it !== w }.shuffled().take(3) + w).shuffled()
            var answered = false
            stage.add(ctx.text("${pos + 1}/${session.size}", T.Small).apply { gravity = Gravity.CENTER }, MATCH, WRAP, top = 8)
            stage.add(ctx.card(pad = 18, onClick = { speak(w.en) }) {
                add(ctx.text("🔊", T.Headline).apply { textSize = 44f; gravity = Gravity.CENTER; contentDescription = t("Повторить", "Replay", "Qayta eshitish") }, MATCH, WRAP)
                add(ctx.text(if (ttsReady) t("Нажми, чтобы послушать", "Tap to listen", "Eshitish uchun bos") else w.en, T.Body).apply { gravity = Gravity.CENTER }, MATCH, WRAP, top = 4)
            }, MATCH, WRAP, top = 4, bottom = 12)
            val boxes = ArrayList<LinearLayout>()
            for ((i, o) in opts.withIndex()) {
                val b = ctx.row(Gravity.CENTER) {
                    background = ctx.rounded(P.alpha(P.teal, 55), 22f, P.teal, 2f)
                    add(ctx.text(tr(o), T.Title).apply { textSize = 18f; gravity = Gravity.CENTER }, MATCH, WRAP)
                    setPadding(ctx.dp(8), ctx.dp(8), ctx.dp(8), ctx.dp(8))
                    press {
                        if (answered) return@press
                        answered = true
                        val right = o === w
                        if (right) correct++
                        boxes[i].background = ctx.rounded(P.alpha(if (right) P.success else P.error, 170), 22f, if (right) P.success else P.error, 2f)
                        if (!right) boxes[opts.indexOf(w)].background = ctx.rounded(P.alpha(P.success, 170), 22f, P.success, 2f)
                        stage.postDelayed({ pos++; show() }, 900)
                    }
                }
                boxes.add(b)
                stage.add(b, MATCH, ctx.dp(58), bottom = 8)
            }
            speak(w.en)
            stage.postDelayed({ if (!answered && pos < session.size && session[pos] === w) speak(w.en) }, 600)
        }
        show()
    }
}
