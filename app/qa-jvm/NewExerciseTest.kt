package com.example.buddy

import com.example.buddy.ui.vocab.*
import java.util.Random

private fun nc(original: String, pos: String = "noun", source: String = "en", examples: List<String> = emptyList(), relations: Map<String, List<String>> = emptyMap(), entry: Long = 1) =
    Card(entry, entry, original, original, "", "", pos, "", listOf("tr_$original"), examples.map { ExampleData(it, "tx: $it", "") }, emptyMap(), source, "uz", relations)

private fun ni(id: Long, progress: Map<Dimension, Progress>, dims: Set<Dimension> = ExerciseTypes.RAMP.toSet()) = Item(id, id, 1, setOf("n$id"), progress, dims)

fun newExerciseTests() {
    check("new frame whole word", Exercises.frame("make a decision", listOf("decision")) == "make a {}")
    check("new frame not inside word", Exercises.frame("indecision", listOf("decision")) == null)
    check("new frame longest first", Exercises.frame("take off quickly", listOf("take", "take off")) == "{} quickly")
    check("new frame case insensitive", Exercises.frame("Make A Decision", listOf("decision")) == "Make A {}")
    check("new mark wraps match", Exercises.mark("She made a Decision.", listOf("decision")) == "She made a «Decision».")
    check("new mark none", Exercises.mark("No match here", listOf("decision")) == null)
    check("new cloze unchanged", Exercises.cloze("She was reluctant to go.", listOf("reluctant")) == "She was ____ to go.")

    val t1 = listOf("alpha", "beta")
    val t2 = listOf("gamma", "delta", "epsilon", "zeta")
    val d1 = Exercises.distractors(listOf(t1, t2), emptyList(), 3, Random(5))
    check("new distractors tier order", d1.size == 3 && d1.take(2).toSet() == t1.toSet() && d1[2] in t2)
    check("new distractors deterministic", Exercises.distractors(listOf(t1, t2), emptyList(), 3, Random(5)) == d1)
    check("new distractors pool order free", (0L..10L).all { s -> Exercises.distractors(listOf(t2), emptyList(), 2, Random(s)) == Exercises.distractors(listOf(t2.reversed()), emptyList(), 2, Random(s)) })
    check("new distractors case variants order free", (0L..10L).all { s -> Exercises.distractors(listOf(listOf("Big", "big", "x")), emptyList(), 2, Random(s)) == Exercises.distractors(listOf(listOf("x", "big", "Big")), emptyList(), 2, Random(s)) })
    val d2 = Exercises.distractors(listOf(listOf("c", "d", " d ", "D", ""), listOf("C", "e", "d")), listOf("C"), 5, Random(1))
    check("new distractors exclude and dedupe", d2.none { VocabText.normalize(it) == "c" } && d2.map { VocabText.normalize(it) }.let { it.size == it.toSet().size } && d2.size == 2)

    val cols = listOf("make a decision", "reach a decision")
    val colCard = nc("decision", relations = mapOf("collocation" to cols, "mistake" to listOf("do a decision")))
    val frames = listOf(Rel(2, "noun", "take a {}"), Rel(3, "noun", "pay {}"), Rel(4, "verb", "{} quickly"), Rel(1, "noun", "own {}"))
    val col = Exercises.build("collocation", colCard, emptyList(), emptyList(), 3, frames)!!
    check("new collocation shape", col.dim == Dimension.CONTEXT && col.ask == Ask.CHOICE && col.options.size == 4 && col.shown in cols && col.shown in col.options)
    check("new collocation one listed option", (0L..20L).all { s -> Exercises.build("collocation", colCard, emptyList(), emptyList(), s, frames)!!.options.count { it in cols } == 1 })
    check("new collocation mistakes first", (0L..20L).all { s -> "do a decision" in Exercises.build("collocation", colCard, emptyList(), emptyList(), s, frames)!!.options })
    check("new collocation same pos before other", (0L..20L).all { s -> Exercises.build("collocation", colCard, emptyList(), emptyList(), s, frames)!!.options.let { "take a decision" in it && "pay decision" in it && "decision quickly" !in it } })
    check("new collocation skips own entry frames", (0L..20L).all { s -> "own decision" !in Exercises.build("collocation", colCard, emptyList(), emptyList(), s, frames)!!.options })
    check("new collocation null without collocations", Exercises.build("collocation", nc("decision"), emptyList(), emptyList(), 1, frames) == null)
    check("new collocation null with one distractor", Exercises.build("collocation", nc("decision", relations = mapOf("collocation" to cols)), emptyList(), emptyList(), 1, listOf(Rel(2, "noun", "take a {}"))) == null)
    check("new collocation also correct", Exercises.alsoCorrect(col) == cols.filter { it != col.shown })
    check("new grade collocation any listed", Exercises.grade(col, cols[0]) == Grade.CORRECT && Exercises.grade(col, "do a decision") == Grade.WRONG)

    val syns = listOf("large", "huge", "Big")
    val synCard = nc("big", pos = "adjective", examples = listOf("It is a big house.", "No word here."), relations = mapOf("synonym" to syns, "antonym" to listOf("small", "tiny"), "confusable" to listOf("bag")))
    val pool = listOf("big", "red", "fast", "huge")
    val sc = Exercises.build("synonym_context", synCard, pool, emptyList(), 2, synonyms = listOf(Rel(9, "adjective", "great")))!!
    check("new synonym marked question", sc.question == "It is a «big» house." && sc.context == "syn_ex0" && sc.prompt.startsWith("tx:"))
    check("new synonym correct listed", sc.shown in listOf("large", "huge") && sc.accepted == listOf("large", "huge"))
    check("new synonym antonyms preferred", (0L..20L).all { s -> Exercises.build("synonym_context", synCard, pool, emptyList(), s)!!.options.let { "small" in it && "tiny" in it && "bag" in it } })
    check("new synonym no word itself", (0L..20L).all { s -> Exercises.build("synonym_context", synCard, pool, emptyList(), s)!!.options.none { VocabText.normalize(it) == "big" } })
    check("new synonym one correct option", (0L..20L).all { s -> Exercises.build("synonym_context", synCard, pool, emptyList(), s)!!.options.count { it == "large" || it == "huge" } == 1 })
    check("new synonym null without synonyms", Exercises.build("synonym_context", nc("big", examples = listOf("It is big.")), pool, emptyList(), 1) == null)
    check("new synonym null without usable example", Exercises.build("synonym_context", nc("big", examples = listOf("No word here."), relations = mapOf("synonym" to syns)), pool, emptyList(), 1) == null)
    check("new synonym null with few distractors", Exercises.build("synonym_context", nc("big", examples = listOf("It is big."), relations = mapOf("synonym" to syns)), listOf("red"), emptyList(), 1) == null)

    val dictCard = nc("reluctant", examples = listOf("She was reluctant to go."))
    check("new dictation null for non english", Exercises.build("dictation", nc("Haus", source = "de", examples = listOf("Das Haus ist alt.")), emptyList(), emptyList(), 1) == null)
    val dicts = (0L..30L).map { Exercises.build("dictation", dictCard, emptyList(), emptyList(), it)!! }
    check("new dictation speaks and accepts words", dicts.all { it.speak.isNotBlank() && it.accepted == dictCard.words() && it.dim == Dimension.LISTENING && it.ask == Ask.TYPED })
    check("new dictation word variant", dicts.filter { it.context == "dict_word" }.let { w -> w.isNotEmpty() && w.all { it.question.isEmpty() && it.speak == "reluctant" } })
    check("new dictation example variant", dicts.filter { it.context == "dict_ex0" }.let { w -> w.isNotEmpty() && w.all { it.question == "She was ____ to go." && it.speak == "She was reluctant to go." } })
    check("new dictation without example is word", Exercises.build("dictation", nc("reluctant"), emptyList(), emptyList(), 1)!!.context == "dict_word")

    val dict = dicts.first()
    val tw = Exercises.build("type_word", dictCard, emptyList(), emptyList(), 1)!!
    check("new passes dictation close fails", Exercises.grade(dict, "reluctnt") == Grade.CLOSE && !Exercises.passes(dict, Grade.CLOSE) && Exercises.passes(dict, Grade.CORRECT))
    check("new passes type word close passes", Exercises.passes(tw, Grade.CLOSE) && !Exercises.passes(tw, Grade.WRONG))
    check("new also correct empty for typed", Exercises.alsoCorrect(tw).isEmpty())
    check("new supports", Exercises.supports("collocation", colCard, emptyList(), emptyList(), frames, emptyList()) && !Exercises.supports("collocation", nc("decision"), emptyList(), emptyList(), frames, emptyList()))

    check("new weak null", !Planner.weak(null))
    check("new weak untried", !Planner.weak(Progress(step = 0, attempts = 0)))
    check("new weak low step", Planner.weak(Progress(step = 1, attempts = 1)))
    check("new weak step two clean", !Planner.weak(Progress(step = 2, attempts = 2)))
    check("new weak step two lapse", Planner.weak(Progress(step = 2, attempts = 3, lapses = 1)))
    check("new weak lapses", Planner.weak(Progress(step = 4, attempts = 6, lapses = 2)))
    check("new weak illusion", Planner.weak(Progress(step = 4, attempts = 4, illusion = true)))
    check("new weak strong", !Planner.weak(Progress(step = 3, attempts = 3, lapses = 1)))

    val mixItem = ni(1, mapOf(Dimension.RECOGNITION to Progress(step = 2, due = 200, last = 99, attempts = 3, lapses = 2)))
    check("new weak dim before untried", Planner.unlocked(mixItem) == listOf(Dimension.RECOGNITION, Dimension.MEANING_RECALL) && Planner.plan(listOf(mixItem), 100, PlanOptions(activePair = 1)).first().dim == Dimension.RECOGNITION)

    val full = ExerciseTypes.RAMP.associateWith { Progress(step = 2, due = 5, last = 3, attempts = 2) }
    val many = (1L..8L).map { ni(it, full) }
    val noCloze: (Item, String) -> Boolean = { _, t -> t != "cloze" && t != "synonym_context" }
    val planned = Planner.plan(many, 20, PlanOptions(activePair = 1), can = noCloze)
    check("new can filters plan", planned.isNotEmpty() && planned.none { it.type == "cloze" || it.type == "synonym_context" })
    val onlyContext = ExerciseTypes.RAMP.associateWith { if (it == Dimension.CONTEXT) Progress(step = 0, due = 5, last = 3, attempts = 1) else Progress(step = 3, due = 50, last = 3, attempts = 3) }
    val ctxPlan = Planner.plan((1L..6L).map { ni(it, onlyContext) }, 20, PlanOptions(activePair = 1), can = noCloze)
    check("new can filters context plan", ctxPlan.isNotEmpty() && ctxPlan.none { it.type == "cloze" || it.type == "synonym_context" })
    check("new can skips dim without type", Planner.plan((1L..3L).map { ni(it, onlyContext) }, 20, PlanOptions(activePair = 1), can = { _, t -> ExerciseTypes.OF[Dimension.CONTEXT]?.contains(t) != true }).none { it.dim == Dimension.CONTEXT })
    val r = Planner.retry(Drill(many[0], Dimension.CONTEXT, "collocation"), false, can = noCloze)
    check("new can filters retry", r != null && r.type != "cloze" && r.type != "synonym_context" && r.type != "collocation")
    check("new can none gives no retry", Planner.retry(Drill(many[0], Dimension.CONTEXT, "collocation"), false, can = { _, _ -> false }) == null)

    val fresh = (1L..10L).map { ni(it, emptyMap()) }
    check("new default can same plan", Planner.plan(fresh, 100, PlanOptions(activePair = 1)).map { it.item.sense to it.type } == Planner.plan(fresh, 100, PlanOptions(activePair = 1), can = { _, _ -> true }).map { it.item.sense to it.type })
    check("new fresh still starts at recognition", Planner.plan(fresh, 100, PlanOptions(activePair = 1)).all { it.dim == Dimension.RECOGNITION })
}
