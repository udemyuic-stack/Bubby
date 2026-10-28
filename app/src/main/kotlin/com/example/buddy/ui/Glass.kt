package com.example.buddy.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.drawable.Drawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.sign

/**
 * "Soft glass": translucent white fills with a thin white rim over a cool blue background. Real backdrop blur is not
 * available to plain Views, so a glass surface is a gradient fill + rim + a soft shadow, which reads the same over the
 * smooth background drawn by [GlassBackground].
 */
object Glass {
    fun white(a: Float) = Color.argb((a * 255).toInt().coerceIn(0, 255), 255, 255, 255)

    var TEXT = 0xFF1C2B45.toInt()
    var TEXT2 = 0xFF4A5C7D.toInt()
    var TEXT_LABEL = 0xFF3F5578.toInt()
    var ACCENT = 0xFFE08A4A.toInt()
    var ACCENT2 = 0xFF7A6BD0.toInt()
    var DATE = 0xFFB8672F.toInt()
    var OK = 0xFF2F9D73.toInt()
    var NAVY_A = 0xFF34466B.toInt()
    var NAVY_B = 0xFF1C2B45.toInt()
    const val OK_GLOW = 0xFF7AE8BD.toInt()
}

/** One look: the default blue-grey glass, a green one with a butterfly, a blue one with a whale. */
class ThemeDef(
    val id: String, val bgTop: Int, val bgMid: Int, val bgBottom: Int, val glowBottom: Int, val leftLight: Int,
    val ink: Int, val ink2: Int, val accent: Int, val accent2: Int, val date: Int, val dark: Int, val glow: Int, val glow2: Int, val sh: Int,
    val eye: Int, val rim: Int, val tiles: List<IntArray>, val navyA: Int, val navyB: Int, val amberA: Int, val amberB: Int,
    val mascot: String, val ok: Int, val homeTop: Int = bgTop
)

object Theme {
    val GLASS = ThemeDef("glass", 0xFFE3EBF4.toInt(), 0xFFCBD8E6.toInt(), 0xFFAEBFD5.toInt(), 0xFF7F9BBD.toInt(), 0xFFF3F6FA.toInt(),
        0xFF1C2B45.toInt(), 0xFF4A5C7D.toInt(), 0xFFE08A4A.toInt(), 0xFF7A6BD0.toInt(), 0xFFB8672F.toInt(), 0xFF341E1A.toInt(), 0xFF7F9BBD.toInt(), 0xFFE2A0B0.toInt(), 0xFF466496.toInt(),
        0xFFFFFFFF.toInt(), 0xFFFFFFFF.toInt(),
        listOf(intArrayOf(0xFF8D7EE0.toInt(), 0xFF5B4BB0.toInt()), intArrayOf(0xFF58B9A2.toInt(), 0xFF3A8C78.toInt()), intArrayOf(0xFF6FA0EE.toInt(), 0xFF4A74C4.toInt()), intArrayOf(0xFFE48AA8.toInt(), 0xFFBD5F80.toInt())),
        0xFF34466B.toInt(), 0xFF1C2B45.toInt(), 0x80E08A4A.toInt(), 0x66965A3C.toInt(), "mochi", 0xFF2F9D73.toInt(), 0xFFDDE6F0.toInt())
    val GREEN = ThemeDef("green", 0xFFE2F4D6.toInt(), 0xFFB6E58F.toInt(), 0xFF7FCF45.toInt(), 0xFF6FCF3A.toInt(), 0xFFF6FDF0.toInt(),
        0xFF123D1A.toInt(), 0xFF3F6E48.toInt(), 0xFF2FA83A.toInt(), 0xFF7BC62D.toInt(), 0xFF3F6E48.toInt(), 0xFF082C0E.toInt(), 0xFF78D746.toInt(), 0xFFBEEB3C.toInt(), 0xFF1E6428.toInt(),
        0xFFEAFF7A.toInt(), 0xFFC4F76E.toInt(),
        listOf(intArrayOf(0xFF6FD13A.toInt(), 0xFF2F9A1C.toInt()), intArrayOf(0xFF3FCF8A.toInt(), 0xFF168A52.toInt()), intArrayOf(0xFFB6E03A.toInt(), 0xFF79A812.toInt()), intArrayOf(0xFF5FD6B0.toInt(), 0xFF22987A.toInt())),
        0xFF2F6B3A.toInt(), 0xFF123D1A.toInt(), 0xB3C8F58A.toInt(), 0x99A0E05A.toInt(), "butterfly", 0xFF2FA83A.toInt())
    val BLUE = ThemeDef("blue", 0xFFE3EEFF.toInt(), 0xFFA9C6FF.toInt(), 0xFF6A96F2.toInt(), 0xFF4F86F0.toInt(), 0xFFF0F6FF.toInt(),
        0xFF0F2A6B.toInt(), 0xFF3A5AA8.toInt(), 0xFF2F66E0.toInt(), 0xFF7A8CF0.toInt(), 0xFF3A5AA8.toInt(), 0xFF06165A.toInt(), 0xFF468CFF.toInt(), 0xFF9682F0.toInt(), 0xFF1E46AA.toInt(),
        0xFFD2E8FF.toInt(), 0xFF9CCAFF.toInt(),
        listOf(intArrayOf(0xFF5AA8FF.toInt(), 0xFF2A5FD8.toInt()), intArrayOf(0xFF8D86F0.toInt(), 0xFF4C42C4.toInt()), intArrayOf(0xFF45C8F0.toInt(), 0xFF1A7FD0.toInt()), intArrayOf(0xFFA9A4F4.toInt(), 0xFF6A5CDC.toInt())),
        0xFF2D4FB0.toInt(), 0xFF0F2A6B.toInt(), 0xB3A8BEFA.toInt(), 0x998CA0F0.toInt(), "whale", 0xFF2F66E0.toInt())
    val ALL = listOf(GLASS, GREEN, BLUE)

