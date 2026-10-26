package com.example.buddy

import android.content.Context
import android.os.BatteryManager
import org.json.JSONArray
import org.json.JSONObject
import java.util.Calendar
import java.util.Locale

/** Result of an offline command: spoken confirmation, mascot emotion, and actions for [Actions.run]. */
class Quick(val say: String, val emotion: String, val actions: JSONArray)

/** Android-free parse result. [intent] is an action name ("alarm", "flashlight", ...) or a local one ("time", "date", "battery"). Params starting with "_" are hints, not action fields. */
internal class ParsedQuick(val intent: String, val params: Map<String, Any> = emptyMap())

/**
 * Offline command grammar (ru / uz / en). High precision: any unknown word in the sentence means "no match"
 * so the cloud AI handles it.
 */
object QuickIntent {

    fun match(ctx: Context, text: String): Quick? {
        val now = Calendar.getInstance()
        val p = parse(text, now.get(Calendar.HOUR_OF_DAY), now.get(Calendar.MINUTE)) ?: return null
        return try { build(ctx, p, now) } catch (_: Exception) { null }
    }

    // ---------------------------------------------------------------- normalisation

    private val RX_APOS = Regex("[ʻʼ’‘`´ʹ]")
    private val RX_TIMESEP = Regex("(\\d)[:.,](\\d)")
    private val RX_JUNK = Regex("[^\\p{L}\\p{N}'\\s]")
    private val RX_EDGE_APOS = Regex("(?<!\\p{L})'|'(?!\\p{L})")
    private val RX_DL = Regex("(\\d)(\\p{L})")
    private val RX_LD = Regex("(\\p{L})(\\d)")
    private val RX_WS = Regex("\\s+")
    private val RX_AMPM = Regex("\\b([ap]) m\\b")

    private val WAKE = hashSetOf(
        "бубби", "буби", "бабби", "bubby", "buby", "bubbi", "пожалуйста", "пожалуста", "плиз", "iltimos",
        "please", "pls", "hey", "эй", "слушай", "ну", "давай", "окей", "ok", "okay", "привет", "hi", "hello"
    )
    private val DROP = hashSetOf("the", "my", "мне", "ка")

    internal fun normalize(text: String): String {
        var s = text.lowercase(Locale.ROOT)
            .replace('ё', 'е').replace('ў', 'у').replace('қ', 'к').replace('ғ', 'г').replace('ҳ', 'х')
        s = RX_APOS.replace(s, "'")
        s = s.replace("%", " percent ")
        s = RX_TIMESEP.replace(s, "$1 $2")
        s = RX_JUNK.replace(s, " ")
        s = RX_EDGE_APOS.replace(s, " ")
        s = RX_DL.replace(s, "$1 $2")
        s = RX_LD.replace(s, "$1 $2")
        s = RX_WS.replace(s, " ").trim()
        return RX_AMPM.replace(s, "$1m")
    }

    // ---------------------------------------------------------------- number words

    private val NUM = HashMap<String, Int>()
    private val TENSW = HashSet<String>()
    private val NUM_SUF = arrayOf("ga", "da", "dan", "ta", "ni", "ning")

    private fun num(v: Int, vararg ws: String) { for (w in ws) NUM[w] = v }
    private fun tens(v: Int, vararg ws: String) { for (w in ws) { NUM[w] = v; TENSW.add(w) } }

    init {
        num(0, "ноль", "нуль", "nol", "zero")
        num(1, "один", "одна", "одну", "bir", "бир", "one")
        num(2, "два", "две", "ikki", "икки", "two")
        num(3, "три", "uch", "уч", "three")
        num(4, "четыре", "to'rt", "турт", "four")
        num(5, "пять", "besh", "беш", "five")
        num(6, "шесть", "olti", "олти", "six")
        num(7, "семь", "yetti", "етти", "seven")
        num(8, "восемь", "sakkiz", "саккиз", "eight")
        num(9, "девять", "to'qqiz", "туккиз", "nine")
        num(10, "десять", "ten")
        num(11, "одиннадцать", "eleven")
        num(12, "двенадцать", "twelve")
        num(13, "тринадцать", "thirteen")
        num(14, "четырнадцать", "fourteen")
        num(15, "пятнадцать", "fifteen")
        num(16, "шестнадцать", "sixteen")
        num(17, "семнадцать", "seventeen")
        num(18, "восемнадцать", "eighteen")
        num(19, "девятнадцать", "nineteen")
        tens(10, "o'n", "ун")
        tens(20, "двадцать", "twenty", "yigirma", "йигирма")
        tens(30, "тридцать", "thirty", "o'ttiz", "уттиз")
        tens(40, "сорок", "forty", "qirq", "кирк")
        tens(50, "пятьдесят", "fifty", "ellik", "эллик")
        tens(60, "шестьдесят", "sixty", "oltmish", "олтмиш")
        tens(70, "семьдесят", "seventy", "yetmish", "етмиш")
        tens(80, "восемьдесят", "eighty", "sakson", "саксон")
        tens(90, "девяносто", "ninety", "to'qson", "туксон")
        num(100, "сто", "yuz", "юз", "hundred")
    }

