package com.example.buddy

import org.json.JSONArray
import org.json.JSONObject

// Pure Kotlin (org.json only, no android.*): compiled into the JVM tests in app/qa-jvm.

/** How risky a tool is. READ and ACT run freely (after the injection gate); SENSITIVE always needs the user's yes. */
enum class Risk { READ, ACT, SENSITIVE }

/** Who runs a tool: Actions.run on the main thread, an observation answered by Brain, a device tool, or the loop itself. */
enum class Exec { ACTION, OBSERVE, DEVICE, LOOP }

/** Tool groups: CORE is always offered; the others are added when the request needs them (or by more_tools). */
enum class Group(val key: String) { CORE("core"), SCREEN("screen"), PHONE("phone"), DATA("data"), ORGANIZER("organizer"), MASCOT("mascot");
    companion object { fun of(k: String): Group? = values().firstOrNull { it.key == k.trim().lowercase() } }
}

/** One tool parameter. [ex] is the example value shown in the legacy JSON-mode block (null = not shown there). */
class P(val name: String, val type: String, val desc: String = "", val ex: Any? = null, val req: Boolean = false, val enum: List<String>? = null)

/** Three-language text, Russian first like Tr.t. */
class T3(val ru: String, val en: String, val uz: String)

/**
 * One declarative tool. The registry generates the native tool schemas (Anthropic, OpenAI-compatible, Gemini), the compact
 * text block of the legacy JSON mode, and the progress label shown on the mascot.
 */
class Tool(
    val name: String,
    val desc: String,
    val risk: Risk,
    val exec: Exec,
    val group: Group,
    val params: List<P> = emptyList(),
    /** Part of the original JSON-mode action list: always listed there, whatever the groups. */
    val legacy: Boolean = false,
    /** Legacy note when it differs from [desc]. */
    val note: String? = null,
    /** Progress label; "%s" is replaced by the value of [arg]. */
    val progress: T3? = null,
    val arg: String? = null,
)

/** JSON string field; JSON null or a missing key never becomes the text "null". */
internal fun jsStr(j: JSONObject, k: String, def: String = ""): String = if (j.isNull(k)) def else j.optString(k, def)

/** Picks one of three texts by language code (pure counterpart of Tr.t). */
fun pick(lang: String?, ru: String, en: String, uz: String): String = when (lang) { "en" -> en; "uz" -> uz; else -> ru }
fun pick(lang: String?, t: T3): String = pick(lang, t.ru, t.en, t.uz)

object ToolRegistry {
    private fun s(n: String, d: String = "", ex: Any? = "", req: Boolean = false, enum: List<String>? = null) = P(n, "string", d, ex, req, enum)
    private fun i(n: String, d: String = "", ex: Any? = 0, req: Boolean = false) = P(n, "integer", d, ex, req)
    private fun n(n: String, d: String = "", ex: Any? = 0, req: Boolean = false) = P(n, "number", d, ex, req)
    private fun b(n: String, d: String = "", ex: Any? = true, req: Boolean = false) = P(n, "boolean", d, ex, req)
    private fun t(ru: String, en: String, uz: String) = T3(ru, en, uz)

    private val R = Risk.READ; private val A = Risk.ACT; private val S = Risk.SENSITIVE
    private val ACTION = Exec.ACTION; private val OBS = Exec.OBSERVE; private val DEV = Exec.DEVICE; private val LOOP = Exec.LOOP
    private val CORE = Group.CORE; private val SCREEN = Group.SCREEN; private val PHONE = Group.PHONE
    private val DATA = Group.DATA; private val ORG = Group.ORGANIZER; private val MASCOT = Group.MASCOT