    var cur: ThemeDef = GLASS
        private set

    /** Tile gradient [i] of the theme; the default theme keeps the colours passed in. */
    fun tile(i: Int, a: Int, b: Int): IntArray = if (cur.id == "glass") intArrayOf(a, b) else cur.tiles[i % cur.tiles.size]

    fun byId(id: String) = ALL.firstOrNull { it.id == id } ?: GLASS

    fun apply(ctx: Context) {
        cur = byId(Backend.str(ctx, "theme", "glass"))
        val t = cur
        Glass.TEXT = t.ink; Glass.TEXT2 = t.ink2; Glass.TEXT_LABEL = if (t.id == "glass") 0xFF3F5578.toInt() else t.ink2
        Glass.ACCENT = t.accent; Glass.ACCENT2 = t.accent2; Glass.DATE = t.date
        Glass.OK = t.ok; Glass.NAVY_A = t.navyA; Glass.NAVY_B = t.navyB
    }

    fun rgb(c: Int, a: Int) = Color.argb(a, Color.red(c), Color.green(c), Color.blue(c))

    /** A slightly lighter step of the dark patch colour, for the soft edge. */
    fun mixDark(c: Int) = Color.rgb(minOf(255, Color.red(c) + 26), minOf(255, Color.green(c) + 18), minOf(255, Color.blue(c) + 16))
}

enum class GlassKind { Base, Dim, Mid, Active, Amber, Navy, Nav }

