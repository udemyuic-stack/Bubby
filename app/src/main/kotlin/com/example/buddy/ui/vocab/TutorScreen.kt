package com.example.buddy.ui.vocab

import android.graphics.Color
import android.text.SpannableString
import android.text.Spanned
import android.text.TextPaint
import android.text.method.LinkMovementMethod
import android.text.style.BackgroundColorSpan
import android.text.style.ClickableSpan
import android.view.View
import android.widget.LinearLayout
import com.example.buddy.Tr
import com.example.buddy.ui.*

class TutorScreen(act: UiActivity) : Screen(act) {
    override fun title() = t("ИИ-репетитор", "AI tutor", "AI repetitor")

    private enum class Phase { LOADING, LOAD_ERROR, EMPTY, SETUP, NO_AI, OFFLINE, GENERATING, ERROR, LESSON, DONE }

    private var phase = Phase.LOADING
    private var source: Long? = null
    private var topic: Long? = null
    private var loadedKey: Pair<Long?, Long?>? = null
    private var kind = TutorKind.PASSAGE
    private var targets: List<TutorTarget> = emptyList()
    private var cards: Map<Long, Card> = emptyMap()
    private var lesson: TutorLesson? = null
    private var lessonCards: Map<Long, Card> = emptyMap()
    private var errorText = ""
    private var gen = 0
    private var qIdx = 0
    private var chosen: Int? = null
    private var conf = Confidence.SOME
    private var shownAt = 0L
    private var answered = 0
    private var correct = 0

    override fun onShow() {
        when (phase) {
            Phase.NO_AI -> { phase = Phase.SETUP; refresh() }
            Phase.LOADING, Phase.SETUP, Phase.EMPTY, Phase.LOAD_ERROR -> if (loadedKey != (source to topic) || phase == Phase.LOADING || phase == Phase.EMPTY) load()
            else -> Unit
        }
    }

    private fun load() {
        val key = source to topic
        loadedKey = key
        phase = Phase.LOADING
        refresh()
        val token = ++gen
        Thread {
            val r = try { Tutor.load(ctx, key.first, key.second) } catch (_: Exception) { null }
            act.runIfAlive {
                if (token != gen) return@runIfAlive
                if (r == null) {
                    phase = Phase.LOAD_ERROR
                } else {
                    targets = r.targets
                    cards = r.cards
                    phase = if (r.targets.size < TutorPick.MIN) Phase.EMPTY else Phase.SETUP
                }
                refresh()
            }
        }.start()
    }

    private fun create() {
        if (targets.size < TutorPick.MIN) return
        phase = Phase.GENERATING
        refresh()
        val token = ++gen
        val k = kind
        val list = targets
        val snapshot = cards
        val hint = try { Tutor.topicHint(ctx, topic) } catch (_: Exception) { null }
        Tutor.generate(ctx, k, list, hint) { r ->
            act.runIfAlive {
                if (token != gen) return@runIfAlive
                when (r) {
                    is TutorResult.Ok -> {
                        lesson = r.lesson
                        lessonCards = snapshot
                        qIdx = 0; answered = 0; correct = 0
                        resetQuestion()
                        phase = Phase.LESSON
                    }
                    TutorResult.NoAi -> phase = Phase.NO_AI
                    TutorResult.Offline -> phase = Phase.OFFLINE
                    is TutorResult.Unavailable -> { errorText = t("ИИ недоступен: ", "AI is not available: ", "AI mavjud emas: ") + r.message; phase = Phase.ERROR }
                    is TutorResult.Bad -> { errorText = badText(r.reason); phase = Phase.ERROR }
                }
                refresh()
            }
        }
    }

    private fun badText(reason: String) = when (reason) {
        "missing" -> t("ИИ не использовал все ваши слова. Попробуйте ещё раз.", "The AI text left out some of your words. Try again.", "AI matnida so'zlaringizning bir qismi yo'q. Qayta urinib ko'ring.")
        "questions" -> t("ИИ не составил достаточно правильных вопросов. Попробуйте ещё раз.", "The AI did not write enough valid questions. Try again.", "AI yetarli to'g'ri savol tuzmadi. Qayta urinib ko'ring.")
        else -> t("Ответ ИИ не подошёл. Попробуйте ещё раз.", "The AI reply could not be used. Try again.", "AI javobi yaroqsiz. Qayta urinib ko'ring.")
    }

    private fun resetQuestion() {
        chosen = null
        conf = Confidence.SOME
        shownAt = System.currentTimeMillis()
    }

