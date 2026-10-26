package com.example.buddy

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale

class Voice(private val ctx: Context, private val h: Handler) {
    private var sr: SpeechRecognizer? = null
    private var hot: SpeechRecognizer? = null
    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var queued: (() -> Unit)? = null
    private var onSpoken: (() -> Unit)? = null
    private var seq = 0
    private var curId = ""
    private var spk = false
    private var focusRelease: (() -> Unit)? = null

    /** Called on the main thread with true exactly while TTS audio is being spoken, false otherwise (lip-sync). */
    var onSpeaking: (Boolean) -> Unit = {}

    val speaking get() = tts?.isSpeaking == true

    /** Other hypotheses of the last recognised utterance (best first, without the winner). */
    @Volatile var lastAlternatives: List<String> = emptyList()
    /** True from the moment a speak() is requested until it finished or was stopped. */
    val talking get() = curId.isNotEmpty() || speaking
    val listening get() = sr != null || pendingListen
    private var pendingListen = false
    val hotRunning get() = hot != null

    /** Abandons the speech audio focus; safe to call any number of times (releases exactly once). */
    private fun releaseFocus() {
        val f = focusRelease ?: return
        focusRelease = null
        try { f() } catch (_: Exception) {}
    }

    /** API 34+ in auto language mode: let the recogniser pick between Uzbek, Russian and English. */
    private fun addLangDetection(i: Intent, langTag: String?) {
        val l = ctx.getSharedPreferences("buddy", Context.MODE_PRIVATE).getString("lang", "auto") ?: "auto"
        if (l == "uz" || l == "ru" || l == "en") return
        if (Build.VERSION.SDK_INT >= 34) {
            try {
                i.putExtra("android.speech.extra.ENABLE_LANGUAGE_DETECTION", true)
                i.putStringArrayListExtra("android.speech.extra.LANGUAGE_DETECTION_ALLOWED_LANGUAGES", arrayListOf("uz-UZ", "ru-RU", "en-US"))
            } catch (_: Exception) {}
        }
    }

    private fun setSpeaking(b: Boolean) {
        if (spk == b) return
        spk = b
        try { onSpeaking(b) } catch (_: Exception) {}
    }

