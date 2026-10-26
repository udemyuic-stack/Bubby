package com.example.buddy

import android.app.ActivityManager
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build

object Reminders {
    const val ACTION_SPEAK = "com.example.buddy.SPEAK"
    const val ACTION_FIRE = "com.example.buddy.REMINDER_FIRE"
    const val ACTION_SNOOZE = "com.example.buddy.REMINDER_SNOOZE"
    const val ACTION_DONE = "com.example.buddy.REMINDER_DONE"
    const val CHANNEL = "reminders"

    private fun pending(ctx: Context, id: String, title: String?, create: Boolean): PendingIntent? {
        val i = Intent(ctx, ReminderReceiver::class.java).setAction(ACTION_FIRE)
            .setData(android.net.Uri.parse("buddy://task/$id"))
            .putExtra("id", id)
        if (title != null) i.putExtra("title", title)
        val flags = PendingIntent.FLAG_IMMUTABLE or
            if (create) PendingIntent.FLAG_UPDATE_CURRENT else PendingIntent.FLAG_NO_CREATE
        return PendingIntent.getBroadcast(ctx, id.hashCode(), i, flags)
    }

    fun canExact(ctx: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
        val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        return am.canScheduleExactAlarms()
    }

    fun schedule(ctx: Context, t: Task) {
        cancel(ctx, t.id)
        val due = t.dueMillis ?: return
        if (!t.remind || t.done || due <= System.currentTimeMillis()) return
        val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pi = pending(ctx, t.id, t.title, true) ?: return
        try {
            if (canExact(ctx)) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, due, pi)
            else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, due, pi)
        } catch (_: SecurityException) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, due, pi)
        }
    }

    fun cancel(ctx: Context, id: String) {
        val pi = pending(ctx, id, null, false) ?: return
        (ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager).cancel(pi)
        pi.cancel()
    }

    fun rescheduleAll(ctx: Context) {
        TaskStore(ctx).all().forEach { schedule(ctx, it) }
    }

    internal fun ensureChannel(ctx: Context) {
        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (nm.getNotificationChannel(CHANNEL) != null) return
        val ch = NotificationChannel(CHANNEL, Tr.t(ctx, "Напоминания", "Reminders", "Eslatmalar"), NotificationManager.IMPORTANCE_HIGH)
        nm.createNotificationChannel(ch)
    }

    @Suppress("DEPRECATION")
    internal fun serviceRunning(ctx: Context): Boolean {
        val am = ctx.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        return am.getRunningServices(100).any { it.service.className == BuddyService::class.java.name }
    }
}

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        val id = intent.getStringExtra("id") ?: return
        if (intent.action == Reminders.ACTION_SNOOZE || intent.action == Reminders.ACTION_DONE) {
            val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.cancel(id.hashCode())
            val store = TaskStore(ctx)
            if (intent.action == Reminders.ACTION_DONE) {
                store.setDone(id, true)
                Reminders.cancel(ctx, id)
            } else {
                val t = store.all().firstOrNull { it.id == id }
                if (t != null) {
                    t.dueMillis = System.currentTimeMillis() + 10 * 60_000L
                    t.remind = true
                    t.done = false
                    store.update(t)
                    Reminders.schedule(ctx, t)
                }
            }
            return
        }
        val task = TaskStore(ctx).all().firstOrNull { it.id == id }
        if (task != null && task.done) return
        val title = task?.title ?: intent.getStringExtra("title") ?: return

        Reminders.ensureChannel(ctx)
        val open = ctx.packageManager.getLaunchIntentForPackage(ctx.packageName)
        val content = open?.let {
            PendingIntent.getActivity(ctx, 0, it, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        }
        fun act(action: String, label: String): android.app.Notification.Action {
            val i = Intent(ctx, ReminderReceiver::class.java).setAction(action)
                .setData(android.net.Uri.parse("buddy://task/$id/$action"))
                .putExtra("id", id).putExtra("title", title)
            val pi = PendingIntent.getBroadcast(
                ctx, (id + action).hashCode(), i, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            return android.app.Notification.Action.Builder(null, label, pi).build()
        }
        val n = android.app.Notification.Builder(ctx, Reminders.CHANNEL)
            .addAction(act(Reminders.ACTION_SNOOZE, Tr.t(ctx, "Через 10 минут", "Snooze 10 min", "10 daqiqadan keyin")))
            .addAction(act(Reminders.ACTION_DONE, Tr.t(ctx, "Готово", "Done", "Bajarildi")))
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle(Tr.t(ctx, "Напоминание: $title", "Reminder: $title", "Eslatma: $title"))
            .also { b -> task?.notes?.takeIf { it.isNotBlank() }?.let { b.setContentText(it) } }
            .setCategory(android.app.Notification.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setContentIntent(content)
            .build()
        try {
            (ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).notify(id.hashCode(), n)
        } catch (_: SecurityException) {
        }

        try {
            if (Reminders.serviceRunning(ctx)) {
                ctx.startService(
                    Intent(ctx, BuddyService::class.java).setAction(Reminders.ACTION_SPEAK).putExtra("text", title)
                )
            }
        } catch (_: Exception) {
        }
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        val act = intent.action
        if (act == Intent.ACTION_BOOT_COMPLETED || act == Intent.ACTION_MY_PACKAGE_REPLACED) {
            try { Reminders.rescheduleAll(ctx) } catch (_: Exception) {}
        }
        if (act == Intent.ACTION_BOOT_COMPLETED || act == Intent.ACTION_MY_PACKAGE_REPLACED) {
            try {
                val p = ctx.getSharedPreferences("buddy", Context.MODE_PRIVATE)
                if (p.getBoolean("autostart", true) && p.getBoolean("was_running", true) &&
                    android.provider.Settings.canDrawOverlays(ctx)
                ) {
                    ctx.startForegroundService(Intent(ctx, BuddyService::class.java))
                }
            } catch (_: Exception) {
            }
        }
    }
}