    /** Every tool, in a fixed order (CORE first) so the generated schemas are identical between requests (prompt caching). */
    val ALL: List<Tool> = listOf(
        // ---- loop control (native tool mode) ----
        Tool("done", "Finish the task: summary = what you did or found, said aloud to the user (at most 3 short sentences, in the user's language). Call it together with the last action in the same reply.",
            R, LOOP, CORE, listOf(s("summary", "spoken result for the user", req = true), s("emotion", "mood for the mascot", ex = null))),
        Tool("plan", "Write a short plan (or a note to remember: names, numbers, what you found) before a multi-step phone task. Not shown to the user.",
            R, LOOP, CORE, listOf(s("text", "numbered short steps or the note", req = true))),
        Tool("more_tools", "Adds a group of tools to the next turn: screen (tap, type, scroll, swipe, look at the screen), phone (calls, settings, toggles, intents), data (contacts, SMS, files, apps, clipboard, share), organizer (tasks, lists, calendar), mascot (outfits, pet, size, games).",
            R, LOOP, CORE, listOf(s("group", "group name", req = true, enum = listOf("screen", "phone", "data", "organizer", "mascot")))),
        // ---- core actions (the original JSON-mode list) ----
        Tool("alarm", "Sets an alarm (24h clock; compute relative times from the current time).", A, ACTION, CORE,
            listOf(i("hour", ex = 7, req = true), i("minute", ex = 30, req = true), s("label")), legacy = true,
            note = "(24h clock; compute relative times from the current time)", progress = t("Ставлю будильник", "Setting an alarm", "Budilnik qo'yilmoqda")),
        Tool("timer", "Starts a countdown timer.", A, ACTION, CORE, listOf(i("seconds", ex = 300, req = true)), legacy = true,
            progress = t("Ставлю таймер", "Starting a timer", "Taymer qo'yilmoqda")),
        Tool("search", "Opens a Google search on the screen for the user.", A, ACTION, CORE, listOf(s("q", req = true)), legacy = true,
            note = "opens Google on screen", progress = t("Открываю поиск", "Opening search", "Qidiruv ochilmoqda")),
        Tool("web_search", "Looks up fresh facts yourself (news, prices, weather, \"who is\", anything uncertain); you get the results back.",
            R, OBS, CORE, listOf(s("q", req = true)), legacy = true,
            note = "looks up fresh facts yourself (news, prices, weather, \"who is\", anything uncertain): send it ALONE with an empty say, you get the results next turn",
            progress = t("Ищу в интернете", "Searching the web", "Internetdan qidiryapman")),
        Tool("music", "Finds the track and starts PLAYING it automatically (optional app: package id only if the user names an app).",
            A, ACTION, CORE, listOf(s("q", "song, artist, mood or playlist", ex = "song, artist, mood or playlist", req = true), s("app", ex = null)), legacy = true,
            note = "finds the track and starts PLAYING it automatically (optional \"app\": package id only if the user names an app)",
            progress = t("Включаю музыку", "Playing music", "Musiqa yoqilmoqda")),
        Tool("video", "Finds the video on YouTube and starts PLAYING it (not just a results page).", A, ACTION, CORE,
            listOf(s("q", ex = "what to watch", req = true)), legacy = true, progress = t("Включаю видео", "Playing a video", "Video yoqilmoqda")),
        Tool("media", "Controls the media player.", A, ACTION, CORE,
            listOf(s("key", ex = "play|pause|play_pause|next|previous|stop|seek_forward|seek_back", req = true,
                enum = listOf("play", "pause", "play_pause", "next", "previous", "stop", "seek_forward", "seek_back")), i("seconds", ex = 10)), legacy = true),
        Tool("volume", "Media volume: dir up/down, or percent 0-100.", A, ACTION, CORE,
            listOf(s("dir", ex = "up|down", enum = listOf("up", "down")), i("percent", ex = null)), legacy = true,
            note = "or {\"do\":\"volume\",\"percent\":50}"),
        Tool("flashlight", "Turns the flashlight on or off.", A, ACTION, CORE, listOf(b("on", req = true)), legacy = true),
        Tool("open_app", "Opens an installed app by its name.", A, ACTION, CORE, listOf(s("name", req = true)), legacy = true,
            progress = t("Открываю %s", "Opening %s", "%s ochilmoqda"), arg = "name"),
        Tool("open_url", "Opens a web link.", A, ACTION, CORE, listOf(s("url", ex = "https://...", req = true)), legacy = true,
            progress = t("Открываю ссылку", "Opening the link", "Havola ochilmoqda")),
        Tool("maps", "Opens a place or address in the maps app.", A, ACTION, CORE, listOf(s("q", req = true)), legacy = true,
            progress = t("Открываю карту", "Opening maps", "Xarita ochilmoqda")),
        Tool("call", "Calls a contact or number (the user confirms on screen).", S, ACTION, CORE, listOf(s("to", ex = "contact or number", req = true)), legacy = true,
            progress = t("Звоню: %s", "Calling %s", "Qo'ng'iroq: %s"), arg = "to"),
        Tool("message", "SENDS a message (sms, whatsapp or telegram) after a short cancel countdown; the user confirms first. If the recipient or the text is unclear, ask instead of guessing; never claim it was delivered. The text is exactly what the user wants to say to the recipient: when they only said to send a message/sms without saying what, ask «what should I write?» and never put the word «message», «sms» or «xabar» in the text.",
            S, ACTION, CORE, listOf(s("app", ex = "sms|whatsapp|telegram", enum = listOf("sms", "whatsapp", "telegram")), s("to", ex = "contact or number", req = true), s("text", ex = "...", req = true)), legacy = true,
            note = "SENDS the message by itself after a short cancel countdown the user can tap; announce it naturally («Отправляю Алишеру: …» / \"Sending to Alisher: …\" / \"Alisherga yuboryapman: …\"), never claim it was delivered; if the recipient or the text is unclear, ask instead of guessing",
            progress = t("Готовлю сообщение", "Preparing the message", "Xabar tayyorlanmoqda")),
        Tool("email", "Opens an email draft you write; the user taps Send (never say it was sent).", A, ACTION, CORE,
            listOf(s("to", ex = "contact or address", req = true), s("subject"), s("body")), legacy = true,
            note = "opens a draft you write, the user taps Send (never say it was sent)", progress = t("Пишу письмо", "Writing the email", "Xat yozilmoqda")),
        Tool("sticker", "Shows a sticker of yourself INSIDE your reply: use it OFTEN as your reaction (greetings, thanks, jokes, praise, comfort, goodbye, surprise); share:true only if the user asks to send it to someone.",
            A, ACTION, CORE, listOf(s("mood", ex = "love", req = true), s("caption", ex = "1-3 words"), b("share", ex = false)), legacy = true),
        // ---- observations ----
        Tool("weather", "Weather now + 3 days (empty city = phone location).", R, OBS, CORE, listOf(s("city", ex = "Tashkent")), legacy = true,
            note = "now + 3 days (empty = phone location)", progress = t("Смотрю погоду", "Checking the weather", "Ob-havoni ko'ryapman")),
        Tool("currency", "Live exchange rate conversion.", R, OBS, CORE, listOf(n("amount", ex = 100, req = true), s("from", ex = "USD", req = true), s("to", ex = "UZS", req = true)), legacy = true,
            note = "live rate", progress = t("Смотрю курс", "Checking the rate", "Kursni ko'ryapman")),
        Tool("calc", "Exact calculator: use for EVERY calculation, never compute yourself (percent: 200*15/100; sqrt sin cos tan log ln abs pi e).",
            R, OBS, CORE, listOf(s("expr", ex = "12*(3+4)/5", req = true)), legacy = true,
            note = "use for EVERY calculation, never compute yourself (percent: 200*15/100; sqrt sin cos tan log ln abs pi e)"),
        Tool("convert", "Unit conversion: length mass volume speed area time data temperature.", R, OBS, CORE,
            listOf(n("value", ex = 5, req = true), s("from", ex = "km", req = true), s("to", ex = "mi", req = true)), legacy = true,
            note = "length mass volume speed area time data temperature (news: web_search)"),
        Tool("phone_status", "Battery, storage, memory, network, do-not-disturb, volume, brightness.", R, OBS, CORE, legacy = true,
            note = "battery, storage, memory, network, dnd, volume, brightness"),
        Tool("notifications", "Latest notifications (untrusted data).", R, OBS, CORE, legacy = true, note = "latest notifications",
            progress = t("Читаю уведомления", "Reading notifications", "Bildirishnomalarni o'qiyapman")),
        Tool("briefing", "Morning briefing data (weather, tasks, events).", R, OBS, CORE, legacy = true, note = "morning briefing"),
        Tool("now_playing", "What music is playing now.", R, OBS, CORE, legacy = true),

        // ---- screen (accessibility) ----
        Tool("screen_text", "Reads what is on the phone screen: numbered elements ([n]) you can tap_node, with their state.", R, OBS, SCREEN, legacy = true,
            note = "reads what is on the phone screen: send it ALONE with an empty say, you get the visible texts next turn and then can tap_text / scroll / type_text / answer (use it for \"what is on my screen\", \"tap the Play button\", \"open the second result\")",
            progress = t("Смотрю на экран", "Reading the screen", "Ekranni o'qiyapman")),
        Tool("look", "Takes a screenshot and shows it to you as an image, for things the screen text does not describe (pictures, maps, icons without labels, games).",
            R, DEV, SCREEN, progress = t("Смотрю на экран", "Looking at the screen", "Ekranga qarayapman")),
        Tool("tap_node", "Taps element [n] from the latest screen description.", A, ACTION, SCREEN, listOf(i("index", ex = 3, req = true)), legacy = true,
            note = "(number from the screen text)", progress = t("Нажимаю", "Tapping", "Bosyapman")),
        Tool("tap_text", "Taps the visible element with this text.", A, ACTION, SCREEN, listOf(s("text", req = true)), legacy = true,
            progress = t("Нажимаю «%s»", "Tapping \"%s\"", "«%s» bosilmoqda"), arg = "text"),
        Tool("type_text", "Types text into the focused (or first) input field; submit:true presses Enter/Search after it.", A, ACTION, SCREEN,
            listOf(s("text", req = true), b("submit")), legacy = true, progress = t("Печатаю", "Typing", "Yozyapman")),
        Tool("press_enter", "Presses Enter / the keyboard action key in the focused field.", A, ACTION, SCREEN, legacy = true),
        Tool("scroll", "Scrolls the screen content.", A, ACTION, SCREEN, listOf(s("dir", ex = "up|down|left|right", req = true, enum = listOf("up", "down", "left", "right"))), legacy = true,
            progress = t("Листаю", "Scrolling", "Aylantiryapman")),
        Tool("back", "Presses Back.", A, ACTION, SCREEN, legacy = true, progress = t("Назад", "Going back", "Orqaga")),
        Tool("home", "Goes to the home screen.", A, ACTION, SCREEN, legacy = true, progress = t("На главный экран", "Going home", "Bosh ekranga")),
        Tool("recents", "Opens the recent apps list.", A, ACTION, SCREEN, legacy = true),
        Tool("notification_shade", "Pulls down the notification shade.", A, ACTION, SCREEN, legacy = true, note = "(pulls down the shade)"),
        Tool("quick_settings", "Opens quick settings (Wi-Fi, Bluetooth, flashlight tiles...).", A, ACTION, SCREEN, legacy = true),
        Tool("scroll_to_text", "Scrolls until an element with this text is visible (at most ~8 swipes); then use the new screen numbers.", A, DEV, SCREEN,
            listOf(s("text", req = true), s("dir", ex = "down", enum = listOf("down", "up"))), progress = t("Ищу «%s»", "Looking for \"%s\"", "«%s» qidirilmoqda"), arg = "text"),
        Tool("tap_xy", "Taps a point. space=screen (pixels, default) or image (pixels of the last look screenshot).", A, DEV, SCREEN,
            listOf(i("x", req = true), i("y", req = true), s("space", ex = "screen", enum = listOf("screen", "image"))), progress = t("Нажимаю", "Tapping", "Bosyapman")),
        Tool("long_press", "Long-presses element [index] or a point x,y.", A, DEV, SCREEN, listOf(i("index", ex = null), i("x", ex = null), i("y", ex = null), s("space", ex = null, enum = listOf("screen", "image"))),
            progress = t("Долгое нажатие", "Long-pressing", "Uzoq bosyapman")),
        Tool("double_tap", "Double-taps element [index] or a point x,y.", A, DEV, SCREEN, listOf(i("index", ex = null), i("x", ex = null), i("y", ex = null), s("space", ex = null, enum = listOf("screen", "image")))),
        Tool("swipe", "Swipes from x1,y1 to x2,y2 (screen pixels) in ms milliseconds.", A, DEV, SCREEN,
            listOf(i("x1", req = true), i("y1", req = true), i("x2", req = true), i("y2", req = true), i("ms", ex = 300)), progress = t("Провожу пальцем", "Swiping", "Suryapman")),
        Tool("drag", "Presses and holds at x1,y1, then drags to x2,y2 (move icons, sliders).", A, DEV, SCREEN,
            listOf(i("x1", req = true), i("y1", req = true), i("x2", req = true), i("y2", req = true), i("ms", ex = 900))),
        Tool("select_all", "Selects all text in the focused field.", A, DEV, SCREEN),
        Tool("copy", "Copies the selected text (or element [index]'s text) to the clipboard.", A, DEV, SCREEN, listOf(i("index", ex = null))),
        Tool("paste", "Pastes the clipboard into the focused field.", A, DEV, SCREEN),
        Tool("wait", "Waits 1-10 seconds for a page or app to load.", R, LOOP, SCREEN, listOf(i("seconds", ex = 2, req = true)),
            progress = t("Жду загрузку", "Waiting for it to load", "Yuklanishini kutyapman")),

        // ---- phone ----
        Tool("alarm_cancel", "Turns off an alarm (no args = the next alarm).", A, ACTION, PHONE, listOf(i("hour", ex = 7), i("minute", ex = 30)), legacy = true, note = "(no args = next alarm)"),
        Tool("alarm_list", "Shows the alarms.", A, ACTION, PHONE, legacy = true),
        Tool("timer_cancel", "Cancels the timer.", A, ACTION, PHONE, legacy = true),
        Tool("timer_list", "Shows the timers.", A, ACTION, PHONE, legacy = true),
        Tool("answer_call", "Answers the ringing call.", A, ACTION, PHONE, legacy = true),
        Tool("end_call", "Ends the current call.", A, ACTION, PHONE, legacy = true),
        Tool("reject_call", "Declines the ringing call and SMSes the caller (empty text = standard \"busy\" text): use it, not end_call + message, for \"reject and tell them I'm busy\".",
            S, ACTION, PHONE, listOf(s("text")), legacy = true,
            note = "declines the ringing call and SMSes the caller (empty text = standard \"busy\" text): use it, not end_call + message, for \"reject and tell them I'm busy\""),
        Tool("speaker", "Speakerphone on/off.", A, ACTION, PHONE, listOf(b("on", req = true)), legacy = true),
        Tool("auto_reply", "Auto-answers private chat messages (not groups/unknown numbers) and switches off by itself after hours 1-12; for \"I'm driving/busy/in a meeting\", empty text = standard.",
            S, ACTION, PHONE, listOf(b("on", req = true), s("mode", ex = "busy|driving", enum = listOf("busy", "driving")), i("hours", ex = 2), s("text")), legacy = true,
            note = "auto-answers private chat messages (not groups/unknown numbers) and switches off by itself after \"hours\" 1-12; for \"I'm driving/busy/in a meeting\", empty text = standard"),
        Tool("dnd", "Do Not Disturb on/off.", A, ACTION, PHONE, listOf(b("on", req = true)), legacy = true, note = "Do Not Disturb"),
        Tool("screen_timeout", "How long the screen stays on.", A, ACTION, PHONE, listOf(i("seconds", ex = 60, req = true)), legacy = true),
        Tool("find_phone", "Loud alarm for 30 s so the phone can be found (on:false stops).", A, ACTION, PHONE, listOf(b("on", req = true)), legacy = true,
            note = "loud alarm for 30 s (on:false stops)"),
        Tool("lock", "Locks the screen.", A, ACTION, PHONE, legacy = true),
        Tool("screenshot", "Saves a screenshot to the gallery (for the user; to see the screen yourself use look).", A, ACTION, PHONE, legacy = true),
        Tool("power_menu", "Opens the power menu.", A, ACTION, PHONE, legacy = true),
        Tool("brightness", "Screen brightness in percent.", A, ACTION, PHONE, listOf(i("percent", ex = 50, req = true)), legacy = true),
        Tool("settings", "Opens a settings screen (only opens it; apps cannot flip wifi/bluetooth/airplane themselves, say so).", A, ACTION, PHONE,
            listOf(s("panel", ex = "wifi|bluetooth|volume|airplane|battery|storage|display|location", enum = listOf("wifi", "bluetooth", "volume", "airplane", "battery", "storage", "display", "location"))), legacy = true,
            note = "(only opens the screen; apps cannot flip wifi/bluetooth/airplane themselves, say so)", progress = t("Открываю настройки", "Opening Settings", "Sozlamalar ochilmoqda")),
        Tool("toggle", "Wi-Fi, Bluetooth, airplane mode or mobile data on/off, best effort: Android forbids apps to flip most of them directly, so this opens the right panel and tells you whether to tap the switch yourself (then check the screen).",
            A, DEV, PHONE, listOf(s("setting", req = true, enum = listOf("wifi", "bluetooth", "airplane", "mobile_data", "location", "nfc", "hotspot")), b("on", req = true)),
            progress = t("Переключаю %s", "Switching %s", "%s almashtirilmoqda"), arg = "setting"),
        Tool("open_intent", "Opens a safe system screen: view (https URL), dial (number, does not call), sms_compose / email_compose (draft only), settings (page: wifi, bluetooth, apps, app_details, notifications, battery, display, sound, location, security, accessibility, date, language, storage, data_usage, nfc, internet), maps (q or navigate), store (Play Store page of package).",
            A, DEV, PHONE, listOf(s("action", req = true, enum = listOf("view", "dial", "sms_compose", "email_compose", "settings", "maps", "navigate", "store")),
                s("target", "url, number, address, settings page, place or package"), s("text", "draft text for compose")),
            progress = t("Открываю", "Opening", "Ochilmoqda")),
        Tool("notification_reply", "Replies to the newest notification that can be answered.", S, ACTION, PHONE, listOf(s("text", req = true)), legacy = true,
            note = "replies to the newest notification"),

        // ---- data ----
        Tool("contacts_search", "Finds contacts by name (numbers and labels).", R, DEV, DATA, listOf(s("q", req = true)),
            progress = t("Ищу контакт", "Looking up the contact", "Kontakt qidirilmoqda")),
        Tool("sms_read", "Reads the latest SMS messages (only when the user allowed it in settings).", R, DEV, DATA, listOf(i("count", ex = 5), s("from", "contact or number filter", ex = null)),
            progress = t("Читаю SMS", "Reading SMS", "SMS o'qilmoqda")),
        Tool("files_find", "Finds files by name in Downloads or Pictures (read only).", R, DEV, DATA,
            listOf(s("q", "part of the name; empty = newest"), s("where", ex = "downloads", enum = listOf("downloads", "pictures", "all")), i("count", ex = 10))),
        Tool("app_list", "Lists installed apps (name and package), optionally filtered.", R, DEV, DATA, listOf(s("q", ex = null))),
        Tool("app_info", "Version, install date and permissions of an installed app.", R, DEV, DATA, listOf(s("name", "app name or package", req = true))),
        Tool("clipboard_get", "Reads the clipboard (Android may block it in the background).", R, DEV, DATA),
        Tool("clipboard_set", "Puts text on the clipboard.", A, DEV, DATA, listOf(s("text", req = true))),
        Tool("share_text", "Opens the Android share sheet with this text; the user picks where to send it.", S, DEV, DATA, listOf(s("text", req = true))),
        Tool("list_read", "Reads a list (shopping...).", R, OBS, ORG, listOf(s("list", ex = "shopping")), legacy = true),

        // ---- organizer ----
        Tool("add_task", "Adds a task.", A, ACTION, ORG, listOf(s("title", req = true), s("due", ex = "yyyy-MM-dd HH:mm"), s("notes")), legacy = true),
        Tool("done_task", "Marks a task done.", A, ACTION, ORG, listOf(s("title", req = true)), legacy = true),
        Tool("delete_task", "Deletes a task.", S, ACTION, ORG, listOf(s("title", req = true)), legacy = true),
        Tool("add_event", "Adds a calendar event.", A, ACTION, ORG,
            listOf(s("title", req = true), s("start", ex = "yyyy-MM-dd HH:mm", req = true), s("end", ex = "yyyy-MM-dd HH:mm"), s("location")), legacy = true),
        Tool("list_add", "Adds item(s) to a list.", A, ACTION, ORG, listOf(s("list", ex = "shopping"), s("item", ex = "milk"), P("items", "array", "several items", null)), legacy = true,
            note = "(or \"items\":[...])"),
        Tool("list_remove", "Removes an item from a list.", A, ACTION, ORG, listOf(s("list"), s("item", req = true)), legacy = true),
        Tool("list_clear", "Clears a list.", S, ACTION, ORG, listOf(s("list")), legacy = true),

        // ---- mascot ----
        Tool("outfit", "Puts on or takes off an outfit item or costume. Items: hat cap crown bow headphones flower party santa beanie pumpkin wizard ninja samurai pirate astronaut halo horns catears glasses sunglasses mask visor scarf bowtie cape tie fire ice lightning hearts sakura stars wand sword umbrella lollipop; costumes: ninja wizard samurai superhero pirate astronaut fire_mage ice_queen angel devil; \"none\" removes everything.",
            A, ACTION, MASCOT, listOf(s("item", ex = "<item or costume>", req = true), b("on")), legacy = true,
            note = ": items hat cap crown bow headphones flower party santa beanie pumpkin wizard ninja samurai pirate astronaut halo horns catears glasses sunglasses mask visor scarf bowtie cape tie fire ice lightning hearts sakura stars wand sword umbrella lollipop; costumes ninja wizard samurai superhero pirate astronaut fire_mage ice_queen angel devil; \"none\" removes everything"),
        Tool("species", "Changes the pet species (map the user's words in any language).", A, ACTION, MASCOT,
            listOf(s("name", ex = "cat|dog|bunny|bear|panda|fox|frog|penguin|capybara|hamster|owl|duck|pig|koala|lion|tiger", req = true)), legacy = true,
            note = "(map the user's words in any language)"),
        Tool("size", "Mascot size: bigger, smaller, normal or 0.15-6.0.", A, ACTION, MASCOT, listOf(s("value", ex = "bigger|smaller|normal|0.15-6.0", req = true)), legacy = true),
        Tool("move", "Moves the mascot. status_left = in the status bar just right of the clock, status_right = left of the system icons, status_center = middle of the status bar like a camera island; use them for \"next to the clock\", \"at the very top\", or when it blocks a video.",
            A, ACTION, MASCOT, listOf(s("where", ex = "top|bottom|left|right|center|bottom_left|bottom_right|top_left|top_right|status_left|status_right|status_center", req = true)), legacy = true,
            note = "(status_left = in the status bar just right of the clock, status_right = in the status bar left of the system icons, status_center = the middle of the status bar like a phone's camera island; use them when the user says \"next to the clock\", \"at the very top\", or complains it blocks a video)"),
        Tool("quiet", "Hides the mascot completely so a movie is not disturbed (on:false shows it again).", A, ACTION, MASCOT, listOf(b("on", req = true)), legacy = true,
            note = "(hide yourself completely so a movie is not disturbed; on:false shows you again)"),
        Tool("game", "Remembers the game state and answer chips (empty state ends the game).", A, ACTION, MASCOT,
            listOf(s("state", ex = "what to remember about the game", req = true), P("chips", "array", "the possible answers the user can tap (for a quiz: 3-4 options, never your feedback)", listOf("a", "b"))), legacy = true, note = "(empty state ends the game)"),
        Tool("set_mode", "Study mode (patient tutor) or normal.", A, ACTION, MASCOT, listOf(s("mode", ex = "study|normal", req = true, enum = listOf("study", "normal"))), legacy = true),
        Tool("set_persona", "Sets your personality (empty text = back to normal).", A, ACTION, MASCOT, listOf(s("text", ex = "funny and cheerful")), legacy = true,
            note = "(empty text = back to normal)"),
    )

