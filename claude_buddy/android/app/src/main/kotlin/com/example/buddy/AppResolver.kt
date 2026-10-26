package com.example.buddy

import android.content.Context
import android.content.Intent

/**
 * Resolves a spoken app / system screen name (ru/uz/en) to an installed launcher app.
 * resolve() and launch() are BLOCKING (PackageManager): call from a background thread.
 */
object AppResolver {
    class Match(val pkg: String, val label: String, val score: Int)

    internal class AppEntry(val label: String, val norm: String, val key: String, val pkg: String)

    private class Alias(val names: List<String>, val pkgs: List<String>, val hints: List<String> = emptyList()) {
        val keys: List<String> = names.map { NameText.key(it) }
        val hintKeys: List<String> = hints.map { NameText.key(it) }
    }

    private val ALIASES: List<Alias> = listOf(
        Alias(listOf("youtube music", "yt music", "ютуб музыка", "ютуб мьюзик", "youtube musiqa", "yutub musiqa"),
            listOf("com.google.android.apps.youtube.music"), listOf("youtube music")),
        Alias(listOf("youtube", "you tube", "ютуб", "ютьюб", "yutub", "yutube"),
            listOf("com.google.android.youtube", "app.revanced.android.youtube", "com.vanced.android.youtube"), listOf("youtube")),
        Alias(listOf("spotify", "спотифай", "спотифи", "spotifay"),
            listOf("com.spotify.music", "com.spotify.lite"), listOf("spotify")),
        Alias(listOf("telegram", "телеграм", "телеграмм", "телега", "tg"),
            listOf("org.telegram.messenger", "org.telegram.messenger.web", "org.telegram.plus"), listOf("telegram")),
        Alias(listOf("whatsapp", "whats app", "ватсап", "ватсапп", "вацап", "вотсап", "vatsap", "vatsapp", "watsap"),
            listOf("com.whatsapp", "com.whatsapp.w4b"), listOf("whatsapp")),
        Alias(listOf("instagram", "инстаграм", "инста", "insta", "instagramm"),
            listOf("com.instagram.android", "com.instagram.lite"), listOf("instagram")),
        Alias(listOf("facebook", "фейсбук", "фэйсбук", "feysbuk", "fb"),
            listOf("com.facebook.katana", "com.facebook.lite"), listOf("facebook")),
        Alias(listOf("tiktok", "tik tok", "тикток", "тик ток"),
            listOf("com.zhiliaoapp.musically", "com.ss.android.ugc.trill", "com.ss.android.ugc.tiktok"), listOf("tiktok")),
        Alias(listOf("chrome", "google chrome", "хром", "гугл хром"),
            listOf("com.android.chrome", "com.chrome.beta", "com.google.android.apps.chrome"), listOf("chrome")),
        Alias(listOf("samsung internet", "самсунг интернет"),
            listOf("com.sec.android.app.sbrowser"), listOf("samsung internet")),
        Alias(listOf("browser", "brauzer", "браузер", "internet", "интернет"),
            listOf("com.android.chrome", "com.sec.android.app.sbrowser", "org.mozilla.firefox", "com.brave.browser",
                "com.microsoft.emmx", "com.opera.browser", "com.yandex.browser", "com.android.browser"),
            listOf("chrome", "browser", "internet")),
        Alias(listOf("yandex browser", "яндекс браузер"), listOf("com.yandex.browser"), listOf("yandex browser", "яндекс браузер")),
        Alias(listOf("yandex maps", "яндекс карты", "yandex xarita"),
            listOf("ru.yandex.yandexmaps"), listOf("yandex maps", "яндекс карты")),
        Alias(listOf("yandex navigator", "яндекс навигатор", "навигатор", "navigator"),
            listOf("ru.yandex.yandexnavi"), listOf("navigator", "навигатор")),
        Alias(listOf("maps", "google maps", "карты", "карта", "гугл карты", "xarita", "xaritalar"),
            listOf("com.google.android.apps.maps"), listOf("maps", "карты")),
        Alias(listOf("gmail", "джимейл", "гмейл", "почта", "pochta", "mail", "email", "имейл", "e mail"),
            listOf("com.google.android.gm", "com.google.android.gm.lite", "com.samsung.android.email.provider",
                "com.android.email", "ru.yandex.mail", "ru.mail.mailapp"),
            listOf("gmail", "mail", "почта", "email")),
        Alias(listOf("camera", "камера", "kamera", "камеру"),
            listOf("com.google.android.GoogleCamera", "com.sec.android.app.camera", "com.android.camera",
                "com.android.camera2", "com.huawei.camera", "com.oneplus.camera", "com.oppo.camera",
                "com.coloros.camera", "com.motorola.camera3", "com.transsion.camera"),
            listOf("camera", "камера", "kamera")),
        Alias(listOf("gallery", "галерея", "galereya", "galeriya", "gallereya", "фото", "foto", "photos", "google photos",
            "фотографии", "rasmlar", "rasm"),
            listOf("com.sec.android.gallery3d", "com.miui.gallery", "com.android.gallery3d", "com.oneplus.gallery",
                "com.coloros.gallery3d", "com.huawei.photos", "com.google.android.apps.photos",
                "com.google.android.apps.photosgo"),
            listOf("gallery", "галерея", "photos", "фото")),
        Alias(listOf("calculator", "калькулятор", "калькулатор", "kalkulyator", "hisoblagich"),
            listOf("com.google.android.calculator", "com.sec.android.app.popupcalculator", "com.android.calculator2",
                "com.miui.calculator", "com.oneplus.calculator", "com.coloros.calculator", "com.huawei.calculator"),
            listOf("calculator", "калькулятор")),
        Alias(listOf("clock", "часы", "soat", "soatlar", "будильник", "budilnik", "alarm"),
            listOf("com.google.android.deskclock", "com.sec.android.app.clockpackage", "com.android.deskclock",
                "com.oneplus.deskclock", "com.coloros.alarmclock", "com.huawei.deskclock"),
            listOf("clock", "часы", "soat")),
        Alias(listOf("calendar", "календарь", "kalendar", "taqvim"),
            listOf("com.google.android.calendar", "com.samsung.android.calendar", "com.android.calendar"),
            listOf("calendar", "календарь")),
        Alias(listOf("contacts", "контакты", "контакт", "kontaktlar", "kontakt"),
            listOf("com.google.android.contacts", "com.samsung.android.app.contacts", "com.samsung.android.contacts",
                "com.android.contacts"),
            listOf("contacts", "контакты")),
        Alias(listOf("phone", "dialer", "телефон", "telefon", "звонилка", "звонилку", "zvonilka", "звонки", "qongiroq"),
            listOf("com.google.android.dialer", "com.samsung.android.dialer", "com.android.dialer", "com.android.contacts"),
            listOf("phone", "телефон", "dialer")),
        Alias(listOf("messages", "sms", "смс", "сообщения", "xabarlar", "xabar"),
            listOf("com.google.android.apps.messaging", "com.samsung.android.messaging", "com.android.mms"),
            listOf("messages", "сообщения", "sms")),
        Alias(listOf("settings", "setting", "настройки", "sozlamalar", "sozlama"),
            listOf("com.android.settings"), listOf("settings", "настройки")),
        Alias(listOf("files", "file manager", "файлы", "файловый менеджер", "fayllar", "fayl"),
            listOf("com.google.android.apps.nbu.files", "com.sec.android.app.myfiles", "com.mi.android.globalFileexplorer",
                "com.android.documentsui", "com.google.android.documentsui", "com.android.fileexplorer"),
            listOf("files", "файлы", "file manager")),
        Alias(listOf("play store", "google play", "плей маркет", "плей стор", "плеймаркет", "play market", "playmarket"),
            listOf("com.android.vending"), listOf("play store", "play market")),
        Alias(listOf("notes", "note", "заметки", "заметка", "qaydlar", "qayd"),
            listOf("com.google.android.keep", "com.samsung.android.app.notes", "com.miui.notes", "com.oneplus.note"),
            listOf("notes", "заметки", "keep")),
        Alias(listOf("yandex music", "яндекс музыка", "yandex musiqa"),
            listOf("ru.yandex.music"), listOf("yandex music", "яндекс музыка")),
        Alias(listOf("yandex go", "яндекс го", "yandex taxi", "яндекс такси", "taxi", "такси"),
            listOf("ru.yandex.taxi", "com.yandex.go"), listOf("yandex go", "яндекс go", "яндекс го")),
        Alias(listOf("yandex", "яндекс"), listOf("ru.yandex.searchplugin", "ru.yandex.yandexapp"), listOf("yandex", "яндекс")),
        // Uzbek apps: label matching only
        Alias(listOf("payme", "пейме", "пайме"), emptyList(), listOf("payme")),
        Alias(listOf("click", "клик"), emptyList(), listOf("click")),
        Alias(listOf("uzum", "узум"), emptyList(), listOf("uzum")),
        Alias(listOf("olx", "олх"), emptyList(), listOf("olx")),
        Alias(listOf("mobi", "моби"), emptyList(), listOf("mobi")),
        Alias(listOf("my uzcard", "uzcard", "узкард", "май узкард"), emptyList(), listOf("my uzcard", "uzcard"))
    )

