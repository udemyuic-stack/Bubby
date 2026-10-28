package com.example.buddy

import org.json.JSONArray
import org.json.JSONObject

// Pure Kotlin (org.json only): the budgeted agent loop. Android plugs in through ModelClient and AgentHost,
// the JVM tests through a scripted fake model and a fake device.

/** Limits of one run. */
class AgentConfig(
    val maxSteps: Int = 30,
    val maxMs: Long = 240_000L,
    /** Same action on an unchanged screen this many times: the model is told to change approach. */
    val loopWarn: Int = 3,
    /** ...and this many times: the run stops and the user is told. */
    val loopStop: Int = 5,
    val maxObservations: Int = 12,
    val maxLooks: Int = 6,
    val maxMalformed: Int = 2,
    val maxWaitS: Int = 10,
)

class TurnRequest(val nativeSys: String, val legacySys: String, val tools: List<Tool>, val msgs: List<Msg>)

/** The AI provider(s). Brain implements it with HTTP and the connection fallback; tests with a script. */
interface ModelClient {
    /** One model turn: native tool calling when the connection supports it, else the legacy JSON mode (ModelOut.native = false). */
    fun turn(req: TurnRequest): ModelOut
    /** Tool calls of the current connection keep coming back malformed: use JSON mode with it from now on. */
    fun nativeBroken()
}

/** What a tool returned. [untrusted] = source tag when the text is data from outside; [note] = trusted instruction after it. */
class ToolOutput(val text: String, val error: Boolean = false, val image: String? = null, val untrusted: String? = null, val note: String? = null)

/** [sig] = signature of what is on screen; null when the screen is hidden (privacy, no consent). */
class ScreenInfo(val text: String, val sig: Int?, val pkg: String)

class ApprovalReq(val title: String, val detail: String, val lock: Boolean, val tool: String)

/** The phone side: runs tools, describes the screen, asks the user. Called on the worker thread. */
interface AgentHost {
    fun now(): Long
    fun cancelled(): Boolean
    fun progress(label: String)
    /** Runs a tool the loop does not handle itself (observations, device tools, actions). */
    fun execute(tool: Tool, args: JSONObject): ToolOutput
    /** False: nothing can run during the loop (no service); actions only come back in the final reply. */
    val canAct: Boolean
    /** The screen for the feedback after a step; null when it cannot be read. */
    fun screen(): ScreenInfo?
    /** The element a screen action would touch (label, app, password field). */
    fun target(tool: Tool, args: JSONObject): Target?
    fun foreground(): String?
    /** Shows the approval card (and the lock-screen check when asked); blocks until the user answers. */
    fun approve(req: ApprovalReq): Boolean
    /** Waits until the screen settles after actions. */
    fun settle()
    fun sleep(ms: Long)
}

/** One request of the user. [text] = the user's own words (for the safety gate), [shown] = what the model sees. */
class AgentTask(val text: String, val shown: String = text, val history: List<Msg> = emptyList(), val untrusted: Boolean = false,
                val lang: String? = null, val uiLang: String? = null, val groups: Set<Group> = emptySet())

enum class Outcome { DONE, CANCELLED, BUDGET_STEPS, BUDGET_TIME, STUCK }

class AgentResult(val emotion: String, val lang: String?, val say: String, val actions: JSONArray, val outcome: Outcome, val log: RunLog, val did: List<String>)

// ------------------------------------------------------------------ run log
class RunStep(val n: Int, val tool: String, val detail: String, val result: String, val ok: Boolean)

/** What Bubby did in one run, for the user (Home > Last run). Never holds screen contents or keys. */
class RunLog(val task: String, val start: Long) {
    val steps = ArrayList<RunStep>()
    var outcome: String = "running"
    var summary: String = ""
    var end: Long = start
    var mode: String = ""

    fun add(n: Int, tool: String, detail: String, result: String, ok: Boolean) {
        if (steps.size < 60) steps.add(RunStep(n, tool, detail.take(80), result.take(120), ok))
    }

