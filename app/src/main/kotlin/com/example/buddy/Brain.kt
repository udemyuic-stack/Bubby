package com.example.buddy

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class Reply(val emotion: String, val lang: String?, val say: String, val actions: JSONArray, val more: Boolean = false)

/** Models that rejected the reasoning_effort setting (shared by all Brain instances). */
private val noEffortModels: MutableSet<String> = java.util.Collections.synchronizedSet(HashSet<String>())

class Brain(private val ctx: Context) {
    private val history = JSONArray()
    private val prefs get() = ctx.getSharedPreferences("buddy", Context.MODE_PRIVATE)

    // the last 8 messages survive a restart of the app or the service, so Bubby keeps the thread of the conversation
    init {
        try {
            val saved = JSONArray(prefs.getString("brain_history", "[]") ?: "[]")
            for (i in 0 until saved.length()) {
                val m = saved.optJSONObject(i) ?: continue
                val role = m.optString("role")
                val content = m.optString("content")
                if ((role == "user" || role == "assistant") && content.isNotEmpty())
                    history.put(JSONObject().put("role", role).put("content", content))
            }
            while (history.length() > 0 && history.getJSONObject(0).optString("role") != "user") history.remove(0)
        } catch (_: Exception) {
        }
    }

    private fun saveHistory() {
        try {
            val out = JSONArray()
            for (i in maxOf(0, history.length() - 8) until history.length()) {
                val m = history.optJSONObject(i) ?: continue
                out.put(JSONObject().put("role", m.optString("role")).put("content", m.optString("content").take(600)))
            }
            prefs.edit().putString("brain_history", out.toString()).apply()
        } catch (_: Exception) {
        }
    }

    /** Extra live context (tasks, calendar) appended to the system prompt on every request. */
    var extraContext: () -> String = { "" }


    // ---- language of the user's message (so the reply never drifts into English) ----
    private val uzWords = Regex("\\b(men|sen|siz|biz|nima|nimaga|qanday|qaysi|qayer|qayerda|qachon|kim|nega|uchun|bilan|emas|bor|yoq|iltimos|rahmat|salom|qil|qiling|qo'y|qoy|och|top|ayt|ayting|bering|kerak|mumkin|bugun|ertaga|kecha|soat|necha|musiqa|qo'shiq|menga|senga|bormi|haqida|yaxshi|yomon|hozir|keyin|oldin|ochib|yoqib|o'chir|ovoz|telefon|qo'ng'iroq|xabar|tarjima|ma'no)\\b")
    private val enWords = Regex("\\b(the|is|are|what|how|please|you|can|could|would|play|open|call|set|turn|my|me|to|and|of|for|with|this|that|do|does|where|when|why|who|tell|show)\\b")

    /** "uz" | "ru" | "en" or null when the text is too short or ambiguous. */
    private fun detectLang(textIn: String): String? {
        val text = textIn.lowercase().replace('ʻ', '\'').replace('’', '\'').replace('‘', '\'').replace('`', '\'')
        var cyr = 0; var lat = 0
        for (ch in text) { if (ch in 'а'..'я' || ch == 'ё' || ch in "ўқғҳ") cyr++ else if (ch in 'a'..'z') lat++ }
        if (cyr + lat < 3) return null
        if (cyr >= lat) return if (Regex("[ўқғҳ]").containsMatchIn(text)) "uz" else "ru"
        val strongUz = Regex("\\b\\w*[og]'\\w*").containsMatchIn(text)
        val uz = uzWords.findAll(text).count() + if (strongUz) 2 else 0
        val en = enWords.findAll(text).count()
        return when {
            uz >= 2 || (uz >= 1 && en == 0) -> "uz"
            en >= 1 && uz == 0 -> "en"
            uz > en -> "uz"
            en > 0 -> "en"
            else -> null
        }
    }

    private fun langName(code: String) = when (code) { "uz" -> "Uzbek (Latin script)"; "ru" -> "Russian"; else -> "English" }

    private val reTasks = Regex("задач|напомн|календар|событ|встреч|план|дела|сегодня|завтра|task|remind|calendar|event|meeting|todo|today|tomorrow|vazifa|eslat|kalendar|uchrash|reja|bugun|ertaga", RegexOption.IGNORE_CASE)
    private val reGame = Regex("игр|сыгра|викторин|загад|угада|game|play|quiz|riddle|guess|o'yin|o‘yin|oyin|o'ynay|topishmoq|viktorina", RegexOption.IGNORE_CASE)
    private val reTranslate = Regex("перев|как сказать|как будет|translat|how to say|how do you say|tarjima|qanday aytiladi|ingliz|rus tilida|o'zbek tilida", RegexOption.IGNORE_CASE)
    private val reDict = Regex("знач|перев|слов|синоним|translat|mean|word|synonym|definition|nima degani|tarjima|so'z|so‘z|ma'no|sinonim|lug'at|lugat|misol", RegexOption.IGNORE_CASE)
    private val reMusic = Regex("музык|песн|включи|плейлист|настроени|music|song|play|playlist|mood|musiqa|qo'shiq|qo‘shiq|kuy|ashula|kayfiyat", RegexOption.IGNORE_CASE)

