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

class Entry(val id: Long, val pair: Long, val original: String, val status: EntryStatus, val added: Long, val detected: String? = null, val error: String? = null)

class SenseRow(val id: Long, val data: SenseData, val picked: Boolean)

class EntryDetail(val entry: Entry, val lemma: String, val ipa: String, val reading: String, val senses: List<SenseRow>, val relations: Map<String, List<String>>, val meta: Map<String, String>)

sealed class AddResult {
    class Added(val id: Long, val pair: Long) : AddResult()
    object Empty : AddResult()
    object TooLong : AddResult()
    object Duplicate : AddResult()
    object NoPair : AddResult()
}

class VocabDb private constructor(ctx: Context) : SQLiteOpenHelper(ctx.applicationContext, "vocab.db", null, 3) {
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

        fun entries(ctx: Context, pair: Long, query: String = "", limit: Int = 200): List<Entry> {
            val q = VocabText.fold(query)
            val sql = "SELECT id, pair, original, status, added, detected, error FROM entry WHERE pair = ?" + (if (q.isEmpty()) "" else " AND folded LIKE ? ESCAPE '\\'") + " ORDER BY id DESC LIMIT $limit"
            val args = if (q.isEmpty()) arrayOf(pair.toString()) else arrayOf(pair.toString(), "%" + q.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%")
            return get(ctx).readableDatabase.rawQuery(sql, args).use {
                val out = ArrayList<Entry>()
                while (it.moveToNext()) out.add(Entry(it.getLong(0), it.getLong(1), it.getString(2), EntryStatus.valueOf(it.getString(3)), it.getLong(4), it.getString(5), it.getString(6)))
                out
            }
        }

        fun addEntry(ctx: Context, pair: Long, input: String): AddResult {
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
                is AddCheck.Duplicate -> AddResult.Duplicate
                is AddCheck.Ok -> {
                    val cv = ContentValues().apply {
                        put("pair", pair); put("original", c.original); put("normalized", c.normalized); put("folded", c.folded)
                        put("status", EntryStatus.SAVED.name); put("added", System.currentTimeMillis())
                    }
                    val id = db.insert("entry", null, cv)
                    if (id < 0) AddResult.Duplicate else AddResult.Added(id, pair)
                }
            }
        }

        fun entry(ctx: Context, id: Long): Entry? = get(ctx).readableDatabase.rawQuery("SELECT id, pair, original, status, added, detected, error FROM entry WHERE id = ?", arrayOf(id.toString())).use {
            if (it.moveToFirst()) Entry(it.getLong(0), it.getLong(1), it.getString(2), EntryStatus.valueOf(it.getString(3)), it.getLong(4), it.getString(5), it.getString(6)) else null
        }

        fun pair(ctx: Context, id: Long): LangPair? = pairs(ctx).firstOrNull { it.id == id }

        fun pending(ctx: Context): List<Long> = get(ctx).readableDatabase.rawQuery("SELECT id FROM entry WHERE status IN ('SAVED','ENRICHING') ORDER BY id", null).use {
            val out = ArrayList<Long>()
            while (it.moveToNext()) out.add(it.getLong(0))
            out
        }

        fun setStatus(ctx: Context, id: Long, status: EntryStatus, error: String?) {
            val cv = ContentValues().apply { put("status", status.name); put("error", error) }
            get(ctx).writableDatabase.update("entry", cv, "id = ?", arrayOf(id.toString()))
        }

        fun setDetected(ctx: Context, id: Long, lang: String?) {
            val cv = ContentValues().apply { put("detected", lang) }
            get(ctx).writableDatabase.update("entry", cv, "id = ?", arrayOf(id.toString()))
        }

        fun clearDetail(db: SQLiteDatabase, id: Long) {
            val arg = arrayOf(id.toString())
            db.execSQL("DELETE FROM sense WHERE entry = ?", arg)
            db.delete("pron", "entry = ?", arg)
            db.delete("meta", "entry = ?", arg)
            db.delete("relation", "entry = ?", arg)
        }

