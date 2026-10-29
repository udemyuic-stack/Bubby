package com.example.buddy.ui

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient

/**
 * The home page of the themed looks (space, Halloween, rainy forest, ocean, New Year): the page of the design mockup,
 * running as it is in the system WebView (assets/home), so the scene, the mascot and every animation and surprise of the
 * look are the original ones. The island on top and the cards below are native; the petals and the Start button
 * call back into the app through [Host]. Nothing is loaded from the network.
 */
@SuppressLint("SetJavaScriptEnabled")
class HomeWebView(c: Context, private val host: Host) : WebView(c) {
    interface Host {
        fun nav(name: String)
        fun ask(text: String)
        fun toggle()
    }

    private var ready = false
    private val queue = ArrayList<String>()

    private inner class Bridge {
        @JavascriptInterface fun nav(n: String) { if (n in setOf("tasks", "calendar", "settings", "more")) post { host.nav(n) } }
        @JavascriptInterface fun ask(t: String) { if (t.length <= 200) post { host.ask(t) } }
        @JavascriptInterface fun toggle() { post { host.toggle() } }
        @JavascriptInterface fun ready() { post { ready = true; queue.forEach { evaluateJavascript(it, null) }; queue.clear() } }
    }

    init {
        setBackgroundColor(Color.TRANSPARENT)
        overScrollMode = OVER_SCROLL_NEVER
        isVerticalScrollBarEnabled = false; isHorizontalScrollBarEnabled = false
        settings.javaScriptEnabled = true
        settings.allowFileAccess = true; settings.allowContentAccess = false
        settings.blockNetworkLoads = true
        settings.mediaPlaybackRequiresUserGesture = true
        setLayerType(LAYER_TYPE_HARDWARE, null)
        addJavascriptInterface(Bridge(), "BubbyNative")
        webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(v: WebView, r: WebResourceRequest) = true
        }
    }

    fun load(theme: String) { loadUrl("file:///android_asset/home/$theme.html") }

    /** Runs [js] in the page, as soon as the page is ready. */
    fun run(js: String) { if (ready) evaluateJavascript(js, null) else queue.add(js) }

    fun rest() { try { onPause(); pauseTimers() } catch (_: Exception) { } }
    fun wake() { try { onResume(); resumeTimers() } catch (_: Exception) { } }
}