/** A rounded glass surface. [radii] is 4 pairs (x,y) like Path.addRoundRect; null = uniform [radius]. */
class GlassDrawable(
    private val kind: GlassKind = GlassKind.Base,
    private val radius: Float = 0f,
    private val radii: FloatArray? = null,
    private val shadow: Boolean = true,
    private val density: Float = 1f
) : Drawable() {
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rim = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val shade = Paint(Paint.ANTI_ALIAS_FLAG)
    private val glow = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()
    private var alphaV = 255

    override fun onBoundsChange(b: android.graphics.Rect) { rebuild() }

    private fun rebuild() {
        val b = bounds
        if (b.width() <= 0 || b.height() <= 0) return
        val inset = density * 0.5f
        val r = RectF(b.left + inset, b.top + inset, b.right - inset, b.bottom - inset)
        path.reset()
        if (radii != null) {
            val w = r.width(); val h = r.height()
            // radii given as fractions of width/height (like CSS percentages)
            val rr = FloatArray(8) { i -> radii[i] * (if (i % 2 == 0) w else h) }
            path.addRoundRect(r, rr, Path.Direction.CW)
        } else {
            val rad = minOf(radius * density, minOf(r.width(), r.height()) / 2f)
            path.addRoundRect(r, rad, rad, Path.Direction.CW)
        }
        val w = r.width(); val h = r.height()
        // 145deg gradient: top-left -> bottom-right
        val x0 = r.left; val y0 = r.top; val x1 = r.right; val y1 = r.bottom
        when (kind) {
            GlassKind.Base -> {
                fill.shader = LinearGradient(x0, y0, x1, y1, Glass.white(.62f), Glass.white(.22f), Shader.TileMode.CLAMP)
                rim.color = Glass.white(.7f)
            }
            GlassKind.Nav -> {
                val a = Theme.cur.bgMid
                fill.shader = LinearGradient(x0, y0, x1, y1, Color.argb(235, 255 - (255 - Color.red(a)) / 4, 255 - (255 - Color.green(a)) / 4, 255 - (255 - Color.blue(a)) / 4), Color.argb(214, 255 - (255 - Color.red(a)) / 2, 255 - (255 - Color.green(a)) / 2, 255 - (255 - Color.blue(a)) / 2), Shader.TileMode.CLAMP)
                rim.color = Glass.white(.85f)
            }
            GlassKind.Dim -> {
                fill.shader = LinearGradient(x0 + w * .2f, y0, x1 - w * .2f, y1, Glass.white(.34f), Color.argb(46, 150, 175, 205), Shader.TileMode.CLAMP)
                rim.color = Glass.white(.55f)
            }
            GlassKind.Mid -> {
                fill.shader = LinearGradient(x0 + w * .2f, y0, x1 - w * .2f, y1, Glass.white(.20f), Glass.white(.06f), Shader.TileMode.CLAMP)
                rim.color = Glass.white(.35f)
            }
            GlassKind.Active -> {
                val cx = r.left + w * .6f; val cy = r.top + h * .7f
                fill.shader = RadialGradient(cx, cy, maxOf(w, h) * .75f, intArrayOf(Glass.white(.98f), Glass.white(.55f), Glass.white(.55f)), floatArrayOf(0f, .7f, 1f), Shader.TileMode.CLAMP)
                rim.color = Color.WHITE
            }
            GlassKind.Amber -> {
                fill.shader = LinearGradient(x0, y0, x1, y1, Theme.cur.amberA, Theme.cur.amberB, Shader.TileMode.CLAMP)
                rim.color = Glass.white(.55f)
            }
            GlassKind.Navy -> {
                fill.shader = LinearGradient(x0, y0, x1, y1, Glass.NAVY_A, Glass.NAVY_B, Shader.TileMode.CLAMP)
                rim.color = Glass.white(.18f)
            }
        }
        rim.strokeWidth = maxOf(1f, density)
        shade.color = when (kind) {
            GlassKind.Active -> Theme.rgb(Theme.cur.sh, 46)
            GlassKind.Mid -> Color.argb(0, 0, 0, 0)
            GlassKind.Navy -> Theme.rgb(Theme.cur.navyB, 52)
            else -> Theme.rgb(Theme.cur.sh, 30)
        }
        // inner glow
        val glowA = when (kind) { GlassKind.Active -> .8f; GlassKind.Dim -> .25f; GlassKind.Mid -> .12f; GlassKind.Base -> .18f; else -> 0f }
        glow.shader = if (glowA > 0f) RadialGradient(r.centerX(), r.centerY(), maxOf(w, h) * .62f,
            intArrayOf(Glass.white(0f), Glass.white(0f), Glass.white(glowA)), floatArrayOf(0f, .55f, 1f), Shader.TileMode.CLAMP) else null
    }

    override fun draw(c: Canvas) {
        if (bounds.isEmpty) return
        if (shadow && kind != GlassKind.Mid) {
            for (i in 3 downTo 1) {
                c.save()
                c.translate(0f, density * (2f + i * 2.2f))
                val s = 1f - i * 0.012f
                c.scale(s, s, bounds.exactCenterX(), bounds.exactCenterY())
                shade.alpha = (Color.alpha(shade.color) / (i + 1)).coerceIn(0, 255)
                c.drawPath(path, shade)
                c.restore()
            }
        }
        fill.alpha = alphaV
        c.drawPath(path, fill)
        if (glow.shader != null) c.drawPath(path, glow)
        c.drawPath(path, rim)
    }

    override fun setAlpha(a: Int) { alphaV = a }
    override fun setColorFilter(cf: android.graphics.ColorFilter?) { fill.colorFilter = cf }
    @Suppress("OVERRIDE_DEPRECATION")
    override fun getOpacity() = PixelFormat.TRANSLUCENT
}

