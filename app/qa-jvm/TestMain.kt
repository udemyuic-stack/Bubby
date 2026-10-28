package com.example.buddy

var pass = 0
var fail = 0
fun check(name: String, ok: Boolean, detail: String = "") {
    if (ok) pass++ else { fail++; println("FAIL: $name $detail") }
}
internal fun P(t: String, h: Int = 10, m: Int = 0) = QuickIntent.parse(t, h, m)
internal fun fmt(p: ParsedQuick?) = if (p == null) "null" else "${p.intent}${p.params}"
internal fun intent(t: String, exp: String, h: Int = 10, m: Int = 0) { val p = P(t, h, m); check("intent '$t'", p?.intent == exp, "got ${fmt(p)} want $exp") }
internal fun param(t: String, exp: String, k: String, v: Any, h: Int = 10, m: Int = 0) {
    val p = P(t, h, m); check("'$t' $k", p?.intent == exp && p.params[k] == v, "got ${fmt(p)} want $exp $k=$v")
}
internal fun alarm(t: String, hh: Int, mm: Int, h: Int = 10, m: Int = 0) {
    val p = P(t, h, m)
    check("alarm '$t' @$h:$m", p?.intent == "alarm" && p.params["hour"] == hh && p.params["minute"] == mm, "got ${fmt(p)} want $hh:$mm")
}
internal fun nul(t: String) { val p = P(t); check("null '$t'", p == null, "got ${fmt(p)}") }

