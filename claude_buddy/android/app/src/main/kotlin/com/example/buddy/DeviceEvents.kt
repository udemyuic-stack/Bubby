package com.example.buddy

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.BatteryManager
import android.os.Build
import android.os.Handler
import android.os.Looper

/** Emits coarse device events: charging_started/stopped, battery_low, battery_full, headphones_in/out, welcome_back. */
object DeviceEvents {
    private const val DEBOUNCE_MS = 60_000L
    private const val IDLE_MS = 3 * 3_600_000L
    private val main = Handler(Looper.getMainLooper())
    private val last = HashMap<String, Long>()
    private var receiver: BroadcastReceiver? = null
    private var callback: AudioDeviceCallback? = null
    private var listener: ((String) -> Unit)? = null
    private var startedAt = 0L
    private var lastOff = 0L
    private var fullSent = false

    private fun emit(e: String) {
        val now = System.currentTimeMillis()
        synchronized(last) {
            val prev = last[e]
            if (prev != null && now - prev < DEBOUNCE_MS) return
            last[e] = now
        }
        main.post { try { listener?.invoke(e) } catch (_: Exception) {} }
    }

    private fun isHeadset(d: AudioDeviceInfo): Boolean = when (d.type) {
        AudioDeviceInfo.TYPE_WIRED_HEADSET, AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
        AudioDeviceInfo.TYPE_BLUETOOTH_A2DP, AudioDeviceInfo.TYPE_USB_HEADSET -> true
        else -> false
    }

    @Synchronized
    fun start(ctx: Context, onEvent: (String) -> Unit) {
        listener = onEvent
        if (receiver != null) return
        val app = ctx.applicationContext
        startedAt = System.currentTimeMillis()
        lastOff = startedAt
        val r = object : BroadcastReceiver() {
            override fun onReceive(c: Context, i: Intent) {
                when (i.action) {
                    Intent.ACTION_POWER_CONNECTED -> emit("charging_started")
                    Intent.ACTION_POWER_DISCONNECTED -> { fullSent = false; emit("charging_stopped") }
                    Intent.ACTION_BATTERY_LOW -> emit("battery_low")
                    Intent.ACTION_BATTERY_OKAY -> {}
                    Intent.ACTION_BATTERY_CHANGED -> {
                        val level = i.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                        val scale = i.getIntExtra(BatteryManager.EXTRA_SCALE, 100).coerceAtLeast(1)
                        val plugged = i.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) != 0
                        if (!plugged) fullSent = false
                        else if (!fullSent && level >= 0 && level * 100 / scale >= 100) {
                            fullSent = true
                            emit("battery_full")
                        }
                    }
                    Intent.ACTION_HEADSET_PLUG ->
                        emit(if (i.getIntExtra("state", 0) == 1) "headphones_in" else "headphones_out")
                    Intent.ACTION_SCREEN_OFF -> lastOff = System.currentTimeMillis()
                    Intent.ACTION_USER_PRESENT -> {
                        val now = System.currentTimeMillis()
                        if (now - lastOff >= IDLE_MS) emit("welcome_back")
                        lastOff = now
                    }
                }
            }
        }
        val f = IntentFilter().apply {
            addAction(Intent.ACTION_POWER_CONNECTED)
            addAction(Intent.ACTION_POWER_DISCONNECTED)
            addAction(Intent.ACTION_BATTERY_LOW)
            addAction(Intent.ACTION_BATTERY_OKAY)
            addAction(Intent.ACTION_BATTERY_CHANGED)
            addAction(Intent.ACTION_HEADSET_PLUG)
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_USER_PRESENT)
        }
        try {
            if (Build.VERSION.SDK_INT >= 33) app.registerReceiver(r, f, Context.RECEIVER_NOT_EXPORTED)
            else app.registerReceiver(r, f)
            receiver = r
        } catch (_: Exception) {
        }
        try {
            val am = app.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            val cb = object : AudioDeviceCallback() {
                override fun onAudioDevicesAdded(added: Array<out AudioDeviceInfo>) {
                    if (System.currentTimeMillis() - startedAt < 2000) return
                    if (added.any { isHeadset(it) }) emit("headphones_in")
                }
                override fun onAudioDevicesRemoved(removed: Array<out AudioDeviceInfo>) {
                    if (removed.any { isHeadset(it) }) emit("headphones_out")
                }
            }
            am.registerAudioDeviceCallback(cb, main)
            callback = cb
        } catch (_: Exception) {
        }
    }

    @Synchronized
    fun stop(ctx: Context) {
        val app = ctx.applicationContext
        receiver?.let { try { app.unregisterReceiver(it) } catch (_: Exception) {} }
        receiver = null
        callback?.let {
            try { (app.getSystemService(Context.AUDIO_SERVICE) as AudioManager).unregisterAudioDeviceCallback(it) } catch (_: Exception) {}
        }
        callback = null
        listener = null
    }
}
