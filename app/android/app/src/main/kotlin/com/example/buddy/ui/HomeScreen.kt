package com.example.buddy.ui

import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.widget.LinearLayout
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
    override fun onShow() { refresh() }
    override fun onPermissionResult() { refresh() }

    private fun heroBackground(): GradientDrawable {
        val c = Calendar.getInstance()
        val h = c.get(Calendar.HOUR_OF_DAY); val m = c.get(Calendar.MINUTE)
        val accentLight = P.accentLight(h, m)
        val deep = android.graphics.Color.HSVToColor(floatArrayOf(P.hourHue(h, m), 0.75f, 0.55f))
        val inner = if (P.dark) P.mix(0xFF4B2F6B.toInt(), deep, 0.55f) else P.mix(0xFFFFD8AE.toInt(), accentLight, 0.7f)
        val outer = if (P.dark) P.mix(0xFF1E1736.toInt(), deep, 0.15f) else P.mix(0xFFFFF1E2.toInt(), accentLight, 0.18f)
        return GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(inner, outer)).apply {
            gradientType = GradientDrawable.RADIAL_GRADIENT
            setGradientCenter(0.5f, 0.4f)
            gradientRadius = ctx.dpf(360f)
            val r = ctx.dpf(40f)
            cornerRadii = floatArrayOf(0f, 0f, 0f, 0f, r, r, r, r)
        }
    }

    private fun startButtonBackground(running: Boolean): GradientDrawable {
        val colors = if (running) intArrayOf(0xFF8D7FD6.toInt(), 0xFF5A4BA6.toInt())
        else {
            val c = Calendar.getInstance()
            val a = P.accent(c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE))
            intArrayOf(P.mix(a, 0xFFFFFFFF.toInt(), 0.28f), a, P.mix(a, 0xFFEE7BA3.toInt(), 0.35f))
        }
        return ctx.gradient(colors, 34f)
    }

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
        if (firstShow) { firstShow = false; if (missing) content.post { act.open("setup") } }

        // ---- hero ----
        val hero = ctx.col {
            gravity = Gravity.CENTER_HORIZONTAL
            background = heroBackground()
            setPadding(0, ctx.dp(20), 0, ctx.dp(24))
        }
        val buddy = BuddyView(ctx).apply {
            scaleF = 2.1f
            species = Backend.str(ctx, "species", "cat")
            outfit = Outfits.parse(Backend.str(ctx, "outfit", ""))
            mood = if (running) "happy" else "sleepy"
            setActive(true)
            setOnClickListener { pet() }
        }
        hero.add(buddy, WRAP, WRAP)
        hero.add(ctx.text("Bubby", T.Headline), WRAP, WRAP, top = 10)
        hero.add(
            ctx.pill(
                if (running) t("Помощник работает", "Assistant is on", "Yordamchi ishlayapti") else t("Помощник выключен", "Assistant is off", "Yordamchi o'chiq"),
                if (running) P.success else P.outline
            ), WRAP, WRAP, top = 8
        )
        c.add(hero)

        // ---- start / stop ----
        val start = ctx.row(Gravity.CENTER) {
            background = startButtonBackground(running)
            alpha = if (busy) 0.6f else 1f
            val fg = if (running) 0xFFFFFFFF.toInt() else P.ink
            add(ctx.icon(if (running) R.drawable.ic_stop else R.drawable.ic_play, fg, 34), WRAP, WRAP, end = 10)
            add(ctx.text(if (running) t("Остановить", "Stop", "To'xtatish") else t("Запустить", "Start", "Ishga tushirish"), T.Title, fg).apply { textSize = 20f }, WRAP, WRAP)
            press { toggle() }
        }
        c.add(start, MATCH, ctx.dp(68), top = 24, start = 20, end = 20)

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

        // ---- status ----
        val pad = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
        pad.add(ctx.section(t("Состояние", "Status", "Holat"), R.drawable.ic_info))
        val pills = com.example.buddy.ui.FlowLayout(ctx)
        fun level(l: Int) = when (l) { 2 -> P.success; 1 -> P.warning; else -> P.error }
        pills.addView(ctx.pill(t("ИИ", "AI", "AI"), level(if (hasAi) 2 else 0)) { act.open("ai") })
        pills.addView(ctx.pill(t("Микрофон", "Microphone", "Mikrofon"), level(if (perms["mic"] == true) 2 else 0)) {
            if (perms["mic"] != true) Backend.requestPermissions(act, "mic")
        })
        pills.addView(ctx.pill(t("Не засыпать", "Stay awake", "Uxlamaslik"), level(if (Backend.batteryIgnored(ctx)) 2 else 1)) { Backend.openSettings(act, "battery") })
        pad.add(pills)
        pad.add(ctx.button(t("Проверить: скажите привет", "Test: say hello", "Tekshirish: salom deng"), BtnStyle.Tonal, R.drawable.ic_mic) {
            send(t("Привет!", "Hello!", "Salom!"))
        }, MATCH, WRAP, top = 12)

        pad.add(ctx.section(t("Что можно сказать", "What you can say", "Nima deyish mumkin"), R.drawable.ic_mic))
        val hints = com.example.buddy.ui.FlowLayout(ctx)
        for (h in listOf(
            t("поставь будильник на 7", "set an alarm for 7", "soat 7 ga budilnik qo'y"),
            t("включи музыку, мне грустно", "play some music, I feel sad", "musiqa qo'y, xafaman"),
            t("ответь на звонок", "answer the call", "qo'ng'iroqqa javob ber"),
            t("напомни купить молоко в 18:00", "remind me to buy milk at 18:00", "soat 18:00 da sut olishni eslat"),
            t("будь весёлым", "be cheerful", "quvnoq bo'l")
        )) {
            hints.addView(ctx.row(Gravity.CENTER_VERTICAL) {
                setPadding(ctx.dp(14), ctx.dp(11), ctx.dp(14), ctx.dp(11))
                background = ctx.rounded(P.secondaryContainer, 22f)
                add(ctx.icon(R.drawable.ic_mic, P.violet, 18), WRAP, WRAP, end = 8)
                add(ctx.text("«$h»", T.Body, P.onSecondaryContainer), WRAP, WRAP)
                press { send(h) }
            })
        }
        pad.add(hints)
        c.add(pad, MATCH, WRAP, start = 16, end = 16)
    }
}
