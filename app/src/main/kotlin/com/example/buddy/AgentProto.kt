package com.example.buddy

/*
 * The Android-free part of the agents link v3 (docs/AGENTS-V3.md): capabilities, session details, questions and their
 * answers, file changes and diff parts. Everything the bridge sends is checked against the limits of the spec and cleaned
 * (no control or format characters, lengths and counts capped) before the app shows it; nothing here touches org.json or
 * Android, so app/qa-jvm tests it on a plain JVM. AgentLink turns each JSON line into Map/List/String/Number/Boolean first.
 * Message shapes follow the open-source Coucou project's phone link (MIT); the code is Bubby's own.
 */

/** One file an agent changed (capability `diffs`). [id] is the bridge's id as text; [idNum] is set when it was a number. */
data class AgentFile(
    val id: String, val idNum: Long?, val name: String, val added: Int, val removed: Int,
    val tooLarge: Boolean = false, val isNew: Boolean = false
)

/** One running agent. [project], [steps], [finalLine] come with `details`, [files] with `diffs`; empty when not offered. */
data class AgentSession(
    val pillId: String, val agent: String, val state: String, val statusText: String, val stepIndex: Int, val stepCount: Int,
    val project: String = "", val steps: List<String> = emptyList(), val finalLine: String = "", val files: List<AgentFile> = emptyList()
)

/** [label] is the exact text that goes back in an answer; [shown] is the cleaned text the app draws. */
data class QOption(val label: String, val shown: String, val description: String)
data class QItem(val question: String, val multiSelect: Boolean, val options: List<QOption>)
/** A question Claude Code asks (capability `answers`). [createdAt] is the bridge's time in ms. */
data class AgentQuestion(val pillId: String, val fingerprint: String, val createdAt: Long, val items: List<QItem>)

/** One line of a diff: [kind] is '+', '-', ' ' (context) or '@' (a hunk starts). */
data class DiffLine(val kind: Char, val text: String)
/** One `diff` message. */
data class DiffPart(
    val pillId: String, val fileId: String, val name: String, val added: Int, val removed: Int,
    val tooLarge: Boolean, val gone: Boolean, val truncated: Boolean, val part: Int, val parts: Int, val lines: List<DiffLine>
)

object AgentProto {
    /** What this phone understands; it uses only those that `welcome.caps` offers back. */
    val CAPS = listOf("details", "answers", "diffs", "relay")

    const val MAX_SESSIONS = 50
    const val MAX_AGENT = 40
    const val MAX_STATUS = 200
    const val MAX_PROJECT = 64
    const val MAX_STEPS = 20
    const val MAX_STEP = 200
    const val MAX_FINAL = 200
    const val MAX_FILES = 20
    const val MAX_FILE_NAME = 80
    const val MAX_QUESTIONS = 4
    const val MAX_OPTIONS = 8
    const val MAX_QUESTION = 500
    const val MAX_LABEL = 120
    const val MAX_DESCRIPTION = 300
    const val QUESTION_TTL_MS = 115_000L
    const val MAX_DIFF_PARTS = 2
    const val DIFF_PART_LINES = 100
    const val MAX_DIFF_LINE = 400
    const val DIFF_RATE_MAX = 20
    const val DIFF_RATE_WINDOW_MS = 10_000L

    private val STATES = setOf(
        "idle", "working", "thinking", "searching", "approval", "question",
        "error", "finished", "ratelimit", "sleeping", "dizzy"
    )

    // ---- cleaning ------------------------------------------------------------------------------------------------------