    private fun numOf(tok: String): Int? {
        NUM[tok]?.let { return it }
        for (suf in NUM_SUF) {
            if (tok.length > suf.length + 1 && tok.endsWith(suf)) {
                NUM[tok.substring(0, tok.length - suf.length)]?.let { return it }
            }
        }
        return null
    }

    private fun convertNumbers(src: List<String>): List<String> {
        val out = ArrayList<String>(src.size)
        var i = 0
        while (i < src.size) {
            val tk = src[i]
            val v = numOf(tk)
            if (v == null) { out.add(tk); i++; continue }
            var n = v
            if (tk in TENSW && i + 1 < src.size && src[i + 1].toIntOrNull() == null) {
                val u = numOf(src[i + 1])
                if (u != null && u in 1..9) { n += u; i++ }
            }
            out.add(n.toString())
            i++
        }
        return out
    }

    private fun tokens(text: String): List<String> {
        val raw = normalize(text).split(' ').filter { it.isNotEmpty() }.toMutableList()
        while (raw.isNotEmpty() && raw[0] in WAKE) raw.removeAt(0)
        while (raw.isNotEmpty() && raw[raw.size - 1] in WAKE) raw.removeAt(raw.size - 1)
        raw.removeAll { it in DROP }
        return convertNumbers(raw)
    }

    // ---------------------------------------------------------------- regex tables

    private const val L = "[\\p{L}']*"

    private val TIME_RX = Regex(
        "(?:скажи )?(?:сколько (?:сейчас )?времени|который (?:сейчас )?час|сколько сейчас|текущее время|" +
            "(?:hozir )?soat necha[\\p{L}']*|soat nechi|(?:hozir )?vaqt necha|what time is it(?: now)?|" +
            "what(?:'s| is) (?:the )?time(?: now)?|what time|(?:tell me|say) (?:the )?time|current time|time now)"
    )
    private val DATE_RX = Regex(
        "(?:скажи )?(?:какое (?:сегодня )?число(?: сегодня)?|какая (?:сегодня )?дата(?: сегодня)?|какой (?:сегодня )?день(?: недели)?|" +
            "число сегодня|дата сегодня|bugun nechinchi(?: sana)?|bugun nechanchi(?: sana)?|bugun sana nechi|bugungi sana|sana nechi|" +
            "bugun qaysi kun|bugun haftaning qaysi kuni|what(?:'s| is) (?:the )?date(?: today)?|what(?:'s| is) today(?:'s date)?|" +
            "what day is it(?: today)?|today's date|what date is it|what(?:'s| is) (?:the )?day(?: today)?)"
    )
    private val BATT_RX = Regex(
        "(?:сколько (?:у меня )?(?:заряда|процентов заряда|процентов|батареи)(?: осталось)?|какой (?:у меня )?(?:заряд|уровень заряда)(?: батареи)?|" +
            "уровень (?:заряда|батареи)|заряд(?: батареи)?|батарея|" +
            "batareya(?: qancha| necha foiz| darajasi| holati)?|batareya[\\p{L}']* qancha|zaryad[\\p{L}']*(?: qancha)?|" +
            "battery(?: level| status| percentage| percent| left)?|how much battery(?: do i have| is left| left)?|" +
            "what(?:'s| is) (?:the )?battery(?: level| percentage)?)"
    )

    private const val ONW = "включи(?:ть)?|зажги|вруби|yoq(?:ib ber|ib qo'y|ing)?|on|enable|turn on|switch on|put on"
    private const val OFFW = "выключи(?:ть)?|отключи(?:ть)?|погаси|выруби|потуши|o'chir[\\p{L}']*(?: ber| qo'y)?|off|disable|turn off|switch off"
    private const val TORCHW = "фонар[\\p{L}]*|chiroq[\\p{L}']*|fonar[\\p{L}']*|flash ?light|torch"
    private val TORCH_RX = Regex("(?:turn )?(?:($ONW|$OFFW) )?(?:$TORCHW)(?: ($ONW|$OFFW))?")
    private val OFFW_RX = Regex(OFFW)

