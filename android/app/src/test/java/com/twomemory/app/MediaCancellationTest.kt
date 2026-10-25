package com.twomemory.app

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.twomemory.database.*
import com.twomemory.model.*
import com.twomemory.network.*
import java.io.File
import java.util.UUID
import kotlinx.coroutines.*
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MediaCancellationTest {
    @Test fun cancelledUploadIsNotReportedAsCompleted() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        val session = SyncSession.Session("test", UUID.randomUUID(), UUID.randomUUID(), "http://127.0.0.1:1")
        val file = File.createTempFile("audio-test", ".m4a").apply { writeBytes(byteArrayOf(1,2,3)) }
        val api = object : MediaApi by RetrofitMediaApi.create() {
            override suspend fun createUpload(baseUrl: String, bearer: String, command: MediaCreateCommand): UploadTicketDto {
                throw CancellationException("cancelled by owner")
            }
        }
        try {
            LocalEntryWriter(db).save(LocalEntryCommand(coupleId=session.coupleId, authorId=session.userId,
                mode=EntryMode.PERSONAL, occurredAt=java.time.Instant.now(), occurredTimezone="Asia/Shanghai",
                title=null, blocks=listOf(LocalBlockCommand(type=BlockType.AUDIO, orderKey=0,
                    payload=org.json.JSONObject().put("localPath", file.absolutePath).put("mime", "audio/mp4").toString(), authorId=session.userId))), publish=true)
            try { MediaUploadManager.uploadPendingImages(session, db, api); fail("Cancellation must propagate") }
            catch (expected: CancellationException) { assertEquals("cancelled by owner", expected.message) }
            assertEquals(1, db.entryDao().mediaBlocksWithoutAsset().size)
        } finally { db.close(); file.delete() }
    }
}
