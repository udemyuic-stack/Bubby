package com.example.buddy

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.net.URLDecoder
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

data class ParsedLink(val host: String, val port: Int, val fp: String, val token: String, val name: String)

private val TOKEN_RE = Regex("[A-Za-z0-9_-]{16,128}")
private val FP_RE = Regex("[0-9a-fA-F]{64}")
private val HOST_RE = Regex("[A-Za-z0-9]([A-Za-z0-9.-]{0,251}[A-Za-z0-9])?")
private const val PREFIX = "coucou://pair?"

// Pair-link format and limits follow coucou's docs/ANDROID_LINK.md (MIT); parsing is our own.
fun parsePairLink(s: String): ParsedLink? {
    val t = s.trim()
    if (t.length > 2048 || !t.regionMatches(0, PREFIX, 0, PREFIX.length, ignoreCase = true)) return null
    val q = HashMap<String, String>()
    for (part in t.substring(PREFIX.length).split('&')) {
        val i = part.indexOf('=')
        if (i <= 0) return null
        val v = try {
            URLDecoder.decode(part.substring(i + 1), "UTF-8")
        } catch (e: IllegalArgumentException) {
            return null
        }
        if (q.put(part.substring(0, i), v) != null) return null
    }
    if (q["v"] != "1") return null
    val host = q["host"] ?: return null
    if (!HOST_RE.matches(host) || host.contains("..")) return null
    val portS = q["port"] ?: return null
    if (portS.isEmpty() || portS.length > 5 || !portS.all { it in '0'..'9' }) return null
    val port = portS.toInt()
    if (port !in 1..65535) return null
    val fp = q["fp"] ?: return null
    if (!FP_RE.matches(fp)) return null
    val token = q["token"] ?: return null
    if (!TOKEN_RE.matches(token)) return null
    val name = (q["name"] ?: "").filter { !it.isISOControl() }.trim().take(64).ifEmpty { host }
    return ParsedLink(host, port, fp.lowercase(), token, name)
}

/** The token is encrypted with an AES-GCM key that lives in the Android Keystore; save() throws if the Keystore fails. */
class PairingStore(ctx: Context) {
    private val prefs = ctx.applicationContext.getSharedPreferences("agent_link", Context.MODE_PRIVATE)

    fun save(link: ParsedLink) {
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.ENCRYPT_MODE, key())
        c.updateAAD(link.fp.toByteArray())
        val ct = c.doFinal(link.token.toByteArray())
        val enc = b64(c.iv) + ":" + b64(ct)
        prefs.edit()
            .putString("host", link.host).putInt("port", link.port).putString("fp", link.fp)
            .putString("name", link.name).putString("tok", enc)
            .remove("relay_url").remove("relay_key")   // a new pairing: the computer sends its relay again
            .apply()
    }

    /** The relay the computer gave us (address and key), encrypted like the token. */
    fun saveRelay(r: RelayInfo) {
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.ENCRYPT_MODE, key())
        c.updateAAD("relay".toByteArray())
        val ct = c.doFinal(r.key.toByteArray())
        prefs.edit().putString("relay_url", r.url).putString("relay_key", b64(c.iv) + ":" + b64(ct)).apply()
    }

    fun hasRelay(): Boolean = prefs.contains("relay_key")

    fun loadRelay(): RelayInfo? {
        val url = prefs.getString("relay_url", null) ?: return null
        val enc = prefs.getString("relay_key", null) ?: return null
        return try {
            val parts = enc.split(':')
            if (parts.size != 2) throw IllegalStateException()
            val c = Cipher.getInstance("AES/GCM/NoPadding")
            c.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, Base64.decode(parts[0], Base64.NO_WRAP)))
            c.updateAAD("relay".toByteArray())
            val k = String(c.doFinal(Base64.decode(parts[1], Base64.NO_WRAP)))
            if (RelayInfo.valid(url, k)) RelayInfo(url, k) else null
        } catch (e: Exception) {
            clearRelay()
            null
        }
    }

    fun clearRelay() { prefs.edit().remove("relay_url").remove("relay_key").apply() }

    /** Cheap check (no Keystore work): is a computer paired? */
    fun isPaired(): Boolean = prefs.contains("tok")

    fun load(): ParsedLink? {
        val host = prefs.getString("host", null) ?: return null
        val fp = prefs.getString("fp", null) ?: return null
        val name = prefs.getString("name", null) ?: return null
        val enc = prefs.getString("tok", null) ?: return null
        val port = prefs.getInt("port", 0)
        return try {
            val parts = enc.split(':')
            if (parts.size != 2) throw IllegalStateException()
            val c = Cipher.getInstance("AES/GCM/NoPadding")
            c.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, Base64.decode(parts[0], Base64.NO_WRAP)))
            c.updateAAD(fp.toByteArray())
            val token = String(c.doFinal(Base64.decode(parts[1], Base64.NO_WRAP)))
            val link = ParsedLink(host, port, fp, token, name)
            if (port !in 1..65535 || !FP_RE.matches(fp) || !TOKEN_RE.matches(token)) null else link
        } catch (e: Exception) {
            clear()
            null
        }
    }

    fun clear() {
        prefs.edit().clear().apply()
        try {
            KeyStore.getInstance("AndroidKeyStore").apply { load(null) }.deleteEntry(ALIAS)
        } catch (e: Exception) {
        }
    }

    private fun b64(b: ByteArray) = Base64.encodeToString(b, Base64.NO_WRAP)

    private fun key(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        val g = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        g.init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return g.generateKey()
    }

    private companion object {
        const val ALIAS = "buddy_agent_token"
    }
}
