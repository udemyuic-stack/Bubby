package com.example.buddy.ui.vocab

import android.widget.EditText
import android.widget.LinearLayout
import com.example.buddy.Brain
import com.example.buddy.R
import com.example.buddy.Tr
import com.example.buddy.ui.*

class ReviewScreen(act: UiActivity) : Screen(act) {
    override fun title() = t("Повторение", "Practice", "Takrorlash")

    private enum class Phase { LOADING, EMPTY, ASKING, JUDGING, GRADED, DONE }

    private var phase = Phase.LOADING
    private var data: ReviewData? = null
    private val queue = ArrayList<Drill>()
    private var idx = 0
    private var ex: Exercise? = null
    private var shownAt = 0L
    private var conf: Confidence? = null
    private var picked: String? = null
    private var typed = ""
    private var grade: Grade? = null
    private var aiNote = ""
    private var skippedScore = false
    private var reflection = ""
    private var asked = 0
    private var correct = 0
    private var started = false
    private var aiOn = false
    private var canSkip = false
    private var day = 0L
    private var input: EditText? = null
    private var missedSure = false

    private fun lang() = Tr.lang(ctx)

    override fun onShow() {
        if (started) return
        started = true
        day = VocabDb.today()
        Thread {
            val d = try { ReviewLoader.load(ctx) } catch (_: Exception) { null }
            val rows = try { VocabDb.attemptRows(ctx, VocabDb.activePair(ctx), day - 30) } catch (_: Exception) { emptyList() }
            val on = try { Brain(ctx.applicationContext).keyList().isNotEmpty() } catch (_: Exception) { false }
            act.runOnUiThread {
                aiOn = on
                data = d
                if (d != null) {
                    queue.addAll(Planner.plan(d.items, day, Analytics.options(rows, day, on, VocabDb.activePair(ctx))))
                }
                next(0)
            }
        }.start()
    }

    private fun build(q: Drill, n: Int): Exercise? {
        val d = data ?: return null
        val card = d.cards[q.item.sense] ?: return null
        val seed = day * 31 + q.item.sense * 7 + n
        Exercises.build(q.type, card, d.words[q.item.pair] ?: emptyList(), d.meanings[q.item.pair] ?: emptyList(), seed)?.let { return it }
        val alt = Planner.retry(q, aiOn) ?: return null
        return Exercises.build(alt.type, card, d.words[q.item.pair] ?: emptyList(), d.meanings[q.item.pair] ?: emptyList(), seed)
    }

    private fun next(from: Int) {
        var i = from
        while (i < queue.size) {
            val e = build(queue[i], i)
            if (e != null) {
                idx = i
                ex = e
                conf = null; picked = null; typed = ""; grade = null; aiNote = ""; skippedScore = false; missedSure = false
                shownAt = System.currentTimeMillis()
                canSkip = false
                phase = Phase.ASKING
                refresh()
                content.postDelayed({ if (phase == Phase.ASKING && ex === e) { canSkip = true; refresh() } }, 3000)
                return
            }
            i++
        }
        if (asked > 0) VocabDb.saveSession(ctx, day, asked, correct, reflection.ifBlank { null })
        phase = if (asked == 0) Phase.EMPTY else Phase.DONE
        refresh()
    }

    private fun record(ok: Boolean, c: Confidence, answer: String) {
        val q = queue[idx]
        val e = ex ?: return
        val ms = (System.currentTimeMillis() - shownAt).toInt().coerceAtLeast(0)
        VocabDb.recordAttempt(ctx, q.item.sense, e.dim, Attempt(day, e.type, ok, c, ms, e.context), answer)
        asked++
        if (ok) correct++
        missedSure = Exercises.isHighConfidenceMiss(ok, c)
        if (!ok && !q.retry) {
            val r = Planner.retry(q, aiOn)
            if (r != null) Planner.reinsert(queue, idx, r)
        }
    }

    private fun submit(answer: String, dontKnow: Boolean = false) {
        val e = ex ?: return
        if (phase != Phase.ASKING) return
        typed = answer
        val c = if (dontKnow) Confidence.GUESS else conf ?: Confidence.SOME
        if (e.ask == Ask.SENTENCE) { judge(answer, c); return }
        val g = if (dontKnow) Grade.WRONG else Exercises.grade(e, answer)
        grade = g
        record(g != Grade.WRONG, c, answer)
        phase = Phase.GRADED
        refresh()
    }

