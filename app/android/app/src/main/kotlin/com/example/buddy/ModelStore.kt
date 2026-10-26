package com.example.buddy

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.StatFs
import java.io.File
import java.util.concurrent.ConcurrentHashMap

object ModelStore {
    val IDS = listOf("e2b", "e4b")

    const val ACTION_CANCEL = "com.example.buddy.MODEL_CANCEL"
    const val EXTRA_ID = "id"

    private const val BASE = "https://huggingface.co/litert-community/"
    private val REPOS = mapOf(
        "e2b" to "gemma-4-E2B-it-litert-lm/resolve/main/gemma-4-E2B-it.litertlm",
        "e4b" to "gemma-4-E4B-it-litert-lm/resolve/main/gemma-4-E4B-it.litertlm"
    )
    private val SIZES = mapOf("e2b" to 2588147712L, "e4b" to 3659530240L)

    class State(val state: String, val done: Long, val total: Long, val error: String?)

    private val states = ConcurrentHashMap<String, State>()

    fun url(id: String): String = BASE + (REPOS[id] ?: "")
    fun expectedSize(id: String): Long = SIZES[id] ?: 0L

    fun dir(ctx: Context): File = File(ctx.filesDir, "models").also { it.mkdirs() }
    fun file(ctx: Context, id: String): File = File(dir(ctx), "$id.litertlm")
    fun partFile(ctx: Context, id: String): File = File(dir(ctx), "$id.litertlm.part")

    fun setState(id: String, state: String, done: Long, total: Long, error: String? = null) {
        states[id] = State(state, done, total, error)
    }

    fun clearState(id: String) {
        states.remove(id)
    }

    private fun one(ctx: Context, id: String): Map<String, Any?> {
        val f = file(ctx, id)
        val total = expectedSize(id)
        if (f.exists() && f.length() == total) {
            return mapOf("state" to "ready", "done" to total, "total" to total, "error" to null)
        }
        val s = states[id]
        if (s != null && s.state != "ready") {
            return mapOf("state" to s.state, "done" to s.done, "total" to s.total, "error" to s.error)
        }
        val part = partFile(ctx, id)
        return mapOf("state" to "none", "done" to (if (part.exists()) part.length() else 0L), "total" to total, "error" to null)
    }

    fun status(ctx: Context): Map<String, Any?> {
        val am = ctx.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val mi = ActivityManager.MemoryInfo()
        am.getMemoryInfo(mi)
        val free = try {
            StatFs(ctx.filesDir.absolutePath).availableBytes
        } catch (e: Exception) {
            0L
        }
        val m = LinkedHashMap<String, Any?>()
        for (id in IDS) m[id] = one(ctx, id)
        m["supported"] = LocalLlm.supported
        m["ramGb"] = mi.totalMem / 1073741824.0
        m["freeGb"] = free / 1073741824.0
        return m
    }

    fun start(ctx: Context, id: String) {
        if (id !in IDS) return
        if (!LocalLlm.supported) {
            setState(id, "error", 0L, expectedSize(id), Tr.t(ctx, "Нужен 64-битный телефон.", "This needs a 64-bit phone.", "64-bitli telefon kerak."))
            return
        }
        val app = ctx.applicationContext
        val i = Intent(app, ModelDownloadService::class.java).putExtra(EXTRA_ID, id)
        try {
            if (Build.VERSION.SDK_INT >= 26) app.startForegroundService(i) else app.startService(i)
        } catch (e: Exception) {
            setState(id, "error", 0L, expectedSize(id), Tr.t(ctx, "Не удалось запустить загрузку: ", "Could not start the download: ", "Yuklashni boshlab bo'lmadi: ") + (e.message ?: ""))
        }
    }

    fun cancel(ctx: Context) {
        val app = ctx.applicationContext
        try {
            app.startService(Intent(app, ModelDownloadService::class.java).setAction(ACTION_CANCEL))
        } catch (e: Exception) {
            ModelDownloadService.cancelRequested = true
        }
    }

    fun delete(ctx: Context, id: String) {
        if (id !in IDS) return
        if (ModelDownloadService.activeId == id) cancel(ctx)
        if (LocalLlm.loadedModel() == id) LocalLlm.release()
        file(ctx, id).delete()
        partFile(ctx, id).delete()
        clearState(id)
    }
}
