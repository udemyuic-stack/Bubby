package com.example.buddy

import android.os.SystemClock

/** Helps the agent loop wait until the screen has settled after an action. */
object StepRunner {
    /**
     * Blocks (worker thread only) until no window event arrived for [quietMs], at most [maxMs].
     * Returns immediately when the accessibility service is not connected.
     */
    fun awaitIdle(maxMs: Long = 2500, quietMs: Long = 600) {
        if (!BuddyAccess.enabled) return
        val start = SystemClock.uptimeMillis()
        try {
            while (true) {
                val now = SystemClock.uptimeMillis()
                if (now - start >= maxMs) return
                val last = BuddyAccess.lastEventMs
                val quiet = now - last >= quietMs
                // an app that is still starting has not produced events yet: give it a short grace period
                val seen = last >= start || now - start >= quietMs + 500
                if (quiet && seen) return
                Thread.sleep(50)
            }
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
        }
    }
}
