package com.example.buddy.ui.vocab

object EnrichPrompt {
    fun system(original: String, source: String?, target: String, context: String? = null): String {
        val tgt = Languages.byCode(target)?.en ?: target
        val langRule = if (source != null) {
            "The word is in ${Languages.byCode(source)?.en ?: source} (code $source). Use that as \"language\" with confidence 1."
        } else {
            "Detect the language of the word. It must be one of: " + Languages.ALL.joinToString(", ") { it.code + " (" + it.en + ")" } +
                ". If it is none of them or you are not sure, still answer, but put a confidence below 0.7. Uzbek in Cyrillic is uz."
        }
        val sentence = context?.trim()?.take(MAX_CONTEXT)?.replace("\"", "'")?.takeIf { it.isNotEmpty() }
        val contextRule = if (sentence == null) "" else
            "The learner met it in this sentence: \"$sentence\". Also return \"context\": {\"translation\": the sentence translated into $tgt, \"sense\": one sense object (same shape as in senses) for the meaning in THIS sentence, with the sentence itself as its first example}.\n"
        val metaRules = MetaSchema.KEYS.entries.joinToString("\n") { "- ${it.key}: " + it.value.joinToString(", ") }
        return "You are a careful lexicographer for a language-learning app. Reply with ONE JSON object and nothing else: no markdown, no comments.\n" +
            "Word or phrase to study: \"" + original.replace("\"", "'") + "\"\n" + langRule + "\n" + contextRule +
            "The learner reads $tgt (code $target). Write translations, definitions, usage notes and example translations in $tgt" +
            (if (target == "uz") " (Latin script)" else "") + ". Write example sentences in the language of the word.\n" +
            "Rules:\n" +
            "- 1 to 4 senses, most common first. Make separate senses only for truly different meanings. Each sense: at most 3 translations and 2 examples.\n" +
            "- Give the dictionary form in \"lemma\".\n" +
            "- Never invent pronunciation. Use an empty string when you are not sure. \"ipa\" only where IPA is normal; \"reading\" is pinyin with tone marks, kana, or a romanization.\n" +
            "- \"level\" is one of A1 A2 B1 B2 C1 C2 or an empty string.\n" +
            "- \"meta\" holds only the keys that fit the detected language and this word. Omit every key that does not apply. Allowed keys:\n" + metaRules + "\n" +
            "Format:\n" +
            "{\"language\":\"en\",\"confidence\":0.95,\"lemma\":\"\",\"senses\":[{\"pos\":\"\",\"definition\":\"\",\"level\":\"\",\"usage\":\"\",\"formality\":\"\",\"translations\":[\"\"],\"examples\":[{\"text\":\"\",\"translation\":\"\",\"reading\":\"\"}]}]," +
            "\"pronunciation\":{\"ipa\":\"\",\"reading\":\"\"},\"relations\":{\"synonym\":[],\"antonym\":[],\"collocation\":[],\"related\":[],\"confusable\":[],\"mistake\":[]},\"meta\":{}}"
    }

    fun user(original: String) = original

    const val MAX_CONTEXT = 300
}
