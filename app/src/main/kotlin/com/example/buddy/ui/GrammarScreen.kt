package com.example.buddy.ui

import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.LinearLayout
import com.example.buddy.Brain
import com.example.buddy.R
import com.example.buddy.ui.vocab.BrainVocabAi
import kotlin.random.Random

class GrammarScreen(act: UiActivity) : Screen(act) {
    override fun title() = t("Грамматика", "Grammar", "Grammatika")
    override val padH = 12

    private enum class Mode { HOME, PLAY, RESULT, AI }
    private enum class AiState { IDLE, LOADING, NO_KEYS, OFFLINE, BAD }

    private var mode = Mode.HOME
    private var session: GrammarSession? = null
    private var source = ""
    private var aiQuestions: List<GrammarQ> = emptyList()
    private var aiState = AiState.IDLE
    private var aiLevel = "B1"
    private var aiTopic = GrammarBank.topics.first().id
    private var aiRequest = 0
    private val rules = HashSet<String>()
    private val results = ArrayList<Boolean>()
    private val rnd = Random(System.nanoTime())
    private lateinit var stage: LinearLayout

    private fun starsText(n: Int) = "★".repeat(n) + "☆".repeat(3 - n)
    private fun topicStars(id: String) = Backend.int(ctx, "gr_stars_$id", 0)
    private fun weak() = GrammarWeak.decode(Backend.str(ctx, "gr_weak"))
    private fun topicName(tp: GrammarTopic) = t(tp.ru, tp.en, tp.uz)
    private fun levelColor(level: String) = when (level) { "A2" -> P.teal; "B1" -> P.sky; "B2" -> P.violet; else -> P.rose }

    override fun render(c: LinearLayout) {
        when (mode) {
            Mode.HOME -> home(c)
            Mode.AI -> aiRound(c)
            else -> {
                c.add(ctx.button(t("← Темы", "← Topics", "← Mavzular"), BtnStyle.Text) { home() }, WRAP, ctx.dp(48))
                stage = ctx.col {}
                c.add(stage, MATCH, WRAP)
                if (mode == Mode.PLAY) show() else result()
            }
        }
    }

    private fun home() {
        mode = Mode.HOME
        session = null
        aiRequest++
        refresh()
    }

    private fun home(c: LinearLayout) {
        val total = GrammarBank.topics.sumOf { topicStars(it.id) }
        val max = GrammarBank.topics.size * 3
        c.add(ctx.card(pad = 16) {
            add(ctx.row {
                add(ctx.col {
                    add(ctx.text(t("Грамматика", "Grammar", "Grammatika"), T.Title), MATCH, WRAP)
                    add(ctx.text(t("Выбери тему: 10 вопросов, 4 вида заданий.", "Pick a topic: 10 questions, 4 kinds of tasks.", "Mavzu tanla: 10 savol, 4 xil topshiriq."), T.Small), MATCH, WRAP, top = 4)
                }, 0, WRAP, 1f)
                add(ctx.text("★ $total/$max", T.Title, P.orange), WRAP, WRAP, start = 8)
            })
        }, MATCH, WRAP, bottom = 10)
        c.add(ctx.button(t("Грамматическая битва", "Grammar Battle", "Grammatika jangi"), BtnStyle.Filled, R.drawable.ic_play) {
            act.push(BattleScreen(act, grammar = true))
        }, MATCH, WRAP, bottom = 8)
        val weak = weak()
        if (weak.isNotEmpty()) c.add(ctx.button(t("Слабые места (${weak.size})", "Weak spots (${weak.size})", "Zaif joylar (${weak.size})"), BtnStyle.Tonal) {
            start("weak", GrammarWeak.questions(weak, rnd))
        }, MATCH, WRAP, bottom = 8)
        c.add(ctx.button(t("Раунд от ИИ", "AI round", "AI raundi"), BtnStyle.Outline) {
            mode = Mode.AI; aiState = AiState.IDLE; refresh()
        }, MATCH, WRAP)
        c.add(ctx.section(t("Темы", "Topics", "Mavzular")))
        for (tp in GrammarBank.topics) topicCard(c, tp)
    }

