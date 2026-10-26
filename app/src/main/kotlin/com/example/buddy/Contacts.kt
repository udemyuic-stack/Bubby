package com.example.buddy

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.ContactsContract
import android.provider.ContactsContract.CommonDataKinds.Phone
import java.text.Normalizer

/** Shared text normalisation for spoken names (ru / uz / en). */
internal object NameText {
    private val CYR: Map<Char, String> = mapOf(
        'а' to "a", 'б' to "b", 'в' to "v", 'г' to "g", 'д' to "d", 'е' to "e", 'ж' to "j",
        'з' to "z", 'и' to "i", 'й' to "y", 'к' to "k", 'л' to "l", 'м' to "m", 'н' to "n",
        'о' to "o", 'п' to "p", 'р' to "r", 'с' to "s", 'т' to "t", 'у' to "u", 'ф' to "f",
        'х' to "x", 'ц' to "ts", 'ч' to "ch", 'ш' to "sh", 'щ' to "sh", 'ъ' to "", 'ь' to "",
        'ы' to "i", 'э' to "e", 'ю' to "yu", 'я' to "ya", 'ў' to "o", 'ғ' to "g", 'қ' to "q",
        'ҳ' to "x", 'ҷ' to "j", 'ё' to "e"
    )
    private val H_RE = Regex("(?<![sc])h")
    private val DUP_RE = Regex("(.)\\1+")
    private val SPACES = Regex("\\s+")

    /** Lowercase, no diacritics/punctuation, unified apostrophes, ё->е. Keeps Cyrillic. */
    fun norm(s: String): String {
        val low = s.lowercase().replace('ё', 'е')
        val sb = StringBuilder(low.length)
        for (c in low) {
            val ch: Char = when (c) {
                'ʻ', 'ʼ', '’', '‘', '`', '´', 'ʻ', 'ʼ' -> '\''
                else -> c
            }
            if (ch.code in 0x0400..0x052F || ch == '\'') {
                sb.append(ch)
            } else if (ch.isLetterOrDigit()) {
                val d = Normalizer.normalize(ch.toString(), Normalizer.Form.NFD)
                for (x in d) {
                    if (Character.getType(x) != Character.NON_SPACING_MARK.toInt()) sb.append(x)
                }
            } else {
                sb.append(' ')
            }
        }
        return sb.toString().replace(SPACES, " ").trim()
    }

    /** Phonetic Latin key: Cyrillic and Latin spellings of the same name collapse to the same string. */
    fun key(s: String): String {
        val n = norm(s)
        val sb = StringBuilder(n.length + 4)
        for (c in n) {
            val m = CYR[c]
            if (m != null) sb.append(m) else if (c != '\'') sb.append(c)
        }
        return sb.toString()
            .replace("kh", "x")
            .replace("zh", "j")
            .replace(H_RE, "x")
            .replace('q', 'k')
            .replace('w', 'v')
            .replace("ye", "e")
            .replace("yo", "o")
            .replace(DUP_RE, "\$1")
            .replace(SPACES, " ")
            .trim()
    }

    fun lev(a: String, b: String): Int {
        if (a == b) return 0
        if (a.isEmpty()) return b.length
        if (b.isEmpty()) return a.length
        var prev = IntArray(b.length + 1) { it }
        var cur = IntArray(b.length + 1)
        for (i in 1..a.length) {
            cur[0] = i
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                cur[j] = minOf(prev[j] + 1, cur[j - 1] + 1, prev[j - 1] + cost)
            }
            val t = prev; prev = cur; cur = t
        }
        return prev[b.length]
    }
}

/**
 * Resolves a spoken name (ru/uz/en, nicknames, transliteration) to phone contacts.
 * All functions here are BLOCKING (content provider query): call from a background thread.
 * Never touches the UI. Never logs names or numbers.
 */
object Contacts {
    class Candidate(val name: String, val number: String, val label: String, val score: Int)

    internal class Row(
        val name: String, val number: String, val type: Int, val custom: String?,
        val starred: Boolean, val times: Int
    )

    private val FAMILY: List<Set<String>> = listOf(
        // mother
        listOf("мама", "мам", "маме", "маму", "мамы", "мамочка", "мамуля", "мать", "mama", "mom", "mother", "mum", "mummy",
            "mommy", "ona", "onam", "onamga", "onajon", "oyi", "oyim", "oyijon", "oyimga", "ойи", "она", "онам", "оям"),
        // father
        listOf("папа", "папе", "папу", "папы", "папочка", "отец", "батя", "papa", "dad", "daddy", "father", "ota", "otam",
            "otamga", "otajon", "dadam", "dada", "dadajon", "pochcha", "ота", "отам", "дада"),
        // wife
        listOf("жена", "жене", "жену", "жёнушка", "супруга", "wife", "xotin", "xotinim", "xotinga", "ayol", "хотин", "хотиним"),
        // husband
        listOf("муж", "мужу", "супруг", "husband", "er", "erim", "eri", "эр", "эрим"),
        // brother
        listOf("брат", "брату", "брата", "братан", "brother", "bro", "aka", "akam", "akamga", "akajon", "uka", "ukam",
            "ukamga", "ukajon", "ака", "акам", "ука", "укам"),
        // sister
        listOf("сестра", "сестре", "сестру", "сестрёнка", "sister", "sis", "opa", "opam", "opamga", "opajon", "singil",
            "singlim", "опа", "опам", "сингил"),
        // boss
        listOf("boss", "босс", "начальник", "начальника", "шеф", "rahbar", "boshliq", "bosh")
    ).map { g -> g.map { NameText.key(it) }.toSet() }

