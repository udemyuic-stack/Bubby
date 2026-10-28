package com.example.buddy

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.provider.Settings
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** The last agent runs (steps and results, never screen contents), for Home > "What Bubby did". */
object RunHistory {
    private const val KEY = "agent_runs"
    private const val MAX = 5

    fun save(ctx: Context, log: RunLog) {
        try {
            val p = ctx.getSharedPreferences("buddy", Context.MODE_PRIVATE)
            val old = try { JSONArray(p.getString(KEY, "[]")) } catch (_: Exception) { JSONArray() }
            val out = JSONArray().put(log.toJson())
            for (i in 0 until minOf(old.length(), MAX - 1)) out.put(old.get(i))
            p.edit().putString(KEY, out.toString()).apply()
        } catch (_: Exception) { }
    }

    fun load(ctx: Context): List<RunLog> = try {
        val a = JSONArray(ctx.getSharedPreferences("buddy", Context.MODE_PRIVATE).getString(KEY, "[]"))
        (0 until a.length()).mapNotNull { a.optJSONObject(it)?.let { o -> RunLog.fromJson(o) } }
    } catch (_: Exception) { emptyList() }

    fun clear(ctx: Context) { ctx.getSharedPreferences("buddy", Context.MODE_PRIVATE).edit().remove(KEY).apply() }
}

/** What the service shows while the agent works. All calls come from the worker thread. */
interface AgentUi {
    fun cancelled(): Boolean
    fun progress(label: String)
    /** Approval card (plus the lock-screen check when [ApprovalReq.lock]); blocks until answered or timed out. */
    fun approve(req: ApprovalReq): Boolean
    /** Runs one Actions command on the main thread and waits; returns the error text or null. */
    fun runAction(a: JSONObject): String?
}

/** The phone for the agent loop: actions, device tools, the screen, the user's consent. */
class PhoneHost(private val ctx: Context, private val ui: AgentUi, private val userText: String) : AgentHost {
    private val prefs get() = ctx.getSharedPreferences("buddy", Context.MODE_PRIVATE)
    private fun tr(ru: String, en: String, uz: String) = Tr.t(ctx, ru, en, uz)
    /** Scale of the last screenshot: screen pixels per image pixel (for tap_xy space=image). */
    @Volatile private var shotScale = 1f
    private val dataGrants = HashSet<String>()

    override fun now() = System.currentTimeMillis()
    override fun cancelled() = ui.cancelled()
    override fun progress(label: String) = ui.progress(label)
    override val canAct: Boolean get() = true
    override fun approve(req: ApprovalReq) = ui.approve(req)
    override fun settle() = StepRunner.awaitIdle(2500, 600)
    override fun sleep(ms: Long) {
        val until = System.currentTimeMillis() + ms
        while (System.currentTimeMillis() < until && !ui.cancelled()) Thread.sleep(100)
    }
    override fun foreground(): String? = BuddyAccess.foregroundPackage()

    override fun screen(): ScreenInfo? {
        if (!BuddyAccess.enabled) return null
        val d = BuddyAccess.describe() ?: return null
        return ScreenInfo(d.text, if (d.hidden) null else d.sig, foreground().orEmpty())
    }

    private fun xy(a: JSONObject): Pair<Int, Int>? {
        if (!a.has("x") || !a.has("y") || a.isNull("x") || a.isNull("y")) return null
        var x = a.optInt("x", -1); var y = a.optInt("y", -1)
        if (x < 0 || y < 0) return null
        if (jsStr(a, "space") == "image") { x = (x * shotScale).toInt(); y = (y * shotScale).toInt() }
        return x to y
    }

    override fun target(tool: Tool, args: JSONObject): Target? {
        if (!BuddyAccess.enabled) return null
        val idx = args.optInt("index", -1)
        return when (tool.name) {
            "tap_node", "long_press", "double_tap", "copy" -> if (idx >= 1) BuddyAccess.nodeTarget(idx) else xy(args)?.let { BuddyAccess.targetAt(it.first, it.second) }
            "tap_xy" -> xy(args)?.let { BuddyAccess.targetAt(it.first, it.second) }
            "tap_text" -> Target(jsStr(args, "text"), foreground().orEmpty())
            "type_text", "paste", "press_enter", "select_all" -> BuddyAccess.focusedTarget()
            else -> Target("", foreground().orEmpty())
        }
    }

