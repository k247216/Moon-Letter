package com.twomemory.database

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class OutboxQueueOrderTest {
    private lateinit var database: AppDatabase
    private lateinit var outbox: OutboxDao

    private val now = 1_000L
    private val createId = "00000000-0000-0000-0000-000000000002"
    private val publishId = "00000000-0000-0000-0000-000000000001"

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java,
        ).build()
        outbox = database.outboxDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun aCreateInBackoffBlocksItsDuePublish() = runBlocking {
        outbox.insert(operation(createId, "entry", "CREATE_ENTRY"))
        outbox.insert(operation(publishId, "entry", "PUBLISH_ENTRY"))
        outbox.markRetry(createId, "PENDING", attemptCount = 1, nextAt = now + 1)

        assertEquals(emptyList<String>(), outbox.pending(now, 50).map { it.operationId })
    }

    @Test
    fun aMediaPendingCreateBlocksItsDuePublishEvenWhenItsRetryTimeIsDue() = runBlocking {
        outbox.insert(operation(createId, "entry", "CREATE_ENTRY", state = "MEDIA_PENDING"))
        outbox.insert(operation(publishId, "entry", "PUBLISH_ENTRY"))

        assertEquals(emptyList<String>(), outbox.pending(now, 50).map { it.operationId })
    }

    @Test
    fun aCreateInBackoffDoesNotBlockAnotherEntryOrConsumeTheLimit() = runBlocking {
        outbox.insert(operation(createId, "stalled", "CREATE_ENTRY", nextAt = now + 1))
        outbox.insert(operation(publishId, "stalled", "PUBLISH_ENTRY"))
        outbox.insert(operation("healthy-create", "healthy", "CREATE_ENTRY"))

        assertEquals(listOf("healthy-create"), outbox.pending(now, 1).map { it.operationId })
    }

    @Test
    fun aMediaPendingCreateDoesNotBlockAnotherEntry() = runBlocking {
        outbox.insert(operation(createId, "stalled", "CREATE_ENTRY", state = "MEDIA_PENDING"))
        outbox.insert(operation(publishId, "stalled", "PUBLISH_ENTRY"))
        outbox.insert(operation("healthy-create", "healthy", "CREATE_ENTRY"))

        assertEquals(listOf("healthy-create"), outbox.pending(now, 50).map { it.operationId })
    }

    @Test
    fun acknowledgingTheEarlierCreateMakesItsDuePublishAvailable() = runBlocking {
        outbox.insert(operation(createId, "entry", "CREATE_ENTRY", nextAt = now + 1))
        outbox.insert(operation(publishId, "entry", "PUBLISH_ENTRY"))
        assertEquals(emptyList<String>(), outbox.pending(now, 50).map { it.operationId })

        RoomSyncStore(database).markApplied(UUID.fromString(createId), serverEntrySnapshot = null)

        assertNull(outbox.find(createId))
        assertEquals(listOf(publishId), outbox.pending(now, 50).map { it.operationId })
    }

    @Test
    fun equalTimesKeepInsertionOrderRatherThanOperationIdOrder() = runBlocking {
        // Outbox has no createdAt column. Equal retry times and payload timestamps
        // must still use rowid insertion order, even when IDs sort the other way.
        outbox.insert(operation(createId, "entry", "CREATE_ENTRY"))
        outbox.insert(operation(publishId, "entry", "PUBLISH_ENTRY"))

        assertEquals(listOf(createId, publishId), outbox.pending(now, 50).map { it.operationId })
    }

    @Test
    fun aLaterOperationInBackoffDoesNotBlockAnEarlierDueCreate() = runBlocking {
        outbox.insert(operation(createId, "entry", "CREATE_ENTRY"))
        outbox.insert(operation(publishId, "entry", "PUBLISH_ENTRY", nextAt = now + 1))

        assertEquals(listOf(createId), outbox.pending(now, 50).map { it.operationId })
    }

    @Test
    fun theCreateAndPublishBecomeAvailableInOrderAtTheRetryBoundary() = runBlocking {
        outbox.insert(operation(createId, "entry", "CREATE_ENTRY", nextAt = now + 1))
        outbox.insert(operation(publishId, "entry", "PUBLISH_ENTRY"))
        assertEquals(emptyList<String>(), outbox.pending(now, 50).map { it.operationId })

        assertEquals(listOf(createId, publishId), outbox.pending(now + 1, 50).map { it.operationId })
    }

    private fun operation(
        id: String,
        entryId: String,
        action: String,
        state: String = "PENDING",
        nextAt: Long = now,
    ) = OutboxOperationEntity(
        operationId = id,
        coupleId = "00000000-0000-0000-0000-00000000c0de",
        entityId = entryId,
        action = action,
        payload = "{\"createdAt\":\"2026-10-10T00:00:00Z\"}",
        baseVersion = 0,
        state = state,
        nextAttemptAtEpochMillis = nextAt,
    )
}
