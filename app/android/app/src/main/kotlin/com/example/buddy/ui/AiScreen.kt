package com.example.buddy.ui

import android.content.ClipboardManager
import android.content.Context
import android.text.method.HideReturnsTransformationMethod
import android.text.method.PasswordTransformationMethod
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import com.example.buddy.R
import com.example.buddy.Tr

/** Where the user adds, tests, orders and deletes AI connections. */
class AiScreen(act: UiActivity) : Screen(act) {
    override fun title() = t("Подключение ИИ", "AI connection", "AI ulanishi")

    private class Provider(
        val id: String, val name: String, val icon: Int, val tagline: String, val url: String, val siteLabel: String,
        val steps: List<String>, val note: String, val models: List<String> = emptyList(), val custom: Boolean = false
    )

    private var keys: List<String> = emptyList()
    private var open: String? = null
    private var advanced = false
    private var status: String? = null
    private var statusOk = false
    private var testing = -1
    private var clip: String? = null
    private var busy: String? = null
    private var groqDone: List<String>? = null
    private val errors = HashMap<String, String>()
    private val picked = HashMap<String, Int>()
    private val fields = HashMap<String, EditText>()
    private var firstLoad = true

    private fun field(id: String, hint: String, secret: Boolean = false): EditText = fields.getOrPut(id) {
        ctx.field(hint, secret = secret).apply { setSingleLine(); if (secret) transformationMethod = PasswordTransformationMethod.getInstance() }
    }.also { (it.parent as? ViewGroup)?.removeView(it) }

    private fun providers() = listOf(
        Provider("openrouter", "OpenRouter", R.drawable.ic_link,
            t("Бесплатные модели, в том числе Gemma 4", "Free models, including Gemma 4", "Bepul modellar, jumladan Gemma 4"),
            "https://openrouter.ai/keys", "openrouter.ai/keys",
            listOf(t("Откройте openrouter.ai/keys", "Open openrouter.ai/keys", "openrouter.ai/keys sahifasini oching"),
                t("Войдите и нажмите Create Key", "Sign in and press Create Key", "Hisobingizga kiring va Create Key tugmasini bosing"),
                t("Скопируйте ключ sk-or-…", "Copy the key sk-or-…", "sk-or-… kalitini nusxalang"),
                t("Вставьте его сюда", "Paste it here", "Uni shu yerga qo'ying")),
            t("Бесплатные модели: примерно 20 запросов в минуту и около 50 в день. Это число может меняться — проверьте на сайте.",
                "Free models: about 20 requests per minute and around 50 per day. These numbers may change — check the website.",
                "Bepul modellar: daqiqasiga taxminan 20 ta va kuniga taxminan 50 ta so'rov. Bu raqamlar o'zgarishi mumkin — saytdan tekshiring."),
            listOf("google/gemma-4-31b-it:free", "google/gemma-4-26b-a4b-it:free", "nvidia/nemotron-3-super-120b-a12b:free", "openrouter/free")),
        Provider("gemini", "Google Gemini", R.drawable.ic_sparkle,
            t("Ключ AIza… из Google AI Studio", "AIza… key from Google AI Studio", "Google AI Studio'dan AIza… kaliti"),
            "https://aistudio.google.com/apikey", "aistudio.google.com",
            listOf(t("Откройте aistudio.google.com", "Open aistudio.google.com", "aistudio.google.com sahifasini oching"),
                t("Нажмите Get API key", "Press Get API key", "Get API key tugmasini bosing"),
                t("Скопируйте ключ AIza…", "Copy the key AIza…", "AIza… kalitini nusxalang"),
                t("Вставьте его сюда", "Paste it here", "Uni shu yerga qo'ying")),
            t("Бесплатных запросов в день немного — они могут закончиться.", "There are only a few free requests per day — they can run out.", "Kuniga bepul so'rovlar kam — ular tugab qolishi mumkin.")),
        Provider("anthropic", "Anthropic", R.drawable.ic_face,
            t("Платный сервис (Claude)", "Paid service (Claude)", "Pullik xizmat (Claude)"),
            "https://console.anthropic.com/settings/keys", "console.anthropic.com",
            listOf(t("Откройте console.anthropic.com", "Open console.anthropic.com", "console.anthropic.com sahifasini oching"),
                t("Пополните баланс и создайте ключ в API Keys", "Top up your balance and create a key in API Keys", "Balansni to'ldiring va API Keys bo'limida kalit yarating"),
                t("Скопируйте ключ sk-ant-…", "Copy the key sk-ant-…", "sk-ant-… kalitini nusxalang"),
                t("Вставьте его сюда", "Paste it here", "Uni shu yerga qo'ying")),
            t("Сервис платный: расходуется баланс вашего аккаунта.", "The service is paid: your account balance is used up.", "Xizmat pullik: hisobingizdagi balans sarflanadi.")),
        Provider("custom", t("Свой сервер / Ollama", "Custom server / Ollama", "O'z serveringiz / Ollama"), R.drawable.ic_wifi,
            t("Любой OpenAI-совместимый сервер", "Any OpenAI-compatible server", "Har qanday OpenAI bilan mos server"),
            "https://ollama.com", "ollama.com",
            listOf(t("Запустите Ollama или другой сервер на своём компьютере", "Run Ollama or another server on your computer", "Kompyuteringizda Ollama yoki boshqa serverni ishga tushiring"),
                t("Узнайте его адрес, например http://192.168.1.5:11434", "Find out its address, for example http://192.168.1.5:11434", "Uning manzilini bilib oling, masalan http://192.168.1.5:11434"),
                t("Укажите адрес и название модели ниже", "Enter the address and the model name below", "Manzil va model nomini quyida kiriting")),
            t("Работает только пока этот компьютер включён и доступен телефону (например, в одной Wi-Fi сети).",
                "Works only while this computer is on and reachable from the phone (for example, on the same Wi-Fi network).",
                "Faqat shu kompyuter yoqilgan va telefon unga ulana olgandagina ishlaydi (masalan, bitta Wi-Fi tarmog'ida)."), custom = true),
    )