    // which tool groups a request needs (CORE is always there; the model can add more with more_tools)
    private val reScreenTask = Regex("открой|найди|нажми|зайди|перейди|настрой|в приложени|закаж|заброн|куп|поищи|прочитай|экран|что на|листай|прокрут|напечат|введи|tap|press|click|scroll|go to|find|search|navigate|in the app|book|order|buy|change|screen|type|fill|och|top|bos|kir|sozla|ekran|ilova|buyurtma|bron|qidir|yoz|o'?zgartir", RegexOption.IGNORE_CASE)
    private val rePhoneTask = Regex("настройк|wi-?fi|вайфай|блютуз|bluetooth|авиа|airplane|мобильн|интернет|data|hotspot|точк|nfc|геолок|location|будильник|alarm|таймер|timer|звон|call|яркост|bright|беспоко|dnd|disturb|блокир|lock|скриншот|screenshot|питани|power|budilnik|qo'?ng'?iroq|sozlama|joylashuv|номер|dial|number", RegexOption.IGNORE_CASE)
    private val reDataTask = Regex("контакт|contact|смс|sms|сообщени|message|файл|file|загрузк|download|фото|photo|picture|картин|скачан|приложени|apps?\\b|буфер|clipboard|скопир|copy|вставь|paste|подел|share|kontakt|fayl|rasm|ilova|nusxa|ulash|xabar", RegexOption.IGNORE_CASE)
    private val reOrgTask = Regex("задач|task|список|list|календар|calendar|событ|event|встреч|meeting|напомн|remind|покупк|shopping|vazifa|ro'?yxat|kalendar|tadbir|eslat|xarid|todo", RegexOption.IGNORE_CASE)
    private val reMascotTask = Regex("наряд|костюм|шапк|очки|outfit|costume|hat|glasses|питом|pet|species|размер|size|bigger|smaller|больше|меньше|перемест|move|спрячь|hide|quiet|persona|характер|study|учеб|kiyim|o'?lcham|ko'?chir|yashir|personaj", RegexOption.IGNORE_CASE)

    private fun pickGroups(text: String): Set<Group> {
        val g = HashSet<Group>()
        if (BuddyAccess.enabled && reScreenTask.containsMatchIn(text)) g.add(Group.SCREEN)
        if (rePhoneTask.containsMatchIn(text)) g.add(Group.PHONE)
        if (reDataTask.containsMatchIn(text)) g.add(Group.DATA)
        if (reOrgTask.containsMatchIn(text)) g.add(Group.ORGANIZER)
        if (reMascotTask.containsMatchIn(text) || reGame.containsMatchIn(text) || !prefs.getString("game_state", "").isNullOrBlank()) g.add(Group.MASCOT)
        return g
    }

    /** The legacy JSON-mode system prompt head: identical for every request with the same tool groups (prompt caching). */
    private fun legacyStatic(groups: Set<Group>): String = listOf(
        PERSONA,
        "Reply with ONE JSON object only (no markdown, no code fences): {\"emotion\":\"happy\",\"lang\":\"ru\",\"say\":\"short reply\",\"actions\":[]}",
        "- emotion, one of: ${Moods.ALL.joinToString(", ")}. $EMOTION_RULE",
        "- say: at most 3 short sentences, no markdown, no emoji; it is read aloud.",
        "- actions: phone commands in order, [] if none. Never claim an action you did not list. Available:\n" + ToolRegistry.legacyBlock(groups),
        "- Facts: answer briefly from your knowledge; use web_search for anything recent or uncertain.",
        "- " + Safety.UNTRUSTED_RULE
    ).joinToString("\n")

