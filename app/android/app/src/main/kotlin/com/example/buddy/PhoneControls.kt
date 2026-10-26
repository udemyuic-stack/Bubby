package com.example.buddy

import android.app.ActivityManager
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.Ringtone
import android.media.RingtoneManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.BatteryManager
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.os.StatFs
import android.provider.Settings
import java.util.Locale

/** Phone-level controls: do not disturb, screen timeout, "find my phone" ring and a status report for the AI. */
object PhoneControls {
    private const val RING_MS = 30_000L
    private val handler = Handler(Looper.getMainLooper())
    private var ringtone: Ringtone? = null
    private val stopTask = Runnable { stopRing() }

    private fun open(ctx: Context, i: Intent) {
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        ctx.startActivity(i)
    }

    /** Turns "do not disturb" on (priority only: alarms still ring) or off. Asks for notification-policy access the first time. */
    fun dnd(ctx: Context, on: Boolean): String? {
        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (!nm.isNotificationPolicyAccessGranted) {
            open(ctx, Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS))
            return Tr.t(ctx, "Разреши Bubby доступ к режиму «Не беспокоить» и повтори команду", "Allow Bubby to control Do Not Disturb, then repeat the command", "Bubby'ga «Bezovta qilmang» rejimini boshqarishga ruxsat bering va buyruqni takrorlang")
        }
        nm.setInterruptionFilter(if (on) NotificationManager.INTERRUPTION_FILTER_PRIORITY else NotificationManager.INTERRUPTION_FILTER_ALL)
        return null
    }

    /** Sets how long the screen stays on, 15 s to 30 min. */
    fun screenTimeout(ctx: Context, seconds: Int): String? {
        if (seconds <= 0) return Tr.t(ctx, "На сколько секунд оставлять экран включённым?", "For how many seconds should the screen stay on?", "Ekran necha soniya yoniq tursin?")
        if (!Settings.System.canWrite(ctx)) {
            open(ctx, Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:" + ctx.packageName)))
            return Tr.t(ctx, "Разреши Bubby менять настройки системы и повтори команду", "Allow Bubby to change system settings, then repeat the command", "Bubby'ga tizim sozlamalarini o'zgartirishga ruxsat bering va buyruqni takrorlang")
        }
        Settings.System.putInt(ctx.contentResolver, Settings.System.SCREEN_OFF_TIMEOUT, seconds.coerceIn(15, 1800) * 1000)
        return null
    }

    /** Rings the alarm sound at full volume for 30 s so the phone can be found; on=false stops it. */
    fun findPhone(ctx: Context, on: Boolean): String? {
        handler.removeCallbacks(stopTask)
        stopRing()
        if (!on) return null
        val app = ctx.applicationContext
        val am = app.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        am.setStreamVolume(AudioManager.STREAM_ALARM, am.getStreamMaxVolume(AudioManager.STREAM_ALARM), 0)
        val uri = RingtoneManager.getActualDefaultRingtoneUri(app, RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
        val r = RingtoneManager.getRingtone(app, uri)
            ?: return Tr.t(ctx, "Не нашёл звук будильника", "Could not find the alarm sound", "Budilnik ovozi topilmadi")
        r.audioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ALARM)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        if (Build.VERSION.SDK_INT >= 28) r.isLooping = true
        r.play()
        ringtone = r
        handler.postDelayed(stopTask, RING_MS)
        return null
    }

    private fun stopRing() {
        val r = ringtone ?: return
        ringtone = null
        try { r.stop() } catch (_: Exception) { }
    }

    private fun gb(bytes: Long) = String.format(Locale.US, "%.1f GB", bytes / 1073741824.0)

    /** English facts about the phone for the AI. Safe to call off the main thread. */
    fun status(ctx: Context): String {
        val sb = StringBuilder()
        try {
            val i = ctx.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            val lvl = i?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
            val scale = i?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
            val st = i?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
            val charging = st == BatteryManager.BATTERY_STATUS_CHARGING || st == BatteryManager.BATTERY_STATUS_FULL
            if (lvl >= 0 && scale > 0) sb.append("Battery: ").append(lvl * 100 / scale).append('%').append(if (charging) ", charging" else ", not charging").append('\n')
        } catch (_: Exception) { }
        try {
            val sf = StatFs(Environment.getDataDirectory().absolutePath)
            sb.append("Storage: ").append(gb(sf.availableBytes)).append(" free of ").append(gb(sf.totalBytes)).append('\n')
        } catch (_: Exception) { }
        try {
            val am = ctx.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val mi = ActivityManager.MemoryInfo()
            am.getMemoryInfo(mi)
            sb.append("Memory: ").append(gb(mi.availMem)).append(" free of ").append(gb(mi.totalMem)).append('\n')
        } catch (_: Exception) { }
        try {
            val cm = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val caps = cm.getNetworkCapabilities(cm.activeNetwork)
            val net = when {
                caps == null -> "offline"
                caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "Wi-Fi"
                caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "mobile data"
                caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "ethernet"
                else -> "connected"
            }
            sb.append("Network: ").append(net).append('\n')
        } catch (_: Exception) { }
        try {
            val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            sb.append("Do not disturb: ").append(if (nm.currentInterruptionFilter > NotificationManager.INTERRUPTION_FILTER_ALL) "on" else "off").append('\n')
        } catch (_: Exception) { }
        try {
            val am = ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            val max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            if (max > 0) sb.append("Media volume: ").append(am.getStreamVolume(AudioManager.STREAM_MUSIC) * 100 / max).append("%\n")
        } catch (_: Exception) { }
        try {
            val cr = ctx.contentResolver
            sb.append("Brightness: ").append(Settings.System.getInt(cr, Settings.System.SCREEN_BRIGHTNESS) * 100 / 255).append("%\n")
            sb.append("Screen timeout: ").append(Settings.System.getInt(cr, Settings.System.SCREEN_OFF_TIMEOUT) / 1000).append(" s\n")
        } catch (_: Exception) { }
        return sb.toString().trim()
    }
}
