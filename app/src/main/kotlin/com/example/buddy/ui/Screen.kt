package com.example.buddy.ui

import android.content.Context
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import com.example.buddy.R
import com.example.buddy.Tr

/**
 * One screen of the app. [render] fills a vertical column inside a scroll view; call [refresh] to draw it again after
 * the data changed. Tab screens show a large title, pushed screens get a top bar with a back arrow.
 */
abstract class Screen(val act: UiActivity) {
    val ctx: Context get() = act
    protected lateinit var content: LinearLayout
    private var scroll: ScrollView? = null
    private var root: View? = null
    /** True from building the page until the main thread is next idle: nothing can have changed in between. */
    private var fresh = false

    /** A floating action button (label, action) shown at the bottom right, or null. */
    open fun fab(): Pair<String, () -> Unit>? = null

    /** Page title (large on tabs, in the top bar on pushed screens). */
    abstract fun title(): String

    /** Horizontal padding of the page in dp (0 for screens that draw edge to edge). */
    protected open val padH: Int = 16

    /** Set by the activity for the five tab pages, which sit above the floating glass navigation. */
    var inTab = false

    /** Fills [c] with the screen's widgets. */
    abstract fun render(c: LinearLayout)

    /** Called every time the screen becomes visible again (return from settings, permission dialog...). */
    open fun onShow() {}
    open fun onPermissionResult() {}

    protected fun t(ru: String, en: String, uz: String): String = Tr.t(ctx, ru, en, uz)

    fun view(): View {
        root?.let { return it }
        val f = fab()
        content = ctx.col { setPadding(ctx.dp(padH), ctx.dp(if (padH == 0) 0 else 8), ctx.dp(padH), ctx.dp((if (f != null) 100 else 28) + (if (inTab) 90 else 0))) }
        render(content)
        content.staggerChildren()
        fresh = true
        content.post { fresh = false }
        val sv = ctx.scroll(content)
        scroll = sv
        val r: View = if (f == null) sv else FrameLayout(ctx).apply {
            addView(sv, FrameLayout.LayoutParams(MATCH, MATCH))
            val button = ctx.row(Gravity.CENTER) {
                background = ctx.glass(GlassKind.Active, 27f)
                setPadding(ctx.dp(22), 0, ctx.dp(24), 0)
                add(ctx.icon(R.drawable.ic_add, Glass.TEXT, 22), WRAP, WRAP, end = 8)
                add(ctx.text(f.first, T.Label, Glass.TEXT).apply { textSize = 15f }, WRAP, WRAP)
                press { f.second() }
            }
            addView(button, FrameLayout.LayoutParams(WRAP, ctx.dp(54), Gravity.END or Gravity.BOTTOM).apply {
                setMargins(0, 0, ctx.dp(20), ctx.dp(if (inTab) 102 else 20))
            })
        }
        root = r
        return r
    }

    /**
     * For [onShow] of pages that only redraw there: skips the redraw when the page was built a moment ago in the same
     * main-thread turn (opening a tab, then onResume, used to build the start page three times and cut its fade-in short).
     */
    protected fun reshow() { if (!fresh) refresh() }

    fun refresh() {
        if (scroll == null) return
        val y = scroll!!.scrollY
        content.removeAllViews()
        render(content)
        scroll!!.post { scroll!!.scrollTo(0, y) }
    }

    /** Large page title used by the tab screens. */
    protected fun header(c: LinearLayout, subtitle: String? = null) {
        c.add(ctx.text(title(), T.Headline), MATCH, WRAP, top = 28, start = 8)
        if (!subtitle.isNullOrEmpty()) c.add(ctx.text(subtitle, T.Small).apply { textSize = 14f }, MATCH, WRAP, top = 4, bottom = 14, start = 8)
        else c.add(ctx.space(14), MATCH, ctx.dp(14))
    }
}

/** Wraps a pushed [Screen] with a top bar. */
fun pageFor(act: UiActivity, s: Screen): FrameLayout {
    val frame = FrameLayout(act)
    frame.addView(GlassBackground(act, false), FrameLayout.LayoutParams(MATCH, MATCH))
    val box = act.col()
    val bar = act.row {
        setPadding(act.dp(10), act.dp(8), act.dp(16), act.dp(6))
        add(act.row(Gravity.CENTER) {
            background = act.glass(GlassKind.Base, 22f)
            add(act.icon(R.drawable.ic_arrow_back, Glass.TEXT, 22), WRAP, WRAP)
            press { act.pop() }
        }, act.dp(44), act.dp(44))
        add(act.text(s.title(), T.Title).apply { textSize = 22f; typeface = glassFont() }, 0, WRAP, 1f, start = 12)
    }
    box.add(bar)
    box.add(s.view(), MATCH, 0, 1f)
    frame.addView(box, FrameLayout.LayoutParams(MATCH, MATCH))
    return frame
}
