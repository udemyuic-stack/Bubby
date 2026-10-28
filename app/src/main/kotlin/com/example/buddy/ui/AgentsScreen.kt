package com.example.buddy.ui

import android.content.ClipboardManager
import android.content.Context
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.ProgressBar
import com.example.buddy.AgentFile
import com.example.buddy.AgentPairing
import com.example.buddy.AgentProto
import com.example.buddy.AgentQuestion
import com.example.buddy.AgentSession
import com.example.buddy.AgentStats
import com.example.buddy.AgentWidgets
import com.example.buddy.BuddyView
import com.example.buddy.FoundComputer
import com.example.buddy.Outfits
import com.example.buddy.BuddyService
import com.example.buddy.R

class AgentsScreen(act: UiActivity) : Screen(act) {
    override fun title() = t("Агенты", "Agents", "Agentlar")
    private var polling = false
    private var discovery: AgentPairing.Discovery? = null
    private var nearby: List<FoundComputer> = emptyList()
    private var showOld = false
    private var pairing = false
    private val shown = HashMap<String, Int>()   // progress already drawn per session, so a refresh only animates the change
    private val linkField by lazy { ctx.field(t("Ссылка для подключения", "Connection link", "Ulanish havolasi")).apply { setSingleLine(); hint = "coucou://pair?..." } }

    /** What the page last showed of the live link state; the poll redraws only when it changes. */
    private var shownLive: Any? = null
    private fun live(): Any = listOf(BuddyService.agentDesktop, BuddyService.agentConnected, BuddyService.agentSessions, BuddyService.agentUsage, BuddyService.agentApprovals,
        BuddyService.agentQuestions, BuddyService.agentCaps)
    private val openSteps = HashSet<String>()     // sessions whose step list is unfolded
    private val allFiles = HashSet<String>()      // sessions showing every changed file, not only the first few
    private val qSel = HashMap<String, List<Set<Int>>>()   // picks so far per question fingerprint (kept across redraws)
    private var demoDiffShown = false