    private fun topicCard(c: LinearLayout, tp: GrammarTopic) {
        val open = tp.id in rules
        c.add(ctx.card(pad = 14, onClick = { start(tp.id, GrammarSession.topic(tp, rnd).questions) }) {
            add(ctx.row {
                add(ctx.col {
                    add(ctx.text(topicName(tp), T.Label), MATCH, WRAP)
                    add(ctx.row {
                        add(ctx.pill(tp.level, levelColor(tp.level)), WRAP, WRAP, end = 8)
                        add(ctx.text(starsText(topicStars(tp.id)), T.Body, P.orange), WRAP, WRAP)
                    }, MATCH, WRAP, top = 6)
                }, 0, WRAP, 1f)
                add(ctx.button(if (open) t("Скрыть", "Hide", "Yashirish") else t("Правило", "Rule", "Qoida"), BtnStyle.Text) {
                    if (!rules.remove(tp.id)) rules.add(tp.id)
                    refresh()
                }, WRAP, ctx.dp(48), start = 6)
            })
            if (open) add(ctx.text(t(tp.ruleRu, tp.ruleEn, tp.ruleUz), T.Small), MATCH, WRAP, top = 8)
        }, MATCH, WRAP, bottom = 8)
    }

    private fun start(src: String, qs: List<GrammarQ>) {
        if (qs.isEmpty()) return
        source = src
        session = GrammarSession(qs)
        results.clear()
        mode = Mode.PLAY
        refresh()
    }

    private fun retry() {
        val qs = when (source) {
            "weak" -> GrammarWeak.questions(weak(), rnd)
            GrammarWeak.AI_TOPIC -> aiQuestions.shuffled(rnd)
            else -> GrammarBank.topic(source)?.let { GrammarSession.topic(it, rnd).questions } ?: emptyList()
        }
        if (qs.isEmpty()) home() else start(source, qs)
    }

    private fun kindLabel(k: GKind) = when (k) {
        GKind.CHOOSE -> t("Выбери ответ", "Choose the answer", "Javobni tanla")
        GKind.MISTAKE -> t("Найди ошибку", "Find the mistake", "Xatoni top")
        GKind.ORDER -> t("Собери предложение", "Build the sentence", "Gapni tuz")
        GKind.TYPE -> t("Впиши форму", "Type the form", "Shaklni yoz")
    }

    private fun dots(s: GrammarSession): View = ctx.row(Gravity.CENTER) {
        for (i in s.questions.indices) {
            val colour = when {
                i < results.size -> if (results[i]) P.success else P.error
                i == s.index -> P.orange
                else -> P.alpha(Glass.TEXT2, 90)
            }
            add(View(ctx).apply { background = ctx.rounded(colour, 6f) }, ctx.dp(if (i == s.index) 18 else 10), ctx.dp(10), start = 3, end = 3)
        }
    }

    private fun show() {
        val s = session ?: return home()
        stage.removeAllViews()
        val q = s.current ?: return finish()
        stage.add(dots(s), MATCH, WRAP, top = 6)
        stage.add(ctx.row {
            add(ctx.text("${s.index + 1}/${s.questions.size} · " + kindLabel(q.kind), T.Small), 0, WRAP, 1f)
            if (s.streak >= 2) add(ctx.text("🔥 ${s.streak}", T.Label, P.orange), WRAP, WRAP)
        }, MATCH, WRAP, top = 10)
        val feedback = ctx.col {}
        val card = ctx.card(pad = 18) {}
        stage.add(card, MATCH, WRAP, top = 6, bottom = 10)
        val input = ctx.col {}
        stage.add(input, MATCH, WRAP)
        stage.add(feedback, MATCH, WRAP, top = 10)
        var answered = false
        val done: (Boolean) -> Unit = { right ->
            if (!answered) {
                answered = true
                answer(q, right, card, feedback)
            }
        }
        when (q.kind) {
            GKind.CHOOSE -> choose(q, card, input, done)
            GKind.MISTAKE -> mistake(q, card, done)
            GKind.ORDER -> order(q, card, input, done)
            GKind.TYPE -> typed(q, card, input, done)
        }
        if (Motion.enabled(ctx)) {
            card.alpha = 0f
            card.translationX = ctx.dpf(28f)
            card.animate().alpha(1f).translationX(0f).setDuration(Motion.STANDARD).setInterpolator(Motion.standard).start()
        }
    }

