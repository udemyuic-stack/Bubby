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
    /** Text on the bright "active" surfaces (selected tab, start petal, buttons): always dark, also in the night look. */
    var ON_ACTIVE = 0xFF1C2B45.toInt()
    /** Text fields and other inset surfaces. */
    var FIELD = Color.argb(89, 255, 255, 255)
    var NIGHT = false
    const val OK_GLOW = 0xFF7AE8BD.toInt()
}

/** One look: the default blue-grey glass, a green one with a butterfly, a blue one with a whale. */
class ThemeDef(
    val id: String, val bgTop: Int, val bgMid: Int, val bgBottom: Int, val glowBottom: Int, val leftLight: Int,
    val ink: Int, val ink2: Int, val accent: Int, val accent2: Int, val date: Int, val dark: Int, val glow: Int, val glow2: Int, val sh: Int,
    val eye: Int, val rim: Int, val tiles: List<IntArray>, val navyA: Int, val navyB: Int, val amberA: Int, val amberB: Int,
    val mascot: String, val ok: Int, val homeTop: Int = bgTop, val night: Boolean = false, val title: String = id,
    /** Name of the animated scene behind the app (assets/scene/<name>.html), or null. */
    val scene: String? = null,
    /** True for the five themed looks whose home page is the design's own page (assets/home). */
    val homePage: Boolean = false
)

