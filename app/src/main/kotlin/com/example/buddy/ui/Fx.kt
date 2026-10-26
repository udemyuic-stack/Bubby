package com.example.buddy.ui

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.app.Activity
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.drawable.GradientDrawable
import android.os.SystemClock
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin

// ---- small motions ---------------------------------------------------------------------------------------------------

/** Fades the view in while it slides up a little. */
fun View.fadeSlideIn(delayMs: Long, rise: Float = 18f) {
    alpha = 0f
    translationY = context.dpf(rise)
    animate().alpha(1f).translationY(0f).setStartDelay(delayMs).setDuration(320).setInterpolator(DecelerateInterpolator(1.6f)).start()
}

/** The first children of a page appear one after another. */
fun LinearLayout.staggerChildren(max: Int = 12) {
    for (i in 0 until minOf(childCount, max)) getChildAt(i).fadeSlideIn(i * 45L)
}

/** A quick pop, used when something becomes selected. */
fun View.pop() {
    scaleX = 0.9f; scaleY = 0.9f
    animate().scaleX(1f).scaleY(1f).setDuration(380).setInterpolator(OvershootInterpolator(3.2f)).start()
}

/** Moves a progress bar smoothly to [value] (out of its max). */
fun ProgressBar.animateTo(value: Int) {
    ObjectAnimator.ofInt(this, "progress", progress, value).setDuration(650).apply { interpolator = DecelerateInterpolator(1.8f) }.start()
}

/** A round check with a ring that expands and fades once. */
fun Context.successPulse(sizeDp: Int = 40): View = FrameLayout(this).apply {
    val ring = View(context).apply { background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setStroke(dp(3), P.success); setColor(Color.TRANSPARENT) } }
    addView(ring, FrameLayout.LayoutParams(dp(sizeDp), dp(sizeDp), Gravity.CENTER))
    val check = icon(com.example.buddy.R.drawable.ic_check_circle, P.success, sizeDp)
    addView(check, FrameLayout.LayoutParams(dp(sizeDp), dp(sizeDp), Gravity.CENTER))
    check.scaleX = 0f; check.scaleY = 0f
    check.animate().scaleX(1f).scaleY(1f).setDuration(420).setInterpolator(OvershootInterpolator(3f)).start()
    ring.animate().scaleX(1.8f).scaleY(1.8f).alpha(0f).setDuration(700).setStartDelay(150).start()
}

// ---- confetti --------------------------------------------------------------------------------------------------------

private class ConfettiView(c: Context) : View(c) {
    private val n = 70
    private val seed = FloatArray(n * 4) { Math.random().toFloat() }
    private val colors = intArrayOf(P.orange, P.violet, P.rose, P.teal, P.sky, P.warning)
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val start = SystemClock.uptimeMillis()
    private val total = 2400f

    override fun onDraw(cv: Canvas) {
        val t = (SystemClock.uptimeMillis() - start) / total
        if (t >= 1f) return
        for (i in 0 until n) {
            val a = seed[i * 4]; val b = seed[i * 4 + 1]; val s = seed[i * 4 + 2]; val r = seed[i * 4 + 3]
            val ang = (a * 0.9f + 0.05f) * PI.toFloat()           // fan upwards from the bottom centre
            val speed = (0.55f + b * 0.75f) * height
            val x = width / 2f + cos(ang) * speed * t * (if (s < 0.5f) -1f else 1f) * 0.9f + sin(t * 9f + r * 6f) * 14f
            val y = height * 0.72f - sin(ang) * speed * t * 1.1f + 0.5f * height * 1.6f * t * t
            p.color = colors[(i + (r * 6).toInt()) % colors.size]
            p.alpha = (255 * (1f - t * t)).toInt().coerceIn(0, 255)
            cv.save(); cv.translate(x, y); cv.rotate(t * 540f * (r - 0.5f) * 2f + a * 90f)
            val w = context.dpf(5f + s * 4f)
            cv.drawRect(-w, -w * 0.5f, w, w * 0.5f, p)
            cv.restore()
        }
        postInvalidateOnAnimation()
    }
}

