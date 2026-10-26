package com.example.buddy

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin

object Outfits {
    val SLOTS: Map<String, List<String>> = linkedMapOf(
        "head" to listOf("hat", "cap", "crown", "bow", "headphones", "flower", "party", "santa",
            "wizard", "ninja", "samurai", "pirate", "astronaut", "halo", "horns", "catears", "beanie", "pumpkin"),
        "eyes" to listOf("glasses", "sunglasses", "mask", "visor"),
        "neck" to listOf("scarf", "bowtie", "cape", "tie"),
        "aura" to listOf("fire", "ice", "lightning", "hearts", "sakura", "stars"),
        "hand" to listOf("wand", "sword", "umbrella", "lollipop")
    )

    val LABELS: Map<String, String> = mapOf(
        "hat" to "Шляпа", "cap" to "Кепка", "crown" to "Корона", "bow" to "Бант",
        "headphones" to "Наушники", "flower" to "Цветок", "party" to "Колпак", "santa" to "Шапка Санты",
        "wizard" to "Колпак волшебника", "ninja" to "Повязка ниндзя", "samurai" to "Шлем самурая",
        "pirate" to "Шляпа пирата", "astronaut" to "Шлем космонавта", "halo" to "Нимб",
        "horns" to "Рожки", "catears" to "Кошачьи ушки", "beanie" to "Вязаная шапка", "pumpkin" to "Тыква",
        "glasses" to "Очки", "sunglasses" to "Солнечные очки", "mask" to "Маска героя", "visor" to "Кибер-визор",
        "scarf" to "Шарф", "bowtie" to "Бабочка", "cape" to "Плащ", "tie" to "Галстук",
        "fire" to "Огонь", "ice" to "Лёд", "lightning" to "Молния", "hearts" to "Сердечки",
        "sakura" to "Сакура", "stars" to "Звёзды",
        "wand" to "Волшебная палочка", "sword" to "Меч", "umbrella" to "Зонтик", "lollipop" to "Леденец"
    )

    val PRESETS: Map<String, Set<String>> = linkedMapOf(
        "ninja" to setOf("ninja", "scarf"),
        "wizard" to setOf("wizard", "wand", "stars"),
        "samurai" to setOf("samurai", "sword"),
        "superhero" to setOf("mask", "cape", "lightning"),
        "pirate" to setOf("pirate", "sword"),
        "astronaut" to setOf("astronaut", "stars"),
        "fire_mage" to setOf("wizard", "fire", "wand"),
        "ice_queen" to setOf("crown", "ice", "wand"),
        "angel" to setOf("halo", "sakura"),
        "devil" to setOf("horns", "fire", "cape")
    )

    val PRESET_LABELS: Map<String, String> = mapOf(
        "ninja" to "Ниндзя", "wizard" to "Волшебник", "samurai" to "Самурай", "superhero" to "Супергерой",
        "pirate" to "Пират", "astronaut" to "Космонавт", "fire_mage" to "Огненный маг",
        "ice_queen" to "Снежная королева", "angel" to "Ангел", "devil" to "Демон"
    )

    private val SLOT_OF: Map<String, String> =
        SLOTS.entries.flatMap { e -> e.value.map { it to e.key } }.toMap()

    private const val INK = 0xFF1B1530.toInt()
    /** Vertical lift (negative = up) for head-top items; set by BuddyView per species. */
    @JvmField var headTop = 0f
    /** Parallax offsets (units) set by BuddyView: ears layer, body layer, eyes (face plate + eyes). */
    @JvmField var earX = 0f
    @JvmField var earY = 0f
    @JvmField var bodyX = 0f
    @JvmField var bodyY = 0f
    @JvmField var eyeX = 0f
    @JvmField var eyeY = 0f
    private val path = Path()
    private val r = RectF()
    private val rb = RectF()
    private val ur = RectF()
    private val gp = Paint(Paint.ANTI_ALIAS_FLAG)

    // Unit-space shaders (radius 1, scaled by the canvas at draw time), built once.
    private val VOL: Shader = RadialGradient(-0.38f, -0.5f, 1.55f,
        intArrayOf(0x55FFFFFF, 0x10FFFFFF, 0x00000000, 0x30301848, 0x66301848),
        floatArrayOf(0f, 0.22f, 0.45f, 0.8f, 1f), Shader.TileMode.CLAMP)
    private val SHADOW: Shader = RadialGradient(0f, 0f, 1f, 0x66000000, 0x00000000, Shader.TileMode.CLAMP)
    private fun glowShader(c: Int): Shader = RadialGradient(0f, 0f, 1f,
        intArrayOf((0x70 shl 24) or (c and 0xFFFFFF), (0x28 shl 24) or (c and 0xFFFFFF), c and 0xFFFFFF),
        floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP)
    private val G_FIRE = glowShader(0xFF8A1F)
    private val G_ICE = glowShader(0x9FE4FF)
    private val G_BOLT = glowShader(0xFFE066)
    private val G_STAR = glowShader(0xFFC83D)
    private val G_HEART = glowShader(0xFF5C8A)
    private val G_SAKURA = glowShader(0xFFB7D0)
    private val G_HALO = glowShader(0xFFE066)
    private val HEADS = arrayOf("hat", "cap", "crown", "bow", "party", "santa", "wizard", "ninja",
        "samurai", "pirate", "halo", "horns", "catears", "beanie", "pumpkin")

    /** Soft additive-looking glow disc (unit shader scaled to rx,ry). */
    private fun glowDisc(cv: Canvas, sh: Shader, cx: Float, cy: Float, rx: Float, ry: Float, a: Int) {
        if (a <= 0) return
        gp.style = Paint.Style.FILL; gp.shader = sh; gp.alpha = a.coerceAtMost(255)
        cv.save(); cv.translate(cx, cy); cv.scale(rx, ry)
        cv.drawCircle(0f, 0f, 1f, gp)
        cv.restore()
        gp.shader = null
    }

    private fun volRect(cv: Canvas, p: Paint, l: Float, t: Float, rt: Float, b: Float, rad: Float) {
        val hw = (rt - l) * 0.5f; val hh = (b - t) * 0.5f
        if (hw <= 0f || hh <= 0f) return
        cv.save(); cv.translate(l + hw, t + hh); cv.scale(hw, hh)
        p.style = Paint.Style.FILL; p.shader = VOL
        ur.set(-1f, -1f, 1f, 1f)
        if (rad < 0f) cv.drawOval(ur, p) else cv.drawRoundRect(ur, rad / hw, rad / hh, p)
        p.shader = null
        cv.restore()
    }

