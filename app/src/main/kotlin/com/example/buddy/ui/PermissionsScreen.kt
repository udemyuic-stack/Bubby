package com.example.buddy.ui

import android.view.Gravity
import android.widget.LinearLayout
import com.example.buddy.R

/** Optional abilities, each asks only for its own permission. */
class PermissionsScreen(act: UiActivity) : Screen(act) {
    override fun title() = t("Все разрешения", "All permissions", "Barcha ruxsatlar")
    override fun onShow() { reshow() }
    override fun onPermissionResult() { refresh() }

    private class Perm(val key: String, val icon: Int, val title: String, val why: String, val page: String?)

    private fun items() = listOf(
        Perm("accessibility", R.drawable.ic_play, t("Управление экраном голосом", "Control the screen by voice", "Ekranni ovoz bilan boshqarish"),
            t("Нажимать кнопки и листать экран по команде", "Tap buttons and scroll the screen on command", "Buyruq bo'yicha tugmalarni bosish va ekranni aylantirish"), "accessibility"),
        Perm("phone", R.drawable.ic_bell, t("Звонки голосом", "Calls by voice", "Ovoz bilan qo'ng'iroq"),
            t("Отвечать на звонки и звонить по просьбе", "Answer calls and make calls on request", "Qo'ng'iroqlarga javob berish va so'rovingiz bo'yicha qo'ng'iroq qilish"), null),
        Perm("contacts", R.drawable.ic_face, t("Контакты", "Contacts", "Kontaktlar"),
            t("Называть звонящего и набирать по имени", "Name the caller and dial by name", "Qo'ng'iroq qilayotganni aytish va ism bo'yicha terish"), null),
        Perm("sms", R.drawable.ic_edit, t("Отправка SMS", "Send SMS", "SMS yuborish"),
            t("Отправлять сообщения по вашей просьбе", "Send text messages on request", "So'rovingiz bo'yicha SMS yuborish"), null),
        Perm("smsRead", R.drawable.ic_bell, t("Чтение SMS", "Read SMS", "SMS o'qish"),
            t("Пересказать последние сообщения по вашей просьбе (включите и в Настройках)", "Tell you your latest messages when you ask (also turn it on in Settings)", "So'rovingiz bo'yicha so'nggi xabarlarni aytib berish (Sozlamalarda ham yoqing)"), null),
        Perm("files", R.drawable.ic_apps, t("Файлы и фото", "Files and photos", "Fayllar va rasmlar"),
            t("Находить файлы по имени в Загрузках и Фото (только чтение)", "Find files by name in Downloads and Pictures (read only)", "Yuklanmalar va Rasmlardan nomi bo'yicha fayl topish (faqat o'qish)"), null),
        Perm("calendar", R.drawable.ic_calendar, t("Календарь", "Calendar", "Kalendar"),
            t("Читать и создавать события", "Read and create events", "Tadbirlarni o'qish va yaratish"), null),
        Perm("notifications", R.drawable.ic_bell, t("Напоминания", "Reminders", "Eslatmalar"),
            t("Показывать уведомления и напоминания", "Show notifications and reminders", "Bildirishnoma va eslatmalarni ko'rsatish"), null),
        Perm("location", R.drawable.ic_wifi, t("Погода в утренней сводке", "Weather in the morning briefing", "Ertalabki xulosada ob-havo"),
            t("Примерное местоположение нужно только для погоды", "Approximate location is used only for the weather", "Taxminiy joylashuv faqat ob-havo uchun kerak"), null),
        Perm("exactAlarm", R.drawable.ic_schedule, t("Точные будильники", "Exact alarms", "Aniq budilniklar"),
            t("Будильники срабатывают вовремя", "Alarms go off on time", "Budilniklar o'z vaqtida chalinadi"), "exactAlarm"),
        Perm("notifListener", R.drawable.ic_bell, t("Уведомления", "Notifications", "Bildirishnomalar"),
            t("Читать уведомления и отвечать на них", "Read and answer notifications", "Bildirishnomalarni o'qish va ularga javob berish"), "notifListener"),
        Perm("dnd", R.drawable.ic_stop, t("Не беспокоить", "Do not disturb", "Bezovta qilmang"),
            t("Режим «Не беспокоить» и беззвучный режим голосом", "Do-not-disturb and silent mode by voice", "Bezovta qilmang va ovozsiz rejimni ovoz bilan boshqarish"), "dnd"),
        Perm("writeSettings", R.drawable.ic_palette, t("Яркость экрана", "Screen brightness", "Ekran yorqinligi"),
            t("Яркость экрана голосом", "Screen brightness by voice", "Ekran yorqinligini ovoz bilan o'zgartirish"), "writeSettings"),
    )

    override fun render(c: LinearLayout) {
        val status = Backend.permissions(act)
        c.add(ctx.text(t("Дополнительные возможности", "Extra features", "Qo'shimcha imkoniyatlar"), T.Title), MATCH, WRAP, top = 4, bottom = 10)
        for (p in items()) {
            val ok = status[p.key] == true
            c.add(ctx.card(pad = 12) {
                add(ctx.row {
                    add(ctx.row(Gravity.CENTER) {
                        background = ctx.rounded(P.alpha(if (ok) P.success else P.primary, 40), 22f)
                        add(ctx.icon(if (ok) R.drawable.ic_check_circle else p.icon, if (ok) P.success else P.primary, 24), WRAP, WRAP)
                    }, ctx.dp(44), ctx.dp(44), end = 12)
                    add(ctx.col {
                        add(ctx.text(p.title, T.Title).apply { textSize = 15f }, MATCH, WRAP)
                        add(ctx.text(p.why, T.Small), MATCH, WRAP, top = 2)
                    }, 0, WRAP, 1f)
                    if (!ok) add(ctx.button(if (p.page == null) t("Разрешить", "Allow", "Ruxsat berish") else t("Открыть", "Open", "Ochish"), BtnStyle.Tonal) {
                        if (p.page != null) Backend.openSettings(act, p.page) else Backend.requestPermissions(act, p.key)
                    }, WRAP, WRAP, start = 8)
                })
            }, MATCH, WRAP, bottom = 8)
        }
        c.add(ctx.card(pad = 14, fill = P.secondaryContainer, stroke = 0) {
            add(ctx.row {
                add(ctx.icon(R.drawable.ic_info, P.onSecondaryContainer, 22), WRAP, WRAP, end = 10)
                add(ctx.text(t("Если кнопка серая: Настройки → Приложения → Bubby → ⋮ → Разрешить ограниченные настройки",
                    "If the button is grey: Settings → Apps → Bubby → ⋮ → Allow restricted settings",
                    "Agar tugma kulrang bo'lsa: Sozlamalar → Ilovalar → Bubby → ⋮ → Cheklangan sozlamalarga ruxsat berish"), T.Body, P.onSecondaryContainer), 0, WRAP, 1f)
            })
        }, MATCH, WRAP, top = 6)
        c.add(ctx.button(t("Открыть настройки приложения", "Open app settings", "Ilova sozlamalarini ochish"), BtnStyle.Text, R.drawable.ic_settings) {
            Backend.openSettings(act, "app")
        }, MATCH, WRAP, top = 6)
    }
}
