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

    var primary = orange
    var onPrimary = ink
    var primaryContainer = 0xFFFFE6C8.toInt()
    var onPrimaryContainer = 0xFF4A2300.toInt()
    var secondaryContainer = 0xFFE7E2FA.toInt()
    var onSecondaryContainer = 0xFF2A2159.toInt()
    var tertiaryContainer = 0xFFFFDCE8.toInt()
    var onTertiaryContainer = 0xFF5C1A38.toInt()
    var error = 0xFFD13B3B.toInt()
    var errorContainer = 0xFFFFDAD6.toInt()
    var onErrorContainer = 0xFF5A0F0F.toInt()
    var surface = 0xFFFFFAF3.toInt()
    var onSurface = ink
    var onSurfaceVariant = 0xFF6A607A.toInt()
    var outline = 0xFF8E819E.toInt()
    var outlineVariant = 0xFFEBDFD2.toInt()
    var low = 0xFFFFFFFF.toInt()          // cards
    var container = 0xFFFFF4E6.toInt()
    var high = 0xFFFFEEDC.toInt()
    var highest = 0xFFFBE8D2.toInt()

    fun init(ctx: Context) {
        dark = (ctx.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        if (dark) {
            onPrimary = 0xFF2B1400.toInt()
            primaryContainer = 0xFF5A2E0C.toInt(); onPrimaryContainer = 0xFFFFE6C8.toInt()
            secondaryContainer = 0xFF3B3377.toInt(); onSecondaryContainer = 0xFFE7E2FA.toInt()
            tertiaryContainer = 0xFF6B2347.toInt(); onTertiaryContainer = 0xFFFFD9E6.toInt()
            error = 0xFFFF8A80.toInt(); errorContainer = 0xFF7A2020.toInt(); onErrorContainer = 0xFFFFDAD6.toInt()
            surface = 0xFF130F1F.toInt(); onSurface = 0xFFF3ECFA.toInt(); onSurfaceVariant = 0xFFB9AFCB.toInt()
            outline = 0xFF6E6585.toInt(); outlineVariant = 0xFF3A3256.toInt()
            low = 0xFF1C1631.toInt(); container = 0xFF241D3E.toInt(); high = 0xFF2D2549.toInt(); highest = 0xFF372E57.toInt()
        } else {
            onPrimary = ink
            primaryContainer = 0xFFFFE6C8.toInt(); onPrimaryContainer = 0xFF4A2300.toInt()
            secondaryContainer = 0xFFE7E2FA.toInt(); onSecondaryContainer = 0xFF2A2159.toInt()
            tertiaryContainer = 0xFFFFDCE8.toInt(); onTertiaryContainer = 0xFF5C1A38.toInt()
            error = 0xFFD13B3B.toInt(); errorContainer = 0xFFFFDAD6.toInt(); onErrorContainer = 0xFF5A0F0F.toInt()
            surface = 0xFFFFFAF3.toInt(); onSurface = ink; onSurfaceVariant = 0xFF6A607A.toInt()
            outline = 0xFF8E819E.toInt(); outlineVariant = 0xFFEBDFD2.toInt()
            low = 0xFFFFFFFF.toInt(); container = 0xFFFFF4E6.toInt(); high = 0xFFFFEEDC.toInt(); highest = 0xFFFBE8D2.toInt()
        }
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
