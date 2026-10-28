package com.example.buddy.ui

import android.content.ClipboardManager
import android.content.Context
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.ProgressBar
import com.example.buddy.AgentPairing
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
    private fun live(): Any = listOf(BuddyService.agentDesktop, BuddyService.agentConnected, BuddyService.agentSessions, BuddyService.agentUsage)

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
        // hero
        c.add(ctx.card(pad = 18, fill = P.accentContainer(java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY), 0), stroke = 0, radius = 28f) {
            add(ctx.row {
                add(BuddyView(ctx).apply {
                    scaleF = 1.25f
                    species = Backend.str(ctx, "species", "cat")
                    outfit = Outfits.parse(Backend.str(ctx, "outfit", ""))
                    mood = AgentWidgets.mascotMood(st)
                    setActive(!act.intent.getBooleanExtra("static", false))
                }, WRAP, WRAP, end = 8)
                add(ctx.col {
                    add(ctx.row {
                        add(View(ctx).apply { background = ctx.rounded(color, 6f) }, ctx.dp(11), ctx.dp(11), end = 8)
                        add(ctx.text(AgentWidgets.title(ctx, st), T.Headline).apply { textSize = 24f }, WRAP, WRAP)
                    })
                    add(ctx.text(s.desktop.ifEmpty { t("Компьютер", "Computer", "Kompyuter") } + if (s.connected) "" else " · " + t("нет связи", "offline", "aloqa yo'q"), T.Body), MATCH, WRAP, top = 4)
                    val busy = s.sessions.count { it.state in setOf("working", "thinking", "searching") }
                    add(ctx.text(if (s.sessions.isEmpty()) t("Агентов нет", "No agents running", "Agentlar yo'q")
                        else t("Работает: $busy из ${s.sessions.size}", "Working: $busy of ${s.sessions.size}", "Ishlayapti: $busy / ${s.sessions.size}"), T.Small), MATCH, WRAP, top = 2)
                }, 0, WRAP, 1f)
            })
        }, MATCH, WRAP, bottom = 12)

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
        }, MATCH, WRAP, bottom = 8)
        c.add(ctx.button(t("Отключить", "Disconnect", "Uzish"), BtnStyle.Outline) { unpair() }, MATCH, WRAP, top = 8)
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

    /** `--ez demo true` (CI screenshots): shows the dashboard with made-up numbers. */
    private fun demo(): AgentInfo? {
        if (!act.intent.getBooleanExtra("demo", false)) return null
        BuddyService.agentConnected = true; BuddyService.agentDesktop = "Work laptop"
        val now = System.currentTimeMillis()
        BuddyService.agentSessions = listOf(
            AgentSession("a", "Claude Code", "working", "Editing UiActivity.kt", 3, 8),
            AgentSession("b", "Claude Code", "approval", "Run: gradle assembleRelease", 0, 0),
            AgentSession("c", "Claude Code", "idle", "", 0, 0)
        )
        BuddyService.agentUsage = com.example.buddy.Usage(0.68f, now + 130 * 60_000L, 0.42f, now + 4 * 86_400_000L, 4, 182_400L, "max", now)
        return AgentInfo(true, "Work laptop", true, BuddyService.agentSessions)
    }

    override fun render(c: LinearLayout) {
        shownLive = live()
        val s = demo() ?: Backend.agentStatus(ctx)
        if (s.paired) {
            discovery?.stop(); discovery = null
            dashboard(c, s)
        } else {
            c.add(ctx.card(pad = 16, fill = P.accentContainer(java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY), 0)) {
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
