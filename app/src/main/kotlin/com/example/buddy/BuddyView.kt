package com.example.buddy

import android.content.Context
import android.graphics.*
import android.os.SystemClock
import android.view.View
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin
import kotlin.random.Random

object Moods {
    val ALL: List<String> = listOf(
        "happy", "laugh", "love", "surprised", "sad", "confused", "offended", "angry",
        "excited", "shy", "proud", "cool", "wink", "crying", "dizzy", "scared",
        "silly", "smug", "worried", "tired", "dance", "curious"
    )

    val DESCRIPTIONS: Map<String, String> = mapOf(
        "happy" to "cheerful and glad, bouncing with a smile",
        "laugh" to "laughing out loud",
        "love" to "adoring, heart eyes, affectionate",
        "surprised" to "shocked or amazed, wide eyes",
        "sad" to "sad and down, a lonely tear",
        "confused" to "puzzled, does not understand",
        "offended" to "sulking, pouting, hurt feelings",
        "angry" to "angry and fuming",
        "excited" to "very happy and energetic, star eyes",
        "shy" to "bashful, blushing, looking away",
        "proud" to "proud of itself or of the user, chin up",
        "cool" to "confident and cool, wearing sunglasses",
        "wink" to "playful wink, flirty or joking",
        "crying" to "crying with streaming tears",
        "dizzy" to "dizzy, spiral eyes, overwhelmed",
        "scared" to "frightened, trembling, sweating",
        "silly" to "goofy, sticking out the tongue",
        "smug" to "self-satisfied smirk, told you so",
        "worried" to "anxious and concerned, sweat drop",
        "tired" to "exhausted, heavy eyelids",
        "dance" to "dancing happily to music",
        "curious" to "curious and interested, head tilted, question mark"
    )

    val LABELS: Map<String, String> = mapOf(
        "idle" to "Спокоен",
        "sleepy" to "Сонный",
        "listen" to "Слушает",
        "think" to "Думает",
        "happy" to "Радуется",
        "laugh" to "Смеётся",
        "love" to "Влюблён",
        "surprised" to "Удивлён",
        "sad" to "Грустит",
        "confused" to "Не понимает",
        "offended" to "Обижен",
        "angry" to "Злится",
        "excited" to "В восторге",
        "shy" to "Смущается",
        "proud" to "Гордится",
        "cool" to "Крутой",
        "wink" to "Подмигивает",
        "crying" to "Плачет",
        "dizzy" to "Кружится голова",
        "scared" to "Боится",
        "silly" to "Дурачится",
        "smug" to "Ухмыляется",
        "worried" to "Волнуется",
        "tired" to "Устал",
        "dance" to "Танцует",
        "curious" to "Любопытно"
    )

    /** Localized status label for a mood (Russian base from LABELS). */
    fun label(ctx: android.content.Context, mood: String): String {
        val l = Tr.lang(ctx)
        val ru = LABELS[mood] ?: return mood
        if (l == "ru") return ru
        return when (mood) {
            "idle" -> if (l == "en") "Calm" else "Xotirjam"
            "sleepy" -> if (l == "en") "Sleepy" else "Mudroq"
            "listen" -> if (l == "en") "Listening" else "Tinglayapti"
            "think" -> if (l == "en") "Thinking" else "O'ylayapti"
            "happy" -> if (l == "en") "Happy" else "Xursand"
            "laugh" -> if (l == "en") "Laughing" else "Kulyapti"
            "love" -> if (l == "en") "In love" else "Sevgida"
            "surprised" -> if (l == "en") "Surprised" else "Hayron"
            "sad" -> if (l == "en") "Sad" else "Xafa"
            "confused" -> if (l == "en") "Confused" else "Tushunmayapti"
            "offended" -> if (l == "en") "Offended" else "Xafa bo'ldi"
            "angry" -> if (l == "en") "Angry" else "Jahli chiqdi"
            "excited" -> if (l == "en") "Thrilled" else "Zavqda"
            "shy" -> if (l == "en") "Shy" else "Uyalyapti"
            "proud" -> if (l == "en") "Proud" else "G'ururlanyapti"
            "cool" -> if (l == "en") "Cool" else "Zo'r"
            "wink" -> if (l == "en") "Winking" else "Ko'z qisyapti"
            "crying" -> if (l == "en") "Crying" else "Yig'layapti"
            "dizzy" -> if (l == "en") "Dizzy" else "Boshi aylanyapti"
            "scared" -> if (l == "en") "Scared" else "Qo'rqyapti"
            "silly" -> if (l == "en") "Goofing around" else "Hazillashyapti"
            "smug" -> if (l == "en") "Smirking" else "Irshayyapti"
            "worried" -> if (l == "en") "Worried" else "Xavotirda"
            "tired" -> if (l == "en") "Tired" else "Charchadi"
            "dance" -> if (l == "en") "Dancing" else "Raqsga tushyapti"
            "curious" -> if (l == "en") "Curious" else "Qiziqyapti"
        else -> ru
        }
    }

    internal val KNOWN: Set<String> = ALL.toSet() + setOf("idle", "sleepy", "listen", "think")
}

object Species {
    val ALL: List<String> = listOf("cat", "dog", "bunny", "bear", "panda", "fox", "frog", "penguin",
        "capybara", "hamster", "owl", "duck", "pig", "koala", "lion", "tiger")

    val LABELS: Map<String, String> = mapOf(
        "cat" to "Кот", "dog" to "Пёс", "bunny" to "Зайчик", "bear" to "Мишка",
        "panda" to "Панда", "fox" to "Лис", "frog" to "Лягушонок", "penguin" to "Пингвин",
        "capybara" to "Капибара", "hamster" to "Хомяк", "owl" to "Сова", "duck" to "Утёнок",
        "pig" to "Поросёнок", "koala" to "Коала", "lion" to "Лев", "tiger" to "Тигр"
    )

    fun normalize(s: String?): String {
        val k = s?.trim()?.lowercase() ?: return "cat"
        return if (k in ALL) k else "cat"
    }
}

class BuddyView(c: Context) : View(c) {
    var mood: String = "idle"
        set(v) {
            val m = if (v in Moods.KNOWN) v else "idle"
            if (!inFlash) { flashing = false; removeCallbacks(restore) }
            if (m != field) startBlend()
            field = m
            mk = 0; mNext = 0L
            kick()
        }
    var species: String = "cat"
        set(v) { field = Species.normalize(v); kick() }
    var scaleF = 1f
    var outfit: Set<String> = emptySet()
        set(v) { field = v; kick() }
    private var base = 0xFFF0883A.toInt()
    private var light = 0xFFFFE6C8.toInt()
    private val dens = resources.displayMetrics.density
    private val padPx: Float get() = maxOf(12f * dens, 24f * u)   // grows with the size so auras and tall ears are never clipped

    /** While true the mouth flaps with a pseudo-random syllable rhythm on top of the mood mouth. */
    var speaking: Boolean = false
        set(v) { field = v; kick() }
    /** Being held / dragged: wide surprised then giggly face, wiggling body. */
    var grabbed: Boolean = false
        set(v) { if (v && !field) { grabStart = SystemClock.uptimeMillis(); poke() }; field = v; kick() }
    /** Soft pulsing halo behind the mascot. */
    var glow: Boolean = false
        set(v) { field = v; kick() }
    private var glowCol = 0xFF7B4FD6.toInt()
    private var glowShader: Shader? = null

    /** True for a view that is never attached to a window and is only drawn into a bitmap (stickers, previews). */
    var offscreen: Boolean = false

    /** What Bubby is doing, shown as a small badge on the head: "", think, search, listen, approval, limit, error or done. */
    var badge: String = ""
        set(v) { if (field != v) { field = v; kick() } }
    var badgeOn: Boolean = true
        set(v) { field = v; kick() }
    /** Soft halo in the colour of the badge state. */
    var stateGlow: Boolean = true
        set(v) { field = v; kick() }
    private var sgCol = 0
    private var sgShader: Shader? = null

    private fun badgeColor(k: String): Int = when (k) {
        "think" -> 0xFF7B4FD6.toInt()
        "search" -> 0xFF14B8A6.toInt()
        "listen" -> 0xFF2E9BE6.toInt()
        "done" -> 0xFF2FBF71.toInt()
        "error" -> 0xFFE5484D.toInt()
        else -> 0xFFF2A33A.toInt()   // approval, limit
    }

    private fun drawStateGlow(cv: Canvas) {
        val c = badgeColor(badge)
        if (c != sgCol || sgShader == null) {
            sgCol = c
            val rr = Color.red(c); val gg = Color.green(c); val bb = Color.blue(c)
            sgShader = RadialGradient(0f, 0f, 1f,
                intArrayOf(Color.argb(0x78, rr, gg, bb), Color.argb(0x30, rr, gg, bb), Color.argb(0, rr, gg, bb)),
                floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP)
        }
        gp.shader = sgShader; gp.alpha = 255
        cv.save(); cv.translate(0f, -30f); cv.scale(34f, 34f)
        cv.drawCircle(0f, 0f, 1f, gp)
        cv.restore()
    }

    /** A pill with moving dots while thinking or searching, otherwise a round badge with a small sign. */
    private fun drawBadge(cv: Canvas, t: Float) {
        val k = badge
        val col = badgeColor(k)
        val white = 0xFFFFFFFF.toInt()
        val ring = 0xFF1B1530.toInt()
        val cx = -21f; val cy = -49f
        if (k == "think" || k == "search") {
            r.set(cx - 10f, cy - 5.6f, cx + 10f, cy + 5.6f)
            fill(ring); cv.drawRoundRect(r, 5.6f, 5.6f, p)
            r.set(cx - 8.6f, cy - 4.2f, cx + 8.6f, cy + 4.2f)
            fill(col); cv.drawRoundRect(r, 4.2f, 4.2f, p)
            for (i in -1..1) {
                fill(white)
                p.alpha = (255 * (0.45f + 0.55f * abs(sin(t * 5f - i * 0.9f)))).toInt()
                cv.drawCircle(cx + i * 5f, cy, 1.5f, p)
            }
            p.alpha = 255
            return
        }
        val pulse = 1f + 0.06f * sin(t * 5f)
        fill(ring); cv.drawCircle(cx, cy, 6.8f * pulse, p)
        fill(col); cv.drawCircle(cx, cy, 5.2f * pulse, p)
        when (k) {
            "error" -> {
                line(white, 1.8f)
                cv.drawLine(cx - 2f, cy - 2f, cx + 2f, cy + 2f, p); cv.drawLine(cx - 2f, cy + 2f, cx + 2f, cy - 2f, p)
            }
            "done" -> {
                line(white, 1.9f); path.reset()
                path.moveTo(cx - 2.4f, cy + 0.2f); path.lineTo(cx - 0.6f, cy + 2.1f); path.lineTo(cx + 2.6f, cy - 1.9f)
                cv.drawPath(path, p)
            }
            "listen" -> { fill(white); cv.drawCircle(cx, cy, 1.9f, p) }
            else -> {   // approval, limit: an exclamation mark
                fill(white)
                r.set(cx - 0.95f, cy - 3.3f, cx + 0.95f, cy + 0.9f); cv.drawRoundRect(r, 0.95f, 0.95f, p)
                cv.drawCircle(cx, cy + 2.7f, 1.05f, p)
            }
        }
    }
    private val shadowShader: Shader = RadialGradient(0f, 0f, 1f, 0x66000000, 0x00000000, Shader.TileMode.CLAMP)
    private val gp = Paint(Paint.ANTI_ALIAS_FLAG)
    private var grabStart = 0L
    private var petStart = 0L
    private var spinStart = 0L
    private var wakeStart = 0L
    private var spkEnv = 0f
    private var lastMs = 0L
    private var em = "idle"

    // ---- 2.5D parallax / look state (all smoothing happens inside the view)
    private var tgtTx = 0f
    private var tgtTy = 0f
    private var tiltStamp = 0L
    private var tx = 0f
    private var ty = 0f
    private var lkTx = 0f
    private var lkTy = 0f
    private var lkStamp = 0L
    private var idTx = 0f
    private var idTy = 0f
    private var idX = 0f
    private var idY = 0f
    private var idNext = 0L
    private var glX = 0f
    private var glY = 0f
    private var glNext = 0L
    private var eyeX = 0f
    private var eyeY = 0f
    private var hx = 0f
    private var hy = 0f
    private var earX = 0f
    private var earY = 0f
    private var bodyX = 0f
    private var bodyY = 0f
    private var faceX = 0f
    private var faceY = 0f
    private var eyeDx = 0f
    private var eyeDy = 0f