    private fun volPath(cv: Canvas, p: Paint) {
        path.computeBounds(rb, true)
        val hw = rb.width() * 0.5f; val hh = rb.height() * 0.5f
        if (hw <= 0.01f || hh <= 0.01f) return
        cv.save(); cv.clipPath(path)
        cv.translate(rb.centerX(), rb.centerY()); cv.scale(hw, hh)
        p.style = Paint.Style.FILL; p.shader = VOL
        ur.set(-1.1f, -1.1f, 1.1f, 1.1f); cv.drawRect(ur, p)
        p.shader = null
        cv.restore()
    }

    fun parse(pref: String): Set<String> {
        val bySlot = LinkedHashMap<String, String>()
        for (raw in pref.split(',')) {
            val key = raw.trim().lowercase()
            val preset = PRESETS[key]
            if (preset != null) {
                for (item in preset) { val s = SLOT_OF[item] ?: continue; bySlot[s] = item }
                continue
            }
            val slot = SLOT_OF[key] ?: continue
            bySlot[slot] = key
        }
        return bySlot.values.toSet()
    }

    private fun fill(p: Paint, c: Int) { p.shader = null; p.style = Paint.Style.FILL; p.color = c }
    private fun line(p: Paint, c: Int, w: Float) {
        p.shader = null
        p.style = Paint.Style.STROKE; p.color = c; p.strokeWidth = w
        p.strokeCap = Paint.Cap.ROUND; p.strokeJoin = Paint.Join.ROUND
    }
    private fun al(c: Int, a: Int): Int = (a.coerceIn(0, 255) shl 24) or (c and 0xFFFFFF)
    private fun rnd(i: Int): Float { val x = sin(i * 12.9898f) * 43758.547f; return x - floor(x) }

    /** Outline thickness varies: a heavier offset stroke on the lower right, thinner upright one on top. */
    private fun outline(cv: Canvas, p: Paint, draw: Int, rx: Float, ry: Float, rad: Float, cx: Float, cy: Float, w: Float) {
        // draw: 0 path, 1 round rect (r), 2 circle
        line(p, al(INK, 0xAA), w + 0.8f)
        cv.save(); cv.translate(0.45f, 0.55f)
        when (draw) { 0 -> cv.drawPath(path, p); 1 -> cv.drawRoundRect(r, rx, ry, p); else -> cv.drawCircle(cx, cy, rad, p) }
        cv.restore()
        line(p, INK, w - 0.3f)
        when (draw) { 0 -> cv.drawPath(path, p); 1 -> cv.drawRoundRect(r, rx, ry, p); else -> cv.drawCircle(cx, cy, rad, p) }
    }

    private fun shape(cv: Canvas, p: Paint, c: Int) {
        fill(p, c); cv.drawPath(path, p)
        volPath(cv, p)
        outline(cv, p, 0, 0f, 0f, 0f, 0f, 0f, 2.2f)
    }

    private fun box(cv: Canvas, p: Paint, l: Float, t: Float, rt: Float, b: Float, rad: Float, c: Int) {
        r.set(l, t, rt, b)
        fill(p, c); cv.drawRoundRect(r, rad, rad, p)
        volRect(cv, p, l, t, rt, b, rad)
        r.set(l, t, rt, b)
        outline(cv, p, 1, rad, rad, 0f, 0f, 0f, 2.2f)
    }

    private fun ball(cv: Canvas, p: Paint, x: Float, y: Float, rad: Float, c: Int) {
        fill(p, c); cv.drawCircle(x, y, rad, p)
        volRect(cv, p, x - rad, y - rad, x + rad, y + rad, -1f)
        outline(cv, p, 2, 0f, 0f, rad, x, y, 2f)
    }

    private fun sparkle(cv: Canvas, p: Paint, cx: Float, cy: Float, s: Float, c: Int = 0xFFFFFFFF.toInt()) {
        if (s <= 0f) return
        path.reset()
        path.moveTo(cx, cy - s); path.lineTo(cx + s * 0.3f, cy - s * 0.3f)
        path.lineTo(cx + s, cy); path.lineTo(cx + s * 0.3f, cy + s * 0.3f)
        path.lineTo(cx, cy + s); path.lineTo(cx - s * 0.3f, cy + s * 0.3f)
        path.lineTo(cx - s, cy); path.lineTo(cx - s * 0.3f, cy - s * 0.3f)
        path.close()
        fill(p, c); cv.drawPath(path, p)
    }

    private fun star(cv: Canvas, p: Paint, cx: Float, cy: Float, ro: Float, rot: Float, c: Int) {
        path.reset()
        for (i in 0..9) {
            val a = rot + i * 0.62831855f - 1.5707964f
            val rr = if (i % 2 == 0) ro else ro * 0.45f
            val px = cx + cos(a) * rr
            val py = cy + sin(a) * rr
            if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
        }
        path.close()
        fill(p, c); cv.drawPath(path, p)
    }

    private fun heart(cv: Canvas, p: Paint, cx: Float, cy: Float, s: Float, c: Int) {
        path.reset()
        path.moveTo(cx, cy + s * 0.9f)
        path.cubicTo(cx - s * 1.6f, cy - s * 0.1f, cx - s * 0.7f, cy - s * 1.1f, cx, cy - s * 0.35f)
        path.cubicTo(cx + s * 0.7f, cy - s * 1.1f, cx + s * 1.6f, cy - s * 0.1f, cx, cy + s * 0.9f)
        path.close()
        fill(p, c); cv.drawPath(path, p)
    }

    /** Drawn BEFORE the head: capes and the back half of auras. */
    fun drawBack(cv: Canvas, p: Paint, items: Set<String>, mood: String, t: Float) {
        if (items.isEmpty()) return
        if ("cape" in items) { cv.save(); cv.translate(bodyX, bodyY); cape(cv, p, t); cv.restore() }
        if ("fire" in items) fireBack(cv, p, t)
        if ("ice" in items) iceBack(cv, p, t)
        if ("lightning" in items) lightning(cv, p, t)
        if ("stars" in items) starsAura(cv, p, t, true)
    }