    private fun prompt(card: LinearLayout, text: String) {
        card.add(ctx.text(text, T.Title).apply { textSize = 20f; gravity = Gravity.CENTER }, MATCH, WRAP)
    }

    private fun optionBox(label: String, onTap: () -> Unit): LinearLayout = ctx.row(Gravity.CENTER) {
        minimumHeight = ctx.dp(54)
        setPadding(ctx.dp(12), ctx.dp(8), ctx.dp(12), ctx.dp(8))
        background = ctx.rounded(P.alpha(P.teal, 55), 22f, P.teal, 2f)
        add(ctx.text(label, T.Title).apply { gravity = Gravity.CENTER }, MATCH, WRAP)
        press(onTap)
    }

    private fun mark(v: View, right: Boolean) {
        val c = if (right) P.success else P.error
        v.background = ctx.rounded(P.alpha(c, 170), 22f, c, 2f)
    }

    private fun choose(q: GrammarQ, card: LinearLayout, input: LinearLayout, done: (Boolean) -> Unit) {
        prompt(card, q.text)
        val opts = GrammarCheck.options(q, rnd)
        val boxes = ArrayList<LinearLayout>()
        var locked = false
        for (o in opts) {
            val b = optionBox(o) {
                if (locked) return@optionBox
                locked = true
                val right = GrammarCheck.choose(q, o)
                mark(boxes[opts.indexOf(o)], right)
                if (!right) mark(boxes[opts.indexOf(q.answers.first())], true)
                done(right)
            }
            boxes.add(b)
            input.add(b, MATCH, WRAP, bottom = 8)
        }
    }

    private fun mistake(q: GrammarQ, card: LinearLayout, done: (Boolean) -> Unit) {
        card.add(ctx.text(t("Нажми на неверное слово", "Tap the wrong word", "Noto'g'ri so'zni bos"), T.Small).apply { gravity = Gravity.CENTER }, MATCH, WRAP, bottom = 10)
        val flow = FlowLayout(ctx, ctx.dp(8))
        val chips = ArrayList<View>()
        var locked = false
        for ((i, w) in q.words().withIndex()) {
            val chip = ctx.row(Gravity.CENTER) {
                minimumHeight = ctx.dp(48)
                minimumWidth = ctx.dp(48)
                setPadding(ctx.dp(12), ctx.dp(6), ctx.dp(12), ctx.dp(6))
                background = ctx.rounded(P.alpha(P.violet, 60), 16f)
                add(ctx.text(w, T.Title), WRAP, WRAP)
                press {
                    if (locked) return@press
                    locked = true
                    val right = GrammarCheck.mistake(q, i)
                    mark(chips[i], right)
                    if (!right && q.wrongIndex in chips.indices) mark(chips[q.wrongIndex], true)
                    done(right)
                }
            }
            chips.add(chip)
            flow.addView(chip)
        }
        card.add(flow, MATCH, WRAP)
    }

