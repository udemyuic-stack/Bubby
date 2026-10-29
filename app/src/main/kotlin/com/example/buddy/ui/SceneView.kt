package com.example.buddy.ui

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.view.MotionEvent
import android.view.View
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient

/**
 * The animated scene behind the app for the themed looks (space, Halloween, rainy forest, ocean, New Year): the SVG and CSS
 * of the design mockups, played by the system WebView from assets/scene. It is transparent, takes no touches and loads nothing
 * from the network. It rests (no frames) while the app is in the background or the scene is hidden.
 */
@SuppressLint("SetJavaScriptEnabled")
class SceneView(c: Context) : WebView(c) {
    private var loaded: String? = null
    private var wanted: String? = null
    private var active = true
    private var pendingSlide: String? = null
    private val client = object : WebViewClient() {
        override fun shouldOverrideUrlLoading(v: WebView, r: WebResourceRequest) = true   // nothing navigates away
        override fun onPageFinished(v: WebView, url: String?) { pendingSlide?.let { try { v.evaluateJavascript(it, null) } catch (_: Exception) { } } }
    }


    init {
        setBackgroundColor(Color.TRANSPARENT)
        overScrollMode = OVER_SCROLL_NEVER
        isVerticalScrollBarEnabled = false; isHorizontalScrollBarEnabled = false
        isClickable = false; isFocusable = false; isFocusableInTouchMode = false
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
        settings.javaScriptEnabled = true   // the rain and snow canvases
        settings.allowFileAccess = true; settings.allowContentAccess = false
        settings.blockNetworkLoads = true
        settings.cacheMode = android.webkit.WebSettings.LOAD_NO_CACHE
        setLayerType(LAYER_TYPE_HARDWARE, null)
        webViewClient = client
        visibility = View.GONE
    }

    // touches pass through to the app
    override fun onTouchEvent(e: MotionEvent) = false
    override fun dispatchTouchEvent(e: MotionEvent) = false

    /** Shows the scene [name] (assets/scene/<name>.html) or hides the view when null. */
    fun setScene(name: String?) {
        wanted = name
        if (name == null) { visibility = View.GONE; pauseWeb(); return }
        if (loaded != name) { loaded = name; loadUrl("file:///android_asset/scene/$name.html") }
        visibility = View.VISIBLE
        if (active) resumeWeb()
    }

    /** The scene drifts 8 px per page, as in the mockups. */
    fun slideTo(page: Int) {
        val js = "(function(){var s=document.querySelector('.scene');if(s&&s.animate)s.animate({translate:['" + (-page * 8) + "px 0']},{duration:900,easing:'cubic-bezier(.22,1,.36,1)',fill:'forwards'})})()"
        try { evaluateJavascript(js, null) } catch (_: Exception) { }
        pendingSlide = js
    }

    fun onAppResume() { active = true; if (wanted != null) resumeWeb() }
    fun onAppPause() { active = false; pauseWeb() }

    private fun resumeWeb() { try { onResume(); resumeTimers() } catch (_: Exception) { } }
    private fun pauseWeb() { try { onPause(); pauseTimers() } catch (_: Exception) { } }
}

/** The mockup pages use modern CSS (individual transform properties, inset): they need a WebView of Chrome 105 or newer. */
object WebCompat {
    private var cached: Boolean? = null
    fun ok(): Boolean {
        cached?.let { return it }
        val r = try {
            if (android.os.Build.VERSION.SDK_INT < 26) false
            else {
                val v = android.webkit.WebView.getCurrentWebViewPackage()?.versionName ?: ""
                (v.substringBefore('.').toIntOrNull() ?: 0) >= 105
            }
        } catch (_: Throwable) { false }
        cached = r
        return r
    }
}
