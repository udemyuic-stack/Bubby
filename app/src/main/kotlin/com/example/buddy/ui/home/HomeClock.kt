package com.example.buddy.ui.home

object HomeClock {
    private const val NS_PER_MS = 1_000_000L
    private const val NS_PER_SECOND = 1_000_000_000f
    private val originNs = System.nanoTime()

    fun ms(): Long = System.nanoTime() / NS_PER_MS

    fun seconds(): Float = (System.nanoTime() - originNs) / NS_PER_SECOND
}