    override fun onShow() {
        keys = Backend.aiKeys(ctx)
        if (firstLoad) { firstLoad = false; if (keys.isEmpty()) open = "groq" }
        checkClipboard()
        refresh()
    }

    private fun looksLikeKey(s: String) = !s.contains(Regex("\\s")) && s.length > 20 && (s.startsWith("gsk_") || s.startsWith("sk-or-") || s.startsWith("AIza"))

    private fun clipboardText(): String = try {
        (ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).primaryClip?.getItemAt(0)?.coerceToText(ctx)?.toString()?.trim().orEmpty()
    } catch (_: Exception) { "" }

    private fun checkClipboard() {
        val s = clipboardText()
        clip = if (looksLikeKey(s) && keys.none { it.contains(s) }) s else null
    }

    private fun useClip() {
        val s = clip ?: return
        val id = when { s.startsWith("gsk_") -> "groq"; s.startsWith("sk-or-") -> "openrouter"; else -> "gemini" }
        clip = null; open = id
        if (id != "groq") advanced = true
        field(if (id == "groq") "groq" else id, "").setText(s)
        refresh()
    }

    private fun persist(next: List<String>) { Backend.saveAiKeys(ctx, next); keys = next }

    private fun move(i: Int, d: Int) {
        val j = i + d
        if (j < 0 || j >= keys.size) return
        val next = keys.toMutableList(); next.add(j, next.removeAt(i)); persist(next); refresh()
    }

    private fun delete(i: Int) {
        val info = AiApi.describe(ctx, keys[i])
        ctx.confirm(t("Удалить подключение?", "Delete this connection?", "Ulanish o'chirilsinmi?"),
            t("${info.title} ${info.masked} будет удалено с телефона.", "${info.title} ${info.masked} will be removed from this phone.", "${info.title} ${info.masked} telefondan o'chiriladi."),
            t("Удалить", "Delete", "O'chirish"), t("Отмена", "Cancel", "Bekor qilish")) {
            persist(keys.toMutableList().also { it.removeAt(i) }); refresh()
        }
    }

    private fun test(i: Int) {
        testing = i; status = null; refresh()
        val spec = keys[i]
        Thread {
            val (ok, raw) = AiApi.test(spec, ctx)
            act.runOnUiThread {
                testing = -1; statusOk = ok
                status = if (ok) t("Подключение работает", "The connection works", "Ulanish ishlayapti") else AiApi.hint(ctx, raw)
                refresh()
            }
        }.start()
    }

