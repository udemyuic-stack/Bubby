package com.example.buddy.ui.vocab

class Lang(val code: String, val native: String, val ru: String, val en: String, val uz: String) {
    fun name(ui: String) = when (ui) { "en" -> en; "uz" -> uz; else -> ru }
}

object Languages {
    val ALL: List<Lang> = listOf(
        Lang("uz", "O'zbekcha", "Узбекский", "Uzbek", "O'zbek"),
        Lang("en", "English", "Английский", "English", "Ingliz"),
        Lang("ru", "Русский", "Русский", "Russian", "Rus"),
        Lang("zh", "中文", "Китайский", "Chinese", "Xitoy"),
        Lang("ja", "日本語", "Японский", "Japanese", "Yapon"),
        Lang("ko", "한국어", "Корейский", "Korean", "Koreys"),
        Lang("de", "Deutsch", "Немецкий", "German", "Nemis")
    )

    fun byCode(code: String): Lang? = ALL.firstOrNull { it.code == code }

    fun isSupported(code: String) = ALL.any { it.code == code }

    fun validPair(source: String, target: String) = source != target && isSupported(source) && isSupported(target)

    fun defaultTarget(uiLang: String) = if (uiLang == "en") "uz" else uiLang
}

object Scripts {
    fun candidates(text: String): List<String> {
        var kana = 0; var hangul = 0; var han = 0; var cyr = 0; var latin = 0
        var i = 0
        while (i < text.length) {
            val cp = text.codePointAt(i)
            i += Character.charCount(cp)
            when {
                cp in 0x3040..0x30FF || cp in 0x31F0..0x31FF || cp in 0xFF66..0xFF9F -> kana++
                cp in 0xAC00..0xD7AF || cp in 0x1100..0x11FF || cp in 0x3130..0x318F -> hangul++
                cp in 0x4E00..0x9FFF || cp in 0x3400..0x4DBF || cp in 0xF900..0xFAFF || cp in 0x20000..0x2A6DF -> han++
                cp in 0x0400..0x04FF -> cyr++
                Character.isLetter(cp) && cp < 0x0250 -> latin++
            }
        }
        return when {
            kana > 0 -> listOf("ja")
            hangul > 0 -> listOf("ko")
            han > 0 -> listOf("zh", "ja")
            cyr > 0 -> listOf("ru", "uz")
            latin > 0 -> listOf("en", "de", "uz")
            else -> emptyList()
        }
    }

    fun fits(text: String, source: String): Boolean {
        val c = candidates(text)
        return c.isEmpty() || source in c
    }
}