object Theme {
    val GLASS = ThemeDef("glass", 0xFFE3EBF4.toInt(), 0xFFCBD8E6.toInt(), 0xFFAEBFD5.toInt(), 0xFF7F9BBD.toInt(), 0xFFF3F6FA.toInt(),
        0xFF1C2B45.toInt(), 0xFF3B4D6E.toInt(), 0xFFE08A4A.toInt(), 0xFF7A6BD0.toInt(), 0xFFB8672F.toInt(), 0xFF341E1A.toInt(), 0xFF7F9BBD.toInt(), 0xFFE2A0B0.toInt(), 0xFF466496.toInt(),
        0xFFFFFFFF.toInt(), 0xFFFFFFFF.toInt(),
        listOf(intArrayOf(0xFF8D7EE0.toInt(), 0xFF5B4BB0.toInt()), intArrayOf(0xFF58B9A2.toInt(), 0xFF3A8C78.toInt()), intArrayOf(0xFF6FA0EE.toInt(), 0xFF4A74C4.toInt()), intArrayOf(0xFFE48AA8.toInt(), 0xFFBD5F80.toInt())),
        0xFF34466B.toInt(), 0xFF1C2B45.toInt(), 0x40E08A4A.toInt(), 0x30E08A4A.toInt(), "mochi", 0xFF2F9D73.toInt(), 0xFFDDE6F0.toInt(), scene = "glass")
    val GREEN = ThemeDef("green", 0xFFE2F4D6.toInt(), 0xFFB6E58F.toInt(), 0xFF7FCF45.toInt(), 0xFF6FCF3A.toInt(), 0xFFF6FDF0.toInt(),
        0xFF123D1A.toInt(), 0xFF2F5A38.toInt(), 0xFF2FA83A.toInt(), 0xFF7BC62D.toInt(), 0xFF2F5A38.toInt(), 0xFF082C0E.toInt(), 0xFF78D746.toInt(), 0xFFBEEB3C.toInt(), 0xFF1E6428.toInt(),
        0xFFEAFF7A.toInt(), 0xFFC4F76E.toInt(),
        listOf(intArrayOf(0xFF6FD13A.toInt(), 0xFF2F9A1C.toInt()), intArrayOf(0xFF3FCF8A.toInt(), 0xFF168A52.toInt()), intArrayOf(0xFFB6E03A.toInt(), 0xFF79A812.toInt()), intArrayOf(0xFF5FD6B0.toInt(), 0xFF22987A.toInt())),
        0xFF2F6B3A.toInt(), 0xFF123D1A.toInt(), 0x70C8F58A.toInt(), 0x50A0E05A.toInt(), "butterfly", 0xFF2FA83A.toInt(), scene = "green", homePage = true)
    val BLUE = ThemeDef("blue", 0xFFE3EEFF.toInt(), 0xFFA9C6FF.toInt(), 0xFF6A96F2.toInt(), 0xFF4F86F0.toInt(), 0xFFF0F6FF.toInt(),
        0xFF0F2A6B.toInt(), 0xFF2D4C96.toInt(), 0xFF2F66E0.toInt(), 0xFF7A8CF0.toInt(), 0xFF2D4C96.toInt(), 0xFF06165A.toInt(), 0xFF468CFF.toInt(), 0xFF9682F0.toInt(), 0xFF1E46AA.toInt(),
        0xFFD2E8FF.toInt(), 0xFF9CCAFF.toInt(),
        listOf(intArrayOf(0xFF5AA8FF.toInt(), 0xFF2A5FD8.toInt()), intArrayOf(0xFF8D86F0.toInt(), 0xFF4C42C4.toInt()), intArrayOf(0xFF45C8F0.toInt(), 0xFF1A7FD0.toInt()), intArrayOf(0xFFA9A4F4.toInt(), 0xFF6A5CDC.toInt())),
        0xFF2D4FB0.toInt(), 0xFF0F2A6B.toInt(), 0x66A8BEFA.toInt(), 0x4C8CA0F0.toInt(), "shark", 0xFF2F66E0.toInt(), scene = "blue", homePage = true)
    /** The "night" look: a dark page, a black island and the white cube mascot (like the notch apps). */
    val NIGHT = ThemeDef("night", 0xFF1B1D24.toInt(), 0xFF14161C.toInt(), 0xFF0E1014.toInt(), 0xFF232A52.toInt(), 0xFF2A2D3A.toInt(),
        0xFFF1F3F8.toInt(), 0xFFA6AEC2.toInt(), 0xFF8C9CFF.toInt(), 0xFFB79CFF.toInt(), 0xFFA6AEC2.toInt(), 0xFF000000.toInt(), 0xFF5B6CFF.toInt(), 0xFFA68BFF.toInt(), 0xFF000000.toInt(),
        0xFFFFFFFF.toInt(), 0xFFFFFFFF.toInt(),
        listOf(intArrayOf(0xFF8F7BFF.toInt(), 0xFF5A47D6.toInt()), intArrayOf(0xFF3FC9A5.toInt(), 0xFF1B8F78.toInt()), intArrayOf(0xFF5B9BFF.toInt(), 0xFF2F63D6.toInt()), intArrayOf(0xFFFF8FB3.toInt(), 0xFFD0507E.toInt())),
        0xFF3A4070.toInt(), 0xFF22264A.toInt(), 0x667C8CFF.toInt(), 0x555B6CD6.toInt(), "cube", 0xFF4CD08A.toInt(), 0xFF1B1D24.toInt(), night = true, scene = "night")
    /** The five themed looks: each has its own animated scene and mascot (space, Halloween, rainy forest, ocean, New Year). */
    val SPACE = ThemeDef("space", 0xFFD8D4F2.toInt(), 0xFFB3ABE0.toInt(), 0xFF8A7FCA.toInt(), 0xFF7B70C4.toInt(), 0xFFF4F0FF.toInt(),
        0xFF1D1A40.toInt(), 0xFF4C4880.toInt(), 0xFFFF9A1F.toInt(), 0xFFFFC93C.toInt(), 0xFF4C4880.toInt(), 0xFF16123A.toInt(), 0xFFFFBE5A.toInt(), 0xFFBEAFFF.toInt(), 0xFF28205A.toInt(),
        0xFFFFFFFF.toInt(), 0xFFFFE2A0.toInt(),
        listOf(intArrayOf(0xFFFFC93C.toInt(), 0xFFFF8A1F.toInt()), intArrayOf(0xFF8FB6FF.toInt(), 0xFF3F5FD0.toInt()), intArrayOf(0xFFC8A8FF.toInt(), 0xFF7350D0.toInt()), intArrayOf(0xFF7FE0D0.toInt(), 0xFF2F9A8A.toInt())),
        0xFF4C4880.toInt(), 0xFF1D1A40.toInt(), 0x40FF9A1F.toInt(), 0x30FF9A1F.toInt(), "sun", 0xFF2F9D73.toInt(), title = "Space", scene = "space", homePage = true)
    val HALLOWEEN = ThemeDef("halloween", 0xFFDDD3E8.toInt(), 0xFFBFB0D4.toInt(), 0xFF9884B4.toInt(), 0xFF8A6AA8.toInt(), 0xFFF6F0FC.toInt(),
        0xFF221836.toInt(), 0xFF54466E.toInt(), 0xFFFF7A1A.toInt(), 0xFFFFB04D.toInt(), 0xFF54466E.toInt(), 0xFF1E122C.toInt(), 0xFFFF8C32.toInt(), 0xFFC8AAFF.toInt(), 0xFF321E50.toInt(),
        0xFFFFFFFF.toInt(), 0xFFFFD9A8.toInt(),
        listOf(intArrayOf(0xFFFFB04D.toInt(), 0xFFFF7A1A.toInt()), intArrayOf(0xFFB69CF0.toInt(), 0xFF6A48B8.toInt()), intArrayOf(0xFF8FD6A0.toInt(), 0xFF3C9A5C.toInt()), intArrayOf(0xFFFF8FA8.toInt(), 0xFFC43C66.toInt())),
        0xFF54466E.toInt(), 0xFF221836.toInt(), 0x40FF7A1A.toInt(), 0x30FF7A1A.toInt(), "pumpkin", 0xFF2F9D73.toInt(), title = "Halloween", scene = "halloween", homePage = true)
    val FOREST = ThemeDef("forest", 0xFFC7D2DF.toInt(), 0xFFA8B7CA.toInt(), 0xFF8597B0.toInt(), 0xFF6D84A6.toInt(), 0xFFECF2FA.toInt(),
        0xFF1A2840.toInt(), 0xFF46587A.toInt(), 0xFF3F6FB0.toInt(), 0xFF7FA6D6.toInt(), 0xFF46587A.toInt(), 0xFF182234.toInt(), 0xFF82A5D7.toInt(), 0xFFAAC8F0.toInt(), 0xFF283C5F.toInt(),
        0xFFFFFFFF.toInt(), 0xFFE6F0FF.toInt(),
        listOf(intArrayOf(0xFF7FA6D6.toInt(), 0xFF3F6FB0.toInt()), intArrayOf(0xFF8FC1FF.toInt(), 0xFF3D7FC2.toInt()), intArrayOf(0xFFA9B4E6.toInt(), 0xFF5A68B8.toInt()), intArrayOf(0xFF9FD3D6.toInt(), 0xFF3F8F9A.toInt())),
        0xFF46587A.toInt(), 0xFF1A2840.toInt(), 0x403F6FB0.toInt(), 0x303F6FB0.toInt(), "cloud", 0xFF2F9D73.toInt(), title = "Rainy forest", scene = "forest", homePage = true)
    val OCEAN = ThemeDef("ocean", 0xFFEAFCFF.toInt(), 0xFFB4ECF6.toInt(), 0xFF7CCFE6.toInt(), 0xFF7FD4EA.toInt(), 0xFFECFCFF.toInt(),
        0xFF123048.toInt(), 0xFF3C6680.toInt(), 0xFFFF7A5C.toInt(), 0xFFFFB08A.toInt(), 0xFF3C6680.toInt(), 0xFF0A3250.toInt(), 0xFF78DCE6.toInt(), 0xFFFFBEAA.toInt(), 0xFF144664.toInt(),
        0xFFFFFFFF.toInt(), 0xFFC8F4FF.toInt(),
        listOf(intArrayOf(0xFFFFB08A.toInt(), 0xFFFF7A5C.toInt()), intArrayOf(0xFF7FE0E8.toInt(), 0xFF2A9AB8.toInt()), intArrayOf(0xFF9AE6A0.toInt(), 0xFF3A9A5A.toInt()), intArrayOf(0xFFFFD27A.toInt(), 0xFFE0A030.toInt())),
        0xFF3C6680.toInt(), 0xFF123048.toInt(), 0x40FF7A5C.toInt(), 0x30FF7A5C.toInt(), "jelly", 0xFF2F9D73.toInt(), title = "Ocean", scene = "ocean", homePage = true)
    val NEWYEAR = ThemeDef("newyear", 0xFFDDE6F0.toInt(), 0xFFCBD8E6.toInt(), 0xFFAEBFD5.toInt(), 0xFF7F9BBD.toInt(), 0xFFF3F6FA.toInt(),
        0xFF1C2B45.toInt(), 0xFF4A5C7D.toInt(), 0xFF4F78B8.toInt(), 0xFF8FB0DE.toInt(), 0xFF4A5C7D.toInt(), 0xFF1C2B45.toInt(), 0xFF96B9EB.toInt(), 0xFFBED7FA.toInt(), 0xFF466496.toInt(),
        0xFFFFFFFF.toInt(), 0xFFD6E6FF.toInt(),
        listOf(intArrayOf(0xFF8FB0DE.toInt(), 0xFF4F78B8.toInt()), intArrayOf(0xFF9FD8E6.toInt(), 0xFF4C9AB8.toInt()), intArrayOf(0xFFB9C4F0.toInt(), 0xFF6B78C8.toInt()), intArrayOf(0xFFA8E6D8.toInt(), 0xFF4AA894.toInt())),
        0xFF4A5C7D.toInt(), 0xFF1C2B45.toInt(), 0x404F78B8.toInt(), 0x304F78B8.toInt(), "snowman", 0xFF2F9D73.toInt(), title = "New Year", scene = "newyear", homePage = true)
    val ICE = ThemeDef("ice", 0xFFDDE6F0.toInt(), 0xFFCBD8E6.toInt(), 0xFFAEBFD5.toInt(), 0xFF7F9BBD.toInt(), 0xFFF3F6FA.toInt(),
        0xFF1C2B45.toInt(), 0xFF4A5C7D.toInt(), 0xFF4F78B8.toInt(), 0xFF8FB0DE.toInt(), 0xFF4A5C7D.toInt(), 0xFF1C2B45.toInt(), 0xFF96B9EB.toInt(), 0xFFBED7FA.toInt(), 0xFF466496.toInt(),
        0xFFFFFFFF.toInt(), 0xFFD6E6FF.toInt(),
        listOf(intArrayOf(0xFF8FB0DE.toInt(), 0xFF4F78B8.toInt()), intArrayOf(0xFF9FD8E6.toInt(), 0xFF4C9AB8.toInt()), intArrayOf(0xFFB9C4F0.toInt(), 0xFF6B78C8.toInt()), intArrayOf(0xFFA8E6D8.toInt(), 0xFF4AA894.toInt())),
        0xFF4A5C7D.toInt(), 0xFF1C2B45.toInt(), 0x404F78B8.toInt(), 0x304F78B8.toInt(), "mochi", 0xFF2F9D73.toInt(), title = "Glacier", scene = "ice", homePage = true)
    val ALL = listOf(GLASS, GREEN, BLUE, NIGHT, SPACE, HALLOWEEN, FOREST, OCEAN, NEWYEAR, ICE)

