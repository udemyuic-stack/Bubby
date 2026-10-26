package com.example.buddy.ui

import android.view.Gravity
import android.view.View
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import com.example.buddy.CalEvent
import com.example.buddy.CalendarStore
import com.example.buddy.R
import java.util.Calendar

class CalendarScreen(act: UiActivity) : Screen(act) {
    private var selected: Long? = null   // midnight of the chosen day, or null for the whole range
    private val days = 14

    override fun title() = t("Календарь", "Calendar", "Kalendar")
    override fun onShow() { reshow() }
    override fun onPermissionResult() { refresh() }
    override fun fab() = if (CalendarStore(ctx).hasPermission()) t("Событие", "Event", "Tadbir") to { add() } else null

    private fun midnight(offsetDays: Int): Long = Calendar.getInstance().apply {
        add(Calendar.DAY_OF_YEAR, offsetDays)
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    private fun accentFor(title: String): Int {
        val palette = intArrayOf(P.orange, P.violet, P.rose, P.teal, P.sky)
        return palette[Math.floorMod(title.hashCode(), palette.size)]
    }

    private fun add() {
        val start0 = Calendar.getInstance().apply { add(Calendar.HOUR_OF_DAY, 1); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0) }.timeInMillis
        var start = start0
        var end = start0 + 3_600_000L
        val title = ctx.field(t("Название", "Title", "Nomi"))
        val place = ctx.field(t("Место", "Location", "Joy"))
        val startLabel = ctx.text("", T.Label)
        val endLabel = ctx.text("", T.Label)
        fun sync() {
            startLabel.text = t("Начало: ", "Start: ", "Boshlanishi: ") + fmtDateTime(start)
            endLabel.text = t("Конец: ", "End: ", "Tugashi: ") + fmtDateTime(end)
        }
        fun pickRow(label: View, onPick: () -> Unit) = ctx.row {
            background = ctx.rounded(0, 16f, P.outline)
            setPadding(ctx.dp(14), ctx.dp(12), ctx.dp(14), ctx.dp(12))
            add(ctx.icon(R.drawable.ic_schedule, P.onSurfaceVariant, 20), WRAP, WRAP, end = 10)
            add(label, 0, WRAP, 1f)
            press(onPick)
        }
        sync()
        val form = ctx.col(20) {
            add(title, MATCH, WRAP)
            add(place, MATCH, WRAP, top = 10)
            add(pickRow(startLabel) {
                ctx.pickDateTime(start) { picked ->
                    val len = end - start
                    start = picked; end = picked + len.coerceAtLeast(60_000L); sync()
                }
            }, MATCH, WRAP, top = 12)
            add(pickRow(endLabel) { ctx.pickDateTime(end) { end = it; sync() } }, MATCH, WRAP, top = 10)
        }
        val dlg = ctx.dialogBuilder()
            .setTitle(t("Новое событие", "New event", "Yangi tadbir"))
            .setView(ctx.scroll(form))
            .setNegativeButton(t("Отмена", "Cancel", "Bekor qilish"), null)
            .setPositiveButton(t("Добавить", "Add", "Qo'shish"), null).create()
        dlg.setOnShowListener {
            dlg.getButton(android.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val name = title.text.toString().trim()
                if (name.isEmpty()) { ctx.toast(t("Введите название", "Enter a title", "Nomini kiriting")); return@setOnClickListener }
                if (end <= start) {
                    ctx.toast(t("Конец должен быть позже начала", "The end must be after the start", "Tugash vaqti boshlanishdan keyin bo'lishi kerak"))
                    return@setOnClickListener
                }
                val id = CalendarStore(ctx).add(name, start, end, place.text.toString().trim())
                if (id == null) ctx.toast(t("Не удалось создать событие", "Could not create the event", "Tadbirni yaratib bo'lmadi"))
                dlg.dismiss(); refresh()
            }
        }
        dlg.show()
    }

    private fun confirmDelete(e: CalEvent) {
        ctx.confirm(
            t("Удалить событие?", "Delete event?", "Tadbir o'chirilsinmi?"),
            t("«${e.title}» будет удалено.", "“${e.title}” will be deleted.", "«${e.title}» o'chiriladi."),
            t("Удалить", "Delete", "O'chirish"), t("Отмена", "Cancel", "Bekor qilish")
        ) { CalendarStore(ctx).delete(e.id); refresh() }
    }