fun quick() {
    // flashlight
    for (t in listOf("включи фонарик", "включи фонарь", "фонарик включи", "yoqing chiroq", "chiroqni yoq", "turn on flashlight", "flashlight on", "turn on the torch", "включи фонарик пожалуйста", "бубби включи фонарик"))
        param(t, "flashlight", "on", true)
    for (t in listOf("выключи фонарик", "погаси фонарик", "chiroqni o'chir", "turn off flashlight", "flashlight off", "turn off the torch"))
        param(t, "flashlight", "on", false)
    // volume
    for (t in listOf("громче", "сделай громче", "увеличь громкость", "balandroq", "ovozni oshir", "louder", "volume up", "turn it up", "turn up the volume"))
        param(t, "volume", "dir", "up")
    for (t in listOf("тише", "потише", "уменьши громкость", "pastroq", "ovozni kamaytir", "quieter", "volume down", "turn the volume down"))
        param(t, "volume", "dir", "down")
    param("громкость на 50", "volume", "percent", 50)
    param("громкость 50 процентов", "volume", "percent", 50)
    param("set volume to 30", "volume", "percent", 30)
    param("volume 100", "volume", "percent", 100)
    param("volume 0%", "volume", "percent", 0)
    param("ovozni 70 foiz qil", "volume", "percent", 70)
    param("громкость на пятьдесят", "volume", "percent", 50)
    param("volume 70%", "volume", "percent", 70)
    param("громкость максимум", "volume", "percent", 100)
    param("mute", "volume", "percent", 0)
    param("без звука", "volume", "percent", 0)
    nul("громкость 150")
    nul("volume 101")
    // media
    // pause words must only pause (never toggle), see the QA fix: "pause while paused" must not start the music
    for (t in listOf("пауза", "поставь на паузу", "стоп", "pause", "pause music", "pauza")) param(t, "media", "key", "pause")
    for (t in listOf("следующая песня", "next", "next song", "skip", "дальше", "keyingi", "следующий трек")) param(t, "media", "key", "next")
    for (t in listOf("предыдущая песня", "previous", "prev track", "oldingi", "предыдущий трек")) param(t, "media", "key", "previous")
    param("play", "media", "_v", "play"); param("продолжи", "media", "_v", "play")
    // alarm absolute
    alarm("поставь будильник на 7:30", 7, 30, 6, 0)
    alarm("будильник на 7:30", 7, 30, 6, 0)
    alarm("set an alarm for 7:30", 7, 30, 6, 0)
    alarm("set alarm for 7 pm", 19, 0)
    alarm("alarm for 7 am", 7, 0)
    alarm("alarm at 7 pm", 19, 0)
    alarm("будильник в 7 утра", 7, 0)
    alarm("разбуди меня в 7 утра", 7, 0)
    alarm("будильник в 7 вечера", 19, 0)
    alarm("будильник в 12 ночи", 0, 0)
    alarm("будильник в 3 ночи", 3, 0)
    alarm("будильник в 10 ночи", 22, 0)
    alarm("будильник в 5 дня", 17, 0)
    alarm("alarm 12 am", 0, 0)
    alarm("alarm 12 pm", 12, 0)
    alarm("alarm 12:30 am", 0, 30)
    alarm("alarm 12:30 pm", 12, 30)
    alarm("alarm 11 pm", 23, 0)
    alarm("alarm 1 am", 1, 0)
    alarm("alarm at 0:00", 0, 0)
    alarm("alarm at 23:59", 23, 59)
    alarm("будильник на 0:15", 0, 15)
    alarm("будильник на 14:00", 14, 0)
    alarm("будильник на 7 часов утра", 7, 0)
    alarm("budilnik yettiga qo'y", 7, 0, 6, 0)
    alarm("soat yettiga budilnik qo'y", 7, 0, 6, 0)
    alarm("budilnik 7 ga", 7, 0, 6, 0)
    alarm("budilnik ertalab 7 ga", 7, 0)
    alarm("budilnik kechqurun 8 ga", 20, 0)
    alarm("будильник на семь тридцать", 7, 30, 6, 0)
    alarm("будильник на девять сорок пять", 9, 45, 8, 0)
    alarm("alarm at seven thirty", 7, 30, 6, 0)
    // am/pm-less: next occurrence
    alarm("будильник на 7", 7, 0, 6, 0)
    alarm("будильник на 7", 19, 0, 8, 0)    // 7 AM passed -> 7 PM today is next
    alarm("будильник на 7", 7, 0, 20, 0)    // next is 7 AM tomorrow
    alarm("будильник на 12", 12, 0, 10, 0)
    alarm("будильник на 12", 0, 0, 13, 0)
    // relative
    alarm("будильник через 20 минут", 10, 20, 10, 0)
    alarm("alarm in 20 minutes", 10, 20, 10, 0)
    alarm("20 daqiqadan keyin budilnik qo'y", 10, 20, 10, 0)
    alarm("разбуди меня через 20 минут", 10, 20, 10, 0)
    alarm("будильник через 30 минут", 11, 10, 10, 40)   // minute rollover
    alarm("будильник через час", 11, 0, 10, 0)
    alarm("alarm in 1 hour 30 minutes", 11, 30, 10, 0)
    alarm("будильник через полчаса", 10, 30, 10, 0)
    alarm("alarm in 30 minutes", 0, 10, 23, 40)         // midnight wrap
    alarm("будильник через 5 минут", 0, 2, 23, 57)
    alarm("alarm in 90 seconds", 10, 2, 10, 0)           // rounds up
    alarm("alarm in 10 seconds", 10, 1, 10, 0)
    alarm("alarm in twenty minutes", 10, 20, 10, 0)
    alarm("будильник через двадцать минут", 10, 20, 10, 0)
    alarm("budilnik 20 daqiqadan keyin", 10, 20, 10, 0)
    // alarm invalid
    nul("будильник на 24:00"); nul("alarm at 25"); nul("alarm 7:75"); nul("alarm 13 pm"); nul("alarm 0 pm x"); nul("будильник"); nul("alarm 7:30:15 pm")
    nul("alarm 13 am")
    // timer
    param("таймер на 30 секунд", "timer", "seconds", 30)
    param("таймер на 5 минут", "timer", "seconds", 300)
    param("поставь таймер на 2 часа", "timer", "seconds", 7200)
    param("таймер на 1 час 30 минут", "timer", "seconds", 5400)
    param("таймер на полчаса", "timer", "seconds", 1800)
    param("set a timer for 10 minutes", "timer", "seconds", 600)
    param("timer 45 seconds", "timer", "seconds", 45)
    param("start timer for 2 hours", "timer", "seconds", 7200)
    param("timer for one hour", "timer", "seconds", 3600)
    param("taymer 5 daqiqa", "timer", "seconds", 300)
    param("5 daqiqaga taymer qo'y", "timer", "seconds", 300)
    param("taymer 30 soniya", "timer", "seconds", 30)
    param("taymer yarim soat", "timer", "seconds", 1800)
    param("таймер на пять минут", "timer", "seconds", 300)
    param("таймер на пятнадцать минут", "timer", "seconds", 900)
    param("таймер на минуту", "timer", "seconds", 60)
    param("timer 1 minute", "timer", "seconds", 60)
    nul("таймер"); nul("таймер на 0 минут"); nul("таймер на 25 часов"); nul("таймер на 5 яблок"); nul("timer 5")
    // number words 0..99 in 3 langs, via timer minutes
    val ru1 = listOf("один","два","три","четыре","пять","шесть","семь","восемь","девять")
    val en1 = listOf("one","two","three","four","five","six","seven","eight","nine")
    val uz1 = listOf("bir","ikki","uch","to'rt","besh","olti","yetti","sakkiz","to'qqiz")
    val ru10 = mapOf(20 to "двадцать", 30 to "тридцать", 40 to "сорок", 50 to "пятьдесят", 60 to "шестьдесят", 70 to "семьдесят", 80 to "восемьдесят", 90 to "девяносто")
    val en10 = mapOf(20 to "twenty", 30 to "thirty", 40 to "forty", 50 to "fifty", 60 to "sixty", 70 to "seventy", 80 to "eighty", 90 to "ninety")
    val uz10 = mapOf(20 to "yigirma", 30 to "o'ttiz", 40 to "qirq", 50 to "ellik", 60 to "oltmish", 70 to "yetmish", 80 to "sakson", 90 to "to'qson")
    for (i in 0..8) {
        param("таймер на ${ru1[i]} минут", "timer", "seconds", (i + 1) * 60)
        param("timer for ${en1[i]} minutes", "timer", "seconds", (i + 1) * 60)
        param("taymer ${uz1[i]} daqiqa", "timer", "seconds", (i + 1) * 60)
    }
    for ((n, w) in ru10) { param("таймер на $w минут", "timer", "seconds", n * 60); if (n <= 50) param("таймер на $w пять минут", "timer", "seconds", (n + 5) * 60) }
    for ((n, w) in en10) { param("timer for $w minutes", "timer", "seconds", n * 60); param("timer for $w five minutes", "timer", "seconds", (n + 5) * 60) }
    for ((n, w) in uz10) { param("taymer $w daqiqa", "timer", "seconds", n * 60); param("taymer $w besh daqiqa", "timer", "seconds", (n + 5) * 60) }
    param("таймер на девяносто девять секунд", "timer", "seconds", 99)
    param("timer ninety nine seconds", "timer", "seconds", 99)
    param("taymer to'qson to'qqiz soniya", "timer", "seconds", 99)
    param("таймер на одиннадцать минут", "timer", "seconds", 660)
    param("timer for nineteen minutes", "timer", "seconds", 1140)
    // open app
    param("открой ютуб", "open_app", "name", "ютуб")
    param("открой приложение телеграм", "open_app", "name", "телеграм")
    param("запусти камеру", "open_app", "name", "камеру")
    param("open youtube", "open_app", "name", "youtube")
    param("open the calculator", "open_app", "name", "calculator")
    param("launch spotify", "open_app", "name", "spotify")
    param("telegramni och", "open_app", "name", "telegram")
    param("kalkulyatorni ochib ber", "open_app", "name", "kalkulyator")
    param("youtube och", "open_app", "name", "youtube")
    param("открой ютуб музыка", "open_app", "name", "ютуб музыка")
    nul("открой"); nul("open 5"); nul("открой сайт google"); nul("открой настройки"); nul("open settings"); nul("открой ютуб и включи музыку"); nul("открой одну две три четыре")
    // call
    param("позвони маме", "call", "to", "маме")
    param("позвони Алишеру", "call", "to", "алишеру")
    param("call mom", "call", "to", "mom")
    param("call John Smith", "call", "to", "john smith")
    param("dial dad", "call", "to", "dad")
    param("onamga qo'ng'iroq qil", "call", "to", "onam")
    param("Jasurga qo'ng'iroq qil", "call", "to", "jasur")
    nul("позвони"); nul("call"); nul("alarm 13 pm"); alarm("alarm 15 pm".replace("15 pm","3 pm"), 15, 0)
    // nav
    intent("назад", "back"); intent("go back", "back"); intent("orqaga", "back")
    intent("домой", "home"); intent("go home", "home"); intent("home", "home"); intent("на главный экран", "home"); intent("bosh ekran", "home")
    intent("скриншот", "screenshot"); intent("сделай скриншот", "screenshot"); intent("take a screenshot", "screenshot"); intent("skrinshot ol", "screenshot")
    intent("недавние приложения", "recents"); intent("recent apps", "recents")
    intent("уведомления", "notifications"); intent("open notifications", "notifications"); intent("bildirishnomalar", "notifications")
    intent("быстрые настройки", "quick_settings"); intent("quick settings", "quick_settings")
    intent("заблокируй экран", "lock"); intent("lock screen", "lock")
    // quiet
    param("спрячься", "quiet", "on", true); param("hide", "quiet", "on", true); param("не мешай", "quiet", "on", true); param("yashirin", "quiet", "on", true)
    param("покажись", "quiet", "on", false); param("show yourself", "quiet", "on", false); param("появись", "quiet", "on", false); param("chiqing", "quiet", "on", false)
    // move / size
    param("встань вверх", "move", "where", "top"); param("перейди вниз", "move", "where", "bottom")
    param("move left", "move", "where", "left"); param("move right", "move", "where", "right")
    param("перейди в центр", "move", "where", "center"); param("move to the center", "move", "where", "center")
    param("move to the top left", "move", "where", "top_left"); param("перейди вверх влево", "move", "where", "top_left")
    param("move bottom right", "move", "where", "bottom_right")
    param("tepaga bor", "move", "where", "top")
    param("стань больше", "size", "value", "bigger"); param("become smaller", "size", "value", "smaller"); param("be bigger", "size", "value", "bigger")
    param("стань обычный", "size", "value", "normal"); param("kattaroq bo'l", "size", "value", "bigger"); param("уменьшись", "size", "value", "smaller")
    nul("move up and down"); nul("move left right")
    // time/date/battery
    intent("сколько времени", "time"); intent("который час", "time"); intent("what time is it", "time"); intent("soat necha", "time"); intent("what's the time", "time")
    intent("какое сегодня число", "date"); intent("какая сегодня дата", "date"); intent("what's the date", "date"); intent("what day is it", "date"); intent("bugun nechinchi sana", "date")
    intent("сколько заряда", "battery"); intent("какой заряд батареи", "battery"); intent("battery", "battery"); intent("how much battery", "battery"); intent("batareya qancha", "battery")
    intent("сколько сейчас времени?", "time"); intent("What time is it?", "time")
    // negatives
    for (t in listOf(
        "как включить фонарик на айфоне", "позвони маме и скажи что я опоздаю", "how do I turn on the flashlight", "почему фонарик не работает",
        "расскажи анекдот", "привет как дела", "что такое фонарик", "я устал сегодня", "tell me a joke", "who is the president",
        "qanday qilib chiroqni yoqaman", "nima gap", "включи музыку", "поставь музыку", "включи фонарик и позвони маме", "turn on the flashlight and call mom",
        "будильник на 7 и таймер на 5 минут", "громче и потом тише", "открой ютуб, потом позвони маме", "позвони маме, потом папе",
        "can you turn on the flashlight?", "фонарик?", "включи свет", "абракадабра", "blah blah", "фонарик на айфоне", "alarm clock is broken",
        "расскажи про будильник", "make me a sandwich", "позвони маме скажи что опоздаю", "позвони маме чтобы она знала", "call mom if she is home",
        "", "   ", "?!", "...", "включи", "выключи", "turn on", "волшебное слово", "mute the microphone please now and always forever ok", "громкость на ноль целых пять"
    )) nul(t)
    nul("a".repeat(200)); nul("включи фонарик ".repeat(20)); nul("один ".repeat(15))
    nul("а а а а а а а а а а а")
    // normalize
    check("norm ё", QuickIntent.normalize("Ёлка") == "елка")
    check("norm apos", QuickIntent.normalize("To‘rt") == "to'rt")
    check("norm time", QuickIntent.normalize("7:30") == "7 30")
    check("norm pct", QuickIntent.normalize("50%").trim() == "50 percent")
    check("norm a.m.", QuickIntent.normalize("7 a.m.") == "7 am", QuickIntent.normalize("7 a.m."))
    check("norm uz cyr", QuickIntent.normalize("Ўқитувчи") == "укитувчи")
    check("wake word alone", P("пожалуйста") == null)
    check("wake word stripped", P("bubby flashlight on")?.intent == "flashlight")
}

