package com.example.buddy

import com.example.buddy.ui.*
import org.json.JSONArray
import org.json.JSONObject
import kotlin.random.Random

private fun gChoose(text: String = "She ___ here since 2020.", options: List<String> = listOf("has lived", "lives", "lived", "is living"), answer: String = "has lived") =
    JSONObject().put("type", "choose").put("text", text).put("options", JSONArray(options)).put("answer", answer).put("why", "since")

private fun gMistake(text: String = "He don't like tea.", index: Int = 1, fix: String = "doesn't") =
    JSONObject().put("type", "mistake").put("text", text).put("index", index).put("fix", fix).put("why", "he")

private fun gOrder(text: String = "Where did you buy it?") = JSONObject().put("type", "order").put("text", text).put("why", "q")

private fun gType(text: String = "If I ___ (know), I would tell you.", answers: List<String> = listOf("knew")) =
    JSONObject().put("type", "type").put("text", text).put("answers", JSONArray(answers)).put("why", "2nd")

private fun gReply(vararg items: JSONObject) = "```json\n" + JSONObject().put("questions", JSONArray(items.toList())).toString() + "\n```"

private val GOOD = arrayOf(gChoose(), gChoose(), gChoose(), gMistake(), gOrder(), gType())

fun grammarTests() {
    val topics = GrammarBank.topics
    val all = GrammarBank.all()
    check("grammar 16 topics", topics.size == 16, topics.size.toString())
    check("grammar 160 questions", all.size == 160, all.size.toString())
    check("grammar 10 per topic", topics.all { it.questions.size == 10 }, topics.filter { it.questions.size != 10 }.joinToString { it.id })
    check("grammar mix per topic", topics.all { t -> t.questions.count { it.kind == GKind.CHOOSE } == 5 && t.questions.count { it.kind == GKind.MISTAKE } == 2 && t.questions.count { it.kind == GKind.ORDER } == 2 && t.questions.count { it.kind == GKind.TYPE } == 1 })
    check("grammar ids unique", all.map { it.id }.distinct().size == all.size)
    check("grammar levels", topics.all { it.level in GrammarAiText.levels })
    check("grammar rules filled", topics.all { listOf(it.ru, it.en, it.uz, it.ruleRu, it.ruleEn, it.ruleUz).all { s -> s.isNotBlank() } })
    check("grammar why filled", all.all { it.why.isNotBlank() })
    val badChoose = all.filter { it.kind == GKind.CHOOSE && ("___" !in it.text || it.options.size != 4 || it.options.distinct().size != 4 || it.answers.first() !in it.options) }
    check("grammar choose valid", badChoose.isEmpty(), badChoose.joinToString { it.id })
    val badMistake = all.filter { it.kind == GKind.MISTAKE && (it.wrongIndex !in it.words().indices || GrammarCheck.norm(it.words()[it.wrongIndex]) in it.answers.map { a -> GrammarCheck.norm(a) }) }
    check("grammar mistake valid", badMistake.isEmpty(), badMistake.joinToString { it.id })
    check("grammar order valid", all.filter { it.kind == GKind.ORDER }.all { it.words().size in 3..14 })
    check("grammar type valid", all.filter { it.kind == GKind.TYPE }.all { "___" in it.text && it.answers.isNotEmpty() })
    check("grammar text length", all.all { it.text.length <= GrammarAiText.MAX_TEXT })
    check("grammar byId", GrammarBank.byId("cond.0")?.text?.startsWith("If it rains") == true)

    val ch = all.first { it.kind == GKind.CHOOSE }
    check("grammar choose right", GrammarCheck.choose(ch, ch.answers.first()))
    check("grammar choose wrong", !GrammarCheck.choose(ch, ch.options.last()))
    val m = GrammarBank.byId("pres.5")!!
    check("grammar mistake right", GrammarCheck.mistake(m, 1))
    check("grammar mistake wrong", !GrammarCheck.mistake(m, 0))
    check("grammar mistake filled", m.filled() == "He doesn't like coffee.", m.filled())
    val o = GrammarBank.byId("pres.7")!!
    check("grammar order right", GrammarCheck.order(o, o.words()))
    check("grammar order wrong", !GrammarCheck.order(o, o.words().reversed()))
    val tiles = GrammarCheck.tiles(o, Random(3))
    check("grammar tiles shuffled", tiles != o.words() && tiles.sorted() == o.words().sorted())
    val ty = GrammarBank.byId("pres.9")!!
    check("grammar type exact", GrammarCheck.typed(ty, "doesn't eat"))
    check("grammar type case spaces curly", GrammarCheck.typed(ty, "  Doesn’t   EAT. "))
    check("grammar type alt answer", GrammarCheck.typed(ty, "does not eat"))
    check("grammar type wrong", !GrammarCheck.typed(ty, "don't eat"))
    check("grammar type empty", !GrammarCheck.typed(ty, "  "))
    check("grammar type filled", ty.filled() == "She doesn't eat meat. She is a vegetarian.", ty.filled())

    check("grammar stars", listOf(10, 9, 8, 7, 6, 5, 4, 0).map { GrammarStars.of(it) } == listOf(3, 3, 2, 2, 1, 1, 0, 0))
    val s = GrammarSession.topic(topics[0], Random(1))
    check("grammar session size", s.questions.size == 10)
    repeat(10) { i -> s.answer(i != 2 && i != 5); s.next() }
    check("grammar session score", s.done && s.score == 8 && s.stars == 2 && s.missed.size == 2 && s.bestStreak == 4, "${s.score} ${s.stars} ${s.bestStreak}")

    var weak = GrammarWeak.update(emptySet(), m, false)
    check("grammar weak add", weak.contains(m.id))
    weak = GrammarWeak.update(weak, o, false)
    check("grammar weak encode", GrammarWeak.decode(GrammarWeak.encode(weak)) == weak)
    check("grammar weak questions", GrammarWeak.questions(weak, Random(1)).map { it.id }.toSet() == setOf(m.id, o.id))
    weak = GrammarWeak.update(weak, m, true)
    check("grammar weak remove", !weak.contains(m.id) && weak.contains(o.id))
    check("grammar weak decode empty", GrammarWeak.decode("").isEmpty())

    val bp = GrammarBattle.phrases()
    check("grammar battle phrases", bp.size == 80 && bp.all { it.answer in it.pool && "___" in it.text })

    val good = GrammarAiText.parse(gReply(*GOOD))
    check("grammar ai good", good != null && good.questions.size == 6 && good.rejected == 0)
    check("grammar ai kinds", good?.questions?.map { it.kind }?.toSet() == GKind.values().toSet())
    check("grammar ai answer in options", good?.questions?.first()?.options?.first() == "has lived")
    check("grammar ai not json", GrammarAiText.parse("sorry") == null)
    check("grammar ai too few", GrammarAiText.parse(gReply(gChoose(), gChoose(), gOrder(), gType())) == null)
    val bads = listOf(
        "choose no gap" to gChoose(text = "She lives here."),
        "choose 3 options" to gChoose(options = listOf("has lived", "lives", "lived")),
        "choose dup options" to gChoose(options = listOf("has lived", "lives", "Lives", "lived")),
        "choose answer missing" to gChoose(answer = "had lived"),
        "mistake index" to gMistake(index = 9),
        "mistake same fix" to gMistake(fix = "don't"),
        "order short" to gOrder("Go home"),
        "order long" to gOrder("one two three four five six seven eight nine ten eleven twelve thirteen fourteen fifteen"),
        "type no gap" to gType(text = "If I knew, I would tell you."),
        "type no answers" to gType(answers = emptyList()),
        "text too long" to gChoose(text = "___ " + "word ".repeat(40)),
        "unknown type" to JSONObject().put("type", "essay").put("text", "Write ___ more.")
    )
    for ((name, bad) in bads) {
        val r = GrammarAiText.parse(gReply(*GOOD, bad))
        check("grammar ai rejects $name", r != null && r.questions.size == 6 && r.rejected == 1)
    }
    check("grammar ai prompt", GrammarAiText.user("Passive voice", "B1").contains("B1") && GrammarAiText.system().contains("\"questions\""))
}
