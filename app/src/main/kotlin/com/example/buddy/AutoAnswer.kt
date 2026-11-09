package com.example.buddy

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.ContactsContract
import android.telecom.TelecomManager
import android.telephony.TelephonyManager
import android.util.Log

object AutoAnswer {
    private const val TAG = "AutoAnswer"
    private const val CHANNEL = "calls"
    private const val VERIFY_MS = 1500L

    private val main = Handler(Looper.getMainLooper())
    private var timer: Runnable? = null
    private var verify: Runnable? = null
    private var held: BroadcastReceiver.PendingResult? = null
    private const val STALE_RING_MS = 120_000L

    private var ringing = false
    private var ringStartMs = 0L
    private var number: String? = null
    @Volatile private var userHandled = false

    enum class ServiceResult { ANSWERED, HANDLED, UNAVAILABLE }

    @Volatile var serviceAnswer: ((talk: Boolean) -> ServiceResult)? = null
    @Volatile var serviceReject: (() -> ServiceResult)? = null

    fun prefs(ctx: Context) = ctx.applicationContext.getSharedPreferences("buddy", Context.MODE_PRIVATE)

    fun enabled(ctx: Context) = prefs(ctx).getBoolean("call_auto", false)
    fun delaySec(ctx: Context) = AutoAnswerRules.delaySec(prefs(ctx).getInt("call_auto_delay", AutoAnswerRules.DEFAULT_DELAY))
    fun onlyContacts(ctx: Context) = prefs(ctx).getBoolean("call_auto_contacts", false)
    fun onlyHeadset(ctx: Context) = prefs(ctx).getBoolean("call_auto_headset", false)
    fun rejectSms(ctx: Context) = prefs(ctx).getBoolean("call_reject_sms", false)

    private fun granted(ctx: Context, p: String) = ctx.checkSelfPermission(p) == PackageManager.PERMISSION_GRANTED

    fun requiredPermissions(ctx: Context): List<String> {
        val p = mutableListOf(Manifest.permission.READ_PHONE_STATE, Manifest.permission.ANSWER_PHONE_CALLS)
        if (onlyContacts(ctx) || rejectSms(ctx)) { p.add(Manifest.permission.READ_CALL_LOG); p.add(Manifest.permission.READ_CONTACTS) }
        if (rejectSms(ctx)) p.add(Manifest.permission.SEND_SMS)
        return p
    }

    fun missingPermissions(ctx: Context): List<String> = requiredPermissions(ctx).filterNot { granted(ctx, it) }

    fun headsetOn(ctx: Context): Boolean = try {
        val am = ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val types = mutableSetOf(
            AudioDeviceInfo.TYPE_WIRED_HEADSET, AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
            AudioDeviceInfo.TYPE_BLUETOOTH_SCO, AudioDeviceInfo.TYPE_BLUETOOTH_A2DP, AudioDeviceInfo.TYPE_USB_HEADSET)
        if (Build.VERSION.SDK_INT >= 31) types.add(AudioDeviceInfo.TYPE_BLE_HEADSET)
        am.getDevices(AudioManager.GET_DEVICES_OUTPUTS).any { it.type in types }
    } catch (e: Exception) { Log.w(TAG, "headset check", e); false }

