package com.example.buddy

import android.Manifest
import android.accessibilityservice.AccessibilityService
import android.app.SearchManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.AlarmClock
import android.provider.ContactsContract
import android.provider.MediaStore
import android.provider.Settings
import android.telephony.SmsManager
import android.telecom.TelecomManager
import android.view.KeyEvent
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Locale

class Actions(private val ctx: Context) {
    private val prefs get() = ctx.getSharedPreferences("buddy", Context.MODE_PRIVATE)
    private val main = Handler(Looper.getMainLooper())

    companion object {
        /** Called on the main thread when a background step (video lookup etc.) fails. Set by BuddyService. */
        @Volatile var onAsyncError: ((String) -> Unit)? = null

        /** Called on the main thread when the user must choose (prompt + options: label to action). Set by BuddyService. */
        @Volatile var onChoice: ((String, List<Pair<String, JSONObject>>) -> Unit)? = null

        /** Main thread: (prompt, seconds, run). The UI shows a cancel countdown and calls run() when it ends. Set by BuddyService. */
        @Volatile var onPending: ((String, Int, () -> Unit) -> Unit)? = null

        /** SystemClock.elapsedRealtime() of the last message send (0 = never). */
        @Volatile private var lastSendMs = 0L
    }

    private fun choice(prompt: String, options: List<Pair<String, JSONObject>>) {
        main.post {
            try {
                val cb = onChoice
                if (cb != null) cb(prompt, options) else onAsyncError?.invoke(prompt)
            } catch (_: Exception) { }
        }
    }

    private fun asyncError(msg: String) {
        main.post { try { onAsyncError?.invoke(msg) } catch (_: Exception) { } }
    }

    fun run(a: JSONObject): String? {
        return try {
            exec(a)
        } catch (e: SecurityException) {
            tr("Нет нужного разрешения", "Missing permission", "Kerakli ruxsat yo'q")
        } catch (e: ActivityNotFoundException) {
            tr("Не нашёл подходящее приложение", "No suitable app found", "Mos ilova topilmadi")
        } catch (e: Exception) {
            tr("Не получилось выполнить команду", "Could not run the command", "Buyruqni bajarib bo'lmadi")
        }
    }

    private fun tr(ru: String, en: String, uz: String) = Tr.t(ctx, ru, en, uz)

    private fun s(a: JSONObject, k: String): String = if (a.isNull(k)) "" else a.optString(k, "").trim()