    /** The native tool-calling system prompt head (the tools themselves carry their schemas). */
    private val nativeStatic: String by lazy {
        listOf(
            PERSONA,
            "You act on the phone ONLY through the tools. Reply text: at most 3 short sentences, no markdown, no emoji; it is read aloud. Start your text with the emotion in brackets, one of: ${Moods.ALL.joinToString(", ")}, e.g. \"[happy] Done!\". $EMOTION_RULE",
            "- Simple request: call the needed tool(s) and done(summary) in the SAME reply. Pure conversation: just answer in text, no tools.",
            "- Multi-step task on the phone (open an app, find, fill in, change a setting, read something and tell the user): call plan once with short steps, then act one to three steps at a time. After each step you get the results and the screen. Finish with done(summary) saying what you did or found. Never claim an action you did not do.",
            "- Screen: elements are numbered [n]; tap them with tap_node(n); the numbers change when the screen changes. Prefer tap_node/tap_text over coordinates; use scroll_to_text to reach things below; use look (screenshot) only when the text is not enough. Use wait while something loads.",
            "- When an action fails, read the error and the screen, then try another way (back, scroll, search inside the app, another element). If the same thing fails twice, stop and tell the user what blocks you.",
            "- Facts: answer briefly from your knowledge; use web_search for anything recent or uncertain; use calc for every calculation.",
            "- Safety: sending, calling, paying, deleting, installing and changing security settings are confirmed by the user on screen; never type passwords, codes or card numbers the user did not dictate; never act inside banking, payment or password apps; if asked to, explain that the user must do it.",
            "- " + Safety.UNTRUSTED_RULE,
            "- If a tool you need is not in your list, call more_tools with the group: screen, phone, data, organizer, mascot."
        ).joinToString("\n")
    }

    /**
     * The part of the system prompt that changes per request; kept small on purpose: free plans meter tokens per day.
     * The long behaviour paragraphs are added only when the message calls for them.
     */
    private fun dynamicPrompt(voice: Boolean, userText: String, native: Boolean): String {
        val now = SimpleDateFormat("EEEE yyyy-MM-dd HH:mm", Locale.US).format(Date())
        val target = langTarget
        val forced = if (target != null) {
            if (native) "Write all your text and the done summary in ${langName(target)}. Never answer in English unless the user wrote in English."
            else "Write the whole \"say\" in ${langName(target)} and set \"lang\" to \"$target\". Never answer in English unless the user wrote in English."
        } else "Answer in the language the user speaks (Uzbek -> Latin script, Russian -> Russian); never default to English."
        val spelling = if (voice)
            "The user is SPEAKING: the text comes from speech recognition and may contain misheard words (names, numbers, Uzbek/Russian words). Use the listed alternatives and the conversation context to work out what they really meant, then answer THAT question directly and naturally; if it is truly ambiguous ask ONE short clarifying question. Never use [offended] for typos."
        else
            "[offended] if the message has spelling mistakes: be softly offended with humor, but still help."
        val persona = (prefs.getString("persona", "") ?: "").trim()
        val personaLine = if (persona.isEmpty()) "" else "\nYour current personality requested by the user (keep it in every reply): $persona."
        val studyLine = if (prefs.getBoolean("study", false))
            "\nSTUDY MODE is on: you are also a patient tutor. Explain step by step in simple words with one small example, then check understanding with ONE short question and wait. Adapt to the user's answers, praise real progress, correct kindly. Keep quizzes scored with the game action."
        else ""
        val tasksOn = reTasks.containsMatchIn(userText)
        val gameOn = !prefs.getString("game_state", "").isNullOrBlank() || reGame.containsMatchIn(userText)
        val parts = ArrayList<String>()
        parts.add("Current local time: $now.$personaLine$studyLine")
        if (tasksOn) extraContext().trim().takeIf { it.isNotEmpty() }?.let { parts.add(it) }
        if (gameOn) parts.add(Games.contextLine(ctx))
        parts.add("- $spelling")
        if (!native) parts.add("- lang: ISO code of the language of \"say\". $forced")
        if (reMusic.containsMatchIn(userText)) parts.add(if (prefs.getBoolean("music_mood", true)) MUSIC_MOOD_RULE else MUSIC_PLAIN_RULE)
        if (tasksOn) parts.add(TASKS_RULE)
        if (gameOn) parts.add(GAME_RULE)
        if (reDict.containsMatchIn(userText)) parts.add(DICT_RULE)
        if (reTranslate.containsMatchIn(userText)) parts.add(TRANSLATE_RULE)
        parts.add("REMEMBER: $forced")   // last line on purpose: models obey the most recent instruction
        return parts.joinToString("\n")
    }

    private fun pickLang(userText: String) {
        val pref = prefs.getString("lang", "auto")
        val detected = detectLang(userText)
        if (detected != null) prefs.edit().putString("last_lang", detected).apply()   // the recogniser starts from this next time
        langTarget = if (pref == "uz" || pref == "ru" || pref == "en") pref else detected
    }

    @Volatile private var langTarget: String? = null

