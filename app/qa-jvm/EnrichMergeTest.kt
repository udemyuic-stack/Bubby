package com.example.buddy

import com.example.buddy.ui.vocab.*

private fun sd(pos: String, def: String, vararg tr: String) = SenseData(pos, def, "", "", "", tr.toList(), emptyList())

fun enrichMergeTests() {
    val produce = sd("verb", "to produce or provide", "hosil qilmoq")
    val giveWay = sd("verb", "to give way to pressure", "yon bermoq")
    val noun = sd("noun", "an amount produced", "hosil")

    val a = EnrichMerge.plan(listOf(ExistingSense(1, produce, protected = true, hasHistory = false)), listOf(sd("verb", "to produce or provide", "ishlab chiqarmoq"), giveWay))
    check("merge protected kept and match dropped", a.delete.isEmpty() && a.update.isEmpty() && a.insert.size == 1 && a.insert[0] === giveWay)

    val fresh = sd("verb", "to produce or provide", "bermoq")
    val b = EnrichMerge.plan(listOf(ExistingSense(2, produce, protected = false, hasHistory = true)), listOf(fresh))
    check("merge history match updated in place", b.update.size == 1 && b.update[0].first == 2L && b.update[0].second === fresh && b.delete.isEmpty() && b.insert.isEmpty())

    val c = EnrichMerge.plan(listOf(ExistingSense(3, produce, protected = false, hasHistory = true)), listOf(giveWay))
    check("merge history no match kept", c.delete.isEmpty() && c.update.isEmpty() && c.insert == listOf(giveWay))

    val d = EnrichMerge.plan(listOf(ExistingSense(4, produce, protected = false, hasHistory = false)), listOf(giveWay))
    check("merge untouched no match deleted", d.delete == listOf(4L) && d.update.isEmpty() && d.insert == listOf(giveWay))

    val e = EnrichMerge.plan(emptyList(), listOf(produce, giveWay))
    check("merge unmatched inserted", e.insert == listOf(produce, giveWay) && e.delete.isEmpty())

    check("merge shared translation same pos", EnrichMerge.matches(sd("verb", "make", "Hosil qilmoq "), sd("verb", "create", "hosil qilmoq")))
    check("merge different pos no match", !EnrichMerge.matches(sd("noun", "to produce or provide", "hosil qilmoq"), produce) && !EnrichMerge.matches(noun, produce))

    val rel = EnrichMerge.relationsToInsert(mapOf("synonym" to listOf("Produce", "generate", ""), "antonym" to listOf("consume")), mapOf("synonym" to listOf("produce")))
    check("merge relations skip user duplicates", rel == listOf("synonym" to "generate", "antonym" to "consume"))
}

fun enrichMergeOrderTests() {
    val produce = SenseData("verb", "to produce or provide", "", "", "", listOf("hosil qilmoq"), emptyList())
    val p = EnrichMerge.plan(listOf(ExistingSense(1, produce, protected = false, hasHistory = false), ExistingSense(2, produce, protected = true, hasHistory = false)), listOf(produce))
    check("merge protected claims match first", p.update.isEmpty() && p.delete == listOf(1L) && p.insert.isEmpty())
}
