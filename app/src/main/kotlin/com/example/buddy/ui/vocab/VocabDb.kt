package com.example.buddy.ui.vocab

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.example.buddy.Tr
import com.example.buddy.ui.Backend

class LangPair(val id: Long, val source: String, val target: String) {
    fun label() = source.uppercase() + " → " + target.uppercase()
}

class Entry(val id: Long, val pair: Long, val original: String, val status: EntryStatus, val added: Long, val detected: String? = null, val error: String? = null, val tries: Int = 0, val nextTry: Long = 0L)

class SenseRow(val id: Long, val data: SenseData, val picked: Boolean, val origin: String = "ai", val verified: Boolean = false, val canRestore: Boolean = false, val hasHistory: Boolean = false, val kind: String = "general")

class EntryDetail(val entry: Entry, val lemma: String, val ipa: String, val reading: String, val senses: List<SenseRow>, val relations: Map<String, List<String>>, val meta: Map<String, String>, val context: String? = null, val contextTr: String? = null, val pron: PronDetail? = null)

sealed class AddResult {
    class Added(val id: Long, val pair: Long) : AddResult()
    object Empty : AddResult()
    object TooLong : AddResult()
    class Duplicate(val id: Long) : AddResult()
    object NoPair : AddResult()
}

class VocabDb private constructor(ctx: Context) : SQLiteOpenHelper(ctx.applicationContext, "vocab.db", null, 8) {
    override fun onConfigure(db: SQLiteDatabase) {
        db.setForeignKeyConstraintsEnabled(true)
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE pair(id INTEGER PRIMARY KEY AUTOINCREMENT, source TEXT NOT NULL, target TEXT NOT NULL, created INTEGER NOT NULL, UNIQUE(source, target))")
        db.execSQL(
            "CREATE TABLE entry(id INTEGER PRIMARY KEY AUTOINCREMENT, pair INTEGER NOT NULL REFERENCES pair(id) ON DELETE CASCADE, " +
                "original TEXT NOT NULL, normalized TEXT NOT NULL, folded TEXT NOT NULL, detected TEXT, status TEXT NOT NULL, added INTEGER NOT NULL, error TEXT, " +
                "UNIQUE(pair, normalized))"
        )
        db.execSQL("CREATE INDEX entry_folded ON entry(pair, folded)")
        createDetail(db)
        createLearning(db)
        addEditMeta(db)
        addRetryMeta(db)
        addContextMeta(db)
        addSources(db)
        addPronDetail(db)
    }

    private fun addPronDetail(db: SQLiteDatabase) {
        db.execSQL("ALTER TABLE pron ADD COLUMN ipa_uk TEXT")
        db.execSQL("ALTER TABLE pron ADD COLUMN ipa_us TEXT")
        db.execSQL("ALTER TABLE pron ADD COLUMN syllables TEXT")
        db.execSQL("ALTER TABLE pron ADD COLUMN stress INTEGER NOT NULL DEFAULT -1")
        db.execSQL("ALTER TABLE pron ADD COLUMN mistakes TEXT")
    }

    private fun addSources(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE source(id INTEGER PRIMARY KEY AUTOINCREMENT, parent INTEGER REFERENCES source(id) ON DELETE CASCADE, kind TEXT NOT NULL, title TEXT NOT NULL, skill TEXT, created INTEGER NOT NULL)")
        db.execSQL("CREATE INDEX source_parent ON source(parent)")
        db.execSQL("CREATE TABLE entry_source(entry INTEGER NOT NULL REFERENCES entry(id) ON DELETE CASCADE, source INTEGER NOT NULL REFERENCES source(id) ON DELETE CASCADE, added INTEGER NOT NULL, PRIMARY KEY(entry, source))")
        db.execSQL("CREATE INDEX entry_source_source ON entry_source(source)")
        db.execSQL("CREATE TABLE topic(id INTEGER PRIMARY KEY AUTOINCREMENT, key TEXT UNIQUE, name TEXT)")
        db.execSQL("CREATE TABLE entry_topic(entry INTEGER NOT NULL REFERENCES entry(id) ON DELETE CASCADE, topic INTEGER NOT NULL REFERENCES topic(id) ON DELETE CASCADE, PRIMARY KEY(entry, topic))")
        db.execSQL("CREATE INDEX entry_topic_topic ON entry_topic(topic)")
        for (p in Topics.PRESETS) db.insert("topic", null, ContentValues().apply { put("key", p.key) })
    }

    private fun addContextMeta(db: SQLiteDatabase) {
        db.execSQL("ALTER TABLE entry ADD COLUMN context TEXT")
        db.execSQL("ALTER TABLE entry ADD COLUMN context_tr TEXT")
        db.execSQL("ALTER TABLE sense ADD COLUMN kind TEXT NOT NULL DEFAULT 'general'")
    }

    private fun addRetryMeta(db: SQLiteDatabase) {
        db.execSQL("ALTER TABLE entry ADD COLUMN tries INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE entry ADD COLUMN next_try INTEGER NOT NULL DEFAULT 0")
    }

    private fun addEditMeta(db: SQLiteDatabase) {
        db.execSQL("ALTER TABLE sense ADD COLUMN origin TEXT NOT NULL DEFAULT 'ai'")
        db.execSQL("ALTER TABLE sense ADD COLUMN verified INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE sense ADD COLUMN updated INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE sense ADD COLUMN ai_json TEXT")
        db.execSQL("ALTER TABLE relation ADD COLUMN origin TEXT NOT NULL DEFAULT 'ai'")
        db.execSQL("ALTER TABLE pron ADD COLUMN origin TEXT NOT NULL DEFAULT 'ai'")
    }

