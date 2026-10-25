package com.twomemory.sync

import com.twomemory.model.PendingOperation
import com.twomemory.model.RemoteChange
import com.twomemory.model.SyncStore
import com.twomemory.network.ChangePage
import com.twomemory.network.CoupleDiaryApi
import com.twomemory.network.PushResult
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Assert.*
import org.junit.Test

class SyncPullCancellationTest {
    private val coupleId = UUID.randomUUID()
    private val page = ChangePage(listOf(RemoteChange(8, "ENTRY", UUID.randomUUID(), "CREATE", "{}")), 8, false)

    @Test fun cancellationFromPullPropagatesForBothPullMethods() = runBlocking {
        for (all in listOf(false, true)) {
            val cancellation = CancellationException("session changed")
            val store = RecordingStore()
            val engine = SyncEngine(api { throw cancellation }, store, coupleId)
            val failure = runCatching { if (all) engine.pullAll() else engine.pullAfter(7) }.exceptionOrNull()
            assertSame("Cancellation must escape the engine", cancellation, failure)
            assertEquals(0, store.commits)
            assertEquals(7L, store.cursor)
        }
    }

    @Test fun cancellationFromCursorReadPropagates() = runBlocking {
        val cancellation = CancellationException("session changed")
        val store = RecordingStore(cursorFailure = cancellation)
        val failure = runCatching { SyncEngine(api { page }, store, coupleId).pullAll() }.exceptionOrNull()
        assertSame(cancellation, failure)
        assertEquals(0, store.commits)
    }

    @Test fun cancellationFromAtomicApplyPropagatesForBothPullMethods() = runBlocking {
        for (all in listOf(false, true)) {
            val cancellation = CancellationException("transaction cancelled")
            val store = RecordingStore(applyFailure = cancellation)
            val engine = SyncEngine(api { page }, store, coupleId)
            val failure = runCatching { if (all) engine.pullAll() else engine.pullAfter(7) }.exceptionOrNull()
            assertSame(cancellation, failure)
            assertEquals(7L, store.cursor)
        }
    }

    @Test fun cancelledSessionCannotCommitPageReturnedByNonCooperativeApi() = runBlocking {
        for (all in listOf(false, true)) {
            val store = RecordingStore()
            val engine = SyncEngine(api {
                currentCoroutineContext()[Job]!!.cancel(CancellationException("session changed"))
                page
            }, store, coupleId)
            val job = Job()
            try {
                runCatching { withContext(job) { if (all) engine.pullAll() else engine.pullAfter(7) } }
                assertEquals("Check cancellation before committing a returned page", 0, store.commits)
                assertEquals(7L, store.cursor)
            } finally {
                job.cancel()
            }
        }
    }

    private fun api(pull: suspend () -> ChangePage) = object : CoupleDiaryApi {
        override suspend fun push(operation: PendingOperation) = PushResult(operation.operationId, PushResult.Status.APPLIED)
        override suspend fun pull(coupleId: UUID, after: Long, limit: Int) = pull()
    }

    private class RecordingStore(
        private val cursorFailure: Throwable? = null,
        private val applyFailure: Throwable? = null,
    ) : SyncStore {
        var cursor = 7L
        var commits = 0
        override suspend fun currentCursor(coupleId: UUID): Long {
            cursorFailure?.let { throw it }
            return cursor
        }
        override suspend fun applyChangesAtomically(coupleId: UUID, changes: List<RemoteChange>, nextSequence: Long) {
            applyFailure?.let { throw it }
            commits++
            cursor = nextSequence
        }
        override suspend fun pendingOperations(limit: Int) = emptyList<PendingOperation>()
        override suspend fun markApplied(operationId: UUID, serverEntrySnapshot: String?) = Unit
        override suspend fun markConflict(operationId: UUID) = Unit
        override suspend fun markRetry(operationId: UUID, attemptCount: Int, nextAttemptAtEpochMillis: Long) = Unit
    }
}
