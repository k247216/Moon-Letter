package com.twomemory.model

import java.util.UUID

/**
 * Contract between the local persistence layer (Room) and the sync engine.
 * Lives in :core:model so both :core:sync and :core:database can see it
 * without creating a module cycle.
 */
interface SyncStore {
    suspend fun pendingOperations(limit: Int): List<PendingOperation>

    /**
     * Consumes an operation. When the server answered with the entry as the space
     * now holds it, that snapshot is merged in the same transaction: a personal
     * draft never enters the change feed, so the push response is the only channel
     * that can tell the author's device which version of its own record to publish
     * against.
     */
    suspend fun markApplied(operationId: UUID, serverEntrySnapshot: String? = null)
    suspend fun markRetry(operationId: UUID, attemptCount: Int, nextAttemptAtEpochMillis: Long)
    suspend fun markConflict(operationId: UUID)
    suspend fun applyChangesAtomically(coupleId: UUID, changes: List<RemoteChange>, nextSequence: Long)
    suspend fun currentCursor(coupleId: UUID): Long
}