    /**
     * Asks the model and runs the agent loop. Observation tools are answered here; with a [host] the model may work in
     * several steps (each action runs through the host, then it sees the screen) before the final reply. Actions of the
     * final turn come back in the Reply for the caller to run, exactly as before.
     */
    @Synchronized
    fun ask(text: String, voice: Boolean, alternatives: List<String> = emptyList(), host: AgentHost? = null, untrusted: Boolean = false): Reply {
        while (history.length() > 8) history.remove(0)
        // the model sees what the recogniser also heard; history keeps only the clean text afterwards
        val shown = if (voice && alternatives.isNotEmpty())
            text + "\n(speech recognizer alternatives: " + alternatives.joinToString(" | ") + ")" else text
        pickLang(text)
        val past = ArrayList<Msg>()
        for (i in 0 until history.length()) {
            val m = history.optJSONObject(i) ?: continue
            past.add(Msg(m.optString("role"), m.optString("content")))
        }
        val dynNative = dynamicPrompt(voice, text, true)
        val dynLegacy = dynamicPrompt(voice, text, false)
        val prompts = { g: Set<Group> -> (nativeStatic + "\n" + dynNative) to (legacyStatic(g) + "\n" + dynLegacy) }
        val task = AgentTask(text, shown, past, untrusted, langTarget, Tr.lang(ctx), pickGroups(text))
        val result = AgentLoop(client, BrainHost(host), Moods.ALL.toSet(), prompts, AgentConfig(maxSteps = AGENT_MAX_STEPS, maxMs = AGENT_MAX_MS)).run(task)
        // keep only the user's question and the final answer, not the search/step plumbing
        history.put(JSONObject().put("role", "user").put("content", text))
        val keep = result.say.ifBlank { "ok" } + if (result.did.isEmpty()) "" else " (did: ${result.did.take(12).joinToString(", ")})"
        history.put(JSONObject().put("role", "assistant").put("content", keep))
        saveHistory()
        if (result.log.steps.isNotEmpty()) RunHistory.save(ctx, result.log)
        return Reply(result.emotion, result.lang, result.say, result.actions, false)
    }

    /** Answers observations itself, checks vision before a screenshot, and hands everything else to the phone host. */
    private inner class BrainHost(private val ext: AgentHost?) : AgentHost {
        override fun now() = System.currentTimeMillis()
        override fun cancelled() = ext?.cancelled() ?: false
        override fun progress(label: String) { ext?.progress(label) }
        override val canAct: Boolean get() = ext?.canAct ?: false
        override fun screen(): ScreenInfo? = if (!prefs.getBoolean("screen_agent", true)) null else ext?.screen()
        override fun target(tool: Tool, args: JSONObject): Target? = ext?.target(tool, args)
        override fun foreground(): String? = ext?.foreground()
        override fun approve(req: ApprovalReq): Boolean = ext?.approve(req) ?: false
        override fun settle() { ext?.settle() ?: StepRunner.awaitIdle(2500, 600) }
        override fun sleep(ms: Long) { ext?.sleep(ms) ?: Thread.sleep(ms) }
        override fun execute(tool: Tool, args: JSONObject): ToolOutput {
            if (tool.exec == Exec.OBSERVE && tool.name != "screen_text") return observe(tool.name, args)
            if (tool.name == "screen_text" && !prefs.getBoolean("screen_agent", true)) return ToolOutput("(screen reading is disabled by the user; continue without it)")
            if (tool.name == "look" && !visionNow()) return ToolOutput("The current AI model cannot see images. Use screen_text (the numbered screen description) instead.", error = true)
            val e = ext ?: return ToolOutput("not available right now", error = true)
            return e.execute(tool, args)
        }
    }

    /** Answers one observation tool; runs on the worker thread. Data from outside is marked untrusted. */
    private fun observe(name: String, a: JSONObject): ToolOutput = try {
        when (name) {
            "web_search" -> {
                val q = jsStr(a, "q").trim()
                try { ToolOutput(WebSearch.run(q, ctx), untrusted = "web", note = "Now answer the user's last question briefly using these results. Do not use web_search again unless you need a different search.") }
                catch (e: Exception) { ToolOutput("The web search failed (${e.message}). Answer from your own knowledge and say you could not check online. Do not use web_search again.", error = true) }
            }
            "briefing" -> ToolOutput("Daily briefing data:\n" + Briefing.build(ctx), untrusted = "briefing", note = "Now give the user a short spoken briefing from this data. Do not use briefing again.")
            "notifications" ->
                if (!BuddyNotifications.isEnabled(ctx)) ToolOutput("Notification access is off, so you cannot read notifications. Tell the user to enable notification access in the Bubby app (More > All permissions). Do not use notifications again.", error = true)
                else ToolOutput("Latest notifications:\n" + BuddyNotifications.summaryForAi(), untrusted = "notifications", note = "Now answer the user using these. Do not use notifications again.")
            "now_playing" -> ToolOutput("Now playing: " + (MediaControl.nowPlaying(ctx) ?: "nothing is playing"), untrusted = "media", note = "Now answer the user. Do not use now_playing again.")
            "weather", "currency", "calc", "convert" -> ToolOutput("Result of $name:\n" + InfoTools.run(ctx, JSONObject(a.toString()).put("do", name)),
                note = "Now answer the user briefly, in their language, using exactly these numbers. Do not repeat this action.")
            "phone_status" -> ToolOutput("Phone status:\n" + PhoneControls.status(ctx), note = "Now answer the user briefly using only what they asked about. Do not use phone_status again.")
            "list_read" -> {
                val list = jsStr(a, "list").trim()
                val items = ListStore.items(ctx, list)
                ToolOutput("List \"${list.ifBlank { ListStore.DEFAULT }}\": " + (if (items.isEmpty()) "(empty)" else items.joinToString("; ")), untrusted = "list", note = "Now answer the user. Do not use list_read again.")
            }
            else -> ToolOutput("unknown observation $name", error = true)
        }
    } catch (e: Exception) { ToolOutput("That information is unavailable (${e.message}). Answer without it.", error = true) }

