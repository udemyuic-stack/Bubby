package com.example.buddy.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.view.View
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

class BattleView(c: Context, private val game: BattleGame) : View(c) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = glassFont(bold = true); textAlign = Paint.Align.CENTER }
    private val rect = RectF()

    private var playerShake = 0f
    private var botShake = 0f
    private var flash = 0f
    private var flashColor = 0
    private var floatText = ""
    private var floatT = 0f
    private var floatOnBot = false
    private var floatColor = 0
    private var shownPlayer = 100f
    private var shownBot = 100f
    private var clock = 0f

    private var wantRun = false
    private var running = false
    private var last = 0L
    private val tick = Runnable { step() }

    var onHide: (() -> Unit)? = null

    fun play(on: Boolean) { wantRun = on; sync() }

    private fun sync() {
        val go = wantRun && isAttachedToWindow && windowVisibility == VISIBLE
        if (go == running) return
        running = go
        if (go) { last = System.nanoTime(); postOnAnimation(tick) } else removeCallbacks(tick)
    }

    fun onGameEvent(e: BattleGame.Ev, value: Int) {
        when (e) {
            BattleGame.Ev.PLAYER_HIT -> { botShake = 0.4f; pop("-$value", true, 0xFFFFD23A.toInt()); flashColor = 0xFF8DFF9B.toInt(); flash = 0.3f }
            BattleGame.Ev.BOT_HIT -> { playerShake = 0.4f; pop("-$value", false, 0xFFFF5470.toInt()); flashColor = 0xFFFF5470.toInt(); flash = 0.3f }
            BattleGame.Ev.PLAYER_WRONG, BattleGame.Ev.TIMEOUT -> { playerShake = 0.4f; pop("-$value", false, 0xFFFF5470.toInt()); flashColor = 0xFFFF5470.toInt(); flash = 0.3f }
            else -> {}
        }
    }

    private fun pop(s: String, onBot: Boolean, color: Int) { floatText = s; floatOnBot = onBot; floatColor = color; floatT = 0.9f }

    private fun step() {
        if (!running) return
        val now = System.nanoTime()
        val dt = (now - last) / 1e9f
        if (dt >= 0.030f) {
            last = now
            val d = min(dt, 0.06f)
            game.update(d)
            clock += d
            playerShake = max(0f, playerShake - d)
            botShake = max(0f, botShake - d)
            flash = max(0f, flash - d)
            floatT = max(0f, floatT - d)
            shownPlayer += (game.playerHp - shownPlayer) * min(1f, d * 8f)
            shownBot += (game.botHp - shownBot) * min(1f, d * 8f)
            invalidate()
        }
        postOnAnimation(tick)
    }

    override fun onAttachedToWindow() { super.onAttachedToWindow(); sync() }
    override fun onDetachedFromWindow() { super.onDetachedFromWindow(); running = false; removeCallbacks(tick) }
    override fun onWindowVisibilityChanged(visibility: Int) {
        super.onWindowVisibilityChanged(visibility)
        if (visibility != VISIBLE && running) onHide?.invoke()
        sync()
    }

    override fun onDraw(cv: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        paint.color = 0xFF14102B.toInt()
        rect.set(0f, 0f, w, h)
        cv.drawRoundRect(rect, 36f, 36f, paint)
        if (flash > 0f) {
            paint.color = (flashColor and 0xFFFFFF) or ((flash / 0.3f * 70).toInt() shl 24)
            cv.drawRoundRect(rect, 36f, 36f, paint)
        }

        val r = min(w * 0.14f, h * 0.3f)
        val cy = h * 0.42f
        drawAvatar(cv, w * 0.22f + shake(playerShake), cy + bob(0f), r, 0xFF6FA0EE.toInt(), false)
        drawAvatar(cv, w * 0.78f + shake(botShake), cy + bob(1.5f), r, game.opp.color, true)

        textPaint.color = 0xFFFFFFFF.toInt()
        textPaint.textSize = r * 0.5f
        cv.drawText("VS", w / 2f, cy + r * 0.2f, textPaint)

        hpBar(cv, w * 0.04f, h * 0.82f, w * 0.40f, shownPlayer, 0xFF8DFF9B.toInt())
        hpBar(cv, w * 0.56f, h * 0.82f, w * 0.40f, shownBot, 0xFFFF7BE0.toInt())

        val frac = if (game.resolved) 0f else game.timeLeft / game.opp.timer
        paint.color = 0x33FFFFFF
        rect.set(w * 0.04f, h * 0.94f, w * 0.96f, h * 0.975f)
        cv.drawRoundRect(rect, 8f, 8f, paint)
        paint.color = if (frac < 0.3f) 0xFFFF5470.toInt() else 0xFFFFD23A.toInt()
        rect.set(w * 0.04f, h * 0.94f, w * 0.04f + w * 0.92f * frac, h * 0.975f)
        cv.drawRoundRect(rect, 8f, 8f, paint)

        if (floatT > 0f) {
            textPaint.color = floatColor
            textPaint.alpha = (floatT / 0.9f * 255).toInt().coerceIn(0, 255)
            textPaint.textSize = r * 0.7f
            val x = if (floatOnBot) w * 0.78f else w * 0.22f
            cv.drawText(floatText, x, cy - r - (0.9f - floatT) * 40f, textPaint)
            textPaint.alpha = 255
        }
    }

    private fun shake(t: Float) = if (t > 0f) sin(t * 80f) * t * 40f else 0f
    private fun bob(phase: Float) = sin(clock * 3f + phase) * 4f

    private fun hpBar(cv: Canvas, x: Float, y: Float, w: Float, hp: Float, color: Int) {
        val bh = height * 0.07f
        paint.color = 0x33FFFFFF
        rect.set(x, y, x + w, y + bh)
        cv.drawRoundRect(rect, bh / 2f, bh / 2f, paint)
        paint.color = color
        rect.set(x, y, x + w * (hp / 100f).coerceIn(0f, 1f), y + bh)
        cv.drawRoundRect(rect, bh / 2f, bh / 2f, paint)
    }

    private fun drawAvatar(cv: Canvas, x: Float, y: Float, r: Float, color: Int, bot: Boolean) {
        paint.color = color
        cv.drawCircle(x, y, r, paint)
        paint.color = 0xFF1A1030.toInt()
        cv.drawCircle(x - r * 0.32f, y - r * 0.15f, r * 0.11f, paint)
        cv.drawCircle(x + r * 0.32f, y - r * 0.15f, r * 0.11f, paint)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = r * 0.07f
        rect.set(x - r * 0.35f, y - r * 0.05f, x + r * 0.35f, y + r * 0.45f)
        val sad = if (bot) game.botHp < 30 else game.playerHp < 30
        cv.drawArc(rect, if (sad) 200f else 20f, 140f, false, paint)
        paint.style = Paint.Style.FILL
    }
}
