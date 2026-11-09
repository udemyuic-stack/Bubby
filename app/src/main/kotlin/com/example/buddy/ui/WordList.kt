package com.example.buddy.ui

object WordList {
    val words: List<String> = (
        "kitob qalam daryo bahor yomon tovuq dunyo osmon tuxum paxta bozor sabzi piyoz baliq eshak tulki echki sigir " +
        "hovli devor tomon qorin yurak qadam sevgi hayot vatan shifo sovuq issiq katta yangi tinch oltin temir yozuv " +
        "xabar savol javob ustoz yozgi kuzgi hafta amaki maqol ertak raqam ekran tugma somsa manti kabob tuzli sinov " +
        "bilim aqlli mehnat oyoqq"
        ).split(" ").filter { it.length == 5 && it.all { c -> c in 'a'..'z' } }.distinct()
}
