package com.example.buddy

object AutoAnswerRules {
    val DELAYS = listOf(3, 5, 10, 15)
    const val DEFAULT_DELAY = 5

    fun delaySec(stored: Int): Int = if (stored in DELAYS) stored else DEFAULT_DELAY

    enum class Skip { OFF, NO_PERMISSION, NOT_CONTACT, NO_HEADSET }

    fun skipReason(
        enabled: Boolean, permsOk: Boolean, onlyContacts: Boolean, isContact: Boolean,
        onlyHeadset: Boolean, headsetOn: Boolean
    ): Skip? = when {
        !enabled -> Skip.OFF
        !permsOk -> Skip.NO_PERMISSION
        onlyContacts && !isContact -> Skip.NOT_CONTACT
        onlyHeadset && !headsetOn -> Skip.NO_HEADSET
        else -> null
    }

    const val SMS_WINDOW_MS = 10 * 60_000L
    const val SMS_MIN_DIGITS = 7

    fun smsAllowed(number: String?, lastSentMs: Long, nowMs: Long): Boolean {
        if (number.isNullOrEmpty() || number.count { it.isDigit() } < SMS_MIN_DIGITS) return false
        return lastSentMs <= 0L || nowMs - lastSentMs !in 0 until SMS_WINDOW_MS
    }

    private val answerWords = listOf("answer", "accept", "ответить", "принять", "javob", "qabul")
    private val declineWords = listOf("decline", "reject", "отклонить", "сбросить", "rad")

    fun isAnswerAction(title: String): Boolean {
        val t = title.lowercase().trim()
        if (t.isEmpty() || declineWords.any { t.contains(it) }) return false
        return answerWords.any { t.contains(it) }
    }
}
