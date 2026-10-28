package com.example.buddy

import android.app.Activity
import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle

/** Transparent hop that asks for the screen lock (PIN, pattern, fingerprint) and reports the result through [callback]. */
class ConfirmActivity : Activity() {
    private var delivered = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState != null) return
        val km = getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
        if (km == null || !km.isDeviceSecure) {
            done(false)
            return
        }
        val title = intent.getStringExtra("title") ?: Tr.t(this, "Подтвердите разрешение", "Confirm permission", "Ruxsatni tasdiqlang")
        val desc = intent.getStringExtra("desc") ?: Tr.t(this, "Разрешить агенту выполнить команду?", "Allow the agent to run this command?", "Agent buyruqni bajarishiga ruxsat berasizmi?")
        val i = km.createConfirmDeviceCredentialIntent(title, desc)
        if (i == null) {
            done(false)
            return
        }
        try {
            startActivityForResult(i, REQ)
        } catch (e: Exception) {
            done(false)
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQ) done(resultCode == RESULT_OK)
    }

    override fun onDestroy() {
        if (!delivered && isFinishing) {
            delivered = true
            val cb = callback
            callback = null
            cb?.invoke(false)
        }
        super.onDestroy()
    }

    private fun done(ok: Boolean) {
        if (!delivered) {
            delivered = true
            val cb = callback
            callback = null
            cb?.invoke(ok)
        }
        finish()
    }

    companion object {
        private const val REQ = 4711

        /** Set before starting the activity; called once on the main thread with true only if the user confirmed. */
        var callback: ((Boolean) -> Unit)? = null

        fun request(ctx: Context, cb: (Boolean) -> Unit) = request(ctx, null, null, cb)

        /** Same, with the words the lock screen shows (null keeps "Confirm permission"). */
        fun request(ctx: Context, title: String?, desc: String?, cb: (Boolean) -> Unit) {
            callback = cb
            val i = Intent(ctx, ConfirmActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (title != null) i.putExtra("title", title)
            if (desc != null) i.putExtra("desc", desc)
            ctx.startActivity(i)
        }

        /** The lock screen words before an answer to Claude Code's question leaves the phone. */
        fun requestAnswer(ctx: Context, cb: (Boolean) -> Unit) = request(ctx,
            Tr.t(ctx, "Подтвердите ответ", "Confirm the answer", "Javobni tasdiqlang"),
            Tr.t(ctx, "Отправить ответ на вопрос Claude?", "Send your answer to Claude's question?", "Claude savoliga javobni yuborasizmi?"), cb)
    }
}
