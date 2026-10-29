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
            val dot = Glass.ACCENT2
            for (i in 0 until days) {
                val day = midnight(i)
                val c = cal(day)
                val isSel = selected == day
                val has = events.any { it.startMillis in day until day + 86_400_000L }
                val fg = if (isSel) Glass.ON_ACTIVE else Glass.TEXT
                add(ctx.col {
                    gravity = Gravity.CENTER_HORIZONTAL
                    setPadding(ctx.dp(4), ctx.dp(12), ctx.dp(4), ctx.dp(8))
                    background = ctx.petalShape(if (isSel) GlassKind.Active else GlassKind.Dim, PETAL_RADII_DAY)
                    add(ctx.text(short[c.mondayDow() - 1], T.Small, if (isSel) fg else Glass.TEXT_LABEL).apply { textSize = 12f }, WRAP, WRAP)
                    add(ctx.text("${c.get(Calendar.DAY_OF_MONTH)}", T.Title, fg).apply { textSize = 20f }, WRAP, WRAP, top = 2)
                    add(View(ctx).apply { background = ctx.rounded(if (has) dot else 0, 4f) }, ctx.dp(6), ctx.dp(6), top = 6)
                    press { selected = if (isSel) null else day; refresh() }
                }, ctx.dp(56), ctx.dp(80), end = 8)
            }
        }
        return HorizontalScrollView(ctx).apply { isHorizontalScrollBarEnabled = false; addView(row) }
    }

    private fun eventCard(e: CalEvent): View {
        val accent = accentFor(e.title)
        return ctx.card(pad = 0, radius = 22f, onClick = { confirmDelete(e) }) {
            add(ctx.row(Gravity.CENTER_VERTICAL) {
                minimumHeight = ctx.dp(58)
                setPadding(ctx.dp(14), ctx.dp(10), ctx.dp(14), ctx.dp(10))
                add(ctx.col {
                    gravity = Gravity.CENTER_HORIZONTAL
                    add(ctx.text(fmtTime(cal(e.startMillis)), T.Label, Glass.TEXT).apply { textSize = 14f }, WRAP, WRAP)
                    add(ctx.text(fmtTime(cal(e.endMillis)), T.Small).apply { textSize = 12f }, WRAP, WRAP, top = 2)
                }, ctx.dp(46), WRAP)
                add(View(ctx).apply { background = ctx.rounded(accent, 2f) }, ctx.dp(4), MATCH, start = 10, end = 12)
                add(ctx.col {
                    add(ctx.text(e.title.ifEmpty { t("Без названия", "Untitled", "Nomsiz") }, T.Body).apply { textSize = 15f }, MATCH, WRAP)
                    if (e.location.isNotEmpty()) add(ctx.row {
                        add(ctx.icon(R.drawable.tg_pin, Glass.TEXT2, 12), WRAP, WRAP, end = 4)
                        add(ctx.text(e.location, T.Small).apply { textSize = 12f }, WRAP, WRAP)
                    }, MATCH, WRAP, top = 4)
                }, 0, WRAP, 1f)
            })
        }
    }

    override fun render(c: LinearLayout) {
        header(c, fmtDayHeader(ctx, System.currentTimeMillis()))
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
                else t("Нажмите «Событие», чтобы добавить.", "Tap “Event” to add one.", "Qo'shish uchun «Tadbir» ni bosing."),
                sleepy = true
            ))
            return
        }
        var lastDay = -1L
        for (e in shown) {
            val d = cal(e.startMillis).apply { set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0) }.timeInMillis
            if (d != lastDay) { lastDay = d; c.add(ctx.text(fmtDayHeader(ctx, e.startMillis), T.Label, Glass.DATE), MATCH, WRAP, top = 10, bottom = 8, start = 4) }
            c.add(eventCard(e), MATCH, WRAP, bottom = 8)
        }
        c.add(ctx.text(t("Нажмите на событие, чтобы удалить", "Tap an event to delete it", "O'chirish uchun tadbirni bosing"), T.Small).apply { gravity = Gravity.CENTER }, MATCH, WRAP, top = 6)
    }
}
