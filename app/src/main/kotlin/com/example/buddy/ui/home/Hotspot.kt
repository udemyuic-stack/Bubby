package com.example.buddy.ui.home

import android.graphics.RectF

class Hotspot(val area: RectF, val onTap: () -> Unit) {
    fun hit(x: Float, y: Float, scale: Float) =
        x >= area.left * scale && x <= area.right * scale && y >= area.top * scale && y <= area.bottom * scale
}
