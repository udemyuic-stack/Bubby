package com.example.buddy

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

class Task(val id: String, var title: String, var notes: String, var dueMillis: Long?, var remind: Boolean, var done: Boolean)

internal fun fmtWhen(millis: Long): String =
    SimpleDateFormat("EEE d MMM HH:mm", Locale.getDefault()).format(Date(millis))

class TaskStore(ctx: Context) {
    private val prefs = ctx.applicationContext.getSharedPreferences("buddy", Context.MODE_PRIVATE)

    private fun load(): MutableList<Task> {
        val out = mutableListOf<Task>()
        try {
            val arr = JSONArray(prefs.getString("tasks", "[]") ?: "[]")
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                out.add(fromJson(o))
            }
        } catch (_: Exception) {
        }
        return out
    }

    private fun save(list: List<Task>) {
        val arr = JSONArray()
        list.forEach { arr.put(toJson(it)) }
        prefs.edit().putString("tasks", arr.toString()).apply()
    }

    fun all(): List<Task> = load().sortedWith(
        compareBy<Task> { it.done }.thenBy { it.dueMillis ?: Long.MAX_VALUE }.thenBy { it.title.lowercase() }
    )

    fun add(title: String, notes: String = "", dueMillis: Long? = null, remind: Boolean = true): Task {
        val t = Task(UUID.randomUUID().toString(), title, notes, dueMillis, remind, false)
        val list = load()
        list.add(t)
        save(list)
        return t
    }

    fun update(t: Task) {
        val list = load()
        val i = list.indexOfFirst { it.id == t.id }
        if (i >= 0) list[i] = t else list.add(t)
        save(list)
    }

    fun delete(id: String) {
        val list = load()
        if (list.removeAll { it.id == id }) save(list)
    }

    fun setDone(id: String, done: Boolean) {
        val list = load()
        val t = list.firstOrNull { it.id == id } ?: return
        t.done = done
        save(list)
    }

    fun findByTitle(q: String): Task? {
        val s = q.trim().lowercase()
        if (s.isEmpty()) return null
        return all().firstOrNull { it.title.lowercase().contains(s) }
    }

    fun summaryForPrompt(): String {
        val open = all().filter { !it.done }
        if (open.isEmpty()) return "none"
        return open.take(15).joinToString("\n") { t ->
            val due = t.dueMillis?.let { " (due ${fmtWhen(it)})" } ?: ""
            "- ${t.title.take(60)}$due"
        }
    }

    fun toJson(t: Task): JSONObject = JSONObject().apply {
        put("id", t.id)
        put("title", t.title)
        put("notes", t.notes)
        put("dueMillis", t.dueMillis ?: JSONObject.NULL)
        put("remind", t.remind)
        put("done", t.done)
    }

    fun fromJson(j: JSONObject): Task {
        val id = j.optString("id", "").ifEmpty { UUID.randomUUID().toString() }
        val due = if (j.isNull("dueMillis")) null else j.optLong("dueMillis").takeIf { it > 0 }
        return Task(
            id, j.optString("title", ""), j.optString("notes", ""), due,
            j.optBoolean("remind", true), j.optBoolean("done", false)
        )
    }
}
