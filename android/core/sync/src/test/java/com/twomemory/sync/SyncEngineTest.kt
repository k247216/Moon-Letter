package com.twomemory.sync

import com.twomemory.model.PendingOperation
import com.twomemory.model.RemoteChange
import com.twomemory.model.SyncStore
import com.twomemory.network.ChangePage
import com.twomemory.network.CoupleDiaryApi
import com.twomemory.network.HttpUnauthorizedException
import com.twomemory.network.PushResult
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import java.io.IOException
import java.util.UUID

class SyncEngineTest {
    private val coupleId = UUID.fromString("00000000-0000-0000-0000-000000000010")

    @Test
    fun fifoSuccessRemovesOperationAndDuplicateIsAppliedOnce() = runTest {
        val first = UUID.fromString("00000000-0000-0000-0000-000000000001")
        val store = FakeStore(listOf(operation(first)))
        val api = FakeApi(PushResult.Status.DUPLICATE)
        val result = SyncEngine(api, store, coupleId).pushPending()
        assertEquals(1, result.applied)
        assertEquals(listOf(first), store.applied)
    }

    /**
     * A personal draft never enters the space feed, so the push response is
     * the only channel that returns the server-side row version. Dropping it
     * would strand every later operation on a stale base version.
     */
    @Test
    fun appliedPushForwardsTheServerEntrySnapshotToTheStore() = runTest {
        val operationId = UUID.fromString("00000000-0000-0000-0000-000000000005")
        val store = FakeStore(listOf(operation(operationId)))
        val api = FakeApi(PushResult.Status.APPLIED)
        api.pushBody = """{"id":"$operationId","state":"PUBLISHED"}"""
        SyncEngine(api, store, coupleId).pushPending()
        assertEquals(listOf(api.pushBody), store.appliedBodies)
    }

    @Test
    fun retryUsesExponentialBackoffAndConflictIsPreserved() = runTest {
        val operationId = UUID.randomUUID()
        val store = FakeStore(listOf(operation(operationId, attemptCount = 2)))
        val api = FakeApi(PushResult.Status.RETRYABLE_FAILURE)
        val result = SyncEngine(api, store, coupleId).pushPending()
        assertEquals(1, result.retried)
        assertNotNull(store.retryAt)
        api.apiStatus = PushResult.Status.CONFLICT
        val conflictResult = SyncEngine(api, store, coupleId).pushPending()
        assertEquals(1, conflictResult.conflicts)
        assertEquals(emptyList(), store.applied)
    }

    @Test
    fun timeoutReuseSameIdempotencyIdThenApply() = runTest {
        val operationId = UUID.fromString("00000000-0000-0000-0000-000000000002")
        val store = FakeStore(listOf(operation(operationId)))
        val api = FakeApi(PushResult.Status.APPLIED)
        api.throwOnPush = true
        SyncEngine(api, store, coupleId).pushPending()
        assertEquals(0, store.applied.size)
        api.throwOnPush = false
        val result = SyncEngine(api, store, coupleId).pushPending()
        assertEquals(1, result.applied)
        // Both attempts carried the SAME idempotency id.
        assertEquals(listOf(operationId, operationId), api.pushedIds)
        assertEquals(listOf(operationId), store.applied)
    }

    @Test
    fun pullLoopsUntilHasMoreIsFalse() = runTest {
        val store = FakeStore(emptyList())
        val api = FakeApi(PushResult.Status.APPLIED)
        api.pages = ArrayDeque(listOf(
            ChangePage(listOf(change(1)), 5, hasMore = true),
            ChangePage(listOf(change(6), change(7)), 9, hasMore = false),
        ))
        val result = SyncEngine(api, store, coupleId).pullAll()
        assertEquals(3, result.pulled)
        assertEquals(9L, result.nextSequence)
        assertEquals(3, store.appliedChanges.size)
        assertEquals(9L, store.currentCursor(coupleId))
    }

    @Test
    fun unauthorized401StopsWithRePairMessage() = runTest {
        val operationId = UUID.randomUUID()
        val store = FakeStore(listOf(operation(operationId)))
        val api = FakeApi(PushResult.Status.UNAUTHORIZED)
        val pushResult = SyncEngine(api, store, coupleId).pushPending()
        assertTrue(pushResult.needsRePair)
        assertTrue(store.applied.isEmpty())
        assertNull(store.retryAt)

        val pullResult = SyncEngine(api, store, coupleId).pullAll()
        assertTrue(pullResult.needsRePair)
        assertNotNull(pullResult.failed)
        assertTrue(pullResult.failed is HttpUnauthorizedException)
    }

    @Test
    fun pullFailureBeforeCommitKeepsCursorUnchanged() = runTest {
        val store = FakeStore(emptyList(), failApply = true)
        val page = ChangePage(listOf(RemoteChange(4, "ENTRY", UUID.randomUUID(), "DELETE", null)), 4, false)
        val result = SyncEngine(FakeApi(PushResult.Status.APPLIED, page), store, coupleId).pullAfter(3)
        assertEquals(3, result.nextSequence)
        assertNotNull(result.failed)
        assertFalse(result.needsRePair)
    }

