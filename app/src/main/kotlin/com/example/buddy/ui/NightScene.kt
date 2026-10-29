package com.example.buddy.ui

import android.graphics.Canvas

/** Native animated background of the Night look (placeholder until it is ported from assets/scene/night.html). */
class NightScene : ThemeScene() {
    override val ready = false
    override fun draw(cv: Canvas, w: Float, h: Float, t: Float, dt: Float, page: Float) {}
}
