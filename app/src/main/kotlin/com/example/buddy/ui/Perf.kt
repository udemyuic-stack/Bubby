package com.example.buddy.ui

import android.content.Context
import android.os.Handler
import android.os.Looper

/**
 * The web pages behind the themed looks are heavy for a weak phone. A few seconds after a page is up it counts its own frames
 * and reports them here; when two reports in a row are low the animated background is switched off (native look), once,
 * and the person is told where to switch it back on.
 */
object Perf {
    private const val SLOW_FPS = 22
    private var slow = 0
    private var done = false

    /** [fps]: frames per second a page just measured while it was visible (0 means it was hidden: ignored). */
    fun report(ctx: Context, fps: Int, who: String) {
        android.util.Log.i("com.example.buddy", "Perf: $who $fps fps")
        val act = ctx as? UiActivity ?: return
        if (done || fps <= 0 || act.intent.getBooleanExtra("nosetup", false) || act.intent.getBooleanExtra("static", false)) return
        if (fps >= SLOW_FPS) { slow = 0; return }
        if (++slow < 2) return
        done = true
        act.getSharedPreferences("buddy", Context.MODE_PRIVATE).edit().putBoolean("scene_anim", false).apply()
        Handler(Looper.getMainLooper()).post {
            act.toast(com.example.buddy.Tr.t(act, "Фон не тянет этот телефон — анимация выключена (Настройки → Стиль)", "The phone is too slow for the animated background, so it is off now (Settings → Style)", "Telefon animatsiyali fonni tortmayapti, o'chirildi (Sozlamalar → Uslub)"))
            act.restart()
        }
    }

    /** JavaScript that measures the page's frame rate for four seconds, starting three seconds after it runs, and calls [bridge].fps(n). */
    fun probe(bridge: String) =
        "setTimeout(function(){var n=0,t0=performance.now(),vis=true;function f(){if(document.visibilityState!=='visible')vis=false;n++;" +
            "if(performance.now()-t0<4000)requestAnimationFrame(f);else if(vis)$bridge.fps(Math.round(n/((performance.now()-t0)/1000)))}requestAnimationFrame(f)},3000)"
}