    fun draw(cv: Canvas, p: Paint, items: Set<String>, mood: String, t: Float) {
        if (items.isEmpty()) return
        cv.save(); cv.translate(bodyX, bodyY)
        if ("scarf" in items) scarf(cv, p, t)
        if ("bowtie" in items) bowtie(cv, p)
        if ("tie" in items) tie(cv, p)
        if ("cape" in items) capeFront(cv, p)
        cv.restore()
        var hasHead = false
        for (i in 0 until HEADS.size) if (HEADS[i] in items) { hasHead = true; break }
        if (hasHead) {
            // soft contact shadow the headwear casts on the forehead
            gp.style = Paint.Style.FILL; gp.shader = SHADOW; gp.alpha = 120
            cv.save(); cv.translate(earX * 0.6f, headTop - 46f + earY * 0.6f); cv.scale(30f, 5.5f)
            cv.drawCircle(0f, 0f, 1f, gp)
            cv.restore(); gp.shader = null
        }
        cv.save(); cv.translate(earX, headTop + earY)
        if ("hat" in items) hat(cv, p)
        if ("cap" in items) cap(cv, p)
        if ("crown" in items) crown(cv, p, t)
        if ("bow" in items) bow(cv, p)
        if ("flower" in items) flower(cv, p, t)
        if ("party" in items) party(cv, p)
        if ("santa" in items) santa(cv, p)
        if ("wizard" in items) wizard(cv, p, t)
        if ("ninja" in items) ninja(cv, p, t)
        if ("samurai" in items) samurai(cv, p, t)
        if ("pirate" in items) pirate(cv, p, t)
        if ("halo" in items) halo(cv, p, t)
        if ("horns" in items) horns(cv, p)
        if ("catears" in items) catears(cv, p)
        if ("beanie" in items) beanie(cv, p)
        if ("pumpkin" in items) pumpkin(cv, p)
        cv.restore()
        cv.save(); cv.translate(bodyX, bodyY)
        if ("astronaut" in items) astronaut(cv, p, t)
        cv.restore()
        cv.save(); cv.translate(earX, earY)
        if ("headphones" in items) headphones(cv, p, mood, t)
        cv.restore()
        cv.save(); cv.translate(eyeX, eyeY)
        if ("glasses" in items) glasses(cv, p)
        if ("sunglasses" in items && mood != "cool") sunglasses(cv, p)
        if ("mask" in items) mask(cv, p)
        if ("visor" in items && mood != "cool") visor(cv, p, t)
        cv.restore()
        if (mood != "angry") {
            cv.save(); cv.translate(bodyX, bodyY)
            if ("wand" in items) hand(cv, p, t, 0)
            if ("sword" in items) hand(cv, p, t, 1)
            if ("umbrella" in items) hand(cv, p, t, 2)
            if ("lollipop" in items) hand(cv, p, t, 3)
            cv.restore()
        }
        if ("fire" in items) fireFront(cv, p, t)
        if ("ice" in items) iceFront(cv, p, t)
        if ("hearts" in items) heartsAura(cv, p, t)
        if ("sakura" in items) sakura(cv, p, t)
        if ("stars" in items) starsAura(cv, p, t, false)
    }

    // ---------------------------------------------------------------- original items

    private fun hat(cv: Canvas, p: Paint) {
        box(cv, p, -14f, -68f, 14f, -50f, 4f, 0xFF3B3355.toInt())
        fill(p, 0xFFFF5C8A.toInt()); cv.drawRect(-12.9f, -58f, 12.9f, -53f, p)
        box(cv, p, -25f, -54f, 25f, -47f, 3.5f, 0xFF3B3355.toInt())
    }

    private fun cap(cv: Canvas, p: Paint) {
        box(cv, p, 2f, -55f, 32f, -47f, 4f, 0xFF2F6BC0.toInt())
        r.set(-25f, -70f, 25f, -34f)
        path.reset(); path.arcTo(r, 180f, 180f); path.close()
        shape(cv, p, 0xFF4A8FE7.toInt())
        ball(cv, p, 0f, -70f, 3f, 0xFF2F6BC0.toInt())
    }

    private fun crown(cv: Canvas, p: Paint, t: Float) {
        path.reset()
        path.moveTo(-19f, -51f); path.lineTo(-21f, -68f); path.lineTo(-9f, -59f)
        path.lineTo(0f, -70f); path.lineTo(9f, -59f); path.lineTo(21f, -68f)
        path.lineTo(19f, -51f); path.close()
        shape(cv, p, 0xFFFFC83D.toInt())
        ball(cv, p, 0f, -57f, 3f, 0xFFE24B4A.toInt())
        sparkle(cv, p, -21f, -68f, 1.5f + 2.5f * abs(sin(t * 4f)))
        sparkle(cv, p, 21f, -68f, 1.5f + 2.5f * abs(sin(t * 4f + 2f)))
    }

    private fun bow(cv: Canvas, p: Paint) {
        val cx = 19f; val cy = -54f
        path.reset(); path.moveTo(cx, cy); path.lineTo(cx - 13f, cy - 8f); path.lineTo(cx - 13f, cy + 8f); path.close()
        shape(cv, p, 0xFFFF5C8A.toInt())
        path.reset(); path.moveTo(cx, cy); path.lineTo(cx + 13f, cy - 8f); path.lineTo(cx + 13f, cy + 8f); path.close()
        shape(cv, p, 0xFFFF5C8A.toInt())
        ball(cv, p, cx, cy, 4f, 0xFFFFC83D.toInt())
    }

    private fun headphones(cv: Canvas, p: Paint, mood: String, t: Float) {
        val k = if (mood == "dance") abs(sin(t * 12f)) * 3f else 0f
        r.set(-37f, -66f, 37f, 6f)
        line(p, INK, 7f); cv.drawArc(r, 180f, 180f, false, p)
        line(p, 0xFF7B4FD6.toInt(), 3.4f); cv.drawArc(r, 180f, 180f, false, p)
        box(cv, p, -43f, -39f - k, -33f, -20f + k, 4f, 0xFFFF5C8A.toInt())
        box(cv, p, 33f, -39f - k, 43f, -20f + k, 4f, 0xFFFF5C8A.toInt())
    }

    private fun flower(cv: Canvas, p: Paint, t: Float) {
        val cx = -21f; val cy = -54f
        val sw = sin(t * 1.5f) * 0.25f
        fill(p, 0xFFFF8FB5.toInt())
        for (i in 0..4) {
            val a = sw + i * 1.2566371f
            cv.drawCircle(cx + cos(a) * 5.5f, cy + sin(a) * 5.5f, 4.4f, p)
        }
        line(p, INK, 1.8f)
        for (i in 0..4) {
            val a = sw + i * 1.2566371f
            cv.drawCircle(cx + cos(a) * 5.5f, cy + sin(a) * 5.5f, 4.4f, p)
        }
        fill(p, 0xFFFFC83D.toInt()); cv.drawCircle(cx, cy, 3.6f, p)
        line(p, INK, 1.8f); cv.drawCircle(cx, cy, 3.6f, p)
    }