    private fun exec(a: JSONObject): String? {
        return when (val op = s(a, "do")) {
            "alarm", "alarm_list", "alarm_cancel", "timer", "timer_cancel", "timer_list" -> ClockActions.exec(ctx, a)
            "noop" -> null
            "search" -> {
                val q = s(a, "q"); if (q.isEmpty()) return tr("Пустой запрос", "Empty query", "So'rov bo'sh")
                view("https://www.google.com/search?q=" + enc(q))
            }
            "music" -> music(s(a, "q"), s(a, "app"))
            "video" -> video(s(a, "q"))
            "message" -> message(a)
            "screenshot" -> {
                if (Build.VERSION.SDK_INT < 28) tr("Скриншот не поддерживается", "Screenshots are not supported", "Skrinshot qo'llab-quvvatlanmaydi")
                else global(AccessibilityService.GLOBAL_ACTION_TAKE_SCREENSHOT)
            }
            "power_menu" -> global(AccessibilityService.GLOBAL_ACTION_POWER_DIALOG)
            "brightness" -> brightness(a)
            "media" -> media(s(a, "key"), a.optInt("seconds", 10))
            "open_app" -> openApp(s(a, "name"))
            "open_url" -> {
                var u = s(a, "url"); if (u.isEmpty()) return tr("Пустая ссылка", "Empty link", "Havola bo'sh")
                if (!u.contains("://")) u = "https://$u"
                view(u)
            }
            "maps" -> {
                val q = s(a, "q"); if (q.isEmpty()) return tr("Пустой запрос", "Empty query", "So'rov bo'sh")
                view("geo:0,0?q=" + enc(q))
            }
            "call" -> call(s(a, "to"))
            "answer_call" -> answerCall()
            "end_call" -> endCall()
            "speaker" -> {
                val am = ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager
                am.isSpeakerphoneOn = a.optBoolean("on", true)
                null
            }
            "volume" -> volume(a)
            "flashlight" -> flashlight(a.optBoolean("on", true))
            "reject_call" -> rejectCall(s(a, "text"))
            "email" -> email(a)
            "auto_reply" -> AutoReply.set(ctx, a.optBoolean("on", true), s(a, "mode").lowercase(), a.optInt("hours", 2), s(a, "text"))
            "dnd" -> PhoneControls.dnd(ctx, a.optBoolean("on", true))
            "screen_timeout" -> PhoneControls.screenTimeout(ctx, a.optInt("seconds", 0))
            "find_phone" -> PhoneControls.findPhone(ctx, a.optBoolean("on", true))
            "back" -> global(AccessibilityService.GLOBAL_ACTION_BACK)
            "home" -> global(AccessibilityService.GLOBAL_ACTION_HOME)
            "recents" -> global(AccessibilityService.GLOBAL_ACTION_RECENTS)
            "notifications", "notification_shade" -> global(AccessibilityService.GLOBAL_ACTION_NOTIFICATIONS)
            "quick_settings" -> global(AccessibilityService.GLOBAL_ACTION_QUICK_SETTINGS)
            "lock" -> {
                if (Build.VERSION.SDK_INT < 28) tr("Блокировка экрана не поддерживается", "Screen lock is not supported", "Ekranni qulflash qo'llab-quvvatlanmaydi")
                else global(AccessibilityService.GLOBAL_ACTION_LOCK_SCREEN)
            }
            "scroll" -> {
                needAccess()?.let { return it }
                if (BuddyAccess.swipe(s(a, "dir").lowercase())) null else tr("Не получилось прокрутить", "Could not scroll", "Aylantirib bo'lmadi")
            }
            "tap_text" -> {
                needAccess()?.let { return it }
                val t = s(a, "text")
                if (t.isNotEmpty() && BuddyAccess.clickText(t)) null else tr("Не нашёл на экране: $t", "Not found on screen: $t", "Ekranda topilmadi: $t")
            }
            "type_text" -> {
                needAccess()?.let { return it }
                val t = s(a, "text")
                if (t.isNotEmpty() && BuddyAccess.typeText(t, a.optBoolean("submit", false))) null else tr("Нет поля для ввода", "No input field", "Kiritish maydoni yo'q")
            }
            "tap_node" -> {
                needAccess()?.let { return it }
                val i = a.optInt("index", -1)
                if (i >= 1 && BuddyAccess.tapNode(i)) null else tr("Не получилось нажать элемент", "Could not tap that item", "Elementni bosib bo'lmadi")
            }
            "press_enter" -> {
                needAccess()?.let { return it }
                if (BuddyAccess.pressEnter()) null else tr("Не получилось нажать Enter", "Could not press Enter", "Enter bosib bo'lmadi")
            }
            "list_add" -> listAdd(a)
            "list_remove" -> {
                val item = s(a, "item")
                if (item.isEmpty()) tr("Что убрать?", "What should I remove?", "Nimani o'chiray?")
                else if (ListStore.remove(ctx, s(a, "list"), item)) null
                else tr("Не нашёл в списке: $item", "Not in the list: $item", "Ro'yxatda yo'q: $item")
            }
            "list_clear" -> { ListStore.clear(ctx, s(a, "list")); null }
            "notification_reply" -> notificationReply(s(a, "text"))
            "settings" -> settings(s(a, "panel").lowercase())
            "add_task" -> addTask(a)
            "done_task" -> {
                val store = TaskStore(ctx)
                val t = store.findByTitle(s(a, "title")) ?: return tr("Задача не найдена", "Task not found", "Vazifa topilmadi")
                store.setDone(t.id, true)
                Reminders.cancel(ctx, t.id)
                null
            }
            "delete_task" -> {
                val store = TaskStore(ctx)
                val q = s(a, "title")
                val t = store.findByTitle(q) ?: return tr("Задача не найдена", "Task not found", "Vazifa topilmadi")
                if (!t.title.trim().equals(q, ignoreCase = true)) {
                    // not an exact match: never delete on a guess
                    choice(
                        tr("Удалить «${t.title}»?", "Delete \"${t.title}\"?", "«${t.title}» o'chirilsinmi?"),
                        listOf(
                            tr("Удалить", "Delete", "O'chirish") to JSONObject().put("do", "delete_task").put("title", t.title),
                            tr("Отмена", "Cancel", "Bekor qilish") to JSONObject().put("do", "noop")
                        )
                    )
                    return null
                }
                store.delete(t.id)
                Reminders.cancel(ctx, t.id)
                null
            }
            "add_event" -> addEvent(a)
            "set_persona" -> {
                val t = s(a, "text")
                val e = prefs.edit()
                if (t.isEmpty()) e.remove("persona") else e.putString("persona", t)
                e.apply()
                null
            }
            "species" -> {
                val n = s(a, "name").lowercase()
                if (n !in Species.ALL) tr("Такого питомца нет", "No such pet", "Bunday uy hayvoni yo'q")
                else { prefs.edit().putString("species", n).apply(); null }
            }
            // Bubby lives in the status bar next to the clock; its place and size are fixed
            "size", "move" -> tr("Моё место у часов, его не меняют", "My place is next to the clock and it does not change", "Mening joyim soat yonida, u o'zgarmaydi")
            "quiet" -> {
                prefs.edit().putBoolean("quiet", a.optBoolean("on", true)).apply()
                null
            }
            "set_mode" -> {
                prefs.edit().putBoolean("study", s(a, "mode") == "study").apply()
                null
            }
            "outfit" -> outfit(a)
            "sticker" -> {
                val m = s(a, "mood").ifEmpty { "happy" }
                Stickers.share(ctx, m, s(a, "caption"))
            }
            "game" -> game(a)
            else -> tr("Не знаю команду: $op", "Unknown command: $op", "Noma'lum buyruq: $op")
        }
    }