    override fun onShow() {
        reshow()
        if (polling) return
        polling = true
        val tick = object : Runnable {
            override fun run() {
                // gone, or the app went to the background: stop (onShow starts it again on return)
                if (!content.isAttachedToWindow || content.windowVisibility != View.VISIBLE) { polling = false; return }
                // the pairing check decrypts the token through the Keystore: only done when the live state really changed
                if (live() != shownLive && Backend.agentStatus(ctx).paired) refresh()
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
        val asks = BuddyService.agentQuestions.any { it.pillId == a.pillId }
        val (label, color) = stateInfo(if (asks) "question" else a.state)
        val name = a.agent.ifEmpty { "Claude Code" }
        val tint = AgentWidgets.agentColor(name)
        return ctx.card(pad = 14, stroke = P.alpha(tint, 110)) {
            add(ctx.row {
                // the agent's own colour and initial, so several agents are told apart at a glance
                add(ctx.row(Gravity.CENTER) {
                    background = ctx.rounded(P.alpha(tint, 60), 14f, tint)
                    add(ctx.text(name.take(1).uppercase(), T.Title, tint), WRAP, WRAP)
                }, ctx.dp(38), ctx.dp(38), end = 10)
                add(ctx.col {
                    add(ctx.text(name, T.Title).apply { maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END }, MATCH, WRAP)
                    if (a.project.isNotEmpty()) add(ctx.text("▸ " + a.project, T.Small).apply { maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END }, MATCH, WRAP, top = 1)
                }, 0, WRAP, 1f, end = 8)
                add(ctx.pill(label, color), WRAP, WRAP)
            })
            if (a.statusText.isNotEmpty()) add(ctx.text(a.statusText, T.Body), MATCH, WRAP, top = 8)
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
            if (a.finalLine.isNotEmpty() && a.state in setOf("finished", "idle", "sleeping", "error")) add(ctx.row(Gravity.TOP) {
                background = ctx.rounded(P.alpha(P.success, 28), 12f)
                setPadding(ctx.dp(10), ctx.dp(8), ctx.dp(10), ctx.dp(8))
                add(ctx.icon(R.drawable.ic_check_circle, P.success, 18), WRAP, WRAP, end = 8)
                add(ctx.text(a.finalLine, T.Body).apply { maxLines = 4; ellipsize = android.text.TextUtils.TruncateAt.END }, 0, WRAP, 1f)
            }, MATCH, WRAP, top = 10)
            if (a.steps.isNotEmpty()) {
                val unfolded = a.pillId in openSteps
                add(ctx.row {
                    setPadding(0, ctx.dp(6), 0, ctx.dp(2))
                    add(ctx.text(t("Последние шаги: ${a.steps.size}", "Last steps: ${a.steps.size}", "Oxirgi qadamlar: ${a.steps.size}"), T.Label, P.primary), 0, WRAP, 1f)
                    add(ctx.icon(if (unfolded) R.drawable.ic_arrow_up else R.drawable.ic_arrow_down, P.primary, 20), WRAP, WRAP)
                    press { if (!openSteps.add(a.pillId)) openSteps.remove(a.pillId); refresh() }
                }, MATCH, WRAP, top = 6)
                if (unfolded) add(ctx.col {
                    for ((k, st) in a.steps.withIndex()) add(ctx.row(Gravity.TOP) {
                        val last = k == a.steps.size - 1
                        add(View(ctx).apply { background = ctx.rounded(if (last) tint else P.outline, 4f) }, ctx.dp(7), ctx.dp(7), end = 10, top = 7)
                        add(ctx.text(st, T.Small, if (last) P.onSurface else P.onSurfaceVariant).apply { maxLines = 3; ellipsize = android.text.TextUtils.TruncateAt.END }, 0, WRAP, 1f)
                    }, MATCH, WRAP, top = 3)
                }, MATCH, WRAP, top = 2)
            }
            if (a.files.isNotEmpty()) {
                add(ctx.text(t("Изменённые файлы: ${a.files.size}", "Files changed: ${a.files.size}", "O'zgargan fayllar: ${a.files.size}"), T.Label), MATCH, WRAP, top = 10)
                val all = a.pillId in allFiles
                for (f in if (all) a.files else a.files.take(4)) add(fileRow(a.pillId, f), MATCH, WRAP, top = 4)
                if (a.files.size > 4) add(ctx.button(if (all) t("Свернуть", "Show fewer", "Kamroq") else t("Все файлы (${a.files.size})", "All files (${a.files.size})", "Barcha fayllar (${a.files.size})"), BtnStyle.Text) {
                    if (!allFiles.add(a.pillId)) allFiles.remove(a.pillId); refresh()
                }, WRAP, WRAP)
            }
        }
    }

    /** One changed file: name, +added -removed, new / too large; a tap opens its lines (when the computer offers them). */
    private fun fileRow(pill: String, f: AgentFile): View {
        val canOpen = !f.tooLarge && ("diffs" in BuddyService.agentCaps || demoOn())
        val green = if (P.dark) 0xFF7EE2A1.toInt() else 0xFF1C7C3E.toInt()
        val red = if (P.dark) 0xFFFF9A93.toInt() else 0xFFB3261E.toInt()
        return ctx.row {
            background = ctx.rounded(P.container, 12f)
            setPadding(ctx.dp(10), ctx.dp(8), ctx.dp(8), ctx.dp(8))
            add(ctx.text(f.name, T.Body).apply { typeface = android.graphics.Typeface.MONOSPACE; textSize = 13f; maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.MIDDLE }, 0, WRAP, 1f, end = 6)
            if (f.isNew) add(badge(ctx, t("новый", "new", "yangi"), P.sky), WRAP, WRAP, end = 6)
            if (f.tooLarge) add(badge(ctx, t("очень большой", "too large", "juda katta"), P.orange), WRAP, WRAP, end = 6)
            add(ctx.text("+${f.added}", T.Label, green).apply { textSize = 13f }, WRAP, WRAP, end = 6)
            add(ctx.text("−${f.removed}", T.Label, red).apply { textSize = 13f }, WRAP, WRAP)
            if (canOpen) {
                add(ctx.icon(R.drawable.ic_chevron_right, P.onSurfaceVariant, 18), WRAP, WRAP, start = 2)
                press { DiffSheet(act, pill, f, if (demoOn()) demoDiff(f) else null).show() }
            }
        }
    }

    /** A question from Claude Code: chips per question, Send only when every question has its picks, then the lock screen. */
    private fun questionCard(q: AgentQuestion): View {
        val holder = ctx.col()
        fun build() {
            holder.removeAllViews()
            val sel = qSel[q.fingerprint]?.takeIf { it.size == q.items.size } ?: AgentProto.emptySelection(q)
            val who = BuddyService.agentSessions.firstOrNull { it.pillId == q.pillId }
            holder.add(ctx.card(pad = 16, fill = P.alpha(P.sky, 34), stroke = P.sky) {
                add(ctx.row {
                    add(ctx.icon(R.drawable.ic_info, P.sky, 24), WRAP, WRAP, end = 10)
                    add(ctx.col {
                        add(ctx.text(t("У Claude вопрос", "Claude has a question", "Claude'da savol bor"), T.Title), MATCH, WRAP)
                        val sub = listOfNotNull(who?.agent?.ifEmpty { null }, who?.project?.ifEmpty { null }).joinToString(" · ")
                        if (sub.isNotEmpty()) add(ctx.text(sub, T.Small), MATCH, WRAP)
                    }, 0, WRAP, 1f)
                })
                for ((qi, item) in q.items.withIndex()) {
                    add(ctx.text(item.question, T.Body), MATCH, WRAP, top = if (qi == 0) 10 else 14)
                    add(ctx.text(if (item.multiSelect) t("Можно выбрать несколько", "Pick one or more", "Bir nechtasini tanlash mumkin") else t("Выберите один вариант", "Pick one", "Bittasini tanlang"), T.Small), MATCH, WRAP, top = 2)
                    val flow = FlowLayout(ctx, 8)
                    for ((oi, op) in item.options.withIndex()) flow.addView(ctx.chip(op.shown, oi in sel[qi]) {
                        qSel[q.fingerprint] = AgentProto.toggle(q, sel, qi, oi); build()
                    })
                    add(flow, MATCH, WRAP, top = 6)
                    for (oi in sel[qi].sorted()) item.options.getOrNull(oi)?.takeIf { it.description.isNotEmpty() }?.let {
                        add(ctx.text(it.shown + " — " + it.description, T.Small).apply { maxLines = 4; ellipsize = android.text.TextUtils.TruncateAt.END }, MATCH, WRAP, top = 4)
                    }
                }
                val picks = AgentProto.picksFor(q, sel)
                add(ctx.row {
                    add(ctx.button(t("На компьютере", "On the computer", "Kompyuterda"), BtnStyle.Outline) { later(q.fingerprint) }, 0, WRAP, 1f, end = 8)
                    add(ctx.button(t("Отправить", "Send", "Yuborish")) {
                        val p = AgentProto.picksFor(q, qSel[q.fingerprint] ?: return@button) ?: return@button
                        com.example.buddy.ConfirmActivity.requestAnswer(ctx) { ok -> if (ok) answer(q.fingerprint, p) else ctx.toast(t("Нужен экран блокировки", "Lock screen required", "Ekran qulfi kerak")) }
                    }.apply { alpha = if (picks != null) 1f else 0.4f; isEnabled = picks != null }, 0, WRAP, 1f)
                }, MATCH, WRAP, top = 12)
                add(ctx.text(t("Ответ уйдёт только после проверки экрана блокировки", "The answer is sent only after the lock screen check", "Javob faqat qulf ekrani tekshiruvidan keyin yuboriladi"), T.Small), MATCH, WRAP, top = 8)
            }, MATCH, WRAP)
        }
        build()
        return holder
    }

    private fun answer(fp: String, picks: List<List<String>>) {
        if (demoOn()) { ctx.toast(t("Демо: ответ не отправлен", "Demo: nothing was sent", "Demo: hech narsa yuborilmadi")); return }
        val json = org.json.JSONArray().apply { for (p in picks) put(org.json.JSONArray(p)) }.toString()
        ctx.startService(android.content.Intent(ctx, BuddyService::class.java).setAction(BuddyService.ACTION_AGENT_ANSWER).putExtra("fp", fp).putExtra("picks", json))
        qSel.remove(fp)
        content.postDelayed({ refresh() }, 300)
    }

    private fun later(fp: String) {
        if (demoOn()) return
        ctx.startService(android.content.Intent(ctx, BuddyService::class.java).setAction(BuddyService.ACTION_AGENT_LATER).putExtra("fp", fp))
        qSel.remove(fp)
        content.postDelayed({ refresh() }, 300)
    }

    private fun startDiscovery() {
        if (discovery != null) return
        discovery = AgentPairing.Discovery(ctx) { list -> nearby = list; if (!pairing) refresh() }.also { it.start() }
        content.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: View) {}
            override fun onViewDetachedFromWindow(v: View) { discovery?.stop(); discovery = null }
        })
    }

    private fun failText(code: String?) = when (code) {
        "bad_code" -> t("Код не подошёл. На компьютере появился новый — введите его", "That code did not work. The computer shows a new one: type it", "Kod mos kelmadi. Kompyuterda yangisi chiqdi: shuni kiriting")
        "slow_down" -> t("Слишком много попыток, подождите минуту", "Too many tries, wait a minute", "Urinishlar ko'p, bir daqiqa kuting")
        "not_pairing" -> t("Этот компьютер уже связан с телефоном. Запустите на нём мост с ключом --new-pairing", "This computer is already paired with a phone. Run the bridge with --new-pairing", "Bu kompyuter telefon bilan ulangan. Ko'prikni --new-pairing bilan ishga tushiring")
        "fingerprint" -> t("Это не тот компьютер (сертификат другой)", "This is not that computer (different certificate)", "Bu o'sha kompyuter emas (sertifikat boshqa)")
        "network" -> t("Не удалось связаться. Телефон и компьютер в одной сети?", "Could not reach it. Are the phone and the computer on the same network?", "Ulanib bo'lmadi. Telefon va kompyuter bitta tarmoqdami?")
        else -> t("Не получилось подключиться", "Could not connect", "Ulanib bo'lmadi")
    }

    /** Asks for the 6 digits shown on the computer, then pairs. [host]/[port] may come from discovery or from typing. */
    private fun askCode(name: String, host: String, port: Int, fp: String?) {
        val code = ctx.field(t("6 цифр с компьютера", "6 digits from the computer", "Kompyuterdagi 6 raqam"), number = true).apply {
            filters = arrayOf(android.text.InputFilter.LengthFilter(6)); textSize = 26f; gravity = Gravity.CENTER; letterSpacing = 0.3f
        }
        val form = ctx.col(20) {
            add(ctx.text(t("Введите код, который показан в терминале на «$name».", "Type the code shown in the terminal on “$name”.", "«$name» terminalida ko'rsatilgan kodni kiriting."), T.Body), MATCH, WRAP, bottom = 12)
            add(code, MATCH, WRAP)
        }
        val dlg = ctx.dialogBuilder().setTitle(t("Подключение", "Connect", "Ulanish")).setView(form)
            .setNegativeButton(t("Отмена", "Cancel", "Bekor qilish"), null)
            .setPositiveButton(t("Подключить", "Connect", "Ulash"), null).create()
        dlg.setOnShowListener {
            code.requestFocus()
            dlg.getButton(android.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener { btn ->
                val digits = code.text.toString().filter { it.isDigit() }
                if (digits.length != 6) { ctx.toast(t("Нужно 6 цифр", "6 digits needed", "6 ta raqam kerak")); return@setOnClickListener }
                btn.isEnabled = false; pairing = true
                Thread {
                    val r = AgentPairing.pair(host, port, digits, fp, name)
                    act.runOnUiThread {
                        pairing = false
                        val link = r.link
                        if (link != null) {
                            val err = Backend.agentSave(ctx, link)
                            if (err != null) ctx.toast(err) else { dlg.dismiss(); act.confetti(); refresh() }
                        } else { ctx.toast(failText(r.error)); btn.isEnabled = true; code.setText("") }
                    }
                }.start()
            }
        }
        dlg.show()
    }

    private fun askAddress() {
        val addr = ctx.field(t("Адрес, например 192.168.1.5", "Address, for example 192.168.1.5", "Manzil, masalan 192.168.1.5")).apply { setSingleLine() }
        val form = ctx.col(20) { add(addr, MATCH, WRAP) }
        val dlg = ctx.dialogBuilder().setTitle(t("Адрес компьютера", "Computer address", "Kompyuter manzili")).setView(form)
            .setNegativeButton(t("Отмена", "Cancel", "Bekor qilish"), null)
            .setPositiveButton(t("Дальше", "Next", "Keyingisi"), null).create()
        dlg.setOnShowListener {
            dlg.getButton(android.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val hp = AgentPairing.parseAddress(addr.text.toString())
                if (hp == null) { ctx.toast(t("Неверный адрес", "Invalid address", "Manzil noto'g'ri")); return@setOnClickListener }
                dlg.dismiss(); askCode(hp.first, hp.first, hp.second, null)
            }
        }
        dlg.show()
    }

    private fun pairOld() {
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

    // ---- paired: the dashboard ----

    private fun statCard(title: String, sub: String, body: LinearLayout.() -> Unit): LinearLayout = ctx.card(pad = 14) {
        gravity = Gravity.CENTER_HORIZONTAL
        body()
        add(ctx.text(title, T.Title).apply { gravity = Gravity.CENTER; textSize = 14.5f }, MATCH, WRAP, top = 6)
        if (sub.isNotEmpty()) add(ctx.text(sub, T.Small).apply { gravity = Gravity.CENTER }, MATCH, WRAP, top = 1)
    }

    private fun dashboard(c: LinearLayout, s: AgentInfo) {
        val st = AgentWidgets.overall()
        val color = AgentWidgets.color(st)
        // every agent as a small coloured cube around the mascot
        if (s.sessions.isNotEmpty()) {
            val seen = LinkedHashMap<String, AgentOrbit.Item>()
            for (x in s.sessions) {
                val nm = x.agent.ifEmpty { "Claude Code" }
                if (nm !in seen && seen.size < 9) seen[nm] = AgentOrbit.Item(nm, AgentWidgets.agentColor(nm), x.state)
            }
            c.add(ctx.card(pad = 4) { add(AgentOrbit(ctx, seen.values.toList()), MATCH, ctx.dp(300)) }, MATCH, WRAP, bottom = 12)
        }
        // hero
        c.add(ctx.card(pad = 18, fill = P.primaryContainer, stroke = 0, radius = 28f) {
            add(ctx.row {
                add(ctx.mascotStage(96, 70), ctx.dp(96), (ctx.dp(96) * 0.9f).toInt(), end = 8)
                add(ctx.col {
                    add(ctx.row {
                        add(View(ctx).apply { background = ctx.rounded(color, 6f) }, ctx.dp(11), ctx.dp(11), end = 8)
                        add(ctx.text(AgentWidgets.title(ctx, st), T.Headline).apply { textSize = 21f }, 0, WRAP, 1f)
                    })
                    add(ctx.text(s.desktop.ifEmpty { t("Компьютер", "Computer", "Kompyuter") } + if (s.connected) "" else " · " + t("нет связи", "offline", "aloqa yo'q"), T.Body), MATCH, WRAP, top = 4)
                    val busy = s.sessions.count { it.state in setOf("working", "thinking", "searching") }
                    add(ctx.text(if (s.sessions.isEmpty()) t("Агентов нет", "No agents running", "Agentlar yo'q")
                        else t("Работает: $busy из ${s.sessions.size}", "Working: $busy of ${s.sessions.size}", "Ishlayapti: $busy / ${s.sessions.size}"), T.Small), MATCH, WRAP, top = 2)
                }, 0, WRAP, 1f)
            })
        }, MATCH, WRAP, bottom = 12)

        // requests waiting for an answer
        for (ap in BuddyService.agentApprovals) c.add(ctx.card(pad = 16, fill = P.warning.let { P.alpha(it, 40) }, stroke = P.warning) {
            add(ctx.row {
                add(ctx.icon(R.drawable.ic_warning, P.warning, 24), WRAP, WRAP, end = 10)
                add(ctx.text(t("Ждёт вашего ответа", "Waiting for you", "Javobingizni kutyapti"), T.Title), 0, WRAP, 1f)
            })
            add(ctx.text(ap.tool, T.Label), MATCH, WRAP, top = 8)
            if (ap.command.isNotEmpty()) add(ctx.text(ap.command, T.Body).apply { typeface = android.graphics.Typeface.MONOSPACE; textSize = 13f; maxLines = 6; ellipsize = android.text.TextUtils.TruncateAt.END
                background = ctx.rounded(P.container, 12f); setPadding(ctx.dp(10), ctx.dp(8), ctx.dp(10), ctx.dp(8)) }, MATCH, WRAP, top = 6)
            add(ctx.row {
                add(ctx.button(t("Отклонить", "Deny", "Rad etish"), BtnStyle.Outline) { decide(ap.fingerprint, false) }, 0, WRAP, 1f, end = 8)
                if (!ap.truncated) add(ctx.button(t("Разрешить", "Allow", "Ruxsat berish")) {
                    // the lock screen (PIN, pattern, fingerprint) must confirm first
                    com.example.buddy.ConfirmActivity.request(ctx) { ok -> if (ok) decide(ap.fingerprint, true) else ctx.toast(t("Нужен экран блокировки", "Lock screen required", "Ekran qulfi kerak")) }
                }, 0, WRAP, 1f)
            }, MATCH, WRAP, top = 10)
            add(ctx.text(if (ap.truncated) t("Команда слишком длинная: разрешить можно только на компьютере", "The command is too long: it can only be allowed on the computer", "Buyruq juda uzun: ruxsatni faqat kompyuterda berish mumkin")
                else t("Разрешить можно только после проверки экрана блокировки", "You can allow only after the lock screen check", "Ruxsat faqat qulf ekrani tekshiruvidan keyin beriladi"), T.Small), MATCH, WRAP, top = 8)
        }, MATCH, WRAP, bottom = 12)

        // questions from Claude Code
        qSel.keys.retainAll(BuddyService.agentQuestions.map { it.fingerprint }.toSet())
        for (q in BuddyService.agentQuestions) c.add(questionCard(q), MATCH, WRAP, bottom = 12)

        // usage rings and done today
        val u = AgentStats.current(ctx)
        fun ringCard(value: Float?, resets: Long, title: String): LinearLayout = statCard(title,
            if (value == null) t("нет данных", "no data", "ma'lumot yo'q") else AgentWidgets.until(ctx, resets).let { r -> if (r.isEmpty()) "" else t("сброс через $r", "resets in $r", "$r dan keyin yangilanadi") }) {
            add(RingGauge(ctx).apply { setValue(value) }, ctx.dp(112), ctx.dp(112))
        }
        c.add(ctx.row(Gravity.TOP) {
            add(ringCard(u?.fiveHour, u?.fiveHourResets ?: 0L, "Claude · 5 h"), 0, WRAP, 1f, end = 6)
            add(ringCard(u?.sevenDay, u?.sevenDayResets ?: 0L, "Claude · 7 d"), 0, WRAP, 1f, start = 6)
        }, MATCH, WRAP, bottom = 12)
        c.add(ctx.row(Gravity.TOP) {
            add(statCard(t("сделано сегодня", "done today", "bugun bajarildi"), "") {
                add(ctx.text(u?.doneToday?.toString() ?: "—", T.Headline).apply { textSize = 44f; gravity = Gravity.CENTER; setTextColor(P.success) }, MATCH, WRAP)
            }, 0, WRAP, 1f, end = 6)
            add(statCard(t("токенов сегодня", "tokens today", "bugungi tokenlar"), u?.plan?.let { "Claude $it" } ?: "") {
                add(ctx.text(u?.tokensToday?.let { fmtTokens(it) } ?: "—", T.Headline).apply { textSize = 32f; gravity = Gravity.CENTER; setTextColor(P.violet) }, MATCH, WRAP, top = 6)
            }, 0, WRAP, 1f, start = 6)
        }, MATCH, WRAP, bottom = 12)

        // the agents
        c.add(ctx.section(if (s.sessions.isEmpty()) t("Агенты", "Agents", "Agentlar") else t("Агенты: ${s.sessions.size}", "Agents: ${s.sessions.size}", "Agentlar: ${s.sessions.size}"), R.drawable.ic_bolt))
        if (s.sessions.isEmpty()) c.add(ctx.card(pad = 16) { add(ctx.text(t("Сейчас никто не работает. Запустите Claude Code на компьютере.", "Nothing is running right now. Start Claude Code on the computer.", "Hozir hech kim ishlamayapti. Kompyuterda Claude Code'ni ishga tushiring."), T.Body), MATCH, WRAP) }, MATCH, WRAP, bottom = 10)
        for (a in s.sessions) c.add(sessionCard(a), MATCH, WRAP, bottom = 10)
        // widgets
        c.add(ctx.section(t("Виджеты на экране", "Home-screen widgets", "Bosh ekran vidjetlari"), R.drawable.ic_apps))
        c.add(ctx.card(pad = 14) {
            add(ctx.text(t("Удерживайте пустое место на главном экране → «Виджеты» → Bubby: статус, лимит Claude, сделано сегодня и список агентов.",
                "Long-press an empty spot on the home screen → Widgets → Bubby: status, Claude limit, done today and the agent list.",
                "Bosh ekranning bo'sh joyini bosib turing → «Vidjetlar» → Bubby: holat, Claude limiti, bugun bajarilgani va agentlar ro'yxati."), T.Body), MATCH, WRAP)
            add(ctx.row(Gravity.TOP) {
                for (k in listOf("status", "usage")) {
                    val iv = android.widget.ImageView(ctx).apply { setImageBitmap(try { AgentWidgets.preview(ctx, k, 150, 150) } catch (_: Throwable) { null }) }
                    add(iv, 0, ctx.dp(150), 1f, end = 6, start = 6)
                }
            }, MATCH, WRAP, top = 10)
            for ((k, hh) in listOf("bar" to 56, "agents" to 150)) {
                val iv = android.widget.ImageView(ctx).apply { setImageBitmap(try { AgentWidgets.preview(ctx, k, 320, hh) } catch (_: Throwable) { null }) }
                add(iv, MATCH, ctx.dp(hh), top = 8)
            }
        }, MATCH, WRAP, bottom = 8)
        c.add(ctx.button(t("Отключить", "Disconnect", "Uzish"), BtnStyle.Outline) { unpair() }, MATCH, WRAP, top = 8)
    }

    private fun decide(fp: String, allow: Boolean) {
        ctx.startService(android.content.Intent(ctx, BuddyService::class.java).setAction(BuddyService.ACTION_AGENT_DECIDE).putExtra("fp", fp).putExtra("allow", allow))
        content.postDelayed({ refresh() }, 300)
    }

    private fun fmtTokens(n: Long): String = when { n >= 1_000_000 -> "%.1fM".format(n / 1_000_000.0); n >= 1_000 -> "%.1fk".format(n / 1_000.0); else -> n.toString() }

    // ---- not paired: find the computer ----

    private fun connect(c: LinearLayout) {
        startDiscovery()
        c.add(ctx.section(t("Компьютеры рядом", "Computers nearby", "Yaqin kompyuterlar"), R.drawable.ic_wifi))
        if (nearby.isEmpty()) c.add(ctx.card(pad = 16) {
            add(ctx.text(t("Ищу в этой сети… Запустите на компьютере команду ниже.", "Looking on this network… Run the command below on the computer.", "Shu tarmoqda qidiryapman… Kompyuterda quyidagi buyruqni ishga tushiring."), T.Body), MATCH, WRAP)
        }, MATCH, WRAP, bottom = 10)
        for (f in nearby) c.add(ctx.card(pad = 14, onClick = { askCode(f.name, f.host, f.port, f.fp) }) {
            add(ctx.row {
                add(ctx.row(Gravity.CENTER) {
                    background = ctx.rounded(P.primaryContainer, 14f)
                    add(ctx.icon(R.drawable.ic_wifi, P.onPrimaryContainer, 22), WRAP, WRAP)
                }, ctx.dp(44), ctx.dp(44), end = 12)
                add(ctx.col {
                    add(ctx.text(f.name, T.Title), MATCH, WRAP)
                    add(ctx.text("${f.host}:${f.port}" + if (f.paired) " · " + t("уже связан с телефоном", "already paired with a phone", "telefon bilan ulangan") else "", T.Small), MATCH, WRAP)
                }, 0, WRAP, 1f)
                add(ctx.icon(R.drawable.ic_chevron_right, P.onSurfaceVariant, 22), WRAP, WRAP)
            })
        }, MATCH, WRAP, bottom = 10)

        c.add(ctx.card(pad = 16) {
            add(ctx.text(t("Как подключить", "How to connect", "Qanday ulanadi"), T.Title), MATCH, WRAP)
            add(ctx.text(t("1. На компьютере выполните одну команду:", "1. On the computer run one command:", "1. Kompyuterda bitta buyruqni bajaring:"), T.Body), MATCH, WRAP, top = 8)
            add(ctx.text("node agent-bridge/bridge.mjs", T.Body).apply { setTextIsSelectable(true); typeface = android.graphics.Typeface.MONOSPACE }, MATCH, WRAP, top = 2)
            add(ctx.text(t("2. Нажмите здесь на свой компьютер.", "2. Tap your computer here.", "2. Shu yerda kompyuteringizni bosing."), T.Body), MATCH, WRAP, top = 8)
            add(ctx.text(t("3. Введите 6 цифр, которые показаны в терминале.", "3. Type the 6 digits shown in the terminal.", "3. Terminalda ko'rsatilgan 6 raqamni kiriting."), T.Body), MATCH, WRAP, top = 8)
        }, MATCH, WRAP, top = 4)

        c.add(ctx.row {
            add(ctx.button(t("Ввести адрес", "Enter address", "Manzil kiritish"), BtnStyle.Tonal, R.drawable.ic_edit) { askAddress() }, 0, WRAP, 1f, end = 8)
            add(ctx.button(if (showOld) t("Скрыть ссылку", "Hide link", "Havolani yashirish") else t("Есть ссылка", "I have a link", "Havolam bor"), BtnStyle.Text, R.drawable.ic_link) { showOld = !showOld; refresh() }, 0, WRAP, 1f)
        }, MATCH, WRAP, top = 12)
        if (showOld) {
            (linkField.parent as? android.view.ViewGroup)?.removeView(linkField)
            c.add(linkField, MATCH, WRAP, top = 8)
            c.add(ctx.row {
                add(ctx.button(t("Вставить", "Paste", "Qo'yish"), BtnStyle.Tonal) {
                    val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    val txt = cm.primaryClip?.getItemAt(0)?.coerceToText(ctx)?.toString()?.trim().orEmpty()
                    if (txt.isNotEmpty()) linkField.setText(txt)
                }, 0, WRAP, 1f, end = 8)
                add(ctx.button(t("Подключить", "Connect", "Ulash")) { pairOld() }, 0, WRAP, 1f)
            }, MATCH, WRAP, top = 8)
        }
    }

    private fun demoOn() = act.intent.getBooleanExtra("demo", false)

    /**
     * `--ez demo true` (CI screenshots): the dashboard with made-up numbers, several agents with details and changed files,
     * a permission request and a question. `--ez demodiff true` also opens the lines of the first file.
     */
    private fun demo(): AgentInfo? {
        if (!demoOn()) return null
        BuddyService.agentConnected = true; BuddyService.agentDesktop = "Work laptop"
        BuddyService.agentCaps = AgentProto.CAPS.toSet()
        val now = System.currentTimeMillis()
        BuddyService.agentSessions = listOf(
            AgentSession("a", "Claude Code", "working", "Editing UiActivity.kt", 3, 8, "bubby",
                listOf("Read AgentLink.kt", "Searched for \"caps\"", "Edited AgentsScreen.kt", "Running the JVM tests"), "",
                listOf(AgentFile("1", 1L, "AgentsScreen.kt", 42, 7), AgentFile("2", 2L, "AgentProto.kt", 310, 0, isNew = true),
                    AgentFile("3", 3L, "strings-big.json", 4200, 3900, tooLarge = true))),
            AgentSession("q", "Claude Code", "question", "Choosing the database", 1, 4, "shop-api"),
            AgentSession("b", "codex", "approval", "Run: gradle assembleRelease", 0, 0, "bubby"),
            AgentSession("c", "gemini", "finished", "", 5, 5, "landing-page", listOf("Wrote index.html", "Checked the links"),
                "Done: the landing page builds and every link works.", listOf(AgentFile("9", 9L, "index.html", 18, 4)))
        )
        if ("a" !in openSteps && !act.intent.getBooleanExtra("demofold", false)) openSteps.add("a")
        BuddyService.agentApprovals = listOf(com.example.buddy.Approval("b", "fp1", "Bash", "gradle assembleRelease -Pbuddy.code=45", now, false))
        BuddyService.agentQuestions = listOf(AgentQuestion("q", "fpq", now, listOf(
            com.example.buddy.QItem("Which database should the shop use?", false, listOf(
                com.example.buddy.QOption("PostgreSQL", "PostgreSQL", "Full SQL server, best for many users"),
                com.example.buddy.QOption("SQLite", "SQLite", "One file, nothing to install"))),
            com.example.buddy.QItem("Which tests should I add?", true, listOf(
                com.example.buddy.QOption("Unit", "Unit", ""), com.example.buddy.QOption("API", "API", ""), com.example.buddy.QOption("End to end", "End to end", "")))
        )))
        if (qSel["fpq"] == null) qSel["fpq"] = listOf(setOf(0), emptySet())
        BuddyService.agentUsage = com.example.buddy.Usage(0.68f, now + 130 * 60_000L, 0.42f, now + 4 * 86_400_000L, 4, 182_400L, "max", now)
        if (act.intent.getBooleanExtra("demodiff", false) && !demoDiffShown) {
            demoDiffShown = true
            val f = BuddyService.agentSessions[0].files[0]
            content.post { DiffSheet(act, "a", f, demoDiff(f)).show() }
        }
        return AgentInfo(true, "Work laptop", true, BuddyService.agentSessions)
    }

    /** Made-up lines for the demo sheet, sent in two parts like the bridge does. */
    private fun demoDiff(f: AgentFile): List<com.example.buddy.DiffPart> {
        fun l(k: Char, s: String) = com.example.buddy.DiffLine(k, s)
        val a = listOf(l('@', "@@ 64"), l(' ', "    private fun sessionCard(a: AgentSession): LinearLayout {"),
            l('-', "        val (label, color) = stateInfo(a.state)"),
            l('+', "        val asks = BuddyService.agentQuestions.any { it.pillId == a.pillId }"),
            l('+', "        val (label, color) = stateInfo(if (asks) \"question\" else a.state)"),
            l('+', "        val tint = AgentWidgets.agentColor(a.agent)"), l(' ', "        return ctx.card(pad = 14) {"))
        val b = listOf(l('@', "@@ 120"), l(' ', "            if (a.steps.isNotEmpty()) {"), l('-', "                add(old)"),
            l('+', "                add(ctx.text(t(\"Последние шаги\", \"Last steps\", \"Oxirgi qadamlar\"), T.Label))"), l(' ', "            }"))
        return listOf(
            com.example.buddy.DiffPart("a", f.id, f.name, f.added, f.removed, false, false, false, 1, 2, b),
            com.example.buddy.DiffPart("a", f.id, f.name, f.added, f.removed, false, false, false, 0, 2, a))
    }

    companion object {
        /** A small coloured word next to a file ("new", "too large"). */
        fun badge(ctx: Context, s: String, color: Int): View = ctx.text(s, T.Small, color).apply {
            textSize = 11f; setPadding(ctx.dp(7), ctx.dp(1), ctx.dp(7), ctx.dp(2))
            background = ctx.rounded(P.alpha(color, 40), 8f)
        }
    }

    override fun render(c: LinearLayout) {
        shownLive = live()
        val s = demo() ?: Backend.agentStatus(ctx)
        if (s.paired) {
            discovery?.stop(); discovery = null
            dashboard(c, s)
        } else {
            c.add(ctx.card(pad = 16, fill = P.primaryContainer) {
                add(ctx.text(t(
                    "Следите за Claude Code на компьютере и разрешайте или отклоняйте его запросы с телефона. Пока вы не подтвердите, ничего не выполняется.",
                    "Keep an eye on Claude Code on your computer and allow or decline its requests from your phone. Nothing runs until you confirm.",
                    "Kompyuterdagi Claude Code ishini kuzating va uning so'rovlariga telefondan ruxsat bering yoki rad eting. Siz tasdiqlamaguncha hech narsa bajarilmaydi."), T.Body), MATCH, WRAP)
            }, MATCH, WRAP, bottom = 6)
            connect(c)
        }
        c.add(ctx.text(t("Разрешить можно только после проверки экрана блокировки", "You can allow only after the lock screen check", "Ruxsat faqat qulf ekrani tekshiruvidan keyin beriladi"), T.Small)
            .apply { gravity = Gravity.CENTER }, MATCH, WRAP, top = 16)
    }
}
