package com.example.buddy.ui

object FrameGate {
    const val FRAME_NANOS = 33_333_333L
    const val LITE_FRAME_NANOS = 50_000_000L

    fun slot(frameTimeNanos: Long): Long = frameTimeNanos / FRAME_NANOS
}