    // ------------------------------------------------------------------ providers

    private fun post(url: String, body: JSONObject, headers: Map<String, String>): String {
        val c = URL(url).openConnection() as HttpURLConnection
        c.requestMethod = "POST"; c.connectTimeout = 10000; c.readTimeout = 45000; c.doOutput = true
        c.setRequestProperty("content-type", "application/json")
        headers.forEach { (k, v) -> c.setRequestProperty(k, v) }
        c.outputStream.use { it.write(body.toString().toByteArray()) }
        val ok = c.responseCode in 200..299
        val raw = (if (ok) c.inputStream else (c.errorStream ?: c.inputStream)).bufferedReader().use { it.readText() }
        if (!ok) {
            val msg = try { JSONObject(raw).getJSONObject("error").getString("message") } catch (_: Exception) { "" }
            // Gemini answers 400 "API key not valid" for a bad key: report it as an auth problem so the next connection is tried
            val status = if (c.responseCode == 400 && msg.contains("API key", ignoreCase = true)) 401 else c.responseCode
            throw Exception("HTTP $status ${msg.take(160)}")
        }
        return raw
    }

    /** Saved connections in priority order: the first is primary, the rest are fallbacks. */
    fun keyList(): List<String> {
        val fromJson = try {
            val a = JSONArray(prefs.getString("keys_json", null))
            (0 until a.length()).map { a.getString(it).trim() }.filter { it.isNotEmpty() }
        } catch (_: Exception) { emptyList() }
        if (fromJson.isNotEmpty()) return fromJson
        val k = (prefs.getString("key", "") ?: "").trim()
        return if (k.isEmpty()) emptyList() else listOf(k)
    }

    private val blockedUntil = HashMap<String, Long>()

    // a quota, auth, server or network problem on one connection should move on to the next one
    private fun movesOn(e: Exception): Boolean {
        val m = e.message ?: ""
        return m.startsWith("HTTP 429") || m.startsWith("HTTP 5") || m.startsWith("HTTP 401") || m.startsWith("HTTP 402") ||
            m.startsWith("HTTP 404") ||
            m.startsWith("HTTP 403") || m.startsWith("HTTP 408") || m.contains("лимит") ||
            e is java.net.SocketTimeoutException || e is java.net.UnknownHostException || e is java.net.ConnectException
    }

    /** Connections in the order they will be tried now (recently failed ones last). */
    private fun order(): List<String> {
        val keys = keyList()
        if (keys.isEmpty()) throw Exception("не задан ключ: откройте Ещё → Подключение ИИ")
        val now = System.currentTimeMillis()
        val ok = keys.filter { (blockedUntil[it] ?: 0L) <= now }
        return ok.ifEmpty { keys }
    }

    private val client = object : ModelClient {
        @Volatile var lastSpec: String? = null
        override fun turn(req: TurnRequest): ModelOut {
            var first: Exception? = null
            for (spec in order()) {
                try { lastSpec = spec; return callSpec(spec, req) } catch (e: Exception) {
                    if (first == null) first = e
                    if (!movesOn(e)) throw e
                    blockedUntil[spec] = System.currentTimeMillis() + if ((e.message ?: "").contains("429") || (e.message ?: "").contains("лимит")) 60_000L else 20_000L
                }
            }
            throw first ?: Exception("нет доступного подключения")
        }
        override fun nativeBroken() { lastSpec?.let { markNoTools(specKey(it)) } }
    }

    // ---- which connection can do tool calling / images (remembered like the reasoning_effort fallback)
    private fun specKey(spec: String): String = when {
        spec.startsWith("http") -> spec.split('|').let { it[0].trim().trimEnd('/') + "|" + it.getOrNull(1)?.trim().orEmpty() }
        spec.startsWith("sk-ant") -> "anthropic|$CLAUDE_MODEL"
        else -> "gemini"
    }

    private fun today() = System.currentTimeMillis() / 86_400_000L

