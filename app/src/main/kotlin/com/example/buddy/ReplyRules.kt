package com.example.buddy

/** When an automatic reply may be sent. No Android classes: testable on the JVM. */
object ReplyRules {
    const val COOLDOWN_MS = 60 * 60 * 1000L
    const val MAX_HOURS = 12

    /** Time at which the automatic reply switches itself off: 1..12 hours from [now]. */
    fun until(now: Long, hours: Int): Long = now + hours.coerceIn(1, MAX_HOURS) * 3_600_000L

    /** Only for a private chat message from a known sender, once per chat per hour, while the switch is on. */
    fun shouldReply(
        until: Long, now: Long, canReply: Boolean, isMessage: Boolean, isGroup: Boolean,
        senderIsNumber: Boolean, lastReplyMs: Long?
    ): Boolean {
        if (until <= now || !canReply || !isMessage || isGroup || senderIsNumber) return false
        return lastReplyMs == null || now - lastReplyMs >= COOLDOWN_MS
    }
}
