package com.example.buddy

import com.example.buddy.ui.vocab.*

private class FakeAi(val replies: List<Any>) : VocabAi {
    var calls = 0
    val prompts = ArrayList<String>()
    override fun complete(system: String, user: String): String {
        prompts.add(user)
        val r = replies[minOf(calls, replies.size - 1)]
        calls++
        if (r is Exception) throw r
        return r as String
    }
}

private class FakeStore(var ref: EntryRef) : EnrichStore {
    var status = ref.status
    var error: String? = null
    var saved: Enriched? = null
    var guess: String? = null
    override fun ref(id: Long) = EntryRef(ref.id, ref.original, ref.source, ref.target, ref.detected, status)
    override fun mark(id: Long, status: EntryStatus, error: String?) { this.status = status; this.error = error }
    override fun save(id: Long, data: Enriched) { saved = data; status = EntryStatus.READY }
    override fun needLanguage(id: Long, guess: String?) { this.guess = guess; status = EntryStatus.NEEDS_LANGUAGE }
}

private fun good(lang: String, translation: String, extra: String = "") =
    "{\"language\":\"$lang\",\"confidence\":0.95,\"lemma\":\"x\",\"senses\":[{\"pos\":\"noun\",\"definition\":\"a thing\",\"level\":\"B1\",\"usage\":\"\",\"formality\":\"\"," +
        "\"translations\":[\"$translation\"],\"examples\":[{\"text\":\"Example.\",\"translation\":\"Misol.\",\"reading\":\"\"}]}],\"pronunciation\":{\"ipa\":\"\",\"reading\":\"\"},\"relations\":{\"synonym\":[\"a\"]},\"meta\":{$extra}}"

private fun ref(source: String = "en", target: String = "uz", orig: String = "ubiquitous") = EntryRef(1, orig, source, target, null, EntryStatus.SAVED)

