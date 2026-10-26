# Bubby (Flutter) — build contract

Project: `app` (Flutter app, Android only).
Toolchain (no spaces in paths, already installed by the lead):
```
export JAVA_HOME=<JDK 17 path>
export ANDROID_HOME=<Android SDK path>
export PATH=<Flutter SDK>/bin:$JAVA_HOME/bin:$PATH
```
Kotlin package: `com.example.buddy`, in `app/android/app/src/main/kotlin/com/example/buddy/`.
Dart: `app/lib/`. UI language of the app: Russian. No third-party Dart packages (SDK only).

## Architecture
Flutter = app UI (setup, settings, tasks, calendar). Native Kotlin = the always-on mascot:
an overlay service (draggable island over other apps), voice in/out, an AI "brain" that returns
JSON `{emotion, lang, say, actions[]}`, an action executor (alarm, call, music, accessibility…),
tasks + reminders, calendar. Flutter talks to native through ONE MethodChannel: `buddy/native`.
Settings, tasks and the overlay live in native SharedPreferences file `buddy`.

## File ownership (never edit a file you do not own; ask the lead instead)
| Owner | Files |
|---|---|
| lead | `BuddyService.kt`, `Voice.kt`, `DragLayout.kt`, `Brain.kt`, `MainActivity.kt`, `AndroidManifest.xml`, gradle files, `NativeChannel.kt` |
| emotions | `BuddyView.kt` (+ `Moods` object inside it) |
| actions | `Actions.kt`, `BuddyAccess.kt`, `CallWatcher.kt`, `res/xml/accessibility_config.xml`, `res/values/strings.xml` |
| tasks | `TaskStore.kt`, `Reminders.kt` (incl. `ReminderReceiver`, `BootReceiver`), `CalendarStore.kt` |
| flutter | everything under `lib/`, `pubspec.yaml` assets section |

Manifest needs from non-lead owners: write them to `app/MANIFEST_NEEDS_<owner>.md`
(permissions, `<service>`/`<receiver>` blocks, `<queries>`), the lead merges them.

## Kotlin interfaces (exact signatures)

### emotions — `BuddyView.kt`
```kotlin
object Moods {
    val ALL: List<String>                 // moods the AI may choose (excludes internal: idle, sleepy, listen, think)
    val DESCRIPTIONS: Map<String, String> // mood -> one short English line for the AI prompt, e.g. "excited" -> "very happy and energetic"
    val LABELS: Map<String, String>       // mood -> short Russian label shown in the status line ("Радуется")
}
class BuddyView(c: Context) : View(c) {
    var mood: String          // set any name from Moods.ALL or idle/sleepy/listen/think; unknown -> idle
    var scaleF: Float
    fun setPalette(base: Int, light: Int)
    fun flash(mood: String, ms: Long)     // show a mood briefly, then return to the previous one
}
```
Keep ALL existing moods (happy laugh love surprised sad confused offended angry idle sleepy listen think) and add
at least 12 new ones (e.g. excited shy proud cool wink crying dizzy scared silly smug worried tired dance curious).
While `mood == "idle"` the view must also do random micro-expressions by itself (blink, wink, look around, yawn,
tiny smile) at random intervals — no timers outside the view; 0 cost when detached.
Drawing style: all in Canvas code like the existing file (no images). Do not change the body/ears design.

### tasks — `TaskStore.kt`, `Reminders.kt`, `CalendarStore.kt`
```kotlin
class Task(val id: String, var title: String, var notes: String, var dueMillis: Long?, var remind: Boolean, var done: Boolean)
class TaskStore(ctx: Context) {
    fun all(): List<Task>                                   // sorted: open first, by due date
    fun add(title: String, notes: String = "", dueMillis: Long? = null, remind: Boolean = true): Task
    fun update(t: Task); fun delete(id: String); fun setDone(id: String, done: Boolean)
    fun findByTitle(q: String): Task?                       // case-insensitive contains, open tasks first
    fun summaryForPrompt(): String                          // <= 15 short lines: open tasks with due dates, "none" if empty
    fun toJson(t: Task): org.json.JSONObject                // {id,title,notes,dueMillis(nullable),remind,done}
    fun fromJson(j: org.json.JSONObject): Task              // id empty/missing -> new id
}
object Reminders {
    const val ACTION_SPEAK = "com.example.buddy.SPEAK"       // intent action the lead's service handles; extra "text": String
    fun schedule(ctx: Context, t: Task)                      // AlarmManager (exact if allowed, else inexact) at dueMillis when remind && !done
    fun cancel(ctx: Context, id: String)
    fun rescheduleAll(ctx: Context)
    fun canExact(ctx: Context): Boolean
}
// ReminderReceiver: posts a heads-up notification "Напоминание: <title>" (own channel) AND, if BuddyService is running,
// startService(Intent(ctx, BuddyService::class.java).setAction(Reminders.ACTION_SPEAK).putExtra("text", "<title>")).
// BootReceiver: calls Reminders.rescheduleAll on BOOT_COMPLETED.
class CalEvent(val id: Long, val title: String, val startMillis: Long, val endMillis: Long, val location: String)
class CalendarStore(ctx: Context) {
    fun hasPermission(): Boolean                             // READ_CALENDAR && WRITE_CALENDAR
    fun events(fromMillis: Long, toMillis: Long): List<CalEvent>   // [] without permission
    fun add(title: String, startMillis: Long, endMillis: Long, location: String = "", notes: String = ""): Long?  // null on failure; uses first writable calendar
    fun delete(id: Long): Boolean
    fun summaryForPrompt(): String                           // events in the next 48h, <= 10 lines, "none" if empty
    fun toJson(e: CalEvent): org.json.JSONObject             // {id,title,startMillis,endMillis,location}
}
```

