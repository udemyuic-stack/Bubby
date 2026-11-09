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
        return Parsed.Ok(Enriched(lang, conf, lemma, senses, p?.optString("ipa", "")?.trim()?.take(60) ?: "", p?.optString("reading", "")?.trim()?.take(80) ?: "", rel, meta, context))
    }
}
