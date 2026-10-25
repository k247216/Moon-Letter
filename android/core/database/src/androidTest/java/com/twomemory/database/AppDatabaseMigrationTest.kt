package com.twomemory.database

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppDatabaseMigrationTest {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java,
    )

    @Test
    fun migration1To2PreservesExistingRowsAndCreatesCommentsTable() {
        val databaseName = "migration-1-2-test"
        helper.createDatabase(databaseName, 1).use { database ->
            database.execSQL(
                """
                INSERT INTO entries(
                    id, coupleId, authorId, mode, state, occurredAtEpochMillis,
                    occurredTimezone, title, currentRevision, rowVersion, deleted
                ) VALUES ('entry-1', 'couple-1', 'author-1', 'PERSONAL', 'DRAFT',
                    1727698680000, 'Asia/Shanghai', '旧记录', 0, 0, 0)
                """.trimIndent(),
            )
            database.execSQL(
                """
                INSERT INTO entry_blocks(
                    id, entryId, type, orderKey, authorId, payload, assetId,
                    blockVersion, deleted
                ) VALUES ('block-1', 'entry-1', 'TEXT', 0, 'author-1',
                    '{"text":"迁移前的正文"}', NULL, 0, 0)
                """.trimIndent(),
            )
            database.execSQL(
                """
                INSERT INTO outbox_operations(
                    operationId, coupleId, entityId, action, payload, baseVersion,
                    attemptCount, nextAttemptAtEpochMillis, state
                ) VALUES ('operation-1', 'couple-1', 'entry-1', 'CREATE_ENTRY',
                    '{"entryId":"entry-1"}', 0, 0, 0, 'PENDING')
                """.trimIndent(),
            )
            database.execSQL(
                "INSERT INTO sync_cursors(coupleId, nextSequence) VALUES ('couple-1', 7)",
            )
        }

        helper.runMigrationsAndValidate(
            databaseName,
            2,
            true,
            AppDatabase.MIGRATION_1_2,
        ).use { database ->
            assertEquals(1, count(database, "entries"))
            assertEquals(1, count(database, "entry_blocks"))
            assertEquals(1, count(database, "outbox_operations"))
            assertEquals(1, count(database, "sync_cursors"))
            assertEquals(0, count(database, "comments"))
            assertTrue(tableExists(database, "comments"))

            database.query("SELECT title FROM entries WHERE id = ?", arrayOf("entry-1")).use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("旧记录", cursor.getString(0))
            }
            database.query("SELECT nextSequence FROM sync_cursors WHERE coupleId = ?", arrayOf("couple-1"))
                .use { cursor ->
                    assertTrue(cursor.moveToFirst())
                    assertEquals(7, cursor.getLong(0))
                }
        }
    }

    private fun count(database: androidx.sqlite.db.SupportSQLiteDatabase, table: String): Int =
        database.query("SELECT count(*) FROM $table", emptyArray()).use { cursor ->
            assertTrue(cursor.moveToFirst())
            cursor.getInt(0)
        }

    private fun tableExists(database: androidx.sqlite.db.SupportSQLiteDatabase, table: String): Boolean =
        database.query(
            "SELECT 1 FROM sqlite_master WHERE type = 'table' AND name = ?",
            arrayOf(table),
        ).use { cursor -> cursor.moveToFirst() }
}
