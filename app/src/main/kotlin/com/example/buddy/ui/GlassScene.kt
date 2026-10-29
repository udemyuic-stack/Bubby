package com.example.buddy.ui

import android.graphics.Canvas

/** Native animated background of the Glass look (placeholder until it is ported from assets/scene/glass.html). */
class GlassScene : ThemeScene() {
    override val ready = false
    override fun draw(cv: Canvas, w: Float, h: Float, t: Float, dt: Float, page: Float) {}
}
