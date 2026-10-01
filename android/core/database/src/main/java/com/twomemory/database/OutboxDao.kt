package com.twomemory.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface OutboxDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(operation: OutboxOperationEntity)

    /**
     * What the engine may send next, in the order the space must see it.
     *
     * A rejected operation stays in this table, so without the guard the same
     * record's later operations would overtake the row that failed and be refused
     * for a reason that has nothing to do with them. One rejection therefore holds
     * back its own record and nothing else.
     */
    @Query(
        "SELECT * FROM outbox_operations AS o " +
            "WHERE o.state = 'PENDING' AND o.nextAttemptAtEpochMillis <= :now " +
            "AND NOT EXISTS (" +
            "SELECT 1 FROM outbox_operations AS c " +
            "WHERE c.entityId = o.entityId AND c.state = 'CONFLICT' AND c.rowid < o.rowid" +
            ") " +
            "ORDER BY o.rowid LIMIT :limit",
    )
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

    /**
     * Everything still owed for one record, in the order the server must see it.
     * Empty means the record is fully delivered: an operation only leaves the
     * queue once the server has accepted it.
     */
    @Query("SELECT * FROM outbox_operations WHERE entityId = :entityId ORDER BY rowid")
    fun observeForEntity(entityId: String): Flow<List<OutboxOperationEntity>>

    /** Records whose operation the server rejected; these are invisible unless queried. */
    @Query("SELECT DISTINCT entityId FROM outbox_operations WHERE state = 'CONFLICT'")
    fun observeRejectedEntityIds(): Flow<List<String>>

    /**
     * Puts a server-rejected operation back in line. The engine parks CONFLICT rows
     * at nextAttemptAtEpochMillis = Long.MAX_VALUE and never touches them again, so
     * this is the only way a rejected record moves — and the attempt counter resets
     * so an explicit retry does not start at the longest backoff.
     */
    @Query(
        "UPDATE outbox_operations SET state = 'PENDING', attemptCount = 0, " +
            "nextAttemptAtEpochMillis = :now " +
            "WHERE entityId = :entityId AND state = 'CONFLICT'",
    )
    suspend fun requeueRejected(entityId: String, now: Long): Int
}
