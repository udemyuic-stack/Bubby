package com.example.buddy.ui.vocab

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.app.AlertDialog
import android.content.DialogInterface
import android.text.Editable
import android.text.InputFilter
import android.text.TextUtils
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupMenu
import com.example.buddy.R
import com.example.buddy.Tr
import com.example.buddy.ui.*

class SourcesScreen(
    act: UiActivity,
    private val sourceOnly: Boolean = false,
    private val allowNone: Boolean = false,
    private val selected: Long? = null,
    private val folder: Long? = null,
    private val depth: Int = 0,
    private val pick: ((source: Long?, topic: Long?) -> Unit)? = null
) : Screen(act) {
    companion object {
        private val expanded = HashSet<Long>()
        private const val INDENT_DP = 20
        private const val ARROW_DP = 36
        private const val CHEVRON_OPEN = 90f
    }

    private var pair = 0L

    override fun title(): String {
        val id = folder
        if (id != null) VocabDb.sources(ctx).firstOrNull { it.id == id }?.let { return it.title }
        return when {
            sourceOnly -> t("Выберите папку", "Choose folder", "Papkani tanlang")
            pick != null -> t("Фильтр", "Filter", "Filtr")
            else -> t("Папки и темы", "Folders and topics", "Papkalar va mavzular")
        }
    }

    override fun onShow() { reshow() }

    override fun render(c: LinearLayout) {
        val rows = VocabDb.sources(ctx)
        val byId = rows.associateBy { it.id }
        val id = folder
        if (id != null && id !in byId) {
            c.add(ctx.emptyState(t("Папка удалена", "This folder was deleted", "Bu papka o'chirilgan"), t("Вернитесь назад.", "Go back.", "Orqaga qayting.")), MATCH, WRAP)
            return
        }
        pair = VocabDb.activePair(ctx)
        revealSelected(byId)
        pathBar(c, byId)
        if (pick != null && id != null) {
            c.add(ctx.button(t("Выбрать эту папку", "Choose this folder", "Shu papkani tanlash")) { choose(id) }, MATCH, WRAP, bottom = 8)
        }
        val tree = SourceTree.build(rows)
        val level = if (id == null) tree else find(tree, id)?.children ?: emptyList()
        val words = wordsIn(id)
        val none = sourceOnly && allowNone && id == null
        if (level.isEmpty() && words.isEmpty() && !none) {
            c.add(ctx.emptyState(
                t("Папка пуста", "This folder is empty", "Papka bo'sh"),
                t("Создайте папку или добавьте слово сюда.", "Create a folder or add a word here.", "Papka yarating yoki shu yerga so'z qo'shing.")
            ), MATCH, WRAP, bottom = 8)
        } else {
            c.add(ctx.card(6) {
                if (none) add(noneRow(), MATCH, WRAP)
                for (b in level) branch(this, b, 0)
                for (e in words) wordRow(this, e, 0, id)
            }, MATCH, WRAP, bottom = 8)
        }
        c.add(ctx.row {
            add(ctx.button(t("Папка", "Folder", "Papka"), BtnStyle.Tonal, R.drawable.ic_add) { newFolder(id) }, 0, WRAP, 1f, end = 4)
            if (pick == null && pair != 0L) add(ctx.button(t("Слово сюда", "Word here", "Shu yerga so'z"), BtnStyle.Tonal, R.drawable.ic_add) { addWord(id) }, 0, WRAP, 1f, start = 4)
        }, MATCH, WRAP, bottom = 8)
        if (id == null && !sourceOnly) topics(c)
        c.add(View(ctx), MATCH, ctx.dp(24))
    }

    private fun wordsIn(id: Long?): List<Entry> =
        if (pick != null || pair == 0L) emptyList() else VocabDb.entries(ctx, pair, source = id, direct = true)

    private fun find(list: List<SourceBranch>, id: Long): SourceBranch? {
        for (b in list) {
            if (b.node.id == id) return b
            find(b.children, id)?.let { return it }
        }
        return null
    }

    private fun revealSelected(byId: Map<Long, SourceNode>) {
        val seen = HashSet<Long>()
        var cur = selected?.let { byId[it] }?.parent?.let { byId[it] }
        while (cur != null && cur.id != folder && seen.add(cur.id)) {
            expanded.add(cur.id)
            cur = cur.parent?.let { byId[it] }
        }
    }

    private fun pathBar(c: LinearLayout, byId: Map<Long, SourceNode>) {
        val chain = ArrayList<SourceNode>()
        val seen = HashSet<Long>()
        var cur = folder?.let { byId[it] }
        while (cur != null && seen.add(cur.id)) {
            chain.add(0, cur)
            cur = cur.parent?.let { byId[it] }
        }
        val flow = FlowLayout(ctx)
        val levels = listOf<Pair<Long?, String>>(null to t("Мои слова", "My Words", "Mening so'zlarim")) + chain.map { it.id to it.title }
        levels.forEachIndexed { i, (target, label) ->
            if (i > 0) flow.addView(ctx.text("›", T.Label, Glass.TEXT2).apply { gravity = Gravity.CENTER; minHeight = ctx.dp(48); setPadding(ctx.dp(2), 0, ctx.dp(2), 0) })
            val last = i == levels.size - 1
            flow.addView(ctx.chip(label, last) { if (!last) jump(target) })
        }
        c.add(flow, MATCH, WRAP, bottom = 8)
    }

    private fun jump(target: Long?) {
        repeat(depth) { act.pop() }
        if (target != null) act.push(SourcesScreen(act, sourceOnly, allowNone, selected, target, 1, pick))
    }

    private fun choose(id: Long?) {
        pick?.invoke(id, null)
        repeat(depth + 1) { act.pop() }
    }

    private fun open(id: Long) {
        act.push(SourcesScreen(act, sourceOnly, allowNone, selected, id, depth + 1, pick))
    }

    private fun noneRow(): View = ctx.row {
        minimumHeight = ctx.dp(52)
        setPadding(ctx.dp(4 + ARROW_DP), 0, ctx.dp(8), 0)
        if (selected == null) background = ctx.rounded(P.alpha(P.primary, 36), 12f)
        add(ctx.icon(R.drawable.ic_folder, P.outline, 22), ctx.dp(22), ctx.dp(22), end = 12)
        add(ctx.text(t("Без папки", "No folder", "Papkasiz"), T.Label), 0, WRAP, 1f)
        press { choose(null) }
    }

    private fun itemsLabel(n: Int) = t("Элементов: $n", "$n items", "$n ta element")

    private fun branch(box: LinearLayout, b: SourceBranch, level: Int) {
        val n = b.node
        val hasContent = b.children.isNotEmpty() || (pick == null && n.words > 0)
        val isOpen = n.id in expanded && hasContent
        val kids = ctx.col { visibility = if (isOpen) View.VISIBLE else View.GONE }
        if (isOpen) fillKids(kids, b, level)
        val folderIcon = ctx.icon(if (isOpen) R.drawable.ic_folder_open else R.drawable.ic_folder, kindColor(n), 22)
        val chevron = ctx.icon(R.drawable.ic_chevron_right, Glass.TEXT2, 20).apply { rotation = if (isOpen) CHEVRON_OPEN else 0f }
        box.add(ctx.row {
            minimumHeight = ctx.dp(52)
            setPadding(ctx.dp(4 + level * INDENT_DP), 0, 0, 0)
            if (n.id == selected) background = ctx.rounded(P.alpha(P.primary, 36), 12f)
            add(ctx.row(Gravity.CENTER) {
                if (hasContent) {
                    add(chevron, ctx.dp(20), ctx.dp(20))
                    contentDescription = if (isOpen) t("Свернуть", "Collapse", "Yig'ish") else t("Развернуть", "Expand", "Yoyish")
                    press { toggle(kids, b, level, chevron, folderIcon, this) }
                }
            }, ctx.dp(ARROW_DP), ctx.dp(48))
            add(folderIcon, ctx.dp(22), ctx.dp(22), end = 12)
            add(ctx.col {
                add(ctx.text(n.title, T.Label).apply { setSingleLine(true); ellipsize = TextUtils.TruncateAt.END }, MATCH, WRAP)
                add(ctx.text(itemsLabel(b.children.size + n.words), T.Small), MATCH, WRAP, top = 2)
            }, 0, WRAP, 1f)
            val skill = if (n.kind == "part") skillLabel(n.skill) else null
            if (skill != null) add(ctx.pill(skill, if (n.skill == "listening") P.violet else P.sky), WRAP, WRAP, start = 6)
            val more = moreButton()
            more.press { folderMenu(more, n) }
            add(more, ctx.dp(48), ctx.dp(48))
            pressLong({ folderMenu(more, n) }) { open(n.id) }
        }, MATCH, WRAP)
        if (hasContent) box.add(kids, MATCH, WRAP)
    }

    private fun fillKids(kids: LinearLayout, b: SourceBranch, level: Int) {
        kids.removeAllViews()
        for (ch in b.children) branch(kids, ch, level + 1)
        for (e in wordsIn(b.node.id)) wordRow(kids, e, level + 1, b.node.id)
    }

    private fun moreButton(): LinearLayout = ctx.row(Gravity.CENTER) {
        add(ctx.icon(R.drawable.ic_more_vert, Glass.TEXT2, 22), ctx.dp(22), ctx.dp(22))
        contentDescription = t("Действия", "Actions", "Amallar")
    }

    private fun wordRow(box: LinearLayout, e: Entry, level: Int, home: Long?) {
        box.add(ctx.row {
            minimumHeight = ctx.dp(48)
            setPadding(ctx.dp(4 + level * INDENT_DP + ARROW_DP), 0, 0, 0)
            add(ctx.icon(R.drawable.ic_file, Glass.TEXT2, 22), ctx.dp(22), ctx.dp(22), end = 12)
            add(ctx.text(e.original, T.Body).apply { setSingleLine(true); ellipsize = TextUtils.TruncateAt.END }, 0, WRAP, 1f)
            val more = moreButton()
            more.press { wordMenu(more, e, home) }
            add(more, ctx.dp(48), ctx.dp(48))
            press { wordMenu(more, e, home) }
        }, MATCH, WRAP)
    }

    private fun kindColor(n: SourceNode) = when (n.kind) {
        "book" -> P.sky
        "test" -> P.teal
        "part" -> if (n.skill == "listening") P.violet else P.sky
        else -> P.orange
    }

    private fun toggle(kids: LinearLayout, b: SourceBranch, level: Int, chevron: View, folderIcon: ImageView, handle: View) {
        val id = b.node.id
        (kids.tag as? Animator)?.cancel()
        val opening = expanded.add(id)
        if (!opening) expanded.remove(id)
        if (opening) fillKids(kids, b, level)
        folderIcon.setImageResource(if (opening) R.drawable.ic_folder_open else R.drawable.ic_folder)
        handle.contentDescription = if (opening) t("Свернуть", "Collapse", "Yig'ish") else t("Развернуть", "Expand", "Yoyish")
        val motion = Motion.enabled(ctx)
        chevron.animate().cancel()
        chevron.animate().rotation(if (opening) CHEVRON_OPEN else 0f).setDuration(if (motion) Motion.QUICK else 0L).setInterpolator(Motion.standard).start()
        val width = (kids.parent as? View)?.width ?: 0
        if (!motion || width == 0) {
            kids.visibility = if (opening) View.VISIBLE else View.GONE
            return
        }
        kids.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
        val from = if (opening) 0 else kids.height
        val to = if (opening) kids.measuredHeight else 0
        kids.visibility = View.VISIBLE
        kids.layoutParams.height = from
        kids.alpha = if (opening) 0f else 1f
        kids.requestLayout()
        val anim = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = Motion.STANDARD
            interpolator = Motion.standard
            addUpdateListener {
                val f = it.animatedValue as Float
                kids.layoutParams.height = (from + (to - from) * f).toInt()
                kids.alpha = if (opening) f else 1f - f
                kids.requestLayout()
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    kids.tag = null
                    kids.layoutParams.height = WRAP
                    kids.alpha = 1f
                    kids.visibility = if (opening) View.VISIBLE else View.GONE
                    kids.requestLayout()
                }
            })
        }
        kids.tag = anim
        anim.start()
    }

    private fun folderMenu(anchor: View, n: SourceNode) {
        val popup = PopupMenu(ctx, anchor)
        popup.menu.add(0, 0, 0, t("Новая папка внутри", "New folder inside", "Ichida yangi papka"))
        popup.menu.add(0, 1, 1, t("Переименовать", "Rename", "Nomini o'zgartirish"))
        popup.menu.add(0, 2, 2, t("Удалить", "Delete", "O'chirish"))
        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                0 -> newFolder(n.id)
                1 -> titleDialog(t("Переименовать", "Rename", "Nomini o'zgartirish"), n.title) { if (it != n.title) { VocabDb.renameSource(ctx, n.id, it); refresh() } }
                2 -> ctx.confirm(
                    t("Удалить «${n.title}»?", "Delete \"${n.title}\"?", "«${n.title}» o'chirilsinmi?"),
                    t("Слова останутся в словаре. Вложенные папки тоже удалятся.", "Words stay in your vocabulary. Folders inside it are deleted too.", "So'zlar lug'atda qoladi. Ichidagi papkalar ham o'chiriladi."),
                    t("Удалить", "Delete", "O'chirish"), t("Отмена", "Cancel", "Bekor qilish")
                ) { VocabDb.deleteSource(ctx, n.id); expanded.remove(n.id); refresh() }
            }
            true
        }
        popup.show()
    }

    private fun wordMenu(anchor: View, e: Entry, home: Long?) {
        val popup = PopupMenu(ctx, anchor)
        popup.menu.add(0, 0, 0, t("Переместить в папку", "Move to folder", "Papkaga ko'chirish"))
        if (home != null) popup.menu.add(0, 1, 1, t("Убрать из этой папки", "Remove from this folder", "Bu papkadan olib tashlash"))
        popup.menu.add(0, 2, 2, t("Удалить слово", "Delete word", "So'zni o'chirish"))
        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                0 -> act.push(SourcesScreen(act, sourceOnly = true, selected = home) { s, _ ->
                    if (s != null && s != home) {
                        VocabDb.linkSource(ctx, e.id, s)
                        if (home != null) VocabDb.unlinkSource(ctx, e.id, home)
                    }
                })
                1 -> if (home != null) { VocabDb.unlinkSource(ctx, e.id, home); refresh() }
                2 -> ctx.confirm(
                    t("Удалить слово?", "Delete this word?", "So'z o'chirilsinmi?"), e.original,
                    t("Удалить", "Delete", "O'chirish"), t("Отмена", "Cancel", "Bekor qilish")
                ) { VocabDb.deleteEntry(ctx, e.id); refresh() }
            }
            true
        }
        popup.show()
    }

    private fun newFolder(parent: Long?) {
        titleDialog(t("Новая папка", "New folder", "Yangi papka"), "") {
            VocabDb.addSource(ctx, parent, "other", it, null)
            if (parent != null) expanded.add(parent)
            refresh()
        }
    }

    private fun addWord(home: Long?) {
        val field = ctx.field(t("Слово или фраза", "Word or phrase", "So'z yoki ibora")).apply {
            setSingleLine(true)
            imeOptions = EditorInfo.IME_ACTION_DONE
        }
        val form = ctx.col(20) { add(field, MATCH, WRAP) }
        val dialog = ctx.dialogBuilder().setTitle(t("Слово в эту папку", "Word in this folder", "Shu papkaga so'z")).setView(form)
            .setPositiveButton(t("Сохранить", "Save", "Saqlash")) { _, _ -> saveWord(field.text.toString(), home) }
            .setNegativeButton(t("Отмена", "Cancel", "Bekor qilish"), null)
            .show()
        bindSave(dialog, field)
    }

    private fun saveWord(text: String, home: Long?) {
        when (val r = VocabDb.addEntry(ctx, pair, text, null, home)) {
            is AddResult.Added -> {
                VocabEnrich.start(ctx, r.id)
                ctx.toast(t("Сохранено", "Saved", "Saqlandi"))
            }
            is AddResult.Duplicate -> if (home != null) {
                VocabDb.linkSource(ctx, r.id, home)
                ctx.toast(t("Слово уже было: добавлено в папку", "Word already saved: added to this folder", "So'z bor edi: papkaga qo'shildi"))
            } else ctx.toast(t("Это слово уже есть", "Already saved", "Bu so'z allaqachon bor"))
            is AddResult.TooLong -> ctx.toast(t("Слишком длинно: до 8 слов", "Too long: up to 8 words", "Juda uzun: 8 so'zgacha"))
            is AddResult.Empty -> ctx.toast(t("Введите слово", "Type a word", "So'z kiriting"))
            is AddResult.NoPair -> ctx.toast(t("Сначала выберите языки в словаре", "Choose languages in Vocabulary first", "Avval lug'atda tillarni tanlang"))
        }
        if (home != null) expanded.add(home)
        refresh()
    }

    private fun skillLabel(skill: String?) = when (skill) {
        "reading" -> t("Чтение", "Reading", "O'qish")
        "listening" -> t("Аудирование", "Listening", "Tinglash")
        else -> null
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
}
