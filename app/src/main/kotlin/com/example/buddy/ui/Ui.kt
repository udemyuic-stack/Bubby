package com.example.buddy.ui

import android.app.AlertDialog
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.InputType
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import com.example.buddy.R

const val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
const val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT

fun Context.dp(v: Int): Int = (v * resources.displayMetrics.density + 0.5f).toInt()
fun Context.dpf(v: Float): Float = v * resources.displayMetrics.density

fun Context.rounded(fill: Int, radiusDp: Float, stroke: Int = 0, strokeDp: Float = 1f): GradientDrawable =
    GradientDrawable().apply {
        setColor(fill)
        cornerRadius = dpf(radiusDp)
        if (stroke != 0) setStroke(maxOf(1, (strokeDp * resources.displayMetrics.density).toInt()), stroke)
    }

enum class T { Headline, Title, Body, Small, Label }

fun Context.text(s: CharSequence, style: T = T.Body, color: Int = 0): TextView = TextView(this).apply {
    this.text = s
    when (style) {
        T.Headline -> { setTextSize(TypedValue.COMPLEX_UNIT_SP, 36f); typeface = glassFont(thin = true); letterSpacing = 0.015f; setTextColor(Glass.TEXT) }
        T.Title -> { setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f); typeface = glassFont(bold = true); setTextColor(Glass.TEXT) }
        T.Body -> { setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f); typeface = glassFont(); setTextColor(Glass.TEXT); setLineSpacing(0f, 1.12f) }
        T.Small -> { setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f); typeface = glassFont(); setTextColor(Glass.TEXT2); setLineSpacing(0f, 1.1f) }
        T.Label -> { setTextSize(TypedValue.COMPLEX_UNIT_SP, 13.5f); typeface = glassFont(bold = true); setTextColor(Glass.TEXT) }
    }
    if (color != 0) setTextColor(color)
    includeFontPadding = false
}

fun Context.icon(res: Int, tint: Int = P.onSurface, sizeDp: Int = 24): ImageView = ImageView(this).apply {
    setImageResource(res)
    imageTintList = ColorStateList.valueOf(tint)
    layoutParams = LinearLayout.LayoutParams(dp(sizeDp), dp(sizeDp))
}

/** Adds [v] to a LinearLayout with sizes and margins in dp. */
fun LinearLayout.add(
    v: View, w: Int = MATCH, h: Int = WRAP, weight: Float = 0f,
    top: Int = 0, bottom: Int = 0, start: Int = 0, end: Int = 0
): View {
    addView(v, LinearLayout.LayoutParams(w, h, weight).apply { setMargins(context.dp(start), context.dp(top), context.dp(end), context.dp(bottom)) })
    return v
}

fun Context.col(pad: Int = 0, block: LinearLayout.() -> Unit = {}): LinearLayout = LinearLayout(this).apply {
    orientation = LinearLayout.VERTICAL
    if (pad != 0) setPadding(dp(pad), dp(pad), dp(pad), dp(pad))
    block()
}

fun Context.row(gravity: Int = Gravity.CENTER_VERTICAL, block: LinearLayout.() -> Unit = {}): LinearLayout = LinearLayout(this).apply {
    orientation = LinearLayout.HORIZONTAL
    isBaselineAligned = false
    this.gravity = gravity
    block()
}

/** Gives a view a quick squash when pressed and calls [onClick] on release inside it. */
fun View.press(onClick: () -> Unit) = pressLong(null, onClick)