/** Petal radii (CSS: 50% 50% 46% 46% / 64% 64% 36% 36%) as x,y fractions per corner. */
val PETAL_RADII = floatArrayOf(.50f, .64f, .50f, .64f, .46f, .36f, .46f, .36f)
val PETAL_RADII_NAV = floatArrayOf(.46f, .60f, .46f, .60f, .44f, .40f, .44f, .40f)
val PETAL_RADII_DAY = floatArrayOf(.50f, .58f, .50f, .58f, .46f, .42f, .46f, .42f)

fun Context.glass(kind: GlassKind = GlassKind.Base, radius: Float = 22f, shadow: Boolean = true): Drawable =
    GlassDrawable(kind, radius, null, shadow, resources.displayMetrics.density)

fun Context.petalShape(kind: GlassKind, radii: FloatArray = PETAL_RADII, shadow: Boolean = true): Drawable =
    GlassDrawable(kind, 0f, radii, shadow, resources.displayMetrics.density)

/** The page background: cool blue gradient with soft light blobs. Home also has the dark brown patch under the mascot. */
class GlassBackground(c: Context, private val home: Boolean) : View(c) {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)

    private fun blob(cv: Canvas, cx: Float, cy: Float, rx: Float, ry: Float, stops: IntArray, pos: FloatArray?, rot: Float = 0f) {
        cv.save()
        cv.translate(cx, cy)
        if (rot != 0f) cv.rotate(rot)
        cv.scale(1f, ry / rx)
        p.shader = RadialGradient(0f, 0f, rx, stops, pos, Shader.TileMode.CLAMP)
        cv.drawCircle(0f, 0f, rx, p)
        cv.restore()
        p.shader = null
    }

    override fun onDraw(cv: Canvas) {
        val w = width.toFloat(); val h = height.toFloat()
        if (w <= 0f) return
        val s = w / 390f
        val t = Theme.cur
        val top = if (home) t.homeTop else t.bgTop
        p.shader = LinearGradient(0f, 0f, 0f, h, intArrayOf(top, t.bgMid, t.bgBottom), floatArrayOf(0f, .45f, 1f), Shader.TileMode.CLAMP)
        cv.drawRect(0f, 0f, w, h, p)
        p.shader = null
        val clear = 0
        val lite = t.leftLight
        // soft white light on the left, level with the core / at the top of sub pages
        if (home) blob(cv, 0.1f * w, 0.4f * h, 0.9f * w * .5f + 40 * s, 0.4f * h * .5f + 40 * s, intArrayOf(lite, clear), null)
        else blob(cv, 30 * s, 120 * s, 260 * s, 200 * s, intArrayOf(Glass.white(.7f), clear), null)
        // cool glow rising from the bottom edge
        blob(cv, 0.5f * w, h, 0.6f * w, 0.25f * h, intArrayOf(t.glowBottom, clear), null)
        val d = t.dark
        if (home) {
            // dark patch behind the mascot, with a coloured glint to the right
            blob(cv, 235 * s, 115 * s, 215 * s, 235 * s, intArrayOf(Theme.rgb(d, 247), Theme.rgb(Theme.mixDark(d), 217), clear), floatArrayOf(0f, .55f, 1f))
            blob(cv, 330 * s, 30 * s, 100 * s, 60 * s, intArrayOf(Theme.rgb(t.glow2, 140), clear), null)
            blob(cv, 50 * s, 480 * s, 130 * s, 150 * s, intArrayOf(Glass.white(.7f), clear), null)
            blob(cv, 200 * s, 660 * s, 240 * s, 100 * s, intArrayOf(Glass.white(.35f), clear), null, -8f)
        } else {
            blob(cv, 400 * s, 20 * s, 190 * s, 110 * s, intArrayOf(Theme.rgb(d, 46), clear), null)
            if (t.id != "glass") blob(cv, 340 * s, 590 * s, 170 * s, 170 * s, intArrayOf(Theme.rgb(t.glow, 115), clear), null)
        }
    }
}

