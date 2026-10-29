package com.example.buddy

import org.json.JSONObject
import java.io.Closeable
import java.io.EOFException
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.SocketTimeoutException

/** Where the relay is and the key for it (sent by the computer over the home-network link, kept encrypted). */
data class RelayInfo(val url: String, val key: String) {
    companion object {
        /** A relay address the phone accepts: wss:// (or ws:// for a local test relay), a host, no user info, query or fragment. */
        fun valid(url: String, key: String): Boolean {
            if (url.length > 256 || key.length > 64) return false
            val t = WsClient.parse(url) ?: return false
            if (t.path != "/" || url.contains('?') || url.contains('#')) return false
            return RelayCrypto.derive(key) != null
        }
    }
}

/**
 * The phone's end of one relay connection: a WebSocket to the relay with every protocol line end-to-end encrypted, shown
 * to AgentLink as an ordinary pair of streams (read blocks at most TICK_MS and then throws SocketTimeoutException).
 * The relay's own {"type":"peer","up":false} notice and the computer's end marker both read as end of stream.
 */
class RelayChannel(url: String, rk: String, private val now: () -> Long = { System.currentTimeMillis() }) : Closeable {
    private val keys = RelayCrypto.derive(rk) ?: throw IOException("bad relay key")
    private val sender = RelayCrypto.Sender(keys.key, RelayCrypto.TO_BRIDGE, now)
    private val receiver = RelayCrypto.Receiver(now)
    private val ws = WsClient.connect(url.trimEnd('/') + "/r/" + keys.room + "?role=phone")
    private var buf = ByteArray(0)
    private var pos = 0
    private var lastKeep = now()

    private fun fill() {
        val t = now()
        if (t - lastKeep >= KEEP_MS) { lastKeep = t; ws.sendText("ping") }
        val text = ws.readText(TICK_MS) ?: throw SocketTimeoutException()
        if (text.startsWith("{")) {
            val j = try { JSONObject(text) } catch (e: Exception) { return }
            if (j.optString("type") == "peer" && !j.optBoolean("up", true)) throw EOFException("the computer is not on the relay")
            return
        }
        if (text.isEmpty() || text == "pong") return
        val p = RelayCrypto.open(keys.key, RelayCrypto.TO_PHONE, text) ?: return
        if (!receiver.accept(p)) return
        if (p.end) throw EOFException("the computer ended this connection")
        buf = (p.line + "\n").toByteArray(); pos = 0
    }

    val input = object : InputStream() {
        override fun read(): Int {
            while (pos >= buf.size) fill()
            return buf[pos++].toInt() and 255
        }
    }

    val output = object : OutputStream() {
        private val line = java.io.ByteArrayOutputStream()
        override fun write(b: Int) { line.write(b) }
        override fun write(b: ByteArray, off: Int, len: Int) { line.write(b, off, len) }
        override fun flush() {
            if (line.size() == 0) return
            val text = line.toString("UTF-8"); line.reset()
            for (l in text.split('\n')) if (l.isNotEmpty()) ws.sendText(sender.line(l))
        }
    }

    override fun close() {
        try { ws.sendText(sender.end()) } catch (e: Exception) { }
        ws.close()
    }

    private companion object {
        const val TICK_MS = 300
        const val KEEP_MS = 30_000L
    }
}