    private fun addSpec(spec: String): Boolean {
        if (spec in keys) { ctx.toast(t("Такое подключение уже добавлено", "This connection is already added", "Bunday ulanish allaqachon qo'shilgan")); return false }
        persist(keys + spec); open = null
        status = t("Подключение добавлено", "Connection added", "Ulanish qo'shildi"); statusOk = true
        return true
    }

    // ---- Groq quick start ----

    private fun connectGroq() {
        val f = field("groq", "")
        val key = f.text.toString().trim()
        if (!key.startsWith("gsk_") || key.contains(Regex("\\s")) || key.length <= 20) {
            errors["groq"] = t("Это не похоже на ключ Groq: он начинается с gsk_", "This does not look like a Groq key: it starts with gsk_", "Bu Groq kalitiga o'xshamaydi: u gsk_ bilan boshlanadi")
            refresh(); return
        }
        errors.remove("groq"); busy = "groq"; refresh()
        Thread {
            var err: String? = null
            var chosen: List<String> = emptyList()
            val (ids, msg) = AiApi.listModels(ctx, AiApi.GROQ_BASE, key)
            if (ids == null) {
                val low = msg.lowercase()
                err = if (low.contains("401") || low.contains("403")) t("Ключ не подходит: скопируйте его заново целиком", "The key does not fit: copy it again in full", "Kalit mos kelmadi: uni qaytadan to'liq nusxalang")
                else AiApi.groqHint(ctx, msg.ifEmpty { "network" })
            } else {
                chosen = AiApi.pickGroqModels(ids, Tr.lang(ctx))
                if (chosen.isEmpty()) err = t("У этого ключа нет доступных моделей", "This key has no available models", "Bu kalitda mavjud modellar yo'q")
                else {
                    val (ok, raw) = AiApi.test("${AiApi.GROQ_BASE}|${chosen.first()}|$key", ctx)
                    if (!ok) err = AiApi.groqHint(ctx, raw)
                }
            }
            act.runOnUiThread {
                busy = null
                if (err != null) errors["groq"] = err
                else {
                    persist(keys + chosen.map { "${AiApi.GROQ_BASE}|$it|$key" }.filter { it !in keys })
                    f.setText(""); groqDone = chosen
                }
                refresh()
            }
        }.start()
    }

    private fun pasteInto(f: EditText) { val s = clipboardText(); if (s.isNotEmpty()) f.setText(s) }

    private fun numbered(n: Int, text: String): View = ctx.row(Gravity.TOP) {
        add(ctx.row(Gravity.CENTER) {
            background = ctx.rounded(P.primaryContainer, 12f)
            add(ctx.text("$n", T.Label, P.onPrimaryContainer), WRAP, WRAP)
        }, ctx.dp(24), ctx.dp(24), end = 10)
        add(ctx.text(text, T.Body), 0, WRAP, 1f)
    }

    private fun keyField(id: String, hint: String): View {
        val f = field(id, hint, secret = true)
        return ctx.col {
            add(f, MATCH, WRAP)
            add(ctx.row {
                add(ctx.button(t("Вставить из буфера", "Paste from clipboard", "Buferdan qo'yish"), BtnStyle.Text) { pasteInto(f) }, WRAP, WRAP)
                add(ctx.button(t("Показать ключ", "Show key", "Kalitni ko'rsatish"), BtnStyle.Text) {
                    val hidden = f.transformationMethod is PasswordTransformationMethod
                    f.transformationMethod = if (hidden) HideReturnsTransformationMethod.getInstance() else PasswordTransformationMethod.getInstance()
                }, WRAP, WRAP)
            }, MATCH, WRAP)
        }
    }

    private fun errorLine(id: String): View? = errors[id]?.let { ctx.text(it, T.Small, P.error) }

