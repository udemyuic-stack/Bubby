package com.example.buddy

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** Named lists (shopping, notes...) stored as JSON in prefs `buddy` / `lists_json`. */
object ListStore {
    private const val KEY = "lists_json"
    private const val CAP = 200
    const val DEFAULT = "shopping"

    private fun prefs(ctx: Context) = ctx.applicationContext.getSharedPreferences("buddy", Context.MODE_PRIVATE)
    private fun norm(l: String) = l.trim().lowercase().ifEmpty { DEFAULT }

    private fun load(ctx: Context): LinkedHashMap<String, MutableList<String>> {
        val m = LinkedHashMap<String, MutableList<String>>()
        try {
            val o = JSONObject(prefs(ctx).getString(KEY, "{}") ?: "{}")
            val it = o.keys()
            while (it.hasNext()) {
                val k = it.next()
                val a = o.optJSONArray(k) ?: continue
                val l = mutableListOf<String>()
                for (i in 0 until a.length()) a.optString(i, "").takeIf { s -> s.isNotEmpty() }?.let { s -> l.add(s) }
                m[k] = l
            }
        } catch (_: Exception) {
        }
        return m
    }

    private fun save(ctx: Context, m: Map<String, List<String>>) {
        val o = JSONObject()
        for ((k, v) in m) if (v.isNotEmpty()) o.put(k, JSONArray(v))
        prefs(ctx).edit().putString(KEY, o.toString()).apply()
    }

    /** Adds an item; returns the new item count of the list (unchanged if duplicate or full). */
    @Synchronized
    fun add(ctx: Context, list: String, item: String): Int {
        val name = norm(list)
        val text = item.trim().take(200)
        val m = load(ctx)
        val l = m.getOrPut(name) { mutableListOf() }
        if (text.isNotEmpty() && l.size < CAP && l.none { it.equals(text, ignoreCase = true) }) l.add(text)
        save(ctx, m)
        return l.size
    }

    @Synchronized
    fun items(ctx: Context, list: String): List<String> = load(ctx)[norm(list)]?.toList() ?: emptyList()

    @Synchronized
    fun remove(ctx: Context, list: String, query: String): Boolean {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return false
        val m = load(ctx)
        val l = m[norm(list)] ?: return false
        val idx = l.indexOfFirst { it.lowercase() == q }.takeIf { it >= 0 } ?: l.indexOfFirst { it.lowercase().contains(q) }
        if (idx < 0) return false
        l.removeAt(idx)
        save(ctx, m)
        return true
    }

    @Synchronized
    fun clear(ctx: Context, list: String) {
        val m = load(ctx)
        if (m.remove(norm(list)) != null) save(ctx, m)
    }

    @Synchronized
    fun lists(ctx: Context): List<String> = load(ctx).filter { it.value.isNotEmpty() }.keys.toList()

    @Synchronized
    fun summaryForAi(ctx: Context): String {
        val m = load(ctx).filter { it.value.isNotEmpty() }
        if (m.isEmpty()) return "none"
        return m.entries.joinToString("\n") { (k, v) -> "$k: " + v.take(30).joinToString("; ") { it.take(40) } }
    }
}