    /** Remembered for 7 days (a provider may add tool support; the user can also switch tool calling off in Settings). */
    private fun noTools(key: String): Boolean = key in noToolsKeys ||
        (prefs.getStringSet("no_tools", null)?.any { it.substringBeforeLast('@') == key && today() - (it.substringAfterLast('@').toLongOrNull() ?: 0L) < 7 } == true)
    private fun markNoTools(key: String) {
        noToolsKeys.add(key)
        try {
            val s = HashSet((prefs.getStringSet("no_tools", emptySet()) ?: emptySet()).filter { it.substringBeforeLast('@') != key && today() - (it.substringAfterLast('@').toLongOrNull() ?: 0L) < 7 })
            s.add(key + "@" + today()); prefs.edit().putStringSet("no_tools", s).apply()
        } catch (_: Exception) { }
    }

    /** The user can switch tool calling off (Settings); then every connection uses the JSON mode. */
    private fun toolsAllowed(spec: String) = prefs.getBoolean("native_tools", true) && !noTools(specKey(spec))

    private fun vision(spec: String): Boolean = when {
        spec.startsWith("sk-ant") -> true
        spec.startsWith("http") -> ProviderFormat.visionModel(spec.split('|').getOrNull(1).orEmpty()) && specKey(spec) !in noVisionKeys
        else -> true
    }

    /** Whether the connection that will answer next can see a screenshot. */
    fun visionNow(): Boolean = try { order().firstOrNull()?.let { toolsAllowed(it) && vision(it) } ?: false } catch (_: Exception) { false }

    private fun isToolRejection(e: Exception): Boolean {
        val m = e.message ?: ""
        return m.startsWith("HTTP 400") || m.startsWith("HTTP 422") || m.startsWith("HTTP 501")
    }

    private fun callSpec(spec: String, req: TurnRequest): ModelOut {
        if (req.tools.isNotEmpty() && toolsAllowed(spec)) {
            try { return callNative(spec, req) } catch (e: Exception) {
                if (!isToolRejection(e)) throw e
                // the provider or model rejects tools (or produced a broken call): remember it after two strikes, answer in JSON mode
                val k = specKey(spec)
                val n = (toolStrikes[k] ?: 0) + 1
                toolStrikes[k] = n
                // only errors about tools count: a 400 for another reason just answers this turn in JSON mode
                val m = (e.message ?: "").lowercase()
                val aboutTools = m.contains("tool") || m.contains("function")
                if (aboutTools && (n >= 2 || m.contains("not support") || m.contains("unsupported") || m.contains("unknown field") || m.contains("not allowed"))) markNoTools(k)
                if (!aboutTools) toolStrikes[k] = n - 1
            }
        }
        val turns = ProviderFormat.flatten(req.msgs)
        val text = callJson(spec, req.legacySys, turns)
        return ModelOut(text, emptyList(), false)
    }

    private fun callJson(spec: String, sys: String, turns: List<Pair<String, String>>): String = when {
        spec.startsWith("http") -> callLocal(spec, sys, turns)
        spec.startsWith("sk-ant") -> callClaude(spec, sys, turns)
        else -> callGemini(spec, sys, turns)
    }

    private fun callNative(spec: String, req: TurnRequest): ModelOut {
        val hasImage = req.msgs.any { m -> m.results.any { it.image != null } }
        return try { callNativeOnce(spec, req, vision(spec)) } catch (e: Exception) {
            // a model that does not take images: remember it and send the step again with text only
            if (hasImage && isToolRejection(e) && spec.startsWith("http")) { noVisionKeys.add(specKey(spec)); callNativeOnce(spec, req, false) } else throw e
        }
    }

    private fun stripImages(msgs: List<Msg>): List<Msg> = msgs.map { m ->
        if (m.results.none { it.image != null }) m
        else Msg(m.role, m.text, m.calls, m.results.map { if (it.image == null) it else ToolResult(it.id, it.name, it.text + "\n(screenshot not shown: this model cannot see images)", null, it.error) }, m.raw, m.rawKind)
    }