    private fun expandable(id: String, title: String, subtitle: String, icon: Int, badge: String? = null, body: LinearLayout.() -> Unit): View {
        val isOpen = open == id
        return ctx.card(pad = 0) {
            add(ctx.row {
                setPadding(ctx.dp(14), ctx.dp(14), ctx.dp(14), ctx.dp(14))
                add(ctx.row(Gravity.CENTER) {
                    background = ctx.rounded(P.primaryContainer, 14f)
                    add(ctx.icon(icon, P.onPrimaryContainer, 22), WRAP, WRAP)
                }, ctx.dp(44), ctx.dp(44), end = 12)
                add(ctx.col {
                    add(ctx.row {
                        add(ctx.text(title, T.Title), WRAP, WRAP)
                        if (badge != null) add(ctx.pill(badge, P.success, dot = false), WRAP, WRAP, start = 8)
                    })
                    add(ctx.text(subtitle, T.Small), MATCH, WRAP, top = 2)
                }, 0, WRAP, 1f)
                add(ctx.icon(if (isOpen) R.drawable.ic_arrow_up else R.drawable.ic_arrow_down, P.onSurfaceVariant, 22), WRAP, WRAP)
                press { open = if (isOpen) null else id; refresh() }
            })
            if (isOpen) add(ctx.col(14) { body() }, MATCH, WRAP)
        }
    }

    private fun groqCard(): View {
        val done = groqDone
        return expandable("groq", "Groq", t("Бесплатно, очень быстро. Нужен свой ключ", "Free, very fast. You need your own key", "Bepul, juda tez. O'z kalitingiz kerak"), R.drawable.ic_bolt,
            badge = t("Рекомендуем", "Recommended", "Tavsiya etamiz")) {
            if (done != null) {
                add(ctx.row {
                    add(ctx.icon(R.drawable.ic_check_circle, P.success, 24), WRAP, WRAP, end = 10)
                    add(ctx.text(AiApi.connectedText(ctx, done), T.Body), 0, WRAP, 1f)
                }, MATCH, WRAP)
                add(ctx.button(t("Готово", "Done", "Tayyor")) { groqDone = null; open = null; refresh() }, MATCH, WRAP, top = 12)
                return@expandable
            }
            val steps = listOf(
                t("Нажмите «Открыть Groq» — откроется console.groq.com/keys", "Press “Open Groq” — console.groq.com/keys will open", "«Groqni ochish» tugmasini bosing — console.groq.com/keys ochiladi"),
                t("Войдите (через Google, GitHub или e-mail) — регистрация бесплатная, карта не нужна", "Sign in (with Google, GitHub or e-mail) — signing up is free, no card needed", "Kiring (Google, GitHub yoki e-mail orqali) — ro'yxatdan o'tish bepul, karta kerak emas"),
                t("Нажмите «Create API Key», придумайте любое имя (например Bubby) и нажмите «Submit»", "Press “Create API Key”, make up any name (for example Bubby) and press “Submit”", "«Create API Key» tugmasini bosing, istalgan nom o'ylab toping (masalan, Bubby) va «Submit» tugmasini bosing"),
                t("Скопируйте ключ — он начинается с gsk_ и показывается ОДИН раз", "Copy the key — it starts with gsk_ and is shown only ONCE", "Kalitni nusxalang — u gsk_ bilan boshlanadi va FAQAT BIR marta ko'rsatiladi"),
                t("Вернитесь сюда, нажмите «Вставить из буфера» и «Подключить Groq»", "Come back here, press “Paste from clipboard” and “Connect Groq”", "Shu yerga qayting, «Buferdan qo'yish» va «Groqni ulash» tugmalarini bosing"),
            )
            steps.forEachIndexed { i, s -> add(numbered(i + 1, s), MATCH, WRAP, bottom = 8) }
            add(ctx.button(t("Открыть Groq", "Open Groq", "Groqni ochish"), BtnStyle.Tonal) { Backend.openUrl(ctx, "https://console.groq.com/keys")?.let { ctx.toast(it) } }, MATCH, WRAP, top = 4)
            add(ctx.text(t("Сайт может немного отличаться — ищите кнопку Create API Key", "The site may look a little different — look for the Create API Key button", "Sayt biroz boshqacha ko'rinishi mumkin — Create API Key tugmasini qidiring"), T.Small), MATCH, WRAP, top = 6)
            add(ctx.row {
                add(ctx.icon(R.drawable.ic_warning, P.warning, 18), WRAP, WRAP, end = 6)
                add(ctx.text(t("Никому не показывайте свой ключ", "Never show your key to anyone", "Kalitingizni hech kimga ko'rsatmang"), T.Small), 0, WRAP, 1f)
            }, MATCH, WRAP, top = 10)
            add(keyField("groq", t("Ключ Groq (gsk_…)", "Groq key (gsk_…)", "Groq kaliti (gsk_…)")), MATCH, WRAP, top = 8)
            add(ctx.text(t("Бесплатно. У каждой модели свой лимит — примерно 1000 запросов в день и 8000 токенов в минуту (точные цифры в консоли Groq, они могут меняться)",
                "Free. Each model has its own limit — about 1000 requests per day and 8000 tokens per minute (exact numbers are in the Groq console and may change)",
                "Bepul. Har bir modelning o'z limiti bor — kuniga taxminan 1000 ta so'rov va daqiqasiga 8000 token (aniq raqamlar Groq konsolida, ular o'zgarishi mumkin)"), T.Small), MATCH, WRAP, top = 4)
            errorLine("groq")?.let { add(it, MATCH, WRAP, top = 8) }
            add(ctx.button(if (busy == "groq") t("Проверяю…", "Checking…", "Tekshirilmoqda…") else t("Подключить Groq", "Connect Groq", "Groqni ulash")) {
                if (busy == null) connectGroq()
            }, MATCH, WRAP, top = 12)
        }
    }

