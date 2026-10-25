package com.twomemory.app.notifications

import android.app.Application
import android.app.NotificationManager
import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import androidx.test.core.app.ApplicationProvider
import androidx.work.*
import androidx.work.impl.utils.taskexecutor.WorkManagerTaskExecutor
import com.twomemory.app.IndependentAnniversaryStore
import com.twomemory.app.SyncSession
import com.twomemory.app.recordingScopeKey
import com.twomemory.network.AnniversaryViewDto
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID
import java.util.concurrent.Executor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Executes the real worker with a previously queued, valid occurrence as input. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class IndependentAnniversaryWorkerTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    private data class Fixture(val binding: SyncSession.BindingSnapshot, val remote: AnniversaryViewDto,
        val scope: String, val configuration: String, val key: String, val at: Long)

    private fun fixture(occurrence: LocalDate = LocalDate.now(), initializeManager: Boolean = true): Fixture {
        if (initializeManager) runCatching { WorkManager.getInstance(context) }.getOrElse {
            WorkManager.initialize(context, Configuration.Builder().build())
            WorkManager.getInstance(context)
        }
        SyncSession.save(context, "synthetic-worker", UUID.randomUUID(), UUID.randomUUID(), "http://127.0.0.1:9")
        val binding = requireNotNull(SyncSession.captureBinding(context))
        val date = occurrence.withYear(2000)
        val remote = AnniversaryViewDto(UUID.randomUUID(), binding.session.coupleId, "只用于配置的私密标题",
            "GREGORIAN", date.monthValue, date.dayOfMonth, false, 2000, true)
        assertTrue(IndependentAnniversaryStore.acceptConfirmed(context, binding, remote))
        val scope = IndependentAnniversaryStore.scopeOf(binding.session)
        val zone = ZoneId.systemDefault()
        val at = occurrence.atStartOfDay(zone).toInstant().toEpochMilli()
        // Input fixture follows the persisted work-data contract, not the gate being tested.
        val configuration = recordingScopeKey("${remote.id}|${remote.title}|$date|true|00:00|${zone.id}")
        val key = "independent-anniversary:$scope:$configuration:$at"
        assertTrue(IndependentAnniversaryStore.preferences(context, binding).edit()
            .putString("reminderTime", "00:00").putString("queuedConfiguration", configuration)
            .putString("queuedKey", key).putLong("queuedAt", at).commit())
        return Fixture(binding, remote, scope, configuration, key, at)
    }

    private fun worker(fixture: Fixture, workerContext: Context = context): IndependentAnniversaryReminderWorker {
        // Success-path workers must have a real scheduler available to enqueue next year.
        runCatching { WorkManager.getInstance(context) }.getOrElse {
            WorkManager.initialize(context, Configuration.Builder().build())
            WorkManager.getInstance(context)
        }
        val executor = Executor { it.run() }
        val parameters = WorkerParameters(UUID.randomUUID(), workDataOf("scope" to fixture.scope,
            "configuration" to fixture.configuration, "key" to fixture.key, "at" to fixture.at), emptyList(),
            WorkerParameters.RuntimeExtras(), 0, 0, executor, Dispatchers.IO, WorkManagerTaskExecutor(executor),
            object : WorkerFactory() {
                override fun createWorker(appContext: Context, workerClassName: String, workerParameters: WorkerParameters): ListenableWorker? = null
            }, ProgressUpdater { _, _, _ -> throw AssertionError("Unexpected progress update") },
            ForegroundUpdater { _, _, _ -> throw AssertionError("Unexpected foreground update") })
        return IndependentAnniversaryReminderWorker(workerContext, parameters)
    }

    @Test fun deviceReminderOffSuppressesPreviouslyQueuedWorkAndKeepsReceiptUndelivered() = runBlocking {
        val fixture = fixture()
        withDeviceToggle(false) {
            assertEquals(ListenableWorker.Result.success(), worker(fixture).doWork())
            assertTrue(context.getSystemService(NotificationManager::class.java)!!.activeNotifications.isEmpty())
            assertTrue(LocalReminderState(context).shouldDeliver(fixture.key))
        }
    }

    @Test fun oldAccountWorkCannotNotifyOrMarkDeliveredUnderTheNewAccount() = runBlocking {
        val fixture = fixture()
        SyncSession.save(context, "synthetic-new-owner", UUID.randomUUID(), UUID.randomUUID(), "http://127.0.0.1:9")
        withDeviceToggle(true) {
            assertEquals(ListenableWorker.Result.success(), worker(fixture).doWork())
            assertTrue(context.getSystemService(NotificationManager::class.java)!!.activeNotifications.isEmpty())
            assertTrue(LocalReminderState(context).shouldDeliver(fixture.key))
        }
    }

    @Test fun editedConfirmedDateMakesOldQueuedWorkIneligible() = runBlocking {
        val fixture = fixture()
        val tomorrow = LocalDate.now().plusDays(1)
        assertTrue(IndependentAnniversaryStore.acceptConfirmed(context, fixture.binding,
            fixture.remote.copy(month = tomorrow.monthValue, day = tomorrow.dayOfMonth)))
        withDeviceToggle(true) {
            assertEquals(ListenableWorker.Result.success(), worker(fixture).doWork())
            assertTrue(context.getSystemService(NotificationManager::class.java)!!.activeNotifications.isEmpty())
            assertTrue(LocalReminderState(context).shouldDeliver(fixture.key))
        }
    }

    @Test fun currentValidDueWorkPostsGenericNoticeOnlyOnce() = runBlocking {
        val fixture = fixture()
        withDeviceToggle(true) {
            assertEquals(ListenableWorker.Result.success(), worker(fixture).doWork())
            val notices = context.getSystemService(NotificationManager::class.java)!!.activeNotifications
            assertEquals(1, notices.size)
            assertEquals("今天是你保存的纪念日", notices.single().notification.extras.getString("android.title"))
            assertFalse(LocalReminderState(context).shouldDeliver(fixture.key))
            assertEquals(ListenableWorker.Result.success(), worker(fixture).doWork())
            assertEquals(1, context.getSystemService(NotificationManager::class.java)!!.activeNotifications.size)
        }
    }

    @Test fun yesterdayOccurrenceCannotAnnounceTodayOrMarkTheOldOccurrenceDelivered() = runBlocking {
        val fixture = fixture(LocalDate.now().minusDays(1))
        withDeviceToggle(true) {
            assertEquals(ListenableWorker.Result.success(), worker(fixture).doWork())
            assertTrue("Days-late work must not say today is the anniversary",
                context.getSystemService(NotificationManager::class.java)!!.activeNotifications.isEmpty())
            assertTrue("Skipping a stale occurrence is not delivery", LocalReminderState(context).shouldDeliver(fixture.key))
            val next = IndependentAnniversaryStore.preferences(context, fixture.binding).getLong("queuedAt", -1)
            assertTrue("The stale occurrence must be replanned, not left in the past", next > System.currentTimeMillis())
        }
    }

    @Test fun failedReceiptCommitReturnsRetryWithoutClaimingDurableDelivery() = runBlocking {
        val fixture = fixture()
        withDeviceToggle(true) {
            val result = runCatching { worker(fixture, failingCommit("moon_letter_local_reminders")).doWork() }
            assertNull("Receipt persistence errors must not escape the worker", result.exceptionOrNull())
            assertEquals(ListenableWorker.Result.retry(), result.getOrNull())
            assertTrue("A failed receipt commit is not durable delivery", LocalReminderState(context).shouldDeliver(fixture.key))
        }
    }

    @Test fun failedNextQueueCommitReturnsRetryAndCanRecoverWithoutPostingAgain() = runBlocking {
        val fixture = fixture()
        withDeviceToggle(true) {
            val namespace = "independent_anniversary_${fixture.scope}"
            val result = runCatching { worker(fixture, failingCommit(namespace)).doWork() }
            assertNull("Future-queue persistence errors must not escape the worker", result.exceptionOrNull())
            assertEquals(ListenableWorker.Result.retry(), result.getOrNull())
            assertFalse("The already successful receipt remains durable", LocalReminderState(context).shouldDeliver(fixture.key))
            assertEquals(fixture.key, IndependentAnniversaryStore.preferences(context, fixture.binding).getString("queuedKey", null))
            assertEquals(ListenableWorker.Result.success(), worker(fixture).doWork())
            assertEquals(1, context.getSystemService(NotificationManager::class.java)!!.activeNotifications.size)
            assertTrue(IndependentAnniversaryStore.preferences(context, fixture.binding).getLong("queuedAt", -1) > System.currentTimeMillis())
        }
    }

    @Test fun unavailableWorkManagerIsReportedInsteadOfSilentlyClaimingQueueSuccess() {
        val fixture = fixture(initializeManager = false)
        val type = androidx.work.impl.WorkManagerImpl::class.java
        val fields = listOf("sDefaultInstance", "sDelegatedInstance").map { type.getDeclaredField(it).apply { isAccessible = true } }
        val previous = fields.map { it.get(null) }
        try {
            fields.forEach { it.set(null, null) }
            assertTrue("This fixture intentionally has no initialized WorkManager", runCatching { WorkManager.getInstance(context) }.isFailure)
            withDeviceToggleSync(true) {
                val failure = runCatching { IndependentAnniversaryReminder.schedule(context, fixture.binding) }.exceptionOrNull()
                assertNotNull("An unavailable scheduler must reach the Store's truthful error path", failure)
            }
        } finally { fields.forEachIndexed { index, field -> field.set(null, previous[index]) } }
    }

    @Test fun receiptCancellationPropagatesInsteadOfBeingConvertedToRetry() = runBlocking {
        val fixture = fixture()
        withDeviceToggle(true) {
            val result = runCatching { worker(fixture,
                failingCommit("moon_letter_local_reminders", CancellationException("synthetic receipt cancellation"))).doWork() }
            assertTrue(result.exceptionOrNull() is CancellationException)
            assertTrue(LocalReminderState(context).shouldDeliver(fixture.key))
        }
    }

    private fun failingCommit(namespace: String, failure: RuntimeException? = null): Context = object : ContextWrapper(context) {
        override fun getApplicationContext(): Context = this
        override fun getSharedPreferences(name: String, mode: Int): SharedPreferences {
            val prefs = super.getSharedPreferences(name, mode)
            if (name != namespace) return prefs
            return object : SharedPreferences by prefs {
                override fun edit(): SharedPreferences.Editor {
                    val target = prefs.edit()
                    return object : SharedPreferences.Editor by target {
                        override fun putBoolean(key: String, value: Boolean): SharedPreferences.Editor { target.putBoolean(key, value); return this }
                        override fun putString(key: String, value: String?): SharedPreferences.Editor { target.putString(key, value); return this }
                        override fun putLong(key: String, value: Long): SharedPreferences.Editor { target.putLong(key, value); return this }
                        override fun remove(key: String): SharedPreferences.Editor { target.remove(key); return this }
                        override fun commit(): Boolean { if (failure != null) throw failure; return false }
                    }
                }
            }
        }
    }

    private fun withDeviceToggleSync(enabled: Boolean, block: () -> Unit) = runBlocking { withDeviceToggle(enabled) { block() } }

    private suspend fun withDeviceToggle(enabled: Boolean, block: suspend () -> Unit) {
        val prefs = context.getSharedPreferences("moon_letter_tools", Context.MODE_PRIVATE)
        val hadValue = prefs.contains("localRemindersEnabled")
        val original = prefs.getBoolean("localRemindersEnabled", true)
        assertTrue(prefs.edit().putBoolean("localRemindersEnabled", enabled).commit())
        try { block() } finally {
            val editor = prefs.edit()
            if (hadValue) editor.putBoolean("localRemindersEnabled", original) else editor.remove("localRemindersEnabled")
            assertTrue(editor.commit())
        }
    }
}
