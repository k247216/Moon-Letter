package com.twomemory.app.notifications

import android.app.NotificationManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.*
import androidx.work.impl.utils.taskexecutor.WorkManagerTaskExecutor
import com.sun.net.httpserver.HttpServer
import com.twomemory.app.*
import java.net.InetSocketAddress
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
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CloudKeepsakeReminderRegressionTest {
    private val context get() = ApplicationProvider.getApplicationContext<Context>()
    private val today get() = LocalDate.now(CloudKeepsakeReminders.zone())
    private val couple = UUID.randomUUID()
    private val author = UUID.randomUUID()
    private val id = UUID.randomUUID().toString()
    private fun card(repeating: Boolean = false) = OccasionCard(id, couple.toString(), author.toString(), "BIRTHDAY",
        "生日", "绝不放进通知的正文", today.toString(), repeating, null, null, "2026-10-10T00:00:00Z")
    private fun wire(card: OccasionCard) = JSONObject().put("id", card.id).put("coupleId", card.coupleId)
        .put("authorId", card.authorId).put("kind", card.kind).put("title", card.title).put("body", card.body)
        .put("date", card.date).put("repeatsYearly", card.repeatsYearly).put("recipientId", JSONObject.NULL)
        .put("photoAssetId", JSONObject.NULL).put("updatedAt", card.updatedAt).put("visibility", card.visibility).toString()
    private fun key(session: SyncSession.Session, date: LocalDate) = "cloud-${CloudKeepsakeReminders.currentScope(session)}-occasion-$id-${date.atTime(9, 0).atZone(CloudKeepsakeReminders.zone()).toInstant().toEpochMilli()}"
    private fun worker(session: SyncSession.Session): CloudKeepsakeReminderWorker {
        // Execute the actual worker; these unused progress/foreground dependencies fail if invoked.
        val executor = Executor { it.run() }
        val parameters = WorkerParameters(UUID.randomUUID(), workDataOf("scope" to CloudKeepsakeReminders.currentScope(session),
            "kind" to "occasion", "id" to id, "key" to key(session, today),
            "at" to today.atStartOfDay(CloudKeepsakeReminders.zone()).toInstant().toEpochMilli()), emptyList(),
            WorkerParameters.RuntimeExtras(), 0, 0, executor, Dispatchers.IO, WorkManagerTaskExecutor(executor),
            object : WorkerFactory() {
                override fun createWorker(appContext: Context, workerClassName: String, workerParameters: WorkerParameters): ListenableWorker? = null
            }, ProgressUpdater { _, _, _ -> throw AssertionError("unexpected progress write") },
            ForegroundUpdater { _, _, _ -> throw AssertionError("unexpected foreground write") })
        return CloudKeepsakeReminderWorker(context, parameters)
    }
    private fun fixture(response: String, status: Int = 200, block: suspend (SyncSession.Session, AtomicInteger) -> Unit) = runBlocking {
        val calls = AtomicInteger()
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/") { exchange ->
            calls.incrementAndGet()
            val bytes = response.toByteArray(); exchange.sendResponseHeaders(status, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
        server.start()
        try {
            SyncSession.save(context, "reminder-test", couple, author, "http://127.0.0.1:${server.address.port}")
            val session = SyncSession.load(context)!!
            OccasionCloud.store(context, session).replace(listOf(card()))
            block(session, calls)
        } finally { server.stop(0) }
    }
    private fun notifications() = context.getSystemService(NotificationManager::class.java)!!.activeNotifications

    @Test fun withdrawnOccasionIsFetchedBeforeDeliveryAndNeverUsesCachedCard() = fixture("[]") { session, calls ->
        assertEquals(ListenableWorker.Result.success(), worker(session).doWork())
        assertEquals(1, calls.get())
        assertTrue(notifications().isEmpty())
        assertTrue(LocalReminderState(context).shouldDeliver(key(session, today)))
    }

    @Test fun unavailableServerRetriesWithoutPostingOrMarkingDelivered() = fixture("offline", 503) { session, calls ->
        assertEquals(ListenableWorker.Result.retry(), worker(session).doWork())
        assertEquals(1, calls.get()); assertTrue(notifications().isEmpty())
        assertTrue(LocalReminderState(context).shouldDeliver(key(session, today)))
    }

    @Test fun malformedSnapshotRetriesWithoutPosting() = fixture("[{}]") { session, calls ->
        assertEquals(ListenableWorker.Result.retry(), worker(session).doWork())
        assertEquals(1, calls.get()); assertTrue(notifications().isEmpty())
    }

    @Test fun remoteDateChangeSuppressesOldReminder() = fixture("[${wire(card().copy(date = today.plusDays(1).toString()))}]") { session, calls ->
        assertEquals(ListenableWorker.Result.success(), worker(session).doWork())
        assertEquals(1, calls.get()); assertTrue(notifications().isEmpty())
    }

    @Test fun deliveredAnnualReminderSchedulesNextYearOnTheSameDayAndOnlyOnce() {
        SyncSession.save(context, "annual-test", couple, author, "http://127.0.0.1:1")
        val session = SyncSession.load(context)!!
        OccasionCloud.store(context, session).replace(listOf(card(true)))
        LocalReminderState(context).markDelivered(key(session, today))
        val manager = runCatching { WorkManager.getInstance(context) }.getOrElse {
            WorkManager.initialize(context, Configuration.Builder().build()); WorkManager.getInstance(context)
        }
        val nextYear = today.plusYears(1)
        CloudKeepsakeReminders.schedule(context)
        assertEquals(1, manager.getWorkInfosForUniqueWork(key(session, nextYear)).get(10, TimeUnit.SECONDS).size)
        assertTrue(manager.getWorkInfosForUniqueWork(key(session, today)).get(10, TimeUnit.SECONDS).isEmpty())
        CloudKeepsakeReminders.schedule(context)
        assertEquals(1, manager.getWorkInfosForUniqueWork(key(session, nextYear)).get(10, TimeUnit.SECONDS).size)
        manager.cancelUniqueWork(key(session, nextYear)).result.get(10, TimeUnit.SECONDS)
    }
}