    private fun granted(p: String) = ctx.checkSelfPermission(p) == PackageManager.PERMISSION_GRANTED
    private fun err(t: String) = ToolOutput(t, error = true)
    private fun needAccess(): ToolOutput? = if (BuddyAccess.enabled) null
        else err("the accessibility service (screen control) is off: tell the user to turn it on in the Bubby app (More > All permissions)")

    /** Personal data (SMS, files, clipboard): only when the user's words asked for it, or after a yes for this task. */
    private fun dataConsent(kind: String, words: Regex, title: String): Boolean {
        if (words.containsMatchIn(userText)) return true
        synchronized(dataGrants) { if (kind in dataGrants) return true }
        val ok = ui.approve(ApprovalReq(title, tr("Только чтение, для этой задачи. Данные уходят выбранному ИИ-сервису.",
            "Read only, for this task. The data goes to your chosen AI service.", "Faqat o'qish, shu vazifa uchun. Ma'lumot tanlangan AI xizmatiga yuboriladi."), false, kind))
        if (ok) synchronized(dataGrants) { dataGrants.add(kind) }
        return ok
    }

    override fun execute(tool: Tool, args: JSONObject): ToolOutput {
        val a = JSONObject(args.toString()).put("do", tool.name)
        return try {
            when {
                tool.name == "screen_text" -> screenText()
                tool.exec == Exec.ACTION -> {
                    if (tool.name in contentTools && BuddyAccess.privateNow()) return err("the app on screen is a banking, payment or password app: Bubby never acts there")
                    val e = ui.runAction(a)
                    if (e == null) ToolOutput("ok") else err(e)
                }
                else -> device(tool.name, a)
            }
        } catch (e: SecurityException) { err("missing permission (${e.message?.take(60)})") }
        catch (e: Exception) { err(e.message ?: "failed") }
    }

    /** Actions that touch an app's content (navigation like back/home stays allowed everywhere). */
    private val contentTools = setOf("tap_node", "tap_text", "type_text", "press_enter", "scroll")

    private fun screenText(): ToolOutput {
        if (!BuddyAccess.enabled) return err("Screen reading is unavailable: the accessibility service (screen control by voice) is off. Say the user should turn it on in the Bubby app (More > All permissions).")
        val d = BuddyAccess.describe() ?: return err("the screen cannot be read right now")
        return ToolOutput(d.text, untrusted = "screen", note = if (d.hidden) null else "Use tap_node with these numbers, or tap_text / scroll / type_text, or just answer.")
    }

    private val reSmsWords = Regex("смс|sms|сообщени|message|xabar", RegexOption.IGNORE_CASE)
    private val reFileWords = Regex("файл|file|загрузк|download|фото|photo|картин|picture|скачан|fayl|rasm|yuklab", RegexOption.IGNORE_CASE)
    private val reClipWords = Regex("буфер|clipboard|скопир|copied|вставь|paste|nusxa", RegexOption.IGNORE_CASE)

