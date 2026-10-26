package com.example.buddy.ui

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import com.example.buddy.Brain
import com.example.buddy.BuddyAccess
import com.example.buddy.BuddyService
import com.example.buddy.CalendarStore
import com.example.buddy.Tr

/** What the screens need from the rest of the app (this replaces the old Flutter method channel). */
object Backend {
    fun prefs(ctx: Context): SharedPreferences = ctx.getSharedPreferences("buddy", Context.MODE_PRIVATE)

    fun bool(ctx: Context, key: String, def: Boolean) = prefs(ctx).getBoolean(key, def)
    fun setBool(ctx: Context, key: String, v: Boolean) = prefs(ctx).edit().putBoolean(key, v).apply()
    fun str(ctx: Context, key: String, def: String = "") = prefs(ctx).getString(key, def) ?: def
    fun setStr(ctx: Context, key: String, v: String) = prefs(ctx).edit().putString(key, v).apply()
    fun int(ctx: Context, key: String, def: Int) = prefs(ctx).getInt(key, def)
    fun setInt(ctx: Context, key: String, v: Int) = prefs(ctx).edit().putInt(key, v).apply()

    fun running() = BuddyService.running

    fun aiKeys(ctx: Context): List<String> = Brain(ctx).keyList()

    /** Stores the connections in priority order (first = primary). */
    fun saveAiKeys(ctx: Context, list: List<String>) {
        val clean = list.map { it.trim() }.filter { it.isNotEmpty() }
        prefs(ctx).edit()
            .putString("keys_json", org.json.JSONArray(clean).toString())
            .putString("key", clean.firstOrNull().orEmpty()).apply()
    }

    private fun granted(ctx: Context, p: String) = ctx.checkSelfPermission(p) == PackageManager.PERMISSION_GRANTED

    fun permissions(act: Activity): Map<String, Boolean> {
        fun all(vararg p: String) = p.all { granted(act, it) }
        return mapOf(
            "overlay" to Settings.canDrawOverlays(act),
            "accessibility" to BuddyAccess.enabled,
            "mic" to granted(act, Manifest.permission.RECORD_AUDIO),
            "phone" to all(Manifest.permission.CALL_PHONE, Manifest.permission.ANSWER_PHONE_CALLS, Manifest.permission.READ_PHONE_STATE),
            "calendar" to CalendarStore(act).hasPermission(),
            "notifications" to (Build.VERSION.SDK_INT < 33 || granted(act, Manifest.permission.POST_NOTIFICATIONS)),
            "contacts" to granted(act, Manifest.permission.READ_CONTACTS),
            "location" to granted(act, Manifest.permission.ACCESS_COARSE_LOCATION),
            "sms" to granted(act, Manifest.permission.SEND_SMS),
            "exactAlarm" to com.example.buddy.Reminders.canExact(act),
            "notifListener" to (Settings.Secure.getString(act.contentResolver, "enabled_notification_listeners") ?: "").contains(act.packageName),
            "dnd" to (act.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager).isNotificationPolicyAccessGranted,
            "writeSettings" to Settings.System.canWrite(act)
        )
    }

    fun batteryIgnored(ctx: Context): Boolean =
        (ctx.getSystemService(Context.POWER_SERVICE) as PowerManager).isIgnoringBatteryOptimizations(ctx.packageName)

