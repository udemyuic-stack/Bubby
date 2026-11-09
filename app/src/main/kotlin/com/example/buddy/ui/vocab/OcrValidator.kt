package com.example.buddy.ui.vocab

import org.json.JSONArray
import org.json.JSONObject

sealed class OcrParsed {
    class Ok(val sentences: List<String>, val truncated: Boolean) : OcrParsed()
    object Empty : OcrParsed()
    class Bad(val reason: String) : OcrParsed()
}

class Token(val text: String, val word: String)

object OcrValidator {
    private val THINK = Regex("(?is)<think>.*?</think>")
    private val FENCE = Regex("```[a-zA-Z]*")
    private val SPACES = Regex("\\s+")
    private const val ELLIPSIS = "…"

    fun parse(raw: String): OcrParsed {
        val s = raw.replace(THINK, "").replace(FENCE, "")
        val a = s.indexOf('{')
        val b = s.lastIndexOf('}')
        if (a < 0 || b <= a) return OcrParsed.Bad("no JSON object")
        val obj = try { JSONObject(s.substring(a, b + 1)) } catch (e: Exception) { return OcrParsed.Bad("invalid JSON") }
        val arr = obj.opt("sentences") as? JSONArray ?: return OcrParsed.Bad("sentences is not an array")
        val out = ArrayList<String>()
        var total = 0
        var truncated = false
        for (i in 0 until arr.length()) {
            val item = arr.opt(i) as? String ?: continue
            val v = item.replace(SPACES, " ").trim()
            if (v.isEmpty() || v == out.lastOrNull()) continue
            if (out.size >= OcrPrompt.MAX_SENTENCES || total + v.length > OcrPrompt.MAX_CHARS) { truncated = true; break }
            out.add(v)
            total += v.length
        }
        return if (out.isEmpty()) OcrParsed.Empty else OcrParsed.Ok(out, truncated)
    }

    private fun isWordChar(cp: Int): Boolean {
        if (Character.isLetterOrDigit(cp)) return true
        val t = Character.getType(cp)
        return t == Character.NON_SPACING_MARK.toInt() || t == Character.COMBINING_SPACING_MARK.toInt()
    }

    fun strip(text: String): String {
        var a = 0
        var b = text.length
        while (a < b && !isWordChar(text.codePointAt(a))) a += Character.charCount(text.codePointAt(a))
        while (b > a && !isWordChar(text.codePointBefore(b))) b -= Character.charCount(text.codePointBefore(b))
        return text.substring(a, b)
    }

    fun tokens(sentence: String): List<Token> =
        sentence.split(SPACES).filter { it.isNotEmpty() }.map { Token(it, strip(it)) }

    fun phrase(tokens: List<Token>, from: Int, to: Int): String? {
        val a = minOf(from, to)
        val b = maxOf(from, to)
        if (a < 0 || b >= tokens.size) return null
        if (tokens[a].word.isEmpty() || tokens[b].word.isEmpty()) return null
        if (b - a + 1 > VocabText.MAX_WORDS) return null
        val s = (a..b).map { tokens[it].word }.filter { it.isNotEmpty() }.joinToString(" ")
        if (s.length > VocabText.MAX_LEN) return null
        return s
    }

    fun context(sentence: String, phrase: String): String {
        val max = EnrichPrompt.MAX_CONTEXT
        if (sentence.length <= max) return sentence
        val budget = max - 2 * ELLIPSIS.length
        val found = sentence.indexOf(phrase, ignoreCase = true)
        val idx = if (found < 0) 0 else found
        val plen = if (found < 0) 0 else phrase.length
        var start = maxOf(0, idx - (budget - plen) / 2)
        var end = minOf(sentence.length, start + budget)
        start = maxOf(0, end - budget)
        if (start > 0 && !sentence[start - 1].isWhitespace()) {
            val sp = sentence.indexOf(' ', start)
            if (sp in start until idx) start = sp + 1
        }
        if (end < sentence.length && !sentence[end].isWhitespace()) {
            val sp = sentence.lastIndexOf(' ', end - 1)
            if (sp >= idx + plen && sp > start) end = sp
        }
        val body = sentence.substring(start, end).trim()
        return (if (start > 0) ELLIPSIS else "") + body + (if (end < sentence.length) ELLIPSIS else "")
    }
}