    private fun party(cv: Canvas, p: Paint) {
        path.reset(); path.moveTo(-13f, -51f); path.lineTo(13f, -51f); path.lineTo(0f, -69f); path.close()
        shape(cv, p, 0xFF7B4FD6.toInt())
        fill(p, 0xFFFFC83D.toInt())
        cv.drawCircle(-4f, -56f, 2.2f, p); cv.drawCircle(4f, -59f, 2.2f, p); cv.drawCircle(-1f, -63f, 1.8f, p)
        ball(cv, p, 0f, -69f, 3.6f, 0xFFFF5C8A.toInt())
    }

    private fun beanie(cv: Canvas, p: Paint) {
        path.reset()
        path.moveTo(-22f, -49f)
        path.cubicTo(-22f, -65f, -9f, -71f, 0f, -71f)
        path.cubicTo(9f, -71f, 22f, -65f, 22f, -49f)
        path.close()
        shape(cv, p, 0xFF3F7FD9.toInt())
        box(cv, p, -24f, -57f, 24f, -47f, 4f, 0xFF2F63B5.toInt())   // folded rim
        line(p, 0x55FFFFFF, 1.3f)
        for (i in -3..3) cv.drawLine(i * 6.2f, -55.5f, i * 6.2f, -48.5f, p)
        ball(cv, p, 0f, -72f, 4.6f, 0xFFFFFFFF.toInt())              // pompom
    }

    private fun pumpkin(cv: Canvas, p: Paint) {
        ball(cv, p, -9f, -58f, 9.5f, 0xFFF08A24.toInt())
        ball(cv, p, 9f, -58f, 9.5f, 0xFFF08A24.toInt())
        ball(cv, p, 0f, -59f, 10.5f, 0xFFF5A04A.toInt())
        box(cv, p, -2.2f, -73f, 2.6f, -65f, 1.6f, 0xFF4E8F3A.toInt())  // stem
        path.reset()                                                  // a small leaf
        path.moveTo(2.6f, -69f); path.quadTo(9f, -75f, 12f, -69f); path.quadTo(7f, -66f, 2.6f, -69f); path.close()
        shape(cv, p, 0xFF6BBF59.toInt())
    }

    private fun santa(cv: Canvas, p: Paint) {
        path.reset()
        path.moveTo(-22f, -51f)
        path.cubicTo(-22f, -66f, -6f, -70f, 8f, -66f)
        path.cubicTo(18f, -63f, 24f, -58f, 22f, -51f)
        path.close()
        shape(cv, p, 0xFFE24B4A.toInt())
        ball(cv, p, 11f, -66f, 4.4f, 0xFFFFFFFF.toInt())
        box(cv, p, -25f, -57f, 25f, -48f, 4.5f, 0xFFFFFFFF.toInt())
    }

    private fun glasses(cv: Canvas, p: Paint) {
        for (i in 0..1) {
            val x = (i * 2 - 1) * 14f
            fill(p, 0x33FFFFFF); cv.drawCircle(x, -30f, 8.5f, p)
            line(p, INK, 5f); cv.drawCircle(x, -30f, 8.5f, p)
            line(p, 0xFFFF5C8A.toInt(), 2.4f); cv.drawCircle(x, -30f, 8.5f, p)
        }
        line(p, INK, 3.4f)
        cv.drawLine(-5.5f, -31f, 5.5f, -31f, p)
        cv.drawLine(-22.5f, -32f, -32f, -34f, p); cv.drawLine(22.5f, -32f, 32f, -34f, p)
        line(p, 0xFFFF5C8A.toInt(), 1.6f)
        cv.drawLine(-5.5f, -31f, 5.5f, -31f, p)
    }

    private fun sunglasses(cv: Canvas, p: Paint) {
        for (i in 0..1) {
            val x = (i * 2 - 1) * 14f
            r.set(x - 9.5f, -36f, x + 9.5f, -24f)
            fill(p, 0xFF14102A.toInt()); cv.drawRoundRect(r, 5f, 5f, p)
            line(p, 0xFFFF5C8A.toInt(), 2.2f); cv.drawRoundRect(r, 5f, 5f, p)
            line(p, 0xBBFFFFFF.toInt(), 1.6f)
            cv.drawLine(x - 6f, -32.5f, x - 3f, -32.5f, p)
        }
        line(p, INK, 2.6f)
        cv.drawLine(-4.5f, -32f, 4.5f, -32f, p)
        cv.drawLine(-23.5f, -32f, -32f, -34f, p); cv.drawLine(23.5f, -32f, 32f, -34f, p)
    }

    private fun scarf(cv: Canvas, p: Paint, t: Float) {
        val sw = sin(t * 2f) * 1.2f
        box(cv, p, 15f + sw, -5f, 26f + sw, 8f, 3.5f, 0xFFE24B4A.toInt())
        box(cv, p, -31f, -10f, 31f, 0f, 5f, 0xFFE24B4A.toInt())
        fill(p, 0xFFFFC83D.toInt())
        cv.drawRect(-19f, -8.9f, -14f, -1.1f, p)
        cv.drawRect(-3f, -8.9f, 2f, -1.1f, p)
        cv.drawRect(13f, -8.9f, 18f, -1.1f, p)
    }

    private fun bowtie(cv: Canvas, p: Paint) {
        path.reset(); path.moveTo(0f, -4f); path.lineTo(-12f, -11f); path.lineTo(-12f, 3f); path.close()
        shape(cv, p, 0xFF4A8FE7.toInt())
        path.reset(); path.moveTo(0f, -4f); path.lineTo(12f, -11f); path.lineTo(12f, 3f); path.close()
        shape(cv, p, 0xFF4A8FE7.toInt())
        box(cv, p, -3.5f, -8f, 3.5f, 0f, 2.5f, 0xFF2F6BC0.toInt())
    }

    // ---------------------------------------------------------------- new neck items

    private fun cape(cv: Canvas, p: Paint, t: Float) {
        val f1 = sin(t * 3f) * 3f
        val f2 = sin(t * 3f + 1.3f) * 4f
        val f3 = sin(t * 4f + 0.6f) * 2f
        path.reset()
        path.moveTo(-26f, -14f); path.lineTo(26f, -14f)
        path.quadTo(44f + f1, -4f, 51f + f2, 7f)
        path.quadTo(26f, 3f + f3, 0f, 8f + f3)
        path.quadTo(-26f, 3f - f3, -51f - f2, 7f)
        path.quadTo(-44f - f1, -4f, -26f, -14f)
        path.close()
        shape(cv, p, 0xFFD83A3A.toInt())
        line(p, al(INK, 70), 1.6f)
        cv.drawLine(-14f, -12f, -20f + f3, 5f, p)
        cv.drawLine(14f, -12f, 20f - f3, 5f, p)
        cv.drawLine(0f, -10f, 0f, 6f, p)
    }

