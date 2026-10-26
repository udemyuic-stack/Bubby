package com.example.buddy

import android.content.Context
import android.os.Handler
import android.os.Looper
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.SamplerConfig
import org.json.JSONArray

private class LlmError(msg: String) : Exception(msg)

object LocalLlm {
    private const val IDLE_MS = 3 * 60 * 1000L
    private val lock = Any()
    private val handler = Handler(Looper.getMainLooper())
    private var engine: Engine? = null
    private var loadedId: String? = null
    private var loadedBackend: String? = null
    private val idleTask = Runnable { Thread { release() }.start() }

    private fun prefs(ctx: Context) = ctx.applicationContext.getSharedPreferences("buddy", Context.MODE_PRIVATE)

    private fun modelId(ctx: Context): String {
        val id = prefs(ctx).getString("gemma_model", "e2b") ?: "e2b"
        return if (id in ModelStore.IDS) id else "e2b"
    }

    fun loadedModel(): String? = loadedId

    fun available(ctx: Context): Boolean {
        val id = modelId(ctx)
        val f = ModelStore.file(ctx, id)
        return f.exists() && f.length() == ModelStore.expectedSize(id)
    }

    private fun build(path: String, backend: String): Engine {
        val b = if (backend == "cpu") Backend.CPU() else Backend.GPU()
        val e = Engine(EngineConfig(modelPath = path, backend = b))
        try {
            e.initialize()
        } catch (t: Throwable) {
            try {
                e.close()
            } catch (ignored: Throwable) {
            }
            throw t
        }
        return e
    }

    // must hold lock
    private fun ensure(ctx: Context, forceCpu: Boolean = false): Engine {
        val id = modelId(ctx)
        val cur = engine
        if (cur != null && loadedId == id && !(forceCpu && loadedBackend != "cpu")) return cur
        closeLocked()
        if (!available(ctx)) throw LlmError(Tr.t(ctx, "Модель Gemma не скачана. Скачайте её в настройках.", "The Gemma model is not downloaded. Download it in settings.", "Gemma modeli yuklab olinmagan. Uni sozlamalarda yuklab oling."))
        val path = ModelStore.file(ctx, id).absolutePath
        val p = prefs(ctx)
        val first = if (forceCpu) "cpu" else (p.getString("gemma_backend", null) ?: "gpu")
        var used = first
        val e: Engine = try {
            build(path, first)
        } catch (t: Throwable) {
            if (first == "cpu") throw LlmError(Tr.t(ctx, "Не удалось запустить Gemma: ", "Could not start Gemma: ", "Gemma ishga tushmadi: ") + (t.message ?: t.javaClass.simpleName))
            used = "cpu"
            try {
                build(path, "cpu")
            } catch (t2: Throwable) {
                throw LlmError(Tr.t(ctx, "Не удалось запустить Gemma: ", "Could not start Gemma: ", "Gemma ishga tushmadi: ") + (t2.message ?: t2.javaClass.simpleName))
            }
        }
        p.edit().putString("gemma_backend", used).apply()
        engine = e
        loadedId = id
        loadedBackend = used
        return e
    }

    private fun closeLocked() {
        val e = engine
        engine = null
        loadedId = null
        loadedBackend = null
        if (e != null) {
            try {
                e.close()
            } catch (ignored: Throwable) {
            }
        }
    }

    private fun pack(history: JSONArray): String {
        var n = history.length()
        var last: String? = null
        if (n > 0) {
            val o = history.optJSONObject(n - 1)
            if (o != null && o.optString("role") == "user") {
                last = o.optString("content")
                n--
            }
        }
        val sb = StringBuilder()
        val from = maxOf(0, n - 4)
        if (from < n) {
            sb.append("Conversation so far:\n")
            for (i in from until n) {
                val o = history.optJSONObject(i) ?: continue
                val who = if (o.optString("role") == "user") "User" else "You"
                sb.append(who).append(": ").append(o.optString("content")).append('\n')
            }
            sb.append('\n')
        }
        if (last != null) sb.append("User: ").append(last)
        return sb.toString().trim()
    }

    private fun ask(e: Engine, sys: String, text: String): String {
        val conv = e.createConversation(
            ConversationConfig(
                systemInstruction = Contents.of(sys),
                samplerConfig = SamplerConfig(topK = 10, topP = 0.95, temperature = 0.3)
            )
        )
        try {
            return conv.sendMessage(text).toString()
        } finally {
            try {
                conv.close()
            } catch (ignored: Throwable) {
            }
        }
    }

    fun chat(ctx: Context, sys: String, history: JSONArray): String {
        val app = ctx.applicationContext
        val text = pack(history)
        if (text.isEmpty()) throw LlmError(Tr.t(app, "Пустой запрос.", "Empty request.", "So'rov bo'sh."))
        handler.removeCallbacks(idleTask)
        try {
            return synchronized(lock) {
                var e = ensure(app)
                val out = try {
                    ask(e, sys, text)
                } catch (t: Throwable) {
                    if (loadedBackend == "cpu") throw t
                    // GPU can fail only at inference time: remember and retry on CPU
                    prefs(app).edit().putString("gemma_backend", "cpu").apply()
                    e = ensure(app, true)
                    ask(e, sys, text)
                }
                if (out.isBlank()) throw LlmError(Tr.t(app, "Gemma вернула пустой ответ.", "Gemma returned an empty answer.", "Gemma bo'sh javob qaytardi."))
                out
            }
        } catch (t: OutOfMemoryError) {
            release()
            throw LlmError(Tr.t(app, "Не хватает памяти для Gemma. Выберите модель поменьше (E2B).", "Not enough memory for Gemma. Choose a smaller model (E2B).", "Gemma uchun xotira yetarli emas. Kichikroq model (E2B) tanlang."))
        } catch (t: Throwable) {
            if (t is LlmError) throw t
            if (t.message != null && t.message!!.any { it in 'а'..'я' || it in 'А'..'Я' }) throw (t as? Exception ?: Exception(t.message))
            throw LlmError(Tr.t(app, "Ошибка Gemma: ", "Gemma error: ", "Gemma xatosi: ") + (t.message ?: t.javaClass.simpleName))
        } finally {
            handler.removeCallbacks(idleTask)
            handler.postDelayed(idleTask, IDLE_MS)
        }
    }

    fun warmUp(ctx: Context) {
        val app = ctx.applicationContext
        if (!available(app)) return
        Thread {
            try {
                handler.removeCallbacks(idleTask)
                synchronized(lock) { ensure(app) }
                handler.postDelayed(idleTask, IDLE_MS)
            } catch (ignored: Throwable) {
            }
        }.start()
    }

    fun release() {
        handler.removeCallbacks(idleTask)
        synchronized(lock) { closeLocked() }
    }
}
