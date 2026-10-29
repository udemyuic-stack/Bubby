package com.example.buddy

import java.io.ByteArrayOutputStream
import java.io.EOFException
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketTimeoutException
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory

/**
 * A small RFC 6455 client (text messages only) for the relay, because the app uses no libraries. wss:// uses the
 * system trust store and checks the host name; ws:// is for a local test relay only.
 * All frames the client sends are masked; pings are answered; a close or a dropped connection throws an IOException.
 */
class WsClient private constructor(private val sock: Socket, private val input: InputStream, private val output: OutputStream, private var rx: ByteArray) : java.io.Closeable {
    private val rng = SecureRandom()
    private val parts = ByteArrayOutputStream()
    private var partsOpcode = 0

    @Synchronized fun sendText(s: String) = write(0x1, s.toByteArray(Charsets.UTF_8))

    @Synchronized private fun write(opcode: Int, p: ByteArray) {
        val head = ByteArrayOutputStream()
        head.write(0x80 or opcode)
        when {
            p.size < 126 -> head.write(0x80 or p.size)
            p.size < 65536 -> { head.write(0x80 or 126); head.write(p.size shr 8); head.write(p.size and 255) }
            else -> { head.write(0x80 or 127); for (i in 7 downTo 0) head.write(((p.size.toLong() shr (8 * i)) and 255).toInt()) }
        }
        val mask = ByteArray(4).also { rng.nextBytes(it) }
        head.write(mask)
        val body = ByteArray(p.size) { (p[it].toInt() xor mask[it % 4].toInt()).toByte() }
        output.write(head.toByteArray() + body)
        output.flush()
    }

    /** The next text message, or null when none arrived within [timeoutMs]. Throws IOException when the link is closed. */
    fun readText(timeoutMs: Int): String? {
        sock.soTimeout = timeoutMs
        while (true) {
            val m = parseOne()
            if (m != null) return m
            val buf = ByteArray(8192)
            val n = try { input.read(buf) } catch (e: SocketTimeoutException) { return null }
            if (n < 0) throw EOFException("closed")
            rx += buf.copyOf(n)
            if (rx.size > 2 * MAX_MESSAGE) throw IOException("too much data")
        }
    }

    // one text message out of the buffer, or null when the buffer holds no complete one (control frames are handled on the way)
    private fun parseOne(): String? {
        while (true) {
            if (rx.size < 2) return null
            val b0 = rx[0].toInt() and 255
            val b1 = rx[1].toInt() and 255
            val fin = b0 and 0x80 != 0
            val op = b0 and 15
            var len = (b1 and 127).toLong()
            var off = 2
            if (len == 126L) { if (rx.size < 4) return null; len = (((rx[2].toInt() and 255) shl 8) or (rx[3].toInt() and 255)).toLong(); off = 4 }
            else if (len == 127L) {
                if (rx.size < 10) return null
                len = 0
                for (i in 2..9) len = (len shl 8) or (rx[i].toLong() and 255)
                off = 10
            }
            if (len < 0 || len > MAX_MESSAGE) throw IOException("frame too large")
            val masked = b1 and 0x80 != 0
            val total = off + (if (masked) 4 else 0) + len.toInt()
            if (rx.size < total) return null
            val start = off + (if (masked) 4 else 0)
            val payload = rx.copyOfRange(start, total)
            if (masked) for (i in payload.indices) payload[i] = (payload[i].toInt() xor rx[off + i % 4].toInt()).toByte()
            rx = rx.copyOfRange(total, rx.size)
            when (op) {
                0x0, 0x1, 0x2 -> {
                    if (op != 0) { parts.reset(); partsOpcode = op }
                    parts.write(payload)
                    if (parts.size() > MAX_MESSAGE) throw IOException("message too large")
                    if (fin) {
                        val bytes = parts.toByteArray(); parts.reset()
                        if (partsOpcode == 0x1) return String(bytes, Charsets.UTF_8)
                    }
                }
                0x8 -> { try { write(0x8, payload.copyOf(minOf(2, payload.size))) } catch (e: IOException) { }; throw EOFException("closed by the relay") }
                0x9 -> write(0xA, payload)
                else -> { }
            }
        }
    }

