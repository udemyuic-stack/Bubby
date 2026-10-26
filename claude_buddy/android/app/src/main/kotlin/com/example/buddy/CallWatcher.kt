package com.example.buddy

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.ContactsContract
import android.telephony.TelephonyManager

class CallWatcher(
    private val ctx: Context,
    private val onRinging: (name: String?) -> Unit,
    private val onEnded: () -> Unit
) {
    private val handler = Handler(Looper.getMainLooper())
    private var registered = false
    private var ringing = false
    private var announced = false
    private var pending: Runnable? = null

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context?, i: Intent?) {
            try { handle(i) } catch (_: Exception) { }
        }
    }

    @Suppress("DEPRECATION")
    private fun handle(i: Intent?) {
        if (i == null || i.action != TelephonyManager.ACTION_PHONE_STATE_CHANGED) return
        when (i.getStringExtra(TelephonyManager.EXTRA_STATE)) {
            TelephonyManager.EXTRA_STATE_RINGING -> {
                ringing = true
                val number = i.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER)
                if (announced) return
                if (!number.isNullOrEmpty()) {
                    announce(number)
                } else if (pending == null) {
                    // the number often arrives in a second broadcast; wait briefly for it
                    val r = Runnable { pending = null; announce(null) }
                    pending = r
                    handler.postDelayed(r, 800)
                }
            }
            TelephonyManager.EXTRA_STATE_IDLE, TelephonyManager.EXTRA_STATE_OFFHOOK -> {
                pending?.let { handler.removeCallbacks(it) }
                pending = null
                if (ringing) {
                    ringing = false; announced = false
                    onEnded()
                }
            }
        }
    }

    private fun announce(number: String?) {
        pending?.let { handler.removeCallbacks(it) }
        pending = null
        announced = true
        onRinging(if (number.isNullOrEmpty()) null else nameFor(number))
    }

    private fun nameFor(number: String): String? {
        if (ctx.checkSelfPermission(Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) return null
        return try {
            val uri = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(number))
            ctx.contentResolver.query(uri, arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME), null, null, null)?.use {
                if (it.moveToFirst()) it.getString(0) else null
            }
        } catch (_: Exception) { null }
    }

    fun start() {
        if (registered) return
        val f = IntentFilter(TelephonyManager.ACTION_PHONE_STATE_CHANGED)
        try {
            if (Build.VERSION.SDK_INT >= 33) ctx.registerReceiver(receiver, f, Context.RECEIVER_EXPORTED)
            else ctx.registerReceiver(receiver, f)
            registered = true
        } catch (_: Exception) { }
    }

    fun stop() {
        pending?.let { handler.removeCallbacks(it) }
        pending = null
        ringing = false; announced = false
        if (!registered) return
        try { ctx.unregisterReceiver(receiver) } catch (_: Exception) { }
        registered = false
    }

    companion object {
        private val answerStems = listOf("ответ", "возьми", "бери трубку", "подними", "прими", "принять", "javob ber", "javob", "telefonni ol", "trubkani ol", "ko'taring", "ha javob", "javob bering", "jawob", "ответь", "ответить", "отвечай", "возьми трубку", "поднимай", "answer it", "answer the", "take it", "pick it up", "get it", "yes answer", "ko'tar", "ko‘tar", "qabul", "answer", "pick up", "pickup", "accept")
        private val answerExact = setOf("да", "ха", "ha", "yes", "ok", "окей", "давай", "yeah", "yep", "sure", "okay", "xo'p", "xop", "mayli", "ha ha", "да да", "ага", "конечно")
        private val rejectStems = listOf("не отвечай", "не бери", "не надо", "отклон", "сбрось", "сбрас", "отбой", "положи", "заверши", "не хочу", "javob berma", "rad et", "ko'tarma", "ko‘tarma", "olma", "tashla", "reject", "decline", "javob bermang", "olmang", "rad qil", "o'chir", "qo'ng'iroqni tashla", "отказ", "не принимай", "не поднимай", "сбрось звонок", "don't answer", "do not answer", "don't pick", "not now", "cancel the call", "end call", "end the call", "hang up", "hangup", "ignore", "dismiss")
        private val rejectExact = setOf("нет", "не", "yo'q", "yoq", "no", "nope", "yo'q", "нет нет", "nah", "yo'qol")

        private fun norm(s: String) = s.lowercase().replace('ё', 'е').trim().trim('.', '!', '?', ',')

        fun looksLikeReject(s: String): Boolean {
            val t = norm(s)
            return t in rejectExact || rejectStems.any { t.contains(it) }
        }

        fun looksLikeAnswer(s: String): Boolean {
            val t = norm(s)
            if (looksLikeReject(t)) return false
            return t in answerExact || answerStems.any { t.contains(it) }
        }
    }
}
