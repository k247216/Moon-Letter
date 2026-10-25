package com.twomemory.app.notifications

import android.app.Application
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import androidx.work.*
import androidx.work.impl.utils.taskexecutor.WorkManagerTaskExecutor
import com.sun.net.httpserver.HttpServer
import com.twomemory.app.CapsuleCard
import com.twomemory.app.CapsuleCloud
import com.twomemory.app.OccasionCard
import com.twomemory.app.OccasionCloud
import com.twomemory.app.SyncSession
import java.net.InetSocketAddress
import java.time.Instant
import java.time.LocalDate
import java.util.UUID
import java.util.concurrent.Executor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class LocalReminderDeliveryV42Test {
    private val context get() = ApplicationProvider.getApplicationContext<Context>()
    private val manager get() = requireNotNull(context.getSystemService(NotificationManager::class.java))
    private val zone get() = CloudKeepsakeReminders.zone()
    private val today get() = Instant.ofEpochMilli(System.currentTimeMillis()).atZone(zone).toLocalDate()
    private val couple = UUID.randomUUID()
    private val author = UUID.randomUUID()
    private val id = UUID.randomUUID().toString()

    private fun notice(): Notification = manager.activeNotifications.single().notification
    private fun target(): Intent = shadowOf(requireNotNull(notice().contentIntent)).savedIntent

    @Test fun localAnniversaryPendingIntentCarriesRawAnniversaryTarget() {
        assertTrue(LocalReminderNotifier.showReminder(context, "anniversary"))
        assertEquals("anniversary", target().getStringExtra("com.twomemory.app.OPEN_REMINDER"))
        assertFalse(target().hasExtra("com.twomemory.app.REMINDER_ID"))
    }

    @Test fun localCapsulePendingIntentCarriesRawCapsuleTarget() {
        assertTrue(LocalReminderNotifier.showReminder(context, "capsule"))
        assertEquals("capsule", target().getStringExtra("com.twomemory.app.OPEN_REMINDER"))
        assertFalse(target().hasExtra("com.twomemory.app.REMINDER_ID"))
    }

    @Test fun cloudOccasionPendingIntentCarriesRawOccasionTargetAndIdentity() {
        assertTrue(LocalReminderNotifier.showCloudReminder(context, "occasion", id))
        assertEquals("occasion", target().getStringExtra("com.twomemory.app.OPEN_REMINDER"))
        assertEquals(id, target().getStringExtra("com.twomemory.app.REMINDER_ID"))
    }

    @Test fun cloudCapsulePendingIntentCarriesRawCapsuleTargetAndIdentity() {
        assertTrue(LocalReminderNotifier.showCloudReminder(context, "capsule", id))
        assertEquals("capsule", target().getStringExtra("com.twomemory.app.OPEN_REMINDER"))
        assertEquals(id, target().getStringExtra("com.twomemory.app.REMINDER_ID"))
    }

    @Test fun testNotificationHasNoReminderTargetOrIdentity() {
        LocalReminderNotifier.sendTest(context)
        assertFalse(target().hasExtra("com.twomemory.app.OPEN_REMINDER"))
        assertFalse(target().hasExtra("com.twomemory.app.REMINDER_ID"))
    }

    @Test fun targetedReminderCapturesCurrentServerSpaceAndMemberScope() {
        SyncSession.save(context, "synthetic-scope", couple, author, "http://127.0.0.1:9")
        val expected = java.security.MessageDigest.getInstance("SHA-256")
            .digest("http://127.0.0.1:9|$couple|$author".toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
        assertTrue(LocalReminderNotifier.showReminder(context, "anniversary"))
        assertEquals(expected, target().getStringExtra("com.twomemory.app.NOTICE_SCOPE"))
        manager.cancelAll()
        assertTrue(LocalReminderNotifier.showCloudReminder(context, "capsule", id))
        assertEquals(expected, target().getStringExtra("com.twomemory.app.NOTICE_SCOPE"))
    }

    @Test fun cloudNoticeIsPrivateAndContainsOnlyGenericDisplayText() {
        assertTrue(LocalReminderNotifier.showCloudReminder(context, "capsule", id))
        assertEquals(Notification.VISIBILITY_PRIVATE, notice().visibility)
        assertEquals("一封时光信可以打开了", notice().extras.getString(Notification.EXTRA_TITLE))
        assertEquals("打开月笺，看看收好的那份心意。", notice().extras.getString(Notification.EXTRA_TEXT))
    }

    @Test fun disabledChannelSuppressesTargetedCloudNotification() {
        manager.createNotificationChannel(NotificationChannel(LocalReminderNotifier.CHANNEL,
            "本机提醒", NotificationManager.IMPORTANCE_NONE))
        assertFalse(LocalReminderNotifier.showCloudReminder(context, "occasion", id))
        assertTrue(manager.activeNotifications.isEmpty())
    }

    @Test fun daysLateAnnualOccasionRefreshesThenSkipsTodayNoticeAndQueuesNextYear() {
        val occurrence = today.minusDays(3)
        val card = occasion(occurrence, repeats = true)
        fixture("[${wire(card)}]") { session, calls ->
            OccasionCloud.store(context, session).replace(listOf(card))
            val workManager = workManager()
            val next = occurrence.plusYears(1)
            try {
                assertEquals(ListenableWorker.Result.success(), worker(session, "occasion", occurrence).doWork())
                assertEquals("Late work must still refresh authorization", 1, calls.get())
                assertTrue("A past occurrence must not say today", manager.activeNotifications.isEmpty())
                assertTrue("Skipping is not delivery", LocalReminderState(context).shouldDeliver(key(session, "occasion", occurrence)))
                assertEquals("The annual reminder must recover its next occurrence", 1,
                    workManager.getWorkInfosForUniqueWork(key(session, "occasion", next)).get(10, TimeUnit.SECONDS).size)
            } finally {
                workManager.cancelUniqueWork(key(session, "occasion", next)).result.get(10, TimeUnit.SECONDS)
            }
        }
    }

    @Test fun daysLateOneTimeOccasionRefreshesThenSkipsWithoutDeliveryReceipt() {
        val occurrence = today.minusDays(3)
        val card = occasion(occurrence)
        fixture("[${wire(card)}]") { session, calls ->
            OccasionCloud.store(context, session).replace(listOf(card))
            workManager()
            assertEquals(ListenableWorker.Result.success(), worker(session, "occasion", occurrence).doWork())
            assertEquals(1, calls.get())
            assertTrue(manager.activeNotifications.isEmpty())
            assertTrue(LocalReminderState(context).shouldDeliver(key(session, "occasion", occurrence)))
        }
    }

    @Test fun sameShanghaiDayOccasionStillDeliversOnceAfterRefresh() {
        val occurrence = today
        val card = occasion(occurrence)
        fixture("[${wire(card)}]") { session, calls ->
            OccasionCloud.store(context, session).replace(listOf(card))
            workManager()
            assertEquals(ListenableWorker.Result.success(), worker(session, "occasion", occurrence).doWork())
            assertEquals(1, calls.get())
            assertEquals(1, manager.activeNotifications.size)
            assertFalse(LocalReminderState(context).shouldDeliver(key(session, "occasion", occurrence)))
            assertEquals(ListenableWorker.Result.success(), worker(session, "occasion", occurrence).doWork())
            assertEquals(1, calls.get())
            assertEquals(1, manager.activeNotifications.size)
        }
    }

    @Test fun futureOccasionRetriesWithoutPostingOrConsumingReceipt() {
        val occurrence = today.plusDays(1)
        fixture("[]") { session, calls ->
            assertEquals(ListenableWorker.Result.retry(), worker(session, "occasion", occurrence).doWork())
            assertEquals(0, calls.get())
            assertTrue(manager.activeNotifications.isEmpty())
            assertTrue(LocalReminderState(context).shouldDeliver(key(session, "occasion", occurrence)))
        }
    }

    @Test fun offlineCapsuleCannotUseCachedUnlockedStateToNotify() {
        val occurrence = today.minusDays(3)
        fixture("offline", status = 503) { session, calls ->
            CapsuleCloud.store(context, session).replace(listOf(capsule(occurrence, unlocked = true)))
            assertEquals(ListenableWorker.Result.retry(), worker(session, "capsule", occurrence).doWork())
            assertEquals(1, calls.get())
            assertTrue(manager.activeNotifications.isEmpty())
            assertTrue(LocalReminderState(context).shouldDeliver(key(session, "capsule", occurrence)))
        }
    }

    @Test fun serverLockedCapsuleRetriesDespitePastPhoneDateAndUnlockedCache() {
        val occurrence = today.minusDays(3)
        fixture("[${wire(capsule(occurrence, unlocked = false))}]") { session, calls ->
            CapsuleCloud.store(context, session).replace(listOf(capsule(occurrence, unlocked = true)))
            assertEquals(ListenableWorker.Result.retry(), worker(session, "capsule", occurrence).doWork())
            assertEquals(1, calls.get())
            assertTrue(manager.activeNotifications.isEmpty())
            assertTrue(LocalReminderState(context).shouldDeliver(key(session, "capsule", occurrence)))
        }
    }

    @Test fun pastCapsuleConfirmedUnlockedStillPostsOnlyGenericPrivateNotice() {
        val occurrence = today.minusDays(3)
        fixture("[${wire(capsule(occurrence, unlocked = true))}]") { session, calls ->
            CapsuleCloud.store(context, session).replace(listOf(capsule(occurrence, unlocked = false)))
            workManager()
            assertEquals(ListenableWorker.Result.success(), worker(session, "capsule", occurrence).doWork())
            assertEquals(1, calls.get())
            assertEquals(Notification.VISIBILITY_PRIVATE, notice().visibility)
            assertEquals("一封时光信可以打开了", notice().extras.getString(Notification.EXTRA_TITLE))
            assertEquals("打开月笺，看看收好的那份心意。", notice().extras.getString(Notification.EXTRA_TEXT))
            assertFalse(LocalReminderState(context).shouldDeliver(key(session, "capsule", occurrence)))
        }
    }

    private fun occasion(date: LocalDate, repeats: Boolean = false) = OccasionCard(id, couple.toString(),
        author.toString(), "BIRTHDAY", "私密标题", "私密正文", date.toString(), repeats, null, null,
        "2026-10-10T00:00:00Z", "PRIVATE")

    private fun capsule(date: LocalDate, unlocked: Boolean) = CapsuleCard(id, couple.toString(), author.toString(),
        "私密信件标题", "私密信件正文", date.toString(), "PRIVATE", unlocked, "2026-10-10T00:00:00Z")

    private fun wire(card: OccasionCard) = JSONObject().put("id", card.id).put("coupleId", card.coupleId)
        .put("authorId", card.authorId).put("kind", card.kind).put("title", card.title).put("body", card.body)
        .put("date", card.date).put("repeatsYearly", card.repeatsYearly).put("recipientId", JSONObject.NULL)
        .put("photoAssetId", JSONObject.NULL).put("updatedAt", card.updatedAt).put("visibility", card.visibility)

    private fun wire(card: CapsuleCard) = JSONObject().put("id", card.id).put("coupleId", card.coupleId)
        .put("authorId", card.authorId).put("title", card.title).put("body", card.body)
        .put("unlockDate", card.unlockDate).put("visibility", card.visibility).put("unlocked", card.unlocked)
        .put("updatedAt", card.updatedAt)

    // Occasion delivery has a 09:00 queue key; using midnight for today's input avoids a clock/hour dependency.
    private fun at(kind: String, date: LocalDate) = (if (kind == "occasion") date.atTime(9, 0)
        else date.atStartOfDay()).atZone(zone).toInstant().toEpochMilli()
    private fun key(session: SyncSession.Session, kind: String, date: LocalDate) =
        "cloud-${CloudKeepsakeReminders.currentScope(session)}-$kind-$id-${at(kind, date)}"

    private fun worker(session: SyncSession.Session, kind: String, date: LocalDate): CloudKeepsakeReminderWorker {
        val executor = Executor { it.run() }
        val parameters = WorkerParameters(UUID.randomUUID(), workDataOf("scope" to CloudKeepsakeReminders.currentScope(session),
            "kind" to kind, "id" to id, "key" to key(session, kind, date),
            "at" to if (kind == "occasion" && date == today) date.atStartOfDay(zone).toInstant().toEpochMilli() else at(kind, date)),
            emptyList(), WorkerParameters.RuntimeExtras(), 0, 0, executor, Dispatchers.IO, WorkManagerTaskExecutor(executor),
            object : WorkerFactory() {
                override fun createWorker(appContext: Context, workerClassName: String, workerParameters: WorkerParameters): ListenableWorker? = null
            }, ProgressUpdater { _, _, _ -> throw AssertionError("Unexpected progress") },
            ForegroundUpdater { _, _, _ -> throw AssertionError("Unexpected foreground") })
        return CloudKeepsakeReminderWorker(context, parameters)
    }

    private fun workManager(): WorkManager = runCatching { WorkManager.getInstance(context) }.getOrElse {
        WorkManager.initialize(context, Configuration.Builder().build())
        WorkManager.getInstance(context)
    }

    private fun fixture(response: String, status: Int = 200,
        block: suspend (SyncSession.Session, AtomicInteger) -> Unit) = runBlocking {
        val calls = AtomicInteger()
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/") { exchange ->
            calls.incrementAndGet()
            val bytes = response.toByteArray(Charsets.UTF_8)
            exchange.sendResponseHeaders(status, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
        server.start()
        try {
            SyncSession.save(context, "synthetic-v42-reminder", couple, author, "http://127.0.0.1:${server.address.port}")
            block(requireNotNull(SyncSession.load(context)), calls)
        } finally { server.stop(0) }
    }
}
