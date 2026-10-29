package com.example.buddy.ui

import android.content.Context
import android.graphics.Canvas
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View

/**
 * The animated background of a look, drawn natively with Canvas (no web page, no JavaScript): one subclass per look.
 * Rules for a scene: everything it needs is created in [onSize] or at construction, nothing is allocated in [draw]
 * (reuse Paint, Path, RectF, FloatArray), and every moving thing is a pure function of the time [t].
 */
abstract class ThemeScene {
    /** False while a look has no native scene yet: the old web page is used for it. */
    open val ready: Boolean = false

    /** The view was measured: [w] x [h] pixels, [density] pixels per dp. Build gradients and star fields here. */
    open fun onSize(w: Float, h: Float, density: Float) {}

    /**
     * One frame. [t] is the scene time in seconds (stops while the app is in the background), [dt] the seconds since the
     * last frame, [page] the sideways drift: 0 on the home page, growing smoothly by 1 for every tab further right (the
     * mockups slide the scene 8 dp per page: shift far layers by `-page * 8 * density`, near layers a little more).
     */
    abstract fun draw(cv: Canvas, w: Float, h: Float, t: Float, dt: Float, page: Float)
}

/** The scenes by the name a look gives them (Theme.cur.scene). */
object ThemeScenes {
    fun create(id: String?): ThemeScene? = when (id) {
        "glass" -> GlassScene()
        "night" -> NightScene()
        "green" -> GreenScene()
        "blue" -> BlueScene()
        "space" -> SpaceScene()
        "halloween" -> HalloweenScene()
        "forest" -> ForestScene()
        "ocean" -> OceanScene()
        "newyear" -> NewYearScene()
        "ice" -> IceScene()
        else -> null
    }

    private val cache = HashMap<String, Boolean>()
    /** Does this look have a finished native scene? */
    fun ready(id: String?): Boolean = id != null && cache.getOrPut(id) { create(id)?.ready == true }
}

/** What the activity needs from the thing behind the pages, whether it is the native view or the old web view. */
interface SceneHost {
    val view: View
    fun setScene(name: String?)
    fun slideTo(page: Int)
    fun onAppResume()
    fun onAppPause()
    fun release()
}

/** Draws a [ThemeScene] behind everything. Touches go through. The loop runs only while the view is visible and the app in front. */
class SceneCanvasView(c: Context) : View(c), SceneHost {
    private var scene: ThemeScene? = null
    private var name: String? = null
    private var active = true
    private var t = 0f
    private var last = 0L
    private var page = 0f
    private var targetPage = 0f
    private var frames = 0
    private var measureStart = 0L

    init { isClickable = false; isFocusable = false; importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS }

    override val view: View get() = this
    override fun onTouchEvent(e: MotionEvent) = false
    override fun dispatchTouchEvent(e: MotionEvent) = false

    override fun setScene(name: String?) {
        if (name == null) { visibility = GONE; return }
        if (this.name != name) {
            this.name = name
            scene = ThemeScenes.create(name)
            if (width > 0) scene?.onSize(width.toFloat(), height.toFloat(), resources.displayMetrics.density)
            measureStart = 0L; frames = 0
        }
        visibility = VISIBLE
        invalidate()
    }

    override fun slideTo(page: Int) { targetPage = page.toFloat(); invalidate() }
    override fun onAppResume() { active = true; last = 0L; invalidate() }
    override fun onAppPause() { active = false }
    override fun release() { scene = null }

    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) { scene?.onSize(w.toFloat(), h.toFloat(), resources.displayMetrics.density) }

    override fun onDraw(cv: Canvas) {
        val s = scene ?: return
        val now = SystemClock.uptimeMillis()
        val dt = if (last == 0L) 0.016f else ((now - last) / 1000f).coerceAtMost(0.05f)
        last = now
        t += dt
        page += (targetPage - page) * (1f - Math.exp((-dt * 4.5f).toDouble()).toFloat())   // eases toward the page like the mockups' slide
        s.draw(cv, width.toFloat(), height.toFloat(), t, dt, page)
        // a phone too slow for this: the guard (Perf) switches the animated background off
        if (measureStart == 0L) { measureStart = now; frames = 0 } else frames++
        if (now - measureStart > 4000 && active) { Perf.report(context, (frames * 1000L / (now - measureStart)).toInt(), "native scene"); measureStart = now; frames = 0 }
        // 30 frames a second are plenty for these slow drifts and half the work of 60: the next frame waits out the rest of its slot
        if (active && visibility == VISIBLE) postInvalidateDelayed((FRAME_MS - (SystemClock.uptimeMillis() - now)).coerceAtLeast(0L))
    }

    private companion object {
        /** Not 33: asking a little early lands on the second vsync (33 ms) instead of slipping to the third (50 ms). */
        const val FRAME_MS = 26L
    }
}
