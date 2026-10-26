package com.example.buddy.ui

import android.content.Context
import android.view.View
import android.view.ViewGroup

/** Lays children out left to right and wraps to the next line; [gap] dp between them. */
class FlowLayout(context: Context, private val gap: Int = 8) : ViewGroup(context) {
    private val g get() = (gap * resources.displayMetrics.density).toInt()

    override fun onMeasure(wSpec: Int, hSpec: Int) {
        val maxW = MeasureSpec.getSize(wSpec) - paddingLeft - paddingRight
        var x = 0; var y = 0; var lineH = 0
        for (i in 0 until childCount) {
            val v = getChildAt(i)
            if (v.visibility == View.GONE) continue
            v.measure(MeasureSpec.makeMeasureSpec(maxW, MeasureSpec.AT_MOST), MeasureSpec.UNSPECIFIED)
            if (x > 0 && x + v.measuredWidth > maxW) { x = 0; y += lineH + g; lineH = 0 }
            x += v.measuredWidth + g
            lineH = maxOf(lineH, v.measuredHeight)
        }
        setMeasuredDimension(MeasureSpec.getSize(wSpec), y + lineH + paddingTop + paddingBottom)
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        val maxW = r - l - paddingLeft - paddingRight
        var x = 0; var y = 0; var lineH = 0
        for (i in 0 until childCount) {
            val v = getChildAt(i)
            if (v.visibility == View.GONE) continue
            if (x > 0 && x + v.measuredWidth > maxW) { x = 0; y += lineH + g; lineH = 0 }
            v.layout(paddingLeft + x, paddingTop + y, paddingLeft + x + v.measuredWidth, paddingTop + y + v.measuredHeight)
            x += v.measuredWidth + g
            lineH = maxOf(lineH, v.measuredHeight)
        }
    }
}
