package com.twomemory.sync

import com.twomemory.model.PendingOperation
import com.twomemory.model.RemoteChange
import com.twomemory.model.SyncStore
import com.twomemory.network.ChangePage
import com.twomemory.network.CoupleDiaryApi
import com.twomemory.network.PushResult
import java.io.IOException
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Assert.*
import org.junit.Test

class SyncPushCancellationTest {
    private val coupleId = UUID.randomUUID()
    private val clock = Clock.fixed(Instant.ofEpochMilli(1_700_000_000_000L), ZoneOffset.UTC)

    @Test fun cancelledAppliedResponseCannotMarkApplied() = runBlocking {
        verifyCancelledResponse(PushResult.Status.APPLIED)
    }

    @Test fun cancelledConflictResponseCannotMarkConflict() = runBlocking {
        verifyCancelledResponse(PushResult.Status.CONFLICT)
    }

    @Test fun cancelledRetryableResponseCannotScheduleRetry() = runBlocking {
        verifyCancelledResponse(PushResult.Status.RETRYABLE_FAILURE)
    }

    @Test fun activeResponsesStillWriteTheirRespectiveStoreStates() = runBlocking {
        for (status in listOf(PushResult.Status.APPLIED, PushResult.Status.CONFLICT, PushResult.Status.RETRYABLE_FAILURE)) {
            val operation = operation()
            val store = RecordingStore(listOf(operation))
            val engine = SyncEngine(api { PushResult(it.operationId, status) }, store, coupleId, clock)
            val result = engine.pushPending()
            when (status) {
                PushResult.Status.APPLIED -> {
                    assertEquals(1, result.applied)
                    assertEquals(listOf(operation.operationId), store.applied)
                    assertTrue(store.conflicts.isEmpty())
                    assertTrue(store.retries.isEmpty())
                }
                PushResult.Status.CONFLICT -> {
                    assertEquals(1, result.conflicts)
                    assertEquals(listOf(operation.operationId), store.conflicts)
                    assertTrue(store.applied.isEmpty())
                    assertTrue(store.retries.isEmpty())
                }
                PushResult.Status.RETRYABLE_FAILURE -> {
                    assertEquals(1, result.retried)
                    assertEquals(listOf(RetryWrite(operation.operationId, 1, 1_700_000_002_000L)), store.retries)
                    assertTrue(store.applied.isEmpty())
                    assertTrue(store.conflicts.isEmpty())
                }
                else -> error("unexpected fixture status")
            }
        }
    }

    @Test fun retryBackoffUsesFixedClockAndCapsDelayWhileAttemptsContinueIncreasing() = runBlocking {
        // Preserve the existing effective cap: exponent stops at 8, so the
        // delay tops out at 256 seconds (below the separate 300-second bound).
        val cases = listOf(
            Triple(0, 1, 1_700_000_002_000L),
            Triple(1, 2, 1_700_000_004_000L),
            Triple(2, 3, 1_700_000_008_000L),
            Triple(6, 7, 1_700_000_128_000L),
            Triple(7, 8, 1_700_000_256_000L),
            Triple(8, 9, 1_700_000_256_000L),
            Triple(20, 21, 1_700_000_256_000L),
        )
        for ((previousAttempt, expectedAttempt, expectedAt) in cases) {
            val operation = operation(previousAttempt)
            val store = RecordingStore(listOf(operation))
            val engine = SyncEngine(api { PushResult(it.operationId, PushResult.Status.RETRYABLE_FAILURE) }, store, coupleId, clock)
            assertEquals(1, engine.pushPending().retried)
            assertEquals("previous attempt $previousAttempt",
                listOf(RetryWrite(operation.operationId, expectedAttempt, expectedAt)), store.retries)
            assertTrue(store.applied.isEmpty())
            assertTrue(store.conflicts.isEmpty())
        }
    }

    @Test fun networkExceptionUsesSameFixedClockBackoff() = runBlocking {
        val operation = operation(attemptCount = 2)
        val store = RecordingStore(listOf(operation))
        val engine = SyncEngine(api { throw IOException("connection lost") }, store, coupleId, clock)
        assertEquals(1, engine.pushPending().retried)
        assertEquals(listOf(RetryWrite(operation.operationId, 3, 1_700_000_008_000L)), store.retries)
        assertTrue(store.applied.isEmpty())
        assertTrue(store.conflicts.isEmpty())
    }

    private suspend fun verifyCancelledResponse(status: PushResult.Status) {
        val operation = operation()
        val store = RecordingStore(listOf(operation))
        val engine = SyncEngine(api {
            // Model an API that cancels the session but returns without another
            // suspension point: the engine must check before any store write.
            currentCoroutineContext()[Job]!!.cancel(CancellationException("session changed"))
            PushResult(it.operationId, status)
        }, store, coupleId, clock)
        val job = Job()
        try {
            val failure = runCatching { withContext(job) { engine.pushPending() } }.exceptionOrNull()
            // Store methods deliberately do not cooperate with cancellation.
            // Outer withContext throwing alone cannot prove no write happened.
            assertTrue("$status must not markApplied after cancellation", store.applied.isEmpty())
            assertTrue("$status must not markConflict after cancellation", store.conflicts.isEmpty())
            assertTrue("$status must not markRetry after cancellation", store.retries.isEmpty())
            assertTrue("Cancellation must propagate", failure is CancellationException)
        } finally {
            job.cancel()
        }
    }

    private fun operation(attemptCount: Int = 0) = PendingOperation(
        UUID.randomUUID(), coupleId, UUID.randomUUID(), "CREATE_ENTRY", "{}", 0, attemptCount,
    )

    private fun api(push: suspend (PendingOperation) -> PushResult) = object : CoupleDiaryApi {
        override suspend fun push(operation: PendingOperation) = push.invoke(operation)
        override suspend fun pull(coupleId: UUID, after: Long, limit: Int): ChangePage = error("unexpected pull")
    }

    private data class RetryWrite(val operationId: UUID, val attempt: Int, val nextAt: Long)

    private class RecordingStore(private val operations: List<PendingOperation>) : SyncStore {
        val applied = mutableListOf<UUID>()
        val conflicts = mutableListOf<UUID>()
        val retries = mutableListOf<RetryWrite>()
        override suspend fun pendingOperations(limit: Int) = operations.take(limit)
        override suspend fun markApplied(operationId: UUID, serverEntrySnapshot: String?) { applied.add(operationId) }
        override suspend fun markConflict(operationId: UUID) { conflicts.add(operationId) }
        override suspend fun markRetry(operationId: UUID, attemptCount: Int, nextAttemptAtEpochMillis: Long) {
            retries.add(RetryWrite(operationId, attemptCount, nextAttemptAtEpochMillis))
        }
        override suspend fun currentCursor(coupleId: UUID): Long = error("unexpected cursor read")
        override suspend fun applyChangesAtomically(coupleId: UUID, changes: List<RemoteChange>, nextSequence: Long): Unit =
            error("unexpected change commit")
    }
}