    private fun capeFront(cv: Canvas, p: Paint) {
        box(cv, p, -27f, -9f, 27f, -3f, 3f, 0xFFB02A2A.toInt())
        ball(cv, p, -22f, -6f, 3f, 0xFFFFC83D.toInt())
        ball(cv, p, 22f, -6f, 3f, 0xFFFFC83D.toInt())
    }

    private fun tie(cv: Canvas, p: Paint) {
        path.reset(); path.moveTo(0f, -5f); path.lineTo(-15f, -11f); path.lineTo(-13f, 1f); path.close()
        shape(cv, p, 0xFFFFFFFF.toInt())
        path.reset(); path.moveTo(0f, -5f); path.lineTo(15f, -11f); path.lineTo(13f, 1f); path.close()
        shape(cv, p, 0xFFFFFFFF.toInt())
        path.reset(); path.moveTo(-3.5f, -3f); path.lineTo(3.5f, -3f); path.lineTo(6.5f, 8f); path.lineTo(0f, 12f); path.lineTo(-6.5f, 8f); path.close()
        shape(cv, p, 0xFF2F6BC0.toInt())
        line(p, 0x88FFFFFF.toInt(), 1.4f)
        cv.drawLine(-3.5f, 3f, 3.5f, 0f, p); cv.drawLine(-5f, 7f, 5f, 4f, p)
        box(cv, p, -4.2f, -8f, 4.2f, -1.5f, 2.5f, 0xFF1F4E96.toInt())
    }

    // ---------------------------------------------------------------- new head items

    private fun wizard(cv: Canvas, p: Paint, t: Float) {
        val bend = sin(t * 2f) * 2.5f
        box(cv, p, -27f, -54f, 27f, -46f, 4f, 0xFF4B3FA8.toInt())
        path.reset(); path.moveTo(-18f, -50f)
        path.quadTo(-8f, -72f, 8f + bend, -96f)
        path.quadTo(11f, -72f, 18f, -50f); path.close()
        shape(cv, p, 0xFF5B4BC8.toInt())
        fill(p, 0xFFFFC83D.toInt()); cv.drawRect(-17f, -58f, 17f, -53f, p)
        val gold = 0xFFFFE066.toInt()
        sparkle(cv, p, -6f, -68f, 1.5f + 2.5f * abs(sin(t * 3f)), gold)
        sparkle(cv, p, 5f, -79f, 1.5f + 2.5f * abs(sin(t * 3f + 2f)), gold)
        sparkle(cv, p, 3f, -62f, 1f + 2f * abs(sin(t * 3f + 4f)), gold)
    }

    private fun ninja(cv: Canvas, p: Paint, t: Float) {
        // tails fluttering behind on the right
        for (k in 0..1) {
            val ph = t * (8f + k * 1.5f) + k * 1.7f
            val ex = 53f + k * 3f + sin(ph) * 2.5f
            val ey = -43f + k * 6f + sin(ph + 1f) * 4f
            path.reset(); path.moveTo(33f, -50f + k * 3f)
            path.quadTo(43f, -49f + sin(ph) * 3f + k * 3f, ex, ey)
            path.lineTo(ex - 2f, ey + 5.5f)
            path.quadTo(43f, -42f + k * 3f, 33f, -44f + k * 3f); path.close()
            shape(cv, p, 0xFF2A2F4A.toInt())
        }
        box(cv, p, -37f, -51f, 37f, -43f, 3f, 0xFF2A2F4A.toInt())
        box(cv, p, -10f, -52f, 10f, -42f, 3f, 0xFFC9D2E0.toInt())
        line(p, INK, 1.6f)
        path.reset(); path.moveTo(-5f, -46f); path.quadTo(0f, -51f, 5f, -46f); path.quadTo(0f, -43f, -2f, -46f)
        cv.drawPath(path, p)
        fill(p, 0x99FFFFFF.toInt()); cv.drawRect(-8f, -50.5f, -5f, -49.5f, p)
    }

    private fun samurai(cv: Canvas, p: Paint, t: Float) {
        for (i in 0..1) {
            val sd = (i * 2 - 1).toFloat()
            box(cv, p, if (sd < 0) -40f else 29f, -50f, if (sd < 0) -29f else 40f, -36f, 3f, 0xFF6E1A1A.toInt())
        }
        path.reset(); path.moveTo(-29f, -47f)
        path.quadTo(-30f, -70f, 0f, -70f); path.quadTo(30f, -70f, 29f, -47f); path.close()
        shape(cv, p, 0xFF8E2323.toInt())
        box(cv, p, -31f, -53f, 31f, -46f, 3f, 0xFFFFC83D.toInt())
        for (i in 0..1) {
            val sd = (i * 2 - 1).toFloat()
            path.reset(); path.moveTo(sd * 2f, -69f)
            path.quadTo(sd * 14f, -74f, sd * 21f, -90f)
            path.quadTo(sd * 9f, -78f, sd * 2f, -76f); path.close()
            shape(cv, p, 0xFFFFC83D.toInt())
        }
        val g = sin(t * 2.5f)
        if (g > 0.5f) sparkle(cv, p, -21f, -90f, (g - 0.5f) * 8f)
    }

    private fun pirate(cv: Canvas, p: Paint, t: Float) {
        val w = sin(t * 4f) * 3f
        path.reset(); path.moveTo(-34f, -47f)
        path.quadTo(-28f, -72f, 0f, -64f); path.quadTo(28f, -72f, 34f, -47f)
        path.quadTo(0f, -56f, -34f, -47f); path.close()
        shape(cv, p, 0xFF1F1B2E.toInt())
        line(p, 0xFFFFC83D.toInt(), 1.8f)
        path.reset(); path.moveTo(-31f, -49f); path.quadTo(0f, -58f, 31f, -49f); cv.drawPath(path, p)
        fill(p, 0xFFFFFFFF.toInt()); cv.drawCircle(0f, -57f, 3.4f, p)
        fill(p, INK); cv.drawCircle(-1.3f, -57.5f, 0.8f, p); cv.drawCircle(1.3f, -57.5f, 0.8f, p)
        line(p, INK, 1.2f); cv.drawLine(-2f, -53.5f, 2f, -53.5f, p)
        line(p, INK, 5.6f)
        path.reset(); path.moveTo(22f, -58f); path.quadTo(34f + w, -74f, 43f + w * 1.5f, -64f + w)
        cv.drawPath(path, p)
        line(p, 0xFFE24B4A.toInt(), 3.4f); cv.drawPath(path, p)
    }

