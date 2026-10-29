package com.example.buddy.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.os.SystemClock
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * The board of "Bubby's Room": one view, one onDraw. The engine has already played the move when the animation starts;
 * this view keeps its own "what is shown" arrays and replays the engine's steps on them, so that the screen is never ahead
 * of or behind what the player sees. A move plays as: swap (with a little lift) → pieces glow, swell and burst into sparks
 * (the pieces of a match slide into the new special) → the rest rain down one column after another, squash when they land →
 * the next cascade, with a score floater, a cheer ("Great!") and a shake when something explodes.
 * The animation loop runs only while something moves; nothing is allocated per frame.
 */
class MatchBoardView(c: Context, val engine: Match3Engine) : View(c) {
    /** Called when a move (with all its cascades) has been shown to the end. */
    var onMoveShown: ((Match3Engine.Result) -> Unit)? = null
    /** A swap that is not allowed was tried. */
    var onRefused: (() -> Unit)? = null
    var onCombo: ((Int) -> Unit)? = null
    var locked = false
    /** Cheers shown over the board: nice, great, awesome, incredible, boom, sparkle (the screen sets them in the user's language). */
    var words = arrayOf("Nice!", "Great!", "Awesome!", "Incredible!", "Boom!", "Sparkle!")

    private val n = engine.w * engine.h
    private val vType = IntArray(n) { -1 }
    private val vSpecial = IntArray(n)
    private val vDust = IntArray(n)
    private val offX = FloatArray(n)
    private val offY = FloatArray(n)
    private val sx = FloatArray(n) { 1f }
    private val sy = FloatArray(n) { 1f }
    private val rot = FloatArray(n)
    private val flash = FloatArray(n)
    private val fallFrom = FloatArray(n)
    private val fallDelay = FloatArray(n)
    private val fallDur = FloatArray(n)
    private val falling = BooleanArray(n)

    private enum class Phase { IDLE, SWAP, POP, FALL, SHUFFLE }
    private var phase = Phase.IDLE
    private var phaseStart = 0L
    private var phaseDur = 1L
    private var result: Match3Engine.Result? = null
    private var stepIndex = 0
    private var swapA = -1; private var swapB = -1
    private var refused = false
    private var intro = false   // the fall that fills the board at the start of a level

    private var selected = -1
    private var hint: IntArray? = null
    private var lastTouch = SystemClock.uptimeMillis()
    private var hintStart = 0L
    private val wake = Runnable { invalidate() }
    private val fm = Paint.FontMetrics()
    private val hsv = FloatArray(3)

    // sparks
    private val maxP = 220
    private val px = FloatArray(maxP); private val py = FloatArray(maxP)
    private val pvx = FloatArray(maxP); private val pvy = FloatArray(maxP)
    private val plife = FloatArray(maxP); private val pmax = FloatArray(maxP); private val psize = FloatArray(maxP)
    private val pcol = IntArray(maxP)
    private var pNext = 0
    private var pAlive = 0
    private var lastFrame = 0L

    // score floaters and the cheer
    private val fx = FloatArray(4); private val fy = FloatArray(4); private val fStart = LongArray(4) { -10000L }
    private val fText = arrayOfNulls<String>(4)
    private var fNext = 0
    private var cheer: String? = null
    private var cheerStart = -10000L
    private var shakeUntil = 0L
    private var shakeAmp = 0f

    // a piece that wiggles now and then when nothing happens
    private var wiggleCell = -1
    private var wiggleStart = 0L
    private var nextWiggle = SystemClock.uptimeMillis() + 2500

