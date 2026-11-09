package com.example.buddy.ui.vocab

import android.content.Context
import com.example.buddy.Brain
import com.example.buddy.ui.isOnline
import java.util.concurrent.Executors

sealed class TutorResult {
    class Ok(val lesson: TutorLesson, val targets: List<TutorTarget>) : TutorResult()
    object NoAi : TutorResult()
    object Offline : TutorResult()
    class Unavailable(val message: String) : TutorResult()
    class Bad(val reason: String) : TutorResult()
}

class TutorSetup(val targets: List<TutorTarget>, val cards: Map<Long, Card>)

object Tutor {
    private val worker = Executors.newSingleThreadExecutor()

    fun load(ctx: Context, source: Long?, topic: Long?): TutorSetup {
        val pair = VocabDb.activePair(ctx)
        val data = ReviewLoader.load(ctx)
        val items = data.items.filter { it.pair == pair }
        val allowed = if (source == null && topic == null) null
            else VocabDb.entries(ctx, pair, "", Int.MAX_VALUE, source, topic).map { it.id }.toSet()
        val picked = TutorPick.weak(items, data.cards, allowed, VocabDb.today())
        return TutorSetup(picked, data.cards.filterKeys { k -> picked.any { it.sense == k } })
    }

    fun targets(ctx: Context, source: Long?, topic: Long?): List<TutorTarget> = load(ctx, source, topic).targets

    fun topicHint(ctx: Context, topic: Long?): String? {
        val id = topic ?: return null
        val row = VocabDb.topics(ctx).firstOrNull { it.id == id } ?: return null
        return row.key?.let { Topics.name(it, "en") } ?: row.name?.takeIf { it.isNotBlank() }
    }

    fun generate(ctx: Context, kind: TutorKind, targets: List<TutorTarget>, topic: String?, done: (TutorResult) -> Unit) {
        val app = ctx.applicationContext
        worker.execute {
            val r = try {
                when {
                    Brain(app).keyList().isEmpty() -> TutorResult.NoAi
                    !app.isOnline() -> TutorResult.Offline
                    else -> {
                        val explain = VocabDb.pair(app, VocabDb.activePair(app))?.target ?: "en"
                        when (val p = TutorGen.run(BrainVocabAi(app), kind, targets, explain, topic)) {
                            is TutorParsed.Ok -> TutorResult.Ok(p.lesson, targets)
                            is TutorParsed.Bad -> TutorResult.Bad(p.reason)
                        }
                    }
                }
            } catch (e: Exception) {
                TutorResult.Unavailable(e.message ?: "AI is not available")
            }
            done(r)
        }
    }

    fun answer(ctx: Context, q: TutorQuestion, chosen: Int, conf: Confidence, ms: Int): Boolean {
        val ok = chosen == q.answer
        val gap = q.type == "gap"
        val dim = if (gap) Dimension.CONTEXT else Dimension.RECOGNITION
        val type = if (gap) "tutor_gap" else "tutor_read"
        VocabDb.recordAttempt(ctx.applicationContext, q.sense, dim, Attempt(VocabDb.today(), type, ok, conf, ms.coerceAtLeast(0), "tutor"), q.options.getOrNull(chosen))
        return ok
    }
}