    /** [done] gets (text, null) on success, (null, null) when nothing was heard, (null, message) on a real error. */
    fun listen(langTag: String?, partial: (String) -> Unit, done: (String?, String?) -> Unit) {
        val hadHot = hot != null
        stopHot()
        stopListening()
        stopSpeaking()
        if (!SpeechRecognizer.isRecognitionAvailable(ctx)) { done(null, "нет распознавания речи"); return }
        val i = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            .putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5)
            .putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, ctx.packageName)
        if (langTag != null) i.putExtra(RecognizerIntent.EXTRA_LANGUAGE, langTag)
        addLangDetection(i, langTag)
        // the system recogniser is often still busy right after the wake-word one was destroyed
        if (hadHot) {
            val token = ++listenToken
            pendingListen = true
            h.postDelayed({ if (token == listenToken) { pendingListen = false; startMain(i, 0, partial, done) } }, 250)
        } else startMain(i, 0, partial, done)
    }

    private var listenToken = 0

    private fun startMain(i: Intent, attempt: Int, partial: (String) -> Unit, done: (String?, String?) -> Unit) {
        val r = try { SpeechRecognizer.createSpeechRecognizer(ctx) } catch (_: Exception) {
            done(null, "ошибка распознавания (${SpeechRecognizer.ERROR_CLIENT})"); return
        }
        sr = r
        r.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(p: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(v: Float) {}
            override fun onBufferReceived(b: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onEvent(t: Int, p: Bundle?) {}
            override fun onPartialResults(b: Bundle?) {
                if (sr !== r) return
                b?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.let(partial)
            }
            override fun onResults(b: Bundle?) {
                if (sr !== r) return
                val all = b?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val t = all?.firstOrNull()
                lastAlternatives = all?.drop(1)?.filter { it.isNotBlank() && !it.equals(t, ignoreCase = true) }?.take(4) ?: emptyList()
                release(r); done(t?.takeIf { it.isNotBlank() }, null)
            }
            override fun onError(e: Int) {
                if (sr !== r) return
                release(r)
                if (attempt == 0 && (e == SpeechRecognizer.ERROR_RECOGNIZER_BUSY || e == SpeechRecognizer.ERROR_CLIENT)) {
                    val token = ++listenToken
                    pendingListen = true
                    h.postDelayed({ if (token == listenToken) { pendingListen = false; startMain(i, 1, partial, done) } }, 300)
                    return
                }
                when (e) {
                    SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> done(null, null)
                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> done(null, "нет доступа к микрофону")
                    SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> done(null, "нет сети для распознавания")
                    else -> done(null, "ошибка распознавания ($e)")
                }
            }
        })
        try { r.startListening(i) } catch (_: Exception) { release(r); done(null, "ошибка распознавания (${SpeechRecognizer.ERROR_CLIENT})") }
    }

    /**
     * One lightweight wake-word pass: [done] gets (text or null, 0) on a result, or (null, SpeechRecognizer.ERROR_*).
     * The system recogniser may beep on start; we do not touch audio streams, so that is accepted.
     */
    fun listenHot(langTag: String?, done: (String?, Int) -> Unit) {
        stopHot()
        lastAlternatives = emptyList()
        if (!SpeechRecognizer.isRecognitionAvailable(ctx)) { done(null, SpeechRecognizer.ERROR_CLIENT); return }
        val r = try { SpeechRecognizer.createSpeechRecognizer(ctx) } catch (_: Exception) {
            done(null, SpeechRecognizer.ERROR_CLIENT); return
        }
        hot = r
        r.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(p: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(v: Float) {}
            override fun onBufferReceived(b: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onEvent(t: Int, p: Bundle?) {}
            override fun onPartialResults(b: Bundle?) {}
            override fun onResults(b: Bundle?) {
                if (hot !== r) return
                lastAlternatives = emptyList()
                val t = b?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.joinToString(" | ")
                releaseHot(r); done(t?.takeIf { it.isNotBlank() }, 0)
            }
            override fun onError(e: Int) {
                if (hot !== r) return
                releaseHot(r); done(null, e)
            }
        })
        val i = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
            .putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
            .putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
            .putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, ctx.packageName)
        if (langTag != null) i.putExtra(RecognizerIntent.EXTRA_LANGUAGE, langTag)
        addLangDetection(i, langTag)
        try { r.startListening(i) } catch (_: Exception) { releaseHot(r); done(null, SpeechRecognizer.ERROR_CLIENT) }
    }

    private fun releaseHot(r: SpeechRecognizer) {
        if (hot === r) hot = null
        try { r.destroy() } catch (_: Exception) {}
    }

    fun stopHot() {
        val r = hot ?: return
        hot = null
        try { r.cancel(); r.destroy() } catch (_: Exception) {}
    }

    private fun release(r: SpeechRecognizer) {
        if (sr === r) sr = null
        try { r.destroy() } catch (_: Exception) {}
    }

    fun stopListening() {
        listenToken++; pendingListen = false
        val r = sr ?: return
        sr = null
        try { r.cancel(); r.destroy() } catch (_: Exception) {}
    }

    fun stopSpeaking() {
        onSpoken = null
        curId = ""
        try { tts?.stop() } catch (_: Exception) {}
        setSpeaking(false)
        releaseFocus()
    }

    /** Reads [text] aloud in [lang] (ISO code, null = phone language); [done] runs on the main thread when finished. */
    fun speak(text: String, lang: String?, done: () -> Unit) {
        if (text.isBlank()) { done(); return }
        stopHot()
        curId = "pending"
        val go: () -> Unit = {
            val t = tts
            if (t == null) { curId = ""; setSpeaking(false); releaseFocus(); done() }
            else {
                setLanguage(t, lang)
                val id = "buddy${++seq}"
                curId = id
                onSpoken = done
                releaseFocus()
                focusRelease = try { MediaControl.requestFocusForSpeech(ctx) } catch (_: Exception) { null }
                val r = try { t.speak(text, TextToSpeech.QUEUE_FLUSH, null, id) } catch (_: Exception) { TextToSpeech.ERROR }
                if (r != TextToSpeech.SUCCESS) end(id)
            }
        }
        if (ttsReady) { go(); return }
        queued = go
        if (tts == null) {
            tts = TextToSpeech(ctx) { st ->
                ttsReady = st == TextToSpeech.SUCCESS
                tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(id: String?) { h.post { if (id == curId) setSpeaking(true) } }
                    override fun onDone(id: String?) { end(id) }
                    @Deprecated("old callback") override fun onError(id: String?) { end(id) }
                    override fun onError(id: String?, code: Int) { end(id) }
                    override fun onStop(id: String?, interrupted: Boolean) {
                        h.post { if (id == curId) { curId = ""; setSpeaking(false); releaseFocus() } }
                    }
                })
                val q = queued; queued = null
                if (ttsReady) q?.invoke() else {
                    try { tts?.shutdown() } catch (_: Exception) {}
                    tts = null
                    h.post { curId = ""; setSpeaking(false); releaseFocus(); done() }
                }
            }
        }
    }

    private fun end(id: String?) {
        h.post {
            if (id != curId) return@post
            curId = ""
            setSpeaking(false)
            releaseFocus()
            val cb = onSpoken; onSpoken = null; cb?.invoke()
        }
    }

    // Google TTS often has no Uzbek voice; Turkish reads Latin-script Uzbek far better than Russian does.
    private fun setLanguage(t: TextToSpeech, lang: String?) {
        val tries = when (lang) {
            null, "" -> listOf(Locale.getDefault())
            "uz" -> listOf(Locale("uz"), Locale("tr"), Locale.getDefault())
            else -> listOf(Locale(lang), Locale.getDefault())
        }
        for (l in tries) {
            if (t.isLanguageAvailable(l) >= TextToSpeech.LANG_AVAILABLE) { t.language = l; return }
        }
    }

    fun shutdown() {
        onSpeaking = {}
        releaseFocus()
        stopHot()
        stopListening()
        try { tts?.stop(); tts?.shutdown() } catch (_: Exception) {}
        tts = null; ttsReady = false; queued = null; onSpoken = null; curId = ""; spk = false
    }
}