    // ---- "alive" layer: entrance, idle behaviours, doze, mood blend, touch feel (all timestamp based, driven by onDraw) ----
    /** Set by the service: false while the island is expanded / listening / in a call. Idle behaviours and dozing only run while true. */
    var calm: Boolean = true
        set(v) { field = v; if (!v) { bk = 0; dozing = false; dozeK = 0f } }
    private var enterStart = 0L
    private var enterWanted = false
    private var entX = 0f
    private var entY = -1f
    private val locBuf = IntArray(2)
    // idle behaviours: 0 none, 1 yawn+stretch, 2 shake-off, 3 curious look, 4 hop, 5 hum, 6 peek, 7 scratch
    private var bk = 0
    private var bStart = 0L
    private var bDur = 0L
    private var bNext = 0L
    private var bDir = 1
    private var lastInteract = SystemClock.uptimeMillis()
    private var dozing = false
    private var dozeK = 0f
    private var nightStamp = 0L
    private var night = false
    private var touchDown = false
    private var touchStart = 0L
    private var touchLast = 0L
    private var sqStart = 0L
    // mood cross-blend: blend 0 -> 1 over ~220 ms; p* = values drawn when the change began, c* = currently drawn
    private var blend = 1f
    private var blendStart = 0L
    private var lastEm = "idle"
    private var prevEm = "idle"
    private var pEye = 1f
    private var pMouth = 0.5f
    private var pBrow = 0f
    private var cEye = 1f
    private var cMouth = 0.5f
    private var cBrow = 0f
    private var mvDx = 0f
    private var mvDy = 0f
    private var mvRot = 0f
    private var mvS = 1f
    private val emPh = floatArrayOf(0f, 0.37f, 0.71f, 0.19f, 0.53f, 0.86f)
    private val puffAng = floatArrayOf(0.2f, 1.2f, 2.3f, 3.4f, 4.5f, 5.6f)

    private fun startBlend() {
        pEye = cEye; pMouth = cMouth; pBrow = cBrow
        prevEm = lastEm; blend = 0f; blendStart = SystemClock.uptimeMillis()
    }
    /** Any user interaction: restarts the 15 s behaviour cooldown and wakes the mascot instantly if it was dozing. */
    private fun poke() { lastInteract = SystemClock.uptimeMillis(); dozing = false; dozeK = 0f; bk = 0 }

    /** Plays the 700 ms entrance (pop-in from the nearest screen edge). No-op when animations are disabled system-wide. */
    fun enter() { enterWanted = true; if (looping) { beginEnter(); kick() } else updateLoop() }
    private fun beginEnter() {
        if (!enterWanted || width == 0) return
        enterWanted = false
        val now = SystemClock.uptimeMillis()
        if (enterStart != 0L && now - enterStart < 1000L) return
        val sc = try {
            android.provider.Settings.Global.getFloat(context.contentResolver, android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
        } catch (_: Exception) { 1f }
        if (sc == 0f) return
        getLocationOnScreen(locBuf)
        val cx = locBuf[0] + width / 2f; val cy = locBuf[1] + height / 2f
        val dl = cx; val dr = resources.displayMetrics.widthPixels - cx
        if (cy <= dl && cy <= dr) { entX = 0f; entY = -1f }
        else if (dl < dr) { entX = -1f; entY = -0.15f } else { entX = 1f; entY = -0.15f }
        enterStart = now; bk = 0; dozing = false; dozeK = 0f; lastInteract = now
    }

    /** Finger is on the mascot at (x, y) in this view's local pixels: squash on first contact, eyes follow the finger. */
    fun touchAt(x: Float, y: Float) {
        val now = SystemClock.uptimeMillis()
        val first = !touchDown
        if (first) { touchDown = true; touchStart = now; sqStart = now }
        touchLast = now; poke()
        val hw = width.coerceAtLeast(1) * 0.5f; val hh = height.coerceAtLeast(1) * 0.5f
        lookAt((x - hw) / hw * 0.7f, (y - hh) / hh * 0.5f)
        if (first) kick()
    }
    /** Finger lifted / gesture cancelled. */
    fun touchEnd() {
        if (!touchDown) return
        touchDown = false
        lkStamp = SystemClock.uptimeMillis() - 1800L   // eyes drift back after ~0.7 s
        kick()
    }

    private fun idleOk(): Boolean = calm && mood == "idle" && !speaking && !grabbed && !glow && !flashing &&
        spinStart == 0L && petStart == 0L && wakeStart == 0L && !touchDown && enterStart == 0L && spkEnv <= 0f && !hidden

    private fun isNight(now: Long): Boolean {
        if (nightStamp == 0L || now - nightStamp > 60_000L) {
            nightStamp = now
            val h = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
            night = h >= 23 || h < 6
        }
        return night
    }

    /** Behaviour scheduler, called from onDraw (so it only ticks at the governor's rate). */
    private fun idleStep(now: Long, dtS: Float) {
        if (!idleOk()) { bk = 0; dozing = false; dozeK = 0f; return }
        if (bk != 0) {
            if (now - bStart >= bDur) { bk = 0; bNext = now + Random.nextLong(25_000L, 70_000L) }
            return
        }
        if (mk != 0) return
        val nt = isNight(now)
        val quiet = now - lastInteract
        if (dozing && !nt) { dozing = false; dozeK = 0f }
        if (!dozing && nt && quiet > 180_000L) dozing = true
        if (dozing) { dozeK = minOf(1f, dozeK + dtS / 2.5f); return }
        if (bNext == 0L) bNext = now + Random.nextLong(25_000L, 70_000L)
        if (now < bNext || quiet < 15_000L) return
        val r0 = Random.nextInt(100)
        bk = if (nt && r0 < 55) 1 else when {
            r0 < 12 -> 1; r0 < 28 -> 2; r0 < 46 -> 3; r0 < 60 -> 4; r0 < 74 -> 5; r0 < 88 -> 6; else -> 7
        }
        bDur = when (bk) { 1 -> 2200L; 2 -> 900L; 3 -> 1600L; 4 -> 1000L; 5 -> 2200L; 6 -> 1800L; else -> 1500L }
        bStart = now
        bDir = if (Random.nextBoolean()) 1 else -1
        if (bk == 3) {
            val vy = Random.nextFloat() * 0.8f - 0.4f
            idTx = bDir * 0.85f; idTy = vy; idNext = now + bDur + 500L
            lookAt(bDir * 0.9f, vy)
        } else if (bk == 6) {
            getLocationOnScreen(locBuf)
            bDir = if (locBuf[0] + width / 2 < resources.displayMetrics.widthPixels / 2) -1 else 1   // lean toward the nearer edge
            idTx = bDir * 0.6f; idNext = now + bDur + 500L
            lookAt(bDir * 0.9f, 0f)
        }
        kick()   // 60 fps for the length of the burst, busy() hands back to the idle rate afterwards
    }

    // target face parameters per mood, used only to cross-blend (eye openness, mouth curve, brow height)
    private fun faceEye(m: String): Float = when (m) {
        "sleepy" -> 0.35f; "tired" -> 0.55f; "happy" -> 0.55f; "laugh" -> 0.45f; "dance" -> 0.5f; "pet" -> 0.4f
        "wink" -> 0.8f; "surprised" -> 1.25f; "scared" -> 1.2f; "angry" -> 0.8f; "smug" -> 0.7f; "offended" -> 0.65f
        "excited" -> 1.1f; "cool" -> 0.9f; "sad" -> 0.9f; else -> 1f
    }
    private fun faceMouth(m: String): Float = when (m) {
        "happy" -> 1f; "laugh" -> 1.4f; "love" -> 1f; "excited" -> 1.3f; "dance" -> 1.2f; "silly" -> 1f; "proud" -> 0.8f
        "cool" -> 0.5f; "smug" -> 0.6f; "wink" -> 0.7f; "pet" -> 0.8f; "idle" -> 0.5f; "listen" -> 0.3f
        "sad" -> -1f; "crying" -> -1.2f; "angry" -> -0.6f; "offended" -> -0.5f; "worried" -> -0.4f; "scared" -> -0.8f
        "tired" -> -0.1f; "surprised" -> 0.2f; "shy" -> 0.3f; "confused", "dizzy" -> -0.1f; else -> 0f
    }
    private fun faceBrow(m: String): Float = when (m) {
        "surprised", "scared" -> 1f; "curious" -> 0.6f; "proud" -> 0.5f; "worried" -> 0.5f; "excited" -> 0.4f; "confused" -> 0.3f
        "angry" -> -1f; "offended" -> -0.6f; "sad" -> -0.4f; "tired" -> -0.5f; "smug" -> -0.2f; else -> 0f
    }

    // Unit-space shaders (radius 1, scaled by the canvas when drawn). They do not depend on
    // palette, species or size, so they are built exactly once.
    private lateinit var volSh: Shader
    private lateinit var warmSh: Shader
    private lateinit var rimSh: Shader
    private lateinit var irisSh: Shader
    private lateinit var glintSh: Shader
    private lateinit var blushSoftSh: Shader
    private lateinit var blushStrongSh: Shader
    private val ur = RectF()
    private val rb = RectF()

    init { buildGlowShader(); buildUnitShaders() }

    /** Tilt of the phone, each axis -1..1 (low-pass filtered inside the view). */
    fun setTilt(x: Float, y: Float) {
        tgtTx = x.coerceIn(-1f, 1f); tgtTy = y.coerceIn(-1f, 1f)
        tiltStamp = SystemClock.uptimeMillis()
    }

    /** Make the pupils look toward (x, y), each -1..1, for a couple of seconds. */
    fun lookAt(x: Float, y: Float) {
        lkTx = x.coerceIn(-1f, 1f); lkTy = y.coerceIn(-1f, 1f)
        lkStamp = SystemClock.uptimeMillis()
    }

    private fun buildUnitShaders() {
        volSh = RadialGradient(-0.38f, -0.5f, 1.55f,
            intArrayOf(0x55FFFFFF, 0x10FFFFFF, 0x00000000, 0x30301848, 0x66301848),
            floatArrayOf(0f, 0.22f, 0.45f, 0.8f, 1f), Shader.TileMode.CLAMP)
        warmSh = RadialGradient(0.45f, 0.62f, 0.75f, 0x55FF8A5C, 0x00FF8A5C, Shader.TileMode.CLAMP)
        rimSh = LinearGradient(-0.7f, -0.7f, 0.8f, 0.8f,
            intArrayOf(0x00FFFFFF, 0x00FFFFFF, 0x99FFFFFF.toInt()),
            floatArrayOf(0f, 0.6f, 1f), Shader.TileMode.CLAMP)
        irisSh = RadialGradient(0.2f, 0.6f, 0.9f,
            intArrayOf(0xFF6A5AA8.toInt(), 0xFF2C2350.toInt(), 0xFF120D24.toInt()),
            floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP)
        glintSh = RadialGradient(0f, 0f, 1f,
            intArrayOf(0xEBFFFFFF.toInt(), 0x5AFFFFFF, 0x00FFFFFF),
            floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP)
        blushSoftSh = RadialGradient(0f, 0f, 1f, 0x80E5365B.toInt(), 0x00E5365B, Shader.TileMode.CLAMP)
        blushStrongSh = RadialGradient(0f, 0f, 1f, 0xB0E5365B.toInt(), 0x00E5365B, Shader.TileMode.CLAMP)
    }

    /** Lit volume overlay: highlight upper left, darker rim lower right, warm glow, thin rim light.
     *  rad < 0 draws an oval. Call right after filling the same shape with its base colour. */
    private fun vol(cv: Canvas, l: Float, t: Float, rt: Float, b: Float, rad: Float, rim: Boolean = false) {
        val hw = (rt - l) * 0.5f; val hh = (b - t) * 0.5f
        if (hw <= 0f || hh <= 0f) return
        cv.save(); cv.translate(l + hw, t + hh); cv.scale(hw, hh)
        ur.set(-1f, -1f, 1f, 1f)
        p.style = Paint.Style.FILL
        p.shader = volSh
        if (rad < 0f) cv.drawOval(ur, p) else cv.drawRoundRect(ur, rad / hw, rad / hh, p)
        p.shader = warmSh
        if (rad < 0f) cv.drawOval(ur, p) else cv.drawRoundRect(ur, rad / hw, rad / hh, p)
        if (rim) {
            ur.set(-0.94f, -0.94f, 0.94f, 0.94f)
            p.style = Paint.Style.STROKE; p.strokeWidth = 0.05f; p.shader = rimSh
            if (rad < 0f) cv.drawOval(ur, p) else cv.drawRoundRect(ur, rad / hw, rad / hh, p)
        }
        p.shader = null
        cv.restore()
    }
    private fun volC(cv: Canvas, cx: Float, cy: Float, rad: Float) =
        vol(cv, cx - rad, cy - rad, cx + rad, cy + rad, -1f)

    /** Same overlay for the shape currently in [path] (clipped to it). */
    private fun volPath(cv: Canvas) {
        path.computeBounds(rb, true)
        val hw = rb.width() * 0.5f; val hh = rb.height() * 0.5f
        if (hw <= 0.01f || hh <= 0.01f) return
        cv.save(); cv.clipPath(path)
        cv.translate(rb.centerX(), rb.centerY()); cv.scale(hw, hh)
        ur.set(-1.1f, -1.1f, 1.1f, 1.1f)
        p.style = Paint.Style.FILL; p.shader = volSh; cv.drawRect(ur, p)
        p.shader = null
        cv.restore()
    }

    /** Specular glint: soft radial ellipse, crisp core and a tiny secondary dot. */
    private fun glint(cv: Canvas, cx: Float, cy: Float, w: Float, h: Float, ang: Float, a: Float = 1f) {
        cv.save(); cv.translate(cx, cy); cv.rotate(ang)
        cv.save(); cv.scale(w * 0.5f, h * 0.5f)
        p.style = Paint.Style.FILL; p.shader = glintSh; p.color = 0xFFFFFFFF.toInt(); p.alpha = (255 * a).toInt()
        cv.drawCircle(0f, 0f, 1f, p)
        p.shader = null
        cv.restore()
        fill(0xFFFFFFFF.toInt()); p.alpha = (235 * a).toInt()
        ur.set(-w * 0.25f, -h * 0.21f, -w * 0.25f + w * 0.42f, -h * 0.21f + h * 0.34f); cv.drawOval(ur, p)
        cv.restore()
        fill(0xFFFFFFFF.toInt()); p.alpha = (190 * a).toInt()
        cv.drawCircle(cx + w * 0.5f, cy + h * 0.55f, h * 0.2f, p)
    }

    fun setGlowColor(c: Int) { if (c == glowCol) return; glowCol = c; buildGlowShader(); kick() }
    private fun kick() { if (looping) { removeCallbacks(tick); postOnAnimation(tick) } else updateLoop() }
    fun pet() { poke(); kick(); petStart = SystemClock.uptimeMillis(); spinStart = 0L; wakeStart = 0L; invalidate() }
    fun spinHead() { poke(); kick(); spinStart = SystemClock.uptimeMillis(); petStart = 0L; wakeStart = 0L; invalidate() }
    fun wake() { poke(); kick(); wakeStart = SystemClock.uptimeMillis(); invalidate() }

    // Both are unit-space (radius 1, scaled when drawn), so a size change (pinch resize) never rebuilds them;
    // only a new glow colour does, and that rebuilds the glow alone.
    private fun buildGlowShader() {
        val rr = Color.red(glowCol); val gg = Color.green(glowCol); val bb = Color.blue(glowCol)
        glowShader = RadialGradient(0f, 0f, 1f,
            intArrayOf(Color.argb(0xB0, rr, gg, bb), Color.argb(0x50, rr, gg, bb), Color.argb(0, rr, gg, bb)),
            floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP)
    }

    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) {
        super.onSizeChanged(w, h, ow, oh)
        if (enterWanted && looping && w > 0) { beginEnter(); kick() }
    }

