package com.twomemory.database

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AlbumMigrationTest {
    @Test fun versionTwoUpgradesAndRoomValidatesNewSchemaWhilePreservingEveryOldTable() = verifyUpgrade(2)
    @Test fun versionOneUsesBothMigrationsAndKeepsOldEntries() = verifyUpgrade(1)
    @Test fun versionThreeKeepsAlbumOriginalsWhileAddingCloudQueue() = verifyUpgrade(3)
    @Test fun versionFourPreservesTheSharedBookAndAddsPrivacyWithoutDroppingQueues() = verifyUpgrade(4)

    private fun verifyUpgrade(version: Int) = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "album-migration-$version.db"
        context.deleteDatabase(name)
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context).name(name)
                .callback(object : SupportSQLiteOpenHelper.Callback(version) {
                    override fun onCreate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                        db.execSQL("CREATE TABLE entries (id TEXT NOT NULL PRIMARY KEY, coupleId TEXT NOT NULL, authorId TEXT NOT NULL, mode TEXT NOT NULL, state TEXT NOT NULL, occurredAtEpochMillis INTEGER NOT NULL, occurredTimezone TEXT NOT NULL, title TEXT, currentRevision INTEGER NOT NULL, rowVersion INTEGER NOT NULL, deleted INTEGER NOT NULL)")
                        db.execSQL("CREATE TABLE entry_blocks (id TEXT NOT NULL PRIMARY KEY, entryId TEXT NOT NULL, type TEXT NOT NULL, orderKey INTEGER NOT NULL, authorId TEXT NOT NULL, payload TEXT NOT NULL, assetId TEXT, blockVersion INTEGER NOT NULL, deleted INTEGER NOT NULL)")
                        db.execSQL("CREATE TABLE outbox_operations (operationId TEXT NOT NULL PRIMARY KEY, coupleId TEXT NOT NULL, entityId TEXT NOT NULL, action TEXT NOT NULL, payload TEXT NOT NULL, baseVersion INTEGER NOT NULL, attemptCount INTEGER NOT NULL, nextAttemptAtEpochMillis INTEGER NOT NULL, state TEXT NOT NULL)")
                        db.execSQL("CREATE TABLE sync_cursors (coupleId TEXT NOT NULL PRIMARY KEY, nextSequence INTEGER NOT NULL)")
                        if (version >= 2) AppDatabase.MIGRATION_1_2.migrate(db)
                        if (version >= 3) AppDatabase.MIGRATION_2_3.migrate(db)
                        if (version >= 4) AppDatabase.MIGRATION_3_4.migrate(db)
                    }
                    override fun onUpgrade(db: androidx.sqlite.db.SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
                }).build(),
        )
        helper.writableDatabase.apply {
            execSQL("INSERT INTO entries VALUES ('e', 'c', 'a', 'PERSONAL', 'DRAFT', 42, 'Asia/Shanghai', '旧记录', 2, 3, 0)")
            execSQL("INSERT INTO entry_blocks VALUES ('b', 'e', 'TEXT', 0, 'a', '旧正文', NULL, 4, 0)")
            execSQL("INSERT INTO outbox_operations VALUES ('o', 'c', 'e', 'CREATE_ENTRY', '{}', 3, 2, 100, 'PENDING')")
            execSQL("INSERT INTO sync_cursors VALUES ('c', 7)")
            if (version >= 2) execSQL("INSERT INTO comments VALUES ('comment', 'e', 'a', '旧评论', NULL, 100, 0)")
            if (version >= 3) {
                execSQL("INSERT INTO album_categories VALUES ('cat','旅行',100)")
                execSQL("INSERT INTO independent_albums(id,name,categoryId,createdAtEpochMillis,coverPhotoId) VALUES ('album','旧相册','cat',100,'photo')")
                execSQL("INSERT INTO album_photos(id,albumId,localPath,mimeType,width,height,takenAtEpochMillis,importedAtEpochMillis) VALUES ('photo','album','/private/original.png','image/png',20,30,100,100)")
            }
        }
        helper.close()
        val database = Room.databaseBuilder(context, AppDatabase::class.java, name)
            .addMigrations(AppDatabase.MIGRATION_1_2, AppDatabase.MIGRATION_2_3, AppDatabase.MIGRATION_3_4, AppDatabase.MIGRATION_4_5).build()
        try {
            assertEquals("旧记录", database.entryDao().findEntry("e")?.title)
            assertEquals("SHARED", database.entryDao().findEntry("e")?.visibility)
            assertEquals("旧正文", database.entryDao().blocks("e").single().payload)
            assertEquals(2, database.outboxDao().pending(Long.MAX_VALUE, 10).single().attemptCount)
            database.openHelper.readableDatabase.query("SELECT nextSequence FROM sync_cursors").use {
                assertTrue(it.moveToFirst()); assertEquals(7, it.getInt(0))
            }
            database.openHelper.readableDatabase.query("SELECT COUNT(*) FROM comments").use {
                assertTrue(it.moveToFirst()); assertEquals(if (version >= 2) 1 else 0, it.getInt(0))
            }
            if (version < 3) assertTrue(database.albumDao().observeAlbums().first().isEmpty())
            else {
                val old = database.albumDao().observeAlbums().first().single()
                assertEquals("旧相册", old.album.name)
                assertEquals("photo", old.album.coverPhotoId)
                assertEquals("/private/original.png", old.photos.single().localPath)
                assertNull(old.album.coupleId)
                assertFalse(old.album.cloudKnown)
                assertFalse(old.photos.single().deleted)
            }
            AlbumRepository(database, context).createAlbum("迁移后的相册", "旅行")
            assertEquals(if (version >= 3) 2 else 1, database.albumDao().observeAlbums().first().size)
        } finally {
            database.close()
            context.deleteDatabase(name)
        }
    }
}
