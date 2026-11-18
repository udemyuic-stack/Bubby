package com.example.buddy.ui.vocab

import org.json.JSONArray
import org.json.JSONObject

sealed class Parsed {
    class Ok(val data: Enriched) : Parsed()
    class NeedsLanguage(val detected: String?, val confidence: Double) : Parsed()
    class Bad(val reason: String) : Parsed()
}

object EnrichValidator {
    const val MIN_CONFIDENCE = 0.7
    private const val MAX_SENSES = 5
    private const val MAX_TRANSLATIONS = 4
    private const val MAX_EXAMPLES = 3
    private val LEVELS = setOf("A1", "A2", "B1", "B2", "C1", "C2")

    private fun strings(a: JSONArray?, maxItems: Int, maxLen: Int): List<String> {
        if (a == null) return emptyList()
        val out = ArrayList<String>()
        for (i in 0 until a.length()) {
            val s = a.optString(i, "").trim()
            if (s.isNotEmpty() && s.length <= maxLen && s !in out) out.add(s)
            if (out.size >= maxItems) break
        }
        return out
    }

    private fun extract(raw: String): JSONObject? {
        val a = raw.indexOf('{')
        val b = raw.lastIndexOf('}')
        if (a < 0 || b <= a) return null
        return try { JSONObject(raw.substring(a, b + 1)) } catch (_: Exception) { null }
    }

    fun parse(raw: String, target: String, original: String, forced: String?, hasContext: Boolean = false): Parsed {
        val o = extract(raw) ?: return Parsed.Bad("the reply is not a JSON object")
        val reported = o.optString("language", "").lowercase().substringBefore('-').trim()
        val conf = if (forced != null) 1.0 else o.optDouble("confidence", 0.5).let { if (it.isNaN()) 0.5 else it }
        val lang = forced ?: reported
        if (forced == null && (!Languages.isSupported(lang) || conf < MIN_CONFIDENCE || lang == target)) {
            return Parsed.NeedsLanguage(if (Languages.isSupported(lang)) lang else null, conf)
        }
        if (lang == target) return Parsed.Bad("the word language equals the translation language")
        val arr = o.optJSONArray("senses") ?: return Parsed.Bad("senses are missing")
        val origNorm = VocabText.normalize(original)
        val senses = ArrayList<SenseData>()
        for (i in 0 until arr.length()) {
            val s = sense(arr.optJSONObject(i) ?: continue, origNorm) ?: continue
            senses.add(s)
            if (senses.size >= MAX_SENSES) break
        }
        if (senses.isEmpty()) return Parsed.Bad("no sense has both a translation and a definition")
        val context = if (!hasContext) null else o.optJSONObject("context")?.let { c ->
            val cs = c.optJSONObject("sense")?.let { sense(it, origNorm) }
            if (cs == null) null else ContextData(c.optString("translation", "").trim().take(400), cs)
        }
        return rest(o, original, lang, conf, senses, context)
    }

    private fun sense(s: JSONObject, origNorm: String): SenseData? {
        val pos = s.optString("pos", "").trim().take(30)
        val proper = pos.contains("proper", true)
        val translations = strings(s.optJSONArray("translations"), MAX_TRANSLATIONS + 2, 80)
            .filter { proper || VocabText.normalize(it) != origNorm }.take(MAX_TRANSLATIONS)
        val definition = s.optString("definition", "").trim().take(300)
        if (translations.isEmpty() || definition.isEmpty()) return null
        val examples = ArrayList<ExampleData>()
        val ea = s.optJSONArray("examples")
        if (ea != null) for (j in 0 until ea.length()) {
            val e = ea.optJSONObject(j) ?: continue
            val text = e.optString("text", "").trim()
            if (text.isEmpty() || text.length > EnrichPrompt.MAX_CONTEXT) continue
            examples.add(ExampleData(text, e.optString("translation", "").trim().take(200), e.optString("reading", "").trim().take(200)))
            if (examples.size >= MAX_EXAMPLES) break
        }
        val level = s.optString("level", "").trim().uppercase().let { if (it in LEVELS) it else "" }
        return SenseData(pos, definition, level, s.optString("usage", "").trim().take(300), s.optString("formality", "").trim().take(30), translations, examples)
    }