    /** Asks for one group of runtime permissions ("mic", "phone", "contacts"...); null group asks for the common set. */
    fun requestPermissions(act: Activity, group: String?) {
        val only: List<String>? = when (group) {
            "mic" -> listOf(Manifest.permission.RECORD_AUDIO) +
                (if (Build.VERSION.SDK_INT >= 33) listOf(Manifest.permission.POST_NOTIFICATIONS) else emptyList())
            "phone" -> listOf(Manifest.permission.CALL_PHONE, Manifest.permission.ANSWER_PHONE_CALLS,
                Manifest.permission.READ_PHONE_STATE, Manifest.permission.READ_CALL_LOG)
            "contacts" -> listOf(Manifest.permission.READ_CONTACTS)
            "location" -> listOf(Manifest.permission.ACCESS_COARSE_LOCATION)
            "sms" -> listOf(Manifest.permission.SEND_SMS)
            "calendar" -> listOf(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR)
            "notifications" -> if (Build.VERSION.SDK_INT >= 33) listOf(Manifest.permission.POST_NOTIFICATIONS) else emptyList()
            else -> null
        }
        if (only != null) {
            if (only.isNotEmpty()) act.requestPermissions(only.toTypedArray(), 8)
            return
        }
        val p = mutableListOf(
            Manifest.permission.RECORD_AUDIO, Manifest.permission.CALL_PHONE,
            Manifest.permission.ANSWER_PHONE_CALLS, Manifest.permission.READ_PHONE_STATE,
            Manifest.permission.READ_CALL_LOG, Manifest.permission.READ_CALENDAR,
            Manifest.permission.WRITE_CALENDAR, Manifest.permission.READ_CONTACTS)
        if (Build.VERSION.SDK_INT >= 33) p.add(Manifest.permission.POST_NOTIFICATIONS)
        act.requestPermissions(p.toTypedArray(), 7)
    }

    fun openSettings(act: Activity, page: String) {
        val pkg = Uri.parse("package:${act.packageName}")
        val i = when (page) {
            "overlay" -> Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, pkg)
            "accessibility" -> Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            "notifListener" -> Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
            "dnd" -> Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)
            "writeSettings" -> Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, pkg)
            "battery" -> Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, pkg)
            "exactAlarm" ->
                if (Build.VERSION.SDK_INT >= 31) Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, pkg)
                else Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, pkg)
            else -> Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, pkg)
        }
        act.startActivity(i)
    }

    private fun noOverlay(ctx: Context) = Tr.t(ctx, "Нужно разрешение «Поверх других окон»", "The \"Display over other apps\" permission is required", "\"Boshqa oynalar ustida ko'rsatish\" ruxsati kerak")

    /** Starts the floating mascot; returns an error text or null. */
    fun startBuddy(ctx: Context): String? {
        if (!Settings.canDrawOverlays(ctx)) return noOverlay(ctx)
        ctx.startForegroundService(Intent(ctx, BuddyService::class.java))
        return null
    }

    fun stopBuddy(ctx: Context) { ctx.stopService(Intent(ctx, BuddyService::class.java)) }

    /** Sends a sentence to the mascot as if it had been spoken. */
    fun ask(ctx: Context, text: String): String? {
        if (!Settings.canDrawOverlays(ctx)) return noOverlay(ctx)
        ctx.startForegroundService(Intent(ctx, BuddyService::class.java).setAction(BuddyService.ACTION_ASK).putExtra("text", text))
        return null
    }

    fun openUrl(ctx: Context, url: String): String? {
        if (!url.startsWith("https://")) return Tr.t(ctx, "Только https-ссылки", "Only https links are allowed", "Faqat https havolalar mumkin")
        ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        return null
    }

    /** Moves the mascot ("status_right", "status_left", "top_right"...). The service reacts to the pref. */
    /** Short name of an AI connection spec for summaries: "Groq", "Anthropic"... */
    fun aiTitle(ctx: Context, spec: String): String {
        val s = spec.trim()
        if (s.startsWith("AIza")) return "Google Gemini"
        if (s.startsWith("sk-ant")) return "Anthropic"
        val p = s.split('|')
        if (p.size >= 2) return when {
            p[0].contains("openrouter.ai") -> "OpenRouter"
            p[0].contains("groq.com") -> "Groq"
            else -> Tr.t(ctx, "Свой сервер", "Custom server", "O'z serveringiz")
        }
        return Tr.t(ctx, "ИИ-ключ", "AI key", "AI kaliti")
    }

    fun dockTo(ctx: Context, where: String) {
        val e = prefs(ctx).edit().putString("dock", where + ":" + System.currentTimeMillis())
        if (where.startsWith("status_")) e.putString("status_dock", where) else e.remove("status_dock")
        e.apply()
    }
}

