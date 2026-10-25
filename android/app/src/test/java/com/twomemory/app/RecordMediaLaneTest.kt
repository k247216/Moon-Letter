package com.twomemory.app

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.*
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RecordMediaLaneTest {
    @Test fun longMediaDoesNotAcquireForegroundTextLock() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        SyncSession.save(context, "test", UUID.randomUUID(), UUID.randomUUID(), "http://127.0.0.1:1")
        val started = CompletableDeferred<Unit>()
        val upload = launch(Dispatchers.Default) {
            RecordMediaSync.sync(context) { _, _ -> started.complete(Unit); awaitCancellation() }
        }
        try {
            withTimeout(3000) { started.await() }
            withTimeout(1000) {
                SyncSession.withCurrentForegroundSession(context) { session ->
                    assertEquals("test", session.token)
                    assertTrue(upload.isActive)
                } ?: fail("Text lane must retain its session during upload")
            }
        } finally { upload.cancelAndJoin() }
    }
}
