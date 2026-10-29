package com.example.buddy.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.os.SystemClock
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import kotlin.math.abs
import kotlin.math.sin

/**
 * The board of "Bubby's Room": one view, one onDraw. The engine has already played the move when the animation starts;
 * this view keeps its own "what is shown" arrays and replays the engine's steps on them (swap, pop, fall), so that the
 * screen is never ahead of or behind what the player sees. Animation runs only while something moves.
 */
class MatchBoardView(c: Context, val engine: Match3Engine) : View(c) {
    /** Called when a move (with all its cascades) has been shown to the end. */
    var onMoveShown: ((Match3Engine.Result) -> Unit)? = null
    /** A swap that is not allowed was tried. */
    var onRefused: (() -> Unit)? = null
    var onCombo: ((Int) -> Unit)? = null
    var locked = false

    private val n = engine.w * engine.h
    private val vType = IntArray(n) { -1 }
    private val vSpecial = IntArray(n)
    private val vDust = IntArray(n)
    private val offX = FloatArray(n)
    private val offY = FloatArray(n)
    private val scale = FloatArray(n) { 1f }
    private val flash = FloatArray(n)

    private enum class Phase { IDLE, SWAP, POP, FALL, SHAKE }
    private var phase = Phase.IDLE
    private var phaseStart = 0L
    private var phaseDur = 1L
    private var result: Match3Engine.Result? = null
    private var stepIndex = 0
    private var swapA = -1; private var swapB = -1
    private var refused = false

    private var selected = -1
    private var hint: IntArray? = null
    private var lastTouch = SystemClock.uptimeMillis()
    private var hintStart = 0L
    private val wake = Runnable { invalidate() }
    private val fm = Paint.FontMetrics()
    private val hsv = FloatArray(3)

