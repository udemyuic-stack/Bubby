package com.example.buddy.ui

import android.content.ClipboardManager
import android.content.Context
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.ProgressBar
import com.example.buddy.AgentSession
import com.example.buddy.R

class AgentsScreen(act: UiActivity) : Screen(act) {
    override fun title() = t("Агенты", "Agents", "Agentlar")
    private var polling = false
    private val shown = HashMap<String, Int>()   // progress already drawn per session, so a refresh only animates the change
    private val linkField by lazy { ctx.field(t("Ссылка для подключения", "Connection link", "Ulanish havolasi")).apply { setSingleLine(); hint = "coucou://pair?..." } }

    override fun onShow() {
        refresh()
        if (polling) return
        polling = true
        val tick = object : Runnable {
            override fun run() {
                if (!content.isAttachedToWindow) { polling = false; return }
                if (Backend.agentStatus(ctx).paired) refresh()
                content.postDelayed(this, 2000)
            }
        }
        content.postDelayed(tick, 2000)
    }

    private fun stateInfo(s: String): Pair<String, Int> = when (s) {
        "idle" -> t("Ждёт", "Idle", "Kutyapti") to P.outline
        "working" -> t("Работает", "Working", "Ishlayapti") to P.sky
        "thinking" -> t("Думает", "Thinking", "O'ylayapti") to P.violet
        "searching" -> t("Ищет", "Searching", "Qidiryapti") to P.teal
        "approval" -> t("Нужно разрешение", "Needs permission", "Ruxsat kerak") to P.orange
        "question" -> t("Вопрос", "Question", "Savol") to P.warning
        "error" -> t("Ошибка", "Error", "Xato") to P.error
        "finished" -> t("Готово", "Done", "Tayyor") to P.success
        "ratelimit" -> t("Лимит", "Limit", "Limit") to P.orange
        "sleeping" -> t("Спит", "Sleeping", "Uxlayapti") to P.outline
        "dizzy" -> t("Устал", "Tired", "Charchadi") to P.violet
        else -> s to P.outline
    }

    private fun sessionCard(a: AgentSession): LinearLayout {
        val (label, color) = stateInfo(a.state)
        return ctx.card(pad = 14) {
            add(ctx.row {
                add(ctx.text(a.agent, T.Title), 0, WRAP, 1f)
                add(ctx.pill(label, color), WRAP, WRAP)
            })
            if (a.statusText.isNotEmpty()) add(ctx.text(a.statusText, T.Body), MATCH, WRAP, top = 6)
            if (a.stepCount > 0) {
                val bar = ProgressBar(ctx, null, android.R.attr.progressBarStyleHorizontal).apply {
                    max = a.stepCount; progress = shown[a.pillId] ?: 0
                    progressTintList = android.content.res.ColorStateList.valueOf(P.primary)
                }
                add(bar, MATCH, ctx.dp(10), top = 10)
                val goal = a.stepIndex.coerceIn(0, a.stepCount)
                if (goal != bar.progress) bar.post { bar.animateTo(goal) }
                shown[a.pillId] = goal
                add(ctx.text(t("Шаг ${a.stepIndex} из ${a.stepCount}", "Step ${a.stepIndex} of ${a.stepCount}", "${a.stepIndex}-qadam, jami ${a.stepCount} ta"), T.Small), MATCH, WRAP, top = 4)
            }
        }
    }

    private fun pair() {
        val raw = linkField.text.toString().trim()
        if (raw.isEmpty()) { ctx.toast(t("Вставьте ссылку из терминала", "Paste the link from the terminal", "Terminaldagi havolani qo'ying")); return }
        val err = Backend.agentPair(ctx, raw)
        if (err != null) ctx.toast(err) else { linkField.setText(""); refresh() }
    }

    private fun unpair() = ctx.confirm(
        t("Отключить компьютер?", "Disconnect the computer?", "Kompyuterni uzamizmi?"),
        t("Телефон перестанет получать запросы от Claude Code.", "The phone will stop receiving requests from Claude Code.", "Telefon Claude Code so'rovlarini olmay qo'yadi."),
        t("Отключить", "Disconnect", "Uzish"), t("Отмена", "Cancel", "Bekor qilish")
    ) { Backend.agentUnpair(ctx); refresh() }

