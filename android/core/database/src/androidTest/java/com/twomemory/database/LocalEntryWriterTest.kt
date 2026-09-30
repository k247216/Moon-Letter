package com.twomemory.database

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.twomemory.model.EntryMode
import com.twomemory.model.LocalEntryCommand
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class LocalEntryWriterTest {
    private lateinit var database: AppDatabase

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext<Context>(), AppDatabase::class.java,
        ).build()
    }

    @After
    fun tearDown() = database.close()

    @Test
    fun entryAndOutboxAreCommittedTogether() = runBlocking {
        val entryId = LocalEntryWriter(database).save(
            LocalEntryCommand(
                coupleId = UUID.randomUUID(), authorId = UUID.randomUUID(), mode = EntryMode.PERSONAL,
                occurredAt = Instant.parse("2026-09-30T12:18:00Z"), occurredTimezone = "Asia/Shanghai",
            ),
        )
        assertNotNull(database.entryDao().findEntry(entryId.toString()))
        assertNotNull(database.outboxDao().pending(Long.MAX_VALUE, 1).singleOrNull())
    }
}
