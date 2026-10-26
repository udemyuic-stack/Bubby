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

/** Package-name fragments of apps whose screen is never read out (banks, wallets, password managers, crypto). */
private val PRIVATE_PKG_HINTS = listOf(
    "bank", "wallet", "paypal", "payme", "uz.click", "uzum", "pay", "authenticator", "1password", "bitwarden",
    "keepass", "lastpass", "dashlane", "passwords", "crypto", "binance", "metamask", "trust"
)

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

        fun global(action: Int): Boolean = try { inst?.performGlobalAction(action) ?: false } catch (_: Exception) { false }
        fun swipe(dir: String): Boolean = try { inst?.doSwipe(dir) ?: false } catch (_: Exception) { false }
        fun clickText(t: String): Boolean = try { inst?.doClickText(t) ?: false } catch (_: Exception) { false }
        /** Visible text of the app on screen (null when the accessibility service is off). */
        fun screenText(): String? = try { inst?.doScreenText() } catch (_: Exception) { null }
        fun typeText(t: String, submit: Boolean = false): Boolean = try { inst?.doTypeSubmit(t, submit) ?: false } catch (_: Exception) { false }
        /** Exact-label click restricted to the given app packages; false when nothing matched. */
        fun clickExact(labels: List<String>, pkgs: List<String>): Boolean = try { inst?.doClickExact(labels, pkgs) ?: false } catch (_: Exception) { false }
        fun activePackage(): String? = try { inst?.doActivePackage() } catch (_: Exception) { null }
        fun tapNode(index: Int): Boolean = try { inst?.doTapNode(index) ?: false } catch (_: Exception) { false }
        fun pressEnter(): Boolean = try { inst?.doPressEnter() ?: false } catch (_: Exception) { false }
    }
}
