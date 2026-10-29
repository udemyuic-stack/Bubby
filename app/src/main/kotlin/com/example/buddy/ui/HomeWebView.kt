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
        /** The loading splash of the page started (true) or ended (false). */
        fun splash(show: Boolean)
    }

    private var ready = false
    private val queue = ArrayList<String>()

    private inner class Bridge {
        @JavascriptInterface fun nav(n: String) { if (n in setOf("tasks", "calendar", "settings", "more")) post { host.nav(n) } }
        @JavascriptInterface fun ask(t: String) { if (t.length <= 200) post { host.ask(t) } }
        @JavascriptInterface fun toggle() { post { host.toggle() } }
        @JavascriptInterface fun splash(show: Boolean) { android.util.Log.i("com.example.buddy", "HomeWeb: page says splash show=$show"); post { host.splash(show) } }
        @JavascriptInterface fun fps(n: Int) { post { Perf.report(context, n, "home page") } }
        @JavascriptInterface fun probe(s: String) { if (s.length < 80) android.util.Log.i("com.example.buddy", "HomeWeb: $s") }
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
            // one look at the page a few seconds in (CI log: did the splash end?)
            override fun onPageFinished(v: WebView, url: String) {
                v.evaluateJavascript("setTimeout(function(){BubbyNative.probe('after 3 s splash='+(document.querySelector('.splash')?'on':'off'))},3000)", null)
                v.evaluateJavascript(Perf.probe("BubbyNative"), null)
            }
            // the web view's process was killed (low memory): never take the app down with it, open the native cards instead
            override fun onRenderProcessGone(v: WebView, d: android.webkit.RenderProcessGoneDetail): Boolean {
                android.util.Log.w("com.example.buddy", "HomeWeb: render process gone (crashed=${d.didCrash()})")
                post { host.splash(false) }
                return true
            }
        }
        webChromeClient = object : android.webkit.WebChromeClient() {
            override fun onConsoleMessage(m: android.webkit.ConsoleMessage): Boolean {
                if (m.messageLevel() == android.webkit.ConsoleMessage.MessageLevel.ERROR) android.util.Log.w("com.example.buddy", "HomeWeb console: ${m.message().take(120)} @${m.lineNumber()}")
                return true
            }
        }
    }

    fun load(theme: String, splash: Boolean) { loadUrl("file:///android_asset/home/$theme.html" + if (splash) "" else "?nosplash=1") }

    /** Runs [js] in the page, as soon as the page is ready. */
    fun run(js: String) { if (ready) evaluateJavascript(js, null) else queue.add(js) }

    fun rest() { try { onPause() } catch (_: Exception) { } }
    fun wake() { try { onResume() } catch (_: Exception) { } }
}
