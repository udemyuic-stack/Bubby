package com.example.buddy.ui.vocab

sealed class RetryStep {
    class Retry(val tries: Int, val at: Long) : RetryStep()
    class GiveUp(val tries: Int) : RetryStep()
    object Done : RetryStep()
    object Unchanged : RetryStep()
}

object RetryPolicy {
    const val MAX_UNAVAILABLE = 8
    const val MAX_FAILED = 3
    private const val MIN = 60_000L
    val delays = longArrayOf(1 * MIN, 5 * MIN, 30 * MIN, 120 * MIN, 360 * MIN, 720 * MIN)

    fun cap(status: EntryStatus): Int = if (status == EntryStatus.FAILED) MAX_FAILED else MAX_UNAVAILABLE

    fun gaveUp(status: EntryStatus, tries: Int): Boolean =
        (status == EntryStatus.SAVED || status == EntryStatus.FAILED) && tries >= cap(status)

    fun next(outcome: EnrichOutcome, tries: Int, now: Long): RetryStep = when (outcome) {
        EnrichOutcome.DONE, EnrichOutcome.NEEDS_LANGUAGE -> RetryStep.Done
        EnrichOutcome.SKIPPED -> RetryStep.Unchanged
        EnrichOutcome.UNAVAILABLE -> step(tries + 1, MAX_UNAVAILABLE, now)
        EnrichOutcome.FAILED -> step(tries + 1, MAX_FAILED, now)
    }

    private fun step(tries: Int, max: Int, now: Long): RetryStep =
        if (tries >= max) RetryStep.GiveUp(tries) else RetryStep.Retry(tries, now + delays[minOf(tries, delays.size) - 1])
}
