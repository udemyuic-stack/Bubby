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

    /** A floating action button (label, action) shown at the bottom right, or null. */
    open fun fab(): Pair<String, () -> Unit>? = null

    /** Page title (large on tabs, in the top bar on pushed screens). */
    abstract fun title(): String

    /** Horizontal padding of the page in dp (0 for screens that draw edge to edge). */
    protected open val padH: Int = 16

    /** Fills [c] with the screen's widgets. */
    abstract fun render(c: LinearLayout)

    /** Called every time the screen becomes visible again (return from settings, permission dialog...). */
    open fun onShow() {}
    open fun onPermissionResult() {}

    protected fun t(ru: String, en: String, uz: String): String = Tr.t(ctx, ru, en, uz)

    fun view(): View {
        root?.let { return it }
        val f = fab()
        content = ctx.col { setPadding(ctx.dp(padH), ctx.dp(if (padH == 0) 0 else 8), ctx.dp(padH), ctx.dp(if (f != null) 100 else 28)) }
        render(content)
        content.staggerChildren()
        val sv = ctx.scroll(content)
        scroll = sv
        val r: View = if (f == null) sv else FrameLayout(ctx).apply {
            addView(sv, FrameLayout.LayoutParams(MATCH, MATCH))
            val button = ctx.row(Gravity.CENTER) {
                background = ctx.gradient(intArrayOf(P.primary, P.mix(P.primary, P.rose, 0.35f)), 28f)
                elevation = ctx.dpf(6f)
                setPadding(ctx.dp(20), 0, ctx.dp(22), 0)
                add(ctx.icon(R.drawable.ic_add, P.onPrimary, 24), WRAP, WRAP, end = 8)
                add(ctx.text(f.first, T.Label, P.onPrimary).apply { textSize = 15f }, WRAP, WRAP)
                press { f.second() }
            }
            addView(button, FrameLayout.LayoutParams(WRAP, ctx.dp(56), Gravity.END or Gravity.BOTTOM).apply {
                setMargins(0, 0, ctx.dp(16), ctx.dp(20))
            })
        }
        root = r
        return r
    }

    fun refresh() {
        if (scroll == null) return
        val y = scroll!!.scrollY
        content.removeAllViews()
        render(content)
        scroll!!.post { scroll!!.scrollTo(0, y) }
    }

    /** Large page title used by the tab screens. */
    protected fun header(c: LinearLayout, subtitle: String? = null) {
        c.add(ctx.text(title(), T.Headline), MATCH, WRAP, top = 8)
        if (!subtitle.isNullOrEmpty()) c.add(ctx.text(subtitle, T.Small), MATCH, WRAP, top = 4, bottom = 8)
        else c.add(ctx.space(8), MATCH, ctx.dp(8))
    }
}

/** Wraps a pushed [Screen] with a top bar. */
fun pageFor(act: UiActivity, s: Screen): FrameLayout {
    val frame = FrameLayout(act)
    frame.setBackgroundColor(P.surface)
    val box = act.col()
    val bar = act.row {
        setPadding(act.dp(8), act.dp(6), act.dp(16), act.dp(6))
        add(act.row(Gravity.CENTER) {
            add(act.icon(R.drawable.ic_arrow_back, P.onSurface, 24), WRAP, WRAP)
            press { act.pop() }
        }, act.dp(48), act.dp(48))
        add(act.text(s.title(), T.Title).apply { textSize = 21f }, 0, WRAP, 1f, start = 4)
    }
    box.add(bar)
    box.add(s.view(), MATCH, 0, 1f)
    frame.addView(box, FrameLayout.LayoutParams(MATCH, MATCH))
    return frame
}
