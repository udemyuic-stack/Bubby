package com.example.buddy

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.graphics.Rect
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

// PRIVATE_PKG_HINTS and PRIVATE_TEXT live in Safety.kt (shared with the agent loop and the JVM tests).

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
    private fun consentOk(): Boolean {
        val p = getSharedPreferences("buddy", MODE_PRIVATE)
        if (!p.getBoolean("screen_ask", true)) return true
        if (runGrant) return true
        if (runDenied) return false
        val ok = try { asker?.invoke() ?: false } catch (_: Exception) { false }
        if (ok) runGrant = true else runDenied = true
        return ok
    }

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

    // numbered nodes of the last screenText(), used by tap_node
    private val numbered = ArrayList<AccessibilityNodeInfo>()

    private fun doScreenText(): String? {
        val root = rootInActiveWindow ?: return null
        synchronized(numbered) { numbered.clear() }
        if (!getSharedPreferences("buddy", MODE_PRIVATE).getBoolean("screen_agent", true)) return "(screen reading is disabled by the user)"
        if (isOurs(root)) return "(no readable text)"
        val pkgName = root.packageName?.toString()?.lowercase().orEmpty()
        if (PRIVATE_PKG_HINTS.any { pkgName.contains(it) }) return "(screen hidden for privacy)"
        if (!consentOk()) return "(the user did not allow reading the screen for this task; say so and ask them to allow it or to do this step themselves)"
        val out = StringBuilder()
        val list = ArrayList<AccessibilityNodeInfo>()
        fun walk(n: AccessibilityNodeInfo?, depth: Int, tappableAbove: Boolean) {
            if (n == null || depth > 25 || list.size >= 60 || out.length > 2500) return
            if (isOurs(n)) return
            if (n.isVisibleToUser) {
                var t = (n.text ?: n.contentDescription)?.toString()?.replace('\n', ' ')?.trim().orEmpty()
                if (t.isEmpty() && n.isEditable) t = n.hintText?.toString()?.trim().orEmpty()
                if (t.isNotEmpty()) {
                    val line = StringBuilder()
                    line.append('[').append(list.size + 1).append("] ").append(t.take(80))
                    if (n.isEditable) line.append(" [input]")
                    else if (n.isClickable || n.isCheckable || tappableAbove) line.append(" [tap]")
                    line.append('\n')
                    if (out.length + line.length <= 2600) {
                        out.append(line)
                        list.add(n)
                    }
                }
            }
            val up = tappableAbove || n.isClickable
            for (i in 0 until n.childCount) walk(n.getChild(i), depth + 1, up)
        }
        walk(root, 0, false)
        // card or payment details on screen (even in a browser): nothing leaves the phone, nothing can be tapped by number
        if (PRIVATE_TEXT.containsMatchIn(out)) return "(screen hidden for privacy)"
        synchronized(numbered) { numbered.addAll(list) }
        return out.toString().ifBlank { "(no readable text)" }
    }

    private fun doTapNode(index: Int): Boolean {
        val n = synchronized(numbered) { numbered.getOrNull(index - 1) } ?: return false
        return clickNode(n)
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