/** Confetti over the whole screen for about two seconds, with a short buzz. */
fun Activity.confetti() {
    val decor = window.decorView as? ViewGroup ?: return
    val v = ConfettiView(this)
    decor.addView(v, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
    v.isClickable = false
    v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
    v.postDelayed({ decor.removeView(v) }, 2500)
}

// ---- aura previews ---------------------------------------------------------------------------------------------------

/** A small dark tile with a looping animation of one aura (fire, ice, lightning, hearts, sakura, stars). */
class AuraView(c: Context, private val kind: String) : View(c) {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val bg = RectF()
    private val path = Path()
    private val radius = c.dpf(14f)
    private val n = 9

    private fun h(i: Int, k: Int): Float { val x = sin(i * 12.9898f + k * 78.233f) * 43758.547f; return x - floor(x) }

    override fun onDraw(cv: Canvas) {
        val w = width.toFloat(); val hh = height.toFloat()
        bg.set(0f, 0f, w, hh)
        p.shader = LinearGradient(0f, 0f, 0f, hh, 0xFF2A2060.toInt(), 0xFF14102E.toInt(), Shader.TileMode.CLAMP)
        p.style = Paint.Style.FILL
        cv.drawRoundRect(bg, radius, radius, p)
        p.shader = null
        cv.save(); path.reset(); path.addRoundRect(bg, radius, radius, Path.Direction.CW); cv.clipPath(path)
        val t = (SystemClock.uptimeMillis() % 100000L) / 1000f
        val u = context.dpf(1f)
        for (i in 0 until n) {
            val ph = (t * (0.35f + h(i, 1) * 0.5f) + h(i, 2)) % 1f
            val x = w * (0.12f + 0.76f * h(i, 3))
            when (kind) {
                "fire" -> {
                    val y = hh * (1.05f - ph * 0.95f)
                    p.color = if (i % 2 == 0) 0xFFFF8A3A.toInt() else 0xFFFFD25A.toInt()
                    p.alpha = ((1f - ph) * 230).toInt()
                    cv.drawCircle(x + sin(t * 6f + i) * 3f * u, y, (6f - ph * 4f) * u, p)
                }
                "ice" -> {
                    val y = hh * (ph * 1.1f - 0.05f)
                    p.color = if (i % 2 == 0) 0xFFBFE8FF.toInt() else 0xFFFFFFFF.toInt()
                    p.alpha = 220
                    cv.save(); cv.translate(x + sin(t * 2f + i) * 5f * u, y); cv.rotate(45f + t * 60f)
                    cv.drawRect(-2.5f * u, -2.5f * u, 2.5f * u, 2.5f * u, p); cv.restore()
                }
                "lightning" -> {
                    if (i >= 3) continue
                    val flash = (sin(t * (3f + i) + i * 2f) * 0.5f + 0.5f)
                    if (flash < 0.55f) continue
                    p.style = Paint.Style.STROKE; p.strokeWidth = 2.2f * u; p.strokeCap = Paint.Cap.ROUND; p.strokeJoin = Paint.Join.ROUND
                    p.color = 0xFFFFE066.toInt(); p.alpha = ((flash - 0.55f) / 0.45f * 255).toInt()
                    val bx = w * (0.2f + 0.3f * i)
                    path.reset(); path.moveTo(bx, 0f); path.lineTo(bx - 6f * u, hh * 0.35f); path.lineTo(bx + 5f * u, hh * 0.5f); path.lineTo(bx - 4f * u, hh)
                    cv.drawPath(path, p); p.style = Paint.Style.FILL
                }
                "hearts" -> {
                    val y = hh * (1.05f - ph * 1.1f)
                    p.color = if (i % 2 == 0) 0xFFFF7AA8.toInt() else 0xFFFF4F8A.toInt(); p.alpha = ((1f - ph) * 240).toInt()
                    heart(cv, x + sin(t * 2f + i) * 4f * u, y, (4f + h(i, 4) * 3f) * u)
                }
                "sakura" -> {
                    val y = hh * (ph * 1.1f - 0.05f)
                    p.color = 0xFFFFB7D0.toInt(); p.alpha = 235
                    cv.save(); cv.translate(x + sin(t * 1.6f + i * 2f) * 8f * u, y); cv.rotate(t * 80f + i * 40f)
                    cv.drawOval(-4f * u, -2.2f * u, 4f * u, 2.2f * u, p); cv.restore()
                }
                else -> { // stars
                    val tw = abs(sin(t * (1.2f + h(i, 5)) + i))
                    p.color = 0xFFFFF2A8.toInt(); p.alpha = (90 + tw * 165).toInt()
                    val y = hh * (0.1f + 0.8f * h(i, 6))
                    star(cv, x, y, (2.5f + tw * 3.5f) * u)
                }
            }
        }
        cv.restore()
        postInvalidateOnAnimation()
    }

    private fun heart(cv: Canvas, x: Float, y: Float, s: Float) {
        path.reset()
        path.moveTo(x, y + s * 0.9f)
        path.cubicTo(x - s * 2.2f, y - s * 0.2f, x - s * 0.9f, y - s * 1.6f, x, y - s * 0.5f)
        path.cubicTo(x + s * 0.9f, y - s * 1.6f, x + s * 2.2f, y - s * 0.2f, x, y + s * 0.9f)
        cv.drawPath(path, p)
    }

    private fun star(cv: Canvas, x: Float, y: Float, s: Float) {
        path.reset()
        path.moveTo(x, y - s); path.quadTo(x, y, x + s, y); path.quadTo(x, y, x, y + s); path.quadTo(x, y, x - s, y); path.quadTo(x, y, x, y - s)
        cv.drawPath(path, p)
    }
}

// ---- 3D tilt ---------------------------------------------------------------------------------------------------------

/** An image that can show a soft highlight sliding across it, only where the picture itself is opaque. */
class SheenImageView(c: Context) : ImageView(c) {
    var sheen = 0f   // -1 .. 1, where the highlight is
    private val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_ATOP) }

    override fun onDraw(cv: Canvas) {
        val sc = cv.saveLayer(0f, 0f, width.toFloat(), height.toFloat(), null)
        super.onDraw(cv)
        val cx = width * (0.5f + sheen * 0.6f)
        val half = width * 0.22f
        p.shader = LinearGradient(cx - half, 0f, cx + half, height * 0.35f,
            intArrayOf(0x00FFFFFF, 0x66FFFFFF, 0x00FFFFFF), floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP)
        cv.drawRect(0f, 0f, width.toFloat(), height.toFloat(), p)
        cv.restoreToCount(sc)
    }
}

