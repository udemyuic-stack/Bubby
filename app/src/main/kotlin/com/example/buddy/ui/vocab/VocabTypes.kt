package com.example.buddy.ui.vocab

enum class EntryStatus { SAVED, ENRICHING, READY, FAILED, NEEDS_LANGUAGE }

class ExampleData(val text: String, val translation: String, val reading: String)

class SenseData(
    val pos: String,
    val definition: String,
    val level: String,
    val usage: String,
    val formality: String,
    val translations: List<String>,
    val examples: List<ExampleData>
)

class ContextData(val translation: String, val sense: SenseData)

class Enriched(
    val language: String,
    val confidence: Double,
    val lemma: String,
    val senses: List<SenseData>,
    val ipa: String,
    val reading: String,
    val relations: Map<String, List<String>>,
    val meta: Map<String, String>,
    val context: ContextData? = null
)

object MetaSchema {
    val KEYS: Map<String, List<String>> = mapOf(
        "de" to listOf("article", "gender", "plural", "genitive", "cases", "verb_forms", "separable", "auxiliary"),
        "zh" to listOf("simplified", "traditional", "pinyin", "tones", "readings", "measure_word"),
        "ja" to listOf("kanji", "hiragana", "katakana", "furigana", "romaji", "readings", "verb_group"),
        "ko" to listOf("hangul", "pronunciation", "batchim", "forms", "honorific"),
        "ru" to listOf("stress", "gender", "aspect", "declension", "conjugation", "aspect_pair"),
        "uz" to listOf("latin", "cyrillic", "forms", "plural", "suffixes"),
        "en" to listOf("irregular_forms", "phrasal", "countable", "transitive", "plural")
    )

    val RELATIONS = listOf("synonym", "antonym", "collocation", "related", "confusable", "mistake")

    fun keep(lang: String, key: String): Boolean {
        if (key in (KEYS[lang] ?: emptyList())) return true
        return KEYS.values.none { key in it }
    }
}