    private fun judge(sentence: String, c: Confidence) {
        val card = data?.cards?.get(queue[idx].item.sense) ?: return
        if (sentence.isBlank() || !Judge.containsWord(sentence, card)) {
            grade = Grade.WRONG
            aiNote = t("В предложении нет этого слова.", "The sentence does not contain this word.", "Gapda bu so'z yo'q.")
            record(false, c, sentence)
            phase = Phase.GRADED
            refresh()
            return
        }
        phase = Phase.JUDGING
        refresh()
        val e = ex
        Thread {
            val v = try {
                val raw = BrainVocabAi(ctx).complete(Judge.system(card, lang()), sentence)
                Judge.parse(raw)
            } catch (_: Exception) { null }
            act.runOnUiThread {
                if (ex !== e) return@runOnUiThread
                if (v == null) {
                    skippedScore = true
                    grade = null
                    aiNote = t("ИИ недоступен: это упражнение не засчитано.", "AI is not available: this exercise is not scored.", "AI mavjud emas: bu mashq hisobga olinmadi.")
                } else {
                    grade = if (v.ok) Grade.CORRECT else Grade.WRONG
                    aiNote = v.why
                    record(v.ok, c, sentence)
                }
                phase = Phase.GRADED
                refresh()
            }
        }.start()
    }

    override fun render(c: LinearLayout) {
        when (phase) {
            Phase.LOADING -> c.add(ctx.text(t("Готовлю вопросы…", "Preparing questions…", "Savollar tayyorlanmoqda…"), T.Small), MATCH, WRAP, top = 24)
            Phase.EMPTY -> c.add(ctx.emptyState(t("Сейчас повторять нечего", "Nothing to practise right now", "Hozir takrorlash uchun hech narsa yo'q"),
                t("Добавьте слова и дождитесь, пока они будут дополнены, или вернитесь позже.", "Add words and wait until they are filled in, or come back later.", "So'z qo'shing va to'ldirilishini kuting yoki keyinroq qayting.")), MATCH, WRAP)
            Phase.DONE -> done(c)
            else -> question(c)
        }
    }

    private fun done(c: LinearLayout) {
        val pct = if (asked == 0) 0 else correct * 100 / asked
        c.add(ctx.card(18) {
            add(ctx.text(t("Готово: $correct из $asked", "Done: $correct of $asked", "Tugadi: $asked tadan $correct"), T.Title), MATCH, WRAP)
            add(ctx.text("$pct%", T.Title).apply { textSize = 32f }, MATCH, WRAP, top = 6)
            add(ctx.text(t("Ошибки вернутся в ближайшие дни: так слова запоминаются крепче.", "Misses come back in the next days: that is what makes words stick.", "Xatolar yaqin kunlarda qaytadi: so'zlar shunday mustahkam yodda qoladi."), T.Small), MATCH, WRAP, top = 8)
            add(ctx.button(t("Готово", "Done", "Tayyor")) { act.pop() }, MATCH, WRAP, top = 12)
        }, MATCH, WRAP, top = 8)
    }

    private fun question(c: LinearLayout) {
        val e = ex ?: return
        val total = queue.size
        c.add(ctx.text(t("Вопрос ${idx + 1} из $total", "Question ${idx + 1} of $total", "Savol ${idx + 1} / $total"), T.Small), MATCH, WRAP, top = 4, bottom = 6)
        val graded = phase == Phase.GRADED
        c.add(ctx.card(18) {
            add(ctx.text(instruction(e), T.Small), MATCH, WRAP, bottom = 8)
            add(ctx.text(e.question, T.Title).apply { textSize = 24f }, MATCH, WRAP)
            if (e.prompt.isNotBlank()) add(ctx.text(e.prompt, T.Small), MATCH, WRAP, top = 4)
        }, MATCH, WRAP, bottom = 10)
        if (!graded) {
            if (phase == Phase.JUDGING) { c.add(ctx.text(t("Проверяю предложение…", "Checking your sentence…", "Gapingiz tekshirilmoqda…"), T.Small), MATCH, WRAP, top = 8); return }
            when (e.ask) {
                Ask.CHOICE -> for (o in e.options) c.add(ctx.button(o, BtnStyle.Tonal) { submit(o) }, MATCH, WRAP, bottom = 6)
                else -> {
                    val f = ctx.field(if (e.ask == Ask.SENTENCE) t("Ваше предложение", "Your sentence", "Sizning gapingiz") else t("Ваш ответ", "Your answer", "Javobingiz"), typed, multiline = e.ask == Ask.SENTENCE)
                    input = f
                    c.add(f, MATCH, WRAP, bottom = 8)
                    c.add(ctx.button(t("Проверить", "Check", "Tekshirish")) { submit(f.text.toString()) }, MATCH, WRAP)
                }
            }
            c.add(ctx.text(t("Насколько вы уверены? (необязательно)", "How sure are you? (optional)", "Qanchalik ishonchingiz komil? (ixtiyoriy)"), T.Small), MATCH, WRAP, top = 12, bottom = 4)
            val chips = FlowLayout(ctx)
            chips.addView(ctx.chip(t("Угадываю", "Guessing", "Taxmin"), conf == Confidence.GUESS) { conf = Confidence.GUESS; refreshKeep() })
            chips.addView(ctx.chip(t("Почти уверен", "Somewhat sure", "Deyarli aniq"), conf == Confidence.SOME) { conf = Confidence.SOME; refreshKeep() })
            chips.addView(ctx.chip(t("Уверен", "Very sure", "Aniq"), conf == Confidence.SURE) { conf = Confidence.SURE; refreshKeep() })
            c.add(chips, MATCH, WRAP)
            if (canSkip) c.add(ctx.button(t("Не знаю", "I don't know", "Bilmayman"), BtnStyle.Text) { submit("", true) }, WRAP, WRAP, top = 8)
            return
        }
        feedback(c, e)
    }

