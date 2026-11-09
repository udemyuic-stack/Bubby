package com.example.buddy.ui

import android.content.res.ColorStateList
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.Voice
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.SeekBar
import com.example.buddy.CallProfiles
import com.example.buddy.CallPrompt
import com.example.buddy.R
import com.example.buddy.Tr
import java.util.Locale
import kotlin.math.roundToInt

class CallAssistantScreen(act: UiActivity) : Screen(act) {
    override fun title() = t("ИИ на звонках", "AI on calls", "Qo'ng'iroqlarda AI")

    private val prefs get() = Backend.prefs(ctx)
    private val main = Handler(Looper.getMainLooper())
    private var profiles: MutableList<CallProfiles.Profile> = mutableListOf()
    private var editId: String? = null
    private var pendingSave: Runnable? = null
    private var tts: TextToSpeech? = null
    private var ttsState = 0
    private var voiceBox: LinearLayout? = null
    private var watching = false

    private val langs = listOf("uz", "ru", "en")

    private fun langName(l: String) = when (l) {
        "uz" -> t("Узбекский", "Uzbek", "O'zbekcha")
        "ru" -> t("Русский", "Russian", "Ruscha")
        else -> t("Английский", "English", "Inglizcha")
    }

    private fun appLang() = Tr.lang(ctx)

    private fun load() {
        profiles = CallProfiles.load(prefs.getString(CallProfiles.KEY_LIST, null)).toMutableList()
        if (editId == null || profiles.none { it.id == editId }) editId = (profiles.firstOrNull { it.id == defaultId() } ?: profiles.firstOrNull())?.id
    }

    private fun store() { prefs.edit().putString(CallProfiles.KEY_LIST, CallProfiles.save(profiles)).apply() }

    private fun defaultId() = prefs.getString(CallProfiles.KEY_DEFAULT, null)
    private fun setId(key: String, id: String?) { prefs.edit().putString(key, id).apply() }

    private fun update(id: String, f: (CallProfiles.Profile) -> CallProfiles.Profile) {
        val i = profiles.indexOfFirst { it.id == id }
        if (i >= 0) profiles[i] = f(profiles[i])
    }

    private fun scheduleSave() {
        pendingSave?.let { main.removeCallbacks(it) }
        val r = Runnable { pendingSave = null; store() }
        pendingSave = r
        main.postDelayed(r, 800)
    }

    private fun flush() {
        pendingSave?.let { main.removeCallbacks(it); pendingSave = null; store() }
    }