    private fun halo(cv: Canvas, p: Paint, t: Float) {
        val y = -63f + sin(t * 2.5f) * 2.5f
        val pulse = 0.5f + 0.5f * sin(t * 4f)
        glowDisc(cv, G_HALO, 0f, y, 28f, 14f, (110 + 80 * pulse).toInt())
        r.set(-15f, y - 5f, 15f, y + 5f)
        line(p, al(0xFFE066, 40 + (50 * pulse).toInt()), 9f); cv.drawOval(r, p)
        line(p, 0xFFFFD84D.toInt(), 4.2f); cv.drawOval(r, p)
        line(p, 0xAAFFFFFF.toInt(), 1.4f)
        r.set(-13.5f, y - 4f, 13.5f, y + 4f); cv.drawArc(r, 200f, 70f, false, p)
    }

    private fun horns(cv: Canvas, p: Paint) {
        for (i in 0..1) {
            val sd = (i * 2 - 1).toFloat()
            path.reset(); path.moveTo(sd * 25f, -46f)
            path.quadTo(sd * 31f, -58f, sd * 20f, -69f)
            path.quadTo(sd * 17f, -58f, sd * 13f, -49f); path.close()
            shape(cv, p, 0xFFD83A3A.toInt())
            fill(p, 0x66FFFFFF); cv.drawCircle(sd * 24f, -56f, 1.4f, p)
        }
    }

    private fun catears(cv: Canvas, p: Paint) {
        line(p, INK, 5.4f)
        r.set(-30f, -62f, 30f, -30f); cv.drawArc(r, 205f, 130f, false, p)
        line(p, 0xFFFF5C8A.toInt(), 3f); cv.drawArc(r, 205f, 130f, false, p)
        for (i in 0..1) {
            val sd = (i * 2 - 1).toFloat()
            path.reset(); path.moveTo(sd * 31f, -47f); path.lineTo(sd * 27f, -72f); path.lineTo(sd * 10f, -55f); path.close()
            shape(cv, p, 0xFFFFE6EE.toInt())
            path.reset(); path.moveTo(sd * 27f, -51f); path.lineTo(sd * 25.5f, -64f); path.lineTo(sd * 15f, -55f); path.close()
            fill(p, 0xFFF4A3B8.toInt()); cv.drawPath(path, p)
        }
    }

    private fun astronaut(cv: Canvas, p: Paint, t: Float) {
        cv.save(); cv.translate(0f, headTop)
        line(p, INK, 1.8f)
        cv.drawLine(24f, -63f, 31f, -78f, p)
        ball(cv, p, 31f, -79f, 2.8f, 0xFFE24B4A.toInt())
        cv.restore()
        fill(p, 0x2A9FD8FF); cv.drawCircle(0f, -28f, 43f, p)
        path.reset(); path.addCircle(0f, -28f, 41f, Path.Direction.CW)
        cv.save(); cv.clipPath(path)
        val k = -40f + (t * 22f) % 90f
        line(p, 0x55FFFFFF, 5f); cv.drawLine(k, -66f, k - 14f, 10f, p)
        line(p, 0x77FFFFFF, 2f); cv.drawLine(k + 8f, -66f, k - 6f, 10f, p)
        cv.restore()
        line(p, INK, 6.6f); cv.drawCircle(0f, -28f, 43f, p)
        line(p, 0xFFE8EEF5.toInt(), 4.2f); cv.drawCircle(0f, -28f, 43f, p)
        line(p, 0xBBFFFFFF.toInt(), 1.8f)
        r.set(-37f, -62f, 37f, -8f); cv.drawArc(r, 205f, 50f, false, p)
        box(cv, p, -29f, -4f, 29f, 4f, 3.5f, 0xFFD6DCE6.toInt())
        fill(p, 0xFFFF5C8A.toInt()); cv.drawCircle(-18f, 0f, 1.7f, p); cv.drawCircle(18f, 0f, 1.7f, p)
    }

    // ---------------------------------------------------------------- new eye items

    private fun mask(cv: Canvas, p: Paint) {
        path.reset(); path.fillType = Path.FillType.EVEN_ODD
        r.set(-29f, -41f, 29f, -21f); path.addRoundRect(r, 9f, 9f, Path.Direction.CW)
        for (i in 0..1) {
            val x = (i * 2 - 1) * 14f
            r.set(x - 7f, -36f, x + 7f, -24f); path.addOval(r, Path.Direction.CW)
        }
        fill(p, 0xFF2B3FAE.toInt()); cv.drawPath(path, p)
        line(p, INK, 2f); cv.drawPath(path, p)
        path.fillType = Path.FillType.WINDING
        line(p, 0x77FFFFFF, 1.5f); cv.drawLine(-25f, -38f, -18f, -39.5f, p)
    }

    private fun visor(cv: Canvas, p: Paint, t: Float) {
        r.set(-31f, -38f, 31f, -23f)
        fill(p, 0xAA0F2A4A.toInt()); cv.drawRoundRect(r, 7f, 7f, p)
        line(p, 0xFF39E6FF.toInt(), 2f); cv.drawRoundRect(r, 7f, 7f, p)
        val sx = -26f + (t * 24f) % 52f
        line(p, 0xC87FF6FF.toInt(), 2.2f); cv.drawLine(sx, -35f, sx, -26f, p)
        line(p, 0x96FFFFFF.toInt(), 1.4f); cv.drawLine(-24f, -35f, -15f, -35f, p)
        line(p, INK, 2.6f)
        cv.drawLine(-31f, -31f, -37f, -33f, p); cv.drawLine(31f, -31f, 37f, -33f, p)
    }

    // ---------------------------------------------------------------- hand items