// ---- tasks and calendar ---------------------------------------------------------------------------------------------

fun Backend.tasks(ctx: Context): List<com.example.buddy.Task> = com.example.buddy.TaskStore(ctx).all()

/** Adds a new task (blank id) or updates an existing one, and re-schedules its reminder. */
fun Backend.saveTask(ctx: Context, t: com.example.buddy.Task): com.example.buddy.Task {
    val store = com.example.buddy.TaskStore(ctx)
    val saved = if (t.id.isNotEmpty() && store.all().any { it.id == t.id }) { store.update(t); t }
    else store.add(t.title, t.notes, t.dueMillis, t.remind)
    com.example.buddy.Reminders.cancel(ctx, saved.id)
    if (saved.remind && !saved.done && saved.dueMillis != null) com.example.buddy.Reminders.schedule(ctx, saved)
    return saved
}

fun Backend.deleteTask(ctx: Context, id: String) {
    com.example.buddy.Reminders.cancel(ctx, id)
    com.example.buddy.TaskStore(ctx).delete(id)
}

fun Backend.setTaskDone(ctx: Context, id: String, done: Boolean) {
    val store = com.example.buddy.TaskStore(ctx)
    store.setDone(id, done)
    if (done) com.example.buddy.Reminders.cancel(ctx, id)
    else store.all().firstOrNull { it.id == id }?.let { com.example.buddy.Reminders.schedule(ctx, it) }
}

// ---- games, stickers, agents ------------------------------------------------------------------------------------------

/** Asks the floating mascot to start a game; returns an error text or null. */
fun Backend.startGame(ctx: Context, name: String): String? {
    if (!android.provider.Settings.canDrawOverlays(ctx)) return Tr.t(ctx, "Нужно разрешение «Поверх других окон»", "The \"Display over other apps\" permission is required", "\"Boshqa oynalar ustida ko'rsatish\" ruxsati kerak")
    ctx.startForegroundService(android.content.Intent(ctx, BuddyService::class.java).setAction(BuddyService.ACTION_ASK)
        .putExtra("text", Tr.t(ctx, "Давай сыграем: $name", "Let's play: $name", "Keling, o'ynaymiz: $name")))
    return null
}

class AgentInfo(val paired: Boolean, val desktop: String, val connected: Boolean, val sessions: List<com.example.buddy.AgentSession>)

fun Backend.agentStatus(ctx: Context): AgentInfo {
    val link = com.example.buddy.PairingStore(ctx).load()
    return AgentInfo(link != null, BuddyService.agentDesktop ?: link?.name.orEmpty(), BuddyService.agentConnected, BuddyService.agentSessions)
}

private fun reloadAgents(ctx: Context) {
    if (BuddyService.running)
        ctx.startService(android.content.Intent(ctx, BuddyService::class.java).setAction(BuddyService.ACTION_AGENT_RELOAD))
}

/** Pairs with the computer; returns an error text or null. */
fun Backend.agentPair(ctx: Context, raw: String): String? {
    val link = com.example.buddy.parsePairLink(raw.trim()) ?: return Tr.t(ctx, "Неверная ссылка", "Invalid link", "Havola noto'g'ri")
    try { com.example.buddy.PairingStore(ctx).save(link) } catch (e: Exception) {
        return Tr.t(ctx, "Не удалось сохранить ключ: ${e.message}", "Could not save the key: ${e.message}", "Kalitni saqlab bo'lmadi: ${e.message}")
    }
    reloadAgents(ctx)
    return null
}

fun Backend.agentUnpair(ctx: Context) { com.example.buddy.PairingStore(ctx).clear(); reloadAgents(ctx) }