### actions — `Actions.kt`, `BuddyAccess.kt`, `CallWatcher.kt`
```kotlin
class Actions(private val ctx: Context) {
    fun run(a: org.json.JSONObject): String?   // null = ok, otherwise a SHORT Russian error text. Never throws.
}
class BuddyAccess : android.accessibilityservice.AccessibilityService() {
    companion object { val enabled: Boolean   // true when connected
        fun global(action: Int): Boolean; fun swipe(dir: String): Boolean
        fun clickText(t: String): Boolean; fun typeText(t: String): Boolean }
}
class CallWatcher(private val ctx: Context, private val onRinging: (name: String?) -> Unit, private val onEnded: () -> Unit) {
    fun start(); fun stop()                    // dynamic PHONE_STATE receiver; resolves caller name from contacts when allowed
    companion object { fun looksLikeAnswer(s: String): Boolean; fun looksLikeReject(s: String): Boolean }  // ru/uz/en keywords, lowercase
}
```
`Actions.run` handles every action of the lead's prompt (see `Brain.kt` list) plus:
`{"do":"add_task","title":"","due":"yyyy-MM-dd HH:mm","notes":""}` (due optional, local time → millis; schedules reminder via `Reminders`),
`{"do":"done_task","title":""}`, `{"do":"delete_task","title":""}`,
`{"do":"add_event","title":"","start":"yyyy-MM-dd HH:mm","end":"yyyy-MM-dd HH:mm","location":""}` (end optional = start+1h),
`{"do":"set_persona","text":"funny and cheerful"}` (writes pref `persona` in SharedPreferences `buddy`; empty text clears).
Calls: answer via `TelecomManager.acceptRingingCall`, end via `endCall`. Background activity starts need
`FLAG_ACTIVITY_NEW_TASK`. Music: `MediaStore.INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH`, fallback YouTube search URL.

## MethodChannel `buddy/native` (Dart → native). All args/results are JSON-like Maps/Lists.
| Method | Args | Result |
|---|---|---|
| `getSettings` | – | `{key:String, lang:"auto"/"uz"/"ru"/"en", tts:bool, conv:bool, voice:bool, persona:String, running:bool}` |
| `saveSettings` | any subset of the keys above (except running) | true |
| `permissionsStatus` | – | `{overlay,accessibility,mic,phone,calendar,notifications,contacts,exactAlarm: bool}` |
| `requestPermissions` | `{group:"runtime"}` asks mic+phone(CALL_PHONE, ANSWER_PHONE_CALLS, READ_PHONE_STATE, READ_CALL_LOG)+calendar+contacts+notifications | true |
| `openSettings` | `{page:"overlay"/"accessibility"/"exactAlarm"/"app"}` | true |
| `startBuddy` / `stopBuddy` | – | true / true |
| `listTasks` | – | `[Task json]` |
| `saveTask` | Task json (id empty = new) | Task json |
| `deleteTask` | `{id}` | true |
| `setTaskDone` | `{id, done}` | true |
| `listEvents` | `{from:int, to:int}` (epoch millis) | `[CalEvent json]` |
| `addEvent` | `{title,startMillis,endMillis,location,notes}` | `{id:int}` or error `FAILED` |
| `deleteEvent` | `{id:int}` | true |

Errors: `result.error(code, message, null)`; Dart wrapper turns them into a `NativeException`.
Task JSON: `{id,title,notes,dueMillis|null,remind,done}`. Event JSON: `{id,title,startMillis,endMillis,location}`.

