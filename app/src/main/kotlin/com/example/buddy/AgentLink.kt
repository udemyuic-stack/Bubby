package com.example.buddy

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import org.json.JSONException
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketTimeoutException
import java.security.MessageDigest
import java.security.SecureRandom
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import java.util.concurrent.ConcurrentLinkedQueue
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocket
import javax.net.ssl.X509TrustManager

data class AgentSession(val pillId: String, val agent: String, val state: String, val statusText: String, val stepIndex: Int, val stepCount: Int)
/** What the computer reports about limits and work done (every field may be missing). `used` is 0..1, times are epoch ms. */
data class Usage(
    val fiveHour: Float?, val fiveHourResets: Long, val sevenDay: Float?, val sevenDayResets: Long,
    val doneToday: Int?, val tokensToday: Long?, val plan: String?, val updatedAt: Long
)

data class Approval(val pillId: String, val fingerprint: String, val tool: String, val command: String, val createdAt: Long, val truncated: Boolean)

/** Accepts only the certificate whose SHA-256 was in the pairing link; no CA, no hostname check. */
@SuppressLint("CustomX509TrustManager")
private class PinnedTrust(fp: String) : X509TrustManager {
    private val want = fp.lowercase().toByteArray()

    override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {
        val leaf = chain?.firstOrNull() ?: throw CertificateException("empty chain")
        val got = MessageDigest.getInstance("SHA-256").digest(leaf.encoded).joinToString("") { "%02x".format(it) }
        if (!MessageDigest.isEqual(got.toByteArray(), want)) throw CertificateException("certificate is not the paired one")
    }

    override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {
        throw CertificateException("no client certificates")
    }

    override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
}

/**
 * Phone side of coucou's link protocol v1 (docs/ANDROID_LINK.md, MIT), written from the spec.
 * start/stop/decide are for the main thread; all socket work runs on one background thread,
 * every Listener callback is posted to the main Looper.
 */
class AgentLink(ctx: Context, val listener: Listener) {
    interface Listener {
        fun onStatus(connected: Boolean, desktop: String?)
        fun onSessions(list: List<AgentSession>)
        fun onApproval(a: Approval)
        fun onApprovalResolved(fp: String)
        fun onUsage(u: Usage) {}
    }

    private enum class Next { GO, RETRY, FATAL }

    private val app = ctx.applicationContext
    private val main = Handler(Looper.getMainLooper())
    private val out = ConcurrentLinkedQueue<String>()
    private val pending = HashMap<String, Runnable>()
    private val decided = HashSet<String>()
    private var thread: Thread? = null

    @Volatile private var gen = 0
    @Volatile private var connected = false
    @Volatile private var raw: Socket? = null

    fun start() {
        if (thread != null) return
        val link = try { PairingStore(app).load() } catch (e: Exception) { null }
        if (link == null) {
            listener.onStatus(false, null)
            return
        }
        val g = ++gen
        thread = Thread({ loop(link, g) }, "agent-link").apply { isDaemon = true; start() }
    }

    fun stop() {
        gen++
        thread?.interrupt()
        thread = null
        connected = false
        out.clear()
        closeAsync(raw)
        raw = null
        main.removeCallbacksAndMessages(null)
        pending.clear()
        decided.clear()
    }

    fun decide(fingerprint: String, allow: Boolean) {
        if (!connected || fingerprint in decided) return
        val timer = pending.remove(fingerprint) ?: return
        main.removeCallbacks(timer)
        decided.add(fingerprint)
        out.add(
            JSONObject().put("type", "decision").put("fingerprint", fingerprint)
                .put("decision", if (allow) "allow" else "deny").toString()
        )
    }

    private fun closeAsync(s: Socket?) {
        if (s == null) return
        Thread { try { s.close() } catch (e: Exception) { } }.start()
    }

    private fun post(g: Int, f: () -> Unit) {
        main.post { if (g == gen) f() }
    }

