package com.example.buddy.ui

import android.app.Activity
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.WindowInsets
import android.widget.FrameLayout
import android.widget.LinearLayout
import com.example.buddy.R
import com.example.buddy.Tr
import java.util.Calendar

/** The whole app UI: five tabs and a stack of pushed screens, built with plain Views. */
class UiActivity : Activity() {
    private lateinit var host: FrameLayout
    private lateinit var shell: LinearLayout
    private lateinit var body: FrameLayout
    private lateinit var tabBar: LinearLayout
    private val tabs = ArrayList<Screen>()
    private var tab = 0
    private val stack = ArrayList<Pair<Screen, View>>()
    private var insetTop = 0
    private var insetBottom = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        P.init(this)
        window.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(P.surface))
        setBars()
        host = FrameLayout(this)
        shell = col { setBackgroundColor(P.surface) }
        body = FrameLayout(this)
        shell.add(body, MATCH, 0, 1f)
        tabs += listOf(HomeScreen(this), TasksScreen(this), CalendarScreen(this), SettingsScreen(this), MoreScreen(this))
        tabBar = buildTabBar()
        shell.add(tabBar)
        host.addView(shell, FrameLayout.LayoutParams(MATCH, MATCH))
        host.setOnApplyWindowInsetsListener { _, ins ->
            readInsets(ins)
            applyInsets()
            ins
        }
        setContentView(host)
        showTab(0)
        intent?.getStringExtra("open")?.let { openByName(it) }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        intent.getStringExtra("open")?.let { openByName(it) }
    }

    /** Opens a tab or a screen by name (used by the CI screenshots: `am start --es open settings`). */
    fun openByName(name: String) {
        while (stack.isNotEmpty()) { val (_, v) = stack.removeAt(stack.size - 1); host.removeView(v) }
        val tabNames = listOf("home", "tasks", "calendar", "settings", "more")
        val i = tabNames.indexOf(name)
        if (i >= 0) { showTab(i); return }
        Screens.create(name, this)?.let { push(it) }
    }

    @Suppress("DEPRECATION")
    private fun readInsets(ins: WindowInsets) {
        if (Build.VERSION.SDK_INT >= 30) {
            val b = ins.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
            insetTop = b.top; insetBottom = b.bottom
        } else {
            insetTop = ins.systemWindowInsetTop; insetBottom = ins.systemWindowInsetBottom
        }
    }

    private fun applyInsets() {
        shell.setPadding(0, insetTop, 0, 0)
        tabBar.setPadding(0, 0, 0, insetBottom)
        for ((_, v) in stack) v.setPadding(0, insetTop, 0, insetBottom)
    }

    @Suppress("DEPRECATION")
    private fun setBars() {
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT
        if (Build.VERSION.SDK_INT >= 30) {
            val light = if (P.dark) 0 else (android.view.WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS or android.view.WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS)
            window.insetsController?.setSystemBarsAppearance(light,
                android.view.WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS or android.view.WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS)
        } else {
            var f = View.SYSTEM_UI_FLAG_LAYOUT_STABLE or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
            if (!P.dark) {
                f = f or View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
                if (Build.VERSION.SDK_INT >= 26) f = f or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
            }
            window.decorView.systemUiVisibility = f
        }
        window.setDecorFitsSystemWindowsCompat()
    }

    @Suppress("DEPRECATION")
    private fun android.view.Window.setDecorFitsSystemWindowsCompat() {
        if (Build.VERSION.SDK_INT >= 30) setDecorFitsSystemWindows(false)
    }

    // ---- tabs ----------------------------------------------------------------------------------------------------

    private fun tabLabels() = listOf(
        Triple(R.drawable.ic_home, Tr.t(this, "Главная", "Home", "Bosh sahifa"), 0),
        Triple(R.drawable.ic_check_circle, Tr.t(this, "Задачи", "Tasks", "Vazifalar"), 1),
        Triple(R.drawable.ic_calendar, Tr.t(this, "Календарь", "Calendar", "Kalendar"), 2),
        Triple(R.drawable.ic_settings, Tr.t(this, "Настройки", "Settings", "Sozlamalar"), 3),
        Triple(R.drawable.ic_apps, Tr.t(this, "Ещё", "More", "Yana"), 4)
    )

    private fun buildTabBar(): LinearLayout {
        val bar = row(Gravity.CENTER_VERTICAL) {
            background = rounded(P.low, 0f)
            setPadding(0, dp(8), 0, dp(8))
        }
        val line = View(this).apply { setBackgroundColor(P.outlineVariant) }
        val box = col {}
        box.add(line, MATCH, 1)
        box.add(bar)
        val cal = Calendar.getInstance()
        val pill = P.accentContainer(cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE))
        val onPill = P.onAccentContainer(cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE))
        for ((i, it) in tabLabels().withIndex()) {
            val selected = i == tab
            val item = col {
                gravity = Gravity.CENTER_HORIZONTAL
                add(row(Gravity.CENTER) {
                    background = if (selected) rounded(pill, 16f) else null
                    add(icon(it.first, if (selected) onPill else P.onSurfaceVariant, 24), WRAP, WRAP)
                }, dp(64), dp(32))
                add(text(it.second, T.Label, if (selected) P.onSurface else P.onSurfaceVariant).apply {
                    setSingleLine(); textSize = 11.5f
                    if (!selected) setTypeface(null, android.graphics.Typeface.NORMAL)
                }, WRAP, WRAP, top = 4)
                press { showTab(i) }
            }
            bar.add(item, 0, WRAP, 1f)
        }
        // the bar is rebuilt on every tab change, so wrap it in a stable parent
        return LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; addView(box); setBackgroundColor(P.low) }
    }

    fun showTab(i: Int) {
        tab = i
        body.removeAllViews()
        body.addView(tabs[i].view(), FrameLayout.LayoutParams(MATCH, MATCH))
        val idx = shell.indexOfChild(tabBar)
        shell.removeView(tabBar)
        tabBar = buildTabBar()
        shell.addView(tabBar, idx, LinearLayout.LayoutParams(MATCH, WRAP))
        tabBar.setPadding(0, 0, 0, insetBottom)
        tabs[i].onShow()
    }

    // ---- pushed screens ------------------------------------------------------------------------------------------

    fun push(s: Screen) {
        val page = pageFor(this, s)
        page.setPadding(0, insetTop, 0, insetBottom)
        host.addView(page, FrameLayout.LayoutParams(MATCH, MATCH))
        stack += s to page
        page.alpha = 0f
        page.translationX = dp(40).toFloat()
        page.animate().alpha(1f).translationX(0f).setDuration(220).start()
        s.onShow()
    }

    fun pop() {
        if (stack.isEmpty()) return
        val (_, page) = stack.removeAt(stack.size - 1)
        page.animate().alpha(0f).translationX(dp(40).toFloat()).setDuration(160).withEndAction { host.removeView(page) }.start()
        (stack.lastOrNull()?.first ?: tabs[tab]).onShow()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (stack.isNotEmpty()) pop() else super.onBackPressed()
    }

    override fun onResume() {
        super.onResume()
        (stack.lastOrNull()?.first ?: tabs[tab]).onShow()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        (stack.lastOrNull()?.first ?: tabs[tab]).onPermissionResult()
    }

    /** Pushes the named screen, or says it is not there yet. */
    fun open(name: String) {
        val s = Screens.create(name, this)
        if (s != null) push(s) else toast("...")
    }

    /** The language changed: draw everything again. */
    fun restart() = recreate()
}