/** The glass "mochi" mascot: a super-ellipse with a rim, light patches and two closed happy eyes. Drawn at 170x136 and scaled. */
/**
 * The mascots of the three looks (glass mochi, butterfly, whale), drawn on a 170x136 canvas. Used by [MochiView] in the app
 * and by the floating assistant (BuddyView), so the same character is everywhere.
 */
class GlassCritter(val kind: String = Theme.cur.mascot) {
    private val body = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rim = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 1.1f }
    private val lit = Paint(Paint.ANTI_ALIAS_FLAG)
    private val eye = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; color = Color.argb(242, 255, 255, 255) }
    private val eyeGlow = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }
    private val shape = Path()
    private val eyes = Path()
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND }
    private val wing = Paint(Paint.ANTI_ALIAS_FLAG)
    private val dot = Paint(Paint.ANTI_ALIAS_FLAG)
    /** Blink: 0 = open, 1 = closed (they are drawn as arcs; a blink flattens them). */
    var squash = 0f
    var mini = false
    var withHalo = true

    init {
        val rx = 78f; val ry = 59f
        val n = 3.0
        val steps = 96
        for (i in 0 until steps) {
            val a = i * 2 * Math.PI / steps
            val ca = Math.cos(a); val sa = Math.sin(a)
            val x = 85f + (sign(ca) * abs(ca).pow(2.0 / n) * rx).toFloat()
            val y = 68f + (sign(sa) * abs(sa).pow(2.0 / n) * ry).toFloat()
            if (i == 0) shape.moveTo(x, y) else shape.lineTo(x, y)
        }
        shape.close()
        for (cx in floatArrayOf(55f, 115f)) {
            eyes.moveTo(cx - 15f, 78f)
            eyes.cubicTo(cx - 12f, 63f, cx + 12f, 63f, cx + 15f, 78f)
        }
        body.shader = RadialGradient(85f - 78f + 156f * .34f, 68f - 59f + 118f * .20f, 120f,
            intArrayOf(Glass.white(.42f), Glass.white(.16f), Color.argb(26, 200, 220, 245), Color.argb(51, 190, 212, 240)),
            floatArrayOf(0f, .5f, .8f, 1f), Shader.TileMode.CLAMP)
        rim.shader = LinearGradient(7f, 9f, 163f, 127f, intArrayOf(Glass.white(.75f), Glass.white(.18f), Glass.white(.45f)), floatArrayOf(0f, .55f, 1f), Shader.TileMode.CLAMP)
        eyeGlow.color = Glass.white(.3f)
    }

    private fun rimA(a: Float) = Theme.rgb(Theme.cur.rim, (a * 255).toInt())

    private fun glowDot(cv: Canvas, x: Float, y: Float, r: Float, ring: Boolean) {
        val eyeC = Theme.cur.eye
        dot.style = Paint.Style.FILL
        dot.color = Theme.rgb(eyeC, 70); cv.drawCircle(x, y, r * 2f, dot)
        if (ring) {
            dot.style = Paint.Style.STROKE; dot.strokeWidth = 1.6f; dot.color = eyeC; cv.drawCircle(x, y, r, dot)
            dot.style = Paint.Style.FILL; cv.drawCircle(x, y, r * .37f, dot)
        } else { dot.style = Paint.Style.FILL; dot.color = eyeC; cv.drawCircle(x, y, r, dot) }
    }

    private fun stroked(cv: Canvas, path: Path, a: Float, w: Float) { line.strokeWidth = w; line.color = rimA(a); cv.drawPath(path, line) }

    private fun filled(cv: Canvas, path: Path, strokeW: Float) {
        val b = RectF(); path.computeBounds(b, true)
        wing.shader = LinearGradient(b.left, b.top, b.right, b.bottom, intArrayOf(rimA(.42f), rimA(.10f), rimA(.22f)), floatArrayOf(0f, .6f, 1f), Shader.TileMode.CLAMP)
        cv.drawPath(path, wing)
        stroked(cv, path, 1f, strokeW)
    }

    private fun drawCritter(cv: Canvas) {
        if (withHalo) {
            lit.shader = RadialGradient(85f, 68f, 111f, intArrayOf(Theme.rgb(Theme.cur.glow, 70), Glass.white(0f)), null, Shader.TileMode.CLAMP)
            cv.save(); cv.scale(1f, 176f / 222f, 85f, 68f); cv.drawCircle(85f, 68f, 111f, lit); cv.restore()
            lit.shader = null
        }
        if (kind == "butterfly") {
            val flap = 1f - .18f * squash
            for (side in 0..1) {
                cv.save()
                if (side == 1) { cv.translate(170f, 0f); cv.scale(-1f, 1f) }
                cv.scale(flap, 1f, 82f, 70f)
                filled(cv, BUTTERFLY_WING_A, 1.2f); filled(cv, BUTTERFLY_WING_B, 1.2f)
                stroked(cv, BUTTERFLY_VEINS, .35f, .8f)
                glowDot(cv, 44f, 46f, 7f, true)
                glowDot(cv, 50f, 100f, 4f, false)
                wing.shader = null; wing.color = Glass.white(.28f)
                cv.save(); cv.rotate(-24f, 46f, 34f); cv.drawOval(RectF(24f, 29f, 68f, 39f), wing); cv.restore()
                cv.restore()
            }
            wing.shader = null
            wing.color = rimA(.55f); cv.drawOval(RectF(80.8f, 50f, 89.2f, 102f), wing)
            line.strokeWidth = 1f; line.color = rimA(1f)
            val body = Path().apply { addOval(RectF(80.8f, 50f, 89.2f, 102f), Path.Direction.CW) }; cv.drawPath(body, line)
            wing.color = rimA(.5f); cv.drawCircle(85f, 46f, 5.5f, wing); cv.drawCircle(85f, 46f, 5.5f, line)
            stroked(cv, BUTTERFLY_ANT, 1f, 1.2f)
            glowDot(cv, 62f, 18f, 2f, false); glowDot(cv, 108f, 18f, 2f, false)
        } else {
            cv.save(); cv.translate(0f, 0f)
            filled(cv, WHALE_BODY, 1.3f)
            filled(cv, WHALE_FIN, 1f)
            stroked(cv, WHALE_LINES, .4f, .9f)
            stroked(cv, WHALE_ANT, 1f, 1.3f)
            glowDot(cv, 40f, 9f, 2f, false); glowDot(cv, 80f, 9f, 2f, false); glowDot(cv, 38f, 68f, 4.2f, false)
            wing.shader = null; wing.color = Glass.white(.30f)
            cv.save(); cv.rotate(-8f, 62f, 44f); cv.drawOval(RectF(36f, 39f, 88f, 49f), wing); cv.restore()
            cv.restore()
        }
    }

    fun draw(cv: Canvas) {
        if (kind != "mochi") { drawCritter(cv); return }
        if (withHalo) {
            lit.shader = RadialGradient(85f, 68f, 111f, intArrayOf(Glass.white(.28f), Glass.white(0f)), null, Shader.TileMode.CLAMP)
            cv.save(); cv.scale(1f, 176f / 222f, 85f, 68f); cv.drawCircle(85f, 68f, 111f, lit); cv.restore()
        }
        cv.drawPath(shape, body)
        cv.save()
        cv.clipPath(shape)
        // soft light patch on the upper part and a faint reflection at the bottom
        lit.shader = RadialGradient(69f, 26f, 48f, intArrayOf(Glass.white(.5f), Glass.white(0f)), null, Shader.TileMode.CLAMP)
        cv.save(); cv.scale(1f, 11f / 48f, 69f, 26f); cv.drawCircle(69f, 26f, 48f, lit); cv.restore()
        lit.shader = RadialGradient(99f, 125f, 60f, intArrayOf(Glass.white(.18f), Glass.white(0f)), null, Shader.TileMode.CLAMP)
        cv.save(); cv.scale(1f, 13f / 60f, 99f, 125f); cv.drawCircle(99f, 125f, 60f, lit); cv.restore()
        lit.shader = null
        cv.restore()
        cv.drawPath(shape, rim)
        // eyes: a blink squashes the arcs flat
        cv.save()
        if (squash > 0f) cv.scale(1f, 1f - .75f * squash, 85f, 80f)
        val sw = if (mini) 3.4f else 4.6f
        eyeGlow.strokeWidth = sw + 3f
        eye.strokeWidth = sw
        cv.drawPath(eyes, eyeGlow)
        cv.drawPath(eyes, eye)
        cv.restore()
    }
}

