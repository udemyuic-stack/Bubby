package com.example.buddy

import android.Manifest
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CalendarContract
import org.json.JSONObject
import java.util.TimeZone

class CalEvent(val id: Long, val title: String, val startMillis: Long, val endMillis: Long, val location: String)

class CalendarStore(private val ctx: Context) {
    private fun granted(p: String) = ctx.checkSelfPermission(p) == PackageManager.PERMISSION_GRANTED
    private fun canRead() = granted(Manifest.permission.READ_CALENDAR)

    fun hasPermission(): Boolean = canRead() && granted(Manifest.permission.WRITE_CALENDAR)

    fun events(fromMillis: Long, toMillis: Long): List<CalEvent> {
        if (!canRead()) return emptyList()
        val out = mutableListOf<CalEvent>()
        try {
            val uri = CalendarContract.Instances.CONTENT_URI.buildUpon().also {
                ContentUris.appendId(it, fromMillis)
                ContentUris.appendId(it, toMillis)
            }.build()
            val proj = arrayOf(
                CalendarContract.Instances.EVENT_ID, CalendarContract.Instances.TITLE,
                CalendarContract.Instances.BEGIN, CalendarContract.Instances.END,
                CalendarContract.Instances.EVENT_LOCATION, CalendarContract.Instances.ALL_DAY
            )
            ctx.contentResolver.query(uri, proj, null, null, "${CalendarContract.Instances.BEGIN} ASC")?.use { c ->
                val tz = TimeZone.getDefault()
                while (c.moveToNext()) {
                    var b = c.getLong(2)
                    var e = c.getLong(3)
                    if (c.getInt(5) == 1) { // all-day instances are stored in UTC
                        b -= tz.getOffset(b); e -= tz.getOffset(e)
                    }
                    out.add(CalEvent(c.getLong(0), c.getString(1) ?: "", b, e, c.getString(4) ?: ""))
                }
            }
        } catch (_: Exception) {
        }
        return out
    }

    private fun writableCalendarId(): Long? {
        val proj = arrayOf(CalendarContract.Calendars._ID)
        val sel = "${CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL} >= ?"
        val order = "${CalendarContract.Calendars.IS_PRIMARY} DESC, ${CalendarContract.Calendars._ID} ASC"
        ctx.contentResolver.query(
            CalendarContract.Calendars.CONTENT_URI, proj, sel,
            arrayOf(CalendarContract.Calendars.CAL_ACCESS_CONTRIBUTOR.toString()), order
        )?.use { c -> if (c.moveToFirst()) return c.getLong(0) }
        return null
    }

    fun add(title: String, startMillis: Long, endMillis: Long, location: String = "", notes: String = ""): Long? {
        if (!hasPermission()) return null
        return try {
            val cal = writableCalendarId() ?: return null
            val v = ContentValues().apply {
                put(CalendarContract.Events.CALENDAR_ID, cal)
                put(CalendarContract.Events.TITLE, title)
                put(CalendarContract.Events.DTSTART, startMillis)
                put(CalendarContract.Events.DTEND, if (endMillis > startMillis) endMillis else startMillis + 3_600_000L)
                put(CalendarContract.Events.EVENT_TIMEZONE, TimeZone.getDefault().id)
                if (location.isNotEmpty()) put(CalendarContract.Events.EVENT_LOCATION, location)
                if (notes.isNotEmpty()) put(CalendarContract.Events.DESCRIPTION, notes)
            }
            ctx.contentResolver.insert(CalendarContract.Events.CONTENT_URI, v)?.lastPathSegment?.toLongOrNull()
        } catch (_: Exception) {
            null
        }
    }

    fun delete(id: Long): Boolean {
        if (!hasPermission()) return false
        return try {
            ctx.contentResolver.delete(ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, id), null, null) > 0
        } catch (_: Exception) {
            false
        }
    }

    fun summaryForPrompt(): String {
        val now = System.currentTimeMillis()
        val list = events(now, now + 48 * 3_600_000L).take(10)
        if (list.isEmpty()) return "none"
        return list.joinToString("\n") { e ->
            val loc = if (e.location.isNotEmpty()) " @${e.location.take(30)}" else ""
            "- ${fmtWhen(e.startMillis)} ${e.title.take(60)}$loc"
        }
    }

    fun toJson(e: CalEvent): JSONObject = JSONObject().apply {
        put("id", e.id)
        put("title", e.title)
        put("startMillis", e.startMillis)
        put("endMillis", e.endMillis)
        put("location", e.location)
    }
}