    private fun ease(x: Float): Float =
        if (x < 0.5f) 4f * x * x * x else { val y = -2f * x + 2f; 1f - y * y * y / 2f }

    private fun syll(t: Float): Float {
        val a = sin(t * 11f + 2f * sin(t * 2.3f)) * 0.5f + 0.5f
        val b = 0.6f + 0.4f * sin(t * 7.3f + 1f)
        var o = a * b
        if (sin(t * 1.9f + sin(t * 0.7f) * 2f) < -0.6f) o *= 0.12f
        return o.coerceIn(0f, 1f)
    }
    private val u: Float get() = dens * scaleF
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val r = RectF()
    private val path = Path()
    private val ink = 0xFF1B1530.toInt()
    private val PANDA_INK = 0xFF2B2B33.toInt()
    // ---- frame-rate governor -------------------------------------------------
    /** Service calls this with false while the screen is off / quiet. */
    private var activeFlag = true
    /** True while the buddy is hidden (quiet / auto-hide). No frames are produced. */
    var hidden: Boolean = false
        set(v) { if (field != v) { field = v; if (!v) enterWanted = true; updateLoop() } }   // visible again -> entrance
    private var winVisible = true
    private var looping = false
    fun setActive(active: Boolean) { if (activeFlag != active) { activeFlag = active; updateLoop() } }

    private fun shouldRun() = activeFlag && !hidden && winVisible && isAttachedToWindow &&
        visibility == VISIBLE && alpha > 0f

    private fun updateLoop() {
        if (shouldRun()) {
            if (!looping) {
                looping = true; lastMs = 0L
                lastInteract = SystemClock.uptimeMillis(); bNext = 0L   // resuming: fresh cooldown, no instant behaviour / doze
                beginEnter()
                invalidate(); postOnAnimation(tick)
            }
        } else if (looping) { looping = false; removeCallbacks(tick) }
    }

    /** Anything beyond calm breathing / blinking / looking around. */
    private fun busy(): Boolean {
        if (bk != 0 || enterStart != 0L || blend < 1f || touchDown || sqStart != 0L) return true   // short bursts only
        if (mk != 0 || speaking || grabbed || glow || flashing) return true
        if (spinStart != 0L || petStart != 0L || wakeStart != 0L) return true
        if (spkEnv > 0f) return true
        return !(mood == "idle" || mood == "sleepy")
    }

    private val visR = Rect()
    private val tick = object : Runnable {
        override fun run() {
            if (!looping) return
            if (!shouldRun()) { looping = false; return }
            // scrolled out of sight inside a page (the app's previews): no frames, just look again a few times a second
            if (!getLocalVisibleRect(visR)) { postOnAnimationDelayed(this, 250L); return }
            invalidate()
            if (busy()) postOnAnimation(this)
            else postOnAnimationDelayed(this, if (outfit.isEmpty()) 66L else 33L)
        }
    }

    override fun onWindowVisibilityChanged(v: Int) {
        super.onWindowVisibilityChanged(v); winVisible = v == VISIBLE; updateLoop()
    }
    override fun onVisibilityChanged(changed: View, v: Int) {
        super.onVisibilityChanged(changed, v); if (changed === this) updateLoop()
    }
    override fun setAlpha(a: Float) {
        val was = alpha; super.setAlpha(a); if ((was > 0f) != (a > 0f)) updateLoop()
    }

    private var flashing = false
    private var inFlash = false
    private var prev = "idle"
    private val restore = Runnable {
        inFlash = true; mood = prev; inFlash = false; flashing = false
    }

    // idle micro-expressions: 0 none, 1 blink, 2 wink, 3 glance, 4 yawn, 5 smile
    private var mk = 0
    private var mStart = 0L
    private var mDur = 0L
    private var mNext = 0L
    private var mDir = 1

    fun setPalette(b: Int, l: Int) { base = b; light = l; kick() }

    fun flash(m: String, ms: Long) {
        if (!flashing) { prev = mood; flashing = true }
        inFlash = true; mood = m; inFlash = false
        removeCallbacks(restore)
        postDelayed(restore, ms)
    }

    override fun onMeasure(a: Int, b: Int) =
        setMeasuredDimension((84 * u + 2f * padPx).toInt(), (80 * u + 2f * padPx).toInt())
    override fun onAttachedToWindow() { super.onAttachedToWindow(); updateLoop() }
    override fun onDetachedFromWindow() {
        looping = false
        removeCallbacks(tick)
        removeCallbacks(restore)
        if (flashing) restore.run()
        bk = 0; dozing = false; dozeK = 0f; enterStart = 0L; enterWanted = false
        touchDown = false; sqStart = 0L; blend = 1f
        super.onDetachedFromWindow()
    }

    private fun microStep(now: Long) {
        if (mood != "idle") { mk = 0; mNext = 0L; return }
        if (mk == 0) {
            if (mNext == 0L) mNext = now + Random.nextLong(1200L, 3500L)
            if (now >= mNext) {
                val k = Random.nextInt(100)
                mk = when {
                    k < 50 -> 1
                    k < 60 -> 2
                    k < 80 -> 3
                    k < 87 -> 4
                    else -> 5
                }
                mDur = when (mk) { 1 -> 170L; 2 -> 450L; 3 -> 1300L; 4 -> 1900L; else -> 1700L }
                mStart = now
                mDir = if (Random.nextBoolean()) 1 else -1
            }
        } else if (now - mStart >= mDur) {
            mk = 0
            mNext = now + Random.nextLong(1200L, 4200L)
        }
    }

    private fun fill(col: Int) { p.shader = null; p.style = Paint.Style.FILL; p.color = col }
    private fun line(col: Int, w: Float) {
        p.shader = null
        p.style = Paint.Style.STROKE; p.color = col; p.strokeWidth = w
        p.strokeCap = Paint.Cap.ROUND; p.strokeJoin = Paint.Join.ROUND
    }
    private fun mix(a: Int, b: Int, f: Float): Int {
        val k = 1f - f
        return Color.argb(255,
            (Color.red(a) * k + Color.red(b) * f).toInt(),
            (Color.green(a) * k + Color.green(b) * f).toInt(),
            (Color.blue(a) * k + Color.blue(b) * f).toInt())
    }
    private fun heart(cv: Canvas, cx: Float, cy: Float, s: Float) {
        path.reset()
        path.moveTo(cx, cy + s * 0.9f)
        path.cubicTo(cx - s * 1.6f, cy - s * 0.1f, cx - s * 0.7f, cy - s * 1.1f, cx, cy - s * 0.35f)
        path.cubicTo(cx + s * 0.7f, cy - s * 1.1f, cx + s * 1.6f, cy - s * 0.1f, cx, cy + s * 0.9f)
        path.close()
        cv.drawPath(path, p)
    }
    private fun star(cv: Canvas, cx: Float, cy: Float, ro: Float, rot: Float) {
        path.reset()
        for (i in 0..9) {
            val a = rot + i * 0.62831855f - 1.5707964f
            val rr = if (i % 2 == 0) ro else ro * 0.45f
            val px = cx + cos(a) * rr
            val py = cy + sin(a) * rr
            if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
        }
        path.close()
        cv.drawPath(path, p)
    }
    private fun sparkle(cv: Canvas, cx: Float, cy: Float, s: Float) {
        path.reset()
        path.moveTo(cx, cy - s); path.lineTo(cx + s * 0.3f, cy - s * 0.3f)
        path.lineTo(cx + s, cy); path.lineTo(cx + s * 0.3f, cy + s * 0.3f)
        path.lineTo(cx, cy + s); path.lineTo(cx - s * 0.3f, cy + s * 0.3f)
        path.lineTo(cx - s, cy); path.lineTo(cx - s * 0.3f, cy - s * 0.3f)
        path.close()
        cv.drawPath(path, p)
    }
    private fun drop(cv: Canvas, cx: Float, cy: Float, s: Float) {
        path.reset()
        path.moveTo(cx, cy - s * 2f); path.lineTo(cx - s * 0.9f, cy - s * 0.3f); path.lineTo(cx + s * 0.9f, cy - s * 0.3f)
        path.close()
        cv.drawPath(path, p)
        cv.drawCircle(cx, cy, s, p)
    }
    private fun spiral(cv: Canvas, cx: Float, cy: Float, rad: Float, rot: Float) {
        path.reset()
        for (i in 0..22) {
            val a = rot + i * 0.55f
            val rr = rad * i / 22f
            val px = cx + cos(a) * rr
            val py = cy + sin(a) * rr
            if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
        }
        cv.drawPath(path, p)
    }
    private fun note(cv: Canvas, x: Float, y: Float, al: Int) {
        val col = (al shl 24) or 0x7B4FD6
        fill(col); r.set(x - 3f, y - 2f, x + 3f, y + 2f); cv.drawOval(r, p)
        line(col, 1.6f)
        cv.drawLine(x + 2.6f, y, x + 2.6f, y - 9f, p)
        cv.drawLine(x + 2.6f, y - 9f, x + 6f, y - 6.5f, p)
    }

    private fun tooth(cv: Canvas) {
        if (em == "happy" || em == "laugh") {
            fill(0xFFFFFFFF.toInt()); r.set(-2.6f, -12f, 2.6f, -5.5f); cv.drawRoundRect(r, 1.6f, 1.6f, p)
            line(0x661B1530, 1f); cv.drawRoundRect(r, 1.6f, 1.6f, p)
            cv.drawLine(0f, -12f, 0f, -5.5f, p)
        }
    }