    private val tileColors = intArrayOf(0xFF4DB3FF.toInt(), 0xFFFF9A3D.toInt(), 0xFFFFD83D.toInt(), 0xFFFF6F9F.toInt(), 0xFF62D062.toInt())
    private val emoji = arrayOf("💧", "🖌️", "💡", "🛋️", "🌿")
    private val grads = arrayOfNulls<LinearGradient>(5)
    private val plain = arrayOfNulls<Bitmap>(5)     // each piece drawn once (shadow, gradient, shine, picture); a frame then only copies bitmaps
    private val special = arrayOfNulls<Bitmap>(5)   // the same with a small picture, for pieces that carry a special
    private val board = Paint(Paint.ANTI_ALIAS_FLAG)
    private val tile = Paint(Paint.ANTI_ALIAS_FLAG)
    private val shadow = Paint(Paint.ANTI_ALIAS_FLAG)
    private val spark = Paint(Paint.ANTI_ALIAS_FLAG)
    private val glyph = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }
    private val mark = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER; typeface = android.graphics.Typeface.DEFAULT_BOLD; color = Color.WHITE; setShadowLayer(4f, 0f, 2f, 0xAA000000.toInt()) }
    private val big = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER; typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD_ITALIC); setShadowLayer(10f, 0f, 4f, 0xCC000000.toInt()) }
    private val dustPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val rect = RectF()
    private var cell = 0f

    init { syncFromEngine() }

    fun syncFromEngine() {
        for (i in 0 until n) { vType[i] = engine.type[i]; vSpecial[i] = engine.special[i]; vDust[i] = engine.dust[i]; offX[i] = 0f; offY[i] = 0f; sx[i] = 1f; sy[i] = 1f; rot[i] = 0f; flash[i] = 0f; falling[i] = false }
        phase = Phase.IDLE; selected = -1; hint = null; lastTouch = SystemClock.uptimeMillis(); intro = false
        invalidate()
    }

    /** The pieces of a new level rain into the board, column after column. */
    fun introDrop() {
        if (cell <= 0f) return   // not measured yet: the board just appears
        syncFromEngine()
        var total = 0f
        for (i in 0 until n) {
            val row = i / engine.w; val col = i % engine.w
            fallFrom[i] = -(row + 2) * cell
            fallDelay[i] = col * 45f + (engine.h - 1 - row) * 38f
            fallDur[i] = 420f + row * 40f
            falling[i] = true
            offY[i] = fallFrom[i]
            total = max(total, fallDelay[i] + fallDur[i])
        }
        intro = true; result = null
        begin(Phase.FALL, total.toLong() + 30)
    }

    override fun onMeasure(wSpec: Int, hSpec: Int) {
        val w = MeasureSpec.getSize(wSpec)
        setMeasuredDimension(w, (w * engine.h / engine.w.toFloat()).toInt())
    }

    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) {
        cell = w / engine.w.toFloat()
        glyph.textSize = cell * 0.52f
        mark.textSize = cell * 0.5f
        big.textSize = cell * 1.1f
        ring.strokeWidth = cell * 0.07f
        val half = cell / 2f - cell * 0.07f
        for (k in 0 until 5) {
            val base = tileColors[k]
            Color.colorToHSV(base, hsv)
            val top = Color.HSVToColor(floatArrayOf(hsv[0], max(0f, hsv[1] - 0.22f), min(1f, hsv[2] + 0.12f)))
            val bottom = Color.HSVToColor(floatArrayOf(hsv[0], min(1f, hsv[1] + 0.05f), hsv[2] * 0.82f))
            grads[k] = LinearGradient(0f, -half, 0f, half, top, bottom, Shader.TileMode.CLAMP)
        }
        for (k in 0 until 5) { plain[k] = renderTile(k, false); special[k] = renderTile(k, true) }
    }

    /** One piece on its own bitmap, centred; [small] draws the picture small in the corner (a special's symbol goes on top later). */
    private fun renderTile(k: Int, small: Boolean): Bitmap {
        val size = Math.ceil(cell.toDouble()).toInt().coerceAtLeast(8)
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val cv = Canvas(bmp)
        cv.translate(size / 2f, size / 2f)
        val half = cell / 2f - cell * 0.07f
        shadow.color = 0x33000000
        rect.set(-half + cell * .02f, -half + cell * .06f, half + cell * .02f, half + cell * .06f); cv.drawRoundRect(rect, cell * .26f, cell * .26f, shadow)
        tile.shader = grads[k]
        rect.set(-half, -half, half, half); cv.drawRoundRect(rect, cell * .26f, cell * .26f, tile)
        tile.shader = null
        tile.color = Glass.white(.3f)
        rect.set(-half + cell * .06f, -half + cell * .05f, half - cell * .06f, -half * 0.05f); cv.drawRoundRect(rect, cell * .2f, cell * .2f, tile)
        if (small) drawGlyph(cv, emoji[k], -cell * .17f, -cell * .17f, cell * .3f) else drawGlyph(cv, emoji[k], 0f, cell * .01f, glyph.textSize)
        return bmp
    }

    // ---- touch ----------------------------------------------------------------------------------------------------------

    private var downCell = -1
    private var downX = 0f; private var downY = 0f
    private var swiped = false

    private fun cellAt(x: Float, y: Float): Int {
        val col = (x / cell).toInt(); val row = (y / cell).toInt()
        return if (col in 0 until engine.w && row in 0 until engine.h) row * engine.w + col else -1
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        if (locked || phase != Phase.IDLE) { downCell = -1; return true }
        lastTouch = SystemClock.uptimeMillis(); hint = null
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                parent?.requestDisallowInterceptTouchEvent(true)
                downCell = cellAt(e.x, e.y); downX = e.x; downY = e.y; swiped = false
                if (downCell >= 0) { wiggleCell = downCell; wiggleStart = lastTouch - 200; invalidate() }   // the piece answers the finger at once
            }
            MotionEvent.ACTION_MOVE -> if (!swiped && downCell >= 0) {
                val dx = e.x - downX; val dy = e.y - downY
                if (abs(dx) > cell * 0.3f || abs(dy) > cell * 0.3f) {
                    swiped = true
                    val to = if (abs(dx) > abs(dy)) downCell + (if (dx > 0) 1 else -1) else downCell + (if (dy > 0) engine.w else -engine.w)
                    val wrap = abs(dx) > abs(dy) && (to / engine.w != downCell / engine.w)
                    if (to in 0 until n && !wrap) tryMove(downCell, to)
                    selected = -1
                }
            }
            MotionEvent.ACTION_UP -> if (!swiped && downCell >= 0) {
                val tapped = cellAt(e.x, e.y)
                if (tapped == downCell) {
                    if (selected < 0) { selected = tapped; invalidate() }
                    else if (selected == tapped) { selected = -1; invalidate() }
                    else if (isNeighbour(selected, tapped)) { val a = selected; selected = -1; tryMove(a, tapped) }
                    else { selected = tapped; invalidate() }
                }
            }
            MotionEvent.ACTION_CANCEL -> downCell = -1
        }
        if (e.actionMasked == MotionEvent.ACTION_UP) downCell = -1
        return true
    }

    private fun isNeighbour(a: Int, b: Int) = (a / engine.w == b / engine.w && abs(a - b) == 1) || (a % engine.w == b % engine.w && abs(a - b) == engine.w)

    // ---- playing a move -------------------------------------------------------------------------------------------------

    fun tryMove(a: Int, b: Int) {
        if (locked || phase != Phase.IDLE) return
        val res = engine.swap(a, b)
        performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
        if (!res.valid) {
            swapA = a; swapB = b; refused = true; result = null
            begin(Phase.SWAP, 260)
            onRefused?.invoke()
            return
        }
        swapA = a; swapB = b; refused = false; result = res; stepIndex = 0; intro = false
        begin(Phase.SWAP, 150)
    }

    private fun begin(p: Phase, ms: Long) { phase = p; phaseStart = SystemClock.uptimeMillis(); phaseDur = max(1L, ms); invalidate() }

    private fun easeOut(t: Float): Float { val k = 1f - t; return 1f - k * k * k }
    private fun easeIn(t: Float) = t * t
    private fun easeBack(t: Float): Float { val k = t - 1f; return 1f + 2.0f * k * k * k + 1.0f * k * k }
    private val PIf = PI.toFloat()

    /** Moves the animation on by the time passed; changes phase when one has finished. */
    private fun advance(now: Long) {
        val t = ((now - phaseStart).toFloat() / phaseDur).coerceIn(0f, 1f)
        when (phase) {
            Phase.SWAP -> {
                val e = if (refused) sin(t * PIf) * 0.45f else easeOut(t)
                val ax = (swapB % engine.w - swapA % engine.w) * cell * e; val ay = (swapB / engine.w - swapA / engine.w) * cell * e
                offX[swapA] = ax; offY[swapA] = ay; offX[swapB] = -ax; offY[swapB] = -ay
                val lift = 1f + 0.12f * sin(t * PIf)
                sx[swapA] = lift; sy[swapA] = lift; sx[swapB] = 1f / lift; sy[swapB] = 1f / lift
                if (t >= 1f) {
                    offX[swapA] = 0f; offY[swapA] = 0f; offX[swapB] = 0f; offY[swapB] = 0f
                    sx[swapA] = 1f; sy[swapA] = 1f; sx[swapB] = 1f; sy[swapB] = 1f
                    if (refused) { phase = Phase.IDLE; return }
                    val ty = vType[swapA]; vType[swapA] = vType[swapB]; vType[swapB] = ty
                    val sp = vSpecial[swapA]; vSpecial[swapA] = vSpecial[swapB]; vSpecial[swapB] = sp
                    startPop(now)
                }
            }
            Phase.POP -> {
                val s = result!!.steps[stepIndex]
                // grow and glow first, then shrink into nothing; the pieces of a match slide into the special they make
                val up = if (t < 0.32f) easeOut(t / 0.32f) else 1f
                val down = if (t < 0.32f) 0f else easeIn((t - 0.32f) / 0.68f)
                val k = 1f + 0.28f * up - (1.28f * down)
                val glow = if (t < 0.32f) t / 0.32f else 1f - down
                for (i in s.cleared) {
                    sx[i] = max(0f, k); sy[i] = max(0f, k); flash[i] = glow
                    rot[i] = (if (i % 2 == 0) 1f else -1f) * 16f * down
                    if (s.spawned.isNotEmpty()) {
                        var best = s.spawned[0].cell; var bd = Int.MAX_VALUE
                        for (sp in s.spawned) { val d = abs(sp.cell / engine.w - i / engine.w) + abs(sp.cell % engine.w - i % engine.w); if (d < bd) { bd = d; best = sp.cell } }
                        val m = easeIn(t)
                        offX[i] = ((best % engine.w) - (i % engine.w)) * cell * m
                        offY[i] = ((best / engine.w) - (i / engine.w)) * cell * m
                    }
                }
                for (sp in s.spawned) { val b = 1f + 0.4f * sin(t * PIf); sx[sp.cell] = b; sy[sp.cell] = b; flash[sp.cell] = sin(t * PIf) }
                for (i in s.fired) flash[i] = max(flash[i], 1f - t)
                if (t >= 1f) endPop(now)
            }
            Phase.FALL -> {
                val el = (now - phaseStart).toFloat()
                for (i in 0 until n) if (falling[i]) {
                    val ti = ((el - fallDelay[i]) / fallDur[i]).coerceIn(0f, 1f)
                    offY[i] = fallFrom[i] * (1f - easeBack(ti))
                    // squash when it lands
                    val land = if (ti > 0.72f && ti < 1f) sin((ti - 0.72f) / 0.28f * PIf) else 0f
                    sy[i] = 1f - 0.16f * land; sx[i] = 1f + 0.1f * land
                    if (ti >= 1f) { offY[i] = 0f; sy[i] = 1f; sx[i] = 1f; falling[i] = false }
                }
                if (t >= 1f) {
                    for (i in 0 until n) { offY[i] = 0f; sy[i] = 1f; sx[i] = 1f; falling[i] = false }
                    if (intro) { intro = false; phase = Phase.IDLE; lastTouch = now; return }
                    stepIndex++
                    if (stepIndex < result!!.steps.size) startPop(now) else finish()
                }
            }
            Phase.SHUFFLE -> {
                val e = sin(t * PIf)
                for (i in 0 until n) { rot[i] = sin((t * 6f + i * 0.7f).toDouble()).toFloat() * 8f * e; sx[i] = 1f - 0.12f * e; sy[i] = 1f - 0.12f * e }
                if (t >= 1f) { for (i in 0 until n) { rot[i] = 0f; sx[i] = 1f; sy[i] = 1f }; phase = Phase.IDLE; finishMove() }
            }
            Phase.IDLE -> {}
        }
    }

    private fun startPop(now: Long) {
        val s = result!!.steps[stepIndex]
        if (s.combo >= 3) { val cb = s.combo; post { onCombo?.invoke(cb) } }
        val loud = s.fired.isNotEmpty()
        if (loud) performHapticFeedback(HapticFeedbackConstants.LONG_PRESS) else if (s.combo >= 2) performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
        // sparks from every piece that goes, a score floater and a cheer
        var cx = 0f; var cy = 0f
        for (i in s.cleared) {
            val x = (i % engine.w) * cell + cell / 2f; val y = (i / engine.w) * cell + cell / 2f
            cx += x; cy += y
            val col = tileColors[vType[i].coerceIn(0, 4)]
            emit(x, y, col, if (loud) 9 else 6, cell * (if (loud) 4.2f else 2.8f))
            emit(x, y, Color.WHITE, 2, cell * 3.2f)
        }
        for (sp in s.spawned) emit((sp.cell % engine.w) * cell + cell / 2f, (sp.cell / engine.w) * cell + cell / 2f, Color.WHITE, 14, cell * 3.5f)
        if (s.cleared.isNotEmpty()) {
            val k = fNext; fNext = (fNext + 1) % fx.size
            fx[k] = cx / s.cleared.size; fy[k] = cy / s.cleared.size; fStart[k] = now; fText[k] = "+" + s.gained
        }
        var word = -1
        for (sp in s.spawned) if (sp.special == Match3Engine.SPARKLE) word = 5
        if (word < 0 && loud) word = 4
        if (word < 0 && s.combo >= 5) word = 3 else if (word < 0 && s.combo == 4) word = 2 else if (word < 0 && s.combo == 3) word = 1 else if (word < 0 && s.combo == 2) word = 0
        if (word >= 0) { cheer = words[word.coerceIn(0, words.size - 1)]; cheerStart = now }
        if (loud || s.combo >= 4) { shakeUntil = now + 320; shakeAmp = cell * (if (loud) 0.16f else 0.1f) }
        begin(Phase.POP, if (loud) 340 else 250)
    }

    private fun endPop(now: Long) {
        val s = result!!.steps[stepIndex]
        for (i in s.cleared) { if (vDust[i] > 0) vDust[i]--; vType[i] = -1; vSpecial[i] = 0; sx[i] = 1f; sy[i] = 1f; rot[i] = 0f; flash[i] = 0f; offX[i] = 0f; offY[i] = 0f }
        for (sp in s.spawned) { if (vDust[sp.cell] > 0) vDust[sp.cell]--; vSpecial[sp.cell] = sp.special; sx[sp.cell] = 1f; sy[sp.cell] = 1f; flash[sp.cell] = 0f }
        for (i in s.fired) flash[i] = 0f
        // the fall: the same moves the engine made, on the pictured board (columns are independent); every piece has its own
        // start delay (columns one after another) and a duration that grows with the distance
        java.util.Arrays.fill(fallFrom, 0f)
        var total = 0f
        for (f in s.falls.sortedByDescending { it.to }) {
            vType[f.to] = vType[f.from]; vSpecial[f.to] = vSpecial[f.from]; vType[f.from] = -1; vSpecial[f.from] = 0
            val dist = (f.to / engine.w) - (f.from / engine.w)
            fallFrom[f.to] = -dist * cell; fallDelay[f.to] = (f.to % engine.w) * 22f; fallDur[f.to] = 230f + 55f * dist
            falling[f.to] = true; offY[f.to] = fallFrom[f.to]
            total = max(total, fallDelay[f.to] + fallDur[f.to])
        }
        for (f in s.fresh) {
            vType[f.cell] = f.type; vSpecial[f.cell] = 0
            val dist = (f.cell / engine.w) - f.fromRow
            fallFrom[f.cell] = -dist * cell; fallDelay[f.cell] = (f.cell % engine.w) * 22f + 30f; fallDur[f.cell] = 260f + 55f * dist
            falling[f.cell] = true; offY[f.cell] = fallFrom[f.cell]
            total = max(total, fallDelay[f.cell] + fallDur[f.cell])
        }
        begin(Phase.FALL, total.toLong() + 30)
    }

    private fun finish() {
        val res = result!!
        if (res.shuffled) { syncFromEngine(); begin(Phase.SHUFFLE, 480) } else { syncFromEngine(); phase = Phase.IDLE; finishMove() }
    }

    private fun finishMove() {
        val res = result ?: return
        result = null
        lastTouch = SystemClock.uptimeMillis()
        post { onMoveShown?.invoke(res) }   // not from inside onDraw
    }

    // ---- sparks ---------------------------------------------------------------------------------------------------------

    private fun emit(x: Float, y: Float, color: Int, count: Int, speed: Float) {
        for (k in 0 until count) {
            val i = pNext; pNext = (pNext + 1) % maxP
            val a = (Math.random() * 2 * PI).toFloat()
            val v = speed * (0.35f + Math.random().toFloat() * 0.75f)
            px[i] = x; py[i] = y; pvx[i] = cos(a) * v; pvy[i] = sin(a) * v - speed * 0.25f
            pmax[i] = 420f + Math.random().toFloat() * 380f; plife[i] = pmax[i]
            psize[i] = cell * (0.045f + Math.random().toFloat() * 0.06f); pcol[i] = color
        }
    }

    // ---- drawing -------------------------------------------------------------------------------------------------------

    override fun onDraw(cv: Canvas) {
        val now = SystemClock.uptimeMillis()
        val dt = if (lastFrame == 0L) 16f else min(50f, (now - lastFrame).toFloat())
        lastFrame = now
        if (phase != Phase.IDLE) advance(now)
        val w = width.toFloat(); val h = height.toFloat()
        cv.save()
        if (now < shakeUntil) { val k = (shakeUntil - now) / 320f; cv.translate(sin(now / 18.0).toFloat() * shakeAmp * k, cos(now / 23.0).toFloat() * shakeAmp * k) }
        board.color = if (Glass.NIGHT) Glass.white(.08f) else Glass.white(.45f)
        rect.set(0f, 0f, w, h); cv.drawRoundRect(rect, cell * .35f, cell * .35f, board)
        // cells like a soft chessboard
        board.color = if (Glass.NIGHT) Glass.white(.05f) else Glass.white(.28f)
        for (i in 0 until n) if (((i / engine.w) + (i % engine.w)) % 2 == 0) {
            val x = (i % engine.w) * cell; val y = (i / engine.w) * cell
            rect.set(x + cell * .04f, y + cell * .04f, x + cell * .96f, y + cell * .96f); cv.drawRoundRect(rect, cell * .22f, cell * .22f, board)
        }
        // idle life: a hint after a few seconds, a piece that wiggles now and then
        if (phase == Phase.IDLE && !locked) {
            if (hint == null && now - lastTouch > 8000) { hint = engine.findMove(); hintStart = now; lastTouch = now }
            if (now >= nextWiggle) { wiggleCell = (Math.random() * n).toInt().coerceIn(0, n - 1); wiggleStart = now; nextWiggle = now + 2000 + (Math.random() * 1800).toLong() }
        }
        if (hint != null && now - hintStart > 5000) { hint = null; lastTouch = now }
        val pad = cell * 0.06f
        val cols = engine.w
        for (i in 0 until n) {
            if (vDust[i] > 0) {
                val x = (i % cols) * cell; val y = (i / cols) * cell
                dustPaint.color = if (vDust[i] > 1) 0xCC7A6A5A.toInt() else 0x887A6A5A.toInt()
                rect.set(x + pad, y + pad, x + cell - pad, y + cell - pad); cv.drawRoundRect(rect, cell * .22f, cell * .22f, dustPaint)
            }
        }
        var wiggling = false
        for (i in 0 until n) {
            val t = vType[i]; if (t < 0) continue
            val row = i / cols; val col = i % cols
            var kx = sx[i]; var ky = sy[i]; var r = rot[i]
            if (hint?.let { i == it[0] || i == it[1] } == true) { val b = 1f + 0.1f * sin(now / 130.0).toFloat(); kx *= b; ky *= b }
            if (i == wiggleCell && phase == Phase.IDLE) {
                val wt = (now - wiggleStart) / 520f
                if (wt in 0f..1f) { wiggling = true; r += sin(wt * 3 * PIf) * 9f * (1f - wt); val b = 1f + 0.1f * sin(wt * PIf); kx *= b; ky *= b }
            }
            if (i == selected) { val b = 1.1f + 0.03f * sin(now / 120.0).toFloat(); kx *= b; ky *= b }
            if (kx <= 0.01f || ky <= 0.01f) continue
            val cx = col * cell + cell / 2f + offX[i]; val cy = row * cell + cell / 2f + offY[i]
            cv.save(); cv.translate(cx, cy); cv.rotate(r); cv.scale(kx, ky)
            val half = cell / 2f - cell * 0.07f
            val sp = vSpecial[i]
            val bmp = (if (sp == Match3Engine.NONE) plain[t % 5] else special[t % 5])!!   // made in onSizeChanged, before any draw
            cv.drawBitmap(bmp, -bmp.width / 2f, -bmp.height / 2f, null)
            if (sp != Match3Engine.NONE) {
                val sym = when (sp) { Match3Engine.ROLLER_H -> "↔"; Match3Engine.ROLLER_V -> "↕"; Match3Engine.SPLASH -> "✹"; else -> "★" }
                if (sp == Match3Engine.SPARKLE) { hsv[0] = ((now / 6) % 360).toFloat(); hsv[1] = .35f; hsv[2] = 1f; mark.color = Color.HSVToColor(hsv) } else mark.color = Color.WHITE
                cv.drawText(sym, cell * .06f, cell * .2f, mark)
                ring.color = Color.WHITE; ring.alpha = (170 + 70 * sin(now / 200.0 + i)).toInt().coerceIn(0, 255)
                cv.drawCircle(0f, 0f, half * (0.96f + 0.03f * sin(now / 220.0 + i).toFloat()), ring)
            }
            if (flash[i] > 0.02f) { tile.color = Color.argb((flash[i] * 170).toInt().coerceIn(0, 255), 255, 255, 255); rect.set(-half, -half, half, half); cv.drawRoundRect(rect, cell * .26f, cell * .26f, tile) }
            if (i == selected) { ring.color = Glass.ACCENT; ring.alpha = 255; rect.set(-half, -half, half, half); cv.drawRoundRect(rect, cell * .26f, cell * .26f, ring) }
            cv.restore()
        }
        // blasts of fired specials
        for (i in 0 until n) if (flash[i] > 0.02f && vSpecial[i] != Match3Engine.NONE && phase == Phase.POP) {
            val cx = (i % cols) * cell + cell / 2f; val cy = (i / cols) * cell + cell / 2f
            tile.color = Color.argb((flash[i] * 150).toInt().coerceIn(0, 255), 255, 255, 255)
            when (vSpecial[i]) {
                Match3Engine.ROLLER_H -> { rect.set(0f, cy - cell * .3f, w, cy + cell * .3f); cv.drawRect(rect, tile) }
                Match3Engine.ROLLER_V -> { rect.set(cx - cell * .3f, 0f, cx + cell * .3f, h); cv.drawRect(rect, tile) }
                Match3Engine.SPLASH -> cv.drawCircle(cx, cy, cell * 1.8f * (1.2f - flash[i]), tile)
                else -> { rect.set(0f, 0f, w, h); cv.drawRect(rect, tile) }
            }
        }
        // sparks
        var alive = 0
        for (i in 0 until maxP) if (plife[i] > 0f) {
            plife[i] -= dt
            if (plife[i] <= 0f) continue
            alive++
            val k = dt / 1000f
            pvy[i] += cell * 7f * k   // gravity
            px[i] += pvx[i] * k; py[i] += pvy[i] * k
            val a = (plife[i] / pmax[i]).coerceIn(0f, 1f)
            spark.color = pcol[i]; spark.alpha = (255 * a).toInt().coerceIn(0, 255)
            cv.drawCircle(px[i], py[i], psize[i] * (0.4f + a), spark)
        }
        pAlive = alive
        // score floaters
        var floaters = false
        for (k in fx.indices) {
            val age = (now - fStart[k]) / 900f
            if (age in 0f..1f) {
                floaters = true
                mark.textSize = cell * 0.42f; mark.color = Color.WHITE; mark.alpha = (255 * (1f - age * age)).toInt().coerceIn(0, 255)
                cv.drawText(fText[k] ?: "", fx[k], fy[k] - cell * 0.9f * easeOut(age), mark)
                mark.alpha = 255; mark.textSize = cell * 0.5f
            }
        }
        // the cheer
        var cheering = false
        val ca = (now - cheerStart) / 1100f
        if (ca in 0f..1f && cheer != null) {
            cheering = true
            val pop = if (ca < 0.18f) easeBack(ca / 0.18f) else 1f
            val fade = if (ca < 0.7f) 1f else 1f - (ca - 0.7f) / 0.3f
            big.color = Color.HSVToColor((255 * fade).toInt().coerceIn(0, 255), floatArrayOf((now / 4 % 360).toFloat(), 0.45f, 1f))
            big.alpha = (255 * fade).toInt().coerceIn(0, 255)
            cv.save(); cv.translate(w / 2f, h * 0.42f); cv.rotate(-6f); cv.scale(pop, pop)
            cv.drawText(cheer!!, 0f, 0f, big)
            cv.restore()
        }
        cv.restore()
        // what keeps the loop going
        removeCallbacks(wake)
        val animating = phase != Phase.IDLE || alive > 0 || floaters || cheering || now < shakeUntil || wiggling || (hint != null && !locked)
        if (animating) postInvalidateOnAnimation()
        else if (!locked) {
            val nextHint = if (now - lastTouch <= 8000) 8000 - (now - lastTouch) else Long.MAX_VALUE
            val wait = min(nextHint, max(60L, nextWiggle - now))
            postDelayed(wake, wait.coerceAtLeast(60L))
        }
    }

    private fun drawGlyph(cv: Canvas, text: String, dx: Float, dy: Float, size: Float) {
        glyph.textSize = size
        glyph.getFontMetrics(fm)
        cv.drawText(text, dx, dy - (fm.ascent + fm.descent) / 2f, glyph)
        glyph.textSize = cell * 0.52f
    }
}
