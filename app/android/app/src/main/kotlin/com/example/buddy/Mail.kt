package com.example.buddy

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.ContactsContract.CommonDataKinds.Email

/** Finds a contact's email address by the name the user spoke. */
internal object Mail {
    class Cand(val name: String, val address: String, val score: Int)

    /** Pure ranking over (name, address) rows. */
    internal fun rank(rows: List<Pair<String, String>>, spoken: String, max: Int = 4): List<Cand> {
        val q = NameText.key(spoken)
        if (q.isEmpty()) return emptyList()
        val seen = HashSet<String>()
        val out = ArrayList<Cand>()
        for ((name, addr) in rows) {
            val n = NameText.key(name)
            val s = when {
                n == q -> 100
                n.startsWith(q) -> 90
                q in n.split(' ') -> 80
                q.length >= 3 && n.contains(q) -> 60
                else -> 0
            }
            if (s == 0 || !seen.add(addr.lowercase())) continue
            out.add(Cand(name, addr, s))
        }
        out.sortWith(compareByDescending<Cand> { it.score }.thenBy { it.name })
        return if (out.size > max) out.subList(0, max).toList() else out
    }

    /** Blocking: call off the main thread. Empty without READ_CONTACTS. */
    fun find(ctx: Context, spoken: String): List<Cand> {
        if (ctx.checkSelfPermission(Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) return emptyList()
        val rows = ArrayList<Pair<String, String>>()
        ctx.contentResolver.query(Email.CONTENT_URI, arrayOf(Email.DISPLAY_NAME, Email.ADDRESS), null, null, null)?.use { c ->
            while (c.moveToNext()) {
                val n = c.getString(0) ?: continue
                val a = c.getString(1) ?: continue
                rows.add(n to a)
            }
        }
        return rank(rows, spoken)
    }
}
