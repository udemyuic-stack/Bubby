package com.example.buddy.ui.vocab

import java.text.Normalizer
import java.util.Locale

object VocabText {
    const val MAX_LEN = 80
    const val MAX_WORDS = 8

    private val APOS = Regex("[ʻʼ’‘`´ʹ]")
    private val SPACES = Regex("\\s+")

    fun clean(s: String): String = Normalizer.normalize(s, Normalizer.Form.NFC).replace(APOS, "'").replace(SPACES, " ").trim()

    fun normalize(s: String): String = clean(s).lowercase(Locale.ROOT)

    fun fold(s: String): String {
        val d = Normalizer.normalize(normalize(s), Normalizer.Form.NFD)
        val out = StringBuilder()
        var base = 0
        var i = 0
        while (i < d.length) {
            val cp = d.codePointAt(i)
            i += Character.charCount(cp)
            if (Character.getType(cp).let { it == Character.NON_SPACING_MARK.toInt() || it == Character.COMBINING_SPACING_MARK.toInt() } && base < 0x0530) continue
            if (!Character.isWhitespace(cp)) base = cp
            out.appendCodePoint(cp)
        }
        return Normalizer.normalize(out.toString(), Normalizer.Form.NFC)
    }
}

sealed class AddCheck {
    class Ok(val original: String, val normalized: String, val folded: String) : AddCheck()
    object Empty : AddCheck()
    object TooLong : AddCheck()
    object Duplicate : AddCheck()
}

object VocabRules {
    fun check(input: String, existingNormalized: Set<String>): AddCheck {
        val original = VocabText.clean(input)
        if (original.isEmpty()) return AddCheck.Empty
        if (original.length > VocabText.MAX_LEN || original.split(' ').size > VocabText.MAX_WORDS) return AddCheck.TooLong
        val n = VocabText.normalize(original)
        if (n in existingNormalized) return AddCheck.Duplicate
        return AddCheck.Ok(original, n, VocabText.fold(original))
    }

    fun matches(folded: String, queryFolded: String) = queryFolded.isEmpty() || folded.contains(queryFolded)
}