    private fun hand(cv: Canvas, p: Paint, t: Float, kind: Int) {
        cv.save()
        cv.translate(42f, -12f)
        cv.rotate(10f + sin(t * 2f) * 4f)
        when (kind) {
            0 -> {
                line(p, INK, 5f); cv.drawLine(0f, 0f, 13f, -27f, p)
                line(p, 0xFF7B4FD6.toInt(), 2.8f); cv.drawLine(0f, 0f, 13f, -27f, p)
                star(cv, p, 13f, -28f, 6.5f, t * 1.5f, 0xFFFFC83D.toInt())
                line(p, INK, 1.3f); cv.drawPath(path, p)
                for (i in 0..2) {
                    val ph = (t * 1.2f + i * 0.33f) % 1f
                    sparkle(cv, p, 13f + (rnd(i + 3) - 0.5f) * 16f, -30f - ph * 16f, 2.8f * (1f - ph), 0xFFFFF3A0.toInt())
                }
            }
            1 -> {
                box(cv, p, -2f, -9f, 2f, 5f, 1.5f, 0xFF7A4A2A.toInt())
                box(cv, p, -7f, -12f, 7f, -8.5f, 1.8f, 0xFFFFC83D.toInt())
                path.reset(); path.moveTo(-3f, -12f); path.lineTo(-3f, -41f); path.lineTo(0f, -47f)
                path.lineTo(3f, -41f); path.lineTo(3f, -12f); path.close()
                shape(cv, p, 0xFFDDE6F0.toInt())
                line(p, 0xAAFFFFFF.toInt(), 1.2f); cv.drawLine(-0.6f, -14f, -0.6f, -40f, p)
                val gy = -16f - (t * 22f) % 26f
                sparkle(cv, p, 0f, gy, 3.2f * abs(sin(t * 3f)) + 0.6f)
            }
            2 -> {
                cv.rotate(8f)
                line(p, INK, 3.6f); cv.drawLine(0f, 2f, 0f, -34f, p)
                line(p, 0xFF9AA3B5.toInt(), 1.6f); cv.drawLine(0f, 2f, 0f, -34f, p)
                r.set(-19f, -56f, 19f, -22f)
                path.reset(); path.arcTo(r, 180f, 180f); path.quadTo(10f, -34f, 0f, -38f)
                path.quadTo(-10f, -34f, -19f, -39f); path.close()
                shape(cv, p, 0xFFFF5C8A.toInt())
                line(p, 0xCCFFFFFF.toInt(), 1.8f)
                path.reset(); path.moveTo(0f, -56f); path.quadTo(-11f, -50f, -9.5f, -38.5f)
                path.moveTo(0f, -56f); path.quadTo(11f, -50f, 9.5f, -38.5f); cv.drawPath(path, p)
            }
            else -> {
                line(p, INK, 4f); cv.drawLine(0f, 0f, 0f, -18f, p)
                line(p, 0xFFFFFFFF.toInt(), 2f); cv.drawLine(0f, 0f, 0f, -18f, p)
                ball(cv, p, 0f, -25f, 9f, 0xFFFF7AA8.toInt())
                path.reset()
                for (i in 0..16) {
                    val a = t * 1.2f + i * 0.75f
                    val rr = 7.4f * i / 16f
                    val px = cos(a) * rr; val py = -25f + sin(a) * rr
                    if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
                }
                line(p, 0xFFFFFFFF.toInt(), 1.8f); cv.drawPath(path, p)
                fill(p, 0xAAFFFFFF.toInt()); cv.drawCircle(-4f, -29f, 1.6f, p)
            }
        }
        ball(cv, p, 0f, 0f, 4.6f, 0xFFFFE6C8.toInt())
        cv.restore()
    }

    // ---------------------------------------------------------------- auras

    private fun flame(cv: Canvas, p: Paint, x: Float, by: Float, h: Float, w: Float, sw: Float, c: Int) {
        path.reset()
        path.moveTo(x - w, by)
        path.quadTo(x - w * 0.9f, by - h * 0.55f, x + sw, by - h)
        path.quadTo(x + w * 0.9f, by - h * 0.55f, x + w, by)
        path.close()
        fill(p, c); cv.drawPath(path, p)
    }

    private fun fireBack(cv: Canvas, p: Paint, t: Float) {
        val fk = 0.8f + 0.2f * sin(t * 7f)
        glowDisc(cv, G_FIRE, 0f, -26f, 62f, 56f, (200 * fk).toInt())
        glowDisc(cv, G_FIRE, 0f, -20f, 42f, 38f, (110 * fk).toInt())
        for (i in 0..10) {
            val a = 3.1415927f + i * 0.31415927f
            val cx = cos(a) * 40f
            val cy = -22f + sin(a) * 38f
            val fl = 0.75f + 0.25f * sin(t * 9f + i * 1.7f) + 0.15f * sin(t * 15f + i * 3.1f)
            val h = (17f + 9f * rnd(i)) * fl
            val sw = sin(t * 6f + i) * 2.5f + cx * 0.05f
            flame(cv, p, cx, cy, h * 1.05f, 7.5f, sw, 0xCCE24B4A.toInt())
            flame(cv, p, cx, cy, h * 0.78f, 5.6f, sw * 0.8f, 0xDDFF8A1F.toInt())
            flame(cv, p, cx, cy, h * 0.5f, 3.4f, sw * 0.6f, 0xEEFFE066.toInt())
        }
    }

    private fun fireFront(cv: Canvas, p: Paint, t: Float) {
        for (i in 0..4) {
            val x = -30f + i * 15f
            val fl = 0.75f + 0.25f * sin(t * 11f + i * 2.3f) + 0.15f * sin(t * 17f + i)
            val h = (10f + 5f * rnd(i + 20)) * fl
            val sw = sin(t * 7f + i * 1.3f) * 2f
            flame(cv, p, x, 2f, h, 6f, sw, 0xCCE24B4A.toInt())
            flame(cv, p, x, 2f, h * 0.72f, 4.4f, sw * 0.8f, 0xDDFF8A1F.toInt())
            flame(cv, p, x, 2f, h * 0.45f, 2.6f, sw * 0.6f, 0xEEFFE066.toInt())
        }
        for (i in 0..7) {
            val ph = (t * 0.5f + rnd(i * 3 + 1)) % 1f
            val x = (rnd(i * 5 + 2) - 0.5f) * 76f + sin(t * 3f + i) * 4f
            val y = -4f - ph * 78f
            val a = ((1f - ph) * 255f).toInt()
            fill(p, al(if (i % 2 == 0) 0xFF9A2B else 0xFFE066, a))
            cv.drawCircle(x, y, 1.2f + 1.2f * rnd(i + 9) * (1f - ph * 0.5f), p)
        }
    }

