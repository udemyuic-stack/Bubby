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

    /** Page title (large on tabs, in the top bar on pushed screens). */
    abstract fun title(): String

    /** Fills [c] with the screen's widgets. */
    abstract fun render(c: LinearLayout)

    /** Called every time the screen becomes visible again (return from settings, permission dialog...). */
    open fun onShow() {}
    open fun onPermissionResult() {}

    protected fun t(ru: String, en: String, uz: String): String = Tr.t(ctx, ru, en, uz)

    fun view(): View {
        scroll?.let { return it }
        content = ctx.col { setPadding(ctx.dp(16), ctx.dp(8), ctx.dp(16), ctx.dp(28)) }
        render(content)
        return ctx.scroll(content).also { scroll = it }
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
        else c.add(ctx.space(8))
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