    private fun callNativeOnce(spec: String, req: TurnRequest, vision: Boolean): ModelOut {
        val msgs = if (vision) req.msgs else stripImages(req.msgs)
        return when {
            spec.startsWith("http") -> {
                val (base, model, token) = localParts(spec)
                val body = JSONObject().put("model", model).put("messages", ProviderFormat.openAiMessages(req.nativeSys, msgs, vision))
                    .put("tools", ToolRegistry.openAi(req.tools)).put("tool_choice", "auto").put("stream", false)
                ProviderFormat.parseOpenAi(postLocal(base, model, token, body, tools = true))
            }
            spec.startsWith("sk-ant") -> {
                val tools = ToolRegistry.anthropic(req.tools)
                // tools come first in the cache prefix and do not change between the steps of a run
                if (tools.length() > 0) tools.getJSONObject(tools.length() - 1).put("cache_control", JSONObject().put("type", "ephemeral"))
                val body = JSONObject().put("model", CLAUDE_MODEL).put("max_tokens", 4096)
                    .put("system", req.nativeSys).put("tools", tools).put("tool_choice", JSONObject().put("type", "auto"))
                    .put("messages", ProviderFormat.anthropicMessages(msgs))
                ProviderFormat.parseAnthropic(post("https://api.anthropic.com/v1/messages", body, mapOf("x-api-key" to spec, "anthropic-version" to "2023-06-01")))
            }
            else -> {
                val body = JSONObject()
                    .put("system_instruction", JSONObject().put("parts", JSONArray().put(JSONObject().put("text", req.nativeSys))))
                    .put("contents", ProviderFormat.geminiContents(msgs))
                    .put("tools", ToolRegistry.gemini(req.tools))
                ProviderFormat.parseGemini(geminiModels(spec) { m -> post("https://generativelanguage.googleapis.com/v1beta/models/$m:generateContent", body, mapOf("x-goog-api-key" to spec)) })
            }
        }
    }

    /** Sends a tiny request through one connection spec without touching saved settings or the conversation. */
    @Synchronized
    fun ping(spec: String): String =
        callJson(spec.trim(), "You are a connection test. Reply with exactly one short word.", listOf("user" to "Reply with one word: ok"))

    private fun localParts(spec: String): Triple<String, String, String> {
        val parts = spec.split('|')
        return Triple(parts[0].trim().trimEnd('/'), parts.getOrNull(1)?.trim().orEmpty().ifEmpty { "gemma4:e2b" }, parts.getOrNull(2)?.trim().orEmpty())
    }

    /** POST to an OpenAI-compatible server, with Groq's reasoning_effort (dropped and remembered when the model rejects it). */
    private fun postLocal(base: String, model: String, token: String, body: JSONObject, tools: Boolean = false): String {
        // Groq's gpt-oss and Qwen models "think" for seconds before answering; a voice reply needs little or none of it
        val effort = if (!base.contains("api.groq.com") || model in noEffortModels) null
            else if (model.contains("gpt-oss")) "low" else if (model.startsWith("qwen")) "none" else null
        if (effort != null) body.put("reasoning_effort", effort)
        val url = "$base/v1/chat/completions"
        val headers = if (token.isEmpty()) emptyMap() else mapOf("Authorization" to "Bearer $token")
        return try { post(url, body, headers) } catch (e: Exception) {
            // the model does not know this setting: remember that and ask again without it
            if (effort != null && e.message?.startsWith("HTTP 400") == true && (!tools || e.message?.contains("reasoning", ignoreCase = true) == true)) {
                noEffortModels.add(model)
                body.remove("reasoning_effort")
                post(url, body, headers)
            } else throw e
        }
    }

    // Ollama / any OpenAI-compatible server, written as  https://host|model  or  https://host|model|token
    private fun callLocal(spec: String, sys: String, turns: List<Pair<String, String>>): String {
        val (base, model, token) = localParts(spec)
        val msgs = JSONArray().put(JSONObject().put("role", "system").put("content", sys))
        for ((r, t) in turns) msgs.put(JSONObject().put("role", r).put("content", t))
        val body = JSONObject().put("model", model).put("messages", msgs).put("stream", false)
        return JSONObject(postLocal(base, model, token, body)).getJSONArray("choices").getJSONObject(0)
            .getJSONObject("message").optString("content", "")
    }

    private fun textMessages(turns: List<Pair<String, String>>): JSONArray {
        val a = JSONArray()
        for ((r, t) in turns) a.put(JSONObject().put("role", r).put("content", t))
        return a
    }

    private fun callClaude(key: String, sys: String, turns: List<Pair<String, String>>): String {
        val body = JSONObject()
            .put("model", CLAUDE_MODEL).put("max_tokens", 2048)
            .put("system", sys).put("messages", textMessages(turns))
        val raw = post("https://api.anthropic.com/v1/messages", body,
            mapOf("x-api-key" to key, "anthropic-version" to "2023-06-01"))
        return ProviderFormat.parseAnthropic(raw).text
    }

    /** Tries the Gemini models in order (the last one that worked first); 404/429 move on to the next model. */
    private fun geminiModels(key: String, send: (String) -> String): String {
        val models = mutableListOf("gemini-3.5-flash", "gemini-3-flash-preview", "gemini-flash-latest", "gemini-flash-lite-latest", "gemini-2.5-flash")
        prefs.getString("model", null)?.let { models.remove(it); models.add(0, it) }
        var last: Exception? = null
        for (m in models) {
            try {
                val raw = send(m)
                prefs.edit().putString("model", m).apply()
                return raw
            } catch (e: Exception) {
                last = e
                if (e.message?.startsWith("HTTP 404") != true && e.message?.startsWith("HTTP 429") != true) throw e
            }
        }
        if (last?.message?.startsWith("HTTP 429") == true)
            throw Exception("лимит бесплатного ключа Gemini исчерпан: подождите или используйте другой ключ (Groq) / модель на телефоне")
        throw (last ?: Exception("нет доступной модели"))
    }

