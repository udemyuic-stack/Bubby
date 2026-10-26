package com.example.buddy.ui

import android.view.Gravity
import android.widget.LinearLayout
import com.example.buddy.Actions
import com.example.buddy.MusicApps
import com.example.buddy.R
import org.json.JSONObject

class MusicScreen(act: UiActivity) : Screen(act) {
    override fun title() = t("Музыка", "Music", "Musiqa")

    private class Mood(val ru: String, val en: String, val uz: String, val qRu: String, val qEn: String, val qUz: String, val icon: Int, val color: Int)

    private val moods = listOf(
        Mood("Весёлое", "Happy", "Quvnoq", "весёлая музыка", "happy upbeat music", "quvnoq musiqa", R.drawable.ic_face, P.warning),
        Mood("Грустное", "Sad", "G'amgin", "грустная музыка", "sad music", "g'amgin musiqa", R.drawable.ic_drop, P.sky),
        Mood("Спокойное", "Calm", "Sokin", "спокойная музыка", "calm relaxing music", "tinch musiqa", R.drawable.ic_sparkle, P.teal),
        Mood("Энергичное", "Energetic", "Shijoatli", "энергичная музыка", "energetic workout music", "shijoatli musiqa", R.drawable.ic_bolt, P.orange),
        Mood("Романтика", "Romantic", "Romantik", "романтическая музыка", "romantic music", "romantik musiqa", R.drawable.ic_heart, P.rose),
        Mood("Фокус", "Focus", "Diqqat", "музыка для концентрации", "focus concentration music", "diqqat uchun musiqa", R.drawable.ic_schedule, P.violet),
        Mood("Ночь", "Night", "Tun", "ночная музыка", "night chill music", "tungi musiqa", R.drawable.ic_moon, 0xFF5A4BA6.toInt()),
        Mood("Дорога", "On the road", "Yo'lda", "музыка в дорогу", "road trip music", "yo'lda tinglash uchun musiqa", R.drawable.ic_car, P.success),
    )

    private fun label(m: Mood) = t(m.ru, m.en, m.uz)

    private fun play(m: Mood) {
        ctx.toast(t("Включаю: ${label(m).lowercase()}…", "Playing: ${label(m).lowercase()}…", "Qo'yyapman: ${label(m).lowercase()}…"))
        val q = t(m.qRu, m.qEn, m.qUz)
        Thread {
            val err = try { Actions(ctx).run(JSONObject().put("do", "music").put("q", q)) } catch (e: Exception) { e.message }
            if (err != null) act.runOnUiThread { ctx.toast(err) }
        }.start()
    }

    private fun kindLabel(k: String) = when (k) {
        "video" -> t("Видео", "Video", "Video")
        "local" -> t("На устройстве", "On device", "Qurilmada")
        else -> t("Стриминг", "Streaming", "Strimming")
    }

    private fun appCard(label: String, selected: Boolean, subtitle: String? = null, kind: String? = null, installed: Boolean? = null, onTap: () -> Unit) =
        ctx.card(pad = 14, stroke = if (selected) P.primary else P.outlineVariant, onClick = onTap) {
            add(ctx.row {
                add(ctx.icon(if (selected) R.drawable.ic_radio_on else R.drawable.ic_radio_off, if (selected) P.primary else P.onSurfaceVariant, 24), WRAP, WRAP, end = 12)
                add(ctx.col {
                    add(ctx.text(label, T.Title), MATCH, WRAP)
                    if (subtitle != null) add(ctx.text(subtitle, T.Small), MATCH, WRAP, top = 2)
                    if (kind != null) add(ctx.row {
                        add(ctx.pill(kindLabel(kind), P.outline, dot = false), WRAP, WRAP, end = 8)
                        if (installed != null) add(ctx.pill(
                            if (installed) t("Установлено", "Installed", "O'rnatilgan") else t("Не установлено", "Not installed", "O'rnatilmagan"),
                            if (installed) P.success else P.outline), WRAP, WRAP)
                    }, MATCH, WRAP, top = 6)
                }, 0, WRAP, 1f)
            })
        }

