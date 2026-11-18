package com.example.buddy

import com.example.buddy.ui.vocab.*

private fun att(day: Long, ok: Boolean, c: Confidence = Confidence.SURE, type: String = "recall_meaning", ms: Int = 3000, ctx: String = "") = Attempt(day, type, ok, c, ms, ctx)

private fun item(id: Long, pair: Long = 1, names: Set<String> = setOf("w$id"), progress: Map<Dimension, Progress> = emptyMap(), dims: Set<Dimension> = setOf(Dimension.RECOGNITION, Dimension.MEANING_RECALL, Dimension.PRODUCTION, Dimension.CONTEXT)) =
    Item(id, id, pair, names, progress, dims)

fun learnTests() {
    var p = Progress()
    check("learn first sure correct goes to step 1 due next day", Scheduler.apply(p, att(10, true)).let { it.step == 1 && it.due == 11L })
    val days = ArrayList<Long>()
    var day = 10L
    p = Progress()
    repeat(5) { p = Scheduler.apply(p, att(day, true)); days.add(p.due - day); day = p.due }
    check("learn intervals grow 1 3 7 14 30 at ease 2.5 cap", days.first() == 1L && days.zipWithNext().all { it.second >= it.first } && days.last() >= 30L)
    check("learn step capped at 5", Scheduler.apply(p, att(day, true)).step == 5)

    val s3 = Progress(step = 3, ease = 2.0, due = 20, last = 13, attempts = 3)
    check("learn wrong drops two steps due tomorrow", Scheduler.apply(s3, att(20, false, Confidence.SOME)).let { it.step == 1 && it.due == 21L && it.lapses == 1 })
    check("learn wrong never below zero", Scheduler.apply(Progress(step = 1, attempts = 1, last = 1, due = 2), att(2, false)).step == 0)
    val sureWrong = Scheduler.apply(s3, att(20, false, Confidence.SURE))
    val unsureWrong = Scheduler.apply(s3, att(20, false, Confidence.SOME))
    check("learn confident wrong flags illusion and drops ease more", sureWrong.illusion && !unsureWrong.illusion && sureWrong.ease < unsureWrong.ease)
    check("learn guess correct stays", Scheduler.apply(s3, att(20, true, Confidence.GUESS)).step == 3)
    check("learn some confidence fast goes up slow stays", Scheduler.apply(s3, att(20, true, Confidence.SOME, ms = 2000)).step == 4 && Scheduler.apply(s3, att(20, true, Confidence.SOME, ms = 20000)).step == 3)
    check("learn ease clamped", Scheduler.fold((0 until 40).map { att(it.toLong(), false) }).ease == Scheduler.MIN_EASE && Scheduler.fold((0 until 40).map { att(it.toLong(), true) }).ease <= Scheduler.MAX_EASE)
    val lapsed = Progress(step = 3, ease = 2.0, lapses = 3)
    check("learn lapses shorten gap", Scheduler.gap(3, 2.0, 3) < Scheduler.gap(3, 2.0, 0))
    check("learn late correct earns ease", Scheduler.apply(Progress(step = 2, ease = 2.0, due = 10, last = 7, attempts = 2), att(25, true)).ease > Scheduler.apply(Progress(step = 2, ease = 2.0, due = 10, last = 7, attempts = 2), att(10, true)).ease)
    check("learn illusion clears after two sure correct", Scheduler.fold(listOf(att(1, false), att(2, true), att(4, true))).illusion == false && Scheduler.fold(listOf(att(1, false), att(2, true))).illusion)
    check("learn deterministic", Scheduler.fold(listOf(att(1, true), att(3, false), att(4, true))).let { a -> Scheduler.fold(listOf(att(1, true), att(3, false), att(4, true))).let { b -> a.step == b.step && a.due == b.due && a.ease == b.ease } })

    check("label new", Mastery.label(Progress()) == Label.NEW)
    check("label learning developing strong", Mastery.label(Progress(step = 1, attempts = 1)) == Label.LEARNING && Mastery.label(Progress(step = 2, attempts = 2)) == Label.DEVELOPING && Mastery.label(Progress(step = 4, attempts = 4)) == Label.STRONG)
    check("label step 5 alone is not well retained", Mastery.label(Progress(step = 5, attempts = 6)) == Label.STRONG)
    check("label well retained needs types and contexts", Mastery.label(Progress(step = 5, attempts = 8, longTypes = setOf("a", "b"), longContexts = setOf("x", "y"))) == Label.WELL_RETAINED && Mastery.label(Progress(step = 5, attempts = 8, longTypes = setOf("a"), longContexts = setOf("x", "y"))) == Label.STRONG)
    var long = Progress()
    val seq = listOf(att(0, true, type = "a", ctx = "c1"), att(1, true, type = "b", ctx = "c1"), att(4, true, type = "a", ctx = "c1"), att(11, true, type = "b", ctx = "c2"), att(25, true, type = "a", ctx = "c1"), att(45, true, type = "b", ctx = "c2"), att(75, true, type = "a", ctx = "c2"))
    long = Scheduler.fold(seq)
    check("label well retained reachable through long gaps", long.step == 5 && Mastery.label(long) == Label.WELL_RETAINED)
    check("label wrong after retained drops", Mastery.label(Scheduler.apply(long, att(200, false))) != Label.WELL_RETAINED)
    val strongRecognition = mapOf(Dimension.RECOGNITION to Progress(step = 5, attempts = 9, longTypes = setOf("a", "b"), longContexts = setOf("x", "y")))
    check("overall capped when production never tried", Mastery.overall(strongRecognition) == Label.DEVELOPING)
    check("overall weakest core dimension", Mastery.overall(mapOf(Dimension.RECOGNITION to Progress(step = 4, attempts = 4), Dimension.PRODUCTION to Progress(step = 1, attempts = 2))) == Label.LEARNING)
    check("overall none", Mastery.overall(emptyMap()) == Label.NEW)

    val fresh = (1L..10L).map { item(it) }
    val q1 = Planner.plan(fresh, 100, PlanOptions(activePair = 1))
    check("plan new limited to three", q1.size == 3 && q1.all { it.dim == Dimension.RECOGNITION })
    check("plan deterministic", Planner.plan(fresh, 100, PlanOptions(activePair = 1)).map { it.item.sense to it.type } == q1.map { it.item.sense to it.type })

    val due = (1L..14L).map { item(it, progress = mapOf(Dimension.RECOGNITION to Progress(step = 1, due = 50, last = 49, attempts = 1))) }
    val qd = Planner.plan(due, 60, PlanOptions(activePair = 1))
    check("plan caps session", qd.size <= 14)
    check("plan due items first", qd.take(9).all { it.item.progress[Dimension.RECOGNITION]!!.due <= 60 })
    check("plan fewer new when many due", qd.count { it.item.progress.values.none { p -> p.attempts > 0 } } == 0)

    val flagged = item(99, progress = mapOf(Dimension.RECOGNITION to Progress(step = 1, due = 59, last = 58, attempts = 1, illusion = true)))
    val plain = item(1, progress = mapOf(Dimension.RECOGNITION to Progress(step = 1, due = 10, last = 9, attempts = 1)))
    check("plan flagged illusion first", Planner.plan(listOf(plain, flagged), 60, PlanOptions(activePair = 1)).first().item.sense == 99L)

    val notDue = item(5, progress = mapOf(Dimension.RECOGNITION to Progress(step = 1, due = 200, last = 99, attempts = 1)))
    check("plan skips not due when no mix room", Planner.plan(listOf(notDue), 100, PlanOptions(activePair = 1, mixed = 0)).isEmpty())
    check("plan mixes other pair items", Planner.plan(listOf(item(7, pair = 2, progress = notDue.progress)), 100, PlanOptions(activePair = 1)).any { it.mixed })

    val ramp = item(1, progress = mapOf(Dimension.RECOGNITION to Progress(step = 3, due = 5, last = 4, attempts = 3), Dimension.MEANING_RECALL to Progress(step = 0, due = 5, last = 4, attempts = 1)))
    check("ramp locks later dimensions until earlier solid", Planner.unlocked(ramp) == listOf(Dimension.RECOGNITION, Dimension.MEANING_RECALL))
    val solid = item(1, progress = ExerciseTypesRamp())
    check("ramp unlocks all core when solid", Planner.unlocked(solid).containsAll(ExerciseTypes.RAMP))
    check("plan due picks weakest due dimension", Planner.plan(listOf(ramp), 10, PlanOptions(activePair = 1)).first().dim == Dimension.MEANING_RECALL)

    val many = (1L..8L).map { item(it, progress = mapOf(Dimension.RECOGNITION to Progress(step = 2, due = 5, last = 3, attempts = 2), Dimension.MEANING_RECALL to Progress(step = 2, due = 5, last = 3, attempts = 2), Dimension.PRODUCTION to Progress(step = 2, due = 5, last = 3, attempts = 2), Dimension.CONTEXT to Progress(step = 2, due = 5, last = 3, attempts = 2))) }
    val multi = Planner.plan(many, 20, PlanOptions(activePair = 1, aiOn = true))
    check("plan never repeats type twice in a row", multi.zipWithNext().all { it.first.type != it.second.type })
    val noAi = Planner.plan(many, 20, PlanOptions(activePair = 1, aiOn = false))
    check("plan no ai excludes ai types", noAi.none { it.type in ExerciseTypes.NEEDS_AI })

    val a = item(1, names = setOf("affect", "effect"), progress = notDue.progress)
    val b = item(2, names = setOf("effect", "affect"), progress = notDue.progress)
    val c = item(3, names = setOf("table"), progress = notDue.progress)
    val dueProg = mapOf(Dimension.RECOGNITION to Progress(step = 1, due = 1, last = 0, attempts = 1))
    val ordered = Planner.plan(listOf(item(1, names = setOf("affect", "effect"), progress = dueProg), item(2, names = setOf("effect"), progress = dueProg), item(3, names = setOf("table"), progress = dueProg)), 10, PlanOptions(activePair = 1))
    check("plan confusable words not adjacent", ordered.size == 3 && ordered.zipWithNext().none { (x, y) -> x.item.names.any { it in y.item.names } })

    val q = Drill(item(1), Dimension.MEANING_RECALL, "recall_meaning")
    val r = Planner.retry(q, false)
    check("retry uses another type and is marked", r != null && r.retry && r.type != q.type)
    val queue = mutableListOf(q, Drill(item(2), Dimension.RECOGNITION, "choice_meaning"))
    Planner.reinsert(queue, 0, r!!)
    check("retry reinserted later", queue.size == 3 && queue.last() === r)
}

private fun ExerciseTypesRamp(): Map<Dimension, Progress> = ExerciseTypes.RAMP.associateWith { Progress(step = 2, due = 5, last = 4, attempts = 2) }