    /**
     * Text from the bridge made safe to draw: control, format (bidi overrides, zero-width), private-use and unassigned
     * characters are dropped, lone surrogates too; tabs become spaces; line breaks are kept only when [multiline]
     * (at most two in a row); runs of spaces collapse unless [keepSpaces]; trimmed (unless [keepSpaces]) and cut at [max]
     * UTF-16 units without splitting a pair.
     */
    fun clean(s: String?, max: Int, multiline: Boolean = false, keepSpaces: Boolean = false): String {
        if (s.isNullOrEmpty() || max <= 0) return ""
        val b = StringBuilder(minOf(s.length, max + 8))
        var i = 0
        var breaks = 0
        val limit = minOf(s.length, max * 4 + 64)   // never walk a huge string: what is past this is cut anyway
        while (i < limit) {
            val cp = s.codePointAt(i)
            i += Character.charCount(cp)
            val c: Int = when {
                cp == '\n'.code || cp == '\r'.code || cp == 0x2028 || cp == 0x2029 || cp == 0x85 -> if (multiline) '\n'.code else ' '.code
                cp == '\t'.code -> ' '.code
                else -> when (Character.getType(cp).toByte()) {
                    Character.CONTROL, Character.FORMAT, Character.PRIVATE_USE, Character.UNASSIGNED, Character.SURROGATE -> -1
                    Character.LINE_SEPARATOR, Character.PARAGRAPH_SEPARATOR -> if (multiline) '\n'.code else ' '.code
                    Character.SPACE_SEPARATOR -> ' '.code
                    else -> cp
                }
            }
            if (c < 0) continue
            if (c == '\n'.code) {
                if (cp == '\r'.code && i < s.length && s[i] == '\n') i++
                while (!keepSpaces && b.isNotEmpty() && b[b.length - 1] == ' ') b.setLength(b.length - 1)
                if (b.isEmpty() || ++breaks > 2) continue
                b.append('\n'); continue
            }
            if (c == ' '.code && !keepSpaces && (b.isEmpty() || b[b.length - 1] == ' ' || b[b.length - 1] == '\n')) continue
            breaks = 0
            b.appendCodePoint(c)
            if (b.length >= max + 2) break
        }
        var out = if (keepSpaces) b.toString() else b.toString().trim()
        if (out.length > max) {
            var cut = max
            if (cut > 0 && Character.isHighSurrogate(out[cut - 1])) cut--
            out = out.substring(0, cut)
            if (!keepSpaces) out = out.trimEnd()
        }
        return out
    }

    /** The last segment of a path ("src/a/b.kt" -> "b.kt"), cleaned and cut: the app never shows a path. */
    fun baseName(s: String?, max: Int): String {
        val c = clean(s, 4096)
        val last = c.trimEnd('/', '\\').substringAfterLast('/').substringAfterLast('\\')
        return clean(last, max)
    }

    private fun str(v: Any?): String = v as? String ?: ""
    private fun int(v: Any?, max: Int): Int {
        val n = (v as? Number)?.toDouble() ?: return 0
        if (n.isNaN() || n <= 0.0) return 0
        return if (n >= max) max else n.toInt()
    }
    private fun yes(v: Any?) = v == true

    /** A file id: a whole number 0..2^53, or a short string (sent back exactly as it came). */
    fun fileId(v: Any?): Pair<String, Long?>? = when (v) {
        is String -> clean(v, 64).takeIf { it.isNotEmpty() && it == v }?.let { it to null }
        is Number -> {
            val d = v.toDouble()
            if (d.isNaN() || d < 0 || d > 9.007199254740991E15 || d != Math.floor(d)) null else d.toLong().let { it.toString() to it }
        }
        else -> null
    }

    // ---- capabilities --------------------------------------------------------------------------------------------------

    /** The capabilities a `welcome` offers that this phone also asked for; no `caps` at all means none (a v1/V2 bridge). */
    fun parseCaps(v: Any?): Set<String> = (v as? List<*>)?.mapNotNull { it as? String }?.filter { it in CAPS }?.toSet() ?: emptySet()

    // ---- sessions ------------------------------------------------------------------------------------------------------

    /** One entry of `sessions`; details and files only when [caps] offered them. Null if it has no pillId. */
    fun parseSession(o: Map<*, *>, caps: Set<String>): AgentSession? {
        val pill = clean(str(o["pillId"]), 64)
        if (pill.isEmpty()) return null
        val state = str(o["state"]).let { if (it in STATES) it else "idle" }
        var project = ""; var steps = emptyList<String>(); var finalLine = ""
        if ("details" in caps) {
            project = baseName(str(o["project"]), MAX_PROJECT)
            steps = parseSteps(o["steps"])
            finalLine = clean(str(o["finalLine"]), MAX_FINAL)
        }
        val files = if ("diffs" in caps) parseFiles(o["files"]) else emptyList()
        val count = int(o["stepCount"], 100_000)
        return AgentSession(
            pill, clean(str(o["agent"]), MAX_AGENT), state, clean(str(o["statusText"]), MAX_STATUS),
            int(o["stepIndex"], 100_000), count, project, steps, finalLine, files
        )
    }

    /** The last [MAX_STEPS] steps, oldest first, each cleaned and cut; blank ones dropped. */
    fun parseSteps(v: Any?): List<String> {
        val l = v as? List<*> ?: return emptyList()
        return l.takeLast(MAX_STEPS * 3).mapNotNull { (it as? String)?.let { s -> clean(s, MAX_STEP) }?.takeIf { s -> s.isNotEmpty() } }.takeLast(MAX_STEPS)
    }

