package com.twomemory.database

import androidx.room.withTransaction
import com.twomemory.model.BlockType
import com.twomemory.model.LocalBlockCommand
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
                        state = if (command.blocks.awaitsAsset()) MEDIA_PENDING else "PENDING",
                    ),
                )
                if (publish) {
                    // The server stores a new entry at row version 0 and the outbox
                    // applies operations in insertion order, so a publish enqueued
                    // here faces exactly that version. Both operations gate together:
                    // a publish must never overtake a create whose picture is missing.
                    database.outboxDao().insert(
                        publishOperation(command.coupleId, command.entryId, 0L, command.blocks.awaitsAsset()),
                    )
                }
            }
            command.entryId
        }

    private fun publishOperation(coupleId: UUID, entryId: UUID, baseVersion: Long, waitsForAsset: Boolean) =
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
            state = if (waitsForAsset) MEDIA_PENDING else "PENDING",
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
                    state = if (block.awaitsAsset()) MEDIA_PENDING else "PENDING",
                ),
            )
        }
    }

    /**
     * Records a finished upload and, when that was the entry's last missing
     * picture, releases the whole entry to the queue in one transaction.
     */
    suspend fun attachAsset(blockId: UUID, assetId: UUID): Unit = withContext(Dispatchers.IO) {
        database.withTransaction {
            val block = database.entryDao().findBlock(blockId.toString()) ?: return@withTransaction
            database.entryDao().insertBlocks(listOf(block.copy(assetId = assetId.toString())))
            releaseEntry(block.entryId)
        }
    }

    private suspend fun releaseEntry(entryId: String) {
        val gated = database.outboxDao().mediaPendingForEntity(entryId)
        if (gated.isEmpty() || database.entryDao().imagesWithoutAsset(entryId) > 0) return
        val assetsByBlock = database.entryDao().blocks(entryId)
            .mapNotNull { block -> block.assetId?.let { block.id to it } }
            .toMap()
        for (operation in gated) {
            database.outboxDao().release(operation.operationId, fillAssets(operation.payload, assetsByBlock))
        }
    }

    /** Patches the stored operation JSON in place, so its shape stays the server's contract. */
    private fun fillAssets(payload: String, assetsByBlock: Map<String, String>): String {
        val json = org.json.JSONObject(payload)
        json.optJSONArray("blocks")?.let { blocks ->
            for (index in 0 until blocks.length()) {
                assetsByBlock[blocks.getJSONObject(index).optString("blockId")]
                    ?.let { blocks.getJSONObject(index).put("assetId", it) }
            }
        }
        json.optJSONObject("block")?.let { block ->
            assetsByBlock[block.optString("blockId")]?.let { block.put("assetId", it) }
        }
        return json.toString()
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
            if (text != null) put("text", text) else put("payload", wirePayload(block))
            if (block.assetId != null) put("assetId", block.assetId.toString())
        }
    }

    /**
     * What leaves the device for a non-text block. A local file path names a
     * location inside this app's private storage: it means nothing on the other
     * phone, and the picture itself arrives through the asset id, so the path
     * would only ever travel further than the photo it points at.
     */
    private fun wirePayload(block: com.twomemory.model.LocalBlockCommand): String =
        if (block.type != com.twomemory.model.BlockType.IMAGE) {
            block.payload
        } else {
            org.json.JSONObject()
                .put("mime", mimeTypeOf(block.payload))
                .toString()
        }

    private fun mimeTypeOf(payload: String): String = try {
        org.json.JSONObject(payload).optString("mime", "image/jpeg").ifBlank { "image/jpeg" }
    } catch (notJson: org.json.JSONException) {
        // A non-JSON image payload is a bare path; it must not be echoed onward.
        "image/jpeg"
    }
}

/** Outbox rows in this state are invisible to the sync engine until their pictures land. */
private const val MEDIA_PENDING = "MEDIA_PENDING"

private fun LocalBlockCommand.awaitsAsset(): Boolean = type == BlockType.IMAGE && assetId == null

private fun List<LocalBlockCommand>.awaitsAsset(): Boolean = any { it.awaitsAsset() }
