package com.example.buddy

import android.Manifest
import android.app.Notification
import android.app.PendingIntent
import android.graphics.drawable.Icon
import android.content.res.Configuration
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.KeyguardManager
import android.app.Service
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.media.AudioManager
import android.text.Editable
import android.text.TextWatcher
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.speech.SpeechRecognizer
import android.text.SpannableString
import android.text.Spanned
import android.text.method.ScrollingMovementMethod
import android.text.style.ForegroundColorSpan
import android.util.DisplayMetrics
import android.view.WindowInsets
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.view.animation.DecelerateInterpolator
import android.view.animation.LinearInterpolator
import android.view.animation.OvershootInterpolator
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import org.json.JSONObject
import java.util.Calendar
import java.util.Locale
import kotlin.math.abs
import kotlin.random.Random

class BuddyService : Service() {
    private val AUTO_STICKER_MOODS = setOf("love", "laugh", "excited", "proud", "shy", "crying", "wink", "cool", "silly", "dance", "angry")
    private val h = Handler(Looper.getMainLooper())
    private lateinit var wm: WindowManager          // the manager the mascot window is attached to right now
    private lateinit var sysWm: WindowManager       // the normal one
    private var a11yWin = false                     // attached as an accessibility overlay (the only kind that gets touches over the status bar)
    private lateinit var root: DragLayout
    private lateinit var win: FrameLayout
    private lateinit var wave: WaveView
    private lateinit var lp: WindowManager.LayoutParams
    private lateinit var buddy: BuddyView
    private lateinit var status: TextView
    private lateinit var input: EditText
    private lateinit var answer: TextView
    private lateinit var bg: GradientDrawable
    private lateinit var brain: Brain
    private lateinit var voice: Voice
    private lateinit var actions: Actions
    private lateinit var callWatcher: CallWatcher
    private lateinit var tasks: TaskStore
    private lateinit var calendar: CalendarStore
    private val prefs by lazy { getSharedPreferences("buddy", MODE_PRIVATE) }
    private var u = 1f
    private var mode = "idle"
    private var busy = false
    private var expanded = false
    private var calling = false
    private var callName: String? = null
    private var prog = 0f
    private var islandAnim: ValueAnimator? = null
    private var snapAnim: ValueAnimator? = null
    private var dockAnim: ValueAnimator? = null
    private var errorPinned = false
    private var lastAsk = ""
    private var lastSpoken = false
    private var lastUntrusted = false
    @Volatile private var agentCancel: java.util.concurrent.atomic.AtomicBoolean? = null   // set by a tap or "stop" while the agent works
    private var agentStepping = false                // the agent loop has started acting (progress shown, Stop chip up)
    private var localApproval = false                // Bubby's own approval card is on the island (the agent waits for it)
    @Volatile private var reqId = 0                 // id of the in-flight ask; late results of older ids are ignored
    private var lastFgRetry = 0L
    private var typeAnim: ValueAnimator? = null
    private lateinit var stickerIv: ImageView
    private var stickerBmp: Bitmap? = null
    private var stickerGen = 0
    private var stickerMood = "happy"
    private var stickerCaption = ""
    private var lastReplyHadSticker = false
    private var fullAnswer = ""
    private var dockRight = false
    private var statusDock: String? = null          // "status_left" / "status_right" while docked in the status-bar row
    private var preExpandY = Int.MIN_VALUE
    private var wakeShow = false                    // quiet mode: island temporarily shown after a wake / ask / reminder
    private var autoHidden = false                  // fullscreen auto-hide flag (separate from pref `quiet`)
    private var fsStatusHidden = false              // last insets report: status bar not visible
    private var winShown = true
    private lateinit var approvalHolder: LinearLayout
    private var agentLink: AgentLink? = null
    private val approvals = ArrayList<Approval>()
    private val questions = ArrayList<AgentQuestion>()
    /** An approval or a question from the agents waits for the user: the island stays open for it. */
    private fun waiting() = approvals.isNotEmpty() || questions.isNotEmpty()
    private var holdAnim: ValueAnimator? = null
    private var tapPending: Runnable? = null
    private var hueBase = 0xFF7A6BC4.toInt()
    private var screenOn = true
    private var lastAffPet = 0L
    private var lastLonely = 0L
    private var lastReact = 0L                      // device reactions: at most one per 30 s
    private var lastGreet = 0L                      // welcome-back greeting dedupe
    private var choiceActive = false                // choice chips shown: no auto-collapse
    private var pendingRun: (() -> Unit)? = null       // message waiting out its cancel countdown
    private var pendingLeft = 0
    private var pendingPrompt = ""
    private val pendingTick = Runnable { pendingStep() }
    private var replying = false                    // capturing a spoken reply to a notification
    private var callFocusRelease: (() -> Unit)? = null
    // wake-word loop state
    private var hotActive = false
    private var hotRestartPending = false
    private var hotFails = 0
    private var hotFailSince = 0L
    private var hotPausedUntil = 0L
    private val hotRestart = Runnable { hotRestartPending = false; hotStep() }
    private val screenRx = object : BroadcastReceiver() {
        override fun onReceive(c: Context?, i: Intent?) {
            when (i?.action) {
                Intent.ACTION_SCREEN_OFF -> { screenOn = false; buddy.setActive(false); hotStop(); tiltStop(); fsPollRestart() }
                Intent.ACTION_SCREEN_ON -> {
                    screenOn = true; applyPalette(); buddy.calm = calm()   // the minute clock skips the visuals while the screen is off
                    buddy.setActive(true); hotKick(); tiltStart(); fsPollRestart()
                }
                Intent.ACTION_USER_PRESENT -> { checkReturn(); if (approvals.isEmpty() && questions.isNotEmpty()) showApproval(announce = false) }
            }
        }
    }
    /** A game is running (the model updated it in the last 90 s): the island stays open so the answer chips can be tapped. */
    private fun gameOn() = !prefs.getString("game_state", "").isNullOrBlank() && now() - prefs.getLong("game_ts", 0L) < 90_000L
    private val closeTask: Runnable = Runnable {
        if (!busy && !calling && !waiting() && !choiceActive && !replying) {
            if (gameOn() && expanded) h.postDelayed(closeTask, 15_000L) else collapse()
        }
    }

    private val agentListener = object : AgentLink.Listener {
        override fun onStatus(connected: Boolean, desktop: String?) {
            agentConnected = connected; agentDesktop = desktop; agentViaRelay = connected && (agentLink?.viaRelay == true)
            agentCaps = if (connected) agentLink?.caps ?: emptySet() else emptySet()
            AgentStats.refreshWidgets(this@BuddyService)
        }
        override fun onSessions(list: List<AgentSession>) { agentSessions = list; AgentStats.refreshWidgets(this@BuddyService) }
        override fun onProjects(list: List<String>, current: String) { agentProjects = list to current }
        override fun onRun(id: String, state: String, text: String) { agentRun = Triple(id, state, text) }
        override fun onUsage(u: Usage) { agentUsage = u; AgentStats.save(this@BuddyService, u); AgentStats.refreshWidgets(this@BuddyService) }
        override fun onApproval(a: Approval) {
            if (approvals.none { it.fingerprint == a.fingerprint }) approvals.add(a)
            publishApprovals()
            showApproval(announce = true)
        }
        override fun onApprovalResolved(fp: String) {
            approvals.removeAll { it.fingerprint == fp }
            questions.removeAll { it.fingerprint == fp }
            publishApprovals()
            showApproval(announce = false)
        }
        override fun onQuestion(q: AgentQuestion) {
            if (questions.none { it.fingerprint == q.fingerprint }) questions.add(q)
            publishApprovals()
            showApproval(announce = true)
        }
        override fun onDiff(d: DiffPart) { agentDiffSink?.invoke(d) }
    }

    private lateinit var chipsScroll: HorizontalScrollView
    private lateinit var chipsRow: LinearLayout
    private val prefListener = SharedPreferences.OnSharedPreferenceChangeListener { _, k ->
        when (k) {
            "outfit" -> h.post { buddy.outfit = Outfits.parse(prefs.getString("outfit", "").orEmpty()) }
            "species" -> h.post { buddy.species = prefs.getString("species", "glass").orEmpty() }
            "theme" -> h.post { com.example.buddy.ui.Theme.apply(this); buddy.invalidate() }
            "dock" -> { }   // the place is fixed (next to the clock); nothing moves the mascot any more
            "game_chips" -> h.post { refreshChips() }
            "hotword" -> h.post { hotStop(); hotPausedUntil = 0L; hotKick() }
            "badge" -> h.post { buddy.badgeOn = prefs.getBoolean("badge", true) }
            "state_glow" -> h.post { buddy.stateGlow = prefs.getBoolean("state_glow", true) }
            "quiet" -> h.post { applyHidden(); refreshNotif() }
            "autohide" -> h.post { fsPollRestart() }
            "lang" -> h.post { refreshNotif(); updateDesc() }
        }
    }

    companion object {
        /** Where the mascot lives: in the status-bar row, just right of the clock. */
        const val FIXED_DOCK = "status_left"
        @Volatile var running = false
        @Volatile private var crashHookInstalled = false
        @Volatile var agentConnected = false
        @Volatile var agentViaRelay = false
        @Volatile var agentDesktop: String? = null
        @Volatile var agentSessions: List<AgentSession> = emptyList()
        @Volatile var agentUsage: Usage? = null
        /** Requests waiting for an answer (the app's agents page lists them; answers go back through ACTION_AGENT_DECIDE). */
        @Volatile var agentApprovals: List<Approval> = emptyList()
        /** Questions from Claude Code waiting for an answer (answers go back through ACTION_AGENT_ANSWER). */
        @Volatile var agentQuestions: List<AgentQuestion> = emptyList()
        /** The task started from the phone: (id, state, text) or null. */
        @Volatile var agentRun: Triple<String, String, String>? = null
        /** Folders of the computer a task can run in, and the default one. */
        @Volatile var agentProjects: Pair<List<String>, String> = emptyList<String>() to ""
        /** What the connected bridge offered (`details`, `answers`, `diffs`); empty for an older bridge or offline. */
        @Volatile var agentCaps: Set<String> = emptySet()
        /** The open diff sheet: gets each part asked for with ACTION_AGENT_GET_DIFF, or null when it could not be asked. */
        @Volatile var agentDiffSink: ((DiffPart?) -> Unit)? = null
        const val ACTION_AGENT_ANSWER = "com.example.buddy.AGENT_ANSWER"
        const val ACTION_AGENT_LATER = "com.example.buddy.AGENT_LATER"
        const val ACTION_AGENT_GET_DIFF = "com.example.buddy.AGENT_GET_DIFF"
        /** Spoken words that stop a running agent task. */
        private val STOP_WORDS = Regex("\\b(стоп|хватит|отмена|отмени|останови\\w*|stop|cancel|enough|to'?xta\\w*|bas|yetarli|bekor)\\b", RegexOption.IGNORE_CASE)
        const val ACTION_ASK = "com.example.buddy.ASK"
        const val ACTION_TOGGLE_QUIET = "com.example.buddy.TOGGLE_QUIET"
        const val ACTION_AGENT_RELOAD = "com.example.buddy.AGENT_RELOAD"
        const val ACTION_AGENT_DECIDE = "com.example.buddy.AGENT_DECIDE"
        const val ACTION_AGENT_RUN = "com.example.buddy.AGENT_RUN"
        const val ACTION_AGENT_RUN_STOP = "com.example.buddy.AGENT_RUN_STOP"
        const val ACTION_LISTEN = "com.example.buddy.LISTEN"
        private val WAKE_NAMES = setOf("bubby", "buby", "bubbi", "bubi", "buuby", "буби", "бубби", "бабби", "бубы", "бубя")
        // close to "bubby"/"buby" in spelling but ordinary words
        private val WAKE_NOT = setOf("bubble", "baby", "bobby", "buddy", "busy", "bury", "cubby", "bunny", "bubba")
        private val WORD = Regex("[\\p{L}\\p{N}ʻ’`'‘ʼ]+")
        private val WAKE_APOS = Regex("[ʻ’`'‘ʼ]")

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

        private fun isWakeName(tok: String): Boolean {
            if (tok in WAKE_NOT) return false
            if (tok in WAKE_NAMES) return true
            return tok.length in 4..6 && (lev(tok, "bubby") <= 1 || lev(tok, "buby") <= 1)
        }

        /** Looks for the name in the first three words; returns the text after it ("" if nothing follows) or null if absent. */
        fun wakeMatch(text: String): String? {
            var n = 0
            for (m in WORD.findAll(text)) {
                if (n++ >= 3) break
                val tok = m.value.lowercase().replace('ё', 'е').replace(WAKE_APOS, "")
                if (tok.isNotEmpty() && isWakeName(tok)) {
                    return text.substring(m.range.last + 1).trim().trimStart(',', '.', '!', '?', '-', ':', ' ', ';').trim()
                }
            }
            return null
        }
    }

    override fun onBind(i: Intent?): IBinder? = null

    private var fgNotif: Notification? = null
    private var fgMic = false
    private var fgMicTried = false