fun View.pressLong(onLong: (() -> Unit)?, onClick: () -> Unit) {
    isClickable = true
    var longFired = false
    val long = Runnable { longFired = true; performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS); onLong?.invoke() }
    setOnTouchListener { v, e ->
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                longFired = false
                v.animate().scaleX(0.97f).scaleY(0.97f).setDuration(80).start()
                if (onLong != null) v.postDelayed(long, 550)
            }
            MotionEvent.ACTION_MOVE -> {
                if (e.x !in -20f..(v.width + 20f) || e.y !in -20f..(v.height + 20f)) v.removeCallbacks(long)
            }
            MotionEvent.ACTION_UP -> {
                v.removeCallbacks(long)
                v.animate().scaleX(1f).scaleY(1f).setDuration(120).start()
                if (!longFired && e.x in 0f..v.width.toFloat() && e.y in 0f..v.height.toFloat()) { v.performClick(); onClick() }
            }
            MotionEvent.ACTION_CANCEL -> { v.removeCallbacks(long); v.animate().scaleX(1f).scaleY(1f).setDuration(120).start() }
        }
        true
    }
}

fun Context.card(
    pad: Int = 16, fill: Int = P.low, stroke: Int = P.outlineVariant, radius: Float = 26f,
    onClick: (() -> Unit)? = null, onLong: (() -> Unit)? = null, block: LinearLayout.() -> Unit = {}
): LinearLayout = col(pad) {
    val base = glass(GlassKind.Base, radius)
    background = if (fill == P.low) base
    else android.graphics.drawable.LayerDrawable(arrayOf(base, rounded(fill, radius))).also { it.setLayerInset(1, 1, 1, 1, 1) }
    if (onClick != null) pressLong(onLong, onClick)
    block()
}

enum class BtnStyle { Filled, Tonal, Outline, Text }

fun Context.button(label: String, style: BtnStyle = BtnStyle.Filled, iconRes: Int = 0, onClick: () -> Unit): View {
    val fg = when (style) {
        BtnStyle.Filled -> Glass.white(1f)
        BtnStyle.Tonal -> Glass.TEXT
        BtnStyle.Outline -> Glass.TEXT
        BtnStyle.Text -> P.primary
    }
    return row(Gravity.CENTER) {
        minimumHeight = dp(48)
        setPadding(dp(20), dp(10), dp(20), dp(10))
        background = when (style) {
            BtnStyle.Filled -> glass(GlassKind.Navy, 24f)
            BtnStyle.Tonal -> glass(GlassKind.Base, 24f)
            BtnStyle.Outline -> rounded(0, 24f, P.outline)
            BtnStyle.Text -> null
        }
        if (iconRes != 0) add(icon(iconRes, fg, 20), WRAP, WRAP, end = 8)
        add(text(label, T.Label, fg).apply { setTextSize(TypedValue.COMPLEX_UNIT_SP, 14.5f) }, WRAP, WRAP)
        press(onClick)
    }
}

fun Context.section(title: String, iconRes: Int = 0): View = row {
    add(View(this@section).apply { background = rounded(Glass.ACCENT, 3f) }, dp(4), dp(22), end = 10)
    if (iconRes != 0) add(icon(iconRes, Glass.TEXT_LABEL, 20), WRAP, WRAP, end = 8)
    add(text(title, T.Title), WRAP, WRAP)
}.also { it.setPadding(0, dp(18), 0, dp(10)) }

fun Context.chip(label: String, selected: Boolean, onClick: () -> Unit): View = row(Gravity.CENTER) {
    setPadding(dp(14), dp(8), dp(14), dp(8))
    background = glass(if (selected) GlassKind.Active else GlassKind.Dim, 20f)
    add(text(label, T.Label, if (selected) Glass.TEXT else Glass.TEXT_LABEL), WRAP, WRAP)
    press(onClick)
}

fun Context.switchRow(title: String, subtitle: String?, checked: Boolean, onChange: (Boolean) -> Unit): View = row {
    setPadding(0, dp(8), 0, dp(8))
    val texts = col {
        add(text(title, T.Body), MATCH, WRAP)
        if (!subtitle.isNullOrEmpty()) add(text(subtitle, T.Small), MATCH, WRAP, top = 3)
    }
    add(texts, 0, WRAP, 1f, end = 12)
    val cb = onChange
    val sw = GlassToggle(this@switchRow).apply {
        isChecked = checked
        this.onChange = { v -> cb(v) }
    }
    add(sw, dp(50), dp(30))
    setOnClickListener { sw.toggle() }
}

