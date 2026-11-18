package com.example.buddy

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.telephony.TelephonyManager
import android.util.Log

class CallStateReceiver : BroadcastReceiver() {
    @Suppress("DEPRECATION")
    override fun onReceive(ctx: Context, intent: Intent) {
        if (intent.action != TelephonyManager.ACTION_PHONE_STATE_CHANGED) return
        try {
            when (intent.getStringExtra(TelephonyManager.EXTRA_STATE)) {
                TelephonyManager.EXTRA_STATE_RINGING ->
                    AutoAnswer.onRinging(ctx.applicationContext, intent.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER), goAsync())
                TelephonyManager.EXTRA_STATE_OFFHOOK, TelephonyManager.EXTRA_STATE_IDLE -> AutoAnswer.onOffhookOrIdle()
            }
        } catch (e: Exception) {
            Log.w("CallStateReceiver", "phone state", e)
        }
    }
}
