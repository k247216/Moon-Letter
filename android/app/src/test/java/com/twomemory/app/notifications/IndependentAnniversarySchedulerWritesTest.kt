package com.twomemory.app.notifications

import android.app.Application
import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.WorkManager
import com.twomemory.app.IndependentAnniversaryStore
import com.twomemory.app.SyncSession
import com.twomemory.network.AnniversaryViewDto
import java.util.UUID
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class IndependentAnniversarySchedulerWritesTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test fun unchangedQueuedOccurrenceAndAlreadyEmptyQueueDoNotRewritePreferences() {
        val manager = runCatching { WorkManager.getInstance(context) }.getOrElse {
            WorkManager.initialize(context, Configuration.Builder().build())
            WorkManager.getInstance(context)
        }
        SyncSession.save(context, "synthetic-scheduler-writes", UUID.randomUUID(), UUID.randomUUID(), "http://127.0.0.1:9")
        val binding = requireNotNull(SyncSession.captureBinding(context))
        val scope = IndependentAnniversaryStore.scopeOf(binding.session)
        val toggle = context.getSharedPreferences("moon_letter_tools", Context.MODE_PRIVATE)
        val original = toggle.getBoolean("localRemindersEnabled", true)
        val hadToggle = toggle.contains("localRemindersEnabled")
        val commits = AtomicInteger()
        val counted = countingContext(commits)
        try {
            assertTrue(toggle.edit().putBoolean("localRemindersEnabled", true).commit())
            assertTrue(IndependentAnniversaryStore.acceptConfirmed(context, binding,
                AnniversaryViewDto(UUID.randomUUID(), binding.session.coupleId, "独立调度测试", "GREGORIAN", 9, 24, false, 2020, true)))
            IndependentAnniversaryReminder.schedule(counted, binding)
            val prefs = IndependentAnniversaryStore.preferences(context, binding)
            assertNotNull("A real first schedule must persist its occurrence", prefs.getString("queuedKey", null))
            commits.set(0)
            IndependentAnniversaryReminder.schedule(counted, binding)
            assertEquals("Foreground rescheduling must not rewrite identical queue metadata", 0, commits.get())
            assertTrue(IndependentAnniversaryStore.acceptConfirmed(context, binding, null))
            IndependentAnniversaryReminder.schedule(counted, binding)
            assertFalse(prefs.contains("queuedKey"))
            commits.set(0)
            IndependentAnniversaryReminder.schedule(counted, binding)
            assertEquals("An already-empty queue requires no preference write", 0, commits.get())
        } finally {
            manager.cancelAllWorkByTag("independent-anniversary:$scope").result.get(10, TimeUnit.SECONDS)
            val restore = toggle.edit()
            if (hadToggle) restore.putBoolean("localRemindersEnabled", original) else restore.remove("localRemindersEnabled")
            assertTrue(restore.commit())
        }
    }

    private fun countingContext(commits: AtomicInteger): Context = object : ContextWrapper(context) {
        override fun getApplicationContext(): Context = this
        override fun getSharedPreferences(name: String, mode: Int): SharedPreferences {
            val prefs = super.getSharedPreferences(name, mode)
            if (!name.startsWith("independent_anniversary_")) return prefs
            return object : SharedPreferences by prefs {
                override fun edit(): SharedPreferences.Editor {
                    val target = prefs.edit()
                    return object : SharedPreferences.Editor by target {
                        override fun putString(key: String, value: String?): SharedPreferences.Editor { target.putString(key, value); return this }
                        override fun putLong(key: String, value: Long): SharedPreferences.Editor { target.putLong(key, value); return this }
                        override fun remove(key: String): SharedPreferences.Editor { target.remove(key); return this }
                        override fun commit(): Boolean { commits.incrementAndGet(); return target.commit() }
                    }
                }
            }
        }
    }
}
