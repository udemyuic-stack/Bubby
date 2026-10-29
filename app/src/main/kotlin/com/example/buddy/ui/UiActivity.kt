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

private val TAB_NAMES = listOf("home", "tasks", "calendar", "settings", "more")

/** The whole app UI: five tabs and a stack of pushed screens, built with plain Views. */
class UiActivity : Activity() {
    private lateinit var host: FrameLayout
    private lateinit var shell: LinearLayout
    private lateinit var body: FrameLayout
    private lateinit var navFrame: FrameLayout
    private lateinit var bgHome: View
    private lateinit var bgSub: View
    private var scene: SceneView? = null
    private val tabs = ArrayList<Screen>()
    private var tab = 0
    private val stack = ArrayList<Pair<Screen, View>>()
    private var shownLang = ""
    private var insetTop = 0
    private var insetBottom = 0
    val bottomInset: Int get() = insetBottom

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        intent?.getStringExtra("theme")?.let { Backend.setStr(this, "theme", it) }   // CI screenshots: `--es theme green`
        P.init(this)
        shownLang = Tr.lang(this)
        cleanOldModel()
        window.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(P.surface))
        host = FrameLayout(this)
        bgHome = GlassBackground(this, true)
        bgSub = GlassBackground(this, false)
        host.addView(bgHome, FrameLayout.LayoutParams(MATCH, MATCH))
        host.addView(bgSub, FrameLayout.LayoutParams(MATCH, MATCH))
        if (Theme.cur.scene != null && WebCompat.ok()) {
            scene = try { SceneView(this).also { host.addView(it, FrameLayout.LayoutParams(MATCH, MATCH)) } } catch (_: Throwable) { null }   // no WebView on this phone: plain background
        }
        shell = col {}
        body = FrameLayout(this)
        shell.add(body, MATCH, 0, 1f)
        tabs += listOf(HomeScreen(this), TasksScreen(this), CalendarScreen(this), SettingsScreen(this), MoreScreen(this))
        for (s in tabs) s.inTab = true
        tabs[0].inTab = false   // the home page has no navigation bar: its petals are the navigation
        host.addView(shell, FrameLayout.LayoutParams(MATCH, MATCH))
        navFrame = FrameLayout(this)
        host.addView(navFrame, FrameLayout.LayoutParams(MATCH, WRAP, Gravity.BOTTOM))
        host.setOnApplyWindowInsetsListener { _, ins ->
            readInsets(ins)
            applyInsets()
            ins
        }
        setContentView(host)
        setBars(false)
        // `--es open <name>` (used by the CI screenshots) starts straight on that tab or screen
        val open = intent?.getStringExtra("open")
        val first = TAB_NAMES.indexOf(open)
        showTab(if (first >= 0) first else if (open != null) 1 else 0)
        if (open != null && first < 0) openByName(open)
    }

    /** Older versions could download a 2.6-3.7 GB on-device model; it is gone now, so free that space once. */
    private fun cleanOldModel() {
        val app = applicationContext
        val prefs = app.getSharedPreferences("buddy", MODE_PRIVATE)
        if (prefs.getBoolean("gemma_cleaned", false)) return
        Thread {
            try { java.io.File(app.filesDir, "models").deleteRecursively() } catch (_: Exception) { }
            prefs.edit().remove("provider").remove("gemma_model").remove("gemma_backend").putBoolean("gemma_cleaned", true).apply()
        }.start()
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        intent.getStringExtra("open")?.let { openByName(it) }
    }

    /** Opens a tab or a screen by name (used by the CI screenshots: `am start --es open settings`). */
    fun openByName(name: String) {
        while (stack.isNotEmpty()) { val (_, v) = stack.removeAt(stack.size - 1); host.removeView(v) }
        val i = TAB_NAMES.indexOf(name)
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
        navFrame.setPadding(dp(12), 0, dp(12), dp(18) + insetBottom)
        for (s in tabs) s.applyNavInset(insetBottom)
        for ((_, v) in stack) v.setPadding(0, insetTop, 0, insetBottom)
    }

    @Suppress("DEPRECATION")
    private fun setBars(darkIcons: Boolean = false) {
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT
        if (Build.VERSION.SDK_INT >= 30) {
            val light = if (!darkIcons) 0 else (android.view.WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS or android.view.WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS)
            try {
                window.decorView.windowInsetsController?.setSystemBarsAppearance(light,
                    android.view.WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS or android.view.WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS)
            } catch (_: Exception) { }
        } else {
            var f = View.SYSTEM_UI_FLAG_LAYOUT_STABLE or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
            if (darkIcons) {
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
        Triple(R.drawable.tg_home, Tr.t(this, "Главная", "Home", "Bosh sahifa"), 0),
        Triple(R.drawable.tg_check, Tr.t(this, "Задачи", "Tasks", "Vazifalar"), 1),
        Triple(R.drawable.tg_cal, Tr.t(this, "Календарь", "Calendar", "Kalendar"), 2),
        Triple(R.drawable.tg_gear, Tr.t(this, "Настройки", "Settings", "Sozlamalar"), 3),
        Triple(R.drawable.tg_apps, Tr.t(this, "Ещё", "More", "Yana"), 4)
    )

    /** The floating glass navigation: the selected tab is a bright petal. */
    private fun buildNav(): View {
        val bar = row(Gravity.CENTER_VERTICAL) {
            background = glass(GlassKind.Nav, 34f)
            setPadding(dp(6), 0, dp(6), 0)
        }
        for ((i, it) in tabLabels().withIndex()) {
            val selected = i == tab
            val item = FrameLayout(this).apply {
                if (selected) {
                    addView(View(this@UiActivity).apply { background = petalShape(GlassKind.Active, PETAL_RADII_NAV) },
                        FrameLayout.LayoutParams(dp(68), dp(54), Gravity.CENTER))
                }
                val inner = col {
                    gravity = Gravity.CENTER_HORIZONTAL
                    add(icon(it.first, if (selected) Glass.ON_ACTIVE else Glass.TEXT2, 22), WRAP, WRAP)
                    add(text(it.second, T.Label, if (selected) Glass.ON_ACTIVE else Glass.TEXT2).apply { setSingleLine(); textSize = 10f }, WRAP, WRAP, top = 2)
                }
                addView(inner, FrameLayout.LayoutParams(WRAP, WRAP, Gravity.CENTER))
                press { showTab(i) }
            }
            bar.add(item, 0, MATCH, 1f)
        }
        return bar
    }

    fun showTab(i: Int) {
        tab = i
        body.removeAllViews()
        body.addView(tabs[i].view(), FrameLayout.LayoutParams(MATCH, MATCH))
        setBars(i != 0 && !Glass.NIGHT)
        bgHome.visibility = if (i == 0) View.VISIBLE else View.GONE
        (tabs[0] as HomeScreen).let { if (i == 0) it.wakeWeb() else it.restWeb() }
        bgSub.visibility = if (i == 0) View.GONE else View.VISIBLE
        navFrame.removeAllViews()
        if (i != 0) navFrame.addView(buildNav(), FrameLayout.LayoutParams(MATCH, dp(68)))
        tabs[i].onShow()
        updateChrome()
    }

    /**
     * With a themed look the animated scene is behind every page, also the pushed ones (they are see-through then): it slides a little
     * from page to page like in the design; the themed home page carries its own scene.
     */
    private fun updateChrome() {
        val sc = scene ?: return
        val pushed = stack.isNotEmpty()
        sc.setScene(if (tab == 0 && !pushed && (tabs[0] as HomeScreen).themed) null else Theme.cur.scene)
        sc.slideTo(if (pushed) 5 else tab)
        shell.visibility = if (pushed) View.INVISIBLE else View.VISIBLE
        navFrame.visibility = if (pushed) View.INVISIBLE else View.VISIBLE
        bgHome.visibility = if (tab == 0 && !pushed) View.VISIBLE else View.GONE
        bgSub.visibility = if (tab == 0 && !pushed) View.GONE else View.VISIBLE
    }

    // ---- pushed screens ------------------------------------------------------------------------------------------

    fun push(s: Screen) {
        val page = pageFor(this, s, plain = scene != null)
        page.setPadding(0, insetTop, 0, insetBottom)
        if (scene != null) stack.lastOrNull()?.second?.visibility = View.INVISIBLE   // see-through pages: the one below must not show
        host.addView(page, FrameLayout.LayoutParams(MATCH, MATCH))
        stack += s to page
        page.alpha = 0f
        page.translationX = dp(40).toFloat()
        page.animate().alpha(1f).translationX(0f).setDuration(220).start()
        s.onShow()
        updateChrome()
    }

    fun pop() {
        if (stack.isEmpty()) return
        val (_, page) = stack.removeAt(stack.size - 1)
        page.animate().alpha(0f).translationX(dp(40).toFloat()).setDuration(160).withEndAction { host.removeView(page) }.start()
        stack.lastOrNull()?.second?.visibility = View.VISIBLE
        (stack.lastOrNull()?.first ?: tabs[tab]).onShow()
        updateChrome()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (stack.isNotEmpty()) pop() else super.onBackPressed()
    }

    override fun onPause() { super.onPause(); scene?.onAppPause(); (tabs[0] as HomeScreen).restWeb() }

    override fun onDestroy() { try { scene?.destroy() } catch (_: Throwable) { }; try { (tabs[0] as HomeScreen).destroyWeb() } catch (_: Throwable) { }; super.onDestroy() }

    override fun onResume() {
        super.onResume()
        scene?.onAppResume()
        if (tab == 0) (tabs[0] as HomeScreen).wakeWeb()
        // the language the user speaks changed (auto mode): draw everything in it
        if (Tr.lang(this) != shownLang) { recreate(); return }
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