    /** (Re)enters the foreground state; the microphone type is dropped again if the system refuses it. */
    private fun startFg(withMic: Boolean, first: Boolean = false) {
        val n = fgNotif ?: return
        try {
            if (Build.VERSION.SDK_INT >= 34) {
                var t = ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                if (withMic) t = t or ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
                try {
                    startForeground(1, n, t); fgMic = withMic
                } catch (e: Exception) {
                    if (!withMic) throw e
                    startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE); fgMic = false
                }
            } else if (Build.VERSION.SDK_INT >= 30) {
                startForeground(1, n, if (withMic) ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE else 0); fgMic = withMic
            } else startForeground(1, n)
        } catch (e: Exception) {
            if (first) {
                // last resort: stay alive without a type rather than crash the process
                try { startForeground(1, n) } catch (_: Exception) {}
            }
        }
    }

    private fun buildNotif(): Notification {
        val quiet = prefs.getBoolean("quiet", false)
        val pi = PendingIntent.getService(this, 7,
            Intent(this, BuddyService::class.java).setAction(ACTION_TOGGLE_QUIET),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val label = if (quiet) t("Показать Bubby", "Show Bubby", "Bubby'ni ko'rsatish") else t("Скрыть Bubby", "Hide Bubby", "Bubby'ni yashirish")
        val act = Notification.Action.Builder(
            Icon.createWithResource(this, android.R.drawable.ic_menu_view), label, pi).build()
        return Notification.Builder(this, "buddy")
            .setContentTitle(t("Bubby работает", "Bubby is running", "Bubby ishlayapti"))
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setOngoing(true)
            .addAction(act).build()
    }

    /** Re-posts the foreground notification (same id and channel) after quiet / language changes. */
    private fun refreshNotif() {
        try {
            val n = buildNotif(); fgNotif = n
            getSystemService(NotificationManager::class.java).notify(1, n)
        } catch (_: Exception) {}
    }

    override fun onStartCommand(i: Intent?, f: Int, id: Int): Int {
        if (!fgMicTried && micOk()) { fgMicTried = true; startFg(true) }
        when (i?.action) {
            Reminders.ACTION_SPEAK -> remind(i.getStringExtra("text").orEmpty())
            ACTION_AGENT_RELOAD -> startAgentLink()
            ACTION_AGENT_RUN -> {
                val id = i.getStringExtra("id").orEmpty(); val text = i.getStringExtra("prompt").orEmpty()
                if (agentLink?.runTask(id, text, i.getStringExtra("project").orEmpty()) == true) agentRun = Triple(id, "running", "") else agentRun = Triple(id, "error", t("Не отправилось: компьютер не принимает задачи (запустите мост с --allow-run)", "Not sent: the computer does not take tasks (start the bridge with --allow-run)", "Yuborilmadi: kompyuter vazifa qabul qilmayapti (bridge'ni --allow-run bilan ishga tushiring)"))
            }
            ACTION_AGENT_RUN_STOP -> agentLink?.stopTask(i.getStringExtra("id").orEmpty())
            ACTION_AGENT_DECIDE -> i.getStringExtra("fp")?.let { fp -> agentLink?.decide(fp, i.getBooleanExtra("allow", false), i.getBooleanExtra("always", false)); resolveLocal(fp) }
            // the app asked for the screen lock already; picks is a JSON list of label lists, checked again by AgentLink
            ACTION_AGENT_ANSWER -> i.getStringExtra("fp")?.let { fp ->
                val picks = try {
                    val a = org.json.JSONArray(i.getStringExtra("picks").orEmpty())
                    List(a.length()) { k -> a.getJSONArray(k).let { l -> List(l.length()) { l.getString(it) } } }
                } catch (_: Exception) { null }
                if (picks != null && agentLink?.answer(fp, picks) == true) resolveLocal(fp)
            }
            ACTION_AGENT_LATER -> i.getStringExtra("fp")?.let { resolveLocal(it) }
            ACTION_AGENT_GET_DIFF -> {
                val pill = i.getStringExtra("pill").orEmpty(); val id = i.getStringExtra("file").orEmpty()
                val file = agentSessions.firstOrNull { it.pillId == pill }?.files?.firstOrNull { it.id == id }
                if (file == null || agentLink?.getDiff(pill, file) != true) agentDiffSink?.invoke(null)
            }
            ACTION_LISTEN -> if (!busy && !calling) {
                cancelTap(); voice.stopSpeaking(); touched()
                if (voiceOn()) startVoice()
                else if (!micOk()) showError("нет микрофона")
                else openInput()
            }
            ACTION_TOGGLE_QUIET -> {
                prefs.edit().putBoolean("quiet", !prefs.getBoolean("quiet", false)).apply()
                applyHidden(); refreshNotif()
            }
            ACTION_ASK -> i.getStringExtra("text")?.takeIf { it.isNotBlank() }?.let { t ->
                if (!busy && !calling) {
                    val untrusted = i.getBooleanExtra("untrusted", false)
                    cancelTap(); errorPinned = false
                    voice.stopListening(); voice.stopSpeaking(); h.removeCallbacks(closeTask)
                    style(true); ask(t, false, untrusted)
                }
            }
        }
        return START_STICKY
    }

    override fun onCreate() {
        super.onCreate()
        running = true
        prefs.edit().putBoolean("was_running", true).apply()
        u = resources.displayMetrics.density
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel("buddy", "Bubby", NotificationManager.IMPORTANCE_MIN))
        fgNotif = buildNotif()
        fgMicTried = micOk()
        startFg(fgMicTried, first = true)
        if (!crashHookInstalled) {
            crashHookInstalled = true
            val old = Thread.getDefaultUncaughtExceptionHandler()
            val crashPrefs = applicationContext.getSharedPreferences("buddy", MODE_PRIVATE)
            Thread.setDefaultUncaughtExceptionHandler { t, e ->
                try { crashPrefs.edit().putString("last_crash", e.stackTraceToString().take(1500)).commit() } catch (_: Exception) {}
                old?.uncaughtException(t, e)
            }
        }
        sysWm = getSystemService(WINDOW_SERVICE) as WindowManager
        wm = sysWm
        BuddyAccess.asker = { askScreenConsent() }
        ClockActions.report = { msg -> h.post { if (msg == null) flashBadge("done", 2500L) else showError(msg) } }
        brain = Brain(this)
        voice = Voice(this, h)
        actions = Actions(this)
        Actions.onAsyncError = { msg -> showAsyncError(msg) }
        Actions.onChoice = { prompt, opts -> showChoices(prompt, opts) }
        Actions.onPending = { prompt, secs, run -> showPending(prompt, secs, run) }
        BuddyNotifications.onNew = { item -> h.post { onNotifItem(item) } }
        tasks = TaskStore(this)
        calendar = CalendarStore(this)
        brain.extraContext = {
            "User's open tasks:\n${tasks.summaryForPrompt()}\nUpcoming calendar events:\n${calendar.summaryForPrompt()}"
        }
        build()
        DeviceEvents.start(this) { e -> h.post { onDeviceEvent(e) } }
        voice.onSpeaking = { sp -> buddy.speaking = sp; if (!sp) hotKick() }
        screenOn = try { getSystemService(PowerManager::class.java).isInteractive } catch (_: Exception) { true }
        buddy.setActive(screenOn)   // do not animate if the service starts while the screen is off
        try {
            val f = IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_OFF); addAction(Intent.ACTION_SCREEN_ON); addAction(Intent.ACTION_USER_PRESENT)
            }
            if (Build.VERSION.SDK_INT >= 33) registerReceiver(screenRx, f, Context.RECEIVER_NOT_EXPORTED)
            else registerReceiver(screenRx, f)
        } catch (_: Exception) {}
        buddy.outfit = Outfits.parse(prefs.getString("outfit", "").orEmpty())
        com.example.buddy.ui.Theme.apply(this)
        buddy.species = prefs.getString("species", "glass").orEmpty()
        buddy.badgeOn = prefs.getBoolean("badge", true)
        buddy.stateGlow = prefs.getBoolean("state_glow", true)
        prefs.registerOnSharedPreferenceChangeListener(prefListener)
        h.post(clock)
        tiltStart()
        startAgentLink()
        callWatcher = CallWatcher(this, { name -> onRinging(name) }, { onCallEnded() }, { onCallAnswered() })
        try { callWatcher.start() } catch (_: Exception) {}
        h.postDelayed({ hotKick() }, 1500)
        if (!prefs.getBoolean("coach_done", false)) h.postDelayed({ showCoach() }, 1500)
    }

    private fun t(ru: String, en: String, uz: String) = Tr.t(this, ru, en, uz)
    private fun dp(v: Int) = (v * u).toInt()
    private fun screenW(): Int = try {
        if (Build.VERSION.SDK_INT >= 30) wm.currentWindowMetrics.bounds.width() else resources.displayMetrics.widthPixels
    } catch (_: Exception) { resources.displayMetrics.widthPixels }
    private fun islandW() = minOf(dp(300), screenW() - dp(32))
    private fun imm() = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
    private fun setMode(m: String) {
        mode = m; buddy.mood = m; buddy.calm = calm()   // calm: lets BuddyView run idle behaviours
        if (badgeHold == null) buddy.badge = when (m) { "think" -> "think"; "listen" -> "listen"; else -> "" }
    }

    // a short-lived badge (error, limit, done, approval) wins over the one that follows the mode
    private var badgeHold: Runnable? = null
    private fun flashBadge(kind: String, ms: Long) {
        badgeHold?.let { h.removeCallbacks(it) }
        buddy.badge = kind
        val r = Runnable { badgeHold = null; buddy.badge = when (mode) { "think" -> "think"; "listen" -> "listen"; else -> "" } }
        badgeHold = r
        h.postDelayed(r, ms)
    }
    private fun micOk() = checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
    private fun voiceOn() = prefs.getBoolean("voice", true) && micOk()
    private fun ttsOn() = prefs.getBoolean("tts", true) && !prefs.getBoolean("silent", false)
    private fun conv() = prefs.getBoolean("conv", false)

    private fun tagOf(l: String?): String? = when (l) {
        "uz" -> "uz-UZ"; "ru" -> "ru-RU"; "en" -> "en-US"; else -> null
    }

    /** Recognition language: explicit pref, else the language of the user's last message, else the system default. */
    private fun langTag(): String? {
        tagOf(prefs.getString("lang", "auto"))?.let { return it }
        // API 34+ detects the language itself (see Voice.addLangDetection); a forced guess would defeat it
        if (Build.VERSION.SDK_INT >= 34) return null
        return tagOf(freshLastLang())
    }

    /** Brain's last detected language, only while recent (10 min); no timestamp written yet counts as fresh. */
    private fun freshLastLang(): String? {
        val l = prefs.getString("last_lang", null) ?: return null
        val at = try { prefs.getLong("last_lang_at", 0L) } catch (_: Exception) { 0L }
        if (at != 0L && now() - at > 10 * 60_000L) return null
        return l
    }

    /** Language for offline replies: an explicit setting wins, else the recently spoken one, else the UI language. */
    private fun replyLang(): String {
        val l = prefs.getString("lang", "auto") ?: "auto"
        if (l == "uz" || l == "ru" || l == "en") return l
        val f = freshLastLang()
        return if (f == "uz" || f == "ru" || f == "en") f else uiLang()
    }

    // uz / ru / en only: the phrases below exist in these three
    private fun uiLang(): String {
        val l = prefs.getString("lang", "auto") ?: "auto"
        if (l == "uz" || l == "ru" || l == "en") return l
        // auto: the language the user spoke last, until they have said anything the phone's language
        prefs.getString("last_lang", null)?.let { if (it == "uz" || it == "ru" || it == "en") return it }
        return Locale.getDefault().language.let { if (it == "uz" || it == "ru") it else "en" }
    }

    private fun hour() = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
    private fun now() = System.currentTimeMillis()
    // last_touch and affection live in memory and reach the prefs file only now and then: every apply() rewrites the whole
    // file (chat history and tasks included), which used to happen on every touch and once a minute around the clock
    private var touchMem = -1L
    private var touchSaved = 0L
    private fun lastTouch(): Long {
        if (touchMem < 0L) { touchMem = prefs.getLong("last_touch", 0L); touchSaved = touchMem }
        if (touchMem == 0L) setTouch(now(), force = true)
        return touchMem
    }
    /** Remembers the time of the last touch; written to the prefs when [force]d or at most once a minute (thresholds are hours). */
    private fun setTouch(t: Long, force: Boolean = false) {
        touchMem = t
        if (force || t - touchSaved >= 60_000L) { touchSaved = t; prefs.edit().putLong("last_touch", t).apply() }
    }
    private fun ignored() = now() - lastTouch() >= 2 * 3600_000L
    private fun baseMood() = if (ignored()) "sleepy" else "idle"   // at night the mascot dozes off by itself after a quiet while (BuddyView), it is not asleep by default

    private var affMem = Float.NaN
    private var affSaved = 0f
    private fun aff(): Float {
        if (affMem.isNaN()) { affMem = try { prefs.getFloat("affection", 50f) } catch (_: Exception) { 50f }; affSaved = affMem }
        return affMem
    }
    /** The slow decay (-0.03 a minute) is saved every half point, about every 17 minutes; petting (+4) right away. */
    private fun addAff(d: Float) {
        affMem = (aff() + d).coerceIn(0f, 100f)
        if (kotlin.math.abs(affMem - affSaved) >= 0.5f) { affSaved = affMem; prefs.edit().putFloat("affection", affMem).apply() }
    }
    private fun flushMemPrefs() {
        val e = prefs.edit()
        if (touchMem > touchSaved) e.putLong("last_touch", touchMem)
        if (!affMem.isNaN() && affMem != affSaved) e.putFloat("affection", affMem)
        e.apply()
    }

    private fun calm() = !expanded && prog < 0.02f && !busy && !calling && !waiting() && screenOn

    /** Returns true (and greets) when the user comes back after more than 3 hours without touching the mascot. */
    private fun checkReturn(): Boolean {
        if (now() - lastTouch() <= 3 * 3600_000L || calling || busy) return false
        setTouch(now(), force = true)
        buddy.flash("happy", 3500)
        val locked = try { getSystemService(KeyguardManager::class.java).isKeyguardLocked } catch (_: Exception) { false }
        val tn = SystemClock.elapsedRealtime()
        val fresh = lastGreet == 0L || tn - lastGreet > 60_000L
        lastGreet = tn
        if (fresh && ttsOn() && !locked && calm()) {
            val l = uiLang()
            say(when (l) { "uz" -> "Salom! Qaytganingdan xursandman"; "ru" -> "С возвращением! Я скучал"; else -> "Welcome back! I missed you" }, l) {}
        }
        return true
    }

    private fun touched() {
        checkReturn()
        setTouch(now())
    }

    private fun say(text: String, lang: String?, done: () -> Unit) { hotStop(); voice.speak(text, lang, done) }

    /** Smooth per-hour colour: 15 degrees of hue per hour, advancing with the minutes. */
    private fun applyPalette() {
        val c = Calendar.getInstance()
        val t = c.get(Calendar.HOUR_OF_DAY) + c.get(Calendar.MINUTE) / 60f
        val hue = (t / 24f * 360f + 250f) % 360f
        val b = Color.HSVToColor(floatArrayOf(hue, 0.65f, 0.95f))
        val l = Color.HSVToColor(floatArrayOf(hue, 0.14f, 1f))
        hueBase = b
        buddy.setPalette(b, l); wave.color = b; wave.accent = l
        buddy.setGlowColor(b)
        tintIsland(0x66)
        if (mode == "idle" || mode == "sleepy") setMode(baseMood())
    }

    private fun mixC(a: Int, b: Int, f: Float): Int {
        val k = 1f - f
        return Color.rgb((Color.red(a) * k + Color.red(b) * f).toInt(),
            (Color.green(a) * k + Color.green(b) * f).toInt(), (Color.blue(a) * k + Color.blue(b) * f).toInt())
    }

    private fun tintIsland(strokeAlpha: Int) {
        // the panel follows the chosen look: near-black glass, deep green or deep blue
        val t = com.example.buddy.ui.Theme.cur
        val deep = when (t.id) { "green" -> 0xFF0A2A12.toInt(); "blue" -> 0xFF0A1C5C.toInt(); "night" -> 0xFF0B0C10.toInt(); else -> 0xFF14161C.toInt() }
        val edge = if (t.id == "glass") 0xFFFFFFFF.toInt() else t.rim
        bg.colors = intArrayOf(mixC(0xFF000000.toInt(), deep, 0.55f), deep)
        bg.setStroke(maxOf(1, dp(1)), ((strokeAlpha * 0.6f).toInt().coerceIn(0, 255) shl 24) or (edge and 0xFFFFFF))
    }

    private val clock = object : Runnable {
        override fun run() {
            addAff(-0.03f)   // slow decay, ~2 points per hour
            // screen off: nothing is drawn and the wake word is stopped, so only the decay runs (SCREEN_ON repaints)
            if (screenOn) {
                applyPalette()
                buddy.calm = calm()
                if (calm()) {
                    if (ignored()) {
                        val t = SystemClock.elapsedRealtime()
                        if (lastLonely == 0L || t - lastLonely > 20 * 60_000L) { lastLonely = t; buddy.flash("sad", 4000) }
                    } else if (aff() > 80f && mode == "idle" && Random.nextInt(8) == 0) buddy.flash("love", 3000)
                }
                hotKick()
            }
            syncOverlayType()
            h.postDelayed(this, 60000)
        }
    }

    // ---- wake word ("Bubby") -------------------------------------------------------------------------------------
    /** True during any phone / VoIP call (the audio mode leaves NORMAL), when the mic belongs to the call. */
    private fun inPhoneCall(): Boolean = try {
        (getSystemService(AUDIO_SERVICE) as AudioManager).mode != AudioManager.MODE_NORMAL
    } catch (_: Exception) { false }

    /** Re-asks for the microphone foreground type after the system refused it (reboot / sticky restart); at most once per minute. */
    private fun retryFgMic() {
        if (fgMic || Build.VERSION.SDK_INT < 30 || !micOk()) return
        val t = SystemClock.elapsedRealtime()
        if (lastFgRetry != 0L && t - lastFgRetry < 60_000L) return
        lastFgRetry = t
        startFg(true)
    }

    // while the agent works the wake-word loop also listens for "stop"
    private fun hotWanted() = prefs.getBoolean("hotword", true) && micOk() && screenOn && (calm() || (busy && agentStepping && !localApproval)) && !inPhoneCall() && !powerSaving() &&
        SystemClock.elapsedRealtime() >= hotPausedUntil && !voice.talking && !voice.listening && holdAnim == null

    /** Battery saver on: the always-listening wake word rests (tapping the mascot still works). */
    private fun powerSaving() = try { (getSystemService(POWER_SERVICE) as android.os.PowerManager).isPowerSaveMode } catch (_: Exception) { false }

    private fun hotKick() {
        if (!hotWanted()) { hotStop(); return }
        if (hotActive && (voice.hotRunning || hotRestartPending)) return
        hotActive = true
        h.removeCallbacks(hotRestart); hotRestartPending = false
        h.post(hotRestart)
    }

    private fun hotStop() {
        hotActive = false; hotRestartPending = false
        h.removeCallbacks(hotRestart)
        voice.stopHot()
    }

    private fun hotStep() {
        if (!hotActive) return
        if (!hotWanted()) { hotStop(); return }
        retryFgMic()
        voice.listenHot(langTag()) { text, err -> onHot(text, err) }
    }

    private fun onHot(text: String?, err: Int) {
        if (!hotActive) return
        val normal = err == 0 || err == SpeechRecognizer.ERROR_NO_MATCH || err == SpeechRecognizer.ERROR_SPEECH_TIMEOUT
        val t = SystemClock.elapsedRealtime()
        if (normal) { hotFails = 0; hotFailSince = 0L }
        else {
            if (hotFailSince == 0L) hotFailSince = t
            hotFails++
            // battery guard: 30 s of continuous real failures (busy/network/client errors) pauses the loop for 5 min
            if (err == SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS || t - hotFailSince > 30_000L) {
                hotActive = false; hotPausedUntil = t + 5 * 60_000L; hotFails = 0; hotFailSince = 0L
                return
            }
        }
        if (text != null && busy && agentStepping) {
            if (text.split('|').any { STOP_WORDS.containsMatchIn(it) }) {
                hotStop(); cancelAsk()
                status.text = t("Остановился", "Stopped", "To'xtadim")
                return
            }
        } else if (text != null) {
            // hypotheses are separated by " | "
            for (hyp in text.split('|')) {
                val rest = wakeMatch(hyp)
                if (rest != null) { hotStop(); onWake(rest); return }
            }
        }
        val delay = if (normal) 500L else minOf(8000L, 600L shl minOf(hotFails, 4))
        hotRestartPending = true
        h.postDelayed(hotRestart, delay)
    }

    private fun onWake(rest: String) {
        buddy.wake()
        touched()
        tick()
        if (rest.length >= 3 && !busy && !calling) {
            voice.stopListening(); style(true); ask(rest, true)
        } else if (!busy && !calling) {
            if (ttsOn()) {
                // Siri-like acknowledgement, then listen once it has been spoken
                h.removeCallbacks(closeTask); errorPinned = false
                style(true); status.text = sListen(); setMode("listen"); setWave(WaveView.LISTEN)
                hide(input); hide(answer); focusable(false)
                say(t("Да?", "Yes?", "Ha?"), uiLang()) { if (!busy && !calling && !voice.listening) startVoice() }
            } else startVoice()
        }
    }

    /** A very short vibration tick (falls back to view haptics when the VIBRATE permission is missing). */
    private fun tick() {
        try {
            val v: Vibrator? = if (Build.VERSION.SDK_INT >= 31) getSystemService(VibratorManager::class.java)?.defaultVibrator
            else (getSystemService(VIBRATOR_SERVICE) as? Vibrator)
            if (v != null && v.hasVibrator()) { v.vibrate(VibrationEffect.createOneShot(25, 120)); return }
        } catch (_: Exception) {}
        try { buddy.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK) } catch (_: Exception) {}
    }

    private var liveSize = 1f
    private fun sizePref() = prefs.getFloat("size", 1f).coerceIn(0.15f, 6f)

    private fun applySize(v: Float = sizePref()) {
        liveSize = v
        // however tiny it gets, keep a 48 dp touch area so it can still be grabbed
        win.minimumWidth = dp(48); win.minimumHeight = dp(48)
        buddy.scaleF = 0.5f * v
        buddy.requestLayout(); buddy.invalidate()
        root.post { clamp() }
    }

    private fun build() {
        buddy = BuddyView(this).apply {
            scaleF = 0.5f * sizePref().also { liveSize = it }
            contentDescription = t("Bubby. Нажмите, чтобы говорить", "Bubby. Tap to talk", "Bubby. Gaplashish uchun bosing")
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
            setOnClickListener { onTap() }
            setOnLongClickListener { cancelTap(); touched(); openInput(); true }
        }
        IslandUi.pressable(buddy, 0.96f)
        wave = WaveView(this).apply { visibility = View.GONE }
        status = TextView(this).apply {
            visibility = View.GONE; textSize = 14f; setTextColor(0xFFFFFFFF.toInt())
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
            setPadding(dp(10), 0, 0, 0)
        }
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            addView(buddy); addView(status)
            addView(wave, LinearLayout.LayoutParams(-2, -2).apply { leftMargin = dp(8) })
        }
        input = EditText(this).apply {
            visibility = View.GONE; hint = t("Спросите…", "Ask me…", "So'rang…"); isSingleLine = true
            setTextColor(0xFFFFFFFF.toInt()); setHintTextColor(0xFFA6A6AF.toInt())
            imeOptions = EditorInfo.IME_ACTION_SEND
            setPadding(dp(14), dp(10), dp(14), dp(10))
            background = GradientDrawable().apply { setColor(0xFF26262B.toInt()); cornerRadius = dp(16).toFloat(); setStroke(maxOf(1, dp(1)), 0x22FFFFFF) }
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, a: Int) {}
                override fun onTextChanged(s: CharSequence?, st: Int, b: Int, c: Int) {}
                override fun afterTextChanged(s: Editable?) {
                    if (expanded && !busy && visibility == View.VISIBLE) armInputClose()
                }
            })
            setOnEditorActionListener { _, a, _ ->
                if (a == EditorInfo.IME_ACTION_SEND) {
                    val t = text.toString().trim()
                    if (t.isNotEmpty() && !busy) { text.clear(); ask(t, false) }
                    true
                } else false
            }
        }
        answer = TextView(this).apply {
            visibility = View.GONE; textSize = 15f; setTextColor(0xFFFFFFFF.toInt())
            maxHeight = dp(200)
            maxWidth = islandW() - dp(28)
            movementMethod = ScrollingMovementMethod()
            isVerticalScrollBarEnabled = true
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
            accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
            setOnClickListener { if (!busy && !calling && !errorPinned && !choiceActive && !replying) collapse() }
        }
        stickerIv = ImageView(this).apply {
            visibility = View.GONE; scaleType = ImageView.ScaleType.FIT_CENTER
            contentDescription = t("Стикер. Удерживайте, чтобы поделиться", "Sticker. Long-press to share", "Stiker. Ulashish uchun bosib turing")
            setOnLongClickListener { shareInlineSticker(); true }
        }
        approvalHolder = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; visibility = View.GONE }
        chipsRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        chipsScroll = HorizontalScrollView(this).apply {
            visibility = View.GONE; isHorizontalScrollBarEnabled = false; addView(chipsRow)
        }
        bg = GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(0xFF000000.toInt(), 0xFF14121B.toInt())).apply {
            cornerRadius = dp(20).toFloat(); setStroke(maxOf(1, dp(1)), 0x22FFFFFF)
            alpha = 0
        }
        root = DragLayout(this).apply {
            orientation = LinearLayout.VERTICAL; background = bg
            addView(header)
            addView(input, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) })
            addView(stickerIv, LinearLayout.LayoutParams(dp(96), dp(96)).apply { topMargin = dp(8) })
            addView(answer, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) })
            addView(chipsScroll, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) })
            addView(approvalHolder, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) })
            elevation = 0f   // collapsed: the mascot floats freely, no box and no square shadow around its head
            canPet = { !expanded && prog < 0.05f && !busy && !calling && !waiting() }
            onPet = { petMascot() }
            // eyes follow the finger while it touches the mascot; coordinates are converted into the mascot's own space
            onTouchAt = { x, y ->
                val p = buddy.parent as? View
                if (p != null && !expanded) buddy.touchAt(x - p.left - buddy.left, y - p.top - buddy.top)
            }
            onTouchEnd = { buddy.touchEnd() }
            // Bubby sits at the top next to the clock and stays there: no dragging, no pinch resize
            onPinch = {}
            onPinchEnd = {}
            onDragStart = { cancelTap() }
            onDrag = { _, _ -> }
            onDragEnd = {}
        }
        win = FrameLayout(this).apply {
            clipChildren = false; clipToPadding = false
            setPadding(dp(12), dp(12), dp(12), dp(12))
            addView(root, FrameLayout.LayoutParams(-2, -2))
        }
        applyProgress(0f)
        val sx = prefs.getInt("px", Int.MIN_VALUE)
        lp = WindowManager.LayoutParams(-2, -2, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT).apply {
            // lets the window reach the status-bar row and the cutout area
            if (Build.VERSION.SDK_INT >= 30) layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
            else if (Build.VERSION.SDK_INT >= 28) layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            gravity = Gravity.TOP or Gravity.START
            x = if (sx == Int.MIN_VALUE) 0 else sx
            y = prefs.getInt("py", -dp(6))   // default: up in the status-bar row
            softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
        }
        setMode(baseMood())
        statusDock = FIXED_DOCK
        quietInit()
        try { wm.addView(win, lp) } catch (e: Exception) { stopSelf(); return }
        applyHidden()
        win.post { syncOverlayType() }
        buddy.enter()   // 700 ms pop-in entrance (skipped when animations are off)
        fsPollRestart()
        // the fixed place: just right of the clock, in the status-bar row (once the window has its size)
        h.postDelayed({ if (!expanded) dockTo(FIXED_DOCK) else pendingDock = FIXED_DOCK }, 300)
        h.postDelayed({ if (!expanded) dockTo(FIXED_DOCK) }, 1200)
    }

    private var dragMaxX = 0
    private var dragMinY = 0
    private var dragMaxY = 0
    private var dragFrame = false
    private val dragRun = Runnable {
        dragFrame = false
        try { wm.updateViewLayout(win, lp) } catch (_: Exception) {}
    }

    private fun startHold() {
        buddy.grabbed = true; buddy.glow = true
        holdAnim?.cancel(); holdAnim = null
        if (!IslandUi.animOn(this)) return
        holdAnim = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 520; repeatCount = ValueAnimator.INFINITE; repeatMode = ValueAnimator.REVERSE
            interpolator = LinearInterpolator()
            var lastStep = -1
            addUpdateListener {
                val f = it.animatedValue as Float
                root.rotation = (f - 0.5f) * 5f
                // the glow pulse changes in 8 steps: rebuilding its shader on every frame would slow the drag down
                val step = (f * 8f).toInt()
                if (step != lastStep) {
                    lastStep = step
                    buddy.setGlowColor((((130 + 125 * f).toInt()) shl 24) or (hueBase and 0xFFFFFF))
                    tintIsland((90 + 140 * f).toInt())
                }
            }
            start()
        }
    }

    private fun endHold() {
        holdAnim?.cancel(); holdAnim = null
        buddy.grabbed = false; buddy.glow = false
        buddy.setGlowColor(hueBase)
        tintIsland(0x66)
    }

    private fun petMascot() {
        cancelTap()
        val high = aff() >= 60f
        buddy.pet()
        buddy.flash(if (high) "love" else "happy", 2500)
        val t = SystemClock.elapsedRealtime()
        if (t - lastAffPet >= 5000L) { lastAffPet = t; addAff(4f) }
        touched()
    }

    private fun cancelTap() { tapPending?.let { h.removeCallbacks(it) }; tapPending = null }

    /** The transparent top padding may leave the screen so the visible mascot can touch y=0; expanded, the panel stays fully on-screen. */
    private fun minY() = if (expanded || prog > 0.02f) 0 else -win.paddingTop

    private fun clamp() {
        if (win.width == 0) return
        val maxX = maxOf(0, screenW() - win.width)
        val ox = lp.x; val oy = lp.y
        if (dockRight) lp.x = maxX
        lp.x = lp.x.coerceIn(0, maxX)
        val minY = minY()
        lp.y = lp.y.coerceIn(minY, maxOf(minY, realH() - win.height))
        if (lp.x != ox || lp.y != oy) try { wm.updateViewLayout(win, lp) } catch (_: Exception) {}
    }

    private fun snap() {
        win.postDelayed({ syncOverlayType() }, 450)
        snapAnim?.cancel(); snapAnim = null
        dockAnim?.let { it.removeAllListeners(); it.cancel() }; dockAnim = null
        val w = win.width
        if (w == 0) return
        val sw = screenW()
        val maxX = maxOf(0, sw - w)
        val cx = lp.x + w / 2
        val topCenter = lp.y < dp(96) && abs(cx - sw / 2) < sw * 0.2f
        val target = when { topCenter -> maxX / 2; cx < sw / 2 -> 0; else -> maxX }
        dockRight = !topCenter && maxX > 0 && target == maxX
        prefs.edit().putInt("px", target).putInt("py", lp.y).apply()
        if (target == lp.x) return
        if (!IslandUi.animOn(this)) {
            lp.x = target
            try { wm.updateViewLayout(win, lp) } catch (_: Exception) {}
            return
        }
        snapAnim = ValueAnimator.ofInt(lp.x, target).apply {
            duration = 300
            interpolator = DecelerateInterpolator(2f)
            addUpdateListener {
                lp.x = it.animatedValue as Int
                try { wm.updateViewLayout(win, lp) } catch (_: Exception) {}
            }
            start()
        }
    }

    private fun realH(): Int = try {
        if (Build.VERSION.SDK_INT >= 30) wm.currentWindowMetrics.bounds.height()
        else {
            val d = DisplayMetrics()
            @Suppress("DEPRECATION") wm.defaultDisplay.getRealMetrics(d)
            d.heightPixels
        }
    } catch (_: Exception) { resources.displayMetrics.heightPixels }

    private fun navH(): Int {
        val id = resources.getIdentifier("navigation_bar_height", "dimen", "android")
        var n = if (id > 0) resources.getDimensionPixelSize(id) else dp(24)
        if (Build.VERSION.SDK_INT >= 30) try {
            n = maxOf(n, wm.currentWindowMetrics.windowInsets.getInsetsIgnoringVisibility(WindowInsets.Type.navigationBars()).bottom)
        } catch (_: Exception) {}
        return n
    }

    private fun topH(): Int {
        val id = resources.getIdentifier("status_bar_height", "dimen", "android")
        var n = if (id > 0) resources.getDimensionPixelSize(id) else dp(24)
        if (Build.VERSION.SDK_INT >= 30) try {
            n = maxOf(n, wm.currentWindowMetrics.windowInsets.getInsetsIgnoringVisibility(WindowInsets.Type.statusBars()).top)
        } catch (_: Exception) {}
        return n
    }

    private var pendingDock: String? = null

    /**
     * The status bar sits above normal overlay windows, so a mascot parked up there cannot be tapped. Windows added through
     * the accessibility service are layered above it, so while the mascot touches the status bar (and that service is on)
     * it lives in such a window, and goes back to a normal one elsewhere.
     */
    private fun syncOverlayType() {
        if (!::win.isInitialized || !win.isAttachedToWindow) return
        val inBar = lp.y + win.paddingTop + dp(6) < topH()
        val a11y = if (inBar) BuddyAccess.windowManager() else null
        val want = a11y != null
        if (want == a11yWin) return
        if (!want && BuddyAccess.enabled && a11yWin && inBar) return
        val next = if (want) a11y!! else sysWm
        val oldType = lp.type
        try { wm.removeView(win) } catch (_: Exception) {}
        lp.type = if (want) WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY else WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        try {
            next.addView(win, lp); wm = next; a11yWin = want
        } catch (_: Exception) {
            // could not move: put it back as it was
            lp.type = oldType
            try { sysWm.addView(win, lp); wm = sysWm; a11yWin = false } catch (_: Exception) { stopSelf() }
        }
    }

    private fun dockTo(where: String) {
        if (win.width == 0 || win.height == 0) return
        // positions depend on the window size: wait for the collapsed one
        if (expanded || prog > 0.02f) { pendingDock = where; return }
        val isStatus = where.startsWith("status_")
        if (isStatus) {
            val sbh = topH(); val bh = buddy.height
            val target = (sbh * 1.1f).toInt()
            if (bh > 0 && bh > target) {
                val ns = (liveSize * target / bh).coerceIn(0.15f, 6f)
                if (ns < liveSize - 0.01f) {
                    if (!prefs.contains("size_before_status")) prefs.edit().putFloat("size_before_status", liveSize).apply()
                    prefs.edit().putFloat("size", ns).apply()
                    applySize(ns)
                    pendingDock = null
                    h.postDelayed({ if (!expanded) dockTo(where) else pendingDock = where }, 180)
                    return
                }
            }
        } else if (prefs.contains("size_before_status")) {
            val old = prefs.getFloat("size_before_status", -1f)
            prefs.edit().remove("size_before_status").remove("status_dock").apply()
            statusDock = null
            if (old > 0f) {
                prefs.edit().putFloat("size", old).apply()
                applySize(old)
                pendingDock = null
                h.postDelayed({ if (!expanded) dockTo(where) else pendingDock = where }, 180)
                return
            }
        }
        pendingDock = null
        snapAnim?.cancel(); snapAnim = null
        dockAnim?.let { it.removeAllListeners(); it.cancel() }; dockAnim = null
        val sw = screenW()
        val sh = realH()
        val maxX = maxOf(0, sw - win.width)
        val pad = dp(12)                         // transparent window padding around the island
        val m = dp(8)
        val topY = maxOf(0, topH() + m - pad)
        val minY = -win.paddingTop
        val botY = maxOf(0, sh - navH() - win.height + pad - m)   // island sits just above the nav bar / gesture area
        val midY = maxOf(0, (sh - win.height) / 2)
        val tx: Int; val ty: Int
        when (where) {
            "top" -> { tx = maxX / 2; ty = topY }
            "bottom" -> { tx = maxX / 2; ty = botY }
            "left" -> { tx = 0; ty = lp.y }
            "right" -> { tx = maxX; ty = lp.y }
            "center" -> { tx = maxX / 2; ty = midY }
            "bottom_left" -> { tx = 0; ty = botY }
            "bottom_right" -> { tx = maxX; ty = botY }
            "top_left" -> { tx = 0; ty = topY }
            "top_right" -> { tx = maxX; ty = topY }
            "status_left", "status_right", "status_center" -> {
                // centre of the mascot inside the window, then centre that on the status bar
                val hdr = buddy.parent as? View
                val cy = win.paddingTop + root.top + (hdr?.top ?: 0) + buddy.top + buddy.height / 2
                ty = topH() / 2 - cy
                val inL = win.paddingLeft + root.left + root.paddingLeft
                tx = when (where) {
                    "status_left" -> dp(72) - inL
                    "status_center" -> (sw - win.width) / 2        // in the camera-cutout "island" spot
                    else -> sw - dp(170) - win.width
                }
            }
            else -> return
        }
        val fx = tx.coerceIn(0, maxX)
        val fy = ty.coerceIn(minY, maxOf(minY, sh - win.height))
        dockRight = maxX > 0 && fx == maxX && !isStatus && where != "top" && where != "bottom" && where != "center"
        if (isStatus) { statusDock = where; prefs.edit().putString("status_dock", where).apply() }
        val sx0 = lp.x; val sy0 = lp.y
        fun save() { prefs.edit().putInt("px", lp.x).putInt("py", lp.y).apply(); win.post { syncOverlayType() } }
        if (!IslandUi.animOn(this)) {
            lp.x = fx; lp.y = fy
            try { wm.updateViewLayout(win, lp) } catch (_: Exception) {}
            save(); return
        }
        dockAnim = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 280
            interpolator = OvershootInterpolator(1.4f)
            addUpdateListener {
                val f = it.animatedValue as Float
                lp.x = (sx0 + (fx - sx0) * f).toInt().coerceIn(0, maxX)
                lp.y = (sy0 + (fy - sy0) * f).toInt().coerceIn(minY, maxOf(minY, sh - win.height))
                try { wm.updateViewLayout(win, lp) } catch (_: Exception) {}
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(a: android.animation.Animator) {
                    dockAnim = null
                    lp.x = fx; lp.y = fy
                    try { wm.updateViewLayout(win, lp) } catch (_: Exception) {}
                    save()
                }
            })
            start()
        }
    }

    private fun applyProgress(p: Float) {
        prog = p
        val q = p.coerceIn(0f, 1f)
        root.minimumWidth = maxOf(0, (islandW() * p).toInt())
        bg.cornerRadius = dp(20) + dp(4) * p
        // the dark panel (and its shadow) only appears as the island opens
        bg.alpha = (q * 255).toInt()
        root.elevation = dp(6) * q
        root.setPadding(
            (dp(12) + dp(2) * p).toInt(), (dp(2) + dp(6) * p).toInt(),
            (dp(12) + dp(2) * p).toInt(), (dp(2) + dp(10) * p).toInt()
        )
        status.alpha = q; status.translationX = (q - 1f) * dp(6)
        wave.alpha = q
        if (!expanded) { answer.alpha = q; chipsScroll.alpha = q; approvalHolder.alpha = q }
        buddy.calm = calm()   // idle behaviours pause while the island is open
        clamp()
    }

    private fun style(exp: Boolean, force: Boolean = false) {
        // inside Bubby's own app the island does not pop open over the page by itself (the app shows the answer itself);
        // what the user asked for with a tap or the voice, an agent's question or request, Bubby's own confirmations,
        // reminders and phone calls (force) still open it
        if (exp && !force && com.example.buddy.ui.UiActivity.appInFront && !calling && approvals.isEmpty() && questions.isEmpty()) return
        val was = expanded
        if (exp && !was) preExpandY = lp.y
        expanded = exp
        if (exp) {
            if (prefs.getBoolean("quiet", false) || autoHidden) { wakeShow = true; applyHidden() }
            hotStop()
            status.visibility = View.VISIBLE
            if (!was) { answer.alpha = 1f; chipsScroll.alpha = 1f; approvalHolder.alpha = 1f }
            h.removeCallbacks(speakPoll); h.postDelayed(speakPoll, 150)
            refreshChips()
        }
        updateDesc()
        animateIsland(exp)
    }

    private fun animateIsland(exp: Boolean) {
        islandAnim?.let { it.removeAllListeners(); it.cancel() }
        islandAnim = null
        val to = if (exp) 1f else 0f
        val from = prog
        if (from == to || !IslandUi.animOn(this)) {
            if (from != to) applyProgress(to)
            if (exp) win.post { clamp() } else finishCollapse()
            return
        }
        islandAnim = ValueAnimator.ofFloat(from, to).apply {
            duration = if (exp) 260L else 220L
            interpolator = if (exp) OvershootInterpolator(1.3f) else DecelerateInterpolator()
            addUpdateListener { applyProgress(it.animatedValue as Float) }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(a: android.animation.Animator) {
                    islandAnim = null
                    applyProgress(to)
                    if (exp) win.post { clamp() } else finishCollapse()
                }
            })
            start()
        }
    }

    private fun finishCollapse() {
        if (expanded) return
        status.visibility = View.GONE
        if (lp.flags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE == 0) {
            try { imm().hideSoftInputFromWindow(input.windowToken, 0) } catch (_: Exception) {}
            focusable(false)
        }
        setWave(WaveView.NONE)
        hide(answer); hide(chipsScroll)
        clearSticker()
        refreshChips()
        wakeShow = false
        if (preExpandY < 0 && lp.y == 0) lp.y = preExpandY
        preExpandY = Int.MIN_VALUE
        win.post { clamp() }
        statusDock?.let { w -> if (pendingDock == null) win.post { if (!expanded) dockTo(w) } }
        applyHidden()
        pendingDock?.let { w -> win.post { if (!expanded) { pendingDock = null; dockTo(w) } } }
        hotKick()
    }

    private fun show(v: View) {
        if (v.visibility == View.VISIBLE) return
        v.visibility = View.VISIBLE
        IslandUi.reveal(v, u, if (islandAnim != null) 90L else 0L)
    }

    private fun hide(v: View) {
        v.animate().cancel()
        v.alpha = 1f; v.translationY = 0f
        v.visibility = View.GONE
    }

    private fun setWave(m: Int) {
        wave.mode = m
        wave.visibility = if (m != WaveView.NONE && expanded) View.VISIBLE else View.GONE
        updateDesc()
    }

    private fun updateDesc() {
        buddy.contentDescription = when {
            !expanded -> t("Bubby. Нажмите, чтобы говорить", "Bubby. Tap to talk", "Bubby. Gaplashish uchun bosing")
            wave.mode == WaveView.LISTEN -> t("Bubby слушает", "Bubby is listening", "Bubby eshityapti")
            else -> t("Bubby отвечает", "Bubby is answering", "Bubby javob berayapti")
        }
    }

    private val speakPoll = object : Runnable {
        override fun run() {
            if (!expanded) return
            if (!busy && wave.mode != WaveView.LISTEN) {
                val sp = voice.speaking
                if (sp && wave.mode != WaveView.SPEAK) setWave(WaveView.SPEAK)
                else if (!sp && wave.mode == WaveView.SPEAK) setWave(WaveView.NONE)
            }
            h.postDelayed(this, 150)
        }
    }

    private fun setAnswer(text: String, type: Boolean) {
        typeAnim?.let { it.removeAllListeners(); it.cancel() }
        typeAnim = null
        fullAnswer = text
        answer.text = text
        // the island stays closed while Bubby's own app is in front: the answer would be invisible, so show it as a toast
        if (!expanded && text.isNotBlank() && com.example.buddy.ui.UiActivity.appInFront) h.post { android.widget.Toast.makeText(this, text.take(300), android.widget.Toast.LENGTH_LONG).show() }
        if (!type || text.length < 6 || !IslandUi.animOn(this)) return
        val n = text.length
        val sp = SpannableString(text)
        val hidden = ForegroundColorSpan(0x00FFFFFF)
        typeAnim = ValueAnimator.ofInt(0, n).apply {
            duration = minOf(1200L, maxOf(300L, n * 22L))
            interpolator = LinearInterpolator()
            addUpdateListener {
                val k = it.animatedValue as Int
                if (k >= n) answer.text = fullAnswer
                else { sp.setSpan(hidden, k, n, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE); answer.text = sp }
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(a: android.animation.Animator) { answer.text = fullAnswer; typeAnim = null }
            })
            start()
        }
        answer.text = sp.also { sp.setSpan(hidden, 0, n, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE) }
    }

    private fun startAgentLink() {
        agentLink?.stop(); agentLink = null
        approvals.clear(); questions.clear(); publishApprovals(); showApproval(announce = false)
        agentConnected = false; agentDesktop = null; agentSessions = emptyList(); agentCaps = emptySet(); AgentStats.refreshWidgets(this)
        if (PairingStore(this).load() == null) return
        agentLink = AgentLink(this, agentListener).also { it.start() }
    }

    private fun showApproval(announce: Boolean) {
        if (localApproval) return   // Bubby's own card is up; the agent-link card comes back when it is answered
        approvalHolder.removeAllViews()
        val a = approvals.firstOrNull()
        if (a == null && questions.isNotEmpty()) { showQuestion(questions.first(), announce); return }
        if (a == null) {
            approvalHolder.visibility = View.GONE
            if (!busy && !calling && !listeningNow()) h.postDelayed(closeTask, 1500)
            return
        }
        h.removeCallbacks(closeTask)
        voice.stopListening()
        val locked = getSystemService(KeyguardManager::class.java).isKeyguardLocked
        style(true, force = true); status.text = t("Нужно разрешение", "Permission needed", "Ruxsat kerak"); setMode("surprised"); setWave(WaveView.NONE)
        hide(input); hide(answer)
        val canAlways = "always" in agentCaps && !a.truncated && IslandUi.risk(a.tool, a.command) != IslandUi.RISK_HIGH
        approvalHolder.addView(ApprovalCard.build(this, a, locked, onAllow = {
            ConfirmActivity.request(this) { ok ->
                if (ok) { agentLink?.decide(a.fingerprint, true); resolveLocal(a.fingerprint) }
                else { status.text = t("Нужен экран блокировки", "Lock screen required", "Ekran qulfi kerak") }
            }
        }, onDeny = { agentLink?.decide(a.fingerprint, false); resolveLocal(a.fingerprint) },
        onAlways = if (!canAlways) null else ({
            ConfirmActivity.request(this) { ok ->
                if (ok) { agentLink?.decide(a.fingerprint, true, true); resolveLocal(a.fingerprint) }
                else { status.text = t("Нужен экран блокировки", "Lock screen required", "Ekran qulfi kerak") }
            }
        })))
        approvalHolder.alpha = 1f; approvalHolder.visibility = View.VISIBLE
        if (announce && ttsOn() && !locked) {
            val l = uiLang()
            say(when (l) { "uz" -> "Claude Code ruxsat so'rayapti: ${a.tool}"; "ru" -> "Claude Code просит разрешение: ${a.tool}"; else -> "Claude Code asks permission: ${a.tool}" }, l) {}
        }
        focusable(false)
    }

    /** A question from Claude Code on the island: like an approval, the mascot is surprised, flashes and says so. */
    private fun showQuestion(q: AgentQuestion, announce: Boolean) {
        h.removeCallbacks(closeTask)
        voice.stopListening()
        val locked = getSystemService(KeyguardManager::class.java).isKeyguardLocked
        style(true, force = true); status.text = t("У Claude вопрос", "Claude has a question", "Claude'da savol bor"); setMode("surprised"); setWave(WaveView.NONE)
        hide(input); hide(answer)
        approvalHolder.addView(QuestionCard.build(this, q, locked, onSend = { picks ->
            ConfirmActivity.requestAnswer(this) { ok ->
                if (ok && agentLink?.answer(q.fingerprint, picks) == true) resolveLocal(q.fingerprint)
                else if (questions.any { it.fingerprint == q.fingerprint }) {
                    showApproval(announce = false)   // a fresh card: the picks can be made again
                    status.text = if (ok) t("Ответ не отправлен", "The answer was not sent", "Javob yuborilmadi") else t("Нужен экран блокировки", "Lock screen required", "Ekran qulfi kerak")
                }
            }
        }, onLater = { resolveLocal(q.fingerprint) }))
        approvalHolder.alpha = 1f; approvalHolder.visibility = View.VISIBLE
        if (announce) {
            flashBadge("approval", 6000L)
            if (ttsOn() && !locked) {
                val l = uiLang()
                say(when (l) { "uz" -> "Claude'da savol bor"; "ru" -> "У Claude вопрос"; else -> "Claude has a question" }, l) {}
            }
        }
        focusable(false)
    }

    private fun resolveLocal(fp: String) {
        approvals.removeAll { it.fingerprint == fp }; questions.removeAll { it.fingerprint == fp }
        publishApprovals(); showApproval(announce = false)
    }

    private fun publishApprovals() { agentApprovals = approvals.toList(); agentQuestions = questions.toList(); AgentStats.refreshWidgets(this) }

    private fun sListen() = t("Слушаю…", "Listening…", "Eshityapman…")
    private fun listeningNow() = status.text.toString() == sListen()

    private fun refreshChips() {
        if (choiceActive) return
        chipsRow.removeAllViews()
        val list = if (expanded && !busy) Games.chips(this) else emptyList()
        for (c in list) chipsRow.addView(chip(c) { if (!busy && !calling) { voice.stopSpeaking(); ask(c, false) } },
            LinearLayout.LayoutParams(-2, -2).apply { rightMargin = dp(6) })
        if (list.isEmpty()) hide(chipsScroll) else show(chipsScroll)
    }

    private fun chip(label: String, onClick: () -> Unit) = TextView(this).apply {
        text = label; textSize = 14f; setTextColor(0xFFFFFFFF.toInt())
        gravity = Gravity.CENTER
        minHeight = dp(48)
        setPadding(dp(14), dp(14), dp(14), dp(14))
        background = GradientDrawable().apply { setColor(0xFF26262B.toInt()); cornerRadius = dp(24).toFloat(); setStroke(maxOf(1, dp(1)), 0x22FFFFFF) }
        isClickable = true
        IslandUi.pressable(this, 0.94f)
        setOnClickListener { onClick() }
    }

    /** Replaces the chips row with one action chip. */
    private fun showActionChip(label: String, onClick: () -> Unit) {
        chipsRow.removeAllViews()
        chipsRow.addView(chip(label, onClick), LinearLayout.LayoutParams(-2, -2))
        chipsScroll.animate().cancel()
        chipsScroll.alpha = 1f; chipsScroll.translationY = 0f; chipsScroll.visibility = View.VISIBLE
    }

    private fun openBubby() {
        try {
            startActivity(Intent(this, com.example.buddy.ui.UiActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (_: Exception) {}
        collapse()
    }

    private fun showCoach() {
        if (prefs.getBoolean("coach_done", false)) return
        if (calling || busy || expanded || waiting()) { h.postDelayed({ showCoach() }, 15000); return }
        prefs.edit().putBoolean("coach_done", true).apply()
        h.removeCallbacks(closeTask)
        style(true, force = true); status.text = t("Привет, я Bubby!", "Hi, I'm Bubby!", "Salom, men Bubby!"); setMode("excited"); setWave(WaveView.NONE)
        hide(input)
        setAnswer(t("Нажмите — говорите. Удерживайте — пишите. Тяните — переносите. Двумя пальцами — размер", "Tap to talk. Hold to type. Drag to move. Pinch to resize", "Bosing — gapiring. Bosib turing — yozing. Tortib — ko'chiring. Ikki barmoq bilan — o'lcham"), false); show(answer)
        errorPinned = true
        showActionChip(t("Понятно", "Got it", "Tushunarli")) { errorPinned = false; collapse() }
        focusable(false)
        h.postDelayed({ errorPinned = false }, 7900)
        h.postDelayed(closeTask, 8000)
    }

    private fun focusable(f: Boolean) {
        lp.flags = if (f) lp.flags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE.inv()
        else lp.flags or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
        try { wm.updateViewLayout(win, lp) } catch (_: Exception) {}
    }

    private fun onTap() {
        when {
            calling -> {}
            expanded && busy -> { touched(); cancelAsk() }
            expanded -> { touched(); if (!busy && !waiting() && pendingRun == null) collapse() }
            tapPending != null -> { cancelTap(); touched(); addAff(-3f); buddy.spinHead() }
            else -> {
                // wait out the double-tap window before starting the voice session
                val r = Runnable { tapPending = null; singleTap() }
                tapPending = r
                h.postDelayed(r, 260)
            }
        }
    }

    private fun singleTap() {
        if (calling || expanded) return
        touched(); addAff(1f)
        if (voiceOn()) startVoice()
        else if (!micOk()) showError("нет микрофона")
        else openInput()
    }

    private fun startVoice() {
        h.removeCallbacks(closeTask); errorPinned = false
        retryFgMic()
        style(true, force = true); status.text = sListen(); setMode("listen"); setWave(WaveView.LISTEN)
        hide(input); hide(answer)
        focusable(false)
        var woke = false
        voice.listen(langTag(), { p ->
            if (!woke && wakeMatch(p) != null) { woke = true; buddy.wake() }
            setAnswer(p, false); show(answer)
        }, { text, err ->
            when {
                text != null -> { if (!woke && wakeMatch(text) != null) buddy.wake(); ask(text, true) }
                err != null -> showError(err) { startVoice() }
                else -> { status.text = t("Не расслышал", "Didn't catch that", "Eshitmadim"); setWave(WaveView.NONE); setMode(baseMood()); h.postDelayed(closeTask, 2000) }
            }
        })
    }

    /** Typed input closes itself after 20 s without typing, so the focusable window never keeps the keyboard from other apps. */
    private fun armInputClose() {
        h.removeCallbacks(closeTask)
        h.postDelayed(closeTask, 20_000L)
    }

    private fun openInput() {
        h.removeCallbacks(closeTask); errorPinned = false
        voice.stopListening()
        style(true, force = true); input.hint = t("Спросите…", "Ask me…", "So'rang…"); status.text = t("Пишите…", "Type here…", "Yozing…"); setMode("listen"); setWave(WaveView.NONE)
        hide(answer); show(input)
        focusable(true)
        input.requestFocus()
        h.postDelayed({ imm().showSoftInput(input, 0) }, 150)
        armInputClose()
    }

    private fun collapse() {
        // a running send countdown keeps the island (and its Cancel chip) open; the message goes out at 0 unless cancelled
        if (pendingRun != null) return
        h.removeCallbacks(closeTask); errorPinned = false
        choiceActive = false; replying = false
        dockAnim?.let { it.removeAllListeners(); it.cancel() }; dockAnim = null
        voice.stopListening(); voice.stopSpeaking()
        imm().hideSoftInputFromWindow(input.windowToken, 0)
        h.removeCallbacks(speakPoll)
        hide(input)
        typeAnim?.let { it.removeAllListeners(); it.cancel() }; typeAnim = null
        if (answer.visibility == View.VISIBLE) answer.text = fullAnswer
        setWave(WaveView.NONE)
        style(false); setMode(baseMood()); focusable(false)
    }

    /** The user tapped the mascot while an ask is in flight: forget it (its late result is ignored) and close. */
    private fun cancelAsk() {
        agentCancel?.set(true)
        if (localApproval) { localApproval = false; approvalHolder.visibility = View.GONE; approvalHolder.removeAllViews() }
        if (agentStepping) { agentStepping = false; hide(chipsScroll) }
        reqId++
        h.removeCallbacks(askDeadline)
        busy = false
        if (pendingRun == null) collapse()
        else { setWave(WaveView.NONE); status.text = t("Отправка", "Sending", "Yuborish"); setMode("listen") }
    }

    private val askDeadline = Runnable {
        if (busy) {
            reqId++   // drop whatever the worker returns later
            finish(Result.failure(RuntimeException("deadline")))
        }
    }

    private fun refusePending() {
        showActionError(t("Подождите, сообщение ещё отправляется", "Wait, a message is still being sent", "Kuting, xabar hali yuborilmoqda"))
        h.removeCallbacks(closeTask)
        h.postDelayed({
            if (pendingRun != null) { status.text = t("Отправка", "Sending", "Yuborish"); setMode("listen"); renderPending() }
        }, 2500)
    }

    private fun ask(text: String, spoken: Boolean, untrusted: Boolean = false) {
        if (pendingRun != null) { refusePending(); return }
        h.removeCallbacks(closeTask); errorPinned = false
        lastAsk = text; lastSpoken = spoken; lastUntrusted = untrusted
        busy = true; setMode("think"); status.text = t("Думаю…", "Thinking…", "O'ylayapman…"); setWave(WaveView.THINK)
        val id = ++reqId
        h.removeCallbacks(askDeadline)
        clearChoices()
        hide(chipsScroll)
        imm().hideSoftInputFromWindow(input.windowToken, 0)
        hide(input); hide(answer)
        clearSticker()
        focusable(false)
        // offline instant commands: no network, no quota, brain history untouched
        // text that came from another app (share sheet) never triggers offline device commands
        val quick = if (untrusted) null else try { QuickIntent.match(this, text) } catch (_: Throwable) { null }
        if (quick != null) {
            finish(Result.success(Reply(quick.emotion, replyLang(), quick.say, quick.actions)))
            return
        }
        val alts = if (spoken) voice.lastAlternatives else emptyList()
        h.postDelayed(askDeadline, 90_000L)
        BuddyAccess.newRun(text)
        val cancel = java.util.concurrent.atomic.AtomicBoolean(false)
        agentCancel = cancel; agentStepping = false
        Thread {
            val r = try {
                Result.success(brain.ask(text, spoken, alts, host = PhoneHost(this, agentUi(id, cancel), text), untrusted = untrusted))
            } catch (e: Throwable) { Result.failure(e) }
            h.post {
                if (id == reqId) { h.removeCallbacks(askDeadline); finish(r) }
            }
        }.start()
    }

    /** Asks the user, on the island, whether Bubby may read the screen for this task. Blocks the worker thread up to 30 s. */
    private fun askScreenConsent(): Boolean {
        val latch = java.util.concurrent.CountDownLatch(1)
        var yes = false
        fun answer(ok: Boolean) { if (latch.count == 0L) return; yes = ok; latch.countDown(); approvalHolder.visibility = View.GONE; approvalHolder.removeAllViews(); setMode("think") }
        h.post {
            voice.stopListening()
            style(true, force = true); status.text = t("Можно посмотреть экран?", "May I look at the screen?", "Ekranga qarasam bo'ladimi?"); setMode("curious"); setWave(WaveView.NONE)
            hide(input); hide(answer)
            approvalHolder.removeAllViews()
            val a = Approval("screen-consent", "screen-consent", t("Чтение экрана", "Reading the screen", "Ekranni o'qish"),
                t("Для этой задачи Bubby нужно прочитать текст на экране. Банковские и платёжные приложения он не читает никогда.",
                    "For this task Bubby needs to read the text on your screen. He never reads banking and payment apps.",
                    "Bu vazifa uchun Bubby ekrandagi matnni o'qishi kerak. Bank va to'lov ilovalarini hech qachon o'qimaydi."), System.currentTimeMillis(), false)
            approvalHolder.addView(ApprovalCard.build(this, a, false, onAllow = { answer(true) }, onDeny = { answer(false) }))
            approvalHolder.alpha = 1f; approvalHolder.visibility = View.VISIBLE
            focusable(false)
        }
        val got = try { latch.await(30, java.util.concurrent.TimeUnit.SECONDS) } catch (_: InterruptedException) { false }
        if (!got) h.post { answer(false) }
        return got && yes
    }

    private fun cancelledText() = t("Отменено", "Cancelled", "Bekor qilindi")

    /** The agent loop's view of the island: progress text, the Stop chip, approvals, actions on the main thread. */
    private fun agentUi(id: Int, cancel: java.util.concurrent.atomic.AtomicBoolean) = object : AgentUi {
        override fun cancelled() = cancel.get() || id != reqId || !running
        override fun progress(label: String) {
            h.post {
                if (id != reqId || !busy || !running) return@post
                // each step proves the run is alive: the deadline moves with it (the loop has its own 4 min budget)
                h.removeCallbacks(askDeadline); h.postDelayed(askDeadline, 90_000L)
                if (!agentStepping) {
                    agentStepping = true
                    showActionChip(t("Стоп", "Stop", "To'xtat")) { cancelAsk() }
                    hotKick()
                }
                status.text = label.take(40)
                setMode("think"); setWave(WaveView.THINK)
            }
        }
        override fun approve(req: ApprovalReq): Boolean = if (cancelled()) false else askApproval(req, cancel)
        override fun runAction(a: JSONObject): String? = if (cancelled()) cancelledText() else runOnMainBlocking(a)
    }

    /** Shows Bubby's own approval card for one agent action and blocks the worker until the user answers (60 s = no). */
    private fun askApproval(req: ApprovalReq, cancel: java.util.concurrent.atomic.AtomicBoolean): Boolean {
        val latch = java.util.concurrent.CountDownLatch(1)
        var yes = false
        fun answer(ok: Boolean) {
            if (latch.count == 0L) return
            yes = ok; latch.countDown()
            localApproval = false
            approvalHolder.visibility = View.GONE; approvalHolder.removeAllViews()
            if (busy) { setMode("think"); status.text = t("Думаю…", "Thinking…", "O'ylayapman…") }
            if (approvals.isNotEmpty()) showApproval(announce = false)
        }
        h.post {
            if (!running || cancel.get()) { answer(false); return@post }
            voice.stopListening()
            localApproval = true
            h.removeCallbacks(askDeadline); h.postDelayed(askDeadline, 120_000L)
            style(true, force = true); status.text = t("Подтвердите", "Please confirm", "Tasdiqlang"); setMode("surprised"); setWave(WaveView.NONE)
            flashBadge("approval", 60_000L)
            hide(input); hide(answer)
            approvalHolder.removeAllViews()
            val a = Approval("bubby-agent", "bubby-agent", req.title, req.detail, System.currentTimeMillis(), false)
            approvalHolder.addView(ApprovalCard.build(this, a, false, onAllow = {
                if (req.lock) ConfirmActivity.request(this) { ok ->
                    if (!ok) status.text = t("Нужен экран блокировки", "Lock screen required", "Ekran qulfi kerak")
                    answer(ok)
                } else answer(true)
            }, onDeny = { answer(false) }, riskLevel = if (req.lock) IslandUi.RISK_HIGH else IslandUi.RISK_MID))
            approvalHolder.alpha = 1f; approvalHolder.visibility = View.VISIBLE
            focusable(false)
            if (ttsOn()) say(req.title, uiLang()) {}
        }
        var got = false
        val until = System.currentTimeMillis() + 60_000L
        try {
            while (!got && System.currentTimeMillis() < until) {
                got = latch.await(250, java.util.concurrent.TimeUnit.MILLISECONDS)
                if (!got && cancel.get()) break
            }
        } catch (_: InterruptedException) { }
        if (!got) h.post { answer(false) }
        return got && yes
    }

    /** Runs an action; a new `message` is refused while another one is still counting down. */
    private fun runAction(a: JSONObject): String? {
        if (pendingRun != null && a.optString("do") == "message") {
            return t("Подождите, сообщение ещё отправляется", "Wait, a message is still being sent", "Kuting, xabar hali yuborilmoqda")
        }
        return actions.run(a)
    }

    /** Runs one action on the main thread and waits (max 6 s) for it; returns the error text (also on timeout / exception) or null. */
    private fun runOnMainBlocking(a: JSONObject): String? {
        var err: String? = null
        val latch = java.util.concurrent.CountDownLatch(1)
        h.post {
            try { err = runAction(a) }
            catch (_: Throwable) { err = t("Не удалось выполнить действие", "Couldn't do that", "Buni bajarib bo'lmadi") }
            finally { latch.countDown() }
        }
        return if (latch.await(6, java.util.concurrent.TimeUnit.SECONDS)) err
        else t("Действие не успело выполниться", "The action took too long", "Amal juda uzoq davom etdi")
    }

    private fun finish(r: Result<Reply>) {
        if (!running) return
        busy = false
        h.removeCallbacks(askDeadline)
        BuddyAccess.endRun()
        agentCancel = null
        if (agentStepping) { agentStepping = false; chipsRow.removeAllViews(); hide(chipsScroll) }
        if (calling) return
        r.onSuccess { reply ->
            val emo = reply.emotion
            status.text = Moods.label(this, emo)
            setWave(WaveView.NONE)
            setMode(emo)
            if (reply.actions.length() > 0) flashBadge("done", 2200L)
            h.postDelayed({ if (mode == emo) setMode(baseMood()) }, 6000)
            val errors = ArrayList<String>()
            var hadSticker = false
            val inlineStickers = ArrayList<Pair<String, String>>()
            for (i in 0 until reply.actions.length()) {
                val a = reply.actions.optJSONObject(i) ?: continue
                if (a.optString("do") == "sticker") {
                    hadSticker = true
                    if (!a.optBoolean("share", false)) {
                        val m = if (a.isNull("mood")) "" else a.optString("mood", "").trim()
                        val c = if (a.isNull("caption")) "" else a.optString("caption", "").trim()
                        inlineStickers.add(m.ifEmpty { "happy" } to c)
                        continue
                    }
                }
                runAction(a)?.let { errors.add(it) }
            }
            val failed = errors.isNotEmpty()
            // honest errors: never present the model's optimistic text as if it had worked
            val shown = if (failed) "⚠ " + errors.joinToString("; ") else reply.say
            val spokenText = if (failed) errors.joinToString(". ") else reply.say
            if (failed) {
                status.text = t("Ошибка", "Error", "Xato")
                setMode("sad")
                h.postDelayed({ if (mode == "sad") setMode(baseMood()) }, 4000)
            }
            setAnswer(shown, !failed); if (shown.isBlank()) hide(answer) else show(answer)
            if (!failed) inlineStickers.lastOrNull()?.let { showInlineSticker(it.first, it.second) }
            if (!failed && !hadSticker && !lastReplyHadSticker && prefs.getBoolean("sticker_auto", true) && emo in AUTO_STICKER_MOODS &&
                reply.say.length <= 160 && !calling && !waiting() && Random.nextFloat() < 0.35f) {
                hadSticker = true
                showInlineSticker(emo, "")
            }
            lastReplyHadSticker = hadSticker
            refreshChips()
            if (pendingRun != null) renderPending() else if (choiceActive) renderChoices()
            val next = {
                if (conv() && voiceOn() && !failed && !calling && !choiceActive) startVoice()
                else h.postDelayed(closeTask, 2500)
            }
            if (ttsOn() && spokenText.isNotBlank()) say(spokenText, if (failed) uiLang() else reply.lang) { next() }
            else h.postDelayed(closeTask, 8000L + shown.length * 50L)
        }.onFailure {
            val raw = it.javaClass.simpleName + " " + (it.message ?: "")
            val t = lastAsk; val sp = lastSpoken; val ut = lastUntrusted
            showError(raw) { ask(t, sp, ut) }
        }
    }

    private fun showAsyncError(msg: String) {
        if (!running || calling || busy) return
        h.removeCallbacks(closeTask)
        if (!expanded) style(true)
        status.text = t("Ошибка", "Error", "Xato"); hide(input); setAnswer(msg, false); show(answer)
        setWave(WaveView.NONE); setMode("sad")
        errorPinned = false
        h.postDelayed({ if (mode == "sad") setMode(baseMood()) }, 2500)
        h.postDelayed(closeTask, 8000)
    }

    /** Renders a sticker off the main thread and pops it in above the answer. */
    private fun showInlineSticker(mood: String, caption: String) {
        val gen = ++stickerGen
        stickerMood = mood; stickerCaption = caption
        val outfit = Outfits.parse(prefs.getString("outfit", "").orEmpty())
        Thread {
            val bmp = try { Stickers.render(this, mood, caption, outfit) } catch (_: Throwable) { null }
            h.post {
                if (!running || gen != stickerGen || !expanded) { bmp?.recycle(); return@post }
                val b = bmp ?: try { Stickers.render(this, mood, caption, outfit) } catch (_: Throwable) { null } ?: return@post
                stickerBmp?.let { stickerIv.setImageDrawable(null); it.recycle() }
                stickerBmp = b
                stickerIv.setImageBitmap(b)
                stickerIv.animate().cancel()
                stickerIv.visibility = View.VISIBLE
                stickerIv.pivotX = dp(48).toFloat(); stickerIv.pivotY = dp(48).toFloat()
                if (IslandUi.animOn(this)) {
                    stickerIv.scaleX = 0.3f; stickerIv.scaleY = 0.3f
                    stickerIv.animate().scaleX(1f).scaleY(1f).setDuration(320)
                        .setInterpolator(OvershootInterpolator(2f)).start()
                } else { stickerIv.scaleX = 1f; stickerIv.scaleY = 1f }
                win.post { clamp() }
                h.postDelayed({ clamp() }, 80)
            }
        }.start()
    }

    private fun clearSticker() {
        stickerGen++
        if (!::stickerIv.isInitialized) return
        stickerIv.animate().cancel()
        stickerIv.visibility = View.GONE
        stickerIv.setImageDrawable(null)
        stickerBmp?.recycle(); stickerBmp = null
    }

    private fun shareInlineSticker() {
        val m = stickerMood; val c = stickerCaption
        status.text = t("Выбери, куда отправить стикер", "Pick where to send the sticker", "Stikerni qayerga yuborishni tanlang")
        Thread {
            val err = try { Stickers.share(this, m, c) } catch (_: Throwable) { null }
            if (err != null) h.post { if (running) status.text = err }
        }.start()
    }

    /** Maps a raw failure to a human message and the kind of action chip ("open", "retry" or null). */
    private fun friendly(raw: String): Pair<String, String?> {
        val r = raw.lowercase()
        return when {
            "не задан ключ" in r -> t("Подключите ИИ — это бесплатно", "Connect an AI — it's free", "AI'ni ulang — bu bepul") to "open"
            "http 401" in r || "http 403" in r -> t("Ключ ИИ не подходит. Откройте Ещё → Подключение ИИ", "The AI key doesn't work. Open More → AI connection", "AI kaliti mos kelmadi. Yana → AI ulash bo'limini oching") to "open"
            "http 429" in r || "лимит" in r -> t("Лимит ИИ на сегодня исчерпан. Подключите запасной сервис в Ещё → Подключение ИИ", "Today's AI limit is used up. Add a backup service in More → AI connection", "Bugungi AI limiti tugadi. Yana → AI ulash bo'limida zaxira xizmatni ulang") to "open"
            "микрофон" in r || "(9)" in r || "permission" in r -> t("Разрешите микрофон, чтобы я вас слышал", "Allow the microphone so I can hear you", "Sizni eshitishim uchun mikrofonga ruxsat bering…") to "open"
            "нет сети для распознавания" in r -> t("Нужен интернет для распознавания речи", "Internet is needed for speech recognition", "Nutqni tanish uchun internet kerak") to "retry"
            "ошибка распознавания" in r -> t("Не расслышал, повторите", "Didn't catch that, please repeat", "Eshitmadim, qaytaring") to "retry"
            "unknownhost" in r || "unable to resolve" in r || "connectexception" in r || "failed to connect" in r ||
                "timeout" in r || "timed out" in r || "unreachable" in r || "ssl" in r || "нет сети" in r ||
                "connection" in r -> t("Нет интернета. Проверьте сеть и повторите", "No internet. Check your connection and try again", "Internet yo'q. Tarmoqni tekshirib, qayta urining") to "retry"
            else -> t("Что-то пошло не так. Попробуйте ещё раз", "Something went wrong. Please try again", "Nimadir xato ketdi. Qayta urinib ko'ring") to "retry"
        }
    }

    private fun showError(raw: String, retry: (() -> Unit)? = null) {
        val (msg, kind) = friendly(raw)
        val lr = raw.lowercase()
        flashBadge(if ("http 429" in lr || "лимит" in lr) "limit" else "error", 5000L)
        h.removeCallbacks(closeTask)
        if (!expanded) style(true)
        status.text = t("Ошибка", "Error", "Xato"); hide(input); setAnswer(msg, false); show(answer)
        setWave(WaveView.NONE); setMode("sad")
        errorPinned = false
        when {
            kind == "open" -> { showActionChip(t("Открыть Bubby", "Open Bubby", "Bubby'ni ochish")) { openBubby() }; errorPinned = true }
            kind == "retry" && retry != null -> {
                showActionChip(t("Повторить", "Retry", "Qaytarish")) { errorPinned = false; retry() }; errorPinned = true
            }
        }
        h.postDelayed({ if (mode == "sad") setMode(baseMood()) }, 2500)
        h.postDelayed(closeTask, 15000)
    }

    private fun remind(text: String) {
        if (!running) return
        if (text.isBlank() || calling) return
        h.removeCallbacks(closeTask)
        voice.stopListening()
        val prefix = t("Напоминание: ", "Reminder: ", "Eslatma: ")
        style(true, force = true); status.text = t("Напоминание", "Reminder", "Eslatma"); setMode("excited")
        hide(input); setAnswer(prefix + text, true); show(answer)
        focusable(false)
        if (ttsOn()) say(prefix + text, uiLang()) { h.postDelayed(closeTask, 4000) }
        else h.postDelayed(closeTask, 10000)
        h.postDelayed({ if (mode == "excited") setMode(baseMood()) }, 6000)
    }

    private fun onRinging(name: String?) {
        if (name != null) callName = name
        if (calling) return
        calling = true
        hotStop()
        h.removeCallbacks(closeTask)
        voice.stopListening(); voice.stopSpeaking()
        // the first broadcast often has no number yet; wait a moment for the caller name
        h.postDelayed({ if (calling) announceCall() }, 700)
    }

    private fun announceCall() {
        val l = uiLang()
        val who = callName ?: when (l) { "uz" -> "noma'lum raqam"; "ru" -> "неизвестный номер"; else -> "an unknown number" }
        val text = when (l) {
            "uz" -> "$who qo'ng'iroq qilyapti. Javob beraymanmi?"
            "ru" -> "Звонит $who. Ответить?"
            else -> "$who is calling. Answer?"
        }
        releaseCallFocus()
        callFocusRelease = try { MediaControl.requestFocusForSpeech(this) } catch (_: Exception) { null }
        style(true, force = true); status.text = t("Звонок", "Call", "Qo'ng'iroq"); setMode("surprised")
        hide(input); setAnswer(text, true); show(answer)
        focusable(false)
        showCallChips()
        say(text, l) { listenForCall(0) }
        if (prefs.getBoolean("call_auto", false)) h.postDelayed({ if (calling && !callHandled) callDo(if (prefs.getBoolean("call_talk", false)) "talk" else "answer") }, 7000)
    }

    private var callHandled = false

    /** Answer / decline buttons for the ringing call, so it also works by touch when the voice is not heard over the ringtone. */
    private fun showCallChips() {
        callHandled = false
        chipsRow.removeAllViews()
        val lp = { LinearLayout.LayoutParams(-2, -2).apply { rightMargin = dp(6) } }
        chipsRow.addView(chip(t("Ответить", "Answer", "Javob berish")) { callDo("answer") }, lp())
        chipsRow.addView(chip(t("Ответит ИИ", "AI answers", "AI javob beradi")) { callDo("talk") }, lp())
        chipsRow.addView(chip(t("Сбросить", "Decline", "Rad etish")) { callDo("reject") }, lp())
        chipsRow.addView(chip(t("Сбросить + SMS", "Decline + text", "Rad + SMS")) { callDo("reject_text") }, lp())
        chipsScroll.animate().cancel()
        chipsScroll.alpha = 1f; chipsScroll.translationY = 0f; chipsScroll.visibility = View.VISIBLE
    }

    private fun callDo(kind: String) {
        if (!calling || callHandled) return
        callHandled = true
        releaseCallFocus()
        voice.stopListening(); voice.stopSpeaking()
        callTalk = kind == "talk"
        val err = when (kind) {
            "answer", "talk" -> actions.run(JSONObject().put("do", "answer_call"))
            "reject_text" -> actions.run(JSONObject().put("do", "reject_call").put("text", AutoReply.defaultText(this, "busy")))
            else -> actions.run(JSONObject().put("do", "end_call"))
        }
        chipsRow.removeAllViews(); hide(chipsScroll)
        status.text = err ?: when (kind) {
            "answer", "talk" -> t("Отвечаю", "Answering", "Javob beryapman")
            "reject_text" -> t("Сбросил, пишу", "Declined, texting", "Rad etdim, xabar yuboryapman")
            else -> t("Сбросил", "Declined", "Rad etdim")
        }
        if (err != null) { callHandled = false; callTalk = false }
    }

    private fun listenForCall(attempt: Int) {
        if (!calling) return
        if (!micOk()) return
        voice.listen(langTag(), {}, { text, _ ->
            if (calling) {
                val t = text?.lowercase().orEmpty()
                when {
                    CallWatcher.looksLikeRejectAndText(t) -> callDo("reject_text")
                    CallWatcher.looksLikeReject(t) -> callDo("reject")
                    CallWatcher.looksLikeAnswer(t) -> callDo("answer")
                    attempt < 25 && !callHandled -> h.postDelayed({ listenForCall(attempt + 1) }, 300)
                }
            }
        })
    }

    // ---- the AI talks on the call ---------------------------------------------------------------------------------
    // Android gives apps no access to the call audio, so this works through the speakerphone: the speech goes out loud,
    // the phone's microphone hears the caller. It depends on the phone letting the recogniser use the microphone in a call.
    private var callTalk = false
    private var callSilence = 0
    private val callLog = ArrayList<Pair<String, String>>()
    private var callTalkWho: String? = null
    private val callTalkLimit = Runnable { if (callTalk) callTalkEnd(true) }

    private fun onCallAnswered() {
        if (!calling) return
        if (!callTalk) { onCallEnded(); return }
        callTalkStart()
    }

    private fun callSystem(): String = "You are Bubby, the phone assistant of the owner of this phone. The owner cannot pick up right now, so you answered. " +
        "You are on a live call with ${callTalkWho ?: "an unknown caller"}. You are an AI assistant: say so honestly if asked, and never pretend to be the owner. " +
        "Find out who is calling and what they need, take a message, and say the owner will get back to them. " +
        "Do not give out personal information, passwords, codes or the owner's whereabouts, and do not agree to payments or promises. " +
        "Speak the caller's language (Uzbek, Russian or English). Reply with at most two short spoken sentences, no lists, no emojis. " +
        "Start every reply with a language tag: [uz], [ru] or [en]. When the conversation is finished, say goodbye and add [END] at the very end."

    private fun callTalkStart() {
        callTalkWho = callName
        callLog.clear(); callSilence = 0
        try {
            val am = getSystemService(Context.AUDIO_SERVICE) as AudioManager
            @Suppress("DEPRECATION") am.isSpeakerphoneOn = true
        } catch (_: Exception) {}
        voice.callAudio = true
        status.text = t("Разговор", "On the call", "Suhbatda"); setMode("listen")
        val l = uiLang()
        val greet = when (l) {
            "uz" -> "Assalomu alaykum, men Bubby, telefon egasining yordamchisiman. Hozir u javob bera olmaydi. Xabaringizni ayta olasizmi?"
            "ru" -> "Здравствуйте, я Bubby, помощник владельца телефона. Сейчас он не может ответить. Что передать?"
            else -> "Hello, this is Bubby, the phone owner's assistant. They can't pick up right now. Can I take a message?"
        }
        callLog.add("assistant" to greet)
        setAnswer(greet, false); show(answer)
        h.removeCallbacks(callTalkLimit); h.postDelayed(callTalkLimit, 180_000)
        // a moment for the line and the speaker to settle
        h.postDelayed({ if (callTalk) say(greet, l) { callTalkListen() } }, 600)
    }

    private fun callTalkListen() {
        if (!callTalk) return
        if (!micOk()) { callTalkEnd(true); return }
        voice.listen(langTag(), { p -> if (callTalk) setAnswer(p, false) }, { text, _ ->
            if (callTalk) {
                if (text.isNullOrBlank()) {
                    if (++callSilence >= 4) { callTalkEnd(true); return@listen }
                    h.postDelayed({ callTalkListen() }, 200)
                } else {
                    callSilence = 0
                    callLog.add("user" to text)
                    setAnswer(text, false)
                    val turns = ArrayList(callLog)
                    Thread {
                        val out = try { brain.chatOnce(callSystem(), turns) } catch (_: Exception) { "" }
                        h.post { if (callTalk) callTalkReply(out) }
                    }.start()
                }
            }
        })
    }

    private fun callTalkReply(raw: String) {
        if (raw.isBlank()) { callTalkListen(); return }
        val end = raw.contains("[END]", true)
        val tag = Regex("^\\s*\\[(uz|ru|en)]", RegexOption.IGNORE_CASE).find(raw)?.groupValues?.get(1)?.lowercase()
        val text = raw.replace(Regex("\\[(uz|ru|en|END)]", RegexOption.IGNORE_CASE), "").trim()
        callLog.add("assistant" to text)
        setAnswer(text, false)
        say(text, tag ?: uiLang()) { if (end) callTalkEnd(true) else callTalkListen() }
    }

    /** Ends the conversation (hangs up when [hangUp]) and tells the owner what it was about. */
    private fun callTalkEnd(hangUp: Boolean) {
        if (!callTalk) return
        callTalk = false
        h.removeCallbacks(callTalkLimit)
        voice.stopListening(); voice.stopSpeaking()
        voice.callAudio = false
        if (hangUp) try { actions.run(JSONObject().put("do", "end_call")) } catch (_: Exception) {}
        try {
            val am = getSystemService(Context.AUDIO_SERVICE) as AudioManager
            @Suppress("DEPRECATION") am.isSpeakerphoneOn = false
        } catch (_: Exception) {}
        val log = ArrayList(callLog); val who = callTalkWho
        if (log.none { it.first == "user" }) { status.text = t("Звонок завершён", "Call ended", "Qo'ng'iroq tugadi"); return }
        Thread {
            val l = uiLang()
            val sum = try {
                brain.chatOnce("Summarize this phone call for the phone's owner in one or two short sentences, in " +
                    (when (l) { "uz" -> "Uzbek (Latin)"; "ru" -> "Russian"; else -> "English" }) +
                    ". Say who called and what they want. The caller: ${who ?: "unknown"}.",
                    listOf("user" to log.joinToString("\n") { (if (it.first == "user") "Caller: " else "Assistant: ") + it.second }))
            } catch (_: Exception) { log.filter { it.first == "user" }.joinToString(" ") { it.second } }
            prefs.edit().putString("last_call_summary", (who ?: "?") + ": " + sum).apply()
            h.post {
                h.removeCallbacks(closeTask)
                style(true, force = true); status.text = t("Звонок", "Call", "Qo'ng'iroq"); setMode("happy")
                setAnswer((who ?: "") + (if (who != null) ": " else "") + sum, false); show(answer)
                h.postDelayed(closeTask, 30_000)
            }
        }.start()
    }

    private fun onCallEnded() {
        if (callTalk) callTalkEnd(false)
        if (!calling) return
        calling = false; callName = null; callHandled = false
        chipsRow.removeAllViews(); hide(chipsScroll)
        releaseCallFocus()
        voice.stopListening(); voice.stopSpeaking()
        collapse()
    }

    private fun releaseCallFocus() {
        val f = callFocusRelease ?: return
        callFocusRelease = null
        try { f() } catch (_: Exception) {}
    }

    // ---- choice chips -------------------------------------------------------------------------------------------
    private var choicePrompt = ""
    private var choiceOpts: List<Pair<String, JSONObject>> = emptyList()

    // ---- cancel countdown before an outgoing message ------------------------------------------------------------
    private fun showPending(prompt: String, seconds: Int, run: () -> Unit) {
        if (!running) return
        if (seconds <= 0 || calling) { if (!calling) run(); return }
        if (pendingRun != null) { refusePending(); return }   // backstop: never replace a message that is counting down
        h.removeCallbacks(closeTask)
        voice.stopListening()
        pendingRun = run; pendingLeft = seconds; pendingPrompt = prompt
        flashBadge("approval", seconds * 1000L + 500L)
        choiceActive = true; errorPinned = false
        style(true)
        setWave(WaveView.NONE)
        if (!busy) { status.text = t("Отправка", "Sending", "Yuborish"); setMode("listen") }
        renderPending()
        focusable(false)
        h.postDelayed(pendingTick, 1000)
    }

    private fun renderPending() {
        if (pendingRun == null) return
        hide(input)
        setAnswer(pendingPrompt, false); show(answer)
        chipsRow.removeAllViews()
        val label = t("Отмена ($pendingLeft)", "Cancel ($pendingLeft)", "Bekor ($pendingLeft)")
        chipsRow.addView(chip(label) { userCancelPending() }, LinearLayout.LayoutParams(-2, -2).apply { rightMargin = dp(6) })
        chipsScroll.animate().cancel()
        chipsScroll.alpha = 1f; chipsScroll.translationY = 0f; chipsScroll.visibility = View.VISIBLE
    }

    private fun pendingStep() {
        val run = pendingRun ?: return
        pendingLeft--
        if (pendingLeft > 0) {
            renderPending()
            h.postDelayed(pendingTick, 1000)
            return
        }
        pendingRun = null
        choiceActive = false
        chipsRow.removeAllViews(); hide(chipsScroll)
        status.text = t("Готово", "Done", "Tayyor"); setMode("happy")
        h.postDelayed({ if (mode == "happy") setMode(baseMood()) }, 2000)
        h.postDelayed(closeTask, 3500)
        try { run() } catch (_: Throwable) { }
    }

    /** Silent cancel: no UI changes (callers handle the rest). */
    private fun cancelPending() {
        h.removeCallbacks(pendingTick)
        if (pendingRun == null) return
        pendingRun = null
        choiceActive = false
        chipsRow.removeAllViews()
    }

    private fun userCancelPending() {
        if (pendingRun == null) return
        cancelPending()
        hide(chipsScroll)
        status.text = t("Отменено", "Cancelled", "Bekor qilindi"); setMode("sad")
        setAnswer(t("Отменено", "Cancelled", "Bekor qilindi"), false); show(answer)
        h.postDelayed({ if (mode == "sad") setMode(baseMood()) }, 2000)
        h.postDelayed(closeTask, 1500)
    }

    private fun clearChoices() {
        if (!choiceActive) return
        choiceActive = false; choiceOpts = emptyList()
        chipsRow.removeAllViews(); hide(chipsScroll)
    }

    private fun showChoices(prompt: String, options: List<Pair<String, JSONObject>>) {
        if (!running || calling || options.isEmpty()) return
        h.removeCallbacks(closeTask)
        voice.stopListening()
        choicePrompt = prompt; choiceOpts = options; choiceActive = true
        flashBadge("approval", 15_000L)
        errorPinned = false
        style(true)
        setWave(WaveView.NONE)
        if (!busy) {
            status.text = t("Выберите", "Choose", "Tanlang"); setMode("listen")
            if (ttsOn() && prompt.isNotBlank()) say(prompt, uiLang()) {}
        }
        renderChoices()
        focusable(false)
    }

    /** Draws the prompt and the option chips (also re-run by finish() so a late reply cannot wipe them). */
    private fun renderChoices() {
        if (!choiceActive) return
        hide(input)
        setAnswer(choicePrompt, false)
        if (choicePrompt.isBlank()) hide(answer) else show(answer)
        chipsRow.removeAllViews()
        for ((label, json) in choiceOpts) {
            chipsRow.addView(chip(label) { pickChoice(json) }, LinearLayout.LayoutParams(-2, -2).apply { rightMargin = dp(6) })
        }
        chipsScroll.animate().cancel()
        chipsScroll.alpha = 1f; chipsScroll.translationY = 0f; chipsScroll.visibility = View.VISIBLE
    }

    private fun pickChoice(opt: JSONObject) {
        if (!choiceActive) return
        voice.stopSpeaking()
        h.removeCallbacks(closeTask)
        choiceActive = false; choiceOpts = emptyList()
        chipsRow.removeAllViews(); hide(chipsScroll)
        if (opt.optString("do") == "noop") { collapse(); return }
        val err = try { runAction(opt) } catch (e: Throwable) { e.message ?: "error" }
        if (err != null) showActionError(err)
        else if (pendingRun != null) {
            // a message countdown has just started: showPending already drew its status and the cancel chip
        } else {
            status.text = t("Готово", "Done", "Tayyor"); setMode("happy")
            h.postDelayed({ if (mode == "happy") setMode(baseMood()) }, 2000)
            h.postDelayed(closeTask, 1500)
        }
    }

    private fun showActionError(msg: String) {
        h.removeCallbacks(closeTask)
        if (!expanded) style(true)
        status.text = t("Ошибка", "Error", "Xato"); hide(input); setAnswer("⚠ $msg", false); show(answer)
        setWave(WaveView.NONE); setMode("sad")
        h.postDelayed({ if (mode == "sad") setMode(baseMood()) }, 2500)
        h.postDelayed(closeTask, 6000)
        if (ttsOn()) say(msg, uiLang()) {}
    }

    // ---- device reactions ---------------------------------------------------------------------------------------
    /** Shows [text] for ~3 s under the mascot, optionally with an inline sticker. */
    private fun toastReact(text: String, mood: String, sticker: Boolean) {
        h.removeCallbacks(closeTask)
        style(true)
        status.text = Moods.label(this, mood); setMode(mood); setWave(WaveView.NONE)
        hide(input); chipsRow.removeAllViews(); hide(chipsScroll)
        setAnswer(text, true); show(answer)
        if (sticker) showInlineSticker(mood, "")
        focusable(false)
        h.postDelayed({ if (mode == mood) setMode(baseMood()) }, 3000)
        h.postDelayed(closeTask, 3000)
    }

    private fun onDeviceEvent(e: String) {
        if (!running) return
        if (!prefs.getBoolean("device_reactions", true) || prefs.getBoolean("quiet", false)) return
        if (!calm() || !winShown || voice.listening || voice.talking || choiceActive || replying) return
        val tn = SystemClock.elapsedRealtime()
        if (lastReact != 0L && tn - lastReact < 30_000L) return
        val l = uiLang()
        when (e) {
            "charging_started" -> {
                lastReact = tn
                buddy.flash("happy", 3000)
                toastReact(t("Ммм, зарядка!", "Mmm, charging!", "Mmm, quvvat!"), "happy", prefs.getBoolean("stickers_on_events", true))
            }
            "battery_low" -> {
                lastReact = tn
                buddy.flash("worried", 4000)
                val msg = t("Заряд на исходе…", "Battery is low…", "Batareya tugayapti…")
                if (ttsOn()) say(msg, l) {} else toastReact(msg, "worried", false)
            }
            "headphones_in" -> { lastReact = tn; buddy.flash("dance", 4000) }
            "headphones_out" -> { lastReact = tn; buddy.flash("sad", 2500) }
            "battery_full" -> { lastReact = tn; buddy.flash("proud", 3500) }
            "welcome_back" -> {
                if (lastGreet != 0L && tn - lastGreet < 60_000L) return
                lastReact = tn; lastGreet = tn
                buddy.wake(); buddy.flash("happy", 3500)
                val locked = try { getSystemService(KeyguardManager::class.java).isKeyguardLocked } catch (_: Exception) { false }
                if (ttsOn() && !locked) {
                    val hr = hour()
                    val msg = when {
                        hr in 5..10 -> t("Доброе утро!", "Good morning!", "Xayrli tong!")
                        hr in 11..16 -> t("Добрый день!", "Good afternoon!", "Xayrli kun!")
                        hr in 17..21 -> t("Добрый вечер!", "Good evening!", "Xayrli kech!")
                        else -> t("Доброй ночи!", "Good night!", "Xayrli tun!")
                    }
                    say(msg, l) {}
                }
            }
        }
    }

    // ---- notifications aloud ------------------------------------------------------------------------------------
    /** Headphones (wired, USB or Bluetooth) are the audio output right now. */
    private fun headsetOn(): Boolean = try {
        (getSystemService(AUDIO_SERVICE) as AudioManager).getDevices(AudioManager.GET_DEVICES_OUTPUTS).any {
            it.type in setOf(android.media.AudioDeviceInfo.TYPE_WIRED_HEADSET, android.media.AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
                android.media.AudioDeviceInfo.TYPE_BLUETOOTH_A2DP, android.media.AudioDeviceInfo.TYPE_BLUETOOTH_SCO,
                android.media.AudioDeviceInfo.TYPE_USB_HEADSET, android.media.AudioDeviceInfo.TYPE_BLE_HEADSET)
        }
    } catch (_: Throwable) { false }

    private fun onNotifItem(item: BuddyNotifications.Item) {
        if (!running || !prefs.getBoolean(if (item.sms) "read_sms_aloud" else "read_notifs", false) || prefs.getBoolean("quiet", false)) return
        if (!screenOn || calling || !calm() || voice.listening || voice.talking || choiceActive || replying) return
        if (prefs.getBoolean("read_private", true) && !headsetOn()) return   // nobody else should hear a message read aloud
        if (try { getSystemService(KeyguardManager::class.java).isKeyguardLocked } catch (_: Exception) { false }) return
        var body = if (item.title.isBlank()) item.text else if (item.text.isBlank()) item.title else item.title + " — " + item.text
        body = (item.app + ": " + body).trim()
        if (body.length > 140) body = body.take(139).trimEnd() + "…"
        h.removeCallbacks(closeTask)
        style(true)
        status.text = t("Сообщение", "Message", "Xabar"); setMode("surprised"); setWave(WaveView.NONE)
        hide(input); setAnswer(body, true); show(answer)
        chipsRow.removeAllViews()
        val m = LinearLayout.LayoutParams(-2, -2).apply { rightMargin = dp(6) }
        if (item.canReply) chipsRow.addView(chip(t("Ответить", "Reply", "Javob berish")) { replyToNotif(item) }, m)
        chipsRow.addView(chip(t("Пропустить", "Skip", "O'tkazish")) { collapse() }, m)
        chipsScroll.animate().cancel()
        chipsScroll.alpha = 1f; chipsScroll.translationY = 0f; chipsScroll.visibility = View.VISIBLE
        focusable(false)
        if (ttsOn()) say(t("Сообщение: ", "Message: ", "Xabar: ") + body, uiLang()) { h.postDelayed(closeTask, 6000) }
        else h.postDelayed(closeTask, 9000)
    }

    private fun replyToNotif(item: BuddyNotifications.Item) {
        if (!micOk()) { showError("нет микрофона"); return }
        h.removeCallbacks(closeTask)
        replying = true
        chipsRow.removeAllViews(); hide(chipsScroll)
        status.text = sListen(); setMode("listen"); setWave(WaveView.LISTEN)
        hide(input); hide(answer)
        voice.listen(langTag(), { p -> if (replying) { setAnswer(p, false); show(answer) } }, { text, err ->
            if (replying) {
                replying = false
                when {
                    text != null -> {
                        val ok = try { BuddyNotifications.reply(this, item.key, text) } catch (_: Throwable) { false }
                        val msg = if (ok) t("Ответ отправлен", "Reply sent", "Javob yuborildi")
                        else t("Не получилось ответить", "Couldn't send the reply", "Javob yuborib bo'lmadi")
                        status.text = if (ok) t("Готово", "Done", "Tayyor") else t("Ошибка", "Error", "Xato")
                        setWave(WaveView.NONE); setMode(if (ok) "happy" else "sad")
                        setAnswer(msg, false); show(answer)
                        if (ttsOn()) say(msg, uiLang()) { h.postDelayed(closeTask, 1500) } else h.postDelayed(closeTask, 3000)
                    }
                    err != null -> showError(err)
                    else -> {
                        status.text = t("Не расслышал", "Didn't catch that", "Eshitmadim"); setWave(WaveView.NONE); setMode(baseMood())
                        h.postDelayed(closeTask, 2000)
                    }
                }
            }
        })
    }

    // ---- quiet mode + fullscreen auto-hide ----------------------------------------------------------------------
    private fun mustShow() = wakeShow || calling || waiting()

    /** Shows or hides the whole overlay; hidden means invisible, alpha 0 and click-through. Mic / wake-word keep running. */
    private fun applyHidden() {
        if (!::win.isInitialized || !::lp.isInitialized) return
        val quiet = prefs.getBoolean("quiet", false)
        val vis = mustShow() || (!quiet && (!autoHidden || expanded || prog > 0.02f))
        if (vis == winShown) return
        winShown = vis
        win.alpha = if (vis) 1f else 0f
        win.visibility = if (vis) View.VISIBLE else View.INVISIBLE
        buddy.hidden = !vis   // no frames at all while the mascot is invisible
        val nt = WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        lp.flags = if (vis) lp.flags and nt.inv() else lp.flags or nt
        try { wm.updateViewLayout(win, lp) } catch (_: Exception) {}
    }

    /**
     * Fullscreen detection is best effort. An overlay window should receive the system-bar visibility of the window below it
     * (WindowInsets.isVisible(statusBars) on API 30+, SYSTEM_UI_FLAG_FULLSCREEN before that), but some OEMs report a constant
     * value to overlays. If so, auto-hide never fires and manual quiet mode (pref `quiet`, notification button) is the reliable path.
     */
    private fun quietInit() {
        win.setOnApplyWindowInsetsListener { v, ins ->
            val visible = if (Build.VERSION.SDK_INT >= 30) ins.isVisible(WindowInsets.Type.statusBars()) else !fsStatusHidden
            if (Build.VERSION.SDK_INT >= 30) fsStatusHidden = !visible
            updateAutoHide()
            v.onApplyWindowInsets(ins)
        }
        if (Build.VERSION.SDK_INT < 30) {
            @Suppress("DEPRECATION")
            win.setOnSystemUiVisibilityChangeListener { vis ->
                fsStatusHidden = vis and View.SYSTEM_UI_FLAG_FULLSCREEN != 0
                updateAutoHide()
            }
        }
    }

    private fun updateAutoHide() {
        val land = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        val want = prefs.getBoolean("autohide", true) && screenOn && fsStatusHidden && land
        if (want != autoHidden) { autoHidden = want; applyHidden() }
    }

    private fun landscape() = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    /**
     * Auto-hide only ever applies in landscape, so the once-a-second insets poll (each one a layout pass of the overlay)
     * only runs there; rotating restarts it (onConfigurationChanged).
     */
    private val fsPoll = object : Runnable {
        override fun run() {
            if (!running || !screenOn || !prefs.getBoolean("autohide", true) || !landscape()) return
            try { if (win.isAttachedToWindow) win.requestApplyInsets() } catch (_: Exception) {}
            updateAutoHide()
            h.postDelayed(this, 1000)
        }
    }

    private fun fsPollRestart() {
        h.removeCallbacks(fsPoll)
        if (screenOn && prefs.getBoolean("autohide", true)) h.post(fsPoll)
        else { autoHidden = false; fsStatusHidden = false; applyHidden() }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        if (!running || !::win.isInitialized || !::lp.isInitialized) return
        u = resources.displayMetrics.density
        // the screen size changed (rotation / fold / display size): recompute the width-dependent metrics, then re-clamp
        answer.maxWidth = islandW() - dp(28)
        applyProgress(prog)
        updateAutoHide()
        if (screenOn && landscape() && prefs.getBoolean("autohide", true)) { h.removeCallbacks(fsPoll); h.post(fsPoll) }
        val reclamp = Runnable {
            if (!running) return@Runnable
            clamp()
            if (!expanded && prog <= 0.02f) statusDock?.let { dockTo(it) }
        }
        win.post(reclamp)
        h.postDelayed(reclamp, 350)   // the window may only report its new size after the next layout pass
    }

    // phone tilt drives the mascot's 2.5D parallax; only while the screen is on, and it can be switched off with pref `tilt`
    private var tiltOn = false
    private val tiltListener = object : SensorEventListener {
        override fun onSensorChanged(e: SensorEvent) {
            val x = (-e.values[0] / 5f).coerceIn(-1f, 1f)
            val y = (e.values[1] / 5f - 0.7f).coerceIn(-1f, 1f) // a phone is usually held tilted back, so 0 is "natural"
            buddy.setTilt(x, y)
        }
        override fun onAccuracyChanged(s: Sensor?, a: Int) {}
    }

    private fun tiltStart() {
        if (tiltOn || !prefs.getBoolean("tilt", true)) return
        try {
            val sm = getSystemService(SENSOR_SERVICE) as SensorManager
            val s = sm.getDefaultSensor(Sensor.TYPE_GRAVITY) ?: sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) ?: return
            tiltOn = sm.registerListener(tiltListener, s, SensorManager.SENSOR_DELAY_UI)
        } catch (_: Exception) { }
    }

    private fun tiltStop() {
        if (!tiltOn) return
        tiltOn = false
        try { (getSystemService(SENSOR_SERVICE) as SensorManager).unregisterListener(tiltListener) } catch (_: Exception) { }
        buddy.setTilt(0f, 0f)
    }

    override fun onDestroy() {
        tiltStop()
        running = false
        BuddyAccess.asker = null
        ClockActions.report = null
        hotStop()
        try { unregisterReceiver(screenRx) } catch (_: Exception) {}
        holdAnim?.cancel(); holdAnim = null
        prefs.unregisterOnSharedPreferenceChangeListener(prefListener)
        agentLink?.stop(); agentLink = null
        h.removeCallbacksAndMessages(null)
        try { win.setOnApplyWindowInsetsListener(null) } catch (_: Exception) {}
        islandAnim?.let { it.removeAllListeners(); it.cancel() }; islandAnim = null
        snapAnim?.cancel(); snapAnim = null
        dockAnim?.let { it.removeAllListeners(); it.cancel() }; dockAnim = null
        typeAnim?.let { it.removeAllListeners(); it.cancel() }; typeAnim = null
        Actions.onAsyncError = null
        Actions.onChoice = null
        Actions.onPending = null
        pendingRun = null
        BuddyNotifications.onNew = null
        try { DeviceEvents.stop(this) } catch (_: Exception) {}
        releaseCallFocus()
        flushMemPrefs()
        prefs.edit().putBoolean("was_running", false).apply()
        clearSticker()
        wave.mode = WaveView.NONE
        root.animate().cancel(); buddy.animate().cancel()
        try { callWatcher.stop() } catch (_: Exception) {}
        voice.shutdown()
        try { wm.removeView(win) } catch (_: Exception) {}
        super.onDestroy()
    }
}
