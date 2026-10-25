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
    @androidx.room.ColumnInfo(defaultValue = "'SHARED'") val visibility: String = "SHARED",
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
        AlbumCategoryEntity::class,
        AlbumEntity::class,
        AlbumPhotoEntity::class,
    ],
    version = 5,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun entryDao(): EntryDao
    abstract fun outboxDao(): OutboxDao
    abstract fun syncCursorDao(): SyncCursorDao
    abstract fun commentDao(): CommentDao
    abstract fun albumDao(): AlbumDao

    companion object {
        internal val MIGRATION_4_5 = object : androidx.room.migration.Migration(4, 5) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE entries ADD COLUMN visibility TEXT NOT NULL DEFAULT 'SHARED'")
            }
        }
        internal val MIGRATION_1_2: androidx.room.migration.Migration =
            object : androidx.room.migration.Migration(1, 2) {
                override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                    db.execSQL(
                        "CREATE TABLE IF NOT EXISTS `comments` (" +
                            "`id` TEXT NOT NULL, `entryId` TEXT NOT NULL, `authorId` TEXT NOT NULL, " +
                            "`body` TEXT NOT NULL, `replyToId` TEXT, `createdAtEpochMillis` INTEGER NOT NULL, " +
                            "`deleted` INTEGER NOT NULL, PRIMARY KEY(`id`))",
                    )
                }
            }

        internal val MIGRATION_2_3: androidx.room.migration.Migration =
            object : androidx.room.migration.Migration(2, 3) {
                override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                    db.execSQL("CREATE TABLE IF NOT EXISTS `album_categories` (`id` TEXT NOT NULL, `name` TEXT COLLATE NOCASE NOT NULL, `createdAtEpochMillis` INTEGER NOT NULL, PRIMARY KEY(`id`))")
                    db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_album_categories_name` ON `album_categories` (`name`)")
                    db.execSQL("CREATE TABLE IF NOT EXISTS `independent_albums` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, `categoryId` TEXT NOT NULL, `createdAtEpochMillis` INTEGER NOT NULL, `coverPhotoId` TEXT, PRIMARY KEY(`id`), FOREIGN KEY(`categoryId`) REFERENCES `album_categories`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT)")
                    db.execSQL("CREATE INDEX IF NOT EXISTS `index_independent_albums_categoryId` ON `independent_albums` (`categoryId`)")
                    db.execSQL("CREATE TABLE IF NOT EXISTS `album_photos` (`id` TEXT NOT NULL, `albumId` TEXT NOT NULL, `localPath` TEXT NOT NULL, `mimeType` TEXT NOT NULL, `width` INTEGER NOT NULL, `height` INTEGER NOT NULL, `takenAtEpochMillis` INTEGER NOT NULL, `importedAtEpochMillis` INTEGER NOT NULL, PRIMARY KEY(`id`), FOREIGN KEY(`albumId`) REFERENCES `independent_albums`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)")
                    db.execSQL("CREATE INDEX IF NOT EXISTS `index_album_photos_albumId` ON `album_photos` (`albumId`)")
                    db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_album_photos_localPath` ON `album_photos` (`localPath`)")
                }
            }

        internal val MIGRATION_3_4: androidx.room.migration.Migration =
            object : androidx.room.migration.Migration(3, 4) {
                override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                    for (column in listOf("serverUrl", "coupleId", "createdBy"))
                        db.execSQL("ALTER TABLE independent_albums ADD COLUMN `$column` TEXT")
                    for (column in listOf("cloudKnown", "deleted", "coverDirty"))
                        db.execSQL("ALTER TABLE independent_albums ADD COLUMN `$column` INTEGER NOT NULL DEFAULT 0")
                    for (column in listOf("assetId", "addedBy"))
                        db.execSQL("ALTER TABLE album_photos ADD COLUMN `$column` TEXT")
                    for (column in listOf("cloudKnown", "deleted"))
                        db.execSQL("ALTER TABLE album_photos ADD COLUMN `$column` INTEGER NOT NULL DEFAULT 0")
                }
            }

        /**
         * One handle per process: the editor, the sync engine and the uploader all
         * write the same file, and separate connections would contend for it.
         */
        private val instances = mutableMapOf<String, AppDatabase>()

        fun build(context: android.content.Context, name: String = context.getSharedPreferences(
            "moon_letter_session", android.content.Context.MODE_PRIVATE).getString("databaseName", "moon-letter.db")
            ?: "moon-letter.db"): AppDatabase = synchronized(this) {
            require(name.matches(Regex("[a-zA-Z0-9_-]+\\.db"))) { "Invalid database name" }
            instances[name] ?: androidx.room.Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                name,
            ).addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5).build().also { instances[name] = it }
        }
    }
}