    override fun render(c: LinearLayout) {
        when (phase) {
            Phase.LOADING -> c.add(ctx.text(t("Подбираю слабые слова…", "Finding your weak words…", "Zaif so'zlaringiz tanlanmoqda…"), T.Small), MATCH, WRAP, top = 24)
            Phase.LOAD_ERROR -> {
                c.add(ctx.emptyState(t("Не удалось загрузить слова", "Could not load your words", "So'zlarni yuklab bo'lmadi"),
                    t("Попробуйте ещё раз.", "Please try again.", "Qayta urinib ko'ring.")), MATCH, WRAP)
                c.add(ctx.button(t("Повторить", "Retry", "Qayta urinish")) { load() }, MATCH, WRAP, top = 8)
            }
            Phase.EMPTY -> empty(c)
            Phase.SETUP -> setup(c)
            Phase.NO_AI -> {
                c.add(ctx.emptyState(t("ИИ не настроен", "AI is not set up", "AI sozlanmagan"),
                    t("Добавьте ключ ИИ-провайдера, чтобы репетитор мог писать тексты.", "Add an AI provider key so the tutor can write texts.", "Repetitor matn yozishi uchun AI provayder kalitini qo'shing.")), MATCH, WRAP)
                c.add(ctx.button(t("Настроить ИИ", "Set up AI", "AI ni sozlash")) { act.push(AiScreen(act)) }, MATCH, WRAP, top = 8)
                c.add(ctx.button(t("Назад", "Back", "Orqaga"), BtnStyle.Text) { phase = Phase.SETUP; refresh() }, MATCH, WRAP, top = 4)
            }
            Phase.OFFLINE -> {
                c.add(ctx.offlineCard(t("Нет подключения к интернету. Тексту нужен ИИ.", "No internet connection. The text needs the AI.", "Internet aloqasi yo'q. Matn uchun AI kerak.")) { create() }, MATCH, WRAP, top = 8)
                c.add(ctx.button(t("Назад", "Back", "Orqaga"), BtnStyle.Text) { phase = Phase.SETUP; refresh() }, MATCH, WRAP, top = 8)
            }
            Phase.GENERATING -> {
                c.add(ctx.text(t("ИИ пишет текст с вашими словами…", "The AI is writing a text with your words…", "AI so'zlaringiz bilan matn yozmoqda…"), T.Body), MATCH, WRAP, top = 24)
                c.add(ctx.text(t("Обычно это занимает до 30 секунд.", "This usually takes up to 30 seconds.", "Bu odatda 30 soniyagacha davom etadi."), T.Small), MATCH, WRAP, top = 6)
            }
            Phase.ERROR -> {
                c.add(ctx.card(16, fill = P.errorContainer, stroke = 0, kind = CardKind.Tint) {
                    add(ctx.text(errorText, T.Body, P.onErrorContainer), MATCH, WRAP)
                }, MATCH, WRAP, top = 8)
                c.add(ctx.button(t("Повторить", "Retry", "Qayta urinish")) { create() }, MATCH, WRAP, top = 10)
                c.add(ctx.button(t("Назад", "Back", "Orqaga"), BtnStyle.Text) { phase = Phase.SETUP; refresh() }, MATCH, WRAP, top = 4)
            }
            Phase.LESSON -> lessonView(c)
            Phase.DONE -> done(c)
        }
    }

    private fun filterRow(c: LinearLayout) {
        val rows = VocabDb.sources(ctx)
        if (source != null && rows.none { it.id == source }) source = null
        val tp = topic?.let { id -> VocabDb.topics(ctx).firstOrNull { it.id == id } }
        if (tp == null) topic = null
        val flow = FlowLayout(ctx)
        flow.addView(ctx.chip(t("Все", "All", "Hammasi"), source == null && topic == null) { source = null; topic = null; load() })
        source?.let { s -> flow.addView(ctx.chip(SourceTree.path(rows, s) + "  ×", true) { source = null; load() }) }
        if (tp != null) flow.addView(ctx.chip(Topics.display(tp, Tr.lang(ctx)) + "  ×", true) { topic = null; load() })
        flow.addView(ctx.chip(t("Фильтр", "Filter", "Filtr"), false) {
            act.push(SourcesScreen(act) { s, tpc -> source = s ?: source; topic = tpc ?: topic })
        })
        c.add(flow, MATCH, WRAP, bottom = 8)
    }