    fun toJson(): JSONObject {
        val a = JSONArray()
        for (s in steps) a.put(JSONObject().put("n", s.n).put("tool", s.tool).put("detail", s.detail).put("result", s.result).put("ok", s.ok))
        return JSONObject().put("task", task.take(120)).put("start", start).put("end", end).put("outcome", outcome)
            .put("summary", summary.take(300)).put("mode", mode).put("steps", a)
    }

    companion object {
        fun fromJson(o: JSONObject): RunLog {
            val r = RunLog(o.optString("task"), o.optLong("start"))
            r.end = o.optLong("end", r.start); r.outcome = o.optString("outcome"); r.summary = o.optString("summary"); r.mode = o.optString("mode")
            val a = o.optJSONArray("steps") ?: JSONArray()
            for (i in 0 until a.length()) { val s = a.optJSONObject(i) ?: continue
                r.steps.add(RunStep(s.optInt("n"), s.optString("tool"), s.optString("detail"), s.optString("result"), s.optBoolean("ok", true))) }
            return r
        }
    }
}

// ------------------------------------------------------------------ loop detection
/** Counts the same action on an unchanged screen (or failing again and again). */
class LoopGuard(private val warnAt: Int, private val stopAt: Int) {
    private val counts = HashMap<String, Int>()
    var unchangedSteps = 0; private set

    enum class Verdict { OK, WARN, STOP }

    fun canon(a: JSONObject): String = a.keys().asSequence().filter { !it.startsWith("_") && it != "do" }.sorted().joinToString(",") { "$it=${a.opt(it)}" }

    /** [sigBefore]/[sigAfter] are screen signatures (null = unknown). [failed] = the action reported an error. */
    fun record(name: String, args: JSONObject, sigBefore: Int?, sigAfter: Int?, failed: Boolean): Verdict {
        val unchanged = sigBefore != null && sigAfter != null && sigBefore == sigAfter
        if (sigBefore != null && sigAfter != null && !unchanged && !failed) { counts.clear(); unchangedSteps = 0; return Verdict.OK }
        if (unchanged) unchangedSteps++
        val key = name + "(" + canon(args) + ")@" + (sigBefore ?: 0)
        val c = (counts[key] ?: 0) + 1
        counts[key] = c
        return when { c >= stopAt -> Verdict.STOP; c >= warnAt -> Verdict.WARN; else -> Verdict.OK }
    }
}

