package com.example.buddy.ui.home

import android.graphics.Path

object PathData {
    private val TOKEN = Regex("[MLHVCQZ]|-?\\d*\\.?\\d+")

    fun parse(d: String): Path {
        val path = Path()
        val tokens = TOKEN.findAll(d).map { it.value }.toList()
        var i = 0
        var cmd = 'M'
        var x = 0f
        var y = 0f
        fun num() = tokens[i++].toFloat()
        while (i < tokens.size) {
            if (tokens[i][0].isLetter()) {
                cmd = tokens[i][0]
                i++
                if (cmd == 'Z') { path.close(); continue }
            }
            when (cmd) {
                'M' -> { x = num(); y = num(); path.moveTo(x, y); cmd = 'L' }
                'L' -> { x = num(); y = num(); path.lineTo(x, y) }
                'H' -> { x = num(); path.lineTo(x, y) }
                'V' -> { y = num(); path.lineTo(x, y) }
                'C' -> {
                    val x1 = num(); val y1 = num(); val x2 = num(); val y2 = num()
                    x = num(); y = num()
                    path.cubicTo(x1, y1, x2, y2, x, y)
                }
                'Q' -> {
                    val x1 = num(); val y1 = num()
                    x = num(); y = num()
                    path.quadTo(x1, y1, x, y)
                }
                else -> i = tokens.size
            }
        }
        return path
    }
}