    private fun empty(c: LinearLayout) {
        filterRow(c)
        c.add(ctx.emptyState(t("Сначала потренируйте хотя бы 3 слова", "Practise at least 3 words first", "Avval kamida 3 ta so'zni mashq qiling"),
            t("Репетитор берёт слова, которые вам даются труднее всего. Здесь их пока меньше трёх.", "The tutor uses the words you find hardest. There are fewer than three of them here yet.", "Repetitor sizga eng qiyin so'zlarni oladi. Bu yerda ular hozircha uchtadan kam.")), MATCH, WRAP)
        c.add(ctx.button(t("Начать тренировку", "Start practice", "Mashqni boshlash")) { act.push(ReviewScreen(act)) }, MATCH, WRAP, top = 8)
    }

    private fun setup(c: LinearLayout) {
        filterRow(c)
        c.add(ctx.section(t("Формат", "Format", "Format")))
        val kinds = FlowLayout(ctx)
        kinds.addView(ctx.chip(t("Текст", "Passage", "Matn"), kind == TutorKind.PASSAGE) { kind = TutorKind.PASSAGE; refresh() })
        kinds.addView(ctx.chip(t("Диалог", "Dialogue", "Dialog"), kind == TutorKind.DIALOGUE) { kind = TutorKind.DIALOGUE; refresh() })
        c.add(kinds, MATCH, WRAP, bottom = 8)
        c.add(ctx.section(t("Слабые слова: ${targets.size}", "Weak words: ${targets.size}", "Zaif so'zlar: ${targets.size}")))
        val words = FlowLayout(ctx)
        for (w in targets) words.addView(ctx.pill(w.shown, P.primary))
        c.add(words, MATCH, WRAP, bottom = 12)
        c.add(ctx.button(t("Создать текст", "Create text", "Matn yaratish")) { create() }, MATCH, WRAP, top = 8)
    }

    private fun lessonView(c: LinearLayout) {
        val l = lesson ?: return
        c.add(ctx.card(18) {
            if (l.title.isNotBlank()) add(ctx.text(l.title, T.Title), MATCH, WRAP, bottom = 8)
            add(passage(l), MATCH, WRAP)
            add(ctx.text(t("Нажмите на выделенное слово, чтобы открыть карточку.", "Tap a highlighted word to open its card.", "Kartani ochish uchun ajratilgan so'zni bosing."), T.Small), MATCH, WRAP, top = 8)
        }, MATCH, WRAP, top = 4, bottom = 12)
        val q = l.questions.getOrNull(qIdx) ?: return
        c.add(ctx.text(t("Вопрос ${qIdx + 1} из ${l.questions.size}", "Question ${qIdx + 1} of ${l.questions.size}", "Savol ${qIdx + 1} / ${l.questions.size}"), T.Small), MATCH, WRAP, bottom = 6)
        c.add(ctx.card(16) {
            add(ctx.text(if (q.type == "gap") t("Вставьте слово", "Fill the gap", "Bo'sh joyni to'ldiring") else t("Вопрос по тексту", "Question about the text", "Matn bo'yicha savol"), T.Small), MATCH, WRAP, bottom = 6)
            add(ctx.text(q.prompt, T.Body), MATCH, WRAP)
        }, MATCH, WRAP, bottom = 10)
        val pick = chosen
        if (pick == null) {
            for ((i, o) in q.options.withIndex()) c.add(ctx.button(o, BtnStyle.Tonal) { submit(q, i) }, MATCH, WRAP, bottom = 6)
            c.add(ctx.text(t("Насколько вы уверены?", "How sure are you?", "Qanchalik ishonchingiz komil?"), T.Small), MATCH, WRAP, top = 12, bottom = 4)
            val chips = FlowLayout(ctx)
            chips.addView(ctx.chip(t("Угадываю", "Guessing", "Taxmin"), conf == Confidence.GUESS) { conf = Confidence.GUESS; refresh() })
            chips.addView(ctx.chip(t("Почти уверен", "Somewhat sure", "Deyarli aniq"), conf == Confidence.SOME) { conf = Confidence.SOME; refresh() })
            chips.addView(ctx.chip(t("Уверен", "Very sure", "Aniq"), conf == Confidence.SURE) { conf = Confidence.SURE; refresh() })
            c.add(chips, MATCH, WRAP)
            c.add(ctx.button(t("Пропустить", "Skip", "O'tkazib yuborish"), BtnStyle.Text) { advance() }, WRAP, WRAP, top = 8)
            return
        }
        val ok = pick == q.answer
        c.add(ctx.card(16) {
            add(ctx.pill(if (ok) t("Верно", "Correct", "To'g'ri") else t("Неверно", "Not quite", "Noto'g'ri"), if (ok) P.success else P.rose), WRAP, WRAP)
            if (!ok) add(ctx.text(t("Ваш ответ: ", "Your answer: ", "Sizning javobingiz: ") + q.options[pick], T.Small), MATCH, WRAP, top = 8)
            add(ctx.text(t("Правильно: ", "Correct: ", "To'g'ri javob: ") + q.options[q.answer], T.Label), MATCH, WRAP, top = 6)
            if (q.why.isNotBlank()) add(ctx.text(q.why, T.Small), MATCH, WRAP, top = 6)
        }, MATCH, WRAP, bottom = 10)
        c.add(ctx.button(t("Дальше", "Next", "Keyingisi")) { advance() }, MATCH, WRAP)
    }