// ------------------------------------------------------------------ the loop
class AgentLoop(
    private val client: ModelClient,
    private val host: AgentHost,
    private val emotions: Set<String>,
    /** (groups) -> (native system prompt, legacy JSON-mode system prompt). */
    private val prompts: (Set<Group>) -> Pair<String, String>,
    private val cfg: AgentConfig = AgentConfig(),
) {
    private val thinkBlock = Regex("(?s)<think>.*?</think>")
    private val thinkOpen = Regex("(?s)<think>.*$")
    private val sayRe = Regex("\"say\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)")
    private val emotionRe = Regex("\"emotion\"\\s*:\\s*\"([A-Za-z_]+)\"")
    private val langRe = Regex("\"lang\"\\s*:\\s*\"([A-Za-z]{2,3})\"")
    private val tagRe = Regex("^\\s*\\[(\\w+)\\]\\s*")

    /** A legacy JSON reply. */
    class Legacy(val emotion: String, val lang: String?, val say: String, val actions: JSONArray, val more: Boolean)

    private fun unescapeJson(x: String): String {
        val sb = StringBuilder()
        var i = 0
        while (i < x.length) {
            val c = x[i]
            if (c == '\\' && i + 1 < x.length) {
                when (val n = x[i + 1]) {
                    'n' -> { sb.append('\n'); i += 2 }
                    't' -> { sb.append(' '); i += 2 }
                    'u' -> {
                        val cp = if (i + 6 <= x.length) x.substring(i + 2, i + 6).toIntOrNull(16) else null
                        if (cp != null) { sb.append(cp.toChar()); i += 6 } else i += 2
                    }
                    else -> { sb.append(n); i += 2 }
                }
            } else { sb.append(c); i++ }
        }
        return sb.toString()
    }

    fun clean(raw: String) = raw.replace(thinkBlock, "").replace(thinkOpen, "").trim()

    /** Parses the legacy JSON reply (same rules as before: malformed JSON is never read aloud). */
    fun parseLegacy(rawIn: String, uiLang: String?): Legacy {
        val raw = clean(rawIn)
        val a = raw.indexOf('{'); val b = raw.lastIndexOf('}')
        if (a >= 0 && b > a) {
            try {
                val j = JSONObject(raw.substring(a, b + 1))
                val emo = jsStr(j, "emotion", "happy").lowercase().let { if (it in emotions) it else "happy" }
                return Legacy(emo, jsStr(j, "lang", "").ifEmpty { null }, jsStr(j, "say", ""), j.optJSONArray("actions") ?: JSONArray(), j.optBoolean("more", false))
            } catch (_: Exception) { }
        }
        if (raw.startsWith("{") || raw.contains("\"emotion\"") || raw.startsWith("```")) {
            val say = sayRe.find(raw)?.groupValues?.get(1)?.let { unescapeJson(it).trim() }.orEmpty()
            val emo = emotionRe.find(raw)?.groupValues?.get(1)?.lowercase()?.let { if (it in emotions) it else "happy" } ?: "happy"
            val lang = langRe.find(raw)?.groupValues?.get(1)?.lowercase()
            val fallback = say.ifEmpty { pick(uiLang, "Не расслышал ответ, повтори, пожалуйста.", "I did not get that, please try again.", "Javobni tushunmadim, iltimos qayta ayting.") }
            return Legacy(emo, if (say.isNotEmpty()) lang else null, fallback, JSONArray(), false)
        }
        val (tag, text) = splitTag(raw)
        return Legacy(tag, null, text, JSONArray(), false)
    }

    /** "[happy] text" -> ("happy", "text"); unknown or missing tag -> "happy". */
    fun splitTag(raw: String): Pair<String, String> {
        val m = tagRe.find(raw)
        val tag = m?.groupValues?.get(1)?.lowercase() ?: "happy"
        val text = if (m != null) raw.substring(m.range.last + 1) else raw
        return (if (tag in emotions) tag else "happy") to text.trim()
    }

    private val enReplyWords = Regex("\\b(the|is|are|you|your|to|and|of|for|with|this|that|it|i|will|can|here|let|sure|okay|ok|hello|hi)\\b")
    fun looksEnglish(say: String): Boolean {
        val t = say.lowercase()
        if (t.length < 12 || Regex("[а-яё]").containsMatchIn(t)) return false
        val words = Regex("[a-z']+").findAll(t).count().coerceAtLeast(1)
        return enReplyWords.findAll(t).count() * 5 >= words
    }

    fun brief(name: String, a: JSONObject): String {
        for (k in listOf("name", "text", "q", "to", "url", "key", "item", "index", "title", "dir", "panel", "setting", "action", "target")) {
            if (a.has(k) && !a.isNull(k)) {
                val v = a.optString(k, "").trim()
                if (v.isNotEmpty()) return "$name ${v.take(30)}"
            }
        }
        return name
    }

    private fun lname(uiLang: String?, o: Outcome, did: List<String>, last: String): String = when (o) {
        Outcome.CANCELLED -> pick(uiLang, "Остановился.", "Stopped.", "To'xtadim.")
        Outcome.BUDGET_STEPS, Outcome.BUDGET_TIME -> pick(uiLang, "Задача заняла слишком много шагов, я остановился.", "The task took too many steps, so I stopped.", "Vazifa juda ko'p qadam oldi, to'xtadim.") +
            (if (did.isEmpty()) "" else pick(uiLang, " Успел: ", " Done so far: ", " Bajarildi: ") + did.takeLast(4).joinToString(", ") + ".")
        Outcome.STUCK -> pick(uiLang, "Я застрял: экран не меняется", "I got stuck: the screen does not change", "Tiqilib qoldim: ekran o'zgarmayapti") +
            (if (last.isEmpty()) "" else " ($last)") + pick(uiLang, ". Сделайте этот шаг сами или попробуйте сказать иначе.", ". Please do this step yourself or ask me differently.", ". Bu qadamni o'zingiz qiling yoki boshqacha ayting.")
        Outcome.DONE -> ""
    }

    private fun isObservation(t: Tool?) = t != null && (t.exec == Exec.OBSERVE || (t.exec == Exec.DEVICE && t.risk == Risk.READ))

    fun run(task: AgentTask): AgentResult {
        val start = host.now()
        val log = RunLog(task.text, start)
        val msgs = ArrayList(task.history)
        msgs.add(Msg("user", task.shown))
        val groups = task.groups.toMutableSet()
        val did = ArrayList<String>()
        val guard = LoopGuard(cfg.loopWarn, cfg.loopStop)
        var tainted = false
        var steps = 0
        var observations = 0
        var looks = 0
        var malformed = 0
        var lastSig: Int? = null
        var lastAction = ""
        val ui = task.uiLang ?: task.lang
        // UI-only actions (an inline sticker) are always shown with the final answer, never in the middle of a task
        val deferred = JSONArray()

        fun finish(o: Outcome, emotion: String, say: String, lang: String?, actions: JSONArray): AgentResult {
            log.outcome = o.name.lowercase(); log.end = host.now(); log.summary = say
            return AgentResult(emotion, lang, say, actions, o, log, did)
        }
        fun stop(o: Outcome) = finish(o, if (o == Outcome.CANCELLED) "sad" else "confused", lname(ui, o, did, lastAction), ui, JSONArray())

        while (true) {
            if (host.cancelled()) return stop(Outcome.CANCELLED)
            if (steps >= cfg.maxSteps) return stop(Outcome.BUDGET_STEPS)
            if (host.now() - start >= cfg.maxMs) return stop(Outcome.BUDGET_TIME)
            if (steps > 0) host.progress(pick(ui, "Думаю…", "Thinking…", "O'ylayapman…"))
            val (nativeSys, legacySys) = prompts(groups)
            val tools = ToolRegistry.select(groups)
            var out = client.turn(TurnRequest(nativeSys, legacySys, tools, msgs))
            if (!out.native && !out.text.contains('{')) {
                val again = client.turn(TurnRequest(nativeSys, "$legacySys\nIMPORTANT: reply ONLY with the JSON object, nothing else.", tools, msgs))
                if (again.text.contains('{') || again.native) out = again
            }
            if (host.cancelled()) return stop(Outcome.CANCELLED)
            log.mode = if (out.native) "tools" else "json"

            // ---- interpret the turn
            val calls: List<ToolCall>
            var say: String
            var emotion: String
            var lang: String? = task.lang
            var more = false
            if (out.native) {
                calls = out.calls
                val (tag, text) = splitTag(clean(out.text))
                emotion = tag; say = text
                calls.firstOrNull { it.name == "done" }?.let { d ->
                    jsStr(d.args, "summary").trim().takeIf { it.isNotEmpty() }?.let { say = it }
                    jsStr(d.args, "emotion").lowercase().takeIf { it in emotions }?.let { emotion = it }
                }
            } else {
                val r = parseLegacy(out.text, ui)
                emotion = r.emotion; say = r.say; lang = r.lang ?: task.lang; more = r.more
                val list = ArrayList<ToolCall>()
                for (i in 0 until r.actions.length()) {
                    val a = r.actions.optJSONObject(i) ?: continue
                    val name = jsStr(a, "do").trim()
                    if (name.isEmpty()) continue
                    val args = JSONObject(a.toString()); args.remove("do")
                    list.add(ToolCall(ProviderFormat.newId(), name, args))
                }
                calls = list
            }
            val obsCalls = calls.filter { isObservation(ToolRegistry.get(it.name)) }
            val obsAllowed = observations < cfg.maxObservations
            val hasObs = obsCalls.isNotEmpty() && obsAllowed
            val hasMoreTools = calls.any { it.name == "more_tools" }
            val final = if (out.native) (calls.isEmpty() || calls.any { it.name == "done" } || !host.canAct) && !hasObs && !hasMoreTools
                        else (!more || !host.canAct) && !hasObs && !hasMoreTools

            if (final) {
                // the model drifted into English although the user spoke Uzbek/Russian: ask once more, firmly
                val t = task.lang
                if ((t == "uz" || t == "ru") && looksEnglish(say)) {
                    try {
                        val fix = "\nYour previous reply was in the WRONG language. Reply again with the same actions, but write the spoken text in ${if (t == "uz") "Uzbek (Latin script)" else "Russian"}" +
                            (if (out.native) "." else " and set \"lang\":\"$t\".") + " English is not allowed."
                        val o2 = client.turn(TurnRequest(nativeSys + fix, legacySys + fix, tools, msgs))
                        val s2 = if (o2.native) (o2.calls.firstOrNull { it.name == "done" }?.let { jsStr(it.args, "summary") }?.ifEmpty { null } ?: splitTag(clean(o2.text)).second)
                                 else parseLegacy(o2.text, ui).say
                        if (s2.isNotBlank() && !looksEnglish(s2)) { say = s2; if (!o2.native) lang = parseLegacy(o2.text, ui).lang ?: lang }
                    } catch (_: Exception) { }
                }
                val actions = JSONArray()
                for (i in 0 until deferred.length()) actions.put(deferred.get(i))
                for (c in calls) {
                    if (host.cancelled()) return stop(Outcome.CANCELLED)
                    val tool = ToolRegistry.get(c.name)
                    if (tool != null && (tool.exec == Exec.LOOP || isObservation(tool))) continue
                    if (tool == null) {
                        if (!out.native) actions.put(JSONObject(c.args.toString()).put("do", c.name))   // Actions reports "unknown command"
                        continue
                    }
                    val args = gate(tool, c, task, tainted, steps > 0, log, steps) ?: continue
                    if (tool.exec == Exec.ACTION) { actions.put(args); did.add(brief(c.name, args)) }
                    else if (host.canAct) {
                        host.progress(ToolRegistry.progress(c.name, args, ui))
                        val r = try { host.execute(tool, args) } catch (e: Exception) { ToolOutput(e.message ?: "failed", error = true) }
                        log.add(steps + 1, c.name, brief(c.name, args), if (r.error) r.text else "ok", !r.error)
                        did.add(brief(c.name, args))
                    }
                }
                for (i in 0 until actions.length()) actions.optJSONObject(i)?.let { log.add(steps + 1, jsStr(it, "do"), brief(jsStr(it, "do"), it), "run by the app", true) }
                return finish(Outcome.DONE, emotion, say, lang, actions)
            }

            // ---- one step
            steps++
            val results = ArrayList<ToolResult>()
            val legacyNotes = StringBuilder()
            var acted = false
            var screenWork = false
            var sigBefore = lastSig
            var lastVerdict = LoopGuard.Verdict.OK
            val actedCalls = ArrayList<Triple<String, JSONObject, Boolean>>()
            var bad = 0
            for (c in calls) {
                if (host.cancelled()) { results.add(ToolResult(c.id, c.name, "cancelled by the user", error = true)); continue }
                val tool = ToolRegistry.get(c.name)
                fun res(text: String, err: Boolean = false, image: String? = null) { results.add(ToolResult(c.id, c.name, text, image, err)) }
                if (tool == null) {
                    bad++
                    res("unknown tool \"${c.name}\". Use only the listed tools.", true)
                    legacyNotes.append("Unknown action ${c.name}.\n")
                    continue
                }
                when {
                    c.name == "done" -> res("not finished: read the results of the other calls first, then call done.")
                    c.name == "plan" -> { log.add(steps, "plan", jsStr(c.args, "text").take(80), "noted", true); res("noted") }
                    c.name == "more_tools" -> {
                        val g = Group.of(jsStr(c.args, "group"))
                        if (g == null) res("unknown group; use screen, phone, data, organizer or mascot", true)
                        else { groups.add(g); res("added: " + ToolRegistry.ALL.filter { it.group == g }.joinToString(", ") { it.name }); legacyNotes.append("More actions are now listed in the system prompt.\n") }
                    }
                    c.name == "wait" -> {
                        val s = c.args.optInt("seconds", 2).coerceIn(1, cfg.maxWaitS)
                        host.progress(ToolRegistry.progress("wait", c.args, ui))
                        host.sleep(s * 1000L)
                        res("waited $s s"); screenWork = true; acted = true
                    }
                    isObservation(tool) -> {
                        if (!obsAllowed) { res("too many look-ups in this task; answer with what you have", true); continue }
                        if (c.name == "look" && looks >= cfg.maxLooks) { res("screenshot limit for this task reached; use the screen text", true); continue }
                        observations++
                        if (c.name == "look") looks++
                        if (tool.group == Group.SCREEN) screenWork = true
                        host.progress(ToolRegistry.progress(c.name, c.args, ui))
                        val o = try { host.execute(tool, JSONObject(c.args.toString()).put("do", c.name)) } catch (e: Exception) { ToolOutput("unavailable (${e.message})", error = true) }
                        tainted = true   // any looked-up data counts as untrusted for the injection gate (as before)
                        val text = (if (o.untrusted != null) Safety.wrap(o.untrusted, o.text) else o.text) + (o.note?.let { "\n$it" } ?: "")
                        res(text, o.error, o.image)
                        legacyNotes.append(text).append('\n')
                        log.add(steps, c.name, brief(c.name, c.args), if (o.error) o.text.take(80) else "read", !o.error)
                    }
                    c.name in DEFER_TO_REPLY -> { deferred.put(JSONObject(c.args.toString()).put("do", c.name)); res("will be shown with your final answer") }
                    !host.canAct -> res("actions cannot run right now", true)
                    !out.native && !more -> legacyNotes.append("Not run yet: ${c.name} (repeat it in your final reply if still needed).\n")
                    else -> {
                        val args = gate(tool, c, task, tainted, true, log, steps)
                        if (args == null) {
                            val why = lastDenial.ifEmpty { "not allowed" }
                            res("not done: $why", true); legacyNotes.append("${c.name}: not done: $why\n"); continue
                        }
                        if (tool.group == Group.SCREEN) screenWork = true
                        val target = lastTargetLabel
                        host.progress(ToolRegistry.progress(c.name, args, ui, target))
                        val o = try { host.execute(tool, args) } catch (e: Exception) { ToolOutput(e.message ?: "failed", error = true) }
                        acted = true
                        lastAction = brief(c.name, args)
                        did.add(lastAction)
                        actedCalls.add(Triple(c.name, args, o.error))
                        val shownText = if (o.error) "error: ${o.text}" else o.text.ifEmpty { "ok" }
                        res(shownText, o.error, o.image)
                        legacyNotes.append("${c.name}: ").append(shownText).append('\n')
                        log.add(steps, c.name, logDetail(c.name, args, target), if (o.error) o.text else "ok", !o.error)
                        if (o.error) break   // the rest of this step depended on it: let the model look again
                    }
                }
            }
            // every call needs an answer (calls after a failure are reported as skipped)
            for (c in calls) if (results.none { it.id == c.id }) results.add(ToolResult(c.id, c.name, "skipped: an earlier action in this step failed", error = true))

            // ---- feedback: the screen after the actions
            var screenText = ""
            val wantScreen = acted && (if (out.native) (screenWork || Group.SCREEN in groups) else more)
            if (wantScreen && !host.cancelled()) {
                host.settle()
                val s = host.screen()
                if (s != null) {
                    tainted = true
                    screenText = "Screen now (numbered elements; tap_node uses these numbers):\n" + Safety.wrap("screen", s.text)
                    for ((name, args, err) in actedCalls) {
                        val v = guard.record(name, args, sigBefore, s.sig, err)
                        if (v.ordinal > lastVerdict.ordinal) lastVerdict = v
                    }
                    lastSig = s.sig
                } else {
                    for ((name, args, err) in actedCalls) { val v = guard.record(name, args, null, null, err); if (v.ordinal > lastVerdict.ordinal) lastVerdict = v }
                    screenText = "(the screen cannot be read now)"
                }
            } else for ((name, args, err) in actedCalls) { val v = guard.record(name, args, null, null, err); if (v.ordinal > lastVerdict.ordinal) lastVerdict = v }
            sigBefore = lastSig
            if (lastVerdict == LoopGuard.Verdict.STOP) return stop(Outcome.STUCK)
            val warn = if (lastVerdict == LoopGuard.Verdict.WARN)
                "WARNING: you repeated the same action and nothing changed. Do something different (scroll, back, search inside the app, another element, look) or call done and tell the user what blocks you." else ""
            val budget = if (cfg.maxSteps - steps <= 3) "Only ${cfg.maxSteps - steps} steps left: finish soon." else ""

            // ---- append the step to the transcript
            if (out.native) {
                msgs.add(Msg("assistant", clean(out.text), calls, raw = out.raw, rawKind = out.rawKind))
                val extra = listOf(screenText, warn, budget).filter { it.isNotEmpty() }.joinToString("\n")
                if (extra.isNotEmpty() && results.isNotEmpty()) {
                    val last = results.removeAt(results.size - 1)
                    results.add(ToolResult(last.id, last.name, last.text + "\n" + extra, last.image, last.error))
                }
                msgs.add(Msg("user", results = results))
                malformed += bad + out.malformed.size
                if (malformed >= cfg.maxMalformed) { client.nativeBroken(); malformed = 0 }
            } else {
                msgs.add(Msg("assistant", clean(out.text)))
                val sb = StringBuilder()
                if (acted || actedCalls.isNotEmpty()) sb.append("Step result:\n")
                sb.append(legacyNotes)
                if (screenText.isNotEmpty()) sb.append(screenText).append('\n')
                if (warn.isNotEmpty()) sb.append(warn).append('\n')
                if (budget.isNotEmpty()) sb.append(budget).append('\n')
                msgs.add(Msg("user", sb.toString().trim().ifEmpty { "ok" }))
            }
        }
    }

    companion object {
        /** Shown with the final answer even when called mid-task. */
        val DEFER_TO_REPLY = setOf("sticker")
    }

    @Volatile private var lastDenial = ""
    @Volatile private var lastTargetLabel: String? = null

    private fun logDetail(name: String, a: JSONObject, target: String?): String {
        if (name == "type_text" || name == "paste" || name == "clipboard_set") return name + " (" + jsStr(a, "text").length + " chars)"
        return brief(name, a) + (target?.takeIf { it.isNotBlank() }?.let { " «${it.take(30)}»" } ?: "")
    }

    /** Safety gate + approval for one action. Returns the args to run (with "do"), or null when it must not run. */
    private fun gate(tool: Tool, c: ToolCall, task: AgentTask, tainted: Boolean, agentLoop: Boolean, log: RunLog, step: Int): JSONObject? {
        lastDenial = ""
        lastTargetLabel = null
        val target = if (tool.group == Group.SCREEN || tool.name == "type_text") try { host.target(tool, c.args) } catch (_: Exception) { null } else null
        lastTargetLabel = target?.label
        val d = Safety.decide(tool, c, task.text, task.untrusted, tainted, agentLoop, target, try { host.foreground() } catch (_: Exception) { null }, task.uiLang ?: task.lang)
        return when (d) {
            is Decision.Allow -> d.args
            is Decision.Deny -> { lastDenial = d.reason; log.add(step, c.name, brief(c.name, c.args), "blocked: " + d.reason.take(80), false); null }
            is Decision.Ask -> {
                val ok = !host.cancelled() && try { host.approve(ApprovalReq(d.title, d.detail, d.lock, tool.name)) } catch (_: Exception) { false }
                if (!ok) { lastDenial = "the user did not allow it. Do not try again; ask the user what to do."; log.add(step, c.name, brief(c.name, c.args), "denied by the user", false); null }
                else { log.add(step, c.name, brief(c.name, c.args), "allowed by the user", true); d.args }
            }
        }
    }
}
