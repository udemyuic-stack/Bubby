package com.example.buddy.ui.vocab

import android.text.Editable
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

    private fun ui() = Tr.lang(ctx)

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
                }) { VocabDb.setActive(ctx, p.id); building = false; query = ""; refresh() }
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
                when (val it = VocabDb.addEntry(ctx, pairId, text)) {
                    is AddResult.Added -> {
                        field.setText("")
                        ctx.toast(t("Сохранено", "Saved", "Saqlandi"))
                        fillList(pairId)
                        enrich(it.id)
                    }
                    is AddResult.Duplicate -> ctx.toast(t("Это слово уже есть", "Already saved", "Bu so'z allaqachon bor"))
                    is AddResult.TooLong -> ctx.toast(t("Слишком длинно: до 8 слов", "Too long: up to 8 words", "Juda uzun: 8 so'zgacha"))
                    is AddResult.Empty -> ctx.toast(t("Введите слово", "Type a word", "So'z kiriting"))
                    is AddResult.NoPair -> { building = true; refresh() }
                }
            }
            field.setOnEditorActionListener { _, id, _ -> if (id == EditorInfo.IME_ACTION_DONE) { save(false); true } else false }
            add(field, MATCH, WRAP, bottom = 10)
            add(ctx.button(t("Сохранить", "Save", "Saqlash")) { save(false) }, MATCH, WRAP)
        }, MATCH, WRAP, bottom = 12)
    }

    private fun fillList(pairId: Long) {
        val box = listBox ?: return
        box.removeAllViews()
        val total = VocabDb.count(ctx, pairId)
        val rows = VocabDb.entries(ctx, pairId, query)
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

    private fun entryCard(e: Entry, pairId: Long): LinearLayout = ctx.card(14) {
        minimumHeight = ctx.dp(48)
        add(ctx.row {
            add(ctx.text(e.original, T.Label), 0, WRAP, 1f)
            add(statusPill(e), WRAP, WRAP)
        })
        when (e.status) {
            EntryStatus.FAILED -> {
                add(ctx.text(t("Не получилось дополнить слово. Само слово сохранено.", "Could not fill in this word. The word itself is saved.", "So'zni to'ldirib bo'lmadi. So'zning o'zi saqlangan."), T.Small), MATCH, WRAP, top = 4)
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
            EntryStatus.SAVED -> if (e.error != null) add(ctx.text(t("Дополню, когда появится связь", "Will be filled in when online", "Aloqa paydo bo'lganda to'ldiriladi"), T.Small), MATCH, WRAP, top = 4)
            EntryStatus.READY -> {
                val d = VocabDb.detail(ctx, e.id)
                if (d != null) {
                    val line = d.senses.flatMap { it.data.translations.take(1) }.take(3).joinToString(" · ")
                    if (line.isNotEmpty()) add(ctx.text(line, T.Small), MATCH, WRAP, top = 2)
                    if (e.id in open) detailBlock(this, d)
                }
            }
            else -> {}
        }
        if (e.status == EntryStatus.READY) press { if (!open.add(e.id)) open.remove(e.id); fillList(pairId) }
        pressLong({
            ctx.confirm(
                t("Удалить слово?", "Delete this word?", "So'z o'chirilsinmi?"), e.original,
                t("Удалить", "Delete", "O'chirish"), t("Отмена", "Cancel", "Bekor qilish")
            ) { VocabDb.deleteEntry(ctx, e.id); open.remove(e.id); fillList(pairId) }
        }) { if (e.status == EntryStatus.READY) { if (!open.add(e.id)) open.remove(e.id); fillList(pairId) } }
    }

    private fun detailBlock(box: LinearLayout, d: EntryDetail) {
        val head = listOf(d.lemma.takeIf { it.isNotBlank() && VocabText.normalize(it) != VocabText.normalize(d.entry.original) }, d.ipa.takeIf { it.isNotBlank() }?.let { "/$it/" }, d.reading.takeIf { it.isNotBlank() }).filterNotNull().joinToString("  ")
        if (head.isNotEmpty()) box.add(ctx.text(head, T.Label), MATCH, WRAP, top = 8)
        for (s in d.senses) {
            val picked = s.picked
            box.add(ctx.col {
                setPadding(ctx.dp(10), ctx.dp(8), ctx.dp(10), ctx.dp(8))
                background = ctx.rounded(P.alpha(P.sky, if (picked) 90 else 36), 14f)
                minimumHeight = ctx.dp(48)
                val tag = listOf(s.data.pos, s.data.level, s.data.formality).filter { it.isNotBlank() }.joinToString(" · ")
                if (tag.isNotEmpty()) add(ctx.text(tag, T.Small), MATCH, WRAP)
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
        if (d.senses.size > 1) box.add(ctx.text(t("Нажмите на значение, чтобы учить только его", "Tap a meaning to study only that one", "Faqat shuni o'rganish uchun ma'noni bosing"), T.Small), MATCH, WRAP, top = 4)
        if (d.meta.isNotEmpty()) box.add(ctx.text(d.meta.entries.joinToString("\n") { it.key.replace('_', ' ') + ": " + it.value }, T.Small), MATCH, WRAP, top = 8)
        val rel = d.relations.entries.joinToString("\n") { it.key + ": " + it.value.joinToString(", ") }
        if (rel.isNotEmpty()) box.add(ctx.text(rel, T.Small), MATCH, WRAP, top = 6)
        box.add(ctx.button(t("Обновить через ИИ", "Regenerate with AI", "AI bilan yangilash"), BtnStyle.Text) {
            ctx.confirm(
                t("Обновить карточку?", "Regenerate this card?", "Kartochka yangilansinmi?"), t("Текущие значения будут заменены.", "The current meanings will be replaced.", "Hozirgi ma'nolar almashtiriladi."),
                t("Обновить", "Regenerate", "Yangilash"), t("Отмена", "Cancel", "Bekor qilish")
            ) { enrich(d.entry.id, true) }
        }, WRAP, WRAP, top = 6)
    }
}