    private const val TTL_MS = 5 * 60 * 1000L
    private val lock = Any()
    private var cache: List<AppEntry> = emptyList()
    private var cacheAt = 0L
    private var cacheCount = -2

    /** Blocking. Call from a background thread. Null when nothing matches well enough. */
    fun resolve(ctx: Context, spoken: String): Match? {
        val q = NameText.norm(spoken)
        if (q.isEmpty()) return null
        val apps = apps(ctx)
        return resolveIn(apps, spoken)
    }

    /** Pure resolution over a given app list (unit-testable). */
    internal fun resolveIn(apps: List<AppEntry>, spoken: String): Match? {
        val q = NameText.norm(spoken)
        if (q.isEmpty()) return null
        val qk = NameText.key(spoken)
        if (apps.isEmpty()) return null

        // 1) alias table (longest alias match wins; whole-string equality beats containment)
        var bestAlias: Alias? = null
        var bestLen = -1
        val padded = " $qk "
        for (a in ALIASES) {
            for (k in a.keys) {
                if (k.isEmpty()) continue
                val len = when {
                    k == qk -> 1000 + k.length
                    padded.contains(" $k ") -> k.length
                    else -> -1
                }
                if (len > bestLen) { bestLen = len; bestAlias = a }
            }
        }
        if (bestAlias != null) {
            for (p in bestAlias.pkgs) {
                val e = apps.firstOrNull { it.pkg == p }
                if (e != null) return Match(e.pkg, e.label, 100)
            }
            for (h in bestAlias.hintKeys) {
                if (h.isEmpty()) continue
                val e = apps.filter { it.key == h || it.key.contains(h) }.minByOrNull { it.key.length }
                if (e != null) return Match(e.pkg, e.label, 90)
            }
        }

        // 2) label search
        val qCompact = q.replace(" ", "")
        var best: AppEntry? = null
        var bestScore = 0
        for (e in apps) {
            var s = labelScore(q, e.norm)
            val sk = labelScore(qk, e.key)
            if (sk > 0 && sk - 3 > s) s = sk - 3
            if (s < 50 && qCompact.length >= 4 && e.pkg.lowercase().contains(qCompact)) s = 50
            if (s > bestScore || (s == bestScore && s > 0 && best != null && e.label.length < best.label.length)) {
                bestScore = s
                best = e
            }
        }
        val b = best ?: return null
        return if (bestScore >= 50) Match(b.pkg, b.label, bestScore) else null
    }

