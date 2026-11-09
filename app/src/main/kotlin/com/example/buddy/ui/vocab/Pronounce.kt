package com.example.buddy.ui.vocab

import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale

enum class Accent { UK, US }

object Pronounce {
    private const val IDLE_MS = 60_000L

    private class Req(val text: String, val accent: Accent, val done: () -> Unit)

    private val h = Handler(Looper.getMainLooper())
    private var tts: TextToSpeech? = null
    private var ready = false
    private var failed = false
    private var queued: Req? = null
    private var curId: String? = null
    private var curDone: (() -> Unit)? = null
    private var seq = 0
    private var focus: (() -> Unit)? = null
    private var am: AudioManager? = null
    private val idle = Runnable { shutdown() }

    private val attrs: AudioAttributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY)
        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
        .build()

    fun available(ctx: Context): Boolean {
        if (failed) return false
        return try {
            ctx.packageManager.queryIntentServices(Intent(TextToSpeech.Engine.INTENT_ACTION_TTS_SERVICE), 0).isNotEmpty()
        } catch (_: Exception) {
            false
        }
    }

    fun speak(ctx: Context, text: String, accent: Accent = Accent.UK, done: () -> Unit = {}) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            h.post { speak(ctx, text, accent, done) }
            return
        }
        h.removeCallbacks(idle)
        finish()
        dropQueued()
        if (text.isBlank()) {
            done()
            scheduleIdle()
            return
        }
        val req = Req(text, accent, done)
        val engine = tts ?: create(ctx)
        if (engine == null) {
            done()
            return
        }
        if (ready) start(engine, req) else queued = req
    }

    fun stop() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            h.post { stop() }
            return
        }
        finish()
        dropQueued()
        scheduleIdle()
    }

    private fun create(ctx: Context): TextToSpeech? {
        val app = ctx.applicationContext
        am = app.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        failed = false
        ready = false
        var created: TextToSpeech? = null
        created = try {
            TextToSpeech(app) { st -> h.post { onInit(created, st) } }
        } catch (_: Exception) {
            null
        }
        val t = created
        if (t == null) {
            failed = true
            return null
        }
        tts = t
        try { t.setAudioAttributes(attrs) } catch (_: Exception) {}
        t.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(id: String?) {}
            override fun onDone(id: String?) { h.post { end(id) } }
            @Deprecated("old callback") override fun onError(id: String?) { h.post { end(id) } }
            override fun onError(id: String?, code: Int) { h.post { end(id) } }
            override fun onStop(id: String?, interrupted: Boolean) { h.post { end(id) } }
        })
        return t
    }

    private fun onInit(engine: TextToSpeech?, status: Int) {
        if (engine == null || engine !== tts) return
        if (status == TextToSpeech.SUCCESS) {
            ready = true
            val q = queued
            queued = null
            if (q != null) start(engine, q) else scheduleIdle()
        } else {
            failed = true
            tts = null
            ready = false
            try { engine.shutdown() } catch (_: Exception) {}
            dropQueued()
        }
    }

    private fun start(t: TextToSpeech, req: Req) {
        if (pickLocale(t, req.accent) == null) {
            req.done()
            scheduleIdle()
            return
        }
        val id = "pron${++seq}"
        curId = id
        curDone = req.done
        focus = requestFocus()
        val r = try { t.speak(req.text, TextToSpeech.QUEUE_FLUSH, null, id) } catch (_: Exception) { TextToSpeech.ERROR }
        if (r != TextToSpeech.SUCCESS) end(id)
    }

    private fun pickLocale(t: TextToSpeech, accent: Accent): Locale? {
        val order = if (accent == Accent.UK) listOf(Locale.UK, Locale.US, Locale.ENGLISH) else listOf(Locale.US, Locale.UK, Locale.ENGLISH)
        return order.firstOrNull {
            val r = try { t.setLanguage(it) } catch (_: Exception) { TextToSpeech.LANG_NOT_SUPPORTED }
            r >= TextToSpeech.LANG_AVAILABLE
        }
    }

    private fun end(id: String?) {
        if (id == null || id != curId) return
        val d = curDone
        curId = null
        curDone = null
        releaseFocus()
        scheduleIdle()
        d?.invoke()
    }

    private fun finish() {
        val d = curDone ?: return
        curId = null
        curDone = null
        try { tts?.stop() } catch (_: Exception) {}
        releaseFocus()
        d()
    }

    private fun dropQueued() {
        val q = queued ?: return
        queued = null
        q.done()
    }

    private fun scheduleIdle() {
        h.removeCallbacks(idle)
        if (tts != null) h.postDelayed(idle, IDLE_MS)
    }

    private fun shutdown() {
        if (curId != null || queued != null) return
        val t = tts ?: return
        tts = null
        ready = false
        try { t.shutdown() } catch (_: Exception) {}
    }

    private fun requestFocus(): (() -> Unit)? {
        val m = am ?: return null
        return try {
            val req = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK).setAudioAttributes(attrs).build()
            m.requestAudioFocus(req)
            val release: () -> Unit = { try { m.abandonAudioFocusRequest(req) } catch (_: Exception) {} }
            release
        } catch (_: Exception) {
            null
        }
    }

    private fun releaseFocus() {
        val f = focus ?: return
        focus = null
        f()
    }
}
