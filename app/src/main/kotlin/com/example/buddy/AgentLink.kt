package com.example.buddy

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.Closeable
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
        /** State of the task started from the phone: running, done, error or stopped (capability `run`). */
        fun onRun(id: String, state: String, text: String) {}
        /** A question from Claude Code (only with the `answers` capability); it ends with [onApprovalResolved]. */
        fun onQuestion(q: AgentQuestion) {}
        /** One part of a diff asked for with [getDiff]. */
        fun onDiff(d: DiffPart) {}
    }

    private enum class Next { GO, RETRY, FATAL }

    private val app = ctx.applicationContext
    private val main = Handler(Looper.getMainLooper())
    private val out = ConcurrentLinkedQueue<String>()
    private val pending = HashMap<String, Runnable>()
    private val decided = HashSet<String>()
    private val asked = HashMap<String, AgentQuestion>()
    private var thread: Thread? = null

    @Volatile private var gen = 0
    @Volatile private var connected = false
    @Volatile private var raw: Closeable? = null
    /** True while the connection runs through the relay instead of the home network. */
    @Volatile var viaRelay = false
        private set

    /** What the bridge offered in this connection's `welcome` (empty for a bridge without capabilities). */
    @Volatile var caps: Set<String> = emptySet()
        private set

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
        asked.clear()
        caps = emptySet()
    }

    /** [always] = allow and let Claude Code remember it for the rest of its session (only when the bridge offers it). */
    fun decide(fingerprint: String, allow: Boolean, always: Boolean = false) {
        if (!connected || fingerprint in decided || fingerprint in asked) return
        val timer = pending.remove(fingerprint) ?: return
        main.removeCallbacks(timer)
        decided.add(fingerprint)
        val m = JSONObject().put("type", "decision").put("fingerprint", fingerprint).put("decision", if (allow) "allow" else "deny")
        if (allow && always && "always" in caps) m.put("always", true)
        out.add(m.toString())
    }

    /**
     * Sends the answer to a pending question. [picks] must be exactly what the bridge accepts (one list per question),
     * otherwise nothing is sent. The caller has already asked for the screen lock. True when it was queued.
     */
    fun answer(fingerprint: String, picks: List<List<String>>): Boolean {
        if (!connected || "answers" !in caps || fingerprint in decided) return false
        val q = asked[fingerprint] ?: return false
        if (!AgentProto.picksValid(q, picks) || AgentProto.expired(q.createdAt, System.currentTimeMillis())) return false
        pending.remove(fingerprint)?.let { main.removeCallbacks(it) }
        asked.remove(fingerprint)
        decided.add(fingerprint)
        out.add(JSONObject().put("type", "answer").put("fingerprint", fingerprint)
            .put("picks", JSONArray().apply { for (p in picks) put(JSONArray(p)) }).toString())
        return true
    }

    /** Starts a Claude Code task on the computer (capability `run`; the computer must have been started with --allow-run). */
    fun runTask(id: String, prompt: String): Boolean {
        if (!connected || "run" !in caps || prompt.isBlank() || prompt.length > 4000) return false
        out.add(JSONObject().put("type", "run").put("id", id).put("prompt", prompt).toString())
        return true
    }

    fun stopTask(id: String): Boolean {
        if (!connected || "run" !in caps) return false
        out.add(JSONObject().put("type", "runStop").put("id", id).toString())
        return true
    }

    /** Asks for the lines of one changed file (capability `diffs`, at most 20 in 10 s). False when it cannot be sent now. */
    fun getDiff(pillId: String, file: AgentFile): Boolean {
        if (!connected || "diffs" !in caps) return false
        if (!diffRate.tryTake(System.currentTimeMillis())) return false
        val o = JSONObject().put("type", "getDiff").put("pillId", pillId)
        if (file.idNum != null) o.put("fileId", file.idNum) else o.put("fileId", file.id)
        out.add(o.toString())
        return true
    }

    private fun closeAsync(s: Closeable?) {
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
            var next = try {
                session(link, g) { welcomed = true }
            } catch (e: Exception) {
                Next.RETRY
            }
            // the computer is not on this network: try the relay, when the computer gave us one
            if (g == gen && !welcomed && next != Next.FATAL) {
                val relay = try { PairingStore(app).loadRelay() } catch (e: Exception) { null }
                if (relay != null) next = try { relaySession(link, relay, g) { welcomed = true } } catch (e: Exception) { Next.RETRY }
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
        viaRelay = false
        out.clear()
        for ((fp, timer) in pending.toMap()) {
            main.removeCallbacks(timer)
            pending.remove(fp)
            asked.remove(fp)
            listener.onApprovalResolved(fp)
        }
        asked.clear()
    }

    private val hasRelay get() = try { PairingStore(app).hasRelay() } catch (e: Exception) { false }

    private fun session(link: ParsedLink, g: Int, onWelcome: () -> Unit): Next {
        val s = Socket()
        raw = s
        try {
            if (g != gen) return Next.RETRY
            s.connect(InetSocketAddress(link.host, link.port), if (hasRelay) 2500 else 5000)
            s.tcpNoDelay = true
            val ctx = SSLContext.getInstance("TLS")
            ctx.init(null, arrayOf(PinnedTrust(link.fp)), SecureRandom())
            val ssl = ctx.socketFactory.createSocket(s, link.host, link.port, true) as SSLSocket
            ssl.useClientMode = true
            ssl.enabledProtocols = ssl.supportedProtocols.filter { it == "TLSv1.2" || it == "TLSv1.3" }.toTypedArray()
            ssl.soTimeout = 8000
            ssl.startHandshake()
            ssl.soTimeout = TICK_MS
            viaRelay = false
            return converse(BufferedInputStream(ssl.inputStream), ssl.outputStream, link, g, onWelcome)
        } finally {
            closeAsync(s)
            if (raw === s) raw = null
        }
    }

    private fun relaySession(link: ParsedLink, relay: RelayInfo, g: Int, onWelcome: () -> Unit): Next {
        val ch = RelayChannel(relay.url, relay.key)
        raw = ch
        try {
            if (g != gen) return Next.RETRY
            viaRelay = true
            return converse(ch.input, ch.output, link, g, onWelcome)
        } finally {
            viaRelay = false
            closeAsync(ch)
            if (raw === ch) raw = null
        }
    }

    /** The protocol conversation, the same over both transports: hello, then lines in and out until the link ends. */
    private fun converse(input: java.io.InputStream, os: OutputStream, link: ParsedLink, g: Int, onWelcome: () -> Unit): Next {
        run {
            out.clear()
            caps = emptySet()
            send(os, JSONObject().put("type", "hello").put("v", 1).put("token", link.token)
                .put("device", Build.MODEL ?: "Android").put("caps", JSONArray(AgentProto.CAPS)).toString())

            var welcomed = false
            val began = System.currentTimeMillis()
            var lastRx = began
            var lastPing = began
            val buf = ByteArrayOutputStream()
            var skipping = false   // a line longer than MAX_LINE is dropped up to its end, the link stays up

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
                    if (skipping) continue
                    buf.write(b)
                    if (buf.size() > MAX_LINE) { buf.reset(); skipping = true }
                    continue
                }
                if (skipping) { skipping = false; continue }
                val line = buf.toString("UTF-8").trim()
                buf.reset()
                if (line.isNotEmpty()) {
                    val r = handle(line, g, welcomed) { welcomed = true; onWelcome() }
                    if (r != Next.GO) return r
                }
                if (!housekeeping()) return Next.RETRY
            }
            return Next.RETRY
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
                val desktop = AgentProto.clean(str(j, "desktop"), 64).ifEmpty { null }
                caps = AgentProto.parseCaps(plain(j.opt("caps"), 0))
                onWelcome()
                post(g) { connected = true; listener.onStatus(true, desktop) }
            }
            "error" -> if (str(j, "code") == "auth") return Next.FATAL
            "sessions" -> if (welcomed) {
                val arr = j.optJSONArray("sessions") ?: return Next.GO
                val list = ArrayList<AgentSession>()
                val c = caps
                for (i in 0 until minOf(arr.length(), AgentProto.MAX_SESSIONS)) {
                    val o = plain(arr.optJSONObject(i), 0) as? Map<*, *> ?: continue
                    AgentProto.parseSession(o, c)?.let { list.add(it) }
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
            "runState" -> if (welcomed && "run" in caps) {
                val id = str(j, "id").take(40); val st = str(j, "state"); val tx = str(j, "text").take(4000)
                if (id.isNotEmpty() && st in setOf("running", "done", "error", "stopped")) post(g) { listener.onRun(id, st, tx) }
            }
            // the computer tells us where its relay is (only on the home-network link, which is certificate-pinned)
            "relay" -> if (welcomed && !viaRelay && "relay" in caps) {
                val url = str(j, "url"); val key = str(j, "key")
                if (RelayInfo.valid(url, key)) try { PairingStore(app).saveRelay(RelayInfo(url, key)) } catch (e: Exception) { }
            }
            "approvalResolved" -> if (welcomed) {
                val fp = str(j, "fingerprint")
                if (fp.isNotEmpty()) post(g) {
                    pending.remove(fp)?.let { main.removeCallbacks(it) }
                    asked.remove(fp)
                    listener.onApprovalResolved(fp)
                }
            }
            // a question that breaks any limit of the spec is dropped: it stays on the computer
            "question" -> if (welcomed && "answers" in caps) {
                val m = plain(j, 0) as? Map<*, *> ?: return Next.GO
                val q = AgentProto.parseQuestion(m, System.currentTimeMillis()) ?: return Next.GO
                post(g) { onQuestionMain(q) }
            }
            "diff" -> if (welcomed && "diffs" in caps) {
                val m = plain(j, 0) as? Map<*, *> ?: return Next.GO
                val d = AgentProto.parseDiff(m) ?: return Next.GO
                post(g) { listener.onDiff(d) }
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

    private fun onQuestionMain(q: AgentQuestion) {
        val now = System.currentTimeMillis()
        if (AgentProto.expired(q.createdAt, now)) return
        if (q.fingerprint in decided || pending.containsKey(q.fingerprint)) return
        val timer = Runnable {
            pending.remove(q.fingerprint)
            asked.remove(q.fingerprint)
            listener.onApprovalResolved(q.fingerprint)
        }
        pending[q.fingerprint] = timer
        asked[q.fingerprint] = q
        main.postDelayed(timer, AgentProto.msLeft(q.createdAt, now))
        listener.onQuestion(q)
    }

    /** org.json values as plain Kotlin ones (Map, List, String, Number, Boolean, null) for the Android-free checks. */
    private fun plain(v: Any?, depth: Int): Any? = when {
        depth > 8 -> null
        v is JSONObject -> {
            val m = LinkedHashMap<String, Any?>()
            val it = v.keys()
            while (it.hasNext()) { val k = it.next(); m[k] = plain(v.opt(k), depth + 1) }
            m
        }
        v is JSONArray -> List(v.length()) { plain(v.opt(it), depth + 1) }
        v === JSONObject.NULL -> null
        v is String || v is Number || v is Boolean -> v
        else -> null
    }

    private companion object {
        /** diff parts can be about 100 lines of 400 characters, more than 64 KiB once escaped; longer lines are skipped */
        const val MAX_LINE = 256 * 1024
        val diffRate = RateWindow(AgentProto.DIFF_RATE_MAX, AgentProto.DIFF_RATE_WINDOW_MS)
        const val PING_MS = 20000L
        const val TTL_MS = 120000L
        const val TICK_MS = 300
    }
}