    /** Blocking-safe; starts the launcher activity in a new task. */
    fun launch(ctx: Context, pkg: String): Boolean {
        return try {
            val i = ctx.packageManager.getLaunchIntentForPackage(pkg) ?: return false
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            ctx.startActivity(i)
            true
        } catch (e: Exception) {
            false
        }
    }

    private fun labelScore(q: String, l: String): Int {
        if (q.isEmpty() || l.isEmpty()) return 0
        if (l == q) return 100
        if (q.length >= 2 && l.startsWith(q)) return 85
        val qt = q.split(' ')
        val lt = l.split(' ')
        if (qt.all { it in lt }) return 80
        if (q.length >= 3 && l.contains(q)) return 65
        if (l.length >= 3 && q.contains(l)) return 55
        if (qt.all { t -> t.length >= 5 && lt.any { NameText.lev(it, t) <= 1 } }) return 50
        if (q.length >= 5 && NameText.lev(l, q) <= 1) return 50
        return 0
    }

    private fun apps(ctx: Context): List<AppEntry> {
        val pm = ctx.packageManager
        val count = try { pm.getInstalledPackages(0).size } catch (e: Exception) { -1 }
        val now = System.currentTimeMillis()
        synchronized(lock) {
            if (cache.isNotEmpty() && count == cacheCount && now - cacheAt < TTL_MS) return cache
        }
        val list = ArrayList<AppEntry>()
        try {
            val main = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
            val seen = HashSet<String>()
            for (ri in pm.queryIntentActivities(main, 0)) {
                val pkg = ri.activityInfo?.packageName ?: continue
                if (pkg == ctx.packageName || !seen.add(pkg)) continue
                val label = ri.loadLabel(pm)?.toString() ?: continue
                list.add(AppEntry(label, NameText.norm(label), NameText.key(label), pkg))
            }
        } catch (e: Exception) {
            // keep whatever we have
        }
        synchronized(lock) {
            if (list.isNotEmpty()) {
                cache = list
                cacheAt = now
                cacheCount = count
            }
            return if (list.isNotEmpty()) list else cache
        }
    }
}
