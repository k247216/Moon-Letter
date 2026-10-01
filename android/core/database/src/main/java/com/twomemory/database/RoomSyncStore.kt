package com.twomemory.database

import androidx.room.withTransaction
import com.twomemory.model.PendingOperation
import com.twomemory.model.RemoteChange
import com.twomemory.model.SyncStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/**
 * SyncStore backed by Room: the outbox drives pushes, pulled changes plus the
 * per-couple cursor are applied in one Room transaction, so a crash mid-apply
 * leaves both the data and the cursor untouched.
 */
class RoomSyncStore(private val database: AppDatabase) : SyncStore {

    override suspend fun pendingOperations(limit: Int): List<PendingOperation> =
        withContext(Dispatchers.IO) {
            database.outboxDao().pending(System.currentTimeMillis(), limit).map { entity ->
                PendingOperation(
                    operationId = UUID.fromString(entity.operationId),
                    coupleId = UUID.fromString(entity.coupleId),
                    entityId = UUID.fromString(entity.entityId),
                    action = entity.action,
                    payload = entity.payload,
                    baseVersion = entity.baseVersion,
                    attemptCount = entity.attemptCount,
                )
            }
        }

    override suspend fun markApplied(operationId: UUID) = withContext(Dispatchers.IO) {
        database.outboxDao().delete(operationId.toString())
    }

    override suspend fun markRetry(operationId: UUID, attemptCount: Int, nextAttemptAtEpochMillis: Long) =
        withContext(Dispatchers.IO) {
            database.outboxDao().markRetry(operationId.toString(), "PENDING", attemptCount, nextAttemptAtEpochMillis)
        }

    override suspend fun markConflict(operationId: UUID) = withContext(Dispatchers.IO) {
        database.outboxDao().markRetry(operationId.toString(), "CONFLICT", 0, Long.MAX_VALUE)
    }

    override suspend fun applyChangesAtomically(
        coupleId: UUID,
        changes: List<RemoteChange>,
        nextSequence: Long,
    ) = withContext(Dispatchers.IO) {
        database.withTransaction {
            for (change in changes) {
                if (change.entityType != "ENTRY" || change.payload.isNullOrBlank()) {
                    continue
                }
                applyEntryChange(change)
            }
            database.syncCursorDao().upsert(
                SyncCursorEntity(coupleId = coupleId.toString(), nextSequence = nextSequence),
            )
        }
    }

    /** Upsert semantics make duplicate deliveries harmless. */
    private suspend fun applyEntryChange(change: RemoteChange) {
        val payload = JSONObject(change.payload)
        val entryId = payload.optString("id", change.entityId.toString())
        val blocks = payload.optJSONArray("blocks") ?: JSONArray()
        database.entryDao().insertEntry(
            EntryEntity(
                id = entryId,
                coupleId = payload.optString("coupleId"),
                authorId = payload.optString("authorId"),
                mode = payload.optString("mode", "PERSONAL"),
                state = mapState(payload.optString("state", "DRAFT")),
                occurredAtEpochMillis = payload.optLong("occurredAtEpochMillis", 0L),
                occurredTimezone = payload.optString("occurredTimezone", "UTC"),
                title = if (payload.isNull("title")) null else payload.optString("title"),
                currentRevision = payload.optInt("currentRevisionNo", 0),
                rowVersion = payload.optLong("rowVersion", 0L),
            ),
        )
        database.entryDao().insertBlocks((0 until blocks.length()).map { index ->
            val block = blocks.getJSONObject(index)
            EntryBlockEntity(
                id = block.getString("id"),
                entryId = entryId,
                type = block.getString("type"),
                orderKey = block.optLong("orderKey", index.toLong()),
                authorId = block.optString("updatedBy", payload.optString("authorId")),
                payload = block.optString("payload", "{}"),
                assetId = if (block.isNull("assetId")) null else block.optString("assetId"),
                blockVersion = block.optLong("blockVersion", 0L),
                deleted = block.optBoolean("deleted", false),
            )
        })
    }

    private fun mapState(raw: String): String = when (raw) {
        "PUBLISHED", "CAPSULE_LOCKED", "ARCHIVED" -> raw
        else -> "DRAFT"
    }
}
