package com.example.buddy.ui

import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import com.example.buddy.Tr
import com.example.buddy.BuddyView
import com.example.buddy.Outfits
import com.example.buddy.R
import java.util.Calendar

/** Blocking set (plus a non-empty AI key). Everything else is optional. */
private val REQUIRED = listOf("mic", "overlay")

class HomeScreen(act: UiActivity) : Screen(act) {
    private var busy = false
    private var micPoked = false
    private var firstShow = true

    override val padH = 0
    override fun title() = "Bubby"
    override fun onShow() { reshow() }
    override fun onPermissionResult() { refresh() }

    private fun toggle() {
        if (busy) return
        val perms = Backend.permissions(act)
        if (!Backend.running() && perms["overlay"] != true) {
            ctx.toast(t("Сначала выдайте разрешение «Поверх других окон»", "First grant the \"Display over other apps\" permission", "Avval «Boshqa oynalar ustida ko'rsatish» ruxsatini bering"))
            act.open("setup")
            return
        }
        if (Backend.running()) Backend.stopBuddy(ctx)
        else Backend.startBuddy(ctx)?.let { ctx.toast(it); return }
        busy = true; refresh()
        content.postDelayed({ busy = false; refresh() }, 700)
    }

    private fun send(text: String) {
        val err = Backend.ask(ctx, text)
        ctx.toast(err ?: t("Отправил Bubby: $text", "Sent to Bubby: $text", "Bubby ga yuborildi: $text"))
    }

