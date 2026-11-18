package com.example.buddy

import com.example.buddy.ui.vocab.*

fun retryPolicyTests() {
    val now = 1_000_000L
    val min = 60_000L
    val expected = listOf(1L, 5L, 30L, 120L, 360L, 720L, 720L)
    var tries = 0
    val got = ArrayList<Long>()
    while (true) {
        when (val s = RetryPolicy.next(EnrichOutcome.UNAVAILABLE, tries, now)) {
            is RetryStep.Retry -> { got.add((s.at - now) / min); tries = s.tries }
            is RetryStep.GiveUp -> { tries = s.tries; break }
            else -> break
        }
    }
    check("retry unavailable delays", got == expected, got.toString())
    check("retry unavailable gives up at 8", tries == 8)

    val f1 = RetryPolicy.next(EnrichOutcome.FAILED, 0, now)
    val f2 = RetryPolicy.next(EnrichOutcome.FAILED, 1, now)
    val f3 = RetryPolicy.next(EnrichOutcome.FAILED, 2, now)
    check("retry failed backoff", f1 is RetryStep.Retry && f1.at == now + min && f2 is RetryStep.Retry && f2.at == now + 5 * min)
    check("retry failed gives up at 3", f3 is RetryStep.GiveUp && f3.tries == 3)

    check("retry done resets", RetryPolicy.next(EnrichOutcome.DONE, 5, now) === RetryStep.Done)
    check("retry needs language resets", RetryPolicy.next(EnrichOutcome.NEEDS_LANGUAGE, 2, now) === RetryStep.Done)
    check("retry skipped unchanged", RetryPolicy.next(EnrichOutcome.SKIPPED, 4, now) === RetryStep.Unchanged)

    check("retry gave up by status", RetryPolicy.gaveUp(EntryStatus.FAILED, 3) && !RetryPolicy.gaveUp(EntryStatus.SAVED, 3) && RetryPolicy.gaveUp(EntryStatus.SAVED, 8) && !RetryPolicy.gaveUp(EntryStatus.NEEDS_LANGUAGE, 9))
}