    override fun render(c: LinearLayout) {
        val s = Backend.agentStatus(ctx)
        c.add(ctx.card(pad = 16, fill = P.accentContainer(java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY), 0)) {
            add(ctx.text(t(
                "Следите за Claude Code на компьютере и разрешайте или отклоняйте его запросы с телефона.\nТелефон и компьютер должны быть в одной сети.\nПока вы не подтвердите, ничего не выполняется.",
                "Keep an eye on Claude Code on your computer and allow or decline its requests from your phone.\nThe phone and the computer must be on the same network.\nNothing runs until you confirm.",
                "Kompyuterdagi Claude Code ishini kuzating va uning so'rovlariga telefondan ruxsat bering yoki rad eting.\nTelefon va kompyuter bitta tarmoqda bo'lishi kerak.\nSiz tasdiqlamaguncha hech narsa bajarilmaydi."), T.Body), MATCH, WRAP)
        }, MATCH, WRAP, bottom = 14)
        if (s.paired) {
            c.add(ctx.row {
                add(ctx.text(s.desktop.ifEmpty { t("Компьютер", "Computer", "Kompyuter") }, T.Headline).apply { textSize = 22f }, 0, WRAP, 1f)
                add(ctx.pill(if (s.connected) t("на связи", "online", "aloqada") else t("нет связи", "offline", "aloqa yo'q"), if (s.connected) P.success else P.outline), WRAP, WRAP)
            }, MATCH, WRAP, bottom = 10)
            if (s.sessions.isEmpty()) c.add(ctx.emptyState(t("Активных сессий нет", "No active sessions", "Faol sessiyalar yo'q"), ""))
            for (a in s.sessions) c.add(sessionCard(a), MATCH, WRAP, bottom = 10)
            c.add(ctx.button(t("Отключить", "Disconnect", "Uzish"), BtnStyle.Outline) { unpair() }, MATCH, WRAP, top = 8)
        } else {
            (linkField.parent as? android.view.ViewGroup)?.removeView(linkField)
            c.add(linkField, MATCH, WRAP)
            c.add(ctx.row {
                add(ctx.button(t("Вставить", "Paste", "Qo'yish"), BtnStyle.Tonal) {
                    val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    val txt = cm.primaryClip?.getItemAt(0)?.coerceToText(ctx)?.toString()?.trim().orEmpty()
                    if (txt.isNotEmpty()) linkField.setText(txt)
                }, 0, WRAP, 1f, end = 8)
                add(ctx.button(t("Подключить", "Connect", "Ulash")) { pair() }, 0, WRAP, 1f)
            }, MATCH, WRAP, top = 10)
            c.add(ctx.card(pad = 16) {
                add(ctx.text(t("Как настроить на компьютере", "How to set up on the computer", "Kompyuterda qanday sozlanadi"), T.Title), MATCH, WRAP)
                add(ctx.text(t("1. Запустите мост:", "1. Start the bridge:", "1. Ko'prikni ishga tushiring:"), T.Body), MATCH, WRAP, top = 8)
                add(ctx.text("node agent-bridge/bridge.mjs", T.Body).apply { setTextIsSelectable(true); typeface = android.graphics.Typeface.MONOSPACE }, MATCH, WRAP)
                add(ctx.text(t("2. Скопируйте ссылку, которую он напечатает, и вставьте её сюда.", "2. Copy the link it prints and paste it here.", "2. U chop etgan havolani nusxalab, shu yerga qo'ying."), T.Body), MATCH, WRAP, top = 8)
                add(ctx.text(t("3. Подключите хуки Claude Code:", "3. Install the Claude Code hooks:", "3. Claude Code hooklarini ulang:"), T.Body), MATCH, WRAP, top = 8)
                add(ctx.text("node agent-bridge/install-hooks.mjs", T.Body).apply { setTextIsSelectable(true); typeface = android.graphics.Typeface.MONOSPACE }, MATCH, WRAP)
            }, MATCH, WRAP, top = 14)
        }
        c.add(ctx.text(t("Разрешить можно только после проверки экрана блокировки", "You can allow only after the lock screen check", "Ruxsat faqat qulf ekrani tekshiruvidan keyin beriladi"), T.Small)
            .apply { gravity = Gravity.CENTER }, MATCH, WRAP, top = 16)
    }
}