    private fun createLearning(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE progress(sense INTEGER NOT NULL REFERENCES sense(id) ON DELETE CASCADE, dimension TEXT NOT NULL, step INTEGER NOT NULL, ease REAL NOT NULL, due INTEGER NOT NULL, lapses INTEGER NOT NULL, last INTEGER NOT NULL, attempts INTEGER NOT NULL, illusion INTEGER NOT NULL, streak INTEGER NOT NULL, long_types TEXT NOT NULL, long_contexts TEXT NOT NULL, PRIMARY KEY(sense, dimension))")
        db.execSQL("CREATE INDEX progress_due ON progress(due)")
        db.execSQL("CREATE TABLE attempt(id INTEGER PRIMARY KEY AUTOINCREMENT, sense INTEGER NOT NULL REFERENCES sense(id) ON DELETE CASCADE, dimension TEXT NOT NULL, type TEXT NOT NULL, ok INTEGER NOT NULL, confidence TEXT NOT NULL, ms INTEGER NOT NULL, day INTEGER NOT NULL, context TEXT, answer TEXT)")
        db.execSQL("CREATE INDEX attempt_sense ON attempt(sense, dimension)")
        db.execSQL("CREATE TABLE session(id INTEGER PRIMARY KEY AUTOINCREMENT, day INTEGER NOT NULL, asked INTEGER NOT NULL, correct INTEGER NOT NULL, reflection TEXT)")
    }