    /** At most [MAX_FILES] files, newest first as sent; an entry without a valid id or name is dropped. */
    fun parseFiles(v: Any?): List<AgentFile> {
        val l = v as? List<*> ?: return emptyList()
        val out = ArrayList<AgentFile>()
        val seen = HashSet<String>()
        for (e in l) {
            if (out.size >= MAX_FILES) break
            val m = e as? Map<*, *> ?: continue
            val (id, num) = fileId(m["id"]) ?: continue
            val name = baseName(str(m["name"]), MAX_FILE_NAME)
            if (name.isEmpty() || !seen.add(id)) continue
            out.add(AgentFile(id, num, name, int(m["added"], 1_000_000), int(m["removed"], 1_000_000), yes(m["tooLarge"]), yes(m["isNew"])))
        }
        return out
    }

    // ---- questions -----------------------------------------------------------------------------------------------------

    /**
     * A `question`, or null when it breaks any limit of the spec (1..4 questions of 1..8 options; question text <= 500,
     * label <= 120, description <= 300; no blank text; no two labels alike) or is already 115 s old at [now]. Labels are
     * kept exactly as sent because the answer must carry them back; the app draws the cleaned [QOption.shown].
     */
    fun parseQuestion(o: Map<*, *>, now: Long): AgentQuestion? {
        val fp = str(o["fingerprint"])
        if (fp.isEmpty() || fp.length > 128 || clean(fp, 128) != fp) return null
        val pill = clean(str(o["pillId"]), 64)
        val created = (o["createdAt"] as? Number)?.toLong()?.takeIf { it > 0L } ?: now
        if (expired(created, now)) return null
        val qs = o["questions"] as? List<*> ?: return null
        if (qs.isEmpty() || qs.size > MAX_QUESTIONS) return null
        val items = ArrayList<QItem>()
        for (q in qs) {
            val m = q as? Map<*, *> ?: return null
            val text = m["question"] as? String ?: return null
            if (text.length > MAX_QUESTION) return null
            val shownText = clean(text, MAX_QUESTION, multiline = true)
            if (shownText.isEmpty()) return null
            val multi = m["multiSelect"]
            if (multi != null && multi !is Boolean) return null
            val opts = m["options"] as? List<*> ?: return null
            if (opts.isEmpty() || opts.size > MAX_OPTIONS) return null
            val options = ArrayList<QOption>()
            val labels = HashSet<String>()
            val shownLabels = HashSet<String>()
            for (op in opts) {
                val om = op as? Map<*, *> ?: return null
                val label = om["label"] as? String ?: return null
                if (label.isEmpty() || label.length > MAX_LABEL) return null
                val shown = clean(label, MAX_LABEL)
                if (shown.isEmpty() || !labels.add(label) || !shownLabels.add(shown)) return null
                val d = om["description"]
                if (d != null && d !is String) return null
                val desc = d as? String ?: ""
                if (desc.length > MAX_DESCRIPTION) return null
                options.add(QOption(label, shown, clean(desc, MAX_DESCRIPTION, multiline = true)))
            }
            items.add(QItem(shownText, multi == true, options))
        }
        return AgentQuestion(pill, fp, created, items)
    }

    /** The phone offers a question for 115 s after the bridge made it (a bridge clock ahead of the phone counts as now). */
    fun expired(createdAt: Long, now: Long): Boolean = now - minOf(createdAt, now) >= QUESTION_TTL_MS
    fun msLeft(createdAt: Long, now: Long): Long = (QUESTION_TTL_MS - (now - minOf(createdAt, now))).coerceAtLeast(0L)

    /** Exactly what the bridge accepts: one list per question, known distinct labels, one for single choice, >= 1 for multi. */
    fun picksValid(q: AgentQuestion, picks: List<List<String>>): Boolean {
        if (picks.size != q.items.size) return false
        for ((i, item) in q.items.withIndex()) {
            val p = picks[i]
            if (p.isEmpty() || p.toSet().size != p.size) return false
            val labels = item.options.mapTo(HashSet()) { it.label }
            if (p.any { it !in labels }) return false
            if (!item.multiSelect && p.size != 1) return false
        }
        return true
    }

    /** The labels for the chosen option indexes (in option order), or null when they would not be a valid answer yet. */
    fun picksFor(q: AgentQuestion, sel: List<Set<Int>>): List<List<String>>? {
        if (sel.size != q.items.size) return null
        val picks = q.items.mapIndexed { i, item -> sel[i].sorted().mapNotNull { item.options.getOrNull(it)?.label } }
        return picks.takeIf { picksValid(q, it) }
    }

