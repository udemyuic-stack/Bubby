package com.example.buddy.ui

import android.app.Dialog
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.Window
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import com.example.buddy.AgentFile
import com.example.buddy.BuddyService
import com.example.buddy.DiffAssembler
import com.example.buddy.DiffPart
import com.example.buddy.R

/**
 * The lines of one file an agent changed, in a sheet from the bottom. Asks the computer with `getDiff` (through the
 * service), puts the parts together, colours the lines (+ green, - red, @ grey, monospace, scrolls sideways) and forgets
 * everything when it closes. [demo] parts are shown instead of asking (CI screenshots).
 */
class DiffSheet(private val act: UiActivity, private val pillId: String, private val file: AgentFile, private val demo: List<DiffPart>? = null) {
    private val ctx = act
    private val main = Handler(Looper.getMainLooper())
    private var asm: DiffAssembler? = DiffAssembler(pillId, file.id)
    private val dlg = Dialog(act)
    private val status = ctx.text("", T.Small)
    private val counts = ctx.row()
    private val body = ctx.col()
    private var open = true
    private val timeout = Runnable { if (open && asm?.complete != true) failed(t("Компьютер не ответил", "The computer did not answer", "Kompyuter javob bermadi")) }
    private val sink: (DiffPart?) -> Unit = { p -> main.post { onPart(p) } }

    private fun t(ru: String, en: String, uz: String) = com.example.buddy.Tr.t(ctx, ru, en, uz)

