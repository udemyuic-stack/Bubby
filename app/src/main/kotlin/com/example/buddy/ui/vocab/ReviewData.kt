package com.example.buddy.ui.vocab

import android.content.Context

class ReviewData(
    val items: List<Item>,
    val cards: Map<Long, Card>,
    val words: Map<Long, List<String>>,
    val meanings: Map<Long, List<String>>,
    val frames: Map<Long, List<Rel>> = emptyMap(),
    val synonyms: Map<Long, List<Rel>> = emptyMap(),
    val tts: Boolean = false
)

object ReviewLoader {
    fun load(ctx: Context): ReviewData {
        val tts = try { Pronounce.available(ctx) } catch (_: Exception) { false }
        val items = VocabDb.items(ctx, tts)
        val cards = HashMap<Long, Card>()
        val words = HashMap<Long, MutableList<String>>()
        val meanings = HashMap<Long, MutableList<String>>()
        val frames = HashMap<Long, MutableList<Rel>>()
        val synonyms = HashMap<Long, MutableList<Rel>>()
        val seen = HashSet<Pair<Long, Long>>()
        val details = HashMap<Long, EntryDetail?>()
        val pairs = VocabDb.pairs(ctx).associateBy { it.id }
        for (it in items) {
            val d = details.getOrPut(it.entry) { VocabDb.detail(ctx, it.entry) } ?: continue
            val pair = pairs[it.pair] ?: continue
            val row = d.senses.firstOrNull { s -> s.id == it.sense } ?: continue
            val card = Card(it.entry, it.sense, d.entry.original, d.lemma, d.ipa, d.reading, row.data.pos, row.data.definition, row.data.translations, row.data.examples, d.meta, d.entry.detected ?: pair.source, pair.target, d.relations)
            cards[it.sense] = card
            words.getOrPut(it.pair) { ArrayList() }.add(d.entry.original)
            meanings.getOrPut(it.pair) { ArrayList() }.addAll(row.data.translations)
            if (seen.add(it.pair to it.entry)) {
                val f = d.relations["collocation"].orEmpty().mapNotNull { c -> Exercises.frame(c, card.words())?.let { t -> Rel(it.entry, card.pos, t) } }
                frames.getOrPut(it.pair) { ArrayList() }.addAll(f.distinctBy { r -> r.text })
                val s = d.relations["synonym"].orEmpty().map { x -> x.trim() }.filter { x -> x.isNotEmpty() }.distinct().map { x -> Rel(it.entry, card.pos, x) }
                synonyms.getOrPut(it.pair) { ArrayList() }.addAll(s)
            }
        }
        return ReviewData(items.filter { it.sense in cards }, cards, words, meanings, frames, synonyms, tts)
    }

    fun dueCount(ctx: Context, day: Long): Int = Planner.plan(VocabDb.items(ctx, false), day, PlanOptions(activePair = VocabDb.activePair(ctx))).size
}