## PHASE 2 addendum (Gemma on phone, outfits, stickers, games)
Extra Gradle dependency allowed ONLY for the gemma owner: `com.google.ai.edge.litertlm:litertlm-android:0.18.1` (lead adds it to gradle).
Additional ownership: `gemma` → `LocalLlm.kt`, `ModelStore.kt`, `ModelDownloadService.kt`; `emotions` → `Outfits.kt` + small edit of `BuddyView.kt`;
`stickers` → `Stickers.kt`, `StickerProvider.kt`; `games` → `Games.kt`; `actions` also adds action cases below; `flutter` adds screens.
Write MANIFEST_NEEDS_<owner>.md for the lead as before.

```kotlin
object ModelStore { val IDS = listOf("e2b","e4b")
  fun file(ctx: Context, id: String): File          // filesDir/models/<id>.litertlm ; partial = <id>.litertlm.part
  fun status(ctx: Context): Map<String, Any?>        // {e2b:{state:"none|downloading|paused|ready|error",done:Long,total:Long,error:String?}, e4b:{..}, ramGb:Double, freeGb:Double}
  fun start(ctx: Context, id: String); fun cancel(ctx: Context); fun delete(ctx: Context, id: String) }
object LocalLlm { fun available(ctx: Context): Boolean          // pref gemma_model (e2b|e4b, default e2b) file is ready
  fun chat(ctx: Context, sys: String, history: org.json.JSONArray): String   // blocking; throws Exception with Russian message
  fun warmUp(ctx: Context); fun release() }
object Outfits { val SLOTS: Map<String, List<String>>   // head: hat cap crown bow headphones flower party santa; eyes: glasses sunglasses; neck: scarf bowtie
  val LABELS: Map<String, String>                        // Russian names
  fun parse(pref: String): Set<String>                   // pref `outfit` = comma list, max one item per slot
  fun draw(cv: android.graphics.Canvas, p: android.graphics.Paint, items: Set<String>, mood: String, t: Float) }
// BuddyView: `var outfit: Set<String>`; Outfits.draw is called inside the translated/scaled canvas just before cv.restore()
object Stickers { fun render(ctx: Context, mood: String, caption: String, outfit: Set<String>): android.graphics.Bitmap   // 512px, transparent, white die-cut border
  fun png(ctx: Context, mood: String, caption: String): ByteArray
  fun share(ctx: Context, mood: String, caption: String): String? }   // share chooser (NEW_TASK, read grant); null = ok else Russian error
object Games { fun contextLine(ctx: Context): String      // "Current game: <pref game_state or none>. Fresh random: n100=57, d6=4, rps=scissors"
  fun chips(ctx: Context): List<String> }                  // reads pref game_chips (JSON array string)
```
New actions in `Actions.run`: `{"do":"outfit","item":"hat|none","on":true}` (pref `outfit`; "none" clears; adding replaces same slot),
`{"do":"sticker","mood":"love","caption":""}` → Stickers.share, `{"do":"game","state":"free text memory of the game","chips":["камень","ножницы","бумага"]}`
(prefs `game_state`, `game_chips`; empty state = game over, clears both).
New MethodChannel methods (lead implements in NativeChannel, flutter calls): `modelStatus`→map above; `modelDownload{id}`; `modelCancel`; `modelDelete{id}`;
`renderSticker{mood,caption}`→Uint8List PNG; `shareSticker{mood,caption}`→true; `startGame{name}`→true (makes the buddy start that game);
`batteryStatus`→`{ignoring:bool}`; `openSettings{page:"battery"}`. `getSettings` gains `provider:"cloud"|"gemma"`, `gemmaModel:"e2b"|"e4b"`, `outfit:String`, `lastCrash:String`;
`saveSettings` accepts `provider`, `gemmaModel`, `outfit`. Tell users: Gemma needs 4 GB+ RAM (E2B) / 6 GB+ (E4B).