    private fun isContact(ctx: Context, n: String?): Boolean {
        if (n.isNullOrEmpty() || !granted(ctx, Manifest.permission.READ_CONTACTS)) return false
        return try {
            val uri = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(n))
            ctx.contentResolver.query(uri, arrayOf(ContactsContract.PhoneLookup._ID), null, null, null)?.use { it.moveToFirst() } ?: false
        } catch (e: Exception) { Log.w(TAG, "contact lookup", e); false }
    }

    fun onRinging(ctx: Context, incoming: String?, result: BroadcastReceiver.PendingResult?) {
        main.post {
            if (!incoming.isNullOrEmpty()) number = incoming
            val now = System.currentTimeMillis()
            if (ringing && now - ringStartMs < STALE_RING_MS) { result?.finish(); return@post }
            clearTimers()
            ringing = true
            ringStartMs = now
            userHandled = false
            if (!enabled(ctx)) { Log.i(TAG, "ringing, auto-answer off"); result?.finish(); return@post }
            held?.finish()
            held = result
            val r = Runnable { timer = null; fire(ctx.applicationContext) }
            timer = r
            main.postDelayed(r, delaySec(ctx) * 1000L)
            Log.i(TAG, "ringing, answer in ${delaySec(ctx)} s")
        }
    }

    fun onOffhookOrIdle() {
        main.post {
            ringing = false
            number = null
            clearTimers()
            release()
        }
    }

    fun cancel() {
        userHandled = true
        val r = Runnable {
            if (timer != null) Log.i(TAG, "cancelled by the user")
            clearTimers()
            release()
        }
        if (Looper.myLooper() == main.looper) r.run() else main.post(r)
    }

    private fun clearTimers() {
        timer?.let { main.removeCallbacks(it) }; timer = null
        verify?.let { main.removeCallbacks(it) }; verify = null
    }

    private fun release() {
        held?.finish()
        held = null
    }

    private fun fire(ctx: Context) {
        if (!ringing || userHandled) { release(); return }
        val n = number
        Thread {
            val skip = AutoAnswerRules.skipReason(
                enabled(ctx), missingPermissions(ctx).isEmpty(), onlyContacts(ctx), onlyContacts(ctx) && isContact(ctx, n),
                onlyHeadset(ctx), headsetOn(ctx))
            main.post { decide(ctx, skip) }
        }.start()
    }

    @Suppress("MissingPermission", "DEPRECATION")
    private fun stillRinging(ctx: Context): Boolean = try {
        (ctx.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager).callState == TelephonyManager.CALL_STATE_RINGING
    } catch (e: Exception) { Log.w(TAG, "call state", e); ringing }

    private fun decide(ctx: Context, skip: AutoAnswerRules.Skip?) {
        if (!ringing || userHandled) { release(); return }
        if (skip != null) {
            Log.i(TAG, "not answering: $skip")
            if (skip == AutoAnswerRules.Skip.NO_PERMISSION) failed(ctx, Tr.t(ctx, "нет разрешения на звонки", "no permission for calls", "qo'ng'iroqlarga ruxsat yo'q"))
            release()
            return
        }
        if (rejectSms(ctx)) {
            val r = try { serviceReject?.invoke() ?: ServiceResult.UNAVAILABLE } catch (e: Exception) { Log.w(TAG, "service reject", e); ServiceResult.UNAVAILABLE }
            if (r == ServiceResult.UNAVAILABLE) failed(ctx, Tr.t(ctx, "звонок не сброшен, SMS не отправлено", "the call was not declined, no SMS sent", "qo'ng'iroq rad etilmadi, SMS yuborilmadi"))
            release()
            return
        }
        val talk = prefs(ctx).getBoolean("call_talk", false)
        val viaService = try { serviceAnswer?.invoke(talk) ?: ServiceResult.UNAVAILABLE } catch (e: Exception) { Log.w(TAG, "service answer", e); ServiceResult.UNAVAILABLE }
        if (viaService == ServiceResult.HANDLED) { Log.i(TAG, "the user already handled the call"); release(); return }
        if (viaService == ServiceResult.UNAVAILABLE) acceptDirect(ctx)
        val v = Runnable {
            verify = null
            if (!userHandled && stillRinging(ctx)) {
                Log.w(TAG, "still ringing after accept, trying the notification button")
                if (!BuddyNotifications.answerFromNotification()) {
                    failed(ctx, if (BuddyNotifications.isEnabled(ctx))
                        Tr.t(ctx, "телефон не дал ответить", "the phone did not allow it", "telefon ruxsat bermadi")
                    else Tr.t(ctx, "включите доступ к уведомлениям", "turn on notification access", "bildirishnomalarga ruxsat bering"))
                }
            }
            release()
        }
        verify = v
        main.postDelayed(v, VERIFY_MS)
    }

    @Suppress("MissingPermission", "DEPRECATION")
    private fun acceptDirect(ctx: Context) {
        try {
            (ctx.getSystemService(Context.TELECOM_SERVICE) as TelecomManager).acceptRingingCall()
            Log.i(TAG, "acceptRingingCall sent")
        } catch (e: Exception) {
            Log.w(TAG, "acceptRingingCall failed", e)
        }
    }

    private fun failed(ctx: Context, reason: String) {
        try {
            val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (nm.getNotificationChannel(CHANNEL) == null)
                nm.createNotificationChannel(NotificationChannel(CHANNEL, Tr.t(ctx, "Звонки", "Calls", "Qo'ng'iroqlar"), NotificationManager.IMPORTANCE_DEFAULT))
            val n = Notification.Builder(ctx, CHANNEL)
                .setSmallIcon(R.drawable.ic_warning)
                .setContentTitle(Tr.t(ctx, "Не получилось ответить автоматически", "Could not answer automatically", "Avtomatik javob berib bo'lmadi"))
                .setContentText(reason)
                .setAutoCancel(true)
                .build()
            nm.notify(4711, n)
        } catch (e: Exception) { Log.w(TAG, "failure notification", e) }
    }
}
