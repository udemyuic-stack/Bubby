package com.example.buddy.ui

import android.widget.LinearLayout
import com.example.buddy.R
import com.example.buddy.RunHistory
import com.example.buddy.RunLog
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** What Bubby did on the phone in the last tasks: every step and its result (no screen contents are stored). */
class RunsScreen(act: UiActivity) : Screen(act) {
    override fun title() = t("Что сделал Bubby", "What Bubby did", "Bubby nima qildi")
    override fun onShow() { reshow() }

    private fun outcome(o: String): Pair<String, Int> = when (o) {
        "done" -> t("Готово", "Done", "Bajarildi") to P.success
        "cancelled" -> t("Остановлено", "Stopped", "To'xtatildi") to P.outline
        "stuck" -> t("Застрял", "Got stuck", "Tiqilib qoldi") to P.warning
        "budget_steps", "budget_time" -> t("Слишком долго", "Took too long", "Juda uzoq") to P.warning
        else -> o to P.outline
    }

    private fun card(c: LinearLayout, r: RunLog, first: Boolean) {
        val df = SimpleDateFormat("d MMM HH:mm", Locale.getDefault())
        val (label, color) = outcome(r.outcome)
        c.add(ctx.card(pad = 14) {
            add(ctx.row {
                add(ctx.text(r.task.ifBlank { "…" }, T.Title).apply { textSize = 15f; maxLines = 2 }, 0, WRAP, 1f)
                add(ctx.pill(label, color), WRAP, WRAP, start = 8)
            })
            val secs = ((r.end - r.start) / 1000).coerceAtLeast(0)
            add(ctx.text(df.format(Date(r.start)) + "  ·  " + t("шагов: ", "steps: ", "qadamlar: ") + r.steps.size + "  ·  " + secs + " s" +
                (if (r.mode.isNotEmpty()) "  ·  " + (if (r.mode == "tools") t("инструменты", "tool calling", "vositalar") else "JSON") else ""), T.Small), MATCH, WRAP, top = 4)
            if (r.summary.isNotBlank()) add(ctx.text("«" + r.summary.take(200) + "»", T.Body), MATCH, WRAP, top = 8)
            val shown = if (first) r.steps else r.steps.take(6)
            for (s in shown) {
                add(ctx.row {
                    add(ctx.icon(if (s.ok) R.drawable.ic_check_circle else R.drawable.ic_warning, if (s.ok) P.success else P.warning, 18), WRAP, WRAP, end = 8)
                    add(ctx.col {
                        add(ctx.text("${s.n}. ${s.detail}", T.Label), MATCH, WRAP)
                        if (s.result.isNotBlank() && s.result != "ok") add(ctx.text(s.result, T.Small), MATCH, WRAP, top = 1)
                    }, 0, WRAP, 1f)
                }, MATCH, WRAP, top = 8)
            }
            if (shown.size < r.steps.size) add(ctx.text(t("…и ещё ", "…and ", "…yana ") + (r.steps.size - shown.size) + t(" шагов", " more steps", " qadam"), T.Small), MATCH, WRAP, top = 6)
        }, MATCH, WRAP, bottom = 10)
    }

    override fun render(c: LinearLayout) {
        val runs = RunHistory.load(ctx)
        if (runs.isEmpty()) {
            c.add(ctx.emptyState(t("Пока пусто", "Nothing yet", "Hozircha bo'sh"),
                t("Попросите Bubby сделать что-то на телефоне, например «открой настройки и включи тёмную тему». Здесь будет видно каждый шаг.",
                    "Ask Bubby to do something on the phone, like \"open settings and turn on dark mode\". Every step will show up here.",
                    "Bubby'dan telefonda biror narsa qilishni so'rang, masalan «sozlamalarni ochib, tungi rejimni yoq». Har bir qadam shu yerda ko'rinadi."), sleepy = true), MATCH, WRAP)
            return
        }
        c.add(ctx.text(t("Последние задачи. Тексты с экрана и ключи здесь не хранятся.", "Recent tasks. Screen texts and keys are never stored here.",
            "So'nggi vazifalar. Ekran matnlari va kalitlar bu yerda saqlanmaydi."), T.Small), MATCH, WRAP, bottom = 10)
        runs.forEachIndexed { i, r -> card(c, r, i == 0) }
        c.add(ctx.button(t("Очистить историю", "Clear history", "Tarixni tozalash"), BtnStyle.Outline, R.drawable.ic_delete) {
            RunHistory.clear(ctx); refresh()
        }, MATCH, WRAP, top = 6)
    }
}