    private fun drawHead(cv: Canvas, body: Int, face: Int) {
        val sp = species
        // ears / bumps behind the head (parallax: a bit)
        cv.save(); cv.translate(earX, earY)
        when (sp) {
            "bunny" -> {
                val pk = mix(face, 0xFFF4A3B8.toInt(), 0.6f)
                for (i in 0..1) {
                    val sd = i * 2 - 1
                    cv.save(); cv.rotate(sd * 8f, sd * 17f, -46f)
                    val bl = if (sd < 0) -25f else 10f; val br = if (sd < 0) -10f else 25f
                    fill(body); r.set(bl, -88f, br, -44f); cv.drawRoundRect(r, 7.5f, 7.5f, p)
                    vol(cv, bl, -88f, br, -44f, 7.5f)
                    fill(pk); r.set(bl + 3.5f, -82f, br - 3.5f, -48f); cv.drawRoundRect(r, 4f, 4f, p)
                    fill(0x30301848); r.set(bl + 3.5f, -68f, br - 3.5f, -48f); cv.drawRoundRect(r, 4f, 4f, p)
                    cv.restore()
                }
            }
            "bear" -> {
                val inner = mix(body, 0xFFFFFFFF.toInt(), 0.5f)
                for (i in 0..1) {
                    val sd = i * 2 - 1
                    fill(body); cv.drawCircle(sd * 26f, -49f, 10f, p); volC(cv, sd * 26f, -49f, 10f)
                    fill(inner); cv.drawCircle(sd * 26f, -48.5f, 5.5f, p)
                    fill(0x33301848); cv.drawCircle(sd * 26f + sd * 0.8f, -47.5f, 4.2f, p)
                }
            }
            "panda" -> {
                fill(PANDA_INK)
                cv.drawCircle(-26f, -49f, 10f, p); cv.drawCircle(26f, -49f, 10f, p)
                volC(cv, -26f, -49f, 10f); volC(cv, 26f, -49f, 10f)
            }
            "fox" -> {
                val tip = 0xFF3B2A2A.toInt()
                for (i in 0..1) {
                    val sd = (i * 2 - 1).toFloat()
                    fill(body)
                    path.reset(); path.moveTo(sd * 34f, -38f); path.lineTo(sd * 30f, -70f); path.lineTo(sd * 8f, -50f); path.close(); cv.drawPath(path, p)
                    volPath(cv)
                    fill(face)
                    path.reset(); path.moveTo(sd * 29f, -43f); path.lineTo(sd * 28.5f, -59f); path.lineTo(sd * 16f, -49f); path.close(); cv.drawPath(path, p)
                    fill(0x38301848); path.reset(); path.moveTo(sd * 29f, -43f); path.lineTo(sd * 28.7f, -50f); path.lineTo(sd * 16f, -49f); path.close(); cv.drawPath(path, p)
                    fill(tip)
                    path.reset(); path.moveTo(sd * 30f, -70f); path.lineTo(sd * 31.8f, -56.5f); path.lineTo(sd * 20f, -61f); path.close(); cv.drawPath(path, p)
                }
            }
            "frog" -> {
                val hi = mix(body, 0xFFFFFFFF.toInt(), 0.3f)
                for (i in 0..1) {
                    val sd = i * 2 - 1
                    fill(body); cv.drawCircle(sd * 21f, -52f, 12f, p); volC(cv, sd * 21f, -52f, 12f)
                    fill(hi); cv.drawCircle(sd * 21f - 3f, -56f, 4f, p)
                    glint(cv, sd * 21f - 3.5f, -56.5f, 7f, 3.6f, -0.5f, 0.8f)
                }
            }
            "capybara" -> {
                fill(mix(body, ink, 0.2f))
                cv.drawCircle(-30f, -47f, 5.5f, p); cv.drawCircle(30f, -47f, 5.5f, p)
                volC(cv, -30f, -47f, 5.5f); volC(cv, 30f, -47f, 5.5f)
            }
            "hamster" -> {
                for (i in 0..1) {
                    val sd = i * 2 - 1
                    fill(body); cv.drawCircle(sd * 24f, -51f, 6.5f, p); volC(cv, sd * 24f, -51f, 6.5f)
                    fill(0xFFF4A3B8.toInt()); cv.drawCircle(sd * 24f, -50.6f, 3.3f, p)
                }
            }
            "owl" -> {
                fill(body)
                path.reset(); path.moveTo(-34f, -40f); path.lineTo(-31f, -66f); path.lineTo(-11f, -50f); path.close(); cv.drawPath(path, p); volPath(cv)
                path.reset(); path.moveTo(34f, -40f); path.lineTo(31f, -66f); path.lineTo(11f, -50f); path.close(); cv.drawPath(path, p); volPath(cv)
                fill(mix(body, ink, 0.35f))
                path.reset(); path.moveTo(-31f, -66f); path.lineTo(-30.5f, -56f); path.lineTo(-23f, -57f); path.close(); cv.drawPath(path, p)
                path.reset(); path.moveTo(31f, -66f); path.lineTo(30.5f, -56f); path.lineTo(23f, -57f); path.close(); cv.drawPath(path, p)
            }
            "duck" -> {
                fill(body)
                path.reset(); path.moveTo(-3f, -50f); path.quadTo(-5f, -60f, 3f, -63f); path.quadTo(1f, -57f, 4f, -50f); path.close()
                cv.drawPath(path, p)
            }
            "pig" -> {
                fill(mix(body, 0xFFE0708F.toInt(), 0.45f))
                for (i in 0..1) {
                    val sd = (i * 2 - 1).toFloat()
                    path.reset(); path.moveTo(sd * 35f, -42f); path.lineTo(sd * 31f, -62f); path.lineTo(sd * 13f, -50f); path.close()
                    cv.drawPath(path, p); volPath(cv)
                    fill(mix(body, 0xFFE0708F.toInt(), 0.45f))
                }
            }
            "koala" -> {
                val inner = mix(body, 0xFFFFFFFF.toInt(), 0.65f)
                for (i in 0..1) {
                    val sd = i * 2 - 1
                    fill(body); cv.drawCircle(sd * 29f, -50f, 13f, p); volC(cv, sd * 29f, -50f, 13f)
                    fill(inner); cv.drawCircle(sd * 29f, -49.4f, 8f, p)
                    fill(0x30301848); cv.drawCircle(sd * 29f + sd * 1f, -48f, 6f, p)
                }
            }
            "lion" -> {
                fill(body); cv.drawCircle(0f, -32f, 32f, p)
                for (i in 0..11) {
                    val a = i * 0.5235988f
                    cv.drawCircle(cos(a) * 30f, -32f + sin(a) * 30f, 8f, p)
                }
                volC(cv, 0f, -32f, 32f)
                for (i in 0..11) {
                    val a = i * 0.5235988f
                    volC(cv, cos(a) * 30f, -32f + sin(a) * 30f, 8f)
                }
                fill(face)
                cv.drawCircle(-27f, -56f, 6.5f, p); cv.drawCircle(27f, -56f, 6.5f, p)
                volC(cv, -27f, -56f, 6.5f); volC(cv, 27f, -56f, 6.5f)
                fill(mix(face, body, 0.5f))
                cv.drawCircle(-27f, -56f, 3.3f, p); cv.drawCircle(27f, -56f, 3.3f, p)
            }
            "tiger" -> {
                for (i in 0..1) {
                    val sd = i * 2 - 1
                    fill(body); cv.drawCircle(sd * 27f, -50f, 9f, p); volC(cv, sd * 27f, -50f, 9f)
                    fill(0xFFFFFFFF.toInt()); cv.drawCircle(sd * 27f, -49.6f, 5f, p)
                    fill(0x30301848); cv.drawCircle(sd * 27f + sd * 0.8f, -48.4f, 3.8f, p)
                }
            }
            "cat" -> {
                for (i in 0..1) {
                    val sd = (i * 2 - 1).toFloat()
                    path.reset(); path.moveTo(sd * 34f, -38f); path.lineTo(sd * 31f, -66f); path.lineTo(sd * 11f, -50f); path.close()
                    fill(body); cv.drawPath(path, p); volPath(cv)
                    line(0x991B1530.toInt(), 1.6f); cv.drawPath(path, p)
                    path.reset(); path.moveTo(sd * 29f, -42f); path.lineTo(sd * 28f, -58f); path.lineTo(sd * 16f, -49f); path.close()
                    fill(mix(face, 0xFFF4A3B8.toInt(), 0.5f)); cv.drawPath(path, p)
                    fill(0x38301848); path.reset(); path.moveTo(sd * 29f, -42f); path.lineTo(sd * 28.3f, -50f); path.lineTo(sd * 16f, -49f); path.close(); cv.drawPath(path, p)
                }
            }
        }
        cv.restore()
        // cat ears keep their shape outline; the outer-ear gradient is applied above
        // the head casts a soft shadow onto the ears for depth
        gp.shader = shadowShader; gp.alpha = 105
        cv.save(); cv.translate(1.2f + earX * 0.3f, -25f); cv.scale(41f, 29f)
        cv.drawCircle(0f, 0f, 1f, gp)
        cv.restore()
        // head (body layer, moves least)
        cv.save(); cv.translate(bodyX, bodyY)
        fill(body); r.set(-36f, -52f, 36f, -4f); cv.drawRoundRect(r, 24f, 24f, p)
        vol(cv, -36f, -52f, 36f, -4f, 24f, true)
        r.set(-36f, -52f, 36f, -4f)
        val inkA = if (sp == "panda") 0x882B2B33.toInt() else 0xAA1B1530.toInt()
        line(inkA, 1.8f); cv.drawRoundRect(r, 24f, 24f, p)
        line(inkA and 0x00FFFFFF or 0x55000000, 3.2f); r.set(-35f, -51f, 35f, -5f); cv.drawArc(r, 15f, 100f, false, p)
        if (sp == "dog") {
            fill(mix(body, ink, 0.3f))
            cv.save(); cv.rotate(8f, -33f, -48f); r.set(-46f, -49f, -29f, -13f); cv.drawRoundRect(r, 9f, 9f, p); cv.restore()
            cv.save(); cv.rotate(-8f, 33f, -48f); r.set(29f, -49f, 46f, -13f); cv.drawRoundRect(r, 9f, 9f, p); cv.restore()
            vol(cv, -46f, -49f, -29f, -13f, 9f); vol(cv, 29f, -49f, 46f, -13f, 9f)
        }
        cv.restore()
        // face plate (parallax: moves most) with a soft shadow lip onto the head
        cv.save(); cv.translate(faceX, faceY)
        fill(0x261B1530)
        if (sp == "penguin") { r.set(-28f, -44.5f, 30f, -6.5f); cv.drawRoundRect(r, 22f, 22f, p) }
        else { r.set(-29f, -45.5f, 31f, -6.5f); cv.drawRoundRect(r, 20f, 20f, p) }
        fill(face)
        if (sp == "penguin") { r.set(-29f, -46f, 29f, -8f); cv.drawRoundRect(r, 22f, 22f, p); vol(cv, -29f, -46f, 29f, -8f, 22f, true) }
        else { r.set(-30f, -47f, 30f, -8f); cv.drawRoundRect(r, 20f, 20f, p); vol(cv, -30f, -47f, 30f, -8f, 20f, true) }
        // face details
        when (sp) {
            "dog" -> {
                fill(mix(face, 0xFFFFFFFF.toInt(), 0.55f)); r.set(-15f, -27f, 15f, -8f); cv.drawOval(r, p)
                fill(ink); r.set(-5f, -30f, 5f, -24f); cv.drawOval(r, p); vol(cv, -5f, -30f, 5f, -24f, -1f)
                fill(0xAAFFFFFF.toInt()); cv.drawCircle(-1.6f, -28.2f, 1.1f, p)
            }
            "bunny" -> {
                fill(0xFFF4809E.toInt()); cv.drawCircle(0f, -27f, 2.8f, p); volC(cv, 0f, -27f, 2.8f)
                tooth(cv)
            }
            "bear" -> {
                fill(mix(face, 0xFFFFFFFF.toInt(), 0.5f)); r.set(-14f, -28f, 14f, -8f); cv.drawOval(r, p)
                fill(ink); r.set(-4.5f, -30f, 4.5f, -24.5f); cv.drawOval(r, p); vol(cv, -4.5f, -30f, 4.5f, -24.5f, -1f)
            }
            "panda" -> {
                for (i in 0..1) {
                    val sd = i * 2 - 1
                    cv.save(); cv.rotate(-sd * 20f, sd * 14f, -30f)
                    fill(PANDA_INK); r.set(sd * 14f - 8.5f, -41f, sd * 14f + 8.5f, -19f); cv.drawOval(r, p)
                    cv.restore()
                }
                fill(PANDA_INK); r.set(-4.5f, -30f, 4.5f, -24.5f); cv.drawOval(r, p)
            }
            "fox" -> {
                fill(body)
                path.reset(); path.moveTo(-30f, -44f); path.quadTo(-30f, -47f, -26f, -47f); path.lineTo(26f, -47f)
                path.quadTo(30f, -47f, 30f, -44f); path.lineTo(0f, -33f); path.close(); cv.drawPath(path, p)
                fill(face)
                path.reset(); path.moveTo(-34f, -30f); path.lineTo(-41f, -19f); path.lineTo(-26f, -12f); path.close(); cv.drawPath(path, p)
                path.reset(); path.moveTo(34f, -30f); path.lineTo(41f, -19f); path.lineTo(26f, -12f); path.close(); cv.drawPath(path, p)
                r.set(-14f, -12f, 14f, -3.5f); cv.drawOval(r, p)
                path.reset(); path.moveTo(-3.5f, -29f); path.lineTo(3.5f, -29f); path.lineTo(0f, -24f); path.close()
                fill(ink); cv.drawPath(path, p)
                line(ink, 1.4f); cv.drawPath(path, p)
            }
            "frog" -> {
                line(mix(body, ink, 0.35f), 1.8f); r.set(-22f, -34f, 22f, -6f); cv.drawArc(r, 20f, 140f, false, p)
            }
            "capybara" -> {
                fill(mix(face, body, 0.4f)); r.set(-17f, -29f, 17f, -8f); cv.drawRoundRect(r, 6f, 6f, p)
                fill(0xFF3A2A22.toInt()); r.set(-8f, -30f, 8f, -24.5f); cv.drawRoundRect(r, 2.8f, 2.8f, p)
            }
            "hamster" -> {
                fill(0xFFFFFFFF.toInt()); r.set(-20f, -26f, 20f, -6f); cv.drawOval(r, p)
                fill(mix(face, 0xFFF4A3B8.toInt(), 0.25f))
                cv.drawCircle(-24f, -17f, 9.5f, p); cv.drawCircle(24f, -17f, 9.5f, p)
                fill(0xFFF4809E.toInt()); cv.drawCircle(0f, -27f, 2.5f, p)
                tooth(cv)
            }
            "owl" -> {
                for (i in 0..1) {
                    val sd = i * 2 - 1
                    fill(mix(face, 0xFFFFFFFF.toInt(), 0.5f)); cv.drawCircle(sd * 14f, -30f, 13f, p)
                    line(mix(body, ink, 0.2f), 1.4f); cv.drawCircle(sd * 14f, -30f, 13f, p)
                }
                path.reset(); path.moveTo(-4f, -28.5f); path.lineTo(4f, -28.5f); path.lineTo(0f, -23.5f); path.close()
                fill(0xFFFF9F1C.toInt()); cv.drawPath(path, p)
                line(0xFFE07A00.toInt(), 1.2f); cv.drawPath(path, p)
            }
            "duck" -> {
                fill(0xFFFF9F1C.toInt()); r.set(-9f, -29f, 9f, -21f); cv.drawRoundRect(r, 4f, 4f, p)
                line(0xFFE07A00.toInt(), 1.2f); cv.drawLine(-6f, -25f, 6f, -25f, p)
            }
            "pig" -> {
                fill(mix(face, 0xFFFFFFFF.toInt(), 0.35f)); r.set(-13f, -31f, 13f, -14f); cv.drawOval(r, p)
                fill(mix(body, ink, 0.3f)); cv.drawCircle(-4.2f, -24f, 1.7f, p); cv.drawCircle(4.2f, -24f, 1.7f, p)
            }
            "koala" -> {
                fill(0xFF3A3A44.toInt()); r.set(-6f, -32f, 6f, -23f); cv.drawOval(r, p); vol(cv, -6f, -32f, 6f, -23f, -1f)
                fill(0x88FFFFFF.toInt()); cv.drawCircle(-2f, -29.5f, 1.2f, p)
            }
            "lion" -> {
                fill(0xFF5A3418.toInt()); r.set(-3.2f, -29f, 3.2f, -24.5f); cv.drawOval(r, p)
            }
            "tiger" -> {
                fill(0xFFFFFFFF.toInt()); r.set(-13f, -28f, 13f, -8f); cv.drawOval(r, p)
                fill(0xFFF4809E.toInt()); r.set(-3.5f, -29f, 3.5f, -25f); cv.drawOval(r, p)
                line(ink, 2.4f)
                cv.drawLine(0f, -47f, 0f, -40f, p); cv.drawLine(-6f, -47f, -5f, -41f, p); cv.drawLine(6f, -47f, 5f, -41f, p)
                for (i in 0..1) {
                    val sd = i * 2 - 1
                    cv.drawLine(sd * 36f, -29f, sd * 29f, -28f, p)
                    cv.drawLine(sd * 36f, -23f, sd * 30f, -23f, p)
                }
            }
            "penguin" -> {
                path.reset(); path.moveTo(-4.5f, -29f); path.lineTo(4.5f, -29f); path.lineTo(0f, -23.5f); path.close()
                fill(0xFFFF9F1C.toInt()); cv.drawPath(path, p)
                line(0xFFE07A00.toInt(), 1.2f); cv.drawPath(path, p)
            }
            "cat" -> {
                path.reset(); path.moveTo(-3.8f, -29f); path.quadTo(0f, -31f, 3.8f, -29f)
                path.quadTo(1.6f, -24.5f, 0f, -24f); path.quadTo(-1.6f, -24.5f, -3.8f, -29f); path.close()
                fill(0xFFF4809E.toInt()); cv.drawPath(path, p); volPath(cv)
                line(0x88B03A5A.toInt(), 1f); cv.drawPath(path, p)
                fill(0xCCFFFFFF.toInt()); cv.drawCircle(-1.2f, -28.3f, 0.9f, p)
                whiskers(cv, 3)
                line(0x771B1530, 1.4f)
                cv.drawLine(0f, -48f, 0f, -43f, p); cv.drawLine(-4f, -48f, -5f, -44f, p); cv.drawLine(4f, -48f, 5f, -44f, p)
            }
        }
        if (sp == "fox" || sp == "tiger") whiskers(cv, 2)
        if (sp == "fox" || sp == "bear" || sp == "panda" || sp == "bunny" || sp == "pig") {
            fill(0xAAFFFFFF.toInt()); cv.drawCircle(-1.3f, -28.2f, 0.9f, p)
        }
        if (sp == "fox") {
            line(0x66B8561E, 1.4f)
            cv.drawLine(-31f, -22f, -37f, -20f, p); cv.drawLine(-31f, -18f, -36f, -14f, p)
            cv.drawLine(31f, -22f, 37f, -20f, p); cv.drawLine(31f, -18f, 36f, -14f, p)
        }
        cv.restore() // face plate
        shade(cv)
    }

