package com.example.buddy.ui.vocab

import android.content.Context
import com.example.buddy.Brain
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

class BrainVocabAi(ctx: Context) : VocabAi {
    private val brain = Brain(ctx.applicationContext)

    override fun complete(system: String, user: String): String {
        val ex = Executors.newSingleThreadExecutor()
        try {
            val f = ex.submit(Callable { brain.chatOnce(system, listOf("user" to user)) })
            val out = try { f.get(TIMEOUT_S, TimeUnit.SECONDS) } catch (e: TimeoutException) { f.cancel(true); throw AiUnavailable("AI timeout") }
            catch (e: java.util.concurrent.ExecutionException) { throw AiUnavailable(e.cause?.message ?: "AI error") }
            if (out.isBlank()) throw AiUnavailable("AI returned nothing")
            return out
        } finally {
            ex.shutdownNow()
        }
    }

    private companion object { const val TIMEOUT_S = 25L }
}

class DbEnrichStore(private val ctx: Context) : EnrichStore {
    override fun ref(id: Long): EntryRef? {
        val e = VocabDb.entry(ctx, id) ?: return null
        val p = VocabDb.pair(ctx, e.pair) ?: return null
        return EntryRef(e.id, e.original, p.source, p.target, e.detected, e.status, VocabDb.context(ctx, e.id))
    }
    override fun mark(id: Long, status: EntryStatus, error: String?) = VocabDb.setStatus(ctx, id, status, error)
    override fun save(id: Long, data: Enriched) = VocabDb.saveEnriched(ctx, id, data)
    override fun needLanguage(id: Long, guess: String?) {
        VocabDb.setDetected(ctx, id, guess)
        VocabDb.setStatus(ctx, id, EntryStatus.NEEDS_LANGUAGE, null)
    }
}

object VocabEnrich {
    private val workers = Executors.newFixedThreadPool(2)

    fun start(ctx: Context, id: Long, force: Boolean = false, language: String? = null, manual: Boolean = true, done: (EnrichOutcome) -> Unit = {}) {
        val app = ctx.applicationContext
        workers.execute {
            val out = runOnce(app, id, force, language, manual)
            EnrichJob.schedule(app)
            done(out)
        }
    }

    fun runOnce(app: Context, id: Long, force: Boolean, language: String?, manual: Boolean): EnrichOutcome {
        if (manual) VocabDb.resetTries(app, id)
        val out = try { Enricher(BrainVocabAi(app), DbEnrichStore(app)).run(id, force, language) } catch (e: Exception) {
            VocabDb.setStatus(app, id, EntryStatus.FAILED, e.message)
            EnrichOutcome.FAILED
        }
        val tries = VocabDb.entry(app, id)?.tries ?: return out
        when (val s = RetryPolicy.next(out, tries, System.currentTimeMillis())) {
            is RetryStep.Retry -> VocabDb.recordTry(app, id, s.tries, s.at)
            is RetryStep.GiveUp -> VocabDb.recordTry(app, id, s.tries, 0L)
            RetryStep.Done -> VocabDb.resetTries(app, id)
            RetryStep.Unchanged -> Unit
        }
        return out
    }

    fun resumePending(ctx: Context) {
        for (id in VocabDb.due(ctx, System.currentTimeMillis())) start(ctx, id, force = true, manual = false)
    }
}
