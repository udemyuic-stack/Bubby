package com.example.buddy.ui

import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.ProgressBar
import com.example.buddy.BuddyView
import com.example.buddy.Outfits
import com.example.buddy.R

/** First-run stepper: one plain sentence, one big button. */
class SetupScreen(act: UiActivity) : Screen(act) {
    override fun title() = t("Настройка", "Setup", "Sozlash")

    private class Step(val title: String, val why: String, val action: String, val optional: Boolean = false)

    private var i = 0
    private var positioned = false
    private var ok = BooleanArray(5)
    private var lastProgress = 100
    private var slide = 0          // -1 / 1 while moving between steps, 0 for a plain redraw
    private var confettiFor = -1
    private var pulseFor = -1

    private fun go(to: Int) { slide = if (to > i) 1 else -1; i = to; refresh() }

    private fun steps() = listOf(
        Step(t("Микрофон", "Microphone", "Mikrofon"), t("Чтобы Bubby вас слышал", "So Bubby can hear you", "Bubby sizni eshitishi uchun"),
            t("Разрешить микрофон", "Allow microphone", "Mikrofonga ruxsat bering")),
        Step(t("Показ поверх окон", "Display over other apps", "Boshqa oynalar ustida ko'rsatish"),
            t("Чтобы Bubby жил на экране поверх других приложений", "So Bubby can live on the screen above other apps", "Bubby boshqa ilovalar ustida ekranda yashashi uchun"),
            t("Открыть настройки", "Open settings", "Sozlamalarni ochish")),
        Step(t("ИИ", "AI", "AI"),
            t("Bubby думает с помощью бесплатного ИИ. Нужен ваш личный бесплатный пропуск (ключ) — получить его занимает 2 минуты",
                "Bubby thinks with a free AI. You need your own free pass (key) — getting it takes 2 minutes",
                "Bubby bepul AI yordamida o'ylaydi. Sizga shaxsiy bepul kalit kerak — uni olish 2 daqiqa oladi"),
            t("Получить ключ", "Get a key", "Kalit olish")),
        Step(t("Не засыпать", "Stay awake", "Uxlamaslik"),
            t("Чтобы Android не выключал Bubby в фоне", "So Android does not shut Bubby down in the background", "Android Bubby ni fonda o'chirib qo'ymasligi uchun"),
            t("Открыть настройки", "Open settings", "Sozlamalarni ochish"), optional = true),
        Step(t("Попробуйте!", "Try it!", "Sinab ko'ring!"),
            t("Нажмите на Bubby и скажите: «Привет»", "Tap Bubby and say: “Hello”", "Bubby ni bosing va «Salom» deng"),
            t("Готово", "Done", "Tayyor")),
    )

    override fun onShow() { check(initial = !positioned); refresh() }
    override fun onPermissionResult() { check(); refresh() }

    private fun check(initial: Boolean = false) {
        val p = Backend.permissions(act)
        ok = booleanArrayOf(p["mic"] == true, p["overlay"] == true, Backend.aiKeys(ctx).isNotEmpty(), Backend.batteryIgnored(ctx), false)
        if (initial) {
            positioned = true
            val first = ok.take(4).indexOf(false)
            i = if (first < 0) 4 else first
        } else if (i < 4 && ok[i]) {
            slide = 1
            i++
            while (i < 4 && ok[i]) i++
        }
    }

    private fun action() {
        when (i) {
            0 -> { Backend.requestPermissions(act, "mic"); return }
            1 -> Backend.openSettings(act, "overlay")
            2 -> act.open("ai")
            3 -> Backend.openSettings(act, "battery")
            else -> {
                val err = Backend.startBuddy(ctx)
                if (err != null) { ctx.toast(err); return }
                content.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY)
                act.pop(); return
            }
        }
    }

    private fun mood(step: Int, passed: Boolean) = when (step) {
        0 -> if (passed) "happy" else "listen"
        1 -> if (passed) "happy" else "idle"
        2 -> if (passed) "excited" else "think"
        3 -> if (passed) "happy" else "sleepy"
        else -> "excited"
    }

    override fun render(c: LinearLayout) {
        val steps = steps()
        val step = steps[i]
        val passed = ok[i] && i < 4
        c.add(ctx.text(t("Шаг ${i + 1} из ${steps.size}", "Step ${i + 1} of ${steps.size}", "${i + 1}-qadam, jami ${steps.size} ta"), T.Label), MATCH, WRAP, top = 8)
        val bar = ProgressBar(ctx, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = steps.size * 100; progress = lastProgress
            progressTintList = android.content.res.ColorStateList.valueOf(P.primary)
        }
        c.add(bar, MATCH, ctx.dp(10), top = 8, bottom = 20)
        val target = (i + 1) * 100
        if (target != lastProgress) { bar.post { bar.animateTo(target) }; lastProgress = target }
        val card = ctx.card(pad = 24, radius = 28f) {
            gravity = Gravity.CENTER_HORIZONTAL
            add(ctx.mascotStage(240, 170), ctx.dp(240), (ctx.dp(240) * 0.9f).toInt())
            if (passed) add(ctx.row(Gravity.CENTER) {
                add(ctx.pill(t("Готово", "Done", "Tayyor"), P.success), WRAP, WRAP, end = 8)
                if (pulseFor != i) { pulseFor = i; add(ctx.successPulse(32), ctx.dp(44), ctx.dp(44)) }
            }, WRAP, WRAP, top = 8)
            add(ctx.text(step.title, T.Headline).apply { gravity = Gravity.CENTER; textSize = 24f }, MATCH, WRAP, top = 12)
            add(ctx.text(step.why, T.Body).apply { gravity = Gravity.CENTER }, MATCH, WRAP, top = 8)
            if (i == 4) add(ctx.text(t("Bubby запустится, когда вы нажмёте «Готово»", "Bubby will start when you tap “Done”", "«Tayyor» ni bossangiz, Bubby ishga tushadi"), T.Small).apply { gravity = Gravity.CENTER }, MATCH, WRAP, top = 8)
        }
        c.add(card, MATCH, WRAP)
        if (slide != 0) {
            card.alpha = 0f; card.translationX = ctx.dpf(48f) * slide
            card.animate().alpha(1f).translationX(0f).setDuration(380).setInterpolator(android.view.animation.DecelerateInterpolator(1.6f)).start()
            slide = 0
        }
        if (i == 4 && confettiFor != 4) { confettiFor = 4; card.post { act.confetti() } }
        c.add(ctx.button(if (passed) t("Уже готово — дальше", "Already done — next", "Tayyor — keyingisi") else step.action) {
            if (passed) go(i + 1) else { action(); if (i != 4) { /* result arrives on resume */ } }
        }, MATCH, ctx.dp(60), top = 24)
        if (step.optional && !passed) c.add(ctx.button(t("Пропустить", "Skip", "O'tkazib yuborish"), BtnStyle.Text) { go((i + 1).coerceIn(0, 4)) }, MATCH, WRAP, top = 6)
        if (i > 0) c.add(ctx.button(t("Назад", "Back", "Orqaga"), BtnStyle.Text) { go(i - 1) }, MATCH, WRAP, top = 6)
        c.add(ctx.button(t("Все разрешения", "All permissions", "Barcha ruxsatlar"), BtnStyle.Outline, R.drawable.ic_check_circle) { act.open("permissions") }, MATCH, WRAP, top = 18)
    }
}