    private fun order(q: GrammarQ, card: LinearLayout, input: LinearLayout, done: (Boolean) -> Unit) {
        val tiles = GrammarCheck.tiles(q, rnd)
        val placed = ArrayList<Int>()
        val line = FlowLayout(ctx, ctx.dp(8))
        val bank = FlowLayout(ctx, ctx.dp(8))
        val bankViews = ArrayList<View>()
        var locked = false
        lateinit var sync: () -> Unit
        fun tile(word: String, tint: Int, onTap: () -> Unit) = ctx.row(Gravity.CENTER) {
            minimumHeight = ctx.dp(48)
            minimumWidth = ctx.dp(48)
            setPadding(ctx.dp(12), ctx.dp(6), ctx.dp(12), ctx.dp(6))
            background = ctx.rounded(P.alpha(tint, 70), 16f)
            add(ctx.text(word, T.Title), WRAP, WRAP)
            press { if (!locked) onTap() }
        }
        sync = {
            line.removeAllViews()
            for ((k, idx) in placed.withIndex()) line.addView(tile(tiles[idx], P.orange) { placed.removeAt(k); sync() })
            for (i in bankViews.indices) bankViews[i].visibility = if (i in placed) View.INVISIBLE else View.VISIBLE
        }
        for ((i, w) in tiles.withIndex()) {
            val v = tile(w, P.violet) { if (i !in placed) { placed.add(i); sync() } }
            bankViews.add(v)
            bank.addView(v)
        }
        card.add(ctx.text(t("Нажимай слова по порядку", "Tap the words in order", "So'zlarni tartib bilan bos"), T.Small).apply { gravity = Gravity.CENTER }, MATCH, WRAP, bottom = 10)
        card.add(line.apply { minimumHeight = ctx.dp(56) }, MATCH, WRAP)
        input.add(bank, MATCH, WRAP, bottom = 12)
        input.add(ctx.button(t("Проверить", "Check", "Tekshirish")) {
            if (locked || placed.size != tiles.size) {
                if (!locked) line.shake()
                return@button
            }
            locked = true
            done(GrammarCheck.order(q, placed.map { tiles[it] }))
        }, MATCH, WRAP)
    }

    private fun typed(q: GrammarQ, card: LinearLayout, input: LinearLayout, done: (Boolean) -> Unit) {
        prompt(card, q.text)
        val field = ctx.field(t("Ответ", "Answer", "Javob"))
        field.setSingleLine(true)
        field.imeOptions = EditorInfo.IME_ACTION_DONE
        var locked = false
        val check = {
            if (!locked && field.text.toString().isNotBlank()) {
                locked = true
                field.isEnabled = false
                done(GrammarCheck.typed(q, field.text.toString()))
            }
        }
        field.setOnEditorActionListener { _, id, _ -> if (id == EditorInfo.IME_ACTION_DONE) { check(); true } else false }
        input.add(field, MATCH, WRAP, bottom = 10)
        input.add(ctx.button(t("Проверить", "Check", "Tekshirish")) { check() }, MATCH, WRAP)
    }

