package com.twomemory.database

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.twomemory.model.EntryMode
import com.twomemory.model.LocalBlockCommand
import com.twomemory.model.LocalEntryCommand
import com.twomemory.model.RemoteChange
import com.twomemory.model.BlockType
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.time.Instant
import java.util.UUID

/**
 * Task 9 acceptance, run on the JVM with Robolectric against REAL Room
 * databases (per plan Task 8 decision). Two separate temporary database
 * files represent two devices.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RoomSyncStoreTest {

    private lateinit var context: Context
    private lateinit var fileA: File
    private lateinit var fileB: File
    private lateinit var databaseA: AppDatabase
    private lateinit var databaseB: AppDatabase

    private val coupleId = UUID.fromString("00000000-0000-0000-0000-00000000c0de")

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        fileA = File(context.cacheDir, "device-a-${UUID.randomUUID()}.db")
        fileB = File(context.cacheDir, "device-b-${UUID.randomUUID()}.db")
        databaseA = Room.databaseBuilder(context, AppDatabase::class.java, fileA.absolutePath).build()
        databaseB = Room.databaseBuilder(context, AppDatabase::class.java, fileB.absolutePath).build()
    }

    @After
    fun tearDown() {
        databaseA.close()
        databaseB.close()
        fileA.deleteRecursively()
        fileB.deleteRecursively()
    }

    private fun command(entryId: UUID = UUID.randomUUID()) = LocalEntryCommand(
        coupleId = coupleId,
        authorId = UUID.randomUUID(),
        mode = EntryMode.PERSONAL,
        occurredAt = Instant.parse("2026-09-30T12:18:00Z"),
        occurredTimezone = "Asia/Shanghai",
        title = "傍晚散步",
        blocks = listOf(
            LocalBlockCommand(
                type = BlockType.TEXT,
                orderKey = 0,
                payload = "{\"text\":\"暴雨天窝在家\"}",
                authorId = UUID.randomUUID(),
            ),
        ),
        entryId = entryId,
    )

    @Test
    fun entryAndOutboxCommitAtomically() = runBlocking {
        val entryId = LocalEntryWriter(databaseA).save(command())
        assertNotNull(databaseA.entryDao().findEntry(entryId.toString()))
        assertEquals(1, databaseA.outboxDao().pending(Long.MAX_VALUE, 10).size)
    }

    @Test
    fun pendingOperationsSurviveKillAndReopen() = runBlocking {
        val entryId = LocalEntryWriter(databaseA).save(command())
        databaseA.close()
        databaseA = Room.databaseBuilder(context, AppDatabase::class.java, fileA.absolutePath).build()

        val pending = databaseA.outboxDao().pending(Long.MAX_VALUE, 10)
        assertEquals(1, pending.size)
        assertEquals(entryId.toString(), pending.single().entityId)
        assertNotNull(databaseA.entryDao().findEntry(entryId.toString()))
    }

    @Test
    fun applyingPageAndCursorIsOneTransaction() = runBlocking {
        val store = RoomSyncStore(databaseB)
        val goodPayload = entryPayload("00000000-0000-0000-0000-000000000101")
        val changes = listOf(
            RemoteChange(1, "ENTRY", UUID.fromString("00000000-0000-0000-0000-000000000101"), "CREATE", goodPayload),
            RemoteChange(2, "ENTRY", UUID.fromString("00000000-0000-0000-0000-000000000102"), "CREATE", "{not-json"),
        )
        try {
            store.applyChangesAtomically(coupleId, changes, nextSequence = 2)
            throw AssertionError("expected the malformed change to fail the transaction")
        } catch (expected: Exception) {
            // any failure must roll the whole page back
        }
        assertNull(databaseB.entryDao().findEntry("00000000-0000-0000-0000-000000000101"))
        assertNull(databaseB.syncCursorDao().nextSequence(coupleId.toString()))
    }

    @Test
    fun duplicatePulledChangesAreHarmless() = runBlocking {
        val store = RoomSyncStore(databaseB)
        val entryId = "00000000-0000-0000-0000-000000000201"
        val payload = entryPayload(entryId)
        val change = RemoteChange(7, "ENTRY", UUID.fromString(entryId), "CREATE", payload)

        store.applyChangesAtomically(coupleId, listOf(change), nextSequence = 7)
        store.applyChangesAtomically(coupleId, listOf(change), nextSequence = 7)

        val entry = databaseB.entryDao().findEntry(entryId)
        assertNotNull(entry)
        assertEquals(1, databaseB.entryDao().blocks(entryId).size)
        assertEquals(7L, databaseB.syncCursorDao().nextSequence(coupleId.toString()))
    }

    /**
     * A personal draft never reaches the change feed, so the push response is
     * the only way the author's own device learns the server-side version.
     */
    @Test
    fun pushSnapshotMergeAdoptsTheServerVersionAndConsumesTheOperation() = runBlocking {
        val entryId = UUID.randomUUID()
        LocalEntryWriter(databaseA).save(command(entryId), publish = true)
        val store = RoomSyncStore(databaseA)

        val pending = store.pendingOperations(10)
        assertEquals(listOf("CREATE_ENTRY", "PUBLISH_ENTRY"), pending.map { it.action })

        store.markApplied(pending[0].operationId, entryPayload(entryId.toString()))

        assertEquals(1, store.pendingOperations(10).size)
        val entry = databaseA.entryDao().findEntry(entryId.toString())
        assertNotNull(entry)
        assertEquals("PUBLISHED", entry!!.state)
        assertEquals(1L, entry.rowVersion)
    }

    @Test
    fun nonEntryPushBodyStillConsumesTheAcceptedOperation() = runBlocking {
        val entryId = LocalEntryWriter(databaseA).save(command())
        val store = RoomSyncStore(databaseA)
        val operation = store.pendingOperations(10).single()

        store.markApplied(
            operation.operationId,
            """{"id":"00000000-0000-0000-0000-0000000000c1","body":"今天的风很软"}""",
        )

        assertEquals(0, store.pendingOperations(10).size)
        assertEquals("DRAFT", databaseA.entryDao().findEntry(entryId.toString())!!.state)
    }

    @Test
    fun cursorAdvancesOnlyWithAppliedPage() = runBlocking {
        val store = RoomSyncStore(databaseB)
        store.applyChangesAtomically(coupleId, emptyList(), nextSequence = 5)
        assertEquals(5L, databaseB.syncCursorDao().nextSequence(coupleId.toString()))

        val entryId = "00000000-0000-0000-0000-000000000301"
        store.applyChangesAtomically(
            coupleId,
            listOf(RemoteChange(6, "ENTRY", UUID.fromString(entryId), "CREATE", entryPayload(entryId))),
            nextSequence = 6,
        )
        assertNotNull(databaseB.entryDao().findEntry(entryId))
        assertEquals(6L, databaseB.syncCursorDao().nextSequence(coupleId.toString()))
        assertTrue(fileB.length() > 0)
    }

    /** Mirrors the server EntryView JSON shape the change feed delivers. */
    private fun entryPayload(entryId: String): String {
        val block = """{"id":"00000000-0000-0000-0000-0000000000b1","type":"TEXT","orderKey":0,
            "updatedBy":"00000000-0000-0000-0000-0000000000b2","blockVersion":1,
            "payload":"{\"text\":\"对话\"}","assetId":null,"deleted":false}"""
        return """{"id":"$entryId","coupleId":"$coupleId","mode":"PERSONAL","state":"PUBLISHED",
            "authorId":"00000000-0000-0000-0000-0000000000b2","rowVersion":1,"currentRevisionNo":1,
            "occurredAtEpochMillis":1791137880000,"occurredTimezone":"Asia/Shanghai","title":"傍晚散步",
            "blocks":[$block]}"""
    }
}
