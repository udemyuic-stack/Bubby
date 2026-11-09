package com.example.buddy.ui.vocab

import android.text.Editable
import android.text.InputFilter
import android.text.TextWatcher
import android.view.Gravity
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.LinearLayout
import com.example.buddy.Tr
import com.example.buddy.ui.*

class VocabScreen(act: UiActivity) : Screen(act) {
    override fun title() = t("Словарь", "Vocabulary", "Lug'at")

    private var building = false
    private var srcPick = ""
    private var tgtPick = ""
    private var query = ""
    private var listBox: LinearLayout? = null
    private var countText: android.widget.TextView? = null
    private var input: EditText? = null
    private var resumed = false
    private val open = HashSet<Long>()
    private var activePair = 0L
    private var filterSource: Long? = null
    private var filterTopic: Long? = null

    private fun ui() = Tr.lang(ctx)

    override fun onShow() { reshow() }

    override fun render(c: LinearLayout) {
        val pairs = VocabDb.pairs(ctx)
        if (pairs.isEmpty()) building = true
        if (building && srcPick.isEmpty()) {
            srcPick = "en"
            tgtPick = Languages.defaultTarget(ui())
        }
        val active = VocabDb.activePair(ctx)
        activePair = active
        if (!resumed) { resumed = true; VocabEnrich.resumePending(ctx) }
        pairRow(c, pairs, active)
        if (building) { builder(c, pairs.isNotEmpty()); return }
        practiceCard(c)
        addCard(c, active)
        c.add(ctx.section(t("Сохранённые слова", "Saved words", "Saqlangan so'zlar"), com.example.buddy.R.drawable.ic_edit))
        countText = ctx.text("", T.Small)
        c.add(countText!!, MATCH, WRAP, bottom = 6)
        val search = ctx.field(t("Поиск", "Search", "Qidirish"), query).apply {
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, a: Int) {}
                override fun onTextChanged(s: CharSequence?, st: Int, b: Int, c: Int) {}
                override fun afterTextChanged(s: Editable?) { query = s?.toString() ?: ""; fillList(active) }
            })
        }
        c.add(search, MATCH, WRAP, bottom = 8)
        filterRow(c)
        listBox = ctx.col {}
        c.add(listBox!!, MATCH, WRAP)
        fillList(active)
    }

    private fun pairRow(c: LinearLayout, pairs: List<LangPair>, active: Long) {
        val flow = FlowLayout(ctx)
        for (p in pairs) {
            flow.addView(ctx.chip(p.label(), p.id == active && !building) {}.apply {
                pressLong({
                    val n = VocabDb.count(ctx, p.id)
                    ctx.confirm(
                        t("Удалить пару ${p.label()}?", "Delete pair ${p.label()}?", "${p.label()} juftligi o'chirilsinmi?"),
                        t("Будет удалено слов: $n", "Words that will be deleted: $n", "O'chiriladigan so'zlar: $n"),
                        t("Удалить", "Delete", "O'chirish"), t("Отмена", "Cancel", "Bekor qilish")
                    ) { VocabDb.deletePair(ctx, p.id); building = false; refresh() }
                }) { VocabDb.setActive(ctx, p.id); building = false; query = ""; filterSource = null; filterTopic = null; refresh() }
            })
        }
        flow.addView(ctx.chip("+", building) { building = true; refresh() })
        c.add(flow, MATCH, WRAP, bottom = 8)
    }

    private fun builder(c: LinearLayout, canCancel: Boolean) {
        c.add(ctx.section(t("Что и на каком языке учим", "What you study", "Nimani o'rganasiz"), com.example.buddy.R.drawable.ic_play))
        c.add(ctx.card(16) {
            add(ctx.text(t("Я учу слова на языке", "I learn words in", "Men quyidagi tildagi so'zlarni o'rganaman"), T.Label), MATCH, WRAP, bottom = 6)
            add(langChips(srcPick, tgtPick) { srcPick = it; if (tgtPick == it) tgtPick = Languages.ALL.first { l -> l.code != it }.code; refresh() }, MATCH, WRAP, bottom = 12)
            add(ctx.text(t("Перевод на язык", "Translate into", "Tarjima tili"), T.Label), MATCH, WRAP, bottom = 6)
            add(langChips(tgtPick, srcPick) { tgtPick = it; refresh() }, MATCH, WRAP, bottom = 12)
            add(ctx.button(t("Создать пару $srcPick → $tgtPick", "Create pair $srcPick → $tgtPick", "$srcPick → $tgtPick juftligini yaratish").uppercase()) {
                val id = VocabDb.addPair(ctx, srcPick, tgtPick)
                if (id == 0L) ctx.toast(t("Не получилось создать пару", "Could not create the pair", "Juftlikni yaratib bo'lmadi"))
                building = false
                refresh()
            }, MATCH, WRAP)
            if (canCancel) add(ctx.button(t("Отмена", "Cancel", "Bekor qilish"), BtnStyle.Text) { building = false; refresh() }, MATCH, WRAP, top = 4)
        }, MATCH, WRAP)
    }

    private fun langChips(selected: String, blocked: String, onPick: (String) -> Unit): FlowLayout {
        val flow = FlowLayout(ctx)
        for (l in Languages.ALL) {
            if (l.code == blocked) continue
            flow.addView(ctx.chip(l.native, l.code == selected) { onPick(l.code) })
        }
        return flow
    }

    private fun practiceCard(c: LinearLayout) {
        val holder = ctx.col {}
        c.add(holder, MATCH, WRAP, bottom = 12)
        c.add(ctx.button(t("Мой прогресс", "My progress", "Mening natijam"), BtnStyle.Outline) { act.push(ProgressScreen(act)) }, MATCH, WRAP, bottom = 8)
        c.add(ctx.button(t("Репетитор", "Tutor", "Repetitor"), BtnStyle.Outline) { act.push(TutorScreen(act)) }, MATCH, WRAP, bottom = 8)
        Thread {
            val n = try { ReviewLoader.dueCount(ctx, VocabDb.today()) } catch (_: Exception) { 0 }
            act.runOnUiThread {
                if (n > 0) holder.add(ctx.card(16) {
                    add(ctx.text(t("Пора повторить: $n", "Ready to practise: $n", "Takrorlashga tayyor: $n"), T.Label), MATCH, WRAP)
                    add(ctx.button(t("Начать повторение", "Start practice", "Takrorlashni boshlash")) { act.push(ReviewScreen(act)) }, MATCH, WRAP, top = 8)
                }, MATCH, WRAP)
            }
        }.start()
    }

    private fun addCard(c: LinearLayout, pairId: Long) {
        val pair = VocabDb.pairs(ctx).firstOrNull { it.id == pairId }
        val srcName = pair?.let { Languages.byCode(it.source)?.name(ui()) } ?: ""
        c.add(ctx.card(16) {
            val field = ctx.field(t("Новое слово или фраза ($srcName)", "New word or phrase ($srcName)", "Yangi so'z yoki ibora ($srcName)")).apply {
                imeOptions = EditorInfo.IME_ACTION_DONE
                setSingleLine(true)
            }
            input = field
            val sentence = ctx.field(t("Предложение, где вы встретили слово (необязательно)", "Sentence where you met it (optional)", "So'zni uchratgan gapingiz (ixtiyoriy)"), multiline = true).apply {
                filters = arrayOf(InputFilter.LengthFilter(300))
            }
            fun save(force: Boolean) {
                val text = field.text.toString()
                if (!force && text.isNotBlank() && pair != null && !Scripts.fits(text, pair.source)) {
                    val guess = Scripts.candidates(text).mapNotNull { Languages.byCode(it)?.name(ui()) }.joinToString(" / ")
                    ctx.confirm(
                        t("Похоже на другой язык", "This looks like another language", "Bu boshqa tilga o'xshaydi"),
                        t("Текст похож на: $guess. Пара: ${pair.label()}. Сохранить как есть?", "The text looks like: $guess. Pair: ${pair.label()}. Save it anyway?", "Matn: $guess. Juftlik: ${pair.label()}. Baribir saqlansinmi?"),
                        t("Сохранить", "Save", "Saqlash"), t("Отмена", "Cancel", "Bekor qilish")
                    ) { save(true) }
                    return
                }
                val said = sentence.text.toString().trim().ifBlank { null }
                val src = currentSource()
                when (val it = VocabDb.addEntry(ctx, pairId, text, said, src)) {
                    is AddResult.Added -> {
                        field.setText("")
                        sentence.setText("")
                        ctx.toast(t("Сохранено", "Saved", "Saqlandi"))
                        fillList(pairId)
                        enrich(it.id)
                    }
                    is AddResult.Duplicate -> if (src != null) {
                        ctx.confirm(
                            t("Уже сохранено", "Already saved", "Allaqachon saqlangan"),
                            t("Добавить этот источник к сохранённому слову?", "Add this source to the existing word?", "Bu manba saqlangan so'zga qo'shilsinmi?"),
                            t("Добавить", "Add", "Qo'shish"), t("Отмена", "Cancel", "Bekor qilish")
                        ) {
                            VocabDb.linkSource(ctx, it.id, src)
                            ctx.toast(t("Источник добавлен", "Source added", "Manba qo'shildi"))
                            fillList(pairId)
                        }
                    } else ctx.toast(t("Это слово уже есть", "Already saved", "Bu so'z allaqachon bor"))
                    is AddResult.TooLong -> ctx.toast(t("Слишком длинно: до 8 слов", "Too long: up to 8 words", "Juda uzun: 8 so'zgacha"))
                    is AddResult.Empty -> ctx.toast(t("Введите слово", "Type a word", "So'z kiriting"))
                    is AddResult.NoPair -> { building = true; refresh() }
                }
            }
            field.setOnEditorActionListener { _, id, _ -> if (id == EditorInfo.IME_ACTION_DONE) { save(false); true } else false }
            add(field, MATCH, WRAP, bottom = 10)
            add(sentence, MATCH, WRAP, bottom = 10)
            val srcBox = ctx.col {}
            fun showSource() {
                srcBox.removeAllViews()
                val cur = currentSource()
                val label = if (cur == null) t("Без источника", "No source", "Manbasiz")
                else SourceTree.path(VocabDb.sources(ctx), cur).let { p -> t("Источник: $p", "Source: $p", "Manba: $p") }
                srcBox.add(ctx.row {
                    add(ctx.pill(label, P.violet) { pickSource(true) { id -> VocabDb.setLastSource(ctx, id); showSource() } }, WRAP, WRAP)
                }, MATCH, WRAP)
            }
            showSource()
            add(srcBox, MATCH, WRAP, bottom = 10)
            add(ctx.button(t("Сохранить", "Save", "Saqlash")) { save(false) }, MATCH, WRAP)
            add(ctx.button(t("Слова с фото", "Words from a photo", "Rasmdan so'zlar"), BtnStyle.Outline) { act.push(PhotoScreen(act, pairId)) }, MATCH, WRAP, top = 8)
        }, MATCH, WRAP, bottom = 12)
    }

    private fun currentSource(): Long? {
        val id = VocabDb.lastSource(ctx) ?: return null
        return id.takeIf { s -> VocabDb.sources(ctx).any { it.id == s } }
    }

    private fun pickSource(withNone: Boolean, onPick: (Long?) -> Unit) {
        val labels = ArrayList<String>()
        val ids = ArrayList<Long?>()
        if (withNone) { labels.add(t("Без источника", "No source", "Manbasiz")); ids.add(null) }
        fun walk(list: List<SourceBranch>, depth: Int) {
            for (b in list) {
                labels.add(" ".repeat(depth) + b.node.title)
                ids.add(b.node.id)
                walk(b.children, depth + 1)
            }
        }
        walk(SourceTree.build(VocabDb.sources(ctx)), 0)
        val manage = labels.size
        labels.add(t("Управлять источниками", "Manage sources", "Manbalarni boshqarish"))
        ctx.dialogBuilder().setTitle(t("Источник", "Source", "Manba"))
            .setItems(labels.toTypedArray()) { _, i -> if (i == manage) act.push(SourcesScreen(act)) else onPick(ids[i]) }
            .setNegativeButton(t("Отмена", "Cancel", "Bekor qilish"), null)
            .show()
    }

    private fun filterRow(c: LinearLayout) {
        val rows = VocabDb.sources(ctx)
        if (filterSource != null && rows.none { it.id == filterSource }) filterSource = null
        val topic = filterTopic?.let { id -> VocabDb.topics(ctx).firstOrNull { it.id == id } }
        if (topic == null) filterTopic = null
        val flow = FlowLayout(ctx)
        flow.addView(ctx.chip(t("Все", "All", "Hammasi"), filterSource == null && filterTopic == null) { filterSource = null; filterTopic = null; refresh() })
        filterSource?.let { s -> flow.addView(ctx.chip(SourceTree.path(rows, s) + "  ×", true) { filterSource = null; refresh() }) }
        if (topic != null) flow.addView(ctx.chip(Topics.display(topic, ui()) + "  ×", true) { filterTopic = null; refresh() })
        flow.addView(ctx.chip(t("Фильтр", "Filter", "Filtr"), false) {
            act.push(SourcesScreen(act) { s, tp -> filterSource = s ?: filterSource; filterTopic = tp ?: filterTopic })
        })
        c.add(flow, MATCH, WRAP, bottom = 8)
    }

    private fun fillList(pairId: Long) {
        val box = listBox ?: return
        box.removeAllViews()
        val total = VocabDb.count(ctx, pairId)
        val rows = VocabDb.entries(ctx, pairId, query, source = filterSource, topic = filterTopic)
        countText?.text = t("Всего: $total", "Total: $total", "Jami: $total")
        if (total == 0) {
            box.add(ctx.emptyState(t("Пока пусто", "Nothing here yet", "Hozircha bo'sh"), t("Введите слово выше: остальное появится позже.", "Type a word above: the rest comes later.", "Yuqoriga so'z kiriting: qolganini keyinroq qo'shamiz.")), MATCH, WRAP)
            return
        }
        if (rows.isEmpty()) {
            box.add(ctx.text(t("Ничего не найдено", "No matches", "Hech narsa topilmadi"), T.Small), MATCH, WRAP, top = 8)
            return
        }
        for (e in rows) box.add(entryCard(e, pairId), MATCH, WRAP, bottom = 6)
    }

    private fun enrich(id: Long, force: Boolean = false, language: String? = null) {
        VocabEnrich.start(ctx, id, force, language) { out ->
            act.runOnUiThread {
                if (out == EnrichOutcome.UNAVAILABLE) ctx.toast(t("Нет связи с ИИ: слово сохранено, дополню позже", "AI is not reachable: the word is saved, I will fill it in later", "AI bilan aloqa yo'q: so'z saqlandi, keyinroq to'ldiraman"))
                if (listBox?.isAttachedToWindow == true && activePair != 0L) fillList(activePair)
            }
        }
        if (listBox?.isAttachedToWindow == true && activePair != 0L) listBox?.post { fillList(activePair) }
    }

    private fun statusPill(e: Entry): android.view.View = when (e.status) {
        EntryStatus.READY -> ctx.pill(t("Готово", "Ready", "Tayyor"), P.success)
        EntryStatus.ENRICHING -> ctx.pill(t("Дополняю…", "Enriching…", "To'ldirilmoqda…"), P.sky)
        EntryStatus.FAILED -> ctx.pill(t("Ошибка", "Failed", "Xato"), P.rose)
        EntryStatus.NEEDS_LANGUAGE -> ctx.pill(t("Какой язык?", "Which language?", "Qaysi til?"), P.orange)
        EntryStatus.SAVED -> ctx.pill(t("Сохранено", "Saved", "Saqlangan"), P.teal)
    }

    private fun retryLine(card: LinearLayout, e: Entry): Boolean {
        val text = when {
            RetryPolicy.gaveUp(e.status, e.tries) -> t("Больше не пробую сам. Нажмите «Повторить».", "Stopped trying automatically. Tap Retry.", "Endi o'zim urinmayman. «Qayta urinish»ni bosing.")
            e.nextTry > System.currentTimeMillis() -> {
                val at = android.text.format.DateFormat.getTimeFormat(ctx).format(java.util.Date(e.nextTry))
                t("Попробую снова в $at", "Will try again at $at", "$at da yana urinib ko'raman")
            }
            else -> return false
        }
        card.add(ctx.text(text, T.Small), MATCH, WRAP, top = 4)
        return true
    }

    private fun entryCard(e: Entry, pairId: Long): LinearLayout = ctx.card(14) {
        minimumHeight = ctx.dp(48)
        add(ctx.row {
            add(ctx.text(e.original, T.Label), 0, WRAP, 1f)
            add(statusPill(e), WRAP, WRAP)
        })
        when (e.status) {
            EntryStatus.FAILED -> {
                add(ctx.text(t("Не получилось дополнить слово. Само слово сохранено.", "Could not fill in this word. The word itself is saved.", "So'zni to'ldirib bo'lmadi. So'zning o'zi saqlangan."), T.Small), MATCH, WRAP, top = 4)
                retryLine(this, e)
                add(ctx.button(t("Повторить", "Retry", "Qayta urinish"), BtnStyle.Text) { enrich(e.id, true) }, WRAP, WRAP, top = 4)
            }
            EntryStatus.NEEDS_LANGUAGE -> {
                add(ctx.text(t("Не уверен, на каком это языке. Выберите:", "Not sure which language this is. Pick one:", "Bu qaysi tilda ekaniga ishonchim komil emas. Tanlang:"), T.Small), MATCH, WRAP, top = 4)
                val pair = VocabDb.pair(ctx, pairId)
                val flow = FlowLayout(ctx)
                for (l in Languages.ALL) {
                    if (pair != null && l.code == pair.target) continue
                    flow.addView(ctx.chip(l.native, l.code == e.detected) { enrich(e.id, true, l.code) })
                }
                add(flow, MATCH, WRAP, top = 4)
            }
            EntryStatus.SAVED -> if (e.error != null) {
                if (!retryLine(this, e)) add(ctx.text(t("Дополню, когда появится связь", "Will be filled in when online", "Aloqa paydo bo'lganda to'ldiriladi"), T.Small), MATCH, WRAP, top = 4)
                if (RetryPolicy.gaveUp(e.status, e.tries)) add(ctx.button(t("Повторить", "Retry", "Qayta urinish"), BtnStyle.Text) { enrich(e.id, true) }, WRAP, WRAP, top = 4)
            }
            else -> {}
        }
        val d = VocabDb.detail(ctx, e.id)
        val hasSenses = d != null && d.senses.isNotEmpty()
        if (d != null && hasSenses) {
            val line = d.senses.flatMap { it.data.translations.take(1) }.take(3).joinToString(" · ")
            if (line.isNotEmpty()) add(ctx.text(line, T.Small), MATCH, WRAP, top = 2)
            if (e.id in open) detailBlock(this, d)
        } else if (e.status != EntryStatus.ENRICHING) {
            add(ctx.button(t("Добавить значение самому", "Add a meaning yourself", "Ma'noni o'zingiz qo'shing"), BtnStyle.Text, com.example.buddy.R.drawable.ic_add) { act.push(WordEditScreen(act, e.id, null)) }, WRAP, WRAP, top = 4)
        }
        if (hasSenses) press { if (!open.add(e.id)) open.remove(e.id); fillList(pairId) }
        pressLong({
            ctx.confirm(
                t("Удалить слово?", "Delete this word?", "So'z o'chirilsinmi?"), e.original,
                t("Удалить", "Delete", "O'chirish"), t("Отмена", "Cancel", "Bekor qilish")
            ) { VocabDb.deleteEntry(ctx, e.id); open.remove(e.id); fillList(pairId) }
        }) { if (hasSenses) { if (!open.add(e.id)) open.remove(e.id); fillList(pairId) } }
    }

    private var sayVoice: com.example.buddy.Voice? = null
    private var sayWaiting: Long? = null
    private var sayArmed: Long? = null
    private var sayActive = false

    override fun onPermissionResult() {
        val id = sayWaiting ?: return
        sayWaiting = null
        if (ctx.checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            ctx.toast(t("Без микрофона не получится проверить произношение", "Microphone access is needed to check how you say it", "Talaffuzni tekshirish uchun mikrofonga ruxsat kerak"))
            return
        }
        sayArmed = id
        android.os.Handler(android.os.Looper.getMainLooper()).post { if (sayArmed != null && activePair != 0L) fillList(activePair) }
    }

    private fun stopPron() {
        Pronounce.stop()
        if (sayActive) {
            sayActive = false
            sayVoice?.stopListening()
        }
    }

    private fun pronBlock(box: LinearLayout, d: EntryDetail) {
        val word = d.lemma.ifBlank { d.entry.original }
        val p = d.pron
        box.add(ctx.section(t("Произношение", "Pronunciation", "Talaffuz")), MATCH, WRAP)
        val tts = Pronounce.available(ctx)
        for ((accent, ipa) in listOf(Accent.UK to (p?.uk ?: d.ipa), Accent.US to (p?.us ?: ""))) {
            box.add(ctx.row {
                add(ctx.text(accent.name + (if (ipa.isBlank()) "" else "  /$ipa/"), T.Label), 0, WRAP, 1f)
                if (tts) add(ctx.button(t("Слушать", "Play", "Tinglash"), BtnStyle.Tonal, com.example.buddy.R.drawable.ic_play) { Pronounce.speak(ctx, word, accent) }, WRAP, WRAP)
            }, MATCH, WRAP, top = 4)
        }
        if (!tts) box.add(ctx.text(t("На этом телефоне нет движка синтеза речи", "No speech engine on this phone", "Bu telefonda nutq sintezi dasturi yo'q"), T.Small), MATCH, WRAP, top = 4)
        if (p != null && p.syllables.isNotEmpty()) {
            val sb = android.text.SpannableStringBuilder()
            p.syllables.forEachIndexed { i, s ->
                if (i > 0) sb.append("·")
                if (i == p.stress) {
                    val at = sb.length
                    sb.append(s.uppercase())
                    sb.setSpan(android.text.style.ForegroundColorSpan(P.primary), at, sb.length, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                    sb.setSpan(android.text.style.StyleSpan(android.graphics.Typeface.BOLD), at, sb.length, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                } else sb.append(s)
            }
            box.add(ctx.text(sb, T.Body), MATCH, WRAP, top = 6)
        }
        if (p != null && p.mistakes.isNotEmpty()) {
            box.add(ctx.text(t("Частые ошибки", "Common mistakes", "Ko'p uchraydigan xatolar"), T.Label), MATCH, WRAP, top = 8)
            for (m in p.mistakes) box.add(ctx.text("• $m", T.Small), MATCH, WRAP, top = 2)
        }
        val say = ctx.col {}
        box.add(say, MATCH, WRAP, top = 6)
        sayRow(say, word, d.entry.id, false, null, 0)
        if (sayArmed == d.entry.id) {
            sayArmed = null
            say.post { if (say.isAttachedToWindow) startSay(say, word, d.entry.id) }
        }
    }

    private fun sayRow(box: LinearLayout, word: String, id: Long, listening: Boolean, result: String?, color: Int) {
        box.removeAllViews()
        val label = if (listening) t("Слушаю…", "Listening...", "Tinglayapman…") else t("Скажите сами", "Say it", "O'zingiz ayting")
        box.add(ctx.button(label, BtnStyle.Tonal, com.example.buddy.R.drawable.ic_mic) {
            if (listening) {
                sayActive = false
                sayVoice?.stopListening()
                sayRow(box, word, id, false, null, 0)
            } else startSay(box, word, id)
        }, WRAP, WRAP)
        if (result != null) box.add(ctx.text(result, T.Small, color), MATCH, WRAP, top = 4)
    }

    private fun startSay(box: LinearLayout, word: String, id: Long) {
        if (ctx.checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            sayWaiting = id
            Backend.requestPermissions(act, "mic")
            return
        }
        Pronounce.stop()
        val v = sayVoice ?: com.example.buddy.Voice(ctx, android.os.Handler(android.os.Looper.getMainLooper())).also { sayVoice = it }
        sayActive = true
        sayRow(box, word, id, true, null, 0)
        v.listen("en-GB", {}) { text, err ->
            if (!sayActive || !box.isAttachedToWindow) return@listen
            sayActive = false
            val heard = text?.trim().orEmpty()
            when {
                heard.isNotEmpty() -> {
                    val ok = PronMatch.matches(word, (listOf(heard) + v.lastAlternatives).joinToString("|"))
                    if (ok) sayRow(box, word, id, false, t("Распознано: $heard", "Recognised: $heard", "Tanildi: $heard"), P.success)
                    else sayRow(box, word, id, false, t("Не распознано. Услышано: $heard", "Not recognised. Heard: $heard", "Tanilmadi. Eshitildi: $heard"), P.error)
                }
                err == null -> sayRow(box, word, id, false, t("Ничего не услышал. Попробуйте ещё раз", "Didn't catch anything. Try again", "Hech narsa eshitilmadi. Yana urinib ko'ring"), 0)
                else -> sayRow(box, word, id, false, t("Распознавание речи недоступно", "Speech recognition is unavailable", "Nutqni tanish mavjud emas"), P.error)
            }
        }
    }

    private fun detailBlock(box: LinearLayout, d: EntryDetail) {
        stopPron()
        val en = (d.entry.detected ?: VocabDb.pair(ctx, d.entry.pair)?.source) == "en"
        val head = listOf(d.lemma.takeIf { it.isNotBlank() && VocabText.normalize(it) != VocabText.normalize(d.entry.original) }, d.ipa.takeIf { it.isNotBlank() && !en }?.let { "/$it/" }, d.reading.takeIf { it.isNotBlank() }).filterNotNull().joinToString("  ")
        if (head.isNotEmpty()) box.add(ctx.text(head, T.Label), MATCH, WRAP, top = 8)
        if (en) pronBlock(box, d)
        val said = d.context?.takeIf { it.isNotBlank() }
        if (said != null) {
            box.add(ctx.text("“$said”", T.Small), MATCH, WRAP, top = 6)
            d.contextTr?.takeIf { it.isNotBlank() }?.let { box.add(ctx.text("   $it", T.Small), MATCH, WRAP) }
            box.add(ctx.button(t("Изменить предложение", "Change sentence", "Gapni o'zgartirish"), BtnStyle.Text) { sentenceDialog(d.entry.id, said) }, WRAP, WRAP, top = 4)
        } else {
            box.add(ctx.button(t("Добавить предложение", "Add a sentence", "Gap qo'shish"), BtnStyle.Text, com.example.buddy.R.drawable.ic_add) { sentenceDialog(d.entry.id, "") }, WRAP, WRAP, top = 4)
        }
        sourcesBlock(box, d.entry.id)
        topicsBlock(box, d.entry.id)
        for (s in d.senses) {
            val picked = s.picked
            box.add(ctx.col {
                setPadding(ctx.dp(10), ctx.dp(8), ctx.dp(10), ctx.dp(8))
                background = ctx.rounded(P.alpha(P.sky, if (picked) 90 else 36), 14f)
                minimumHeight = ctx.dp(48)
                val tag = listOf(s.data.pos, s.data.level, s.data.formality).filter { it.isNotBlank() }.joinToString(" · ")
                val mark = when {
                    s.verified -> ctx.pill(t("Проверено", "Verified", "Tasdiqlangan"), P.success)
                    s.origin == "edited" -> ctx.pill(t("Изменено", "Edited", "Tahrirlangan"), P.teal)
                    s.origin == "user" -> ctx.pill(t("Ваше", "Yours", "Sizniki"), P.teal)
                    else -> null
                }
                add(ctx.row {
                    add(ctx.text(tag, T.Small), 0, WRAP, 1f)
                    if (s.kind == "context") add(ctx.pill(t("В вашем предложении", "In your sentence", "Gapingizda"), P.violet), WRAP, WRAP, end = 4)
                    if (mark != null) add(mark, WRAP, WRAP, end = 4)
                    add(ctx.button(t("Изменить", "Edit", "Tahrirlash"), BtnStyle.Text, com.example.buddy.R.drawable.ic_edit) { act.push(WordEditScreen(act, d.entry.id, s.id)) }, WRAP, WRAP)
                }, MATCH, WRAP)
                add(ctx.text(s.data.translations.joinToString(", "), T.Label), MATCH, WRAP, top = 2)
                add(ctx.text(s.data.definition, T.Small), MATCH, WRAP, top = 2)
                if (s.data.usage.isNotBlank()) add(ctx.text(s.data.usage, T.Small), MATCH, WRAP, top = 2)
                for (x in s.data.examples) {
                    add(ctx.text("• " + x.text + (if (x.reading.isNotBlank()) "  (" + x.reading + ")" else ""), T.Small), MATCH, WRAP, top = 4)
                    if (x.translation.isNotBlank()) add(ctx.text("   " + x.translation, T.Small), MATCH, WRAP)
                }
                if (d.senses.size > 1) press {
                    VocabDb.pickSense(ctx, d.entry.id, if (picked) null else s.id)
                    fillList(activePair)
                }
            }, MATCH, WRAP, top = 6)
        }
        box.add(ctx.button(t("Добавить значение", "Add meaning", "Ma'no qo'shish"), BtnStyle.Text, com.example.buddy.R.drawable.ic_add) { act.push(WordEditScreen(act, d.entry.id, null)) }, WRAP, WRAP, top = 4)
        if (d.senses.size > 1) box.add(ctx.text(t("Нажмите на значение, чтобы учить только его", "Tap a meaning to study only that one", "Faqat shuni o'rganish uchun ma'noni bosing"), T.Small), MATCH, WRAP, top = 4)
        if (d.meta.isNotEmpty()) box.add(ctx.text(d.meta.entries.joinToString("\n") { it.key.replace('_', ' ') + ": " + it.value }, T.Small), MATCH, WRAP, top = 8)
        val rel = d.relations.entries.joinToString("\n") { it.key + ": " + it.value.joinToString(", ") }
        if (rel.isNotEmpty()) box.add(ctx.text(rel, T.Small), MATCH, WRAP, top = 6)
        box.add(ctx.button(t("Обновить через ИИ", "Regenerate with AI", "AI bilan yangilash"), BtnStyle.Text) {
            ctx.confirm(
                t("Обновить карточку?", "Regenerate this card?", "Kartochka yangilansinmi?"), t("Ваши правки и проверенные значения сохранятся. Значения, которые вы уже повторяли, сохранят историю.", "Your edits and verified meanings are kept. Meanings you have practised keep their history.", "Tuzatishlaringiz va tasdiqlangan ma'nolar saqlanadi. Mashq qilgan ma'nolaringiz tarixi saqlanadi."),
                t("Обновить", "Regenerate", "Yangilash"), t("Отмена", "Cancel", "Bekor qilish")
            ) { enrich(d.entry.id, true) }
        }, WRAP, WRAP, top = 6)
    }

    private fun sourcesBlock(box: LinearLayout, id: Long) {
        val rows = VocabDb.sources(ctx)
        for (sid in VocabDb.entrySources(ctx, id)) {
            box.add(ctx.row {
                add(ctx.text(SourceTree.path(rows, sid), T.Small), 0, WRAP, 1f)
                add(ctx.button(t("Убрать", "Remove", "Olib tashlash"), BtnStyle.Text) { VocabDb.unlinkSource(ctx, id, sid); fillList(activePair) }, WRAP, WRAP)
            }, MATCH, WRAP, top = 4)
        }
        box.add(ctx.button(t("Добавить источник", "Add source", "Manba qo'shish"), BtnStyle.Text, com.example.buddy.R.drawable.ic_add) {
            pickSource(false) { sid -> if (sid != null) { VocabDb.linkSource(ctx, id, sid); fillList(activePair) } }
        }, WRAP, WRAP, top = 4)
    }

    private fun topicsBlock(box: LinearLayout, id: Long) {
        val mine = VocabDb.entryTopics(ctx, id).toHashSet()
        val tagged = VocabDb.topics(ctx).filter { it.id in mine }
        if (tagged.isNotEmpty()) {
            val flow = FlowLayout(ctx)
            for (tp in tagged) flow.addView(ctx.chip(Topics.display(tp, ui()), true) {})
            box.add(flow, MATCH, WRAP, top = 4)
        }
        box.add(ctx.button(t("Темы", "Topics", "Mavzular"), BtnStyle.Text, com.example.buddy.R.drawable.ic_edit) { topicsDialog(id, HashSet(mine)) }, WRAP, WRAP, top = 4)
    }

    private fun topicsDialog(id: Long, chosen: MutableSet<Long>) {
        val rows = VocabDb.topics(ctx)
        val labels = rows.map { Topics.display(it, ui()) }.toTypedArray()
        val checked = BooleanArray(rows.size) { rows[it].id in chosen }
        ctx.dialogBuilder().setTitle(t("Темы", "Topics", "Mavzular"))
            .setMultiChoiceItems(labels, checked) { _, i, on -> if (on) chosen.add(rows[i].id) else chosen.remove(rows[i].id) }
            .setPositiveButton(t("Сохранить", "Save", "Saqlash")) { _, _ ->
                VocabDb.setTopics(ctx, id, chosen)
                fillList(activePair)
            }
            .setNeutralButton(t("Новая тема", "New topic", "Yangi mavzu")) { _, _ -> newTopicDialog(id, chosen) }
            .setNegativeButton(t("Отмена", "Cancel", "Bekor qilish"), null)
            .show()
    }

    private fun newTopicDialog(id: Long, chosen: MutableSet<Long>) {
        val field = ctx.field(t("Название темы", "Topic name", "Mavzu nomi")).apply {
            setSingleLine(true)
            filters = arrayOf(InputFilter.LengthFilter(60))
        }
        val form = ctx.col(20) { add(field, MATCH, WRAP) }
        ctx.dialogBuilder().setTitle(t("Новая тема", "New topic", "Yangi mavzu")).setView(form)
            .setPositiveButton(t("Добавить", "Add", "Qo'shish")) { _, _ ->
                val name = field.text.toString().trim()
                if (name.isEmpty()) return@setPositiveButton
                val tid = VocabDb.addTopic(ctx, name)
                if (tid <= 0L) { ctx.toast(t("Не получилось добавить тему", "Could not add the topic", "Mavzuni qo'shib bo'lmadi")); return@setPositiveButton }
                chosen.add(tid)
                VocabDb.setTopics(ctx, id, chosen)
                fillList(activePair)
            }
            .setNegativeButton(t("Отмена", "Cancel", "Bekor qilish"), null)
            .show()
    }

    private fun sentenceDialog(id: Long, current: String) {
        val field = ctx.field(t("Предложение, где вы встретили слово", "Sentence where you met it", "So'zni uchratgan gapingiz"), value = current, multiline = true).apply {
            filters = arrayOf(InputFilter.LengthFilter(300))
        }
        val form = ctx.col(20) { add(field, MATCH, WRAP) }
        ctx.dialogBuilder().setTitle(t("Предложение", "Sentence", "Gap")).setView(form)
            .setPositiveButton(t("Сохранить", "Save", "Saqlash")) { _, _ ->
                if (field.text.toString().trim() == current.trim()) return@setPositiveButton
                VocabDb.setContext(ctx, id, field.text.toString())
                enrich(id, true)
                fillList(activePair)
            }
            .setNegativeButton(t("Отмена", "Cancel", "Bekor qilish"), null)
            .show()
    }
}