    override fun render(c: LinearLayout) {
        val perms = Backend.permissions(act)
        val hasAi = Backend.aiKeys(ctx).isNotEmpty()
        val running = Backend.running()
        val missing = !hasAi || REQUIRED.any { perms[it] != true }
        val granted = REQUIRED.count { perms[it] == true } + (if (hasAi) 1 else 0)
        // the microphone was granted after the floating service started: poke it so it re-declares the microphone type
        if (running && perms["mic"] == true && perms["overlay"] == true && !micPoked) { micPoked = true; Backend.startBuddy(ctx) }
        if (perms["mic"] != true) micPoked = false
        if (firstShow) { firstShow = false; if (missing && !act.intent.getBooleanExtra("static", false)) content.post { act.open("setup") } }

        // ---- the glass scene: island, mascot, core and the fan of petals (laid out on a 390-wide grid) ----
        val still = act.intent.getBooleanExtra("static", false)
        val sc = ctx.resources.displayMetrics.widthPixels / 390f
        val scene = DesignCanvas(ctx, 668).apply { clipChildren = false; clipToPadding = false }
        val mascot = MochiView(ctx)
        val nameV = ctx.text("Bubby", T.Headline, 0xFFF4F1EE.toInt()).apply {
            setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, 30f * sc); gravity = Gravity.CENTER
        }
        val statusV = ctx.row(Gravity.CENTER) {
            background = ctx.rounded(Glass.white(.14f), 20f, Glass.white(.3f))
            setPadding((14 * sc).toInt(), (6 * sc).toInt(), (14 * sc).toInt(), (6 * sc).toInt())
            add(DotView(ctx, if (running || Theme.cur.id != "glass") Glass.OK else 0xFFA3A9C9.toInt()), (9 * sc).toInt(), (9 * sc).toInt(), end = (8 * sc).toInt())
            add(ctx.text(if (running) t("Помощник работает", "Assistant is on", "Yordamchi ishlayapti") else t("Помощник выключен", "Assistant is off", "Yordamchi o'chiq"), T.Small, 0xFFF1ECEA.toInt())
                .apply { setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, 12f * sc) }, WRAP, WRAP)
        }
        val statusBox = FrameLayout(ctx).apply { addView(statusV, FrameLayout.LayoutParams(WRAP, WRAP, Gravity.CENTER_HORIZONTAL)) }
        val island = Island(ctx, sc, still, { txt -> send(txt) }) { open ->
            // with the island open the mascot steps back: smaller and lower (scale .68, top 134)
            val k = if (open) .68f else 1f
            mascot.pivotX = mascot.width / 2f; mascot.pivotY = 0f
            mascot.animate().scaleX(k).scaleY(k).translationY(if (open) 68f * sc else 0f).setDuration(220).start()
            nameV.animate().translationY(if (open) 24f * sc else 0f).setDuration(220).start()
            statusBox.animate().translationY(if (open) 24f * sc else 0f).setDuration(220).start()
        }
        scene.put(island, 0f, 0f, 390f, 134f)
        scene.put(mascot, 110f, 66f, 170f, 136f)
        scene.put(nameV, 0f, 204f, 390f, 40f)
        scene.put(statusBox, 0f, 250f, 390f, 32f)
        scene.put(CollarView(ctx, sc), 0f, 380f, 390f, 140f)
        fun petal(kind: GlassKind, icon: Int, label: String, l: Float, tp: Float, w: Float, h: Float, ang: Float, tc: Int, size: Int = 12, bold: Boolean = false, go: () -> Unit) {
            scene.put(PetalView(ctx, kind, icon, label, ang, tc, size, bold, sc).apply { press(go) }, l, tp, w, h)
        }
        petal(GlassKind.Mid, R.drawable.tg_hand, t("Проверить:\nпривет", "Test:\nhello", "Tekshirish:\nsalom"), 166f, 304f, 104f, 112f, -10f, 0xFFFFFFFF.toInt()) { send(t("Привет!", "Hello!", "Salom!")) }
        petal(GlassKind.Dim, R.drawable.tg_check, t("Задачи", "Tasks", "Vazifalar"), 44f, 459f, 104f, 112f, -28f, Glass.TEXT_LABEL) { act.showTab(1) }
        petal(GlassKind.Dim, R.drawable.tg_apps, t("Ещё", "More", "Yana"), 246f, 459f, 104f, 112f, 28f, Glass.TEXT_LABEL) { act.showTab(4) }
        petal(GlassKind.Dim, R.drawable.tg_cal, t("Календарь", "Calendar", "Kalendar"), 100f, 524f, 104f, 112f, -8f, Glass.TEXT_LABEL) { act.showTab(2) }
        petal(GlassKind.Dim, R.drawable.tg_gear, t("Настройки", "Settings", "Sozlamalar"), 184f, 524f, 104f, 112f, 8f, Glass.TEXT_LABEL) { act.showTab(3) }
        scene.put(CoreView(ctx, sc), 171f, 448f, 44f, 44f)
        petal(GlassKind.Active, if (running) R.drawable.tg_stop else R.drawable.tg_play, if (running) t("Остановить", "Stop", "To'xtatish") else t("Запустить", "Start", "Ishga tushirish"),
            237f, 379f, 116f, 106f, 12f, Glass.ON_ACTIVE, 13, true) { toggle() }
        scene.alpha = if (busy) 0.7f else 1f
        mascot.setOnClickListener {
            if (!still) android.animation.ValueAnimator.ofFloat(0f, 1f, 0f).apply { duration = 260; addUpdateListener { mascot.squash = it.animatedValue as Float } }.start()
        }
        c.add(scene)

        // ---- cards ----
        if (!hasAi) {
            c.add(ctx.card(fill = P.primaryContainer, stroke = P.alpha(P.primary, 110), onClick = { act.open("ai") }) {
                add(ctx.row {
                    add(ctx.icon(R.drawable.ic_sparkle, P.onPrimaryContainer, 32), WRAP, WRAP, end = 14)
                    add(ctx.col {
                        add(ctx.text(t("Подключите ИИ за 2 минуты — бесплатно", "Connect AI in 2 minutes — for free", "AI ni 2 daqiqada ulang — bepul"), T.Title, P.onPrimaryContainer), MATCH, WRAP)
                        add(ctx.text(t("Нужен свой бесплатный ключ Groq. Покажем по шагам", "You need your own free Groq key. We will guide you step by step", "O'zingizning bepul Groq kalitingiz kerak. Qadamma-qadam ko'rsatamiz"), T.Body, P.onPrimaryContainer), MATCH, WRAP, top = 2)
                    }, 0, WRAP, 1f)
                    add(ctx.icon(R.drawable.ic_chevron_right, P.onPrimaryContainer, 24), WRAP, WRAP)
                })
            }, MATCH, WRAP, top = 16, start = 16, end = 16)
        }
        if (missing) {
            c.add(ctx.card(fill = P.tertiaryContainer, stroke = P.alpha(P.rose, 90), onClick = { act.open("setup") }) {
                add(ctx.row {
                    add(ctx.text("$granted/${REQUIRED.size + 1}", T.Title, P.onTertiaryContainer).apply { gravity = Gravity.CENTER; background = ctx.rounded(P.alpha(P.rose, 70), 26f) }, ctx.dp(52), ctx.dp(52), end = 14)
                    add(ctx.col {
                        add(ctx.text(t("Настройка", "Setup", "Sozlash"), T.Title, P.onTertiaryContainer), MATCH, WRAP)
                        add(ctx.text(t("Нужны разрешения, чтобы помощник работал", "Permissions are needed for the assistant to work", "Yordamchi ishlashi uchun ruxsatlar kerak"), T.Body, P.onTertiaryContainer), MATCH, WRAP, top = 2)
                    }, 0, WRAP, 1f)
                    add(ctx.icon(R.drawable.ic_chevron_right, P.onTertiaryContainer, 24), WRAP, WRAP)
                })
            }, MATCH, WRAP, top = 16, start = 16, end = 16)
        } else if (perms["accessibility"] != true) {
            c.add(ctx.card(onClick = { act.open("setup") }) {
                add(ctx.row {
                    add(ctx.icon(R.drawable.ic_bolt, P.primary, 26), WRAP, WRAP, end = 12)
                    add(ctx.col {
                        add(ctx.text(t("Управление экраном голосом", "Control the screen by voice", "Ekranni ovoz bilan boshqarish"), T.Label), MATCH, WRAP)
                        add(ctx.text(t("Включите, чтобы Bubby нажимал кнопки за вас", "Turn on so Bubby can tap buttons for you", "Bubby sizning o'rningizga tugmalarni bosishi uchun yoqing"), T.Small), MATCH, WRAP, top = 2)
                    }, 0, WRAP, 1f)
                    add(ctx.icon(R.drawable.ic_chevron_right, P.onSurfaceVariant, 22), WRAP, WRAP)
                })
            }, MATCH, WRAP, top = 16, start = 16, end = 16)
        }

        // ---- the last task Bubby did on the phone ----
        com.example.buddy.RunHistory.load(ctx).firstOrNull()?.let { r ->
            c.add(ctx.card(onClick = { act.open("runs") }) {
                add(ctx.row {
                    add(ctx.icon(R.drawable.ic_schedule, P.primary, 26), WRAP, WRAP, end = 12)
                    add(ctx.col {
                        add(ctx.text(t("Последняя задача: ", "Last task: ", "Oxirgi vazifa: ") + r.task.take(60), T.Label).apply { maxLines = 2 }, MATCH, WRAP)
                        val res = when (r.outcome) {
                            "done" -> t("готово", "done", "bajarildi"); "cancelled" -> t("остановлено", "stopped", "to'xtatildi")
                            "stuck" -> t("застрял", "got stuck", "tiqilib qoldi"); else -> t("слишком долго", "took too long", "juda uzoq")
                        }
                        add(ctx.text(t("шагов: ", "steps: ", "qadamlar: ") + r.steps.size + "  ·  " + res + "  ·  " + t("посмотреть, что сделал Bubby", "see what Bubby did", "Bubby nima qilganini ko'rish"), T.Small), MATCH, WRAP, top = 2)
                    }, 0, WRAP, 1f)
                    add(ctx.icon(R.drawable.ic_chevron_right, P.onSurfaceVariant, 22), WRAP, WRAP)
                })
            }, MATCH, WRAP, top = 16, start = 16, end = 16)
        }

        // ---- agents on the computer (only when a computer is paired) ----
        if (com.example.buddy.PairingStore(ctx).isPaired()) {
            val st = com.example.buddy.AgentWidgets.overall()
            val u = com.example.buddy.AgentStats.current(ctx)
            c.add(ctx.card(onClick = { act.open("agents") }) {
                add(ctx.row {
                    add(View(ctx).apply { background = ctx.rounded(com.example.buddy.AgentWidgets.color(st), 6f) }, ctx.dp(12), ctx.dp(12), end = 12)
                    add(ctx.col {
                        add(ctx.text(t("Агенты: ", "Agents: ", "Agentlar: ") + com.example.buddy.AgentWidgets.title(ctx, st), T.Label), MATCH, WRAP)
                        val n = com.example.buddy.BuddyService.agentSessions.size
                        val q = com.example.buddy.BuddyService.agentQuestions.size
                        val bits = listOfNotNull(
                            if (n > 0) t("агентов: $n", "agents: $n", "agentlar: $n") else null,
                            if (q > 1) t("вопросов: $q", "questions: $q", "savollar: $q") else null,
                            u?.fiveHour?.let { "5 h: ${(it * 100).toInt()}%" },
                            u?.sevenDay?.let { "7 d: ${(it * 100).toInt()}%" },
                            u?.doneToday?.let { t("сегодня: $it", "today: $it", "bugun: $it") }
                        )
                        add(ctx.text(if (bits.isEmpty()) t("Нажмите, чтобы открыть", "Tap to open", "Ochish uchun bosing") else bits.joinToString("  ·  "), T.Small), MATCH, WRAP, top = 2)
                    }, 0, WRAP, 1f)
                    add(ctx.icon(R.drawable.ic_chevron_right, P.onSurfaceVariant, 22), WRAP, WRAP)
                })
            }, MATCH, WRAP, top = 16, start = 16, end = 16)
        }

        // ---- status ----
        fun label(txt: String) = ctx.text(txt.uppercase(), T.Small, Glass.TEXT_LABEL).apply { textSize = 13f; letterSpacing = 0.12f }
        val pad = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
        pad.add(label(t("Состояние", "Status", "Holat")), MATCH, WRAP, bottom = 10, start = 4)
        val pills = com.example.buddy.ui.FlowLayout(ctx)
        fun level(l: Int) = when (l) { 2 -> Glass.OK; 1 -> P.warning; else -> P.error }
        pills.addView(ctx.pill(t("ИИ", "AI", "AI"), level(if (hasAi) 2 else 0)) { act.open("ai") })
        pills.addView(ctx.pill(t("Микрофон", "Microphone", "Mikrofon"), level(if (perms["mic"] == true) 2 else 0)) {
            if (perms["mic"] != true) Backend.requestPermissions(act, "mic")
        })
        pills.addView(ctx.pill(t("Не засыпать", "Stay awake", "Uxlamaslik"), level(if (Backend.batteryIgnored(ctx)) 2 else 1)) { Backend.openSettings(act, "battery") })
        pad.add(pills)

        pad.add(label(t("Что можно сказать", "What you can say", "Nima deyish mumkin")), MATCH, WRAP, top = 20, bottom = 10, start = 4)
        val hints = com.example.buddy.ui.FlowLayout(ctx)
        for (h in listOf(
            t("поставь будильник на 7", "set an alarm for 7", "soat 7 ga budilnik qo'y"),
            t("включи музыку, мне грустно", "play some music, I feel sad", "musiqa qo'y, xafaman"),
            t("ответь на звонок", "answer the call", "qo'ng'iroqqa javob ber"),
            t("напомни купить молоко в 18:00", "remind me to buy milk at 18:00", "soat 18:00 da sut olishni eslat"),
            t("будь весёлым", "be cheerful", "quvnoq bo'l")
        )) {
            hints.addView(ctx.row(Gravity.CENTER_VERTICAL) {
                setPadding(ctx.dp(18), ctx.dp(11), ctx.dp(18), ctx.dp(11))
                background = ctx.glass(GlassKind.Base, 30f)
                add(ctx.icon(R.drawable.tg_mic, Glass.TEXT, 16), WRAP, WRAP, end = 8)
                add(ctx.text("«$h»", T.Body, Glass.TEXT).apply { textSize = 14f }, WRAP, WRAP)
                press { send(h) }
            })
        }
        pad.add(hints)
        c.add(pad, MATCH, WRAP, start = 20, end = 20, bottom = 28)
    }
}

