package com.example.buddy

import com.example.buddy.ui.vocab.*

fun vocabTests() {
    check("vocab seven languages", Languages.ALL.map { it.code } == listOf("uz", "en", "ru", "zh", "ja", "ko", "de"))
    check("vocab every pair of seven valid", Languages.ALL.all { a -> Languages.ALL.all { b -> Languages.validPair(a.code, b.code) == (a.code != b.code) } })
    check("vocab unknown language invalid", !Languages.validPair("en", "fr") && !Languages.validPair("xx", "en"))
    check("vocab default target", Languages.defaultTarget("ru") == "ru" && Languages.defaultTarget("uz") == "uz" && Languages.defaultTarget("en") == "uz")
    check("vocab names", Languages.byCode("de")?.name("ru") == "Немецкий" && Languages.byCode("ko")?.native == "한국어")

    check("script en", Scripts.candidates("reluctant") == listOf("en", "de", "uz"))
    check("script ru", Scripts.candidates("нежелающий") == listOf("ru", "uz"))
    check("script zh", Scripts.candidates("你好") == listOf("zh", "ja"))
    check("script ja kana", Scripts.candidates("ありがとう") == listOf("ja") && Scripts.candidates("漢字かな") == listOf("ja"))
    check("script ko", Scripts.candidates("안녕하세요") == listOf("ko"))
    check("script de umlaut", "de" in Scripts.candidates("Mädchen"))
    check("script uz latin apostrophe", "uz" in Scripts.candidates("o'zbek"))
    check("script digits only no opinion", Scripts.candidates("123") == emptyList<String>() && Scripts.fits("123", "ja"))
    check("script fits", Scripts.fits("hello", "en") && !Scripts.fits("hello", "ja") && !Scripts.fits("привет", "en") && Scripts.fits("привет", "uz"))

    check("text nfc equal", VocabText.normalize("é") == VocabText.normalize("é"))
    check("text apostrophes unified", VocabText.normalize("oʻzbek") == "o'zbek" && VocabText.normalize("g’ish") == "g'ish")
    check("text spaces and case", VocabText.normalize("  Give   UP ") == "give up")
    check("text fold latin accents", VocabText.fold("Café") == "cafe" && VocabText.fold("Mädchen") == "madchen")
    check("text fold keeps pinyin base search", VocabText.fold("nǐ hǎo") == "ni hao")
    check("text fold keeps japanese dakuten", VocabText.fold("がっこう") == "がっこう" && VocabText.fold("かっこう") != VocabText.fold("がっこう"))
    check("text fold keeps hangul", VocabText.fold("안녕") == "안녕")
    check("text uz latin and cyrillic stay distinct", VocabText.normalize("ona") != VocabText.normalize("она"))

    val have = setOf("reluctant", "give up")
    check("rules ok", (VocabRules.check("  Serendipity ", have) as? AddCheck.Ok)?.original == "Serendipity")
    check("rules empty", VocabRules.check("   ", have) is AddCheck.Empty)
    check("rules duplicate case and spaces", VocabRules.check("Give   UP", have) is AddCheck.Duplicate)
    check("rules too long chars", VocabRules.check("x".repeat(81), have) is AddCheck.TooLong)
    check("rules too many words", VocabRules.check("a b c d e f g h i", have) is AddCheck.TooLong && VocabRules.check("a b c d e f g h", have) is AddCheck.Ok)
    check("rules cjk accepted", VocabRules.check("日本語", have) is AddCheck.Ok && VocabRules.check("안녕하세요", have) is AddCheck.Ok)
    check("rules search", VocabRules.matches(VocabText.fold("Café au lait"), VocabText.fold("CAFE")) && VocabRules.matches("x", "") && !VocabRules.matches("abc", "z"))
}