fun contacts() {
    fun r(n: String, num: String = "+998901234567", star: Boolean = false, times: Int = 0) = Contacts.Row(n, num, 2, null, star, times)
    fun top(rows: List<Contacts.Row>, q: String) = Contacts.rank(rows, q).firstOrNull()?.name
    val rows = listOf(r("Alisher"), r("Jasur Karimov", "+998901111111"), r("Oktam", "+998902222222"), r("Мама", "+998903333333"),
        r("Dad", "+998904444444"), r("Sardor", "+998905555555"), r("Bobur Aka", "+998906666666"), r("Иван Петров", "+79991112233"), r("Азиза", "+998907777777"))
    check("Алишер->Alisher", top(rows, "Алишер") == "Alisher", "${top(rows, "Алишер")}")
    check("Alisher->Alisher", top(rows, "alisher") == "Alisher")
    check("Алишеру?", top(rows, "алишер") == "Alisher")
    check("Жасур->Jasur", top(rows, "Жасур") == "Jasur Karimov", "${top(rows, "Жасур")}")
    check("jasur", top(rows, "jasur") == "Jasur Karimov")
    check("Jasur Karimov full", top(rows, "жасур каримов") == "Jasur Karimov")
    check("Ўктам->Oktam", top(rows, "Ўктам") == "Oktam", "${top(rows, "Ўктам")}")
    check("Октам->Oktam", top(rows, "Октам") == "Oktam")
    check("Иван->Иван Петров", top(rows, "иван") == "Иван Петров")
    check("Ivan->Иван Петров", top(rows, "ivan") == "Иван Петров")
    check("Петров", top(rows, "Petrov") == "Иван Петров")
    check("Azizа latin", top(rows, "aziza") == "Азиза")
    for (w in listOf("мама", "маме", "маму", "onam", "oyi", "mom", "mother", "mum", "ona", "onajon", "oyim")) check("family mom '$w'", top(rows, w) == "Мама", "${top(rows, w)}")
    for (w in listOf("папа", "dad", "papa", "father", "ota", "otam")) check("family dad '$w'", top(rows, w) == "Dad", "${top(rows, w)}")
    val fam2 = listOf(r("Onam"), r("Dadam", "+998909999999"))
    check("мама->Onam", top(fam2, "мама") == "Onam")
    check("папа->Dadam", top(fam2, "папа") == "Dadam")
    check("aka family", top(rows, "aka") == "Bobur Aka", "${top(rows, "aka")}")
    // typos
    check("typo Sardar", top(rows, "sardar") == "Sardor", "${top(rows, "sardar")}")
    check("typo Alisheer", top(rows, "alisheer") == "Alisher", "${top(rows, "alisheer")}")
    check("typo Bobor", top(rows, "bobor") == "Bobur Aka" || top(rows, "bobor") == null, "${top(rows, "bobor")}")
    check("no match xyz", Contacts.rank(rows, "zxqvw").isEmpty())
    check("short a no match", Contacts.rank(rows, "z").isEmpty())
    // ties + starred
    val tie = listOf(r("Ali Valiyev", "+998901000001"), r("Ali Karimov", "+998901000002", star = true))
    check("starred wins", top(tie, "ali") == "Ali Karimov", "${top(tie, "ali")}")
    val tie2 = listOf(r("Ali B", "+998901000001"), r("Ali A", "+998901000002"))
    check("tie alphabetical", top(tie2, "ali") == "Ali A")
    check("times bonus", top(listOf(r("Ali X", "+998901000001"), r("Ali Y", "+998901000002", times = 20)), "ali") == "Ali Y")
    check("exact beats prefix", top(listOf(r("Alisher"), r("Ali")), "ali") == "Ali")
    check("max respected", Contacts.rank(listOf(r("A1 Ali", "+99890100001"), r("A2 Ali", "+99890100002"), r("A3 Ali", "+99890100003")), "ali", 2).size == 2)
    check("max 0", Contacts.rank(rows, "alisher", 0).isEmpty())
    check("dedupe same number", Contacts.rank(listOf(r("Ali", "+998 90 100 00 01"), r("Ali", "90-100-00-01")), "ali").size == 1)
    check("two numbers same name", Contacts.rank(listOf(r("Ali", "+998901000001"), r("Ali", "+998901000002")), "ali").size == 2)
    check("empty query", Contacts.rank(rows, "").isEmpty() && Contacts.rank(rows, "  ").isEmpty())
    check("number query empty", Contacts.rank(rows, "+998901234567").isEmpty())
    check("empty digits skipped", Contacts.rank(listOf(r("Ali", "abc")), "ali").isEmpty())
    check("number normalised in out", Contacts.rank(listOf(r("Ali", "+998 (90) 123-45-67")), "ali")[0].number == "+998901234567")
    // number helpers
    check("isNumber +", Contacts.isNumber("+998 90 123 45 67")); check("isNumber 112", Contacts.isNumber("112")); check("isNumber ab", !Contacts.isNumber("ab1"))
    check("isNumber 12", !Contacts.isNumber("12")); check("isNumber empty", !Contacts.isNumber("")); check("isNumber (90)", Contacts.isNumber("(90) 123"))
    check("isNumber nbsp", Contacts.isNumber("90 123 45"))
    check("normNum", Contacts.normalizeNumber("+998 (90) 123-45-67") == "+998901234567")
    check("normNum mid plus", Contacts.normalizeNumber("90+12") == "9012")
    check("normNum empty", Contacts.normalizeNumber("") == "")
    // NameText
    check("lev eq", NameText.lev("abc", "abc") == 0); check("lev sub", NameText.lev("abc", "abd") == 1); check("lev ins", NameText.lev("abc", "abcd") == 1)
    check("lev empty", NameText.lev("", "abc") == 3 && NameText.lev("abc", "") == 3); check("lev kitten", NameText.lev("kitten", "sitting") == 3)
    check("lev sym", NameText.lev("flaw", "lawn") == NameText.lev("lawn", "flaw"))
    check("norm lower", NameText.norm("ALISHER") == "alisher"); check("norm diacritics", NameText.norm("Şükrü") == "sukru", NameText.norm("Şükrü"))
    check("norm punct", NameText.norm("O'Neil, John.") == "o'neil john"); check("norm keep cyr", NameText.norm("Алишер") == "алишер")
    check("norm apos", NameText.norm("Oʻktam") == "o'ktam"); check("norm yo", NameText.norm("Фёдор") == "федор"); check("norm empty", NameText.norm("") == "")
    check("key alisher", NameText.key("Алишер") == NameText.key("Alisher"), "${NameText.key("Алишер")} ${NameText.key("Alisher")}")
    check("key jasur", NameText.key("Жасур") == NameText.key("Jasur"), "${NameText.key("Жасур")} ${NameText.key("Jasur")}")
    check("key oktam", NameText.key("Ўктам") == NameText.key("Oktam"), "${NameText.key("Ўктам")} ${NameText.key("Oktam")}")
    check("key oktam2", NameText.key("Oʻktam") == NameText.key("Oktam"))
    check("key sherzod", NameText.key("Шерзод") == NameText.key("Sherzod"), "${NameText.key("Шерзод")} ${NameText.key("Sherzod")}")
    check("key xurshid", NameText.key("Хуршид") == NameText.key("Xurshid"), "${NameText.key("Хуршид")} ${NameText.key("Xurshid")}")
    check("key khurshid", NameText.key("Khurshid") == NameText.key("Xurshid"))
    check("key qodir", NameText.key("Қодир") == NameText.key("Qodir"))
    check("key qodir k", NameText.key("Kodir") == NameText.key("Qodir"))
    check("key dilshod", NameText.key("Дилшод") == NameText.key("Dilshod"))
    check("key anvar", NameText.key("Анвар") == NameText.key("Anvar"))
    check("key vova w", NameText.key("Wova") == NameText.key("Вова"))
    check("key dup", NameText.key("Anna") == NameText.key("Ана"), "${NameText.key("Anna")} ${NameText.key("Ана")}")
    check("key multiword", NameText.key("Иван Петров") == "ivan petrov", NameText.key("Иван Петров"))
    check("key mama", NameText.key("мама") == NameText.key("mama"))
    check("key maxsus ъ", NameText.key("Объект") == "obekt", NameText.key("Объект"))
    check("key empty", NameText.key("") == "")
}

