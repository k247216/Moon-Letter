package com.twomemory.model

import java.util.UUID

/**
 * Contract between the local persistence layer (Room) and the sync engine.
 * Lives in :core:model so both :core:sync and :core:database can see it
 * without creating a module cycle.
 */
interface SyncStore {
    suspend fun pendingOperations(limit: Int): List<PendingOperation>
    suspend fun markApplied(operationId: UUID)
    suspend fun markRetry(operationId: UUID, attemptCount: Int, nextAttemptAtEpochMillis: Long)
    suspend fun markConflict(operationId: UUID)
    suspend fun applyChangesAtomically(coupleId: UUID, changes: List<RemoteChange>, nextSequence: Long)
}