    override fun render(c: LinearLayout) {
        c.add(ctx.card(pad = 16, fill = P.violet, stroke = 0) {
            add(ctx.row {
                add(ctx.row(Gravity.CENTER) {
                    background = ctx.rounded(0x2EFFFFFF, 18f)
                    add(ctx.icon(R.drawable.ic_music, 0xFFFFFFFF.toInt(), 30), WRAP, WRAP)
                }, ctx.dp(56), ctx.dp(56), end = 14)
                add(ctx.col {
                    add(ctx.text(t("Музыка по настроению", "Music for your mood", "Kayfiyatga mos musiqa"), T.Title, 0xFFFFFFFF.toInt()), MATCH, WRAP)
                    add(ctx.text(t("Скажите: «Bubby, включи музыку» — я спрошу про настроение и подберу плейлист",
                        "Say: “Bubby, play some music” — I will ask about your mood and pick a playlist",
                        "Ayting: «Bubby, musiqa qo'y» — kayfiyatingizni so'rab, pleylist tanlayman"), T.Body, 0xE6FFFFFF.toInt()), MATCH, WRAP, top = 4)
                }, 0, WRAP, 1f)
            })
        }, MATCH, WRAP)

        c.add(ctx.section(t("Выберите настроение", "Pick a mood", "Kayfiyatni tanlang"), R.drawable.ic_face))
        for (pair in moods.chunked(2)) {
            val row = ctx.row {}
            for (m in pair) row.add(ctx.card(pad = 12, onClick = { play(m) }) {
                add(ctx.row {
                    add(ctx.row(Gravity.CENTER) {
                        background = ctx.rounded(P.alpha(m.color, 40), 14f)
                        add(ctx.icon(m.icon, m.color, 24), WRAP, WRAP)
                    }, ctx.dp(44), ctx.dp(44), end = 10)
                    add(ctx.text(label(m), T.Label).apply { maxLines = 2 }, 0, WRAP, 1f)
                })
            }, 0, WRAP, 1f, end = if (m === pair.first()) 10 else 0, bottom = 10)
            c.add(row, MATCH, WRAP)
        }

        c.add(ctx.card(pad = 12) {
            add(ctx.switchRow(t("Подбирать музыку по настроению", "Pick music by mood", "Musiqani kayfiyatga qarab tanlash"),
                t("Bubby сам выберет плейлист под ваше настроение", "Bubby will choose a playlist to match your mood", "Bubby kayfiyatingizga mos pleylistni o'zi tanlaydi"),
                Backend.bool(ctx, "music_mood", true)) { Backend.setBool(ctx, "music_mood", it) })
        }, MATCH, WRAP, top = 6)

        c.add(ctx.section(t("Какие приложения можно использовать", "Which apps can be used", "Qaysi ilovalardan foydalanish mumkin"), R.drawable.ic_apps))
        val selected = Backend.str(ctx, "music_app")
        fun pick(id: String) { Backend.setStr(ctx, "music_app", id); refresh() }
        c.add(appCard(t("Автоматически", "Automatic", "Avtomatik"), selected.isEmpty(),
            subtitle = t("Телефон сам выберет подходящий проигрыватель", "The phone will pick a suitable player", "Telefon mos pleyerni o'zi tanlaydi")) { pick("") }, MATCH, WRAP, bottom = 8)
        for (a in MusicApps.ALL) c.add(appCard(a.label, selected == a.id, kind = a.kind, installed = MusicApps.installed(ctx, a.id)) { pick(a.id) }, MATCH, WRAP, bottom = 8)

        c.add(ctx.card(pad = 16, fill = P.secondaryContainer, stroke = 0) {
            add(ctx.row {
                add(ctx.icon(R.drawable.ic_info, P.onSecondaryContainer, 22), WRAP, WRAP, end = 8)
                add(ctx.text(t("Как это работает", "How it works", "Bu qanday ishlaydi"), T.Title, P.onSecondaryContainer), 0, WRAP, 1f)
            })
            add(ctx.text(t("Работает через проигрыватели, которые принимают поиск по голосу: YouTube Music и Spotify лучше всего; для остальных откроется поиск в приложении.",
                "Works through players that accept voice search: YouTube Music and Spotify work best; for the others, the in-app search will open.",
                "Ovozli qidiruvni qabul qiladigan pleyerlar orqali ishlaydi: YouTube Music va Spotify eng yaxshi; qolganlarida ilova ichidagi qidiruv ochiladi."), T.Body, P.onSecondaryContainer), MATCH, WRAP, top = 8)
            add(ctx.text(t("Приложение должно быть установлено, и вы должны быть в нём авторизованы.", "The app must be installed and you must be signed in to it.",
                "Ilova o'rnatilgan bo'lishi va unga kirgan bo'lishingiz kerak."), T.Body, P.onSecondaryContainer), MATCH, WRAP, top = 8)
        }, MATCH, WRAP, top = 8)
    }
}
