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

@Entity(tableName = "comments", primaryKeys = ["id"])
data class CommentEntity(
    val id: String,
    val entryId: String,
    val authorId: String,
    val body: String,
    val replyToId: String?,
    val createdAtEpochMillis: Long,
    val deleted: Boolean = false,
)

@Database(
    entities = [
        EntryEntity::class,
        EntryBlockEntity::class,
        OutboxOperationEntity::class,
        SyncCursorEntity::class,
        CommentEntity::class,
    ],
    version = 2,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun entryDao(): EntryDao
    abstract fun outboxDao(): OutboxDao
    abstract fun syncCursorDao(): SyncCursorDao
    abstract fun commentDao(): CommentDao

    companion object {
        private val MIGRATION_1_2 = object : androidx.room.migration.Migration(1, 2) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `comments` (" +
                        "`id` TEXT NOT NULL, `entryId` TEXT NOT NULL, `authorId` TEXT NOT NULL, " +
                        "`body` TEXT NOT NULL, `replyToId` TEXT, `createdAtEpochMillis` INTEGER NOT NULL, " +
                        "`deleted` INTEGER NOT NULL, PRIMARY KEY(`id`))",
                )
            }
        }

        /**
         * One handle per process: the editor, the sync engine and the uploader all
         * write the same file, and separate connections would contend for it.
         */
        @Volatile
        private var instance: AppDatabase? = null

        fun build(context: android.content.Context): AppDatabase = instance ?: synchronized(this) {
            instance ?: androidx.room.Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                "moon-letter.db",
            ).addMigrations(MIGRATION_1_2).build().also { instance = it }
        }
    }
}
