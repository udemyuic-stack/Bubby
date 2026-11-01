package com.example.buddy.ui.home

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.PowerManager
import android.provider.Settings
import com.example.buddy.ui.Backend
import android.view.MotionEvent
import android.view.View
import android.view.animation.LinearInterpolator

abstract class HomeScene(ctx: Context, protected val host: Host) : View(ctx), SensorEventListener {
    interface Host {
        fun nav(name: String)
        fun ask(text: String)
        fun toggle()
    }

    protected open val tripleTap = TapCounter()
    protected val hotspots = ArrayList<Hotspot>()
    protected var scale = 1f
        private set
    protected var seconds = 0f
        private set

    protected var tiltX = 0f
        private set
    protected var tiltY = 0f
        private set
    private var rawX = 0f
    private var rawY = 0f
    private var tiltOn = false

    private val clock = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = CLOCK_PERIOD_MS
        repeatCount = ValueAnimator.INFINITE
        interpolator = LinearInterpolator()
        addUpdateListener { seconds = (System.nanoTime() / 1_000_000L) / 1000f; invalidate() }
    }

    abstract fun setLabels(labels: List<String>)

    abstract fun setStatus(running: Boolean, text: String)

    abstract fun setIslandOpen(open: Boolean)

    protected abstract val show: Show

    protected abstract fun drawScene(c: Canvas, seconds: Float)

    protected abstract fun onMascotTap()

    protected abstract fun mascotArea(): Hotspot

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        scale = w / DESIGN_WIDTH
    }

    protected fun shift(c: Canvas, depth: Float) = c.translate(tiltX * depth * scale, tiltY * depth * scale)

    protected fun hitShifted(spot: Hotspot, x: Float, y: Float, depth: Float) =
        spot.hit(x - tiltX * depth * scale, y - tiltY * depth * scale, scale)

    private fun tiltAllowed(): Boolean {
        if (!Backend.bool(context, "scene_anim", true)) return false
        if (Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) <= 0f) return false
        return (context.getSystemService(Context.POWER_SERVICE) as PowerManager).isInteractive
    }

    private fun tiltStart() {
        if (tiltOn || !tiltAllowed()) return
        try {
            val sm = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
            val sensor = sm.getDefaultSensor(Sensor.TYPE_GRAVITY) ?: sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) ?: return
            tiltOn = sm.registerListener(this, sensor, SensorManager.SENSOR_DELAY_UI)
        } catch (_: Exception) {
        }
    }

    private fun tiltStop() {
        if (tiltOn) {
            tiltOn = false
            try {
                (context.getSystemService(Context.SENSOR_SERVICE) as SensorManager).unregisterListener(this)
            } catch (_: Exception) {
            }
        }
        rawX = 0f
        rawY = 0f
    }

    override fun onSensorChanged(e: SensorEvent) {
        val x = (-e.values[0] / TILT_RANGE).coerceIn(-1f, 1f)
        val y = (e.values[1] / TILT_RANGE - TILT_REST).coerceIn(-1f, 1f)
        rawX += (x - rawX) * TILT_LOW_PASS
        rawY += (y - rawY) * TILT_LOW_PASS
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    override fun onWindowVisibilityChanged(visibility: Int) {
        super.onWindowVisibilityChanged(visibility)
        if (visibility == VISIBLE && isAttachedToWindow) tiltStart() else tiltStop()
    }

    override fun onDraw(c: Canvas) {
        tiltX += (rawX * TILT_MAX - tiltX) * TILT_FOLLOW
        tiltY += (rawY * TILT_MAX - tiltY) * TILT_FOLLOW
        drawScene(c, seconds)
        show.draw(c, scale)
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        if (e.actionMasked != MotionEvent.ACTION_UP) return e.actionMasked == MotionEvent.ACTION_DOWN
        val mascot = mascotArea()
        if (hitShifted(mascot, e.x, e.y, DEPTH_MASCOT)) {
            onMascotTap()
            if (tripleTap.tap(e.eventTime) && Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) > 0f) {
                show.originX = e.x
                show.originY = e.y
                show.start()
            }
            return true
        }
        hotspots.firstOrNull { hitShifted(it, e.x, e.y, DEPTH_PETAL) }?.onTap?.invoke()
        return true
    }

    override fun onAttachedToWindow() { super.onAttachedToWindow(); clock.start(); tiltStart() }

    override fun onDetachedFromWindow() { tiltStop(); clock.cancel(); show.cancel(); super.onDetachedFromWindow() }

    override fun onVisibilityChanged(changed: View, visibility: Int) {
        super.onVisibilityChanged(changed, visibility)
        if (visibility == VISIBLE && isAttachedToWindow) { clock.start(); tiltStart() } else { clock.cancel(); show.cancel(); tiltStop() }
    }

    companion object {
        const val DESIGN_WIDTH = 390f
        const val DEPTH_BACKDROP = 0.3f
        const val DEPTH_MASCOT = 1f
        const val DEPTH_PETAL = 0.6f
        private const val CLOCK_PERIOD_MS = 1000L
        private const val TILT_MAX = 6f
        private const val TILT_RANGE = 5f
        private const val TILT_REST = 0.7f
        private const val TILT_LOW_PASS = 0.2f
        private const val TILT_FOLLOW = 0.15f
    }
}
