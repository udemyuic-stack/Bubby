package com.example.buddy.ui

import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import com.example.buddy.R
import com.example.buddy.Task

class TasksScreen(act: UiActivity) : Screen(act) {
    override fun title() = t("Задачи", "Tasks", "Vazifalar")
    override fun onShow() { reshow() }
    override fun fab() = t("Задача", "Task", "Vazifa") to { edit(null) }

    private fun edit(task: Task?) {
        val title = ctx.field(t("Название", "Title", "Nomi"), task?.title.orEmpty())
        val notes = ctx.field(t("Заметки", "Notes", "Eslatmalar"), task?.notes.orEmpty(), multiline = true)
        var due: Long? = task?.dueMillis
        var remind = task?.remind ?: true
        val dueLabel = ctx.text("", T.Label)
        val clear = ctx.icon(R.drawable.ic_close, P.onSurfaceVariant, 22)
        fun sync() {
            dueLabel.text = due?.let { fmtDateTime(it) } ?: t("Срок", "Due date", "Muddat")
            clear.visibility = if (due == null) View.GONE else View.VISIBLE
        }
        val dueRow = ctx.row {
            background = ctx.rounded(0, 16f, P.outline)
            setPadding(ctx.dp(14), ctx.dp(12), ctx.dp(14), ctx.dp(12))
            add(ctx.icon(R.drawable.ic_calendar, P.onSurfaceVariant, 20), WRAP, WRAP, end = 10)
            add(dueLabel, 0, WRAP, 1f)
            add(clear, WRAP, WRAP)
            clear.setOnClickListener { due = null; sync() }
            press { ctx.pickDateTime(due ?: (System.currentTimeMillis() + 3_600_000L)) { due = it; sync() } }
        }
        sync()
        val form = ctx.col(20) {
            add(title, MATCH, WRAP)
            add(notes, MATCH, WRAP, top = 10)
            add(dueRow, MATCH, WRAP, top = 12)
            add(ctx.switchRow(t("Напомнить", "Remind me", "Eslatish"), null, remind) { remind = it }, MATCH, WRAP, top = 6)
        }
        val b = ctx.dialogBuilder()
            .setTitle(if (task == null) t("Новая задача", "New task", "Yangi vazifa") else t("Задача", "Task", "Vazifa"))
            .setView(ctx.scroll(form))
            .setNegativeButton(t("Отмена", "Cancel", "Bekor qilish"), null)
        if (task != null) b.setNeutralButton(t("Удалить", "Delete", "O'chirish")) { _, _ -> confirmDelete(task) }
        val dlg = b.setPositiveButton(t("Сохранить", "Save", "Saqlash"), null).create()
        dlg.setOnShowListener {
            dlg.getButton(android.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val name = title.text.toString().trim()
                if (name.isEmpty()) { ctx.toast(t("Введите название", "Enter a title", "Nomini kiriting")); return@setOnClickListener }
                Backend.saveTask(ctx, Task(task?.id.orEmpty(), name, notes.text.toString().trim(), due, remind, task?.done ?: false))
                dlg.dismiss(); refresh()
            }
        }
        dlg.show()
    }

    private fun confirmDelete(task: Task) {
        ctx.confirm(
            t("Удалить задачу?", "Delete task?", "Vazifa o'chirilsinmi?"),
            t("«${task.title}» будет удалена.", "“${task.title}” will be deleted.", "«${task.title}» o'chiriladi."),
            t("Удалить", "Delete", "O'chirish"), t("Отмена", "Cancel", "Bekor qilish")
        ) { Backend.deleteTask(ctx, task.id); refresh() }
    }

