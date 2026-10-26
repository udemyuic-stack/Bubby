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
        val i = km.createConfirmDeviceCredentialIntent(Tr.t(this, "Подтвердите разрешение", "Confirm permission", "Ruxsatni tasdiqlang"), Tr.t(this, "Разрешить агенту выполнить команду?", "Allow the agent to run this command?", "Agent buyruqni bajarishiga ruxsat berasizmi?"))
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

        fun request(ctx: Context, cb: (Boolean) -> Unit) {
            callback = cb
            ctx.startActivity(Intent(ctx, ConfirmActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }
}
