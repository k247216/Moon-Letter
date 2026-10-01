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

    /** Refreshes a pending payload (e.g. after the image got its asset id). */
    @Query(
        "UPDATE outbox_operations SET payload = :payload " +
            "WHERE entityId = :entityId AND action IN ('CREATE_SHARED_ENTRY', 'APPEND_BLOCK') AND state = 'PENDING'",
    )
    suspend fun replacePayloadForEntity(entityId: String, payload: String)
}