## PHASE 3 addendum — "Agents": watch coding agents (Claude Code) and approve from the phone, like the coucou project
Protocol: read `ref-coucou/coucou/docs/ANDROID_LINK.md` (protocol v1: TLS+pinned self-signed cert, newline JSON, pairing link `coucou://pair?...`, hello/welcome/sessions/approval/decision/ping). We implement protocol v1 ourselves in our own code (coucou's code is MIT; the Mochi character, name, icon and sounds are NOT ours to use — never copy them; we use our own scheme name too: pairing links may be `coucou://pair?...` for interop but our UI says "Bubby"). Reference implementations to LEARN from (MIT, give a one-line attribution comment where you adapt): `ref-coucou/coucou/android/app/` (Kotlin client), `ref-coucou/coucou/android/tools/dev-desktop.mjs` (Node stand-in desktop), `ref-coucou/coucou/windows/src-tauri/src/phone_link/` (Rust server), `ref-coucou/coucou/windows/hook/` (how Claude Code hooks reach the app).
Owners: `agentlink` (Kotlin): `AgentLink.kt`, `PairingStore.kt`, `ConfirmActivity.kt`, `ApprovalCard.kt` in the Kotlin dir; `bridge` (Node, no npm deps): everything under `agent-bridge/`; `flutter`: `lib/screens/agents_screen.dart` + native.dart additions.
Kotlin interface (agentlink owner designs details; lead wires them): 
```kotlin
class PairingStore(ctx) { fun save(link: ParsedLink); fun load(): ParsedLink?; fun clear() }  // token encrypted with an Android Keystore AES-GCM key (framework APIs, no androidx)
data class ParsedLink(val host: String, val port: Int, val fp: String, val token: String, val name: String)
fun parsePairLink(s: String): ParsedLink?              // strict validation per the doc (token 16-128 [A-Za-z0-9_-], fp 64 hex, port 1-65535)
data class AgentSession(val pillId: String, val agent: String, val state: String, val statusText: String, val stepIndex: Int, val stepCount: Int)
data class Approval(val pillId: String, val fingerprint: String, val tool: String, val command: String, val createdAt: Long, val truncated: Boolean)
class AgentLink(ctx: Context, val listener: Listener) { interface Listener { fun onStatus(connected: Boolean, desktop: String?); fun onSessions(list: List<AgentSession>); fun onApproval(a: Approval); fun onApprovalResolved(fp: String) }
  fun start(); fun stop(); fun decide(fingerprint: String, allow: Boolean) }   // pinned-cert TLS via SSLContext+custom X509TrustManager comparing SHA-256 of the leaf cert to `fp`; hello caps []; ping every 20 s; reconnect with backoff 1..30 s; `error auth` => stop retrying; offer each approval max 120 s
object ApprovalCard { fun build(ctx: Context, a: Approval, locked: Boolean, onAllow: () -> Unit, onDeny: () -> Unit): View }   // dark compact card; command hidden when locked; truncated => Deny only
class ConfirmActivity : Activity  // transparent; KeyguardManager.createConfirmDeviceCredentialIntent; result delivered through a static callback; Allow is refused when the phone has no screen lock
```
New MethodChannel methods (lead implements, flutter calls): `agentPair{link}`→`{ok:bool, name:String?, error:String?}`, `agentUnpair`, `agentStatus`→`{paired:bool, desktop:String?, connected:bool, sessions:[{pillId,agent,state,statusText,stepIndex,stepCount}]}`.
Bridge (Node ≥ 18, macOS/Linux, zero npm dependencies): `bridge.mjs` = the desktop side of protocol v1 (TLS server on a configurable port bound to the LAN interface, self-signed cert generated once with the system `openssl` into `~/.bubby/`, one token per pairing, pairing link printed in the terminal, `--revoke`), session state kept from Claude Code hook events, `approval` sent to the phone when a Claude Code `PermissionRequest` is pending and the phone's `decision` returned to the waiting hook; `hook.mjs` = the command Claude Code runs for hooks (reads the hook JSON on stdin, talks to the bridge over a unix socket in `~/.bubby/`, never blocks Claude: if the bridge is not running or no answer within 110 s it exits 0 printing nothing so Claude's normal prompt appears), `install-hooks.mjs` (prints the exact diff to `~/.claude/settings.json`, writes a dated backup, merges only our entries, applies only with `--yes`; `--uninstall`), `README.md` (setup in Russian: start bridge, pair, install hooks), tests with a fake phone client (`node --test`). Command text sent to the phone: full up to 500 chars, else cut + `truncated:true`; fingerprint = lowercase hex SHA-256 of pillId, sessionId, tool, command, inputKey joined by U+001F. Never send prompts, outputs, file contents, or full paths. Check Claude Code's current hooks documentation (https://docs.claude.com/en/docs/claude-code/hooks) for the exact `PermissionRequest`/`Notification`/`Stop`/`PreToolUse` input and output JSON, and use WebFetch if available.

## Rules
- Match the surrounding code style: short, no comment noise, comments only for non-obvious why.
- No new Gradle dependencies in Kotlin (plain Android framework APIs). `minSdk 26`, `targetSdk 34`, Kotlin 1.9+.
- Verify: Dart → `flutter analyze` (in `app`). Kotlin → the lead builds; double-check API levels, imports, nullability by reading carefully, and run `kotlinc`-style review yourself if tools exist.
- Report when done: files written, any interface deviation, manifest needs. Under 150 words.
