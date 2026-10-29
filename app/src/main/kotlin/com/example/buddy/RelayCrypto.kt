package com.example.buddy

import org.json.JSONObject
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * End-to-end encryption of the relay link (docs/RELAY.md), the phone's half of agent-bridge/lib/relay-crypto.mjs.
 * A frame is "1." + base64url(iv || ciphertext || tag); the plaintext is {t, s, b, l[, x]}. No Android classes in here.
 */
object RelayCrypto {
    class Keys(val room: String, val key: ByteArray)
    class Plain(val t: Long, val s: Long, val b: String, val line: String, val end: Boolean)

    const val TO_PHONE = "b2p"
    const val TO_BRIDGE = "p2b"
    private const val MAX_SKEW_MS = 5 * 60_000L
    private val rng = SecureRandom()

    fun b64u(b: ByteArray): String = Base64.getUrlEncoder().withoutPadding().encodeToString(b)
    fun unb64u(s: String): ByteArray? = try { Base64.getUrlDecoder().decode(s) } catch (e: IllegalArgumentException) { null }

    private fun hmac(key: ByteArray, data: ByteArray): ByteArray {
        val m = Mac.getInstance("HmacSHA256")
        m.init(SecretKeySpec(key, "HmacSHA256"))
        return m.doFinal(data)
    }

    private fun expand(rk: ByteArray, label: String) = hmac(rk, label.toByteArray() + byteArrayOf(1))

    /** The relay key (32 random bytes, base64url) -> the room id for the relay URL and the AES key; null when it is not a key. */
    fun derive(rkText: String): Keys? {
        val rk = unb64u(rkText) ?: return null
        if (rk.size != 32) return null
        val room = expand(rk, "bubby-relay-room").copyOf(16).joinToString("") { "%02x".format(it) }
        return Keys(room, expand(rk, "bubby-relay-enc"))
    }

    fun seal(key: ByteArray, dir: String, t: Long, s: Long, boot: String, line: String, end: Boolean = false, iv: ByteArray = ByteArray(12).also { rng.nextBytes(it) }): String {
        val o = JSONObject().put("t", t).put("s", s).put("b", boot).put("l", line)
        if (end) o.put("x", 1)
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, iv))
        c.updateAAD(dir.toByteArray())
        return "1." + b64u(iv + c.doFinal(o.toString().toByteArray()))
    }

    /** Opens one frame; null for anything that is not an authentic frame in this direction. */
    fun open(key: ByteArray, dir: String, text: String): Plain? {
        if (!text.startsWith("1.") || text.length > 700_000) return null
        return try {
            val b = unb64u(text.substring(2)) ?: return null
            if (b.size < 12 + 16) return null
            val c = Cipher.getInstance("AES/GCM/NoPadding")
            c.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, b.copyOfRange(0, 12)))
            c.updateAAD(dir.toByteArray())
            val j = JSONObject(String(c.doFinal(b, 12, b.size - 12)))
            if (!j.has("t") || !j.has("s") || !j.has("b") || !j.has("l")) return null
            Plain(j.getLong("t"), j.getLong("s"), j.getString("b"), j.getString("l"), j.optInt("x", 0) == 1)
        } catch (e: Exception) { null }
    }

    /** Replay protection: fresh (clock within 5 minutes) and a counter that grows within its sender's boot id. */
    class Receiver(private val now: () -> Long = { System.currentTimeMillis() }) {
        private val boots = LinkedHashMap<String, Long>()
        fun accept(p: Plain): Boolean {
            if (p.b.isEmpty() || p.b.length > 32 || Math.abs(now() - p.t) > MAX_SKEW_MS) return false
            val last = boots[p.b]
            if (last != null && p.s <= last) return false
            boots[p.b] = p.s
            if (boots.size > 16) boots.remove(boots.keys.first())
            return true
        }
    }

    /** Numbers the frames this side sends. */
    class Sender(private val key: ByteArray, private val dir: String, private val now: () -> Long = { System.currentTimeMillis() }) {
        private val boot = ByteArray(6).also { rng.nextBytes(it) }.joinToString("") { "%02x".format(it) }
        private var seq = 0L
        @Synchronized fun line(l: String): String = seal(key, dir, now(), ++seq, boot, l)
        @Synchronized fun end(): String = seal(key, dir, now(), ++seq, boot, "", end = true)
    }
}
