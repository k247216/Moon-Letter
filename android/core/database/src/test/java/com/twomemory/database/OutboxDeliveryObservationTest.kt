package com.twomemory.database

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.twomemory.model.BlockType
import com.twomemory.model.EntryMode
import com.twomemory.model.EntrySyncPhase
import com.twomemory.model.LocalBlockCommand
import com.twomemory.model.LocalEntryCommand
import com.twomemory.model.QueuedOperation
import com.twomemory.model.entrySyncPhase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
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
 * What the screens promise about a record comes from these three reads, so they are
 * tested against real Room rather than a fake: the queue is the only place a phone
 * can learn that the server refused to take something, and a refused operation is
 * parked where the sync engine will never look again.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class OutboxDeliveryObservationTest {

    private lateinit var context: Context
    private lateinit var dbFile: File
    private lateinit var database: AppDatabase
    private lateinit var writer: LocalEntryWriter
    private lateinit var outbox: OutboxDao
    private lateinit var store: RoomSyncStore

    private val coupleId = UUID.fromString("00000000-0000-0000-0000-00000000c0de")
    private val authorId = UUID.fromString("00000000-0000-0000-0000-0000000a11ce")

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        dbFile = File(context.cacheDir, "delivery-${UUID.randomUUID()}.db")
        database = Room.databaseBuilder(context, AppDatabase::class.java, dbFile.absolutePath).build()
        writer = LocalEntryWriter(database)
        outbox = database.outboxDao()
        store = RoomSyncStore(database)
    }

    @After
    fun tearDown() {
        database.close()
        dbFile.deleteRecursively()
    }

    private fun command() = LocalEntryCommand(
        coupleId = coupleId,
        authorId = authorId,
        mode = EntryMode.PERSONAL,
        occurredAt = Instant.parse("2026-10-01T09:30:00Z"),
        occurredTimezone = "Asia/Shanghai",
        title = "回家的路上",
        blocks = listOf(
            LocalBlockCommand(
                type = BlockType.TEXT,
                orderKey = 0,
                payload = JSONObject().put("text", "今天想跟你说一件事").toString(),
                authorId = authorId,
            ),
        ),
    )

    @Test
    fun aRecordOwedTwoOperationsIsObservedInQueueOrder() = runBlocking {
        val entryId = writer.save(command(), publish = true)

        val rows = outbox.observeForEntity(entryId.toString()).first()

        assertEquals(listOf("CREATE_ENTRY", "PUBLISH_ENTRY"), rows.map { it.action })
        assertEquals(listOf("PENDING", "PENDING"), rows.map { it.state })
    }

    @Test
    fun anEmptiedQueueIsReportedAsDelivered() = runBlocking {
        val entryId = writer.save(command(), publish = true)
        outbox.observeForEntity(entryId.toString()).first().forEach { outbox.delete(it.operationId) }

        val rows = outbox.observeForEntity(entryId.toString()).first()

        assertTrue(rows.isEmpty())
        assertEquals(EntrySyncPhase.DELIVERED, entrySyncPhase(rows.map { QueuedOperation(it.state, it.attemptCount) }))
    }

    @Test
    fun aParkedRejectionIsTheRecordOnlyVisibleThroughTheQueue() = runBlocking {
        val entryId = writer.save(command(), publish = true)
        val create = outbox.observeForEntity(entryId.toString()).first().first()
        outbox.markRetry(create.operationId, "CONFLICT", create.attemptCount, Long.MAX_VALUE)

        assertEquals(listOf(entryId.toString()), outbox.observeRejectedEntityIds().first())
        assertEquals(
            EntrySyncPhase.REJECTED,
            entrySyncPhase(outbox.observeForEntity(entryId.toString()).first().map { QueuedOperation(it.state, it.attemptCount) }),
        )
    }

    @Test
    fun requeueingARejectionHandsItBackToTheEngine() = runBlocking {
        val entryId = writer.save(command(), publish = true)
        val rows = outbox.observeForEntity(entryId.toString()).first()
        val create = rows.first()
        val publish = rows.last()
        outbox.markRetry(create.operationId, "CONFLICT", 5, Long.MAX_VALUE)

        val requeued = outbox.requeueRejected(entryId.toString(), System.currentTimeMillis())

        assertEquals(1, requeued)
        assertEquals(listOf("PENDING", "PENDING"), outbox.observeForEntity(entryId.toString()).first().map { it.state })
        assertEquals(0, outbox.find(create.operationId)!!.attemptCount)
        assertTrue(outbox.find(create.operationId)!!.nextAttemptAtEpochMillis < Long.MAX_VALUE)
        // Only the refused operation moves; the one still waiting its turn is untouched.
        assertEquals("PENDING", outbox.find(publish.operationId)!!.state)
        assertEquals(0, outbox.find(publish.operationId)!!.attemptCount)
        assertEquals(emptyList<String>(), outbox.observeRejectedEntityIds().first())
    }

    @Test
    fun aRejectionHoldsBackItsOwnRecordButNoOtherRecord() = runBlocking {
        val stalled = writer.save(command(), publish = true)
        val healthy = writer.save(command(), publish = true)
        val create = outbox.observeForEntity(stalled.toString()).first().first()
        store.markConflict(UUID.fromString(create.operationId))

        val pushable = store.pendingOperations(limit = 50)

        assertEquals(
            listOf("CREATE_ENTRY", "PUBLISH_ENTRY"),
            pushable.map { it.action },
        )
        assertEquals(listOf(healthy, healthy), pushable.map { it.entityId })
        assertEquals(listOf(stalled.toString()), outbox.observeRejectedEntityIds().first())
    }

    @Test
    fun requeueingLandsTheWholeRecordBackInOrder() = runBlocking {
        val entryId = writer.save(command(), publish = true)
        val rows = outbox.observeForEntity(entryId.toString()).first()
        store.markConflict(UUID.fromString(rows.first().operationId))
        store.markConflict(UUID.fromString(rows.last().operationId))
        assertTrue(store.pendingOperations(limit = 50).isEmpty())

        outbox.requeueRejected(entryId.toString(), System.currentTimeMillis())

        assertEquals(
            listOf("CREATE_ENTRY", "PUBLISH_ENTRY"),
            store.pendingOperations(limit = 50).map { it.action },
        )
    }

    @Test
    fun aCommentOwedForTheSameRecordCountsAsUndeliveredToo() = runBlocking {
        val entryId = writer.save(command(), publish = true)
        outbox.observeForEntity(entryId.toString()).first().forEach { outbox.delete(it.operationId) }
        assertEquals(
            EntrySyncPhase.DELIVERED,
            entrySyncPhase(outbox.observeForEntity(entryId.toString()).first().map { QueuedOperation(it.state, it.attemptCount) }),
        )

        writer.addComment(coupleId, entryId, UUID.randomUUID(), authorId, "我在这里")

        val phase = entrySyncPhase(
            outbox.observeForEntity(entryId.toString()).first().map { QueuedOperation(it.state, it.attemptCount) },
        )
        assertEquals(EntrySyncPhase.QUEUED, phase)
    }
}