    private fun whiskers(cv: Canvas, n: Int) {
        line(0x881B1530.toInt(), 1.1f)
        for (k in 0..1) {
            val sd = k * 2 - 1
            for (i in 0 until n) {
                val y = -24f + i * 3.6f
                cv.drawLine(sd * 21f, y, sd * 39f, y + (i - (n - 1) / 2f) * 3f, p)
            }
        }
    }

    /** Tiny mood emotes from fixed formulas (no allocation): heart / sparkle / star pop, sleeping bubble + z, anger vein. */
    private fun emotes(cv: Canvas, t: Float, doze: Float) {
        val e = em
        if (e == "love" || e == "happy" || e == "proud") {
            val k = (t % 2.6f) / 2.6f
            if (k < 0.55f) {
                val u2 = k / 0.55f
                val sz = 3.4f * (1f + 0.3f * sin(u2 * 3.1415927f)) * minOf(1f, u2 * 6f)
                val x = (if (e == "proud") -22f else 21f) + sin(t * 3f) * 2f; val y = -66f - u2 * 14f
                val al = (255 * (1f - u2 * u2)).toInt()
                when (e) {
                    "love" -> { fill(0xFFFF5C8A.toInt()); p.alpha = al; heart(cv, x, y, sz) }
                    "happy" -> { fill(0xFFFFC83D.toInt()); p.alpha = al; sparkle(cv, x, y, sz * 1.4f) }
                    else -> { fill(0xFFFFC83D.toInt()); p.alpha = al; star(cv, x, y, sz * 1.5f, t * 2f) }
                }
            }
        }
        if (e == "sleepy" || doze > 0.85f) {
            val k = (t % 3.4f) / 3.4f
            if (k < 0.92f) {   // nose bubble breathing in
                val rr = 1.5f + k * 5.5f
                line(0xCCBFD8FF.toInt(), 1.2f); cv.drawCircle(15f + k * 2f, -26f - k * 6f, rr, p)
                fill(0x33FFFFFF); cv.drawCircle(15f + k * 2f, -26f - k * 6f, rr, p)
            }
        }
        if (doze > 0.7f) for (i in 0..1) {
            val ph = (t * 0.42f + emPh[i] * 1.3f + i * 0.5f) % 1f
            fill(0xFF8E9BFF.toInt()); p.alpha = (sin(ph * 3.1415927f) * 230f).toInt()
            p.textSize = 9f + ph * 7f; p.typeface = Typeface.DEFAULT_BOLD
            cv.drawText("z", 22f + ph * 10f, -52f - ph * 22f, p)
        }
        if (e == "angry") {
            val pl = 3.2f * (1f + 0.22f * sin(t * 14f))
            line(0xFFE24B4A.toInt(), 1.9f)
            for (i in 0..3) {
                val sx2 = if (i and 1 == 0) 1f else -1f; val sy2 = if (i < 2) 1f else -1f
                val cx = 26f; val cy = -58f
                cv.drawLine(cx + sx2 * pl, cy + sy2 * pl * 0.35f, cx + sx2 * pl * 0.35f, cy + sy2 * pl * 0.35f, p)
                cv.drawLine(cx + sx2 * pl * 0.35f, cy + sy2 * pl, cx + sx2 * pl * 0.35f, cy + sy2 * pl * 0.35f, p)
            }
        }
    }

    private fun shade(cv: Canvas) {
        // glossy coat: crisp specular glint that drifts opposite to the tilt
        glint(cv, -14f - hx * 1.1f, -43f - hy * 0.8f, 17f, 7f, -28f)
    }

    private fun gun(cv: Canvas, t: Float) {
        val ph = (t % 1.2f) / 1.2f
        val rec = maxOf(0f, 1f - ph / 0.14f)
        cv.save()
        cv.translate(41f - rec * 3.5f, -12f)
        cv.rotate(-rec * 9f)
        val orange = 0xFFFF8A1F.toInt(); val blue = 0xFF3D8BFF.toInt()
        r.set(3f, -13f, 17f, -6f); fill(0xCC8FD0FF.toInt()); cv.drawRoundRect(r, 3f, 3f, p)
        line(ink, 1.6f); cv.drawRoundRect(r, 3f, 3f, p)
        cv.save(); cv.rotate(14f, 5f, 3f)
        r.set(1f, 2f, 9f, 14f); fill(blue); cv.drawRoundRect(r, 3f, 3f, p)
        line(ink, 1.8f); cv.drawRoundRect(r, 3f, 3f, p)
        cv.restore()
        r.set(0f, -7f, 24f, 3f); fill(orange); cv.drawRoundRect(r, 4f, 4f, p)
        line(ink, 1.8f); cv.drawRoundRect(r, 4f, 4f, p)
        r.set(22f, -5.5f, 31f, -0.5f); fill(blue); cv.drawRoundRect(r, 2.2f, 2.2f, p)
        line(ink, 1.6f); cv.drawRoundRect(r, 2.2f, 2.2f, p)
        r.set(10f, 2.5f, 15f, 9.5f); fill(0xFFFFC83D.toInt()); cv.drawRoundRect(r, 2f, 2f, p)
        line(ink, 1.4f); cv.drawRoundRect(r, 2f, 2f, p)
        fill(0x77FFFFFF); r.set(3f, -5.5f, 15f, -4f); cv.drawRoundRect(r, 1f, 1f, p)
        fill(light); cv.drawCircle(6f, 9f, 5f, p)
        line(ink, 1.6f); cv.drawCircle(6f, 9f, 5f, p)
        fill(0xFF9FD8FF.toInt()); cv.drawCircle(32.4f, 0.6f + minOf(1.6f, ph * 4f), 1.8f, p)
        cv.restore()
        if (ph < 0.45f) {
            val k = ph / 0.45f
            for (i in 0..2) {
                fill(0xFF9FD8FF.toInt()); p.alpha = ((1f - k) * 255f).toInt()
                cv.drawCircle(34f + k * (12f + i * 3f), -2f - i * 1.5f + k * k * (8f + i * 4f), 1.8f - k * 0.6f, p)
            }
        }
        if (ph < 0.38f) {
            val k = ph / 0.38f
            val sz = 4f + 4.5f * sin(k * 3.1415927f)
            fill(0xFFFFC83D.toInt()); star(cv, 46f, -25f, sz, k * 0.6f)
            line(ink, 1.1f); cv.drawPath(path, p)
            if (sz > 6.5f) {
                fill(0xFFE24B4A.toInt()); p.textSize = 5.6f; p.typeface = Typeface.DEFAULT_BOLD
                cv.drawText("POW", 40.5f, -23f, p)
            }
        }
    }

    private fun extras(cv: Canvas, t: Float, petT: Long, grabT: Long, wakeT: Long) {
        if (petT >= 0L) {
            val edge = minOf(1f, (2500f - petT) / 400f, petT / 300f).coerceIn(0f, 1f)
            for (i in 0..4) {
                val ph = ((petT / 1100f) + i * 0.2f) % 1f
                val x = -30f + i * 15f + sin(t * 3f + i * 1.7f) * 4f
                fill(0xFFFF5C8A.toInt()); p.alpha = (sin(ph * 3.1415927f) * 255f * edge).toInt()
                heart(cv, x, -52f - ph * 40f, 2.6f + (i % 3) * 0.9f)
            }
        }
        if (grabT >= 0L) {
            fill(0xFF6CB4F5.toInt()); drop(cv, 31f, -44f + (t * 7f) % 4f, 2.6f)
            fill(0xFFFFC83D.toInt())
            sparkle(cv, -38f, -50f, 1.5f + 2.5f * abs(sin(t * 9f)))
            sparkle(cv, 38f, -30f, 1.5f + 2.5f * abs(sin(t * 9f + 2f)))
        }
        if (wakeT >= 450L) {
            fill(0xFFFFC83D.toInt())
            sparkle(cv, 30f, -52f, 5f * maxOf(0f, sin((wakeT - 450L) / 1050f * 3.1415927f)))
        }
    }