    private fun outfit(a: JSONObject): String? {
        val item = s(a, "item").lowercase()
        if (item.isEmpty()) return tr("Такого наряда нет", "No such outfit", "Bunday kiyim yo'q")
        if (item == "none") {
            prefs.edit().remove("outfit").apply()
            return null
        }
        Outfits.PRESETS[item]?.let { set ->
            if (a.optBoolean("on", true)) prefs.edit().putString("outfit", set.joinToString(",")).apply()
            else prefs.edit().remove("outfit").apply()
            return null
        }
        val slot = Outfits.SLOTS.entries.firstOrNull { item in it.value }?.key ?: return tr("Такого наряда нет", "No such outfit", "Bunday kiyim yo'q")
        val items = Outfits.parse(prefs.getString("outfit", "") ?: "").toMutableSet()
        val same = Outfits.SLOTS[slot] ?: emptyList()
        items.removeAll(same.toSet())
        if (a.optBoolean("on", true)) items.add(item)
        prefs.edit().putString("outfit", items.joinToString(",")).apply()
        return null
    }

    private fun game(a: JSONObject): String? {
        val st = s(a, "state")
        val e = prefs.edit()
        if (st.isEmpty()) {
            e.remove("game_state").remove("game_chips")
        } else {
            val arr = org.json.JSONArray()
            val ch = a.optJSONArray("chips")
            if (ch != null) for (i in 0 until minOf(ch.length(), 6)) {
                val c = ch.optString(i, "").trim()
                if (c.isNotEmpty()) arr.put(c)
            }
            e.putString("game_state", st).putString("game_chips", arr.toString()).putLong("game_ts", System.currentTimeMillis())
        }
        e.apply()
        return null
    }

    private fun enc(q: String) = URLEncoder.encode(q, "UTF-8")

    private fun start(i: Intent) {
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        ctx.startActivity(i)
    }

