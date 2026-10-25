package com.twomemory.app

import android.content.Context
import android.graphics.Bitmap
import androidx.test.core.app.ApplicationProvider
import com.twomemory.designsystem.EntryPhotos
import java.io.ByteArrayOutputStream
import java.util.UUID
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PhotoSourceNamespaceTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    @Test fun equalAssetIdsInDifferentBackendsNeverReuseEachOthersReadingPixels() = runBlocking {
        val id = UUID.randomUUID().toString()
        EntryPhotos.installSource("backend-A", reading = { picture(600) })
        assertEquals(600, requireNotNull(EntryPhotos.load(context, null, id, reading = true)).width)
        EntryPhotos.installSource("backend-B", reading = { picture(320) })
        assertEquals(320, requireNotNull(EntryPhotos.load(context, null, id, reading = true)).width)
    }
    @Test fun equalAssetIdsNeverReuseAnotherBackendsPersistedOriginal() = runBlocking {
        val id = UUID.randomUUID().toString()
        EntryPhotos.installSource("backend-A", file = { _, destination -> destination.writeBytes("original-A".toByteArray()) })
        assertEquals("original-A", requireNotNull(EntryPhotos.localOriginal(context, null, id)).readText())
        EntryPhotos.installSource("backend-B", file = { _, destination -> destination.writeBytes("original-B".toByteArray()) })
        assertEquals("original-B", requireNotNull(EntryPhotos.localOriginal(context, null, id)).readText())
    }
    @Test fun lateOldPreviewCompletesInItsCapturedNamespaceNotTheNewBackendsCache() = runBlocking {
        val id = UUID.randomUUID().toString()
        val entered = CompletableDeferred<Unit>(); val release = CompletableDeferred<Unit>()
        EntryPhotos.installSource("backend-A", preview = { entered.complete(Unit); release.await(); picture(600) })
        val pending = async(Dispatchers.IO) { EntryPhotos.load(context, null, id) }
        try {
            withTimeout(5_000) { entered.await() }
            EntryPhotos.installSource("backend-B", preview = { picture(320) })
            val current = requireNotNull(EntryPhotos.load(context, null, id))
            release.complete(Unit)
            assertEquals(600, requireNotNull(withTimeout(5_000) { pending.await() }).width)
            assertSame(current, EntryPhotos.load(context, null, id))
        } finally { release.complete(Unit); pending.cancelAndJoin() }
    }
    private fun picture(width: Int): ByteArray {
        val image = Bitmap.createBitmap(width, 180, Bitmap.Config.ARGB_8888)
        return try { ByteArrayOutputStream().use { out ->
            image.compress(Bitmap.CompressFormat.PNG, 100, out); out.toByteArray()
        } } finally { image.recycle() }
    }
}
