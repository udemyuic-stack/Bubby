package com.example.buddy.ui.vocab

class ExistingSense(val id: Long, val data: SenseData, val protected: Boolean, val hasHistory: Boolean)

class MergePlan(val delete: List<Long>, val update: List<Pair<Long, SenseData>>, val insert: List<SenseData>)

sealed class ContextPlan {
    object Keep : ContextPlan()
    object Insert : ContextPlan()
    class Update(val id: Long) : ContextPlan()
    class Delete(val id: Long) : ContextPlan()
}

object EnrichMerge {
    fun planContext(existing: ExistingSense?, incoming: SenseData?): ContextPlan = when {
        existing == null -> if (incoming != null) ContextPlan.Insert else ContextPlan.Keep
        existing.protected -> ContextPlan.Keep
        incoming != null -> ContextPlan.Update(existing.id)
        existing.hasHistory -> ContextPlan.Keep
        else -> ContextPlan.Delete(existing.id)
    }

    fun matches(a: SenseData, b: SenseData): Boolean {
        if (VocabText.normalize(a.pos) != VocabText.normalize(b.pos)) return false
        val da = VocabText.normalize(a.definition)
        if (da.isNotEmpty() && da == VocabText.normalize(b.definition)) return true
        val ta = a.translations.map { VocabText.normalize(it) }.filter { it.isNotEmpty() }.toSet()
        return b.translations.any { VocabText.normalize(it) in ta }
    }

    fun plan(existing: List<ExistingSense>, incoming: List<SenseData>): MergePlan {
        val delete = ArrayList<Long>()
        val update = ArrayList<Pair<Long, SenseData>>()
        val used = HashSet<Int>()
        for (e in existing.sortedByDescending { it.protected }) {
            val idx = incoming.indices.firstOrNull { it !in used && matches(e.data, incoming[it]) }
            if (idx != null) used.add(idx)
            when {
                e.protected -> {}
                idx != null -> update.add(e.id to incoming[idx])
                !e.hasHistory -> delete.add(e.id)
            }
        }
        val insert = incoming.indices.filter { it !in used }.map { incoming[it] }
        return MergePlan(delete, update, insert)
    }

    fun relationsToInsert(incoming: Map<String, List<String>>, user: Map<String, List<String>>): List<Pair<String, String>> {
        val taken = user.flatMap { (k, l) -> l.map { k to VocabText.normalize(it) } }.toHashSet()
        val out = ArrayList<Pair<String, String>>()
        for ((k, list) in incoming) for (t in list) {
            val key = k to VocabText.normalize(t)
            if (key.second.isEmpty() || !taken.add(key)) continue
            out.add(k to t)
        }
        return out
    }
}