    var cur: ThemeDef = GLASS
        private set

    /** Tile gradient [i] of the theme; the default theme keeps the colours passed in. */
    fun tile(i: Int, a: Int, b: Int): IntArray = if (cur.id == "glass") intArrayOf(a, b) else cur.tiles[i % cur.tiles.size]

    /** The animated scene behind the app is on for this look (and not switched off in Settings). */
    fun sceneOn(ctx: Context): Boolean = cur.scene != null && Backend.bool(ctx, "scene_anim", true) && (!cur.homePage || WebCompat.ok())

    fun byId(id: String) = ALL.firstOrNull { it.id == id } ?: GLASS

    fun apply(ctx: Context) {
        cur = byId(Backend.str(ctx, "theme", "glass"))
        SvgArt.init(ctx)
        val t = cur
        Glass.TEXT = t.ink; Glass.TEXT2 = t.ink2; Glass.TEXT_LABEL = t.ink2
        Glass.ACCENT = t.accent; Glass.ACCENT2 = t.accent2; Glass.DATE = t.date
        Glass.OK = t.ok; Glass.NAVY_A = t.navyA; Glass.NAVY_B = t.navyB
        Glass.NIGHT = t.night
        Glass.ON_ACTIVE = if (t.night) 0xFF14161C.toInt() else t.ink
        Glass.FIELD = if (t.night) Glass.white(.08f) else Glass.white(.35f)
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
            GlassKind.Base -> if (Glass.NIGHT) {
                fill.shader = LinearGradient(x0, y0, x1, y1, Glass.white(.11f), Glass.white(.05f), Shader.TileMode.CLAMP)
                rim.color = Glass.white(.14f)
            } else {
                fill.shader = LinearGradient(x0, y0, x1, y1, Glass.white(.80f), Glass.white(.56f), Shader.TileMode.CLAMP)
                rim.color = Glass.white(.85f)
            }
            GlassKind.Nav -> if (Glass.NIGHT) {
                fill.shader = LinearGradient(x0, y0, x1, y1, 0xFA20232C.toInt(), 0xFA191B22.toInt(), Shader.TileMode.CLAMP)
                rim.color = Glass.white(.12f)
            } else {
                val a = Theme.cur.bgMid
                fill.shader = LinearGradient(x0, y0, x1, y1, Color.argb(252, 255 - (255 - Color.red(a)) / 4, 255 - (255 - Color.green(a)) / 4, 255 - (255 - Color.blue(a)) / 4), Color.argb(248, 255 - (255 - Color.red(a)) / 2, 255 - (255 - Color.green(a)) / 2, 255 - (255 - Color.blue(a)) / 2), Shader.TileMode.CLAMP)
                rim.color = Glass.white(.85f)
            }
            GlassKind.Dim -> if (Glass.NIGHT) {
                fill.shader = LinearGradient(x0 + w * .2f, y0, x1 - w * .2f, y1, Glass.white(.10f), Glass.white(.04f), Shader.TileMode.CLAMP)
                rim.color = Glass.white(.16f)
            } else {
                fill.shader = LinearGradient(x0 + w * .2f, y0, x1 - w * .2f, y1, Glass.white(.58f), Color.argb(90, 170, 190, 215), Shader.TileMode.CLAMP)
                rim.color = Glass.white(.7f)
            }
            GlassKind.Mid -> {
                fill.shader = LinearGradient(x0 + w * .2f, y0, x1 - w * .2f, y1, Glass.white(.20f), Glass.white(.06f), Shader.TileMode.CLAMP)
                rim.color = Glass.white(.35f)
            }
            GlassKind.Active -> if (Glass.NIGHT) {
                fill.shader = LinearGradient(x0, y0, x1, y1, 0xFFF4F6FB.toInt(), 0xFFD9DEEA.toInt(), Shader.TileMode.CLAMP)
                rim.color = Color.WHITE
            } else {
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
            else -> Theme.rgb(Theme.cur.sh, if (Glass.NIGHT) 60 else 30)
        }
        // inner glow
        val glowA = if (Glass.NIGHT && kind != GlassKind.Active) 0f else when (kind) { GlassKind.Active -> .8f; GlassKind.Dim -> .25f; GlassKind.Mid -> .12f; GlassKind.Base -> .18f; else -> 0f }
        glow.shader = if (glowA > 0f) RadialGradient(r.centerX(), r.centerY(), maxOf(w, h) * .62f,
            intArrayOf(Glass.white(0f), Glass.white(0f), Glass.white(glowA)), floatArrayOf(0f, .55f, 1f), Shader.TileMode.CLAMP) else null
    }