    private fun updateLook(now: Long, dtS: Float, gain: Float) {
        val tiltActive = now - tiltStamp < 1500L && (abs(tgtTx) + abs(tgtTy) > 0.05f)
        // low-pass filter on the tilt input
        val kT = 1f - exp(-dtS * 8f)
        tx += (tgtTx - tx) * kT; ty += (tgtTy - ty) * kT
        // idle look-around: head turns toward random points with slow easing
        if (now >= idNext) {
            idNext = now + Random.nextLong(1800L, 4800L)
            if (Random.nextInt(100) < 25) { idTx = 0f; idTy = 0f }
            else { idTx = (Random.nextFloat() * 2f - 1f) * 0.85f; idTy = (Random.nextFloat() * 2f - 1f) * 0.45f }
        }
        val kI = 1f - exp(-dtS * 2.6f)
        val wantX = if (tiltActive) 0f else idTx * gain
        val wantY = if (tiltActive) 0f else idTy * gain
        idX += (wantX - idX) * kI; idY += (wantY - idY) * kI
        hx = (tx + idX).coerceIn(-1.2f, 1.2f); hy = (ty + idY).coerceIn(-1.2f, 1.2f)
        // eyes: random glances, sometimes following the tilt, or an explicit lookAt()
        if (now >= glNext) {
            glNext = now + Random.nextLong(700L, 2600L)
            if (Random.nextInt(100) < 35) { glX = 0f; glY = 0f }
            else { glX = (Random.nextFloat() * 2f - 1f) * 0.9f; glY = (Random.nextFloat() * 2f - 1f) * 0.55f }
        }
        val ex: Float; val ey: Float
        if (now - lkStamp < 2500L) { ex = lkTx; ey = lkTy }
        else if (tiltActive) { ex = tx * 0.9f + glX * 0.25f; ey = ty * 0.9f + glY * 0.25f }
        else { ex = glX * 0.7f * gain + idX * 0.3f; ey = glY * 0.7f * gain + idY * 0.3f }
        val kE = 1f - exp(-dtS * 14f)
        eyeX += (ex - eyeX) * kE; eyeY += (ey - eyeY) * kE
        // parallax layers: face plate and eyes move most, ears a bit, body least
        earX = hx * 1.6f; earY = hy * 1.1f
        bodyX = hx * 0.6f; bodyY = hy * 0.4f
        faceX = hx * 3.2f; faceY = hy * 2.2f
        eyeDx = hx * 1.6f; eyeDy = hy * 1.1f
        Outfits.earX = earX; Outfits.earY = earY
        Outfits.bodyX = bodyX; Outfits.bodyY = bodyY
        Outfits.eyeX = faceX + eyeDx; Outfits.eyeY = faceY + eyeDy
    }

    private fun motion(m: String, t: Float, petK: Float) {
        mvDx = 0f; mvDy = 0f; mvRot = 0f; mvS = 1f
        when (m) {
            "idle" -> mvDy = sin(t * 2.2f) * 1.5f
            "sleepy" -> mvDy = sin(t * 1.2f) * 2f
            "listen" -> { mvS = 1.07f; mvDy = -2f }
            "think" -> mvRot = sin(t * 7f) * 5f
            "happy" -> mvDy = -abs(sin(t * 9f)) * 10f
            "laugh" -> { mvDy = -abs(sin(t * 12f)) * 5f; mvRot = sin(t * 26f) * 4f }
            "sad" -> mvDy = 3f
            "offended" -> { mvDx = 4f; mvRot = 6f }
            "angry" -> mvRot = sin(t * 32f) * 2.5f
            "surprised" -> mvDy = -abs(sin(t * 5f)) * 4f
            "love" -> mvS = 1f + 0.04f * sin(t * 8f)
            "confused" -> mvRot = sin(t * 3f) * 5f
            "excited" -> { mvDy = -abs(sin(t * 11f)) * 12f; mvRot = sin(t * 22f) * 3f }
            "shy" -> { mvDx = -2f; mvDy = 1f; mvRot = -6f }
            "proud" -> { mvS = 1.04f; mvDy = -2f; mvRot = sin(t * 2f) * 1.5f }
            "cool" -> mvDy = sin(t * 2f)
            "wink" -> mvDy = -abs(sin(t * 6f)) * 3f
            "crying" -> { mvDy = 2f; mvDx = sin(t * 30f) * 0.8f }
            "dizzy" -> { mvRot = sin(t * 4f) * 10f; mvDx = sin(t * 4f + 1.5f) * 3f }
            "scared" -> { mvDx = sin(t * 40f) * 1.8f; mvS = 0.96f }
            "silly" -> { mvRot = sin(t * 5f) * 7f; mvDy = -abs(sin(t * 5f)) * 3f }
            "smug" -> { mvRot = 4f; mvDy = 1f }
            "worried" -> { mvDx = sin(t * 14f); mvDy = 1f }
            "tired" -> { mvDy = 3f + sin(t * 1f) * 1.5f; mvS = 0.97f }
            "dance" -> { mvDx = sin(t * 6f) * 7f; mvDy = -abs(sin(t * 6f)) * 7f; mvRot = sin(t * 6f) * 9f }
            "curious" -> mvRot = 8f + sin(t * 3f) * 2f
            "pet" -> { mvDy = sin(t * 2.4f) * 1.5f; mvRot = sin(t * 19f) * 2.2f; mvDx = sin(t * 23f) * 0.7f; mvS = 1f + 0.03f * petK }
        }
    }

