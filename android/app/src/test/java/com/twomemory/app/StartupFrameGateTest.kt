package com.twomemory.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StartupFrameGateTest {
    @Test fun firstFrameWaitsForInitialLocalContent() {
        val gate = StartupFrameGate(startedAtMillis = 10_000L)
        assertTrue(gate.shouldKeepOnScreen(10_000L))
        assertTrue(gate.shouldKeepOnScreen(10_016L))
    }

    @Test fun localContentAndDecorReadyReleaseBeforeDeadlineWithoutNetwork() {
        val gate = StartupFrameGate(startedAtMillis = 10_000L)
        assertTrue(gate.shouldKeepOnScreen(10_100L))
        gate.onInitialContentReady()
        assertFalse(gate.shouldKeepOnScreen(10_101L))
        assertFalse(gate.shouldKeepOnScreen(10_200L))
    }

    @Test fun missingLocalLoadOrDecorCallbackCannotHoldPastOneSecond() {
        val gate = StartupFrameGate(startedAtMillis = 10_000L)
        assertTrue(gate.shouldKeepOnScreen(10_999L))
        assertFalse(gate.shouldKeepOnScreen(11_000L))
        assertFalse(gate.shouldKeepOnScreen(11_001L))
    }

    @Test fun repeatedPreDrawChecksDoNotRestartTheDeadline() {
        val gate = StartupFrameGate(startedAtMillis = 10_000L)
        for (offset in listOf(0L, 16L, 100L, 500L, 999L)) {
            assertTrue(gate.shouldKeepOnScreen(10_000L + offset))
        }
        assertFalse(gate.shouldKeepOnScreen(11_000L))
    }

    @Test fun bypassReleasesImmediatelyAndIsIdempotent() {
        // Setup, secure-storage errors, and direct entry/editor share this release.
        val gate = StartupFrameGate(startedAtMillis = 10_000L)
        gate.release()
        assertFalse(gate.shouldKeepOnScreen(10_000L))
        gate.release()
        gate.onInitialContentReady()
        assertFalse(gate.shouldKeepOnScreen(10_001L))
    }

    @Test fun timeoutReleaseIsLatchedEvenIfSubsequentTimestampIsOlder() {
        val gate = StartupFrameGate(startedAtMillis = 10_000L)
        assertFalse(gate.shouldKeepOnScreen(11_000L))
        assertFalse(gate.shouldKeepOnScreen(10_999L))
        gate.onInitialContentReady()
        assertFalse(gate.shouldKeepOnScreen(10_000L))
    }

    @Test fun aNewActivityGetsAFreshGateWhileOldGateStaysReleased() {
        val previous = StartupFrameGate(startedAtMillis = 10_000L)
        previous.onInitialContentReady()
        assertFalse(previous.shouldKeepOnScreen(10_100L))
        val recreated = StartupFrameGate(startedAtMillis = 10_100L)
        assertTrue(recreated.shouldKeepOnScreen(10_100L))
        assertFalse(previous.shouldKeepOnScreen(10_100L))
    }

    @Test fun zeroBudgetCannotDelayAnyFrame() {
        val gate = StartupFrameGate(startedAtMillis = 10_000L, maxWaitMillis = 0L)
        assertFalse(gate.shouldKeepOnScreen(10_000L))
    }
}