    private const val VOLN = "громкост[\\p{L}]*|звук[\\p{L}]*|ovoz[\\p{L}']*|tovush[\\p{L}']*|volume|sound"
    private const val VLEAD = "(?:(?:сделай|поставь|установи|выставь|set|make|put|qo'y) )?"
    private val VOL_PCT = Regex(
        VLEAD + "(?:$VOLN) (?:(?:на|до|to|at) )?(\\d{1,3})(?: percent| процент[\\p{L}]*| foiz[\\p{L}']*)?(?: ga)?" +
            "(?: (?:qo'y|qil|o'rnat|qilib qo'y|qilib ber|ber))?"
    )
    private val VOL_MAX = Regex(
        VLEAD + "(?:$VOLN) (?:(?:на|до|to|at) )?(?:максимум|максимальн[\\p{L}]*|полн[\\p{L}]*|max|maximum|full|to'liq|eng baland)" +
            "(?: ga)?(?: (?:qo'y|qil|ber))?"
    )
    private val VOL_MUTE = Regex(
        "без звука|беззвучн[\\p{L}]*(?: режим)?|(?:выключи|отключи|убери|заглуши) (?:звук|громкость)|mute(?: (?:sound|volume|it))?|" +
            "silent(?: mode)?|(?:ovoz|tovush)[\\p{L}']* o'chir[\\p{L}']*(?: ber| qo'y)?|ovozsiz(?: rejim)?|jim rejim"
    )
    private val VOLN_TOK = Regex(VOLN)

    private val UPW = hashSetOf(
        "громче", "погромче", "прибавь", "добавь", "увеличь", "подними", "повысь", "balandroq", "oshir", "oshiring",
        "ko'tar", "ko'taring", "balandla", "ko'proq", "louder", "up", "increase", "raise", "higher"
    )
    private val DNW = hashSetOf(
        "тише", "потише", "убавь", "уменьши", "убери", "понизь", "опусти", "pastroq", "pasaytir", "pasaytiring",
        "kamaytir", "kamaytiring", "sekinroq", "quieter", "down", "lower", "decrease", "reduce", "softer"
    )
    private val STRONG = hashSetOf("громче", "погромче", "тише", "потише", "balandroq", "pastroq", "sekinroq", "louder", "quieter", "softer")
    private val VFILL = hashSetOf(
        "сделай", "сделайте", "чуть", "немного", "еще", "it", "make", "a", "bit", "little", "qil", "qo'y", "ber", "biroz", "yana", "на"
    )

    private val MEDIA_PAUSE = Regex(
        "пауза|поставь на паузу|останови(?: музыку)?|стоп|выключи музыку|pause(?: music)?|stop(?: music)?|music pause|pauza|" +
            "to'xta[\\p{L}']*|musiqa[\\p{L}']* to'xtat[\\p{L}']*|musiqa[\\p{L}']* o'chir[\\p{L}']*"
    )
    private val MEDIA_PLAY = Regex(
        "продолжи|продолжай|продолжить|play|resume|continue|play music|resume music|davom et|davom ettir|davom|ijro et|" +
            "musiqa[\\p{L}']* davom ettir[\\p{L}']*"
    )
    private val MEDIA_NEXT = Regex(
        "(?:(?:включи|поставь) )?следующ[\\p{L}]*(?: песн[\\p{L}]*| трек[\\p{L}]*| композици[\\p{L}]*)?|дальше|переключи(?: трек)?|" +
            "next(?: song| track)?|skip(?: song| track)?|keyingi(?: qo'shiq| trek)?|keyingisi|keyingisini qo'y|keyingi qo'shiqni qo'y"
    )
    private val MEDIA_PREV = Regex(
        "(?:(?:включи|поставь) )?предыдущ[\\p{L}]*(?: песн[\\p{L}]*| трек[\\p{L}]*)?|previous(?: song| track)?|prev(?: song| track)?|" +
            "oldingi(?: qo'shiq| trek)?|oldingisi|oldingisini qo'y|oldingi qo'shiqni qo'y"
    )

    private const val NPRE = "(?:(?:сделай|сделайте|открой|покажи|show|open|go to|go|take|make|перейди на|вернись на|выйди на) )?(?:a )?"
    private const val NSUF = "(?: (?:och|ochib ber|oching|ko'rsat|ko'rsating))?"
    private val NAV = arrayOf(
        "back" to Regex("назад|вернись|вернись назад|go back|back|orqaga|orqaga qayt[\\p{L}']*|ortga|ortga qayt[\\p{L}']*|nazad"),
        "home" to Regex(
            NPRE + "(?:домой|(?:на |в )?(?:главн[\\p{L}]* экран[\\p{L}]*|домашн[\\p{L}]* экран[\\p{L}]*|рабоч[\\p{L}]* стол[\\p{L}]*)|" +
                "home|home screen|main screen|bosh ekran[\\p{L}']*|bosh sahifa[\\p{L}']*|bosh menyu|uyga)" + NSUF
        ),
        "recents" to Regex(
            NPRE + "(?:недавн[\\p{L}]*(?: приложени[\\p{L}]*)?|последн[\\p{L}]* приложени[\\p{L}]*|список приложений|recents?|recent apps|" +
                "app switcher|so'nggi ilovalar|oxirgi ilovalar|yaqinda ochilgan ilovalar)" + NSUF
        ),
        "notifications" to Regex(
            NPRE + "(?:уведомлени[\\p{L}]*|шторк[\\p{L}]*(?: уведомлений)?|notifications?|notification (?:panel|shade|bar)|" +
                "bildirishnoma[\\p{L}']*|xabarnoma[\\p{L}']*)" + NSUF
        ),
        "quick_settings" to Regex(
            NPRE + "(?:быстр[\\p{L}]* настройк[\\p{L}]*|quick settings|tezkor sozlamalar[\\p{L}']*)" + NSUF
        ),
        "lock" to Regex(
            "заблокируй(?: экран| телефон)?|блокировк[\\p{L}]*(?: экрана)?|выключи экран|погаси экран|lock(?: screen| phone)?|" +
                "ekran[\\p{L}']* (?:qulfla|blokla)[\\p{L}']*|telefon[\\p{L}']* qulfla[\\p{L}']*|qulfla[\\p{L}']*"
        ),
        "screenshot" to Regex(
            NPRE + "(?:скриншот[\\p{L}]*|скрин|снимок экрана|screenshot|screen capture|skrinshot[\\p{L}']*(?: ol| olish| qil)?|" +
                "ekran(?: tasvirini| rasmini)? (?:ol|olish))"
        )
    )

