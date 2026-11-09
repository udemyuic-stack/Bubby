package com.example.buddy

import com.example.buddy.ui.vocab.*
import org.json.JSONObject

private const val PRON_SENSES = "\"senses\":[{\"pos\":\"noun\",\"definition\":\"the natural world\",\"translations\":[\"atrof-muhit\"],\"examples\":[]}]"

private fun pronReply(extra: String) = "{\"language\":\"en\",\"confidence\":0.95,\"lemma\":\"environment\",$PRON_SENSES,\"pronunciation\":{\"ipa\":\"ɪnˈvaɪrənmənt\",\"reading\":\"\"}$extra}"

private fun pj(s: String) = JSONObject(s)

fun pronTests() {
    check("pron match case and article", PronMatch.matches("Environment", "the environment"))
    check("pron match apostrophe", PronMatch.matches("don't", "dont"))
    check("pron match hyphen", PronMatch.matches("ice cream", "ice-cream"))
    check("pron match second hypothesis", PronMatch.matches("environment", "invite them|the environment"))
    check("pron match newline hypothesis", PronMatch.matches("environment", "invite\nenvironment"))
    check("pron no match longer word", !PronMatch.matches("environment", "environmental"))
    check("pron no match empty word", !PronMatch.matches("", "environment"))
    check("pron no match empty transcript", !PronMatch.matches("environment", ""))
    check("pron no match split tokens", !PronMatch.matches("ice cream", "ice and cream"))

    val full = PronParse.parse(pj("{\"uk\":\"/ɪnˈvaɪrənmənt/\",\"us\":\"[ɪnˈvaɪrənmənt]\",\"syllables\":[\"en\",\"vi\",\"ron\",\"ment\"],\"stress\":1,\"mistakes\":[\"Do not drop the n in -ron-\",\"Stress is on vi\"]}"), "en", "environment")
    check("pron parse full", full != null && full.uk == "ɪnˈvaɪrənmənt" && full.us == "ɪnˈvaɪrənmənt" && full.syllables == listOf("en", "vi", "ron", "ment") && full.stress == 1 && full.mistakes.size == 2)
    val badUk = PronParse.parse(pj("{\"uk\":\"<b>1</b>\",\"us\":\"ɪnˈvaɪrənmənt\"}"), "en", "environment")
    check("pron parse bad uk dropped us kept", badUk != null && badUk.uk.isEmpty() && badUk.us == "ɪnˈvaɪrənmənt")
    val echo = PronParse.parse(pj("{\"uk\":\"environment\",\"us\":\"ɪnˈvaɪrənmənt\"}"), "en", "environment")
    check("pron parse lemma echo dropped", echo != null && echo.uk.isEmpty())
    val badSyl = PronParse.parse(pj("{\"uk\":\"ɪnˈvaɪrənmənt\",\"syllables\":[\"en\",\"vi\",\"ment\"],\"stress\":1}"), "en", "environment")
    check("pron parse wrong syllables dropped", badSyl != null && badSyl.syllables.isEmpty() && badSyl.stress == -1 && badSyl.uk.isNotEmpty())
    val outRange = PronParse.parse(pj("{\"syllables\":[\"en\",\"vi\",\"ron\",\"ment\"],\"stress\":7}"), "en", "environment")
    check("pron parse stress out of range", outRange != null && outRange.syllables.size == 4 && outRange.stress == -1)
    val many = PronParse.parse(pj("{\"mistakes\":[\"a\",\"b\",\"c\",\"d\",\"a\"]}"), "en", "environment")
    check("pron parse mistakes capped", many != null && many.mistakes == listOf("a", "b", "c"))
    val dup = PronParse.parse(pj("{\"mistakes\":[\"a\",\"a\",\"b\"]}"), "en", "environment")
    check("pron parse mistakes deduped", dup != null && dup.mistakes == listOf("a", "b"))
    val phrase = PronParse.parse(pj("{\"syllables\":[\"ice\",\"cream\"],\"stress\":0}"), "en", "ice cream")
    check("pron parse phrase syllables", phrase != null && phrase.syllables.size == 2 && phrase.stress == 0)
    check("pron parse non english null", PronParse.parse(pj("{\"uk\":\"ɪnˈvaɪrənmənt\"}"), "de", "Umwelt") == null)
    check("pron parse missing null", PronParse.parse(null, "en", "environment") == null)
    check("pron parse all dropped null", PronParse.parse(pj("{\"uk\":\"1\",\"syllables\":[\"x\"]}"), "en", "environment") == null)

    val withPron = EnrichValidator.parse(pronReply(",\"pron_en\":{\"uk\":\"ɪnˈvaɪrənmənt\",\"us\":\"ɪnˈvaɪrənmənt\",\"syllables\":[\"en\",\"vi\",\"ron\",\"ment\"],\"stress\":1,\"mistakes\":[\"x\"]}"), "uz", "environment", "en")
    check("pron enrich parsed", withPron is Parsed.Ok && withPron.data.pron?.stress == 1 && withPron.data.ipa == "ɪnˈvaɪrənmənt")
    val without = EnrichValidator.parse(pronReply(""), "uz", "environment", "en")
    check("pron enrich reply without key", without is Parsed.Ok && without.data.pron == null && without.data.ipa == "ɪnˈvaɪrənmənt" && without.data.senses.size == 1)

    check("pron prompt english", EnrichPrompt.system("environment", "en", "uz").contains("\"pron_en\""))
    check("pron prompt detect", EnrichPrompt.system("environment", null, "uz").contains("\"pron_en\""))
    check("pron prompt other language", !EnrichPrompt.system("Umwelt", "de", "uz").contains("pron_en"))
}