    private val byName: Map<String, Tool> = ALL.associateBy { it.name }
    fun get(name: String): Tool? = byName[name]
    val names: Set<String> get() = byName.keys

    /** Tools offered for these groups, in registry order. */
    fun select(groups: Set<Group>): List<Tool> = ALL.filter { it.group == Group.CORE || it.group in groups }

    // ---------------------------------------------------------------- native schemas
    private fun paramSchema(p: P): JSONObject {
        val o = JSONObject().put("type", p.type)
        if (p.desc.isNotEmpty()) o.put("description", p.desc)
        if (p.enum != null) o.put("enum", JSONArray(p.enum))
        if (p.type == "array") o.put("items", JSONObject().put("type", "string"))
        return o
    }

    /** JSON schema of a tool's arguments (object with properties and required). */
    fun schema(t: Tool): JSONObject {
        val props = JSONObject()
        val req = JSONArray()
        for (p in t.params) { props.put(p.name, paramSchema(p)); if (p.req) req.put(p.name) }
        val o = JSONObject().put("type", "object").put("properties", props)
        if (req.length() > 0) o.put("required", req)
        return o
    }

    /** Anthropic Messages API `tools`. */
    fun anthropic(tools: List<Tool>): JSONArray = JSONArray().also { a ->
        for (t in tools) a.put(JSONObject().put("name", t.name).put("description", t.desc).put("input_schema", schema(t)))
    }

