package com.twomemory.database

import androidx.room.withTransaction
import com.twomemory.model.LocalEntryCommand
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import java.util.UUID

class LocalEntryWriter(private val database: AppDatabase) {

    suspend fun save(command: LocalEntryCommand): UUID = withContext(Dispatchers.IO) {
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
                    action = "CREATE_ENTRY",
                    payload = createOperationPayload(command),
                    baseVersion = 0,
                ),
            )
        }
        command.entryId
    }

    /**
     * The outbox payload must match the server's typed dispatcher contract
     * (CREATE_PERSONAL_ENTRY): the server executes it, not the client.
     */
    private fun createOperationPayload(command: LocalEntryCommand): String {
        val blocks = org.json.JSONArray()
        for (block in command.blocks) {
            val text = if (block.type == com.twomemory.model.BlockType.TEXT) {
                try {
                    org.json.JSONObject(block.payload).optString("text", block.payload)
                } catch (exception: org.json.JSONException) {
                    block.payload
                }
            } else {
                null
            }
            blocks.put(org.json.JSONObject().apply {
                put("blockId", block.id.toString())
                put("type", block.type.name)
                put("orderKey", block.orderKey)
                if (text != null) put("text", text) else put("payload", block.payload)
            })
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
}