/** The thin curved collar line around the core. */
class CollarView(c: android.content.Context, private val sc: Float) : View(c) {
    private val line = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        style = android.graphics.Paint.Style.STROKE; color = Glass.white(.45f); strokeWidth = 1.5f * sc
    }
    private val shade = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        style = android.graphics.Paint.Style.STROKE; color = android.graphics.Color.argb(89, 120, 140, 170); strokeWidth = 6f * sc
        strokeCap = android.graphics.Paint.Cap.ROUND; maskFilter = android.graphics.BlurMaskFilter(3f * sc, android.graphics.BlurMaskFilter.Blur.NORMAL)
    }
    init { setLayerType(LAYER_TYPE_SOFTWARE, null) }
    override fun onDraw(cv: android.graphics.Canvas) {
        cv.scale(sc, sc)
        val p = android.graphics.Path().apply { moveTo(60f, 70f); cubicTo(100f, 120f, 160f, 112f, 193f, 90f); cubicTo(230f, 105f, 290f, 100f, 330f, 60f) }
        val q = android.graphics.Path().apply { moveTo(60f, 70f); cubicTo(100f, 125f, 160f, 118f, 193f, 96f) }
        shade.strokeWidth = 6f; line.strokeWidth = 1.5f
        cv.drawPath(q, shade)
        cv.drawPath(p, line)
    }
}

