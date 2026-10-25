package com.twomemory.app

import android.content.Context
import android.graphics.Bitmap
import androidx.test.core.app.ApplicationProvider
import com.twomemory.designsystem.EntryPhotos
import java.io.File
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LocalTimelinePreviewCacheTest {
    @Test fun localTimelineImageCreatesReusableSmallDiskPixelsWithoutChangingTheOriginal(): Unit = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val scope = "local-disk-${UUID.randomUUID()}"
        EntryPhotos.installSource(scope)
        val namespace = java.security.MessageDigest.getInstance("SHA-256").digest(scope.toByteArray())
            .joinToString("") { "%02x".format(it) }
        val folder = File(context.cacheDir, "entry-previews/$namespace")
        val original = File(context.filesDir, "photos/${UUID.randomUUID()}.png")
        original.parentFile!!.mkdirs()
        val image = Bitmap.createBitmap(2400, 1600, Bitmap.Config.ARGB_8888)
        try { original.outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) } }
        finally { image.recycle() }
        val before = original.readBytes()
        try {
            val preview = requireNotNull(EntryPhotos.load(context, original.absolutePath, null))
            val derived = folder.listFiles().orEmpty().filter { it.isFile && it.length() > 0 }
            assertEquals("Local photos also need a disk preview after decoded RAM is evicted", 1, derived.size)
            val savedPixels = android.graphics.BitmapFactory.decodeFile(derived.single().absolutePath)
            assertNotNull(savedPixels)
            assertEquals(preview.width, savedPixels.width)
            assertEquals(preview.height, savedPixels.height)
            savedPixels.recycle()
            assertArrayEquals(before, original.readBytes())
            original.writeBytes(byteArrayOf(1, 2))
            assertNull("An invalid replacement must not adopt the prior file's preview", EntryPhotos.load(context, original.absolutePath, null))
        } finally { original.delete(); EntryPhotos.installSource("local-disk-cleanup-${UUID.randomUUID()}") }
    }
}
