package com.example.buddy.ui.home

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import com.example.buddy.ui.Art

fun interface Part {
    fun draw(c: Canvas, t: Float, ms: Long)
}

class Actor(
    val width: Float,
    val height: Float,
    private val view: FloatArray,
    private val parts: List<Part>
) {
    fun draw(c: Canvas, t: Float, ms: Long) {
        c.save()
        c.scale(width / view[2], height / view[3])
        c.translate(-(view[0] + view[2] / 2f), -(view[1] + view[3] / 2f))
        for (part in parts) part.draw(c, t, ms)
        c.restore()
    }
}

object NewYearActors {
    private const val SWING = 25f

    private fun tri(t: Float, period: Float) = Ease.smooth(Ease.wave(t / (2f * period)))

    private fun art(name: String, body: String): Part {
        val drawing = Art(name, "<svg>$body</svg>")
        return Part { c, t, _ -> drawing.draw(c, t) }
    }

    private fun moved(inner: List<Part>, move: (Canvas, Float, Long) -> Unit) = Part { c, t, ms ->
        c.save()
        move(c, t, ms)
        for (p in inner) p.draw(c, t, ms)
        c.restore()
    }

    private fun leg(d: String, color: Int, width: Float, reverse: Boolean, period: Float): Part {
        val path = Path()
        val bounds = RectF()
        val nums = Regex("-?\\d+(\\.\\d+)?").findAll(d).map { it.value.toFloat() }.toList()
        for (i in 0 until nums.size / 2) {
            if (i % 2 == 0) path.moveTo(nums[2 * i], nums[2 * i + 1]) else path.lineTo(nums[2 * i], nums[2 * i + 1])
        }
        path.computeBounds(bounds, true)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeWidth = width
            this.color = color
        }
        return Part { c, t, _ ->
            val k = tri(t, period)
            val angle = if (reverse) SWING - 2f * SWING * k else -SWING + 2f * SWING * k
            c.save()
            c.rotate(angle, bounds.centerX(), bounds.top)
            c.drawPath(path, paint)
            c.restore()
        }
    }

    private fun deerLegs(): List<Part> = listOf(
        leg("M-12 6 L-16 22", DEER_LEG, 3f, false, 0.22f),
        leg("M-6 6 L-2 22", DEER_LEG, 3f, true, 0.22f),
        leg("M10 6 L6 22", DEER_LEG, 3f, false, 0.22f),
        leg("M15 6 L19 22", DEER_LEG, 3f, true, 0.22f)
    )

    private fun deer(x: Float, nose: String): Part {
        val body = art(
            "ny-deer",
            """<ellipse cx="0" cy="0" rx="20" ry="9" fill="#9a6a3e"/><ellipse cx="2" cy="4" rx="14" ry="4" fill="#c9a07a"/>
<path d="M-14 -4 C-18 -10 -22 -14 -24 -18 L-18 -20 C-16 -14 -12 -10 -8 -6Z" fill="#9a6a3e"/>
<ellipse cx="-26" cy="-20" rx="8" ry="6" fill="#9a6a3e"/><circle cx="-33" cy="-19" r="3" fill="$nose"/>
<circle cx="-26" cy="-22" r="1.2" fill="#111"/>
<path d="M-24 -25 L-22 -36 L-26 -42 M-22 -36 L-16 -40 M-20 -25 L-16 -34 L-10 -36" fill="none" stroke="#6b4a2a" stroke-width="1.8" stroke-linecap="round"/>
<path d="M19 -3 L24 -6" stroke="#9a6a3e" stroke-width="3" stroke-linecap="round"/>"""
        )
        return moved(deerLegs() + body) { c, _, _ -> c.translate(x, 0f) }
    }

    fun sleigh(): Actor {
        val harness = art("ny-harness", """<path d="M-150 -2 L-104 -2 M-90 -2 L-44 -2 M-30 -2 L14 -8" stroke="#c9a24a" stroke-width="1.4" fill="none"/>""")
        val ride = art(
            "ny-ride",
            """<g transform="translate(40 0)">
<path d="M-24 18 C-30 18 -32 12 -26 10 L40 10 C50 10 54 4 52 -2" fill="none" stroke="#e6b84a" stroke-width="3" stroke-linecap="round"/>
<path d="M-22 8 L-26 -14 C-26 -20 -18 -20 -16 -14 L-12 -4 L34 -4 L40 -18 L48 -18 L44 8Z" fill="#d42a2a" stroke="#ffd34d" stroke-width="1.4"/>
<ellipse cx="30" cy="-18" rx="12" ry="11" fill="#8a5a32"/><rect x="22" y="-34" width="10" height="9" fill="#4dd38a"/>
<rect x="32" y="-32" width="9" height="8" fill="#5aa8ff"/><circle cx="6" cy="-14" r="11" fill="#d42a2a"/>
<path d="M-5 -10 L17 -10" stroke="#fff" stroke-width="3"/><circle cx="2" cy="-30" r="7" fill="#ffd2b0"/>
<path d="M-5 -28 C-6 -18 4 -14 9 -24" fill="#fff"/><circle cx="0" cy="-31" r="1" fill="#111"/>
<path d="M-6 -33 C-4 -46 10 -46 14 -36 L8 -34Z" fill="#d42a2a"/><circle cx="16" cy="-36" r="3" fill="#fff"/>
<path d="M-6 -33 L9 -34" stroke="#fff" stroke-width="3" stroke-linecap="round"/>
<path d="M-3 -16 L-12 -6" stroke="#d42a2a" stroke-width="4" stroke-linecap="round"/></g>"""
        )
        val all = moved(listOf(harness, deer(-160f, "#ff3b3b"), deer(-100f, "#3a2418"), deer(-40f, "#3a2418"), ride)) { c, t, _ ->
            c.translate(0f, -2f + 5f * tri(t, 0.6f))
        }
        return Actor(310f, 110f, floatArrayOf(-200f, -60f, 310f, 110f), listOf(all))
    }

    fun gift(index: Int): Actor {
        val (box, ribbon) = GIFTS[index]
        val svg = """<rect x="-14" y="-9" width="28" height="22" rx="2" fill="$box"/>
<rect x="-16" y="-14" width="32" height="7" rx="2" fill="$box" stroke="rgba(0,0,0,.15)"/>
<rect x="-3" y="-14" width="6" height="27" fill="$ribbon"/><rect x="-14" y="0" width="28" height="4" fill="$ribbon" opacity=".85"/>
<path d="M0 -14 C-10 -24 -14 -14 -4 -13Z M0 -14 C10 -24 14 -14 4 -13Z" fill="$ribbon"/>
<rect x="-14" y="-9" width="28" height="3" fill="#000" opacity=".12"/>"""
        return Actor(34f, 38f, floatArrayOf(-17f, -22f, 34f, 38f), listOf(art("ny-gift$index", svg)))
    }

    fun rabbit(): Actor {
        val body = art(
            "ny-rabbit",
            """<ellipse cx="-2" cy="0" rx="14" ry="11" fill="#f3f3f6"/><circle cx="-15" cy="-2" r="4" fill="#fff"/>
<circle cx="10" cy="-10" r="8" fill="#f3f3f6"/>
<ellipse cx="6" cy="-26" rx="3" ry="11" fill="#f3f3f6" transform="rotate(-12 6 -26)"/>
<ellipse cx="13" cy="-26" rx="3" ry="11" fill="#f3f3f6" transform="rotate(10 13 -26)"/>
<ellipse cx="6" cy="-26" rx="1.4" ry="7" fill="#ffb6c6" transform="rotate(-12 6 -26)"/>
<circle cx="13" cy="-11" r="1.4" fill="#222"/><circle cx="17.5" cy="-8" r="1.5" fill="#ff8fa8"/>
<ellipse cx="4" cy="10" rx="6" ry="3" fill="#e6e6ec"/>"""
        )
        val hop = moved(listOf(body)) { c, t, _ -> c.translate(0f, -8f * tri(t, 0.36f)) }
        return Actor(46f, 50f, floatArrayOf(-24f, -40f, 48f, 52f), listOf(hop))
    }

    fun fox(): Actor {
        val body = art(
            "ny-fox",
            """<path d="M-18 0 C-30 -4 -34 6 -26 10 C-22 12 -18 8 -16 4Z" fill="#ff8a2a"/><circle cx="-29" cy="6" r="3" fill="#fff"/>
<ellipse cx="0" cy="0" rx="17" ry="8" fill="#ff8a2a"/><ellipse cx="2" cy="4" rx="11" ry="3" fill="#fff3e6"/>
<path d="M12 -4 L26 -6 L30 -2 L22 2 L12 2Z" fill="#ff8a2a"/><path d="M22 -2 L30 -2 L22 2Z" fill="#fff"/>
<circle cx="30" cy="-2" r="1.3" fill="#222"/><path d="M14 -6 L16 -16 L20 -6Z M18 -6 L22 -15 L24 -5Z" fill="#ff8a2a"/>
<circle cx="20" cy="-4" r="1.2" fill="#222"/>"""
        )
        val legs = listOf(
            leg("M-10 6 L-12 14 M-4 6 L-2 14", FOX_LEG, 2.6f, false, 0.22f),
            leg("M6 6 L4 14 M12 5 L14 14", FOX_LEG, 2.6f, true, 0.22f)
        )
        val trot = moved(listOf(body) + legs) { c, t, _ -> c.translate(0f, -2f * tri(t, 0.22f)) }
        return Actor(64f, 40f, floatArrayOf(-34f, -30f, 66f, 42f), listOf(trot))
    }

    fun penguin(): Actor {
        val body = art(
            "ny-penguin",
            """<ellipse cx="0" cy="-8" rx="13" ry="18" fill="#1f2433"/><ellipse cx="2" cy="-6" rx="8" ry="13" fill="#fff"/>
<circle cx="2" cy="-22" r="9" fill="#1f2433"/><ellipse cx="5" cy="-21" rx="5" ry="5" fill="#fff"/>
<circle cx="6" cy="-22" r="1.4" fill="#111"/><path d="M9 -20 L15 -18 L9 -17Z" fill="#ffa31a"/>
<path d="M-6 9 L-1 9 L-3 12Z M3 9 L8 9 L6 12Z" fill="#ffa31a"/><path d="M-12 -10 C-18 -4 -16 4 -12 4" fill="#1f2433"/>
<path d="M-4 -40 C0 -44 6 -38 4 -32 L-2 -30Z" fill="#d42a2a"/><circle cx="-5" cy="-41" r="2.4" fill="#fff"/>"""
        )
        val waddle = moved(listOf(body)) { c, t, _ -> c.rotate(-8f + 16f * tri(t, 0.3f), 0f, 12f) }
        return Actor(40f, 46f, floatArrayOf(-20f, -34f, 40f, 46f), listOf(waddle))
    }

    fun eagle(): Actor {
        val wingLeft = art("ny-wing-l", """<path d="M0 0 C-14 -16 -34 -22 -52 -16 C-40 -10 -30 -4 -22 2 C-30 2 -38 6 -44 10 C-28 12 -12 8 0 4Z" fill="#5a3a22"/>""")
        val wingRight = art("ny-wing-r", """<path d="M0 0 C14 -16 34 -22 52 -16 C40 -10 30 -4 22 2 C30 2 38 6 44 10 C28 12 12 8 0 4Z" fill="#5a3a22"/>""")
        val body = art(
            "ny-eagle",
            """<ellipse cx="0" cy="4" rx="9" ry="14" fill="#6b4428"/><path d="M-6 16 L0 26 L6 16Z" fill="#f2efe6"/>
<circle cx="0" cy="-10" r="7" fill="#f6f4ee"/><path d="M0 -8 L5 -2 L0 -4Z" fill="#f2b82a"/><circle cx="2" cy="-11" r="1.2" fill="#1a1a1a"/>"""
        )
        val flap = { c: Canvas, t: Float, _: Long -> c.scale(1f, 1f - 0.65f * tri(t, 0.5f), 0f, -4f) }
        return Actor(112f, 56f, floatArrayOf(-56f, -26f, 112f, 56f), listOf(moved(listOf(wingLeft, wingRight), flap), body))
    }

    fun bear(armsUpAtMs: Long): Actor {
        val shadow = art("ny-bear-shadow", """<ellipse cx="0" cy="8" rx="34" ry="5" fill="#2a2a3a" opacity=".2"/>""")
        val torso = art(
            "ny-bear-torso",
            """<ellipse cx="0" cy="-34" rx="28" ry="32" fill="#6b4428"/><ellipse cx="0" cy="-28" rx="17" ry="20" fill="#9a6a3e"/>"""
        )
        val head = art(
            "ny-bear-head",
            """<circle cx="0" cy="-76" r="20" fill="#6b4428"/><circle cx="-15" cy="-92" r="7" fill="#6b4428"/>
<circle cx="15" cy="-92" r="7" fill="#6b4428"/><circle cx="-15" cy="-92" r="3.4" fill="#9a6a3e"/>
<circle cx="15" cy="-92" r="3.4" fill="#9a6a3e"/><ellipse cx="0" cy="-70" rx="9" ry="7" fill="#c9a07a"/>
<ellipse cx="0" cy="-73" rx="3.6" ry="2.6" fill="#2a1a10"/><circle cx="-7" cy="-80" r="2" fill="#111"/>
<circle cx="7" cy="-80" r="2" fill="#111"/><path d="M-3 -66 Q0 -63 3 -66" stroke="#2a1a10" stroke-width="1.4" fill="none"/>
<path d="M-20 -88 C-16 -104 16 -104 20 -88 L14 -86 C10 -96 -10 -96 -14 -86Z" fill="#d42a2a"/>
<circle cx="20" cy="-98" r="4" fill="#fff"/>"""
        )
        val legs = listOf(
            leg("M-14 -10 L-16 6", BEAR_BROWN, 12f, false, 0.5f),
            leg("M14 -10 L16 6", BEAR_BROWN, 12f, true, 0.5f)
        )
        val armLeft = art("ny-arm-l", """<path d="M-24 -50 L-36 -24" stroke="#6b4428" stroke-width="12" stroke-linecap="round"/>""")
        val armRight = art("ny-arm-r", """<path d="M24 -50 L36 -24" stroke="#6b4428" stroke-width="12" stroke-linecap="round"/>""")
        val raise = { ms: Long -> 120f * Curves.overshoot.getInterpolation(((ms - armsUpAtMs) / ARM_MS).coerceIn(0f, 1f)) }
        val left = Part { c, t, ms ->
            c.save(); c.rotate(raise(ms), -24f, -50f); armLeft.draw(c, t, ms); c.restore()
        }
        val right = Part { c, t, ms ->
            c.save(); c.rotate(-raise(ms), 24f, -50f); armRight.draw(c, t, ms); c.restore()
        }
        val walk = moved(listOf(shadow) + legs + torso + left + right + head) { c, t, _ ->
            val k = tri(t, 0.5f)
            c.translate(0f, -3f * k)
            c.rotate(-2f + 4f * k)
        }
        return Actor(92f, 112f, floatArrayOf(-46f, -100f, 92f, 112f), listOf(walk))
    }

    private const val DEER_LEG = 0xFF6B4A2A.toInt()
    private const val FOX_LEG = 0xFFC95F12.toInt()
    private const val BEAR_BROWN = 0xFF5A3B24.toInt()
    private const val ARM_MS = 500f
    private val GIFTS = listOf(
        "#ff4d5e" to "#ffd34d",
        "#4dd38a" to "#ff4d5e",
        "#5aa8ff" to "#fff",
        "#c77dff" to "#ffd34d",
        "#ffb03a" to "#4dd38a"
    )
}
