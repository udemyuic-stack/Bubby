package com.example.buddy

import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/** The proof a phone sends to pair with a 6-digit code (docs/AGENTS-V2.md). Pure logic, tested in qa-jvm. */
object PairProof {
    /** HMAC-SHA256(key = the code, data = "bubby-pair-v1|" + fingerprint the phone saw + "|" + nonce), lowercase hex. */
    fun proof(code: String, fpSeen: String, nonce: String): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(code.toByteArray(Charsets.UTF_8), "HmacSHA256"))
        val out = mac.doFinal(("bubby-pair-v1|" + fpSeen.lowercase() + "|" + nonce).toByteArray(Charsets.UTF_8))
        return out.joinToString("") { "%02x".format(it) }
    }

    fun cleanCode(raw: String): String = raw.filter { it.isDigit() }
}