/** The glowing core in the middle of the fan. */
class CoreView(c: android.content.Context, private val sc: Float) : View(c) {
    private val p = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
    override fun onDraw(cv: android.graphics.Canvas) {
        val cx = width / 2f; val cy = height / 2f
        p.shader = android.graphics.RadialGradient(cx, cy, 22f * sc, intArrayOf(0xFFFFFFFF.toInt(), Glass.white(.7f), Glass.white(0f)), floatArrayOf(.25f / .72f, .5f / .72f, 1f), android.graphics.Shader.TileMode.CLAMP)
        cv.drawCircle(cx, cy, 22f * sc, p)
        p.shader = android.graphics.RadialGradient(cx, cy, 24f * sc, intArrayOf(Glass.white(.95f), Glass.white(.95f), Glass.white(0f)), floatArrayOf(0f, 11f / 24f, 1f), android.graphics.Shader.TileMode.CLAMP)
        cv.drawCircle(cx, cy, 24f * sc, p)
        p.shader = null; p.color = 0xFFFFFFFF.toInt()
        cv.drawCircle(cx, cy, 11f * sc, p)
    }
}

/** The app's own top panel: a capsule that opens into a text field ("Пишите…"). */
class Island(c: android.content.Context, private val sc: Float, private val still: Boolean, private val onSend: (String) -> Unit, private val onToggle: (Boolean) -> Unit) : FrameLayout(c) {
    private var open = false
    private val capsule = LinearLayout(c).apply { orientation = LinearLayout.VERTICAL }
    private val field = android.widget.EditText(c)