    /** OpenAI-compatible `tools` (Groq, OpenRouter, Ollama, custom servers). */
    fun openAi(tools: List<Tool>): JSONArray = JSONArray().also { a ->
        for (t in tools) a.put(JSONObject().put("type", "function").put("function",
            JSONObject().put("name", t.name).put("description", t.desc).put("parameters", schema(t))))
    }

    /** Gemini `tools` with functionDeclarations (no parameters key for tools without arguments: Gemini rejects empty objects). */
    fun gemini(tools: List<Tool>): JSONArray {
        val decl = JSONArray()
        for (t in tools) {
            val d = JSONObject().put("name", t.name).put("description", t.desc)
            if (t.params.isNotEmpty()) d.put("parameters", schema(t))
            decl.put(d)
        }
        return JSONArray().put(JSONObject().put("functionDeclarations", decl))
    }

    // ---------------------------------------------------------------- legacy JSON mode
    /** The example object of a tool in the legacy block, e.g. {"do":"alarm","hour":7,"minute":30,"label":""}. */
    fun example(t: Tool): String {
        val sb = StringBuilder("{\"do\":\"").append(t.name).append('"')
        for (p in t.params) {
            val ex = p.ex ?: continue
            sb.append(",\"").append(p.name).append("\":")
            sb.append(when (ex) {
                is String -> JSONObject.quote(ex)
                is List<*> -> JSONArray(ex).toString()
                else -> ex.toString()
            })
        }
        return sb.append('}').toString()
    }

