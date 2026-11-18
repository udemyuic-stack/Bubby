package com.example.buddy

import com.example.buddy.ui.vocab.*
import org.json.JSONArray
import org.json.JSONObject

private fun tCard(sense: Long, entry: Long, word: String, source: String = "en") =
    Card(entry, sense, word, word, "", "", "verb", "def of $word", listOf("tr $word"), emptyList(), emptyMap(), source, "uz")

private fun tItem(sense: Long, entry: Long, p: Progress?) =
    Item(sense, entry, 1L, emptySet(), if (p == null) emptyMap() else mapOf(Dimension.RECOGNITION to p), setOf(Dimension.RECOGNITION))

private class FakeTutorAi(private val replies: List<Any>) : VocabAi {
    var calls = 0
    val users = ArrayList<String>()
    override fun complete(system: String, user: String): String {
        users.add(user)
        val r = replies[calls++]
        if (r is Exception) throw r
        return r as String
    }
}

private val TARGETS = listOf(
    TutorTarget(10, 100, listOf("study"), "study", "learn about a subject", listOf("o'qimoq")),
    TutorTarget(11, 101, listOf("carry out"), "carry out", "do a task", listOf("bajarmoq")),
    TutorTarget(12, 102, listOf("government"), "government", "the group that rules a country", listOf("hukumat"))
)

private const val FILLER = "The city council met again to discuss the long list of local issues that worried many families in the area. "

private fun tText(core: String, fillers: Int = 6) = core + " " + FILLER.repeat(fillers)

private val GOOD_TEXT = tText("Students studied the report while engineers carried out new tests for the government's plan.")

private fun tq(type: String, word: String, prompt: String, options: List<String>, answer: Any) =
    JSONObject().put("type", type).put("word", word).put("q", prompt).put("options", JSONArray(options)).put("answer", answer).put("why", "because")

private val GAP_STUDY = tq("gap", "study", "Many pupils ____ late at night.", listOf("study", "carry out", "govern"), 0)
private val GAP_CARRY = tq("gap", "carry out", "Engineers will ___ the tests.", listOf("study", "carry out", "rule"), 1)
private val READ_GOV = tq("read", "government", "Whose plan was tested?", listOf("the government", "the pupils", "the council"), 0)
private val READ_STUDY = tq("read", "study", "What did the students do?", listOf("read the report", "built tests", "met"), 0)
private val READ_CARRY = tq("read", "carry out", "Who did the tests?", listOf("engineers", "students", "families"), 0)
private val READ_GOV2 = tq("read", "government", "Who owns the plan?", listOf("the government", "the city", "nobody"), 0)

private fun tReply(text: String = GOOD_TEXT, kind: String = "passage", title: String = "Local plans", qs: List<JSONObject>) =
    "Here you go:\n" + JSONObject().put("kind", kind).put("title", title).put("text", text).put("questions", JSONArray(qs)).toString()

