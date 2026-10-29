package com.example.buddy.ui

import android.content.res.ColorStateList
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.SeekBar
import com.example.buddy.MusicApps
import com.example.buddy.R
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.roundToInt

class SettingsScreen(act: UiActivity) : Screen(act) {
    override fun title() = t("Настройки", "Settings", "Sozlamalar")
    override fun onShow() { reshow() }

    private val moods = listOf("funny and cheerful", "serious and precise", "warm and affectionate", "sarcastic", "sleepy", "energetic", "")
    private val legacy = mapOf(
        "Весёлый" to "funny and cheerful", "Серьёзный" to "serious and precise", "Ласковый" to "warm and affectionate",
        "Саркастичный" to "sarcastic", "Сонный" to "sleepy", "Энергичный" to "energetic"
    )

    private fun moodLabel(m: String) = when (m) {
        "funny and cheerful" -> t("Весёлый", "Cheerful", "Quvnoq")
        "serious and precise" -> t("Серьёзный", "Serious", "Jiddiy")
        "warm and affectionate" -> t("Ласковый", "Affectionate", "Mehribon")
        "sarcastic" -> t("Саркастичный", "Sarcastic", "Kinoyali")
        "sleepy" -> t("Сонный", "Sleepy", "Uyqusiragan")
        "energetic" -> t("Энергичный", "Energetic", "Shijoatli")
        else -> t("По умолчанию", "Default", "Odatiy")
    }

    private fun sw(title: String, key: String, def: Boolean, sub: String? = null) =
        ctx.switchRow(title, sub, Backend.bool(ctx, key, def)) { Backend.setBool(ctx, key, it) }

    private fun group(c: LinearLayout, title: String, icon: Int, block: LinearLayout.() -> Unit) {
        c.add(ctx.section(title, icon))
        c.add(ctx.card(pad = 14, block = block), MATCH, WRAP)
    }

    private fun chips(options: List<Pair<String, String>>, current: String, onPick: (String) -> Unit): View {
        val flow = FlowLayout(ctx)
        for ((id, label) in options) {
            flow.addView(ctx.chip(label, id == current) { onPick(id); refresh() },
                LinearLayout.LayoutParams(WRAP, WRAP).apply { setMargins(0, 0, ctx.dp(8), ctx.dp(8)) })
        }
        return flow
    }

    // size slider is logarithmic so both tiny (x0.15) and huge (x6) sizes are easy to reach
    private val sizeMin = 0.15f
    private val sizeMax = 6f
    private fun sizeToProgress(s: Float) = (ln(s.coerceIn(sizeMin, sizeMax) / sizeMin) / ln(sizeMax / sizeMin) * 1000).roundToInt()
    private fun progressToSize(p: Int) = ((sizeMin * (sizeMax / sizeMin).toDouble().pow(p / 1000.0)) * 100).roundToInt() / 100f
    private fun setSize(v: Float) = Backend.prefs(ctx).edit().putFloat("size", v.coerceIn(sizeMin, sizeMax)).apply()