    // ---- other providers ----

    private fun compose(p: Provider): String? {
        val key = field(p.id + ":key", "").text.toString().trim()
        fun bad(s: String) = s.contains(Regex("\\s"))
        if (p.custom) {
            val url = field(p.id + ":url", "").text.toString().trim()
            val model = field(p.id + ":model", "").text.toString().trim()
            if (url.isEmpty() || model.isEmpty() || bad(url) || bad(model) || bad(key)) {
                errors[p.id] = t("Укажите адрес и модель без пробелов.", "Enter the address and the model without spaces.", "Manzil va modelni bo'sh joysiz kiriting."); return null
            }
            if (!url.startsWith("http")) {
                errors[p.id] = t("Адрес должен начинаться с http:// или https://", "The address must start with http:// or https://", "Manzil http:// yoki https:// bilan boshlanishi kerak"); return null
            }
            return if (key.isEmpty()) "$url|$model" else "$url|$model|$key"
        }
        if (key.isEmpty() || bad(key)) { errors[p.id] = t("Вставьте ключ без пробелов.", "Paste the key without spaces.", "Kalitni bo'sh joysiz qo'ying."); return null }
        return when (p.id) {
            "openrouter" -> "https://openrouter.ai/api|${p.models[picked[p.id] ?: 0]}|$key"
            else -> key
        }
    }

    private fun submit(p: Provider) {
        errors.remove(p.id)
        val spec = compose(p)
        if (spec == null) { refresh(); return }
        busy = p.id; refresh()
        Thread {
            val (ok, raw) = AiApi.test(spec, ctx)
            act.runOnUiThread {
                busy = null
                if (!ok) errors[p.id] = AiApi.hint(ctx, raw)
                else if (addSpec(spec)) for (k in listOf(":key", ":url", ":model")) fields[p.id + k]?.setText("")
                refresh()
            }
        }.start()
    }