fun enrichTests() {
    for (l in Languages.ALL) {
        val target = if (l.code == "uz") "en" else "uz"
        val r = EnrichValidator.parse(good(l.code, "tarjima"), target, "so'z", null)
        check("enrich ${l.code} good json accepted", r is Parsed.Ok && r.data.language == l.code && r.data.senses.size == 1)
    }

    check("enrich non json rejected", EnrichValidator.parse("sorry I cannot", "uz", "x", null) is Parsed.Bad)
    check("enrich json wrapped in fences accepted", EnrichValidator.parse("```json\n" + good("en", "t") + "\n```", "uz", "x", null) is Parsed.Ok)
    check("enrich no senses rejected", EnrichValidator.parse("{\"language\":\"en\",\"confidence\":0.9,\"senses\":[]}", "uz", "x", null) is Parsed.Bad)
    check("enrich sense without translation dropped", EnrichValidator.parse("{\"language\":\"en\",\"confidence\":0.9,\"senses\":[{\"definition\":\"d\",\"translations\":[]}]}", "uz", "x", null) is Parsed.Bad)
    check("enrich translation equal to input rejected", EnrichValidator.parse(good("en", "ubiquitous"), "uz", "ubiquitous", null) is Parsed.Bad)
    check("enrich proper noun keeps same translation", EnrichValidator.parse(good("en", "London").replace("noun", "proper noun"), "uz", "London", null) is Parsed.Ok)
    check("enrich low confidence asks language", EnrichValidator.parse(good("en", "t").replace("0.95", "0.4"), "uz", "x", null) is Parsed.NeedsLanguage)
    check("enrich unsupported language asks", EnrichValidator.parse(good("fr", "t"), "uz", "x", null).let { it is Parsed.NeedsLanguage && it.detected == null })
    check("enrich same as target asks", EnrichValidator.parse(good("uz", "t"), "uz", "x", null) is Parsed.NeedsLanguage)
    check("enrich forced language overrides low confidence", EnrichValidator.parse(good("fr", "t").replace("0.95", "0.1"), "uz", "x", "de").let { it is Parsed.Ok && it.data.language == "de" })
    val many = StringBuilder("{\"language\":\"en\",\"confidence\":1,\"senses\":[")
    for (i in 0 until 9) many.append(if (i > 0) "," else "").append("{\"definition\":\"d$i\",\"translations\":[\"a$i\",\"b\",\"c\",\"d\",\"e\",\"f\"]}")
    many.append("]}")
    val capped = EnrichValidator.parse(many.toString(), "uz", "x", null) as Parsed.Ok
    check("enrich senses and translations capped", capped.data.senses.size == 5 && capped.data.senses.all { it.translations.size <= 4 })
    check("enrich bad level dropped", (EnrichValidator.parse(good("en", "t").replace("B1", "Z9"), "uz", "x", null) as Parsed.Ok).data.senses[0].level == "")
    val de = EnrichValidator.parse(good("de", "qiz", "\"article\":\"das\",\"gender\":\"n\",\"pinyin\":\"x\",\"empty\":\"\""), "uz", "Mädchen", null) as Parsed.Ok
    check("enrich meta keeps only language keys", de.data.meta.keys.toSet() == setOf("article", "gender"))
    val zh = EnrichValidator.parse(good("zh", "salom", "\"pinyin\":\"nǐ hǎo\",\"tones\":\"3,3\""), "uz", "你好", null) as Parsed.Ok
    check("enrich zh meta", zh.data.meta["pinyin"] == "nǐ hǎo" && zh.data.meta["tones"] == "3,3")
    check("enrich relations kept", de.data.relations["synonym"] == listOf("a"))

    val prompt = EnrichPrompt.system("你好", null, "uz")
    check("enrich prompt lists all seven languages", Languages.ALL.all { prompt.contains("(" + it.en + ")") })
    check("enrich prompt knows target", prompt.contains("code uz"))
    check("enrich prompt with known source", EnrichPrompt.system("x", "ja", "en").contains("code ja"))
    check("enrich prompt escapes quotes", !EnrichPrompt.system("a\"b", null, "en").contains("\"a\"b\""))

    run {
        val ai = FakeAi(listOf(good("en", "hamma joyda")))
        val st = FakeStore(ref())
        val out = Enricher(ai, st).run(1)
        check("enricher success saves ready", out == EnrichOutcome.DONE && st.status == EntryStatus.READY && st.saved != null && ai.calls == 1)
    }
    run {
        val ai = FakeAi(listOf("garbage", good("en", "hamma joyda")))
        val st = FakeStore(ref())
        val out = Enricher(ai, st).run(1)
        check("enricher retries once with error text", out == EnrichOutcome.DONE && ai.calls == 2 && ai.prompts[1].contains("rejected"))
    }
    run {
        val ai = FakeAi(listOf("garbage", "still garbage"))
        val st = FakeStore(ref())
        val out = Enricher(ai, st).run(1)
        check("enricher fails after one retry", out == EnrichOutcome.FAILED && st.status == EntryStatus.FAILED && ai.calls == 2 && st.error != null && st.saved == null)
    }
    run {
        val ai = FakeAi(listOf(AiUnavailable("offline")))
        val st = FakeStore(ref())
        val out = Enricher(ai, st).run(1)
        check("enricher offline stays saved", out == EnrichOutcome.UNAVAILABLE && st.status == EntryStatus.SAVED && st.error == "offline" && ai.calls == 1)
    }
    run {
        val ai = FakeAi(listOf(good("fr", "t")))
        val st = FakeStore(ref())
        val out = Enricher(ai, st).run(1)
        check("enricher unknown language asks user", out == EnrichOutcome.NEEDS_LANGUAGE && st.status == EntryStatus.NEEDS_LANGUAGE && ai.calls == 1)
        val ai2 = FakeAi(listOf(good("de", "t")))
        val out2 = Enricher(ai2, st).run(1, force = true, forcedLanguage = "de")
        check("enricher rerun after user picks language", out2 == EnrichOutcome.DONE && st.saved?.language == "de")
    }
    run {
        val ai = FakeAi(listOf(good("en", "t")))
        val st = FakeStore(EntryRef(1, "x", "en", "uz", null, EntryStatus.READY))
        check("enricher skips ready entry without force", Enricher(ai, st).run(1) == EnrichOutcome.SKIPPED && ai.calls == 0)
        check("enricher regenerate with force", Enricher(ai, st).run(1, force = true) == EnrichOutcome.DONE && ai.calls == 1)
    }
    run {
        val ai = FakeAi(listOf(good("en", "t")))
        val st = FakeStore(ref())
        val slow = object : VocabAi {
            override fun complete(system: String, user: String): String {
                check("enricher blocks duplicate in flight", Enricher(ai, st).run(1) == EnrichOutcome.SKIPPED)
                return good("en", "t")
            }
        }
        Enricher(slow, st).run(1)
    }
}
