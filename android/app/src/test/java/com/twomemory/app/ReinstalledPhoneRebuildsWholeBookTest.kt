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
import com.twomemory.network.RetrofitSessionApi
import com.twomemory.sync.SyncEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
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
import java.time.Instant
import java.util.UUID

/**
 * The open-day sequence and the one thing that makes it safe, on the JVM per the
 * Task 8 harness decision: real Room files, real Retrofit, real Spring Boot,
 * real PostgreSQL.
 *
 * 清除应用数据 deletes the Room file and the stored session together. Two
 * promises have to survive that: the deployment secret must return the SAME
 * member of the SAME space (not a third member squeezing the partner out of her
 * slot), and the now-empty phone must pull the whole book back out of the change
 * feed — records, replies and names included. Nothing else in this suite proves
 * the second one, because no other device ever starts at cursor 0 after the space
 * has content.
 *
 * Requires the server executable jar: `mvn -DskipTests package` in `server/`.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ReinstalledPhoneRebuildsWholeBookTest {

    private lateinit var context: Context
    private lateinit var databaseA: AppDatabase
    private lateinit var databaseB: AppDatabase

    companion object {
        private const val TEST_DB = "moon_letter_reinstall_test"
        private const val BASE_URL = "http://127.0.0.1:18082"
        private const val BOOTSTRAP_SECRET = "reinstall-slice-secret"
        private const val SESSION_PREFS = "moon_letter_session"
        private const val DEVICE_A_DB = "reinstall-device-a.db"
        private const val DEVICE_B_DB = "reinstall-device-b.db"

        @JvmStatic
        private var running: RealServerHarness.Running? = null

        @BeforeClass
        @JvmStatic
        fun startRealServer() {
            running = RealServerHarness.start(18082, TEST_DB, BOOTSTRAP_SECRET, "reinstall-server.log")
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
        databaseA = open(DEVICE_A_DB)
        databaseB = open(DEVICE_B_DB)
    }

    @After
    fun tearDown() {
        databaseA.close()
        databaseB.close()
        context.getDatabasePath(DEVICE_A_DB).deleteRecursively()
        context.getDatabasePath(DEVICE_B_DB).deleteRecursively()
        context.getSharedPreferences(SESSION_PREFS, Context.MODE_PRIVATE).edit().clear().apply()
    }

    private fun open(name: String) =
        Room.databaseBuilder(context, AppDatabase::class.java, name).build()

    private fun api(token: String) = RetrofitCoupleDiaryApi.create(BASE_URL) { token }

    private fun record(
        coupleId: UUID,
        authorId: UUID,
        body: String,
        entryId: UUID,
        occurredAt: String,
    ) = LocalEntryCommand(
        coupleId = coupleId,
        authorId = authorId,
        mode = EntryMode.PERSONAL,
        occurredAt = Instant.parse(occurredAt),
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
    fun aWipedPhoneGetsItsWholeBookBack() = runBlocking {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        try {
            // 1. Both devices bind through the shipped screens.
            val setupA = SetupViewModel(RetrofitSessionApi.create())
            setupA.updateServerUrl(BASE_URL)
            setupA.updateBootstrapSecret(BOOTSTRAP_SECRET)
            setupA.updateDisplayName("小满")
            setupA.bootstrap()
            awaitSetupIdle(setupA)
            val boundA = setupA.state.value.boundSession
                ?: error("bootstrap failed: ${setupA.state.value.error}")
            val coupleId = boundA.coupleId
            val userIdA = boundA.userId
            assertEquals(SetupViewModel.Phase.WAITING_PARTNER, setupA.state.value.phase)

            // The name she is remembered by is set once, on purpose different from
            // what she will re-declare after the wipe.
            SyncSession.save(context, boundA.token, coupleId, userIdA, BASE_URL)
            assertEquals("小满呀", SyncSession.renameOwn(context, "小满呀"))

            val setupB = SetupViewModel(RetrofitSessionApi.create())
            setupB.updateMode(SetupViewModel.Mode.PARTNER_DEVICE)
            setupB.updateServerUrl(BASE_URL)
            setupB.updatePairingToken(setupA.state.value.shownPairingToken.orEmpty())
            setupB.updateDisplayName("阿屿")
            setupB.pair()
            awaitSetupIdle(setupB)
            val boundB = setupB.state.value.boundSession
                ?: error("pair failed: ${setupB.state.value.error}")
            val userIdB = boundB.userId

            // 2. A week of content: three published records and one reply. Pushed in
            //    dependency order — a reply to a record the server has never seen
            //    would stall the strictly-FIFO outbox.
            val storeA = RoomSyncStore(databaseA)
            val storeB = RoomSyncStore(databaseB)
            val entry1 = UUID.randomUUID()
            val entry2 = UUID.randomUUID()
            val entry3 = UUID.randomUUID()
            LocalEntryWriter(databaseA).save(
                record(coupleId, userIdA, "第一次一起做饭", entry1, "2026-09-05T20:00:00Z"), publish = true)
            LocalEntryWriter(databaseA).save(
                record(coupleId, userIdA, "雨很大但很值得", entry2, "2026-09-12T20:00:00Z"), publish = true)
            assertEquals(4, SyncEngine(api(boundA.token), storeA, coupleId).pushPending().applied)
            LocalEntryWriter(databaseB).save(
                record(coupleId, userIdB, "她比我先到终点", entry3, "2026-09-20T08:00:00Z"), publish = true)
            assertEquals(2, SyncEngine(api(boundB.token), storeB, coupleId).pushPending().applied)
            LocalEntryWriter(databaseA).addComment(coupleId, entry3, UUID.randomUUID(), userIdA, "明天再去一次好不好")
            assertEquals(1, SyncEngine(api(boundA.token), storeA, coupleId).pushPending().applied)
            // Four feed rows in total — two publishes by A, one by B, her reply — and
            // both devices read all four from their own cursor 0.
            assertEquals(4, SyncEngine(api(boundA.token), storeA, coupleId).pullAll().pulled)
            assertEquals(4, SyncEngine(api(boundB.token), storeB, coupleId).pullAll().pulled)
            assertEquals(
                setOf(entry1, entry2, entry3),
                databaseB.entryDao().observeTimeline().first()
                    .filter { it.state == "PUBLISHED" }.map { UUID.fromString(it.id) }.toSet(),
            )

            // 3. 清除应用数据: the Room file and the session go, and nothing but the
            //    server keeps the book. The app must fall back to the binding screen.
            databaseA.close()
            context.getDatabasePath(DEVICE_A_DB).deleteRecursively()
            context.getSharedPreferences(SESSION_PREFS, Context.MODE_PRIVATE).edit().clear().apply()
            assertNull("a wiped device has no session to load", SyncSession.load(context))
            databaseA = open(DEVICE_A_DB)
            val freshStoreA = RoomSyncStore(databaseA)
            assertTrue(databaseA.entryDao().observeTimeline().first().isEmpty())
            assertEquals("a wiped device starts at the head of the feed", 0L, freshStoreA.currentCursor(coupleId))

            // 4. Bootstrap again with the SAME secret: same person, same space, and
            //    the name she already had is not re-declared back to the old one.
            val again = SetupViewModel(RetrofitSessionApi.create())
            again.updateServerUrl(BASE_URL)
            again.updateBootstrapSecret(BOOTSTRAP_SECRET)
            again.updateDisplayName("小满")
            again.bootstrap()
            awaitSetupIdle(again)
            val rebound = again.state.value.boundSession
                ?: error("re-bootstrap failed: ${again.state.value.error}")
            assertEquals("the secret reclaims the founding member's own slot", userIdA, rebound.userId)
            assertEquals(coupleId, rebound.coupleId)
            assertEquals(
                "both slots are taken, so the code this phone mints opens the partner's own slot",
                "REJOIN", again.state.value.shownPairingTokenKind,
            )
            assertEquals(
                setOf("小满呀", "阿屿"),
                RetrofitSessionApi.create().readSpace(BASE_URL, rebound.token, coupleId)
                    .members.map { it.profile.displayName }.toSet(),
            )

            // 5. The token that lived on the wiped phone is dead now: reclaim revokes
            //    that member's own sessions, so a lost phone does not keep writing.
            val stale = SyncEngine(api(boundA.token), freshStoreA, coupleId).pullAll()
            assertTrue("the revoked session must report it needs re-pairing", stale.needsRePair)
            assertEquals(0, stale.pulled)
            assertEquals(
                "a refused page must not move the cursor",
                0L, freshStoreA.currentCursor(coupleId),
            )

            // 6. The whole book comes back with the new token: every published
            //    record, its body, the reply on it, and what both people are called.
            SyncSession.save(context, rebound.token, rebound.coupleId, rebound.userId, BASE_URL)
            val rebuilt = SyncEngine(api(rebound.token), freshStoreA, coupleId).pullAll()
            assertNull("pull failed: ${rebuilt.failed}", rebuilt.failed)
            assertEquals("three records plus the one reply", 4, rebuilt.pulled)
            val timeline = databaseA.entryDao().observeTimeline().first()
            assertEquals(setOf(entry1, entry2, entry3), timeline.map { UUID.fromString(it.id) }.toSet())
            assertEquals(setOf("PUBLISHED"), timeline.map { it.state }.toSet())
            assertTrue(
                databaseA.entryDao().blocks(entry3.toString()).single().payload.contains("她比我先到终点"),
            )
            assertEquals(
                "明天再去一次好不好",
                databaseA.commentDao().commentsForEntry(entry3.toString()).single().body,
            )
            SyncSession.refreshNames(context)
            assertEquals(SyncSession.Names("小满呀", "阿屿"), SyncSession.loadNames(context))

            // 7. And the reclaimed phone can write again: the partner sees the new
            //    record without re-pairing anything on its own side.
            val entry4 = UUID.randomUUID()
            LocalEntryWriter(databaseA).save(
                record(coupleId, userIdA, "重装之后的第一条", entry4, "2026-09-28T21:00:00Z"), publish = true)
            assertEquals(2, SyncEngine(api(rebound.token), freshStoreA, coupleId).pushPending().applied)
            assertEquals(1, SyncEngine(api(boundB.token), RoomSyncStore(databaseB), coupleId).pullAll().pulled)
            val seenByPartner = databaseB.entryDao().findEntry(entry4.toString())
            assertNotNull(seenByPartner)
            assertEquals("PUBLISHED", seenByPartner!!.state)
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
