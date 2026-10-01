package com.twomemory.sync

import com.twomemory.model.PendingOperation
import com.twomemory.model.RemoteChange
import com.twomemory.model.SyncStore
import com.twomemory.network.ChangePage
import com.twomemory.network.CoupleDiaryApi
import com.twomemory.network.HttpUnauthorizedException
import com.twomemory.network.PushResult
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.time.Clock
import java.time.Duration
import java.util.UUID

data class SyncResult(
    val applied: Int = 0,
    val retried: Int = 0,
    val conflicts: Int = 0,
    val pulled: Int = 0,
    val nextSequence: Long? = null,
    val failed: Throwable? = null,
    val needsRePair: Boolean = false,
)

class SyncEngine(
    private val api: CoupleDiaryApi,
    private val store: SyncStore,
    private val coupleId: UUID,
    private val clock: Clock = Clock.systemUTC(),
) {
    suspend fun pushPending(): SyncResult {
        var applied = 0
        var retried = 0
        var conflicts = 0
        for (operation in store.pendingOperations(limit = 50)) {
            currentCoroutineContext().ensureActive()
            try {
                when (api.push(operation).status) {
                    PushResult.Status.APPLIED, PushResult.Status.DUPLICATE -> {
                        store.markApplied(operation.operationId)
                        applied++
                    }
                    PushResult.Status.CONFLICT -> {
                        store.markConflict(operation.operationId)
                        conflicts++
                    }
                    PushResult.Status.UNAUTHORIZED -> {
                        // Session is gone: stop pushing, surface re-pair to UI.
                        return SyncResult(needsRePair = true)
                    }
                    PushResult.Status.RETRYABLE_FAILURE -> {
                        scheduleRetry(operation)
                        retried++
                    }
                }
            } catch (failure: Throwable) {
                scheduleRetry(operation)
                retried++
            }
        }
        return SyncResult(applied = applied, retried = retried, conflicts = conflicts)
    }

    suspend fun pullAfter(cursor: Long): SyncResult {
        return try {
            val page = api.pull(coupleId, cursor, 200)
            store.applyChangesAtomically(coupleId, page.changes, page.nextSequence)
            SyncResult(pulled = page.changes.size, nextSequence = page.nextSequence)
        } catch (failure: Throwable) {
            SyncResult(failed = failure, nextSequence = cursor, needsRePair = failure is HttpUnauthorizedException)
        }
    }

    /** Pulls page after page until the server reports has_more=false. */
    suspend fun pullAll(): SyncResult {
        var cursor = try {
            store.currentCursor(coupleId)
        } catch (failure: Throwable) {
            return SyncResult(failed = failure)
        }
        var pulled = 0
        var hasMore = true
        while (hasMore) {
            try {
                val page = api.pull(coupleId, cursor, 200)
                store.applyChangesAtomically(coupleId, page.changes, page.nextSequence)
                pulled += page.changes.size
                cursor = page.nextSequence
                hasMore = page.hasMore
            } catch (failure: Throwable) {
                return SyncResult(
                    pulled = pulled,
                    nextSequence = cursor,
                    failed = failure,
                    needsRePair = failure is HttpUnauthorizedException,
                )
            }
        }
        return SyncResult(pulled = pulled, nextSequence = cursor)
    }

    private suspend fun scheduleRetry(operation: PendingOperation) {
        val attempt = operation.attemptCount + 1
        val delaySeconds = minOf(300L, 1L shl minOf(attempt, 8))
        val nextAt = clock.millis() + Duration.ofSeconds(delaySeconds).toMillis()
        store.markRetry(operation.operationId, attempt, nextAt)
    }
}
