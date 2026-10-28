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

    const val TEXT = 0xFF1C2B45.toInt()
    const val TEXT2 = 0xFF4A5C7D.toInt()
    const val TEXT_LABEL = 0xFF3F5578.toInt()
    const val ACCENT = 0xFFE08A4A.toInt()
    const val DATE = 0xFFB8672F.toInt()
    const val OK = 0xFF2F9D73.toInt()
    const val OK_GLOW = 0xFF7AE8BD.toInt()
    const val NAVY_A = 0xFF34466B.toInt()
    const val NAVY_B = 0xFF1C2B45.toInt()
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
                fill.shader = LinearGradient(x0, y0, x1, y1, Color.argb(235, 238, 244, 251), Color.argb(214, 214, 226, 240), Shader.TileMode.CLAMP)
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
                fill.shader = LinearGradient(x0, y0, x1, y1, Color.argb(128, 224, 138, 74), Color.argb(102, 150, 90, 60), Shader.TileMode.CLAMP)
                rim.color = Glass.white(.55f)
            }
            GlassKind.Navy -> {
                fill.shader = LinearGradient(x0, y0, x1, y1, Glass.NAVY_A, Glass.NAVY_B, Shader.TileMode.CLAMP)
                rim.color = Glass.white(.18f)
            }
        }
        rim.strokeWidth = maxOf(1f, density)
        shade.color = when (kind) {
            GlassKind.Active -> Color.argb(46, 60, 90, 140)
            GlassKind.Mid -> Color.argb(0, 0, 0, 0)
            GlassKind.Navy -> Color.argb(52, 28, 43, 69)
            else -> Color.argb(30, 70, 100, 150)
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
        val top = if (home) 0xFFDDE6F0.toInt() else 0xFFE3EBF4.toInt()
        p.shader = LinearGradient(0f, 0f, 0f, h, intArrayOf(top, 0xFFCBD8E6.toInt(), 0xFFAEBFD5.toInt()), floatArrayOf(0f, .45f, 1f), Shader.TileMode.CLAMP)
        cv.drawRect(0f, 0f, w, h, p)
        p.shader = null
        val clear = 0
        // soft white light on the left, level with the core / at the top of sub pages
        if (home) blob(cv, 0.1f * w, 0.4f * h, 0.9f * w * .5f + 40 * s, 0.4f * h * .5f + 40 * s, intArrayOf(0xFFF3F6FA.toInt(), clear), null)
        else blob(cv, 30 * s, 120 * s, 260 * s, 200 * s, intArrayOf(Glass.white(.7f), clear), null)
        // cool glow rising from the bottom edge
        blob(cv, 0.5f * w, h, 0.6f * w, 0.25f * h, intArrayOf(0xFF7F9BBD.toInt(), clear), null)
        if (home) {
            // dark brown patch behind the glass mascot, with a pink glint to the right
            blob(cv, 235 * s, 115 * s, 215 * s, 235 * s, intArrayOf(Color.argb(247, 52, 30, 26), Color.argb(217, 78, 48, 42), clear), floatArrayOf(0f, .55f, 1f))
            blob(cv, 330 * s, 30 * s, 100 * s, 60 * s, intArrayOf(Color.argb(140, 226, 160, 176), clear), null)
            blob(cv, 50 * s, 480 * s, 130 * s, 150 * s, intArrayOf(Glass.white(.7f), clear), null)
            blob(cv, 200 * s, 660 * s, 240 * s, 100 * s, intArrayOf(Glass.white(.35f), clear), null, -8f)
        } else {
            blob(cv, 400 * s, 20 * s, 190 * s, 110 * s, intArrayOf(Color.argb(46, 52, 30, 26), clear), null)
        }
    }
}

/** The glass "mochi" mascot: a super-ellipse with a rim, light patches and two closed happy eyes. Drawn at 170x136 and scaled. */
class MochiView(c: Context, private val withHalo: Boolean = true) : View(c) {
    private val body = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rim = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 1.1f }
    private val lit = Paint(Paint.ANTI_ALIAS_FLAG)
    private val eye = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; color = Color.argb(242, 255, 255, 255) }
    private val eyeGlow = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }
    private val shape = Path()
    private val eyes = Path()
    /** Blink: 0 = open, 1 = closed (they are drawn as arcs; a blink flattens them). */
    var squash = 0f
        set(v) { field = v; invalidate() }
    var mini = false

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

    override fun onMeasure(w: Int, h: Int) {
        val d = resources.displayMetrics.density
        val mw = if (MeasureSpec.getMode(w) == MeasureSpec.EXACTLY) MeasureSpec.getSize(w) else (170 * d).toInt()
        val mh = if (MeasureSpec.getMode(h) == MeasureSpec.EXACTLY) MeasureSpec.getSize(h) else (mw * 136f / 170f).toInt()
        setMeasuredDimension(mw, mh)
    }

    override fun onDraw(cv: Canvas) {
        val k = width / 170f
        cv.save()
        cv.scale(k, k)
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
        cv.restore()
    }
}

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