    private fun dueChip(task: Task): View {
        val millis = task.dueMillis ?: return View(ctx)
        val left = millis - System.currentTimeMillis()
        val (bg, fg, ic) = when {
            task.done -> Triple(P.highest, P.onSurfaceVariant, R.drawable.ic_check)
            left < 0 -> Triple(P.errorContainer, P.onErrorContainer, R.drawable.ic_error)
            left < 24 * 3_600_000L -> Triple(P.primaryContainer, P.onPrimaryContainer, R.drawable.ic_fire)
            left < 4 * 24 * 3_600_000L -> Triple(P.secondaryContainer, P.onSecondaryContainer, R.drawable.ic_schedule)
            else -> Triple(P.highest, P.onSurfaceVariant, R.drawable.ic_calendar)
        }
        return ctx.row(Gravity.CENTER_VERTICAL) {
            background = ctx.rounded(bg, 20f)
            setPadding(ctx.dp(10), ctx.dp(4), ctx.dp(10), ctx.dp(4))
            add(ctx.icon(ic, fg, 14), WRAP, WRAP, end = 5)
            add(ctx.text(fmtDue(ctx, millis), T.Label, fg).apply { textSize = 11.5f }, WRAP, WRAP)
        }
    }

    private fun checkBox(done: Boolean, onToggle: () -> Unit): View = ctx.row(Gravity.CENTER) {
        setPadding(ctx.dp(8), ctx.dp(8), ctx.dp(8), ctx.dp(8))
        add(ctx.row(Gravity.CENTER) {
            background = ctx.rounded(if (done) P.primary else 0, 14f, if (done) P.primary else P.outline, 2f)
            if (done) add(ctx.icon(R.drawable.ic_check, P.onPrimary, 18), WRAP, WRAP)
        }, ctx.dp(28), ctx.dp(28))
        press(onToggle)
    }

    private fun tile(task: Task): View = ctx.card(pad = 12, onClick = { edit(task) }, onLong = { confirmDelete(task) }) {
        add(ctx.row {
            add(checkBox(task.done) { Backend.setTaskDone(ctx, task.id, !task.done); refresh() }, WRAP, WRAP, end = 4)
            add(ctx.col {
                add(ctx.text(task.title, T.Title, if (task.done) P.onSurfaceVariant else P.onSurface).apply {
                    if (task.done) paintFlags = paintFlags or android.graphics.Paint.STRIKE_THRU_TEXT_FLAG
                }, MATCH, WRAP)
                if (task.notes.isNotEmpty()) add(ctx.text(task.notes, T.Small).apply { maxLines = 2; ellipsize = android.text.TextUtils.TruncateAt.END }, MATCH, WRAP, top = 2)
                if (task.dueMillis != null) add(dueChip(task), WRAP, WRAP, top = 8)
            }, 0, WRAP, 1f)
            if (task.remind && task.dueMillis != null) add(ctx.icon(R.drawable.ic_bell, P.onSurfaceVariant, 20), WRAP, WRAP, start = 8)
        })
    }

    override fun render(c: LinearLayout) {
        val all = Backend.tasks(ctx)
        val open = all.filter { !it.done }
        val done = all.filter { it.done }
        header(c, if (all.isEmpty()) t("Пока пусто", "Nothing yet", "Hozircha bo'sh")
        else t("Открыто: ${open.size} · выполнено: ${done.size}", "Open: ${open.size} · done: ${done.size}", "Ochiq: ${open.size} · bajarilgan: ${done.size}"))
        if (all.isEmpty()) {
            c.add(ctx.emptyState(
                t("Задач пока нет", "No tasks yet", "Hozircha vazifalar yo'q"),
                t("Нажмите «Задача», чтобы добавить первую", "Tap “Task” to add your first one", "Birinchisini qo'shish uchun «Vazifa» ni bosing")
            ))
            return
        }
        if (open.isNotEmpty()) { c.add(ctx.section(t("Нужно сделать", "To do", "Bajarish kerak"))); for (x in open) c.add(tile(x), MATCH, WRAP, bottom = 8) }
        if (done.isNotEmpty()) { c.add(ctx.section(t("Готово", "Done", "Tayyor"))); for (x in done) c.add(tile(x), MATCH, WRAP, bottom = 8) }
        c.add(ctx.text(t("Долгое нажатие — удалить", "Long press to delete", "O'chirish uchun uzoq bosing"), T.Small).apply { gravity = Gravity.CENTER }, MATCH, WRAP, top = 6)
    }
}
