package com.example.buddy

import com.example.buddy.ui.vocab.*

private fun card(source: String = "en", original: String = "reluctant", trans: List<String> = listOf("istamaydigan", "ikkilanayotgan"), examples: List<ExampleData> = listOf(ExampleData("She was reluctant to go.", "U ketgisi kelmadi.", "")), meta: Map<String, String> = emptyMap(), reading: String = "") =
    Card(1, 1, original, original, "", reading, "adjective", "not willing", trans, examples, meta, source, "uz")

fun exerciseTests() {
    val c = card()
    val wp = listOf("brave", "quick", "happy", "reluctant")
    val mp = listOf("jasur", "tez", "xursand", "istamaydigan")
    val cm = Exercises.build("choice_meaning", c, wp, mp, 1)!!
    check("ex choice meaning has 4 options with correct once", cm.options.size == 4 && cm.options.count { VocabText.normalize(it) == "istamaydigan" } == 1 && cm.ask == Ask.CHOICE)
    check("ex choice grades by option", Exercises.grade(cm, "istamaydigan") == Grade.CORRECT && Exercises.grade(cm, "jasur") == Grade.WRONG)
    check("ex choice deterministic by seed", Exercises.build("choice_meaning", c, wp, mp, 1)!!.options == cm.options && Exercises.build("choice_meaning", c, wp, mp, 2)!!.options.toSet() == cm.options.toSet())
    check("ex choice without distractors falls back to typed", Exercises.build("choice_meaning", c, emptyList(), listOf("istamaydigan"), 1)!!.ask == Ask.TYPED)
    check("ex choice word falls back to typed", Exercises.build("choice_word", c, listOf("reluctant"), mp, 1)!!.type == "type_word")
    check("ex no translation gives null", Exercises.build("recall_meaning", card(trans = emptyList()), wp, mp, 1) == null)

    val rm = Exercises.build("recall_meaning", c, wp, mp, 1)!!
    check("ex typed exact and any translation", Exercises.grade(rm, "Istamaydigan") == Grade.CORRECT && Exercises.grade(rm, "ikkilanayotgan") == Grade.CORRECT)
    check("ex typed typo is close", Exercises.grade(rm, "istamaydgan") == Grade.CLOSE)
    check("ex typed wrong and empty", Exercises.grade(rm, "tez") == Grade.WRONG && Exercises.grade(rm, "  ") == Grade.WRONG)
    val tw = Exercises.build("type_word", c, wp, mp, 1)!!
    check("ex short word no typo tolerance", Exercises.grade(Exercises.build("type_word", card(original = "cat", trans = listOf("mushuk")), wp, mp, 1)!!, "cet") == Grade.WRONG)
    check("ex type word accepts", Exercises.grade(tw, "reluctant") == Grade.CORRECT && Exercises.grade(tw, "reluctent") == Grade.CLOSE)
    check("ex accent difference is close", Exercises.grade(Exercises.build("type_word", card(original = "café", trans = listOf("kafe")), wp, mp, 1)!!, "cafe") == Grade.CLOSE)
    check("ex uzbek apostrophe variants equal", Exercises.grade(Exercises.build("recall_meaning", card(trans = listOf("o'zbek")), wp, mp, 1)!!, "oʻzbek") == Grade.CORRECT)
    check("ex cjk exact only", Exercises.grade(Exercises.build("type_word", card(source = "zh", original = "你好", trans = listOf("salom")), wp, mp, 1)!!, "你们") == Grade.WRONG)

    val cl = Exercises.build("cloze", c, wp, mp, 1)!!
    check("ex cloze blanks the word", cl.question == "She was ____ to go." && cl.context == "ex0" && Exercises.grade(cl, "reluctant") == Grade.CORRECT)
    check("ex cloze null without usable example", Exercises.build("cloze", card(examples = listOf(ExampleData("She hesitated.", "", ""))), wp, mp, 1) == null)
    check("ex cloze ignores word inside another word", Exercises.cloze("The reluctantly tone", listOf("reluctant")) == null)
    check("ex cloze cjk", Exercises.cloze("我喜欢你好吗", listOf("你好")) == "我喜欢____吗")

    val art = Exercises.build("lang_drill", card(source = "de", original = "Mädchen", meta = mapOf("article" to "das")), wp, mp, 1)!!
    check("ex de article drill", art.options == listOf("der", "die", "das") && Exercises.grade(art, "das") == Grade.CORRECT && Exercises.grade(art, "der") == Grade.WRONG)
    val rd = Exercises.build("lang_drill", card(source = "zh", original = "你好", reading = "nǐ hǎo"), wp, mp, 1)!!
    check("ex reading drill", Exercises.grade(rd, "nǐ hǎo") == Grade.CORRECT)
    check("ex lang drill null without data", Exercises.build("lang_drill", c, wp, mp, 1) == null)
    check("ex own sentence asks for sentence", Exercises.build("own_sentence", c, wp, mp, 1)!!.ask == Ask.SENTENCE)
    check("ex unknown type null", Exercises.build("zzz", c, wp, mp, 1) == null)

    check("judge parse ok", Judge.parse("{\"ok\":true,\"why\":\"Good.\"}").let { it != null && it.ok && it.why == "Good." })
    check("judge parse fenced", Judge.parse("```json\n{\"ok\":false,\"why\":\"Wrong tense\"}\n```")?.ok == false)
    check("judge rejects non boolean and garbage", Judge.parse("{\"ok\":\"yes\"}") == null && Judge.parse("nope") == null && Judge.parse("{\"why\":\"x\"}") == null)
    check("judge contains word", Judge.containsWord("I felt Reluctant today", c) && !Judge.containsWord("I felt fine", c))
    check("judge prompt names word and language", Judge.system(c, "ru").contains("reluctant") && Judge.system(c, "ru").contains("language code ru"))
    check("high confidence miss", Exercises.isHighConfidenceMiss(false, Confidence.SURE) && !Exercises.isHighConfidenceMiss(true, Confidence.SURE) && !Exercises.isHighConfidenceMiss(false, Confidence.GUESS))
}
