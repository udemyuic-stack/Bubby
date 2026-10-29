package com.example.buddy.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.os.SystemClock
import android.view.View
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Bubby's room as a side view: back wall, floor, the owned items in the look the player chose and a dotted outline
 * with a "+" for every item not bought yet. The mascot ([GlassCritter], the same drawing the home page uses) sits in the middle.
 *
 * Everything is drawn on a 340 x 220 unit canvas scaled to the view's width. Paints, paths and rectangles are created once,
 * [onDraw] allocates nothing. Feed it with [setOwned]; call [flash] after a purchase for a ring around the new item.
 */
class RoomView(c: Context) : View(c) {
    private companion object {
        const val W = 340f
        const val H = 220f
        const val FLOOR_Y = 150f
        val NEUTRAL_WALL = 0xFFDDD8D2.toInt()
        val NEUTRAL_STRIPE = 0xFFCDC7C0.toInt()
        val FLOOR = 0xFFB98B5E.toInt()
        val FLOOR_LINE = 0xFFA97B50.toInt()
        val BASEBOARD = 0xFF8E6440.toInt()
        val WOOD_DARK = 0xFF5A4A42.toInt()
        val INK = 0xFF2A2A33.toInt()
        val BULBS = intArrayOf(0xFFEE7BA3.toInt(), 0xFF5B9BF0.toInt(), 0xFFFFD27A.toInt(), 0xFF3FB8A5.toInt(), 0xFFB48CF0.toInt())
        // books on the shelf: x, width, height (colour 0 = the look's accent colour)
        val BOOK_X = floatArrayOf(166f, 173f, 179f, 187f, 193f)
        val BOOK_W = floatArrayOf(6f, 5f, 7f, 5f, 6f)
        val BOOK_H = floatArrayOf(16f, 20f, 14f, 18f, 15f)
        val BOOK_C = intArrayOf(0, 0xFFEE7BA3.toInt(), 0xFF5B9BF0.toInt(), 0, 0xFFF2B01E.toInt())
    }

    // ---- state ----------------------------------------------------------------------------------------------------
    private val n = RoomItems.all.size
    private val look = IntArray(n) { -1 }          // chosen look per item, -1 = not owned
    private val colA = IntArray(n)
    private val colB = IntArray(n)
    private val bounds = FloatArray(n * 4)          // l, t, r, b of each item, for the flash ring
    private var flashSlot = -1
    private var flashAt = 0L
    private var tz = 0L                             // time zone offset for the clock, read once

    /** Gentle motion (the mascot bobs, lights twinkle). Turn it off for screenshots. */
    var animated = true

    // slots of the items in [RoomItems.all]; -1 when an id is missing from the table (then that item is simply not drawn)
    private val sWall = RoomItems.indexOf("wall")
    private val sRug = RoomItems.indexOf("rug")
    private val sWindow = RoomItems.indexOf("window")
    private val sLamp = RoomItems.indexOf("lamp")
    private val sPoster = RoomItems.indexOf("poster")
    private val sShelf = RoomItems.indexOf("shelf")
    private val sPlant = RoomItems.indexOf("plant")
    private val sClock = RoomItems.indexOf("clock")
    private val sDesk = RoomItems.indexOf("desk")
    private val sSofa = RoomItems.indexOf("sofa")
    private val sLights = RoomItems.indexOf("lights")
    private val sFish = RoomItems.indexOf("fish")

