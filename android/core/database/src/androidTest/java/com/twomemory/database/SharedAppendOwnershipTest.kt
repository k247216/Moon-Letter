package com.twomemory.database

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.twomemory.model.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant
import java.util.UUID

class SharedAppendOwnershipTest {
    @Test fun replayingTheSameBlockCannotDuplicateItsQueuedOperation() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext, AppDatabase::class.java).build()
        try {
            val writer = LocalEntryWriter(db)
            val couple = UUID.randomUUID()
            val own = UUID.randomUUID()
            val entry = writer.save(LocalEntryCommand(couple, own, EntryMode.COLLABORATIVE, Instant.now(), "Asia/Shanghai"))
            val block = LocalBlockCommand(type = BlockType.TEXT, orderKey = 1, payload = "{\"text\":\"我的视角\"}", authorId = own)
            writer.appendBlock(couple, entry, block)
            writer.appendBlock(couple, entry, block)
            assertEquals(1, db.entryDao().blocks(entry.toString()).size)
            assertEquals(1, db.outboxDao().pending(Long.MAX_VALUE, 20).count { it.action == "APPEND_BLOCK" })
        } finally { db.close() }
    }

    @Test fun aCollidingIdCannotOverwriteTheOtherPersonsBlock() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext, AppDatabase::class.java).build()
        try {
            val writer = LocalEntryWriter(db)
            val couple = UUID.randomUUID()
            val own = UUID.randomUUID()
            val partner = UUID.randomUUID()
            val original = LocalBlockCommand(type = BlockType.TEXT, orderKey = 0, payload = "{\"text\":\"伴侣原文\"}", authorId = partner)
            val entry = writer.save(LocalEntryCommand(couple, partner, EntryMode.COLLABORATIVE, Instant.now(), "Asia/Shanghai", blocks = listOf(original)))
            val result = runCatching { writer.appendBlock(couple, entry, original.copy(authorId = own, payload = "{\"text\":\"替换\"}")) }
            assertTrue("Append must reject an existing block ID", result.isFailure)
            assertEquals(partner.toString(), db.entryDao().findBlock(original.id.toString())?.authorId)
            assertEquals("{\"text\":\"伴侣原文\"}", db.entryDao().findBlock(original.id.toString())?.payload)
        } finally { db.close() }
    }
}