/** The mascot as a View (170x136 design units scaled to the view). */
class MochiView(c: Context, withHalo: Boolean = true) : View(c) {
    private var critter = GlassCritter().also { it.withHalo = withHalo }
    /** Blink: 0 = open, 1 = closed. */
    var squash = 0f
        set(v) { field = v; invalidate() }
    var mini = false

    override fun onMeasure(w: Int, h: Int) {
        val d = resources.displayMetrics.density
        val mw = if (MeasureSpec.getMode(w) == MeasureSpec.EXACTLY) MeasureSpec.getSize(w) else (170 * d).toInt()
        val mh = if (MeasureSpec.getMode(h) == MeasureSpec.EXACTLY) MeasureSpec.getSize(h) else (mw * 136f / 170f).toInt()
        setMeasuredDimension(mw, mh)
    }

    override fun onDraw(cv: Canvas) {
        val k = width / 170f
        critter.squash = squash; critter.mini = mini
        cv.save()
        cv.scale(k, k)
        critter.draw(cv)
        cv.restore()
    }
}

/** Parses the absolute M / C / L / Z path strings of the mockups. */
private fun svgPath(d: String): Path {
    val p = Path()
    val tok = Regex("[MCLZ]|-?[0-9]*\\.?[0-9]+").findAll(d).map { it.value }.toList()
    var i = 0
    fun n() = tok[i++].toFloat()
    while (i < tok.size) {
        when (tok[i++]) {
            "M" -> p.moveTo(n(), n())
            "L" -> p.lineTo(n(), n())
            "C" -> p.cubicTo(n(), n(), n(), n(), n(), n())
            "Z" -> p.close()
        }
    }
    return p
}

