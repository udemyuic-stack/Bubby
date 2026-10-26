package com.example.buddy.ui

import android.widget.LinearLayout

/** Temporary screens used while the real ones are being ported. */
abstract class Placeholder(act: UiActivity, private val name: String) : Screen(act) {
    override fun title() = name
    override fun render(c: LinearLayout) {
        header(c)
        c.add(ctx.card { add(ctx.text(name, T.Title)); add(ctx.text("...", T.Small), MATCH, WRAP, top = 6) })
    }
}

class SettingsScreen(act: UiActivity) : Placeholder(act, "Settings")
class MoreScreen(act: UiActivity) : Placeholder(act, "More")
