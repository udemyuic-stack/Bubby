package com.example.buddy.ui.vocab

class AiUnavailable(message: String) : Exception(message)

interface VocabAi {
    fun complete(system: String, user: String): String
}

class EntryRef(val id: Long, val original: String, val source: String, val target: String, val detected: String?, val status: EntryStatus)

interface EnrichStore {
    fun ref(id: Long): EntryRef?
    fun mark(id: Long, status: EntryStatus, error: String?)
    fun save(id: Long, data: Enriched)
    fun needLanguage(id: Long, guess: String?)
}

enum class EnrichOutcome { DONE, NEEDS_LANGUAGE, FAILED, UNAVAILABLE, SKIPPED }

class Enricher(private val ai: VocabAi, private val store: EnrichStore) {
    private companion object { val running = HashSet<Long>() }

    fun run(id: Long, force: Boolean = false, forcedLanguage: String? = null): EnrichOutcome {
        synchronized(running) { if (!running.add(id)) return EnrichOutcome.SKIPPED }
        try {
            val ref = store.ref(id) ?: return EnrichOutcome.SKIPPED
            if (!force && ref.status != EntryStatus.SAVED) return EnrichOutcome.SKIPPED
            val forced = forcedLanguage ?: if (force) ref.detected else null
            store.mark(id, EntryStatus.ENRICHING, null)
            val system = EnrichPrompt.system(ref.original, forced, ref.target)
            var error: String? = null
            for (attempt in 0..1) {
                val user = EnrichPrompt.user(ref.original) + (error?.let { "\n\nYour previous reply was rejected: $it. Reply again with valid JSON only." } ?: "")
                val raw = try {
                    ai.complete(system, user)
                } catch (e: Exception) {
                    store.mark(id, EntryStatus.SAVED, e.message ?: "AI is not available")
                    return EnrichOutcome.UNAVAILABLE
                }
                when (val p = EnrichValidator.parse(raw, ref.target, ref.original, forced)) {
                    is Parsed.Ok -> { store.save(id, p.data); return EnrichOutcome.DONE }
                    is Parsed.NeedsLanguage -> { store.needLanguage(id, p.detected); return EnrichOutcome.NEEDS_LANGUAGE }
                    is Parsed.Bad -> error = p.reason
                }
            }
            store.mark(id, EntryStatus.FAILED, error)
            return EnrichOutcome.FAILED
        } finally {
            synchronized(running) { running.remove(id) }
        }
    }
}