    private val QUIET_ON = Regex(
        "спрячься|спрячь(?:ся)?|скройся|не мешай|не отвлекай|исчезни|уйди|тихо|yashir[\\p{L}']*(?: ol| tur)?|hide(?: yourself)?|go hide|" +
            "disappear|do not disturb|don't disturb|leave me alone"
    )
    private val QUIET_OFF = Regex(
        "покажись|появись|выходи|выйди|покажи себя|chiq|chiqing|chiqib kel|ko'rin|ko'rinish ber|paydo bo'l[\\p{L}']*|" +
            "show yourself|show up|come out|appear|unhide|come back"
    )

    private val W_TOP = hashSetOf("вверх", "наверх", "вверху", "сверху", "верхний", "верхнем", "tepaga", "tepada", "yuqoriga", "yuqorida", "yuqori", "tepa", "top", "up", "upper")
    private val W_BOT = hashSetOf("вниз", "внизу", "снизу", "нижний", "нижнем", "pastga", "pastda", "past", "bottom", "down", "lower")
    private val W_LEFT = hashSetOf("влево", "налево", "слева", "левый", "левом", "левее", "chapga", "chap", "chapda", "left")
    private val W_RIGHT = hashSetOf("вправо", "направо", "справа", "правый", "правом", "o'ngga", "o'ng", "o'ngda", "right")
    private val W_CENTER = hashSetOf("центр", "центре", "центру", "середину", "середине", "markaz", "markazga", "markazda", "o'rtaga", "o'rtada", "center", "centre", "middle")
    private val M_VERB = hashSetOf(
        "встань", "стань", "перейди", "перемести", "переместись", "двигайся", "иди", "отойди", "поднимись", "спустись",
        "o't", "o'tgin", "o'ting", "bor", "boring", "tur", "turing", "ko'chir", "ko'chgin", "joylash", "tushgin", "ko'tarilgin", "move", "go"
    )
    private val M_FILL = hashSetOf(
        "в", "на", "во", "к", "по", "угол", "углу", "угла", "corner", "to", "side", "of", "screen", "экрана", "ekran", "ekranning",
        "ekranga", "burchak", "burchakka", "yourself", "bit", "чуть", "немного", "biroz"
    )

    private val SZ_UP = hashSetOf("больше", "побольше", "крупнее", "увеличься", "kattaroq", "katta", "kattalash", "bigger", "larger", "big", "grow")
    private val SZ_DN = hashSetOf("меньше", "поменьше", "уменьшись", "kichikroq", "kichik", "kichrayt", "kichraygin", "smaller", "small", "shrink")
    private val SZ_NORM = hashSetOf("обычный", "нормальный", "normal", "odatiy")
    private val SZ_VERB = hashSetOf(
        "стань", "будь", "сделайся", "сделай", "увеличься", "уменьшись", "bo'l", "bo'lgin", "bo'ling", "qil", "qilgin",
        "kattalash", "kichrayt", "kichraygin", "be", "get", "become", "make", "grow", "shrink"
    )
    private val SZ_FILL = hashSetOf("себя", "чуть", "немного", "еще", "o'zingni", "biroz", "yana", "a", "bit", "little", "yourself", "на")