    private fun refreshKeep() {
        typed = input?.text?.toString() ?: typed
        refresh()
    }

    private fun instruction(e: Exercise) = when (e.type) {
        "choice_meaning" -> t("Выберите перевод", "Choose the meaning", "Tarjimani tanlang")
        "choice_word" -> t("Выберите слово", "Choose the word", "So'zni tanlang")
        "recall_meaning" -> t("Напишите перевод", "Type the meaning", "Tarjimasini yozing")
        "recall_definition" -> t("Какое это слово?", "Which word is this?", "Bu qaysi so'z?")
        "type_word" -> t("Напишите слово", "Type the word", "So'zni yozing")
        "cloze" -> t("Вставьте пропущенное слово", "Fill in the missing word", "Tushib qolgan so'zni yozing")
        "own_sentence" -> t("Составьте предложение с этим словом", "Write a sentence with this word", "Shu so'z bilan gap tuzing")
        "lang_drill" -> t("Особенность языка", "Language detail", "Til xususiyati")
        else -> ""
    }

    private fun feedback(c: LinearLayout, e: Exercise) {
        val g = grade
        val card = data?.cards?.get(queue[idx].item.sense)
        c.add(ctx.card(16) {
            val head = when {
                skippedScore -> t("Не засчитано", "Not scored", "Hisobga olinmadi")
                g == Grade.CORRECT -> t("Верно", "Correct", "To'g'ri")
                g == Grade.CLOSE -> t("Почти: проверьте написание", "Almost: check the spelling", "Deyarli: imloni tekshiring")
                else -> t("Неверно", "Not quite", "Noto'g'ri")
            }
            add(ctx.pill(head, if (g == Grade.CORRECT) P.success else if (g == Grade.CLOSE) P.orange else if (skippedScore) P.sky else P.rose), WRAP, WRAP)
            if (typed.isNotBlank() && g != Grade.CORRECT) add(ctx.text(t("Ваш ответ: ", "Your answer: ", "Sizning javobingiz: ") + typed, T.Small), MATCH, WRAP, top = 8)
            if (e.ask != Ask.SENTENCE || g != Grade.CORRECT) add(ctx.text(t("Правильно: ", "Correct: ", "To'g'ri javob: ") + e.shown, T.Label), MATCH, WRAP, top = 6)
            if (aiNote.isNotBlank()) add(ctx.text(aiNote, T.Small), MATCH, WRAP, top = 6)
            if (card != null) {
                if (card.definition.isNotBlank()) add(ctx.text(card.original + ": " + card.definition, T.Small), MATCH, WRAP, top = 8)
                card.examples.firstOrNull()?.let { x ->
                    add(ctx.text("• " + x.text, T.Small), MATCH, WRAP, top = 4)
                    if (x.translation.isNotBlank()) add(ctx.text("   " + x.translation, T.Small), MATCH, WRAP)
                }
            }
        }, MATCH, WRAP, bottom = 10)
        if (missedSure) {
            c.add(ctx.text(t("Вы были уверены, но ошиблись. Почему так показалось? (необязательно)", "You were sure but missed. Why did it feel right? (optional)", "Aniq edingiz, lekin xato bo'ldi. Nega to'g'ri tuyuldi? (ixtiyoriy)"), T.Small), MATCH, WRAP, bottom = 4)
            val f = ctx.field(t("Одна строка", "One line", "Bir qator"), reflection)
            input = f
            c.add(f, MATCH, WRAP, bottom = 8)
        }
        c.add(ctx.button(t("Дальше", "Next", "Keyingisi")) {
            if (missedSure) input?.text?.toString()?.trim()?.takeIf { it.isNotEmpty() }?.let { reflection = it }
            next(idx + 1)
        }, MATCH, WRAP)
    }
}
