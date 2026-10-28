package com.example.buddy.ui

import android.content.Context
import android.content.res.Configuration
import android.graphics.Color

/** Colours: the same light and dark schemes as the old Flutter theme, plus the accent that drifts through the day. */
object P {
    var dark = false
        private set

    val orange = 0xFFF0883A.toInt()
    val ink = 0xFF1B1530.toInt()
    val violet = 0xFF7A6BC4.toInt()
    val rose = 0xFFEE7BA3.toInt()
    val teal = 0xFF3FB8A5.toInt()
    val sky = 0xFF5B9BF0.toInt()
    val success = 0xFF3DA66B.toInt()
    val warning = 0xFFF2B01E.toInt()

    // soft-glass light palette (the redesign has no dark scheme)
    var primary = 0xFF2C4470.toInt()
    var onPrimary = 0xFFFFFFFF.toInt()
    var primaryContainer = 0x59E08A4A.toInt()
    var onPrimaryContainer = 0xFF4A2A10.toInt()
    var secondaryContainer = 0x66FFFFFF.toInt()
    var onSecondaryContainer = 0xFF24344F.toInt()
    var tertiaryContainer = 0x38E48AA8.toInt()
    var onTertiaryContainer = 0xFF5C1A38.toInt()
    var error = 0xFFC93A3A.toInt()
    var errorContainer = 0x40E06A6A.toInt()
    var onErrorContainer = 0xFF5A0F0F.toInt()
    var surface = 0xFFCBD8E6.toInt()
    var onSurface = 0xFF1C2B45.toInt()
    var onSurfaceVariant = 0xFF4A5C7D.toInt()
    var outline = 0xFF7F93B0.toInt()
    var outlineVariant = 0xB3FFFFFF.toInt()
    var low = 0x66FFFFFF.toInt()          // cards
    var container = 0x59FFFFFF.toInt()
    var high = 0x66FFFFFF.toInt()
    var highest = 0x73FFFFFF.toInt()

    fun init(ctx: Context) {
        Theme.apply(ctx)
        val t = Theme.cur
        dark = t.night
        primary = t.navyA
        primaryContainer = Theme.rgb(t.accent, 44); onPrimaryContainer = t.ink
        tertiaryContainer = Theme.rgb(t.accent, 36); onTertiaryContainer = t.ink
        errorContainer = Theme.rgb(error, 40); onErrorContainer = t.ink
        onSurface = t.ink
        onSurfaceVariant = t.ink2
        onSecondaryContainer = t.ink
        outline = mix(t.ink2, 0xFFFFFFFF.toInt(), 0.35f)
    }

    // ---- hour-of-day accent: the app drifts through 24 hues during the day ----
    private val hues = floatArrayOf(
        262f, 258f, 252f, 248f, 270f, 305f, 340f, 12f, 26f, 34f, 46f, 62f,
        100f, 150f, 178f, 195f, 210f, 228f, 275f, 310f, 332f, 318f, 296f, 276f
    )

    fun hourHue(hour: Int, minute: Int): Float {
        val f = hour + minute / 60f
        val i = f.toInt() % 24
        val j = (i + 1) % 24
        var k = f - f.toInt()
        k = k * k * (3 - 2 * k)
        val a = hues[i]; val b = hues[j]
        val d = ((b - a + 540f) % 360f) - 180f
        return (a + d * k + 360f) % 360f
    }

    private fun hsv(h: Float, s: Float, v: Float) = Color.HSVToColor(floatArrayOf(h, s, v))

    /** Pill behind the selected tab, chips. */
    fun accentContainer(hour: Int, minute: Int) = hsv(hourHue(hour, minute), if (dark) 0.55f else 0.26f, if (dark) 0.40f else 1f)
    fun onAccentContainer(hour: Int, minute: Int) =
        if (dark) hsv(hourHue(hour, minute), 0.15f, 1f) else hsv(hourHue(hour, minute), 0.80f, 0.34f)
    fun accent(hour: Int, minute: Int) = hsv(hourHue(hour, minute), 0.65f, 0.95f)
    fun accentLight(hour: Int, minute: Int) = hsv(hourHue(hour, minute), 0.28f, 1f)

    fun mix(a: Int, b: Int, t: Float): Int {
        val k = t.coerceIn(0f, 1f)
        return Color.argb(
            (Color.alpha(a) + (Color.alpha(b) - Color.alpha(a)) * k).toInt(),
            (Color.red(a) + (Color.red(b) - Color.red(a)) * k).toInt(),
            (Color.green(a) + (Color.green(b) - Color.green(a)) * k).toInt(),
            (Color.blue(a) + (Color.blue(b) - Color.blue(a)) * k).toInt()
        )
    }

    fun alpha(c: Int, a: Int) = (a.coerceIn(0, 255) shl 24) or (c and 0xFFFFFF)
}