    @Test
    fun firstRetryableFailureStopsStrictFifoBeforeSecondOperation() = runTest {
        val first = UUID.fromString("00000000-0000-0000-0000-000000000003")
        val second = UUID.fromString("00000000-0000-0000-0000-000000000004")
        val store = FakeStore(listOf(operation(first), operation(second)))
        val api = FakeApi(PushResult.Status.RETRYABLE_FAILURE)
        val result = SyncEngine(api, store, coupleId).pushPending()
        assertEquals(1, result.retried)
        // Strict FIFO: the second operation must not be attempted after the
        // first failed; ordering would otherwise break server-side sequence.
        assertEquals(listOf(first), api.pushedIds)
        assertNotNull(store.retryAt)
    }

    @Test
    fun cancellationPropagatesInsteadOfBeingSwallowedAsRetry() = runTest {
        val operationId = UUID.randomUUID()
        val store = FakeStore(listOf(operation(operationId)))
        val api = FakeApi(PushResult.Status.APPLIED)
        api.cancelOnPush = true
        val engine = SyncEngine(api, store, coupleId)
        val result = runCatching { engine.pushPending() }
        assertTrue(result.isFailure, "cancellation must propagate")
        assertTrue(result.exceptionOrNull() is kotlinx.coroutines.CancellationException)
        assertNull(store.retryAt)
    }

    @Test
    fun runSyncCycleReturnsRetryWhenPushWasRetryable() = runTest {
        val operationId = UUID.randomUUID()
        val store = FakeStore(listOf(operation(operationId)))
        val api = FakeApi(PushResult.Status.RETRYABLE_FAILURE)
        assertEquals(SyncOutcome.RETRY, runSyncCycle(SyncEngine(api, store, coupleId)))
    }

    @Test
    fun runSyncCycleReturnsFailureOnRePairAndSuccessOtherwise() = runTest {
        val store = FakeStore(emptyList())
        val unauthorized = FakeApi(PushResult.Status.UNAUTHORIZED)
        assertEquals(SyncOutcome.FAILURE, runSyncCycle(SyncEngine(unauthorized, store, coupleId)))
        val ok = FakeApi(PushResult.Status.APPLIED)
        assertEquals(SyncOutcome.SUCCESS, runSyncCycle(SyncEngine(ok, store, coupleId)))
    }

    private fun change(sequence: Long) =
        RemoteChange(sequence, "ENTRY", UUID.randomUUID(), "CREATE", "{}")

    private fun operation(id: UUID, attemptCount: Int = 0) = PendingOperation(
        id, coupleId, UUID.randomUUID(), "CREATE_ENTRY", "{}", 0, attemptCount,
    )

    private class FakeApi(
        var apiStatus: PushResult.Status,
        private val page: ChangePage = ChangePage(emptyList(), 0, false),
    ) : CoupleDiaryApi {
        var throwOnPush = false
        var cancelOnPush = false
        val pushedIds = mutableListOf<UUID>()
        var pages: ArrayDeque<ChangePage> = ArrayDeque()
        var pushBody: String? = null

        override suspend fun push(operation: PendingOperation): PushResult {
            pushedIds += operation.operationId
            if (cancelOnPush) throw kotlinx.coroutines.CancellationException("worker cancelled")
            if (throwOnPush) throw IOException("simulated timeout")
            return PushResult(operation.operationId, apiStatus, pushBody)
        }

        override suspend fun pull(coupleId: UUID, after: Long, limit: Int): ChangePage {
            if (apiStatus == PushResult.Status.UNAUTHORIZED) throw HttpUnauthorizedException()
            return pages.removeFirstOrNull() ?: page
        }
    }

    private class FakeStore(
        private val operations: List<PendingOperation>,
        private val failApply: Boolean = false,
    ) : SyncStore {
        val applied = mutableListOf<UUID>()
        val appliedBodies = mutableListOf<String?>()
        val appliedChanges = mutableListOf<RemoteChange>()
        var retryAt: Long? = null
        var cursor: Long = 0
        override suspend fun pendingOperations(limit: Int) = operations
        override suspend fun markApplied(operationId: UUID, serverEntrySnapshot: String?) {
            applied += operationId
            appliedBodies += serverEntrySnapshot
        }
        override suspend fun markRetry(operationId: UUID, attemptCount: Int, nextAttemptAtEpochMillis: Long) {
            retryAt = nextAttemptAtEpochMillis
        }
        override suspend fun markConflict(operationId: UUID) = Unit
        override suspend fun applyChangesAtomically(coupleId: UUID, changes: List<RemoteChange>, nextSequence: Long) {
            if (failApply) error("room failed")
            appliedChanges += changes
            cursor = nextSequence
        }
        override suspend fun currentCursor(coupleId: UUID) = cursor
    }
}
