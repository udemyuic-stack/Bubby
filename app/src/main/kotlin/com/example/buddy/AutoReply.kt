package com.example.buddy

import android.app.Notification
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.widget.Toast

/** Answers chat messages for the user ("I'm driving") through the notification's own reply button. Opt-in and time-limited. */
object AutoReply {
    private val replied = HashMap<String, Long>()   // chat -> time of our last automatic reply

    private fun prefs(ctx: Context) = ctx.applicationContext.getSharedPreferences("buddy", Context.MODE_PRIVATE)

    fun defaultText(ctx: Context, mode: String): String =
        if (mode == "driving") Tr.t(ctx, "Я за рулём, отвечу позже.", "I'm driving right now, I'll reply later.", "Hozir rulda man, keyinroq javob beraman.")
        else Tr.t(ctx, "Сейчас не могу ответить, напишу позже.", "I can't reply right now, I'll get back to you later.", "Hozir javob bera olmayman, keyinroq yozaman.")

    /** Switches the automatic reply on for [hours] (1..12) or off. Returns a message when it cannot be done, null on success. */
    fun set(ctx: Context, on: Boolean, mode: String, hours: Int, text: String): String? {
        val p = prefs(ctx)
        synchronized(replied) { replied.clear() }
        if (!on) {
            p.edit().putLong("auto_reply_until", 0L).apply()
            return null
        }
        if (!BuddyNotifications.isEnabled(ctx)) {
            BuddyNotifications.openSettings(ctx)
            return Tr.t(ctx, "Разреши Bubby доступ к уведомлениям и повтори команду", "Allow Bubby to access notifications, then repeat the command", "Bubby'ga bildirishnomalarga ruxsat bering va buyruqni takrorlang")
        }
        val m = if (mode == "driving") "driving" else "busy"
        val msg = text.trim().take(200).ifEmpty { defaultText(ctx, m) }
        p.edit().putLong("auto_reply_until", ReplyRules.until(System.currentTimeMillis(), hours)).putString("auto_reply_text", msg).apply()
        return null
    }

    /** Called for every new notification (listener thread). */
    fun onNotification(ctx: Context, item: BuddyNotifications.Item, category: String?, isGroup: Boolean) {
        val p = prefs(ctx)
        val now = System.currentTimeMillis()
        val chat = item.app + "|" + item.title
        val last = synchronized(replied) { replied[chat] }
        if (!ReplyRules.shouldReply(p.getLong("auto_reply_until", 0L), now, item.canReply,
                category == Notification.CATEGORY_MESSAGE, isGroup, Contacts.isNumber(item.title), last)) return
        val text = p.getString("auto_reply_text", "").orEmpty().ifEmpty { defaultText(ctx, "busy") }
        synchronized(replied) { replied[chat] = now }
        if (BuddyNotifications.reply(ctx, item.key, text)) {
            Handler(Looper.getMainLooper()).post {
                try {
                    Toast.makeText(ctx, Tr.t(ctx, "Автоответ: ${item.title}", "Auto-replied to ${item.title}", "Avto-javob: ${item.title}"), Toast.LENGTH_SHORT).show()
                } catch (_: Exception) { }
            }
        }
    }
}