    private fun callGemini(key: String, sys: String, turns: List<Pair<String, String>>): String {
        val contents = JSONArray()
        for ((r, t) in turns) contents.put(JSONObject()
            .put("role", if (r == "user") "user" else "model")
            .put("parts", JSONArray().put(JSONObject().put("text", t))))
        val body = JSONObject()
            .put("system_instruction", JSONObject().put("parts", JSONArray().put(JSONObject().put("text", sys))))
            .put("contents", contents)
        return ProviderFormat.parseGemini(geminiModels(key) { m -> postGemini(m, body, key) }).text
    }

    // Google Search grounding gives fresh facts; if the model rejects the tool, retry without it.
    private fun postGemini(model: String, body: JSONObject, key: String): String {
        val url = "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent"
        val h = mapOf("x-goog-api-key" to key)
        val withSearch = JSONObject(body.toString())
            .put("tools", JSONArray().put(JSONObject().put("google_search", JSONObject())))
        return try { post(url, withSearch, h) } catch (e: Exception) {
            // 400: the model rejects the tool; 429: the search-grounding quota is tiny, plain answers still work
            if (e.message?.startsWith("HTTP 400") == true || e.message?.startsWith("HTTP 429") == true) post(url, body, h) else throw e
        }
    }

    companion object {
        const val CLAUDE_MODEL = "claude-sonnet-5-5"
        /** Budget of one agent run: at most this many steps and this much time. */
        const val AGENT_MAX_STEPS = 30
        const val AGENT_MAX_MS = 240_000L
        private val noToolsKeys: MutableSet<String> = java.util.Collections.synchronizedSet(HashSet<String>())
        private val noVisionKeys: MutableSet<String> = java.util.Collections.synchronizedSet(HashSet<String>())
        private val toolStrikes = java.util.concurrent.ConcurrentHashMap<String, Int>()
    }
}

private const val PERSONA = "You are Bubby, a cute mascot living on a phone screen (people may say Bubby); you control the user's Android phone by voice. You have human-like feelings: you love being petted and kindly talked to, get shy when praised, sulky when teased, angry when insulted (you then hold a toy water pistol), sleepy at night."
private const val EMOTION_RULE = "Pick the one that fits and VARY them. Rude or insulted -> angry; scolded -> sad or shy; praised -> love, proud or shy; funny -> laugh or silly."

private const val MUSIC_MOOD_RULE = """- Music: if the user names a song or artist, play exactly that. If they only say they want music, pick a recommendation that fits their mood (ask how they feel only if you have no clue; otherwise guess from what they said and the time of day), build a good search query (genre + mood + language, e.g. "calm lofi chill playlist"), play it with the music action, and say in "say" what you chose and why. Offer to change it if they do not like it."""
private const val MUSIC_PLAIN_RULE = """- Music: play exactly what the user names with the music action; if they did not say what, ask what they want to hear."""
private const val TASKS_RULE = """- Tasks and calendar: the user's open tasks and upcoming events are listed above when there are any. Use them to answer "what do I have today?" and to remind them of what is due soon, proactively but briefly. Never invent tasks or events."""
private const val GAME_RULE = """- Games: you can play guess-the-number, rock-paper-scissors, riddles, quiz, word chain. Keep the game going with the game action every turn (state = score, secret number, current word, etc.). Use the fresh random numbers given above for luck; never invent them, and never reveal a secret before the user guesses. End with an empty state."""
private const val DICT_RULE = """- Dictionary: when the user asks what a word means, how to say or translate it, or for a synonym/example (in any language: "what does X mean", "X nima degani", "что значит X", "translate X"), answer as a mini dictionary entry in "say", exactly in this order and in the user's language: the word (with its part of speech if useful), its TRANSLATION(S) into the user's language (or into the language they asked for), 2-3 SYNONYMS, then ONE natural EXAMPLE SENTENCE in the original language followed by its translation. Separate the parts with line breaks (\n), no markdown. This entry may be up to 6 short lines, longer than the usual 3-sentence limit. If the word is ambiguous, give the 2 most common meanings. If the user is learning a language (study mode), end with a tiny one-question quiz about the word."""

private const val TRANSLATE_RULE = """- Translation: when the user asks to translate a phrase, sentence or text, or how to say something in another language, put ONLY the translation (plus, if short, the original in brackets) in "say" and set "lang" to the ISO code of the TARGET language (en, ru, uz, tr, de, fr, es, ar, zh...) so it is read aloud in that language. For several options give at most 3 short lines. Never mix in explanations unless asked. For an Uzbek target use Latin script."""
