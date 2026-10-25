package com.twomemory.app

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.twomemory.database.AppDatabase
import com.twomemory.database.LocalEntryWriter
import com.twomemory.database.EntryBlockEntity
import com.twomemory.database.SyncCursorEntity
import com.twomemory.model.*
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SessionDatabaseIsolationTest {
    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private val origin = "http://127.0.0.1:18088"

    @Test fun switchingMemberCannotReadPrivateRecordsOrPendingQueue() = runBlocking {
        val couple = UUID.randomUUID(); val author = UUID.randomUUID(); val partner = UUID.randomUUID()
        SyncSession.save(context, "author-test", couple, author, origin)
        val a = SyncSession.load(context)!!
        val db = AppDatabase.build(context, a.databaseName)
        val id = seedPrivate(db, couple, author)
        SyncSession.save(context, "partner-test", couple, partner, origin)
        val b = SyncSession.load(context)!!
        val partnerDb = AppDatabase.build(context, b.databaseName)
        assertNull(partnerDb.entryDao().findEntry(id.toString()))
        assertTrue(partnerDb.outboxDao().observeForEntity(id.toString()).first().isEmpty())
        SyncSession.save(context, "author-return", couple, author, origin)
        assertEquals(a.databaseName, SyncSession.load(context)!!.databaseName)
        assertEquals("PRIVATE", db.entryDao().findEntry(id.toString())!!.visibility)
        assertEquals(2, db.outboxDao().observeForEntity(id.toString()).first().size)
    }

    @Test fun legacyOwnerCanRecoverAfterDirectSwitchWithoutLoadingOldSession() = legacyRoundTrip(false)
    @Test fun legacyOwnerCanRecoverAfterStartupLoadsOldSession() = legacyRoundTrip(true)

    private fun legacyRoundTrip(loadFirst: Boolean) = runBlocking {
        val couple = UUID.randomUUID(); val author = UUID.randomUUID(); val partner = UUID.randomUUID()
        val legacy = "legacy-${UUID.randomUUID()}.db"
        SyncSession.sessionPrefs(context).edit().clear()
            .putString("token", "legacy-author-test").putString("baseUrl", origin)
            .putString("storageOrigin", origin).putString("coupleId", couple.toString())
            .putString("userId", author.toString()).putString("databaseName", legacy).commit()
        val db = AppDatabase.build(context, legacy)
        val id = seedPrivate(db, couple, author)
        val original = java.io.File(context.filesDir, "${UUID.randomUUID()}.mp4")
        val bytes = byteArrayOf(0, 1, 2, 3, 127, -1)
        original.writeBytes(bytes)
        val mediaPayload = org.json.JSONObject().put("localPath", original.absolutePath).put("mime", "video/mp4").toString()
        db.entryDao().insertBlocks(listOf(EntryBlockEntity(UUID.randomUUID().toString(), id.toString(),
            "VIDEO", 1, author.toString(), mediaPayload, UUID.randomUUID().toString())))
        db.syncCursorDao().upsert(SyncCursorEntity(couple.toString(), 73))
        if (loadFirst) assertEquals(legacy, SyncSession.load(context)!!.databaseName)
        SyncSession.save(context, "partner-test", couple, partner, origin)
        val b = SyncSession.load(context)!!
        assertNotEquals(legacy, b.databaseName)
        assertNull(AppDatabase.build(context, b.databaseName).entryDao().findEntry(id.toString()))
        assertNull(AppDatabase.build(context, b.databaseName).syncCursorDao().nextSequence(couple.toString()))
        SyncSession.save(context, "author-return", couple, author, origin)
        assertEquals(legacy, SyncSession.load(context)!!.databaseName)
        assertTrue(context.getDatabasePath(legacy).isFile)
        assertEquals("secret", db.entryDao().blocks(id.toString()).first().payload.let { org.json.JSONObject(it).getString("text") })
        assertEquals(mediaPayload, db.entryDao().blocks(id.toString()).last().payload)
        assertArrayEquals(bytes, original.readBytes())
        assertEquals(73L, db.syncCursorDao().nextSequence(couple.toString()))
        assertEquals(2, db.outboxDao().observeForEntity(id.toString()).first().size)
    }

    @Test fun freshUserScopeAlsoSeparatesSpacesOnDevelopmentOrigin() {
        val author = UUID.randomUUID()
        SyncSession.save(context, "first", UUID.randomUUID(), author, ServerAddress.DEV_DEFAULT_BASE_URL)
        val first = SyncSession.load(context)!!.databaseName
        SyncSession.save(context, "second", UUID.randomUUID(), author, ServerAddress.DEV_DEFAULT_BASE_URL)
        assertNotEquals(first, SyncSession.load(context)!!.databaseName)
    }

    @Test fun unreadableOldTokenDoesNotGiveItsDatabaseToAnotherMember() {
        val couple = UUID.randomUUID(); val author = UUID.randomUUID()
        SyncSession.save(context, "old", couple, author, origin)
        val before = SyncSession.load(context)!!
        SyncSession.sessionPrefs(context).edit().putString("token", "enc:AA==").commit()
        assertNull(SyncSession.load(context))
        SyncSession.save(context, "partner", couple, UUID.randomUUID(), origin)
        assertNotEquals(before.databaseName, SyncSession.load(context)!!.databaseName)
        SyncSession.save(context, "recovered", couple, author, origin)
        assertEquals(before.databaseName, SyncSession.load(context)!!.databaseName)
    }

    @Test fun previouslyOwnedLegacyFileCannotBeAdoptedByAnotherMember() {
        val couple = UUID.randomUUID(); val author = UUID.randomUUID()
        SyncSession.save(context, "author", couple, author, origin)
        val before = SyncSession.load(context)!!
        SyncSession.sessionPrefs(context).edit().putString("userId", UUID.randomUUID().toString()).commit()
        val other = SyncSession.load(context)!!
        assertNotEquals(before.databaseName, other.databaseName)
        SyncSession.save(context, "author-return", couple, author, origin)
        assertEquals(before.databaseName, SyncSession.load(context)!!.databaseName)
    }

    private suspend fun seedPrivate(db: AppDatabase, couple: UUID, author: UUID): UUID =
        LocalEntryWriter(db).save(LocalEntryCommand(couple, author, EntryMode.PERSONAL,
            Instant.parse("2026-10-10T00:00:00Z"), "Asia/Shanghai", "private",
            listOf(LocalBlockCommand(type = BlockType.TEXT, orderKey = 0,
                payload = "{\"text\":\"secret\"}", authorId = author)), visibility = EntryVisibility.PRIVATE), publish = true)
}