    // ---- pre-allocated drawing tools ------------------------------------------------------------------------------
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND }
    private val dashLight = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = 2.6f; color = 0xB0FFFFFF.toInt(); pathEffect = DashPathEffect(floatArrayOf(5f, 4f), 0f)
    }
    private val dashDark = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = 1.2f; color = 0x90504050.toInt(); pathEffect = DashPathEffect(floatArrayOf(5f, 4f), 0f)
    }
    private val plus = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeWidth = 2f; color = 0xC8FFFFFF.toInt() }
    private val spot = Paint(Paint.ANTI_ALIAS_FLAG)
    private val r = RectF()
    private val pa = Path()
    private val rope = Path()
    private val clip = Path()
    private val clipRect = RectF()
    private var k = 1f

    /** The mascot. Swap this for another drawing if the glass mochi ever gets too coupled to the theme. */
    private val critter = GlassCritter().also { it.withHalo = false }

    init {
        // the string of lights: five scallops across the top of the wall
        rope.moveTo(0f, 8f)
        for (i in 0 until 5) rope.quadTo(i * 68f + 34f, 26f, (i + 1) * 68f, 8f)
        spot.shader = RadialGradient(0f, 0f, 56f, intArrayOf(Theme.rgb(Theme.cur.dark, 150), Theme.rgb(Theme.cur.dark, 70), 0),
            floatArrayOf(0f, .6f, 1f), Shader.TileMode.CLAMP)
        tz = java.util.TimeZone.getDefault().getOffset(System.currentTimeMillis()).toLong()
        putBound(sWall, 0f, 0f, W, FLOOR_Y)
        putBound(sRug, 96f, 161f, 244f, 195f)
        putBound(sWindow, 14f, 26f, 96f, 104f)
        putBound(sLamp, 298f, 74f, 326f, 160f)
        putBound(sPoster, 108f, 26f, 146f, 80f)
        putBound(sShelf, 160f, 52f, 240f, 82f)
        putBound(sPlant, 90f, 110f, 130f, 158f)
        putBound(sClock, 253f, 27f, 283f, 57f)
        putBound(sDesk, 216f, 86f, 288f, 158f)
        putBound(sSofa, 6f, 104f, 98f, 158f)
        putBound(sLights, 0f, 0f, W, 28f)
        putBound(sFish, 262f, 84f, 290f, 112f)
    }

    private fun putBound(slot: Int, l: Float, t: Float, rr: Float, b: Float) {
        if (slot < 0) return
        bounds[slot * 4] = l; bounds[slot * 4 + 1] = t; bounds[slot * 4 + 2] = rr; bounds[slot * 4 + 3] = b
    }

    // ---- public API -----------------------------------------------------------------------------------------------

    /** Shows what the player owns: id -> index of the chosen look (see [RoomItems.parse]). */
    fun setOwned(owned: Map<String, Int>) {
        for (i in 0 until n) {
            val item = RoomItems.all[i]
            val v = owned[item.id] ?: -1
            if (v in item.variants.indices) {
                look[i] = v; colA[i] = item.variants[v].color; colB[i] = item.variants[v].accent
            } else {
                look[i] = -1
            }
        }
        invalidate()
    }

    /** A ring that grows and fades around the item with this id (call after buying it or changing its look). */
    fun flash(id: String) {
        flashSlot = RoomItems.indexOf(id)
        flashAt = SystemClock.uptimeMillis()
        invalidate()
    }

    // ---- layout ---------------------------------------------------------------------------------------------------

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val d = resources.displayMetrics.density
        val size = MeasureSpec.getSize(widthMeasureSpec)
        val w = if (size > 0) size else (W * d).toInt()
        setMeasuredDimension(w, (w * H / W).toInt())
    }

    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) {
        super.onSizeChanged(w, h, ow, oh)
        k = w / W
        val rad = 20f * resources.displayMetrics.density
        clipRect.set(0f, 0f, w.toFloat(), h.toFloat())
        clip.reset()
        clip.addRoundRect(clipRect, rad, rad, Path.Direction.CW)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        tz = java.util.TimeZone.getDefault().getOffset(System.currentTimeMillis()).toLong()
    }

    // ---- small helpers --------------------------------------------------------------------------------------------

    private fun al(color: Int, a: Int): Int = (color and 0x00FFFFFF) or (a shl 24)
    private fun lk(s: Int): Int = if (s in 0 until n) look[s] else -1
    private fun ca(s: Int): Int = if (s in 0 until n) colA[s] else 0
    private fun cb(s: Int): Int = if (s in 0 until n) colB[s] else 0

    private fun rect(cv: Canvas, l: Float, t: Float, rr: Float, b: Float, rad: Float, color: Int) {
        fill.color = color
        r.set(l, t, rr, b)
        if (rad > 0f) cv.drawRoundRect(r, rad, rad, fill) else cv.drawRect(r, fill)
    }

    private fun oval(cv: Canvas, l: Float, t: Float, rr: Float, b: Float, color: Int) {
        fill.color = color
        r.set(l, t, rr, b)
        cv.drawOval(r, fill)
    }

    private fun disc(cv: Canvas, x: Float, y: Float, rad: Float, color: Int) {
        fill.color = color
        cv.drawCircle(x, y, rad, fill)
    }

    private fun stroke(cv: Canvas, x1: Float, y1: Float, x2: Float, y2: Float, w: Float, color: Int) {
        line.color = color; line.strokeWidth = w
        cv.drawLine(x1, y1, x2, y2, line)
    }

    private fun plusSign(cv: Canvas, cx: Float, cy: Float) {
        cv.drawLine(cx - 4f, cy, cx + 4f, cy, plus)
        cv.drawLine(cx, cy - 4f, cx, cy + 4f, plus)
    }

    /** The "not yet" placeholder of an item: dotted rounded outline with a plus in the middle. */
    private fun dashRect(cv: Canvas, l: Float, t: Float, rr: Float, b: Float, rad: Float) {
        r.set(l, t, rr, b)
        cv.drawRoundRect(r, rad, rad, dashLight)
        cv.drawRoundRect(r, rad, rad, dashDark)
        plusSign(cv, (l + rr) / 2f, (t + b) / 2f)
    }

    private fun dashOval(cv: Canvas, l: Float, t: Float, rr: Float, b: Float) {
        r.set(l, t, rr, b)
        cv.drawOval(r, dashLight)
        cv.drawOval(r, dashDark)
        plusSign(cv, (l + rr) / 2f, (t + b) / 2f)
    }

    // ---- drawing --------------------------------------------------------------------------------------------------

    override fun onDraw(cv: Canvas) {
        val now = SystemClock.uptimeMillis()
        val sec = (now % 1000000L) / 1000f
        cv.save()
        cv.clipPath(clip)
        cv.scale(k, k)

        wall(cv)
        floor(cv)
        rug(cv)
        window(cv)
        poster(cv)
        shelf(cv)
        clock(cv)
        lights(cv, sec)
        sofa(cv)
        desk(cv)
        fish(cv, sec)
        plant(cv, sec)
        lamp(cv)
        mascot(cv, sec)
        flashRing(cv, now)

        cv.restore()
        if (animated || flashSlot >= 0) postInvalidateOnAnimation()
    }

    private fun wall(cv: Canvas) {
        val v = lk(sWall)
        val base = if (v >= 0) ca(sWall) else NEUTRAL_WALL
        val stripe = if (v >= 0) cb(sWall) else NEUTRAL_STRIPE
        rect(cv, 0f, 0f, W, FLOOR_Y, 0f, base)
        if (v == 1) { var x = 10f; while (x < W) { rect(cv, x, 0f, x + 8f, FLOOR_Y, 0f, al(stripe, 70)); x += 20f } }
        if (v == 2) {
            var y = 14f
            while (y < 130f) { var x = if (((y / 24f).toInt() and 1) == 0) 12f else 24f; while (x < W) { disc(cv, x, y, 2.2f, al(stripe, 110)); x += 24f }; y += 24f }
        }
        rect(cv, 0f, 118f, W, FLOOR_Y, 0f, al(stripe, 150))   // lower wall band
        if (v < 0) dashRect(cv, 6f, 6f, W - 6f, 112f, 8f)
    }

    private fun floor(cv: Canvas) {
        rect(cv, 0f, FLOOR_Y, W, H, 0f, FLOOR)
        var y = 164f
        while (y < H) { stroke(cv, 0f, y, W, y, 1f, FLOOR_LINE); y += 16f }
        rect(cv, 0f, 144f, W, 152f, 0f, BASEBOARD)
    }

    private fun rug(cv: Canvas) {
        val v = lk(sRug)
        if (v < 0) { dashOval(cv, 96f, 161f, 244f, 195f); return }
        oval(cv, 96f, 161f, 244f, 195f, ca(sRug))
        oval(cv, 114f, 166f, 226f, 190f, cb(sRug))
        oval(cv, 132f, 171f, 208f, 185f, ca(sRug))
    }

    private fun window(cv: Canvas) {
        val v = lk(sWindow)
        if (v < 0) { dashRect(cv, 20f, 28f, 90f, 98f, 4f); return }
        val sky = ca(sWindow); val frame = cb(sWindow)
        rect(cv, 18f, 26f, 92f, 100f, 4f, frame)
        rect(cv, 23f, 31f, 87f, 95f, 1f, sky)
        cv.save()
        r.set(23f, 31f, 87f, 95f)
        cv.clipRect(r)
        when (v) {
            0 -> {
                disc(cv, 68f, 48f, 8f, 0xFFFFE07A.toInt())
                oval(cv, 28f, 66f, 52f, 76f, 0xCCFFFFFF.toInt()); oval(cv, 36f, 60f, 58f, 72f, 0xCCFFFFFF.toInt())
            }
            1 -> {
                disc(cv, 55f, 96f, 14f, 0xFFFFD27A.toInt())
                oval(cv, 62f, 46f, 84f, 54f, 0x99FFFFFF.toInt())
            }
            else -> {
                disc(cv, 68f, 48f, 8f, 0xFFFFF2B0.toInt()); disc(cv, 72f, 45f, 7f, sky)
                disc(cv, 34f, 42f, 1.4f, 0xFFFFFFFF.toInt()); disc(cv, 48f, 56f, 1.2f, 0xCCFFFFFF.toInt()); disc(cv, 40f, 78f, 1.4f, 0xFFFFFFFF.toInt()); disc(cv, 78f, 76f, 1.2f, 0xCCFFFFFF.toInt())
            }
        }
        cv.restore()
        rect(cv, 54f, 31f, 56f, 95f, 0f, frame)
        rect(cv, 23f, 62f, 87f, 64f, 0f, frame)
        rect(cv, 14f, 98f, 96f, 104f, 2f, frame)
    }

    private fun poster(cv: Canvas) {
        val v = lk(sPoster)
        if (v < 0) { dashRect(cv, 108f, 26f, 146f, 80f, 3f); return }
        val paper = ca(sPoster); val ink = cb(sPoster)
        rect(cv, 108f, 26f, 146f, 80f, 2f, WOOD_DARK)
        rect(cv, 111f, 29f, 143f, 77f, 1f, paper)
        when (v) {
            0 -> {
                disc(cv, 136f, 40f, 5f, 0xFFFFE07A.toInt())
                pa.reset(); pa.moveTo(111f, 77f); pa.lineTo(124f, 50f); pa.lineTo(134f, 66f); pa.lineTo(139f, 58f); pa.lineTo(143f, 77f); pa.close()
                fill.color = ink; cv.drawPath(pa, fill)
            }
            1 -> {
                disc(cv, 127f, 53f, 12f, ink); disc(cv, 132f, 49f, 10f, paper)
                disc(cv, 117f, 38f, 1.2f, 0xFFFFFFFF.toInt()); disc(cv, 136f, 68f, 1.2f, 0xFFFFFFFF.toInt()); disc(cv, 119f, 68f, 1.2f, 0xCCFFFFFF.toInt())
            }
            else -> {
                disc(cv, 122f, 49f, 7f, ink); disc(cv, 132f, 49f, 7f, ink)
                pa.reset(); pa.moveTo(115.5f, 52f); pa.lineTo(138.5f, 52f); pa.lineTo(127f, 68f); pa.close()
                fill.color = ink; cv.drawPath(pa, fill)
            }
        }
    }

    private fun shelf(cv: Canvas) {
        val v = lk(sShelf)
        if (v < 0) { dashRect(cv, 160f, 52f, 240f, 76f, 3f); return }
        val wood = ca(sShelf); val acc = cb(sShelf)
        for (i in BOOK_X.indices) {
            val c = if (BOOK_C[i] == 0) acc else BOOK_C[i]
            rect(cv, BOOK_X[i], 70f - BOOK_H[i], BOOK_X[i] + BOOK_W[i], 70f, 1f, c)
        }
        disc(cv, 224f, 63f, 7f, 0xFFF4F0EA.toInt())      // a little vase
        rect(cv, 220f, 56f, 228f, 60f, 1f, 0xFFF4F0EA.toInt())
        rect(cv, 160f, 70f, 240f, 75f, 2f, wood)
        rect(cv, 166f, 75f, 170f, 83f, 1f, wood)
        rect(cv, 230f, 75f, 234f, 83f, 1f, wood)
    }

    private fun clock(cv: Canvas) {
        val v = lk(sClock)
        if (v < 0) { dashOval(cv, 253f, 27f, 283f, 57f); return }
        val rim = ca(sClock); val face = cb(sClock)
        disc(cv, 268f, 42f, 15f, rim)
        disc(cv, 268f, 42f, 12f, face)
        for (i in 0 until 4) {
            val a = i * (PI / 2.0)
            val dx = cos(a).toFloat(); val dy = sin(a).toFloat()
            stroke(cv, 268f + dx * 9f, 42f + dy * 9f, 268f + dx * 11f, 42f + dy * 11f, 1.2f, INK)
        }
        val ms = System.currentTimeMillis() + tz
        val minutes = ((ms / 60000L) % 720L).toFloat() + ((ms / 1000L) % 60L) / 60f      // minutes since 12:00
        val am = (minutes % 60f) / 60f * 2f * PI.toFloat() - PI.toFloat() / 2f
        val ah = minutes / 720f * 2f * PI.toFloat() - PI.toFloat() / 2f
        stroke(cv, 268f, 42f, 268f + cos(ah) * 6.5f, 42f + sin(ah) * 6.5f, 2f, INK)
        stroke(cv, 268f, 42f, 268f + cos(am) * 9.5f, 42f + sin(am) * 9.5f, 1.3f, INK)
        disc(cv, 268f, 42f, 1.6f, INK)
    }

    private fun lights(cv: Canvas, sec: Float) {
        val v = lk(sLights)
        if (v < 0) {
            cv.drawPath(rope, dashLight); cv.drawPath(rope, dashDark)
            return
        }
        line.color = WOOD_DARK; line.strokeWidth = 1f
        cv.drawPath(rope, line)
        val a = ca(sLights); val b = cb(sLights)
        for (i in 0 until 5) {
            val x0 = i * 68f; val x1 = x0 + 34f; val x2 = x0 + 68f
            for (j in 1..4) {
                val t = j / 5f; val u = 1f - t
                val x = u * u * x0 + 2f * u * t * x1 + t * t * x2
                val y = u * u * 8f + 2f * u * t * 26f + t * t * 8f
                val c = if (v == 1) BULBS[(i * 4 + j) % BULBS.size] else if (j % 2 == 0) a else b
                val tw = 0.65f + 0.35f * sin(sec * 2.2f + i * 1.7f + j * 0.9f)
                disc(cv, x, y + 2f, 6f, al(c, (55 * tw).toInt()))
                disc(cv, x, y + 2f, 2.6f, al(c, (255 * tw).toInt()))
            }
        }
    }

    private fun sofa(cv: Canvas) {
        val v = lk(sSofa)
        if (v < 0) { dashRect(cv, 8f, 108f, 96f, 156f, 8f); return }
        val a = ca(sSofa); val b = cb(sSofa)
        rect(cv, 12f, 106f, 92f, 140f, 9f, a)           // back
        rect(cv, 6f, 120f, 22f, 152f, 7f, b)            // arms
        rect(cv, 82f, 120f, 98f, 152f, 7f, b)
        rect(cv, 20f, 128f, 53f, 148f, 6f, a)           // cushions
        rect(cv, 55f, 128f, 84f, 148f, 6f, a)
        rect(cv, 26f, 114f, 42f, 132f, 5f, 0xFFFFE08A.toInt())   // pillow
        rect(cv, 14f, 152f, 20f, 157f, 1f, WOOD_DARK)
        rect(cv, 84f, 152f, 90f, 157f, 1f, WOOD_DARK)
    }

    private fun desk(cv: Canvas) {
        val v = lk(sDesk)
        if (v < 0) { dashRect(cv, 216f, 90f, 288f, 156f, 4f); return }
        val wood = ca(sDesk); val glow = cb(sDesk)
        rect(cv, 220f, 116f, 226f, 156f, 1f, wood)
        rect(cv, 278f, 116f, 284f, 156f, 1f, wood)
        rect(cv, 232f, 116f, 270f, 134f, 2f, 0x22000000)
        rect(cv, 216f, 110f, 288f, 117f, 2f, wood)
        rect(cv, 228f, 106f, 252f, 110f, 1f, 0xFF9AA3B5.toInt())    // laptop
        rect(cv, 231f, 90f, 249f, 106f, 2f, INK)
        rect(cv, 233f, 92f, 247f, 104f, 1f, glow)
        rect(cv, 255f, 102f, 261f, 110f, 1f, 0xFFFFFFFF.toInt())  // mug
    }

    private fun fish(cv: Canvas, sec: Float) {
        val v = lk(sFish)
        if (v < 0) { dashOval(cv, 264f, 86f, 288f, 110f); return }
        val body = ca(sFish); val water = cb(sFish)
        disc(cv, 276f, 98f, 12f, al(water, 190))
        val fx = 276f + sin(sec * 1.6f) * 4f
        val dir = if (cos(sec * 1.6f) >= 0f) 1f else -1f
        oval(cv, fx - 4f, 96f, fx + 4f, 101f, body)
        pa.reset(); pa.moveTo(fx - dir * 3f, 98.5f); pa.lineTo(fx - dir * 8f, 95.5f); pa.lineTo(fx - dir * 8f, 101.5f); pa.close()
        fill.color = body; cv.drawPath(pa, fill)
        disc(cv, fx + dir * 2f, 97.5f, 0.9f, INK)
        disc(cv, 281f, 91f - ((sec * 6f) % 4f), 1.1f, 0xAAFFFFFF.toInt())   // a bubble
        line.color = 0xCCFFFFFF.toInt(); line.strokeWidth = 1.4f
        r.set(264f, 86f, 288f, 110f)
        line.style = Paint.Style.STROKE
        cv.drawOval(r, line)
    }

    private fun plant(cv: Canvas, sec: Float) {
        val v = lk(sPlant)
        if (v < 0) { dashRect(cv, 98f, 118f, 122f, 157f, 4f); return }
        val green = ca(sPlant); val pot = cb(sPlant)
        val sway = sin(sec * 1.1f) * 2f
        when (v) {
            0 -> for (i in 0 until 5) {
                cv.save(); cv.translate(110f, 140f); cv.rotate(-50f + 25f * i + sway)
                oval(cv, -3.5f, -26f, 3.5f, 0f, green)
                cv.restore()
            }
            1 -> {
                rect(cv, 105f, 112f, 115f, 140f, 5f, green)
                rect(cv, 98f, 122f, 106f, 127f, 2.5f, green); rect(cv, 98f, 115f, 103f, 127f, 2.5f, green)
                rect(cv, 114f, 127f, 122f, 132f, 2.5f, green); rect(cv, 118f, 120f, 123f, 132f, 2.5f, green)
                disc(cv, 110f, 112f, 3.2f, pot)
            }
            else -> {
                stroke(cv, 110f, 140f, 110f + sway * .5f, 116f, 2f, 0xFF4FAE62.toInt())
                oval(cv, 102f, 126f, 110f, 133f, 0xFF4FAE62.toInt()); oval(cv, 110f, 122f, 118f, 129f, 0xFF4FAE62.toInt())
                val fx = 110f + sway * .5f
                for (i in 0 until 5) {
                    val a = i * (2.0 * PI / 5.0)
                    disc(cv, fx + cos(a).toFloat() * 5f, 112f + sin(a).toFloat() * 5f, 4f, green)
                }
                disc(cv, fx, 112f, 3f, 0xFFFFD27A.toInt())
            }
        }
        pa.reset(); pa.moveTo(101f, 140f); pa.lineTo(119f, 140f); pa.lineTo(116f, 157f); pa.lineTo(104f, 157f); pa.close()
        fill.color = pot; cv.drawPath(pa, fill)
        rect(cv, 99f, 138f, 121f, 143f, 2f, pot)
    }

    private fun lamp(cv: Canvas) {
        val v = lk(sLamp)
        if (v < 0) { dashRect(cv, 298f, 74f, 326f, 160f, 3f); return }
        val shade = ca(sLamp); val pole = cb(sLamp)
        disc(cv, 312f, 92f, 34f, al(shade, 30))
        disc(cv, 312f, 92f, 22f, al(shade, 44))
        rect(cv, 310.8f, 98f, 313.4f, 158f, 1f, pole)
        oval(cv, 302f, 153f, 322f, 160f, pole)
        pa.reset(); pa.moveTo(298f, 98f); pa.lineTo(326f, 98f); pa.lineTo(320f, 76f); pa.lineTo(304f, 76f); pa.close()
        fill.color = shade; cv.drawPath(pa, fill)
    }

    private fun mascot(cv: Canvas, sec: Float) {
        // dark soft patch: the glass mascot is whitish and would vanish on a light wall
        cv.save(); cv.translate(170f, 140f); cv.scale(1f, 0.62f); cv.drawCircle(0f, 0f, 56f, spot); cv.restore()
        val bob = if (animated) sin(sec * 2.2f) * 1.6f else 0f
        cv.save(); cv.translate(127f, 104f + bob); cv.scale(0.5f, 0.5f)
        critter.draw(cv)
        cv.restore()
    }

    private fun flashRing(cv: Canvas, now: Long) {
        val s = flashSlot
        if (s < 0) return
        val t = (now - flashAt) / 900f
        if (t >= 1f || s >= n) { flashSlot = -1; return }
        val g = 2f + 8f * t
        line.color = al(0xFFFFFF, (255 * (1f - t)).toInt()); line.strokeWidth = 2.5f
        r.set(bounds[s * 4] - g, bounds[s * 4 + 1] - g, bounds[s * 4 + 2] + g, bounds[s * 4 + 3] + g)
        cv.drawRoundRect(r, 8f + g, 8f + g, line)
    }
}
