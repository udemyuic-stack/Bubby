package com.example.buddy

fun autoAnswerTests() {
    fun skip(enabled: Boolean = true, perms: Boolean = true, onlyContacts: Boolean = false, contact: Boolean = false, onlyHeadset: Boolean = false, headset: Boolean = false) =
        AutoAnswerRules.skipReason(enabled, perms, onlyContacts, contact, onlyHeadset, headset)
    check("autoanswer plain", skip() == null)
    check("autoanswer off", skip(enabled = false) == AutoAnswerRules.Skip.OFF)
    check("autoanswer off wins over perms", skip(enabled = false, perms = false) == AutoAnswerRules.Skip.OFF)
    check("autoanswer no perms", skip(perms = false) == AutoAnswerRules.Skip.NO_PERMISSION)
    check("autoanswer stranger skipped", skip(onlyContacts = true) == AutoAnswerRules.Skip.NOT_CONTACT)
    check("autoanswer contact answered", skip(onlyContacts = true, contact = true) == null)
    check("autoanswer no headset", skip(onlyHeadset = true) == AutoAnswerRules.Skip.NO_HEADSET)
    check("autoanswer headset", skip(onlyHeadset = true, headset = true) == null)
    check("autoanswer delay default", AutoAnswerRules.delaySec(7) == 5)
    check("autoanswer delay kept", AutoAnswerRules.delaySec(10) == 10)
    check("autoanswer delay zero", AutoAnswerRules.delaySec(0) == 5)
    check("autoanswer delay 3", AutoAnswerRules.delaySec(3) == 3)
    check("autoanswer delay 15", AutoAnswerRules.delaySec(15) == 15)
    check("autoanswer action en", AutoAnswerRules.isAnswerAction("Answer"))
    check("autoanswer action ru", AutoAnswerRules.isAnswerAction("Ответить"))
    check("autoanswer action uz", AutoAnswerRules.isAnswerAction("Javob berish"))
    check("autoanswer decline ignored", !AutoAnswerRules.isAnswerAction("Decline"))
    check("autoanswer reject ru ignored", !AutoAnswerRules.isAnswerAction("Отклонить"))
    check("autoanswer empty", !AutoAnswerRules.isAnswerAction(""))
    val now = 1_000_000_000L
    check("callsms first time", AutoAnswerRules.smsAllowed("+998901234567", 0L, now))
    check("callsms hidden number", !AutoAnswerRules.smsAllowed(null, 0L, now))
    check("callsms empty number", !AutoAnswerRules.smsAllowed("", 0L, now))
    check("callsms short code", !AutoAnswerRules.smsAllowed("1234", 0L, now))
    check("callsms inside window", !AutoAnswerRules.smsAllowed("+998901234567", now - 60_000L, now))
    check("callsms window edge", AutoAnswerRules.smsAllowed("+998901234567", now - AutoAnswerRules.SMS_WINDOW_MS, now))
    check("callsms clock moved back", AutoAnswerRules.smsAllowed("+998901234567", now + 60_000L, now))
}
