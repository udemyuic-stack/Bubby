package com.example.buddy.ui.home

import android.graphics.Canvas

class IdleShow : Show() {
    override val durationMs = 0L

    override fun onDraw(c: Canvas, scale: Float, ms: Long) {}
}