    private fun providerCard(p: Provider): View = expandable(p.id, p.name, p.tagline, p.icon) {
        p.steps.forEachIndexed { i, s -> add(numbered(i + 1, s), MATCH, WRAP, bottom = 8) }
        add(ctx.button(t("Открыть сайт · ${p.siteLabel}", "Open the site · ${p.siteLabel}", "Saytni ochish · ${p.siteLabel}"), BtnStyle.Tonal) {
            Backend.openUrl(ctx, p.url)?.let { ctx.toast(it) }
        }, MATCH, WRAP, top = 4)
        add(ctx.text(p.note, T.Small), MATCH, WRAP, top = 8, bottom = 8)
        if (p.custom) {
            add(field(p.id + ":url", t("Адрес сервера", "Server address", "Server manzili")).apply { hint = "http://192.168.1.5:11434" }, MATCH, WRAP, top = 6)
            add(field(p.id + ":model", t("Название модели", "Model name", "Model nomi")), MATCH, WRAP, top = 8)
            add(keyField(p.id + ":key", t("Токен (необязательно)", "Token (optional)", "Token (majburiy emas)")), MATCH, WRAP, top = 8)
        } else {
            add(keyField(p.id + ":key", t("Ключ", "Key", "Kalit")), MATCH, WRAP, top = 6)
            if (p.models.isNotEmpty()) {
                add(ctx.text(t("Модель", "Model", "Model"), T.Label), MATCH, WRAP, top = 8, bottom = 6)
                val flow = FlowLayout(ctx)
                p.models.forEachIndexed { i, m -> flow.addView(ctx.chip(AiApi.shortModel(m), (picked[p.id] ?: 0) == i) { picked[p.id] = i; refresh() }) }
                add(flow, MATCH, WRAP)
            }
        }
        errorLine(p.id)?.let { add(it, MATCH, WRAP, top = 8) }
        add(ctx.button(if (busy == p.id) t("Проверяю…", "Checking…", "Tekshirilmoqda…") else t("Проверить и добавить", "Check and add", "Tekshirish va qo'shish")) {
            if (busy == null) submit(p)
        }, MATCH, WRAP, top = 12)
    }

    // ---- saved connections ----

    private fun connCard(i: Int): View {
        val info = AiApi.describe(ctx, keys[i])
        val badge = if (i == 0) t("Основной", "Primary", "Asosiy") else t("Запасной №${i + 1}", "Backup #${i + 1}", "Zaxira №${i + 1}")
        return ctx.card(pad = 14) {
            add(ctx.row {
                add(ctx.row(Gravity.CENTER) {
                    background = ctx.rounded(P.primaryContainer, 14f)
                    add(ctx.text(info.title.take(1).uppercase(), T.Title, P.onPrimaryContainer), WRAP, WRAP)
                }, ctx.dp(44), ctx.dp(44), end = 12)
                add(ctx.col {
                    add(ctx.text(info.title, T.Title), MATCH, WRAP)
                    if (info.model.isNotEmpty()) add(ctx.text(info.model, T.Small).apply { maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END }, MATCH, WRAP)
                    add(ctx.text(info.masked, T.Small), MATCH, WRAP)
                }, 0, WRAP, 1f)
                add(ctx.pill(badge, if (i == 0) P.success else P.outline), WRAP, WRAP)
            })
            add(ctx.row {
                fun round(res: Int, tint: Int, enabled: Boolean, onClick: () -> Unit) = ctx.row(Gravity.CENTER) {
                    alpha = if (enabled) 1f else 0.3f
                    add(ctx.icon(res, tint, 22), WRAP, WRAP)
                    if (enabled) press(onClick)
                }
                add(round(R.drawable.ic_arrow_up, P.onSurface, i > 0) { move(i, -1) }, ctx.dp(44), ctx.dp(44))
                add(round(R.drawable.ic_arrow_down, P.onSurface, i < keys.size - 1) { move(i, 1) }, ctx.dp(44), ctx.dp(44))
                add(View(ctx), 0, WRAP, 1f)
                add(ctx.button(if (testing == i) t("Проверяю…", "Checking…", "Tekshirilmoqda…") else t("Проверить", "Check", "Tekshirish"), BtnStyle.Tonal, R.drawable.ic_wifi) {
                    if (testing < 0) test(i)
                }, WRAP, WRAP)
                add(round(R.drawable.ic_delete, P.error, true) { delete(i) }, ctx.dp(44), ctx.dp(44))
            }, MATCH, WRAP, top = 6)
        }
    }