fun tutorTests() {
    val study = TutorMatch.forms("study")
    check("tutor forms study", "studies" in study && "studied" in study && "study" in study)
    check("tutor forms make", "making" in TutorMatch.forms("make"))
    check("tutor forms stop", "stopped" in TutorMatch.forms("stop") && "stopping" in TutorMatch.forms("stop"))
    check("tutor forms lie", "lying" in TutorMatch.forms("lie"))
    check("tutor forms go exact", TutorMatch.forms("go") == setOf("go"))
    check("tutor forms short exact", TutorMatch.forms("an") == setOf("an"))

    check("tutor find single", TutorMatch.find("Students study hard.", "study") == listOf(9..13))
    check("tutor find inflected", TutorMatch.find("She studied it.", "study") == listOf(4..10))
    check("tutor find multi", TutorMatch.find("They carried out the plan", "carry out") == listOf(5..15))
    check("tutor find multi last token", TutorMatch.find("Two rules of thumb apply", "rule of thumb") == listOf(4..17))
    check("tutor find possessive", TutorMatch.find("The government's plan", "government") == listOf(4..13))
    check("tutor find curly possessive", TutorMatch.find("The government’s plan", "government") == listOf(4..13))
    check("tutor find not inside word", TutorMatch.find("We start now", "art").isEmpty())
    check("tutor find multi gap", TutorMatch.find("They carried the box out", "carry out").isEmpty())

    val weakP = Progress(step = 1, attempts = 2)
    val cards = HashMap<Long, Card>()
    for (s in 1L..7L) cards[s] = tCard(s, if (s == 6L) 3L else s, "w$s", if (s == 7L) "uz" else "en")
    val items = listOf(
        tItem(1, 1, null),
        tItem(2, 2, Progress(step = 4, attempts = 5, due = 100)),
        tItem(3, 3, weakP),
        tItem(4, 4, Progress(step = 3, attempts = 4, illusion = true)),
        tItem(5, 5, Progress(step = 2, attempts = 5, lapses = 3)),
        tItem(6, 3, Progress(step = 0, attempts = 1)),
        tItem(7, 7, weakP)
    )
    val picked = TutorPick.weak(items, cards, null, 10).map { it.sense }
    check("tutor pick excludes new and strong", 1L !in picked && 2L !in picked, picked.toString())
    check("tutor pick order flagged first", picked == listOf(4L, 5L, 6L), picked.toString())
    check("tutor pick one sense per entry", 3L !in picked)
    check("tutor pick excludes non en", 7L !in picked)
    check("tutor pick allowed", TutorPick.weak(items, cards, setOf(5L), 10).map { it.sense } == listOf(5L))
    val many = (20L..35L).map { tItem(it, it, weakP) }
    val manyCards = (20L..35L).associateWith { tCard(it, it, "m$it") }
    check("tutor pick max", TutorPick.weak(many, manyCards, null, 10).size == TutorPick.MAX)
    check("tutor pick words", TutorPick.weak(items, cards, setOf(5L), 10).firstOrNull()?.shown == "w5")

    val ok = TutorValidator.parse(tReply(qs = listOf(GAP_STUDY, GAP_CARRY, READ_GOV)), TutorKind.PASSAGE, TARGETS)
    check("tutor parse ok", ok is TutorParsed.Ok && ok.lesson.questions.size == 3 && ok.lesson.hits.keys == setOf(10L, 11L, 12L) && ok.lesson.title == "Local plans")
    check("tutor parse gap normalised", ok is TutorParsed.Ok && ok.lesson.questions[1].prompt == "Engineers will ____ the tests." && ok.lesson.questions[1].sense == 11L)
    val missing = TutorValidator.parse(tReply(text = tText("Students studied while engineers carried out tests."), qs = listOf(GAP_STUDY, GAP_CARRY, READ_GOV)), TutorKind.PASSAGE, TARGETS)
    check("tutor parse missing", missing is TutorParsed.Bad && missing.reason == "missing" && missing.missing == listOf("government"))
    check("tutor parse wrong kind", TutorValidator.parse(tReply(kind = "dialogue", qs = listOf(GAP_STUDY, GAP_CARRY, READ_GOV)), TutorKind.PASSAGE, TARGETS) is TutorParsed.Bad)
    check("tutor parse too short", TutorValidator.parse(tReply(text = tText("Students studied while engineers carried out the government's tests.", 0), qs = listOf(GAP_STUDY, GAP_CARRY, READ_GOV)), TutorKind.PASSAGE, TARGETS) is TutorParsed.Bad)
    check("tutor parse passage needs title", TutorValidator.parse(tReply(title = "", qs = listOf(GAP_STUDY, GAP_CARRY, READ_GOV)), TutorKind.PASSAGE, TARGETS) is TutorParsed.Bad)
    check("tutor parse not json", TutorValidator.parse("sorry, no", TutorKind.PASSAGE, TARGETS) is TutorParsed.Bad)

    val badOnes = listOf(
        tq("read", "study", "Two options?", listOf("a", "b"), 0),
        tq("read", "study", "Duplicate?", listOf("Same", "same", "other"), 0),
        tq("read", "study", "Out of range?", listOf("a", "b", "c"), 5),
        tq("read", "nothing", "Not a target?", listOf("a", "b", "c"), 0),
        tq("quiz", "study", "Bad type?", listOf("a", "b", "c"), 0),
        tq("gap", "study", "Wrong answer word ____.", listOf("rule", "study", "carry out"), 0)
    )
    val filtered = TutorValidator.parse(tReply(qs = listOf(GAP_STUDY) + badOnes + listOf(GAP_CARRY, READ_GOV)), TutorKind.PASSAGE, TARGETS)
    check("tutor parse bad questions filtered", filtered is TutorParsed.Ok && filtered.lesson.questions.size == 3)
    val few = TutorValidator.parse(tReply(qs = listOf(GAP_STUDY, READ_GOV) + badOnes), TutorKind.PASSAGE, TARGETS)
    check("tutor parse fewer than 3 bad", few is TutorParsed.Bad && few.reason == "questions")
    val noBlank = tq("gap", "study", "Many pupils study late.", listOf("study", "carry out", "govern"), 0)
    val noBlankOk = TutorValidator.parse(tReply(qs = listOf(noBlank, GAP_CARRY, READ_GOV, READ_STUDY)), TutorKind.PASSAGE, TARGETS)
    check("tutor parse gap without blank dropped", noBlankOk is TutorParsed.Ok && noBlankOk.lesson.questions.none { it.type == "gap" && it.sense == 10L })
    check("tutor parse gap without blank bad", TutorValidator.parse(tReply(qs = listOf(noBlank, GAP_CARRY, READ_GOV)), TutorKind.PASSAGE, TARGETS) is TutorParsed.Bad)
    val six = TutorValidator.parse(tReply(qs = listOf(GAP_STUDY, GAP_CARRY, READ_GOV, READ_STUDY, READ_CARRY, READ_GOV2)), TutorKind.PASSAGE, TARGETS)
    check("tutor parse truncates to 5", six is TutorParsed.Ok && six.lesson.questions.size == TutorValidator.MAX_Q)
    val dialogue = TutorValidator.parse(tReply(kind = "dialogue", title = "", qs = listOf(GAP_STUDY, GAP_CARRY, READ_GOV)), TutorKind.DIALOGUE, TARGETS)
    check("tutor parse dialogue without title", dialogue is TutorParsed.Ok)

    check("tutor fix names missing", TutorPrompt.fix("missing", listOf("a", "b")).let { it.startsWith("Your previous reply was rejected: ") && it.contains("missing words: a, b") && it.endsWith("Reply again with valid JSON only.") })
    check("tutor system lists targets", TutorPrompt.system(TutorKind.DIALOGUE, TARGETS, "uz", "Environment").let { it.contains("\"carry out\"") && it.contains("Uzbek") && it.contains("Environment") && it.contains("B: ") })

    val good = tReply(qs = listOf(GAP_STUDY, GAP_CARRY, READ_GOV))
    val bad = tReply(text = tText("Nothing here."), qs = listOf(GAP_STUDY, GAP_CARRY, READ_GOV))
    val retry = FakeTutorAi(listOf(bad, good))
    val r1 = TutorGen.run(retry, TutorKind.PASSAGE, TARGETS, "uz", null)
    check("tutor gen retry succeeds", r1 is TutorParsed.Ok && retry.calls == 2 && retry.users[1].contains("rejected") && retry.users[1].contains("government"))
    val twice = FakeTutorAi(listOf(bad, bad))
    val r2 = TutorGen.run(twice, TutorKind.PASSAGE, TARGETS, "uz", null)
    check("tutor gen second bad", r2 is TutorParsed.Bad && twice.calls == 2)
    val down = FakeTutorAi(listOf(AiUnavailable("down")))
    val thrown = try { TutorGen.run(down, TutorKind.PASSAGE, TARGETS, "uz", null); false } catch (e: AiUnavailable) { e.message == "down" }
    check("tutor gen unavailable propagates", thrown)
}