fun apps() {
    fun a(label: String, pkg: String) = AppResolver.AppEntry(label, NameText.norm(label), NameText.key(label), pkg)
    val list = listOf(a("YouTube", "com.google.android.youtube"), a("Telegram", "org.telegram.messenger"), a("Calculator", "com.google.android.calculator"),
        a("Settings", "com.android.settings"), a("Chrome", "com.android.chrome"), a("YT Music", "com.google.android.apps.youtube.music"),
        a("Camera", "com.sec.android.app.camera"), a("Instagram", "com.instagram.android"), a("Payme", "uz.dida.payme"), a("Моё приложение", "x.my.app"),
        a("WhatsApp", "com.whatsapp"), a("Gallery", "com.sec.android.gallery3d"), a("Clock", "com.google.android.deskclock"), a("Play Store", "com.android.vending"))
    fun pk(q: String) = AppResolver.resolveIn(list, q)?.pkg
    for (q in listOf("ютуб", "yutub", "youtube", "YouTube", "you tube", "ютьюб", "yutube")) check("yt '$q'", pk(q) == "com.google.android.youtube", "${pk(q)}")
    for (q in listOf("ютуб музыка", "youtube music", "yt music", "yutub musiqa")) check("ytm '$q'", pk(q) == "com.google.android.apps.youtube.music", "${pk(q)}")
    for (q in listOf("телеграм", "телеграмм", "telegram", "телега", "tg")) check("tg '$q'", pk(q) == "org.telegram.messenger", "${pk(q)}")
    for (q in listOf("kalkulyator", "калькулятор", "calculator", "калькулатор")) check("calc '$q'", pk(q) == "com.google.android.calculator", "${pk(q)}")
    for (q in listOf("sozlamalar", "настройки", "settings", "sozlama")) check("settings '$q'", pk(q) == "com.android.settings", "${pk(q)}")
    for (q in listOf("камера", "kamera", "camera", "камеру")) check("cam '$q'", pk(q) == "com.sec.android.app.camera", "${pk(q)}")
    for (q in listOf("инстаграм", "instagram", "инста")) check("insta '$q'", pk(q) == "com.instagram.android", "${pk(q)}")
    for (q in listOf("ватсап", "whatsapp", "vatsap")) check("wa '$q'", pk(q) == "com.whatsapp", "${pk(q)}")
    for (q in listOf("хром", "chrome", "браузер", "brauzer")) check("chrome '$q'", pk(q) == "com.android.chrome", "${pk(q)}")
    for (q in listOf("галерея", "galereya", "фото")) check("gal '$q'", pk(q) == "com.sec.android.gallery3d", "${pk(q)}")
    for (q in listOf("часы", "будильник", "clock")) check("clock '$q'", pk(q) == "com.google.android.deskclock", "${pk(q)}")
    for (q in listOf("play store", "плей маркет", "google play")) check("play '$q'", pk(q) == "com.android.vending", "${pk(q)}")
    for (q in listOf("пейме", "payme")) check("payme '$q'", pk(q) == "uz.dida.payme", "${pk(q)}")
    check("label cyr", pk("моё приложение") == "x.my.app", "${pk("моё приложение")}")
    check("typo Instagrm", pk("instagrm") == "com.instagram.android", "${pk("instagrm")}")
    check("not installed spotify", pk("spotify") == null, "${pk("spotify")}")
    check("not installed tiktok", pk("тикток") == null, "${pk("тикток")}")
    check("garbage", pk("zzzzqqq") == null)
    check("empty", pk("") == null && pk("   ") == null)
    check("empty list", AppResolver.resolveIn(emptyList(), "youtube") == null)
    check("alias score", AppResolver.resolveIn(list, "ютуб")?.score == 100)
    check("alias in sentence", pk("ютуб пожалуйста") == "com.google.android.youtube", "${pk("ютуб пожалуйста")}")
}

