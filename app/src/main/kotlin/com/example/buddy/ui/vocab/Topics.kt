package com.example.buddy.ui.vocab

class TopicRow(val id: Long, val key: String?, val name: String?, val words: Int)

class TopicPreset(val key: String, val ru: String, val en: String, val uz: String)

object Topics {
    val PRESETS = listOf(
        TopicPreset("environment", "Окружающая среда", "Environment", "Atrof-muhit"),
        TopicPreset("education", "Образование", "Education", "Ta'lim"),
        TopicPreset("technology", "Технологии", "Technology", "Texnologiya"),
        TopicPreset("health", "Здоровье", "Health", "Sog'liq"),
        TopicPreset("work", "Работа", "Work", "Ish"),
        TopicPreset("crime", "Преступность", "Crime", "Jinoyat"),
        TopicPreset("media", "СМИ", "Media", "OAV"),
        TopicPreset("travel", "Путешествия", "Travel", "Sayohat"),
        TopicPreset("society", "Общество", "Society", "Jamiyat"),
        TopicPreset("science", "Наука", "Science", "Fan"),
        TopicPreset("economy", "Экономика", "Economy", "Iqtisodiyot"),
        TopicPreset("culture", "Культура", "Culture", "Madaniyat"),
        TopicPreset("government", "Государство", "Government", "Davlat"),
        TopicPreset("family", "Семья", "Family", "Oila"),
        TopicPreset("food", "Еда", "Food", "Oziq-ovqat"),
        TopicPreset("housing", "Жильё", "Housing", "Uy-joy"),
        TopicPreset("sport", "Спорт", "Sport", "Sport"),
        TopicPreset("art", "Искусство", "Art", "San'at")
    )

    fun name(key: String, lang: String): String {
        val p = PRESETS.firstOrNull { it.key == key } ?: return key
        return when (lang) { "en" -> p.en; "uz" -> p.uz; else -> p.ru }
    }

    fun display(row: TopicRow, lang: String): String = row.key?.let { name(it, lang) } ?: row.name ?: ""

    fun presetKeyFor(name: String): String? {
        val n = VocabText.normalize(name)
        return PRESETS.firstOrNull { p -> listOf(p.key, p.ru, p.en, p.uz).any { VocabText.normalize(it) == n } }?.key
    }
}