    override fun draw(c: Canvas) {
        if (bounds.isEmpty) return
        if (shadow && kind != GlassKind.Mid) {
            // one soft offset copy (three were too heavy for long lists)
            c.save()
            c.translate(0f, density * 5f)
            c.scale(.985f, .985f, bounds.exactCenterX(), bounds.exactCenterY())
            shade.alpha = (Color.alpha(shade.color) * .7f).toInt().coerceIn(0, 255)
            c.drawPath(path, shade)
            c.restore()
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

/** The page background: cool gradient with soft light blobs, painted once into a half-size bitmap (the gradients are soft, and scrolling must not repaint them). */
class GlassBackground(c: Context, private val home: Boolean) : View(c) {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val blit = Paint(Paint.FILTER_BITMAP_FLAG)
    private var bmp: android.graphics.Bitmap? = null
    private var bmpFor = ""

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

    private fun paint(cv: Canvas, w: Float, h: Float) {
        val s = w / 390f
        val t = Theme.cur
        val top = if (home) t.homeTop else t.bgTop
        p.shader = LinearGradient(0f, 0f, 0f, h, intArrayOf(top, t.bgMid, t.bgBottom), floatArrayOf(0f, .45f, 1f), Shader.TileMode.CLAMP)
        cv.drawRect(0f, 0f, w, h, p)
        p.shader = null
        val clear = 0
        val lite = t.leftLight
        val soft = if (t.night) .05f else .7f   // the white light blobs are barely there on the night look
        // soft white light on the left, level with the core / at the top of sub pages
        if (home) blob(cv, 0.1f * w, 0.4f * h, 0.9f * w * .5f + 40 * s, 0.4f * h * .5f + 40 * s, intArrayOf(lite, clear), null)
        else blob(cv, 30 * s, 120 * s, 260 * s, 200 * s, intArrayOf(Glass.white(soft), clear), null)
        // cool glow rising from the bottom edge
        blob(cv, 0.5f * w, h, 0.6f * w, 0.25f * h, intArrayOf(t.glowBottom, clear), null)
        val d = t.dark
        if (home) {
            // dark patch behind the mascot, with a coloured glint to the right
            blob(cv, 235 * s, 115 * s, 215 * s, 235 * s, intArrayOf(Theme.rgb(d, 247), Theme.rgb(Theme.mixDark(d), 217), clear), floatArrayOf(0f, .55f, 1f))
            blob(cv, 330 * s, 30 * s, 100 * s, 60 * s, intArrayOf(Theme.rgb(t.glow2, 140), clear), null)
            blob(cv, 50 * s, 480 * s, 130 * s, 150 * s, intArrayOf(Glass.white(soft), clear), null)
            blob(cv, 200 * s, 660 * s, 240 * s, 100 * s, intArrayOf(Glass.white(soft / 2), clear), null, -8f)
        } else {
            blob(cv, 400 * s, 20 * s, 190 * s, 110 * s, intArrayOf(Theme.rgb(d, 46), clear), null)
            if (t.id != "glass") blob(cv, 340 * s, 590 * s, 170 * s, 170 * s, intArrayOf(Theme.rgb(t.glow, 115), clear), null)
        }
    }

    override fun onDraw(cv: Canvas) {
        val w = width; val h = height
        if (w <= 0 || h <= 0) return
        val key = "$w x $h ${Theme.cur.id}"
        if (bmp == null || bmpFor != key) {
            bmp?.recycle()
            val b = android.graphics.Bitmap.createBitmap(maxOf(1, w / 2), maxOf(1, h / 2), android.graphics.Bitmap.Config.ARGB_8888)
            val bc = Canvas(b)
            bc.scale(.5f, .5f)
            paint(bc, w.toFloat(), h.toFloat())
            bmp = b; bmpFor = key
        }
        cv.drawBitmap(bmp!!, null, android.graphics.Rect(0, 0, w, h), blit)
    }

    override fun onDetachedFromWindow() { super.onDetachedFromWindow(); bmp?.recycle(); bmp = null }
}

/**
 * The mascots of the three looks (glass mochi, butterfly, whale), drawn on a 170x136 canvas. Used by [MochiView] in the app
 * and by the floating assistant (BuddyView), so the same character is everywhere.
 */
private val ART = setOf("sun", "pumpkin", "cloud", "jelly", "snowman", "butterfly", "shark")

class GlassCritter(val kind: String = Theme.cur.mascot, private val def: ThemeDef = Theme.cur) {
    private val body = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rim = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 1.1f }
    private val lit = Paint(Paint.ANTI_ALIAS_FLAG)
    private val eye = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; color = Color.argb(242, 255, 255, 255) }
    private val eyeGlow = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }
    private val shape = Path()
    private val eyes = Path()
    private val cubeShape = Path()
    private val cubeFill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND }
    private val wing = Paint(Paint.ANTI_ALIAS_FLAG)
    private val dot = Paint(Paint.ANTI_ALIAS_FLAG)
    /** Blink: 0 = open, 1 = closed (they are drawn as arcs; a blink flattens them). */
    var squash = 0f
    var mini = false
    var withHalo = true
    /** Poke reaction: 1 draws wide-open dot eyes instead of the happy arcs (mochi and cube). */
    var awake = false

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
        // the "cube": a super-ellipse a little boxier than the glass mochi (exponent 2.7)
        for (i in 0 until steps) {
            val a = i * 2 * Math.PI / steps
            val ca = Math.cos(a); val sa = Math.sin(a)
            val x = 85f + (sign(ca) * abs(ca).pow(2.0 / 2.7) * 74f).toFloat()
            val y = 68f + (sign(sa) * abs(sa).pow(2.0 / 2.7) * 58f).toFloat()
            if (i == 0) cubeShape.moveTo(x, y) else cubeShape.lineTo(x, y)
        }
        cubeShape.close()
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

    private fun rimA(a: Float) = Theme.rgb(def.rim, (a * 255).toInt())

    private fun glowDot(cv: Canvas, x: Float, y: Float, r: Float, ring: Boolean) {
        val eyeC = def.eye
        dot.style = Paint.Style.FILL
        dot.color = Theme.rgb(eyeC, 70); cv.drawCircle(x, y, r * 2f, dot)
        if (ring) {
            dot.style = Paint.Style.STROKE; dot.strokeWidth = 1.6f; dot.color = eyeC; cv.drawCircle(x, y, r, dot)
            dot.style = Paint.Style.FILL; cv.drawCircle(x, y, r * .37f, dot)
        } else { dot.style = Paint.Style.FILL; dot.color = eyeC; cv.drawCircle(x, y, r, dot) }
    }

    private fun stroked(cv: Canvas, path: Path, a: Float, w: Float) { line.strokeWidth = w; line.color = rimA(a); cv.drawPath(path, line) }

    private fun filled(cv: Canvas, path: Path, strokeW: Float) {
        wing.shader = shaderCache.getOrPut(path) {
            val b = RectF(); path.computeBounds(b, true)
            LinearGradient(b.left, b.top, b.right, b.bottom, intArrayOf(rimA(.42f), rimA(.10f), rimA(.22f)), floatArrayOf(0f, .6f, 1f), Shader.TileMode.CLAMP)
        }
        cv.drawPath(path, wing)
        stroked(cv, path, 1f, strokeW)
    }

    private val shaderCache = HashMap<Any, Shader>()
    private val cubeHalo: Shader by lazy { RadialGradient(85f, 68f, 111f, intArrayOf(Theme.rgb(def.glow, 60), Glass.white(0f)), null, Shader.TileMode.CLAMP) }
    private val cubeBodySh: Shader by lazy { LinearGradient(0f, 10f, 0f, 126f, intArrayOf(0xFFFFFFFF.toInt(), 0xFFF1F3F8.toInt(), 0xFFC9D2E4.toInt()), floatArrayOf(0f, .55f, 1f), Shader.TileMode.CLAMP) }
    private val cubeHi: Shader by lazy { RadialGradient(62f, 30f, 46f, intArrayOf(Glass.white(.85f), Glass.white(0f)), null, Shader.TileMode.CLAMP) }
    private val mochiHalo: Shader by lazy { RadialGradient(85f, 68f, 111f, intArrayOf(Glass.white(.28f), Glass.white(0f)), null, Shader.TileMode.CLAMP) }
    private val mochiPatch: Shader by lazy { RadialGradient(69f, 26f, 48f, intArrayOf(Glass.white(.5f), Glass.white(0f)), null, Shader.TileMode.CLAMP) }
    private val mochiRefl: Shader by lazy { RadialGradient(99f, 125f, 60f, intArrayOf(Glass.white(.18f), Glass.white(0f)), null, Shader.TileMode.CLAMP) }
    private val critterHalo: Shader by lazy { RadialGradient(85f, 68f, 66f, intArrayOf(Theme.rgb(def.glow, 70), Glass.white(0f)), null, Shader.TileMode.CLAMP) }   // fades out inside the view: no hard edge

    private fun drawCritter(cv: Canvas) {
        if (withHalo) {
            lit.shader = critterHalo
            cv.drawCircle(85f, 68f, 66f, lit)
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

    private fun drawCube(cv: Canvas) {
        if (withHalo) {
            lit.shader = cubeHalo
            cv.save(); cv.scale(1f, 176f / 222f, 85f, 68f); cv.drawCircle(85f, 68f, 111f, lit); cv.restore()
            lit.shader = null
        }
        cubeFill.shader = cubeBodySh
        cv.drawPath(cubeShape, cubeFill)
        cubeFill.shader = null
        cv.save(); cv.clipPath(cubeShape)
        lit.shader = cubeHi
        cv.save(); cv.scale(1f, .45f, 62f, 30f); cv.drawCircle(62f, 30f, 46f, lit); cv.restore()
        lit.shader = null
        cv.restore()
        // blush and happy closed eyes
        cubeFill.color = Color.argb(70, 255, 130, 160)
        cv.drawOval(RectF(30f, 78f, 58f, 92f), cubeFill); cv.drawOval(RectF(112f, 78f, 140f, 92f), cubeFill)
        eye.color = 0xFF1A1F2C.toInt()
        cv.save()
        if (squash > 0f) cv.scale(1f, 1f - .75f * squash, 85f, 80f)
        eye.strokeWidth = if (mini) 6f else 5.2f
        if (awake) { eye.style = Paint.Style.FILL; for (ex in floatArrayOf(55f, 115f)) cv.drawCircle(ex, 74f, 6.2f, eye); eye.style = Paint.Style.STROKE }
        else cv.drawPath(eyes, eye)
        eye.color = Color.argb(242, 255, 255, 255)
        cv.restore()
    }

    /** The mascots drawn from SVG files (see [SvgArt]); they move all the time. */
    val animated get() = kind in ART

    fun draw(cv: Canvas) {
        if (kind in ART) { val a = SvgArt.get(kind); if (a != null) { a.draw(cv, android.os.SystemClock.uptimeMillis() / 1000f, squash); return } }
        if (kind == "cube") { drawCube(cv); return }
        if (kind != "mochi") { drawCritter(cv); return }
        if (withHalo) {
            lit.shader = mochiHalo
            cv.save(); cv.scale(1f, 176f / 222f, 85f, 68f); cv.drawCircle(85f, 68f, 111f, lit); cv.restore()
        }
        cv.drawPath(shape, body)
        cv.save()
        cv.clipPath(shape)
        // soft light patch on the upper part and a faint reflection at the bottom
        lit.shader = mochiPatch
        cv.save(); cv.scale(1f, 11f / 48f, 69f, 26f); cv.drawCircle(69f, 26f, 48f, lit); cv.restore()
        lit.shader = mochiRefl
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
        if (awake) {
            eye.style = Paint.Style.FILL
            for (ex in floatArrayOf(55f, 115f)) { dot.style = Paint.Style.FILL; dot.color = Glass.white(.3f); cv.drawCircle(ex, 74f, 10f, dot); cv.drawCircle(ex, 74f, 6.2f, eye) }
            eye.style = Paint.Style.STROKE
        } else {
            cv.drawPath(eyes, eyeGlow)
            cv.drawPath(eyes, eye)
        }
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
    /** Tap reaction (like pressing the little round-screen pet): flatten under the finger, spring back with sparks, eyes pop open. */
    var pokeable = false
        set(v) { field = v; if (v) isClickable = true }
    private var press = 0f            // 0..1 how flat it is under the finger
    private var popAt = 0L            // uptime when the finger lifted
    private val rayPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }
    private var pressAnim: android.animation.ValueAnimator? = null

    override fun onMeasure(w: Int, h: Int) {
        val d = resources.displayMetrics.density
        val mw = if (MeasureSpec.getMode(w) == MeasureSpec.EXACTLY) MeasureSpec.getSize(w) else (170 * d).toInt()
        val mh = if (MeasureSpec.getMode(h) == MeasureSpec.EXACTLY) MeasureSpec.getSize(h) else (mw * 136f / 170f).toInt()
        setMeasuredDimension(mw, mh)
    }

    override fun onTouchEvent(e: android.view.MotionEvent): Boolean {
        if (!pokeable) return super.onTouchEvent(e)
        when (e.actionMasked) {
            android.view.MotionEvent.ACTION_DOWN -> { popAt = 0L; pressTo(1f, 90); return true }
            android.view.MotionEvent.ACTION_UP, android.view.MotionEvent.ACTION_CANCEL -> {
                pressTo(0f, 0); popAt = android.os.SystemClock.uptimeMillis(); invalidate()
                if (e.actionMasked == android.view.MotionEvent.ACTION_UP) { performClick(); jump(); burst(Glass.ACCENT) }
                return true
            }
        }
        return true
    }

    private fun pressTo(to: Float, ms: Long) {
        pressAnim?.cancel()
        if (ms == 0L) { press = to; invalidate(); return }
        pressAnim = android.animation.ValueAnimator.ofFloat(press, to).apply { duration = ms; addUpdateListener { press = it.animatedValue as Float; invalidate() }; start() }
    }

    override fun onDraw(cv: Canvas) {
        val k = width / 170f
        critter.squash = squash; critter.mini = mini
        var sx = 1f; var sy = 1f
        var rays = 0f; var wake = false
        if (press > 0f) { sy -= .34f * press; sx += .10f * press }
        if (popAt != 0L) {
            val t = (android.os.SystemClock.uptimeMillis() - popAt) / 1000f
            if (t > 1.5f) popAt = 0L else {
                // damped spring from the flattened shape, overshooting upwards
                val spring = Math.exp(-5.0 * t).toFloat() * Math.cos(t * 16.0).toFloat()
                sy += (-.34f * spring).coerceIn(-.34f, .22f); sx += (.10f * spring)
                wake = t > .12f && t < 1.15f
                if (t in .1f..0.75f) rays = Math.sin((t - .1f) / .65f * Math.PI).toFloat()
                invalidate()
            }
        }
        critter.awake = wake
        if (critter.animated) postInvalidateOnAnimation()
        cv.save()
        cv.scale(k, k)
        cv.scale(sx, sy, 85f, 118f)
        critter.draw(cv)
        cv.restore()
        if (rays > 0f) {
            // short sparks around the body, flying outwards
            cv.save(); cv.scale(k, k)
            rayPaint.color = Glass.white(.85f * rays); rayPaint.strokeWidth = 3.2f
            for (i in 0 until 10) {
                val a = Math.toRadians(i * 36.0 + 18.0)
                val cx = 85f; val cy = 68f
                val r0 = 1.10f + .08f * (1f - rays); val r1 = r0 + .12f + .05f * rays
                val ex = Math.cos(a).toFloat() * 78f; val ey = Math.sin(a).toFloat() * 59f
                cv.drawLine(cx + ex * r0, cy + ey * r0, cx + ex * r1, cy + ey * r1, rayPaint)
            }
            cv.restore()
        }
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
    thin -> android.graphics.Typeface.create("sans-serif-light", android.graphics.Typeface.NORMAL)
    bold -> android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL)
    else -> android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.NORMAL)
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

/** A card of the Style picker: the look's own page colours with its mascot, a name and a mark when chosen. */
class StylePreview(c: Context, private val def: ThemeDef, private val title: String, private val sub: String, private val chosen: Boolean) : View(c) {
    private val critter = GlassCritter(def.mascot, def).also { it.withHalo = false }
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val d = c.resources.displayMetrics.density

    override fun onDraw(cv: Canvas) {
        val w = width.toFloat(); val h = height.toFloat()
        val r = 18f * d
        val rect = RectF(1.5f * d, 1.5f * d, w - 1.5f * d, h - 1.5f * d)
        p.style = Paint.Style.FILL
        p.shader = LinearGradient(0f, 0f, 0f, h, intArrayOf(def.bgTop, def.bgMid, def.bgBottom), floatArrayOf(0f, .5f, 1f), Shader.TileMode.CLAMP)
        cv.drawRoundRect(rect, r, r, p)
        // the dark patch the mascot sits on (as on the home page)
        p.shader = RadialGradient(w / 2f, h * .38f, h * .55f, intArrayOf(Theme.rgb(def.dark, 235), Theme.rgb(def.dark, 110), 0), floatArrayOf(0f, .55f, 1f), Shader.TileMode.CLAMP)
        cv.save(); cv.clipPath(Path().apply { addRoundRect(rect, r, r, Path.Direction.CW) })
        cv.drawCircle(w / 2f, h * .38f, h * .55f, p)
        cv.restore()
        p.shader = null
        val mw = w * .56f
        cv.save()
        cv.translate((w - mw) / 2f, h * .06f)
        val k = mw / 170f
        cv.scale(k, k)
        critter.draw(cv)
        cv.restore()
        // name
        p.style = Paint.Style.FILL
        p.color = if (def.night) 0xFFF1F3F8.toInt() else def.ink
        p.textSize = 14f * d; p.typeface = glassFont(bold = true); p.textAlign = Paint.Align.CENTER
        cv.drawText(title, w / 2f, h - 26f * d, p)
        p.color = if (def.night) 0xFFA6AEC2.toInt() else def.ink2
        p.textSize = 12f * d; p.typeface = glassFont()
        cv.drawText(sub, w / 2f, h - 10f * d, p)
        // frame: the chosen one has a strong one
        p.style = Paint.Style.STROKE
        p.strokeWidth = (if (chosen) 2.5f else 1f) * d
        p.color = if (chosen) def.accent else Glass.white(.5f)
        cv.drawRoundRect(rect, r, r, p)
        if (chosen) {
            p.style = Paint.Style.FILL; p.color = def.accent
            cv.drawCircle(w - 18f * d, 18f * d, 10f * d, p)
            p.style = Paint.Style.STROKE; p.strokeWidth = 2f * d; p.color = if (def.night) 0xFF14161C.toInt() else 0xFFFFFFFF.toInt(); p.strokeCap = Paint.Cap.ROUND
            cv.drawLine(w - 22.5f * d, 18.5f * d, w - 19f * d, 22f * d, p); cv.drawLine(w - 19f * d, 22f * d, w - 13f * d, 14.5f * d, p)
        }
    }
}