    override fun render(c: LinearLayout) {
        keys = Backend.aiKeys(ctx)
        c.add(ctx.card(pad = 16, fill = P.primaryContainer, stroke = P.alpha(P.primary, 90)) {
            add(ctx.text(t(
                "Bubby думает с помощью ИИ. Чтобы им пользоваться, нужен ваш личный бесплатный пропуск — «ключ». Он как логин: ваш, не для других. Можно добавить несколько: если один закончится, Bubby сам перейдёт к следующему.",
                "Bubby thinks with the help of AI. To use it you need your own free pass — a “key”. It works like a login: it is yours, not for others. You can add several: if one runs out, Bubby switches to the next one by itself.",
                "Bubby AI yordamida o'ylaydi. Undan foydalanish uchun sizga shaxsiy bepul ruxsatnoma — «kalit» (key) kerak. U login kabi: faqat sizniki, boshqalar uchun emas. Bir nechtasini qo'shish mumkin: biri tugasa, Bubby o'zi keyingisiga o'tadi."),
                T.Body, P.onPrimaryContainer), MATCH, WRAP)
            add(ctx.row {
                add(ctx.icon(R.drawable.ic_key, P.onPrimaryContainer, 16), WRAP, WRAP, end = 6)
                add(ctx.text(t("Ключ хранится только на этом телефоне и отправляется только выбранному сервису.", "The key is stored only on this phone and is sent only to the service you chose.", "Kalit faqat shu telefonda saqlanadi va faqat siz tanlagan xizmatga yuboriladi."), T.Small, P.onPrimaryContainer), 0, WRAP, 1f)
            }, MATCH, WRAP, top = 8)
        }, MATCH, WRAP)

        clip?.let {
            c.add(ctx.card(pad = 12, fill = P.secondaryContainer, stroke = 0) {
                add(ctx.row {
                    add(ctx.text(t("В буфере похоже на ключ — вставить?", "The clipboard looks like a key — paste it?", "Buferda kalitga o'xshash narsa bor — qo'yaymi?"), T.Body, P.onSecondaryContainer), 0, WRAP, 1f, end = 8)
                    add(ctx.button(t("Вставить", "Paste", "Qo'yish"), BtnStyle.Tonal) { useClip() }, WRAP, WRAP)
                })
            }, MATCH, WRAP, top = 12)
        }
        status?.let {
            c.add(ctx.card(pad = 12, fill = if (statusOk) P.secondaryContainer else P.errorContainer, stroke = 0) {
                add(ctx.row {
                    add(ctx.icon(if (statusOk) R.drawable.ic_check_circle else R.drawable.ic_error, if (statusOk) P.onSecondaryContainer else P.onErrorContainer, 22), WRAP, WRAP, end = 8)
                    add(ctx.text(it, T.Body, if (statusOk) P.onSecondaryContainer else P.onErrorContainer), 0, WRAP, 1f)
                })
            }, MATCH, WRAP, top = 12)
        }

        c.add(ctx.section(t("Ваши подключения", "Your connections", "Ulanishlaringiz"), R.drawable.ic_key))
        if (keys.isEmpty()) c.add(ctx.card(pad = 16) {
            add(ctx.text(t("Пока пусто. Начните с Groq ниже и добавьте ключ.", "Nothing here yet. Start with Groq below and add a key.", "Hozircha bo'sh. Pastdagi Groqdan boshlang va kalit qo'shing."), T.Body), MATCH, WRAP)
        }, MATCH, WRAP)
        else for (i in keys.indices) c.add(connCard(i), MATCH, WRAP, bottom = 10)

        c.add(ctx.section(t("Groq — быстрый старт", "Groq — quick start", "Groq — tez boshlash"), R.drawable.ic_bolt))
        c.add(groqCard(), MATCH, WRAP)

        c.add(ctx.row {
            setPadding(ctx.dp(4), ctx.dp(16), ctx.dp(4), ctx.dp(8))
            add(ctx.icon(R.drawable.ic_add, P.onSurfaceVariant, 22), WRAP, WRAP, end = 8)
            add(ctx.text(t("Для продвинутых", "For advanced users", "Tajribali foydalanuvchilar uchun"), T.Title), 0, WRAP, 1f)
            add(ctx.icon(if (advanced) R.drawable.ic_arrow_up else R.drawable.ic_arrow_down, P.onSurfaceVariant, 22), WRAP, WRAP)
            press { advanced = !advanced; refresh() }
        }, MATCH, WRAP)
        if (advanced) {
            c.add(ctx.text(t("Другие сервисы: OpenRouter, Gemini, Anthropic и свой сервер.", "Other services: OpenRouter, Gemini, Anthropic and your own server.", "Boshqa xizmatlar: OpenRouter, Gemini, Anthropic va o'z serveringiz."), T.Small), MATCH, WRAP, bottom = 10)
            for (p in providers()) c.add(providerCard(p), MATCH, WRAP, bottom = 10)
        }
    }
}
