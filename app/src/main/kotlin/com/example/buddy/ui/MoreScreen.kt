package com.example.buddy.ui

import android.graphics.Color
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import com.example.buddy.R

class MoreScreen(act: UiActivity) : Screen(act) {
    override fun title() = t("Ещё", "More", "Yana")
    override fun onShow() { reshow() }

    private class Item(val icon: Int, val title: String, val sub: String, val color: Int, val page: String, val color2: Int = 0)

    private fun menuCard(i: Item) = ctx.card(pad = 18, onClick = { act.open(i.page) }) {
        add(ctx.row {
            add(ctx.row(Gravity.CENTER) {
                background = ctx.gradient(intArrayOf(i.color, if (i.color2 != 0) i.color2 else P.mix(i.color, Color.BLACK, 0.25f)), 16f)
                add(ctx.icon(i.icon, 0xFFFFFFFF.toInt(), 24), WRAP, WRAP)
            }, ctx.dp(46), ctx.dp(46), end = 14)
            add(ctx.col {
                add(ctx.text(i.title, T.Title).apply { textSize = 16f }, MATCH, WRAP)
                add(ctx.text(i.sub, T.Small).apply { textSize = 12f }, MATCH, WRAP, top = 2)
            }, 0, WRAP, 1f)
            add(ctx.icon(R.drawable.ic_chevron_right, P.onSurfaceVariant, 22), WRAP, WRAP)
        })
    }

    override fun render(c: LinearLayout) {
        header(c, t("Агенты, наряды и игры", "Agents, outfits and games", "Agentlar, kiyimlar va o'yinlar"))
        val paired = Backend.agentStatus(ctx).paired
        val agents = Item(R.drawable.ic_bolt, t("Агенты (Claude Code)", "Agents (Claude Code)", "Agentlar (Claude Code)"),
            t("Следите за Claude Code с телефона", "Watch Claude Code from your phone", "Claude Code ni telefondan kuzating"), P.violet, "agents")
        val items = buildList {
            add(Item(R.drawable.ic_key, t("Подключение ИИ", "AI connection", "AI ulanishi"),
                t("Свой бесплатный ключ и запасные подключения", "Your own free key and backup connections", "O'zingizning bepul kalitingiz va zaxira ulanishlar"), 0xFF8D7EE0.toInt(), "ai", 0xFF5B4BB0.toInt()))
            if (paired) add(agents)
            add(Item(R.drawable.ic_music, t("Музыка", "Music", "Musiqa"),
                t("Музыка по настроению и выбор приложения", "Music for your mood and app choice", "Kayfiyatga mos musiqa va ilova tanlash"), 0xFF6FA0EE.toInt(), "music", 0xFF4A74C4.toInt()))
            add(Item(R.drawable.ic_face, t("Наряды", "Outfits", "Kiyimlar"),
                t("Шляпы, очки и шарфы для Bubby", "Hats, glasses and scarves for Bubby", "Bubby uchun shlyapa, ko'zoynak va sharflar"), 0xFFE48AA8.toInt(), "outfits", 0xFFBD5F80.toInt()))
            add(Item(R.drawable.ic_play, t("Игры и стикеры", "Games and stickers", "O'yinlar va stikerlar"),
                t("Играйте и отправляйте стикеры друзьям", "Play and send stickers to friends", "O'ynang va do'stlaringizga stiker yuboring"), 0xFFE89A58.toInt(), "fun", 0xFFC0702F.toInt()))
        }
        for (i in items) c.add(menuCard(i), MATCH, WRAP, bottom = 10)

        val ignoring = Backend.batteryIgnored(ctx)
        c.add(ctx.row {
            setPadding(ctx.dp(8), ctx.dp(12), ctx.dp(4), ctx.dp(10))
            add(View(ctx).apply { background = ctx.rounded(if (ignoring) Glass.OK else P.warning, 3f) }, ctx.dp(10), ctx.dp(24), end = 12)
            add(ctx.text(if (ignoring) t("Не засыпать: включено", "Stay awake: on", "Uxlamaslik: yoqilgan")
            else t("Не засыпать: выключено — нажмите, чтобы исправить", "Stay awake: off — tap to fix", "Uxlamaslik: o'chiq — tuzatish uchun bosing"), T.Body), 0, WRAP, 1f)
            press { Backend.openSettings(act, "battery") }
        }, MATCH, WRAP)

        c.add(ctx.linkRow(t("Что сделал Bubby", "What Bubby did", "Bubby nima qildi"), t("Шаги последних задач на телефоне", "Steps of the latest tasks on the phone", "Telefondagi so'nggi vazifalar qadamlari"), R.drawable.ic_schedule, P.primary) { act.open("runs") }, MATCH, WRAP, top = 4)
        if (!paired) c.add(ctx.linkRow(t("Для разработчиков: агенты (Claude Code)", "For developers: agents (Claude Code)", "Dasturchilar uchun: agentlar (Claude Code)"), null, R.drawable.ic_bolt, P.violet) { act.open("agents") }, MATCH, WRAP, top = 4)

        c.add(ctx.card(pad = 16) {
            add(ctx.row {
                add(ctx.icon(R.drawable.ic_info, P.primary, 22), WRAP, WRAP, end = 8)
                add(ctx.text(t("Что Bubby отправляет", "What Bubby sends", "Bubby nimalarni yuboradi"), T.Title), 0, WRAP, 1f)
            })
            for (line in listOf(
                t("Ваши сообщения уходят только выбранному ИИ-сервису", "Your messages go only to the AI service you chose", "Xabarlaringiz faqat siz tanlagan AI xizmatiga ketadi"),
                t("Ключи хранятся только на этом телефоне (не в копиях)", "Keys stay on this phone (not in backups)", "Kalitlar faqat shu telefonda saqlanadi (zaxira nusxada emas)"),
                t("Текст экрана — только когда вы просите задачу на экране", "Screen text only when you ask for a screen task", "Ekran matni faqat ekran bo'yicha vazifa so'raganingizda"),
                t("Уведомления и контакты не покидают телефон, кроме нужного для ответа", "Notifications and contacts never leave the phone except what is needed to answer", "Bildirishnomalar va kontaktlar telefondan chiqmaydi, javob uchun kerakligidan tashqari")
            )) add(ctx.text("• $line", T.Small), MATCH, WRAP, top = 6)
        }, MATCH, WRAP, top = 12)

        val crash = Backend.str(ctx, "last_crash")
        if (crash.isNotEmpty()) c.add(ctx.card(pad = 16, fill = P.errorContainer, stroke = P.alpha(P.error, 90)) {
            add(ctx.text(t("Последний сбой", "Last crash", "Oxirgi nosozlik"), T.Title, P.onErrorContainer), MATCH, WRAP)
            add(ctx.text(crash, T.Small, P.onErrorContainer).apply { setTextIsSelectable(true); textSize = 12f }, MATCH, WRAP, top = 8)
        }, MATCH, WRAP, top = 12)
    }
}