    private fun device(name: String, a: JSONObject): ToolOutput {
        val idx = a.optInt("index", -1)
        fun ok(b: Boolean, fail: String) = if (b) ToolOutput("ok") else err(fail)
        return when (name) {
            "look" -> look()
            "tap_xy" -> { needAccess()?.let { return it }; val p = xy(a) ?: return err("need x and y"); ok(BuddyAccess.tapXY(p.first, p.second), "the tap was not accepted") }
            "long_press", "double_tap" -> {
                needAccess()?.let { return it }
                val p = xy(a) ?: (if (idx >= 1) BuddyAccess.nodeCenter(idx) else null) ?: return err("need index (from the screen) or x and y; the element may be gone, read the screen again")
                if (name == "long_press") ok(if (idx >= 1 && xy(a) == null) BuddyAccess.longPressNode(idx) else BuddyAccess.longPressXY(p.first, p.second), "the long press was not accepted")
                else ok(BuddyAccess.doubleTapXY(p.first, p.second), "the double tap was not accepted")
            }
            "swipe", "drag" -> {
                needAccess()?.let { return it }
                val x1 = a.optInt("x1", -1); val y1 = a.optInt("y1", -1); val x2 = a.optInt("x2", -1); val y2 = a.optInt("y2", -1)
                if (minOf(x1, y1, x2, y2) < 0) return err("need x1, y1, x2, y2 in screen pixels")
                if (name == "swipe") ok(BuddyAccess.swipeXY(x1, y1, x2, y2, a.optLong("ms", 300)), "the swipe was not accepted")
                else ok(BuddyAccess.dragXY(x1, y1, x2, y2, a.optLong("ms", 900)), "the drag was not accepted")
            }
            "scroll_to_text" -> {
                needAccess()?.let { return it }
                val t = jsStr(a, "text").trim().ifEmpty { return err("what text?") }
                when (BuddyAccess.scrollToText(t, jsStr(a, "dir"), 8)) {
                    1 -> ToolOutput("\"$t\" is visible now")
                    0 -> err("\"$t\" did not appear after 8 swipes; try the other direction or the app's search")
                    else -> err("could not scroll here")
                }
            }
            "select_all" -> { needAccess()?.let { return it }; ok(BuddyAccess.selectAll(), "no text field to select in") }
            "copy" -> { needAccess()?.let { return it }; ok(BuddyAccess.copy(idx), "nothing selected to copy") }
            "paste" -> { needAccess()?.let { return it }; ok(BuddyAccess.paste(), "no text field to paste into") }
            "clipboard_set" -> {
                val t = jsStr(a, "text"); if (t.isEmpty()) return err("empty text")
                (ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("Bubby", t)); ToolOutput("ok")
            }
            "clipboard_get" -> clipboardGet()
            "share_text" -> {
                val t = jsStr(a, "text"); if (t.isEmpty()) return err("empty text")
                val i = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, t)
                ctx.startActivity(Intent.createChooser(i, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); ToolOutput("the share sheet is open: the user picks where to send it")
            }
            "open_intent" -> openIntent(jsStr(a, "action").lowercase(), jsStr(a, "target").trim(), jsStr(a, "text"))
            "toggle" -> toggle(jsStr(a, "setting").lowercase(), a.optBoolean("on", true))
            "contacts_search" -> contacts(jsStr(a, "q"))
            "sms_read" -> smsRead(a.optInt("count", 5).coerceIn(1, 15), jsStr(a, "from"))
            "files_find" -> filesFind(jsStr(a, "q"), jsStr(a, "where").ifEmpty { "all" }, a.optInt("count", 10).coerceIn(1, 25))
            "app_list" -> appList(jsStr(a, "q"))
            "app_info" -> appInfo(jsStr(a, "name"))
            else -> err("unknown device tool $name")
        }
    }

    private fun look(): ToolOutput {
        needAccess()?.let { return it }
        val s = BuddyAccess.screenshot(1024)
        if (s.err != null || s.b64 == null) return err(s.err ?: "no screenshot")
        if (s.black) return err("the app protects its screen (secure window): the screenshot is black. Use the screen text instead.")
        shotScale = if (s.w > 0) s.screenW.toFloat() / s.w else 1f
        return ToolOutput("Screenshot attached (${s.w}x${s.h} image of a ${s.screenW}x${s.screenH} screen; Bubby's own mascot may be visible). " +
            "Element numbers from the last screen text still apply; for a point seen in the image use tap_xy with space=image.", image = s.b64, untrusted = "screenshot")
    }

    private fun clipboardGet(): ToolOutput {
        if (!dataConsent("clipboard", reClipWords, tr("Прочитать буфер обмена", "Read the clipboard", "Bufer xotirani o'qish"))) return err("the user did not allow reading the clipboard")
        val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val t = try { cm.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(ctx)?.toString() } catch (_: Exception) { null }
            ?: return err("Android does not let apps read the clipboard in the background (or it is empty). Ask the user to paste it, or use paste into a field.")
        if (Safety.hasPrivateText(t)) return err("the clipboard holds card or payment details: not read")
        return ToolOutput(t.take(2000), untrusted = "clipboard")
    }

