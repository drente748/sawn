package com.sawn.app.system

import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The watchdog's cadence.
 *
 * Two numbers decide how fast a dead protection is noticed: the ordinary check and the
 * fast one that runs while a time lock holds the user. They are encoded here so that
 * a change to either is a change to a promise — the user is told the protection is
 * watched every few minutes, and it must be.
 */
class GuardHealthWorkerTest {

    @Test
    fun theStrictCheckRunsEveryTwoToThreeMinutes() {
        assertTrue(
            "the strict check must run every 2-3 minutes, was ${GuardHealthWorker.STRICT_INTERVAL_MINUTES}",
            GuardHealthWorker.STRICT_INTERVAL_MINUTES in 2L..3L,
        )
    }

    @Test
    fun theOrdinaryCheckRunsEveryFifteenToThirtyMinutes() {
        assertTrue(
            "the ordinary check must run every 15-30 minutes, was ${GuardHealthWorker.PERIODIC_INTERVAL_MINUTES}",
            GuardHealthWorker.PERIODIC_INTERVAL_MINUTES in 15L..30L,
        )
    }
}
