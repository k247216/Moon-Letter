package com.twomemory.database

import androidx.room.withTransaction
import com.twomemory.model.PendingOperation
import com.twomemory.model.RemoteChange
import com.twomemory.model.SyncStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.time.Instant
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

    override suspend fun markApplied(operationId: UUID, serverEntrySnapshot: String?) =
        withContext(Dispatchers.IO) {
            database.withTransaction {
                database.outboxDao().delete(operationId.toString())
                if (serverEntrySnapshot != null) mergeEntrySnapshot(serverEntrySnapshot)
            }
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
                if (change.payload.isNullOrBlank()) {
                    continue
                }
                when (change.entityType) {
                    "ENTRY" -> applyEntryChange(change)
                    "COMMENT" -> applyCommentChange(change)
                    // Falling through here would advance the cursor past a row this
                    // build cannot read, and that change would be gone for good.
                    else -> throw JSONException("nothing reads ${change.entityType} changes yet")
                }
            }
            database.syncCursorDao().upsert(
                SyncCursorEntity(coupleId = coupleId.toString(), nextSequence = nextSequence),
            )
        }
    }

    override suspend fun currentCursor(coupleId: UUID): Long = withContext(Dispatchers.IO) {
        database.syncCursorDao().nextSequence(coupleId.toString()) ?: 0L
    }

    /** A feed change is part of a page: malformed JSON fails the page, cursor included. */
    private suspend fun applyEntryChange(change: RemoteChange) =
        upsertEntry(JSONObject(change.payload), change.entityId.toString())

    /**
     * A push response carries the entry as the space now holds it. For a personal
     * record this is the only channel that reports the author's own version back,
     * because private drafts never enter the change feed. Bodies that are not an
     * entry projection (a comment view, an empty response) are ignored: a failed
     * merge must never invalidate an operation the server already accepted.
     */
    private suspend fun mergeEntrySnapshot(payload: String) {
        val snapshot = parseObjectOrNull(payload) ?: return
        if (!snapshot.has("id") || !snapshot.has("coupleId") || !snapshot.has("blocks")) return
        upsertEntry(snapshot, snapshot.optString("id"))
    }

    private fun parseObjectOrNull(raw: String): JSONObject? = try {
        JSONObject(raw)
    } catch (expected: JSONException) {
        null
    }

    /** Upsert semantics make duplicate deliveries harmless. */
    private suspend fun upsertEntry(payload: JSONObject, fallbackEntryId: String) {
        val entryId = payload.optString("id", fallbackEntryId)
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

    /**
     * Comment payloads come from the server's CommentView JSON. The change's
     * entityId is the *entry*, so the comment's own id and moment have to come
     * from the payload: falling back to the entry id would fold every comment
     * under one record onto the same row.
     */
    private suspend fun applyCommentChange(change: RemoteChange) {
        val payload = JSONObject(change.payload)
        val createdAt = when (val raw = payload.opt("createdAt")) {
            is Number -> (raw.toDouble() * 1000).toLong()
            is String -> Instant.parse(raw).toEpochMilli()
            else -> throw JSONException("comment change carries no createdAt")
        }
        database.commentDao().insert(
            CommentEntity(
                id = payload.getString("id"),
                entryId = payload.optString("entryId", change.entityId.toString()),
                authorId = payload.optString("authorId"),
                body = payload.optString("body"),
                replyToId = if (payload.isNull("replyToId")) null else payload.optString("replyToId"),
                createdAtEpochMillis = createdAt,
            ),
        )
    }
}