fun mathTools() {
    fun c(e: String) = try { MathTools.fmt(MathTools.calc(e)) } catch (x: Exception) { "ERR" }
    check("calc add", c("2+2") == "4")
    check("calc precedence", c("2+3*4") == "14")
    check("calc parens", c("12*(3+4)/5") == "16.8")
    check("calc -2^2", c("-2^2") == "-4")
    check("calc float", c("0.1+0.2") == "0.3")
    check("calc sqrt", c("sqrt(144)") == "12")
    check("calc div0", c("1/0") == "ERR")
    check("calc garbage", c("2+*3") == "ERR")
    check("convert km mi", MathTools.convert(5.0, "km", "mi") == "5 km = 3.106855961 mi")
    check("convert C F", MathTools.convert(100.0, "C", "F") == "100 °C = 212 °F")
    check("convert ru", MathTools.convert(10.0, "км", "миль") == "10 km = 6.213711922 mi")
    check("convert uz", MathTools.convert(2.0, "soat", "daqiqa") == "2 h = 120 min")
    check("convert mixed", try { MathTools.convert(1.0, "kg", "km"); false } catch (x: IllegalArgumentException) { true })
}

fun comms() {
    val now = 1_000_000_000L
    val on = ReplyRules.until(now, 2)
    fun rep(until: Long = on, canReply: Boolean = true, msg: Boolean = true, group: Boolean = false, num: Boolean = false, last: Long? = null) =
        ReplyRules.shouldReply(until, now, canReply, msg, group, num, last)
    check("autoreply private message", rep())
    check("autoreply off", !rep(until = 0))
    check("autoreply expired", !rep(until = now - 1))
    check("autoreply no reply button", !rep(canReply = false))
    check("autoreply group", !rep(group = true))
    check("autoreply unknown number", !rep(num = true))
    check("autoreply cooldown", !rep(last = now - 10 * 60_000L))
    check("autoreply after cooldown", rep(last = now - 61 * 60_000L))
    check("autoreply max 12h", ReplyRules.until(now, 99) == now + 12 * 3_600_000L)
    val rows = listOf("Alisher Karimov" to "a@mail.uz", "Alisher Valiyev" to "v@gmail.com", "Dilnoza" to "d@mail.ru", "Бобур" to "b@gmail.com")
    check("mail unique name", Mail.rank(rows, "Dilnoza").map { it.address } == listOf("d@mail.ru"))
    check("mail two matches", Mail.rank(rows, "Alisher").size == 2)
    check("mail cyrillic vs latin", Mail.rank(rows, "Bobur").firstOrNull()?.address == "b@gmail.com")
    check("mail no match", Mail.rank(rows, "Zafar").isEmpty())
}

