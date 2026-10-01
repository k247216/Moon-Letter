package com.twomemory.database

import androidx.room.Database
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.RoomDatabase

@Entity(tableName = "entries")
data class EntryEntity(
    @PrimaryKey val id: String,
    val coupleId: String,
    val authorId: String,
    val mode: String,
    val state: String,
    val occurredAtEpochMillis: Long,
    val occurredTimezone: String,
    val title: String?,
    val currentRevision: Int = 0,
    val rowVersion: Long = 0,
    val deleted: Boolean = false,
)

@Entity(tableName = "entry_blocks", primaryKeys = ["id"])
data class EntryBlockEntity(
    val id: String,
    val entryId: String,
    val type: String,
    val orderKey: Long,
    val authorId: String,
    val payload: String,
    val assetId: String?,
    val blockVersion: Long = 0,
    val deleted: Boolean = false,
)

@Entity(tableName = "outbox_operations")
data class OutboxOperationEntity(
    @PrimaryKey val operationId: String,
    val coupleId: String,
    val entityId: String,
    val action: String,
    val payload: String,
    val baseVersion: Long,
    val attemptCount: Int = 0,
    val nextAttemptAtEpochMillis: Long = 0,
    val state: String = "PENDING",
)

@Entity(tableName = "sync_cursors")
data class SyncCursorEntity(
    @PrimaryKey val coupleId: String,
    val nextSequence: Long = 0,
)

@Database(
    entities = [EntryEntity::class, EntryBlockEntity::class, OutboxOperationEntity::class, SyncCursorEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun entryDao(): EntryDao
    abstract fun outboxDao(): OutboxDao
    abstract fun syncCursorDao(): SyncCursorDao
}