    fun isNumber(s: String): Boolean {
        val t = s.trim()
        if (t.isEmpty()) return false
        var digits = 0
        for (c in t) {
            when {
                c.isDigit() -> digits++
                c == '+' || c == '-' || c == '(' || c == ')' || c == ' ' || c == ' ' -> {}
                else -> return false
            }
        }
        return digits >= 3
    }

    fun normalizeNumber(s: String): String {
        val sb = StringBuilder()
        for (c in s) {
            if (c.isDigit()) sb.append(c)
            else if (c == '+' && sb.isEmpty()) sb.append(c)
        }
        return sb.toString()
    }

    /** Blocking. Call from a background thread. Returns best-first; empty without READ_CONTACTS. */
    fun resolve(ctx: Context, spoken: String, max: Int = 4): List<Candidate> {
        val q = NameText.norm(spoken)
        if (q.isEmpty() || isNumber(spoken) || max <= 0) return emptyList()
        if (ctx.checkSelfPermission(Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
            return emptyList()
        }
        return rank(load(ctx), spoken, max)
    }

    /** Pure ranking over already-loaded rows (unit-testable). */
    internal fun rank(rows: List<Row>, spoken: String, max: Int = 4): List<Candidate> {
        val q = NameText.norm(spoken)
        if (q.isEmpty() || isNumber(spoken) || max <= 0) return emptyList()
        val qk = NameText.key(spoken)
        val fam = FAMILY.filter { qk in it }
        val nameScores = HashMap<String, Int>()
        val seen = HashSet<String>()
        val out = ArrayList<Candidate>()
        for (r in rows) {
            val base = nameScores.getOrPut(r.name) { nameScore(q, qk, fam, r.name) }
            if (base < 40) continue
            val digits = r.number.filter { it.isDigit() }
            if (digits.isEmpty()) continue
            if (!seen.add(r.name + "|" + digits.takeLast(9))) continue
            var bonus = 0
            if (r.starred) bonus += 4
            if (r.times > 0) bonus += minOf(4, r.times / 5 + 1)
            out.add(Candidate(r.name, normalizeNumber(r.number), typeLabel(r.type, r.custom), base + bonus))
        }
        out.sortWith(compareByDescending<Candidate> { it.score }.thenBy { it.name })
        return if (out.size > max) out.subList(0, max).toList() else out
    }

    private fun typeLabel(type: Int, custom: String?): String = when (type) {
        Phone.TYPE_MOBILE -> "mobile"
        Phone.TYPE_HOME -> "home"
        Phone.TYPE_WORK -> "work"
        Phone.TYPE_WORK_MOBILE -> "work mobile"
        Phone.TYPE_MAIN -> "main"
        Phone.TYPE_CUSTOM -> if (!custom.isNullOrBlank()) custom else "other"
        else -> "other"
    }

    private fun nameScore(q: String, qk: String, fam: List<Set<String>>, name: String): Int {
        val n = NameText.norm(name)
        val nk = NameText.key(name)
        var s = score(q, n)
        val sk = score(qk, nk)
        if (sk > 0 && sk - 5 > s) s = sk - 5
        if (fam.isNotEmpty()) {
            val toks = nk.split(' ')
            if (fam.any { g -> toks.any { it in g } }) s = maxOf(s, 85)
        }
        return s
    }

    private fun score(q: String, n: String): Int {
        if (q.isEmpty() || n.isEmpty()) return 0
        if (n == q) return 100
        val qt = q.split(' ')
        val nt = n.split(' ')
        if (q.length >= 2 && n.startsWith(q)) return 90
        if (qt.size > 1 && qt.all { it in nt }) return 88
        if (qt.size == 1 && qt[0] in nt) return 80
        if (q.length >= 2 && qt.all { t -> t.length >= 2 && nt.any { it.startsWith(t) } }) return 74
        if (q.length >= 3 && n.contains(q)) return 62
        if (qt.all { t -> t.length >= 5 && nt.any { NameText.lev(it, t) <= 1 } }) return 50
        if (q.length >= 5 && NameText.lev(n, q) <= 1) return 50
        return 0
    }

    private fun load(ctx: Context): List<Row> {
        return try {
            query(ctx, true)
        } catch (e: Exception) {
            try { query(ctx, false) } catch (e2: Exception) { emptyList() }
        }
    }

    private fun query(ctx: Context, withTimes: Boolean): List<Row> {
        val cols = if (withTimes) arrayOf(
            Phone.DISPLAY_NAME, Phone.NUMBER, Phone.TYPE, Phone.LABEL, Phone.STARRED, Phone.TIMES_CONTACTED
        ) else arrayOf(
            Phone.DISPLAY_NAME, Phone.NUMBER, Phone.TYPE, Phone.LABEL, Phone.STARRED
        )
        val rows = ArrayList<Row>()
        ctx.contentResolver.query(Phone.CONTENT_URI, cols, null, null, null)?.use { c ->
            while (c.moveToNext()) {
                val name = c.getString(0) ?: continue
                val number = c.getString(1) ?: continue
                rows.add(
                    Row(
                        name, number, c.getInt(2), c.getString(3),
                        c.getInt(4) == 1, if (withTimes) c.getInt(5) else 0
                    )
                )
            }
        }
        return rows
    }
}
