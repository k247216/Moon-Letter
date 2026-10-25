package com.twomemory.app.notifications

import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import com.twomemory.app.MainActivity
import com.twomemory.app.SyncSession
import java.util.UUID
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class NoticeIntentTest {
    private lateinit var context: Context
    @Before fun setup() {
        context = ApplicationProvider.getApplicationContext()
        SyncSession.save(context, "synthetic-notice", UUID.randomUUID(), UUID.randomUUID())
    }
    private fun intent(scope: String) = Intent(context, MainActivity::class.java)
        .putExtra(MainActivity.EXTRA_OPEN_ENTRY, "synthetic-entry")
        .putExtra("com.twomemory.app.NOTICE_SCOPE", scope)

    @Test fun staleTapIsIgnored() {
        val controller = Robolectric.buildActivity(MainActivity::class.java, intent("another-space")).create()
        try { assertNull(controller.get().openEntryId) } finally { controller.destroy() }
    }
    @Test fun staleWarmTapIsIgnored() {
        val controller = Robolectric.buildActivity(MainActivity::class.java).create()
        try {
            controller.newIntent(intent("another-space"))
            assertNull(controller.get().openEntryId)
        } finally { controller.destroy() }
    }
    @Test fun currentTapStillOpens() {
        val controller = Robolectric.buildActivity(MainActivity::class.java, intent(NoticeTargetScope.current(context)!!)).create()
        try { assertEquals("synthetic-entry", controller.get().openEntryId) } finally { controller.destroy() }
    }
    @Test fun legacyUnscopedTapOpensAppWithoutRouting() {
        val oldIntent = Intent(context, MainActivity::class.java)
            .putExtra(MainActivity.EXTRA_OPEN_ENTRY, "synthetic-entry")
        val controller = Robolectric.buildActivity(MainActivity::class.java, oldIntent).create()
        try { assertNull(controller.get().openEntryId) } finally { controller.destroy() }
    }
}