    private fun answer(q: GrammarQ, right: Boolean, card: View, feedback: LinearLayout) {
        val s = session ?: return
        s.answer(right)
        results.add(right)
        Backend.setStr(ctx, "gr_weak", GrammarWeak.encode(GrammarWeak.update(weak(), q, right)))
        if (right) card.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY) else card.shake()
        feedback.add(ctx.card(pad = 14, fill = P.alpha(if (right) P.success else P.error, 60), kind = CardKind.Tint) {
            add(ctx.text(if (right) t("Верно!", "Correct!", "To'g'ri!") else t("Неверно", "Not quite", "Noto'g'ri"), T.Title, if (right) P.success else P.error), MATCH, WRAP)
            add(ctx.text(q.filled(), T.Body), MATCH, WRAP, top = 6)
            if (q.why.isNotBlank()) add(ctx.text(q.why, T.Small), MATCH, WRAP, top = 4)
        }, MATCH, WRAP, bottom = 10)
        val last = s.index + 1 >= s.questions.size
        feedback.add(ctx.button(if (last) t("Итог", "Results", "Natija") else t("Дальше", "Next", "Keyingi")) {
            s.next()
            show()
        }, MATCH, WRAP, bottom = 12)
    }

    private fun finish() {
        mode = Mode.RESULT
        result()
    }

    private fun result() {
        val s = session ?: return home()
        stage.removeAllViews()
        val st = s.stars
        var earned = 0
        if (GrammarBank.topic(source) != null && s.done && !s.rewarded) {
            s.rewarded = true
            val prev = topicStars(source)
            if (st > prev) {
                Backend.setInt(ctx, "gr_stars_$source", st)
                earned = st - prev
                DailyStreak.addStars(ctx, earned)
            }
        }
        val stars = ctx.text(starsText(st), T.Headline, P.orange).apply { gravity = Gravity.CENTER }
        stage.add(ctx.card(pad = 18) {
            add(ctx.text(t("Готово!", "Done!", "Tayyor!"), T.Title).apply { gravity = Gravity.CENTER; textSize = 20f }, MATCH, WRAP)
            add(stars, MATCH, WRAP, top = 4)
            add(ctx.text("${s.score} / ${s.questions.size} · 🔥 ${s.bestStreak}", T.Body).apply { gravity = Gravity.CENTER }, MATCH, WRAP, top = 4)
            if (earned > 0) add(ctx.text("+$earned ⭐", T.Title, P.orange).apply { gravity = Gravity.CENTER; textSize = 18f }, MATCH, WRAP, top = 6)
        }, MATCH, WRAP, top = 8, bottom = 10)
        if (s.missed.isNotEmpty()) {
            stage.add(ctx.section(t("Повтори", "Review", "Takrorlang")))
            stage.add(ctx.card(pad = 14) {
                for ((i, m) in s.missed.withIndex()) {
                    add(ctx.text(m.filled(), T.Label), MATCH, WRAP, top = if (i == 0) 0 else 10)
                    if (m.why.isNotBlank()) add(ctx.text(m.why, T.Small), MATCH, WRAP, top = 2)
                }
            }, MATCH, WRAP, bottom = 10)
        }
        stage.add(ctx.button(t("Ещё раз", "Retry", "Qayta")) { retry() }, MATCH, WRAP, bottom = 8)
        stage.add(ctx.button(t("К темам", "Topics", "Mavzular"), BtnStyle.Outline) { home() }, MATCH, WRAP)
        if (Motion.enabled(ctx) && st > 0) {
            stars.scaleX = 0.4f; stars.scaleY = 0.4f
            stars.animate().scaleX(1f).scaleY(1f).setDuration(Motion.EMPHASIS).setInterpolator(Motion.spring).start()
        }
    }

    private fun aiRound(c: LinearLayout) {
        c.add(ctx.button(t("← Темы", "← Topics", "← Mavzular"), BtnStyle.Text) { home() }, WRAP, ctx.dp(48))
        c.add(ctx.card(pad = 16) {
            add(ctx.text(t("Раунд от ИИ", "AI round", "AI raundi"), T.Title), MATCH, WRAP)
            add(ctx.text(t("ИИ составит 10 новых заданий по теме и уровню.", "AI writes 10 new tasks for a topic and level.", "AI mavzu va daraja bo'yicha 10 ta yangi topshiriq tuzadi."), T.Small), MATCH, WRAP, top = 4)
        }, MATCH, WRAP, bottom = 10)
        when (aiState) {
            AiState.LOADING -> {
                c.add(ctx.card(pad = 18) {
                    add(android.widget.ProgressBar(ctx), MATCH, WRAP)
                    add(ctx.text(t("ИИ пишет задания…", "AI is writing tasks…", "AI topshiriqlar yozmoqda…"), T.Body).apply { gravity = Gravity.CENTER }, MATCH, WRAP, top = 8)
                }, MATCH, WRAP, bottom = 10)
                c.add(ctx.button(t("Отмена", "Cancel", "Bekor qilish"), BtnStyle.Outline) { aiRequest++; aiState = AiState.IDLE; refresh() }, MATCH, WRAP)
                return
            }
            AiState.NO_KEYS -> {
                c.add(ctx.card(pad = 14, fill = P.errorContainer, kind = CardKind.Tint) {
                    add(ctx.text(t("ИИ не настроен. Добавь ключ, чтобы играть раунды от ИИ.", "AI is not set up. Add a key to play AI rounds.", "AI sozlanmagan. AI raundlari uchun kalit qo'sh."), T.Body), MATCH, WRAP)
                    add(ctx.button(t("Настроить ИИ", "Set up AI", "AI sozlash")) { Screens.create("ai", act)?.let { act.push(it) } }, MATCH, WRAP, top = 10)
                }, MATCH, WRAP, bottom = 10)
            }
            AiState.OFFLINE -> c.add(ctx.offlineCard(t("Нет интернета. Раунд от ИИ работает онлайн.", "No internet. The AI round needs a connection.", "Internet yo'q. AI raundi internet bilan ishlaydi.")) { generate() }, MATCH, WRAP, bottom = 10)
            AiState.BAD -> {
                c.add(ctx.card(pad = 14, fill = P.errorContainer, kind = CardKind.Tint) {
                    add(ctx.text(t("ИИ прислал неподходящие задания.", "The AI reply was not usable.", "AI yaroqsiz javob yubordi."), T.Body), MATCH, WRAP)
                    add(ctx.button(t("Попробовать снова", "Try again", "Qayta urinish")) { generate() }, MATCH, WRAP, top = 10)
                    add(ctx.button(t("Играть встроенный раунд", "Play built-in round", "Ichki raundni o'ynash"), BtnStyle.Outline) {
                        GrammarBank.topic(aiTopic)?.let { start(it.id, GrammarSession.topic(it, rnd).questions) }
                    }, MATCH, WRAP, top = 8)
                }, MATCH, WRAP, bottom = 10)
            }
            AiState.IDLE -> Unit
        }
        c.add(ctx.text(t("Уровень", "Level", "Daraja"), T.Label), MATCH, WRAP, bottom = 6)
        c.add(FlowLayout(ctx, ctx.dp(8)).apply {
            for (l in GrammarAiText.levels) addView(ctx.chip(l, l == aiLevel) { aiLevel = l; refresh() })
        }, MATCH, WRAP, bottom = 12)
        c.add(ctx.text(t("Тема", "Topic", "Mavzu"), T.Label), MATCH, WRAP, bottom = 6)
        c.add(FlowLayout(ctx, ctx.dp(8)).apply {
            for (tp in GrammarBank.topics) addView(ctx.chip(topicName(tp), tp.id == aiTopic) { aiTopic = tp.id; refresh() })
        }, MATCH, WRAP, bottom = 12)
        c.add(ctx.button(t("Создать раунд", "Make a round", "Raund yaratish"), BtnStyle.Filled) { generate() }, MATCH, WRAP)
    }

    private fun generate() {
        val tp = GrammarBank.topic(aiTopic) ?: return
        val req = ++aiRequest
        aiState = AiState.LOADING
        refresh()
        val app = ctx.applicationContext
        val level = aiLevel
        Thread {
            val r: Any? = try {
                when {
                    Brain(app).keyList().isEmpty() -> AiState.NO_KEYS
                    !app.isOnline() -> AiState.OFFLINE
                    else -> GrammarAiText.parse(BrainVocabAi(app).complete(GrammarAiText.system(), GrammarAiText.user(tp.en, level)))
                }
            } catch (e: Exception) {
                null
            }
            act.runIfAlive {
                if (req != aiRequest || mode != Mode.AI) return@runIfAlive
                when (r) {
                    is GrammarAiResult -> {
                        aiQuestions = r.questions
                        start(GrammarWeak.AI_TOPIC, r.questions)
                    }
                    is AiState -> { aiState = r; refresh() }
                    else -> { aiState = AiState.BAD; refresh() }
                }
            }
        }.start()
    }
}