fun pairing() {
    check("pair proof matches the Node HMAC", com.example.buddy.PairProof.proof("123456", "ab".repeat(32), "00112233445566778899aabbccddeeff") == "5f340d585a5c9e96680612e01da930742b91cca112759506a9e907b488fb4fcd")
    check("pair proof lowercases the fingerprint", com.example.buddy.PairProof.proof("123456", "AB".repeat(32), "00112233445566778899aabbccddeeff") == "5f340d585a5c9e96680612e01da930742b91cca112759506a9e907b488fb4fcd")
    check("pair code keeps only digits", com.example.buddy.PairProof.cleanCode("12 34-56") == "123456")
}

fun groq() {
    val ids = listOf("llama-3.3-70b-versatile", "openai/gpt-oss-20b", "qwen/qwen3.8-27b", "openai/gpt-oss-120b")
    check("groq uz: newest Qwen first", com.example.buddy.ui.GroqModels.pick(ids, "uz") == listOf("qwen/qwen3.8-27b", "openai/gpt-oss-120b", "openai/gpt-oss-20b"))
    check("groq en: gpt-oss-120b first", com.example.buddy.ui.GroqModels.pick(ids, "en") == listOf("openai/gpt-oss-120b", "qwen/qwen3.8-27b", "openai/gpt-oss-20b"))
    check("groq uz without Qwen", com.example.buddy.ui.GroqModels.pick(listOf("openai/gpt-oss-120b", "llama-3.3-70b-versatile"), "uz") == listOf("openai/gpt-oss-120b", "llama-3.3-70b-versatile"))
    check("groq empty", com.example.buddy.ui.GroqModels.pick(emptyList(), "en").isEmpty())
}

