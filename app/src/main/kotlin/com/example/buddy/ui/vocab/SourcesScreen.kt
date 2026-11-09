package com.example.buddy.ui.vocab

import android.app.AlertDialog
import android.content.DialogInterface
import android.text.Editable
import android.text.InputFilter
import android.text.TextWatcher
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import com.example.buddy.R
import com.example.buddy.Tr
import com.example.buddy.ui.*

class SourcesScreen(act: UiActivity, private val pick: ((source: Long?, topic: Long?) -> Unit)? = null) : Screen(act) {
    override fun title() = if (pick != null) t("Фильтр", "Filter", "Filtr") else t("Источники и темы", "Sources and topics", "Manbalar va mavzular")

    override fun onShow() { reshow() }

    override fun render(c: LinearLayout) {
        val rows = VocabDb.sources(ctx)
        c.add(ctx.section(t("Источники", "Sources", "Manbalar"), R.drawable.ic_link))
        if (rows.isEmpty()) {
            c.add(ctx.emptyState(
                t("Источников пока нет", "No sources yet", "Hozircha manbalar yo'q"),
                t("Добавьте книгу, например Cambridge IELTS 18, или другой источник.", "Add a book, for example Cambridge IELTS 18, or another source.", "Kitob, masalan Cambridge IELTS 18, yoki boshqa manba qo'shing.")
            ), MATCH, WRAP, bottom = 8)
        } else {
            val box = ctx.col {}
            for (b in SourceTree.build(rows)) branch(box, b, 0)
            c.add(box, MATCH, WRAP, bottom = 8)
        }
        c.add(ctx.button(t("Добавить книгу", "Add book", "Kitob qo'shish"), BtnStyle.Outline, R.drawable.ic_add) {
            titleDialog(t("Новая книга", "New book", "Yangi kitob"), "") { VocabDb.addSource(ctx, null, "book", it, null); refresh() }
        }, MATCH, WRAP, top = 4)
        c.add(ctx.button(t("Добавить другой источник", "Add other source", "Boshqa manba qo'shish"), BtnStyle.Text, R.drawable.ic_add) {
            titleDialog(t("Новый источник", "New source", "Yangi manba"), "") { VocabDb.addSource(ctx, null, "other", it, null); refresh() }
        }, MATCH, WRAP, top = 4)

        topics(c)
        c.add(View(ctx), MATCH, ctx.dp(24))
    }

    private fun branch(box: LinearLayout, b: SourceBranch, depth: Int) {
        box.add(nodeRow(b.node), MATCH, WRAP, bottom = 6, start = depth * 20)
        for (ch in b.children) branch(box, ch, depth + 1)
    }

    private fun skillLabel(skill: String?) = when (skill) {
        "reading" -> t("Чтение", "Reading", "O'qish")
        "listening" -> t("Аудирование", "Listening", "Tinglash")
        else -> null
    }

    private fun wordsLabel(n: Int) = t("Слов: $n", "Words: $n", "So'zlar: $n")

    private fun nodeRow(n: SourceNode): View = ctx.card(14) {
        minimumHeight = ctx.dp(48)
        add(ctx.row {
            add(ctx.text(n.title, T.Label), 0, WRAP, 1f)
            val skill = if (n.kind == "part") skillLabel(n.skill) else null
            if (skill != null) add(ctx.pill(skill, if (n.skill == "listening") P.violet else P.sky), WRAP, WRAP, start = 6)
        }, MATCH, WRAP)
        add(ctx.text(wordsLabel(n.words), T.Small), MATCH, WRAP, top = 2)
        add(ctx.row {
            when (n.kind) {
                "book" -> add(ctx.button(t("Добавить тест", "Add test", "Test qo'shish"), BtnStyle.Text, R.drawable.ic_add) {
                    titleDialog(t("Новый тест", "New test", "Yangi test"), "") { VocabDb.addSource(ctx, n.id, "test", it, null); refresh() }
                }, WRAP, WRAP)
                "test" -> add(ctx.button(t("Добавить часть", "Add part", "Qism qo'shish"), BtnStyle.Text, R.drawable.ic_add) { partDialog(n.id) }, WRAP, WRAP)
            }
            add(ctx.button(t("Переименовать", "Rename", "Nomini o'zgartirish"), BtnStyle.Text, R.drawable.ic_edit) {
                titleDialog(t("Переименовать", "Rename", "Nomini o'zgartirish"), n.title) { if (it != n.title) { VocabDb.renameSource(ctx, n.id, it); refresh() } }
            }, WRAP, WRAP)
            add(ctx.button(t("Удалить", "Delete", "O'chirish"), BtnStyle.Text, R.drawable.ic_delete) {
                ctx.confirm(
                    t("Удалить «${n.title}»?", "Delete \"${n.title}\"?", "«${n.title}» o'chirilsinmi?"),
                    t("Слова останутся в словаре. Вложенные источники тоже удалятся.", "Words stay in your vocabulary. Sources inside it are deleted too.", "So'zlar lug'atda qoladi. Ichidagi manbalar ham o'chiriladi."),
                    t("Удалить", "Delete", "O'chirish"), t("Отмена", "Cancel", "Bekor qilish")
                ) { VocabDb.deleteSource(ctx, n.id); refresh() }
            }, WRAP, WRAP)
        }, MATCH, WRAP, top = 2)
        val p = pick
        if (p != null) press { p(n.id, null); act.pop() }
    }