    private val QWORDS = hashSetOf(
        "как", "почему", "зачем", "что", "где", "когда", "кто", "какой", "какая", "какое", "сколько", "можно", "можешь", "how", "why",
        "what", "where", "when", "who", "can", "could", "do", "does", "is", "qanday", "nega", "nima", "qayerda", "qachon", "kim", "mumkinmi"
    )
    private val NAME_STOP = hashSetOf(
        "и", "а", "потом", "then", "and", "ва", "keyin", "скажи", "say", "tell", "что", "чтобы", "как", "how", "what", "why", "почему",
        "where", "где", "if", "если", "after", "после", "когда", "when"
    )
    private val OPEN_DROP = hashSetOf("приложение", "приложения", "программу", "app", "application", "ilovasini", "ilovani", "ilova", "dasturni", "the")
    private val OPEN_DENY = hashSetOf(
        "настройки", "настройку", "settings", "wifi", "bluetooth", "sozlamalar", "музыку", "песню", "сайт", "ссылку", "site", "website",
        "url", "link", "видео", "music", "song", "video"
    )
    private val CALL_RX = Regex("(?:позвони|позвоните|набери|call|phone|ring|dial)(?: to)? (.+)")
    private val CALL_UZ = Regex("(.+?) (?:qo'ng'iroq qil[\\p{L}']*|qo'ng'iroq|telefon qil[\\p{L}']*|zang qil[\\p{L}']*|кунгирок кил[\\p{L}]*)")
    private val OPEN_RX = Regex("(?:открой|открыть|запусти|запустить|open|launch|run) (.+)")
    private val OPEN_UZ = Regex("(.+?) (?:och|ochib ber|ochib bering|oching|ishga tushir[\\p{L}']*)")

    // ---------------------------------------------------------------- durations / alarms

    private val MARKS = hashSetOf("через", "in", "after", "на", "for", "keyin", "later", "спустя", "и", "and", "ga", "dan", "a", "an")
    private val REL_MARK = hashSetOf("через", "in", "after", "keyin", "later", "спустя")
    private val ALARM_TRIG = arrayOf("будильник", "разбуди", "budilnik", "alarm", "uyg'ot", "wake")
    private val ALARM_FILL = hashSetOf(
        "меня", "поставь", "поставьте", "заведи", "установи", "сделай", "на", "в", "к", "во", "ровно", "set", "a", "an", "for", "at",
        "to", "me", "up", "clock", "ber", "meni", "qo'y", "qil", "o'rnat", "o'rnatib", "ga", "da", "o'clock"
    )
    private val TIMER_TRIG = arrayOf("таймер", "taymer", "timer")
    private val TIMER_FILL = hashSetOf("поставь", "поставьте", "засеки", "запусти", "установи", "сделай", "set", "start", "a", "an", "qo'y", "qil", "o'rnat", "ber")
    private val AM = hashSetOf("утра", "утром", "ertalab", "ertalabki", "am")
    private val PM = hashSetOf("вечера", "вечером", "дня", "днем", "kechqurun", "kechki", "pm")
    private val NIGHT = hashSetOf("ночи", "ночью", "tunda", "kechasi")

    private fun unitSecs(k: String): Int = when {
        k.startsWith("секунд") || k == "сек" || k.startsWith("second") || k == "sec" || k == "secs" ||
            k.startsWith("soniya") || k.startsWith("sekund") -> 1
        k.startsWith("минут") || k == "мин" || k.startsWith("minute") || k == "min" || k == "mins" ||
            k.startsWith("minut") || k.startsWith("daqiqa") -> 60
        k.startsWith("час") || k.startsWith("hour") || k == "hr" || k == "hrs" || k.startsWith("soat") -> 3600
        else -> 0
    }

    /** "5 минут", "через 1 час 30 минут", "полчаса", "yarim soat" -> seconds, or null if anything is unrecognised. */
    private fun parseDuration(t: List<String>): Int? {
        var total = 0
        var half = false
        var i = 0
        while (i < t.size) {
            val k = t[i]
            if (k == "полчаса" || k == "получаса") { total += 1800; i++; continue }
            if (k == "yarim" || k == "half" || k == "пол") { half = true; i++; continue }
            if (k in MARKS) { i++; continue }
            val n = k.toIntOrNull()
            if (n != null) {
                if (i + 1 >= t.size) return null
                val u = unitSecs(t[i + 1])
                if (u == 0) return null
                total += n * u
                i += 2
                continue
            }
            val u = unitSecs(k)
            if (u == 0) return null
            total += if (half) u / 2 else u
            half = false
            i++
        }
        return if (total in 1..86400) total else null
    }

    private fun parseTimer(tk: List<String>): ParsedQuick? {
        if (tk.none { w -> TIMER_TRIG.any { w.startsWith(it) } }) return null
        val rest = tk.filter { w -> w !in TIMER_FILL && TIMER_TRIG.none { w.startsWith(it) } }
        val secs = parseDuration(rest) ?: return null
        return ParsedQuick("timer", mapOf("seconds" to secs))
    }

