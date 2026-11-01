package com.example.buddy.ui.home

class TapCounter(private val needed: Int = 3, private val windowMs: Long = 1100) {
    private val times = ArrayDeque<Long>()

    fun tap(nowMs: Long): Boolean {
        while (times.isNotEmpty() && nowMs - times.first() >= windowMs) times.removeFirst()
        times.addLast(nowMs)
        if (times.size < needed) return false
        times.clear()
        return true
    }
}
