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
                    payload = command.entryId.toString(),
                    baseVersion = 0,
                ),
            )
        }
        command.entryId
    }
}