    private fun rest(o: JSONObject, original: String, lang: String, conf: Double, senses: List<SenseData>, context: ContextData?): Parsed {
        val lemma = o.optString("lemma", "").trim().let { if (it.isEmpty() || it.length > 80) VocabText.clean(original) else it }
        val p = o.optJSONObject("pronunciation")
        val rel = LinkedHashMap<String, List<String>>()
        val ro = o.optJSONObject("relations")
        if (ro != null) for (k in MetaSchema.RELATIONS) {
            val l = strings(ro.optJSONArray(k), 6, 60)
            if (l.isNotEmpty()) rel[k] = l
        }
        val meta = LinkedHashMap<String, String>()
        val mo = o.optJSONObject("meta")
        if (mo != null) {
            val keys = mo.keys()
            while (keys.hasNext() && meta.size < 12) {
                val k = keys.next()
                if (!MetaSchema.keep(lang, k)) continue
                val v = mo.opt(k)?.toString()?.trim() ?: continue
                if (v.isEmpty() || v == "null" || v == "[]" || v == "{}") continue
                meta[k] = v.take(400)
            }
        }
        return Parsed.Ok(Enriched(lang, conf, lemma, senses, p?.optString("ipa", "")?.trim()?.take(60) ?: "", p?.optString("reading", "")?.trim()?.take(80) ?: "", rel, meta, context, PronParse.parse(o.optJSONObject("pron_en"), lang, lemma)))
    }
}

object PronParse {
    private const val MAX_IPA = 60
    private const val MAX_SYLLABLES = 12
    private const val MAX_SYLLABLE_LEN = 12
    private const val MAX_MISTAKES = 3
    private const val MAX_MISTAKE_LEN = 140
    private val BAD_IPA = Regex("[0-9<>{}]")
    private val ASCII_LETTERS = Regex("[A-Za-z]+")
    private val SYLLABLE_MARKS = Regex("[-·.]")
    private val LEMMA_MARKS = Regex("[\\s'-]")

    fun parse(o: JSONObject?, lang: String, lemma: String): PronDetail? {
        if (lang != "en" || o == null) return null
        val uk = ipa(o.optString("uk", ""), lemma)
        val us = ipa(o.optString("us", ""), lemma)
        val syllables = syllables(o.optJSONArray("syllables"), lemma)
        val stress = if (syllables.isEmpty()) -1 else stress(o.opt("stress"), syllables.size)
        val mistakes = mistakes(o.optJSONArray("mistakes"))
        val d = PronDetail(uk, us, syllables, stress, mistakes)
        return if (d.empty) null else d
    }

    private fun ipa(raw: String, lemma: String): String {
        var s = raw.trim()
        if (s.length >= 2 && ((s.first() == '/' && s.last() == '/') || (s.first() == '[' && s.last() == ']'))) s = s.substring(1, s.length - 1).trim()
        if (s.isEmpty() || s.length > MAX_IPA) return ""
        if (BAD_IPA.containsMatchIn(s)) return ""
        if (ASCII_LETTERS.matches(s) && s.equals(lemma.trim(), ignoreCase = true)) return ""
        return s
    }

    private fun syllables(a: JSONArray?, lemma: String): List<String> {
        if (a == null || a.length() < 1 || a.length() > MAX_SYLLABLES) return emptyList()
        val out = ArrayList<String>()
        for (i in 0 until a.length()) {
            val s = a.opt(i) as? String ?: return emptyList()
            val t = s.trim()
            if (t.isEmpty() || t.length > MAX_SYLLABLE_LEN) return emptyList()
            out.add(t)
        }
        val spelled = VocabText.fold(out.joinToString("").replace(SYLLABLE_MARKS, "")).replace(LEMMA_MARKS, "")
        val target = VocabText.fold(lemma).replace(LEMMA_MARKS, "")
        return if (spelled == target && target.isNotEmpty()) out else emptyList()
    }

    private fun stress(v: Any?, size: Int): Int {
        val n = when (v) {
            is Int -> v
            is Long -> if (v in 0L..Int.MAX_VALUE.toLong()) v.toInt() else -1
            is Number -> v.toDouble().let { if (it == Math.floor(it) && it >= 0 && it < size) it.toInt() else -1 }
            else -> -1
        }
        return if (n in 0 until size) n else -1
    }

    private fun mistakes(a: JSONArray?): List<String> {
        if (a == null) return emptyList()
        val out = ArrayList<String>()
        for (i in 0 until a.length()) {
            val s = (a.opt(i) as? String)?.trim() ?: continue
            if (s.isEmpty() || s.length > MAX_MISTAKE_LEN || s in out) continue
            out.add(s)
            if (out.size >= MAX_MISTAKES) break
        }
        return out
    }
}
