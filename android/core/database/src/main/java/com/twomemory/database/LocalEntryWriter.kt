package com.twomemory.database

import androidx.room.withTransaction
import com.twomemory.model.LocalEntryCommand
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import java.util.UUID

class LocalEntryWriter(private val database: AppDatabase) {

    suspend fun save(command: LocalEntryCommand, publish: Boolean = false): UUID =
        withContext(Dispatchers.IO) {
            require(command.occurredTimezone.isNotBlank()) { "occurrence timezone is required" }
            command.blocks.forEach { block ->
                require(block.type != com.twomemory.model.BlockType.TEXT ||
                        block.payload.codePointCount(0, block.payload.length) <= 20_000) {
                    "text block exceeds 20000 Unicode code points"
                }
            }
            database.withTransaction {
                database.entryDao().insertEntry(
                    EntryEntity(
                        id = command.entryId.toString(),
                        coupleId = command.coupleId.toString(),
                        authorId = command.authorId.toString(),
                        mode = command.mode.name,
                        state = "DRAFT",
                        occurredAtEpochMillis = command.occurredAt.toEpochMilli(),
                        occurredTimezone = command.occurredTimezone,
                        title = command.title,
                    ),
                )
                database.entryDao().insertBlocks(command.blocks.map { block ->
                    EntryBlockEntity(
                        id = block.id.toString(),
                        entryId = command.entryId.toString(),
                        type = block.type.name,
                        orderKey = block.orderKey,
                        authorId = block.authorId.toString(),
                        payload = block.payload,
                        assetId = block.assetId?.toString(),
                    )
                })
                database.outboxDao().insert(
                    OutboxOperationEntity(
                        operationId = UUID.randomUUID().toString(),
                        coupleId = command.coupleId.toString(),
                        entityId = command.entryId.toString(),
                        action = if (command.mode == com.twomemory.model.EntryMode.COLLABORATIVE) {
                            "CREATE_SHARED_ENTRY"
                        } else {
                            "CREATE_ENTRY"
                        },
                        payload = createOperationPayload(command),
                        baseVersion = 0,
                    ),
                )
                if (publish) {
                    // The server stores a new entry at row version 0 and the outbox
                    // applies operations in insertion order, so a publish enqueued
                    // here faces exactly that version.
                    database.outboxDao().insert(publishOperation(command.coupleId, command.entryId, 0L))
                }
            }
            command.entryId
        }

    private fun publishOperation(coupleId: UUID, entryId: UUID, baseVersion: Long) =
        OutboxOperationEntity(
            operationId = UUID.randomUUID().toString(),
            coupleId = coupleId.toString(),
            entityId = entryId.toString(),
            action = "PUBLISH_ENTRY",
            payload = org.json.JSONObject().apply {
                put("entryId", entryId.toString())
                put("baseVersion", baseVersion)
            }.toString(),
            baseVersion = baseVersion,
        )

    /**
     * Appends the caller's own perspective block to an existing shared entry:
     * Room insert + outbox operation commit together. Server contract:
     * APPEND_BLOCK with {entryId, block{blockId,type,orderKey,text,payload,assetId}}.
     */
    suspend fun appendBlock(
        coupleId: UUID,
        entryId: UUID,
        block: com.twomemory.model.LocalBlockCommand,
    ): Unit = withContext(Dispatchers.IO) {
        database.withTransaction {
            database.entryDao().insertBlocks(listOf(
                EntryBlockEntity(
                    id = block.id.toString(),
                    entryId = entryId.toString(),
                    type = block.type.name,
                    orderKey = block.orderKey,
                    authorId = block.authorId.toString(),
                    payload = block.payload,
                    assetId = block.assetId?.toString(),
                ),
            ))
            val payloadJson = org.json.JSONObject().apply {
                put("entryId", entryId.toString())
                put("block", blockJson(block))
            }.toString()
            database.outboxDao().insert(
                OutboxOperationEntity(
                    operationId = UUID.randomUUID().toString(),
                    coupleId = coupleId.toString(),
                    entityId = entryId.toString(),
                    action = "APPEND_BLOCK",
                    payload = payloadJson,
                    baseVersion = 0,
                ),
            )
        }
    }

    /**
     * Adds a comment with a client-generated id (server adopts it, so retries
     * stay idempotent): Room insert + outbox operation commit together.
     */
    suspend fun addComment(
        coupleId: UUID,
        entryId: UUID,
        commentId: UUID,
        authorId: UUID,
        body: String,
        replyToId: UUID? = null,
    ): Unit = withContext(Dispatchers.IO) {
        database.withTransaction {
            database.commentDao().insert(
                CommentEntity(
                    id = commentId.toString(),
                    entryId = entryId.toString(),
                    authorId = authorId.toString(),
                    body = body.trim(),
                    replyToId = replyToId?.toString(),
                    createdAtEpochMillis = System.currentTimeMillis(),
                ),
            )
            val payloadJson = org.json.JSONObject().apply {
                put("entryId", entryId.toString())
                put("commentId", commentId.toString())
                put("body", body.trim())
                put("replyToId", replyToId?.toString() ?: org.json.JSONObject.NULL)
            }.toString()
            database.outboxDao().insert(
                OutboxOperationEntity(
                    operationId = UUID.randomUUID().toString(),
                    coupleId = coupleId.toString(),
                    entityId = entryId.toString(),
                    action = "ADD_COMMENT",
                    payload = payloadJson,
                    baseVersion = 0,
                ),
            )
        }
    }

    /**
     * The outbox payload must match the server's typed dispatcher contract
     * (CREATE_PERSONAL_ENTRY / CREATE_SHARED_ENTRY): the server executes it.
     */
    private fun createOperationPayload(command: LocalEntryCommand): String {
        val blocks = org.json.JSONArray()
        for (block in command.blocks) {
            blocks.put(blockJson(block))
        }
        return org.json.JSONObject().apply {
            put("entryId", command.entryId.toString())
            put("authorId", command.authorId.toString())
            put("title", command.title ?: org.json.JSONObject.NULL)
            put("occurredAt", command.occurredAt.toString())
            put("occurredTimezone", command.occurredTimezone)
            put("blocks", blocks)
        }.toString()
    }

    private fun blockJson(block: com.twomemory.model.LocalBlockCommand): org.json.JSONObject {
        val text = if (block.type == com.twomemory.model.BlockType.TEXT) {
            try {
                org.json.JSONObject(block.payload).optString("text", block.payload)
            } catch (exception: org.json.JSONException) {
                block.payload
            }
        } else {
            null
        }
        return org.json.JSONObject().apply {
            put("blockId", block.id.toString())
            put("type", block.type.name)
            put("orderKey", block.orderKey)
            if (text != null) put("text", text) else put("payload", block.payload)
            if (block.assetId != null) put("assetId", block.assetId.toString())
        }
    }
}
