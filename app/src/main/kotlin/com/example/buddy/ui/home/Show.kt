package com.example.buddy.ui.home

import android.graphics.Canvas

abstract class Show {
    var originX = 0f
    var originY = 0f

    var running = false
        private set
    private var startedMs = 0L

    protected val elapsedMs get() = (System.nanoTime() / 1_000_000L - startedMs).coerceAtLeast(0L)

    abstract val durationMs: Long

    fun start() {
        if (running) return
        running = true
        startedMs = System.nanoTime() / 1_000_000L
        onStart()
    }

    fun cancel() {
        if (!running) return
        running = false
        onCancel()
    }

    fun draw(c: Canvas, scale: Float) {
        if (!running) return
        if (elapsedMs >= durationMs) { cancel(); return }
        onDraw(c, scale, elapsedMs)
    }

    protected open fun onStart() {}
    protected open fun onCancel() {}
    protected abstract fun onDraw(c: Canvas, scale: Float, ms: Long)
}