    private fun view(url: String): String? {
        start(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        return null
    }

    private fun granted(p: String) = ctx.checkSelfPermission(p) == PackageManager.PERMISSION_GRANTED

    private fun needAccess(): String? =
        if (BuddyAccess.enabled) null else tr("Включи службу специальных возможностей", "Turn on the accessibility service", "Maxsus imkoniyatlar xizmatini yoqing")

    private fun global(action: Int): String? {
        needAccess()?.let { return it }
        return if (BuddyAccess.global(action)) null else tr("Не получилось выполнить", "Could not do that", "Bajarib bo'lmadi")
    }

    private val YT = "com.google.android.youtube"
    private val YTM = "com.google.android.apps.youtube.music"

    private fun playFromSearch(q: String, pkg: String?): Intent =
        Intent(MediaStore.INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH)
            .putExtra(MediaStore.EXTRA_MEDIA_FOCUS, "vnd.android.cursor.item/*")
            .putExtra(SearchManager.QUERY, q).apply { if (pkg != null) setPackage(pkg) }

    /** Maps a package id or a spoken app name to an installed package id (null when unknown / not installed). */
    private fun pickApp(x: String): String? {
        if (x.isEmpty()) return null
        if (MusicApps.installed(ctx, x)) return x
        val n = norm(x).replace("ютуб", "youtube").replace("спотифай", "spotify")
        val hit = MusicApps.ALL.firstOrNull { norm(it.label) == n }
            ?: MusicApps.ALL.firstOrNull { n.length >= 3 && (norm(it.label).contains(n) || it.id.contains(n)) }
        return hit?.id?.takeIf { MusicApps.installed(ctx, it) }
    }

    /** First video id for a query, or null. Blocking network: call off the main thread only. */
    private fun resolveVideoId(q: String): String? {
        var conn: HttpURLConnection? = null
        return try {
            conn = URL("https://www.youtube.com/results?search_query=" + enc(q) + "&sp=EgIQAQ%253D%253D").openConnection() as HttpURLConnection
            conn.connectTimeout = 8000
            conn.readTimeout = 8000
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36")
            conn.setRequestProperty("Accept-Language", "en")
            conn.setRequestProperty("Cookie", "CONSENT=YES+1; SOCS=CAI")
            if (conn.responseCode !in 200..299) return null
            val buf = java.io.ByteArrayOutputStream()
            conn.inputStream.use { ins ->
                val chunk = ByteArray(16384)
                while (buf.size() < 3_000_000) {
                    val n = ins.read(chunk)
                    if (n < 0) break
                    buf.write(chunk, 0, n)
                }
            }
            val html = buf.toString("UTF-8")
            Regex("\"videoRenderer\":\\{\"videoId\":\"([A-Za-z0-9_-]{11})\"").find(html)?.groupValues?.get(1)
                ?: Regex("\"videoId\":\"([A-Za-z0-9_-]{11})\"").find(html)?.groupValues?.get(1)
        } catch (_: Exception) {
            null
        } finally {
            try { conn?.disconnect() } catch (_: Exception) { }
        }
    }

    private fun lookup(q: String, then: (String?) -> Unit) {
        Thread {
            val id = try { resolveVideoId(q) } catch (_: Throwable) { null }
            main.post { try { then(id) } catch (_: Exception) { } }
        }.start()
    }

    private fun video(q: String): String? {
        if (q.isEmpty()) return tr("Что показать?", "What should I show?", "Nimani ko'rsatay?")
        lookup(q) { id ->
            try {
                if (id != null) {
                    if (MusicApps.installed(ctx, YT)) {
                        try { start(Intent(Intent.ACTION_VIEW, Uri.parse("vnd.youtube:$id")).setPackage(YT)); return@lookup }
                        catch (_: ActivityNotFoundException) { }
                    }
                    start(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/watch?v=$id")))
                } else {
                    view("https://www.youtube.com/results?search_query=" + enc(q))
                }
            } catch (_: Exception) {
                asyncError(tr("Не получилось открыть видео", "Could not open the video", "Videoni ochib bo'lmadi"))
            }
        }
        return null
    }

    private fun music(q: String, app: String): String? {
        if (q.isEmpty()) return tr("Что включить?", "What should I play?", "Nimani yoqay?")
        val raw = app.ifEmpty { prefs.getString("music_app", "") ?: "" }
        val chosen = pickApp(raw)
        val ytm = MusicApps.installed(ctx, YTM)
        val useYt = chosen == YT || chosen == YTM || (chosen == null && raw.isEmpty() && ytm)
        if (chosen != null && !useYt) {
            // Spotify or another installed app: hand it the search, then nudge Play once
            try {
                start(playFromSearch(q, chosen))
                if (BuddyAccess.enabled) main.postDelayed({
                    for (t in listOf("Play", "Воспроизвести", "Ijro etish")) if (BuddyAccess.clickText(t)) break
                }, 2500)
                return null
            } catch (_: ActivityNotFoundException) { }
        }
        if (!useYt) { fallbackMusic(q, chosen); return null }
        lookup(q) { id ->
            try {
                if (id == null) {
                    fallbackMusic(q, chosen)
                } else {
                    if (ytm) {
                        try { start(Intent(Intent.ACTION_VIEW, Uri.parse("https://music.youtube.com/watch?v=$id")).setPackage(YTM)); return@lookup }
                        catch (_: ActivityNotFoundException) { }
                    }
                    val w = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/watch?v=$id"))
                    if (MusicApps.installed(ctx, YT)) w.setPackage(YT)
                    try { start(w) } catch (_: ActivityNotFoundException) {
                        start(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/watch?v=$id")))
                    }
                }
            } catch (_: Exception) {
                asyncError(tr("Не получилось включить музыку", "Could not play the music", "Musiqani yoqib bo'lmadi"))
            }
        }
        return null
    }

    private fun fallbackMusic(q: String, pkg: String?) {
        try {
            start(playFromSearch(q, pkg))
        } catch (_: ActivityNotFoundException) {
            try { start(playFromSearch(q, null)) } catch (_: ActivityNotFoundException) {
                view("https://www.youtube.com/results?search_query=" + enc(q))
            }
        }
    }

    private val WA_PKGS = listOf("com.whatsapp", "com.whatsapp.w4b")
    private val TG_PKGS = listOf("org.telegram.messenger", "org.telegram.messenger.web")
    private val SEND_LABELS = listOf("Send", "Отправить", "Yuborish")

    private fun pressSendMsg() = tr(
        "Я открыл чат, нажмите «Отправить»", "I opened the chat, press send", "Chatni ochdim, «Yuborish» tugmasini bosing")

    /** Sends [text] by itself (after a cancel countdown). Resolves the recipient off the main thread. */
    private fun message(a: JSONObject): String? {
        val app = s(a, "app").lowercase().ifEmpty { "sms" }
        val text = s(a, "text")
        val to = s(a, "to")
        val force = a.optInt("_force_delay", 0).coerceIn(0, 30)
        val alive = onPending != null   // liveness of the service, captured at call time
        if (text.isEmpty()) return tr("Что отправить?", "What should I send?", "Nimani yuboray?")
        if (to.isEmpty()) return tr("Кому отправить?", "Who should I send it to?", "Kimga yuboray?")
        if (Contacts.isNumber(to)) {
            val name = s(a, "name")
            proceed(app, Contacts.normalizeNumber(to), name.ifEmpty { to }, text, force, alive)
            return null
        }
        if (!granted(Manifest.permission.READ_CONTACTS)) return tr("Нет доступа к контактам", "No access to contacts", "Kontaktlarga ruxsat yo'q")
        Thread {
            val c = try { Contacts.resolve(ctx, to, 4) } catch (_: Throwable) { emptyList() }
            main.post {
                try {
                    if (c.isEmpty()) {
                        asyncError(tr("Не нашёл контакт: $to", "Contact not found: $to", "Kontakt topilmadi: $to"))
                    } else {
                        val top = c[0]
                        val clear = top.score >= 80 && (c.size == 1 || top.score - c[1].score >= 10)
                        if (clear) {
                            proceed(app, top.number, top.name, text, force, alive)
                        } else {
                            choice(
                                tr("Кому отправить?", "Who should I send it to?", "Kimga yuboray?"),
                                c.map {
                                    "${it.name} (${it.label})" to JSONObject().put("do", "message").put("app", app)
                                        .put("to", it.number).put("name", it.name).put("text", text).put("_force_delay", force)
                                }
                            )
                        }
                    }
                } catch (_: Exception) {
                    asyncError(tr("Не получилось отправить", "Could not send the message", "Xabarni yuborib bo'lmadi"))
                }
            }
        }.start()
        return null
    }

    /** Declines the ringing call and texts the caller at once (the user asked for exactly this in one sentence). */
    private fun rejectCall(text: String): String? {
        val number = CallWatcher.lastNumber
        val fresh = System.currentTimeMillis() - CallWatcher.lastRingMs < 120_000L
        endCall()?.let { return it }
        if (number.isNullOrEmpty() || !fresh)
            return tr("Звонок сброшен, но номер звонившего неизвестен: сообщение не отправлено", "Call declined, but the caller's number is unknown: no message sent", "Qo'ng'iroq rad etildi, lekin raqam noma'lum: xabar yuborilmadi")
        if (!granted(Manifest.permission.SEND_SMS)) return tr("Нет разрешения отправлять SMS", "No permission to send SMS", "SMS yuborishga ruxsat yo'q")
        val msg = text.trim().take(300).ifEmpty { AutoReply.defaultText(ctx, "busy") }
        main.post { sendNow("sms", Contacts.normalizeNumber(number), msg) }
        return null
    }

    /** Opens a ready email draft in the mail app; the user reads it and taps Send (Android cannot send email silently). */
    private fun email(a: JSONObject): String? {
        val to = s(a, "to")
        val subject = s(a, "subject")
        val body = s(a, "body")
        if (to.isEmpty() || to.contains('@')) { composeEmail(to, subject, body); return null }
        if (!granted(Manifest.permission.READ_CONTACTS)) return tr("Нет доступа к контактам", "No access to contacts", "Kontaktlarga ruxsat yo'q")
        Thread {
            val c = try { Mail.find(ctx, to) } catch (_: Throwable) { emptyList() }
            main.post {
                try {
                    when {
                        c.isEmpty() -> asyncError(tr("Не нашёл почту контакта: $to", "No email address found for $to", "$to uchun email topilmadi"))
                        c.size == 1 || c[0].score - c[1].score >= 10 -> composeEmail(c[0].address, subject, body)
                        else -> choice(
                            tr("Кому написать?", "Who should I write to?", "Kimga yozay?"),
                            c.map {
                                "${it.name} (${it.address})" to JSONObject().put("do", "email").put("to", it.address)
                                    .put("subject", subject).put("body", body)
                            }
                        )
                    }
                } catch (_: Exception) {
                    asyncError(tr("Не получилось открыть почту", "Could not open the mail app", "Pochta ilovasini ochib bo'lmadi"))
                }
            }
        }.start()
        return null
    }

    private fun composeEmail(to: String, subject: String, body: String) {
        val i = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:")).apply {
            if (to.isNotEmpty()) putExtra(Intent.EXTRA_EMAIL, arrayOf(to))
            putExtra(Intent.EXTRA_SUBJECT, subject)
            putExtra(Intent.EXTRA_TEXT, body)
        }
        start(i)
    }

    /** Main thread. Rate limit, then countdown (if configured) and send. */
    private fun proceed(app: String, number: String, name: String, text: String, force: Int = 0, aliveAtCall: Boolean = true) {
        if (rateLimited()) { asyncError(rateMsg()); return }
        val delay = maxOf(prefs.getInt("message_delay", 4).coerceIn(0, 30), force)
        val cb = onPending
        // service gone (or never there): never send without the cancel UI being able to run
        if (cb == null || !aliveAtCall) {
            asyncError(tr("Сообщение не отправлено", "Message was not sent", "Xabar yuborilmadi"))
            return
        }
        if (delay <= 0) { sendNow(app, number, text); return }
        val shown = if (text.length > 80) text.take(80) + "…" else text
        val prompt = tr("Отправляю $name: «$shown»", "Sending to $name: \"$shown\"", "${name}ga yuboryapman: \"$shown\"")
        cb(prompt, delay, { sendNow(app, number, text) })
    }

    private fun rateLimited(): Boolean {
        val last = lastSendMs
        return last != 0L && android.os.SystemClock.elapsedRealtime() - last < 5000L
    }

    private fun rateMsg() = tr("Слишком часто, подождите несколько секунд", "Too fast, wait a few seconds", "Juda tez, bir necha soniya kuting")

    /** Main thread. Actually sends. Never reports success unless the send step really happened. */
    private fun sendNow(app: String, number: String, text: String) {
        try {
            if (rateLimited()) { asyncError(rateMsg()); return }
            lastSendMs = android.os.SystemClock.elapsedRealtime()
            when {
                app.contains("whats") -> sendWhatsApp(number, text)
                app.contains("tele") || app == "tg" -> sendTelegram(number, text)
                else -> sendSms(number, text)
            }
        } catch (_: SecurityException) {
            asyncError(tr("Нет нужного разрешения", "Missing permission", "Kerakli ruxsat yo'q"))
        } catch (_: Exception) {
            asyncError(tr("Не получилось отправить", "Could not send the message", "Xabarni yuborib bo'lmadi"))
        }
    }

    @Suppress("DEPRECATION")
    private fun sendSms(number: String, text: String) {
        if (!granted(Manifest.permission.SEND_SMS)) {
            start(Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:$number")).putExtra("sms_body", text))
            asyncError(tr("Разреши Bubby отправку SMS в настройках — пока открыл сообщение, нажмите «Отправить»",
                "Allow SMS for Bubby in settings; for now I opened the message, press send",
                "Sozlamalarda Bubby uchun SMS ruxsatini bering; hozircha xabarni ochdim, «Yuborish» ni bosing"))
            return
        }
        val sm: SmsManager = (if (Build.VERSION.SDK_INT >= 31) ctx.getSystemService(SmsManager::class.java) else null)
            ?: SmsManager.getDefault()
        val parts = sm.divideMessage(text)
        sm.sendMultipartTextMessage(number, null, parts, null, null)
    }

    private fun e164Digits(number: String): String {
        val plain = number.filter { it.isDigit() }
        return try {
            val tm = ctx.getSystemService(Context.TELEPHONY_SERVICE) as? android.telephony.TelephonyManager
            val iso = listOf(tm?.networkCountryIso, tm?.simCountryIso, Locale.getDefault().country)
                .firstOrNull { !it.isNullOrBlank() }?.uppercase(Locale.US)
            val e = if (iso != null) android.telephony.PhoneNumberUtils.formatNumberToE164(number, iso) else null
            e?.filter { it.isDigit() }?.ifEmpty { null } ?: plain
        } catch (_: Exception) { plain }
    }

    private fun sendWhatsApp(number: String, text: String) {
        val digits = e164Digits(number)
        val i = Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/$digits?text=" + enc(text)))
        val pkg = WA_PKGS.firstOrNull { MusicApps.installed(ctx, it) }
        if (pkg != null) i.setPackage(pkg)
        start(i)
        if (pkg == null || !BuddyAccess.enabled) { asyncError(pressSendMsg()); return }
        Thread {
            var ok = false
            try {
                StepRunner.awaitIdle(6000, 700)
                ok = pollClick(8000)
            } catch (_: Throwable) { }
            if (!ok) asyncError(pressSendMsg())
        }.start()
    }

    private fun sendTelegram(number: String, text: String) {
        val digits = e164Digits(number)
        val pkg = TG_PKGS.firstOrNull { MusicApps.installed(ctx, it) }
            ?: run { asyncError(tr("Telegram не установлен", "Telegram is not installed", "Telegram o'rnatilmagan")); return }
        start(Intent(Intent.ACTION_VIEW, Uri.parse("tg://resolve?phone=$digits")).setPackage(pkg))
        if (!BuddyAccess.enabled) {
            asyncError(tr("Я открыл чат, напишите и отправьте сообщение сами", "I opened the chat, type and send the message yourself", "Chatni ochdim, xabarni o'zingiz yozib yuboring"))
            return
        }
        Thread {
            var ok = false
            try {
                StepRunner.awaitIdle(6000, 700)
                var typed = false
                val until = android.os.SystemClock.uptimeMillis() + 8000
                while (!typed && android.os.SystemClock.uptimeMillis() < until) {
                    if (BuddyAccess.activePackage() in TG_PKGS) typed = BuddyAccess.typeText(text)
                    if (!typed) Thread.sleep(500)
                }
                if (typed) {
                    Thread.sleep(500)
                    ok = BuddyAccess.clickExact(SEND_LABELS, TG_PKGS)
                    if (!ok) { Thread.sleep(800); ok = pollClick(3000, TG_PKGS) }
                }
            } catch (_: Throwable) { }
            if (!ok) asyncError(pressSendMsg())
        }.start()
    }

    /** Worker thread: polls for the exact Send button of a messenger for up to [ms]. */
    private fun pollClick(ms: Long, pkgs: List<String> = WA_PKGS): Boolean {
        val until = android.os.SystemClock.uptimeMillis() + ms
        while (android.os.SystemClock.uptimeMillis() < until) {
            if (BuddyAccess.clickExact(SEND_LABELS, pkgs)) return true
            Thread.sleep(400)
        }
        return false
    }

    private fun brightness(a: JSONObject): String? {
        if (!a.has("percent") || a.isNull("percent")) return tr("Какую яркость поставить?", "What brightness?", "Qanday yorqinlik qo'yay?")
        val p = a.optInt("percent", 50).coerceIn(0, 100)
        if (!Settings.System.canWrite(ctx)) {
            start(Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:" + ctx.packageName)))
            return tr("Разреши Bubby менять настройки системы и повтори команду", "Allow Bubby to change system settings, then repeat the command", "Bubby'ga tizim sozlamalarini o'zgartirishga ruxsat bering va buyruqni takrorlang")
        }
        val cr = ctx.contentResolver
        Settings.System.putInt(cr, Settings.System.SCREEN_BRIGHTNESS_MODE, Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL)
        Settings.System.putInt(cr, Settings.System.SCREEN_BRIGHTNESS, maxOf(1, p * 255 / 100))
        return null
    }

    private fun media(key: String, seconds: Int): String? {
        val sec = if (seconds in 1..600) seconds else 10
        when (key) {
            "play" -> MediaControl.play(ctx)
            "pause" -> MediaControl.pause(ctx)
            "play_pause", "toggle" -> MediaControl.toggle(ctx)
            "next" -> MediaControl.next(ctx)
            "previous" -> MediaControl.previous(ctx)
            "stop" -> MediaControl.stop(ctx)
            "seek_forward", "seek_back" -> {
                val ok = MediaControl.seekBy(ctx, if (key == "seek_forward") sec else -sec)
                if (!ok) return tr("Этот плеер не умеет перематывать", "This player cannot seek", "Bu pleyer o'tkaza olmaydi")
            }
            else -> return tr("Неизвестная медиа-команда", "Unknown media command", "Noma'lum media buyrug'i")
        }
        return null
    }

    private fun lev(a: String, b: String): Int {
        var prev = IntArray(b.length + 1) { it }
        for (i in 1..a.length) {
            val cur = IntArray(b.length + 1)
            cur[0] = i
            for (j in 1..b.length) {
                cur[j] = minOf(prev[j] + 1, cur[j - 1] + 1, prev[j - 1] + if (a[i - 1] == b[j - 1]) 0 else 1)
            }
            prev = cur
        }
        return prev[b.length]
    }

    private fun norm(x: String) = x.lowercase().replace('ё', 'е').trim()

    private fun openApp(name: String): String? {
        if (name.isEmpty()) return tr("Какое приложение открыть?", "Which app should I open?", "Qaysi ilovani ochay?")
        val notFound = tr("Не нашёл приложение $name", "App not found: $name", "Ilova topilmadi: $name")
        Thread {
            val m = try { AppResolver.resolve(ctx, name) } catch (_: Throwable) { null }
            main.post {
                try {
                    if (m == null || !AppResolver.launch(ctx, m.pkg)) asyncError(notFound)
                } catch (_: Exception) { asyncError(notFound) }
            }
        }.start()
        return null
    }

    private fun listAdd(a: JSONObject): String? {
        val list = s(a, "list")
        val items = ArrayList<String>()
        val arr = a.optJSONArray("items")
        if (arr != null) for (i in 0 until arr.length()) arr.optString(i, "").trim().takeIf { it.isNotEmpty() }?.let { items.add(it) }
        s(a, "item").takeIf { it.isNotEmpty() }?.let { items.add(it) }
        if (items.isEmpty()) return tr("Что добавить?", "What should I add?", "Nimani qo'shay?")
        for (it in items) ListStore.add(ctx, list, it)
        return null
    }

    private fun notificationReply(text: String): String? {
        if (text.isEmpty()) return tr("Что ответить?", "What should I reply?", "Nima deb javob beray?")
        if (!BuddyNotifications.isEnabled(ctx)) return tr("Включи доступ к уведомлениям в приложении Bubby (Ещё > Все разрешения)", "Enable notification access in the Bubby app (More > All permissions)", "Bubby ilovasida bildirishnomalarga ruxsatni yoqing (Ko'proq > Barcha ruxsatlar)")
        val n = BuddyNotifications.latest(15).firstOrNull { it.canReply } ?: return tr("Нет уведомления, на которое можно ответить", "No notification to reply to", "Javob beriladigan bildirishnoma yo'q")
        return if (BuddyNotifications.reply(ctx, n.key, text)) null else tr("Не получилось ответить", "Could not reply", "Javob berib bo'lmadi")
    }

    private fun findContact(name: String): String? {
        val q = norm(name)
        val cols = arrayOf(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME, ContactsContract.CommonDataKinds.Phone.NUMBER)
        var best: String? = null
        var bestScore = 0
        ctx.contentResolver.query(ContactsContract.CommonDataKinds.Phone.CONTENT_URI, cols, null, null, null)?.use { c ->
            while (c.moveToNext()) {
                val n = norm(c.getString(0) ?: continue)
                val num = c.getString(1) ?: continue
                val sc = when {
                    n == q -> 100
                    n.startsWith(q) -> 80
                    n.split(' ').any { it == q } -> 70
                    n.contains(q) -> 60
                    n.split(' ').any { it.length >= 3 && q.length >= 3 && lev(it, q) <= maxOf(1, q.length / 4) } -> 40
                    else -> 0
                }
                if (sc > bestScore) { bestScore = sc; best = num }
            }
        }
        return best
    }

    private fun placeCall(number: String) {
        val uri = Uri.fromParts("tel", number, null)
        if (granted(Manifest.permission.CALL_PHONE)) start(Intent(Intent.ACTION_CALL, uri))
        else start(Intent(Intent.ACTION_DIAL, uri))
    }

    private fun call(to: String): String? {
        if (to.isEmpty()) return tr("Кому позвонить?", "Whom should I call?", "Kimga qo'ng'iroq qilay?")
        if (Contacts.isNumber(to)) {
            placeCall(Contacts.normalizeNumber(to))
            return null
        }
        if (!granted(Manifest.permission.READ_CONTACTS)) return tr("Нет доступа к контактам", "No access to contacts", "Kontaktlarga ruxsat yo'q")
        Thread {
            val c = try { Contacts.resolve(ctx, to, 4) } catch (_: Throwable) { emptyList() }
            main.post {
                try {
                    if (c.isEmpty()) {
                        asyncError(tr("Не нашёл контакт: $to", "Contact not found: $to", "Kontakt topilmadi: $to"))
                    } else {
                        // scores: 100 exact ... 80 single name token, <=74 weak; bonuses add up to 8
                        val top = c[0]
                        val clear = top.score >= 80 && (c.size == 1 || top.score - c[1].score >= 10)
                        if (clear) {
                            placeCall(top.number)
                        } else {
                            choice(
                                tr("Кому позвонить?", "Who should I call?", "Kimga qo'ng'iroq qilay?"),
                                c.map { "${it.name} (${it.label})" to JSONObject().put("do", "call").put("to", it.number) }
                            )
                        }
                    }
                } catch (_: SecurityException) {
                    asyncError(tr("Нет нужного разрешения", "Missing permission", "Kerakli ruxsat yo'q"))
                } catch (_: Exception) {
                    asyncError(tr("Не получилось позвонить", "Could not place the call", "Qo'ng'iroq qilib bo'lmadi"))
                }
            }
        }.start()
        return null
    }

    private fun telecom() = ctx.getSystemService(Context.TELECOM_SERVICE) as TelecomManager

    @Suppress("MissingPermission")
    private fun answerCall(): String? {
        if (!granted(Manifest.permission.ANSWER_PHONE_CALLS)) return tr("Нет разрешения отвечать на звонки", "No permission to answer calls", "Qo'ng'iroqqa javob berishga ruxsat yo'q")
        telecom().acceptRingingCall()
        return null
    }

    @Suppress("MissingPermission")
    private fun endCall(): String? {
        if (Build.VERSION.SDK_INT < 28) return tr("Завершение звонка не поддерживается", "Ending calls is not supported", "Qo'ng'iroqni tugatish qo'llab-quvvatlanmaydi")
        if (!granted(Manifest.permission.ANSWER_PHONE_CALLS)) return tr("Нет разрешения завершать звонки", "No permission to end calls", "Qo'ng'iroqni tugatishga ruxsat yo'q")
        return if (telecom().endCall()) null else tr("Нет активного звонка", "No active call", "Faol qo'ng'iroq yo'q")
    }

    private fun volume(a: JSONObject): String? {
        val am = ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        if (a.has("percent") && !a.isNull("percent")) {
            val p = a.optInt("percent", 50).coerceIn(0, 100)
            val max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            am.setStreamVolume(AudioManager.STREAM_MUSIC, Math.round(max * p / 100f), AudioManager.FLAG_SHOW_UI)
            MediaControl.setVolumePercent(ctx, p)
            return null
        }
        val dir = when (s(a, "dir").lowercase()) {
            "up" -> AudioManager.ADJUST_RAISE
            "down" -> AudioManager.ADJUST_LOWER
            else -> return tr("Не понял, громче или тише", "Did not understand: louder or quieter", "Balandroqmi yoki pastroqmi, tushunmadim")
        }
        am.adjustStreamVolume(AudioManager.STREAM_MUSIC, dir, AudioManager.FLAG_SHOW_UI)
        return null
    }

    private fun flashlight(on: Boolean): String? {
        val cm = ctx.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        val id = cm.cameraIdList.firstOrNull {
            cm.getCameraCharacteristics(it).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
        } ?: return tr("На телефоне нет фонарика", "This phone has no flashlight", "Telefonda chiroq yo'q")
        cm.setTorchMode(id, on)
        return null
    }

    private fun settings(panel: String): String? {
        val i = when (panel) {
            "wifi" -> if (Build.VERSION.SDK_INT >= 29) Intent(Settings.Panel.ACTION_INTERNET_CONNECTIVITY) else Intent(Settings.ACTION_WIFI_SETTINGS)
            "bluetooth" -> Intent(Settings.ACTION_BLUETOOTH_SETTINGS)
            "airplane" -> Intent(Settings.ACTION_AIRPLANE_MODE_SETTINGS)
            "battery" -> Intent(Settings.ACTION_BATTERY_SAVER_SETTINGS)
            "storage" -> Intent(Settings.ACTION_INTERNAL_STORAGE_SETTINGS)
            "display" -> Intent(Settings.ACTION_DISPLAY_SETTINGS)
            "location" -> Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
            "volume" -> if (Build.VERSION.SDK_INT >= 29) Intent(Settings.Panel.ACTION_VOLUME) else Intent(Settings.ACTION_SOUND_SETTINGS)
            else -> Intent(Settings.ACTION_SETTINGS)
        }
        start(i)
        return null
    }

    private val dateFormats = listOf("yyyy-MM-dd HH:mm", "yyyy-MM-dd'T'HH:mm", "yyyy-MM-dd HH:mm:ss", "yyyy-MM-dd")

    private fun parseTime(x: String): Long? {
        if (x.isEmpty()) return null
        for (f in dateFormats) {
            try {
                val df = SimpleDateFormat(f, Locale.US)
                df.isLenient = false
                val d = df.parse(x) ?: continue
                return d.time
            } catch (_: Exception) { }
        }
        return null
    }

    private fun addTask(a: JSONObject): String? {
        val title = s(a, "title")
        if (title.isEmpty()) return tr("У задачи нет названия", "The task has no title", "Vazifa nomi yo'q")
        val dueStr = s(a, "due")
        val due = parseTime(dueStr)
        if (dueStr.isNotEmpty() && due == null) return tr("Не понял дату задачи", "Did not understand the task date", "Vazifa sanasini tushunmadim")
        val t = TaskStore(ctx).add(title, s(a, "notes"), due, due != null)
        if (due != null) Reminders.schedule(ctx, t)
        return null
    }

    private fun addEvent(a: JSONObject): String? {
        val title = s(a, "title")
        if (title.isEmpty()) return tr("У события нет названия", "The event has no title", "Tadbir nomi yo'q")
        val start = parseTime(s(a, "start")) ?: return tr("Не понял время события", "Did not understand the event time", "Tadbir vaqtini tushunmadim")
        val end = parseTime(s(a, "end")) ?: (start + 3600_000L)
        val cal = CalendarStore(ctx)
        if (!cal.hasPermission()) return tr("Нет доступа к календарю", "No access to the calendar", "Kalendarga ruxsat yo'q")
        return if (cal.add(title, start, if (end > start) end else start + 3600_000L, s(a, "location")) != null) null
        else tr("Не удалось добавить событие", "Could not add the event", "Tadbirni qo'shib bo'lmadi")
    }
}