fun agentsV3() {
    val P = AgentProto
    // caps: only the ones this phone knows; none at all for an old bridge
    check("caps old bridge", P.parseCaps(null).isEmpty())
    check("caps filter", P.parseCaps(listOf("details", "chat", 3, "diffs")) == setOf("details", "diffs"))
    check("caps not a list", P.parseCaps("details").isEmpty())
    // cleaning
    check("clean controls", P.clean("a\u0000b\u202Ec\u200Bd", 50) == "abcd")
    check("clean newline single", P.clean("a\nb\r\nc", 50) == "a b c")
    check("clean newline multi", P.clean("a\n\n\n\nb", 50, multiline = true) == "a\n\nb")
    check("clean spaces", P.clean("  a   b\t c  ", 50) == "a b c")
    check("clean cut", P.clean("abcdef", 3) == "abc")
    check("clean no split pair", P.clean("ab\uD83D\uDE00", 3) == "ab")
    check("clean keep pair", P.clean("ab\uD83D\uDE00", 4) == "ab\uD83D\uDE00")
    check("clean lone surrogate", P.clean("a\uD800b", 9) == "ab")
    check("clean html stays text", P.clean("<b>x</b>", 20) == "<b>x</b>")
    check("clean keepSpaces", P.clean("    if (a)\tb", 50, keepSpaces = true) == "    if (a) b")
    check("clean null", P.clean(null, 5) == "")
    check("basename unix", P.baseName("/home/u/proj/src/Main.kt", 80) == "Main.kt")
    check("basename win", P.baseName("C:\\work\\proj\\", 64) == "proj")
    check("basename cut", P.baseName("x".repeat(100), 80).length == 80)
    // sessions: details only with the capability
    val raw = mapOf("pillId" to "p1", "agent" to "codex", "state" to "finished", "statusText" to "ok\nfine", "stepIndex" to 3, "stepCount" to 8.0,
        "project" to "/Users/me/bubby", "steps" to (1..30).map { "step $it" } + listOf("", 5), "finalLine" to "All done",
        "files" to listOf(mapOf("id" to 7, "name" to "src/a/UiActivity.kt", "added" to 12, "removed" to 3, "isNew" to true),
            mapOf("id" to "x9", "name" to "b.txt", "added" to -4, "removed" to 1e12, "tooLarge" to true),
            mapOf("id" to 7, "name" to "dup.kt"), mapOf("id" to 1.5, "name" to "frac.kt"), mapOf("id" to 8, "name" to "/"), mapOf("name" to "noid.kt")))
    val v2 = P.parseSession(raw, emptySet())!!
    check("session v2 no details", v2.project == "" && v2.steps.isEmpty() && v2.finalLine == "" && v2.files.isEmpty())
    check("session v2 fields", v2.agent == "codex" && v2.state == "finished" && v2.statusText == "ok fine" && v2.stepIndex == 3 && v2.stepCount == 8)
    val v3 = P.parseSession(raw, setOf("details", "diffs"))!!
    check("session project is a folder name", v3.project == "bubby")
    check("session last 20 steps", v3.steps.size == 20 && v3.steps.first() == "step 11" && v3.steps.last() == "step 30")
    check("session final line", v3.finalLine == "All done")
    check("session files valid only", v3.files.map { it.name } == listOf("UiActivity.kt", "b.txt"), "${v3.files}")
    check("session file numeric id", v3.files[0].idNum == 7L && v3.files[0].id == "7" && v3.files[0].isNew)
    check("session file string id", v3.files[1].idNum == null && v3.files[1].id == "x9" && v3.files[1].tooLarge)
    check("session file counts clamped", v3.files[1].added == 0 && v3.files[1].removed == 1_000_000)
    check("session details only", P.parseSession(raw, setOf("details"))!!.files.isEmpty())
    check("session no pill", P.parseSession(mapOf("agent" to "x"), emptySet()) == null)
    check("session unknown state", P.parseSession(mapOf("pillId" to "p", "state" to "evil"), emptySet())!!.state == "idle")
    check("session long step cut", P.parseSession(mapOf("pillId" to "p", "steps" to listOf("s".repeat(500))), setOf("details"))!!.steps[0].length == 200)
    check("session 25 files capped", P.parseFiles((1..25).map { mapOf("id" to it, "name" to "f$it") }).size == 20)
    // questions
    val now = 1_000_000_000L
    fun opt(l: String, d: String = "") = mapOf("label" to l, "description" to d)
    fun q(vararg items: Map<String, Any?>, created: Any? = now - 1000, fp: Any? = "fp1") =
        mapOf("pillId" to "p1", "fingerprint" to fp, "createdAt" to created, "questions" to items.toList())
    val q1 = mapOf("question" to "Which db?", "multiSelect" to false, "options" to listOf(opt("Postgres", "fast"), opt("SQLite")))
    val q2 = mapOf("question" to "Which tests?", "multiSelect" to true, "options" to listOf(opt("unit"), opt("ui"), opt("e2e")))
    val good = P.parseQuestion(q(q1, q2), now)
    check("question parses", good != null && good.items.size == 2 && good.items[1].multiSelect && good.items[0].options[0].description == "fast")
    check("question too old", P.parseQuestion(q(q1, created = now - 115_000), now) == null)
    check("question almost too old", P.parseQuestion(q(q1, created = now - 114_000), now) != null)
    check("question future clock", P.parseQuestion(q(q1, created = now + 60_000), now) != null)
    check("question no createdAt", P.parseQuestion(q(q1, created = null), now)?.createdAt == now)
    check("question no fingerprint", P.parseQuestion(q(q1, fp = ""), now) == null)
    check("question fingerprint not text", P.parseQuestion(q(q1, fp = 5), now) == null)
    check("question none", P.parseQuestion(q(), now) == null)
    check("question 5 too many", P.parseQuestion(q(q1, q1, q1, q1, q1), now) == null)
    check("question 4 ok", P.parseQuestion(q(q1, q1, q1, q1), now) != null)
    check("question 9 options", P.parseQuestion(q(mapOf("question" to "x", "options" to (1..9).map { opt("o$it") })), now) == null)
    check("question 8 options", P.parseQuestion(q(mapOf("question" to "x", "options" to (1..8).map { opt("o$it") })), now) != null)
    check("question no options", P.parseQuestion(q(mapOf("question" to "x", "options" to emptyList<Any>())), now) == null)
    check("question text 501", P.parseQuestion(q(mapOf("question" to "x".repeat(501), "options" to listOf(opt("a")))), now) == null)
    check("question text 500", P.parseQuestion(q(mapOf("question" to "x".repeat(500), "options" to listOf(opt("a")))), now) != null)
    check("question blank text", P.parseQuestion(q(mapOf("question" to " \u200B ", "options" to listOf(opt("a")))), now) == null)
    check("question label 121", P.parseQuestion(q(mapOf("question" to "x", "options" to listOf(opt("l".repeat(121))))), now) == null)
    check("question label 120", P.parseQuestion(q(mapOf("question" to "x", "options" to listOf(opt("l".repeat(120))))), now) != null)
    check("question description 301", P.parseQuestion(q(mapOf("question" to "x", "options" to listOf(opt("a", "d".repeat(301))))), now) == null)
    check("question duplicate labels", P.parseQuestion(q(mapOf("question" to "x", "options" to listOf(opt("a"), opt("a")))), now) == null)
    check("question labels alike once cleaned", P.parseQuestion(q(mapOf("question" to "x", "options" to listOf(opt("a"), opt("a\u200B")))), now) == null)
    check("question empty label", P.parseQuestion(q(mapOf("question" to "x", "options" to listOf(opt("")))), now) == null)
    check("question multiSelect not bool", P.parseQuestion(q(mapOf("question" to "x", "multiSelect" to "yes", "options" to listOf(opt("a")))), now) == null)
    check("question label kept exact", P.parseQuestion(q(mapOf("question" to "x", "options" to listOf(opt("  Yes\u202E  ")))), now)!!.items[0].options[0].let { it.label == "  Yes\u202E  " && it.shown == "Yes" })
    check("question expiry", P.msLeft(now - 15_000, now) == 100_000L && P.expired(now - 115_000, now))
    // answers
    val g = good!!
    check("picks valid", P.picksValid(g, listOf(listOf("Postgres"), listOf("unit", "e2e"))))
    check("picks wrong count", !P.picksValid(g, listOf(listOf("Postgres"))))
    check("picks two for single", !P.picksValid(g, listOf(listOf("Postgres", "SQLite"), listOf("ui"))))
    check("picks none for multi", !P.picksValid(g, listOf(listOf("Postgres"), emptyList())))
    check("picks unknown label", !P.picksValid(g, listOf(listOf("postgres"), listOf("ui"))))
    check("picks duplicate", !P.picksValid(g, listOf(listOf("SQLite"), listOf("ui", "ui"))))
    var sel = P.emptySelection(g)
    check("send disabled at start", P.picksFor(g, sel) == null)
    sel = P.toggle(g, sel, 0, 0); sel = P.toggle(g, sel, 0, 1)
    check("single choice replaces", sel[0] == setOf(1))
    check("send disabled until every question", P.picksFor(g, sel) == null)
    sel = P.toggle(g, sel, 1, 2); sel = P.toggle(g, sel, 1, 0)
    check("multi toggles", sel[1] == setOf(0, 2))
    check("send enabled", P.picksFor(g, sel) == listOf(listOf("SQLite"), listOf("unit", "e2e")))
    sel = P.toggle(g, sel, 1, 0); sel = P.toggle(g, sel, 1, 2)
    check("multi untoggle to none disables", sel[1].isEmpty() && P.picksFor(g, sel) == null)
    check("toggle out of range", P.toggle(g, sel, 5, 0) == sel && P.toggle(g, sel, 0, 9) == sel)
    // diffs
    fun part(i: Int, n: Int, lines: List<Any?>, extra: Map<String, Any?> = emptyMap()) =
        mapOf("pillId" to "p1", "fileId" to 7, "name" to "a/b/Main.kt", "added" to 3, "removed" to 1, "part" to i, "parts" to n, "lines" to lines) + extra
    val d0 = P.parseDiff(part(0, 2, listOf(listOf("@", "@@ 10"), listOf("+", "new\tline"), listOf("?", "ctx"), listOf("-", "x".repeat(450)), "bad")))!!
    check("diff parse", d0.name == "Main.kt" && d0.fileId == "7" && d0.lines.size == 4 && d0.lines[2].kind == ' ' && d0.lines[1].text == "new line")
    check("diff long line cut", d0.lines[3].text.length == 400 && d0.truncated)
    check("diff too many parts", P.parseDiff(part(0, 3, emptyList())) == null)
    check("diff part out of range", P.parseDiff(part(2, 2, emptyList())) == null)
    check("diff part not whole", P.parseDiff(part(0, 2, emptyList(), mapOf("part" to 0.5))) == null)
    check("diff no file id", P.parseDiff(part(0, 1, emptyList(), mapOf("fileId" to null))) == null)
    check("diff 150 lines cut to 100", P.parseDiff(part(0, 1, (1..150).map { listOf("+", "l$it") }))!!.let { it.lines.size == 100 && it.truncated })
    val asm = DiffAssembler("p1", "7")
    val d1 = P.parseDiff(part(1, 2, listOf(listOf("+", "second"))))!!
    check("assembler other file ignored", !asm.add(P.parseDiff(part(0, 1, emptyList(), mapOf("fileId" to 8)))!!))
    check("assembler part 1 first", asm.add(d1) && !asm.complete)
    check("assembler repeat ignored", !asm.add(d1))
    check("assembler contradicting parts ignored", !asm.add(P.parseDiff(part(0, 1, emptyList()))!!))
    check("assembler complete in order", asm.add(d0) && asm.complete && asm.lines().first().text == "@@ 10" && asm.lines().last().text == "second" && asm.truncated)
    check("assembler done takes nothing", !asm.add(d0))
    asm.clear()
    check("assembler forgets", asm.lines().isEmpty())
    val gone = DiffAssembler("p1", "7")
    check("assembler gone", gone.add(P.parseDiff(part(0, 1, listOf(listOf("+", "x")), mapOf("gone" to true)))!!) && gone.complete && gone.gone && gone.lines().isEmpty())
    val big = DiffAssembler("p1", "x9")
    check("assembler tooLarge", big.add(P.parseDiff(mapOf("pillId" to "p1", "fileId" to "x9", "tooLarge" to true))!!) && big.complete && big.tooLarge)
    // the phone keeps 20 getDiff in 10 s
    val rw = RateWindow(20, 10_000L)
    check("rate 20 ok", (1..20).all { rw.tryTake(1000L + it) })
    check("rate 21st refused", !rw.tryTake(2000L))
    check("rate after window", rw.tryTake(11_002L))
}

fun main() {
    quick(); contacts(); apps(); mathTools(); comms(); groq(); pairing(); agentsV3(); agentTests(); if (System.getenv("PROBE") != null) probe()
    println("RESULT: $pass passed, $fail failed (${pass + fail} assertions)")
    if (fail > 0) System.exit(1)
}
