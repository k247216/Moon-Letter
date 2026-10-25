package com.twomemory.app.notifications

import android.Manifest
import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.twomemory.app.SyncSession
import com.twomemory.database.AppDatabase
import com.twomemory.database.EntryEntity
import com.twomemory.database.CommentEntity
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** Real account-scoped Room records and platform notices, never cloud production data. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class NoticeArrivalTest {
    private lateinit var context: Context
    private lateinit var db: AppDatabase
    private lateinit var manager: NotificationManager
    private val couple = UUID.randomUUID()
    private val own = UUID.randomUUID()
    private val partner = UUID.randomUUID()

    @Before fun setup() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        context = app
        shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        SyncSession.save(context, "synthetic-notice-token", couple, own)
        db = AppDatabase.build(context, requireNotNull(SyncSession.load(context)).databaseName)
        manager = context.getSystemService(NotificationManager::class.java)!!
    }
    @After fun close() { db.close() }

    private suspend fun record(id: String, author: UUID = partner, space: UUID = couple,
        visibility: String = "SHARED") {
        db.entryDao().insertEntry(EntryEntity(id, space.toString(), author.toString(), "PERSONAL", "PUBLISHED",
            1000, "Asia/Shanghai", "合成私密标题", visibility = visibility))
    }

    @Test fun firstNewRecord() = runBlocking {
        NewEntryNotice.announceIfNeeded(context)
        record("first")
        NewEntryNotice.announceIfNeeded(context)
        assertEquals(1, manager.activeNotifications.size)
    }

    @Test fun foregroundSyncWithoutAuxiliaryRefreshStillAnnouncesArrivals() = runBlocking {
        runCatching { androidx.work.WorkManager.getInstance(context) }.getOrElse {
            androidx.work.WorkManager.initialize(context, androidx.work.Configuration.Builder().build())
        }
        val server = com.sun.net.httpserver.HttpServer.create(java.net.InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/") { exchange ->
            val bytes = "{\"changes\":[],\"nextSequence\":7,\"hasMore\":false}".toByteArray()
            exchange.responseHeaders.add("Content-Type", "application/json")
            exchange.sendResponseHeaders(200, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
        server.start()
        try {
            db.close()
            SyncSession.save(context, "synthetic-notice-token", couple, own, "http://127.0.0.1:${server.address.port}")
            db = AppDatabase.build(context, requireNotNull(SyncSession.load(context)).databaseName)
            NewEntryNotice.announceIfNeeded(context)
            record("arrival-while-auxiliary-refresh-is-busy")
            SyncSession.triggerSync(context, includeSideEffects = false)
            assertEquals("A successful text pull must not wait for auxiliary refresh to post its notice", 1,
                manager.activeNotifications.size)
        } finally { server.stop(0) }
    }

    @Test fun blockedCanRetry() = runBlocking {
        record("old")
        NewEntryNotice.announceIfNeeded(context)
        shadowOf(manager).setNotificationsEnabled(false)
        record("new")
        NewEntryNotice.announceIfNeeded(context)
        assertEquals("old", NotificationPreferences(context).lastNotifiedEntryId)
        assertTrue(manager.activeNotifications.isEmpty())
        shadowOf(manager).setNotificationsEnabled(true)
        NewEntryNotice.announceIfNeeded(context)
        assertEquals(1, manager.activeNotifications.size)
        assertEquals("new", NotificationPreferences(context).lastNotifiedEntryId)
    }

    @Test fun deleteIsNotArrival() = runBlocking {
        record("a"); record("b")
        NewEntryNotice.announceIfNeeded(context)
        db.entryDao().withdraw("b")
        NewEntryNotice.announceIfNeeded(context)
        assertTrue(manager.activeNotifications.isEmpty())
    }

    @Test fun editIsNotArrival() = runBlocking {
        record("a"); record("b")
        NewEntryNotice.announceIfNeeded(context)
        record("a") // Room REPLACE changes rowid; it must not turn an old record into a new arrival.
        NewEntryNotice.announceIfNeeded(context)
        assertTrue(manager.activeNotifications.isEmpty())
    }

    @Test fun privateStaysQuiet() = runBlocking {
        record("old"); NewEntryNotice.announceIfNeeded(context)
        record("private", visibility = "PRIVATE")
        NewEntryNotice.announceIfNeeded(context)
        assertTrue(manager.activeNotifications.isEmpty())
    }

    @Test fun foreignStaysQuiet() = runBlocking {
        record("old"); NewEntryNotice.announceIfNeeded(context)
        record("foreign", space = UUID.randomUUID())
        NewEntryNotice.announceIfNeeded(context)
        assertTrue(manager.activeNotifications.isEmpty())
    }

    @Test fun replyIsNoticedOnce() = runBlocking {
        record("mine", author = own)
        NewEntryNotice.announceIfNeeded(context)
        db.commentDao().insert(CommentEntity("reply", "mine", partner.toString(), "不能出现在锁屏上的回应", null, 1000))
        NewEntryNotice.announceIfNeeded(context)
        val notice = manager.activeNotifications.single().notification
        assertFalse(notice.extras.toString().contains("不能出现在锁屏上的回应"))
        NewEntryNotice.announceIfNeeded(context)
        assertEquals(1, manager.activeNotifications.size)
    }

    @Test fun oldRepliesAreSilent() = runBlocking {
        record("mine", author = own)
        db.commentDao().insert(CommentEntity("old", "mine", partner.toString(), "合成旧回应", null, 1000))
        NewEntryNotice.announceIfNeeded(context)
        assertTrue(manager.activeNotifications.isEmpty())
    }
}
