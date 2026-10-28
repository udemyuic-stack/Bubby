package com.example.buddy

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.ClipData
import android.content.ClipboardManager
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Path
import android.graphics.Rect
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

// PRIVATE_PKG_HINTS and PRIVATE_TEXT live in Safety.kt (shared with the agent loop and the JVM tests).

/** A downscaled screenshot as base64 JPEG ([w]x[h]) of a [screenW]x[screenH] screen, or [err]; [black] = protected window. */
class Shot(val b64: String?, val w: Int, val h: Int, val screenW: Int, val screenH: Int, val err: String?, val black: Boolean = false) {
    companion object { fun error(m: String) = Shot(null, 0, 0, 0, 0, m) }
}

/** Words in the user's own request that already say "look at / use my screen": no question is asked then. */
private val SCREEN_WORDS = Regex("(?iu)экран|screen|ekran|на странице|on the page|sahifa|прочитай|read (this|it|the)|o'qib ber|нажми|tap |press |bosing|bos |scroll|листай|прокрути|type |напечатай|yoz ")

class BuddyAccess : AccessibilityService() {
    override fun onServiceConnected() { inst = this }
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        val t = event.eventType
        if (t != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED && t != AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) return
        if (event.packageName?.toString() == packageName) return   // our own overlay animating is not app activity
        lastEventMs = SystemClock.uptimeMillis()
        if (t == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            val c = event.className?.toString().orEmpty()
            // activities and dialogs, not toasts or keyboard windows
            if (c.isNotEmpty() && !c.startsWith("android.widget.") && !c.startsWith("android.inputmethod")) { lastWindowClass = c; lastWindowPkg = event.packageName?.toString() }
        }
    }
    override fun onInterrupt() {}
    override fun onUnbind(intent: android.content.Intent?): Boolean { inst = null; return super.onUnbind(intent) }
    override fun onDestroy() { if (inst === this) inst = null; super.onDestroy() }

    private fun gesture(path: Path, ms: Long): Boolean {
        val g = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, ms)).build()
        return dispatchGesture(g, null, null)
    }

    private fun doSwipe(dir: String): Boolean {
        val m = resources.displayMetrics
        val w = m.widthPixels.toFloat(); val h = m.heightPixels.toFloat()
        val cx = w / 2; val cy = h / 2
        val p = Path()
        // "down" scrolls the content down, so the finger moves up
        when (dir) {
            "down" -> { p.moveTo(cx, h * 0.7f); p.lineTo(cx, h * 0.3f) }
            "up" -> { p.moveTo(cx, h * 0.3f); p.lineTo(cx, h * 0.7f) }
            "right" -> { p.moveTo(w * 0.8f, cy); p.lineTo(w * 0.2f, cy) }
            "left" -> { p.moveTo(w * 0.2f, cy); p.lineTo(w * 0.8f, cy) }
            else -> return false
        }
        return gesture(p, 300)
    }

    private fun tapAt(x: Float, y: Float): Boolean {
        val p = Path(); p.moveTo(x, y); p.lineTo(x, y + 1)
        return gesture(p, 50)
    }

    /** Reading the screen needs the user's yes for each task (unless they turned the question off or already asked for it). */
    private fun consentOk(): Boolean = consent(this)

    /** True when the foreground app looks like a bank / payment / password app: no taps or typing there either. */
    private fun privateForeground(): Boolean {
        val pkg = rootInActiveWindow?.packageName?.toString()?.lowercase().orEmpty()
        return pkg.isNotEmpty() && PRIVATE_PKG_HINTS.any { pkg.contains(it) }
    }

    private fun allNodes(root: AccessibilityNodeInfo?, out: MutableList<AccessibilityNodeInfo>, depth: Int = 0) {
        if (root == null || depth > 30 || out.size > 600) return
        out.add(root)
        for (i in 0 until root.childCount) allNodes(root.getChild(i), out, depth + 1)
    }

    private fun checkables(n: AccessibilityNodeInfo): List<AccessibilityNodeInfo> {
        val all = ArrayList<AccessibilityNodeInfo>(); allNodes(n, all)
        return all.filter { it.isCheckable && it.isVisibleToUser }
    }

    /**
     * Turns off an alarm in the clock app that is on screen (its alarm list). Matches by time (24 h or 12 h with AM/PM) or,
     * with no time, takes the first alarm that is on. Returns 1 = switched off and confirmed, 0 = no such alarm that is on,
     * -1 = the screen could not be read. Reads only the clock app's own screen and nothing is sent anywhere.
     */
    private fun doDisableAlarm(hour: Int, minute: Int): Int {
        val root = rootInActiveWindow ?: return -1
        if (isOurs(root)) return -1
        val pkg = root.packageName?.toString()?.lowercase().orEmpty()
        if (!pkg.contains("clock") && !pkg.contains("alarm") && !pkg.contains("deskclock")) return -1
        val all = ArrayList<AccessibilityNodeInfo>(); allNodes(root, all)
        val h12 = if (hour % 12 == 0) 12 else hour % 12
        val re = Regex("^\\s*0?(\\d{1,2}):(\\d{2})\\s*([AaPp]\\.?[Mm]\\.?)?\\s*$")
        var target: AccessibilityNodeInfo? = null
        if (hour < 0) {
            target = all.firstOrNull { it.isCheckable && it.isChecked && it.isVisibleToUser }
        } else for (n in all) {
            val t = n.text?.toString() ?: continue
            val m = re.find(t) ?: continue
            val hh = m.groupValues[1].toInt(); val mm = m.groupValues[2].toInt()
            val ap = m.groupValues[3].lowercase().firstOrNull()
            if (mm != minute) continue
            val ok = when {
                ap != null -> hh == h12 && ((ap == 'a') == (hour < 12))
                hh == hour -> true
                hh == h12 -> {
                    // a 12 h clock may show AM/PM as its own text next to the time: look in the same row
                    var up: AccessibilityNodeInfo? = n; var amPm: Boolean? = null
                    repeat(3) { up = up?.parent; val rowTexts = ArrayList<AccessibilityNodeInfo>(); allNodes(up, rowTexts)
                        rowTexts.mapNotNull { it.text?.toString()?.trim()?.lowercase() }.firstOrNull { it == "am" || it == "pm" || it == "a.m." || it == "p.m." }?.let { amPm = it.startsWith("a") } }
                    amPm == null || amPm == (hour < 12)
                }
                else -> false
            }
            if (!ok) continue
            // the switch of this alarm: the nearest ancestor row that holds one
            var row: AccessibilityNodeInfo? = n
            var sw: AccessibilityNodeInfo? = null
            repeat(7) { if (sw == null) { row = row?.parent; val c = row?.let { checkables(it) }.orEmpty(); if (c.size == 1) sw = c[0] } }
            if (sw != null && sw!!.isChecked) { target = sw; break }
        }
        val sw = target ?: return 0
        sw.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        // the screen redraws: read it again and make sure that alarm is really off now
        SystemClock.sleep(600)
        val again = rootInActiveWindow ?: return 1
        val now = ArrayList<AccessibilityNodeInfo>(); allNodes(again, now)
        val stillOn = if (hour < 0) false else now.any { n ->
            val t = n.text?.toString() ?: return@any false
            val m = re.find(t) ?: return@any false
            m.groupValues[2].toInt() == minute && (m.groupValues[1].toInt() == hour || m.groupValues[1].toInt() == h12) &&
                run { var row: AccessibilityNodeInfo? = n; var c: List<AccessibilityNodeInfo> = emptyList()
                    repeat(7) { if (c.size != 1) { row = row?.parent; c = row?.let { checkables(it) }.orEmpty() } }
                    c.size == 1 && c[0].isChecked }
        }
        return if (stillOn) 0 else 1
    }

    private fun isOurs(n: AccessibilityNodeInfo?): Boolean = n?.packageName?.toString() == packageName

    private fun label(n: AccessibilityNodeInfo): String =
        (n.text ?: n.contentDescription)?.toString()?.trim().orEmpty()

    /** True if the node or one of its near ancestors can be clicked. */
    private fun clickableUp(n: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        var cur: AccessibilityNodeInfo? = n
        var depth = 0
        while (cur != null && depth < 8) {
            if (cur.isClickable && cur.isEnabled) return cur
            cur = cur.parent
            depth++
        }
        return null
    }

    private fun tapBounds(n: AccessibilityNodeInfo): Boolean {
        val r = Rect()
        n.getBoundsInScreen(r)
        if (r.isEmpty) return false
        return tapAt(r.exactCenterX(), r.exactCenterY())
    }

    private fun clickNode(n: AccessibilityNodeInfo): Boolean {
        try { n.refresh() } catch (_: Exception) { }
        val c = clickableUp(n)
        if (c != null && c.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return true
        return tapBounds(n)
    }

    private fun doClickText(t: String): Boolean {
        val root = rootInActiveWindow ?: return false
        if (isOurs(root)) return false
        val found = root.findAccessibilityNodeInfosByText(t)
        if (found.isNullOrEmpty()) return false
        val want = t.trim()
        val cands = found.filter { !isOurs(it) && it.isVisibleToUser }
        if (cands.isEmpty()) return false
        // exact matches first, then clickable ones first
        val sorted = cands.sortedWith(compareByDescending<AccessibilityNodeInfo> { label(it).equals(want, ignoreCase = true) }
            .thenByDescending { clickableUp(it) != null })
        for (n in sorted) {
            val c = clickableUp(n)
            if (c != null && c.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return true
        }
        return tapBounds(sorted[0])
    }

    /**
     * Clicks a visible, enabled node whose text/description EQUALS one of [labels] (case-insensitive), only while
     * the active window belongs to one of [pkgs]. Never falls back to a partial match.
     */
    private fun doClickExact(labels: List<String>, pkgs: List<String>): Boolean {
        val root = rootInActiveWindow ?: return false
        val rp = root.packageName?.toString() ?: return false
        if (isOurs(root) || rp !in pkgs) return false
        for (l in labels) {
            val found = root.findAccessibilityNodeInfosByText(l)
            if (found.isNullOrEmpty()) continue
            for (n in found) {
                if (isOurs(n) || !n.isVisibleToUser) continue
                val nl = label(n)
                val dl = n.contentDescription?.toString()?.trim().orEmpty()
                if (!nl.equals(l, ignoreCase = true) && !dl.equals(l, ignoreCase = true)) continue
                val c = clickableUp(n) ?: continue
                if (c.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return true
            }
        }
        return false
    }

    private fun doActivePackage(): String? = rootInActiveWindow?.packageName?.toString()

    // nodes numbered by the last description, used by tap_node and the other actions that take an element number
    private val numbered = ArrayList<AccessibilityNodeInfo>()
    @Volatile private var lastSig: Int? = null

    private fun realSize(): Pair<Int, Int> {
        val m = android.util.DisplayMetrics()
        @Suppress("DEPRECATION")
        (getSystemService(WINDOW_SERVICE) as android.view.WindowManager).defaultDisplay.getRealMetrics(m)
        return m.widthPixels to m.heightPixels
    }

    private fun hiddenDesc(t: String) = ScreenDesc(t, 0, emptyList(), emptyList(), null, hidden = true)

    /** Builds the rich numbered description of the app on screen (consent, privacy and the screen_agent setting first). */
    private fun doDescribe(): ScreenDesc? {
        val root = rootInActiveWindow ?: return null
        synchronized(numbered) { numbered.clear() }
        if (!getSharedPreferences("buddy", MODE_PRIVATE).getBoolean("screen_agent", true)) return hiddenDesc("(screen reading is disabled by the user)")
        if (isOurs(root)) return hiddenDesc("(no readable text: Bubby's own window is in front)")
        val pkg = root.packageName?.toString().orEmpty()
        if (Safety.isPrivatePkg(pkg)) return hiddenDesc("(screen hidden for privacy: banking, payment or password app)")
        if (!consentOk()) return hiddenDesc("(the user did not allow reading the screen for this task; say so and ask them to allow it or to do this step themselves)")
        val nodes = ArrayList<ScreenNode>()
        val refs = ArrayList<AccessibilityNodeInfo>()
        val r = Rect()
        fun walk(n: AccessibilityNodeInfo?, parent: Int, depth: Int) {
            if (n == null || depth > 30 || nodes.size >= 600 || isOurs(n)) return
            n.getBoundsInScreen(r)
            val me = nodes.size
            nodes.add(ScreenNode(parent,
                text = n.text?.toString().orEmpty(), desc = n.contentDescription?.toString().orEmpty(),
                hint = (if (Build.VERSION.SDK_INT >= 26) n.hintText?.toString() else null).orEmpty(),
                viewId = n.viewIdResourceName.orEmpty(), cls = n.className?.toString().orEmpty(),
                clickable = n.isClickable, longClickable = n.isLongClickable, scrollable = n.isScrollable,
                checkable = n.isCheckable, checked = n.isChecked, focused = n.isFocused, editable = n.isEditable,
                enabled = n.isEnabled, password = n.isPassword, selected = n.isSelected, visible = n.isVisibleToUser,
                l = r.left, t = r.top, r = r.right, b = r.bottom))
            refs.add(n)
            for (i in 0 until n.childCount) walk(n.getChild(i), me, depth + 1)
        }
        walk(root, -1, 0)
        val (w, h) = realSize()
        val act = if (lastWindowPkg == pkg) lastWindowClass else null
        val d = ScreenDescriber.describe(nodes, pkg, act, w, h, lastSig)
        if (!d.hidden) synchronized(numbered) { for (i in d.numbered) numbered.add(refs[i]) }
        lastSig = d.sig
        return d
    }

    private fun doScreenText(): String? = doDescribe()?.text

    private fun numberedNode(index: Int): AccessibilityNodeInfo? {
        val n = synchronized(numbered) { numbered.getOrNull(index - 1) } ?: return null
        // the screen changed since it was described: the element is gone
        return if (try { n.refresh() } catch (_: Exception) { false }) n else null
    }

    private fun doTapNode(index: Int): Boolean {
        val n = numberedNode(index) ?: return false
        return clickNode(n)
    }

    private fun childText(n: AccessibilityNodeInfo, depth: Int = 0): String {
        if (depth > 5) return ""
        for (i in 0 until n.childCount) {
            val c = n.getChild(i) ?: continue
            val t = label(c)
            if (t.isNotEmpty()) return t
            val d = childText(c, depth + 1)
            if (d.isNotEmpty()) return d
        }
        return ""
    }

    private fun targetOf(n: AccessibilityNodeInfo): Target {
        val l = label(n).ifEmpty { childText(n) }.ifEmpty { if (Build.VERSION.SDK_INT >= 26) n.hintText?.toString().orEmpty() else "" }
        return Target(l.replace('\n', ' ').take(80), n.packageName?.toString().orEmpty(), n.isPassword, n.isEditable)
    }

    private fun doNodeTarget(index: Int): Target? = numberedNode(index)?.let { targetOf(it) }

    private fun doNodeCenter(index: Int): Pair<Int, Int>? {
        val n = numberedNode(index) ?: return null
        val r = Rect(); n.getBoundsInScreen(r)
        return if (r.isEmpty) null else r.centerX() to r.centerY()
    }

    /** The deepest visible element under a point (what a tap there would hit). */
    private fun doTargetAt(x: Int, y: Int): Target? {
        val root = rootInActiveWindow ?: return null
        var best: AccessibilityNodeInfo? = null
        val r = Rect()
        fun walk(n: AccessibilityNodeInfo?, depth: Int) {
            if (n == null || depth > 30 || isOurs(n)) return
            n.getBoundsInScreen(r)
            if (!n.isVisibleToUser || !r.contains(x, y)) return
            if (n.isClickable || n.isCheckable || n.isEditable || label(n).isNotEmpty() || best == null) best = n
            for (i in 0 until n.childCount) walk(n.getChild(i), depth + 1)
        }
        walk(root, 0)
        val b = best ?: return Target("", root.packageName?.toString().orEmpty())
        // a tap goes to the clickable ancestor: judge by the whole row's text
        val c = clickableUp(b) ?: b
        val t = targetOf(c)
        return if (t.label.isEmpty()) targetOf(b) else t
    }

    private fun focusedEditable(): AccessibilityNodeInfo? {
        val root = rootInActiveWindow ?: return null
        val focused = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
        return (if (focused != null && focused.isEditable) focused else null) ?: findEditable(root)
    }

    private fun doFocusedTarget(): Target? = focusedEditable()?.let { targetOf(it) } ?: rootInActiveWindow?.let { Target("", it.packageName?.toString().orEmpty()) }

    /** Dispatches strokes one after another and waits for each to finish. Worker thread only. */
    private fun blockingStroke(strokes: List<Pair<Path, Long>>): Boolean {
        for ((p, ms) in strokes) if (!dispatchWait(GestureDescription.Builder().addStroke(GestureDescription.StrokeDescription(p, 0, ms)).build(), ms)) return false
        return true
    }

    private fun dispatchWait(g: GestureDescription, ms: Long): Boolean {
        val latch = java.util.concurrent.CountDownLatch(1)
        var ok = false
        val sent = dispatchGesture(g, object : GestureResultCallback() {
            override fun onCompleted(d: GestureDescription?) { ok = true; latch.countDown() }
            override fun onCancelled(d: GestureDescription?) { latch.countDown() }
        }, null)
        if (!sent) return false
        latch.await(ms + 2500, java.util.concurrent.TimeUnit.MILLISECONDS)
        return ok
    }

    private fun doDrag(x1: Int, y1: Int, x2: Int, y2: Int, ms: Long): Boolean {
        val hold = Path().apply { moveTo(x1.toFloat(), y1.toFloat()); lineTo(x1 + 1f, y1.toFloat()) }
        val s1 = GestureDescription.StrokeDescription(hold, 0, 650, true)
        if (!dispatchWait(GestureDescription.Builder().addStroke(s1).build(), 650)) return false
        val move = Path().apply { moveTo(x1 + 1f, y1.toFloat()); lineTo(x2.toFloat(), y2.toFloat()) }
        return dispatchWait(GestureDescription.Builder().addStroke(s1.continueStroke(move, 0, ms, false)).build(), ms)
    }

    private fun doLongPressNode(index: Int): Boolean {
        val n = numberedNode(index) ?: return false
        var cur: AccessibilityNodeInfo? = n
        var depth = 0
        while (cur != null && depth < 8) {
            if (cur.isLongClickable && cur.performAction(AccessibilityNodeInfo.ACTION_LONG_CLICK)) return true
            cur = cur.parent; depth++
        }
        val r = Rect(); n.getBoundsInScreen(r)
        if (r.isEmpty) return false
        return blockingStroke(listOf(Path().apply { moveTo(r.exactCenterX(), r.exactCenterY()); lineTo(r.exactCenterX(), r.exactCenterY() + 1) } to 900L))
    }

    private fun doSelectAll(): Boolean {
        val n = focusedEditable() ?: return false
        val len = n.text?.length ?: 0
        val b = Bundle()
        b.putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_START_INT, 0)
        b.putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_END_INT, len)
        return n.performAction(AccessibilityNodeInfo.ACTION_SET_SELECTION, b)
    }

    private fun doCopy(index: Int): Boolean {
        if (index > 0) {
            val n = numberedNode(index) ?: return false
            val t = label(n).ifEmpty { childText(n) }
            if (t.isEmpty() || Safety.hasPrivateText(t)) return false
            (getSystemService(CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("Bubby", t))
            return true
        }
        val root = rootInActiveWindow ?: return false
        val n = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT) ?: return false
        return n.performAction(AccessibilityNodeInfo.ACTION_COPY)
    }

    private fun doPaste(): Boolean {
        val n = focusedEditable() ?: return false
        if (!n.isFocused) n.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
        return n.performAction(AccessibilityNodeInfo.ACTION_PASTE)
    }

    private fun textVisible(t: String): Boolean {
        val root = rootInActiveWindow ?: return false
        return root.findAccessibilityNodeInfosByText(t)?.any { !isOurs(it) && it.isVisibleToUser } == true
    }

    private fun doScrollToText(t: String, dir: String, max: Int): Int {
        for (k in 0..max) {
            if (textVisible(t)) return 1
            if (k == max) break
            if (!doSwipe(if (dir == "up") "up" else "down")) return -1
            SystemClock.sleep(750)
        }
        return 0
    }

    /** All text on screen (for the payment-details check before a screenshot). */
    private fun anyPrivateText(): Boolean {
        val root = rootInActiveWindow ?: return false
        val sb = StringBuilder()
        fun walk(n: AccessibilityNodeInfo?, depth: Int) {
            if (n == null || depth > 30 || sb.length > 20000 || isOurs(n)) return
            if (n.isVisibleToUser) { n.text?.let { sb.append(it).append('\n') }; n.contentDescription?.let { sb.append(it).append('\n') } }
            for (i in 0 until n.childCount) walk(n.getChild(i), depth + 1)
        }
        walk(root, 0)
        return Safety.hasPrivateText(sb)
    }

    private fun doScreenshot(longSide: Int): Shot {
        if (Build.VERSION.SDK_INT < 30) return Shot.error("screenshots need Android 11 or newer; use the screen text")
        if (!getSharedPreferences("buddy", MODE_PRIVATE).getBoolean("screen_agent", true)) return Shot.error("screen reading is disabled by the user")
        if (privateForeground()) return Shot.error("the app on screen is a banking, payment or password app: Bubby never takes screenshots there")
        if (anyPrivateText()) return Shot.error("the screen shows card or payment details: no screenshot")
        if (!consentOk()) return Shot.error("the user did not allow looking at the screen for this task")
        val latch = java.util.concurrent.CountDownLatch(1)
        var buf: android.hardware.HardwareBuffer? = null
        var space: android.graphics.ColorSpace? = null
        var code = 0
        takeScreenshot(android.view.Display.DEFAULT_DISPLAY, mainExecutor, object : TakeScreenshotCallback {
            override fun onSuccess(s: ScreenshotResult) { buf = s.hardwareBuffer; space = s.colorSpace; latch.countDown() }
            override fun onFailure(errorCode: Int) { code = errorCode; latch.countDown() }
        })
        if (!latch.await(4, java.util.concurrent.TimeUnit.SECONDS)) return Shot.error("the screenshot timed out")
        val hb = buf ?: return Shot.error(if (code == ERROR_TAKE_SCREENSHOT_INTERVAL_TIME_SHORT) "screenshots too fast, wait a second" else "the system refused the screenshot ($code)")
        try {
            val hw = Bitmap.wrapHardwareBuffer(hb, space) ?: return Shot.error("could not read the screenshot")
            val soft = hw.copy(Bitmap.Config.ARGB_8888, false)
            hw.recycle()
            val sw = soft.width; val sh = soft.height
            val k = minOf(1f, longSide.toFloat() / maxOf(sw, sh))
            val small = if (k < 1f) Bitmap.createScaledBitmap(soft, maxOf(1, (sw * k).toInt()), maxOf(1, (sh * k).toInt()), true) else soft
            if (small !== soft) soft.recycle()
            // a protected window (FLAG_SECURE) comes back black
            var bright = 0
            for (gy in 1..12) for (gx in 1..12) {
                val c = small.getPixel(small.width * gx / 13, small.height * gy / 13)
                if (Color.red(c) + Color.green(c) + Color.blue(c) > 40) bright++
            }
            val out = java.io.ByteArrayOutputStream()
            small.compress(Bitmap.CompressFormat.JPEG, 70, out)
            val w = small.width; val h = small.height
            small.recycle()
            return Shot(android.util.Base64.encodeToString(out.toByteArray(), android.util.Base64.NO_WRAP), w, h, sw, sh, null, black = bright < 3)
        } finally { try { hb.close() } catch (_: Exception) { } }
    }

    private fun findEditable(n: AccessibilityNodeInfo?, depth: Int = 0): AccessibilityNodeInfo? {
        if (n == null || depth > 25) return null
        if (n.isEditable && !isOurs(n)) return n
        for (i in 0 until n.childCount) {
            val f = findEditable(n.getChild(i), depth + 1)
            if (f != null) return f
        }
        return null
    }

    private fun doType(t: String): Boolean {
        val root = rootInActiveWindow ?: return false
        val focused = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
        val node = (if (focused != null && focused.isEditable) focused else null) ?: findEditable(root) ?: return false
        if (!node.isFocused) node.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
        val cur = if (node.isShowingHintText) "" else (node.text?.toString() ?: "")
        val args = Bundle()
        args.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, cur + t)
        return node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
    }

    private fun doPressEnter(): Boolean {
        if (Build.VERSION.SDK_INT < 30) return false
        val root = rootInActiveWindow ?: return false
        val focused = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
        val node = (if (focused != null && focused.isEditable) focused else null) ?: findEditable(root) ?: return false
        return node.performAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_IME_ENTER.id)
    }

    private fun doTypeSubmit(t: String, submit: Boolean): Boolean {
        if (!doType(t)) return false
        if (submit) {
            if (!doPressEnter()) {
                // older Android or field without an IME action: press a visible "search" button instead
                for (w in listOf("Search", "Поиск", "Найти", "Qidirish", "Go")) {
                    val root = rootInActiveWindow ?: break
                    val hit = root.findAccessibilityNodeInfosByText(w)?.firstOrNull {
                        !isOurs(it) && it.isVisibleToUser && label(it).equals(w, ignoreCase = true) && clickableUp(it) != null
                    }
                    if (hit != null && clickNode(hit)) break
                }
            }
        }
        return true
    }

    companion object {
        @Volatile private var inst: BuddyAccess? = null
        /** SystemClock.uptimeMillis() of the last window state/content change in another app. */
        @Volatile var lastEventMs: Long = 0L
        val enabled: Boolean get() = inst != null
        /** Set by the service: shows "may Bubby read your screen for this task?" and waits for the answer (worker thread). */
        @Volatile var asker: (() -> Boolean)? = null
        @Volatile private var runGrant = false
        @Volatile private var runDenied = false

        /** A new request from the user: the answer given for the last task is forgotten. */
        fun newRun(userText: String) {
            runDenied = false
            runGrant = SCREEN_WORDS.containsMatchIn(userText)
        }
        /** See [doDisableAlarm]. */
        fun disableAlarm(hour: Int, minute: Int): Int = try { inst?.doDisableAlarm(hour, minute) ?: -1 } catch (_: Exception) { -1 }
        fun foregroundPackage(): String? = try { inst?.rootInActiveWindow?.packageName?.toString() } catch (_: Exception) { null }
        fun endRun() { runGrant = false; runDenied = false }

        /** Screen reading needs the user's yes for each task (unless they turned the question off or already asked for it). */
        fun consent(ctx: android.content.Context): Boolean {
            val p = ctx.getSharedPreferences("buddy", android.content.Context.MODE_PRIVATE)
            if (!p.getBoolean("screen_ask", true)) return true
            if (runGrant) return true
            if (runDenied) return false
            val ok = try { asker?.invoke() ?: false } catch (_: Exception) { false }
            if (ok) runGrant = true else runDenied = true
            return ok
        }
        @Volatile var lastWindowClass: String? = null
        @Volatile var lastWindowPkg: String? = null

        /** The rich numbered description (also used by screen_text and tap_node); null when the service is off. */
        fun describe(): ScreenDesc? = try { inst?.doDescribe() } catch (_: Exception) { null }
        fun privateNow(): Boolean = try { inst?.privateForeground() ?: false } catch (_: Exception) { false }
        fun nodeTarget(index: Int): Target? = try { inst?.doNodeTarget(index) } catch (_: Exception) { null }
        fun targetAt(x: Int, y: Int): Target? = try { inst?.doTargetAt(x, y) } catch (_: Exception) { null }
        fun focusedTarget(): Target? = try { inst?.doFocusedTarget() } catch (_: Exception) { null }
        fun screenSize(): Pair<Int, Int> = try { inst?.realSize() ?: (0 to 0) } catch (_: Exception) { 0 to 0 }
        fun nodeCenter(index: Int): Pair<Int, Int>? = try { inst?.doNodeCenter(index) } catch (_: Exception) { null }
        /** Blocking gestures (worker thread): true when the system accepted and completed them. Never in private apps. */
        fun tapXY(x: Int, y: Int): Boolean = gestureOk { it.blockingStroke(listOf(pathOf(x, y, x, y + 1) to 60L)) }
        fun longPressXY(x: Int, y: Int): Boolean = gestureOk { it.blockingStroke(listOf(pathOf(x, y, x, y + 1) to 900L)) }
        fun doubleTapXY(x: Int, y: Int): Boolean = gestureOk { it.blockingStroke(listOf(pathOf(x, y, x, y + 1) to 50L)) && run { SystemClock.sleep(90); it.blockingStroke(listOf(pathOf(x, y, x, y + 1) to 50L)) } }
        fun swipeXY(x1: Int, y1: Int, x2: Int, y2: Int, ms: Long): Boolean = gestureOk { it.blockingStroke(listOf(pathOf(x1, y1, x2, y2) to ms.coerceIn(50, 5000))) }
        fun dragXY(x1: Int, y1: Int, x2: Int, y2: Int, ms: Long): Boolean = gestureOk { it.doDrag(x1, y1, x2, y2, ms.coerceIn(300, 5000)) }
        fun longPressNode(index: Int): Boolean = gestureOk { it.doLongPressNode(index) }
        fun selectAll(): Boolean = gestureOk { it.doSelectAll() }
        fun copy(index: Int): Boolean = gestureOk { it.doCopy(index) }
        fun paste(): Boolean = gestureOk { it.doPaste() }
        /** Scrolls until a visible element shows [text]: 1 = found, 0 = not found, -1 = cannot scroll. */
        fun scrollToText(text: String, dir: String, max: Int): Int = try { inst?.let { if (it.privateForeground()) -1 else it.doScrollToText(text, dir, max) } ?: -1 } catch (_: Exception) { -1 }
        /** Screenshot (API 30+), downscaled; see [Shot]. */
        fun screenshot(longSide: Int): Shot = try { inst?.doScreenshot(longSide) ?: Shot.error("the accessibility service is off") } catch (e: Exception) { Shot.error(e.message ?: "failed") }

        private fun pathOf(x1: Int, y1: Int, x2: Int, y2: Int) = Path().apply { moveTo(x1.toFloat().coerceAtLeast(0f), y1.toFloat().coerceAtLeast(0f)); lineTo(x2.toFloat().coerceAtLeast(0f), y2.toFloat().coerceAtLeast(0f)) }
        private fun gestureOk(f: (BuddyAccess) -> Boolean): Boolean = try { inst?.let { !it.privateForeground() && f(it) } ?: false } catch (_: Exception) { false }
        /** The window manager for accessibility overlays (they are layered above the status bar), or null when the service is off. */
        fun windowManager(): android.view.WindowManager? = try { inst?.getSystemService(android.content.Context.WINDOW_SERVICE) as? android.view.WindowManager } catch (_: Exception) { null }

        fun global(action: Int): Boolean = try { inst?.performGlobalAction(action) ?: false } catch (_: Exception) { false }
        fun swipe(dir: String): Boolean = try { inst?.doSwipe(dir) ?: false } catch (_: Exception) { false }
        fun clickText(t: String): Boolean = try { inst?.let { !it.privateForeground() && it.doClickText(t) } ?: false } catch (_: Exception) { false }
        /** Visible text of the app on screen (null when the accessibility service is off). */
        fun screenText(): String? = try { inst?.doScreenText() } catch (_: Exception) { null }
        fun typeText(t: String, submit: Boolean = false): Boolean = try { inst?.let { !it.privateForeground() && it.doTypeSubmit(t, submit) } ?: false } catch (_: Exception) { false }
        /** Exact-label click restricted to the given app packages; false when nothing matched. */
        fun clickExact(labels: List<String>, pkgs: List<String>): Boolean = try { inst?.doClickExact(labels, pkgs) ?: false } catch (_: Exception) { false }
        fun activePackage(): String? = try { inst?.doActivePackage() } catch (_: Exception) { null }
        fun tapNode(index: Int): Boolean = try { inst?.let { !it.privateForeground() && it.doTapNode(index) } ?: false } catch (_: Exception) { false }
        fun pressEnter(): Boolean = try { inst?.doPressEnter() ?: false } catch (_: Exception) { false }
    }
}
