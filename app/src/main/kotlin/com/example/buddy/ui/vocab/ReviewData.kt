package com.example.buddy.ui.vocab

import android.content.Context

class ReviewData(val items: List<Item>, val cards: Map<Long, Card>, val words: Map<Long, List<String>>, val meanings: Map<Long, List<String>>)

object ReviewLoader {
    fun load(ctx: Context): ReviewData {
        val items = VocabDb.items(ctx, false)
        val cards = HashMap<Long, Card>()
        val words = HashMap<Long, MutableList<String>>()
        val meanings = HashMap<Long, MutableList<String>>()
        val details = HashMap<Long, EntryDetail?>()
        val pairs = VocabDb.pairs(ctx).associateBy { it.id }
        for (it in items) {
            val d = details.getOrPut(it.entry) { VocabDb.detail(ctx, it.entry) } ?: continue
            val pair = pairs[it.pair] ?: continue
            val row = d.senses.firstOrNull { s -> s.id == it.sense } ?: continue
            cards[it.sense] = Card(it.entry, it.sense, d.entry.original, d.lemma, d.ipa, d.reading, row.data.pos, row.data.definition, row.data.translations, row.data.examples, d.meta, d.entry.detected ?: pair.source, pair.target)
            words.getOrPut(it.pair) { ArrayList() }.add(d.entry.original)
            meanings.getOrPut(it.pair) { ArrayList() }.addAll(row.data.translations)
        }
        return ReviewData(items.filter { it.sense in cards }, cards, words, meanings)
    }

    fun dueCount(ctx: Context, day: Long): Int = Planner.plan(VocabDb.items(ctx, false), day, PlanOptions(activePair = VocabDb.activePair(ctx))).size
}
