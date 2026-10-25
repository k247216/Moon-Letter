package com.twomemory.app

import android.content.Context
import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.WorkManager
import com.twomemory.database.AppDatabase
import com.twomemory.database.LocalEntryWriter
import com.twomemory.editor.EditorUiState
import com.twomemory.editor.EditorPhoto
import com.twomemory.model.*
import com.twomemory.sync.SyncEngineRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant
import java.util.UUID

class SharedPerspectiveIntegrationTest {
    @Test fun supplementKeepsOriginalEntryAndBothAuthorsAndRollsBackACollision() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val prefs = SyncSession.sessionPrefs(context)
        val backup = prefs.all
        val oldCompletedHook = SyncEngineRegistry.onCycleCompleted
        val oldFactory = runCatching { SyncEngineRegistry.factory }.getOrNull()
        val couple = UUID.randomUUID()
        val own = UUID.randomUUID()
        val partner = UUID.randomUUID()
        val entry = UUID.randomUUID()
        val original = LocalBlockCommand(type = BlockType.TEXT, orderKey = 0,
            payload = "{\"text\":\"伴侣的原文\"}", authorId = partner)
        val db = AppDatabase.build(context)
        try {
            // Closed loopback port: exercise local persistence without an external service.
            SyncSession.save(context, "instrumentation-local-only", couple, own, "http://127.0.0.1:1/")
            LocalEntryWriter(db).save(LocalEntryCommand(couple, partner, EntryMode.COLLABORATIVE,
                Instant.now(), "Asia/Shanghai", entryId = entry, title = "原来的标题", blocks = listOf(original)))
            db.entryDao().insertEntry(db.entryDao().findEntry(entry.toString())!!.copy(state = "PUBLISHED"))
            val returned = SyncSession.appendPerspective(context, entry, EditorUiState(body = "我的补写"))
            assertEquals(entry, returned)
            assertEquals("原来的标题", db.entryDao().findEntry(entry.toString())!!.title)
            val blocks = db.entryDao().blocks(entry.toString())
            assertEquals(2, blocks.size)
            assertEquals(partner.toString(), blocks[0].authorId)
            assertEquals("{\"text\":\"伴侣的原文\"}", blocks[0].payload)
            assertEquals(own.toString(), blocks[1].authorId)
            assertEquals(1L, blocks[1].orderKey)
            val failed = runCatching { SyncSession.appendPerspective(context, entry,
                EditorUiState(body = "这一段也必须回滚", photos = listOf(EditorPhoto(original.id, "/unavailable.png", "image/png")))) }
            assertTrue(failed.isFailure)
            assertEquals(blocks, db.entryDao().blocks(entry.toString()))
            db.openHelper.readableDatabase.query("SELECT COUNT(*) FROM entries WHERE coupleId = ?", arrayOf(couple.toString())).use {
                assertTrue(it.moveToFirst()); assertEquals(1, it.getInt(0))
            }
        } finally {
            WorkManager.getInstance(context).cancelUniqueWork("moon-letter-sync-$couple").result.get()
            // Remove only this test's UUID-scoped fixtures, never the user's database.
            db.openHelper.writableDatabase.execSQL("DELETE FROM outbox_operations WHERE entityId = ?", arrayOf(entry.toString()))
            db.openHelper.writableDatabase.execSQL("DELETE FROM entry_blocks WHERE entryId = ?", arrayOf(entry.toString()))
            db.openHelper.writableDatabase.execSQL("DELETE FROM entries WHERE id = ?", arrayOf(entry.toString()))
            prefs.edit().clear().apply {
                backup.forEach { (key, value) -> when (value) {
                    is String -> putString(key, value)
                    is Boolean -> putBoolean(key, value)
                    is Long -> putLong(key, value)
                    is Int -> putInt(key, value)
                } }
            }.commit()
            if (oldFactory != null) SyncEngineRegistry.factory = oldFactory
            SyncEngineRegistry.onCycleCompleted = oldCompletedHook
        }
    }
}