    private fun sizeCard(): LinearLayout = ctx.col {
        var size = Backend.prefs(ctx).getFloat("size", 1f)
        val label = ctx.text("×" + "%.2f".format(size), T.Title)
        val bar = SeekBar(ctx).apply {
            max = 1000
            progress = sizeToProgress(size)
            progressTintList = ColorStateList.valueOf(P.primary)
            thumbTintList = ColorStateList.valueOf(P.primary)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(s: SeekBar?, p: Int, fromUser: Boolean) {
                    if (!fromUser) return
                    size = progressToSize(p); label.text = "×" + "%.2f".format(size)
                }
                override fun onStartTrackingTouch(s: SeekBar?) {}
                override fun onStopTrackingTouch(s: SeekBar?) { setSize(size) }
            })
        }
        fun apply(v: Float) { size = (v.coerceIn(sizeMin, sizeMax) * 100).roundToInt() / 100f; setSize(size); label.text = "×" + "%.2f".format(size); bar.progress = sizeToProgress(size) }
        add(ctx.row {
            add(bar, 0, WRAP, 1f)
            add(label, ctx.dp(64), WRAP)
            label.gravity = Gravity.END
        })
        add(ctx.row {
            add(ctx.button(t("Меньше", "Smaller", "Kichikroq"), BtnStyle.Tonal) { apply(size * 0.8f) }, 0, WRAP, 1f, end = 8)
            add(ctx.button(t("Обычный", "Normal", "Odatiy"), BtnStyle.Tonal) { apply(1f) }, 0, WRAP, 1f, end = 8)
            add(ctx.button(t("Больше", "Larger", "Kattaroq"), BtnStyle.Tonal) { apply(size * 1.25f) }, 0, WRAP, 1f)
        }, MATCH, WRAP, top = 6)
        val docks = listOf(
            "top" to t("Сверху", "Top", "Tepada"),
            "bottom" to t("У зарядки (снизу)", "By the charger (bottom)", "Zaryadlash yonida (pastda)"),
            "left" to t("Слева", "Left", "Chapda"),
            "right" to t("Справа", "Right", "O'ngda"),
            "center" to t("По центру", "Center", "O'rtada"),
        )
        add(ctx.text(t("Положение", "Position", "Joylashuv"), T.Title), MATCH, WRAP, top = 14, bottom = 8)
        add(chips(docks, "") { Backend.dockTo(ctx, it) })
        add(ctx.text(t("Размер можно менять и двумя пальцами прямо на экране или голосом: «Bubby, стань больше»",
            "You can also resize with two fingers right on the screen or by voice: “Bubby, get bigger”",
            "O'lchamini ekranda ikki barmoq bilan yoki ovoz bilan ham o'zgartirish mumkin: «Bubby, kattaroq bo'l»"), T.Small), MATCH, WRAP, top = 4)
    }

    private fun aiCard(): View {
        val keys = Backend.aiKeys(ctx)
        val n = keys.size
        val summary = if (n == 0) t("Пока не подключено", "Not connected yet", "Hali ulanmagan") else {
            val main = Backend.aiTitle(ctx, keys.first())
            t("Основное: $main" + if (n > 1) " · запасных: ${n - 1}" else "", "Main: $main" + if (n > 1) " · backups: ${n - 1}" else "", "Asosiy: $main" + if (n > 1) " · zaxira: ${n - 1}" else "")
        }
        return ctx.col {
            add(ctx.row {
                background = ctx.glass(GlassKind.Amber, 20f, false)
                setPadding(ctx.dp(16), ctx.dp(12), ctx.dp(16), ctx.dp(12))
                add(ctx.icon(R.drawable.ic_key, Glass.TEXT, 24), WRAP, WRAP, end = 14)
                add(ctx.col {
                    add(ctx.text(t("ИИ-подключения: $n", "AI connections: $n", "AI ulanishlari: $n"), T.Title, Glass.TEXT), MATCH, WRAP)
                    add(ctx.text(summary, T.Small, Glass.TEXT), MATCH, WRAP)
                }, 0, WRAP, 1f)
            }, MATCH, WRAP)
            add(ctx.button(if (n == 0) t("Подключить ИИ", "Connect AI", "AI ulash") else t("Управлять подключениями", "Manage connections", "Ulanishlarni boshqarish"),
                iconRes = R.drawable.ic_settings) { act.open("ai") }, MATCH, WRAP, top = 12)
        }
    }

    private fun musicCard(): View {
        val sel = Backend.str(ctx, "music_app")
        val installed = MusicApps.ALL.filter { MusicApps.installed(ctx, it.id) }.map { it.label }
        val title = if (sel.isEmpty()) t("Автоматически", "Automatic", "Avtomatik") else MusicApps.ALL.firstOrNull { it.id == sel }?.label ?: sel
        val summary = if (installed.isEmpty()) t("Поддерживаемые приложения не найдены", "No supported apps found", "Qo'llab-quvvatlanadigan ilovalar topilmadi")
        else t("Установлено: ${installed.joinToString(", ")}", "Installed: ${installed.joinToString(", ")}", "O'rnatilgan: ${installed.joinToString(", ")}")
        return ctx.card(pad = 14, onClick = { act.open("music") }) {
            add(ctx.row {
                add(ctx.row(Gravity.CENTER) {
                    background = ctx.gradient(Theme.tile(2, 0xFF8D7EE0.toInt(), 0xFF5B4BB0.toInt()), 16f)
                    add(ctx.icon(R.drawable.ic_music, 0xFFFFFFFF.toInt(), 24), WRAP, WRAP)
                }, ctx.dp(46), ctx.dp(46), end = 14)
                add(ctx.col {
                    add(ctx.text(t("Музыка и приложения", "Music and apps", "Musiqa va ilovalar"), T.Title), MATCH, WRAP)
                    add(ctx.text(t("Приложение: $title", "App: $title", "Ilova: $title"), T.Body), MATCH, WRAP, top = 2)
                    add(ctx.text(summary, T.Small), MATCH, WRAP)
                }, 0, WRAP, 1f)
                add(ctx.icon(R.drawable.ic_chevron_right, P.onSurfaceVariant, 22), WRAP, WRAP)
            })
        }
    }

    override fun render(c: LinearLayout) {
        header(c, t("Подстройте Bubby под себя", "Tune Bubby to your liking", "Bubby'ni o'zingizga moslang"))

        group(c, t("Искусственный интеллект", "Artificial intelligence", "Sun'iy intellekt"), R.drawable.ic_sparkle) {
            add(aiCard(), MATCH, WRAP)
            add(ctx.text(t("Язык", "Language", "Til"), T.Title), MATCH, WRAP, top = 14, bottom = 8)
            add(chips(listOf("auto" to t("Авто (как я говорю)", "Auto (as I speak)", "Avto (gapirgan tilim)"), "uz" to "O'zbekcha", "ru" to "Русский", "en" to "English"),
                Backend.str(ctx, "lang", "auto")) {
                if (it != Backend.str(ctx, "lang", "auto")) { Backend.prefs(ctx).edit().putString("lang", it).commit(); act.restart() }
            })
            add(ctx.text(t("Язык ответов и приложения. «Авто» — язык, на котором вы говорите",
                "Language of the replies and the app. “Auto” follows the language you speak",
                "Javoblar va ilova tili. «Avto» — siz gaplashgan til"), T.Small), MATCH, WRAP, top = 2)
        }
        c.add(musicCard(), MATCH, WRAP, top = 14)

        group(c, t("Стиль", "Style", "Uslub"), R.drawable.ic_palette) {
            add(ctx.text(t("Цвета приложения, остров и зверёк", "App colours, the island and the mascot", "Ilova ranglari, orol va maskot"), T.Small), MATCH, WRAP, bottom = 10)
            val cur = Backend.str(ctx, "theme", "glass")
            val styles = listOf(
                Triple("night", t("Ночь", "Night", "Tun"), t("Куб", "Cube", "Kub")),
                Triple("glass", t("Стекло", "Glass", "Shisha"), t("Мочи", "Mochi", "Mochi")),
                Triple("green", t("Зелёный", "Green", "Yashil"), t("Мотылёк", "Butterfly", "Kapalak")),
                Triple("blue", t("Синий", "Blue", "Ko'k"), t("Кит", "Whale", "Kit")),
                Triple("space", t("Космос", "Space", "Kosmos"), t("Солнце", "Sun", "Quyosh")),
                Triple("halloween", t("Хэллоуин", "Halloween", "Halloween"), t("Тыква", "Pumpkin", "Qovoq")),
                Triple("forest", t("Дождливый лес", "Rainy forest", "Yomg'irli o'rmon"), t("Облако", "Cloud", "Bulut")),
                Triple("ocean", t("Океан", "Ocean", "Okean"), t("Медуза", "Jellyfish", "Meduza")),
                Triple("newyear", t("Новый год", "New Year", "Yangi yil"), t("Снеговик", "Snowman", "Qorbobo"))
            )
            add(sw(t("Живой фон", "Animated background", "Jonli fon"), "scene_anim", true,
                t("Движущийся фон за страницами. Выключите, чтобы экономить батарею", "A moving background behind the pages. Turn off to save battery", "Sahifalar orqasida harakatlanuvchi fon. Batareyani tejash uchun o'chiring")).apply { }, MATCH, WRAP, bottom = 10)
            for (pair in styles.chunked(2)) {
                add(ctx.row {
                    for ((i, st) in pair.withIndex()) {
                        add(StylePreview(ctx, Theme.byId(st.first), st.second, st.third, st.first == cur).apply {
                            press {
                                if (st.first != Backend.str(ctx, "theme", "glass")) {
                                    // the mascot of the chosen style, everywhere (also the floating one)
                                    Backend.prefs(ctx).edit().putString("theme", st.first).putString("species", "glass").commit()
                                    act.restart()
                                }
                            }
                        }, 0, ctx.dp(132), 1f, end = if (i == 0) 10 else 0)
                    }
                }, MATCH, WRAP, bottom = 10)
            }
        }

        group(c, t("Голос и поведение", "Voice and behavior", "Ovoz va xulq"), R.drawable.ic_mic) {
            add(sw(t("Озвучивать ответы", "Speak replies aloud", "Javoblarni ovoz chiqarib aytish"), "tts", true))
            add(sw(t("Голосовой ввод по касанию", "Voice input on tap", "Bosganda ovozli kiritish"), "voice", true))
            add(sw(t("Режим диалога", "Conversation mode", "Suhbat rejimi"), "conv", false,
                t("Слушать снова после ответа", "Listen again after a reply", "Javobdan keyin yana tinglash")))
            add(sw(t("Звать по имени: «Bubby»", "Call by name: “Bubby”", "Ism bilan chaqirish: «Bubby»"), "hotword", true,
                t("Микрофон всегда включён, батарея садится быстрее",
                    "The microphone stays on and the battery drains faster",
                    "Mikrofon doim yoniq, batareya tezroq sarflanadi")))
            add(sw(t("Отвечать на звонки сам", "Pick up calls by itself", "Qo'ng'iroqqa o'zi javob berish"), "call_auto", false,
                t("Если вы молчите, через 7 секунд берёт трубку", "If you say nothing, answers after 7 seconds", "Jim tursangiz, 7 soniyadan keyin o'zi ko'taradi")))
            add(sw(t("ИИ говорит по телефону", "AI talks on the call", "AI qo'ng'iroqda gaplashadi"), "call_talk", false,
                t("Если Bubby берёт трубку сам: включает громкую связь и говорит с абонентом, потом присылает итог. Экспериментально",
                    "When Bubby picks up by itself: turns on the speaker, talks to the caller, then tells you what it was about. Experimental",
                    "Bubby o'zi ko'targanda: karnayni yoqib, qo'ng'iroq qiluvchi bilan gaplashadi, keyin nima haqida ekanini aytadi. Tajribaviy")))
            add(sw(t("Зачитывать уведомления", "Read notifications aloud", "Bildirishnomalarni ovoz chiqarib o'qish"), "read_notifs", false,
                t("Нужен доступ к уведомлениям; читает вслух только при включённом экране",
                    "Needs notification access; reads aloud only while the screen is on",
                    "Bildirishnomalarga ruxsat kerak; faqat ekran yoniq paytida o'qiydi. SMS lar bunga kirmaydi")))
            add(sw(t("Зачитывать входящие SMS", "Read incoming SMS aloud", "Kelgan SMS larni ovoz chiqarib o'qish"), "read_sms_aloud", false,
                t("Отдельный переключатель: SMS не читаются вслух, пока он выключен", "Its own switch: SMS are never read aloud while it is off", "Alohida tugma: o'chiq bo'lsa, SMS lar hech qachon ovoz chiqarib o'qilmaydi")))
            add(sw(t("Читать вслух только в наушниках", "Read aloud only in headphones", "Faqat quloqchinda o'qish"), "read_private", true,
                t("Сообщения не звучат на весь дом или туалет", "Messages are never played on the speaker for others to hear", "Xabarlar karnayda boshqalarga eshitilmaydi")))
            add(sw(t("Режим учёбы", "Study mode", "O'qish rejimi"), "study", false,
                t("Bubby объясняет шаг за шагом и проверяет вас вопросами", "Bubby explains step by step and quizzes you",
                    "Bubby bosqichma-bosqich tushuntiradi va savollar bilan tekshiradi")))
        }

        group(c, t("Поведение Bubby", "Bubby's behaviour", "Bubby xulq-atvori"), R.drawable.ic_bolt) {
            add(sw(t("Разрешить Bubby читать экран", "Let Bubby read the screen", "Bubby ekranni o'qishiga ruxsat"), "screen_agent", true,
                t("Нужно, чтобы нажимать и печатать за вас. Банки и пароли не читаются",
                    "Needed to tap and type for you. Banking and password apps are never read",
                    "Siz uchun bosish va yozish uchun kerak. Bank va parollar o'qilmaydi")))
            add(sw(t("Спрашивать перед чтением экрана", "Ask before reading the screen", "Ekranni o'qishdan oldin so'rash"), "screen_ask", true,
                t("Экран читается только после вашего «Да» для этой задачи",
                    "The screen is read only after your “Yes” for that task",
                    "Ekran faqat shu vazifa uchun «Ha» deganingizdan keyin o'qiladi")))
            add(sw(t("Умные инструменты ИИ", "Smart AI tools", "Aqlli AI vositalari"), "native_tools", true,
                t("Для задач в несколько шагов. Выключите, если ИИ-сервер даёт ошибки",
                    "For multi-step tasks. Turn off if your AI server gives errors",
                    "Ko'p bosqichli vazifalar uchun. AI server xato bersa o'chiring")))
            add(ctx.switchRow(t("Разрешить Bubby читать SMS", "Let Bubby read SMS", "Bubby SMS o'qishiga ruxsat"),
                t("Только по просьбе; коды и банковские SMS скрыты",
                    "Only on request; codes and bank SMS are hidden",
                    "Faqat so'rov bo'yicha; kodlar va bank SMS'lari yashirin"),
                Backend.bool(ctx, "sms_read", false)) { on -> Backend.setBool(ctx, "sms_read", on); if (on) Backend.requestPermissions(act, "smsRead") })
            add(sw(t("Реагировать на зарядку и наушники", "React to charging and headphones", "Zaryadka va quloqchinga munosabat bildirish"), "device_reactions", true))
            add(sw(t("Стикеры при реакциях", "Stickers on reactions", "Reaksiyalarda stiklerlar"), "stickers_on_events", true))
            add(sw(t("Отвечать стикерами", "Reply with stickers", "Stikerlar bilan javob berish"), "sticker_auto", true))
            add(sw(t("Запускать после перезагрузки", "Start after reboot", "Telefon qayta yonganda ishga tushirish"), "autostart", true))
        }

        group(c, t("Сообщения", "Messages", "Xabarlar"), R.drawable.ic_bell) {
            add(ctx.text(t("Пауза перед отправкой", "Pause before sending", "Yuborishdan oldin pauza"), T.Title), MATCH, WRAP, bottom = 8)
            val cur = Backend.int(ctx, "message_delay", 4).let { if (it in listOf(0, 4, 8)) it else 4 }
            add(chips(listOf("0" to t("Сразу", "Immediately", "Darhol"), "4" to t("4 с", "4 s", "4 s"), "8" to t("8 с", "8 s", "8 s")), cur.toString()) {
                Backend.setInt(ctx, "message_delay", it.toInt())
            })
        }

        group(c, t("Вид Bubby", "Bubby look", "Bubby ko'rinishi"), R.drawable.ic_palette) {
            add(ctx.text(t("Где живёт Bubby", "Where Bubby lives", "Bubby qayerda turadi"), T.Title), MATCH, WRAP)
            add(ctx.text(t("В строке состояния он не закрывает видео. Его можно сдвинуть пальцем в любой момент",
                "In the status bar it never covers a video. You can still drag him anywhere",
                "Holat panelida u videoni yopmaydi. Xohlagan payt barmoq bilan surib qo'yish mumkin"), T.Small), MATCH, WRAP, top = 2, bottom = 8)
            val place = Backend.str(ctx, "status_dock")
            add(chips(listOf(
                "status_right" to t("Справа, у батареи", "Right, by the battery", "O'ngda, batareya yonida"),
                "status_center" to t("По центру, «остров»", "Centre, like an island", "O'rtada, «orol» kabi"),
                "status_left" to t("Слева, у часов", "Left, by the clock", "Chapda, soat yonida"),
                "top_right" to t("Обычное место", "Normal spot", "Oddiy joy")),
                if (place.startsWith("status_")) place else "top_right") {
                Backend.dockTo(ctx, it)
                if (it.startsWith("status_") && !com.example.buddy.BuddyAccess.enabled) ctx.toast(t("Чтобы Bubby реагировал на нажатия в строке состояния, включите «Управление экраном голосом» в разрешениях",
                    "For taps in the status bar, turn on “Control the screen by voice” in Permissions", "Holat qatorida bosish ishlashi uchun Ruxsatlarda «Ekranni ovoz bilan boshqarish»ni yoqing"))
            })
            add(ctx.text(t("В строке состояния нужна служба доступности: без неё Bubby там виден, но нажать на него нельзя",
                "The status bar needs the accessibility service: without it Bubby shows there but cannot be tapped",
                "Holat qatorida maxsus imkoniyatlar xizmati kerak: usiz Bubby u yerda ko'rinadi, lekin bosib bo'lmaydi"), T.Small), MATCH, WRAP, bottom = 6)
            add(sw(t("Значок состояния", "Status badge", "Holat belgisi"), "badge", true,
                t("Маленький значок на голове: думает, слушает, ждёт подтверждения, лимит, ошибка, готово",
                    "A small badge on the head: thinking, listening, waiting for approval, limit, error, done",
                    "Boshdagi kichik belgi: o'ylayapti, tinglayapti, tasdiq kutyapti, limit, xato, tayyor")))
            add(sw(t("Цветное свечение", "Coloured glow", "Rangli nur"), "state_glow", true,
                t("Мягкое свечение цвета текущего состояния вокруг Bubby", "A soft glow around Bubby in the colour of the current state",
                    "Bubby atrofida joriy holat rangidagi yumshoq nur")))
            add(ctx.row {
                add(ctx.text(t("Питомец и наряды", "Pet and outfits", "Uy hayvoni va kiyimlar"), T.Title), 0, WRAP, 1f)
                add(ctx.icon(R.drawable.ic_chevron_right, P.onSurfaceVariant, 22), WRAP, WRAP)
                setPadding(0, ctx.dp(12), 0, ctx.dp(4))
                press { act.open("outfits") }
            }, MATCH, WRAP)
        }


        group(c, t("Не мешать при просмотре", "Stay out of the way during videos", "Video ko'rganda halaqit bermasin"), R.drawable.ic_play) {
            add(sw(t("Скрыть Bubby сейчас", "Hide Bubby now", "Bubby'ni hozir yashirish"), "quiet", false,
                t("Вернуть можно здесь, из шторки уведомлений или голосом: «Bubby»",
                    "You can bring him back here, from the notification shade, or by voice: “Bubby”",
                    "Uni shu yerdan, bildirishnomalar pardasidan yoki ovoz bilan qaytarish mumkin: «Bubby»")))
            add(sw(t("Прятать в полноэкранном видео", "Hide during fullscreen video", "To'liq ekran videoda yashirish"), "autohide", true,
                t("Bubby сам скрывается, когда видео на весь экран, и возвращается после",
                    "Bubby hides on his own when a video is fullscreen and comes back afterwards",
                    "Video butun ekranda bo'lganda Bubby o'zi yashirinadi va keyin qaytadi")))
        }

        group(c, t("Характер", "Personality", "Xarakter"), R.drawable.ic_face) {
            val cur0 = Backend.str(ctx, "persona").let { legacy[it] ?: it }
            val persona = ctx.field(t("Характер / настроение", "Personality / mood", "Xarakter / kayfiyat"), cur0)
            var pending: Runnable? = null
            persona.addTextChangedListener(object : android.text.TextWatcher {
                override fun afterTextChanged(s: android.text.Editable?) {
                    pending?.let { persona.removeCallbacks(it) }
                    pending = Runnable { Backend.setStr(ctx, "persona", persona.text.toString().trim()) }.also { persona.postDelayed(it, 400) }
                }
                override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c2: Int) {}
                override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c2: Int) {}
            })
            add(persona, MATCH, WRAP)
            val flow = FlowLayout(ctx)
            for (m in moods) flow.addView(ctx.chip(moodLabel(m), cur0 == m) {
                persona.setText(m); Backend.setStr(ctx, "persona", m)
                pending?.let { persona.removeCallbacks(it) }
                refresh()
            }, LinearLayout.LayoutParams(WRAP, WRAP).apply { setMargins(0, 0, ctx.dp(8), ctx.dp(8)) })
            add(flow, MATCH, WRAP, top = 12)
        }
    }
}