    private fun topics(c: LinearLayout) {
        c.add(ctx.section(t("Темы", "Topics", "Mavzular"), R.drawable.ic_tile))
        val list = VocabDb.topics(ctx)
        val lang = Tr.lang(ctx)
        if (list.isEmpty()) {
            c.add(ctx.emptyState(
                t("Тем пока нет", "No topics yet", "Hozircha mavzular yo'q"),
                t("Создайте тему, чтобы группировать слова.", "Create a topic to group your words.", "So'zlarni guruhlash uchun mavzu yarating.")
            ), MATCH, WRAP, bottom = 8)
        } else {
            val flow = FlowLayout(ctx)
            for (row in list) {
                val onTap: () -> Unit = { val p = pick; if (p != null) { p(null, row.id); act.pop() } }
                flow.addView(ctx.chip(Topics.display(row, lang) + " · " + row.words, false, onTap).apply {
                    if (row.key == null) pressLong({
                        ctx.confirm(
                            t("Удалить тему?", "Delete this topic?", "Mavzu o'chirilsinmi?"),
                            t("«${Topics.display(row, lang)}»: слова останутся в словаре.", "\"${Topics.display(row, lang)}\": words stay in your vocabulary.", "«${Topics.display(row, lang)}»: so'zlar lug'atda qoladi."),
                            t("Удалить", "Delete", "O'chirish"), t("Отмена", "Cancel", "Bekor qilish")
                        ) { VocabDb.deleteTopic(ctx, row.id); refresh() }
                    }, onTap)
                })
            }
            c.add(flow, MATCH, WRAP, bottom = 4)
            c.add(ctx.text(t("Свою тему можно удалить долгим нажатием", "Long-press your own topic to delete it", "O'z mavzuingizni o'chirish uchun uzoq bosing"), T.Small), MATCH, WRAP, bottom = 4)
        }
        c.add(ctx.button(t("Новая тема", "New topic", "Yangi mavzu"), BtnStyle.Outline, R.drawable.ic_add) {
            titleDialog(t("Новая тема", "New topic", "Yangi mavzu"), "") { VocabDb.addTopic(ctx, it); refresh() }
        }, MATCH, WRAP, top = 4)
    }

    private fun titleField(current: String): EditText =
        ctx.field(t("Название", "Title", "Nomi"), current).apply {
            setSingleLine(true)
            filters = arrayOf(InputFilter.LengthFilter(80))
        }

    private fun bindSave(dialog: AlertDialog, field: EditText) {
        val save = dialog.getButton(DialogInterface.BUTTON_POSITIVE) ?: return
        fun sync() { save.isEnabled = field.text.toString().isNotBlank() }
        field.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, st: Int, n: Int, a: Int) {}
            override fun onTextChanged(s: CharSequence?, st: Int, b: Int, n: Int) {}
            override fun afterTextChanged(s: Editable?) = sync()
        })
        sync()
    }

    private fun titleDialog(title: String, current: String, onSave: (String) -> Unit) {
        val field = titleField(current)
        val form = ctx.col(20) { add(field, MATCH, WRAP) }
        val dialog = ctx.dialogBuilder().setTitle(title).setView(form)
            .setPositiveButton(t("Сохранить", "Save", "Saqlash")) { _, _ ->
                val v = field.text.toString().trim()
                if (v.isNotEmpty()) onSave(v)
            }
            .setNegativeButton(t("Отмена", "Cancel", "Bekor qilish"), null)
            .show()
        bindSave(dialog, field)
    }

    private fun partDialog(testId: Long) {
        val field = titleField("")
        var skill = "reading"
        val chips = FlowLayout(ctx)
        fun fillChips() {
            chips.removeAllViews()
            for (k in listOf("reading", "listening")) chips.addView(ctx.chip(skillLabel(k) ?: k, k == skill) { skill = k; fillChips() })
        }
        fillChips()
        val form = ctx.col(20) {
            add(field, MATCH, WRAP, bottom = 10)
            add(chips, MATCH, WRAP)
        }
        val dialog = ctx.dialogBuilder().setTitle(t("Новая часть", "New part", "Yangi qism")).setView(form)
            .setPositiveButton(t("Сохранить", "Save", "Saqlash")) { _, _ ->
                val v = field.text.toString().trim()
                if (v.isNotEmpty()) { VocabDb.addSource(ctx, testId, "part", v, skill); refresh() }
            }
            .setNegativeButton(t("Отмена", "Cancel", "Bekor qilish"), null)
            .show()
        bindSave(dialog, field)
    }
}