private val BUTTERFLY_WING_A = svgPath("M82 64 C70 30 36 6 16 24 C2 38 18 66 50 74 C64 78 76 74 82 70 Z")
private val BUTTERFLY_WING_B = svgPath("M82 72 C62 76 36 80 34 102 C33 118 56 124 68 108 C76 98 80 84 82 78 Z")
private val BUTTERFLY_VEINS = svgPath("M80 66 C62 46 40 30 22 28 M80 68 C58 62 36 60 14 44 M80 80 C64 88 50 98 42 112")
private val BUTTERFLY_ANT = svgPath("M83 42 C78 28 70 20 62 18 M87 42 C92 28 100 20 108 18")
private val WHALE_BODY = svgPath("M14 74 C14 48 44 34 80 34 C112 34 136 48 146 64 C150 60 156 52 164 50 C162 62 160 70 154 76 C160 80 164 88 166 98 C156 96 150 90 146 84 C132 100 104 108 70 106 C36 104 14 94 14 74 Z")
private val WHALE_FIN = svgPath("M62 88 C58 102 66 112 80 114 C80 104 78 96 74 90 Z")
private val WHALE_LINES = svgPath("M30 88 C60 99 100 98 132 84 M34 94 C62 104 98 102 124 92")
private val WHALE_ANT = svgPath("M60 33 C58 22 50 16 42 14 M60 33 C62 22 70 16 78 14")