        fun saveEnriched(ctx: Context, id: Long, d: Enriched) {
            val db = get(ctx).writableDatabase
            db.beginTransaction()
            try {
                clearDetail(db, id)
                for (sd in d.senses) {
                    val cv = ContentValues().apply {
                        put("entry", id); put("pos", sd.pos); put("definition", sd.definition); put("level", sd.level)
                        put("usage", sd.usage); put("formality", sd.formality)
                    }
                    val sid = db.insert("sense", null, cv)
                    for (t in sd.translations) db.insert("translation", null, ContentValues().apply { put("sense", sid); put("text", t) })
                    for (e in sd.examples) db.insert("example", null, ContentValues().apply { put("sense", sid); put("text", e.text); put("translation", e.translation); put("reading", e.reading) })
                }
                db.insert("pron", null, ContentValues().apply { put("entry", id); put("ipa", d.ipa); put("reading", d.reading) })
                for ((k, v) in d.meta) db.insert("meta", null, ContentValues().apply { put("entry", id); put("key", k); put("json", v) })
                for ((k, list) in d.relations) for (t in list) db.insert("relation", null, ContentValues().apply { put("entry", id); put("kind", k); put("text", t) })
                db.update("entry", ContentValues().apply { put("status", EntryStatus.READY.name); put("detected", d.language); put("lemma", d.lemma); putNull("error") }, "id = ?", arrayOf(id.toString()))
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
            val (ipa, reading) = db.rawQuery("SELECT ipa, reading FROM pron WHERE entry = ?", arrayOf(id.toString())).use { if (it.moveToFirst()) (it.getString(0) ?: "") to (it.getString(1) ?: "") else "" to "" }
            val senses = ArrayList<SenseRow>()
            db.rawQuery("SELECT id, pos, definition, level, usage, formality, picked FROM sense WHERE entry = ? ORDER BY id", arrayOf(id.toString())).use { c ->
                while (c.moveToNext()) {
                    val sid = c.getLong(0)
                    val tr = db.rawQuery("SELECT text FROM translation WHERE sense = ? ORDER BY id", arrayOf(sid.toString())).use { q -> val l = ArrayList<String>(); while (q.moveToNext()) l.add(q.getString(0)); l }
                    val ex = db.rawQuery("SELECT text, translation, reading FROM example WHERE sense = ? ORDER BY id", arrayOf(sid.toString())).use { q -> val l = ArrayList<ExampleData>(); while (q.moveToNext()) l.add(ExampleData(q.getString(0), q.getString(1) ?: "", q.getString(2) ?: "")); l }
                    senses.add(SenseRow(sid, SenseData(c.getString(1) ?: "", c.getString(2), c.getString(3) ?: "", c.getString(4) ?: "", c.getString(5) ?: "", tr, ex), c.getInt(6) == 1))
                }
            }
            val rel = LinkedHashMap<String, MutableList<String>>()
            db.rawQuery("SELECT kind, text FROM relation WHERE entry = ? ORDER BY id", arrayOf(id.toString())).use { while (it.moveToNext()) rel.getOrPut(it.getString(0)) { ArrayList() }.add(it.getString(1)) }
            val meta = LinkedHashMap<String, String>()
            db.rawQuery("SELECT key, json FROM meta WHERE entry = ? ORDER BY rowid", arrayOf(id.toString())).use { while (it.moveToNext()) meta[it.getString(0)] = it.getString(1) }
            return EntryDetail(e, lemma, ipa, reading, senses, rel, meta)
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
                    "FROM sense s JOIN entry e ON e.id = s.entry JOIN pair p ON p.id = e.pair WHERE e.status = 'READY' ORDER BY s.id", null
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
            val rows = db.rawQuery("SELECT s.id, s.entry, e.original FROM sense s JOIN entry e ON e.id = s.entry WHERE e.pair = ? AND e.status = 'READY'", arrayOf(pair.toString())).use {
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
