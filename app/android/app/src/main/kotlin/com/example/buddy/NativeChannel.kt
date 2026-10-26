package com.example.buddy

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import org.json.JSONArray
import android.os.PowerManager
import android.provider.Settings
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import org.json.JSONObject

class NativeChannel(private val act: Activity) : MethodChannel.MethodCallHandler {
    private val prefs = act.getSharedPreferences("buddy", Context.MODE_PRIVATE)
    private val tasks by lazy { TaskStore(act) }
    private val calendar by lazy { CalendarStore(act) }

    private fun granted(p: String) = act.checkSelfPermission(p) == PackageManager.PERMISSION_GRANTED
    private fun all(vararg p: String) = p.all { granted(it) }

    override fun onMethodCall(call: MethodCall, r: MethodChannel.Result) {
        try { handle(call, r) } catch (e: Exception) { r.error("FAILED", e.message ?: e.toString(), null) }
    }

    private fun handle(call: MethodCall, r: MethodChannel.Result) {
        when (call.method) {
            "getSettings" -> r.success(mapOf(
                "key" to prefs.getString("key", ""),
                "keys" to Brain(act).keyList(),
                "lang" to prefs.getString("lang", "auto"),
                "quiet" to prefs.getBoolean("quiet", false),
                "autohide" to prefs.getBoolean("autohide", true),
                "tts" to prefs.getBoolean("tts", true),
                "conv" to prefs.getBoolean("conv", false),
                "voice" to prefs.getBoolean("voice", true),
                "persona" to prefs.getString("persona", ""),
                "study" to prefs.getBoolean("study", false),
                "musicApp" to prefs.getString("music_app", ""),
                "hotword" to prefs.getBoolean("hotword", true),
                "stickerAuto" to prefs.getBoolean("sticker_auto", true),
                "autostart" to prefs.getBoolean("autostart", true),
                "readNotifs" to prefs.getBoolean("read_notifs", false),
                "lastLang" to prefs.getString("last_lang", ""),
                "musicMood" to prefs.getBoolean("music_mood", true),
                "species" to prefs.getString("species", "cat"),
                "size" to prefs.getFloat("size", 1f).toDouble(),
                "outfit" to prefs.getString("outfit", ""),
                "lastCrash" to prefs.getString("last_crash", ""),
                "messageDelay" to prefs.getInt("message_delay", 4),
                "screenAgent" to prefs.getBoolean("screen_agent", true),
                "deviceReactions" to prefs.getBoolean("device_reactions", true),
                "stickersOnEvents" to prefs.getBoolean("stickers_on_events", true),
                "statusBadge" to prefs.getBoolean("badge", true),
                "dockPlace" to prefs.getString("status_dock", "").orEmpty(),
                "stateGlow" to prefs.getBoolean("state_glow", true),
                "running" to BuddyService.running))
            "saveSettings" -> {
                val e = prefs.edit()
                call.argument<String>("key")?.let {
                    val rest = Brain(act).keyList().drop(1)
                    val list = (if (it.trim().isEmpty()) emptyList() else listOf(it.trim())) + rest
                    e.putString("key", it.trim()).putString("keys_json", JSONArray(list).toString())
                }
                call.argument<List<*>>("keys")?.let { raw ->
                    val list = raw.map { x -> x.toString().trim() }.filter { x -> x.isNotEmpty() }
                    e.putString("keys_json", JSONArray(list).toString()).putString("key", list.firstOrNull().orEmpty())
                }
                call.argument<String>("lang")?.let { e.putString("lang", it) }
                call.argument<String>("persona")?.let { e.putString("persona", it.trim()) }
                call.argument<String>("outfit")?.let { e.putString("outfit", it) }
                call.argument<Double>("size")?.let { e.putFloat("size", it.toFloat().coerceIn(0.15f, 6f)) }
                call.argument<String>("musicApp")?.let { e.putString("music_app", it) }
                call.argument<Number>("messageDelay")?.let { e.putInt("message_delay", it.toInt().coerceIn(0, 30)) }
                call.argument<Boolean>("screenAgent")?.let { e.putBoolean("screen_agent", it) }
                call.argument<Boolean>("deviceReactions")?.let { e.putBoolean("device_reactions", it) }
                call.argument<Boolean>("stickersOnEvents")?.let { e.putBoolean("stickers_on_events", it) }
                call.argument<Boolean>("statusBadge")?.let { e.putBoolean("badge", it) }
                call.argument<String>("dock")?.let { w ->
                    if (w in setOf("status_left", "status_right", "top_right")) {
                        // "where:time": the changing time makes the service act even when the same place is chosen again
                        e.putString("dock", w + ":" + System.currentTimeMillis())
                        if (w.startsWith("status_")) e.putString("status_dock", w) else e.remove("status_dock")
                    }
                }
                call.argument<Boolean>("stateGlow")?.let { e.putBoolean("state_glow", it) }
                call.argument<Boolean>("hotword")?.let { e.putBoolean("hotword", it) }
                call.argument<Boolean>("stickerAuto")?.let { e.putBoolean("sticker_auto", it) }
                call.argument<Boolean>("autostart")?.let { e.putBoolean("autostart", it) }
                call.argument<Boolean>("readNotifs")?.let { e.putBoolean("read_notifs", it) }
                call.argument<Boolean>("musicMood")?.let { e.putBoolean("music_mood", it) }
                call.argument<String>("species")?.let { e.putString("species", Species.normalize(it)) }
                for (b in listOf("tts", "conv", "voice", "study", "quiet", "autohide")) call.argument<Boolean>(b)?.let { e.putBoolean(b, it) }
                e.apply(); r.success(true)
            }
            "permissionsStatus" -> r.success(mapOf(
                "overlay" to Settings.canDrawOverlays(act),
                "accessibility" to BuddyAccess.enabled,
                "mic" to granted(Manifest.permission.RECORD_AUDIO),
                "phone" to all(Manifest.permission.CALL_PHONE, Manifest.permission.ANSWER_PHONE_CALLS, Manifest.permission.READ_PHONE_STATE),
                "calendar" to calendar.hasPermission(),
                "notifications" to (Build.VERSION.SDK_INT < 33 || granted(Manifest.permission.POST_NOTIFICATIONS)),
                "contacts" to granted(Manifest.permission.READ_CONTACTS),
                "location" to granted(Manifest.permission.ACCESS_COARSE_LOCATION),
                "sms" to granted(Manifest.permission.SEND_SMS),
                "exactAlarm" to Reminders.canExact(act),
                "notifListener" to (Settings.Secure.getString(act.contentResolver, "enabled_notification_listeners") ?: "").contains(act.packageName),
                "dnd" to (act.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager).isNotificationPolicyAccessGranted,
                "writeSettings" to Settings.System.canWrite(act)))
            "requestPermissions" -> {
                val only: List<String>? = when (call.argument<String>("group")) {
                    // one request for both: Android drops a second permission request fired while the first is open
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
                    r.success(true); return
                }
                val p = mutableListOf(
                    Manifest.permission.RECORD_AUDIO, Manifest.permission.CALL_PHONE,
                    Manifest.permission.ANSWER_PHONE_CALLS, Manifest.permission.READ_PHONE_STATE,
                    Manifest.permission.READ_CALL_LOG, Manifest.permission.READ_CALENDAR,
                    Manifest.permission.WRITE_CALENDAR, Manifest.permission.READ_CONTACTS)
                if (Build.VERSION.SDK_INT >= 33) p.add(Manifest.permission.POST_NOTIFICATIONS)
                act.requestPermissions(p.toTypedArray(), 7)
                r.success(true)
            }
            "openSettings" -> {
                val pkg = Uri.parse("package:${act.packageName}")
                val i = when (call.argument<String>("page")) {
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
                act.startActivity(i); r.success(true)
            }
            "startBuddy" -> {
                if (!Settings.canDrawOverlays(act)) { r.error("NO_OVERLAY", Tr.t(act, "Нужно разрешение «Поверх других окон»", "The \"Display over other apps\" permission is required", "\"Boshqa ilovalar ustidan ko'rsatish\" ruxsati kerak"), null); return }
                act.startForegroundService(Intent(act, BuddyService::class.java)); r.success(true)
            }
            "stopBuddy" -> { act.stopService(Intent(act, BuddyService::class.java)); r.success(true) }
            "listModels" -> {
                val base = call.argument<String>("base").orEmpty().trim().trimEnd('/')
                val key = call.argument<String>("key").orEmpty().trim()
                if (!base.startsWith("https://")) { r.error("FAILED", Tr.t(act, "Только https-адрес", "Only https addresses are allowed", "Faqat https manzil mumkin"), null); return }
                val main = Handler(Looper.getMainLooper())
                Thread {
                    val res: Any = try {
                        val c = java.net.URL("$base/v1/models").openConnection() as java.net.HttpURLConnection
                        c.connectTimeout = 15000; c.readTimeout = 20000
                        if (key.isNotEmpty()) c.setRequestProperty("Authorization", "Bearer $key")
                        val code = c.responseCode
                        val body = (if (code in 200..299) c.inputStream else (c.errorStream ?: c.inputStream)).bufferedReader().use { it.readText() }
                        if (code !in 200..299) mapOf("ok" to false, "message" to "HTTP $code")
                        else {
                            val arr = org.json.JSONObject(body).getJSONArray("data")
                            val skip = Regex("whisper|guard|orpheus|tts|embed|moderation|safeguard|allam|transcri", RegexOption.IGNORE_CASE)
                            val ids = (0 until arr.length()).map { arr.getJSONObject(it).optString("id") }
                                .filter { it.isNotEmpty() && !skip.containsMatchIn(it) }
                            mapOf("ok" to true, "models" to ids)
                        }
                    } catch (e: Exception) { mapOf("ok" to false, "message" to (e.message ?: Tr.t(act, "ошибка сети", "network error", "tarmoq xatosi"))) }
                    main.post { r.success(res) }
                }.start()
            }
            "openUrl" -> {
                val u = call.argument<String>("url").orEmpty()
                if (!u.startsWith("https://")) { r.error("FAILED", Tr.t(act, "Только https-ссылки", "Only https links are allowed", "Faqat https havolalar mumkin"), null); return }
                act.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(u))); r.success(true)
            }
            "testAi" -> {
                val spec = call.argument<String>("spec").orEmpty()
                val main = Handler(Looper.getMainLooper())
                Thread {
                    val res: Map<String, Any> = try {
                        Brain(act).ping(spec)
                        mapOf("ok" to true, "message" to Tr.t(act, "Подключение работает", "Connection works", "Ulanish ishlayapti"))
                    } catch (e: Exception) {
                        val m = e.message ?: Tr.t(act, "ошибка", "error", "xato")
                        mapOf("ok" to false, "message" to when {
                            m.startsWith("HTTP 401") || m.startsWith("HTTP 403") -> Tr.t(act, "Ключ не подходит ($m)", "The key does not work ($m)", "Kalit mos kelmadi ($m)")
                            m.startsWith("HTTP 429") || m.contains("лимит") -> Tr.t(act, "Лимит этого ключа исчерпан ($m)", "This key's limit is used up ($m)", "Bu kalit limiti tugadi ($m)")
                            m.startsWith("HTTP 404") -> Tr.t(act, "Модель не найдена: проверьте название ($m)", "Model not found: check the name ($m)", "Model topilmadi: nomini tekshiring ($m)")
                            else -> m
                        })
                    }
                    main.post { r.success(res) }
                }.start()
            }
            "ask" -> {
                if (!Settings.canDrawOverlays(act)) { r.error("NO_OVERLAY", Tr.t(act, "Нужно разрешение «Поверх других окон»", "The \"Display over other apps\" permission is required", "\"Boshqa ilovalar ustidan ko'rsatish\" ruxsati kerak"), null); return }
                act.startForegroundService(Intent(act, BuddyService::class.java)
                    .setAction(BuddyService.ACTION_ASK).putExtra("text", call.argument<String>("text").orEmpty()))
                r.success(true)
            }
            "dockTo" -> {
                val w = call.argument<String>("where").orEmpty()
                prefs.edit().putString("dock", w + ":" + System.currentTimeMillis()).apply(); r.success(true)
            }
            "musicApps" -> r.success(MusicApps.status(act))
            "testMusic" -> {
                val err = Actions(act).run(JSONObject().put("do", "music").put("q", call.argument<String>("q").orEmpty()))
                if (err == null) r.success(true) else r.error("FAILED", err, null)
            }
            "renderPreview" -> {
                val mood = call.argument<String>("mood") ?: "happy"
                // drawing the mascot takes a moment: do it off the UI thread so the screen stays smooth
                Thread {
                    val out = try { Stickers.previewPng(act, mood) } catch (e: Throwable) { null }
                    android.os.Handler(android.os.Looper.getMainLooper()).post {
                        if (out != null) r.success(out) else r.error("FAILED", "preview failed", null)
                    }
                }.start()
            }
            "renderSticker" -> r.success(Stickers.png(act,
                call.argument<String>("mood") ?: "happy", call.argument<String>("caption").orEmpty()))
            "shareSticker" -> {
                val err = Stickers.share(act, call.argument<String>("mood") ?: "happy", call.argument<String>("caption").orEmpty())
                if (err == null) r.success(true) else r.error("FAILED", err, null)
            }
            "startGame" -> {
                if (!Settings.canDrawOverlays(act)) { r.error("NO_OVERLAY", Tr.t(act, "Нужно разрешение «Поверх других окон»", "The \"Display over other apps\" permission is required", "\"Boshqa ilovalar ustidan ko'rsatish\" ruxsati kerak"), null); return }
                val name = call.argument<String>("name").orEmpty()
                act.startForegroundService(Intent(act, BuddyService::class.java)
                    .setAction(BuddyService.ACTION_ASK).putExtra("text", Tr.t(act, "Давай сыграем: $name", "Let's play: $name", "Keling, o'ynaymiz: $name")))
                r.success(true)
            }
            "batteryStatus" -> r.success(mapOf("ignoring" to
                (act.getSystemService(Context.POWER_SERVICE) as PowerManager).isIgnoringBatteryOptimizations(act.packageName)))
            "agentPair" -> {
                val link = parsePairLink(call.argument<String>("link").orEmpty().trim())
                if (link == null) { r.success(mapOf("ok" to false, "error" to Tr.t(act, "Неверная ссылка", "Invalid link", "Havola noto'g'ri"))); return }
                try { PairingStore(act).save(link) } catch (e: Exception) {
                    r.success(mapOf("ok" to false, "error" to Tr.t(act, "Не удалось сохранить ключ: ${e.message}", "Could not save the key: ${e.message}", "Kalitni saqlab bo'lmadi: ${e.message}"))); return
                }
                reloadAgents()
                r.success(mapOf("ok" to true, "name" to link.name))
            }
            "agentUnpair" -> { PairingStore(act).clear(); reloadAgents(); r.success(true) }
            "agentStatus" -> {
                val link = PairingStore(act).load()
                r.success(mapOf(
                    "paired" to (link != null),
                    "desktop" to (BuddyService.agentDesktop ?: link?.name),
                    "connected" to BuddyService.agentConnected,
                    "sessions" to BuddyService.agentSessions.map { mapOf(
                        "pillId" to it.pillId, "agent" to it.agent, "state" to it.state,
                        "statusText" to it.statusText, "stepIndex" to it.stepIndex, "stepCount" to it.stepCount) }))
            }
            "listTasks" -> r.success(tasks.all().map { toMap(tasks.toJson(it)) })
            "saveTask" -> {
                val j = JSONObject(call.arguments as Map<*, *>)
                val t = tasks.fromJson(j)
                val saved = if (tasks.all().any { it.id == t.id }) { tasks.update(t); t }
                else tasks.add(t.title, t.notes, t.dueMillis, t.remind)
                Reminders.cancel(act, saved.id)
                if (saved.remind && !saved.done && saved.dueMillis != null) Reminders.schedule(act, saved)
                r.success(toMap(tasks.toJson(saved)))
            }
            "deleteTask" -> {
                val id = call.argument<String>("id").orEmpty()
                Reminders.cancel(act, id); tasks.delete(id); r.success(true)
            }
            "setTaskDone" -> {
                val id = call.argument<String>("id").orEmpty()
                val done = call.argument<Boolean>("done") ?: true
                tasks.setDone(id, done)
                if (done) Reminders.cancel(act, id)
                else tasks.all().firstOrNull { it.id == id }?.let { Reminders.schedule(act, it) }
                r.success(true)
            }
            "listEvents" -> {
                val from = call.argument<Number>("from")?.toLong() ?: 0L
                val to = call.argument<Number>("to")?.toLong() ?: Long.MAX_VALUE
                r.success(calendar.events(from, to).map { toMap(calendar.toJson(it)) })
            }
            "addEvent" -> {
                val s = call.argument<Number>("startMillis")?.toLong() ?: 0L
                val e = call.argument<Number>("endMillis")?.toLong() ?: (s + 3600_000L)
                val id = calendar.add(call.argument<String>("title").orEmpty(), s, e,
                    call.argument<String>("location").orEmpty(), call.argument<String>("notes").orEmpty())
                if (id == null) r.error("FAILED", Tr.t(act, "Не удалось добавить событие (нет доступа к календарю?)", "Could not add the event (no calendar access?)", "Tadbirni qo'shib bo'lmadi (kalendarga ruxsat yo'qmi?)"), null)
                else r.success(mapOf("id" to id))
            }
            "deleteEvent" -> r.success(calendar.delete(call.argument<Number>("id")?.toLong() ?: -1L))
            else -> r.notImplemented()
        }
    }

    private fun reloadAgents() {
        if (BuddyService.running)
            act.startService(Intent(act, BuddyService::class.java).setAction(BuddyService.ACTION_AGENT_RELOAD))
    }

    private fun toMap(j: JSONObject): Map<String, Any?> =
        j.keys().asSequence().associateWith { k -> if (j.isNull(k)) null else j.get(k) }
}