    private fun passage(l: TutorLesson): View {
        val tv = ctx.text("", T.Body)
        val sp = SpannableString(l.text)
        for ((sense, ranges) in l.hits) {
            for (r in ranges) {
                val s = r.first.coerceIn(0, l.text.length)
                val e = (r.last + 1).coerceIn(s, l.text.length)
                if (e <= s) continue
                sp.setSpan(BackgroundColorSpan(P.primaryContainer), s, e, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                sp.setSpan(object : ClickableSpan() {
                    override fun onClick(widget: View) { wordCard(sense) }
                    override fun updateDrawState(ds: TextPaint) { ds.isUnderlineText = false }
                }, s, e, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
        }
        tv.text = sp
        tv.movementMethod = LinkMovementMethod.getInstance()
        tv.highlightColor = Color.TRANSPARENT
        return tv
    }

    private fun wordCard(sense: Long) {
        val card = lessonCards[sense]
        val target = targets.firstOrNull { it.sense == sense }
        val title = card?.original ?: target?.shown ?: return
        val lines = ArrayList<String>()
        if (card != null) {
            val head = listOf(card.lemma.takeIf { it.isNotBlank() && it != card.original }, card.ipa.takeIf { it.isNotBlank() }?.let { "/$it/" }, card.pos.takeIf { it.isNotBlank() }).filterNotNull()
            if (head.isNotEmpty()) lines.add(head.joinToString("  ·  "))
            if (card.definition.isNotBlank()) lines.add(card.definition)
            if (card.translations.isNotEmpty()) lines.add(card.translations.joinToString(", "))
            card.examples.firstOrNull()?.let { x -> lines.add("• " + x.text + if (x.translation.isNotBlank()) "\n   " + x.translation else "") }
        } else if (target != null) {
            if (target.definition.isNotBlank()) lines.add(target.definition)
            if (target.translations.isNotEmpty()) lines.add(target.translations.joinToString(", "))
        }
        ctx.dialogBuilder().setTitle(title).setMessage(lines.joinToString("\n\n"))
            .setPositiveButton(t("Закрыть", "Close", "Yopish"), null)
            .show()
    }

    private fun submit(q: TutorQuestion, i: Int) {
        if (chosen != null) return
        chosen = i
        val ms = (System.currentTimeMillis() - shownAt).toInt().coerceAtLeast(0)
        val c = conf
        answered++
        if (i == q.answer) correct++
        val app = ctx.applicationContext
        Thread {
            val saved = try { Tutor.answer(app, q, i, c, ms); true } catch (_: Exception) { false }
            if (!saved) act.runIfAlive { ctx.toast(t("Не удалось сохранить ответ", "Could not save the answer", "Javobni saqlab bo'lmadi")) }
        }.start()
        refresh()
    }

    private fun advance() {
        val l = lesson ?: return
        if (qIdx + 1 >= l.questions.size) { phase = Phase.DONE; refresh(); return }
        qIdx++
        resetQuestion()
        refresh()
    }

    private fun done(c: LinearLayout) {
        c.add(ctx.card(18) {
            add(ctx.text(t("$correct из $answered верно", "$correct of $answered correct", "$answered tadan $correct to'g'ri"), T.Title), MATCH, WRAP)
            add(ctx.text(t("Ответы засчитаны в прогресс этих слов.", "Your answers count towards these words' progress.", "Javoblar shu so'zlar natijasiga qo'shildi."), T.Small), MATCH, WRAP, top = 8)
            add(ctx.button(t("Новый текст", "New text", "Yangi matn")) { lesson = null; load() }, MATCH, WRAP, top = 12)
            add(ctx.button(t("Назад", "Back", "Orqaga"), BtnStyle.Text) { act.pop() }, MATCH, WRAP, top = 4)
        }, MATCH, WRAP, top = 8)
    }
}