    private fun crystal(cv: Canvas, p: Paint, g: Float) {
        path.reset()
        path.moveTo(0f, -9f * g); path.lineTo(3.4f * g, 0f); path.lineTo(0f, 5f * g); path.lineTo(-3.4f * g, 0f); path.close()
        fill(p, 0xCCBFEFFF.toInt()); cv.drawPath(path, p)
        line(p, 0xCCFFFFFF.toInt(), 1.2f); cv.drawPath(path, p)
        line(p, 0xAA6CB4F5.toInt(), 1f); cv.drawLine(0f, -8f * g, 0f, 4f * g, p)
    }

    private fun iceBack(cv: Canvas, p: Paint, t: Float) {
        val sh = 0.5f + 0.5f * sin(t * 2f)
        glowDisc(cv, G_ICE, 0f, -28f, 66f, 60f, 130 + (70 * sh).toInt())
        fill(p, al(0x7FC8FF, 24 + (22 * sh).toInt())); cv.drawCircle(0f, -28f, 54f, p)
        fill(p, al(0xBFEFFF, 20 + (20 * (1f - sh)).toInt())); cv.drawCircle(0f, -28f, 46f, p)
        for (i in 0..9) {
            val a = i * 0.62831855f + sin(t * 0.6f) * 0.08f
            val g = 0.8f + 0.25f * sin(t * 2.2f + i * 1.3f)
            cv.save()
            cv.translate(cos(a) * 47f, -28f + sin(a) * 43f)
            cv.rotate(a * 57.29578f + 90f)
            crystal(cv, p, g)
            cv.restore()
        }
    }

    private fun iceFront(cv: Canvas, p: Paint, t: Float) {
        line(p, 0xEEFFFFFF.toInt(), 1f)
        for (i in 0..6) {
            val ph = (t * 0.17f + rnd(i + 40)) % 1f
            val x = (rnd(i + 50) - 0.5f) * 84f + sin(t * 1.5f + i) * 5f
            val y = -92f + ph * 98f
            val sz = 2f + rnd(i + 60) * 1.6f
            for (k in 0..2) {
                val a = k * 1.0471976f + t * 0.8f + i
                val cx = cos(a) * sz; val sy = sin(a) * sz
                cv.drawLine(x - cx, y - sy, x + cx, y + sy, p)
            }
        }
        fill(p, 0x55BFEFFF); cv.drawCircle(-24f, -17f, 6f, p); cv.drawCircle(24f, -17f, 6f, p)
        sparkle(cv, p, -30f, -52f, 3f * maxOf(0f, sin(t * 3f)))
        sparkle(cv, p, 35f, -40f, 3f * maxOf(0f, sin(t * 3f + 2f)))
        sparkle(cv, p, -37f, -12f, 3f * maxOf(0f, sin(t * 3f + 4f)))
        sparkle(cv, p, -24f, -19f, 1.8f * maxOf(0f, sin(t * 4f + 1f)))
        sparkle(cv, p, 24f, -19f, 1.8f * maxOf(0f, sin(t * 4f + 3f)))
    }

    private fun lightning(cv: Canvas, p: Paint, t: Float) {
        val q = (t * 9f).toInt()
        val fl = if (rnd(q) > 0.5f) 0x22 else 0x0C
        glowDisc(cv, G_BOLT, 0f, -28f, 64f, 58f, if (fl > 0x20) 230 else 90)
        fill(p, al(0xFFE066, fl)); cv.drawCircle(0f, -28f, 52f, p)
        for (i in 0..3) {
            val seed = q * 7 + i * 31
            if (rnd(seed) < 0.35f) continue
            val a = i * 1.5707964f + 0.6f + rnd(seed + 1) * 0.5f
            val ca = cos(a); val sa = sin(a)
            path.reset()
            path.moveTo(ca * 30f, -28f + sa * 28f)
            for (k in 1..4) {
                val rr = 30f + k * 7f
                val j = (rnd(seed + k * 3) - 0.5f) * 11f
                path.lineTo(ca * rr - sa * j, -28f + sa * rr * 0.95f + ca * j)
            }
            line(p, al(0xFFC83D, 170), 4.2f); cv.drawPath(path, p)
            line(p, 0xFFFFFFFF.toInt(), 1.5f); cv.drawPath(path, p)
        }
    }

    private fun heartsAura(cv: Canvas, p: Paint, t: Float) {
        glowDisc(cv, G_HEART, 0f, -30f, 58f, 54f, (50 + 30 * sin(t * 2.4f)).toInt())
        for (i in 0..6) {
            val ph = (t * 0.33f + i * 0.1428f) % 1f
            val x = -40f + i * 13.3f + sin(t * 2f + i * 1.9f) * 5f
            val y = 2f - ph * 88f
            val a = (sin(ph * 3.1415927f) * 230f).toInt()
            heart(cv, p, x, y, 2.8f + rnd(i + 5) * 2.4f, al(if (i % 2 == 0) 0xFF5C8A else 0xFF9DBB, a))
        }
    }

    private fun sakura(cv: Canvas, p: Paint, t: Float) {
        glowDisc(cv, G_SAKURA, 0f, -30f, 60f, 56f, (40 + 22 * sin(t * 1.8f)).toInt())
        for (i in 0..11) {
            val ph = (t * 0.2f + rnd(i) + i * 0.083f) % 1f
            val x = -62f + ph * 124f + sin(t * 2.2f + i) * 5f
            val y = -96f + ph * 100f + sin(t * 1.7f + i * 2f) * 3f
            val a = (sin(ph * 3.1415927f) * 255f).toInt()
            cv.save(); cv.translate(x, y); cv.rotate(t * 70f + i * 37f)
            r.set(-3.4f, -1.9f, 3.4f, 1.9f)
            fill(p, al(0xFFB7D0, a)); cv.drawOval(r, p)
            fill(p, al(0xFF8FB5, a)); cv.drawCircle(-2.8f, 0f, 1f, p)
            cv.restore()
        }
    }

    private fun starsAura(cv: Canvas, p: Paint, t: Float, back: Boolean) {
        if (back) glowDisc(cv, G_STAR, 0f, -28f, 60f, 54f, (70 + 30 * sin(t * 3f)).toInt())
        for (i in 0..4) {
            val a = t * 1.5f + i * 1.2566371f
            val sn = sin(a)
            if ((sn < 0f) != back) continue
            val x = cos(a) * 46f
            val y = -28f + sn * 20f
            val sz = 3.6f + 1.6f * sin(t * 6f + i * 2f)
            star(cv, p, x, y, sz, t * 2f + i, 0xFFFFC83D.toInt())
            sparkle(cv, p, x, y, sz * 0.45f)
        }
    }
}