    init {
        clipChildren = false
        layoutTransition = if (still) null else android.animation.LayoutTransition()
        addView(capsule)
        build()
    }

    private fun u(v: Float) = (v * sc).toInt()

    private fun bars(): View = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
        for ((i, h) in listOf(8, 14, 20, 12, 22, 16, 9).withIndex()) {
            addView(View(context).apply { background = context.rounded(Glass.white(.7f), 1.5f) }, LinearLayout.LayoutParams(u(3f), u(h.toFloat())).apply { if (i > 0) leftMargin = u(3f) })
        }
    }

    private fun build() {
        capsule.removeAllViews()
        val d = context
        capsule.background = GlassDrawable(GlassKind.Mid, if (open) 30f else 23f, null, true, sc)
        val top = LinearLayout(d).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        top.addView(MochiView(d, false).apply { mini = true }, LinearLayout.LayoutParams(u(34f), u(28f)))
        if (open) {
            top.addView(d.text(Tr.t(d, "Пишите…", "Write…", "Yozing…"), T.Body, 0xFFFFFFFF.toInt()).apply { setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, 17f * sc) },
                LinearLayout.LayoutParams(0, WRAP, 1f).apply { leftMargin = u(12f) })
        } else top.addView(View(d), LinearLayout.LayoutParams(0, 1, 1f))
        top.addView(bars(), LinearLayout.LayoutParams(WRAP, u(24f)))
        top.setOnClickListener { toggle() }
        capsule.addView(top, LinearLayout.LayoutParams(MATCH, if (open) u(30f) else MATCH))
        if (open) {
            val row = LinearLayout(d).apply {
                orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
                background = d.rounded(Glass.white(.13f), 18f * sc / d.resources.displayMetrics.density, Glass.white(.4f))
                setPadding(u(16f), 0, u(6f), 0)
            }
            field.apply {
                background = null; hint = Tr.t(d, "Спросите…", "Ask…", "So'rang…")
                setHintTextColor(Glass.white(.75f)); setTextColor(0xFFFFFFFF.toInt()); typeface = glassFont()
                setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, 17f * sc); setSingleLine(); imeOptions = android.view.inputmethod.EditorInfo.IME_ACTION_SEND
                setPadding(0, 0, 0, 0)
                setOnEditorActionListener { _, _, _ -> submit(); true }
            }
            (field.parent as? ViewGroup)?.removeView(field)
            row.addView(field, LinearLayout.LayoutParams(0, MATCH, 1f))
            val send = FrameLayout(d).apply {
                background = d.rounded(Glass.white(.3f), 20f, Glass.white(.6f))
                addView(d.icon(R.drawable.tg_send, 0xFFFFFFFF.toInt(), 16), FrameLayout.LayoutParams(u(16f), u(16f), Gravity.CENTER))
                press { submit() }
            }
            row.addView(send, LinearLayout.LayoutParams(u(34f), u(34f)))
            capsule.addView(row, LinearLayout.LayoutParams(MATCH, u(46f)).apply { topMargin = u(10f) })
        }
        capsule.setPadding(if (open) u(14f) else u(12f), if (open) u(12f) else 0, if (open) u(14f) else u(16f), 0)
        capsule.gravity = Gravity.CENTER_VERTICAL
        val lp = if (open) LayoutParams(MATCH, u(118f)).apply { setMargins(u(16f), u(12f), u(16f), 0) }
        else LayoutParams(u(196f), u(46f), Gravity.CENTER_HORIZONTAL).apply { topMargin = u(12f) }
        capsule.layoutParams = lp
    }

    private fun submit() {
        val txt = field.text.toString().trim()
        if (txt.isEmpty()) return
        field.setText("")
        onSend(txt)
        toggle()
    }

    fun toggle() {
        open = !open
        build()
        if (open) { field.requestFocus(); (context.getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager).showSoftInput(field, 0) }
        else (context.getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager).hideSoftInputFromWindow(windowToken, 0)
        onToggle(open)
    }
}
