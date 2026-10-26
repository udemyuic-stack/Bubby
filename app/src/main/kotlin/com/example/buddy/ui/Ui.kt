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
        T.Headline -> { setTextSize(TypedValue.COMPLEX_UNIT_SP, 28f); setTypeface(typeface, Typeface.BOLD); setTextColor(P.onSurface) }
        T.Title -> { setTextSize(TypedValue.COMPLEX_UNIT_SP, 17f); setTypeface(typeface, Typeface.BOLD); setTextColor(P.onSurface) }
        T.Body -> { setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f); setTextColor(P.onSurface); setLineSpacing(0f, 1.12f) }
        T.Small -> { setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f); setTextColor(P.onSurfaceVariant); setLineSpacing(0f, 1.1f) }
        T.Label -> { setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f); setTypeface(typeface, Typeface.BOLD); setTextColor(P.onSurface) }
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
    pad: Int = 16, fill: Int = P.low, stroke: Int = P.outlineVariant, radius: Float = 22f,
    onClick: (() -> Unit)? = null, onLong: (() -> Unit)? = null, block: LinearLayout.() -> Unit = {}
): LinearLayout = col(pad) {
    background = rounded(fill, radius, stroke)
    if (onClick != null) pressLong(onLong, onClick)
    block()
}

enum class BtnStyle { Filled, Tonal, Outline, Text }

fun Context.button(label: String, style: BtnStyle = BtnStyle.Filled, iconRes: Int = 0, onClick: () -> Unit): View {
    val (bg, fg, stroke) = when (style) {
        BtnStyle.Filled -> Triple(P.primary, P.onPrimary, 0)
        BtnStyle.Tonal -> Triple(P.secondaryContainer, P.onSecondaryContainer, 0)
        BtnStyle.Outline -> Triple(0, P.onSurface, P.outline)
        BtnStyle.Text -> Triple(0, P.primary, 0)
    }
    return row(Gravity.CENTER) {
        minimumHeight = dp(46)
        setPadding(dp(20), dp(10), dp(20), dp(10))
        background = if (bg == 0 && stroke == 0) null else rounded(bg, 30f, stroke)
        if (iconRes != 0) add(icon(iconRes, fg, 20), WRAP, WRAP, end = 8)
        add(text(label, T.Label, fg).apply { setTextSize(TypedValue.COMPLEX_UNIT_SP, 14.5f) }, WRAP, WRAP)
        press(onClick)
    }
}

fun Context.section(title: String, iconRes: Int = 0): View = row {
    add(View(this@section).apply { background = rounded(P.primary, 3f) }, dp(4), dp(18), end = 10)
    if (iconRes != 0) add(icon(iconRes, P.onSurfaceVariant, 18), WRAP, WRAP, end = 6)
    add(text(title, T.Title), WRAP, WRAP)
}.also { it.setPadding(0, dp(18), 0, dp(8)) }

fun Context.chip(label: String, selected: Boolean, onClick: () -> Unit): View = row(Gravity.CENTER) {
    setPadding(dp(14), dp(8), dp(14), dp(8))
    background = rounded(if (selected) P.primaryContainer else P.low, 20f, if (selected) P.primary else P.outlineVariant, if (selected) 1.5f else 1f)
    add(text(label, T.Label, if (selected) P.onPrimaryContainer else P.onSurface), WRAP, WRAP)
    press(onClick)
}

fun Context.switchRow(title: String, subtitle: String?, checked: Boolean, onChange: (Boolean) -> Unit): View = row {
    setPadding(0, dp(8), 0, dp(8))
    val texts = col {
        add(text(title, T.Body), MATCH, WRAP)
        if (!subtitle.isNullOrEmpty()) add(text(subtitle, T.Small), MATCH, WRAP, top = 3)
    }
    add(texts, 0, WRAP, 1f, end = 12)
    val sw = Switch(this@switchRow).apply {
        isChecked = checked
        val states = arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf())
        thumbTintList = ColorStateList(states, intArrayOf(0xFFFFFFFF.toInt(), P.mix(P.outline, 0xFFFFFFFF.toInt(), 0.5f)))
        trackTintList = ColorStateList(states, intArrayOf(P.primary, P.outlineVariant))
        setOnCheckedChangeListener { _, v -> onChange(v) }
    }
    add(sw, WRAP, WRAP)
    setOnClickListener { sw.toggle() }
}

/** A tappable row: title, optional subtitle, chevron. */
fun Context.linkRow(title: String, subtitle: String?, iconRes: Int = 0, tint: Int = P.primary, onClick: () -> Unit): View = row {
    setPadding(dp(4), dp(10), dp(4), dp(10))
    if (iconRes != 0) {
        add(row(Gravity.CENTER) {
            background = rounded(P.alpha(tint, 40), 14f)
            add(icon(iconRes, tint, 22), WRAP, WRAP)
        }, dp(44), dp(44), end = 12)
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
        setHintTextColor(P.onSurfaceVariant)
        setTextColor(P.onSurface)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
        setPadding(dp(16), dp(12), dp(16), dp(12))
        background = rounded(P.container, 16f, P.outlineVariant)
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

/** Small rounded label with an optional dot: "Assistant is on". */
fun Context.pill(label: String, color: Int, dot: Boolean = true, onClick: (() -> Unit)? = null): View = row(Gravity.CENTER) {
    setPadding(dp(14), dp(8), dp(14), dp(8))
    background = rounded(P.alpha(color, 36), 30f, P.alpha(color, 90))
    if (dot) add(View(this@pill).apply { background = rounded(color, 5f) }, dp(9), dp(9), end = 8)
    add(text(label, T.Label, P.onSurface), WRAP, WRAP)
    if (onClick != null) press(onClick)
}

fun Context.gradient(colors: IntArray, radiusDp: Float, orientation: GradientDrawable.Orientation = GradientDrawable.Orientation.TL_BR): GradientDrawable =
    GradientDrawable(orientation, colors).apply { cornerRadius = dpf(radiusDp) }

/** The mascot with a title and a hint, for lists with nothing in them. */
fun Context.emptyState(title: String, message: String): View = col {
    gravity = Gravity.CENTER_HORIZONTAL
    setPadding(dp(24), dp(32), dp(24), dp(24))
    val b = com.example.buddy.BuddyView(this@emptyState).apply {
        scaleF = 1.5f
        species = Backend.str(this@emptyState, "species", "cat")
        outfit = com.example.buddy.Outfits.parse(Backend.str(this@emptyState, "outfit", ""))
        mood = "happy"
        setActive(false)
    }
    add(b, WRAP, WRAP)
    add(text(title, T.Title).apply { gravity = Gravity.CENTER }, MATCH, WRAP, top = 12)
    add(text(message, T.Small).apply { gravity = Gravity.CENTER }, MATCH, WRAP, top = 6)
}
