package com.twomemory.app

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.twomemory.database.AppDatabase
import com.twomemory.database.LocalEntryWriter
import com.twomemory.database.RoomSyncStore
import com.twomemory.model.BlockType
import com.twomemory.model.EntryMode
import com.twomemory.model.LocalBlockCommand
import com.twomemory.model.LocalEntryCommand
import com.twomemory.network.RetrofitCoupleDiaryApi
import com.twomemory.network.RetrofitMediaApi
import com.twomemory.network.RetrofitSessionApi
import com.twomemory.sync.SyncEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.AfterClass
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.json.JSONObject
import java.awt.image.BufferedImage
import java.io.File
import java.nio.file.Files
import java.security.MessageDigest
import java.time.Instant
import java.util.UUID
import javax.imageio.ImageIO

/**
 * Task 13 gate on the JVM, per the Task 8 harness decision: TWO independent REAL
 * Room databases, REAL Retrofit HTTP against a REAL Spring Boot server with REAL
 * PostgreSQL and REAL local object storage.
 *
 * It pins the two things a record needs before it can be shown to a partner: a
 * picture that is byte-identical on the other phone, and a comment that arrives.
 *
 * Requires the server executable jar: `mvn -DskipTests package` in `server/`.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TwoDevicePictureAndCommentLoopTest {

    private lateinit var context: Context
    private lateinit var databaseA: AppDatabase
    private lateinit var databaseB: AppDatabase

    companion object {
        private const val TEST_DB = "moon_letter_media_test"
        private const val BASE_URL = "http://127.0.0.1:18081"
        private const val BOOTSTRAP_SECRET = "task13-media-secret"

        @JvmStatic
        private var running: RealServerHarness.Running? = null

        @BeforeClass
        @JvmStatic
        fun startRealServer() {
            running = RealServerHarness.start(18081, TEST_DB, BOOTSTRAP_SECRET, "media-server.log")
        }

        @AfterClass
        @JvmStatic
        fun stopRealServer() {
            running?.let(RealServerHarness::stop)
            running = null
        }
    }

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        databaseA = Room.databaseBuilder(context, AppDatabase::class.java, "media-device-a.db").build()
        databaseB = Room.databaseBuilder(context, AppDatabase::class.java, "media-device-b.db").build()
    }

    @After
    fun tearDown() {
        databaseA.close()
        databaseB.close()
        context.getDatabasePath("media-device-a.db").deleteRecursively()
        context.getDatabasePath("media-device-b.db").deleteRecursively()
    }

    private fun api(token: String) = RetrofitCoupleDiaryApi.create(BASE_URL) { token }

    /** A genuine PNG on disk, exactly what the editor copies into filesDir/photos. */
    private fun realPng(tag: String): File {
        val image = BufferedImage(64, 40, BufferedImage.TYPE_INT_RGB)
        for (x in 0 until 64) for (y in 0 until 40) image.setRGB(x, y, (x * 400 + y * 7 + tag.hashCode()) and 0xFFFFFF)
        val file = Files.createTempFile("moon-$tag", ".png").toFile()
        assertTrue(ImageIO.write(image, "png", file))
        return file
    }

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    @Test
    fun aPictureAndAReplyReachThePartnerPhone() = runBlocking {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        try {
            val setupA = SetupViewModel(RetrofitSessionApi.create())
            setupA.updateServerUrl(BASE_URL)
            setupA.updateBootstrapSecret(BOOTSTRAP_SECRET)
            setupA.updateDisplayName("小满")
            setupA.bootstrap()
            awaitSetupIdle(setupA)
            val boundA = setupA.state.value.boundSession
                ?: error("phase=${setupA.state.value.phase} error=${setupA.state.value.error}")
            val coupleId = boundA.coupleId
            val userIdA = boundA.userId

            val setupB = SetupViewModel(RetrofitSessionApi.create())
            setupB.updateMode(SetupViewModel.Mode.PARTNER_DEVICE)
            setupB.updateServerUrl(BASE_URL)
            setupB.updatePairingToken(setupA.state.value.shownPairingToken.orEmpty())
            setupB.updateDisplayName("阿屿")
            setupB.pair()
            awaitSetupIdle(setupB)
            val boundB = setupB.state.value.boundSession
                ?: error("phase=${setupB.state.value.phase} error=${setupB.state.value.error}")

            // 1. A writes a record with a real picture and taps 发布. The record is
            //    not pushable yet: both its operations wait for the upload.
            val png = realPng("a")
            val pngBytes = png.readBytes()
            val entryId = UUID.randomUUID()
            val imageBlockId = UUID.randomUUID()
            LocalEntryWriter(databaseA).save(
                LocalEntryCommand(
                    coupleId = coupleId,
                    authorId = userIdA,
                    mode = EntryMode.PERSONAL,
                    occurredAt = Instant.parse("2026-10-01T12:18:00Z"),
                    occurredTimezone = "Asia/Shanghai",
                    title = "河边的晚霞",
                    blocks = listOf(
                        LocalBlockCommand(
                            type = BlockType.TEXT,
                            orderKey = 0,
                            payload = JSONObject().put("text", "云烧到一半就灭了").toString(),
                            authorId = userIdA,
                        ),
                        LocalBlockCommand(
                            id = imageBlockId,
                            type = BlockType.IMAGE,
                            orderKey = 1,
                            payload = JSONObject()
                                .put("localPath", png.absolutePath)
                                .put("mime", "image/png")
                                .toString(),
                            authorId = userIdA,
                        ),
                    ),
                    entryId = entryId,
                ),
                publish = true,
            )
            val storeA = RoomSyncStore(databaseA)
            assertEquals(emptyList<String>(), storeA.pendingOperations(10).map { it.action })
            assertEquals(
                listOf("CREATE_ENTRY", "PUBLISH_ENTRY"),
                databaseA.outboxDao().mediaPendingForEntity(entryId.toString()).map { it.action },
            )
            assertEquals("DRAFT", databaseA.entryDao().findEntry(entryId.toString())!!.state)
            assertEquals(0, SyncEngine(api(boundA.token), storeA, coupleId).pushPending().applied)
            assertEquals(0, SyncEngine(api(boundB.token), RoomSyncStore(databaseB), coupleId).pullAll().pulled)
            // An unsent picture must not half-publish: the partner sees nothing at all.
            assertNull(databaseB.entryDao().findEntry(entryId.toString()))

            // 2. The uploader streams the bytes, fills the asset id into the stored
            //    operations, and releases the whole entry in one go.
            MediaUploadManager.uploadPendingImages(
                SyncSession.Session(boundA.token, coupleId, userIdA, BASE_URL),
                databaseA,
            )
            val released = storeA.pendingOperations(10)
            assertEquals(listOf("CREATE_ENTRY", "PUBLISH_ENTRY"), released.map { it.action })
            val assetId = UUID.fromString(
                databaseA.entryDao().blocks(entryId.toString())
                    .first { it.id == imageBlockId.toString() }.assetId!!,
            )
            val createPayload = JSONObject(released.first { it.action == "CREATE_ENTRY" }.payload)
            val wireAsset = createPayload.getJSONArray("blocks")
                .let { blocks -> (0 until blocks.length()).map { blocks.getJSONObject(it) } }
                .first { it.getString("blockId") == imageBlockId.toString() }
                .getString("assetId")
            assertEquals("the operation carries the asset the upload returned", assetId.toString(), wireAsset)

            // 3. The publish succeeds because the server holds a READY asset for it.
            assertEquals(2, SyncEngine(api(boundA.token), storeA, coupleId).pushPending().applied)
            assertEquals("PUBLISHED", databaseA.entryDao().findEntry(entryId.toString())!!.state)

            // 4. B pulls the record and can render the picture: the bytes it downloads
            //    hash to the same value as the file A picked.
            val engineB = SyncEngine(api(boundB.token), RoomSyncStore(databaseB), coupleId)
            assertTrue(engineB.pullAll().pulled > 0)
            val seenBlocks = databaseB.entryDao().blocks(entryId.toString())
            assertEquals(2, seenBlocks.size)
            val seenImage = seenBlocks.first { it.type == "IMAGE" }
            assertEquals(assetId, UUID.fromString(seenImage.assetId!!))
            val downloaded = RetrofitMediaApi.create().download(BASE_URL, boundB.token, assetId)
            assertEquals(sha256(pngBytes), sha256(downloaded))

            // 5. A replies under the record; B's copy of the record shows the reply.
            val commentId = UUID.randomUUID()
            LocalEntryWriter(databaseA).addComment(coupleId, entryId, commentId, userIdA, "明天再去一次好不好")
            assertEquals(1, SyncEngine(api(boundA.token), storeA, coupleId).pushPending().applied)
            engineB.pullAll()
            val commentsOnB = databaseB.commentDao().commentsForEntry(entryId.toString())
            assertEquals(1, commentsOnB.size)
            assertEquals("明天再去一次好不好", commentsOnB.single().body)
            assertEquals(userIdA.toString(), commentsOnB.single().authorId)
            assertEquals(1, databaseA.commentDao().commentsForEntry(entryId.toString()).size)

            // 6. App-process recreation keeps both the record and the reply.
            databaseA.close()
            databaseA = Room.databaseBuilder(context, AppDatabase::class.java, "media-device-a.db").build()
            assertNotNull(databaseA.entryDao().findEntry(entryId.toString()))
            assertEquals(1, databaseA.commentDao().commentsForEntry(entryId.toString()).size)
        } finally {
            Dispatchers.resetMain()
        }
    }

    /** The setup flow runs on Dispatchers.IO under the hood; wait for it to settle. */
    private suspend fun awaitSetupIdle(viewModel: SetupViewModel) {
        val deadline = System.currentTimeMillis() + 30_000
        while (viewModel.state.value.busy) {
            check(System.currentTimeMillis() < deadline) { "setup flow timed out" }
            delay(50)
        }
    }
}
