package com.twomemory.app

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.twomemory.database.*
import com.twomemory.model.*
import com.twomemory.network.*
import com.twomemory.sync.SyncEngine
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

/** Real HTTP and PostgreSQL, two independent Room stores, not a simulated pairing server. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class TwoDevicePrivateKeepsakeLoopTest {
    private lateinit var running: RealServerHarness.Running
    @Before fun start() { running = RealServerHarness.start(18089, "moon_letter_private_keepsake_loop_test", "keepsake-test-only", "private-keepsake-loop-server.log") }
    @After fun stop() { if (::running.isInitialized) RealServerHarness.stop(running) }

    @Test fun privateRecordRestoresOnlyForAuthorThenSharingAndWithdrawalConverge() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val sessionApi = RetrofitSessionApi.create()
        val a = sessionApi.bootstrap(running.baseUrl, "keepsake-test-only", "甲")
        val b = sessionApi.pair(running.baseUrl, sessionApi.pairingToken(running.baseUrl, a.token, a.coupleId).pairingToken, "乙")
        val dbA = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        val dbB = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        val restored = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        try {
            PrivacyApi(running.baseUrl, a.token).requireSupport()
            val id = LocalEntryWriter(dbA).save(LocalEntryCommand(a.coupleId, a.userId, EntryMode.PERSONAL,
                Instant.now(), "Asia/Shanghai", "小秘密", listOf(LocalBlockCommand(type = BlockType.TEXT,
                    orderKey = 0, payload = "{\"text\":\"生日准备\"}", authorId = a.userId)), visibility = EntryVisibility.PRIVATE), publish = true)
            val engineA = SyncEngine(RetrofitCoupleDiaryApi.create(running.baseUrl) { a.token }, RoomSyncStore(dbA), a.coupleId)
            val engineB = SyncEngine(RetrofitCoupleDiaryApi.create(running.baseUrl) { b.deviceToken }, RoomSyncStore(dbB), a.coupleId)
            assertEquals(2, engineA.pushPending().applied)
            engineB.pullAll()
            assertNull(dbB.entryDao().findEntry(id.toString()))
            SyncEngine(RetrofitCoupleDiaryApi.create(running.baseUrl) { a.token }, RoomSyncStore(restored), a.coupleId).pullAll()
            assertEquals("PRIVATE", restored.entryDao().findEntry(id.toString())!!.visibility)
            var entry = dbA.entryDao().findEntry(id.toString())!!
            RoomSyncStore(dbA).mergeConfirmedEntry(PrivacyApi(running.baseUrl, a.token).change(id, "SHARED", entry.rowVersion))
            engineB.pullAll()
            assertEquals("小秘密", dbB.entryDao().findEntry(id.toString())!!.title)
            entry = dbA.entryDao().findEntry(id.toString())!!
            RoomSyncStore(dbA).mergeConfirmedEntry(PrivacyApi(running.baseUrl, a.token).change(id, "PRIVATE", entry.rowVersion))
            engineB.pullAll()
            assertTrue(dbB.entryDao().findEntry(id.toString())!!.deleted)
            assertTrue(dbB.entryDao().blocks(id.toString()).isEmpty())
        } finally { dbA.close(); dbB.close(); restored.close() }
    }

    @Test fun sealedLetterHasNoPartnerBodyUntilServerOpensAndPrivatePlanNeverAppears() = runBlocking {
        val api = RetrofitSessionApi.create()
        val a = api.bootstrap(running.baseUrl, "keepsake-test-only", "甲")
        val b = api.pair(running.baseUrl, api.pairingToken(running.baseUrl, a.token, a.coupleId).pairingToken, "乙")
        val author = CapsuleApi(running.baseUrl, a.token, a.coupleId)
        val partner = CapsuleApi(running.baseUrl, b.deviceToken, a.coupleId)
        val today = LocalDate.now(ZoneId.of("Asia/Shanghai"))
        val id = UUID.randomUUID().toString()
        val letter = CapsuleRequest("未来的信", "封口前不应泄露", today.plusDays(1).toString(), "SHARED")
        author.save(id, letter, a.userId.toString())
        assertNull(partner.read(b.userId.toString()).single().readableBody(b.userId.toString()))
        author.save(id, letter.copy(unlockDate = today.toString()), a.userId.toString())
        assertEquals("封口前不应泄露", partner.read(b.userId.toString()).single().readableBody(b.userId.toString()))
        val privateId = UUID.randomUUID().toString()
        author.save(privateId, letter.copy(visibility = "PRIVATE"), a.userId.toString())
        assertEquals(listOf(id), partner.read(b.userId.toString()).map { it.id })
        val personal = OccasionRequest("SURPRISE", "准备礼物", "不让对方提前知道", today.toString(), false, null, null, "PRIVATE")
        OccasionApi(running.baseUrl, a.token, a.coupleId).save(UUID.randomUUID().toString(), personal)
        assertTrue(OccasionApi(running.baseUrl, b.deviceToken, a.coupleId).read().isEmpty())
    }
}
