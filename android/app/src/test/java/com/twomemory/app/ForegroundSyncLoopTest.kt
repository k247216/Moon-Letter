package com.twomemory.app

import kotlinx.coroutines.launch
import kotlinx.coroutines.test.*
import org.junit.Assert.assertEquals
import org.junit.Test
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.withTimeout

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class ForegroundSyncLoopTest {
    @Test fun auxiliaryProcessingDoesNotInheritTheUiThread() = runBlocking {
        val uiThread = Thread.currentThread()
        val processing = CompletableDeferred<Thread>()
        val job = launch(start = CoroutineStart.UNDISPATCHED) {
            foregroundSyncLoop(sync = {}, refresh = {
                processing.complete(Thread.currentThread())
                awaitCancellation()
            })
        }
        try {
            val worker = withTimeout(5_000) { processing.await() }
            org.junit.Assert.assertNotSame("Avatar processing must not block the UI", uiThread, worker)
        } finally { job.cancelAndJoin() }
    }

    @Test fun slowAuxiliaryRefreshDoesNotDelayTextOrAccumulateRefreshJobs() = runTest {
        var pulls = 0
        var refreshes = 0
        var refreshCancelled = false
        val job = backgroundScope.launch {
            foregroundSyncLoop(refreshDispatcher = UnconfinedTestDispatcher(testScheduler), sync = { pulls++ }, refresh = {
                refreshes++
                try { awaitCancellation() } finally { refreshCancelled = true }
            })
        }
        runCurrent()
        assertEquals(1, pulls)
        assertEquals(1, refreshes)
        advanceTimeBy(9_000); runCurrent()
        assertEquals(4, pulls)
        assertEquals(1, refreshes)
        job.cancel(); runCurrent()
        assertEquals(true, refreshCancelled)
        advanceTimeBy(9_000); runCurrent()
        assertEquals(4, pulls)
    }

    @Test fun receivesWithoutInteractionRetriesFailureAndStopsWhenLeaving() = runTest {
        var calls = 0
        val job = backgroundScope.launch {
            foregroundSyncLoop { calls++; if (calls == 1) error("Temporary network loss") }
        }
        runCurrent()
        assertEquals(1, calls)
        advanceTimeBy(3_000); runCurrent()
        assertEquals(2, calls)
        advanceTimeBy(3_000); runCurrent()
        assertEquals(3, calls)
        job.cancel(); runCurrent()
        advanceTimeBy(9_000); runCurrent()
        assertEquals(3, calls)
    }
}