    private fun dayStrip(events: List<CalEvent>): View {
        val row = ctx.row {
            setPadding(ctx.dp(12), ctx.dp(4), ctx.dp(12), ctx.dp(8))
            val hour = Calendar.getInstance()
            val accent = P.accent(hour.get(Calendar.HOUR_OF_DAY), hour.get(Calendar.MINUTE))
            val short = weekdaysShort(ctx)
            for (i in 0 until days) {
                val day = midnight(i)
                val c = cal(day)
                val isSel = selected == day
                val has = events.any { it.startMillis in day until day + 86_400_000L }
                val fg = if (isSel) P.ink else P.onSurface
                add(ctx.col {
                    gravity = Gravity.CENTER_HORIZONTAL
                    setPadding(ctx.dp(8), ctx.dp(10), ctx.dp(8), ctx.dp(10))
                    background = if (isSel) ctx.rounded(accent, 20f) else ctx.rounded(P.low, 20f, P.outlineVariant)
                    add(ctx.text(short[c.mondayDow() - 1], T.Small, fg), WRAP, WRAP)
                    add(ctx.text("${c.get(Calendar.DAY_OF_MONTH)}", T.Title, fg), WRAP, WRAP, top = 2)
                    add(View(ctx).apply { background = ctx.rounded(if (has) (if (isSel) P.ink else accent) else 0, 4f) }, ctx.dp(6), ctx.dp(6), top = 4)
                    press { selected = if (isSel) null else day; refresh() }
                }, ctx.dp(54), WRAP, end = 8)
            }
        }
        return HorizontalScrollView(ctx).apply { isHorizontalScrollBarEnabled = false; addView(row) }
    }

    private fun eventCard(e: CalEvent): View {
        val accent = accentFor(e.title)
        return ctx.card(pad = 0, onClick = { confirmDelete(e) }) {
            add(ctx.row(Gravity.CENTER_VERTICAL) {
                add(View(ctx).apply { background = ctx.rounded(accent, 3f) }, ctx.dp(5), ctx.dp(44), end = 12, start = 12)
                add(ctx.col {
                    setPadding(0, ctx.dp(12), ctx.dp(14), ctx.dp(12))
                    add(ctx.text(e.title.ifEmpty { t("Без названия", "Untitled", "Nomsiz") }, T.Title), MATCH, WRAP)
                    add(ctx.text("${fmtTime(cal(e.startMillis))} – ${fmtTime(cal(e.endMillis))}", T.Small), MATCH, WRAP, top = 2)
                    if (e.location.isNotEmpty()) add(ctx.text(e.location, T.Small), MATCH, WRAP, top = 2)
                }, 0, WRAP, 1f)
            })
        }
    }

    override fun render(c: LinearLayout) {
        header(c)
        val store = CalendarStore(ctx)
        if (!store.hasPermission()) {
            c.add(ctx.emptyState(
                t("Нет доступа к календарю", "No calendar access", "Kalendarga ruxsat yo'q"),
                t("Разрешите доступ, чтобы видеть и создавать события.", "Allow access to see and create events.", "Tadbirlarni ko'rish va yaratish uchun ruxsat bering.")
            ))
            c.add(ctx.button(t("Дать разрешения", "Grant permissions", "Ruxsat berish")) { Backend.requestPermissions(act, "calendar") }, WRAP, WRAP, top = 8)
            c.gravity = Gravity.CENTER_HORIZONTAL
            return
        }
        val from = midnight(0)
        val to = midnight(days)
        val events = store.events(from, to).sortedBy { it.startMillis }
        c.add(dayStrip(events), MATCH, WRAP, start = -16, end = -16)
        val shown = selected?.let { d -> events.filter { it.startMillis in d until d + 86_400_000L } } ?: events
        if (shown.isEmpty()) {
            c.add(ctx.emptyState(
                if (selected == null) t("Событий нет", "No events", "Tadbirlar yo'q") else t("В этот день пусто", "Nothing on this day", "Bu kunda hech narsa yo'q"),
                if (selected == null) t("На 14 дней вперёд ничего не запланировано.", "Nothing planned for the next 14 days.", "Keyingi 14 kunda hech narsa rejalashtirilmagan.")
                else t("Нажмите «Событие», чтобы добавить.", "Tap “Event” to add one.", "Qo'shish uchun «Tadbir» ni bosing.")
            ))
            return
        }
        var lastDay = -1L
        for (e in shown) {
            val d = cal(e.startMillis).apply { set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0) }.timeInMillis
            if (d != lastDay) { lastDay = d; c.add(ctx.section(fmtDayHeader(ctx, e.startMillis))) }
            c.add(eventCard(e), MATCH, WRAP, bottom = 8)
        }
        c.add(ctx.text(t("Нажмите на событие, чтобы удалить", "Tap an event to delete it", "O'chirish uchun tadbirni bosing"), T.Small).apply { gravity = Gravity.CENTER }, MATCH, WRAP, top = 6)
    }
}