    private fun parseAlarm(tk: List<String>, nowH: Int, nowM: Int): ParsedQuick? {
        if (tk.none { w -> ALARM_TRIG.any { w.startsWith(it) } }) return null
        val rest = tk.filter { w -> w !in ALARM_FILL && ALARM_TRIG.none { w.startsWith(it) } }
        if (rest.isEmpty()) return null
        val h: Int
        val m: Int
        if (rest.any { it in REL_MARK }) {
            val secs = parseDuration(rest) ?: return null
            val tot = (nowH * 60 + nowM + (secs + 59) / 60) % 1440
            h = tot / 60
            m = tot % 60
        } else {
            var period = 0 // 1 am, 2 pm, 3 night
            val nums = ArrayList<Int>(2)
            var pmEn = false
            for (k in rest) {
                val n = k.toIntOrNull()
                when {
                    n != null -> nums.add(n)
                    k in AM -> period = 1
                    k in PM -> { period = 2; if (k == "pm") pmEn = true }
                    k in NIGHT -> period = 3
                    unitSecs(k) >= 60 -> { } // "часов", "минут", "soat", "daqiqa"
                    else -> return null
                }
            }
            if (nums.size !in 1..2) return null
            val hh = nums[0]
            val mm = if (nums.size > 1) nums[1] else 0
            if (hh !in 0..23 || mm !in 0..59) return null
            when (period) {
                1 -> { if (hh > 12) return null; h = if (hh == 12) 0 else hh }
                2 -> { if (pmEn && hh > 12) return null; h = if (hh in 1..11) hh + 12 else hh }
                3 -> { h = when { hh == 12 -> 0; hh in 6..11 -> hh + 12; else -> hh } }
                else -> {
                    if (hh in 1..12) {
                        // no am/pm: the next occurrence of that clock time from now
                        val now = nowH * 60 + nowM
                        var d1 = ((hh % 12) * 60 + mm - now + 1440) % 1440
                        var d2 = ((hh % 12) * 60 + 720 + mm - now + 1440) % 1440
                        if (d1 == 0) d1 = 1440
                        if (d2 == 0) d2 = 1440
                        h = if (d1 <= d2) hh % 12 else (hh % 12) + 12
                    } else h = hh
                }
            }
            m = mm
        }
        return ParsedQuick("alarm", mapOf("hour" to h, "minute" to m, "label" to ""))
    }

    // ---------------------------------------------------------------- vocabulary-style intents

    private fun volumeDir(t: List<String>): String? {
        if (t.size > 5) return null
        var up = false; var dn = false; var noun = false; var strong = false; var turn = false
        for (k in t) {
            when {
                k in UPW -> { up = true; if (k in STRONG) strong = true }
                k in DNW -> { dn = true; if (k in STRONG) strong = true }
                VOLN_TOK.matches(k) -> noun = true
                k == "turn" -> turn = true
                k in VFILL -> { }
                else -> return null
            }
        }
        if (up == dn) return null
        if (!(noun || strong || turn)) return null
        return if (up) "up" else "down"
    }

    private fun moveWhere(t: List<String>): String? {
        if (t.size > 6) return null
        var v: String? = null
        var h: String? = null
        var c = false
        var verb = false
        for (k in t) {
            when {
                k in W_TOP -> { if (v != null) return null; v = "top" }
                k in W_BOT -> { if (v != null) return null; v = "bottom" }
                k in W_LEFT -> { if (h != null) return null; h = "left" }
                k in W_RIGHT -> { if (h != null) return null; h = "right" }
                k in W_CENTER -> c = true
                k in M_VERB -> verb = true
                k in M_FILL -> { }
                else -> return null
            }
        }
        if (!verb) return null
        if (c) return if (v == null && h == null) "center" else null
        return when {
            v != null && h != null -> v + "_" + h
            v != null -> v
            else -> h
        }
    }

    private fun sizeValue(t: List<String>): String? {
        if (t.size > 6) return null
        var up = false; var dn = false; var norm = false; var verb = false
        for (k in t) {
            var known = false
            if (k in SZ_UP) { up = true; known = true }
            if (k in SZ_DN) { dn = true; known = true }
            if (k in SZ_NORM) { norm = true; known = true }
            if (k in SZ_VERB) { verb = true; known = true }
            if (!known && k !in SZ_FILL) return null
        }
        if (!verb) return null
        return when {
            up && !dn && !norm -> "bigger"
            dn && !up && !norm -> "smaller"
            norm && !up && !dn -> "normal"
            else -> null
        }
    }

    private fun cleanTarget(words: List<String>, drop: Set<String>, deny: Set<String>): String? {
        val w = words.filter { it !in drop }
        if (w.isEmpty() || w.size > 3) return null
        for (k in w) if (k in NAME_STOP || k in deny) return null
        return w.joinToString(" ")
    }

    // ---------------------------------------------------------------- main parser