/** A tappable row: title, optional subtitle, chevron. */
fun Context.linkRow(title: String, subtitle: String?, iconRes: Int = 0, tint: Int = P.primary, onClick: () -> Unit): View = row {
    setPadding(dp(4), dp(10), dp(4), dp(10))
    if (iconRes != 0) {
        add(row(Gravity.CENTER) {
            background = gradient(intArrayOf(P.mix(tint, 0xFFFFFFFF.toInt(), 0.28f), P.mix(tint, 0xFF1C2B45.toInt(), 0.12f)), 16f)
            add(icon(iconRes, 0xFFFFFFFF.toInt(), 22), WRAP, WRAP)
        }, dp(46), dp(46), end = 12)
    }
    val texts = col {
        add(text(title, T.Body), MATCH, WRAP)
        if (!subtitle.isNullOrEmpty()) add(text(subtitle, T.Small), MATCH, WRAP, top = 2)
    }
    add(texts, 0, WRAP, 1f)
    add(icon(R.drawable.ic_chevron_right, P.onSurfaceVariant, 22), WRAP, WRAP, start = 6)
    press(onClick)
}

fun Context.field(hint: String, value: String = "", multiline: Boolean = false, number: Boolean = false, secret: Boolean = false): EditText =
    EditText(this).apply {
        setText(value)
        this.hint = hint
        setHintTextColor(Glass.TEXT2)
        setTextColor(Glass.TEXT)
        typeface = glassFont()
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
        setPadding(dp(16), dp(12), dp(16), dp(12))
        background = rounded(Glass.white(.35f), 16f, Glass.white(.7f))
        inputType = when {
            number -> InputType.TYPE_CLASS_NUMBER
            secret -> InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            multiline -> InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            else -> InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
        }
        if (multiline) { minLines = 3; gravity = Gravity.TOP }
    }

fun Context.toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_SHORT).show()

fun Context.dialogBuilder(): AlertDialog.Builder = AlertDialog.Builder(
    this,
    if (P.dark) android.R.style.Theme_DeviceDefault_Dialog_Alert else android.R.style.Theme_DeviceDefault_Light_Dialog_Alert
)

fun Context.confirm(title: String, message: String, ok: String, cancel: String, onOk: () -> Unit) {
    dialogBuilder().setTitle(title).setMessage(message)
        .setPositiveButton(ok) { _, _ -> onOk() }
        .setNegativeButton(cancel, null)
        .show()
}

fun Context.scroll(content: View): ScrollView = ScrollView(this).apply {
    isFillViewport = true
    overScrollMode = View.OVER_SCROLL_NEVER
    addView(content, FrameLayout.LayoutParams(MATCH, WRAP))
}

fun Context.space(hDp: Int): View = View(this).apply { layoutParams = LinearLayout.LayoutParams(MATCH, dp(hDp)) }

/** A glass capsule with a glowing status dot: "AI", "Microphone". */
fun Context.pill(label: String, color: Int, dot: Boolean = true, onClick: (() -> Unit)? = null): View = row(Gravity.CENTER) {
    setPadding(dp(13), dp(8), dp(13), dp(8))
    background = glass(GlassKind.Base, 30f)
    if (dot) add(DotView(this@pill, color), dp(9), dp(9), end = 7)
    add(text(label, T.Small, 0xFF24344F.toInt()).apply { textSize = 12f; typeface = glassFont(bold = false) }, WRAP, WRAP)
    if (onClick != null) press(onClick)
}

/** A small dot with a soft glow. */
class DotView(c: Context, private val colour: Int) : View(c) {
    private val p = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
    override fun onDraw(cv: android.graphics.Canvas) {
        val r = width / 2f
        p.color = P.alpha(colour, 70); cv.drawCircle(r, r, r, p)
        p.color = colour; cv.drawCircle(r, r, r * .72f, p)
    }
}

