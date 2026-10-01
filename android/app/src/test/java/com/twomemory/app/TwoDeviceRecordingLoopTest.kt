package com.twomemory.app

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.twomemory.database.AppDatabase
import com.twomemory.database.LocalEntryWriter
import com.twomemory.database.RoomSyncStore
import com.twomemory.model.BlockType
import com.twomemory.model.LocalBlockCommand
import com.twomemory.model.LocalEntryCommand
import com.twomemory.model.EntryMode
import com.twomemory.model.PendingOperation
import com.twomemory.network.PushResult
import com.twomemory.network.RetrofitCoupleDiaryApi
import com.twomemory.network.RetrofitSessionApi
import com.twomemory.sync.SyncEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.junit.After
import org.junit.AfterClass
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
import java.time.Instant
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * Task 11 gate: the personal-text two-device vertical slice, on the JVM per
 * the Task 8 harness decision — TWO independent REAL Room databases, REAL
 * Retrofit HTTP against a REAL Spring Boot server process with REAL
 * PostgreSQL. No fake server, no in-memory store.
 *
 * Requires the server executable jar: `mvn -DskipTests package` in `server/`.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TwoDeviceRecordingLoopTest {

    private lateinit var context: Context
    private lateinit var databaseA: AppDatabase
    private lateinit var databaseB: AppDatabase

    private val baseUrl = "http://127.0.0.1:18080"
    private val bootstrapSecret = "task11-slice-secret"
    private val jsonMediaType = "application/json".toMediaType()

    companion object {
        private const val TEST_DB = "moon_letter_slice_test"
        private const val BASE_URL = "http://127.0.0.1:18080"
        private const val BOOTSTRAP_SECRET = "task11-slice-secret"

        @JvmStatic
        private var running: RealServerHarness.Running? = null

        @BeforeClass
        @JvmStatic
        fun startRealServer() {
            running = RealServerHarness.start(18080, TEST_DB, BOOTSTRAP_SECRET, "slice-server.log")
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
        databaseA = Room.databaseBuilder(context, AppDatabase::class.java, "slice-device-a.db").build()
        databaseB = Room.databaseBuilder(context, AppDatabase::class.java, "slice-device-b.db").build()
    }

    @After
    fun tearDown() {
        databaseA.close()
        databaseB.close()
        context.getDatabasePath("slice-device-a.db").deleteRecursively()
        context.getDatabasePath("slice-device-b.db").deleteRecursively()
    }

    private fun call(method: String, path: String, json: String?, token: String?): String {
        val builder = Request.Builder().url(baseUrl + path)
        val body = json?.toRequestBody(jsonMediaType)
        when (method) {
            "POST" -> builder.post(body ?: "".toRequestBody(jsonMediaType))
            else -> builder.get()
        }
        if (token != null) builder.header("Authorization", "Bearer $token")
        if (json != null) builder.header("Content-Type", "application/json")
        val secret = if (path == "/api/v1/bootstrap") bootstrapSecret else null
        if (secret != null) builder.header("X-Bootstrap-Secret", secret)
        OkHttpClient.Builder()
            .connectTimeout(5, TimeUnit.SECONDS).readTimeout(15, TimeUnit.SECONDS).build()
            .newCall(builder.build()).execute().use { response ->
                val text = response.body?.string() ?: ""
                check(response.code == 200 || response.code == 201) {
                    "$method $path -> ${response.code}: $text"
                }
                return text
            }
    }

    private fun api(token: String) = RetrofitCoupleDiaryApi.create(baseUrl) { token }

    private fun command(
        coupleId: UUID,
        authorId: UUID,
        body: String,
        entryId: UUID = UUID.randomUUID(),
    ) = LocalEntryCommand(
        coupleId = coupleId,
        authorId = authorId,
        mode = EntryMode.PERSONAL,
        occurredAt = Instant.parse("2026-09-30T12:18:00Z"),
        occurredTimezone = "Asia/Shanghai",
        title = "傍晚散步",
        blocks = listOf(
            LocalBlockCommand(
                type = BlockType.TEXT,
                orderKey = 0,
                payload = JSONObject().put("text", body).toString(),
                authorId = authorId,
            ),
        ),
        entryId = entryId,
    )

    @Test
    fun twoDevicesRecordAndSeeEachOthersPublishedEntries() = runBlocking {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        try {
            // 1. Device A bootstraps the installation through the REAL setup
            //    flow the app ships (SetupViewModel + RetrofitSessionApi).
            val setupA = SetupViewModel(RetrofitSessionApi.create())
            setupA.updateServerUrl(baseUrl)
            setupA.updateBootstrapSecret(bootstrapSecret)
            setupA.updateDisplayName("小满")
            setupA.bootstrap()
            awaitSetupIdle(setupA)
            val setupState = setupA.state.value
            assertEquals(SetupViewModel.Phase.WAITING_PARTNER, setupState.phase)
            val boundA = setupState.boundSession!!
            val tokenA = boundA.token
            val coupleId = boundA.coupleId
            val userIdA = boundA.userId
            val pairingToken = setupState.shownPairingToken.orEmpty()
            assertTrue("pairing token expected", pairingToken.isNotBlank())

            // 2. Device B pairs through the same real flow and gets its own session.
            val setupB = SetupViewModel(RetrofitSessionApi.create())
            setupB.updateMode(SetupViewModel.Mode.PARTNER_DEVICE)
            setupB.updateServerUrl(baseUrl)
            setupB.updatePairingToken(pairingToken)
            setupB.updateDisplayName("阿屿")
            setupB.pair()
            awaitSetupIdle(setupB)
            assertEquals(
                "pair failed: phase=${setupB.state.value.phase} error=${setupB.state.value.error}",
                SetupViewModel.Phase.BOUND, setupB.state.value.phase,
            )
            val boundB = setupB.state.value.boundSession!!
            val tokenB = boundB.token
            val userIdB = boundB.userId
            assertEquals(coupleId, boundB.coupleId)
            assertEquals(
                setOf("小满", "阿屿"),
                RetrofitSessionApi.create().readSpace(baseUrl, tokenB, coupleId)
                    .members.map { it.profile.displayName }.toSet(),
            )

            // The screen persists the issued session; prove the roundtrip works.
            SyncSession.save(context, tokenA, coupleId, userIdA, baseUrl)
            val loaded = SyncSession.load(context)!!
            assertEquals(coupleId, loaded.coupleId)
            assertEquals(userIdA, loaded.userId)
            assertEquals(baseUrl, loaded.baseUrl)

            // 2.5 Renaming through the shipped client reaches the partner's space
            //     read and refreshes this device's cached names.
            assertEquals("小满呀", SyncSession.renameOwn(context, "小满呀"))
            assertTrue(
                "partner must see the rename",
                RetrofitSessionApi.create().readSpace(baseUrl, tokenB, coupleId)
                    .members.map { it.profile.displayName }.contains("小满呀"),
            )
            assertEquals("小满呀", SyncSession.loadNames(context).own)

            // 3. Device A writes OFFLINE and taps 发布: Room holds a draft, the
            //    outbox holds the create followed by the publish.
            val storeA = RoomSyncStore(databaseA)
            val entryIdA = UUID.randomUUID()
            val writerA = LocalEntryWriter(databaseA)
            writerA.save(command(coupleId, userIdA, "A 的一天：暴雨", entryIdA), publish = true)
            assertEquals(
                listOf("CREATE_ENTRY", "PUBLISH_ENTRY"),
                storeA.pendingOperations(10).map { it.action },
            )
            assertEquals("DRAFT", databaseA.entryDao().findEntry(entryIdA.toString())!!.state)

            // 4. While it is a draft, A's record does not exist for B — not
            //    even after A pushes it: the private create writes no feed row.
            val createA = storeA.pendingOperations(10).first()
            assertEquals(0, SyncEngine(api(tokenB), RoomSyncStore(databaseB), coupleId).pullAll().pulled)
            assertEquals(2, SyncEngine(api(tokenA), storeA, coupleId).pushPending().applied)
            val draftOnlyPull = SyncEngine(api(tokenB), RoomSyncStore(databaseB), coupleId).pullAll()
            assertEquals("one change row for create+publish", 1, draftOnlyPull.pulled)

            // 5. A duplicate timeout retry of a consumed operation hits server
            //    idempotency: replayed, and no extra change row appears.
            val replay = api(tokenA).push(createA)
            assertEquals(PushResult.Status.APPLIED, replay.status)
            assertTrue("replayed response expected", replay.replayed)
            assertEquals(0, SyncEngine(api(tokenB), RoomSyncStore(databaseB), coupleId).pullAll().pulled)

            // 6. The author's device adopts the server version from the push
            //    response: a personal record is never pulled back, so without
            //    this merge A would keep showing its own record as unsent.
            val ownCopyA = databaseA.entryDao().findEntry(entryIdA.toString())!!
            assertEquals("PUBLISHED", ownCopyA.state)
            assertEquals(1L, ownCopyA.rowVersion)

            // 7. B's copy is rebuildable from that single row: published, with
            //    the body text, not an id-only stub.
            val seenByB = databaseB.entryDao().findEntry(entryIdA.toString())
            assertNotNull(seenByB)
            assertEquals("傍晚散步", seenByB!!.title)
            assertEquals("PUBLISHED", seenByB.state)
            val seenByBBlocks = databaseB.entryDao().blocks(entryIdA.toString())
            assertEquals(1, seenByBBlocks.size)
            assertTrue(seenByBBlocks.single().payload.contains("暴雨"))

            // 8. A catches up (the shared feed carries its own publish row too),
            //    then B writes a draft WITHOUT publishing and pushes it.
            SyncEngine(api(tokenA), storeA, coupleId).pullAll()
            val privateIdB = UUID.randomUUID()
            LocalEntryWriter(databaseB).save(command(coupleId, userIdB, "B 还没写完", privateIdB))
            assertEquals(1, SyncEngine(api(tokenB), RoomSyncStore(databaseB), coupleId).pushPending().applied)
            assertEquals(0, SyncEngine(api(tokenA), storeA, coupleId).pullAll().pulled)
            assertNull(databaseA.entryDao().findEntry(privateIdB.toString()))

            // 9. Reverse reconnect: B publishes, A pulls and sees it.
            val entryIdB = UUID.randomUUID()
            LocalEntryWriter(databaseB).save(command(coupleId, userIdB, "B 的一天：台风天", entryIdB), publish = true)
            assertEquals(2, SyncEngine(api(tokenB), RoomSyncStore(databaseB), coupleId).pushPending().applied)
            SyncEngine(api(tokenA), storeA, coupleId).pullAll()
            assertNotNull(databaseA.entryDao().findEntry(entryIdB.toString()))

            // 10. What each device can actually read: B's timeline holds both
            //     published records, and its own unwritten-out draft is still
            //     just a draft rather than a page of the shared book.
            assertEquals(
                setOf(entryIdA.toString(), entryIdB.toString()),
                databaseB.entryDao().timelineSnapshot()
                    .filter { it.state == "PUBLISHED" }.map { it.id }.toSet(),
            )
            assertEquals("DRAFT", databaseB.entryDao().findEntry(privateIdB.toString())!!.state)

            // 11. App-process recreation: closing and reopening Room keeps everything.
            databaseA.close()
            databaseA = Room.databaseBuilder(context, AppDatabase::class.java, "slice-device-a.db").build()
            assertNotNull(databaseA.entryDao().findEntry(entryIdA.toString()))
            assertNotNull(databaseA.entryDao().findEntry(entryIdB.toString()))
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