    private val settingsPages = mapOf(
        "wifi" to Settings.ACTION_WIFI_SETTINGS, "bluetooth" to Settings.ACTION_BLUETOOTH_SETTINGS, "apps" to Settings.ACTION_APPLICATION_SETTINGS,
        "notifications" to "android.settings.ALL_APPS_NOTIFICATION_SETTINGS", "battery" to Settings.ACTION_BATTERY_SAVER_SETTINGS,
        "display" to Settings.ACTION_DISPLAY_SETTINGS, "sound" to Settings.ACTION_SOUND_SETTINGS, "location" to Settings.ACTION_LOCATION_SOURCE_SETTINGS,
        "security" to Settings.ACTION_SECURITY_SETTINGS, "accessibility" to Settings.ACTION_ACCESSIBILITY_SETTINGS, "date" to Settings.ACTION_DATE_SETTINGS,
        "language" to Settings.ACTION_LOCALE_SETTINGS, "storage" to Settings.ACTION_INTERNAL_STORAGE_SETTINGS, "data_usage" to Settings.ACTION_DATA_USAGE_SETTINGS,
        "nfc" to Settings.ACTION_NFC_SETTINGS, "internet" to Settings.ACTION_WIRELESS_SETTINGS, "airplane" to Settings.ACTION_AIRPLANE_MODE_SETTINGS,
        "hotspot" to Settings.ACTION_WIRELESS_SETTINGS, "mobile_data" to Settings.ACTION_DATA_ROAMING_SETTINGS, "settings" to Settings.ACTION_SETTINGS
    )

    private fun start(i: Intent): ToolOutput {
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return try { ctx.startActivity(i); ToolOutput("opened") } catch (_: android.content.ActivityNotFoundException) { err("no app on this phone can open that") }
    }