    private fun loop(link: ParsedLink, g: Int) {
        var backoff = 1000L
        while (g == gen) {
            var welcomed = false
            val next = try {
                session(link, g) { welcomed = true }
            } catch (e: Exception) {
                Next.RETRY
            }
            if (g != gen) return
            if (welcomed) {
                post(g) { dropConnection(); listener.onStatus(false, null) }
                backoff = 1000L
            }
            if (next == Next.FATAL) {
                post(g) { dropConnection(); thread = null; listener.onStatus(false, null) }
                return
            }
            try {
                Thread.sleep(backoff)
            } catch (e: InterruptedException) {
                return
            }
            backoff = minOf(backoff * 2, 30000L)
        }
    }

    private fun dropConnection() {
        connected = false
        out.clear()
        for ((fp, timer) in pending.toMap()) {
            main.removeCallbacks(timer)
            pending.remove(fp)
            listener.onApprovalResolved(fp)
        }
    }

    private fun session(link: ParsedLink, g: Int, onWelcome: () -> Unit): Next {
        val s = Socket()
        raw = s
        try {
            if (g != gen) return Next.RETRY
            s.connect(InetSocketAddress(link.host, link.port), 5000)
            s.tcpNoDelay = true
            val ctx = SSLContext.getInstance("TLS")
            ctx.init(null, arrayOf(PinnedTrust(link.fp)), SecureRandom())
            val ssl = ctx.socketFactory.createSocket(s, link.host, link.port, true) as SSLSocket
            ssl.useClientMode = true
            ssl.enabledProtocols = ssl.supportedProtocols.filter { it == "TLSv1.2" || it == "TLSv1.3" }.toTypedArray()
            ssl.soTimeout = 8000
            ssl.startHandshake()
            ssl.soTimeout = TICK_MS
            val input = BufferedInputStream(ssl.inputStream)
            val os = ssl.outputStream
            out.clear()
            send(os, JSONObject().put("type", "hello").put("v", 1).put("token", link.token)
                .put("device", Build.MODEL ?: "Android").put("caps", org.json.JSONArray()).toString())

            var welcomed = false
            val began = System.currentTimeMillis()
            var lastRx = began
            var lastPing = began
            val buf = ByteArrayOutputStream()

            fun housekeeping(): Boolean {
                val now = System.currentTimeMillis()
                if (!welcomed) return now - began <= 10000
                while (true) send(os, out.poll() ?: break)
                if (now - lastPing >= PING_MS) {
                    send(os, "{\"type\":\"ping\"}")
                    lastPing = now
                }
                return now - lastRx <= 70000
            }

            while (g == gen) {
                val b = try {
                    input.read()
                } catch (e: SocketTimeoutException) {
                    if (!housekeeping()) return Next.RETRY
                    continue
                }
                if (b < 0) return Next.RETRY
                lastRx = System.currentTimeMillis()
                if (b != '\n'.code) {
                    buf.write(b)
                    if (buf.size() > MAX_LINE) return Next.RETRY
                    continue
                }
                val line = buf.toString("UTF-8").trim()
                buf.reset()
                if (line.isNotEmpty()) {
                    val r = handle(line, g, welcomed) { welcomed = true; onWelcome() }
                    if (r != Next.GO) return r
                }
                if (!housekeeping()) return Next.RETRY
            }
            return Next.RETRY
        } finally {
            closeAsync(s)
            if (raw === s) raw = null
        }
    }

    private fun send(os: OutputStream, line: String) {
        os.write((line + "\n").toByteArray())
        os.flush()
    }

    private fun str(j: JSONObject, k: String): String = if (j.isNull(k)) "" else j.optString(k, "")