    private fun createDetail(db: SQLiteDatabase) {
        db.execSQL("ALTER TABLE entry ADD COLUMN lemma TEXT")
        db.execSQL("CREATE TABLE sense(id INTEGER PRIMARY KEY AUTOINCREMENT, entry INTEGER NOT NULL REFERENCES entry(id) ON DELETE CASCADE, pos TEXT, definition TEXT NOT NULL, level TEXT, usage TEXT, formality TEXT, picked INTEGER NOT NULL DEFAULT 0)")
        db.execSQL("CREATE INDEX sense_entry ON sense(entry)")
        db.execSQL("CREATE TABLE translation(id INTEGER PRIMARY KEY AUTOINCREMENT, sense INTEGER NOT NULL REFERENCES sense(id) ON DELETE CASCADE, text TEXT NOT NULL)")
        db.execSQL("CREATE TABLE example(id INTEGER PRIMARY KEY AUTOINCREMENT, sense INTEGER NOT NULL REFERENCES sense(id) ON DELETE CASCADE, text TEXT NOT NULL, translation TEXT, reading TEXT)")
        db.execSQL("CREATE TABLE pron(entry INTEGER PRIMARY KEY REFERENCES entry(id) ON DELETE CASCADE, ipa TEXT, reading TEXT)")
        db.execSQL("CREATE TABLE meta(entry INTEGER NOT NULL REFERENCES entry(id) ON DELETE CASCADE, key TEXT NOT NULL, json TEXT NOT NULL, PRIMARY KEY(entry, key))")
        db.execSQL("CREATE TABLE relation(id INTEGER PRIMARY KEY AUTOINCREMENT, entry INTEGER NOT NULL REFERENCES entry(id) ON DELETE CASCADE, kind TEXT NOT NULL, text TEXT NOT NULL)")
        db.execSQL("CREATE INDEX relation_entry ON relation(entry)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) createDetail(db)
        if (oldVersion < 3) createLearning(db)
        if (oldVersion < 4) addEditMeta(db)
        if (oldVersion < 5) addRetryMeta(db)
        if (oldVersion < 6) addContextMeta(db)
        if (oldVersion < 7) addSources(db)
        if (oldVersion < 8) addPronDetail(db)
    }

    companion object {
        private const val ACTIVE = "vocab_pair"
        @Volatile private var inst: VocabDb? = null

        private fun get(ctx: Context): VocabDb = inst ?: synchronized(this) { inst ?: VocabDb(ctx).also { inst = it } }

        fun pairs(ctx: Context): List<LangPair> = get(ctx).readableDatabase.rawQuery("SELECT id, source, target FROM pair ORDER BY id", null).use {
            val out = ArrayList<LangPair>()
            while (it.moveToNext()) out.add(LangPair(it.getLong(0), it.getString(1), it.getString(2)))
            out
        }

        fun addPair(ctx: Context, source: String, target: String): Long {
            if (!Languages.validPair(source, target)) return 0L
            val db = get(ctx).writableDatabase
            val cv = ContentValues().apply { put("source", source); put("target", target); put("created", System.currentTimeMillis()) }
            db.insertWithOnConflict("pair", null, cv, SQLiteDatabase.CONFLICT_IGNORE)
            val id = db.rawQuery("SELECT id FROM pair WHERE source = ? AND target = ?", arrayOf(source, target)).use { if (it.moveToFirst()) it.getLong(0) else 0L }
            if (id != 0L) setActive(ctx, id)
            return id
        }

        fun deletePair(ctx: Context, id: Long) {
            get(ctx).writableDatabase.delete("pair", "id = ?", arrayOf(id.toString()))
            if (Backend.prefs(ctx).getLong(ACTIVE, 0L) == id) Backend.prefs(ctx).edit().remove(ACTIVE).apply()
        }

        fun activePair(ctx: Context): Long {
            val all = pairs(ctx)
            val saved = Backend.prefs(ctx).getLong(ACTIVE, 0L)
            return if (all.any { it.id == saved }) saved else all.firstOrNull()?.id ?: 0L
        }

        fun setActive(ctx: Context, id: Long) = Backend.prefs(ctx).edit().putLong(ACTIVE, id).apply()

        fun ensurePair(ctx: Context): Long {
            val a = activePair(ctx)
            if (a != 0L) return a
            return addPair(ctx, "en", Languages.defaultTarget(Tr.lang(ctx)))
        }

        fun count(ctx: Context, pair: Long): Int = get(ctx).readableDatabase.rawQuery("SELECT COUNT(*) FROM entry WHERE pair = ?", arrayOf(pair.toString())).use { if (it.moveToFirst()) it.getInt(0) else 0 }

        fun entries(ctx: Context, pair: Long, query: String = "", limit: Int = 200, source: Long? = null, topic: Long? = null): List<Entry> {
            val q = VocabText.fold(query)
            val args = ArrayList<String>()
            val pre = if (source == null) "" else {
                args.add(source.toString())
                "WITH RECURSIVE sub(id) AS (SELECT ? UNION SELECT s.id FROM source s JOIN sub ON s.parent = sub.id) "
            }
            args.add(pair.toString())
            val sb = StringBuilder(pre + "SELECT id, pair, original, status, added, detected, error, tries, next_try FROM entry WHERE pair = ?")
            if (q.isNotEmpty()) {
                sb.append(" AND folded LIKE ? ESCAPE '\\'")
                args.add("%" + q.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%")
            }
            if (source != null) sb.append(" AND id IN (SELECT entry FROM entry_source WHERE source IN (SELECT id FROM sub))")
            if (topic != null) { sb.append(" AND id IN (SELECT entry FROM entry_topic WHERE topic = ?)"); args.add(topic.toString()) }
            sb.append(" ORDER BY id DESC LIMIT $limit")
            return get(ctx).readableDatabase.rawQuery(sb.toString(), args.toTypedArray()).use {
                val out = ArrayList<Entry>()
                while (it.moveToNext()) out.add(Entry(it.getLong(0), it.getLong(1), it.getString(2), EntryStatus.valueOf(it.getString(3)), it.getLong(4), it.getString(5), it.getString(6), it.getInt(7), it.getLong(8)))
                out
            }
        }

        fun addEntry(ctx: Context, pair: Long, input: String, context: String? = null, source: Long? = null): AddResult {
            if (pair == 0L) return AddResult.NoPair
            val db = get(ctx).writableDatabase
            val existing = db.rawQuery("SELECT normalized FROM entry WHERE pair = ?", arrayOf(pair.toString())).use {
                val s = HashSet<String>()
                while (it.moveToNext()) s.add(it.getString(0))
                s
            }
            return when (val c = VocabRules.check(input, existing)) {
                is AddCheck.Empty -> AddResult.Empty
                is AddCheck.TooLong -> AddResult.TooLong
                is AddCheck.Duplicate -> AddResult.Duplicate(idByNormalized(db, pair, VocabText.normalize(input)))
                is AddCheck.Ok -> {
                    val cv = ContentValues().apply {
                        put("pair", pair); put("original", c.original); put("normalized", c.normalized); put("folded", c.folded)
                        put("status", EntryStatus.SAVED.name); put("added", System.currentTimeMillis())
                        cleanContext(context)?.let { s -> put("context", s) }
                    }
                    val id = db.insert("entry", null, cv)
                    if (id < 0) AddResult.Duplicate(idByNormalized(db, pair, c.normalized)) else {
                        if (source != null) linkSource(ctx, id, source)
                        AddResult.Added(id, pair)
                    }
                }
            }
        }

        fun entry(ctx: Context, id: Long): Entry? = get(ctx).readableDatabase.rawQuery("SELECT id, pair, original, status, added, detected, error, tries, next_try FROM entry WHERE id = ?", arrayOf(id.toString())).use {
            if (it.moveToFirst()) Entry(it.getLong(0), it.getLong(1), it.getString(2), EntryStatus.valueOf(it.getString(3)), it.getLong(4), it.getString(5), it.getString(6), it.getInt(7), it.getLong(8)) else null
        }

        fun pair(ctx: Context, id: Long): LangPair? = pairs(ctx).firstOrNull { it.id == id }

        private val retryable = "((status IN ('SAVED','ENRICHING') AND tries < ${RetryPolicy.MAX_UNAVAILABLE}) OR (status = 'FAILED' AND tries < ${RetryPolicy.MAX_FAILED}))"

        fun due(ctx: Context, now: Long, limit: Int = 20): List<Long> = get(ctx).readableDatabase.rawQuery("SELECT id FROM entry WHERE $retryable AND next_try <= ? ORDER BY id LIMIT $limit", arrayOf(now.toString())).use {
            val out = ArrayList<Long>()
            while (it.moveToNext()) out.add(it.getLong(0))
            out
        }

        fun nextDue(ctx: Context): Long? = get(ctx).readableDatabase.rawQuery("SELECT MIN(next_try) FROM entry WHERE $retryable", null).use {
            if (it.moveToFirst() && !it.isNull(0)) it.getLong(0) else null
        }

        private fun idByNormalized(db: SQLiteDatabase, pair: Long, normalized: String): Long =
            db.rawQuery("SELECT id FROM entry WHERE pair = ? AND normalized = ?", arrayOf(pair.toString(), normalized)).use { if (it.moveToFirst()) it.getLong(0) else 0L }

        private const val LAST_SOURCE = "vocab_last_source"

        fun lastSource(ctx: Context): Long? {
            val id = Backend.prefs(ctx).getLong(LAST_SOURCE, 0L)
            if (id == 0L) return null
            val ok = get(ctx).readableDatabase.rawQuery("SELECT 1 FROM source WHERE id = ?", arrayOf(id.toString())).use { it.moveToFirst() }
            return if (ok) id else null
        }

        fun setLastSource(ctx: Context, id: Long?) {
            val e = Backend.prefs(ctx).edit()
            if (id == null) e.remove(LAST_SOURCE) else e.putLong(LAST_SOURCE, id)
            e.apply()
        }

        fun sources(ctx: Context): List<SourceNode> = get(ctx).readableDatabase.rawQuery(
            "SELECT s.id, s.parent, s.kind, s.title, s.skill, (SELECT COUNT(*) FROM entry_source x WHERE x.source = s.id) FROM source s ORDER BY s.id", null
        ).use {
            val out = ArrayList<SourceNode>()
            while (it.moveToNext()) out.add(SourceNode(it.getLong(0), if (it.isNull(1)) null else it.getLong(1), it.getString(2), it.getString(3), it.getString(4), it.getInt(5)))
            out
        }

        fun addSource(ctx: Context, parent: Long?, kind: String, title: String, skill: String?): Long {
            val t = title.trim().take(80)
            if (t.isEmpty()) return 0L
            return get(ctx).writableDatabase.insert("source", null, ContentValues().apply {
                if (parent == null) putNull("parent") else put("parent", parent)
                put("kind", kind); put("title", t); if (skill == null) putNull("skill") else put("skill", skill); put("created", System.currentTimeMillis())
            })
        }

        fun renameSource(ctx: Context, id: Long, title: String) {
            val t = title.trim().take(80)
            if (t.isEmpty()) return
            get(ctx).writableDatabase.update("source", ContentValues().apply { put("title", t) }, "id = ?", arrayOf(id.toString()))
        }

        fun deleteSource(ctx: Context, id: Long) {
            get(ctx).writableDatabase.delete("source", "id = ?", arrayOf(id.toString()))
            if (lastSource(ctx) == null) setLastSource(ctx, null)
        }

        fun topics(ctx: Context): List<TopicRow> = get(ctx).readableDatabase.rawQuery(
            "SELECT t.id, t.key, t.name, (SELECT COUNT(*) FROM entry_topic x WHERE x.topic = t.id) FROM topic t ORDER BY t.key IS NULL, t.id", null
        ).use {
            val out = ArrayList<TopicRow>()
            while (it.moveToNext()) out.add(TopicRow(it.getLong(0), it.getString(1), it.getString(2), it.getInt(3)))
            out
        }

        fun addTopic(ctx: Context, name: String): Long {
            val n = name.trim().take(40)
            if (n.isEmpty()) return 0L
            val db = get(ctx).writableDatabase
            Topics.presetKeyFor(n)?.let { k -> db.rawQuery("SELECT id FROM topic WHERE key = ?", arrayOf(k)).use { if (it.moveToFirst()) return it.getLong(0) } }
            val norm = VocabText.normalize(n)
            db.rawQuery("SELECT id, name FROM topic WHERE key IS NULL", null).use { while (it.moveToNext()) if (VocabText.normalize(it.getString(1) ?: "") == norm) return it.getLong(0) }
            return db.insert("topic", null, ContentValues().apply { putNull("key"); put("name", n) })
        }

        fun deleteTopic(ctx: Context, id: Long) {
            get(ctx).writableDatabase.delete("topic", "id = ? AND key IS NULL", arrayOf(id.toString()))
        }

        fun linkSource(ctx: Context, entry: Long, source: Long) {
            get(ctx).writableDatabase.insertWithOnConflict("entry_source", null, ContentValues().apply { put("entry", entry); put("source", source); put("added", System.currentTimeMillis()) }, SQLiteDatabase.CONFLICT_IGNORE)
        }

        fun unlinkSource(ctx: Context, entry: Long, source: Long) {
            get(ctx).writableDatabase.delete("entry_source", "entry = ? AND source = ?", arrayOf(entry.toString(), source.toString()))
        }

        fun entrySources(ctx: Context, entry: Long): List<Long> = ids(ctx, "SELECT source FROM entry_source WHERE entry = ? ORDER BY added", entry)

        fun entryTopics(ctx: Context, entry: Long): List<Long> = ids(ctx, "SELECT topic FROM entry_topic WHERE entry = ? ORDER BY topic", entry)

        fun setTopics(ctx: Context, entry: Long, ids: Collection<Long>) {
            val db = get(ctx).writableDatabase
            db.beginTransaction()
            try {
                db.delete("entry_topic", "entry = ?", arrayOf(entry.toString()))
                for (t in ids.toSet()) db.insertWithOnConflict("entry_topic", null, ContentValues().apply { put("entry", entry); put("topic", t) }, SQLiteDatabase.CONFLICT_IGNORE)
                db.setTransactionSuccessful()
            } finally {
                db.endTransaction()
            }
        }

        private fun ids(ctx: Context, sql: String, arg: Long): List<Long> = get(ctx).readableDatabase.rawQuery(sql, arrayOf(arg.toString())).use {
            val out = ArrayList<Long>()
            while (it.moveToNext()) out.add(it.getLong(0))
            out
        }

        private fun cleanContext(s: String?): String? = s?.trim()?.take(EnrichPrompt.MAX_CONTEXT)?.takeIf { it.isNotEmpty() }

        fun context(ctx: Context, id: Long): String? = get(ctx).readableDatabase.rawQuery("SELECT context FROM entry WHERE id = ?", arrayOf(id.toString())).use { if (it.moveToFirst()) it.getString(0) else null }

        fun setContext(ctx: Context, id: Long, sentence: String?) {
            val s = cleanContext(sentence)
            val cv = ContentValues().apply {
                if (s == null) { putNull("context"); putNull("context_tr") } else put("context", s)
            }
            get(ctx).writableDatabase.update("entry", cv, "id = ?", arrayOf(id.toString()))
        }

        fun recordTry(ctx: Context, id: Long, tries: Int, nextTry: Long) {
            val cv = ContentValues().apply { put("tries", tries); put("next_try", nextTry) }
            get(ctx).writableDatabase.update("entry", cv, "id = ?", arrayOf(id.toString()))
        }

        fun resetTries(ctx: Context, id: Long) = recordTry(ctx, id, 0, 0L)

        fun setStatus(ctx: Context, id: Long, status: EntryStatus, error: String?) {
            val cv = ContentValues().apply { put("status", status.name); put("error", error) }
            get(ctx).writableDatabase.update("entry", cv, "id = ?", arrayOf(id.toString()))
        }

        fun setDetected(ctx: Context, id: Long, lang: String?) {
            val cv = ContentValues().apply { put("detected", lang) }
            get(ctx).writableDatabase.update("entry", cv, "id = ?", arrayOf(id.toString()))
        }

        fun saveEnriched(ctx: Context, id: Long, d: Enriched) {
            val db = get(ctx).writableDatabase
            db.beginTransaction()
            try {
                val arg = arrayOf(id.toString())
                val now = System.currentTimeMillis()
                val existing = existingSenses(db, id, "general")
                val plan = EnrichMerge.plan(existing, d.senses)
                for (sid in plan.delete) db.delete("sense", "id = ?", arrayOf(sid.toString()))
                for ((sid, sd) in plan.update) {
                    writeSense(db, sid, sd)
                    db.update("sense", ContentValues().apply { put("ai_json", senseJson(sd)); put("updated", now) }, "id = ?", arrayOf(sid.toString()))
                }
                for (sd in plan.insert) insertSense(db, id, sd, "ai", now)
                val incomingCtx = d.context
                when (val cp = EnrichMerge.planContext(existingSenses(db, id, "context").firstOrNull(), incomingCtx?.sense)) {
                    is ContextPlan.Insert -> insertSense(db, id, incomingCtx!!.sense, "ai", now, "context")
                    is ContextPlan.Update -> {
                        writeSense(db, cp.id, incomingCtx!!.sense)
                        db.update("sense", ContentValues().apply { put("ai_json", senseJson(incomingCtx.sense)); put("updated", now) }, "id = ?", arrayOf(cp.id.toString()))
                    }
                    is ContextPlan.Delete -> db.delete("sense", "id = ?", arrayOf(cp.id.toString()))
                    ContextPlan.Keep -> {}
                }
                if (incomingCtx != null) db.update("entry", ContentValues().apply { put("context_tr", incomingCtx.translation) }, "id = ?", arrayOf(id.toString()))
                val anyPicked = db.rawQuery("SELECT 1 FROM sense WHERE entry = ? AND picked = 1", arg).use { it.moveToFirst() }
                if (!anyPicked) db.execSQL("UPDATE sense SET picked = 1 WHERE id = (SELECT id FROM sense WHERE entry = ? AND kind = 'context' ORDER BY id LIMIT 1)", arrayOf<Any>(id))
                val pronOrigin = db.rawQuery("SELECT origin FROM pron WHERE entry = ?", arg).use { if (it.moveToFirst()) it.getString(0) else null }
                if (pronOrigin == null || pronOrigin == "ai") {
                    db.delete("pron", "entry = ?", arg)
                    db.insert("pron", null, ContentValues().apply { put("entry", id); put("ipa", d.ipa); put("reading", d.reading); put("origin", "ai"); putPron(this, d.pron) })
                } else d.pron?.let { p ->
                    val syl = p.syllables.takeIf { it.isNotEmpty() }?.joinToString(SYL_SEP)
                    db.execSQL(
                        "UPDATE pron SET ipa_uk = COALESCE(NULLIF(ipa_uk, ''), ?), ipa_us = COALESCE(NULLIF(ipa_us, ''), ?), " +
                            "stress = CASE WHEN NULLIF(syllables, '') IS NULL AND ? IS NOT NULL THEN ? ELSE stress END, " +
                            "syllables = COALESCE(NULLIF(syllables, ''), ?), mistakes = COALESCE(NULLIF(mistakes, ''), ?) WHERE entry = ?",
                        arrayOf<Any?>(p.uk.ifEmpty { null }, p.us.ifEmpty { null }, syl, p.stress, syl, mistakesJson(p.mistakes), id)
                    )
                }
                db.delete("meta", "entry = ?", arg)
                for ((k, v) in d.meta) db.insert("meta", null, ContentValues().apply { put("entry", id); put("key", k); put("json", v) })
                val user = LinkedHashMap<String, MutableList<String>>()
                db.rawQuery("SELECT kind, text FROM relation WHERE entry = ? AND origin != 'ai'", arg).use { while (it.moveToNext()) user.getOrPut(it.getString(0)) { ArrayList() }.add(it.getString(1)) }
                db.delete("relation", "entry = ? AND origin = 'ai'", arg)
                for ((k, t) in EnrichMerge.relationsToInsert(d.relations, user)) db.insert("relation", null, ContentValues().apply { put("entry", id); put("kind", k); put("text", t); put("origin", "ai") })
                db.update("entry", ContentValues().apply { put("status", EntryStatus.READY.name); put("detected", d.language); put("lemma", d.lemma); putNull("error") }, "id = ?", arrayOf(id.toString()))
                db.setTransactionSuccessful()
            } finally {
                db.endTransaction()
            }
        }

        private fun existingSenses(db: SQLiteDatabase, entry: Long, kind: String): List<ExistingSense> {
            val out = ArrayList<ExistingSense>()
            db.rawQuery(
                "SELECT s.id, s.origin, s.verified, EXISTS(SELECT 1 FROM progress p WHERE p.sense = s.id) FROM sense s WHERE s.entry = ? AND s.kind = ? ORDER BY s.id",
                arrayOf(entry.toString(), kind)
            ).use { c ->
                while (c.moveToNext()) {
                    val sid = c.getLong(0)
                    out.add(ExistingSense(sid, readSense(db, sid) ?: continue, c.getString(1) != "ai" || c.getInt(2) == 1, c.getInt(3) == 1))
                }
            }
            return out
        }

        private fun readSense(db: SQLiteDatabase, sid: Long): SenseData? {
            val arg = arrayOf(sid.toString())
            val tr = db.rawQuery("SELECT text FROM translation WHERE sense = ? ORDER BY id", arg).use { q -> val l = ArrayList<String>(); while (q.moveToNext()) l.add(q.getString(0)); l }
            val ex = db.rawQuery("SELECT text, translation, reading FROM example WHERE sense = ? ORDER BY id", arg).use { q -> val l = ArrayList<ExampleData>(); while (q.moveToNext()) l.add(ExampleData(q.getString(0), q.getString(1) ?: "", q.getString(2) ?: "")); l }
            return db.rawQuery("SELECT pos, definition, level, usage, formality FROM sense WHERE id = ?", arg).use { c ->
                if (c.moveToFirst()) SenseData(c.getString(0) ?: "", c.getString(1), c.getString(2) ?: "", c.getString(3) ?: "", c.getString(4) ?: "", tr, ex) else null
            }
        }

        private fun writeSense(db: SQLiteDatabase, sid: Long, sd: SenseData) {
            val arg = arrayOf(sid.toString())
            db.update("sense", ContentValues().apply {
                put("pos", sd.pos); put("definition", sd.definition); put("level", sd.level); put("usage", sd.usage); put("formality", sd.formality)
            }, "id = ?", arg)
            db.delete("translation", "sense = ?", arg)
            db.delete("example", "sense = ?", arg)
            for (t in sd.translations) db.insert("translation", null, ContentValues().apply { put("sense", sid); put("text", t) })
            for (e in sd.examples) db.insert("example", null, ContentValues().apply { put("sense", sid); put("text", e.text); put("translation", e.translation); put("reading", e.reading) })
        }

        private fun insertSense(db: SQLiteDatabase, entry: Long, sd: SenseData, origin: String, now: Long, kind: String = "general"): Long {
            val sid = db.insert("sense", null, ContentValues().apply {
                put("entry", entry); put("pos", sd.pos); put("definition", sd.definition); put("level", sd.level)
                put("usage", sd.usage); put("formality", sd.formality); put("origin", origin); put("updated", now); put("kind", kind)
                if (origin == "ai") put("ai_json", senseJson(sd)) else putNull("ai_json")
            })
            for (t in sd.translations) db.insert("translation", null, ContentValues().apply { put("sense", sid); put("text", t) })
            for (e in sd.examples) db.insert("example", null, ContentValues().apply { put("sense", sid); put("text", e.text); put("translation", e.translation); put("reading", e.reading) })
            return sid
        }

        private fun senseJson(sd: SenseData): String {
            val o = org.json.JSONObject()
            o.put("pos", sd.pos).put("definition", sd.definition).put("level", sd.level).put("usage", sd.usage).put("formality", sd.formality)
            o.put("translations", org.json.JSONArray(sd.translations))
            val ex = org.json.JSONArray()
            for (e in sd.examples) ex.put(org.json.JSONObject().put("text", e.text).put("translation", e.translation).put("reading", e.reading))
            o.put("examples", ex)
            return o.toString()
        }

        private fun senseFromJson(s: String): SenseData? = try {
            val o = org.json.JSONObject(s)
            val tr = o.optJSONArray("translations")
            val ex = o.optJSONArray("examples")
            SenseData(
                o.optString("pos"), o.optString("definition"), o.optString("level"), o.optString("usage"), o.optString("formality"),
                (0 until (tr?.length() ?: 0)).map { tr!!.getString(it) },
                (0 until (ex?.length() ?: 0)).map { val x = ex!!.getJSONObject(it); ExampleData(x.optString("text"), x.optString("translation"), x.optString("reading")) }
            )
        } catch (_: org.json.JSONException) { null }

        fun <R> inTransaction(ctx: Context, block: () -> R): R {
            val db = get(ctx).writableDatabase
            db.beginTransaction()
            try {
                val r = block()
                db.setTransactionSuccessful()
                return r
            } finally {
                db.endTransaction()
            }
        }

        fun updateSense(ctx: Context, senseId: Long, sd: SenseData) {
            val db = get(ctx).writableDatabase
            db.beginTransaction()
            try {
                writeSense(db, senseId, sd)
                db.execSQL("UPDATE sense SET origin = CASE WHEN origin = 'ai' THEN 'edited' ELSE origin END, updated = ? WHERE id = ?", arrayOf<Any>(System.currentTimeMillis(), senseId))
                db.setTransactionSuccessful()
            } finally {
                db.endTransaction()
            }
        }

        fun addSense(ctx: Context, entryId: Long, sd: SenseData): Long {
            val db = get(ctx).writableDatabase
            db.beginTransaction()
            try {
                val sid = insertSense(db, entryId, sd, "user", System.currentTimeMillis())
                db.setTransactionSuccessful()
                return sid
            } finally {
                db.endTransaction()
            }
        }

        fun deleteSense(ctx: Context, senseId: Long) {
            get(ctx).writableDatabase.delete("sense", "id = ?", arrayOf(senseId.toString()))
        }

        fun setVerified(ctx: Context, senseId: Long, verified: Boolean) {
            get(ctx).writableDatabase.update("sense", ContentValues().apply { put("verified", if (verified) 1 else 0); put("updated", System.currentTimeMillis()) }, "id = ?", arrayOf(senseId.toString()))
        }

        fun restoreAi(ctx: Context, senseId: Long): Boolean {
            val db = get(ctx).writableDatabase
            val raw = db.rawQuery("SELECT ai_json FROM sense WHERE id = ?", arrayOf(senseId.toString())).use { if (it.moveToFirst()) it.getString(0) else null } ?: return false
            val sd = senseFromJson(raw) ?: return false
            db.beginTransaction()
            try {
                writeSense(db, senseId, sd)
                db.update("sense", ContentValues().apply { put("origin", "ai"); put("verified", 0); put("updated", System.currentTimeMillis()) }, "id = ?", arrayOf(senseId.toString()))
                db.setTransactionSuccessful()
            } finally {
                db.endTransaction()
            }
            return true
        }

        fun setPron(ctx: Context, entryId: Long, ipa: String) {
            val db = get(ctx).writableDatabase
            val arg = arrayOf(entryId.toString())
            db.beginTransaction()
            try {
                val n = db.update("pron", ContentValues().apply { put("ipa", ipa); put("origin", "user") }, "entry = ?", arg)
                if (n == 0) db.insert("pron", null, ContentValues().apply { put("entry", entryId); put("ipa", ipa); put("origin", "user") })
                db.setTransactionSuccessful()
            } finally {
                db.endTransaction()
            }
        }

        fun setRelations(ctx: Context, entryId: Long, kind: String, list: List<String>) {
            val db = get(ctx).writableDatabase
            db.beginTransaction()
            try {
                db.delete("relation", "entry = ? AND kind = ?", arrayOf(entryId.toString(), kind))
                for (t in list.map { it.trim() }.filter { it.isNotEmpty() }.distinctBy { VocabText.normalize(it) }) {
                    db.insert("relation", null, ContentValues().apply { put("entry", entryId); put("kind", kind); put("text", t); put("origin", "user") })
                }
                db.setTransactionSuccessful()
            } finally {
                db.endTransaction()
            }
        }

        fun pickSense(ctx: Context, entry: Long, sense: Long?) {
            val db = get(ctx).writableDatabase
            db.execSQL("UPDATE sense SET picked = CASE WHEN id = ? THEN 1 ELSE 0 END WHERE entry = ?", arrayOf<Any>(sense ?: -1L, entry))
        }

        fun detail(ctx: Context, id: Long): EntryDetail? {
            val e = entry(ctx, id) ?: return null
            val db = get(ctx).readableDatabase
            val lemma = db.rawQuery("SELECT lemma FROM entry WHERE id = ?", arrayOf(id.toString())).use { if (it.moveToFirst()) it.getString(0) else null } ?: e.original
            var pron: PronDetail? = null
            val (ipa, reading) = db.rawQuery("SELECT ipa, reading, ipa_uk, ipa_us, syllables, stress, mistakes FROM pron WHERE entry = ?", arrayOf(id.toString())).use {
                if (!it.moveToFirst()) return@use "" to ""
                val generic = it.getString(0) ?: ""
                val syl = it.getString(4)?.split(SYL_SEP)?.filter { s -> s.isNotEmpty() } ?: emptyList()
                val p = PronDetail((it.getString(2) ?: "").ifEmpty { generic }, it.getString(3) ?: "", syl, if (syl.isEmpty()) -1 else it.getInt(5).takeIf { s -> s in syl.indices } ?: -1, mistakesList(it.getString(6)))
                if (!p.empty) pron = p
                generic to (it.getString(1) ?: "")
            }
            val senses = ArrayList<SenseRow>()
            db.rawQuery("SELECT s.id, s.pos, s.definition, s.level, s.usage, s.formality, s.picked, s.origin, s.verified, s.ai_json IS NOT NULL, EXISTS(SELECT 1 FROM progress p WHERE p.sense = s.id), s.kind FROM sense s WHERE s.entry = ? ORDER BY s.kind = 'context' DESC, s.id", arrayOf(id.toString())).use { c ->
                while (c.moveToNext()) {
                    val sid = c.getLong(0)
                    val tr = db.rawQuery("SELECT text FROM translation WHERE sense = ? ORDER BY id", arrayOf(sid.toString())).use { q -> val l = ArrayList<String>(); while (q.moveToNext()) l.add(q.getString(0)); l }
                    val ex = db.rawQuery("SELECT text, translation, reading FROM example WHERE sense = ? ORDER BY id", arrayOf(sid.toString())).use { q -> val l = ArrayList<ExampleData>(); while (q.moveToNext()) l.add(ExampleData(q.getString(0), q.getString(1) ?: "", q.getString(2) ?: "")); l }
                    senses.add(SenseRow(sid, SenseData(c.getString(1) ?: "", c.getString(2), c.getString(3) ?: "", c.getString(4) ?: "", c.getString(5) ?: "", tr, ex), c.getInt(6) == 1, c.getString(7) ?: "ai", c.getInt(8) == 1, c.getInt(9) == 1, c.getInt(10) == 1, c.getString(11) ?: "general"))
                }
            }
            val rel = LinkedHashMap<String, MutableList<String>>()
            db.rawQuery("SELECT kind, text FROM relation WHERE entry = ? ORDER BY id", arrayOf(id.toString())).use { while (it.moveToNext()) rel.getOrPut(it.getString(0)) { ArrayList() }.add(it.getString(1)) }
            val meta = LinkedHashMap<String, String>()
            db.rawQuery("SELECT key, json FROM meta WHERE entry = ? ORDER BY rowid", arrayOf(id.toString())).use { while (it.moveToNext()) meta[it.getString(0)] = it.getString(1) }
            val (context, contextTr) = db.rawQuery("SELECT context, context_tr FROM entry WHERE id = ?", arrayOf(id.toString())).use { if (it.moveToFirst()) it.getString(0) to it.getString(1) else null to null }
            return EntryDetail(e, lemma, ipa, reading, senses, rel, meta, context, contextTr, pron)
        }

        private const val SYL_SEP = "·"

        private fun putPron(cv: ContentValues, p: PronDetail?) {
            cv.put("ipa_uk", p?.uk?.ifEmpty { null })
            cv.put("ipa_us", p?.us?.ifEmpty { null })
            val syl = p?.syllables?.takeIf { it.isNotEmpty() }
            cv.put("syllables", syl?.joinToString(SYL_SEP))
            cv.put("stress", if (p != null && syl != null) p.stress else -1)
            cv.put("mistakes", p?.let { mistakesJson(it.mistakes) })
        }

        private fun mistakesJson(l: List<String>): String? = if (l.isEmpty()) null else org.json.JSONArray(l).toString()

        private fun mistakesList(raw: String?): List<String> {
            if (raw.isNullOrEmpty()) return emptyList()
            return try {
                val a = org.json.JSONArray(raw)
                (0 until a.length()).mapNotNull { a.optString(it, "").trim().takeIf { s -> s.isNotEmpty() } }
            } catch (_: Exception) {
                emptyList()
            }
        }

        private const val SEP = "\u001f"

        fun today(): Long = java.util.concurrent.TimeUnit.MILLISECONDS.toDays(System.currentTimeMillis() + java.util.TimeZone.getDefault().getOffset(System.currentTimeMillis()))

        private fun readProgress(db: SQLiteDatabase, sense: Long): Map<Dimension, Progress> =
            db.rawQuery("SELECT dimension, step, ease, due, lapses, last, attempts, illusion, streak, long_types, long_contexts FROM progress WHERE sense = ?", arrayOf(sense.toString())).use {
                val out = LinkedHashMap<Dimension, Progress>()
                while (it.moveToNext()) {
                    val d = Dimension.values().firstOrNull { x -> x.name == it.getString(0) } ?: continue
                    out[d] = Progress(it.getInt(1), it.getDouble(2), it.getLong(3), it.getInt(4), it.getLong(5), it.getInt(6), it.getInt(7) == 1, it.getInt(8),
                        it.getString(9).split(SEP).filter { s -> s.isNotEmpty() }.toSet(), it.getString(10).split(SEP).filter { s -> s.isNotEmpty() }.toSet())
                }
                out
            }

        fun progressOf(ctx: Context, sense: Long): Map<Dimension, Progress> = readProgress(get(ctx).readableDatabase, sense)

        fun recordAttempt(ctx: Context, sense: Long, dim: Dimension, a: Attempt, answer: String? = null): Progress {
            val db = get(ctx).writableDatabase
            db.beginTransaction()
            try {
                val old = readProgress(db, sense)[dim] ?: Progress()
                val p = Scheduler.apply(old, a)
                db.insert("attempt", null, ContentValues().apply {
                    put("sense", sense); put("dimension", dim.name); put("type", a.type); put("ok", if (a.ok) 1 else 0)
                    put("confidence", a.confidence.name); put("ms", a.ms); put("day", a.day); put("context", a.context); put("answer", answer)
                })
                db.insertWithOnConflict("progress", null, ContentValues().apply {
                    put("sense", sense); put("dimension", dim.name); put("step", p.step); put("ease", p.ease); put("due", p.due)
                    put("lapses", p.lapses); put("last", p.last); put("attempts", p.attempts); put("illusion", if (p.illusion) 1 else 0)
                    put("streak", p.streak); put("long_types", p.longTypes.joinToString(SEP)); put("long_contexts", p.longContexts.joinToString(SEP))
                }, SQLiteDatabase.CONFLICT_REPLACE)
                db.setTransactionSuccessful()
                return p
            } finally {
                db.endTransaction()
            }
        }

        fun items(ctx: Context, hasTts: Boolean): List<Item> {
            val db = get(ctx).readableDatabase
            val rows = db.rawQuery(
                "SELECT s.id, s.entry, e.pair, s.picked, e.detected, p.target, " +
                    "(SELECT COUNT(*) FROM example x WHERE x.sense = s.id), (SELECT COUNT(*) FROM sense o WHERE o.entry = s.entry AND o.picked = 1) " +
                    "FROM sense s JOIN entry e ON e.id = s.entry JOIN pair p ON p.id = e.pair ORDER BY s.id", null
            ).use {
                class R(val sense: Long, val entry: Long, val pair: Long, val picked: Boolean, val lang: String?, val examples: Int, val anyPicked: Boolean)
                val l = ArrayList<R>()
                while (it.moveToNext()) l.add(R(it.getLong(0), it.getLong(1), it.getLong(2), it.getInt(3) == 1, it.getString(4), it.getInt(6), it.getInt(7) > 0))
                l
            }
            val names = db.rawQuery("SELECT r.entry, r.text FROM relation r WHERE r.kind = 'confusable'", null).use {
                val m = HashMap<Long, MutableSet<String>>()
                while (it.moveToNext()) m.getOrPut(it.getLong(0)) { HashSet() }.add(VocabText.normalize(it.getString(1)))
                m
            }
            val own = db.rawQuery("SELECT id, normalized FROM entry", null).use {
                val m = HashMap<Long, String>()
                while (it.moveToNext()) m[it.getLong(0)] = it.getString(1)
                m
            }
            return rows.filter { !it.anyPicked || it.picked }.map { r ->
                val dims = LinkedHashSet<Dimension>()
                dims.add(Dimension.RECOGNITION); dims.add(Dimension.MEANING_RECALL); dims.add(Dimension.PRODUCTION)
                if (r.examples > 0) dims.add(Dimension.CONTEXT)
                dims.add(Dimension.SENTENCE)
                if (hasTts) dims.add(Dimension.LISTENING)
                if (r.lang in setOf("zh", "ja", "de", "ru", "ko")) dims.add(Dimension.LANGUAGE)
                Item(r.sense, r.entry, r.pair, (names[r.entry] ?: emptySet<String>()) + setOfNotNull(own[r.entry]), readProgress(db, r.sense), dims)
            }
        }

        fun attemptRows(ctx: Context, pair: Long, sinceDay: Long = 0): List<AttemptRow> =
            get(ctx).readableDatabase.rawQuery(
                "SELECT a.sense, s.entry, e.original, a.dimension, a.type, a.ok, a.day, a.confidence FROM attempt a JOIN sense s ON s.id = a.sense JOIN entry e ON e.id = s.entry " +
                    "WHERE e.pair = ? AND a.day >= ? ORDER BY a.id", arrayOf(pair.toString(), sinceDay.toString())
            ).use {
                val out = ArrayList<AttemptRow>()
                while (it.moveToNext()) {
                    val d = Dimension.values().firstOrNull { x -> x.name == it.getString(3) } ?: continue
                    val c = Confidence.values().firstOrNull { x -> x.name == it.getString(7) } ?: Confidence.SOME
                    out.add(AttemptRow(it.getLong(0), it.getLong(1), it.getString(2), d, it.getString(4), it.getInt(5) == 1, it.getLong(6), c))
                }
                out
            }

        fun senseStats(ctx: Context, pair: Long): List<SenseStat> {
            val db = get(ctx).readableDatabase
            val rows = db.rawQuery("SELECT s.id, s.entry, e.original FROM sense s JOIN entry e ON e.id = s.entry WHERE e.pair = ?", arrayOf(pair.toString())).use {
                val l = ArrayList<Triple<Long, Long, String>>()
                while (it.moveToNext()) l.add(Triple(it.getLong(0), it.getLong(1), it.getString(2)))
                l
            }
            return rows.map { SenseStat(it.first, it.second, it.third, readProgress(db, it.first)) }
        }

        fun sessionDays(ctx: Context): Set<Long> = get(ctx).readableDatabase.rawQuery("SELECT DISTINCT day FROM session", null).use {
            val s = HashSet<Long>()
            while (it.moveToNext()) s.add(it.getLong(0))
            s
        }

        fun saveSession(ctx: Context, day: Long, asked: Int, correct: Int, reflection: String?) {
            get(ctx).writableDatabase.insert("session", null, ContentValues().apply { put("day", day); put("asked", asked); put("correct", correct); put("reflection", reflection) })
        }

        fun deleteEntry(ctx: Context, id: Long) {
            get(ctx).writableDatabase.delete("entry", "id = ?", arrayOf(id.toString()))
        }
    }
}