    /** Taps option [oi] of question [qi]: a single choice replaces the pick, a multiple choice toggles it. */
    fun toggle(q: AgentQuestion, sel: List<Set<Int>>, qi: Int, oi: Int): List<Set<Int>> {
        val item = q.items.getOrNull(qi) ?: return sel
        if (oi !in item.options.indices) return sel
        val base = if (sel.size == q.items.size) sel else List(q.items.size) { emptySet() }
        return base.mapIndexed { i, s ->
            when {
                i != qi -> s
                !item.multiSelect -> if (oi in s) emptySet() else setOf(oi)
                oi in s -> s - oi
                else -> s + oi
            }
        }
    }

    fun emptySelection(q: AgentQuestion): List<Set<Int>> = List(q.items.size) { emptySet() }

    // ---- diffs ---------------------------------------------------------------------------------------------------------

    /** One `diff` message, or null if it breaks the limits (at most 2 parts of 100 lines, part in range, ids present). */
    fun parseDiff(o: Map<*, *>): DiffPart? {
        val pill = clean(str(o["pillId"]), 64)
        val (id, _) = fileId(o["fileId"]) ?: return null
        if (pill.isEmpty()) return null
        val gone = yes(o["gone"])
        val partsRaw = o["parts"] as? Number
        val partRaw = o["part"] as? Number
        val parts = partsRaw?.toInt() ?: 1
        val part = partRaw?.toInt() ?: 0
        if (partsRaw != null && partsRaw.toDouble() != parts.toDouble()) return null
        if (partRaw != null && partRaw.toDouble() != part.toDouble()) return null
        if (parts !in 1..MAX_DIFF_PARTS || part !in 0 until parts) return null
        val raw = o["lines"] as? List<*> ?: emptyList<Any?>()
        var cut = raw.size > DIFF_PART_LINES
        val lines = ArrayList<DiffLine>()
        for (r in raw.take(DIFF_PART_LINES)) {
            val pair = r as? List<*> ?: continue
            val k = (pair.getOrNull(0) as? String)?.firstOrNull() ?: ' '
            val kind = if (k == '+' || k == '-' || k == '@') k else ' '
            val t = pair.getOrNull(1) as? String ?: ""
            if (t.length > MAX_DIFF_LINE) cut = true
            lines.add(DiffLine(kind, clean(t, MAX_DIFF_LINE, keepSpaces = true).trimEnd()))
        }
        return DiffPart(
            pill, id, baseName(str(o["name"]), MAX_FILE_NAME), int(o["added"], 1_000_000), int(o["removed"], 1_000_000),
            yes(o["tooLarge"]), gone, yes(o["truncated"]) || cut, part, parts, if (gone) emptyList() else lines
        )
    }
}

/** Collects the parts of one file's diff; parts for another file, out of range, repeated or contradicting are ignored. */
class DiffAssembler(val pillId: String, val fileId: String) {
    var name = ""; private set
    var added = 0; private set
    var removed = 0; private set
    var tooLarge = false; private set
    var gone = false; private set
    var truncated = false; private set
    private var parts = 0
    private val got = HashMap<Int, List<DiffLine>>()

    val complete: Boolean get() = gone || (parts > 0 && got.size == parts)
    val received: Int get() = got.size

    /** True when [p] was taken. */
    fun add(p: DiffPart): Boolean {
        if (p.pillId != pillId || p.fileId != fileId || complete) return false
        if (p.gone) { gone = true; got.clear(); return true }
        if (parts == 0) parts = p.parts else if (p.parts != parts) return false
        if (got.containsKey(p.part)) return false
        got[p.part] = p.lines
        if (name.isEmpty()) name = p.name
        added = maxOf(added, p.added); removed = maxOf(removed, p.removed)
        tooLarge = tooLarge || p.tooLarge; truncated = truncated || p.truncated
        return true
    }

    /** All lines in order (only meaningful once [complete]). */
    fun lines(): List<DiffLine> = if (gone) emptyList() else (0 until parts).flatMap { got[it] ?: emptyList() }

    /** Forgets everything it holds. */
    fun clear() { got.clear(); parts = 0; name = "" }
}

/** At most [max] events in any [windowMs] (the phone keeps the bridge's 20 `getDiff` in 10 s too). */
class RateWindow(private val max: Int, private val windowMs: Long) {
    private val times = ArrayDeque<Long>()
    @Synchronized fun tryTake(now: Long): Boolean {
        while (times.isNotEmpty() && now - times.first() >= windowMs) times.removeFirst()
        if (times.size >= max) return false
        times.addLast(now)
        return true
    }
}