    private val tileColors = intArrayOf(0xFF6EC1FF.toInt(), 0xFFFFB066.toInt(), 0xFFFFE066.toInt(), 0xFFFF8FB1.toInt(), 0xFF8EE08E.toInt())
    private val emoji = arrayOf("💧", "🖌️", "💡", "🛋️", "🌿")
    private val board = Paint(Paint.ANTI_ALIAS_FLAG)
    private val tile = Paint(Paint.ANTI_ALIAS_FLAG)
    private val glyph = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }
    private val mark = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER; typeface = android.graphics.Typeface.DEFAULT_BOLD; color = Color.WHITE; setShadowLayer(4f, 0f, 2f, 0xAA000000.toInt()) }
    private val dustPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val rect = RectF()
    private var cell = 0f

    init { syncFromEngine() }

    fun syncFromEngine() {
        for (i in 0 until n) { vType[i] = engine.type[i]; vSpecial[i] = engine.special[i]; vDust[i] = engine.dust[i]; offX[i] = 0f; offY[i] = 0f; scale[i] = 1f; flash[i] = 0f }
        phase = Phase.IDLE; selected = -1; hint = null; lastTouch = SystemClock.uptimeMillis()
        invalidate()
    }

    override fun onMeasure(wSpec: Int, hSpec: Int) {
        val w = MeasureSpec.getSize(wSpec)
        setMeasuredDimension(w, (w * engine.h / engine.w.toFloat()).toInt())
    }

    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) {
        cell = w / engine.w.toFloat()
        glyph.textSize = cell * 0.52f
        mark.textSize = cell * 0.5f
        ring.strokeWidth = cell * 0.07f
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
            }
            MotionEvent.ACTION_MOVE -> if (!swiped && downCell >= 0) {
                val dx = e.x - downX; val dy = e.y - downY
                if (abs(dx) > cell * 0.35f || abs(dy) > cell * 0.35f) {
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
            // show the two pieces going toward each other and back
            swapA = a; swapB = b; refused = true; result = null
            begin(Phase.SWAP, 220)
            onRefused?.invoke()
            return
        }
        swapA = a; swapB = b; refused = false; result = res; stepIndex = 0
        begin(Phase.SWAP, 140)
    }

    private fun begin(p: Phase, ms: Long) { phase = p; phaseStart = SystemClock.uptimeMillis(); phaseDur = ms; invalidate() }

    private fun easeOut(t: Float): Float { val k = 1f - t; return 1f - k * k * k }
    private fun easeBack(t: Float): Float { val k = t - 1f; return 1f + 2.2f * k * k * k + 1.2f * k * k }

    /** Moves the animation on by the time passed; changes phase when one has finished. */
    private fun advance(now: Long) {
        val t = ((now - phaseStart).toFloat() / phaseDur).coerceIn(0f, 1f)
        when (phase) {
            Phase.SWAP -> {
                val e = if (refused) sin(t * Math.PI).toFloat() * 0.5f else easeOut(t)
                val ax = (swapB % engine.w - swapA % engine.w) * cell * e; val ay = (swapB / engine.w - swapA / engine.w) * cell * e
                offX[swapA] = ax; offY[swapA] = ay; offX[swapB] = -ax; offY[swapB] = -ay
                if (t >= 1f) {
                    offX[swapA] = 0f; offY[swapA] = 0f; offX[swapB] = 0f; offY[swapB] = 0f
                    if (refused) { phase = Phase.IDLE; return }
                    val ty = vType[swapA]; vType[swapA] = vType[swapB]; vType[swapB] = ty
                    val sp = vSpecial[swapA]; vSpecial[swapA] = vSpecial[swapB]; vSpecial[swapB] = sp
                    startPop()
                }
            }
            Phase.POP -> {
                val s = result!!.steps[stepIndex]
                for (i in s.cleared) { scale[i] = 1f - easeOut(t); flash[i] = 1f - t }
                for (sp in s.spawned) scale[sp.cell] = 1f + 0.3f * sin(t * Math.PI).toFloat()
                for (i in s.fired) flash[i] = 1f - t
                if (t >= 1f) endPop()
            }
            Phase.FALL -> {
                val e = easeBack(t)
                val s = result!!.steps[stepIndex]
                for (f in s.falls) offY[f.to] = fallFrom[f.to] * (1f - e)
                for (f in s.fresh) offY[f.cell] = fallFrom[f.cell] * (1f - e)
                if (t >= 1f) {
                    for (i in 0 until n) { offY[i] = 0f }
                    stepIndex++
                    if (stepIndex < result!!.steps.size) startPop() else finish()
                }
            }
            Phase.SHAKE -> if (t >= 1f) { phase = Phase.IDLE; finishMove() }
            Phase.IDLE -> {}
        }
    }

    private val fallFrom = FloatArray(n)

    private fun startPop() {
        val s = result!!.steps[stepIndex]
        if (s.combo >= 3) { val cb = s.combo; post { onCombo?.invoke(cb) } }
        if (s.fired.isNotEmpty()) performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        begin(Phase.POP, if (s.fired.isNotEmpty()) 280 else 190)
    }

    private fun endPop() {
        val s = result!!.steps[stepIndex]
        for (i in s.cleared) { if (vDust[i] > 0) vDust[i]--; vType[i] = -1; vSpecial[i] = 0; scale[i] = 1f; flash[i] = 0f }
        for (sp in s.spawned) { if (vDust[sp.cell] > 0) vDust[sp.cell]--; vSpecial[sp.cell] = sp.special; scale[sp.cell] = 1f }
        for (i in s.fired) flash[i] = 0f
        // the fall: the same moves the engine made, on the pictured board (columns are independent)
        java.util.Arrays.fill(fallFrom, 0f)
        val falls = s.falls.sortedByDescending { it.to }
        for (f in falls) {
            vType[f.to] = vType[f.from]; vSpecial[f.to] = vSpecial[f.from]; vType[f.from] = -1; vSpecial[f.from] = 0
            fallFrom[f.to] = -((f.to / engine.w) - (f.from / engine.w)) * cell
        }
        for (f in s.fresh) { vType[f.cell] = f.type; vSpecial[f.cell] = 0; fallFrom[f.cell] = (f.fromRow - f.cell / engine.w) * cell }
        begin(Phase.FALL, 300)
    }

    private fun finish() {
        val res = result!!
        if (res.shuffled) { syncFromEngine(); begin(Phase.SHAKE, 350) } else { syncFromEngine(); phase = Phase.IDLE; finishMove() }
    }

    private fun finishMove() {
        val res = result ?: return
        result = null
        lastTouch = SystemClock.uptimeMillis()
        post { onMoveShown?.invoke(res) }   // not from inside onDraw
    }

    // ---- drawing -------------------------------------------------------------------------------------------------------

    override fun onDraw(cv: Canvas) {
        val now = SystemClock.uptimeMillis()
        if (phase != Phase.IDLE) advance(now)
        val w = width.toFloat(); val h = height.toFloat()
        board.color = if (Glass.NIGHT) Glass.white(.08f) else Glass.white(.45f)
        rect.set(0f, 0f, w, h); cv.drawRoundRect(rect, cell * .35f, cell * .35f, board)
        // hint after a few seconds without a touch
        if (phase == Phase.IDLE && !locked && hint == null && now - lastTouch > 8000) { hint = engine.findMove(); hintStart = now; lastTouch = now }
        if (hint != null && now - hintStart > 5000) { hint = null; lastTouch = now }   // the pulse stops after five seconds, then the wait starts again
        val pad = cell * 0.06f
        val rowsCols = engine.w
        for (i in 0 until n) {
            val row = i / rowsCols; val col = i % rowsCols
            val x = col * cell; val y = row * cell
            // dust lies under the piece
            if (vDust[i] > 0) {
                dustPaint.color = if (vDust[i] > 1) 0xCC7A6A5A.toInt() else 0x887A6A5A.toInt()
                rect.set(x + pad, y + pad, x + cell - pad, y + cell - pad); cv.drawRoundRect(rect, cell * .22f, cell * .22f, dustPaint)
            }
        }
        for (i in 0 until n) {
            val t = vType[i]; if (t < 0) continue
            val row = i / rowsCols; val col = i % rowsCols
            var s = scale[i]
            val hinted = hint?.let { i == it[0] || i == it[1] } == true
            if (hinted) s *= 1f + 0.09f * sin(now / 140.0).toFloat()
            val cx = col * cell + cell / 2f + offX[i]; val cy = row * cell + cell / 2f + offY[i]
            if (s <= 0.01f) continue
            cv.save(); cv.translate(cx, cy); cv.scale(s, s)
            val half = cell / 2f - cell * 0.07f
            tile.color = tileColors[t % tileColors.size]
            rect.set(-half, -half, half, half); cv.drawRoundRect(rect, cell * .26f, cell * .26f, tile)
            // soft highlight on the top half
            tile.color = Glass.white(.28f)
            rect.set(-half + cell * .05f, -half + cell * .04f, half - cell * .05f, 0f); cv.drawRoundRect(rect, cell * .22f, cell * .22f, tile)
            val sp = vSpecial[i]
            if (sp == Match3Engine.NONE) drawGlyph(cv, emoji[t % emoji.size], 0f, 0f, glyph.textSize)
            else {
                drawGlyph(cv, emoji[t % emoji.size], -cell * .17f, -cell * .17f, cell * .3f)
                val sym = when (sp) { Match3Engine.ROLLER_H -> "↔"; Match3Engine.ROLLER_V -> "↕"; Match3Engine.SPLASH -> "✹"; else -> "★" }
                if (sp == Match3Engine.SPARKLE) { hsv[0] = ((now / 6) % 360).toFloat(); hsv[1] = .35f; hsv[2] = 1f; mark.color = Color.HSVToColor(hsv) } else mark.color = Color.WHITE
                cv.drawText(sym, cell * .06f, cell * .2f, mark)
                ring.color = Color.WHITE; ring.alpha = 220
                cv.drawCircle(0f, 0f, half * .98f, ring)
            }
            if (i == selected) { ring.color = Glass.ACCENT; ring.alpha = 255; rect.set(-half, -half, half, half); cv.drawRoundRect(rect, cell * .26f, cell * .26f, ring) }
            cv.restore()
        }
        // blasts of fired specials
        for (i in 0 until n) if (flash[i] > 0.02f && vSpecial[i] != Match3Engine.NONE) {
            val row = i / rowsCols; val col = i % rowsCols
            val cx = col * cell + cell / 2f; val cy = row * cell + cell / 2f
            tile.color = Color.argb((flash[i] * 150).toInt(), 255, 255, 255)
            when (vSpecial[i]) {
                Match3Engine.ROLLER_H -> { rect.set(0f, cy - cell * .3f, w, cy + cell * .3f); cv.drawRect(rect, tile) }
                Match3Engine.ROLLER_V -> { rect.set(cx - cell * .3f, 0f, cx + cell * .3f, h); cv.drawRect(rect, tile) }
                Match3Engine.SPLASH -> cv.drawCircle(cx, cy, cell * 1.6f * (1.2f - flash[i]), tile)
                else -> { rect.set(0f, 0f, w, h); cv.drawRect(rect, tile) }
            }
        }
        removeCallbacks(wake)
        if (phase != Phase.IDLE || (hint != null && !locked)) postInvalidateOnAnimation()
        else if (!locked && now - lastTouch <= 8000) postDelayed(wake, (8000 - (now - lastTouch)).coerceAtLeast(100))   // one wake-up for the hint
    }

    private fun drawGlyph(cv: Canvas, text: String, dx: Float, dy: Float, size: Float) {
        glyph.textSize = size
        glyph.getFontMetrics(fm)
        cv.drawText(text, dx, dy - (fm.ascent + fm.descent) / 2f, glyph)
        glyph.textSize = cell * 0.52f
    }
}