    private fun watchLifecycle(c: LinearLayout) {
        if (watching) return
        watching = true
        c.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: View) {}
            override fun onViewDetachedFromWindow(v: View) {
                flush()
                try { tts?.stop(); tts?.shutdown() } catch (_: Exception) {}
                tts = null; ttsState = 0
            }
        })
    }

    override fun onShow() { reshow() }

    override fun render(c: LinearLayout) {
        watchLifecycle(c)
        flush()
        load()
        c.add(ctx.card(pad = 16) {
            add(ctx.text(t("Как это работает", "How it works", "Bu qanday ishlaydi"), T.Title), MATCH, WRAP)
            add(ctx.text(t("Когда Bubby берёт трубку, он включает громкую связь и говорит с абонентом по вашим инструкциям. Он всегда говорит, что он ИИ-помощник. Положите телефон рядом: звук идёт через динамик, шум в комнате слышен абоненту.",
                "When Bubby picks up, it turns on the speaker and talks to the caller following your instructions. It always says it is an AI assistant. Keep the phone close: sound goes through the loudspeaker, room noise reaches the caller.",
                "Bubby trubkani ko'targanda karnayni yoqadi va ko'rsatmalaringiz bo'yicha gaplashadi. U doim AI yordamchi ekanini aytadi. Telefonni yaqin qo'ying: ovoz karnay orqali chiqadi, xona shovqini ham eshitiladi."), T.Small), MATCH, WRAP, top = 4)
            add(ctx.switchRow(t("ИИ говорит по телефону", "AI talks on the call", "AI qo'ng'iroqda gaplashadi"),
                t("Работает, когда Bubby запущен", "Works while Bubby is running", "Bubby ishlab turganda ishlaydi"),
                Backend.bool(ctx, "call_talk", false)) { v -> Backend.setBool(ctx, "call_talk", v) }, MATCH, WRAP, top = 6)
        }, MATCH, WRAP)

        profilesSection(c)
        editor(c)
        assignment(c)

        c.add(ctx.section(t("Голос", "Voice", "Ovoz")))
        val box = ctx.card(pad = 14)
        voiceBox = box
        c.add(box, MATCH, WRAP)
        renderVoices()
    }

    private fun profilesSection(c: LinearLayout) {
        c.add(ctx.section(t("Профили", "Profiles", "Profillar")))
        if (profiles.isEmpty()) {
            c.add(ctx.card(pad = 14) {
                add(ctx.text(t("Пока используются встроенные инструкции: узнать, кто звонит и зачем, и принять сообщение.",
                    "Built-in instructions are used for now: find out who is calling and why, and take a message.",
                    "Hozircha ichki ko'rsatmalar ishlatiladi: kim va nima uchun qo'ng'iroq qilayotganini bilib, xabar qabul qilish."), T.Small), MATCH, WRAP)
                add(ctx.button(t("Создать из шаблона", "Create from a template", "Shablondan yaratish"), BtnStyle.Filled, R.drawable.ic_add) { pickTemplate() }, WRAP, WRAP, top = 10)
            }, MATCH, WRAP)
            return
        }
        val def = defaultId()
        val contacts = prefs.getString(CallProfiles.KEY_CONTACTS, null)
        val unknown = prefs.getString(CallProfiles.KEY_UNKNOWN, null)
        for (p in profiles) {
            val sel = p.id == editId
            c.add(ctx.card(pad = 14, stroke = if (sel) P.primary else P.outlineVariant, onClick = { flush(); editId = p.id; refresh() }) {
                add(ctx.row {
                    add(ctx.icon(if (sel) R.drawable.ic_radio_on else R.drawable.ic_radio_off, if (sel) P.primary else P.onSurfaceVariant, 24), WRAP, WRAP, end = 12)
                    add(ctx.col {
                        add(ctx.text(p.name.ifBlank { t("Без названия", "Untitled", "Nomsiz") }, T.Title), MATCH, WRAP)
                        add(ctx.row {
                            if (p.id == def || (def == null && p == profiles.first())) add(ctx.pill(t("По умолчанию", "Default", "Asosiy"), P.primary), WRAP, WRAP, end = 6)
                            if (p.id == contacts) add(ctx.pill(t("Контакты", "Contacts", "Kontaktlar"), P.success, dot = false), WRAP, WRAP, end = 6)
                            if (p.id == unknown) add(ctx.pill(t("Незнакомые", "Unknown", "Notanish"), P.outline, dot = false), WRAP, WRAP, end = 6)
                            if (CallProfiles.validate(p.prompt) != null) add(ctx.pill(t("Не используется", "Not used", "Ishlatilmaydi"), P.error), WRAP, WRAP)
                        }, MATCH, WRAP, top = 6)
                    }, 0, WRAP, 1f)
                })
            }, MATCH, WRAP, bottom = 8)
        }
        c.add(ctx.button(t("Новый профиль из шаблона", "New profile from a template", "Shablondan yangi profil"), BtnStyle.Tonal, R.drawable.ic_add) { pickTemplate() }, WRAP, WRAP)
    }

    private fun pickTemplate() {
        val lang = appLang()
        val items = CallProfiles.TEMPLATES.map { it.name(lang) }.toTypedArray()
        ctx.dialogBuilder().setTitle(t("Шаблон", "Template", "Shablon")).setItems(items) { _, i ->
            flush()
            val p = CallProfiles.fromTemplate("p" + System.currentTimeMillis(), CallProfiles.TEMPLATES[i].id, lang)
            profiles.add(p)
            store()
            if (profiles.size == 1) setId(CallProfiles.KEY_DEFAULT, p.id)
            editId = p.id
            refresh()
        }.setNegativeButton(t("Отмена", "Cancel", "Bekor qilish"), null).show()
    }

    private fun problemText(p: CallProfiles.Problem?): String? = when (p) {
        CallProfiles.Problem.EMPTY -> t("Инструкции пустые: на звонках используются встроенные.", "Instructions are empty: built-in ones are used on calls.", "Ko'rsatmalar bo'sh: qo'ng'iroqlarda ichki ko'rsatmalar ishlatiladi.")
        CallProfiles.Problem.TOO_LONG -> t("Больше ${CallProfiles.MAX_PROMPT} символов: на звонках используются встроенные.", "Over ${CallProfiles.MAX_PROMPT} characters: built-in ones are used on calls.", "${CallProfiles.MAX_PROMPT} belgidan ko'p: qo'ng'iroqlarda ichki ko'rsatmalar ishlatiladi.")
        null -> null
    }

    private fun editor(c: LinearLayout) {
        val p = profiles.firstOrNull { it.id == editId } ?: return
        val id = p.id
        c.add(ctx.section(t("Инструкции", "Instructions", "Ko'rsatmalar")))
        c.add(ctx.card(pad = 14) {
            add(ctx.text(t("Название", "Name", "Nomi"), T.Label), MATCH, WRAP, bottom = 6)
            val name = ctx.field(t("Название профиля", "Profile name", "Profil nomi"), p.name)
            name.addTextChangedListener(watcher { s -> update(id) { it.copy(name = s) }; scheduleSave() })
            add(name, MATCH, WRAP)
            add(ctx.text(t("Что должен делать ИИ", "What the AI should do", "AI nima qilishi kerak"), T.Label), MATCH, WRAP, top = 14, bottom = 6)
            val prompt: EditText = ctx.field(t("Например: узнай, кто звонит и зачем, и скажи, что я перезвоню", "For example: find out who is calling and why, and say I will call back", "Masalan: kim va nima uchun qo'ng'iroq qilayotganini bilib ol, qayta qo'ng'iroq qilishimni ayt"), p.prompt, multiline = true)
            prompt.minLines = 6
            add(prompt, MATCH, WRAP)
            val counter = ctx.text("", T.Small)
            val problem = ctx.text("", T.Small, P.error)
            fun show(s: String) {
                counter.text = "${s.length} / ${CallProfiles.MAX_PROMPT}"
                counter.setTextColor(if (s.length > CallProfiles.MAX_PROMPT) P.error else Glass.TEXT_LABEL)
                val msg = problemText(CallProfiles.validate(s))
                problem.text = msg ?: ""
                problem.visibility = if (msg == null) View.GONE else View.VISIBLE
            }
            show(p.prompt)
            prompt.addTextChangedListener(watcher { s -> update(id) { it.copy(prompt = s) }; show(s); scheduleSave() })
            add(ctx.row {
                add(problem, 0, WRAP, 1f, end = 8)
                add(counter.apply { gravity = Gravity.END }, WRAP, WRAP)
            }, MATCH, WRAP, top = 6)
            add(ctx.text(t("Правила безопасности добавляются всегда и важнее этих инструкций: ИИ не называет коды и пароли, ничего не обещает за вас и не выдаёт себя за вас.",
                "Safety rules are always added and win over these instructions: the AI never gives codes or passwords, promises nothing for you and never pretends to be you.",
                "Xavfsizlik qoidalari doim qo'shiladi va bu ko'rsatmalardan ustun: AI kod va parollarni aytmaydi, siz uchun va'da bermaydi va o'zini siz deb ko'rsatmaydi."), T.Small), MATCH, WRAP, top = 10)
            val flow = FlowLayout(ctx)
            fun btn(v: View) = flow.addView(v, LinearLayout.LayoutParams(WRAP, WRAP).apply { setMargins(0, 0, ctx.dp(8), ctx.dp(8)) })
            if (CallProfiles.template(p.template)?.let { it.id != CallProfiles.CUSTOM } == true) {
                btn(ctx.button(t("Вернуть шаблон", "Reset to template", "Shablonga qaytarish"), BtnStyle.Tonal, R.drawable.ic_edit) {
                    ctx.confirm(t("Вернуть шаблон?", "Reset to template?", "Shablonga qaytarilsinmi?"),
                        t("Ваш текст заменится текстом шаблона.", "Your text will be replaced with the template text.", "Matningiz shablon matni bilan almashtiriladi."),
                        t("Вернуть", "Reset", "Qaytarish"), t("Отмена", "Cancel", "Bekor qilish")) {
                        val tp = CallProfiles.template(p.template) ?: return@confirm
                        update(id) { it.copy(prompt = tp.prompt(appLang())) }; store(); refresh()
                    }
                })
            }
            if (id != defaultId()) btn(ctx.button(t("Сделать основным", "Make default", "Asosiy qilish"), BtnStyle.Tonal, R.drawable.ic_check) {
                flush(); setId(CallProfiles.KEY_DEFAULT, id); refresh()
            })
            if (profiles.size > 1) btn(ctx.button(t("Удалить", "Delete", "O'chirish"), BtnStyle.Outline, R.drawable.ic_delete) {
                ctx.confirm(t("Удалить профиль?", "Delete the profile?", "Profil o'chirilsinmi?"), p.name,
                    t("Удалить", "Delete", "O'chirish"), t("Отмена", "Cancel", "Bekor qilish")) {
                    flush()
                    profiles.removeAll { it.id == id }
                    store()
                    for (k in listOf(CallProfiles.KEY_DEFAULT, CallProfiles.KEY_CONTACTS, CallProfiles.KEY_UNKNOWN))
                        if (prefs.getString(k, null) == id) setId(k, null)
                    editId = null
                    refresh()
                }
            })
            add(flow, MATCH, WRAP, top = 12)
        }, MATCH, WRAP)
    }

    private fun watcher(on: (String) -> Unit) = object : TextWatcher {
        override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
        override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
        override fun afterTextChanged(s: Editable?) { on(s?.toString() ?: "") }
    }

    private fun assignment(c: LinearLayout) {
        if (profiles.size < 2) return
        c.add(ctx.section(t("Кому какой профиль", "Which profile for whom", "Kimga qaysi profil")))
        c.add(ctx.card(pad = 14) {
            for ((key, label) in listOf(
                CallProfiles.KEY_CONTACTS to t("Звонят из контактов", "Calls from contacts", "Kontaktlardan qo'ng'iroq"),
                CallProfiles.KEY_UNKNOWN to t("Незнакомые номера", "Unknown numbers", "Notanish raqamlar"))) {
                val cur = prefs.getString(key, null)
                add(ctx.text(label, T.Title), MATCH, WRAP, top = if (key == CallProfiles.KEY_CONTACTS) 0 else 14, bottom = 8)
                val flow = FlowLayout(ctx)
                val opts = listOf<Pair<String?, String>>(null to t("Основной", "Default", "Asosiy")) + profiles.map { it.id to it.name.ifBlank { "…" } }
                for ((id, name) in opts) flow.addView(ctx.chip(name, id == cur || (id == null && profiles.none { it.id == cur })) {
                    flush(); setId(key, id); refresh()
                }, LinearLayout.LayoutParams(WRAP, WRAP).apply { setMargins(0, 0, ctx.dp(8), ctx.dp(8)) })
                add(flow, MATCH, WRAP)
            }
        }, MATCH, WRAP)
    }

    private fun ensureTts() {
        if (tts != null) return
        ttsState = 1
        var engine: TextToSpeech? = null
        engine = TextToSpeech(ctx.applicationContext) { st ->
            main.post {
                if (tts !== engine) { try { engine?.shutdown() } catch (_: Exception) {}; return@post }
                ttsState = if (st == TextToSpeech.SUCCESS) 2 else 3
                renderVoices()
            }
        }
        tts = engine
    }

    private fun installed(v: Voice) = !v.features.orEmpty().contains(TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED)

    private fun voicesFor(lang: String): List<Voice> {
        val all = try { tts?.voices.orEmpty() } catch (_: Exception) { emptySet() }
        return all.filter { installed(it) && (it.locale.language == lang || runCatching { it.locale.isO3Language }.getOrNull() == Locale(lang).isO3Language) }
            .sortedWith(compareBy({ it.isNetworkConnectionRequired }, { -it.quality }, { it.name }))
    }

    private fun renderVoices() {
        val box = voiceBox ?: return
        box.removeAllViews()
        ensureTts()
        when (ttsState) {
            1 -> { box.add(ctx.text(t("Загружаю голоса телефона…", "Loading the phone's voices…", "Telefon ovozlari yuklanmoqda…"), T.Small), MATCH, WRAP); return }
            3 -> { box.add(ctx.text(t("Синтез речи на телефоне не запустился. Проверьте Настройки → Специальные возможности → Синтез речи.", "Text-to-speech did not start on this phone. Check Settings, Accessibility, Text-to-speech.", "Telefonda nutq sintezi ishga tushmadi. Sozlamalar → Maxsus imkoniyatlar → Nutq sintezini tekshiring."), T.Small, P.error), MATCH, WRAP); return }
        }
        box.add(ctx.text(t("Только голоса, установленные на этом телефоне.", "Only the voices installed on this phone.", "Faqat shu telefonga o'rnatilgan ovozlar."), T.Small), MATCH, WRAP)
        for (lang in langs) {
            var list = voicesFor(lang)
            var note: String? = null
            if (list.isEmpty() && lang == "uz") {
                list = voicesFor("tr")
                note = if (list.isNotEmpty()) t("Узбекского голоса на телефоне нет. Узбекский текст читает турецкий голос.", "There is no Uzbek voice on this phone. A Turkish voice reads Uzbek text.", "Telefonda o'zbekcha ovoz yo'q. O'zbekcha matnni turkcha ovoz o'qiydi.")
                else t("Узбекского и турецкого голоса нет: читает голос телефона по умолчанию.", "No Uzbek or Turkish voice: the phone's default voice reads.", "O'zbekcha va turkcha ovoz yo'q: telefonning asosiy ovozi o'qiydi.")
            } else if (list.isEmpty()) {
                note = t("Голосов для этого языка нет: читает голос по умолчанию.", "No voices for this language: the default voice reads.", "Bu til uchun ovoz yo'q: asosiy ovoz o'qiydi.")
            }
            val key = "call_voice_$lang"
            val cur = prefs.getString(key, "") ?: ""
            box.add(ctx.row {
                add(ctx.text(langName(lang), T.Title), 0, WRAP, 1f)
                add(ctx.button(t("Послушать", "Listen", "Tinglash"), BtnStyle.Text, R.drawable.ic_play) { preview(lang) }, WRAP, WRAP)
            }, MATCH, WRAP, top = 14)
            if (note != null) box.add(ctx.text(note, T.Small), MATCH, WRAP, bottom = 6)
            if (list.isEmpty()) continue
            val flow = FlowLayout(ctx)
            fun add(v: View) = flow.addView(v, LinearLayout.LayoutParams(WRAP, WRAP).apply { setMargins(0, 0, ctx.dp(8), ctx.dp(8)) })
            add(ctx.chip(t("По умолчанию", "Default", "Asosiy"), list.none { it.name == cur }) { prefs.edit().putString(key, "").apply(); renderVoices() })
            list.forEachIndexed { i, v ->
                val marks = buildList {
                    if (v.quality >= Voice.QUALITY_HIGH) add(t("высокое качество", "high quality", "yuqori sifat"))
                    if (v.isNetworkConnectionRequired) add(t("нужен интернет", "needs internet", "internet kerak"))
                }
                val region = v.locale.getDisplayCountry(Locale(appLang())).ifBlank { v.locale.getDisplayLanguage(Locale(appLang())) }
                val label = "$region ${i + 1}" + if (marks.isEmpty()) "" else " · " + marks.joinToString(", ")
                add(ctx.chip(label, v.name == cur) { prefs.edit().putString(key, v.name).apply(); renderVoices(); preview(lang) })
            }
            box.add(flow, MATCH, WRAP)
        }
        box.add(slider(t("Скорость", "Speed", "Tezlik"), "call_voice_rate", 0.7f, 1.4f), MATCH, WRAP, top = 14)
        box.add(slider(t("Высота", "Pitch", "Ohang"), "call_voice_pitch", 0.8f, 1.2f), MATCH, WRAP, top = 8)
    }

    private fun slider(label: String, key: String, min: Float, max: Float): View = ctx.col {
        val steps = ((max - min) / 0.05f).roundToInt()
        var v = prefs.getFloat(key, 1f).coerceIn(min, max)
        val value = ctx.text("×" + "%.2f".format(v), T.Body)
        add(ctx.row {
            add(ctx.text(label, T.Title), 0, WRAP, 1f)
            add(value, WRAP, WRAP)
        }, MATCH, WRAP)
        add(SeekBar(ctx).apply {
            this.max = steps
            progress = ((v - min) / 0.05f).roundToInt()
            minimumHeight = ctx.dp(48)
            progressTintList = ColorStateList.valueOf(P.primary)
            thumbTintList = ColorStateList.valueOf(P.primary)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(s: SeekBar?, p: Int, fromUser: Boolean) {
                    if (!fromUser) return
                    v = ((min + p * 0.05f) * 100).roundToInt() / 100f
                    value.text = "×" + "%.2f".format(v)
                    prefs.edit().putFloat(key, v).apply()
                }
                override fun onStartTrackingTouch(s: SeekBar?) {}
                override fun onStopTrackingTouch(s: SeekBar?) {}
            })
        }, MATCH, WRAP)
    }

    private fun preview(lang: String) {
        val engine = tts ?: return
        if (ttsState != 2) return
        try {
            engine.stop()
            val name = prefs.getString("call_voice_$lang", "") ?: ""
            val fallback = if (lang == "uz" && voicesFor("uz").isEmpty()) "tr" else lang
            engine.language = Locale(fallback)
            if (name.isNotEmpty()) engine.voices?.firstOrNull { it.name == name }?.let { engine.voice = it }
            engine.setSpeechRate(prefs.getFloat("call_voice_rate", 1f))
            engine.setPitch(prefs.getFloat("call_voice_pitch", 1f))
            engine.speak(CallPrompt.greeting(lang), TextToSpeech.QUEUE_FLUSH, null, "preview")
        } catch (e: Exception) {
            ctx.toast(t("Не получилось воспроизвести", "Could not play it", "Ijro etib bo'lmadi"))
        }
    }
}
