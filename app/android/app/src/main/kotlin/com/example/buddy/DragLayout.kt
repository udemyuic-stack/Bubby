package com.example.buddy

import android.content.Context
import android.os.SystemClock
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.ViewConfiguration
import android.widget.LinearLayout
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max

/**
 * Vertical box that turns a finger move (past touch slop) into a drag of the whole window; taps still reach the children.
 * When [canPet] is true (collapsed mascot only) a rubbing gesture (x or y direction reverses twice within 40 dp of the
 * touch-down point) is reported as petting instead: no drag, [onPet] about every 3 s, soft haptic ticks.
 */
class DragLayout(c: Context) : LinearLayout(c) {
    var onDrag: (dx: Float, dy: Float) -> Unit = { _, _ -> }
    var onDragStart: () -> Unit = {}
    var onDragEnd: () -> Unit = {}
    var canPet: () -> Boolean = { false }
    var onPet: () -> Unit = {}
    var onPetEnd: () -> Unit = {}
    /** Finger position in this layout's coordinates while touching (the mascot's eyes follow it) and the end of the touch. */
    var onTouchAt: (x: Float, y: Float) -> Unit = { _, _ -> }
    var onTouchEnd: () -> Unit = {}
    var onPinch: (factor: Float) -> Unit = {}
    var onPinchEnd: () -> Unit = {}

    private val u = c.resources.displayMetrics.density
    private val slop = ViewConfiguration.get(c).scaledTouchSlop
    private val petRange = 40f * u
    private val petStep = 5f * u
    private var sx = 0f; private var sy = 0f
    private var lx = 0f; private var ly = 0f
    private var dragging = false
    private var petting = false
    private var petOk = false
    private var pinching = false
    private var ignoreRest = false
    private val scaler = ScaleGestureDetector(c, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(d: ScaleGestureDetector): Boolean { onPinch(d.scaleFactor); return true }
    })
    private var lastPet = 0L
    private var lastTick = 0L
    private var tickX = 0f; private var tickY = 0f

    /** Tracks direction reversals of one coordinate. */
    private class Axis {
        var dir = 0; var ext = 0f; var rev = 0
        fun reset(v: Float) { dir = 0; ext = v; rev = 0 }
        fun feed(v: Float, step: Float) {
            when {
                dir == 0 -> if (abs(v - ext) >= step) { dir = if (v > ext) 1 else -1; ext = v }
                dir > 0 -> if (v > ext) ext = v else if (ext - v >= step) { dir = -1; ext = v; rev++ }
                else -> if (v < ext) ext = v else if (v - ext >= step) { dir = 1; ext = v; rev++ }
            }
        }
    }
    private val ax = Axis()
    private val ay = Axis()

    private fun cancelDragAndPet() {
        if (dragging) { dragging = false; onDragEnd() }
        if (petting) { petting = false; onPetEnd() }
    }

    private fun initDown(e: MotionEvent) {
        if (pinching) { pinching = false; onPinchEnd() }
        cancelDragAndPet()
        ignoreRest = false
        sx = e.rawX; sy = e.rawY; lx = sx; ly = sy
        petOk = try { canPet() } catch (_: Exception) { false }
        ax.reset(sx); ay.reset(sy)
    }

    private fun startPinch() {
        if (pinching) return
        pinching = true; ignoreRest = true
        cancelDragAndPet()
    }

    /** Pet / drag detection on ACTION_MOVE; shared by the intercept and the touch path. */
    private fun detect(e: MotionEvent) {
        if (e.actionMasked != MotionEvent.ACTION_MOVE || dragging || petting) return
        val dist = hypot(e.rawX - sx, e.rawY - sy)
        if (petOk) {
            ax.feed(e.rawX, petStep); ay.feed(e.rawY, petStep)
            if ((ax.rev >= 2 || ay.rev >= 2) && dist <= petRange) {
                petting = true
                val now = SystemClock.uptimeMillis()
                lastPet = now; lastTick = now; tickX = e.rawX; tickY = e.rawY
                performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                onPet()
                return
            }
        }
        // while petting is still possible, the drag waits for 40 dp of travel; the window then catches up in one go
        val threshold = if (petOk) max(slop.toFloat(), petRange) else slop.toFloat()
        if (dist > threshold) {
            dragging = true
            if (!petOk) { lx = e.rawX; ly = e.rawY }
            onDragStart()
        }
    }

    private fun reportTouch(e: MotionEvent) {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> onTouchAt(e.x, e.y)
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> onTouchEnd()
        }
    }

    override fun onInterceptTouchEvent(e: MotionEvent): Boolean {
        reportTouch(e)
        if (e.actionMasked == MotionEvent.ACTION_DOWN) initDown(e)
        scaler.onTouchEvent(e)
        if (e.actionMasked == MotionEvent.ACTION_POINTER_DOWN || scaler.isInProgress) {
            startPinch()
            return true
        }
        if (pinching || ignoreRest) return true
        detect(e)
        return dragging || petting
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        reportTouch(e)
        if (e.actionMasked == MotionEvent.ACTION_DOWN) initDown(e) else scaler.onTouchEvent(e)
        if (e.actionMasked == MotionEvent.ACTION_POINTER_DOWN || scaler.isInProgress) startPinch()
        if (pinching || ignoreRest) {
            val end = e.actionMasked == MotionEvent.ACTION_UP || e.actionMasked == MotionEvent.ACTION_CANCEL ||
                (e.actionMasked == MotionEvent.ACTION_POINTER_UP && e.pointerCount <= 2)
            if (end && pinching) { pinching = false; onPinchEnd() }
            if (e.actionMasked == MotionEvent.ACTION_UP || e.actionMasked == MotionEvent.ACTION_CANCEL) ignoreRest = false
            return true
        }
        if (!dragging && !petting) detect(e)
        when (e.actionMasked) {
            MotionEvent.ACTION_MOVE -> {
                if (dragging) {
                    onDrag(e.rawX - lx, e.rawY - ly); lx = e.rawX; ly = e.rawY
                } else if (petting) {
                    val now = SystemClock.uptimeMillis()
                    if (now - lastPet >= 3000) { lastPet = now; onPet() }
                    if (now - lastTick >= 120 && hypot(e.rawX - tickX, e.rawY - tickY) >= 4f * u) {
                        lastTick = now; tickX = e.rawX; tickY = e.rawY
                        performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                    }
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (dragging) { dragging = false; onDragEnd() }
                if (petting) { petting = false; onPetEnd() }
            }
        }
        return true
    }
}
