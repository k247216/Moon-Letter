package com.twomemory.database

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.twomemory.model.BlockType
import com.twomemory.model.EntryMode
import com.twomemory.model.LocalBlockCommand
import com.twomemory.model.LocalEntryCommand
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.time.Instant
import java.util.UUID

/**
 * An entry that still owes the server a picture must leave the device only once
 * every one of its images has an asset: a partial push would make the space hold
 * a record whose photo is missing forever, and a publish that overtakes its own
 * create would fail against a row the server never received. So the entry gates
 * as a whole and releases as a whole.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MediaGatedOutboxTest {

    private lateinit var context: Context
    private lateinit var dbFile: File
    private lateinit var database: AppDatabase
    private lateinit var writer: LocalEntryWriter
    private lateinit var store: RoomSyncStore

    private val coupleId = UUID.fromString("00000000-0000-0000-0000-00000000c0de")
    private val authorId = UUID.fromString("00000000-0000-0000-0000-0000000a11ce")

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        dbFile = File(context.cacheDir, "media-gate-${UUID.randomUUID()}.db")
        database = Room.databaseBuilder(context, AppDatabase::class.java, dbFile.absolutePath).build()
        writer = LocalEntryWriter(database)
        store = RoomSyncStore(database)
    }

    @After
    fun tearDown() {
        database.close()
        dbFile.deleteRecursively()
    }

    private fun imageBlock(orderKey: Long, id: UUID = UUID.randomUUID()) = LocalBlockCommand(
        id = id,
        type = BlockType.IMAGE,
        orderKey = orderKey,
        payload = JSONObject()
            .put("localPath", "/data/user/0/com.twomemory.app/files/photos/$id.png")
            .put("mime", "image/jpeg")
            .toString(),
        authorId = authorId,
    )

    private fun textBlock(text: String) = LocalBlockCommand(
        type = BlockType.TEXT,
        orderKey = 0,
        payload = JSONObject().put("text", text).toString(),
        authorId = authorId,
    )

    private fun command(vararg blocks: LocalBlockCommand) = LocalEntryCommand(
        coupleId = coupleId,
        authorId = authorId,
        mode = EntryMode.PERSONAL,
        occurredAt = Instant.parse("2026-09-30T12:18:00Z"),
        occurredTimezone = "Asia/Shanghai",
        title = "海边的照片",
        blocks = blocks.toList(),
    )

    /** orderKey -> assetId for every image block of a CREATE payload. */
    private fun assetIdsByOrderKey(payload: String): Map<Long, String> {
        val blocks = JSONObject(payload).getJSONArray("blocks")
        return (0 until blocks.length())
            .map { blocks.getJSONObject(it) }
            .filter { it.getString("type") == "IMAGE" }
            .associate { it.getLong("orderKey") to it.getString("assetId") }
    }

    @Test
    fun anEntryWaitingOnItsPictureIsNotPushableYet() = runBlocking {
        val entryId = writer.save(command(textBlock("今天去了海边"), imageBlock(1)), publish = true)

        assertEquals(emptyList<String>(), store.pendingOperations(limit = 50).map { it.action })
        assertEquals(
            listOf("CREATE_ENTRY", "PUBLISH_ENTRY"),
            database.outboxDao().mediaPendingForEntity(entryId.toString()).map { it.action },
        )
    }

    @Test
    fun theWholeEntryReleasesOnceTheLastPictureHasAnAsset() = runBlocking {
        val firstId = UUID.randomUUID()
        val secondId = UUID.randomUUID()
        val first = imageBlock(1, firstId)
        val second = imageBlock(2, secondId)
        val entryId = writer.save(command(first, second), publish = true)

        val firstAsset = UUID.randomUUID()
        writer.attachAsset(firstId, firstAsset)
        assertEquals("one picture still missing must gate the entry",
            emptyList<String>(), store.pendingOperations(limit = 50).map { it.action })
        assertEquals(2, database.outboxDao().mediaPendingForEntity(entryId.toString()).size)

        val secondAsset = UUID.randomUUID()
        writer.attachAsset(secondId, secondAsset)

        assertEquals(
            listOf("CREATE_ENTRY", "PUBLISH_ENTRY"),
            store.pendingOperations(limit = 50).map { it.action },
        )
        assertEquals(0, database.outboxDao().mediaPendingForEntity(entryId.toString()).size)
        val create = store.pendingOperations(limit = 50).first().payload
        assertEquals(
            "each image must reach the server with its own asset",
            mapOf(1L to firstAsset.toString(), 2L to secondAsset.toString()),
            assetIdsByOrderKey(create),
        )
    }

    @Test
    fun aReleasedPictureCarriesItsAssetButNeverItsLocalPath() = runBlocking {
        val image = imageBlock(1)
        val entryId = writer.save(command(image))
        assertEquals(1, database.outboxDao().mediaPendingForEntity(entryId.toString()).size)

        val assetId = UUID.randomUUID()
        writer.attachAsset(image.id, assetId)

        val operation = store.pendingOperations(limit = 50).single()
        assertEquals("CREATE_ENTRY", operation.action)
        assertEquals(mapOf(1L to assetId.toString()), assetIdsByOrderKey(operation.payload))
        assertEquals(0, database.outboxDao().mediaPendingForEntity(entryId.toString()).size)

        // A device file path is neither meaningful on the other phone nor hers to
        // receive: it names a location inside this app's private storage.
        assertFalse(operation.payload, operation.payload.contains("localPath"))
        assertEquals(
            "image/jpeg",
            JSONObject(JSONObject(operation.payload).getJSONArray("blocks").getJSONObject(0)
                .getString("payload")).getString("mime"),
        )

        // Room keeps it: the upload worker reads the file from there.
        assertEquals(
            "/data/user/0/com.twomemory.app/files/photos/${image.id}.png",
            JSONObject(database.entryDao().blocks(entryId.toString())
                .first { it.type == "IMAGE" }.payload).getString("localPath"),
        )
    }

    @Test
    fun anEntryWithoutPicturesIsNeverGated() = runBlocking {
        val entryId = writer.save(command(textBlock("只是文字")), publish = true)

        assertEquals(
            listOf("CREATE_ENTRY", "PUBLISH_ENTRY"),
            store.pendingOperations(limit = 50).map { it.action },
        )
        assertEquals(0, database.outboxDao().mediaPendingForEntity(entryId.toString()).size)
    }

    @Test
    fun anAppendBlockWithAPictureWaitsWhileItsEntryAlreadyWentOut() = runBlocking {
        val entryId = writer.save(command(textBlock("晚上好")))
        val image = imageBlock(1)
        writer.appendBlock(coupleId, entryId, image)

        assertEquals(
            listOf("CREATE_ENTRY"),
            store.pendingOperations(limit = 50).map { it.action },
        )
        assertEquals(
            listOf("APPEND_BLOCK"),
            database.outboxDao().mediaPendingForEntity(entryId.toString()).map { it.action },
        )

        val assetId = UUID.randomUUID()
        writer.attachAsset(image.id, assetId)

        assertEquals(
            listOf("CREATE_ENTRY", "APPEND_BLOCK"),
            store.pendingOperations(limit = 50).map { it.action },
        )
        assertEquals(0, database.outboxDao().mediaPendingForEntity(entryId.toString()).size)
        val appended = store.pendingOperations(limit = 50).first { it.action == "APPEND_BLOCK" }
        assertEquals(
            assetId.toString(),
            JSONObject(appended.payload).getJSONObject("block").getString("assetId"),
        )
    }
}