/** A petal: a rotated glass drop whose icon and label stay upright. */
class PetalView(c: Context, kind: GlassKind, iconRes: Int, label: String, angle: Float, textColor: Int, size: Int = 12, bold: Boolean = false, sc: Float = 0f) : FrameLayout(c) {
    init {
        background = c.petalShape(kind)
        rotation = angle
        val inner = c.col {
            gravity = Gravity.CENTER_HORIZONTAL
            rotation = -angle
            val ic = if (sc > 0f) (26 * sc).toInt() else c.dp(26)
            add(c.icon(iconRes, textColor, 26).apply { layoutParams = LinearLayout.LayoutParams(ic, ic) }, ic, ic)
            add(c.text(label, T.Small, textColor).apply {
                if (sc > 0f) setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, size * sc) else textSize = size.toFloat()
                gravity = Gravity.CENTER; setLineSpacing(0f, 1.05f)
                typeface = glassFont(bold)
            }, WRAP, WRAP, top = 6)
        }
        addView(inner, LayoutParams(WRAP, WRAP, Gravity.CENTER).apply { topMargin = if (sc > 0f) (10 * sc).toInt() else c.dp(10) })
        clipChildren = false
    }
}

fun glassFont(bold: Boolean = false, thin: Boolean = false): android.graphics.Typeface = when {
    thin -> android.graphics.Typeface.create("sans-serif-thin", android.graphics.Typeface.NORMAL)
    bold -> android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL)
    else -> android.graphics.Typeface.create("sans-serif-light", android.graphics.Typeface.NORMAL)
}

/** A FrameLayout whose children are placed in a 390-wide design grid and scaled to the real width. */
class DesignCanvas(c: Context, private val designH: Int) : ViewGroup(c) {
    private class Spec(val v: View, val l: Float, val t: Float, val w: Float, val h: Float)
    private val specs = ArrayList<Spec>()

    fun put(v: View, l: Float, t: Float, w: Float, h: Float) {
        specs += Spec(v, l, t, w, h)
        addView(v)
    }

    override fun onMeasure(wSpec: Int, hSpec: Int) {
        val w = MeasureSpec.getSize(wSpec)
        val s = w / 390f
        for (sp in specs) sp.v.measure(MeasureSpec.makeMeasureSpec((sp.w * s).toInt(), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec((sp.h * s).toInt(), MeasureSpec.EXACTLY))
        setMeasuredDimension(w, (designH * s).toInt())
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        val s = (r - l) / 390f
        for (sp in specs) sp.v.layout((sp.l * s).toInt(), (sp.t * s).toInt(), ((sp.l + sp.w) * s).toInt(), ((sp.t + sp.h) * s).toInt())
    }
}
