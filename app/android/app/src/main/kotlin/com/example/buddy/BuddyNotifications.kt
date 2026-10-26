package com.example.buddy

import android.app.Notification
import android.app.PendingIntent
import android.app.RemoteInput
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

/** Keeps the latest notifications in memory only (never persisted, never logged). */
class BuddyNotifications : NotificationListenerService() {

    class Item(
        val key: String,
        val app: String,
        val title: String,
        val text: String,
        val whenMs: Long,
        val canReply: Boolean
    )

    private class Entry(val item: Item, val action: Notification.Action?)

    companion object {
        private const val MAX = 30
        private val lock = Any()
        private val entries = ArrayList<Entry>() // newest last
        private val main = Handler(Looper.getMainLooper())

        @Volatile private var instance: BuddyNotifications? = null

        /** Called on the main thread for each new non-ongoing notification when pref read_notifs is true. */
        @JvmStatic @Volatile var onNew: ((Item) -> Unit)? = null

        @JvmStatic fun isEnabled(ctx: Context): Boolean {
            return try {
                val s = Settings.Secure.getString(ctx.contentResolver, "enabled_notification_listeners")
                s != null && s.contains(ctx.packageName)
            } catch (e: Exception) { false }
        }

        @JvmStatic fun latest(n: Int = 5): List<Item> = synchronized(lock) {
            entries.asReversed().take(n.coerceAtLeast(0)).map { it.item }
        }

        @JvmStatic fun summaryForAi(n: Int = 5): String {
            val list = latest(n)
            if (list.isEmpty()) return "no notifications"
            val sb = StringBuilder()
            for (it in list) {
                val line = "${it.app}: ${it.title} — ${it.text}".trim()
                if (sb.length + line.length + 1 > 700) {
                    if (sb.isEmpty()) sb.append(line.take(700))
                    break
                }
                if (sb.isNotEmpty()) sb.append('\n')
                sb.append(line)
            }
            return sb.toString()
        }

        @JvmStatic fun reply(ctx: Context, key: String, text: String): Boolean {
            val action = synchronized(lock) { entries.firstOrNull { it.item.key == key }?.action } ?: return false
            val ri = action.remoteInputs?.firstOrNull() ?: return false
            val pi: PendingIntent = action.actionIntent ?: return false
            return try {
                val intent = Intent()
                val results = Bundle()
                results.putCharSequence(ri.resultKey, text)
                RemoteInput.addResultsToIntent(arrayOf(ri), intent, results)
                pi.send(ctx, 0, intent)
                true
            } catch (e: Exception) { false }
        }

        @JvmStatic fun dismiss(key: String) {
            try { instance?.cancelNotification(key) } catch (e: Exception) { }
            synchronized(lock) { entries.removeAll { it.item.key == key } }
        }

        @JvmStatic fun openSettings(ctx: Context) {
            try {
                ctx.startActivity(
                    Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            } catch (e: Exception) { }
        }
    }

    override fun onListenerConnected() {
        instance = this
        try {
            val active = activeNotifications ?: return
            for (sbn in active) add(sbn, false)
        } catch (e: Exception) { }
    }

    override fun onListenerDisconnected() {
        if (instance === this) instance = null
        synchronized(lock) { entries.clear() }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        if (sbn == null) return
        add(sbn, true)
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        if (sbn == null) return
        val k = sbn.key
        synchronized(lock) { entries.removeAll { it.item.key == k } }
    }

    override fun onDestroy() {
        if (instance === this) instance = null
        super.onDestroy()
    }

    private fun add(sbn: StatusBarNotification, fresh: Boolean) {
        try {
            if (sbn.packageName == packageName) return
            val n = sbn.notification ?: return
            val f = n.flags
            if (f and Notification.FLAG_ONGOING_EVENT != 0 ||
                f and Notification.FLAG_FOREGROUND_SERVICE != 0 ||
                f and Notification.FLAG_GROUP_SUMMARY != 0) return
            val ex = n.extras ?: return
            val title = (ex.getCharSequence(Notification.EXTRA_TITLE)
                ?: ex.getCharSequence(Notification.EXTRA_TITLE_BIG))?.toString()?.trim() ?: ""
            val text = extractText(ex)
            if (title.isEmpty() && text.isEmpty()) return
            val app = try {
                packageManager.getApplicationLabel(packageManager.getApplicationInfo(sbn.packageName, 0)).toString()
            } catch (e: Exception) { sbn.packageName }
            var act: Notification.Action? = null
            n.actions?.let { arr ->
                for (a in arr) {
                    if (a.remoteInputs?.isNotEmpty() == true && a.actionIntent != null) { act = a; break }
                }
            }
            val item = Item(sbn.key, app, title, text, sbn.postTime, act != null)
            synchronized(lock) {
                entries.removeAll { it.item.key == item.key }
                entries.add(Entry(item, act))
                while (entries.size > MAX) entries.removeAt(0)
            }
            if (fresh) {
                try {
                    AutoReply.onNotification(applicationContext, item, n.category, ex.getBoolean(Notification.EXTRA_IS_GROUP_CONVERSATION, false))
                } catch (e: Exception) { }
                val read = getSharedPreferences("buddy", Context.MODE_PRIVATE).getBoolean("read_notifs", false)
                val cb = onNew
                if (read && cb != null) main.post { try { cb(item) } catch (e: Exception) { } }
            }
        } catch (e: Exception) { }
    }

    private fun extractText(ex: Bundle): String {
        val msgs = try { ex.getParcelableArray(Notification.EXTRA_MESSAGES) } catch (e: Exception) { null }
        if (msgs != null && msgs.isNotEmpty()) {
            val lines = ArrayList<String>()
            for (m in msgs) {
                if (m is Bundle) {
                    val t = m.getCharSequence("text")?.toString()?.trim()
                    if (!t.isNullOrEmpty()) lines.add(t)
                }
            }
            if (lines.isNotEmpty()) return lines.takeLast(3).joinToString(" / ")
        }
        val big = ex.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()?.trim()
        if (!big.isNullOrEmpty()) return big
        val t = ex.getCharSequence(Notification.EXTRA_TEXT)?.toString()?.trim()
        if (!t.isNullOrEmpty()) return t
        val ls = ex.getCharSequenceArray(Notification.EXTRA_TEXT_LINES)
        if (ls != null && ls.isNotEmpty()) return ls.joinToString(" / ") { it.toString() }.trim()
        return ""
    }
}
