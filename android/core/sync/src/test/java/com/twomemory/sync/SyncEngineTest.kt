package com.twomemory.sync

import com.twomemory.model.PendingOperation
import com.twomemory.model.RemoteChange
import com.twomemory.network.ChangePage
import com.twomemory.network.CoupleDiaryApi
import com.twomemory.network.PushResult
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
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
    fun pullDoesNotAdvanceCursorWhenRoomApplyFails() = runTest {
        val store = FakeStore(emptyList(), failApply = true)
        val page = ChangePage(listOf(RemoteChange(4, "ENTRY", UUID.randomUUID(), "DELETE", null)), 4, false)
        val result = SyncEngine(FakeApi(PushResult.Status.APPLIED, page), store, coupleId).pullAfter(3)
        assertEquals(3, result.nextSequence)
        assertNotNull(result.failed)
    }

    private fun operation(id: UUID, attemptCount: Int = 0) = PendingOperation(
        id, coupleId, UUID.randomUUID(), "CREATE_ENTRY", "{}", 0, attemptCount,
    )

    private class FakeApi(
        var apiStatus: PushResult.Status,
        private val page: ChangePage = ChangePage(emptyList(), 0, false),
    ) : CoupleDiaryApi {
        override suspend fun push(operation: PendingOperation) = PushResult(operation.operationId, apiStatus)
        override suspend fun pull(coupleId: UUID, after: Long, limit: Int) = page
    }

    private class FakeStore(
        private val operations: List<PendingOperation>,
        private val failApply: Boolean = false,
    ) : SyncStore {
        val applied = mutableListOf<UUID>()
        var retryAt: Long? = null
        override suspend fun pendingOperations(limit: Int) = operations
        override suspend fun markApplied(operationId: UUID) { applied += operationId }
        override suspend fun markRetry(operationId: UUID, attemptCount: Int, nextAttemptAtEpochMillis: Long) {
            retryAt = nextAttemptAtEpochMillis
        }
        override suspend fun markConflict(operationId: UUID) = Unit
        override suspend fun applyChangesAtomically(coupleId: UUID, changes: List<RemoteChange>, nextSequence: Long) {
            if (failApply) error("room failed")
        }
    }
}
