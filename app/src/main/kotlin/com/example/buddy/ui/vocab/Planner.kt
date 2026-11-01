package com.example.buddy.ui.vocab

import java.util.Random
import kotlin.math.max
import kotlin.math.min

class Item(
    val sense: Long,
    val entry: Long,
    val pair: Long,
    val names: Set<String>,
    val progress: Map<Dimension, Progress>,
    val dims: Set<Dimension>
)

class Drill(val item: Item, val dim: Dimension, val type: String, val retry: Boolean = false, val mixed: Boolean = false)

class PlanOptions(val aiOn: Boolean = false, val activePair: Long = 0, val newPerSession: Int = 3, val mixed: Int = 2, val cap: Int = 14, val dueLimit: Int = 9)

object Planner {
    fun unlocked(item: Item): List<Dimension> {
        val out = ArrayList<Dimension>()
        for (d in ExerciseTypes.RAMP) {
            if (d !in item.dims) continue
            out.add(d)
            val p = item.progress[d]
            if (p == null || p.attempts == 0 || p.step < 2) break
        }
        for (d in Dimension.values()) if (!d.core && d in item.dims && ExerciseTypes.RAMP.all { (item.progress[it]?.step ?: 0) >= 2 }) out.add(d)
        return out
    }

    fun typesFor(dim: Dimension, aiOn: Boolean): List<String> =
        (ExerciseTypes.OF[dim] ?: emptyList()).filter { aiOn || it !in ExerciseTypes.NEEDS_AI }

    fun dueDims(item: Item, day: Long): List<Dimension> =
        unlocked(item).filter { d -> val p = item.progress[d]; p != null && p.attempts > 0 && p.due <= day }

    fun flagged(item: Item) = item.progress.values.any { it.illusion }

    private fun weakest(item: Item, dims: List<Dimension>): Dimension? =
        dims.minWithOrNull(compareBy<Dimension> { item.progress[it]?.step ?: -1 }.thenBy { it.ordinal })

    private fun pickType(dim: Dimension, prev: String?, aiOn: Boolean, rnd: Random, history: List<String>): String? {
        val all = typesFor(dim, aiOn)
        if (all.isEmpty()) return null
        val fresh = all.filter { it != prev }
        val pool = if (fresh.isEmpty()) all else fresh
        val least = pool.minOf { t -> history.count { it == t } }
        val cands = pool.filter { t -> history.count { it == t } == least }
        return cands[rnd.nextInt(cands.size)]
    }

    fun plan(items: List<Item>, day: Long, opt: PlanOptions = PlanOptions(), seed: Long = day): List<Drill> {
        val rnd = Random(seed)
        val usable = items.filter { unlocked(it).isNotEmpty() }
        val due = usable.filter { dueDims(it, day).isNotEmpty() }
            .sortedWith(compareByDescending<Item> { flagged(it) }
                .thenByDescending { it.progress.values.filter { p -> p.attempts > 0 }.maxOfOrNull { p -> day - p.due } ?: 0 }
                .thenBy { it.sense })
            .take(opt.dueLimit)
        val dueIds = due.map { it.sense }.toSet()
        val newBudget = max(0, opt.newPerSession - max(0, due.size - 4) / 2)
        val fresh = usable.filter { it.progress.values.none { p -> p.attempts > 0 } && it.pair == opt.activePair }.sortedBy { it.sense }.take(newBudget)
        val taken = dueIds + fresh.map { it.sense }
        val mix = usable.filter { it.sense !in taken && it.progress.values.any { p -> p.attempts > 0 } && it.progress.values.none { p -> p.attempts > 0 && p.due <= day } }
            .sortedWith(compareBy<Item> { it.progress.values.filter { p -> p.attempts > 0 }.minOf { p -> Mastery.strength(p) } }.thenBy { it.pair == opt.activePair }.thenBy { it.sense })
            .take(opt.mixed)

        class Draft(val item: Item, val dim: Dimension, val mixed: Boolean)
        val drafts = ArrayList<Draft>()
        for (i in due) weakest(i, dueDims(i, day))?.let { drafts.add(Draft(i, it, false)) }
        for (i in fresh) weakest(i, unlocked(i))?.let { drafts.add(Draft(i, it, false)) }
        for (i in mix) weakest(i, unlocked(i))?.let { drafts.add(Draft(i, it, true)) }

        val ordered = interleave(drafts.map { Triple(it.item, it.dim, it.mixed) }, rnd)
        val out = ArrayList<Drill>()
        val history = ArrayList<String>()
        for ((item, dim, mixed) in ordered) {
            if (out.size >= opt.cap) break
            val prev = out.lastOrNull()?.type
            var d = dim
            var type = pickType(d, prev, opt.aiOn, rnd, history)
            if (type == null || type == prev) {
                val alt = unlocked(item).filter { it != d && typesFor(it, opt.aiOn).isNotEmpty() }
                val other = alt.firstNotNullOfOrNull { a -> pickType(a, prev, opt.aiOn, rnd, history)?.takeIf { it != prev }?.let { a to it } }
                if (other != null && (type == null || item.progress[other.first]?.let { it.attempts > 0 } == true || item.progress.values.none { it.attempts > 0 })) { d = other.first; type = other.second }
            }
            if (type == null) continue
            out.add(Drill(item, d, type, false, mixed))
            history.add(type)
        }
        return out
    }

    fun interleave(list: List<Triple<Item, Dimension, Boolean>>, rnd: Random): List<Triple<Item, Dimension, Boolean>> {
        val rest = ArrayList(list)
        val out = ArrayList<Triple<Item, Dimension, Boolean>>()
        while (rest.isNotEmpty()) {
            val prev = out.lastOrNull()
            fun linked(a: Item, b: Item) = a.names.any { it in b.names }
            val idx = rest.indexOfFirst { prev == null || !linked(it.first, prev.first) && it.first.pair != prev.first.pair }
                .takeIf { it >= 0 }
                ?: rest.indexOfFirst { prev == null || !linked(it.first, prev.first) }.takeIf { it >= 0 }
                ?: 0
            out.add(rest.removeAt(idx))
        }
        return out
    }

    fun retry(q: Drill, aiOn: Boolean, rnd: Random = Random(q.item.sense)): Drill? {
        val dims = unlocked(q.item).filter { it != q.dim }
        for (d in listOf(q.dim) + dims) {
            val types = typesFor(d, aiOn).filter { it != q.type }
            if (types.isNotEmpty()) return Drill(q.item, d, types[rnd.nextInt(types.size)], true)
        }
        return null
    }

    fun reinsert(queue: MutableList<Drill>, at: Int, q: Drill, minGap: Int = 3) {
        val pos = min(queue.size, at + minGap)
        queue.add(pos, q)
    }
}
