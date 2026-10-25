package com.twomemory.app

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.WorkManager
import com.twomemory.network.AnniversaryViewDto
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class IndependentAnniversaryOfflineStartupTest {
    @Test fun foregroundSchedulesConfirmedLocalDateEvenWhenServerIsUnreachable(): Unit = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        runCatching { WorkManager.getInstance(context) }.getOrElse {
            WorkManager.initialize(context, Configuration.Builder().build())
            WorkManager.getInstance(context)
        }
        SyncSession.save(context, "synthetic-offline-anniversary", UUID.randomUUID(), UUID.randomUUID(), "http://127.0.0.1:9")
        val binding = requireNotNull(SyncSession.captureBinding(context))
        val date = LocalDate.now().plusDays(2)
        val remote = AnniversaryViewDto(UUID.randomUUID(), binding.session.coupleId, "我们的纪念日",
            "GREGORIAN", date.monthValue, date.dayOfMonth, false, 2000, true)
        assertTrue(IndependentAnniversaryStore.acceptConfirmed(context, binding, remote))
        val prefs = IndependentAnniversaryStore.preferences(context, binding)
        assertNull(prefs.getString("queuedKey", null))
        SyncSession.triggerSync(context, includeSideEffects = false)
        assertNotNull("Offline foreground must restore a known reminder without waiting for server GET", prefs.getString("queuedKey", null))
        assertEquals(remote, IndependentAnniversaryStore.readConfirmed(prefs))
    }
}
