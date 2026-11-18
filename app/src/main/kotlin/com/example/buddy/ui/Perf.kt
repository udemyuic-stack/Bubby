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
    private const val LITE_SLOW_FPS = 14
    private var slow = 0
    private var done = false

    /** [fps]: frames per second a page just measured while it was visible (0 means it was hidden: ignored). */
    fun report(ctx: Context, fps: Int, who: String) {
        android.util.Log.i("com.example.buddy", "Perf: $who $fps fps")
        val act = ctx as? UiActivity ?: return
        if (done || fps <= 0 || act.intent.getBooleanExtra("nosetup", false) || act.intent.getBooleanExtra("static", false)) return
        val prefs = act.getSharedPreferences("buddy", Context.MODE_PRIVATE)
        val lite = prefs.getBoolean("scene_lite", false)
        if (fps >= (if (lite) LITE_SLOW_FPS else SLOW_FPS)) { slow = 0; return }
        if (++slow < 3) return
        slow = 0
        if (!lite) {
            prefs.edit().putBoolean("scene_lite", true).apply()
            Handler(Looper.getMainLooper()).post { act.restart() }
            return
        }
        done = true
        prefs.edit().putBoolean("scene_anim", false).putBoolean("scene_anim_auto_off", true).apply()
        Handler(Looper.getMainLooper()).post {
            act.toast(com.example.buddy.Tr.t(act, "Фон не тянет этот телефон — анимация выключена (Настройки → Стиль)", "The phone is too slow for the animated background, so it is off now (Settings → Style)", "Telefon animatsiyali fonni tortmayapti, o'chirildi (Sozlamalar → Uslub)"))
            act.restart()
        }
    }

    fun lite(ctx: Context): Boolean = ctx.getSharedPreferences("buddy", Context.MODE_PRIVATE).getBoolean("scene_lite", false)

    fun resetAfterUpdate(ctx: Context) {
        val prefs = ctx.getSharedPreferences("buddy", Context.MODE_PRIVATE)
        val ver = try { ctx.packageManager.getPackageInfo(ctx.packageName, 0).lastUpdateTime } catch (_: Exception) { return }
        if (prefs.getLong("scene_checked_ver", -1L) == ver) return
        val e = prefs.edit().putLong("scene_checked_ver", ver).putBoolean("scene_lite", false)
        if (prefs.getBoolean("scene_anim_auto_off", false)) e.putBoolean("scene_anim", true).putBoolean("scene_anim_auto_off", false)
        e.apply()
    }

    /** JavaScript that measures the page's frame rate for four seconds, starting three seconds after it runs, and calls [bridge].fps(n). */
    fun probe(bridge: String) =
        "setTimeout(function(){var n=0,t0=performance.now(),vis=true;function f(){if(document.visibilityState!=='visible')vis=false;n++;" +
            "if(performance.now()-t0<4000)requestAnimationFrame(f);else if(vis)$bridge.fps(Math.round(n/((performance.now()-t0)/1000)))}requestAnimationFrame(f)},3000)"
}