/** Glass switch 50x30: navy track when on, glass when off, white knob. */
class GlassToggle(c: Context) : View(c) {
    var isChecked = false
        set(v) { field = v; pos = if (v) 1f else 0f; invalidate() }
    var onChange: ((Boolean) -> Unit)? = null
    private var pos = 0f
    private val p = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
    private val on = GlassDrawable(GlassKind.Navy, 15f, null, false, c.resources.displayMetrics.density)
    private val off = GlassDrawable(GlassKind.Dim, 15f, null, false, c.resources.displayMetrics.density)
    fun toggle() {
        isChecked = !isChecked
        onChange?.invoke(isChecked)
    }
    override fun onDraw(cv: android.graphics.Canvas) {
        val d = if (isChecked) on else off
        d.setBounds(0, 0, width, height); d.draw(cv)
        val r = height * .4f
        val x = height / 2f + pos * (width - height)
        p.color = 0x33000000; cv.drawCircle(x, height / 2f + 1.5f, r, p)
        p.color = if (isChecked) 0xFFFFFFFF.toInt() else 0xFFF4F8FC.toInt(); cv.drawCircle(x, height / 2f, r, p)
    }
}

fun Context.gradient(colors: IntArray, radiusDp: Float, orientation: GradientDrawable.Orientation = GradientDrawable.Orientation.TL_BR): GradientDrawable =
    GradientDrawable(orientation, colors).apply { cornerRadius = dpf(radiusDp) }

/** The mascot with a title and a hint, for lists with nothing in them. */
fun Context.emptyState(title: String, message: String, bubble: String? = null): View = col {
    gravity = Gravity.CENTER_HORIZONTAL
    setPadding(dp(24), dp(24), dp(24), dp(24))
    if (bubble != null) {
        add(row(Gravity.CENTER) {
            background = glass(GlassKind.Base, 25f)
            setPadding(dp(24), dp(14), dp(24), dp(14))
            add(text(bubble, T.Body).apply { textSize = 15f }, WRAP, WRAP)
        }, WRAP, WRAP, top = 40)
        add(View(this@emptyState).apply { rotation = 45f; background = rounded(Glass.white(.5f), 3f, Glass.white(.6f)) }, dp(14), dp(14), top = -6)
    }
    // the glass mascot needs a dark patch behind it to be seen on the light page
    val stage = FrameLayout(this@emptyState).apply {
        background = SpotDrawable()
        add(MochiView(this@emptyState), dp(170), dp(136), Gravity.CENTER)
    }
    add(stage, dp(240), dp(220))
    add(text(title, T.Title).apply { gravity = Gravity.CENTER; textSize = 22f; typeface = glassFont() }, MATCH, WRAP, top = 8)
    add(text(message, T.Small).apply { gravity = Gravity.CENTER; textSize = 15f }, MATCH, WRAP, top = 6)
}

private fun FrameLayout.add(v: View, w: Int, h: Int, gravity: Int) = addView(v, FrameLayout.LayoutParams(w, h, gravity))

/** The soft dark patch under the glass mascot. */
class SpotDrawable : android.graphics.drawable.Drawable() {
    private val p = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
    override fun draw(c: android.graphics.Canvas) {
        val b = bounds
        val r = b.width() * .5f
        p.shader = android.graphics.RadialGradient(0f, 0f, r,
            intArrayOf(android.graphics.Color.argb(199, 60, 40, 48), android.graphics.Color.argb(102, 60, 40, 48), 0), floatArrayOf(0f, .6f, 1f), android.graphics.Shader.TileMode.CLAMP)
        c.save(); c.translate(b.exactCenterX(), b.exactCenterY()); c.scale(1f, b.height() / b.width().toFloat())
        c.drawCircle(0f, 0f, r, p); c.restore()
    }
    override fun setAlpha(a: Int) {}
    override fun setColorFilter(cf: android.graphics.ColorFilter?) {}
    @Suppress("OVERRIDE_DEPRECATION")
    override fun getOpacity() = android.graphics.PixelFormat.TRANSLUCENT
}