    private fun line(t: Tool): String {
        val note = t.note ?: if (t.legacy) "" else t.desc
        return if (note.isEmpty()) example(t) else example(t) + " " + note
    }

    /**
     * The action list of the legacy JSON mode: every original action always (same behaviour as before), the new tools of
     * the selected groups, then the observations line and the multi-step rule.
     */
    fun legacyBlock(groups: Set<Group>): String {
        val sb = StringBuilder()
        val shown = ALL.filter { it.name != "done" && it.name != "plan" && (it.legacy || it.group == Group.CORE || it.group in groups) }
        for (t in shown) if (t.exec != Exec.OBSERVE || t.name == "web_search" || t.name == "screen_text") {
            if (t.exec == Exec.LOOP && t.name == "more_tools") continue
            sb.append(line(t)).append('\n')
        }
        sb.append("Observations (send ALONE with empty say, you get the data next turn): ")
        sb.append(shown.filter { it.exec == Exec.OBSERVE && it.name != "web_search" && it.name != "screen_text" }.joinToString("; ") { line(it) })
        sb.append('\n')
        sb.append(line(get("more_tools")!!)).append(" (send ALONE)\n")
        sb.append("Set \"more\":true when you must SEE the result of your actions before continuing (multi-step tasks such as opening an app and then searching or tapping inside it). Do at most 3 actions per step; after each step you get the result and the screen; set more:false in your last step with a short spoken confirmation.")
        return sb.toString()
    }

    // ---------------------------------------------------------------- progress label
    /** Short status text for the mascot while the tool runs ("Opening Settings", "Tapping «Wi-Fi»"). */
    fun progress(name: String, args: JSONObject, lang: String?, targetLabel: String? = null): String {
        val t = get(name)
        val tpl = t?.progress
        if (tpl == null) return pick(lang, "Выполняю: ", "Doing: ", "Bajarilmoqda: ") + name.replace('_', ' ')
        val raw = pick(lang, tpl)
        val v = (t.arg?.let { if (args.isNull(it)) "" else args.optString(it, "") } ?: "").ifEmpty { targetLabel.orEmpty() }.trim().take(28)
        if (!raw.contains("%s")) {
            return if (targetLabel.isNullOrBlank() || (name != "tap_node" && name != "tap_xy" && name != "look")) raw
            else raw + " «" + targetLabel.trim().take(28) + "»"
        }
        return if (v.isEmpty()) raw.replace(" «%s»", "").replace(" \"%s\"", "").replace("«%s» ", "").replace(": %s", "").replace(" %s", "").replace("%s ", "").trim()
        else raw.replace("%s", v)
    }
}