    fun show() {
        val root = ctx.col {
            background = GradientDrawable().apply {
                setColor(P.surface); val r = ctx.dpf(26f); cornerRadii = floatArrayOf(r, r, r, r, 0f, 0f, 0f, 0f)
            }
            setPadding(ctx.dp(16), ctx.dp(10), ctx.dp(16), ctx.dp(16))
            add(ctx.row(Gravity.CENTER) { add(View(ctx).apply { background = ctx.rounded(P.outline, 3f) }, ctx.dp(40), ctx.dp(5)) }, MATCH, WRAP, bottom = 10)
            add(ctx.row {
                add(ctx.icon(R.drawable.ic_edit, P.onSurfaceVariant, 20), WRAP, WRAP, end = 8)
                add(ctx.text(file.name, T.Title).apply { typeface = Typeface.MONOSPACE; maxLines = 2 }, 0, WRAP, 1f)
                add(ctx.row(Gravity.CENTER) {
                    add(ctx.icon(R.drawable.ic_close, P.onSurface, 22), WRAP, WRAP)
                    contentDescription = t("Закрыть", "Close", "Yopish")
                    press { dlg.dismiss() }
                }, ctx.dp(44), ctx.dp(44))
            }, MATCH, WRAP)
            add(counts, MATCH, WRAP, top = 2)
            add(status, MATCH, WRAP, top = 6)
            add(ScrollView(ctx).apply {
                isFillViewport = true
                addView(HorizontalScrollView(ctx).apply { isFillViewport = true; addView(body) })
                background = ctx.rounded(P.container, 14f)
                setPadding(0, ctx.dp(6), 0, ctx.dp(6))
            }, MATCH, 0, 1f, top = 10)
        }
        fillCounts(file.added, file.removed, file.isNew, file.tooLarge)
        dlg.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dlg.setContentView(root)
        dlg.window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            setGravity(Gravity.BOTTOM)
            setLayout(MATCH, (ctx.resources.displayMetrics.heightPixels * 0.82f).toInt())
            attributes = attributes.apply { windowAnimations = android.R.style.Animation_InputMethod }
        }
        dlg.setOnDismissListener { forget() }
        dlg.show()
        ask()
    }

    private fun ask() {
        asm = DiffAssembler(pillId, file.id)
        body.removeAllViews()
        say(t("Загружаю изменения…", "Loading the changes…", "O'zgarishlar yuklanmoqda…"))
        if (demo != null) { main.postDelayed({ for (p in demo) onPart(p) }, 250); return }
        BuddyService.agentDiffSink = sink
        main.removeCallbacks(timeout)
        main.postDelayed(timeout, 10_000L)
        ctx.startService(android.content.Intent(ctx, BuddyService::class.java).setAction(BuddyService.ACTION_AGENT_GET_DIFF)
            .putExtra("pill", pillId).putExtra("file", file.id))
    }

    private fun say(msg: String) { status.text = msg; status.visibility = if (msg.isEmpty()) View.GONE else View.VISIBLE }

    private fun failed(msg: String) {
        main.removeCallbacks(timeout)
        say(msg)
        body.removeAllViews()
        body.add(ctx.button(t("Ещё раз", "Try again", "Qayta urinish"), BtnStyle.Tonal) { if (open) ask() }, WRAP, WRAP, start = 12, top = 8, bottom = 8)
    }

    private fun onPart(p: DiffPart?) {
        if (!open) return
        val a = asm ?: return
        if (p == null) {
            failed(t("Сейчас нельзя запросить (нет связи или слишком много запросов). Подождите немного.",
                "Cannot ask right now (offline or too many requests). Wait a moment.",
                "Hozir so'rab bo'lmaydi (aloqa yo'q yoki so'rov ko'p). Biroz kuting."))
            return
        }
        if (!a.add(p) || !a.complete) return
        main.removeCallbacks(timeout)
        fillCounts(maxOf(a.added, 0), maxOf(a.removed, 0), file.isNew, a.tooLarge)
        when {
            a.gone -> { say(t("Компьютер больше не хранит это изменение", "The computer no longer has this change", "Kompyuterda bu o'zgarish endi yo'q")); body.removeAllViews() }
            a.tooLarge -> { say(t("Изменение слишком большое: только счётчики строк", "The change is too large: line counts only", "O'zgarish juda katta: faqat qatorlar soni")); body.removeAllViews() }
            else -> {
                val lines = a.lines()
                say(if (a.truncated) t("Показаны не все строки", "Not every line is shown", "Hamma qatorlar ko'rsatilmagan")
                    else if (lines.isEmpty()) t("Строк нет", "No lines", "Qatorlar yo'q") else "")
                draw(lines)
            }
        }
    }

    private fun draw(lines: List<com.example.buddy.DiffLine>) {
        body.removeAllViews()
        val green = if (P.dark) 0xFF7EE2A1.toInt() else 0xFF1C7C3E.toInt()
        val red = if (P.dark) 0xFFFF9A93.toInt() else 0xFFB3261E.toInt()
        for (l in lines) {
            val (fg, bg) = when (l.kind) {
                '+' -> green to P.alpha(green, 34)
                '-' -> red to P.alpha(red, 34)
                '@' -> P.onSurfaceVariant to P.alpha(P.outline, 30)
                else -> P.onSurface to 0
            }
            // the text is plain characters from the computer, cleaned already: never parsed as HTML or styled text
            body.add(ctx.text((if (l.kind == ' ') "  " else "${l.kind} ") + l.text, T.Body, fg).apply {
                typeface = Typeface.MONOSPACE; textSize = 12.5f; setSingleLine(); setHorizontallyScrolling(true)
                setPadding(ctx.dp(10), ctx.dp(1), ctx.dp(16), ctx.dp(1))
                if (bg != 0) setBackgroundColor(bg)
            }, MATCH, WRAP)
        }
    }

    private fun fillCounts(added: Int, removed: Int, isNew: Boolean, tooLarge: Boolean) {
        counts.removeAllViews()
        counts.add(ctx.text("+$added", T.Label, if (P.dark) 0xFF7EE2A1.toInt() else 0xFF1C7C3E.toInt()), WRAP, WRAP, end = 8)
        counts.add(ctx.text("−$removed", T.Label, if (P.dark) 0xFFFF9A93.toInt() else 0xFFB3261E.toInt()), WRAP, WRAP, end = 10)
        if (isNew) counts.add(AgentsScreen.badge(ctx, t("новый", "new", "yangi"), P.sky), WRAP, WRAP, end = 6)
        if (tooLarge) counts.add(AgentsScreen.badge(ctx, t("очень большой", "too large", "juda katta"), P.orange), WRAP, WRAP)
    }

    /** Closing forgets every line: nothing is kept or written anywhere. */
    private fun forget() {
        open = false
        main.removeCallbacksAndMessages(null)
        if (BuddyService.agentDiffSink === sink) BuddyService.agentDiffSink = null
        asm?.clear(); asm = null
        body.removeAllViews()
    }
}