    override fun onDraw(cv: Canvas) {
        if (!isAttachedToWindow && !offscreen) return
        val now = SystemClock.uptimeMillis()
        val t = now / 1000f
        val dtS = if (lastMs == 0L) 0.016f else ((now - lastMs) / 1000f).coerceIn(0f, 0.1f)
        lastMs = now
        if (bk == 0 && !dozing) microStep(now)
        idleStep(now, dtS)
        spkEnv = if (speaking) minOf(1f, spkEnv + dtS * 8f) else maxOf(0f, spkEnv - dtS * 8f)
        val spinT = if (spinStart != 0L) now - spinStart else -1L
        val petT = if (petStart != 0L) now - petStart else -1L
        val wakeT = if (wakeStart != 0L) now - wakeStart else -1L
        val grabT = if (grabbed) now - grabStart else -1L
        var sx = 1f; var spinA = 0f; var xtra = 0f; var wkDy = 0f; var petK = 0f
        em = mood
        if (spinT > 4700L) { spinStart = 0L }
        if (petT > 2500L) { petStart = 0L }
        if (wakeT > 1500L) { wakeStart = 0L; if (mood == "sleepy") mood = "idle" }
        if (spinT in 0L..4700L) {
            em = if (spinT < 1700L) "dizzy" else "offended"
            if (spinT < 700L) spinA = 360f * ease(spinT / 700f)
            if (spinT >= 1700L) { sx = 0.9f; xtra = 7f }
        } else if (grabT >= 0L) {
            em = if (grabT < 600L) "surprised" else "laugh"
        } else if (petT in 0L..2500L) {
            em = "pet"
            petK = minOf(1f, petT / 400f, (2500f - petT) / 400f).coerceIn(0f, 1f)
        } else if (wakeT in 0L..1500L) {
            em = if (wakeT < 450L) "tired" else if (wakeT < 900L) "surprised" else "idle"
            if (wakeT in 450L..900L) wkDy = -sin((wakeT - 450L) / 450f * 3.1415927f) * 6f
        }
        val mf =if (mk != 0) ((now - mStart).toFloat() / mDur).coerceIn(0f, 1f) else 0f
        val env = if (mk >= 4) sin(mf * 3.1415927f) else minOf(1f, minOf(mf, 1f - mf) * 6f)

        if (em != lastEm) { if (blend >= 1f && !offscreen) startBlend(); lastEm = em }   // a single frame has nothing to blend from
        var b = 1f
        if (blend < 1f) {
            blend = ((now - blendStart) / 220f).coerceIn(0f, 1f)
            b = blend * blend * (3f - 2f * blend)
        }
        motion(em, t, petK)
        var dx = mvDx; var dy = mvDy; var rot = mvRot; var s = mvS
        if (blend < 1f) {   // cross-blend the body bounce with the previous mood's motion
            motion(prevEm, t, petK)
            dx = mvDx + (dx - mvDx) * b; dy = mvDy + (dy - mvDy) * b
            rot = mvRot + (rot - mvRot) * b; s = mvS + (s - mvS) * b
        }
        val tEye = faceEye(em); val tMouth = faceMouth(em); val tBrow = faceBrow(em)
        cEye = pEye + (tEye - pEye) * b; cMouth = pMouth + (tMouth - pMouth) * b; cBrow = pBrow + (tBrow - pBrow) * b
        if (blend >= 1f) { cEye = tEye; cMouth = tMouth; cBrow = tBrow }
        if (grabT >= 0L) { rot = sin(t * 26f) * 7f; dx = sin(t * 19f) * 2.5f; dy = -2f + sin(t * 30f) }
        dx += xtra; dy += wkDy
        val lookGain = if (grabT >= 0L || spinT in 0L..4700L) 0f else when (em) {
            "idle", "listen", "think", "curious", "proud", "cool", "smug", "wink", "happy" -> 1f
            "sleepy", "tired", "pet", "sad", "crying" -> 0.3f
            else -> 0.5f
        }
        updateLook(now, dtS, lookGain)
        // ---- alive layer: idle behaviours, doze, touch squash, entrance (offsets on top of the mood motion)
        var qx = 1f; var qy = 1f
        var bEx = 0f; var bEy = 0f; var bf0 = 0f
        if (bk != 0 && em == "idle") {
            val bf = ((now - bStart).toFloat() / bDur).coerceIn(0f, 1f); bf0 = bf
            val bE = sin(bf * 3.1415927f)
            when (bk) {
                1 -> { qy *= 1f + 0.07f * bE; qx *= 1f - 0.03f * bE; bEy = -4f * bE; bEx = sin(bf * 18f) * 0.8f * bE; dy -= 1.5f * bE }
                2 -> { val d = 1f - bf; rot += sin(bf * 56f) * 9f * d; dx += sin(bf * 56f + 1f) * 2.5f * d; bEx = sin(bf * 70f) * 3f * d }
                3 -> { rot += bDir * 5f * bE; bEx = sin(bf * 47f) * 2.2f * bE; bEy = -abs(sin(bf * 31f)) * 2f * bE }
                4 -> { val hp = abs(sin(bf * 6.2831855f)); dy -= hp * 9f; qy *= 1f + 0.06f * hp; qx *= 1f - 0.03f * hp }
                5 -> { rot += sin(t * 5f) * 3.5f * bE; dy -= abs(sin(t * 5f)) * 1.2f }
                6 -> { dx += bDir * 9f * bE; rot += bDir * 7f * bE }
                7 -> { rot += bDir * 3f * bE }
            }
        }
        if (dozeK > 0f) { dy += dozeK * 2.5f; rot += dozeK * (4f + sin(t * 0.9f) * 2f) }
        earX += bEx; earY += bEy
        Outfits.earX = earX; Outfits.earY = earY   // hats / ear accessories follow the twitch
        if (sqStart != 0L) {   // soft squash-and-stretch when first touched
            val sqT = now - sqStart
            if (sqT > 320L) sqStart = 0L else {
                val x = sqT / 320f; val k = sin(x * 6.2831855f) * (1f - x)
                qx *= 1f + 0.07f * k; qy *= 1f - 0.09f * k
            }
        }
        if (touchDown && now - touchLast > 3000L) touchDown = false   // safety if touchEnd() is never called
        val entT = if (enterStart != 0L) now - enterStart else -1L
        if (entT > 700L) enterStart = 0L
        val entOn = entT in 0L..700L
        if (entOn) {
            val f = minOf(1f, entT / 385f)
            val c1 = 1.70158f; val c3 = c1 + 1f; val m1 = f - 1f
            val g = 1f - (1f + c3 * m1 * m1 * m1 + c1 * m1 * m1)   // easeOutBack: overshoots past the landing spot
            dx += entX * g * 34f; dy += entY * g * 34f
            s *= 0.55f + 0.45f * minOf(1f, f * 1.5f)
            if (entT > 385L) {
                val x = (entT - 385L) / 260f
                if (x < 1f) { val k = sin(x * 3.1415927f) * (1f - x * 0.4f); qx *= 1f + 0.12f * k; qy *= 1f - 0.16f * k }
            }
            if (entT > 520L) rot += sin(entT * 0.05f) * 4f
        }
        val entBlink = entT in 440L..530L
        val sleepEyes = bk == 1 || dozeK > 0.85f
        val happyEyes = bk == 5 || bk == 7 || (touchDown && now - touchStart > 450L && em == "idle")
        val eyeSy = if (blend < 1f) (cEye / tEye.coerceAtLeast(0.2f)).coerceIn(0.25f, 1.6f) else 1f
        val mOff = (cMouth - tMouth) * -1.6f
        val mSy = 1f + (cMouth - tMouth) * 0.25f
        val brOff = (cBrow - tBrow) * -3f
        var bs = base; var ls = light
        when (species) {
            "dog" -> bs = mix(base, 0xFFD9A066.toInt(), 0.25f)
            "panda" -> { bs = 0xFFFFFFFF.toInt(); ls = 0xFFFFFFFF.toInt() }
            "fox" -> { bs = 0xFFE8742C.toInt(); ls = 0xFFFFF1DC.toInt() }
            "frog" -> { bs = 0xFF6BBF59.toInt(); ls = 0xFFE5F6D3.toInt() }
            "penguin" -> { bs = 0xFF2D3A55.toInt(); ls = 0xFFFFFFFF.toInt() }
            "capybara" -> { bs = 0xFFB07A4B.toInt(); ls = 0xFFD8B088.toInt() }
            "hamster" -> { bs = 0xFFF2C48D.toInt(); ls = 0xFFFFF6EA.toInt() }
            "owl" -> { bs = 0xFF8B6B4A.toInt(); ls = 0xFFF0DDBB.toInt() }
            "duck" -> { bs = 0xFFFFD84D.toInt(); ls = 0xFFFFE98A.toInt() }
            "pig" -> { bs = 0xFFF5A3B8.toInt(); ls = 0xFFFAC4D2.toInt() }
            "koala" -> { bs = 0xFF9AA0A6.toInt(); ls = 0xFFC9CDD1.toInt() }
            "lion" -> { bs = 0xFFB5652B.toInt(); ls = 0xFFF0B349.toInt() }
            "tiger" -> { bs = 0xFFF08A24.toInt(); ls = 0xFFF5A04A.toInt() }
        }
        val body = if (em == "angry") mix(bs, 0xFFE24B4A.toInt(), 0.5f) else bs
        val face = when (em) {
            "angry" -> mix(ls, 0xFFFFC9C4.toInt(), 0.6f)
            "scared" -> mix(ls, 0xFFB9D4F2.toInt(), 0.45f)
            else -> ls
        }
        val isPanda = species == "panda"
        val ec = if (isPanda) 0xFFFFFFFF.toInt() else ink
        val lidCol = if (isPanda) PANDA_INK else face
        Outfits.headTop = when (species) { "frog" -> -4f; "lion" -> -5f; else -> 0f }

        cv.save()
        cv.translate(width / 2f, height - 6f * u - padPx)
        cv.scale(u, u)
        if (glow) {
            val pl = 0.78f + 0.22f * sin(t * 2.6f)
            gp.shader = glowShader; gp.alpha = (255 * pl).toInt()
            cv.save(); cv.translate(0f, -30f); cv.scale(36f * (0.92f + 0.08f * pl), 36f * (0.92f + 0.08f * pl)) // radius stays inside the view so the halo is never cut into a box
            cv.drawCircle(0f, 0f, 1f, gp)
            cv.restore()
        }
        if (stateGlow && badge.isNotEmpty() && !glow) drawStateGlow(cv)
        // contact shadow: squashes and fades with the jump, slides opposite to the tilt
        val j = (-dy / 12f).coerceIn(0f, 1f)
        val shk = 1f - 0.3f * j
        gp.shader = shadowShader
        gp.alpha = (150 * (1f - 0.45f * j)).toInt()
        cv.save(); cv.translate(dx * 0.5f - hx * 2.5f, -1f); cv.scale(44f * shk, 8f * shk)
        cv.drawCircle(0f, 0f, 1f, gp)
        cv.restore()
        gp.alpha = (235 * (1f - 0.5f * j)).toInt()
        cv.save(); cv.translate(dx * 0.5f - hx * 2f, -1f); cv.scale(30f * shk, 4.6f * shk)
        cv.drawCircle(0f, 0f, 1f, gp)
        cv.restore()
        cv.translate(dx, dy)
        cv.translate(0f, -28f); cv.rotate(spinA); cv.translate(0f, 28f)
        // squash & stretch on bounces plus slow breathing (3.2 s), head turns with the tilt
        val jumpy = when (em) { "happy", "laugh", "excited", "dance", "wink", "silly", "surprised" -> true; else -> false }
        val sq = (if (jumpy) (j - 0.35f) * 0.09f else 0f) + sin(t * 1.9634954f) * 0.014f
        cv.rotate(rot + hx * 2.9f); cv.skew(-hy * 0.03f, 0f)
        cv.scale(s * sx * (1f - sq * 0.6f) * qx, s * (1f + sq) * qy)

        Outfits.drawBack(cv, p, outfit, em, t)
        drawHead(cv, body, face)

        val ch = when (em) { "offended" -> 8f; "love" -> 6f; "shy" -> 7.5f; "pet" -> 5f + 5f * petK; else -> 4.5f }
        val strong = em == "love" || em == "offended" || em == "shy" || em == "pet"
        // features live on the face plate layer
        cv.save(); cv.translate(faceX, faceY)
        for (i in 0..1) {
            val bx = (i * 2 - 1) * 24f
            // soft radial blush
            p.style = Paint.Style.FILL; p.color = 0xFFFFFFFF.toInt(); p.alpha = 255
            p.shader = if (strong) blushStrongSh else blushSoftSh
            cv.save(); cv.translate(bx, -17f); cv.scale(ch * 2.1f, ch * 1.55f)
            cv.drawCircle(0f, 0f, 1f, p)
            cv.restore()
            p.shader = null
            fill(0x44FFFFFF); cv.drawCircle(bx - ch * 0.4f, -17f - ch * 0.4f, ch * 0.25f, p)
        }

        cv.save(); cv.translate(eyeDx, eyeDy)

        if (eyeSy != 1f) { cv.save(); cv.translate(0f, -30f); cv.scale(1f, eyeSy); cv.translate(0f, 30f) }   // mood blend: eye openness
        for (i in 0..1) {
            val sd = i * 2 - 1
            val x = sd * 14f
            val y = -30f
            when {
                mk == 1 || entBlink || (mk == 2 && sd == mDir) -> { line(ec, 2.4f); cv.drawLine(x - 5f, y, x + 5f, y, p) }
                happyEyes -> { line(ec, 2.8f); r.set(x - 5f, y - 4f, x + 5f, y + 6f); cv.drawArc(r, 200f, 140f, false, p) }
                mk == 4 || sleepEyes -> { line(ec, 2.4f); r.set(x - 5f, y - 4f, x + 5f, y + 4f); cv.drawArc(r, 20f, 140f, false, p) }
                em == "happy" || em == "laugh" || em == "dance" || em == "pet" || (em == "wink" && sd == 1) -> {
                    line(ec, 2.8f); r.set(x - 5f, y - 4f, x + 5f, y + 6f); cv.drawArc(r, 200f, 140f, false, p)
                }
                em == "love" -> { fill(0xFFFF5C8A.toInt()); heart(cv, x, y, 3.6f + sin(t * 8f) * 0.5f) }
                em == "sleepy" -> {
                    line(ec, 2.4f); r.set(x - 5f, y - 4f, x + 5f, y + 4f); cv.drawArc(r, 20f, 140f, false, p)
                }
                em == "surprised" -> {
                    fill(0xFFFFFFFF.toInt()); cv.drawCircle(x, y, 6.5f, p)
                    line(ink, 1.2f); cv.drawCircle(x, y, 6.5f, p)
                    fill(ink); cv.drawCircle(x + eyeX * 2.2f, y + eyeY * 1.8f, 2.6f, p)
                    fill(0xCCFFFFFF.toInt()); cv.drawCircle(x + eyeX * 2.2f - 0.8f, y + eyeY * 1.8f - 0.9f, 0.9f, p)
                }
                em == "scared" -> {
                    fill(0xFFFFFFFF.toInt()); cv.drawCircle(x, y, 6.8f, p)
                    line(ink, 1.2f); cv.drawCircle(x, y, 6.8f, p)
                    fill(ink); cv.drawCircle(x + sin(t * 30f), y + 0.5f, 1.8f, p)
                }
                em == "excited" -> {
                    fill(0xFFFFC83D.toInt()); star(cv, x, y, 7f + sin(t * 10f) * 0.8f, t * 2f)
                    line(ink, 1.1f); cv.drawPath(path, p)
                }
                em == "dizzy" -> {
                    fill(0xFFFFFFFF.toInt()); cv.drawCircle(x, y, 6.5f, p)
                    line(ink, 1.5f); spiral(cv, x, y, 5.8f, t * 8f)
                }
                em == "cool" -> {
                    fill(0xFF14102A.toInt()); r.set(x - 8.5f, y - 5f, x + 8.5f, y + 5.5f); cv.drawRoundRect(r, 3.5f, 3.5f, p)
                    line(0x99FFFFFF.toInt(), 1.5f)
                    cv.drawLine(x - 5.5f, y - 2.5f, x - 2.5f, y - 2.5f, p)
                    cv.drawLine(x - 5.5f, y, x - 4f, y, p)
                }
                em == "silly" -> {
                    fill(0xFFFFFFFF.toInt()); cv.drawCircle(x, y, 6f, p)
                    line(ink, 1.2f); cv.drawCircle(x, y, 6f, p)
                    fill(ink); cv.drawCircle(x - sd * 2.4f + eyeX * 1.5f, y + 0.5f + eyeY * 1.2f, 2.5f, p)
                    fill(0xCCFFFFFF.toInt()); cv.drawCircle(x - sd * 2.4f + eyeX * 1.5f - 0.8f, y - 0.4f + eyeY * 1.2f, 0.8f, p)
                }
                else -> {
                    var er = 4.5f; var ox = 0f; var oy = 0f; var hl = 1.4f; var lid = 99f
                    when (em) {
                        "listen" -> er = 5.8f
                        "think" -> oy = -2.5f
                        "offended" -> { ox = 2.2f; lid = -0.5f }
                        "angry" -> er = 3.8f
                        "confused" -> er = if (sd < 0) 5.6f else 3.4f
                        "sad" -> oy = 1.5f
                        "shy" -> { er = 4f; ox = -2f; oy = 2f }
                        "proud" -> { er = 5f; oy = -1.5f; hl = 1.8f }
                        "crying" -> { er = 5.4f; oy = 1f; hl = 2.3f }
                        "worried" -> { er = 5f; oy = 1f; hl = 2f }
                        "curious" -> { er = 5.6f; ox = 2f; oy = -2f; hl = 1.8f }
                        "smug" -> { ox = -2f; lid = 0.5f }
                        "tired" -> { oy = 1f; lid = 1.5f }
                        "idle" -> if (mk == 3) ox = mDir * 3f * env
                    }
                    ox += eyeX * 1.8f; oy += eyeY * 1.3f
                    if (dozeK > 0f) lid = -6f + dozeK * 12f   // eyelids sink while dozing off
                    if (isPanda) { fill(0xFFFFFFFF.toInt()); cv.drawCircle(x + ox, y + oy, er + 1.8f, p) }
                    fill(ink); cv.drawCircle(x + ox, y + oy, er, p)
                    // layered iris that follows the look direction
                    val ix = x + ox + eyeX * er * 0.3f; val iy = y + oy + eyeY * er * 0.25f
                    p.style = Paint.Style.FILL; p.color = 0xFFFFFFFF.toInt(); p.shader = irisSh
                    cv.save(); cv.translate(ix, iy); cv.scale(er * 0.88f, er * 0.88f)
                    cv.drawCircle(0f, 0f, 1f, p)
                    cv.restore()
                    fill(ink); cv.drawCircle(ix, iy, er * 0.42f, p)
                    // faint window reflection (lower right), main and secondary glints stay fixed
                    fill(0x40FFFFFF); r.set(ix + er * 0.1f, iy + er * 0.15f, ix + er * 0.46f, iy + er * 0.4f); cv.drawRoundRect(r, 0.5f, 0.5f, p)
                    fill(0xFFFFFFFF.toInt()); cv.drawCircle(x + ox + 1.6f, y + oy - 1.6f, hl, p)
                    fill(0xCCFFFFFF.toInt()); cv.drawCircle(x + ox - 1.5f, y + oy + 1.7f, hl * 0.55f, p)
                    if (lid < 50f) {
                        fill(lidCol); cv.drawRect(x - 7f + ox, y - 7f, x + 7f + ox, y + lid, p)
                        line(ec, 2f); cv.drawLine(x - 5f + ox, y + lid, x + 5f + ox, y + lid, p)
                    }
                    if (em == "tired") {
                        line(0x771B1530, 1.4f); r.set(x - 5f, y + 4f, x + 5f, y + 10f); cv.drawArc(r, 30f, 120f, false, p)
                    }
                }
            }
        }

        if (eyeSy != 1f) cv.restore()
        cv.save(); cv.translate(0f, brOff)   // mood blend: brow height
        line(ink, 2.2f)
        when (em) {
            "angry" -> { cv.drawLine(-21f, -40f, -9f, -35f, p); cv.drawLine(21f, -40f, 9f, -35f, p) }
            "sad" -> { cv.drawLine(-20f, -37f, -9f, -40f, p); cv.drawLine(20f, -37f, 9f, -40f, p) }
            "offended" -> { cv.drawLine(-20f, -39f, -9f, -37f, p); cv.drawLine(20f, -37f, 9f, -39f, p) }
            "surprised" -> { cv.drawLine(-19f, -42f, -9f, -42f, p); cv.drawLine(19f, -42f, 9f, -42f, p) }
            "confused" -> { cv.drawLine(-20f, -43f, -9f, -40f, p); cv.drawLine(9f, -37f, 20f, -37f, p) }
            "shy" -> { cv.drawLine(-20f, -39f, -9f, -41f, p); cv.drawLine(20f, -39f, 9f, -41f, p) }
            "proud" -> { cv.drawLine(-20f, -42f, -9f, -43f, p); cv.drawLine(20f, -42f, 9f, -43f, p) }
            "wink" -> cv.drawLine(9f, -41f, 20f, -44f, p)
            "crying" -> { cv.drawLine(-21f, -36f, -9f, -42f, p); cv.drawLine(21f, -36f, 9f, -42f, p) }
            "scared" -> { cv.drawLine(-20f, -41f, -9f, -45f, p); cv.drawLine(20f, -41f, 9f, -45f, p) }
            "smug" -> { cv.drawLine(-20f, -39f, -9f, -38f, p); cv.drawLine(9f, -41f, 20f, -45f, p) }
            "worried" -> { cv.drawLine(-20f, -37f, -9f, -42f, p); cv.drawLine(20f, -37f, 9f, -42f, p) }
            "tired" -> { cv.drawLine(-20f, -36f, -9f, -38f, p); cv.drawLine(20f, -36f, 9f, -38f, p) }
            "curious" -> { cv.drawLine(-20f, -41f, -9f, -40f, p); cv.drawLine(9f, -44f, 20f, -46f, p) }
        }

        cv.restore() // brow blend
        cv.restore() // eyes layer
        // behaviour paw (scratch) on the cheek, drawn on the face plate
        if (bk == 7 && bf0 > 0f) {
            fill(body); val py0 = -21f + sin(t * 34f) * 2.5f
            cv.drawCircle(bDir * 24f, py0, 5.5f, p)
            line(0x881B1530.toInt(), 1.4f); cv.drawCircle(bDir * 24f, py0, 5.5f, p)
        }
        cv.save(); cv.translate(0f, mOff + 15f * mSy - 15f); cv.scale(1f, mSy)   // mood blend: mouth

        when (em) {
            "happy", "love", "wink", "pet" -> { line(ink, 2.6f); r.set(-8f, -24f, 8f, -10f); cv.drawArc(r, 10f, 160f, false, p) }
            "laugh" -> {
                fill(ink); r.set(-7f, -22f, 7f, -9f); cv.drawOval(r, p)
                fill(0xFFFF8FA3.toInt()); r.set(-4f, -14f, 4f, -9.5f); cv.drawOval(r, p)
            }
            "sad" -> { line(ink, 2.4f); r.set(-6f, -18f, 6f, -9f); cv.drawArc(r, 200f, 140f, false, p) }
            "offended" -> { line(ink, 2.2f); r.set(-4f, -17f, 4f, -11f); cv.drawArc(r, 200f, 140f, false, p) }
            "angry" -> {
                line(ink, 2.2f); path.reset()
                path.moveTo(-7f, -13f); path.lineTo(-3.5f, -17f); path.lineTo(0f, -13f)
                path.lineTo(3.5f, -17f); path.lineTo(7f, -13f); cv.drawPath(path, p)
            }
            "surprised" -> { fill(ink); r.set(-3.5f, -20f, 3.5f, -11f); cv.drawOval(r, p) }
            "confused", "dizzy" -> {
                line(ink, 2.2f); path.reset(); path.moveTo(-7f, -15f)
                path.quadTo(-3.5f, -19f, 0f, -15f); path.quadTo(3.5f, -11f, 7f, -15f); cv.drawPath(path, p)
            }
            "think" -> {
                fill(0xFF7B4FD6.toInt())
                for (i in -1..1) {
                    p.alpha = (255 * (0.4f + 0.6f * abs(sin(t * 5f + i)))).toInt()
                    cv.drawCircle(i * 5f, -15f, 2f, p)
                }
            }
            "sleepy" -> { line(ink, 2f); r.set(-3f, -18f, 3f, -12f); cv.drawArc(r, 20f, 140f, false, p) }
            "excited" -> {
                fill(ink); r.set(-9f, -23f, 9f, -8f); cv.drawArc(r, 0f, 180f, true, p)
                fill(0xFFFF8FA3.toInt()); r.set(-3.5f, -12.5f, 3.5f, -9.5f); cv.drawOval(r, p)
            }
            "dance" -> {
                fill(ink); r.set(-6.5f, -21f, 6.5f, -9f); cv.drawArc(r, 0f, 180f, true, p)
                fill(0xFFFF8FA3.toInt()); r.set(-3f, -12.5f, 3f, -9.8f); cv.drawOval(r, p)
            }
            "shy" -> { line(ink, 2f); r.set(-3.5f, -18f, 3.5f, -12f); cv.drawArc(r, 20f, 140f, false, p) }
            "proud" -> { line(ink, 2.6f); r.set(-8f, -24f, 8f, -12f); cv.drawArc(r, 20f, 140f, false, p) }
            "cool" -> { line(ink, 2.4f); r.set(-8f, -23f, 8f, -11f); cv.drawArc(r, 30f, 120f, false, p) }
            "crying" -> {
                val top = -20f + sin(t * 18f) * 1.2f
                fill(ink); r.set(-4.5f, top, 4.5f, -9f); cv.drawOval(r, p)
                fill(0xFFFF8FA3.toInt()); r.set(-3f, -12.5f, 3f, -9.8f); cv.drawOval(r, p)
            }
            "scared" -> {
                line(ink, 2.2f); path.reset()
                path.moveTo(-8f, -12f); path.lineTo(-4f, -18f); path.lineTo(0f, -12f)
                path.lineTo(4f, -18f); path.lineTo(8f, -12f); cv.drawPath(path, p)
            }
            "silly" -> {
                line(ink, 2.4f); r.set(-8f, -24f, 8f, -12f); cv.drawArc(r, 10f, 160f, false, p)
                val tx = sin(t * 8f) * 1.5f
                fill(0xFFFF7A9A.toInt()); r.set(-3.5f + tx, -16f, 3.5f + tx, -7f); cv.drawRoundRect(r, 3.5f, 3.5f, p)
                line(0xFFC94A6A.toInt(), 1f); cv.drawLine(tx, -15f, tx, -10f, p)
            }
            "smug" -> {
                line(ink, 2.4f); path.reset(); path.moveTo(-7f, -16f)
                path.quadTo(0f, -12f, 7f, -20f); cv.drawPath(path, p)
                cv.drawLine(7f, -20f, 8.5f, -22f, p)
            }
            "worried" -> {
                line(ink, 2.2f); path.reset(); path.moveTo(-6f, -13f)
                path.quadTo(-3f, -16f, 0f, -13f); path.quadTo(3f, -10f, 6f, -13f); cv.drawPath(path, p)
            }
            "tired" -> { line(ink, 2.2f); cv.drawLine(-5f, -14f, 5f, -14f, p) }
            "curious" -> { line(ink, 2f); cv.drawCircle(0f, -14.5f, 2.8f, p) }
            else -> when {
                bk == 1 -> { fill(ink); val yo = sin(bf0 * 3.1415927f); r.set(-4f - yo * 2f, -13f - yo * 10f, 4f + yo * 2f, -9f); cv.drawOval(r, p) }
                bk == 5 -> { fill(ink); val yo = 1.5f + abs(sin(t * 5f)) * 2f; r.set(-yo, -15f - yo, yo, -15f + yo); cv.drawOval(r, p) }
                bk == 7 -> { line(ink, 2.4f); r.set(-6f, -22f, 6f, -12f); cv.drawArc(r, 20f, 140f, false, p) }
                dozeK > 0.3f -> { fill(ink); val yo = 1.2f + dozeK * 1.2f + sin(t * 1.6f) * 0.4f; r.set(-yo, -14f - yo, yo, -14f + yo); cv.drawOval(r, p) }
                mk == 4 -> { fill(ink); r.set(-4f - env * 1.5f, -13f - env * 9f, 4f + env * 1.5f, -9f); cv.drawOval(r, p) }
                mk == 5 -> { line(ink, 2.4f); r.set(-5f - 3f * env, -22f, 5f + 3f * env, -11f); cv.drawArc(r, 10f, 160f, false, p) }
                else -> { line(ink, 2.4f); r.set(-6f, -22f, 6f, -12f); cv.drawArc(r, 20f, 140f, false, p) }
            }
        }

        if (spkEnv > 0.02f) {
            val o = syll(t) * spkEnv
            val w = 3.5f + 3f * o; val h = (1.5f + 8f * o) * spkEnv
            fill(ink); r.set(-w, -15f - h * 0.4f, w, -15f + h * 0.6f); cv.drawOval(r, p)
            if (h > 5f) { fill(0xFFFF8FA3.toInt()); r.set(-w * 0.55f, -15f + h * 0.6f - h * 0.38f, w * 0.55f, -15f + h * 0.6f - 0.4f); cv.drawOval(r, p) }
        }

        cv.restore() // mouth blend
        cv.restore() // face plate layer

        when (em) {
            "sad" -> { fill(0xFF6CB4F5.toInt()); cv.drawCircle(-19f, -24f + (t * 8f) % 9f, 1.8f, p) }
            "angry" -> {
                fill(0xCCFFFFFF.toInt()); val k = (t * 18f) % 12f
                cv.drawCircle(-27f, -62f - k, 2.4f, p); cv.drawCircle(27f, -62f - k * 0.8f, 2.4f, p)
            }
            "sleepy" -> {
                fill(0xAA1B1530.toInt()); p.textSize = 14f; p.typeface = Typeface.DEFAULT_BOLD
                cv.drawText("z", 24f, -50f - (t * 6f) % 6f, p)
            }
            "excited" -> {
                fill(0xFFFFC83D.toInt())
                sparkle(cv, -39f, -56f, 2.5f + 2.5f * abs(sin(t * 7f)))
                sparkle(cv, 39f, -48f, 2.5f + 2.5f * abs(sin(t * 7f + 2f)))
                sparkle(cv, 0f, -63f, 2.5f + 2.5f * abs(sin(t * 7f + 4f)))
            }
            "shy" -> {
                line(0xFFFF5C8A.toInt(), 1.2f)
                for (i in 0..1) {
                    val cx = (i * 2 - 1) * 24f
                    cv.drawLine(cx - 4f, -13f, cx - 2f, -19f, p)
                    cv.drawLine(cx, -13f, cx + 2f, -19f, p)
                    cv.drawLine(cx + 4f, -13f, cx + 6f, -19f, p)
                }
            }
            "proud" -> { fill(0xFFFFC83D.toInt()); sparkle(cv, 34f, -40f, 2.5f + 2f * abs(sin(t * 6f))) }
            "cool" -> {
                line(0xFF14102A.toInt(), 2.2f)
                cv.drawLine(-5.5f, -32f, 5.5f, -32f, p)
                cv.drawLine(-22.5f, -32f, -29f, -34f, p); cv.drawLine(22.5f, -32f, 29f, -34f, p)
                fill(0xFFFFFFFF.toInt()); sparkle(cv, 24f, -38f, 1.5f + 2f * abs(sin(t * 4f)))
            }
            "wink" -> { fill(0xFFFFC83D.toInt()); sparkle(cv, 29f, -38f, 2.5f + 2f * abs(sin(t * 8f))) }
            "crying" -> {
                for (i in 0..1) {
                    val sd = i * 2 - 1
                    line(0xAA6CB4F5.toInt(), 3f)
                    cv.drawLine(sd * 14f, -25f, sd * 15f, -17f + 4f * abs(sin(t * 9f + i)), p)
                    fill(0xFF6CB4F5.toInt()); cv.drawCircle(sd * 17f, -22f + (t * 14f + i * 5f) % 14f, 1.8f, p)
                }
            }
            "dizzy" -> {
                fill(0xFFFFC83D.toInt())
                for (i in 0..2) {
                    val a = t * 4f + i * 2.0944f
                    star(cv, cos(a) * 17f, -60f + sin(a) * 4f, 4f, t * 3f)
                }
            }
            "scared" -> { fill(0xFF6CB4F5.toInt()); drop(cv, 31f, -44f + (t * 6f) % 5f, 2.6f) }
            "worried" -> { fill(0xFF6CB4F5.toInt()); drop(cv, 28f, -44f + sin(t * 3f) * 1.5f, 2.6f) }
            "dance" -> {
                for (i in 0..1) {
                    val ph = (t * 0.9f + i * 0.5f) % 1f
                    val nx = (if (i == 0) -34f else 34f) + sin(t * 4f + i * 3f) * 2f
                    note(cv, nx, -40f - ph * 26f, (255 * (1f - ph)).toInt())
                }
            }
            "curious" -> {
                fill(0xFF7B4FD6.toInt()); p.textSize = 15f; p.typeface = Typeface.DEFAULT_BOLD
                cv.drawText("?", -4f, -56f - abs(sin(t * 3f)) * 2f, p)
            }
        }
        emotes(cv, t, dozeK)
        if (bk == 5) for (i in 0..1) {
            val ph = (t * 0.9f + emPh[i] + i * 0.5f) % 1f
            note(cv, (if (i == 0) -34f else 34f) + sin(t * 4f + i * 3f) * 2f, -40f - ph * 26f, (255 * (1f - ph)).toInt())
        }
        if (bk == 2) for (i in 0..3) {   // shaken-off droplets
            val sd = if (i and 1 == 0) 1f else -1f
            fill(0xFF6CB4F5.toInt()); p.alpha = (200 * (1f - bf0)).toInt()
            cv.drawCircle(sd * (28f + bf0 * (12f + i * 5f)), -34f - i * 4f + bf0 * bf0 * 14f, 1.7f, p)
        }
        if (entOn && entT > 385L) {
            val x = (entT - 385L) / 315f
            fill(0xFFFFC83D.toInt())
            for (i in 0..5) {   // sparkle puff around the landing spot
                val a = puffAng[i]; val rr = 16f + x * 24f
                p.alpha = (255 * (1f - x)).toInt()
                sparkle(cv, cos(a) * rr, -10f + sin(a) * rr * 0.55f, 1.5f + 2.5f * (1f - x))
            }
            if (entT > 520L) {   // waving paw
                fill(body); val wy = -26f + sin(entT * 0.05f) * 6f
                cv.drawCircle(38f, wy, 5.5f, p)
                line(0x881B1530.toInt(), 1.4f); cv.drawCircle(38f, wy, 5.5f, p)
            }
        }
        if (em == "angry") gun(cv, t)
        extras(cv, t, if (petT in 0L..2500L && em == "pet") petT else -1L,
            if (grabT >= 0L) grabT else -1L, if (wakeT in 0L..1500L) wakeT else -1L)
        Outfits.draw(cv, p, outfit, em, t)
        if (badgeOn && badge.isNotEmpty()) drawBadge(cv, t)
        cv.restore()
    }
}