/**
 * The outfit preview: drag to turn it in perspective (it springs back), a gentle idle sway, a swing when the pet or outfit
 * changes, a highlight that slides across it and a contact shadow that moves the other way.
 */
class Tilt3D(c: Context, private val still: Boolean = false) : FrameLayout(c) {
    private val glow = View(c)
    private val shadow = View(c)
    val image = SheenImageView(c)
    private var touching = false
    private var touchX = 0f
    private var touchY = 0f
    private var blend = 0f          // 1 while a finger turns it, eases back to 0
    private var kick = 0f           // the swing on change
    private var kickStart = 0L
    private var lastTouchX = 0f
    private var lastTouchY = 0f
    private val loop = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = 7000; repeatCount = ValueAnimator.INFINITE; interpolator = android.view.animation.LinearInterpolator()
        addUpdateListener { frame() }
    }
    private var release: ValueAnimator? = null

    init {
        clipChildren = false
        glow.background = GradientDrawable().apply {
            gradientType = GradientDrawable.RADIAL_GRADIENT; gradientRadius = c.dpf(100f)
            colors = intArrayOf(P.alpha(P.primary, 110), P.alpha(P.primary, 0))
        }
        shadow.background = GradientDrawable().apply {
            gradientType = GradientDrawable.RADIAL_GRADIENT; gradientRadius = c.dpf(60f)
            colors = intArrayOf(0x55000000, 0x00000000)
        }
        addView(glow, LayoutParams(c.dp(200), c.dp(200), Gravity.CENTER))
        addView(shadow, LayoutParams(c.dp(120), c.dp(22), Gravity.CENTER_HORIZONTAL or Gravity.BOTTOM).apply { bottomMargin = c.dp(6) })
        image.scaleType = ImageView.ScaleType.FIT_CENTER
        image.cameraDistance = c.dpf(800f)
        addView(image, LayoutParams(c.dp(170), c.dp(170), Gravity.CENTER))
    }

    fun setBitmap(b: android.graphics.Bitmap?) { image.setImageBitmap(b) }

    /** A quick swing, called when the pet or outfit changed. */
    fun swing() { kickStart = SystemClock.uptimeMillis(); kick = 1f }

    private fun frame() {
        val now = SystemClock.uptimeMillis()
        val t = loop.animatedFraction * 2f * PI.toFloat()
        // idle sway and glow breathing
        var ry = if (still) 0f else sin(t) * 7f
        var rx = if (still) 0f else cos(t * 2f) * 3f
        // the swing: a damped wobble over about 700 ms
        if (kick > 0f) {
            val k = (now - kickStart) / 700f
            if (k >= 1f) kick = 0f else ry += sin(k * 3f * PI.toFloat()) * (1f - k) * 24f
        }
        if (blend > 0f) {
            ry = ry * (1f - blend) + touchY * blend
            rx = rx * (1f - blend) + touchX * blend
        }
        image.rotationY = ry
        image.rotationX = rx
        image.sheen = (ry / 25f).coerceIn(-1f, 1f)
        image.invalidate()
        shadow.translationX = -ry * 1.4f
        shadow.scaleX = 1f - abs(ry) / 70f
        shadow.alpha = 0.85f - abs(rx) / 60f
        val breathe = 1f + 0.05f * (0.5f + 0.5f * sin(t * 4f))
        glow.scaleX = breathe; glow.scaleY = breathe
    }

    override fun onAttachedToWindow() { super.onAttachedToWindow(); loop.start() }
    override fun onDetachedFromWindow() { loop.cancel(); super.onDetachedFromWindow() }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                parent?.requestDisallowInterceptTouchEvent(true)
                release?.cancel(); touching = true
                val nx = ((e.x / width) - 0.5f).coerceIn(-0.5f, 0.5f)
                val ny = ((e.y / height) - 0.5f).coerceIn(-0.5f, 0.5f)
                touchY = nx * 55f; touchX = -ny * 55f
                blend = 1f
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                touching = false
                release = ValueAnimator.ofFloat(blend, 0f).apply {
                    duration = 700; interpolator = OvershootInterpolator(2.2f)
                    addUpdateListener { blend = (it.animatedValue as Float).coerceAtLeast(0f) }
                    start()
                }
            }
        }
        return true
    }
}