    /** Pure text -> intent parsing (no Android). [nowHour]/[nowMinute] resolve relative and am/pm-less alarm times. */
    internal fun parse(text: String, nowHour: Int, nowMinute: Int): ParsedQuick? {
        if (text.length > 160) return null
        val tk = tokens(text)
        if (tk.isEmpty() || tk.size > 10) return null
        val t = tk.joinToString(" ")

        if (TIME_RX.matches(t)) return ParsedQuick("time")
        if (DATE_RX.matches(t)) return ParsedQuick("date")
        if (BATT_RX.matches(t)) return ParsedQuick("battery")

        if (tk[0] in QWORDS || text.trimEnd().endsWith("?")) return null

        TORCH_RX.matchEntire(t)?.let { m ->
            val a = m.groupValues[1]
            val b = m.groupValues[2]
            if (a.isEmpty() == b.isEmpty()) return null
            val w = if (a.isNotEmpty()) a else b
            return ParsedQuick("flashlight", mapOf("on" to !OFFW_RX.matches(w)))
        }

        VOL_PCT.matchEntire(t)?.let { m ->
            val p = m.groupValues[1].toIntOrNull() ?: return null
            if (p !in 0..100) return null
            return ParsedQuick("volume", mapOf("percent" to p))
        }
        if (VOL_MAX.matches(t)) return ParsedQuick("volume", mapOf("percent" to 100))
        if (VOL_MUTE.matches(t)) return ParsedQuick("volume", mapOf("percent" to 0))
        volumeDir(tk)?.let { return ParsedQuick("volume", mapOf("dir" to it)) }

        if (MEDIA_PAUSE.matches(t)) return ParsedQuick("media", mapOf("key" to "pause", "_v" to "pause"))
        if (MEDIA_PLAY.matches(t)) return ParsedQuick("media", mapOf("key" to "play", "_v" to "play"))
        if (MEDIA_NEXT.matches(t)) return ParsedQuick("media", mapOf("key" to "next", "_v" to "next"))
        if (MEDIA_PREV.matches(t)) return ParsedQuick("media", mapOf("key" to "previous", "_v" to "previous"))

        parseTimer(tk)?.let { return it }
        parseAlarm(tk, nowHour, nowMinute)?.let { return it }

        for ((name, rx) in NAV) if (rx.matches(t)) return ParsedQuick(name)

        if (QUIET_ON.matches(t)) return ParsedQuick("quiet", mapOf("on" to true))
        if (QUIET_OFF.matches(t)) return ParsedQuick("quiet", mapOf("on" to false))

        moveWhere(tk)?.let { return ParsedQuick("move", mapOf("where" to it)) }
        sizeValue(tk)?.let { return ParsedQuick("size", mapOf("value" to it)) }

        CALL_RX.matchEntire(t)?.let { m ->
            val to = cleanTarget(m.groupValues[1].split(' '), DROP, emptySet()) ?: return null
            return ParsedQuick("call", mapOf("to" to to))
        }
        CALL_UZ.matchEntire(t)?.let { m ->
            var to = cleanTarget(m.groupValues[1].split(' '), DROP, emptySet()) ?: return null
            if (to.length > 4 && to.endsWith("ga")) to = to.substring(0, to.length - 2)
            return ParsedQuick("call", mapOf("to" to to))
        }

        OPEN_RX.matchEntire(t)?.let { m ->
            val name = cleanTarget(m.groupValues[1].split(' '), OPEN_DROP, OPEN_DENY) ?: return null
            if (name.toIntOrNull() != null) return null
            return ParsedQuick("open_app", mapOf("name" to name))
        }
        OPEN_UZ.matchEntire(t)?.let { m ->
            var name = cleanTarget(m.groupValues[1].split(' '), OPEN_DROP, OPEN_DENY) ?: return null
            if (name.length > 4 && name.endsWith("ni")) name = name.substring(0, name.length - 2)
            if (name.toIntOrNull() != null) return null
            return ParsedQuick("open_app", mapOf("name" to name))
        }
        return null
    }

    // ---------------------------------------------------------------- spoken replies

    private val RU_MONTHS = arrayOf("января", "февраля", "марта", "апреля", "мая", "июня", "июля", "августа", "сентября", "октября", "ноября", "декабря")
    private val EN_MONTHS = arrayOf("January", "February", "March", "April", "May", "June", "July", "August", "September", "October", "November", "December")
    private val UZ_MONTHS = arrayOf("yanvar", "fevral", "mart", "aprel", "may", "iyun", "iyul", "avgust", "sentabr", "oktabr", "noyabr", "dekabr")
    private val RU_DAYS = arrayOf("воскресенье", "понедельник", "вторник", "среда", "четверг", "пятница", "суббота")
    private val EN_DAYS = arrayOf("Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday")
    private val UZ_DAYS = arrayOf("yakshanba", "dushanba", "seshanba", "chorshanba", "payshanba", "juma", "shanba")

    private fun hhmm(h: Int, m: Int) = String.format(Locale.US, "%02d:%02d", h, m)

    private fun fmtDur(ctx: Context, secs: Int): String {
        val h = secs / 3600
        val m = secs % 3600 / 60
        val s = secs % 60
        val sb = StringBuilder()
        if (h > 0) sb.append(h).append(' ').append(Tr.t(ctx, "ч", "h", "soat")).append(' ')
        if (m > 0) sb.append(m).append(' ').append(Tr.t(ctx, "мин", "min", "daq")).append(' ')
        if (s > 0) sb.append(s).append(' ').append(Tr.t(ctx, "сек", "s", "son")).append(' ')
        return sb.toString().trim()
    }

