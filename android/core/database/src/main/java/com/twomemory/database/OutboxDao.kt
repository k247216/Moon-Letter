package com.twomemory.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface OutboxDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(operation: OutboxOperationEntity)

    @Query("SELECT * FROM outbox_operations WHERE state = 'PENDING' AND nextAttemptAtEpochMillis <= :now ORDER BY rowid LIMIT :limit")
    suspend fun pending(now: Long, limit: Int): List<OutboxOperationEntity>

    @Query("DELETE FROM outbox_operations WHERE operationId = :operationId")
    suspend fun delete(operationId: String)

    @Query("UPDATE outbox_operations SET state = :state, attemptCount = :attemptCount, nextAttemptAtEpochMillis = :nextAt WHERE operationId = :operationId")
    suspend fun markRetry(operationId: String, state: String, attemptCount: Int, nextAt: Long)

    @Query("SELECT * FROM outbox_operations WHERE operationId = :operationId LIMIT 1")
    suspend fun find(operationId: String): OutboxOperationEntity?

    /** Operations held back because an image of their entry has no asset yet. */
    @Query(
        "SELECT * FROM outbox_operations WHERE entityId = :entityId AND state = 'MEDIA_PENDING' ORDER BY rowid",
    )
    suspend fun mediaPendingForEntity(entityId: String): List<OutboxOperationEntity>

    /** Re-queues one held operation with its assets filled in; rowid order is kept. */
    @Query("UPDATE outbox_operations SET state = 'PENDING', payload = :payload WHERE operationId = :operationId")
    suspend fun release(operationId: String, payload: String)
}