    override fun close() {
        try { write(0x8, byteArrayOf(0x03, 0xe8.toByte())) } catch (e: Exception) { }
        try { sock.close() } catch (e: Exception) { }
    }

    companion object {
        const val MAX_MESSAGE = 1024 * 1024
        private const val GUID = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11"

        class Target(val tls: Boolean, val host: String, val port: Int, val path: String)

        /** ws://host[:port]/path or wss://...; null when it is neither. */
        fun parse(url: String): Target? {
            val tls = when {
                url.startsWith("wss://") -> true
                url.startsWith("ws://") -> false
                else -> return null
            }
            val rest = url.substring(if (tls) 6 else 5)
            val slash = rest.indexOf('/')
            val authority = if (slash < 0) rest else rest.substring(0, slash)
            val path = if (slash < 0) "/" else rest.substring(slash)
            if (authority.isEmpty() || authority.contains('@') || path.any { it.code < 33 || it.code > 126 }) return null
            val colon = authority.lastIndexOf(':')
            val host = if (colon >= 0) authority.substring(0, colon) else authority
            val port = if (colon >= 0) authority.substring(colon + 1).toIntOrNull() ?: return null else if (tls) 443 else 80
            if (host.isEmpty() || port !in 1..65535 || !host.all { it.isLetterOrDigit() || it == '.' || it == '-' }) return null
            return Target(tls, host, port, path)
        }

        fun accept(key: String): String =
            Base64.getEncoder().encodeToString(MessageDigest.getInstance("SHA-1").digest((key + GUID).toByteArray()))

        fun connect(url: String, connectMs: Int = 8000): WsClient {
            val t = parse(url) ?: throw IOException("bad relay address")
            val plain = Socket()
            var s: Socket = plain
            try {
                plain.connect(InetSocketAddress(t.host, t.port), connectMs)
                plain.tcpNoDelay = true
                if (t.tls) {
                    val ssl = (SSLSocketFactory.getDefault() as SSLSocketFactory).createSocket(plain, t.host, t.port, true) as SSLSocket
                    val p = ssl.sslParameters
                    p.endpointIdentificationAlgorithm = "HTTPS"   // the certificate must be for this host name
                    ssl.sslParameters = p
                    ssl.soTimeout = connectMs
                    ssl.startHandshake()
                    s = ssl
                }
                s.soTimeout = connectMs
                val keyBytes = ByteArray(16).also { SecureRandom().nextBytes(it) }
                val key = Base64.getEncoder().encodeToString(keyBytes)
                val os = s.getOutputStream()
                val hostHeader = if ((t.tls && t.port == 443) || (!t.tls && t.port == 80)) t.host else "${t.host}:${t.port}"
                os.write(("GET ${t.path} HTTP/1.1\r\nHost: $hostHeader\r\nUpgrade: websocket\r\nConnection: Upgrade\r\n" +
                    "Sec-WebSocket-Key: $key\r\nSec-WebSocket-Version: 13\r\n\r\n").toByteArray())
                os.flush()
                val ins = s.getInputStream()
                val head = ByteArrayOutputStream()
                while (true) {
                    val b = ins.read()
                    if (b < 0) throw EOFException("closed during the handshake")
                    head.write(b)
                    // the headers end with "\r\n\r\n" = 13 10 13 10
                    val h = head.toByteArray()
                    if (h.size >= 4 && h[h.size - 4].toInt() == 13 && h[h.size - 3].toInt() == 10 && h[h.size - 2].toInt() == 13 && h[h.size - 1].toInt() == 10) break
                    if (h.size > 8192) throw IOException("handshake too long")
                }
                val lines = String(head.toByteArray(), Charsets.ISO_8859_1).split("\r\n")
                if (!lines[0].contains(" 101")) throw IOException("the relay refused: ${lines[0].take(60)}")
                val got = lines.firstOrNull { it.startsWith("sec-websocket-accept:", ignoreCase = true) }?.substringAfter(':')?.trim()
                if (got != accept(key)) throw IOException("bad handshake answer")
                return WsClient(s, ins, os, ByteArray(0))
            } catch (e: Exception) {
                try { s.close() } catch (x: Exception) { }
                try { plain.close() } catch (x: Exception) { }
                throw e
            }
        }
    }
}