    private fun handle(line: String, g: Int, welcomed: Boolean, onWelcome: () -> Unit): Next {
        val j = try { JSONObject(line) } catch (e: JSONException) { return Next.GO }
        when (str(j, "type")) {
            "welcome" -> {
                if (j.optInt("v", -1) != 1) return Next.FATAL
                val desktop = str(j, "desktop").take(64).ifEmpty { null }
                onWelcome()
                post(g) { connected = true; listener.onStatus(true, desktop) }
            }
            "error" -> if (str(j, "code") == "auth") return Next.FATAL
            "sessions" -> if (welcomed) {
                val arr = j.optJSONArray("sessions") ?: return Next.GO
                val list = ArrayList<AgentSession>()
                for (i in 0 until minOf(arr.length(), 50)) {
                    val o = arr.optJSONObject(i) ?: continue
                    val pill = str(o, "pillId").take(64)
                    if (pill.isEmpty()) continue
                    val state = str(o, "state").let { if (it in STATES) it else "idle" }
                    list.add(
                        AgentSession(
                            pill, str(o, "agent").take(64), state, str(o, "statusText").take(200),
                            o.optInt("stepIndex", 0).coerceAtLeast(0), o.optInt("stepCount", 0).coerceAtLeast(0)
                        )
                    )
                }
                post(g) { listener.onSessions(list) }
            }
            "approval" -> if (welcomed) {
                val fp = str(j, "fingerprint").take(128)
                if (fp.isEmpty()) return Next.GO
                val cmd = str(j, "command")
                val cut = cmd.length > 500
                val a = Approval(
                    str(j, "pillId").take(64), fp, str(j, "tool").take(64), cmd.take(500),
                    j.optLong("createdAt", 0L).let { if (it <= 0L) System.currentTimeMillis() else it },
                    cut || j.optBoolean("truncated", false)
                )
                post(g) { onApprovalMain(a) }
            }
            "usage" -> if (welcomed) {
                fun ring(o: JSONObject?, k: String): Pair<Float?, Long> {
                    val r = o?.optJSONObject(k) ?: return null to 0L
                    val used = if (r.has("used") && !r.isNull("used")) r.optDouble("used", Double.NaN).toFloat() else Float.NaN
                    return (if (used.isNaN()) null else used.coerceIn(0f, 1f)) to r.optLong("resetsAt", 0L)
                }
                val (f, fr) = ring(j, "fiveHour")
                val (w, wr) = ring(j, "sevenDay")
                val u = Usage(
                    f, fr, w, wr,
                    if (j.has("doneToday") && !j.isNull("doneToday")) j.optInt("doneToday", 0).coerceAtLeast(0) else null,
                    if (j.has("tokensToday") && !j.isNull("tokensToday")) j.optLong("tokensToday", 0L).coerceAtLeast(0L) else null,
                    str(j, "plan").take(24).ifEmpty { null }, j.optLong("updatedAt", System.currentTimeMillis())
                )
                post(g) { listener.onUsage(u) }
            }
            "approvalResolved" -> if (welcomed) {
                val fp = str(j, "fingerprint")
                if (fp.isNotEmpty()) post(g) {
                    pending.remove(fp)?.let { main.removeCallbacks(it) }
                    listener.onApprovalResolved(fp)
                }
            }
        }
        return Next.GO
    }

    private fun onApprovalMain(a: Approval) {
        val now = System.currentTimeMillis()
        val age = now - minOf(a.createdAt, now)
        if (age >= TTL_MS) return
        if (a.fingerprint in decided || pending.containsKey(a.fingerprint)) return
        val timer = Runnable {
            pending.remove(a.fingerprint)
            listener.onApprovalResolved(a.fingerprint)
        }
        pending[a.fingerprint] = timer
        main.postDelayed(timer, TTL_MS - age)
        listener.onApproval(a)
    }

    private companion object {
        const val MAX_LINE = 64 * 1024
        const val PING_MS = 20000L
        const val TTL_MS = 120000L
        const val TICK_MS = 300
        val STATES = setOf(
            "idle", "working", "thinking", "searching", "approval", "question",
            "error", "finished", "ratelimit", "sleeping", "dizzy"
        )
    }
}
