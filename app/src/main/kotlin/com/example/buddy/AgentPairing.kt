package com.example.buddy

import android.annotation.SuppressLint
import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Build
import android.os.Handler
import android.os.Looper
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.security.MessageDigest
import java.security.SecureRandom
import java.security.cert.X509Certificate
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocket
import javax.net.ssl.X509TrustManager

/** A computer found on the network (or typed in by hand). [fp] is what it advertised, null when typed. */
data class FoundComputer(val name: String, val host: String, val port: Int, val fp: String?, val paired: Boolean)

/** Finding the computer and pairing with a 6-digit code (docs/AGENTS-V2.md). */
object AgentPairing {
    const val SERVICE = "_bubby-agent._tcp."
    private val TOKEN_RE = Regex("[A-Za-z0-9_-]{16,128}")

    /** Looks for bridges on the local network with Android's NsdManager; [onChange] runs on the main thread with the full list. */
    class Discovery(ctx: Context, private val onChange: (List<FoundComputer>) -> Unit) {
        private val nsd = ctx.applicationContext.getSystemService(Context.NSD_SERVICE) as NsdManager
        private val main = Handler(Looper.getMainLooper())
        private val found = LinkedHashMap<String, FoundComputer>()
        private val queue = ArrayDeque<NsdServiceInfo>()
        private var resolving = false
        private var running = false
        private val listener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(serviceType: String) {}
            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) { running = false }
            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {}
            override fun onDiscoveryStopped(serviceType: String) {}
            override fun onServiceFound(info: NsdServiceInfo) { main.post { queue.add(info); next() } }
            override fun onServiceLost(info: NsdServiceInfo) { main.post { if (found.remove(info.serviceName) != null) onChange(found.values.toList()) } }
        }

        fun start() {
            if (running) return
            running = true
            try { nsd.discoverServices(SERVICE, NsdManager.PROTOCOL_DNS_SD, listener) } catch (_: Exception) { running = false }
        }

        fun stop() {
            if (!running) return
            running = false
            queue.clear()
            try { nsd.stopServiceDiscovery(listener) } catch (_: Exception) { }
        }

        @Suppress("DEPRECATION")
        private fun next() {
            if (resolving || queue.isEmpty()) return
            resolving = true
            val info = queue.removeFirst()
            try {
                nsd.resolveService(info, object : NsdManager.ResolveListener {
                    override fun onResolveFailed(si: NsdServiceInfo, errorCode: Int) { main.post { resolving = false; next() } }
                    override fun onServiceResolved(si: NsdServiceInfo) {
                        main.post {
                            resolving = false
                            val host = si.host?.hostAddress
                            if (host != null && running) {
                                fun txt(k: String): String? = si.attributes?.get(k)?.let { String(it, Charsets.UTF_8) }
                                val name = (txt("name") ?: si.serviceName).filter { !it.isISOControl() }.take(64)
                                val fp = txt("fp")?.lowercase()?.takeIf { Regex("[0-9a-f]{64}").matches(it) }
                                found[si.serviceName] = FoundComputer(name, host, si.port, fp, txt("paired") == "1")
                                onChange(found.values.toList())
                            }
                            next()
                        }
                    }
                })
            } catch (_: Exception) { resolving = false; next() }
        }
    }

    /** Accepts any certificate but remembers its fingerprint: trust on first use, bound to the code by the proof. */
    @SuppressLint("CustomX509TrustManager")
    private class Capture : X509TrustManager {
        @Volatile var fp = ""
        override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {
            val leaf = chain?.firstOrNull() ?: throw java.security.cert.CertificateException("empty chain")
            fp = MessageDigest.getInstance("SHA-256").digest(leaf.encoded).joinToString("") { "%02x".format(it) }
        }
        override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
        override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
    }

    /** Result of [pair]: the link to save, or an error code ("bad_code", "slow_down", "fingerprint", "network", "protocol"). */
    class Result(val link: ParsedLink?, val error: String?)

    /** Blocking, call off the main thread. */
    fun pair(host: String, port: Int, codeRaw: String, expectedFp: String?, nameHint: String): Result {
        val code = PairProof.cleanCode(codeRaw)
        if (code.length != 6) return Result(null, "bad_code")
        val s = Socket()
        try {
            s.connect(InetSocketAddress(host, port), 5000)
            s.tcpNoDelay = true
            val cap = Capture()
            val ctx = SSLContext.getInstance("TLS")
            ctx.init(null, arrayOf(cap), SecureRandom())
            val ssl = ctx.socketFactory.createSocket(s, host, port, true) as SSLSocket
            ssl.useClientMode = true
            ssl.enabledProtocols = ssl.supportedProtocols.filter { it == "TLSv1.2" || it == "TLSv1.3" }.toTypedArray()
            ssl.soTimeout = 8000
            ssl.startHandshake()
            val fpSeen = cap.fp
            if (fpSeen.length != 64) return Result(null, "protocol")
            if (expectedFp != null && expectedFp != fpSeen) return Result(null, "fingerprint")
            val nonceBytes = ByteArray(16).also { SecureRandom().nextBytes(it) }
            val nonce = nonceBytes.joinToString("") { "%02x".format(it) }
            val msg = JSONObject().put("type", "pair").put("v", 1).put("nonce", nonce)
                .put("proof", PairProof.proof(code, fpSeen, nonce)).put("name", Build.MODEL ?: "Android").toString()
            ssl.outputStream.write((msg + "\n").toByteArray()); ssl.outputStream.flush()
            val input = BufferedInputStream(ssl.inputStream)
            val buf = ByteArrayOutputStream()
            while (true) {
                val b = input.read()
                if (b < 0) return Result(null, "protocol")
                if (b == '\n'.code) break
                buf.write(b)
                if (buf.size() > 64 * 1024) return Result(null, "protocol")
            }
            val j = JSONObject(buf.toString("UTF-8"))
            return when (j.optString("type")) {
                "paired" -> {
                    val token = j.optString("token")
                    if (!TOKEN_RE.matches(token)) Result(null, "protocol")
                    else Result(ParsedLink(host, port, fpSeen, token, j.optString("name").filter { !it.isISOControl() }.trim().take(64).ifEmpty { nameHint }), null)
                }
                "error" -> Result(null, when (j.optString("error")) { "bad_code" -> "bad_code"; "slow_down" -> "slow_down"; "not_pairing" -> "not_pairing"; else -> "protocol" })
                else -> Result(null, "protocol")
            }
        } catch (e: java.net.SocketTimeoutException) {
            return Result(null, "network")
        } catch (e: java.io.IOException) {
            return Result(null, "network")
        } catch (e: Exception) {
            return Result(null, "protocol")
        } finally {
            try { s.close() } catch (_: Exception) { }
        }
    }

    /** "192.168.1.5", "192.168.1.5:47821" or "mac.local:47821" -> host and port (default port 47821). */
    fun parseAddress(raw: String): Pair<String, Int>? {
        val t = raw.trim().removePrefix("https://").removePrefix("http://").trimEnd('/')
        if (t.isEmpty() || t.any { it.isWhitespace() }) return null
        val i = t.lastIndexOf(':')
        val host = if (i > 0) t.substring(0, i) else t
        val port = if (i > 0) t.substring(i + 1).toIntOrNull() ?: return null else 47821
        if (port !in 1..65535 || !Regex("[A-Za-z0-9]([A-Za-z0-9.-]{0,251}[A-Za-z0-9])?").matches(host)) return null
        return host to port
    }
}