    private fun build(ctx: Context, p: ParsedQuick, now: Calendar): Quick? {
        fun t(ru: String, en: String, uz: String) = Tr.t(ctx, ru, en, uz)
        val pr = p.params
        var emo = "happy"
        val say: String = when (p.intent) {
            "time" -> {
                emo = "cool"
                val h = now.get(Calendar.HOUR_OF_DAY)
                val m = now.get(Calendar.MINUTE)
                val h12 = if (h % 12 == 0) 12 else h % 12
                t("Сейчас ${hhmm(h, m)}", "It's $h12:${String.format(Locale.US, "%02d", m)} ${if (h < 12) "AM" else "PM"}", "Soat ${hhmm(h, m)}")
            }
            "date" -> {
                emo = "cool"
                val d = now.get(Calendar.DAY_OF_MONTH)
                val mo = now.get(Calendar.MONTH)
                val wd = now.get(Calendar.DAY_OF_WEEK) - 1
                t("Сегодня $d ${RU_MONTHS[mo]}, ${RU_DAYS[wd]}", "Today is ${EN_DAYS[wd]}, ${EN_MONTHS[mo]} $d", "Bugun $d ${UZ_MONTHS[mo]}, ${UZ_DAYS[wd]}")
            }
            "battery" -> {
                emo = "cool"
                val bm = ctx.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager ?: return null
                val lvl = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
                if (lvl <= 0 || lvl > 100) return null
                t("Заряд $lvl%", "Battery is $lvl%", "Batareya $lvl%")
            }
            "flashlight" -> {
                emo = "cool"
                if (pr["on"] == true) t("Фонарик включён", "Flashlight on", "Chiroq yoqildi")
                else t("Фонарик выключен", "Flashlight off", "Chiroq o'chirildi")
            }
            "volume" -> {
                emo = "cool"
                val pc = pr["percent"] as? Int
                when {
                    pc == 0 -> t("Звук выключен", "Muted", "Ovoz o'chirildi")
                    pc != null -> t("Громкость $pc%", "Volume $pc%", "Ovoz $pc%")
                    pr["dir"] == "up" -> t("Громче", "Louder", "Balandroq")
                    else -> t("Тише", "Quieter", "Pastroq")
                }
            }
            "media" -> when (pr["_v"]) {
                "pause" -> t("Пауза", "Paused", "Pauza")
                "play" -> t("Продолжаю", "Playing", "Davom etaman")
                "next" -> t("Следующая", "Next", "Keyingisi")
                else -> t("Предыдущая", "Previous", "Oldingisi")
            }
            "alarm" -> {
                emo = "proud"
                val hm = hhmm(pr["hour"] as Int, pr["minute"] as Int)
                t("Будильник на $hm", "Alarm set for $hm", "Budilnik $hm ga qo'yildi")
            }
            "timer" -> {
                emo = "proud"
                val d = fmtDur(ctx, pr["seconds"] as Int)
                t("Таймер на $d", "Timer for $d", "Taymer: $d")
            }
            "open_app" -> {
                emo = "excited"
                val n = pr["name"] as String
                t("Открываю $n", "Opening $n", "$n ochilmoqda")
            }
            "call" -> {
                emo = "excited"
                val n = pr["to"] as String
                t("Звоню: $n", "Calling $n", "$n ga qo'ng'iroq qilaman")
            }
            "back" -> t("Назад", "Back", "Orqaga")
            "home" -> t("Домой", "Home", "Bosh ekran")
            "recents" -> t("Недавние", "Recent apps", "Oxirgi ilovalar")
            "notifications" -> t("Уведомления", "Notifications", "Bildirishnomalar")
            "quick_settings" -> t("Быстрые настройки", "Quick settings", "Tezkor sozlamalar")
            "lock" -> t("Блокирую", "Locking", "Qulflayman")
            "screenshot" -> {
                emo = "wink"
                t("Скриншот", "Screenshot", "Skrinshot")
            }
            "quiet" -> {
                emo = if (pr["on"] == true) "wink" else "excited"
                if (pr["on"] == true) t("Прячусь", "Hiding", "Yashirinaman")
                else t("Я тут!", "I'm back!", "Men shu yerdaman!")
            }
            "move", "size" -> t("Хорошо", "Okay", "Xo'p")
            else -> return null
        }
        val acts = JSONArray()
        if (p.intent != "time" && p.intent != "date" && p.intent != "battery") {
            val o = JSONObject()
            o.put("do", p.intent)
            for ((k, v) in pr) if (!k.startsWith("_")) o.put(k, v)
            acts.put(o)
        }
        return Quick(say, emo, acts)
    }
}