    /** Only a fixed set of harmless intents: viewing, dialing (no call), drafts, settings pages, maps, store pages. */
    private fun openIntent(action: String, target: String, text: String): ToolOutput = when (action) {
        "view" -> {
            val u = if (target.contains("://")) target else "https://$target"
            if (!u.startsWith("https://") && !u.startsWith("http://")) err("only web links can be viewed") else start(Intent(Intent.ACTION_VIEW, Uri.parse(u)))
        }
        "dial" -> if (target.isEmpty()) err("which number?") else start(Intent(Intent.ACTION_DIAL, Uri.fromParts("tel", target, null)))
        "sms_compose" -> start(Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:" + Uri.encode(target))).putExtra("sms_body", text)).let { if (it.error) it else ToolOutput("the SMS draft is open; the user presses Send") }
        "email_compose" -> start(Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:")).apply {
            if (target.isNotEmpty()) putExtra(Intent.EXTRA_EMAIL, arrayOf(target)); putExtra(Intent.EXTRA_TEXT, text) }).let { if (it.error) it else ToolOutput("the email draft is open; the user presses Send") }
        "settings" -> {
            val page = target.lowercase().ifEmpty { "settings" }
            if (page == "app_details" || page.contains('.')) start(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + (if (page.contains('.')) page else ctx.packageName))))
            else settingsPages[page]?.let { start(Intent(it)) } ?: err("unknown settings page; use one of: " + settingsPages.keys.joinToString(", ") + ", app_details")
        }
        "maps" -> if (target.isEmpty()) err("which place?") else start(Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=" + Uri.encode(target))))
        "navigate" -> if (target.isEmpty()) err("where to?") else start(Intent(Intent.ACTION_VIEW, Uri.parse("google.navigation:q=" + Uri.encode(target)))).let {
            if (it.error) start(Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=" + Uri.encode(target)))) else it }
        "store" -> if (target.isEmpty()) err("which app package?") else start(Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=" + Uri.encode(target)))).let {
            if (it.error) start(Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=" + Uri.encode(target)))) else it }
        else -> err("not an allowed intent; use view, dial, sms_compose, email_compose, settings, maps, navigate or store")
    }

    /** Android forbids apps to flip these switches directly (since Android 10): open the right panel and let the model tap it. */
    private fun toggle(setting: String, on: Boolean): ToolOutput {
        val want = if (on) "ON" else "OFF"
        val panel: Intent? = when (setting) {
            "wifi" -> if (Build.VERSION.SDK_INT >= 29) Intent(Settings.Panel.ACTION_WIFI) else Intent(Settings.ACTION_WIFI_SETTINGS)
            "mobile_data" -> if (Build.VERSION.SDK_INT >= 29) Intent(Settings.Panel.ACTION_INTERNET_CONNECTIVITY) else Intent(Settings.ACTION_DATA_ROAMING_SETTINGS)
            "bluetooth" -> Intent(Settings.ACTION_BLUETOOTH_SETTINGS)
            "airplane" -> Intent(Settings.ACTION_AIRPLANE_MODE_SETTINGS)
            "location" -> Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
            "nfc" -> if (Build.VERSION.SDK_INT >= 29) Intent(Settings.Panel.ACTION_NFC) else Intent(Settings.ACTION_NFC_SETTINGS)
            "hotspot" -> Intent(Settings.ACTION_WIRELESS_SETTINGS)
            else -> null
        }
        if (panel == null) return err("unknown setting; use wifi, bluetooth, airplane, mobile_data, location, nfc or hotspot")
        val r = start(panel)
        if (r.error) return r
        return if (BuddyAccess.enabled)
            ToolOutput("Android does not let apps switch $setting directly, so I opened its panel. Read the screen, find the $setting switch and tap it only if it is not already $want, then check it.")
        else ToolOutput("Android does not let apps switch $setting directly; I opened its settings panel. Screen control is off, so tell the user to tap the switch to turn it $want.")
    }

    private fun contacts(q: String): ToolOutput {
        if (q.isBlank()) return err("whose contact?")
        if (!granted(Manifest.permission.READ_CONTACTS)) return err("no access to contacts: the user can allow it in the Bubby app (More > All permissions > Contacts)")
        val c = Contacts.resolve(ctx, q, 6)
        if (c.isEmpty()) return ToolOutput("no contact matches \"$q\"")
        return ToolOutput(c.joinToString("\n") { "${it.name}: ${it.number} (${it.label})" }, untrusted = "contacts")
    }

    private val privateSms = Regex("(?iu)bank|банк|card|карт|karta|код|code|kod|parol|пароль|password|otp|verification|подтвержд|tasdiq")

    private fun smsRead(count: Int, from: String): ToolOutput {
        if (!prefs.getBoolean("sms_read", false)) return err("reading SMS is off: the user can allow it in Bubby Settings (\"Let Bubby read SMS\")")
        if (!granted(Manifest.permission.READ_SMS)) return err("no permission to read SMS: the user can allow it in the Bubby app (More > All permissions > Read SMS)")
        if (!dataConsent("sms", reSmsWords, tr("Прочитать последние SMS", "Read your latest SMS", "So'nggi SMS'larni o'qish"))) return err("the user did not allow reading SMS for this task")
        val out = StringBuilder()
        val df = SimpleDateFormat("d MMM HH:mm", Locale.US)
        val sel = if (from.isNotBlank()) "address LIKE ?" else null
        val selArgs = if (from.isNotBlank()) arrayOf("%" + from.filter { it.isLetterOrDigit() || it == '+' }.takeLast(9) + "%") else null
        ctx.contentResolver.query(Uri.parse("content://sms/inbox"), arrayOf("address", "body", "date"), sel, selArgs, "date DESC")?.use { c ->
            var n = 0
            while (c.moveToNext() && n < count) {
                val addr = c.getString(0).orEmpty(); val body = c.getString(1).orEmpty(); val date = c.getLong(2)
                // codes and bank messages never leave the phone
                val shown = if (privateSms.containsMatchIn(body) || privateSms.containsMatchIn(addr) || Safety.hasPrivateText(body) || Regex("\\b\\d{4,8}\\b").containsMatchIn(body) && body.length < 200 && Regex("(?iu)code|код|kod").containsMatchIn(body))
                    "(hidden: banking or code message)" else body.replace('\n', ' ').take(300)
                out.append(df.format(Date(date))).append(" from ").append(addr).append(": ").append(shown).append('\n')
                n++
            }
        }
        return if (out.isEmpty()) ToolOutput("no SMS found") else ToolOutput(out.toString().trim(), untrusted = "sms")
    }

    private fun filesFind(q: String, where: String, count: Int): ToolOutput {
        val imgPerm = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_IMAGES else Manifest.permission.READ_EXTERNAL_STORAGE
        if (!dataConsent("files", reFileWords, tr("Посмотреть имена файлов", "Look at file names", "Fayl nomlarini ko'rish"))) return err("the user did not allow looking at files for this task")
        val out = StringBuilder()
        val df = SimpleDateFormat("d MMM yyyy", Locale.US)
        fun query(uri: Uri, label: String) {
            val sel = if (q.isNotBlank()) MediaStore.MediaColumns.DISPLAY_NAME + " LIKE ?" else null
            val args = if (q.isNotBlank()) arrayOf("%$q%") else null
            ctx.contentResolver.query(uri, arrayOf(MediaStore.MediaColumns._ID, MediaStore.MediaColumns.DISPLAY_NAME, MediaStore.MediaColumns.SIZE, MediaStore.MediaColumns.DATE_MODIFIED),
                sel, args, MediaStore.MediaColumns.DATE_MODIFIED + " DESC")?.use { c ->
                var n = 0
                while (c.moveToNext() && n < count) {
                    val nm = c.getString(1) ?: continue
                    out.append(label).append(": ").append(nm).append(" (").append(c.getLong(2) / 1024).append(" KB, ").append(df.format(Date(c.getLong(3) * 1000))).append(")\n")
                    n++
                }
            }
        }
        var note = ""
        if (where == "pictures" || where == "all") {
            if (granted(imgPerm)) query(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, "picture")
            else note += "No permission to see photos (the user can allow it in More > All permissions > Files). "
        }
        if ((where == "downloads" || where == "all") && Build.VERSION.SDK_INT >= 29) {
            try { query(MediaStore.Downloads.EXTERNAL_CONTENT_URI, "download") } catch (_: Exception) { }
            note += "Android shows apps only some files in Downloads (media and their own). "
        }
        if (out.isEmpty()) return ToolOutput("no files found. $note".trim())
        return ToolOutput(out.toString().trim() + if (note.isEmpty()) "" else "\n$note", untrusted = "files")
    }

    private fun appList(q: String): ToolOutput {
        val apps = AppResolver.apps(ctx)
        val f = if (q.isBlank()) apps else apps.filter { it.label.contains(q, true) || it.pkg.contains(q, true) }
        if (f.isEmpty()) return ToolOutput("no installed app matches \"$q\"")
        return ToolOutput(f.take(80).joinToString("\n") { "${it.label} (${it.pkg})" } + if (f.size > 80) "\n… ${f.size - 80} more" else "", untrusted = "apps")
    }

    private fun appInfo(name: String): ToolOutput {
        if (name.isBlank()) return err("which app?")
        val pkg = if (name.contains('.') && !name.contains(' ')) name else AppResolver.resolve(ctx, name)?.pkg ?: return err("app not found: $name")
        val pm = ctx.packageManager
        val pi = try {
            @Suppress("DEPRECATION")
            pm.getPackageInfo(pkg, PackageManager.GET_PERMISSIONS)
        } catch (_: Exception) { return err("app not found: $pkg") }
        val label = try { pm.getApplicationLabel(pi.applicationInfo!!).toString() } catch (_: Exception) { pkg }
        val df = SimpleDateFormat("d MMM yyyy", Locale.US)
        val perms = pi.requestedPermissions?.filterIndexed { i, _ -> (pi.requestedPermissionsFlags?.getOrNull(i) ?: 0) and android.content.pm.PackageInfo.REQUESTED_PERMISSION_GRANTED != 0 }
            ?.map { it.substringAfterLast('.') }?.take(30).orEmpty()
        return ToolOutput("$label ($pkg)\nversion ${pi.versionName}\ninstalled ${df.format(Date(pi.firstInstallTime))}, updated ${df.format(Date(pi.lastUpdateTime))}\n" +
            "granted permissions: " + perms.joinToString(", ").ifEmpty { "none" }, untrusted = "apps")
    }
}
